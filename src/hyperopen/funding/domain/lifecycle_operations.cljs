(ns hyperopen.funding.domain.lifecycle-operations
  (:require [clojure.string :as str]))

(defn- non-blank-text
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      text)))

(defn- canonical-token
  [value]
  (some-> value str str/trim str/lower-case))

(defn- token->keyword
  [value]
  (let [text (some-> value
                     canonical-token
                     (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
                     (str/replace #"[^a-z0-9]+" "-")
                     (str/replace #"(^-+)|(-+$)" ""))]
    (when (seq text)
      (keyword text))))

(defn- timestamp->ms
  [value]
  (let [text (non-blank-text value)
        parsed (if text (js/Date.parse text) js/NaN)]
    (when (and (number? parsed)
               (not (js/isNaN parsed)))
      (js/Math.floor parsed))))

(defn op-sort-ms
  [op]
  (or (timestamp->ms (:state-updated-at op))
      (timestamp->ms (:state-started-at op))
      (timestamp->ms (:op-created-at op))
      0))

(defn select-operation
  [operations {:keys [asset-key protocol-address source-address destination-address]}]
  (let [asset-token (some-> asset-key name canonical-token)
        protocol-token (canonical-token protocol-address)
        source-token (canonical-token source-address)
        destination-token (canonical-token destination-address)
        for-asset (->> (or operations [])
                       (filter (fn [op]
                                 (= asset-token
                                    (canonical-token (:asset op)))))
                       vec)
        protocol-matches (if (seq protocol-token)
                           (filterv #(= protocol-token
                                        (canonical-token (:protocol-address %)))
                                    for-asset)
                           [])
        source-matches (if (seq source-token)
                         (filterv #(= source-token
                                      (canonical-token (:source-address %)))
                                  for-asset)
                         [])
        destination-matches (if (seq destination-token)
                              (filterv #(= destination-token
                                           (canonical-token (:destination-address %)))
                                       for-asset)
                              [])
        source-and-destination-matches (if (and (seq source-token)
                                                (seq destination-token))
                                         (filterv #(and (= source-token
                                                         (canonical-token (:source-address %)))
                                                        (= destination-token
                                                           (canonical-token (:destination-address %))))
                                                  for-asset)
                                         [])
        candidates (cond
                     (seq protocol-matches) protocol-matches
                     (seq source-and-destination-matches) source-and-destination-matches
                     (seq source-matches) source-matches
                     (seq destination-matches) destination-matches
                     :else for-asset)]
    (last (sort-by op-sort-ms candidates))))

(defn lifecycle-next-delay-ms
  [{:keys [default-delay-ms
           min-delay-ms
           max-delay-ms]}
   now-ms
   lifecycle]
  (let [state-next-at (:state-next-at lifecycle)
        next-delay (when (number? state-next-at)
                     (max 0 (- state-next-at now-ms)))
        base-delay (if (number? next-delay)
                     next-delay
                     default-delay-ms)]
    (-> base-delay
        (max min-delay-ms)
        (min max-delay-ms)
        js/Math.floor)))

(defn operation->lifecycle
  [normalize-hyperunit-lifecycle operation direction asset-key now-ms]
  (normalize-hyperunit-lifecycle
   {:direction direction
    :asset-key asset-key
    :operation-id (:operation-id operation)
    :state (or (:state-key operation)
               (token->keyword (:state operation)))
    :status (token->keyword (:status operation))
    :source-tx-confirmations (:source-tx-confirmations operation)
    :destination-tx-confirmations (:destination-tx-confirmations operation)
    :position-in-withdraw-queue (:position-in-withdraw-queue operation)
    :destination-tx-hash (:destination-tx-hash operation)
    :state-next-at (timestamp->ms (:state-next-attempt-at operation))
    :last-updated-ms now-ms
    :error (:error operation)}))

(defn awaiting-lifecycle
  [normalize-hyperunit-lifecycle direction asset-key now-ms]
  (normalize-hyperunit-lifecycle
   {:direction direction
    :asset-key asset-key
    :status :pending
    :state (if (= direction :withdraw)
             :awaiting-hyperliquid-send
             :awaiting-source-transfer)
    :last-updated-ms now-ms
    :error nil}))

(defn awaiting-deposit-lifecycle
  [normalize-hyperunit-lifecycle asset-key now-ms]
  (awaiting-lifecycle normalize-hyperunit-lifecycle :deposit asset-key now-ms))

(defn awaiting-withdraw-lifecycle
  [normalize-hyperunit-lifecycle asset-key now-ms]
  (awaiting-lifecycle normalize-hyperunit-lifecycle :withdraw asset-key now-ms))
