(ns hyperopen.portfolio.optimizer.application.engine-solver-diagnostics-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.engine.target-selection :as target-selection]))

(def infeasible-turnover-problem
  {:kind :quadratic-program
   :objective-kind :minimum-variance
   :instrument-ids ["perp:BTC" "perp:ETH"]
   :quadratic [[1 0]
               [0 1]]
   :linear [0 0]
   :equalities [{:code :net-exposure
                 :coefficients [1 1]
                 :target 1}]
   :inequalities []
   :l1-constraints [{:code :gross-exposure
                     :max 1}
                    {:code :turnover
                     :current-weights [20 -11.313329083687073]
                     :max 2}]
   :lower-bounds [0 0]
   :upper-bounds [0.5 0.5]})

(deftest target-selection-explains-solver-results-that-violate-constraints-test
  (let [result (target-selection/target-selection
                {:objective {:kind :minimum-variance}}
                {:strategy :single-qp}
                [{:status :solved
                  :solver :osqp
                  :weights [0 0]
                  :problem infeasible-turnover-problem}]
                [0 0]
                [[1 0]
                 [0 1]])
        violations (get-in result [:details :violations])
        messages (mapv :message violations)]
    (is (= :infeasible (:status result)))
    (is (= :solver-returned-invalid-solution (:reason result)))
    (is (= #{:solver-result-equality-violation
             :solver-result-turnover-violation}
           (set (map :code violations))))
    (is (some #(str/includes? % "net-exposure expected 1.0000")
              messages))
    (is (some #(str/includes? % "turnover limit 2.0000")
              messages))
    (is (= 1 (get-in result [:details :solver-result-count])))
    (is (= 1 (get-in result [:details :rejected-result-count])))))
