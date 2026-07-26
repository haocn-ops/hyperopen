---
owner: platform
status: completed
last_reviewed: 2026-03-13
review_cycle_days: 90
source_of_truth: false
---

# Reduce Order History Direction CRAP

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs` should keep the same order-history direction labels and colors, but the direction logic will no longer live in one branch-heavy hotspot. A contributor should be able to understand how explicit direction text, reduce-only fallbacks, and buy/sell coloring work without tracing one private function with many conditionals.

The observable proof from `/hyperopen` is:

    npx shadow-cljs compile test
    npm run check
    npm test
    npm run test:websocket
    npm ci
    npm run coverage
    bb tools/crap_report.clj --module src/hyperopen/views/account_info/tabs/order_history.cljs --format json

The compile pass gives fast feedback during the refactor. The repository gates confirm the change does not regress the shared workspace, `npm ci` records the dependency recovery needed in this worktree, and the module-scoped CRAP report proves the touched file now has no functions above threshold.

## Progress

- [x] (2026-03-14 01:26Z) Reviewed `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/docs/WORK_TRACKING.md`, `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs`, and `/hyperopen/test/hyperopen/views/account_info/tabs/order_history_test.cljs` to confirm planning, tracking, and validation requirements.
- [x] (2026-03-14 01:26Z) Created and claimed `bd` issue `hyperopen-0599` for this hotspot reduction work.
- [x] (2026-03-14 01:26Z) Authored this active ExecPlan.
- [x] (2026-03-14 01:28Z) Refactored the direction label/class hotspot in `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs` into smaller pure helpers and data-driven lookup rules while preserving the rendered output.
- [x] (2026-03-14 01:30Z) Added targeted regression coverage in `/hyperopen/test/hyperopen/views/account_info/tabs/order_history_test.cljs` for explicit direction text, reduce-only fallback labels, and direction class inference.
- [x] (2026-03-14 01:34Z) Installed missing workspace dependencies with `npm ci` after the first `npm run check` attempt failed during `shadow-cljs` app compilation because `node_modules` was absent.
- [x] (2026-03-14 01:38Z) After the first CRAP rerun showed the module hotspot had moved to `sort-order-history-by-column`, extracted the sorter accessor table and order-id normalization into dedicated helpers and added direct derived-column sort regression coverage.
- [x] (2026-03-14 01:38Z) Ran `npx shadow-cljs compile test`, `npm run check`, `npm test`, `npm run test:websocket`, `npm run coverage`, and the module-scoped CRAP report successfully; confirmed `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs` now reports `crappy-functions = 0` and `max-crap = 12.0`.
- [x] (2026-03-14 01:38Z) Closed `hyperopen-0599` and moved this plan to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The user-reported hotspot sits in the order-history direction helpers near lines 138-165, not in the pagination or memoization logic lower in the file.
  Evidence: The file only has one branch-heavy private block in that region: `order-history-direction-label` plus `order-history-direction-class`.
- Observation: This worktree did not start with a `coverage/lcov.info` artifact, so a fresh coverage run is required before the CRAP report can verify the fix.
  Evidence: `bb tools/crap_report.clj --module src/hyperopen/views/account_info/tabs/order_history.cljs --format json` returned `Missing coverage/lcov.info` until coverage is regenerated.
- Observation: This worktree also lacked `node_modules`, which blocked the required `npm run check` gate at the app compile step even though the changed test build compiled cleanly.
  Evidence: The first `npm run check` attempt failed with `The required JS dependency "@noble/secp256k1" is not available` and pointed at `/Users/barry/.codex/worktrees/a07e/hyperopen/node_modules`.
- Observation: Removing the original direction hotspot was not sufficient by itself to finish the module because the CRAP tool then surfaced `sort-order-history-by-column` as the sole remaining hotspot.
  Evidence: The first post-refactor module report showed `sort-order-history-by-column` at `CRAP 173.355...`, `complexity 14`, `coverage 0.0666...`, and `crappy-functions = 1`.

## Decision Log

- Decision: Keep the refactor inside `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs` instead of splitting a new namespace.
  Rationale: The hotspot is a small pure view-policy seam. A local helper decomposition reduces CRAP without adding namespace churn or moving a tiny concern into a second file.
  Date/Author: 2026-03-14 / Codex
- Decision: Treat success as eliminating the module’s remaining CRAP hotspots, not merely lowering the reported function’s score somewhat.
  Rationale: Recent CRAP-remediation work in this repository uses `crappy-functions = 0` in the touched module as the clearest finish line.
  Date/Author: 2026-03-14 / Codex
- Decision: After the first CRAP rerun shifted the hotspot to `sort-order-history-by-column`, extract the column accessor table out of the `defn` and add direct derived-column sort tests instead of widening only the original direction tests.
  Rationale: The accessor map was a real readability seam inside the sorter, so extracting it reduced structural complexity and let the tests document the tricky derived columns such as Direction, Reduce Only, Trigger Conditions, TP/SL, Status, and Order ID.
  Date/Author: 2026-03-14 / Codex

## Outcomes & Retrospective

Implementation completed. `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs` now has smaller direction helpers, a dedicated accessor table for column sorting, and direct regression tests for both the direction policy and the derived sort columns in `/hyperopen/test/hyperopen/views/account_info/tabs/order_history_test.cljs`.

This reduced overall complexity. The user-reported direction hotspot was eliminated first, and the follow-up sorter extraction removed the module’s final remaining hotspot without changing public APIs. The final module CRAP report shows `crappy-functions = 0`, `project-crapload = 0.0`, and `max-crap = 12.0`, so the file is now comfortably below the threshold.

## Context and Orientation

`/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs` renders the desktop order-history table and owns a handful of small formatting helpers. The relevant branchy area has two responsibilities. First, `order-history-direction-label` decides what text to render in the Direction column by preferring any explicit `:direction` field, then falling back to reduce-only close labels, then finally using the open-orders side label helper. Second, `order-history-direction-class` decides whether that rendered label should be colored as buy/long (`text-success`), sell/short (`text-error`), or neutral (`text-base-content`).

`/hyperopen/src/hyperopen/views/account_info/tabs/open_orders.cljs` already exposes canonical side-based helpers: `"B"` maps to a long/buy tone and `"A"` or `"S"` map to a short/sell tone. The order-history view extends that with explicit labels such as `"Buy"`, `"Sell"`, `"Open Long"`, `"Open Short"`, `"Close Long"`, and `"Close Short"`, especially for reduce-only rows or API rows that include direction text directly.

`/hyperopen/test/hyperopen/views/account_info/tabs/order_history_test.cljs` is the established regression suite for this view. It already verifies rendering, formatting, pagination, and a reduce-only `"Close Long"` row. This plan will add direct tests for the private direction helpers there rather than creating a parallel test namespace.

## Plan of Work

First, reshape the direction text logic into small helpers. Introduce one helper that resolves the reduce-only fallback label from `:side`, one helper that chooses the final rendered direction label, and one helper that normalizes a rendered label into an action-side keyword such as `:buy` or `:sell`. Back the label-to-action mapping with small phrase collections so the code reads as policy instead of a long `cond`. This was implemented as `reduce-only-order-history-direction-label`, `fallback-order-history-direction-label`, `order-history-action-side-from-label`, and `order-history-action-side`.

Second, make the direction class helper depend on that smaller action-side helper. It should still prefer the canonical side-based class from `/hyperopen/src/hyperopen/views/account_info/tabs/open_orders.cljs` whenever the side is known, and only infer from label text when the side-based class is neutral. This is now the `order-history-direction-class` implementation.

Third, extend `/hyperopen/test/hyperopen/views/account_info/tabs/order_history_test.cljs` with direct regression coverage for the new helper seams. Cover explicit direction normalization, reduce-only fallback labels for both buy and sell sides, class inference from explicit direction labels when `:side` is absent, and the neutral fallback when the label does not match supported phrases. This work also added direct coverage for derived sort columns after the first CRAP rerun surfaced `sort-order-history-by-column`.

Finally, run the repository validation gates plus coverage and the module-scoped CRAP report, update this plan with the actual results, close `hyperopen-0599`, and move the plan to `/hyperopen/docs/exec-plans/completed/`. All of those steps completed successfully.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs` to split direction label/class policy into smaller helpers while keeping the same rendered output.
2. Edit `/hyperopen/test/hyperopen/views/account_info/tabs/order_history_test.cljs` to add targeted direction-helper regression tests.
3. Run a fast compile loop during development:

       npx shadow-cljs compile test

