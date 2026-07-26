(ns hyperopen.config-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.config :as app-config]))

(def hyperliquid-network-contract-keys
  [:network
   :is-mainnet
   :signature-chain-id
   :hyperliquid-chain
   :info-url
   :exchange-url
   :ws-url])

(def mainnet-hyperliquid-network
  {:network :mainnet
   :is-mainnet true
   :signature-chain-id "0xa4b1"
   :hyperliquid-chain "Mainnet"
   :info-url "https://api.hyperliquid.xyz/info"
   :exchange-url "https://api.hyperliquid.xyz/exchange"
   :ws-url "wss://api.hyperliquid.xyz/ws"})

(def testnet-hyperliquid-network
  {:network :testnet
   :is-mainnet false
   :signature-chain-id "0x66eee"
   :hyperliquid-chain "Testnet"
   :info-url "https://api.hyperliquid-testnet.xyz/info"
   :exchange-url "https://api.hyperliquid-testnet.xyz/exchange"
   :ws-url "wss://api.hyperliquid-testnet.xyz/ws"})

(defn selected-network-contract
  [inputs]
  (select-keys (app-config/resolve-hyperliquid-network inputs)
               hyperliquid-network-contract-keys))

(deftest resolve-hyperliquid-network-selects-one-complete-contract-test
  (doseq [[label inputs expected]
          [["query testnet wins over global mainnet"
            {:query-network "testnet"
             :global-network "mainnet"}
            testnet-hyperliquid-network]
           ["query mainnet wins over global testnet"
            {:query-network "mainnet"
             :global-network "testnet"}
            mainnet-hyperliquid-network]
           ["global testnet applies when query is absent"
            {:query-network nil
             :global-network "testnet"}
            testnet-hyperliquid-network]
           ["missing selectors retain the mainnet fallback"
            {:query-network nil
             :global-network nil}
            mainnet-hyperliquid-network]
           ["blank selectors retain the mainnet fallback"
            {:query-network " "
             :global-network ""}
            mainnet-hyperliquid-network]
           ["invalid selectors retain the mainnet fallback"
            {:query-network "test-net"
             :global-network "unsupported"}
            mainnet-hyperliquid-network]]]
    (is (= expected (selected-network-contract inputs)) label)))

(deftest config-publishes-the-startup-network-snapshot-and-websocket-facade-test
  (let [network (:hyperliquid app-config/config)]
    (is (= mainnet-hyperliquid-network
           (select-keys network hyperliquid-network-contract-keys)))
    (is (= (:ws-url network) (:ws-url app-config/config)))))

(deftest config-exposes-centralized-runtime-parameters-test
  (let [cfg app-config/config]
    (is (= "wss://api.hyperliquid.xyz/ws" (:ws-url cfg)))
    (is (= "/sw.js" (:icon-service-worker-path cfg)))
    (is (string? (:app-version cfg)))
    (is (seq (:app-version cfg)))
    (is (= 5000 (get-in cfg [:cooldowns :reconnect-ms])))
    (is (= 5000 (get-in cfg [:cooldowns :reset-subscriptions-ms])))
    (is (= 30000 (get-in cfg [:cooldowns :auto-recover-severe-threshold-ms])))
    (is (= 300000 (get-in cfg [:cooldowns :auto-recover-cooldown-ms])))
    (is (= 1500 (get-in cfg [:ui :wallet-copy-feedback-ms])))
    (is (= 3500 (get-in cfg [:ui :order-toast-ms])))
    (is (= 1200 (get-in cfg [:startup :deferred-bootstrap-delay-ms])))
    (is (= 120 (get-in cfg [:startup :per-dex-stagger-ms])))
    (is (= 5000 (get-in cfg [:startup :startup-summary-delay-ms])))
    (is (= {:enabled? true
            :base-url "https://price-history.hyperopen.xyz"
            :proxy-policy :approved-proxy-allowed
            :include-aligned-returns? true
            :fallback-to-legacy? true
            :legacy-fallback-request-spacing-ms 200}
           (:optimizer-history-api cfg)))
    (is (= 50 (get-in cfg [:diagnostics :timeline-limit])))))
