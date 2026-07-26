(ns hyperopen.funding.application.submit-effects
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]))

(defn- spectate-mode-submit-error
  [store]
  (account-context/mutations-blocked-message @store))

(defn api-submit-funding-send!
  [{:keys [store
           request
           dispatch!
           submit-send-asset!
           exchange-response-error
           runtime-error-message
           show-toast!
           default-funding-modal-state
           set-funding-submit-error!
           close-funding-modal!
           refresh-after-funding-submit!]}]
  (let [spectate-mode-message (spectate-mode-submit-error store)
        address (get-in @store [:wallet :address])
        action (:action request)]
    (if (seq spectate-mode-message)
      (set-funding-submit-error! store show-toast! spectate-mode-message)
      (if (nil? address)
        (set-funding-submit-error! store
                                   show-toast!
                                   "Connect your wallet before sending tokens.")
        (-> (submit-send-asset! store address action)
            (.then (fn [resp]
                     (if (= "ok" (:status resp))
                       (do
                         (close-funding-modal! store default-funding-modal-state)
                         (show-toast! store :success "Send submitted.")
                         (refresh-after-funding-submit! store dispatch! address)
                         resp)
                       (let [error-text (str/trim (str (exchange-response-error resp)))
                             message (str "Send failed: "
                                          (if (seq error-text) error-text "Unknown exchange error"))]
                         (set-funding-submit-error! store show-toast! message)
                         resp))))
            (.catch (fn [err]
                      (let [error-text (str/trim (str (runtime-error-message err)))
                            message (str "Send failed: "
                                         (if (seq error-text) error-text "Unknown runtime error"))]
                        (set-funding-submit-error! store show-toast! message)))))))))

(defn api-submit-funding-transfer!
  [{:keys [store
           request
           dispatch!
           submit-usd-class-transfer!
           submit-send-asset!
           exchange-response-error
           runtime-error-message
           show-toast!
           default-funding-modal-state
           set-funding-submit-error!
           close-funding-modal!
           refresh-after-funding-submit!]}]
  (let [spectate-mode-message (spectate-mode-submit-error store)
        ;; The connected owner wallet signs, even when the funds source from a
        ;; selected subaccount (Hyperliquid subaccount actions are owner-signed).
        address (get-in @store [:wallet :address])
        ;; Balances that change belong to the active/effective account (which may be
        ;; the selected subaccount), so refresh that rather than only the owner wallet.
        refresh-address (or (account-context/effective-account-address @store) address)
        action (:action request)
        ;; Named-DEX transfers are `sendAsset` and must be signed with the
        ;; sendAsset signer; default spot <-> perps stays `usdClassTransfer`.
        submit-transfer! (if (= "sendAsset" (:type action))
                           submit-send-asset!
                           submit-usd-class-transfer!)]
    (if (seq spectate-mode-message)
      (set-funding-submit-error! store show-toast! spectate-mode-message)
      (if (nil? address)
      (set-funding-submit-error! store
                                 show-toast!
                                 "Connect your wallet before transferring funds.")
      (-> (submit-transfer! store address action)
          (.then (fn [resp]
                   (if (= "ok" (:status resp))
                     (do
                       (close-funding-modal! store default-funding-modal-state)
                       (show-toast! store :success "Transfer submitted.")
                       (refresh-after-funding-submit! store dispatch! refresh-address)
                       resp)
                     (let [error-text (str/trim (str (exchange-response-error resp)))
                           message (str "Transfer failed: "
                                        (if (seq error-text) error-text "Unknown exchange error"))]
                       (set-funding-submit-error! store show-toast! message)
                       resp))))
          (.catch (fn [err]
                    (let [error-text (str/trim (str (runtime-error-message err)))
                          message (str "Transfer failed: "
                                       (if (seq error-text) error-text "Unknown runtime error"))]
                      (set-funding-submit-error! store show-toast! message)))))))))

