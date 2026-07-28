# Remove Confirmed Dead Code Without Changing Runtime Behavior

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. It is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

This cleanup removes code that cannot be reached by the shipped application or its tooling, while leaving every supported route, action, API, and testable behavior unchanged. A contributor can see the result by compiling the application and test builds, running the focused suites for the touched areas, and running the complete gate matrix successfully.

This is behavior-preserving maintenance, not performance work. The reported line counts are scope measurements, not a claim about runtime speed; no performance baseline or profiling is required.

## Context References

Public refs:

- Direct user request on 2026-07-28: remove only the confirmed dead-code audit findings, run the full gates, and skip browser QA.

Repo artifacts:

- `/hyperopen/AGENTS.md` defines write authority, required gates, and browser-test routing.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define this plan's lifecycle and required sections.
- `/hyperopen/docs/references/toolchain.md` documents `rg`, `clojure-lsp`, and build/test commands used to recheck static findings.

Local scratch refs (non-authoritative):

- The temporary clj-kondo and Knip audit supplied for this work established the frozen worklists below. This plan reproduces every in-scope target, so implementation must not depend on that temporary directory.

## Scope and Non-goals

The removal scope is exactly the five orphan production namespaces, 40 completely unreferenced private ClojureScript definitions, seven zero-call JavaScript tooling helpers, 11 unused ClojureScript require edges, and ten no-op string expressions listed below. The two additional private definitions and two require edges are direct transitive dead code created by the approved initial deletions and were verified by post-edit clj-kondo. Do not edit `/hyperopen/src/**` except through the assigned `worker` role. No new tests are required or materialized for this cleanup; the existing regression and tooling tests are the approved coverage contract.

The 32 private definitions that are referenced only by tests are explicitly excluded from the initial removal. They may be included only when implementation finds a trivial migration of every existing assertion to an owning module's supported behavior, with no reduced coverage and no new compatibility facade. Otherwise leave the definition and its test intact and record the decision here.

Do not remove the 127 unreferenced public-API candidates, the 36 likely stale CSS selectors, arbitrary unused bindings, lint findings outside this worklist, or the three apparently unused `hyperopen.core.compat` requires. The latter are load-time registration dependencies and must remain. Before deleting each of the seven JavaScript helpers, the `worker` must prove it is neither a package export nor a documented public tool interface; an export, documentation, or consumer that establishes such a contract blocks that helper's deletion and must be recorded here. Browser QA is out of scope: this ticket changes no reachable UI behavior or interaction flow.

## Progress

- [x] (2026-07-28 19:00Z) Captured the direct user request, audit evidence, frozen deletion worklists, exclusions, and validation contract in this active ExecPlan.
- [x] (2026-07-28 16:58Z) Re-verified inbound references and deleted the five named orphan production namespaces. `universe_panel_test.cljs` and both generated-runner entries remain unchanged and `npm test` executes the live optimizer-workspace regression.
- [x] (2026-07-28 16:58Z) Removed the original 38 listed non-test private definitions, seven tooling helpers, nine require edges, and ten no-op strings. Regenerated the test runner (843 namespaces) without a runner diff.
- [x] (2026-07-28 16:58Z) Retained all 32 test-only seams; no trivial assertion-preserving migration was attempted or needed, so no test seam or test file was removed.
- [x] (2026-07-28 16:58Z) Passed focused compilation, `npm test`, browser-inspection Node coverage including `scenario_runner`, and `npm run test:multi-agent`; completed pre/post frozen-target reference checks.
- [x] (2026-07-28 17:00Z) Post-edit clj-kondo confirmed and the worker removed four direct transitive items: two private definitions and two require edges caused solely by the initial approved removals.
- [x] (2026-07-28) Review approved. Parent completed `npm run gates` (PASS 34/34; 6,494 tests, 35,266 assertions, 0 failures; 1m46s), `npx shadow-cljs release app` (1,263 files, 1,110 compiled, 0 warnings; 35.85s), and a post-edit temp-binary clj-kondo audit. The audit reports only the 32 protected test seams and three retained `hyperopen.core.compat` load-time requires; no further in-scope or transitive finding remains. Browser QA is explicitly skipped.

## Surprises & Discoveries

- Observation: `hyperopen.views.portfolio.optimize.universe-panel` has a similarly named test file, but that test does not require the orphan namespace.
  Evidence: `test/hyperopen/views/portfolio/optimize/universe_panel_test.cljs` requires `hyperopen.views.portfolio-view` and exercises live optimizer-workspace regression behavior. The test and its generated-runner entry must remain after deleting the production orphan.
