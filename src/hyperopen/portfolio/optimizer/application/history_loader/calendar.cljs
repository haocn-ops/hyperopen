(ns hyperopen.portfolio.optimizer.application.history-loader.calendar
  (:require [clojure.set :as set]
            [hyperopen.portfolio.metrics.history :as metrics-history]))

(defn row-by-time
  [rows]
  (into {}
        (map (juxt :time-ms identity))
        rows))

(defn common-calendar
  [histories]
  (let [sets (map #(set (keep :time-ms %)) histories)]
    (if (seq sets)
      (->> (apply set/intersection sets)
           sort
           vec)
      [])))

(defn- finite-number?
  [value]
  (and (number? value)
       (js/isFinite value)))

(defn point-return-map
  "Map of :time-ms -> finite :return for a series' point rows."
  [points]
  (into {}
        (keep (fn [{:keys [time-ms return]}]
                (when (and (number? time-ms)
                           (finite-number? return))
                  [time-ms return])))
        points))

(defn point-level-return-calendar
  "The subset of `calendar` timestamps at which EVERY member carries a finite
  return. `points-by-id` maps member id -> point rows."
  [points-by-id calendar]
  (let [return-maps (map point-return-map (vals points-by-id))]
    (->> calendar
         (filter (fn [time-ms]
                   (every? #(finite-number? (get % time-ms)) return-maps)))
         vec)))

(defn returns-from-point-level
  "Per-member return vectors sampled onto `return-calendar`."
  [points-by-id return-calendar]
  (into {}
        (map (fn [[id points]]
               (let [returns (point-return-map points)]
                 [id (mapv #(get returns %) return-calendar)])))
        points-by-id))

(defn- finite-return-times
  [points]
  (set (keys (point-return-map points))))

(defn- intersection-of
  [sets]
  (if (seq sets)
    (apply set/intersection sets)
    #{}))

(defn- best-peel-candidate
  "The member whose single removal yields the largest shared return set.
  Ties break to the member with the fewest own finite returns (the thinner
  series contributes less), then lexicographic id, for determinism. Scored
  with prefix/suffix intersections so one round costs O(n) intersections."
  [ordered-ids sets-by-id]
  (let [n (count ordered-ids)
        sets (mapv sets-by-id ordered-ids)
        prefixes (reduce (fn [acc s]
                           (conj acc (if-let [p (peek acc)]
                                       (set/intersection p s)
                                       s)))
                         []
                         (pop sets))
        suffixes (->> (reduce (fn [acc s]
                                (conj acc (if-let [p (peek acc)]
                                            (set/intersection p s)
                                            s)))
                              []
                              (reverse (subvec sets 1)))
                      reverse
                      vec)]
    (->> (map-indexed
          (fn [i id]
            (let [without (cond
                            (zero? i) (nth suffixes 0)
                            (= i (dec n)) (nth prefixes (dec i))
                            :else (set/intersection (nth prefixes (dec i))
                                                    (nth suffixes i)))]
              {:id id
               :score (count without)
               :own (count (get sets-by-id id))}))
          ordered-ids)
         (sort-by (juxt #(- (:score %)) :own :id))
         first
         :id)))

(defn peel-poisoning-members
  "When a member set's shared return calendar is below `min-return-observations`,
  peel members - each round removing the one whose removal most enlarges the
  shared calendar - until the calendar clears the bar or one member remains.
  A newly-listed asset whose few days of history are disjoint from a stale-ended
  series otherwise empties the intersection and (2026-07-08, live) excluded an
  entire 79-asset universe. Peeling deliberately does NOT require every step to
  improve: with two disjoint young members, removing either alone still leaves
  an empty intersection.

  `points-by-id` maps member id -> point rows ({:time-ms :return ...}).
  Returns nil when there is nothing to peel (calendar already sufficient) or no
  viable subset exists; otherwise {:kept-ids #{...}
                                   :peeled [{:instrument-id id :observations n} ...]
                                   :return-calendar [...]}
  where :observations is the member's finite-return overlap with the survivors'
  shared calendar."
  [points-by-id min-return-observations]
  (let [sets-by-id (into {}
                         (map (fn [[id points]]
                                [id (finite-return-times points)]))
                         points-by-id)]
    (loop [member-ids (vec (sort (keys sets-by-id)))
           peeled-ids []]
      (let [shared (intersection-of (map sets-by-id member-ids))]
        (cond
          (>= (count shared) min-return-observations)
          (when (seq peeled-ids)
            {:kept-ids (set member-ids)
             :peeled (mapv (fn [id]
                             {:instrument-id id
                              :observations (count (set/intersection
                                                    (get sets-by-id id)
                                                    shared))})
                           peeled-ids)
             :return-calendar (vec (sort shared))})

          (<= (count member-ids) 1)
          nil

          :else
          (let [worst (best-peel-candidate member-ids sets-by-id)]
            (recur (vec (remove #(= worst %) member-ids))
                   (conj peeled-ids worst))))))))

(defn return-intervals
  [calendar]
  (mapv (fn [[start-ms end-ms]]
          (let [dt-ms (- end-ms start-ms)
                dt-days (/ dt-ms metrics-history/day-ms)]
            {:start-ms start-ms
             :end-ms end-ms
             :dt-days dt-days
             :dt-years (/ dt-days 365.2425)}))
        (partition 2 1 calendar)))

(defn return-intervals-for-calendar
  [calendar return-calendar]
  (let [previous-by-end (into {}
                              (map (fn [[start-ms end-ms]]
                                     [end-ms start-ms]))
                              (partition 2 1 calendar))]
    (mapv (fn [end-ms]
            (let [start-ms (get previous-by-end end-ms)
                  dt-ms (when (and (number? start-ms)
                                   (number? end-ms))
                          (- end-ms start-ms))
                  dt-days (when (number? dt-ms)
                            (/ dt-ms metrics-history/day-ms))]
              {:start-ms start-ms
               :end-ms end-ms
               :dt-days dt-days
               :dt-years (when (number? dt-days)
                           (/ dt-days 365.2425))}))
          return-calendar)))

(def default-stale-after-ms
  ;; Aligned daily closes legitimately trail the run date by a few days
  ;; (weekends, market holidays, proxy serve cadence). Past a week the shared
  ;; covariance window is materially out of date — the same threshold as the
  ;; per-instrument stale-history incident escalation. Without a default at the
  ;; request-input seam, a nil runtime override makes `freshness` report
  ;; stale? false no matter how old the calendar is.
  (* 7 24 60 60 1000))

(defn freshness
  [calendar as-of-ms stale-after-ms]
  (let [latest-common-ms (last calendar)
        oldest-common-ms (first calendar)
        age-ms (when (and (number? as-of-ms)
                          (number? latest-common-ms))
                 (- as-of-ms latest-common-ms))
        stale? (if (number? latest-common-ms)
                 (and (number? stale-after-ms)
                      (number? age-ms)
                      (> age-ms stale-after-ms))
                 true)]
    {:as-of-ms as-of-ms
     :latest-common-ms latest-common-ms
     :oldest-common-ms oldest-common-ms
     :age-ms age-ms
     :stale? (boolean stale?)}))
