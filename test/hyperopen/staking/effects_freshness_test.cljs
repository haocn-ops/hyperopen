(ns hyperopen.staking.effects-freshness-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.staking.effects :as effects]))

(defn- controlled-staking-request
  [resolvers rejects]
  (fn [_address _opts]
    (js/Promise.
     (fn [resolve reject]
       (swap! resolvers conj resolve)
       (swap! rejects conj reject)))))

(defn- begin-delegator-summary-load
  [state]
  (-> state
      (assoc-in [:staking :loading :delegator-summary] true)
      (assoc-in [:staking :errors :delegator-summary] nil)))

(defn- apply-delegator-summary-success
  [state response]
  (-> state
      (assoc-in [:staking :delegator-summary] response)
      (assoc-in [:staking :loading :delegator-summary] false)
      (assoc-in [:staking :errors :delegator-summary] nil)))

(defn- apply-delegator-summary-error
  [state error]
  (-> state
      (assoc-in [:staking :loading :delegator-summary] false)
      (assoc-in [:staking :errors :delegator-summary] (str error))))

(deftest newer-same-account-staking-response-wins-over-an-older-response-test
  (async done
    (let [address "0x1111111111111111111111111111111111111111"
          resolvers (atom [])
          rejects (atom [])
          store (atom {:router {:path "/staking"}
                       :wallet {:address address}
                       :staking {:account-address address
                                 :loading {:delegator-summary false}
                                 :errors {:delegator-summary nil}}})
          request (controlled-staking-request resolvers rejects)
          first-request (effects/api-fetch-staking-delegator-summary!
                         {:store store
                          :address address
                          :request-staking-delegator-summary! request
                          :begin-staking-delegator-summary-load begin-delegator-summary-load
                          :apply-staking-delegator-summary-success apply-delegator-summary-success
                          :apply-staking-delegator-summary-error apply-delegator-summary-error})
          second-request (effects/api-fetch-staking-delegator-summary!
                          {:store store
                           :address address
                           :request-staking-delegator-summary! request
                           :begin-staking-delegator-summary-load begin-delegator-summary-load
                           :apply-staking-delegator-summary-success apply-delegator-summary-success
                           :apply-staking-delegator-summary-error apply-delegator-summary-error})]
      (is (= 2 (count @resolvers)))
      ((nth @resolvers 1) {:delegated "newer"})
      (-> second-request
          (.then (fn [_]
                   ((nth @resolvers 0) {:delegated "older"})
                   first-request))
          (.then (fn [_]
                   (is (= {:delegated "newer"}
                          (get-in @store [:staking :delegator-summary])))
                   (is (= false
                          (get-in @store [:staking :loading :delegator-summary])))
                   (is (nil? (get-in @store [:staking :errors :delegator-summary])))
                   (is (= address
                          (get-in @store [:staking :loaded-for :delegator-summary])))
                   (done)))
          (.catch (fn [error]
                    (is false (str "Unexpected error: " error))
                    (done)))))))

(deftest newer-same-account-staking-success-is-not-clobbered-by-an-older-error-test
  (async done
    (let [address "0x1111111111111111111111111111111111111111"
          resolvers (atom [])
          rejects (atom [])
          store (atom {:router {:path "/staking"}
                       :wallet {:address address}
                       :staking {:account-address address
                                 :loading {:delegator-summary false}
                                 :errors {:delegator-summary nil}}})
          request (controlled-staking-request resolvers rejects)
          first-request (effects/api-fetch-staking-delegator-summary!
                         {:store store
                          :address address
                          :request-staking-delegator-summary! request
                          :begin-staking-delegator-summary-load begin-delegator-summary-load
                          :apply-staking-delegator-summary-success apply-delegator-summary-success
                          :apply-staking-delegator-summary-error apply-delegator-summary-error})
          second-request (effects/api-fetch-staking-delegator-summary!
                          {:store store
                           :address address
                           :request-staking-delegator-summary! request
                           :begin-staking-delegator-summary-load begin-delegator-summary-load
                           :apply-staking-delegator-summary-success apply-delegator-summary-success
                           :apply-staking-delegator-summary-error apply-delegator-summary-error})]
      ((nth @resolvers 1) {:delegated "newer"})
      (-> second-request
          (.then (fn [_]
                   ((nth @rejects 0) (js/Error. "older request failed"))
                   (-> first-request
                       (.then (fn [_]
                                (is false "Older failed request must reject.")
                                (done)))
                       (.catch (fn [_]
                                 (is (= {:delegated "newer"}
                                        (get-in @store [:staking :delegator-summary])))
                                 (is (= false
                                        (get-in @store [:staking :loading :delegator-summary])))
                                 (is (nil? (get-in @store [:staking :errors :delegator-summary])))
                                 (is (= address
                                        (get-in @store [:staking :loaded-for :delegator-summary])))
                                 (done))))))
          (.catch (fn [error]
                    (is false (str "Unexpected newer request error: " error))
                    (done)))))))

