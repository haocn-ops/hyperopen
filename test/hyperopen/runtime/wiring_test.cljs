(ns hyperopen.runtime.wiring-test
  (:require [clojure.set :as set]
            [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.actions :as portfolio-optimizer-actions]
            [hyperopen.route-modules :as route-modules]
            [hyperopen.runtime.action-adapters :as action-adapters]
            [hyperopen.runtime.effect-adapters :as effect-adapters]
            [hyperopen.schema.runtime-registration-catalog :as runtime-registration-catalog]
            [hyperopen.runtime.wiring :as wiring]))

(defn- flatten-leaf-keys
  [node]
  (reduce-kv (fn [acc k v]
               (if (map? v)
                 (into acc (flatten-leaf-keys v))
                 (conj acc k)))
             #{}
             (or node {})))

(deftest runtime-deps-load-route-owned-optimizer-and-vault-handlers-through-lazy-route-runtime-deps-test
  (let [runtime {:runtime-id :lazy-route-runtime}
        portfolio-action-handler (fn [& _] :portfolio-action)
        vault-action-handler (fn [& _] :vault-action)
        portfolio-effect-handler (fn [& _] :portfolio-effect)
        vault-effect-handler (fn [& _] :vault-effect)
        action-calls (atom [])
        effect-calls (atom [])]
    (with-redefs [route-modules/lazy-route-action-leaf-deps
                  (fn [module-id group-key handler-keys]
                    (swap! action-calls conj [module-id group-key (set handler-keys)])
                    (case [module-id group-key]
                      [:portfolio :portfolio-optimizer]
                      {:portfolio-optimizer
                       {:run-portfolio-optimizer portfolio-action-handler}}

                      [:vaults :vaults]
                      {:vaults
                       {:set-vaults-sort vault-action-handler}}

                      {}))
                  route-modules/lazy-route-effect-leaf-deps
                  (fn [runtime* module-id group-key handler-keys]
                    (is (identical? runtime runtime*))
                    (swap! effect-calls conj [module-id group-key (set handler-keys)])
                    (case [module-id group-key]
                      [:portfolio :portfolio-optimizer]
                      {:portfolio-optimizer
                       {:load-portfolio-optimizer-history portfolio-effect-handler}}

                      [:vaults :api]
                      {:api
                       {:api-fetch-vault-index vault-effect-handler}}

                      {}))]
      (let [action-deps (wiring/runtime-action-deps)
            effect-deps (wiring/runtime-effect-deps runtime)]
        (is (identical? portfolio-action-handler
                        (get-in action-deps
                                [:portfolio-optimizer :run-portfolio-optimizer])))
        (is (identical? vault-action-handler
                        (get-in action-deps [:vaults :set-vaults-sort])))
        (is (identical? portfolio-effect-handler
                        (get-in effect-deps
                                [:portfolio-optimizer :load-portfolio-optimizer-history])))
        (is (identical? vault-effect-handler
                        (get-in effect-deps [:api :api-fetch-vault-index])))
        (is (fn? (get-in action-deps [:portfolio-optimizer :load-portfolio-optimizer-route])))
        (is (identical?
             portfolio-optimizer-actions/restore-or-preseed-portfolio-optimizer-draft
             (get-in action-deps
                     [:portfolio-optimizer
                      :restore-or-preseed-portfolio-optimizer-draft])))
        (is (fn? (get-in action-deps [:vaults :load-vault-route])))
        (is (not (some #(contains? (nth % 2)
                                   :restore-or-preseed-portfolio-optimizer-draft)
                       @action-calls)))
        (is (seq @action-calls)))
      (is (seq @effect-calls)))))

