# Refactor Vault Detail Activity View Namespace

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained under `/hyperopen/.agents/PLANS.md` and follows that contract. The live `bd` issue for this work is `hyperopen-5mmz`, titled `Refactor vault detail activity view namespace`.

## Purpose / Big Picture

The vault detail activity surface already works, but its renderer is concentrated in one 919-line view namespace: `src/hyperopen/views/vaults/detail/activity.cljs`. That file currently owns performance-metric rendering, tab and filter shell markup, table header chrome, row tone helpers, all activity table bodies, and the public `activity-panel` entry point. After this refactor, users should see the same vault detail activity behavior on `/vaults/<address>`, while contributors can change performance metrics, table rows, or the panel shell in smaller files with clearer ownership.

The observable outcome is behavior-preserving: the vault detail route still renders the activity panel, performance metrics, all activity tabs, sort headers, direction filter, loading/error/empty states, and the position coin navigation affordance. The maintainability outcome is also observable: `src/hyperopen/views/vaults/detail/activity.cljs` becomes a small facade below the namespace-size threshold, extracted production namespaces are also below the threshold, and the old exception entry for `src/hyperopen/views/vaults/detail/activity.cljs` is removed from `dev/namespace_size_exceptions.edn`.

This is not performance-motivated work. Do not add memoization, virtualization, async loading, or data-shape changes in this plan unless the plan is amended with a baseline measurement, workload assumptions, and a reason a simpler extraction is insufficient.

## Progress

- [x] (2026-04-20 02:17Z) Read repository operating and planning contracts: `AGENTS.md`, `docs/PLANS.md`, `.agents/PLANS.md`, `docs/MULTI_AGENT.md`, `docs/WORK_TRACKING.md`, `docs/FRONTEND.md`, `docs/BROWSER_TESTING.md`, and `docs/agent-guides/browser-qa.md`.
- [x] (2026-04-20 02:17Z) Inspected current activity implementation and tests: `src/hyperopen/views/vaults/detail/activity.cljs`, `src/hyperopen/vaults/detail/activity.cljs`, `src/hyperopen/views/vaults/detail_vm.cljs`, `test/hyperopen/vaults/detail/activity_test.cljs`, and `test/hyperopen/views/vaults/detail/activity_test.cljs`.
- [x] (2026-04-20 02:17Z) Confirmed `bd ready --json` reports live task `hyperopen-5mmz` for this exact refactor.
- [x] (2026-04-20 02:17Z) Authored this active ExecPlan draft before any production-code edits.
- [x] (2026-04-20 02:22Z) Reviewed acceptance, edge-case, and explorer agent findings and folded the useful constraints into this plan.
- [x] (2026-04-20 02:22Z) Claimed `hyperopen-5mmz` with `bd update hyperopen-5mmz --claim --json`.
- [x] (2026-04-20 02:26Z) Materialized RED tests for extracted performance metrics and table contracts.
- [x] (2026-04-20 02:26Z) Verified the RED test compile fails for the expected missing namespace: `hyperopen.views.vaults.detail.activity.performance-metrics`.
- [x] (2026-04-20 02:30Z) Re-ran the RED contract locally: `npm run test:runner:generate` succeeded, then `npx shadow-cljs --force-spawn compile test` failed on the expected missing `hyperopen.views.vaults.detail.activity.performance-metrics` namespace.
- [x] (2026-04-20 02:34Z) Split the vault detail activity renderer into facade, shell, performance-metrics, table-chrome, and tables namespaces while preserving `hyperopen.views.vaults.detail.activity/activity-panel`.
- [x] (2026-04-20 02:34Z) Verified the post-split test compile with `npx shadow-cljs --force-spawn compile test`; build completed with 1299 files, 78 compiled, and 0 warnings.
- [x] (2026-04-20 02:35Z) Confirmed line counts are below the namespace-size threshold: facade 4 lines, performance metrics 358, shell 162, table chrome 107, tables 300.
- [x] (2026-04-20 02:35Z) Removed the retired `src/hyperopen/views/vaults/detail/activity.cljs` entry from `dev/namespace_size_exceptions.edn`.
- [x] (2026-04-20 02:50Z) Passed focused activity validation with `node out/test.js --test=hyperopen.views.vaults.detail.activity-test,hyperopen.views.vaults.detail.activity.performance-metrics-test,hyperopen.views.vaults.detail.activity.tables-test`: 11 tests, 144 assertions, 0 failures.
- [x] (2026-04-20 02:40Z) Passed `npm run lint:namespace-sizes` after removing the retired source exception.
- [x] (2026-04-20 02:51Z) Passed `npm test`: 3317 tests, 18130 assertions, 0 failures.
- [x] (2026-04-20 02:51Z) Passed `npm run test:websocket`: 449 tests, 2701 assertions, 0 failures.
- [x] (2026-04-20 02:52Z) Passed `npm run check`.
- [x] (2026-04-20 02:43Z) Passed targeted Playwright vault activity flows: `vault detail chart and activity state replace the URL and restore from a fresh shared link` and `vault position coin jumps to the trade route market`.
- [x] (2026-04-20 02:47Z) Passed governed design review for `vault-detail-route` at widths 375, 768, 1280, and 1440; artifact `tmp/browser-inspection/design-review-2026-04-20T02-46-57-260Z-3fca9aee`.
- [x] (2026-04-20 02:47Z) Ran `npm run browser:cleanup` and stopped session `sess-1776653211654-ae9344`.
- [x] (2026-04-20 02:50Z) Completed final read-only reviewer pass; no render-code regression found. Added the requested `100+` tab-count boundary assertion.
- [x] (2026-04-20 02:54Z) Closed `bd` issue `hyperopen-5mmz` and moved this plan to completed.

