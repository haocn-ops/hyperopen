(ns hyperopen.state.trading-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.state.trading.order-form-key-policy :as key-policy]
            [hyperopen.trading.order-form-ownership :as ownership]
            [hyperopen.state.trading :as trading]
            [hyperopen.state.trading.test-support :as support]))

(def base-state support/base-state)

(deftest state-trading-facade-builds-submit-ready-policy-when-prereqs-present-test
  (let [state (assoc base-state
                     :asset-contexts {:BTC {:idx 5}}
                     :order-form (trading/default-order-form)
                     :order-form-ui (trading/default-order-form-ui))
        form (assoc (trading/default-order-form)
                    :type :limit
                    :side :buy
                    :size "1"
                    :price "100")
        policy (trading/submit-policy state form {:mode :submit
                                                  :agent-ready? true})]
    (is (nil? (:reason policy)))
    (is (false? (:disabled? policy)))
    (is (= "order" (get-in policy [:request :action :type])))))

(deftest state-trading-facade-composes-order-form-draft-and-ui-state-test
  (let [state (assoc base-state
                     :order-form {:entry-mode :market
                                  :type :limit
                                  :size "1"
                                  :price "100"}
                     :order-form-ui nil)
        draft (trading/order-form-draft state)
        ui-state (trading/order-form-ui-state state)]
    (is (= :market (:entry-mode draft)))
    (is (= :market (:type draft)))
    (is (= :market (:entry-mode ui-state)))
    (is (false? (:price-input-focused? ui-state)))))

(deftest order-form-ui-state-collapses-cross-margin-when-market-disallows-it-test
  (let [state (assoc base-state
                     :active-market {:coin "BTC"
                                     :mark 100
                                     :maxLeverage 40
                                     :szDecimals 4
                                     :marginMode "noCross"}
                     :order-form (trading/default-order-form)
                     :order-form-ui (assoc (trading/default-order-form-ui)
                                           :margin-mode :cross
                                           :margin-mode-dropdown-open? true))
        ui-state (trading/order-form-ui-state state)]
    (is (= :isolated (:margin-mode ui-state)))
    (is (false? (:margin-mode-dropdown-open? ui-state)))))

(deftest persist-order-form-strips-policy-deprecated-canonical-keys-test
  (let [raw (merge {:type :limit
                    :price "100"
                    :side :buy}
                   (zipmap key-policy/deprecated-canonical-order-form-keys
                           (repeat true)))
        persisted (trading/persist-order-form raw)]
    (is (= {:type :limit
            :price "100"
            :side :buy}
           persisted))
    (is (every? #(not (contains? persisted %))
                key-policy/deprecated-canonical-order-form-keys))))

(deftest order-form-ownership-enforce-field-ownership-strips-ui-owned-and-runtime-changes-test
  (let [state (assoc base-state
                     :order-form (trading/default-order-form)
                     :order-form-ui (trading/default-order-form-ui))
        transition (ownership/enforce-field-ownership
                    state
                    {:order-form (assoc (trading/default-order-form)
                                        :entry-mode :pro
                                        :ui-leverage 37
                                        :margin-mode :isolated
                                        :size-input-mode :base
                                        :size-input-source :percent
                                        :size-display "15")
                     :order-form-ui {:price-input-focused? true
                                     :tpsl-panel-open? true
                                     :tpsl-unit-dropdown-open? true}
                     :order-form-runtime {:submitting? true
                                          :error "oops"}})]
    (is (= (trading/default-order-form)
           (:order-form transition)))
    (is (= :limit (:entry-mode (:order-form-ui transition))))
    (is (= 37 (:ui-leverage (:order-form-ui transition))))
    (is (= 20 (:leverage-draft (:order-form-ui transition))))
    (is (true? (:price-input-focused? (:order-form-ui transition))))
    (is (true? (:tpsl-panel-open? (:order-form-ui transition))))
    (is (= {:submitting? true
            :error "oops"}
           (:order-form-runtime transition)))))

(deftest normalize-ui-leverage-clamps-to-bounds-test
  (let [state (assoc base-state
                     :active-market {:coin "BTC"
                                     :mark 100
                                     :maxLeverage 12
                                     :szDecimals 4})
        low (trading/normalize-ui-leverage state 0)
        high (trading/normalize-ui-leverage state 27)]
    (is (= 1 low))
    (is (= 12 high))))
