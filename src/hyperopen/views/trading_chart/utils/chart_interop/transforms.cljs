(ns hyperopen.views.trading-chart.utils.chart-interop.transforms
  (:require [hyperopen.views.trading-chart.utils.theme-colors :as theme-colors]))

(defn hyperliquid-volume-up-color
  "Volume up-bar color from the active theme (--ho-chart-up)."
  []
  (theme-colors/token "--ho-chart-up"))

(defn hyperliquid-volume-down-color
  "Volume down-bar color from the active theme (--ho-chart-down)."
  []
  (theme-colors/token "--ho-chart-down"))

(defn normalize-main-chart-type
  "Normalize aliases to canonical chart-type keywords."
  [chart-type]
  (if (= chart-type :histogram) :columns chart-type))

(defn- vectorize-data
  [data]
  (if (vector? data) data (vec (or data []))))

(defn- close-value
  [candle]
  (:close candle))

(defn- hlc3-value
  [candle]
  (/ (+ (:high candle) (:low candle) (:close candle)) 3))

(defn transform-data-for-single-value
  "Transform OHLC data to single-value data using `value-fn`."
  [data value-fn]
  (map (fn [candle]
         {:value (value-fn candle)
          :time (:time candle)})
       data))

(defn transform-data-for-close
  "Transform OHLC data to close-value points."
  [data]
  (transform-data-for-single-value data close-value))

(defn transform-data-for-hlc3
  "Transform OHLC data to HLC3-value points."
  [data]
  (transform-data-for-single-value data hlc3-value))

(defn- single-value-point
  [candle value-fn]
  {:value (value-fn candle)
   :time (:time candle)})

(defn- close-point
  [candle _previous-point]
  (single-value-point candle close-value))

(defn- hlc3-point
  [candle _previous-point]
  (single-value-point candle hlc3-value))

(defn transform-data-for-columns
  "Transform OHLC data to columns data with directional colors."
  [data]
  (map (fn [candle]
         {:value (:close candle)
          :time (:time candle)
          :color (if (>= (:close candle) (:open candle)) "#26a69a" "#ef5350")})
       data))

(defn- columns-point
  [candle _previous-point]
  {:value (:close candle)
   :time (:time candle)
   :color (if (>= (:close candle) (:open candle)) "#26a69a" "#ef5350")})

(defn- heikin-ashi-point
  [candle previous-point]
  (let [ha-close (/ (+ (:open candle)
                       (:high candle)
                       (:low candle)
                       (:close candle))
                    4)
        ha-open (if (and (map? previous-point)
                         (number? (:open previous-point))
                         (number? (:close previous-point)))
                  (/ (+ (:open previous-point) (:close previous-point)) 2)
                  (/ (+ (:open candle) (:close candle)) 2))]
    {:time (:time candle)
     :open ha-open
     :high (max (:high candle) ha-open ha-close)
     :low (min (:low candle) ha-open ha-close)
     :close ha-close}))

(defn transform-data-for-heikin-ashi
  "Transform raw candles into Heikin Ashi candles."
  [data]
  (loop [remaining data
         prev-ha-open nil
         prev-ha-close nil
         acc []]
    (if (empty? remaining)
      acc
      (let [candle (first remaining)
            transformed-candle (heikin-ashi-point
                                candle
                                (when (and (number? prev-ha-open)
                                           (number? prev-ha-close))
                                  {:open prev-ha-open
                                   :close prev-ha-close}))]
        (recur (rest remaining)
               (:open transformed-candle)
               (:close transformed-candle)
               (conj acc transformed-candle))))))

(defn transform-data-for-high-low
  "Transform candles into solid high-low range bars."
  [data]
  (map (fn [candle]
         {:time (:time candle)
          :open (:low candle)
          :high (:high candle)
          :low (:low candle)
          :close (:high candle)})
       data))

(defn- high-low-point
  [candle _previous-point]
  {:time (:time candle)
   :open (:low candle)
   :high (:high candle)
   :low (:low candle)
   :close (:high candle)})

