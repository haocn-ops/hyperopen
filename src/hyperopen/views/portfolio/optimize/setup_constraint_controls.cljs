(ns hyperopen.views.portfolio.optimize.setup-constraint-controls
  (:require [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.portfolio.optimizer.application.view-model.exposure :as exposure-vm]
            [hyperopen.portfolio.optimizer.domain.exposure-policy :as exposure-policy]
            [hyperopen.views.portfolio.optimize.setup-controls :as controls]
            [hyperopen.views.portfolio.optimize.setup-exposure-map :as exposure-map]
            [hyperopen.views.ui.toggle :as toggle]))

(def ^:private default-turnover-cap
  1.0)

(def ^:private constraint-help
  {:long-only? "Restricts target weights to zero or positive values. Turn this off when short or hedged perp exposure is allowed."
   :include-spot? "Allows spot instruments in optimizer recommendations. Leave this off to restrict target legs to perps and vaults."
   :max-asset-weight "Maximum target portfolio weight any single asset can receive. 0.5 means no asset can exceed 50%."
   :gross-min "Minimum total absolute exposure to hold. Seeded from your current gross leverage so the optimizer preserves leverage instead of delevering. Leave blank to allow full delevering."
   :gross-max "Maximum total absolute exposure across all legs. 1 means long exposure plus short exposure can total up to 100% of capital."
   :net-min "Minimum signed net exposure allowed after optimization. Leave blank when only the maximum net exposure matters."
   :net-max "Maximum signed net exposure allowed after optimization. 1 means the portfolio can be net long up to 100% of capital."
   :dust-usdc "Small rebalance trades below this USDC notional are ignored so the output avoids noisy dust orders."
   :max-turnover "Maximum total portfolio turnover allowed for the rebalance. Turn this off when current exposure is too far from the target constraints."
   :rebalance-tolerance "Minimum target-vs-current weight difference before a rebalance row is considered actionable. 0.03 means 3 percentage points."})

(defn- constraint-tooltip
  [tooltip-id copy]
  [:span {:class ["pointer-events-none" "absolute" "left-0" "top-[calc(100%+6px)]"
                  "z-30" "w-[min(22rem,calc(100vw-2rem))]" "border"
                  "border-base-300" "bg-base-100" "px-2" "py-1.5"
                  "font-sans" "text-[0.75rem]" "font-normal"
                  "normal-case" "leading-[1.45]" "tracking-normal"
                  "text-trading-muted" "opacity-0" "shadow-[0_12px_32px_rgba(0,0,0,0.45)]"
                  "transition-opacity" "duration-150" "group-hover:opacity-100"
                  "group-focus-within:opacity-100"]
          :id tooltip-id
          :role "tooltip"
          :data-role tooltip-id}
   copy])

(defn- constraint-label
  [label tooltip-id help-copy]
  [:span {:class ["relative" "inline-flex" "min-w-0" "items-center" "gap-1.5"]}
   [:span {:class controls/eyebrow-class} label]
   [:span {:class ["font-mono" "text-[0.625rem]" "text-trading-muted/70"]
           :aria-hidden "true"}
    "?"]
   (constraint-tooltip tooltip-id help-copy)])

(defn- constraint-echo
  "Persistent interpreted-value echo so the unit isn't hidden in the `?` hover tooltip
  (the audit found constraint units lived only there). 0.5 -> \"max 50% per asset\"."
  [unit value]
  (when (and unit (number? value))
    (case unit
      :weight (str "max " (js/Math.round (* 100 value)) "% per asset")
      :mult (str (.toFixed value 2) "× capital")
      :pts (str (.toFixed (* 100 value) 1) " pp band")
      :usd (str "$" value " min trade")
      nil)))

