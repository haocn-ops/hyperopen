(ns hyperopen.views.portfolio.optimize.leverage-impact-panel-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.portfolio.optimizer.domain.leverage-risk :as leverage-risk]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]
            [hyperopen.views.portfolio.optimize.format :as opt-format]
            [hyperopen.views.portfolio.optimize.leverage-impact-panel :as panel]
            [hyperopen.views.portfolio.optimize.test-support
             :refer [collect-strings node-by-role]]))

(defn- strings-of
  [node]
  (set (collect-strings node)))

(defn- whole-usd
  [value]
  (opt-format/format-usdc value {:maximum-fraction-digits 0}))

(deftest panel-hidden-below-the-leverage-gate-test
  ;; Base fixture: 0.9x gross at 28% σ — no leverage story to tell.
  (is (nil? (panel/leverage-impact-panel (fixtures/sample-solved-result)))))

(deftest levered-target-gets-modeled-dollar-outcomes-test
  (let [result (fixtures/sample-solved-result
                {:diagnostics {:gross-exposure 2.5}})
        node (panel/leverage-impact-panel result)
        strings (strings-of node)
        target-outcome (leverage-risk/outcome-model {:expected-return 0.16
                                                     :volatility 0.28})
        current-outcome (leverage-risk/outcome-model {:expected-return 0.12
                                                      :volatility 0.24})]
    (is (some? node))
    (is (contains? strings "One-year modeled leverage impact"))
    (is (contains? strings "Modeled"))
    ;; Median rows carry the modeled dollars for both books (capital $100k).
    (is (contains? strings
                   (whole-usd (* 100000 (:median-ending-factor target-outcome)))))
    (is (contains? strings
                   (whole-usd (* 100000 (:median-ending-factor current-outcome)))))
    (is (some? (node-by-role node
                             "portfolio-optimizer-leverage-impact-median-current")))
    (is (some? (node-by-role node
                             "portfolio-optimizer-leverage-impact-median-target")))
    ;; The mockup's tile row: mean, 5th percentile, and both loss odds.
    (is (contains? (strings-of (node-by-role node
                                             "portfolio-optimizer-leverage-impact-mean"))
                   (whole-usd (* 100000 (:mean-ending-factor target-outcome)))))
    (is (some? (node-by-role node "portfolio-optimizer-leverage-impact-p5")))
    (is (some? (node-by-role node "portfolio-optimizer-leverage-impact-terminal")))
    ;; The honesty fine print is always on the panel.
    (is (some #(str/includes? % "Lognormal model") (collect-strings node)))
    (is (some #(str/includes? % "Modeled, not a guarantee") (collect-strings node)))
    ;; Never a liquidation claim — the drawdown odds are framed as a floor.
    (is (not (some #(str/includes? (str/lower-case %) "liquidation probability")
                   (collect-strings node))))
    (is (some #(str/includes? % "floor on ruin risk") (collect-strings node)))))

(deftest median-shortfall-headline-is-signed-test
  (let [shortfall-node (panel/leverage-impact-panel
                        (fixtures/sample-solved-result
                         {:diagnostics {:gross-exposure 2.5}
                          ;; High σ drags the target median below current.
                          :volatility 1.4}))
        gain-node (panel/leverage-impact-panel
                   (fixtures/sample-solved-result
                    {:diagnostics {:gross-exposure 2.5}}))
        headline (fn [node]
                   (some->> (node-by-role node
                                          "portfolio-optimizer-leverage-impact-median-shortfall")
                            collect-strings
                            (str/join " ")))]
    (is (str/includes? (headline shortfall-node)
                       "Median wealth shortfall vs current"))
    (is (str/includes? (headline shortfall-node) "−$"))
    (is (str/includes? (headline gain-node) "Median wealth gain vs current"))
    (is (str/includes? (headline gain-node) "+$"))))

(deftest volatility-gate-surfaces-panel-without-gross-leverage-test
  (is (some? (panel/leverage-impact-panel
              (fixtures/sample-solved-result {:volatility 1.2})))))

(deftest distribution-draws-the-lognormal-with-three-markers-test
  (let [result (fixtures/sample-solved-result
                {:diagnostics {:gross-exposure 2.5}})
        node (panel/leverage-impact-panel result)
        dist (node-by-role node "portfolio-optimizer-leverage-impact-distribution")
        dist-strings (strings-of dist)
        target-outcome (leverage-risk/outcome-model {:expected-return 0.16
                                                     :volatility 0.28})]
    (is (some? dist))
    (is (some? (node-by-role dist "portfolio-optimizer-leverage-impact-dist-curve")))
    (is (some? (node-by-role dist "portfolio-optimizer-leverage-impact-dist-p5")))
    (is (some? (node-by-role dist "portfolio-optimizer-leverage-impact-dist-median")))
    (is (some? (node-by-role dist "portfolio-optimizer-leverage-impact-dist-mean")))
    ;; Marker labels use the compact dollar form, computed from the model.
    (doseq [factor [(:p5-ending-factor target-outcome)
                    (:median-ending-factor target-outcome)
                    (:mean-ending-factor target-outcome)]]
      (is (contains? dist-strings (panel/compact-usd (* 100000 factor)))))
    (is (contains? dist-strings "5th pct."))
    (is (contains? dist-strings "Median"))
    (is (contains? dist-strings "Mean"))
    (is (contains? dist-strings "Lower"))
    (is (contains? dist-strings "Higher"))
    ;; The log-scaled axis is disclosed, not silent.
    (is (some #(str/includes? % "log-scaled") (collect-strings node)))))

(deftest distribution-skipped-for-a-degenerate-zero-sigma-model-test
  ;; A zero-volatility target can still pass the gross gate; the deterministic
  ;; outcome has no distribution to draw.
  (let [node (panel/leverage-impact-panel
              (fixtures/sample-solved-result
               {:diagnostics {:gross-exposure 2.5}
                :volatility 0}))]
    (is (some? node))
    (is (nil? (node-by-role node
                            "portfolio-optimizer-leverage-impact-distribution")))))

(deftest panel-speaks-in-multiples-without-account-equity-test
  (let [node (panel/leverage-impact-panel
              (fixtures/sample-solved-result
               {:diagnostics {:gross-exposure 2.5}
                :rebalance-preview {:capital-usd nil}}))
        strings (strings-of node)
        target-outcome (leverage-risk/outcome-model {:expected-return 0.16
                                                     :volatility 0.28})]
    (is (some? node))
    (is (contains? strings
                   (str (opt-format/format-multiple
                         (:median-ending-factor target-outcome))
                        " start")))
    (is (nil? (node-by-role node
                            "portfolio-optimizer-leverage-impact-median-shortfall")))
    (is (some #(str/includes? % "multiples of starting equity")
              (collect-strings node)))
    ;; Distribution markers fall back to multiples too.
    (is (contains? (strings-of (node-by-role node
                                             "portfolio-optimizer-leverage-impact-distribution"))
                   (opt-format/format-multiple
                    (:median-ending-factor target-outcome))))))

(deftest probabilities-render-as-percentages-test
  (let [node (panel/leverage-impact-panel
              (fixtures/sample-solved-result
               {:diagnostics {:gross-exposure 2.5}
                :volatility 4.1182
                :expected-return 18.6606}))
        touch (node-by-role node "portfolio-optimizer-leverage-impact-touch")
        terminal (node-by-role node "portfolio-optimizer-leverage-impact-terminal")]
    ;; From the domain tests: ~87.8% terminal, ~98.2% touch at this μ/σ.
    (is (contains? (strings-of terminal) "87.8%"))
    (is (contains? (strings-of touch) "98.2%"))))

(deftest compact-usd-matches-the-mockup-forms-test
  (is (= "$408" (panel/compact-usd 408.2)))
  (is (= "$4.1k" (panel/compact-usd 4120)))
  (is (= "$43k" (panel/compact-usd 43210)))
  (is (= "$186k" (panel/compact-usd 186000)))
  (is (= "$2M" (panel/compact-usd 1966060)))
  (is (= "−$500" (panel/compact-usd -500))))
