(ns hyperopen.views.account-activity-module
  (:require [hyperopen.views.account-info.tabs.trade-history :as trade-history-tab]
            [hyperopen.views.account-info.tabs.twap :as twap-tab]))

(defn ^:export trade-history-tab-renderer
  [{:keys [trade-history-rows trade-history-state mobile-expanded-card]}]
  (trade-history-tab/trade-history-tab-content trade-history-rows
                                               (assoc trade-history-state
                                                      :mobile-expanded-card mobile-expanded-card)))

(defn ^:export twap-tab-renderer
  [view-model]
  (twap-tab/twap-tab-content view-model))

(goog/exportSymbol "hyperopen.views.account_activity_module.trade_history_tab_renderer" trade-history-tab-renderer)
(goog/exportSymbol "hyperopen.views.account_activity_module.twap_tab_renderer" twap-tab-renderer)
