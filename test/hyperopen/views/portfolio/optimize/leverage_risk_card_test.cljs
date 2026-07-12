(ns hyperopen.views.portfolio.optimize.leverage-risk-card-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.portfolio.optimizer.domain.leverage-risk :as leverage-risk]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]
            [hyperopen.views.portfolio.optimize.format :as opt-format]
            [hyperopen.views.portfolio.optimize.leverage-risk-card :as card]
            [hyperopen.views.portfolio.optimize.test-support
             :refer [collect-strings node-by-role]]))

(defn- strings-of
  [node]
  (set (collect-strings node)))

(defn- whole-usd
  [value]
  (opt-format/format-usdc value {:maximum-fraction-digits 0}))

(deftest card-hidden-below-the-leverage-gate-test
  ;; Base fixture: 0.9x gross at 28% σ — no leverage story to tell.
  (is (nil? (card/leverage-risk-card (fixtures/sample-solved-result)))))

(deftest levered-target-gets-modeled-dollar-outcomes-test
  (let [result (fixtures/sample-solved-result
                {:diagnostics {:gross-exposure 2.5}})
        node (card/leverage-risk-card result)
        strings (strings-of node)
        target-outcome (leverage-risk/outcome-model {:expected-return 0.16
                                                     :volatility 0.28})
        current-outcome (leverage-risk/outcome-model {:expected-return 0.12
                                                      :volatility 0.24})]
    (is (some? node))
    (is (contains? strings "Leverage risk"))
    (is (contains? strings "1y · modeled"))
    ;; Median rows carry the modeled dollars for both books (capital $100k).
    (is (contains? strings
                   (whole-usd (* 100000 (:median-ending-factor target-outcome)))))
    (is (contains? strings
                   (whole-usd (* 100000 (:median-ending-factor current-outcome)))))
    (is (some? (node-by-role node
                             "portfolio-optimizer-leverage-risk-median-current")))
    (is (some? (node-by-role node
                             "portfolio-optimizer-leverage-risk-median-target")))
    (is (some? (node-by-role node "portfolio-optimizer-leverage-risk-p5")))
    ;; The honesty fine print is always on the card.
    (is (some #(str/includes? % "Lognormal model") (collect-strings node)))
    (is (some #(str/includes? % "Modeled, not a guarantee") (collect-strings node)))
    ;; Never a liquidation claim — the drawdown odds are framed as a floor.
    (is (not (some #(str/includes? (str/lower-case %) "liquidation probability")
                   (collect-strings node))))
    (is (some #(str/includes? % "floor on ruin risk") (collect-strings node)))))

(deftest median-shortfall-note-is-signed-test
  (let [shortfall-result (fixtures/sample-solved-result
                          {:diagnostics {:gross-exposure 2.5}
                           ;; High σ drags the target median below current.
                           :volatility 1.4})
        gain-node (card/leverage-risk-card
                   (fixtures/sample-solved-result
                    {:diagnostics {:gross-exposure 2.5}}))
        shortfall-node (card/leverage-risk-card shortfall-result)
        note (fn [node]
               (some->> (node-by-role node
                                      "portfolio-optimizer-leverage-risk-median-shortfall")
                        collect-strings
                        (str/join " ")))]
    (is (str/includes? (note gain-node) "Median vs current: +$"))
    (is (str/includes? (note shortfall-node) "Median vs current: −$"))))

(deftest volatility-gate-surfaces-card-without-gross-leverage-test
  (let [node (card/leverage-risk-card
              (fixtures/sample-solved-result {:volatility 1.2}))]
    (is (some? node))))

(deftest card-speaks-in-multiples-without-account-equity-test
  (let [node (card/leverage-risk-card
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
                            "portfolio-optimizer-leverage-risk-median-shortfall")))
    (is (some #(str/includes? % "multiple of starting equity")
              (collect-strings node)))))

(deftest probabilities-render-as-percentages-test
  (let [node (card/leverage-risk-card
              (fixtures/sample-solved-result
               {:diagnostics {:gross-exposure 2.5}
                :volatility 4.1182
                :expected-return 18.6606}))
        touch (node-by-role node "portfolio-optimizer-leverage-risk-touch")
        terminal (node-by-role node "portfolio-optimizer-leverage-risk-terminal")]
    ;; From the domain tests: ~87.8% terminal, ~98.2% touch at this μ/σ.
    (is (contains? (strings-of terminal) "87.8%"))
    (is (contains? (strings-of touch) "98.2%"))))
