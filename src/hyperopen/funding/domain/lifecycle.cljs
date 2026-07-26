(ns hyperopen.funding.domain.lifecycle
  (:require [clojure.string :as str]
            [hyperopen.domain.trading :as trading-domain]))

(def ^:private supported-asset-keys
  #{:usdc :usdt :btc :eth :sol :2z :bonk :ena :fart :mon :pump :spxs :xpl :usdh})

(def ^:private hyperunit-lifecycle-terminal-fragments
  ["done" "fail" "error" "revert" "cancel" "refund"])

(def ^:private status-values
  #{:idle :loading :ready :error})

(defn- non-blank-text
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      text)))

(defn- parse-num
  [value]
  (trading-domain/parse-num value))

(defn- finite-number?
  [value]
  (and (number? value)
       (js/isFinite value)
       (not (js/isNaN value))))

(defn- normalize-asset-key
  [value]
  (let [asset-key (cond
                    (keyword? value) value
                    (string? value) (some-> value str/trim str/lower-case keyword)
                    :else nil)]
    (when (contains? supported-asset-keys asset-key)
      asset-key)))

(defn- normalize-lifecycle-direction
  [value]
  (let [direction (cond
                    (keyword? value) value
                    (string? value) (some-> value str/trim str/lower-case keyword)
                    :else nil)]
    (when (contains? #{:deposit :withdraw} direction)
      direction)))

(defn- normalize-lifecycle-keyword
  [value]
  (cond
    (keyword? value) value
    (string? value)
    (let [text (-> value
                   str
                   str/trim
                   str/lower-case
                   (str/replace #"[^a-z0-9]+" "-")
                   (str/replace #"^-+|-+$" ""))]
      (when (seq text)
        (keyword text)))
    :else nil))

(defn- normalize-lifecycle-non-negative-int
  [value]
  (let [parsed (parse-num value)]
    (when (and (finite-number? parsed)
               (>= parsed 0))
      (js/Math.floor parsed))))

(defn- lifecycle-token
  [value]
  (some-> value
          name
          str/lower-case
          (str/replace #"[^a-z0-9]+" "-")
          (str/replace #"^-+|-+$" "")))

(defn- lifecycle-fragment-match?
  [value fragments]
  (let [token (lifecycle-token value)]
    (and (seq token)
         (some #(str/includes? token %) fragments))))

(defn default-hyperunit-lifecycle-state
  []
  {:direction nil
   :asset-key nil
   :operation-id nil
   :state nil
   :status nil
   :source-tx-confirmations nil
   :destination-tx-confirmations nil
   :position-in-withdraw-queue nil
   :destination-tx-hash nil
   :state-next-at nil
   :last-updated-ms nil
   :error nil})

(defn normalize-hyperunit-lifecycle
  [lifecycle]
  (let [lifecycle* (if (map? lifecycle) lifecycle {})]
    {:direction (normalize-lifecycle-direction (:direction lifecycle*))
     :asset-key (normalize-asset-key (:asset-key lifecycle*))
     :operation-id (non-blank-text (:operation-id lifecycle*))
     :state (normalize-lifecycle-keyword (:state lifecycle*))
     :status (normalize-lifecycle-keyword (:status lifecycle*))
     :source-tx-confirmations (normalize-lifecycle-non-negative-int (:source-tx-confirmations lifecycle*))
     :destination-tx-confirmations (normalize-lifecycle-non-negative-int (:destination-tx-confirmations lifecycle*))
     :position-in-withdraw-queue (normalize-lifecycle-non-negative-int (:position-in-withdraw-queue lifecycle*))
     :destination-tx-hash (non-blank-text (:destination-tx-hash lifecycle*))
     :state-next-at (normalize-lifecycle-non-negative-int (:state-next-at lifecycle*))
     :last-updated-ms (normalize-lifecycle-non-negative-int (:last-updated-ms lifecycle*))
     :error (non-blank-text (:error lifecycle*))}))

(defn hyperunit-lifecycle-terminal?
  [lifecycle]
  (let [lifecycle* (normalize-hyperunit-lifecycle lifecycle)]
    (boolean
     (or (lifecycle-fragment-match? (:state lifecycle*) hyperunit-lifecycle-terminal-fragments)
         (lifecycle-fragment-match? (:status lifecycle*) hyperunit-lifecycle-terminal-fragments)))))

(defn- normalize-estimate-status
  [value]
  (let [status (cond
                 (keyword? value) value
                 (string? value) (some-> value str/trim str/lower-case keyword)
                 :else nil)]
    (if (contains? status-values status)
      status
      :idle)))

(defn- normalize-estimate-fee-value
  [value]
  (cond
    (and (number? value)
         (finite-number? value))
    value

    (string? value)
    (let [text (non-blank-text value)
          parsed (parse-num text)]
      (if (finite-number? parsed)
        parsed
        text))

    :else nil))

(defn- normalize-estimate-entry
  [value]
  (let [entry (if (map? value) value {})
        chain (some-> (:chain entry)
                      non-blank-text
                      str/lower-case)
        deposit-eta (non-blank-text (:deposit-eta entry))
        withdrawal-eta (non-blank-text (:withdrawal-eta entry))
        deposit-fee (normalize-estimate-fee-value (:deposit-fee entry))
        withdrawal-fee (normalize-estimate-fee-value (:withdrawal-fee entry))
        metrics (if (map? (:metrics entry))
                  (:metrics entry)
                  {})]
    {:chain chain
     :deposit-eta deposit-eta
     :withdrawal-eta withdrawal-eta
     :deposit-fee deposit-fee
     :withdrawal-fee withdrawal-fee
     :metrics metrics}))

(defn default-hyperunit-fee-estimate-state
  []
  {:status :idle
   :by-chain {}
   :requested-at-ms nil
   :updated-at-ms nil
   :error nil})

(defn normalize-hyperunit-fee-estimate
  [value]
  (let [estimate (if (map? value) value {})
        by-chain (if (map? (:by-chain estimate))
                   (:by-chain estimate)
                   {})
        normalized-by-chain (reduce-kv (fn [acc chain-key entry]
                                         (if-let [chain (cond
                                                          (keyword? chain-key)
                                                          (some-> chain-key name non-blank-text str/lower-case)

                                                          (string? chain-key)
                                                          (some-> chain-key non-blank-text str/lower-case)

                                                          :else
                                                          (some-> chain-key str non-blank-text str/lower-case))]
                                           (assoc acc chain (normalize-estimate-entry
                                                             (assoc (if (map? entry) entry {})
                                                                    :chain chain)))
                                           acc))
                                       {}
                                       by-chain)]
    {:status (normalize-estimate-status (:status estimate))
     :by-chain normalized-by-chain
     :requested-at-ms (normalize-lifecycle-non-negative-int (:requested-at-ms estimate))
     :updated-at-ms (normalize-lifecycle-non-negative-int (:updated-at-ms estimate))
     :error (non-blank-text (:error estimate))}))

(defn- normalize-withdraw-queue-status
  [value]
  (let [status (cond
                 (keyword? value) value
                 (string? value) (some-> value str/trim str/lower-case keyword)
                 :else nil)]
    (if (contains? status-values status)
      status
      :idle)))

(defn- normalize-withdraw-queue-entry
  [value]
  (let [entry (if (map? value) value {})
        queue-length (normalize-lifecycle-non-negative-int
                      (:withdrawal-queue-length entry))]
    {:chain (some-> (:chain entry)
                    non-blank-text
                    str/lower-case)
     :last-withdraw-queue-operation-tx-id
     (non-blank-text (:last-withdraw-queue-operation-tx-id entry))
     :withdrawal-queue-length (if (number? queue-length)
                                queue-length
                                0)}))

(defn default-hyperunit-withdrawal-queue-state
  []
  {:status :idle
   :by-chain {}
   :requested-at-ms nil
   :updated-at-ms nil
   :error nil})

(defn normalize-hyperunit-withdrawal-queue
  [value]
  (let [queue (if (map? value) value {})
        by-chain (if (map? (:by-chain queue))
                   (:by-chain queue)
                   {})
        normalized-by-chain (reduce-kv (fn [acc chain-key entry]
                                         (if-let [chain (cond
                                                          (keyword? chain-key)
                                                          (some-> chain-key name non-blank-text str/lower-case)

                                                          (string? chain-key)
                                                          (some-> chain-key non-blank-text str/lower-case)

                                                          :else
                                                          (some-> chain-key str non-blank-text str/lower-case))]
                                           (assoc acc chain (normalize-withdraw-queue-entry
                                                             (assoc (if (map? entry) entry {})
                                                                    :chain chain)))
                                           acc))
                                       {}
                                       by-chain)]
    {:status (normalize-withdraw-queue-status (:status queue))
     :by-chain normalized-by-chain
     :requested-at-ms (normalize-lifecycle-non-negative-int (:requested-at-ms queue))
     :updated-at-ms (normalize-lifecycle-non-negative-int (:updated-at-ms queue))
     :error (non-blank-text (:error queue))}))
