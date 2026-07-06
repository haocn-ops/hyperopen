(ns hyperopen.views.portfolio.optimize.setup-history-assumptions
  "Proxy workflow cards for assets that lack optimizer-safe history. Views render
  the view-model cards and dispatch the carried action ids only - no history
  math, no raw payload branching."
  (:require [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.views.portfolio.optimize.setup-controls :as controls]))

(def ^:private card-subtext
  "Insufficient native history. Model behavior using proxies.")

(def ^:private how-this-works-copy
  ["Qualitative prior chooses the proxy set."
   "Quantitative regression adjusts the split."
   "Confidence governs how much the regression can move the prior."
   "Specific risk and the cap keep the optimizer honest."])

(defn- percent-input
  [{:keys [label field role action]}]
  [:label {:class ["block" "border" "border-base-300" "bg-base-200/20" "p-2"]}
   [:span {:class controls/eyebrow-class} label]
   [:div {:class ["mt-2" "flex" "items-center" "gap-1"]}
    [:input {:type "text"
             :inputmode "decimal"
             :class controls/input-class
             :data-role role
             :value (or (:input-text field) "")
             :on {:input [action]}}]
    [:span {:class ["font-mono" "text-[0.8125rem]" "text-trading-muted"]} "%"]]])

(defn- status-chip
  [card]
  (let [id (:instrument-id card)
        configured? (= :configured (:status card))]
    [:span {:class ["border" "px-1.5" "py-0.5" "font-mono" "text-[0.625rem]"
                    "font-semibold" "uppercase" "tracking-[0.1em]"
                    (if configured? "border-success/50" "border-warning/50")
                    (if configured? "text-success" "text-warning")
                    (if configured? "bg-success/10" "bg-warning/10")]
            :data-role (str "portfolio-optimizer-history-assumption-status-" id)
            :data-status (some-> (:status card) name)}
     (:status-label card)]))

(defn- mode-tabs
  "Two-way behavior selector. Selecting a mode seeds that mode's editable
  defaults; switching preserves the user's return/volatility."
  [card]
  (let [id (:instrument-id card)
        active (:mode card)]
    (into [:div {:class ["grid" "grid-cols-2" "gap-1"]
                 :data-role (str "portfolio-optimizer-history-assumption-modes-" id)}]
          (map (fn [{:keys [value label]}]
                 (let [active? (= value active)]
                   [:button {:type "button"
                             :class ["border" "px-2" "py-1.5" "text-[0.75rem]" "font-semibold"
                                     (if active? "border-warning/70" "border-base-300")
                                     (if active? "bg-warning/15" "bg-base-200/30")
                                     (if active? "text-warning" "text-trading-muted")]
                             :aria-pressed (if active? "true" "false")
                             :data-role (str "portfolio-optimizer-history-assumption-mode-"
                                             id "-" (name value))
                             :on {:click [[(get-in card [:actions :set-mode]) id value]]}}
                    label])))
          (:mode-options card))))

(defn- proxy-chips
  [card]
  (let [id (:instrument-id card)]
    (when (seq (:selected-proxies card))
      (into [:div {:class ["flex" "flex-wrap" "gap-1"]
                   :data-role (str "portfolio-optimizer-history-assumption-proxies-" id)}]
            (map (fn [{:keys [instrument-id label loading?]}]
                   [:span {:class ["inline-flex" "items-center" "gap-1" "border"
                                   "border-base-300" "bg-base-200/40" "px-1.5" "py-0.5"
                                   "font-mono" "text-[0.6875rem]" "text-trading-text"]
                           :data-role (str "portfolio-optimizer-history-assumption-proxy-chip-"
                                           id "-" instrument-id)}
                    label
                    ;; A just-picked reference proxy is still fetching history.
                    (when loading?
                      [:span {:class ["text-[0.5625rem]" "uppercase" "tracking-[0.06em]"
                                      "text-trading-muted"]}
                       "loading"])
                    [:button {:type "button"
                              :class ["text-trading-muted" "hover:text-warning"]
                              :aria-label (str "Remove proxy " label)
                              :data-role (str "portfolio-optimizer-history-assumption-proxy-remove-"
                                              id "-" instrument-id)
                              :on {:click [[(get-in card [:actions :toggle-proxy-asset])
                                            id instrument-id false]]}}
                     "x"]]))
            (:selected-proxies card)))))

