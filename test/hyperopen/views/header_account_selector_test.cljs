(ns hyperopen.views.header-account-selector-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.header-view :as header-view]
            [hyperopen.views.header.account-selector :as selector]
            [hyperopen.wallet.core :as wallet]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def subaccount-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(def other-subaccount-address
  "0x9999999999999999999999999999999999999999")

(defn- state
  [& {:keys [connected? selected rows]}]
  {:wallet {:connected? (not= false connected?)
            :address owner-address}
   :account-context {:subaccounts
                     {:selected-address selected
                      :rows (or rows
                                [{:name "Desk"
                                  :master owner-address
                                  :sub-account-user subaccount-address}
                                 {:name "Other Owner"
                                  :master other-subaccount-address
                                  :sub-account-user other-subaccount-address}])}}})

(deftest header-account-selector-vm-projects-owned-master-and-subaccount-options-test
  (let [result (selector/vm (state :selected subaccount-address))
        master-option (first (:options result))
        subaccount-option (second (:options result))]
    (is (= "Sub: Desk" (:trigger-label result)))
    (is (= (wallet/short-addr subaccount-address)
           (:trigger-address-label result)))
    (is (= {:name "Desk"
            :address subaccount-address}
           (:active-subaccount result)))
    (is (= ["Master" "Sub"]
           (mapv :label (:options result))))
    (is (= ["Desk"]
           (mapv :name-label (rest (:options result)))))
    (is (= [[:actions/select-master-account]]
           (:action master-option)))
    (is (= [[:actions/select-subaccount subaccount-address]]
           (:action subaccount-option)))
    (is (= [[:actions/copy-subaccount-address owner-address]]
           (:copy-action master-option)))
    (is (= [[:actions/copy-subaccount-address subaccount-address]]
           (:copy-action subaccount-option)))
    (is (= {:label "Disconnect"
            :action [[:actions/select-master-account]]}
           (:disconnect result)))
    (is (false? (:selected? master-option)))
    (is (true? (:selected? subaccount-option)))))

(deftest header-account-selector-vm-hides-without-connected-owned-subaccounts-test
  (is (nil? (selector/vm (state :connected? false))))
  (is (nil? (selector/vm (state :rows []))))
  (let [result (selector/vm (state :selected other-subaccount-address))]
    (is (= "Master" (:trigger-label result)))
    (is (true? (:selected? (first (:options result)))))))

(deftest header-renders-account-target-menu-with-existing-selection-actions-test
  (let [view (header-view/header-view (state :selected subaccount-address))
        details (hiccup/find-by-data-role view "header-account-target-details")
        trigger (hiccup/find-by-data-role view "wallet-menu-trigger")
        menu (hiccup/find-by-data-role view "wallet-menu-panel")
        master-option (hiccup/find-by-data-role
                       view
                       "header-account-target-option-master")
        subaccount-option (hiccup/find-by-data-role
                           view
                           (str "header-account-target-option-"
                                subaccount-address))
        strings (set (hiccup/collect-strings view))]
    (is (nil? details))
    (is (some? trigger))
    (is (= "Connected account" (get-in trigger [1 :aria-label])))
    (is (some? menu))
    (is (contains? strings "Master"))
    (is (contains? strings "Desk"))
    (is (contains? strings "Sub: Desk"))
    (is (contains? strings "Sub"))
    (is (contains? strings "Disconnect"))
    (is (= [[:actions/select-master-account]]
           (get-in master-option [1 :on :click])))
    (is (= [[:actions/select-subaccount subaccount-address]]
           (get-in subaccount-option [1 :on :click])))
    (is (= "true" (get-in subaccount-option [1 :aria-current])))))

(deftest connected-header-uses-single-wallet-dropdown-for-account-selection-test
  (let [view (header-view/header-view (state :selected subaccount-address))
        wallet-details (hiccup/find-by-data-role view "wallet-menu-details")
        wallet-trigger (hiccup/find-by-data-role view "wallet-menu-trigger")
        account-details (hiccup/find-by-data-role view "header-account-target-details")
        wallet-menu (hiccup/find-by-data-role view "wallet-menu-panel")
        wallet-copy (hiccup/find-by-data-role view "wallet-menu-copy")
        wallet-disconnect (hiccup/find-by-data-role view "wallet-menu-disconnect")
        strings (set (hiccup/collect-strings wallet-trigger))]
    (is (some? wallet-details))
    (is (some? wallet-trigger))
    (is (nil? account-details))
    (is (some? wallet-menu))
    (is (contains? strings "Sub: Desk"))
    (is (not (contains? strings (wallet/short-addr subaccount-address))))
    (is (some? wallet-copy))
    (is (some? wallet-disconnect))))

(deftest header-renders-account-target-copy-buttons-and-disconnect-test
  (let [view (header-view/header-view (state :selected subaccount-address))
        master-copy (hiccup/find-by-data-role
                     view
                     "header-account-target-copy-master")
        subaccount-copy (hiccup/find-by-data-role
                         view
                         (str "header-account-target-copy-" subaccount-address))
        account-target-disconnect (hiccup/find-by-data-role
                                   view
                                   "header-account-target-disconnect")
        wallet-disconnect (hiccup/find-by-data-role
                           view
                           "wallet-menu-disconnect")]
    (is (some? master-copy))
    (is (= "Copy master account address"
           (get-in master-copy [1 :aria-label])))
    (is (= [[:actions/copy-subaccount-address owner-address]]
           (get-in master-copy [1 :on :click])))
    (is (some? subaccount-copy))
    (is (= "Copy subaccount address"
           (get-in subaccount-copy [1 :aria-label])))
    (is (= [[:actions/copy-subaccount-address subaccount-address]]
           (get-in subaccount-copy [1 :on :click])))
    (is (nil? account-target-disconnect))
    (is (some? wallet-disconnect))
    (is (= [[:actions/disconnect-wallet]]
           (get-in wallet-disconnect [1 :on :click])))))
