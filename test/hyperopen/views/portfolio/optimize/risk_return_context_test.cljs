(ns hyperopen.views.portfolio.optimize.risk-return-context-test
  "The RISK / RETURN tab panel (designer spec 2026-07-11): a real vol/return
  chart on the frontier chart's chrome — grid, ticks, icon markers — with the
  designer's dashed crosshair + zero line, ring-and-dot current/recommended
  markers, connector, context boxes, and legend; never a frontier curve. The
  panel degrades by dropping the pieces whose data is absent and returns nil
  only when neither portfolio point exists."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.string :as str]
            [hyperopen.views.portfolio.optimize.risk-return-context
             :as risk-return-context]
            [hyperopen.views.portfolio.optimize.test-support
             :refer [collect-strings node-by-role]]))

(def ^:private full-result
  {:volatility 3.0147
   :expected-return 8.4191
   :current-volatility 2.9888
   :current-expected-return 8.5323
   :performance {:in-sample-sharpe 2.79}
   :current-performance {:in-sample-sharpe 2.86}
   :frontier-overlays
   {:standalone [{:instrument-id "perp:BTC"
                  :label "BTC"
                  :volatility 4.42
                  :expected-return 8.05}
                 {:instrument-id "perp:ETH"
                  :label "ETH"
                  :volatility 3.55
                  :expected-return 5.1}
                 {:instrument-id "perp:BROKEN"
                  :label "BROKEN"
                  :volatility js/NaN
                  :expected-return 1.0}]}})

(deftest risk-return-panel-renders-designer-chart-test
  (let [view-node (risk-return-context/risk-return-panel full-result)
        strings (set (collect-strings view-node))]
    (testing "chart chrome: svg, grid, ticks, axis titles"
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-svg")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-grid")))
      (is (some? (node-by-role view-node "portfolio-optimizer-frontier-x-tick-0"))
          "tick labels come from the shared frontier axes helpers")
      (is (contains? strings "Volatility (Annualized)"))
      (is (contains? strings "Expected Return (Annualized)")))
    (testing "never drawn as a frontier"
      (is (nil? (node-by-role view-node "portfolio-optimizer-frontier-path")))
      (is (nil? (node-by-role view-node "portfolio-optimizer-frontier-svg")))
      (is (some #(str/includes? % "did not determine the Equal Risk allocation")
                strings)))
    (testing "designer guides: zero-return line, current-vol crosshair, connector"
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-zero-line")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-crosshair")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-connector"))))
    (testing "portfolio markers with the mock's labels"
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-current")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-target")))
      (is (contains? strings "Current"))
      (is (contains? strings "Recommended (Equal Risk)")))
    (testing "assets render as icon markers with static labels; non-finite points drop"
      (is (some? (node-by-role view-node
                               "portfolio-optimizer-frontier-overlay-standalone-perp:BTC")))
      (is (some? (node-by-role view-node
                               "portfolio-optimizer-risk-return-asset-label-perp:ETH")))
      (is (nil? (node-by-role view-node
                              "portfolio-optimizer-frontier-overlay-standalone-perp:BROKEN"))))
    (testing "context boxes carry the exact portfolio numbers"
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-current-box")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-recommended-box")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-context-box")))
      (is (contains? strings "298.88%"))
      (is (contains? strings "853.23%"))
      (is (contains? strings "301.47%"))
      (is (contains? strings "841.91%")))
    (testing "legend names the assets and both portfolio markers"
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-legend")))
      (is (contains? strings "Current Portfolio"))
      (is (contains? strings "BTC")))
    (testing "the Sharpe footnote survives the redesign"
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-sharpe"))))))

(deftest risk-return-panel-degrades-without-a-current-book-test
  (let [view-node (risk-return-context/risk-return-panel
                   (dissoc full-result :current-volatility :current-expected-return))
        strings (set (collect-strings view-node))]
    (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-svg")))
    (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-target")))
    (testing "no fabricated current pieces"
      (is (nil? (node-by-role view-node "portfolio-optimizer-risk-return-crosshair")))
      (is (nil? (node-by-role view-node "portfolio-optimizer-risk-return-connector")))
      (is (nil? (node-by-role view-node "portfolio-optimizer-risk-return-current")))
      (is (nil? (node-by-role view-node "portfolio-optimizer-risk-return-current-box")))
      (is (not (contains? strings "Current Portfolio"))))))

(deftest risk-return-panel-caps-the-legend-honestly-test
  (let [assets (mapv (fn [index]
                       {:instrument-id (str "perp:A" index)
                        :label (str "A" index)
                        :volatility (+ 0.5 (* 0.1 index))
                        :expected-return (* 0.05 index)})
                     (range 10))
        view-node (risk-return-context/risk-return-panel
                   (assoc full-result :frontier-overlays {:standalone assets}))
        strings (set (collect-strings view-node))]
    (is (contains? strings "+ 2 more assets"))
    (is (some? (node-by-role view-node
                             "portfolio-optimizer-frontier-overlay-standalone-perp:A9"))
        "the CHART still plots every asset — only the legend caps")))

(deftest risk-return-panel-nil-without-any-portfolio-point-test
  (is (nil? (risk-return-context/risk-return-panel
             (dissoc full-result
                     :volatility :expected-return
                     :current-volatility :current-expected-return)))))
