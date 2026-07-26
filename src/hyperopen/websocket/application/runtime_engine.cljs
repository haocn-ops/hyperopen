(ns hyperopen.websocket.application.runtime-engine
  (:require [cljs.core.async :as async :refer [<! >! chan close! put!]]
            [hyperopen.telemetry :as telemetry]
            [hyperopen.platform :as platform])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(defn safe-put! [channel value]
  (when channel
    (try
      (boolean (put! channel value))
      (catch :default _
        false))))

(defn- safe-callback!
  [callback & args]
  (when callback
    (try
      (apply callback args)
      (catch :default _
        nil))))

(defn start-engine!
  [{:keys [initial-state
           reducer
           interpret-effect!
           context
           mailbox-size
           effects-size
           now-ms
           record-runtime-msg!
           record-runtime-effects!
           record-runtime-drop!]
    :or {mailbox-size 4096
         effects-size 4096
         now-ms platform/now-ms}}]
  (let [mailbox-ch (chan (async/buffer mailbox-size))
        effects-ch (chan (async/buffer effects-size))
        state (atom initial-state)
        closed? (atom false)
        record-msg! #(safe-callback! record-runtime-msg! %)
        record-effects! #(safe-callback! record-runtime-effects! %1 %2)
        record-drop! #(safe-callback! record-runtime-drop! %)
        dispatch! (fn [msg]
                    (let [msg* (if (number? (:ts msg))
                                 msg
                                 (assoc msg :ts (now-ms)))
                          _ (record-msg! msg*)
                          enqueued? (safe-put! mailbox-ch msg*)]
                      (when-not enqueued?
                        (record-drop! {:reason :mailbox-enqueue-failure
                                       :message msg*}))
                      enqueued?))]
    (go-loop []
      (when-let [msg (<! mailbox-ch)]
        (try
          (let [result (reducer @state msg)
                resolved-state (or (:next-state result) (:state result) @state)
                effects (:effects result)]
            (reset! state resolved-state)
            (record-effects! msg effects)
            (doseq [fx (or effects [])]
              (>! effects-ch fx)))
          (catch :default e
            (let [dead-letter {:fx/type :fx/dead-letter
                               :reason :engine-reducer-failure
                               :error e
                               :message msg}]
              (record-effects! msg [dead-letter])
              (>! effects-ch dead-letter))))
        (recur)))
    (go-loop []
      (when-let [fx (<! effects-ch)]
        (try
          (interpret-effect! (assoc context
                                    :dispatch! dispatch!
                                    :get-state (fn [] @state))
                             fx)
          (catch :default e
            (record-drop! {:reason :interpreter-failure
                           :effect fx
                           :error (str e)})
            (telemetry/emit! :websocket/runtime-interpreter-failure
                             {:error (str e)
                              :effect fx})))
        (recur)))
    {:mailbox-ch mailbox-ch
     :effects-ch effects-ch
     :state state
     :dispatch! dispatch!
     :stop! (fn []
              (when-not @closed?
                (reset! closed? true)
                (close! mailbox-ch)
                (close! effects-ch)))}))

(defn dispatch! [engine msg]
  (when-let [f (:dispatch! engine)]
    (f msg)))

(defn stop-engine! [engine]
  (when-let [f (:stop! engine)]
    (f)))
