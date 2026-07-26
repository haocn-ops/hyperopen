(ns hyperopen.portfolio.optimizer.contracts.worker-wire
  (:require [hyperopen.portfolio.optimizer.instrument-keyed-codec :as instrument-keyed-codec]))

(def worker-wire-schema-version 1)

(def enum-value-keys instrument-keyed-codec/enum-value-keys)
(def instrument-keyed-map-keys instrument-keyed-codec/instrument-keyed-map-keys)
(def instrument-keyed-map-paths instrument-keyed-codec/instrument-keyed-map-paths)
(def instrument-id-key instrument-keyed-codec/instrument-id-key)
(def stringify-instrument-keyed-map
  instrument-keyed-codec/stringify-instrument-keyed-map)
(def normalize-wire-values instrument-keyed-codec/normalize-wire-values)
(def normalize-instrument-keyed-maps
  instrument-keyed-codec/normalize-instrument-keyed-maps)
(def normalize-worker-boundary instrument-keyed-codec/normalize-worker-boundary)
