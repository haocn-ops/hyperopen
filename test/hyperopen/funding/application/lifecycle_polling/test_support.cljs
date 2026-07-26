(ns hyperopen.funding.application.lifecycle-polling.test-support
  (:require [hyperopen.funding.test-support.effects :as effects-support]))

(defn base-poll-store
  [mode]
  (atom {:funding-ui {:modal (effects-support/seed-modal mode)}}))

(defn base-poll-opts
  [{:keys [direction
           store
           request-hyperunit-operations!
           request-hyperunit-withdrawal-queue!
           set-timeout-fn
           now-ms-fn
           on-terminal-lifecycle!
           install-lifecycle-poll-token!
           clear-lifecycle-poll-token!
           lifecycle-poll-token-active?
           modal-active-for-lifecycle?
           select-operation
           operation->lifecycle
           awaiting-lifecycle
           lifecycle-next-delay-ms
           hyperunit-lifecycle-terminal?
           fetch-hyperunit-withdrawal-queue!
           default-poll-delay-ms]}]
  {:store (or store (base-poll-store direction))
   :direction direction
   :wallet-address "0xabc"
   :asset-key :btc
   :protocol-address "bc1qprotocol"
   :destination-address "0xdestination"
   :base-url "https://api.hyperunit.xyz"
   :base-urls ["https://api.hyperunit.xyz"]
   :request-hyperunit-operations! (or request-hyperunit-operations!
                                      (fn [_opts]
                                        (js/Promise.resolve {:operations []})))
   :request-hyperunit-withdrawal-queue! (or request-hyperunit-withdrawal-queue!
                                            (fn [_opts]
                                              (js/Promise.resolve {:queue []})))
   :set-timeout-fn (or set-timeout-fn
                       (fn [_f _delay-ms]
                         :timer-id))
   :now-ms-fn (or now-ms-fn
                  (fn []
                    1700000000000))
   :runtime-error-message effects-support/fallback-runtime-error-message
   :on-terminal-lifecycle! on-terminal-lifecycle!
   :lifecycle-poll-key-fn (fn [_store direction* asset-key*]
                            [direction* asset-key*])
   :install-lifecycle-poll-token! (or install-lifecycle-poll-token!
                                      (fn [_poll-key _token]
                                        nil))
   :clear-lifecycle-poll-token! (or clear-lifecycle-poll-token!
                                    (fn [_poll-key _token]
                                      nil))
   :lifecycle-poll-token-active? (or lifecycle-poll-token-active?
                                     (fn [_poll-key _token]
                                       true))
   :modal-active-for-lifecycle? (or modal-active-for-lifecycle?
                                    (fn [_store _direction _asset-key _protocol-address]
                                      true))
   :normalize-hyperunit-lifecycle identity
   :select-operation (or select-operation
                         (fn [operations _opts]
                           (first operations)))
   :operation->lifecycle (or operation->lifecycle
                             (fn [operation direction* asset-key* now-ms]
                               {:operation-id (:operation-id operation)
                                :direction direction*
                                :asset-key asset-key*
                                :state (:state-key operation)
                                :status (:status operation)
                                :last-updated-ms now-ms}))
   :awaiting-lifecycle (or awaiting-lifecycle
                           (fn [direction* asset-key* now-ms]
                             {:direction direction*
                              :asset-key asset-key*
                              :state :awaiting
                              :status :pending
                              :last-updated-ms now-ms}))
   :lifecycle-next-delay-ms (or lifecycle-next-delay-ms
                                (fn [_now-ms _lifecycle]
                                  2500))
   :hyperunit-lifecycle-terminal? (or hyperunit-lifecycle-terminal?
                                      (fn [lifecycle]
                                        (= :done (:state lifecycle))))
   :fetch-hyperunit-withdrawal-queue! (or fetch-hyperunit-withdrawal-queue!
                                          (fn [_opts]
                                            nil))
   :non-blank-text (fn [value]
                     (when-let [text (some-> value str .trim)]
                       (when (seq text)
                         text)))
   :default-poll-delay-ms (or default-poll-delay-ms 3000)})
