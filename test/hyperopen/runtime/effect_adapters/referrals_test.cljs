(ns hyperopen.runtime.effect-adapters.referrals-test
  (:require [cljs.test :refer-macros [deftest is]]
            [nexus.registry :as nxr]
            [hyperopen.api.default :as api]
            [hyperopen.api.projections :as api-projections]
            [hyperopen.api.trading :as trading-api]
            [hyperopen.referrals.effects :as referrals-effects]
            [hyperopen.runtime.effect-adapters :as effect-adapters]
            [hyperopen.runtime.effect-adapters.common :as common]
            [hyperopen.runtime.effect-adapters.referrals :as referrals-adapters]))

(deftest facade-referral-adapters-delegate-to-referrals-module-test
  (is (identical? referrals-adapters/api-fetch-referral-effect
                  effect-adapters/api-fetch-referral-effect))
  (is (fn? effect-adapters/api-set-referrer-effect))
  (is (fn? effect-adapters/api-register-referrer-effect))
  (is (fn? effect-adapters/api-claim-referral-rewards-effect)))

(deftest referral-fetch-adapter-wires-api-and-projection-dependencies-test
  (let [store (atom {})
        calls (atom [])
        address "0xabc"]
    (with-redefs [referrals-effects/api-fetch-referral!
                  (fn [deps]
                    (swap! calls conj deps)
                    :fetch-result)]
      (is (= :fetch-result
             (referrals-adapters/api-fetch-referral-effect nil store address))))
    (let [deps (first @calls)]
      (is (= store (:store deps)))
      (is (= address (:address deps)))
      (is (identical? api/request-referral! (:request-referral! deps)))
      (is (identical? api-projections/begin-referrals-load (:begin-referrals-load deps)))
      (is (identical? api-projections/apply-referrals-success (:apply-referrals-success deps)))
      (is (identical? api-projections/apply-referrals-error (:apply-referrals-error deps))))))

(deftest referral-submit-adapters-wire-submit-dependencies-test
  (let [store (atom {})
        request {:owner "0xabc" :code "ABC"}
        calls (atom [])
        custom-show-toast! (fn [& _] :custom-toast)]
    (with-redefs [referrals-effects/api-submit-referral-mutation!
                  (fn [deps]
                    (swap! calls conj deps)
                    (:submit-kind deps))]
      (is (= :set-referrer
             (referrals-adapters/api-set-referrer-effect nil store request)))
      (is (= :register-referrer
             (referrals-adapters/api-register-referrer-effect nil store request)))
      (is (= :claim-rewards
             (referrals-adapters/api-claim-referral-rewards-effect nil store request)))
      (is (= :set-referrer
             (referrals-adapters/api-set-referrer-effect
              nil
              store
              request
              {:show-toast! custom-show-toast!}))))
    (let [[set-deps register-deps claim-deps custom-deps] @calls]
      (is (identical? trading-api/set-referrer! (:submit! set-deps)))
      (is (identical? trading-api/register-referrer! (:submit! register-deps)))
      (is (identical? trading-api/claim-rewards! (:submit! claim-deps)))
      (doseq [deps [set-deps register-deps claim-deps]]
        (is (= store (:store deps)))
        (is (= request (:request deps)))
        (is (identical? nxr/dispatch (:dispatch! deps)))
        (is (fn? (:refresh-user-fees! deps)))
        (is (fn? (:refresh-spot-balances! deps)))
        (is (identical? common/exchange-response-error (:exchange-response-error deps)))
        (is (identical? common/runtime-error-message (:runtime-error-message deps)))
        (is (nil? ((:show-toast! deps) store :info "ignored"))))
      (is (identical? custom-show-toast! (:show-toast! custom-deps))))))
