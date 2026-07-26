(ns hyperopen.domain.trading.indicators.math-engine-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.domain.trading.indicators.flow.volume :as flow-volume]
            [hyperopen.domain.trading.indicators.math-engine :as math-engine]
            [hyperopen.domain.trading.indicators.oscillators.classic :as osc-classic]
            [hyperopen.domain.trading.indicators.trend.moving-averages :as trend-moving-averages]
            [hyperopen.domain.trading.indicators.trend.regression :as trend-regression]))

(def ^:private sample-candles
  [{:time 1 :open 100 :high 104 :low 98 :close 102 :volume 1000}
   {:time 2 :open 102 :high 106 :low 100 :close 105 :volume 1100}
   {:time 3 :open 105 :high 107 :low 103 :close 104 :volume 1200}])

(deftest with-math-engine-binds-wrapper-dispatch-test
  (let [fake-engine {:engine-id :fake}
        default-engine math-engine/*math-engine*
        seen (atom nil)]
    (with-redefs [math-engine/on-balance-volume* (fn [engine close-values volume-values]
                                                    (reset! seen {:engine engine
                                                                  :closes close-values
                                                                  :volumes volume-values})
                                                    [1 2 3])]
      (is (= [1 2 3]
             (math-engine/with-math-engine fake-engine
               #(math-engine/on-balance-volume [10 11] [100 90]))))
      (is (= fake-engine (:engine @seen)))
      (reset! seen nil)
      (is (= [1 2 3] (math-engine/on-balance-volume [10 11] [100 90])))
      (is (= default-engine (:engine @seen))))))

(deftest flow-volume-calculator-uses-math-engine-boundary-test
  (let [seen (atom nil)]
    (with-redefs [math-engine/on-balance-volume (fn [close-values volume-values]
                                                   (reset! seen {:closes close-values
                                                                 :volumes volume-values})
                                                   [nil 2.0 3.0])]
      (let [result (flow-volume/calculate-on-balance-volume sample-candles {})]
        (is (= {:closes (mapv :close sample-candles)
                :volumes (mapv :volume sample-candles)}
               @seen))
        (is (= [nil 2.0 3.0]
               (get-in result [:series 0 :values])))))))

(deftest oscillator-classic-calculator-uses-math-engine-boundary-test
  (let [seen (atom nil)]
    (with-redefs [math-engine/stochastic (fn [high-values low-values close-values opts]
                                           (reset! seen {:high high-values
                                                         :low low-values
                                                         :close close-values
                                                         :opts opts})
                                           {:k [20 30 40]
                                            :d [10 15 25]})]
      (let [result (osc-classic/calculate-stochastic sample-candles {})]
        (is (= {:high (mapv :high sample-candles)
                :low (mapv :low sample-candles)
                :close (mapv :close sample-candles)
                :opts {:k-period 14 :d-period 3}}
               @seen))
        (is (= [20 30 40]
               (get-in result [:series 0 :values])))
        (is (= [10 15 25]
               (get-in result [:series 1 :values])))))))

(deftest trend-moving-average-calculator-uses-math-engine-boundary-test
  (let [seen (atom nil)]
    (with-redefs [math-engine/ema (fn [close-values opts]
                                    (reset! seen {:closes close-values
                                                  :opts opts})
                                    [nil 101.0 102.0])]
      (let [result (trend-moving-averages/calculate-moving-average-exponential sample-candles {})]
        (is (= {:closes (mapv :close sample-candles)
                :opts {:period 20}}
               @seen))
        (is (= [nil 101.0 102.0]
               (get-in result [:series 0 :values])))))))

(deftest trend-regression-calculator-uses-math-engine-boundary-test
  (let [seen (atom nil)]
    (with-redefs [math-engine/moving-linear-regression (fn [period x-values y-values]
                                                          (reset! seen {:period period
                                                                        :x x-values
                                                                        :y y-values})
                                                          [nil 100.5 101.5])]
      (let [result (trend-regression/calculate-linear-regression-curve sample-candles {})]
        (is (= {:period 25
                :x [0 1 2]
                :y (mapv :close sample-candles)}
               @seen))
        (is (= [nil 100.5 101.5]
               (get-in result [:series 0 :values])))))))
