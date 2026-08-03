(ns hyperopen.staking.effects-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.staking.effects :as effects]))

(deftest api-fetch-staking-validator-summaries-respects-route-gate-test
  (async done
    (let [store (atom {:router {:path "/trade"}})
          request-calls (atom 0)]
      (-> (effects/api-fetch-staking-validator-summaries!
           {:store store
            :request-staking-validator-summaries! (fn [_opts]
                                                    (swap! request-calls inc)
                                                    (js/Promise.resolve []))
            :begin-staking-validator-summaries-load identity
            :apply-staking-validator-summaries-success (fn [state _] state)
            :apply-staking-validator-summaries-error (fn [state _] state)})
          (.then (fn [result]
                   (is (nil? result))
                   (is (= 0 @request-calls))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " err))
                    (done)))))))

(deftest api-fetch-staking-validator-summaries-applies-begin-and-success-projections-test
  (async done
    (let [store (atom {:router {:path "/staking"}})]
      (-> (effects/api-fetch-staking-validator-summaries!
           {:store store
            :request-staking-validator-summaries! (fn [_opts]
                                                    (js/Promise.resolve [{:validator "0xabc"}]))
            :begin-staking-validator-summaries-load (fn [state]
                                                      (assoc-in state [:staking :loading :validator-summaries] true))
            :apply-staking-validator-summaries-success (fn [state rows]
                                                         (-> state
                                                             (assoc-in [:staking :validator-summaries] rows)
                                                             (assoc-in [:staking :loading :validator-summaries] false)))
            :apply-staking-validator-summaries-error (fn [state _err]
                                                       (assoc-in state [:staking :loading :validator-summaries] false))})
          (.then (fn [_]
                   (is (= [{:validator "0xabc"}]
                          (get-in @store [:staking :validator-summaries])))
                   (is (= false
                          (get-in @store [:staking :loading :validator-summaries])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " err))
                    (done)))))))

(deftest submitting-key-normalizes-kind-aliases-and-defaults-test
  (let [submitting-key @#'hyperopen.staking.effects/submitting-key]
    (is (= :deposit? (submitting-key :deposit)))
    (is (= :withdraw? (submitting-key :withdraw?)))
    (is (= :delegate? (submitting-key :delegate)))
    (is (= :undelegate? (submitting-key :undelegate?)))
    (is (= :deposit? (submitting-key :unexpected-kind)))
    (is (= :deposit? (submitting-key nil)))))

(defn- selected-subaccount-staking-store []
  (atom {:router {:path "/staking"}
         :wallet {:address "0x1111111111111111111111111111111111111111"}
         :staking {:account-address "0x1111111111111111111111111111111111111111"}
         :account-context {:spectate-mode {:active? false :address nil}
                           :subaccounts {:selected-address "0x2222222222222222222222222222222222222222"
                                         :rows [{:sub-account-user "0x2222222222222222222222222222222222222222"
                                                 :master "0x1111111111111111111111111111111111111111"}]}}}))

(deftest addressless-staking-fetches-use-native-staking-identity-test
  (async done
    (let [owner "0x1111111111111111111111111111111111111111"
          selected "0x2222222222222222222222222222222222222222"
          store (selected-subaccount-staking-store)
          calls (atom [])
          request (fn [effect-id]
                    (fn [address _opts]
                      (swap! calls conj [effect-id address])
                      (js/Promise.resolve {})))
          success (fn [state _response] state)
          failure (fn [state _error] state)
          promises [(effects/api-fetch-staking-delegator-summary!
                     {:store store
                      :request-staking-delegator-summary! (request :delegator-summary)
                      :begin-staking-delegator-summary-load identity
                      :apply-staking-delegator-summary-success success
                      :apply-staking-delegator-summary-error failure})
                    (effects/api-fetch-staking-delegations!
                     {:store store
                      :request-staking-delegations! (request :delegations)
                      :begin-staking-delegations-load identity
                      :apply-staking-delegations-success success
                      :apply-staking-delegations-error failure})
                    (effects/api-fetch-staking-rewards!
                     {:store store
                      :request-staking-delegator-rewards! (request :rewards)
                      :begin-staking-rewards-load identity
                      :apply-staking-rewards-success success
                      :apply-staking-rewards-error failure})
                    (effects/api-fetch-staking-history!
                     {:store store
                      :request-staking-delegator-history! (request :history)
                      :begin-staking-history-load identity
                      :apply-staking-history-success success
                      :apply-staking-history-error failure})
                    (effects/api-fetch-staking-spot-state!
                     {:store store
                      :request-spot-clearinghouse-state! (request :spot-state)
                      :begin-staking-spot-state-load identity
                      :apply-staking-spot-state-success success
                      :apply-staking-spot-state-error failure})]]
      (-> (js/Promise.all (clj->js promises))
          (.then (fn [_]
                   (is (= #{[:delegator-summary owner]
                            [:delegations owner]
                            [:rewards owner]
                            [:history owner]
                            [:spot-state owner]}
                          (set @calls)))
                   (is (not-any? #(= selected (second %)) @calls))
                   (done)))
          (.catch (fn [error]
                    (is false (str "Unexpected error: " error))
                    (done)))))))

(deftest current-staking-account-fetches-record-loaded-for-every-user-resource-test
  (async done
    (let [owner "0x1111111111111111111111111111111111111111"
          store (atom {:router {:path "/staking"}
                       :wallet {:address owner}
                       :staking {:account-address owner}})
          request (fn [_address _opts] (js/Promise.resolve {}))
          success (fn [state _response] state)
          failure (fn [state _error] state)
          promises [(effects/api-fetch-staking-delegator-summary!
                     {:store store
                      :address owner
                      :request-staking-delegator-summary! request
                      :begin-staking-delegator-summary-load identity
                      :apply-staking-delegator-summary-success success
                      :apply-staking-delegator-summary-error failure})
                    (effects/api-fetch-staking-delegations!
                     {:store store
                      :address owner
                      :request-staking-delegations! request
                      :begin-staking-delegations-load identity
                      :apply-staking-delegations-success success
                      :apply-staking-delegations-error failure})
                    (effects/api-fetch-staking-rewards!
                     {:store store
                      :address owner
                      :request-staking-delegator-rewards! request
                      :begin-staking-rewards-load identity
                      :apply-staking-rewards-success success
                      :apply-staking-rewards-error failure})
                    (effects/api-fetch-staking-history!
                     {:store store
                      :address owner
                      :request-staking-delegator-history! request
                      :begin-staking-history-load identity
                      :apply-staking-history-success success
                      :apply-staking-history-error failure})
                    (effects/api-fetch-staking-spot-state!
                     {:store store
                      :address owner
                      :request-spot-clearinghouse-state! request
                      :begin-staking-spot-state-load identity
                      :apply-staking-spot-state-success success
                      :apply-staking-spot-state-error failure})]]
      (-> (js/Promise.all (clj->js promises))
          (.then (fn [_]
                   (doseq [resource [:delegator-summary :delegations :rewards :history :spot-state]]
                     (is (= owner (get-in @store [:staking :loaded-for resource]))
                         (str "loaded-for " resource)))
                   (done)))
          (.catch (fn [error]
                    (is false (str "Unexpected error: " error))
                    (done)))))))

(deftest stale-staking-fetch-response-is-ignored-after-account-switch-test
  (async done
    (let [address-a "0x1111111111111111111111111111111111111111"
          address-b "0x2222222222222222222222222222222222222222"
          resolve-request! (atom nil)
          store (atom {:router {:path "/staking"}
                       :wallet {:address address-a}
                       :staking {:account-address address-a
                                 :delegator-summary {:delegated "before-switch"}}})
          request (fn [_address _opts]
                    (js/Promise.
                     (fn [resolve _reject]
                       (reset! resolve-request! resolve))))
          pending (effects/api-fetch-staking-delegator-summary!
                   {:store store
                    :address address-a
                    :request-staking-delegator-summary! request
                    :begin-staking-delegator-summary-load identity
                    :apply-staking-delegator-summary-success
                    (fn [state response]
                      (assoc-in state [:staking :delegator-summary] response))
                    :apply-staking-delegator-summary-error identity})]
      (swap! store (fn [state]
                     (-> state
                         (assoc-in [:wallet :address] address-b)
                         (assoc-in [:staking :account-address] address-b))))
      (@resolve-request! {:delegated "stale-response"})
      (-> pending
          (.then (fn [_]
                   (is (= {:delegated "before-switch"}
                          (get-in @store [:staking :delegator-summary])))
                   (is (nil? (get-in @store [:staking :loaded-for :delegator-summary])))
                   (done)))
          (.catch (fn [error]
                    (is false (str "Unexpected error: " error))
                    (done)))))))

(deftest api-submit-staking-deposit-success-clears-submitting-state-and-refreshes-test
  (async done
    (let [store (atom {:wallet {:address "0x1234567890abcdef1234567890abcdef12345678"}
                       :staking-ui {:submitting {:deposit? true}
                                    :deposit-amount "1.2"
                                    :form-error nil}})
          submit-calls (atom [])
          toasts (atom [])
          dispatches (atom [])]
      (-> (effects/api-submit-staking-deposit!
           {:store store
            :request {:kind :deposit
                      :action {:type "cDeposit"
                               :wei 120000000}}
            :submit-c-deposit! (fn [store* address action]
                                 (swap! submit-calls conj [store* address action])
                                 (js/Promise.resolve {:status "ok"}))
            :show-toast! (fn [_store kind message]
                           (swap! toasts conj [kind message]))
            :dispatch! (fn [store* _ctx effects]
                         (swap! dispatches conj [store* effects]))})
          (.then (fn [resp]
                   (is (= {:status "ok"} resp))
                   (is (= false (get-in @store [:staking-ui :submitting :deposit?])))
                   (is (= "" (get-in @store [:staking-ui :deposit-amount])))
                   (is (nil? (get-in @store [:staking-ui :form-error])))
                   (is (= [["0x1234567890abcdef1234567890abcdef12345678"
                            {:type "cDeposit" :wei 120000000}]]
                          (mapv (fn [[_store address action]]
                                  [address action])
                                @submit-calls)))
                   (is (= [[:success "Transfer to staking balance submitted."]]
                          @toasts))
                   (is (= [[[[:actions/load-staking]]]]
                          (mapv (fn [[_store effects]] [effects]) @dispatches)))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " err))
                    (done)))))))

(deftest api-submit-staking-delegate-without-wallet-sets-error-without-submitting-test
  (let [store (atom {:wallet {}
                     :staking-ui {:submitting {:delegate? true}
                                  :form-error nil}})
        submit-calls (atom 0)
        toasts (atom [])]
    (effects/api-submit-staking-delegate!
     {:store store
      :request {:kind :delegate
                :action {:type "tokenDelegate"
                         :validator "0x1234567890abcdef1234567890abcdef12345678"
                         :wei 100000000
                         :isUndelegate false}}
      :submit-token-delegate! (fn [_store _address _action]
                                (swap! submit-calls inc)
                                (js/Promise.resolve {:status "ok"}))
      :show-toast! (fn [_store kind message]
                     (swap! toasts conj [kind message]))})
    (is (= 0 @submit-calls))
    (is (= false (get-in @store [:staking-ui :submitting :delegate?])))
    (is (= "Connect your wallet before submitting stake."
           (get-in @store [:staking-ui :form-error])))
    (is (= [[:error "Connect your wallet before submitting stake."]]
           @toasts))))

(deftest api-submit-staking-undelegate-submits-canonical-owner-action-despite-stale-selected-subaccount-test
  (async done
    (let [owner "0x1111111111111111111111111111111111111111"
          stale-selected "0x2222222222222222222222222222222222222222"
          validator "0x3333333333333333333333333333333333333333"
          action {:type "tokenDelegate"
                  :validator validator
                  :wei 10100000000
                  :isUndelegate true}
          store (atom {:router {:path "/staking"}
                       :wallet {:address owner}
                       :account-context {:spectate-mode {:active? false
                                                         :address nil}
                                         :subaccounts {:selected-address stale-selected
                                                       :rows []}}
                       :staking {:account-address owner
                                 :loaded-for {:delegations owner}}
                       :staking-ui {:submitting {:undelegate? true}
                                    :undelegate-amount "101"
                                    :form-error nil}})
          submit-calls (atom [])
          toasts (atom [])
          dispatches (atom [])
          result (effects/api-submit-staking-undelegate!
                  {:store store
                   :request {:kind :undelegate
                             :action action}
                   :submit-token-delegate! (fn [store* address submitted-action]
                                             (swap! submit-calls conj [store* address submitted-action])
                                             (js/Promise.resolve {:status "ok"}))
                   :show-toast! (fn [_store kind message]
                                  (swap! toasts conj [kind message]))
                   :dispatch! (fn [_store _ctx dispatched-effects]
                                (swap! dispatches conj dispatched-effects))})]
      (if (instance? js/Promise result)
        (-> result
            (.then (fn [resp]
                     (is (= {:status "ok"} resp))
                     (is (= [[owner action]]
                            (mapv (fn [[_store address submitted-action]]
                                    [address submitted-action])
                                  @submit-calls)))
                     (is (= false (get-in @store [:staking-ui :submitting :undelegate?])))
                     (is (= "" (get-in @store [:staking-ui :undelegate-amount])))
                     (is (nil? (get-in @store [:staking-ui :form-error])))
                     (is (= [[:success "Unstake submitted."]]
                            @toasts))
                     (is (= [[[:actions/load-staking]]]
                            @dispatches))
                     (done)))
            (.catch (fn [err]
                      (is false (str "Unexpected error: " err))
                      (done))))
        (do
          (is (instance? js/Promise result)
              "A canonical undelegate request must enter the asynchronous submit lifecycle.")
          (is (= [[owner action]]
                 (mapv (fn [[_store address submitted-action]]
                         [address submitted-action])
                       @submit-calls)))
          (done))))))

(deftest api-submit-staking-undelegate-keeps-inspection-routes-read-only-test
  (let [owner "0x1111111111111111111111111111111111111111"
        trader "0x2222222222222222222222222222222222222222"
        action {:type "tokenDelegate"
                :validator "0x3333333333333333333333333333333333333333"
                :wei 10100000000
                :isUndelegate true}
        cases [{:label "spectate"
                :state {:wallet {:address owner}
                        :account-context {:spectate-mode {:active? true
                                                          :address trader}}
                        :staking-ui {:submitting {:undelegate? true}
                                     :undelegate-amount "101"
                                     :form-error nil}}
                :message "Spectate Mode is read-only. Stop Spectate Mode to place trades or move funds."}
               {:label "trader portfolio"
                :state {:router {:path (str "/portfolio/trader/" trader)}
                        :wallet {:address owner}
                        :account-context {:spectate-mode {:active? false
                                                          :address nil}}
                        :staking-ui {:submitting {:undelegate? true}
                                     :undelegate-amount "101"
                                     :form-error nil}}
                :message "Trader portfolio routes are read-only. Open your Portfolio to place trades or move funds."}]]
    (doseq [{:keys [label state message]} cases]
      (let [store (atom state)
            submit-calls (atom [])
            toasts (atom [])]
        (effects/api-submit-staking-undelegate!
         {:store store
          :request {:kind :undelegate
                    :action action}
          :submit-token-delegate! (fn [store* address submitted-action]
                                    (swap! submit-calls conj [store* address submitted-action])
                                    (js/Promise.resolve {:status "ok"}))
          :show-toast! (fn [_store kind toast-message]
                         (swap! toasts conj [kind toast-message]))})
        (is (= 0 (count @submit-calls))
            (str label " must not submit"))
        (is (= false (get-in @store [:staking-ui :submitting :undelegate?]))
            (str label " clears pending"))
        (is (= message (get-in @store [:staking-ui :form-error]))
            (str label " keeps its read-only message"))
        (is (= [[:error message]] @toasts)
            (str label " shows the read-only message"))))))
