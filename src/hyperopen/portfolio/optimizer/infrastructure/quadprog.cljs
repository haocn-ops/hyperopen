(ns hyperopen.portfolio.optimizer.infrastructure.quadprog
  (:require ["quadprog" :as quadprog-js]
            [hyperopen.portfolio.optimizer.infrastructure.problem-adapter :as problem-adapter]))

(defn- one-indexed-vector
  [values]
  (clj->js (vec (cons nil values))))

(defn- one-indexed-matrix
  [matrix]
  (clj->js (vec (cons nil
                      (mapv #(vec (cons nil %)) matrix)))))

(defn- constraint-columns
  [problem]
  (let [equalities (or (:equalities problem) [])
        inequalities (or (:inequalities problem) [])
        lower-bounds (:lower-bounds problem)
        upper-bounds (:upper-bounds problem)
        n (count (:instrument-ids problem))
        equality-columns (mapv (fn [constraint]
                                 {:coefficients (:coefficients constraint)
                                  :bound (:target constraint)
                                  :equality? true})
                               equalities)
        inequality-columns (mapcat (fn [constraint]
                                     (concat
                                      (when (number? (:lower constraint))
                                        [{:coefficients (:coefficients constraint)
                                          :bound (:lower constraint)}])
                                      (when (number? (:upper constraint))
                                        [{:coefficients (mapv - (:coefficients constraint))
                                          :bound (- (:upper constraint))}])))
                                   inequalities)
        lower-columns (map-indexed (fn [idx lower]
                                     (when (number? lower)
                                       {:coefficients (mapv (fn [i]
                                                              (if (= i idx) 1 0))
                                                            (range n))
                                        :bound lower}))
                                   lower-bounds)
        upper-columns (map-indexed (fn [idx upper]
                                     (when (number? upper)
                                       {:coefficients (mapv (fn [i]
                                                              (if (= i idx) -1 0))
                                                            (range n))
                                        :bound (- upper)}))
                                   upper-bounds)]
    {:columns (vec (concat equality-columns
                           inequality-columns
                           (keep identity lower-columns)
                           (keep identity upper-columns)))
     :meq (count equality-columns)}))

(defn- quadprog-amat
  [n columns]
  (mapv (fn [row-idx]
          (mapv #(nth (:coefficients %) row-idx) columns))
        (range n)))

(defn- normalize-solution
  [problem solved decode]
  (let [message (.-message ^js solved)]
    (if (seq message)
      {:status :infeasible
       :solver :quadprog
       :reason :solver-message
       :message message}
      (let [solution (vec (js->clj (.slice (.-solution ^js solved) 1)))
            weights (decode solution)]
        {:status :solved
         :solver :quadprog
         :weights weights
         :objective-value (problem-adapter/objective-value problem weights)
         :iterations (second (js->clj (.-iterations solved)))}))))

(defn solve
  [problem]
  (if-let [unsupported (problem-adapter/unsupported-l1-constraints problem)]
    (problem-adapter/unsupported-result :invalid-l1-constraints
                                        {:constraints (vec unsupported)})
    (let [{adapted-problem :problem decode :decode} (problem-adapter/adapt-problem problem)
          n (count (:instrument-ids adapted-problem))
          {:keys [columns meq]} (constraint-columns adapted-problem)
          dmat (one-indexed-matrix (problem-adapter/add-diagonal-epsilon
                                    (:quadratic adapted-problem)))
          dvec (one-indexed-vector (mapv - (:linear adapted-problem)))
          amat (one-indexed-matrix (quadprog-amat n columns))
          bvec (one-indexed-vector (mapv :bound columns))
          solved (.solveQP quadprog-js dmat dvec amat bvec meq)]
      (normalize-solution problem solved decode))))