- Observation: a static unused-require result is not by itself authority to remove a load-time require.
  Evidence: `src/hyperopen/core/compat.cljs` requires registration namespaces for initialization; its three warnings are excluded from the nine removable require edges.
- Observation: the ten strings are executable expressions placed after function argument vectors, rather than metadata docstrings.
  Evidence: static analysis reports each as `Unused value`; removing it does not alter any function's input, output, or metadata contract.
- Observation: the post-edit repository-wide target search found no unexpected inbound reference. The only text match for the deleted optimizer namespace is its intentionally retained `-test` namespace in the source test and generated runner.
  Evidence: scoped `rg` over `src`, `test`, `dev`, `portfolio`, and `tools`; source-file absence checks; and the unchanged test-runner generation result.
- Observation: post-edit clj-kondo identified four direct transitive dead items introduced by the approved removals: `performance-periods-per-year`, `sort-direction-icon`, the `hyperopen.domain.trading` require in funding assets, and the `hyperopen.portfolio.optimizer.coercion` require in equal-risk.
  Evidence: each target's only former reference was in a just-removed in-scope definition; targeted source searches now show no reference.

## Decision Log

- Decision: Freeze cleanup to confirmed private, namespace, JavaScript, require-edge, and no-op-expression findings.
  Rationale: Public exports and CSS class names can be consumed dynamically, so static non-reference evidence is insufficient for safe deletion.
  Date/Author: 2026-07-28 / platform
- Decision: Retain all 32 test-only seams unless an implementation-time migration is demonstrably trivial and preserves every assertion at an owning behavioral boundary.
  Rationale: A test-only reference protects test coverage even when no production call exists. Removing it merely to lower the audit count would weaken verification.
  Date/Author: 2026-07-28 / platform
- Decision: Explicitly skip Browser MCP, visual QA, and Playwright browser runs.
  Rationale: this is non-UI behavior-preserving cleanup of unreachable code and static expressions; focused unit/compile checks plus the required complete gates provide the relevant evidence.
  Date/Author: 2026-07-28 / platform
- Decision: Do not materialize new tests for this cleanup.
  Rationale: the approved contract is to preserve and execute existing ClojureScript, tooling, scenario, and full-suite coverage; new tests would expand a subtractive maintenance ticket without a new behavior to specify.
  Date/Author: 2026-07-28 / platform
- Decision: Delete the seven JavaScript helpers.
  Rationale: repository-wide caller searches found only their declarations (and the plan), `package.json` has no package export surface, and no tool documentation names these helpers as public interfaces. They are zero-call internal exports, not supported CLI or package APIs.
  Date/Author: 2026-07-28 / worker
- Decision: Remove the four clj-kondo-confirmed transitive items.
  Rationale: each is direct cleanup caused by an approved deletion, has no remaining caller, and preserves the subtractive contract without expanding into unrelated audit findings.
  Date/Author: 2026-07-28 / worker
- Decision: Approve the completed cleanup after review, full gates, advanced release compilation, and a post-edit clj-kondo audit.
  Rationale: the final evidence confirms the removal set preserves supported behavior and exhausts the permitted in-scope/transitive findings. Browser QA remains skipped because the work changes no reachable UI flow.
  Date/Author: 2026-07-28 / platform

## Outcomes & Retrospective

Implementation removed five orphan production namespaces, 40 listed private definitions, seven internal JavaScript helpers, 11 unused ClojureScript require edges, and ten no-op expressions. Removed test seams: zero; all 32 test-only seams and the live `universe-panel-test` remain.

Focused validation passed: `npm run setup:worktree`; pre/post scoped reference checks; `npm run test:runner:generate` (843 namespaces, no runner diff); `npx shadow-cljs --force-spawn compile test` (initially 2,108 files/944 compiled and again after the transitive cleanup with 427 compiled, both 0 warnings); `npm test` (5,774 tests, 31,880 assertions, 0 failures/errors); the six focused browser-inspection Node files including `scenario_runner` (35/35); and `npm run test:multi-agent` (14/14). The local worktree does not install `clj-kondo`, but the parent ran the post-edit audit using its temporary binary; it found only the 32 protected test seams and three retained `hyperopen.core.compat` load-time requires. The earlier `clojure-lsp diagnostics --project-root .` exit 3 is attributable to broad existing repository diagnostics outside this scope.