(defn- proxy-search
  "Typeahead over the WHOLE asset catalog (not just the selected universe):
  type a ticker/name, click a match, and it becomes a proxy chip. A picked proxy
  outside the portfolio is modeled as reference-only (history loaded for
  covariance, never allocated)."
  [card]
  (let [id (:instrument-id card)
        results (:proxy-search-results card)]
    [:div {:class ["relative"]}
     [:input {:type "text"
              :class ["w-full" "border" "border-base-300" "bg-base-100/80" "px-2" "py-1.5"
                      "font-mono" "text-[0.75rem]" "text-trading-text"
                      "outline-none" "focus:border-warning/70"]
              :placeholder "Search any asset to add as a proxy…"
              :data-role (str "portfolio-optimizer-history-assumption-proxy-search-" id)
              :value (or (:proxy-search-query card) "")
              :on {:input [[(get-in card [:actions :set-proxy-search]) id
                            [:event.target/value]]]}}]
     (when (seq results)
       (into [:div {:class ["mt-1" "border" "border-base-300" "bg-base-200/80"
                            "shadow-[0_12px_32px_rgba(0,0,0,0.45)]"]
                    :role "listbox"
                    :data-role (str "portfolio-optimizer-history-assumption-proxy-results-" id)}]
             (map (fn [{:keys [instrument-id label]}]
                    [:button {:type "button"
                              :class ["flex" "w-full" "items-center" "justify-between" "gap-2"
                                      "border-b" "border-base-300" "px-2" "py-1.5" "text-left"
                                      "last:border-b-0" "hover:bg-base-200/60"]
                              :role "option"
                              :data-role (str "portfolio-optimizer-history-assumption-proxy-option-"
                                              id "-" instrument-id)
                              ;; Add the proxy, then clear the search buffer.
                              :on {:click [[(get-in card [:actions :toggle-proxy-asset])
                                            id instrument-id true]
                                           [(get-in card [:actions :set-proxy-search]) id ""]]}}
                     [:span {:class ["truncate" "font-mono" "text-[0.75rem]" "font-semibold"]}
                      label]
                     [:span {:class ["shrink-0" "font-mono" "text-[0.6875rem]" "text-warning"]}
                      "+ add"]]))
             results))]))

(defn- relationship-selector
  [card]
  (let [id (:instrument-id card)
        active (:relationship-strength card)]
    [:div
     [:span {:class controls/eyebrow-class} "Relationship strength"]
     [:p {:class ["mt-1" "text-[0.6875rem]" "text-trading-muted"]}
      "How similar this asset is to the chosen proxies"]
     (into [:div {:class ["mt-1.5" "grid" "grid-cols-3" "gap-1"]}]
           (map (fn [{:keys [value label]}]
                  (let [active? (= value active)]
                    [:button {:type "button"
                              :class ["border" "px-2" "py-1" "text-[0.75rem]" "font-semibold"
                                      (if active? "border-warning/70" "border-base-300")
                                      (if active? "bg-warning/15" "bg-base-200/30")
                                      (if active? "text-warning" "text-trading-muted")]
                              :aria-pressed (if active? "true" "false")
                              :data-role (str "portfolio-optimizer-history-assumption-relationship-"
                                              id "-" (name value))
                              :on {:click [[(get-in card [:actions :set-relationship-strength])
                                            id value]]}}
                     label])))
                (:relationship-options card))]))

