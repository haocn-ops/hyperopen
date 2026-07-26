(ns hyperopen.portfolio.optimizer.application.return-inputs-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.return-inputs :as return-inputs]))

(defn- near?
  [expected actual]
  (< (js/Math.abs (- expected actual)) 0.0000001))

(def ^:private year-days
  365.2425)

(deftest readiness-inputs-use-baseline-sharpe-return-estimator-for-black-litterman-prefill-test
  (let [inputs (return-inputs/readiness-inputs-by-instrument
                {:status :ready
                 :request {:universe [{:instrument-id "perp:BTC"}]
                           :return-model {:kind :black-litterman
                                          :views []}
                           :risk-model {:kind :sample-covariance}
                           :periods-per-year 10
                           :history {:return-series-by-instrument
                                     {"perp:BTC" [0.01 0.03]}}
                           :black-litterman-prior
                           {:source :market-cap
                            :weights-by-instrument {"perp:BTC" 1}}}})]
    (is (near? 0.2 (get inputs "perp:BTC"))
        "Prefill should use the historical expected-return input for Sharpe, not BL equilibrium return.")))

(deftest readiness-inputs-use-baseline-sharpe-return-estimator-before-black-litterman-is-applied-test
  (let [inputs (return-inputs/readiness-inputs-by-instrument
                {:status :ready
                 :request {:universe [{:instrument-id "perp:BTC"}]
                           :return-model {:kind :historical-mean}
                           :risk-model {:kind :sample-covariance}
                           :periods-per-year 10
                           :history {:return-series-by-instrument
                                     {"perp:BTC" [0.01 0.03]}}}})]
    (is (near? 0.2 (get inputs "perp:BTC"))
        "Use my views should be able to prefill before the draft return model has switched to Black-Litterman.")))

(deftest readiness-inputs-keep-single-asset-prefill-while-history-loading-test
  (let [inputs (return-inputs/readiness-inputs-by-instrument
                {:status :blocked
                 :reason :history-loading
                 :request {:universe [{:instrument-id "perp:BTC"}]
                           :return-model {:kind :black-litterman
                                          :views []}
                           :risk-model {:kind :sample-covariance}
                           :periods-per-year 10
                           :history {:return-series-by-instrument
                                     {"perp:BTC" [0.01 0.03]}}
                           :black-litterman-prior
                           {:source :market-cap
                            :weights-by-instrument {"perp:BTC" 1}}}})]
    (is (near? 0.2 (get inputs "perp:BTC"))
        "A loading status should not suppress a usable single-asset baseline return.")))

(deftest readiness-inputs-use-geometric-vault-window-return-when-history-has-subdaily-anchor-test
  (let [hlp-id "vault:0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
        inputs (return-inputs/readiness-inputs-by-instrument
                {:status :ready
                 :request {:universe [{:instrument-id hlp-id}]
                           :return-model {:kind :black-litterman
                                          :views []}
                           :risk-model {:kind :sample-covariance}
                           :periods-per-year 365
                           :history {:return-series-by-instrument {hlp-id [0.2 0]}
                                     :expected-return-series-by-instrument {hlp-id [0.2 0]}
                                     :expected-return-intervals-by-instrument
                                     {hlp-id [{:dt-days 0.5
                                               :dt-years (/ 0.5 year-days)}
                                              {:dt-days (- year-days 0.5)
                                               :dt-years (/ (- year-days 0.5)
                                                            year-days)}]}}
                           :black-litterman-prior
                           {:source :market-cap
                            :weights-by-instrument {hlp-id 1}}}})]
    (is (near? 0.2 (get inputs hlp-id))
        "A vault that gained 20% across the one-year window should not fall back to mean * 365.")))
