(ns hyperopen.portfolio.optimizer.coercion-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(deftest text-and-number-coercion-normalizes-common-inputs-test
  (testing "text"
    (is (= "BTC" (coercion/non-blank-text " BTC ")))
    (is (= "42" (coercion/non-blank-text 42)))
    (is (nil? (coercion/non-blank-text "   ")))
    (is (nil? (coercion/non-blank-text nil))))
  (testing "finite numbers"
    (is (true? (coercion/finite-number? 12.5)))
    (is (false? (coercion/finite-number? js/NaN)))
    (is (false? (coercion/finite-number? js/Infinity)))
    (is (false? (coercion/finite-number? "12.5"))))
  (testing "strict numeric parsing"
    (is (= 12.5 (coercion/parse-number " 12.5 ")))
    (is (= -3 (coercion/parse-number -3)))
    (is (nil? (coercion/parse-number "12px")))
    (is (nil? (coercion/parse-number ""))))
  (testing "parseFloat-compatible parsing stays explicit"
    (is (= 12 (coercion/parse-float-number "12px")))
    (is (= 12.5 (coercion/parse-float-number " 12.5 ")))
    (is (nil? (coercion/parse-float-number "px12"))))
  (testing "derived numeric helpers"
    (is (= 1234 (coercion/parse-ms "1234.9")))
    (is (true? (coercion/positive-number? 0.1)))
    (is (false? (coercion/positive-number? 0)))
    (is (= 0.125 (coercion/parse-percent-text "12.5%")))
    (is (= 0.125 (coercion/parse-percent-text "12.5")))
    (is (= "12.5" (coercion/decimal->percent-text 0.125)))))

(deftest keyword-and-list-normalization-centralizes-ui-enum-shaping-test
  (is (= :partially-executed
         (coercion/normalize-keyword-like "partiallyExecuted")))
  (is (= :updated-desc
         (coercion/normalize-keyword-like " updated_desc ")))
  (is (= :saved
         (coercion/normalize-keyword-like :saved)))
  (is (nil? (coercion/normalize-keyword-like "")))
  (is (= :fallback
         (coercion/normalize-enum "missing" #{:active :saved} :fallback)))
  (is (= :saved
         (coercion/normalize-enum "saved" #{:active :saved} :fallback)))
  (is (= ["perp:BTC" "spot:PURR/USDC"]
         (coercion/normalize-id-list [" perp:BTC "
                                      ""
                                      nil
                                      "spot:PURR/USDC"
                                      "perp:BTC"]))))

(deftest boolean-parsing-is-explicit-test
  (is (true? (coercion/parse-boolean-value true)))
  (is (false? (coercion/parse-boolean-value false)))
  (is (true? (coercion/parse-boolean-value " true ")))
  (is (false? (coercion/parse-boolean-value "FALSE")))
  (is (nil? (coercion/parse-boolean-value "yes"))))
