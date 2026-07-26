(ns hyperopen.workbench.scenes.account.history.order-history-scenes
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.workbench.scenes.account.history.support :as support]
            [hyperopen.views.account-info.tabs.order-history :as order-history]))

(portfolio/configure-scenes
  {:title "Order History"
   :collection :account.history})

(portfolio/defscene mixed
  []
  (support/history-panel
   (order-history/order-history-tab-content
    support/order-rows
    (support/order-history-state))))

(portfolio/defscene loading
  []
  (support/history-panel
   (order-history/order-history-tab-content
    []
    (support/order-history-state {:loading? true}))))
