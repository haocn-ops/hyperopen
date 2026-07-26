(ns hyperopen.funding.contracts
  (:require [hyperopen.schema.funding-modal-contracts :as schema-contracts]))

(defn funding-modal-vm-valid?
  [view-model]
  (schema-contracts/funding-modal-vm-valid? view-model))

(defn assert-funding-modal-vm!
  [view-model context]
  (schema-contracts/assert-funding-modal-vm! view-model context))
