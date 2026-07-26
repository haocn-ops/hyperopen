(ns hyperopen.websocket.diagnostics-actions
  (:require [hyperopen.websocket.diagnostics.policy :as diagnostics-policy]))

(defn toggle-ws-diagnostics
  [state]
  (let [open? (not (boolean (get-in state [:websocket-ui :diagnostics-open?])))]
    (cond-> [[:effects/save-many [[[:websocket-ui :diagnostics-open?] open?]
                                  [[:websocket-ui :reveal-sensitive?] false]
                                  [[:websocket-ui :copy-status] nil]]]]
      open?
      (conj [:effects/refresh-websocket-health]))))

(defn close-ws-diagnostics
  [_]
  [[:effects/save-many [[[:websocket-ui :diagnostics-open?] false]
                        [[:websocket-ui :reveal-sensitive?] false]
                        [[:websocket-ui :copy-status] nil]]]])

(defn handle-ws-diagnostics-keydown
  [state key]
  (if (= key "Escape")
    (close-ws-diagnostics state)
    []))

(defn toggle-ws-diagnostics-sensitive
  [state]
  (if (boolean (get-in state [:websocket-ui :reveal-sensitive?]))
    [[:effects/save [:websocket-ui :reveal-sensitive?] false]]
    [[:effects/confirm-ws-diagnostics-reveal]]))

(defn- reconnect-blocked?
  [state effective-now-ms]
  (let [transport-state (get-in state [:websocket :health :transport :state])
        generated-at-ms (or (get-in state [:websocket :health :generated-at-ms]) 0)
        now-ms (effective-now-ms generated-at-ms)
        health {:transport {:state transport-state}}]
    (diagnostics-policy/reconnect-blocked?
     state
     health
     now-ms)))

(defn ws-diagnostics-reconnect-now
  [state {:keys [effective-now-ms reconnect-cooldown-ms]}]
  (if (reconnect-blocked? state effective-now-ms)
    []
    (let [generated-at-ms (or (get-in state [:websocket :health :generated-at-ms]) 0)
          now-ms (effective-now-ms generated-at-ms)]
      [[:effects/save-many [[[:websocket-ui :diagnostics-open?] false]
                            [[:websocket-ui :reveal-sensitive?] false]
                            [[:websocket-ui :copy-status] nil]]]
       [:effects/save [:websocket-ui :reconnect-cooldown-until-ms]
        (+ now-ms reconnect-cooldown-ms)]
       [:effects/reconnect-websocket]])))

(defn ws-diagnostics-copy
  [_]
  [[:effects/copy-websocket-diagnostics]])

(defn set-show-surface-freshness-cues
  [_ checked]
  [[:effects/save [:websocket-ui :show-surface-freshness-cues?] (boolean checked)]])

(defn toggle-show-surface-freshness-cues
  [state]
  [[:effects/save [:websocket-ui :show-surface-freshness-cues?]
    (not (boolean (get-in state [:websocket-ui :show-surface-freshness-cues?] false)))]])

(defn- reset-blocked?
  [state effective-now-ms]
  (let [transport-state (get-in state [:websocket :health :transport :state])
        generated-at-ms (or (get-in state [:websocket :health :generated-at-ms]) 0)
        now-ms (effective-now-ms generated-at-ms)
        health {:transport {:state transport-state}}]
    (diagnostics-policy/reset-blocked?
     state
     health
     now-ms)))

(defn ws-diagnostics-reset-subscriptions
  [state group source {:keys [effective-now-ms]}]
  (if (reset-blocked? state effective-now-ms)
    []
    [[:effects/ws-reset-subscriptions {:group group
                                       :source source}]]))

(defn ws-diagnostics-reset-market-subscriptions
  ([state config]
   (ws-diagnostics-reset-market-subscriptions state :manual config))
  ([state source config]
   (ws-diagnostics-reset-subscriptions state :market_data source config)))

(defn ws-diagnostics-reset-orders-subscriptions
  ([state config]
   (ws-diagnostics-reset-orders-subscriptions state :manual config))
  ([state source config]
   (ws-diagnostics-reset-subscriptions state :orders_oms source config)))

(defn ws-diagnostics-reset-all-subscriptions
  ([state config]
   (ws-diagnostics-reset-all-subscriptions state :manual config))
  ([state source config]
   (ws-diagnostics-reset-subscriptions state :all source config)))
