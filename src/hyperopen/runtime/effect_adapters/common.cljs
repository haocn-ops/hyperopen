(ns hyperopen.runtime.effect-adapters.common
  (:require [hyperopen.platform :as platform]
            [hyperopen.runtime.app-effects :as app-effects]
            [hyperopen.wallet.agent-runtime :as agent-runtime]))

(defn- effect-handler-store-1
  [f]
  (fn [_ store arg]
    (f store arg)))

(defn- effect-handler-store-2
  [f]
  (fn [_ store arg1 arg2]
    (f store arg1 arg2)))

(defn- effect-handler-1
  [f]
  (fn [_ _ arg]
    (f arg)))

(defn- effect-handler-2
  [f]
  (fn [_ _ arg1 arg2]
    (f arg1 arg2)))

(def save
  (effect-handler-store-2 #'app-effects/save!))

(def save-many
  (effect-handler-store-1 #'app-effects/save-many!))

(def local-storage-set
  (effect-handler-2 #'app-effects/local-storage-set!))

(def local-storage-set-json
  (effect-handler-2 #'app-effects/local-storage-set-json!))

(def push-state
  (effect-handler-1 #'app-effects/push-state!))

(def replace-state
  (effect-handler-1 #'app-effects/replace-state!))

(defn schedule-animation-frame! [f]
  (platform/request-animation-frame! f))

(defn exchange-response-error
  [resp]
  (agent-runtime/exchange-response-error resp))

(defn runtime-error-message
  [err]
  (agent-runtime/runtime-error-message err))