(defn- basket-weight-rows
  "One label/percent line per basket member. `role-prefix` disambiguates the
  prior / regression / final panels for tests."
  [card-id role-prefix rows emphasized?]
  (into [:div {:class ["mt-2" "space-y-1"]}]
        (map (fn [{:keys [instrument-id label weight-percent]}]
               [:div {:class ["flex" "items-center" "justify-between" "gap-2"
                              "font-mono" "text-[0.75rem]"]
                      :data-role (str role-prefix card-id "-" instrument-id)}
                [:span {:class [(when emphasized? "font-semibold")]} label]
                [:span {:class [(if emphasized? "text-trading-text" "text-trading-muted")
                                (when emphasized? "font-semibold")]}
                 (str (.toFixed weight-percent 0) "%")]]))
        rows))

(defn- prior-basket-panel
  "Panel A: the source-labeled prior. Never presented as the model's output -
  the final modeled basket below is what drives covariance."
  [card]
  (let [id (:instrument-id card)]
    (when (seq (:prior-basket card))
      [:div {:class ["border" "border-base-300" "bg-base-200/20" "p-2"]
             :data-role (str "portfolio-optimizer-history-assumption-prior-basket-" id)}
       [:span {:class controls/eyebrow-class} "Prior basket"]
       (when (:prior-source-label card)
         [:p {:class ["mt-1" "text-[0.6875rem]" "text-trading-muted"]
              :data-role (str "portfolio-optimizer-history-assumption-prior-source-" id)}
          (str "Source: " (:prior-source-label card))])
       (basket-weight-rows id
                           "portfolio-optimizer-history-assumption-prior-weight-"
                           (:prior-basket card)
                           false)])))

(defn- regression-estimate-panel
  "Panel B: the joint regression's own estimate over the available overlap, or
  the reason it was skipped. Its weights never reach the optimizer directly -
  confidence shrinkage blends them with the prior first."
  [card]
  (let [id (:instrument-id card)
        estimate (:regression-estimate card)
        estimated? (= :estimated (:status estimate))]
    (when estimate
      [:div {:class ["border" "border-base-300" "bg-base-200/20" "p-2"]
             :data-role (str "portfolio-optimizer-history-assumption-regression-" id)
             :data-status (some-> (:status estimate) name)}
       [:span {:class controls/eyebrow-class} "Regression estimate"]
       (when estimated?
         [:p {:class ["mt-1" "text-[0.6875rem]" "text-trading-muted"]}
          "Regression estimates exposure over the available overlap."])
       (when estimated?
         (basket-weight-rows id
                             "portfolio-optimizer-history-assumption-regression-weight-"
                             (:rows estimate)
                             false))
       (when estimated?
         [:p {:class ["mt-1.5" "font-mono" "text-[0.6875rem]" "text-trading-muted"]}
          (:summary estimate)])
       (when-not estimated?
         [:p {:class ["mt-1" "text-[0.6875rem]" "text-trading-muted"]}
          (:message estimate)])])))

(defn- final-basket-panel
  "Panel C: the confidence-shrunk final basket - the split covariance synthesis
  actually uses. Visually the loudest of the three."
  [card]
  (let [id (:instrument-id card)
        final (:final-basket card)]
    (when (seq (:rows final))
      [:div {:class ["border" "border-warning/50" "bg-warning/10" "p-2"]
             :data-role (str "portfolio-optimizer-history-assumption-final-basket-" id)}
       [:span {:class controls/eyebrow-class} "Final modeled basket"]
       [:p {:class ["mt-1" "text-[0.6875rem]" "text-trading-muted"]}
        "Final modeled basket after confidence-weighted shrinkage."]
       (basket-weight-rows id
                           "portfolio-optimizer-history-assumption-final-weight-"
                           (:rows final)
                           true)
       (when (:confidence-label final)
         [:p {:class ["mt-1.5" "font-mono" "text-[0.6875rem]" "text-trading-muted"]
              :data-role (str "portfolio-optimizer-history-assumption-confidence-" id)}
          (str "Confidence q " (:confidence-label final)
               " — controls how much the regression can move the prior")])])))

