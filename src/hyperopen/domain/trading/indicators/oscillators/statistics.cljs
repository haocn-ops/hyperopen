(ns hyperopen.domain.trading.indicators.oscillators.statistics
  (:require [hyperopen.domain.trading.indicators.math.statistics :as mstats]
            [hyperopen.domain.trading.indicators.math :as imath]
            [hyperopen.domain.trading.indicators.result :as result]))

(def ^:private finite-number? imath/finite-number?)
(def ^:private finite-subtract imath/finite-subtract)
(def ^:private safe-percent-ratio imath/safe-percent-ratio)
(def ^:private parse-period imath/parse-period)
(def ^:private field-values imath/field-values)

(defn- tsi-core
  [close-values short-period long-period]
  (let [size (count close-values)
        mtm (mapv (fn [idx]
                    (if (pos? idx)
                      (- (nth close-values idx) (nth close-values (dec idx)))
                      nil))
                  (range size))
        abs-mtm (mapv (fn [v]
                        (when (finite-number? v)
                          (js/Math.abs v)))
                      mtm)
        ema1 (imath/ema-values mtm short-period)
        ema2 (imath/ema-values ema1 long-period)
        abs-ema1 (imath/ema-values abs-mtm short-period)
        abs-ema2 (imath/ema-values abs-ema1 long-period)]
    (mapv (fn [a b]
            (safe-percent-ratio a b))
          ema2 abs-ema2)))

(defn calculate-correlation-coefficient
  [data params]
  (let [period (parse-period (:period params) 20 3 400)
        values (mstats/rolling-correlation-with-time (field-values data :close) period)]
    (result/indicator-result :correlation-coefficient
                             :separate
                             [(result/line-series :correlation values)])))

(defn calculate-correlation-log
  [data params]
  (let [period (parse-period (:period params) 20 3 400)
        close-values (field-values data :close)
        logged (mapv (fn [price]
                       (when (and (finite-number? price)
                                  (pos? price))
                         (js/Math.log price)))
                     close-values)
        values (mstats/rolling-correlation-with-time logged period)]
    (result/indicator-result :correlation-log
                             :separate
                             [(result/line-series :correlation-log values)])))

(defn calculate-true-strength-index
  [data params]
  (let [short-period (parse-period (:short params) 13 2 200)
        long-period (parse-period (:long params) 25 2 200)
        tsi (tsi-core (field-values data :close) short-period long-period)]
    (result/indicator-result :true-strength-index
                             :separate
                             [(result/line-series :tsi tsi)])))

(defn calculate-trend-strength-index
  [data params]
  (let [short-period (parse-period (:short params) 13 2 200)
        long-period (parse-period (:long params) 25 2 200)
        signal-period (parse-period (:signal params) 13 2 200)
        tsi (tsi-core (field-values data :close) short-period long-period)
        trend (mapv (fn [value]
                      (when (finite-number? value)
                        (js/Math.abs value)))
                    tsi)
        signal (imath/ema-values trend signal-period)]
    (result/indicator-result :trend-strength-index
                             :separate
                             [(result/line-series :trend-si trend)
                              (result/line-series :signal signal)])))

(defn calculate-smi-ergodic
  [data params]
  (let [short-period (parse-period (:short params) 13 2 200)
        long-period (parse-period (:long params) 25 2 200)
        signal-period (parse-period (:signal params) 13 2 200)
        tsi (tsi-core (field-values data :close) short-period long-period)
        signal (imath/ema-values tsi signal-period)
        osc (mapv (fn [t s]
                    (finite-subtract t s))
                  tsi signal)]
    (result/indicator-result :smi-ergodic
                             :separate
                             [(result/histogram-series :osc osc)
                              (result/line-series :indicator tsi)
                              (result/line-series :signal signal)])))
