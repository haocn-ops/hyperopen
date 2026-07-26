(ns hyperopen.domain.trading.order-values-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.domain.trading.order-values :as values]))

(deftest order-values-normalization-test
  (is (= :limit (:value (values/order-type-value :unknown))))
  (is (= :take-limit (:value (values/order-type-value "take-limit"))))
  (is (= :gtc (:value (values/tif-value :unsupported))))
  (is (= :ioc (:value (values/tif-value "ioc"))))
  (is (= :buy (:value (values/side-value nil))))
  (is (= :sell (:value (values/side-value "sell")))))

(deftest numeric-order-value-objects-test
  (let [price (values/price-value "100.5")
        bad-price (values/price-value "")
        size (values/size-value "2.25")
        pct (values/percent-value 123)]
    (is (= 100.5 (:value price)))
    (is (true? (:valid? price)))
    (is (false? (:present? bad-price)))
    (is (false? (:valid? bad-price)))
    (is (= 2.25 (:value size)))
    (is (= 100 (:value pct)))))
