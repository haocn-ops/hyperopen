(ns hyperopen.views.account-info.margin-recommendation-panel
  "Anchored popover showing the modeled isolated-margin recommendation for one
  position, laid out to the designer's card: current-state stats, the green
  recommendation block, the modeled probability-of-liquidation-vs-collateral
  curve with Current/Recommended markers, how-we-estimated + buffers columns,
  the target-risk selector, and the apply / custom-margin actions.

  This is a page-local, recoverable control, so per docs/FRONTEND.md it is an
  anchored popover (like the sibling Reduce/Margin popovers in this table) —
  not a full-screen modal. It floats from the risk chip that opens it, sized
  and clamped to the viewport by hyperopen.views.ui.anchored-popover."
  (:require [clojure.string :as str]
            [hyperopen.views.account-info.margin-rec-curve :as margin-rec-curve]
            [hyperopen.views.account-info.shared :as shared]
            [hyperopen.views.ui.anchored-popover :as anchored-popover]
            [hyperopen.views.ui.dialog-focus :as dialog-focus]))

(defn- fmt-usd
  [value]
  (if (number? value)
    (str "$" (shared/format-currency value))
    "--"))

(defn- fmt-usdc
  [value]
  (if (number? value)
    (str "$" (shared/format-currency value) " USDC")
    "--"))

(defn- fmt-price
  [value]
  (if (number? value)
    (shared/format-trade-price value)
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
                :per-coin (str "inferred from " samples
                               " similar position episodes")
                :account (str "inferred from " samples
                              " episodes across your account")
                :override "manual override"
                "default horizon — limited trade history")]
    {:duration duration :basis basis}))

;; --- stat cells --------------------------------------------------------------

(defn- stat-cell
  [role label value sub & [value-class]]
  [:div {:class ["min-w-0" "px-3" "py-2"]
         :data-role role}
   [:div {:class ["text-xs" "text-trading-text-secondary"]} label]
   [:div {:class ["text-sm" "font-semibold" "num"
                  (or value-class "text-trading-text")]}
    value]
   (when sub
     [:div {:class ["text-xs" "text-trading-text-secondary"]} sub])])

(defn- current-stats
  [{:keys [as-of sigma horizon p-now]}]
  (let [horizon* (horizon-copy horizon)]
    [:div {:class ["overflow-hidden" "rounded-lg" "border" "border-base-300"
                   "bg-base-300/20"]
           :data-role "margin-rec-current-stats"}
     [:div {:class ["grid" "grid-cols-3" "divide-x" "divide-base-300"]}
      (stat-cell "margin-rec-stat-current" "Current margin"
                 (fmt-usdc (:equity as-of)) nil)
      (stat-cell "margin-rec-stat-liq" "Current liq. price"
                 (fmt-price (:liquidation-px as-of)) nil)
      (stat-cell "margin-rec-stat-distance" "Distance to liq."
                 (if-let [sigmas (:buffer-sigmas sigma)]
                   (str (.toFixed sigmas 2) "σ buffer")
                   "--")
                 (when-let [frac (:distance-frac sigma)]
                   (str "≈ " (fmt-percent frac 1) " adverse move")))]
     [:div {:class ["grid" "grid-cols-2" "divide-x" "divide-base-300"
                    "border-t" "border-base-300"]}
      (stat-cell "margin-rec-stat-horizon" "Typical intervention"
                 (:duration horizon*)
                 (:basis horizon*))
      (stat-cell "margin-rec-stat-p-now" "Modeled liq. probability (current)"
                 (fmt-probability p-now)
                 "before next intervention"
                 "text-amber-300")]]))

(defn- recommendation-block
  [{:keys [as-of recommended p-after]}]
  (let [{:keys [equity additional new-liquidation-px new-liq-change-frac]} recommended
        current-equity (:equity as-of)
        vs-current (when (and (number? additional)
                              (number? current-equity)
                              (pos? current-equity)
                              (>= additional 0.01))
                     (/ additional current-equity))]
    [:div {:class ["overflow-hidden" "rounded-lg" "border" "border-trading-green/30"
                   "bg-trading-green/5"]
           :data-role "margin-rec-recommendation"}
     [:div {:class ["grid" "grid-cols-2" "divide-x" "divide-base-300"]}
      [:div {:class ["min-w-0" "px-3" "py-2"]
             :data-role "margin-rec-recommended"}
       [:div {:class ["text-xs" "text-trading-text-secondary"]}
        "Recommended isolated margin"]
       [:div {:class ["text-lg" "font-semibold" "num" "text-trading-green"]}
        (fmt-usdc equity)]
       (when vs-current
         [:div {:class ["mt-0.5" "inline-block" "rounded" "bg-trading-green/15"
                        "px-1.5" "py-0.5" "text-xs" "font-medium"
                        "text-trading-green"]}
          (str "+" (fmt-percent vs-current 1) " vs current")])]
      [:div {:class ["min-w-0" "px-3" "py-2"]
             :data-role "margin-rec-additional"}
       [:div {:class ["text-xs" "text-trading-text-secondary"]}
        "Additional collateral needed"]
       [:div {:class ["text-lg" "font-semibold" "num" "text-trading-text"]}
        (fmt-usdc additional)]]]
     [:div {:class ["grid" "grid-cols-2" "divide-x" "divide-base-300"
                    "border-t" "border-base-300"]}
      (stat-cell "margin-rec-new-liq" "New liquidation price (est.)"
                 (fmt-price new-liquidation-px)
                 (when (number? new-liq-change-frac)
                   (str "≈ " (fmt-percent (js/Math.abs new-liq-change-frac) 1)
                        (if (pos? new-liq-change-frac) " lower" " higher"))))
      (stat-cell "margin-rec-p-after" "New modeled liq. probability (est.)"
                 (fmt-probability p-after)
                 "after recommendation"
                 "text-trading-green")]]))

