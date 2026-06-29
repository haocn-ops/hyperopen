(ns hyperopen.views.portfolio.optimize.setup-actions
  (:require [hyperopen.views.portfolio.optimize.setup-controls :as controls]))

(defn model-assumptions-panel
  []
  [:section {:class ["optimizer-note" "optimizer-model-assumptions-note"
                     "border" "border-base-300" "bg-base-100/90"]
             :data-role "portfolio-optimizer-model-assumptions-panel"
             :data-optimizer-note "true"}
   [:p {:class controls/eyebrow-class} "What this model assumes"]
   [:ul {:class ["mt-1" "space-y-px" "text-[0.65625rem]" "leading-[1.32]" "text-trading-muted"]}
    [:li "Returns are roughly normal at the chosen horizon."]
    [:li "Past covariance is informative about future covariance."]
    [:li "Cross-margin is treated as one book."]
    [:li "Tail risk and drawdown are not modeled in this setup pass."]]])

(defn- action-objective-label
  [objective-kind]
  (case objective-kind
    :max-sharpe "Maximum Sharpe"
    :target-volatility "Target volatility"
    :target-return "Target return"
    "Minimum variance"))

(defn- action-model-label
  [return-kind risk-kind]
  (cond
    (= :black-litterman return-kind) "posterior views"
    (= :mixed-frequency risk-kind) "mixed-frequency covariance"
    (= :sample-covariance risk-kind) "sample historical returns"
    :else "stabilized historical returns"))

(defn- run-status-label
  [reason]
  (case reason
    :history-loading "Loading history"
    :no-eligible-history "No usable history"
    :incomplete-history "History incomplete"
    :missing-history-assumptions "Needs assumptions"
    :missing-black-litterman-views "Add a view"
    :missing-universe "Add assets to run"
    "Not ready to run"))

(defn run-status
  "Pure derivation of the Run button + status-pill state from the run gate and
  readiness. The pill never reads green \"Ready to run\" while readiness is not
  runnable; instead it names the blocking reason so the CTA cannot contradict the
  Readiness panel beside it."
  [{:keys [run-triggerable? running? readiness asset-count]}]
  (let [runnable? (if (some? readiness)
                    (boolean (:runnable? readiness))
                    true)
        ready? (boolean (and run-triggerable? runnable?))]
    (cond
      running?
      {:ready? false :tone :busy :label "Optimizing"}

      ready?
      {:ready? true :tone :ready :label "Ready to run"}

      (zero? (or asset-count 0))
      {:ready? false :tone :empty :label "Add assets to run"}

      :else
      {:ready? false :tone :blocked :label (run-status-label (:reason readiness))})))

(defn results-tab-nav-actions
  "Click actions that open the results surface at `tab` from the setup route.

  `navigate` switches to the scenario route and
  `set-portfolio-optimizer-results-tab` selects the tab. nexus applies both
  `:effects/save` projections (router path, then the results tab) before the
  route loaders run, so the results surface opens on the requested tab instead of
  re-deriving it from the URL."
  [result-path tab]
  [[:actions/navigate result-path]
   [:actions/set-portfolio-optimizer-results-tab tab]])

(defn- result-nav-button
  [{:keys [result-path tab label data-role accent?]}]
  [:button {:type "button"
            :class (into ["border" "px-3" "py-2" "whitespace-nowrap"
                          "text-[0.6875rem]" "font-semibold" "scroll-mb-12"]
                         (if accent?
                           ["border-primary/50" "bg-primary/10" "text-primary"]
                           ["border-base-300" "bg-base-200/30" "text-trading-text"]))
            :data-role data-role
            :on {:click (results-tab-nav-actions result-path tab)}}
   label])

