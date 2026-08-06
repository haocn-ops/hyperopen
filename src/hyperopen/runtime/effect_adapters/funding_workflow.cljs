(ns hyperopen.runtime.effect-adapters.funding-workflow
  (:require [nexus.registry :as nxr]
            [hyperopen.api.default :as api]
            [hyperopen.funding.effects :as funding-effects]
            [hyperopen.platform :as platform]
            [hyperopen.runtime.effect-adapters.common :as common]))

(defn api-fetch-hyperunit-fee-estimate-effect
  [_ store]
  (funding-effects/api-fetch-hyperunit-fee-estimate!
   {:store store
    :request-hyperunit-estimate-fees! api/request-hyperunit-estimate-fees!
    :now-ms-fn platform/now-ms
    :runtime-error-message common/runtime-error-message}))

(defn api-fetch-hyperliquid-legal-check-effect
  [_ store]
  (funding-effects/api-fetch-hyperliquid-legal-check!
   {:store store
    :request-hyperliquid-legal-check! api/request-hyperliquid-legal-check!
    :now-ms-fn platform/now-ms}))

(defn api-fetch-hyperunit-withdrawal-queue-effect
  [_ store]
  (funding-effects/api-fetch-hyperunit-withdrawal-queue!
   {:store store
    :request-hyperunit-withdrawal-queue! api/request-hyperunit-withdrawal-queue!
    :now-ms-fn platform/now-ms
    :runtime-error-message common/runtime-error-message}))

(defn- submit-effect
  [submit! store request show-toast! extra-deps]
  (submit! (merge {:store store
                   :request request
                   :dispatch! nxr/dispatch
                   :runtime-error-message common/runtime-error-message
                   :show-toast! show-toast!}
                  extra-deps)))

(defn api-submit-funding-transfer-effect
  ([_ store request]
   (api-submit-funding-transfer-effect nil store request {}))
  ([_ store request {:keys [show-toast!]
                     :or {show-toast! (fn [_store _kind _message] nil)}}]
   (submit-effect funding-effects/api-submit-funding-transfer!
                  store request show-toast!
                  {:exchange-response-error common/exchange-response-error})))

(defn api-submit-funding-send-effect
  ([_ store request]
   (api-submit-funding-send-effect nil store request {}))
  ([_ store request {:keys [show-toast!]
                     :or {show-toast! (fn [_store _kind _message] nil)}}]
   (submit-effect funding-effects/api-submit-funding-send!
                  store request show-toast!
                  {:exchange-response-error common/exchange-response-error})))

(defn api-submit-funding-repay-effect
  ([_ store request]
   (api-submit-funding-repay-effect nil store request {}))
  ([_ store request {:keys [show-toast!]
                     :or {show-toast! (fn [_store _kind _message] nil)}}]
   (submit-effect funding-effects/api-submit-funding-repay!
                  store request show-toast!
                  {:exchange-response-error common/exchange-response-error})))

(defn api-submit-funding-withdraw-effect
  ([_ store request]
   (api-submit-funding-withdraw-effect nil store request {}))
  ([_ store request {:keys [show-toast!]
                     :or {show-toast! (fn [_store _kind _message] nil)}}]
   (submit-effect funding-effects/api-submit-funding-withdraw!
                  store request show-toast!
                  {:request-hyperunit-operations! api/request-hyperunit-operations!
                   :request-hyperunit-withdrawal-queue! api/request-hyperunit-withdrawal-queue!
                   :set-timeout-fn platform/set-timeout!
                   :now-ms-fn platform/now-ms
                   :exchange-response-error common/exchange-response-error})))

(defn api-submit-funding-deposit-effect
  ([_ store request]
   (api-submit-funding-deposit-effect nil store request {}))
  ([_ store request {:keys [show-toast!]
                     :or {show-toast! (fn [_store _kind _message] nil)}}]
   (submit-effect funding-effects/api-submit-funding-deposit!
                  store request show-toast!
                  {:request-hyperunit-operations! api/request-hyperunit-operations!
                   :set-timeout-fn platform/set-timeout!
                   :now-ms-fn platform/now-ms})))
