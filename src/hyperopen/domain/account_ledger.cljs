(ns hyperopen.domain.account-ledger
  (:require [clojure.string :as str]))

(def ^:private default-status-label
  "Completed")

(def ^:private trading-account-label
  "Trading Account")

(def ^:private bridge-label
  "Arbitrum")

(defn- finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))
       (js/isFinite value)))

(defn- parse-decimal
  [value]
  (cond
    (finite-number? value)
    value

    (string? value)
    (let [num (js/parseFloat value)]
      (when (finite-number? num)
        num))

    :else nil))

(defn- parse-ms
  [value]
  (when-let [num (parse-decimal value)]
    (js/Math.floor num)))

(defn- field
  [m k]
  (or (get m k)
      (get m (name k))))

(defn- non-blank-text
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      text)))

(defn- normalized-token
  [value]
  (some-> value
          non-blank-text
          (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
          (str/replace #"[_\s]+" "-")
          str/lower-case))

(defn- title-case-label
  [value]
  (let [text (or (normalized-token value) "")]
    (->> (str/split text #"-+")
         (remove str/blank?)
         (map (fn [part]
                (str (str/upper-case (subs part 0 1))
                     (subs part 1))))
         (str/join " "))))

(defn- action-label
  [type-token]
  (case type-token
    "deposit" "Deposit"
    "withdraw" "Withdrawal"
    "vault-deposit" "Vault Deposit"
    "vault-withdraw" "Vault Withdrawal"
    "spot-genesis" "Genesis Distribution"
    "internal-transfer" "Send"
    "spot-transfer" "Send"
    "sub-account-transfer" "Send"
    "account-class-transfer" "Transfer"
    "liquidation" "Liquidation"
    "rewards-claim" "Rewards Claim"
    (title-case-label type-token)))

(defn- account-source-destination
  [type-token]
  (case type-token
    "deposit" [bridge-label trading-account-label]
    "withdraw" [trading-account-label bridge-label]
    [trading-account-label trading-account-label]))

(defn- positive-change-type?
  [type-token]
  (contains? #{"deposit"
               "vault-withdraw"
               "spot-genesis"
               "rewards-claim"}
             type-token))

(defn- negative-change-type?
  [type-token]
  (contains? #{"withdraw"
               "vault-deposit"
               "internal-transfer"
               "spot-transfer"
               "sub-account-transfer"
               "liquidation"}
             type-token))

(defn- signed-amount
  [type-token amount]
  (cond
    (nil? amount)
    nil

    (negative-change-type? type-token)
    (- (js/Math.abs amount))

    (positive-change-type? type-token)
    (js/Math.abs amount)

    :else amount))

(defn- ledger-delta
  [row]
  (let [delta (field row :delta)]
    (if (map? delta)
      delta
      row)))

(defn- amount-value
  [delta]
  (some parse-decimal
        [(field delta :usdc)
         (field delta :amount)
         (field delta :value)
         (field delta :qty)
         (field delta :sz)]))

(defn- asset-label
  [delta]
  (or (non-blank-text (field delta :token))
      (non-blank-text (field delta :coin))
      (non-blank-text (field delta :asset))
      "USDC"))

(defn- fee-value
  [delta]
  (some parse-decimal
        [(field delta :fee)
         (field delta :withdrawalFee)
         (field delta :gasFee)]))

(defn- strip-trailing-zeroes
  [value]
  (-> value
      (str/replace #"(\.\d*?[1-9])0+$" "$1")
      (str/replace #"\.0+$" "")))

(defn- format-number
  [value]
  (if (finite-number? value)
    (strip-trailing-zeroes (.toFixed value 8))
    "--"))

(defn- format-signed-amount
  [amount asset]
  (if (finite-number? amount)
    (str (if (neg? amount) "-" "+")
         (format-number (js/Math.abs amount))
         " "
         (or asset "USDC"))
    "--"))

(defn- format-fee
  [fee asset]
  (if (finite-number? fee)
    (str (format-number fee) " " (or asset "USDC"))
    "--"))

(defn- status-label
  [row delta]
  (or (some-> (or (field row :status)
                  (field delta :status))
              title-case-label
              non-blank-text)
      default-status-label))

(defn- explorer-url
  [hash]
  (when-let [hash* (non-blank-text hash)]
    (str "https://app.hyperliquid.xyz/explorer/tx/" hash*)))

(defn- ledger-row-id
  [time-ms type-token hash asset amount]
  (str (or (non-blank-text hash) "no-hash")
       "|"
       (or time-ms 0)
       "|"
       (or type-token "")
       "|"
       (or asset "")
       "|"
       (format-number (or amount 0))))

(defn normalize-ledger-row
  [row]
  (when (map? row)
    (let [delta (ledger-delta row)
          type-token (normalized-token (field delta :type))
          time-ms (or (parse-ms (field row :time))
                      (parse-ms (field row :timestamp)))
          amount (amount-value delta)
          asset (asset-label delta)
          signed (signed-amount type-token amount)
          hash (non-blank-text (or (field row :hash)
                                   (field delta :hash)))
          [source destination] (account-source-destination type-token)]
      (when (and type-token
                 time-ms
                 (finite-number? signed)
                 (seq (action-label type-token)))
        {:id (ledger-row-id time-ms type-token hash asset signed)
         :time-ms time-ms
         :time time-ms
         :status-label (status-label row delta)
         :action-label (action-label type-token)
         :source-label source
         :destination-label destination
         :asset asset
         :amount amount
         :signed-amount signed
         :amount-text (format-signed-amount signed asset)
         :fee (fee-value delta)
         :fee-text (format-fee (fee-value delta) "USDC")
         :hash hash
         :explorer-url (explorer-url hash)}))))

(defn- sort-ledger-rows
  [rows]
  (->> rows
       (sort-by (fn [row]
                  [(- (or (:time-ms row) 0))
                   (or (:id row) "")]))
       vec))

(defn normalize-ledger-rows
  [rows]
  (sort-ledger-rows
   (into []
         (comp
           (map normalize-ledger-row)
           (keep identity))
         (or rows []))))

(defn merge-ledger-rows
  [primary secondary]
  (->> (concat (or primary []) (or secondary []))
       normalize-ledger-rows
       (reduce (fn [acc row]
                 (if (seq (:id row))
                   (assoc acc (:id row) row)
                   acc))
               {})
       vals
       sort-ledger-rows))