(defn- diagnostics-strip
  [card]
  (let [id (:instrument-id card)]
    (when (seq (:diagnostics card))
      [:div {:data-role (str "portfolio-optimizer-history-assumption-diagnostics-" id)}
       (into [:div {:class ["grid" "gap-1" "sm:grid-cols-3"]}]
             (map (fn [{:keys [label value detail]}]
                    [:div {:class ["border" "border-base-300" "bg-base-200/20" "p-2"]}
                     [:p {:class ["text-[0.625rem]" "uppercase" "tracking-[0.08em]"
                                  "text-trading-muted/70"]}
                      label]
                     [:p {:class ["mt-0.5" "font-mono" "text-[0.75rem]" "font-semibold"]}
                      value]
                     (when detail
                       [:p {:class ["mt-0.5" "text-[0.625rem]" "leading-[1.4]"
                                    "text-trading-muted"]}
                        detail])]))
             (:diagnostics card))
       (when (:final-model-line card)
         [:p {:class ["mt-1.5" "border" "border-base-300" "bg-base-200/40" "px-2" "py-1"
                      "font-mono" "text-[0.6875rem]" "text-trading-muted"]}
          (:final-model-line card)])])))

(defn- how-this-works
  []
  [:div {:class ["border" "border-primary/30" "bg-primary/5" "p-2"]}
   [:p {:class ["text-[0.625rem]" "font-semibold" "uppercase" "tracking-[0.08em]"
                "text-primary/80"]}
    "How this works"]
   (into [:div {:class ["mt-1" "space-y-0.5" "text-[0.6875rem]" "leading-[1.4]"
                        "text-trading-muted"]}]
         (map (fn [line] [:p line]))
         how-this-works-copy)])

(defn- proxy-fields
  [card]
  (let [id (:instrument-id card)]
    [:div {:class ["space-y-2"]}
     [:div
      [:span {:class controls/eyebrow-class} "Proxy assets"]
      [:p {:class ["mt-1" "text-[0.6875rem]" "text-trading-muted"]}
       "Search any asset this one behaves like — it doesn't have to be in your portfolio"]
      [:div {:class ["mt-1.5" "space-y-1.5"]}
       (proxy-chips card)
       (proxy-search card)]]
     (relationship-selector card)
     [:div {:class ["grid" "gap-2" "sm:grid-cols-2"]}
      (percent-input
       {:label "Expected volatility (annualized)"
        :field (:volatility card)
        :role (str "portfolio-optimizer-history-assumption-volatility-" id)
        :action [(get-in card [:actions :set-expected-volatility]) id
                 [:event.target/value]]})
      (percent-input
       {:label "Max weight cap"
        :field (:max-weight card)
        :role (str "portfolio-optimizer-history-assumption-max-weight-" id)
        :action [(get-in card [:actions :set-max-weight-cap]) id
                 [:event.target/value]]})]
     (when (:expected-return-required? card)
       (percent-input
        {:label "Expected annual return"
         :field (:expected-return card)
         :role (str "portfolio-optimizer-history-assumption-return-" id)
         :action [(get-in card [:actions :set-expected-return]) id
                  [:event.target/value]]}))
     (prior-basket-panel card)
     (regression-estimate-panel card)
     (final-basket-panel card)
     (how-this-works)
     (diagnostics-strip card)]))

