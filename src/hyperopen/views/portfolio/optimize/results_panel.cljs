(ns hyperopen.views.portfolio.optimize.results-panel
  (:require [hyperopen.portfolio.optimizer.application.view-model.results :as results-model]
            [hyperopen.portfolio.optimizer.application.view-model.setup-summary :as setup-summary]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.views.portfolio.optimize.equal-risk-confidence-rail
             :as equal-risk-confidence-rail]
            [hyperopen.views.portfolio.optimize.equal-risk-impact-strip
             :as equal-risk-impact-strip]
            [hyperopen.views.portfolio.optimize.frontier-chart :as frontier-chart]
            [hyperopen.views.portfolio.optimize.leverage-impact-panel
             :as leverage-impact-panel]
            [hyperopen.views.portfolio.optimize.refinement-status-card :as refinement-card]
            [hyperopen.views.portfolio.optimize.results-diagnostics-rail :as diagnostics-rail]
            [hyperopen.views.portfolio.optimize.results-summary :as summary]
            [hyperopen.views.portfolio.optimize.risk-contributions-card :as risk-contributions-card]
            [hyperopen.views.portfolio.optimize.scenario-objective-menu :as objective-menu]
            [hyperopen.views.portfolio.optimize.target-exposure-table :as target-exposure-table]
            [hyperopen.views.portfolio.optimize.volatility-intuition-card
             :as volatility-intuition-card]))

(defn- active-views-editor
  ;; Rendered while the return model consumes views AND the objective consumes
  ;; returns. Views are an input policy, not an objective — but covariance-only
  ;; goals (Equal Risk keeps Black-Litterman set when selected after Maximum
  ;; Sharpe) never read the forecast, so soliciting return edits that "rerun
  ;; automatically" yet cannot move the weights would be dishonest. Collapsed by
  ;; default here: on the results page view editing is a by-exception input
  ;; task, so the rail shows only the title + live counts until opened.
  [state draft result readiness]
  (when (and (= :black-litterman (get-in draft [:return-model :kind]))
             (not (contains? setup-summary/return-free-objective-kinds
                             (get-in draft [:objective :kind]))))
    (objective-menu/views-editor-section
     draft
     state
     result
     readiness
     {:container-role "portfolio-optimizer-results-your-views-editor"
      :title "Return views"
      :description "Annualized. Your views tilt the forecast; implied rows use the baseline estimate. Edits save and rerun automatically."
      :extra-class "optimizer-results-your-views-editor"
      :collapsible? true
      :include-apply? true
      :apply-role "portfolio-optimizer-results-your-views-apply"})))

