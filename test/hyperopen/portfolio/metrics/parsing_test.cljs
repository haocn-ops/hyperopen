(ns hyperopen.portfolio.metrics.parsing-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.metrics.parsing :as parsing]
            [hyperopen.portfolio.metrics.test-utils :refer [approx=]]))

(deftest optional-number-accepts-finite-decimal-and-exponent-strings-test
  (is (= 42
         (parsing/optional-number "42")))
  (is (approx= -0.5
               (parsing/optional-number "-0.5")
               1e-12))
  (is (= 1250000
         (parsing/optional-number "1.25e6")))
  (is (= 7
         (parsing/optional-number " 7 "))))

(deftest optional-number-accepts-native-finite-numbers-test
  (is (= 42
         (parsing/optional-number 42)))
  (is (approx= -0.5
               (parsing/optional-number -0.5)
               1e-12))
  (is (= 0
         (parsing/optional-number 0))))

(deftest optional-number-rejects-partial-empty-and-invalid-strings-test
  (doseq [value [""
                 " "
                 "123abc"
                 "1_000"
                 "abc"]]
    (testing value
      (is (nil? (parsing/optional-number value))))))

(deftest optional-number-rejects-nonfinite-tokens-test
  (doseq [value ["Infinity"
                 "-Infinity"
                 "NaN"
                 js/Infinity
                 js/-Infinity
                 js/NaN]]
    (testing (str value)
      (is (nil? (parsing/optional-number value))))))

(deftest history-point-map-key-precedence-is-deterministic-test
  (let [row {:time "1000"
             :timestamp "2000"
             :time-ms "3000"
             :value "10"
             :pnl "99"
             :account-value "101"
             :accountValue "102"}]
    (is (= 1000
           (parsing/history-point-time-ms row)))
    (is (= 10
           (parsing/history-point-value row)))))
