(ns hyperopen.portfolio.optimizer.universe-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.actions :as actions]))

(def ^:private selection-prefetch-effect
  [:effects/load-portfolio-optimizer-history
   {:source :selection-prefetch
    :queue? true
    :merge? true}])

(defn- queued-prefetch-state
  [instruments]
  {:queue (vec instruments)
   :active-instrument-id nil
   :by-instrument-id
   (into {}
         (map (fn [instrument]
                [(:instrument-id instrument)
                 {:status :queued
                  :started-at-ms nil
                  :completed-at-ms nil
                  :error nil
                  :warnings []}]))
         instruments)})

(defn- effect-values-by-path
  [effects]
  (reduce (fn [acc effect]
            (case (first effect)
              :effects/save
              (assoc acc (second effect) (nth effect 2))

              :effects/save-many
              (reduce (fn [acc [path value]]
                        (assoc acc path value))
                      acc
                      (second effect))

              acc))
          {}
          (or effects [])))

(deftest set-draft-universe-from-current-holdings-test
  (let [btc-instrument {:instrument-id "perp:BTC"
                        :market-type :perp
                        :coin "BTC"
                        :shortable? true
                        :position-side :long}
        purr-instrument {:instrument-id "spot:PURR"
                         :market-type :spot
                         :coin "PURR"
                         :shortable? false
                         :position-side :long
                         :symbol "PURR/USDC"
                         :base "PURR"
                         :quote "USDC"}
        state {:webdata2 {:clearinghouseState
                          {:marginSummary {:accountValue "1000"}
                           :assetPositions
                           [{:position {:coin "BTC"
                                        :szi "0.5"
                                        :positionValue "500"
                                        :leverage {:type "cross"
                                                   :value "5"}}}]}}
               :spot {:balances [{:coin "PURR"
                                  :total "10"}]}
               :asset-selector {:market-by-key
                                {"spot:PURR" {:key "spot:PURR"
                                              :market-type :spot
                                              :coin "PURR/USDC"
                                              :symbol "PURR/USDC"
                                              :base "PURR"
                                              :quote "USDC"
                                              :mark "2"}}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :universe]
               [btc-instrument purr-instrument]]
              [[:portfolio :optimizer :history-prefetch]
               (queued-prefetch-state [btc-instrument purr-instrument])]
              [[:portfolio :optimizer :draft :metadata :dirty?]
               true]]]
            selection-prefetch-effect]
           (actions/set-portfolio-optimizer-universe-from-current state)))))

(deftest set-draft-universe-from-current-holdings-ignores-empty-snapshot-test
  (is (= []
         (actions/set-portfolio-optimizer-universe-from-current {}))))

(deftest add-draft-universe-instrument-from-asset-selector-market-test
  (let [btc-instrument {:instrument-id "perp:BTC"
                        :market-type :perp
                        :coin "BTC"
                        :shortable? true}
        eth-instrument {:instrument-id "perp:ETH"
                        :market-type :perp
                        :coin "ETH"
                        :shortable? true
                        :position-side :long
                        :dex "hl"
                        :symbol "ETH-USDC"
                        :base "ETH"
                        :quote "USDC"}
        purr-instrument {:instrument-id "spot:PURR/USDC"
                         :market-type :spot
                         :coin "PURR/USDC"
                         :shortable? false
                         :position-side :long
                         :symbol "PURR/USDC"
                         :base "PURR"
                         :quote "USDC"}
        state {:portfolio {:optimizer {:draft {:universe [btc-instrument]}}}
               :asset-selector {:market-by-key
                                {"perp:ETH" {:key "perp:ETH"
                                             :market-type :perp
                                             :coin "ETH"
                                             :symbol "ETH-USDC"
                                             :base "ETH"
                                             :quote "USDC"
                                             :dex "hl"
                                             :maxLeverage 50}
                                 "spot:PURR/USDC" {:key "spot:PURR/USDC"
                                                   :market-type :spot
                                                   :coin "PURR/USDC"
                                                   :symbol "PURR/USDC"
                                                   :base "PURR"
                                                   :quote "USDC"}}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :universe]
               [btc-instrument eth-instrument]]
              [[:portfolio-ui :optimizer :universe-search-query]
               ""]
              [[:portfolio-ui :optimizer :universe-search-active-index]
               0]
              [[:portfolio :optimizer :history-prefetch]
               (queued-prefetch-state [eth-instrument])]
              [[:portfolio :optimizer :draft :metadata :dirty?]
               true]]]
            selection-prefetch-effect]
           (actions/add-portfolio-optimizer-universe-instrument
            state
            "perp:ETH")))
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :universe]
               [btc-instrument purr-instrument]]
              [[:portfolio-ui :optimizer :universe-search-query]
               ""]
              [[:portfolio-ui :optimizer :universe-search-active-index]
               0]
              [[:portfolio :optimizer :history-prefetch]
               (queued-prefetch-state [purr-instrument])]
              [[:portfolio :optimizer :draft :metadata :dirty?]
               true]]]
            selection-prefetch-effect]
           (actions/add-portfolio-optimizer-universe-instrument
            state
            "spot:PURR/USDC")))))

