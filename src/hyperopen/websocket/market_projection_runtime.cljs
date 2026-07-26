(ns hyperopen.websocket.market-projection-runtime
  (:require [clojure.string :as str]
            [hyperopen.platform :as platform]
            [hyperopen.telemetry :as telemetry]))

(defonce market-projection-runtime
  (atom {:stores {}}))

(def ^:private scheduled-frame-handle
  ::scheduled-frame-handle)

(def ^:private flush-duration-window-size
  50)

(defn- default-store-telemetry
  []
  {:store-id nil
   :queued-total 0
   :overwrite-total 0
   :pending-overwrite-count 0
   :flush-count 0
   :max-pending-depth 0
   :last-enqueue-at-ms nil
   :last-frame-scheduled-at-ms nil
   :last-flush-at-ms nil
   :last-flush-duration-ms nil
   :last-queue-wait-ms nil
   :flush-duration-samples []
   :queue-wait-samples []
   :p95-flush-duration-ms nil
   :p95-queue-wait-ms nil})

(defn- default-apply-store!
  [store apply-update-fn]
  (let [state @store
        next-state (apply-update-fn state)]
    (when (not= state next-state)
      (reset! store next-state))))

(defn- default-emit-fn
  [event attrs]
  (telemetry/emit! event attrs))

(defn- ordered-pending-updates
  [pending]
  (sort-by (comp pr-str key) pending))

(defn- store-runtime-entry
  [state store]
  (let [entry (get-in state [:stores store] {})]
    {:pending (or (:pending entry) {})
     :frame-handle (:frame-handle entry)
     :telemetry (merge (default-store-telemetry)
                       (:telemetry entry))}))

(defn- inferred-store-id
  [store]
  (cond
    (nil? store) "market-projection/store-unknown"
    (string? store) store
    (keyword? store) (name store)
    (symbol? store) (str store)
    (satisfies? IDeref store) (str "market-projection/store-" (hash store))
    :else (str "market-projection/store-" (hash store))))

(defn- invalid-legacy-store-id?
  [store-id]
  (and (string? store-id)
       (str/starts-with? store-id "#object[")))

(defn- store-id-value
  [store explicit-store-id telemetry]
  (let [existing-store-id (:store-id telemetry)
        sanitized-existing-store-id (when-not (invalid-legacy-store-id? existing-store-id)
                                      existing-store-id)]
    (or explicit-store-id
        sanitized-existing-store-id
        (inferred-store-id store))))

(defn- bounded-conj
  [values value max-size]
  (let [next-values (conj (vec (or values [])) value)
        trim-start (max 0 (- (count next-values) max-size))]
    (subvec next-values trim-start)))

(defn- percentile
  [samples pct]
  (when (seq samples)
    (let [sorted (vec (sort samples))
          pct* (max 0 (min 100 pct))
          raw-index (js/Math.ceil (* (/ pct* 100) (count sorted)))
          idx (-> raw-index
                  dec
                  (max 0)
                  (min (dec (count sorted)))
                  int)]
      (nth sorted idx))))

(defn- telemetry-summary
  [store {:keys [pending frame-handle telemetry]}]
  (let [telemetry* (merge (default-store-telemetry) telemetry)]
    {:store-id (store-id-value store nil telemetry*)
     :pending-count (count (or pending {}))
     :frame-scheduled? (some? frame-handle)
     :queued-total (:queued-total telemetry*)
     :overwrite-total (:overwrite-total telemetry*)
     :pending-overwrite-count (:pending-overwrite-count telemetry*)
     :flush-count (:flush-count telemetry*)
     :max-pending-depth (:max-pending-depth telemetry*)
     :p95-flush-duration-ms (:p95-flush-duration-ms telemetry*)
     :p95-queue-wait-ms (:p95-queue-wait-ms telemetry*)
     :flush-duration-sample-count (count (:flush-duration-samples telemetry*))
     :queue-wait-sample-count (count (:queue-wait-samples telemetry*))
     :flush-duration-window-size flush-duration-window-size
     :last-enqueue-at-ms (:last-enqueue-at-ms telemetry*)
     :last-frame-scheduled-at-ms (:last-frame-scheduled-at-ms telemetry*)
     :last-flush-at-ms (:last-flush-at-ms telemetry*)
     :last-flush-duration-ms (:last-flush-duration-ms telemetry*)
     :last-queue-wait-ms (:last-queue-wait-ms telemetry*)}))

(defn- drain-store-pending!
  [store]
  (let [drained (volatile! {:pending {}
                            :telemetry (default-store-telemetry)})]
    (swap! market-projection-runtime
           (fn [state]
             (if (contains? (:stores state) store)
               (let [{:keys [pending telemetry]} (store-runtime-entry state store)]
                 (vreset! drained {:pending pending
                                   :telemetry telemetry})
                 (assoc-in state [:stores store] {:pending {}
                                                  :frame-handle nil
                                                  :telemetry telemetry}))
               state)))
    @drained))

