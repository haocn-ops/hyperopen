(ns hyperopen.portfolio.worker-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.metrics :as metrics]
            [hyperopen.portfolio.worker :as worker]))

(deftest metrics-result-payload-uses-direct-daily-rows-test
  (let [metrics-result-payload @#'worker/metrics-result-payload
        captured-requests* (atom [])
        request-data {:portfolio-request {:strategy-cumulative-rows [[:portfolio 1]]
                                          :strategy-daily-rows [[:portfolio-daily 0.10]]
                                          :benchmark-daily-rows [[:spy-daily 0.02]]
                                          :rf 0.01
                                          :mar 0.02
                                          :periods-per-year 52
                                          :quality-gates {:minimum-samples 2}}
                      :benchmark-requests [{:coin "BTC"
                                            :request {:strategy-cumulative-rows [[:btc 1]]
                                                      :strategy-daily-rows [[:btc-daily 0.03]]}}]}]
    (with-redefs [metrics/daily-compounded-returns
                  (fn [rows]
                    (throw (js/Error. (str "unexpected fallback daily rows for " rows))))
                  metrics/compute-performance-metrics
                  (fn [request]
                    (swap! captured-requests* conj request)
                    {:request request})]
      (is (= {:portfolio-values {:request {:strategy-cumulative-rows [[:portfolio 1]]
                                           :strategy-daily-rows [[:portfolio-daily 0.10]]
                                           :benchmark-daily-rows [[:spy-daily 0.02]]
                                           :rf 0.01
                                           :mar 0.02
                                           :periods-per-year 52
                                           :quality-gates {:minimum-samples 2}}}
              :benchmark-values-by-coin {"BTC" {:request {:strategy-cumulative-rows [[:btc 1]]
                                                          :strategy-daily-rows [[:btc-daily 0.03]]
                                                          :rf 0
                                                          :periods-per-year 365}}}}
             (metrics-result-payload request-data)))
      (is (= [{:strategy-cumulative-rows [[:portfolio 1]]
               :strategy-daily-rows [[:portfolio-daily 0.10]]
               :benchmark-daily-rows [[:spy-daily 0.02]]
               :rf 0.01
               :mar 0.02
               :periods-per-year 52
               :quality-gates {:minimum-samples 2}}
              {:strategy-cumulative-rows [[:btc 1]]
               :strategy-daily-rows [[:btc-daily 0.03]]
               :rf 0
               :periods-per-year 365}
              {:strategy-cumulative-rows [[:portfolio 1]]
               :strategy-daily-rows [[:portfolio-daily 0.10]]
               :benchmark-daily-rows [[:btc-daily 0.03]]
               :rf 0.01
               :mar 0.02
               :periods-per-year 52
               :quality-gates {:minimum-samples 2}}]
             @captured-requests*)))))

(deftest metrics-result-payload-derives-fallback-daily-rows-from-cumulative-test
  (let [metrics-result-payload @#'worker/metrics-result-payload
        daily-rows-calls* (atom [])
        captured-requests* (atom [])
        request-data {:portfolio-request {:strategy-cumulative-rows [[:portfolio 1]]
                                          :benchmark-cumulative-rows [[:spy 1] [:spy 2]]}
                      :benchmark-requests [{:coin "ETH"
                                            :request {:strategy-cumulative-rows [[:eth 1] [:eth 2]]}}]}]
    (with-redefs [metrics/daily-compounded-returns
                  (fn [rows]
                    (swap! daily-rows-calls* conj rows)
                    [[:daily-from rows]])
                  metrics/compute-performance-metrics
                  (fn [request]
                    (swap! captured-requests* conj request)
                    {:request request})]
      (is (= {:portfolio-values {:request {:strategy-cumulative-rows [[:portfolio 1]]
                                           :strategy-daily-rows [[:daily-from [[:portfolio 1]]]]
                                           :benchmark-daily-rows [[:daily-from [[:spy 1] [:spy 2]]]]
                                           :rf 0
                                           :mar 0
                                           :periods-per-year 365
                                           :quality-gates nil}}
              :benchmark-values-by-coin {"ETH" {:request {:strategy-cumulative-rows [[:eth 1] [:eth 2]]
                                                          :strategy-daily-rows [[:daily-from [[:eth 1] [:eth 2]]]]
                                                          :rf 0
                                                          :periods-per-year 365}}}}
             (metrics-result-payload request-data)))
      (is (= [[[:spy 1] [:spy 2]]
              [[:portfolio 1]]
              [[:eth 1] [:eth 2]]]
             @daily-rows-calls*))
      (is (= [{:strategy-cumulative-rows [[:portfolio 1]]
               :strategy-daily-rows [[:daily-from [[:portfolio 1]]]]
               :benchmark-daily-rows [[:daily-from [[:spy 1] [:spy 2]]]]
               :rf 0
               :mar 0
               :periods-per-year 365
               :quality-gates nil}
              {:strategy-cumulative-rows [[:eth 1] [:eth 2]]
               :strategy-daily-rows [[:daily-from [[:eth 1] [:eth 2]]]]
               :rf 0
               :periods-per-year 365}
              {:strategy-cumulative-rows [[:portfolio 1]]
               :strategy-daily-rows [[:daily-from [[:portfolio 1]]]]
               :benchmark-daily-rows [[:daily-from [[:eth 1] [:eth 2]]]]
               :rf 0
               :mar 0
               :periods-per-year 365
               :quality-gates nil}]
             @captured-requests*)))))
