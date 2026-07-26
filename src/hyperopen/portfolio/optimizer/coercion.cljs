(ns hyperopen.portfolio.optimizer.coercion
  (:require [clojure.string :as str]))

(defn non-blank-text
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      text)))

(defn finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))
       (js/isFinite value)))

(defn parse-number
  [value]
  (cond
    (finite-number? value)
    value

    (string? value)
    (when-let [text (non-blank-text value)]
      (let [parsed (js/Number text)]
        (when (finite-number? parsed)
          parsed)))

    :else
    nil))

(defn parse-float-number
  [value]
  (cond
    (finite-number? value)
    value

    (string? value)
    (when-let [text (non-blank-text value)]
      (let [parsed (js/parseFloat text)]
        (when (finite-number? parsed)
          parsed)))

    :else
    nil))

(defn parse-ms
  [value]
  (when-let [parsed (parse-number value)]
    (js/Math.floor parsed)))

(defn positive-number?
  [value]
  (and (finite-number? value)
       (pos? value)))

(defn parse-boolean-value
  [value]
  (cond
    (boolean? value) value
    (string? value) (case (str/lower-case (str/trim value))
                      "true" true
                      "false" false
                      nil)
    :else nil))

(defn parse-percent-text
  [value]
  (cond
    (finite-number? value)
    (/ value 100)

    (string? value)
    (let [text (-> value
                   str/trim
                   (str/replace #"," "")
                   (str/replace #"%" "")
                   str/trim)]
      (when (seq text)
        (let [parsed (js/Number text)]
          (when (finite-number? parsed)
            (/ parsed 100)))))

    :else
    nil))

(defn decimal->percent-text
  [value]
  (if (finite-number? value)
    (-> (.toFixed (* value 100) 4)
        (str/replace #"\.?0+$" ""))
    ""))

(defn normalize-keyword-like
  [value]
  (let [text (cond
               (keyword? value) (name value)
               (string? value) (str/trim value)
               :else nil)]
    (when (seq text)
      (-> text
          (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
          (str/replace #"[_\s]+" "-")
          str/lower-case
          keyword))))

(defn normalize-enum
  [value valid-values fallback]
  (let [value* (normalize-keyword-like value)]
    (if (contains? valid-values value*)
      value*
      fallback)))

(defn normalize-id-list
  [values]
  (->> (cond
         (nil? values) []
         (set? values) values
         (sequential? values) values
         :else [values])
       (keep non-blank-text)
       distinct
       vec))
