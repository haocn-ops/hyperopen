(ns hyperopen.domain.trading.indicators.family-runtime
  (:require [clojure.set :as set]
            [hyperopen.domain.trading.indicators.contracts :as contracts]))

(defn- definition-ids
  [definitions]
  (set (map :id definitions)))

(defn- ensure-parity!
  [family-id definitions calculators]
  (let [def-ids (definition-ids definitions)
        calc-ids (set (keys calculators))
        missing-calc (set/difference def-ids calc-ids)
        orphan-calc (set/difference calc-ids def-ids)]
    (when (or (seq missing-calc)
              (seq orphan-calc))
      (throw (ex-info "Indicator family definition/calculator parity mismatch"
                      {:family-id family-id
                       :missing-calculators missing-calc
                       :orphan-calculators orphan-calc})))
    {:family-id family-id
     :definitions (vec definitions)
     :calculators calculators
     :supported-indicator-ids calc-ids}))

(defn build-family
  [family-id definitions calculators]
  (ensure-parity! family-id definitions calculators))

(defn indicators
  [family]
  (:definitions family))

(defn supported-indicator-ids
  [family]
  (:supported-indicator-ids family))

(defn calculate
  [family indicator-type data params]
  (let [config (or params {})
        calculator (get-in family [:calculators indicator-type])]
    (when (and calculator
               (contracts/valid-indicator-input? indicator-type data config))
      (contracts/enforce-indicator-result indicator-type
                                          (count data)
                                          (calculator data config)))))
