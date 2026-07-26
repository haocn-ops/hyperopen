(ns hyperopen.views.header-subaccount-state-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.header-view :as header-view]))

(def connected-address "0x1234567890abcdef1234567890abcdef12345678")
(def subaccount-address "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(defn- subaccount-header-state
  [selected-address]
  {:wallet {:connected? true
            :address connected-address}
   :account-context {:subaccounts
                     {:selected-address selected-address
                      :rows [{:name "Desk"
                              :master connected-address
                              :sub-account-user subaccount-address}]}}})

(deftest selected-subaccount-header-shows-active-trading-banner-test
  (let [view (header-view/header-view (subaccount-header-state subaccount-address))
        banner (hiccup/find-by-data-role view "header-subaccount-active-banner")
        all-text (set (hiccup/collect-strings view))]
    (is (= "header-shell" (get-in view [1 :data-role])))
    (is (not= :<> (first view)))
    (is (some? banner))
    (is (contains? all-text "Sub: Desk"))
    (is (contains? all-text
                   "IMPORTANT: You are trading on behalf of your sub-account Desk"))))

(deftest selected-subaccount-header-banner-hides-for-master-or-stale-selection-test
  (is (nil? (hiccup/find-by-data-role
             (header-view/header-view (subaccount-header-state nil))
             "header-subaccount-active-banner")))
  (is (nil? (hiccup/find-by-data-role
             (header-view/header-view
              (subaccount-header-state "0x9999999999999999999999999999999999999999"))
             "header-subaccount-active-banner"))))
