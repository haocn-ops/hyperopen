(ns hyperopen.api.trading.user-actions
  (:require [clojure.string :as str]
            [hyperopen.api.trading.debug-exchange-simulator :as debug-exchange-simulator]
            [hyperopen.api.trading.http :as http]
            [hyperopen.config :as app-config]
            [hyperopen.trading-crypto-modules :as trading-crypto-modules]
            [hyperopen.wallet.agent-session :as agent-session]))

(defn parse-chain-id-int
  [value]
  (let [raw (some-> value str str/trim)]
    (when (seq raw)
      (let [hex? (str/starts-with? raw "0x")
            source (if hex? (subs raw 2) raw)
            base (if hex? 16 10)
            parsed (js/parseInt source base)]
        (when (and (number? parsed)
                   (not (js/isNaN parsed)))
          (js/Math.floor parsed))))))

(defn- normalize-signature-chain-id
  [value]
  (cond
    (string? value)
    (let [text (str/trim value)]
      (when (seq text)
        text))

    (number? value)
    (str "0x" (.toString (js/Math.floor value) 16))

    :else nil))

(defn- supported-wallet-signature-chain-id
  [wallet-chain-id]
  (let [candidate (normalize-signature-chain-id wallet-chain-id)
        candidate-int (parse-chain-id-int candidate)
        mainnet-chain-id (agent-session/default-signature-chain-id-for-environment true)
        testnet-chain-id (agent-session/default-signature-chain-id-for-environment false)]
    (cond
      (= candidate-int (parse-chain-id-int mainnet-chain-id))
      mainnet-chain-id

      (= candidate-int (parse-chain-id-int testnet-chain-id))
      testnet-chain-id

      :else nil)))

(defn resolve-user-signing-context
  [_store]
  (let [{:keys [signature-chain-id hyperliquid-chain]} (:hyperliquid app-config/config)]
    {:signature-chain-id signature-chain-id
     :hyperliquid-chain hyperliquid-chain}))

(defn- wallet-network-mismatch-error
  [store expected-signature-chain-id expected-hyperliquid-chain]
  (let [wallet-signature-chain-id
        (supported-wallet-signature-chain-id (get-in @store [:wallet :chain-id]))]
    (when (and wallet-signature-chain-id
               (not= wallet-signature-chain-id expected-signature-chain-id))
      (js/Error.
       (str "Wallet network does not match Hyperliquid " expected-hyperliquid-chain
            ". Switch the wallet network or reload with the matching hyperliquidNetwork selector.")))))

(defn- next-user-signed-nonce!
  [store]
  (let [cursor (get-in @store [:wallet :user-signed-nonce-cursor])
        nonce (http/next-nonce cursor)]
    (swap! store assoc-in [:wallet :user-signed-nonce-cursor] nonce)
    nonce))

(defn approve-agent!
  [store address action]
  (if-let [rejection (http/reject-when-trading-disabled!)]
    rejection
    (-> (trading-crypto-modules/load-trading-crypto-module!)
        (.then (fn [crypto]
                 ((:sign-approve-agent-action! crypto) address action)))
        (.then (fn [sig]
                 (let [{:keys [r s v]} (js->clj sig :keywordize-keys true)
                       payload {:action action
                                :nonce (:nonce action)
                                :signature {:r r
                                            :s s
                                            :v v}}]
                   (or (debug-exchange-simulator/simulated-fetch-response [[:approveAgent]])
                       (http/json-post! http/exchange-url payload))))))))

(defn- sign-and-post-user-action!
  [store address action nonce-field sign-action-key]
  (if-let [rejection (http/reject-when-trading-disabled!)]
    rejection
    (let [{:keys [signature-chain-id hyperliquid-chain]} (resolve-user-signing-context store)]
      (if-let [mismatch-error (wallet-network-mismatch-error store
                                                             signature-chain-id
                                                             hyperliquid-chain)]
        (js/Promise.reject mismatch-error)
        (let [nonce (next-user-signed-nonce! store)
              action* (-> action
                          (assoc :signatureChainId signature-chain-id
                                 :hyperliquidChain hyperliquid-chain)
                          (assoc nonce-field nonce))]
          (-> (trading-crypto-modules/load-trading-crypto-module!)
              (.then (fn [crypto]
                       (when-not (contains? crypto sign-action-key)
                         (throw (js/Error.
                                 (str "Missing trading crypto signer: " sign-action-key))))
                       ((get crypto sign-action-key) address action*)))
              (.then (fn [sig]
                       (let [{:keys [r s v]} (js->clj sig :keywordize-keys true)
                             signature {:r r
                                        :s s
                                        :v v}]
                         (-> (http/post-signed-action! action* nonce signature)
                             (.then http/parse-json!)))))))))))

(defn submit-usd-class-transfer! [store address action]
  (sign-and-post-user-action! store address action :nonce :sign-usd-class-transfer-action!))

(defn submit-send-asset! [store address action]
  (sign-and-post-user-action! store address action :nonce :sign-send-asset-action!))

(defn submit-c-deposit! [store address action]
  (sign-and-post-user-action! store address action :nonce :sign-c-deposit-action!))

(defn submit-c-withdraw! [store address action]
  (sign-and-post-user-action! store address action :nonce :sign-c-withdraw-action!))

(defn submit-token-delegate! [store address action]
  (sign-and-post-user-action! store address action :nonce :sign-token-delegate-action!))

(defn submit-withdraw3! [store address action]
  (sign-and-post-user-action! store address action :time :sign-withdraw3-action!))
