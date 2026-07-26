(ns hyperopen.websocket.api-wallets-application-coverage-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.api-wallets.application.form-policy :as form-policy]
            [hyperopen.api-wallets.application.ui-state :as ui-state]
            [hyperopen.wallet.agent-session :as agent-session]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def generated-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(def day-ms
  (* 24 60 60 1000))

(defn- days-valid-error-message
  []
  (str "Enter a value from 1 to "
       agent-session/max-agent-valid-days
       " days."))

(deftest ws-api-wallet-form-policy-coverage-test
  (is (= {:name "Enter an API wallet name."
          :address "Enter a valid wallet address."
          :days-valid nil}
         (form-policy/form-errors nil)))
  (is (= {:name nil
          :address nil
          :days-valid nil}
         (form-policy/form-errors {:name " Desk API "
                                   :address (str " " owner-address " ")
                                   :days-valid "   "})))
  (is (= (days-valid-error-message)
         (:days-valid (form-policy/form-errors {:name "Desk"
                                                :address owner-address
                                                :days-valid "0"}))))
  (is (= (days-valid-error-message)
         (:days-valid (form-policy/form-errors {:name "Desk"
                                                :address owner-address
                                                :days-valid "letters"}))))
  (is (true? (form-policy/form-valid? {:name "Desk"
                                       :address owner-address
                                       :days-valid "30"})))
  (is (= "Enter an API wallet name."
         (form-policy/first-form-error {:name ""
                                        :address owner-address
                                        :days-valid ""})))
  (is (= "0xpriv"
         (form-policy/generated-private-key
          {:address generated-address
           :private-key "0xpriv"}
          "0xABCDEFabcdefABCDEFabcdefABCDEFabcdefABCD")))
  (is (nil? (form-policy/generated-private-key {:address generated-address
                                                :private-key "0xpriv"}
                                               owner-address)))
  (is (nil? (form-policy/generated-private-key {:address "not-a-wallet"
                                                :private-key "0xpriv"}
                                               "also-not-a-wallet")))
  (is (nil? (form-policy/valid-until-preview-ms 1700000000000 nil)))
  (is (nil? (form-policy/valid-until-preview-ms "1700000000000" "7")))
  (is (= (+ 1700000000000 (* 7 day-ms))
         (form-policy/valid-until-preview-ms 1700000000000 " 7 "))))

(deftest ws-api-wallet-ui-state-coverage-test
  (is (= {:column :name
          :direction :asc}
         (ui-state/default-sort-state)))
  (is (= {:name ""
          :address ""
          :days-valid ""}
         (ui-state/default-form)))
  (is (= {:open? false
          :type nil
          :row nil
          :error nil
          :submitting? false}
         (ui-state/default-modal-state)))
  (is (= {:address nil
          :private-key nil}
         (ui-state/default-generated-state)))
  (doseq [[value expected] [[:name :name]
                            ["wallet" :address]
                            ["api_wallet_address" :address]
                            ["validUntil" :valid-until]
                            ["valid-until-ms" :valid-until]
                            [nil :name]
                            [42 :name]
                            ["unsupported" :name]]]
    (is (= expected (ui-state/normalize-sort-column value))
        (str "sort column " (pr-str value))))
  (doseq [[value expected] [[:asc :asc]
                            [:desc :desc]
                            [" DESC " :desc]
                            ["sideways" :asc]
                            [nil :asc]
                            [42 :asc]]]
    (is (= expected (ui-state/normalize-sort-direction value))
        (str "sort direction " (pr-str value))))
  (is (= {:column :name
          :direction :asc}
         (ui-state/normalize-sort-state nil)))
  (is (= {:column :address
          :direction :desc}
         (ui-state/next-sort-state {:column "wallet-address"
                                    :direction "ASC"}
                                   "wallet")))
  (is (= {:column :valid-until
          :direction :desc}
         (ui-state/next-sort-state nil "validUntil")))
  (is (= {:column :name
          :direction :desc}
         (ui-state/next-sort-state nil "unsupported")))
  (doseq [[value expected] [[:name :name]
                            ["address" :address]
                            ["days valid" :days-valid]
                            [nil nil]
                            [42 nil]
                            ["wallet" nil]]]
    (is (= expected (ui-state/normalize-form-field value))
        (str "form field " (pr-str value))))
  (is (= "  Desk API  "
         (ui-state/normalize-form-value :name "  Desk API  ")))
  (is (= "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
         (ui-state/normalize-form-value
          :address
          " 0xABCDEFabcdefABCDEFabcdefABCDEFabcdefABCD ")))
  (is (= "309"
         (ui-state/normalize-form-value :days-valid " 30 days, 9 hours ")))
  (is (= ""
         (ui-state/normalize-form-value :days-valid nil)))
  (is (= "42"
         (ui-state/normalize-form-value :unsupported 42))))
