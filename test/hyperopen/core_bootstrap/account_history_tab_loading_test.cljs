(ns hyperopen.core-bootstrap.account-history-tab-loading-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.core.compat :as core]
            [hyperopen.core-bootstrap.test-support.effect-extractors :as effect-extractors]
            [hyperopen.platform :as platform]))

(def ^:private account-tab-heavy-effect-ids
  #{:effects/api-fetch-user-funding-history
    :effects/api-fetch-historical-orders})
(def ^:private account-tab-module-effect-id
  :effects/load-account-tab-module)

(deftest select-account-info-tab-loads-lazy-module-for-non-default-tabs-test
  (let [state {:account-info {:selected-tab :balances}
               :router {:path "/trade"}
               :active-asset "BTC"}
        effects (core/select-account-info-tab state :positions)]
    (is (= [[:effects/save [:account-info :selected-tab] :positions]
            [account-tab-module-effect-id :positions]
            [:effects/push-state "/trade?market=BTC&tab=positions"]]
           effects))))

(deftest select-account-info-tab-skips-lazy-module-load-for-default-balances-tab-test
  (let [state {:account-info {:selected-tab :positions}
               :router {:path "/trade"}
               :active-asset "BTC"}
        effects (core/select-account-info-tab state :balances)]
    (is (= [[:effects/save [:account-info :selected-tab] :balances]
            [:effects/push-state "/trade?market=BTC&tab=balances"]]
           effects))))

(deftest select-account-info-tab-funding-history-saves-selection-before-fetch-test
  (let [state {:account-info {:selected-tab :balances
                              :funding-history {:filters {:coin-set #{}
                                                          :start-time-ms 0
                                                          :end-time-ms 1000}
                                                :request-id 2}}
               :orders {:fundings-raw []}}
        effects (core/select-account-info-tab state :funding-history)
        immediate (first effects)
        path-values (second immediate)]
    (is (= :effects/save-many (first immediate)))
    (is (= [:account-info :selected-tab]
           (-> path-values first first)))
    (is (= :funding-history
           (-> path-values first second)))
    (is (= [account-tab-module-effect-id :funding-history]
           (second effects)))
    (is (effect-extractors/projection-before-heavy? effects account-tab-heavy-effect-ids))
    (is (effect-extractors/phase-order-valid? effects account-tab-heavy-effect-ids))
    (is (empty? (effect-extractors/duplicate-heavy-effect-ids effects account-tab-heavy-effect-ids)))
    (is (= [account-tab-module-effect-id :funding-history]
           (second effects)))
    (is (= [:effects/api-fetch-user-funding-history 3]
           (nth effects 2)))))

(deftest select-account-info-tab-order-history-saves-selection-before-fetch-test
  (let [state {:account-info {:selected-tab :balances
                              :order-history {:request-id 2}}}
        effects (core/select-account-info-tab state :order-history)
        immediate (first effects)
        path-values (second immediate)]
    (is (= :effects/save-many (first immediate)))
    (is (= [:account-info :selected-tab]
           (-> path-values first first)))
    (is (= :order-history
           (-> path-values first second)))
    (is (effect-extractors/projection-before-heavy? effects account-tab-heavy-effect-ids))
    (is (effect-extractors/phase-order-valid? effects account-tab-heavy-effect-ids))
    (is (empty? (effect-extractors/duplicate-heavy-effect-ids effects account-tab-heavy-effect-ids)))
    (is (= [account-tab-module-effect-id :order-history]
           (second effects)))
    (is (= [:effects/api-fetch-historical-orders 3]
           (nth effects 2)))))

(deftest select-account-info-tab-order-history-skips-fetch-when-preloaded-data-is-fresh-test
  (with-redefs [platform/now-ms (constantly 200000)]
    (let [state {:wallet {:address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                 :account-info {:selected-tab :balances
                                :order-history {:request-id 2
                                                :loaded-at-ms 150000
                                                :loaded-for-address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                                :error nil}}
                 :orders {:order-history []}}
          effects (core/select-account-info-tab state :order-history)]
      (is (= [[:effects/save [:account-info :selected-tab] :order-history]
              [account-tab-module-effect-id :order-history]]
             effects)))))

(deftest select-account-info-tab-order-history-refetches-when-preload-is-stale-or-address-mismatched-test
  (with-redefs [platform/now-ms (constantly 200000)]
    (let [stale-state {:wallet {:address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                       :account-info {:selected-tab :balances
                                      :order-history {:request-id 2
                                                      :loaded-at-ms 100000
                                                      :loaded-for-address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                                      :error nil}}}
          wrong-address-state {:wallet {:address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                               :account-info {:selected-tab :balances
                                              :order-history {:request-id 2
                                                              :loaded-at-ms 199000
                                                              :loaded-for-address "0xdddddddddddddddddddddddddddddddddddddddd"
                                                              :error nil}}}
          stale-effects (core/select-account-info-tab stale-state :order-history)
          wrong-address-effects (core/select-account-info-tab wrong-address-state :order-history)]
      (is (= [account-tab-module-effect-id :order-history]
             (second stale-effects)))
      (is (= [:effects/api-fetch-historical-orders 3]
             (nth stale-effects 2)))
      (is (= [account-tab-module-effect-id :order-history]
             (second wrong-address-effects)))
      (is (= [:effects/api-fetch-historical-orders 3]
             (nth wrong-address-effects 2))))))

(deftest refresh-order-history-fetches-only-when-order-history-tab-is-selected-test
  (let [selected-state {:account-info {:selected-tab :order-history
                                       :order-history {:request-id 5}}}
        background-state {:account-info {:selected-tab :balances
                                         :order-history {:request-id 5}}}
        selected-effects (core/refresh-order-history selected-state)
        background-effects (core/refresh-order-history background-state)]
    (is (= :effects/save-many (ffirst selected-effects)))
    (is (= [:effects/api-fetch-historical-orders 6]
           (second selected-effects)))
    (is (= true
           (-> selected-effects first second (nth 1) second)))
    (is (= false
           (-> background-effects first second (nth 1) second)))
    (is (= 1 (count background-effects)))))
