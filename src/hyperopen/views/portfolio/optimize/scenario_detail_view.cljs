(ns hyperopen.views.portfolio.optimize.scenario-detail-view
  (:require [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.portfolio.routes :as portfolio-routes]
            [hyperopen.system :as app-system]
            [hyperopen.views.portfolio.optimize.execution-tab :as execution-tab]
            [hyperopen.views.portfolio.optimize.format :as opt-format]
            [hyperopen.views.portfolio.optimize.inputs-tab :as inputs-tab-view]
            [hyperopen.views.portfolio.optimize.optimization-progress-panel :as optimization-progress-panel]
            [hyperopen.views.portfolio.optimize.rebalance-tab :as rebalance-tab-view]
            [hyperopen.views.portfolio.optimize.results-panel :as results-panel]
            [hyperopen.views.portfolio.optimize.scenario-objective-menu :as objective-menu]
            [hyperopen.views.portfolio.optimize.target-sigma :as target-sigma]
            [hyperopen.views.portfolio.optimize.tracking-panel :as tracking-panel]
            [nexus.registry :as nxr]))

(def ^:private tabs
  [{:key :recommendation :label "Recommendation" :data-role "portfolio-optimizer-scenario-tab-recommendation"}
   {:key :rebalance :label "Rebalance preview" :data-role "portfolio-optimizer-scenario-tab-rebalance"}
   {:key :execution :label "Execution" :data-role "portfolio-optimizer-scenario-tab-execution"
    ;; Entering Execution rebuilds the plan snapshot, so route the tab click through
    ;; the open action rather than the bare set-tab.
    :on [[:actions/open-portfolio-optimizer-execution]]}
   {:key :tracking :label "Tracking" :data-role "portfolio-optimizer-scenario-tab-tracking"}
   {:key :inputs :label "Inputs" :data-role "portfolio-optimizer-scenario-tab-inputs"}])

(defn- copy-scenario-link!
  [scenario-id]
  (fn [_event]
    (let [clipboard (some-> js/globalThis .-navigator .-clipboard)]
      (when (some-> clipboard .-writeText)
        (.writeText clipboard
                    (str (.-origin js/location)
                         (portfolio-routes/portfolio-optimize-scenario-path scenario-id)))))))

