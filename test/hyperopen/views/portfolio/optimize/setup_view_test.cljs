(ns hyperopen.views.portfolio.optimize.setup-view-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.portfolio-view :as portfolio-view]
            [hyperopen.views.portfolio.optimize.test-support
             :refer [click-actions collect-strings input-actions node-by-role]]))

(defn- portfolio-optimizer-setup-view
  [objective]
  (portfolio-view/portfolio-view
   {:router {:path "/portfolio/optimize/new"}
    :portfolio {:optimizer {:draft {:objective objective}}}}))

(deftest portfolio-view-delegates-optimizer-new-route-to-setup-only-surface-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/new"}
                    :wallet {:address "0x1111111111111111111111111111111111111111"}
                    :webdata2 {:clearinghouseState {:marginSummary {:accountValue "100"}}}})]
    (is (some? (node-by-role view-node "portfolio-optimizer-setup-surface")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-workspace")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-scenario-detail-surface")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-scenario-tabs")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-provenance-strip")))
    (is (some? (node-by-role view-node "portfolio-optimizer-setup-header")))
    (is (some? (node-by-role view-node "portfolio-optimizer-setup-status-tag")))
    (is (some? (node-by-role view-node "portfolio-optimizer-setup-preset-row")))
    (is (= [[:actions/apply-portfolio-optimizer-setup-preset :conservative]]
           (click-actions
            (node-by-role view-node
                          "portfolio-optimizer-setup-preset-conservative"))))
    (is (= [[:actions/apply-portfolio-optimizer-setup-preset :risk-adjusted]]
           (click-actions
            (node-by-role view-node
                          "portfolio-optimizer-setup-preset-risk-adjusted"))))
    (is (= [[:actions/apply-portfolio-optimizer-setup-preset :use-my-views]]
           (click-actions
            (node-by-role view-node
                          "portfolio-optimizer-setup-preset-use-my-views"))))
    (is (some? (node-by-role view-node "portfolio-optimizer-draft-state")))
    (is (= true
           (get-in (node-by-role view-node "portfolio-optimizer-run-draft")
                   [1 :disabled])))
    (is (= true
           (get-in (node-by-role view-node "portfolio-optimizer-save-scenario")
                   [1 :disabled])))
    (is (nil? (node-by-role view-node "portfolio-optimizer-load-history")))
    (is (some? (node-by-role view-node "portfolio-optimizer-universe-panel")))
    (is (= [[:actions/set-portfolio-optimizer-universe-from-current]]
           (click-actions
            (node-by-role view-node
                          "portfolio-optimizer-universe-use-current"))))
    (is (some? (node-by-role view-node "portfolio-optimizer-setup-model-grid")))
    (is (some? (node-by-role view-node "portfolio-optimizer-setup-model-column")))
    (is (some? (node-by-role view-node "portfolio-optimizer-objective-panel")))
    (is (some? (node-by-role view-node "portfolio-optimizer-return-model-panel")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-black-litterman-panel")))
    (is (some? (node-by-role view-node "portfolio-optimizer-risk-model-panel")))
    (is (some? (node-by-role view-node "portfolio-optimizer-constraints-panel")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-execution-assumptions-panel")))
    (is (= [[:actions/set-portfolio-optimizer-constraint
             :long-only?
             true]]
           (click-actions
            (node-by-role view-node
                          "portfolio-optimizer-constraint-long-only-input"))))
    (is (= "false"
           (get-in (node-by-role view-node
                                 "portfolio-optimizer-constraint-long-only-input")
                   [1 :aria-checked])))
    (is (= "false"
           (get-in (node-by-role view-node
                                 "portfolio-optimizer-constraint-include-spot-input")
                   [1 :aria-checked])))
    (is (= [[:actions/set-portfolio-optimizer-constraint
             :include-spot?
             true]]
           (click-actions
            (node-by-role view-node
                          "portfolio-optimizer-constraint-include-spot-input"))))
    (is (= "0.5"
           (get-in (node-by-role view-node
                                 "portfolio-optimizer-constraint-max-asset-weight-input")
                   [1 :value])))
    (is (= [[:actions/set-portfolio-optimizer-constraint
             :max-asset-weight
             [:event.target/value]]]
           (input-actions
            (node-by-role view-node
                          "portfolio-optimizer-constraint-max-asset-weight-input"))))
    (is (= [[:actions/set-portfolio-optimizer-constraint
             :gross-max
             [:event.target/value]]]
           (input-actions
            (node-by-role view-node
                          "portfolio-optimizer-constraint-gross-max-input"))))
    (is (= [[:actions/set-portfolio-optimizer-constraint
             :net-min
             [:event.target/value]]]
           (input-actions
            (node-by-role view-node
                          "portfolio-optimizer-constraint-net-min-input"))))
    (is (= [[:actions/set-portfolio-optimizer-constraint
             :net-max
             [:event.target/value]]]
           (input-actions
            (node-by-role view-node
                          "portfolio-optimizer-constraint-net-max-input"))))
    (is (= [[:actions/set-portfolio-optimizer-constraint
             :dust-usdc
             [:event.target/value]]]
           (input-actions
            (node-by-role view-node
                          "portfolio-optimizer-constraint-dust-usdc-input"))))
    (is (= [[:actions/set-portfolio-optimizer-constraint
             :max-turnover
             [:event.target/value]]]
           (input-actions
            (node-by-role view-node
                          "portfolio-optimizer-constraint-max-turnover-input"))))
    (is (= "true"
           (get-in (node-by-role view-node
                                 "portfolio-optimizer-constraint-max-turnover-toggle")
                   [1 :aria-checked])))
    (is (= [[:actions/set-portfolio-optimizer-constraint :max-turnover nil]]
           (click-actions
            (node-by-role view-node
                          "portfolio-optimizer-constraint-max-turnover-toggle"))))
    (is (= [[:actions/set-portfolio-optimizer-constraint
             :rebalance-tolerance
             [:event.target/value]]]
           (input-actions
            (node-by-role view-node
                          "portfolio-optimizer-constraint-rebalance-tolerance-input"))))
    (is (= [[:actions/set-portfolio-optimizer-objective-kind :max-sharpe]]
           (click-actions (node-by-role view-node "portfolio-optimizer-objective-max-sharpe"))))
    (is (= [[:actions/set-portfolio-optimizer-return-model-kind :black-litterman]]
           (click-actions (node-by-role view-node "portfolio-optimizer-return-model-black-litterman"))))
    (is (= [[:actions/set-portfolio-optimizer-risk-model-kind :sample-covariance]]
           (click-actions (node-by-role view-node "portfolio-optimizer-risk-model-sample-covariance"))))
    (is (= [[:actions/set-portfolio-optimizer-risk-model-kind :diagonal-shrink]]
           (click-actions (node-by-role view-node "portfolio-optimizer-risk-model-diagonal-shrink"))))
    (is (= [[:actions/set-portfolio-optimizer-risk-model-kind :mixed-frequency]]
           (click-actions (node-by-role view-node "portfolio-optimizer-risk-model-mixed-frequency"))))
    (doseq [role ["portfolio-optimizer-objective-target-return-input" "portfolio-optimizer-objective-target-volatility-input"]]
      (is (nil? (node-by-role view-node role))))
    (is (some? (node-by-role view-node "portfolio-optimizer-advanced-overrides-shell")))
    (is (some? (node-by-role view-node "portfolio-optimizer-instrument-overrides-panel")))
    (let [strings (set (collect-strings view-node))]
      (is (contains? strings "Minimum Variance"))
      (is (contains? strings "Historical Mean"))
      (is (contains? strings "Diagonal Shrink"))
      (is (contains? strings "Draft clean"))
      (is (not (contains? strings "Load History")))
      (is (contains? strings "Max Asset Weight"))
      (is (contains? strings "Gross Leverage"))
      (is (contains? strings "Include Spot Assets"))
      (is (contains? strings "Rebalance Tolerance"))
      (is (not (contains? strings "Execution Assumptions")))
      (is (not (contains? strings "Fallback Slippage")))
      (is (not (contains? strings "Manual Capital Base")))
      (is (not (contains? strings "Default Order: Market")))
      (is (not (contains? strings "Fee Mode: Taker"))))))

(deftest portfolio-optimizer-turnover-cap-toggle-disables-cap-input-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/new"}
                    :portfolio {:optimizer {:draft {:constraints {:max-turnover nil}}}}})
        toggle (node-by-role view-node
                             "portfolio-optimizer-constraint-max-turnover-toggle")
        input (node-by-role view-node
                            "portfolio-optimizer-constraint-max-turnover-input")
        strings (set (collect-strings view-node))]
    (is (= "false" (get-in toggle [1 :aria-checked])))
    (is (= [[:actions/set-portfolio-optimizer-constraint :max-turnover 1.0]]
           (click-actions toggle)))
    (is (= true (get-in input [1 :disabled])))
    (is (= "" (get-in input [1 :value])))
    (is (nil? (input-actions input)))
    (is (contains? strings "no cap"))))

