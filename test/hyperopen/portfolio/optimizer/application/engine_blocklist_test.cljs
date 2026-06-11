(ns hyperopen.portfolio.optimizer.application.engine-blocklist-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.engine :as engine]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]))

(def base-request
  (fixtures/sample-engine-request
   {:draft (fixtures/sample-draft
            {:id "blocklist-frontier"
             :universe [{:instrument-id "perp:BTC"
                         :market-type :perp
                         :coin "BTC"}
                        {:instrument-id "perp:ETH"
                         :market-type :perp
                         :coin "ETH"}]
             :return-model {:kind :historical-mean}
             :risk-model {:kind :sample-covariance}
             :objective {:kind :minimum-variance}
             :constraints {:long-only? true
                           :max-asset-weight 1.0
                           :per-perp-leverage-caps {"perp:BTC" {:max-weight 1.0}}}
             :execution-assumptions {:fallback-slippage-bps 20
                                     :prices-by-id {"perp:BTC" 100
                                                    "perp:ETH" 50}
                                     :fee-bps-by-id {"perp:BTC" 4
                                                    "perp:ETH" 5}}})
    :current-portfolio (fixtures/sample-current-portfolio
                        {:capital {:nav-usdc 10000}
                         :by-instrument {"perp:BTC" {:weight 0.6}
                                         "perp:ETH" {:weight 0.4}}})
    :history-data {:candle-history-by-coin
                   {"BTC" [{:time-ms 0 :close "100"}
                           {:time-ms 100 :close "101"}
                           {:time-ms 200 :close "103.02"}
                           {:time-ms 300 :close "106.1106"}]
                    "ETH" [{:time-ms 0 :close "100"}
                           {:time-ms 100 :close "102"}
                           {:time-ms 200 :close "103.02"}
                           {:time-ms 300 :close "103.02"}]}
                   :funding-history-by-coin
                   {"BTC" [{:time-ms 0 :funding-rate-raw 0.000045662100456621}]
                    "ETH" [{:time-ms 0 :funding-rate-raw -0.000009132420091324}]}}
    :market-cap-by-coin {}
    :as-of-ms 1000}))

(deftest run-optimization-excludes-blocklisted-assets-from-solver-and-frontier-test
  (let [calls (atom [])
        result (engine/run-optimization
                (-> base-request
                    (assoc-in [:constraints :blocklist] ["perp:ETH"])
                    (assoc-in [:constraints :max-asset-weight] 1.0)
                    (assoc-in [:constraints :per-perp-leverage-caps "perp:BTC" :max-weight] 1.0))
                {:solve-problem (fn [problem]
                                  (swap! calls conj problem)
                                  {:status :solved
                                   :solver :fixture-solver
                                   :weights [1]})})]
    (is (= :solved (:status result)))
    (is (= #{["perp:BTC"]}
           (set (map :instrument-ids @calls))))
    (is (= ["perp:BTC"] (:instrument-ids result)))
    (is (= {"perp:BTC" 1}
           (:target-weights-by-instrument result)))
    (is (not (contains? (:target-weights-by-instrument result)
                        "perp:ETH")))
    (is (every? #(= 1 (count (:weights %)))
                (:frontier result)))))

