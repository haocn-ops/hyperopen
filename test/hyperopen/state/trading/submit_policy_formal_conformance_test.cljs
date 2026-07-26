(ns hyperopen.state.trading.submit-policy-formal-conformance-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.api.gateway.orders.commands :as order-commands]
            [hyperopen.formal.trading-submit-policy-vectors :as vectors]
            [hyperopen.schema.order-request-contracts :as order-request-contracts]
            [hyperopen.schema.trading-submit-policy-contracts :as contracts]
            [hyperopen.trading.submit-policy :as submit-policy]))

(deftest effective-margin-mode-conforms-to-committed-formal-vectors-test
  (doseq [{:keys [id market input expected]} vectors/effective-margin-mode-vectors]
    (testing (name id)
      (is (= expected
             (submit-policy/effective-margin-mode market input))))))

(deftest prepare-order-form-conforms-to-committed-formal-vectors-test
  (doseq [{:keys [id context form expected]} vectors/prepare-order-form-vectors]
    (testing (name id)
      (let [actual (submit-policy/prepare-order-form-for-submit context form)
            projection (contracts/assert-prepare-result-projection!
                        (contracts/prepare-result-projection actual)
                        {:vector id})
            expected* (contracts/assert-prepare-result-projection!
                       expected
                       {:vector id :expected true})]
        (is (= expected* projection))))))

(deftest validation-summary-conforms-to-committed-formal-vectors-test
  (doseq [{:keys [id context form expected]} vectors/validation-vectors]
    (testing (name id)
      (let [actual (submit-policy/validation-summary context form)
            projection (contracts/assert-validation-summary-projection!
                        (contracts/validation-summary-projection actual)
                        {:vector id})
            expected* (contracts/assert-validation-summary-projection!
                       expected
                       {:vector id :expected true})]
        (is (= expected* projection))))))

(deftest submit-policy-conforms-to-committed-formal-vectors-test
  (doseq [{:keys [id context identity spectate-mode-message form options expected]}
          vectors/submit-policy-vectors]
    (testing (name id)
      (let [request-builder (partial order-commands/build-order-request context)
            actual (submit-policy/submit-policy
                    {:trading-context context
                     :identity identity
                     :spectate-mode-message spectate-mode-message
                     :request-builder request-builder}
                    form
                    options)
            projection (contracts/assert-submit-policy-projection!
                        (contracts/submit-policy-projection actual)
                        {:vector id})
            expected* (contracts/assert-submit-policy-projection!
                       expected
                       {:vector id :expected true})]
        (is (= expected* projection))
        (if (:request-present? projection)
          (is (order-request-contracts/order-request-valid? (:request actual)))
          (is (nil? (:request actual))))))))