(deftest set-draft-add-asset-open-updates-results-selector-ui-state-test
  (is (= [[:effects/save-many
           [[[:portfolio-ui :optimizer :draft-add-asset-open?] true]
            [[:portfolio-ui :optimizer :universe-search-query] ""]
            [[:portfolio-ui :optimizer :universe-search-active-index] 0]]]]
         (actions/set-portfolio-optimizer-draft-add-asset-open
          {:portfolio-ui {:optimizer {:universe-search-query "eth"
                                      :universe-search-active-index 2}}}
          true)))
  (is (= [[:effects/save-many
           [[[:portfolio-ui :optimizer :draft-add-asset-open?] false]
            [[:portfolio-ui :optimizer :universe-search-query] ""]
            [[:portfolio-ui :optimizer :universe-search-active-index] 0]]]]
         (actions/set-portfolio-optimizer-draft-add-asset-open
          {:portfolio-ui {:optimizer {:universe-search-query "eth"
                                      :universe-search-active-index 2}}}
          false))))

(deftest add-draft-universe-instrument-and-run-closes-selector-before-recompute-test
  (let [btc-instrument {:instrument-id "perp:BTC"
                        :market-type :perp
                        :coin "BTC"
                        :shortable? true}
        eth-instrument {:instrument-id "perp:ETH"
                        :market-type :perp
                        :coin "ETH"
                        :shortable? true
                        :position-side :long
                        :symbol "ETH-USDC"
                        :base "ETH"
                        :quote "USDC"}
        state {:portfolio {:optimizer {:draft {:universe [btc-instrument]}}}
               :portfolio-ui {:optimizer {:draft-add-asset-open? true
                                          :universe-search-query "eth"
                                          :universe-search-active-index 0}}
               :asset-selector {:market-by-key
                                {"perp:ETH" {:key "perp:ETH"
                                             :market-type :perp
                                             :coin "ETH"
                                             :symbol "ETH-USDC"
                                             :base "ETH"
                                             :quote "USDC"}}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :universe]
               [btc-instrument eth-instrument]]
              [[:portfolio-ui :optimizer :universe-search-query]
               ""]
              [[:portfolio-ui :optimizer :universe-search-active-index]
               0]
              [[:portfolio-ui :optimizer :draft-add-asset-open?]
               false]
              [[:portfolio :optimizer :draft :metadata :dirty?]
               true]]]
            [:effects/run-portfolio-optimizer-pipeline]]
           (actions/add-portfolio-optimizer-universe-instrument-and-run
            state
            "perp:ETH")))))

