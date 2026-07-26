(ns hyperopen.portfolio.fee-schedule-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.fee-schedule :as fee-schedule]))

(deftest normalize-market-type-accepts-keywords-strings-and-labels-test
  (is (= :perps (fee-schedule/normalize-market-type nil)))
  (is (= :perps (fee-schedule/normalize-market-type :unknown)))
  (is (= :perps (fee-schedule/normalize-market-type "Perps")))
  (is (= :perps (fee-schedule/normalize-market-type "Core Perps")))
  (is (= :spot (fee-schedule/normalize-market-type "spot")))
  (is (= :spot-stable-pair
         (fee-schedule/normalize-market-type "Spot + Stable Pair")))
  (is (= :spot-aligned-quote
         (fee-schedule/normalize-market-type "spotAlignedQuote")))
  (is (= :spot-aligned-stable-pair
         (fee-schedule/normalize-market-type :spot-aligned-stable-pair)))
  (is (= :spot-aligned-stable-pair
         (fee-schedule/normalize-market-type "Spot + Aligned Quote + Stable Pair")))
  (is (= :hip3-perps
         (fee-schedule/normalize-market-type "HIP-3 Perps")))
  (is (= :hip3-perps-growth-mode
         (fee-schedule/normalize-market-type "HIP-3 Perps + Growth mode")))
  (is (= :hip3-perps-aligned-quote
         (fee-schedule/normalize-market-type :hip3-perps-aligned-quote)))
  (is (= :hip3-perps-growth-mode-aligned-quote
         (fee-schedule/normalize-market-type "hip3PerpsGrowthModeAlignedQuote"))))

(deftest market-type-options-include-expanded-volume-tier-scenarios-test
  (is (= ["Spot"
          "Spot + Aligned Quote"
          "Spot + Stable Pair"
          "Spot + Aligned Quote + Stable Pair"
          "Core Perps"
          "HIP-3 Perps"
          "HIP-3 Perps + Growth mode"
          "HIP-3 Perps + Aligned Quote"
          "HIP-3 Perps + Growth mode + Aligned Quote"]
         (mapv :label fee-schedule/market-type-options))))

(deftest normalize-fee-schedule-option-selectors-accept-labels-descriptions-and-defaults-test
  (is (= :referral-4
         (fee-schedule/normalize-referral-discount "Referral discount")))
  (is (= :referral-4
         (fee-schedule/normalize-referral-discount "4%")))
  (is (= :none
         (fee-schedule/normalize-referral-discount "unknown")))
  (is (= :platinum
         (fee-schedule/normalize-staking-tier ">100k HYPE staked = 30% discount")))
  (is (= :gold
         (fee-schedule/normalize-staking-tier "Gold")))
  (is (= :none
         (fee-schedule/normalize-staking-tier "unknown")))
  (is (= :tier-3
         (fee-schedule/normalize-maker-rebate-tier
          ">3.0% 14d weighted maker volume = -0.003% maker fee")))
  (is (= :tier-1
         (fee-schedule/normalize-maker-rebate-tier "Tier 1")))
  (is (= :none
         (fee-schedule/normalize-maker-rebate-tier "unknown"))))

