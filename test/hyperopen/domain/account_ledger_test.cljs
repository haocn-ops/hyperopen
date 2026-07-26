(ns hyperopen.domain.account-ledger-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.domain.account-ledger :as account-ledger]))

(deftest normalize-ledger-rows-builds-main-client-history-rows-test
  (let [rows (account-ledger/normalize-ledger-rows
              [{:time 1770000000000
                :hash "0xdeposit"
                :delta {:type "deposit"
                        :usdc "100.0"}}
               {:time 1770000100000
                :hash "0xwithdraw"
                :delta {:type "withdraw"
                        :usdc "25.5"
                        :fee "1.0"}}
               {:time 1770000200000
                :hash "0xvault"
                :delta {:type "vaultDeposit"
                        :usdc "10.0"}}
               {:time 1770000300000
                :hash "0xgenesis"
                :delta {:type "spotGenesis"
                        :token "HYPE"
                        :amount "2.67"}}
               {:time 1770000400000
                :hash "0xsend"
                :delta {:type "spotTransfer"
                        :token "HYPE"
                        :amount "1.0"}}])]
    (is (= ["Send"
            "Genesis Distribution"
            "Vault Deposit"
            "Withdrawal"
            "Deposit"]
           (mapv :action-label rows)))
    (is (= ["-1 HYPE"
            "+2.67 HYPE"
            "-10 USDC"
            "-25.5 USDC"
            "+100 USDC"]
           (mapv :amount-text rows)))
    (is (= ["Trading Account" "Trading Account" "Trading Account" "Trading Account" "Arbitrum"]
           (mapv :source-label rows)))
    (is (= ["Trading Account" "Trading Account" "Trading Account" "Arbitrum" "Trading Account"]
           (mapv :destination-label rows)))
    (is (= ["--" "--" "--" "1 USDC" "--"]
           (mapv :fee-text rows)))
    (is (= (repeat 5 "Completed")
           (map :status-label rows)))))

(deftest normalize-ledger-rows-supports-direct-delta-and-drops-malformed-test
  (let [rows (account-ledger/normalize-ledger-rows
              [{:time 1770000000000
                :type "internalTransfer"
                :usdc "3.0"
                :hash "0xdirect"}
               {:time nil
                :delta {:type "deposit"
                        :usdc "1.0"}}
               {:time 1770000000001
                :delta {:type "unknownWithoutAmount"}}
               "not-a-row"])]
    (is (= 1 (count rows)))
    (is (= "Send" (:action-label (first rows))))
    (is (= "-3 USDC" (:amount-text (first rows))))
    (is (= "0xdirect" (:hash (first rows))))))

(deftest merge-ledger-rows-dedupes-rest-and-websocket-duplicates-test
  (let [rest-row {:time 1770000000000
                  :hash "0xdup"
                  :delta {:type "deposit"
                          :usdc "100.0"}}
        ws-row {:time 1770000000000
                :hash "0xdup"
                :delta {:type "deposit"
                        :usdc "100.0"}}
        newer-row {:time 1770000100000
                   :hash "0xnew"
                   :delta {:type "withdraw"
                           :usdc "1.0"}}
        rows (account-ledger/merge-ledger-rows [rest-row newer-row] [ws-row])]
    (is (= 2 (count rows)))
    (is (= ["Withdrawal" "Deposit"] (mapv :action-label rows)))
    (is (= ["0xnew" "0xdup"] (mapv :hash rows)))))
