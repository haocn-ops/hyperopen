(ns hyperopen.domain.trading.indicators.price
  (:require [hyperopen.domain.trading.indicators.catalog.price :as catalog]
            [hyperopen.domain.trading.indicators.family-runtime :as family-runtime]
            [hyperopen.domain.trading.indicators.math :as imath]
            [hyperopen.domain.trading.indicators.result :as result]))

(def ^:private price-indicator-definitions catalog/price-indicator-definitions)

(declare price-family)

(defn get-price-indicators
  []
  (family-runtime/indicators price-family))

(def ^:private field-values imath/field-values)
(def ^:private hl2-values imath/hl2-values)

(defn- calculate-average-price
  [data _params]
  (let [opens (field-values data :open)
        highs (field-values data :high)
        lows (field-values data :low)
        closes (field-values data :close)
        size (count data)
        values (mapv (fn [idx]
                       (/ (+ (nth opens idx)
                             (nth highs idx)
                             (nth lows idx)
                             (nth closes idx))
                          4))
                     (range size))]
    (result/indicator-result :average-price
                             :overlay
                             [(result/line-series :ohlc4 values)])))

(defn- calculate-median-price
  [data _params]
  (let [high-values (field-values data :high)
        low-values (field-values data :low)
        values (hl2-values high-values low-values)]
    (result/indicator-result :median-price
                             :overlay
                             [(result/line-series :median values)])))

(defn- calculate-typical-price
  [data _params]
  (let [high-values (field-values data :high)
        low-values (field-values data :low)
        close-values (field-values data :close)
        values (mapv (fn [idx]
                       (/ (+ (nth high-values idx)
                             (nth low-values idx)
                             (nth close-values idx))
                          3))
                     (range (count high-values)))]
    (result/indicator-result :typical-price
                             :overlay
                             [(result/line-series :typical values)])))

(def ^:private price-calculators
  {:average-price calculate-average-price
   :median-price calculate-median-price
   :typical-price calculate-typical-price})

(def ^:private price-family
  (family-runtime/build-family :price
                               price-indicator-definitions
                               price-calculators))

(defn supported-price-indicator-ids
  []
  (family-runtime/supported-indicator-ids price-family))

(defn calculate-price-indicator
  [indicator-type data params]
  (family-runtime/calculate price-family indicator-type data params))
