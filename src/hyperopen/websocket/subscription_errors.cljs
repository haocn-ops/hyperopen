(ns hyperopen.websocket.subscription-errors
  "Registers the wire `error` channel handler. Schema-level subscription
  rejections publish :evt/subscription-rejected so the runtime downgrades the
  stream's health and `usable?` gates fall back to REST. Before this handler
  existed, a provider-side topic removal (webData2, 2026-07) was invisible:
  streams stayed optimistically 'subscribed' and their buckets silently never
  hydrated."
  (:require [hyperopen.platform :as platform]
            [hyperopen.telemetry :as telemetry]
            [hyperopen.websocket.acl.subscription-errors :as acl-errors]
            [hyperopen.websocket.client :as ws-client]))

(defn create-error-handler
  ([]
   (create-error-handler {:publish-control! ws-client/publish-control!
                          :now-ms-fn platform/now-ms
                          :log-fn telemetry/log!}))
  ([{:keys [publish-control! now-ms-fn log-fn]}]
   (fn [msg]
     (when (= "error" (:channel msg))
       (let [text (:data msg)
             {:keys [kind subscription]} (acl-errors/classify-error-text text)]
         (log-fn "WebSocket error frame" kind ":" text)
         (when (= :rejected kind)
           (publish-control!
            {:msg/type :evt/subscription-rejected
             :ts (now-ms-fn)
             :subscription subscription})))))))

(defn init! []
  (ws-client/register-handler! "error" (create-error-handler))
  (telemetry/log! "WebSocket subscription-error handler initialized"))
