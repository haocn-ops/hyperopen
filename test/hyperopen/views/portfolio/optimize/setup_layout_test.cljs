(ns hyperopen.views.portfolio.optimize.setup-layout-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.views.portfolio-view :as portfolio-view]
            [hyperopen.views.portfolio.optimize.setup-layout-fixtures :refer [node-children find-first-node collect-strings node-by-role child-roles node-text click-actions input-actions change-actions keydown-actions day-start-ms summary-from-points class-token-set count-nodes btc-instrument eth-instrument black-litterman-ready-readiness black-litterman-ready-draft black-litterman-empty-readiness black-litterman-empty-draft candle-rows]]))

(deftest setup-new-route-uses-canonical-grid-instead-of-old-left-rail-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/new"}
                    :wallet {:address "0x1111111111111111111111111111111111111111"}})
        strings (set (collect-strings view-node))]
    (is (some? (node-by-role view-node "portfolio-optimizer-setup-route-surface")))
    (is (some? (node-by-role view-node "portfolio-optimizer-setup-header")))
    (is (some? (node-by-role view-node "portfolio-optimizer-setup-preset-row")))
    (is (some? (node-by-role view-node "portfolio-optimizer-setup-surface")))
    (is (some? (node-by-role view-node "portfolio-optimizer-setup-control-rail")))
    (is (some? (node-by-role view-node "portfolio-optimizer-setup-summary-pane")))
    (is (some? (node-by-role view-node "portfolio-optimizer-assumptions-rail")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-trust-freshness-panel")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-left-rail")))
    (is (contains? strings "Portfolio Optimizer"))
    (is (contains? strings "Start with"))
    (is (contains? strings "Load my holdings"))
    (is (contains? strings "Custom"))
    (is (not (contains? strings "Index")))
    (is (contains? strings "What this scenario will solve for"))
    (is (contains? strings "Why this preset is safe"))
    (is (not (contains? strings "Execution Assumptions")))))

(deftest setup-universe-source-toggle-has-two-equal-options-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/new"}
                    :wallet {:address "0x1111111111111111111111111111111111111111"}})
        universe-panel (node-by-role view-node "portfolio-optimizer-universe-panel")
        source-toggle (find-first-node
                       universe-panel
                       #(and (vector? %)
                             (contains? (class-token-set %) "grid-cols-2")
                             (= ["portfolio-optimizer-universe-use-current"]
                                (child-roles %))
                             (str/starts-with? (node-text %) "Custom")))
        strings (set (collect-strings source-toggle))]
    (is (some? source-toggle))
    (is (contains? strings "Load my holdings"))
    (is (contains? strings "Custom"))
    (is (not (contains? strings "Index")))))

(deftest setup-universe-load-holdings-is-single-affordance-test
  ;; "Load my holdings" lives in ONE place — the universe source strip button
  ;; (portfolio-optimizer-universe-use-current), relabelled from "From holdings".
  ;; The old duplicate prominent empty-state button (…-universe-load-holdings) was
  ;; removed; the empty state points at the strip button instead.
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/new"}
                    :wallet {:address "0x1111111111111111111111111111111111111111"}})
        load-strip (node-by-role view-node "portfolio-optimizer-universe-use-current")]
    (is (some? load-strip))
    (is (= [[:actions/set-portfolio-optimizer-universe-from-current]]
           (click-actions load-strip)))
    (is (str/includes? (node-text load-strip) "Load my holdings"))
    ;; the duplicate empty-state button is gone
    (is (nil? (node-by-role view-node "portfolio-optimizer-universe-load-holdings")))))

(deftest setup-control-rail-orders-objective-before-return-risk-model-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/new"}
                    :portfolio {:optimizer
                                {:draft {:universe [{:instrument-id "perp:BTC"
                                                     :market-type :perp
                                                     :coin "BTC"}]
                                         :objective {:kind :minimum-variance}
                                         :return-model {:kind :historical-mean}
                                         :risk-model {:kind :diagonal-shrink}
                                         :constraints {:long-only? true}}}}})
        control-rail (node-by-role view-node "portfolio-optimizer-setup-control-rail")]
    (is (= ["portfolio-optimizer-universe-panel"
            "portfolio-optimizer-objective-panel"
            "portfolio-optimizer-return-risk-panel"
            "portfolio-optimizer-constraints-panel"
            "portfolio-optimizer-advanced-overrides-shell"]
           (child-roles control-rail)))
    ;; The leading section numbers (01..05) are gone: the panels read as a
    ;; sovereign workbench, not a required wizard sequence. Titles remain.
    (is (str/includes? (node-text (node-by-role view-node "portfolio-optimizer-objective-panel"))
                       "Objective"))
    (is (not (str/includes? (node-text (node-by-role view-node "portfolio-optimizer-objective-panel"))
                            "02Objective")))
    (is (str/includes? (node-text (node-by-role view-node "portfolio-optimizer-return-risk-panel"))
                       "Return / Risk Model"))
    (is (not (str/includes? (node-text (node-by-role view-node "portfolio-optimizer-return-risk-panel"))
                            "03Return / Risk Model")))))

