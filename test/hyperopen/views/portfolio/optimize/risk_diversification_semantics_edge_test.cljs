(ns hyperopen.views.portfolio.optimize.risk-diversification-semantics-edge-test
  "User-visible semantics for final-weight covariance attribution."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.string :as str]
            [hyperopen.views.portfolio.optimize.risk-contributions-card :as card]
            [hyperopen.views.portfolio.optimize.test-support
             :refer [collect-nodes collect-strings node-by-role solved-result]]))

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

(defn- render
  [result]
  (card/risk-contributions-card result))

(defn- row-for
  [view instrument-id]
  (first (collect-nodes
          view
          #(and (= "portfolio-optimizer-risk-breakdown-row"
                   (get-in % [1 :data-role]))
                (= instrument-id (get-in % [1 :data-instrument-id]))))))

(deftest risk-card-separates-balance-diversification-and-attribution-test
  (let [result (assoc-in base-result
                         [:risk-structure :current-diversification]
                         {:modeled-volatility 0.24
                          :all-move-together-volatility 0.32
                          :zero-correlation-volatility 0.20
                          :reduction-vs-all-move-together 0.08
                          :reduction-ratio-vs-all-move-together 0.25
                          :modeled-minus-zero-correlation 0.04})
        view (render result)
        strings (set (collect-strings view))
        comparison (node-by-role
                    view "portfolio-optimizer-risk-diversification-comparison")
        row (row-for view "perp:BTC")]
    (testing "Risk Balance explains its narrow optimizer objective"
      (is (some #(str/includes? % "balances risk ownership") strings))
      (is (some #(str/includes? % "does not minimize total volatility") strings)))
    (testing "Diversification compares both books and all three benchmarks"
      (is (some? comparison))
      (is (every? (set (collect-strings comparison))
                  ["Current" "Recommended" "All move together"
                   "Zero correlation" "Modeled"])))
    (testing "attribution is explicitly additive and non-causal"
      (is (some #(str/includes? % "final-weight attribution") strings))
      (is (some #(str/includes? % "not removal impact") strings))
      (is (= (get-in row [1 :data-own-end])
             (get-in row [1 :data-cross-start])))
      (is (= (get-in row [1 :data-cross-end])
             (get-in row [1 :data-net-end]))))
    (testing "the retired causal-overclaim labels are absent"
      (is (not (contains? strings "Risk if held in isolation")))
      (is (not (contains? strings "Diversification Effect"))))))

(deftest legacy-structure-keeps-existing-risk-views-test
  (let [legacy (update base-result :risk-structure
                       dissoc :current-diversification :target-diversification)
        view (render legacy)
        strings (set (collect-strings view))]
    (is (some? (node-by-role view
                             "portfolio-optimizer-risk-view-tab-contribution")))
    (is (some? (node-by-role view
                             "portfolio-optimizer-risk-selected-breakdown")))
    (is (some? (node-by-role view
                             "portfolio-optimizer-risk-view-tab-correlation")))
    (is (some? (node-by-role view
                             "portfolio-optimizer-risk-view-tab-risk-return")))
    (is (or (nil? (node-by-role
                   view "portfolio-optimizer-risk-diversification-comparison"))
            (some #(str/includes? % "Re-run") strings)))
    (is (not (some #(re-find #"All move together.*0(?:\.0)?%" %) strings)))))

(deftest bridge-directions-use-words-and-economic-tones-test
  (let [view (render base-result)
        positive (row-for view "perp:BTC")
        negative (row-for view "spot:PURR")]
    (is (= "amplifies" (get-in positive [1 :data-cross-effect])))
    (is (= "offsets" (get-in negative [1 :data-cross-effect])))
    (is (= "risk-amplifying" (get-in positive [1 :data-cross-tone])))
    (is (= "risk-offsetting" (get-in negative [1 :data-cross-tone])))
    (is (str/includes? (get-in positive [1 :title]) "amplifies"))
    (is (str/includes? (get-in negative [1 :title]) "offsets"))
    (doseq [row [positive negative]]
      (is (str/includes? (get-in row [1 :title]) "starts"))
      (is (str/includes? (get-in row [1 :title]) "ends")))))

(deftest net-and-negative-contributor-kpi-stay-judgment-neutral-test
  (doseq [net [0.25 -0.25 0.0]]
    (let [result (-> base-result
                     (assoc-in [:risk-contributions
                                :relative-contributions-by-instrument
                                "perp:BTC"] net)
                     (assoc-in [:risk-contributions :relative-contributions 0]
                               net)
                     (assoc-in [:risk-structure
                                :standalone-share-by-instrument
                                "perp:BTC"] 0.25)
                     (assoc-in [:risk-structure
                                :diversification-share-by-instrument
                                "perp:BTC"] (- net 0.25)))
          view (render result)
          row (row-for view "perp:BTC")
          net-node (node-by-role row "portfolio-optimizer-risk-breakdown-net")]
      (is (= "target" (get-in net-node [1 :data-tone])))))
  (let [view (render (assoc-in base-result
                               [:risk-contributions :negative-contribution-count]
                               1))
        kpi (node-by-role view
                          "portfolio-optimizer-risk-contributions-negative")]
    (is (= "neutral" (get-in kpi [1 :data-tone])))
    (is (some #{"1"} (collect-strings kpi)))))

(deftest attribution-copy-forbids-removal-overclaims-test
  (let [view (render base-result)
        strings (set (collect-strings view))]
    (is (every? strings ["Own-variance term" "Cross-covariance effect"
                         "Net risk contribution"]))
    (is (some #(str/includes? % "final-weight attribution") strings))
    (is (not (contains? strings "Risk if held in isolation")))
    (is (not (contains? strings "Diversification Effect")))
    (is (not-any? #(or (str/includes? % "Removing this asset would")
                       (str/includes? % "Without this asset"))
                  strings))))

(deftest legacy-results-keep-stable-view-identities-test
  (let [legacy (update base-result :risk-structure
                       dissoc :target-diversification :current-diversification)
        view (render legacy)
        strings (set (collect-strings view))]
    (doseq [role ["portfolio-optimizer-risk-view-tab-contribution"
                  "portfolio-optimizer-risk-view-tab-breakdown"
                  "portfolio-optimizer-risk-view-tab-correlation"
                  "portfolio-optimizer-risk-view-tab-risk-return"]]
      (is (some? (node-by-role view role))))
    (is (some? (node-by-role view
                             "portfolio-optimizer-risk-selected-breakdown")))
    (is (or (nil? (node-by-role
                   view "portfolio-optimizer-risk-diversification-comparison"))
            (some #(str/includes? % "Re-run") strings)))))

(deftest current-only-summary-never-renders-a-comparison-test
  (let [summary {:modeled-volatility 0.2
                 :all-move-together-volatility 0.3
                 :zero-correlation-volatility 0.18
                 :reduction-vs-all-move-together 0.1
                 :reduction-ratio-vs-all-move-together (/ 1 3)
                 :modeled-minus-zero-correlation 0.02}
        current-only (-> base-result
                         (update :risk-structure dissoc
                                 :target-diversification)
                         (assoc-in [:risk-structure :current-diversification]
                                   summary))
        view (render current-only)]
    (is (nil? (node-by-role
               view "portfolio-optimizer-risk-diversification-comparison")))
    (is (nil? (node-by-role
               view "portfolio-optimizer-risk-diversification-current")))))

(deftest rounded-zero-cross-effects-render-neutral-test
  (doseq [[effect expected tone]
          [[0.00049 "neutral" "neutral"]
           [0.00051 "amplifies" "risk-amplifying"]
           [-0.00051 "offsets" "risk-offsetting"]]]
    (let [net (+ 0.4 effect)
          result (-> base-result
                     (assoc-in [:risk-structure
                                :diversification-share-by-instrument
                                "perp:BTC"] effect)
                     (assoc-in [:risk-contributions
                                :relative-contributions-by-instrument
                                "perp:BTC"] net)
                     (assoc-in [:risk-contributions :relative-contributions 0]
                               net))
          row (row-for (render result) "perp:BTC")]
      (is (= expected (get-in row [1 :data-cross-effect])))
      (is (= tone (get-in row [1 :data-cross-tone]))))))
