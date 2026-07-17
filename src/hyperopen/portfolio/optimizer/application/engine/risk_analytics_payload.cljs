(ns hyperopen.portfolio.optimizer.application.engine.risk-analytics-payload
  "Objective-agnostic weights+covariance analytics sections shared by the
  covariance-only result payloads (:equal-risk, :inverse-volatility):

  - :risk-contributions — the signed Euler contribution summary computed over
    the FINAL published target weights (never a raw solver candidate). The
    summary is returned WITHOUT a :quality — each objective owns its own
    quality semantics (Equal Risk classifies convergence, Risk-weighted
    sizing labels the section :diagnostic).
  - :current-risk-contributions — the SAME summary over the current book's
    aligned weights, so the results page can show current vs recommended.
    Omitted when the current book is empty or degenerate.
  - :risk-structure — the correlation-view section (see
    domain.risk-structure): per-instrument standalone/diversification
    decomposition of the SAME net shares, P&L-to-portfolio correlations, and
    the capped underlying correlation matrix. The covariance itself is not
    persisted, so this section is the only correlation data the results page
    ever has.

  Callers own objective-specific decoration (quality classification, solver
  metadata) and the user-facing warnings for the error statuses."
  (:require [hyperopen.portfolio.optimizer.domain.risk-contributions :as risk-contributions]
            [hyperopen.portfolio.optimizer.domain.risk-structure :as risk-structure]))

(defn aligned-vector?
  [values n]
  (and (sequential? values)
       (= n (count values))
       (every? #(and (number? %) (js/isFinite %)) values)))

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

(defn- scalar-diversification-summary
  [covariance weights]
  (let [summary (risk-structure/portfolio-diversification-summary covariance
                                                                  weights)]
    (when (= :ok (:status summary))
      (dissoc summary :status))))

(defn analytics-sections
  "The shared analytics sections from aligned inputs, or an error marker the
  caller maps to its own warning copy:

    {:risk-contributions <summary sans :status, NO :quality>
     :current-risk-contributions <...>   ; only when computable
     :risk-structure <...>}              ; only when computable
    {:error :alignment}    ; ids/weights/covariance disagree
    {:error :degenerate}   ; published weights have no portfolio variance"
  [{:keys [instrument-ids covariance target-weights current-weights targets]}]
  (let [n (count instrument-ids)
        covariance-validation (risk-contributions/validate-covariance covariance n)
        aligned? (and (pos? n)
                      (= :ok (:status covariance-validation))
                      (aligned-vector? target-weights n))]
    (if-not aligned?
      {:error :alignment}
      (let [covariance* (:covariance covariance-validation)
            summary (risk-contributions/contribution-summary
                     {:instrument-ids instrument-ids
                      :covariance covariance*
                      :weights target-weights
                      :targets targets})]
        (if (not= :ok (:status summary))
          {:error :degenerate}
          (let [current-summary (when (aligned-vector? current-weights n)
                                  (current-contribution-summary
                                   {:instrument-ids instrument-ids
                                    :covariance covariance*
                                    :current-weights current-weights
                                    :targets targets}))
                structure (risk-structure/structure-summary
                           {:instrument-ids instrument-ids
                            :covariance covariance*
                            :weights target-weights})
                target-diversification (scalar-diversification-summary
                                        covariance* target-weights)
                current-diversification (when (aligned-vector? current-weights n)
                                          (scalar-diversification-summary
                                           covariance* current-weights))
                structure* (when (= :ok (:status structure))
                             (cond-> (dissoc structure :status)
                               target-diversification
                               (assoc :target-diversification
                                      target-diversification)
                               current-diversification
                               (assoc :current-diversification
                                      current-diversification)))]
            (cond-> {:risk-contributions (dissoc summary :status)}
              current-summary (assoc :current-risk-contributions current-summary)
              structure* (assoc :risk-structure structure*))))))))
