(ns hyperopen.portfolio.optimizer.application.history-merge
  "Folding a fetched history bundle into the cached history data (split from
  application.history-workflow when the delta-fetch merge rules pushed it past
  the namespace size gate, 2026-07-08). The api-v2 rules are the load-bearing
  part: a delta response's calendars and aligned-returns answer only ITS
  instruments' intersection and must never mix with rows aligned to a
  different calendar.")

(defn- same-api-v2-calendars?
  [existing incoming]
  (and (= (vec (or (:common-calendar existing) []))
          (vec (or (:common-calendar incoming) [])))
       (= (vec (or (:return-calendar existing) []))
          (vec (or (:return-calendar incoming) [])))))

(defn- full-api-v2-refresh?
  "An incoming response whose series cover every existing instrument is a full
  refresh (the full-universe refetch path), not a delta."
  [existing incoming]
  (let [incoming-ids (set (keys (or (:series-by-instrument incoming) {})))]
    (every? incoming-ids (keys (or (:series-by-instrument existing) {})))))

(defn- merge-api-v2-history
  "Fold an incoming api-v2 response into the cached one. A full refresh
  replaces wholesale. A DELTA merges only its series and keeps the existing
  calendars/metadata: the delta's calendars describe just the delta
  instruments' intersection, and its aligned-returns answer that calendar —
  mixing them with rows aligned to a different calendar would silently
  misalign the joint return matrix. Aligned rows merge only when both
  calendars match exactly; otherwise the client-side point-level alignment
  (the calendar-poisoning fallback) realigns the merged series."
  [existing incoming]
  (let [existing* (or existing {})]
    (cond
      (empty? existing*)
      incoming

      (full-api-v2-refresh? existing* incoming)
      (update incoming
              :warnings
              #(vec (concat (or (:warnings existing*) []) (or % []))))

      :else
      (-> existing*
          (assoc :series-by-instrument
                 (merge (or (:series-by-instrument existing*) {})
                        (or (:series-by-instrument incoming) {})))
          (cond-> (same-api-v2-calendars? existing* incoming)
            (assoc :aligned-returns-by-instrument
                   (merge (or (:aligned-returns-by-instrument existing*) {})
                          (or (:aligned-returns-by-instrument incoming) {}))))
          (update :warnings
                  #(vec (concat (or % []) (or (:warnings incoming) []))))))))

(defn merge-history-bundle
  [history-data bundle completed-at-ms]
  (-> (or history-data {})
      (update :candle-history-by-coin
              merge
              (or (:candle-history-by-coin bundle) {}))
      (update :funding-history-by-coin
              merge
              (or (:funding-history-by-coin bundle) {}))
      (update :vault-details-by-address
              merge
              (or (:vault-details-by-address bundle) {}))
      (update :api-v2-history
              (fn [existing]
                (if-let [api-v2-history (:api-v2-history bundle)]
                  (merge-api-v2-history existing api-v2-history)
                  existing)))
      (update :warnings
              #(vec (concat (or % []) (or (:warnings bundle) []))))
      (assoc :loaded-at-ms completed-at-ms)))