## Surprises & Discoveries

- Observation: The domain/application activity model is already separated from the view renderer.
  Evidence: `src/hyperopen/vaults/detail/activity.cljs` owns tab metadata, direction filtering, stable sort columns, sort-state normalization, and `project-rows`. `src/hyperopen/views/vaults/detail_vm.cljs` consumes those functions when building the activity section.

- Observation: The oversized source namespace is a view-rendering problem, not a data/model problem.
  Evidence: `src/hyperopen/views/vaults/detail/activity.cljs` is 919 lines and contains only Hiccup-rendering helpers plus view-local formatting/tone helpers. The paired domain test `test/hyperopen/vaults/detail/activity_test.cljs` is 27 lines.

- Observation: The current view test exercises private helpers directly, so extraction should move those assertions to the owning extracted namespaces instead of keeping the root facade as a test-only private API hub.
  Evidence: `test/hyperopen/views/vaults/detail/activity_test.cljs` uses forms such as `@#'activity/format-activity-count`, `@#'activity/fills-table`, and `@#'activity/depositors-table`.

- Observation: There is already a namespace-size exception for the target file.
  Evidence: `dev/namespace_size_exceptions.edn` contains `{:path "src/hyperopen/views/vaults/detail/activity.cljs" ... :max-lines 919 ...}`.

- Observation: The acceptance and edge-case agents identified useful contracts that are not explicit enough in the current tests.
  Evidence: proposed additions include tab button action/count rendering, benchmark grid-width preservation, loading overlay `aria-live`, async table state precedence and colspans, Replicant-safe class/style attrs, and unsupported-tab isolation.

- Observation: The RED phase failed for the intended reason before production extraction.
  Evidence: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test` generated 515 namespaces, then failed with `The required namespace "hyperopen.views.vaults.detail.activity.performance-metrics" is not available`.

- Observation: The first compile after extraction failed on a delimiter error in `performance_metrics.cljs`, not a contract issue.
  Evidence: `npx shadow-cljs --force-spawn compile test` reported an unmatched delimiter at `src/hyperopen/views/vaults/detail/activity/performance_metrics.cljs:159:36`; fixing the extracted `resolved-benchmark-metric-columns` closing form restored a clean test compile.

- Observation: Focused activity validation passes after the split.
  Evidence: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.views.vaults.detail.activity-test,hyperopen.views.vaults.detail.activity.performance-metrics-test,hyperopen.views.vaults.detail.activity.tables-test` completed with 11 tests, 144 assertions, 0 failures, and 0 errors after adding the reviewer-requested tab-count cap assertion.