(defn api-submit-funding-withdraw!
  [{:keys [store
           request
           dispatch!
           submit-withdraw3!
           submit-send-asset!
           submit-hyperunit-send-asset-withdraw-request-fn
           request-hyperunit-operations!
           request-hyperunit-withdrawal-queue!
           set-timeout-fn
           now-ms-fn
           exchange-response-error
           runtime-error-message
           show-toast!
           default-funding-modal-state
           set-funding-submit-error!
           close-funding-modal!
           refresh-after-funding-submit!
           resolve-hyperunit-base-urls
           awaiting-withdraw-lifecycle
           start-hyperunit-withdraw-lifecycle-polling!]}]
  (let [spectate-mode-message (spectate-mode-submit-error store)
        address (get-in @store [:wallet :address])
        action (:action request)
        submit-withdraw! (case (:type action)
                           "withdraw3" submit-withdraw3!
                           "hyperunitSendAssetWithdraw"
                           (fn [store* owner-address action*]
                             (submit-hyperunit-send-asset-withdraw-request-fn
                              store*
                              owner-address
                              action*
                              submit-send-asset!))
                           (fn [_store _address _action]
                             (js/Promise.resolve {:status "err"
                                                  :error "Withdrawal action type is not supported."})))]
    (if (seq spectate-mode-message)
      (set-funding-submit-error! store show-toast! spectate-mode-message)
      (if (nil? address)
      (set-funding-submit-error! store
                                 show-toast!
                                 "Connect your wallet before withdrawing.")
      (-> (submit-withdraw! store address action)
          (.then (fn [resp]
                   (if (= "ok" (:status resp))
                     (if (true? (:keep-modal-open? resp))
                       (let [asset-key (some-> (:asset resp) str str/lower-case keyword)
                             base-urls (resolve-hyperunit-base-urls store)
                             base-url (first base-urls)
                             now-ms (if (fn? now-ms-fn)
                                      (now-ms-fn)
                                      (js/Date.now))]
                         (swap! store (fn [state]
                                        (-> state
                                            (assoc-in [:funding-ui :modal :submitting?] false)
                                            (assoc-in [:funding-ui :modal :error] nil)
                                            (assoc-in [:funding-ui :modal :withdraw-generated-address] (:protocol-address resp))
                                            (assoc-in [:funding-ui :modal :hyperunit-lifecycle]
                                                      (awaiting-withdraw-lifecycle asset-key now-ms)))))
                         (start-hyperunit-withdraw-lifecycle-polling!
                          {:store store
                           :wallet-address address
                           :asset-key asset-key
                           :protocol-address (:protocol-address resp)
                           :destination-address (:destination resp)
                           :base-url base-url
                           :base-urls base-urls
                           :request-hyperunit-operations! request-hyperunit-operations!
                           :request-hyperunit-withdrawal-queue! request-hyperunit-withdrawal-queue!
                           :set-timeout-fn set-timeout-fn
                           :now-ms-fn now-ms-fn
                           :runtime-error-message runtime-error-message
                           :on-terminal-lifecycle! (fn [_lifecycle]
                                                    (refresh-after-funding-submit! store
                                                                                   dispatch!
                                                                                   address))})
                         (show-toast! store :success "Withdrawal submitted.")
                         resp)
                       (do
                         (close-funding-modal! store default-funding-modal-state)
                         (show-toast! store :success "Withdrawal submitted.")
                         (refresh-after-funding-submit! store dispatch! address)
                         resp))
                     (let [error-text (str/trim (str (or (:error resp)
                                                        (exchange-response-error resp))))
                           message (str "Withdrawal failed: "
                                        (if (seq error-text) error-text "Unknown exchange error"))]
                       (set-funding-submit-error! store show-toast! message)
                       resp))))
          (.catch (fn [err]
                    (let [error-text (str/trim (str (runtime-error-message err)))
                          message (str "Withdrawal failed: "
                                       (if (seq error-text) error-text "Unknown runtime error"))]
                      (set-funding-submit-error! store show-toast! message)))))))))

