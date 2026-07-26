(ns hyperopen.views.spectate-mode-modal-module
  (:require [hyperopen.views.spectate-mode-modal :as spectate-mode-modal]))

(defn ^:export spectate-mode-modal-view
  [state]
  (spectate-mode-modal/spectate-mode-modal-view state))

(goog/exportSymbol "hyperopen.views.spectate_mode_modal_module.spectate_mode_modal_view" spectate-mode-modal-view)
