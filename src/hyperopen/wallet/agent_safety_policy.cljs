(ns hyperopen.wallet.agent-safety-policy
  (:require [hyperopen.trading-settings :as trading-settings]))

(def extended-ahead-ms
  (* 4 60 60 1000))

(def extended-refresh-ms
  (* 15 60 1000))

(defn mode
  [state]
  (trading-settings/open-order-safety-mode state))

(defn policy
  [state {:keys [strict-ahead-ms strict-refresh-ms]}]
  (case (mode state)
    :off
    {:mode :off
     :enabled? false
     :ahead-ms nil
     :refresh-ms nil}

    :extended
    {:mode :extended
     :enabled? true
     :ahead-ms extended-ahead-ms
     :refresh-ms extended-refresh-ms}

    {:mode :strict
     :enabled? true
     :ahead-ms strict-ahead-ms
     :refresh-ms strict-refresh-ms}))
