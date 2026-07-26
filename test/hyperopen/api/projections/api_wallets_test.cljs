(ns hyperopen.api.projections.api-wallets-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.api.projections.api-wallets :as api-wallets]))

(deftest api-wallet-default-agent-projection-falls-back-to-default-name-test
  (let [state {:wallet {:address "0x162cc7c861ebd0c06b3d72319201150482518185"}
               :api-wallets {:loading {:default-agent? true}
                             :errors {:default-agent "stale"}}}
        snapshot {:agentAddress "0xbd20240037a1c694baf89af2f3aaea3161e95ce2"
                  :agentValidUntil "1700000000000"
                  :serverTime "1699999000000"}
        next-state (api-wallets/apply-api-wallets-default-agent-success
                    state
                    "0x162cc7c861ebd0c06b3d72319201150482518185"
                    snapshot)]
    (is (= {:row-kind :default
            :name "app.hyperopen.xyz"
            :approval-name nil
            :address "0xbd20240037a1c694baf89af2f3aaea3161e95ce2"
            :valid-until-ms 1700000000000}
           (get-in next-state [:api-wallets :default-agent-row])))
    (is (= 1699999000000
           (get-in next-state [:api-wallets :server-time-ms])))
    (is (= false
           (get-in next-state [:api-wallets :loading :default-agent?])))
    (is (nil? (get-in next-state [:api-wallets :errors :default-agent])))))

(deftest api-wallet-default-agent-projection-prefers-api-name-when-present-test
  (let [state {:wallet {:address "0x162cc7c861ebd0c06b3d72319201150482518185"}}
        snapshot {:agentAddress "0xbd20240037a1c694baf89af2f3aaea3161e95ce2"
                  :agentName "Desk default valid_until 1700000000000"
                  :serverTime "1699999000000"}
        next-state (api-wallets/apply-api-wallets-default-agent-success
                    state
                    "0x162cc7c861ebd0c06b3d72319201150482518185"
                    snapshot)]
    (is (= {:row-kind :default
            :name "Desk default"
            :approval-name nil
            :address "0xbd20240037a1c694baf89af2f3aaea3161e95ce2"
            :valid-until-ms 1700000000000}
           (get-in next-state [:api-wallets :default-agent-row])))))

(deftest api-wallet-default-agent-projection-prefers-explicit-valid-until-over-encoded-name-test
  (let [state {:wallet {:address "0x162cc7c861ebd0c06b3d72319201150482518185"}}
        snapshot {:agentAddress "0xbd20240037a1c694baf89af2f3aaea3161e95ce2"
                  :agentLabel "Desk default valid_until 1700000000000"
                  :agentValidUntil "1700001234567"
                  :serverTime "1699999000000"}
        next-state (api-wallets/apply-api-wallets-default-agent-success
                    state
                    "0x162cc7c861ebd0c06b3d72319201150482518185"
                    snapshot)]
    (is (= "Desk default"
           (get-in next-state [:api-wallets :default-agent-row :name])))
    (is (= 1700001234567
           (get-in next-state [:api-wallets :default-agent-row :valid-until-ms])))))

(deftest api-wallet-default-agent-projection-ignores-stale-owner-address-success-and-error-test
  (let [state {:wallet {:address "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}
               :api-wallets {:default-agent-row {:name "Existing"}
                             :owner-webdata2 {:agentAddress "0xexisting"}
                             :server-time-ms 1
                             :loading {:default-agent? true}
                             :errors {:default-agent "existing"}
                             :loaded-at-ms {:default-agent 2}}}
        stale-address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        success (api-wallets/apply-api-wallets-default-agent-success
                 state
                 stale-address
                 {:agentAddress "0xbd20240037a1c694baf89af2f3aaea3161e95ce2"
                  :serverTime "1699999000000"})
        failed (api-wallets/apply-api-wallets-default-agent-error
                state
                stale-address
                (js/Error. "stale-fail"))]
    (is (= state success))
    (is (= state failed))))
