(ns hyperopen.subaccounts.owner-mode-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.account.context :as account-context]
            [hyperopen.subaccounts.actions :as actions]
            [hyperopen.subaccounts.effects :as effects]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def previous-owner-address
  "0x2222222222222222222222222222222222222222")

(def subaccount-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(def spectate-address
  "0x7777777777777777777777777777777777777777")

(defn- path-value
  [effects path]
  (let [[_ path-values] (first effects)
        missing (js-obj)
        value (reduce (fn [result [candidate-path candidate-value]]
                        (if (= path candidate-path)
                          (reduced candidate-value)
                          result))
                      missing
                      path-values)]
    (when-not (identical? missing value)
      value)))

(defn- apply-save-many
  [state effects]
  (let [[effect-id path-values] (first effects)]
    (assert (= :effects/save-many effect-id))
    (reduce (fn [state* [path value]]
              (assoc-in state* path value))
            state
            path-values)))

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

(deftest load-subaccounts-route-seeds-owner-scoped-mode-record-test
  (let [owner-effects (actions/load-subaccounts-route
                       {:wallet {:address owner-address}}
                       "/subAccounts")
        spectate-effects (actions/load-subaccounts-route
                          {:wallet {:address owner-address}
                           :account-context
                           {:spectate-mode {:active? true
                                             :address spectate-address}}}
                          "/subAccounts")
        missing-owner-effects (actions/load-subaccounts-route
                               {:wallet {:address nil}}
                               "/subAccounts")]
    (is (= {:owner owner-address
            :mode nil}
           (path-value owner-effects [:account-context :subaccounts :owner-mode])))
    (is (= {:owner spectate-address
            :mode nil}
           (path-value spectate-effects [:account-context :subaccounts :owner-mode])))
    (is (nil? (path-value missing-owner-effects
                          [:account-context :subaccounts :owner-mode])))))

(deftest load-subaccounts-route-invalidates-owner-mode-authority-when-owner-changes-test
  (let [state {:wallet {:address owner-address}
               :account {:mode :classic}
               :account-context
               {:subaccounts {:status :loaded
                              :loaded-for-owner previous-owner-address
                              :owner-mode :unified
                              :rows [{:name "Stale"
                                      :master previous-owner-address
                                      :sub-account-user subaccount-address}]}}}
        effects (actions/load-subaccounts-route state "/subAccounts")
        projected-state (apply-save-many state effects)]
    (is (= :loading
           (path-value effects [:account-context :subaccounts :status])))
    (is (= owner-address
           (path-value effects [:account-context :subaccounts :loaded-for-owner])))
    (is (= []
           (path-value effects [:account-context :subaccounts :rows])))
    (is (= {:owner owner-address
            :mode nil}
           (path-value effects [:account-context :subaccounts :owner-mode])))
    (is (= [:effects/api-load-subaccounts] (second effects)))
    (is (false? (account-context/subaccounts-owner-unified? projected-state)))))

(deftest refresh-subaccounts-preserves-current-owner-mode-record-test
  (let [state {:wallet {:address owner-address}
               :account-context
               {:subaccounts {:status :loaded
                              :loaded-for-owner owner-address
                              :owner-mode {:owner owner-address
                                           :mode :unified}
                              :rows [{:name "Desk"
                                      :master owner-address
                                      :sub-account-user subaccount-address}]}}}
        effects (actions/refresh-subaccounts state)]
    (is (= {:owner owner-address
            :mode :unified}
           (path-value effects [:account-context :subaccounts :owner-mode])))
    (is (= [:effects/api-refresh-subaccounts] (second effects)))))

(deftest transfer-deposit-uses-send-asset-from-owner-mode-even-when-active-account-classic-test
  (async done
    (let [store (management-store)
          l1-calls (atom [])
          send-asset-calls (atom [])]
      (swap! store assoc-in [:account :mode] :classic)
      (swap! store assoc-in [:account-context :subaccounts :owner-mode]
             {:owner owner-address :mode :unified})
      (-> (effects/transfer-subaccount!
           {:store store
            :request {:sub-account-user subaccount-address
                      :is-deposit true
                      :usd 1230000
                      :amount "1.23"}
            :transfer-sub-account! (fn [& args]
                                     (swap! l1-calls conj args)
                                     (js/Promise.resolve {:status "err"}))
            :submit-send-asset! (fn [store* owner action]
                                  (swap! send-asset-calls conj [store* owner action])
                                  (js/Promise.resolve {:status "ok"
                                                       :response {:type "default"}}))
            :load-subaccounts! (fn [_opts] (js/Promise.resolve :reloaded))
            :dispatch! (fn [_store _ctx _effects] nil)
            :runtime-error-message (fn [err] (str err))})
          (.then (fn [_result]
                   (is (= [] @l1-calls))
                   (is (= [[store owner-address
                            {:type "sendAsset"
                             :destination subaccount-address
                             :sourceDex "spot"
                             :destinationDex "spot"
                             :token "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"
                             :amount "1.23"
                             :fromSubAccount ""}]]
                          @send-asset-calls))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected mixed-mode deposit error: " err))
                    (done)))))))

(deftest transfer-uses-legacy-path-when-owner-mode-classic-even-if-active-account-unified-test
  (async done
    (let [store (management-store)
          l1-calls (atom [])
          send-asset-calls (atom [])]
      (swap! store assoc-in [:account :mode] :unified)
      (swap! store assoc-in [:account-context :subaccounts :owner-mode]
             {:owner owner-address :mode :classic})
      (-> (effects/transfer-subaccount!
           {:store store
            :request {:sub-account-user subaccount-address
                      :is-deposit true
                      :usd 1230000
                      :amount "1.23"}
            :transfer-sub-account! (fn [store* owner address is-deposit? usd]
                                     (swap! l1-calls conj [store* owner address is-deposit? usd])
                                     (js/Promise.resolve {:status "ok"
                                                          :response {:type "default"}}))
            :submit-send-asset! (fn [& args]
                                  (swap! send-asset-calls conj args)
                                  (js/Promise.resolve {:status "ok"}))
            :load-subaccounts! (fn [_opts] (js/Promise.resolve :reloaded))
            :dispatch! (fn [_store _ctx _effects] nil)
            :runtime-error-message (fn [err] (str err))})
          (.then (fn [_result]
                   (is (= [] @send-asset-calls))
                   (is (= [[store owner-address subaccount-address true 1230000]]
                          @l1-calls))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected classic-master transfer error: " err))
                    (done)))))))

(deftest transfer-does-not-use-send-asset-while-owner-mode-is-pending-test
  (async done
    (let [store (management-store)
          l1-calls (atom [])
          send-asset-calls (atom [])]
      (swap! store assoc-in [:account :mode] :unified)
      (swap! store assoc-in [:account-context :subaccounts :owner-mode]
             {:owner owner-address :mode nil})
      (-> (effects/transfer-subaccount!
           {:store store
            :request {:sub-account-user subaccount-address
                      :is-deposit true
                      :usd 1230000
                      :amount "1.23"}
            :transfer-sub-account! (fn [store* owner address is-deposit? usd]
                                     (swap! l1-calls conj [store* owner address is-deposit? usd])
                                     (js/Promise.resolve {:status "ok"
                                                          :response {:type "default"}}))
            :submit-send-asset! (fn [& args]
                                  (swap! send-asset-calls conj args)
                                  (js/Promise.resolve {:status "ok"}))
            :load-subaccounts! (fn [_opts] (js/Promise.resolve :reloaded))
            :dispatch! (fn [_store _ctx _effects] nil)
            :runtime-error-message (fn [err] (str err))})
          (.then (fn [_result]
                   (is (= [] @send-asset-calls))
                   (is (= [[store owner-address subaccount-address true 1230000]]
                          @l1-calls))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected pending-owner-mode transfer error: " err))
                    (done)))))))

(deftest transfer-subaccount-ignores-stale-legacy-owner-mode-test
  (async done
    (let [store (management-store)
          l1-calls (atom [])
          send-asset-calls (atom [])]
      (swap! store assoc-in [:account :mode] :classic)
      (swap! store assoc-in [:account-context :subaccounts :owner-mode] :unified)
      (-> (effects/transfer-subaccount!
           {:store store
            :request {:sub-account-user subaccount-address
                      :is-deposit true
                      :usd 1230000
                      :amount "1.23"}
            :transfer-sub-account! (fn [store* owner address is-deposit? usd]
                                     (swap! l1-calls conj [store* owner address is-deposit? usd])
                                     (js/Promise.resolve {:status "ok"
                                                          :response {:type "default"}}))
            :submit-send-asset! (fn [& args]
                                  (swap! send-asset-calls conj args)
                                  (js/Promise.resolve {:status "ok"}))
            :load-subaccounts! (fn [_opts] (js/Promise.resolve :reloaded))
            :dispatch! (fn [_store _ctx _effects] nil)
            :runtime-error-message (fn [err] (str err))})
          (.then (fn [_result]
                   (is (= [] @send-asset-calls))
                   (is (= [[store owner-address subaccount-address true 1230000]]
                          @l1-calls))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected stale-owner-mode transfer error: " err))
                    (done)))))))

(deftest load-subaccounts-records-owner-mode-from-abstraction-test
  (async done
    (let [owner-mode-calls (atom [])
          store (atom {:router {:path "/subAccounts"}
                       :wallet {:address owner-address}
                       :account-context {:subaccounts {:status :loading}}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [_address _opts]
                                     (js/Promise.resolve
                                      [{:name "Desk"
                                        :master owner-address
                                        :sub-account-user subaccount-address}]))
            :request-owner-mode! (fn [owner force-refresh?]
                                   (swap! owner-mode-calls conj [owner force-refresh?])
                                   (js/Promise.resolve :unified))
            :local-storage-get (fn [_] nil)})
          (.then (fn [_result]
                   (js/setTimeout
                    (fn []
                      (is (= [[owner-address false]] @owner-mode-calls))
                      (is (= {:owner owner-address :mode :unified}
                             (get-in @store [:account-context :subaccounts :owner-mode])))
                      (done))
                    0)))
          (.catch (fn [err]
                    (is false (str "Unexpected owner-mode load error: " err))
                    (done)))))))

