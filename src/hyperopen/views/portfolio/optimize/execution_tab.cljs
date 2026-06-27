(ns hyperopen.views.portfolio.optimize.execution-tab
  "Execution surface — the step after \"Stage trades for execution\". A dense,
  phase-aware order list (staged → armed → running → done | halted) with a per-order
  type editor and an Execution-health diagnostics rail.

  Honesty: the engine submits every ready row as a single market order. The per-order
  type editor (Market/Limit/TWAP/Passive) and the arm/halt affordances are surfaced for
  review but are labelled as not-yet-wired where they don't change real routing."
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

;; ── pure helpers ────────────────────────────────────────────────────────

(def ^:private order-type-labels
  {:market "Market" :limit "Limit" :twap "TWAP" :passive "Passive"})

(def ^:private order-types [:market :limit :twap :passive])

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

(defn- signed-bps
  [value]
  (if (finite value)
    (str (when (pos? value) "+") (opt-format/format-decimal value {:maximum-fraction-digits 1}) " bp")
    "—"))

(defn- format-knotional
  "Compact $Nk notional for the dense order list."
  [value]
  (let [amount (abs-num value)]
    (if (>= amount 1000)
      (str "$" (opt-format/format-decimal (/ amount 1000) {:maximum-fraction-digits 1}) "k")
      (opt-format/format-usdc amount))))

(defn- recommend-exec-type
  "Algorithm-recommended order type per row (per the execution design spec)."
  [{:keys [instrument-type side delta-notional-usd]}]
  (let [amount (abs-num delta-notional-usd)]
    (cond
      (>= amount 70000) :twap
      (and (= :sell side) (= :spot instrument-type)) :limit
      (<= amount 22000) :market
      :else :passive)))

(defn- rec-reason
  [order-type]
  (case order-type
    :twap "large clip — slice over time to limit market impact"
    :limit "liquid spot sell — rest at mid and capture the spread"
    :market "small clip — immediacy outweighs impact"
    "medium position — post passively, avoid crossing the spread"))

(defn- effective-type
  [{:keys [default-order-type overrides]} row]
  (or (get overrides (:row-id row))
      (if (= :recommended default-order-type)
        (recommend-exec-type row)
        default-order-type)))

(defn- row-params
  [{:keys [params]} row]
  (merge {:limit-bps (if (= :buy (:side row)) -2 2)
          :twap-min (if (>= (abs-num (:delta-notional-usd row)) 70000) 20 10)}
         (get params (:row-id row))))

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
   :failed "✕"
   :blocked "–"
   :skipped "–"
   :queued "○"
   :staged "○"})

(defn- row-display-state
  [{:keys [phase]} row]
  (case (:status row)
    :submitted :filled
    :failed :failed
    :blocked :blocked
    :skipped :skipped
    :ready (if (contains? #{:armed :running} phase) :queued :staged)
    :staged))

(defn- side-tone
  [side]
  (case side :buy :long :sell :short :muted))

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
    :halted "· halted — partial fills sent"
    :running "· sending live orders"
    :armed "· armed — confirm to send"
    "· staged from rebalance preview"))

