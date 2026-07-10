(ns hyperopen.portfolio.optimizer.application.default-assumptions
  "Pure model for backend-recommended history assumptions.

  Instrument discovery rows can carry a `default-assumption` block — a
  server-generated modeling recommendation for a short-history instrument:
  model it as a basket of OTHER instruments (`proxy`, with an economic prior
  and a user-facing rationale), or treat it conservatively when no defensible
  basket exists. This namespace turns those blocks into the exact draft
  entries the manual card flow and the agent-file import produce; the action
  layer supplies state access and effects.

  Honesty rules, inherited from the backend contract and the import precedent:
  - The served prior is the members' `weight` (equal weight when absent). The
    block's offline `fit-evidence` is provenance only and is never read.
  - A member whose history the backend cannot serve yet (discovery history
    status missing/rejected) is HELD BACK and disclosed, never silently
    applied — a basket member the engine cannot load would park the card in a
    permanently blocked state.
  - A recommendation never overwrites an existing draft entry: the user's
    authored assumption always wins."
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.domain.history-assumptions :as history-assumptions]))

(defn discovery-instrument
  "Normalized discovery row for a backend instrument id, or nil."
  [discovery backend-id]
  (when (coercion/non-blank-text backend-id)
    (get-in discovery [:instruments-by-backend-id backend-id])))

(defn- normalized-member
  [member]
  (when-let [id (coercion/non-blank-text (:instrument-id member))]
    {:instrument-id id
     :weight (let [weight (:weight member)]
               (when (coercion/finite-number? weight) weight))
     :role (coercion/non-blank-text (:role member))}))

(defn recommendation
  "The backend's default-assumption recommendation for a backend instrument
  id, normalized for the frontend: keyword approach/strength, member list with
  finite weights only. Nil when discovery carries none, or an unusable one (an
  unknown approach, or a proxy approach with no members)."
  [discovery backend-id]
  (let [row (discovery-instrument discovery backend-id)
        rec (:default-assumption row)]
    (when (map? rec)
      (let [approach (coercion/normalize-keyword-like (:approach rec))
            members (into [] (keep normalized-member) (:members rec))
            strength (let [value (coercion/normalize-keyword-like
                                  (:relationship-strength rec))]
                       (when (contains? history-assumptions/relationship-strengths
                                        value)
                         value))]
        (when (and (contains? history-assumptions/behaviors approach)
                   (or (= :conservative approach) (seq members)))
          {:approach approach
           :members members
           :relationship-strength strength
           :rationale (coercion/non-blank-text (:rationale rec))
           :review-status (coercion/normalize-keyword-like (:review-status rec))})))))

;; --- Members ------------------------------------------------------------------

(defn- external-proxy-row?
  [row]
  (or (true? (:basket-member-only row))
      (= :external-proxy
         (coercion/normalize-keyword-like (:instrument-kind row)))))

