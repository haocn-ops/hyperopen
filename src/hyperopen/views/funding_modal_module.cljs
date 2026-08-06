(ns hyperopen.views.funding-modal-module
  (:require [hyperopen.runtime.effect-adapters.funding-workflow :as funding-workflow]
            [hyperopen.runtime.effect-adapters.order :as order-adapters]
            [hyperopen.views.funding-modal :as funding-modal]))

(defn ^:export funding-modal-view
  [state]
  (funding-modal/funding-modal-view state))

(goog/exportSymbol "hyperopen.views.funding_modal_module.funding_modal_view" funding-modal-view)

(defn ^:export effect-deps
  [runtime]
  (let [show-toast! (fn [store kind message]
                      (order-adapters/show-order-feedback-toast!
                       runtime store kind message))]
    {:api
     {:api-fetch-hyperliquid-legal-check
      funding-workflow/api-fetch-hyperliquid-legal-check-effect
      :api-fetch-hyperunit-fee-estimate
      funding-workflow/api-fetch-hyperunit-fee-estimate-effect
      :api-fetch-hyperunit-withdrawal-queue
      funding-workflow/api-fetch-hyperunit-withdrawal-queue-effect
      :api-submit-funding-transfer
      (fn [ctx store request]
        (apply funding-workflow/api-submit-funding-transfer-effect
               [ctx store request {:show-toast! show-toast!}]))
      :api-submit-funding-send
      (fn [ctx store request]
        (apply funding-workflow/api-submit-funding-send-effect
               [ctx store request {:show-toast! show-toast!}]))
      :api-submit-funding-repay
      (fn [ctx store request]
        (apply funding-workflow/api-submit-funding-repay-effect
               [ctx store request {:show-toast! show-toast!}]))
      :api-submit-funding-withdraw
      (fn [ctx store request]
        (apply funding-workflow/api-submit-funding-withdraw-effect
               [ctx store request {:show-toast! show-toast!}]))
      :api-submit-funding-deposit
      (fn [ctx store request]
        (apply funding-workflow/api-submit-funding-deposit-effect
               [ctx store request {:show-toast! show-toast!}]))}}))

(goog/exportSymbol "hyperopen.views.funding_modal_module.effect_deps" effect-deps)
