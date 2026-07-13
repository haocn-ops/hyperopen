(ns hyperopen.portfolio.optimizer.contracts.result-sections
  "Focused additive validators for optional solved-result sections."
  (:require [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def ^:private diversification-scalar-keys
  [:modeled-volatility
   :all-move-together-volatility
   :zero-correlation-volatility
   :reduction-vs-all-move-together
   :reduction-ratio-vs-all-move-together
   :modeled-minus-zero-correlation])

(defn diversification-summary?
  [summary]
  (and (map? summary)
       (every? #(coercion/finite-number? (get summary %))
               diversification-scalar-keys)))

(defn diversification-summaries?
  "Legacy results may omit both summaries; current is optional only when a
  valid target summary establishes the comparison baseline."
  [structure]
  (let [target (:target-diversification structure)
        current (:current-diversification structure)]
    (and (or (nil? target) (diversification-summary? target))
         (or (nil? current) (diversification-summary? current))
         (or (nil? current) (some? target)))))
