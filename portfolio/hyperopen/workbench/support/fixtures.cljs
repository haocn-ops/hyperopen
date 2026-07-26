(ns hyperopen.workbench.support.fixtures
  (:require [hyperopen.state.trading :as trading]))

(def ^:private now-ms
  1762790400000)

(defn market
  ([] (market {}))
  ([overrides]
   (merge {:key "perp:BTC"
           :coin "BTC"
           :symbol "BTC-USDC"
           :base "BTC"
           :quote "USDC"
           :dex "HL"
           :market-type :perp
           :mark 102345.4
           :markRaw "102345.4"
           :change24h 2245.8
           :change24hPct 2.24
           :volume24h 1400000000
           :openInterest 860000000
           :fundingRate 0.000175
           :maxLeverage 40
           :szDecimals 4}
          overrides)))

(defn markets
  []
  [(market)
   (market {:key "perp:ETH"
            :coin "ETH"
            :symbol "ETH-USDC"
            :base "ETH"
            :mark 3421.5
            :markRaw "3421.5"
            :change24h -88.4
            :change24hPct -2.52
            :volume24h 820000000
            :openInterest 410000000
            :fundingRate -0.00009})
   (market {:key "spot:@1"
            :coin "@1"
            :symbol "HYPE/USDC"
            :base "HYPE"
            :quote "USDC"
            :market-type :spot
            :mark 10.14
            :markRaw "10.14"
            :change24h 0.42
            :change24hPct 4.32
            :volume24h 7300000
            :openInterest nil
            :fundingRate nil
            :maxLeverage nil})
   (market {:key "perp:SOL"
            :coin "SOL"
            :symbol "SOL-USDC"
            :base "SOL"
            :mark 201.77
            :markRaw "201.77"
            :change24h 7.05
            :change24hPct 3.62
            :volume24h 470000000
            :openInterest 190000000
            :fundingRate 0.00011})
   (market {:key "tradfi:NVDA"
            :coin "xyz:NVDA"
            :symbol "NVDA-USD"
            :base "NVDA"
            :quote "USD"
            :dex "xyz"
            :mark 128.44
            :markRaw "128.44"
            :change24h -1.88
            :change24hPct -1.44
            :volume24h 91000000
            :openInterest 36000000
            :fundingRate 0.00004
            :maxLeverage 5})
   (market {:key "perp:PUMP"
            :coin "PUMP"
            :symbol "PUMP-USDC"
            :base "PUMP"
            :mark 0.0044
            :markRaw "0.0044"
            :change24h 0.0002
            :change24hPct 4.71
            :volume24h 44000000
            :openInterest 18000000
            :fundingRate 0.00031})])

(defn asset-contexts
  []
  {"BTC" {:coin "BTC"
          :mark 102345.4
          :markRaw "102345.4"
          :oracle 102210.2
          :oracleRaw "102210.2"
          :change24h 2245.8
          :change24hPct 2.24
          :volume24h 1400000000
          :openInterest 8400
          :fundingRate 0.000175}
   "ETH" {:coin "ETH"
          :mark 3421.5
          :markRaw "3421.5"
          :oracle 3419.7
          :oracleRaw "3419.7"
          :change24h -88.4
          :change24hPct -2.52
          :volume24h 820000000
          :openInterest 5800
          :fundingRate -0.00009}
   "@1" {:coin "@1"
         :mark 10.14
         :markRaw "10.14"
         :change24h 0.42
         :change24hPct 4.32
         :volume24h 7300000}
   "SOL" {:coin "SOL"
          :mark 201.77
          :markRaw "201.77"
          :oracle 201.33
          :oracleRaw "201.33"
          :change24h 7.05
          :change24hPct 3.62
          :volume24h 470000000
          :openInterest 1200
          :fundingRate 0.00011}})

(defn orderbook
  []
  {:bids [{:px "102345.0" :sz "12.40"}
          {:px "102344.5" :sz "9.90"}
          {:px "102344.0" :sz "7.25"}
          {:px "102343.5" :sz "5.10"}
          {:px "102343.0" :sz "4.85"}
          {:px "102342.5" :sz "3.91"}]
   :asks [{:px "102345.5" :sz "10.15"}
          {:px "102346.0" :sz "11.90"}
          {:px "102346.5" :sz "6.75"}
          {:px "102347.0" :sz "4.25"}
          {:px "102347.5" :sz "3.61"}
          {:px "102348.0" :sz "2.94"}]})

