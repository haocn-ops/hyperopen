(ns hyperopen.views.portfolio.optimize.rebalance-tab
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.ids :as ids]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(defn- instrument-group-key
  [labels-by-instrument instrument-id]
  (let [value (or (get labels-by-instrument instrument-id)
                  (str instrument-id))
        unprefixed (last (str/split value #":"))
        base (first (str/split unprefixed #"[/-]"))]
    (if (seq base) base value)))

(defn- instrument-label
  [labels-by-instrument instrument-id]
  (let [value (str instrument-id)]
    (if (ids/vault-instrument-id? value)
      (or (get labels-by-instrument instrument-id)
          value)
      value)))

(defn- leg-label
  "Implementation leg shown on expanded sub-rows: spot / perp / vault name."
  [labels-by-instrument instrument-id]
  (let [value (str instrument-id)
        kind (first (str/split value #":"))]
    (case kind
      "spot" "spot"
      "perp" "perp"
      "vault" (instrument-label labels-by-instrument instrument-id)
      value)))

(defn- data-role-token
  [value]
  (-> (str value)
      (str/replace #"[^A-Za-z0-9_-]+" "-")
      (str/replace #"(^-+|-+$)" "")))

(defn- abs-num
  [value]
  (if (number? value) (js/Math.abs value) 0))

(defn- finite
  [value]
  (opt-format/finite-number? value))

(defn- format-bps
  [value]
  (if (finite value)
    (str (opt-format/format-decimal value {:maximum-fraction-digits 2}) " bps")
    nil))

(defn- format-age
  [age-ms]
  (when (finite age-ms)
    (let [seconds (js/Math.max 0 (js/Math.round (/ age-ms 1000)))]
      (if (< seconds 60)
        (str seconds "s old")
        (str (js/Math.round (/ seconds 60)) "m old")))))

(defn- depth-label
  [status]
  (case status
    :full-visible-depth "full depth"
    :insufficient-visible-depth "depth limited"
    nil))

(defn- signed-pct
  "Δ as a percentage with an explicit leading sign."
  [fraction]
  (if (finite fraction)
    (str (when (pos? fraction) "+") (opt-format/format-pct fraction))
    "—"))

(defn- side-tone
  [side]
  (case side
    :buy :long
    :sell :short
    :muted))

(defn- side-class
  [side]
  (case side
    :buy "text-trading-green"
    :sell "text-trading-red"
    "text-trading-muted"))

;; ── Cost source / freshness summary (honesty signals) ───────────────────

(defn- slippage-source-key
  [cost]
  (cond
    (and (= :snapshot (:source cost)) (:stale? cost)) :stale-snapshot
    (:source cost) (:source cost)
    :else :unknown))

(defn- counted-label
  [[source count]]
  (str count " " (opt-format/keyword-label source)))

(defn- max-age-ms
  [costs]
  (let [ages (keep :age-ms costs)]
    (when (seq ages)
      (apply max ages))))

(defn- slippage-summary-detail
  "Aggregated source + freshness signal across the ready rows, e.g.
   \"1 snapshot · max age 1s old\"."
  [preview]
  (let [costs (->> (:rows preview)
                   (filter #(= :ready (:status %)))
                   (keep :cost)
                   vec)
        source-labels (->> costs
                           (map slippage-source-key)
                           frequencies
                           (sort-by (comp str key))
                           (map counted-label))
        depth-limited-count (count (filter #(= :insufficient-visible-depth
                                               (:depth-status %))
                                           costs))
        age (format-age (max-age-ms costs))
        details (cond-> (vec source-labels)
                  (pos? depth-limited-count)
                  (conj (str depth-limited-count " depth limited"))

                  (seq age)
                  (conj (str "max age " age)))]
    (when (seq details)
      (str/join " · " details))))

(defn- chip
  [label tone]
  [:span {:class ["optimizer-chip" "border" "px-1.5" "py-[1px]" "font-mono"
                  "text-[0.53125rem]" "font-semibold" "uppercase" "tracking-[0.12em]"]
          :data-optimizer-chip "true"
          :data-tone (name tone)}
   label])

;; ── Header (Tier 4) ────────────────────────────────────────────────────

(defn- review-header
  [summary]
  (let [reviewable-count (+ (or (:ready-count summary) 0)
                            (or (:blocked-count summary) 0))]
  [:div {:class ["flex" "flex-wrap" "items-end" "justify-between" "gap-3"
                 "border-b" "border-base-300" "bg-base-100/95" "px-5" "py-3"]
         :data-role "portfolio-optimizer-rebalance-review-header"}
   [:div
    [:p {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.18em]" "text-trading-muted/70"]}
     "Rebalance · review only"]
    [:div {:class ["mt-1" "flex" "flex-wrap" "items-center" "gap-2"]}
     [:span {:class ["text-lg" "font-semibold" "tracking-[-0.01em]" "text-trading-text"]}
      "Rebalance preview"]
     [:span {:class ["text-xs" "text-trading-muted"]}
      "· no orders are sent yet"]
     (chip "review" :warn)]]
   [:div {:class ["flex" "items-center" "gap-2"]}
    [:button {:type "button"
              :class ["border" "border-base-300" "px-3" "py-2" "text-sm" "font-medium"
                      "text-trading-muted"]
              :data-role "portfolio-optimizer-rebalance-back"
              :on {:click [[:actions/set-portfolio-optimizer-results-tab :recommendation]]}}
     "Back to recommendation"]
    [:button {:type "button"
              :class ["optimizer-primary-action" "border" "px-3" "py-2" "text-sm" "font-semibold"
                      "disabled:cursor-not-allowed" "disabled:border-base-300"
                      "disabled:bg-base-200/40" "disabled:text-trading-muted"]
              :data-role "portfolio-optimizer-open-execution-modal"
              :disabled (not (pos? reviewable-count))
              :on {:click [[:actions/open-portfolio-optimizer-execution-modal]]}}
     "Stage trades for execution"]]]))

;; ── Summary KPI row (Tier 1) ───────────────────────────────────────────

(defn- kpi-block
  [{:keys [data-role label value value-class sub sub-class]}]
  [:div {:class ["optimizer-kpi-card" "border-r" "border-base-300" "px-3" "py-2.5" "last:border-r-0"]
         :data-role data-role}
   [:p {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
    label]
   [:p {:class ["mt-1" "font-mono" "text-sm" "font-semibold" "tabular-nums" (or value-class "text-trading-text")]}
    value]
   (when (seq sub)
     [:p {:class ["mt-0.5" "font-mono" "text-[0.65rem]" "tabular-nums" (or sub-class "text-trading-muted")]}
      sub])])

(defn- side-totals
  [labels-by-instrument rows]
  (reduce
   (fn [acc {:keys [side delta-notional-usd instrument-id]}]
     (let [amt (abs-num delta-notional-usd)
           asset (instrument-group-key labels-by-instrument instrument-id)]
       (case side
         :buy (-> acc (update :buys + amt) (update :buy-assets conj asset))
         :sell (-> acc (update :sells + amt) (update :sell-assets conj asset))
         acc)))
   {:buys 0 :sells 0 :buy-assets #{} :sell-assets #{}}
   rows))

(defn- summary-kpis
  [preview summary labels-by-instrument]
  (let [rows (vec (:rows preview))
        {:keys [buys sells buy-assets sell-assets]} (side-totals labels-by-instrument rows)
        fees (or (:estimated-fees-usd summary) 0)
        slip (or (:estimated-slippage-usd summary) 0)
        gross (or (:gross-trade-notional-usd summary) 0)
        total-cost (+ fees slip)
        ready (or (:ready-count summary) 0)
        blocked (or (:blocked-count summary) 0)]
    [:section {:class ["optimizer-rebalance-kpis" "grid" "grid-cols-2" "border-b" "border-base-300"
                       "bg-base-100/95" "sm:grid-cols-3" "lg:grid-cols-5"]
               :data-role "portfolio-optimizer-rebalance-summary-kpis"}
     (kpi-block {:data-role "portfolio-optimizer-rebalance-kpi-buys"
                 :label "Buys"
                 :value (str "+" (opt-format/format-usdc buys))
                 :value-class "text-trading-green"
                 :sub (str (count buy-assets) " assets")})
     (kpi-block {:data-role "portfolio-optimizer-rebalance-kpi-sells"
                 :label "Sells"
                 :value (str "−" (opt-format/format-usdc sells))
                 :value-class "text-trading-red"
                 :sub (str (count sell-assets) " assets")})
     (kpi-block {:data-role "portfolio-optimizer-rebalance-kpi-cost"
                 :label "Est. fees + slippage"
                 :value (opt-format/format-usdc total-cost)
                 :sub (slippage-summary-detail preview)})
     (kpi-block {:data-role "portfolio-optimizer-rebalance-kpi-gross"
                 :label "Gross trade"
                 :value (opt-format/format-usdc gross)
                 :sub "ready notional"})
     (kpi-block {:data-role "portfolio-optimizer-rebalance-kpi-readiness"
                 :label "Ready / blocked"
                 :value (str ready " / " blocked)
                 :sub (opt-format/keyword-label (:status preview) "preview")
                 :sub-class (if (pos? blocked) "text-warning" "text-trading-muted")})]))

;; ── Trade table (Tier 2) ───────────────────────────────────────────────

(defn- row-notes
  [row]
  (case (:status row)
    :blocked (opt-format/keyword-label (:reason row))
    :within-tolerance "within tolerance"
    :ready (->> [(when-let [source (get-in row [:cost :source])]
                   (opt-format/keyword-label source))
                 (format-age (get-in row [:cost :age-ms]))
                 (depth-label (get-in row [:cost :depth-status]))]
                (remove nil?)
                (str/join " · ")
                not-empty)
    nil))

(defn- slip-cell
  [row]
  [:td {:class ["right" "font-mono" "tabular-nums" "text-trading-muted"]}
   (or (format-bps (get-in row [:cost :slippage-bps])) "—")])

(defn- trade-row
  "A single instrument row. `leg?` renders the asset cell as an indented leg
   label; otherwise it shows the asset name."
  [labels-by-instrument asset row leg?]
  [:tr {:class (cond-> (if leg? ["optimizer-table-leg"] [])
                 (= :blocked (:status row)) (conj "optimizer-table-blocked"))
        :data-role (str "portfolio-optimizer-rebalance-row-"
                        (data-role-token (:instrument-id row)))}
   (if leg?
     [:td {:class ["pl-6"]}
      [:span {:class ["text-trading-muted"]}
       (leg-label labels-by-instrument (:instrument-id row))]]
     [:td
      [:span {:class ["font-mono" "font-semibold" "text-trading-text"]} asset]
      [:span {:class ["ml-2" "text-trading-muted/70"]}
       (leg-label labels-by-instrument (:instrument-id row))]])
   [:td (chip (name (or (:side row) :flat)) (side-tone (:side row)))]
   [:td {:class ["right" "font-mono" "tabular-nums" (side-class (:side row))]}
    (signed-pct (:delta-weight row))]
   [:td {:class ["right" "font-mono" "tabular-nums"]}
    (opt-format/format-decimal (:quantity row) {:maximum-fraction-digits 4})]
   [:td {:class ["right" "font-mono" "tabular-nums"]}
    (opt-format/format-usdc (abs-num (:delta-notional-usd row)))]
   (slip-cell row)
   [:td {:class ["text-trading-muted"]} (row-notes row)]])

(defn- group-summary-row
  [asset rows]
  (let [delta (reduce + 0 (map #(or (:delta-notional-usd %) 0) rows))
        delta-weight (reduce + 0 (map #(or (:delta-weight %) 0) rows))
        sides (set (keep :side rows))
        side (cond (= sides #{:buy}) :buy
                   (= sides #{:sell}) :sell
                   :else :mixed)
        blocked (count (filter #(= :blocked (:status %)) rows))]
    [:tr {:class ["optimizer-table-group"]}
     [:td [:span {:class ["font-mono" "font-semibold" "text-trading-text"]} asset]]
     [:td (if (= :mixed side)
            (chip "mixed" :muted)
            (chip (name side) (side-tone side)))]
     [:td {:class ["right" "font-mono" "tabular-nums" (side-class side)]}
      (signed-pct delta-weight)]
     [:td]
     [:td {:class ["right" "font-mono" "tabular-nums"]}
      (opt-format/format-usdc (abs-num delta))]
     [:td]
     [:td {:class ["text-trading-muted"]}
      (str (count rows) " legs"
           (when (pos? blocked) (str " · " blocked " blocked")))]]))

(defn- asset-tbody
  "One <tbody> per asset (carries the asset data-role); member <tr>s carry the
   per-instrument row data-role. Multi-leg assets lead with a summary row."
  [labels-by-instrument asset rows]
  (let [body-attrs {:data-role (str "portfolio-optimizer-rebalance-asset-"
                                    (data-role-token asset))}]
    (if (> (count rows) 1)
      (into [:tbody body-attrs (group-summary-row asset rows)]
            (map #(trade-row labels-by-instrument asset % true) rows))
      [:tbody body-attrs (trade-row labels-by-instrument asset (first rows) false)])))

(defn- ordered-groups
  "Group preview rows by asset, biggest absolute move first."
  [labels-by-instrument rows]
  (->> rows
       (group-by #(instrument-group-key labels-by-instrument (:instrument-id %)))
       (sort-by (fn [[_ rs]]
                  (- (reduce + 0 (map #(abs-num (:delta-notional-usd %)) rs)))))
       vec))

(defn- trade-table
  [preview labels-by-instrument]
  (let [rows (vec (:rows preview))
        groups (ordered-groups labels-by-instrument rows)]
    [:section {:class ["flex" "flex-col" "min-h-0" "xl:border-r" "border-base-300"]
               :data-role "portfolio-optimizer-rebalance-preview"}
     [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
      [:p {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.18em]" "text-trading-muted/70"]}
       "Trade summary"]
      [:p {:class ["mt-1" "text-xs" "text-trading-muted"]}
       "By asset · multi-leg assets expand to their implementation. Blocked rows stay visible with their reason."]]
     (if (seq groups)
       [:div {:class ["overflow-x-auto"]}
        (into
         [:table {:class ["optimizer-table"]}
          [:thead
           [:tr
            [:th "Asset"]
            [:th "Side"]
            [:th {:class ["right"]} "Δ%"]
            [:th {:class ["right"]} "Size"]
            [:th {:class ["right"]} "Notional"]
            [:th {:class ["right"]} "Est. slip"]
            [:th "Notes"]]]]
         (map (fn [[asset asset-rows]]
                (asset-tbody labels-by-instrument asset asset-rows))
              groups))]
       [:p {:class ["px-4" "py-4" "text-sm" "text-trading-muted"]}
        "No rebalance rows are available for this run."])]))

;; ── Right rail: what this rebalance achieves (Tier 3) ──────────────────

(defn- sharpe
  [performance return* vol]
  (or (when (finite (:in-sample-sharpe performance))
        (:in-sample-sharpe performance))
      (when (and (finite return*) (finite vol) (pos? vol))
        (/ return* vol))))

(defn- arrow-value
  [before after fmt]
  (if (finite before)
    [:span
     [:span {:class ["text-trading-muted"]} (fmt before)]
     " → "
     (fmt after)]
    (fmt after)))

(defn- outcome-diag
  ([data-role label value]
   (outcome-diag data-role label value nil nil))
  ([data-role label value sub sub-class]
   [:div {:class ["optimizer-diag"]
          :data-role data-role}
    [:div {:class ["optimizer-diag-label"]} [:span label]]
    [:div {:class ["optimizer-diag-value" "font-mono" "tabular-nums"]} value]
    (when (seq sub)
      [:div {:class ["optimizer-diag-sub" "font-mono" "tabular-nums" (or sub-class "text-trading-muted/70")]} sub])]))

(defn- delta-sub
  "Signed delta sub-line, coloured by whether the move is an improvement."
  [delta improvement? suffix]
  (when (finite delta)
    {:text (str (opt-format/format-pct-delta delta) suffix)
     :class (cond
              (zero? delta) "text-trading-muted/70"
              improvement? "text-trading-green"
              :else "text-warning")}))

(defn- achieves-panel
  [result summary]
  (let [current-vol (:current-volatility result)
        target-vol (:volatility result)
        current-return (:current-expected-return result)
        target-return (:expected-return result)
        current-sharpe (sharpe (:current-performance result) current-return current-vol)
        target-sharpe (sharpe (:performance result) target-return target-vol)
        turnover (get-in result [:diagnostics :turnover])
        margin-after (get-in summary [:margin :after-utilization])
        margin-warning (get-in summary [:margin :warning])
        margin-warn? (and margin-warning (not= :none margin-warning))
        vol-delta (when (finite current-vol) (- (or target-vol 0) current-vol))
        return-delta (when (finite current-return) (- (or target-return 0) current-return))
        sharpe-delta (when (and (finite current-sharpe) (finite target-sharpe))
                       (- target-sharpe current-sharpe))
        vol-sub (when vol-delta (delta-sub vol-delta (neg? vol-delta) " · annualized"))
        ret-sub (when return-delta (delta-sub return-delta (pos? return-delta) " · annualized"))]
    [:section {:class ["border-b" "border-base-300"]}
     [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
      [:p {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.18em]" "text-trading-muted/70"]}
       "What this rebalance achieves"]]
     (outcome-diag "portfolio-optimizer-rebalance-outcome-volatility"
                   "Volatility"
                   (arrow-value current-vol target-vol opt-format/format-pct)
                   (:text vol-sub) (:class vol-sub))
     (outcome-diag "portfolio-optimizer-rebalance-outcome-return"
                   "Expected return"
                   (arrow-value current-return target-return opt-format/format-pct)
                   (:text ret-sub) (:class ret-sub))
     (outcome-diag "portfolio-optimizer-rebalance-outcome-sharpe"
                   "Sharpe"
                   (arrow-value current-sharpe target-sharpe opt-format/format-decimal)
                   (when (finite sharpe-delta)
                     (str (when (pos? sharpe-delta) "+")
                          (opt-format/format-decimal sharpe-delta) " raw"))
                   (cond
                     (not (finite sharpe-delta)) "text-trading-muted/70"
                     (pos? sharpe-delta) "text-trading-green"
                     (neg? sharpe-delta) "text-warning"
                     :else "text-trading-muted/70"))
     (outcome-diag "portfolio-optimizer-rebalance-outcome-turnover"
                   "Turnover required"
                   (opt-format/format-pct turnover)
                   "of NAV moved"
                   "text-trading-muted/70")
     (outcome-diag "portfolio-optimizer-rebalance-margin-context"
                   "Margin after"
                   (opt-format/format-pct margin-after)
                   (when margin-warn? (opt-format/keyword-label margin-warning))
                   (if margin-warn? "text-warning" "text-trading-muted/70"))]))

(defn- blocked-reason-summary
  [preview]
  (let [reasons (->> (:rows preview)
                     (filter #(= :blocked (:status %)))
                     (keep :reason)
                     frequencies)]
    (when (seq reasons)
      (->> reasons
           (map (fn [[reason count]]
                  (str (opt-format/keyword-label reason) " ×" count)))
           (str/join ", ")))))

(defn- before-staging
  [preview]
  (let [reason-summary (blocked-reason-summary preview)]
    [:section {:class ["optimizer-note" "m-4"]
               :data-role "portfolio-optimizer-rebalance-review-caution"}
     [:p {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.12em]" "text-warning"]}
      "Before staging"]
     [:ul {:class ["mt-2" "space-y-1" "text-xs"]}
      [:li "Estimated fills assume top-of-book and recent depth; real fills will vary."]
      [:li "Review only — no orders are sent until you stage trades and confirm in the execution review."]
      (if (seq reason-summary)
        [:li {:class ["text-warning"]}
         (str "Blocked: " reason-summary ". Ready rows can still be staged separately.")]
        [:li "All proposed trades are ready to stage."])]]))

(defn rebalance-tab
  [last-successful-run]
  (let [result (:result last-successful-run)
        preview (:rebalance-preview result)
        labels-by-instrument (or (:labels-by-instrument result) {})
        summary (:summary preview)]
    (if (and (= :solved (:status result)) (map? preview))
      [:section {:class ["border" "border-base-300" "bg-base-100/95"]
                 :data-role "portfolio-optimizer-rebalance-review-surface"}
       (review-header summary)
       (summary-kpis preview summary labels-by-instrument)
       [:div {:class ["grid" "grid-cols-1" "xl:grid-cols-[minmax(0,1fr)_380px]"]}
        (trade-table preview labels-by-instrument)
        [:aside {:class ["flex" "flex-col" "min-h-0"]}
         (achieves-panel result summary)
         (before-staging preview)]]]
      [:section {:class ["optimizer-results-panel" "border" "border-base-300" "bg-base-100/95" "p-4"]
                 :data-role "portfolio-optimizer-rebalance-empty"}
       [:p {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.18em]" "text-trading-muted/70"]}
        "Rebalance preview"]
       [:p {:class ["mt-2" "text-sm" "text-trading-muted"]}
        "A rebalance preview is available after a successful optimization run."]])))