(defn external-reference-instrument
  "Universe-instrument-shaped map for an external-proxy discovery row (a
  basket-member-only row with no tradable market behind it). The backend id
  doubles as the local instrument id — external rows have no market key — and
  the :optimizer-history/* identity rides along so history loading and
  alignment can map the stored instrument to its backend series."
  [row]
  (let [id (coercion/non-blank-text (:instrument-id row))
        symbol (or (coercion/non-blank-text (:display-symbol row))
                   (some-> id (str/split #":") last))]
    (when (and id symbol (external-proxy-row? row))
      {:instrument-id id
       :market-type :external
       :coin symbol
       :symbol symbol
       :shortable? false
       :position-side :long
       :optimizer-history/instrument-id id
       :optimizer-history/display-symbol symbol
       :optimizer-history/instrument-kind (coercion/normalize-keyword-like
                                           (:instrument-kind row))
       :optimizer-history/history-status (coercion/normalize-keyword-like
                                          (get-in row [:history :status]))})))

(def ^:private unservable-history-statuses
  ;; Discovery history statuses the request-time serve cannot back today. A
  ;; member in this state would fetch a tiny/empty series, fail the usable-
  ;; proxy gate, and block the card — so it is held back instead of applied.
  #{:missing :rejected})

(defn- servable-member-row?
  [row]
  (and (map? row)
       (not (contains? unservable-history-statuses
                       (coercion/normalize-keyword-like
                        (get-in row [:history :status]))))))

(defn- member-local-id
  "Local (frontend) instrument id for a member's discovery row: the Hyperopen
  market key for tradable rows, the backend id itself for external rows."
  [row backend-id]
  (or (coercion/non-blank-text (get-in row [:aliases :hyperopen-market-key]))
      backend-id))

(defn member-availability
  "Splits a recommendation's members by whether the backend can serve their
  history today. Every member gains :instrument-id (local), :backend-id,
  :label (discovery display symbol fallback), and :external?. Self-references
  are dropped outright — an asset can never proxy itself."
  [discovery target-instrument-id members]
  (reduce
   (fn [acc {:keys [instrument-id weight role]}]
     (let [row (discovery-instrument discovery instrument-id)
           local-id (member-local-id row instrument-id)
           member {:instrument-id local-id
                   :backend-id instrument-id
                   :weight weight
                   :role role
                   :label (or (coercion/non-blank-text (:display-symbol row))
                              local-id)
                   :external? (boolean (and row (external-proxy-row? row)))}]
       (cond
         (= local-id target-instrument-id) acc
         (servable-member-row? row) (update acc :usable conj member)
         :else (update acc :held conj member))))
   {:usable []
    :held []}
   members))

;; --- Entries ------------------------------------------------------------------

(defn- recommendation-metadata
  [rationale]
  (cond-> {:source :backend-recommendation
           :acknowledged? true}
    rationale (assoc :rationale rationale)))

(defn- prior-weights
  "Members' economic prior keyed by local id, normalized to sum 1. All-or-
  nothing like the import channel: any member without a finite >= 0 weight (or
  a non-positive total) collapses the basket to nil = equal weight."
  [members]
  (let [weights (map :weight members)]
    (when (and (seq members)
               (every? #(and (coercion/finite-number? %) (>= % 0)) weights)
               (pos? (reduce + 0 weights)))
      (let [total (reduce + 0 weights)]
        (into {}
              (map (fn [{:keys [instrument-id weight]}]
                     [instrument-id (/ weight total)]))
              members)))))

(defn conservative-entry
  "Conservative draft entry seeded from the behavior defaults, carrying the
  recommendation's rationale as provenance."
  [rec]
  (-> (history-assumptions/default-assumption :conservative)
      (assoc :metadata (recommendation-metadata (:rationale rec)))))

(defn proxy-entry
  "Proxy draft entry over the usable members: the same shape the manual card
  flow builds, with the backend's relationship strength and economic prior."
  [rec usable-members]
  (-> (history-assumptions/default-assumption :proxy)
      (assoc-in [:proxy :instrument-ids] (mapv :instrument-id usable-members))
      (assoc-in [:proxy :relationship-strength]
                (or (:relationship-strength rec)
                    history-assumptions/default-relationship-strength))
      (assoc-in [:proxy :prior-weights] (prior-weights usable-members))
      (assoc :metadata (recommendation-metadata (:rationale rec)))))

;; --- Plan ---------------------------------------------------------------------

(defn- plan-proxy-target
  [acc {:keys [discovery universe-ids resolve-member]} instrument-id rec]
  (let [{:keys [usable held]} (member-availability discovery instrument-id
                                                   (:members rec))
        resolved (reduce (fn [resolved member]
                           (if (contains? universe-ids (:instrument-id member))
                             (assoc resolved (:instrument-id member) :universe)
                             (if-let [instrument (resolve-member member)]
                               (assoc resolved (:instrument-id member) instrument)
                               resolved)))
                         {}
                         usable)
        kept (filterv #(contains? resolved (:instrument-id %)) usable)
        ;; A usable member the frontend cannot materialize (no catalog market,
        ;; no external discovery row) is held like a data-less one.
        held* (into (vec held)
                    (remove #(contains? resolved (:instrument-id %)))
                    usable)]
    (if (empty? kept)
      (update acc :skipped conj {:instrument-id instrument-id
                                 :reason :members-unavailable
                                 :held held*})
      (-> acc
          (assoc-in [:assumptions instrument-id] (proxy-entry rec kept))
          (update :applied conj {:instrument-id instrument-id :held held*})
          (update :new-reference-instruments into
                  (keep (fn [member]
                          (let [resolved-member (get resolved (:instrument-id member))]
                            (when (map? resolved-member) resolved-member)))
                        kept))))))

(defn recommendation-plan
  "Decides what applying the backend recommendations does to the draft.

  Inputs: :discovery (normalized discovery state), :assumptions (current
  draft map), :universe-ids (set of universe instrument ids), :resolve-member
  (fn member -> universe-instrument | nil, for usable members outside the
  universe), :targets (seq of {:instrument-id local :backend-id backend}).

  Returns {:assumptions <final map>
           :applied [{:instrument-id .. :held [..]}]
           :skipped [{:instrument-id .. :reason .. :held [..]}]
           :new-reference-instruments [..]} where :reason is
  :no-recommendation | :already-configured | :members-unavailable. Applying
  never removes or overwrites an existing entry."
  [{:keys [discovery assumptions targets] :as inputs}]
  (let [result
        (reduce
         (fn [acc {:keys [instrument-id backend-id]}]
           (let [rec (recommendation discovery backend-id)]
             (cond
               (nil? rec)
               (update acc :skipped conj {:instrument-id instrument-id
                                          :reason :no-recommendation})

               (contains? (:assumptions acc) instrument-id)
               (update acc :skipped conj {:instrument-id instrument-id
                                          :reason :already-configured})

               (= :conservative (:approach rec))
               (-> acc
                   (assoc-in [:assumptions instrument-id]
                             (conservative-entry rec))
                   (update :applied conj {:instrument-id instrument-id
                                          :held []}))

               :else
               (plan-proxy-target acc inputs instrument-id rec))))
         {:assumptions (or assumptions {})
          :applied []
          :skipped []
          :new-reference-instruments []}
         targets)]
    (update result :new-reference-instruments
            (fn [instruments]
              (into []
                    (comp (filter :instrument-id)
                          (distinct))
                    instruments)))))

;; --- Note messages --------------------------------------------------------------

(defn- pluralize-assumptions
  [n]
  (if (= 1 n)
    (str n " recommended assumption")
    (str n " recommended assumptions")))

(defn- held-labels
  [plan]
  (->> (concat (mapcat :held (:applied plan))
               (mapcat :held (:skipped plan)))
       (keep :label)
       distinct
       vec))

(defn apply-note-message
  "One-line outcome for the workflow's note strip: what applied, what is still
  waiting on backend member history, and which members were held back."
  [plan]
  (let [applied-count (count (:applied plan))
        unavailable (count (filter #(= :members-unavailable (:reason %))
                                   (:skipped plan)))
        labels (held-labels plan)
        held-note (when (seq labels)
                    (str "members awaiting backend history: "
                         (str/join ", " (take 6 labels))
                         (when (> (count labels) 6) ", …")))]
    (if (zero? applied-count)
      (str/join " — "
                (keep identity
                      ["No recommendations applied"
                       (when (pos? unavailable)
                         (str unavailable
                              (if (= 1 unavailable) " asset is" " assets are")
                              " waiting on backend member history"))
                       held-note]))
      (str/join " · "
                (keep identity
                      [(str "Applied " (pluralize-assumptions applied-count))
                       (when (pos? unavailable)
                         (str unavailable
                              (if (= 1 unavailable) " asset" " assets")
                              " left waiting on backend member history"))
                       held-note])))))
