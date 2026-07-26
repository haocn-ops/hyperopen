(ns hyperopen.domain.trading.indicators.math-kernels-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.domain.trading.indicators.math-kernel-bench-baselines :as baselines]
            [hyperopen.domain.trading.indicators.math :as imath]
            [hyperopen.domain.trading.indicators.math.patterns :as patterns]
            [hyperopen.domain.trading.indicators.math.statistics :as mstats]))

(defn- approx=
  [a b]
  (and (number? a)
       (number? b)
       (< (js/Math.abs (- a b)) 1e-9)))

(defn- finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))
       (js/isFinite value)))

(defn- synthetic-close-values
  [n]
  (mapv (fn [idx]
          (+ 120
             (* 0.06 idx)
             (* 2.1 (js/Math.sin (/ idx 9)))
             (* 0.9 (js/Math.cos (/ idx 17)))))
        (range n)))

(defn- strict-bench-mode?
  []
  (= "1" (aget (.-env js/process) baselines/strict-benchmark-env-key)))

(deftest finite-subtract-and-band-kernels-test
  (is (= 5 (imath/finite-subtract 9 4)))
  (is (nil? (imath/finite-subtract js/NaN 4)))
  (is (= 14 (imath/band-upper-value 10 2 2)))
  (is (= 6 (imath/band-lower-value 10 2 2)))
  (is (= [14 nil 7]
         (imath/band-upper-values [10 5 1] [2 nil 3] 2)))
  (is (= [6 nil -5]
         (imath/band-lower-values [10 5 1] [2 nil 3] 2))))

(deftest safe-percent-ratio-and-ratio-kernels-test
  (is (= 0.25 (imath/finite-ratio 1 4)))
  (is (nil? (imath/finite-ratio 1 0)))
  (is (= 25 (imath/safe-percent-ratio 1 4)))
  (is (nil? (imath/safe-percent-ratio 3 0)))
  (is (nil? (imath/safe-percent-ratio js/NaN 3))))

(deftest true-range-kernels-test
  (let [highs [11 15 14]
        lows [9 10 8]
        closes [10 12 9]]
    (is (= 2 (imath/true-range-at 11 9 nil)))
    (is (= 5 (imath/true-range-index highs lows closes 1)))
    (is (= [2 5 6]
           (imath/true-range-values highs lows closes)))))

(deftest roc-and-hl2-kernels-test
  (let [close-values [100 110 121 108.9]
        roc-values (imath/roc-percent-values close-values 1)]
    (is (= nil (nth roc-values 0)))
    (is (approx= 10 (nth roc-values 1)))
    (is (approx= 10 (nth roc-values 2)))
    (is (approx= -10 (nth roc-values 3)))
    (is (nil? (imath/roc-percent-at [0 10] 1 1)))
    (is (= [8 12 16]
           (imath/hl2-values [10 14 20] [6 10 12])))))

(defn- benchmark-trend
  [kernel-id elapsed-ms]
  (let [baseline-ms (get-in baselines/kernel-benchmark-snapshot [:kernels kernel-id :baseline-ms])
        multiplier (:soft-threshold-multiplier baselines/kernel-benchmark-snapshot)
        threshold-ms (* baseline-ms multiplier)
        delta-pct (* 100 (/ (- elapsed-ms baseline-ms) baseline-ms))]
    (js/console.info (str "[kernel-bench] " (name kernel-id)
                          ": elapsed=" elapsed-ms "ms"
                          ", baseline=" baseline-ms "ms"
                          ", delta=" (.toFixed delta-pct 1) "%"))
    (when (> elapsed-ms threshold-ms)
      (js/console.warn (str "[kernel-bench] soft regression candidate for "
                            (name kernel-id)
                            ": elapsed="
                            elapsed-ms
                            "ms exceeds threshold="
                            threshold-ms
                            "ms")))
    {:threshold-ms threshold-ms
     :strict? (strict-bench-mode?)}))

