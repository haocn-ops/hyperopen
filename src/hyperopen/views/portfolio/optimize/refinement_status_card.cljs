(ns hyperopen.views.portfolio.optimize.refinement-status-card
  "Center 'Optimization status / Refinement options' card. Pure view of the refinement
  view-model (application.view-model.refinement). Shows the result-quality tier, lets the
  user pick a refinement depth and re-run at higher frontier density, reports in-flight
  progress, and — after a refinement — summarizes whether the selected portfolio changed."
  (:require [hyperopen.views.portfolio.optimize.format :as opt-format]))

(defn- tier-short-label
  [tier]
  (case tier
    :draft "draft"
    :refined "refined"
    :maximum "maximum"
    :partial "partial"
    "result"))

(defn- quality-tone
  "House rule: status tags tint only when they carry signal. Medium quality is the
  neutral default; High is a positive (green) confirmation, Low a caution (amber)."
  [quality]
  (case quality
    :high :good
    :low :warn
    :neutral))

(defn- stability-tone
  [stability]
  (case stability
    :stable :good
    :provisional :warn
    :neutral))

(defn- tag
  "Square, 1px-bordered mono tag chip. Tints (and shows a leading status pip) only when
  the tone carries signal — amber for caution, green for confirmation; neutral otherwise."
  [data-role tone label value]
  [:span {:class ["optimizer-refinement-tag" "inline-flex" "items-center" "gap-1.5"]
          :data-role data-role
          :data-tone (name tone)}
   (when (not= tone :neutral)
     [:span {:class ["optimizer-refinement-tag-pip"]}])
   [:span {:class ["optimizer-refinement-tag-label"]} label]
   [:span {:class ["optimizer-refinement-tag-value"]} value]])

(defn- status-header
  [{:keys [assessment runtime-ms]}]
  (let [tier (:tier assessment)
        points (:point-count assessment)
        quality (:frontier-quality assessment)
        stability (:selection-stability assessment)]
    [:div {:class ["optimizer-refinement-status"]
           :data-role "portfolio-optimizer-refinement-status"}
     [:div {:class ["optimizer-refinement-head" "flex" "items-center" "justify-between" "gap-3"]
            :data-role "portfolio-optimizer-refinement-head"
            :data-tier (some-> tier name)}
      [:span {:class ["inline-flex" "items-center" "gap-2" "min-w-0"]}
       [:span {:class ["optimizer-refinement-head-pip"]}]
       [:span {:class ["optimizer-refinement-head-label"]} "Optimization status"]]
      [:span {:class ["optimizer-refinement-head-meta" "font-mono"]}
       (str (tier-short-label tier) " · " (or points "—") " pts")]]
     [:p {:class ["optimizer-refinement-title" "mt-3" "text-sm" "font-semibold"
                  "text-trading-text"]}
      (opt-format/refinement-tier-ready-label tier)]
     [:div {:class ["mt-2" "flex" "flex-wrap" "items-center" "gap-1.5"]}
      (tag "portfolio-optimizer-refinement-points"
           :neutral "Points" (str (or points "—")))
      (tag "portfolio-optimizer-refinement-runtime"
           :neutral "Runtime" (opt-format/format-duration runtime-ms))
      (tag "portfolio-optimizer-refinement-quality"
           (quality-tone quality) "Frontier quality"
           (opt-format/refinement-quality-label quality))
      (tag "portfolio-optimizer-refinement-stability"
           (stability-tone stability) "Selection stability"
           (opt-format/refinement-stability-label stability))]]))

(defn- depth-tile
  [{:keys [key points label hint selected?]}]
  [:button {:type "button"
            :class ["optimizer-refinement-depth-tile" "flex" "flex-col" "gap-1" "text-left"]
            :data-role (str "portfolio-optimizer-refinement-depth-" (name key))
            :data-selected (str (boolean selected?))
            :aria-pressed (str (boolean selected?))
            :on {:click [[:actions/set-portfolio-optimizer-refinement-depth key]]}}
   [:span {:class ["flex" "items-center" "gap-2"]}
    [:span {:class ["optimizer-refinement-depth-check"]}]
    [:span {:class ["optimizer-refinement-depth-label" "text-[0.72rem]" "font-semibold"]}
     label]]
   [:span {:class ["optimizer-refinement-depth-hint" "text-[0.6rem]"]} hint]
   [:span {:class ["optimizer-refinement-depth-points" "text-[0.58rem]" "font-mono"]}
    (str points " points")]])