(deftest add-draft-universe-instrument-and-run-preserves-black-litterman-run-gate-test
  (let [btc-instrument {:instrument-id "perp:BTC"
                        :market-type :perp
                        :coin "BTC"
                        :shortable? true}
        eth-instrument {:instrument-id "perp:ETH"
                        :market-type :perp
                        :coin "ETH"
                        :shortable? true
                        :position-side :long}
        state {:portfolio {:optimizer {:draft {:universe [btc-instrument]
                                               :return-model {:kind :black-litterman
                                                              :views []}
                                               :risk-model {:kind :sample-covariance}
                                               :constraints {:long-only? true}}}}
               :portfolio-ui {:optimizer {:draft-add-asset-open? true
                                          :black-litterman-editor
                                          {:selected-kind :absolute
                                           :drafts {:absolute {:instrument-id "perp:BTC"
                                                               :return-text ""
                                                               :return-text-touched? false
                                                               :confidence :medium
                                                               :horizon :3m
                                                               :notes ""}}
                                           :errors {}}}}
               :asset-selector {:market-by-key
                                {"perp:ETH" {:key "perp:ETH"
                                             :market-type :perp
                                             :coin "ETH"}}}}
        effects (actions/add-portfolio-optimizer-universe-instrument-and-run
                 state
                 "perp:ETH")
        values (effect-values-by-path effects)]
    (is (= [:effects/save-many
            :effects/save-many]
           (mapv first effects)))
    (is (= [btc-instrument eth-instrument]
           (get values [:portfolio :optimizer :draft :universe])))
    (is (= false
           (get values [:portfolio-ui :optimizer :draft-add-asset-open?])))
    (is (= "Add a view before running Use my views."
           (get values [:portfolio-ui :optimizer :black-litterman-editor :errors :return-text])))
    (is (not (some #(= :effects/run-portfolio-optimizer-pipeline (first %))
                   effects)))))

(deftest toggle-draft-universe-instrument-exclusion-and-run-keeps-row-in-universe-test
  (let [btc-instrument {:instrument-id "perp:BTC"
                        :market-type :perp
                        :coin "BTC"
                        :shortable? true}
        eth-instrument {:instrument-id "perp:ETH"
                        :market-type :perp
                        :coin "ETH"
                        :shortable? true}
        state {:portfolio {:optimizer {:draft {:universe [btc-instrument
                                                           eth-instrument]
                                               :constraints {:blocklist []}}}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :constraints :blocklist]
               ["perp:ETH"]]
              [[:portfolio :optimizer :draft :metadata :dirty?]
               true]]]
            [:effects/run-portfolio-optimizer-pipeline]]
           (actions/toggle-portfolio-optimizer-universe-instrument-exclusion-and-run
            state
            "perp:ETH")))
    (is (= [btc-instrument eth-instrument]
           (get-in state [:portfolio :optimizer :draft :universe])))))

(deftest toggle-draft-universe-instrument-exclusion-and-run-reincludes-blocklisted-row-test
  (let [btc-instrument {:instrument-id "perp:BTC"
                        :market-type :perp
                        :coin "BTC"
                        :shortable? true}
        eth-instrument {:instrument-id "perp:ETH"
                        :market-type :perp
                        :coin "ETH"
                        :shortable? true}
        state {:portfolio {:optimizer {:draft {:universe [btc-instrument
                                                           eth-instrument]
                                               :constraints {:blocklist ["perp:ETH"]}}}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :constraints :blocklist]
               []]
              [[:portfolio :optimizer :draft :metadata :dirty?]
               true]]]
            [:effects/run-portfolio-optimizer-pipeline]]
           (actions/toggle-portfolio-optimizer-universe-instrument-exclusion-and-run
            state
            "perp:ETH")))))

(deftest set-draft-universe-instrument-side-updates-row-and-marks-dirty-test
  (let [btc-instrument {:instrument-id "perp:BTC"
                        :market-type :perp
                        :coin "BTC"
                        :shortable? true
                        :position-side :long}
        eth-instrument {:instrument-id "perp:ETH"
                        :market-type :perp
                        :coin "ETH"
                        :shortable? true
                        :position-side :long}
        state {:portfolio {:optimizer {:draft {:universe [btc-instrument
                                                           eth-instrument]}}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :universe]
               [(assoc btc-instrument :position-side :short)
                eth-instrument]]
              [[:portfolio :optimizer :draft :metadata :dirty?]
               true]]]]
           (actions/set-portfolio-optimizer-universe-instrument-side
            state
            "perp:BTC"
            :short)))))

