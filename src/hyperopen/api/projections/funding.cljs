(ns hyperopen.api.projections.funding
  (:require [hyperopen.api.errors :as api-errors]))

(defn- normalized-error
  [err]
  (api-errors/normalize-error err))

(defn begin-funding-comparison-load
  [state]
  (-> state
      (assoc-in [:funding-comparison-ui :loading?] true)
      (assoc-in [:funding-comparison :error] nil)
      (assoc-in [:funding-comparison :error-category] nil)))

(defn apply-funding-comparison-success
  [state rows]
  (let [rows* (if (sequential? rows) (vec rows) [])]
    (-> state
        (assoc-in [:funding-comparison :predicted-fundings] rows*)
        (assoc-in [:funding-comparison-ui :loading?] false)
        (assoc-in [:funding-comparison :error] nil)
        (assoc-in [:funding-comparison :error-category] nil)
        (assoc-in [:funding-comparison :loaded-at-ms] (.now js/Date)))))

(defn apply-funding-comparison-error
  [state err]
  (let [{:keys [message category]} (normalized-error err)]
    (-> state
        (assoc-in [:funding-comparison-ui :loading?] false)
        (assoc-in [:funding-comparison :error] message)
        (assoc-in [:funding-comparison :error-category] category))))
