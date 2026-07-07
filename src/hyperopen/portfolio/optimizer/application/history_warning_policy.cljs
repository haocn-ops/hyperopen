(ns hyperopen.portfolio.optimizer.application.history-warning-policy
  "Readiness policy for optimizer history warnings: which codes block a run,
  which map to a per-asset history status, and how several warnings on the
  same asset resolve to one status.")

(def history-blocking-warning-codes
  #{:missing-history-coin
    :missing-candle-history
    :missing-return-history
    :insufficient-candle-history
    :insufficient-return-history
    :missing-vault-address
    :missing-vault-history
    :insufficient-vault-history
    :insufficient-common-history
    :missing-native-risk-history
    :identity-ambiguous
    :instrument-kind-mismatch
    :proxy-mapping-unapproved
    :proxy-validation-failed
    :validation-failed
    :history-assumption-required
    :history-assumption-incomplete})

(def history-assumption-warning-codes
  #{:history-assumption-required
    :history-assumption-incomplete})

(def missing-history-warning-codes
  #{:missing-history-coin
    :missing-candle-history
    :missing-return-history
    :missing-vault-address
    :missing-vault-history
    :missing-native-risk-history
    :identity-ambiguous})

(def insufficient-history-warning-codes
  #{:insufficient-candle-history
    :insufficient-return-history
    :insufficient-vault-history
    :insufficient-common-history})

(def stale-history-warning-codes
  #{:stale-history
    :source-fetch-failed})

(def rejected-history-warning-codes
  #{:instrument-kind-mismatch
    :proxy-mapping-unapproved
    :proxy-validation-failed
    :rejected
    :validation-failed})

(defn warning-history-status
  [warning]
  (cond
    (contains? missing-history-warning-codes (:code warning))
    :missing

    (contains? insufficient-history-warning-codes (:code warning))
    :insufficient

    (contains? stale-history-warning-codes (:code warning))
    :stale

    (contains? rejected-history-warning-codes (:code warning))
    :rejected

    :else
    nil))

(def ^:private warning-status-severity
  ;; :rejected must out-rank :stale (and the rest): one asset routinely carries
  ;; several warnings (e.g. proxy-validation-failed AND stale-history), and a
  ;; last-warning-wins map let a stale note mask the rejection that excluded
  ;; the asset from alignment - hiding its remediation card in the proxy
  ;; workflow while the run stayed blocked on it.
  {:rejected 3
   :missing 2
   :insufficient 1
   :stale 0})

(defn strongest-warning-status-by-id
  "Per-asset warning-derived history status, resolving multiple warnings on the
  same asset by severity rather than input order."
  [warnings]
  (reduce (fn [acc warning]
            (let [instrument-id (:instrument-id warning)
                  status (when instrument-id
                           (warning-history-status warning))]
              (if (and status
                       (> (get warning-status-severity status)
                          (get warning-status-severity (get acc instrument-id) -1)))
                (assoc acc instrument-id status)
                acc)))
          {}
          warnings))
