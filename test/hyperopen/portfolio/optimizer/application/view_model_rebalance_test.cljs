(ns hyperopen.portfolio.optimizer.application.view-model-rebalance-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.view-model.rebalance :as rebalance]))

(def ^:private sample-result
  {:instrument-ids ["spot:BTC" "perp:BTC" "vault:0xabc"]
   :current-weights [0.125 -0.0625 0]
   :target-weights [0.25 -0.125 0.125]
   :labels-by-instrument {"spot:BTC" "BTC"
                          "perp:BTC" "BTC-PERP"
                          "vault:0xabc" "Grow Vault"}
   :diagnostics {:binding-constraints [{:instrument-id "perp:BTC"}]}
   :rebalance-preview {:capital-usd 10000}})

(deftest target-exposure-table-model-groups-legs-and-binding-state-test
  (let [model (rebalance/target-exposure-table-model sample-result)
        groups (:groups model)
        btc-group (first groups)
        btc-rows (:rows btc-group)
        perp-row (second btc-rows)
        vault-group (second groups)]
    (is (= 10000 (:capital-usd model)))
    (is (= #{"perp:BTC"} (:binding-instrument-ids model)))
    (is (= ["BTC" "Grow Vault"]
           (mapv :asset groups)))
    (is (= {:asset "BTC"
            :current-weight 0.0625
            :target-weight 0.125
            :delta 0.0625
            :binding? true
            :expandable? true}
           (select-keys btc-group
                        [:asset :current-weight :target-weight :delta
                         :binding? :expandable?])))
    (is (= {:idx 1
            :asset "BTC"
            :instrument-id "perp:BTC"
            :current-weight -0.0625
            :target-weight -0.125
            :current-notional -625
            :target-notional -1250
            :delta -0.0625
            :delta-notional -625
            :binding? true
            :current-sign "short"
            :target-sign "short"
            :leg-label "perp short"
            :hidden? false}
           (select-keys perp-row
                        [:idx :asset :instrument-id :current-weight :target-weight
                         :current-notional :target-notional :delta :delta-notional
                         :binding? :current-sign :target-sign :leg-label :hidden?])))
    (is (= {:key "perp:BTC"
            :coin "BTC"
            :symbol "BTC"
            :base "BTC"
            :market-type :perp}
           (:market perp-row)))
    (is (= :vault (:icon-kind vault-group)))
    (is (= true (:hidden? (first (:rows vault-group)))))))

(deftest target-exposure-table-model-matches-excluded-backend-id-to-local-row-test
  (let [draft {:universe [{:instrument-id "hl:perp:BTC"
                           :market-type :perp
                           :coin "BTC"}
                          {:instrument-id "hl:perp:ETH"
                           :market-type :perp
                           :coin "ETH"}]
               :constraints {:blocklist ["hl:perp:ETH"]}}
        result {:instrument-ids ["perp:BTC"]
                :current-weights [0]
                :target-weights [1]
                :labels-by-instrument {"perp:BTC" "BTC"}
                :rebalance-preview {:capital-usd 10000}}
        model (rebalance/target-exposure-table-model result {:draft draft})
        eth-group (some #(when (= "ETH" (:asset %)) %) (:groups model))
        eth-row (first (:rows eth-group))]
    (is (some? eth-group))
    (is (= {:asset "ETH"
            :instrument-id "perp:ETH"
            :target-weight 0
            :delta 0
            :delta-notional 0
            :excluded? true
            :status-label "sell to 0"}
           (select-keys eth-group
                        [:asset :instrument-id :target-weight :delta
                         :delta-notional :excluded? :status-label])))
    (is (= {:instrument-id "perp:ETH"
            :target-weight 0
            :excluded? true
            :status-label "sell to 0"}
           (select-keys eth-row
                        [:instrument-id :target-weight :excluded? :status-label])))))

(deftest target-exposure-table-model-preserves-draft-market-identity-for-icon-rendering-test
  (let [draft {:universe [{:instrument-id "perp:XYZ100"
                           :market-type :perp
                           :coin "XYZ100-USDC"
                           :symbol "XYZ100-USDC"
                           :base "XYZ100"
                           :dex "xyz"
                           :hip3? true}
                          {:instrument-id "perp:xyz:SP500"
                           :market-type :perp
                           :coin "SP500-USDC"
                           :symbol "SP500-USDC"
                           :base "SP500"
                           :dex "xyz"
                           :hip3? true
                           :optimizer-history/instrument-id "external:tiingo:SPY"}]}
        result {:instrument-ids ["perp:XYZ100" "external:tiingo:SPY"]
                :current-weights [0.1 0.2]
                :target-weights [0.4 0.6]
                :labels-by-instrument {"perp:XYZ100" "XYZ100"
                                       "external:tiingo:SPY" "SP500"}
                :rebalance-preview {:capital-usd 10000}}
        model (rebalance/target-exposure-table-model result {:draft draft})
        groups-by-asset (into {} (map (juxt :asset identity) (:groups model)))]
    (is (= {:key "perp:XYZ100"
            :coin "XYZ100-USDC"
            :symbol "XYZ100-USDC"
            :base "XYZ100"
            :dex "xyz"
            :market-type :perp}
           (select-keys (:market (get groups-by-asset "XYZ100"))
                        [:key :coin :symbol :base :dex :market-type])))
    (is (= {:key "perp:xyz:SP500"
            :coin "SP500-USDC"
            :symbol "SP500-USDC"
            :base "SP500"
            :dex "xyz"
            :market-type :perp}
           (select-keys (:market (get groups-by-asset "SP500"))
                        [:key :coin :symbol :base :dex :market-type])))))
