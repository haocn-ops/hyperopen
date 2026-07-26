(ns hyperopen.funding.application.lifecycle-polling-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.funding.application.lifecycle-polling.test-support :as polling-support]
            [hyperopen.funding.application.lifecycle-polling :as lifecycle-polling]
            [hyperopen.funding.effects :as effects]
            [hyperopen.funding.test-support.effects :as effects-support]
            [hyperopen.test-support.async :as async-support]))

(deftest start-hyperunit-lifecycle-polling-noops-when-required-inputs-are-missing-test
  (let [request-calls (atom 0)
        install-calls (atom 0)]
    (lifecycle-polling/start-hyperunit-lifecycle-polling!
     (assoc (polling-support/base-poll-opts {:direction :deposit
                                             :request-hyperunit-operations! (fn [_opts]
                                                                              (swap! request-calls inc)
                                                                              (js/Promise.resolve {:operations []}))
                                             :install-lifecycle-poll-token! (fn [_poll-key _token]
                                                                              (swap! install-calls inc))})
            :wallet-address "  "
            :asset-key nil))
    (is (zero? @request-calls))
    (is (zero? @install-calls))))


(deftest start-hyperunit-lifecycle-polling-clears-token-without-request-when-modal-inactive-test
  (let [request-calls (atom 0)
        install-calls (atom [])
        clear-calls (atom [])]
    (lifecycle-polling/start-hyperunit-lifecycle-polling!
     (polling-support/base-poll-opts {:direction :deposit
                                      :request-hyperunit-operations! (fn [_opts]
                                                                       (swap! request-calls inc)
                                                                       (js/Promise.resolve {:operations []}))
                                      :install-lifecycle-poll-token! (fn [poll-key token]
                                                                       (swap! install-calls conj [poll-key token]))
                                      :clear-lifecycle-poll-token! (fn [poll-key token]
                                                                     (swap! clear-calls conj [poll-key token]))
                                      :modal-active-for-lifecycle? (fn [_store _direction _asset-key _protocol-address]
                                                                     false)}))
    (is (zero? @request-calls))
    (is (= 1 (count @install-calls)))
    (is (= @install-calls @clear-calls))))

(deftest start-hyperunit-lifecycle-polling-ignores-stale-successes-after-token-turns-inactive-test
  (async done
    (let [store (polling-support/base-poll-store :deposit)
          initial-lifecycle (get-in @store [:funding-ui :modal :hyperunit-lifecycle])
          token-active? (atom true)
          clear-calls (atom [])
          terminal-calls (atom [])
          scheduled-delays (atom [])]
      (lifecycle-polling/start-hyperunit-lifecycle-polling!
       (polling-support/base-poll-opts {:direction :deposit
                                        :store store
                                        :request-hyperunit-operations! (fn [_opts]
                                                                         (reset! token-active? false)
                                                                         (js/Promise.resolve
                                                                          {:operations [{:operation-id "op-stale"
                                                                                         :state-key :done
                                                                                         :status :completed}]}))
                                        :lifecycle-poll-token-active? (fn [_poll-key _token]
                                                                        @token-active?)
                                        :clear-lifecycle-poll-token! (fn [poll-key token]
                                                                       (swap! clear-calls conj [poll-key token]))
                                        :on-terminal-lifecycle! (fn [lifecycle]
                                                                  (swap! terminal-calls conj lifecycle))
                                        :set-timeout-fn (fn [_f delay-ms]
                                                          (swap! scheduled-delays conj delay-ms))}))
      (js/setTimeout
       (fn []
         (is (= initial-lifecycle
                (get-in @store [:funding-ui :modal :hyperunit-lifecycle])))
         (is (empty? @clear-calls))
         (is (empty? @terminal-calls))
         (is (empty? @scheduled-delays))
         (done))
       0))))

