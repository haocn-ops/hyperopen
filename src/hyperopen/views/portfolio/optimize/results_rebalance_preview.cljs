(ns hyperopen.views.portfolio.optimize.results-rebalance-preview
  (:require [hyperopen.portfolio.optimizer.application.view-model.results :as results-model]
            [hyperopen.views.portfolio.optimize.format :as opt-format]
            [hyperopen.views.portfolio.optimize.results-summary :as summary]))

(defn- rebalance-row
  [labels-by-instrument row]
  [:div {:class ["optimizer-row"
                 "grid"
                 "grid-cols-[minmax(8rem,1.1fr)_repeat(8,minmax(5rem,0.75fr))]"
                 "gap-3"
                 "rounded-lg"
                 "border"
                 "border-base-300"
                 "bg-base-200/40"
                 "p-3"
                 "text-xs"
                 "tabular-nums"]
         :data-role (str "portfolio-optimizer-rebalance-row-" (:instrument-id row))}
   [:span {:class ["font-semibold" "text-trading-text"]}
    (results-model/instrument-label labels-by-instrument (:instrument-id row))]
   [:span (opt-format/keyword-label (:status row))]
   [:span (opt-format/keyword-label (:side row))]
   [:span (opt-format/format-decimal (:quantity row))]
   [:span (opt-format/format-usdc (:price row))]
   [:span (opt-format/keyword-label (get-in row [:cost :source]))]
   [:span (opt-format/format-usdc (get-in row [:cost :estimated-slippage-usd]))]
   [:span (opt-format/format-usdc (:delta-notional-usd row))]
   [:span (opt-format/keyword-label (:reason row))]])

(defn rebalance-preview
  [result]
  (let [preview (:rebalance-preview result)
        labels-by-instrument (or (:labels-by-instrument result) {})
        summary* (:summary preview)
        reviewable-count (+ (or (:ready-count summary*) 0)
                            (or (:blocked-count summary*) 0))]
    (summary/panel-shell
     "portfolio-optimizer-rebalance-preview"
     "Rebalance Preview"
     "Rows that cannot execute through the current trading stack remain visible instead of being dropped."
     [:div {:class ["grid" "grid-cols-2" "gap-2" "lg:grid-cols-4"]}
      (summary/summary-card "Status" (opt-format/keyword-label (:status preview)))
      (summary/summary-card "Ready" (str (or (:ready-count summary*) 0)))
      (summary/summary-card "Blocked" (str (or (:blocked-count summary*) 0)))
      (summary/summary-card "Gross Trade" (opt-format/format-usdc (:gross-trade-notional-usd summary*)))]
     [:div {:class ["grid" "grid-cols-2" "gap-2" "lg:grid-cols-4"]}
      (summary/summary-card "Fees" (opt-format/format-usdc (:estimated-fees-usd summary*)))
      (summary/summary-card "Slippage" (opt-format/format-usdc (:estimated-slippage-usd summary*)))
      (summary/summary-card "Margin After"
                            (opt-format/format-pct (get-in summary* [:margin :after-utilization])))
      (summary/summary-card "Margin Warning"
                            (opt-format/keyword-label (get-in summary* [:margin :warning])))]
     [:button {:type "button"
               :class ["optimizer-primary-action"
                       "rounded-lg" "border" "border-primary/50" "bg-primary/10" "px-3" "py-2"
                       "text-left" "text-sm" "font-semibold" "text-primary"
                       "disabled:cursor-not-allowed" "disabled:border-base-300"
                       "disabled:bg-base-200/40" "disabled:text-trading-muted"]
               :data-role "portfolio-optimizer-stage-execution"
               :disabled (not (pos? reviewable-count))
               :on {:click [[:actions/open-portfolio-optimizer-execution]]}}
      "Review Execution"]
     [:div {:class ["grid"
                    "grid-cols-[minmax(8rem,1.1fr)_repeat(8,minmax(5rem,0.75fr))]"
                    "gap-3"
                    "rounded-lg"
                    "border"
                    "border-base-300"
                    "bg-base-200/40"
                    "p-3"
                    "text-xs"
                    "font-semibold"
                    "uppercase"
                    "tracking-[0.14em]"
                    "text-trading-muted"]}
      [:span "Instrument"]
      [:span "Status"]
      [:span "Side"]
      [:span "Size"]
      [:span "Price"]
      [:span "Cost Source"]
      [:span "Slippage"]
      [:span "Delta"]
      [:span "Reason"]]
     (map (partial rebalance-row labels-by-instrument) (:rows preview)))))
