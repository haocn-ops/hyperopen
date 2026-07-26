(ns hyperopen.state.trading.order-form-ownership-formal-conformance-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.formal.order-form-ownership-vectors :as vectors]
            [hyperopen.schema.order-form-ownership-contracts :as contracts]
            [hyperopen.state.trading :as trading]
            [hyperopen.state.trading.test-support :as support]
            [hyperopen.trading.order-form-ownership :as ownership]
            [hyperopen.trading.order-form-transitions :as transitions]))

(def base-state support/base-state)

(defn- state-with
  ([order-form order-form-ui]
   (state-with order-form order-form-ui {}))
  ([order-form order-form-ui {:keys [market-max-leverage cross-margin-allowed?]}]
   (let [order-form-ui* (cond-> order-form-ui
                          (and (nil? (:leverage-draft order-form-ui))
                               (some? (:ui-leverage order-form)))
                          (assoc :leverage-draft (:ui-leverage order-form)))]
     (cond-> (assoc base-state
                    :order-form order-form
                    :order-form-ui order-form-ui*)
     market-max-leverage (assoc-in [:active-market :maxLeverage] market-max-leverage)
     (false? cross-margin-allowed?) (assoc-in [:active-market :marginMode] "noCross")))))

(deftest persisted-form-vectors-conform-to-generated-spec-test
  (doseq [{:keys [id input expected]} vectors/persisted-form-vectors]
    (testing (name id)
      (let [actual (ownership/persist-order-form input)
            projection (contracts/persisted-form-projection actual)]
        (contracts/assert-persisted-form-projection!
         projection
         {:vector id})
        (is (= expected actual))))))

(deftest blocked-write-vectors-conform-to-generated-spec-test
  (doseq [{:keys [id input expected]} vectors/blocked-write-vectors]
    (testing (name id)
      (let [state (state-with (trading/default-order-form)
                              (trading/default-order-form-ui))
            actual (transitions/update-order-form state
                                                  (:path input)
                                                  (:value input))
            projection (contracts/transition-projection actual)]
        (contracts/assert-transition-projection!
         projection
         {:vector id})
        (is (= expected actual))))))

(deftest effective-ui-vectors-conform-to-generated-spec-test
  (doseq [{:keys [id input expected]} vectors/effective-ui-vectors]
    (testing (name id)
      (let [form (:order-form input)
            ui (:order-form-ui input)
            actual (ownership/effective-order-form-ui form ui)
            projection (contracts/effective-ui-projection form ui)]
        (contracts/assert-effective-ui-projection!
         projection
         {:vector id})
        (is (= (assoc expected :type (:type form))
               projection))
        (is (= expected actual))))))

(deftest order-form-ui-state-vectors-conform-to-generated-spec-test
  (doseq [{:keys [id input expected]} vectors/order-form-ui-state-vectors]
    (testing (name id)
      (let [state (state-with (:order-form input)
                              (:order-form-ui input)
                              {:cross-margin-allowed? (:cross-margin-allowed? input)})
            actual (trading/order-form-ui-state state)
            projection (contracts/order-form-ui-state-projection state)
            expected* (assoc expected
                             :type (:type (trading/order-form-draft state))
                             :cross-margin-allowed? (:cross-margin-allowed? input))]
        (contracts/assert-order-form-ui-state-projection!
         projection
         {:vector id})
        (is (= (select-keys expected* [:cross-margin-allowed?
                                       :entry-mode
                                       :ui-leverage
                                       :leverage-draft
                                       :margin-mode
                                       :margin-mode-dropdown-open?])
               (select-keys projection [:cross-margin-allowed?
                                        :entry-mode
                                        :ui-leverage
                                        :leverage-draft
                                        :margin-mode
                                        :margin-mode-dropdown-open?])))
        (is (= (:margin-mode expected) (:margin-mode actual)))
        (is (= (:margin-mode-dropdown-open? expected) (:margin-mode-dropdown-open? actual)))
        (is (= (:entry-mode expected*) (:entry-mode actual)))
        (is (= (:ui-leverage expected*) (:ui-leverage actual)))
        (is (= (:leverage-draft expected*) (:leverage-draft actual)))))))

