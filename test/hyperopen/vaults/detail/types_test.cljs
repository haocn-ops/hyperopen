(ns hyperopen.vaults.detail.types-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.vaults.detail.types :as detail-types]))

(deftest normalize-vault-address-lowercases-and-trims-test
  (is (= "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
         (detail-types/normalize-vault-address " 0xABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD ")))
  (is (nil? (detail-types/normalize-vault-address ""))))

(deftest parse-benchmark-id-parses-market-and-vault-values-test
  (is (= {:kind :market
          :coin "BTC"}
         (detail-types/parse-benchmark-id "BTC")))
  (is (= {:kind :vault
          :address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
         (detail-types/parse-benchmark-id "vault:0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))))

(deftest benchmark-id-value-roundtrip-test
  (is (= "ETH"
         (detail-types/benchmark-id->value {:kind :market
                                            :coin "ETH"})))
  (is (= "vault:0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
         (detail-types/benchmark-id->value {:kind :vault
                                            :address "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"})))
  (is (= "0xcccccccccccccccccccccccccccccccccccccccc"
         (detail-types/vault-benchmark-address "vault:0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC")))
  (is (nil? (detail-types/vault-benchmark-address "BTC"))))
