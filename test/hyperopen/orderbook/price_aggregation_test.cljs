(ns hyperopen.orderbook.price-aggregation-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.orderbook.price-aggregation :as agg]))

(deftest build-options-pump-like-test
  (testing "PUMP-like perp options dedupe to UI-style labels"
    (let [options (agg/build-options {:market-type :perp
                                      :sz-decimals 0
                                      :reference-price 0.002})
          labels (mapv :label options)
          modes (mapv :mode options)]
      (is (= ["0.000001" "0.00001" "0.0001"] labels))
      (is (= [:full :sf3 :sf2] modes)))))

(deftest build-options-btc-like-test
  (testing "BTC-like perp options produce coarse step ladder"
    (let [options (agg/build-options {:market-type :perp
                                      :sz-decimals 5
                                      :reference-price 64700})
          labels (mapv :label options)
          modes (mapv :mode options)]
      (is (= ["1" "10" "100" "1000"] labels))
      (is (= [:full :sf4 :sf3 :sf2] modes)))))

(deftest dedupe-behavior-test
  (testing "full and sf4 collapse when their computed steps match"
    (let [options (agg/build-options {:market-type :perp
                                      :sz-decimals 0
                                      :reference-price 0.002})]
      (is (= :full (:mode (first options))))
      (is (not-any? #(= :sf4 (:mode %)) options)))))

(deftest mode-to-subscription-config-test
  (testing "mode mapping omits nSigFigs for full and sets it for sf4/sf3/sf2"
    (is (= {} (agg/mode->subscription-config :full)))
    (is (= {:nSigFigs 4} (agg/mode->subscription-config :sf4)))
    (is (= {:nSigFigs 3} (agg/mode->subscription-config :sf3)))
    (is (= {:nSigFigs 2} (agg/mode->subscription-config :sf2)))))

(deftest selected-mode-resolution-test
  (testing "selected mode falls back to first available option when unavailable after dedupe"
    (let [options (agg/build-options {:market-type :perp
                                      :sz-decimals 0
                                      :reference-price 0.002})]
      (is (= :full (agg/resolve-selected-mode options :sf4)))
      (is (= :sf3 (agg/resolve-selected-mode options :sf3))))))
