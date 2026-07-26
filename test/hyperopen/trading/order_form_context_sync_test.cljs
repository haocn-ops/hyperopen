(ns hyperopen.trading.order-form-context-sync-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.state.trading :as trading]
            [hyperopen.state.trading.test-support :as support]
            [hyperopen.trading.order-form-context-sync :as context-sync]
            [hyperopen.trading.order-form-transitions :as transitions]))

(defn- committed-quote-buy
  [opts size-display]
  (let [state (support/spot-buy-state opts)]
    (support/apply-order-form-transition
     state
     (transitions/set-order-size-display state size-display))))

(deftest reconcile-is-noop-when-no-size-is-committed-test
  (let [state (support/spot-buy-state {:ask "1.00" :usdc "100"})]
    (is (identical? state (context-sync/reconcile-active-order-form state))
        "empty ticket should not touch state")))

(deftest reconcile-is-noop-when-best-ask-is-unchanged-test
  (testing "re-projecting against the same book leaves the committed form intact"
    (let [committed (committed-quote-buy {:ask "1.00" :usdc "100"} "50")
          reconciled (context-sync/reconcile-active-order-form committed)]
      (is (= (:order-form committed) (:order-form reconciled)))
      (is (= (:order-form-ui committed) (:order-form-ui reconciled)))
      (is (identical? committed reconciled)))))

(deftest reconcile-rederives-canonical-size-against-new-ask-but-preserves-display-test
  (let [committed (committed-quote-buy {:ask "1.00" :usdc "100"} "100")
        frozen-size (:size (trading/order-form-draft committed))
        ticked (support/set-active-best-ask committed "1.01")
        reconciled (context-sync/reconcile-active-order-form ticked)
        form (trading/order-form-draft reconciled)]
    (testing "the user-facing quote commitment is preserved"
      (is (= "100" (:size-display form))))
    (testing "the canonical base size shrinks to stay within the live notional"
      (is (not= frozen-size (:size form)))
      (is (<= (* (js/parseFloat (:size form)) 1.01) (+ 100 1e-9))))))

(deftest reconcile-is-idempotent-after-a-single-tick-test
  (let [ticked (support/set-active-best-ask
                (committed-quote-buy {:ask "1.00" :usdc "100"} "100")
                "1.02")
        once (context-sync/reconcile-active-order-form ticked)
        twice (context-sync/reconcile-active-order-form once)]
    (is (= (:order-form once) (:order-form twice)))
    (is (= (:order-form-ui once) (:order-form-ui twice)))))
