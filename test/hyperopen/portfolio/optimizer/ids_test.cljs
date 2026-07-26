(ns hyperopen.portfolio.optimizer.ids-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.ids :as ids]))

(deftest vault-address-and-instrument-id-helpers-are-centralized-test
  (is (= "0xabc123"
         (ids/normalize-vault-address " 0xAbC123 ")))
  (is (nil? (ids/normalize-vault-address "   ")))
  (is (= "vault:0xabc123"
         (ids/vault-instrument-id " 0xAbC123 ")))
  (is (= "0xabc123"
         (ids/vault-address-from-instrument-id "vault:0xAbC123")))
  (is (= "0xabc123"
         (ids/vault-address-from-value "vault:0xAbC123")))
  (is (true? (ids/vault-instrument-id? "vault:0xAbC123")))
  (is (false? (ids/vault-instrument-id? "perp:BTC")))
  (is (true? (ids/vault-instrument? {:market-type "vault"
                                     :vault-address "0xAbC123"})))
  (is (true? (ids/vault-instrument? {:instrument-id "vault:0xAbC123"})))
  (is (false? (ids/vault-instrument? {:market-type "perp"
                                      :instrument-id "perp:BTC"}))))

(deftest instrument-key-and-market-type-normalization-are-centralized-test
  (is (= "perp:BTC"
         (ids/instrument-id-key (keyword "perp:BTC"))))
  (is (= "spot:PURR/USDC"
         (ids/instrument-id-key "spot:PURR/USDC")))
  (is (= "42"
         (ids/instrument-id-key 42)))
  (is (= :perp
         (ids/normalize-market-type " Perp ")))
  (is (= :spot
         (ids/normalize-market-type :spot)))
  (is (nil? (ids/normalize-market-type "")))
  (is (= "perp:BTC"
         (ids/normalize-instrument-id {:instrument-id " perp:BTC "
                                       :coin "BTC"})))
  (is (= "BTC"
         (ids/normalize-instrument-id {:coin " BTC "}))))

(deftest instrument-id-candidates-include-backend-and-history-local-ids-test
  (is (= ["hl:perp:ETH" "perp:ETH"]
         (ids/instrument-id-candidates
          {:instrument-id "hl:perp:ETH"
           :market-type :perp
           :coin "ETH"})))
  (is (= ["perp:ETH" "hl:perp:ETH"]
         (ids/instrument-id-candidates
          {:instrument-id "perp:ETH"
           :market-type :perp
           :coin "ETH"
           :optimizer-history/instrument-id "hl:perp:ETH"}))))
