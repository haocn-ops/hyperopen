(ns hyperopen.views.account-info.test-support.lazy-modules
  (:require [hyperopen.views.account-info.tabs.funding-history :as funding-history-tab]
            [hyperopen.views.account-info.tabs.open-orders :as open-orders-tab]
            [hyperopen.views.account-info.tabs.order-history :as order-history-tab]
            [hyperopen.views.account-info.tabs.outcomes :as outcomes-tab]
            [hyperopen.views.account-info.tabs.positions :as positions-tab]
            [hyperopen.views.account-info.tabs.trade-history :as trade-history-tab]
            [hyperopen.views.account-info.tabs.twap :as twap-tab]))

(defn- positions-tab-renderer
  [{:keys [positions
           webdata2
           positions-sort
           perp-dex-states
           position-tpsl-modal
           position-reduce-popover
           position-margin-modal
           positions-state
           mobile-expanded-card]}]
  (if (some? positions)
    (positions-tab/positions-tab-content {:positions positions
                                          :sort-state positions-sort
                                          :tpsl-modal position-tpsl-modal
                                          :reduce-popover position-reduce-popover
                                          :margin-modal position-margin-modal
                                          :positions-state (assoc positions-state
                                                                  :mobile-expanded-card mobile-expanded-card)})
    (positions-tab/positions-tab-content {:webdata2 webdata2
                                          :sort-state positions-sort
                                          :perp-dex-states perp-dex-states
                                          :tpsl-modal position-tpsl-modal
                                          :reduce-popover position-reduce-popover
                                          :margin-modal position-margin-modal
                                          :positions-state (assoc positions-state
                                                                  :mobile-expanded-card mobile-expanded-card)})))

(defn- outcomes-tab-renderer
  [{:keys [outcomes position-reduce-popover read-only?]}]
  (outcomes-tab/outcomes-tab-content {:outcomes outcomes
                                      :reduce-popover position-reduce-popover
                                      :read-only? read-only?}))

(defn- open-orders-tab-renderer
  [{:keys [open-orders open-orders-sort open-orders-state]}]
  (open-orders-tab/open-orders-tab-content open-orders open-orders-sort open-orders-state))

(defn- order-history-tab-renderer
  [{:keys [order-history-rows order-history-state]}]
  (order-history-tab/order-history-tab-content order-history-rows order-history-state))

(defn- trade-history-tab-renderer
  [{:keys [trade-history-rows trade-history-state mobile-expanded-card]}]
  (trade-history-tab/trade-history-tab-content trade-history-rows
                                               (assoc trade-history-state
                                                      :mobile-expanded-card mobile-expanded-card)))

(defn- twap-tab-renderer
  [view-model]
  (twap-tab/twap-tab-content view-model))

(defn- funding-history-tab-renderer
  [{:keys [funding-history-rows funding-history-state funding-history-raw]}]
  (funding-history-tab/funding-history-tab-content funding-history-rows
                                                   funding-history-state
                                                   funding-history-raw))

(defn tab-renderer
  [tab]
  (case tab
    :positions positions-tab-renderer
    :outcomes outcomes-tab-renderer
    :open-orders open-orders-tab-renderer
    :order-history order-history-tab-renderer
    :trade-history trade-history-tab-renderer
    :twap twap-tab-renderer
    :funding-history funding-history-tab-renderer
    nil))

(defn tab-ready?
  [_state tab]
  (boolean (tab-renderer tab)))

(defn tab-loading?
  [_state _tab]
  false)

(defn tab-error
  [_state _tab]
  nil)
