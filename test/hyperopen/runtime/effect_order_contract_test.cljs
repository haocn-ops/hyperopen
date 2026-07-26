(ns hyperopen.runtime.effect-order-contract-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.schema.runtime-registration-catalog :as registration-catalog]
            [hyperopen.runtime.effect-order-contract :as contract]))

(deftest assert-action-effect-order-rejects-phase-order-regression-after-heavy-test
  (let [effects [[:effects/save [:portfolio-ui :returns-benchmark-coin] "SPY"]
                 [:effects/fetch-candle-snapshot :coin "SPY" :interval :1h :bars 800]
                 [:effects/local-storage-set "portfolio-returns-benchmark" "SPY"]]]
    (is (thrown-with-msg?
         js/Error
         #"rule=phase-order-regression"
         (contract/assert-action-effect-order!
          :actions/select-portfolio-returns-benchmark
          effects
          {:phase :test})))))

(deftest assert-action-effect-order-allows-duplicate-heavy-effects-when-policy-permits-test
  (let [effects [[:effects/save [:portfolio-ui :summary-time-range] :1w]
                 [:effects/fetch-candle-snapshot :interval :1h]
                 [:effects/fetch-candle-snapshot :interval :1h]]
        summary (contract/effect-order-summary
                 :actions/select-portfolio-summary-time-range
                 effects)]
    (is (= effects
           (contract/assert-action-effect-order!
            :actions/select-portfolio-summary-time-range
            effects
            {:phase :test})))
    (is (= [:effects/fetch-candle-snapshot]
           (:duplicate-heavy-effect-ids summary)))
    (is (true? (:phase-order-valid summary)))))

(deftest assert-action-effect-order-classifies-shareable-query-replacement-as-persistence-test
  (let [effects [[:effects/save [:portfolio-ui :chart-tab] :returns]
                 [:effects/replace-shareable-route-query]
                 [:effects/fetch-candle-snapshot :interval :1h]]
        summary (contract/effect-order-summary
                 :actions/select-portfolio-chart-tab
                 effects)]
    (is (= [:projection :persistence :heavy-io]
           (:phases summary)))
    (is (= effects
           (contract/assert-action-effect-order!
            :actions/select-portfolio-chart-tab
            effects
            {:phase :test})))))

(deftest assert-action-effect-order-allows-route-loads-to-fetch-multiple-vault-records-test
  (let [effects [[:effects/save [:vaults-ui :list-loading?] true]
                 [:effects/save [:vaults-ui :detail-loading?] true]
                 [:effects/api-fetch-vault-index-with-cache]
                 [:effects/api-fetch-vault-summaries]
                 [:effects/api-fetch-vault-benchmark-details
                  "0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"]
                 [:effects/api-fetch-vault-benchmark-details
                  "0x1e37a337ed460039d1b15bd3bc489de789768d5e"]
                 [:effects/api-fetch-vault-details
                  "0x07fd993f0fa3a185f7207adccd29f7a87404689d"
                  nil]
                 [:effects/api-fetch-vault-webdata2
                  "0x07fd993f0fa3a185f7207adccd29f7a87404689d"]]
        summary (contract/effect-order-summary
                 :actions/load-vault-route
                 effects)]
    (is (= effects
           (contract/assert-action-effect-order!
            :actions/load-vault-route
            effects
            {:phase :test})))
    (is (= [:effects/api-fetch-vault-benchmark-details]
           (:duplicate-heavy-effect-ids summary)))
    (is (true? (:phase-order-valid summary)))))

(deftest effect-order-summary-can-report-projection-before-heavy-true-while-phase-order-invalid-test
  (let [effects [[:effects/save [:portfolio-ui :returns-benchmark-coin] "SPY"]
                 [:effects/fetch-candle-snapshot :coin "SPY" :interval :1h :bars 800]
                 [:effects/local-storage-set "portfolio-returns-benchmark" "SPY"]]
        summary (contract/effect-order-summary
                 :actions/select-portfolio-returns-benchmark
                 effects)]
    (testing "Projection still occurs before heavy work"
      (is (true? (:projection-before-heavy summary))))
    (testing "The later persistence effect still invalidates the tracked phase order"
      (is (false? (:phase-order-valid summary)))
      (is (= [:projection :heavy-io :persistence]
             (:phases summary))))
    (testing "No duplicate heavy effect is involved in this regression"
      (is (empty? (:duplicate-heavy-effect-ids summary))))))

(deftest effect-order-policy-coverage-matches-runtime-registration-metadata-test
  (is (= (registration-catalog/effect-order-policy-required-action-ids)
         (contract/covered-action-ids))
      "Actions marked as requiring effect-order policy in the runtime registration catalog must exactly match effect-order policy coverage."))
