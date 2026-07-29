(ns hyperopen.startup.builder-fee-recovery-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.surface-service :as account-surface-service]
            [hyperopen.startup.runtime :as startup-runtime]))

(deftest account-bootstrap-refreshes-builder-fee-once-per-address-test
  (let [store (atom {})
        startup-runtime (atom {:bootstrapped-address nil})
        refresh-calls (atom [])
        deps {:store store
              :startup-runtime startup-runtime
              :refresh-builder-fee-approval!
              (fn [context store-arg]
                (swap! refresh-calls conj [context store-arg])
                (js/Promise.reject (js/Error. "approval-refresh-failed")))}]
    (with-redefs [account-surface-service/bootstrap-account-surfaces! (fn [_] nil)]
      (startup-runtime/bootstrap-account-data! (assoc deps :address nil))
      (startup-runtime/bootstrap-account-data! (assoc deps :address "0xaaa"))
      (startup-runtime/bootstrap-account-data! (assoc deps :address "0xaaa"))
      (startup-runtime/bootstrap-account-data! (assoc deps :address "0xbbb")))
    (is (= [[nil store] [nil store]] @refresh-calls))
    (is (= "0xbbb" (:bootstrapped-address @startup-runtime)))))
