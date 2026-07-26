(ns hyperopen.portfolio.optimizer.application.rebalance-snapshot-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.rebalance-snapshot :as rebalance-snapshot]))

(defn- near?
  [expected actual]
  (< (js/Math.abs (- expected actual)) 0.0000001))

(defn- sample-last-run
  []
  {:request-signature
   {:request {:current-portfolio {:capital {:nav-usdc 300}}
              :constraints {:rebalance-tolerance 0}
              :execution-assumptions {:fallback-slippage-bps 25
                                      :prices-by-id {"perp:BTC" 100
                                                     "perp:ETH" 100}}
              :requested-universe [{:instrument-id "perp:BTC"
                                    :instrument-type :perp
                                    :coin "BTC"}
                                   {:instrument-id "perp:ETH"
                                    :instrument-type :perp
                                    :coin "ETH"}]
              :universe [{:instrument-id "perp:BTC"
                          :instrument-type :perp
                          :coin "BTC"}
                         {:instrument-id "perp:ETH"
                          :instrument-type :perp
                          :coin "ETH"}]}}
   :result {:status :solved
            :instrument-ids ["perp:BTC" "perp:ETH"]
            :current-weights [0 0]
            :target-weights [1 0.5]
            :rebalance-preview
            {:status :ready
             :capital-usd 300
             :rows [{:instrument-id "perp:BTC"
                     :instrument-type :perp
                     :coin "BTC"
                     :status :ready
                     :side :buy
                     :cost {:source :fallback-bps}}
                    {:instrument-id "perp:ETH"
                     :instrument-type :perp
                     :coin "ETH"
                     :status :ready
                     :side :buy
                     :cost {:source :snapshot
                            :age-ms 1000
                            :stale? false}}]}}})

(deftest build-snapshot-refresh-plan-dedupes-caps-and-skips-fresh-snapshots-test
  (let [plan (rebalance-snapshot/build-snapshot-refresh-plan
              (sample-last-run)
              {:max-snapshot-coins 1
               :snapshot-stale-after-ms 30000})]
    (is (= :ready (:status plan)))
    (is (= 1 (:eligible-count plan)))
    (is (= 1 (:skipped-count plan)))
    (is (= [{:coin "BTC"
             :instrument-ids ["perp:BTC"]}]
           (mapv #(select-keys % [:coin :instrument-ids])
                 (:requests plan))))))

(deftest normalize-l2-book-snapshot-context-preserves-depth-and-freshness-test
  (let [context (rebalance-snapshot/normalize-l2-book-snapshot-context
                 "BTC"
                 {:time 9000
                  :levels [[{:px "99" :sz "2"}]
                           [{:px "102" :sz "2"}
                            {:px "101" :sz "1"}]]}
                 {:now-ms 10000
                  :snapshot-stale-after-ms 30000})]
    (is (= :snapshot (:source context)))
    (is (= false (:stale? context)))
    (is (= 1000 (:age-ms context)))
    (is (= [{:px "99" :sz "2"}] (:bids context)))
    (is (= ["101" "102"] (mapv :px (:asks context))))))

(deftest last-run-with-snapshot-contexts-rebuilds-rebalance-preview-test
  (let [context {:source :snapshot
                 :asks [{:px "101" :sz "1"}
                        {:px "102" :sz "2"}]
                 :observed-at-ms 9000
                 :age-ms 1000
                 :stale? false}
        updated (rebalance-snapshot/last-run-with-snapshot-contexts
                 (sample-last-run)
                 {"perp:BTC" context})
        cost (get-in updated [:result :rebalance-preview :rows 0 :cost])]
    (is (= context
           (get-in updated
                   [:request-signature
                    :request
                    :execution-assumptions
                    :cost-contexts-by-id
                    "perp:BTC"])))
    (is (= :snapshot (:source cost)))
    (is (near? 101.66666666666667 (:estimated-fill-price cost)))
    (is (near? 166.66666666666669 (:slippage-bps cost)))
    (is (near? 5 (:estimated-slippage-usd cost)))
    (is (= 1000 (:age-ms cost)))))
