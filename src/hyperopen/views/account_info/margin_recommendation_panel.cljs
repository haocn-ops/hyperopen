(ns hyperopen.views.account-info.margin-recommendation-panel
  "Anchored popover showing the modeled isolated-margin recommendation for one
  position: how the number was built (named buffers), the modeled liquidation
  probability before the next likely intervention, and the actions (prefilled
  margin add, reduce instead, risk-limit selection).

  This is a page-local, recoverable control, so per docs/FRONTEND.md it is an
  anchored popover (like the sibling Reduce/Margin popovers in this table) —
  not a full-screen modal. It floats from the risk chip that opens it, sized
  and clamped to the viewport by hyperopen.views.ui.anchored-popover; the
  content stacks vertically so the whole recommendation reads in one column
  without the cramped strip the inline version forced."
  (:require [hyperopen.margin-rec.state :as margin-rec-state]
            [hyperopen.views.account-info.shared :as shared]
            [hyperopen.views.ui.anchored-popover :as anchored-popover]
            [hyperopen.views.ui.dialog-focus :as dialog-focus]))

(defn- fmt-usd
  [value]
  (if (number? value)
    (str "$" (shared/format-currency value))
    "--"))

(defn- fmt-price
  [value]
  (if (number? value)
    (str "$" (js/parseFloat (.toPrecision value 6)))
    "--"))

(defn- fmt-probability
  [p]
  (cond
    (not (number? p)) "--"
    (< p 0.0005) "<0.1%"
    :else (str (.toFixed (* 100 p) 1) "%")))

(defn- fmt-percent
  [frac digits]
  (if (number? frac)
    (str (.toFixed (* 100 frac) digits) "%")
    "--"))

(defn horizon-copy
  [{:keys [hours source samples]}]
  (let [duration (cond
                   (not (number? hours)) "--"
                   (< hours 24) (str (js/Math.round hours) " hours")
                   (< hours 42) "1 day"
                   :else (str (js/Math.round (/ hours 24)) " days"))
        basis (case source
                :per-coin (str "based on " samples " prior position episodes")
                :account (str "based on " samples " episodes across your account")
                :override "manual override"
                "default horizon — limited trade history")]
    {:duration duration :basis basis}))

(defn- tile
  [role label value sub]
  [:div {:class ["min-w-0" "rounded-md" "bg-base-300/40" "px-2.5" "py-1.5"]
         :data-role role}
   [:div {:class ["text-xs" "uppercase" "tracking-wide" "text-trading-text-secondary"]}
    label]
   [:div {:class ["text-sm" "font-semibold" "num" "text-trading-text"]}
    value]
   (when sub
     [:div {:class ["text-xs" "text-trading-text-secondary"]}
      sub])])

(defn- breakdown-table
  [breakdown total]
  [:div {:class ["rounded-md" "bg-base-300/40" "p-2.5"]
         :data-role "margin-rec-breakdown"}
   [:div {:class ["mb-2" "text-xs" "font-semibold" "text-trading-text"]}
    "Recommended margin breakdown"]
   (into [:div {:class ["space-y-1"]}]
         (map (fn [{:keys [key label amount]}]
                [:div {:class ["flex" "items-center" "justify-between" "gap-3" "text-xs"]
                       :data-role (str "margin-rec-breakdown-" (name key))}
                 [:span {:class ["text-trading-text-secondary"]} label]
                 [:span {:class ["num" "text-trading-text"]} (fmt-usd amount)]])
              breakdown))
   [:div {:class ["mt-2" "flex" "items-center" "justify-between" "gap-3" "border-t"
                  "border-base-300" "pt-2" "text-xs" "font-semibold"]}
    [:span {:class ["text-trading-text"]} "Total recommended margin"]
    [:span {:class ["num" "text-trading-green"]} (fmt-usd total)]]])

(defn- risk-compare
  [p-now p-after]
  [:div {:class ["rounded-md" "bg-base-300/40" "p-2.5"]
         :data-role "margin-rec-risk-compare"}
   [:div {:class ["mb-2" "text-xs" "font-semibold" "text-trading-text"]}
    "Modeled probability of liquidation (before next intervention)"]
   [:div {:class ["flex" "items-center" "justify-between" "text-xs"]}
    [:span {:class ["flex" "items-center" "gap-1.5" "text-trading-text-secondary"]}
     [:span {:class ["inline-block" "h-2" "w-2" "rounded-full" "bg-amber-400"]}]
     "Current margin"]
    [:span {:class ["num" "font-semibold" "text-amber-300"]}
     (fmt-probability p-now)]]
   [:div {:class ["mt-1.5" "flex" "items-center" "justify-between" "text-xs"]}
    [:span {:class ["flex" "items-center" "gap-1.5" "text-trading-text-secondary"]}
     [:span {:class ["inline-block" "h-2" "w-2" "rounded-full" "bg-trading-green"]}]
     "Recommended margin"]
    [:span {:class ["num" "font-semibold" "text-trading-green"]}
     (fmt-probability p-after)]]])

