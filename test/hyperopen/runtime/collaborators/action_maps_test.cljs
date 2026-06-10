(ns hyperopen.runtime.collaborators.action-maps-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.history.actions :as account-history-actions]
            [hyperopen.asset-selector.actions :as asset-actions]
            [hyperopen.chart.actions :as chart-actions]
            [hyperopen.chart.settings :as chart-settings]
            [hyperopen.funding.actions :as funding-actions]
            [hyperopen.funding-comparison.actions :as funding-comparison-actions]
            [hyperopen.header.actions :as header-actions]
            [hyperopen.order.actions :as order-actions]
            [hyperopen.portfolio.actions :as portfolio-actions]
            [hyperopen.runtime.action-adapters :as action-adapters]
            [hyperopen.runtime.collaborators :as collaborators]
            [hyperopen.staking.actions :as staking-actions]
            [hyperopen.wallet.actions :as wallet-actions]))

(deftest runtime-action-deps-provides-default-domain-action-handlers-test
  (let [deps (collaborators/runtime-action-deps {})]
    (is (identical? wallet-actions/connect-wallet-action
                    (get-in deps [:wallet :connect-wallet-action])))
    (is (identical? wallet-actions/close-agent-recovery-modal-action
                    (get-in deps [:wallet :close-agent-recovery-modal-action])))
    (is (identical? asset-actions/select-asset
                    (get-in deps [:asset-selector :select-asset])))
    (is (identical? asset-actions/set-funding-hypothetical-size
                    (get-in deps [:asset-selector :set-funding-hypothetical-size])))
    (is (identical? asset-actions/set-funding-hypothetical-value
                    (get-in deps [:asset-selector :set-funding-hypothetical-value])))
    (is (identical? chart-actions/select-chart-type
                    (get-in deps [:chart :select-chart-type])))
    (is (identical? portfolio-actions/toggle-portfolio-summary-scope-dropdown
                    (get-in deps [:chart :toggle-portfolio-summary-scope-dropdown])))
    (is (identical? portfolio-actions/toggle-portfolio-performance-metrics-time-range-dropdown
                    (get-in deps [:chart :toggle-portfolio-performance-metrics-time-range-dropdown])))
    (is (identical? portfolio-actions/open-portfolio-fee-schedule
                    (get-in deps [:chart :open-portfolio-fee-schedule])))
    (is (identical? portfolio-actions/close-portfolio-fee-schedule
                    (get-in deps [:chart :close-portfolio-fee-schedule])))
    (is (identical? portfolio-actions/select-portfolio-fee-schedule-referral-discount
                    (get-in deps [:chart :select-portfolio-fee-schedule-referral-discount])))
    (is (identical? portfolio-actions/select-portfolio-fee-schedule-staking-tier
                    (get-in deps [:chart :select-portfolio-fee-schedule-staking-tier])))
    (is (identical? portfolio-actions/select-portfolio-fee-schedule-maker-rebate-tier
                    (get-in deps [:chart :select-portfolio-fee-schedule-maker-rebate-tier])))
    (is (identical? portfolio-actions/select-portfolio-fee-schedule-market-type
                    (get-in deps [:chart :select-portfolio-fee-schedule-market-type])))
    (is (identical? portfolio-actions/select-portfolio-chart-tab
                    (get-in deps [:chart :select-portfolio-chart-tab])))
    (is (identical? portfolio-actions/set-portfolio-account-info-tab
                    (get-in deps [:chart :set-portfolio-account-info-tab])))
    (is (identical? portfolio-actions/select-portfolio-returns-benchmark
                    (get-in deps [:chart :select-portfolio-returns-benchmark])))
    (is (identical? portfolio-actions/clear-portfolio-returns-benchmark
                    (get-in deps [:chart :clear-portfolio-returns-benchmark])))
    (is (identical? portfolio-actions/open-portfolio-volume-history
                    (get-in deps [:chart :open-portfolio-volume-history])))
    (is (identical? portfolio-actions/close-portfolio-volume-history
                    (get-in deps [:chart :close-portfolio-volume-history])))
    (is (identical? chart-settings/hide-volume-indicator
                    (get-in deps [:chart :hide-volume-indicator])))
    (is (identical? account-history-actions/select-account-info-tab
                    (get-in deps [:account-history :select-account-info-tab])))
    (is (identical? account-history-actions/toggle-positions-direction-filter-open
                    (get-in deps [:account-history :toggle-positions-direction-filter-open])))
    (is (identical? action-adapters/open-spectate-mode-modal
                    (get-in deps [:spectate-mode :open-spectate-mode-modal])))
    (is (identical? header-actions/set-confirm-open-orders-enabled
                    (get-in deps [:spectate-mode :set-confirm-open-orders-enabled]))
        "Header settings actions remain assembled under the spectate-mode key")
    (is (identical? action-adapters/load-leaderboard-route-action
                    (get-in deps [:leaderboard :load-leaderboard-route])))
    (is (identical? action-adapters/set-leaderboard-sort-action
                    (get-in deps [:leaderboard :set-leaderboard-sort])))
    (is (fn? (get-in deps [:leaderboard :set-leaderboard-page-size])))
    (is (fn? (get-in deps [:leaderboard :toggle-leaderboard-page-size-dropdown])))
    (is (fn? (get-in deps [:leaderboard :close-leaderboard-page-size-dropdown])))
    (is (nil? (get-in deps [:vaults :load-vault-route])))
    (is (nil? (get-in deps [:vaults :set-vaults-user-page-size])))
    (is (identical? funding-comparison-actions/load-funding-comparison-route
                    (get-in deps [:funding-comparison :load-funding-comparison-route])))
    (is (identical? staking-actions/load-staking-route
                    (get-in deps [:staking :load-staking-route])))
    (is (identical? staking-actions/submit-staking-delegate
                    (get-in deps [:staking :submit-staking-delegate])))
    (is (identical? funding-comparison-actions/set-funding-comparison-query
                    (get-in deps [:funding-comparison :set-funding-comparison-query])))
    (is (nil? (get-in deps [:vaults :open-vault-transfer-modal])))
    (is (nil? (get-in deps [:vaults :submit-vault-transfer])))
    (is (identical? order-actions/submit-order
                    (get-in deps [:orders :submit-order])))
    (is (identical? funding-actions/set-hyperunit-lifecycle
                    (get-in deps [:orders :set-hyperunit-lifecycle])))))
