(ns hyperopen.portfolio.optimizer.domain.volatility-intuition
  "Translate annualized portfolio volatility into shorter-horizon
  one-standard-deviation (1σ) move scales via square-root-of-time scaling.

  Explanatory analytics only: nothing here feeds weights, constraints,
  expected returns, covariance estimation, or frontier construction. All
  volatilities are decimal fractions (0.40 = 40%), matching the engine.

  The scaling basis must be the SAME annualization convention that produced
  the displayed annualized volatility. The optimizer annualizes daily crypto
  returns with 365 calendar days per year (`domain.risk/default-periods-per-year`,
  `domain.returns/default-periods-per-year`), so a week is 7 periods and a
  month 30. A 252-trading-day basis (week 5, month 21) is understood for
  completeness; anything else resolves to no basis rather than an invented
  convention — callers must then show the metric as unavailable."
  (:require [hyperopen.portfolio.optimizer.domain.math :as math]))

(def calendar-day-basis
  {:periods-per-year 365
   :period-label "calendar days"
   :weekly-periods 7
   :monthly-periods 30})

(def trading-day-basis
  {:periods-per-year 252
   :period-label "trading days"
   :weekly-periods 5
   :monthly-periods 21})

(defn resolve-basis
  [periods-per-year]
  (case periods-per-year
    365 calendar-day-basis
    252 trading-day-basis
    nil))

(defn- valid-volatility?
  "Finite and non-negative. Zero is a legitimate (all-cash) volatility;
  negative, NaN, and infinite inputs are data errors and yield no model."
  [value]
  (and (math/finite-number? value)
       (>= value 0)))

(defn horizon-volatility
  [annualized basis horizon-periods]
  (when (and (valid-volatility? annualized)
             (some? basis)
             (math/finite-number? horizon-periods)
             (pos? horizon-periods))
    (* annualized
       (js/Math.sqrt (/ horizon-periods (:periods-per-year basis))))))

(defn horizon-vols
  "Daily/weekly/monthly 1σ move scales for one annualized volatility under
  `basis`, or nil when the volatility is invalid or the basis unknown."
  [annualized basis]
  (when (and (valid-volatility? annualized) (some? basis))
    {:annualized annualized
     :daily (horizon-volatility annualized basis 1)
     :weekly (horizon-volatility annualized basis (:weekly-periods basis))
     :monthly (horizon-volatility annualized basis (:monthly-periods basis))}))

(def ^:private severity-order
  [:none :elevated :very-high :extreme])

(defn severity
  "Annualized-volatility severity tier: <50% :none, <100% :elevated,
  <200% :very-high, otherwise :extreme. nil for invalid input."
  [annualized]
  (when (valid-volatility? annualized)
    (cond
      (>= annualized 2.0) :extreme
      (>= annualized 1.0) :very-high
      (>= annualized 0.5) :elevated
      :else :none)))

(defn severity-at-least?
  [tier threshold]
  (let [rank (into {} (map-indexed (fn [idx t] [t idx]) severity-order))]
    (boolean (and (contains? rank tier)
                  (contains? rank threshold)
                  (>= (rank tier) (rank threshold))))))

(defn- monthly-boundary?
  "A ±1σ monthly range crossing −100% cannot be read as a literal symmetric
  simple-return interval (losses stop at −100% without extra liability), so
  the UI must explain the value as a dispersion scale. The value itself is
  never capped."
  [{:keys [monthly]}]
  (boolean (and (math/finite-number? monthly)
                (>= monthly 1.0))))

(defn- decorated-horizons
  [horizons]
  (when horizons
    (assoc horizons
           :severity (severity (:annualized horizons))
           :monthly-boundary? (monthly-boundary? horizons))))

(defn- horizon-change
  [target current]
  (when (and target current)
    (into {}
          (keep (fn [k]
                  (let [t (get target k)
                        c (get current k)]
                    (when (and (math/finite-number? t)
                               (math/finite-number? c))
                      [k (- t c)]))))
          [:annualized :daily :weekly :monthly])))

(defn intuition-model
  "Card model for the recommendation surface.

  Input: {:target-volatility σ :current-volatility σ :periods-per-year n}
  (decimals; current optional). Output is either
  {:status :unavailable :reason ...} — unknown basis or missing/invalid
  target volatility — or

    {:status :ok
     :basis {...}
     :target {:annualized :daily :weekly :monthly :severity :monthly-boundary?}
     :current <same shape or nil>
     :change {:annualized :daily :weekly :monthly} or nil}

  No NaN or infinite number ever appears in an :ok model."
  [{:keys [target-volatility current-volatility periods-per-year]}]
  (let [basis (resolve-basis periods-per-year)]
    (cond
      (nil? basis)
      {:status :unavailable :reason :unknown-annualization-basis}

      :else
      (let [target (decorated-horizons (horizon-vols target-volatility basis))
            current (decorated-horizons (horizon-vols current-volatility basis))]
        (if (nil? target)
          {:status :unavailable :reason :missing-target-volatility}
          {:status :ok
           :basis basis
           :target target
           :current current
           :change (horizon-change target current)})))))
