(ns hyperopen.portfolio.optimizer.application.refinement-view-model-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.optimizer.application.view-model.refinement :as refinement-vm]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(defn- result
  [{:keys [points strategy sharpe weights]
    :or {strategy :frontier-sweep sharpe 0.84}}]
  {:status :solved
   :solver {:strategy strategy :objective-kind :minimum-variance}
   :frontier-summary {:point-count points}
   :diagnostics {:weight-sensitivity-by-instrument {}}
   :warnings []
   :performance {:in-sample-sharpe sharpe}
   :expected-return 0.1
   :volatility 0.12
   :target-weights-by-instrument (or weights {"perp:BTC" 0.6 "perp:ETH" 0.4})})

(deftest draft-result-model-test
  (let [model (refinement-vm/refinement-model
               {:state (-> {}
                           (assoc-in contracts/refinement-path
                                     {:active? false :depth nil
                                      :requested-points nil :baseline-result nil}))
                :result (result {:points 16})
                :run-state {:status :succeeded}
                :running? false
                :progress {:status :succeeded :started-at-ms 1000 :completed-at-ms 4800
                           :overall-percent 100 :active-step nil}})]
    (is (true? (:solved? model)))
    (is (true? (:can-refine? model)))
    (is (false? (:in-flight? model)))
    (is (= :thorough (:depth model)))
    (is (= 3 (count (:depth-options model))))
    (is (= [56 72 80] (mapv :points (:depth-options model))))
    (is (true? (:selected? (second (:depth-options model)))))
    (is (= :draft (get-in model [:assessment :tier])))
    (is (= 3800 (:runtime-ms model)))
    (is (nil? (:outcome model)))))

(deftest in-flight-model-test
  (let [model (refinement-vm/refinement-model
               {:state (assoc-in {} contracts/refinement-path
                                 {:active? true :depth :thorough
                                  :requested-points 72 :baseline-result (result {:points 16})})
                :result (result {:points 16})
                :run-state {:status :running}
                :running? true
                :progress {:status :running :overall-percent 40 :active-step :solve}})]
    (is (true? (:in-flight? model)))
    (is (false? (:can-refine? model)))
    (is (= 40 (get-in model [:progress :overall-percent])))
    (testing "outcome is withheld while a refinement is still running"
      (is (nil? (:outcome model))))))

(deftest refined-outcome-model-test
  (let [baseline (result {:points 16 :sharpe 0.84 :weights {"perp:BTC" 0.6 "perp:ETH" 0.4}})
        refined (result {:points 72 :sharpe 0.9 :weights {"perp:BTC" 0.75 "perp:ETH" 0.25}})
        model (refinement-vm/refinement-model
               {:state (assoc-in {} contracts/refinement-path
                                 {:active? false :depth :thorough
                                  :requested-points 72 :baseline-result baseline})
                :result refined
                :run-state {:status :succeeded}
                :running? false
                :progress {:status :succeeded :started-at-ms 0 :completed-at-ms 12000}})]
    (is (= :refined (get-in model [:assessment :tier])))
    (is (some? (:outcome model)))
    (is (true? (get-in model [:outcome :material?])))
    (is (= :thorough (get-in model [:outcome :depth])))
    (is (false? (get-in model [:outcome :exact-selection?])))))
