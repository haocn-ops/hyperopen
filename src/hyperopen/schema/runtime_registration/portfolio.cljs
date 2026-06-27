(ns hyperopen.schema.runtime-registration.portfolio)

(def effect-binding-rows
  [[:effects/run-portfolio-optimizer :run-portfolio-optimizer]
   [:effects/api-fetch-trader-portfolio-benchmark
    :api-fetch-trader-portfolio-benchmark]
   [:effects/run-portfolio-optimizer-pipeline :run-portfolio-optimizer-pipeline]
   [:effects/load-portfolio-optimizer-history :load-portfolio-optimizer-history]
   [:effects/load-portfolio-optimizer-history-discovery
    :load-portfolio-optimizer-history-discovery]
   [:effects/load-portfolio-optimizer-scenario-index
    :load-portfolio-optimizer-scenario-index]
   [:effects/load-portfolio-optimizer-scenario
    :load-portfolio-optimizer-scenario]
   [:effects/archive-portfolio-optimizer-scenario
    :archive-portfolio-optimizer-scenario]
	   [:effects/duplicate-portfolio-optimizer-scenario
	    :duplicate-portfolio-optimizer-scenario]
	   [:effects/save-portfolio-optimizer-scenario :save-portfolio-optimizer-scenario]
	   [:effects/execute-portfolio-optimizer-plan :execute-portfolio-optimizer-plan]
	   [:effects/refresh-portfolio-optimizer-tracking :refresh-portfolio-optimizer-tracking]
	   [:effects/refresh-portfolio-optimizer-rebalance-slippage-snapshots
	    :refresh-portfolio-optimizer-rebalance-slippage-snapshots]
	   [:effects/enable-portfolio-optimizer-manual-tracking
	    :enable-portfolio-optimizer-manual-tracking]])

(def effect-order-policy-required-action-ids
  #{:actions/select-portfolio-summary-time-range
    :actions/select-portfolio-chart-tab
    :actions/set-portfolio-returns-benchmark-suggestions-open
    :actions/select-portfolio-returns-benchmark
    :actions/add-portfolio-optimizer-universe-instrument
    :actions/add-portfolio-optimizer-universe-instrument-and-run
    :actions/set-portfolio-optimizer-universe-from-current
    :actions/confirm-portfolio-optimizer-scenario-save
    :actions/auto-recompute-stale-portfolio-optimizer-scenario})

