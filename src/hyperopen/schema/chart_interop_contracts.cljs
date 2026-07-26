(ns hyperopen.schema.chart-interop-contracts
  (:require [cljs.spec.alpha :as s]
            [clojure.string :as str]))

(defn validation-enabled?
  []
  ^boolean goog.DEBUG)

(defn- finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))))

(defn- parse-int-like
  [value]
  (cond
    (integer? value) value
    (and (number? value)
         (not (js/isNaN value))
         (= value (js/Math.floor value)))
    value
    (string? value) (let [text (str/trim value)]
                      (when (re-matches #"[+-]?\\d+" text)
                        (js/parseInt text 10)))
    :else nil))

(defn- positive-int-like?
  [value]
  (when-let [parsed (parse-int-like value)]
    (pos? parsed)))

(defn- non-empty-string?
  [value]
  (and (string? value)
       (seq (str/trim value))))

(defn- business-day?
  [value]
  (let [m (if (map? value) value (some-> value (js->clj :keywordize-keys true)))]
    (and (map? m)
         (positive-int-like? (:year m))
         (positive-int-like? (:month m))
         (positive-int-like? (:day m)))))

(defn- has-property?
  [value property]
  (let [prop-name (name property)]
    (cond
      (map? value) (contains? value property)
      (some? value) (some? (aget value prop-name))
      :else false)))

(s/def ::time
  (s/or :timestamp finite-number?
        :text non-empty-string?
        :business-day business-day?))
(s/def ::open finite-number?)
(s/def ::high finite-number?)
(s/def ::low finite-number?)
(s/def ::close finite-number?)
(s/def ::volume finite-number?)

(s/def ::candle
  (s/keys :req-un [::time ::open ::high ::low ::close]
          :opt-un [::volume]))

(s/def ::kind #{:time :logical})
(s/def ::from finite-number?)
(s/def ::to finite-number?)
(s/def ::visible-range
  (s/and
   (s/keys :req-un [::kind ::from ::to])
   (fn [{:keys [from to]}]
     (<= from to))))

(s/def ::series-type keyword?)
(s/def ::data vector?)
(s/def ::color string?)
(s/def ::line-width finite-number?)
(s/def ::base finite-number?)
(s/def ::series-def
  (s/and
   (s/keys :req-un [::series-type ::data]
           :opt-un [::color ::line-width ::base])
   (fn [{:keys [series-type]}]
     (contains? #{:line :histogram} series-type))))

(s/def ::pane keyword?)
(s/def ::series vector?)
(s/def ::indicator-def
  (s/and
   (s/keys :req-un [::pane ::series])
   (fn [{:keys [pane series]}]
     (and (or (= pane :overlay) (= pane :separate))
          (every? #(s/valid? ::series-def %) series)))))

(s/def ::symbol non-empty-string?)
(s/def ::timeframe-label non-empty-string?)
(s/def ::venue non-empty-string?)
(s/def ::market-open? boolean?)
(s/def ::candle-data sequential?)
(s/def ::legend-meta
  (s/and
   (s/keys :req-un [::candle-data]
           :opt-un [::symbol ::timeframe-label ::venue ::market-open?])
   (fn [{:keys [candle-data]}]
     (every? #(s/valid? ::candle %) candle-data))))

(s/def ::chart-handle
  (fn [value]
    (and (some? value)
         (has-property? value :chart)
         (has-property? value :mainSeries))))

(defn- assert-spec!
  [label spec value context]
  (when (and (validation-enabled?)
             (not (s/valid? spec value)))
    (throw (js/Error.
            (str label " contract validation failed."
                 " context=" (pr-str context)
                 " value=" (pr-str value)
                 " explain=" (pr-str (s/explain-data spec value))))))
  value)

(defn- candle-sample
  [candles]
  (let [items (vec candles)
        n (count items)]
    (cond
      (zero? n) []
      (= 1 n) [(first items)]
      (= 2 n) [(first items) (second items)]
      :else [(first items)
             (nth items (int (js/Math.floor (/ n 2))))
             (last items)])))

(defn assert-candles!
  ([candles context]
   (assert-candles! candles context {}))
  ([candles context {:keys [require-volume?]
                     :or {require-volume? false}}]
  (when (validation-enabled?)
     (when-not (sequential? candles)
       (throw (js/Error.
               (str "candles contract validation failed."
                    " context=" (pr-str context)
                    " expected=sequential?"
                    " value=" (pr-str candles)))))
     (doseq [[idx candle] (map-indexed vector (candle-sample candles))]
       (assert-spec! "candle sample"
                     ::candle
                     candle
                     (assoc context :sample-index idx)))
     (when require-volume?
       (doseq [[idx candle] (map-indexed vector (candle-sample candles))]
         (when-not (finite-number? (:volume candle))
           (throw (js/Error.
                   (str "candle volume contract validation failed."
                        " context=" (pr-str (assoc context :sample-index idx))
                        " candle=" (pr-str candle))))))))
   candles))

(defn assert-visible-range!
  [visible-range context]
  (assert-spec! "visible range" ::visible-range visible-range context))

(defn assert-indicators!
  [indicators context]
  (when (validation-enabled?)
    (when-not (vector? indicators)
      (throw (js/Error.
              (str "indicators contract validation failed."
                   " context=" (pr-str context)
                   " expected=vector?"
                   " value=" (pr-str indicators)))))
    (doseq [[idx indicator] (map-indexed vector indicators)]
      (assert-spec! "indicator"
                    ::indicator-def
                    indicator
                    (assoc context :indicator-index idx))))
  indicators)

(defn assert-legend-meta!
  [legend-meta context]
  (assert-spec! "legend-meta" ::legend-meta legend-meta context))

(defn assert-chart-handle!
  [chart-handle context]
  (assert-spec! "chart-handle" ::chart-handle chart-handle context))