(defn websocket-health
  []
  {:transport {:freshness :live
               :last-recv-at-ms now-ms}
   :topics {"l2Book|BTC" {:freshness :live
                          :last-payload-at-ms now-ms
                          :stale-threshold-ms 4000}}})

(defn balance-row
  ([] (balance-row {}))
  ([overrides]
   (merge {:key "spot-0"
           :coin "USDC (Spot)"
           :selection-coin "USDC"
           :total-balance 12450.32
           :available-balance 9800.14
           :usdc-value 12450.32
           :pnl-value 0
           :pnl-pct 0
           :amount-decimals 2}
          overrides)))

(defn position-row
  ([] (position-row {}))
  ([overrides]
   (merge {:position {:coin "BTC"
                      :leverage {:value 5}
                      :szi "0.1500"
                      :positionValue "15351.81"
                      :entryPx "101920.20"
                      :markPx "102345.40"
                      :unrealizedPnl "63.78"
                      :returnOnEquity "0.041"
                      :liquidationPx "82754.11"
                      :marginUsed "3081.50"
                      :cumFunding {:allTime "12.44"}}
           :dex "HL"}
          overrides)))

(defn open-order
  ([] (open-order {}))
  ([overrides]
   (merge {:time now-ms
           :coin "BTC"
           :type "Limit"
           :side "B"
           :sz 0.05
           :orig-sz 0.05
           :px 101950
           :reduce-only false
           :trigger nil
           :tp-trigger nil
           :sl-trigger nil
           :oid 987654321}
          overrides)))

(defn trade-row
  ([] (trade-row {}))
  ([overrides]
   (merge {:tid 1001
           :time-ms now-ms
           :coin "BTC"
           :side "Buy"
           :side-key :long
           :price 102340.1
           :size 0.025
           :trade-value 2558.5
           :fee 1.12
           :closed-pnl 42.8}
          overrides)))

(defn funding-history-row
  ([] (funding-history-row {}))
  ([overrides]
   (merge {:id "funding-1"
           :time-ms now-ms
           :coin "BTC"
           :position-size-raw 0.15
           :payment-usdc-raw -4.12
           :funding-rate-raw 0.000175}
          overrides)))

(defn order-history-row
  ([] (order-history-row {}))
  ([overrides]
   (merge {:time-ms now-ms
           :coin "xyz:NVDA"
           :side "Buy"
           :side-key :long
           :type "Limit"
           :size 0.5
           :price 128.25
           :status "Filled"
           :status-key :positive
           :oid 307891000622}
          overrides)))

(defn api-wallet-row
  ([] (api-wallet-row {}))
  ([overrides]
   (merge {:address "0x6d0d4e4f5a1d3c5b0b3b4c9c51e7d2b9b8fd6a13"
           :label "Trading Agent"
           :valid-until-ms (+ now-ms (* 7 24 60 60 1000))
           :created-at-ms (- now-ms (* 2 24 60 60 1000))
           :kind :generated}
          overrides)))

(defn vault-row
  ([] (vault-row {}))
  ([overrides]
   (merge {:vault-address "0xvault-alpha"
           :name "Basis Capture Alpha"
           :leader "0xfeed000000000000000000000000000000beef"
           :tvl 12400000
           :apr 18.4
           :your-deposit 12500
           :snapshot 8.2
           :snapshot-series [0.2 0.6 1.2 1.9 2.7 3.6 4.8 5.5 6.4 7.1 7.8 8.2]
           :is-leading? false
           :has-deposit? true
           :is-other? false
           :is-closed? false
           :is-protocol? false
           :age-days 87
           :relationship-type :normal}
          overrides)))

