(ns hyperopen.schema.contracts.common-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.schema.contracts.common :as common]))

(deftest parse-int-value-accepts-integer-shaped-values-test
  (is (= 12 (common/parse-int-value 12)))
  (is (= 12 (common/parse-int-value 12.0)))
  (is (= -7 (common/parse-int-value " -7 ")))
  (is (= 42 (common/parse-int-value "+42"))))

(deftest parse-int-value-rejects-non-integer-values-test
  (is (nil? (common/parse-int-value 12.5)))
  (is (nil? (common/parse-int-value "12.5")))
  (is (nil? (common/parse-int-value "")))
  (is (nil? (common/parse-int-value "not-a-number")))
  (is (nil? (common/parse-int-value nil)))
  (is (nil? (common/parse-int-value {:value 1})))
  (is (nil? (common/parse-int-value js/Infinity))))
