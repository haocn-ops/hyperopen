(ns hyperopen.portfolio.optimizer.application.engine.equal-risk-payload
  "Equal Risk (:equal-risk) result-payload sections, assembled from the FINAL
  published target weights (never a raw solver candidate):

  - :risk-contributions — the signed Euler contribution summary with the
    truthful :exact / :approximate / :not-converged quality (gated on solver
    convergence, see domain.equal-risk/classify-quality).
  - :current-risk-contributions — the SAME summary computed over the current
    portfolio's aligned weights, so the results page can show current vs
    recommended balance. Omitted (nil) when the current book is empty or its
    variance is degenerate on the selected universe.
  - :equal-risk-solver — solver metadata plus :allocation-freedom, a property
    of the problem geometry: one signed-gross equality pins one unlocked
    selected member, so free degrees = unlocked selected positions - 1. Zero
    degrees => :fully-determined; binding bound constraints on a
    non-degenerate problem => :limited; else :open.
  - :risk-structure — the correlation-view section (see domain.risk-structure):
    per-instrument standalone/diversification decomposition of the SAME net
    shares, P&L-to-portfolio correlations, and the capped underlying
    correlation matrix. Computed from the same covariance + published weights
    as :risk-contributions; the covariance itself is not persisted, so this
    section is the only correlation data the results page ever has."
  (:require [hyperopen.portfolio.optimizer.domain.equal-risk :as equal-risk]
            [hyperopen.portfolio.optimizer.domain.risk-contributions :as risk-contributions]
            [hyperopen.portfolio.optimizer.domain.risk-structure :as risk-structure]))

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

(defn- current-contribution-summary
  "Signed contribution summary of the CURRENT aligned weights, for the
  current-vs-recommended comparison. Nil when the current book is flat or its
  variance is degenerate — the views degrade to em-dashes, never fabricate."
  [{:keys [instrument-ids covariance current-weights targets]}]
  (when (some #(and (number? %) (not (zero? %))) current-weights)
    (let [summary (risk-contributions/contribution-summary
                   {:instrument-ids instrument-ids
                    :covariance covariance
                    :weights current-weights
                    :targets targets})]
      (when (= :ok (:status summary))
        (dissoc summary :status)))))

(defn equal-risk-sections
  "The :risk-contributions / :current-risk-contributions / :equal-risk-solver
  payload sections plus any warnings. Quality gates :exact on solver
  convergence; every number derives from the published weights."
  [{:keys [risk-result selection solver-plan diagnostics
           instrument-ids target-weights current-weights]}]
  (let [n (count instrument-ids)
        targets (vec (repeat n (/ 1 n)))
        solver-metadata (get-in selection [:selected :equal-risk])
        converged? (boolean (:converged? solver-metadata))
        covariance (:covariance risk-result)
        summary (risk-contributions/contribution-summary
                 {:instrument-ids instrument-ids
                  :covariance covariance
                  :weights target-weights
                  :targets targets})]
    (if (not= :ok (:status summary))
      {:warnings [{:code :equal-risk-contributions-unavailable
                   :message "Risk contributions could not be computed for the published weights (degenerate portfolio variance)."}]}
      (let [quality (equal-risk/classify-quality summary n converged?)
            freedom (allocation-freedom solver-plan
                                        (:binding-constraints diagnostics))
            current-summary (current-contribution-summary
                             {:instrument-ids instrument-ids
                              :covariance covariance
                              :current-weights current-weights
                              :targets targets})
            structure (risk-structure/structure-summary
                       {:instrument-ids instrument-ids
                        :covariance covariance
                        :weights target-weights})]
        (cond-> {:risk-contributions (-> summary
                                         (dissoc :status)
                                         (assoc :quality quality))
                 :equal-risk-solver (cond-> solver-metadata
                                      freedom (assoc :allocation-freedom freedom))
                 :warnings (when (= :not-converged quality)
                             [{:code :equal-risk-not-converged
                               :message "Equal Risk stopped at its iteration limit before converging - showing the best feasible portfolio found. Risk contributions are labeled Not converged."}])}
          current-summary (assoc :current-risk-contributions current-summary)
          (= :ok (:status structure)) (assoc :risk-structure
                                             (dissoc structure :status)))))))
