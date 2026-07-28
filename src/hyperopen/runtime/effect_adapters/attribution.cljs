(ns hyperopen.runtime.effect-adapters.attribution
  "Non-blocking delivery and bounded persistence for redacted attribution events."
  (:require [clojure.string :as str]
            [hyperopen.platform :as platform]
            [hyperopen.service.attribution :as attribution]
            [hyperopen.service.tenant-config :as tenant-config]))

(def storage-key "hyperopen:attribution-events:v1")
(def ^:private consent-storage-prefix "hyperopen:affiliate-consent:v1:")
(def queue-limit 200)
(def max-delivery-attempts 3)

(def ^:private export-fields
  [:event/id :event/type :tenant/id :affiliate/id :venue/id :session/id
   :wallet/address-hash :occurred-at-ms :market :range :outcome
   :provider-event-id :rebate-amount :settled-at-ms])

(def ^:private export-field-set
  (set export-fields))

(def ^:private queue-record-fields
  #{:event :delivery/status :delivery/attempt-count :provider/confirmation})

(def ^:private delivery-statuses
  #{:observed :pending :accepted :unavailable})

(def ^:private event-types
  #{:tenant-loaded :affiliate-attribution-seen :wallet-connected
    :trade-submit-requested :trade-submit-result :analytics-viewed})

(def ^:private event-outcomes
  #{:observed :submitted :accepted :rejected :unavailable :unknown :settled})

(def ^:private non-settlement-outcomes
  (disj event-outcomes :settled))

(def ^:private provider-confirmation-fields
  [:provider/evidence :settlement/verified?])

(defn- finite-number?
  [value]
  (and (number? value) (js/isFinite value)))

(defn- nonblank-string?
  [value]
  (and (string? value) (seq (str/trim value))))

(defn- value->keyword
  [value]
  (if (and (string? value) (seq (str/trim value)))
    (keyword (str/trim value))
    value))

(defn- json-key
  [key]
  (if (keyword? key)
    (if-let [namespace (namespace key)]
      (str namespace "/" (name key))
      (name key))
    (str key)))

(defn- json-value
  [value]
  (cond
    (map? value) (reduce-kv (fn [result key nested-value]
                              (aset result (json-key key) (json-value nested-value))
                              result)
                            #js {}
                            value)
    (sequential? value) (into-array (map json-value value))
    (keyword? value) (name value)
    :else value))

(defn- normalize-persisted-event
  [event]
  (let [event* (select-keys event export-fields)]
    (-> (cond-> event*
          (string? (:event/type event*)) (update :event/type value->keyword)
          (string? (:venue/id event*)) (update :venue/id value->keyword)
          (string? (:range event*)) (update :range value->keyword)
          (string? (:outcome event*)) (update :outcome value->keyword))
        (dissoc :rebate-amount :settled-at-ms))))

(defn- downgrade-persisted-settlement
  [event]
  (if (= :settled (:outcome event))
    (-> event
        (dissoc :rebate-amount :settled-at-ms)
        (assoc :outcome :unknown))
    event))

(defn- valid-persisted-event?
  [event]
  (and (map? event)
       (nonblank-string? (:event/id event))
       (contains? event-types (:event/type event))
       (nonblank-string? (:tenant/id event))
       (nonblank-string? (:affiliate/id event))
       (or (keyword? (:venue/id event))
           (nonblank-string? (:venue/id event)))
       (nonblank-string? (:wallet/address-hash event))
       (finite-number? (:occurred-at-ms event))
       (contains? event-outcomes (:outcome event))
       (not (attribution/contains-secret? event))))

(defn- normalize-queue-record
  [record]
  (let [raw-event (:event record)
        event (-> raw-event
                  normalize-persisted-event
                  downgrade-persisted-settlement)
        delivery-status (value->keyword (:delivery/status record))
        attempt-count (:delivery/attempt-count record)]
    (when (and (map? record)
               (map? raw-event)
               (every? export-field-set (keys raw-event))
               (every? queue-record-fields (keys record))
               (valid-persisted-event? event)
               (contains? delivery-statuses delivery-status)
               (finite-number? attempt-count)
               (== attempt-count (js/Math.floor attempt-count))
               (not (neg? attempt-count))
               (not (attribution/contains-secret? record)))
      {:event event
       :delivery/status delivery-status
       :delivery/attempt-count attempt-count})))

(defn- delivery-status-precedence
  [delivery-status]
  (if (= :pending delivery-status) 0 1))

(defn- coalesce-delivery-records
  [current record]
  (let [current-status (:delivery/status current)
        record-status (:delivery/status record)
        preferred-status (if (>= (delivery-status-precedence record-status)
                                 (delivery-status-precedence current-status))
                           record-status
                           current-status)]
    {:event (:event record)
     :delivery/status preferred-status
     :delivery/attempt-count (max (:delivery/attempt-count current)
                                  (:delivery/attempt-count record))}))

(defn- coalesce-by-event-id
  [records]
  (let [{:keys [by-id reverse-last-order]}
        (reduce (fn [{:keys [by-id reverse-last-order] :as state} record]
                  (let [event-id (get-in record [:event :event/id])]
                    (if-let [current (get by-id event-id)]
                      (assoc state :by-id
                             (assoc by-id event-id
                                    (coalesce-delivery-records record current)))
                      {:by-id (assoc by-id event-id record)
                       :reverse-last-order (conj reverse-last-order event-id)})))
                {:by-id {}
                 :reverse-last-order []}
                (rseq (vec records)))]
    (mapv by-id (reverse reverse-last-order))))

(defn- parse-queue
  [raw]
  (try
    (let [parsed (when (seq raw)
                   (js->clj (js/JSON.parse raw) :keywordize-keys true))]
      (if (sequential? parsed)
        (->> parsed
             (keep normalize-queue-record)
             coalesce-by-event-id
             (take-last queue-limit)
             vec)
        []))
    (catch :default _
      [])))

(defn- serialize-queue
  [queue]
  (js/JSON.stringify (json-value (vec queue))))

(defn- session-id
  [now-ms random-value]
  (str "session-" (js/Math.floor now-ms) "-"
       (js/Math.floor (* 1000000000 random-value))))

(defn- ensure-session-id!
  [store now-ms-fn random-value-fn]
  (or (get-in @store [:attribution :session-id])
      (let [value (session-id (now-ms-fn) (random-value-fn))]
        (swap! store assoc-in [:attribution :session-id] value)
        value)))

(defn- queue-record
  [event delivery-status]
  {:event event
   :delivery/status delivery-status
   :delivery/attempt-count 0})

(defn- queue-index
  [queue event-id]
  (first
   (keep-indexed (fn [idx record]
                   (when (= event-id (get-in record [:event :event/id])) idx))
                 queue)))

(defn- bounded-conj
  [queue record]
  (let [next-queue (conj (vec queue) record)
        start (max 0 (- (count next-queue) queue-limit))]
    (subvec next-queue start)))

(defn- latest-status
  [queue]
  (or (:delivery/status (peek queue)) :unavailable))

(defn- sync-attribution-state
  [state queue]
  (-> state
      (assoc-in [:attribution :queue] queue)
      (assoc-in [:attribution :status] (latest-status queue))
      (assoc-in [:attribution :last-event-id]
                (get-in (peek queue) [:event :event/id]))
      (assoc-in [:attribution :event-count] (count queue))))

(defn restore-queue!
  ([store]
   (restore-queue! store platform/local-storage-get))
  ([store local-storage-get]
   (when-not (true? (get-in @store [:attribution :initialized?]))
     (let [queue (try
                   (parse-queue (local-storage-get storage-key))
                   (catch :default _ []))]
       (swap! store #(-> %
                         (sync-attribution-state queue)
                         (assoc-in [:attribution :initialized?] true)))))
   (get-in @store [:attribution :queue] [])))

(defn- persist-queue!
  [store local-storage-set!]
  (try
    (local-storage-set! storage-key
                        (serialize-queue (get-in @store [:attribution :queue] [])))
    true
    (catch :default _
      false)))

(defn- update-delivery!
  [store event-id delivery-status attempt-count local-storage-set!]
  (swap! store
         (fn [state]
           (let [queue (get-in state [:attribution :queue] [])
                 idx (queue-index queue event-id)
                 next-queue (if (some? idx)
                              (assoc queue idx
                                     (-> (nth queue idx)
                                         (assoc :delivery/status delivery-status)
                                         (assoc :delivery/attempt-count attempt-count)))
                              queue)]
             (sync-attribution-state state next-queue))))
  (persist-queue! store local-storage-set!)
  delivery-status)

(defn- provider-context
  [event]
  (select-keys event [:tenant/id :affiliate/id :venue/id :session/id
                      :wallet/address-hash]))

(defn- apply-provider-result
  [event normalized-result]
  (let [settled? (true? (:settlement/verified? normalized-result))
        candidate (if settled?
                    (merge event
                           (select-keys normalized-result
                                        [:outcome :provider-event-id :occurred-at-ms
                                         :rebate-amount :settled-at-ms
                                         :provider/evidence :settlement/verified?]))
                    (-> event
                        (dissoc :provider-event-id :rebate-amount :settled-at-ms
                                :provider/evidence :settlement/verified?)
                        (assoc :outcome (:outcome normalized-result))))]
    (attribution/redact-attribution-event candidate)))

(defn- update-provider-result!
  [store event-id normalized-result attempt-count local-storage-set!]
  (swap! store
         (fn [state]
           (let [queue (get-in state [:attribution :queue] [])
                 idx (queue-index queue event-id)
                 next-queue (if (some? idx)
                              (let [record (nth queue idx)]
                                (assoc queue idx
                                       (let [settled? (true? (:settlement/verified?
                                                             normalized-result))]
                                         (cond-> (-> record
                                                     (assoc :event
                                                            (apply-provider-result
                                                             (:event record)
                                                             normalized-result))
                                                     (assoc :delivery/status :accepted)
                                                     (assoc :delivery/attempt-count attempt-count)
                                                     (dissoc :provider/confirmation))
                                           settled?
                                           (assoc :provider/confirmation
                                                  (select-keys normalized-result
                                                               provider-confirmation-fields))))))
                              queue)]
             (sync-attribution-state state next-queue))))
  (persist-queue! store local-storage-set!)
  :accepted)

(defn- endpoint-for-state
  [state]
  (let [tenant (tenant-config/active-tenant-config state)
        endpoint (get-in tenant [:affiliate :event-endpoint])]
    (when (and (true? (get-in tenant [:features :affiliate]))
               (= :enabled (get-in tenant [:affiliate :status]))
               (tenant-config/valid-affiliate-event-endpoint? endpoint))
      endpoint)))

(defn- consent-storage-key
  [state]
  (str consent-storage-prefix (:tenant/id (tenant-config/active-tenant-config state))))

(defn- stored-affiliate-consent?
  [state]
  (= "true" (platform/local-storage-get (consent-storage-key state))))

(defn affiliate-consent?
  [state]
  (if (contains? (get-in state [:attribution] {}) :affiliate-consent?)
    (true? (get-in state [:attribution :affiliate-consent?]))
    (stored-affiliate-consent? state)))

(defn- delivery-allowed?
  [deps state endpoint]
  (and (= endpoint (endpoint-for-state state))
       (true? ((:affiliate-consent? deps) state))))

(defn- active-tenant-identity
  [state]
  (let [tenant (tenant-config/active-tenant-config state)]
    {:tenant/id (:tenant/id tenant)
     :affiliate/id (get-in tenant [:affiliate :id])
     :venue/id (get-in tenant [:venue :id])}))

(defn- event-matches-active-tenant?
  [event tenant-identity]
  (= (select-keys event [:tenant/id :affiliate/id :venue/id])
     tenant-identity))

(defn- pending-event-record?
  [state event]
  (let [tenant-identity (active-tenant-identity state)
        event-id (:event/id event)]
    (some (fn [record]
            (and (= event-id (get-in record [:event :event/id]))
                 (= :pending (:delivery/status record))
                 (event-matches-active-tenant? (:event record) tenant-identity)))
          (get-in state [:attribution :queue] []))))

(defn- clear-pending-for-tenant!
  [store tenant-identity local-storage-set!]
  (swap! store
         (fn [state]
           (let [queue (get-in state [:attribution :queue] [])
                 next-queue (vec (remove (fn [record]
                                           (and (= :pending (:delivery/status record))
                                                (event-matches-active-tenant?
                                                 (:event record)
                                                 tenant-identity)))
                                         queue))]
             (sync-attribution-state state next-queue))))
  (persist-queue! store local-storage-set!)
  true)

(declare default-deps)

(defn set-affiliate-consent-with-deps!
  [deps store consent?]
  (let [deps* (merge (default-deps) deps)
        consent? (true? consent?)
        state @store
        tenant-identity (active-tenant-identity state)
        key (consent-storage-key state)]
    (swap! store assoc-in [:attribution :affiliate-consent?] consent?)
    (when-not consent?
      (clear-pending-for-tenant! store tenant-identity (:local-storage-set! deps*)))
    (try
      ((:local-storage-set! deps*) key (if consent? "true" "false"))
      (catch :default _
        nil))
    consent?))

(defn set-affiliate-consent!
  ([store consent?]
   (set-affiliate-consent-with-deps! (default-deps) store consent?))
  ([_ store consent?]
   (set-affiliate-consent-with-deps! (default-deps) store consent?)))

(defn- delivery-request
  [event]
  #js {:method "POST"
       :headers #js {"Content-Type" "application/json"
                     "Idempotency-Key" (:event/id event)}
       :body (js/JSON.stringify (json-value event))
       :credentials "omit"
       :referrerPolicy "no-referrer"})

