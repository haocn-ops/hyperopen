(ns hyperopen.websocket.diagnostics-runtime
  (:require [hyperopen.websocket.diagnostics.policy :as diagnostics-policy]))

(defn record-info-rate-limit
  "Folds an info-endpoint rate-limit (HTTP 429) event into the
   `[:websocket-ui :info-rate-limit]` summary the diagnostics drawer reads.
   Keeps the max cooldown deadline seen, the authoritative cumulative count
   (falling back to a local increment), and the most recent event time."
  [prev {:keys [cooldown-until-ms at-ms]
         rate-limited-count :count}]
  (let [prev* (or prev {})]
    {:count (or rate-limited-count
                (inc (or (:count prev*) 0)))
     :until-ms (max (or (:until-ms prev*) 0)
                    (or cooldown-until-ms 0))
     :last-at-ms (or at-ms (:last-at-ms prev*))}))

(defn install-info-rate-limit-listener!
  "Wires the info-client's store-agnostic rate-limit callback to fold events into
   app-state via a plain swap! (same pattern as the reconnect/reset counters)."
  [store set-on-rate-limit!]
  (when (fn? set-on-rate-limit!)
    (set-on-rate-limit!
     (fn [event]
       (swap! store update-in [:websocket-ui :info-rate-limit]
              record-info-rate-limit
              event)))))

(defn- reset-group-match?
  [stream group]
  (case group
    :market_data (= :market_data (:group stream))
    :orders_oms (= :orders_oms (:group stream))
    :all true
    false))

(defn- reset-target-descriptors
  [health group]
  (->> (get health :streams {})
       vals
       (filter (fn [stream]
                 (and (:subscribed? stream)
                      (map? (:descriptor stream))
                      (reset-group-match? stream group))))
       (map :descriptor)
       distinct
       (sort-by pr-str)
       vec))

(defn- reset-event
  [group source]
  (if (= :auto-recover source)
    :auto-recover-market
    (case group
      :market_data :reset-market
      :orders_oms :reset-oms
      :all :reset-all
      :reset-unknown)))

(defn ws-reset-subscriptions!
  [{:keys [store
           group
           source
           get-health-snapshot
           effective-now-ms
           reset-subscriptions-cooldown-ms
           send-message!
           append-diagnostics-event!]}]
  (let [state @store
        health (get-health-snapshot)
        generated-at-ms (or (:generated-at-ms health) 0)
        now-ms (effective-now-ms generated-at-ms)
        blocked? (diagnostics-policy/reset-blocked? state health now-ms)
        group-key (if (= group :all) :all group)
        descriptors (reset-target-descriptors health group)]
    (when (and (not blocked?)
               (seq descriptors))
      (swap! store assoc-in [:websocket-ui :reset-in-progress?] true)
      (try
        (doseq [descriptor descriptors]
          (send-message! {:method "unsubscribe"
                          :subscription descriptor}))
        (doseq [descriptor descriptors]
          (send-message! {:method "subscribe"
                          :subscription descriptor}))
        (finally
          (swap! store assoc-in [:websocket-ui :reset-in-progress?] false)))
      (swap! store
             (fn [state*]
               (-> state*
                   (assoc-in [:websocket-ui :reset-cooldown-until-ms]
                             (+ now-ms reset-subscriptions-cooldown-ms))
                   (update-in [:websocket-ui :reset-counts group-key] (fnil inc 0)))))
      (append-diagnostics-event! store
                                 (reset-event group source)
                                 now-ms
                                 {:count (count descriptors)
                                  :source source}))))