(deftest runtime-effect-deps-uses-extracted-effect-adapter-overrides-test
  (let [deps (wiring/runtime-effect-deps)]
    (is (identical? effect-adapters/save
                    (get-in deps [:storage :save])))
    (is (identical? effect-adapters/persist-leaderboard-preferences-effect
                    (get-in deps [:storage :persist-leaderboard-preferences])))
    (is (identical? effect-adapters/sync-asset-selector-active-ctx-subscriptions
                    (get-in deps [:asset-selector :sync-asset-selector-active-ctx-subscriptions])))
    (is (identical? effect-adapters/load-trading-indicators-module-effect
                    (get-in deps [:navigation :load-trading-indicators-module])))
    (is (fn? (get-in deps [:navigation :load-surface-module])))
    (is (fn? (get-in deps [:navigation :load-account-tab-module])))
    (is (identical? effect-adapters/replace-shareable-route-query
                    (get-in deps [:navigation :replace-shareable-route-query])))
    (is (identical? effect-adapters/fetch-candle-snapshot
                    (get-in deps [:websocket :fetch-candle-snapshot])))
    (is (identical? effect-adapters/ws-reset-subscriptions
                    (get-in deps [:diagnostics :ws-reset-subscriptions])))
    (is (identical? effect-adapters/api-fetch-predicted-fundings-effect
                    (get-in deps [:api :api-fetch-predicted-fundings])))
    (is (identical? effect-adapters/api-fetch-leaderboard-effect
                    (get-in deps [:api :api-fetch-leaderboard])))
    (is (fn? (get-in deps [:api :api-fetch-vault-index])))
    (is (fn? (get-in deps [:api :api-fetch-vault-index-with-cache])))
    (is (fn? (get-in deps [:api :api-fetch-vault-ledger-updates])))
    (is (identical? effect-adapters/api-fetch-staking-validator-summaries-effect
                    (get-in deps [:api :api-fetch-staking-validator-summaries])))
    (is (identical? effect-adapters/api-fetch-referral-effect
                    (get-in deps [:api :api-fetch-referral])))
    (is (fn? (get-in deps [:portfolio-optimizer :run-portfolio-optimizer])))
    (is (fn? (get-in deps [:portfolio-optimizer :run-portfolio-optimizer-pipeline])))
    (is (fn? (get-in deps [:portfolio-optimizer :load-portfolio-optimizer-history])))
    (is (fn? (get-in deps [:portfolio-optimizer :load-portfolio-optimizer-scenario-index])))
    (is (fn? (get-in deps [:portfolio-optimizer :load-portfolio-optimizer-scenario])))
    (is (fn? (get-in deps [:portfolio-optimizer :archive-portfolio-optimizer-scenario])))
    (is (fn? (get-in deps [:portfolio-optimizer :duplicate-portfolio-optimizer-scenario])))
    (is (fn? (get-in deps [:portfolio-optimizer :save-portfolio-optimizer-scenario])))
    (is (fn? (get-in deps [:portfolio-optimizer :execute-portfolio-optimizer-plan])))
    (is (fn? (get-in deps [:portfolio-optimizer :refresh-portfolio-optimizer-tracking])))
    (is (fn? (get-in deps [:portfolio-optimizer :enable-portfolio-optimizer-manual-tracking])))
    (is (identical? action-adapters/enable-agent-trading
                    (get-in deps [:wallet :enable-agent-trading])))))

(deftest runtime-action-deps-uses-extracted-action-adapter-overrides-test
  (let [deps (wiring/runtime-action-deps)]
    (is (identical? action-adapters/init-websockets
                    (get-in deps [:core :init-websockets])))
    (is (identical? action-adapters/reconnect-websocket-action
                    (get-in deps [:core :reconnect-websocket-action])))
    (is (identical? action-adapters/refresh-asset-markets
                    (get-in deps [:asset-selector :refresh-asset-markets])))
    (is (fn? (get-in deps [:vaults :load-vault-route])))
    (is (fn? (get-in deps [:vaults :load-vaults])))
    (is (fn? (get-in deps [:vaults :load-vault-detail])))
    (is (identical? action-adapters/load-funding-comparison-route-action
                    (get-in deps [:funding-comparison :load-funding-comparison-route])))
    (is (identical? action-adapters/load-leaderboard-route-action
                    (get-in deps [:leaderboard :load-leaderboard-route])))
    (is (identical? action-adapters/load-staking-route-action
                    (get-in deps [:staking :load-staking-route])))
    (is (identical? action-adapters/load-referrals-route-action
                    (get-in deps [:referrals :load-referrals-route])))
    (is (identical? action-adapters/navigate
                    (get-in deps [:core :navigate])))
    (doseq [handler-key [:run-portfolio-optimizer
                         :set-portfolio-optimizer-objective-kind
                         :load-portfolio-optimizer-route
                         :set-portfolio-optimizer-universe-search-query
                         :refresh-portfolio-optimizer-tracking
                         :enable-portfolio-optimizer-manual-tracking
                         :run-portfolio-optimizer-from-draft]]
      (is (fn? (get-in deps [:portfolio-optimizer handler-key]))
          (str "expected lazy route handler for " handler-key)))
    (is (fn? (get-in deps [:vaults :set-vaults-sort])))
    (is (fn? (get-in deps [:vaults :open-vault-transfer-modal])))))