(defn- normalize-provider-json
  [payload]
  (let [result (js->clj payload :keywordize-keys true)]
    (when (map? result)
      (cond-> result
        (string? (:outcome result)) (update :outcome value->keyword)
        (string? (:venue/id result)) (update :venue/id value->keyword)
        (string? (:verification/status result))
        (update :verification/status value->keyword)
        (string? (get-in result [:provider/evidence :verification/status]))
        (update-in [:provider/evidence :verification/status] value->keyword)))))

(defn- read-provider-result!
  [response]
  (try
    (if (fn? (some-> response .-json))
      (-> (.json response)
          (.then (fn [payload]
                   (if-let [result (normalize-provider-json payload)]
                     result
                     (throw (js/Error. "Provider response must be a JSON object"))))))
      (js/Promise.reject (js/Error. "Provider response has no JSON reader")))
    (catch :default _
      (js/Promise.reject (js/Error. "Provider response JSON reader failed")))))

(defn- accepted-provider-result?
  [provider-result normalized-result]
  (or (true? (:settlement/verified? normalized-result))
      (and (contains? non-settlement-outcomes (:outcome provider-result))
           (contains? non-settlement-outcomes (:outcome normalized-result)))))

(declare attempt-delivery!)

(defn- schedule-retry!
  [deps store endpoint event attempt-count]
  (let [delay-ms (* 500 (js/Math.pow 2 (dec attempt-count)))]
    (try
      ((:schedule-retry! deps)
       #(attempt-delivery! deps store endpoint event (inc attempt-count))
       delay-ms)
      (catch :default _
        (update-delivery! store (:event/id event) :unavailable attempt-count
                          (:local-storage-set! deps))))))

