(ns hyperopen.portfolio.optimizer.application.engine.equal-risk-payload
  "Equal Risk (:equal-risk) result-payload sections, assembled from the FINAL
  published target weights (never a raw solver candidate):

  - :risk-contributions / :current-risk-contributions / :risk-structure — the
    objective-agnostic analytics from engine.risk-analytics-payload, with the
    truthful :exact / :approximate / :not-converged quality stamped on the
    contribution summary (gated on solver convergence, see
    domain.equal-risk/classify-quality).
  - :equal-risk-solver — solver metadata plus :allocation-freedom, a property
    of the problem geometry: one signed-gross equality pins one unlocked
    selected member, so free degrees = unlocked selected positions - 1. Zero
    degrees => :fully-determined; binding bound constraints on a
    non-degenerate problem => :limited; else :open."
  (:require [hyperopen.portfolio.optimizer.application.engine.risk-analytics-payload
             :as risk-analytics]
            [hyperopen.portfolio.optimizer.domain.equal-risk :as equal-risk]))

(defn- book-free-count
  [instrument-ids locked-ids indexes]
  (count (remove #(contains? locked-ids (nth instrument-ids %)) indexes)))

(defn allocation-freedom
  "Allocation-freedom classification from the plan problem's books/locks and
  the run diagnostics' binding constraints. Nil when the plan problem carries
  no book split (e.g. hand-built results)."
  [solver-plan binding-constraints]
  (let [problem (first (:problems solver-plan))
        books (:books problem)]
    (when (map? books)
      (let [instrument-ids (vec (:instrument-ids problem))
            locked-ids (set (keep :instrument-id (:locked-weights problem)))
            long-free (book-free-count instrument-ids locked-ids (:long books))
            short-free (book-free-count instrument-ids locked-ids (:short books))
            free-degrees (max 0 (dec (+ long-free short-free)))
            binding-count (count (distinct (keep :instrument-id
                                                 binding-constraints)))]
        {:status (cond
                   (zero? free-degrees) :fully-determined
                   (pos? binding-count) :limited
                   :else :open)
         :free-degrees free-degrees
         :binding-count binding-count
         :books {:long (count (:long books))
                 :short (count (:short books))}}))))

(defn equal-risk-sections
  "The :risk-contributions / :current-risk-contributions / :risk-structure /
  :equal-risk-solver payload sections plus any warnings. Quality gates :exact
  on solver convergence; every number derives from the published weights."
  [{:keys [risk-result selection solver-plan diagnostics
           instrument-ids target-weights current-weights]}]
  (let [n (count instrument-ids)
        targets (vec (repeat n (/ 1 n)))
        solver-metadata (get-in selection [:selected :equal-risk])
        converged? (boolean (:converged? solver-metadata))
        analytics (risk-analytics/analytics-sections
                   {:instrument-ids instrument-ids
                    :covariance (:covariance risk-result)
                    :target-weights target-weights
                    :current-weights current-weights
                    :targets targets})]
    (case (:error analytics)
      :alignment
      {:warnings [{:code :equal-risk-payload-alignment-invalid
                   :message "Equal Risk contribution and diversification summaries were omitted because the published instruments, weights, and covariance were not aligned."}]}

      :degenerate
      {:warnings [{:code :equal-risk-contributions-unavailable
                   :message "Risk contributions could not be computed for the published weights (degenerate portfolio variance)."}]}

      (let [summary (:risk-contributions analytics)
            quality (equal-risk/classify-quality summary n converged?)
            freedom (allocation-freedom solver-plan
                                        (:binding-constraints diagnostics))]
        (assoc analytics
               :risk-contributions (assoc summary :quality quality)
               :equal-risk-solver (cond-> solver-metadata
                                    freedom (assoc :allocation-freedom freedom))
               :warnings (when (= :not-converged quality)
                           [{:code :equal-risk-not-converged
                             :message "Equal Risk stopped at its iteration limit before converging - showing the best feasible portfolio found. Risk contributions are labeled Not converged."}]))))))
