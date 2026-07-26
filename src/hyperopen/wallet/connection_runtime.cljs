(ns hyperopen.wallet.connection-runtime
  (:require [clojure.string :as str]))

(defn connect-wallet!
  [{:keys [store log-fn request-connection! provider-id]}]
  (log-fn "Connecting wallet...")
  (if (seq provider-id)
    (request-connection! store provider-id)
    (request-connection! store)))

(defn disconnect-wallet!
  [{:keys [store
           log-fn
           clear-wallet-copy-feedback-timeout!
           clear-order-feedback-toast-timeout!
           clear-order-feedback-toast!
           set-disconnected!]}]
  (log-fn "Disconnecting wallet...")
  (clear-wallet-copy-feedback-timeout!)
  (clear-order-feedback-toast-timeout!)
  (clear-order-feedback-toast! store)
  (set-disconnected! store))

(defn should-auto-enable-agent-trading?
  [state connected-address]
  (let [wallet-address (some-> (get-in state [:wallet :address]) str str/lower-case)
        connected-address* (some-> connected-address str str/lower-case)
        connected? (boolean (get-in state [:wallet :connected?]))
        agent-status (get-in state [:wallet :agent :status])]
    (and connected?
         (seq wallet-address)
         (seq connected-address*)
         (= wallet-address connected-address*)
         (= :not-ready agent-status))))

(defn handle-wallet-connected!
  [{:keys [store connected-address should-auto-enable-agent-trading? dispatch!]}]
  (let [state @store]
    (when (should-auto-enable-agent-trading? state connected-address)
      (dispatch! store nil [[:actions/enable-agent-trading]]))))
