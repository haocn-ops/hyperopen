(ns hyperopen.ui.table.sort-kernel-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.ui.table.sort-kernel :as sort-kernel]))

(deftest resolve-column-accessor-prefers-column-mapping-and-fallbacks-test
  (let [accessor-by-column {"Coin" (fn [row] (:coin row))}
        fallback (fn [row] (:fallback row))
        mapped-accessor (sort-kernel/resolve-column-accessor "Coin" accessor-by-column fallback)
        default-accessor (sort-kernel/resolve-column-accessor "Missing" accessor-by-column)
        custom-fallback (sort-kernel/resolve-column-accessor "Missing"
                                                             accessor-by-column
                                                             fallback)]
    (is (= "ETH" (mapped-accessor {:coin "ETH" :fallback "BTC"})))
    (is (= 0 (default-accessor {:coin "ETH"})))
    (is (= 42 (custom-fallback {:fallback 42})))))

(deftest sort-rows-by-column-applies-direction-and-tie-breaker-test
  (let [rows [{:id "b" :value 1}
              {:id "a" :value 1}
              {:id "c" :value 2}]
        options {:column "Value"
                 :accessor-by-column {"Value" (fn [row] (:value row))}
                 :tie-breaker :id}
        asc (sort-kernel/sort-rows-by-column rows (assoc options :direction :asc))
        desc (sort-kernel/sort-rows-by-column rows (assoc options :direction :desc))]
    (is (= ["a" "b" "c"] (mapv :id asc)))
    (is (= ["c" "b" "a"] (mapv :id desc)))))

(deftest sort-rows-by-column-with-equal-keys-keeps-stable-order-without-tie-breaker-test
  (let [rows [{:id "first" :value 1}
              {:id "second" :value 1}
              {:id "third" :value 1}]
        sorted (sort-kernel/sort-rows-by-column rows
                                                {:column "Value"
                                                 :direction :asc
                                                 :accessor-by-column {"Value" (fn [row] (:value row))}})]
    (is (= ["first" "second" "third"] (mapv :id sorted)))))

(deftest sort-rows-by-column-default-fallback-keeps-stable-order-when-column-missing-test
  (let [rows [{:id "first"}
              {:id "second"}
              {:id "third"}]
        sorted (sort-kernel/sort-rows-by-column rows
                                                {:column "Missing"
                                                 :direction :asc
                                                 :accessor-by-column {"Value" :value}})]
    (is (= ["first" "second" "third"] (mapv :id sorted)))))
