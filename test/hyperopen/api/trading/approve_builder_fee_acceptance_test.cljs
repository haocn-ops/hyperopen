(ns hyperopen.api.trading.approve-builder-fee-acceptance-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.api.trading :as trading]
            [hyperopen.api.trading.test-support :as support]
            [hyperopen.api.trading.user-actions :as user-actions]
            [hyperopen.trading-crypto-modules :as trading-crypto-modules]
            [hyperopen.utils.hl-signing :as signing]))

(def owner support/owner-address)
(def builder "0x36a47878219fb346e031f6cf82cbfc8c77e35932")
(def configured-tenant
  {:tenant/id "dexhelm"
   :builder-fee {:status :configured
                 :builder-address builder
                 :fee-tenths-bp 10
                 :disclosure "A disclosed 0.01% builder fee."}})

(deftest approve-builder-fee-typed-data-pins-the-four-field-main-wallet-contract-test
  (let [typed-data (signing/build-approve-builder-fee-typed-data
                    {:hyperliquidChain "Testnet"
                     :signatureChainId "0x66eee"
                     :maxFeeRate "0.01%"
                     :builder builder
                     :nonce 1700000000001})]
    (is (= "HyperliquidTransaction:ApproveBuilderFee" (:primaryType typed-data)))
    (is (= 421614 (get-in typed-data [:domain :chainId])))
    (is (= {:hyperliquidChain "Testnet"
            :maxFeeRate "0.01%"
            :builder builder
            :nonce 1700000000001}
           (:message typed-data)))
    (is (= [{:name "hyperliquidChain" :type "string"}
            {:name "maxFeeRate" :type "string"}
            {:name "builder" :type "address"}
            {:name "nonce" :type "uint64"}]
           (get-in typed-data [:types "HyperliquidTransaction:ApproveBuilderFee"])))))

(deftest approve-builder-fee-uses-the-connected-owner-provider-signer-and-one-matching-exchange-nonce-test
  (async done
    (let [store (atom {:tenant/override configured-tenant
                       :wallet {:chain-id "0x66eee"
                                :user-signed-nonce-cursor 1700000000000}})
          signing-calls (atom [])
          fetch-calls (atom [])
          restore-fetch! (support/install-fetch-stub!
                          (fn [url opts]
                            (swap! fetch-calls conj [url opts])
                            (js/Promise.resolve (support/json-response {:status "ok"}))))]
      (with-redefs [user-actions/resolve-user-signing-context
                    (fn [_] {:signature-chain-id "0x66eee" :hyperliquid-chain "Testnet"})
                    trading-crypto-modules/load-trading-crypto-module!
                    (fn [] (js/Promise.resolve
                            {:sign-approve-builder-fee-action!
                             (fn [address action]
                               (swap! signing-calls conj [address action])
                               (js/Promise.resolve (clj->js {:r "0x01" :s "0x02" :v 27})))}))]
        (-> (trading/approve-builder-fee! store owner)
            (.then (fn [response]
                     (is (= "ok" (:status response)))
                     (is (= 1 (count @signing-calls)))
                     (is (= 1 (count @fetch-calls)))
                     (let [[signed-owner action] (first @signing-calls)
                           [posted-url posted-opts] (first @fetch-calls)
                           posted-payload (support/fetch-body->map posted-opts)]
                       (is (= owner signed-owner))
                       (is (= "approveBuilderFee" (:type action)))
                       (is (= builder (:builder action)))
                       (is (= "0.01%" (:maxFeeRate action)))
                       (is (= "Testnet" (:hyperliquidChain action)))
                       (is (= "0x66eee" (:signatureChainId action)))
                       (is (= trading/exchange-url posted-url))
                       (is (= (:nonce action) (:nonce posted-payload)))
                       (is (= action (:action posted-payload)))
                       (is (= {:r "0x01" :s "0x02" :v 27}
                              (:signature posted-payload))))
                     (done)))
            (.catch (fn [error]
                      (is false (str "Unexpected approveBuilderFee error: " error))
                      (done)))
            (.finally restore-fetch!))))))