(deftest set-draft-universe-instrument-side-keeps-non-shortable-row-long-test
  (let [spot-instrument {:instrument-id "spot:PURR"
                         :market-type :spot
                         :coin "PURR"
                         :shortable? false
                         :position-side :long}
        state {:portfolio {:optimizer {:draft {:universe [spot-instrument]}}}}]
    (is (= []
           (actions/set-portfolio-optimizer-universe-instrument-side
            state
            "spot:PURR"
            :short)))))

(deftest set-draft-universe-instrument-side-and-run-reruns-after-side-change-test
  (let [btc-instrument {:instrument-id "perp:BTC"
                        :market-type :perp
                        :coin "BTC"
                        :shortable? true
                        :position-side :long}
        state {:portfolio {:optimizer {:draft {:universe [btc-instrument]
                                               :return-model {:kind :historical-mean}
                                               :risk-model {:kind :sample-covariance}
                                               :constraints {:long-only? false}}}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :universe]
               [(assoc btc-instrument :position-side :short)]]
              [[:portfolio :optimizer :draft :metadata :dirty?]
               true]]]
            [:effects/run-portfolio-optimizer-pipeline]]
           (actions/set-portfolio-optimizer-universe-instrument-side-and-run
            state
            "perp:BTC"
            :short)))))

(deftest add-draft-universe-instrument-preserves-history-discovery-backend-id-test
  (let [eth-instrument {:instrument-id "perp:ETH"
                        :market-type :perp
                        :coin "ETH"
                        :shortable? true
                        :position-side :long
                        :optimizer-history/instrument-id "hl:perp:ETH"
                        :optimizer-history/display-symbol "ETH"
                        :optimizer-history/instrument-kind :hl-perp
                        :optimizer-history/history-status :available
                        :optimizer-history/quality-status :passed}
        state {:portfolio {:optimizer
                           {:draft {:universe []}
                            :history-discovery
                            {:backend-id-by-local-id {"perp:ETH" "hl:perp:ETH"}
                             :instruments-by-backend-id
                             {"hl:perp:ETH"
                              {:instrument-id "hl:perp:ETH"
                               :display-symbol "ETH"
                               :instrument-kind :hl-perp
                               :history {:status :available
                                         :quality-status :passed}}}}}}
               :asset-selector {:market-by-key
                                {"perp:ETH" {:key "perp:ETH"
                                             :market-type :perp
                                             :coin "ETH"}}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :universe]
               [eth-instrument]]
              [[:portfolio-ui :optimizer :universe-search-query]
               ""]
              [[:portfolio-ui :optimizer :universe-search-active-index]
               0]
              [[:portfolio :optimizer :history-prefetch]
               (queued-prefetch-state [eth-instrument])]
              [[:portfolio :optimizer :draft :metadata :dirty?]
               true]]]
            selection-prefetch-effect]
           (actions/add-portfolio-optimizer-universe-instrument
            state
            "perp:ETH")))))

(deftest add-draft-universe-instrument-from-vault-row-test
  (let [vault-address "0x1111111111111111111111111111111111111111"
        vault-instrument {:instrument-id (str "vault:" vault-address)
                          :market-type :vault
                          :coin (str "vault:" vault-address)
                          :vault-address vault-address
                          :shortable? false
                          :position-side :long
                          :name "Alpha Yield"
                          :symbol "Alpha Yield"
                          :tvl 500}
        state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"
                                                            :market-type :perp
                                                            :coin "BTC"
                                                            :shortable? true}]}}}
               :vaults {:merged-index-rows [{:name "Alpha Yield"
                                             :vault-address "0x1111111111111111111111111111111111111111"
                                             :relationship {:type :normal}
                                             :tvl 500}]}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :universe]
               [{:instrument-id "perp:BTC"
                 :market-type :perp
                 :coin "BTC"
                 :shortable? true}
                vault-instrument]]
              [[:portfolio-ui :optimizer :universe-search-query]
               ""]
              [[:portfolio-ui :optimizer :universe-search-active-index]
               0]
              [[:portfolio :optimizer :history-prefetch]
               (queued-prefetch-state [vault-instrument])]
              [[:portfolio :optimizer :draft :metadata :dirty?]
               true]]]
            selection-prefetch-effect]
           (actions/add-portfolio-optimizer-universe-instrument
            state
            (str "vault:" vault-address))))))