(defn vault-chart
  ([] (vault-chart {}))
  ([overrides]
   (merge {:axis-kind :returns
           :selected-series :returns
           :selected-timeframe :month
           :series-tabs [{:value :returns :label "Returns"}
                         {:value :pnl :label "PNL"}
                         {:value :account-value :label "Account Value"}]
           :timeframe-options [{:value :day :label "24H"}
                               {:value :week :label "7D"}
                               {:value :month :label "30D"}
                               {:value :all-time :label "All-time"}]
           :returns-benchmark {:selected-coin "BTC"
                               :selected-coins ["BTC" "ETH"]
                               :search ""
                               :suggestions-open? false
                               :suggestions [{:value "BTC" :label "BTC"}
                                             {:value "ETH" :label "ETH"}
                                             {:value "SOL" :label "SOL"}]}
           :points [{:time-ms (- now-ms (* 6 24 60 60 1000)) :value 1.1 :x-ratio 0.0 :y-ratio 0.62}
                    {:time-ms (- now-ms (* 5 24 60 60 1000)) :value 2.8 :x-ratio 0.16 :y-ratio 0.54}
                    {:time-ms (- now-ms (* 4 24 60 60 1000)) :value 4.4 :x-ratio 0.33 :y-ratio 0.46}
                    {:time-ms (- now-ms (* 3 24 60 60 1000)) :value 3.6 :x-ratio 0.5 :y-ratio 0.5}
                    {:time-ms (- now-ms (* 2 24 60 60 1000)) :value 6.2 :x-ratio 0.66 :y-ratio 0.38}
                    {:time-ms (- now-ms (* 1 24 60 60 1000)) :value 7.4 :x-ratio 0.83 :y-ratio 0.31}
                    {:time-ms now-ms :value 8.2 :x-ratio 1.0 :y-ratio 0.27}]
           :series [{:id :strategy
                     :label "Vault"
                     :stroke "#55e6ce"
                     :has-data? true
                     :points [{:time-ms (- now-ms (* 6 24 60 60 1000)) :value 1.1 :x-ratio 0.0 :y-ratio 0.62}
                              {:time-ms (- now-ms (* 5 24 60 60 1000)) :value 2.8 :x-ratio 0.16 :y-ratio 0.54}
                              {:time-ms (- now-ms (* 4 24 60 60 1000)) :value 4.4 :x-ratio 0.33 :y-ratio 0.46}
                              {:time-ms (- now-ms (* 3 24 60 60 1000)) :value 3.6 :x-ratio 0.5 :y-ratio 0.5}
                              {:time-ms (- now-ms (* 2 24 60 60 1000)) :value 6.2 :x-ratio 0.66 :y-ratio 0.38}
                              {:time-ms (- now-ms (* 1 24 60 60 1000)) :value 7.4 :x-ratio 0.83 :y-ratio 0.31}
                              {:time-ms now-ms :value 8.2 :x-ratio 1.0 :y-ratio 0.27}]}
                    {:id :benchmark-0
                     :label "BTC"
                     :coin "BTC"
                     :stroke "#ff9f1a"
                     :has-data? true
                     :points [{:time-ms (- now-ms (* 6 24 60 60 1000)) :value 0.8 :x-ratio 0.0 :y-ratio 0.64}
                              {:time-ms (- now-ms (* 5 24 60 60 1000)) :value 1.9 :x-ratio 0.16 :y-ratio 0.58}
                              {:time-ms (- now-ms (* 4 24 60 60 1000)) :value 3.1 :x-ratio 0.33 :y-ratio 0.51}
                              {:time-ms (- now-ms (* 3 24 60 60 1000)) :value 2.4 :x-ratio 0.5 :y-ratio 0.55}
                              {:time-ms (- now-ms (* 2 24 60 60 1000)) :value 4.4 :x-ratio 0.66 :y-ratio 0.43}
                              {:time-ms (- now-ms (* 1 24 60 60 1000)) :value 5.3 :x-ratio 0.83 :y-ratio 0.38}
                              {:time-ms now-ms :value 6.1 :x-ratio 1.0 :y-ratio 0.34}]}]
           :y-ticks [{:value 0 :y-ratio 0.82}
                     {:value 2.5 :y-ratio 0.62}
                     {:value 5 :y-ratio 0.42}
                     {:value 7.5 :y-ratio 0.22}
                     {:value 10 :y-ratio 0.04}]
           :hover {:active? false}}
          overrides)))

