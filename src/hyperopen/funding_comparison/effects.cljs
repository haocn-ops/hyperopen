(ns hyperopen.funding-comparison.effects
  (:require [hyperopen.api.promise-effects :as promise-effects]
            [hyperopen.funding-comparison.actions :as funding-actions]))

(defn api-fetch-predicted-fundings!
  [{:keys [store
           request-predicted-fundings!
           begin-funding-comparison-load
           apply-funding-comparison-success
           apply-funding-comparison-error
           opts]}]
  (let [opts* (or opts {})
        route-gate-disabled? (true? (:skip-route-gate? opts*))
        route-active? (funding-actions/funding-comparison-route?
                       (get-in @store [:router :path]))]
    (if (or route-gate-disabled?
            route-active?)
      (let [request-opts (dissoc opts* :skip-route-gate?)]
        (swap! store begin-funding-comparison-load)
        (-> (request-predicted-fundings! request-opts)
            (.then (promise-effects/apply-success-and-return
                    store
                    apply-funding-comparison-success))
            (.catch (promise-effects/apply-error-and-reject
                     store
                     apply-funding-comparison-error))))
      (js/Promise.resolve nil))))
