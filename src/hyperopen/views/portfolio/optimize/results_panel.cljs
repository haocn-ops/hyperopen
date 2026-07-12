(ns hyperopen.views.portfolio.optimize.results-panel
  (:require [hyperopen.portfolio.optimizer.application.view-model.results :as results-model]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.views.portfolio.optimize.equal-risk-confidence-rail
             :as equal-risk-confidence-rail]
            [hyperopen.views.portfolio.optimize.frontier-chart :as frontier-chart]
            [hyperopen.views.portfolio.optimize.leverage-risk-card :as leverage-risk-card]
            [hyperopen.views.portfolio.optimize.refinement-status-card :as refinement-card]
            [hyperopen.views.portfolio.optimize.results-diagnostics-rail :as diagnostics-rail]
            [hyperopen.views.portfolio.optimize.results-summary :as summary]
            [hyperopen.views.portfolio.optimize.risk-contributions-card :as risk-contributions-card]
            [hyperopen.views.portfolio.optimize.scenario-objective-menu :as objective-menu]
            [hyperopen.views.portfolio.optimize.target-exposure-table :as target-exposure-table]
            [hyperopen.views.portfolio.optimize.volatility-intuition-card
             :as volatility-intuition-card]))

(defn- active-views-editor
  ;; Rendered whenever the return model consumes views, regardless of objective —
  ;; views are an input policy, not an objective. Collapsed by default here: on the
  ;; results page view editing is a by-exception input task, so the rail shows only
  ;; the title + live counts until opened.
  [state draft result readiness]
  (when (= :black-litterman (get-in draft [:return-model :kind]))
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
           (when equal-risk?
             (risk-contributions-card/risk-contributions-card
              result
              {:selected-risk-instrument selected-risk-instrument}))
           (when equal-risk?
             (summary/equal-risk-context-card result))
           (when-not equal-risk?
             (frontier-chart/frontier-chart
              draft
              result
              frontier-overlay-mode
              constrain-frontier?))
           ;; The strip's slot wrapper always renders, so the insight
           ;; appearing/disappearing across reruns can never shift the
           ;; disclosure-holding cards below it.
           (when-not equal-risk?
             (volatility-intuition-card/insight-strip result))
           ;; Decision-support before solver tuning: the engine-derived "why
           ;; this target" facts sit directly under the chart; the refinement
           ;; card below is compact with its options behind a disclosure.
           (when-not equal-risk?
             (summary/target-context-card result))
           (when-not equal-risk?
             (refinement-card/refinement-status-card refinement))]]
         ;; Rail order is review-first: the next-step row leads, then
         ;; volatility/leverage-risk context, then frontier solve-quality
         ;; detail, then trust diagnostics, then the collapsed views editor —
         ;; input editing is the by-exception task on this page.
         [:div {:class ["optimizer-results-right-panel" "min-h-0"
                        "xl:col-span-2" "2xl:col-span-1"]
                :data-role "portfolio-optimizer-results-right-panel"}
          ;; Equal Risk gets its own confidence rail (fit / allocation freedom
          ;; / solution stability / real stop reasons); the refinement-based
          ;; rail speaks frontier language that does not exist for it.
          (or (equal-risk-confidence-rail/equal-risk-confidence-rail result)
              (diagnostics-rail/result-confidence-rail refinement))
          ;; Always-present wrapper: the two cards inside are conditional
          ;; (volatility needs a solved σ, leverage risk is gated on gross/σ),
          ;; and without a stable slot their mounting would shift the trust
          ;; rail and views editor below — resetting open <details> state.
          [:div {:replicant/key "optimizer-volatility-risk-cards"
                 :data-role "portfolio-optimizer-volatility-risk-cards"}
           (volatility-intuition-card/volatility-intuition-card result)
           (leverage-risk-card/leverage-risk-card result)]
          ;; Frontier quality / selection stability / stop reason (2026-07-12
          ;; user request): relocated below the volatility/leverage-risk cards
          ;; instead of leading the rail — the next-step row above is the
          ;; higher-priority lead and stays put. Equal Risk already carries its
          ;; analogous rows in equal-risk-confidence-rail's single card.
          (when-not equal-risk?
            (diagnostics-rail/result-confidence-quality-rail refinement))
          (diagnostics-rail/trust-diagnostics-rail result)
          (active-views-editor state draft result readiness)]]]))))
