(ns hyperopen.portfolio.optimizer.application.engine-history-window-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.engine :as engine]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]))

(def day-ms
  (* 24 60 60 1000))

(deftest run-optimization-includes-history-window-limiter-in-summary-test
  (let [day (fn [n] (* n day-ms))
        request (fixtures/sample-engine-request
                 {:draft (fixtures/sample-draft
                          {:id "history-window-limiter"
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
                                         :max-asset-weight 0.8
                                         :rebalance-tolerance 0.001}})
                  :current-portfolio (fixtures/sample-current-portfolio
                                      {:capital {:nav-usdc 10000}
                                       :by-instrument {"perp:BTC" {:weight 0.6}
                                                       "perp:ETH" {:weight 0.4}}})
                  :as-of-ms (day 4)
                  :history-data {:candle-history-by-coin
                                 {"BTC" [{:time-ms (day 0) :close "100"}
                                         {:time-ms (day 1) :close "101"}
                                         {:time-ms (day 2) :close "102"}
                                         {:time-ms (day 3) :close "103"}]
                                  "ETH" [{:time-ms (day 1) :close "50"}
                                         {:time-ms (day 2) :close "51"}
                                         {:time-ms (day 3) :close "52"}]}
                                 :funding-history-by-coin {}}})
        result (engine/run-optimization
                request
                {:solve-problem (fn [_problem]
                                  {:status :solved
                                   :solver :fixture-solver
                                   :weights [0.5 0.5]})})
        summary (:history-summary result)]
    (is (= :solved (:status result)))
    (is (= 2 (:return-observations summary)))
    (is (= 2 (:return-days summary)))
    (is (= "perp:ETH" (:limiting-instrument-id summary)))
    (is (= :starts-later (:limiting-reason summary)))))
