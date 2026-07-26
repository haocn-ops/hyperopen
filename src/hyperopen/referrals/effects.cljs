(ns hyperopen.referrals.effects
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]
            [hyperopen.api.promise-effects :as promise-effects]
            [hyperopen.api.trading :as trading-api]))

(defn- resolve-address
  [store address]
  (or (account-context/normalize-address address)
      (account-context/effective-account-address @store)))

(defn api-fetch-referral!
  [{:keys [store
           address
           request-referral!
           begin-referrals-load
           apply-referrals-success
           apply-referrals-error]}]
  (let [address* (resolve-address store address)]
    (if-not (seq address*)
      (js/Promise.resolve nil)
      (do
        (swap! store begin-referrals-load address*)
        (-> (request-referral! address* {:priority :high})
            (.then (promise-effects/apply-success-and-return
                    store
                    apply-referrals-success
                    address*))
            (.catch (promise-effects/apply-error-and-reject
                     store
                     apply-referrals-error
                     address*)))))))

(defn- fallback-exchange-response-error
  [resp]
  (or (:error resp)
      (:message resp)
      (:response resp)
      "Unknown exchange error"))

(defn- fallback-runtime-error-message
  [err]
  (or (some-> err .-message)
      (str err)))

(defn- label-for-kind
  [submit-kind]
  (case submit-kind
    :set-referrer "Set referrer"
    :register-referrer "Register referral code"
    :claim-rewards "Claim rewards"
    "Referral action"))

(defn- success-message-for-kind
  [submit-kind]
  (case submit-kind
    :set-referrer "Referral updated."
    :register-referrer "Referral code registered."
    :claim-rewards "Rewards claimed."
    "Referral action submitted."))

(defn- set-submit-error!
  [store show-toast! message]
  (let [message* (str/trim (str message))]
    (swap! store
           (fn [state]
             (-> state
                 (assoc-in [:referrals-ui :submitting?] nil)
                 (assoc-in [:referrals-ui :last-error]
                           (if (seq message*)
                             message*
                             "Unable to submit referral action.")))))
    (show-toast! store
                 :error
                 (or (not-empty message*) "Unable to submit referral action."))))

(defn- set-submit-success!
  [store]
  (swap! store
         (fn [state]
           (-> state
               (assoc-in [:referrals-ui :submitting?] nil)
               (assoc-in [:referrals-ui :active-modal] nil)
               (assoc-in [:referrals-ui :pending-code] nil)
               (assoc-in [:referrals-ui :last-error] nil)))))

(defn- invoke-submit!
  [submit! store owner code]
  (if (seq code)
    (submit! store owner code)
    (submit! store owner)))

(defn- invoke-refresh!
  [refresh! store owner]
  (when (and (fn? refresh!)
             (seq owner))
    (try
      (let [result (refresh! store owner)]
        (when (and result
                   (fn? (.-catch result)))
          (.catch result (fn [_err] nil))))
      (catch :default _
        nil))))

(defn- refresh-after-submit!
  [store dispatch! owner submit-kind refresh-user-fees! refresh-spot-balances!]
  (when (and (fn? dispatch!)
             (seq owner))
    (dispatch! store nil [[:effects/api-fetch-referral owner]]))
  (when (contains? #{:set-referrer :claim-rewards} submit-kind)
    (invoke-refresh! refresh-user-fees! store owner))
  (when (= :claim-rewards submit-kind)
    (invoke-refresh! refresh-spot-balances! store owner)))

(defn api-submit-referral-mutation!
  [{:keys [store
           request
           submit-kind
           submit!
           dispatch!
           refresh-user-fees!
           refresh-spot-balances!
           exchange-response-error
           runtime-error-message
           show-toast!]
    :or {submit! trading-api/set-referrer!
         exchange-response-error fallback-exchange-response-error
         runtime-error-message fallback-runtime-error-message
         show-toast! (fn [_store _kind _message] nil)}}]
  (let [state @store
        owner (or (account-context/normalize-address (:owner request))
                  (account-context/owner-address state))
        code (:code request)
        label (label-for-kind submit-kind)]
    (cond
      (nil? owner)
      (set-submit-error! store show-toast! "Connect your wallet before submitting referral actions.")

      (not (fn? submit!))
      (set-submit-error! store show-toast! "Referral action is unavailable.")

      :else
      (-> (invoke-submit! submit! store owner code)
          (.then (fn [resp]
                   (if (= "ok" (:status resp))
                     (do
                       (set-submit-success! store)
                       (show-toast! store :success (success-message-for-kind submit-kind))
                       (refresh-after-submit! store
                                              dispatch!
                                              owner
                                              submit-kind
                                              refresh-user-fees!
                                              refresh-spot-balances!)
                       resp)
                     (let [error-text (str/trim (str (exchange-response-error resp)))
                           message (str label " failed: "
                                        (if (seq error-text)
                                          error-text
                                          "Unknown exchange error"))]
                       (set-submit-error! store show-toast! message)
                       resp))))
          (.catch (fn [err]
                    (let [error-text (str/trim (str (runtime-error-message err)))
                          message (str label " failed: "
                                       (if (seq error-text)
                                         error-text
                                         "Unknown runtime error"))]
                      (set-submit-error! store show-toast! message))))))))