4. Run the repository gates and CRAP verification:

       npm ci
       npm run check
       npm test
       npm run test:websocket
       npm run coverage
       bb tools/crap_report.clj --module src/hyperopen/views/account_info/tabs/order_history.cljs --format json

Expected result: the tests and gates pass, and the CRAP report for `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs` shows `crappy_functions=0`. This is the final observed result.

## Validation and Acceptance

The work is complete when all of the following are true:

- Order-history rows still render `"Long"`, `"Short"`, `"Close Long"`, and `"Close Short"` exactly where they did before.
- Rows with explicit direction labels still receive the same success/error coloring, even when `:side` is missing or neutral.
- The direct order-history regression tests cover both explicit label paths and reduce-only fallback paths.
- `npm run check`, `npm test`, and `npm run test:websocket` pass.
- After `npm run coverage`, `bb tools/crap_report.clj --module src/hyperopen/views/account_info/tabs/order_history.cljs --format json` reports no functions above the default CRAP threshold.

## Idempotence and Recovery

This refactor is source-only plus tests. Re-running the edits is safe because the helpers are pure and the validation steps are read-only. If a regression appears, verify the new direct direction-helper tests first because they isolate the hotspot behavior more quickly than the full view render tests. If coverage generation fails after the functional gates pass, rerun only `npm run coverage` and the `bb tools/crap_report.clj ...` command once the environment is stable.

