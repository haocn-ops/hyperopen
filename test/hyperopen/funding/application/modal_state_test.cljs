(ns hyperopen.funding.application.modal-state-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.funding.application.modal-state :as modal-state]
            [hyperopen.funding.domain.assets :as assets-domain]))

(deftest default-funding-modal-state-owns-the-modal-ui-shape-test
  (let [state (modal-state/default-funding-modal-state)]
    (is (= false (:open? state)))
    (is (= :asset-select (:deposit-step state)))
    (is (= :asset-select (:withdraw-step state)))
    (is (= assets-domain/withdraw-default-asset-key
           (:withdraw-selected-asset-key state)))
    (is (= "" (:deposit-search-input state)))
    (is (= "" (:withdraw-search-input state)))
    (is (= false (:submitting? state)))
    (is (nil? (:error state)))))

(deftest normalize-modal-state-merges-defaults-and-normalizes-application-fields-test
  (let [normalized (modal-state/normalize-modal-state
                    {:stored-modal {:open? true
                                    :anchor {:left "15"
                                             :top "24"}
                                    :withdraw-step "invalid"
                                    :withdraw-selected-asset-key "invalid"
                                    :hyperunit-lifecycle {:direction "withdraw"
                                                          :asset-key "btc"
                                                          :status "done"
                                                          :position-in-withdraw-queue "4"}
                                    :hyperunit-fee-estimate {:status "ready"
                                                             :by-chain {"bitcoin" {:withdrawal-fee "0.00001"}}}
                                    :hyperunit-withdrawal-queue {:status "ready"
                                                                 :by-chain {"bitcoin" {:withdrawal-queue-length "9"}}}}
                     :normalize-anchor-fn (fn [anchor]
                                            {:left (js/parseFloat (:left anchor))
                                             :top (js/parseFloat (:top anchor))})})]
    (is (= true (:open? normalized)))
    (is (= {:left 15
            :top 24}
           (:anchor normalized)))
    (is (= :asset-select (:withdraw-step normalized)))
    (is (= assets-domain/withdraw-default-asset-key
           (:withdraw-selected-asset-key normalized)))
    (is (= :withdraw (get-in normalized [:hyperunit-lifecycle :direction])))
    (is (= :btc (get-in normalized [:hyperunit-lifecycle :asset-key])))
    (is (= :done (get-in normalized [:hyperunit-lifecycle :status])))
    (is (= 4 (get-in normalized [:hyperunit-lifecycle :position-in-withdraw-queue])))
    (is (= :ready (get-in normalized [:hyperunit-fee-estimate :status])))
    (is (= 0.00001
           (get-in normalized [:hyperunit-fee-estimate :by-chain "bitcoin" :withdrawal-fee])))
    (is (= 9
           (get-in normalized [:hyperunit-withdrawal-queue :by-chain "bitcoin" :withdrawal-queue-length])))))
