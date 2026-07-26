(ns hyperopen.portfolio.optimizer.application.engine.equal-risk-payload-diversification-edge-test
  "Payload-boundary degradation for current/target diversification summaries."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.optimizer.application.engine.equal-risk-payload
             :as equal-risk-payload]
            [hyperopen.portfolio.optimizer.application.view-model.equal-risk-structure
             :as structure-model]))

(defn- input
  [overrides]
  (merge {:risk-result {:covariance [[1.0 0.25] [0.25 1.0]]}
          :selection {:selected {:equal-risk {:converged? true
                                              :strategy :sequential-equal-risk}}}
          :solver-plan {}
          :diagnostics {}
          :instrument-ids ["A" "B"]
          :target-weights [0.5 0.5]
          :current-weights [0.0 0.0]}
         overrides))

(deftest flat-current-book-cannot-erase-valid-target-summary-test
  (let [sections (equal-risk-payload/equal-risk-sections (input {}))
        structure (:risk-structure sections)]
    (is (map? structure))
    (is (map? (:target-diversification structure)))
    (is (= :signed-euler-decomposition (:method structure)))
    (is (or (not (contains? structure :current-diversification))
            (= :unavailable
               (get-in structure [:current-diversification :status]))))
    (is (not= 0.0
              (get-in structure [:current-diversification
                                 :modeled-volatility])))))

(deftest alignment-errors-never-persist-a-partial-summary-test
  (doseq [[label overrides]
          [["instrument id mismatch" {:instrument-ids ["A" "B" "C"]}]
           ["target weight mismatch" {:target-weights [1.0]}]
           ["covariance mismatch" {:risk-result {:covariance [[1.0]]}}]]]
    (testing label
      (let [outcome (try
                      {:value (equal-risk-payload/equal-risk-sections
                               (input overrides))}
                      (catch :default error {:error error}))]
        (is (nil? (:error outcome))
            "payload alignment failures must use a data error path, not throw")
        (let [sections (:value outcome)
              summary (get-in sections
                              [:risk-structure :target-diversification])]
          (is (or (nil? summary) (= :unavailable (:status summary))))
          (is (not (contains? summary :modeled-volatility)))
          (is (or (seq (:warnings sections))
                  (nil? (:risk-structure sections)))))))))

(deftest non-flat-zero-variance-current-book-is-omitted-end-to-end-test
  (let [sections (equal-risk-payload/equal-risk-sections
                  (input {:risk-result {:covariance [[1.0 1.0]
                                                     [1.0 1.0]]}
                          :target-weights [0.5 0.5]
                          :current-weights [0.5 -0.5]}))
        structure (:risk-structure sections)
        model (structure-model/diversification-comparison-model
               {:risk-structure structure})]
    (is (map? (:target-diversification structure)))
    (is (not (contains? structure :current-diversification))
        "non-flat current weights do not make a degenerate hedge available")
    (is (= [:target] (mapv :key (:cards model))))
    (is (pos? (get-in model [:cards 0 :benchmarks 2 :value])))))
