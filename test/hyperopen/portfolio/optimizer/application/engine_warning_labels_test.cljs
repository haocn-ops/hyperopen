(ns hyperopen.portfolio.optimizer.application.engine-warning-labels-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.engine :as engine]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]))

(def ^:private day-ms 86400000)

(deftest run-optimization-labels-vault-risk-warnings-by-human-name-test
  (let [vault-address "0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
        vault-id (str "vault:" vault-address)
        day (fn [n] (* n day-ms))
        btc-rows (mapv (fn [n close]
                         {:time-ms (day n)
                          :close (str close)})
                       (range 841)
                       (map #(+ 100 %) (range 841)))
        vault-history (mapv (fn [n]
                              [(day (* 14 n)) (+ 100 n)])
                            (range 61))
        request (fixtures/sample-engine-request
                 {:draft (fixtures/sample-draft
                          {:id "vault-warning-labels"
                           :universe [{:instrument-id "perp:BTC"
                                       :market-type :perp
                                       :coin "BTC"
                                       :shortable? true}
                                      {:instrument-id vault-id
                                       :market-type :vault
                                       :coin vault-id
                                       :vault-address vault-address
                                       :name "Hyperliquidity Provider (HLP)"
                                       :symbol "HLP"
                                       :shortable? false}]
                           :objective {:kind :minimum-variance}
                           :return-model {:kind :historical-mean}
                           :risk-model {:kind :diagonal-shrink}
                           :constraints {:long-only? true
                                         :max-asset-weight 0.8
                                         :rebalance-tolerance 0.001}})
                  :current-portfolio (fixtures/sample-current-portfolio
                                      {:by-instrument {"perp:BTC" {:weight 0.45
                                                                  :market-type :perp
                                                                  :coin "BTC"}}})
                  :history-data {:candle-history-by-coin {"BTC" btc-rows}
                                 :funding-history-by-coin
                                 {"BTC" [{:time-ms (day 0)
                                          :funding-rate-raw 0.0001}]}
                                 :vault-details-by-address
                                 {vault-address
                                  {:portfolio
                                   {:month
                                    {:accountValueHistory vault-history
                                     :pnlHistory (mapv (fn [[time value]]
                                                         [time (- value 100)])
                                                       vault-history)}}}}}
                  :market-cap-by-coin {"BTC" 600}
                  :as-of-ms (day 43)})
        result (engine/run-optimization
                request
                {:solve-problem (fn [_problem]
                                  {:status :solved
                                   :solver :fixture-solver
                                   :weights [0.5 0.5]})})
        warning (first (filter #(= :sparse-history-risk-estimation (:code %))
                               (:warnings result)))]
    (is (= :solved (:status result)))
    (is (= "Hyperliquidity Provider (HLP)"
           (get-in result [:labels-by-instrument vault-id])))
    (is (= vault-id (:instrument-id warning)))
    (is (= "Hyperliquidity Provider (HLP)" (:instrument-label warning)))
    (is (str/includes? (:message warning)
                       "Hyperliquidity Provider (HLP): sparse history uses mixed-frequency covariance"))
    (is (not (str/includes? (:message warning) vault-id)))))
