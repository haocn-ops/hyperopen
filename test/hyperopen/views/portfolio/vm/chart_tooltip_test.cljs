(ns hyperopen.views.portfolio.vm.chart-tooltip-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.portfolio.vm.chart-tooltip :as chart-tooltip]))

(deftest build-chart-hover-tooltip-formats-portfolio-values-and-benchmarks-test
  (let [day-tooltip (chart-tooltip/build-chart-hover-tooltip :day
                                                             :returns
                                                             {:point {:time-ms 1700000000000
                                                                      :value 14}
                                                              :index 1}
                                                             [{:id :strategy
                                                               :points [{:value 10}
                                                                        {:value 14}]}
                                                              {:id :benchmark-0
                                                               :coin "SPY"
                                                               :label "SPY (SPOT)"
                                                               :stroke "#f2cf66"
                                                               :points [{:value 5}
                                                                        {:value 12}]}])
        month-tooltip (chart-tooltip/build-chart-hover-tooltip :month
                                                               :pnl
                                                               {:point {:time-ms 1700000000000
                                                                        :value 203}
                                                                :index 0}
                                                               [])]
    (is (re-find #":" (:timestamp day-tooltip)))
    (is (= "Returns" (:metric-label day-tooltip)))
    (is (= "+14.00%" (:metric-value day-tooltip)))
    (is (= [{:coin "SPY"
             :label "SPY (SPOT)"
             :value "+12.00%"
             :stroke "#f2cf66"}]
           (:benchmark-values day-tooltip)))
    (is (not (re-find #":" (:timestamp month-tooltip))))
    (is (= "PNL" (:metric-label month-tooltip)))
    (is (= "$203" (:metric-value month-tooltip)))
    (is (= [] (:benchmark-values month-tooltip)))))