Complete validation passed: `npm run gates` printed PASS 34/34 in 1m46s, covering 6,494 tests and 35,266 assertions with zero failures. `npx shadow-cljs release app` completed in 35.85s (1,263 files, 1,110 compiled, 0 warnings). Review is approved. Browser QA is **skipped**: no Browser MCP session, visual comparison, or Playwright run was needed because no reachable UI behavior or interaction flow changed. The cleanup reduced maintenance surface only; it makes no performance claim.

## Context and Orientation

An orphan namespace is a production namespace with no inbound production require, route registration, or dynamic entrypoint. A completely unreferenced private definition is a `defn-`, private `def`, or private constant with no source or test reference. A test seam is different: production does not call it, but a test does. A require edge is one namespace item in an `:require` vector. A no-op expression is a top-level-form value that ClojureScript evaluates and discards.

The implementation touches independent source and tooling modules. Keep the change subtractive: delete definitions or require entries, do not replace them with aliases, wrappers, exports, or public compatibility APIs. If deleting a source file leaves a generated test-runner reference, regenerate the runner from remaining test files.

## Frozen Worklist

Delete these orphan production namespaces, totalling 420 current lines:

- `src/hyperopen/views/footer/market_projection_diagnostics.cljs` (222 lines)
- `src/hyperopen/views/portfolio/optimize/universe_panel.cljs` (125 lines)
- `src/hyperopen/views/trade/order_form_presenter.cljs` (30 lines)
- `src/hyperopen/api/legacy.cljs` (25 lines)
- `src/hyperopen/trading/order_form_contracts.cljs` (18 lines)

Remove these 40 completely unreferenced private definitions. The original 38-form audit is extended only by the two directly caused transitive definitions below:

- `hyperopen.api.default/default-info-client-config`, `hyperopen.api.default/make-default-api-service`, `hyperopen.domain.trading.indicators.trend.moving-averages/parse-number`, and `hyperopen.funding.domain.assets/parse-num`.
- `hyperopen.portfolio.fee-schedule/format-discount-pct`, `hyperopen.portfolio.optimizer.actions.run/current-solved-run?`, `hyperopen.portfolio.optimizer.application.view-model.universe/native-observation-count`, `hyperopen.portfolio.optimizer.black-litterman-actions.editor-model/editing-view-id`, `hyperopen.portfolio.optimizer.black-litterman-actions.editor-model/pending-draft?`, and `hyperopen.portfolio.optimizer.black-litterman-actions.views/view-direction`.
- `hyperopen.portfolio.optimizer.domain.diagnostics/gross-exposure`, `hyperopen.portfolio.optimizer.domain.equal-risk/finite-number?`, `hyperopen.runtime.effect-adapters.order/set-order-feedback-toast!`, `hyperopen.startup.runtime/invalidate-order-history-request!`, `hyperopen.vaults.detail.benchmarks/benchmark-selector-options`, and `hyperopen.vaults.detail.performance/benchmark-performance-column`.
- `hyperopen.views.account-equity.format/unified-account-ratio-tooltip`, `hyperopen.views.account-equity.format/unified-account-leverage-tooltip`, `hyperopen.views.account-info.position-margin-modal/coin-label`, and the nine `hyperopen.views.asset-selector-view/` definitions `mobile-favorite-button`, `asset-list-body`, `asset-list-window-state`, `asset-list-viewport-covered?`, `asset-list-window-covered?`, `schedule-asset-list-render-limit-sync!`, `asset-list-now-ms`, `asset-list-set-timeout!`, and `asset-list-clear-timeout!`.
- `hyperopen.views.footer.links/social-link-shell-classes`, `hyperopen.views.leaderboard.rows/sortable-header`, `hyperopen.views.portfolio.optimize.setup-universe/eyebrow-class`, `hyperopen.views.portfolio.vm/selected-summary-key`, `hyperopen.views.portfolio.vm/selected-summary-entry`, `hyperopen.views.trade-view/render-account-equity-metrics`, `hyperopen.views.trade-view/trade-chart-panel-content`, `hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay/menu-items`, `hyperopen.views.ui.dialog-focus/document-body`, and `hyperopen.views.ui.funding-modal-positioning/anchor-number`.
- Transitive post-edit additions: `hyperopen.vaults.detail.performance/performance-periods-per-year` (only used by removed `benchmark-performance-column`) and `hyperopen.views.leaderboard.rows/sort-direction-icon` (only used by removed `sortable-header`).

Remove these seven zero-call JavaScript tooling helpers, totalling 51 current lines:

