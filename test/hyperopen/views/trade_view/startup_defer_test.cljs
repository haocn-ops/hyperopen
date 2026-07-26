(ns hyperopen.views.trade-view.startup-defer-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.surface-modules :as surface-modules]
            [hyperopen.trade-modules :as trade-modules]
            [hyperopen.views.account-equity-view :as account-equity-view]
            [hyperopen.views.account-info-view :as account-info-view]
            [hyperopen.views.active-asset-view :as active-asset-view]
            [hyperopen.views.l2-orderbook-view :as l2-orderbook-view]
            [hyperopen.views.trade.order-form-view :as order-form-view]
            [hyperopen.views.trade-view :as trade-view]
            [hyperopen.views.trade.test-support :as support]))

(deftest trade-view-defers-desktop-secondary-panels-while-startup-flag-is-false-test
  (support/with-viewport-width
    1280
    (fn []
      (let [active-asset-calls (atom 0)
            chart-calls (atom 0)
            orderbook-calls (atom 0)
            order-form-calls (atom 0)
            account-info-calls (atom 0)
            equity-metrics-calls (atom 0)
            account-equity-calls (atom 0)
            deferred-state (assoc-in (support/active-asset-state)
                                     [:trade-ui :desktop-secondary-panels-ready?]
                                     false)]
        (with-redefs [active-asset-view/active-asset-view (fn [_state]
                                                            (swap! active-asset-calls inc)
                                                            [:div {:data-role "stub-active-asset"}])
                      trade-modules/render-trade-chart-view (fn [_state]
                                                              (swap! chart-calls inc)
                                                              [:div {:data-role "stub-chart"}])
                      l2-orderbook-view/l2-orderbook-view (fn [_state]
                                                            (swap! orderbook-calls inc)
                                                            [:div {:data-role "stub-orderbook"}])
                      order-form-view/order-form-view (fn [_state]
                                                        (swap! order-form-calls inc)
                                                        [:div {:data-role "stub-order-form"}])
                      account-info-view/account-info-view (fn
                                                            ([_state]
                                                             (swap! account-info-calls inc)
                                                             [:div {:data-role "stub-account-info"}])
                                                            ([_state _options]
                                                             (swap! account-info-calls inc)
                                                             [:div {:data-role "stub-account-info"}]))
                      account-equity-view/account-equity-metrics (fn [_state]
                                                                   (swap! equity-metrics-calls inc)
                                                                   {:account-value-display 12})
                      account-equity-view/account-equity-view (fn
                                                                ([_state]
                                                                 (swap! account-equity-calls inc)
                                                                 [:div {:data-role "stub-account-equity"}])
                                                                ([_state _opts]
                                                                 (swap! account-equity-calls inc)
                                                                 [:div {:data-role "stub-account-equity"}]))]
          (let [view-node (trade-view/trade-view deferred-state)]
            (is (= 1 @active-asset-calls))
            (is (= 1 @chart-calls))
            (is (= 1 @orderbook-calls))
            (is (= 1 @order-form-calls))
            (is (= 0 @account-info-calls))
            (is (= 0 @equity-metrics-calls))
            (is (= 0 @account-equity-calls))
            (is (some? (support/find-by-data-role view-node "trade-desktop-account-panel-placeholder")))
            (is (some? (support/find-by-data-role view-node "trade-desktop-account-equity-placeholder")))
            (is (= 0 (count (support/find-all-nodes view-node #(= "stub-account-info"
                                                                  (get-in % [1 :data-role]))))))
            (is (= 0 (count (support/find-all-nodes view-node #(= "stub-account-equity"
                                                                  (get-in % [1 :data-role]))))))))))))

(deftest trade-view-renders-account-placeholders-until-account-surfaces-load-test
  (support/with-viewport-width
    1280
    (fn []
      (let [account-info-calls (atom 0)
            equity-metrics-calls (atom 0)
            account-equity-calls (atom 0)
            ready-state (assoc-in (support/active-asset-state)
                                  [:trade-ui :desktop-secondary-panels-ready?]
                                  true)]
        (with-redefs [surface-modules/resolved-surface-export
                      (fn [surface-id export-id]
                        (when (and (= :account-surfaces surface-id)
                                   (= :unexpected export-id))
                          (throw (js/Error. "unexpected account surface export"))))
                      account-info-view/account-info-view
                      (fn
                        ([_state]
                         (swap! account-info-calls inc)
                         [:div {:data-role "stub-account-info"}])
                        ([_state _options]
                         (swap! account-info-calls inc)
                         [:div {:data-role "stub-account-info"}]))
                      account-equity-view/account-equity-metrics
                      (fn [_state]
                        (swap! equity-metrics-calls inc)
                        {:account-value-display 12})
                      account-equity-view/account-equity-view
                      (fn
                        ([_state]
                         (swap! account-equity-calls inc)
                         [:div {:data-role "stub-account-equity"}])
                        ([_state _opts]
                         (swap! account-equity-calls inc)
                         [:div {:data-role "stub-account-equity"}]))]
          (let [view-node (trade-view/trade-view ready-state)]
            (is (= 0 @account-info-calls))
            (is (= 0 @equity-metrics-calls))
            (is (= 0 @account-equity-calls))
            (is (some? (support/find-by-data-role view-node "trade-desktop-account-panel-placeholder")))
            (is (some? (support/find-by-data-role view-node "trade-desktop-account-equity-placeholder")))
            (is (= 0 (count (support/find-all-nodes view-node #(= "stub-account-info"
                                                                  (get-in % [1 :data-role]))))))
            (is (= 0 (count (support/find-all-nodes view-node #(= "stub-account-equity"
                                                                  (get-in % [1 :data-role]))))))))))))
