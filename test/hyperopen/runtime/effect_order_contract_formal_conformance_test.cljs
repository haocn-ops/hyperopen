(ns hyperopen.runtime.effect-order-contract-formal-conformance-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.formal.effect-order-contract-vectors :as vectors]
            [hyperopen.runtime.effect-order-contract :as effect-order-contract]
            [hyperopen.runtime.validation :as validation]
            [hyperopen.schema.contracts]
            [hyperopen.schema.effect-order-contracts :as effect-order-contracts]))

(defn- failure-projection
  [error]
  (if-let [[_ rule effect-index effect-id]
           (re-find #"rule=([^ ]+) effect-index=(\d+) effect-id=([^ ]+)" (.-message error))]
    {:ok? false
     :rule (keyword rule)
     :effect-index (js/parseInt effect-index 10)
     :effect-id (keyword (subs effect-id 1))}
    (throw (js/Error.
            (str "failed to parse effect-order contract error. "
                 "message=" (.-message error))))))

(defn- assertion-projection
  [action-id effects]
  (try
    (effect-order-contract/assert-action-effect-order!
     action-id
     effects
     {:phase :formal-conformance})
    {:ok? true}
    (catch :default error
      (failure-projection error))))

(defn- trace-summary-projection
  [trace]
  (effect-order-contracts/summary-projection trace))

(deftest effect-order-policy-conforms-to-committed-formal-vectors-test
  (is (= (set (map :action-id vectors/effect-order-policy-vectors))
         (effect-order-contract/covered-action-ids)))
  (doseq [{:keys [action-id expected]} vectors/effect-order-policy-vectors]
    (testing (name action-id)
      (effect-order-contracts/assert-policy-vector!
       {:action-id action-id
        :expected expected}
       {:phase :policy-vector})
      (let [expected* (assoc expected :action-id action-id)
            actual (effect-order-contracts/policy-projection
                    action-id
                    (effect-order-contract/action-policy action-id))]
        (effect-order-contracts/assert-policy-projection!
         actual
         {:phase :runtime-policy-projection
          :action-id action-id})
        (is (= expected* actual))))))

(deftest assert-action-effect-order-conforms-to-committed-formal-vectors-test
  (doseq [{:keys [id action-id effects expected]} vectors/effect-order-assertion-vectors]
    (testing (name id)
      (effect-order-contracts/assert-assertion-vector!
       {:id id
        :action-id action-id
        :effects effects
        :expected expected}
       {:phase :assertion-vector})
      (let [actual (assertion-projection action-id effects)]
        (effect-order-contracts/assert-assertion-outcome!
         actual
         {:phase :runtime-assertion-outcome
          :id id})
        (is (= expected actual))))))

(deftest effect-order-summary-conforms-to-committed-formal-vectors-test
  (doseq [{:keys [id action-id effects expected]} vectors/effect-order-summary-vectors]
    (testing (name id)
      (effect-order-contracts/assert-summary-vector!
       {:id id
        :action-id action-id
        :effects effects
        :expected expected}
       {:phase :summary-vector})
      (let [actual (effect-order-contracts/summary-projection
                    (effect-order-contract/effect-order-summary action-id effects))]
        (effect-order-contracts/assert-summary-projection!
         actual
         {:phase :runtime-summary-projection
          :id id})
        (is (= expected actual))))))

(deftest wrap-action-handler-conforms-to-committed-formal-vectors-test
  (doseq [{:keys [id action-id validation-enabled? effects expected records-debug-summary? expected-summary]}
          vectors/effect-order-wrapper-vectors]
    (testing (name id)
      (effect-order-contracts/assert-wrapper-vector!
       {:id id
        :action-id action-id
        :effects effects
        :validation-enabled? validation-enabled?
        :expected expected
        :records-debug-summary? records-debug-summary?
        :expected-summary expected-summary}
       {:phase :wrapper-vector})
      (validation/clear-debug-action-effect-traces!)
      (with-redefs [validation/validation-enabled? (constantly validation-enabled?)
                    validation/assert-action-args! (fn [& _] true)
                    validation/assert-emitted-effects! (fn [& _] true)]
        (let [wrapped (validation/wrap-action-handler action-id (fn [_state] effects))
              actual (try
                       (wrapped {})
                       {:ok? true}
                       (catch :default error
                         (failure-projection error)))
              trace (last (validation/debug-action-effect-traces-snapshot))
              expects-debug-summary? (and ^boolean goog.DEBUG
                                          records-debug-summary?)]
          (effect-order-contracts/assert-assertion-outcome!
           actual
           {:phase :runtime-wrapper-outcome
            :id id})
          (is (= expected actual))
          (if expects-debug-summary?
            (do
              (is (some? trace))
              (let [actual-summary (trace-summary-projection trace)]
                (effect-order-contracts/assert-summary-projection!
                 actual-summary
                 {:phase :runtime-wrapper-summary
                  :id id})
                (is (= expected-summary actual-summary))))
            (is (nil? trace))))))))
