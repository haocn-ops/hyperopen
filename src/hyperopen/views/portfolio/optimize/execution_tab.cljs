(ns hyperopen.views.portfolio.optimize.execution-tab
  "Execution surface — the step after \"Stage trades for execution\". A dense,
  phase-aware order list (staged → armed → running → done | halted) with a per-order
  type editor and an Execution-health diagnostics rail.

  Honesty: the engine submits every ready row as a single market order. The per-order
  type editor (Market/Limit/TWAP/Passive) and the arm/halt affordances are surfaced for
  review but are labelled as not-yet-wired where they don't change real routing."
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.execution-order-type :as execution-order-type]
            [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

;; ── pure helpers ────────────────────────────────────────────────────────

(def ^:private order-type-labels
  {:market "Market" :limit "Limit" :twap "TWAP" :passive "Passive"})

(def ^:private order-types [:market :limit :twap :passive])

(defn- resting-type?
  "Limit and Passive orders rest at a price — they don't cross the book, so the
  book-crossing slippage estimate doesn't apply to them."
  [order-type]
  (contains? #{:limit :passive} order-type))

(defn- abs-num [value] (if (number? value) (js/Math.abs value) 0))
(defn- finite [value] (opt-format/finite-number? value))

(defn- data-role-token
  "Selector-safe data-role suffix (instrument ids carry colons/slashes)."
  [value]
  (-> (str value)
      (str/replace #"[^A-Za-z0-9_-]+" "-")
      (str/replace #"(^-+|-+$)" "")))

(defn- format-bps
  [value]
  (if (finite value)
    (str (opt-format/format-decimal value {:maximum-fraction-digits 1}) " bp")
    "—"))

(defn- format-knotional
  "Compact $Nk notional for the dense order list."
  [value]
  (let [amount (abs-num value)]
    (if (>= amount 1000)
      (str "$" (opt-format/format-decimal (/ amount 1000) {:maximum-fraction-digits 1}) "k")
      (opt-format/format-usdc amount))))

(defn- rec-reason
  [order-type]
  (case order-type
    :twap "large clip — slice over time to limit market impact"
    :limit "liquid spot sell — rest at mid and capture the spread"
    :market "small clip — immediacy outweighs impact"
    "medium position — post passively, avoid crossing the spread"))

(def ^:private recommend-exec-type execution-order-type/recommend-exec-type)
(def ^:private effective-type execution-order-type/effective-type)
(def ^:private row-params execution-order-type/row-params)

(defn- editable?
  [{:keys [phase read-only?]}]
  (and (contains? #{:staged :armed} phase) (not read-only?)))

(defn- venue-label
  [row]
  (case (:instrument-type row)
    (:perp :spot) "Hyperliquid"
    "—"))

;; ── row state glyphs ────────────────────────────────────────────────────

(def ^:private state-glyph
  {:filled "✓"
   :resting "○"
   :failed "✕"
   :blocked "–"
   :skipped "–"
   :working "◐"
   :queued "○"
   :staged "○"})

(defn- row-display-state
  [{:keys [phase]} row]
  (case (:status row)
    :submitted :filled
    ;; Accepted and live on the book, but NOT filled — an open order.
    :resting :resting
    :failed :failed
    :blocked :blocked
    :skipped :skipped
    :working :working
    :ready (if (contains? #{:armed :running} phase) :queued :staged)
    :staged))

(defn- side-tone
  [side]
  (case side :buy :long :sell :short :muted))

(defn- fill-counts
  "Status breakdown of the displayed order rows so every summary surface agrees and a resting
  (open) order is never reported as filled. :total is the run denominator — queued/working +
  accepted (filled or resting) + failed — i.e. every row that belongs to the run."
  [rows]
  (let [by (frequencies (map :status rows))
        filled (get by :submitted 0)
        resting (get by :resting 0)
        failed (get by :failed 0)
        blocked (get by :blocked 0)
        ready (+ (get by :ready 0) (get by :working 0))]
    {:filled filled
     :resting resting
     :failed failed
     :blocked blocked
     :ready ready
     :total (+ ready filled resting failed)}))

;; ── shared bits ─────────────────────────────────────────────────────────

(defn- eyebrow
  [text]
  [:p {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.18em]" "text-trading-muted/70"]}
   text])

(defn- chip
  [label tone]
  [:span {:class ["optimizer-chip" "border" "px-1.5" "py-[1px]" "font-mono"
                  "text-[0.53125rem]" "font-semibold" "uppercase" "tracking-[0.12em]"]
          :data-optimizer-chip "true"
          :data-tone (name tone)}
   label])

(defn- status-tag
  [phase]
  (let [[label tone] (case phase
                       :done ["complete" :long]
                       :resting ["resting" :info]
                       :halted ["halted" :short]
                       :running ["executing" :live]
                       :armed ["armed" :warn]
                       ["staged" :accent])]
    (chip label tone)))

;; ── header ──────────────────────────────────────────────────────────────

(defn- subtitle
  [phase]
  (case phase
    :done "· all orders filled"
    :resting "· orders resting on the book"
    :halted "· halted — partial fills sent"
    :running "· sending live orders"
    :armed "· armed — confirm to send"
    "· staged from rebalance preview"))

(defn- overflow-menu
  [{:keys [phase]}]
  [:details {:class ["optimizer-exec-overflow"]
             :data-role "portfolio-optimizer-execution-overflow"}
   [:summary {:class ["border" "border-base-300" "px-3" "py-2" "text-sm" "font-medium"
                      "text-trading-muted" "select-none"]}
    "⋯"]
   [:div {:class ["optimizer-exec-overflow-menu" "border" "border-base-300" "bg-base-100" "py-1"]}
    [:button {:type "button"
              :class ["block" "w-full" "px-3" "py-1.5" "text-left" "text-xs" "text-trading-text"]
              :data-role "portfolio-optimizer-execution-open-ticket"
              :on {:click [[:actions/open-portfolio-optimizer-execution-in-ticket]]}}
     "Open in trade ticket ↗"]
    (when-not (= :running phase)
      [:button {:type "button"
                :class ["block" "w-full" "px-3" "py-1.5" "text-left" "text-xs" "text-trading-red"]
                :data-role "portfolio-optimizer-execution-discard"
                :on {:click [[:actions/discard-portfolio-optimizer-execution]]}}
       "Abort & discard"])]])

(defn- header
  [{:keys [phase read-only? disabled-message] :as model}]
  [:div {:class ["flex" "flex-wrap" "items-end" "justify-between" "gap-3"
                 "border-b" "border-base-300" "bg-base-100/95" "px-5" "py-3"]
         :data-role "portfolio-optimizer-execution-header"}
   [:div
    (eyebrow "Execution · review and commit")
    [:div {:class ["mt-1" "flex" "flex-wrap" "items-center" "gap-2"]}
     [:span {:class ["text-lg" "font-semibold" "tracking-[-0.01em]" "text-trading-text"]}
      "Execution"]
     [:span {:class ["text-xs" "text-trading-muted"]} (subtitle phase)]
     (status-tag phase)]]
   [:div {:class ["flex" "items-center" "gap-2"]}
    (overflow-menu model)
    (if (= :done phase)
      [:button {:type "button"
                :class ["border" "border-base-300" "px-3" "py-2" "text-sm" "font-medium" "text-trading-muted"]
                :data-role "portfolio-optimizer-execution-view-tracking"
                :on {:click [[:actions/set-portfolio-optimizer-results-tab :tracking]]}}
       "View tracking →"]
      [:button {:type "button"
                :class ["border" "border-base-300" "px-3" "py-2" "text-sm" "font-medium" "text-trading-muted"]
                :data-role "portfolio-optimizer-execution-back"
                :on {:click [[:actions/set-portfolio-optimizer-results-tab :rebalance]]}}
       "Back to preview"])
    (when (= :staged phase)
      [:button {:type "button"
                :class ["optimizer-primary-action" "border" "px-3" "py-2" "text-sm" "font-semibold"
                        "disabled:cursor-not-allowed" "disabled:border-base-300"
                        "disabled:bg-base-200/40" "disabled:text-trading-muted"]
                :data-role "portfolio-optimizer-execution-arm"
                :disabled (boolean read-only?)
                :title (when read-only? disabled-message)
                :on (when-not read-only?
                      {:click [[:actions/set-portfolio-optimizer-execution-phase :armed]]})}
       "Arm execution"])]])

;; ── control band (one per phase) ────────────────────────────────────────

(defn- order-summary-line
  [{:keys [default-order-type overrides] :as model} rows]
  (let [counts (frequencies (map #(effective-type model %)
                                 (filter #(= :ready (:status %)) rows)))
        parts (->> order-types
                   (keep (fn [t] (when-let [n (get counts t)]
                                   (str n " " (str/lower-case (order-type-labels t))))))
                   (str/join " · "))]
    (when (seq parts) parts)))

(defn- mode-tile
  [{:keys [id title sub active? read-only?]}]
  [:button {:type "button"
            :class (cond-> ["optimizer-exec-mode-tile" "flex" "flex-col" "gap-0.5" "px-3" "py-2"
                            "border-l" "border-base-300" "text-left"]
                     active? (conj "is-active"))
            :data-role (str "portfolio-optimizer-execution-mode-" (name id))
            :data-active (str (boolean active?))
            :disabled (boolean read-only?)
            :on (when-not read-only?
                  {:click [[:actions/set-portfolio-optimizer-execution-default-order-type id]]})}
   [:span {:class ["flex" "items-center" "gap-2"]}
    [:span {:class ["optimizer-exec-mode-dot"]}]
    [:span {:class ["text-xs" "font-medium" "text-trading-text"]} title]]
   [:span {:class ["pl-4" "font-mono" "text-[0.6rem]" "text-trading-muted"]} sub]])

(defn- staged-band
  [{:keys [default-order-type read-only? disabled-message] :as model} rows]
  [:div {:class ["border-b" "border-base-300"]
         :data-role "portfolio-optimizer-execution-control-band"
         :data-phase "staged"}
   (when read-only?
     [:p {:class ["optimizer-exec-readonly" "border-b" "border-base-300" "px-5" "py-2"
                  "text-xs" "font-semibold" "text-warning"]
          :data-role "portfolio-optimizer-execution-readonly"}
      disabled-message])
   [:div {:class ["flex" "items-stretch"]}
    [:div {:class ["flex" "flex-col" "justify-center" "gap-0.5" "px-5" "py-2" "shrink-0"]}
     [:span {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
      "Default order type"]
     [:span {:class ["font-mono" "text-[0.58rem]" "text-trading-muted"]}
      "override any order below →"]]
    [:div {:class ["flex" "flex-1" "border-l" "border-base-300"]}
     (mode-tile {:id :recommended :title "Recommended" :sub "Algo picks the best type per order"
                 :active? (= :recommended default-order-type) :read-only? read-only?})
     (mode-tile {:id :market :title "Market" :sub "Cross the spread on every order"
                 :active? (= :market default-order-type) :read-only? read-only?})
     (mode-tile {:id :limit :title "Limit" :sub "Rest every order at a price"
                 :active? (= :limit default-order-type) :read-only? read-only?})
     (mode-tile {:id :twap :title "TWAP" :sub "Work every order over time"
                 :active? (= :twap default-order-type) :read-only? read-only?})]]
   [:p {:class ["border-t" "border-base-300" "bg-base-200/30" "px-5" "py-1.5"
                "font-mono" "text-[0.6rem]" "text-trading-muted"]}
    "Recommended routes each order by clip size. Limit / Passive rest as maker orders and may not fully fill; TWAP works over time."]])

(defn- armed-band
  [{:keys [confirm-disabled? disabled-message] :as model} rows]
  (let [order-count (count (filter #(= :ready (:status %)) rows))
        summary (order-summary-line model rows)]
    [:div {:class ["optimizer-exec-band" "is-armed" "flex" "items-center" "gap-4"
                   "border-b" "border-base-300" "px-5" "py-3"]
           :data-role "portfolio-optimizer-execution-control-band"
           :data-phase "armed"}
     (chip "confirm" :warn)
     [:div {:class ["min-w-0"]}
      [:p {:class ["text-[0.8125rem]" "font-medium" "text-trading-text"]}
       (str "Send " order-count " live order" (when (not= 1 order-count) "s") " to Hyperliquid?")]
      [:p {:class ["mt-0.5" "font-mono" "text-[0.65rem]" "text-trading-muted"]}
       (str (when (seq summary) (str "Order types: " summary ". "))
            "Each order is sent with its selected type. This cannot be undone without reverting filled trades.")]]
     [:span {:class ["flex-1"]}]
     [:button {:type "button"
               :class ["border" "border-base-300" "px-3" "py-2" "text-sm" "font-medium" "text-trading-muted"]
               :data-role "portfolio-optimizer-execution-cancel"
               :on {:click [[:actions/set-portfolio-optimizer-execution-phase :staged]]}}
      "Cancel"]
     [:button {:type "button"
               :class ["optimizer-primary-action" "border" "px-3" "py-2" "text-sm" "font-semibold"
                       "disabled:cursor-not-allowed" "disabled:border-base-300"
                       "disabled:bg-base-200/40" "disabled:text-trading-muted"]
               :data-role "portfolio-optimizer-execution-confirm"
               :disabled (boolean confirm-disabled?)
               :title (when confirm-disabled? disabled-message)
               :on (when-not confirm-disabled?
                     {:click [[:actions/confirm-portfolio-optimizer-execution]]})}
      "Confirm & send →"]]))

(defn- running-band
  [{:keys [summary] :as model} rows]
  (let [{:keys [filled resting total]} (fill-counts rows)
        ;; Progress tracks orders the exchange has accepted (filled or resting), not just fills,
        ;; so the bar advances as passive orders land on the book.
        accepted (+ filled resting)
        pct (if (pos? total) (/ accepted total) 0)]
    [:div {:class ["optimizer-exec-band" "is-running" "flex" "items-center" "gap-4"
                   "border-b" "border-base-300" "px-5" "py-3"]
           :data-role "portfolio-optimizer-execution-control-band"
           :data-phase "running"}
     [:span {:class ["optimizer-exec-pulse"]}]
     [:div {:class ["shrink-0"]}
      [:p {:class ["text-[0.8125rem]" "text-trading-text"]} "Submitting live orders"]
      [:p {:class ["mt-0.5" "font-mono" "text-[0.65rem]" "text-trading-muted"]}
       (str filled " filled"
            (when (pos? resting) (str " · " resting " resting"))
            " / " total)]]
     [:div {:class ["optimizer-exec-progress" "flex-1" "max-w-[520px]"]}
      [:div {:class ["optimizer-exec-progress-fill"]
             :style {:width (str (* 100 pct) "%")}}
       [:span {:class ["optimizer-exec-progress-shimmer"]}]]]
     [:span {:class ["font-mono" "text-xs" "text-warning"]}
      (str (js/Math.round (* 100 pct)) "%")]
     [:button {:type "button"
               :class ["border" "border-trading-red/60" "px-3" "py-2" "text-sm" "font-medium" "text-trading-red"]
               :data-role "portfolio-optimizer-execution-pause"
               :title "Stop releasing new orders. In-flight orders still settle."
               :on {:click [[:actions/pause-portfolio-optimizer-execution]]}}
      "Pause / abort"]]))

(defn- done-band
  []
  [:div {:class ["optimizer-exec-band" "is-done" "flex" "items-center" "gap-4"
                 "border-b" "border-base-300" "px-5" "py-3"]
         :data-role "portfolio-optimizer-execution-control-band"
         :data-phase "done"}
   (chip "complete" :long)
   [:p {:class ["text-[0.8125rem]" "font-medium" "text-trading-text"]}
    "Execution complete — all orders acknowledged."]
   [:span {:class ["flex-1"]}]
   [:button {:type "button"
             :class ["optimizer-primary-action" "border" "px-3" "py-2" "text-sm" "font-semibold"]
             :data-role "portfolio-optimizer-execution-view-tracking"
             :on {:click [[:actions/set-portfolio-optimizer-results-tab :tracking]]}}
    "View tracking →"]])

(defn- halted-band
  [{:keys [error confirm-disabled?] :as model} rows]
  (let [{:keys [filled resting failed]} (fill-counts rows)
        resume-from (some (fn [[i row]] (when (= :failed (:status row)) (inc i)))
                          (map-indexed vector rows))]
    [:div {:class ["optimizer-exec-band" "is-halted" "flex" "items-center" "gap-4"
                   "border-b" "border-base-300" "px-5" "py-3"]
           :data-role "portfolio-optimizer-execution-control-band"
           :data-phase "halted"}
     (chip "halted" :short)
     [:div {:class ["min-w-0"]}
      [:p {:class ["text-[0.8125rem]" "font-medium" "text-trading-text"]}
       (str "Execution halted — " filled " filled · "
            (when (pos? resting) (str resting " resting · "))
            failed " failed")]
      [:p {:class ["mt-0.5" "font-mono" "text-[0.65rem]" "text-trading-muted"]}
       (or error "One or more orders were rejected. Subsequent orders are never auto-retried.")]]
     [:span {:class ["flex-1"]}]
     [:button {:type "button"
               :class ["border" "border-trading-red/60" "px-3" "py-2" "text-sm" "font-medium" "text-trading-red"
                       "disabled:cursor-not-allowed" "disabled:border-base-300" "disabled:text-trading-muted"]
               :data-role "portfolio-optimizer-execution-revert"
               :disabled (zero? filled)
               :title (when (zero? filled) "No filled orders to revert.")
               :on (when (pos? filled)
                     {:click [[:actions/revert-portfolio-optimizer-execution-filled]]})}
      "Revert filled"]
     [:button {:type "button"
               :class ["border" "border-base-300" "px-3" "py-2" "text-sm" "font-medium" "text-trading-muted"]
               :data-role "portfolio-optimizer-execution-restage"
               :on {:click [[:actions/restage-portfolio-optimizer-execution-smaller]]}}
      "Re-stage smaller"]
     [:button {:type "button"
               :class ["optimizer-primary-action" "border" "px-3" "py-2" "text-sm" "font-semibold"
                       "disabled:cursor-not-allowed" "disabled:border-base-300"
                       "disabled:bg-base-200/40" "disabled:text-trading-muted"]
               :data-role "portfolio-optimizer-execution-resume"
               :disabled (boolean confirm-disabled?)
               :on (when-not confirm-disabled?
                     {:click [[:actions/resume-portfolio-optimizer-execution]]})}
      (if resume-from (str "Resume from #" resume-from) "Resume")]]))

(defn- resting-band
  [_model rows]
  (let [{:keys [filled resting]} (fill-counts rows)]
    [:div {:class ["optimizer-exec-band" "is-done" "flex" "items-center" "gap-4"
                   "border-b" "border-base-300" "px-5" "py-3"]
           :data-role "portfolio-optimizer-execution-control-band"
           :data-phase "resting"}
     (chip "resting" :info)
     [:div {:class ["min-w-0"]}
      [:p {:class ["text-[0.8125rem]" "font-medium" "text-trading-text"]}
       (str resting " order" (when (not= 1 resting) "s") " resting on the book"
            (when (pos? filled)
              (str " · " filled " filled outright")))]
      [:p {:class ["mt-0.5" "font-mono" "text-[0.65rem]" "text-trading-muted"]}
       "Open limit orders are live on Hyperliquid — they fill as the market reaches your price. Manage or cancel them from the trade ticket."]]
     [:span {:class ["flex-1"]}]]))

(defn- control-band
  [{:keys [phase] :as model} rows]
  (case phase
    :armed (armed-band model rows)
    :running (running-band model rows)
    :done (done-band)
    :resting (resting-band model rows)
    :halted (halted-band model rows)
    (staged-band model rows)))

;; ── KPI strip ───────────────────────────────────────────────────────────

(defn- crossing-type?
  "Market and TWAP orders cross the book — they pay market-impact slippage and the taker
  fee. Limit and Passive rest as maker orders: no market impact, the lower maker fee."
  [order-type]
  (not (resting-type? order-type)))

(defn- type-aware-costs
  "Recomputes price cost (spread + book impact) + fees from each row's LIVE effective order
  type so the KPI strip and health rail react to type changes without re-staging. Crossing
  (market/twap) rows keep their spread + impact + taker fee; resting (limit/passive) rows
  contribute no spread/impact and the maker fee. Returns the totals, the spread/impact split,
  the maker/taker split, and the crossing-row price-cost bps samples for the average."
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

(defn- fee-mix-label
  [{:keys [taker-count maker-count]}]
  (cond
    (and (pos? taker-count) (pos? maker-count)) (str taker-count " taker · " maker-count " maker")
    (pos? maker-count) "maker · resting rows"
    :else "taker · ready rows"))

(defn- price-cost-split-text
  "\"spread $X + impact $Y\" for the crossing rows, or nil when nothing crosses the book."
  [{:keys [spread-usd impact-usd taker-count]}]
  (when (pos? taker-count)
    (str "spread " (opt-format/format-usdc spread-usd)
         " + impact " (opt-format/format-usdc impact-usd))))

(defn- price-cost-sub
  [costs avg-bps]
  (cond
    (price-cost-split-text costs)
    (str (price-cost-split-text costs)
         (when avg-bps (str " · " (format-bps avg-bps) " avg")))
    (pos? (+ (:taker-count costs) (:maker-count costs))) "resting — no market impact"
    :else "no ready rows"))

(defn- kpi
  [{:keys [data-role label value value-class sub info]}]
  [:div {:class ["optimizer-kpi-card" "border-r" "border-base-300" "px-3" "py-2.5" "last:border-r-0"]
         :data-role data-role}
   [:p {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]
        :title info}
    label
    (when info [:span {:class ["ml-1" "cursor-help" "text-trading-muted/50"]} "ⓘ"])]
   [:p {:class ["mt-1" "font-mono" "text-sm" "font-semibold" "tabular-nums" (or value-class "text-trading-text")]}
    value]
   (when (seq sub)
     [:p {:class ["mt-0.5" "font-mono" "text-[0.65rem]" "tabular-nums" "text-trading-muted"]} sub])])

(defn- kpi-strip
  [{:keys [summary phase] :as model} rows]
  (let [ready (filter #(contains? #{:ready :working} (:status %)) rows)
        submitted (filter #(= :submitted (:status %)) rows)
        resting (filter #(= :resting (:status %)) rows)
        failed (filter #(= :failed (:status %)) rows)
        blocked (filter #(= :blocked (:status %)) rows)
        total (+ (count ready) (count submitted) (count resting) (count failed))
        filled (count submitted)
        resting-count (count resting)
        ;; "Executed" notional is filled-only — a resting (open) order has not executed.
        filled-notional (reduce + 0 (map #(abs-num (:delta-notional-usd %)) submitted))
        staged-notional (or (:gross-ready-notional-usd summary)
                            (reduce + 0 (map #(abs-num (:delta-notional-usd %))
                                             (concat ready submitted resting failed))))
        ;; Slippage + fees recomputed from each row's LIVE effective order type, so the KPIs
        ;; update as the user toggles types (resting => no impact + maker fee, crossing =>
        ;; impact + taker fee) without re-staging. Covers ready/working (pre-run) and accepted
        ;; (submitted + resting, post-run) rows so the fee total survives a run.
        costs (type-aware-costs model (concat ready submitted resting))
        slip-bps-samples (:slip-bps costs)
        avg-bps (when (seq slip-bps-samples)
                  (/ (reduce + 0 slip-bps-samples) (count slip-bps-samples)))
        ;; Realized slippage is recoverable only post-run, off filled rows (the effect
        ;; stamps :realized; resting/unfilled rows have none). Never relabel the estimate.
        post-run? (contains? #{:done :resting :halted} phase)
        realized-rows (filter #(get-in % [:realized :slippage-bps]) submitted)
        realized-bps (when (seq realized-rows)
                       (/ (reduce + 0 (map #(get-in % [:realized :slippage-bps]) realized-rows))
                          (count realized-rows)))
        realized-usd (reduce + 0 (keep #(get-in % [:realized :slippage-usd]) submitted))
        show-realized? (and post-run? (seq realized-rows))
        ;; Price cost = spread + impact (realized fill cost post-run); all-in = price cost + fees.
        price-cost-usd (if show-realized? realized-usd (:slippage-usd costs))
        all-in-usd (+ price-cost-usd (:fees-usd costs))
        margin (:margin summary)
        margin-warn? (and (:warning margin) (not= :none (:warning margin)))
        orders-value (str filled " / " total)]
    [:section {:class ["optimizer-rebalance-kpis" "grid" "grid-cols-2" "border-b" "border-base-300"
                       "bg-base-100/95" "sm:grid-cols-3" "lg:grid-cols-6"]
               :data-role "portfolio-optimizer-execution-kpis"}
     (kpi {:data-role "portfolio-optimizer-execution-kpi-orders"
           :label "Orders filled"
           :value orders-value
           :value-class (cond (= :halted phase) "text-trading-red"
                              (= :done phase) "text-trading-green"
                              (= :resting phase) "text-info"
                              :else "text-trading-text")
           :sub (cond (pos? (count blocked)) (str (count blocked) " blocked")
                      (pos? resting-count) (str resting-count " resting on book")
                      (= :done phase) "all venues acked"
                      :else "awaiting release")})
     (kpi {:data-role "portfolio-optimizer-execution-kpi-notional"
           :label "Notional executed"
           :value (format-knotional filled-notional)
           :sub (str "of " (format-knotional staged-notional) " staged")})
     (kpi {:data-role "portfolio-optimizer-execution-kpi-margin"
           :label "Margin after"
           :value (opt-format/format-pct (:after-utilization margin))
           :value-class (if margin-warn? "text-trading-red" "text-trading-text")
           :sub (if margin-warn?
                  (opt-format/keyword-label (:warning margin))
                  "post-rebalance maint.")})
     (if show-realized?
       (kpi {:data-role "portfolio-optimizer-execution-kpi-price-cost"
             :label "Realized price cost"
             :info "What you actually paid versus the mark the estimate used."
             :value (opt-format/format-usdc realized-usd)
             :sub (str "≈ " (format-bps realized-bps) " avg · est "
                       (if avg-bps (format-bps avg-bps) "—"))})
       (kpi {:data-role "portfolio-optimizer-execution-kpi-price-cost"
             :label "Est. price cost"
             :info "Price paid to execute = crossing the spread + walking the book (impact). Resting Limit/Passive orders pay neither."
             :value (opt-format/format-usdc (:slippage-usd costs))
             :sub (price-cost-sub costs avg-bps)}))
     (kpi {:data-role "portfolio-optimizer-execution-kpi-fees"
           :label "Est. fees"
           :info "Exchange fees: taker for crossing orders, the lower maker fee for resting ones."
           :value (opt-format/format-usdc (:fees-usd costs))
           :sub (fee-mix-label costs)})
     (kpi {:data-role "portfolio-optimizer-execution-kpi-all-in"
           :label (if show-realized? "Realized all-in" "Est. all-in cost")
           :info "Total cost to execute = price cost + fees."
           :value (opt-format/format-usdc all-in-usd)
           :sub "price cost + fees"})]))

;; ── order table ─────────────────────────────────────────────────────────

(defn- cost-source-label
  [row]
  (let [cost (:cost row)]
    (->> [(when-let [source (:source cost)] (opt-format/keyword-label source))
          (case (:depth-status cost)
            :insufficient-visible-depth "depth limited"
            nil)]
         (remove nil?)
         (str/join " · ")
         not-empty)))

(defn- state-cell
  [display-state row]
  (case display-state
    :filled [:span {:class ["text-trading-green"]} "filled"]
    :resting [:span {:class ["text-info"]} "open"]
    :failed [:span {:class ["text-trading-red"]}
             (str "rejected" (when (:reason row) (str " · " (opt-format/keyword-label (:reason row)))))]
    :blocked [:span {:class ["text-trading-muted"]} (opt-format/keyword-label (:reason row))]
    :skipped [:span {:class ["text-trading-muted"]} "skipped"]
    :working [:span {:class ["text-warning"]} "sending…"]
    :queued [:span {:class ["text-warning"]} "queued"]
    [:span {:class ["text-trading-muted"]} "staged"]))

(defn- cost-stat
  "One term in the execution-cost equation: a small uppercase label, the bp value (large for
  glanceability), and the $ underneath. `emphasis` tunes the hierarchy — :input for the
  spread/impact/fee inputs (muted), :total for the price-cost subtotal (bright), :allin for
  the boxed accent total."
  [label bps usd emphasis]
  [:div {:class ["optimizer-exec-cost-stat"] :data-emphasis (name emphasis)}
   [:span {:class ["optimizer-exec-cost-stat-label"]} label]
   [:span {:class ["optimizer-exec-cost-stat-bp"]}
    (if (finite bps) (format-bps bps) "—")]
   [:span {:class ["optimizer-exec-cost-stat-usd"]}
    (if (finite usd) (opt-format/format-usdc usd) "—")]])

(defn- cost-op
  [glyph]
  [:span {:class ["optimizer-exec-cost-op"]} glyph])

(defn- cost-breakdown
  "Per-row execution-cost components for the row's effective type. Crossing (market/twap):
  spread crossing + book impact = price cost, + taker fee = all-in. Resting (limit/passive):
  no spread/impact (rests), + maker fee = all-in. A crossing row whose book can't be split
  (untrusted snapshot / flat fallback / prebaked) is `splittable?`=false: spread/impact are
  unknown (nil, not a deceptive 0), and the strip collapses them into a single honest note."
  [model row]
  (let [t (effective-type model row)
        crossing? (crossing-type? t)
        cost (:cost row)
        splittable? (and crossing? (some? (:spread-usd cost)))
        price-cost-usd (if crossing? (or (:estimated-slippage-usd cost) 0) 0)
        price-cost-bps (if crossing? (or (:slippage-bps cost) 0) 0)
        fee-usd (if crossing? (or (:estimated-fee-usd cost) 0) (or (:maker-fee-usd cost) 0))
        fee-bps (if crossing? (or (:fee-bps cost) 0) (or (:maker-fee-bps cost) 0))]
    {:crossing? crossing?
     :splittable? splittable?
     :spread-bps (when splittable? (:spread-bps cost))
     :spread-usd (when splittable? (:spread-usd cost))
     :impact-bps (when splittable? (:impact-bps cost))
     :impact-usd (when splittable? (:impact-usd cost))
     :price-cost-bps price-cost-bps :price-cost-usd price-cost-usd
     :fee-bps fee-bps :fee-usd fee-usd
     :all-in-bps (+ price-cost-bps fee-bps) :all-in-usd (+ price-cost-usd fee-usd)}))

(defn- cost-breakdown-strip
  "The right-hand column of the expanded editor: the execution-cost equation laid out across
  the full width — spread crossing + book impact = price cost, + fees = all-in (each in bp and
  $). A resting Limit/Passive row pays neither spread nor impact, so those two terms collapse
  into a single \"rests\" note and the price cost reads ~0."
  [model row]
  (let [{:keys [crossing? splittable? spread-bps spread-usd impact-bps impact-usd
                price-cost-bps price-cost-usd fee-bps fee-usd all-in-bps all-in-usd]}
        (cost-breakdown model row)]
    [:div {:class ["optimizer-exec-cost-panel"]
           :data-role "portfolio-optimizer-execution-cost-breakdown"}
     [:p {:class ["optimizer-exec-cost-head"]}
      [:span "Execution cost breakdown (est.)"]
      [:span {:class ["optimizer-exec-cost-info"]
              :title (str "Price cost = crossing the spread + walking the book (impact). "
                          "All-in adds exchange fees. Resting Limit/Passive orders pay "
                          "neither spread nor impact and earn the lower maker fee.")}
       "ⓘ"]]
     [:div {:class ["optimizer-exec-cost-eq"]}
      (cond
        ;; A crossing row with a real book: show the spread vs impact split.
        splittable?
        (list (cost-stat "Spread crossing" spread-bps spread-usd :input)
              (cost-op "+")
              (cost-stat "Book impact" impact-bps impact-usd :input))

        ;; A crossing row whose book can't be split (untrusted snapshot / flat fallback):
        ;; the spread is unknown — say so honestly instead of rendering a deceptive 0 bp.
        crossing?
        [:div {:class ["optimizer-exec-cost-rests"]
               :data-role "portfolio-optimizer-execution-cost-unsplit"}
         [:span {:class ["optimizer-exec-cost-stat-label"]} "Spread + impact"]
         [:span {:class ["optimizer-exec-cost-rests-note"]}
          "Not separable — flat estimate (no live book)"]]

        ;; A resting Limit/Passive row pays neither.
        :else
        [:div {:class ["optimizer-exec-cost-rests"]}
         [:span {:class ["optimizer-exec-cost-stat-label"]} "Resting order"]
         [:span {:class ["optimizer-exec-cost-rests-note"]} "No spread or market impact"]])
      (cost-op "=")
      (cost-stat "Price cost" price-cost-bps price-cost-usd :total)
      (cost-op "+")
      (cost-stat "Fees" fee-bps fee-usd :input)
      (cost-op "=")
      (cost-stat "All-in" all-in-bps all-in-usd :allin)]]))

(defn- order-editor-row
  [model row colspan]
  (let [t (effective-type model row)
        rec (recommend-exec-type row)
        params (row-params model row)
        buy? (= :buy (:side row))
        row-id (:row-id row)
        source (cost-source-label row)]
    [:tr {:data-role (str "portfolio-optimizer-execution-order-editor-" (data-role-token (:instrument-id row)))}
     [:td {:colspan colspan :class ["optimizer-exec-order-editor"]}
      [:div {:class ["optimizer-exec-editor-grid"]}
       ;; LEFT — order-type controls + plain-English consequence
       [:div {:class ["optimizer-exec-editor-controls"]}
        [:div {:class ["flex" "flex-wrap" "items-center" "gap-3"]}
         [:span {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted"]}
          (str "Order type · " (:instrument-label row))]
         [:div {:class ["optimizer-exec-toggle" "inline-flex"]}
          (for [ot order-types]
            [:button {:type "button"
                      :class (cond-> ["px-2.5" "py-1" "text-[0.65rem]" "font-medium"]
                               (= t ot) (conj "is-on"))
                      :data-active (str (= t ot))
                      :on {:click [[:actions/set-portfolio-optimizer-execution-row-order-type row-id ot]]}}
             (order-type-labels ot)])]
         (if (not= t rec)
           [:button {:type "button"
                     :class ["font-mono" "text-[0.65rem]" "text-warning"]
                     :on {:click [[:actions/set-portfolio-optimizer-execution-row-order-type row-id :recommended]]}}
            (str "↺ use recommended (" (order-type-labels rec) ")")]
           (chip "recommended" :accent))]
        [:div {:class ["flex" "flex-wrap" "items-center" "gap-2" "text-xs" "text-trading-muted"]}
         (case t
           :market
           [:span "Crosses the spread immediately — full size as one marketable order."]
           :limit
           [:span {:class ["flex" "flex-wrap" "items-center" "gap-2"]}
            [:span {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]}
             "Limit price"]
            (for [[label bp] [["At mid" 0]
                              [(str (if buy? "−" "+") "2 bp") (if buy? -2 2)]
                              [(str (if buy? "−" "+") "5 bp") (if buy? -5 5)]]]
              [:button {:type "button"
                        :class (cond-> ["border" "border-base-300" "px-2" "py-0.5" "text-[0.65rem]"]
                                 (= (:limit-bps params) bp) (conj "optimizer-primary-action" "font-semibold"))
                        :on {:click [[:actions/set-portfolio-optimizer-execution-row-param row-id :limit-bps bp]]}}
               label])
            [:span {:class ["font-mono" "text-trading-muted/70"]}
             (str "rests " (if buy? "below" "above") " mark · GTC")]]
           :twap
           [:span {:class ["flex" "flex-wrap" "items-center" "gap-2"]}
            [:span {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]}
             "Duration"]
            (for [m [5 10 20]]
              [:button {:type "button"
                        :class (cond-> ["border" "border-base-300" "px-2" "py-0.5" "text-[0.65rem]"]
                                 (= (:twap-min params) m) (conj "optimizer-primary-action" "font-semibold"))
                        :on {:click [[:actions/set-portfolio-optimizer-execution-row-param row-id :twap-min m]]}}
               (str m " min")])
            [:span {:class ["font-mono" "text-trading-muted/70"]}
             (str (max 2 (js/Math.round (/ (:twap-min params) 2))) " slices · even spacing")]]
           [:span "Post-only at the best price — never crosses the spread, re-pegs as the book moves."])]
        [:p {:class ["font-mono" "text-[0.6rem]" "text-trading-muted/70"]}
         (str "Recommended: " (order-type-labels rec) " — " (rec-reason rec))]
        (when source
          [:p {:class ["font-mono" "text-[0.6rem]" "text-trading-muted/50"]}
           (str "Cost basis · " source)])]
       ;; RIGHT — execution-cost equation across the remaining width
       (cost-breakdown-strip model row)]]]))

(defn- slip-cell
  "Type-aware slippage display. After a fill the realized slippage (vs the same mark the
  estimate used) takes over. Pre-fill: resting orders (limit/passive) read \"rests\" rather
  than the book-crossing market-impact estimate — which would badly overstate their cost;
  crossing orders (market/twap) show the impact estimate; non-ready rows show \"—\"."
  [order-type status est-slip realized-slip]
  (cond
    (finite realized-slip)
    [:span {:title "Realized fill vs the mark the estimate used."} (format-bps realized-slip)]

    (and (= :ready status) (resting-type? order-type))
    [:span {:title "Resting order — pays the spread/offset, not market impact; may not fully fill."}
     "rests"]

    :else (format-bps est-slip)))

(defn- order-row
  [{:keys [open-row] :as model} index row]
  (let [editable (editable? model)
        open? (and editable (= open-row (:row-id row)))
        display-state (row-display-state model row)
        t (effective-type model row)
        overridden? (contains? (:overrides model) (:row-id row))
        side (:side row)
        buy? (= :buy side)
        notional (:delta-notional-usd row)
        row-tr
        [:tr {:class (cond-> ["optimizer-exec-order-row"]
                       (= :failed display-state) (conj "is-failed")
                       (contains? #{:blocked :skipped} display-state) (conj "is-muted")
                       open? (conj "is-open"))
              :data-role (str "portfolio-optimizer-execution-order-row-" (data-role-token (:instrument-id row)))
              :data-exec-state (name display-state)
              :on (when editable
                    {:click [[:actions/toggle-portfolio-optimizer-execution-row (:row-id row)]]})}
         [:td {:class ["optimizer-exec-glyph"] :data-state (name display-state)}
          (state-glyph display-state)]
         [:td {:class ["num" "text-trading-muted/70"]} (opt-format/format-decimal (inc index) {:maximum-fraction-digits 0})]
         [:td [:span {:class ["font-mono" "font-semibold" "text-trading-text"]} (:instrument-label row)]]
         [:td (chip (name (or side :flat)) (side-tone side))]
         [:td
          [:span {:class ["text-[0.7rem]" "text-trading-muted"]} (venue-label row)]
          (when (:instrument-type row)
            [:span {:class ["optimizer-table-kind-badge" "ml-1.5"]
                    :data-kind (name (:instrument-type row))}
             (name (:instrument-type row))])]
         [:td
          [:span {:class ["inline-flex" "items-center" "gap-1.5"]}
           (when overridden? [:span {:class ["optimizer-exec-override-dot"]}])
           [:span {:class (cond-> ["optimizer-exec-type-chip"] overridden? (conj "is-overridden"))}
            (order-type-labels t)]
           (when editable [:span {:class ["text-[0.6rem]" "text-trading-muted/70"]} (if open? "▾" "▸")])]]
         [:td {:class ["num" "right"]} (opt-format/format-decimal (:quantity row) {:maximum-fraction-digits 4})]
         [:td {:class ["num" "right" (if buy? "text-trading-green" "text-trading-red")]}
          (str (if buy? "+" "−") (format-knotional notional))]
         [:td {:class ["num" "right" "text-trading-muted"]}
          (slip-cell t (:status row) (get-in row [:cost :slippage-bps]) (get-in row [:realized :slippage-bps]))]
         [:td {:class ["text-[0.7rem]"]} (state-cell display-state row)]]]
    (if open?
      [row-tr (order-editor-row model row 10)]
      [row-tr])))

(defn- row-visible?
  [order-filter display-state]
  (case order-filter
    ;; A resting order is a live, working order on the book — surface it under "Working".
    :working (contains? #{:queued :working :resting} display-state)
    :filled (= :filled display-state)
    true))

(defn- order-filter-toggle
  [active]
  (into [:div {:class ["optimizer-exec-toggle" "inline-flex"]
               :data-role "portfolio-optimizer-execution-order-filter"}]
        (for [[id label] [[:all "All"] [:working "Working"] [:filled "Filled"]]]
          [:button {:type "button"
                    :class (cond-> ["px-2.5" "py-1" "text-[0.65rem]" "font-medium"]
                             (= active id) (conj "is-on"))
                    :data-active (str (= active id))
                    :on {:click [[:actions/set-portfolio-optimizer-execution-order-filter id]]}}
           label])))

(defn- order-table
  [{:keys [order-filter] :as model} rows]
  (let [active-filter (or order-filter :all)
        any-visible? (some #(row-visible? active-filter (row-display-state model %)) rows)]
    [:section {:class ["flex" "flex-col" "min-h-0" "xl:border-r" "border-base-300"]
               :data-role "portfolio-optimizer-execution-order-list"}
     [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
      [:div {:class ["flex" "flex-wrap" "items-center" "justify-between" "gap-2"]}
       (eyebrow "Order list")
       (order-filter-toggle active-filter)]
      [:p {:class ["mt-1" "text-xs" "text-trading-muted"]}
       (if (editable? model)
         "Click any order to change its type. Blocked rows stay visible with their reason."
         "Order types are locked once execution is armed.")]]
     (cond
       (not (seq rows))
       [:p {:class ["px-4" "py-4" "text-sm" "text-trading-muted"]}
        "No orders are staged for this run."]

       (not any-visible?)
       [:p {:class ["px-4" "py-4" "text-sm" "text-trading-muted"]}
        (str "No " (name active-filter) " orders to show.")]

       :else
       [:div {:class ["overflow-x-auto"]}
        (into
         [:table {:class ["optimizer-table" "optimizer-exec-table"]}
          [:thead
           [:tr
            [:th {:class ["w-8"]}]
            [:th {:class ["w-10"]} "#"]
            [:th "Asset"]
            [:th "Side"]
            [:th "Venue"]
            [:th "Type"]
            [:th {:class ["right"]} "Qty"]
            [:th {:class ["right"]} "Notional"]
            [:th {:class ["right"]} "Cost"]
            [:th "State"]]]]
         ;; index over the full row set so the # column keeps stable order numbers
         ;; even when a filter hides rows.
         (mapcat (fn [index row]
                   (if (row-visible? active-filter (row-display-state model row))
                     (order-row model index row)
                     []))
                 (range)
                 rows))])]))

;; ── Execution-health rail ───────────────────────────────────────────────

(defn- diag
  ([label value sub] (diag label value sub nil nil))
  ([label value sub status-label status-class]
   [:div {:class ["optimizer-diag"]}
    [:div {:class ["optimizer-diag-label"]}
     [:span label]
     (when status-label [:span {:class (cond-> ["optimizer-diag-status"] status-class (conj status-class))} status-label])]
    [:div {:class ["optimizer-diag-value" "font-mono" "tabular-nums"]} value]
    (when (seq sub)
      [:div {:class ["optimizer-diag-sub" "font-mono" "tabular-nums" "text-trading-muted/70"]} sub])]))

(defn- health-note
  [{:keys [phase]}]
  (let [[title items tone-class] (case phase
                                   :halted ["Halted — your move"
                                            ["Resume retries only the failed rows — already-filled orders are never re-sent."
                                             "Re-stage smaller re-stages the unfilled rows at half size for a fresh arm."
                                             "Revert filled sends reduce-only orders to unwind the filled trades."]
                                            "text-trading-red"]
                                   :done ["Execution complete"
                                          ["All ready orders were acknowledged by Hyperliquid."
                                           "Tracking has started against the recommendation."]
                                          "text-trading-green"]
                                   :resting ["Orders resting on the book"
                                             ["Your passive limit orders are live on Hyperliquid and fill as the market reaches your price — they are not filled yet."
                                              "Nothing else is sent automatically. Manage or cancel them from the trade ticket; Re-stage isn't needed."]
                                             "text-info"]
                                   :running ["Live — do not close"
                                             ["Each ready row is submitted with its selected order type."
                                              "Margin and slippage were checked when the plan was staged."]
                                             "text-trading-muted"]
                                   ["Before you arm"
                                    ["Estimated fills assume top-of-book and recent depth; real fills vary."
                                     "Arming requires a second confirm. No orders are live until then."
                                     "Each order routes by its selected type — Limit / Passive rest as maker orders, TWAP works over time."]
                                    "text-trading-muted"])]
    [:div {:class ["p-3.5" "border-t" "border-base-300"]}
     [:div {:class ["optimizer-note"]
            :data-phase (name phase)}
      [:p {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.12em]" tone-class]} title]
      [:ul {:class ["mt-2" "space-y-1" "text-xs"]}
       (for [item items] [:li item])]]]))

(defn- health-rail
  [{:keys [summary phase] :as model} rows]
  (let [ready (filter #(contains? #{:ready :working} (:status %)) rows)
        submitted (filter #(= :submitted (:status %)) rows)
        resting (filter #(= :resting (:status %)) rows)
        failed (filter #(= :failed (:status %)) rows)
        total (+ (count ready) (count submitted) (count resting) (count failed))
        filled (count submitted)
        ;; Fill progress tracks fills only — a resting order has not filled, so the bar stays
        ;; honest (it does not advance for orders merely accepted onto the book).
        pct (if (pos? total) (/ filled total) 0)
        margin (:margin summary)
        margin-warn? (and (:warning margin) (not= :none (:warning margin)))
        ;; Same live, type-aware recompute as the KPI strip so the rail agrees with it.
        costs (type-aware-costs model (concat ready submitted resting))
        sources (->> (concat ready submitted resting)
                     (keep #(get-in % [:cost :source]))
                     frequencies
                     (map (fn [[s n]] (str n " " (opt-format/keyword-label s))))
                     (str/join " · ")
                     not-empty)]
    [:aside {:class ["optimizer-exec-rail" "bg-base-100/95" "overflow-y-auto"]
             :data-role "portfolio-optimizer-execution-health"}
     [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
      (eyebrow "Execution health")]
     (diag "Fill progress"
           [:span filled [:span {:class ["text-trading-muted/70" "text-sm"]} (str " / " total)]]
           [:div {:class ["optimizer-exec-progress" "mt-2"]
                  :data-phase (name phase)}
            [:div {:class ["optimizer-exec-progress-fill"]
                   :style {:width (str (* 100 pct) "%")}}]]
           (case phase :done "complete" :resting "resting" :halted "halted" :running "live" "staged")
           (case phase :halted "text-trading-red" :running "text-warning" :resting "text-info" nil))
     (diag "Cross-margin after"
           (opt-format/format-pct (:after-utilization margin))
           (if margin-warn?
             "review margin headroom before arming"
             "post-rebalance maintenance margin")
           (if margin-warn? "breach" "ok")
           (if margin-warn? "text-trading-red" nil))
     (diag "Est. price cost"
           (opt-format/format-usdc (:slippage-usd costs))
           (or (not-empty (str/join " · " (remove nil? [(price-cost-split-text costs) sources])))
               "no ready rows sampled"))
     (diag "Est. fees"
           (opt-format/format-usdc (:fees-usd costs))
           (str (fee-mix-label costs) " on ready notional"))
     (diag "Est. all-in cost"
           (opt-format/format-usdc (+ (:slippage-usd costs) (:fees-usd costs)))
           "price cost + fees")
     (health-note model)]))

;; ── latest attempt (retry context) ──────────────────────────────────────

(defn- latest-attempt-panel
  [{:keys [latest-attempt phase]}]
  (when (and (contains? #{:halted :done :resting} phase) (seq (:rows latest-attempt)))
    [:section {:class ["border-t" "border-base-300" "bg-base-200/20" "px-4" "py-4"]
               :data-role "portfolio-optimizer-execution-latest-attempt"}
     [:div {:class ["flex" "items-start" "justify-between" "gap-3"]}
      [:div
       (eyebrow "Latest attempt")
       [:p {:class ["mt-1" "text-xs" "text-trading-muted"]}
        "The most recent execution result, including any rejected rows."]]
      (chip (opt-format/keyword-label (:status latest-attempt))
            (case (:status latest-attempt)
              :executed :long
              :resting :info
              (:failed :partially-executed) :short
              :muted))]
     (into
      [:table {:class ["optimizer-table" "mt-3"]}
       [:thead [:tr [:th "Instrument"] [:th "Status"] [:th "Side"] [:th {:class ["right"]} "Notional"] [:th "Detail"]]]]
      (for [row (:rows latest-attempt)]
        [:tbody
         [:tr
          [:td [:span {:class ["font-mono" "font-semibold" "text-trading-text"]} (:instrument-label row)]]
          [:td (opt-format/keyword-label (:status row))]
          [:td (opt-format/keyword-label (:side row))]
          [:td {:class ["right" "num"]} (format-knotional (:delta-notional-usd row))]
          [:td {:class ["text-trading-muted"]}
           (or (get-in row [:error :message]) (opt-format/keyword-label (:reason row)) "—")]]]))]))

;; ── entry point ─────────────────────────────────────────────────────────

(defn execution-tab
  [state]
  (let [{:keys [has-plan?] :as model} (optimizer-view-model/execution-tab-model state)
        rows (:rows model)]
    [:section {:class ["portfolio-optimizer-execution-tab" "border" "border-base-300" "bg-base-100/95"]
               :data-role "portfolio-optimizer-execution-tab"}
     (if has-plan?
       [:div
        (header model)
        (control-band model rows)
        (kpi-strip model rows)
        [:div {:class ["grid" "grid-cols-1" "xl:grid-cols-[minmax(0,1fr)_380px]"]}
         (order-table model rows)
         (health-rail model rows)]
        (latest-attempt-panel model)]
       [:div {:class ["p-6"]
              :data-role "portfolio-optimizer-execution-empty"}
        (eyebrow "Execution")
        [:p {:class ["mt-2" "text-sm" "text-trading-muted"]}
         "Stage a rebalance to review and commit trades. Run or load a scenario, then open the Rebalance preview and choose “Stage trades for execution.”"]])]))
