(ns hyperopen.views.portfolio.optimize.results-panel-equal-risk-test
  "Equal Risk results-panel behavior: the diverging risk-contribution balance
  chart replaces the frontier chart (and the frontier-density refinement
  card), the Risk/Return context disclosure replaces frontier framing, the
  why-card and confidence rail speak in risk contributions, and everything
  degrades gracefully on persisted pre-redesign payloads."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.string :as str]
            [hyperopen.views.portfolio.optimize.results-panel :as results-panel]
            [hyperopen.views.portfolio.optimize.test-support
             :refer [collect-strings node-by-role solved-result]]))

(def ^:private equal-risk-solver-section
  {:strategy :sequential-equal-risk
   :converged? true
   :termination-reason :step-tolerance
   :iterations 7
   :initialization-count 4
   :selected-initialization :inverse-volatility
   :objective-value 1.2e-4
   :exactness-tolerance 0.005
   :allocation-freedom {:status :limited
                        :free-degrees 1
                        :binding-count 2
                        :books {:long 1 :short 1}}
   :initializations [{:seed-kind :equal-notional :status :completed
                      :objective 1.2e-4 :converged? true}
                     {:seed-kind :inverse-volatility :status :completed
                      :objective 1.2e-4 :converged? true}]})

(def ^:private approximate-result
  (-> solved-result
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
      (assoc :current-risk-contributions
             {:relative-contributions-by-instrument {"perp:BTC" 0.9
                                                     "spot:PURR" 0.1}
              :rms-error 0.4
              :max-absolute-error 0.4})
      (assoc :equal-risk-solver equal-risk-solver-section)))

(defn- render
  [result]
  (results-panel/results-panel
   {:result result
    :computed-at-ms 2600}
   {:objective {:kind :equal-risk}}
   {:frontier-overlay-mode :standalone}))

(deftest results-panel-equal-risk-shows-balance-chart-and-hides-frontier-test
  (let [view-node (render approximate-result)
        strings (set (collect-strings view-node))]
    (testing "no frontier machinery renders for equal-risk"
      (is (nil? (node-by-role view-node "portfolio-optimizer-frontier-panel")))
      (is (nil? (node-by-role view-node "portfolio-optimizer-refinement-card")))
      (is (nil? (node-by-role view-node "portfolio-optimizer-result-confidence-panel"))))
    (testing "the diverging balance chart is the centerpiece"
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-contributions")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-contribution-chart")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-contribution-row")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-contribution-bar")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-contribution-current"))
          "current contributions render as muted markers when present")
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-contribution-recommended")))
      (is (contains? strings "Risk contribution balance"))
      (is (contains? strings "Approximate"))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-contributions-quality")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-contributions-rms")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-contributions-max")))
      (is (contains? strings "50.0% per asset"))
      (is (some #(re-find #"Converged · 7 iterations" %) strings)))
    (testing "the designer-spec chrome renders: KPI strip, tabs, columns, reading row"
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-balance-kpis")))
      (is (contains? strings "Status"))
      (is (contains? strings "Negative contributors"))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-view-tabs")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-view-tab-contribution")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-view-tab-risk-return")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-contribution-target")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-contribution-deviation")))
      (is (contains? strings "Reading this"))
      (is (contains? strings "Contribution to Total Volatility (%)")))
    (testing "risk/return context is the card's second tab, never a frontier"
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-context")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-target")))
      (is (some #(str/includes? % "did not affect the Equal Risk allocation") strings)))
    (testing "the why-card speaks in risk contributions"
      (is (some? (node-by-role view-node "portfolio-optimizer-equal-risk-context")))
      (is (nil? (node-by-role view-node "portfolio-optimizer-target-context")))
      (is (contains? strings "Why this risk allocation"))
      (is (contains? strings "Largest risk contributor"))
      (is (contains? strings "Limited · 2 binding caps"))
      (is (some #(str/includes? % "Limited by constraints") strings)
          "the confidence rail keeps the long-form freedom label"))
    (testing "the equal-risk confidence rail replaces frontier language"
      (is (some? (node-by-role view-node "portfolio-optimizer-equal-risk-confidence-panel")))
      (is (contains? strings "Equal-risk fit"))
      (is (contains? strings "Allocation freedom"))
      (is (contains? strings "Solution stability"))
      (is (contains? strings "Projected step tolerance reached")))
    (testing "the trust rail reads contributions, not weight-based effective N"
      (is (contains? strings "Negative contributors"))
      (is (not (contains? strings "Diversification"))))))

(deftest results-panel-equal-risk-distinguishes-exact-quality-and-hedges-test
  (let [exact-result (-> approximate-result
                         (assoc :risk-contributions
                                {:method :signed-euler-volatility
                                 :instrument-ids ["perp:BTC" "spot:PURR"]
                                 :relative-contributions [1.1 -0.1]
                                 :target-relative-contributions [0.5 0.5]
                                 :relative-contributions-by-instrument {"perp:BTC" 1.1
                                                                        "spot:PURR" -0.1}
                                 :target-relative-contributions-by-instrument {"perp:BTC" 0.5
                                                                               "spot:PURR" 0.5}
                                 :sum-relative-contributions 1.0
                                 :rms-error 0.0
                                 :max-absolute-error 0.0
                                 :negative-contribution-count 1
                                 :quality :exact}))
        view-node (render exact-result)
        strings (set (collect-strings view-node))]
    (is (contains? strings "Exact"))
    (is (some #(re-find #"hedges the book" %) strings)
        "negative contributions are named, never absolute-valued away")))

(deftest results-panel-equal-risk-degrades-on-persisted-pre-redesign-results-test
  ;; A scenario saved before this redesign has no current contributions, no
  ;; allocation freedom, and no initializations — the page must still render
  ;; with honest placeholders instead of guessing.
  (let [old-result (-> approximate-result
                       (dissoc :current-risk-contributions)
                       (assoc :equal-risk-solver
                              {:strategy :sequential-equal-risk
                               :converged? true
                               :termination-reason :step-tolerance
                               :iterations 7}))
        view-node (render old-result)
        strings (set (collect-strings view-node))]
    (is (some? (node-by-role view-node "portfolio-optimizer-risk-contributions")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-risk-contribution-current"))
        "no fabricated current markers without current contributions")
    (is (some #(str/includes? % "Not recorded on this result") strings)
        "allocation freedom degrades honestly")
    (is (contains? strings "No initialization record on this result"))))
