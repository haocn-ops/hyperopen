(ns hyperopen.workbench.scenes.trade.account-equity-scenes
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.workbench.support.layout :as layout]
            [hyperopen.views.account-equity-view :as account-equity-view]))

(portfolio/configure-scenes
  {:title "Account Equity"
   :collection :trade})

(defn- account-state
  [overrides]
  (merge {:account {:mode :classic}
          :wallet {:connected? true
                   :address "0x4b20993bc481177ec7e8f571cecae8a9e22c02db"}
          :webdata2 {:clearinghouseState {:marginSummary {:accountValue "20450.45"
                                                          :totalNtlPos "7200.0"
                                                          :totalRawUsd "19100.45"
                                                          :totalMarginUsed "1250.0"}
                                          :crossMarginSummary {:accountValue "20450.45"
                                                               :totalNtlPos "7200.0"
                                                               :totalRawUsd "19100.45"
                                                               :totalMarginUsed "1250.0"}
                                          :crossMaintenanceMarginUsed "420.0"
                                          :assetPositions [{:position {:coin "BTC"
                                                                       :szi "0.15"
                                                                       :positionValue "15351.81"
                                                                       :entryPx "101920.20"
                                                                       :markPx "102345.40"
                                                                       :unrealizedPnl "63.78"}}]}}
          :spot {:balances [{:coin "USDC"
                             :available "12450.32"
                             :hold "0"
                             :total "12450.32"}]}
          :perp-dex-clearinghouse {}
          :asset-selector {:market-by-key {}}}
         overrides))

(portfolio/defscene connected-classic
  []
  (layout/page-shell
   (layout/desktop-shell {:class ["max-w-[360px]"]}
    (account-equity-view/account-equity-view (account-state {}) {:fill-height? false}))))

(portfolio/defscene connected-unified
  []
  (layout/page-shell
   (layout/desktop-shell {:class ["max-w-[360px]"]}
    (account-equity-view/account-equity-view (account-state {:account {:mode :unified}}) {:fill-height? false}))))

(portfolio/defscene funding-actions-disabled
  []
  (layout/page-shell
   (layout/desktop-shell {:class ["max-w-[360px]"]}
    (account-equity-view/account-equity-view (account-state {:account-context {:spectate-mode {:active? true
                                                                                                :address "0x1234567890abcdef1234567890abcdef12345678"}}})
                                             {:fill-height? false}))))