;; --- methods + buffers -------------------------------------------------------

(defn- method-row
  [label value]
  [:div {:class ["flex" "items-baseline" "justify-between" "gap-2" "text-xs"]}
   [:span {:class ["flex" "min-w-0" "items-baseline" "gap-1.5"
                   "text-trading-text-secondary"]}
    [:span {:class ["shrink-0" "text-trading-green"]} "✓"]
    [:span {:class ["min-w-0"]} label]]
   [:span {:class ["shrink-0" "num" "text-trading-text"]} value]])

(defn- methods-column
  [{:keys [sigma horizon paths-count]} coin-label]
  (let [horizon* (horizon-copy horizon)]
    [:div {:class ["min-w-0" "space-y-1.5"]
           :data-role "margin-rec-methods"}
     [:div {:class ["text-xs" "font-semibold" "text-trading-text"]}
      "How we estimated this"]
     (method-row "365-day crypto volatility convention" "Applied")
     (method-row (str "Recent realized volatility (" coin-label ")")
                 (fmt-percent (:annualized sigma) 0))
     (method-row (if (number? paths-count)
                   (str "Scenario simulation ("
                        (.toLocaleString paths-count "en-US") " paths)")
                   "Scenario simulation")
                 "Monte Carlo")
     (method-row "Trade-history-derived horizon" (:duration horizon*))]))

