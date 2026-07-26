(ns hyperopen.views.trade.order-form-intent-adapter
  (:require [hyperopen.views.trade.order-form-runtime-gateway :as runtime-gateway]))

(def ^:private default-runtime-gateway
  (runtime-gateway/default-gateway))

(defn command->actions
  ([command]
   (command->actions default-runtime-gateway command))
  ([gateway command]
   (runtime-gateway/command->runtime-actions gateway command)))
