(ns hyperopen.views.account-orders-module
  (:require [hyperopen.views.account-info.tabs.open-orders :as open-orders-tab]
            [hyperopen.views.account-info.tabs.order-history :as order-history-tab]))

(defn ^:export open-orders-tab-renderer
  [{:keys [open-orders open-orders-sort open-orders-state]}]
  (open-orders-tab/open-orders-tab-content open-orders open-orders-sort open-orders-state))

(defn ^:export order-history-tab-renderer
  [{:keys [order-history-rows order-history-state]}]
  (order-history-tab/order-history-tab-content order-history-rows order-history-state))

(goog/exportSymbol "hyperopen.views.account_orders_module.open_orders_tab_renderer" open-orders-tab-renderer)
(goog/exportSymbol "hyperopen.views.account_orders_module.order_history_tab_renderer" order-history-tab-renderer)
