(ns hyperopen.portfolio.metrics.builder.benchmark
  (:require [hyperopen.portfolio.metrics.builder.core :as core]
            [hyperopen.portfolio.metrics.distribution :as distribution]
            [hyperopen.portfolio.metrics.history :as history]))

(defn- compound-returns
  [returns]
  (- (reduce (fn [acc value]
               (* acc (+ 1 value)))
             1
             returns)
     1))

(defn- benchmark-returns-in-interval
  [benchmark-rows start-ms end-ms]
  (->> benchmark-rows
       (keep (fn [{:keys [time-ms return]}]
               (when (and (> time-ms start-ms)
                          (<= time-ms end-ms)
                          (history/finite-number? return))
                 return)))
       vec))

(defn- align-sparse-interval-returns
  [strategy-intervals benchmark-daily-rows]
  (let [benchmark-rows (history/normalize-daily-rows benchmark-daily-rows)]
    (->> strategy-intervals
         (keep (fn [{:keys [start-ms end-ms simple-return] :as interval}]
                 (when (and (history/finite-number? start-ms)
                            (history/finite-number? end-ms)
                            (> end-ms start-ms)
                            (history/finite-number? simple-return))
                   (let [benchmark-returns (benchmark-returns-in-interval benchmark-rows
                                                                           start-ms
                                                                           end-ms)]
                     (when (seq benchmark-returns)
                       {:start-ms start-ms
                        :end-ms end-ms
                        :dt-days (:dt-days interval)
                        :strategy-return simple-return
                        :benchmark-return (compound-returns benchmark-returns)})))))
         vec)))

(defn- sparse-interval-history?
  [strategy-intervals]
  (boolean (some (fn [{:keys [dt-days]}]
                   (and (history/finite-number? dt-days)
                        (> dt-days 1.5)))
                 strategy-intervals)))

(defn- sparse-aligned-span-days
  [aligned-benchmark]
  (reduce (fn [acc {:keys [dt-days]}]
            (+ acc (if (history/finite-number? dt-days)
                     dt-days
                     0)))
          0
          aligned-benchmark))

(defn- sparse-benchmark-min?
  [aligned-benchmark gates]
  (let [min-points (max (:benchmark-min-points gates)
                        (:core-high-min-intervals gates))
        min-span-days (:core-high-min-span-days gates)]
    (and (>= (count aligned-benchmark) min-points)
         (>= (sparse-aligned-span-days aligned-benchmark)
             min-span-days))))

(defn build-benchmark-context
  [strategy-rows benchmark-daily-rows strategy-intervals gates]
  (let [aligned-benchmark (history/align-daily-returns strategy-rows benchmark-daily-rows)
        sparse-interval-history? (sparse-interval-history? strategy-intervals)
        sparse-aligned-benchmark (if sparse-interval-history?
                                   (align-sparse-interval-returns strategy-intervals
                                                                  benchmark-daily-rows)
                                   [])]
    {:aligned-benchmark aligned-benchmark
     :strategy-aligned (mapv :strategy-return aligned-benchmark)
     :benchmark-aligned (mapv :benchmark-return aligned-benchmark)
     :benchmark-min? (>= (count aligned-benchmark)
                         (:benchmark-min-points gates))
     :sparse-interval-history? sparse-interval-history?
     :sparse-aligned-benchmark sparse-aligned-benchmark
     :sparse-strategy-aligned (mapv :strategy-return sparse-aligned-benchmark)
     :sparse-benchmark-aligned (mapv :benchmark-return sparse-aligned-benchmark)
     :sparse-benchmark-min? (sparse-benchmark-min? sparse-aligned-benchmark
                                                   gates)}))

(defn- assoc-benchmark-metric
  [acc key metric-fn {:keys [aligned-benchmark
                            strategy-aligned
                            benchmark-aligned
                            benchmark-enabled?
                            benchmark-high-confidence?
                            sparse-interval-history?
                            sparse-aligned-benchmark
                            sparse-strategy-aligned
                            sparse-benchmark-aligned
                            sparse-benchmark-min?]}]
  (cond
    sparse-benchmark-min?
    (core/assoc-metric-result acc
                              key
                              (when (seq sparse-aligned-benchmark)
                                (metric-fn sparse-strategy-aligned sparse-benchmark-aligned))
                              true
                              :low-confidence
                              :benchmark-sparse-intervals)

    sparse-interval-history?
    (core/assoc-metric-result acc
                              key
                              nil
                              false
                              :ok
                              :benchmark-coverage-gate-failed)

    benchmark-enabled?
    (core/assoc-metric-result acc
                              key
                              (when (seq aligned-benchmark)
                                (metric-fn strategy-aligned benchmark-aligned))
                              true
                              (if benchmark-high-confidence?
                                :ok
                                :low-confidence)
                              (if benchmark-high-confidence?
                                :benchmark-coverage-gate-failed
                                :daily-coverage-gate-failed))

    :else
    (core/assoc-metric-result acc
                              key
                              nil
                              false
                              :ok
                              :benchmark-coverage-gate-failed)))

(defn add-benchmark-relative-metrics
  [acc {:keys [rf periods-per-year] :as context}]
  (-> acc
      (assoc-benchmark-metric :r2 distribution/r-squared context)
      (assoc-benchmark-metric :information-ratio distribution/information-ratio context)
      (assoc-benchmark-metric :beta distribution/beta context)
      (assoc-benchmark-metric :alpha
                              (fn [strategy-returns benchmark-returns]
                                (distribution/alpha strategy-returns
                                                    benchmark-returns
                                                    {:periods-per-year periods-per-year}))
                              context)
      (assoc-benchmark-metric :correlation distribution/correlation context)
      (assoc-benchmark-metric :treynor-ratio
                              (fn [strategy-returns benchmark-returns]
                                (distribution/treynor-ratio strategy-returns
                                                            benchmark-returns
                                                            {:rf rf}))
                              context)))
