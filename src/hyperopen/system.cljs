(ns hyperopen.system
  (:require [hyperopen.account.history.funding-actions :as funding-actions]
            [hyperopen.account.history.order-actions :as order-actions]
            [hyperopen.config :as app-config]
            [hyperopen.router :as router]
            [hyperopen.runtime.state :as runtime-state]
            [hyperopen.service.product-context :as product-context]
            [hyperopen.service.tenant-config :as tenant-config]
            [hyperopen.state.app-defaults :as app-defaults]
            [hyperopen.state.trading :as trading]
            [hyperopen.wallet.agent-session :as agent-session]
            [hyperopen.websocket.client :as ws-client]))

(def ^:private default-funding-history-state
  funding-actions/default-funding-history-state)

(def ^:private default-order-history-state
  order-actions/default-order-history-state)

(def ^:private default-trade-history-state
  order-actions/default-trade-history-state)

(defn default-store-state
  []
  (let [tenant-override (get-in app-config/config [:tenant :override])
        tenant (tenant-config/active-tenant-config
                {:tenant/override tenant-override})
        tenant-theme (:theme/id tenant)
        context (product-context/build-product-context-view-model tenant)
        initial-route (product-context/safe-route context
                                                  (router/current-path))]
    (-> (app-defaults/default-app-state
         {:websocket-health (ws-client/get-health-snapshot)
          :default-agent-state (agent-session/default-agent-state)
          :default-order-form (trading/default-order-form)
          :default-order-form-ui (trading/default-order-form-ui)
          :default-order-form-runtime (trading/default-order-form-runtime)
          :default-trade-history (default-trade-history-state)
          :default-funding-history (default-funding-history-state)
          :default-order-history (default-order-history-state)})
        (assoc-in [:router :path] initial-route)
        (assoc :tenant/override tenant-override)
        (assoc-in [:ui :theme] tenant-theme)
        ;; Defer lower desktop trade surfaces until the first post-render startup pass.
        (assoc-in [:trade-ui :desktop-secondary-panels-ready?] false))))

(defn make-system
  ([] (make-system {}))
  ([{:keys [store runtime]}]
   {:store (or store (atom (default-store-state)))
    :runtime (or runtime (runtime-state/make-runtime-state))}))

(defonce system
  (make-system {:runtime runtime-state/runtime}))

(def store
  (:store system))

(def runtime
  (:runtime system))