(defn- finalize-flush-telemetry!
  [store {:keys [flush-end-ms flush-duration-ms queue-wait-ms]}]
  (let [summary* (volatile! nil)]
    (swap! market-projection-runtime
           (fn [state]
             (let [{:keys [pending frame-handle telemetry]} (store-runtime-entry state store)
                   flush-duration-samples (bounded-conj (:flush-duration-samples telemetry)
                                                        flush-duration-ms
                                                        flush-duration-window-size)
                   queue-wait-samples (if (number? queue-wait-ms)
                                        (bounded-conj (:queue-wait-samples telemetry)
                                                      queue-wait-ms
                                                      flush-duration-window-size)
                                        (:queue-wait-samples telemetry))
                   telemetry* (-> telemetry
                                  (update :flush-count inc)
                                  (assoc :pending-overwrite-count 0
                                         :last-flush-at-ms flush-end-ms
                                         :last-flush-duration-ms flush-duration-ms
                                         :last-queue-wait-ms queue-wait-ms
                                         :flush-duration-samples flush-duration-samples
                                         :queue-wait-samples queue-wait-samples
                                         :p95-flush-duration-ms (percentile flush-duration-samples 95)
                                         :p95-queue-wait-ms (percentile queue-wait-samples 95)))
                   entry* {:pending pending
                           :frame-handle frame-handle
                           :telemetry telemetry*}]
               (vreset! summary* (telemetry-summary store entry*))
               (assoc-in state [:stores store] entry*))))
    @summary*))

(defn flush-store-updates!
  [{:keys [store apply-store! now-ms-fn emit-fn]
    :or {apply-store! default-apply-store!
         now-ms-fn platform/now-ms
         emit-fn default-emit-fn}}]
  (when store
    (let [flush-start-ms (now-ms-fn)
          {:keys [pending telemetry]} (drain-store-pending! store)]
      (when (seq pending)
        (apply-store!
         store
         (fn [state]
           (reduce (fn [acc [_ apply-update-fn]]
                     (apply-update-fn acc))
                   state
                   (ordered-pending-updates pending))))
        (let [flush-end-ms (now-ms-fn)
              flush-duration-ms (max 0 (- flush-end-ms flush-start-ms))
              queue-wait-ms (when-let [scheduled-at-ms (:last-frame-scheduled-at-ms telemetry)]
                              (max 0 (- flush-start-ms scheduled-at-ms)))
              overwrite-count (or (:pending-overwrite-count telemetry) 0)
              summary (finalize-flush-telemetry! store {:flush-end-ms flush-end-ms
                                                        :flush-duration-ms flush-duration-ms
                                                        :queue-wait-ms queue-wait-ms})]
          (emit-fn :websocket/market-projection-flush
                   {:store-id (:store-id summary)
                    :pending-count (count pending)
                    :overwrite-count overwrite-count
                    :flush-duration-ms flush-duration-ms
                    :queue-wait-ms queue-wait-ms
                    :flush-count (:flush-count summary)
                    :max-pending-depth (:max-pending-depth summary)
                    :p95-flush-duration-ms (:p95-flush-duration-ms summary)
                    :queued-total (:queued-total summary)
                    :overwrite-total (:overwrite-total summary)
                    :at-ms flush-end-ms}))))))

(defn queue-market-projection!
  [{:keys [store
           store-id
           coalesce-key
           apply-update-fn
           schedule-animation-frame!
           apply-store!
           now-ms-fn
           emit-fn]
    :or {schedule-animation-frame! platform/request-animation-frame!
         apply-store! default-apply-store!
         now-ms-fn platform/now-ms
         emit-fn default-emit-fn}}]
  (when (and store
             (some? coalesce-key)
             (fn? apply-update-fn))
    (let [enqueue-at-ms (now-ms-fn)
          needs-frame-schedule? (volatile! false)]
      (swap! market-projection-runtime
             (fn [state]
               (let [{:keys [pending frame-handle telemetry]} (store-runtime-entry state store)
                     had-pending-key? (contains? pending coalesce-key)
                     pending* (assoc pending coalesce-key apply-update-fn)
                     pending-count (count pending*)
                     should-schedule? (nil? frame-handle)
                     telemetry-base (-> telemetry
                                        (assoc :store-id (store-id-value store store-id telemetry)
                                               :last-enqueue-at-ms enqueue-at-ms
                                               :max-pending-depth (max (or (:max-pending-depth telemetry) 0)
                                                                       pending-count))
                                        (update :queued-total inc))
                     telemetry-with-overwrite (if had-pending-key?
                                                (-> telemetry-base
                                                    (update :overwrite-total inc)
                                                    (update :pending-overwrite-count inc))
                                                telemetry-base)
                     telemetry* (if should-schedule?
                                  (assoc telemetry-with-overwrite
                                         :last-frame-scheduled-at-ms enqueue-at-ms)
                                  telemetry-with-overwrite)]
                 (vreset! needs-frame-schedule? should-schedule?)
                 (assoc-in state [:stores store]
                           {:pending pending*
                            :frame-handle (if should-schedule?
                                            scheduled-frame-handle
                                            frame-handle)
                            :telemetry telemetry*}))))
      (when @needs-frame-schedule?
        (let [frame-handle (schedule-animation-frame!
                            (fn [_]
                              (flush-store-updates! {:store store
                                                     :apply-store! apply-store!
                                                     :now-ms-fn now-ms-fn
                                                     :emit-fn emit-fn})))]
          (swap! market-projection-runtime
                 (fn [state]
                   (if (= scheduled-frame-handle
                          (get-in state [:stores store :frame-handle]))
                     (assoc-in state [:stores store :frame-handle] frame-handle)
                     state))))))))

(defn market-projection-telemetry-snapshot
  []
  {:stores (->> (:stores @market-projection-runtime)
                (map (fn [[store entry]]
                       (telemetry-summary store entry)))
                (sort-by :store-id)
                vec)})

(defn reset-market-projection-runtime!
  []
  (reset! market-projection-runtime {:stores {}}))
