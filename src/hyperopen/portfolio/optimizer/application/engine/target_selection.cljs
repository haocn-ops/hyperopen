(ns hyperopen.portfolio.optimizer.application.engine.target-selection
  (:require [hyperopen.portfolio.optimizer.domain.frontier :as frontier]
            [hyperopen.portfolio.optimizer.domain.math :as math]))

(def ^:private solution-tolerance
  1.0e-5)

(defn- sqrt
  [value]
  (js/Math.sqrt (max 0 value)))

(defn- finite-weights?
  [weights]
  (and (sequential? weights)
       (every? math/finite-number? weights)))

(defn- expected-weight-count?
  [problem weights]
  (= (count (:instrument-ids problem))
     (count weights)))

(defn- format-number
  [value]
  (if (math/finite-number? value)
    (.toFixed value 4)
    "N/A"))

(defn- constraint-label
  [constraint fallback]
  (if-let [code (:code constraint)]
    (name code)
    fallback))

(defn- within-lower?
  [value lower]
  (or (not (math/finite-number? lower))
      (>= value (- lower solution-tolerance))))

(defn- within-upper?
  [value upper]
  (or (not (math/finite-number? upper))
      (<= value (+ upper solution-tolerance))))

(defn- linear-constraint-value
  [weights constraint]
  (let [coefficients (:coefficients constraint)]
    (when (and (sequential? coefficients)
               (= (count weights) (count coefficients))
               (every? math/finite-number? coefficients))
      (math/dot coefficients weights))))

(defn- abs-sum
  [values]
  (reduce + 0 (map js/Math.abs values)))

(defn- bounds-violations
  [problem weights]
  (let [lower-bounds (:lower-bounds problem)
        upper-bounds (:upper-bounds problem)]
    (cond
      (not= (count weights) (count lower-bounds) (count upper-bounds))
      [{:code :solver-result-bounds-shape-violation
        :message "Solver result bounds did not match the returned weight count."}]

      :else
      (->> (map-indexed (fn [idx [weight lower upper]]
                          (cond
                            (not (within-lower? weight lower))
                            {:code :solver-result-bound-violation
                             :bound :lower
                             :instrument-id (get-in problem [:instrument-ids idx])
                             :index idx
                             :target lower
                             :value weight
                             :message (str "weight " idx " lower bound "
                                           (format-number lower)
                                           " but solver returned "
                                           (format-number weight)
                                           ".")}

                            (not (within-upper? weight upper))
                            {:code :solver-result-bound-violation
                             :bound :upper
                             :instrument-id (get-in problem [:instrument-ids idx])
                             :index idx
                             :target upper
                             :value weight
                             :message (str "weight " idx " upper bound "
                                           (format-number upper)
                                           " but solver returned "
                                           (format-number weight)
                                           ".")}))
                        (map vector weights lower-bounds upper-bounds))
           (remove nil?)
           vec))))

(defn- equality-violations
  [problem weights]
  (->> (or (:equalities problem) [])
       (keep (fn [equality]
               (let [target (:target equality)
                     value (linear-constraint-value weights equality)]
                 (when-not (and (math/finite-number? target)
                                (math/finite-number? value)
                                (<= (js/Math.abs (- value target))
                                    solution-tolerance))
                   {:code :solver-result-equality-violation
                    :constraint-code (:code equality)
                    :target target
                    :value value
                    :difference (when (and (math/finite-number? target)
                                           (math/finite-number? value))
                                  (- value target))
                    :message (str (constraint-label equality "equality")
                                  " expected "
                                  (format-number target)
                                  " but solver returned "
                                  (format-number value)
                                  ".")}))))
       vec))

(defn- inequality-violations
  [problem weights]
  (->> (or (:inequalities problem) [])
       (keep (fn [inequality]
               (let [value (linear-constraint-value weights inequality)
                     lower (:lower inequality)
                     upper (:upper inequality)]
                 (when-not (and (math/finite-number? value)
                                (within-lower? value lower)
                                (within-upper? value upper))
                   {:code :solver-result-inequality-violation
                    :constraint-code (:code inequality)
                    :lower lower
                    :upper upper
                    :value value
                    :message (str (constraint-label inequality "inequality")
                                  " expected between "
                                  (format-number lower)
                                  " and "
                                  (format-number upper)
                                  " but solver returned "
                                  (format-number value)
                                  ".")}))))
       vec))

(defn- l1-constraint-value
  [weights constraint]
  (case (:code constraint)
    :gross-exposure (abs-sum weights)
    :turnover (let [current-weights (:current-weights constraint)]
                (when (and (finite-weights? current-weights)
                           (= (count weights) (count current-weights)))
                  (abs-sum (map - weights current-weights))))
    nil))

