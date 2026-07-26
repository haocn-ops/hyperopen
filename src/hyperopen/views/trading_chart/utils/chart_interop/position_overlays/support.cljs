(ns hyperopen.views.trading-chart.utils.chart-interop.position-overlays.support
  (:require [hyperopen.views.account-info.shared :as account-shared]))

(defonce ^:private position-overlays-sidecar (js/WeakMap.))

(defn overlay-state
  [chart-obj]
  (if chart-obj
    (or (.get position-overlays-sidecar chart-obj) {})
    {}))

(defn set-overlay-state!
  [chart-obj state]
  (when chart-obj
    (.set position-overlays-sidecar chart-obj state))
  state)

(defn delete-overlay-state!
  [chart-obj]
  (when chart-obj
    (.delete position-overlays-sidecar chart-obj)))

(defn finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))))

(defn non-negative-number
  [value fallback]
  (if (and (finite-number? value)
           (not (neg? value)))
    value
    fallback))

(defn parse-number
  [value]
  (account-shared/parse-optional-num value))

(defn clamp
  [value min-value max-value]
  (-> value
      (max min-value)
      (min max-value)))

(defn apply-inline-style!
  [el style-map]
  (let [style (.-style el)]
    (doseq [[k v] style-map]
      (when (not= v (aget style k))
        (aset style k v))))
  el)

(defn create-text-node!
  [document text]
  (.createTextNode document (or (some-> text str) "")))

(defn set-text-node-value!
  [text-node text]
  (let [next-text (or (some-> text str) "")
        current-text (or (some-> text-node .-data)
                         (some-> text-node .-nodeValue)
                         "")]
    (when (not= next-text current-text)
      (aset text-node "data" next-text)
      (aset text-node "nodeValue" next-text)))
  text-node)

(defn invoke-method
  [target method-name & args]
  (let [method (when target
                 (aget target method-name))]
    (when (fn? method)
      (.apply method target (to-array args)))))

(defn clear-children!
  [el]
  (loop []
    (when-let [child (.-firstChild el)]
      (.removeChild el child)
      (recur))))

(defn append-child!
  [parent child]
  (when (and parent child)
    (when-let [current-parent (.-parentNode child)]
      (when-not (identical? current-parent parent)
        (.removeChild current-parent child)))
    (when-not (identical? (.-parentNode child) parent)
      (.appendChild parent child)))
  child)

(defn resolve-document
  [document]
  (or document
      (some-> js/globalThis .-document)))
