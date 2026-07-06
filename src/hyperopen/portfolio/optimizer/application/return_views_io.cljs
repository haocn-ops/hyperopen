(ns hyperopen.portfolio.optimizer.application.return-views-io
  "Pure export/import model for the Return views panel's file workflow: a user
  downloads the universe's views as a versioned JSON document, lets a desktop
  tool (typically an AI agent running their own forecasting model) fill in
  per-asset returns, and re-imports the file to author those views in bulk.

  Honesty invariant: implied rows export with a null `return-percent` (the
  implied baseline rides in a separate read-only field) and import applies only
  non-null returns, so a round-trip of an unedited export is a no-op — a
  machine-seeded baseline can never silently become a user view. No DOM and no
  clock live here; callers supply `now-ms`."
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.black-litterman-editor-model :as bl-model]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def export-document-type
  "hyperopen-optimizer-return-views")

(def export-document-version
  1)

(def export-instructions
  (str "Fill `return-percent` (annual expected return, in percent, e.g. 12.5)"
       " for any asset you have a forecast for; optionally set `confidence` to"
       " low, medium, or high. Leave `return-percent` null to keep that asset"
       " on the implied baseline. `implied-return-percent` is read-only"
       " context. Do not edit `instrument-id`. Re-import this file with the"
       " Import button in the optimizer's Return views panel."))

(defn- percent-number
  "Decimal → percent as a clean number (15.0, not 15.000000000000002)."
  [decimal]
  (when (coercion/finite-number? decimal)
    (let [text (coercion/decimal->percent-text decimal)
          parsed (js/Number text)]
      (when (coercion/finite-number? parsed)
        parsed))))

