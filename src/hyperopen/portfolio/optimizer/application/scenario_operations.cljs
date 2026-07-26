(ns hyperopen.portfolio.optimizer.application.scenario-operations
  (:require [hyperopen.portfolio.optimizer.application.scenario-workflow :as workflow]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(def ^:private operation-handlers
  {:scenario-index
   {:result-value
    (fn [_operation state]
      (get-in state contracts/scenario-index-path))
    :fail
    (fn [operation state error completed-at-ms]
      (workflow/complete-index-load
       {:state state
        :started-at-ms (:started-at-ms operation)
        :completed-at-ms completed-at-ms
        :error error}))
    :after-scenario-index
    (fn [operation state _command loaded-index completed-at-ms]
      (workflow/complete-index-load
       {:state state
        :loaded-index loaded-index
        :started-at-ms (:started-at-ms operation)
        :completed-at-ms completed-at-ms}))}

   :load
   {:result-value
    (fn [operation _state]
      (when (map? (:loaded-scenario-record operation))
        (:loaded-scenario-record operation)))
    :fail
    (fn [operation state error completed-at-ms]
      (workflow/fail-load
       {:state state
        :scenario-id (:scenario-id operation)
        :started-at-ms (:started-at-ms operation)
        :completed-at-ms completed-at-ms
        :error error}))
    :after-scenario-record
    (fn [operation state command scenario-record completed-at-ms]
      (workflow/continue-load-after-record
       {:state state
        :scenario-id (:scenario-id command)
        :scenario-record scenario-record
        :started-at-ms (:started-at-ms operation)
        :completed-at-ms completed-at-ms}))}

   :save
   {:result-value
    (fn [operation _state]
      (:scenario-record operation))
    :fail
    (fn [operation state error completed-at-ms]
      (workflow/fail-save
       {:state state
        :scenario-id (:scenario-id operation)
        :started-at-ms (:started-at-ms operation)
        :completed-at-ms completed-at-ms
        :error error}))
    :complete
    (fn [operation state completed-at-ms]
      (workflow/complete-save
       {:state state
        :scenario-index (:scenario-index operation)
        :scenario-record (:scenario-record operation)
        :started-at-ms (:started-at-ms operation)
        :completed-at-ms completed-at-ms}))
    :after-scenario-index
    (fn [operation state _command loaded-index _completed-at-ms]
      (workflow/continue-save-after-index
       {:state state
        :address (:address operation)
        :scenario-id (:scenario-id operation)
        :scenario-name (:scenario-name operation)
        :started-at-ms (:started-at-ms operation)
        :loaded-index loaded-index}))}

   :archive
   {:result-value
    (fn [operation _state]
      (:scenario-record operation))
    :fail
    (fn [operation state error completed-at-ms]
      (workflow/fail-archive
       {:state state
        :scenario-id (:scenario-id operation)
        :started-at-ms (:started-at-ms operation)
        :completed-at-ms completed-at-ms
        :error error}))
    :complete
    (fn [operation state completed-at-ms]
      (workflow/complete-archive
       {:state state
        :scenario-index (:scenario-index operation)
        :scenario-record (:scenario-record operation)
        :started-at-ms (:started-at-ms operation)
        :completed-at-ms completed-at-ms}))
    :after-scenario-record
    (fn [operation state command scenario-record completed-at-ms]
      (workflow/continue-archive-after-record
       {:state state
        :address (:address operation)
        :scenario-id (:scenario-id command)
        :scenario-record scenario-record
        :started-at-ms (:started-at-ms operation)
        :completed-at-ms completed-at-ms}))
    :after-scenario-index
    (fn [operation state _command loaded-index _completed-at-ms]
      (workflow/continue-archive-after-index
       {:state state
        :address (:address operation)
        :scenario-id (:scenario-id operation)
        :scenario-record (:loaded-scenario-record operation)
        :started-at-ms (:started-at-ms operation)
        :loaded-index loaded-index}))}

   :duplicate
   {:result-value
    (fn [operation _state]
      (:scenario-record operation))
    :fail
    (fn [operation state error completed-at-ms]
      (workflow/fail-duplicate
       {:state state
        :scenario-id (:scenario-id operation)
        :started-at-ms (:started-at-ms operation)
        :completed-at-ms completed-at-ms
        :error error}))
    :complete
    (fn [operation state completed-at-ms]
      (workflow/complete-duplicate
       {:state state
        :scenario-index (:scenario-index operation)
        :scenario-record (:scenario-record operation)
        :source-scenario-id (:scenario-id operation)
        :started-at-ms (:started-at-ms operation)
        :completed-at-ms completed-at-ms}))
    :after-scenario-record
    (fn [operation state command scenario-record completed-at-ms]
      (workflow/continue-duplicate-after-record
       {:state state
        :address (:address operation)
        :scenario-id (:scenario-id command)
        :duplicated-scenario-id (:duplicated-scenario-id operation)
        :scenario-record scenario-record
        :started-at-ms (:started-at-ms operation)
        :completed-at-ms completed-at-ms}))
    :after-scenario-index
    (fn [operation state _command loaded-index _completed-at-ms]
      (workflow/continue-duplicate-after-index
       {:state state
        :address (:address operation)
        :scenario-id (:scenario-id operation)
        :duplicated-scenario-id (:duplicated-scenario-id operation)
        :scenario-record (:loaded-scenario-record operation)
        :started-at-ms (:started-at-ms operation)
        :loaded-index loaded-index}))}

   :manual-tracking
   {:result-value
    (fn [operation _state]
      (or (:scenario-record operation)
          (:loaded-scenario-record operation)))
    :fail
    (fn [_operation state error _completed-at-ms]
      (workflow/fail-manual-tracking
       {:state state
        :error error}))
    :complete
    (fn [operation state _completed-at-ms]
      (workflow/complete-manual-tracking
       {:state state
        :scenario-index (:scenario-index operation)
        :scenario-record (:scenario-record operation)}))
    :after-scenario-record
    (fn [operation state command scenario-record completed-at-ms]
      (workflow/continue-manual-tracking-after-record
       {:state state
        :address (:address operation)
        :scenario-id (:scenario-id command)
        :scenario-record scenario-record
        :updated-at-ms completed-at-ms}))
    :after-scenario-index
    (fn [operation state command loaded-index completed-at-ms]
      (workflow/continue-manual-tracking-after-index
       {:state state
        :address (:address operation)
        :scenario-id (:scenario-id command)
        :scenario-record (:loaded-scenario-record operation)
        :loaded-index loaded-index
        :updated-at-ms completed-at-ms}))}})

(defn- operation-handler
  [operation handler-key]
  (get-in operation-handlers [(:operation/type operation) handler-key]))

(defn result-value
  [operation state]
  (when-let [result-value (operation-handler operation :result-value)]
    (result-value operation state)))

(defn fail
  [operation state error completed-at-ms]
  (when-let [fail (operation-handler operation :fail)]
    (fail operation state error completed-at-ms)))

(defn complete
  [operation state completed-at-ms]
  (when-let [complete (operation-handler operation :complete)]
    (complete operation state completed-at-ms)))

(defn continue-after-scenario-record
  [operation state command scenario-record completed-at-ms]
  (when-let [continue (operation-handler operation :after-scenario-record)]
    (continue operation state command scenario-record completed-at-ms)))

(defn continue-after-scenario-index
  [operation state command loaded-index completed-at-ms]
  (when-let [continue (operation-handler operation :after-scenario-index)]
    (continue operation state command loaded-index completed-at-ms)))
