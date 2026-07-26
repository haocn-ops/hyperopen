(ns hyperopen.workbench.scenes.account.history.funding-history-scenes
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.workbench.scenes.account.history.support :as support]
            [hyperopen.views.account-info.tabs.funding-history :as funding-history]))

(portfolio/configure-scenes
  {:title "Funding History"
   :collection :account.history})

(portfolio/defscene default
  []
  (support/history-panel
   (funding-history/funding-history-tab-content
    support/funding-rows
    (support/funding-history-state)
    support/funding-rows)))
