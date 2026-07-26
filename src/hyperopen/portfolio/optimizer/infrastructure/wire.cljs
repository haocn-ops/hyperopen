(ns hyperopen.portfolio.optimizer.infrastructure.wire
  (:require [hyperopen.portfolio.optimizer.contracts :as contracts]))

(def enum-value-keys contracts/enum-value-keys)
(def instrument-keyed-map-keys contracts/instrument-keyed-map-keys)
(def instrument-keyed-map-paths contracts/instrument-keyed-map-paths)
(def instrument-id-key contracts/instrument-id-key)
(def stringify-instrument-keyed-map contracts/stringify-instrument-keyed-map)
(def normalize-wire-values contracts/normalize-wire-values)
(def normalize-instrument-keyed-maps contracts/normalize-instrument-keyed-maps)
(def normalize-worker-boundary contracts/normalize-worker-boundary)

(defn- worker-key
  [key]
  (cond
    (keyword? key) (subs (str key) 1)
    (string? key) key
    :else (str key)))

(defn clj->worker-boundary
  [value]
  (clj->js value {:keyword-fn worker-key}))
