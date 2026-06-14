(ns hyperopen.portfolio.optimizer.refinement-domain-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.optimizer.domain.refinement :as refinement]))

(defn- solved-result
  [{:keys [points strategy sensitivity warnings sharpe weights]
    :or {strategy :frontier-sweep}}]
  {:status :solved
   :solver {:strategy strategy :objective-kind :minimum-variance}
   :frontier-summary {:source :display-sweep :point-count points}
   :diagnostics {:weight-sensitivity-by-instrument (or sensitivity {})}
   :warnings (or warnings [])
   :performance {:in-sample-sharpe sharpe}
   :expected-return 0.1
   :volatility 0.12
   :target-weights-by-instrument (or weights {"perp:BTC" 0.6 "perp:ETH" 0.4})})

(deftest depth-budgets-test
  (is (= {:quick 56 :thorough 72 :maximum 80} refinement/depth->points))
  (is (= :thorough refinement/default-depth))
  (is (= 80 refinement/maximum-points))
  (is (= 72 (refinement/depth-points :thorough)))
  (is (= 72 (refinement/depth-points "thorough")))
  (is (nil? (refinement/depth-points :nonsense)))
  (is (= :quick (refinement/normalize-depth "quick")))
  (is (nil? (refinement/normalize-depth :bogus))))

(deftest result-tier-and-quality-test
  (testing "large-universe draft density reads as a Medium-quality draft"
    (let [a (refinement/assess-result (solved-result {:points 16}))]
      (is (= :draft (:tier a)))
      (is (= :medium (:frontier-quality a)))
      (is (= 16 (:point-count a)))))
  (testing "small-universe draft default density is still a draft"
    (is (= :draft (refinement/result-tier (solved-result {:points 40}))))
    (is (= :medium (refinement/frontier-quality (solved-result {:points 40})))))
  (testing "refined density"
    (is (= :refined (refinement/result-tier (solved-result {:points 72}))))
    (is (= :high (refinement/frontier-quality (solved-result {:points 72})))))
  (testing "maximum density"
    (is (= :maximum (refinement/result-tier (solved-result {:points 80})))))
  (testing "truncated sweep is partial / low"
    (let [r (solved-result {:points 72
                            :warnings [{:code :display-frontier-unavailable
                                        :requested-points 72 :available-points 30}]})]
      (is (= :partial (refinement/result-tier r)))
      (is (= :low (refinement/frontier-quality r)))))
  (testing "non-solved result is not assessed"
    (is (nil? (refinement/assess-result {:status :infeasible})))
    (is (nil? (refinement/assess-result nil)))))

(deftest selection-source-and-stability-test
  (testing "closed-form / single-qp selections are exact"
    (is (refinement/exact-selection? (solved-result {:strategy :closed-form :points 16})))
    (is (refinement/exact-selection? (solved-result {:strategy :single-qp :points 40})))
    (is (= :closed-form (refinement/selection-source (solved-result {:strategy :closed-form :points 16})))))
  (testing "frontier-sweep selection below max is provisional (can move when refined)"
    (is (not (refinement/exact-selection? (solved-result {:strategy :frontier-sweep :points 40}))))
    (is (= :provisional (refinement/selection-stability
                         (solved-result {:strategy :frontier-sweep :points 40})))))
  (testing "frontier-sweep at maximum density is no longer provisional"
    (is (= :stable (refinement/selection-stability
                    (solved-result {:strategy :frontier-sweep :points 80})))))
  (testing "exact selection with a sensitive weight reads Moderate, else Stable"
    (is (= :moderate (refinement/selection-stability
                      (solved-result {:strategy :closed-form :points 16
                                      :sensitivity {"perp:BTC" {:max-delta 0.01}}}))))
    (is (= :stable (refinement/selection-stability
                    (solved-result {:strategy :closed-form :points 16}))))))

(deftest stop-reason-and-next-step-test
  (is (= :draft-budget-reached (refinement/stop-reason (solved-result {:points 16}))))
  (is (= :refine-optimization (refinement/next-step (solved-result {:points 16}))))
  (is (= :maximum-density-reached (refinement/stop-reason (solved-result {:points 80}))))
  (is (= :none (refinement/next-step (solved-result {:points 80}))))
  (is (= :refined-density-reached (refinement/stop-reason (solved-result {:points 72}))))
  (is (= :refine-further (refinement/next-step (solved-result {:points 72})))))

(deftest selection-change-test
  (testing "identical selections are immaterial"
    (let [base (solved-result {:points 16 :sharpe 0.84})
          same (solved-result {:points 72 :sharpe 0.84})
          change (refinement/selection-change base same)]
      (is (= 0 (:weight-l1-delta change)))
      (is (false? (:material? change)))
      (is (= 0 (:sharpe-delta change)))))
  (testing "a large allocation move is material"
    (let [base (solved-result {:points 16 :sharpe 0.84
                               :weights {"perp:BTC" 0.6 "perp:ETH" 0.4}})
          refined (solved-result {:points 72 :sharpe 0.88
                                  :weights {"perp:BTC" 0.7 "perp:ETH" 0.3}})
          change (refinement/selection-change base refined)]
      (is (< 0.19 (:weight-l1-delta change) 0.21))
      (is (true? (:material? change)))
      (is (< 0.039 (:sharpe-delta change) 0.041))))
  (testing "a Sharpe-only move past threshold is material"
    (let [base (solved-result {:points 16 :sharpe 0.84})
          refined (solved-result {:points 72 :sharpe 0.88})]
      (is (true? (:material? (refinement/selection-change base refined))))))
  (testing "nil unless both solved"
    (is (nil? (refinement/selection-change {:status :infeasible}
                                           (solved-result {:points 72}))))))
