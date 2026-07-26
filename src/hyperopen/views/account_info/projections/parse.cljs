(ns hyperopen.views.account-info.projections.parse
  (:require [clojure.string :as str]))

(def ^:private epoch-ms-threshold 1000000000000)

(defn parse-optional-num [value]
  (let [num (cond
              (number? value) value
              (string? value) (js/parseFloat value)
              :else js/NaN)]
    (when (and (number? num) (not (js/isNaN num)))
      num)))

(defn parse-num [value]
  (or (parse-optional-num value) 0))

(defn parse-optional-int [value]
  (when-let [num (parse-optional-num value)]
    (js/Math.floor num)))

(defn parse-epoch-ms [value]
  (when-let [num (parse-optional-num value)]
    (let [rounded (js/Math.floor num)]
      (if (< rounded epoch-ms-threshold)
        (* rounded 1000)
        rounded))))

(def parse-time-ms parse-epoch-ms)

(defn boolean-value [value]
  (cond
    (true? value) true
    (false? value) false

    (string? value)
    (let [text (-> value str str/trim str/lower-case)]
      (case text
        "true" true
        "false" false
        nil))

    :else nil))

(defn non-blank-text [value]
  (let [text (some-> value str str/trim)]
    (when (seq text) text)))

(defn normalize-id [value]
  (cond
    (nil? value) nil
    (string? value) (non-blank-text value)
    (number? value) (some-> value str str/trim non-blank-text)
    :else (some-> value str non-blank-text)))
