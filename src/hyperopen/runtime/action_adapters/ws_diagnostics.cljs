(ns hyperopen.runtime.action-adapters.ws-diagnostics
  (:require [hyperopen.runtime.state :as runtime-state]
            [hyperopen.websocket.diagnostics-actions :as diagnostics-actions]
            [hyperopen.websocket.health-runtime :as health-runtime]))

(defn- effective-now-ms
  [generated-at-ms]
  (health-runtime/effective-now-ms generated-at-ms))

(defn- action-deps []
  {:effective-now-ms effective-now-ms
   :reconnect-cooldown-ms runtime-state/reconnect-cooldown-ms})

(def toggle-ws-diagnostics diagnostics-actions/toggle-ws-diagnostics)

(defn close-ws-diagnostics
  [_]
  (diagnostics-actions/close-ws-diagnostics nil))

(defn handle-ws-diagnostics-keydown
  [_ key]
  (diagnostics-actions/handle-ws-diagnostics-keydown nil key))

(def toggle-ws-diagnostics-sensitive
  diagnostics-actions/toggle-ws-diagnostics-sensitive)

(defn ws-diagnostics-reconnect-now
  [state]
  (diagnostics-actions/ws-diagnostics-reconnect-now
   state
   (action-deps)))

(defn ws-diagnostics-copy
  [_]
  (diagnostics-actions/ws-diagnostics-copy nil))

(defn set-show-surface-freshness-cues
  [_ checked]
  (diagnostics-actions/set-show-surface-freshness-cues nil checked))

(def toggle-show-surface-freshness-cues
  diagnostics-actions/toggle-show-surface-freshness-cues)

(defn ws-diagnostics-reset-market-subscriptions
  ([state]
   (ws-diagnostics-reset-market-subscriptions state :manual))
  ([state source]
   (diagnostics-actions/ws-diagnostics-reset-market-subscriptions
    state
    source
    (action-deps))))

(defn ws-diagnostics-reset-orders-subscriptions
  ([state]
   (ws-diagnostics-reset-orders-subscriptions state :manual))
  ([state source]
   (diagnostics-actions/ws-diagnostics-reset-orders-subscriptions
    state
    source
    (action-deps))))

(defn ws-diagnostics-reset-all-subscriptions
  ([state]
   (ws-diagnostics-reset-all-subscriptions state :manual))
  ([state source]
   (diagnostics-actions/ws-diagnostics-reset-all-subscriptions
    state
    source
    (action-deps))))
