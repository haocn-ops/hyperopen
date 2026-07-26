(ns hyperopen.api.endpoints.account.subaccounts
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]
            [hyperopen.api.request-policy :as request-policy]))

(defn- row-value
  [row k kebab-k string-k]
  (or (get row k)
      (get row kebab-k)
      (get row string-k)))

(defn- normalize-subaccount-row
  [row]
  (when (map? row)
    (let [sub-account-user (account-context/normalize-address
                            (row-value row :subAccountUser :sub-account-user "subAccountUser"))
          master (account-context/normalize-address
                  (row-value row :master :master "master"))]
      (when sub-account-user
        (cond-> {:sub-account-user sub-account-user
                 :master master
                 :clearinghouse-state (row-value row
                                                  :clearinghouseState
                                                  :clearinghouse-state
                                                  "clearinghouseState")
                 :spot-state (row-value row
                                        :spotState
                                        :spot-state
                                        "spotState")}
          (some? (row-value row :name :name "name"))
          (assoc :name (some-> (row-value row :name :name "name")
                               str
                               str/trim)))))))

(defn- subaccount-rows
  "Extracts the row collection from a `subAccounts` response. Hyperliquid
   returns a bare array, but tolerate a `{:subAccounts [...]}` wrapper too so a
   proxy/gateway reshaping the payload can't silently collapse the page to
   \"No subaccounts found.\""
  [payload]
  (let [rows (cond
               (sequential? payload) payload
               (map? payload) (some (fn [k] (get payload k))
                                    [:subAccounts :sub-accounts "subAccounts" "sub-accounts"])
               :else nil)]
    (when (sequential? rows) rows)))

(defn normalize-subaccounts
  [payload]
  (->> (subaccount-rows payload)
       (filter map?)
       (keep normalize-subaccount-row)
       vec))

(defn request-sub-accounts!
  [post-info! address opts]
  (if-let [requested-address (account-context/normalize-address address)]
    (let [opts* (request-policy/apply-info-request-policy
                 :sub-accounts
                 (merge {:priority :high
                         :dedupe-key [:sub-accounts requested-address]}
                        opts))]
      (-> (post-info! {"type" "subAccounts"
                       "user" requested-address}
                      opts*)
          (.then normalize-subaccounts)))
    (js/Promise.resolve [])))