(def action-binding-rows
  [[:actions/toggle-portfolio-summary-scope-dropdown :toggle-portfolio-summary-scope-dropdown]
   [:actions/select-portfolio-summary-scope :select-portfolio-summary-scope]
   [:actions/toggle-portfolio-summary-time-range-dropdown :toggle-portfolio-summary-time-range-dropdown]
   [:actions/toggle-portfolio-performance-metrics-time-range-dropdown :toggle-portfolio-performance-metrics-time-range-dropdown]
   [:actions/open-portfolio-fee-schedule :open-portfolio-fee-schedule]
   [:actions/close-portfolio-fee-schedule :close-portfolio-fee-schedule]
   [:actions/toggle-portfolio-fee-schedule-referral-dropdown :toggle-portfolio-fee-schedule-referral-dropdown]
   [:actions/toggle-portfolio-fee-schedule-staking-dropdown :toggle-portfolio-fee-schedule-staking-dropdown]
   [:actions/toggle-portfolio-fee-schedule-maker-rebate-dropdown :toggle-portfolio-fee-schedule-maker-rebate-dropdown]
   [:actions/toggle-portfolio-fee-schedule-market-dropdown :toggle-portfolio-fee-schedule-market-dropdown]
   [:actions/select-portfolio-fee-schedule-referral-discount :select-portfolio-fee-schedule-referral-discount]
   [:actions/select-portfolio-fee-schedule-staking-tier :select-portfolio-fee-schedule-staking-tier]
   [:actions/select-portfolio-fee-schedule-maker-rebate-tier :select-portfolio-fee-schedule-maker-rebate-tier]
   [:actions/select-portfolio-fee-schedule-market-type :select-portfolio-fee-schedule-market-type]
   [:actions/handle-portfolio-fee-schedule-keydown :handle-portfolio-fee-schedule-keydown]
   [:actions/select-portfolio-summary-time-range :select-portfolio-summary-time-range]
   [:actions/select-portfolio-chart-tab :select-portfolio-chart-tab]
   [:actions/set-portfolio-account-info-tab :set-portfolio-account-info-tab]
   [:actions/set-portfolio-monte-carlo-control :set-portfolio-monte-carlo-control]
   [:actions/rerun-portfolio-monte-carlo :rerun-portfolio-monte-carlo]
   [:actions/set-portfolio-returns-benchmark-search :set-portfolio-returns-benchmark-search]
   [:actions/set-portfolio-returns-benchmark-suggestions-open :set-portfolio-returns-benchmark-suggestions-open]
   [:actions/select-portfolio-returns-benchmark :select-portfolio-returns-benchmark]
   [:actions/remove-portfolio-returns-benchmark :remove-portfolio-returns-benchmark]
   [:actions/handle-portfolio-returns-benchmark-search-keydown :handle-portfolio-returns-benchmark-search-keydown]
   [:actions/clear-portfolio-returns-benchmark :clear-portfolio-returns-benchmark]
   [:actions/open-portfolio-volume-history :open-portfolio-volume-history]
   [:actions/close-portfolio-volume-history :close-portfolio-volume-history]
   [:actions/handle-portfolio-volume-history-keydown :handle-portfolio-volume-history-keydown]
   [:actions/set-portfolio-optimizer-objective-kind :set-portfolio-optimizer-objective-kind]
   [:actions/open-portfolio-optimizer-objective-menu
    :open-portfolio-optimizer-objective-menu]
   [:actions/close-portfolio-optimizer-objective-menu
    :close-portfolio-optimizer-objective-menu]
   [:actions/handle-portfolio-optimizer-objective-menu-keydown
    :handle-portfolio-optimizer-objective-menu-keydown]
   [:actions/select-portfolio-optimizer-objective-menu-option
    :select-portfolio-optimizer-objective-menu-option]
   [:actions/set-portfolio-optimizer-objective-menu-view-return
    :set-portfolio-optimizer-objective-menu-view-return]
   [:actions/step-portfolio-optimizer-objective-menu-view-return
    :step-portfolio-optimizer-objective-menu-view-return]
   [:actions/set-portfolio-optimizer-objective-menu-view-confidence
    :set-portfolio-optimizer-objective-menu-view-confidence]
   [:actions/remove-portfolio-optimizer-objective-menu-view
    :remove-portfolio-optimizer-objective-menu-view]
   [:actions/add-portfolio-optimizer-objective-menu-view
    :add-portfolio-optimizer-objective-menu-view]
   [:actions/apply-portfolio-optimizer-objective-menu-selection-and-run
    :apply-portfolio-optimizer-objective-menu-selection-and-run]
   [:actions/set-portfolio-optimizer-return-model-kind :set-portfolio-optimizer-return-model-kind]
   [:actions/set-portfolio-optimizer-risk-model-kind :set-portfolio-optimizer-risk-model-kind]
   [:actions/apply-portfolio-optimizer-setup-preset :apply-portfolio-optimizer-setup-preset]
   [:actions/set-portfolio-optimizer-constraint :set-portfolio-optimizer-constraint]
   [:actions/set-portfolio-optimizer-objective-parameter :set-portfolio-optimizer-objective-parameter]
   [:actions/set-portfolio-optimizer-objective-parameter-percent
    :set-portfolio-optimizer-objective-parameter-percent]
   [:actions/set-portfolio-optimizer-target-sigma-draft
    :set-portfolio-optimizer-target-sigma-draft]
   [:actions/set-portfolio-optimizer-objective-menu-target-sigma
    :set-portfolio-optimizer-objective-menu-target-sigma]
   [:actions/reset-portfolio-optimizer-target-sigma-draft
    :reset-portfolio-optimizer-target-sigma-draft]
   [:actions/rerun-portfolio-optimizer-at-target-sigma
    :rerun-portfolio-optimizer-at-target-sigma]
   [:actions/set-portfolio-optimizer-execution-assumption :set-portfolio-optimizer-execution-assumption]
   [:actions/add-portfolio-optimizer-black-litterman-view
    :add-portfolio-optimizer-black-litterman-view]
   [:actions/set-portfolio-optimizer-black-litterman-view-parameter
    :set-portfolio-optimizer-black-litterman-view-parameter]
   [:actions/remove-portfolio-optimizer-black-litterman-view
    :remove-portfolio-optimizer-black-litterman-view]
   [:actions/set-portfolio-optimizer-black-litterman-editor-type
    :set-portfolio-optimizer-black-litterman-editor-type]
   [:actions/set-portfolio-optimizer-black-litterman-editor-field
    :set-portfolio-optimizer-black-litterman-editor-field]
   [:actions/save-portfolio-optimizer-black-litterman-editor-view
    :save-portfolio-optimizer-black-litterman-editor-view]
   [:actions/edit-portfolio-optimizer-black-litterman-view
    :edit-portfolio-optimizer-black-litterman-view]
   [:actions/cancel-portfolio-optimizer-black-litterman-edit
    :cancel-portfolio-optimizer-black-litterman-edit]
   [:actions/request-clear-portfolio-optimizer-black-litterman-views
    :request-clear-portfolio-optimizer-black-litterman-views]
   [:actions/cancel-clear-portfolio-optimizer-black-litterman-views
    :cancel-clear-portfolio-optimizer-black-litterman-views]
   [:actions/confirm-clear-portfolio-optimizer-black-litterman-views
    :confirm-clear-portfolio-optimizer-black-litterman-views]
   [:actions/set-portfolio-optimizer-instrument-filter :set-portfolio-optimizer-instrument-filter]
   [:actions/set-portfolio-optimizer-asset-override :set-portfolio-optimizer-asset-override]
   [:actions/set-portfolio-optimizer-history-assumption-mode
    :set-portfolio-optimizer-history-assumption-mode]
   [:actions/set-portfolio-optimizer-history-assumption-expected-return
    :set-portfolio-optimizer-history-assumption-expected-return]
   [:actions/set-portfolio-optimizer-history-assumption-expected-volatility
    :set-portfolio-optimizer-history-assumption-expected-volatility]
   [:actions/set-portfolio-optimizer-history-assumption-max-weight-cap
    :set-portfolio-optimizer-history-assumption-max-weight-cap]
   [:actions/set-portfolio-optimizer-history-assumption-proxy-instrument
    :set-portfolio-optimizer-history-assumption-proxy-instrument]
   [:actions/set-portfolio-optimizer-history-assumption-proxy-relationship
    :set-portfolio-optimizer-history-assumption-proxy-relationship]
   [:actions/clear-portfolio-optimizer-history-assumption
    :clear-portfolio-optimizer-history-assumption]
   [:actions/set-portfolio-optimizer-universe-search-query
    :set-portfolio-optimizer-universe-search-query]
   [:actions/set-portfolio-optimizer-draft-add-asset-open
    :set-portfolio-optimizer-draft-add-asset-open]
   [:actions/handle-portfolio-optimizer-universe-search-keydown
    :handle-portfolio-optimizer-universe-search-keydown]
   [:actions/handle-portfolio-optimizer-draft-add-asset-keydown
    :handle-portfolio-optimizer-draft-add-asset-keydown]
   [:actions/set-portfolio-optimizer-frontier-overlay-mode
    :set-portfolio-optimizer-frontier-overlay-mode]
   [:actions/set-portfolio-optimizer-constrain-frontier
    :set-portfolio-optimizer-constrain-frontier]
   [:actions/set-portfolio-optimizer-results-tab
    :set-portfolio-optimizer-results-tab]
   [:actions/add-portfolio-optimizer-universe-instrument
    :add-portfolio-optimizer-universe-instrument]
	   [:actions/add-portfolio-optimizer-universe-instrument-and-run
	    :add-portfolio-optimizer-universe-instrument-and-run]
	   [:actions/toggle-portfolio-optimizer-universe-instrument-exclusion-and-run
	    :toggle-portfolio-optimizer-universe-instrument-exclusion-and-run]
	   [:actions/set-portfolio-optimizer-universe-instrument-side
	    :set-portfolio-optimizer-universe-instrument-side]
	   [:actions/set-portfolio-optimizer-universe-instrument-side-and-run
	    :set-portfolio-optimizer-universe-instrument-side-and-run]
	   [:actions/remove-portfolio-optimizer-universe-instrument
	    :remove-portfolio-optimizer-universe-instrument]
   [:actions/set-portfolio-optimizer-universe-from-current :set-portfolio-optimizer-universe-from-current]
   [:actions/load-portfolio-optimizer-history-from-draft :load-portfolio-optimizer-history-from-draft]
   [:actions/save-portfolio-optimizer-scenario-from-current :save-portfolio-optimizer-scenario-from-current]
   [:actions/open-portfolio-optimizer-scenario-save-modal
    :open-portfolio-optimizer-scenario-save-modal]
   [:actions/close-portfolio-optimizer-scenario-save-modal
    :close-portfolio-optimizer-scenario-save-modal]
   [:actions/set-portfolio-optimizer-scenario-save-name
    :set-portfolio-optimizer-scenario-save-name]
   [:actions/confirm-portfolio-optimizer-scenario-save
    :confirm-portfolio-optimizer-scenario-save]
   [:actions/load-portfolio-optimizer-route :load-portfolio-optimizer-route]
   [:actions/archive-portfolio-optimizer-scenario :archive-portfolio-optimizer-scenario]
   [:actions/duplicate-portfolio-optimizer-scenario :duplicate-portfolio-optimizer-scenario]
   [:actions/open-portfolio-optimizer-execution :open-portfolio-optimizer-execution]
	   [:actions/set-portfolio-optimizer-execution-phase :set-portfolio-optimizer-execution-phase]
	   [:actions/set-portfolio-optimizer-execution-default-order-type :set-portfolio-optimizer-execution-default-order-type]
	   [:actions/set-portfolio-optimizer-execution-row-order-type :set-portfolio-optimizer-execution-row-order-type]
	   [:actions/toggle-portfolio-optimizer-execution-row :toggle-portfolio-optimizer-execution-row]
	   [:actions/set-portfolio-optimizer-execution-row-param :set-portfolio-optimizer-execution-row-param]
	   [:actions/confirm-portfolio-optimizer-execution :confirm-portfolio-optimizer-execution]
	   [:actions/refresh-portfolio-optimizer-tracking :refresh-portfolio-optimizer-tracking]
	   [:actions/enable-portfolio-optimizer-manual-tracking
	    :enable-portfolio-optimizer-manual-tracking]
	   [:actions/auto-recompute-stale-portfolio-optimizer-scenario
	    :auto-recompute-stale-portfolio-optimizer-scenario]
	   [:actions/run-portfolio-optimizer-from-draft :run-portfolio-optimizer-from-draft]
   [:actions/run-portfolio-optimizer :run-portfolio-optimizer]
   [:actions/open-portfolio-optimizer-refinement
    :open-portfolio-optimizer-refinement]
   [:actions/close-portfolio-optimizer-refinement
    :close-portfolio-optimizer-refinement]
   [:actions/set-portfolio-optimizer-refinement-depth
    :set-portfolio-optimizer-refinement-depth]
   [:actions/refine-portfolio-optimizer :refine-portfolio-optimizer]
   [:actions/stop-portfolio-optimizer-refinement
    :stop-portfolio-optimizer-refinement]])
