(ns hyperopen.portfolio.optimizer.infrastructure.solver-adapter-modules-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.infrastructure.fallback :as fallback]
            [hyperopen.portfolio.optimizer.infrastructure.osqp :as osqp]
            [hyperopen.portfolio.optimizer.infrastructure.problem-adapter :as problem-adapter]))

(defn- typed-array->vec
  [values]
  (vec (js->clj (js/Array.from values))))

(def split-turnover-problem
  {:kind :quadratic-program
   :objective-kind :return-tilted
   :instrument-ids ["A" "B"]
   :quadratic [[1 0]
               [0 1]]
   :linear [-1 1]
   :equalities [{:code :net-exposure
                 :coefficients [1 1]
                 :target 0}]
   :inequalities []
   :l1-constraints [{:code :gross-exposure
                     :max 1
                     :requires-split-variables? true}
                    {:code :turnover
                     :max 0.5
                     :current-weights [0.1 -0.1]
                     :requires-split-variables? true}]
   :lower-bounds [-1 -1]
   :upper-bounds [1 1]})

(deftest problem-adapter-expands-and-decodes-l1-split-variables-test
  (let [{adapted-problem :problem decode :decode} (problem-adapter/adapt-problem
                                                   split-turnover-problem)]
    (is (= ["A:positive"
            "B:positive"
            "A:negative"
            "B:negative"
            "A:turnover-positive"
            "B:turnover-positive"
            "A:turnover-negative"
            "B:turnover-negative"]
           (:instrument-ids adapted-problem)))
    (is (= 8 (count (:quadratic adapted-problem))))
    (is (= 8 (count (:linear adapted-problem))))
    (is (= 3 (count (:equalities adapted-problem))))
    (is (= 6 (count (:inequalities adapted-problem))))
    (is (= [] (:l1-constraints adapted-problem)))
    (is (= (repeat 8 0) (:lower-bounds adapted-problem)))
    (is (= (repeat 8 nil) (:upper-bounds adapted-problem)))
    (is (= [0.5 -0.25]
           (decode [0.5 0 0 0.25 0 0 0 0])))))

(deftest osqp-dense-to-csc-converts-column-major-nonzero-values-test
  (let [csc (osqp/dense->csc [[1 0 2]
                              [0 3 0]
                              [4 0 5]]
                             {:upper-triangle? false})]
    (is (= [1 4 3 2 5] (typed-array->vec (.-data csc))))
    (is (= [0 2 1 0 2] (typed-array->vec (.-row_indices csc))))
    (is (= [0 2 3 5] (typed-array->vec (.-column_pointers csc))))))

(deftest osqp-dense-to-csc-can-filter-to-upper-triangle-test
  (let [csc (osqp/dense->csc [[1 0 2]
                              [0 3 0]
                              [4 0 5]]
                             {:upper-triangle? true})]
    (is (= [1 3 2 5] (typed-array->vec (.-data csc))))
    (is (= [0 1 0 2] (typed-array->vec (.-row_indices csc))))
    (is (= [0 1 2 4] (typed-array->vec (.-column_pointers csc))))))

(deftest fallback-relabels-solved-quadprog-recovery-test
  (let [result (fallback/recover-osqp-error
                split-turnover-problem
                (js/Error. "setup failed")
                (fn [_problem]
                  {:status :solved
                   :solver :quadprog
                   :weights [0.5 -0.5]}))]
    (is (= :solved (:status result)))
    (is (= :quadprog-fallback (:solver result)))
    (is (= :osqp (:fallback-from result)))
    (is (= :solver-error (:fallback-reason result)))
    (is (= "Error: setup failed" (:fallback-message result)))))

(deftest fallback-preserves-unsolved-quadprog-recovery-in-osqp-error-test
  (let [fallback-result {:status :unsupported
                         :reason :invalid-l1-constraints}
        result (fallback/recover-osqp-error
                split-turnover-problem
                "setup failed"
                (fn [_problem]
                  fallback-result))]
    (is (= :error (:status result)))
    (is (= :osqp (:solver result)))
    (is (= :solver-error (:reason result)))
    (is (= "setup failed" (:message result)))
    (is (= fallback-result (:fallback-result result)))))