(deftest reduce-only-scale-leverage-and-offset-cache-vectors-conform-to-generated-spec-test
  (doseq [{:keys [id input expected]} vectors/reduce-only-vectors]
    (testing (str "reduce-only " (name id))
      (let [state (state-with (:order-form input) (:order-form-ui input))
            actual (transitions/update-order-form state [:reduce-only] true)
            projection (contracts/transition-projection actual)]
        (contracts/assert-transition-projection!
         projection
         {:vector id})
        (is (= (get-in expected [:order-form :reduce-only])
               (get-in actual [:order-form :reduce-only])))
        (is (= (get-in expected [:order-form :tp :enabled?])
               (get-in actual [:order-form :tp :enabled?])))
        (is (= (get-in expected [:order-form :sl :enabled?])
               (get-in actual [:order-form :sl :enabled?])))
        (is (= (get-in expected [:order-form-ui :tpsl-panel-open?])
               (get-in actual [:order-form-ui :tpsl-panel-open?])))
        (is (= (get-in expected [:order-form-runtime :error])
               (get-in actual [:order-form-runtime :error]))))))
  (doseq [{:keys [id input expected]} vectors/scale-vectors]
    (testing (str "scale " (name id))
      (let [state (state-with (:order-form input) (:order-form-ui input))
            actual (transitions/update-order-form state [:type] :scale)
            projection (contracts/transition-projection actual)]
        (contracts/assert-transition-projection!
         projection
         {:vector id})
        (is (= (get-in expected [:order-form :type])
               (get-in actual [:order-form :type])))
        (is (= (get-in expected [:order-form-ui :entry-mode])
               (get-in actual [:order-form-ui :entry-mode])))
        (is (= (get-in expected [:order-form-ui :tpsl-panel-open?])
               (get-in actual [:order-form-ui :tpsl-panel-open?])))
        (is (= (get-in expected [:order-form-ui :tpsl-unit-dropdown-open?])
               (get-in actual [:order-form-ui :tpsl-unit-dropdown-open?])))))
  (doseq [{:keys [id input expected]} vectors/leverage-vectors]
    (testing (str "leverage " (name id))
      (let [state (state-with (:order-form input)
                              (:order-form-ui input)
                              {:market-max-leverage (:market-max-leverage input)})
            leverage (get-in input [:order-form :ui-leverage])
            actual (transitions/set-order-ui-leverage state leverage)
            projection (contracts/transition-projection actual)
            persisted-projection (contracts/persisted-form-projection (:order-form actual))]
        (contracts/assert-transition-projection!
         projection
         {:vector id})
        (contracts/assert-persisted-form-projection!
         persisted-projection
         {:vector id
          :phase :persisted-order-form})
        (is (nil? (get-in actual [:order-form :ui-leverage])))
        (is (nil? (get-in actual [:order-form :leverage-draft])))
        (is (= (get-in expected [:order-form-ui :ui-leverage])
               (get-in actual [:order-form-ui :ui-leverage])))
        (is (= (get-in expected [:order-form-ui :leverage-draft])
               (get-in actual [:order-form-ui :leverage-draft])))
        (is (= (get-in expected [:order-form-ui :margin-mode])
               (get-in actual [:order-form-ui :margin-mode])))
        (is (= (get-in expected [:order-form-runtime :error])
               (get-in actual [:order-form-runtime :error]))))))
  (doseq [{:keys [id input expected]} vectors/offset-cache-vectors]
    (testing (str "offset cache " (name id))
      (let [state (state-with (:order-form input)
                              (trading/default-order-form-ui))
            actual (transitions/update-order-form state
                                                  (:path input)
                                                  (:value input))
            projection (contracts/transition-projection actual)]
        (contracts/assert-transition-projection!
         projection
         {:vector id})
        (is (= (get-in expected [:order-form :tp :offset-input])
               (get-in actual [:order-form :tp :offset-input])))
        (is (= (get-in expected [:order-form :sl :offset-input])
               (get-in actual [:order-form :sl :offset-input])))
        (is (= (get-in expected [:order-form-runtime :submitting?])
               (get-in actual [:order-form-runtime :submitting?])))
        (is (= (get-in expected [:order-form-runtime :error])
               (get-in actual [:order-form-runtime :error])))))))
)
