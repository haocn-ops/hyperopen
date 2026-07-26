(ns hyperopen.domain.trading.order-values
  (:require [clojure.string :as str]
            [hyperopen.domain.trading.core :as core]))

(defn- ->raw-string [value]
  (cond
    (string? value) value
    (nil? value) ""
    :else (str value)))

(defn order-type-value [value]
  {:kind :order-type
   :raw value
   :value (core/normalize-order-type value)})

(defn tif-value [value]
  (let [candidate (cond
                    (keyword? value) value
                    (string? value) (keyword value)
                    :else nil)
        value* (if (some #{candidate} core/tif-options)
                 candidate
                 :gtc)]
    {:kind :tif
     :raw value
     :value value*}))

(defn side-value [value]
  (let [candidate (cond
                    (keyword? value) value
                    (string? value) (keyword value)
                    :else nil)
        value* (if (some #{candidate} #{:buy :sell})
                 candidate
                 :buy)]
    {:kind :side
     :raw value
     :value value*}))

(defn price-value [value]
  (let [raw (->raw-string value)
        parsed (core/parse-num raw)]
    {:kind :price
     :raw raw
     :present? (not (str/blank? raw))
     :value parsed
     :valid? (and (number? parsed) (pos? parsed))}))

(defn size-value [value]
  (let [raw (->raw-string value)
        parsed (core/parse-num raw)]
    {:kind :size
     :raw raw
     :present? (not (str/blank? raw))
     :value parsed
     :valid? (and (number? parsed) (pos? parsed))}))

(defn percent-value [value]
  {:kind :percent
   :raw value
   :value (core/clamp-percent value)})

(defn leverage-value [context value]
  {:kind :leverage
   :raw value
   :value (core/normalize-ui-leverage context value)})
