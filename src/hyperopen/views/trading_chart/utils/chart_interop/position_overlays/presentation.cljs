(ns hyperopen.views.trading-chart.utils.chart-interop.position-overlays.presentation
  (:require [clojure.string :as str]
            [hyperopen.views.account-info.shared :as account-shared]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlays.support :as support]))

(def long-line-color "rgba(34, 201, 151, 0.9)")
(def short-line-color "rgba(227, 95, 120, 0.9)")
(def long-badge-color "rgba(34, 201, 151, 0.14)")
(def short-badge-color "rgba(227, 95, 120, 0.14)")
(def liq-line-color "rgba(227, 95, 120, 0.88)")
(def liq-badge-color "rgba(227, 95, 120, 0.14)")
(def long-text-color "rgb(151, 252, 228)")
(def short-text-color "rgb(244, 187, 198)")
(def flat-line-color "rgba(148, 163, 184, 0.9)")
(def flat-badge-color "rgba(148, 163, 184, 0.14)")
(def flat-text-color "rgb(226, 232, 240)")
(def liq-text-color "rgb(255, 196, 203)")
(def liq-drag-text-color "rgb(252, 222, 157)")
(def pnl-chip-text-color "rgb(248, 250, 252)")
(def badge-char-width-px 6.2)
(def chart-edge-padding-px 12)
(def pnl-badge-left-anchor-ratio 0.17)
(def pnl-badge-left-anchor-min-px 96)
(def pnl-badge-left-anchor-max-px 180)
(def liquidation-drag-hit-height-px 14)

(defn right-label-reserve-px
  [width]
  (support/clamp (* (support/non-negative-number width 0) 0.14)
                 88
                 168))

(defn estimate-badge-width-px
  [base-width text]
  (let [chars (count (or (some-> text str) ""))]
    (support/clamp (+ base-width (* chars badge-char-width-px))
                   72
                   300)))

(defn clamp-badge-center-x
  [width preferred-x badge-width]
  (let [safe-width (support/non-negative-number width 0)
        half-width (/ badge-width 2)
        min-x (+ chart-edge-padding-px half-width)
        max-x (- safe-width
                 (right-label-reserve-px safe-width)
                 half-width)
        fallback (/ safe-width 2)]
    (if (<= max-x min-x)
      fallback
      (support/clamp (support/non-negative-number preferred-x fallback) min-x max-x))))

(defn preferred-pnl-badge-x
  [width]
  (let [safe-width (support/non-negative-number width 0)]
    (support/clamp (* safe-width pnl-badge-left-anchor-ratio)
                   pnl-badge-left-anchor-min-px
                   pnl-badge-left-anchor-max-px)))

(defn visible-overlay-y?
  [height y]
  (and (support/finite-number? y)
       (or (zero? height)
           (and (> y -30)
                (< y (+ height 30))))))

(defn pnl-tone
  [overlay]
  (let [pnl (support/parse-number (:unrealized-pnl overlay))]
    (cond
      (and (support/finite-number? pnl) (neg? pnl)) :loss
      (and (support/finite-number? pnl) (pos? pnl)) :profit
      :else :flat)))

(defn pnl-line-color
  [overlay]
  (case (pnl-tone overlay)
    :loss short-line-color
    :profit long-line-color
    flat-line-color))

(defn pnl-badge-color
  [overlay]
  (case (pnl-tone overlay)
    :loss short-badge-color
    :profit long-badge-color
    flat-badge-color))

(defn pnl-text-color
  [overlay]
  (case (pnl-tone overlay)
    :loss short-text-color
    :profit long-text-color
    flat-text-color))

(defn format-price-text
  [format-price value]
  (let [formatted (or (when (fn? format-price)
                         (or (try
                               (format-price value value)
                               (catch :default _ nil))
                             (try
                               (format-price value)
                               (catch :default _ nil))))
                       (account-shared/format-trade-price value)
                       "0.00")
        text (some-> formatted str str/trim)]
    (cond
      (not (seq text)) "$0.00"
      (or (str/starts-with? text "$")
          (str/starts-with? text "<$"))
      text
      :else
      (str "$" text))))

(defn strip-dollar-prefix
  [text]
  (cond
    (str/starts-with? text "<$") (str "<" (subs text 2))
    (str/starts-with? text "$") (subs text 1)
    :else text))

(defn format-axis-price-text
  [format-price value]
  (let [formatted (or (when (fn? format-price)
                         (or (try
                               (format-price value value)
                               (catch :default _ nil))
                             (try
                               (format-price value)
                               (catch :default _ nil))))
                       (account-shared/format-trade-price value)
                       "0.00")
        text (some-> formatted str str/trim strip-dollar-prefix)]
    (if (seq text) text "0.00")))

(defn format-size-text
  [format-size value]
  (or (when (fn? format-size)
        (format-size value))
      (account-shared/format-currency value)
      "0.00"))

(defn format-pnl-text
  [pnl]
  (let [pnl* (or (support/parse-number pnl) 0)
        sign (cond
               (pos? pnl*) "+"
               (neg? pnl*) "-"
               :else "")]
    (str sign "$" (account-shared/format-currency (js/Math.abs pnl*)))))
