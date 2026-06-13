(ns hyperopen.portfolio.optimizer.domain.linear-solve
  "Pure linear-algebra primitives for the closed-form optimizer path: a
  multiple-right-hand-side Gaussian elimination with partial pivoting and a
  componentwise residual (backward-error) check. Never forms an explicit
  matrix inverse and has no browser or solver dependencies."
  (:require [hyperopen.portfolio.optimizer.domain.math :as math]))

(def ^:private finite-number? math/finite-number?)

(def tolerances
  "Numeric tolerances local to the linear solve."
  {:pivot 1.0e-12
   :residual 1.0e-6})

(defn max-abs-entry
  [matrix]
  (reduce (fn [acc row]
            (reduce (fn [row-acc value]
                      (max row-acc (js/Math.abs value)))
                    acc
                    row))
          0
          matrix))

(defn- augmented-rows
  [matrix rhs-columns]
  (mapv (fn [idx row]
          (into (vec row)
                (map #(nth % idx))
                rhs-columns))
        (range (count matrix))
        matrix))

(defn- pivot-row-index
  [rows col n]
  (reduce (fn [best row-idx]
            (if (> (js/Math.abs (get-in rows [row-idx col]))
                   (js/Math.abs (get-in rows [best col])))
              row-idx
              best))
          col
          (range (inc col) n)))

(defn- eliminate-rows
  [matrix rhs-columns pivot-floor]
  (let [n (count matrix)]
    (loop [col 0
           rows (augmented-rows matrix rhs-columns)]
      (if (= col n)
        {:status :solved
         :rows rows}
        (let [pivot-row (pivot-row-index rows col n)
              pivot (get-in rows [pivot-row col])]
          (if-not (and (finite-number? pivot)
                       (> (js/Math.abs pivot) pivot-floor))
            {:status :failed
             :reason :singular-covariance}
            (let [rows* (assoc rows
                               col (nth rows pivot-row)
                               pivot-row (nth rows col))
                  normalized (mapv #(/ % pivot) (nth rows* col))
                  eliminated (mapv (fn [row-idx row]
                                     (if (= row-idx col)
                                       normalized
                                       (let [factor (nth row col)]
                                         (if (zero? factor)
                                           row
                                           (mapv (fn [value pivot-value]
                                                   (- value (* factor pivot-value)))
                                                 row
                                                 normalized)))))
                                   (range n)
                                   rows*)]
              (recur (inc col) eliminated))))))))

(defn solve-systems
  "Solves matrix*x = rhs for each right-hand-side column with Gauss-Jordan
  elimination and partial pivoting. Returns {:status :solved :solutions [...]}
  or {:status :failed :reason ...}; never forms an explicit inverse."
  [matrix rhs-columns]
  (let [n (count matrix)
        pivot-floor (* (:pivot tolerances)
                       (max 1 (max-abs-entry matrix)))
        outcome (eliminate-rows matrix rhs-columns pivot-floor)]
    (if-not (= :solved (:status outcome))
      outcome
      (let [solutions (mapv (fn [offset]
                              (mapv #(nth % (+ n offset)) (:rows outcome)))
                            (range (count rhs-columns)))]
        (if (every? #(every? finite-number? %) solutions)
          {:status :solved
           :solutions solutions}
          {:status :failed
           :reason :non-finite-solve})))))

(defn residual-ok?
  "Componentwise backward-error check that matrix*solution reproduces rhs to
  within the residual tolerance, scaled by the magnitude of the terms."
  [matrix solution rhs]
  (let [tol (:residual tolerances)]
    (every? identity
            (map (fn [row rhs-value]
                   (let [predicted (math/dot row solution)
                         scale (reduce +
                                       (js/Math.abs rhs-value)
                                       (map (fn [coefficient value]
                                              (js/Math.abs (* coefficient value)))
                                            row
                                            solution))]
                     (and (finite-number? predicted)
                          (<= (js/Math.abs (- predicted rhs-value))
                              (* tol (max 1 scale))))))
                 matrix
                 rhs))))
