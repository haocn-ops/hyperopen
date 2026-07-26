(ns hyperopen.runtime.effect-adapters.attribution-test
  (:require [cljs.test :refer-macros [async deftest is testing]]
            [hyperopen.runtime.effect-adapters.attribution :as runtime]
            [hyperopen.service.attribution :as attribution]
            [hyperopen.service.fixtures :as fixtures]))

(defn- memory-deps
  [storage fetch-fn now-ms-fn schedule-retry!]
  {:fetch-fn fetch-fn
   :local-storage-get (fn [key] (get @storage key))
   :local-storage-set! (fn [key value] (swap! storage assoc key value))
   :now-ms-fn now-ms-fn
   :random-value-fn (constantly 0.25)
   :schedule-retry! schedule-retry!})

(defn- endpoint-tenant
  []
  (assoc-in fixtures/default-tenant-raw
            [:affiliate :event-endpoint]
            "https://events.example.test/attribution"))

(defn- safe-event
  [event-id market]
  {:event/id event-id
   :event/type :trade-submit-requested
   :tenant/id "hyperopen-default"
   :affiliate/id "hyperopen-official"
   :venue/id :hyperliquid
   :session/id "session-recovery-fixture"
   :wallet/address-hash "wallet-hash-recovery-fixture"
   :occurred-at-ms 1700000000000
   :market market
   :outcome :submitted})

(defn- queue-record
  [event delivery-status attempt-count]
  {:event event
   :delivery/status delivery-status
   :delivery/attempt-count attempt-count})

(defn- json-key
  [key]
  (if (keyword? key)
    (if-let [key-namespace (namespace key)]
      (str key-namespace "/" (name key))
      (name key))
    (str key)))

(defn- json-wire-value
  [value]
  (cond
    (map? value)
    (reduce-kv (fn [result key nested-value]
                 (aset result (json-key key) (json-wire-value nested-value))
                 result)
               #js {}
               value)

    (sequential? value) (into-array (map json-wire-value value))
    (keyword? value) (name value)
    :else value))

(defn- json-string
  [value]
  (js/JSON.stringify (json-wire-value value)))

(defn- parsed-json
  [value]
  (js->clj (js/JSON.parse (json-string value)) :keywordize-keys true))

(defn- response-with-json
  [value]
  #js {:ok true
       :json (fn []
               (js/Promise.resolve (json-wire-value value)))})

(defn- verified-provider-settlement
  [event]
  {:outcome :settled
   :provider-event-id "provider-settlement-fixture"
   :occurred-at-ms (:occurred-at-ms event)
   :settled-at-ms 1700000002000
   :tenant/id (:tenant/id event)
   :affiliate/id (:affiliate/id event)
   :venue/id (:venue/id event)
   :rebate-amount 12.5
   :provider/evidence {:verified? true
                       :verification-id "verification-fixture"
                       :response-digest "digest-fixture"}})

(defn- resume-pending-delivery-fn
  []
  ;; Kept dynamic so the RED runner can expose the absent public recovery API
  ;; while still compiling the provider-response assertions below.
  (js/goog.getObjectByName
   "hyperopen.runtime.effect_adapters.attribution.resume_pending_delivery_BANG_"))

