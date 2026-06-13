(ns hyperopen.views.account-funding-history-module
  (:require [hyperopen.views.account-info.tabs.funding-history :as funding-history-tab]))

(defn ^:export funding-history-tab-renderer
  [{:keys [funding-history-rows funding-history-state funding-history-raw]}]
  (funding-history-tab/funding-history-tab-content funding-history-rows
                                                   funding-history-state
                                                   funding-history-raw))

(goog/exportSymbol "hyperopen.views.account_funding_history_module.funding_history_tab_renderer" funding-history-tab-renderer)