(defn- raw-ohlc-point
  [candle _previous-point]
  candle)

(defn transform-data-for-volume
  "Transform OHLC data to volume data with directional colors."
  [data]
  (let [up-color (hyperliquid-volume-up-color)
        down-color (hyperliquid-volume-down-color)]
    (map (fn [candle]
           {:value (:volume candle)
            :time (:time candle)
            :color (if (>= (:close candle) (:open candle))
                     up-color
                     down-color)})
         data)))

(defn- volume-point
  [candle _previous-point]
  {:value (:volume candle)
   :time (:time candle)
   :color (if (>= (:close candle) (:open candle))
            (hyperliquid-volume-up-color)
            (hyperliquid-volume-down-color))})

(def ^:private main-series-tail-point-builders
  {:area close-point
   :bar raw-ohlc-point
   :high-low high-low-point
   :baseline close-point
   :hlc-area hlc3-point
   :candlestick raw-ohlc-point
   :hollow-candles raw-ohlc-point
   :heikin-ashi heikin-ashi-point
   :columns columns-point
   :line close-point
   :line-with-markers close-point
   :step-line close-point})

(defn- resolve-main-series-tail-point-builder
  [chart-type]
  (let [normalized-chart-type (normalize-main-chart-type chart-type)]
    (or (get main-series-tail-point-builders normalized-chart-type)
        raw-ohlc-point)))

(defn- supported-tail-decision?
  [decision-mode]
  (contains? #{:update-last :append-last} decision-mode))

(defn- valid-tail-input-shape?
  [previous-source-candles previous-transformed-data next-source-candles decision-mode]
  (let [previous-count (count previous-source-candles)
        transformed-count (count previous-transformed-data)
        next-count (count next-source-candles)]
    (and (= transformed-count previous-count)
         (case decision-mode
           :update-last (and (pos? previous-count)
                             (= previous-count next-count))
           :append-last (= next-count (inc previous-count))
           false))))

(defn- tail-context-point
  [previous-transformed-data decision-mode]
  (case decision-mode
    :update-last (let [previous-count (count previous-transformed-data)]
                   (when (> previous-count 1)
                     (nth previous-transformed-data (- previous-count 2))))
    :append-last (peek previous-transformed-data)
    nil))

(defn- derive-next-tail-data
  [point-builder previous-source-candles previous-transformed-data next-source-candles decision-mode]
  (let [previous-source-candles* (vectorize-data previous-source-candles)
        previous-transformed-data* (vectorize-data previous-transformed-data)
        next-source-candles* (vectorize-data next-source-candles)]
    (when (and (supported-tail-decision? decision-mode)
               (valid-tail-input-shape? previous-source-candles*
                                        previous-transformed-data*
                                        next-source-candles*
                                        decision-mode))
      (let [next-candle (peek next-source-candles*)
            context-point (tail-context-point previous-transformed-data* decision-mode)
            next-point (point-builder next-candle context-point)]
        (case decision-mode
          :update-last (assoc previous-transformed-data*
                              (dec (count previous-transformed-data*))
                              next-point)
          :append-last (conj previous-transformed-data* next-point)
          nil)))))

(defn derive-next-main-series-data
  "Return the next transformed main-series vector for a validated tail-only sync
  decision, or nil when the request cannot be handled incrementally. The caller
  is expected to pass `:update-last` or `:append-last` decisions already deemed
  safe for the same source-candle vectors."
  [previous-source-candles previous-transformed-data next-source-candles chart-type decision-mode]
  (derive-next-tail-data (resolve-main-series-tail-point-builder chart-type)
                         previous-source-candles
                         previous-transformed-data
                         next-source-candles
                         decision-mode))

(defn derive-next-volume-data
  "Return the next transformed volume vector for a validated tail-only sync
  decision, or nil when the request cannot be handled incrementally."
  [previous-source-candles previous-transformed-data next-source-candles decision-mode]
  (derive-next-tail-data volume-point
                         previous-source-candles
                         previous-transformed-data
                         next-source-candles
                         decision-mode))
