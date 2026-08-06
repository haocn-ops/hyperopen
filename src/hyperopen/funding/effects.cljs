(ns hyperopen.funding.effects
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]
            [hyperopen.api.default :as default-api]
            [hyperopen.api.trading :as trading-api]
            [hyperopen.funding.actions :as funding-actions]
            [hyperopen.funding.application.hyperunit-query :as hyperunit-query]
            [hyperopen.funding.application.modal-state :as modal-state]
            [hyperopen.funding.application.submit-effects :as submit-effects]
            [hyperopen.funding.domain.legal-check :as legal-check]
            [hyperopen.funding.domain.lifecycle :as funding-lifecycle]
            [hyperopen.funding.effects.common :as common]
            [hyperopen.funding.effects.hyperunit-runtime :as hyperunit-runtime]
            [hyperopen.funding.effects.transport-runtime :as transport-runtime]
            [hyperopen.ui.dialog-focus-runtime :as dialog-focus-runtime]))

(defn update-funding-submit-error
  [state error-text]
  (-> state
      (assoc-in [:funding-ui :modal :submitting?] false)
      (assoc-in [:funding-ui :modal :error] error-text)))

(defn set-funding-submit-error!
  [store show-toast! error-text]
  (swap! store update-funding-submit-error error-text)
  (show-toast! store :error error-text))

(defn close-funding-modal!
  [store default-funding-modal-state]
  (swap! store update-in [:funding-ui :modal]
         (fn [modal]
           (modal-state/closed-funding-modal-state default-funding-modal-state modal)))
  (dialog-focus-runtime/restore-remembered-focus!))

(defn refresh-after-funding-submit!
  [store dispatch! address]
  (when (and (fn? dispatch!)
             (string? address))
    (dispatch! store nil [[:actions/load-user-data address]])))

(defn api-fetch-hyperunit-fee-estimate!
  [{:keys [store
           request-hyperunit-estimate-fees!
           now-ms-fn
           runtime-error-message]
    :or {request-hyperunit-estimate-fees! hyperunit-runtime/request-hyperunit-estimate-fees!
         now-ms-fn (fn [] (js/Date.now))
         runtime-error-message common/fallback-runtime-error-message}}]
  (hyperunit-query/api-fetch-hyperunit-fee-estimate!
   {:modal-active-for-fee-estimate? hyperunit-runtime/modal-active-for-fee-estimate?
    :normalize-hyperunit-fee-estimate funding-lifecycle/normalize-hyperunit-fee-estimate
    :resolve-hyperunit-base-urls common/resolve-hyperunit-base-urls
    :prefetch-selected-hyperunit-deposit-address! hyperunit-runtime/prefetch-selected-hyperunit-deposit-address!
    :non-blank-text common/non-blank-text
    :fallback-runtime-error-message common/fallback-runtime-error-message}
   {:store store
    :request-hyperunit-estimate-fees! request-hyperunit-estimate-fees!
    :now-ms-fn now-ms-fn
    :runtime-error-message runtime-error-message}))

(defn api-fetch-hyperunit-withdrawal-queue!
  [{:keys [store
           request-hyperunit-withdrawal-queue!
           now-ms-fn
           runtime-error-message]
    :or {request-hyperunit-withdrawal-queue! hyperunit-runtime/request-hyperunit-withdrawal-queue!
         now-ms-fn (fn [] (js/Date.now))
         runtime-error-message common/fallback-runtime-error-message}}]
  (hyperunit-runtime/fetch-hyperunit-withdrawal-queue!
   {:store store
    :base-url (common/resolve-hyperunit-base-url store)
    :base-urls (common/resolve-hyperunit-base-urls store)
    :request-hyperunit-withdrawal-queue! request-hyperunit-withdrawal-queue!
    :now-ms-fn now-ms-fn
    :runtime-error-message runtime-error-message
    :transition-loading? true}))

(defn api-fetch-hyperliquid-legal-check!
  [{:keys [store
           request-hyperliquid-legal-check!
           now-ms-fn]
    :or {request-hyperliquid-legal-check! default-api/request-hyperliquid-legal-check!
         now-ms-fn (fn [] (js/Date.now))}}]
  (let [address (get-in @store [:wallet :address])
        loading-state (assoc (legal-check/default-legal-check-state)
                             :status :loading)]
    (swap! store assoc-in [:funding-ui :modal :legal-check] loading-state)
    (if-not (and (string? address) (seq address))
      (let [decision (assoc (legal-check/assess nil)
                            :checked-at-ms (now-ms-fn))]
        (swap! store (fn [state]
                       (-> state
                           (assoc-in [:funding-ui :modal :legal-check] decision)
                           (assoc-in [:funding-ui :modal :error] (:message decision)))))
        (js/Promise.resolve decision))
      (let [request-result (try
                             (request-hyperliquid-legal-check! address)
                             (catch :default _
                               nil))
            request-promise (if (some? (some-> request-result .-then))
                              request-result
                              (js/Promise.resolve request-result))]
        (-> request-promise
            (.then (fn [response]
                     (let [decision (assoc (legal-check/assess response)
                                           :checked-at-ms (now-ms-fn))]
                       (swap! store (fn [state]
                                      (-> state
                                          (assoc-in [:funding-ui :modal :legal-check] decision)
                                          (assoc-in [:funding-ui :modal :error]
                                                    (when (not= :allowed (:status decision))
                                                      (:message decision))))))
                       decision)))
            (.catch (fn [_]
                      (let [decision (assoc (legal-check/assess nil)
                                            :checked-at-ms (now-ms-fn))]
                        (swap! store (fn [state]
                                       (-> state
                                           (assoc-in [:funding-ui :modal :legal-check] decision)
                                           (assoc-in [:funding-ui :modal :error] (:message decision)))))
                        decision))))))))

