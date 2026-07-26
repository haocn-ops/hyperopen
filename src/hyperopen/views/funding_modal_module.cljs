(ns hyperopen.views.funding-modal-module
  (:require [hyperopen.views.funding-modal :as funding-modal]))

(defn ^:export funding-modal-view
  [state]
  (funding-modal/funding-modal-view state))

(goog/exportSymbol "hyperopen.views.funding_modal_module.funding_modal_view" funding-modal-view)
