(ns hyperopen.runtime.effect-adapters.staking-test
  (:require [cljs.test :refer-macros [deftest is]]
            [nexus.registry :as nxr]
            [hyperopen.api.default :as api]
            [hyperopen.api.projections :as api-projections]
            [hyperopen.runtime.effect-adapters :as effect-adapters]
            [hyperopen.runtime.effect-adapters.common :as common]
            [hyperopen.runtime.effect-adapters.staking :as staking-adapters]
            [hyperopen.staking.effects :as staking-effects]))

(deftest facade-staking-fetch-adapters-delegate-to-staking-module-test
  (is (identical? staking-adapters/api-fetch-staking-validator-summaries-effect
                  effect-adapters/api-fetch-staking-validator-summaries-effect))
  (is (identical? staking-adapters/api-fetch-staking-delegator-summary-effect
                  effect-adapters/api-fetch-staking-delegator-summary-effect))
  (is (identical? staking-adapters/api-fetch-staking-delegations-effect
                  effect-adapters/api-fetch-staking-delegations-effect))
  (is (identical? staking-adapters/api-fetch-staking-rewards-effect
                  effect-adapters/api-fetch-staking-rewards-effect))
  (is (identical? staking-adapters/api-fetch-staking-history-effect
                  effect-adapters/api-fetch-staking-history-effect))
  (is (identical? staking-adapters/api-fetch-staking-spot-state-effect
                  effect-adapters/api-fetch-staking-spot-state-effect)))