(defn- scenario-header
  [{:keys [scenario-id
           scenario-name
           active-scenario
           run-state
           running?
           scenario-save-state
           current-result?
           result
           refinement]}]
  (let [status (:status active-scenario)
        read-only? (true? (:read-only? active-scenario))
        running? (or running?
                     (= :running (:status run-state)))
        save-state (:status scenario-save-state)
        saving? (= :saving save-state)
        save-disabled? (or saving?
                           (not current-result?))
        can-refine? (boolean (:can-refine? refinement))
        solved? (= :solved (:status result))]
    [:header {:class ["optimizer-scenario-header"
                      "border-b"
                      "border-base-300"
                      "bg-base-100/95"
                      "px-5"
                      "py-3"]
              :data-role "portfolio-optimizer-scenario-header"}
     [:div {:class ["flex" "flex-wrap" "items-end" "justify-between" "gap-4"]}
      [:div
       [:p {:class ["text-[0.65rem]"
                    "font-semibold"
                    "uppercase"
                    "tracking-[0.24em]"
                    "text-trading-muted"]}
        "Scenario"]
       [:div {:class ["mt-1" "flex" "flex-wrap" "items-center" "gap-2"]}
        [:h1 {:class ["text-lg" "font-medium" "tracking-[-0.01em]"]}
         scenario-name]
        [:span {:class ["text-[0.8125rem]" "text-trading-muted"]}
         (str "/ scenario id " scenario-id
              (when read-only? " · read-only"))]
        [:span {:class ["optimizer-status-tag"
                        "rounded-full"
                        "border"
                        "border-base-300"
                        "bg-base-200/60"
                        "px-2"
                        "py-0.5"
                        "text-[0.58rem]"
                        "font-semibold"
                        "uppercase"
                        "tracking-[0.14em]"
                        "text-trading-muted"]
                :data-role "portfolio-optimizer-scenario-status-tag"}
         (opt-format/keyword-label status)]]]
      [:div {:class ["flex" "flex-wrap" "items-center" "gap-2"]}
       [:button {:type "button"
                 :class ["border"
                         "border-base-300"
                         "bg-base-200/30"
                         "px-2"
                         "py-1"
                         "font-mono"
                         "text-[0.65625rem]"
                         "font-semibold"
                         "text-trading-muted"]
                 :aria-label "More scenario actions"}
        "..."]
       [:button {:type "button"
                 :class ["optimizer-refine-action"
                         "rounded-lg"
                         "border"
                         "border-primary/60"
                         "bg-primary/20"
                         "px-2.5"
                         "py-1"
                         "text-[0.65625rem]"
                         "font-semibold"
                         "text-primary"
                         "transition-colors"
                         "hover:bg-primary/30"
                         "disabled:cursor-not-allowed"
                         "disabled:border-base-300"
                         "disabled:bg-base-200/40"
                         "disabled:text-trading-muted"]
                 :data-role "portfolio-optimizer-scenario-refine"
                 :disabled (not can-refine?)
                 :on (when can-refine?
                       {:click [[:actions/refine-portfolio-optimizer]]})}
        "Refine optimization"]
       [:button {:type "button"
                 :class ["optimizer-primary-action"
                         "rounded-lg"
                         "border"
                         "border-base-300"
                         "bg-base-200/40"
                         "px-2.5"
                         "py-1"
                         "text-[0.65625rem]"
                         "font-semibold"
                         "text-trading-text"
                         "disabled:cursor-not-allowed"
                         "disabled:text-trading-muted"]
                 :data-role "portfolio-optimizer-scenario-save"
                 :disabled save-disabled?
                 :on (when-not save-disabled?
                       {:click [[:actions/open-portfolio-optimizer-scenario-save-modal]]})}
        (if saving? "Saving" "Save scenario")]
       [:button {:type "button"
                 :class ["rounded-lg"
                         "border"
                         "border-primary/50"
                         "bg-primary/10"
                         "px-2.5"
                         "py-1"
                         "text-[0.65625rem]"
                         "font-semibold"
                         "text-primary"
                         "disabled:cursor-not-allowed"
                         "disabled:border-base-300"
                         "disabled:bg-base-200/40"
                         "disabled:text-trading-muted"]
                 :data-role "portfolio-optimizer-scenario-rerun"
                 :disabled running?
                 :on {:click [[:actions/run-portfolio-optimizer-from-draft]]}}
        (cond
          running? "Running"
          :else "Rerun")]
       (when solved?
         [:button {:type "button"
                   :class ["optimizer-review-rebalance-action"
                           "rounded-lg"
                           "border"
                           "border-primary/50"
                           "bg-primary/10"
                           "px-2.5"
                           "py-1"
                           "text-[0.65625rem]"
                           "font-semibold"
                           "text-primary"
                           "transition-colors"
                           "hover:bg-primary/30"]
                   :data-role "portfolio-optimizer-scenario-review-rebalance"
                   :on {:click [[:actions/set-portfolio-optimizer-results-tab :rebalance]]}}
          "Review rebalance"])]]]))

(defn- auto-recompute-stale-scenario!
  [_node]
  (when app-system/store
    (nxr/dispatch app-system/store
                  nil
                  [[:actions/auto-recompute-stale-portfolio-optimizer-scenario]])))

(defn- kpi-delta-class
  [delta {:keys [positive negative]}]
  (cond
    (not (opt-format/finite-number? delta)) "text-trading-muted"
    (pos? delta) positive
    (neg? delta) negative
    :else "text-trading-muted"))

(defn- kpi-card
  ([data-role label value delta]
   (kpi-card data-role label value delta "text-trading-green"))
  ([data-role label value delta delta-class]
   [:div {:class ["optimizer-kpi-card" "border-r" "border-base-300" "px-3" "py-2.5" "last:border-r-0"]
          :data-role data-role}
    [:p {:class ["font-mono"
                 "text-[0.6rem]"
                 "uppercase"
                 "tracking-[0.08em]"
                 "text-trading-muted/70"]}
     label]
    [:p {:class ["mt-1" "font-mono" "text-sm" "font-semibold" "tabular-nums" "text-trading-text"]}
     value]
    [:p {:class ["mt-0.5" "font-mono" "text-[0.65rem]" "tabular-nums" delta-class]}
     delta]]))

(defn- positive-number?
  [value]
  (and (opt-format/finite-number? value)
       (pos? value)))

(defn- sharpe-from
  [performance expected-return volatility]
  (or (when (opt-format/finite-number? (:in-sample-sharpe performance))
        (:in-sample-sharpe performance))
      (when (and (opt-format/finite-number? expected-return)
                 (positive-number? volatility))
        (/ expected-return volatility))))

(defn- format-decimal-delta
  [value]
  (if (opt-format/finite-number? value)
    (str (when (pos? value) "+")
         (opt-format/format-decimal value))
    "N/A"))

