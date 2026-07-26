(ns hyperopen.api.projections.user-abstraction
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]))

(defn apply-user-abstraction-snapshot
  [state requested-address snapshot]
  (let [active-address (some-> (account-context/effective-account-address state)
                               str
                               str/lower-case)]
    (if (= requested-address active-address)
      (assoc state :account snapshot)
      state)))
