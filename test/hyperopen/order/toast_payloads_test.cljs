(ns hyperopen.order.toast-payloads-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.order.toast-payloads :as toast-payloads]))

(defn- margin-request
  [ntli position]
  {:action {:type "updateIsolatedMargin"
            :asset 1
            :isBuy true
            :ntli ntli}
   :position position})

(deftest position-margin-success-toast-names-the-position
  (testing "added margin names the coin, side, and amount"
    (is (= {:headline "BTC long: added 250.00 USDC"
            :subline "Isolated margin"
            :message "BTC long: added 250.00 USDC."}
           (toast-payloads/position-margin-success-toast-payload
            (margin-request 250000000 {:coin "BTC" :side :long})))))

  (testing "negative ntli reads as a removal"
    (is (= {:headline "ETH short: removed 12.50 USDC"
            :subline "Isolated margin"
            :message "ETH short: removed 12.50 USDC."}
           (toast-payloads/position-margin-success-toast-payload
            (margin-request -12500000 {:coin "ETH" :side :short})))))

  (testing "named-dex positions carry the venue so same-coin rows stay distinct"
    (is (= {:headline "TSM long: added 6.22 USDC"
            :subline "Isolated margin on xyz"
            :message "TSM long: added 6.22 USDC."}
           (toast-payloads/position-margin-success-toast-payload
            (margin-request 6220000 {:coin "xyz:TSM" :dex "xyz" :side :long})))))

  (testing "the position survives truncation when the amount is large"
    (is (= "HYPE short: removed 1,250,000.00 USDC"
           (:headline (toast-payloads/position-margin-success-toast-payload
                       (margin-request -1250000000000
                                       {:coin "HYPE" :side :short}))))))

  (testing "venue falls back to the coin namespace when dex is absent"
    (is (= "Isolated margin on xyz"
           (:subline (toast-payloads/position-margin-success-toast-payload
                      (margin-request 6220000 {:coin "xyz:TSM" :side :long})))))))

(deftest position-margin-success-toast-degrades-without-position-detail
  (testing "an unknown side still names the coin"
    (is (= "BTC: added 250.00 USDC"
           (:headline (toast-payloads/position-margin-success-toast-payload
                       (margin-request 250000000 {:coin "BTC" :side :flat}))))))

  (testing "an unknown coin still reports the amount"
    (is (= "Added 250.00 USDC of margin"
           (:headline (toast-payloads/position-margin-success-toast-payload
                       (margin-request 250000000 {:coin "  " :side nil}))))))

  (testing "a request with no describable detail keeps the legacy message"
    (is (= "Margin updated."
           (toast-payloads/position-margin-success-toast-payload
            {:action {:type "updateIsolatedMargin"}})))))

(deftest position-margin-failure-toast-names-the-position
  (testing "failures lead with the position and carry the error as detail"
    (is (= {:headline "ETH short: margin update failed"
            :subline "Isolated margin"
            :message "ETH short margin update failed: margin rejected"
            :detail "margin rejected"}
           (toast-payloads/position-margin-failure-toast-payload
            (margin-request -12500000 {:coin "ETH" :side :short})
            "margin rejected"))))

  (testing "named-dex failures carry the venue"
    (is (= "Isolated margin on xyz"
           (:subline (toast-payloads/position-margin-failure-toast-payload
                      (margin-request 6220000 {:coin "xyz:TSM" :dex "xyz" :side :long})
                      "rejected")))))

  (testing "a blank error still produces a position-scoped failure"
    (is (= {:headline "BTC long: margin update failed"
            :subline "Isolated margin"
            :message "BTC long margin update failed"}
           (toast-payloads/position-margin-failure-toast-payload
            (margin-request 250000000 {:coin "BTC" :side :long})
            "  "))))

  (testing "no position falls back to the legacy error string"
    (is (= "Margin update failed: transport failure"
           (toast-payloads/position-margin-failure-toast-payload
            {:action {:type "updateIsolatedMargin"}}
            "transport failure")))))