(deftest load-subaccounts-records-spectated-owner-mode-from-abstraction-test
  (async done
    (let [owner-mode-calls (atom [])
          store (atom {:router {:path "/subAccounts"}
                       :wallet {:address owner-address}
                       :account-context
                       {:spectate-mode {:active? true
                                        :address spectate-address}
                        :subaccounts {:status :loading}}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [_address _opts]
                                     (js/Promise.resolve
                                      [{:name "Spectated Desk"
                                        :master spectate-address
                                        :sub-account-user subaccount-address}]))
            :request-owner-mode! (fn [owner force-refresh?]
                                   (swap! owner-mode-calls conj [owner force-refresh?])
                                   (js/Promise.resolve :unified))
            :local-storage-get (fn [_] nil)})
          (.then (fn [_result]
                   (js/setTimeout
                    (fn []
                      (is (= [[spectate-address false]] @owner-mode-calls))
                      (is (= {:owner spectate-address :mode :unified}
                             (get-in @store [:account-context :subaccounts :owner-mode])))
                      (done))
                    0)))
          (.catch (fn [err]
                    (is false (str "Unexpected spectated owner-mode load error: " err))
                    (done)))))))

(deftest load-subaccounts-ignores-owner-mode-response-after-viewed-owner-changes-test
  (async done
    (let [resolve-owner-mode! (atom nil)
          owner-mode-promise (js/Promise.
                              (fn [resolve _reject]
                                (reset! resolve-owner-mode! resolve)))
          store (atom {:router {:path "/subAccounts"}
                       :wallet {:address owner-address}
                       :account-context {:subaccounts {:status :loading}}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [_address _opts]
                                     (js/Promise.resolve
                                      [{:name "Desk"
                                        :master owner-address
                                        :sub-account-user subaccount-address}]))
            :request-owner-mode! (fn [_owner _force-refresh?]
                                   owner-mode-promise)
            :local-storage-get (fn [_] nil)})
          (.then (fn [_result]
                   (is (not= {:owner owner-address :mode :unified}
                             (get-in @store [:account-context :subaccounts :owner-mode])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected viewed-owner-change load error: " err))
                    (done))))
      (js/setTimeout
       (fn []
         (swap! store assoc-in
                [:account-context :spectate-mode]
                {:active? true
                 :address spectate-address})
         (@resolve-owner-mode! :unified))
       0))))