(deftest portfolio-optimizer-objective-parameter-inputs-follow-selected-objective-test
  (let [return-role "portfolio-optimizer-objective-target-return-input"
        volatility-role "portfolio-optimizer-objective-target-volatility-input"
        view-for #(portfolio-optimizer-setup-view {:kind % :target-return 0.18 :target-volatility 0.25})
        action-for #(input-actions (node-by-role (view-for %1) %2))]
    (is (= [[:actions/set-portfolio-optimizer-objective-parameter :target-return [:event.target/value]]] (action-for :target-return return-role)))
    (is (= [[:actions/set-portfolio-optimizer-objective-parameter :target-volatility [:event.target/value]]] (action-for :target-volatility volatility-role)))
    (doseq [[kind role] [[:target-return volatility-role] [:target-volatility return-role] [:max-sharpe return-role] [:max-sharpe volatility-role]]]
      (is (nil? (node-by-role (view-for kind) role))))))

(deftest portfolio-optimizer-setup-route-shows-use-my-views-context-for-black-litterman-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/new"}
                    :portfolio {:optimizer
                                {:draft {:universe [{:instrument-id "perp:BTC"
                                                     :market-type :perp
                                                     :coin "BTC"
                                                     :icon-url "https://app.hyperliquid.xyz/coins/BTC.svg"}
                                                    {:instrument-id "perp:ETH"
                                                     :market-type :perp
                                                     :coin "ETH"}]
                                         :objective {:kind :max-sharpe}
                                         :return-model {:kind :black-litterman
                                                        :views [{:id "view-1"
                                                                 :kind :absolute
                                                                 :instrument-id "perp:BTC"
                                                                 :return 0.7
                                                                 :confidence-level :high
                                                                 :confidence 0.75
                                                                 :weights {"perp:BTC" 1}}]}
                                         :risk-model {:kind :diagonal-shrink}
                                         :constraints {:long-only? false}}}}})
        inline-editor (node-by-role view-node
                                    "portfolio-optimizer-setup-use-my-views-editor")
        btc-row (node-by-role view-node
                              "portfolio-optimizer-objective-menu-view-row-perp:BTC")
        btc-icon (node-by-role view-node
                               "portfolio-optimizer-objective-menu-view-perp:BTC-icon-img")
        btc-return (node-by-role view-node
                                 "portfolio-optimizer-objective-menu-view-perp:BTC-return")
        btc-confidence-high (node-by-role
                             view-node
                             "portfolio-optimizer-objective-menu-view-perp:BTC-confidence-high")
        run-button (node-by-role view-node "portfolio-optimizer-run-draft")
        strings (set (collect-strings view-node))]
    (is (some? (node-by-role view-node "portfolio-optimizer-setup-use-my-views-context")))
    (is (some? inline-editor))
    (is (some? btc-row))
    (is (nil? (node-by-role view-node "portfolio-optimizer-black-litterman-panel")))
    (is (= "true"
           (get-in (node-by-role view-node
                                 "portfolio-optimizer-setup-preset-use-my-views")
                   [1 :aria-pressed])))
    (is (contains? strings "Use my views"))
    (is (contains? (set (collect-strings inline-editor)) "Your views"))
    (is (= "https://app.hyperliquid.xyz/coins/BTC.svg"
           (get-in btc-icon [1 :src])))
    (is (= "70" (get-in btc-return [1 :value])))
    (is (= [[:actions/set-portfolio-optimizer-objective-menu-view-return
             "perp:BTC"
             [:event.target/value]]]
           (input-actions btc-return)))
    (is (= "true" (get-in btc-confidence-high [1 :data-selected])))
    (is (= [[:actions/set-portfolio-optimizer-objective-menu-view-confidence
             "perp:BTC"
             :high]]
           (click-actions btc-confidence-high)))
    (is (= [[:actions/apply-portfolio-optimizer-objective-menu-selection-and-run]]
           (click-actions run-button)))))
