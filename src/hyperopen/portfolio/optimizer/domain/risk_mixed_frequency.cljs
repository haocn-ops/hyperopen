(ns hyperopen.portfolio.optimizer.domain.risk-mixed-frequency
  (:require [clojure.set :as set]
            [hyperopen.portfolio.metrics.history :as metrics-history]
            [hyperopen.portfolio.optimizer.domain.history-series :as history-series]
            [hyperopen.portfolio.optimizer.domain.math :as math]
            [hyperopen.portfolio.optimizer.domain.risk-ledoit-wolf :as risk-ledoit-wolf]))

(def ^:private sparse-correlation-prior-observations
  30)

(def ^:private dense-block-min-return-observations
  2)

(defn- pair-key
  [left-id right-id]
  (str left-id "|" right-id))

(defn price-history
  [history instrument-id]
  (let [raw-series-by-instrument (:raw-price-series-by-instrument history)]
    (vec (or (get raw-series-by-instrument instrument-id)
             (when (empty? raw-series-by-instrument)
               (get-in history [:price-series-by-instrument instrument-id]))))))

(defn cadence-for
  [history instrument-id]
  (or (get-in history [:cadence-by-instrument instrument-id])
      (history-series/cadence-summary (price-history history instrument-id))))

(defn cadence-by-instrument
  [history instrument-ids]
  (into {}
        (map (fn [instrument-id]
               [instrument-id (cadence-for history instrument-id)]))
        instrument-ids))

(defn- sparse-cadence?
  [cadence]
  (boolean (:sparse? cadence)))

(defn- dense-cadence?
  [cadence]
  (not (sparse-cadence? cadence)))

