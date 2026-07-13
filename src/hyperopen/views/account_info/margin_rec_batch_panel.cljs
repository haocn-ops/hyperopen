(ns hyperopen.views.account-info.margin-rec-batch-panel
  "Toolbar trigger and anchored popover for fixing several at-risk isolated
  positions in one action: every position with a high/elevated modeled
  liquidation risk is listed with its recommended top-up under the shared risk
  target, and Apply submits one updateIsolatedMargin per selected position
  (worst risks funded first when collateral runs short).

  Like the single-position recommendation panel this is a page-local,
  recoverable control, so per docs/FRONTEND.md it is an anchored popover — not
  a full-screen modal."
  (:require [hyperopen.margin-rec.state :as margin-rec-state]
            [hyperopen.views.account-info.margin-rec-copy :as copy]
            [hyperopen.views.account-info.shared :as shared]
            [hyperopen.views.ui.anchored-popover :as anchored-popover]
            [hyperopen.views.ui.dialog-focus :as dialog-focus]
            [hyperopen.views.ui.hint-tooltip :as hint]))

(defn- fmt-usd
  [value]
  (if (number? value)
    (str "$" (shared/format-currency value))
    "--"))

(defn- fmt-probability
  [p]
  (cond
    (not (number? p)) "--"
    (< p 0.0005) "<0.1%"
    :else (str (.toFixed (* 100 p) 1) "%")))

(def risk-mode-options
  [[:conservative "Conservative" "~1% risk"]
   [:balanced "Balanced" "~2% risk"]
   [:capital-efficient "Capital efficient" "~5% risk"]])

(defn- risk-mode-control
  [active-mode]
  [:div {:data-role "margin-rec-batch-risk-mode"}
   [:div {:class ["mb-1.5" "text-xs" "text-trading-text-secondary"]}
    "Target liquidation risk (applies to all positions)"]
   (into [:div {:class ["grid" "grid-cols-3" "gap-1.5"]}]
         (map (fn [[mode label sub]]
                (let [active? (= mode active-mode)]
                  [:button {:type "button"
                            :aria-pressed (if active? "true" "false")
                            :data-role (str "margin-rec-batch-risk-mode-" (name mode))
                            :class (into ["rounded-md" "border" "px-2" "py-1.5"
                                          "text-center" "text-xs" "font-medium"
                                          "transition-colors" "focus:outline-none"
                                          "focus:ring-1" "focus:ring-ho-text-muted/40"]
                                         (if active?
                                           ["border-trading-green" "bg-trading-green/10"
                                            "text-trading-text"]
                                           ["border-base-300" "bg-transparent"
                                            "text-trading-text-secondary"
                                            "hover:text-trading-text"]))
                            :on {:click [[:actions/set-margin-rec-risk-mode mode]]}}
                   [:span {:class ["block"]} label]
                   [:span {:class ["block" "text-xs" "text-trading-text-secondary"]} sub]]))
              risk-mode-options))])

(defn- risk-level-chip
  [risk-level]
  [:span {:class (into ["rounded" "px-1.5" "py-0.5" "text-xs" "font-medium"]
                       (if (= :high risk-level)
                         ["bg-amber-400/15" "text-amber-300"]
                         ["bg-amber-400/10" "text-amber-200/90"]))}
   (if (= :high risk-level) "High" "Elevated")])