(def risk-mode-options
  [[:conservative "Conservative" "~1% risk"]
   [:balanced "Balanced" "~2% risk"]
   [:capital-efficient "Capital efficient" "~5% risk"]])

(defn- risk-mode-control
  [active-mode]
  [:div {:class ["rounded-md" "bg-base-300/40" "p-2.5"]
         :data-role "margin-rec-risk-mode"}
   [:div {:class ["mb-2" "text-xs" "font-semibold" "text-trading-text"]}
    "Target liquidation risk"]
   (into [:div {:class ["grid" "grid-cols-3" "gap-1"]}]
         (map (fn [[mode label sub]]
                (let [active? (= mode active-mode)]
                  [:button {:type "button"
                            :aria-pressed (if active? "true" "false")
                            :data-role (str "margin-rec-risk-mode-" (name mode))
                            :class (into ["rounded-md" "px-2" "py-1.5" "text-center"
                                          "text-xs" "font-medium" "transition-colors"
                                          "focus:outline-none" "focus:ring-1"
                                          "focus:ring-ho-text-muted/40"]
                                         (if active?
                                           ["bg-base-100" "text-trading-text"
                                            "border" "border-trading-green/50"]
                                           ["bg-transparent" "text-trading-text-secondary"
                                            "hover:text-trading-text"]))
                            :on {:click [[:actions/set-margin-rec-risk-mode mode]]}}
                   [:span {:class ["block"]} label]
                   [:span {:class ["block" "text-xs" "text-trading-text-secondary"]} sub]]))
              risk-mode-options))])

(defn- status-note
  [message role]
  [:div {:class ["rounded-md" "bg-base-300/40" "px-3" "py-2" "text-xs"
                 "text-trading-text-secondary"]
         :data-role role}
   message])

(def ^:private dialog-focus-on-render
  (dialog-focus/dialog-focus-on-render
   {:restore-selector "[data-role='margin-rec-risk-chip']"}))

(defn- panel-shell
  [coin-label anchor children]
  (let [layout-style (anchored-popover/anchored-popover-layout-style
                      {:anchor anchor
                       :preferred-width-px 460
                       ;; Height estimate at/above the real content (~820px) so
                       ;; the layout helper clamps `top` enough to keep the whole
                       ;; card on-screen while still sitting near its trigger.
                       ;; Short viewports fall back to top-margin + internal
                       ;; scroll (max-h below), always within the viewport.
                       :estimated-height-px 880})]
    [:div {:class ["fixed" "z-[240]" "flex" "max-h-[calc(100vh-1.5rem)]" "flex-col"
                   "overflow-hidden" "rounded-xl" "border" "border-base-300" "bg-base-200"
                   "shadow-[0_24px_70px_rgba(0,0,0,0.55)]"]
           :style layout-style
           :role "dialog"
           :aria-label "Isolated margin recommendation"
           :tabindex "-1"
           :replicant/on-render dialog-focus-on-render
           :on {:keydown [[:actions/handle-margin-rec-panel-keydown [:event/key]]]}
           :data-role "margin-rec-panel"}
     [:div {:class ["flex" "shrink-0" "items-center" "justify-between" "gap-3"
                    "border-b" "border-base-300" "px-4" "py-3"]}
      [:div {:class ["flex" "min-w-0" "items-center" "gap-2"]}
       [:span {:class ["truncate" "text-sm" "font-semibold" "text-trading-text"]}
        "Isolated margin recommendation"]
       [:span {:class ["shrink-0" "rounded" "border" "border-base-300" "px-1.5" "py-0.5"
                       "text-xs" "font-medium" "text-trading-text-secondary"]}
        coin-label]]
      [:button {:type "button"
                :data-role "margin-rec-panel-close"
                :class ["inline-flex" "h-7" "w-7" "shrink-0" "items-center" "justify-center"
                        "rounded-md" "border" "border-base-300" "text-sm"
                        "text-trading-text-secondary" "transition-colors"
                        "hover:bg-base-300" "hover:text-trading-text"
                        "focus:outline-none" "focus:ring-1"
                        "focus:ring-ho-text-muted/40"]
                :aria-label "Hide recommendation"
                :on {:click [[:actions/close-margin-rec-panel]]}}
       "✕"]]
     (into [:div {:class ["min-h-0" "space-y-2.5" "overflow-y-auto" "px-4" "py-3"]}]
           children)]))

