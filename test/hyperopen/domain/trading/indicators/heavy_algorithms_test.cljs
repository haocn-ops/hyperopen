(ns hyperopen.domain.trading.indicators.heavy-algorithms-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.domain.trading.indicators.math :as imath]
            [hyperopen.domain.trading.indicators.math.patterns :as patterns]
            [hyperopen.domain.trading.indicators.math.statistics :as mstats]))

(defn- finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))
       (js/isFinite value)))

(defn- synthetic-close-values
  [n]
  (mapv (fn [idx]
          (+ 100
             (* 0.07 idx)
             (* 2.5 (js/Math.sin (/ idx 7)))
             (* 1.1 (js/Math.cos (/ idx 13)))))
        (range n)))

(deftest shared-kernels-determinism-and-performance-test
  (let [close-values (synthetic-close-values 5000)
        highs (mapv #(+ % 1.8) close-values)
        lows (mapv #(- % 1.4) close-values)
        start-ms (js/Date.now)
        tr-a (imath/true-range-values highs lows close-values)
        tr-b (imath/true-range-values highs lows close-values)
        roc-a (imath/roc-percent-values close-values 12)
        roc-b (imath/roc-percent-values close-values 12)
        elapsed-ms (- (js/Date.now) start-ms)]
    (is (= tr-a tr-b))
    (is (= roc-a roc-b))
    (is (= (count close-values) (count tr-a)))
    (is (= (count close-values) (count roc-a)))
    (is (every? finite-number? tr-a))
    (is (every? #(or (nil? %) (finite-number? %)) roc-a))
    (is (< elapsed-ms 8000)
        (str "shared kernels took too long: " elapsed-ms "ms"))))

(deftest tie-aware-ranks-determinism-and-performance-test
  (let [values (synthetic-close-values 2500)
        start-ms (js/Date.now)
        result-a (mstats/tie-aware-ranks values)
        result-b (mstats/tie-aware-ranks values)
        elapsed-ms (- (js/Date.now) start-ms)]
    (is (= result-a result-b))
    (is (= (count values) (count result-a)))
    (is (every? finite-number? result-a))
    (is (< elapsed-ms 8000)
        (str "tie-aware-ranks took too long: " elapsed-ms "ms"))))

(deftest zigzag-pivots-determinism-and-performance-test
  (let [close-values (synthetic-close-values 4000)
        start-ms (js/Date.now)
        pivots-a (patterns/zigzag-pivots close-values 0.03)
        pivots-b (patterns/zigzag-pivots close-values 0.03)
        elapsed-ms (- (js/Date.now) start-ms)]
    (is (= pivots-a pivots-b))
    (is (seq pivots-a))
    (is (every? map? pivots-a))
    (is (every? #(contains? % :idx) pivots-a))
    (is (every? #(contains? % :price) pivots-a))
    (is (< elapsed-ms 8000)
        (str "zigzag-pivots took too long: " elapsed-ms "ms"))))

(deftest rolling-regression-determinism-and-performance-test
  (let [close-values (synthetic-close-values 3000)
        start-ms (js/Date.now)
        regressions-a (mstats/rolling-regression close-values 30)
        regressions-b (mstats/rolling-regression close-values 30)
        elapsed-ms (- (js/Date.now) start-ms)
        realized (filter some? regressions-a)]
    (is (= regressions-a regressions-b))
    (is (seq realized))
    (is (every? #(contains? % :slope) realized))
    (is (every? #(contains? % :intercept) realized))
    (is (every? #(contains? % :standard-error) realized))
    (is (every? #(contains? % :center) realized))
    (is (every? finite-number? (map :slope realized)))
    (is (< elapsed-ms 8000)
        (str "rs-rolling took too long: " elapsed-ms "ms"))))
