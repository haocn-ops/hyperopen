(ns hyperopen.portfolio.optimizer.infrastructure.history-api-v2-client
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.history-loader.api-v2 :as api-v2]
            [hyperopen.portfolio.optimizer.application.history-loader.request-plan :as request-plan]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def default-base-url
  "https://price-history.hyperopen.xyz")

(defn- normalize-base-url
  [base-url]
  (str/replace (or (coercion/non-blank-text base-url)
                   default-base-url)
               #"/+$"
               ""))

(defn- request-id-value
  [request-id]
  (cond
    (fn? request-id) (request-id)
    (some? request-id) request-id
    :else nil))

(defn- keyword-name
  [value fallback]
  (cond
    (keyword? value) (name value)
    (string? value) value
    :else fallback))

(defn- proxy-policy-wire
  [proxy-policy]
  (str/replace (keyword-name proxy-policy "approved-proxy-allowed") #"-" "_"))

(defn- optimizer-history-request-id
  "The request identity is ALWAYS the canonical backend instrument id from
  discovery (e.g. hl:hip3:xyz:NOW). The backend selects the lineage — native,
  approved proxy, or stitched — server-side from the top-level `proxy_policy`, so
  the frontend must NOT substitute a proxy id (external:tiingo:*) here.

  Sending the proxy id makes the backend treat it as a bare external identity
  whose standalone cache is a tiny recent slice, which collapses the shared
  calendar to ~0 usable observations across the basket (the \"not enough usable
  candle observations / 0 usable shared return observations\" failure). Per
  API_CONTRACT.md, `instrument_id` is the canonical backend id from discovery and
  \"the only accepted request identity\"; `client_instrument_id` is the row key."
  [instrument]
  (coercion/non-blank-text (:optimizer-history/instrument-id instrument)))

(defn- interval-wire
  [interval]
  (keyword-name interval "1d"))

(defn- response-json!
  [response]
  (if (and response (fn? (.-json response)))
    (.json response)
    (js/Promise.resolve #js {})))

(defn- parsed-response!
  [normalizer response]
  (-> (response-json! response)
      (.then (fn [payload]
               (let [body (normalizer (js->clj payload))]
                 {:response response
                  :body body})))))

(defn- error-from-response
  [response body]
  (let [status (or (some-> response .-status) 0)
        message (or (:message body)
                    (:error body)
                    (str "Optimizer history API failed with HTTP " status))
        err (js/Error. message)]
    (aset err "status" status)
    (aset err "payload" (clj->js body))
    (aset err "requestId" (:request-id body))
    (aset err "contractVersion" (:contract-version body))
    err))

(defn- validate-contract!
  [body]
  (if (= api-v2/contract-version (:contract-version body))
    body
    (let [err (js/Error. "Unexpected optimizer history API contract version.")]
      (aset err "status" :invalid-contract)
      (aset err "requestId" (:request-id body))
      (aset err "contractVersion" (:contract-version body))
      (throw err))))

(defn- request-json!
  [fetch-fn url init normalizer]
  (let [fetch-fn* (or fetch-fn js/fetch)]
    (-> (fetch-fn* url (clj->js init))
        (.then (partial parsed-response! normalizer))
        (.then (fn [{:keys [response body]}]
                 (if (and response
                          (some? (.-ok response))
                          (false? (.-ok response)))
                   (js/Promise.reject (error-from-response response body))
                   (validate-contract! body)))))))

(defn request-instruments!
  [{:keys [fetch-fn base-url request-id]}]
  (let [rid (request-id-value request-id)
        headers (cond-> {}
                  rid (assoc "x-request-id" rid))]
    (request-json! fetch-fn
                   (str (normalize-base-url base-url)
                        "/v1/optimizer/instruments")
                   {:method "GET"
                    :headers headers}
                   api-v2/normalize-api-map)))

(defn- api-instrument-row
  [instrument]
  (let [local-id (coercion/non-blank-text (:instrument-id instrument))
        request-id (optimizer-history-request-id instrument)]
    (when (and local-id request-id)
      {:client_instrument_id local-id
       :instrument_id request-id})))

(def max-instruments-per-history-request
  "The optimizer history API rejects history-bundle requests with more
  than 100 instruments, so larger universes are fetched in chunks and
  the chunk bodies merged."
  100)

(defn- history-body-base
  [{:keys [proxy-policy include-aligned-returns?]} request]
  {:lookback_days (or (:bars request) request-plan/default-bars)
   :interval (interval-wire (:interval request))
   :proxy_policy (proxy-policy-wire proxy-policy)
   :include_aligned_returns (true? include-aligned-returns?)})

(defn- instrument-rows
  [request]
  (vec (keep api-instrument-row (:universe request))))

(defn- instrument-row-chunks
  [rows]
  (if (seq rows)
    (mapv vec (partition-all max-instruments-per-history-request rows))
    [[]]))

(defn- intersect-preserving-order
  [xs ys]
  (let [keep? (set ys)]
    (filterv keep? xs)))

(defn- merged-calendar
  [calendars]
  (reduce intersect-preserving-order
          (vec (first calendars))
          (rest calendars)))

(defn- merged-status
  [statuses]
  (or (some #(when (not= :ok %) %) statuses)
      :ok))

(defn- merge-history-bodies
  [bodies]
  (if (<= (count bodies) 1)
    (first bodies)
    (let [head (first bodies)]
      {:contract-version (:contract-version head)
       :request-id (:request-id head)
       :dataset-version (:dataset-version head)
       :error (some :error bodies)
       :message (some :message bodies)
       :status (merged-status (map :status bodies))
       :common-calendar (merged-calendar (map :common-calendar bodies))
       :return-calendar (merged-calendar (map :return-calendar bodies))
       :aligned-returns-by-instrument (into {}
                                            (map :aligned-returns-by-instrument)
                                            bodies)
       :series-by-instrument (into {}
                                   (map :series-by-instrument)
                                   bodies)
       :warnings (vec (mapcat :warnings bodies))})))

(defn- request-history-bundle-chunk!
  [{:keys [fetch-fn base-url request-id]} body-base instruments]
  (let [rid (request-id-value request-id)]
    (request-json! fetch-fn
                   (str (normalize-base-url base-url)
                        "/v1/optimizer/history-bundle")
                   {:method "POST"
                    :headers (cond-> {"content-type" "application/json"}
                               rid (assoc "x-request-id" rid))
                    :body (js/JSON.stringify
                           (clj->js (assoc body-base :instruments instruments)))}
                   api-v2/normalize-history-body)))

(defn- report-chunk-progress!
  [on-chunk-progress progress total-chunks total-instruments]
  (when (fn? on-chunk-progress)
    (on-chunk-progress {:completed (:completed progress)
                        :total total-chunks
                        :loaded-count (:loaded-count progress)
                        :requested-count total-instruments})))

(defn request-history-bundle!
  [{:keys [on-chunk-progress] :as deps} request]
  (let [body-base (history-body-base deps request)
        rows (instrument-rows request)
        chunks (instrument-row-chunks rows)
        total-chunks (count chunks)
        total-instruments (count rows)
        progress-state (atom {:completed 0 :loaded-count 0})]
    (-> (js/Promise.all
         (to-array (map (fn [chunk]
                          (-> (request-history-bundle-chunk! deps body-base chunk)
                              (.then (fn [body]
                                       (report-chunk-progress!
                                        on-chunk-progress
                                        (swap! progress-state
                                               #(-> %
                                                    (update :completed inc)
                                                    (update :loaded-count + (count chunk))))
                                        total-chunks
                                        total-instruments)
                                       body))))
                        chunks)))
        (.then (fn [bodies]
                 (merge-history-bodies (vec bodies)))))))
