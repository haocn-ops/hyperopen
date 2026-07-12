(ns hyperopen.portfolio.optimizer.application.view-model.volatility-intuition
  "Read models for the volatility-intuition rail card, the frontier-callout
  horizon rows, the under-chart insight strip, and the leverage-risk rail
  card. One derivation point so the card, tooltip, and strip can never
  disagree about a horizon value.

  Everything reads the solved run result; nothing here feeds the solver. The
  annualization basis is the optimizer's own convention
  (`domain.risk/default-periods-per-year` = 365 calendar days) — resolved,
  never assumed, so a future convention change fails loud (model unavailable)
  instead of silently mixing bases."
  (:require [hyperopen.portfolio.optimizer.domain.leverage-risk :as leverage-risk]
            [hyperopen.portfolio.optimizer.domain.risk :as risk]
            [hyperopen.portfolio.optimizer.domain.volatility-intuition :as intuition]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def ^:private finite-number? coercion/finite-number?)

(defn optimizer-basis
  []
  (intuition/resolve-basis risk/default-periods-per-year))

(defn horizons
  "Daily/weekly/monthly 1σ scales for one annualized volatility under the
  optimizer's convention, or nil. Used by the frontier Current/Target
  callouts."
  [annualized-volatility]
  (intuition/horizon-vols annualized-volatility (optimizer-basis)))

(defn view-radio-name
  "DOM-state radio group for the card's Target/Current toggle. Suffixed with
  the result's :as-of-ms so co-rendered workbench fixtures don't share a
  group."
  [result]
  (str "optimizer-volatility-intuition-" (or (:as-of-ms result) "result")))

(defn view-radio-id
  [result view]
  (str (view-radio-name result) "-" view))

(defn card-model
  "Model for the rail card, or nil when the result carries no usable target
  volatility (card renders nothing — no invented values)."
  [result]
  (let [model (intuition/intuition-model
               {:target-volatility (:volatility result)
                :current-volatility (:current-volatility result)
                :periods-per-year risk/default-periods-per-year})]
    (when (= :ok (:status model))
      (assoc model
             :radio-name (view-radio-name result)
             :target-radio-id (view-radio-id result "target")
             :current-radio-id (view-radio-id result "current")))))

(defn insight-model
  "Under-chart one-liner, only when target volatility is very high (≥100%
  annualized) — at ordinary levels the strip would restate the card."
  [result]
  (let [{:keys [status target]} (intuition/intuition-model
                                 {:target-volatility (:volatility result)
                                  :periods-per-year risk/default-periods-per-year})]
    (when (and (= :ok status)
               (intuition/severity-at-least? (:severity target) :very-high))
      target)))

(def leverage-gate-gross-exposure
  "Gross exposure (x) at or above which the target counts as 'using higher
  leverage' and the leverage-risk card surfaces."
  2.0)

(def leverage-gate-volatility
  "Annualized volatility at or above which the leverage-risk card surfaces
  even below the gross gate — the modeled drag/drawdown story is the point at
  these levels regardless of how the book got there."
  1.0)

(defn- leverage-gated?
  [result]
  (let [gross (get-in result [:diagnostics :gross-exposure])
        volatility (:volatility result)]
    (boolean
     (or (and (finite-number? gross)
              (>= gross leverage-gate-gross-exposure))
         (and (finite-number? volatility)
              (>= volatility leverage-gate-volatility))))))

(defn- dollar-outcomes
  [outcome capital-usd]
  (when (and outcome
             (finite-number? capital-usd)
             (pos? capital-usd))
    {:median-usd (* capital-usd (:median-ending-factor outcome))
     :p5-usd (* capital-usd (:p5-ending-factor outcome))
     :p95-usd (* capital-usd (:p95-ending-factor outcome))}))

(defn leverage-risk-model
  "Model for the leverage-risk rail card, or nil when the target is not
  meaningfully levered/volatile or the outcome model has no valid inputs.

    {:target {...outcome-model... :dollar {...} or nil}
     :current <same shape> or nil
     :capital-usd $ or nil
     :gross-exposure x or nil}"
  [result]
  (when (leverage-gated? result)
    (let [capital-usd (let [value (get-in result [:rebalance-preview :capital-usd])]
                        (when (and (finite-number? value) (pos? value))
                          value))
          decorate (fn [outcome]
                     (when outcome
                       (assoc outcome :dollar (dollar-outcomes outcome capital-usd))))
          target (decorate
                  (leverage-risk/outcome-model
                   {:expected-return (:expected-return result)
                    :volatility (:volatility result)}))
          current (decorate
                   (leverage-risk/outcome-model
                    {:expected-return (:current-expected-return result)
                     :volatility (:current-volatility result)}))]
      (when target
        {:target target
         :current current
         :capital-usd capital-usd
         :gross-exposure (let [gross (get-in result [:diagnostics :gross-exposure])]
                           (when (finite-number? gross) gross))}))))
