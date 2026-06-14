(ns hyperopen.views.portfolio.optimize.refinement-status-card
  "Center 'Optimization status / Refinement options' card. Pure view of the refinement
  view-model (application.view-model.refinement). Shows the result-quality tier, lets the
  user pick a refinement depth and re-run at higher frontier density, reports in-flight
  progress, and — after a refinement — summarizes whether the selected portfolio changed."
  (:require [hyperopen.views.portfolio.optimize.format :as opt-format]))

(defn- status-dot-class
  [status]
  (case status
    :ok "text-trading-green"
    :caution "text-warning"
    "text-trading-muted"))

(defn- quality-status
  [quality]
  (case quality
    :high :ok
    :medium :ok
    :caution))

(defn- stability-status
  [stability]
  (case stability
    :provisional :caution
    :ok))

(defn- chip
  [data-role status label value]
  [:span {:class ["optimizer-refinement-chip"
                  "inline-flex" "items-center" "gap-1.5"
                  "rounded-full" "border" "border-base-300" "bg-base-200/50"
                  "px-2.5" "py-1" "text-[0.62rem]" "font-semibold"]
          :data-role data-role}
   [:span {:class [(status-dot-class status)]} "●"]
   [:span {:class ["text-trading-muted"]} label]
   [:span {:class ["text-trading-text"]} value]])

(defn- status-header
  [{:keys [assessment runtime-ms]}]
  (let [tier (:tier assessment)
        exact? (:exact-selection? assessment)]
    [:div {:class ["optimizer-refinement-status"]
           :data-role "portfolio-optimizer-refinement-status"}
     [:div {:class ["flex" "items-start" "gap-3"]}
      [:span {:class ["mt-0.5" "text-trading-green" "text-base"]} "✓"]
      [:div {:class ["min-w-0"]}
       [:p {:class ["text-[0.65rem]" "font-semibold" "uppercase" "tracking-[0.18em]"
                    "text-trading-muted"]}
        "Optimization status"]
       [:p {:class ["mt-1" "text-sm" "font-semibold" "text-trading-text"]}
        (opt-format/refinement-tier-ready-label tier)]
       [:p {:class ["mt-1" "text-xs" "text-trading-muted"]}
        (if (= :draft tier)
          "Fast draft based on the current frontier sample. Refine for higher confidence."
          "Refined over a denser frontier.")
        (if exact?
          " Selection is exact for this objective — refinement sharpens the chart."
          " Selection is sampled from the frontier and may shift when refined.")]]]
     [:div {:class ["mt-3" "flex" "flex-wrap" "items-center" "gap-2"]}
      (chip "portfolio-optimizer-refinement-points"
            :ok
            "Points"
            (str (or (:point-count assessment) "—")))
      (chip "portfolio-optimizer-refinement-runtime"
            :ok
            "Runtime"
            (opt-format/format-duration runtime-ms))
      (chip "portfolio-optimizer-refinement-quality"
            (quality-status (:frontier-quality assessment))
            "Frontier quality"
            (opt-format/refinement-quality-label (:frontier-quality assessment)))
      (chip "portfolio-optimizer-refinement-stability"
            (stability-status (:selection-stability assessment))
            "Selection stability"
            (opt-format/refinement-stability-label (:selection-stability assessment)))]]))

(defn- depth-tile
  [{:keys [key points label hint selected?]}]
  [:button {:type "button"
            :class (cond-> ["optimizer-refinement-depth-tile"
                            "flex" "flex-col" "items-start" "gap-0.5"
                            "rounded-lg" "border" "px-3" "py-2" "text-left"
                            "transition-colors"]
                     selected? (conj "border-primary/60" "bg-primary/10" "text-trading-text")
                     (not selected?) (conj "border-base-300" "bg-base-200/40"
                                           "text-trading-muted" "hover:text-trading-text"))
            :data-role (str "portfolio-optimizer-refinement-depth-" (name key))
            :data-selected (str (boolean selected?))
            :aria-pressed (str (boolean selected?))
            :on {:click [[:actions/set-portfolio-optimizer-refinement-depth key]]}}
   [:span {:class ["text-[0.72rem]" "font-semibold"]} label]
   [:span {:class ["text-[0.6rem]" "text-trading-muted"]} hint]
   [:span {:class ["text-[0.58rem]" "font-mono" "text-trading-muted/70"]}
    (str points " points")]])

(defn- refine-options
  [{:keys [depth-options can-refine?]}]
  [:div {:class ["optimizer-refinement-options"]
         :data-role "portfolio-optimizer-refinement-options"}
   [:p {:class ["text-[0.65rem]" "font-semibold" "uppercase" "tracking-[0.18em]"
                "text-trading-muted"]}
    "Refinement options"]
   (into [:div {:class ["mt-2" "grid" "grid-cols-3" "gap-2"]}]
         (map depth-tile depth-options))
   [:button {:type "button"
             :class ["mt-3" "w-full" "rounded-lg" "border" "border-primary/50"
                     "bg-primary/15" "px-3" "py-2" "text-[0.72rem]" "font-semibold"
                     "text-primary" "transition-colors" "hover:bg-primary/25"
                     "disabled:cursor-not-allowed" "disabled:border-base-300"
                     "disabled:bg-base-200/40" "disabled:text-trading-muted"]
             :data-role "portfolio-optimizer-refine-now"
             :disabled (not can-refine?)
             :on (when can-refine?
                   {:click [[:actions/refine-portfolio-optimizer]]})}
    "Refine now"]
   [:p {:class ["mt-2" "text-[0.6rem]" "text-trading-muted/70"]}
    "Your current result stays usable while refinement runs. Selection may change after refinement."]])

