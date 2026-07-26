(ns hyperopen.order.exchange-errors-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.order.exchange-errors :as exchange-errors]))

(deftest submit-error-toast-payload-removes-single-order-prefix-test
  (let [payload (exchange-errors/submit-error-toast-payload
                 "Order 1: Order could not be closed because there is insufficient liquidity.")]
    (is (= "Order not placed" (:headline payload)))
    (is (= "The exchange rejected this order." (:subline payload)))
    (is (= "Order could not be closed because there is insufficient liquidity."
           (:detail payload)))
    (is (= "Order not placed: Order could not be closed because there is insufficient liquidity."
           (:message payload)))
    (is (= false (:auto-timeout? payload)))))

(deftest submit-error-toast-payload-preserves-multi-order-detail-test
  (let [payload (exchange-errors/submit-error-toast-payload
                 "Order 1: first leg rejected; Order 2: second leg rejected"
                 {:partial? true})]
    (is (= "Order partially placed" (:headline payload)))
    (is (= "Some order legs were rejected by the exchange." (:subline payload)))
    (is (= "Order 1: first leg rejected; Order 2: second leg rejected"
           (:detail payload)))
    (is (= "Order partially placed: Order 1: first leg rejected; Order 2: second leg rejected"
           (:message payload)))))

(deftest schedule-cancel-volume-gate-parses-required-and-traded-values-test
  (is (= {:status :unavailable
          :reason :volume-gate
          :required "$1,000,000"
          :traded "$890,168.23"
          :message "Safety auto-cancel unavailable until $1,000,000 traded. Current volume: $890,168.23."}
         (exchange-errors/schedule-cancel-volume-gate
          "Cannot set scheduled cancel time until enough volume traded. Required: $1000000. Traded: $890168.23."))))
