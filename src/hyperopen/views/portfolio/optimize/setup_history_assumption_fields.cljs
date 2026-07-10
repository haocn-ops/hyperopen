(ns hyperopen.views.portfolio.optimize.setup-history-assumption-fields
  "Editable field controls for a history-assumption card (mode tabs, proxy
  picker, relationship strength, risk-guardrails drawer, percent inputs).
  Split from setup-history-assumptions when the section view crossed the
  namespace-size gate (2026-07-10) — pure card → hiccup projections plus the
  card's carried action ids, no history math."
  (:require [hyperopen.views.portfolio.optimize.setup-controls :as controls]
            [hyperopen.views.portfolio.optimize.setup-history-assumption-panels :as panels]))

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

(defn mode-tabs
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

(defn proxy-fields
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

(defn conservative-fields
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