## Artifacts and Notes

Issue tracking:

- `bd` issue `hyperopen-0599`: "Reduce CRAP in order history direction helpers"

User-reported hotspot context:

- `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs` currently reports `CRAP 143.36`, `max_crap=173.36`, and `crappy_functions=1`.

Post-implementation validation from `/hyperopen`:

- `npx shadow-cljs compile test` passed.
- `npm ci` completed successfully and restored missing workspace dependencies.
- `npm run check` passed.
- `npm test` passed with `Ran 2383 tests containing 12507 assertions. 0 failures, 0 errors.`
- `npm run test:websocket` passed with `Ran 385 tests containing 2187 assertions. 0 failures, 0 errors.`
- `npm run coverage` passed with `Statements 90.61%`, `Branches 68.07%`, `Functions 85.35%`, and `Lines 90.61%`.
- `bb tools/crap_report.clj --module src/hyperopen/views/account_info/tabs/order_history.cljs --format json` reported `crappy-functions = 0`, `project-crapload = 0.0`, and `max-crap = 12.0`.

## Interfaces and Dependencies

This change must preserve the current view-facing entrypoints re-exported by `/hyperopen/src/hyperopen/views/account_info_view.cljs`, including `order-history-tab-content`, `order-history-table`, and the formatting helpers already used in tests.

The refactor may introduce new file-local or namespace-private helpers inside `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs`, but it must not change the public API of `/hyperopen/src/hyperopen/views/account_info_view.cljs` or alter the order-history state shape consumed by callers.

Plan revision note: 2026-03-14 01:26Z - Initial active ExecPlan created after claiming `hyperopen-0599`, auditing the direction hotspot, and confirming the required validation/CRAP workflow.
Plan revision note: 2026-03-14 01:38Z - Recorded the dependency recovery, the follow-up sorter extraction triggered by the first CRAP rerun, the final passing validation commands, and the zero-hotspot module CRAP result.
