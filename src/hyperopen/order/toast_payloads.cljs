(ns hyperopen.order.toast-payloads)

(defn- cancel-success-toast-message
  [success-count]
  (if (= 1 success-count)
    "Order canceled."
    (str success-count " orders canceled.")))

(defn cancel-success-toast-payload
  [success-count]
  (let [headline (if (= 1 success-count)
                   "Order canceled"
                   (str success-count " orders canceled"))]
    {:toast-surface :order-canceled
     :headline headline
     :subline "Open orders updated"
     :message (cancel-success-toast-message success-count)}))

(defn twap-cancel-success-toast-payload
  []
  {:toast-surface :order-canceled
   :headline "TWAP terminated"
   :subline "Open orders updated"
   :message "TWAP terminated."})