(defn- candidate-row
  [{:keys [position-key coin risk-level additional p-now p-after
           new-liquidation-px]}
   selected?]
  [:label {:class ["flex" "cursor-pointer" "items-center" "gap-3" "px-3" "py-2"
                   "transition-colors" "hover:bg-base-300/30"]
           :replicant/key position-key
           :data-role "margin-rec-batch-row"}
   [:input {:type "checkbox"
            :class ["h-4" "w-4" "shrink-0" "rounded-[3px]" "border" "border-base-300"
                    "bg-transparent" "trade-toggle-checkbox" "transition-colors"
                    "focus:outline-none" "focus:ring-0" "focus:ring-offset-0"
                    "focus:shadow-none"]
            :checked selected?
            :data-role "margin-rec-batch-row-toggle"
            :on {:change [[:actions/toggle-margin-rec-batch-selection position-key]]}}]
   [:div {:class ["flex" "min-w-0" "flex-1" "flex-wrap" "items-center" "gap-x-3" "gap-y-0.5"]}
    [:div {:class ["flex" "w-40" "min-w-0" "items-center" "gap-2"]}
     [:span {:class ["truncate" "text-sm" "font-semibold" "text-trading-text"]}
      coin]
     (risk-level-chip risk-level)]
    [:div {:class ["w-24" "text-sm" "font-semibold" "num" "text-trading-green"]
           :data-role "margin-rec-batch-row-additional"}
     (str "+" (fmt-usd additional))]
    [:div {:class ["flex" "items-center" "gap-1" "text-xs" "num"]}
     [:span {:class ["text-amber-300"]} (fmt-probability p-now)]
     [:span {:class ["text-trading-text-secondary"]} "→"]
     [:span {:class ["text-trading-green"]} (fmt-probability p-after)]]
    (when (number? new-liquidation-px)
      [:div {:class ["ml-auto" "text-xs" "text-trading-text-secondary"]}
       (str "new liq. " (fmt-usd new-liquidation-px))])]])

(def ^:private dialog-focus-on-render
  (dialog-focus/dialog-focus-on-render
   {:restore-selector "[data-role='margin-rec-batch-trigger']"}))

(defn- panel-note
  [message role]
  [:div {:class ["rounded-md" "bg-base-300/40" "px-3" "py-2" "text-xs"
                 "text-trading-text-secondary"]
         :data-role role}
   message])

