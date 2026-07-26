(ns hyperopen.portfolio.optimizer.application.engine-sparse-caps-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.engine :as engine]
            [hyperopen.portfolio.optimizer.application.request-builder :as request-builder]))

(deftest run-optimization-applies-runtime-sparse-history-cap-to-target-solver-bounds-test
  (let [day-ms 86400000
        day (fn [n] (* n day-ms))
        vault-address "0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
        vault-id (str "vault:" vault-address)
        btc-rows (mapv (fn [n]
                         {:time-ms (day n)
                          :close (str (+ 100 n))})
                       (range 29))
        request (request-builder/build-engine-request
                 {:draft {:id "sparse-cap-target-bounds"
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
                                        :max-asset-weight 1}}
                  :current-portfolio {:capital {:nav-usdc 10000}
                                      :by-instrument {"perp:BTC" {:weight 1}}}
                  :history-data {:candle-history-by-coin {"BTC" btc-rows}
                                 :funding-history-by-coin {}
                                 :vault-details-by-address
                                 {vault-address
                                  {:portfolio
                                   {:month
                                    {:accountValueHistory [[(day 0) 100]
                                                           [(day 14) 102]
                                                           [(day 28) 105]]
                                     :pnlHistory [[(day 0) 0]
                                                  [(day 14) 2]
                                                  [(day 28) 5]]}}}}}
                  :market-cap-by-coin {"BTC" 600}
                  :as-of-ms (day 29)})
        calls (atom [])
        result (engine/run-optimization
                request
                {:solve-problem (fn [problem]
                                  (swap! calls conj problem)
                                  {:status :solved
                                   :solver :fixture-solver
                                   :weights [0.95 0.05]})})
        target-problem (first (filter #(= :minimum-variance (:objective-kind %))
                                      @calls))
        cap-warning (some #(when (= :sparse-history-weight-cap-applied (:code %))
                             %)
                          (:warnings result))]
    (is (= :solved (:status result)))
    (is (= ["perp:BTC" vault-id]
           (:instrument-ids result)))
    (is (= [1 0.05]
           (:upper-bounds target-problem)))
    (is (= vault-id (:instrument-id cap-warning)))
    (is (= 0.05 (:max-weight cap-warning)))
    (is (and (string? (:message cap-warning))
             (str/includes? (:message cap-warning) "5%")))))