(defn api-submit-funding-deposit!
  [{:keys [store
           request
           dispatch!
           submit-usdc-bridge2-deposit!
           submit-usdt-lifi-deposit!
           submit-usdh-across-deposit!
           submit-hyperunit-address-request!
           request-hyperunit-operations!
           set-timeout-fn
           now-ms-fn
           runtime-error-message
           show-toast!
           default-funding-modal-state
           set-funding-submit-error!
           close-funding-modal!
           refresh-after-funding-submit!
           resolve-hyperunit-base-urls
           awaiting-deposit-lifecycle
           start-hyperunit-deposit-lifecycle-polling!]}]
  (let [spectate-mode-message (spectate-mode-submit-error store)
        address (get-in @store [:wallet :address])
        action (:action request)
        submit-deposit! (case (:type action)
                          "bridge2Deposit" submit-usdc-bridge2-deposit!
                          "lifiUsdtToUsdcBridge2Deposit" submit-usdt-lifi-deposit!
                          "acrossUsdcToUsdhDeposit" submit-usdh-across-deposit!
                          "hyperunitGenerateDepositAddress" submit-hyperunit-address-request!
                          (fn [_store _address _action]
                            (js/Promise.resolve {:status "err"
                                                 :error "Deposit action type is not supported."})))]
    (if (seq spectate-mode-message)
      (set-funding-submit-error! store show-toast! spectate-mode-message)
      (if (nil? address)
      (set-funding-submit-error! store
                                 show-toast!
                                 "Connect your wallet before depositing.")
      (let [submit-result (try
                            (submit-deposit! store address action)
                            (catch :default err
                              (js/Promise.reject err)))
            submit-promise (if (fn? (some-> submit-result .-then))
                             submit-result
                             (js/Promise.resolve submit-result))]
        (-> submit-promise
            (.then (fn [resp]
                     (if (= "ok" (:status resp))
                       (if (true? (:keep-modal-open? resp))
                         (let [asset-key (some-> (:asset resp) str str/lower-case keyword)
                               base-urls (resolve-hyperunit-base-urls store)
                               base-url (first base-urls)
                               now-ms (if (fn? now-ms-fn)
                                        (now-ms-fn)
                                        (js/Date.now))]
                           (swap! store (fn [state]
                                          (-> state
                                              (assoc-in [:funding-ui :modal :submitting?] false)
                                              (assoc-in [:funding-ui :modal :error] nil)
                                              (assoc-in [:funding-ui :modal :deposit-generated-address] (:deposit-address resp))
                                              (assoc-in [:funding-ui :modal :deposit-generated-signatures] (:deposit-signatures resp))
                                              (assoc-in [:funding-ui :modal :deposit-generated-asset-key] asset-key)
                                              (assoc-in [:funding-ui :modal :hyperunit-lifecycle]
                                                        (awaiting-deposit-lifecycle asset-key now-ms)))))
                           (start-hyperunit-deposit-lifecycle-polling!
                            {:store store
                             :wallet-address address
                             :asset-key asset-key
                             :protocol-address (:deposit-address resp)
                             :base-url base-url
                             :base-urls base-urls
                             :request-hyperunit-operations! request-hyperunit-operations!
                             :set-timeout-fn set-timeout-fn
                             :now-ms-fn now-ms-fn
                             :on-terminal-lifecycle! (fn [_lifecycle]
                                                      (refresh-after-funding-submit! store
                                                                                     dispatch!
                                                                                     address))})
                           (show-toast! store
                                        :success
                                        (if (true? (:reused-address? resp))
                                          "Using existing deposit address."
                                          "Deposit address generated."))
                           resp)
                         (let [network (or (:network resp) "Arbitrum")]
                           (close-funding-modal! store default-funding-modal-state)
                           (show-toast! store :success (str "Deposit submitted on " network "."))
                           (refresh-after-funding-submit! store dispatch! address)
                           resp))
                       (let [error-text (str/trim (str (or (:error resp)
                                                          (runtime-error-message resp))))
                             message (str "Deposit failed: "
                                          (if (seq error-text) error-text "Unknown runtime error"))]
                         (set-funding-submit-error! store show-toast! message)
                         resp))))
            (.catch (fn [err]
                      (let [error-text (str/trim (str (runtime-error-message err)))
                            message (str "Deposit failed: "
                                         (if (seq error-text) error-text "Unknown runtime error"))]
                        (set-funding-submit-error! store show-toast! message))))))))))