(defn setup-bottom-actions
  [{:keys [draft readiness running? run-triggerable? saving-scenario? solved-run? result-path]}]
  (let [asset-count (count (:universe draft))
        black-litterman? (= :black-litterman (get-in draft [:return-model :kind]))
        objective-copy (action-objective-label (get-in draft [:objective :kind]))
        model-copy (action-model-label (get-in draft [:return-model :kind])
                                       (get-in draft [:risk-model :kind]))
        status (run-status {:run-triggerable? run-triggerable?
                            :running? running?
                            :readiness readiness
                            :asset-count asset-count})
        ready? (:ready? status)]
    [:section {:class ["optimizer-setup-actions"
                       "relative" "z-[180]" "mt-2" "flex" "flex-col" "items-start" "gap-3"
                       "border" "border-base-300" "bg-[#101518]"
                       "px-7" "py-[14px]" "scroll-mb-12" "leading-4"
                       "sm:flex-row" "sm:flex-wrap" "sm:items-center" "sm:gap-4"]
               :data-role "portfolio-optimizer-setup-bottom-actions"}
     [:button {:type "button"
               :class ["optimizer-primary-action"
                       "border" "border-warning/70" "bg-warning/80" "px-6" "py-2.5"
                       "whitespace-nowrap" "text-[0.71875rem]" "font-semibold" "text-base-100"
                       "shadow-[0_0_0_1px_rgba(0,0,0,0.25)]"
                       "scroll-mb-12"
                       "disabled:cursor-not-allowed" "disabled:border-base-300"
                       "disabled:bg-base-200/30" "disabled:text-trading-muted"
                       "disabled:shadow-none"]
               :data-role "portfolio-optimizer-run-draft"
               ;; The button stays clickable while a universe is present even when
               ;; readiness is not runnable: clicking is the intentional
               ;; "run retries anything still missing" history-load affordance.
               ;; Honesty lives in the status pill below, not by disabling the CTA.
               :disabled (not run-triggerable?)
               :on {:click [(if black-litterman?
                               [:actions/apply-portfolio-optimizer-objective-menu-selection-and-run]
                               [:actions/run-portfolio-optimizer-from-draft])]}}
      (if running? "Running Optimization" "Run on safe defaults")]
     [:button {:type "button"
               :class ["border" "border-base-300" "bg-base-200/30" "px-3" "py-2"
                       "whitespace-nowrap" "text-[0.6875rem]" "font-semibold" "text-trading-text"
                       "scroll-mb-12"
                       "disabled:cursor-not-allowed" "disabled:text-trading-muted"]
               :data-role "portfolio-optimizer-save-scenario"
               :disabled (or (not solved-run?) saving-scenario?)
               :on {:click [[:actions/open-portfolio-optimizer-scenario-save-modal]]}}
      (if saving-scenario? "Saving" "Save draft")]
     (when (and solved-run? result-path)
       (result-nav-button {:result-path result-path
                           :tab :recommendation
                           :label "View results"
                           :data-role "portfolio-optimizer-view-results"}))
     (when (and solved-run? result-path)
       (result-nav-button {:result-path result-path
                           :tab :rebalance
                           :label "Rebalance preview"
                           :data-role "portfolio-optimizer-view-rebalance"
                           :accent? true}))
     [:div {:class ["flex" "max-w-full" "flex-col" "items-start" "gap-1.5" "font-mono"
                    "sm:ml-auto" "sm:min-w-[220px]" "sm:items-end" "sm:text-right"]}
      [:div {:class ["flex" "items-center" "gap-2" "text-[0.6875rem]" "font-semibold"
                     "whitespace-nowrap" "uppercase" "tracking-[0.14em]"
                     (if ready? "text-[#5a5f68]" "text-[#444951]")
                     "sm:justify-end"]
             :data-role "portfolio-optimizer-setup-bottom-actions-status-meta"
             :data-run-status (name (:tone status))}
       (when ready?
         [:span {:class ["h-2" "w-2" "rounded-full" "bg-success"]
                 :aria-hidden "true"}])
       (when (= :blocked (:tone status))
         [:span {:class ["h-2" "w-2" "rounded-full" "bg-warning"]
                 :aria-hidden "true"}])
       [:span (:label status)]
       [:span {:class ["text-trading-muted/50"]} "·"]
       [:span (str asset-count " assets")]]
      [:div {:class ["max-w-full" "text-[0.625rem]" "font-semibold" "normal-case"
                     "tracking-normal" "text-trading-muted" "sm:max-w-[260px]"]
             :data-role "portfolio-optimizer-setup-bottom-actions-status-detail"}
       (str "Solving " objective-copy " · " model-copy)]]]))