(deftest record-attribution-event-persists-redacted-deduplicated-summary-test
  (let [storage (atom {})
        fetch-count (atom 0)
        store (atom {:tenant/override fixtures/default-tenant-raw
                     :wallet {:address fixtures/wallet-address}})
        deps (memory-deps storage
                          (fn [& _]
                            (swap! fetch-count inc)
                            (js/Promise.resolve #js {:ok true}))
                          (constantly 1700000000000)
                          (fn [_ _] nil))
        attrs {:wallet/address fixtures/wallet-address
               :market "BTC"
               :outcome :observed
               :raw-signature "signed-secret"}
        first-event (runtime/record-attribution-event!
                     deps store :wallet-connected attrs)
        second-event (runtime/record-attribution-event!
                      deps store :wallet-connected attrs)
        summary (runtime/export-event-summary @store)]
    (is (= (:event/id first-event) (:event/id second-event)))
    (is (= 1 (count summary)))
    (is (= :observed (:delivery/status (first summary))))
    (is (= 0 @fetch-count))
    (is (string? (:wallet/address-hash (first summary))))
    (is (not (contains? (first summary) :wallet/address)))
    (is (not (attribution/contains-secret? summary)))
    (is (string? (get @storage runtime/storage-key)))))

(deftest operator-api-exports-only-redacted-event-summaries-test
  (let [original-api (aget js/globalThis "HYPEROPEN_ATTRIBUTION")
        store (atom {:attribution
                     {:queue [{:event {:event/id "evt-safe"
                                      :event/type :wallet-connected
                                      :wallet/address-hash "evt-wallet-hash"
                                      :wallet/address fixtures/wallet-address
                                      :private-key "must-not-export"
                                      :outcome :observed}
                               :delivery/status :observed
                               :delivery/attempt-count 0}]}})]
    (try
      (let [api (runtime/install-operator-api! store)
            payload ((aget api "eventsJson"))]
        (is (string? payload))
        (is (not (re-find #"must-not-export" payload)))
        (is (not (re-find (re-pattern fixtures/wallet-address) payload)))
        (is (re-find #"evt-wallet-hash" payload))
        (is (true? ((aget api "clear"))))
        (is (= [] (runtime/export-event-summary @store))))
      (finally
        (if (some? original-api)
          (aset js/globalThis "HYPEROPEN_ATTRIBUTION" original-api)
          (js-delete js/globalThis "HYPEROPEN_ATTRIBUTION"))))))

(deftest attribution-queue-is-bounded-test
  (let [storage (atom {})
        clock (atom 1700000000000)
        store (atom {:tenant/override fixtures/default-tenant-raw
                     :wallet {:address fixtures/wallet-address}})
        deps (memory-deps storage
                          (fn [& _] (js/Promise.resolve #js {:ok true}))
                          #(swap! clock inc)
                          (fn [_ _] nil))]
    (dotimes [idx (+ runtime/queue-limit 5)]
      (runtime/record-attribution-event!
       deps store :trade-submit-result {:market (str "MARKET-" idx)
                                        :outcome :accepted}))
    (is (= runtime/queue-limit
           (count (runtime/export-event-summary @store))))
    (is (= "MARKET-5"
           (:market (first (runtime/export-event-summary @store)))))))

(deftest failed-attribution-delivery-retries-with-one-idempotency-key-test
  (async done
    (let [storage (atom {})
          calls (atom [])
          tenant (assoc-in fixtures/default-tenant-raw
                           [:affiliate :event-endpoint]
                           "https://events.example.test/attribution")
          store (atom {:tenant/override tenant
                       :wallet {:address fixtures/wallet-address}})
          deps (memory-deps
                storage
                (fn [endpoint request]
                  (swap! calls conj [endpoint
                                     (aget (.-headers request) "Idempotency-Key")])
                  (js/Promise.reject (js/Error. "offline")))
                (constantly 1700000000000)
                (fn [retry-fn _delay-ms] (retry-fn)))]
      (runtime/record-attribution-event!
       deps store :trade-submit-requested {:market "BTC" :outcome :submitted})
      (js/setTimeout
       (fn []
         (try
           (let [summary (first (runtime/export-event-summary @store))]
             (is (= runtime/max-delivery-attempts (count @calls)))
             (is (= 1 (count (set (map second @calls)))))
             (is (= :unavailable (:delivery/status summary)))
             (is (= runtime/max-delivery-attempts
                    (:delivery/attempt-count summary))))
           (finally
             (done))))
       0))))

(deftest verified-provider-json-settles-the-existing-redacted-event-test
  (async done
    (let [storage (atom {})
          calls (atom [])
          store (atom {:tenant/override (endpoint-tenant)
                       :wallet {:address fixtures/wallet-address}})
          deps (memory-deps
                storage
                (fn [endpoint request]
                  (swap! calls conj [endpoint request])
                  (let [event (parsed-json (js->clj (js/JSON.parse (.-body request))
                                                   :keywordize-keys true))
                        provider-result
                        (assoc (verified-provider-settlement event)
                               :private-key "provider-private-key"
                               :raw-signature "provider-raw-signature"
                               :wallet/address fixtures/wallet-address
                               :provider/raw-response "provider-raw-response")]
                    (js/Promise.resolve (response-with-json provider-result))))
                (constantly 1700000000000)
                (fn [_ _] nil))
          event (runtime/record-attribution-event!
                 deps store :trade-submit-result {:market "BTC" :outcome :accepted})]
      (js/setTimeout
       (fn []
         (try
           (let [summary (first (runtime/export-event-summary @store))
                 stored (get @storage runtime/storage-key)
                 request (second (first @calls))]
             (is (= 1 (count @calls)))
             (is (= (:event/id event) (:event/id summary)))
             (is (= (:event/type event) (:event/type summary)))
             (is (= (:market event) (:market summary)))
             (is (= :settled (:outcome summary)))
             (is (= "provider-settlement-fixture" (:provider-event-id summary)))
             (is (= 12.5 (:rebate-amount summary)))
             (is (= 1700000002000 (:settled-at-ms summary)))
             (is (= :accepted (:delivery/status summary)))
             (is (= "omit" (.-credentials request)))
             (is (= (:event/id event)
                    (aget (.-headers request) "Idempotency-Key")))
             (is (not (re-find #"provider-private-key|provider-raw-signature|provider-raw-response"
                               stored)))
             (is (not (re-find (re-pattern fixtures/wallet-address) stored)))
             (is (not (attribution/contains-secret? (runtime/export-event-summary @store))))
             (is (not (attribution/contains-secret? (get-in @store [:attribution :queue])))))
           (finally
             (done))))
       0))))

(deftest invalid-provider-json-follows-the-bounded-failure-path-test
  (async done
    (let [storage (atom {})
          calls (atom [])
          store (atom {:tenant/override (endpoint-tenant)
                       :wallet {:address fixtures/wallet-address}})
          invalid-results
          {"REJECTED-JSON" :reject-json
           "ARRAY-JSON" []
           "MISMATCHED-SUBJECT"
           (assoc (verified-provider-settlement (safe-event "evt-mismatch" "MISMATCHED-SUBJECT"))
                  :tenant/id "another-tenant")
           "INVALID-TIME"
           (assoc (verified-provider-settlement (safe-event "evt-time" "INVALID-TIME"))
                  :settled-at-ms 1699999999999)
           "MISSING-PROOF"
           (assoc-in (verified-provider-settlement (safe-event "evt-proof" "MISSING-PROOF"))
                     [:provider/evidence :verification-id] nil)}
          deps (memory-deps
                storage
                (fn [_endpoint request]
                  (let [market (aget (js/JSON.parse (.-body request)) "market")
                        result (get invalid-results market)]
                    (swap! calls conj [(aget (.-headers request) "Idempotency-Key") market])
                    (js/Promise.resolve
                     (if (= :reject-json result)
                       #js {:ok true
                            :json (fn []
                                    (js/Promise.reject (js/Error. "invalid provider JSON")))}
                       (response-with-json result)))))
                (constantly 1700000000000)
                (fn [retry-fn _delay-ms] (retry-fn)))]
      (doseq [market (keys invalid-results)]
        (runtime/record-attribution-event!
         deps store :trade-submit-result {:market market :outcome :accepted}))
      (js/setTimeout
       (fn []
         (let [summary (runtime/export-event-summary @store)
               calls-by-market (frequencies (map second @calls))]
           (is (= (* runtime/max-delivery-attempts (count invalid-results))
                  (count @calls)))
           (doseq [market (keys invalid-results)]
             (let [event-summary (first (filter #(= market (:market %)) summary))]
               (testing market
                 (is (= runtime/max-delivery-attempts (get calls-by-market market)))
                 (is (= :unavailable (:delivery/status event-summary)))
                 (is (= runtime/max-delivery-attempts
                        (:delivery/attempt-count event-summary)))
                 (is (not= :settled (:outcome event-summary)))
                 (is (nil? (:rebate-amount event-summary)))
                 (is (nil? (:settled-at-ms event-summary))))))
         (done))
       0)))))

(deftest restored-queue-discards-unsafe-records-and-keeps-the-final-duplicate-test
  (let [first-copy (safe-event "evt-duplicate" "OLD")
        final-copy (safe-event "evt-duplicate" "NEW")
        unsafe-copy (assoc-in (queue-record (safe-event "evt-unsafe" "UNSAFE") :pending 0)
                              [:event :wallet/address]
                              fixtures/wallet-address)
        invalid-status (queue-record (safe-event "evt-invalid-status" "INVALID") :made-up 0)
        storage (atom {runtime/storage-key
                       (json-string [(queue-record first-copy :pending 0)
                                     unsafe-copy
                                     invalid-status
                                     (queue-record final-copy :pending 2)])})
        store (atom {})
        queue (runtime/restore-queue!
               store
               (fn [key] (get @storage key)))]
    (is (= 1 (count queue)))
    (is (= final-copy (:event (first queue))))
    (is (= :pending (:delivery/status (first queue))))
    (is (= 2 (:delivery/attempt-count (first queue))))
    (is (not (attribution/contains-secret? queue)))))

(deftest structurally-forged-persisted-settlement-is-downgraded-before-export-test
  (let [forged-event (assoc (safe-event "evt-forged-persisted-settlement" "BTC")
                            :event/type :trade-submit-result
                            :outcome :settled
                            :provider-event-id "forged-provider-event"
                            :rebate-amount 999.99
                            :settled-at-ms 1700000002000)
        forged-record (assoc (queue-record forged-event :accepted 1)
                             :provider/confirmation
                             {:settlement/verified? true
                              :provider/evidence {:verified? true
                                                  :verification-id "forged-verification"
                                                  :response-digest "forged-digest"}})
        storage (atom {runtime/storage-key (json-string [forged-record])})
        store (atom {})
        queue (runtime/restore-queue! store (fn [key] (get @storage key)))
        summary (runtime/export-event-summary @store)
        restored-event (:event (first queue))
        exported-event (first summary)]
    (is (= 1 (count queue)))
    (is (not= :settled (:outcome restored-event)))
    (is (nil? (:rebate-amount restored-event)))
    (is (nil? (:settled-at-ms restored-event)))
    (is (not= :settled (:outcome exported-event)))
    (is (nil? (:rebate-amount exported-event)))
    (is (nil? (:settled-at-ms exported-event)))))

(deftest persisted-accepted-record-cannot-retain-reward-fields-after-restore-test
  (let [accepted-event (assoc (safe-event "evt-accepted-reward-fields" "BTC")
                              :event/type :trade-submit-result
                              :outcome :accepted
                              :provider-event-id "untrusted-provider-event"
                              :rebate-amount 12.5
                              :settled-at-ms 1700000002000)
        storage (atom {runtime/storage-key
                       (json-string [(queue-record accepted-event :accepted 1)])})
        store (atom {})
        queue (runtime/restore-queue! store (fn [key] (get @storage key)))
        summary (runtime/export-event-summary @store)
        restored-event (:event (first queue))
        exported-event (first summary)]
    (is (= :accepted (:outcome restored-event)))
    (is (nil? (:rebate-amount restored-event)))
    (is (nil? (:settled-at-ms restored-event)))
    (is (= :accepted (:outcome exported-event)))
    (is (nil? (:rebate-amount exported-event)))
    (is (nil? (:settled-at-ms exported-event)))))

(deftest resume-keeps-identity-mismatched-pending-records-local-test
  (let [event (safe-event "evt-tenant-isolation" "BTC")
        mismatched-tenants
        {:tenant-id (assoc (endpoint-tenant) :tenant/id "other-tenant")
         :affiliate-id (assoc-in (endpoint-tenant) [:affiliate :id] "other-affiliate")
         :venue-id (assoc-in (endpoint-tenant) [:venue :id] :other-venue)}]
    (doseq [[label tenant] mismatched-tenants]
      (let [storage (atom {runtime/storage-key
                           (json-string [(queue-record event :pending 0)])})
            fetch-count (atom 0)
            store (atom {:tenant/override tenant})
            deps (memory-deps
                  storage
                  (fn [& _]
                    (swap! fetch-count inc)
                    (js/Promise.resolve (response-with-json {:outcome :accepted})))
                  (constantly 1700000000000)
                  (fn [_ _] (throw (js/Error. "mismatched event must not retry"))))
            resume! (resume-pending-delivery-fn)]
        (testing (name label)
          (is (fn? resume!))
          (when (fn? resume!)
            (resume! deps store)
            (is (= 0 @fetch-count))
            (is (= [(queue-record event :pending 0)]
                   (get-in @store [:attribution :queue])))))))))

(deftest resume-keeps-stored-venue-mismatch-local-despite-a-valid-active-endpoint-test
  (let [event (assoc (safe-event "evt-venue-isolation" "BTC")
                     :venue/id :other-venue)
        storage (atom {runtime/storage-key
                       (json-string [(queue-record event :pending 0)])})
        fetch-count (atom 0)
        store (atom {:tenant/override (endpoint-tenant)})
        deps (memory-deps
              storage
              (fn [& _]
                (swap! fetch-count inc)
                (js/Promise.resolve (response-with-json {:outcome :accepted})))
              (constantly 1700000000000)
              (fn [_ _] (throw (js/Error. "venue-mismatched event must not retry"))))
        resume! (resume-pending-delivery-fn)]
    (is (fn? resume!))
    (when (fn? resume!)
      (resume! deps store)
      (is (= 0 @fetch-count))
      (is (= [(queue-record event :pending 0)]
             (get-in @store [:attribution :queue]))))))

(deftest duplicate-terminal-delivery-states-beat-later-pending-records-test
  (doseq [{:keys [label terminal-status terminal-attempt-count]}
          [{:label :accepted-over-pending
            :terminal-status :accepted
            :terminal-attempt-count 1}
           {:label :unavailable-over-pending
            :terminal-status :unavailable
            :terminal-attempt-count runtime/max-delivery-attempts}]]
    (let [event-id (str "evt-duplicate-" (name label))
          terminal-event (safe-event event-id "TERMINAL")
          latest-pending-event (safe-event event-id "LATEST-PENDING")
          storage (atom {runtime/storage-key
                         (json-string [(queue-record terminal-event terminal-status terminal-attempt-count)
                                       (queue-record latest-pending-event :pending 0)])})
          fetch-count (atom 0)
          store (atom {:tenant/override (endpoint-tenant)})
          deps (memory-deps
                storage
                (fn [& _]
                  (swap! fetch-count inc)
                  (js/Promise.resolve (response-with-json {:outcome :accepted})))
                (constantly 1700000000000)
                (fn [_ _] (throw (js/Error. "terminal duplicate must not retry"))))
          resume! (resume-pending-delivery-fn)]
      (testing (name label)
        (is (fn? resume!))
        (when (fn? resume!)
          (resume! deps store)
          (let [queue (get-in @store [:attribution :queue])
                record (first queue)]
            (is (= 0 @fetch-count))
            (is (= 1 (count queue)))
            (is (= "LATEST-PENDING" (get-in record [:event :market])))
            (is (= terminal-status (:delivery/status record)))
            (is (= terminal-attempt-count (:delivery/attempt-count record)))))))))

(deftest resume-pending-delivery-is-one-shot-public-and-bounded-test
  (let [pending (safe-event "evt-resume-pending" "BTC")
        ceiling (safe-event "evt-resume-ceiling" "ETH")
        accepted (safe-event "evt-resume-accepted" "SOL")
        unavailable (safe-event "evt-resume-unavailable" "ARB")
        storage (atom {runtime/storage-key
                       (json-string [(queue-record pending :pending 0)
                                     (queue-record ceiling :pending runtime/max-delivery-attempts)
                                     (queue-record accepted :accepted 1)
                                     (queue-record unavailable :unavailable 1)])})
        calls (atom [])
        store (atom {:tenant/override (endpoint-tenant)})
        deps (memory-deps
              storage
              (fn [_endpoint request]
                (swap! calls conj request)
                (js/Promise.resolve (response-with-json {:outcome :accepted})))
              (constantly 1700000000000)
              (fn [_ _] nil))
        resume! (resume-pending-delivery-fn)]
    (is (fn? resume!))
    (when (fn? resume!)
      (resume! deps store)
      (resume! deps store)
      (let [request (first @calls)
            body (parsed-json (js->clj (js/JSON.parse (.-body request))
                                      :keywordize-keys true))
            queue (get-in @store [:attribution :queue])
            ceiling-record (first (filter #(= "evt-resume-ceiling"
                                               (get-in % [:event :event/id])) queue))]
        (is (= 1 (count @calls)))
        (is (= "POST" (.-method request)))
        (is (= "omit" (.-credentials request)))
        (is (= "no-referrer" (.-referrerPolicy request)))
        (is (= "application/json" (aget (.-headers request) "Content-Type")))
        (is (= (:event/id pending) (aget (.-headers request) "Idempotency-Key")))
        (is (nil? (aget (.-headers request) "Authorization")))
        (is (= (parsed-json pending) body))
        (is (= :unavailable (:delivery/status ceiling-record)))
        (is (= runtime/max-delivery-attempts (:delivery/attempt-count ceiling-record)))
        (is (not (re-find #"private-key|seed-phrase|api-secret|access-token|raw-signature|provider-raw"
                          (.-body request))))))))

(deftest resume-without-endpoint-keeps-pending-records-untouched-test
  (let [pending (safe-event "evt-no-endpoint" "BTC")
        storage (atom {runtime/storage-key
                       (json-string [(queue-record pending :pending 1)])})
        fetch-count (atom 0)
        store (atom {:tenant/override fixtures/default-tenant-raw})
        deps (memory-deps
              storage
              (fn [& _]
                (swap! fetch-count inc)
                (js/Promise.resolve (response-with-json {:outcome :accepted})))
              (constantly 1700000000000)
              (fn [_ _] (throw (js/Error. "must not schedule"))))
        resume! (resume-pending-delivery-fn)]
    (is (fn? resume!))
    (when (fn? resume!)
      (resume! deps store)
      (is (= 0 @fetch-count))
      (is (= [(queue-record pending :pending 1)]
             (get-in @store [:attribution :queue]))))))
