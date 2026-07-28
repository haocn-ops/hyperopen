(ns hyperopen.views.portfolio.summary-cards
  (:require [hyperopen.views.portfolio.format :as portfolio-format]))

(defn summary-selector
  [{:keys [label open? options value]}
   toggle-action
   select-action
   data-role]
  [:div {:class ["relative"]
         :data-role data-role}
   [:button {:type "button"
             :class ["flex"
                     "items-center"
                     "gap-1.5"
                     "rounded-md"
                     "px-2"
                     "py-1"
                     "text-xs"
                     "font-normal"
                     "text-trading-text"
                     "hover:bg-base-200"]
             :aria-expanded (boolean open?)
             :data-role (str data-role "-trigger")
             :on {:click [[toggle-action]]}}
    [:span label]
    [:svg {:class (into ["h-4" "w-4" "text-trading-text-secondary" "transition-transform"]
                        (when open?
                          ["rotate-180"]))
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"}
     [:path {:stroke-linecap "round"
             :stroke-linejoin "round"
             :stroke-width 2
             :d "M19 9l-7 7-7-7"}]]]
   [:div {:class (into ["absolute"
                        "right-0"
                        "top-full"
                        "mt-1"
                        "min-w-[160px]"
                        "overflow-hidden"
                        "rounded-md"
                        "border"
                        "border-base-300"
                        "bg-base-100"
                        "spectate-lg"
                        "z-30"]
                       (if open?
                         ["opacity-100" "scale-y-100" "translate-y-0"]
                         ["opacity-0" "scale-y-95" "-translate-y-1" "pointer-events-none"]))
          :style {:transition "all 80ms ease-in-out"}}
    (for [{option-value :value option-label :label} options]
      ^{:key (str data-role "-" (name option-value))}
      [:button {:type "button"
                :class (into ["block"
                              "w-full"
                              "px-3"
                              "py-2"
                              "text-left"
                              "text-xs"
                              "hover:bg-base-200"]
                             (if (= option-value value)
                               ["text-trading-text" "bg-base-200"]
                               ["text-trading-text-secondary"]))
                :aria-pressed (= option-value value)
                :data-role (str data-role "-option-" (name option-value))
                :on {:click [[select-action option-value]]}}
       option-label])]])

(defn section-card [data-role & children]
  (into [:div {:class ["rounded-xl"
                       "border"
                       "border-base-300"
                       "bg-base-100/95"
                       "overflow-hidden"]
               :data-role data-role}]
        children))

(defn- summary-row [label value & [value-class]]
  [:div {:class ["summary-kv-row" "grid" "grid-cols-[1fr_auto]" "items-center" "gap-3"]}
   [:span {:class ["text-sm" "text-trading-text-secondary"]}
    label]
   [:span {:class (into ["num" "text-sm" "text-trading-text"] (or value-class []))}
    value]])

(defn- pnl-summary [pnl]
  (let [n (if (number? pnl) pnl 0)
        color-class (cond
                      (pos? n) "text-success"
                      (neg? n) "text-error"
                      :else "text-trading-text")]
    {:value (str (cond
                   (pos? n) "+"
                   (neg? n) "-"
                   :else "")
                (portfolio-format/format-currency (js/Math.abs n)))
     :class [color-class]}))

(defn- analytics-value
  [value formatter]
  (if (some? value)
    (formatter value)
    "Unavailable"))

(defn- analytics-pnl-value
  [pnl]
  (when (some? pnl)
    (pnl-summary pnl)))

(defn- analytics-fee-rates-value
  [fee-rates]
  (if (and (number? (:taker fee-rates))
           (number? (:maker fee-rates)))
    (str (portfolio-format/format-fee-pct (* 100 (:taker fee-rates)))
         " / "
         (portfolio-format/format-fee-pct (* 100 (:maker fee-rates))))
    "Unavailable"))

(defn- analytics-status
  [{:keys [data-quality quality as-of-ms message retry?]}]
  (let [quality* (or data-quality quality :unavailable)]
    [:div {:class ["flex" "flex-wrap" "items-center" "gap-x-3" "gap-y-1"
                   "border-b" "border-base-300" "px-4" "py-2" "text-xs"]
           :data-role "portfolio-analytics-status"
           :data-quality (name quality*)}
     [:span {:class ["font-medium" "text-trading-text"]}
      (str "Analytics: " (name quality*))]
     [:span {:class ["text-trading-text-secondary"]}
      (or message "Portfolio analytics status is unavailable")]
     (when (and (= :stale quality*) (number? as-of-ms))
       [:span {:class ["text-trading-text-secondary"]
               :data-role "portfolio-analytics-as-of"}
        (str "As of " (.toISOString (js/Date. as-of-ms)))])
     (when retry?
       [:span {:class ["text-warning"]
               :data-role "portfolio-analytics-retry"}
        (str "Refresh: " (or message "Retry the existing portfolio refresh"))])]))

(defn- analytics-summary-rows
  [analytics]
  (let [pnl-info (analytics-pnl-value (:pnl analytics))]
    [[:div {:class ["summary-kv-row" "grid" "grid-cols-[1fr_auto]" "items-center" "gap-3"]}
      [:span {:class ["text-sm" "text-trading-text-secondary"]} "PNL"]
      [:span {:class (into ["num" "text-sm" "text-trading-text"]
                           (or (:class pnl-info) []))
              :data-role "portfolio-analytics-pnl"}
       (or (:value pnl-info) "Unavailable")]]
     [:div {:class ["summary-kv-row" "grid" "grid-cols-[1fr_auto]" "items-center" "gap-3"]}
      [:span {:class ["text-sm" "text-trading-text-secondary"]} "Return"]
      [:span {:class ["num" "text-sm" "text-trading-text"]
              :data-role "portfolio-analytics-return"}
       (analytics-value (:return-pct analytics) portfolio-format/format-percent)]]
     [:div {:class ["summary-kv-row" "grid" "grid-cols-[1fr_auto]" "items-center" "gap-3"]}
      [:span {:class ["text-sm" "text-trading-text-secondary"]} "Max Drawdown"]
      [:span {:class ["num" "text-sm" "text-trading-text"]
              :data-role "portfolio-analytics-drawdown"}
       (analytics-value (:max-drawdown-pct analytics) portfolio-format/format-percent)]]
     [:div {:class ["summary-kv-row" "grid" "grid-cols-[1fr_auto]" "items-center" "gap-3"]}
      [:span {:class ["text-sm" "text-trading-text-secondary"]} "Total Equity"]
      [:span {:class ["num" "text-sm" "text-trading-text"]
              :data-role "portfolio-analytics-equity"}
       (analytics-value (:equity analytics) portfolio-format/format-currency)]]
     ]))

(defn summary-card [{:keys [analytics summary selectors]}]
  (let [pnl-info (pnl-summary (:pnl summary))
        summary-scope (:summary-scope selectors)
        summary-time-range (:summary-time-range selectors)
        summary-rows (if (map? analytics)
                       (analytics-summary-rows analytics)
                       [(summary-row "PNL" (:value pnl-info) (:class pnl-info))
                        (summary-row "Volume" (portfolio-format/format-currency (:volume summary)))
                        (summary-row "Max Drawdown" (portfolio-format/format-drawdown (:max-drawdown-pct summary)))
                        (summary-row "Total Equity" (portfolio-format/format-currency (:total-equity summary)))])]
    (section-card
     "portfolio-account-summary-card"
     [:div {:class ["summary-card-head" "flex" "items-center" "justify-between" "border-b" "border-base-300" "px-4" "py-3"]}
      (summary-selector summary-scope
                        :actions/toggle-portfolio-summary-scope-dropdown
                        :actions/select-portfolio-summary-scope
                        "portfolio-summary-scope-selector")
      (summary-selector summary-time-range
                        :actions/toggle-portfolio-summary-time-range-dropdown
                        :actions/select-portfolio-summary-time-range
                        "portfolio-summary-time-range-selector")]
     (when (map? analytics)
       (analytics-status analytics))
     (into [:div {:class ["space-y-2.5" "px-4" "py-3"]}]
           (concat summary-rows
                   (when (:show-perps-account-equity? summary)
                     [(summary-row "Perps Account Equity"
                                   (if (map? analytics)
                                     (analytics-value (:perps-account-equity summary) portfolio-format/format-currency)
                                     (portfolio-format/format-currency (:perps-account-equity summary))))])
                   [(summary-row (:spot-equity-label summary)
                                 (if (map? analytics)
                                   (analytics-value (:spot-account-equity summary) portfolio-format/format-currency)
                                   (portfolio-format/format-currency (:spot-account-equity summary))))]
                   (when (:show-vault-equity? summary)
                     [(summary-row "Vault Equity"
                                   (if (map? analytics)
                                     (analytics-value (:vault-equity summary) portfolio-format/format-currency)
                                     (portfolio-format/format-currency (:vault-equity summary))))])
                   (when (:show-earn-balance? summary)
                     [(summary-row "Earn Balance"
                                   (if (map? analytics)
                                     (analytics-value (:earn-balance summary) portfolio-format/format-currency)
                                     (portfolio-format/format-currency (:earn-balance summary))))])
                   (when (:show-staking-account? summary)
                     [(summary-row "Staking Account"
                                   (if (map? analytics)
                                     (analytics-value (:staking-account-hype summary) portfolio-format/format-hype)
                                     (portfolio-format/format-hype (:staking-account-hype summary))))]))))))

(defn- analytics-volume-label
  [range]
  (case range
    :day "24 Hour Volume"
    :week "7 Day Volume"
    :month "30 Day Volume"
    :three-month "3 Month Volume"
    :six-month "6 Month Volume"
    :one-year "1 Year Volume"
    :two-year "2 Year Volume"
    :all-time "All-time Volume"
    "Selected Range Volume"))

(defn metric-cards [{:keys [analytics volume-14d-usd fees fee-schedule]}]
  (let [fee-schedule-open? (if (:open? fee-schedule) "true" "false")]
    [:div {:class ["grid" "grid-cols-2" "gap-3" "lg:grid-cols-1"]}
   (section-card
    "portfolio-14d-volume-card"
    [:div {:class ["space-y-2.5" "px-3" "py-3" "sm:px-4"]}
     [:div {:class ["text-xs" "uppercase" "tracking-wide" "text-trading-text-secondary" "sm:text-sm" "sm:normal-case" "sm:tracking-normal"]}
      (if (and (map? analytics)
               (not= :unavailable (:data-quality analytics)))
        (analytics-volume-label (:range analytics))
        "14 Day Volume")]
     [:div {:class ["num" "text-2xl" "font-medium" "text-trading-text" "sm:text-4xl"]
            :data-role (when (map? analytics) "portfolio-analytics-volume")}
      (if (map? analytics)
        (analytics-value (:volume analytics) portfolio-format/format-currency)
        (portfolio-format/format-compact-currency volume-14d-usd))]
     [:button {:type "button"
               :class ["btn" "btn-xs" "btn-spectate" "justify-start" "px-0" "text-xs" "text-trading-green" "hover:bg-transparent" "sm:text-xs"]
               :data-role "portfolio-volume-history-trigger"
               :on {:click [[:actions/open-portfolio-volume-history
                              :event.currentTarget/bounds]]}}
      "View Volume"]])
   (section-card
    "portfolio-fees-card"
    [:div {:class ["space-y-2.5" "px-3" "py-3" "sm:px-4"]}
     [:div {:class ["flex" "items-center" "justify-between" "gap-2"]}
      [:span {:class ["text-xs" "uppercase" "tracking-wide" "text-trading-text-secondary" "sm:text-sm" "sm:normal-case" "sm:tracking-normal"]}
       "Fees (Taker / Maker)"]
      (when (map? analytics)
        [:span {:class ["text-xs" "text-trading-text-secondary"]}
         "Current maker / taker rates"])
      [:button {:class ["btn" "btn-spectate" "btn-xs" "px-2" "text-xs" "text-trading-text" "sm:text-xs"]}
       "Perps"]]
     [:div {:class ["num" "text-2xl" "font-medium" "leading-tight" "text-trading-text" "sm:text-4xl"]
            :data-role (when (map? analytics) "portfolio-analytics-fee-rates")}
      (if (map? analytics)
        (analytics-fee-rates-value (:fee-rates analytics))
        (str (portfolio-format/format-fee-pct (:taker fees)) " / " (portfolio-format/format-fee-pct (:maker fees))))]
     [:button {:type "button"
               :class ["btn" "btn-xs" "btn-spectate" "justify-start" "px-0" "text-xs" "text-trading-green" "hover:bg-transparent" "sm:text-xs"]
               :aria-haspopup "dialog"
               :aria-expanded fee-schedule-open?
               :data-role "portfolio-fee-schedule-trigger"
               :on {:click [[:actions/open-portfolio-fee-schedule
                             :event.currentTarget/bounds]]}}
      "View Fee Schedule"]])]))
