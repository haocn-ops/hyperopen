(ns hyperopen.portfolio.optimizer.application.view-model.tracking
  (:require [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.ids :as ids]))

(def ^:private trackable-statuses
  #{:executed :partially-executed :tracking})

(def ^:private manual-tracking-source-statuses
  #{:saved :computed})

(defn- labels-by-instrument
  [state]
  (or (get-in state (conj contracts/last-successful-run-result-path
                          :labels-by-instrument))
      {}))

(defn- instrument-label
  [labels-by-instrument instrument-id]
  (let [value (str instrument-id)]
    (if (ids/vault-instrument-id? value)
      (or (get labels-by-instrument instrument-id)
          value)
      value)))

(defn- enrich-instrument-row
  [labels-by-instrument row]
  (assoc row :instrument-label (instrument-label labels-by-instrument
                                                 (:instrument-id row))))

(defn- enrich-instrument-rows
  [labels-by-instrument rows]
  (mapv (partial enrich-instrument-row labels-by-instrument)
        (or rows [])))

(defn- enrich-snapshot
  [labels-by-instrument snapshot]
  (when snapshot
    (assoc snapshot :rows (enrich-instrument-rows labels-by-instrument
                                                  (:rows snapshot)))))

(defn- latest-record
  [records]
  (last (vec records)))

(defn- active-scenario-id
  [state]
  (or (get-in state contracts/active-scenario-loaded-id-path)
      (get-in state contracts/draft-id-path)))

(defn tracking-model
  [state]
  (let [scenario-status (get-in state contracts/active-scenario-status-path)
        tracking-record (get-in state contracts/tracking-path)
        labels-by-instrument* (labels-by-instrument state)
        latest-snapshot (enrich-snapshot labels-by-instrument*
                                         (latest-record (:snapshots tracking-record)))
        active-id (active-scenario-id state)]
    {:scenario-status scenario-status
     :tracking-record tracking-record
     :snapshots (:snapshots tracking-record)
     :latest-snapshot latest-snapshot
     :latest-rows (vec (:rows latest-snapshot))
     :labels-by-instrument labels-by-instrument*
     :active-scenario-id active-id
     :manual-tracking? (contains? manual-tracking-source-statuses scenario-status)
     :trackable? (contains? trackable-statuses scenario-status)
     :manual-tracking-enableable? (some? active-id)}))