(defn- header
  [{:keys [phase read-only? disabled-message]}]
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
    "Live orders submit as Market — Limit / TWAP / Passive preview routing is not yet wired."]])

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
            "Live orders submit as Market. This cannot be undone without reverting filled trades.")]]
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
  (let [total (count (filter #(contains? #{:ready :submitted :failed} (:status %)) rows))
        filled (count (filter #(= :submitted (:status %)) rows))
        pct (if (pos? total) (/ filled total) 0)]
    [:div {:class ["optimizer-exec-band" "is-running" "flex" "items-center" "gap-4"
                   "border-b" "border-base-300" "px-5" "py-3"]
           :data-role "portfolio-optimizer-execution-control-band"
           :data-phase "running"}
     [:span {:class ["optimizer-exec-pulse"]}]
     [:div {:class ["shrink-0"]}
      [:p {:class ["text-[0.8125rem]" "text-trading-text"]} "Submitting live orders"]
      [:p {:class ["mt-0.5" "font-mono" "text-[0.65rem]" "text-trading-muted"]}
       (str filled " / " total " filled")]]
     [:div {:class ["optimizer-exec-progress" "flex-1" "max-w-[520px]"]}
      [:div {:class ["optimizer-exec-progress-fill"]
             :style {:width (str (* 100 pct) "%")}}
       [:span {:class ["optimizer-exec-progress-shimmer"]}]]]
     [:span {:class ["font-mono" "text-xs" "text-warning"]}
      (str (js/Math.round (* 100 pct)) "%")]]))

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
  (let [failed (count (filter #(= :failed (:status %)) rows))
        filled (count (filter #(= :submitted (:status %)) rows))]
    [:div {:class ["optimizer-exec-band" "is-halted" "flex" "items-center" "gap-4"
                   "border-b" "border-base-300" "px-5" "py-3"]
           :data-role "portfolio-optimizer-execution-control-band"
           :data-phase "halted"}
     (chip "halted" :short)
     [:div {:class ["min-w-0"]}
      [:p {:class ["text-[0.8125rem]" "font-medium" "text-trading-text"]}
       (str "Execution halted — " filled " filled · " failed " failed")]
      [:p {:class ["mt-0.5" "font-mono" "text-[0.65rem]" "text-trading-muted"]}
       (or error "One or more orders were rejected. Subsequent orders are never auto-retried.")]]
     [:span {:class ["flex-1"]}]
     [:button {:type "button"
               :class ["border" "border-base-300" "px-3" "py-2" "text-sm" "font-medium" "text-trading-muted"
                       "cursor-not-allowed" "opacity-60"]
               :data-role "portfolio-optimizer-execution-revert"
               :disabled true
               :title "Revert is not yet wired."}
      "Revert filled"]
     [:button {:type "button"
               :class ["border" "border-base-300" "px-3" "py-2" "text-sm" "font-medium" "text-trading-muted"
                       "cursor-not-allowed" "opacity-60"]
               :data-role "portfolio-optimizer-execution-restage"
               :disabled true
               :title "Re-stage is not yet wired."}
      "Re-stage smaller"]
     [:button {:type "button"
               :class ["optimizer-primary-action" "border" "px-3" "py-2" "text-sm" "font-semibold"
                       "disabled:cursor-not-allowed" "disabled:border-base-300"
                       "disabled:bg-base-200/40" "disabled:text-trading-muted"]
               :data-role "portfolio-optimizer-execution-resume"
               :disabled (boolean confirm-disabled?)
               :on (when-not confirm-disabled?
                     {:click [[:actions/confirm-portfolio-optimizer-execution]]})}
      "Resume"]]))

(defn- control-band
  [{:keys [phase] :as model} rows]
  (case phase
    :armed (armed-band model rows)
    :running (running-band model rows)
    :done (done-band)
    :halted (halted-band model rows)
    (staged-band model rows)))

;; ── KPI strip ───────────────────────────────────────────────────────────

(defn- kpi
  [{:keys [data-role label value value-class sub]}]
  [:div {:class ["optimizer-kpi-card" "border-r" "border-base-300" "px-3" "py-2.5" "last:border-r-0"]
         :data-role data-role}
   [:p {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
    label]
   [:p {:class ["mt-1" "font-mono" "text-sm" "font-semibold" "tabular-nums" (or value-class "text-trading-text")]}
    value]
   (when (seq sub)
     [:p {:class ["mt-0.5" "font-mono" "text-[0.65rem]" "tabular-nums" "text-trading-muted"]} sub])])

(defn- kpi-strip
  [{:keys [summary phase] :as model} rows]
  (let [ready (filter #(= :ready (:status %)) rows)
        submitted (filter #(= :submitted (:status %)) rows)
        failed (filter #(= :failed (:status %)) rows)
        blocked (filter #(= :blocked (:status %)) rows)
        total (+ (count ready) (count submitted) (count failed))
        filled (count submitted)
        filled-notional (reduce + 0 (map #(abs-num (:delta-notional-usd %)) submitted))
        staged-notional (or (:gross-ready-notional-usd summary)
                            (reduce + 0 (map #(abs-num (:delta-notional-usd %))
                                             (concat ready submitted failed))))
        sampled (filter #(get-in % [:cost :slippage-bps]) (concat ready submitted))
        avg-bps (when (seq sampled)
                  (/ (reduce + 0 (map #(abs-num (get-in % [:cost :slippage-bps])) sampled))
                     (count sampled)))
        margin (:margin summary)
        margin-warn? (and (:warning margin) (not= :none (:warning margin)))
        orders-value (if (= :done phase) (str filled " / " total) (str filled " / " total))]
    [:section {:class ["optimizer-rebalance-kpis" "grid" "grid-cols-2" "border-b" "border-base-300"
                       "bg-base-100/95" "sm:grid-cols-3" "lg:grid-cols-5"]
               :data-role "portfolio-optimizer-execution-kpis"}
     (kpi {:data-role "portfolio-optimizer-execution-kpi-orders"
           :label "Orders filled"
           :value orders-value
           :value-class (cond (= :halted phase) "text-trading-red"
                              (= :done phase) "text-trading-green"
                              :else "text-trading-text")
           :sub (cond (pos? (count blocked)) (str (count blocked) " blocked")
                      (= :done phase) "all venues acked"
                      :else "awaiting release")})
     (kpi {:data-role "portfolio-optimizer-execution-kpi-notional"
           :label "Notional executed"
           :value (format-knotional filled-notional)
           :sub (str "of " (format-knotional staged-notional) " staged")})
     (kpi {:data-role "portfolio-optimizer-execution-kpi-slippage"
           :label "Est. slippage"
           :value (opt-format/format-usdc (or (:estimated-slippage-usd summary) 0))
           :sub (if avg-bps (str "≈ " (format-bps avg-bps) " avg") "no ready rows")})
     (kpi {:data-role "portfolio-optimizer-execution-kpi-margin"
           :label "Margin after"
           :value (opt-format/format-pct (:after-utilization margin))
           :value-class (if margin-warn? "text-trading-red" "text-trading-text")
           :sub (if margin-warn?
                  (opt-format/keyword-label (:warning margin))
                  "post-rebalance maint.")})
     (kpi {:data-role "portfolio-optimizer-execution-kpi-fees"
           :label "Est. fees"
           :value (opt-format/format-usdc (or (:estimated-fees-usd summary) 0))
           :sub "taker · ready rows"})]))

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
    :failed [:span {:class ["text-trading-red"]}
             (str "rejected" (when (:reason row) (str " · " (opt-format/keyword-label (:reason row)))))]
    :blocked [:span {:class ["text-trading-muted"]} (opt-format/keyword-label (:reason row))]
    :skipped [:span {:class ["text-trading-muted"]} "skipped"]
    :queued [:span {:class ["text-warning"]} "queued"]
    [:span {:class ["text-trading-muted"]} "staged"]))

(defn- order-editor-row
  [model row colspan]
  (let [t (effective-type model row)
        rec (recommend-exec-type row)
        params (row-params model row)
        slip (abs-num (get-in row [:cost :slippage-bps]))
        est (case t :market slip :twap (* slip 0.6) (* slip 0.4))
        buy? (= :buy (:side row))
        row-id (:row-id row)]
    [:tr {:data-role (str "portfolio-optimizer-execution-order-editor-" (data-role-token (:instrument-id row)))}
     [:td {:colspan colspan :class ["optimizer-exec-order-editor"]}
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
         (chip "recommended" :accent))
       [:span {:class ["flex-1"]}]
       [:span {:class ["font-mono" "text-[0.65rem]" "text-trading-muted"]}
        (str "est. fill " (signed-bps (- est)))]]
      [:div {:class ["mt-2.5" "flex" "flex-wrap" "items-center" "gap-2" "text-xs" "text-trading-muted"]}
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
      [:p {:class ["mt-2" "font-mono" "text-[0.6rem]" "text-trading-muted/70"]}
       (str "Recommended: " (order-type-labels rec) " — " (rec-reason rec)
            " · order routing for non-market types is not yet wired.")]]]))

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
          (format-bps (get-in row [:cost :slippage-bps]))]
         [:td {:class ["text-[0.7rem]"]} (state-cell display-state row)]]]
    (if open?
      [row-tr (order-editor-row model row 10)]
      [row-tr])))

(defn- order-table
  [{:keys [phase] :as model} rows]
  [:section {:class ["flex" "flex-col" "min-h-0" "xl:border-r" "border-base-300"]
             :data-role "portfolio-optimizer-execution-order-list"}
   [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
    (eyebrow "Order list")
    [:p {:class ["mt-1" "text-xs" "text-trading-muted"]}
     (if (editable? model)
       "Click any order to change its type. Blocked rows stay visible with their reason."
       "Order types are locked once execution is armed.")]]
   (if (seq rows)
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
          [:th {:class ["right"]} "Slip"]
          [:th "State"]]]]
       (mapcat (fn [index row] (order-row model index row))
               (range)
               rows))]
     [:p {:class ["px-4" "py-4" "text-sm" "text-trading-muted"]}
      "No orders are staged for this run."])])

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
                                            ["Resume retries the still-ready rows — orders are never auto-retried."
                                             "Re-stage / Revert are not yet wired."
                                             "Already-filled orders remain live."]
                                            "text-trading-red"]
                                   :done ["Execution complete"
                                          ["All ready orders were acknowledged by Hyperliquid."
                                           "Tracking has started against the recommendation."]
                                          "text-trading-green"]
                                   :running ["Live — do not close"
                                             ["Each ready row submits as a market order."
                                              "Margin and slippage were checked when the plan was staged."]
                                             "text-trading-muted"]
                                   ["Before you arm"
                                    ["Estimated fills assume top-of-book and recent depth; real fills vary."
                                     "Arming requires a second confirm. No orders are live until then."
                                     "Limit / TWAP / Passive selections are advisory — live orders submit as Market."]
                                    "text-trading-muted"])]
    [:div {:class ["p-3.5" "border-t" "border-base-300"]}
     [:div {:class ["optimizer-note"]
            :data-phase (name phase)}
      [:p {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.12em]" tone-class]} title]
      [:ul {:class ["mt-2" "space-y-1" "text-xs"]}
       (for [item items] [:li item])]]]))

