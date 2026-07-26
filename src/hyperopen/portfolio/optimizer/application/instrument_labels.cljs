(ns hyperopen.portfolio.optimizer.application.instrument-labels
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.ids :as ids]))

(def ^:private non-blank-text coercion/non-blank-text)

(defn- universe-by-id
  [universe]
  (into {}
        (mapcat (fn [instrument]
                  (map (fn [instrument-id]
                         [instrument-id instrument])
                       (ids/instrument-id-candidates instrument))))
        universe))

(def ^:private vault-instrument-id? ids/vault-instrument-id?)

(defn- vault-instrument?
  [instrument instrument-id]
  (or (= :vault (ids/normalize-market-type (:market-type instrument)))
      (vault-instrument-id? instrument-id)))

(defn- spot-instrument?
  [instrument instrument-id]
  (or (= :spot (ids/normalize-market-type (:market-type instrument)))
      (str/starts-with? (str instrument-id) "spot:")))

(defn- base-from-symbol
  [symbol]
  (let [symbol* (non-blank-text symbol)]
    (cond
      (and symbol* (str/includes? symbol* "/"))
      (non-blank-text (first (str/split symbol* #"/" 2)))

      (and symbol* (str/includes? symbol* "-"))
      (non-blank-text (first (str/split symbol* #"-" 2)))

      :else nil)))

(defn- spot-display-label
  [instrument]
  (or (non-blank-text (:base instrument))
      (base-from-symbol (:symbol instrument))
      (non-blank-text (:optimizer-history/display-symbol instrument))))

(defn- label-for-instrument
  [instrument-id instrument]
  (cond
    (vault-instrument? instrument instrument-id)
    (or (non-blank-text (:name instrument))
        (non-blank-text (:symbol instrument))
        (non-blank-text (:coin instrument))
        instrument-id)

    (spot-instrument? instrument instrument-id)
    (or (spot-display-label instrument)
        (non-blank-text (:coin instrument))
        instrument-id)

    :else
    (or (non-blank-text (:coin instrument))
        instrument-id)))

(defn labels-by-instrument
  [universe instrument-ids]
  (let [by-id (universe-by-id universe)]
    (into {}
          (map (fn [instrument-id]
                 [instrument-id
                  (label-for-instrument instrument-id (get by-id instrument-id))]))
          instrument-ids)))
