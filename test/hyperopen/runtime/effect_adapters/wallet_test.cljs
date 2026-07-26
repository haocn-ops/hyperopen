(ns hyperopen.runtime.effect-adapters.wallet-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [nexus.registry :as nxr]
            [hyperopen.runtime.effect-adapters :as effect-adapters]
            [hyperopen.runtime.effect-adapters.wallet :as wallet-adapters]
            [hyperopen.wallet.agent-runtime :as agent-runtime]))

(deftest facade-wallet-adapters-delegate-to-wallet-module-test
  (is (identical? wallet-adapters/connect-wallet effect-adapters/connect-wallet))
  (is (identical? wallet-adapters/set-agent-storage-mode effect-adapters/set-agent-storage-mode))
  (is (identical? wallet-adapters/copy-wallet-address effect-adapters/copy-wallet-address))
  (is (identical? wallet-adapters/make-copy-wallet-address effect-adapters/make-copy-wallet-address))
  (is (identical? wallet-adapters/copy-spectate-link effect-adapters/copy-spectate-link))
  (is (identical? wallet-adapters/make-copy-spectate-link effect-adapters/make-copy-spectate-link)))

(deftest unlock-agent-trading-dispatches-after-success-actions-when-ready-test
  (async done
    (let [store (atom {:wallet {:address "0xabc"
                                :agent {:status :unlocking}}})
          after-success-actions [[:actions/submit-unlocked-order-request
                                  {:action {:type "order"}}]]
          dispatched (atom [])
          original-unlock agent-runtime/unlock-agent-trading!
          original-dispatch nxr/dispatch]
      (set! agent-runtime/unlock-agent-trading!
            (fn [{:keys [store]}]
              (swap! store assoc-in [:wallet :agent :status] :ready)
              (js/Promise.resolve :unlocked)))
      (set! nxr/dispatch
            (fn [store ctx effects]
              (swap! dispatched conj [store ctx effects])))
      (-> (wallet-adapters/unlock-agent-trading nil
                                                store
                                                {:after-success-actions after-success-actions})
          (.then
           (fn [_]
             (is (= [[store nil after-success-actions]]
                    @dispatched))))
          (.catch
           (fn [err]
             (is false (str "Unexpected error: " err))))
          (.finally
           (fn []
             (set! agent-runtime/unlock-agent-trading! original-unlock)
             (set! nxr/dispatch original-dispatch)
             (done)))))))