(deftest rolling-regression-parity-linear-series-test
  (let [values [10 11 12 13 14]
        result (mstats/rolling-regression values 3)]
    (is (= [nil nil] (subvec result 0 2)))
    (is (approx= 1 (:slope (nth result 2))))
    (is (approx= 10 (:intercept (nth result 2))))
    (is (approx= 12 (:center (nth result 2))))
    (is (approx= 0 (:standard-error (nth result 2))))
    (is (approx= 1 (:slope (nth result 3))))
    (is (approx= 11 (:intercept (nth result 3))))
    (is (approx= 13 (:center (nth result 3))))
    (is (approx= 0 (:standard-error (nth result 3))))
    (is (approx= 1 (:slope (nth result 4))))
    (is (approx= 12 (:intercept (nth result 4))))
    (is (approx= 14 (:center (nth result 4))))
    (is (approx= 0 (:standard-error (nth result 4))))))

(deftest rolling-correlation-parity-monotonic-series-test
  (let [ascending [2 4 6 8 10]
        descending [10 8 6 4 2]
        corr-up (mstats/rolling-correlation-with-time ascending 3)
        corr-down (mstats/rolling-correlation-with-time descending 3)]
    (is (= [nil nil] (subvec corr-up 0 2)))
    (is (= [nil nil] (subvec corr-down 0 2)))
    (is (every? #(approx= 1 %) (subvec corr-up 2 5)))
    (is (every? #(approx= -1 %) (subvec corr-down 2 5)))))

(deftest zigzag-pivots-parity-simple-reversal-test
  (let [close-values [100 111 106 94 97 121]
        pivots (patterns/zigzag-pivots close-values 0.10)
        interpolated (patterns/interpolate-zigzag (count close-values) pivots)]
    (is (= [{:idx 0 :price 100}
            {:idx 1 :price 111}
            {:idx 3 :price 94}
            {:idx 5 :price 121}]
           pivots))
    (is (= [100 111 102.5 94 107.5 121] interpolated))))

(deftest rolling-regression-micro-bench-test
  (let [values (synthetic-close-values 5000)
        start-ms (js/Date.now)
        regressions (mstats/rolling-regression values 30)
        elapsed-ms (- (js/Date.now) start-ms)
        {:keys [threshold-ms strict?]} (benchmark-trend :rolling-regression elapsed-ms)
        realized (filter some? regressions)]
    (is (= 5000 (count regressions)))
    (is (seq realized))
    (is (every? finite-number? (map :slope realized)))
    (is (every? finite-number? (map :center realized)))
    (is (< elapsed-ms (:hard-limit-ms baselines/kernel-benchmark-snapshot))
        (str "rolling-regression took too long: " elapsed-ms "ms"))
    (when strict?
      (is (<= elapsed-ms threshold-ms)
          (str "rolling-regression exceeded strict trend threshold: "
               elapsed-ms
               "ms > "
               threshold-ms
               "ms")))))

(deftest rolling-correlation-micro-bench-test
  (let [values (synthetic-close-values 5000)
        start-ms (js/Date.now)
        correlations (mstats/rolling-correlation-with-time values 40)
        elapsed-ms (- (js/Date.now) start-ms)
        {:keys [threshold-ms strict?]} (benchmark-trend :rolling-correlation elapsed-ms)
        realized (filter some? correlations)]
    (is (= 5000 (count correlations)))
    (is (seq realized))
    (is (every? finite-number? realized))
    (is (< elapsed-ms (:hard-limit-ms baselines/kernel-benchmark-snapshot))
        (str "rolling-correlation-with-time took too long: " elapsed-ms "ms"))
    (when strict?
      (is (<= elapsed-ms threshold-ms)
          (str "rolling-correlation-with-time exceeded strict trend threshold: "
               elapsed-ms
               "ms > "
               threshold-ms
               "ms")))))

(deftest zigzag-pivots-micro-bench-test
  (let [values (synthetic-close-values 6000)
        start-ms (js/Date.now)
        pivots (patterns/zigzag-pivots values 0.03)
        elapsed-ms (- (js/Date.now) start-ms)
        {:keys [threshold-ms strict?]} (benchmark-trend :zigzag-pivots elapsed-ms)]
    (is (seq pivots))
    (is (every? map? pivots))
    (is (every? #(contains? % :idx) pivots))
    (is (every? #(contains? % :price) pivots))
    (is (< elapsed-ms (:hard-limit-ms baselines/kernel-benchmark-snapshot))
        (str "zigzag-pivots took too long: " elapsed-ms "ms"))
    (when strict?
      (is (<= elapsed-ms threshold-ms)
          (str "zigzag-pivots exceeded strict trend threshold: "
               elapsed-ms
               "ms > "
               threshold-ms
               "ms")))))
