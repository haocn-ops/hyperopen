(ns hyperopen.funding.application.lifecycle-polling.internal-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.funding.application.lifecycle-polling :as lifecycle-polling]
            [hyperopen.funding.test-support.effects :as effects-support]))

(deftest resolve-poll-runtime-selects-callables-and-defaults-test
  (let [resolve-poll-runtime @#'hyperopen.funding.application.lifecycle-polling/resolve-poll-runtime
        request-ops! (fn [_opts] :ops)
        request-queue! (fn [_opts] :queue)
        timeout! (fn [_f _delay-ms] :timer)
        now-ms!* (fn [] 42)
        terminal-callback! (fn [_lifecycle] :terminal)
        provided (resolve-poll-runtime {:request-hyperunit-operations! request-ops!
                                        :request-hyperunit-withdrawal-queue! request-queue!
                                        :set-timeout-fn timeout!
                                        :now-ms-fn now-ms!*
                                        :on-terminal-lifecycle! terminal-callback!})
        defaulted (resolve-poll-runtime {:request-hyperunit-operations! :not-a-fn
                                         :request-hyperunit-withdrawal-queue! nil
                                         :set-timeout-fn nil
                                         :now-ms-fn nil
                                         :on-terminal-lifecycle! "nope"})]
    (is (identical? request-ops! (:request-ops! provided)))
    (is (identical? request-queue! (:request-queue! provided)))
    (is (identical? timeout! (:timeout! provided)))
    (is (identical? now-ms!* (:now-ms!* provided)))
    (is (identical? terminal-callback! (:terminal-callback! provided)))
    (is (nil? (:request-ops! defaulted)))
    (is (nil? (:request-queue! defaulted)))
    (is (nil? (:terminal-callback! defaulted)))
    (is (fn? (:timeout! defaulted)))
    (is (fn? (:now-ms!* defaulted)))
    (is (number? ((:now-ms!* defaulted))))))

(deftest update-active-lifecycle-respects-should-continue-test
  (let [update-active-lifecycle! @#'hyperopen.funding.application.lifecycle-polling/update-active-lifecycle!
        store (atom {:funding-ui {:modal {:hyperunit-lifecycle {:state :original}}}})
        normalize-hyperunit-lifecycle (fn [lifecycle]
                                        (assoc lifecycle :normalized true))]
    (update-active-lifecycle! store
                              normalize-hyperunit-lifecycle
                              (fn [] false)
                              {:state :pending})
    (is (= {:state :original}
           (get-in @store [:funding-ui :modal :hyperunit-lifecycle])))
    (update-active-lifecycle! store
                              normalize-hyperunit-lifecycle
                              (fn [] true)
                              {:state :pending})
    (is (= {:state :pending
            :normalized true}
           (get-in @store [:funding-ui :modal :hyperunit-lifecycle])))))

(deftest refresh-active-withdraw-queue-passes-transition-loading-false-test
  (let [refresh-active-withdraw-queue! @#'hyperopen.funding.application.lifecycle-polling/refresh-active-withdraw-queue!
        calls (atom [])]
    (refresh-active-withdraw-queue!
     {:direction :withdraw
      :request-queue! (fn [_opts] (js/Promise.resolve {:queue []}))
      :should-continue? (fn [] true)
      :fetch-hyperunit-withdrawal-queue! (fn [opts]
                                           (swap! calls conj opts)
                                           nil)
      :store (atom {})
      :base-url "https://api.hyperunit.xyz"
      :base-urls ["https://api.hyperunit.xyz"]
      :now-ms!* (fn [] 1700000000000)
      :runtime-error-message effects-support/fallback-runtime-error-message
      :asset-key :btc})
    (is (= 1 (count @calls)))
    (let [opts (first @calls)]
      (is (= :btc (:expected-asset-key opts)))
      (is (= false (:transition-loading? opts))))))

(deftest refresh-active-withdraw-queue-noops-for-non-withdraw-or-continued-false-test
  (let [refresh-active-withdraw-queue! @#'hyperopen.funding.application.lifecycle-polling/refresh-active-withdraw-queue!
        calls (atom [])]
    (refresh-active-withdraw-queue!
     {:direction :deposit
      :request-queue! (fn [_opts]
                        (swap! calls conj :called)
                        (js/Promise.resolve {:queue []}))
      :should-continue? (fn [] true)
      :fetch-hyperunit-withdrawal-queue! (fn [opts]
                                           (swap! calls conj opts))
      :store (atom {})
      :base-url "https://api.hyperunit.xyz"
      :base-urls ["https://api.hyperunit.xyz"]
      :now-ms!* (fn [] 1700000000000)
      :runtime-error-message effects-support/fallback-runtime-error-message
      :asset-key :btc})
    (refresh-active-withdraw-queue!
     {:direction :withdraw
      :request-queue! nil
      :should-continue? (fn [] true)
      :fetch-hyperunit-withdrawal-queue! (fn [opts]
                                           (swap! calls conj opts))
      :store (atom {})
      :base-url "https://api.hyperunit.xyz"
      :base-urls ["https://api.hyperunit.xyz"]
      :now-ms!* (fn [] 1700000000000)
      :runtime-error-message effects-support/fallback-runtime-error-message
      :asset-key :btc})
    (refresh-active-withdraw-queue!
     {:direction :withdraw
      :request-queue! (fn [_opts]
                        (swap! calls conj :called)
                        (js/Promise.resolve {:queue []}))
      :should-continue? (fn [] false)
      :fetch-hyperunit-withdrawal-queue! (fn [opts]
                                           (swap! calls conj opts))
      :store (atom {})
      :base-url "https://api.hyperunit.xyz"
      :base-urls ["https://api.hyperunit.xyz"]
      :now-ms!* (fn [] 1700000000000)
      :runtime-error-message effects-support/fallback-runtime-error-message
      :asset-key :btc})
    (is (empty? @calls))))

