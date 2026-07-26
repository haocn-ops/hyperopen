(ns hyperopen.workbench.scenes.account.history.trade-history-scenes
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.workbench.scenes.account.history.support :as support]
            [hyperopen.views.account-info.projections.trades :as trade-projections]
            [hyperopen.views.account-info.tabs.trade-history :as trade-history]))

(portfolio/configure-scenes
  {:title "Trade History"
   :collection :account.history})

(portfolio/defscene default
  []
  (support/history-panel
   (trade-history/trade-history-tab-content
    support/trade-fills
    (support/trade-history-state))))

(portfolio/defscene mobile-closed
  []
  (support/mobile-history-panel
   (trade-history/trade-history-tab-content
    support/trade-fills
    (support/trade-history-state))))

(portfolio/defscene mobile-open
  []
  (support/mobile-history-panel
   (trade-history/trade-history-tab-content
    support/trade-fills
    (support/trade-history-state
     {:mobile-expanded-card {:trade-history (trade-projections/trade-history-row-id
                                             (second support/trade-fills))}}))))

(portfolio/defscene empty-state
  []
  (support/history-panel
   (trade-history/trade-history-tab-content
    []
    (support/trade-history-state))))
