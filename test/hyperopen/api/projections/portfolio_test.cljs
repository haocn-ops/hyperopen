(ns hyperopen.api.projections.portfolio-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.api.projections.portfolio :as portfolio]))

(deftest portfolio-projections-track-loading-success-and-error-test
  (let [summary {:account {:equity "1000"}}
        state {:portfolio {:summary-by-key {:stale true}
                           :loading? false
                           :error "stale"}}
        loading (portfolio/begin-portfolio-load state)
        success (portfolio/apply-portfolio-success loading summary)
        empty-success (portfolio/apply-portfolio-success loading nil)
        failed (portfolio/apply-portfolio-error loading (js/Error. "portfolio-fail"))]
    (is (= true (get-in loading [:portfolio :loading?])))
    (is (nil? (get-in loading [:portfolio :error])))
    (is (= summary (get-in success [:portfolio :summary-by-key])))
    (is (= false (get-in success [:portfolio :loading?])))
    (is (nil? (get-in success [:portfolio :error])))
    (is (number? (get-in success [:portfolio :loaded-at-ms])))
    (is (= {} (get-in empty-success [:portfolio :summary-by-key])))
    (is (= false (get-in failed [:portfolio :loading?])))
    (is (= "Error: portfolio-fail" (get-in failed [:portfolio :error])))))

(deftest portfolio-projections-track-request-address-scope-test
  (let [address "0xabcdefabcdefabcdefabcdefabcdefabcdef1234"
        summary {:month {:accountValueHistory [[1 100] [2 110]]}}
        loading (portfolio/begin-portfolio-load {} address)
        success (portfolio/apply-portfolio-success loading address summary)
        failed (portfolio/apply-portfolio-error loading address (js/Error. "portfolio-fail"))]
    (is (= true (get-in loading [:portfolio :loading?])))
    (is (= address (get-in loading [:portfolio :loading-for-address])))
    (is (= summary (get-in success [:portfolio :summary-by-key])))
    (is (nil? (get-in success [:portfolio :loading-for-address])))
    (is (= address (get-in success [:portfolio :loaded-for-address])))
    (is (nil? (get-in success [:portfolio :error-for-address])))
    (is (= false (get-in failed [:portfolio :loading?])))
    (is (nil? (get-in failed [:portfolio :loading-for-address])))
    (is (= "Error: portfolio-fail" (get-in failed [:portfolio :error])))
    (is (= address (get-in failed [:portfolio :error-for-address])))))

(deftest trader-benchmark-portfolio-projections-track-keyed-loading-success-and-error-test
  (let [address "0xabcdefabcdefabcdefabcdefabcdefabcdef1234"
        other-address "0x1111111111111111111111111111111111111111"
        summary {:month {:accountValueHistory [[1 100] [2 110]]
                         :pnlHistory [[1 0] [2 10]]}}
        state {:portfolio {:summary-by-key {:month {:accountValueHistory [[1 1]]}}
                           :trader-benchmarks-by-address {other-address {:summary-by-key {:month {:stale true}}}}
                           :loading {:trader-benchmarks-by-address {other-address true}}
                           :errors {:trader-benchmarks-by-address {other-address "stale"}}}}
        loading (portfolio/begin-trader-benchmark-portfolio-load state address)
        success (portfolio/apply-trader-benchmark-portfolio-success loading address summary)
        empty-success (portfolio/apply-trader-benchmark-portfolio-success loading address nil)
        failed (portfolio/apply-trader-benchmark-portfolio-error loading address (js/Error. "benchmark-fail"))]
    (is (= {:month {:accountValueHistory [[1 1]]}}
           (get-in success [:portfolio :summary-by-key])))
    (is (= true
           (get-in loading [:portfolio :loading :trader-benchmarks-by-address address])))
    (is (nil?
         (get-in loading [:portfolio :errors :trader-benchmarks-by-address address])))
    (is (= summary
           (get-in success [:portfolio :trader-benchmarks-by-address address :summary-by-key])))
    (is (= false
           (get-in success [:portfolio :loading :trader-benchmarks-by-address address])))
    (is (nil?
         (get-in success [:portfolio :errors :trader-benchmarks-by-address address])))
    (is (number?
         (get-in success [:portfolio :trader-benchmarks-by-address address :loaded-at-ms])))
    (is (= {}
           (get-in empty-success [:portfolio :trader-benchmarks-by-address address :summary-by-key])))
    (is (= false
           (get-in failed [:portfolio :loading :trader-benchmarks-by-address address])))
    (is (= "Error: benchmark-fail"
           (get-in failed [:portfolio :errors :trader-benchmarks-by-address address])))
    (is (= {:summary-by-key {:month {:stale true}}}
           (get-in success [:portfolio :trader-benchmarks-by-address other-address])))
    (is (= true
           (get-in success [:portfolio :loading :trader-benchmarks-by-address other-address])))
    (is (= "stale"
           (get-in success [:portfolio :errors :trader-benchmarks-by-address other-address])))))
