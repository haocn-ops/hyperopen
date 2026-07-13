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
             :refer [collect-nodes collect-strings data-role-order index-of
                     node-by-role solved-result]]))

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
      (is (nil? (node-by-role view-node "portfolio-optimizer-result-confidence-panel")))
      (is (nil? (node-by-role view-node "portfolio-optimizer-result-confidence-quality-panel"))
          "the frontier-quality/selection-stability/stop-reason detail is refinement-specific, not equal-risk's own confidence rail")
      (is (some? (node-by-role view-node "portfolio-optimizer-leverage-impact-slot"))
          "the stable under-chart slot remains mounted when Equal Risk does not clear the leverage gate")
      (is (nil? (node-by-role view-node "portfolio-optimizer-leverage-impact"))
          "the unlevered Equal Risk result leaves its stable slot empty"))
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
      (is (some #(str/includes? % "did not determine the Equal Risk allocation") strings)))
    (testing "the risk/return tab is a real chart (designer spec 2026-07-11)"
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-svg")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-zero-line")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-crosshair"))
          "the dashed crosshair drops through the current book's volatility")
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-current")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-legend")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-current-box")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-recommended-box")))
      (is (some? (node-by-role view-node "portfolio-optimizer-risk-return-context-box"))))
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

(deftest results-panel-equal-risk-places-leverage-impact-below-risk-contributions-test
  ;; The existing one-year modeled leverage-impact component belongs in Equal
  ;; Risk's center column too: directly below the balance chart and above the
  ;; Equal Risk explanation, with neither frontier machinery nor refinement.
  (let [levered-result (assoc-in approximate-result [:diagnostics :gross-exposure] 2.0)
        view-node (render levered-result)
        order (data-role-order view-node)
        leverage-slot (node-by-role view-node "portfolio-optimizer-leverage-impact-slot")
        contributions-index (index-of order "portfolio-optimizer-risk-contributions")
        leverage-index (index-of order "portfolio-optimizer-leverage-impact")
        context-index (index-of order "portfolio-optimizer-equal-risk-context")]
    (is (some? leverage-slot)
        "the stable Equal Risk slot remains the leverage component's parent")
    (is (some? (node-by-role leverage-slot "portfolio-optimizer-leverage-impact"))
        "2.0x gross exposure clears the gate and renders the modeled panel inside its slot")
    (is (and (some? contributions-index)
             (some? leverage-index)
             (some? context-index)
             (< contributions-index leverage-index context-index))
        "the center-column reading order is balance chart, leverage impact, then Equal Risk context")
    (is (nil? (node-by-role view-node "portfolio-optimizer-frontier-panel")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-refinement-card")))))

(deftest results-panel-equal-risk-places-quality-below-volatility-intuition-test
  (let [view-node (render approximate-result)
        order (data-role-order view-node)
        quality-panel (node-by-role view-node
                                    "portfolio-optimizer-equal-risk-confidence-quality-panel")
        quality-strings (set (collect-strings quality-panel))]
    (is (every? quality-strings
                ["Equal-risk fit"
                 "Allocation freedom"
                 "Solution stability"
                 "Stop reason"])
        "The Equal Risk diagnostic rows render together in their own quality block.")
    (is (and (< (index-of order "portfolio-optimizer-equal-risk-confidence-panel")
               (index-of order "portfolio-optimizer-volatility-risk-cards"))
             (< (index-of order "portfolio-optimizer-equal-risk-next-step")
               (index-of order "portfolio-optimizer-volatility-risk-cards"))
             (< (index-of order "portfolio-optimizer-volatility-risk-cards")
               (index-of order "portfolio-optimizer-equal-risk-confidence-quality-panel")))
        "The Equal Risk lead stays above volatility intuition; its quality block follows it.")))

(deftest results-panel-equal-risk-clamps-off-scale-current-markers-test
  ;; A current book concentrating ~all volatility in one asset (the usual
  ;; reason to run Equal Risk) must not stretch the chart scale: that current
  ;; renders as an edge chevron (data-offscale) while in-range currents keep
  ;; their circles.
  (let [result (assoc approximate-result
                      :current-risk-contributions
                      {:relative-contributions-by-instrument {"perp:BTC" 3.0
                                                              "spot:PURR" 0.1}
                       :rms-error 1.2
                       :max-absolute-error 2.5})
        view-node (render result)
        currents (collect-nodes
                  view-node
                  #(= "portfolio-optimizer-risk-contribution-current"
                      (get-in % [1 :data-role])))]
    (is (= 2 (count currents)))
    (is (= #{"right" nil}
           (set (map #(get-in % [1 :data-offscale]) currents)))
        "the 300% current pins to the right edge; the in-range one stays a circle")))

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

;; --- Correlation / breakdown views (:risk-structure section) --------------------

(def ^:private structured-result
  ;; approximate-result + the correlation-view section. BTC is held long,
  ;; PURR short (target weights 0.35 / -0.02), underlying correlation 0.6, so
  ;; the position-P&L view must flip their pair negative. The decomposition
  ;; obeys standalone + diversification = net for both assets.
  (assoc approximate-result
         :risk-structure
         {:method :signed-euler-decomposition
          :portfolio-volatility 0.4
          :current-diversification {:modeled-volatility 0.24
                                    :all-move-together-volatility 0.32
                                    :zero-correlation-volatility 0.20
                                    :reduction-vs-all-move-together 0.08
                                    :reduction-ratio-vs-all-move-together 0.25
                                    :modeled-minus-zero-correlation 0.04}
          :target-diversification {:modeled-volatility 0.40
                                   :all-move-together-volatility 0.60
                                   :zero-correlation-volatility 0.35
                                   :reduction-vs-all-move-together 0.20
                                   :reduction-ratio-vs-all-move-together (/ 1 3)
                                   :modeled-minus-zero-correlation 0.05}
          :standalone-share-by-instrument {"perp:BTC" 0.75
                                           "spot:PURR" 0.5}
          :diversification-share-by-instrument {"perp:BTC" -0.13
                                                "spot:PURR" -0.12}
          :pnl-portfolio-correlation-by-instrument {"perp:BTC" 0.9
                                                    "spot:PURR" 0.55}
          :correlation {:instrument-ids ["perp:BTC" "spot:PURR"]
                        :matrix [[1.0 0.6]
                                 [0.6 1.0]]
                        :hidden-count 0}}))

(defn- render-with-state
  [result state]
  (results-panel/results-panel
   {:result result
    :computed-at-ms 2600}
   {:objective {:kind :equal-risk}}
   {:frontier-overlay-mode :standalone
    :state state}))

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