(defn results-panel
  ([last-successful-run]
   (results-panel last-successful-run nil))
  ([last-successful-run draft]
   (results-panel last-successful-run draft nil))
  ([last-successful-run draft {:keys [state stale? frontier-overlay-mode
                                      readiness
                                      constrain-frontier?
                                      refinement]
                               :or {frontier-overlay-mode :standalone}}]
   (let [result (results-model/enrich-result-labels (:result last-successful-run) draft)
         selected-risk-instrument (get-in state
                                          contracts/ui-selected-risk-instrument-path)
         equal-risk? (= :equal-risk (get-in result [:solver :objective-kind]))]
     (when (= :solved (:status result))
       [:section {:class ["optimizer-results-surface" "space-y-0" "leading-4"]
                  :replicant/key "optimizer-results-surface"
                  :data-role "portfolio-optimizer-results-surface"}
        (summary/stale-result-banner stale?)
        ;; Keyed so the stale banner toggling above cannot recreate the grid —
        ;; the rail's collapsible editors hold their open/closed state in the DOM.
        [:div {:class ["optimizer-results-grid" "grid" "grid-cols-1"
                       "xl:grid-cols-[420px_minmax(0,1fr)]"
                       "2xl:grid-cols-[500px_minmax(0,1fr)_320px]"]
               :replicant/key "optimizer-results-grid"
               :data-role "portfolio-optimizer-results-grid"}
         [:div {:class ["optimizer-results-left-panel" "min-h-0" "space-y-0"]
                :data-role "portfolio-optimizer-results-left-panel"}
          (target-exposure-table/target-exposure-table result
                                                        {:state state
                                                         :draft draft
                                                         :selected-risk-instrument
                                                         selected-risk-instrument})]
         [:div {:class ["optimizer-results-center-panel" "min-h-0" "bg-base-100" "p-6"
                        "space-y-4"]
                :data-role "portfolio-optimizer-results-center-panel"}
          ;; Equal Risk yields one selected portfolio, not a frontier: the
          ;; risk-contribution balance card replaces the frontier chart (a
          ;; one-point "curve" whose click handler would silently switch the
          ;; objective to Target Return) and carries the Risk/Return scatter
          ;; as its second DOM-state tab, the why-card speaks in risk
          ;; contributions, and the frontier-density refinement card
          ;; disappears — there is no sweep to refine.
          ;; Plain conditional siblings, never a list-as-one-child (Replicant
          ;; stringifies those); the whole set only toggles when the solved
          ;; objective itself changes.
          [:div {:class ["space-y-4"]
                 :replicant/key (if equal-risk? "equal-risk-center" "frontier-center")}
           ;; The implications lead: current → target imbalance / volatility /
           ;; modeled outcome, each deep-linking into the card below. Above
           ;; the chart so "what does executing change?" is answered before
           ;; the per-asset detail starts.
           (when equal-risk?
             (equal-risk-impact-strip/equal-risk-impact-strip result))
           (when equal-risk?
             (risk-contributions-card/risk-contributions-card
              result
              {:selected-risk-instrument selected-risk-instrument}))
           (when-not equal-risk?
             (frontier-chart/frontier-chart
              draft
              result
              frontier-overlay-mode
              constrain-frontier?))
           ;; The shared slot always follows the center column's primary
           ;; result visual. Its component keeps the gross/volatility gate,
           ;; while the stable wrapper keeps context cards below it from
           ;; shifting when that panel appears or disappears across reruns.
           [:div {:replicant/key "optimizer-leverage-impact-slot"
                  :data-role "portfolio-optimizer-leverage-impact-slot"}
            (leverage-impact-panel/leverage-impact-panel result)]
           (when equal-risk?
             (summary/equal-risk-context-card result))
           ;; Decision-support before solver tuning: the engine-derived "why
           ;; this target" facts sit directly under the chart; the refinement
           ;; card below is compact with its options behind a disclosure.
           (when-not equal-risk?
             (summary/target-context-card result))
           (when-not equal-risk?
             (refinement-card/refinement-status-card refinement))]]
         ;; Rail order is review-first: the next-step row leads, then the
         ;; volatility-intuition context, then frontier solve-quality detail,
         ;; then trust diagnostics, then the collapsed views editor — input
         ;; editing is the by-exception task on this page.
         [:div {:class ["optimizer-results-right-panel" "min-h-0"
                        "xl:col-span-2" "2xl:col-span-1"]
                :data-role "portfolio-optimizer-results-right-panel"}
          ;; Equal Risk gets its own confidence rail (fit / allocation freedom
          ;; / solution stability / real stop reasons); the refinement-based
          ;; rail speaks frontier language that does not exist for it.
          (or (equal-risk-confidence-rail/equal-risk-confidence-rail result)
              (diagnostics-rail/result-confidence-rail refinement))
          ;; Always-present wrapper: the volatility card is conditional (it
          ;; needs a solved σ), and without a stable slot its mounting would
          ;; shift the trust rail and views editor below — resetting open
          ;; <details> state. (Leverage impact lives in the center column
          ;; under each objective's primary result visual.)
          [:div {:replicant/key "optimizer-volatility-risk-cards"
                 :data-role "portfolio-optimizer-volatility-risk-cards"}
           (volatility-intuition-card/volatility-intuition-card result)]
          ;; Quality detail follows volatility intuition for both objectives;
          ;; their lead cards remain above it. Each renderer owns the rows
          ;; meaningful to its objective.
          (if equal-risk?
            (equal-risk-confidence-rail/equal-risk-confidence-quality-rail result)
            (diagnostics-rail/result-confidence-quality-rail refinement))
          (diagnostics-rail/trust-diagnostics-rail result)
          (active-views-editor state draft result readiness)]]]))))
