(ns hyperopen.domain.trading.indicators.registry
  (:require [hyperopen.domain.trading.indicators.flow :as flow]
            [hyperopen.domain.trading.indicators.oscillators :as oscillators]
            [hyperopen.domain.trading.indicators.price :as price]
            [hyperopen.domain.trading.indicators.structure :as structure]
            [hyperopen.domain.trading.indicators.trend :as trend]
            [hyperopen.domain.trading.indicators.volatility :as volatility]))

(def ^:private built-in-families
  [{:id :trend
    :get-indicators trend/get-trend-indicators
    :calculate-indicator trend/calculate-trend-indicator}
   {:id :structure
    :get-indicators structure/get-structure-indicators
    :calculate-indicator structure/calculate-structure-indicator}
   {:id :oscillators
    :get-indicators oscillators/get-oscillator-indicators
    :calculate-indicator oscillators/calculate-oscillator-indicator}
   {:id :volatility
    :get-indicators volatility/get-volatility-indicators
    :calculate-indicator volatility/calculate-volatility-indicator}
   {:id :flow
    :get-indicators flow/get-flow-indicators
    :calculate-indicator flow/calculate-flow-indicator}
   {:id :price
    :get-indicators price/get-price-indicators
    :calculate-indicator price/calculate-price-indicator}])

(defn- valid-family-descriptor?
  [{:keys [id get-indicators calculate-indicator]}]
  (and (keyword? id)
       (fn? get-indicators)
       (fn? calculate-indicator)))

(defn compose-domain-families
  "Return deterministic family descriptors for registry orchestration.

  `extra-families` is optional and is interpreted as additive descriptors that
  can override built-ins by matching `:id`."
  ([]
   built-in-families)
  ([extra-families]
   (let [extras (->> (or extra-families [])
                     (filter valid-family-descriptor?)
                     vec)
         built-in-by-id (into {} (map (juxt :id identity) built-in-families))
         extra-by-id (into {} (map (juxt :id identity) extras))
         order (distinct (concat (map :id built-in-families)
                                 (map :id extras)))]
     (mapv (fn [id]
             (or (get extra-by-id id)
                 (get built-in-by-id id)))
           order))))

(defn- dedupe-indicators
  [definitions]
  (loop [remaining definitions
         seen #{}
         out []]
    (if-let [indicator (first remaining)]
      (let [indicator-id (:id indicator)]
        (if (contains? seen indicator-id)
          (recur (rest remaining) seen out)
          (recur (rest remaining) (conj seen indicator-id) (conj out indicator))))
      (vec out))))

(defn get-domain-indicators
  ([]
   (get-domain-indicators nil))
  ([extra-families]
   (->> (compose-domain-families extra-families)
        (mapcat (fn [{:keys [get-indicators]}]
                  (get-indicators)))
        dedupe-indicators)))

(defn calculate-domain-indicator
  ([indicator-type data params]
   (calculate-domain-indicator indicator-type data params nil))
  ([indicator-type data params extra-families]
   (let [config (or params {})]
     (some (fn [{:keys [calculate-indicator]}]
             (calculate-indicator indicator-type data config))
           (compose-domain-families extra-families)))))