(defn- ready-panel
  [{:keys [position-key rec-result row-vm read-only? risk-mode]}]
  (let [{:keys [as-of sigma horizon p-now p-after recommended breakdown
                confidence status]} rec-result
        {:keys [additional equity new-liquidation-px new-liq-change-frac
                effective-leverage]} recommended
        horizon* (horizon-copy horizon)
        position-data (:row-data row-vm)
        actionable? (and (not read-only?)
                         (= :ok status)
                         (number? additional)
                         (>= additional 0.01))]
    [[:div {:class ["grid" "grid-cols-2" "gap-2"]}
      (tile "margin-rec-tile-current"
            "Current isolated margin"
            (fmt-usd (:equity as-of))
            (str "Liq. " (fmt-price (:liquidation-px as-of))))
      (tile "margin-rec-tile-distance"
            "Distance to liquidation"
            (if-let [sigmas (:buffer-sigmas sigma)]
              (str (.toFixed sigmas 2) "σ buffer")
              "--")
            (when-let [frac (:distance-frac sigma)]
              (str "≈ " (fmt-percent frac 1) " adverse move")))
      (tile "margin-rec-tile-horizon"
            "Est. intervention horizon"
            (:duration horizon*)
            (:basis horizon*))
      (tile "margin-rec-tile-recommended"
            "Recommended isolated margin"
            (fmt-usd equity)
            (when (number? additional)
              (if (>= additional 0.01)
                (str "+" (fmt-usd additional) " additional")
                "already met")))
      (tile "margin-rec-tile-new-liq"
            "Est. new liquidation price"
            (fmt-price new-liquidation-px)
            (when (number? new-liq-change-frac)
              (str "≈ " (fmt-percent (js/Math.abs new-liq-change-frac) 1)
                   (if (pos? new-liq-change-frac) " lower" " higher"))))
      (tile "margin-rec-tile-leverage"
            "Recommended effective leverage"
            (if (number? effective-leverage)
              (str (.toFixed effective-leverage 1) "x")
              "--")
            (when-let [tier (:tier confidence)]
              (str "model confidence: " (name tier))))]
     (breakdown-table breakdown equity)
     (risk-compare p-now p-after)
     (risk-mode-control risk-mode)
     (when (= :within-target status)
       (status-note "Current margin already meets the selected risk target."
                    "margin-rec-within-target"))
     [:div {:class ["flex" "flex-col" "gap-2"]}
       (when actionable?
         [:button {:type "button"
                   :data-role "margin-rec-apply"
                   :class ["h-9" "rounded-md" "bg-trading-green/15" "px-3"
                           "text-sm" "font-semibold" "text-trading-green"
                           "transition-colors" "hover:bg-trading-green/25"
                           "focus:outline-none" "focus:ring-1"
                           "focus:ring-trading-green/40"]
                   :on {:click [[:actions/open-position-margin-modal
                                 (assoc position-data
                                        :prefill-margin-mode :add
                                        :prefill-margin-amount additional)
                                 :event.currentTarget/bounds]
                                [:actions/close-margin-rec-panel]]}}
          (str "Add recommended margin " (fmt-usd additional))])
       (when-not read-only?
         [:button {:type "button"
                   :data-role "margin-rec-reduce"
                   :class ["h-9" "rounded-md" "border" "border-base-300" "px-3"
                           "text-sm" "font-medium" "text-trading-text"
                           "transition-colors" "hover:bg-base-300"
                           "focus:outline-none" "focus:ring-1"
                           "focus:ring-ho-text-muted/40"]
                   :on {:click [[:actions/open-position-reduce-popover
                                 position-data
                                 :event.currentTarget/bounds]
                                [:actions/close-margin-rec-panel]]}}
          "Reduce position instead"])
       [:div {:class ["text-xs" "leading-4" "text-trading-text-secondary"]}
        (str "Modeled from a block bootstrap of recent hourly moves."
             " No auto-execution — you stay in control.")]]]))

(defn margin-recommendation-panel
  "options: {:position-key :rec :row-vm :read-only? :risk-mode :anchor}
  `rec` is the [:margin-rec :recs <key>] entry; `anchor` is the trigger's
  bounds the popover is positioned against."
  [{:keys [position-key rec row-vm anchor] :as options}]
  (when (and position-key row-vm)
    (let [{:keys [status result error]} rec
          coin-label (or (:coin-label row-vm) position-key)]
      (panel-shell
       coin-label
       anchor
       (cond
         (nil? rec)
         [(status-note "Modeling liquidation risk in the background…"
                       "margin-rec-computing")]

         (= :error status)
         [(status-note (str "Could not model this position: " (or error "unknown error"))
                       "margin-rec-error")]

         (= :insufficient-history status)
         [(status-note
           (str "Not enough hourly price history to model liquidation risk yet"
                " (" (or (:n-bars result) (:n-bars rec) 0) " bars).")
           "margin-rec-insufficient")]

         (= :invalid status)
         [(status-note "This position cannot be modeled." "margin-rec-invalid")]

         (contains? #{:ok :within-target} status)
         (ready-panel (assoc options :rec-result result))

         :else
         [(status-note "Modeling liquidation risk in the background…"
                       "margin-rec-computing")])))))
