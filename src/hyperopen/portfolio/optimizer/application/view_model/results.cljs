(ns hyperopen.portfolio.optimizer.application.view-model.results
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.instrument-labels :as instrument-labels]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.ids :as ids]))

(def ^:private vault-instrument-prefix
  ids/vault-instrument-prefix)

(def ^:private non-blank-text coercion/non-blank-text)

(def ^:private vault-address-from-instrument-id
  ids/vault-address-from-instrument-id)

(defn- raw-vault-label?
  [instrument-id label]
  (let [address (vault-address-from-instrument-id instrument-id)
        label* (some-> (non-blank-text label) str/lower-case)]
    (boolean
     (and address
          (or (nil? label*)
              (= label* address)
              (= label* (str vault-instrument-prefix address)))))))

(defn- display-label
  [instrument-id label]
  (when-not (raw-vault-label? instrument-id label)
    (non-blank-text label)))

(defn- label-map-value
  [labels-by-instrument instrument-id]
  (or (get labels-by-instrument instrument-id)
      (when (some? instrument-id)
        (get labels-by-instrument (keyword instrument-id)))))

(defn- normalize-label-map
  [labels-by-instrument]
  (into {}
        (keep (fn [[instrument-id label]]
                (when (some? instrument-id)
                  [(ids/instrument-id-key instrument-id) label])))
        (or labels-by-instrument {})))

(defn- universe-by-id
  [universe]
  (into {}
        (mapcat (fn [instrument]
                  (map (fn [instrument-id]
                         [instrument-id instrument])
                       (ids/instrument-id-candidates instrument))))
        (or universe [])))

(def ^:private icon-market-keys
  [:instrument-id
   :market-type
   :coin
   :symbol
   :base
   :quote
   :dex
   :hip3?
   :hip3-eligible?
   :optimizer-history/instrument-id
   :optimizer-history/display-symbol
   :optimizer-history/instrument-kind])

(defn- icon-market
  [instrument]
  (when (map? instrument)
    (select-keys instrument icon-market-keys)))

(defn instrument-label
  [labels-by-instrument instrument-id]
  (if (vault-address-from-instrument-id instrument-id)
    (or (display-label instrument-id
                       (label-map-value labels-by-instrument instrument-id))
        (str instrument-id))
    (str instrument-id)))

(defn- enriched-overlay-point
  [labels-by-instrument draft-by-id point]
  (let [instrument-id (:instrument-id point)
        label (label-map-value labels-by-instrument instrument-id)
        draft-instrument (get draft-by-id instrument-id)]
    (cond-> point
      (and (vault-address-from-instrument-id instrument-id)
           (display-label instrument-id label))
      (assoc :label (display-label instrument-id label))
      draft-instrument
      (assoc :icon-market (icon-market draft-instrument)))))

(defn- enrich-frontier-overlays
  [result labels-by-instrument draft-by-id]
  (update result
          :frontier-overlays
          (fn [overlays]
            (if (map? overlays)
              (into {}
                    (map (fn [[mode points]]
                           [mode (mapv (partial enriched-overlay-point
                                                 labels-by-instrument
                                                 draft-by-id)
                                       points)]))
                    overlays)
              overlays))))

(defn enrich-result-labels
  [result draft]
  (if (map? result)
    (let [instrument-ids (vec (:instrument-ids result))
          result-labels (normalize-label-map (:labels-by-instrument result))
          draft-by-id (universe-by-id (:universe draft))
          draft-labels (when (seq (:universe draft))
                         (instrument-labels/labels-by-instrument (:universe draft)
                                                                 instrument-ids))
          merged-labels (into result-labels
                              (keep (fn [instrument-id]
                                      (let [existing-label (label-map-value
                                                            result-labels
                                                            instrument-id)
                                            draft-label (display-label
                                                         instrument-id
                                                         (label-map-value
                                                          draft-labels
                                                          instrument-id))]
                                        (when (and draft-label
                                                   (or (nil? (non-blank-text existing-label))
                                                       (raw-vault-label? instrument-id existing-label)))
                                          [instrument-id draft-label]))))
                              instrument-ids)]
      (-> result
          (assoc :labels-by-instrument merged-labels)
          (enrich-frontier-overlays merged-labels draft-by-id)))
    result))