- `tools/browser-inspection/src/util.mjs`: `chunk` (lines 76–82).
- `tools/browser-inspection/src/design_review_contracts.mjs`: `isDesignReviewContractError` (253–255).
- `tools/browser-inspection/src/design_review/pass_registry.mjs`: `configuredPassRegistry` (627) and `evaluatePass` (633–663).
- `tools/multi-agent/src/paths.mjs`: `toAbsoluteRepoPath` (20–22).
- `tools/browser-inspection/src/scenario_contracts.mjs`: `isScenarioContractError` (127–129).
- `tools/browser-inspection/src/contracts.mjs`: `isContractError` (121–123).

Remove exactly these 11 unused ClojureScript require edges:

- `hyperopen.referrals.actions` from `src/hyperopen/runtime/action_adapters.cljs`.
- `clojure.string` from `src/hyperopen/schema/vault_transfer_contracts.cljs`, `src/hyperopen/views/account_info/projections/orders.cljs`, `src/hyperopen/views/app_view.cljs`, and `src/hyperopen/views/funding_comparison_view.cljs`.
- `hyperopen.staking.actions` from `src/hyperopen/views/app_view.cljs`.
- `hyperopen.portfolio.optimizer.application.view-model` from `src/hyperopen/views/portfolio/optimize/setup_constraint_controls.cljs`.
- `hyperopen.views.portfolio.vm.constants` from `src/hyperopen/views/portfolio/vm/history.cljs`.
- `replicant.core` from `src/hyperopen/views/trading_chart/timeframe_dropdown.cljs`.
- Transitive post-edit additions: `hyperopen.domain.trading` from `src/hyperopen/funding/domain/assets.cljs` (only used by removed `parse-num`) and `hyperopen.portfolio.optimizer.coercion` from `src/hyperopen/portfolio/optimizer/domain/equal_risk.cljs` (only used by removed `finite-number?`).

Remove the literal no-op strings inside four functions in `src/hyperopen/utils/formatting.cljs` (`safe-to-fixed`, `safe-number`, `calculate-open-interest-usd`, and `format-open-interest-usd`) and six functions in `src/hyperopen/views/trading_chart/utils/data_processing.cljs` (`process-candle-data`, `process-volume-data`, `generate-mock-candles`, `update-last-candle`, `format-price`, and `format-volume`). Do not attempt to convert these expressions into doc metadata as part of this cleanup.

The conditional test-seam review covers exactly 32 names in `hyperopen.api.endpoints.vaults` (6), `hyperopen.api.trading` (9), `hyperopen.funding.domain.policy` (8), `hyperopen.vaults.application.list-vm` (1), `hyperopen.views.account-equity-view` (1), `hyperopen.views.portfolio.vm` (1), `hyperopen.views.trading-chart.utils.chart-interop.volume-indicator-overlay` (1), `hyperopen.views.vaults.detail-vm` (4), and `hyperopen.websocket.trades` (1). Initial implementation must leave them unchanged.

## Plan of Work

First, run the worktree setup and inspect the listed namespace and symbol references across `src`, `test`, `dev`, `portfolio`, and `tools`. Stop and record a discovery if an in-scope item has a runtime, generated-entrypoint, or dynamic-load reference not accounted for here. The `worker` then deletes only the five named source files and the listed definitions/expressions/require entries. Retain `test/hyperopen/views/portfolio/optimize/universe_panel_test.cljs`; it is live regression coverage through `hyperopen.views.portfolio-view`, not coverage of the deleted namespace.

For JavaScript, first inspect package manifests, tool command help, and repository documentation for each of the seven named helpers. Delete a helper only when that check confirms it is not a package export or documented public tool interface, as well as having no caller. Do not remove containing modules, alter command-line contracts, or make a broad export-surface change. For the ClojureScript definitions, keep surrounding APIs and function signatures untouched. Re-run the source reference checks after each deletion batch and use the compiler to catch stale imports.

Do not start a test-seam migration as a cleanup goal. A seam can be removed only if the direct migration consists of moving each current assertion to an existing owning-module behavioral test, with the same input, boundary, and result checks, and the focused test passes before and after. If that proof is not immediate, retain it and write the retained count in this plan's retrospective.

## Concrete Steps

Run all commands from `/hyperopen` (the repository root):

    npm run setup:worktree
    git status --short
    rg -n 'hyperopen\\.(api\\.legacy|trading\\.order-form-contracts|views\\.trade\\.order-form-presenter|views\\.footer\\.market-projection-diagnostics|views\\.portfolio\\.optimize\\.universe-panel)' src test dev portfolio tools

