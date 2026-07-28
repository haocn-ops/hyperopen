(ns hyperopen.config
  (:require [clojure.string :as str]
            [hyperopen.service.tenant-config :as tenant-config]))

(goog-define APP_VERSION "0.1.0")
(goog-define TENANT_CONFIG_JSON "")
(goog-define DEPLOYMENT_HYPERLIQUID_NETWORK "")

(defn parse-tenant-config-json
  "Parse and normalize public tenant JSON.

   Invalid, empty, or schema-invalid input returns nil so callers resolve the
   safe default tenant through the normal tenant-config boundary."
  [raw]
  (let [raw* (some-> raw str str/trim)]
    (when (seq raw*)
      (try
        (let [parsed (js->clj (js/JSON.parse raw*) :keywordize-keys true)
              parsed* (cond-> parsed
                        (map? (:features parsed))
                        (update :features
                                (fn [features]
                                  {:terminal (true? (:terminal features))
                                   :analytics (true? (:analytics features))
                                   :affiliate (true? (:affiliate features))}))
                        (some? (:theme/id parsed))
                        (update :theme/id tenant-config/normalize-tenant-theme-id)
                        (string? (get-in parsed [:venue :id]))
                        (update-in [:venue :id] keyword)
                        (string? (get-in parsed [:affiliate :provider]))
                        (update-in [:affiliate :provider] keyword)
                        (string? (get-in parsed [:affiliate :status]))
                        (update-in [:affiliate :status] keyword)
                        (nil? (get-in parsed [:affiliate :status]))
                        (assoc-in [:affiliate :status] :unavailable))]
          (when (tenant-config/valid-tenant-config? parsed*)
            (tenant-config/normalize-tenant-config parsed*)))
        (catch :default _
          nil)))))

(defn configured-tenant-override
  "Parse the optional public tenant JSON build define."
  []
  (parse-tenant-config-json TENANT_CONFIG_JSON))

(def tenant-override
  (configured-tenant-override))

(def ^:private mainnet-hyperliquid-network
  {:network :mainnet
   :is-mainnet true
   :signature-chain-id "0xa4b1"
   :hyperliquid-chain "Mainnet"
   :info-url "https://api.hyperliquid.xyz/info"
   :exchange-url "https://api.hyperliquid.xyz/exchange"
   :ws-url "wss://api.hyperliquid.xyz/ws"})

(def ^:private testnet-hyperliquid-network
  {:network :testnet
   :is-mainnet false
   :signature-chain-id "0x66eee"
   :hyperliquid-chain "Testnet"
   :info-url "https://api.hyperliquid-testnet.xyz/info"
   :exchange-url "https://api.hyperliquid-testnet.xyz/exchange"
   :ws-url "wss://api.hyperliquid-testnet.xyz/ws"})

(def ^:private disabled-hyperliquid-network
  {:network :disabled
   :is-mainnet false
   :signature-chain-id nil
   :hyperliquid-chain nil
   :trading-enabled? false
   :error "Trading is disabled because this release has no valid network declaration."
   :info-url nil
   :exchange-url nil
   :ws-url nil})

(defn- normalize-hyperliquid-network
  [value]
  (let [network (some-> value str str/trim)]
    (when (contains? #{"mainnet" "testnet"} network)
      network)))

(defn resolve-hyperliquid-network
  "Resolve a complete Hyperliquid transport and signing contract without I/O."
  [{:keys [deployment-network]}]
  (let [selected-network (normalize-hyperliquid-network deployment-network)]
    (case selected-network
      "testnet" (assoc testnet-hyperliquid-network :trading-enabled? true :error nil)
      "mainnet" (assoc mainnet-hyperliquid-network :trading-enabled? true :error nil)
      disabled-hyperliquid-network)))

(defn- startup-hyperliquid-network-inputs
  []
  {:deployment-network DEPLOYMENT_HYPERLIQUID_NETWORK
   :query-network (when (exists? js/location)
                    (.get (js/URLSearchParams. (or (.-search js/location) ""))
                          "hyperliquidNetwork"))
   :global-network (when (exists? js/globalThis)
                     (.-HYPEROPEN_HYPERLIQUID_NETWORK js/globalThis))})

(def hyperliquid-network
  (resolve-hyperliquid-network (startup-hyperliquid-network-inputs)))

(def config
  {:hyperliquid hyperliquid-network
   :ws-url (:ws-url hyperliquid-network)
   :icon-service-worker-path "/sw.js"
   :app-version APP_VERSION
   :cooldowns {:reconnect-ms 5000
               :reset-subscriptions-ms 5000
               :auto-recover-severe-threshold-ms 30000
               :auto-recover-cooldown-ms 300000}
   :ui {:wallet-copy-feedback-ms 1500
        :order-toast-ms 3500}
   :trading {:agent-expires-after-ms 15000
             :agent-schedule-cancel-ahead-ms 60000
             :agent-schedule-cancel-refresh-ms 30000}
   :ws-migration {:order-fill-ws-first? true
                  :startup-bootstrap-ws-first? true
                  :candle-subscriptions? false
                  :auto-fallback-on-health-degrade? true}
   :startup {:deferred-bootstrap-delay-ms 1200
             :stream-backfill-delay-ms 450
             :funding-history-lookback-ms 604800000
             :per-dex-stagger-ms 120
             :startup-summary-delay-ms 5000}
   :optimizer-history-api {:enabled? true
                           :base-url "https://price-history.hyperopen.xyz"
                           :proxy-policy :approved-proxy-allowed
                           :include-aligned-returns? true
                           :fallback-to-legacy? true
                           :legacy-fallback-request-spacing-ms 200}
   :diagnostics {:timeline-limit 50}
   :tenant {:override tenant-override}
   :messages {:agent-storage-mode-reset "Trading persistence updated. Enable Trading again."
              :agent-protection-mode-reset "Trading session protection updated. Enable Trading again."}})
