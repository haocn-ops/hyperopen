(ns hyperopen.portfolio.optimizer.universe-exclusion-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.actions :as actions]))

(deftest toggle-draft-universe-instrument-exclusion-and-run-matches-backend-draft-id-test
  (let [btc-instrument {:instrument-id "hl:perp:BTC"
                        :market-type :perp
                        :coin "BTC"
                        :shortable? true}
        eth-instrument {:instrument-id "hl:perp:ETH"
                        :market-type :perp
                        :coin "ETH"
                        :shortable? true}
        base-state {:portfolio {:optimizer {:draft {:universe [btc-instrument
                                                                eth-instrument]
                                                    :constraints {:blocklist []}}}}}
        excluded-state (assoc-in base-state
                                 [:portfolio :optimizer :draft :constraints :blocklist]
                                 ["hl:perp:ETH"])]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :constraints :blocklist]
               ["perp:ETH"]]
              [[:portfolio :optimizer :draft :metadata :dirty?]
               true]]]
            [:effects/run-portfolio-optimizer-pipeline]]
           (actions/toggle-portfolio-optimizer-universe-instrument-exclusion-and-run
            base-state
            "perp:ETH")))
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :constraints :blocklist]
               []]
              [[:portfolio :optimizer :draft :metadata :dirty?]
               true]]]
            [:effects/run-portfolio-optimizer-pipeline]]
           (actions/toggle-portfolio-optimizer-universe-instrument-exclusion-and-run
            excluded-state
            "perp:ETH")))))
