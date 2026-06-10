(ns hyperopen.views.degen.widgets
  "Theme-gated parody decor for the HyperDegen experience (see
   docs/exec-plans/active/2026-06-10-hyperdegen-voice-layer.md, Phase C).

   Every component renders nil unless the degen voice is active, projects
   *real* account/market state into joke chrome where data is available,
   and uses semantic tokens only — no raw colors."
  (:require [hyperopen.ui.voice :as voice]
            [hyperopen.views.account-equity.format :as equity-format]
            [hyperopen.views.account-equity.metrics :as equity-metrics]
            [hyperopen.wallet.core :as wallet]))

(def tips
  ["If it's red, buy. If it's green, buy more. If it nukes, screenshot."
   "If it's green, you're a genius. If it's red, it's the algorithms."
   "Zoom out until the chart looks good. Then trade on that timeframe."
   "A stop loss is just admitting defeat in advance."
   "Never sell. That way you never realize a loss. Genius."])

(defn daily-tip
  "Deterministic tip-of-the-day; `now-ms` injectable for tests."
  ([] (daily-tip (.now js/Date)))
  ([now-ms]
   (nth tips (mod (js/Math.floor (/ now-ms 86400000)) (count tips)))))

(defn liq-risk
  "Margin-ratio (0..1) into a parody risk readout."
  [ratio]
  (cond
    (and (number? ratio) (>= ratio 0.4))
    {:text "VERY HIGH 😬" :hint "(good luck)" :class "text-ho-sell"}

    (and (number? ratio) (>= ratio 0.15))
    {:text "SPICY 🌶️" :hint "(watch it)" :class "text-ho-warn"}

    (and (number? ratio) (pos? ratio))
    {:text "MEH 😴" :hint "(do more)" :class "text-ho-text"}

    :else
    {:text "NONE" :hint "(boring)" :class "text-ho-text-dim"}))

(defn market-vibes
  "24h change percent into market vibes."
  [pct]
  (cond
    (and (number? pct) (>= pct 2))
    {:text "BULLISH AF 🚀🚀" :class "text-ho-buy"}

    (and (number? pct) (<= pct -2))
    {:text "BEARISH AF 💀" :class "text-ho-sell"}

    (number? pct)
    {:text "CRAB MARKET 🦀" :class "text-ho-warn"}

    :else
    {:text "NO VIBES" :class "text-ho-text-dim"}))

(defn feeling
  "Unrealized PNL into the feeling gauge."
  [unrealized-pnl]
  (cond
    (and (number? unrealized-pnl) (pos? unrealized-pnl))
    {:status "AMAZING 🤑" :meter "▰▰▰▰▰" :class "text-ho-buy"}

    (and (number? unrealized-pnl) (neg? unrealized-pnl))
    {:status "TERRIBLE 💩" :meter "▰▱▱▱▱" :class "text-ho-sell"}

    (number? unrealized-pnl)
    {:status "NUMB 😶" :meter "▰▰▱▱▱" :class "text-ho-text-dim"}

    :else
    {:status "NUMB 😶" :meter "▱▱▱▱▱" :class "text-ho-text-dim"}))

(defn- change-24h-pct
  [state]
  (let [raw (get-in state [:active-assets :contexts (:active-asset state) :change24hPct])
        n (js/parseFloat raw)]
    (when-not (js/isNaN n) n)))

(defn- stat-card
  [{:keys [data-role label value value-class hint]}]
  [:div {:class ["flex" "flex-col" "justify-center" "gap-0.5" "rounded-lg"
                 "border" "border-ho-border" "bg-ho-surface" "px-3.5" "py-2"
                 "min-w-[8.5rem]" "shrink-0"]
         :data-role data-role}
   [:span {:class ["text-xs" "uppercase" "tracking-[0.08em]"
                   "text-ho-text-muted" "whitespace-nowrap"]}
    label]
   [:span {:class (conj ["text-sm" "font-bold" "whitespace-nowrap"]
                        (or value-class "text-ho-text"))}
    value]
   (when hint
     [:span {:class ["text-xs" "text-ho-text-dim" "whitespace-nowrap"]}
      hint])])

(defn- congrats-card
  [state]
  (let [who (or (wallet/short-addr (get-in state [:wallet :address]))
                "DEGEN")]
    [:div {:class ["flex" "flex-col" "justify-center" "gap-0.5" "rounded-lg"
                   "border" "border-dashed" "border-ho-warn" "bg-ho-surface"
                   "px-3.5" "py-2" "min-w-[16rem]" "flex-1" "shrink-0"]
           :data-role "degen-congrats-card"}
     [:span {:class ["text-xs" "font-bold" "text-ho-warn" "whitespace-nowrap"]}
      (str "🎉 CONGRATS " who "! 🎉")]
     [:span {:class ["text-xs" "text-ho-warn/80" "whitespace-nowrap"]}
      "You are in the TOP 100% of degens!"]]))

