(ns hyperopen.api.trading.approve-builder-fee-edge-cases-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.api.trading :as trading]
            [hyperopen.api.trading.http :as http]
            [hyperopen.api.trading.test-support :as support]
            [hyperopen.trading-crypto-modules :as trading-crypto-modules]))

(def configured-store
  (fn []
    (atom {:tenant/override
           {:tenant/id "dexhelm"
            :builder-fee {:status :configured
                          :builder-address "0x36a47878219fb346e031f6cf82cbfc8c77e35932"
                          :fee-tenths-bp 10
                          :disclosure "A disclosed 0.01% builder fee."}}
           :wallet {:chain-id "0x66eee"}})))

(deftest disabled-network-builder-fee-approval-rejects-before-any-wallet-or-agent-signer-work-test
  (async done
    (let [load-calls (atom 0)]
      (with-redefs [http/trading-enabled? (constantly false)
                    trading-crypto-modules/load-trading-crypto-module!
                    (fn []
                      (swap! load-calls inc)
                      (js/Promise.resolve {}))]
        (-> (trading/approve-builder-fee! (configured-store) support/owner-address)
            (.then (fn [_]
                     (is false "Expected disabled-network builder-fee rejection")
                     (done)))
            (.catch (fn [error]
                      (is (re-find #"Trading is disabled" (str error)))
                      (is (= 0 @load-calls))
                      (done)))))))

(deftest builder-fee-approval-rejects-missing-config-before-allocating-a-nonce-or-signing-test
  (async done
    (let [store (atom {:wallet {:chain-id "0x66eee"}})
          load-calls (atom 0)]
      (with-redefs [trading-crypto-modules/load-trading-crypto-module!
                    (fn []
                      (swap! load-calls inc)
                      (js/Promise.resolve {}))]
        (-> (trading/approve-builder-fee! store support/owner-address)
            (.then (fn [_]
                     (is false "Expected missing-config builder-fee rejection")
                     (done)))
            (.catch (fn [error]
                      (is (re-find #"builder|configured" (str error)))
                      (is (= 0 @load-calls))
                      (is (nil? (get-in @store [:wallet :user-signed-nonce-cursor])))
                      (done)))))))))
