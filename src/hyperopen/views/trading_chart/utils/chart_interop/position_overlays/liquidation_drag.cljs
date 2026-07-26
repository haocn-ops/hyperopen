(ns hyperopen.views.trading-chart.utils.chart-interop.position-overlays.liquidation-drag
  (:require [hyperopen.views.account-info.shared :as account-shared]))

(def ^:private min-liquidation-drag-margin-amount 0.000001)

(defn- finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))))

(defn- parse-number
  [value]
  (account-shared/parse-optional-num value))

(defn- number->px
  [value fallback]
  (if (finite-number? value)
    value
    fallback))

(defn event-client-coordinate
  [event k]
  (when event
    (parse-number (aget event k))))

(defn event-client-y
  [event]
  (event-client-coordinate event "clientY"))

(defn event-client-x
  [event]
  (event-client-coordinate event "clientX"))

(defn event-anchor
  [overlay source-node event]
  (let [window* (:window overlay)
        viewport-width (or (some-> window* .-innerWidth parse-number)
                           (some-> js/globalThis .-innerWidth parse-number)
                           1280)
        viewport-height (or (some-> window* .-innerHeight parse-number)
                            (some-> js/globalThis .-innerHeight parse-number)
                            800)
        rect (try
               (when-let [method (some-> source-node (aget "getBoundingClientRect"))]
                 (when (fn? method)
                   (.call method source-node)))
               (catch :default _ nil))
        fallback-left (event-client-x event)
        fallback-top (event-client-y event)
        left (number->px (some-> rect (aget "left") parse-number) (or fallback-left 0))
        top (number->px (some-> rect (aget "top") parse-number) (or fallback-top 0))
        width (max 0 (number->px (some-> rect (aget "width") parse-number) 0))
        height (max 0 (number->px (some-> rect (aget "height") parse-number) 0))
        right (+ left width)
        bottom (+ top height)]
    {:left left
     :right right
     :top top
     :bottom bottom
     :width width
     :height height
     :viewport-width viewport-width
     :viewport-height viewport-height}))

(defn liquidation-margin-delta
  [overlay current-liq-price target-liq-price]
  (let [abs-size (parse-number (:abs-size overlay))
        side (:side overlay)
        current* (parse-number current-liq-price)
        target* (parse-number target-liq-price)]
    (when (and (finite-number? abs-size)
               (pos? abs-size)
               (finite-number? current*)
               (pos? current*)
               (finite-number? target*)
               (pos? target*)
               (contains? #{:long :short} side))
      (case side
        :long (* abs-size (- current* target*))
        :short (* abs-size (- target* current*))
        nil))))

(defn liquidation-drag-suggestion
  [overlay current-liq-price target-liq-price]
  (let [margin-delta (liquidation-margin-delta overlay current-liq-price target-liq-price)
        margin-delta* (if (finite-number? margin-delta) margin-delta 0)
        abs-delta (when (finite-number? margin-delta)
                    (js/Math.abs margin-delta))]
    (when (and (finite-number? abs-delta)
               (>= abs-delta min-liquidation-drag-margin-amount))
      {:mode (if (neg? margin-delta*)
               :remove
               :add)
       :amount abs-delta
       :current-liquidation-price current-liq-price
       :target-liquidation-price target-liq-price})))

(defn liquidation-drag-label
  [overlay current-liq-price target-liq-price]
  (when-let [{:keys [mode amount]} (liquidation-drag-suggestion overlay
                                                                current-liq-price
                                                                target-liq-price)]
    (let [mode-label (if (= mode :remove) "Remove" "Add")
          amount-text (account-shared/format-currency amount)]
      (str mode-label " $" amount-text " Margin"))))
