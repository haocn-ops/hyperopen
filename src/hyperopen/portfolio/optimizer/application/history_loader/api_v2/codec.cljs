(ns hyperopen.portfolio.optimizer.application.history-loader.api-v2.codec
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def contract-version
  "optimizer-history-api-v2")

(defn non-blank-text
  [value]
  (coercion/non-blank-text value))

(defn finite-number?
  [value]
  (coercion/finite-number? value))

(defn parse-number
  [value]
  (coercion/parse-number value))

(defn parse-ms
  [value]
  (coercion/parse-ms value))

(defn- kebab-token
  [value]
  (-> value
      (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
      (str/replace #"_" "-")
      str/lower-case))

;; API bundles kebab-case EVERY key of EVERY candle point (~50 assets × ~1100
;; points × ~6 keys per load) over a tiny fixed field vocabulary. Uncached,
;; the regex pipeline above dominated multi-second main-thread tasks in
;; profiling (2026-07-08 trace: ~50% of two 2s normalize tasks), so the
;; string→keyword conversion is memoized. The vocabulary is bounded by the
;; API contract's field names, so the cache cannot grow with payload size.
(def ^:private kebab-keyword-cache (js/Map.))

(defn- string->kebab-keyword
  [s]
  (or (.get kebab-keyword-cache s)
      (let [kw (keyword (kebab-token s))]
        (.set kebab-keyword-cache s kw)
        kw)))

(defn- key->kebab-keyword
  [key]
  (cond
    (keyword? key) (string->kebab-keyword (name key))
    (string? key) (string->kebab-keyword key)
    :else key))

(defn normalize-api-map
  [value]
  (cond
    (map? value)
    (into {}
          (map (fn [[k v]]
                 [(key->kebab-keyword k) (normalize-api-map v)]))
          value)

    (sequential? value)
    (mapv normalize-api-map value)

    :else
    value))

(defn keyed-map-field
  [m snake-key kebab-key]
  (or (get m (keyword snake-key))
      (get m (keyword kebab-key))
      (get m snake-key)
      (get m kebab-key)
      {}))

(defn keyed-map-id
  [key]
  (cond
    (keyword? key) (subs (str key) 1)
    (string? key) key
    :else (str key)))

;; Same memoization rationale as the key cache: keyword-like runs per candle
;; point (:component) and per series/warning enum field, always over the API
;; contract's bounded enum vocabulary. Only strings are cached - other input
;; shapes stay on the uncached coercion path.
(def ^:private keyword-like-cache (js/Map.))

(defn keyword-like
  [value]
  (if (string? value)
    (or (.get keyword-like-cache value)
        (let [normalized (coercion/normalize-keyword-like value)]
          (.set keyword-like-cache value normalized)
          normalized))
    (coercion/normalize-keyword-like value)))

(defn normalize-warning
  [warning]
  (let [warning* (normalize-api-map warning)]
    (cond-> warning*
      (:code warning*) (update :code keyword-like))))

(defn normalize-warnings
  [warnings]
  (mapv normalize-warning (or warnings [])))

(defn- warning-key
  [warning]
  [(:code warning)
   (:instrument-id warning)
   (:proxy-mapping-id warning)
   (:source-id warning)
   (:message warning)])

(defn distinct-warnings
  [warnings]
  (vec (vals (reduce (fn [acc warning]
                       (assoc acc (warning-key warning) warning))
                     {}
                     warnings))))

(defn normalize-proxy
  [proxy]
  (when (map? proxy)
    (cond-> (normalize-api-map proxy)
      (:mapping-kind proxy) (update :mapping-kind keyword-like))))
