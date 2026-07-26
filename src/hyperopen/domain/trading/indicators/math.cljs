(ns hyperopen.domain.trading.indicators.math)

(defn finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))
       (js/isFinite value)))

(defn ensure-vec
  [values]
  (cond
    (vector? values) values
    (array? values) (vec (array-seq values))
    (sequential? values) (vec values)
    :else []))

(defn normalize-values
  ([values]
   (normalize-values values {}))
  ([values {:keys [zero-as-nil?]}]
   (mapv (fn [value]
           (cond
             (not (finite-number? value)) nil
             (and zero-as-nil? (zero? value)) nil
             :else value))
         (ensure-vec values))))

(defn clamp
  [value minimum maximum]
  (-> value
      (max minimum)
      (min maximum)))

(defn parse-period
  [value default minimum maximum]
  (let [base (if (number? value)
               value
               (js/parseInt (str value) 10))
        numeric (if (js/isNaN base) default base)]
    (clamp (int numeric) minimum maximum)))

(defn parse-number
  [value default]
  (let [numeric (if (number? value)
                  value
                  (js/parseFloat (str value)))]
    (if (js/isNaN numeric) default numeric)))

(defn finite-subtract
  [a b]
  (when (and (finite-number? a)
             (finite-number? b))
    (- a b)))

(defn band-upper-value
  [base spread multiplier]
  (when (and (finite-number? base)
             (finite-number? spread))
    (+ base (* multiplier spread))))

(defn band-lower-value
  [base spread multiplier]
  (when (and (finite-number? base)
             (finite-number? spread))
    (- base (* multiplier spread))))

(defn band-upper-values
  [base-values spread-values multiplier]
  (mapv (fn [base spread]
          (band-upper-value base spread multiplier))
        base-values spread-values))

(defn band-lower-values
  [base-values spread-values multiplier]
  (mapv (fn [base spread]
          (band-lower-value base spread multiplier))
        base-values spread-values))

(defn finite-ratio
  [numerator denominator]
  (when (and (finite-number? numerator)
             (finite-number? denominator)
             (not (zero? denominator)))
    (/ numerator denominator)))

(defn safe-percent-ratio
  [numerator denominator]
  (when-let [ratio (finite-ratio numerator denominator)]
    (* 100 ratio)))

(defn true-range-at
  [high low prev-close]
  (let [range-1 (- high low)
        range-2 (if (finite-number? prev-close)
                  (js/Math.abs (- high prev-close))
                  range-1)
        range-3 (if (finite-number? prev-close)
                  (js/Math.abs (- low prev-close))
                  range-1)]
    (max range-1 range-2 range-3)))

(defn true-range-index
  [high-values low-values close-values idx]
  (let [high (nth high-values idx)
        low (nth low-values idx)
        prev-close (if (zero? idx)
                     (nth close-values idx)
                     (nth close-values (dec idx)))]
    (true-range-at high low prev-close)))

(defn true-range-values
  [high-values low-values close-values]
  (mapv (fn [idx]
          (true-range-index high-values low-values close-values idx))
        (range (count high-values))))

(defn roc-percent-at
  [values idx period]
  (if (< idx period)
    nil
    (let [current (nth values idx)
          base (nth values (- idx period))]
      (safe-percent-ratio (finite-subtract current base)
                          base))))

(defn roc-percent-values
  [values period]
  (mapv (fn [idx]
          (roc-percent-at values idx period))
        (range (count values))))

(defn hl2-at
  [high-values low-values idx]
  (/ (+ (nth high-values idx)
        (nth low-values idx))
     2))

(defn hl2-values
  [high-values low-values]
  (mapv (fn [idx]
          (hl2-at high-values low-values idx))
        (range (count high-values))))

(defn times
  [data]
  (mapv :time data))

(defn field-values
  [data field]
  (mapv (fn [candle]
          (let [value (get candle field)]
            (if (number? value)
              value
              (js/parseFloat (str value)))))
        data))

(defn mean
  [values]
  (when (seq values)
    (/ (reduce + 0 values)
       (count values))))

(defn window-for-index
  ([values idx period]
   (window-for-index values idx period :aligned))
  ([values idx period mode]
   (case mode
     :lagged (when (and (pos? period) (>= idx period))
               (subvec values
                       (inc (- idx period))
                       (inc idx)))
     :aligned (when (and (pos? period) (>= idx (dec period)))
                (subvec values
                        (- (inc idx) period)
                        (inc idx)))
     nil)))

(defn rolling-apply
  ([values period f]
   (rolling-apply values period f :aligned))
  ([values period f mode]
   (let [size (count values)]
     (mapv (fn [idx]
             (when-let [window (window-for-index values idx period mode)]
               (when (every? finite-number? window)
                 (f window))))
           (range size)))))

(defn rolling-sum
  ([values period]
   (rolling-sum values period :aligned))
  ([values period mode]
   (rolling-apply values period (fn [window] (reduce + 0 window)) mode)))

(defn sma-values
  ([values period]
   (sma-values values period :aligned))
  ([values period mode]
   (rolling-apply values period mean mode)))

(defn population-stddev
  [values]
  (let [avg (mean values)
        variance (/ (reduce + 0 (map (fn [value]
                                       (let [delta (- value avg)]
                                         (* delta delta)))
                                     values))
                    (count values))]
    (js/Math.sqrt variance)))

(defn stddev-values
  ([values period]
   (stddev-values values period :aligned))
  ([values period mode]
   (rolling-apply values period population-stddev mode)))

(defn ema-values
  [values period]
  (let [size (count values)]
    (if (pos? period)
      (let [alpha (/ 2 (inc period))]
        (loop [idx 0
               prev nil
               out []]
          (if (= idx size)
            out
            (let [value (nth values idx)
                  seeded? (and (nil? prev)
                               (>= idx (dec period))
                               (every? finite-number?
                                       (subvec values (- (inc idx) period) (inc idx))))
                  seed (when seeded?
                         (mean (subvec values (- (inc idx) period) (inc idx))))
                  current (cond
                            (not (finite-number? value)) nil
                            seeded? seed
                            (finite-number? prev) (+ prev (* alpha (- value prev)))
                            :else nil)]
              (recur (inc idx)
                     current
                     (conj out current))))))
      (vec (repeat size nil)))))

(defn rma-values
  ([values period]
   (rma-values values period :aligned))
  ([values period mode]
   (let [size (count values)
         start-idx (case mode
                     :lagged period
                     :aligned (dec period)
                     (dec period))]
     (if (pos? period)
       (loop [idx 0
              prev nil
              out []]
         (if (= idx size)
           out
           (let [value (nth values idx)
                 window (when (and (nil? prev)
                                   (>= idx start-idx))
                          (window-for-index values idx period mode))
                 seeded? (and (vector? window)
                              (every? finite-number? window))
                 seed (when seeded?
                        (mean window))
                 current (cond
                           (not (finite-number? value)) nil
                           seeded? seed
                           (finite-number? prev) (/ (+ (* prev (dec period))
                                                       value)
                                                    period)
                           :else nil)]
             (recur (inc idx)
                    current
                    (conj out current)))))
       (vec (repeat size nil))))))

(defn rolling-max
  ([values period]
   (rolling-max values period :aligned))
  ([values period mode]
   (rolling-apply values period (fn [window] (apply max window)) mode)))

(defn rolling-min
  ([values period]
   (rolling-min values period :aligned))
  ([values period mode]
   (rolling-apply values period (fn [window] (apply min window)) mode)))
