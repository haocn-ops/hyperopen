(ns hyperopen.portfolio.optimizer.application.view-model.setup-history-assumption-rail
  "Right-rail summary projection over the history-assumption cards: compact
  per-asset facts (final modeled basket first among the proxy facts), the
  aggregate readiness line, and the results-disclosure note."
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.view-model.setup-history-assumption-cards :as assumption-cards]))

(defn- diagnostics-cell-value
  [card cell-key]
  (some #(when (= cell-key (:key %)) (:value %))
        (:diagnostics card)))

(defn- rail-summary-pairs
  [card]
  (let [proxy? (= :proxy (:mode card))
        final-rows (get-in card [:final-basket :rows])]
    (->> [["Status" (:status-label card)]
          ["Assumption mode" (or (:mode-label card) "Not chosen")]
          (when proxy?
            ["Proxy assets" (if (seq (:selected-proxies card))
                              (str/join ", " (map :label (:selected-proxies card)))
                              "None selected")])
          ;; The confidence-shrunk split that actually drives covariance
          ;; synthesis - never the raw prior.
          (when (and proxy? (seq final-rows))
            ["Final modeled basket" (assumption-cards/basket-summary-text final-rows " · ")])
          (when proxy?
            ["Relationship strength" (or (:relationship-label card) "--")])
          ["Expected volatility" (or (get-in card [:volatility :percent-label]) "--")]
          ["Max weight cap" (or (get-in card [:max-weight :percent-label]) "--")]
          (when proxy?
            ["Regression confidence" (diagnostics-cell-value card :regression-confidence)])
          ;; Proxy cards report the window the risk model estimates over (the
          ;; proxy-extended shared window), never the asset's own short overlap
          ;; — that one reads as "the model only uses N days" and is demoted to
          ;; its own calibration line.
          (if (and proxy? (:covariance-observations card))
            ["History used" (str (assumption-cards/observations-label (:covariance-observations card))
                                 " · via proxies")]
            ["History used" (assumption-cards/observations-label (:observations card))])
          (when proxy?
            ["Calibration overlap" (assumption-cards/observations-label (:observations card))])]
         (keep identity)
         vec)))

(defn history-assumption-rail-model
  "Right-rail summary of every asset in the assumption workflow: compact
  per-asset facts once configured, plus the aggregate readiness line and the
  results-disclosure note."
  ([state draft readiness history-load-state]
   (history-assumption-rail-model state draft readiness history-load-state nil))
  ([state draft readiness history-load-state opts]
   (let [{:keys [cards applicable?]} (assumption-cards/history-assumption-cards state
                                                               draft
                                                               readiness
                                                               history-load-state
                                                               opts)
         all-configured? (and (seq cards) (every? :engine-applied? cards))]
     {:applicable? (boolean applicable?)
      :rows (mapv (fn [card]
                    {:instrument-id (:instrument-id card)
                     :label (:label card)
                     :status (:status card)
                     :status-label (:status-label card)
                     :mode (:mode card)
                     :configured? (:engine-applied? card)
                     :summary-pairs (rail-summary-pairs card)})
                  cards)
      :all-configured? all-configured?
      :any-proxy? (boolean (some #(= :proxy (:mode %)) cards))
      :ready-message (when all-configured?
                       "All short-history assets have assumptions. Ready to run.")
      :disclosure-note "Proxy assumptions are disclosed in results."})))