- Observation: Managed design review could not start a second local app while an existing shadow-cljs server was running, but a session-based review against the existing local URL succeeded.
  Evidence: `npm run qa:design-ui -- --targets vault-detail-route --manage-local-app` failed because shadow-cljs was already running on `http://localhost:9630`; then `node tools/browser-inspection/src/cli.mjs session start --local-url http://127.0.0.1:8080` followed by `node tools/browser-inspection/src/cli.mjs design-review --targets vault-detail-route --session-id sess-1776653211654-ae9344` returned `reviewOutcome: PASS`.

## Decision Log

- Decision: Keep `src/hyperopen/vaults/detail/activity.cljs` as the model boundary and do not move sorting, filtering, tabs, or table column metadata into view namespaces.
  Rationale: That namespace is already the pure activity model used by `detail_vm.cljs`, and moving those concerns back into views would violate the existing architecture boundary.
  Date/Author: 2026-04-20 / Codex

- Decision: Preserve the public view API by keeping `hyperopen.views.vaults.detail.activity/activity-panel` as the route-facing entry point.
  Rationale: `detail_vm.cljs`, tests, and route composition already depend on the root activity namespace. A facade keeps caller churn low while still retiring the oversized implementation body.
  Date/Author: 2026-04-20 / Codex

- Decision: Split by rendered concern, not by arbitrary line count.
  Rationale: The current file has natural ownership clusters: performance metrics, shell/tab/filter controls, table chrome, and concrete table renderers. Those seams reduce future merge conflicts and keep tests meaningful.
  Date/Author: 2026-04-20 / Codex

- Decision: Treat browser QA as required but behavior-preserving.
  Rationale: The work touches `src/hyperopen/views/**`, so `docs/FRONTEND.md` requires governed browser-QA accounting. Because this is a refactor, deterministic tests should prove behavior parity, and Browser MCP design review should verify that no visual or interaction drift was introduced.
  Date/Author: 2026-04-20 / Codex

- Decision: Materialize focused tests under `test/hyperopen/views/vaults/detail/activity/` before moving production code.
  Rationale: The current root view test is already 459 lines, and growing it would risk creating a new namespace-size exception. Focused tests also prove that the new namespaces are real owners rather than a cosmetic split.
  Date/Author: 2026-04-20 / Codex

## Outcomes & Retrospective

Implementation split complete; final review is still in progress. Current production line counts after extraction are: `activity.cljs` 4, `activity/performance_metrics.cljs` 358, `activity/shell.cljs` 162, `activity/table_chrome.cljs` 107, and `activity/tables.cljs` 300. The stale namespace-size exception for `src/hyperopen/views/vaults/detail/activity.cljs` has been removed. Focused activity tests, full CLJS tests, websocket tests, `npm run check`, targeted Playwright vault flows, and governed design review have all passed. The refactor reduced overall complexity by replacing one 919-line mixed screen namespace with focused owners and a 4-line facade, without changing the public `activity/activity-panel` API.

Browser-QA accounting: visual PASS, native-control PASS, styling-consistency PASS, layout-regression PASS, and jank/perf PASS from design review artifact `tmp/browser-inspection/design-review-2026-04-20T02-46-57-260Z-3fca9aee`. Interaction is accounted as PASS through targeted Playwright coverage for vault activity tab URL restoration and position coin navigation; the design-review runner marked interaction `NOT_APPLICABLE` with residual blind spots because the reviewed `/vaults/detail` target exposed no focusable controls in its sampled state.

Final outcome update (2026-04-20T02:52Z): Final reviewer feedback was addressed by adding a `100+` tab-count assertion to the facade test. Fresh validation after that change passed: focused activity tests 11/144, full `npm test` 3317/18130, `npm run test:websocket` 449/2701, and `npm run check`.

## Context and Orientation

In this repository, views are ClojureScript functions that return Hiccup data. Hiccup is a vector representation of DOM, for example `[:button {:type "button"} "Label"]`. Replicant turns that Hiccup into browser DOM.

