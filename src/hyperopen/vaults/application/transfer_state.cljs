(ns hyperopen.vaults.application.transfer-state
  (:require [hyperopen.vaults.domain.transfer-policy :as transfer-policy]))

(defn default-vault-transfer-modal-state
  []
  {:open? false
   :mode transfer-policy/default-vault-transfer-mode
   :vault-address nil
   :amount-input ""
   :withdraw-all? false
   :submitting? false
   :error nil})
