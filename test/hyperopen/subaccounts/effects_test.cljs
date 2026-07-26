(ns hyperopen.subaccounts.effects-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [async deftest is]]
            [hyperopen.subaccounts.actions :as actions]
            [hyperopen.subaccounts.effects :as effects]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def subaccount-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(def other-subaccount-address
  "0x9999999999999999999999999999999999999999")

(defn- management-store
  []
  (atom {:router {:path "/subAccounts"}
         :wallet {:address owner-address}
         :account-context
         {:subaccounts {:rows [{:name "Desk"
                                :master owner-address
                                :sub-account-user subaccount-address}]
                        :selected-address subaccount-address
                        :create-name "Desk"
                        :create-popover-open? true
                        :rename-name "Ops"
                        :transfer-amount "1.23"
                        :transfer-direction :deposit
                        :transfer-account :trading
                        :transfer-account-menu-open? false
                        :transfer-token "USDC"
                        :transfer-token-menu-open? false
                        :creating? true
                        :renaming-address subaccount-address
                        :transferring-address subaccount-address}}}))

(deftest load-subaccounts-loads-global-header-state-when-route-is-inactive-test
  (async done
    (let [calls (atom [])
          store (atom {:router {:path "/trade"}
                       :wallet {:address owner-address}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [_address _opts]
                                     (swap! calls conj [_address _opts])
                                     (js/Promise.resolve
                                      [{:name "Desk"
                                        :master owner-address
                                        :sub-account-user subaccount-address}]))
            :local-storage-get (fn [key]
                                 (when (= key (actions/selected-subaccount-storage-key owner-address))
                                   subaccount-address))})
          (.then (fn [result]
                   (is (nil? result))
                   (is (= [[owner-address {:priority :high}]]
                          @calls))
                   (is (= :loaded
                          (get-in @store [:account-context :subaccounts :status])))
                   (is (= subaccount-address
                          (get-in @store [:account-context :subaccounts :selected-address])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected global header load error: " err))
                    (done)))))))

(deftest load-subaccounts-resets-state-when-owner-wallet-is-missing-test
  (async done
    (let [calls (atom 0)
          store (atom {:router {:path "/subAccounts"}
                       :wallet {:address nil}
                       :account-context
                       {:subaccounts {:status :loaded
                                      :loaded-for-owner owner-address
                                      :rows [{:name "Desk"}]
                                      :selected-address subaccount-address
                                      :error "stale"
                                      :selection-loaded? true}}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [_address _opts]
                                     (swap! calls inc)
                                     (js/Promise.resolve []))})
          (.then (fn [result]
                   (is (nil? result))
                   (is (= 0 @calls))
                   (is (= {:status :idle
                           :loaded-for-owner nil
                           :owner-mode nil
                           :owner-snapshot nil
                           :rows []
                           :error nil
                           :refreshing? false
                           :selected-address nil
                           :selection-loaded? false
                           :create-name ""
                           :create-popover-open? false
                           :rename-name ""
                           :transfer-amount ""
                           :transfer-direction :deposit
                           :transfer-account :trading
                           :transfer-account-menu-open? false
                           :transfer-token "USDC"
                           :transfer-token-menu-open? false
                           :creating? false
                           :renaming-address nil
                           :transferring-address nil}
                          (get-in @store [:account-context :subaccounts])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected missing-owner error: " err))
                    (done)))))))

(deftest load-subaccounts-applies-owned-rows-and-restores-valid-selection-test
  (async done
    (let [calls (atom [])
          store (atom {:router {:path "/subAccounts"}
                       :wallet {:address owner-address}
                       :account-context
                       {:subaccounts {:status :loading
                                      :loaded-for-owner owner-address
                                      :rows []
                                      :selected-address nil
                                      :error "stale"
                                      :selection-loaded? false}}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [address opts]
                                     (swap! calls conj [address opts])
                                     (js/Promise.resolve
                                      [{:name "Desk"
                                        :master owner-address
                                        :sub-account-user subaccount-address}
                                       {:name "Other"
                                        :master owner-address
                                        :sub-account-user other-subaccount-address}]))
            :local-storage-get (fn [key]
                                 (when (= key (actions/selected-subaccount-storage-key owner-address))
                                   subaccount-address))})
          (.then (fn [result]
                   (is (nil? result))
                   (is (= [[owner-address {:priority :high}]]
                          @calls))
                   (is (= :loaded
                          (get-in @store [:account-context :subaccounts :status])))
                   (is (= owner-address
                          (get-in @store [:account-context :subaccounts :loaded-for-owner])))
                   (is (= subaccount-address
                          (get-in @store [:account-context :subaccounts :selected-address])))
                   (is (true? (get-in @store [:account-context :subaccounts :selection-loaded?])))
                   (is (nil? (get-in @store [:account-context :subaccounts :error])))
                   (done)))
          (.catch (fn [err]
	                    (is false (str "Unexpected load-subaccounts success-path error: " err))
	                    (done)))))))

(deftest load-subaccounts-loads-owner-snapshot-for-viewed-master-test
  (async done
    (let [calls (atom [])
          store (atom {:router {:path "/subAccounts"}
                       :wallet {:address owner-address}
                       :account-context
                       {:subaccounts {:status :loading
                                      :loaded-for-owner owner-address
                                      :rows []
                                      :selected-address subaccount-address
                                      :error nil
                                      :selection-loaded? false}}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [address opts]
                                     (swap! calls conj [:subaccounts address opts])
                                     (js/Promise.resolve
                                      [{:name "Desk"
                                        :master owner-address
                                        :sub-account-user subaccount-address}]))
            :request-owner-clearinghouse-state! (fn [address opts]
                                                  (swap! calls conj [:owner-clearinghouse address opts])
                                                  (js/Promise.resolve
                                                   {:withdrawable "12.34"
                                                    :marginSummary {:accountValue "99.99"}}))
            :request-owner-spot-state! (fn [address opts]
                                         (swap! calls conj [:owner-spot address opts])
                                         (js/Promise.resolve
                                          {:balances [{:coin "USDC"
                                                       :total "0"}]}))
            :local-storage-get (constantly nil)})
          (.then (fn [result]
                   (is (nil? result))
                   (is (some #(= [:owner-clearinghouse owner-address {:priority :high}] %)
                             @calls))
                   (is (some #(= [:owner-spot owner-address {:priority :high}] %)
                             @calls))
                   (is (= {:owner owner-address
                           :clearinghouse-state {:withdrawable "12.34"
                                                 :marginSummary {:accountValue "99.99"}}
                           :spot-state {:balances [{:coin "USDC"
                                                    :total "0"}]}
                           :loading? false
                           :error nil}
                          (get-in @store [:account-context :subaccounts :owner-snapshot])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected owner-snapshot load error: " err))
                    (done)))))))

(deftest load-subaccounts-keeps-current-valid-selection-ahead-of-stored-selection-test
  (async done
    (let [store (atom {:router {:path "/subAccounts"}
                       :wallet {:address owner-address}
                       :account-context
                       {:subaccounts {:selected-address other-subaccount-address}}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [_address _opts]
                                     (js/Promise.resolve
                                      [{:name "Desk"
                                        :master owner-address
                                        :sub-account-user subaccount-address}
                                       {:name "Other"
                                        :master owner-address
                                        :sub-account-user other-subaccount-address}]))
            :local-storage-get (constantly subaccount-address)})
          (.then (fn [_]
                   (is (= other-subaccount-address
                          (get-in @store [:account-context :subaccounts :selected-address])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected selection-retention error: " err))
                    (done)))))))

(deftest load-subaccounts-clears-unowned-stored-selection-test
  (async done
    (let [store (atom {:router {:path "/subAccounts"}
                       :wallet {:address owner-address}
                       :account-context
                       {:subaccounts {:selected-address nil}}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [_address _opts]
                                     (js/Promise.resolve
                                      [{:name "Desk"
                                        :master owner-address
                                        :sub-account-user subaccount-address}]))
            :local-storage-get (constantly other-subaccount-address)})
          (.then (fn [_]
                   (is (nil? (get-in @store [:account-context :subaccounts :selected-address])))
                   (is (true? (get-in @store [:account-context :subaccounts :selection-loaded?])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected stored-selection clear error: " err))
                    (done)))))))

(deftest load-subaccounts-records-request-error-without-rejecting-test
  (async done
    (let [store (atom {:router {:path "/subAccounts"}
                       :wallet {:address owner-address}
                       :account-context
                       {:subaccounts {:status :loading
                                      :error nil}}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [_address _opts]
                                     (js/Promise.reject (js/Error. "boom")))})
          (.then (fn [result]
                   (is (nil? result))
                   (is (= :error
                          (get-in @store [:account-context :subaccounts :status])))
                   (is (= "boom"
                          (get-in @store [:account-context :subaccounts :error])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected load-subaccounts rejection: " err))
                    (done)))))))

(deftest api-load-subaccounts-wrapper-defaults-force-refresh-to-false-test
  (let [captured-opts (atom nil)]
    (with-redefs [effects/load-subaccounts!
                  (fn [opts]
                    (reset! captured-opts opts)
                    :loaded)]
      (is (= :loaded
             (effects/api-load-subaccounts! {:store :test-store
                                             :request-sub-accounts! :request})))
      (is (= {:store :test-store
              :request-sub-accounts! :request
              :force-refresh? false}
             @captured-opts)))))

(deftest api-refresh-subaccounts-uses-force-path-and-clears-refreshing-flag-test
  (async done
    (let [captured-opts (atom nil)
          store (atom {:router {:path "/subAccounts"}
                       :wallet {:address owner-address}
                       :account-context
                       {:subaccounts {:status :loaded
                                      :loaded-for-owner owner-address
                                      :rows [{:name "Stale"
                                              :master owner-address
                                              :sub-account-user subaccount-address}]
                                      :refreshing? true}}})]
      (-> (effects/api-refresh-subaccounts!
           {:store store
            :now-ms-fn (constantly 999)
            :request-sub-accounts! (fn [_address opts]
                                     (reset! captured-opts opts)
                                     (js/Promise.resolve
                                      [{:name "Fresh"
                                        :master owner-address
                                        :sub-account-user subaccount-address}]))})
          (.then (fn [_]
                   (is (= [:sub-accounts owner-address 999]
                          (:dedupe-key @captured-opts))
                       "Force refresh must use a unique tokenized dedupe key to bypass any stuck single-flight entry.")
                   (is (= [:sub-accounts owner-address 999]
                          (:cache-key @captured-opts)))
                   (is (= :loaded
                          (get-in @store [:account-context :subaccounts :status])))
                   (is (= "Fresh"
                          (-> @store
                              (get-in [:account-context :subaccounts :rows])
                              first
                              :name)))
                   (is (false? (get-in @store [:account-context :subaccounts :refreshing?]))
                       "Refreshing flag must be cleared once the force load settles.")
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected api-refresh-subaccounts! error: " err))
                    (done)))))))

(deftest api-refresh-subaccounts-clears-refreshing-flag-and-keeps-rows-on-error-test
  (async done
    (let [store (atom {:router {:path "/subAccounts"}
                       :wallet {:address owner-address}
                       :account-context
                       {:subaccounts {:status :loaded
                                      :loaded-for-owner owner-address
                                      :rows [{:name "Stale"
                                              :master owner-address
                                              :sub-account-user subaccount-address}]
                                      :refreshing? true}}})]
      (-> (effects/api-refresh-subaccounts!
           {:store store
            :request-sub-accounts! (fn [_address _opts]
                                     (js/Promise.reject (js/Error. "boom")))})
          (.then (fn [_]
                   (is (= :error
                          (get-in @store [:account-context :subaccounts :status])))
                   (is (= "boom"
                          (get-in @store [:account-context :subaccounts :error])))
                   (is (false? (get-in @store [:account-context :subaccounts :refreshing?])))
                   (is (= "Stale"
                          (-> @store
                              (get-in [:account-context :subaccounts :rows])
                              first
                              :name))
                       "A failed refresh must keep the previously rendered rows visible.")
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected refresh error rejection: " err))
                    (done)))))))

(deftest create-subaccount-success-clears-form-and-force-refreshes-rows-test
  (async done
    (let [store (management-store)
          submit-calls (atom [])
          refresh-calls (atom [])]
      (-> (effects/create-subaccount!
           {:store store
            :create-sub-account! (fn [store* owner name]
                                   (swap! submit-calls conj [store* owner name])
                                   (js/Promise.resolve {:status "ok"
                                                        :response {:type "createSubAccount"
                                                                   :data other-subaccount-address}}))
            :load-subaccounts! (fn [opts]
                                 (swap! refresh-calls conj
                                        (select-keys opts [:force-refresh?]))
                                 (js/Promise.resolve :reloaded))
            :runtime-error-message (fn [err] (str err))})
          (.then (fn [result]
                   (is (= :reloaded result))
                   (is (= [[store owner-address "Desk"]] @submit-calls))
                   (is (= [{:force-refresh? true}] @refresh-calls))
                   (is (false? (get-in @store [:account-context :subaccounts :creating?])))
                   (is (false? (get-in @store [:account-context :subaccounts :create-popover-open?])))
                   (is (= "" (get-in @store [:account-context :subaccounts :create-name])))
                   (is (nil? (get-in @store [:account-context :subaccounts :error])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected create-subaccount success error: " err))
                    (done)))))))

(deftest create-subaccount-exchange-error-clears-pending-and-surfaces-message-test
  (async done
    (let [store (management-store)
          refresh-calls (atom 0)]
      (-> (effects/create-subaccount!
           {:store store
            :create-sub-account! (fn [_store _owner _name]
                                   (js/Promise.resolve {:status "err"
                                                        :response "Sub-account limit reached"}))
            :load-subaccounts! (fn [_opts]
                                 (swap! refresh-calls inc)
                                 (js/Promise.resolve :reloaded))
            :runtime-error-message (fn [err] (str err))})
          (.then (fn [_]
                   (is (= 0 @refresh-calls))
                   (is (false? (get-in @store [:account-context :subaccounts :creating?])))
                   (is (= "Sub-account limit reached"
                          (get-in @store [:account-context :subaccounts :error])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected create-subaccount error handling rejection: " err))
                    (done)))))))

(deftest rename-subaccount-success-normalizes-address-and-refreshes-test
  (async done
    (let [store (management-store)
          submit-calls (atom [])
          refresh-calls (atom [])]
      (-> (effects/rename-subaccount!
           {:store store
            :request {:sub-account-user (str/upper-case subaccount-address)
                      :name "Ops"}
            :modify-sub-account! (fn [store* owner address name]
                                   (swap! submit-calls conj [store* owner address name])
                                   (js/Promise.resolve {:status "ok"
                                                        :response {:type "default"}}))
            :load-subaccounts! (fn [opts]
                                 (swap! refresh-calls conj
                                        (select-keys opts [:force-refresh?]))
                                 (js/Promise.resolve :reloaded))
            :runtime-error-message (fn [err] (str err))})
          (.then (fn [result]
                   (is (= :reloaded result))
                   (is (= [[store owner-address subaccount-address "Ops"]]
                          @submit-calls))
                   (is (= [{:force-refresh? true}] @refresh-calls))
                   (is (nil? (get-in @store [:account-context :subaccounts :renaming-address])))
                   (is (= "" (get-in @store [:account-context :subaccounts :rename-name])))
                   (is (nil? (get-in @store [:account-context :subaccounts :error])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected rename-subaccount success error: " err))
                    (done)))))))

(deftest transfer-subaccount-success-refreshes-rows-and-active-user-data-test
  (async done
    (let [store (management-store)
          submit-calls (atom [])
          refresh-calls (atom [])
          dispatch-calls (atom [])
          toast-calls (atom [])]
      (-> (effects/transfer-subaccount!
           {:store store
            :request {:sub-account-user subaccount-address
                      :is-deposit true
                      :usd 1230000
                      :amount "1.23"}
            :transfer-sub-account! (fn [store* owner address is-deposit? usd]
                                     (swap! submit-calls conj
                                            [store* owner address is-deposit? usd])
                                     (js/Promise.resolve {:status "ok"
                                                          :response {:type "default"}}))
            :load-subaccounts! (fn [opts]
                                 (swap! refresh-calls conj
                                        (select-keys opts [:force-refresh?]))
                                 (js/Promise.resolve :reloaded))
            :dispatch! (fn [store* ctx effects*]
                         (swap! dispatch-calls conj [store* ctx effects*])
                         nil)
            :show-toast! (fn [_store kind message]
                           (swap! toast-calls conj [kind message]))
            :runtime-error-message (fn [err] (str err))})
          (.then (fn [result]
                   (is (= :reloaded result))
                   (is (= [[store owner-address subaccount-address true 1230000]]
                          @submit-calls))
                   (is (= [{:force-refresh? true}] @refresh-calls))
                   (is (= [[store nil [[:effects/api-load-user-data subaccount-address]]]]
                          @dispatch-calls))
                   (is (= [[:success {:headline "Transfer submitted"
                                       :subline "1.23 USDC from Master Account to Desk"}]]
                          @toast-calls))
                   (is (nil? (get-in @store [:account-context :subaccounts :transferring-address])))
                   (is (= "" (get-in @store [:account-context :subaccounts :transfer-amount])))
                   (is (= :deposit
                          (get-in @store [:account-context :subaccounts :transfer-direction])))
                   (is (= :trading
                          (get-in @store [:account-context :subaccounts :transfer-account])))
                   (is (false?
                        (get-in @store [:account-context :subaccounts :transfer-account-menu-open?])))
                   (is (= "USDC"
                          (get-in @store [:account-context :subaccounts :transfer-token])))
                   (is (false?
                        (get-in @store [:account-context :subaccounts :transfer-token-menu-open?])))
                   (is (nil? (get-in @store [:account-context :subaccounts :error])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected transfer-subaccount success error: " err))
                    (done)))))))

(deftest unified-transfer-subaccount-uses-send-asset-instead-of-l1-transfer-test
  (async done
    (let [store (management-store)
          l1-calls (atom [])
          send-asset-calls (atom [])
          refresh-calls (atom [])]
      (swap! store assoc-in [:account :mode] :unified)
      (-> (effects/transfer-subaccount!
           {:store store
            :request {:sub-account-user subaccount-address
                      :is-deposit true
                      :usd 1230000
                      :amount "1.23"}
            :transfer-sub-account! (fn [& args]
                                     (swap! l1-calls conj args)
                                     (js/Promise.resolve {:status "err"
                                                          :response "Action disabled when unified account is active"}))
            :submit-send-asset! (fn [store* owner action]
                                  (swap! send-asset-calls conj [store* owner action])
                                  (js/Promise.resolve {:status "ok"
                                                       :response {:type "default"}}))
            :load-subaccounts! (fn [opts]
                                 (swap! refresh-calls conj
                                        (select-keys opts [:force-refresh?]))
                                 (js/Promise.resolve :reloaded))
            :dispatch! (fn [_store _ctx _effects] nil)
            :runtime-error-message (fn [err] (str err))})
          (.then (fn [result]
                   (is (= :reloaded result))
                   (is (= [] @l1-calls))
                   (is (= [[store
                            owner-address
                            {:type "sendAsset"
                             :destination subaccount-address
                             :sourceDex "spot"
                             :destinationDex "spot"
                             :token "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"
                             :amount "1.23"
                             :fromSubAccount ""}]]
                          @send-asset-calls))
                   (is (= [{:force-refresh? true}] @refresh-calls))
                   (is (nil? (get-in @store [:account-context :subaccounts :error])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected unified send-asset transfer error: " err))
                    (done)))))))

(deftest unified-transfer-subaccount-withdraw-uses-subaccount-as-source-test
  (async done
    (let [store (management-store)
          send-asset-calls (atom [])]
      (swap! store assoc-in [:account :mode] :unified)
      (-> (effects/transfer-subaccount!
           {:store store
            :request {:sub-account-user subaccount-address
                      :is-deposit false
                      :usd 2500000
                      :amount "2.5"}
            :transfer-sub-account! (fn [& _args]
                                     (js/Promise.resolve {:status "err"
                                                          :response "Should not submit L1 transfer"}))
            :submit-send-asset! (fn [store* owner action]
                                  (swap! send-asset-calls conj [store* owner action])
                                  (js/Promise.resolve {:status "ok"
                                                       :response {:type "default"}}))
            :load-subaccounts! (fn [_opts]
                                 (js/Promise.resolve :reloaded))
            :dispatch! (fn [_store _ctx _effects] nil)
            :runtime-error-message (fn [err] (str err))})
          (.then (fn [_result]
                   (is (= [[store
                            owner-address
                            {:type "sendAsset"
                             :destination owner-address
                             :sourceDex "spot"
                             :destinationDex "spot"
                             :token "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"
                             :amount "2.5"
                             :fromSubAccount subaccount-address}]]
                          @send-asset-calls))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected unified send-asset withdraw error: " err))
                    (done)))))))

(deftest transfer-subaccount-spot-success-submits-token-transfer-test
  (async done
    (let [store (management-store)
          submit-calls (atom [])
          refresh-calls (atom [])
          toast-calls (atom [])]
      (-> (effects/transfer-subaccount!
           {:store store
            :request {:sub-account-user subaccount-address :is-deposit false
                      :amount "4.63" :amount-display "4.63" :amount-units "4630000"
                      :amount-decimals 6 :account-kind :spot
                      :token "USDH:0xabc" :token-symbol "USDH"}
            :transfer-sub-account-spot! (fn [store* owner address is-deposit? token amount]
                                          (swap! submit-calls conj
                                                 [store* owner address is-deposit? token amount])
                                          (js/Promise.resolve {:status "ok"
                                                               :response {:type "default"}}))
            :load-subaccounts! (fn [opts]
                                 (swap! refresh-calls conj
                                        (select-keys opts [:force-refresh?]))
                                 (js/Promise.resolve :reloaded))
            :dispatch! (fn [_store _ctx _effects] nil)
            :show-toast! (fn [_store kind message]
                           (swap! toast-calls conj [kind message]))
            :runtime-error-message (fn [err] (str err))})
          (.then (fn [result]
                   (is (= :reloaded result))
                   (is (= [[store owner-address subaccount-address false "USDH:0xabc" "4.63"]]
                          @submit-calls))
                   (is (= [{:force-refresh? true}] @refresh-calls))
                   (is (= [[:success {:headline "Transfer submitted"
                                       :subline "4.63 USDH from Desk to Master Account"}]]
                          @toast-calls))
                   (is (nil? (get-in @store [:account-context :subaccounts :transferring-address])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected spot transfer success error: " err))
                    (done)))))))
