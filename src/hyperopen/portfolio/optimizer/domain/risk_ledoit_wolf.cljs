(ns hyperopen.portfolio.optimizer.domain.risk-ledoit-wolf
  "Ledoit-Wolf shrinkage toward a scaled-identity target.

  The hot path runs in flat JS loops instead of persistent-vector matrix
  helpers: the original built one n-by-n outer-product matrix PER OBSERVATION
  for the beta estimate, which cost seconds for a mid-size universe. The loop
  order below reproduces the original arithmetic exactly - same operations,
  same left-to-right row-major summation - so results are bit-identical to
  the persistent-vector implementation; only the container types changed."
  (:require [hyperopen.portfolio.optimizer.domain.math :as math]))

(defn- zero-matrix
  [size]
  (vec (repeat size
               (vec (repeat size 0)))))

(defn- rectangular-series?
  [series]
  (or (empty? series)
      (let [sample-count (count (first series))]
        (every? #(= sample-count (count %)) series))))

(defn- centered-columns
  "JS array of n Float64Arrays (one per instrument, length t-count) holding the
  mean-subtracted observations."
  [series means t-count]
  (let [n (count series)
        columns (js/Array. n)]
    (dotimes [i n]
      (let [xs (vec (nth series i))
            m (nth means i)
            column (js/Float64Array. t-count)]
        (dotimes [t t-count]
          (aset column t (- (nth xs t) m)))
        (aset columns i column)))
    columns))

(defn- sample-covariance-array
  "n*n row-major Float64Array of the (1/T)-scaled sample covariance. Each
  element sums c_i[t]*c_j[t] ascending in t then scales, matching the original
  mat-mul + scalar-matrix order."
  [columns n t-count]
  (let [q (/ 1 t-count)
        s (js/Float64Array. (* n n))]
    (dotimes [i n]
      (let [ci (aget columns i)]
        (dotimes [j n]
          (let [cj (aget columns j)]
            (loop [t 0
                   acc 0]
              (if (< t t-count)
                (recur (inc t) (+ acc (* (aget ci t) (aget cj t))))
                (aset s (+ (* i n) j) (* q acc))))))))
    s))

(defn- beta-sample-terms
  "Per-observation squared Frobenius distance between the observation's outer
  product and the sample covariance, summed row-major like the original
  frobenius-squared over matrix-difference (d = x_i*x_j + (-1 * s_ij))."
  [columns s n t-count]
  (loop [t 0
         terms (transient [])]
    (if (< t t-count)
      (recur (inc t)
             (conj! terms
                    (loop [i 0
                           acc 0]
                      (if (< i n)
                        (let [xi (aget (aget columns i) t)
                              acc* (loop [j 0
                                          acc* acc]
                                     (if (< j n)
                                       (let [d (+ (* xi (aget (aget columns j) t))
                                                  (* -1 (aget s (+ (* i n) j))))]
                                         (recur (inc j) (+ acc* (* d d))))
                                       acc*))]
                          (recur (inc i) acc*))
                        acc))))
      (persistent! terms))))

(defn- delta-hat-value
  "Squared Frobenius distance between the sample and the scaled-identity
  target, summed row-major (d = s_ij + (-1 * t_ij))."
  [s mu n]
  (loop [i 0
         acc 0]
    (if (< i n)
      (recur (inc i)
             (loop [j 0
                    acc* acc]
               (if (< j n)
                 (let [t-ij (if (= i j) (* mu 1) (* mu 0))
                       d (+ (aget s (+ (* i n) j)) (* -1 t-ij))]
                   (recur (inc j) (+ acc* (* d d))))
                 acc*)))
      acc)))

(defn estimate
  [{:keys [series periods-per-year]}]
  (let [feature-count (count series)
        sample-count (if (seq series)
                       (count (first series))
                       0)
        periods-per-year* (or periods-per-year 1)]
    (if (and (pos? feature-count)
             (pos? sample-count)
             (rectangular-series? series))
      (let [n feature-count
            means (mapv #(or (math/mean %) 0) series)
            columns (centered-columns series means sample-count)
            s (sample-covariance-array columns n sample-count)
            trace (loop [i 0
                         acc 0]
                    (if (< i n)
                      (recur (inc i) (+ acc (aget s (+ (* i n) i))))
                      acc))
            mu (if (pos? n) (/ trace n) 0)
            beta-hat (/ (or (math/mean (beta-sample-terms columns s n sample-count)) 0)
                        sample-count)
            delta-hat (delta-hat-value s mu n)
            shrinkage (if (pos? delta-hat)
                        (-> (/ beta-hat delta-hat)
                            (max 0)
                            (min 1))
                        0)
            residual (- 1 shrinkage)
            covariance (mapv (fn [i]
                               (mapv (fn [j]
                                       (let [t-ij (if (= i j) (* mu 1) (* mu 0))
                                             s-ij (aget s (+ (* i n) j))]
                                         (* periods-per-year*
                                            (+ (* shrinkage t-ij)
                                               (* residual s-ij)))))
                                     (range n)))
                             (range n))]
        {:covariance covariance
         :shrinkage {:kind :ledoit-wolf
                     :target :scaled-identity
                     :shrinkage shrinkage}
         :sample-count sample-count
         :feature-count feature-count})
      {:covariance (zero-matrix feature-count)
       :shrinkage {:kind :ledoit-wolf
                   :target :scaled-identity
                   :shrinkage 0}
       :sample-count sample-count
       :feature-count feature-count})))
