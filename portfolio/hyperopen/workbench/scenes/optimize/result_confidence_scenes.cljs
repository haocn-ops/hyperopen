(ns hyperopen.workbench.scenes.optimize.result-confidence-scenes
  "Workbench scenes for the optimizer result-confidence rail
  (views.portfolio.optimize.results-diagnostics-rail/result-confidence-rail). Seeds an
  assessment so the lead 'From here' next-step row can be eyeballed across the draft /
  refined / maximum-density states, inside the .portfolio-optimizer surface scope. The
  next-step row frames refine as optional and exposes rebalance as the lone clickable
  next step."
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.workbench.support.layout :as layout]
            [hyperopen.views.portfolio.optimize.results-diagnostics-rail :as diagnostics-rail]))

(portfolio/configure-scenes
  {:title "Result confidence rail"
   :collection :optimize})

(defn- rail
  "Render the rail at roughly its real right-panel width inside the optimizer surface
  scope (so the optimizer tokens + class remaps apply)."
  [assessment]
  (layout/page-shell
   (layout/desktop-shell
    [:div {:class ["portfolio-optimizer" "mx-auto"]
           :style {:width "340px"}}
     (diagnostics-rail/result-confidence-rail {:assessment assessment})])))

(portfolio/defscene draft
  []
  (rail {:next-step :refine-optimization
         :frontier-quality :medium
         :selection-stability :moderate
         :exact-selection? true
         :stop-reason :draft-budget-reached}))

(portfolio/defscene refined
  []
  (rail {:next-step :refine-further
         :frontier-quality :high
         :selection-stability :stable
         :exact-selection? true
         :stop-reason :refined-density-reached}))

(portfolio/defscene maximum-density
  []
  (rail {:next-step :none
         :frontier-quality :high
         :selection-stability :stable
         :exact-selection? true
         :stop-reason :maximum-density-reached}))