(defn- constraint-row
  ([label constraint-key value role highlighted? unit]
   (constraint-row label nil constraint-key value role highlighted? unit))
  ([label hidden-label constraint-key value role highlighted? unit]
   (let [tooltip-id (str role "-tooltip")
         help-copy (get constraint-help constraint-key)
         echo (constraint-echo unit value)]
     [:label {:class (cond-> ["group" "relative" "grid" "grid-cols-[minmax(0,1fr)_92px]" "items-center"
                              "gap-2" "border" "border-base-300" "bg-base-200/20"
                              "px-2" "py-1.5"]
                       highlighted? (conj "border-warning/70" "bg-warning/10"))}
      [:span {:class ["min-w-0"]}
       (if help-copy
         (constraint-label label tooltip-id help-copy)
         [:span {:class controls/eyebrow-class} label])
       (when hidden-label
         [:span {:class ["sr-only"]} hidden-label])
       [:span {:class ["ml-2" "font-mono" "text-[0.625rem]" "uppercase"
                       "tracking-[0.08em]" "text-trading-muted"]}
        "edit"]
       (when echo
         [:span {:class ["mt-0.5" "block" "font-mono" "text-[0.625rem]"
                         "normal-case" "tracking-normal" "text-trading-muted/70"]
                 :data-role (str role "-echo")}
          echo])]
      [:input {:type "text"
               :inputmode "decimal"
               :class controls/input-class
               :data-role role
               :data-infeasible (when highlighted? "true")
               :aria-invalid (when highlighted? "true")
               :aria-describedby (when help-copy tooltip-id)
               :value (str value)
               ;; Commit on blur/Enter, not per keystroke: the handler parses the value with
               ;; js/Number and writes it back into this controlled :value, so on :input a typed
               ;; "0." snaps to "0" and a decimal can never be entered. :change keeps the raw
               ;; text in the DOM until blur, then reconciles to the interpreted value.
               :on {:change [[:actions/set-portfolio-optimizer-constraint
                              constraint-key
                              [:event.target/value]]]}}]])))

(defn- turnover-cap-row
  [constraints highlighted?]
  (let [enabled? (some? (:max-turnover constraints))
        tooltip-id "portfolio-optimizer-constraint-max-turnover-input-tooltip"
        help-copy (:max-turnover constraint-help)]
    [:div {:class (cond-> ["group" "relative" "grid"
                           "grid-cols-[minmax(0,1fr)_auto]" "items-center"
                           "gap-2" "border" "border-base-300" "bg-base-200/20"
                           "px-2" "py-1.5"]
                    highlighted? (conj "border-warning/70" "bg-warning/10")
                    (not enabled?) (conj "text-trading-muted"))}
     [:span {:class ["min-w-0"]}
      [:span {:class ["min-w-0"]}
       (constraint-label "Turnover cap" tooltip-id help-copy)
       [:span {:class ["ml-2" "font-mono" "text-[0.625rem]" "uppercase"
                       "tracking-[0.08em]" "text-trading-muted"]}
        (if enabled? "edit" "no cap")]]]
     [:span {:class ["optimizer-turnover-cap-control" "inline-flex" "items-center" "gap-1.5"]}
      (toggle/toggle {:on? enabled?
                      :aria-label "Toggle turnover cap"
                      :data-role "portfolio-optimizer-constraint-max-turnover-toggle"
                      :on-change [[:actions/set-portfolio-optimizer-constraint
                                   :max-turnover
                                   (if enabled? nil default-turnover-cap)]]})
     [:input (cond-> {:type "text"
                      :inputmode "decimal"
                      :class (cond-> (conj controls/input-class "w-[92px]")
                               (not enabled?) (conj "opacity-50" "cursor-not-allowed"))
                      :data-role "portfolio-optimizer-constraint-max-turnover-input"
                      :data-infeasible (when highlighted? "true")
                      :aria-invalid (when highlighted? "true")
                      :aria-describedby tooltip-id
                      :value (if enabled? (str (:max-turnover constraints)) "")
                      :disabled (not enabled?)}
               ;; Commit on blur/Enter (see constraint-row) so a typed decimal isn't rewritten.
               enabled? (assoc :on {:change [[:actions/set-portfolio-optimizer-constraint
                                              :max-turnover
                                              [:event.target/value]]]}))]]]))

