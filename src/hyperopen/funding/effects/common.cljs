(ns hyperopen.funding.effects.common
  (:require [clojure.string :as str]))

(def arbitrum-mainnet-chain-id
  "0xa4b1")

(def arbitrum-sepolia-chain-id
  "0x66eee")

(def default-deposit-chain-id
  arbitrum-mainnet-chain-id)

(def arbitrum-mainnet-chain-id-decimal
  "42161")

(def arbitrum-usdt-address
  "0xfd086bc7cd5c481dcc9c85ebe478a1c0b69fcbb9")

(def hypercore-chain-id-decimal
  "1337")

(def hypercore-usdh-address
  "0x2000000000000000000000000000000000000168")

(def across-swap-approval-base-url
  "https://app.across.to/api/swap/approval")

(def hyperunit-mainnet-base-url
  "https://api.hyperunit.xyz")

(def hyperunit-testnet-base-url
  "https://api.hyperunit-testnet.xyz")

(def hyperunit-operations-poll-default-delay-ms
  3000)

(def hyperunit-operations-poll-min-delay-ms
  1000)

(def hyperunit-operations-poll-max-delay-ms
  60000)

(def chain-config-by-id
  {arbitrum-mainnet-chain-id {:chain-id arbitrum-mainnet-chain-id
                              :chain-name "Arbitrum One"
                              :network-label "Arbitrum"
                              :rpc-url "https://arb1.arbitrum.io/rpc"
                              :explorer-url "https://arbiscan.io"
                              :usdc-address "0xaf88d065e77c8cc2239327c5edb3a432268e5831"
                              :bridge-address "0x2df1c51e09aecf9cacb7bc98cb1742757f163df7"}
   arbitrum-sepolia-chain-id {:chain-id arbitrum-sepolia-chain-id
                              :chain-name "Arbitrum Sepolia"
                              :network-label "Arbitrum Sepolia"
                              :rpc-url "https://sepolia-rollup.arbitrum.io/rpc"
                              :explorer-url "https://sepolia.arbiscan.io"
                              :usdc-address "0x1baAbB04529D43a73232B713C0FE471f7c7334d5"
                              :bridge-address "0x08cfc1B6b2dCF36A1480b99353A354AA8AC56f89"}})

(defn fallback-exchange-response-error
  [resp]
  (or (:error resp)
      (:message resp)
      (:response resp)
      "Unknown exchange error"))

(defn fallback-runtime-error-message
  [err]
  (or (some-> err .-message)
      (str err)))

(defn normalize-chain-id
  [value]
  (let [raw (some-> value str str/trim)]
    (when (seq raw)
      (let [hex? (str/starts-with? raw "0x")
            source (if hex? (subs raw 2) raw)
            base (if hex? 16 10)
            parsed (js/parseInt source base)]
        (when (and (number? parsed)
                   (not (js/isNaN parsed)))
          (str "0x" (.toString (js/Math.floor parsed) 16)))))))

