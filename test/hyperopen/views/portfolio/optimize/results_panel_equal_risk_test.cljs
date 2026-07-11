(ns hyperopen.views.portfolio.optimize.results-panel-equal-risk-test
  "Equal Risk results-panel behavior: the risk-contribution card replaces the
  frontier chart (and the frontier-density refinement card), and the exact /
  approximate / not-converged quality is rendered truthfully."
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.portfolio.optimize.results-panel :as results-panel]
            [hyperopen.views.portfolio.optimize.test-support
             :refer [collect-strings node-by-role solved-result]]))

(deftest results-panel-equal-risk-shows-contributions-and-hides-frontier-test
  (let [result (-> solved-result
                   (assoc :solver {:strategy :sequential-equal-risk
                                   :objective-kind :equal-risk})
                   (assoc :risk-contributions
                          {:method :signed-euler-volatility
                           :instrument-ids ["perp:BTC" "spot:PURR"]
                           :variance-contributions [0.002 0.002]
                           :volatility-contributions [0.031 0.031]
                           :relative-contributions [0.62 0.38]
                           :target-relative-contributions [0.5 0.5]
                           :relative-contributions-by-instrument {"perp:BTC" 0.62
                                                                  "spot:PURR" 0.38}
                           :target-relative-contributions-by-instrument {"perp:BTC" 0.5
                                                                         "spot:PURR" 0.5}
                           :sum-relative-contributions 1.0
                           :rms-error 0.12
                           :max-absolute-error 0.12
                           :negative-contribution-count 0
                           :quality :approximate})
                   (assoc :equal-risk-solver {:strategy :sequential-equal-risk
                                              :converged? true
                                              :termination-reason :step-tolerance
                                              :iterations 7
                                              :initialization-count 4
                                              :selected-initialization :inverse-volatility
                                              :objective-value 1.2e-4
                                              :exactness-tolerance 0.005}))
        draft {:objective {:kind :equal-risk}}
        view-node (results-panel/results-panel
                   {:result result
                    :computed-at-ms 2600}
                   draft
                   {:frontier-overlay-mode :standalone})
        strings (set (collect-strings view-node))]
    ;; The frontier chart and its refinement card are replaced by the
    ;; risk-contribution balance: Equal Risk has one selected portfolio, not a
    ;; frontier, and nothing may fabricate a curve.
    (is (nil? (node-by-role view-node "portfolio-optimizer-frontier-panel")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-refinement-card")))
    (is (some? (node-by-role view-node "portfolio-optimizer-risk-contributions")))
    (is (some? (node-by-role view-node "portfolio-optimizer-risk-contributions-quality")))
    (is (contains? strings "Approximate"))
    (is (contains? strings "Risk contributions"))
    (is (some #(re-find #"Balance error" %) strings))))

(deftest results-panel-equal-risk-distinguishes-exact-quality-test
  (let [exact-result (-> solved-result
                         (assoc :solver {:strategy :sequential-equal-risk
                                         :objective-kind :equal-risk})
                         (assoc :risk-contributions
                                {:method :signed-euler-volatility
                                 :instrument-ids ["perp:BTC" "spot:PURR"]
                                 :relative-contributions [0.5 0.5]
                                 :target-relative-contributions [0.5 0.5]
                                 :relative-contributions-by-instrument {"perp:BTC" 0.5
                                                                        "spot:PURR" 0.5}
                                 :target-relative-contributions-by-instrument {"perp:BTC" 0.5
                                                                               "spot:PURR" 0.5}
                                 :sum-relative-contributions 1.0
                                 :rms-error 0.0
                                 :max-absolute-error 0.0
                                 :negative-contribution-count 1
                                 :quality :exact}))
        view-node (results-panel/results-panel
                   {:result exact-result
                    :computed-at-ms 2600}
                   {:objective {:kind :equal-risk}}
                   {:frontier-overlay-mode :standalone})
        strings (set (collect-strings view-node))]
    (is (contains? strings "Exact"))
    (is (some #(re-find #"hedges the book" %) strings)
        "negative contributions are named, never absolute-valued away")))
