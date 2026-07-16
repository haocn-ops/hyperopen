(ns hyperopen.views.portfolio.optimize.results-panel-inverse-volatility-test
  "Results-surface coverage for the Risk-weighted sizing (:inverse-volatility)
  objective (ExecPlan optimizer-inverse-volatility-objective, items 9 and 10):
  the sizing-fidelity card replaces the frontier chart and the equal-risk
  rails, the KPI strip goes neutral with no Exact/Approximate quality label,
  and an Equal Risk result with a floored side-locked asset renders the
  one-click switch-to-Risk-weighted-sizing suggestion."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.views.portfolio.optimize.results-panel :as results-panel]
            [hyperopen.views.portfolio.optimize.risk-contributions-card
             :as risk-contributions-card]
            [hyperopen.views.portfolio.optimize.scenario-kpi-strip :as kpi-strip]
            [hyperopen.views.portfolio.optimize.test-support
             :refer [collect-strings node-by-role solved-result]]))

(def ^:private inverse-volatility-result
  (-> solved-result
      (assoc :solver {:strategy :inverse-volatility
                      :objective-kind :inverse-volatility})
      (assoc :inverse-volatility
             {:sizing-rows [{:instrument-id "perp:BTC"
                             :weight 0.35
                             :sigma 0.4
                             :risk-weight 0.14
                             :moved-off-seed? false}
                            {:instrument-id "spot:PURR"
                             :weight -0.2
                             :sigma 0.7
                             :risk-weight 0.14
                             :moved-off-seed? false}]
              :seed-weights [0.35 -0.2]
              :max-sizing-deviation 0.0})))

(defn- render
  [result]
  (results-panel/results-panel
   {:result result
    :computed-at-ms 2600}
   {:objective {:kind :inverse-volatility}}
   {:frontier-overlay-mode :standalone}))

(deftest results-panel-inverse-volatility-shows-sizing-card-and-hides-frontier-test
  (let [view-node (render inverse-volatility-result)
        strings (set (collect-strings view-node))]
    (testing "the sizing-fidelity card is the centerpiece"
      (is (some? (node-by-role view-node
                               "portfolio-optimizer-risk-weighted-sizing-card"))
          "an :inverse-volatility solved payload renders the sizing card"))
    (testing "no frontier machinery renders — this objective is not selected
              from a frontier and must not be plotted as if it were"
      (is (nil? (node-by-role view-node "portfolio-optimizer-frontier-panel")))
      (is (nil? (node-by-role view-node "portfolio-optimizer-refinement-card")))
      (is (nil? (node-by-role view-node
                              "portfolio-optimizer-result-confidence-panel"))))
    (testing "the equal-risk rails stay out — this is not an equal-risk result"
      (is (nil? (node-by-role view-node "portfolio-optimizer-risk-contributions")))
      (is (nil? (node-by-role view-node
                              "portfolio-optimizer-equal-risk-confidence-panel")))
      (is (nil? (node-by-role view-node
                              "portfolio-optimizer-equal-risk-context"))))
    (testing "no Exact/Approximate balance-quality label leaks onto this objective"
      (is (not (contains? strings "Exact")))
      (is (not (contains? strings "Approximate"))))))

(deftest kpi-strip-inverse-volatility-neutral-deltas-and-no-quality-label-test
  (let [strip (kpi-strip/kpi-strip inverse-volatility-result)
        strings (set (collect-strings strip))]
    (testing "vol/return direction is not success or failure for a sizing objective"
      (let [vol-card (node-by-role strip
                                   "portfolio-optimizer-scenario-kpi-volatility")
            delta-node (last vol-card)]
        (is (some #(= "text-trading-muted" %) (get-in delta-node [1 :class]))
            "the volatility delta renders neutrally")))
    (testing "the Sharpe tile is not this objective's success metric"
      (is (nil? (node-by-role strip "portfolio-optimizer-scenario-kpi-sharpe"))))
    (testing "no Exact/Approximate quality label rides the strip"
      (is (not (contains? strings "Exact")))
      (is (not (contains? strings "Approximate"))))))

;; --- Floored-state cross-link (item 10, UI half) --------------------------------

(def ^:private floored-equal-risk-result
  ;; An Equal Risk run that floored a side-locked asset at 0%: spot:PURR holds
  ;; a zero target pinned by a zero bound. The balance card must offer the
  ;; one-click escape hatch to Risk-weighted sizing.
  (-> solved-result
      (assoc :solver {:strategy :sequential-equal-risk
                      :objective-kind :equal-risk})
      (assoc :target-weights [0.35 0])
      (assoc :target-weights-by-instrument {"perp:BTC" 0.35
                                            "spot:PURR" 0})
      (assoc :risk-contributions
             {:method :signed-euler-volatility
              :instrument-ids ["perp:BTC" "spot:PURR"]
              :relative-contributions [1.0 0.0]
              :target-relative-contributions [0.5 0.5]
              :relative-contributions-by-instrument {"perp:BTC" 1.0
                                                     "spot:PURR" 0.0}
              :target-relative-contributions-by-instrument {"perp:BTC" 0.5
                                                            "spot:PURR" 0.5}
              :sum-relative-contributions 1.0
              :rms-error 0.5
              :max-absolute-error 0.5
              :negative-contribution-count 0
              :quality :approximate})
      (assoc-in [:diagnostics :binding-constraints]
                [{:instrument-id "spot:PURR"
                  :constraint :lower-bound
                  :bound 0}])))

(deftest risk-contributions-card-offers-risk-weighted-sizing-for-floored-assets-test
  (let [card (risk-contributions-card/risk-contributions-card
              floored-equal-risk-result)
        suggestion (node-by-role
                    card
                    "portfolio-optimizer-switch-to-risk-weighted-sizing")]
    (is (some? suggestion)
        "a floored side-locked asset surfaces the Risk-weighted sizing suggestion")
    (is (= :actions/switch-portfolio-optimizer-objective-and-run
           (first (first (get-in suggestion [1 :on :click]))))
        "one click switches the objective and reruns")))

(deftest risk-contributions-card-hides-the-suggestion-without-floored-assets-test
  (let [balanced (-> floored-equal-risk-result
                     (assoc :target-weights [0.35 -0.2])
                     (assoc :target-weights-by-instrument {"perp:BTC" 0.35
                                                           "spot:PURR" -0.2})
                     (assoc-in [:diagnostics :binding-constraints] []))
        card (risk-contributions-card/risk-contributions-card balanced)]
    (is (nil? (node-by-role
               card
               "portfolio-optimizer-switch-to-risk-weighted-sizing"))
        "no floored asset, no cross-link nudge")))
