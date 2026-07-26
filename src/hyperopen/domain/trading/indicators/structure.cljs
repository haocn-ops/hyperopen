(ns hyperopen.domain.trading.indicators.structure
  (:require [hyperopen.domain.trading.indicators.catalog.structure :as catalog]
            [hyperopen.domain.trading.indicators.family-runtime :as family-runtime]
            [hyperopen.domain.trading.indicators.math.patterns :as patterns]
            [hyperopen.domain.trading.indicators.math.statistics :as mstats]
            [hyperopen.domain.trading.indicators.math :as imath]
            [hyperopen.domain.trading.indicators.result :as result]))

(def ^:private structure-indicator-definitions catalog/structure-indicator-definitions)

(declare structure-family)

(defn get-structure-indicators
  []
  (family-runtime/indicators structure-family))

(def ^:private finite-number? imath/finite-number?)
(def ^:private parse-period imath/parse-period)
(def ^:private parse-number imath/parse-number)
(def ^:private times imath/times)
(def ^:private field-values imath/field-values)

(defn- calculate-pivot-points-standard
  [data params]
  (let [period (parse-period (:period params) 20 2 400)
        highs-v (field-values data :high)
        lows-v (field-values data :low)
        closes-v (field-values data :close)
        size (count data)
        pivot-data (mapv (fn [idx]
                           (when (>= idx period)
                             (let [from (- idx period)
                                   to idx
                                   window-high (subvec highs-v from to)
                                   window-low (subvec lows-v from to)
                                   window-close (subvec closes-v from to)
                                   h (apply max window-high)
                                   l (apply min window-low)
                                   c (last window-close)
                                   pp (/ (+ h l c) 3)
                                   r1 (- (* 2 pp) l)
                                   s1 (- (* 2 pp) h)
                                   r2 (+ pp (- h l))
                                   s2 (- pp (- h l))
                                   r3 (+ h (* 2 (- pp l)))
                                   s3 (- l (* 2 (- h pp)))]
                               {:pp pp :r1 r1 :s1 s1 :r2 r2 :s2 s2 :r3 r3 :s3 s3})))
                         (range size))
        pick (fn [k]
               (mapv #(get % k) pivot-data))]
    (result/indicator-result :pivot-points-standard
                             :overlay
                             [(result/line-series :pp (pick :pp))
                              (result/line-series :r1 (pick :r1))
                              (result/line-series :s1 (pick :s1))
                              (result/line-series :r2 (pick :r2))
                              (result/line-series :s2 (pick :s2))
                              (result/line-series :r3 (pick :r3))
                              (result/line-series :s3 (pick :s3))])))

(defn- calculate-rank-correlation-index
  [data params]
  (let [period (parse-period (:period params) 9 3 400)
        close-values (field-values data :close)
        size (count data)
        values (mapv (fn [idx]
                       (when-let [window (imath/window-for-index close-values idx period :aligned)]
                         (let [price-ranks (mstats/tie-aware-ranks window)
                               time-ranks (vec (range 1 (inc period)))
                               d-squared (reduce + 0
                                                 (map (fn [time-rank price-rank]
                                                        (let [d (- time-rank price-rank)]
                                                          (* d d)))
                                                      time-ranks price-ranks))]
                           (* 100
                              (- 1 (/ (* 6 d-squared)
                                      (* period (- (* period period) 1))))))))
                     (range size))]
    (result/indicator-result :rank-correlation-index
                             :separate
                             [(result/line-series :rci values)])))

(defn- calculate-williams-fractal
  [data _params]
  (let [high-values (field-values data :high)
        low-values (field-values data :low)
        size (count data)
        time-values (times data)
        markers (->> (range size)
                     (reduce (fn [acc idx]
                               (if (or (< idx 2) (>= idx (- size 2)))
                                 acc
                                 (let [time (nth time-values idx)
                                       high-window (subvec high-values (- idx 2) (+ idx 3))
                                       low-window (subvec low-values (- idx 2) (+ idx 3))
                                       center-high (nth high-values idx)
                                       center-low (nth low-values idx)
                                       bearish? (and (finite-number? center-high)
                                                     (= center-high (apply max high-window))
                                                     (= 1 (count (filter #(= % center-high) high-window))))
                                       bullish? (and (finite-number? center-low)
                                                     (= center-low (apply min low-window))
                                                     (= 1 (count (filter #(= % center-low) low-window))))
                                       with-bearish (if bearish?
                                                      (conj acc {:id (str "fractal-high-" time)
                                                                 :time time
                                                                 :kind :fractal-high
                                                                 :price center-high})
                                                      acc)]
                                   (if bullish?
                                     (conj with-bearish {:id (str "fractal-low-" time)
                                                         :time time
                                                         :kind :fractal-low
                                                         :price center-low})
                                     with-bearish))))
                             [])
                     vec)]
    (result/indicator-result :williams-fractal
                             :overlay
                             []
                             markers)))

(defn- calculate-zig-zag
  [data params]
  (let [threshold-percent (parse-number (:threshold-percent params) 5)
        threshold (max 0.001 (/ threshold-percent 100))
        close-values (field-values data :close)
        pivots (patterns/zigzag-pivots close-values threshold)
        values (patterns/interpolate-zigzag (count data) pivots)]
    (result/indicator-result :zig-zag
                             :overlay
                             [(result/line-series :zig-zag values)])))

(def ^:private structure-calculators
  {:pivot-points-standard calculate-pivot-points-standard
   :rank-correlation-index calculate-rank-correlation-index
   :williams-fractal calculate-williams-fractal
   :zig-zag calculate-zig-zag})

(def ^:private structure-family
  (family-runtime/build-family :structure
                               structure-indicator-definitions
                               structure-calculators))

(defn supported-structure-indicator-ids
  []
  (family-runtime/supported-indicator-ids structure-family))

(defn calculate-structure-indicator
  [indicator-type data params]
  (family-runtime/calculate structure-family indicator-type data params))
