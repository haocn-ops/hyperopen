(ns hyperopen.views.portfolio.optimize.setup-sections
  (:require [hyperopen.portfolio.optimizer.application.constraint-profiles :as constraint-profiles]
            [hyperopen.portfolio.optimizer.application.current-portfolio :as current-portfolio]
            [hyperopen.portfolio.optimizer.application.view-model.exposure :as exposure-vm]
            [hyperopen.portfolio.optimizer.contracts :as optimizer-contracts]
            [hyperopen.views.portfolio.optimize.instrument-overrides-panel :as instrument-overrides-panel]
            [hyperopen.views.portfolio.optimize.setup-constraint-controls :as constraint-controls]
            [hyperopen.views.portfolio.optimize.setup-controls :as controls]
            [hyperopen.views.portfolio.optimize.setup-model-controls :as model-controls]
            [hyperopen.views.portfolio.optimize.setup-objective-controls :as objective-controls]
            [hyperopen.views.portfolio.optimize.setup-actions :as setup-actions]
            [hyperopen.views.portfolio.optimize.setup-universe :as setup-universe]
            [hyperopen.views.portfolio.optimize.setup-use-my-views-workspace :as use-my-views-workspace]
            [hyperopen.views.portfolio.optimize.target-sigma :as target-sigma]))

;; The setup route is a 3-column grid (workspace_view). LEFT = universe selection only; CENTER =
;; the editable scenario policy (objective / model / constraints / advanced); RIGHT = the compact
;; summary + Run readiness (setup_context). This namespace owns the LEFT and CENTER columns.

(defn control-rail
  "LEFT column: universe selection only. Everything the trader configures about POLICY lives in
  the wider center policy-pane, not this narrow rail."
  [{:keys [state draft readiness history-load-state]}]
  [:aside {:class ["optimizer-control-rail" "min-h-0"]
           :data-role "portfolio-optimizer-setup-control-rail"}
   (setup-universe/universe-section state draft
                                    {:readiness readiness
                                     :history-load-state history-load-state})])

(defn- why-safe-note
  "The 'why this preset is safe' explanation, collapsed by default and sitting BELOW the center
  controls so it is available without dominating the screen (it used to sit in the right rail)."
  []
  (controls/disclosure-panel
   "portfolio-optimizer-why-safe-note"
   (controls/disclosure-heading "Why this preset is safe" nil)
   [:div {:class ["mt-3" "space-y-2" "text-[0.6875rem]" "leading-[1.55]" "text-trading-muted"]}
    [:p "Minimum variance does not rely on return forecasts."]
    [:p "Stabilized inputs reduce dependence on a single historical window."]
    [:p "Cash floor and turnover caps protect against destructive rebalances."]
    [:p {:class ["font-semibold" "text-warning"]}
     "Switch to Use my views to add beliefs and compare posterior output."]]))

(defn policy-pane
  "CENTER column: the editable scenario policy. Objective, return/risk model, the constraints
  (2D exposure map + risk guards + rebalance behavior + advanced solver drawer), a collapsed
  'why safe' note, and the Run bottom bar. For Black-Litterman the whole pane is the use-my-views
  workspace (it edits the return model = policy)."
  [{:keys [state draft highlighted-controls readiness running? run-triggerable?
           saving-scenario? solved-run? result-path]}]
  (let [black-litterman? (= :black-litterman (get-in draft [:return-model :kind]))]
    (into
     [:main {:class ["optimizer-policy-pane" "space-y-4" "leading-4"]
             :data-role "portfolio-optimizer-setup-policy-pane"}]
     (if black-litterman?
       [(use-my-views-workspace/use-my-views-workspace
         {:draft draft
          :readiness readiness
          :running? running?
          :run-triggerable? run-triggerable?
          :saving-scenario? saving-scenario?
          :solved-run? solved-run?
          :result-path result-path})]
       [(objective-controls/objective-section
         draft
         highlighted-controls
         (target-sigma/frontier-sigma-bounds
          (get-in state optimizer-contracts/last-successful-run-result-path)))
        (model-controls/model-section draft)
        (constraint-controls/constraints-section
         draft highlighted-controls
         {:current-exposure (exposure-vm/snapshot->current-exposure
                             (current-portfolio/current-portfolio-snapshot state))
          :has-saved-default? (constraint-profiles/has-default?
                               (get-in state optimizer-contracts/constraint-profiles-path)
                               (constraint-profiles/universe-key
                                (get-in state optimizer-contracts/draft-universe-path)))})
        (controls/disclosure-panel
         "portfolio-optimizer-advanced-overrides-shell"
         (controls/disclosure-heading
          "Advanced Overrides"
          (instrument-overrides-panel/overrides-trailing-label draft))
         (instrument-overrides-panel/instrument-overrides-panel draft))
        (why-safe-note)
        [:div {:class ["space-y-2"]
               :data-role "portfolio-optimizer-model-assumptions-stack"}
         (setup-actions/model-assumptions-panel)
         (setup-actions/setup-bottom-actions {:draft draft
                                              :readiness readiness
                                              :running? running?
                                              :run-triggerable? run-triggerable?
                                              :saving-scenario? saving-scenario?
                                              :solved-run? solved-run?
                                              :result-path result-path})]]))))
