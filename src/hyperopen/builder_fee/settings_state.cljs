(ns hyperopen.builder-fee.settings-state
  (:require [hyperopen.account.context :as account-context]
            [hyperopen.builder-fee.policy :as policy]
            [hyperopen.config :as app-config]))

(defn review-row-state
  [state builder-fee]
  (let [review-status (get-in state [:header-ui :builder-fee-review :status])
        approved? (policy/approved? (get-in state [:builder-fee :approval])
                                    (account-context/owner-address state)
                                    (:builder-address builder-fee)
                                    (get-in app-config/config [:hyperliquid :network])
                                    (:fee-tenths-bp builder-fee))]
    {:title (cond
              approved? "Enabled"
              (= :reviewing review-status) "Confirm and enable"
              :else "Review and enable")
     :disabled? (or approved? (= :submitting review-status))
     :action (when-not approved?
               [[(if (= :reviewing review-status)
                   :actions/confirm-builder-fee-review
                   :actions/request-builder-fee-review)]])}))
