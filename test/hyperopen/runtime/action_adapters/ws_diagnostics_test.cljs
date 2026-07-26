(ns hyperopen.runtime.action-adapters.ws-diagnostics-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.runtime.action-adapters.ws-diagnostics :as ws-diagnostics-adapters]
            [hyperopen.runtime.state :as runtime-state]
            [hyperopen.websocket.health-runtime :as health-runtime]))

(deftest ws-diagnostics-reconnect-now-injects-runtime-deps-test
  (let [state {:websocket-ui {:diagnostics-open? true
                              :reconnect-cooldown-until-ms nil}
               :websocket {:health {:generated-at-ms 5000
                                    :transport {:state :connected}}}}]
    (with-redefs [health-runtime/effective-now-ms (fn [generated-at-ms]
                                                    (+ generated-at-ms 10))]
      (is (= [[:effects/save-many [[[:websocket-ui :diagnostics-open?] false]
                                   [[:websocket-ui :reveal-sensitive?] false]
                                   [[:websocket-ui :copy-status] nil]]]
              [:effects/save [:websocket-ui :reconnect-cooldown-until-ms]
               (+ 5010 runtime-state/reconnect-cooldown-ms)]
              [:effects/reconnect-websocket]]
             (ws-diagnostics-adapters/ws-diagnostics-reconnect-now state))))))

(deftest ws-diagnostics-reset-market-subscriptions-defaults-source-test
  (let [state {:websocket-ui {:reset-in-progress? false
                              :reset-cooldown-until-ms nil}
               :websocket {:health {:generated-at-ms 5000
                                    :transport {:state :connected}}}}]
    (with-redefs [health-runtime/effective-now-ms identity]
      (is (= [[:effects/ws-reset-subscriptions {:group :market_data
                                                :source :manual}]]
             (ws-diagnostics-adapters/ws-diagnostics-reset-market-subscriptions state))))))

(deftest ws-diagnostics-reset-orders-subscriptions-passes-explicit-source-test
  (let [state {:websocket-ui {:reset-in-progress? false
                              :reset-cooldown-until-ms nil}
               :websocket {:health {:generated-at-ms 5000
                                    :transport {:state :connected}}}}]
    (with-redefs [health-runtime/effective-now-ms identity]
      (is (= [[:effects/ws-reset-subscriptions {:group :orders_oms
                                                :source :auto-recover}]]
             (ws-diagnostics-adapters/ws-diagnostics-reset-orders-subscriptions
              state
              :auto-recover))))))