The vault detail page is assembled from the view model in `src/hyperopen/views/vaults/detail_vm.cljs`. That view model builds an activity-section map with these keys: `:activity-tabs`, `:selected-activity-tab`, `:activity-direction-filter`, `:activity-filter-open?`, `:activity-filter-options`, `:activity-table-config`, `:activity-sort-state-by-tab`, activity row vectors for each tab, and activity loading/error maps. It imports `hyperopen.vaults.detail.activity` as `activity-model`; that model namespace owns pure sorting/filtering behavior and must remain the source of truth for activity tab semantics.

The screen renderer to split is `src/hyperopen/views/vaults/detail/activity.cljs`, namespace `hyperopen.views.vaults.detail.activity`. It currently requires `clojure.string`, `hyperopen.router`, `hyperopen.views.ui.performance-metrics-tooltip`, `hyperopen.views.vaults.detail.chart-view`, `hyperopen.views.vaults.detail.format`, and `hyperopen.wallet.core`. It exports only `activity-panel`; the rest of the functions are private. It contains the performance metrics card, activity tabs and direction filter, table sort headers, empty/error rows, row tone helpers, position coin navigation, and ten tab-specific render paths.

The current tests are split between model and view. `test/hyperopen/vaults/detail/activity_test.cljs` covers model behavior for stable sort IDs, legacy label normalization, and direction-filtered row projection. `test/hyperopen/views/vaults/detail/activity_test.cljs` covers view rendering for performance metrics, estimated banners/tooltips, the timeframe dropdown, filter states, fallback tab text, position coin navigation, loading overlay copy, and table loading/error/empty/row branches.

## Scope and Non-Goals

This plan covers only the vault detail activity view namespace and directly paired tests plus generated test discovery and namespace-size exception cleanup. The worker may edit:

- `src/hyperopen/views/vaults/detail/activity.cljs`
- new files under `src/hyperopen/views/vaults/detail/activity/`
- `test/hyperopen/views/vaults/detail/activity_test.cljs`
- new files under `test/hyperopen/views/vaults/detail/activity/`
- `test/test_runner_generated.cljs` if regenerated
- `dev/namespace_size_exceptions.edn`
- this active ExecPlan
- `/hyperopen/tmp/multi-agent/hyperopen-5mmz/**` artifacts if using the manager flow

Do not change the public sort/filter model in `src/hyperopen/vaults/detail/activity.cljs`, do not change `src/hyperopen/views/vaults/detail_vm.cljs` unless a compile failure proves the facade contract was not preserved, and do not change vault API/effects/actions behavior. Do not redesign the panel, change colors, change table copy, change route loading behavior, or add new browser persistence.

## Plan of Work

First, materialize RED tests under `test/hyperopen/views/vaults/detail/activity/` and run `npm run test:runner:generate` plus `npx shadow-cljs --force-spawn compile test`. The expected RED failure is that `hyperopen.views.vaults.detail.activity.performance-metrics`, `hyperopen.views.vaults.detail.activity.table-chrome`, `hyperopen.views.vaults.detail.activity.tables`, or `hyperopen.views.vaults.detail.activity.shell` cannot be found. If the compile fails for malformed test syntax instead, fix the test until the missing namespace is the failure.

Second, create narrow view namespaces under `src/hyperopen/views/vaults/detail/activity/` and move code in behavior-preserving chunks. A concrete split is:

