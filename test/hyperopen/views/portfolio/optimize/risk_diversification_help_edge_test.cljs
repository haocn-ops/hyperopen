(ns hyperopen.views.portfolio.optimize.risk-diversification-help-edge-test
  "Accessible disclosure contracts for the diversification comparison."
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.views.portfolio.optimize.risk-contributions-card :as card]
            [hyperopen.views.portfolio.optimize.risk-diversification-summary
             :as summary]
            [hyperopen.views.portfolio.optimize.test-support
             :refer [collect-nodes collect-strings solved-result]]))

(def ^:private base-result
  (assoc solved-result
         :solver {:strategy :sequential-equal-risk :objective-kind :equal-risk}
         :risk-contributions
         {:method :signed-euler-volatility
          :instrument-ids ["perp:BTC" "spot:PURR"]
          :relative-contributions [0.6 0.4]
          :target-relative-contributions [0.5 0.5]
          :relative-contributions-by-instrument {"perp:BTC" 0.6
                                                 "spot:PURR" 0.4}
          :target-relative-contributions-by-instrument {"perp:BTC" 0.5
                                                        "spot:PURR" 0.5}
          :sum-relative-contributions 1.0
          :rms-error 0.1
          :max-absolute-error 0.1
          :negative-contribution-count 0
          :quality :approximate}
         :risk-structure
         {:method :signed-euler-decomposition
          :portfolio-volatility 0.4
          :target-diversification
          {:modeled-volatility 0.4
           :all-move-together-volatility 0.6
           :zero-correlation-volatility 0.35
           :reduction-vs-all-move-together 0.2
           :reduction-ratio-vs-all-move-together (/ 1 3)
           :modeled-minus-zero-correlation 0.05}
          :standalone-share-by-instrument {"perp:BTC" 0.4 "spot:PURR" 0.5}
          :diversification-share-by-instrument {"perp:BTC" 0.2
                                                "spot:PURR" -0.1}
          :pnl-portfolio-correlation-by-instrument {"perp:BTC" 0.8
                                                    "spot:PURR" 0.4}
          :correlation {:instrument-ids ["perp:BTC" "spot:PURR"]
                        :matrix [[1.0 0.5] [0.5 1.0]]
                        :hidden-count 0}}))

(defn- render-comparison
  [current target]
  (card/risk-contributions-card
   (-> base-result
       (assoc-in [:risk-structure :current-diversification] current)
       (assoc-in [:risk-structure :target-diversification] target))))

(deftest two-book-matrix-exposes-six-accessible-help-tooltips-test
  (let [current {:modeled-volatility 0.4
                 :all-move-together-volatility 0.6
                 :zero-correlation-volatility 0.3
                 :reduction-vs-all-move-together 0.2
                 :reduction-ratio-vs-all-move-together (/ 1 3)
                 :modeled-minus-zero-correlation 0.1}
        view (render-comparison current
                                (assoc current :modeled-volatility 0.35))
        triggers (collect-nodes
                  view
                  #(= "portfolio-optimizer-risk-diversification-help-trigger"
                      (get-in % [1 :data-role])))
        tooltips (collect-nodes view #(= "tooltip" (get-in % [1 :role])))
        described-ids (mapv #(get-in % [1 :aria-describedby]) triggers)
        tooltip-ids (mapv #(get-in % [1 :id]) tooltips)
        tooltip-copy-contract
        #{"Current and Recommended share one annualized-volatility scale; Change is Recommended minus Current in percentage points."
          "hypothetical volatility if all held position P&L streams moved together; a stress benchmark, not a forecast."
          "hypothetical volatility if held position P&L streams moved independently."
          "estimated portfolio volatility using the modeled relationships between positions."
          "how far modeled volatility is below the all-move-together benchmark; a larger benefit does not necessarily mean lower absolute risk."
          "modeled volatility minus zero-correlation volatility; negative offsets risk and positive amplifies it."}
        strings (set (collect-strings view))]
    (is (= 6 (count triggers)))
    (is (= 6 (count tooltips)))
    (is (every? #(= :button (first %)) triggers))
    (is (every? #(= "button" (get-in % [1 :type])) triggers))
    (is (every? #(let [label (get-in % [1 :aria-label])]
                   (and (string? label) (not (str/blank? label))))
                triggers))
    (is (= 6 (count (distinct described-ids))))
    (is (= 6 (count (distinct tooltip-ids))))
    (is (= (set described-ids) (set tooltip-ids)))
    (is (= tooltip-copy-contract
           (set (mapcat collect-strings tooltips))))
    (is (every? strings
                ["Diversification benefit" "vs all-move-together"
                 "Correlation effect" "vs zero correlation"]))))

(deftest help-tooltip-ids-can-be-namespaced-per-summary-instance-test
  (let [current {:modeled-volatility 0.4
                 :all-move-together-volatility 0.6
                 :zero-correlation-volatility 0.3
                 :reduction-vs-all-move-together 0.2
                 :reduction-ratio-vs-all-move-together (/ 1 3)
                 :modeled-minus-zero-correlation 0.1}
        result (assoc-in base-result
                         [:risk-structure :current-diversification]
                         current)
        primary (summary/diversification-summary
                 result {:help-id-prefix "optimizer-card-primary"})
        secondary (summary/diversification-summary
                   result {:help-id-prefix "optimizer-card-secondary"})
        described-ids (fn [view]
                        (mapv #(get-in % [1 :aria-describedby])
                              (collect-nodes
                               view
                               #(= "portfolio-optimizer-risk-diversification-help-trigger"
                                   (get-in % [1 :data-role])))))
        tooltip-ids (fn [view]
                      (mapv #(get-in % [1 :id])
                            (collect-nodes
                             view
                             #(= "tooltip" (get-in % [1 :role])))))
        primary-described (described-ids primary)
        secondary-described (described-ids secondary)
        suffixes #{"overview" "all-move-together" "zero-correlation"
                   "modeled" "diversification-benefit" "correlation-effect"}
        expected-ids (fn [prefix]
                       (set (map #(str prefix "-" %) suffixes)))]
    (is (= (expected-ids "optimizer-card-primary")
           (set primary-described)
           (set (tooltip-ids primary))))
    (is (= (expected-ids "optimizer-card-secondary")
           (set secondary-described)
           (set (tooltip-ids secondary))))
    (is (not-any? (set primary-described) secondary-described))))
