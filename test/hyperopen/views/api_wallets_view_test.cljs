(ns hyperopen.views.api-wallets-view-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.views.account-info.test-support.hiccup :as hiccup]
            [hyperopen.views.api-wallets-view :as view]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def generated-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(defn- base-state []
  {:wallet {:address owner-address}
   :api-wallets-ui {:form {:name "Desk"
                           :address generated-address
                           :days-valid "30"}
                    :form-error nil
                    :sort {:column :name
                           :direction :asc}
                    :modal {:open? false
                            :type nil
                            :row nil
                            :error nil
                            :submitting? false}
                    :generated {:address generated-address
                                :private-key "0xpriv"}}
   :api-wallets {:extra-agents [{:row-kind :named
                                 :name "Desk"
                                 :approval-name "Desk valid_until 1700000000000"
                                 :address generated-address
                                 :valid-until-ms 1700000000000}]
                 :default-agent-row nil
                 :server-time-ms 1700000000000
                 :loading {:extra-agents? false
                           :default-agent? false}
                 :errors {:extra-agents nil
                          :default-agent nil}}})

(deftest api-wallets-view-renders-authorize-modal-preview-and-generated-key-test
  (let [view-node (view/api-wallets-view
                   (assoc-in (base-state)
                             [:api-wallets-ui :modal]
                             {:open? true
                              :type :authorize
                              :row nil
                              :error nil
                              :submitting? false}))
        modal (hiccup/find-by-data-role view-node "api-wallets-modal")
        confirm-button (hiccup/find-by-data-role view-node "api-wallets-modal-confirm")
        strings (set (hiccup/collect-strings modal))]
    (is (some? modal))
    (is (= "Authorize API wallet" (get-in modal [1 :aria-label])))
    (is (contains? strings "Authorize API Wallet"))
    (is (contains? strings "Generated Private Key"))
    (is (contains? strings "0xpriv"))
    (is (some #(str/starts-with? % "This API wallet will expire on ")
              (hiccup/collect-strings modal)))
    (is (contains? (set (hiccup/collect-strings confirm-button)) "Authorize"))
    (is (not (contains? (hiccup/node-class-set confirm-button) "cursor-not-allowed")))))

(deftest api-wallets-view-renders-remove-modal-error-and-submitting-state-test
  (let [view-node (view/api-wallets-view
                   (assoc-in (base-state)
                             [:api-wallets-ui :modal]
                             {:open? true
                              :type :remove
                              :row {:name "Desk"
                                    :address generated-address
                                    :valid-until-ms nil}
                              :error "Removal failed."
                              :submitting? true}))
        modal (hiccup/find-by-data-role view-node "api-wallets-modal")
        error-node (hiccup/find-by-data-role view-node "api-wallets-modal-error")
        confirm-button (hiccup/find-by-data-role view-node "api-wallets-modal-confirm")
        strings (set (hiccup/collect-strings modal))]
    (testing "the remove branch renders the row summary and error banner"
      (is (some? modal))
      (is (= "Remove API wallet" (get-in modal [1 :aria-label])))
      (is (contains? strings "Remove API Wallet"))
      (is (contains? strings "Removing this API wallet revokes its access immediately."))
      (is (contains? strings "Desk"))
      (is (contains? strings generated-address))
      (is (contains? strings "Never"))
      (is (= "Removal failed." (first (hiccup/collect-strings error-node)))))

    (testing "the confirm button reflects the submitting state"
      (is (contains? (set (hiccup/collect-strings confirm-button)) "Removing..."))
      (is (contains? (hiccup/node-class-set confirm-button) "cursor-not-allowed")))))