(deftest start-hyperunit-lifecycle-polling-terminal-success-updates-store-and-calls-terminal-callback-test
  (async done
    (let [store (polling-support/base-poll-store :deposit)
          terminal-calls (atom [])
          clear-calls (atom [])
          scheduled-delays (atom [])]
      (lifecycle-polling/start-hyperunit-lifecycle-polling!
       (polling-support/base-poll-opts {:direction :deposit
                                        :store store
                                        :request-hyperunit-operations! (fn [_opts]
                                                                         (js/Promise.resolve
                                                                          {:operations [{:operation-id "op-1"
                                                                                         :state-key :done
                                                                                         :status :completed}]}))
                                        :on-terminal-lifecycle! (fn [lifecycle]
                                                                  (swap! terminal-calls conj lifecycle))
                                        :clear-lifecycle-poll-token! (fn [poll-key token]
                                                                       (swap! clear-calls conj [poll-key token]))
                                        :set-timeout-fn (fn [_f delay-ms]
                                                          (swap! scheduled-delays conj delay-ms))}))
      (js/setTimeout
       (fn []
         (is (= {:operation-id "op-1"
                 :direction :deposit
                 :asset-key :btc
                 :state :done
                 :status :completed
                 :last-updated-ms 1700000000000}
                (get-in @store [:funding-ui :modal :hyperunit-lifecycle])))
         (is (= 1 (count @terminal-calls)))
         (is (= 1 (count @clear-calls)))
         (is (empty? @scheduled-delays))
         (done))
       20))))

(deftest start-hyperunit-lifecycle-polling-passes-addresses-to-select-operation-by-direction-test
  (async done
    (let [withdraw-opts (atom nil)
          deposit-opts (atom nil)]
      (lifecycle-polling/start-hyperunit-lifecycle-polling!
       (polling-support/base-poll-opts {:direction :withdraw
                        :request-hyperunit-operations! (fn [_opts]
                                                         (js/Promise.resolve {:operations [{:operation-id "op-1"}]}))
                        :select-operation (fn [_operations opts]
                                            (reset! withdraw-opts opts)
                                            nil)
                        :set-timeout-fn (fn [_f _delay-ms] nil)}))
      (lifecycle-polling/start-hyperunit-lifecycle-polling!
       (polling-support/base-poll-opts {:direction :deposit
                        :request-hyperunit-operations! (fn [_opts]
                                                         (js/Promise.resolve {:operations [{:operation-id "op-2"}]}))
                        :select-operation (fn [_operations opts]
                                            (reset! deposit-opts opts)
                                            nil)
                        :set-timeout-fn (fn [_f _delay-ms] nil)}))
      (js/setTimeout
       (fn []
         (is (= {:asset-key :btc
                 :protocol-address "bc1qprotocol"
                 :source-address "0xabc"
                 :destination-address "0xdestination"}
                @withdraw-opts))
         (is (= {:asset-key :btc
                 :protocol-address "bc1qprotocol"
                 :source-address nil
                 :destination-address "0xabc"}
                @deposit-opts))
         (done))
       0))))

(deftest start-hyperunit-lifecycle-polling-withdraw-pending-refreshes-queue-and-schedules-next-poll-test
  (async done
    (let [store (polling-support/base-poll-store :withdraw)
          refresh-calls (atom [])
          scheduled-delays (atom [])]
      (lifecycle-polling/start-hyperunit-lifecycle-polling!
       (polling-support/base-poll-opts {:direction :withdraw
                        :store store
                        :request-hyperunit-operations! (fn [_opts]
                                                         (js/Promise.resolve {:operations []}))
                        :fetch-hyperunit-withdrawal-queue! (fn [opts]
                                                             (swap! refresh-calls conj opts))
                        :set-timeout-fn (fn [_f delay-ms]
                                          (swap! scheduled-delays conj delay-ms))
                        :select-operation (fn [_operations _opts] nil)}))
      (js/setTimeout
       (fn []
         (is (= {:direction :withdraw
                 :asset-key :btc
                 :state :awaiting
                 :status :pending
                 :last-updated-ms 1700000000000}
                (get-in @store [:funding-ui :modal :hyperunit-lifecycle])))
         (is (= 1 (count @refresh-calls)))
         (is (= :btc (get-in (first @refresh-calls) [:expected-asset-key])))
         (is (= [2500] @scheduled-delays))
         (done))
       0))))

