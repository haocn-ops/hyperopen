(ns hyperopen.portfolio.optimizer.application.history-loader.api-v2.bundle
  (:require [hyperopen.portfolio.optimizer.application.history-loader.api-v2.codec :as codec]
            [hyperopen.portfolio.optimizer.application.history-loader.instruments :as instruments]))

(def ^:private history-keyed-fields
  [:series_by_instrument
   :series-by-instrument
   "series_by_instrument"
   "series-by-instrument"
   :aligned_returns_by_instrument
   :aligned-returns-by-instrument
   "aligned_returns_by_instrument"
   "aligned-returns-by-instrument"])

(defn- normalize-point
  [point]
  (let [point* (codec/normalize-api-map point)
        time-ms (codec/parse-ms (:time-ms point*))
        close (codec/parse-number (:close point*))
        index-value (codec/parse-number (:index-value point*))
        return-value (codec/parse-number (:return point*))]
    (cond-> point*
      time-ms (assoc :time-ms time-ms)
      close (assoc :close close)
      index-value (assoc :index-value index-value)
      (or (codec/finite-number? return-value)
          (contains? point* :return))
      (assoc :return return-value)
      (:component point*) (update :component codec/keyword-like))))

(defn- normalize-funding
  [funding]
  (when (map? funding)
    (let [funding* (codec/normalize-api-map funding)]
      (cond-> funding*
        (:status funding*) (update :status codec/keyword-like)
        (:annualized-carry funding*) (update :annualized-carry codec/parse-number)
        (:observation-count funding*) (update :observation-count codec/parse-ms)))))

(defn- normalize-quality
  [quality]
  (when (map? quality)
    (let [quality* (codec/normalize-api-map quality)]
      (cond-> quality*
        (:status quality*) (update :status codec/keyword-like)
        (:warnings quality*) (update :warnings codec/normalize-warnings)))))

(defn- normalize-series
  [local-id series]
  (let [series* (codec/normalize-api-map series)]
    (cond-> series*
      true (assoc :local-instrument-id local-id)
      (:lineage-kind series*) (update :lineage-kind codec/keyword-like)
      (:series-kind series*) (update :series-kind codec/keyword-like)
      (:points series*) (update :points #(mapv normalize-point %))
      (:funding series*) (update :funding normalize-funding)
      (:quality series*) (update :quality normalize-quality)
      (:proxy series*) (update :proxy codec/normalize-proxy)
      true (update :warnings (fn [warnings]
                               (mapv #(cond-> (codec/normalize-warning %)
                                        (not (:instrument-id %))
                                        (assoc :instrument-id local-id))
                                     (or warnings [])))))))

(defn- aligned-entry
  [entry]
  (let [entry* (codec/normalize-api-map entry)]
    (cond-> entry*
      (:returns entry*) (update :returns #(mapv codec/parse-number %)))))

(defn- local-id-by-backend-id
  [universe]
  (into {}
        (keep (fn [instrument]
                (let [local-id (instruments/normalize-instrument-id instrument)
                      backend-id (codec/non-blank-text
                                  (:optimizer-history/instrument-id instrument))]
                  (when (and local-id backend-id)
                    [backend-id local-id]))))
        (or universe [])))

(defn- canonical-response-id
  [local-id-by-backend raw-id entry]
  (or (get local-id-by-backend raw-id)
      (get local-id-by-backend (:instrument-id entry))
      raw-id))

(defn- normalize-series-by-instrument
  [raw-series universe]
  (let [local-id-by-backend (local-id-by-backend-id universe)]
    (into {}
          (map (fn [[raw-id series]]
                 (let [raw-id* (codec/keyed-map-id raw-id)
                       series* (normalize-series raw-id* series)
                       local-id (canonical-response-id local-id-by-backend
                                                       raw-id*
                                                       series*)]
                   [local-id
                    (assoc series* :local-instrument-id local-id)])))
          (or raw-series {}))))

(defn- normalize-aligned-returns-by-instrument
  [raw-aligned-returns universe]
  (let [local-id-by-backend (local-id-by-backend-id universe)]
    (into {}
          (map (fn [[raw-id entry]]
                 (let [raw-id* (codec/keyed-map-id raw-id)
                       entry* (aligned-entry entry)]
                   [(canonical-response-id local-id-by-backend raw-id* entry*)
                    entry*])))
          (or raw-aligned-returns {}))))

(defn- identity-ambiguous-warnings
  [universe]
  (into []
        (keep (fn [instrument]
                (let [local-id (instruments/normalize-instrument-id instrument)]
                  (when (and local-id
                             (not (codec/non-blank-text
                                   (:optimizer-history/instrument-id instrument))))
                    {:code :identity-ambiguous
                     :instrument-id local-id
                     :market-type (instruments/market-type instrument)}))))
        (or universe [])))

(defn normalize-history-body
  ([api-body]
   (normalize-history-body nil api-body))
  ([request api-body]
   (let [body (codec/normalize-api-map (apply dissoc api-body history-keyed-fields))
         raw-series (codec/keyed-map-field api-body
                                           "series_by_instrument"
                                           "series-by-instrument")
         raw-aligned-returns (codec/keyed-map-field api-body
                                                    "aligned_returns_by_instrument"
                                                    "aligned-returns-by-instrument")
         universe (:universe request)
         series-by-instrument (normalize-series-by-instrument raw-series
                                                              universe)
         aligned-returns-by-instrument (normalize-aligned-returns-by-instrument
                                        raw-aligned-returns
                                        universe)]
     {:contract-version (:contract-version body)
      :request-id (:request-id body)
      :dataset-version (:dataset-version body)
      :error (:error body)
      :message (:message body)
      :status (or (codec/keyword-like (:status body)) :ok)
      :common-calendar (mapv codec/parse-ms (or (:common-calendar body) []))
      :return-calendar (mapv codec/parse-ms (or (:return-calendar body) []))
      :aligned-returns-by-instrument aligned-returns-by-instrument
      :series-by-instrument series-by-instrument
      :warnings (codec/normalize-warnings (:warnings body))})))

(defn normalize-history-bundle
  [request api-body]
  (update (normalize-history-body request api-body)
          :warnings
          #(vec (concat (or % [])
                        (identity-ambiguous-warnings (:universe request))))))
