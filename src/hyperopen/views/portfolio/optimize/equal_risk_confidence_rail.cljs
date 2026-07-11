(ns hyperopen.views.portfolio.optimize.equal-risk-confidence-rail
  "Result-confidence rail for Equal Risk runs. Replaces the frontier-refinement
  rail (frontier quality / draft point budget / selection stability) — none of
  which exist for this objective — with the honest equivalents: Equal-Risk Fit
  (the measured quality + max deviation), Allocation Freedom (open / limited /
  fully determined by the exposure targets), Solution Stability (agreement of
  the solver's deterministic starts), and the solver's actual stop reason. All
  classification lives in the equal-risk-results view-model; this namespace
  only renders."
  (:require [hyperopen.portfolio.optimizer.application.view-model.equal-risk-results
             :as equal-risk-results]
            [hyperopen.views.portfolio.optimize.results-diagnostics-rail :as rail]))

(defn- status->row-status
  [status]
  (case status
    :ok :ok
    :caution :caution
    :bad :bad
    nil))

(defn- from-here-row
  "The rail's lead: for a fully-determined book the honest next step is to
  review the constraints (the optimizer had nothing to decide); otherwise
  rebalance now or loosen caps/exposure for tighter balance."
  [fully-determined?]
  [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]
         :data-role "portfolio-optimizer-equal-risk-next-step"}
   [:div {:class ["flex" "items-center" "justify-between" "gap-3"]}
    [:span {:class ["text-[0.62rem]" "font-semibold" "uppercase" "tracking-[0.06em]" "text-trading-muted"]}
     "From here"]]
   [:p {:class ["mt-1" "font-mono" "text-sm" "text-trading-muted"]}
    [:button {:type "button"
              :class ["optimizer-result-confidence-rebalance"]
              :data-role "portfolio-optimizer-equal-risk-rebalance"
              :on {:click [[:actions/open-portfolio-optimizer-execution]]}}
     "Rebalance"]
    (if fully-determined?
      " now, or revisit exposure targets"
      " now, or refine constraints for tighter balance")]
   [:p {:class ["mt-0.5" "text-[0.6rem]" "text-trading-muted/70"]}
    (if fully-determined?
      "Gross and net exposure fully determine the selected positions — only changing targets or sides can move the balance."
      "Draft is solved and usable. Loosening binding caps or the net bias can tighten the risk balance.")]])

(defn equal-risk-confidence-rail
  "Renders nil for non-equal-risk results (the caller falls back to the
  refinement rail). Degrades gracefully on persisted pre-redesign results:
  freedom/stability rows show em-dashes instead of guessing."
  [result]
  (when (equal-risk-results/equal-risk-result? result)
    (let [quality (get-in result [:risk-contributions :quality])
          quality-view (equal-risk-results/quality-view quality)
          max-pts (get-in result [:risk-contributions :max-absolute-error])
          freedom (equal-risk-results/freedom-view
                   (equal-risk-results/allocation-freedom result))
          fully-determined? (= :fully-determined
                              (:status (equal-risk-results/allocation-freedom result)))
          stability (equal-risk-results/solution-stability result)
          stop (equal-risk-results/stop-reason result)]
      [:aside {:class ["optimizer-result-confidence-panel"
                       "min-h-0" "border-l" "border-base-300" "bg-base-100/95"]
               :data-role "portfolio-optimizer-equal-risk-confidence-panel"}
       [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
        [:p {:class ["font-mono" "text-[0.62rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
         "Result confidence"]]
       (from-here-row fully-determined?)
       (rail/confidence-row
        {:label "Equal-risk fit"
         :status (status->row-status (:status quality-view))
         :value (:label quality-view)
         :subtext (str "Max deviation "
                       (equal-risk-results/format-pts
                        (when (number? max-pts) (* 100 max-pts))))})
       (rail/confidence-row
        {:label "Allocation freedom"
         :status (status->row-status (:status freedom))
         :value (:label freedom)
         :subtext (:detail freedom)})
       (rail/confidence-row
        {:label "Solution stability"
         :status (status->row-status (:status stability))
         :value (:label stability)
         :subtext (:detail stability)})
       (rail/confidence-row
        {:label "Stop reason"
         :value (:label stop)
         :subtext (:detail stop)})])))