(deftest run-optimization-excludes-spot-assets-unless-enabled-test
  (let [spot-request
        (fixtures/sample-engine-request
         {:draft (fixtures/sample-draft
                  {:id "spot-default-exclusion"
                   :universe [{:instrument-id "perp:BTC"
                               :market-type :perp
                               :coin "BTC"}
                              {:instrument-id "spot:PURR"
                               :market-type :spot
                               :coin "PURR"}]
                   :return-model {:kind :historical-mean}
                   :risk-model {:kind :sample-covariance}
                   :objective {:kind :minimum-variance}
                   :constraints {:long-only? true
                                 :max-asset-weight 1.0
                                 :perp-leverage {"perp:BTC" {:max-weight 1.0}}
                                 :max-turnover nil}})
          :current-portfolio (fixtures/sample-current-portfolio
                              {:capital {:nav-usdc 10000}
                               :by-instrument {"perp:BTC" {:weight 1.0}}})
          :history-data {:candle-history-by-coin
                         {"BTC" [{:time-ms 0 :close "100"}
                                 {:time-ms 100 :close "101"}
                                 {:time-ms 200 :close "103.02"}
                                 {:time-ms 300 :close "106.1106"}]
                          "PURR" [{:time-ms 0 :close "1"}
                                  {:time-ms 100 :close "1.1"}
                                  {:time-ms 200 :close "1.2"}
                                  {:time-ms 300 :close "1.3"}]}
                         :funding-history-by-coin {}}
          :market-cap-by-coin {}
          :as-of-ms 1000})
        default-calls (atom [])
        default-result (engine/run-optimization
                        spot-request
                        {:solve-problem (fn [problem]
                                          (swap! default-calls conj problem)
                                          {:status :solved
                                           :solver :fixture-solver
                                           :weights [1]})})
        included-calls (atom [])
        included-result (engine/run-optimization
                         (assoc-in spot-request [:constraints :include-spot?] true)
                         {:solve-problem (fn [problem]
                                           (swap! included-calls conj problem)
                                           {:status :solved
                                            :solver :fixture-solver
                                            :weights [0.8 0.2]})})]
    (is (= :solved (:status default-result)))
    (is (= #{["perp:BTC"]}
           (set (map :instrument-ids @default-calls))))
    (is (= ["perp:BTC"] (:instrument-ids default-result)))
    (is (not (contains? (:target-weights-by-instrument default-result)
                        "spot:PURR")))
    (is (= :solved (:status included-result)))
    (is (= #{["perp:BTC" "spot:PURR"]}
           (set (map :instrument-ids @included-calls))))
    (is (= ["perp:BTC" "spot:PURR"] (:instrument-ids included-result)))))

(deftest run-optimization-matches-backend-universe-ids-to-local-history-ids-test
  (let [calls (atom [])
        request (-> base-request
                    (assoc :universe
                           [{:instrument-id "hl:perp:BTC"
                             :market-type :perp
                             :coin "BTC"}
                            {:instrument-id "hl:perp:ETH"
                             :market-type :perp
                             :coin "ETH"}])
                    (assoc :requested-universe
                           [{:instrument-id "hl:perp:BTC"
                             :market-type :perp
                             :coin "BTC"}
                            {:instrument-id "hl:perp:ETH"
                             :market-type :perp
                             :coin "ETH"}]))
        result (engine/run-optimization
                request
                {:solve-problem (fn [problem]
                                  (swap! calls conj problem)
                                  {:status :solved
                                   :solver :fixture-solver
                                   :weights [0.5 0.5]})})]
    (is (= :solved (:status result)))
    (is (= ["perp:BTC" "perp:ETH"] (:instrument-ids result)))
    (is (= #{"perp:BTC" "perp:ETH"}
           (set (:instrument-ids (first @calls)))))
    (is (= {"perp:BTC" "BTC"
            "perp:ETH" "ETH"}
           (select-keys (:labels-by-instrument result)
                        ["perp:BTC" "perp:ETH"])))))

(deftest run-optimization-blocklist-matches-backend-id-with-local-history-id-test
  (let [calls (atom [])
        request (-> base-request
                    (assoc :universe
                           [{:instrument-id "hl:perp:BTC"
                             :market-type :perp
                             :coin "BTC"}
                            {:instrument-id "hl:perp:ETH"
                             :market-type :perp
                             :coin "ETH"}])
                    (assoc :requested-universe
                           [{:instrument-id "hl:perp:BTC"
                             :market-type :perp
                             :coin "BTC"}
                            {:instrument-id "hl:perp:ETH"
                             :market-type :perp
                             :coin "ETH"}])
                    (assoc-in [:constraints :blocklist] ["hl:perp:ETH"])
                    (assoc-in [:constraints :max-asset-weight] 1.0)
                    (assoc-in [:constraints :per-perp-leverage-caps "perp:BTC" :max-weight] 1.0))
        result (engine/run-optimization
                request
                {:solve-problem (fn [problem]
                                  (swap! calls conj problem)
                                  {:status :solved
                                   :solver :fixture-solver
                                   :weights [1]})})]
    (is (= :solved (:status result)))
    (is (= #{["perp:BTC"]}
           (set (map :instrument-ids @calls))))
    (is (= ["perp:BTC"] (:instrument-ids result)))))
