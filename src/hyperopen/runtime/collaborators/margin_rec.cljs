(ns hyperopen.runtime.collaborators.margin-rec
  (:require [hyperopen.margin-rec.actions :as margin-rec-actions]))

(defn action-deps []
  {:margin-rec-sync margin-rec-actions/margin-rec-sync
   :margin-rec-process-intents margin-rec-actions/margin-rec-process-intents
   :toggle-margin-rec-panel margin-rec-actions/toggle-margin-rec-panel
   :close-margin-rec-panel margin-rec-actions/close-margin-rec-panel
   :handle-margin-rec-panel-keydown margin-rec-actions/handle-margin-rec-panel-keydown
   :set-margin-rec-risk-mode margin-rec-actions/set-margin-rec-risk-mode
   :set-margin-rec-auto-topup margin-rec-actions/set-margin-rec-auto-topup
   :toggle-margin-rec-batch-panel margin-rec-actions/toggle-margin-rec-batch-panel
   :close-margin-rec-batch-panel margin-rec-actions/close-margin-rec-batch-panel
   :handle-margin-rec-batch-keydown margin-rec-actions/handle-margin-rec-batch-keydown
   :toggle-margin-rec-batch-selection margin-rec-actions/toggle-margin-rec-batch-selection
   :apply-margin-rec-batch margin-rec-actions/apply-margin-rec-batch})
