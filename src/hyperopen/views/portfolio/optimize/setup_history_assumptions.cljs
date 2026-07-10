(ns hyperopen.views.portfolio.optimize.setup-history-assumptions
  "Proxy workflow cards for assets that lack optimizer-safe history. Views render
  the view-model cards and dispatch the carried action ids only - no history
  math, no raw payload branching."
  (:require [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.views.portfolio.optimize.setup-controls :as controls]
            [hyperopen.views.portfolio.optimize.setup-history-assumption-panels :as panels]
            [hyperopen.views.portfolio.optimize.setup-history-assumption-recommendations :as recommendations]
            [hyperopen.views.portfolio.optimize.setup-history-assumptions-io :as assumptions-io]))

(def ^:private card-subtext
  "Insufficient native history. Model behavior using proxies.")

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
  "Status pill. While this card's proxy history is still fetching, ANY status
  verdict is provisional (completeness judged against a not-yet-loaded usable
  set can even read Configured), so the chip reads a pulsing \"Loading
  history…\" until the fetch settles."
  [card]
  (let [id (:instrument-id card)
        configured? (= :configured (:status card))
        loading? (boolean (:history-loading? card))]
    [:span (cond-> {:class ["border" "px-1.5" "py-0.5" "font-mono" "text-[0.625rem]"
                            "font-semibold" "uppercase" "tracking-[0.1em]"
                            (cond loading? "border-base-300"
                                  configured? "border-success/50"
                                  :else "border-warning/50")
                            (cond loading? "text-trading-muted"
                                  configured? "text-success"
                                  :else "text-warning")
                            (cond loading? "bg-base-200/40"
                                  configured? "bg-success/10"
                                  :else "bg-warning/10")
                            (when loading? "animate-pulse")]
                    :data-role (str "portfolio-optimizer-history-assumption-status-" id)
                    :data-status (some-> (:status card) name)}
             loading? (assoc :data-loading "true"))
     (if loading? "Loading history…" (:status-label card))]))

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
                    ;; This proxy's history fetch is still in flight.
                    (when loading?
                      [:span {:class ["animate-pulse" "text-[0.5625rem]" "uppercase"
                                      "tracking-[0.06em]" "text-trading-muted"]}
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

(defn- risk-guardrails-drawer
  "Volatility + cap, auto-set and collapsed: the proxy card's visible asks stay
  behavioral (what does this asset behave like) while the model's required risk
  inputs sit one click away. Collapsed, the summary row reads the current
  values; `optimizer-section-trailing` hides that line while the drawer is open
  (setup.css) because the inputs then show the same numbers. Never :open from
  state — a computed open re-asserts itself against the user's own toggle."
  [card]
  (let [id (:instrument-id card)
        guardrails (:risk-guardrails card)
        attention? (:attention? guardrails)]
    [:details {:class ["border" "bg-base-200/20"
                       (if attention? "border-warning/60" "border-base-300")]
               :data-role (str "portfolio-optimizer-history-assumption-guardrails-" id)
               :replicant/key (str "history-assumption-guardrails-" id)}
     [:summary {:class ["cursor-pointer" "select-none" "p-2"
                        "focus:outline-none" "focus:text-warning"]}
      [:span {:class ["inline-flex" "w-[calc(100%-1.25rem)]" "items-center"
                      "justify-between" "gap-2" "align-middle"]}
       [:span {:class controls/eyebrow-class} "Risk guardrails"]
       [:span {:class ["optimizer-section-trailing" "inline-flex" "items-center" "gap-2"]}
        [:span {:class ["font-mono" "text-[0.75rem]"
                        (if attention? "text-warning" "text-trading-text")]
                :data-role (str "portfolio-optimizer-history-assumption-guardrails-summary-" id)}
         (:summary guardrails)]
        [:span {:class ["border" "border-base-300" "bg-base-200/40" "px-1.5" "py-0.5"
                        "font-mono" "text-[0.625rem]" "font-semibold" "uppercase"
                        "tracking-[0.08em]" "text-trading-muted"]
                :data-role (str "portfolio-optimizer-history-assumption-guardrails-source-" id)}
         (:source-label guardrails)]
        [:span {:class ["text-[0.6875rem]" "uppercase" "tracking-[0.08em]"
                        "text-trading-muted" "hover:text-warning"]}
         "Edit"]]]]
     [:div {:class ["space-y-2" "border-t" "border-base-300" "p-2"]}
      [:p {:class ["text-[0.6875rem]" "leading-[1.5]" "text-trading-muted"]}
       "Auto-set so you don't have to estimate them. Volatility sets this asset's total modeled risk — the basket only sets how it co-moves. The cap limits how much the optimizer can allocate to a proxy-based estimate."]
      [:div {:class ["grid" "gap-2" "sm:grid-cols-2"]}
       (percent-input
        {:label "Modeled annual volatility"
         :field (:volatility card)
         :role (str "portfolio-optimizer-history-assumption-volatility-" id)
         :action [(get-in card [:actions :set-expected-volatility]) id
                  [:event.target/value]]})
       (percent-input
        {:label "Max allocation cap"
         :field (:max-weight card)
         :role (str "portfolio-optimizer-history-assumption-max-weight-" id)
         :action [(get-in card [:actions :set-max-weight-cap]) id
                  [:event.target/value]]})]]]))

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
     (risk-guardrails-drawer card)
     (when (:expected-return-required? card)
       (percent-input
        {:label "Expected annual return"
         :field (:expected-return card)
         :role (str "portfolio-optimizer-history-assumption-return-" id)
         :action [(get-in card [:actions :set-expected-return]) id
                  [:event.target/value]]}))
     (panels/prior-basket-panel card)
     (panels/regression-estimate-panel card)
     (panels/final-basket-panel card)
     (panels/how-this-works)
     (panels/diagnostics-strip card)]))

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
      {:label "Modeled annual volatility"
       :field (:volatility card)
       :role (str "portfolio-optimizer-history-assumption-volatility-" id)
       :action [(get-in card [:actions :set-expected-volatility]) id
                [:event.target/value]]})
     (percent-input
      {:label "Max allocation cap"
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
  (let [id (:instrument-id card)
        loading? (boolean (:history-loading? card))]
    [:div {:class ["flex" "items-center" "justify-end" "gap-2" "border-t"
                   "border-base-300" "pt-2"]}
     ;; Hold Apply while history is fetching: completeness judged against a
     ;; not-yet-loaded proxy set flips on its own moments later, and the copy
     ;; makes the disabled button read as "wait", not "broken".
     (when loading?
       [:p {:class ["mr-auto" "animate-pulse" "text-[0.6875rem]" "text-trading-muted"]
            :data-role (str "portfolio-optimizer-history-assumption-apply-loading-" id)}
        "Waiting for proxy history to load…"])
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
                (or (not (:engine-applied? card)) loading?)
                (assoc :disabled true))
      (if (:configured? card) "Applied" "Apply assumptions")]]))

