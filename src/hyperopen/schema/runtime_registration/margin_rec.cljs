(ns hyperopen.schema.runtime-registration.margin-rec)

(def effect-binding-rows
  [[:effects/margin-rec-fetch-fills :margin-rec-fetch-fills]
   [:effects/margin-rec-compute :margin-rec-compute]])

(def action-binding-rows
  [[:actions/margin-rec-sync :margin-rec-sync]
   [:actions/margin-rec-process-intents :margin-rec-process-intents]
   [:actions/toggle-margin-rec-panel :toggle-margin-rec-panel]
   [:actions/close-margin-rec-panel :close-margin-rec-panel]
   [:actions/handle-margin-rec-panel-keydown :handle-margin-rec-panel-keydown]
   [:actions/set-margin-rec-risk-mode :set-margin-rec-risk-mode]
   [:actions/set-margin-rec-auto-topup :set-margin-rec-auto-topup]
   [:actions/toggle-margin-rec-batch-panel :toggle-margin-rec-batch-panel]
   [:actions/close-margin-rec-batch-panel :close-margin-rec-batch-panel]
   [:actions/handle-margin-rec-batch-keydown :handle-margin-rec-batch-keydown]
   [:actions/toggle-margin-rec-batch-selection :toggle-margin-rec-batch-selection]
   [:actions/apply-margin-rec-batch :apply-margin-rec-batch]])

(def effect-order-policy-required-action-ids
  #{:actions/margin-rec-sync
    :actions/margin-rec-process-intents
    :actions/apply-margin-rec-batch})