(defn- handle-delivery-failure!
  [deps store endpoint event attempt-count]
  (if (< attempt-count max-delivery-attempts)
    (do
      (update-delivery! store (:event/id event) :pending attempt-count
                        (:local-storage-set! deps))
      (schedule-retry! deps store endpoint event attempt-count))
    (update-delivery! store (:event/id event) :unavailable attempt-count
                      (:local-storage-set! deps))))

(defn- attempt-delivery!
  [deps store endpoint event attempt-count]
  (let [state @store
        pending? (pending-event-record? state event)]
    (if-not (and pending?
                 (delivery-allowed? deps state endpoint))
      (when pending?
        (update-delivery! store (:event/id event) :observed (max 0 (dec attempt-count))
                          (:local-storage-set! deps)))
    (do
      (update-delivery! store (:event/id event) :pending attempt-count
                        (:local-storage-set! deps))
      (try
        (-> ((:fetch-fn deps) endpoint (delivery-request event))
            (.then (fn [response]
                     (if (true? (.-ok response))
                       (read-provider-result! response)
                       (js/Promise.reject
                        (js/Error. "Provider response was not successful")))))
            (.then (fn [provider-result]
                     (let [normalized-result
                           (attribution/normalize-provider-result
                            (provider-context event)
                            provider-result)]
                       (if (accepted-provider-result? provider-result normalized-result)
                         (update-provider-result! store (:event/id event) normalized-result
                                                  attempt-count (:local-storage-set! deps))
                         (throw (js/Error. "Provider response did not validate"))))))
            (.catch (fn [_]
                      (handle-delivery-failure! deps store endpoint event attempt-count))))
        (catch :default _
          (handle-delivery-failure! deps store endpoint event attempt-count)))))))