(deftest select-polled-operation-chooses-source-and-destination-addresses-test
  (let [select-polled-operation @#'hyperopen.funding.application.lifecycle-polling/select-polled-operation
        seen (atom [])
        select-operation (fn [operations opts]
                           (swap! seen conj [operations opts])
                           :selected)
        response {:operations [{:operation-id "op-1"}]}]
    (is (= :selected
           (select-polled-operation {:select-operation select-operation
                                     :direction :withdraw
                                     :asset-key :btc
                                     :protocol-address "bc1qprotocol"
                                     :destination-address "0xdest"
                                     :wallet-address "0xwallet"}
                                    response)))
    (is (= :selected
           (select-polled-operation {:select-operation select-operation
                                     :direction :deposit
                                     :asset-key :btc
                                     :protocol-address "bc1qprotocol"
                                     :destination-address "0xdest"
                                     :wallet-address "0xwallet"}
                                    response)))
    (let [[_ops withdraw-opts] (first @seen)
          [_ops2 deposit-opts] (second @seen)]
      (is (= "0xwallet" (:source-address withdraw-opts)))
      (is (= "0xdest" (:destination-address withdraw-opts)))
      (is (nil? (:source-address deposit-opts)))
      (is (= "0xwallet" (:destination-address deposit-opts))))))

(deftest error-poll-lifecycle-preserves-previous-fields-and-sets-error-test
  (let [error-poll-lifecycle @#'hyperopen.funding.application.lifecycle-polling/error-poll-lifecycle
        store (atom {:funding-ui {:modal {:hyperunit-lifecycle {:operation-id "prev-op"
                                                                :state :pending
                                                                :status :pending
                                                                :retained true}}}})
        awaiting-lifecycle (fn [direction* asset-key* now-ms]
                             {:direction direction*
                              :asset-key asset-key*
                              :state :awaiting
                              :status :pending
                              :last-updated-ms now-ms})
        non-blank-text (fn [value]
                         (when-let [text (some-> value str .trim)]
                           (when (seq text)
                             text)))
        result (error-poll-lifecycle {:store store
                                      :direction :deposit
                                      :asset-key :btc
                                      :awaiting-lifecycle awaiting-lifecycle
                                      :non-blank-text non-blank-text}
                                     (js/Error. "boom")
                                     42)]
    (is (= "prev-op" (:operation-id result)))
    (is (= true (:retained result)))
    (is (= :pending (:state result)))
    (is (= :pending (:status result)))
    (is (= :deposit (:direction result)))
    (is (= :btc (:asset-key result)))
    (is (= 42 (:last-updated-ms result)))
    (is (= "boom" (:error result)))))

(deftest refresh-active-withdraw-queue-includes-static-queue-flags-test
  (let [refresh-active-withdraw-queue! @#'hyperopen.funding.application.lifecycle-polling/refresh-active-withdraw-queue!
        calls (atom [])]
    (refresh-active-withdraw-queue!
     {:direction :withdraw
      :request-queue! (fn [] :queue)
      :should-continue? (fn [] true)
      :fetch-hyperunit-withdrawal-queue! (fn [opts]
                                           (swap! calls conj opts))
      :store (atom {})
      :base-url "https://api.hyperunit.xyz"
      :base-urls ["https://api.hyperunit.xyz"]
      :now-ms!* (fn [] 1700000000000)
      :runtime-error-message "boom"
      :asset-key :btc})
    (is (= 1 (count @calls)))
    (is (false? (get-in (first @calls) [:transition-loading?])))
    (is (= :btc (get-in (first @calls) [:expected-asset-key])))))

