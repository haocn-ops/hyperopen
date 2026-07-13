(ns hyperopen.views.account-info.margin-rec-copy
  "Tooltip / help copy for the margin recommendation panel, kept out of the
  view namespace so the panel stays readable and the wording — which makes
  specific, checkable claims about the model — is reviewable in one place.

  Each tip aims to answer: what is this, how is it computed, and what should
  the reader NOT infer from it. Copy is grounded in the actual engine
  (hyperopen.margin-rec.*): EWMA volatility with a ~3-day half-life, an
  80th-percentile trade-history horizon, exact per-path required equity against
  the maintenance curve, and the named buffers that sum to the recommendation.")

(def tips
  {:header
   (str "Estimates the total isolated collateral needed to keep this position's"
        " modeled liquidation probability at or below your selected target until"
        " your next likely intervention. A model-based estimate, not a guarantee"
        " against liquidation.")

   :coin
   (str "The instrument this recommendation is for. Every volatility, funding,"
        " and liquidation figure shown here is specific to this position.")

   :leverage
   (str "Isolated margin: only collateral assigned to this position protects it"
        " from liquidation. The multiple is your configured leverage setting —"
        " effective leverage changes as price and margin move.")

   :recommended-margin
   (str "The TOTAL isolated margin recommended after the change — it already"
        " includes the collateral on the position, so it is not the amount to"
        " add. See the \"Add …\" line for the top-up.")

   :vs-current
   (str "How much larger the recommended margin is than your current margin."
        " This compares position margin, not account equity or position value.")

   :additional
   (str "Collateral to move from your available account balance to reach the"
        " recommended total. Your balance and free-collateral reserve are"
        " re-checked just before submission.")

   :liq-probability
   (str "The chance this position's mark price touches its liquidation boundary"
        " at least once before your next likely intervention, at the current and"
        " recommended margin. It is NOT the chance of losing money, and NOT an"
        " annual figure. Both numbers use the same simulated paths so they are"
        " comparable.")

   :horizon-scope
   (str "Risk is measured over your estimated time-to-next-intervention horizon"
        " (see \"How we estimated this\"), not per day or per year.")

   :new-liq
   (str "Estimated liquidation price after adding the recommended margin, holding"
        " position size and mark price constant. The exchange's figure may differ"
        " if price, funding, or size change before you execute. Liquidation is"
        " based on mark price, not the last traded price.")

   :chart
   (str "How the modeled liquidation probability changes as you vary the total"
        " isolated margin. Each point re-runs the liquidation test on the same"
        " position, horizon, and simulated price paths. The x-axis is total"
        " margin, not the amount being added; more collateral moves the boundary"
        " away, but non-linearly.")

   :vol-convention
   (str "Crypto trades continuously, so volatility is annualized over 365"
        " calendar days and scaled to the horizon by square-root-of-time. The"
        " simulation also captures fat tails and path dependence beyond this"
        " baseline.")

   :realized-vol
   (str "Annualized volatility estimated from this instrument's recent hourly"
        " mark returns using an EWMA with a ~3-day half-life, so recent moves"
        " weigh more heavily.")

   :monte-carlo
   (str "A block-bootstrap Monte Carlo that resamples real hourly moves —"
        " preserving volatility clustering, fat tails, and intra-bar wicks —"
        " rather than assuming normally distributed returns.")

   :buffers-section
   (str "The components that make up the recommended total margin. These amounts"
        " sum exactly to the recommendation.")

   :risk-target
   (str "The most modeled liquidation probability you will allow before your next"
        " intervention — a horizon-specific target, not an annual limit. It does"
        " NOT mean only a 1/2/5% chance of losing money, and repeatedly accepting"
        " it across many positions raises your cumulative chance of a"
        " liquidation.")

   :risk-conservative
   (str "Targets no more than ~1% modeled liquidation probability before your"
        " next intervention. Uses more collateral and lower effective leverage.")

   :risk-balanced
   (str "Targets no more than ~2% modeled liquidation probability before your"
        " next intervention. Balances collateral efficiency against protection.")

   :risk-capital-efficient
   (str "Accepts up to ~5% modeled liquidation probability before your next"
        " intervention in exchange for less collateral — materially higher risk.")

   :settings-note
   (str "Sets the default target for future recommendations. It does not change"
        " any position's margin until you apply a recommendation.")

   :apply
   (str "Opens the Adjust Margin form pre-filled with the recommended top-up."
        " The recommendation is recomputed from the latest mark, funding, size,"
        " and balance first, and you approve the transaction — nothing is"
        " submitted automatically.")

   :custom
   (str "Opens the Adjust Margin form so you can enter your own amount and"
        " preview the resulting liquidation price and modeled probability before"
        " applying.")

   :close "Close the margin recommendation without making changes."

   :disclaimer
   (str "Modeled estimate, not a guarantee — rapid price gaps, oracle behavior,"
        " liquidity changes, or exchange-rule changes can trigger liquidation"
        " earlier than estimated. No automatic top-up: applying requires your"
        " approval.")})

(def buffer-tips
  "Keyed by the engine breakdown :key."
  {:maintenance
   (str "The exchange's minimum maintenance margin for this position's size and"
        " margin tier — the floor below which the position is liquidated.")
   :adverse-path
   (str "Collateral to withstand adverse simulated price paths up to your risk"
        " target, above the maintenance floor. This is not an expected loss.")
   :funding
   (str "Reserve for adverse funding payments expected over the horizon"
        " (adverse funding rate × hours × position notional).")
   :exit
   (str "Estimated cost to reduce or close the position under stressed"
        " liquidity — fees, spread, and market impact — as a fraction of"
        " notional (1.0% on named-dex markets, 0.4% on main-dex).")
   :model
   (str "Extra collateral because volatility, tails, horizon, and liquidity are"
        " uncertain. It grows when price history is short, volatility is"
        " rescaled, or the maintenance model is calibrated to the exchange's own"
        " liquidation price.")})

(defn tip [k] (get tips k))

(defn simulation-tip
  [paths-count]
  (if (number? paths-count)
    (str "Generates " (.toLocaleString paths-count "en-US") " simulated"
         " mark-price paths over the horizon and checks whether each touches the"
         " liquidation boundary. More paths reduce simulation noise but not"
         " errors in the volatility, tail, liquidity, or funding assumptions.")
    (get tips :monte-carlo)))

(defn horizon-tip
  [basis]
  (str "How long this position is likely to go unattended before you next reduce,"
       " close, or adjust it — taken as the 80th-percentile gap between your past"
       " interventions (not the average), so unusually long holds still get some"
       " protection."
       (when (seq basis) (str " Basis: " basis "."))))
