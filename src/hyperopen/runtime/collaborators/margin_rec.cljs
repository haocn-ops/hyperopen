(ns hyperopen.runtime.collaborators.margin-rec
  (:require [hyperopen.margin-rec.actions :as margin-rec-actions]))

(defn action-deps []
  {:margin-rec-sync margin-rec-actions/margin-rec-sync
   :margin-rec-process-intents margin-rec-actions/margin-rec-process-intents
   :toggle-margin-rec-panel margin-rec-actions/toggle-margin-rec-panel
   :set-margin-rec-risk-mode margin-rec-actions/set-margin-rec-risk-mode
   :set-margin-rec-auto-topup margin-rec-actions/set-margin-rec-auto-topup})
