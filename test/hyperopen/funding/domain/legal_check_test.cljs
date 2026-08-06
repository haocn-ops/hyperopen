(ns hyperopen.funding.domain.legal-check-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.funding.domain.legal-check :as legal-check]))

(deftest legal-check-allows-normal-and-hide-outcomes-restrictions-test
  (doseq [restriction ["n" "o"]]
    (testing restriction
      (is (= :allowed
             (:status (legal-check/assess {:acceptedTerms true
                                           :userAllowed true
                                           :restrictions restriction})))))))

(deftest legal-check-blocks-block-actions-and-uk-restrictions-test
  (doseq [restriction ["a" "u"]]
    (let [decision (legal-check/assess {:acceptedTerms true
                                        :userAllowed true
                                        :restrictions restriction})]
      (is (= :blocked (:status decision)))
      (is (= legal-check/jurisdiction-blocked-message (:message decision))))))

(deftest legal-check-blocks-terms-and-user-eligibility-test
  (is (= legal-check/terms-required-message
         (:message (legal-check/assess {:acceptedTerms false
                                        :userAllowed true
                                        :restrictions "n"}))))
  (is (= legal-check/user-not-allowed-message
         (:message (legal-check/assess {:acceptedTerms true
                                        :userAllowed false
                                        :restrictions "n"})))))

(deftest legal-check-fails-closed-for-unknown-or-malformed-results-test
  (doseq [response [nil
                    {}
                    {:acceptedTerms true
                     :userAllowed true
                     :restrictions "unknown"}]]
    (is (= :error (:status (legal-check/assess response))))))