(defn- buffers-column
  [{:keys [breakdown recommended]}]
  (let [total (:equity recommended)
        buffers (remove #(= :maintenance (:key %)) breakdown)]
    (into [:div {:class ["min-w-0" "space-y-1.5"]
                 :data-role "margin-rec-buffers"}
           [:div {:class ["text-xs" "font-semibold" "text-trading-text"]}
            "Buffers included"]]
          (map (fn [{:keys [key label amount]}]
                 [:div {:key (name key)
                        :class ["flex" "items-baseline" "justify-between" "gap-2" "text-xs"]
                        :data-role (str "margin-rec-buffer-" (name key))}
                  [:span {:class ["min-w-0" "text-trading-text-secondary"]} label]
                  [:span {:class ["shrink-0" "num" "text-trading-text"]}
                   (str (fmt-usd amount)
                        (when (and (number? amount) (number? total) (pos? total))
                          (str " (" (js/Math.round (* 100 (/ amount total))) "%)")))]])
               buffers))))

;; --- risk mode ---------------------------------------------------------------

(def risk-mode-options
  [[:conservative "Conservative" "~1% risk"]
   [:balanced "Balanced" "~2% risk"]
   [:capital-efficient "Capital efficient" "~5% risk"]])

(defn- risk-mode-control
  [active-mode]
  [:div {:data-role "margin-rec-risk-mode"}
   [:div {:class ["mb-1.5" "text-xs" "text-trading-text-secondary"]}
    "Target liquidation risk"]
   (into [:div {:class ["grid" "grid-cols-3" "gap-1.5"]}]
         (map (fn [[mode label sub]]
                (let [active? (= mode active-mode)]
                  [:button {:type "button"
                            :aria-pressed (if active? "true" "false")
                            :data-role (str "margin-rec-risk-mode-" (name mode))
                            :class (into ["rounded-md" "border" "px-2" "py-2"
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
              risk-mode-options))
   [:div {:class ["mt-1.5" "text-center" "text-xs" "text-trading-text-secondary"]}
    "You can adjust this anytime in Settings."]])

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
  [coin-label leverage-label anchor children]
  (let [layout-style (anchored-popover/anchored-popover-layout-style
                      {:anchor anchor
                       :preferred-width-px 480
                       ;; Height estimate at/above the real content (~960px) so
                       ;; the layout helper clamps `top` enough to keep the whole
                       ;; card on-screen while still sitting near its trigger.
                       ;; Short viewports fall back to top-margin + internal
                       ;; scroll (max-h below), always within the viewport.
                       :estimated-height-px 960})]
    [:div {:class ["fixed" "z-[240]" "flex" "max-h-[calc(100vh-1.5rem)]" "flex-col"
                   "overflow-hidden" "rounded-xl" "border" "border-base-300" "bg-base-200"
                   "shadow-[0_24px_70px_rgba(0,0,0,0.55)]"]
           :style layout-style
           :role "dialog"
           :aria-label "Margin recommendation"
           :tabindex "-1"
           :replicant/on-render dialog-focus-on-render
           :on {:keydown [[:actions/handle-margin-rec-panel-keydown [:event/key]]]}
           :data-role "margin-rec-panel"}
     [:div {:class ["shrink-0" "border-b" "border-base-300" "px-4" "py-3"]}
      [:div {:class ["flex" "items-center" "justify-between" "gap-3"]}
       [:span {:class ["truncate" "text-base" "font-semibold" "text-trading-text"]}
        "Margin recommendation"]
       [:button {:type "button"
                 :data-role "margin-rec-panel-close"
                 :class ["inline-flex" "h-7" "w-7" "shrink-0" "items-center"
                         "justify-center" "rounded-md" "text-sm"
                         "text-trading-text-secondary" "transition-colors"
                         "hover:bg-base-300" "hover:text-trading-text"
                         "focus:outline-none" "focus:ring-1"
                         "focus:ring-ho-text-muted/40"]
                 :aria-label "Hide recommendation"
                 :on {:click [[:actions/close-margin-rec-panel]]}}
        "✕"]]
      [:div {:class ["mt-1.5" "flex" "items-center" "gap-2"]}
       [:span {:class ["rounded" "bg-trading-green/15" "px-1.5" "py-0.5" "text-xs"
                       "font-semibold" "text-trading-green"]
               :data-role "margin-rec-coin"}
        coin-label]
       (when leverage-label
         [:span {:class ["text-xs" "text-trading-text-secondary"]
                 :data-role "margin-rec-leverage"}
          leverage-label])]]
     (into [:div {:class ["min-h-0" "space-y-2.5" "overflow-y-auto" "px-4" "py-3"]}]
           children)]))

(defn- ready-panel
  [{:keys [rec-result row-vm read-only? risk-mode]}]
  (let [{:keys [as-of p-now p-after recommended curve status]} rec-result
        {:keys [additional]} recommended
        position-data (:row-data row-vm)
        actionable? (and (not read-only?)
                         (= :ok status)
                         (number? additional)
                         (>= additional 0.01))]
    [(current-stats rec-result)
     (recommendation-block rec-result)
     (margin-rec-curve/curve-card curve (:equity as-of) p-now (:equity recommended) p-after)
     [:div {:class ["grid" "grid-cols-2" "gap-3" "rounded-lg" "bg-base-300/40" "p-3"]}
      (methods-column rec-result (:coin-label row-vm))
      (buffers-column rec-result)]
     (risk-mode-control risk-mode)
     (when (= :within-target status)
       (status-note "Current margin already meets the selected risk target."
                    "margin-rec-within-target"))
     [:div {:class ["flex" "flex-col" "gap-2"]}
      (when actionable?
        [:button {:type "button"
                  :data-role "margin-rec-apply"
                  :class ["h-10" "w-full" "rounded-md" "bg-trading-green" "px-3"
                          "text-sm" "font-semibold" "text-base-100"
                          "transition-colors" "hover:bg-trading-green/90"
                          "focus:outline-none" "focus:ring-1"
                          "focus:ring-trading-green/40"]
                  :on {:click [[:actions/open-position-margin-modal
                                (assoc position-data
                                       :prefill-margin-mode :add
                                       :prefill-margin-amount additional)
                                :event.currentTarget/bounds]
                               [:actions/close-margin-rec-panel]]}}
         "Apply recommendation"])
      (when-not read-only?
        [:button {:type "button"
                  :data-role "margin-rec-custom"
                  :class ["h-10" "w-full" "rounded-md" "border" "border-base-300"
                          "px-3" "text-sm" "font-medium" "text-trading-text"
                          "transition-colors" "hover:bg-base-300"
                          "focus:outline-none" "focus:ring-1"
                          "focus:ring-ho-text-muted/40"]
                  :on {:click [[:actions/open-position-margin-modal
                                position-data
                                :event.currentTarget/bounds]
                               [:actions/close-margin-rec-panel]]}}
         "Set custom margin"])]]))

(defn- leverage-copy
  [row-vm]
  (let [value (get-in row-vm [:position :leverage :value])
        mode (some-> (:margin-mode-label row-vm) str/lower-case)]
    (cond
      (and value mode) (str value "x " mode)
      mode mode
      :else nil)))

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
       (leverage-copy row-vm)
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
