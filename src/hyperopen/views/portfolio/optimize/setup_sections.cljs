(ns hyperopen.views.portfolio.optimize.setup-sections
  (:require [hyperopen.portfolio.optimizer.application.constraint-profiles :as constraint-profiles]
            [hyperopen.portfolio.optimizer.application.current-portfolio :as current-portfolio]
            [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
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

(defn control-rail
  [{:keys [state draft highlighted-controls readiness history-load-state]}]
  [:aside {:class ["optimizer-control-rail" "min-h-0" "overflow-hidden"]
           :data-role "portfolio-optimizer-setup-control-rail"}
   (setup-universe/universe-section state draft
                                       {:readiness readiness
                                        :history-load-state history-load-state})
   (objective-controls/objective-section
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
    (instrument-overrides-panel/instrument-overrides-panel draft))])

(defn- summary-row
  [{:keys [label title copy]}]
  [:div {:class ["grid" "grid-cols-[132px_minmax(0,1fr)]" "gap-4" "border-b"
                 "border-base-300" "px-4" "py-3"]}
   [:p {:class controls/eyebrow-class} label]
   [:div
    [:p {:class ["text-[0.6875rem]" "font-medium" "text-trading-text"]} title]
    [:p {:class ["mt-1" "text-[0.6875rem]" "leading-[1.45]" "text-trading-muted"]} copy]]])

(defn setup-bottom-actions
  [opts]
  (setup-actions/setup-bottom-actions opts))

(defn summary-pane
  [{:keys [draft readiness running? run-triggerable? saving-scenario? solved-run? result-path]}]
  (let [{:keys [black-litterman? summary-rows]}
        (optimizer-view-model/setup-summary-model
         draft
         {:labelize controls/labelize
          :percent-label controls/percent-label})]
    (into
     [:main {:class ["optimizer-summary-pane" "space-y-4" "leading-4"]
             :data-role "portfolio-optimizer-setup-summary-pane"}]
     (if black-litterman?
       [(use-my-views-workspace/use-my-views-workspace
         {:draft draft
          :readiness readiness
          :running? running?
          :run-triggerable? run-triggerable?
          :saving-scenario? saving-scenario?
          :solved-run? solved-run?
          :result-path result-path})]
       [[:div {:class ["px-1" "pt-2" "pb-1"]
               :data-role "portfolio-optimizer-setup-summary-heading"}
         [:p {:class controls/eyebrow-class} "Summary"]
         [:h2 {:class ["mt-2" "text-[0.875rem]" "font-medium" "tracking-[-0.01em]"]}
          "What this scenario will solve for"]]
        (into
         [:section {:class ["border" "border-base-300" "bg-base-100/90"]
                    :data-role "portfolio-optimizer-setup-summary-panel"}]
         (map summary-row summary-rows))
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
