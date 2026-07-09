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

(def stale-history-incident-min-age-days
  ;; Serve-time staleness at or beyond this many days is a refresh-pipeline
  ;; INCIDENT, not the routine 1-2 day tail: the backend escalates the
  ;; stale-history warning to severity "error" here (API_CONTRACT.md), and the
  ;; UI stops treating it as an all-clear/info note — it paints a visible amber
  ;; chip and a readiness caution instead.
  7)

(def misaligned-history-warning-codes
  ;; The backend excluded the asset from common_calendar/return_calendar/
  ;; aligned_returns_by_instrument because its served window cannot meaningfully
  ;; overlap the majority window. Its OWN per-instrument series is still fully
  ;; served, so this is "loaded but misaligned" (the shared-gap chip), never
  ;; "missing".
  #{:excluded-from-alignment})

(defn serve-age-days
  "Serve-time staleness age in days a stale-history warning carries in
  details.serve_age_days, or nil when the backend did not report one. Lets the
  STALE surfaces read the age directly instead of recomputing it from
  served_end_ms and the request date."
  [warning]
  (let [age (get-in warning [:details :serve-age-days])]
    (when (number? age) age)))

(defn stale-incident?
  "True when a stale-history warning has escalated to an incident: the backend
  tagged it severity \"error\", or its served series is at least
  `stale-history-incident-min-age-days` days old."
  [warning]
  (and (contains? stale-history-warning-codes (:code warning))
       (boolean
        (or (contains? #{"error" :error} (:severity warning))
            (when-let [age (serve-age-days warning)]
              (>= age stale-history-incident-min-age-days))))))

(def rejected-history-warning-codes
  #{:instrument-kind-mismatch
    :proxy-mapping-unapproved
    :proxy-validation-failed
    :rejected
    :validation-failed})

(defn warning-history-status
  [warning]
  (cond
    ;; A forgiven warning (e.g. a failed OPTIONAL proxy lookback-extension on an
    ;; asset that aligned on its own usable native series) is information, not a
    ;; verdict about the asset's history - it must never drive a status.
    (:forgiven? warning)
    nil

    (contains? missing-history-warning-codes (:code warning))
    :missing

    (contains? insufficient-history-warning-codes (:code warning))
    :insufficient

    ;; Excluded from the shared calendars but served in full: loaded, not
    ;; missing. Paints the by-exception shared-gap chip and invites an
    ;; assumption, exactly like the client-side common-gap fallback.
    (contains? misaligned-history-warning-codes (:code warning))
    :loaded-but-misaligned

    (contains? stale-history-warning-codes (:code warning))
    (if (stale-incident? warning) :stale-critical :stale)

    (contains? rejected-history-warning-codes (:code warning))
    :rejected

    :else
    nil))

(def ^:private warning-status-severity
  ;; :rejected must out-rank :stale (and the rest): one asset routinely carries
  ;; several warnings (e.g. proxy-validation-failed AND stale-history), and a
  ;; last-warning-wins map let a stale note mask the rejection that excluded
  ;; the asset from alignment - hiding its remediation card in the proxy
  ;; workflow while the run stayed blocked on it. :loaded-but-misaligned
  ;; (excluded-from-alignment) ranks BELOW :insufficient so a stronger,
  ;; more-specific client verdict for the same asset still wins ("labels derive
  ;; from what alignment DID"); :stale-critical ranks just above routine :stale.
  {:rejected 5
   :missing 4
   :insufficient 3
   :loaded-but-misaligned 2
   :stale-critical 1
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
