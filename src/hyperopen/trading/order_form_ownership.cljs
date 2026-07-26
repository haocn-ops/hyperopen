(ns hyperopen.trading.order-form-ownership
  (:require [hyperopen.state.trading :as trading]))

(defn persist-order-form
  "Strip UI-owned and legacy compatibility keys from an order form."
  [form]
  (trading/persist-order-form form))

(defn effective-order-form-ui
  "Return normalized order-form UI state with ownership invariants applied."
  [form ui]
  (trading/effective-order-form-ui form ui))

(defn order-form-ui-state
  "Return the effective order-form UI state for the current app state."
  [state]
  (trading/order-form-ui-state state))

(defn enforce-field-ownership
  [state transition]
  (if-not (map? transition)
    transition
    (let [order-form (:order-form transition)
          order-form-ui (:order-form-ui transition)
          order-form-runtime (:order-form-runtime transition)
          persisted-form (when (map? order-form)
                           (persist-order-form order-form))
          persisted-ui (when (or (map? order-form-ui)
                                 (map? order-form))
                         (let [working-form (or order-form (trading/order-form-draft state))
                               merged-ui (merge (order-form-ui-state state)
                                                (or order-form-ui {})
                                                (trading/order-form-ui-overrides-from-form order-form))]
                           (effective-order-form-ui working-form merged-ui)))]
      (cond-> {}
        (map? persisted-form) (assoc :order-form persisted-form)
        (map? persisted-ui) (assoc :order-form-ui persisted-ui)
        (map? order-form-runtime) (assoc :order-form-runtime order-form-runtime)))))
