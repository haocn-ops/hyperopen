(ns hyperopen.portfolio.optimizer.frontier-actions
  (:require [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def ^:private frontier-overlay-modes
  #{:standalone
    :contribution
    :none})

(def ^:private normalize-keyword-like coercion/normalize-keyword-like)

(defn normalize-frontier-overlay-mode
  [value]
  (let [mode (normalize-keyword-like value)]
    (if (contains? frontier-overlay-modes mode)
      mode
      :standalone)))

(defn set-portfolio-optimizer-frontier-overlay-mode
  [_state mode]
  [[:effects/save
    contracts/ui-frontier-overlay-mode-path
    (normalize-frontier-overlay-mode mode)]])

(defn set-portfolio-optimizer-constrain-frontier
  [_state constrained?]
  [[:effects/save
    contracts/ui-constrain-frontier-path
    (true? constrained?)]])