(defn vault-activity
  ([] (vault-activity {}))
  ([overrides]
   (merge {:selected-activity-tab :trade-history
           :activity-tabs [{:value :performance-metrics :label "Performance"}
                           {:value :trade-history :label "Fills" :count 12}
                           {:value :funding-history :label "Funding" :count 6}
                           {:value :order-history :label "Orders" :count 15}]
           :activity-table-config {:trade-history {:columns [{:id :time-ms :label "Time"}
                                                            {:id :coin :label "Coin"}
                                                            {:id :side :label "Side"}
                                                            {:id :price :label "Price"}
                                                            {:id :size :label "Size"}
                                                            {:id :trade-value :label "Trade Value"}
                                                            {:id :fee :label "Fee"}
                                                            {:id :closed-pnl :label "Closed PNL"}]
                                                 :supports-direction-filter? true}
                                   :funding-history {:columns [{:id :time-ms :label "Time"}
                                                              {:id :coin :label "Coin"}
                                                              {:id :funding-rate :label "Rate"}
                                                              {:id :position-size :label "Size"}
                                                              {:id :payment :label "Payment"}]
                                                     :supports-direction-filter? true}
                                   :order-history {:columns [{:id :time-ms :label "Time"}
                                                            {:id :coin :label "Coin"}
                                                            {:id :side :label "Side"}
                                                            {:id :type :label "Type"}
                                                            {:id :size :label "Size"}
                                                            {:id :price :label "Price"}
                                                            {:id :status :label "Status"}]
                                                   :supports-direction-filter? true}
                                   :performance-metrics {:columns [] :supports-direction-filter? false}}
           :performance-metrics {:benchmark-selected? true
                                 :benchmark-label "BTC"
                                 :benchmark-columns [{:coin "BTC" :label "BTC"}]
                                 :groups [{:id :performance
                                           :rows [{:key :net-return :label "Net Return" :kind :percent :portfolio-value 0.082 :benchmark-values {"BTC" 0.061}}
                                                  {:key :sharpe :label "Sharpe" :kind :ratio :portfolio-value 1.88 :benchmark-values {"BTC" 1.42}}
                                                  {:key :days :label "Days Following" :kind :integer :portfolio-value 87 :benchmark-values {"BTC" 87}}]}]
                                 :timeframe-options [{:value :month :label "30D"}]
                                 :selected-timeframe :month}
           :activity-direction-filter :all
           :activity-filter-open? false
           :activity-filter-options [{:value :all :label "All"}
                                     {:value :long :label "Long"}
                                     {:value :short :label "Short"}]
           :activity-sort-state-by-tab {:trade-history {:column :time-ms :direction :desc}
                                        :funding-history {:column :time-ms :direction :desc}
                                        :order-history {:column :time-ms :direction :desc}}
           :activity-loading {}
           :activity-errors {}
           :activity-fills [(trade-row)
                            (trade-row {:time-ms (- now-ms 3600000)
                                        :coin "ETH"
                                        :side "Sell"
                                        :side-key :short
                                        :price 3418.2
                                        :size 1.2
                                        :trade-value 4101.8
                                        :fee 1.75
                                        :closed-pnl -18.4})]
           :activity-funding-history [{:time-ms now-ms
                                       :coin "BTC"
                                       :funding-rate 0.000175
                                       :position-size 0.15
                                       :side-key :long
                                       :payment -4.12}]
           :activity-order-history [(order-history-row)
                                    (order-history-row {:time-ms (- now-ms 7200000)
                                                        :coin "ETH"
                                                        :side "Sell"
                                                        :side-key :short
                                                        :type "Market"
                                                        :size 1.2
                                                        :price 3410
                                                        :status "Canceled"
                                                        :status-key :neutral})]}
          overrides)))

(defn vault-transfer-modal
  ([] (vault-transfer-modal {}))
  ([overrides]
   (merge {:open? true
           :title "Deposit to Basis Capture Alpha"
           :mode :deposit
           :deposit-max-display "12,450.32"
           :deposit-max-input "12450.32"
           :deposit-lockup-copy "Deposits are queued and become active on the next vault roll."
           :amount-input ""
           :withdraw-all? false
           :submitting? false
           :error nil
           :preview-ok? true
           :preview-message "Review transfer"
           :confirm-label "Deposit"
           :submit-disabled? false}
          overrides)))

(defn footer-health
  []
  {:transport {:freshness :live
               :last-recv-at-ms now-ms}
   :groups {:market_data {:worst-status :live}
            :orders_oms {:worst-status :live}
            :account {:worst-status :idle}}})

