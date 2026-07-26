(ns hyperopen.views.portfolio.format
  (:require [clojure.string :as string]
            [hyperopen.ui.fonts :as fonts]
            [hyperopen.utils.formatting :as fmt]))

(def ^:private compact-currency-format-options
  {:style "currency"
   :currency "USD"
   :notation "compact"
   :maximumFractionDigits 1})

(defn format-currency [value]
  (or (fmt/format-currency value)
      "$0.00"))

(defn format-compact-currency [value]
  (let [n (if (number? value) value 0)]
    (or (fmt/format-intl-number n compact-currency-format-options)
        "$0")))

(defn format-fee-pct [pct]
  (let [n (if (number? pct) pct 0)]
    (str (.toFixed n 3) "%")))

(defn format-percent [pct]
  (or (fmt/format-signed-percent pct {:decimals 2
                                      :signed? false})
      "0.00%"))

(defn format-signed-percent-from-decimal [value]
  (fmt/format-signed-percent-from-decimal value
                                          {:decimals 2
                                           :signed? true}))

(defn format-ratio-value [value]
  (fmt/format-ratio value 2))

(defn format-integer-value [value]
  (fmt/format-integer value))

(defn format-metric-value [kind value]
  (case kind
    :percent (or (format-signed-percent-from-decimal value) "--")
    :ratio (or (format-ratio-value value) "--")
    :integer (or (format-integer-value value) "--")
    :date (if (and (string? value)
                   (seq (string/trim value)))
            value
            "--")
    "--"))

(defn format-drawdown [ratio]
  (if (number? ratio)
    (format-percent (* ratio 100))
    "N/A"))

(defn format-hype [value]
  (let [n (if (number? value) value 0)]
    (str (or (fmt/format-integer n)
             "0")
         " HYPE")))

(defn format-axis-number [value]
  (let [n (if (number? value) value 0)]
    (or (fmt/format-integer n)
        "0")))

(defn format-axis-percent [value]
  (or (fmt/format-signed-percent value
                                 {:decimals 2
                                  :signed? true})
      "0.00%"))

(defn format-axis-label [axis-kind value]
  (if (= axis-kind :percent)
    (format-axis-percent value)
    (format-axis-number value)))

(def ^:private axis-label-fallback-char-width-px
  7.5)

(def ^:private axis-label-horizontal-padding-px
  30)

(def ^:private axis-label-min-gutter-width-px
  56)

(def ^:private axis-label-measure-context
  (delay
    (when (and (exists? js/document)
               (some? js/document))
      (let [canvas (.createElement js/document "canvas")]
        (.getContext canvas "2d")))))

(defn- axis-label-width-px [text]
  (let [context @axis-label-measure-context]
    (if context
      (do
        (set! (.-font context) (fonts/canvas-font 12))
        (-> context
            (.measureText text)
            .-width))
      (* axis-label-fallback-char-width-px (count text)))))

(defn y-axis-gutter-width [axis-kind y-ticks]
  (let [widest-label-px (->> y-ticks
                             (map (fn [{:keys [value]}]
                                    (axis-label-width-px (format-axis-label axis-kind value))))
                             (reduce max 0))
        gutter-width (+ widest-label-px axis-label-horizontal-padding-px)]
    (js/Math.ceil (max axis-label-min-gutter-width-px gutter-width))))