(defn default-deps
  []
  {:fetch-fn js/fetch
   :local-storage-get platform/local-storage-get
   :local-storage-set! platform/local-storage-set!
   :now-ms-fn platform/now-ms
   :random-value-fn platform/random-value
   :schedule-retry! platform/set-timeout!
   :affiliate-consent? affiliate-consent?})

(defn resume-pending-delivery!
  "Restore and resume eligible pending deliveries once for this live store."
  ([store]
   (resume-pending-delivery! (default-deps) store))
  ([deps store]
   (let [deps* (merge (default-deps) deps)]
     (try
       (restore-queue! store (:local-storage-get deps*))
       (when-not (true? (get-in @store [:attribution :pending-delivery-resumed?]))
         (swap! store assoc-in [:attribution :pending-delivery-resumed?] true)
         (let [state @store
               tenant-identity (active-tenant-identity state)
               consent? (true? ((:affiliate-consent? deps*) state))]
           (swap! store assoc-in [:attribution :affiliate-consent?] consent?)
           (if-not consent?
             (clear-pending-for-tenant! store tenant-identity (:local-storage-set! deps*))
             (when-let [endpoint (endpoint-for-state state)]
               (doseq [record (get-in @store [:attribution :queue] [])]
                 (let [event (:event record)
                       status (:delivery/status record)
                       attempt-count (:delivery/attempt-count record)]
                   (when (and (= :pending status)
                              (event-matches-active-tenant? event tenant-identity))
                     (if (>= attempt-count max-delivery-attempts)
                       (update-delivery! store (:event/id event) :unavailable attempt-count
                                         (:local-storage-set! deps*))
                       (attempt-delivery! deps* store endpoint event (inc attempt-count))))))))))
       nil
       (catch :default _
         nil)))))

