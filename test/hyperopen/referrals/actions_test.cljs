(ns hyperopen.referrals.actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.context :as account-context]
            [hyperopen.referrals.actions :as actions]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def selected-subaccount-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(def spectate-address
  "0x7777777777777777777777777777777777777777")

(defn- path-value
  [effects path]
  (let [[_ path-values] (first effects)
        missing (js-obj)]
    (let [value (reduce (fn [result [candidate-path candidate-value]]
                          (if (= path candidate-path)
                            (reduced candidate-value)
                            result))
                        missing
                        path-values)]
      (when-not (identical? missing value)
        value))))

(defn- selected-subaccount-state
  []
  {:wallet {:address owner-address}
   :account-context
   {:subaccounts {:selected-address selected-subaccount-address
                  :rows [{:master owner-address
                          :sub-account-user selected-subaccount-address}]}}})

(deftest route-and-join-code-parsing-test
  (is (= "/referrals" actions/canonical-route))
  (is (true? (actions/referrals-route? "/referrals")))
  (is (true? (actions/referrals-route? "/referrals?tab=legacy")))
  (is (true? (actions/referrals-route? "/join/abc123?source=friend")))
  (is (false? (actions/referrals-route? "/leaderboard")))
  (is (= "ABC123" (actions/join-code-from-path "/join/abc123?source=friend")))
  (is (= "ABC123" (actions/join-code-from-path "/join/ABC123/")))
  (is (nil? (actions/join-code-from-path "/referrals"))))

(deftest referral-code-normalization-and-validation-test
  (is (= "ABC123" (actions/normalize-referral-code " abc123 ")))
  (is (true? (actions/valid-referral-code? "abc123")))
  (is (false? (actions/valid-referral-code? "")))
  (is (false? (actions/valid-referral-code? "abc-123")))
  (is (false? (actions/valid-referral-code?
               "AAAAAAAAAAAAAAAAAAAAA"))))

(deftest load-referrals-route-prefills-join-code-and-loads-connected-master-test
  (let [effects (actions/load-referrals-route
                 {:wallet {:address owner-address}}
                 "/join/abc123")]
    (is (= :effects/save-many (ffirst effects)))
    (is (= "ABC123" (path-value effects [:referrals-ui :pending-code])))
    (is (= :enter-code (path-value effects [:referrals-ui :active-modal])))
    (is (= "ABC123" (path-value effects [:referrals-ui :form :code])))
    (is (nil? (path-value effects [:referrals-ui :last-error])))
    (is (= false (path-value effects [:referrals-ui :submitting?])))
    (is (= [:effects/api-fetch-referral owner-address]
           (second effects)))))

(deftest load-referrals-route-loads-spectated-account-when-spectate-mode-active-test
  (let [effects (actions/load-referrals-route
                 {:wallet {:address owner-address}
                  :account-context {:spectate-mode {:active? true
                                                    :address spectate-address}}}
                 "/referrals")]
    (is (= [:effects/api-fetch-referral spectate-address]
           (second effects)))))

(deftest load-referrals-route-preserves-pending-code-while-disconnected-test
  (let [effects (actions/load-referrals-route
                 {:wallet {:address nil}}
                 "/join/abc123")]
    (is (= "ABC123" (path-value effects [:referrals-ui :pending-code])))
    (is (= :enter-code (path-value effects [:referrals-ui :active-modal])))
    (is (= "ABC123" (path-value effects [:referrals-ui :form :code])))
    (is (= 1 (count effects)))))

(deftest load-referrals-route-clears-modal-for-plain-referrals-route-test
  (let [effects (actions/load-referrals-route
                 {:wallet {:address owner-address}
                  :referrals-ui {:active-modal :claim-rewards
                                 :form {:code "OLD"}}}
                 "/referrals")]
    (is (nil? (path-value effects [:referrals-ui :pending-code])))
    (is (nil? (path-value effects [:referrals-ui :active-modal])))
    (is (= "OLD" (path-value effects [:referrals-ui :form :code])))))