(deftest explicit-old-address-staking-response-cannot-reopen-current-resource-loading-test
  (async done
    (let [address-a "0x1111111111111111111111111111111111111111"
          address-b "0x2222222222222222222222222222222222222222"
          resolvers (atom [])
          rejects (atom [])
          store (atom {:router {:path "/staking"}
                       :wallet {:address address-a}
                       :staking {:account-address address-a
                                 :loading {:delegator-summary false}
                                 :errors {:delegator-summary nil}}})
          pending (effects/api-fetch-staking-delegator-summary!
                   {:store store
                    :address address-a
                    :request-staking-delegator-summary!
                    (controlled-staking-request resolvers rejects)
                    :begin-staking-delegator-summary-load begin-delegator-summary-load
                    :apply-staking-delegator-summary-success apply-delegator-summary-success
                    :apply-staking-delegator-summary-error apply-delegator-summary-error})]
      (swap! store
             (fn [state]
               (-> state
                   (assoc-in [:wallet :address] address-b)
                   (assoc-in [:staking :account-address] address-b)
                   (assoc-in [:staking :delegator-summary] {:delegated "current"})
                   (assoc-in [:staking :loading :delegator-summary] false)
                   (assoc-in [:staking :errors :delegator-summary] nil)
                   (assoc-in [:staking :loaded-for :delegator-summary] address-b))))
      ((first @resolvers) {:delegated "old"})
      (-> pending
          (.then (fn [_]
                   (is (= {:delegated "current"}
                          (get-in @store [:staking :delegator-summary])))
                   (is (= false
                          (get-in @store [:staking :loading :delegator-summary])))
                   (is (nil? (get-in @store [:staking :errors :delegator-summary])))
                   (is (= address-b
                          (get-in @store [:staking :loaded-for :delegator-summary])))
                   (done)))
          (.catch (fn [error]
                    (is false (str "Unexpected error: " error))
                    (done)))))))

(deftest explicit-current-staking-fetch-address-is-accepted-when-it-matches-both-identities-test
  (async done
    (let [explicit "0x3333333333333333333333333333333333333333"
          store (atom {:router {:path "/staking"}
                       :wallet {:address explicit}
                       :staking {:account-address explicit}})
          calls (atom [])]
      (-> (effects/api-fetch-staking-delegator-summary!
           {:store store
            :address explicit
            :request-staking-delegator-summary! (fn [address _opts]
                                                  (swap! calls conj address)
                                                  (js/Promise.resolve {}))
            :begin-staking-delegator-summary-load identity
            :apply-staking-delegator-summary-success (fn [state _] state)
            :apply-staking-delegator-summary-error (fn [state _] state)})
          (.then (fn [_]
                   (is (= [explicit] @calls))
                   (done)))
          (.catch (fn [error]
                    (is false (str "Unexpected error: " error))
                    (done)))))))

(deftest queued-explicit-old-address-fetch-is-a-no-op-before-loading-or-network-test
  (async done
    (let [address-a "0x1111111111111111111111111111111111111111"
          address-b "0x2222222222222222222222222222222222222222"
          store (atom {:router {:path "/staking"}
                       :wallet {:address address-b}
                       :staking {:account-address address-b
                                 :delegator-summary {:delegated "current"}
                                 :loading {:delegator-summary false}
                                 :errors {:delegator-summary nil}
                                 :loaded-for {:delegator-summary address-b}}})
          state-before @store
          begin-calls (atom 0)
          request-calls (atom 0)]
      (-> (effects/api-fetch-staking-delegator-summary!
           {:store store
            :address address-a
            :request-staking-delegator-summary! (fn [_address _opts]
                                                  (swap! request-calls inc)
                                                  (js/Promise.resolve {:delegated "old"}))
            :begin-staking-delegator-summary-load (fn [state]
                                                    (swap! begin-calls inc)
                                                    (assoc-in state [:staking :loading :delegator-summary] true))
            :apply-staking-delegator-summary-success apply-delegator-summary-success
            :apply-staking-delegator-summary-error apply-delegator-summary-error})
          (.then (fn [result]
                   (is (nil? result))
                   (is (= 0 @begin-calls))
                   (is (= 0 @request-calls))
                   (is (= state-before @store))
                   (done)))
          (.catch (fn [error]
                    (is false (str "Unexpected error: " error))
                    (done)))))))
