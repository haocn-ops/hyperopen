(ns hyperopen.views.portfolio.optimize.setup-constraint-controls
  (:require [hyperopen.views.portfolio.optimize.setup-controls :as controls]
            [hyperopen.views.ui.toggle :as toggle]))

(def ^:private default-turnover-cap
  1.0)

(def ^:private constraint-help
  {:long-only? "Restricts target weights to zero or positive values. Turn this off when short or hedged perp exposure is allowed."
   :include-spot? "Allows spot instruments in optimizer recommendations. Leave this off to restrict target legs to perps and vaults."
   :max-asset-weight "Maximum target portfolio weight any single asset can receive. 0.5 means no asset can exceed 50%."
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
                  "font-sans" "text-[0.65625rem]" "font-normal"
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
   [:span {:class ["font-mono" "text-[0.5625rem]" "text-trading-muted/70"]
           :aria-hidden "true"}
    "?"]
   (constraint-tooltip tooltip-id help-copy)])

(defn- constraint-row
  ([label constraint-key value role highlighted?]
   (constraint-row label nil constraint-key value role highlighted?))
  ([label hidden-label constraint-key value role highlighted?]
   (let [tooltip-id (str role "-tooltip")
         help-copy (get constraint-help constraint-key)]
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
       [:span {:class ["ml-2" "font-mono" "text-[0.59375rem]" "uppercase"
                       "tracking-[0.08em]" "text-trading-muted"]}
        "edit"]]
      [:input {:type "text"
               :inputmode "decimal"
               :class controls/input-class
               :data-role role
               :data-infeasible (when highlighted? "true")
               :aria-invalid (when highlighted? "true")
               :aria-describedby (when help-copy tooltip-id)
               :value (str value)
               :on {:input [[:actions/set-portfolio-optimizer-constraint
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
       [:span {:class ["ml-2" "font-mono" "text-[0.59375rem]" "uppercase"
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
               enabled? (assoc :on {:input [[:actions/set-portfolio-optimizer-constraint
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

(defn constraints-section
  [draft highlighted-controls]
  (let [constraints (:constraints draft)]
    (controls/disclosure-panel
     "portfolio-optimizer-constraints-panel"
     (controls/disclosure-heading "04" "Constraints" "mandatory")
     [:div {:class ["mt-3" "grid" "grid-cols-1" "gap-2"]}
      (long-only-row constraints)
      (include-spot-row constraints)
      (constraint-row "Per-asset cap" "Max Asset Weight"
                      :max-asset-weight (:max-asset-weight constraints)
                      "portfolio-optimizer-constraint-max-asset-weight-input"
                      (contains? highlighted-controls :max-asset-weight))
      (constraint-row "Gross exposure" "Gross Leverage"
                      :gross-max (:gross-max constraints)
                      "portfolio-optimizer-constraint-gross-max-input"
                      (contains? highlighted-controls :gross-max))
      (constraint-row "Net exposure min" :net-min (:net-min constraints)
                      "portfolio-optimizer-constraint-net-min-input"
                      (contains? highlighted-controls :net-min))
      (constraint-row "Net exposure max" :net-max (:net-max constraints)
                      "portfolio-optimizer-constraint-net-max-input"
                      (contains? highlighted-controls :net-max))
      (constraint-row "Dust threshold" :dust-usdc (:dust-usdc constraints)
                      "portfolio-optimizer-constraint-dust-usdc-input" false)
      (turnover-cap-row constraints
                        (contains? highlighted-controls :max-turnover))
      (constraint-row "Rebalance tolerance" "Rebalance Tolerance"
                      :rebalance-tolerance (:rebalance-tolerance constraints)
                      "portfolio-optimizer-constraint-rebalance-tolerance-input" false)])))
