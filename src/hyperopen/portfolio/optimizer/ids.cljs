(ns hyperopen.portfolio.optimizer.ids
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def vault-instrument-prefix
  "vault:")

(defn instrument-id-key
  [key]
  (cond
    (keyword? key) (subs (str key) 1)
    (string? key) key
    :else (str key)))

(defn- id-text
  [value]
  (coercion/non-blank-text
   (if (keyword? value)
     (instrument-id-key value)
     value)))

(defn normalize-vault-address
  [value]
  (some-> value id-text str/lower-case))

(defn vault-instrument-id
  [vault-address]
  (when-let [vault-address* (normalize-vault-address vault-address)]
    (str vault-instrument-prefix vault-address*)))

(defn vault-address-from-instrument-id
  [value]
  (let [text (id-text value)
        lower (some-> text str/lower-case)]
    (when (and (seq lower)
               (str/starts-with? lower vault-instrument-prefix))
      (normalize-vault-address (subs text (count vault-instrument-prefix))))))

(defn vault-address-from-value
  [value]
  (vault-address-from-instrument-id value))

(defn vault-instrument-id?
  [value]
  (boolean (vault-address-from-instrument-id value)))

(defn normalize-market-type
  [value]
  (coercion/normalize-keyword-like value))

(defn normalize-instrument-id
  [value]
  (if (map? value)
    (or (coercion/non-blank-text (:instrument-id value))
        (coercion/non-blank-text (:coin value))
        (coercion/non-blank-text (:asset value)))
    (id-text value)))

(defn vault-instrument?
  [value]
  (boolean
   (if (map? value)
     (or (= :vault (normalize-market-type (:market-type value)))
         (vault-address-from-value (:instrument-id value))
         (vault-address-from-value (:coin value))
         (normalize-vault-address (:vault-address value)))
     (vault-address-from-value value))))

(defn- base-symbol-from-value
  [value]
  (let [text (id-text value)
        scoped (when text
                 (last (str/split text #":")))
        base (cond
               (and scoped (str/includes? scoped "/"))
               (first (str/split scoped #"/" 2))

               (and scoped (str/includes? scoped "-"))
               (first (str/split scoped #"-" 2))

               :else scoped)]
    (coercion/non-blank-text base)))

(defn- market-prefixed-instrument-id
  [instrument]
  (let [market-type (normalize-market-type (or (:market-type instrument)
                                               (:instrument-type instrument)))
        base (or (base-symbol-from-value (:coin instrument))
                 (base-symbol-from-value (:base instrument))
                 (base-symbol-from-value (:display-symbol instrument))
                 (base-symbol-from-value (:symbol instrument))
                 (base-symbol-from-value (:instrument-id instrument)))]
    (when base
      (case market-type
        :perp (str "perp:" base)
        :spot (str "spot:" base)
        nil))))

(defn instrument-id-candidates
  [value]
  (if (map? value)
    (vec (distinct
          (keep id-text
                [(normalize-instrument-id value)
                 (:key value)
                 (:local-instrument-id value)
                 (:optimizer-history/local-instrument-id value)
                 (:optimizer-history/instrument-id value)
                 (market-prefixed-instrument-id value)])))
    (vec (keep id-text [value]))))