(deftest runtime-registration-deps-builds-effect-and-action-handlers-test
  (let [deps (wiring/runtime-registration-deps)]
    (is (fn? (:register-effects! deps)))
    (is (fn? (:register-actions! deps)))
    (is (fn? (:register-system-state! deps)))
    (is (fn? (:register-placeholders! deps)))
    (is (fn? (:register-interceptors! deps)))
    (is (identical? action-adapters/navigate
                    (get-in deps [:action-handlers :navigate])))
    (is (identical? effect-adapters/save
                    (get-in deps [:effect-handlers :save])))
    (is (fn? (get-in deps [:effect-handlers :load-surface-module])))
    (is (fn? (get-in deps [:effect-handlers :load-account-tab-module])))
    (is (fn? (get-in deps [:action-handlers :run-portfolio-optimizer])))
    (is (fn? (get-in deps [:action-handlers :load-portfolio-optimizer-route])))
    (is (fn? (get-in deps [:action-handlers :load-vault-route])))
    (is (fn? (get-in deps [:effect-handlers :run-portfolio-optimizer])))
    (is (fn? (get-in deps [:effect-handlers :run-portfolio-optimizer-pipeline])))
    (is (fn? (get-in deps [:effect-handlers :load-portfolio-optimizer-history])))
    (is (fn? (get-in deps [:effect-handlers :load-portfolio-optimizer-scenario-index])))
    (is (fn? (get-in deps [:effect-handlers :load-portfolio-optimizer-scenario])))
    (is (fn? (get-in deps [:effect-handlers :archive-portfolio-optimizer-scenario])))
    (is (fn? (get-in deps [:effect-handlers :duplicate-portfolio-optimizer-scenario])))
    (is (fn? (get-in deps [:effect-handlers :save-portfolio-optimizer-scenario])))
    (is (fn? (get-in deps [:effect-handlers :execute-portfolio-optimizer-plan])))
    (is (fn? (get-in deps [:effect-handlers :refresh-portfolio-optimizer-tracking])))
    (is (fn? (get-in deps [:effect-handlers :enable-portfolio-optimizer-manual-tracking])))
    (is (fn? (get-in deps [:effect-handlers :api-fetch-vault-index])))))

(deftest runtime-action-deps-cover-catalog-handler-keys-test
  (let [action-deps (wiring/runtime-action-deps)
        available-handler-keys (flatten-leaf-keys action-deps)
        required-handler-keys (runtime-registration-catalog/action-handler-keys)
        missing (set/difference required-handler-keys available-handler-keys)]
    (is (empty? missing)
        (str "Runtime action deps missing catalog handler keys: "
             (pr-str missing)))))

(deftest runtime-effect-deps-cover-catalog-handler-keys-test
  (let [effect-deps (wiring/runtime-effect-deps)
        available-handler-keys (flatten-leaf-keys effect-deps)
        required-handler-keys (runtime-registration-catalog/effect-handler-keys)
        missing (set/difference required-handler-keys available-handler-keys)]
    (is (empty? missing)
        (str "Runtime effect deps missing catalog handler keys: "
             (pr-str missing)))))