(defn- conservative-fields
  [card]
  (let [id (:instrument-id card)]
    [:div {:class ["space-y-2"]}
     [:p {:class ["text-[0.6875rem]" "leading-[1.5]" "text-trading-muted"]}
      "A high volatility with no diversification credit, pre-filled and editable."]
     (percent-input
      {:label "Expected annual return"
       :field (:expected-return card)
       :role (str "portfolio-optimizer-history-assumption-return-" id)
       :action [(get-in card [:actions :set-expected-return]) id
                [:event.target/value]]})
     (percent-input
      {:label "Expected annual volatility"
       :field (:volatility card)
       :role (str "portfolio-optimizer-history-assumption-volatility-" id)
       :action [(get-in card [:actions :set-expected-volatility]) id
                [:event.target/value]]})
     (percent-input
      {:label "Max weight cap"
       :field (:max-weight card)
       :role (str "portfolio-optimizer-history-assumption-max-weight-" id)
       :action [(get-in card [:actions :set-max-weight-cap]) id
                [:event.target/value]]})]))

(defn- card-errors
  [card]
  (when (seq (:errors card))
    (into [:ul {:class ["space-y-1" "text-[0.75rem]" "text-warning"]
                :data-role (str "portfolio-optimizer-history-assumption-errors-"
                                (:instrument-id card))}]
          (map (fn [message] [:li message]) (:errors card)))))

(defn- card-actions
  "Reset re-seeds the mode defaults; Apply acknowledges the configuration (green
  Configured status). Run readiness never waits on Apply - a complete assumption
  is engine-backed as soon as its fields are valid."
  [card]
  (let [id (:instrument-id card)]
    [:div {:class ["flex" "items-center" "justify-end" "gap-2" "border-t"
                   "border-base-300" "pt-2"]}
     [:button {:type "button"
               :class ["border" "border-base-300" "px-3" "py-1.5" "text-[0.75rem]"
                       "font-semibold" "text-trading-muted" "hover:text-trading-text"]
               :data-role (str "portfolio-optimizer-history-assumption-reset-" id)
               :on {:click [[(get-in card [:actions :reset]) id]]}}
      "Reset"]
     [:button (cond-> {:type "button"
                       :class ["optimizer-primary-action" "border" "border-warning/70"
                               "bg-warning/80" "px-3" "py-1.5" "text-[0.75rem]"
                               "font-semibold" "text-base-100"
                               "disabled:cursor-not-allowed" "disabled:opacity-40"]
                       :data-role (str "portfolio-optimizer-history-assumption-apply-" id)
                       :on {:click [[(get-in card [:actions :apply]) id]]}}
                (not (:engine-applied? card))
                (assoc :disabled true))
      (if (:configured? card) "Applied" "Apply assumptions")]]))

(defn- assumption-card
  [card]
  (let [id (:instrument-id card)]
    [:section {:class ["border" "bg-base-100/70" "p-3" "space-y-2"
                       (if (= :configured (:status card))
                         "border-success/40"
                         "border-warning/40")]
               :data-role (:role card)
               :replicant/key (str "history-assumption-card-" id)}
     [:div {:class ["flex" "items-start" "justify-between" "gap-2"]}
      [:div
       [:p {:class controls/section-title-class}
        (if (= :configured (:status card))
          (:label card)
          (str (:label card) " needs assumptions"))]
       [:p {:class ["mt-1" "text-[0.75rem]" "leading-[1.45]" "text-trading-muted"]}
        card-subtext]]
      [:div {:class ["flex" "items-center" "gap-2"]}
       (status-chip card)
       (when (:mode card)
         [:button {:type "button"
                   :class ["text-[0.6875rem]" "uppercase" "tracking-[0.08em]"
                           "text-trading-muted" "hover:text-warning"]
                   :data-role (str "portfolio-optimizer-history-assumption-clear-" id)
                   :on {:click [[(get-in card [:actions :clear]) id]]}}
          "Clear"])]]
     (mode-tabs card)
     (case (:mode card)
       :proxy (proxy-fields card)
       :conservative (conservative-fields card)
       [:p {:class ["text-[0.75rem]" "leading-[1.5]" "text-trading-muted"]}
        "Choose an approach. Until configured, this asset stays excluded from the run."])
     (when (:note card)
       [:p {:class ["border" "border-warning/40" "bg-warning/10" "p-2"
                    "text-[0.75rem]" "text-warning"]
            :data-role (str "portfolio-optimizer-history-assumption-note-" id)}
        (:note card)])
     (card-errors card)
     (when (:summary card)
       [:p {:class ["text-[0.75rem]" "text-trading-text"]
            :data-role (str "portfolio-optimizer-history-assumption-summary-" id)}
        (str (:label card) ": " (:summary card))])
     (when (:mode card)
       (card-actions card))]))