(defn api-submit-funding-transfer!
  [{:keys [store
           request
           dispatch!
           submit-usd-class-transfer!
           submit-send-asset!
           exchange-response-error
           runtime-error-message
           show-toast!
           default-funding-modal-state]
    :or {submit-usd-class-transfer! trading-api/submit-usd-class-transfer!
         submit-send-asset! trading-api/submit-send-asset!
         exchange-response-error common/fallback-exchange-response-error
         runtime-error-message common/fallback-runtime-error-message
         show-toast! (fn [_store _kind _message] nil)
         default-funding-modal-state funding-actions/default-funding-modal-state}}]
  (submit-effects/api-submit-funding-transfer!
   {:store store
    :request request
    :dispatch! dispatch!
    :submit-usd-class-transfer! submit-usd-class-transfer!
    :submit-send-asset! submit-send-asset!
    :exchange-response-error exchange-response-error
    :runtime-error-message runtime-error-message
    :show-toast! show-toast!
    :default-funding-modal-state default-funding-modal-state
    :set-funding-submit-error! set-funding-submit-error!
    :close-funding-modal! close-funding-modal!
    :refresh-after-funding-submit! refresh-after-funding-submit!}))

(defn api-submit-funding-send!
  [{:keys [store
           request
           dispatch!
           submit-send-asset!
           exchange-response-error
           runtime-error-message
           show-toast!
           default-funding-modal-state]
    :or {submit-send-asset! trading-api/submit-send-asset!
         exchange-response-error common/fallback-exchange-response-error
         runtime-error-message common/fallback-runtime-error-message
         show-toast! (fn [_store _kind _message] nil)
         default-funding-modal-state funding-actions/default-funding-modal-state}}]
  (submit-effects/api-submit-funding-send!
   {:store store
    :request request
    :dispatch! dispatch!
    :submit-send-asset! submit-send-asset!
    :exchange-response-error exchange-response-error
    :runtime-error-message runtime-error-message
    :show-toast! show-toast!
    :default-funding-modal-state default-funding-modal-state
    :set-funding-submit-error! set-funding-submit-error!
    :close-funding-modal! close-funding-modal!
    :refresh-after-funding-submit! refresh-after-funding-submit!}))

(defn api-submit-funding-repay!
  "Submits a `borrowLend` repay L1 action. The action map is pre-built by the
   `submit-funding-repay` action; here we resolve the signing owner and, when a
   subaccount is selected, route the action to it via `:vault-address`."
  [{:keys [store
           request
           dispatch!
           submit-borrow-lend!
           exchange-response-error
           runtime-error-message
           show-toast!]
    :or {submit-borrow-lend! trading-api/submit-borrow-lend!
         exchange-response-error common/fallback-exchange-response-error
         runtime-error-message common/fallback-runtime-error-message
         show-toast! (fn [_store _kind _message] nil)}}]
  (let [spectate-mode-message (account-context/mutations-blocked-message @store)
        address (get-in @store [:wallet :address])
        vault-address (account-context/exchange-vault-address @store)
        refresh-address (or (account-context/effective-account-address @store) address)
        action (:action request)
        options (cond-> {}
                  vault-address (assoc :vault-address vault-address))]
    (cond
      (seq spectate-mode-message)
      (show-toast! store :error spectate-mode-message)

      (nil? address)
      (show-toast! store :error "Connect your wallet before repaying.")

      :else
      (-> (submit-borrow-lend! store address action options)
          (.then (fn [resp]
                   (if (= "ok" (:status resp))
                     (do
                       (show-toast! store :success "Repay submitted.")
                       (refresh-after-funding-submit! store dispatch! refresh-address)
                       resp)
                     (let [error-text (str/trim (str (exchange-response-error resp)))
                           message (str "Repay failed: "
                                        (if (seq error-text) error-text "Unknown exchange error"))]
                       (show-toast! store :error message)
                       resp))))
          (.catch (fn [err]
                    (let [error-text (str/trim (str (runtime-error-message err)))
                          message (str "Repay failed: "
                                       (if (seq error-text) error-text "Unknown runtime error"))]
                      (show-toast! store :error message))))))))

