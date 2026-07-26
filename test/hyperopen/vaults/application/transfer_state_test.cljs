(ns hyperopen.vaults.application.transfer-state-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.vaults.actions :as actions]
            [hyperopen.vaults.application.transfer-state :as transfer-state]
            [hyperopen.vaults.domain.transfer-policy :as transfer-policy]))

(deftest default-vault-transfer-modal-state-owns-the-modal-ui-shape-test
  (let [modal-state (transfer-state/default-vault-transfer-modal-state)]
    (is (= false (:open? modal-state)))
    (is (= transfer-policy/default-vault-transfer-mode
           (:mode modal-state)))
    (is (nil? (:vault-address modal-state)))
    (is (= "" (:amount-input modal-state)))
    (is (= false (:withdraw-all? modal-state)))
    (is (= false (:submitting? modal-state)))
    (is (nil? (:error modal-state)))
    (is (= modal-state
           (transfer-state/default-vault-transfer-modal-state)))
    (is (= modal-state
           (actions/default-vault-transfer-modal-state)))))
