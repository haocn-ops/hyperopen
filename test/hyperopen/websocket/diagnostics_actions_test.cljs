(ns hyperopen.websocket.diagnostics-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.core-bootstrap.test-support.effect-extractors :as effect-extractors]
            [hyperopen.websocket.diagnostics-actions :as diagnostics-actions]))

(def ^:private reconnect-heavy-effect-ids
  #{:effects/reconnect-websocket})

(deftest toggle-ws-diagnostics-opens-and-refreshes-health-test
  (is (= [[:effects/save-many [[[:websocket-ui :diagnostics-open?] true]
                               [[:websocket-ui :reveal-sensitive?] false]
                               [[:websocket-ui :copy-status] nil]]]
          [:effects/refresh-websocket-health]]
         (diagnostics-actions/toggle-ws-diagnostics
          {:websocket-ui {:diagnostics-open? false}}))))

(deftest ws-diagnostics-reconnect-now-respects-cooldown-test
  (let [deps {:effective-now-ms (fn [generated-at-ms] generated-at-ms)
              :reconnect-cooldown-ms 5000}
        blocked-state {:websocket-ui {:reconnect-cooldown-until-ms 9000}
                       :websocket {:health {:generated-at-ms 5000
                                            :transport {:state :connected}}}}
        ready-state {:websocket-ui {:reconnect-cooldown-until-ms nil}
                     :websocket {:health {:generated-at-ms 5000
                                          :transport {:state :connected}}}}]
    (is (= [] (diagnostics-actions/ws-diagnostics-reconnect-now blocked-state deps)))
    (let [effects (diagnostics-actions/ws-diagnostics-reconnect-now ready-state deps)]
      (is (= [[:effects/save-many [[[:websocket-ui :diagnostics-open?] false]
                                   [[:websocket-ui :reveal-sensitive?] false]
                                   [[:websocket-ui :copy-status] nil]]]
              [:effects/save [:websocket-ui :reconnect-cooldown-until-ms] 10000]
              [:effects/reconnect-websocket]]
             effects))
      (is (effect-extractors/projection-before-heavy? effects reconnect-heavy-effect-ids))
      (is (effect-extractors/phase-order-valid? effects reconnect-heavy-effect-ids))
      (is (empty? (effect-extractors/duplicate-heavy-effect-ids effects reconnect-heavy-effect-ids))))))

(deftest handle-ws-diagnostics-keydown-closes-only-on-escape-test
  (is (= [[:effects/save-many [[[:websocket-ui :diagnostics-open?] false]
                               [[:websocket-ui :reveal-sensitive?] false]
                               [[:websocket-ui :copy-status] nil]]]]
         (diagnostics-actions/handle-ws-diagnostics-keydown {} "Escape")))
  (is (= []
         (diagnostics-actions/handle-ws-diagnostics-keydown {} "Enter"))))

(deftest ws-diagnostics-reset-subscriptions-skips-when-blocked-test
  (let [deps {:effective-now-ms (fn [generated-at-ms] generated-at-ms)}
        blocked-state {:websocket-ui {:reset-in-progress? false
                                      :reset-cooldown-until-ms 9000}
                       :websocket {:health {:generated-at-ms 5000
                                            :transport {:state :connected}}}}
        ready-state {:websocket-ui {:reset-in-progress? false
                                    :reset-cooldown-until-ms nil}
                     :websocket {:health {:generated-at-ms 5000
                                          :transport {:state :connected}}}}]
    (is (= []
           (diagnostics-actions/ws-diagnostics-reset-market-subscriptions blocked-state :manual deps)))
    (is (= [[:effects/ws-reset-subscriptions {:group :orders_oms :source :manual}]]
           (diagnostics-actions/ws-diagnostics-reset-orders-subscriptions ready-state :manual deps)))))