(defn api-submit-funding-withdraw!
  [{:keys [store
           request
           dispatch!
           submit-withdraw3!
           submit-send-asset!
           submit-hyperunit-send-asset-withdraw-request-fn
           request-hyperunit-operations!
           request-hyperunit-withdrawal-queue!
           request-hyperliquid-legal-check!
           set-timeout-fn
           now-ms-fn
           exchange-response-error
           runtime-error-message
           show-toast!
           default-funding-modal-state]
    :or {submit-withdraw3! trading-api/submit-withdraw3!
         submit-send-asset! trading-api/submit-send-asset!
         submit-hyperunit-send-asset-withdraw-request-fn transport-runtime/submit-hyperunit-send-asset-withdraw-request!
         request-hyperunit-operations! nil
         request-hyperunit-withdrawal-queue! nil
         request-hyperliquid-legal-check! default-api/request-hyperliquid-legal-check!
         set-timeout-fn nil
         now-ms-fn nil
         exchange-response-error common/fallback-exchange-response-error
         runtime-error-message common/fallback-runtime-error-message
         show-toast! (fn [_store _kind _message] nil)
         default-funding-modal-state funding-actions/default-funding-modal-state}}]
  (submit-effects/api-submit-funding-withdraw!
   {:store store
    :request request
    :dispatch! dispatch!
    :submit-withdraw3! submit-withdraw3!
    :submit-send-asset! submit-send-asset!
    :submit-hyperunit-send-asset-withdraw-request-fn submit-hyperunit-send-asset-withdraw-request-fn
    :request-hyperunit-operations! request-hyperunit-operations!
    :request-hyperunit-withdrawal-queue! request-hyperunit-withdrawal-queue!
    :request-hyperliquid-legal-check! request-hyperliquid-legal-check!
    :set-timeout-fn set-timeout-fn
    :now-ms-fn now-ms-fn
    :exchange-response-error exchange-response-error
    :runtime-error-message runtime-error-message
    :show-toast! show-toast!
    :default-funding-modal-state default-funding-modal-state
    :set-funding-submit-error! set-funding-submit-error!
    :close-funding-modal! close-funding-modal!
    :refresh-after-funding-submit! refresh-after-funding-submit!
    :resolve-hyperunit-base-urls common/resolve-hyperunit-base-urls
    :awaiting-withdraw-lifecycle hyperunit-runtime/awaiting-withdraw-lifecycle
    :start-hyperunit-withdraw-lifecycle-polling! hyperunit-runtime/start-hyperunit-withdraw-lifecycle-polling!}))

(defn api-submit-funding-deposit!
  [{:keys [store
           request
           dispatch!
           submit-usdc-bridge2-deposit!
           submit-usdt-lifi-deposit!
           submit-usdh-across-deposit!
           submit-hyperunit-address-request!
           request-hyperunit-operations!
           request-hyperliquid-legal-check!
           set-timeout-fn
           now-ms-fn
           runtime-error-message
           show-toast!
           default-funding-modal-state]
    :or {submit-usdc-bridge2-deposit! transport-runtime/submit-usdc-bridge2-deposit-tx!
         submit-usdt-lifi-deposit! transport-runtime/submit-usdt-lifi-bridge2-deposit-tx!
         submit-usdh-across-deposit! transport-runtime/submit-usdh-across-deposit-tx!
         submit-hyperunit-address-request! transport-runtime/submit-hyperunit-address-deposit-request!
         request-hyperunit-operations! nil
         request-hyperliquid-legal-check! default-api/request-hyperliquid-legal-check!
         set-timeout-fn nil
         now-ms-fn nil
         runtime-error-message common/fallback-runtime-error-message
         show-toast! (fn [_store _kind _message] nil)
         default-funding-modal-state funding-actions/default-funding-modal-state}}]
  (submit-effects/api-submit-funding-deposit!
   {:store store
    :request request
    :dispatch! dispatch!
    :submit-usdc-bridge2-deposit! submit-usdc-bridge2-deposit!
    :submit-usdt-lifi-deposit! submit-usdt-lifi-deposit!
    :submit-usdh-across-deposit! submit-usdh-across-deposit!
    :submit-hyperunit-address-request! submit-hyperunit-address-request!
    :request-hyperunit-operations! request-hyperunit-operations!
    :request-hyperliquid-legal-check! request-hyperliquid-legal-check!
    :set-timeout-fn set-timeout-fn
    :now-ms-fn now-ms-fn
    :runtime-error-message runtime-error-message
    :show-toast! show-toast!
    :default-funding-modal-state default-funding-modal-state
    :set-funding-submit-error! set-funding-submit-error!
    :close-funding-modal! close-funding-modal!
    :refresh-after-funding-submit! refresh-after-funding-submit!
    :resolve-hyperunit-base-urls common/resolve-hyperunit-base-urls
    :awaiting-deposit-lifecycle hyperunit-runtime/awaiting-deposit-lifecycle
    :start-hyperunit-deposit-lifecycle-polling! hyperunit-runtime/start-hyperunit-deposit-lifecycle-polling!}))
