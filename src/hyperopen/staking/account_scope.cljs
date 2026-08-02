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