(defn- l1-constraint-violation-code
  [constraint]
  (case (:code constraint)
    :gross-exposure :solver-result-gross-exposure-violation
    :turnover :solver-result-turnover-violation
    :solver-result-l1-constraint-violation))

(defn- l1-violations
  [problem weights]
  (->> (or (:l1-constraints problem) [])
       (keep (fn [constraint]
               (let [value (l1-constraint-value weights constraint)
                     max-value (:max constraint)]
                 (when-not (and (math/finite-number? value)
                                (within-upper? value max-value))
                   {:code (l1-constraint-violation-code constraint)
                    :constraint-code (:code constraint)
                    :max max-value
                    :value value
                    :message (str (constraint-label constraint "L1 constraint")
                                  " limit "
                                  (format-number max-value)
                                  " but solver returned "
                                  (format-number value)
                                  ".")}))))
       vec))

(defn- solver-result-violations
  [result]
  (let [problem (:problem result)
        weights (:weights result)]
    (cond
      (not= :solved (:status result)) []
      (not (map? problem)) [{:code :solver-result-missing-problem
                             :message "Solver result did not include the optimization problem metadata."}]
      (not (finite-weights? weights)) [{:code :solver-result-invalid-weights
                                        :message "Solver result did not include a finite weight vector."}]
      (not (expected-weight-count? problem weights))
      [{:code :solver-result-weight-count-mismatch
        :expected (count (:instrument-ids problem))
        :actual (count weights)
        :message (str "Solver returned "
                      (count weights)
                      " weights for "
                      (count (:instrument-ids problem))
                      " instruments.")}]
      :else
      (vec (concat (bounds-violations problem weights)
                   (equality-violations problem weights)
                   (inequality-violations problem weights)
                   (l1-violations problem weights))))))

(defn- solved?
  [result]
  (and (= :solved (:status result))
       (empty? (solver-result-violations result))))

(defn- portfolio-point
  [expected-returns covariance risk-free-rate idx result]
  (let [weights (:weights result)
        expected-return (math/portfolio-return weights expected-returns)
        variance (math/portfolio-variance weights covariance)
        volatility (sqrt variance)]
    (cond-> {:id idx
             :return-tilt (get-in result [:problem :return-tilt])
             :weights weights
             :expected-return expected-return
             :volatility volatility
             :sharpe (when (pos? volatility)
                       (/ (- expected-return (or risk-free-rate 0))
                          volatility))
             :solver-status (:status result)
             :solver (:solver result)
             :iterations (:iterations result)
             :elapsed-ms (:elapsed-ms result)}
      (:equal-risk result)
      (assoc :equal-risk (:equal-risk result)))))

(defn- rejected-results
  [solver-results]
  (->> solver-results
       (keep-indexed (fn [idx result]
                       (when-not (solved? result)
                         (cond-> {:index idx
                                  :status (:status result)
                                  :solver (:solver result)
                                  :objective-value (:objective-value result)
                                  :violations (solver-result-violations result)}
                           ;; Iterative strategies attach a specific failure
                           ;; reason/message (e.g. :equal-risk-no-feasible-start);
                           ;; carry them so the run banner can say why.
                           (:reason result) (assoc :reason (:reason result))
                           (:message result) (assoc :message (:message result))))))
       vec))

(defn- solver-failure
  [solver-plan solver-results]
  (let [rejections (rejected-results solver-results)
        violations (vec (mapcat :violations rejections))
        invalid-solution? (seq violations)]
    {:status :infeasible
     :reason (if invalid-solution?
               :solver-returned-invalid-solution
               :solver-returned-no-solution)
     :message (if invalid-solution?
                "The solver reported a solution, but it violated optimizer constraints."
                "The solver did not return any feasible solution.")
     :solver {:strategy (:strategy solver-plan)}
     :details {:solver-result-count (count solver-results)
               :rejected-result-count (count rejections)
               :violations violations
               :rejected-results rejections}
     :solver-results solver-results}))

(defn solved-points
  [request solver-results expected-returns covariance]
  (->> solver-results
       (keep-indexed (fn [idx result]
                       (when (solved? result)
                         (portfolio-point expected-returns
                                          covariance
                                          (:risk-free-rate request)
                                          idx
                                          result))))
       vec))

(defn target-selection
  [request solver-plan solver-results expected-returns covariance]
  (let [points (solved-points request solver-results expected-returns covariance)]
    (if (empty? points)
      (solver-failure solver-plan solver-results)
      (let [frontier-points (frontier/efficient-frontier points)
            selected (or (when (= :frontier-sweep (:strategy solver-plan))
                           (frontier/select-frontier-point frontier-points (:objective request)))
                         (first frontier-points)
                         (first points))]
        {:status :solved
         :selected selected
         :target-frontier frontier-points}))))
