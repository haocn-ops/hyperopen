(ns hyperopen.portfolio.optimizer.domain.history-assumptions
  "Pure policy + constants for per-asset history assumptions.

  Some selected optimizer assets have little or no return history. The engine
  cannot estimate covariance for them from realized series, so it drops them and
  the run is blocked. A history assumption lets the user supply a stand-in risk
  treatment for such an asset:

    :conservative - no diversification credit. Use the user's volatility and a
                    correlation floor against the rest of the universe, capped to
                    a small max weight. This mode is engine-backed (see
                    `augment-risk-model`). It is the ONLY history-assumption
                    behavior; the fields are pre-seeded with editable anchors so the
                    user can accept or revise them rather than starting from blank.

  (A former :proxy mode - map the asset to a related asset at a chosen relationship
  strength - was removed: it was collected and persisted but the engine never folded
  it into the covariance model, so it was pure wasted effort. Legacy persisted proxy
  entries are migrated to conservative on load; see contracts.migrations.)

  This namespace is the ONE source of truth for the numeric presets so the engine,
  request-builder, readiness, defaults, and view models never disagree. All
  magnitudes are decimals (5% => 0.05)."
  (:require [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def behaviors
  "Supported history-assumption behaviors. Conservative is the only behavior."
  #{:conservative})

(def conservative-correlation-floor
  "Correlation a conservative-mode asset is assumed to have against every other
  asset in the universe (no diversification credit)."
  0.75)

(def default-conservative-max-weight
  "Default max-weight cap for a conservative-mode assumption (3%)."
  0.03)

(def default-conservative-volatility
  "Pre-seeded, editable annual-volatility anchor for a no/short-history asset (80%).
  Deliberately high: with no realized history the asset is treated as risky until the
  user revises it."
  0.8)

(def default-conservative-return
  "Pre-seeded, editable annual expected-return anchor for a no/short-history asset
  (0% - no return edge assumed)."
  0.0)

(def short-history-min-observations
  "User-facing threshold for \"short history\", in native daily observations (~days).
  An asset is \"short\" when it has less than ~a year of returns, independent of the
  optimizer's (larger, ~3-year) request window: this flags genuinely thin-history
  assets, not ones that merely fall short of the full fetch window. The bar sits just
  under a calendar year (~365) to tolerate a few missing daily candles in an
  otherwise-full year. The engine's own minimum is only 1-2 observations, so this is
  the meaningful bar. Tune this single constant to taste."
  360)

(defn default-max-weight
  "Default max-weight cap for the given behavior."
  [behavior]
  (case behavior
    :conservative default-conservative-max-weight
    nil))

(defn default-assumption
  "Default per-asset history-assumption draft for the given behavior. All fields are
  pre-seeded with editable anchors (a conservative volatility and a 0% expected
  return) so the user can accept or revise them rather than starting from blank.
  Returns nil for an unknown behavior."
  [behavior]
  (case behavior
    :conservative {:behavior :conservative
                   :expected-return default-conservative-return
                   :volatility default-conservative-volatility
                   :max-weight default-conservative-max-weight
                   :correlation-floor conservative-correlation-floor}
    nil))

;; --- Objective classification -----------------------------------------------

(def objectives-needing-expected-return
  "Objective kinds that score on expected returns, so a no/short-history asset must
  supply an expected return. Minimum-variance is the only kind that does not."
  #{:max-sharpe :target-return :target-volatility})

(defn return-required-for-objective?
  [objective-kind]
  (contains? objectives-needing-expected-return objective-kind))

;; --- Behavior + completeness ------------------------------------------------

(defn conservative?
  [entry]
  (= :conservative (:behavior entry)))

(defn engine-backed?
  "Only conservative assumptions are folded into the covariance model."
  [entry]
  (conservative? entry))

(defn- valid-cap?
  [entry]
  (coercion/positive-number? (:max-weight entry)))

(defn- valid-return?
  [entry return-required?]
  (or (not return-required?)
      (coercion/finite-number? (:expected-return entry))))

(defn conservative-assumption-complete?
  "True when a conservative entry has everything required to optimize. Expected
  return is required only for return-seeking objectives."
  [entry return-required?]
  (and (conservative? entry)
       (coercion/positive-number? (:volatility entry))
       (coercion/finite-number? (:correlation-floor entry))
       (valid-cap? entry)
       (valid-return? entry return-required?)))

(defn assumption-complete?
  [entry return-required?]
  (if (conservative? entry)
    (conservative-assumption-complete? entry return-required?)
    false))

;; --- Engine inputs (conservative covariance synthesis) ----------------------

(defn conservative-engine-inputs
  "Extracts the conservative assumptions the engine can synthesize into the
  covariance model: those whose asset is actually in the request universe and that
  carry the volatility + correlation floor synthesis needs. Keyed by instrument-id."
  [request]
  (let [universe-ids (set (keep :instrument-id (:universe request)))]
    (reduce-kv (fn [acc id entry]
                 (if (and (conservative? entry)
                          (contains? universe-ids id)
                          (coercion/positive-number? (:volatility entry))
                          (coercion/finite-number? (:correlation-floor entry)))
                   (assoc acc id (select-keys entry [:volatility
                                                     :correlation-floor
                                                     :expected-return]))
                   acc))
               {}
               (:history-assumptions request))))

(defn augment-expected-returns
  "Overrides the expected return for each conservative-assumption asset with the
  user's stated value (used by return-seeking objectives; harmless for min-var)."
  [return-result conservative-by-id]
  (if (empty? conservative-by-id)
    return-result
    (update return-result :expected-returns-by-instrument
            (fn [by-instrument]
              (reduce-kv (fn [acc id entry]
                           (if (coercion/finite-number? (:expected-return entry))
                             (assoc acc id (:expected-return entry))
                             acc))
                         (or by-instrument {})
                         conservative-by-id)))))
