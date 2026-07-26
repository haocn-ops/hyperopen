(ns hyperopen.portfolio.optimizer.refinement-actions-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.optimizer.actions.refinement :as refinement-actions]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.defaults :as defaults]))

(def solved-result
  {:status :solved
   :target-weights-by-instrument {"perp:BTC" 0.6 "perp:ETH" 0.4}
   :frontier-summary {:point-count 16}
   :solver {:strategy :frontier-sweep}})

(defn- state-with
  [{:keys [result run-status progress-status refinement depth]
    :or {run-status :succeeded progress-status :idle}}]
  (-> {}
      (assoc-in contracts/last-successful-run-result-path result)
      (assoc-in contracts/run-state-status-path run-status)
      (assoc-in contracts/optimization-progress-status-path progress-status)
      (assoc-in contracts/refinement-path (or refinement (defaults/default-refinement-state)))
      (assoc-in contracts/ui-refinement-depth-path depth)))

(deftest set-depth-test
  (is (= [[:effects/save contracts/ui-refinement-depth-path :quick]]
         (refinement-actions/set-portfolio-optimizer-refinement-depth {} "quick")))
  (is (= [[:effects/save contracts/ui-refinement-depth-path :maximum]]
         (refinement-actions/set-portfolio-optimizer-refinement-depth {} :maximum)))
  (is (= [] (refinement-actions/set-portfolio-optimizer-refinement-depth {} :bogus))))

(deftest refine-snapshots-baseline-and-runs-test
  (testing "refine snapshots the result, raises the budget, and runs the pipeline"
    (is (= [[:effects/save-many
             [[contracts/refinement-active-path true]
              [contracts/refinement-depth-path :thorough]
              [contracts/refinement-requested-points-path 72]
              [contracts/refinement-baseline-result-path solved-result]]]
            [:effects/run-portfolio-optimizer-pipeline]]
           (refinement-actions/refine-portfolio-optimizer
            (state-with {:result solved-result})))))
  (testing "refine honors the selected depth"
    (is (= 56 (-> (refinement-actions/refine-portfolio-optimizer
                   (state-with {:result solved-result :depth :quick}))
                  first second
                  (->> (some (fn [[p v]] (when (= p contracts/refinement-requested-points-path) v))))))))
  (testing "refine is a no-op without a solved result or while running"
    (is (= [] (refinement-actions/refine-portfolio-optimizer (state-with {:result nil}))))
    (is (= [] (refinement-actions/refine-portfolio-optimizer
               (state-with {:result solved-result :run-status :running}))))))

(deftest stop-refinement-test
  (testing "stop resets refinement and returns to the retained result"
    (is (= [[:effects/save-many
             [[contracts/refinement-path (defaults/default-refinement-state)]
              [contracts/run-state-status-path :succeeded]
              [contracts/optimization-progress-status-path :idle]]]]
           (refinement-actions/stop-portfolio-optimizer-refinement
            (state-with {:result solved-result
                         :refinement {:active? true :depth :thorough
                                      :requested-points 72 :baseline-result solved-result}
                         :run-status :running :progress-status :running}))))
    (is (= [] (refinement-actions/stop-portfolio-optimizer-refinement
               (state-with {:result solved-result}))))))