(defn batch-panel
  "The anchored batch top-up popover. `margin-rec` is the ui-slice from
  positions-state."
  [{:keys [batch batch-candidates batch-computing-count batch-available-pools
           risk-mode]}]
  (let [{:keys [anchor deselected]} batch
        deselected (set deselected)
        selected (vec (remove (comp deselected :position-key) batch-candidates))
        {:keys [total covered fundable-count skipped-count]}
        (margin-rec-state/batch-coverage selected batch-available-pools)
        shortfall? (pos? skipped-count)
        layout-style (anchored-popover/centered-overlay-layout-style
                      {:anchor anchor
                       :preferred-width-px 560
                       :preferred-height-px 520})]
    [:div {:class ["fixed" "z-[240]" "flex" "max-h-[calc(100vh-1.5rem)]" "flex-col"
                   "overflow-hidden" "rounded-xl" "border" "border-base-300" "bg-base-200"
                   "shadow-2xl"]
           :style layout-style
           :role "dialog"
           :aria-label "Fix liquidation risks"
           :tabindex "-1"
           :replicant/on-render dialog-focus-on-render
           :on {:keydown [[:actions/handle-margin-rec-batch-keydown [:event/key]]]}
           :data-role "margin-rec-batch-panel"}
     [:div {:class ["flex" "shrink-0" "items-center" "gap-2" "border-b"
                    "border-base-300" "px-4" "py-3"]}
      [:span {:class ["text-base" "font-semibold" "text-trading-text"]}
       "Fix liquidation risks"]
      [:span {:class ["rounded" "bg-amber-400/15" "px-1.5" "py-0.5" "text-xs"
                      "font-semibold" "text-amber-300"]
              :data-role "margin-rec-batch-count"}
       (count batch-candidates)]
      [:button {:type "button"
                :data-role "margin-rec-batch-close"
                :aria-label "Close"
                :class ["ml-auto" "inline-flex" "h-7" "w-7" "shrink-0" "items-center"
                        "justify-center" "rounded-md" "text-sm"
                        "text-trading-text-secondary" "transition-colors"
                        "hover:bg-base-300" "hover:text-trading-text"
                        "focus:outline-none" "focus:ring-1"
                        "focus:ring-ho-text-muted/40"]
                :on {:click [[:actions/close-margin-rec-batch-panel]]}}
       "✕"]]
     [:div {:class ["min-h-0" "space-y-3" "overflow-y-auto" "px-4" "py-3"]}
      [:div {:class ["text-xs" "leading-relaxed" "text-trading-text-secondary"]}
       "Modeled top-ups that bring every at-risk isolated position to the selected risk target. Uncheck any position you'd rather handle yourself."]
      (risk-mode-control risk-mode)
      (if (seq batch-candidates)
        (into [:div {:class ["divide-y" "divide-base-300" "rounded-lg" "border"
                             "border-base-300"]}]
              (map (fn [candidate]
                     (candidate-row candidate
                                    (not (contains? deselected
                                                    (:position-key candidate)))))
                   batch-candidates))
        (panel-note "No positions currently need a top-up under this risk target."
                    "margin-rec-batch-empty"))
      (when (pos? batch-computing-count)
        (panel-note (str "Still modeling " batch-computing-count
                         (if (= 1 batch-computing-count) " position" " positions")
                         " in the background — reopen in a moment for the full picture.")
                    "margin-rec-batch-computing"))
      [:div {:class ["flex" "flex-wrap" "items-baseline" "justify-between" "gap-2"
                     "rounded-lg" "border" "border-base-300" "bg-base-300/20"
                     "px-3" "py-2"]
             :data-role "margin-rec-batch-total"}
       [:span {:class ["text-xs" "text-trading-text-secondary"]}
        (str "Total to add across " (count selected)
             (if (= 1 (count selected)) " position" " positions"))]
       [:span {:class ["text-sm" "font-semibold" "num" "text-trading-text"]}
        (fmt-usd total)]]
      (when shortfall?
        [:div {:class ["rounded-md" "bg-amber-400/10" "px-3" "py-2" "text-xs"
                       "leading-relaxed" "text-amber-300"]
               :data-role "margin-rec-batch-shortfall"}
         (str "Available collateral covers " (fmt-usd covered) " of "
              (fmt-usd total) ". The highest-risk positions are funded first; "
              skipped-count
              (if (= 1 skipped-count) " position" " positions")
              " would be skipped.")])
      [:button {:type "button"
                :data-role "margin-rec-batch-apply"
                :disabled (zero? fundable-count)
                :class ["h-10" "w-full" "rounded-md" "bg-trading-green" "px-3"
                        "text-sm" "font-semibold" "text-base-100"
                        "transition-colors" "hover:bg-trading-green/90"
                        "focus:outline-none" "focus:ring-1"
                        "focus:ring-trading-green/40"
                        "disabled:cursor-not-allowed" "disabled:opacity-50"]
                :on {:click [[:actions/apply-margin-rec-batch]]}}
       (str "Add margin to " fundable-count
            (if (= 1 fundable-count) " position" " positions")
            " · " (fmt-usd covered))]
      [:div {:class ["text-xs" "leading-relaxed" "text-trading-text-secondary"]
             :data-role "margin-rec-batch-disclaimer"}
       (copy/tip :disclaimer)]]]))

(defn batch-trigger
  "Toolbar button for the Positions tab: visible whenever at least one
  isolated position faces a modeled high/elevated liquidation risk with an
  actionable top-up. Returns nil when there is nothing to fix."
  [{:keys [batch batch-candidates] :as margin-rec} read-only?]
  (when (and (map? margin-rec)
             (not read-only?)
             (seq batch-candidates))
    [:div {:class ["flex" "items-center"]}
     (hint/attach
      "Review and apply the recommended margin top-up for every at-risk position in one action."
      [:button {:type "button"
                :data-role "margin-rec-batch-trigger"
                :aria-haspopup "dialog"
                :aria-expanded (if (:open? batch) "true" "false")
                :class ["inline-flex" "h-7" "items-center" "gap-1.5" "rounded-md"
                        "border" "border-amber-400/40" "bg-amber-400/10" "px-2.5"
                        "text-xs" "font-semibold" "text-amber-300"
                        "transition-colors" "hover:bg-amber-400/20"
                        "focus:outline-none" "focus:ring-1"
                        "focus:ring-amber-400/40"]
                :on {:click [[:actions/toggle-margin-rec-batch-panel
                              :event.currentTarget/bounds]]}}
       [:span {:aria-hidden true} "⚠"]
       (str "Fix liq. risks (" (count batch-candidates) ")")]
      {:placement :bottom-end :cursor? false :delay? true})
     (when (:open? batch)
       (batch-panel margin-rec))]))
