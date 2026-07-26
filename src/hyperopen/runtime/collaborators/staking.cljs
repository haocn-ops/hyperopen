(ns hyperopen.runtime.collaborators.staking
  (:require [hyperopen.staking.actions :as staking-actions]))

(defn action-deps []
  {:load-staking-route staking-actions/load-staking-route
   :load-staking staking-actions/load-staking
   :set-staking-active-tab staking-actions/set-staking-active-tab
   :toggle-staking-validator-timeframe-menu staking-actions/toggle-staking-validator-timeframe-menu
   :close-staking-validator-timeframe-menu staking-actions/close-staking-validator-timeframe-menu
   :set-staking-validator-timeframe staking-actions/set-staking-validator-timeframe
   :set-staking-validator-page staking-actions/set-staking-validator-page
   :set-staking-validator-show-all staking-actions/set-staking-validator-show-all
   :set-staking-validator-sort staking-actions/set-staking-validator-sort
   :open-staking-action-popover staking-actions/open-staking-action-popover
   :close-staking-action-popover staking-actions/close-staking-action-popover
   :handle-staking-action-popover-keydown staking-actions/handle-staking-action-popover-keydown
   :set-staking-transfer-direction staking-actions/set-staking-transfer-direction
   :set-staking-form-field staking-actions/set-staking-form-field
   :select-staking-validator staking-actions/select-staking-validator
   :set-staking-deposit-amount-to-max staking-actions/set-staking-deposit-amount-to-max
   :set-staking-withdraw-amount-to-max staking-actions/set-staking-withdraw-amount-to-max
   :set-staking-delegate-amount-to-max staking-actions/set-staking-delegate-amount-to-max
   :set-staking-undelegate-amount-to-max staking-actions/set-staking-undelegate-amount-to-max
   :submit-staking-deposit staking-actions/submit-staking-deposit
   :submit-staking-withdraw staking-actions/submit-staking-withdraw
   :submit-staking-delegate staking-actions/submit-staking-delegate
   :submit-staking-undelegate staking-actions/submit-staking-undelegate})