(deftest setup-return-risk-and-constraints-panels-are-collapsed-disclosures-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/new"}
                    :portfolio {:optimizer
                                {:draft {:universe [{:instrument-id "perp:BTC"
                                                     :market-type :perp
                                                     :coin "BTC"}]
                                         :objective {:kind :minimum-variance}
                                         :return-model {:kind :historical-mean}
                                         :risk-model {:kind :diagonal-shrink}
                                         :constraints {:long-only? true
                                                       :max-asset-weight 0.25
                                                       :gross-max 2}}}}})
        model-panel (node-by-role view-node "portfolio-optimizer-return-risk-panel")
        constraints-panel (node-by-role view-node "portfolio-optimizer-constraints-panel")
        advanced-panel (node-by-role view-node "portfolio-optimizer-advanced-overrides-shell")]
    (doseq [panel [model-panel constraints-panel advanced-panel]]
      (is (= :details (first panel)))
      (is (not (contains? (second panel) :open)))
      (is (= :summary (first (first (node-children panel))))))
    (is (some? (node-by-role model-panel "portfolio-optimizer-return-model-panel")))
    (is (some? (node-by-role model-panel "portfolio-optimizer-risk-model-panel")))
    (is (some? (node-by-role constraints-panel
                              "portfolio-optimizer-constraint-gross-max-input")))))

(deftest setup-layout-preserves-optimizer-control-actions-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/new"}
                    :portfolio {:optimizer
                                {:draft {:universe [{:instrument-id "perp:BTC"
                                                     :market-type :perp
                                                     :coin "BTC"}]
                                         :objective {:kind :minimum-variance}
                                         :return-model {:kind :historical-mean}
                                         :risk-model {:kind :diagonal-shrink}
                                         :constraints {:long-only? true
                                                       :max-asset-weight 0.25
                                                       :gross-max 2}}}}})
        run-button (node-by-role view-node "portfolio-optimizer-run-draft")]
    (is (= false (get-in run-button [1 :disabled])))
    (is (= [[:actions/run-portfolio-optimizer-from-draft]]
           (click-actions run-button)))
    (is (= [[:actions/set-portfolio-optimizer-universe-from-current]]
           (click-actions
            (node-by-role view-node
                          "portfolio-optimizer-universe-use-current"))))
    (is (= [[:actions/set-portfolio-optimizer-universe-search-query
             [:event.target/value]]]
           (input-actions
            (node-by-role view-node
                          "portfolio-optimizer-universe-search-input"))))
    (is (= [[:actions/set-portfolio-optimizer-objective-kind :max-sharpe]]
           (click-actions
            (node-by-role view-node
                          "portfolio-optimizer-objective-max-sharpe"))))
    (is (= [[:actions/set-portfolio-optimizer-return-model-kind :black-litterman]]
           (click-actions
            (node-by-role view-node
                          "portfolio-optimizer-return-model-black-litterman"))))
    (is (= [[:actions/set-portfolio-optimizer-risk-model-kind :sample-covariance]]
           (click-actions
            (node-by-role view-node
                          "portfolio-optimizer-risk-model-sample-covariance"))))
    (is (= [[:actions/set-portfolio-optimizer-risk-model-kind :mixed-frequency]]
           (click-actions
            (node-by-role view-node
                          "portfolio-optimizer-risk-model-mixed-frequency"))))
    (is (= [[:actions/set-portfolio-optimizer-constraint
             :gross-max
             [:event.target/value]]]
           (change-actions
            (node-by-role view-node
                          "portfolio-optimizer-constraint-gross-max-input"))))))

