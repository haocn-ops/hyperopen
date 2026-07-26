(ns hyperopen.subaccounts.transfer-amount-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.subaccounts.transfer-amount :as transfer-amount]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def subaccount-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(defn- base-state
  []
  {:wallet {:address owner-address}
   :spot {:clearinghouse-state
          {:balances [{:coin "USDC"
                       :token "USDC"
                       :total "50"
                       :hold "0"}
                      {:coin "USDH"
                       :token "USDH:0xabc"
                       :total "12"
                       :hold "0"}]}
          :meta {:tokens [{:name "USDH"
                           :token "USDH:0xabc"
                           :weiDecimals 6}
                          {:name "MEOW"
                           :token "MEOW:0xdef"
                           :weiDecimals 2}
                          {:name "ZERO"
                           :token "ZERO:0xzero"
                           :weiDecimals 0}]}}
   :account-context
   {:subaccounts
    {:rows [{:name "Desk"
             :master owner-address
             :sub-account-user subaccount-address
             :spot-state {:balances [{:coin "MEOW"
                                       :token "MEOW:0xdef"
                                       :total "1"
                                       :hold "0"}]}}]}}})

(defn- normalize-spot
  [state token amount-text & {:keys [direction]
                              :or {direction :deposit}}]
  (transfer-amount/normalize-transfer-amount
   state
   {:subaccount-address subaccount-address
    :account-kind :spot
    :direction direction
    :token token
    :amount-text amount-text}))

(deftest trading-usdc-parser-preserves-safe-integer-boundary-test
  (is (= js/Number.MAX_SAFE_INTEGER
         (transfer-amount/parse-usdc-amount->micros "9007199254.740991")))
  (is (nil? (transfer-amount/parse-usdc-amount->micros "9007199254.740992")))
  (is (nil? (transfer-amount/parse-usdc-amount->micros "")))
  (is (nil? (transfer-amount/parse-usdc-amount->micros "0")))
  (is (nil? (transfer-amount/parse-usdc-amount->micros "1.0000001"))))

(deftest spot-decimal-parser-is-exact-and-canonical-test
  (is (= {:ok? true
          :payload {:account-kind :spot
                    :token "USDH:0xabc"
                    :token-symbol "USDH"
                    :amount "2.5"
                    :amount-display "2.5"
                    :amount-units "2500000"
                    :amount-decimals 6}}
         (normalize-spot (base-state) "USDH:0xabc" "0002.500000")))
  (is (= "1"
         (get-in (normalize-spot (base-state) "USDH:0xabc" "0.000001")
                 [:payload :amount-units])))
  (is (= {:ok? true
          :payload {:account-kind :spot
                    :token "MEOW:0xdef"
                    :token-symbol "MEOW"
                    :amount "0.02"
                    :amount-display "0.02"
                    :amount-units "2"
                    :amount-decimals 2}}
         (normalize-spot (base-state) "MEOW:0xdef" "0.02" :direction :withdraw)))
  (is (= {:ok? false
          :message transfer-amount/invalid-spot-transfer-amount-message}
         (normalize-spot (base-state) "USDH:0xabc" "0.000000"))))

(deftest spot-decimal-parser-rejects-hostile-syntax-test
  (doseq [amount ["1e6" "Infinity" "NaN" "-1" "+1" "1.2.3" ".5" "1." "" "   "]]
    (testing amount
      (is (= {:ok? false
              :message transfer-amount/invalid-spot-transfer-amount-message}
             (normalize-spot (base-state) "USDH:0xabc" amount))))))

(deftest spot-atomic-unit-digit-limit-is-enforced-test
  (let [limit transfer-amount/max-spot-atomic-digits
        exact (apply str (repeat limit "1"))
        over (str exact "1")]
    (is (= exact
           (get-in (normalize-spot (base-state) "ZERO:0xzero" exact)
                   [:payload :amount-units])))
    (is (= {:ok? false
            :message transfer-amount/invalid-spot-transfer-amount-message}
           (normalize-spot (base-state) "ZERO:0xzero" over)))))

(deftest spot-precision-metadata-resolution-order-test
  (let [state (-> (base-state)
                  (assoc-in [:spot :clearinghouse-state :balances 1 :amount-decimals] 2)
                  (assoc-in [:spot :meta :tokens 0 :weiDecimals] 6))]
    (is (= {:ok? false
            :message transfer-amount/invalid-spot-transfer-amount-message}
           (normalize-spot state "USDH:0xabc" "1.234")))
    (is (= "123"
           (get-in (normalize-spot
                    (-> (base-state)
                        (assoc-in [:spot :clearinghouse-state :balances]
                                  [{"coin" "STRINGY"
                                    "token" "STRINGY:0xabc"
                                    "amountDecimals" "3"}]))
                    "STRINGY:0xabc"
                    "0.123")
                   [:payload :amount-units])))))

(deftest spot-precision-fallbacks-and-fail-closed-test
  (is (= 6
         (get-in (normalize-spot (base-state) "USDC" "1.234567")
                 [:payload :amount-decimals])))
  (is (= "12"
         (get-in (normalize-spot
                  {:spot {:clearinghouse-state {:balances []}}
                   :webdata2 {:spotMeta {:tokens [{:name "WEB"
                                                   :token "WEB:0xabc"
                                                   :weiDecimals 4}]}}}
                  "WEB:0xabc"
                  "0.0012")
                 [:payload :amount-units])))
  (is (= "123"
         (get-in (normalize-spot
                  {:spot {:clearinghouse-state {:balances []}}
                   :asset-selector {:market-by-key {"spot:MRKT"
                                                    {:market-type :spot
                                                     :coin "MRKT"
                                                     :szDecimals 3}}}}
                  "MRKT:0xabc"
                  "0.123")
                 [:payload :amount-units])))
  (is (= {:ok? false
          :message transfer-amount/missing-spot-transfer-precision-message}
         (normalize-spot
          {:spot {:clearinghouse-state {:balances []}
                  :meta {:tokens []}}}
          "UNKNOWN:0xabc"
          "1"))))