(deftest load-referrals-route-skips-inactive-route-test
  (is (= []
         (actions/load-referrals-route {:wallet {:address owner-address}}
                                       "/trade"))))

(deftest modal-actions-open-valid-modal-and-clear-errors-test
  (is (= [[:effects/save-many [[[:referrals-ui :active-modal] :enter-code]
                               [[:referrals-ui :last-error] nil]]]]
         (actions/open-referrals-modal {:referrals-ui {:last-error "old"}}
                                       :enter-code)))
  (is (= [[:effects/save-many [[[:referrals-ui :active-modal] :create-code]
                               [[:referrals-ui :last-error] nil]]]]
         (actions/open-referrals-modal {} :unknown)))
  (is (= [[:effects/save-many [[[:referrals-ui :active-modal] nil]
                               [[:referrals-ui :last-error] nil]]]]
         (actions/close-referrals-modal {:referrals-ui {:active-modal :claim-rewards
                                                        :last-error "old"}}))))

(deftest submit-set-referrer-builds-master-scoped-effect-test
  (is (= [[:effects/save-many [[[:referrals-ui :last-error] nil]
                               [[:referrals-ui :submitting?] :set-referrer]]]
          [:effects/api-set-referrer {:owner owner-address
                                      :code "ABC123"}]]
         (actions/submit-set-referrer
          {:wallet {:address owner-address}
           :referrals-ui {:form {:code " abc123 "}}}))))

(deftest submit-register-referrer-builds-master-scoped-effect-test
  (is (= [[:effects/save-many [[[:referrals-ui :last-error] nil]
                               [[:referrals-ui :submitting?] :register-referrer]]]
          [:effects/api-register-referrer {:owner owner-address
                                           :code "MYCODE"}]]
         (actions/submit-register-referrer
          {:wallet {:address owner-address}
           :referrals-ui {:form {:new-code " mycode "}}}))))

(deftest submit-claim-rewards-builds-master-scoped-effect-test
  (is (= [[:effects/save-many [[[:referrals-ui :last-error] nil]
                               [[:referrals-ui :submitting?] :claim-rewards]]]
          [:effects/api-claim-referral-rewards {:owner owner-address}]]
         (actions/submit-claim-rewards
          {:wallet {:address owner-address}}))))

(deftest referral-mutations-require-wallet-and-valid-code-test
  (is (= [[:effects/save [:referrals-ui :last-error]
           "Connect your wallet before entering a referral code."]]
         (actions/submit-set-referrer {:wallet {:address nil}
                                       :referrals-ui {:form {:code "ABC"}}})))
  (is (= [[:effects/save [:referrals-ui :last-error]
           "Enter a valid referral code."]]
         (actions/submit-set-referrer {:wallet {:address owner-address}
                                       :referrals-ui {:form {:code "bad-code"}}}))))

(deftest referral-mutations-block-spectate-and-selected-subaccounts-test
  (let [spectate-state {:wallet {:address owner-address}
                        :account-context {:spectate-mode {:active? true
                                                          :address spectate-address}}
                        :referrals-ui {:form {:code "ABC"}}}
        subaccount-state (assoc (selected-subaccount-state)
                                :referrals-ui {:form {:code "ABC"
                                                      :new-code "MYCODE"}})]
    (is (= [[:effects/save [:referrals-ui :last-error]
             account-context/spectate-mode-read-only-message]]
           (actions/submit-set-referrer spectate-state)))
    (is (= [[:effects/save [:referrals-ui :last-error]
             actions/selected-subaccount-referral-blocked-message]]
           (actions/submit-set-referrer subaccount-state)))
    (is (= [[:effects/save [:referrals-ui :last-error]
             actions/selected-subaccount-referral-blocked-message]]
           (actions/submit-register-referrer subaccount-state)))
    (is (= [[:effects/save [:referrals-ui :last-error]
             actions/selected-subaccount-referral-blocked-message]]
           (actions/submit-claim-rewards subaccount-state)))))
