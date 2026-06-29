(ns hyperopen.views.portfolio.optimize.results-summary
  (:require [clojure.string :as str]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(defn summary-card
  [label value]
  [:div {:class ["optimizer-summary-card" "rounded-lg" "border" "border-base-300" "bg-base-200/50" "p-3"]}
   [:p {:class ["text-[0.65rem]" "font-semibold" "uppercase" "tracking-[0.18em]" "text-trading-muted"]}
    label]
   [:p {:class ["mt-2" "text-lg" "font-semibold" "tabular-nums"]}
    value]])

(defn compact-fact
  [label value]
  [:div {:class ["optimizer-summary-card" "rounded-lg" "border" "border-base-300" "bg-base-200/50" "px-3" "py-2"]}
   [:p {:class ["text-[0.6rem]" "font-semibold" "uppercase" "tracking-[0.18em]" "text-trading-muted"]}
    label]
   [:p {:class ["mt-1" "text-sm" "font-semibold" "tabular-nums"]}
    value]])

(defn panel-shell
  [data-role title subtitle & children]
  [:section {:class ["optimizer-results-panel"
                     "rounded-xl" "border" "border-base-300" "bg-base-100/95" "p-4"]
             :data-role data-role}
   [:p {:class ["text-[0.65rem]" "font-semibold" "uppercase" "tracking-[0.24em]" "text-trading-muted"]}
    title]
   [:p {:class ["mt-2" "text-sm" "text-trading-muted"]} subtitle]
   (into [:div {:class ["mt-4" "space-y-2"]}]
         children)])

(defn stale-result-banner
  [stale?]
  (when stale?
    [:div {:class ["rounded-xl" "border" "border-warning/50" "bg-warning/10" "p-4"]
           :data-role "portfolio-optimizer-stale-result-banner"}
     [:div {:class ["flex" "flex-col" "gap-3" "md:flex-row" "md:items-center" "md:justify-between"]}
      [:div
       [:p {:class ["text-[0.65rem]" "font-semibold" "uppercase" "tracking-[0.24em]" "text-warning"]}
        "Stale Output"]
       [:p {:class ["mt-2" "text-sm" "text-trading-muted"]}
        "These allocation weights come from the last successful run. The app is refreshing them automatically; save and execute unlock when the refreshed run completes."]]]]))

(defn verdict-headline
  "One plain-language sentence at the top of the recommendation tab stating what the
  recommended portfolio does to the user's risk and return, with the trade count —
  so the user reads the answer instead of reconstructing it from five KPI cards and
  a frontier chart. Targets are framed as estimates; deltas are signed (the rest of
  the UI uses the same convention: lower volatility and higher return are good).
  Falls back to non-delta phrasing when there is no current baseline.

  `deltas` is the map produced by scenario-detail-view's `recommendation-deltas`."
  [{:keys [target-vol vol-delta target-return return-delta trade-count]}]
  (let [vol-clause (if (opt-format/finite-number? vol-delta)
                     (str "estimated volatility " (opt-format/format-pct target-vol)
                          " (" (opt-format/format-pct-delta vol-delta) " vs current)")
                     (str "an estimated " (opt-format/format-pct target-vol) " volatility"))
        return-clause (if (opt-format/finite-number? return-delta)
                        (str "expected return " (opt-format/format-pct target-return)
                             " (" (opt-format/format-pct-delta return-delta) " vs current)")
                        (str "an estimated " (opt-format/format-pct target-return) " expected return"))
        body (str "Targets " vol-clause " and " return-clause ".")
        trades-clause (cond
                        (not (opt-format/finite-number? trade-count)) nil
                        (zero? trade-count) "No trades needed — you're already at target."
                        (= 1 trade-count) "1 trade gets you there."
                        :else (str trade-count " trades get you there."))]
    [:section {:class ["optimizer-results-panel" "optimizer-recommendation-verdict"
                       "rounded-xl" "border" "border-base-300" "bg-base-100/95" "p-4"]
               :data-role "portfolio-optimizer-recommendation-verdict"}
     [:p {:class ["text-[0.65rem]" "font-semibold" "uppercase" "tracking-[0.24em]" "text-trading-muted"]}
      "Recommendation"]
     [:p {:class ["mt-2" "text-sm" "font-medium" "text-trading-text"]} body]
     (when trades-clause
       [:p {:class ["mt-1" "text-[0.75rem]" "text-trading-muted"]
            :data-role "portfolio-optimizer-recommendation-verdict-trades"}
        trades-clause])]))

(defn- history-lookback-label
  [result]
  (let [summary (:history-summary result)
        observations (:return-observations summary)]
    (if (opt-format/finite-number? observations)
      (str observations " returns")
      "Loaded history")))

(defn- funding-assumption-label
  [result]
  (let [sources (->> (:return-decomposition-by-instrument result)
                     vals
                     (keep :funding-source)
                     set)]
    (cond
      (empty? sources) "No funding data"
      (= #{:not-applicable} sources) "Spot only"
      (contains? sources :market-funding-history) "Market funding"
      :else (opt-format/keyword-label (first sources)))))

(defn assumptions-strip
  [draft result]
  (let [objective-kind (or (get-in draft [:objective :kind])
                           (get-in result [:solver :objective-kind]))]
    [:section {:class ["optimizer-results-panel"
                       "rounded-xl" "border" "border-base-300" "bg-base-100/95" "p-4"]
               :data-role "portfolio-optimizer-assumptions-strip"}
     [:p {:class ["text-[0.65rem]" "font-semibold" "uppercase" "tracking-[0.24em]" "text-trading-muted"]}
      "Run Assumptions"]
     [:div {:class ["mt-3" "grid" "grid-cols-2" "gap-2" "xl:grid-cols-5"]}
      (compact-fact "Objective" (opt-format/keyword-label objective-kind))
      (compact-fact "Return Model" (opt-format/keyword-label (:return-model result)))
      (compact-fact "Risk Model" (opt-format/keyword-label (:risk-model result)))
      (compact-fact "Lookback" (history-lookback-label result))
      (compact-fact "Funding" (funding-assumption-label result))]]))