(defn- long-only-row
  [constraints]
  (let [enabled? (true? (:long-only? constraints))
        tooltip-id "portfolio-optimizer-constraint-long-only-tooltip"]
    [:div {:class ["group" "relative" "grid" "grid-cols-[minmax(0,1fr)_auto]"
                   "items-center" "gap-2" "border" "border-base-300"
                   "bg-base-200/20" "px-2" "py-1.5"]}
     [:span {:class ["min-w-0"]}
      (constraint-label "Long Only"
                        tooltip-id
                        (:long-only? constraint-help))]
     [:span {:class ["optimizer-long-only-control" "inline-flex" "items-center"]}
      (toggle/toggle {:on? enabled?
                      :aria-label "Toggle long only"
                      :aria-describedby tooltip-id
                      :data-role "portfolio-optimizer-constraint-long-only-input"
                      :on-change [[:actions/set-portfolio-optimizer-constraint
                                   :long-only?
                                   (not enabled?)]]})]]))

(defn- include-spot-row
  [constraints]
  (let [enabled? (true? (:include-spot? constraints))
        tooltip-id "portfolio-optimizer-constraint-include-spot-tooltip"]
    [:div {:class ["group" "relative" "grid" "grid-cols-[minmax(0,1fr)_auto]"
                   "items-center" "gap-2" "border" "border-base-300"
                   "bg-base-200/20" "px-2" "py-1.5"]}
     [:span {:class ["min-w-0"]}
      (constraint-label "Include Spot Assets"
                        tooltip-id
                        (:include-spot? constraint-help))]
     [:span {:class ["optimizer-include-spot-control" "inline-flex" "items-center"]}
      (toggle/toggle {:on? enabled?
                      :aria-label "Toggle include spot assets"
                      :aria-describedby tooltip-id
                      :data-role "portfolio-optimizer-constraint-include-spot-input"
                      :on-change [[:actions/set-portfolio-optimizer-constraint
                                   :include-spot?
                                   (not enabled?)]]})]]))

(defn- group-block
  "A labelled sub-group inside the Portfolio exposure panel. `eyebrow` is the small uppercase label,
  `hint` an optional plain-English caption, `body` the controls."
  [eyebrow hint body]
  [:div {:class ["optimizer-constraint-group"]}
   [:div {:class ["flex" "items-baseline" "justify-between" "gap-2"]}
    [:span {:class controls/eyebrow-class} eyebrow]
    (when hint
      [:span {:class ["font-mono" "text-[0.625rem]" "normal-case"
                      "tracking-normal" "text-trading-muted/70"]}
       hint])]
   [:div {:class ["mt-2"]} body]])

(defn- advanced-drawer
  "The raw gross/net min/max fields the exposure pad abstracts, kept for experts. Editing any raw
  field writes the canonical key directly, which flips the active preset to Custom (the
  exposure-map derives the preset from the values). Lives behind a nested disclosure so the
  common path never sees it. Each field keeps its original data-role and appears exactly once in
  the panel — these are the only place gross-min/gross-max/net-min/net-max are editable."
  [constraints highlighted-controls]
  (controls/disclosure-panel
   "portfolio-optimizer-constraints-advanced"
   (controls/disclosure-heading "Advanced solver limits" "raw min/max")
   [:div {:class ["mt-3" "grid" "grid-cols-1" "gap-2"]}
    (constraint-row "Gross exposure min" :gross-min (:gross-min constraints)
                    "portfolio-optimizer-constraint-gross-min-input"
                    (contains? highlighted-controls :gross-min)
                    :mult)
    (constraint-row "Gross exposure max" "Gross Leverage"
                    :gross-max (:gross-max constraints)
                    "portfolio-optimizer-constraint-gross-max-input"
                    (contains? highlighted-controls :gross-max)
                    :mult)
    (constraint-row "Net exposure min" :net-min (:net-min constraints)
                    "portfolio-optimizer-constraint-net-min-input"
                    (contains? highlighted-controls :net-min)
                    :mult)
    (constraint-row "Net exposure max" :net-max (:net-max constraints)
                    "portfolio-optimizer-constraint-net-max-input"
                    (contains? highlighted-controls :net-max)
                    :mult)]))

