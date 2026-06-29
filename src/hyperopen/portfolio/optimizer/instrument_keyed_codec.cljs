(ns hyperopen.portfolio.optimizer.instrument-keyed-codec
  (:require [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.ids :as ids]))

(def enum-value-keys
  #{:behavior
    :code
    :default-order-type
    :fee-mode
    :funding-source
    :instrument-type
    :kind
    :market-type
    :model
    :objective-kind
    :order-type
    :reason
    :side
    :source
    :status
    :strategy
    :type})

(def instrument-keyed-map-keys
  #{:by-instrument
    :return-series-by-instrument
    :price-series-by-instrument
    :raw-price-series-by-instrument
    :cadence-by-instrument
    :expected-return-series-by-instrument
    :expected-return-intervals-by-instrument
    :funding-by-instrument
    :weights-by-instrument
    :history-assumptions
    :per-asset-overrides
    :per-perp-leverage-caps
    :prices-by-id
    :cost-contexts-by-id
    :fee-bps-by-id
    :return-decomposition-by-instrument
    :expected-returns-by-instrument
    :current-weights-by-instrument
    :target-weights-by-instrument
    ;; Result-only map (engine/payload.cljs). Without this entry its keys survive
    ;; the worker boundary as keywords (js->clj :keywordize-keys true) and later
    ;; stringify to leading-colon ids (":perp:BTC"), spawning phantom blocked
    ;; "market-metadata-missing" rows in the rebalance preview.
    :current-portfolio-weights-by-instrument
    :weight-sensitivity-by-instrument
    :pair-metadata
    :labels-by-instrument})

;; Compatibility only. Normalization is key-driven and does not depend on this
;; list when new request or result payloads add another nesting level.
(def instrument-keyed-map-paths
  [[:current-portfolio :by-instrument]
   [:history :return-series-by-instrument]
   [:history :price-series-by-instrument]
   [:history :raw-price-series-by-instrument]
   [:history :cadence-by-instrument]
   [:history :expected-return-series-by-instrument]
   [:history :expected-return-intervals-by-instrument]
   [:history :funding-by-instrument]
   [:black-litterman-prior :weights-by-instrument]
   [:constraints :per-asset-overrides]
   [:constraints :per-perp-leverage-caps]
   [:execution-assumptions :prices-by-id]
   [:execution-assumptions :cost-contexts-by-id]
   [:execution-assumptions :fee-bps-by-id]
   [:payload :return-decomposition-by-instrument]
   [:payload :expected-returns-by-instrument]
   [:payload :current-weights-by-instrument]
   [:payload :target-weights-by-instrument]
   [:payload :diagnostics :weight-sensitivity-by-instrument]
   [:payload :diagnostics :pair-metadata]
   [:return-decomposition-by-instrument]
   [:expected-returns-by-instrument]
   [:current-weights-by-instrument]
   [:target-weights-by-instrument]
   [:diagnostics :weight-sensitivity-by-instrument]
   [:diagnostics :pair-metadata]])

(defn- keyword-value
  [value]
  (cond
    (keyword? value) value
    (string? value) (some-> value coercion/non-blank-text keyword)
    :else value))

(def instrument-id-key ids/instrument-id-key)

(defn stringify-instrument-keyed-map
  [value]
  (if (map? value)
    (into {}
          (map (fn [[key item]]
                 [(ids/instrument-id-key key) item]))
          value)
    value))

(declare normalize-instrument-keyed-maps)

(defn normalize-wire-values
  [value]
  (cond
    (map? value)
    (into {}
          (map (fn [[key item]]
                 (let [item* (normalize-wire-values item)]
                   [key (if (contains? enum-value-keys key)
                          (keyword-value item*)
                          item*)])))
          value)

    (vector? value)
    (mapv normalize-wire-values value)

    (seq? value)
    (doall (map normalize-wire-values value))

    :else value))

(defn normalize-instrument-keyed-maps
  [value]
  (cond
    (map? value)
    (into {}
          (map (fn [[key item]]
                 (let [item* (normalize-instrument-keyed-maps item)]
                   [key (if (and (contains? instrument-keyed-map-keys key)
                                 (map? item*))
                          (stringify-instrument-keyed-map item*)
                          item*)])))
          value)

    (vector? value)
    (mapv normalize-instrument-keyed-maps value)

    (seq? value)
    (doall (map normalize-instrument-keyed-maps value))

    :else value))

(defn- update-existing-in
  [value path f]
  (if (nil? (get-in value path))
    value
    (update-in value path f)))

(defn- normalize-black-litterman-view-weights
  [value]
  (update-existing-in
   value
   [:return-model :views]
   (fn [views]
     (mapv (fn [view]
             (update-existing-in view [:weights] stringify-instrument-keyed-map))
           views))))

(defn normalize-worker-boundary
  [value]
  (-> value
      normalize-wire-values
      normalize-instrument-keyed-maps
      normalize-black-litterman-view-weights))
