(ns hyperopen.workbench.scenes.account.balances-scenes
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.workbench.support.fixtures :as fixtures]
            [hyperopen.workbench.support.layout :as layout]
            [hyperopen.views.account-info.tabs.balances :as balances]))

(portfolio/configure-scenes
  {:title "Balances"
   :collection :account})

(defn- balance-rows
  []
  [(fixtures/balance-row)
   (fixtures/balance-row {:key "btc"
                          :coin "BTC"
                          :selection-coin "BTC"
                          :total-balance 0.15
                          :available-balance 0.12
                          :usdc-value 15351.81
                          :pnl-value 63.78
                          :pnl-pct 4.1
                          :amount-decimals 4})
   (fixtures/balance-row {:key "sol"
                          :coin "SOL"
                          :selection-coin "SOL"
                          :total-balance 0.0021
                          :available-balance 0.0021
                          :usdc-value 0.43
                          :pnl-value -0.01
                          :pnl-pct -0.5
                          :amount-decimals 4
                          :transfer-disabled? true})])

(defn- balances-panel
  [content]
  (layout/page-shell
   (layout/desktop-shell
    (layout/panel-shell {:class ["h-[640px]"]}
     content))))

(portfolio/defscene default
  []
  (balances-panel
   (balances/balances-tab-content (balance-rows)
                                  false
                                  {:column "USDC Value" :direction :desc})))

(portfolio/defscene hide-small
  []
  (balances-panel
   (balances/balances-tab-content (balance-rows)
                                  true
                                  {:column "USDC Value" :direction :desc})))

(portfolio/defscene coin-search-empty
  []
  (balances-panel
   (balances/balances-tab-content (balance-rows)
                                  false
                                  {:column "Coin" :direction :asc}
                                  "doge")))

(portfolio/defscene mobile-expanded-card
  []
  (layout/page-shell
   (layout/mobile-shell
    (layout/panel-shell {:class ["h-[680px]" "p-0"]}
     (balances/balances-tab-content (balance-rows)
                                    false
                                    {:column "USDC Value" :direction :desc}
                                    ""
                                    {:balances "btc"})))))

(portfolio/defscene transfer-disabled
  []
  (balances-panel
   (balances/balances-tab-content
    [(fixtures/balance-row {:key "usdc"
                            :transfer-disabled? true})
     (fixtures/balance-row {:key "sol"
                            :coin "SOL"
                            :selection-coin "SOL"
                            :total-balance 3.4
                            :available-balance 3.4
                            :usdc-value 681.22
                            :pnl-value 14.8
                            :pnl-pct 2.1
                            :transfer-disabled? true})]
    false
    {:column "Coin" :direction :asc})))