(defn- instrument-symbol
  [instrument]
  (or (coercion/non-blank-text (:coin instrument))
      (coercion/non-blank-text (:symbol instrument))
      (coercion/non-blank-text (:name instrument))
      (some-> (coercion/non-blank-text (:instrument-id instrument))
              (str/split #":")
              last)))

(defn- symbol-by-instrument-id
  [universe]
  (into {}
        (keep (fn [instrument]
                (when-let [instrument-id (coercion/non-blank-text (:instrument-id instrument))]
                  [instrument-id (instrument-symbol instrument)])))
        universe))

(defn- export-entry
  [symbols implied-by-instrument row]
  (let [user? (= :user (:source row))]
    {:instrument-id (:instrument-id row)
     :symbol (get symbols (:instrument-id row))
     :source (name (:source row))
     :return-percent (when user? (percent-number (:return row)))
     :implied-return-percent (percent-number
                              (get implied-by-instrument (:instrument-id row)))
     :confidence (when user? (some-> (:confidence-level row) name))}))

(defn export-payload
  "Download payload for the panel's provenance rows (`return-views/rows` output).
  `implied-returns-by-instrument` is the same baseline map the rows were built
  from, so every entry carries the implied context even where the user has a
  view. Returns {:filename … :count … :document …} for the download effect."
  [{:keys [rows universe implied-returns-by-instrument now-ms]}]
  (let [symbols (symbol-by-instrument-id universe)
        entries (mapv #(export-entry symbols implied-returns-by-instrument %) rows)]
    {:filename (str "return-views-" now-ms ".json")
     :count (count entries)
     :document {:type export-document-type
                :version export-document-version
                :exported-at now-ms
                :instructions export-instructions
                :entries entries}}))

(defn- entry-field
  [entry & keys*]
  (some (fn [key*]
          (let [value (or (get entry key*)
                          (get entry (name key*)))]
            (when (some? value) value)))
        keys*))

(defn extract-entries
  "Candidate entry maps from parsed import data: the full envelope, or a bare
  array of entry maps. Nil when the structure is unusable."
  [data]
  (cond
    (map? data) (let [entries (entry-field data :entries)]
                  (when (sequential? entries)
                    (filterv map? entries)))
    (sequential? data) (filterv map? data)
    :else nil))

(defn- universe-instrument-ids
  [universe]
  (into #{}
        (keep #(coercion/non-blank-text (:instrument-id %)))
        universe))

(defn- instrument-id-by-symbol
  "Case-insensitive symbol → instrument-id, ambiguous symbols dropped."
  [universe]
  (let [pairs (keep (fn [instrument]
                      (when-let [instrument-id (coercion/non-blank-text (:instrument-id instrument))]
                        (when-let [symbol* (instrument-symbol instrument)]
                          [(str/upper-case symbol*) instrument-id])))
                    universe)
        grouped (group-by first pairs)]
    (into {}
          (keep (fn [[symbol* matches]]
                  (when (= 1 (count (distinct (map second matches))))
                    [symbol* (second (first matches))])))
          grouped)))

(defn- resolve-instrument-id
  [ids by-symbol entry]
  (let [entry-id (coercion/non-blank-text (entry-field entry :instrument-id))
        entry-symbol (coercion/non-blank-text (entry-field entry :symbol))]
    (or (when (contains? ids entry-id) entry-id)
        (when entry-symbol (get by-symbol (str/upper-case entry-symbol))))))

(defn- existing-absolute-view
  [views instrument-id]
  (some (fn [view]
          (when (and (= :absolute (coercion/normalize-keyword-like (:kind view)))
                     (= instrument-id (:instrument-id view)))
            view))
        views))

(defn- entry-confidence-level
  "The entry's confidence when it names a valid level; nil otherwise (the
  caller falls back to the existing view's level, then medium)."
  [entry]
  (let [raw (entry-field entry :confidence :confidence-level)
        level (coercion/normalize-keyword-like raw)]
    (when (contains? bl-model/confidence-weight-by-level level)
      level)))

(defn- upsert-imported-view
  "Author/update the absolute view for one imported entry. Mirrors the shape the
  in-panel row edits produce so an imported view is indistinguishable from a
  typed one."
  [views instrument-id return-value confidence-level]
  (let [existing (existing-absolute-view views instrument-id)
        level (bl-model/normalize-confidence-level
               (or confidence-level
                   (when existing
                     (or (:confidence-level existing)
                         (bl-model/confidence-level-from-view existing)))))
        confidence (bl-model/confidence-weight level)
        view (if existing
               (assoc existing
                      :return return-value
                      :confidence-level level
                      :confidence confidence
                      :confidence-variance (bl-model/confidence-variance confidence))
               {:id (bl-model/next-view-id views)
                :kind :absolute
                :instrument-id instrument-id
                :return return-value
                :confidence-level level
                :confidence confidence
                :confidence-variance (bl-model/confidence-variance confidence)
                :horizon :3m
                :weights {instrument-id 1}})]
    {:views (if existing
              (mapv (fn [candidate]
                      (if (= (:id existing) (:id candidate)) view candidate))
                    views)
              (conj views view))
     :view view}))

(defn import-plan
  "Decide what an imported file does to the draft's views.

  Returns {:status :error :reason :invalid|:empty} for unusable/entry-less
  files, otherwise
  {:status :ok
   :views <updated draft views>
   :upserts [{:instrument-id …} …]   ; view-library sync payloads
   :applied-instrument-ids [ … ]     ; for typing-buffer cleanup
   :applied n :unchanged u :unknown k}.

  Only entries with a finite `return-percent` (number, \"12.5\", \"12.5%\") are
  applied; null/blank returns leave the asset untouched (:unchanged) and
  unresolvable assets are skipped (:unknown). Import never removes a view."
  [{:keys [views universe data]}]
  (if-let [entries (extract-entries data)]
    (if (empty? entries)
      {:status :error :reason :empty}
      (let [ids (universe-instrument-ids universe)
            by-symbol (instrument-id-by-symbol universe)]
        (reduce
         (fn [acc entry]
           (let [instrument-id (resolve-instrument-id ids by-symbol entry)
                 return-value (coercion/parse-percent-text
                               (entry-field entry :return-percent :return))]
             (cond
               (nil? instrument-id)
               (update acc :unknown inc)

               (nil? return-value)
               (update acc :unchanged inc)

               :else
               (let [{views* :views view :view}
                     (upsert-imported-view (:views acc)
                                           instrument-id
                                           return-value
                                           (entry-confidence-level entry))]
                 (-> acc
                     (assoc :views views*)
                     (update :upserts conj {:instrument-id (:instrument-id view)
                                            :return (:return view)
                                            :confidence-level (:confidence-level view)})
                     (update :applied-instrument-ids conj instrument-id)
                     (update :applied inc))))))
         {:status :ok
          :views (vec (or views []))
          :upserts []
          :applied-instrument-ids []
          :applied 0
          :unchanged 0
          :unknown 0}
         entries)))
    {:status :error :reason :invalid}))

(defn- pluralize-views
  [n]
  (if (= 1 n) (str n " view") (str n " views")))

(defn export-success-message
  [n]
  (str "Exported " (pluralize-views n) " to a JSON file — fill `return-percent` offline and import it here."))

(def export-empty-message
  "Add assets to the universe before exporting return views.")

(defn import-success-message
  [{:keys [applied unchanged unknown]}]
  (if (zero? (or applied 0))
    (str "No views applied — every `return-percent` was empty."
         (when (pos? (or unknown 0))
           (str " " unknown " unknown "
                (if (= 1 unknown) "asset" "assets") " skipped.")))
    (str/join " · "
              (keep identity
                    [(str "Applied " (pluralize-views applied))
                     (when (pos? (or unchanged 0))
                       (str unchanged " left as-is"))
                     (when (pos? (or unknown 0))
                       (str unknown " unknown "
                            (if (= 1 unknown) "asset" "assets")
                            " skipped"))]))))

(defn import-error-message
  [reason]
  (case reason
    :empty "Import failed: no entries found in the file."
    :views-off "Return views are off for this return model — switch to Maximum Sharpe (views) before importing."
    "Import failed: the file is not valid return-views JSON."))
