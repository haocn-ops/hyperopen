(ns hyperopen.builder-fee.policy-edge-cases-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.builder-fee.policy :as policy]))

(def owner "0x1111111111111111111111111111111111111111")
(def builder "0x36a47878219fb346e031f6cf82cbfc8c77e35932")
(def configured-fee
  {:status :configured
   :builder-address builder
   :fee-tenths-bp 10
   :disclosure "A disclosed 0.01% builder fee."})
(def approved-snapshot
  {:status :ready
   :owner-address owner
   :builder-address builder
   :network :testnet
   :max-builder-fee 10})
(def ordinary-order
  (array-map :type "order"
             :orders [(array-map :a 5 :b false :p "100" :s "1" :r true
                                 :t {:limit {:tif "Gtc"}})]
             :grouping "na"))

(deftest policy-attaches-exactly-one-builder-only-for-the-current-approved-main-owner-test
  (let [decision (policy/policy-decision configured-fee approved-snapshot owner owner :perp ordinary-order :sell)
        expected (array-map :type "order"
                            :orders (:orders ordinary-order)
                            :grouping "na"
                            :builder (array-map :b builder :f 10))]
    (is (true? (:active? decision)))
    (is (= :approved (:reason decision)))
    (is (= expected (:action decision)))
    (is (= (keys expected) (keys (:action decision))))
    (is (= [:b :f] (keys (:builder (:action decision)))))
    (is (= (:action decision)
           (:action (policy/policy-decision configured-fee approved-snapshot owner owner :perp (:action decision) :sell))))))

(deftest policy-is-total-fail-closed-and-preserves-the-original-action-object-test
  (doseq [[label config approval target market action side]
          [["disabled" (assoc configured-fee :status :disabled) approved-snapshot owner :perp ordinary-order :sell]
           ["below threshold" configured-fee (assoc approved-snapshot :max-builder-fee 9) owner :perp ordinary-order :sell]
           ["wrong owner" configured-fee (assoc approved-snapshot :owner-address "0x2222222222222222222222222222222222222222") owner :perp ordinary-order :sell]
           ["wrong target" configured-fee approved-snapshot "0x2222222222222222222222222222222222222222" :perp ordinary-order :sell]
           ["spot buy" configured-fee approved-snapshot owner :spot ordinary-order :buy]
           ["outcome" configured-fee approved-snapshot owner :outcome ordinary-order :sell]
           ["unknown market" configured-fee approved-snapshot owner :unknown ordinary-order :sell]
           ["twap" configured-fee approved-snapshot owner :perp (assoc ordinary-order :type "twapOrder") :sell]
           ["existing builder" configured-fee approved-snapshot owner :perp (assoc ordinary-order :builder (array-map :b builder :f 10)) :sell]]]
    (testing label
      (let [result (:action (policy/policy-decision config approval owner target market action side))]
        (is (identical? action result))))))
