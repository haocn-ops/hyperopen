(ns hyperopen.api.projections.portfolio
  (:require [hyperopen.account.context :as account-context]
            [hyperopen.api.errors :as api-errors]))

(defn- normalized-error
  [err]
  (api-errors/normalize-error err))

(defn begin-portfolio-load
  ([state]
   (begin-portfolio-load state nil))
  ([state address]
   (-> state
       (assoc-in [:portfolio :loading?] true)
       (assoc-in [:portfolio :loading-for-address]
                 (account-context/normalize-address address))
       (assoc-in [:portfolio :error] nil)
       (assoc-in [:portfolio :error-for-address] nil))))

(defn apply-portfolio-success
  ([state summary-by-key]
   (apply-portfolio-success state nil summary-by-key))
  ([state address summary-by-key]
   (-> state
       (assoc-in [:portfolio :summary-by-key] (or summary-by-key {}))
       (assoc-in [:portfolio :loading?] false)
       (assoc-in [:portfolio :loading-for-address] nil)
       (assoc-in [:portfolio :error] nil)
       (assoc-in [:portfolio :error-for-address] nil)
       (assoc-in [:portfolio :loaded-at-ms] (.now js/Date))
       (assoc-in [:portfolio :loaded-for-address]
                 (account-context/normalize-address address)))))

(defn apply-portfolio-error
  ([state err]
   (apply-portfolio-error state nil err))
  ([state address err]
   (let [{:keys [message]} (normalized-error err)]
     (-> state
         (assoc-in [:portfolio :loading?] false)
         (assoc-in [:portfolio :loading-for-address] nil)
         (assoc-in [:portfolio :error] message)
         (assoc-in [:portfolio :error-for-address]
                   (account-context/normalize-address address))))))

(defn- benchmark-address
  [address]
  (account-context/normalize-address address))

(defn begin-trader-benchmark-portfolio-load
  [state address]
  (if-let [address* (benchmark-address address)]
    (-> state
        (assoc-in [:portfolio :loading :trader-benchmarks-by-address address*] true)
        (assoc-in [:portfolio :errors :trader-benchmarks-by-address address*] nil))
    state))

(defn apply-trader-benchmark-portfolio-success
  [state address summary-by-key]
  (if-let [address* (benchmark-address address)]
    (-> state
        (assoc-in [:portfolio :trader-benchmarks-by-address address* :summary-by-key]
                  (or summary-by-key {}))
        (assoc-in [:portfolio :trader-benchmarks-by-address address* :loaded-at-ms] (.now js/Date))
        (assoc-in [:portfolio :loading :trader-benchmarks-by-address address*] false)
        (assoc-in [:portfolio :errors :trader-benchmarks-by-address address*] nil))
    state))

(defn apply-trader-benchmark-portfolio-error
  [state address err]
  (if-let [address* (benchmark-address address)]
    (let [{:keys [message]} (normalized-error err)]
      (-> state
          (assoc-in [:portfolio :loading :trader-benchmarks-by-address address*] false)
          (assoc-in [:portfolio :errors :trader-benchmarks-by-address address*] message)))
    state))
