(ns hyperopen.views.degen.order-form
  "Degen-voice chrome for the trade order form: escalating leverage
   taunts (live in the popover, banner for the committed value) and the
   massive BUY MOON / SELL PANIC side selectors from the HyperDegen
   prototype. Everything renders nil (or the compact default) under
   other themes."
  (:require [hyperopen.trading-settings :as trading-settings]
            [hyperopen.ui.sfx :as sfx]
            [hyperopen.ui.voice :as voice]))

(defn leverage-tier
  "Escalating taunt for a leverage value (prototype's LEVERAGE_WARNINGS).
   nil for non-numeric input."
  [leverage]
  (let [lev (js/parseFloat leverage)]
    (when-not (js/isNaN lev)
      (cond
        (>= lev 200)
        {:level 5
         :text "MAXIMUM DEGEN. Tell your family you love them. 💀"
         :classes ["border-ho-sell" "text-ho-sell"]}

        (>= lev 100)
        {:level 4
         :text "A 0.1% wick ends you. Good luck. 💀"
         :classes ["border-ho-sell" "text-ho-sell"]}

        (>= lev 50)
        {:level 3
         :text "Your liquidation price is basically the current price."
         :classes ["border-ho-sell" "text-ho-sell"]}

        (>= lev 20)
        {:level 2
         :text "WARNING: HIGH LEVERAGE = BIG FUN (or big sadness)"
         :classes ["border-ho-warn" "text-ho-warn"]}

        (>= lev 10)
        {:level 1
         :text "Getting spicy. 🌶️"
         :classes ["border-ho-warn" "text-ho-warn"]}

        :else
        {:level 0
         :text "Sensible. Are you lost?"
         :classes ["border-ho-border" "text-ho-text-dim"]}))))

(defn leverage-warning-banner
  "Escalating parody warning under the leverage row for the committed
   leverage; quiet below 20x so the form isn't permanently noisy."
  [state leverage]
  (when (voice/degen? state)
    (when-some [tier (leverage-tier leverage)]
      (when (>= (:level tier) 2)
        [:div {:class (into ["rounded-md" "border" "border-dashed" "px-2.5"
                             "py-1.5" "text-xs" "font-bold"]
                            (:classes tier))
               :data-role "degen-leverage-warning"}
         (:text tier)]))))

(defn leverage-popover-message
  "Live taunt inside the leverage popover, driven by the slider draft —
   updates on every notch while dragging. All tiers speak, including
   the sensible ones."
  [state draft-leverage]
  (when (voice/degen? state)
    (when-some [tier (leverage-tier draft-leverage)]
      ;; Render-side threshold tick (prototype's riskUp): idempotent per
      ;; tier level, so re-renders are silent and only crossing a tier
      ;; while dragging speaks.
      (sfx/leverage-tick-on-change! (:level tier)
                                    (trading-settings/sound-on-fill? state))
      [:div {:class (into ["rounded-md" "border" "border-dashed" "px-2.5"
                           "py-1.5" "text-xs" "font-bold" "leading-snug"]
                          (:classes tier))
             :data-role "degen-leverage-popover-message"}
       (str "⚠ " (:text tier))])))

(defn- massive-side-button
  "Big stacked side selector with a parenthetical sublabel, per the
   prototype's BUY MOON / SELL PANIC."
  [label sublabel side active? on-click]
  (let [buy? (= side :buy)]
    [:button {:type "button"
              :data-role (str "degen-massive-side-" (name side))
              :aria-pressed (boolean active?)
              :class (into ["flex" "w-full" "flex-col" "items-center"
                            "justify-center" "gap-0.5" "rounded-xl" "py-2.5"
                            "border-2" "font-black" "tracking-wide"
                            "transition-colors"]
                           (cond
                             (and active? buy?)
                             ["bg-ho-buy" "border-ho-buy" "text-ho-bg"]

                             buy?
                             ["bg-ho-accent-soft" "border-ho-border-accent"
                              "text-ho-buy" "hover:bg-ho-accent-soft-hi"]

                             active?
                             ["bg-ho-sell" "border-ho-sell" "text-ho-bg"]

                             :else
                             ["bg-ho-sell-soft" "border-ho-border-sell"
                              "text-ho-sell" "hover:bg-ho-sell-soft-deep"]))
              :on {:click on-click}}
     [:span {:class ["text-base" "leading-tight"]} label]
     (when sublabel
       [:span {:class ["text-xs" "font-semibold" "opacity-80"]} sublabel])]))

(defn massive-side-row
  [side side-handlers {:keys [buy-label sell-label buy-sublabel sell-sublabel]}]
  [:div {:class ["flex" "flex-col" "gap-2"]
         :data-role "degen-massive-side-row"}
   (massive-side-button buy-label buy-sublabel :buy (= side :buy)
                        ((:on-select-side side-handlers) :buy))
   (massive-side-button sell-label sell-sublabel :sell (= side :sell)
                        ((:on-select-side side-handlers) :sell))])
