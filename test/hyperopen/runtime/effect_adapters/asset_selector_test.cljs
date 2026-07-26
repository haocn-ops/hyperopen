(ns hyperopen.runtime.effect-adapters.asset-selector-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.asset-selector.query :as asset-selector-query]
            [hyperopen.runtime.effect-adapters.asset-selector :as asset-adapters]
            [hyperopen.runtime.effect-adapters :as effect-adapters]
            [hyperopen.websocket.active-asset-ctx :as active-ctx]
            [hyperopen.websocket.client :as ws-client]))

(deftest facade-asset-selector-adapters-delegate-to-asset-selector-module-test
  (is (identical? asset-adapters/persist-asset-selector-markets-cache!
                  effect-adapters/persist-asset-selector-markets-cache!))
  (is (identical? asset-adapters/persist-active-market-display!
                  effect-adapters/persist-active-market-display!))
  (is (identical? asset-adapters/load-active-market-display
                  effect-adapters/load-active-market-display))
  (is (identical? asset-adapters/sync-asset-selector-active-ctx-subscriptions
                  effect-adapters/sync-asset-selector-active-ctx-subscriptions)))

(deftest queue-asset-icon-status-wrapper-injects-facade-schedule-animation-frame-seam-test
  (let [captured (atom nil)
        store (atom {})]
    (with-redefs [effect-adapters/schedule-animation-frame! (fn [_] :raf-id)
                  asset-adapters/queue-asset-icon-status!
                  (fn [opts]
                    (reset! captured opts))]
      (effect-adapters/queue-asset-icon-status nil store {:market-key "perp:BTC"
                                                           :icon-status :loaded})
      (is (= :raf-id ((:schedule-animation-frame! @captured) (fn [] nil))))
      (is (fn? (:flush-queued-asset-icon-statuses! @captured))))))

(deftest sync-asset-selector-active-ctx-subscriptions-diffs-owner-scoped-coins-test
  (let [store (atom {:asset-selector {}})
        sent-messages (atom [])
        original-state @active-ctx/active-asset-ctx-state]
    (reset! active-ctx/active-asset-ctx-state {:subscriptions #{"BTC" "SOL"}
                                                :owners-by-coin {"BTC" #{:asset-selector}
                                                                 "SOL" #{:asset-selector}}
                                                :coins-by-owner {:asset-selector #{"BTC" "SOL"}}
                                                :contexts {}})
    (try
      (with-redefs [asset-selector-query/selector-visible-market-coins (fn [_]
                                                                          #{"BTC" "ETH"})
                    ws-client/send-message! (fn [message]
                                              (swap! sent-messages conj message)
                                              true)]
        (effect-adapters/sync-asset-selector-active-ctx-subscriptions nil store))
      (is (= [{:method "subscribe"
               :subscription {:type "activeAssetCtx"
                              :coin "ETH"}}
              {:method "unsubscribe"
               :subscription {:type "activeAssetCtx"
                              :coin "SOL"}}]
             @sent-messages))
      (is (= #{"BTC" "ETH"}
             (active-ctx/get-subscribed-coins-by-owner :asset-selector)))
      (finally
        (reset! active-ctx/active-asset-ctx-state original-state)))))

(deftest sync-asset-selector-active-ctx-subscriptions-preserves-owned-selector-coins-while-live-updates-are-paused-test
  (let [store (atom {:asset-selector {:live-market-subscriptions-paused? true}})
        sent-messages (atom [])
        original-state @active-ctx/active-asset-ctx-state]
    (reset! active-ctx/active-asset-ctx-state {:subscriptions #{"BTC" "SOL"}
                                                :owners-by-coin {"BTC" #{:asset-selector}
                                                                 "SOL" #{:asset-selector}}
                                                :coins-by-owner {:asset-selector #{"BTC" "SOL"}}
                                                :contexts {}})
    (try
      (with-redefs [asset-selector-query/selector-visible-market-coins (fn [_]
                                                                          #{"BTC" "ETH"})
                    ws-client/send-message! (fn [message]
                                              (swap! sent-messages conj message)
                                              true)]
        (effect-adapters/sync-asset-selector-active-ctx-subscriptions nil store))
      (is (empty? @sent-messages))
      (is (= #{"BTC" "SOL"}
             (active-ctx/get-subscribed-coins-by-owner :asset-selector)))
      (finally
        (reset! active-ctx/active-asset-ctx-state original-state)))))