(deftest add-draft-universe-instrument-skips-prefetch-when-history-is-loaded-test
  (let [eth-instrument {:instrument-id "perp:ETH"
                        :market-type :perp
                        :coin "ETH"
                        :shortable? true
                        :position-side :long}
        state {:portfolio {:optimizer
                           {:draft {:universe []}
                            :history-data {:candle-history-by-coin
                                           {"ETH" [{:time 1000 :close "100"}
                                                   {:time 2000 :close "101"}]}
                                           :funding-history-by-coin
                                           {"ETH" [{:time-ms 1000
                                                   :funding-rate-raw 0}]}}}}
               :asset-selector {:market-by-key
                                {"perp:ETH" {:key "perp:ETH"
                                             :market-type :perp
                                             :coin "ETH"}}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :universe]
               [eth-instrument]]
              [[:portfolio-ui :optimizer :universe-search-query]
               ""]
              [[:portfolio-ui :optimizer :universe-search-active-index]
               0]
              [[:portfolio :optimizer :draft :metadata :dirty?]
               true]]]]
           (actions/add-portfolio-optimizer-universe-instrument
            state
            "perp:ETH")))))

(deftest add-draft-universe-instrument-rejects-missing-or-duplicate-market-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"
                                                            :market-type :perp
                                                            :coin "BTC"}]}}}
               :asset-selector {:market-by-key {"perp:BTC" {:key "perp:BTC"
                                                            :market-type :perp
                                                            :coin "BTC"}}}}]
    (is (= []
           (actions/add-portfolio-optimizer-universe-instrument
            state
            "perp:BTC")))
    (is (= []
           (actions/add-portfolio-optimizer-universe-instrument
            state
            "perp:ETH")))
    (is (= []
           (actions/add-portfolio-optimizer-universe-instrument
            state
            " ")))))

(deftest remove-draft-universe-instrument-cleans-dependent-constraints-test
  (let [state {:portfolio
               {:optimizer
                {:draft
                 {:universe [{:instrument-id "perp:BTC"
                              :market-type :perp
                              :coin "BTC"}
                             {:instrument-id "perp:ETH"
                              :market-type :perp
                              :coin "ETH"}]
                  :constraints {:allowlist ["perp:BTC" "perp:ETH"]
                                :blocklist ["perp:ETH"]
                                :held-locks ["perp:ETH"]
                                :asset-overrides {"perp:ETH" {:max-weight 0.2}
                                                  "perp:BTC" {:max-weight 0.5}}
                                :perp-leverage {"perp:ETH" {:max-weight 0.4}}}}}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :universe]
               [{:instrument-id "perp:BTC"
                 :market-type :perp
                 :coin "BTC"}]]
              [[:portfolio :optimizer :draft :constraints :allowlist]
               ["perp:BTC"]]
              [[:portfolio :optimizer :draft :constraints :blocklist]
               []]
              [[:portfolio :optimizer :draft :constraints :held-locks]
               []]
              [[:portfolio :optimizer :draft :constraints :asset-overrides]
               {"perp:BTC" {:max-weight 0.5}}]
              [[:portfolio :optimizer :draft :constraints :perp-leverage]
               {}]
              [[:portfolio :optimizer :draft :metadata :dirty?]
               true]]]]
           (actions/remove-portfolio-optimizer-universe-instrument
            state
            "perp:ETH")))
    (is (= []
           (actions/remove-portfolio-optimizer-universe-instrument
            state
            "perp:SOL")))))
