(ns hyperopen.portfolio.optimizer.application.return-views
  "Pure row/summary models for the Return views panel. Every universe asset gets a
  row whose provenance is explicit: :user when a draft view exists for it (the
  user authored a return), :implied otherwise (the optimizer falls back to the
  stabilized baseline estimate). Ages come from the per-wallet view library;
  `now-ms` is always supplied by the caller so this namespace stays clock-free."
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.black-litterman-editor-model :as bl-model]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def stale-after-ms
  "A user view older than this is flagged stale (30 days)."
  (* 30 24 60 60 1000))

(def ^:private day-ms
  (* 24 60 60 1000))

(def filters
  [[:all "All"]
   [:yours "Your views"]
   [:implied "Implied"]
   [:stale "Stale"]])

(def filter-keys
  (set (map first filters)))

(defn normalize-filter
  [value]
  (let [value* (coercion/normalize-keyword-like value)]
    (if (contains? filter-keys value*)
      value*
      :all)))

(defn absolute-views-by-instrument
  [views]
  (into {}
        (keep (fn [view]
                (when (and (= :absolute (coercion/normalize-keyword-like (:kind view)))
                           (coercion/non-blank-text (:instrument-id view)))
                  [(:instrument-id view) view])))
        views))

(defn age-label
  "Relative age for a view timestamp: \"today\", \"1d ago\", \"42d ago\". Nil when
  the timestamp is unknown (e.g. a view restored from a scenario on a device that
  never edited it)."
  [updated-at-ms now-ms]
  (when (and (number? updated-at-ms)
             (number? now-ms)
             (<= updated-at-ms now-ms))
    (let [days (js/Math.floor (/ (- now-ms updated-at-ms) day-ms))]
      (cond
        (< days 1) "today"
        (= days 1) "1d ago"
        :else (str days "d ago")))))

(defn stale?
  [updated-at-ms now-ms]
  (boolean (and (number? updated-at-ms)
                (number? now-ms)
                (>= (- now-ms updated-at-ms) stale-after-ms))))

(defn- view-confidence-level
  [view]
  (bl-model/normalize-confidence-level
   (or (:confidence-level view)
       (bl-model/confidence-level-from-view view))))

(defn row
  "Provenance row for one universe instrument.

  :source          :user | :implied
  :return          decimal the optimizer would use for this asset (view return for
                   :user rows, baseline input for :implied rows; nil while history
                   is still loading)
  :confidence-level set only on :user rows — implied rows have no confidence, and
                   rendering one (the old always-selected \"M\") is the lie this
                   panel exists to remove
  :age-label/:stale? from the view library timestamp when known"
  [{:keys [instrument-id view library-entry implied-return now-ms]}]
  (let [user? (some? view)
        updated-at-ms (:updated-at-ms library-entry)]
    (cond-> {:instrument-id instrument-id
             :source (if user? :user :implied)
             :return (if user? (:return view) implied-return)}
      user? (assoc :confidence-level (view-confidence-level view)
                   :age-label (age-label updated-at-ms now-ms)
                   :stale? (stale? updated-at-ms now-ms)))))

(defn rows
  "Rows for every universe instrument, in universe order."
  [{:keys [universe views library-entries implied-returns-by-instrument now-ms]}]
  (let [views-by-id (absolute-views-by-instrument views)]
    (vec
     (keep (fn [instrument]
             (when-let [instrument-id (coercion/non-blank-text (:instrument-id instrument))]
               (row {:instrument-id instrument-id
                     :view (get views-by-id instrument-id)
                     :library-entry (get library-entries instrument-id)
                     :implied-return (get implied-returns-by-instrument instrument-id)
                     :now-ms now-ms})))
           universe))))

(defn matches-filter?
  [row* filter-key]
  (case (normalize-filter filter-key)
    :yours (= :user (:source row*))
    :implied (= :implied (:source row*))
    :stale (boolean (:stale? row*))
    true))

(defn summary
  "{:total N :yours y :implied i :stale s} over the provenance rows."
  [rows*]
  {:total (count rows*)
   :yours (count (filter #(= :user (:source %)) rows*))
   :implied (count (filter #(= :implied (:source %)) rows*))
   :stale (count (filter :stale? rows*))})

(defn summary-line
  "\"3 your views · 11 implied · 1 stale\" (stale segment only when present).
  \"Implied returns for all 14 assets\" when nothing is authored yet."
  [{:keys [total yours implied stale]}]
  (if (zero? (or yours 0))
    (str "Implied returns for all " total " assets")
    (->> [(str yours " your " (if (= 1 yours) "view" "views"))
          (str implied " implied")
          (when (pos? (or stale 0)) (str stale " stale"))]
         (keep identity)
         (str/join " · "))))

(defn returns-contract-label
  "Compact returns-source line for the scenario contract and preset card."
  [{:keys [yours implied]}]
  (if (zero? (or yours 0))
    "Implied baseline"
    (str yours " your " (if (= 1 yours) "view" "views") " · " implied " implied")))
