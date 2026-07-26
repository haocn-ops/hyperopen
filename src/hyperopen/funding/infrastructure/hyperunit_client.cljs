(ns hyperopen.funding.infrastructure.hyperunit-client
  (:require [clojure.string :as str]
            [hyperopen.api.gateway.funding-hyperunit :as funding-hyperunit-gateway]))

(defn- non-blank-text
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      text)))

(defn with-hyperunit-base-url-fallbacks!
  [{:keys [base-url
           base-urls
           request-fn
           error-message]
    :or {error-message "HyperUnit request failed."}}]
  (let [candidates (vec (distinct
                         (keep non-blank-text
                               (concat [(non-blank-text base-url)]
                                       (or base-urls [])))))]
    (letfn [(attempt! [remaining last-error]
              (if-let [candidate (first remaining)]
                (let [request-result (try
                                       (request-fn candidate)
                                       (catch :default err
                                         (js/Promise.reject err)))]
                  (-> request-result
                      (.catch (fn [err]
                                (attempt! (rest remaining)
                                          (or err last-error))))))
                (js/Promise.reject
                 (or last-error
                     (js/Error. error-message)))))]
      (attempt! candidates nil))))

(defn request-hyperunit-operations!
  [{:keys [base-url base-urls address]}]
  (with-hyperunit-base-url-fallbacks!
   {:base-url base-url
    :base-urls base-urls
    :error-message "Unable to load HyperUnit operations."
    :request-fn (fn [candidate-base-url]
                  (funding-hyperunit-gateway/request-hyperunit-operations!
                   {:hyperunit-base-url candidate-base-url
                    :fetch-fn js/fetch}
                   {:address address}))}))

(defn request-hyperunit-estimate-fees!
  [{:keys [base-url base-urls]}]
  (with-hyperunit-base-url-fallbacks!
   {:base-url base-url
    :base-urls base-urls
    :error-message "Unable to load HyperUnit fee estimates."
    :request-fn (fn [candidate-base-url]
                  (funding-hyperunit-gateway/request-hyperunit-estimate-fees!
                   {:hyperunit-base-url candidate-base-url
                    :fetch-fn js/fetch}
                   {}))}))

(defn request-hyperunit-withdrawal-queue!
  [{:keys [base-url base-urls]}]
  (with-hyperunit-base-url-fallbacks!
   {:base-url base-url
    :base-urls base-urls
    :error-message "Unable to load HyperUnit withdrawal queue."
    :request-fn (fn [candidate-base-url]
                  (funding-hyperunit-gateway/request-hyperunit-withdrawal-queue!
                   {:hyperunit-base-url candidate-base-url
                    :fetch-fn js/fetch}
                   {}))}))

(defn request-hyperunit-generate-address!
  [{:keys [base-url
           base-urls
           source-chain
           destination-chain
           asset
           destination-address]}]
  (with-hyperunit-base-url-fallbacks!
   {:base-url base-url
    :base-urls base-urls
    :error-message "Unable to generate HyperUnit address."
    :request-fn (fn [candidate-base-url]
                  (funding-hyperunit-gateway/request-hyperunit-generate-address!
                   {:hyperunit-base-url candidate-base-url
                    :fetch-fn js/fetch}
                   {:source-chain source-chain
                    :destination-chain destination-chain
                    :asset asset
                    :destination-address destination-address}))}))
