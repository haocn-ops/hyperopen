(ns hyperopen.views.portfolio.optimize.results-panel-hierarchy-test
  "Review-and-act hierarchy contracts for the results grid (2026-07-02 redesign):
  the rail reads confidence → trust → collapsed views editor, and the center column
  carries the engine-derived 'Why this target' card between the frontier chart and
  the demoted refinement card. Split from results-panel-test to stay under the
  namespace-size ceiling."
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.portfolio.optimize.results-panel :as results-panel]
            [hyperopen.views.portfolio.optimize.test-support
             :refer [click-actions collect-strings data-role-order index-of node-attr
                     node-by-role solved-result]]))

(def ^:private rail-refinement
  {:solved? true
   :can-refine? true
   :in-flight? false
   :depth :quick
   :depth-options [{:key :quick :points 56 :label "Quick" :hint "Light" :selected? true}
                   {:key :thorough :points 72 :label "Thorough" :hint "Balanced" :selected? false}
                   {:key :maximum :points 80 :label "Maximum" :hint "Densest" :selected? false}]
   :assessment {:tier :draft
                :point-count 40
                :frontier-quality :medium
                :selection-stability :provisional
                :exact-selection? false
                :next-step :refine-optimization
                :stop-reason :draft-budget-reached}
   :runtime-ms 7350
   :progress {:overall-percent nil :active-step nil}})

(deftest results-panel-rail-reads-review-first-with-collapsed-views-editor-test
  ;; The rail is review-first: confidence, then trust, then the views editor —
  ;; and the editor is a closed-by-default <details> whose summary carries the
  ;; title + live counts, so input editing no longer dominates the rail.
  (let [draft {:universe [{:instrument-id "perp:BTC"
                           :market-type :perp
                           :coin "BTC"}
                          {:instrument-id "perp:ETH"
                           :market-type :perp
                           :coin "ETH"}]
               :objective {:kind :max-sharpe}
               :return-model {:kind :black-litterman
                              :views [{:id "bl_view_1"
                                       :kind :absolute
                                       :instrument-id "perp:BTC"
                                       :return 0.18
                                       :confidence-level :medium
                                       :confidence 0.5
                                       :weights {"perp:BTC" 1}}]}}
        view-node (results-panel/results-panel
                   {:result solved-result
                    :computed-at-ms 2600}
                   draft
                   {:frontier-overlay-mode :standalone
                    :refinement rail-refinement})
        order (data-role-order view-node)
        editor (node-by-role view-node "portfolio-optimizer-results-your-views-editor")
        toggle (node-by-role view-node
                             "portfolio-optimizer-results-your-views-editor-toggle")
        toggle-strings (set (collect-strings toggle))]
    (is (< (index-of order "portfolio-optimizer-result-confidence-panel")
           (index-of order "portfolio-optimizer-trust-caution-panel")))
    (is (< (index-of order "portfolio-optimizer-trust-caution-panel")
           (index-of order "portfolio-optimizer-results-your-views-editor")))
    (is (= :details (first editor)))
    (is (nil? (node-attr editor :open))
        "The views editor is collapsed by default on the results page.")
    (is (= :summary (first toggle)))
    (is (contains? toggle-strings "Return views"))
    (is (contains? toggle-strings "1 your view · 1 implied"))
    ;; Everything the editor could do stays available once opened.
    (is (some? (node-by-role editor
                             "portfolio-optimizer-objective-menu-view-row-perp:BTC")))
    (is (= [[:actions/apply-portfolio-optimizer-objective-menu-selection-and-run]]
           (click-actions
            (node-by-role editor "portfolio-optimizer-results-your-views-apply"))))))

(deftest results-panel-renders-target-context-card-between-chart-and-refinement-test
  ;; "Why this target" surfaces engine-derived facts (book shape, largest position,
  ;; binding constraints) in the center column, between the frontier chart and the
  ;; demoted refinement card.
  (let [result (assoc solved-result
                      :labels-by-instrument {"perp:BTC" "BTC"
                                             "spot:PURR" "PURR"})
        view-node (results-panel/results-panel
                   {:result result
                    :computed-at-ms 2600}
                   {:objective {:kind :max-sharpe}}
                   {:frontier-overlay-mode :standalone
                    :refinement rail-refinement})
        order (data-role-order view-node)
        context (node-by-role view-node "portfolio-optimizer-target-context")
        strings (set (collect-strings context))
        binding-row (node-by-role context
                                  "portfolio-optimizer-target-context-binding-perp:BTC")]
    (is (some? context))
    (is (< (index-of order "portfolio-optimizer-frontier-panel")
           (index-of order "portfolio-optimizer-target-context")))
    (is (< (index-of order "portfolio-optimizer-target-context")
           (index-of order "portfolio-optimizer-refinement-card")))
    (is (contains? strings "Why this target"))
    (is (contains? strings "1 long · 1 short of 2 assets"))
    (is (contains? strings "BTC · 35.00%"))
    (is (some? binding-row))
    (is (contains? (set (collect-strings binding-row)) "at cap"))))
