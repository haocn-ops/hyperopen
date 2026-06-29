(ns hyperopen.views.portfolio.optimize.execution-shared
  "Pure, presentation-level helpers shared by the Execution tab surface
  (hyperopen.views.portfolio.optimize.execution-tab) and its extracted order-table
  namespace (hyperopen.views.portfolio.optimize.execution-order-table). Kept in one place
  so neither view file carries the other's bulk while both reuse identical formatting,
  order-type, and chip helpers. No behaviour lives here that isn't a direct move from the
  original execution-tab namespace."
  (:require [hyperopen.portfolio.optimizer.application.execution-order-type :as execution-order-type]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(def order-type-labels
  {:market "Market" :limit "Limit" :twap "TWAP" :passive "Passive"})

(def order-types [:market :limit :twap :passive])

(defn resting-type?
  "Limit and Passive orders rest at a price — they don't cross the book, so the
  book-crossing slippage estimate doesn't apply to them."
  [order-type]
  (contains? #{:limit :passive} order-type))

(defn crossing-type?
  "Market and TWAP orders cross the book — they pay market-impact slippage and the taker
  fee. Limit and Passive rest as maker orders: no market impact, the lower maker fee."
  [order-type]
  (not (resting-type? order-type)))

(defn abs-num [value] (if (number? value) (js/Math.abs value) 0))

(defn finite [value] (opt-format/finite-number? value))

(defn format-bps
  [value]
  (if (finite value)
    (str (opt-format/format-decimal value {:maximum-fraction-digits 1}) " bp")
    "—"))

(defn format-knotional
  "Compact $Nk notional for the dense order list aggregates."
  [value]
  (let [amount (abs-num value)]
    (if (>= amount 1000)
      (str "$" (opt-format/format-decimal (/ amount 1000) {:maximum-fraction-digits 1}) "k")
      (opt-format/format-usdc amount))))

(def recommend-exec-type execution-order-type/recommend-exec-type)
(def effective-type execution-order-type/effective-type)
(def row-params execution-order-type/row-params)

;; ── commit-moment margin / leverage copy ──────────────────────────────────
;; The execution figure is account leverage (gross notional ÷ equity, the same
;; metric the account-equity panels show as "Cross/Unified Account Leverage"),
;; not a maintenance-margin ratio. The :warning still rides margin utilization
;; (margin used ÷ equity) so a thin-headroom commit is flagged red.

(defn margin-warn?
  [margin]
  (boolean (and (:warning margin) (not= :none (:warning margin)))))

(defn format-compact-usd
  "Compact $ with M/k suffixes for headroom figures (e.g. $4.62M, $8.6k, $940)."
  [value]
  (let [amount (abs-num value)]
    (cond
      (>= amount 1e6) (str "$" (opt-format/format-decimal (/ amount 1e6) {:maximum-fraction-digits 2}) "M")
      (>= amount 1000) (str "$" (opt-format/format-decimal (/ amount 1000) {:maximum-fraction-digits 1}) "k")
      :else (opt-format/format-usdc amount {:maximum-fraction-digits 0}))))

(defn leverage-after-label
  "Projected account leverage multiple after the rebalance, e.g. \"1.85x\"."
  [margin]
  (opt-format/format-multiple (:after-gross-leverage margin)))

(defn leverage-headroom-sub
  "Sub-line under the leverage figure: prior leverage + free-margin headroom, or a
  thin-headroom caution when margin utilization is in warning range. `full?` appends
  the equity base (for the wider health rail)."
  ([margin] (leverage-headroom-sub margin false))
  ([margin full?]
   (if (margin-warn? margin)
     "thin margin headroom — review before arming"
     (let [before (:before-gross-leverage margin)
           free (:free-margin-usd margin)
           equity (:capital-usd margin)
           was (when (finite before) (str "was " (opt-format/format-multiple before)))
           head (when (finite free)
                  (str (format-compact-usd free) " free"
                       (when (and full? (finite equity))
                         (str " of " (format-compact-usd equity) " equity"))))]
       (cond
         (and was head) (str was " · " head)
         :else (or was head ""))))))

;; ── shared bits ─────────────────────────────────────────────────────────

(defn eyebrow
  [text]
  [:p {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.18em]" "text-trading-muted/70"]}
   text])

(defn chip
  [label tone]
  [:span {:class ["optimizer-chip" "border" "px-1.5" "py-[1px]" "font-mono"
                  "text-[0.53125rem]" "font-semibold" "uppercase" "tracking-[0.12em]"]
          :data-optimizer-chip "true"
          :data-tone (name tone)}
   label])
