(ns hyperopen.views.account-info.tabs.balances.mobile-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.account-info.tabs.balances.test-support :as test-support]
            [hyperopen.views.account-info.test-support.fixtures :as fixtures]
            [hyperopen.views.account-info.test-support.hiccup :as hiccup]))

(deftest balances-tab-content-renders-mobile-summary-cards-with-inline-expansion-test
  (let [rows [(assoc fixtures/sample-balance-row
                     :key "usdc"
                     :coin "USDC"
                     :total-balance 388.555675
                     :available-balance 388.555675
                     :usdc-value 388.55
                     :amount-decimals 8
                     :contract-id "0x1234567890abcdef1234567890abcdef12345678")
              {:key "meow"
               :coin "MEOW"
               :selection-coin "xyz:MEOW"
               :total-balance 34.634736
               :available-balance 34.634736
               :usdc-value 0.01
               :pnl-value 0
               :pnl-pct 0
               :amount-decimals 6}]
        content (test-support/render-balances-tab rows
                                                  false
                                                  fixtures/default-sort-state
                                                  ""
                                                  {:balances "usdc"})
        mobile-viewport (hiccup/find-by-data-role content "balances-mobile-cards-viewport")
        mobile-cards (vec (hiccup/node-children mobile-viewport))
        expanded-card (hiccup/find-by-data-role content "mobile-balance-card-usdc")
        collapsed-card (hiccup/find-by-data-role content "mobile-balance-card-meow")
        expanded-button (first (vec (hiccup/node-children expanded-card)))
        collapsed-button (first (vec (hiccup/node-children collapsed-card)))
        expanded-button-classes (hiccup/node-class-set expanded-button)
        summary-grid (hiccup/find-first-node expanded-button #(contains? (hiccup/node-class-set %) "grid-cols-[minmax(0,0.82fr)_minmax(0,0.8fr)_minmax(0,1.25fr)_auto]"))
        footer-divider (hiccup/find-first-node expanded-card #(and (= :div (first %))
                                                                   (contains? (hiccup/node-class-set %) "border-t")
                                                                   (contains? (hiccup/node-class-set %) "border-ho-border-accent-muted")
                                                                   (contains? (hiccup/node-class-set %) "pt-2.5")
                                                                   (contains? (set (hiccup/collect-strings %)) "Send")))
        send-button (hiccup/find-first-node expanded-card #(= [[:actions/open-funding-send-modal
                                                                {:token "USDC"
                                                                 :symbol "USDC"
                                                                 :prefix-label nil
                                                                 :max-amount 388.555675
                                                                 :max-display "388.55567500"
                                                                 :max-input "388.55567500"}
                                                                :event.currentTarget/bounds]]
                                                              (get-in % [1 :on :click])))
        total-balance-value (hiccup/find-first-node expanded-card #(and (= :div (first %))
                                                                        (contains? (hiccup/direct-texts %) "388.55567500 USDC")
                                                                        (contains? (hiccup/node-class-set %) "whitespace-nowrap")))
        namespace-chip (hiccup/find-first-node collapsed-card #(and (= :span (first %))
                                                                    (contains? (hiccup/direct-texts %) "xyz")))
        expanded-strings (set (hiccup/collect-strings expanded-card))
        collapsed-strings (set (hiccup/collect-strings collapsed-card))]
    (is (some? mobile-viewport))
    (is (= 2 (count mobile-cards)))
    (is (= true (get-in expanded-button [1 :aria-expanded])))
    (is (= [[:actions/toggle-account-info-mobile-card :balances "usdc"]]
           (get-in expanded-button [1 :on :click])))
    (is (some? summary-grid))
    (is (contains? expanded-button-classes "px-3.5"))
    (is (contains? expanded-button-classes "hover:bg-[#0c1b24]"))
    (is (contains? (hiccup/node-class-set expanded-card) "bg-[#08161f]"))
    (is (contains? (hiccup/node-class-set expanded-card) "border-ho-border-accent-muted"))
    (is (not (contains? (hiccup/node-class-set expanded-card) "bg-ho-surface")))
    (is (some? total-balance-value))
    (is (some? send-button))
    (is (some? namespace-chip))
    (is (some? footer-divider))
    (is (contains? (hiccup/node-class-set namespace-chip) "bg-[#242924]"))
    (is (contains? (hiccup/node-class-set namespace-chip) "border"))
    (is (contains? (hiccup/node-class-set namespace-chip) "rounded-lg"))
    (is (contains? expanded-strings "Coin"))
    (is (contains? expanded-strings "USDC Value"))
    (is (contains? expanded-strings "Total Balance"))
    (is (contains? expanded-strings "Available Balance"))
    (is (contains? expanded-strings "PNL (ROE %)"))
    (is (contains? expanded-strings "Contract"))
    (is (contains? expanded-strings "Send"))
    (is (contains? expanded-strings "Transfer to Perps"))
    (is (not (contains? expanded-strings "Actions")))
    (is (zero? (hiccup/count-nodes expanded-card #(contains? (hiccup/node-class-set %) "rounded-full"))))
    (is (= false (get-in collapsed-button [1 :aria-expanded])))
    (is (contains? collapsed-strings "MEOW"))
    (is (not (contains? collapsed-strings "Available Balance")))))

(deftest balances-mobile-card-named-dex-transfer-opens-transfer-modal-with-context-test
  (let [row {:key "perps-usdc-xyz"
             :coin "USDC (Perps) xyz"
             :selection-coin "xyz:USDC"
             :transfer-dex "xyz"
             :transfer-to-perp? false
             :total-balance 1923.97
             :available-balance 1923.97
             :usdc-value 1923.97
             :pnl-value nil
             :pnl-pct nil
             :amount-decimals nil}
        content (test-support/render-balances-tab [row]
                                                  false
                                                  fixtures/default-sort-state
                                                  ""
                                                  {:balances "perps-usdc-xyz"})
        card (hiccup/find-by-data-role content "mobile-balance-card-perps-usdc-xyz")
        transfer-button (hiccup/find-first-node
                         card
                         #(and (= :button (first %))
                               (contains? (hiccup/direct-texts %) "Transfer to Spot")))]
    (is (some? card))
    (is (some? transfer-button))
    (is (= [[:actions/open-funding-transfer-modal
             :event.currentTarget/bounds
             nil
             {:dex "xyz" :to-perp? false}]]
           (get-in transfer-button [1 :on :click])))))

(deftest balances-mobile-card-normalizes-row-id-before-binding-expansion-state-test
  (let [row (assoc fixtures/sample-balance-row
                   :key "  usdc  "
                   :coin "USDC"
                   :total-balance 1.25
                   :available-balance 1.25
                   :usdc-value 1.25
                   :amount-decimals 8)
        content (test-support/render-balances-tab [row]
                                                  false
                                                  fixtures/default-sort-state
                                                  ""
                                                  {:balances "usdc"})
        expanded-card (hiccup/find-by-data-role content "mobile-balance-card-usdc")
        expanded-button (first (vec (hiccup/node-children expanded-card)))]
    (is (some? expanded-card))
    (is (= true (get-in expanded-button [1 :aria-expanded])))
    (is (= [[:actions/toggle-account-info-mobile-card :balances "usdc"]]
           (get-in expanded-button [1 :on :click])))))