(deftest setup-return-risk-model-buttons-expose-tooltips-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/new"}
                    :portfolio {:optimizer
                                {:draft {:universe [{:instrument-id "perp:BTC"
                                                     :market-type :perp
                                                     :coin "BTC"}]
                                         :objective {:kind :minimum-variance}
                                         :return-model {:kind :historical-mean}
                                         :risk-model {:kind :diagonal-shrink}
                                         :constraints {:long-only? true}}}}})
        expectations [["portfolio-optimizer-return-model-historical-mean"
                       "Uses the arithmetic mean of historical returns for each selected asset."]
                      ["portfolio-optimizer-return-model-ew-mean"
                       "Uses exponentially weighted historical returns so recent observations count more."]
                      ["portfolio-optimizer-return-model-black-litterman"
                       "Combines market-implied returns with your Black-Litterman views and confidence inputs."]
                      ["portfolio-optimizer-risk-model-diagonal-shrink"
                       "Shrinks the covariance estimate toward a diagonal model to reduce noisy cross-asset correlations."]
                      ["portfolio-optimizer-risk-model-ledoit-wolf-dense"
                       "Estimates the optimal shrinkage intensity from your data, raising it automatically as assets grow relative to available history."]
                      ["portfolio-optimizer-risk-model-mixed-frequency"
                       "Keeps dense assets on daily history while aggregating them over sparse asset intervals when needed."]
                      ["portfolio-optimizer-risk-model-sample-covariance"
                       "Uses the raw historical covariance matrix from the selected asset return history."]]]
    (doseq [[role copy] expectations]
      (let [button (node-by-role view-node role)
            tooltip-id (str role "-tooltip")
            tooltip (node-by-role view-node tooltip-id)]
        (is (= tooltip-id (get-in button [1 :aria-describedby])))
        (is (contains? (class-token-set button)
                       "focus:shadow-[inset_0_0_0_1px_rgba(212,181,88,0.75)]"))
        (is (= "tooltip" (get-in tooltip [1 :role])))
        (is (= copy (node-text tooltip)))))))

(deftest setup-summary-renders-vault-names-instead-of-addresses-test
  (let [vault-address "0x2222222222222222222222222222222222222222"
        vault-id (str "vault:" vault-address)
        view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/new"}
                    :portfolio {:optimizer
                                {:draft {:universe [{:instrument-id "perp:BTC"
                                                     :market-type :perp
                                                     :coin "BTC"}
                                                    {:instrument-id vault-id
                                                     :market-type :vault
                                                     :coin vault-id
                                                     :vault-address vault-address
                                                     :name "Alpha Yield"}]
                                         :objective {:kind :minimum-variance}
                                         :return-model {:kind :historical-mean}
                                         :risk-model {:kind :diagonal-shrink}
                                         :constraints {:long-only? true
                                                       :max-asset-weight 0.25
                                                       :gross-max 1}}}}})
        summary-panel (node-by-role view-node "portfolio-optimizer-setup-summary-panel")
        summary-text (node-text summary-panel)]
    (is (str/includes? summary-text "2 assets - BTC, Alpha Yield"))
    (is (not (str/includes? summary-text vault-id)))))

(deftest setup-run-action-renders-under-center-assumptions-panel-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/new"}
                    :portfolio {:optimizer
                                {:draft {:universe [{:instrument-id "perp:BTC"
                                                     :market-type :perp
                                                     :coin "BTC"}]
                                         :objective {:kind :minimum-variance}
                                         :return-model {:kind :historical-mean}
                                         :risk-model {:kind :diagonal-shrink}
                                         :constraints {:long-only? true
                                                       :max-asset-weight 0.25
                                                       :gross-max 2}}}}})
        route-surface (node-by-role view-node "portfolio-optimizer-setup-route-surface")
        header (node-by-role view-node "portfolio-optimizer-setup-header")
        summary-pane (node-by-role view-node "portfolio-optimizer-setup-summary-pane")
        assumptions-stack (node-by-role view-node "portfolio-optimizer-model-assumptions-stack")
        assumptions-panel (node-by-role view-node "portfolio-optimizer-model-assumptions-panel")
        action-bar (node-by-role view-node "portfolio-optimizer-setup-bottom-actions")
        run-button (node-by-role action-bar "portfolio-optimizer-run-draft")
        save-button (node-by-role action-bar "portfolio-optimizer-save-scenario")
        action-bar-children (vec (node-children action-bar))
        run-index (.indexOf action-bar-children run-button)
        save-index (.indexOf action-bar-children save-button)
        assumptions-stack-children (vec (node-children assumptions-stack))
        assumptions-index (.indexOf assumptions-stack-children assumptions-panel)
        action-bar-index (.indexOf assumptions-stack-children action-bar)
        route-child-action-index (.indexOf (vec (node-children route-surface)) action-bar)]
    (is (some? summary-pane))
    (is (some? assumptions-stack))
    (is (some? action-bar))
    (is (< assumptions-index action-bar-index))
    (is (= -1 route-child-action-index))
    (is (= 0 run-index))
    (is (= 1 save-index))
    (is (= 0 (count-nodes header #(= "portfolio-optimizer-run-draft"
                                     (get-in % [1 :data-role])))))
    (is (= 1 (count-nodes view-node #(= "portfolio-optimizer-run-draft"
                                        (get-in % [1 :data-role])))))
    (is (= false (get-in run-button [1 :disabled])))
    (is (= [[:actions/run-portfolio-optimizer-from-draft]]
           (click-actions run-button)))
    (is (= [[:actions/open-portfolio-optimizer-scenario-save-modal]]
           (click-actions save-button)))))

