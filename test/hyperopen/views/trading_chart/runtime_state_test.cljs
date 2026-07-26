(ns hyperopen.views.trading-chart.runtime-state-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.trading-chart.runtime-state :as runtime-state]))

(deftest runtime-state-assoc-get-and-clear-test
  (let [node #js {}]
    (is (= {} (runtime-state/get-state node)))
    (runtime-state/assoc-state! node :chart-type :candlestick :visible-range-restore-tried? false)
    (is (= {:chart-type :candlestick
            :visible-range-restore-tried? false}
           (runtime-state/get-state node)))
    (runtime-state/assoc-state! node :visible-range-restore-tried? true)
    (is (= {:chart-type :candlestick
            :visible-range-restore-tried? true}
           (runtime-state/get-state node)))
    (runtime-state/clear-state! node)
    (is (= {} (runtime-state/get-state node)))))

(deftest runtime-state-isolated-per-node-test
  (let [node-a #js {}
        node-b #js {}]
    (runtime-state/assoc-state! node-a :chart-type :line)
    (runtime-state/assoc-state! node-b :chart-type :baseline)
    (is (= :line (:chart-type (runtime-state/get-state node-a))))
    (is (= :baseline (:chart-type (runtime-state/get-state node-b))))
    (runtime-state/clear-state! node-a)
    (runtime-state/clear-state! node-b)))
