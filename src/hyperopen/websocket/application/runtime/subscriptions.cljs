(ns hyperopen.websocket.application.runtime.subscriptions
  (:require [hyperopen.websocket.domain.model :as model]
            [hyperopen.websocket.health :as health]))

(defn refresh-expected-traffic
  [state]
  (assoc-in state
            [:transport :expected-traffic?]
            (health/transport-expected-traffic? (:streams state))))

(defn- baseline-stream-entry
  [state subscription]
  (let [topic (:type subscription)]
    {:descriptor subscription
     :topic topic
     :group (model/topic->group topic)
     :stale-threshold-ms (health/stream-stale-threshold-ms (:config state) topic)
     :status :idle
     :status-pending-status nil
     :status-pending-count 0
     :last-seq nil
     :seq-gap-detected? false
     :seq-gap-count 0
     :last-gap nil}))

(defn- mark-subscribe
  [state subscription subscribed-at-ms]
  (let [sub-key (model/subscription-key subscription)
        existing (get-in state [:streams sub-key] {})
        next-stream (merge existing
                           (baseline-stream-entry state subscription)
                           {:subscribed? true
                            :subscribed-at-ms subscribed-at-ms
                            :rejected-at-ms nil
                            :first-payload-at-ms nil
                            :last-payload-at-ms nil
                            :message-count 0
                            :status :idle
                            :status-pending-status nil
                            :status-pending-count 0
                            :last-seq nil
                            :seq-gap-detected? false
                            :seq-gap-count 0
                            :last-gap nil})]
    (assoc-in state [:streams sub-key] next-stream)))

(defn- mark-unsubscribe
  [state subscription]
  (let [sub-key (model/subscription-key subscription)
        existing (get-in state [:streams sub-key] {})
        next-stream (merge existing
                           (baseline-stream-entry state subscription)
                           {:subscribed? false
                            :subscribed-at-ms nil
                            :rejected-at-ms nil
                            :first-payload-at-ms nil
                            :status :idle
                            :status-pending-status nil
                            :status-pending-count 0
                            :last-seq nil
                            :seq-gap-detected? false
                            :seq-gap-count 0
                            :last-gap nil})]
    (assoc-in state [:streams sub-key] next-stream)))

(defn apply-stream-intent
  [state data at-ms]
  (let [method (model/normalize-method (:method data))
        subscription (:subscription data)]
    (if (map? subscription)
      (case method
        "subscribe" (mark-subscribe state subscription at-ms)
        "unsubscribe" (mark-unsubscribe state subscription)
        state)
      state)))

(defn replay-subscriptions-as-active
  [state at-ms]
  (reduce (fn [acc subscription]
            (mark-subscribe acc subscription at-ms))
          state
          (vals (:desired-subscriptions state))))

(defn- update-stream-payload
  [stream recv-at-ms]
  (if (and (map? stream) (:subscribed? stream))
    (-> stream
        (assoc :first-payload-at-ms (or (:first-payload-at-ms stream) recv-at-ms)
               :last-payload-at-ms recv-at-ms)
        (update :message-count (fnil inc 0)))
    stream))

(defn- update-stream-seq
  [stream seq-value recv-at-ms]
  (if (and (map? stream)
           (:subscribed? stream)
           (number? seq-value))
    (let [last-seq (:last-seq stream)
          gap? (and (number? last-seq)
                    (> seq-value (inc last-seq)))
          next-last-seq (if (number? last-seq)
                          (max last-seq seq-value)
                          seq-value)]
      (cond-> (assoc stream :last-seq next-last-seq)
        gap?
        (-> (assoc :seq-gap-detected? true
                   :last-gap {:expected (inc last-seq)
                              :actual seq-value
                              :at-ms recv-at-ms})
            (update :seq-gap-count (fnil inc 0)))))
    stream))

(defn record-stream-payload
  [state envelope recv-at-ms]
  (let [stream-keys (health/match-stream-keys (:streams state) envelope)
        seq-value (get-in envelope [:payload :seq])]
    (reduce (fn [acc sub-key]
              (if (contains? (:streams acc) sub-key)
                (update-in acc
                           [:streams sub-key]
                           (fn [stream]
                             (-> stream
                                 (update-stream-payload recv-at-ms)
                                 (update-stream-seq seq-value recv-at-ms))))
                acc))
            state
            stream-keys)))
