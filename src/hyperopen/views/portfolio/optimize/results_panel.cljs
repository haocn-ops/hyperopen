(ns hyperopen.views.portfolio.optimize.results-panel
  (:require [hyperopen.portfolio.optimizer.application.view-model.results :as results-model]
            [hyperopen.views.portfolio.optimize.frontier-chart :as frontier-chart]
            [hyperopen.views.portfolio.optimize.refinement-status-card :as refinement-card]
            [hyperopen.views.portfolio.optimize.results-diagnostics-rail :as diagnostics-rail]
            [hyperopen.views.portfolio.optimize.results-rebalance-preview :as rebalance-preview]
            [hyperopen.views.portfolio.optimize.results-summary :as summary]
            [hyperopen.views.portfolio.optimize.scenario-objective-menu :as objective-menu]
            [hyperopen.views.portfolio.optimize.target-exposure-table :as target-exposure-table]))

(defn- active-views-editor
  [state draft result readiness]
  (when (= :use-my-views (objective-menu/current-objective-menu-key draft result))
    (objective-menu/views-editor-section
     draft
     state
     result
     readiness
     {:container-role "portfolio-optimizer-results-your-views-editor"
      :title "Your views"
      :description "Change annualized return views and confidence, then rerun the recommendation."
      :extra-class "optimizer-results-your-views-editor"
      :include-apply? true
      :apply-role "portfolio-optimizer-results-your-views-apply"})))

(defn results-panel
  ([last-successful-run]
   (results-panel last-successful-run nil))
  ([last-successful-run draft]
   (results-panel last-successful-run draft nil))
  ([last-successful-run draft {:keys [state stale? include-rebalance? frontier-overlay-mode
                                      readiness
                                      constrain-frontier?
                                      refinement]
                               :or {include-rebalance? true
                                    frontier-overlay-mode :standalone}}]
   (let [result (results-model/enrich-result-labels (:result last-successful-run) draft)]
     (when (= :solved (:status result))
       [:section {:class ["optimizer-results-surface" "space-y-0" "leading-4"]
                  :data-role "portfolio-optimizer-results-surface"}
        (summary/stale-result-banner stale?)
        [:div {:class ["optimizer-results-grid" "grid" "grid-cols-1"
                       "xl:grid-cols-[420px_minmax(0,1fr)]"
                       "2xl:grid-cols-[500px_minmax(0,1fr)_320px]"]
               :data-role "portfolio-optimizer-results-grid"}
         [:div {:class ["optimizer-results-left-panel" "min-h-0" "space-y-0"]
                :data-role "portfolio-optimizer-results-left-panel"}
          (target-exposure-table/target-exposure-table result
                                                        {:state state
                                                         :draft draft})]
         [:div {:class ["optimizer-results-center-panel" "min-h-0" "bg-base-100" "p-6"
                        "space-y-4"]
                :data-role "portfolio-optimizer-results-center-panel"}
          (frontier-chart/frontier-chart
           draft
           result
           frontier-overlay-mode
           constrain-frontier?)
          (refinement-card/refinement-status-card refinement)]
         [:div {:class ["optimizer-results-right-panel" "min-h-0"
                        "xl:col-span-2" "2xl:col-span-1"]
                :data-role "portfolio-optimizer-results-right-panel"}
          (active-views-editor state draft result readiness)
          (diagnostics-rail/result-confidence-rail refinement)
          (diagnostics-rail/trust-diagnostics-rail result)]]
        (when include-rebalance?
          (rebalance-preview/rebalance-preview result))]))))
