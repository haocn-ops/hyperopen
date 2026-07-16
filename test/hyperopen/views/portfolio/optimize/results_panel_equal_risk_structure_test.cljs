(ns hyperopen.views.portfolio.optimize.results-panel-equal-risk-structure-test
  "Equal Risk results-panel behavior for the :risk-structure section: the
  correlation / breakdown tabs, the per-asset panel + Change-asset select,
  allocation-row selection flow, and the graceful fallback when the section
  is absent. Split from results-panel-equal-risk-test for the namespace-size
  cap; shared fixtures live in results-panel-equal-risk-fixtures."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.string :as str]
            [hyperopen.views.portfolio.optimize.results-panel-equal-risk-fixtures
             :refer [approximate-result render render-with-state
                     structured-result]]
            [hyperopen.views.portfolio.optimize.test-support
             :refer [collect-nodes collect-strings node-by-role]]))

(deftest results-panel-equal-risk-correlation-view-renders-test
  (let [view-node (render structured-result)
        strings (set (collect-strings view-node))]
    (testing "the four stable tab identities get truthful visible labels"
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-view-tab-contribution")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-view-tab-breakdown")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-view-tab-correlation")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-view-tab-risk-return")))
      (is (every? strings ["Risk Balance" "Diversification"
                           "Correlation Drivers" "Risk / Return"])))
    (testing "both heatmap modes pre-render; position P&L flips the long × short pair"
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-corr-mode-position")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-corr-mode-underlying")))
      (let [position-grid (node-by-role view-node
                                        "portfolio-optimizer-risk-corr-grid-position")
            underlying-grid (node-by-role view-node
                                          "portfolio-optimizer-risk-corr-grid-underlying")]
        (is (some #{"-0.60"} (collect-strings position-grid)))
        (is (some #{"0.60"} (collect-strings underlying-grid)))
        (let [off-diagonal (first
                            (collect-nodes
                             position-grid
                             #(and (= "portfolio-optimizer-risk-corr-cell"
                                      (get-in % [1 :data-role]))
                                   (= "spot:PURR" (get-in % [1 :data-col]))
                                   (= "perp:BTC" (get-in % [1 :data-row])))))
              title (get-in off-diagonal [1 :title])]
          (is (str/includes? title "Underlying-return correlation +0.60"))
          (is (str/includes? title "Position-P&L correlation -0.60"))
          (is (str/includes? title "Effect on portfolio risk: Diversifying")))))
    (testing "the per-tab KPI strips carry their View cells"
      (is (contains? strings "Correlation matrix"))
      (is (contains? strings "Diversification details")))
    (testing "the correlation tab is the full-width heatmap alone — the
              breakdown block moved to the BREAKDOWN tab"
      (let [corr-panel (first
                        (collect-nodes
                         view-node
                         #(some #{"optimizer-risk-balance-panel--correlation"}
                                (get-in % [1 :class]))))]
        (is (some? (node-by-role corr-panel
                                 "portfolio-optimizer-risk-correlation-heatmap")))
        (is (nil? (node-by-role corr-panel
                                "portfolio-optimizer-risk-selected-breakdown")))))
    (testing "the selected-asset breakdown defaults to the largest |net| and
              tells the own + cross-covariance = net story"
      (let [selected (node-by-role view-node
                                   "portfolio-optimizer-risk-selected-asset")]
        (is (= "true"
               (-> (collect-nodes view-node
                                  #(= "portfolio-optimizer-target-exposure-asset-BTC"
                                      (get-in % [1 :data-role])))
                   first
                   (get-in [1 :data-selected])))
            "the allocation highlight agrees with the defaulted selection")
        (is (some #{"BTC"} (collect-strings selected))
            "the panel title names the asset; the side rides the badge"))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-selected-standalone")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-selected-diversification")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-selected-net")))
      (let [identity-node (node-by-role view-node
                                        "portfolio-optimizer-risk-selected-identity")
            identity-strings (set (collect-strings identity-node))]
        (is (contains? identity-strings "Net risk contribution"))
        (is (contains? identity-strings "Own-variance term"))
        (is (contains? identity-strings "Cross-covariance effect"))
        (is (contains? identity-strings "75.0%")
            "the equation carries the actual standalone number")
        (is (contains? identity-strings "(-13.0%)")
            "the diversification term keeps its sign in parentheses")))
    (testing "the all-assets chart survives as the second sub-view, with a
              legend that names both diversification directions"
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-breakdown-chart")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-breakdown-row")))
      (is (contains? strings "Own-variance term"))
      (is (contains? strings "Offsets risk"))
      (is (contains? strings "Amplifies risk")))
    (testing "the why-card's third card becomes the Correlation view tab link"
      (is (contains? strings "Correlation view"))
      (is (not (contains? strings "Largest risk contributor")))
      (let [card (node-by-role view-node
                               "portfolio-optimizer-equal-risk-context-correlation")]
        (is (= :label (first card)))
        (is (re-find #"-correlation$" (get-in card [1 :for])))))))

(deftest results-panel-equal-risk-per-asset-breakdown-panel-test
  (let [view-node (render structured-result)
        strings (set (collect-strings view-node))]
    (testing "the sub-view toggle renders with Selected asset first (the
              unchecked-default view) and All assets second"
      (let [tabs (node-by-role view-node
                               "portfolio-optimizer-risk-breakdown-view-tabs")]
        (is (some? (node-by-role tabs
                                 "portfolio-optimizer-risk-breakdown-view-asset")))
        (is (some? (node-by-role tabs
                                 "portfolio-optimizer-risk-breakdown-view-all")))
        (is (= ["Selected asset" "All assets"]
               (filterv #{"Selected asset" "All assets"}
                        (collect-strings tabs))))))
    (testing "the Change-asset select lists every held asset, tracks the
              effective selection, and dispatches the shared action"
      (let [select (node-by-role view-node
                                 "portfolio-optimizer-risk-asset-select")
            selected-options (collect-nodes
                              select
                              #(true? (get-in % [1 :selected])))]
        (is (= ["perp:BTC"]
               (mapv #(get-in % [1 :value]) selected-options))
            "exactly the rendered (defaulted) selection is marked :selected —
             selection rides the options, never a select-level :value (set
             before options mount, it falls back to the first option)")
        (is (= [[:actions/set-portfolio-optimizer-selected-risk-instrument
                 [:event.target/value]]]
               (get-in select [1 :on :change])))
        (is (= #{"BTC" "PURR"} (set (collect-strings select))))))
    (testing "the per-asset block carries the labeled component rows and the
              designer axis title"
      (is (some? (node-by-role view-node
                               "portfolio-optimizer-risk-selected-breakdown")))
      (is (contains? strings "Own-variance term"))
      (is (contains? strings "Cross-covariance effect"))
      (is (contains? strings "Net risk contribution"))
      (is (contains? strings "Contribution to Total Portfolio Volatility")))
    (testing "the four summary tiles render with honest copy"
      (is (some? (node-by-role view-node
                               "portfolio-optimizer-risk-asset-tile-summary")))
      (is (some? (node-by-role view-node
                               "portfolio-optimizer-risk-asset-tile-diversification")))
      (is (some? (node-by-role view-node
                               "portfolio-optimizer-risk-asset-tile-net")))
      (is (some? (node-by-role view-node
                               "portfolio-optimizer-risk-asset-tile-freedom")))
      (is (contains? strings "RMS deviation 12.0 pts"))
      (is (contains? strings "-13.0 pts offsets"))
      (is (contains? strings "62.0% of total risk"))
      (is (contains? strings "+12.0 pts vs 50.0% target"))
      (is (contains? strings "Limited · 2 binding caps"))
      (is (contains? strings "Caps constrain exact equality")))))

(deftest results-panel-equal-risk-allocation-selection-test
  (let [view-node (render structured-result)]
    (testing "held rows show the P&L-correlation line and dispatch selection"
      (let [btc-row (first (collect-nodes
                            view-node
                            #(= "portfolio-optimizer-target-exposure-asset-BTC"
                                (get-in % [1 :data-role]))))]
        (is (= [[:actions/set-portfolio-optimizer-selected-risk-instrument
                 "perp:BTC"]]
               (get-in btc-row [1 :on :click])))
        (is (some? (node-by-role view-node
                                 "portfolio-optimizer-target-exposure-pnl-corr-perp-BTC")))
        (is (some #{"+0.90"}
                  (collect-strings
                   (node-by-role view-node
                                 "portfolio-optimizer-target-exposure-pnl-corr-perp-BTC"))))))
    (testing "explicit app-state selection re-targets the breakdown and the ring"
      (let [state (assoc-in {}
                            [:portfolio-ui :optimizer :selected-risk-instrument]
                            "spot:PURR")
            selected-view (render-with-state structured-result state)
            selected (node-by-role selected-view
                                   "portfolio-optimizer-risk-selected-asset")]
        (is (some #{"PURR"} (collect-strings selected)))
        (is (= "true"
               (-> (collect-nodes selected-view
                                  #(= "portfolio-optimizer-target-exposure-asset-PURR"
                                      (get-in % [1 :data-role])))
                   first
                   (get-in [1 :data-selected]))))))))

(deftest results-panel-equal-risk-hides-structure-tabs-without-the-section-test
  (let [view-node (render approximate-result)]
    (is (nil? (node-by-role view-node "portfolio-optimizer-risk-view-tab-breakdown")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-risk-view-tab-correlation")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-risk-correlation-heatmap")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-risk-asset-select"))
        "no per-asset panel without the structure section")
    (is (nil? (node-by-role view-node
                            "portfolio-optimizer-equal-risk-context-correlation"))
        "the why-card falls back to the largest-contributor fact")))