(defn- refine-options
  [{:keys [depth-options can-refine?]}]
  (let [selected (some #(when (:selected? %) %) depth-options)]
    [:div {:class ["optimizer-refinement-options"]
           :data-role "portfolio-optimizer-refinement-options"}
     (into [:div {:class ["optimizer-refinement-depth-grid" "mt-2" "grid" "grid-cols-3"]}]
           (map depth-tile depth-options))
     [:button {:type "button"
               :class ["optimizer-primary-action" "optimizer-refinement-refine"
                       "mt-3" "w-full" "border" "px-3" "py-2" "text-[0.72rem]"
                       "font-semibold" "disabled:cursor-not-allowed"]
               :data-role "portfolio-optimizer-refine-now"
               :disabled (not can-refine?)
               :on (when can-refine?
                     {:click [[:actions/refine-portfolio-optimizer]]})}
      (str "Refine now · " (:label selected) " · " (:points selected) " points")]
     [:p {:class ["mt-2" "text-[0.6rem]" "text-trading-muted/70"]}
      "Your current result stays usable while refinement runs. Selection may change after refinement."]]))

(defn- in-flight-view
  [{:keys [progress depth-options]}]
  (let [percent (max 0 (min 100 (or (:overall-percent progress) 0)))
        active-step (some-> (:active-step progress) name)
        selected (some #(when (:selected? %) %) depth-options)]
    [:div {:class ["optimizer-refinement-running"]
           :data-role "portfolio-optimizer-refinement-running"}
     [:div {:class ["optimizer-refinement-head" "flex" "items-center" "justify-between" "gap-3"]
            :data-tier "running"}
      [:span {:class ["inline-flex" "items-center" "gap-2" "min-w-0"]}
       [:span {:class ["optimizer-refinement-head-pip"]}]
       [:span {:class ["optimizer-refinement-head-label"]} "Refining optimization"]]
      [:span {:class ["optimizer-refinement-head-meta" "font-mono"]}
       (str "→ " (or (:points selected) "—") " pts")]]
     [:p {:class ["mt-2" "text-xs" "text-trading-muted"]}
      "Sweeping a denser frontier. Your current result stays usable until this finishes."]
     [:div {:class ["optimizer-refinement-progress-track" "mt-3" "h-1.5" "w-full"
                    "overflow-hidden"]}
      [:div {:class ["optimizer-refinement-progress-fill" "h-full" "transition-all"]
             :style {:width (str percent "%")}}]]
     [:div {:class ["mt-2" "flex" "items-center" "justify-between" "gap-3"]}
      [:span {:class ["font-mono" "text-[0.62rem]" "text-trading-muted"]}
       (str percent "%" (when active-step (str " · " active-step)))]
      [:button {:type "button"
                :class ["optimizer-refinement-stop" "rounded-md" "border" "border-base-300"
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

(defn- refine-disclosure
  "Progressive disclosure for the refinement depth picker. Refinement is a
  by-exception tool (a solved draft is already usable), so the options stay behind a
  native <details> — matching the trust rail's accordions — instead of holding prime
  center space. Uncontrolled on purpose: no dispatch round-trip, and the card
  re-renders closed again after a refinement completes."
  [{:keys [assessment] :as refinement}]
  (let [exact? (:exact-selection? assessment)
        tier (:tier assessment)]
    [:details {:class ["optimizer-refinement-disclosure"]
               :data-role "portfolio-optimizer-refinement-disclosure"}
     [:summary {:class ["optimizer-refinement-disclosure-summary" "cursor-pointer"]
                :data-role "portfolio-optimizer-refinement-disclosure-toggle"}
      [:span {:class ["optimizer-refinement-eyebrow-label"]} "Refinement options"]
      [:span {:class ["optimizer-refinement-eyebrow-hint"]}
       " · denser sweep = higher confidence, longer solve"]]
     [:p {:class ["mt-2" "text-xs" "text-trading-muted"]}
      (str (if (= :draft tier)
             "Fast draft based on the current frontier sample. Refine for higher confidence"
             "Refined over a denser frontier")
           (if exact?
             " — the selection is exact for this objective, so refinement only sharpens the chart."
             " — the selection is sampled from the frontier and may shift when refined."))]
     (refine-options refinement)]))

(defn refinement-status-card
  [refinement]
  (when (:solved? refinement)
    [:section {:class ["optimizer-refinement-card"
                       "rounded-xl" "border" "border-base-300" "bg-base-100/95" "p-4"
                       "space-y-4"]
               :data-role "portfolio-optimizer-refinement-card"}
     (if (:in-flight? refinement)
       (in-flight-view refinement)
       [:div {:class ["space-y-3"]}
        (status-header refinement)
        (when-let [outcome (:outcome refinement)]
          (outcome-view outcome))
        (refine-disclosure refinement)])]))
