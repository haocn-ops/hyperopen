(ns hyperopen.portfolio.optimizer.application.black-litterman-calibration-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.engine :as engine]
            [hyperopen.portfolio.optimizer.infrastructure.wire :as wire]))

(defn- within?
  [expected actual tolerance]
  (< (js/Math.abs (- expected actual)) tolerance))

(defn- overlay-by-id
  [result mode]
  (into {}
        (map (juxt :instrument-id identity))
        (get-in result [:frontier-overlays mode])))

(defn- one-asset-btc-view-request
  [view-weights]
  (let [btc-id "perp:BTC"
        one-year-interval [{:dt-days 365.2425
                            :dt-years 1}]]
    {:scenario-id "bl-worker-boundary-regression"
     :universe [{:instrument-id btc-id
                 :market-type :perp
                 :coin "BTC"
                 :shortable? true}]
     :current-portfolio {:capital {:nav-usdc 10000}
                         :by-instrument {}}
     :return-model {:kind :black-litterman
                    :views [{:id "btc-positive-view"
                             :kind :absolute
                             :instrument-id btc-id
                             :weights view-weights
                             :return 0.2
                             :confidence 0.75
                             :confidence-variance 0.25}]}
     :risk-model {:kind :sample-covariance}
     :objective {:kind :max-sharpe}
     :constraints {:long-only? true
                   :max-asset-weight 1
                   :rebalance-tolerance 0.001}
     :history {:calendar [0 1 2 3 4 5]
               :return-calendar [1 2 3 4 5]
               :return-series-by-instrument
               {btc-id [-0.1 -0.05 0 0.05 0.1]}
               :expected-return-series-by-instrument
               {btc-id [-0.13]}
               :expected-return-intervals-by-instrument
               {btc-id one-year-interval}
               :funding-by-instrument
               {btc-id {:source :missing-market-funding-history
                        :annualized-carry 0}}
               :freshness {:as-of-ms 6
                           :oldest-common-ms 0
                           :latest-common-ms 5
                           :age-ms 1
                           :stale? false}}
     :black-litterman-prior
     {:source :fallback-equal-weight
      :weights-by-instrument {btc-id 1}}
     :warnings []
     :as-of-ms 6}))

(deftest black-litterman-run-uses-baseline-return-priors-for-frontier-overlays-test
  (let [btc-id "perp:BTC"
        growi-id "vault:0x1e37a337ed460039d1b15bd3bc489de789768d5e"
        hlp-id "vault:0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
        one-year-interval [{:dt-days 365.2425
                            :dt-years 1}]
        request {:scenario-id "bl-calibration-regression"
                 :universe [{:instrument-id btc-id
                             :market-type :perp
                             :coin "BTC"
                             :shortable? true}
                            {:instrument-id growi-id
                             :market-type :vault
                             :coin growi-id
                             :vault-address "0x1e37a337ed460039d1b15bd3bc489de789768d5e"
                             :name "Growi HF"
                             :shortable? false}
                            {:instrument-id hlp-id
                             :market-type :vault
                             :coin hlp-id
                             :vault-address "0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
                             :name "Hyperliquidity Provider (HLP)"
                             :shortable? false}]
                 :current-portfolio {:capital {:nav-usdc 10000}
                                     :by-instrument {}}
                 :return-model {:kind :black-litterman
                                :views [{:kind :absolute
                                         :instrument-id btc-id
                                         :weights {btc-id 1}
                                         :return 0.2
                                         :confidence 0.75
                                         :confidence-variance 0.25}
                                        {:kind :absolute
                                         :instrument-id growi-id
                                         :weights {growi-id 1}
                                         :return 0.2
                                         :confidence 0.75
                                         :confidence-variance 0.25}
                                        {:kind :absolute
                                         :instrument-id hlp-id
                                         :weights {hlp-id 1}
                                         :return 0.2
                                         :confidence 0.75
                                         :confidence-variance 0.25}]}
                 :risk-model {:kind :sample-covariance}
                 :objective {:kind :minimum-variance}
                 :constraints {:long-only? true
                               :max-asset-weight 1
                               :rebalance-tolerance 0.001}
                 :history {:calendar [0 1 2 3 4 5]
                           :return-calendar [1 2 3 4 5]
                           :return-series-by-instrument
                           {btc-id [-0.1 -0.05 0 0.05 0.1]
                            growi-id [0.03 -0.02 0.01 -0.01 -0.01]
                            hlp-id [0.02 0.01 -0.02 0 -0.01]}
                           :expected-return-series-by-instrument
                           {btc-id [-0.13]
                            growi-id [0.2]
                            hlp-id [0.2]}
                           :expected-return-intervals-by-instrument
                           {btc-id one-year-interval
                            growi-id one-year-interval
                            hlp-id one-year-interval}
                           :funding-by-instrument
                           {btc-id {:source :missing-market-funding-history
                                    :annualized-carry 0}
                            growi-id {:source :not-applicable
                                      :annualized-carry 0}
                            hlp-id {:source :not-applicable
                                    :annualized-carry 0}}
                           :freshness {:as-of-ms 6
                                       :oldest-common-ms 0
                                       :latest-common-ms 5
                                       :age-ms 1
                                       :stale? false}}
                 :black-litterman-prior
                 {:source :fallback-equal-weight
                  :weights-by-instrument {btc-id (/ 1 3)
                                          growi-id (/ 1 3)
                                          hlp-id (/ 1 3)}}
                 :warnings [{:code :missing-market-cap-prior}
                            {:code :missing-current-portfolio-prior}]
                 :as-of-ms 6}
        result (engine/run-optimization
                request
                {:solve-problem (fn [_problem]
                                  {:status :solved
                                   :solver :fixture-solver
                                   :weights [0.6 0.2 0.2]})})
        standalone (overlay-by-id result :standalone)]
    (is (= :solved (:status result)))
    (is (pos? (get-in standalone [btc-id :expected-return]))
        "BTC standalone overlay should use the Black-Litterman posterior, not the negative baseline prior.")
    (is (<= (get-in standalone [btc-id :expected-return]) 0.205)
        "BTC should not inherit a 60% covariance-implied fallback prior after a 20% absolute view.")
    (is (within? 0.2
                 (get-in standalone [hlp-id :expected-return])
                 0.02)
        "HLP should stay close to its unchanged 20% baseline/view expected return.")
    (is (= :baseline-expected-returns
           (get-in result [:black-litterman-diagnostics :prior-return-source])))
    (is (contains? (set (map :code (:warnings result)))
                   :missing-market-cap-prior))))

(deftest black-litterman-run-survives-worker-boundary-keywordized-view-weights-test
  (let [btc-id "perp:BTC"
        decoded-id (keyword btc-id)
        request (wire/normalize-worker-boundary
                 (one-asset-btc-view-request {decoded-id 1}))
        result (engine/run-optimization
                request
                {:solve-problem (fn [_problem]
                                  {:status :solved
                                   :solver :fixture-solver
                                   :weights [1]})})
        standalone (overlay-by-id result :standalone)]
    (is (= :solved (:status result)))
    (is (= {"perp:BTC" 1}
           (get-in request [:return-model :views 0 :weights])))
    (is (= 1 (get-in result [:black-litterman-diagnostics :view-count])))
    (is (pos? (get-in standalone [btc-id :expected-return]))
        "BTC overlay should use the Black-Litterman posterior after worker boundary normalization.")
    (is (pos? (get-in result [:expected-returns-by-instrument btc-id]))
        "Solved payload should expose the effective expected return vector used by the optimizer.")))