(deftest staking-fetch-adapters-wire-api-and-projection-dependencies-test
  (let [store (atom {})
        address "0xabc123"
        calls (atom [])]
    (with-redefs [staking-effects/api-fetch-staking-validator-summaries!
                  (fn [deps]
                    (swap! calls conj [:validator-summaries deps])
                    :validator-summaries-result)
                  staking-effects/api-fetch-staking-delegator-summary!
                  (fn [deps]
                    (swap! calls conj [:delegator-summary deps])
                    :delegator-summary-result)
                  staking-effects/api-fetch-staking-delegations!
                  (fn [deps]
                    (swap! calls conj [:delegations deps])
                    :delegations-result)
                  staking-effects/api-fetch-staking-rewards!
                  (fn [deps]
                    (swap! calls conj [:rewards deps])
                    :rewards-result)
                  staking-effects/api-fetch-staking-history!
                  (fn [deps]
                    (swap! calls conj [:history deps])
                    :history-result)
                  staking-effects/api-fetch-staking-spot-state!
                  (fn [deps]
                    (swap! calls conj [:spot-state deps])
                    :spot-state-result)]
      (is (= :validator-summaries-result
             (staking-adapters/api-fetch-staking-validator-summaries-effect nil store)))
      (is (= :delegator-summary-result
             (staking-adapters/api-fetch-staking-delegator-summary-effect nil store address)))
      (is (= :delegations-result
             (staking-adapters/api-fetch-staking-delegations-effect nil store address)))
      (is (= :rewards-result
             (staking-adapters/api-fetch-staking-rewards-effect nil store address)))
      (is (= :history-result
             (staking-adapters/api-fetch-staking-history-effect nil store address)))
      (is (= :spot-state-result
             (staking-adapters/api-fetch-staking-spot-state-effect nil store address))))
    (is (= [:validator-summaries
            :delegator-summary
            :delegations
            :rewards
            :history
            :spot-state]
           (mapv first @calls)))
    (let [captured (into {} (map (juxt first second) @calls))]
      (is (= store (get-in captured [:validator-summaries :store])))
      (is (identical? api/request-staking-validator-summaries!
                      (get-in captured [:validator-summaries :request-staking-validator-summaries!])))
      (is (identical? api-projections/begin-staking-validator-summaries-load
                      (get-in captured [:validator-summaries :begin-staking-validator-summaries-load])))
      (is (identical? api-projections/apply-staking-validator-summaries-success
                      (get-in captured [:validator-summaries :apply-staking-validator-summaries-success])))
      (is (identical? api-projections/apply-staking-validator-summaries-error
                      (get-in captured [:validator-summaries :apply-staking-validator-summaries-error])))

      (is (= store (get-in captured [:delegator-summary :store])))
      (is (= address (get-in captured [:delegator-summary :address])))
      (is (identical? api/request-staking-delegator-summary!
                      (get-in captured [:delegator-summary :request-staking-delegator-summary!])))
      (is (identical? api-projections/begin-staking-delegator-summary-load
                      (get-in captured [:delegator-summary :begin-staking-delegator-summary-load])))
      (is (identical? api-projections/apply-staking-delegator-summary-success
                      (get-in captured [:delegator-summary :apply-staking-delegator-summary-success])))
      (is (identical? api-projections/apply-staking-delegator-summary-error
                      (get-in captured [:delegator-summary :apply-staking-delegator-summary-error])))

      (is (= store (get-in captured [:delegations :store])))
      (is (= address (get-in captured [:delegations :address])))
      (is (identical? api/request-staking-delegations!
                      (get-in captured [:delegations :request-staking-delegations!])))
      (is (identical? api-projections/begin-staking-delegations-load
                      (get-in captured [:delegations :begin-staking-delegations-load])))
      (is (identical? api-projections/apply-staking-delegations-success
                      (get-in captured [:delegations :apply-staking-delegations-success])))
      (is (identical? api-projections/apply-staking-delegations-error
                      (get-in captured [:delegations :apply-staking-delegations-error])))

      (is (= store (get-in captured [:rewards :store])))
      (is (= address (get-in captured [:rewards :address])))
      (is (identical? api/request-staking-delegator-rewards!
                      (get-in captured [:rewards :request-staking-delegator-rewards!])))
      (is (identical? api-projections/begin-staking-rewards-load
                      (get-in captured [:rewards :begin-staking-rewards-load])))
      (is (identical? api-projections/apply-staking-rewards-success
                      (get-in captured [:rewards :apply-staking-rewards-success])))
      (is (identical? api-projections/apply-staking-rewards-error
                      (get-in captured [:rewards :apply-staking-rewards-error])))

      (is (= store (get-in captured [:history :store])))
      (is (= address (get-in captured [:history :address])))
      (is (identical? api/request-staking-delegator-history!
                      (get-in captured [:history :request-staking-delegator-history!])))
      (is (identical? api-projections/begin-staking-history-load
                      (get-in captured [:history :begin-staking-history-load])))
      (is (identical? api-projections/apply-staking-history-success
                      (get-in captured [:history :apply-staking-history-success])))
      (is (identical? api-projections/apply-staking-history-error
                      (get-in captured [:history :apply-staking-history-error])))

      (is (= store (get-in captured [:spot-state :store])))
      (is (= address (get-in captured [:spot-state :address])))
      (is (identical? api/request-spot-clearinghouse-state!
                      (get-in captured [:spot-state :request-spot-clearinghouse-state!])))
      (is (identical? api-projections/begin-spot-balances-load
                      (get-in captured [:spot-state :begin-spot-balances-load])))
      (is (identical? api-projections/apply-spot-balances-success
                      (get-in captured [:spot-state :apply-spot-balances-success])))
      (is (identical? api-projections/apply-spot-balances-error
                      (get-in captured [:spot-state :apply-spot-balances-error])))))

(deftest staking-submit-adapters-inject-default-and-custom-toast-seams-test
  (let [store (atom {})
        request {:validator "0xvalidator"
                 :amount "10"}
        calls (atom [])
        custom-show-toast! (fn [& _] :custom-toast)]
    (with-redefs [staking-effects/api-submit-staking-deposit!
                  (fn [deps]
                    (swap! calls conj [:deposit deps])
                    :deposit-result)
                  staking-effects/api-submit-staking-withdraw!
                  (fn [deps]
                    (swap! calls conj [:withdraw deps])
                    :withdraw-result)
                  staking-effects/api-submit-staking-delegate!
                  (fn [deps]
                    (swap! calls conj [:delegate deps])
                    :delegate-result)
                  staking-effects/api-submit-staking-undelegate!
                  (fn [deps]
                    (swap! calls conj [:undelegate deps])
                    :undelegate-result)]
      (is (= :deposit-result
             (staking-adapters/api-submit-staking-deposit-effect nil store request)))
      (is (= :deposit-result
             (staking-adapters/api-submit-staking-deposit-effect
              nil
              store
              request
              {:show-toast! custom-show-toast!})))
      (is (= :withdraw-result
             (staking-adapters/api-submit-staking-withdraw-effect nil store request)))
      (is (= :withdraw-result
             (staking-adapters/api-submit-staking-withdraw-effect
              nil
              store
              request
              {:show-toast! custom-show-toast!})))
      (is (= :delegate-result
             (staking-adapters/api-submit-staking-delegate-effect nil store request)))
      (is (= :delegate-result
             (staking-adapters/api-submit-staking-delegate-effect
              nil
              store
              request
              {:show-toast! custom-show-toast!})))
      (is (= :undelegate-result
             (staking-adapters/api-submit-staking-undelegate-effect nil store request)))
      (is (= :undelegate-result
             (staking-adapters/api-submit-staking-undelegate-effect
              nil
              store
              request
              {:show-toast! custom-show-toast!}))))
    (is (= [:deposit
            :deposit
            :withdraw
            :withdraw
            :delegate
            :delegate
            :undelegate
            :undelegate]
           (mapv first @calls)))
    (let [captured (group-by first @calls)]
      (doseq [kind [:deposit :withdraw :delegate :undelegate]]
        (let [[[_ default-deps] [_ custom-deps]] (get captured kind)]
          (is (= store (:store default-deps)))
          (is (= request (:request default-deps)))
          (is (identical? nxr/dispatch (:dispatch! default-deps)))
          (is (identical? common/exchange-response-error
                          (:exchange-response-error default-deps)))
          (is (identical? common/runtime-error-message
                          (:runtime-error-message default-deps)))
          (is (nil? ((:show-toast! default-deps) store :info "ignored")))

          (is (= store (:store custom-deps)))
          (is (= request (:request custom-deps)))
          (is (identical? nxr/dispatch (:dispatch! custom-deps)))
          (is (identical? common/exchange-response-error
                          (:exchange-response-error custom-deps)))
          (is (identical? common/runtime-error-message
                          (:runtime-error-message custom-deps)))
          (is (identical? custom-show-toast! (:show-toast! custom-deps)))))))))