(defn order-form-state
  ([] (order-form-state {} {}))
  ([order-form-overrides]
   (order-form-state order-form-overrides {}))
  ([order-form-overrides order-form-ui-overrides]
   (let [order-form-ui-keys
         #{:entry-mode
           :ui-leverage
           :margin-mode
           :size-input-mode
           :size-input-source
           :size-display
           :margin-mode-dropdown-open?
           :leverage-popover-open?
           :leverage-draft
           :size-unit-dropdown-open?
           :tpsl-unit-dropdown-open?
           :tif-dropdown-open?
           :pro-order-type-dropdown-open?
           :price-input-focused?
           :tpsl-panel-open?}
         ui-overrides-from-form (select-keys order-form-overrides order-form-ui-keys)
         normalized-order-form-overrides (reduce dissoc order-form-overrides order-form-ui-keys)
         merged-form (merge (trading/default-order-form) normalized-order-form-overrides)
         inferred-entry-mode (when (contains? normalized-order-form-overrides :type)
                               (trading/entry-mode-for-type (:type merged-form)))
         final-entry-mode (or (:entry-mode order-form-ui-overrides)
                              (:entry-mode ui-overrides-from-form)
                              inferred-entry-mode)
         order-form-ui (cond-> (merge (trading/default-order-form-ui)
                                      ui-overrides-from-form
                                      order-form-ui-overrides)
                         final-entry-mode
                         (assoc :entry-mode final-entry-mode))]
     {:active-asset "BTC"
      :active-market {:coin "BTC"
                      :quote "USDC"
                      :mark 100
                      :maxLeverage 40
                      :market-type :perp
                      :szDecimals 4}
      :orderbooks {"BTC" {:bids [{:px "99"}]
                          :asks [{:px "101"}]}}
      :webdata2 {:clearinghouseState {:marginSummary {:accountValue "1000"
                                                      :totalMarginUsed "250"}}}
      :order-form merged-form
      :order-form-ui order-form-ui})))

(defn funding-modal-state
  ([] (funding-modal-state {}))
  ([modal-overrides]
   {:wallet {:address "0x1234567890abcdef1234567890abcdef12345678"}
    :spot {:clearinghouse-state {:balances [{:coin "USDC" :available "12.5" :total "12.5" :hold "0"}
                                            {:coin "BTC" :available "1.25" :total "1.25" :hold "0"}]}}
    :webdata2 {:clearinghouseState {:availableToWithdraw "8.5"
                                    :marginSummary {:accountValue "20"
                                                    :totalMarginUsed "11.5"}}}
    :funding-ui {:modal (merge {:open? true
                                :mode :deposit
                                :deposit-step :asset-select
                                :deposit-search-input ""
                                :deposit-selected-asset-key nil
                                :withdraw-step :asset-select
                                :withdraw-search-input ""
                                :withdraw-selected-asset-key nil
                                :transfer-step :asset-select
                                :transfer-search-input ""
                                :transfer-selected-asset-key nil
                                :send-step :asset-select
                                :send-search-input ""
                                :send-selected-asset-key nil
                                :amount-input ""}
                               modal-overrides)}}))

(defn api-wallets-state
  ([] (api-wallets-state {}))
  ([overrides]
   (merge {:wallet {:connected? true
                    :address "0x4b20993bc481177ec7e8f571cecae8a9e22c02db"}
           :api-wallets {:server-time-ms now-ms
                         :loading {:extra-agents? false
                                   :default-agent? false}
                         :errors {:extra-agents nil
                                  :default-agent nil}
                         :default-agent-row {:row-kind :default
                                             :name "Main Agent"
                                             :approval-name "Main Agent"
                                             :address "0x8e6f98f9f09d8a5f6080b88b423d1736db0ec7be"
                                             :valid-until-ms (+ now-ms (* 45 24 60 60 1000))}
                         :extra-agents [{:row-kind :generated
                                         :name "Desk Wallet"
                                         :approval-name "Desk Wallet"
                                         :address "0xa9a94f7ad68eb6d264d240f438ebf4ec4cdbdd69"
                                         :valid-until-ms (+ now-ms (* 7 24 60 60 1000))}]}
           :api-wallets-ui {:sort {:column :name
                                   :direction :asc}
                            :form {:name "Desk Wallet"
                                   :address "0xa9a94f7ad68eb6d264d240f438ebf4ec4cdbdd69"
                                   :days-valid "30"}
                            :generated {:address "0xa9a94f7ad68eb6d264d240f438ebf4ec4cdbdd69"
                                        :private-key "0x7f4f0dbca844ea12f2d0cfafab9f3c7cb9a2fd5ee7e0cf54ec1cb339f7f153b8"}
                            :form-error nil
                            :modal {:open? false
                                    :type :authorize
                                    :row nil
                                    :submitting? false
                                    :error nil}}}
          overrides)))

