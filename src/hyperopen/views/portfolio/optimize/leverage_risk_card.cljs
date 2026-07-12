(ns hyperopen.views.portfolio.optimize.leverage-risk-card
  "The LEVERAGE RISK rail card: modeled one-year outcomes on account equity
  for a levered target — median and 5th-percentile ending equity (current vs
  target), the odds of ending the year down half, and the odds of touching a
  50% drawdown at any point. Surfaces only when the target is meaningfully
  levered (gross ≥ 2x) or extremely volatile (≥100% annualized) — the gate
  lives in the view-model.

  Honesty contract: the model is closed-form lognormal scaled from the run's
  own return/volatility estimates (see domain.leverage-risk). It is labeled
  'modeled', states its assumptions in fine print, and never prints a
  liquidation probability — maintenance margins are not in the result, and
  the 50%-drawdown odds are explained as a floor on ruin-type risk."
  (:require [hyperopen.portfolio.optimizer.application.view-model.volatility-intuition
             :as vm]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(defn- whole-usd
  [value]
  (opt-format/format-usdc value {:maximum-fraction-digits 0}))

(defn- probability-pct
  [value]
  (opt-format/format-pct value {:minimum-fraction-digits 0
                                :maximum-fraction-digits 1}))

(defn- ending-value-text
  "Dollar ending equity when account equity is known, else the multiple of
  starting equity (0.43x = ending at 43% of start)."
  [{:keys [dollar median-ending-factor]}]
  (if dollar
    (whole-usd (:median-usd dollar))
    (str (opt-format/format-multiple median-ending-factor) " start")))

(defn- median-bar-width
  [factor max-factor]
  (if (and (number? factor) (number? max-factor) (pos? max-factor))
    (max 2 (min 100 (* 100 (/ factor max-factor))))
    0))

(defn- median-row
  [kind label outcome max-factor]
  [:div {:class ["optimizer-leverage-risk-median-row"]
         :data-role (str "portfolio-optimizer-leverage-risk-median-" kind)}
   [:span {:class ["optimizer-leverage-risk-median-label"]} label]
   [:div {:class ["optimizer-leverage-risk-track"]
          :aria-hidden "true"}
    ;; Hairline at the starting-equity position (factor 1.0): everything left
    ;; of it is modeled loss, right of it modeled growth. Inset 1px so the
    ;; tick survives the track's overflow clip when start IS the scale max.
    [:div {:class ["optimizer-leverage-risk-start-tick"]
           :style {:left (str "calc(" (median-bar-width 1.0 max-factor) "% - 1px)")}}]
    [:div {:class ["optimizer-leverage-risk-fill"
                   (str "optimizer-leverage-risk-fill--" kind)]
           :style {:width (str (median-bar-width (:median-ending-factor outcome)
                                                 max-factor)
                               "%")}}]]
   [:span {:class ["optimizer-leverage-risk-median-value" "font-mono" "tabular-nums"]}
    (ending-value-text outcome)]])

(defn- median-shortfall-note
  "Signed median difference vs the current book, only when both medians are
  modeled in dollars. A negative difference is the mockup's 'median wealth
  shortfall vs current' — the single number that makes volatility drag real."
  [current target]
  (let [current-usd (get-in current [:dollar :median-usd])
        target-usd (get-in target [:dollar :median-usd])]
    (when (and (number? current-usd) (number? target-usd))
      (let [diff (- target-usd current-usd)
            shortfall? (neg? diff)]
        [:p {:class ["mt-1.5" "text-[0.62rem]" "font-mono" "tabular-nums"
                     (if shortfall? "text-warning" "text-trading-green")]
             :data-role "portfolio-optimizer-leverage-risk-median-shortfall"}
         (str "Median vs current: "
              (if shortfall? "−" "+")
              (whole-usd (js/Math.abs diff)))]))))

(defn- metric-row
  [{:keys [data-role label value subtext value-class]}]
  [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]
         :data-role data-role}
   [:span {:class ["text-[0.62rem]" "font-semibold" "uppercase" "tracking-[0.06em]" "text-trading-muted"]}
    label]
   [:p {:class ["mt-1" "font-mono" "text-sm" "font-semibold" "tabular-nums"
                (or value-class "text-trading-text")]}
    value]
   (when subtext
     [:p {:class ["mt-0.5" "text-[0.6rem]" "text-trading-muted/70"]} subtext])])

(defn leverage-risk-card
  [result]
  (when-let [{:keys [target current capital-usd]} (vm/leverage-risk-model result)]
    (let [max-factor (apply max
                            1.0
                            (keep :median-ending-factor [current target]))
          touch-odds (:prob-touch-half-drawdown target)
          terminal-odds (:prob-terminal-loss-half target)]
      [:aside {:class ["optimizer-leverage-risk"
                       "min-h-0" "border-l" "border-base-300" "bg-base-100/95"]
               :replicant/key "optimizer-leverage-risk-card"
               :data-role "portfolio-optimizer-leverage-risk"}
       [:div {:class ["flex" "items-center" "justify-between" "gap-3"
                      "border-b" "border-base-300" "px-4" "py-3"]}
        [:p {:class ["font-mono" "text-[0.62rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
         "Leverage risk"]
        [:span {:class ["optimizer-leverage-risk-chip"]}
         "1y · modeled"]]
       [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
        [:p {:class ["text-[0.62rem]" "font-semibold" "uppercase" "tracking-[0.06em]" "text-trading-muted"]}
         "Median ending equity"]
        [:div {:class ["mt-2" "space-y-1.5"]}
         (when current
           (median-row "current" "Current" current max-factor))
         (median-row "target" "Target" target max-factor)]
        (median-shortfall-note current target)
        [:p {:class ["mt-1.5" "text-[0.6rem]" "text-trading-muted/70"]}
         (if capital-usd
           (str "On account equity " (whole-usd capital-usd)
                " · tick marks the starting equity.")
           "As a multiple of starting equity · tick marks the start.")]]
       (metric-row {:data-role "portfolio-optimizer-leverage-risk-p5"
                    :label "5th percentile (target)"
                    :value (if-let [dollar (:dollar target)]
                             (whole-usd (:p5-usd dollar))
                             (str (opt-format/format-multiple (:p5-ending-factor target))
                                  " start"))
                    :subtext "One year in twenty ends at or below this."})
       (metric-row {:data-role "portfolio-optimizer-leverage-risk-terminal"
                    :label "Odds of ending the year down 50%+"
                    :value (probability-pct terminal-odds)
                    :value-class (when (and (number? terminal-odds)
                                            (>= terminal-odds 0.5))
                                   "text-warning")})
       (metric-row {:data-role "portfolio-optimizer-leverage-risk-touch"
                    :label "Odds of touching −50% during the year"
                    :value (probability-pct touch-odds)
                    :value-class (when (and (number? touch-odds)
                                            (>= touch-odds 0.5))
                                   "text-warning")
                    :subtext "A levered book is usually liquidated before a drawdown this deep completes — treat as a floor on ruin risk."})
       [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
        [:p {:class ["text-[0.6rem]" "text-trading-muted/70"]}
         "Lognormal model scaled from this run's return and volatility estimates; assumes continuous rebalancing to target weights."]
        [:p {:class ["mt-0.5" "text-[0.6rem]" "text-trading-muted/70"]}
         "Ignores fat tails, funding, execution costs, and margin mechanics. Modeled, not a guarantee."]]])))
