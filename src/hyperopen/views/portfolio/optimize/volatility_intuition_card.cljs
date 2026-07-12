(ns hyperopen.views.portfolio.optimize.volatility-intuition-card
  "The VOLATILITY INTUITION rail card and the under-chart insight strip:
  translate the run's annualized volatility into daily / weekly / monthly
  one-standard-deviation (1σ) move scales a person can feel. Explanatory
  only — reads the solved result, never feeds the solver.

  The Target/Current toggle is a pair of visually hidden radios toggled by
  scoped :has() CSS in src/styles/surfaces/optimizer/results.css (the Equal
  Risk tab idiom): presentation state lives in the DOM, never in app state.
  Bars are aria-hidden magnitude cues; every value is also plain text."
  (:require [hyperopen.portfolio.optimizer.application.view-model.volatility-intuition
             :as vm]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(defn- horizon-pct
  "Designer precision ladder: two decimals below 100%, one decimal above
  (values above 100% render uncapped — the boundary note explains them)."
  [value]
  (let [digits (if (and (number? value) (>= value 1.0)) 1 2)]
    (opt-format/format-pct value {:minimum-fraction-digits digits
                                  :maximum-fraction-digits digits})))

(defn- plus-minus
  [value]
  (str "±" (horizon-pct value)))

(defn- bar-width
  "Bar width as a percent of the largest displayed horizon (monthly). A tiny
  floor keeps a non-zero daily bar visible next to a 100%-wide monthly bar."
  [value max-value]
  (cond
    (not (and (number? value) (number? max-value) (pos? max-value))) 0
    (zero? value) 0
    :else (max 2 (min 100 (* 100 (/ value max-value))))))

(defn- horizon-row
  [view {:keys [key label value max-value]}]
  [:div {:class ["optimizer-vol-intuition-row"]
         :data-role (str "portfolio-optimizer-volatility-intuition-" view "-" key)}
   [:span {:class ["optimizer-vol-intuition-row-label"]} label]
   [:div {:class ["optimizer-vol-intuition-track"]
          :aria-hidden "true"}
    [:div {:class ["optimizer-vol-intuition-fill"]
           :style {:width (str (bar-width value max-value) "%")}}]]
   [:span {:class ["optimizer-vol-intuition-row-value" "font-mono" "tabular-nums"]}
    (plus-minus value)]])

(defn- severity-note
  [view severity]
  (case severity
    :elevated
    [:p {:class ["mt-2" "text-[0.62rem]" "text-trading-muted"]
         :data-role (str "portfolio-optimizer-volatility-intuition-" view "-severity")}
     "Elevated volatility."]

    :very-high
    [:p {:class ["optimizer-vol-intuition-callout" "mt-2" "text-[0.62rem]"]
         :data-role (str "portfolio-optimizer-volatility-intuition-" view "-severity")}
     "Very high volatility."]

    :extreme
    [:p {:class ["optimizer-vol-intuition-callout" "mt-2" "text-[0.62rem]"]
         :data-role (str "portfolio-optimizer-volatility-intuition-" view "-severity")}
     [:span {:class ["font-semibold"]} "Extreme volatility: "]
     "square-root-of-time scaling is only a rough intuition. Fat tails, leverage, margin rules, and liquidation risk may dominate actual outcomes."]

    nil))

(defn- boundary-note
  [view boundary?]
  (when boundary?
    [:p {:class ["mt-2" "text-[0.62rem]" "text-trading-muted"]
         :data-role (str "portfolio-optimizer-volatility-intuition-" view "-boundary")}
     "A ±1σ monthly range extends beyond the −100% return boundary — read it as a dispersion measure, not a literal symmetric outcome range."]))

(defn- portfolio-panel
  "One portfolio's horizon read (the :has() CSS shows exactly one panel)."
  [view {:keys [annualized daily weekly monthly severity monthly-boundary?]}]
  [:div {:class ["optimizer-vol-intuition-body"
                 (str "optimizer-vol-intuition-body--" view)]
         :data-role (str "portfolio-optimizer-volatility-intuition-panel-" view)}
   [:div {:class ["flex" "items-baseline" "justify-between" "gap-3"]}
    [:span {:class ["text-[0.62rem]" "font-semibold" "uppercase" "tracking-[0.06em]" "text-trading-muted"]}
     "Annualized volatility"]
    [:span {:class ["font-mono" "text-base" "font-semibold" "tabular-nums" "text-trading-text"]
            :data-role (str "portfolio-optimizer-volatility-intuition-" view "-annualized")}
     (horizon-pct annualized)]]
   [:div {:class ["mt-2.5" "space-y-1.5"]}
    (horizon-row view {:key "daily"
                       :label "Daily 1σ move"
                       :value daily
                       :max-value monthly})
    (horizon-row view {:key "weekly"
                       :label "Weekly 1σ move"
                       :value weekly
                       :max-value monthly})
    (horizon-row view {:key "monthly"
                       :label "Monthly 1σ move"
                       :value monthly
                       :max-value monthly})]
   (severity-note view severity)
   (boundary-note view monthly-boundary?)])

(defn- view-tab
  "A tab is a label wrapping its own visually hidden radio; checked state
  lives in the DOM and the scoped :has() CSS does the styling + panel
  switching. No radio checked means Target (the recommendation default)."
  [radio-name radio-id view text]
  [:label {:class ["optimizer-vol-intuition-tab"]
           :data-role (str "portfolio-optimizer-volatility-intuition-tab-" view)}
   [:input {:type "radio"
            :name radio-name
            :id radio-id
            :class ["sr-only"]
            :data-vol-view view
            :aria-label (str "Show " view " portfolio volatility")}]
   text])

(defn volatility-intuition-card
  "Rail card. Renders nothing when the result has no usable target
  volatility; the Current tab renders only when the current book has one."
  [result]
  (when-let [{:keys [basis target current radio-name
                     target-radio-id current-radio-id]} (vm/card-model result)]
    [:aside {:class ["optimizer-vol-intuition"
                     "min-h-0" "border-l" "border-base-300" "bg-base-100/95"]
             :replicant/key "optimizer-volatility-intuition-card"
             :data-role "portfolio-optimizer-volatility-intuition"}
     [:div {:class ["flex" "items-center" "justify-between" "gap-3"
                    "border-b" "border-base-300" "px-4" "py-3"]}
      [:p {:class ["font-mono" "text-[0.62rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
       "Volatility intuition"]
      (when current
        [:div {:class ["optimizer-vol-intuition-tabs"]
               :data-role "portfolio-optimizer-volatility-intuition-tabs"}
         (view-tab radio-name target-radio-id "target" "Target")
         (view-tab radio-name current-radio-id "current" "Current")])]
     [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
      (portfolio-panel "target" target)
      (when current
        (portfolio-panel "current" current))
      [:p {:class ["mt-3" "text-[0.6rem]" "text-trading-muted/70"]}
       "A 1σ move is a volatility scale, not a forecast or maximum loss."]
      [:p {:class ["mt-0.5" "text-[0.6rem]" "text-trading-muted/70"]
           :data-role "portfolio-optimizer-volatility-intuition-basis"}
       (str "Scaled from annualized volatility using √time · "
            (:periods-per-year basis) " " (:period-label basis) "/year.")]]]))

(defn insight-strip
  "One-line read under the frontier chart, only at very-high/extreme target
  volatility. The wrapper div always renders so conditional appearance can
  never shift the keyed siblings below the chart (open <details> state)."
  [result]
  [:div {:class ["optimizer-vol-insight-slot"]
         :replicant/key "optimizer-vol-insight-slot"}
   (when-let [{:keys [annualized daily]} (vm/insight-model result)]
     [:section {:class ["optimizer-vol-insight"]
                :data-role "portfolio-optimizer-volatility-insight"}
      [:p {:class ["text-[0.72rem]" "leading-[1.5]" "text-trading-muted"]}
       [:span {:class ["font-semibold" "optimizer-vol-insight-lead"]}
        "Volatility intuition: "]
       (str "at " (horizon-pct annualized)
            " annualized volatility, a typical 1σ day is about "
            (plus-minus daily)
            ". At this level, path dependency and liquidation risk can matter more than the average expected return.")]])])
