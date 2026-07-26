(ns hyperopen.api.projections.referrals-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.api.projections.referrals :as referrals]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def other-address
  "0x9999999999999999999999999999999999999999")

(deftest referrals-projections-track-load-success-and-error-by-address-test
  (let [payload {:cumVlm "12000"
                 :referrerState {:stage "ready"}}
        loading (referrals/begin-load {} owner-address)
        success (referrals/apply-success loading owner-address payload)
        failed (referrals/apply-error loading owner-address (js/Error. "referral-fail"))]
    (is (= true (get-in loading [:referrals :loading?])))
    (is (= owner-address (get-in loading [:referrals :loading-for-address])))
    (is (nil? (get-in loading [:referrals :error])))
    (is (= payload (get-in success [:referrals :raw])))
    (is (= false (get-in success [:referrals :loading?])))
    (is (= owner-address (get-in success [:referrals :loaded-for-address])))
    (is (number? (get-in success [:referrals :loaded-at-ms])))
    (is (= false (get-in failed [:referrals :loading?])))
    (is (= "Error: referral-fail" (get-in failed [:referrals :error])))
    (is (= owner-address (get-in failed [:referrals :error-for-address])))))

(deftest referrals-projections-ignore-stale-success-and-error-test
  (let [state {:referrals {:loading? true
                           :loading-for-address owner-address
                           :raw {:old true}}}
        stale-success (referrals/apply-success state other-address {:new true})
        stale-error (referrals/apply-error state other-address (js/Error. "stale"))]
    (is (= state stale-success))
    (is (= state stale-error))))
