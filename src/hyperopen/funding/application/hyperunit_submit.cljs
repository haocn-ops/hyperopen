(ns hyperopen.funding.application.hyperunit-submit
  (:require [clojure.string :as str]))

(defn submit-hyperunit-address-deposit-request!
  [{:keys [normalize-address
           non-blank-text
           resolve-hyperunit-base-urls
           request-existing-hyperunit-deposit-address!
           fetch-hyperunit-address-with-source-fallbacks!
           hyperunit-request-error-message]}
   store
   owner-address
   action]
  (let [destination-address (normalize-address owner-address)
        from-chain (some-> (:fromChain action) str str/trim str/lower-case)
        asset (some-> (:asset action) str str/trim str/lower-case)
        network-label (or (non-blank-text (:network action))
                          (some-> from-chain str/capitalize))
        base-urls (resolve-hyperunit-base-urls store)
        base-url (first base-urls)]
    (cond
      (nil? destination-address)
      (js/Promise.resolve {:status "err"
                           :error "Connect your wallet before generating a deposit address."})

      (not (seq from-chain))
      (js/Promise.resolve {:status "err"
                           :error "Deposit source chain is missing for this asset."})

      (not (seq asset))
      (js/Promise.resolve {:status "err"
                           :error "Deposit asset is missing for this request."})

      :else
      (let [to-success-response (fn [{:keys [address signatures]} reused-address?]
                                  {:status "ok"
                                   :keep-modal-open? true
                                   :network network-label
                                   :asset asset
                                   :from-chain from-chain
                                   :deposit-address address
                                   :deposit-signatures signatures
                                   :reused-address? (true? reused-address?)})
            generate-address! (fn []
                                (-> (fetch-hyperunit-address-with-source-fallbacks! base-url
                                                                                    base-urls
                                                                                    from-chain
                                                                                    "hyperliquid"
                                                                                    asset
                                                                                    destination-address)
                                    (.then (fn [{:keys [address signatures]}]
                                             (to-success-response {:address address
                                                                   :signatures signatures}
                                                                  false)))))
            lookup-existing-address! (fn []
                                       (request-existing-hyperunit-deposit-address!
                                        base-url
                                        base-urls
                                        destination-address
                                        from-chain
                                        asset))
            fallback-after-generate-error!
            (fn [generate-error]
              (-> (lookup-existing-address!)
                  (.then (fn [fallback-address]
                           (if (map? fallback-address)
                             (to-success-response fallback-address true)
                             (js/Promise.reject generate-error))))))]
        (-> (lookup-existing-address!)
            (.then (fn [existing-address]
                     (if (map? existing-address)
                       (js/Promise.resolve (to-success-response existing-address true))
                       (.catch (generate-address!) fallback-after-generate-error!))))
            (.catch (fn [err]
                      {:status "err"
                       :error (hyperunit-request-error-message err
                                                               {:asset asset
                                                                :source-chain from-chain})})))))))

(defn submit-hyperunit-send-asset-withdraw-request!
  [{:keys [normalize-address
           non-blank-text
           resolve-hyperunit-base-urls
           fetch-hyperunit-address-with-source-fallbacks!
           fallback-exchange-response-error
           hyperunit-request-error-message]}
   store
   owner-address
   action
   submit-send-asset!]
  (let [source-address (normalize-address owner-address)
        destination-address (non-blank-text (:destination action))
        destination-chain (some-> (:destinationChain action) str str/trim str/lower-case)
        asset (some-> (:asset action) str str/trim str/lower-case)
        token (non-blank-text (:token action))
        amount (some-> (:amount action) str str/trim)
        network-label (or (non-blank-text (:network action))
                          (some-> destination-chain str/capitalize))
        base-urls (resolve-hyperunit-base-urls store)
        base-url (first base-urls)]
    (cond
      (nil? source-address)
      (js/Promise.resolve {:status "err"
                           :error "Connect your wallet before withdrawing."})

      (not (seq destination-address))
      (js/Promise.resolve {:status "err"
                           :error "Enter a valid destination address."})

      (not (seq destination-chain))
      (js/Promise.resolve {:status "err"
                           :error "Withdrawal destination chain is missing for this asset."})

      (not (seq asset))
      (js/Promise.resolve {:status "err"
                           :error "Withdrawal asset is missing for this request."})

      (not (seq token))
      (js/Promise.resolve {:status "err"
                           :error "Withdrawal token symbol is missing for this request."})

      (not (seq amount))
      (js/Promise.resolve {:status "err"
                           :error "Enter a valid withdrawal amount."})

      :else
      (-> (fetch-hyperunit-address-with-source-fallbacks! base-url
                                                           base-urls
                                                           "hyperliquid"
                                                           destination-chain
                                                           asset
                                                           destination-address)
          (.then (fn [{:keys [address]}]
                   (-> (submit-send-asset! store
                                           source-address
                                           {:type "sendAsset"
                                            :destination address
                                            :sourceDex "spot"
                                            :destinationDex "spot"
                                            :token token
                                            :amount amount
                                            :fromSubAccount ""})
                       (.then (fn [exchange-resp]
                                (if (= "ok" (:status exchange-resp))
                                  {:status "ok"
                                   :keep-modal-open? true
                                   :network network-label
                                   :asset asset
                                   :token token
                                   :destination destination-address
                                   :destination-chain destination-chain
                                   :protocol-address address}
                                  (let [error-text (str/trim (str (fallback-exchange-response-error exchange-resp)))]
                                    {:status "err"
                                     :error (if (seq error-text)
                                              error-text
                                              "Unknown exchange error")})))))))
          (.catch (fn [err]
                    {:status "err"
                     :error (hyperunit-request-error-message err
                                                             {:asset asset
                                                              :source-chain destination-chain})}))))))
