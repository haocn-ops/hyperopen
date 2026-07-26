(ns hyperopen.domain.trading.indicators.registry-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.domain.trading.indicators.registry :as registry]
            [hyperopen.domain.trading.indicators.result :as result]))

(deftest register-domain-family-extension-test
  (let [family {:id :test-family
                :get-indicators (fn []
                                  [{:id :test-indicator
                                    :name "Test Indicator"
                                    :short-name "TEST"
                                    :description "Test extension"
                                    :supports-period? false
                                    :default-config {}}])
                :calculate-indicator (fn [indicator-type _data _params]
                                       (when (= indicator-type :test-indicator)
                                         (result/indicator-result :test-indicator
                                                                  :separate
                                                                  [(result/line-series :test [1.0 nil])])))}]
    (is (nil? (registry/calculate-domain-indicator :test-indicator [] {})))
    (let [definitions (registry/get-domain-indicators [family])
          calc-result (registry/calculate-domain-indicator :test-indicator [{:time 1}] {} [family])]
      (is (some #(= :test-indicator (:id %)) definitions))
      (is (some? calc-result))
      (is (= :test-indicator (:type calc-result)))
      (is (= :separate (:pane calc-result)))
      (is (= :test (get-in calc-result [:series 0 :id]))))))

(deftest register-domain-family-invalid-input-test
  (let [count-before (count (registry/get-domain-indicators))]
    (is (= count-before
           (count (registry/get-domain-indicators [{:id :broken-family}]))))))