Use the audit list above, including its four explicitly recorded transitive post-edit additions, as the only deletion authority. Compile the existing test runner and rerun semantic diagnostics after the edits:

    npx shadow-cljs --force-spawn compile test
    clojure-lsp diagnostics --project-root .

Run the approved ClojureScript coverage suite; its test runner does not filter by `--test` arguments:

    npm test

Run focused tooling coverage after the seven helper deletions:

    node --test tools/browser-inspection/test/design_review_pass_registry.test.mjs tools/browser-inspection/test/design_review_runner.test.mjs tools/browser-inspection/test/design_review_loader.test.mjs tools/browser-inspection/test/cli_contract.test.mjs tools/browser-inspection/test/scenario_contracts.test.mjs
    npm run test:multi-agent

Finally run the required complete validation matrix:

    npm run gates
    npx shadow-cljs release app

Expected result: each command exits zero; `npm run gates` prints an all-PASS matrix for `npm run check`, `npm test`, and `npm run test:websocket` rather than stopping at an earlier failure. The final release command completes the advanced application build.

## Validation and Acceptance

Acceptance is frozen to these observable outcomes:

- [x] The five listed production files are absent. `test/hyperopen/views/portfolio/optimize/universe_panel_test.cljs` and its `hyperopen.views.portfolio.optimize.universe-panel-test` test-runner entry remain, and `npm test` passes with that live optimizer regression coverage intact.
- [x] Repository-wide searches over `src test dev portfolio tools` find no production or test call/import for any deleted namespace, each of the 40 listed private definitions, or the seven listed JavaScript helpers. The final clj-kondo audit found no additional in-scope/transitive item.
- [x] Targeted source inspection and the final temporary-binary clj-kondo audit show that the 11 listed unused require edges and ten exact no-op string expressions are gone; the three `hyperopen.core.compat` registration requires remain.
- [x] `npm test` and the focused Node commands exit zero, demonstrating that the still-reachable optimizer setup surface, require-edge owners, and browser-tool contracts—including `isScenarioContractError` scenario-contract coverage—behave as before.
- [x] Each JavaScript helper had no caller, package export, or documented public-tool-interface contract before deletion.
- [x] All 32 test-only seams are unchanged; no test seam was deleted.
- [x] `npm run gates` exits zero: PASS 34/34, 6,494 tests, 35,266 assertions, zero failures, in 1m46s.
- [x] `npx shadow-cljs release app` exits zero after the gates: 1,263 files, 1,110 compiled, 0 warnings, in 35.85s.
- [x] Browser QA is **skipped**: no Browser MCP session, visual comparison, or browser Playwright run is required because no reachable UI or interaction behavior changed.

## Idempotence and Recovery

`npm run setup:worktree`, test-runner generation, compiles, and test commands are safe to rerun. Make deletion edits with file-scoped patches so a failed compile can be recovered by restoring only the last target from version control or by reapplying the prior patch in reverse; do not use broad reset or checkout commands. If a supposedly dead item has an unanticipated reference, restore that single item, retain it out of scope, and document the evidence instead of introducing a compatibility wrapper.

## Interfaces and Dependencies

No new interface, dependency, route, action, or public API is introduced. The change intentionally preserves all remaining public interfaces. ClojureScript compilation is performed by local `shadow-cljs`; the generated test runner is the source of truth for test registration. `clojure-lsp` is a supplementary semantic recheck, while the compiler and complete gate matrix are the final correctness evidence.

## Artifacts and Notes

At completion, append concise command results here, including the existing tests run (no new tests materialized), JavaScript public-interface checks, whether any conditional test seam was migrated, the `npm run gates` PASS/FAIL matrix, and the release-build result. Do not create a separate requirements document or durable tracker for this ticket.

Plan revision record: created on 2026-07-28 from the direct user request and its supplied audit. Updated on 2026-07-28 after direct inspection established that `universe_panel_test.cljs` is live `portfolio-view` regression coverage, not an orphan-only test. Updated again with the approved test contract: no new tests, `npm test` rather than ignored test-runner arguments, scenario-contract Node coverage, semantic diagnostics, release-build confidence, and JavaScript public-interface proof. Finalized on 2026-07-28 after review approval, a PASS 34/34 gate matrix, zero-warning release compilation, and a post-edit clj-kondo audit. The detailed frozen target list is embedded so a later contributor can execute the cleanup without the temporary audit directory.
