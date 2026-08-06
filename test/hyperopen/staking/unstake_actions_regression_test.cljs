(ns hyperopen.staking.unstake-actions-regression-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.context :as account-context]
            [hyperopen.staking.actions :as actions]
            [hyperopen.utils.formatting :as formatting]))

(def ^:private staking-not-ready-message
  "Staking account data is still loading. Please try again.")

(def ^:private master-staking-address
  "0x1111111111111111111111111111111111111111")

(def ^:private stale-selected-subaccount-address
  "0x2222222222222222222222222222222222222222")

(def ^:private undelegate-validator-address
  "0x3333333333333333333333333333333333333333")

(def ^:private other-validator-address
  "0x4444444444444444444444444444444444444444")

(defn- master-undelegate-state
  [delegations]
  {:wallet {:address master-staking-address}
   :account-context {:spectate-mode {:active? false
                                     :address nil}
                     :subaccounts {:selected-address stale-selected-subaccount-address
                                   :rows []}}
   :staking {:account-address master-staking-address
             :loaded-for {:delegations master-staking-address}
             :delegations delegations}
   :staking-ui {:undelegate-amount "101"
                :selected-validator undelegate-validator-address}})

(defn- canonical-undelegate-effects
  []
  [[:effects/save [:staking-ui :form-error] nil]
   [:effects/save [:staking-ui :submitting :undelegate?] true]
   [:effects/api-submit-staking-undelegate
    {:kind :undelegate
     :action {:type "tokenDelegate"
              :validator undelegate-validator-address
              :wei 10100000000
              :isUndelegate true}}]])

(defn- undelegate-guard-effects
  [message]
  [[:effects/save [:staking-ui :form-error] message]
   [:effects/save [:staking-ui :submitting :undelegate?] false]])

(deftest submit-staking-undelegate-uses-current-owner-delegation-despite-stale-selected-subaccount-test
  (let [effects (actions/submit-staking-undelegate
                 (master-undelegate-state
                  [{:validator undelegate-validator-address
                    :amount 101}]))]
    (is (= (canonical-undelegate-effects)
           effects))
    (is (= [[:effects/api-submit-staking-undelegate
             {:kind :undelegate
              :action {:type "tokenDelegate"
                       :validator undelegate-validator-address
                       :wei 10100000000
                       :isUndelegate true}}]]
           (filterv #(= :effects/api-submit-staking-undelegate (first %))
                    effects)))
    (is (not (some #{stale-selected-subaccount-address}
                   (tree-seq coll? seq effects))))))

(deftest submit-staking-undelegate-preserves-inspection-and-provenance-blocks-test
  (let [ready-state (master-undelegate-state
                     [{:validator undelegate-validator-address
                       :amount 101}])
        trader-address "0x5555555555555555555555555555555555555555"
        guarded-states [{:label "spectate"
                         :state (assoc-in ready-state
                                          [:account-context :spectate-mode]
                                          {:active? true
                                           :address trader-address})
                         :message account-context/spectate-mode-read-only-message}
                        {:label "trader portfolio"
                         :state (assoc ready-state
                                       :router {:path (str "/portfolio/trader/" trader-address)})
                         :message account-context/trader-portfolio-read-only-message}
                        {:label "saved account mismatch"
                         :state (assoc-in ready-state
                                          [:staking :account-address]
                                          stale-selected-subaccount-address)
                         :message staking-not-ready-message}
                        {:label "loaded-for mismatch"
                         :state (assoc-in ready-state
                                          [:staking :loaded-for :delegations]
                                          stale-selected-subaccount-address)
                         :message staking-not-ready-message}]]
    (doseq [{:keys [label state message]} guarded-states]
      (let [effects (actions/submit-staking-undelegate state)]
        (is (= (undelegate-guard-effects message)
               effects)
            label)
        (is (not-any? #(= :effects/api-submit-staking-undelegate (first %))
                      effects)
            (str label " must not submit"))))))

(deftest submit-staking-undelegate-enforces-selected-validator-lock-at-explicit-now-test
  (let [now-ms 1700000000000
        future-lock-ms (inc now-ms)
        selected-row {:validator undelegate-validator-address
                      :amount 101}
        other-future-row {:validator other-validator-address
                          :amount 999
                          :locked-until-timestamp future-lock-ms}
        future-effects (apply actions/submit-staking-undelegate
                              [(master-undelegate-state
                                [(assoc selected-row :locked-until-timestamp future-lock-ms)
                                 other-future-row])
                               now-ms])]
    (is (= (undelegate-guard-effects
            (str "This delegation is locked until "
                 (formatting/format-local-date-time future-lock-ms)
                 "."))
           future-effects))
    (is (not-any? #(= :effects/api-submit-staking-undelegate (first %))
                  future-effects))
    (doseq [lock-ms [now-ms (dec now-ms) nil]]
      (is (= (canonical-undelegate-effects)
             (apply actions/submit-staking-undelegate
                    [(master-undelegate-state
                      [(assoc selected-row :locked-until-timestamp lock-ms)
                       other-future-row])
                     now-ms]))
          (str "selected lock " lock-ms " allows at the exact boundary")))))

(deftest submit-staking-undelegate-is-idempotent-while-submission-is-pending-test
  (let [state (assoc-in (master-undelegate-state
                         [{:validator undelegate-validator-address
                           :amount 101}])
                        [:staking-ui :submitting :undelegate?]
                        true)
        effects (actions/submit-staking-undelegate state 1700000000000)]
    (is (= [] effects))
    (is (not-any? #(= :effects/api-submit-staking-undelegate (first %))
                  effects))
    (is (not-any? #(= :effects/save (first %))
                  effects))))