(deftest setup-constraints-explain-each-control-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/new"}
                    :portfolio {:optimizer
                                {:draft {:constraints {:long-only? false
                                                       :max-asset-weight 0.25
                                                       :gross-max 3
                                                       :net-max 1.5
                                                       :dust-usdc 50
                                                       :max-turnover 1
                                                       :rebalance-tolerance 0.03}}}}})
        strings (set (collect-strings view-node))
        max-weight (node-by-role
                    view-node
                    "portfolio-optimizer-constraint-max-asset-weight-input")
        max-weight-tooltip (node-by-role
                            view-node
                            "portfolio-optimizer-constraint-max-asset-weight-input-tooltip")
        long-only (node-by-role
                   view-node
                   "portfolio-optimizer-constraint-long-only-input")
        long-only-tooltip (node-by-role
                           view-node
                           "portfolio-optimizer-constraint-long-only-tooltip")]
    (is (= "portfolio-optimizer-constraint-max-asset-weight-input-tooltip"
           (get-in max-weight [1 :aria-describedby])))
    (is (= "tooltip" (get-in max-weight-tooltip [1 :role])))
    (is (= "portfolio-optimizer-constraint-long-only-tooltip"
           (get-in long-only [1 :aria-describedby])))
    (is (= :button (first long-only)))
    (is (= "switch" (get-in long-only [1 :role])))
    (is (= "false" (get-in long-only [1 :aria-checked])))
    (is (contains? (class-token-set long-only) "hx-toggle"))
    (is (= [[:actions/set-portfolio-optimizer-constraint
             :long-only?
             true]]
           (click-actions long-only)))
    (is (= "tooltip" (get-in long-only-tooltip [1 :role])))
    (is (contains? strings
                   "Maximum target portfolio weight any single asset can receive. 0.5 means no asset can exceed 50%."))
    (is (contains? strings
                   "Maximum total absolute exposure across all legs. 1 means long exposure plus short exposure can total up to 100% of capital."))
    (is (contains? strings
                   "Small rebalance trades below this USDC notional are ignored so the output avoids noisy dust orders."))
    (is (contains? strings
                   "Minimum target-vs-current weight difference before a rebalance row is considered actionable. 0.03 means 3 percentage points."))
    ;; The panel leads with the 2D exposure-map Positioning control; its trailing eyebrow now
    ;; reflects the active positioning preset (here :custom) instead of the old "defaults
    ;; applied", and the controls are grouped by what the trader decides.
    (is (contains? strings "Custom"))
    (is (contains? strings "Positioning"))
    (is (contains? strings "Risk guards"))
    (is (contains? strings "Rebalance behavior"))
    (is (contains? strings "Sent to solver"))
    (is (contains? strings "Advanced solver constraints"))
    ;; Each numeric constraint echoes its interpreted unit persistently (not only in the
    ;; hover tooltip): 0.25 -> "max 25% per asset", 3 -> "3.00× capital", 0.03 -> "3.0 pp band".
    (is (contains? strings "max 25% per asset"))
    (is (contains? strings "3.00× capital"))
    (is (contains? strings "3.0 pp band"))
    (is (not (contains? strings "mandatory")))))
