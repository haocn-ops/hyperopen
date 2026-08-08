(ns hyperopen.views.funding-modal-feedback-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.funding.actions :as funding-actions]
            [hyperopen.views.funding-modal :as view]))

(defn- children
  [node]
  (if (map? (second node))
    (drop 2 node)
    (drop 1 node)))

(defn- find-first-node
  [node pred]
  (cond
    (vector? node) (or (when (pred node) node)
                       (some #(find-first-node % pred) (children node)))
    (seq? node) (some #(find-first-node % pred) node)
    :else nil))

(defn- node-text
  [node]
  (cond
    (string? node) node
    (vector? node) (apply str (map node-text (children node)))
    (seq? node) (apply str (map node-text node))
    :else ""))

(defn- base-state
  []
  {:wallet {:address "0x1234567890abcdef1234567890abcdef12345678"}
   :spot {:clearinghouse-state {:balances [{:coin "USDC" :available "12.5" :total "12.5" :hold "0"}]}}
   :webdata2 {:clearinghouseState {:availableToWithdraw "8.5"
                                   :marginSummary {:accountValue "20"
                                                   :totalMarginUsed "11.5"}}}
   :funding-ui {:modal (funding-actions/default-funding-modal-state)}})

(deftest failed-deposit-renders-full-inline-recovery-message-test
  (let [message "Deposit failed: Check current Testnet USDC2 and Arbitrum Sepolia test ETH, then try again."
        state (assoc-in (base-state)
                        [:funding-ui :modal]
                        {:open? true
                         :mode :deposit
                         :deposit-step :amount-entry
                         :deposit-selected-asset-key :usdc
                         :amount-input "5"
                         :submitting? false
                         :error message})
        view-node (view/funding-modal-view state)
        status-node (find-first-node view-node #(= "funding-status"
                                                   (get-in % [1 :data-role])))
        amount-input (find-first-node view-node #(= "funding-deposit-amount-input"
                                                    (get-in % [1 :id])))]
    (is (some? status-node))
    (is (= message (node-text status-node)))
    (is (= "alert" (get-in status-node [1 :role])))
    (is (= "assertive" (get-in status-node [1 :aria-live])))
    (is (= "true" (get-in status-node [1 :aria-atomic])))
    (is (some? amount-input))
    (is (false? (get-in amount-input [1 :disabled])))))