- `src/hyperopen/views/vaults/detail/activity/performance_metrics.cljs`, namespace `hyperopen.views.vaults.detail.activity.performance-metrics`: move `format-signed-percent-from-decimal`, `format-ratio-value`, `format-integer-value`, `format-metric-value`, low-confidence reason ordering/banner helpers, estimated banner rendering, performance metric value cells, benchmark column/value/status/reason helpers, row visibility helpers, grid-style calculation, `performance-metric-row`, and `performance-metrics-card`.
- `src/hyperopen/views/vaults/detail/activity/table_chrome.cljs`, namespace `hyperopen.views.vaults.detail.activity.table-chrome`: move sort header rendering, table header rendering, empty/error rows, shared row/cell class vectors, PNL/side/status/ledger tone helpers, side coin cell styling, and simple interactive value class helpers.
- `src/hyperopen/views/vaults/detail/activity/tables.cljs`, namespace `hyperopen.views.vaults.detail.activity.tables`: move `balances-table`, `positions-table`, `open-orders-table`, `twap-table`, `fills-table`, `funding-history-table`, `order-history-table`, `ledger-table`, and `depositors-table`, plus position-row-specific helpers such as `position-row-key`, `position-coin-click-actions`, `position-coin-cell`, and position text formatting.
- `src/hyperopen/views/vaults/detail/activity/shell.cljs`, namespace `hyperopen.views.vaults.detail.activity.shell`: move `format-activity-count`, `activity-tab-button`, and the real `activity-panel` shell that renders the tab row, direction filter, and selected tab body by delegating to `performance-metrics/performance-metrics-card` and `tables/*`.
- `src/hyperopen/views/vaults/detail/activity.cljs`, namespace `hyperopen.views.vaults.detail.activity`: reduce this file to a compatibility facade requiring `hyperopen.views.vaults.detail.activity.shell` and exposing `activity-panel`. Prefer `(def activity-panel shell/activity-panel)` unless the local style or lint requires a forwarding `defn`.

Third, migrate tests to match the split. Keep root `test/hyperopen/views/vaults/detail/activity_test.cljs` for facade-level behavior, especially that `(activity/activity-panel props)` still renders the expected root `data-role`, filter actions, fallback tab message, and position coin navigation. Move performance metric assertions to `test/hyperopen/views/vaults/detail/activity/performance_metrics_test.cljs`. Move table loading/error/empty/row branch assertions and table-chrome helper assertions to `test/hyperopen/views/vaults/detail/activity/tables_test.cljs`. Move shell-only tab/filter/count assertions to `test/hyperopen/views/vaults/detail/activity/shell_test.cljs` if keeping them in the root facade test would force private-var access. Regenerate `test/test_runner_generated.cljs` after adding namespaces.

Fourth, remove the exact `src/hyperopen/views/vaults/detail/activity.cljs` entry from `dev/namespace_size_exceptions.edn` only after the root facade and all extracted production namespaces are below the repository threshold. Do not add a replacement exception for a new extracted namespace. If one extracted file remains oversized, split it further by tab family before proceeding.

Fifth, run deterministic validation and browser QA. Update this plan after each stopping point with commands, results, surprises, and any deviation from the intended file split.

## Concrete Steps

Run these from the repository root `/Users/barry/.codex/worktrees/f168/hyperopen`.

1. Start from a clean understanding of current sizes and public callers:

    wc -l src/hyperopen/views/vaults/detail/activity.cljs test/hyperopen/views/vaults/detail/activity_test.cljs test/hyperopen/vaults/detail/activity_test.cljs
    rg -n "hyperopen.views.vaults.detail.activity|activity/activity-panel|views/vaults/detail/activity.cljs" src test dev docs

2. Claim or update the tracker when implementation starts:

    bd update hyperopen-5mmz --claim --json

3. Add focused RED tests for `performance-metrics`, `table-chrome`/`tables`, and shell/facade behavior. Then run:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test

   The compile should fail because the new production namespaces do not exist yet. Record the failure in this plan before moving production code.

4. Add the new view namespaces under `src/hyperopen/views/vaults/detail/activity/` and move code one concern at a time. After each move, keep the root facade compiling and avoid changing emitted Hiccup shape except for namespace-qualified implementation ownership.

5. Add or move the paired tests under `test/hyperopen/views/vaults/detail/activity/`. Regenerate test discovery:

    npm run test:runner:generate