(defn- kpi-strip
  [result*]
  (let [preview (:rebalance-preview result*)
        performance (:performance result*)
        current-performance (:current-performance result*)
        diagnostics (:diagnostics result*)
        current-return (:current-expected-return result*)
        current-vol (:current-volatility result*)
        target-return (:expected-return result*)
        target-vol (:volatility result*)
        return-delta (when (opt-format/finite-number? current-return)
                       (- (or target-return 0) current-return))
        vol-delta (when (opt-format/finite-number? current-vol)
                    (- (or target-vol 0) current-vol))
        current-sharpe (sharpe-from current-performance
                                    current-return
                                    current-vol)
        target-sharpe (sharpe-from performance
                                   target-return
                                   target-vol)
        sharpe-delta (when (and (opt-format/finite-number? current-sharpe)
                                (opt-format/finite-number? target-sharpe))
                       (- (or target-sharpe 0) current-sharpe))
        gross (:gross-exposure diagnostics)
        net (:net-exposure diagnostics)]
    [:section {:class ["optimizer-scenario-kpi-strip"
                       "grid" "grid-cols-2" "border-y" "border-base-300" "bg-base-100/95" "lg:grid-cols-5"]
               :data-role "portfolio-optimizer-scenario-kpi-strip"}
     (kpi-card "portfolio-optimizer-scenario-kpi-volatility"
               "Volatility · current → target"
               (if (opt-format/finite-number? current-vol)
                 [:span [:span {:class ["text-trading-muted"]} (opt-format/format-pct current-vol)]
                  " → "
                  (opt-format/format-pct target-vol)]
                 (opt-format/format-pct target-vol))
               (if (opt-format/finite-number? current-vol)
                 (str (opt-format/format-pct-delta vol-delta) " · annualized")
                 "annualized")
               (kpi-delta-class vol-delta
                                {:positive "text-warning"
                                 :negative "text-trading-green"}))
     (kpi-card "portfolio-optimizer-scenario-kpi-expected-return"
               "Expected Return · current → target"
               (if (opt-format/finite-number? current-return)
                 [:span [:span {:class ["text-trading-muted"]} (opt-format/format-pct current-return)]
                  " → "
                  (opt-format/format-pct target-return)]
                 (opt-format/format-pct target-return))
               (if (opt-format/finite-number? current-return)
                 (str (opt-format/format-pct-delta return-delta) " · annualized")
                 "annualized")
               (kpi-delta-class return-delta
                                 {:positive "text-trading-green"
                                  :negative "text-warning"}))
     (kpi-card "portfolio-optimizer-scenario-kpi-sharpe"
               "Sharpe · current → target"
               (if (opt-format/finite-number? current-sharpe)
                 [:span [:span {:class ["text-trading-muted"]} (opt-format/format-decimal current-sharpe)]
                  " → "
                  (opt-format/format-decimal target-sharpe)]
                 (opt-format/format-decimal target-sharpe))
               (if (opt-format/finite-number? current-sharpe)
                 (str (format-decimal-delta sharpe-delta) " · raw Sharpe change")
                 "raw Sharpe")
               (kpi-delta-class sharpe-delta
                                {:positive "text-trading-green"
                                 :negative "text-warning"}))
     (kpi-card "portfolio-optimizer-scenario-kpi-turnover"
               "Turnover Required"
               (opt-format/format-pct (:turnover diagnostics))
               (str "rebalance " (opt-format/keyword-label (:status preview))))
     (kpi-card "portfolio-optimizer-scenario-kpi-rebalance"
               "Gross / Net"
               (str (opt-format/format-multiple gross) " / " (opt-format/format-multiple net))
               "constraint utilization")]))

(defn- stale-banner
  [stale?]
  (when stale?
    [:section {:class ["rounded-xl"
                       "border"
                       "border-warning/50"
                       "bg-warning/10"
                       "p-3"
                       "text-sm"
                       "text-warning"]
               :data-role "portfolio-optimizer-scenario-stale-banner"
               :replicant/on-render auto-recompute-stale-scenario!}
     [:span {:class ["font-semibold"]} "Stale"]
     [:span {:class ["ml-2"]}
      "Draft inputs differ from the last successful run. Refreshing automatically while previous output stays visible."]]))

