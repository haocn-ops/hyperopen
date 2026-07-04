(ns hyperopen.views.portfolio.optimize.execution-shared
  "Pure, presentation-level helpers shared by the Execution tab surface
  (hyperopen.views.portfolio.optimize.execution-tab), its extracted order-table
  namespace (hyperopen.views.portfolio.optimize.execution-order-table), and the
  execution-strategy band (hyperopen.views.portfolio.optimize.execution-strategy-band).
  Kept in one place so no view file carries the others' bulk while all reuse identical
  formatting, order-type, cost-recompute, and chip helpers."
  (:require [hyperopen.portfolio.optimizer.application.execution-order-type :as execution-order-type]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(def order-type-labels
  ;; "Passive maker" (not bare "Passive") everywhere the type is named: post-only maker
  ;; posture is the meaning, and it must never read as a synonym for Limit.
  {:market "Market" :limit "Limit" :twap "TWAP" :passive "Passive maker"})

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
(def high-cost-crossing-bps execution-order-type/high-cost-crossing-bps)
(def high-cost-crossing-row? execution-order-type/high-cost-crossing-row?)
(def crossing-cost-bps execution-order-type/crossing-cost-bps)

;; ── live type-aware cost recompute ────────────────────────────────────────

(defn type-aware-costs
  "Recomputes price cost (spread + book impact) + fees from each row's LIVE effective order
  type so the KPI strip, health rail, and strategy tiles react to type changes without
  re-staging. Crossing (market/twap) rows keep their spread + impact + taker fee; resting
  (limit/passive) rows contribute no spread/impact and the maker fee. Returns the totals,
  the spread/impact split, the maker/taker split, and the crossing-row price-cost bps
  samples for the average."
  [model rows]
  (reduce
   (fn [acc row]
     (let [crossing? (crossing-type? (effective-type model row))
           cost (:cost row)
           slip-bps (:slippage-bps cost)
           slip-usd (if crossing? (or (:estimated-slippage-usd cost) 0) 0)
           has-split? (some? (:spread-usd cost))
           spread-usd (if (and crossing? has-split?) (:spread-usd cost) 0)
           ;; Attribute an un-splittable crossing cost (flat fallback / no book) entirely to
           ;; impact so spread + impact always reconciles to the price-cost total.
           impact-usd (cond (not crossing?) 0
                            has-split? (or (:impact-usd cost) 0)
                            :else slip-usd)]
       (cond-> acc
         true (update :slippage-usd + slip-usd)
         true (update :spread-usd + spread-usd)
         true (update :impact-usd + impact-usd)
         true (update :fees-usd + (if crossing?
                                    (or (:estimated-fee-usd cost) 0)
                                    (or (:maker-fee-usd cost) 0)))
         true (update (if crossing? :taker-count :maker-count) inc)
         (and crossing? (finite slip-bps)) (update :slip-bps conj (abs-num slip-bps)))))
   {:slippage-usd 0 :spread-usd 0 :impact-usd 0 :fees-usd 0
    :taker-count 0 :maker-count 0 :slip-bps []}
   rows))

(defn fee-mix-label
  [{:keys [taker-count maker-count]}]
  (cond
    (and (pos? taker-count) (pos? maker-count)) (str taker-count " taker · " maker-count " maker")
    (pos? maker-count) "maker · resting rows"
    :else "taker · ready rows"))

(defn price-cost-split-text
  "\"spread $X + impact $Y\" for the crossing rows, or nil when nothing crosses the book."
  [{:keys [spread-usd impact-usd taker-count]}]
  (when (pos? taker-count)
    (str "spread " (opt-format/format-usdc spread-usd)
         " + impact " (opt-format/format-usdc impact-usd))))

(defn type-mix-summary
  "\"7 market · 3 passive maker\" summary of the effective types across the given rows,
  in the canonical order-type order; nil when there are no rows."
  [model rows]
  (let [counts (frequencies (map #(effective-type model %) rows))
        parts (keep (fn [t]
                      (when-let [n (get counts t)]
                        (str n " " (.toLowerCase (order-type-labels t)))))
                    order-types)]
    (when (seq parts)
      (apply str (interpose " · " parts)))))

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
