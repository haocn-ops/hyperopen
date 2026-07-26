(ns hyperopen.views.trade.order-form-placeholders)

(def event-target-value :order-form.event/target-value)
(def event-target-checked :order-form.event/target-checked)
(def event-key :order-form.event/key)

(defn resolve-placeholder-token [token]
  (case token
    :order-form.event/target-value [:event.target/value]
    :order-form.event/target-checked [:event.target/checked]
    :order-form.event/key [:event/key]
    token))