(defn- provenance-strip
  [{:keys [state draft result readiness scenario-id]}]
  (let [result* result
        constraints (:constraints draft)
        objective-key (objective-menu/current-objective-menu-key draft result*)
        objective-label (objective-menu/objective-label objective-key draft)]
    (let [field (fn [label value]
                  [:div {:class ["border-r" "border-base-300" "px-3" "py-2"]}
                   [:span {:class ["block" "font-mono" "text-[0.56rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
                    label]
                   [:span {:class ["mt-0.5" "block" "text-[0.7rem]" "font-medium" "text-trading-text"]}
                    value]])
          fields [[:div {:class ["optimizer-provenance-objective"
                                  "border-r"
                                  "border-base-300"
                                  "px-3"
                                  "py-2"]}
                   [:span {:class ["block" "font-mono" "text-[0.56rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
                    "Objective"]
                   [:div {:class ["optimizer-objective-anchor"
                                   "relative"
                                   "inline-block"]}
                    (objective-menu/objective-trigger
                     objective-label
                     (objective-menu/objective-menu-open? state))
                    (objective-menu/objective-menu state draft result* readiness)]]
                  (field "Returns"
                         (opt-format/display-label (or (:return-model result*)
                                                       (get-in draft [:return-model :kind]))))
                  (field "Risk"
                         (opt-format/display-label (or (:risk-model result*)
                                                       (get-in draft [:risk-model :kind]))))
                  (field "Horizon" "Annualized")
                  (field "Funding"
                         (if (seq (:return-decomposition-by-instrument result*))
                           "Included"
                           "Pending run"))
                  (field "Constraints"
                         (str "gross ≤ " (opt-format/format-decimal (:gross-max constraints))
                              " · cap " (opt-format/format-pct (:max-asset-weight constraints))))
                  [:div {:class ["ml-auto" "flex" "items-center" "gap-2" "px-3" "py-2" "font-mono" "text-[0.62rem]" "text-trading-muted"]}
                   [:span "data as of " [:span {:class ["text-trading-muted"]} (opt-format/format-time (:as-of-ms result*))]]
                   [:span "·"]
                   [:a {:class ["text-trading-muted"]
                        :href (portfolio-routes/portfolio-optimize-scenario-path scenario-id)}
                    scenario-id]
                   [:button {:type "button"
                             :class ["border" "border-base-300" "bg-base-200/40" "px-2" "py-1" "font-mono"
                                     "text-[0.58rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted"]
                             :data-role "portfolio-optimizer-copy-scenario-link"
                             :on {:click (copy-scenario-link! scenario-id)}}
                    "Copy link"]]]]
      (into
       [:section {:class ["optimizer-provenance-strip"
                          "flex" "flex-wrap" "items-stretch" "border-y" "border-base-300" "bg-base-200/40"]
                  :data-role "portfolio-optimizer-provenance-strip"}]
       fields))))

(defn- scenario-tabs
  [_scenario-id selected-tab]
  (into
   [:nav {:class ["optimizer-scenario-tabs"
                  "flex" "h-8" "items-stretch" "border-b" "border-base-300" "bg-base-100/95" "pl-4"]
          :data-role "portfolio-optimizer-scenario-tabs"}]
   (map (fn [{:keys [key label data-role on]}]
          [:button {:type "button"
                    :class (cond-> ["flex" "items-center" "border-b" "px-4" "text-[0.7rem]" "font-medium" "transition-colors"]
                             (= key selected-tab) (conj "border-primary" "text-trading-text")
                             (not= key selected-tab) (conj "border-transparent" "text-trading-text/60" "hover:text-trading-text"))
                    :data-role data-role
                    :aria-current (when (= key selected-tab) "page")
                    :on {:click (or on [[:actions/set-portfolio-optimizer-results-tab key]])}}
           label])
        tabs)))

(defn- empty-tab
  [data-role title body]
  [:section {:class ["rounded-xl" "border" "border-base-300" "bg-base-100/95" "p-4"]
             :data-role data-role}
   [:p {:class ["text-[0.65rem]"
                "font-semibold"
                "uppercase"
                "tracking-[0.24em]"
                "text-trading-muted"]}
    title]
   [:p {:class ["mt-2" "text-sm" "text-trading-muted"]} body]])

(defn- solved-result?
  [model]
  (= :solved (:status (:result model))))

(defn- recompute-banner
  [optimization-progress]
  [:section {:class ["border-y"
                     "border-primary/40"
                     "bg-primary/10"
                     "px-4"
                     "py-3"
                     "text-sm"
                     "text-primary"]
             :data-role "portfolio-optimizer-recompute-banner"}
   [:p {:class ["font-semibold"]} "Recomputing recommendation"]
   [:p {:class ["mt-1" "text-trading-muted"]}
    "Keeping the previous allocation visible until the new run finishes."]
   (optimization-progress-panel/progress-panel optimization-progress {:show-header? false})])

(defn- review-rebalance-cta
  "Discoverable bridge from the recommendation read-flow to the rebalance tab.
  Switches tabs in-place (no navigation) so unsaved run state is preserved; the
  spectate/read-only gate lives downstream in the execution modal, so this stays
  enabled in spectate mode (you can still review the rebalance)."
  []
  [:button {:type "button"
            :class ["optimizer-review-rebalance-cta"
                    "mt-3" "flex" "w-full" "items-center" "justify-between" "gap-3"
                    "rounded-lg" "border" "border-primary/50" "bg-primary/10"
                    "px-4" "py-3" "text-left" "text-primary"
                    "transition-colors" "hover:bg-primary/30"]
            :data-role "portfolio-optimizer-recommendation-rebalance-cta"
            :on {:click [[:actions/set-portfolio-optimizer-results-tab :rebalance]]}}
   [:span {:class ["flex" "flex-col" "gap-0.5"]}
    [:span {:class ["text-[0.7rem]" "font-semibold"]}
     "Review rebalance"]
    [:span {:class ["text-[0.62rem]" "font-medium" "text-trading-muted"]}
     "See the trades that move you from current to target allocation."]]
   [:span {:class ["text-sm" "font-semibold"] :aria-hidden "true"} "→"]])

(defn- recommendation-tab
  [{:keys [last-successful-run
           draft
           stale?
           running?
           optimization-progress
           frontier-overlay-mode
           state
           readiness
           constrain-frontier?
           refinement] :as model}]
  (into
   [:section {:class ["space-y-0"]
              :data-role "portfolio-optimizer-recommendation-tab"}]
   (cond
     (solved-result? model)
     (cond-> []
       running? (conj (recompute-banner optimization-progress))
       true (conj (results-panel/results-panel
                   last-successful-run
                   draft
                   {:state state
                    :readiness readiness
                    :stale? (and stale? (not running?))
                    :frontier-overlay-mode frontier-overlay-mode
                    :constrain-frontier? constrain-frontier?
                    :refinement refinement
                    :include-rebalance? false}))
       true (conj (review-rebalance-cta)))

     :else
     [(empty-tab "portfolio-optimizer-recommendation-empty"
                 "Recommendation"
                 "Run or load this scenario to review target allocation, frontier, diagnostics, and rebalance context.")])))

(defn- rebalance-tab
  [{:keys [last-successful-run] :as model}]
  [:section {:class ["space-y-4"]
             :data-role "portfolio-optimizer-rebalance-tab"}
   (if (solved-result? model)
     (rebalance-tab-view/rebalance-tab
      last-successful-run)
     (empty-tab "portfolio-optimizer-rebalance-empty"
                "Rebalance Preview"
                "A rebalance preview is available after a successful optimization run."))])

(defn- tab-body
  [{:keys [state selected-tab] :as model}]
  (case selected-tab
    :rebalance (rebalance-tab model)
    :execution [:section {:class ["space-y-4"]
                          :data-role "portfolio-optimizer-execution-tab-shell"}
                (execution-tab/execution-tab state)]
    :tracking [:section {:class ["space-y-4"]
                         :data-role "portfolio-optimizer-tracking-tab"}
               (tracking-panel/tracking-panel state)]
    :inputs (inputs-tab-view/inputs-tab state)
    (recommendation-tab model)))

(defn- scenario-loading-state
  [scenario-id]
  (empty-tab "portfolio-optimizer-scenario-loading-state"
             "Loading Scenario"
             (str "Scenario " scenario-id " is loading. Retained data from a previous scenario is hidden until the routed scenario is available.")))

(defn scenario-detail-view
  [state route]
  (let [{:keys [scenario-id
                loading?
                state
                selected-tab
                result
                stale?
                running?] :as model} (optimizer-view-model/scenario-detail-model state route)]
    [:section {:class ["portfolio-optimizer" "optimizer-scenario-surface"
                       "space-y-0" "leading-4" "text-trading-text"]
               :data-role "portfolio-optimizer-scenario-detail-surface"
               :data-scenario-id scenario-id}
     (scenario-header model)
     (provenance-strip model)
     (scenario-tabs scenario-id selected-tab)
     (target-sigma/target-sigma-strip model)
     (kpi-strip result)
     (stale-banner (and stale? (not running?)))
     (if loading?
       (scenario-loading-state scenario-id)
       (tab-body model))]))
