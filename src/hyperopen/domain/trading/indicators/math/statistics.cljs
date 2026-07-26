(ns hyperopen.domain.trading.indicators.math.statistics
  (:require [hyperopen.domain.trading.indicators.math :as imath]))

(def ^:private finite-number? imath/finite-number?)

(defn tie-aware-ranks
  [values]
  (let [indexed (map-indexed vector values)
        sorted (vec (sort-by second indexed))
        size (count sorted)]
    (loop [idx 0
           ranks (vec (repeat size nil))]
      (if (= idx size)
        ranks
        (let [value (second (nth sorted idx))
              same-run-end (loop [j idx]
                             (if (and (< (inc j) size)
                                      (= value (second (nth sorted (inc j)))))
                               (recur (inc j))
                               j))
              avg-rank (/ (+ (inc idx) (inc same-run-end)) 2)
              updated (reduce (fn [acc k]
                                (let [orig-idx (first (nth sorted k))]
                                  (assoc acc orig-idx avg-rank)))
                              ranks
                              (range idx (inc same-run-end)))]
          (recur (inc same-run-end) updated))))))

(defn pearson-correlation
  [xs ys]
  (when (and (= (count xs) (count ys))
             (seq xs)
             (every? finite-number? xs)
             (every? finite-number? ys))
    (let [mx (imath/mean xs)
          my (imath/mean ys)
          cov (reduce + 0 (map (fn [x y]
                                 (* (- x mx) (- y my)))
                               xs ys))
          sx (reduce + 0 (map (fn [x]
                                (let [d (- x mx)]
                                  (* d d)))
                              xs))
          sy (reduce + 0 (map (fn [y]
                                (let [d (- y my)]
                                  (* d d)))
                              ys))
          denom (js/Math.sqrt (* sx sy))]
      (when (and (finite-number? denom) (> denom 0))
        (/ cov denom)))))

(defn rolling-correlation-with-time
  [values period]
  (let [time-axis (vec (range 1 (inc period)))
        size (count values)]
    (mapv (fn [idx]
            (when-let [window (imath/window-for-index values idx period :aligned)]
              (pearson-correlation window time-axis)))
          (range size))))

(defn rolling-regression
  [values period]
  (let [size (count values)]
    (mapv (fn [idx]
            (when-let [window (imath/window-for-index values idx period :aligned)]
              (when (every? finite-number? window)
                (let [x-values (vec (range period))
                      x-mean (/ (reduce + 0 x-values) period)
                      y-mean (imath/mean window)
                      sxx (reduce + 0 (map (fn [x]
                                             (let [dx (- x x-mean)]
                                               (* dx dx)))
                                           x-values))
                      sxy (reduce + 0 (map (fn [x y]
                                             (* (- x x-mean)
                                                (- y y-mean)))
                                           x-values window))
                      slope (if (zero? sxx) 0 (/ sxy sxx))
                      intercept (- y-mean (* slope x-mean))
                      residuals (map (fn [x y]
                                       (- y (+ intercept (* slope x))))
                                     x-values window)
                      rss (reduce + 0 (map #(* % %) residuals))
                      denom (max 1 (- period 2))]
                  {:slope slope
                   :intercept intercept
                   :standard-error (js/Math.sqrt (/ rss denom))
                   :center (+ intercept (* slope (dec period)))}))))
          (range size))))
