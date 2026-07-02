(ns hyperopen.views.portfolio.optimize.setup-context
  (:require [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.portfolio.routes :as portfolio-routes]
            [hyperopen.views.portfolio.optimize.optimization-progress-panel :as optimization-progress-panel]
            [hyperopen.views.portfolio.optimize.run-status-panel :as run-status-panel]
            [hyperopen.views.portfolio.optimize.scenario-objective-menu :as scenario-objective-menu]
            [hyperopen.views.portfolio.optimize.setup-controls :as controls]
            [hyperopen.views.portfolio.optimize.setup-readiness-panel :as setup-readiness-panel]))

(def ^:private eyebrow-class
  ["font-mono" "text-[0.625rem]" "font-semibold" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"])

(defn- contract-row
  [label value]
  [:div {:class ["grid" "grid-cols-[74px_minmax(0,1fr)]" "items-baseline" "gap-2"]}
   [:span {:class ["font-mono" "text-[0.575rem]" "font-semibold" "uppercase"
                   "tracking-[0.1em]" "text-trading-muted/60"]}
    label]
   [:span {:class ["min-w-0" "text-[0.65625rem]" "font-medium" "leading-[1.4]"
                   "text-trading-text"]}
    value]])

(defn- summary-card
  "The scenario contract: the exact universe/objective/model/constraint policy the
  solver will receive, as a labeled stack the user can verify at a glance instead
  of inferring it from scattered controls. Derived output, not primary input."
  [draft]
  (let [{:keys [preset-label asset-count universe-source-kind objective-label
                return-label risk-label constraints-line]}
        (optimizer-view-model/setup-summary-card-model draft {:labelize controls/labelize})]
    [:section {:class ["optimizer-setup-panel" "border" "border-base-300" "bg-base-100/90" "p-3"]
               :data-role "portfolio-optimizer-setup-summary-card"}
     [:div {:class ["flex" "items-baseline" "justify-between" "gap-2"]}
      [:p {:class eyebrow-class} "Scenario contract"]
      [:span {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.1em]"
                      "text-trading-muted/70"]}
       preset-label]]
     [:div {:class ["mt-2" "space-y-1"]}
      (contract-row "Universe"
                    (str asset-count " assets"
                         (case universe-source-kind
                           :holdings " · from holdings"
                           :custom " · custom"
                           nil)))
      (contract-row "Objective" objective-label)
      (contract-row "Model" (str return-label " · " risk-label))
      (contract-row "Constraints"
                    [:span {:class ["font-mono" "text-[0.625rem]" "text-trading-muted"]}
                     constraints-line])]]))

(defn context-rail
  [{:keys [draft state readiness snapshot preview-snapshot run-state optimization-progress
           history-load-state last-successful-run current-result? result-path]}]
  (let [bl? (= :black-litterman (get-in draft [:return-model :kind]))
        progress-visible? (contains? #{:running :succeeded :failed}
                                     (:status optimization-progress))
        readiness-visible? (or (contains? #{:loading :failed :succeeded}
                                          (:status history-load-state))
                               (contains? #{:no-eligible-history
                                            :incomplete-history
                                            :missing-history-assumptions
                                            :history-loading}
                                          (:reason readiness))
                               (seq (:warnings readiness)))
        run-visible? (not= :idle (:status run-state))
        last-run-visible? (:result last-successful-run)
        read-only-message (get-in preview-snapshot [:account :read-only-message])
        status-visible? (or progress-visible?
                            readiness-visible?
                            run-visible?
                            last-run-visible?
                            read-only-message)]
    [:aside {:class ["optimizer-context-rail" "min-h-0"]
             :data-role "portfolio-optimizer-right-rail"}
     (summary-card draft)
     ;; The non-BL "why this preset is safe" copy moved to a collapsed note below the center
     ;; controls; only the Black-Litterman belief editor remains as a right-rail panel.
     (when bl?
       [:section {:class ["optimizer-setup-panel" "border" "border-base-300" "bg-base-100/90" "p-3"]
                  :data-role "portfolio-optimizer-assumptions-rail"}
        [:p {:class eyebrow-class} "Edit views"]
        [:div {:class ["mt-3"]}
         (scenario-objective-menu/views-editor-section
          draft
          state
          (:result last-successful-run)
          readiness
          {:container-role "portfolio-optimizer-setup-use-my-views-editor"
           :title "Your views"
           :description "Change annualized return views and confidence, then run the recommendation."})]])
     (when status-visible?
       [:section {:class ["optimizer-setup-panel" "border-t" "border-base-300" "bg-base-100/90" "p-3"]
                  :data-role "portfolio-optimizer-trust-freshness-panel"}
        [:p {:class eyebrow-class} "Trust & Freshness"]
        [:p {:class ["mt-2" "text-[0.6875rem]" "leading-[1.45]" "text-trading-muted"]}
         (cond
           (not (:snapshot-loaded? snapshot))
           (if (= :manual (get-in preview-snapshot [:capital :source]))
             "Manual capital base is being used for preview sizing."
             "Current portfolio snapshot is not loaded yet.")
           (not (:capital-ready? preview-snapshot))
           "Current portfolio snapshot is available, but no positive capital base is available for preview sizing."
           (not (:execution-ready? preview-snapshot))
           "Current portfolio snapshot is available in read-only mode."
           :else
           "Current portfolio snapshot is available.")]
        (optimization-progress-panel/progress-panel optimization-progress)
        (when readiness-visible?
          (setup-readiness-panel/readiness-panel readiness history-load-state))
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
                               "px-3" "py-2" "text-center" "text-[0.6875rem]" "font-medium" "text-warning"]
                       :data-role "portfolio-optimizer-results-link"
                       :on {:click [[:actions/navigate result-path*]]}}
              "Results"]
             [:button {:type "button"
                       :class ["border" "border-primary/50" "bg-primary/10"
                               "px-3" "py-2" "text-center" "text-[0.6875rem]" "font-medium" "text-primary"]
                       :data-role "portfolio-optimizer-rebalance-link"
                       :on {:click [[:actions/navigate result-path*]
                                    [:actions/open-portfolio-optimizer-execution]]}}
              "Review & execute"]]))
        (when read-only-message
          [:p {:class ["mt-3" "border" "border-warning/40" "bg-warning/10" "p-2"
                       "text-[0.6875rem]" "text-warning"]}
           read-only-message])])]))
