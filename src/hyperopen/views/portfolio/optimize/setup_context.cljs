(ns hyperopen.views.portfolio.optimize.setup-context
  (:require [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.portfolio.routes :as portfolio-routes]
            [hyperopen.views.portfolio.optimize.optimization-progress-panel :as optimization-progress-panel]
            [hyperopen.views.portfolio.optimize.run-status-panel :as run-status-panel]
            [hyperopen.views.portfolio.optimize.scenario-objective-menu :as scenario-objective-menu]
            [hyperopen.views.portfolio.optimize.setup-controls :as controls]
            [hyperopen.views.portfolio.optimize.setup-readiness-panel :as setup-readiness-panel]))

(def ^:private eyebrow-class
  ["font-mono" "text-[0.6875rem]" "font-semibold" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"])

(defn- contract-row
  [label value]
  ;; Row labels are plain sentence-case text — mono/uppercase is reserved for
  ;; VALUES and section eyebrows; a rail full of uppercase-mono labels is what
  ;; made the contract read like telemetry. 110px label track keeps
  ;; "Exposure policy" on one line.
  [:div {:class ["grid" "grid-cols-[110px_minmax(0,1fr)]" "items-baseline" "gap-2"]}
   [:span {:class ["text-[0.6875rem]" "font-medium" "text-trading-muted"]}
    label]
   [:span {:class ["min-w-0" "text-[0.75rem]" "font-medium" "leading-[1.4]"
                   "text-trading-text"]}
    value]])

(defn- summary-card
  "The scenario contract: the exact universe/objective/model/constraint policy the
  solver will receive, as a labeled stack the user can verify at a glance instead
  of inferring it from scattered controls. Derived output, not primary input.
  While the holdings auto-seed is pending the Universe row says so — the rail is
  where the user looks to confirm readiness, so it must reflect the wait."
  [draft readiness]
  (let [{:keys [preset-label asset-count universe-source-kind objective-label
                return-forecast-label risk-label exposure-rows]}
        (optimizer-view-model/setup-summary-card-model draft {:labelize controls/labelize})
        holdings-loading? (= :holdings-loading (:reason readiness))]
    [:section {:class ["optimizer-setup-panel" "border" "border-base-300" "bg-base-100/90" "p-3"]
               :data-role "portfolio-optimizer-setup-summary-card"}
     [:div {:class ["flex" "items-baseline" "justify-between" "gap-2"]}
      [:p {:class eyebrow-class} "Scenario contract"]
      [:span {:class ["font-mono" "text-[0.6875rem]" "uppercase" "tracking-[0.1em]"
                      "text-trading-muted/70"]}
       preset-label]]
     [:div {:class ["mt-2" "space-y-1"]}
      (contract-row "Universe"
                    (if holdings-loading?
                      "Loading holdings…"
                      (str asset-count " assets"
                           (case universe-source-kind
                             :holdings " · from holdings"
                             :custom " · custom"
                             nil))))
      (contract-row "Goal" objective-label)
      ;; Under Minimum Variance the forecast does not drive the result, and the
      ;; contract says "Not used" instead of implying the estimator matters.
      ;; Otherwise it is a SOURCE line ("2 your views · 12 implied"), not a
      ;; model name.
      (contract-row "Return forecast" return-forecast-label)
      (contract-row "Risk model" risk-label)
      ;; Stacked exposure rows: four numbers the user must verify, one per
      ;; line, instead of a single wrapping compressed string.
      (contract-row "Exposure policy"
                    (into [:span {:class ["block" "font-mono" "text-[0.75rem]"
                                          "text-trading-muted"]
                                  :data-role "portfolio-optimizer-exposure-policy-rows"}]
                          (map (fn [[label value]]
                                 [:span {:class ["flex" "justify-between" "gap-2"]}
                                  [:span {:class ["text-trading-muted/70"]} label]
                                  [:span value]]))
                          exposure-rows))]]))

(defn context-rail
  [{:keys [draft state readiness snapshot preview-snapshot run-state optimization-progress
           history-load-state last-successful-run current-result? result-path]}]
  (let [progress-visible? (contains? #{:running :succeeded :failed}
                                     (:status optimization-progress))
        readiness-visible? (or (contains? #{:loading :failed :succeeded}
                                          (:status history-load-state))
                               (contains? #{:no-eligible-history
                                            :incomplete-history
                                            :missing-history-assumptions
                                            :history-loading
                                            :holdings-loading}
                                          (:reason readiness))
                               (seq (:warnings readiness)))
        run-visible? (not= :idle (:status run-state))
        last-run-visible? (:result last-successful-run)
        read-only-message (get-in preview-snapshot [:account :read-only-message])
        status-visible? (or progress-visible?
                            readiness-visible?
                            run-visible?
                            last-run-visible?
                            read-only-message)
        views-active? (= :black-litterman (get-in draft [:return-model :kind]))
        min-variance? (= :minimum-variance (get-in draft [:objective :kind]))
        readiness-model (optimizer-view-model/readiness-panel-model readiness history-load-state)
        snapshot-line (cond
                        (not (:snapshot-loaded? snapshot))
                        (if (= :manual (get-in preview-snapshot [:capital :source]))
                          "Manual capital base is being used for preview sizing."
                          "Current portfolio snapshot is not loaded yet.")
                        (not (:capital-ready? preview-snapshot))
                        "Current portfolio snapshot is available, but no positive capital base is available for preview sizing."
                        (not (:execution-ready? preview-snapshot))
                        "Current portfolio snapshot is available in read-only mode."
                        :else
                        "Current portfolio snapshot is available.")
        ;; Two generic healthy lines ("snapshot available" + "history loaded")
        ;; collapse into one status sentence; anything unhealthy keeps its own.
        combined-status-line (when (and (:snapshot-loaded? snapshot)
                                        (:capital-ready? preview-snapshot)
                                        (:execution-ready? preview-snapshot)
                                        (= :succeeded (:status history-load-state)))
                               "Portfolio snapshot and optimizer history loaded.")]
    [:aside {:class ["optimizer-context-rail" "min-h-0"]
             :data-role "portfolio-optimizer-right-rail"}
     (summary-card draft readiness)
     ;; The full Return views editor renders only while the views-aware model is
     ;; live; when views are inert the section is demoted to a one-line note
     ;; after Data health (see below) so inactive functionality never competes
     ;; with readiness warnings.
     (when views-active?
       [:section {:class ["optimizer-setup-panel" "border" "border-base-300" "bg-base-100/90" "p-3"]
                  :data-role "portfolio-optimizer-assumptions-rail"
                  :replicant/key "return-views-editor"}
        [:p {:class eyebrow-class} "Return views"]
        [:div {:class ["mt-3"]}
         (scenario-objective-menu/views-editor-section
          draft
          state
          (:result last-successful-run)
          readiness
          ;; OPEN by default: under Maximum Sharpe the per-asset return
          ;; forecasts are the core input driving the result — hiding them
          ;; behind a closed disclosure made the optimizer look more objective
          ;; than it is (owner + expert review, 2026-07-04). Still collapsible
          ;; so the user can tuck it away; the rows list is height-capped in
          ;; CSS so Data health stays reachable on large universes.
          {:container-role "portfolio-optimizer-setup-use-my-views-editor"
           :title "Used by Maximum Sharpe"
           :collapsible? true
           :open? true
           :description "Edit any return to save it as your view. Saved views override implied returns; the rest use the implied baseline."})]])
     (when status-visible?
       [:section {:class ["optimizer-setup-panel" "border-t" "border-base-300" "bg-base-100/90" "p-3"]
                  :data-role "portfolio-optimizer-trust-freshness-panel"
                  :replicant/key "data-health"}
        [:p {:class eyebrow-class} "Data health"]
        ;; The verdict is the section's headline — the first thing scanned —
        ;; not a small chip competing with the warning cards below it.
        (let [{:keys [level label issue-count]} (:status readiness-model)]
          [:p {:class ["mt-1" "text-[0.8125rem]" "font-semibold"
                       (case level
                         :blocked "text-error"
                         :caution "text-warning"
                         :ready "text-success"
                         "text-trading-muted")]
               :data-role "portfolio-optimizer-data-health-status"}
           ;; The count keeps the verdict meaningful even when the warning
           ;; cards sit below other panels ("Ready with cautions · 3 issues").
           (if (and (pos? (or issue-count 0))
                    (contains? #{:caution :blocked} level))
             (str label " · " issue-count
                  (if (= 1 issue-count) " issue" " issues"))
             label)])
        [:p {:class ["mt-1.5" "text-[0.75rem]" "leading-[1.45]" "text-trading-muted"]}
         (or combined-status-line snapshot-line)]
        (optimization-progress-panel/progress-panel optimization-progress)
        (when readiness-visible?
          (setup-readiness-panel/readiness-panel
           readiness history-load-state
           {:suppress-copy? (some? combined-status-line)}))
        (when run-visible?
          (run-status-panel/run-status-panel run-state))
        (run-status-panel/last-successful-run-panel run-state last-successful-run)
        (when (and current-result? (:result last-successful-run))
          (let [result-path* (or result-path
                                 (portfolio-routes/portfolio-optimize-scenario-path "draft"))]
            [:div {:class ["mt-3" "grid" "grid-cols-2" "gap-2"]
                   :data-role "portfolio-optimizer-results-links"}
             [:button {:type "button"
                       :class ["border" "border-warning/60" "bg-warning/10"
                               "px-3" "py-2" "text-center" "text-[0.8125rem]" "font-medium" "text-warning"]
                       :data-role "portfolio-optimizer-results-link"
                       :on {:click [[:actions/navigate result-path*]]}}
              "Results"]
             [:button {:type "button"
                       :class ["border" "border-primary/50" "bg-primary/10"
                               "px-3" "py-2" "text-center" "text-[0.8125rem]" "font-medium" "text-primary"]
                       :data-role "portfolio-optimizer-rebalance-link"
                       :on {:click [[:actions/navigate result-path*]
                                    [:actions/open-portfolio-optimizer-execution]]}}
              "Review & execute"]]))
        (when read-only-message
          [:p {:class ["mt-3" "border" "border-warning/40" "bg-warning/10" "p-2"
                       "text-[0.8125rem]" "text-warning"]}
           read-only-message])])
     ;; Demoted inactive note: one line + the one-click way to make views
     ;; matter, ranked last — after the contract and Data health.
     (when-not views-active?
       [:section {:class ["optimizer-setup-panel" "border" "border-base-300" "bg-base-100/90" "p-3"]
                  :data-role "portfolio-optimizer-assumptions-rail"
                  :replicant/key "return-views-inactive"}
        [:div {:class ["flex" "items-baseline" "justify-between" "gap-2"]}
         [:p {:class eyebrow-class} "Return views"]
         [:span {:class ["font-mono" "text-[0.6875rem]" "uppercase" "tracking-[0.1em]"
                         "text-trading-muted/50"]
                 :data-role "portfolio-optimizer-return-views-inactive"}
          (if min-variance? "Not used by Minimum risk" "Not used")]]
        [:p {:class ["mt-1.5" "text-[0.6875rem]" "leading-[1.4]" "text-trading-muted"]}
         (if min-variance?
           "Minimum risk ignores expected-return forecasts."
           "This return model uses the historical estimate directly.")]
        [:button {:type "button"
                  :class ["mt-2" "border" "border-base-300" "bg-base-200/40" "px-2"
                          "py-1" "text-[0.6875rem]" "font-semibold" "text-trading-muted"
                          "hover:bg-base-200/60"]
                  :data-role "portfolio-optimizer-return-views-activate"
                  :on {:click (if min-variance?
                                [[:actions/apply-portfolio-optimizer-setup-preset :max-sharpe]]
                                [[:actions/set-portfolio-optimizer-return-model-kind
                                  :black-litterman]])}}
         (if min-variance? "Switch to Maximum Sharpe" "Use my views")]])]))
