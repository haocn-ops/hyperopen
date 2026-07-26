(ns hyperopen.leaderboard.normalization
  (:require [clojure.string :as str]))

(def ^:private known-window-keys
  #{:day :week :month :all-time})

(def default-window-performance
  {:pnl 0
   :roi 0
   :volume 0})

(def default-window-performances
  {:day default-window-performance
   :week default-window-performance
   :month default-window-performance
   :all-time default-window-performance})

(def ^:private metric-aliases
  {:pnl :pnl
   :roi :roi
   :volume :volume
   :vlm :volume})

(def ^:private window-key-aliases
  {:day :day
   :week :week
   :month :month
   :all-time :all-time
   :alltime :all-time
   :all :all-time})

(defn non-blank-text
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      text)))

(defn normalize-address
  [value]
  (some-> value non-blank-text str/lower-case))

(defn parse-optional-num
  [value]
  (let [num (cond
              (number? value) value
              (string? value) (js/Number (str/trim value))
              :else js/NaN)]
    (when (and (number? num)
               (js/isFinite num))
      num)))

(defn- normalize-token
  [value]
  (some-> (cond
            (keyword? value) (name value)
            :else value)
          non-blank-text
          (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
          str/lower-case
          (str/replace #"[^a-z0-9]+" "-")
          (str/replace #"(^-+)|(-+$)" "")
          keyword))

(defn normalize-window-key
  [value]
  (let [token (normalize-token value)
        normalized (get window-key-aliases token token)]
    (when (contains? known-window-keys normalized)
      normalized)))

(defn- normalize-metric-key
  [value]
  (some-> value
          normalize-token
          metric-aliases))

(defn normalize-window-performance
  [payload]
  (when (map? payload)
    (reduce-kv (fn [acc metric value]
                 (let [metric* (normalize-metric-key metric)
                       value* (parse-optional-num value)]
                   (if (and metric* (number? value*))
                     (assoc acc metric* value*)
                     acc)))
               default-window-performance
               payload)))

(defn normalize-window-performances
  [payload]
  (let [entries (cond
                  (map? payload) payload
                  (sequential? payload) payload
                  :else [])]
    (reduce (fn [acc entry]
              (if (and (sequential? entry)
                       (= 2 (count entry)))
                (let [[window-key value] entry
                      window-key* (normalize-window-key window-key)
                      value* (normalize-window-performance value)]
                  (if (and window-key* value*)
                    (assoc acc window-key* value*)
                    acc))
                acc))
            default-window-performances
            entries)))
