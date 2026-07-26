(ns hyperopen.views.portfolio.optimize.setup-use-my-views-workspace
  (:require [hyperopen.views.portfolio.optimize.black-litterman-preview-chart :as black-litterman-preview-chart]
            [hyperopen.views.portfolio.optimize.setup-use-my-views-cards :as use-my-views-cards]))

(defn- use-my-views-legend-item
  [role swatch-class label qualifier]
  [:div {:class ["flex" "items-center" "gap-2.5"] :data-role role}
   [:span {:class (into ["h-2.5" "w-2.5" "shrink-0" "rounded-full" "border" "border-black/10"]
                        swatch-class)
           :aria-hidden "true"}]
   [:div {:class ["flex" "flex-wrap" "items-baseline" "gap-x-1.5" "gap-y-0.5"
                  "text-[0.8125rem]" "leading-[1.4]"]}
    [:span {:class ["font-medium" "text-trading-text"]} label]
    [:span {:class ["text-trading-muted"]} qualifier]]])

(defn views-blend-explainer
  "The Black-Litterman trust explainer (legend + prior/posterior chart + the
  three insight cards). Content only — the policy pane wraps it in a collapsed
  disclosure now that every Maximum Sharpe scenario is views-aware, so it can
  no longer replace the standard pane tail."
  [{:keys [draft readiness]}]
  (let [preview (use-my-views-cards/preview readiness)]
    [:div {:class ["space-y-4"] :data-role "portfolio-optimizer-setup-use-my-views-context"}
     [:div {:class ["optimizer-use-my-views-legend"
                    "grid" "gap-3" "border" "border-base-300" "bg-base-100/90" "p-4"
                    "sm:grid-cols-2" "xl:grid-cols-3"]
            :data-role "portfolio-optimizer-setup-use-my-views-legend"}
      (use-my-views-legend-item
       "portfolio-optimizer-setup-use-my-views-legend-market-reference"
       ["bg-[#6b8db5]"]
       "Implied baseline"
       "(prior)")
      (use-my-views-legend-item
       "portfolio-optimizer-setup-use-my-views-legend-your-view"
       ["bg-transparent" "ring-2" "ring-warning/70" "border-warning/50"]
       "Your view"
       "tilt")
      (use-my-views-legend-item
       "portfolio-optimizer-setup-use-my-views-legend-combined-output"
       ["bg-[#d4b558]"]
       "Combined output"
       "(posterior)")]
     [:div {:class ["optimizer-use-my-views-chart-shell"
                    "border" "border-base-300" "bg-base-200/10" "p-1.5"]
            :data-role "portfolio-optimizer-setup-use-my-views-chart-shell"}
      (black-litterman-preview-chart/black-litterman-preview-panel
       readiness
       {:legend-layout :external
        :preview preview})]
     (use-my-views-cards/cards draft readiness preview)]))
