(ns hyperopen.views.trading-chart.utils.chart-interop.transforms-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.trading-chart.utils.chart-interop :as chart-interop]
            [hyperopen.views.trading-chart.utils.chart-interop.transforms :as transforms]))

(deftest transform-data-for-heikin-ashi-computes-deterministic-candles-test
  (let [raw-candles [{:time 1 :open 10 :high 15 :low 8 :close 12}
                     {:time 2 :open 12 :high 16 :low 11 :close 14}]
        transformed (chart-interop/transform-data-for-heikin-ashi raw-candles)]
    (is (= 2 (count transformed)))
    (let [first-candle (first transformed)
          second-candle (second transformed)]
      (is (= {:time 1
              :open 11
              :high 15
              :low 8
              :close 11.25}
             first-candle))
      (is (= 11.125 (:open second-candle)))
      (is (= 13.25 (:close second-candle)))
      (is (= 16 (:high second-candle)))
      (is (= 11 (:low second-candle))))))

(deftest transform-data-for-columns-adds-directional-color-test
  (let [raw-candles [{:time 1 :open 10 :high 11 :low 9 :close 12}
                     {:time 2 :open 12 :high 13 :low 11 :close 10}]
        transformed (vec (chart-interop/transform-data-for-columns raw-candles))]
    (is (= [{:time 1 :value 12 :color "#26a69a"}
            {:time 2 :value 10 :color "#ef5350"}]
           transformed))))

(deftest transform-data-for-high-low-builds-floating-range-bars-test
  (let [raw-candles [{:time 1 :open 10 :high 16 :low 8 :close 12}
                     {:time 2 :open 12 :high 18 :low 10 :close 16}]
        transformed (vec (chart-interop/transform-data-for-high-low raw-candles))]
    (is (= [{:time 1 :open 8 :high 16 :low 8 :close 16}
            {:time 2 :open 10 :high 18 :low 10 :close 18}]
           transformed))))

(deftest derive-next-main-series-data-updates-line-tail-test
  (let [previous-candles [{:time 1 :open 10 :high 12 :low 9 :close 11}
                          {:time 2 :open 11 :high 13 :low 10 :close 12}
                          {:time 3 :open 12 :high 14 :low 11 :close 13}]
        previous-transformed (vec (transforms/transform-data-for-close previous-candles))
        next-candles [{:time 1 :open 10 :high 12 :low 9 :close 11}
                      {:time 2 :open 11 :high 13 :low 10 :close 12}
                      {:time 3 :open 12 :high 15 :low 11 :close 14}]
        expected (vec (transforms/transform-data-for-close next-candles))
        derived (transforms/derive-next-main-series-data previous-candles
                                                         previous-transformed
                                                         next-candles
                                                         :line
                                                         :update-last)]
    (is (= expected derived))
    (is (= (subvec previous-transformed 0 2)
           (subvec derived 0 2)))))

(deftest derive-next-main-series-data-updates-heikin-ashi-tail-test
  (let [previous-candles [{:time 1 :open 10 :high 15 :low 8 :close 12}
                          {:time 2 :open 12 :high 16 :low 11 :close 14}
                          {:time 3 :open 14 :high 18 :low 13 :close 17}]
        previous-transformed (vec (transforms/transform-data-for-heikin-ashi previous-candles))
        next-candles [{:time 1 :open 10 :high 15 :low 8 :close 12}
                      {:time 2 :open 12 :high 16 :low 11 :close 14}
                      {:time 3 :open 14 :high 19 :low 12 :close 18}]
        expected (vec (transforms/transform-data-for-heikin-ashi next-candles))
        derived (transforms/derive-next-main-series-data previous-candles
                                                         previous-transformed
                                                         next-candles
                                                         :heikin-ashi
                                                         :update-last)]
    (is (= expected derived))
    (is (= (subvec previous-transformed 0 2)
           (subvec derived 0 2)))))

(deftest derive-next-main-series-data-appends-heikin-ashi-tail-test
  (let [previous-candles [{:time 1 :open 10 :high 15 :low 8 :close 12}
                          {:time 2 :open 12 :high 16 :low 11 :close 14}]
        previous-transformed (vec (transforms/transform-data-for-heikin-ashi previous-candles))
        next-candles [{:time 1 :open 10 :high 15 :low 8 :close 12}
                      {:time 2 :open 12 :high 16 :low 11 :close 14}
                      {:time 3 :open 14 :high 19 :low 13 :close 18}]
        expected (vec (transforms/transform-data-for-heikin-ashi next-candles))
        derived (transforms/derive-next-main-series-data previous-candles
                                                         previous-transformed
                                                         next-candles
                                                         :heikin-ashi
                                                         :append-last)]
    (is (= expected derived))
    (is (= previous-transformed
           (subvec derived 0 (count previous-transformed))))))

(deftest derive-next-volume-data-reuses-tail-operations-test
  (let [previous-candles [{:time 1 :open 10 :high 12 :low 9 :close 11 :volume 100}
                          {:time 2 :open 11 :high 13 :low 10 :close 12 :volume 125}]
        previous-transformed (vec (transforms/transform-data-for-volume previous-candles))
        update-candles [{:time 1 :open 10 :high 12 :low 9 :close 11 :volume 100}
                        {:time 2 :open 11 :high 13 :low 10 :close 10 :volume 140}]
        append-candles [{:time 1 :open 10 :high 12 :low 9 :close 11 :volume 100}
                        {:time 2 :open 11 :high 13 :low 10 :close 12 :volume 125}
                        {:time 3 :open 12 :high 14 :low 11 :close 13 :volume 160}]
        derived-update (transforms/derive-next-volume-data previous-candles
                                                           previous-transformed
                                                           update-candles
                                                           :update-last)
        derived-append (transforms/derive-next-volume-data previous-candles
                                                           previous-transformed
                                                           append-candles
                                                           :append-last)]
    (is (= (vec (transforms/transform-data-for-volume update-candles))
           derived-update))
    (is (= (vec (transforms/transform-data-for-volume append-candles))
           derived-append))))

(deftest derive-next-series-data-returns-nil-when-tail-shape-is-invalid-test
  (let [previous-candles [{:time 1 :open 10 :high 12 :low 9 :close 11 :volume 100}]
        previous-transformed (vec (transforms/transform-data-for-close previous-candles))
        next-candles [{:time 1 :open 10 :high 12 :low 9 :close 11 :volume 100}
                      {:time 2 :open 11 :high 13 :low 10 :close 12 :volume 125}]]
    (is (nil? (transforms/derive-next-main-series-data previous-candles
                                                       previous-transformed
                                                       next-candles
                                                       :line
                                                       :update-last)))
    (is (nil? (transforms/derive-next-volume-data previous-candles
                                                  []
                                                  next-candles
                                                  :append-last)))))
