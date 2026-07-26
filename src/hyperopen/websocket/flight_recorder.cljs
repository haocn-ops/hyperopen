(ns hyperopen.websocket.flight-recorder
  (:require [hyperopen.platform :as platform]
            [hyperopen.websocket.diagnostics-sanitize :as diagnostics-sanitize]))

(def ^:private default-capacity
  4000)

(defn- normalize-capacity
  [capacity]
  (let [capacity* (or capacity default-capacity)]
    (if (and (number? capacity*)
             (pos-int? (int capacity*)))
      (int capacity*)
      default-capacity)))

(defn- base-state
  [capacity]
  {:capacity (normalize-capacity capacity)
   :next-seq 1
   :dropped-count 0
   :session nil
   :events []})

(defn create-recorder
  [{:keys [capacity now-ms]
    :or {capacity default-capacity
         now-ms platform/now-ms}}]
  {:state (atom (base-state capacity))
   :now-ms now-ms})

(defn clear-recorder!
  [recorder]
  (when recorder
    (let [{:keys [capacity]} @(:state recorder)]
      (reset! (:state recorder) (base-state capacity))
      true)))

(defn start-session!
  [recorder session-metadata]
  (when recorder
    (let [now-ms-fn (or (:now-ms recorder) platform/now-ms)
          started-at-ms (now-ms-fn)]
      (swap! (:state recorder)
             (fn [state]
               (-> (base-state (:capacity state))
                   (assoc :session {:started-at-ms started-at-ms
                                    :metadata (or session-metadata {})}))))
      true)))

(defn- append-event
  [state event]
  (let [capacity (:capacity state default-capacity)
        next-event (assoc event :seq (:next-seq state 1))
        next-events (conj (vec (:events state [])) next-event)
        overflow (max 0 (- (count next-events) capacity))
        trimmed-events (if (pos? overflow)
                         (subvec next-events overflow)
                         next-events)]
    (-> state
        (assoc :events trimmed-events
               :next-seq (inc (:next-seq state 1)))
        (update :dropped-count (fnil + 0) overflow))))

(defn- record-event!
  [recorder kind payload]
  (when recorder
    (let [now-ms-fn (or (:now-ms recorder) platform/now-ms)]
      (swap! (:state recorder)
             append-event
             {:kind kind
              :at-ms (now-ms-fn)
              :payload payload})
      true)))

(defn record-runtime-msg!
  [recorder msg]
  (record-event! recorder
                 :runtime/msg
                 {:msg-type (:msg/type msg)
                  :msg msg}))

(defn record-runtime-effects!
  [recorder msg effects]
  (record-event! recorder
                 :runtime/effects
                 {:msg-type (:msg/type msg)
                  :effects (vec (or effects []))
                  :effect-types (mapv :fx/type (vec (or effects [])))}))

(defn record-runtime-drop!
  [recorder payload]
  (record-event! recorder :runtime/drop payload))

(defn snapshot
  [recorder]
  (when recorder
    (let [{:keys [capacity dropped-count session events]} @(:state recorder)]
      {:capacity capacity
       :dropped-count dropped-count
       :session session
       :event-count (count events)
       :events (vec events)})))

(defn redacted-snapshot
  [recorder]
  (when-let [raw (snapshot recorder)]
    (diagnostics-sanitize/sanitize-value :redact raw)))

(defn runtime-message-seq
  [recording]
  (->> (get recording :events [])
       (filter #(= :runtime/msg (:kind %)))
       (mapv #(get-in % [:payload :msg]))))

(defn replay-runtime-messages
  [{:keys [recording reducer initial-state]}]
  (assert (fn? reducer) "replay-runtime-messages requires a reducer function")
  (let [messages (runtime-message-seq recording)
        initial-state* (if (some? initial-state)
                         initial-state
                         (get-in recording [:session :metadata :initial-state]))
        {:keys [state trace]}
        (reduce (fn [{:keys [state trace]} msg]
                  (let [result (reducer state msg)
                        next-state (or (:next-state result) (:state result) state)
                        effects (vec (or (:effects result) []))]
                    {:state next-state
                     :trace (conj trace
                                  {:msg-type (:msg/type msg)
                                   :effect-types (mapv :fx/type effects)
                                   :effects effects})}))
                {:state initial-state*
                 :trace []}
                messages)]
    {:message-count (count messages)
     :step-count (count trace)
     :final-state state
     :trace trace}))
