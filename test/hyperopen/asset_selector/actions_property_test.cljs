(ns hyperopen.asset-selector.actions-property-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [hyperopen.asset-selector.actions :as actions]
            [hyperopen.state.trading :as trading]))

(defn- save-many-path-values
  [effects]
  (-> effects first second))

(defn- apply-save-many-path-values
  [state effects]
  (reduce (fn [next-state [path value]]
            (assoc-in next-state path value))
          state
          (save-many-path-values effects)))

(defn- path-value
  [effects target-path]
  (some (fn [[path value]]
          (when (= path target-path)
            value))
        (save-many-path-values effects)))

(def ^:private asset-switch-test-coins
  ["BTC" "ETH" "xyz:SILVER"])

(def ^:private asset-switch-scenario-gen
  (gen/let [current (gen/elements asset-switch-test-coins)
            same-asset? gen/boolean
            next (if same-asset?
                   (gen/return current)
                   (gen/elements (vec (remove #(= % current) asset-switch-test-coins))))
            price (gen/elements ["1" "101" "2500"])
            size (gen/elements ["0.0001" "1" "25"])
            size-percent (gen/choose 1 100)
            size-display (gen/elements ["10" "25" "1000"])
            error (gen/elements ["Order 1: Order has invalid size."
                                 "stale order error"
                                 "previous asset rejected"])]
    {:current current
     :next next
     :price price
     :size size
     :size-percent size-percent
     :size-display size-display
     :error error}))

(deftest select-asset-cross-asset-order-draft-invariant-property-test
  (let [property
        (prop/for-all [{:keys [current next price size size-percent size-display error]}
                       asset-switch-scenario-gen]
          (let [state {:active-asset current
                       :asset-selector {:market-by-key {}}
                       :order-form {:type :limit
                                    :side :buy
                                    :price price
                                    :size size
                                    :size-percent size-percent}
                       :order-form-ui {:size-input-mode :quote
                                       :size-input-source :manual
                                       :size-display size-display
                                       :price-input-focused? true}
                       :order-form-runtime {:submitting? false
                                            :error error}}
                effects (actions/select-asset state
                                              {:key (str "perp:" next)
                                               :coin next})
                next-state (apply-save-many-path-values state effects)
                switched-asset? (not= current next)]
            (if (not switched-asset?)
              (and (nil? (path-value effects [:order-form]))
                   (nil? (path-value effects [:order-form-ui]))
                   (nil? (path-value effects [:order-form-runtime])))
              (let [policy (trading/submit-policy next-state
                                                  (trading/order-form-draft next-state)
                                                  {:mode :submit
                                                   :agent-ready? true})]
                (and (= "" (get-in next-state [:order-form :price]))
                     (= "" (get-in next-state [:order-form :size]))
                     (zero? (get-in next-state [:order-form :size-percent]))
                     (= "" (get-in next-state [:order-form-ui :size-display]))
                     (= :manual (get-in next-state [:order-form-ui :size-input-source]))
                     (nil? (get-in next-state [:order-form-runtime :error]))
                     (:disabled? policy)
                     (some #{"Size"} (:required-fields policy))
                     (nil? (:request policy)))))))]
    (let [result (tc/quick-check 120 property)]
      (is (:pass? result) (pr-str result)))))
