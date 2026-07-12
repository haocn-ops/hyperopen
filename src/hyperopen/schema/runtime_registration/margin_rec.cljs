(ns hyperopen.schema.runtime-registration.margin-rec)

(def effect-binding-rows
  [[:effects/margin-rec-fetch-fills :margin-rec-fetch-fills]
   [:effects/margin-rec-compute :margin-rec-compute]])

(def action-binding-rows
  [[:actions/margin-rec-sync :margin-rec-sync]
   [:actions/margin-rec-process-intents :margin-rec-process-intents]
   [:actions/toggle-margin-rec-panel :toggle-margin-rec-panel]
   [:actions/set-margin-rec-risk-mode :set-margin-rec-risk-mode]
   [:actions/set-margin-rec-auto-topup :set-margin-rec-auto-topup]])

(def effect-order-policy-required-action-ids
  #{:actions/margin-rec-sync
    :actions/margin-rec-process-intents})