(deftest start-hyperunit-lifecycle-polling-error-path-preserves-previous-lifecycle-and-schedules-default-delay-test
  (async done
    (let [store (atom {:funding-ui {:modal (assoc (effects-support/seed-modal :deposit)
                                                  :hyperunit-lifecycle {:operation-id "prev-op"
                                                                        :state :pending
                                                                        :status :pending
                                                                        :retained true})}})
          scheduled-delays (atom [])]
      (lifecycle-polling/start-hyperunit-lifecycle-polling!
       (polling-support/base-poll-opts {:direction :deposit
                        :store store
                        :request-hyperunit-operations! (fn [_opts]
                                                         (js/Promise.reject (js/Error. "boom")))
                        :set-timeout-fn (fn [_f delay-ms]
                                          (swap! scheduled-delays conj delay-ms))
                        :default-poll-delay-ms 3333}))
      (js/setTimeout
       (fn []
         (is (= {:operation-id "prev-op"
                 :state :pending
                 :status :pending
                 :retained true
                 :direction :deposit
                 :asset-key :btc
                 :last-updated-ms 1700000000000
                 :error "boom"}
                (get-in @store [:funding-ui :modal :hyperunit-lifecycle])))
         (is (= [3333] @scheduled-delays))
         (done))
       0))))

(deftest start-hyperunit-lifecycle-polling-error-path-replaces-non-map-previous-lifecycle-test
  (async done
    (let [store (atom {:funding-ui {:modal (assoc (effects-support/seed-modal :deposit)
                                                  :hyperunit-lifecycle :stale-state)}})
          scheduled-delays (atom [])]
      (lifecycle-polling/start-hyperunit-lifecycle-polling!
       (polling-support/base-poll-opts {:direction :deposit
                        :store store
                        :request-hyperunit-operations! (fn [_opts]
                                                         (js/Promise.reject (js/Error. "boom")))
                        :set-timeout-fn (fn [_f delay-ms]
                                          (swap! scheduled-delays conj delay-ms))
                        :default-poll-delay-ms 3333}))
      (js/setTimeout
       (fn []
         (is (= {:direction :deposit
                 :asset-key :btc
                 :state :awaiting
                 :status :pending
                 :last-updated-ms 1700000000000
                 :error "boom"}
                (get-in @store [:funding-ui :modal :hyperunit-lifecycle])))
         (is (= [3333] @scheduled-delays))
         (done))
       0))))

(deftest api-submit-funding-deposit-hyperunit-address-terminal-lifecycle-refreshes-user-data-test
  (async done
    (let [wallet-address "0xabc"
          deposit-address "bc1qexamplexyz"
          store (atom {:wallet {:address wallet-address}
                       :funding-ui {:modal (assoc (effects-support/seed-modal :deposit)
                                                  :deposit-step :amount-entry
                                                  :deposit-selected-asset-key :btc
                                                  :deposit-generated-address nil
                                                  :deposit-generated-signatures nil
                                                  :deposit-generated-asset-key nil)}})
          dispatches (atom [])
          timeout-calls (atom 0)]
      (-> (effects/api-submit-funding-deposit!
           {:store store
            :request {:action {:type "hyperunitGenerateDepositAddress"
                               :asset "btc"
                               :fromChain "bitcoin"
                               :network "Bitcoin"}}
            :submit-hyperunit-address-request! (fn [_store _address _action]
                                                 (js/Promise.resolve {:status "ok"
                                                                      :keep-modal-open? true
                                                                      :asset "btc"
                                                                      :deposit-address deposit-address
                                                                      :deposit-signatures [{:r "0x1"}]}))
            :request-hyperunit-operations! (fn [_opts]
                                             (js/Promise.resolve
                                              {:operations [{:operation-id "op_d1"
                                                             :asset "btc"
                                                             :protocol-address deposit-address
                                                             :destination-address wallet-address
                                                             :state-key :done
                                                             :status "completed"}]}))
            :set-timeout-fn (fn [_f _delay-ms]
                              (swap! timeout-calls inc)
                              :timer-id)
            :dispatch! (effects-support/capture-dispatch! dispatches)})
          (.then (fn [_resp]
                   (js/setTimeout
                    (fn []
                      (is (= [[[[:actions/load-user-data wallet-address]]]]
                             (mapv (fn [[_store event]] [event]) @dispatches)))
                      (is (= 0 @timeout-calls))
                      (is (= :done
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :state])))
                      (done))
                    20)))
          (.catch (async-support/unexpected-error done))))))