(defn- health-rail
  [{:keys [summary phase] :as model} rows]
  (let [ready (filter #(= :ready (:status %)) rows)
        submitted (filter #(= :submitted (:status %)) rows)
        failed (filter #(= :failed (:status %)) rows)
        total (+ (count ready) (count submitted) (count failed))
        filled (count submitted)
        pct (if (pos? total) (/ filled total) 0)
        margin (:margin summary)
        margin-warn? (and (:warning margin) (not= :none (:warning margin)))
        sampled (filter #(get-in % [:cost :slippage-bps]) (concat ready submitted))
        sources (->> (concat ready submitted)
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
           (case phase :done "complete" :halted "halted" :running "live" "staged")
           (case phase :halted "text-trading-red" :running "text-warning" nil))
     (diag "Cross-margin after"
           (opt-format/format-pct (:after-utilization margin))
           (if margin-warn?
             "review margin headroom before arming"
             "post-rebalance maintenance margin")
           (if margin-warn? "breach" "ok")
           (if margin-warn? "text-trading-red" nil))
     (diag "Estimated slippage"
           (opt-format/format-usdc (or (:estimated-slippage-usd summary) 0))
           (or sources "no ready rows sampled"))
     (diag "Estimated fees"
           (opt-format/format-usdc (or (:estimated-fees-usd summary) 0))
           "taker fees on ready notional")
     (health-note model)]))

;; ── latest attempt (retry context) ──────────────────────────────────────

(defn- latest-attempt-panel
  [{:keys [latest-attempt phase]}]
  (when (and (contains? #{:halted :done} phase) (seq (:rows latest-attempt)))
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
