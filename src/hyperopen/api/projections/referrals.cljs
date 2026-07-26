(ns hyperopen.api.projections.referrals
  (:require [hyperopen.account.context :as account-context]
            [hyperopen.api.errors :as api-errors]))

(defn begin-load
  [state address]
  (-> state
      (assoc-in [:referrals :loading?] true)
      (assoc-in [:referrals :loading-for-address]
                (account-context/normalize-address address))
      (assoc-in [:referrals :error] nil)))

(defn- request-address-matches?
  [state address]
  (= (account-context/normalize-address address)
     (get-in state [:referrals :loading-for-address])))

(defn apply-success
  [state address payload]
  (if-not (request-address-matches? state address)
    state
    (-> state
        (assoc-in [:referrals :raw] payload)
        (assoc-in [:referrals :loading?] false)
        (assoc-in [:referrals :loading-for-address] nil)
        (assoc-in [:referrals :error] nil)
        (assoc-in [:referrals :loaded-at-ms] (.now js/Date))
        (assoc-in [:referrals :loaded-for-address]
                  (account-context/normalize-address address)))))

(defn apply-error
  [state address err]
  (if-not (request-address-matches? state address)
    state
    (let [{:keys [message]} (api-errors/normalize-error err)]
      (-> state
          (assoc-in [:referrals :loading?] false)
          (assoc-in [:referrals :loading-for-address] nil)
          (assoc-in [:referrals :error] message)
          (assoc-in [:referrals :error-for-address]
                    (account-context/normalize-address address))))))