(deftest api-submit-funding-deposit-hyperunit-address-polls-and-updates-lifecycle-test
  (async done
    (let [wallet-address "0x1234567890abcdef1234567890abcdef12345678"
          store (atom {:wallet {:address wallet-address}
                       :funding-ui {:modal (assoc (effects-support/seed-modal :deposit)
                                                  :deposit-step :amount-entry
                                                  :deposit-selected-asset-key :btc
                                                  :deposit-generated-address nil
                                                  :deposit-generated-signatures nil
                                                  :deposit-generated-asset-key nil)}})
          operation-calls (atom [])
          timeout-calls (atom 0)]
      (-> (effects/api-submit-funding-deposit!
           {:store store
            :request {:action {:type "hyperunitGenerateDepositAddress"
                               :asset "btc"
                               :fromChain "bitcoin"
                               :network "Bitcoin"}}
            :submit-hyperunit-address-request! (fn [_store _address _action]
                                                 (js/Promise.resolve {:status "ok"
                                                                      :keep-modal-open? true
                                                                      :asset "btc"
                                                                      :deposit-address "bc1qexamplexyz"
                                                                      :deposit-signatures [{:r "0x1"}]}))
            :request-hyperunit-operations! (fn [opts]
                                             (swap! operation-calls conj opts)
                                             (js/Promise.resolve
                                              {:operations [{:operation-id "op_123"
                                                             :asset "btc"
                                                             :protocol-address "bc1qexamplexyz"
                                                             :destination-address wallet-address
                                                             :state-key :done
                                                             :status "completed"
                                                             :source-tx-confirmations 6
                                                             :destination-tx-hash "0xabc"}]}))
            :set-timeout-fn (fn [_f _delay-ms]
                              (swap! timeout-calls inc)
                              :timer-id)})
          (.then (fn [_resp]
                   (js/setTimeout
                    (fn []
                      (is (= [{:base-url "https://api.hyperunit.xyz"
                               :base-urls ["https://api.hyperunit.xyz"]
                               :address wallet-address}]
                             @operation-calls))
                      (is (= :deposit
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :direction])))
                      (is (= :btc
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :asset-key])))
                      (is (= "op_123"
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :operation-id])))
                      (is (= :done
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :state])))
                      (is (= :completed
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :status])))
                      (is (= 0 @timeout-calls))
                      (done))
                    0)))
          (.catch (async-support/unexpected-error done))))))

(deftest api-submit-funding-deposit-hyperunit-address-schedules-next-poll-from-state-next-attempt-test
  (async done
    (let [wallet-address "0x1234567890abcdef1234567890abcdef12345678"
          now-ms 1700000000000
          state-next-at-ms (+ now-ms 4500)
          state-next-at-text (.toISOString (js/Date. state-next-at-ms))
          store (atom {:wallet {:address wallet-address}
                       :funding-ui {:modal (assoc (effects-support/seed-modal :deposit)
                                                  :deposit-step :amount-entry
                                                  :deposit-selected-asset-key :btc
                                                  :deposit-generated-address nil
                                                  :deposit-generated-signatures nil
                                                  :deposit-generated-asset-key nil)}})
          scheduled-delays (atom [])]
      (-> (effects/api-submit-funding-deposit!
           {:store store
            :request {:action {:type "hyperunitGenerateDepositAddress"
                               :asset "btc"
                               :fromChain "bitcoin"
                               :network "Bitcoin"}}
            :submit-hyperunit-address-request! (fn [_store _address _action]
                                                 (js/Promise.resolve {:status "ok"
                                                                      :keep-modal-open? true
                                                                      :asset "btc"
                                                                      :deposit-address "bc1qexamplexyz"
                                                                      :deposit-signatures [{:r "0x1"}]}))
            :request-hyperunit-operations! (fn [_opts]
                                             (js/Promise.resolve
                                              {:operations [{:operation-id "op_124"
                                                             :asset "btc"
                                                             :protocol-address "bc1qexamplexyz"
                                                             :destination-address wallet-address
                                                             :state-key :wait-for-src-tx-finalization
                                                             :status "pending"
                                                             :state-next-attempt-at state-next-at-text}]}))
            :set-timeout-fn (fn [_f delay-ms]
                              (swap! scheduled-delays conj delay-ms)
                              :timer-id)
            :now-ms-fn (fn [] now-ms)})
          (.then (fn [_resp]
                   (js/setTimeout
                    (fn []
                      (is (= [4500] @scheduled-delays))
                      (is (= :wait-for-src-tx-finalization
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :state])))
                      (is (= :pending
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :status])))
                      (done))
                    0)))
          (.catch (async-support/unexpected-error done))))))
