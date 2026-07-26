(ns hyperopen.runtime.effect-adapters.referrals
  (:require [nexus.registry :as nxr]
            [hyperopen.account.context :as account-context]
            [hyperopen.api.default :as api]
            [hyperopen.api.projections :as api-projections]
            [hyperopen.api.trading :as trading-api]
            [hyperopen.referrals.effects :as referrals-effects]
            [hyperopen.runtime.effect-adapters.common :as common]))

(defn api-fetch-referral-effect
  [_ store address]
  (referrals-effects/api-fetch-referral!
   {:store store
    :address address
    :request-referral! api/request-referral!
    :begin-referrals-load api-projections/begin-referrals-load
    :apply-referrals-success api-projections/apply-referrals-success
    :apply-referrals-error api-projections/apply-referrals-error}))

(defn- active-owner-address?
  [store owner]
  (= (account-context/normalize-address owner)
     (account-context/owner-address @store)))

(defn- refresh-user-fees!
  [store owner]
  (let [owner* (account-context/normalize-address owner)]
    (if-not owner*
      (js/Promise.resolve nil)
      (do
        (swap! store api-projections/begin-user-fees-load owner*)
        (-> (api/request-user-fees! owner*
                                     {:priority :high
                                      :dedupe-key [:referrals :user-fees owner*]})
            (.then (fn [payload]
                     (when (active-owner-address? store owner*)
                       (swap! store api-projections/apply-user-fees-success owner* payload))
                     payload))
            (.catch (fn [err]
                      (when (active-owner-address? store owner*)
                        (swap! store api-projections/apply-user-fees-error owner* err))
                      nil)))))))

(defn- refresh-spot-balances!
  [store owner]
  (let [owner* (account-context/normalize-address owner)]
    (if-not owner*
      (js/Promise.resolve nil)
      (do
        (swap! store api-projections/begin-spot-balances-load)
        (-> (api/request-spot-clearinghouse-state! owner*
                                                   {:priority :high
                                                    :dedupe-key [:referrals :spot-balances owner*]})
            (.then (fn [payload]
                     (when (active-owner-address? store owner*)
                       (swap! store api-projections/apply-spot-balances-success payload))
                     payload))
            (.catch (fn [err]
                      (when (active-owner-address? store owner*)
                        (swap! store api-projections/apply-spot-balances-error err))
                      nil)))))))

(defn- submit-referral-effect
  [store request submit-kind submit! show-toast!]
  (referrals-effects/api-submit-referral-mutation!
   {:store store
    :request request
    :submit-kind submit-kind
    :submit! submit!
    :dispatch! nxr/dispatch
    :refresh-user-fees! refresh-user-fees!
    :refresh-spot-balances! refresh-spot-balances!
    :exchange-response-error common/exchange-response-error
    :runtime-error-message common/runtime-error-message
    :show-toast! show-toast!}))

(defn api-set-referrer-effect
  ([_ store request]
   (api-set-referrer-effect nil store request {}))
  ([_ store request {:keys [show-toast!]
                     :or {show-toast! (fn [_store _kind _message] nil)}}]
   (submit-referral-effect store
                           request
                           :set-referrer
                           trading-api/set-referrer!
                           show-toast!)))

(defn api-register-referrer-effect
  ([_ store request]
   (api-register-referrer-effect nil store request {}))
  ([_ store request {:keys [show-toast!]
                     :or {show-toast! (fn [_store _kind _message] nil)}}]
   (submit-referral-effect store
                           request
                           :register-referrer
                           trading-api/register-referrer!
                           show-toast!)))

(defn api-claim-referral-rewards-effect
  ([_ store request]
   (api-claim-referral-rewards-effect nil store request {}))
  ([_ store request {:keys [show-toast!]
                     :or {show-toast! (fn [_store _kind _message] nil)}}]
   (submit-referral-effect store
                           request
                           :claim-rewards
                           trading-api/claim-rewards!
                           show-toast!)))
