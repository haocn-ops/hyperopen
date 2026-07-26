(ns hyperopen.vaults.route-runtime-module-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.runtime.effect-adapters.vaults :as vault-effect-adapters]
            [hyperopen.schema.runtime-registration.vaults :as vault-registration]
            [hyperopen.vaults.route-runtime-module]))

(def ^:private eager-vault-handler-keys
  #{:load-vault-route
    :load-vaults
    :load-vault-detail})

(deftest vault-route-runtime-module-exports-vault-runtime-catalog-test
  (let [module (aget js/globalThis "hyperopen" "vaults" "route_runtime_module")
        action-deps-fn (aget module "action_deps")
        effect-deps-fn (aget module "effect_deps")
        exported-action-deps (:vaults
                              (action-deps-fn))
        exported-effect-deps (:api
                              (effect-deps-fn nil))
        registered-action-keys (->> vault-registration/action-binding-rows
                                    (map second)
                                    (remove eager-vault-handler-keys)
                                    set)
        registered-effect-keys (->> vault-registration/effect-binding-rows
                                    (map second)
                                    set)]
    (is (fn? action-deps-fn))
    (is (fn? effect-deps-fn))
    (is (= registered-action-keys
           (set (keys exported-action-deps))))
    (is (= registered-effect-keys
           (set (keys exported-effect-deps))))
    (is (identical? vault-effect-adapters/api-fetch-vault-index-effect
                    (:api-fetch-vault-index exported-effect-deps)))))
