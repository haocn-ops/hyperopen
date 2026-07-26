(ns hyperopen.views.trade.order-form-view.footer-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.trade.order-form.test-support :refer [base-state
                                                                   collect-strings
                                                                   find-first-node
                                                                   liquidation-price-tooltip-text
                                                                   metric-value-text]]
            [hyperopen.views.trade.order-form-footer :as footer]
            [hyperopen.views.trade.order-form-vm :as order-form-vm]))

(deftest submit-row-renders-disabled-tooltip-and_submit_action_test
  (let [row (footer/submit-row {:submitting? false
                                :submit-disabled? true
                                :submit-tooltip "Fill required fields: Price, Size."
                                :on-submit [[:actions/submit-order]]})
        submit-button (find-first-node row
                                       (fn [node]
                                         (= "trade-submit-order-button"
                                            (get-in node [1 :data-parity-id]))))
        strings (set (collect-strings row))]
    (is (some? submit-button))
    (is (= true (get-in submit-button [1 :disabled])))
    (is (= [[:actions/submit-order]]
           (get-in submit-button [1 :on :click])))
    (is (contains? strings "Fill required fields: Price, Size."))))

(deftest fee-row-copy-expands-maker-taker-tooltip-test
  (let [copy (footer/fee-row-copy {:effective "0.0450% / 0.0150%"})]
    (is (= "Current fee" (:current-label copy)))
    (is (= "Base tier fee" (:baseline-label copy)))
    (is (= "Taker orders pay a 0.0450% fee. Maker orders pay a 0.0150% fee."
           (:tooltip copy)))))

(deftest footer-metrics-renders-scale-preview-liquidation-tooltip-and_fees-test
  (let [state (base-state {:type :scale})
        vm (order-form-vm/order-form-vm state)
        footer-node (footer/footer-metrics (:display vm)
                                           true
                                           true
                                           false
                                           (footer/fee-row-copy (get-in vm [:display :fees]))
                                           {:start "84.00"
                                            :end "79.00"})
        strings (set (collect-strings footer-node))]
    (is (= "84.00" (metric-value-text footer-node "Start")))
    (is (= "79.00" (metric-value-text footer-node "End")))
    (is (contains? strings liquidation-price-tooltip-text))
    (is (contains? strings "Fees"))))