(defn- collapsed-assumption-card
  "One-line summary row for a collapsed card: label, status, the configured
  summary (when complete), and an Edit control that expands it. Keeps a stack
  of configured assets to a glance instead of a long scroll."
  [card]
  (let [id (:instrument-id card)]
    [:section {:class ["border" "bg-base-100/70" "p-3"
                       (if (= :configured (:status card))
                         "border-success/40"
                         "border-warning/40")]
               :data-role (:role card)
               :data-collapsed "true"
               :replicant/key (str "history-assumption-card-" id)}
     [:div {:class ["flex" "items-center" "justify-between" "gap-2"]}
      [:div {:class ["min-w-0" "flex" "items-baseline" "gap-2"]}
       [:p {:class controls/section-title-class} (:label card)]
       [:p {:class ["truncate" "text-[0.75rem]" "text-trading-muted"]
            :data-role (str "portfolio-optimizer-history-assumption-summary-" id)}
        (or (:summary card) "Configuration unfinished")]]
      [:div {:class ["flex" "shrink-0" "items-center" "gap-2"]}
       (status-chip card)
       [:button {:type "button"
                 :class ["text-[0.6875rem]" "uppercase" "tracking-[0.08em]"
                         "text-trading-muted" "hover:text-warning"]
                 :data-role (str "portfolio-optimizer-history-assumption-expand-" id)
                 :on {:click [[(get-in card [:actions :set-card-collapsed]) id false]]}}
        "Edit"]]]]))

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
       (when (:collapsible? card)
         [:button {:type "button"
                   :class ["text-[0.6875rem]" "uppercase" "tracking-[0.08em]"
                           "text-trading-muted" "hover:text-warning"]
                   :data-role (str "portfolio-optimizer-history-assumption-collapse-" id)
                   :on {:click [[(get-in card [:actions :set-card-collapsed]) id true]]}}
          "Collapse"])
       (when (:mode card)
         [:button {:type "button"
                   :class ["text-[0.6875rem]" "uppercase" "tracking-[0.08em]"
                           "text-trading-muted" "hover:text-warning"]
                   :data-role (str "portfolio-optimizer-history-assumption-clear-" id)
                   :on {:click [[(get-in card [:actions :clear]) id]]}}
          "Clear"])]]
     ;; The backend's recommendation leads while no mode is chosen: one click
     ;; beats hand-picking, and choosing a mode by hand dismisses it.
     (when (and (nil? (:mode card)) (:recommendation card))
       (recommendations/recommendation-panel card))
     (mode-tabs card)
     ;; The agent's stated reason for an imported configuration — the trust
     ;; artifact that lets the user audit a file-authored basket at a glance.
     (when (:rationale card)
       [:p {:class ["border" "border-base-300" "bg-base-200/20" "p-2"
                    "text-[0.75rem]" "leading-[1.45]" "text-trading-muted"]
            :data-role (str "portfolio-optimizer-history-assumption-rationale-" id)}
        (str "Agent rationale: " (:rationale card))])
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