6. Confirm line-count and exception cleanup:

    wc -l src/hyperopen/views/vaults/detail/activity.cljs src/hyperopen/views/vaults/detail/activity/*.cljs
    rg -n "src/hyperopen/views/vaults/detail/activity.cljs" dev/namespace_size_exceptions.edn

   The `rg` command must print no matches before completion.

7. Run focused deterministic tests first. If the test runner does not support selecting namespaces through `npm test -- ...`, run the full `npm test` command and record that the selection path is unavailable.

    npm test

8. Run the smallest relevant Playwright coverage before broader browser checks:

    npx playwright test tools/playwright/test/shareable-view-url.spec.mjs tools/playwright/test/trade-regressions.spec.mjs --grep "vault detail chart and activity state|vault position coin jumps"

   This should exercise vault detail activity tab/filter URL restoration and position coin navigation. If this command selects zero tests because titles changed, inspect the matching Playwright specs and update the grep to run those same user flows.

9. Run required repo gates for code changes:

    npm run check
    npm test
    npm run test:websocket

10. Run governed browser QA for the changed UI target and clean up browser-inspection sessions:

    npm run qa:design-ui -- --targets vault-detail-route --manage-local-app
    npm run browser:cleanup

   The browser-QA report must account for all six passes: visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf. It must account for widths `375`, `768`, `1280`, and `1440`. Each pass must be reported as `PASS`, `FAIL`, or `BLOCKED`, with artifact paths and residual blind spots.

## Validation and Acceptance

Acceptance is behavior and evidence based:

- Running `wc -l src/hyperopen/views/vaults/detail/activity.cljs src/hyperopen/views/vaults/detail/activity/*.cljs` shows the root facade and every extracted production namespace below the namespace-size threshold enforced by `npm run lint:namespace-sizes`.
- Running `rg -n "src/hyperopen/views/vaults/detail/activity.cljs" dev/namespace_size_exceptions.edn` prints no matches, proving the retired oversized-source exception was removed.
- Running `npm test` passes with the existing model activity tests and the new or updated view activity tests. The covered behavior must include performance metrics visible-row filtering, estimated metric banner/tooltips, timeframe dropdown action wiring, tab/filter action wiring, fallback tab message, all table loading/error/empty row branches, and the position coin navigation action sequence.
- Running `npx playwright test tools/playwright/test/shareable-view-url.spec.mjs tools/playwright/test/trade-regressions.spec.mjs --grep "vault detail chart and activity state|vault position coin jumps"` passes and observes the same route-level activity tab/filter restoration and position coin navigation behavior after extraction.
- Running `npm run check` passes, including generated test discovery, hiccup lint, docs lint, namespace-size lint, namespace-boundary lint, and ClojureScript compilation.
- Running `npm run test:websocket` passes, proving websocket runtime behavior was not disturbed by the UI refactor.
- Running `npm run qa:design-ui -- --targets vault-detail-route --manage-local-app` produces a browser-QA result that explicitly marks visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf as `PASS`, `FAIL`, or `BLOCKED` across `375`, `768`, `1280`, and `1440`, followed by `npm run browser:cleanup`.
- The root public entry point remains callable as `hyperopen.views.vaults.detail.activity/activity-panel`, and the route-facing Hiccup keeps `data-role="vault-detail-activity-panel"` plus existing action vectors such as `[:actions/set-vault-detail-activity-tab ...]`, `[:actions/toggle-vault-detail-activity-filter-open]`, `[:actions/set-vault-detail-activity-direction-filter ...]`, `[:actions/sort-vault-detail-activity tab id]`, and `[:actions/navigate "/trade/<coin>"]`.

If any acceptance criterion cannot be met, update this plan and `bd` with the blocker before stopping. Do not close `hyperopen-5mmz` until the acceptance criteria and required gates are accounted for.

## Idempotence and Recovery

The implementation should be additive first: create new namespaces, move one concern at a time, and keep the root facade working after each move. This makes the work safe to retry. If a compile or test failure appears, isolate it by temporarily routing the facade back to the old local function for the last moved concern, then retry with a smaller extraction.

Do not use destructive git commands for recovery. If namespace-size lint fails after extraction, split the oversized extracted owner further; do not re-add an exception for `src/hyperopen/views/vaults/detail/activity.cljs`. If browser QA fails because the route cannot be inspected by the managed target, record that pass as `BLOCKED` with the artifact path and keep deterministic Playwright results as the behavior evidence.

## Artifacts and Notes

Primary docs and code inspected while drafting:

- `AGENTS.md`
- `docs/PLANS.md`
- `.agents/PLANS.md`
- `docs/MULTI_AGENT.md`
- `docs/WORK_TRACKING.md`
- `docs/FRONTEND.md`
- `docs/BROWSER_TESTING.md`
- `docs/agent-guides/browser-qa.md`
- `tools/browser-inspection/config/design-review-routing.json`
- `src/hyperopen/views/vaults/detail/activity.cljs`
- `src/hyperopen/vaults/detail/activity.cljs`
- `src/hyperopen/views/vaults/detail_vm.cljs`
- `test/hyperopen/vaults/detail/activity_test.cljs`
- `test/hyperopen/views/vaults/detail/activity_test.cljs`
- `dev/namespace_size_exceptions.edn`
- `docs/exec-plans/completed/2026-02-28-vault-detail-view-composition-split-and-stable-sort-ids.md`

Current baseline facts:

- `src/hyperopen/views/vaults/detail/activity.cljs`: 919 lines.
- `test/hyperopen/views/vaults/detail/activity_test.cljs`: 459 lines.
- `test/hyperopen/vaults/detail/activity_test.cljs`: 27 lines.
- `bd ready --json` reports `hyperopen-5mmz` as open, priority 2, labels `architecture`, `refactor`, and `ui`.

## Interfaces and Dependencies

No new external dependencies are expected.

The public interface to preserve is:

- `hyperopen.views.vaults.detail.activity/activity-panel`

The internal view interfaces to create are:

- `hyperopen.views.vaults.detail.activity.performance-metrics/performance-metrics-card`
- `hyperopen.views.vaults.detail.activity.table-chrome/table-header`
- `hyperopen.views.vaults.detail.activity.table-chrome/empty-table-row`
- `hyperopen.views.vaults.detail.activity.table-chrome/error-table-row`
- `hyperopen.views.vaults.detail.activity.table-chrome/position-pnl-class`
- `hyperopen.views.vaults.detail.activity.table-chrome/side-tone-class`
- `hyperopen.views.vaults.detail.activity.table-chrome/side-coin-tone-class`
- `hyperopen.views.vaults.detail.activity.table-chrome/side-coin-cell-style`
- `hyperopen.views.vaults.detail.activity.table-chrome/status-tone-class`
- `hyperopen.views.vaults.detail.activity.table-chrome/ledger-type-tone-class`
- `hyperopen.views.vaults.detail.activity.tables/balances-table`
- `hyperopen.views.vaults.detail.activity.tables/positions-table`
- `hyperopen.views.vaults.detail.activity.tables/open-orders-table`
- `hyperopen.views.vaults.detail.activity.tables/twap-table`
- `hyperopen.views.vaults.detail.activity.tables/fills-table`
- `hyperopen.views.vaults.detail.activity.tables/funding-history-table`
- `hyperopen.views.vaults.detail.activity.tables/order-history-table`
- `hyperopen.views.vaults.detail.activity.tables/ledger-table`
- `hyperopen.views.vaults.detail.activity.tables/depositors-table`
- `hyperopen.views.vaults.detail.activity.shell/activity-panel`

Keep these existing dependencies as the ownership boundaries:

- `hyperopen.vaults.detail.activity` remains the pure activity model for tabs, columns, sorting, and direction filtering.
- `hyperopen.views.vaults.detail_vm` remains the route view-model assembler.
- `hyperopen.views.vaults.detail.format` remains the vault detail formatting helper namespace.
- `hyperopen.views.vaults.detail.chart-view` remains the owner of `chart-timeframe-menu`.
- `hyperopen.views.ui.performance-metrics-tooltip` remains the tooltip rendering dependency for performance metric labels and estimated metric banners.
- `hyperopen.router` should be required only by the namespace that builds position coin navigation actions.
- `hyperopen.wallet.core` should be required only by the namespace that renders depositor address cells.

Revision note (2026-04-20): Initial active ExecPlan draft created by `spec_writer` for `hyperopen-5mmz`; no production code was edited.
