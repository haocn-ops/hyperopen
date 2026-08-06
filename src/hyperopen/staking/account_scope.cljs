(ns hyperopen.staking.account-scope
  (:require [hyperopen.account.context :as account-context]))

(def cleared-user-projections
  [[[:staking :delegator-summary] nil]
   [[:staking :delegations] []]
   [[:staking :rewards] []]
   [[:staking :history] []]
   [[:staking :spot-state] nil]
   [[:staking :loading :delegator-summary] false]
   [[:staking :loading :delegations] false]
   [[:staking :loading :rewards] false]
   [[:staking :loading :history] false]
   [[:staking :loading :spot-state] false]
   [[:staking :errors :delegator-summary] nil]
   [[:staking :errors :delegations] nil]
   [[:staking :errors :rewards] nil]
   [[:staking :errors :history] nil]
   [[:staking :errors :spot-state] nil]
   [[:staking :loaded-for :delegator-summary] nil]
   [[:staking :loaded-for :delegations] nil]
   [[:staking :loaded-for :rewards] nil]
   [[:staking :loaded-for :history] nil]
   [[:staking :loaded-for :spot-state] nil]])

(defn current-address?
  [state address]
  (and (= address (get-in state [:staking :account-address]))
       (= address (account-context/native-staking-account-address state))))

(defn resource-ready?
  [state resource]
  (let [address (account-context/native-staking-account-address state)]
    (and (current-address? state address)
         (= address (get-in state [:staking :loaded-for resource])))))

(defn delegation-row-by-validator
  [state validator]
  (let [validator* (account-context/normalize-address validator)]
    (some (fn [row]
            (when (= validator* (account-context/normalize-address (:validator row)))
              row))
          (get-in state [:staking :delegations]))))

(defn delegation-locked-after?
  [delegation now-ms]
  (let [locked-until-timestamp (:locked-until-timestamp delegation)]
    (and (number? locked-until-timestamp)
         (js/isFinite locked-until-timestamp)
         (number? now-ms)
         (js/isFinite now-ms)
         (> locked-until-timestamp now-ms))))

(defn mutations-blocked-message
  [state]
  (cond
    (account-context/spectate-mode-active? state)
    account-context/spectate-mode-read-only-message

    (account-context/trader-portfolio-route-active? state)
    account-context/trader-portfolio-read-only-message

    :else
    nil))
