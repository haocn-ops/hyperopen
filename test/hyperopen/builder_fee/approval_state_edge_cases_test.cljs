(ns hyperopen.builder-fee.approval-state-edge-cases-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.builder-fee.approval-state :as approval-state]))

(def owner "0x1111111111111111111111111111111111111111")
(def builder "0x36a47878219fb346e031f6cf82cbfc8c77e35932")
(def approval-identity {:owner-address owner :builder-address builder :network :testnet})

(deftest only-current-owner-network-builder-responses-with-finite-integer-max-builder-fee-become-approval-snapshots-test
  (let [pending (approval-state/begin-refresh approval-identity 7)]
    (is (= {:status :loading
            :request-id 7
            :owner-address owner
            :builder-address builder
            :network :testnet}
           pending))
    (is (= {:status :ready
            :request-id 7
            :owner-address owner
            :builder-address builder
            :network :testnet
            :max-builder-fee 10}
           (approval-state/apply-refresh-response pending approval-identity 7 10)))
    (doseq [invalid [nil "10" true -1 1.5 js/NaN js/Infinity]]
      (testing (str invalid)
        (is (= :unapproved
               (:status (approval-state/apply-refresh-response pending approval-identity 7 invalid))))))))

(deftest stale-or-failed-builder-fee-responses-clear-prior-approval-and-cannot-authorize-a-new-identity-test
  (let [pending (approval-state/begin-refresh approval-identity 7)
        switched (assoc approval-identity :owner-address "0x2222222222222222222222222222222222222222")]
    (is (= :unapproved
           (:status (approval-state/apply-refresh-response pending switched 7 10))))
    (is (= :unapproved
           (:status (approval-state/apply-refresh-error pending approval-identity 7 (js/Error. "offline")))))
    (is (= :unapproved
           (:status (approval-state/apply-refresh-response pending approval-identity 8 10))))))
