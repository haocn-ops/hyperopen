(ns hyperopen.subaccounts.effects-spectate-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.subaccounts.actions :as actions]
            [hyperopen.subaccounts.effects :as effects]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def spectate-address
  "0x7777777777777777777777777777777777777777")

(def subaccount-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(deftest load-subaccounts-uses-spectated-master-when-spectate-mode-active-test
  (async done
    (let [calls (atom [])
          store (atom {:router {:path "/subAccounts"}
                       :wallet {:address owner-address}
                       :account-context
                       {:spectate-mode {:active? true
                                         :address spectate-address}
                        :subaccounts {:status :loading
                                      :loaded-for-owner spectate-address
                                      :rows []
                                      :selected-address nil
                                      :error "stale"
                                      :selection-loaded? false}}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [address opts]
                                     (swap! calls conj [address opts])
                                     (js/Promise.resolve
                                      [{:name "Spectated Desk"
                                        :master spectate-address
                                        :sub-account-user subaccount-address}]))
            :local-storage-get (fn [key]
                                 (when (= key (actions/selected-subaccount-storage-key
                                               spectate-address))
                                   subaccount-address))})
          (.then (fn [result]
                   (is (nil? result))
                   (is (= [[spectate-address {:priority :high}]]
                          @calls))
                   (is (= :loaded
                          (get-in @store [:account-context :subaccounts :status])))
                   (is (= spectate-address
                          (get-in @store [:account-context :subaccounts :loaded-for-owner])))
                   (is (= subaccount-address
                          (get-in @store [:account-context :subaccounts :selected-address])))
                   (is (nil? (get-in @store [:account-context :subaccounts :error])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected spectate load-subaccounts error: " err))
                    (done)))))))
