(ns hyperopen.views.vaults.vm
  (:require [hyperopen.vaults.application.list-vm :as list-vm]
            [hyperopen.vaults.infrastructure.routes :as vault-routes]))

(def vault-route?
  vault-routes/vault-route?)

(def vault-detail-route?
  vault-routes/vault-detail-route?)

(def selected-vault-address
  vault-routes/selected-vault-address)

(def reset-vault-list-vm-cache!
  list-vm/reset-vault-list-vm-cache!)

(def build-startup-preview-record
  list-vm/build-startup-preview-record)

(def vault-list-vm
  list-vm/vault-list-vm)