(deftest handle-poll-success-passes-direction-specific-addresses-to-select-operation-test
  (let [handle-poll-success! @#'hyperopen.funding.application.lifecycle-polling/handle-poll-success!
        select-calls (atom [])
        update-calls (atom [])
        schedule-calls (atom [])]
    (doseq [[direction expected-source expected-destination operation-id]
            [[:withdraw "0xowner" "bc1qdestination" "op-withdraw"]
             [:deposit nil "0xowner" "op-deposit"]]]
      (handle-poll-success!
       {:select-operation (fn [operations opts]
                            (swap! select-calls conj [direction operations opts])
                            {:operation-id operation-id
                             :state-key :pending
                             :status :pending})
        :direction direction
        :asset-key :btc
        :protocol-address "bc1qprotocol"
        :destination-address "bc1qdestination"
        :wallet-address "0xowner"
        :operation->lifecycle (fn [operation direction* asset-key* now-ms]
                                {:operation-id (:operation-id operation)
                                 :direction direction*
                                 :asset-key asset-key*
                                 :state (:state-key operation)
                                 :status (:status operation)
                                 :last-updated-ms now-ms})
        :awaiting-lifecycle (fn [_direction _asset-key _now-ms]
                              (throw (js/Error. "expected operation to be selected")))
        :should-continue? (fn [] true)
        :now-ms!* (fn [] 1700000001234)
        :update-lifecycle! (fn [lifecycle]
                             (swap! update-calls conj [direction lifecycle]))
        :refresh-withdraw-queue! (fn [] nil)
        :hyperunit-lifecycle-terminal? (fn [_lifecycle] false)
        :clear-lifecycle-poll-token! (fn [_poll-key _token]
                                       (throw (js/Error. "should not clear token for pending lifecycle")))
        :poll-key :poll-key
        :token :token
        :notify-terminal! (fn [_lifecycle]
                            (throw (js/Error. "should not notify terminal for pending lifecycle")))
        :lifecycle-next-delay-ms (fn [_now-ms _lifecycle] 2500)
        :schedule-next! (fn [delay-ms poll-fn]
                          (swap! schedule-calls conj [direction delay-ms (fn? poll-fn)]))
        :poll! (fn [] nil)}
       {:operations [{:operation-id operation-id}]})
      (let [[_direction _operations opts] (last @select-calls)
            [_updated-direction lifecycle] (last @update-calls)
            [_scheduled-direction delay-ms poll-fn?] (last @schedule-calls)]
        (is (= expected-source
               (:source-address opts)))
        (is (= expected-destination
               (:destination-address opts)))
        (is (= direction
               _updated-direction))
        (is (= direction
               (:direction lifecycle)))
        (is (= :btc
               (:asset-key lifecycle)))
        (is (= 1700000001234
               (:last-updated-ms lifecycle)))
        (is (= 2500 delay-ms))
        (is (true? poll-fn?))))
    (is (= 2 (count @select-calls)))
    (is (= 2 (count @update-calls)))
    (is (= 2 (count @schedule-calls)))))

(deftest handle-poll-error-preserves-map-previous-state-and-drops-non-map-previous-state-test
  (let [handle-poll-error! @#'hyperopen.funding.application.lifecycle-polling/handle-poll-error!
        update-calls (atom [])
        schedule-calls (atom [])]
    (doseq [[label previous preserve?]
            [[:map {:operation-id "prev-op"
                    :state :pending
                    :status :pending
                    :retained true} true]
             [:non-map :not-a-map false]]]
      (let [store (atom {:funding-ui {:modal {:hyperunit-lifecycle previous}}})]
        (handle-poll-error!
         {:store store
          :direction :deposit
          :asset-key :btc
          :awaiting-lifecycle (fn [direction asset-key now-ms]
                                {:direction direction
                                 :asset-key asset-key
                                 :state :awaiting
                                 :status :pending
                                 :last-updated-ms now-ms
                                 :fallback true})
          :non-blank-text (fn [value]
                            (when-let [text (some-> value str .trim)]
                              (when (seq text)
                                text)))
          :should-continue? (fn [] true)
          :now-ms!* (fn [] 1700000001234)
          :update-lifecycle! (fn [lifecycle]
                               (swap! update-calls conj [label lifecycle]))
          :refresh-withdraw-queue! (fn [] nil)
          :default-poll-delay-ms 3333
          :schedule-next! (fn [delay-ms poll-fn]
                            (swap! schedule-calls conj [label delay-ms (fn? poll-fn)]))
          :poll! (fn [] nil)}
         (js/Error. "boom"))
        (let [[_label lifecycle] (last @update-calls)
              [_scheduled-label delay-ms poll-fn?] (last @schedule-calls)]
          (is (= :deposit
                 (:direction lifecycle)))
          (is (= :btc
                 (:asset-key lifecycle)))
          (is (= 1700000001234
                 (:last-updated-ms lifecycle)))
          (is (= "boom"
                 (:error lifecycle)))
          (is (= 3333 delay-ms))
          (is (true? poll-fn?))
          (if preserve?
            (do
              (is (= "prev-op"
                     (:operation-id lifecycle)))
              (is (= true
                     (:retained lifecycle))))
            (do
              (is (nil? (:operation-id lifecycle)))
              (is (nil? (:retained lifecycle))))))))
    (is (= 2 (count @update-calls)))
    (is (= 2 (count @schedule-calls)))))