(defn- assumption-card-or-summary
  [card]
  (if (:collapsed? card)
    (collapsed-assumption-card card)
    (assumption-card card)))

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
          ;; Options arrive sorted ascending by native return-day count with the
          ;; count in the label, so the assets limiting the shared covariance
          ;; window sit at the top of the list.
          (map (fn [{:keys [instrument-id label option-label]}]
                 [:option {:value instrument-id} (or option-label label)]))
          addable-assets)))

(defn- loading-banner
  "Aggregate \"background work in progress\" strip: while any card's proxy
  history is still fetching, tell the user the cards below will fill in on
  their own so they don't start re-editing provisional state."
  [loading-count]
  (when (pos? loading-count)
    [:div {:class ["flex" "items-center" "gap-2" "border" "border-base-300"
                   "bg-base-200/40" "px-2" "py-1.5"]
           :data-role "portfolio-optimizer-history-assumptions-loading-banner"}
     [:span {:class ["h-1.5" "w-1.5" "shrink-0" "animate-pulse" "rounded-full"
                     "bg-warning"]
             :aria-hidden "true"}]
     [:span {:class ["font-mono" "text-[0.6875rem]" "text-trading-muted"]}
      (str "Loading proxy history for " loading-count
           (if (= 1 loading-count) " asset" " assets")
           " — cards update automatically when it finishes.")]]))

(defn history-assumptions-section
  [{:keys [state draft readiness history-load-state]}]
  (let [{:keys [cards addable-assets applicable? history-loading-count]
         :as cards-model}
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
             (loading-banner (or history-loading-count 0))
             (recommendations/recommended-banner cards-model)
             (assumptions-io/io-toolbar
              {:asset-count (count cards)
               :universe-count (+ (count cards) (count addable-assets))})
             (assumptions-io/io-note
              (get-in state contracts/ui-history-assumptions-io-note-path))
             ;; The picker gets its OWN full-width row so the placeholder text is
             ;; never clipped by the header's title/description competing for width.
             (add-asset-select addable-assets)]
            (concat
             (map assumption-card-or-summary cards)
             (when (empty? cards)
               [[:p {:class ["text-[0.75rem]" "leading-[1.5]" "text-trading-muted"]
                     :data-role "portfolio-optimizer-history-assumptions-empty"}
                 "Nothing needs assumptions right now. Pick an asset above to model it as a basket of assets it behaves like - its returns are then driven by the basket plus specific risk instead of its own short history."]]))))))