(defn normalize-address
  [value]
  (let [text (some-> value str str/trim str/lower-case)]
    (when (and (string? text)
               (re-matches #"^0x[0-9a-f]{40}$" text))
      text)))

(defn parse-usdc-units
  [amount]
  (let [text (some-> amount str str/trim)]
    (when (and (seq text)
               (re-matches #"^(?:0|[1-9]\d*)(?:\.\d{1,6})?$" text))
      (let [[whole fract] (str/split text #"\.")
            whole* (or whole "0")
            fract* (subs (str (or fract "") "000000") 0 6)
            whole-units (* (js/BigInt whole*) (js/BigInt "1000000"))
            fract-units (js/BigInt fract*)]
        (+ whole-units fract-units)))))

(defn parse-usdh-units
  [amount]
  (let [text (some-> amount str str/trim)]
    (when (and (seq text)
               (re-matches #"^(?:0|[1-9]\d*)(?:\.\d{1,8})?$" text))
      (let [[whole fract] (str/split text #"\.")
            whole* (or whole "0")
            fract* (subs (str (or fract "") "00000000") 0 8)
            whole-units (* (js/BigInt whole*) (js/BigInt "100000000"))
            fract-units (js/BigInt fract*)]
        (+ whole-units fract-units)))))

(def usdh-route-max-units
  (parse-usdh-units "1000000"))

(defn usdc-units->amount-text
  [value]
  (let [units (or value (js/BigInt "0"))
        raw (.toString units)
        padded (if (<= (count raw) 6)
                 (str (apply str (repeat (- 7 (count raw)) "0")) raw)
                 raw)
        split-idx (- (count padded) 6)
        whole (subs padded 0 split-idx)
        fract (subs padded split-idx)
        fract-trimmed (str/replace fract #"0+$" "")]
    (if (seq fract-trimmed)
      (str whole "." fract-trimmed)
      whole)))

(defn wallet-error-message
  [err]
  (let [message (-> (or (some-> err .-message)
                        (some-> err (aget "message"))
                        (str err))
                    str
                    str/trim)
        code (or (some-> err .-code)
                 (some-> err (aget "code")))]
    (cond
      (= code 4001) "Deposit transaction rejected in wallet."
      (str/includes? (str/lower-case message) "user rejected") "Deposit transaction rejected in wallet."
      (seq message) message
      :else "Unknown wallet error")))

(def ^:private deposit-error-message-paths
  [["message"]
   ["shortMessage"]
   ["reason"]
   ["error" "message"]
   ["error" "reason"]
   ["error" "data" "message"]
   ["data" "message"]
   ["data" "reason"]
   ["data" "originalError" "message"]
   ["data" "originalError" "reason"]
   ["data" "originalError" "data" "message"]
   ["cause" "message"]
   ["cause" "reason"]
   ["revert" "name"]
   ["revert" "signature"]])

(def ^:private deposit-error-code-paths
  [["code"]
   ["error" "code"]
   ["data" "code"]
   ["data" "originalError" "code"]
   ["cause" "code"]])

(def ^:private deposit-error-data-paths
  [["data"]
   ["error" "data"]
   ["data" "originalError" "data"]
   ["cause" "data"]
   ["revert" "data"]])

(defn- error-field
  [value field]
  (when (some? value)
    (if (map? value)
      (or (get value (keyword field))
          (get value field))
      (try
        (aget value field)
        (catch :default _
          nil)))))

(defn- error-path-value
  [value path]
  (reduce (fn [current field]
            (when (some? current)
              (error-field current field)))
          value
          path))

(defn- normalized-error-values
  [err paths]
  (->> paths
       (keep (fn [path]
               (let [value (error-path-value err path)]
                 (when (or (string? value)
                           (number? value))
                   (some-> value str str/trim)))))
       (remove str/blank?)
       distinct
       vec))

(defn- deposit-feedback
  [kind message headline subline detail]
  {:kind kind
   :message message
   :toast (cond-> {:headline headline
                   :subline subline
                   :auto-timeout? false}
            (seq detail) (assoc :detail detail))})

(defn deposit-wallet-error-feedback
  ([err]
   (deposit-wallet-error-feedback err nil))
  ([err {:keys [submitted?]}]
   (let [messages (normalized-error-values err deposit-error-message-paths)
         codes (set (normalized-error-values err deposit-error-code-paths))
         data-values (normalized-error-values err deposit-error-data-paths)
         signal (-> (str/join " " (concat messages data-values))
                    str/lower-case)
         rejected? (or (contains? codes "4001")
                       (str/includes? signal "user rejected")
                       (str/includes? signal "user denied")
                       (str/includes? signal "request rejected"))
         wrong-network? (or (contains? codes "4902")
                            (str/includes? signal "wrong network")
                            (str/includes? signal "unsupported chain")
                            (str/includes? signal "unrecognized chain")
                            (str/includes? signal "chain not found"))
         insufficient-usdc2? (or (str/includes? signal "transfer amount exceeds balance")
                                 (str/includes? signal "erc20insufficientbalance")
                                 (str/includes? signal "0xe450d38c"))
         insufficient-gas? (or (str/includes? signal "insufficient funds for gas")
                               (str/includes? signal "insufficient funds for intrinsic transaction cost"))
         reverted? (or (str/includes? signal "execution reverted")
                       (str/includes? signal "transaction reverted")
                       (str/includes? signal "reverted on-chain")
                       (str/includes? signal "custom error"))]
     (cond
      rejected?
      (deposit-feedback :wallet-rejected
                        "Deposit canceled in your wallet."
                        "Deposit canceled"
                        "No transaction was submitted."
                        nil)

      wrong-network?
      (deposit-feedback :wrong-network
                        "Switch your wallet to Arbitrum Sepolia and try the deposit again."
                        "Wrong wallet network"
                        "Switch to Arbitrum Sepolia."
                        "After switching networks, confirm the connected account and retry the deposit.")

      submitted?
      (if reverted?
        (deposit-feedback :transaction-reverted
                          "The USDC2 deposit transaction was submitted but reverted on-chain."
                          "Deposit transaction reverted"
                          "The submitted transaction did not complete."
                          "Review the wallet activity before retrying to avoid a duplicate deposit.")
        (deposit-feedback :wallet-rpc
                          "The USDC2 deposit transaction was submitted, but confirmation could not be verified."
                          "Deposit confirmation pending"
                          "Check the wallet activity before retrying."
                          "The transaction may still confirm. Verify its status before submitting another deposit."))

      insufficient-usdc2?
      (deposit-feedback :insufficient-usdc2
                        "This wallet does not have enough current Testnet USDC2 for this deposit."
                        "Deposit could not be submitted"
                        "Check the current Testnet USDC2 balance."
                        "A different token named USDC cannot fund this deposit. Confirm the USDC2 balance, then try again.")

      insufficient-gas?
      (deposit-feedback :wallet-rpc
                        "This wallet does not have enough Arbitrum Sepolia test ETH to pay the network fee."
                        "Deposit could not be submitted"
                        "Add Arbitrum Sepolia test ETH."
                        "Test ETH is required for the network fee. Add a small amount, then retry the deposit.")

      reverted?
      (deposit-feedback :transaction-reverted
                        "The network rejected this USDC2 deposit before submission."
                        "Deposit could not be submitted"
                        "Check the Testnet wallet balances."
                        "Confirm current USDC2 and Arbitrum Sepolia test ETH, then try again.")

      :else
      (deposit-feedback :wallet-rpc
                        "The wallet could not submit this Testnet deposit."
                        "Deposit could not be submitted"
                        "Check the wallet and try again."
                        "Confirm Arbitrum Sepolia, current USDC2, and enough test ETH for the network fee.")))))

(defn resolve-deposit-chain-config
  [store action]
  (let [action-chain-id (normalize-chain-id (:chainId action))
        wallet-chain-id (normalize-chain-id (get-in @store [:wallet :chain-id]))
        chain-id (or action-chain-id
                     (when (contains? chain-config-by-id wallet-chain-id)
                       wallet-chain-id)
                     default-deposit-chain-id)]
    (or (get chain-config-by-id chain-id)
        (get chain-config-by-id default-deposit-chain-id))))

(defn resolve-hyperunit-base-urls
  [store]
  (let [wallet-chain-id (normalize-chain-id (get-in @store [:wallet :chain-id]))]
    (vec
     (distinct
      (if (= wallet-chain-id arbitrum-sepolia-chain-id)
        [hyperunit-testnet-base-url]
        [hyperunit-mainnet-base-url])))))

(defn resolve-hyperunit-base-url
  [store]
  (or (first (resolve-hyperunit-base-urls store))
      hyperunit-mainnet-base-url))

(defn non-blank-text
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      text)))

(defn canonical-token
  [value]
  (some-> value str str/trim str/lower-case))

(def chain-token-aliases
  {"arb" "arbitrum"
   "arbitrum" "arbitrum"
   "btc" "bitcoin"
   "bitcoin" "bitcoin"
   "eth" "ethereum"
   "ethereum" "ethereum"
   "hl" "hyperliquid"
   "hyperliquid" "hyperliquid"
   "sol" "solana"
   "solana" "solana"})

(def known-source-chain-tokens
  #{"arbitrum"
    "bitcoin"
    "ethereum"
    "hyperliquid"
    "solana"
    "monad"
    "plasma"})

(def hyperunit-source-chain-candidates-by-canonical
  {"bitcoin" ["bitcoin" "btc"]
   "ethereum" ["ethereum" "eth"]
   "solana" ["solana" "sol"]})

(defn canonical-chain-token
  [value]
  (let [token (canonical-token value)]
    (when (seq token)
      (get chain-token-aliases token token))))

(defn same-chain-token?
  [left right]
  (let [left* (canonical-chain-token left)
        right* (canonical-chain-token right)]
    (and (seq left*)
         (seq right*)
         (= left* right*))))

(def ^:private evm-protocol-source-chains
  #{"ethereum"
    "monad"
    "plasma"})

(defn- bitcoin-protocol-address?
  [address address-lower]
  (boolean
   (and (seq address-lower)
        (or (re-matches #"^bc1[a-z0-9]{20,}$" address-lower)
            (re-matches #"^tb1[a-z0-9]{20,}$" address-lower)
            (re-matches #"^[13][a-km-zA-HJ-NP-Z1-9]{20,}$" address)))))

(defn- evm-protocol-address?
  [address-lower]
  (boolean
   (and (seq address-lower)
        (re-matches #"^0x[0-9a-f]{40}$" address-lower))))

(defn- solana-protocol-address?
  [address]
  (boolean
   (and (seq address)
        (re-matches #"^[1-9A-HJ-NP-Za-km-z]{32,44}$" address))))

(defn protocol-address-matches-source-chain?
  [source-chain address]
  (let [source* (canonical-chain-token source-chain)
        address* (non-blank-text address)
        address-lower (some-> address* str/lower-case)]
    (cond
      (= source* "bitcoin") (bitcoin-protocol-address? address* address-lower)
      (contains? evm-protocol-source-chains source*) (evm-protocol-address? address-lower)
      (= source* "solana") (solana-protocol-address? address*)
      :else false)))

(defn hyperunit-source-chain-candidates
  [value]
  (let [canonical (canonical-chain-token value)]
    (vec (distinct
          (if-let [known (get hyperunit-source-chain-candidates-by-canonical canonical)]
            known
            (when (seq canonical)
              [canonical]))))))

(defn normalize-asset-key
  [value]
  (cond
    (keyword? value) value
    (string? value) (some-> value canonical-token keyword)
    :else nil))
