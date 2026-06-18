(ns hyperopen.workbench.scenes.optimize.refinement-scenes
  "Workbench scenes for the optimizer 'Optimization status / Refinement options' card
  (views.portfolio.optimize.refinement-status-card). Renders the pure card against
  seeded refinement view-models so the draft / refined / in-flight states can be eyeballed
  in isolation, inside the .portfolio-optimizer surface scope."
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.workbench.support.layout :as layout]
            [hyperopen.views.portfolio.optimize.refinement-status-card :as refinement-card]))

(portfolio/configure-scenes
  {:title "Refinement status card"
   :collection :optimize})

(defn- depth-options
  [selected]
  [{:key :quick :points 56 :label "Quick" :hint "Light pass · ~a few seconds"
    :selected? (= selected :quick)}
   {:key :thorough :points 72 :label "Thorough" :hint "Balanced · ~10–20s est."
    :selected? (= selected :thorough)}
   {:key :maximum :points 80 :label "Maximum" :hint "Densest · ~20–40s est."
    :selected? (= selected :maximum)}])

(defn- shell
  "Wrap the card in the .portfolio-optimizer surface scope (so the optimizer tokens +
  class remaps apply) at roughly its real results-center-panel width."
  [content]
  (layout/page-shell
   (layout/desktop-shell
    [:div {:class ["portfolio-optimizer" "mx-auto" "w-full" "p-6"]
           :style {:max-width "720px"}}
     content])))

(portfolio/defscene draft
  []
  (shell
   (refinement-card/refinement-status-card
    {:solved? true
     :in-flight? false
     :can-refine? true
     :depth :thorough
     :depth-options (depth-options :thorough)
     :assessment {:tier :draft
                  :point-count 24
                  :frontier-quality :medium
                  :selection-stability :provisional
                  :exact-selection? false}
     :runtime-ms 3200
     :outcome nil})))

(portfolio/defscene draft-quick-selected
  []
  (shell
   (refinement-card/refinement-status-card
    {:solved? true
     :in-flight? false
     :can-refine? true
     :depth :quick
     :depth-options (depth-options :quick)
     :assessment {:tier :draft
                  :point-count 24
                  :frontier-quality :medium
                  :selection-stability :provisional
                  :exact-selection? false}
     :runtime-ms 3200
     :outcome nil})))

(portfolio/defscene refined
  []
  (shell
   (refinement-card/refinement-status-card
    {:solved? true
     :in-flight? false
     :can-refine? true
     :depth :thorough
     :depth-options (depth-options :thorough)
     :assessment {:tier :refined
                  :point-count 72
                  :frontier-quality :high
                  :selection-stability :stable
                  :exact-selection? false}
     :runtime-ms 14200
     :outcome {:change {:sharpe-delta 0.08
                        :return-delta 0.0042
                        :vol-delta -0.006
                        :weight-l1-delta 0.124
                        :material? false}
               :exact-selection? false
               :material? false}})))

(portfolio/defscene running
  []
  (shell
   (refinement-card/refinement-status-card
    {:solved? true
     :in-flight? true
     :can-refine? false
     :depth :thorough
     :depth-options (depth-options :thorough)
     :assessment {:tier :draft
                  :point-count 24
                  :frontier-quality :medium
                  :selection-stability :provisional
                  :exact-selection? false}
     :runtime-ms 3200
     :progress {:overall-percent 42
                :active-step :solving-frontier}})))