(defn record-attribution-event!
  ([store event-type attrs]
   (record-attribution-event! (default-deps) store event-type attrs))
  ([deps store event-type attrs]
   (let [{:keys [local-storage-get local-storage-set! now-ms-fn random-value-fn]}
         (merge (default-deps) deps)
         deps* (merge (default-deps) deps)
         state @store
         occurred-at-ms (now-ms-fn)
         wallet-address (or (:wallet/address attrs)
                            (get-in state [:wallet :address]))
         context (attribution/build-attribution-context
                  (tenant-config/active-tenant-config state)
                  {:session/id (ensure-session-id! store now-ms-fn random-value-fn)
                   :wallet/address wallet-address
                   :occurred-at-ms occurred-at-ms})
         event (attribution/build-attribution-event
                context
                event-type
                (-> (or attrs {})
                    (dissoc :wallet/address)
                    (assoc :occurred-at-ms occurred-at-ms)))]
     (restore-queue! store local-storage-get)
     (when-let [event-id (:event/id event)]
       (let [queue (get-in @store [:attribution :queue] [])
             existing? (some? (queue-index queue event-id))
             endpoint (endpoint-for-state @store)
             endpoint (when (and endpoint
                                 (delivery-allowed? deps* @store endpoint))
                        endpoint)
             initial-status (if endpoint :pending :observed)]
         (when-not existing?
           (swap! store
                  (fn [state*]
                    (sync-attribution-state
                     state*
                     (bounded-conj (get-in state* [:attribution :queue] [])
                                   (queue-record event initial-status)))))
           (persist-queue! store local-storage-set!)
           (when endpoint
             (attempt-delivery! deps* store endpoint event 1)))))
     event)))

(defn effect
  [_ store event-type attrs]
  (record-attribution-event! store event-type attrs))

(defn event-summary
  [record]
  (merge (select-keys (:event record) export-fields)
         (select-keys record [:delivery/status :delivery/attempt-count])))

(defn export-event-summary
  [state]
  (->> (get-in state [:attribution :queue] [])
       (mapv event-summary)))

(defn export-event-summary-json
  [state]
  (js/JSON.stringify (clj->js (export-event-summary state)) nil 2))

(defn clear-events!
  [store]
  (try
    (platform/local-storage-remove! storage-key)
    (catch :default _
      nil))
  (swap! store
         (fn [state]
           (-> state
               (assoc-in [:attribution :queue] [])
               (assoc-in [:attribution :status] nil)
               (assoc-in [:attribution :last-event-id] nil)
               (assoc-in [:attribution :event-count] 0)
               (assoc-in [:attribution :initialized?] true))))
  true)

(defn- download-event-summary!
  [store]
  (when-let [document (some-> js/globalThis .-document)]
    (let [payload (export-event-summary-json @store)
          blob (js/Blob. #js [payload] #js {:type "application/json"})
          object-url (js/URL.createObjectURL blob)
          link (.createElement document "a")]
      (set! (.-href link) object-url)
      (set! (.-download link)
            (str "hyperopen-attribution-events-" (platform/now-ms) ".json"))
      (.appendChild (.-body document) link)
      (.click link)
      (.remove link)
      (js/URL.revokeObjectURL object-url)
      true)))

(defn install-operator-api!
  "Expose a read-only, redacted event export for enterprise operators."
  [store]
  (let [api #js {:events (fn []
                           (clj->js (export-event-summary @store)))
                 :eventsJson (fn []
                               (export-event-summary-json @store))
                 :clear (fn []
                          (clear-events! store))
                 :download (fn []
                             (download-event-summary! store))}]
    (aset js/globalThis "HYPEROPEN_ATTRIBUTION" api)
    api))
