(ns hyperopen.api.projections.staking-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.api.projections.staking :as staking]))

(def ^:private list-projection-cases
  [{:label "validator summaries"
    :begin staking/begin-staking-validator-summaries-load
    :success staking/apply-staking-validator-summaries-success
    :failure staking/apply-staking-validator-summaries-error
    :value-path [:staking :validator-summaries]
    :loading-path [:staking :loading :validator-summaries]
    :error-path [:staking :errors :validator-summaries]
    :loaded-path [:staking :loaded-at-ms :validator-summaries]
    :rows [{:validator "alice"}]}
   {:label "delegations"
    :begin staking/begin-staking-delegations-load
    :success staking/apply-staking-delegations-success
    :failure staking/apply-staking-delegations-error
    :value-path [:staking :delegations]
    :loading-path [:staking :loading :delegations]
    :error-path [:staking :errors :delegations]
    :loaded-path [:staking :loaded-at-ms :delegations]
    :rows [{:validator "alice" :amount "10"}]}
   {:label "rewards"
    :begin staking/begin-staking-rewards-load
    :success staking/apply-staking-rewards-success
    :failure staking/apply-staking-rewards-error
    :value-path [:staking :rewards]
    :loading-path [:staking :loading :rewards]
    :error-path [:staking :errors :rewards]
    :loaded-path [:staking :loaded-at-ms :rewards]
    :rows [{:time 1 :amount "0.1"}]}
   {:label "history"
    :begin staking/begin-staking-history-load
    :success staking/apply-staking-history-success
    :failure staking/apply-staking-history-error
    :value-path [:staking :history]
    :loading-path [:staking :loading :history]
    :error-path [:staking :errors :history]
    :loaded-path [:staking :loaded-at-ms :history]
    :rows [{:time 1 :action "delegate"}]}])

(deftest staking-list-projections-track-loading-success-and-error-test
  (doseq [{:keys [label begin success failure value-path loading-path error-path
                  loaded-path rows]}
          list-projection-cases]
    (testing label
      (let [state (-> {}
                      (assoc-in value-path [:stale])
                      (assoc-in loading-path false)
                      (assoc-in error-path "stale"))
            loading (begin state)
            success-state (success loading rows)
            defaulted-state (success loading nil)
            failed-state (failure loading (js/Error. (str label " fail")))]
        (is (= true (get-in loading loading-path)))
        (is (nil? (get-in loading error-path)))
        (is (= rows (get-in success-state value-path)))
        (is (= false (get-in success-state loading-path)))
        (is (nil? (get-in success-state error-path)))
        (is (number? (get-in success-state loaded-path)))
        (is (= [] (get-in defaulted-state value-path)))
        (is (= false (get-in failed-state loading-path)))
        (is (= (str "Error: " label " fail")
               (get-in failed-state error-path)))))))

(deftest staking-delegator-summary-projection-tracks-map-payloads-test
  (let [summary {:delegated "25"}
        state {:staking {:delegator-summary {:stale true}
                         :loading {:delegator-summary false}
                         :errors {:delegator-summary "stale"}}}
        loading (staking/begin-staking-delegator-summary-load state)
        success (staking/apply-staking-delegator-summary-success loading summary)
        rejected (staking/apply-staking-delegator-summary-success loading [])
        failed (staking/apply-staking-delegator-summary-error
                loading
                (js/Error. "delegator summary fail"))]
    (is (= true (get-in loading [:staking :loading :delegator-summary])))
    (is (nil? (get-in loading [:staking :errors :delegator-summary])))
    (is (= summary (get-in success [:staking :delegator-summary])))
    (is (= false (get-in success [:staking :loading :delegator-summary])))
    (is (nil? (get-in success [:staking :errors :delegator-summary])))
    (is (number? (get-in success [:staking :loaded-at-ms :delegator-summary])))
    (is (nil? (get-in rejected [:staking :delegator-summary])))
    (is (= false (get-in failed [:staking :loading :delegator-summary])))
    (is (= "Error: delegator summary fail"
           (get-in failed [:staking :errors :delegator-summary])))))
