(ns hyperopen.order.outcome-option-sort-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.order.outcome-option-sort :as outcome-option-sort]))

(defn- extract-save-many-path-values
  [effects]
  (into {} (second (first effects))))

(deftest set-outcome-option-sort-persists-trade-ui-sort-state-test
  (let [first-click (extract-save-many-path-values
                     (outcome-option-sort/set-outcome-option-sort
                      {:trade-ui {}}
                      :chance))
        repeated-click (extract-save-many-path-values
                        (outcome-option-sort/set-outcome-option-sort
                         {:trade-ui {:outcome-option-sort-by :chance
                                      :outcome-option-sort-direction :desc}}
                         :chance))
        label-click (extract-save-many-path-values
                     (outcome-option-sort/set-outcome-option-sort
                      {:trade-ui {}}
                      :label))]
    (is (= :chance (get first-click [:trade-ui :outcome-option-sort-by])))
    (is (= :desc (get first-click [:trade-ui :outcome-option-sort-direction])))
    (is (= :asc (get repeated-click [:trade-ui :outcome-option-sort-direction])))
    (is (= :label (get label-click [:trade-ui :outcome-option-sort-by])))
    (is (= :asc (get label-click [:trade-ui :outcome-option-sort-direction])))))
