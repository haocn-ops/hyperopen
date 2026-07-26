(ns hyperopen.portfolio.optimizer.application.history-loader.api-v2.discovery
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.history-loader.api-v2.codec :as codec]))

(defn- normalize-history-coverage
  [history]
  (when (map? history)
    (let [history* (codec/normalize-api-map history)]
      (cond-> history*
        (:status history*) (update :status codec/keyword-like)
        (:quality-status history*) (update :quality-status codec/keyword-like)
        (map? (:native-only history*))
        (update-in [:native-only :status] codec/keyword-like)
        (map? (:approved-proxy-allowed history*))
        (update-in [:approved-proxy-allowed :status] codec/keyword-like)
        (get-in history* [:approved-proxy-allowed :lineage-kind])
        (update-in [:approved-proxy-allowed :lineage-kind] codec/keyword-like)
        (get-in history* [:approved-proxy-allowed :series-kind])
        (update-in [:approved-proxy-allowed :series-kind] codec/keyword-like)))))

(defn- normalize-discovery-instrument
  [instrument]
  (let [instrument* (codec/normalize-api-map instrument)]
    (cond-> instrument*
      (:instrument-kind instrument*) (update :instrument-kind codec/keyword-like)
      (:proxy instrument*) (update :proxy codec/normalize-proxy)
      (:history instrument*) (update :history normalize-history-coverage))))

(defn normalize-discovery
  [api-body]
  (let [body (codec/normalize-api-map api-body)
        instruments (mapv normalize-discovery-instrument
                          (:instruments body))
        instruments-by-backend-id (into {}
                                        (keep (fn [{:keys [instrument-id] :as instrument}]
                                                (when (seq instrument-id)
                                                  [instrument-id instrument])))
                                        instruments)
        backend-id-by-local-id (into {}
                                     (keep (fn [{:keys [instrument-id aliases]}]
                                             (when-let [local-id (codec/non-blank-text
                                                                  (:hyperopen-market-key aliases))]
                                               [local-id instrument-id])))
                                     instruments)]
    {:status (or (codec/keyword-like (:status body)) :idle)
     :contract-version (:contract-version body)
     :request-id (:request-id body)
     :dataset-version (:dataset-version body)
     :loaded-at-ms (:loaded-at-ms body)
     :instruments-by-backend-id instruments-by-backend-id
     :backend-id-by-local-id backend-id-by-local-id
     :warnings (codec/normalize-warnings (:warnings body))
     :error (:error body)}))

(defn- strip-market-prefix
  [value]
  (let [text (codec/non-blank-text value)]
    (cond
      (nil? text)
      nil

      (str/starts-with? text "perp:")
      (subs text 5)

      (str/starts-with? text "hip3:")
      (subs text 5)

      :else
      text)))

(defn- namespaced-perp-parts
  [value]
  (let [text (strip-market-prefix value)]
    (when (and text
               (str/includes? text ":")
               (not (str/includes? text "/")))
      (let [[dex base] (str/split text #":" 2)
            dex* (codec/non-blank-text dex)
            base* (codec/non-blank-text base)]
        (when (and dex* base*)
          {:dex dex*
           :base base*})))))

(defn- hip3-alias-key
  [local-market]
  (let [parts (or (namespaced-perp-parts (:coin local-market))
                  (namespaced-perp-parts (:instrument-id local-market))
                  (namespaced-perp-parts (:key local-market)))
        dex (or (codec/non-blank-text (:dex local-market))
                (:dex parts))
        base (or (codec/non-blank-text (:base local-market))
                 (:base parts))]
    (when (and dex base)
      (str "hip3:" dex ":" base))))

(defn- discovery-local-id-candidates
  [local-market]
  (vec (distinct
        (keep codec/non-blank-text
              [(:key local-market)
               (:instrument-id local-market)
               (hip3-alias-key local-market)]))))

(defn with-discovery-metadata
  [local-market discovery]
  (let [backend-id (some #(get-in discovery [:backend-id-by-local-id %])
                         (discovery-local-id-candidates local-market))
        instrument (get-in discovery [:instruments-by-backend-id backend-id])
        history (:history instrument)]
    (cond-> local-market
      backend-id
      (assoc :optimizer-history/instrument-id backend-id)

      (:display-symbol instrument)
      (assoc :optimizer-history/display-symbol (:display-symbol instrument))

      (:instrument-kind instrument)
      (assoc :optimizer-history/instrument-kind (:instrument-kind instrument))

      (:status history)
      (assoc :optimizer-history/history-status (:status history))

      (:quality-status history)
      (assoc :optimizer-history/quality-status (:quality-status history))

      (:proxy instrument)
      (assoc :optimizer-history/proxy (:proxy instrument)))))
