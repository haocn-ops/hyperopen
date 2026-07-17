(ns hyperopen.views.portfolio.optimize.setup-view-inverse-volatility-test
  "Setup-surface coverage for the Risk-weighted sizing (:inverse-volatility)
  goal (ExecPlan optimizer-inverse-volatility-objective, item 8): a third
  secondary card under More goals dispatching the plain objective-kind
  action (no return-model pairing, no parameter block), and a goal-summary
  header line for the selected kind. Lives beside setup-view-test — that
  namespace sits against the size gate."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.views.portfolio-view :as portfolio-view]
            [hyperopen.views.portfolio.optimize.test-support
             :refer [click-actions collect-strings node-by-role]]))

(defn- setup-view
  [objective]
  (portfolio-view/portfolio-view
   {:router {:path "/portfolio/optimize/new"}
    :portfolio {:optimizer {:draft {:objective objective}}}}))

(deftest more-goals-offers-risk-weighted-sizing-secondary-card-test
  (let [view-node (setup-view {:kind :minimum-variance})
        more-goals (node-by-role view-node "portfolio-optimizer-more-goals")
        card (node-by-role more-goals
                           "portfolio-optimizer-objective-inverse-volatility")]
    (is (some? card)
        "the Risk-weighted sizing card renders under More goals")
    (is (= [[:actions/set-portfolio-optimizer-objective-kind :inverse-volatility]]
           (click-actions card))
        "the card sets only the objective — no preset, no return-model pairing")
    (is (= "false" (get-in card [1 :aria-pressed])))
    (is (contains? (set (collect-strings card)) "Risk-weighted sizing"))))

(deftest selected-risk-weighted-sizing-shows-summary-and-pressed-card-test
  (let [view-node (setup-view {:kind :inverse-volatility})
        card (node-by-role view-node
                           "portfolio-optimizer-objective-inverse-volatility")
        strings (set (collect-strings view-node))]
    (is (= "true" (get-in card [1 :aria-pressed])))
    (testing "the collapsed-panel goal summary names the selected goal"
      (is (some #(re-find #"^Risk-weighted sizing — " %) strings)
          (str "expected a goal-summary line starting with "
               "\"Risk-weighted sizing — \"")))
    (testing "parameterless goal: no target parameter inputs appear"
      (is (nil? (node-by-role view-node
                              "portfolio-optimizer-objective-target-return-input")))
      (is (nil? (node-by-role view-node
                              "portfolio-optimizer-objective-target-volatility-input"))))))