(defn- in-flight-view
  [{:keys [progress depth-options]}]
  (let [percent (max 0 (min 100 (or (:overall-percent progress) 0)))
        active-step (some-> (:active-step progress) name)
        selected (some #(when (:selected? %) %) depth-options)]
    [:div {:class ["optimizer-refinement-running"]
           :data-role "portfolio-optimizer-refinement-running"}
     [:p {:class ["text-[0.65rem]" "font-semibold" "uppercase" "tracking-[0.18em]"
                  "text-primary"]}
      "Refining optimization…"]
     [:p {:class ["mt-1" "text-xs" "text-trading-muted"]}
      (str "Targeting " (or (:points selected) "—") " frontier points. "
           "Your current result stays usable.")]
     [:div {:class ["mt-3" "h-1.5" "w-full" "overflow-hidden" "rounded-full" "bg-base-300"]}
      [:div {:class ["h-full" "rounded-full" "bg-primary" "transition-all"]
             :style {:width (str percent "%")}}]]
     [:div {:class ["mt-2" "flex" "items-center" "justify-between" "gap-3"]}
      [:span {:class ["font-mono" "text-[0.62rem]" "text-trading-muted"]}
       (str percent "%" (when active-step (str " · " active-step)))]
      [:button {:type "button"
                :class ["rounded-md" "border" "border-base-300" "bg-base-200/40"
                        "px-2.5" "py-1" "text-[0.62rem]" "font-semibold" "text-trading-muted"
                        "hover:text-trading-text"]
                :data-role "portfolio-optimizer-refinement-stop"
                :on {:click [[:actions/stop-portfolio-optimizer-refinement]]}}
       "Stop · keep current"]]]))

(defn- outcome-row
  [label value delta-class]
  [:div {:class ["flex" "items-center" "justify-between" "gap-3" "py-1"]}
   [:span {:class ["text-[0.62rem]" "uppercase" "tracking-[0.06em]" "text-trading-muted"]} label]
   [:span {:class ["font-mono" "text-[0.7rem]" "tabular-nums" (or delta-class "text-trading-text")]}
    value]])

(defn- signed-delta-class
  [delta good-positive?]
  (cond
    (not (opt-format/finite-number? delta)) "text-trading-muted"
    (zero? delta) "text-trading-muted"
    (= (pos? delta) good-positive?) "text-trading-green"
    :else "text-warning"))

(defn- outcome-view
  [{:keys [change exact-selection? material?]}]
  [:div {:class ["optimizer-refinement-outcome" "mt-3" "rounded-lg" "border"
                 (if material? "border-warning/40" "border-base-300")
                 "bg-base-200/40" "p-3"]
         :data-role "portfolio-optimizer-refinement-outcome"}
   [:p {:class ["text-[0.62rem]" "font-semibold" "uppercase" "tracking-[0.06em]"
                (if material? "text-warning" "text-trading-muted")]}
    (cond
      exact-selection? "Selection stable · chart sharpened"
      material? "Selected portfolio changed materially"
      :else "Selected portfolio stayed stable")]
   (when-not exact-selection?
     [:div {:class ["mt-2" "space-y-0.5"]}
      (outcome-row "Sharpe Δ"
                   (opt-format/format-decimal (:sharpe-delta change))
                   (signed-delta-class (:sharpe-delta change) true))
      (outcome-row "Expected return Δ"
                   (opt-format/format-pct-delta (:return-delta change) {:suffix ""})
                   (signed-delta-class (:return-delta change) true))
      (outcome-row "Volatility Δ"
                   (opt-format/format-pct-delta (:vol-delta change) {:suffix ""})
                   (signed-delta-class (:vol-delta change) false))
      (outcome-row "Allocation changed"
                   (opt-format/format-pct (:weight-l1-delta change))
                   "text-trading-text")])
   (when (and material? (not exact-selection?))
     [:p {:class ["mt-2" "text-[0.6rem]" "text-trading-muted"]}
      "The refined frontier produced a different recommended portfolio. Review before executing."])])

(defn refinement-status-card
  [refinement]
  (when (:solved? refinement)
    [:section {:class ["optimizer-refinement-card"
                       "rounded-xl" "border" "border-base-300" "bg-base-100/95" "p-4"
                       "space-y-4"]
               :data-role "portfolio-optimizer-refinement-card"}
     (if (:in-flight? refinement)
       (in-flight-view refinement)
       [:div {:class ["space-y-4"]}
        (status-header refinement)
        (when-let [outcome (:outcome refinement)]
          (outcome-view outcome))
        (refine-options refinement)])]))
