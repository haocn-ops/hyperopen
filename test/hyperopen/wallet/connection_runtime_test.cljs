(ns hyperopen.wallet.connection-runtime-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.wallet.connection-runtime :as connection-runtime]))

(deftest should-auto-enable-agent-trading-predicate-guards-connect-state-test
  (is (true? (connection-runtime/should-auto-enable-agent-trading?
              {:wallet {:connected? true
                        :address "0xAbC"
                        :agent {:status :not-ready}}}
              "0xabc")))
  (is (false? (connection-runtime/should-auto-enable-agent-trading?
               {:wallet {:connected? false
                         :address "0xabc"
                         :agent {:status :not-ready}}}
               "0xabc")))
  (is (false? (connection-runtime/should-auto-enable-agent-trading?
               {:wallet {:connected? true
                         :address "0xabc"
                         :agent {:status :ready}}}
               "0xabc")))
  (is (false? (connection-runtime/should-auto-enable-agent-trading?
               {:wallet {:connected? true
                         :address "0xabc"
                         :agent {:status :not-ready}}}
               "0xdef"))))

(deftest connect-wallet-invokes-request-connection-hook-test
  (let [calls (atom [])
        store (atom {:wallet {:connected? false}})]
    (connection-runtime/connect-wallet!
     {:store store
      :log-fn (fn [& _] nil)
      :request-connection! (fn [store-arg]
                             (swap! calls conj (= store store-arg)))})
    (is (= [true] @calls))))

(deftest handle-wallet-connected-dispatches-enable-agent-trading-when-eligible-test
  (let [store (atom {:wallet {:connected? true
                              :address "0xabc"
                              :agent {:status :not-ready}}})
        dispatched (atom [])]
    (connection-runtime/handle-wallet-connected!
     {:store store
      :connected-address "0xabc"
      :should-auto-enable-agent-trading? connection-runtime/should-auto-enable-agent-trading?
      :dispatch! (fn [_ _ actions]
                   (swap! dispatched conj actions))})
    (is (= [[[:actions/enable-agent-trading]]]
           @dispatched))))

(deftest disconnect-wallet-runs-clear-and-disconnect-hooks-test
  (let [calls (atom [])
        store (atom {:wallet {:copy-feedback {:kind :success}
                              :connected? true}
                     :ui {:toast {:kind :success}}})]
    (connection-runtime/disconnect-wallet!
     {:store store
      :log-fn (fn [& _] nil)
      :clear-wallet-copy-feedback-timeout! (fn []
                                             (swap! calls conj :clear-wallet-copy-timeout))
      :clear-order-feedback-toast-timeout! (fn []
                                             (swap! calls conj :clear-order-toast-timeout))
      :clear-order-feedback-toast! (fn [_]
                                     (swap! calls conj :clear-order-toast))
      :set-disconnected! (fn [_]
                           (swap! calls conj :set-disconnected))})
    (is (= [:clear-wallet-copy-timeout
            :clear-order-toast-timeout
            :clear-order-toast
            :set-disconnected]
           @calls))))

(deftest handle-wallet-connected-skips-dispatch-when-predicate-fails-test
  (let [store (atom {:wallet {:connected? true
                              :address "0xabc"
                              :agent {:status :ready}}})
        dispatch-calls (atom 0)]
    (connection-runtime/handle-wallet-connected!
     {:store store
      :connected-address "0xabc"
      :should-auto-enable-agent-trading? (fn [_ _] false)
      :dispatch! (fn [& _]
                   (swap! dispatch-calls inc))})
    (is (= 0 @dispatch-calls))))

(deftest connect-and-disconnect-support-plain-js-callbacks-test
  (let [store (atom {:wallet {:connected? true}})
        original-events (aget js/globalThis "__walletConnectionEvents")
        events (array)]
    (aset js/globalThis "__walletConnectionEvents" events)
    (try
      (connection-runtime/connect-wallet!
       {:store store
        :log-fn (js/Function. "msg"
                              "globalThis.__walletConnectionEvents.push(['log-connect', msg]);")
        :request-connection! (js/Function. "storeArg"
                                           "globalThis.__walletConnectionEvents.push(['request', !!storeArg]);")})
      (connection-runtime/disconnect-wallet!
       {:store store
        :log-fn (js/Function. "msg"
                              "globalThis.__walletConnectionEvents.push(['log-disconnect', msg]);")
        :clear-wallet-copy-feedback-timeout! (js/Function.
                                              "globalThis.__walletConnectionEvents.push(['clear-copy']);")
        :clear-order-feedback-toast-timeout! (js/Function.
                                              "globalThis.__walletConnectionEvents.push(['clear-toast-timeout']);")
        :clear-order-feedback-toast! (js/Function. "storeArg"
                                                   "globalThis.__walletConnectionEvents.push(['clear-toast', !!storeArg]);")
        :set-disconnected! (js/Function. "storeArg"
                                         "globalThis.__walletConnectionEvents.push(['set-disconnected', !!storeArg]);")})
      (is (= [["log-connect" "Connecting wallet..."]
              ["request" true]
              ["log-disconnect" "Disconnecting wallet..."]
              ["clear-copy"]
              ["clear-toast-timeout"]
              ["clear-toast" true]
              ["set-disconnected" true]]
             (js->clj events)))
      (finally
        (if (some? original-events)
          (aset js/globalThis "__walletConnectionEvents" original-events)
          (js-delete js/globalThis "__walletConnectionEvents"))))))

(deftest should-auto-enable-agent-trading-rejects-missing-addresses-test
  (is (nil? (connection-runtime/should-auto-enable-agent-trading?
             {:wallet {:connected? true
                       :address nil
                       :agent {:status :not-ready}}}
             "0xabc")))
  (is (nil? (connection-runtime/should-auto-enable-agent-trading?
             {:wallet {:connected? true
                       :address "0xabc"
                       :agent {:status :not-ready}}}
             nil))))
