(ns hyperopen.core-bootstrap.wallet-actions-effects-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [nexus.registry :as nxr]
            [hyperopen.core.compat :as core]
            [hyperopen.core-bootstrap.test-support.fixtures :as fixtures]))

(def clear-wallet-copy-feedback-timeout! fixtures/clear-wallet-copy-feedback-timeout!)

(deftest copy-wallet-address-action-emits-copy-effect-when-address-present-test
  (is (= [[:effects/copy-wallet-address "0xabc"]]
         (core/copy-wallet-address-action {:wallet {:address "0xabc"}})))
  (is (= [[:effects/copy-wallet-address nil]]
         (core/copy-wallet-address-action {:wallet {:address nil}}))))

(deftest disconnect-wallet-action-emits-disconnect-effect-test
  (is (= [[:effects/disconnect-wallet]]
         (core/disconnect-wallet-action {}))))

(deftest should-auto-enable-agent-trading-predicate-guards-connect-state-test
  (let [predicate @#'hyperopen.core.compat/should-auto-enable-agent-trading?]
    (is (true? (predicate {:wallet {:connected? true
                                    :address "0xAbC"
                                    :agent {:status :not-ready}}}
                          "0xabc")))
    (is (false? (predicate {:wallet {:connected? false
                                     :address "0xabc"
                                     :agent {:status :not-ready}}}
                           "0xabc")))
    (is (false? (predicate {:wallet {:connected? true
                                     :address "0xabc"
                                     :agent {:status :ready}}}
                           "0xabc")))
    (is (false? (predicate {:wallet {:connected? true
                                     :address "0xabc"
                                     :agent {:status :not-ready}}}
                           "0xdef")))))

(deftest handle-wallet-connected-dispatches-enable-agent-trading-when-eligible-test
  (let [store (atom {:wallet {:connected? true
                              :address "0xabc"
                              :agent {:status :not-ready}}})
        dispatched (atom [])
        original-queue-microtask js/queueMicrotask]
    (set! js/queueMicrotask (fn [f] (f)))
    (try
      (with-redefs [nxr/dispatch (fn [_ _ actions]
                                   (swap! dispatched conj actions))]
        (core/handle-wallet-connected store "0xabc")
        (is (= [[[:actions/enable-agent-trading]]
                [[:effects/record-attribution-event
                  :wallet-connected
                  {:wallet/address "0xabc" :outcome :observed}]]]
               @dispatched)))
      (finally
        (set! js/queueMicrotask original-queue-microtask)))))

(deftest handle-wallet-connected-skips-dispatch-when-not-eligible-test
  (let [store (atom {:wallet {:connected? true
                              :address "0xabc"
                              :agent {:status :ready}}})
        dispatched (atom [])
        original-queue-microtask js/queueMicrotask]
    (set! js/queueMicrotask (fn [f] (f)))
    (try
      (with-redefs [nxr/dispatch (fn [_ _ actions]
                                   (swap! dispatched conj actions))]
        (core/handle-wallet-connected store "0xabc")
        (is (= [[[:effects/record-attribution-event
                  :wallet-connected
                  {:wallet/address "0xabc" :outcome :observed}]]]
               @dispatched)))
      (finally
        (set! js/queueMicrotask original-queue-microtask)))))

(deftest copy-wallet-address-effect-writes-to-clipboard-when-available-test
  (async done
    (let [written (atom nil)
          navigator-prop "navigator"
          original-navigator-descriptor (js/Object.getOwnPropertyDescriptor js/globalThis navigator-prop)
          fake-clipboard #js {:writeText (fn [payload]
                                           (reset! written payload)
                                           (js/Promise.resolve true))}
          fake-navigator #js {:clipboard fake-clipboard}
          store (atom {:wallet {:copy-feedback nil}})]
      (clear-wallet-copy-feedback-timeout!)
      (js/Object.defineProperty js/globalThis navigator-prop
                                #js {:value fake-navigator
                                     :configurable true})
      (core/copy-wallet-address nil store "0xabc")
      (js/setTimeout
       (fn []
         (try
           (is (= "0xabc" @written))
           (is (= :success (get-in @store [:wallet :copy-feedback :kind])))
           (is (= "Address copied to clipboard"
                  (get-in @store [:wallet :copy-feedback :message])))
           (finally
             (clear-wallet-copy-feedback-timeout!)
             (if original-navigator-descriptor
               (js/Object.defineProperty js/globalThis navigator-prop original-navigator-descriptor)
               (js/Reflect.deleteProperty js/globalThis navigator-prop))
             (done))))
       0))))

(deftest copy-wallet-address-effect-shows-error-feedback-when-clipboard-unavailable-test
  (let [navigator-prop "navigator"
        original-navigator-descriptor (js/Object.getOwnPropertyDescriptor js/globalThis navigator-prop)
        store (atom {:wallet {:copy-feedback nil}})]
    (clear-wallet-copy-feedback-timeout!)
    (js/Object.defineProperty js/globalThis navigator-prop
                              #js {:value #js {}
                                   :configurable true})
    (try
      (core/copy-wallet-address nil store "0xabc")
      (is (= :error (get-in @store [:wallet :copy-feedback :kind])))
      (is (= "Clipboard unavailable"
             (get-in @store [:wallet :copy-feedback :message])))
      (finally
        (clear-wallet-copy-feedback-timeout!)
        (if original-navigator-descriptor
          (js/Object.defineProperty js/globalThis navigator-prop original-navigator-descriptor)
          (js/Reflect.deleteProperty js/globalThis navigator-prop))))))
