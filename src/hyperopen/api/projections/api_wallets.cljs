(ns hyperopen.api.projections.api-wallets
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]
            [hyperopen.api.errors :as api-errors]
            [hyperopen.wallet.agent-session :as agent-session]))

(def ^:private default-api-wallet-name
  "app.hyperopen.xyz")

(defn- normalized-error
  [err]
  (api-errors/normalize-error err))

(defn- default-agent-name-from-snapshot
  [snapshot]
  (some-> (or (:agentName snapshot)
              (:agent-name snapshot)
              (:walletName snapshot)
              (:wallet-name snapshot)
              (:agentLabel snapshot)
              (:agent-label snapshot)
              (:label snapshot))
          str
          str/trim
          not-empty))

(defn- parse-ms
  [value]
  (let [parsed (cond
                 (integer? value) value
                 (and (number? value)
                      (not (js/isNaN value))) value
                 (string? value) (js/parseInt (str/trim value) 10)
                 :else js/NaN)]
    (when (and (number? parsed)
               (not (js/isNaN parsed)))
      (js/Math.floor parsed))))

(defn- build-default-agent-row
  [snapshot]
  (let [address (some-> (or (:agentAddress snapshot)
                            (:agent-address snapshot))
                        str
                        str/trim
                        str/lower-case
                        not-empty)
        approval-name (default-agent-name-from-snapshot snapshot)
        parsed-name (agent-session/parse-agent-name-valid-until approval-name)
        name (:name parsed-name)
        encoded-valid-until-ms (:valid-until-ms parsed-name)
        explicit-valid-until-ms (some parse-ms
                                      [(:agentValidUntil snapshot)
                                       (:agent-valid-until snapshot)
                                       (:validUntil snapshot)
                                       (:valid-until snapshot)])]
    (when address
      {:row-kind :default
       :name (or name approval-name default-api-wallet-name)
       :approval-name nil
       :address address
       :valid-until-ms (or explicit-valid-until-ms
                           encoded-valid-until-ms)})))

(defn clear-api-wallets-errors
  [state]
  (-> state
      (assoc-in [:api-wallets :errors :extra-agents] nil)
      (assoc-in [:api-wallets :errors :default-agent] nil)))

(defn reset-api-wallets
  [state]
  (-> state
      (assoc-in [:api-wallets :extra-agents] [])
      (assoc-in [:api-wallets :default-agent-row] nil)
      (assoc-in [:api-wallets :owner-webdata2] nil)
      (assoc-in [:api-wallets :server-time-ms] nil)
      (assoc-in [:api-wallets :loading :extra-agents?] false)
      (assoc-in [:api-wallets :loading :default-agent?] false)
      (assoc-in [:api-wallets :errors :extra-agents] nil)
      (assoc-in [:api-wallets :errors :default-agent] nil)
      (assoc-in [:api-wallets :loaded-at-ms :extra-agents] nil)
      (assoc-in [:api-wallets :loaded-at-ms :default-agent] nil)))

(defn apply-api-wallets-extra-agents-success
  [state rows]
  (-> state
      (assoc-in [:api-wallets :extra-agents]
                (if (sequential? rows) (vec rows) []))
      (assoc-in [:api-wallets :loading :extra-agents?] false)
      (assoc-in [:api-wallets :errors :extra-agents] nil)
      (assoc-in [:api-wallets :loaded-at-ms :extra-agents] (.now js/Date))))

(defn apply-api-wallets-extra-agents-error
  [state err]
  (let [{:keys [message]} (normalized-error err)]
    (-> state
        (assoc-in [:api-wallets :loading :extra-agents?] false)
        (assoc-in [:api-wallets :errors :extra-agents] message))))

(defn apply-api-wallets-default-agent-success
  [state requested-address snapshot]
  (let [server-time-ms (some parse-ms
                             [(:serverTime snapshot)
                              (:server-time snapshot)])
        owner-address (account-context/owner-address state)]
    (if (= (some-> requested-address str str/lower-case)
           owner-address)
      (-> state
          (assoc-in [:api-wallets :owner-webdata2] snapshot)
          (assoc-in [:api-wallets :server-time-ms] server-time-ms)
          (assoc-in [:api-wallets :default-agent-row] (build-default-agent-row snapshot))
          (assoc-in [:api-wallets :loading :default-agent?] false)
          (assoc-in [:api-wallets :errors :default-agent] nil)
          (assoc-in [:api-wallets :loaded-at-ms :default-agent] (.now js/Date)))
      state)))

(defn apply-api-wallets-default-agent-error
  [state requested-address err]
  (let [{:keys [message]} (normalized-error err)
        owner-address (account-context/owner-address state)]
    (if (= (some-> requested-address str str/lower-case)
           owner-address)
      (-> state
          (assoc-in [:api-wallets :loading :default-agent?] false)
          (assoc-in [:api-wallets :errors :default-agent] message))
      state)))
