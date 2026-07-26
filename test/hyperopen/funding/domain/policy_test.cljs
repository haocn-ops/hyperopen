(ns hyperopen.funding.domain.policy-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.funding.domain.assets :as assets-domain]
            [hyperopen.funding.domain.policy :as policy]))

(deftest direct-balance-row-available-prefers-supported-direct-fields-test
  (let [direct-balance-row-available @#'hyperopen.funding.domain.policy/direct-balance-row-available]
    (is (= 10.5
           (direct-balance-row-available {:available "10.5"
                                          :availableBalance "9"
                                          :free "8"})))
    (is (= 8
           (direct-balance-row-available {:availableBalance "8"
                                          :free "7"})))
    (is (= 7
           (direct-balance-row-available {:free "7"})))
    (is (nil? (direct-balance-row-available {:available "NaN"
                                             :availableBalance nil
                                             :free ""})))))

(deftest derived-balance-row-available-uses-total-minus-hold-when-needed-test
  (let [derived-balance-row-available @#'hyperopen.funding.domain.policy/derived-balance-row-available]
    (is (= 6
           (derived-balance-row-available {:total "10"
                                           :hold "4"})))
    (is (= 12
           (derived-balance-row-available {:totalBalance "12"})))
    (is (= -2
           (derived-balance-row-available {:total "5"
                                           :hold "7"})))
    (is (nil? (derived-balance-row-available {:hold "2"})))))

(deftest balance-row-available-wraps-direct-and-derived-values-test
  (let [balance-row-available @#'hyperopen.funding.domain.policy/balance-row-available]
    (is (= 10.5
           (balance-row-available {:available "10.5"
                                   :availableBalance "9"
                                   :free "8"
                                   :total "100"
                                   :hold "50"})))
    (is (= 0
           (balance-row-available {:total "5"
                                   :hold "7"})))
    (is (nil? (balance-row-available {:available "NaN"})))
    (is (nil? (balance-row-available nil)))))

(deftest normalize-mode-recognizes-legacy-and-ignores-unknown-values-test
  (is (= :legacy (policy/normalize-mode "legacy")))
  (is (= :withdraw (policy/normalize-mode :withdraw)))
  (is (nil? (policy/normalize-mode "unknown"))))

(deftest summary-derived-withdrawable-uses-margin-summary-and-clamps-negative-values-test
  (let [summary-derived-withdrawable @#'hyperopen.funding.domain.policy/summary-derived-withdrawable]
    (is (= 8.5
           (summary-derived-withdrawable {:accountValue "20"
                                          :totalMarginUsed "11.5"})))
    (is (= 0
           (summary-derived-withdrawable {:accountValue "5"
                                          :totalMarginUsed "7"})))
    (is (nil? (summary-derived-withdrawable {:accountValue "invalid"
                                             :totalMarginUsed "7"})))))

(deftest withdrawable-usdc-prefers-unified-spot-before-perps-fallback-test
  (let [withdrawable-usdc @#'hyperopen.funding.domain.policy/withdrawable-usdc]
    (is (= 12
           (withdrawable-usdc {:account {:mode :unified}
                               :spot {:clearinghouse-state {:balances [{:coin "USDC"
                                                                        :available "12"
                                                                        :total "12"
                                                                        :hold "0"}]}}
                               :webdata2 {:clearinghouseState {:withdrawable "4"}}})))
    (is (= 4
           (withdrawable-usdc {:spot {:clearinghouse-state {:balances []}}
                               :webdata2 {:clearinghouseState {:withdrawable "4"}}})))
    (is (= 0
           (withdrawable-usdc {:spot {:clearinghouse-state {:balances []}}
                               :webdata2 {:clearinghouseState {}}})))))

(deftest withdraw-available-amount-handles-nil-usdc-and-spot-assets-test
  (let [withdraw-available-amount @#'hyperopen.funding.domain.policy/withdraw-available-amount
        state {:spot {:clearinghouse-state {:balances [{:coin "USDC"
                                                        :available "3"
                                                        :total "3"
                                                        :hold "0"}
                                                       {:coin "BTC"
                                                        :available "1.25"
                                                        :total "1.25"
                                                        :hold "0"}]}}
               :webdata2 {:clearinghouseState {:withdrawable "4"}}}]
    (is (= 0
           (withdraw-available-amount state nil)))
    (is (= 4
           (withdraw-available-amount state {:key :usdc})))
    (is (= 1.25
           (withdraw-available-amount state {:key :btc
                                             :symbol "BTC"})))
    (is (= 0
           (withdraw-available-amount state {:key :eth
                                             :symbol "ETH"})))))

(deftest withdraw-available-list-display-hides-zeroish-values-test
  (let [withdraw-available-list-display @#'hyperopen.funding.domain.policy/withdraw-available-list-display]
    (is (= "-"
           (withdraw-available-list-display nil)))
    (is (= "-"
           (withdraw-available-list-display 0)))
    (is (= "1.25"
           (withdraw-available-list-display 1.25)))))

(deftest withdraw-assets-filtered-and-selected-asset-helpers-honor-search-and-fallbacks-test
  (with-redefs [assets-domain/withdraw-assets
                (fn [_state]
                  [{:key :btc
                    :symbol "BTC"
                    :name "Bitcoin"
                    :network "Bitcoin"
                    :available-amount 1
                    :available-display "1"
                    :available-detail-display "1"}
                   {:key :sol
                    :symbol "SOL"
                    :name "Solana"
                    :network "Solana"
                    :available-amount 2
                    :available-display "2"
                    :available-detail-display "2"}])]
    (is (= 2
           (count (policy/withdraw-assets-filtered {} {:withdraw-search-input " "}))))
    (is (= [:btc]
           (mapv :key (policy/withdraw-assets-filtered {} {:withdraw-search-input "bit"}))))
    (is (= [:sol]
           (mapv :key (policy/withdraw-assets-filtered {} {:withdraw-search-input "solana"}))))
    (is (= :btc
           (:key (policy/withdraw-asset {} {:withdraw-selected-asset-key :btc}))))
    (is (= :btc
           (:key (policy/withdraw-asset {} {:withdraw-selected-asset-key :missing}))))))

(deftest hyperunit-lifecycle-recovery-hint-uses-direction-specific-defaults-test
  (is (= "Verify the destination address and network, then submit a new withdrawal."
         (policy/hyperunit-lifecycle-recovery-hint {:direction :withdraw
                                                    :state "failed"})))
  (is (= "Verify the source transfer network and amount, then generate a new deposit address."
         (policy/hyperunit-lifecycle-recovery-hint {:direction :deposit
                                                    :state "failed"}))))

(deftest estimate-fee-display-normalizes-chain-units-and-fallbacks-test
  (is (= "100"
         (policy/estimate-fee-display "100" "unknown")))
  (is (= "1 BTC"
         (policy/estimate-fee-display "100000000" "bitcoin")))
  (is (= "1.23456789 BTC"
         (policy/estimate-fee-display 1.23456789 "bitcoin"))))

(deftest usdc-formatters-clamp-invalid-or-negative-values-to-zero-test
  (is (= "0.00"
         (policy/format-usdc-display nil)))
  (is (= "0.00"
         (policy/format-usdc-display "bad")))
  (is (= "0.00"
         (policy/format-usdc-display -3.5)))
  (is (= "12.50"
         (policy/format-usdc-display "12.5")))
  (is (= "0"
         (policy/format-usdc-input nil)))
  (is (= "0"
         (policy/format-usdc-input "bad")))
  (is (= "0"
         (policy/format-usdc-input -3.5)))
  (is (= "12.5"
         (policy/format-usdc-input "12.5"))))