(defn vaults-list-state
  ([] (vaults-list-state {}))
  ([overrides]
   (merge {:wallet {:address "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"}
           :vaults-ui {:search-query ""
                       :filter-leading? true
                       :filter-deposited? true
                       :filter-others? true
                       :filter-closed? false
                       :snapshot-range :month
                       :user-vaults-page-size 10
                       :user-vaults-page 1
                       :user-page-size-dropdown-open? false
                       :sort {:column :tvl
                              :direction :desc}}
           :vaults {:loading {:index? false
                              :summaries? false}
                    :errors {:index nil
                             :summaries nil}
                    :user-equity-by-address {"0x2222222222222222222222222222222222222222" {:equity 25}}
                    :merged-index-rows [{:name "Alpha"
                                         :vault-address "0x1111111111111111111111111111111111111111"
                                         :leader "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
                                         :tvl 1200000
                                         :apr 0.12
                                         :is-closed? false
                                         :create-time-ms (- now-ms (* 3 24 60 60 1000))
                                         :snapshot-by-key {:month [0.1 0.2 0.25 0.29]}}
                                        {:name "Beta"
                                         :vault-address "0x2222222222222222222222222222222222222222"
                                         :leader "0x3333333333333333333333333333333333333333"
                                         :tvl 800000
                                         :apr 0.08
                                         :is-closed? false
                                         :create-time-ms (- now-ms (* 8 24 60 60 1000))
                                         :snapshot-by-key {:month [0.05 0.09 0.1 0.12]}}
                                        {:name "Gamma"
                                         :vault-address "0x3333333333333333333333333333333333333333"
                                         :leader "0x4444444444444444444444444444444444444444"
                                         :tvl 650000
                                         :apr -0.04
                                         :is-closed? true
                                         :create-time-ms (- now-ms (* 40 24 60 60 1000))
                                         :snapshot-by-key {:month [-0.02 -0.03 -0.02 -0.04]}}]}}
          overrides)))

(defn position-tpsl-modal
  ([] (position-tpsl-modal {}))
  ([overrides]
   (merge {:open? true
           :coin "BTC"
           :side :long
           :tab :tp
           :tp-trigger-price "104500"
           :tp-order-type :market
           :tp-pnl-mode :usd
           :tp-pnl-input "1500"
           :tp-size-input "0.1500"
           :tp-size-percent-input "100"
           :sl-trigger-price "100000"
           :sl-order-type :market
           :sl-pnl-mode :usd
           :sl-pnl-input "-600"
           :sl-size-input "0.1500"
           :sl-size-percent-input "100"
           :position-size "0.1500"
           :entry-price "101920.20"
           :mark-price "102345.40"
           :price-decimals 2
           :locale "en-US"}
          overrides)))

(defn position-reduce-popover
  ([] (position-reduce-popover {}))
  ([overrides]
   (merge {:open? true
           :coin "BTC"
           :position-size "0.1500"
           :close-type :market
           :size-percent-input "50"
           :limit-price ""
           :mid-price "102345.4"
           :anchor {:right 1220
                    :top 480
                    :bottom 520
                    :viewport-width 1440
                    :viewport-height 900}}
          overrides)))

(defn position-margin-modal
  ([] (position-margin-modal {}))
  ([overrides]
   (merge {:open? true
           :coin "BTC"
           :amount-input "250"
           :amount-percent-input "25"
           :available-balance "12450.32"
           :position-margin "3081.50"
           :liquidation-price "82754.11"
           :mark-price "102345.40"
           :price-decimals 2
           :prefill-source nil
           :anchor {:right 1220
                    :top 480
                    :bottom 520
                    :viewport-width 1440
                    :viewport-height 900}
           :locale "en-US"}
          overrides)))
