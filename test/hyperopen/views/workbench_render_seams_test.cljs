(ns hyperopen.views.workbench-render-seams-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.walk :as walk]
            [hyperopen.funding.actions :as funding-actions]
            [hyperopen.views.funding-modal :as funding-modal]
            [hyperopen.views.trade.order-form-handlers :as order-form-handlers]
            [hyperopen.views.trade.order-form-vm :as order-form-vm]
            [hyperopen.views.trade.order-form.test-support :refer [base-state]]
            [hyperopen.views.trade.order-form-view :as order-form-view]))

(defn- normalize-hiccup-for-seam-comparison
  [node]
  (walk/postwalk
   (fn [value]
     (if (map? value)
       (cond-> value
         (fn? (:replicant/on-render value))
         (assoc :replicant/on-render ::on-render))
       value))
   node))

(defn- funding-state
  []
  {:wallet {:address "0x1234567890abcdef1234567890abcdef12345678"}
   :spot {:clearinghouse-state {:balances [{:coin "USDC" :available "12.5" :total "12.5" :hold "0"}]}}
   :webdata2 {:clearinghouseState {:availableToWithdraw "8.5"
                                   :marginSummary {:accountValue "20"
                                                   :totalMarginUsed "11.5"}}}
   :funding-ui {:modal {:open? true
                        :mode :deposit
                        :deposit-step :asset-select
                        :deposit-search-input ""
                        :deposit-selected-asset-key nil
                        :amount-input ""}}})

(deftest render-order-form-seam-matches-wrapper-output-test
  (let [state (base-state {:type :limit :price "100" :size "1"})
        via-wrapper (order-form-view/order-form-view state)
        via-seam (order-form-view/render-order-form
                  {:state state
                   :vm (order-form-vm/order-form-vm state)
                   :handlers (order-form-handlers/build-handlers)
                   :ui {:margin-mode-dropdown-open? (boolean (get-in state [:order-form-ui :margin-mode-dropdown-open?]))
                        :leverage-popover-open? (boolean (get-in state [:order-form-ui :leverage-popover-open?]))
                        :leverage-draft (get-in state [:order-form-ui :leverage-draft])
                        :size-unit-dropdown-open? (boolean (get-in state [:order-form-ui :size-unit-dropdown-open?]))
                        :tif-dropdown-open? (boolean (get-in state [:order-form-ui :tif-dropdown-open?]))
                        :max-leverage 40
                        :cross-margin-allowed? true}})]
    (is (= via-wrapper via-seam))))

(deftest render-funding-modal-seam-matches-wrapper-output-test
  (let [state (funding-state)
        via-wrapper (funding-modal/funding-modal-view state)
        via-seam (funding-modal/render-funding-modal
                  (funding-actions/funding-modal-view-model state))]
    (is (= (normalize-hiccup-for-seam-comparison via-wrapper)
           (normalize-hiccup-for-seam-comparison via-seam)))))
