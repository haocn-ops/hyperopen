(ns hyperopen.runtime.effect-adapters.staking
  (:require [nexus.registry :as nxr]
            [hyperopen.api.default :as api]
            [hyperopen.api.projections :as api-projections]
            [hyperopen.runtime.effect-adapters.common :as common]
            [hyperopen.staking.effects :as staking-effects]))

(defn api-fetch-staking-validator-summaries-effect
  [_ store]
  (staking-effects/api-fetch-staking-validator-summaries!
   {:store store
    :request-staking-validator-summaries! api/request-staking-validator-summaries!
    :begin-staking-validator-summaries-load api-projections/begin-staking-validator-summaries-load
    :apply-staking-validator-summaries-success api-projections/apply-staking-validator-summaries-success
    :apply-staking-validator-summaries-error api-projections/apply-staking-validator-summaries-error}))

(defn api-fetch-staking-delegator-summary-effect
  [_ store address]
  (staking-effects/api-fetch-staking-delegator-summary!
   {:store store
    :address address
    :request-staking-delegator-summary! api/request-staking-delegator-summary!
    :begin-staking-delegator-summary-load api-projections/begin-staking-delegator-summary-load
    :apply-staking-delegator-summary-success api-projections/apply-staking-delegator-summary-success
    :apply-staking-delegator-summary-error api-projections/apply-staking-delegator-summary-error}))

(defn api-fetch-staking-delegations-effect
  [_ store address]
  (staking-effects/api-fetch-staking-delegations!
   {:store store
    :address address
    :request-staking-delegations! api/request-staking-delegations!
    :begin-staking-delegations-load api-projections/begin-staking-delegations-load
    :apply-staking-delegations-success api-projections/apply-staking-delegations-success
    :apply-staking-delegations-error api-projections/apply-staking-delegations-error}))

(defn api-fetch-staking-rewards-effect
  [_ store address]
  (staking-effects/api-fetch-staking-rewards!
   {:store store
    :address address
    :request-staking-delegator-rewards! api/request-staking-delegator-rewards!
    :begin-staking-rewards-load api-projections/begin-staking-rewards-load
    :apply-staking-rewards-success api-projections/apply-staking-rewards-success
    :apply-staking-rewards-error api-projections/apply-staking-rewards-error}))

(defn api-fetch-staking-history-effect
  [_ store address]
  (staking-effects/api-fetch-staking-history!
   {:store store
    :address address
    :request-staking-delegator-history! api/request-staking-delegator-history!
    :begin-staking-history-load api-projections/begin-staking-history-load
    :apply-staking-history-success api-projections/apply-staking-history-success
    :apply-staking-history-error api-projections/apply-staking-history-error}))

(defn api-fetch-staking-spot-state-effect
  [_ store address]
  (staking-effects/api-fetch-staking-spot-state!
   {:store store
    :address address
    :request-spot-clearinghouse-state! api/request-spot-clearinghouse-state!
    :begin-spot-balances-load api-projections/begin-spot-balances-load
    :apply-spot-balances-success api-projections/apply-spot-balances-success
    :apply-spot-balances-error api-projections/apply-spot-balances-error}))

(defn api-submit-staking-deposit-effect
  ([_ store request]
   (api-submit-staking-deposit-effect nil store request {}))
  ([_ store request {:keys [show-toast!]
                     :or {show-toast! (fn [_store _kind _message] nil)}}]
   (staking-effects/api-submit-staking-deposit!
    {:store store
     :request request
     :dispatch! nxr/dispatch
     :exchange-response-error common/exchange-response-error
     :runtime-error-message common/runtime-error-message
     :show-toast! show-toast!})))

(defn api-submit-staking-withdraw-effect
  ([_ store request]
   (api-submit-staking-withdraw-effect nil store request {}))
  ([_ store request {:keys [show-toast!]
                     :or {show-toast! (fn [_store _kind _message] nil)}}]
   (staking-effects/api-submit-staking-withdraw!
    {:store store
     :request request
     :dispatch! nxr/dispatch
     :exchange-response-error common/exchange-response-error
     :runtime-error-message common/runtime-error-message
     :show-toast! show-toast!})))

(defn api-submit-staking-delegate-effect
  ([_ store request]
   (api-submit-staking-delegate-effect nil store request {}))
  ([_ store request {:keys [show-toast!]
                     :or {show-toast! (fn [_store _kind _message] nil)}}]
   (staking-effects/api-submit-staking-delegate!
    {:store store
     :request request
     :dispatch! nxr/dispatch
     :exchange-response-error common/exchange-response-error
     :runtime-error-message common/runtime-error-message
     :show-toast! show-toast!})))

(defn api-submit-staking-undelegate-effect
  ([_ store request]
   (api-submit-staking-undelegate-effect nil store request {}))
  ([_ store request {:keys [show-toast!]
                     :or {show-toast! (fn [_store _kind _message] nil)}}]
   (staking-effects/api-submit-staking-undelegate!
    {:store store
     :request request
     :dispatch! nxr/dispatch
     :exchange-response-error common/exchange-response-error
     :runtime-error-message common/runtime-error-message
     :show-toast! show-toast!})))
