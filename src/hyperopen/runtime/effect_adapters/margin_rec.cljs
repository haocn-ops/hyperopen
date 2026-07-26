(ns hyperopen.runtime.effect-adapters.margin-rec
  "IO boundary for isolated-margin recommendations: the low-priority fills
  fetch and the chunked engine run. The simulation code itself lives in the
  lazy :margin_rec shadow module (hyperopen.margin-rec.engine-module) so none
  of it rides the :main bundle."
  (:require [hyperopen.api.default :as api]
            [hyperopen.margin-rec-modules :as margin-rec-modules]
            [hyperopen.margin-rec.state :as margin-rec-state]
            [hyperopen.platform :as platform]
            [hyperopen.telemetry :as telemetry]))

(defn make-margin-rec-fetch-fills
  [{:keys [request-user-fills! now-ms-fn log-fn]
    :or {request-user-fills! api/request-user-fills!
         now-ms-fn platform/now-ms
         log-fn telemetry/log!}}]
  (fn [_ store & [address]]
    (log-fn "Fetching user fills for margin recommendations" address)
    (-> (request-user-fills! address {:priority :low})
        (.then (fn [rows]
                 (swap! store
                        margin-rec-state/apply-fills
                        address
                        (if (sequential? rows) rows [])
                        (now-ms-fn))))
        (.catch (fn [error]
                  (log-fn "Margin-rec fills fetch failed" error)
                  (swap! store margin-rec-state/apply-fills-error address))))))

(def margin-rec-fetch-fills-effect
  (make-margin-rec-fetch-fills {}))

(defn make-margin-rec-compute
  [{:keys [load-engine! now-ms-fn log-fn]
    :or {load-engine! margin-rec-modules/load-margin-rec-engine-module!
         now-ms-fn platform/now-ms
         log-fn telemetry/log!}}]
  (fn [_ store & [job]]
    (swap! store margin-rec-state/apply-computing job (now-ms-fn))
    (-> (load-engine!)
        (.then (fn [{:keys [compute-recommendation-async!]}]
                 (compute-recommendation-async! (:inputs job))))
        (.then (fn [result]
                 (swap! store margin-rec-state/apply-result job result (now-ms-fn))))
        (.catch (fn [error]
                  (log-fn "Margin-rec compute failed" (:key job) error)
                  (swap! store
                         margin-rec-state/apply-compute-error
                         job
                         (str error)
                         (now-ms-fn)))))))

(def margin-rec-compute-effect
  (make-margin-rec-compute {}))
