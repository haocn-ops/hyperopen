(ns hyperopen.app.effects-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.app.effects :as app-effects]
            [hyperopen.route-modules :as route-modules]
            [hyperopen.runtime.effect-adapters :as effect-adapters]))

(deftest runtime-effect-deps-builds-runtime-bound-handlers-via-factories-test
  (let [runtime {:runtime-id :test}
        funding-workflow-effect-keys
        [:api-fetch-hyperliquid-legal-check
         :api-fetch-hyperunit-fee-estimate
         :api-fetch-hyperunit-withdrawal-queue
         :api-submit-funding-transfer
         :api-submit-funding-send
         :api-submit-funding-repay
         :api-submit-funding-withdraw
         :api-submit-funding-deposit]
        queue-handler (fn [& _] nil)
        refresh-handler (fn [& _] nil)
        disconnect-handler (fn [& _] nil)
        copy-handler (fn [& _] nil)
        copy-link-handler (fn [& _] nil)
        optimizer-run-handler (fn [& _] nil)
        optimizer-pipeline-handler (fn [& _] nil)
        vault-transfer-handler (fn [& _] nil)
        submit-handler (fn [& _] nil)
        cancel-handler (fn [& _] nil)
        margin-handler (fn [& _] nil)
        funding-handlers (into {}
                               (map (fn [handler-key]
                                      [handler-key (fn [& _] handler-key)]))
                               funding-workflow-effect-keys)
        lazy-effect-calls (atom [])]
    (with-redefs [effect-adapters/make-queue-asset-icon-status
                  (fn [runtime*]
                    (is (identical? runtime runtime*))
                    queue-handler)
                  effect-adapters/make-refresh-websocket-health
                  (fn [runtime*]
                    (is (identical? runtime runtime*))
                    refresh-handler)
                  effect-adapters/make-disconnect-wallet
                  (fn [runtime*]
                    (is (identical? runtime runtime*))
                    disconnect-handler)
                  effect-adapters/make-copy-wallet-address
                  (fn [runtime*]
                    (is (identical? runtime runtime*))
                    copy-handler)
                  effect-adapters/make-copy-spectate-link
                  (fn [runtime*]
                    (is (identical? runtime runtime*))
                    copy-link-handler)
                  route-modules/lazy-route-effect-leaf-deps
                  (fn [runtime* module-id group-key handler-keys]
                    (is (identical? runtime runtime*))
                    (swap! lazy-effect-calls conj [module-id group-key handler-keys])
                    (case [module-id group-key]
                      [:portfolio :portfolio-optimizer]
                      {:portfolio-optimizer
                       {:run-portfolio-optimizer optimizer-run-handler
                        :run-portfolio-optimizer-pipeline optimizer-pipeline-handler}}

                      [:vaults :api]
                      {:api
                       {:api-submit-vault-transfer vault-transfer-handler}}

                      [:funding-modal :api]
                      {:api funding-handlers}

                      {}))
                  effect-adapters/make-api-submit-order
                  (fn [runtime*]
                    (is (identical? runtime runtime*))
                    submit-handler)
                  effect-adapters/make-api-cancel-order
                  (fn [runtime*]
                    (is (identical? runtime runtime*))
                    cancel-handler)
                  effect-adapters/make-api-submit-position-margin
                  (fn [runtime*]
                    (is (identical? runtime runtime*))
                    margin-handler)]
      (let [deps (app-effects/runtime-effect-deps runtime)]
        (is (identical? queue-handler
                        (get-in deps [:asset-selector :queue-asset-icon-status])))
        (is (identical? effect-adapters/sync-asset-selector-active-ctx-subscriptions
                        (get-in deps [:asset-selector :sync-asset-selector-active-ctx-subscriptions])))
        (is (identical? effect-adapters/replace-shareable-route-query
                        (get-in deps [:navigation :replace-shareable-route-query])))
        (is (fn? (get-in deps [:navigation :load-surface-module])))
        (is (identical? refresh-handler
                        (get-in deps [:websocket :refresh-websocket-health])))
        (is (identical? disconnect-handler
                        (get-in deps [:wallet :disconnect-wallet])))
        (is (identical? copy-handler
                        (get-in deps [:wallet :copy-wallet-address])))
        (is (identical? copy-link-handler
                        (get-in deps [:wallet :copy-spectate-link])))
        (is (identical? optimizer-run-handler
                        (get-in deps [:portfolio-optimizer :run-portfolio-optimizer])))
        (is (identical? optimizer-pipeline-handler
                        (get-in deps [:portfolio-optimizer :run-portfolio-optimizer-pipeline])))
        (is (identical? effect-adapters/api-fetch-predicted-fundings-effect
                        (get-in deps [:api :api-fetch-predicted-fundings])))
        (is (identical? effect-adapters/sync-active-asset-funding-predictability
                        (get-in deps [:api :sync-active-asset-funding-predictability])))
        (doseq [[handler-key funding-handler] funding-handlers]
          (is (identical? funding-handler
                          (get-in deps [:api handler-key]))))
        (is (identical? submit-handler
                        (get-in deps [:orders :api-submit-order])))
        (is (identical? cancel-handler
                        (get-in deps [:orders :api-cancel-order])))
        (is (identical? margin-handler
                        (get-in deps [:orders :api-submit-position-margin])))
        (is (identical? vault-transfer-handler
                        (get-in deps [:api :api-submit-vault-transfer])))
        (is (some #(= [:funding-modal :api funding-workflow-effect-keys] %)
                  @lazy-effect-calls))))))