(defn- add-asset-select
  "Manual entry point: bring ANY selected asset into the proxy workflow. The
  thresholds only auto-flag egregious cases; the user may judge an asset
  statistically unsound (a young listing, a stubby return stream) on their own
  and factor-load it regardless. Choosing an asset seeds proxy mode - its card
  appears immediately below."
  [addable-assets]
  (when (seq addable-assets)
    ;; Full-width own row (see history-assumptions-section) so the placeholder
    ;; "+ Model an asset with proxies…" is never clipped horizontally. py-0 +
    ;; leading-none: optimizer surfaces pin inputs/selects to a fixed 26px
    ;; border-box (optimizer/base.css) while @tailwindcss/forms adds 0.5rem
    ;; vertical padding to every <select> - left alone it clips the text, so
    ;; py-0 overrides it; pr-7 leaves room for the native chevron.
    (into [:select {:class ["w-full" "border" "border-base-300" "bg-base-100/80"
                            "py-0" "pl-2" "pr-7" "font-mono" "text-[0.75rem]" "leading-none"
                            "text-trading-muted"]
                    :value ""
                    :data-role "portfolio-optimizer-history-assumption-workflow-add"
                    :on {:change [[:actions/set-portfolio-optimizer-history-assumption-mode
                                   [:event.target/value] :proxy]]}}
           [:option {:value ""} "+ Model an asset with proxies…"]]
          (map (fn [{:keys [instrument-id label]}]
                 [:option {:value instrument-id} label]))
          addable-assets)))

(defn history-assumptions-section
  [{:keys [state draft readiness history-load-state]}]
  (let [{:keys [cards addable-assets applicable?]}
        (optimizer-view-model/history-assumption-cards
         state draft readiness history-load-state
         {:percent-label controls/percent-label})]
    (when (or applicable? (seq addable-assets))
      (into [:section {:class ["optimizer-setup-panel" "border" "border-base-300"
                               "bg-base-100/90" "p-3" "space-y-3"]
                       :data-role "portfolio-optimizer-history-assumptions-section"}
             [:div {:class ["flex" "items-start" "justify-between" "gap-3"]}
              [:div {:class ["min-w-0"]}
               [:p {:class controls/section-title-class}
                "Proxy Workflow for Short-History Assets"]
               [:p {:class ["mt-1" "text-[0.75rem]" "leading-[1.45]" "text-trading-muted"]}
                "Define assumptions for assets whose return history you don't trust."]]
              (when (seq cards)
                [:span {:class ["shrink-0" "whitespace-nowrap" "font-mono" "text-[0.6875rem]"
                                "text-trading-muted/70"]
                        :data-role "portfolio-optimizer-history-assumptions-count"}
                 (str (count cards)
                      (if (= 1 (count cards))
                        " asset in the workflow"
                        " assets in the workflow"))])]
             ;; The picker gets its OWN full-width row so the placeholder text is
             ;; never clipped by the header's title/description competing for width.
             (add-asset-select addable-assets)]
            (concat
             (map assumption-card cards)
             (when (empty? cards)
               [[:p {:class ["text-[0.75rem]" "leading-[1.5]" "text-trading-muted"]
                     :data-role "portfolio-optimizer-history-assumptions-empty"}
                 "Nothing needs assumptions right now. Pick an asset above to model it as a basket of assets it behaves like - its returns are then driven by the basket plus specific risk instead of its own short history."]]))))))
