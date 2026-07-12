(ns hyperopen.margin-rec.watcher
  "Store watcher that keeps isolated-margin recommendations current in the
  background. It observes only a cheap decision-input fingerprint (isolated
  positions bucketed by mark, candle arrival stamps, settings, intents,
  recommendation stamps) and dispatches a debounced sync + intent-processing
  pass when it changes. Live mark ticks inside a bucket do not fire it, and a
  sync that emits no effects leaves the fingerprint unchanged, so the loop is
  self-quiescing."
  (:require [hyperopen.margin-rec.state :as margin-rec-state]
            [hyperopen.platform :as platform]))

(def ^:private watch-key ::margin-rec)

(def default-debounce-ms 400)

(defn install-margin-rec-watcher!
  [{:keys [store dispatch! now-ms-fn set-timeout-fn debounce-ms]
    :or {now-ms-fn platform/now-ms
         set-timeout-fn platform/set-timeout!
         debounce-ms default-debounce-ms}}]
  (let [pending? (atom false)
        last-fingerprint (atom ::none)
        fire! (fn []
                (reset! pending? false)
                (let [now (now-ms-fn)]
                  (dispatch! store nil [[:actions/margin-rec-sync now]
                                        [:actions/margin-rec-process-intents now]])))]
    (remove-watch store watch-key)
    (add-watch store watch-key
               (fn [_ _ _ new-state]
                 (let [fingerprint (margin-rec-state/watch-fingerprint new-state)]
                   (when (not= fingerprint @last-fingerprint)
                     (reset! last-fingerprint fingerprint)
                     (when-not @pending?
                       (reset! pending? true)
                       (set-timeout-fn fire! debounce-ms))))))
    store))
