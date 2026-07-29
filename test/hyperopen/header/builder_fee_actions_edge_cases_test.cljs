(ns hyperopen.header.builder-fee-actions-edge-cases-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.header.actions :as actions]))

(def builder "0x36a47878219fb346e031f6cf82cbfc8c77e35932")
(def configured-state
  {:tenant/override {:tenant/id "dexhelm"
                     :builder-fee {:status :configured
                                   :builder-address builder
                                   :fee-tenths-bp 10
                                   :disclosure "A disclosed 0.01% builder fee."}}
   :wallet {:address "0x1111111111111111111111111111111111111111"}})

(deftest review-opens-without-wallet-io-and-disabled-config-exposes-no-review-action-test
  (is (= [[:effects/save [:header-ui :builder-fee-review]
          {:status :reviewing
           :owner-address "0x1111111111111111111111111111111111111111"
           :builder-address builder
           :fee-tenths-bp 10}]]
         (actions/request-builder-fee-review configured-state)))
  (is (= []
         (actions/request-builder-fee-review
          (assoc-in configured-state [:tenant/override :builder-fee :status] :disabled)))))

(deftest confirmation-projects-in-flight-before-one-approval-effect-and-rejects-a-stale-review-test
  (let [review-state (assoc-in configured-state [:header-ui :builder-fee-review]
                               {:status :reviewing
                                :owner-address "0x1111111111111111111111111111111111111111"
                                :builder-address builder
                                :fee-tenths-bp 10})]
    (is (= [[:effects/save [:header-ui :builder-fee-review] {:status :submitting}]
            [:effects/approve-builder-fee "0x1111111111111111111111111111111111111111"]]
           (actions/confirm-builder-fee-review review-state)))
    (is (= [[:effects/save [:header-ui :builder-fee-review] nil]]
           (actions/confirm-builder-fee-review
            (assoc-in review-state [:wallet :address] "0x2222222222222222222222222222222222222222"))))))

(deftest checksum-cased-wallet-address-is-normalized-before-review-and-approval-test
  (let [checksum-owner "0xAbCdEf0123456789AbCdEf0123456789AbCdEf01"
        normalized-owner "0xabcdef0123456789abcdef0123456789abcdef01"
        state (assoc-in configured-state [:wallet :address] checksum-owner)
        review-effects (actions/request-builder-fee-review state)
        review (get-in review-effects [0 2])]
    (is (= normalized-owner (:owner-address review)))
    (is (= [[:effects/save [:header-ui :builder-fee-review] {:status :submitting}]
            [:effects/approve-builder-fee normalized-owner]]
           (actions/confirm-builder-fee-review
            (assoc-in state [:header-ui :builder-fee-review] review))))))