(defn- dense-instrument-ids
  [history instrument-ids]
  (filterv #(dense-cadence? (cadence-for history %)) instrument-ids))

(defn- row-times
  [rows]
  (mapv :time-ms rows))

(defn- rows-for-times
  [rows times]
  (let [by-time (into {}
                      (map (juxt :time-ms identity))
                      rows)]
    (mapv by-time times)))

(defn- common-row-times
  [rows-by-id instrument-ids]
  (let [time-sets (map #(set (row-times (get rows-by-id %)))
                       instrument-ids)]
    (if (seq time-sets)
      (->> (apply set/intersection time-sets)
           sort
           vec)
      [])))

(defn- dense-price-rows-by-id
  [history instrument-ids]
  (into {}
        (keep (fn [instrument-id]
                (when-let [rows (seq (price-history history instrument-id))]
                  [instrument-id (vec rows)])))
        instrument-ids))

(defn- dense-native-return-series-by-id
  [history instrument-ids]
  (let [rows-by-id (dense-price-rows-by-id history instrument-ids)
        missing-ids (filterv #(not (contains? rows-by-id %)) instrument-ids)
        common-times (if (seq missing-ids)
                       []
                       (common-row-times rows-by-id instrument-ids))
        series-by-id (if (seq missing-ids)
                       {}
                       (into {}
                             (map (fn [instrument-id]
                                    [instrument-id
                                     (history-series/simple-return-series
                                      (rows-for-times
                                       (get rows-by-id instrument-id)
                                       common-times))]))
                             instrument-ids))
        sample-counts-by-id (into {}
                                  (map (fn [instrument-id]
                                         [instrument-id
                                          (count (get series-by-id
                                                      instrument-id))]))
                                  instrument-ids)]
    {:series-by-id series-by-id
     :missing-ids missing-ids
     :common-times common-times
     :sample-counts-by-id sample-counts-by-id}))

(defn- rectangular-series?
  [series-by-id instrument-ids]
  (let [series (mapv series-by-id instrument-ids)
        sample-counts (mapv count series)]
    (and (seq instrument-ids)
         (every? some? series)
         (apply = sample-counts))))

(defn- dense-ledoit-wolf-block
  [history instrument-ids periods-per-year]
  (let [dense-ids (dense-instrument-ids history instrument-ids)
        {:keys [series-by-id
                missing-ids
                sample-counts-by-id]}
        (dense-native-return-series-by-id history dense-ids)
        sample-count (if (seq dense-ids)
                       (count (get series-by-id (first dense-ids)))
                       0)]
    (cond
      (empty? dense-ids)
      {:block nil
       :warnings []}

      (seq missing-ids)
      {:block nil
       :warnings [{:code :dense-block-ledoit-wolf-unavailable
                   :reason :missing-dense-native-price-series
                   :instrument-ids missing-ids}]}

      (not (rectangular-series? series-by-id dense-ids))
      {:block nil
       :warnings [{:code :dense-block-ledoit-wolf-unavailable
                   :reason :non-rectangular-dense-return-series
                   :instrument-ids dense-ids
                   :sample-counts-by-instrument sample-counts-by-id}]}

      (< sample-count dense-block-min-return-observations)
      {:block nil
       :warnings [{:code :dense-block-ledoit-wolf-unavailable
                   :reason :insufficient-dense-return-observations
                   :instrument-ids dense-ids
                   :observations sample-count
                   :required dense-block-min-return-observations}]}

      :else
      (let [estimate (risk-ledoit-wolf/estimate
                      {:series (mapv series-by-id dense-ids)
                       :periods-per-year periods-per-year})]
        {:block (assoc estimate
                       :instrument-ids dense-ids
                       :index-by-id (zipmap dense-ids (range)))
         :warnings []}))))

(defn mixed-frequency?
  [requested-kind history instrument-ids]
  (or (= :mixed-frequency requested-kind)
      (= :mixed-frequency (get-in history [:risk-estimation :kind]))
      (some (comp sparse-cadence? #(cadence-for history %)) instrument-ids)))

(defn instrument-ids
  [history all-instrument-ids]
  (if (seq (:raw-price-series-by-instrument history))
    (filterv #(seq (price-history history %)) all-instrument-ids)
    all-instrument-ids))

(defn warnings
  [requested-kind cadence-by-instrument]
  (cond-> []
    (= :ledoit-wolf requested-kind)
    (conj {:code :risk-model-renamed
           :from :ledoit-wolf
           :to :diagonal-shrink})

    (seq cadence-by-instrument)
    (into (keep (fn [[instrument-id cadence]]
                  (when (:sparse? cadence)
                    {:code :sparse-history-risk-estimation
                     :instrument-id instrument-id
                     :observations (:observations cadence)
                     :interval-count (:interval-count cadence)
                     :elapsed-days (:elapsed-days cadence)
                     :policy :pairwise-interval-aggregation
                     :message (str "sparse history uses mixed-frequency covariance with "
                                   (:interval-count cadence)
                                   " intervals across "
                                   (js/Math.round (or (:elapsed-days cadence) 0))
                                   " elapsed days. Dense assets are aggregated over each sparse interval.")})))
                cadence-by-instrument)))

(defn- missing-native-risk-history-warning
  [instrument-id]
  {:code :missing-native-risk-history
   :instrument-id instrument-id
   :policy :mixed-frequency-requires-native-price-series})

(defn missing-native-risk-history-warnings
  [history all-instrument-ids risk-instrument-ids]
  (when (seq (:raw-price-series-by-instrument history))
    (let [risk-instrument-set (set risk-instrument-ids)]
      (keep (fn [instrument-id]
              (when-not (contains? risk-instrument-set instrument-id)
                (missing-native-risk-history-warning instrument-id)))
            all-instrument-ids))))

(defn override-warning
  [model-kind]
  (when (not= :mixed-frequency model-kind)
    {:code :risk-model-overridden-for-mixed-frequency
     :requested-model model-kind
     :model :mixed-frequency
     :reason :sparse-history}))

(defn- intersect-times
  [left-rows right-rows]
  (->> (set/intersection (set (row-times left-rows))
                         (set (row-times right-rows)))
       sort
       vec))

(defn- pair-endpoints
  [left-rows right-rows left-cadence right-cadence]
  (cond
    (and (sparse-cadence? left-cadence)
         (sparse-cadence? right-cadence))
    {:calendar-kind :sparse-interval
     :endpoints (intersect-times left-rows right-rows)}

    (sparse-cadence? left-cadence)
    {:calendar-kind :sparse-interval
     :endpoints (row-times left-rows)}

    (sparse-cadence? right-cadence)
    {:calendar-kind :sparse-interval
     :endpoints (row-times right-rows)}

    :else
    {:calendar-kind :daily
     :endpoints (intersect-times left-rows right-rows)}))

(defn- row-at-or-before
  [rows target-ms]
  (reduce (fn [best row]
            (if (<= (:time-ms row) target-ms)
              row
              (reduced best)))
          nil
          rows))

(defn- endpoint-staleness-limit-ms
  [start-ms end-ms]
  (max (* 2 metrics-history/day-ms)
       (- end-ms start-ms)))

(defn- interval-log-return
  [rows start-ms end-ms]
  (let [start-row (row-at-or-before rows start-ms)
        end-row (row-at-or-before rows end-ms)
        staleness-limit-ms (endpoint-staleness-limit-ms start-ms end-ms)
        start-age-ms (when (and start-row (number? start-ms))
                       (- start-ms (:time-ms start-row)))
        end-age-ms (when (and end-row (number? end-ms))
                     (- end-ms (:time-ms end-row)))
        start-close (:close start-row)
        end-close (:close end-row)]
    (when (and start-row
               end-row
               (math/finite-number? start-age-ms)
               (math/finite-number? end-age-ms)
               (<= start-age-ms staleness-limit-ms)
               (<= end-age-ms staleness-limit-ms)
               (< (:time-ms start-row) (:time-ms end-row))
               (math/finite-number? start-close)
               (math/finite-number? end-close)
               (pos? start-close)
               (pos? end-close))
      (js/Math.log (/ end-close start-close)))))

(defn- pair-observations
  [left-rows right-rows endpoints]
  (->> (partition 2 1 endpoints)
       (keep (fn [[start-ms end-ms]]
               (let [dt-days (/ (- end-ms start-ms)
                                metrics-history/day-ms)
                     dt-years (/ dt-days history-series/year-days)
                     left-return (interval-log-return left-rows start-ms end-ms)
                     right-return (interval-log-return right-rows start-ms end-ms)]
                 (when (and (math/finite-number? dt-years)
                            (pos? dt-years)
                            (math/finite-number? left-return)
                            (math/finite-number? right-return))
                   {:start-ms start-ms
                    :end-ms end-ms
                    :dt-days dt-days
                    :dt-years dt-years
                    :left-return left-return
                    :right-return right-return}))))
       vec))

(defn- annualized-interval-covariance
  [observations]
  (let [n (count observations)
        total-years (reduce + 0 (map :dt-years observations))
        total-left (reduce + 0 (map :left-return observations))
        total-right (reduce + 0 (map :right-return observations))
        mean-left (when (pos? total-years)
                    (/ total-left total-years))
        mean-right (when (pos? total-years)
                     (/ total-right total-years))]
    (when (and (> n 1)
               (math/finite-number? mean-left)
               (math/finite-number? mean-right))
      (/ (reduce + 0
                 (map (fn [{:keys [left-return right-return dt-years]}]
                        (let [left-excess (- left-return (* mean-left dt-years))
                              right-excess (- right-return (* mean-right dt-years))]
                          (/ (* left-excess right-excess)
                             dt-years)))
                      observations))
         (dec n)))))

(defn- sparse-involved?
  [left-cadence right-cadence]
  (or (sparse-cadence? left-cadence)
      (sparse-cadence? right-cadence)))

(defn- correlation-retention
  [observations sparse-involved? diagonal?]
  (if (or diagonal? (not sparse-involved?))
    1
    (/ observations
       (+ observations sparse-correlation-prior-observations))))

(defn- insufficient-pairwise-warning
  [left-id right-id observations]
  {:code :insufficient-pairwise-history
   :left-instrument-id left-id
   :right-instrument-id right-id
   :instrument-ids [left-id right-id]
   :observations observations
   :required 2
   :message (str left-id " / " right-id
                 ": mixed-frequency covariance only had "
                 observations
                 " shared intervals; 2 required.")})

(defn- dense-block-covariance
  [dense-block left-id right-id]
  (when (and dense-block
             (contains? (:index-by-id dense-block) left-id)
             (contains? (:index-by-id dense-block) right-id))
    (let [left-idx (get (:index-by-id dense-block) left-id)
          right-idx (get (:index-by-id dense-block) right-id)]
      (get-in dense-block [:covariance left-idx right-idx]))))

(defn- dense-block-pair-estimate
  [dense-block left-id right-id]
  (when-let [covariance (dense-block-covariance dense-block left-id right-id)]
    {:covariance covariance
     :metadata {:calendar-kind :daily
                :observations (:sample-count dense-block)
                :correlation-retention 1
                :estimator :ledoit-wolf-dense
                :dense-block? true
                :target (get-in dense-block [:shrinkage :target])
                :shrinkage (get-in dense-block [:shrinkage :shrinkage])}
     :warnings []}))

(defn- pair-estimate
  [history left-id right-id]
  (let [left-rows (price-history history left-id)
        right-rows (price-history history right-id)
        left-cadence (cadence-for history left-id)
        right-cadence (cadence-for history right-id)
        diagonal? (= left-id right-id)
        sparse-pair? (sparse-involved? left-cadence right-cadence)
        {:keys [calendar-kind endpoints]}
        (pair-endpoints left-rows right-rows left-cadence right-cadence)
        observations* (pair-observations left-rows right-rows endpoints)
        observation-count (count observations*)
        retention (correlation-retention observation-count sparse-pair? diagonal?)
        covariance (if (> observation-count 1)
                     (* retention
                        (or (annualized-interval-covariance observations*) 0))
                     0)]
    {:covariance covariance
     :metadata {:calendar-kind calendar-kind
                :observations observation-count
                :correlation-retention retention
                :estimator :pairwise-mixed-frequency}
     :warnings (cond-> []
                 (< observation-count 2)
                 (conj (insufficient-pairwise-warning left-id
                                                      right-id
                                                      observation-count)))}))

(defn risk-estimation
  [history]
  (let [estimated (history-series/risk-estimation
                   (or (:cadence-by-instrument history) {}))]
    (or (when (map? estimated)
          (assoc estimated :kind :mixed-frequency))
        {:kind :mixed-frequency
         :dense-block-calendar :daily
         :sparse-policy :pairwise-interval-aggregation
         :sparse-correlation-shrinkage true})))

(defn matrix
  ([history instrument-ids]
   (matrix history instrument-ids {}))
  ([history instrument-ids {:keys [dense-block-estimator periods-per-year]}]
   (let [{:keys [block warnings]} (if (= :ledoit-wolf-dense
                                         dense-block-estimator)
                                    (dense-ledoit-wolf-block history
                                                             instrument-ids
                                                             (or periods-per-year 365))
                                    {:block nil
                                     :warnings []})]
     (reduce (fn [{matrix* :covariance
                   metadata-by-pair :pair-metadata
                   collected-warnings :warnings
                   dense-block :dense-block}
                  [row-idx col-idx]]
               (let [left-id (nth instrument-ids row-idx)
                     right-id (nth instrument-ids col-idx)
                     {pair-covariance :covariance
                      pair-metadata :metadata
                      pair-warnings :warnings}
                     (or (dense-block-pair-estimate block left-id right-id)
                         (pair-estimate history left-id right-id))
                     matrix** (assoc-in matrix* [row-idx col-idx] pair-covariance)
                     matrix** (assoc-in matrix** [col-idx row-idx] pair-covariance)
                     pair-metadata* (if (= left-id right-id)
                                      metadata-by-pair
                                      (assoc metadata-by-pair
                                             (pair-key left-id right-id)
                                             pair-metadata))]
                 {:covariance matrix**
                  :pair-metadata pair-metadata*
                  :warnings (into collected-warnings pair-warnings)
                  :dense-block dense-block}))
             {:covariance (vec (repeat (count instrument-ids)
                                       (vec (repeat (count instrument-ids) 0))))
              :pair-metadata {}
              :warnings warnings
              :dense-block (when block
                             (select-keys block
                                          [:instrument-ids
                                           :shrinkage
                                           :sample-count
                                           :feature-count]))}
             (for [row-idx (range (count instrument-ids))
                   col-idx (range row-idx (count instrument-ids))]
               [row-idx col-idx])))))