(deftest fee-schedule-rows-render-protocol-rate-variants-test
  (testing "perps base schedule"
    (let [rows (fee-schedule/fee-schedule-rows :perps)]
      (is (= {:tier "0"
              :volume "<= $5M"
              :taker "0.045%"
              :maker "0.015%"}
             (first rows)))
      (is (= {:tier "6"
              :volume "> $7B"
              :taker "0.024%"
              :maker "0%"}
             (last rows)))))
  (testing "spot base schedule"
    (let [rows (fee-schedule/fee-schedule-rows :spot)]
      (is (= {:tier "0"
              :volume "<= $5M"
              :taker "0.070%"
              :maker "0.040%"}
             (first rows)))))
  (testing "stable pair and aligned quote multipliers"
    (is (= {:tier "0"
            :volume "<= $5M"
            :taker "0.014%"
            :maker "0.008%"}
           (first (fee-schedule/fee-schedule-rows :spot-stable-pair))))
    (is (= {:tier "0"
            :volume "<= $5M"
            :taker "0.056%"
            :maker "0.040%"}
           (first (fee-schedule/fee-schedule-rows :spot-aligned-quote))))
    (is (= {:tier "0"
            :volume "<= $5M"
            :taker "0.0112%"
            :maker "0.008%"}
           (first (fee-schedule/fee-schedule-rows :spot-aligned-stable-pair))))
    (is (= {:tier "6"
            :volume "> $7B"
            :taker "0.004%"
            :maker "0%"}
           (last (fee-schedule/fee-schedule-rows :spot-aligned-stable-pair)))))
  (testing "HIP-3 variants use active deployer fee context"
    (is (= {:tier "0"
            :volume "<= $5M"
            :taker "0.0675%"
            :maker "0.0225%"}
           (first (fee-schedule/fee-schedule-rows
                   :hip3-perps
                   {:active-fee-context {:deployer-fee-scale 0.5}}))))
    (is (= {:tier "0"
            :volume "<= $5M"
            :taker "0.0068%"
            :maker "0.0023%"}
           (first (fee-schedule/fee-schedule-rows
                   :hip3-perps-growth-mode
                   {:active-fee-context {:deployer-fee-scale 0.5}}))))
    (is (= {:tier "0"
            :volume "<= $5M"
            :taker "0.0585%"
            :maker "0.0225%"}
           (first (fee-schedule/fee-schedule-rows
                   :hip3-perps-aligned-quote
                   {:active-fee-context {:deployer-fee-scale 0.5}}))))
    (is (= {:tier "0"
            :volume "<= $5M"
            :taker "0.0059%"
            :maker "0.0023%"}
           (first (fee-schedule/fee-schedule-rows
                   :hip3-perps-growth-mode-aligned-quote
                   {:active-fee-context {:deployer-fee-scale 0.5}}))))))

(deftest fee-schedule-rows-apply-selected-discount-scenarios-test
  (testing "referral and staking reduce positive perps fees before maker rebate adjustment"
    (let [rows (fee-schedule/fee-schedule-rows
                :perps
                {:referral-discount :referral-4
                 :staking-tier :diamond
                 :maker-rebate-tier :tier-2})]
      (is (= {:tier "0"
              :volume "<= $5M"
              :taker "0.0259%"
              :maker "0.0066%"}
             (first rows)))
      (is (= ["0.0066%"
              "0.0049%"
              "0.0026%"
              "0.0003%"
              "-0.002%"
              "-0.002%"
              "-0.002%"]
             (mapv :maker rows)))))
  (testing "spot stable-pair and aligned-quote scaling applies after selected discounts"
    (let [rows (fee-schedule/fee-schedule-rows
                :spot-aligned-stable-pair
                {:referral-discount :referral-4
                 :staking-tier :wood})]
      (is (= {:tier "0"
              :volume "<= $5M"
              :taker "0.0102%"
              :maker "0.0073%"}
             (first rows)))))
  (testing "maker rebate adjusts each spot schedule row after market scaling"
    (let [rows (fee-schedule/fee-schedule-rows
                :spot-aligned-stable-pair
                {:maker-rebate-tier :tier-1})]
      (is (= ["0.007%"
              "0.005%"
              "0.003%"
              "0.001%"
              "-0.001%"
              "-0.001%"
              "-0.001%"]
             (mapv :maker rows)))))
  (testing "maker rebate adjusts each HIP-3 growth aligned row after market scaling"
    (let [rows (fee-schedule/fee-schedule-rows
                :hip3-perps-growth-mode-aligned-quote
                {:maker-rebate-tier :tier-2})]
      (is (= ["0.001%"
              "0.0004%"
              "-0.0004%"
              "-0.0012%"
              "-0.002%"
              "-0.002%"
              "-0.002%"]
             (mapv :maker rows)))))
  (testing "maker rebate can cross from exact positive fee to zero"
    (let [rows (fee-schedule/fee-schedule-rows
                :hip3-perps-growth-mode-aligned-quote
                {:maker-rebate-tier :tier-3})]
      (is (= "0%" (:maker (first rows))))))
  (testing "diamond staking and tier three rebate preserve core perps row shape"
    (let [rows (fee-schedule/fee-schedule-rows
                :perps
                {:staking-tier :diamond
                 :maker-rebate-tier :tier-3})]
      (is (= ["0.006%"
              "0.0042%"
              "0.0018%"
              "-0.0006%"
              "-0.003%"
              "-0.003%"
              "-0.003%"]
             (mapv :maker rows))))))