(defn constraints-section
  ([draft highlighted-controls]
   (constraints-section draft highlighted-controls nil))
  ([draft highlighted-controls {:keys [current-exposure has-saved-default? exposure-zoom-level]}]
   (let [constraints (:constraints draft)
         exposure-model (exposure-vm/exposure-map-model
                         {:constraints constraints
                          :current-exposure current-exposure
                          :highlighted-controls highlighted-controls
                          :has-saved-default? has-saved-default?
                          :zoom-level exposure-zoom-level})
         base-active-label (get exposure-policy/preset-labels
                                (:active-preset exposure-model) "Custom")
         ;; A holdings import derives the envelope from the current book; the
         ;; collapsed header says so instead of a bare "Custom".
         active-label (if (and (= "Custom" base-active-label)
                               (= :holdings
                                  (get-in draft [:metadata :universe-source :kind])))
                        "Custom · from holdings"
                        base-active-label)]
     (controls/disclosure-panel-open
      "portfolio-optimizer-constraints-panel"
      ;; The collapsed header carries the actual policy, not just a preset name:
      ;; exposure limits are a main determinant of the result, so the live numbers
      ;; ("Gross 1.90–1.91× · Net +1.30×–1.41× long · Max asset 50%") stay
      ;; scannable while the panel is closed.
      (controls/disclosure-heading
       "Portfolio exposure"
       [:span {:class ["flex" "min-w-0" "flex-col" "items-end" "gap-0.5" "text-right"]}
        [:span active-label]
        [:span {:class ["normal-case" "tracking-normal" "text-trading-muted"]
                :data-role "portfolio-optimizer-constraints-header-summary"}
         (optimizer-view-model/constraints-summary-line constraints)]])
      [:div {:class ["mt-3" "space-y-4"]}
       [:p {:class ["text-[0.8125rem]" "leading-[1.45]" "text-trading-muted"]
            :data-role "portfolio-optimizer-constraints-description"}
        "Set how levered and net long/short the target portfolio can be."]
       (group-block "Positioning" "gross leverage + net bias"
                    (exposure-map/exposure-map exposure-model))
       ;; Risk guards / Rebalance behavior keep each canonical control exactly once (original
       ;; data-roles), just grouped by what the trader is deciding rather than listed flat.
       ;; The two groups sit side by side on wide screens so the open panel stays compact.
       [:div {:class ["optimizer-constraint-group-row"
                      "grid" "grid-cols-1" "gap-4" "md:grid-cols-2"]}
        (group-block "Risk guards" nil
                     [:div {:class ["grid" "grid-cols-1" "gap-2"]}
                      (constraint-row "Per-asset cap" "Max Asset Weight"
                                      :max-asset-weight (:max-asset-weight constraints)
                                      "portfolio-optimizer-constraint-max-asset-weight-input"
                                      (contains? highlighted-controls :max-asset-weight)
                                      :weight)
                      (long-only-row constraints)
                      (include-spot-row constraints)])
        (group-block "Rebalance behavior" "fewer trades ↔ tighter tracking"
                     [:div {:class ["grid" "grid-cols-1" "gap-2"]}
                      (constraint-row "Rebalance tolerance" "Rebalance Tolerance"
                                      :rebalance-tolerance (:rebalance-tolerance constraints)
                                      "portfolio-optimizer-constraint-rebalance-tolerance-input" false
                                      :pts)
                      (turnover-cap-row constraints
                                        (contains? highlighted-controls :max-turnover))
                      (constraint-row "Dust threshold" :dust-usdc (:dust-usdc constraints)
                                      "portfolio-optimizer-constraint-dust-usdc-input" false
                                      :usd)])]
       (advanced-drawer constraints highlighted-controls)]))))
