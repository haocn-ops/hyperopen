(ns hyperopen.views.portfolio.optimize.execution-strategy-band
  "The staged-phase control band of the Execution tab: the execution-strategy selector
  (Recommended / Market / Passive maker / TWAP) with live projected costs per strategy,
  plus the high-cost-crossing warning. Extracted from
  hyperopen.views.portfolio.optimize.execution-tab so the strategy surface and its
  warning fit within the namespace size budget.

  Honesty contract: every tile's consequence line is projected from the SAME
  type-aware cost recompute the KPI strip uses, over the state the click would actually
  produce (current per-row overrides preserved) — so the selector is a real tradeoff
  comparison, never marketing copy. The high-cost warning shares its 20bp threshold with
  the routing policy (execution-order-type/high-cost-crossing-bps), so Recommended can
  never route a row the warning still flags."
  (:require [hyperopen.views.portfolio.optimize.execution-shared :as shared]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(defn- ready-rows [rows] (filterv #(= :ready (:status %)) rows))

(defn- selections-for
  "The staging selections that clicking the given strategy tile would produce: the
  default flips, per-row overrides and params stay."
  [model strategy]
  {:default-order-type strategy
   :overrides (or (:overrides model) {})
   :params (or (:params model) {})})

(defn- projected-all-in-usd
  [model strategy rows]
  (let [costs (shared/type-aware-costs (selections-for model strategy) rows)]
    (+ (:slippage-usd costs) (:fees-usd costs))))

(def ^:private strategies
  [{:id :recommended
    :title "Recommended"
    :sub "Routes each order by cost and size"}
   {:id :market
    :title "Market"
    :sub "Cross the spread on every order — fastest"}
   {:id :passive
    :title "Passive maker"
    :sub "Post-only maker orders — may not fill"}
   {:id :twap
    :title "TWAP"
    :sub "Work every order over time"}])

(defn- mode-tile
  [{:keys [id title sub consequence active? read-only?]}]
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
   [:span {:class ["pl-4" "font-mono" "optimizer-exec-mode-sub" "text-trading-muted"]} sub]
   (when (seq consequence)
     [:span {:class ["pl-4" "font-mono" "optimizer-exec-mode-sub" "tabular-nums" "text-trading-text/80"]
             :data-role (str "portfolio-optimizer-execution-mode-" (name id) "-consequence")}
      consequence])])

(defn- tile-consequence
  "The live projected consequence line for one strategy tile: the est. all-in cost of
  the plan under that strategy (with current overrides kept), plus the routing mix for
  Recommended so 'the algo picks' is inspectable at a glance. nil with no ready rows."
  [model strategy sendable]
  (when (seq sendable)
    (let [all-in (projected-all-in-usd model strategy sendable)
          cost-text (str "est. all-in " (opt-format/format-usdc all-in))]
      (if (= :recommended strategy)
        (let [mix (shared/type-mix-summary (selections-for model :recommended) sendable)]
          (if mix (str mix " · " cost-text) cost-text))
        cost-text))))

;; ── high-cost crossing warning ─────────────────────────────────────────────

(defn high-cost-crossing-rows
  "Ready rows whose EFFECTIVE type under the current selections crosses the book at a
  high estimated cost (>= shared/high-cost-crossing-bps). These are the rows the trader
  is about to pay real spread on — under Recommended they route passive, so this is
  non-empty only when a bulk Market/TWAP default or per-row overrides expose them."
  [model rows]
  (filterv (fn [row]
             (and (shared/crossing-type? (shared/effective-type model row))
                  (shared/high-cost-crossing-row? row)))
           (ready-rows rows)))

(defn- rest-passively-actions
  [rows]
  (mapv (fn [row]
          [:actions/set-portfolio-optimizer-execution-row-order-type (:row-id row) :passive])
        rows))

(defn- high-cost-warning
  [{:keys [read-only?] :as model} rows]
  (let [flagged (high-cost-crossing-rows model rows)]
    (when (seq flagged)
      (let [sendable (ready-rows rows)
            current (projected-all-in-usd model (or (:default-order-type model) :recommended) sendable)
            ;; Projection of the one-click fix: the flagged rows overridden passive,
            ;; everything else unchanged.
            fixed-model (update model :overrides
                                (fn [overrides]
                                  (reduce #(assoc %1 (:row-id %2) :passive)
                                          (or overrides {})
                                          flagged)))
            fixed (projected-all-in-usd fixed-model (or (:default-order-type model) :recommended) sendable)
            listed (->> flagged
                        (sort-by #(- (or (shared/crossing-cost-bps %) 0)))
                        (map #(str (:instrument-label %) " "
                                   (shared/format-bps (shared/crossing-cost-bps %))))
                        (take 6))
            more (- (count flagged) (count listed))]
        [:div {:class ["flex" "flex-wrap" "items-center" "gap-3" "border-t" "border-base-300"
                       "optimizer-exec-highcost" "px-5" "py-2.5"]
               :data-role "portfolio-optimizer-execution-high-cost-warning"
               :role "status"}
         (shared/chip "high cost" :warn)
         [:div {:class ["min-w-0"]}
          [:p {:class ["text-xs" "font-medium" "text-trading-text"]}
           (str (count flagged) " order" (when (not= 1 (count flagged)) "s")
                " cross" (when (= 1 (count flagged)) "es")
                " the spread at ≥ " shared/high-cost-crossing-bps " bp estimated cost: "
                (apply str (interpose " · " listed))
                (when (pos? more) (str " · +" more " more")))]
          [:p {:class ["mt-0.5" "font-mono" "text-[0.68rem]" "tabular-nums" "text-trading-muted"]}
           (str "Resting " (if (= 1 (count flagged)) "it as a passive maker order" "them as passive maker orders")
                " cuts est. all-in cost from "
                (opt-format/format-usdc current) " to " (opt-format/format-usdc fixed)
                (if (= 1 (count flagged)) " — but it may not fill." " — but they may not fill."))]]
         [:span {:class ["flex-1"]}]
         (when-not read-only?
           [:button {:type "button"
                     :class ["border" "border-warning/60" "px-3" "py-1.5" "text-xs"
                             "font-semibold" "text-warning" "shrink-0"]
                     :data-role "portfolio-optimizer-execution-rest-high-cost"
                     :on {:click (rest-passively-actions flagged)}}
            (str "Rest " (if (= 1 (count flagged)) "it" "these") " passively")])]))))

;; ── the staged band ────────────────────────────────────────────────────────

(defn staged-band
  [{:keys [default-order-type read-only? disabled-message stale? stale-message] :as model} rows]
  (let [sendable (ready-rows rows)]
    [:div {:class ["border-b" "border-base-300"]
           :data-role "portfolio-optimizer-execution-control-band"
           :data-phase "staged"}
     (when read-only?
       [:p {:class ["optimizer-exec-readonly" "border-b" "border-base-300" "px-5" "py-2"
                    "text-xs" "font-semibold" "text-warning"]
            :data-role "portfolio-optimizer-execution-readonly"}
        disabled-message])
     ;; A stale plan can't be armed (the action gate refuses it); say why and point to the re-run.
     (when stale?
       [:p {:class ["optimizer-exec-readonly" "border-b" "border-base-300" "px-5" "py-2"
                    "text-xs" "font-semibold" "text-warning"]
            :data-role "portfolio-optimizer-execution-stale"}
        stale-message])
     [:div {:class ["flex" "items-stretch"]}
      [:div {:class ["flex" "flex-col" "justify-center" "gap-0.5" "px-5" "py-2" "shrink-0"]}
       [:span {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
        "Execution strategy"]
       [:span {:class ["font-mono" "text-[0.58rem]" "text-trading-muted"]}
        "override any order below →"]]
      (into [:div {:class ["flex" "flex-1" "border-l" "border-base-300"]}]
            (for [{:keys [id title sub]} strategies]
              (mode-tile {:id id
                          :title title
                          :sub sub
                          :consequence (tile-consequence model id sendable)
                          :active? (= id default-order-type)
                          :read-only? read-only?})))]
     (or (high-cost-warning model rows)
         [:p {:class ["border-t" "border-base-300" "bg-base-200/30" "px-5" "py-1.5"
                      "font-mono" "text-[0.65rem]" "text-trading-muted"]}
          "Recommended routes each order by estimated cost and clip size. Passive maker / Limit rest as maker orders and may not fully fill; TWAP works over time."])]))
