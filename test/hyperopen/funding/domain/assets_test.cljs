(ns hyperopen.funding.domain.assets-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.config :as app-config]
            [hyperopen.funding.domain.assets :as assets]))

(defn- usdc-asset
  [state]
  (some (fn [asset]
          (when (= :usdc (:key asset))
            asset))
        (assets/deposit-assets state)))

(deftest testnet-app-config-selects-sepolia-over-wallet-chain-test
  (with-redefs [app-config/config {:hyperliquid {:is-mainnet false}}]
    (let [asset (usdc-asset {:wallet {:chain-id assets/deposit-chain-id-mainnet}})]
      (is (= "Arbitrum Sepolia" (:network asset)))
      (is (= assets/deposit-chain-id-testnet (:chain-id asset))))))

(deftest mainnet-app-config-retains-wallet-chain-fallback-test
  (with-redefs [app-config/config {:hyperliquid {:is-mainnet true}}]
    (let [mainnet-asset (usdc-asset {:wallet {:chain-id assets/deposit-chain-id-mainnet}})
          testnet-asset (usdc-asset {:wallet {:chain-id assets/deposit-chain-id-testnet}})]
      (is (= "Arbitrum" (:network mainnet-asset)))
      (is (= assets/deposit-chain-id-mainnet (:chain-id mainnet-asset)))
      (is (= "Arbitrum Sepolia" (:network testnet-asset)))
      (is (= assets/deposit-chain-id-testnet (:chain-id testnet-asset))))))
