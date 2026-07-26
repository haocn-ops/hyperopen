(ns hyperopen.portfolio.optimizer.application.black-litterman-preview
  (:require [hyperopen.portfolio.optimizer.application.engine.context :as engine-context]
            [hyperopen.portfolio.optimizer.application.instrument-labels :as instrument-labels]))

(defn- instrument-ids
  [request]
  (mapv :instrument-id (:universe request)))

(defn- prior-returns-by-instrument
  [request]
  (engine-context/baseline-expected-return-inputs-by-instrument request))

(defn- posterior-returns-by-instrument
  [request]
  (engine-context/expected-return-inputs-by-instrument request))

(defn- preview-rows
  [request]
  (let [prior (prior-returns-by-instrument request)
        posterior (posterior-returns-by-instrument request)
        ids (instrument-ids request)
        labels (instrument-labels/labels-by-instrument (:universe request) ids)]
    (mapv (fn [instrument-id]
            {:instrument-id instrument-id
             :label (get labels instrument-id)
             :prior-return (get prior instrument-id)
             :posterior-return (get posterior instrument-id)})
          ids)))

(defn build-preview
  [readiness]
  (let [request (:request readiness)]
    (cond
      (not= :ready (:status readiness))
      {:status :unavailable
       :reason :no-eligible-request}

      (not= :black-litterman (get-in request [:return-model :kind]))
      {:status :unavailable
       :reason :not-black-litterman}

      (empty? (get-in request [:return-model :views]))
      {:status :empty
       :view-count 0
       :rows (preview-rows request)}

      :else
      {:status :ready
       :view-count (count (get-in request [:return-model :views]))
       :rows (preview-rows request)})))