(defn stats-strip
  "Parody stat strip over the trade surface; real numbers where available."
  [state]
  (when (voice/degen? state)
    (let [metrics (equity-metrics/account-equity-metrics state)
          ratio (or (:unified-account-ratio metrics)
                    (:cross-margin-ratio metrics))
          pnl (:pnl-info metrics)
          risk (liq-risk ratio)
          vibes (market-vibes (change-24h-pct state))]
      [:div {:class ["hidden" "lg:flex" "items-stretch" "gap-2" "px-2" "pt-2"
                     "overflow-x-auto" "scrollbar-hide"]
             :data-role "degen-stats-strip"}
       (stat-card {:data-role "degen-stat-total-value"
                   :label "Total Value (LOL)"
                   :value (equity-format/display-currency
                           (:account-value-display metrics))
                   :hint "(number, allegedly)"})
       (stat-card {:data-role "degen-stat-pnl"
                   :label "Unrealized P&L (pray)"
                   :value (:text pnl)
                   :value-class (:class pnl)
                   :hint "(probably fake)"})
       (stat-card {:data-role "degen-stat-liq-risk"
                   :label "Liquidation Risk"
                   :value (:text risk)
                   :value-class (:class risk)
                   :hint (:hint risk)})
       (stat-card {:data-role "degen-stat-number-go-up"
                   :label "Number Go Up?"
                   :value "¯\\_(ツ)_/¯"
                   :value-class "text-ho-text-secondary"})
       (stat-card {:data-role "degen-stat-vibes"
                   :label "Market Vibes"
                   :value (:text vibes)
                   :value-class (:class vibes)})
       (stat-card {:data-role "degen-stat-server"
                   :label "Server Status"
                   :value "WHO KNOWS"
                   :value-class "text-ho-text-secondary"
                   :hint "(vibes only)"})
       [:div {:class ["flex" "items-center" "rounded-lg" "border" "border-dashed"
                      "border-ho-warn" "bg-ho-surface" "px-3.5" "py-2" "shrink-0"]
              :data-role "degen-nfa-card"}
        [:span {:class ["animate-pulse" "text-xs" "font-bold" "text-ho-warn"
                        "whitespace-nowrap"]}
         "NOT FINANCIAL ADVICE! 🤡"]]
       (congrats-card state)])))

(defn- widget-card
  [{:keys [data-role border-class title]} & body]
  [:div {:class (conj ["flex" "flex-col" "gap-1.5" "rounded-lg" "border"
                       "bg-ho-surface" "px-3.5" "py-3"]
                      (or border-class "border-ho-border"))
         :data-role data-role}
   [:span {:class ["text-xs" "font-bold" "uppercase" "tracking-[0.08em]"
                   "text-ho-text"]}
    title]
   (into [:div {:class ["flex" "flex-col" "gap-1"]}] body)])

(defn widgets-row
  "Bottom row of parody widget cards on the trade route."
  [state]
  (when (voice/degen? state)
    (let [metrics (equity-metrics/account-equity-metrics state)
          mood (feeling (:unrealized-pnl metrics))]
      [:div {:class ["hidden" "lg:grid" "grid-cols-2" "xl:grid-cols-4" "gap-2"
                     "px-2" "pb-2"]
             :data-role "degen-widgets-row"}
       (widget-card {:data-role "degen-widget-tip"
                     :border-class "border-ho-info"
                     :title "Degen Tip 💡"}
                    [:p {:class ["text-xs" "text-ho-text-secondary" "leading-relaxed"]}
                     (daily-tip)])
       (widget-card {:data-role "degen-widget-whale"
                     :border-class "border-ho-info"
                     :title "Whale Watch 🐋"}
                    [:p {:class ["text-xs" "text-ho-text-secondary" "leading-relaxed"]}
                     "They bought. You exit liquidity."]
                    [:p {:class ["text-xs" "font-bold" "text-ho-info"]}
                     "Nice."])
       (widget-card {:data-role "degen-widget-motivation"
                     :title "Daily Motivation 🐕"}
                    [:p {:class ["text-xs" "text-ho-text-secondary" "leading-relaxed"]}
                     "Such leverage. Much risk. Very degen. Wow."])
       (widget-card {:data-role "degen-widget-feeling"
                     :title "Feeling Gauge"}
                    [:div {:class ["flex" "items-baseline" "gap-2"]}
                     [:span {:class (conj ["text-sm" "font-bold"] (:class mood))}
                      (:status mood)]
                     [:span {:class (conj ["text-xs" "tracking-[0.2em]"] (:class mood))}
                      (:meter mood)]]
                    [:p {:class ["text-xs" "text-ho-text-dim"]}
                     "How's it going? (derived from real P&L)"])])))
