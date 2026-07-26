# Split Position Overlays Interop

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. Active-phase local tracker context was `bd` issue `hyperopen-hv9i`.

## Purpose / Big Picture

The trading chart renders position overlays for entry PNL and liquidation price directly on top of Lightweight Charts. Today that behavior lives in one 1,028-line namespace, `src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs`, which owns state storage, DOM creation, inline styles, event listeners, drag lifecycle, repaint subscriptions, formatting, and liquidation-margin calculations. This makes the chart overlay hard to review safely and violates the repository target that namespaces stay below 500 lines unless explicitly excepted.

After this change, a user should see the same `/trade` chart behavior: PNL and liquidation overlays render at the same coordinates, repaint when the chart changes, and dragging the liquidation handle still previews and confirms a margin adjustment. The difference is internal: the implementation is split into focused chart-interoperability namespaces with tests covering the behavior that could regress during the split.

## Progress

- [x] (2026-04-18 12:24Z) Created and claimed `bd` issue `hyperopen-hv9i` for the active refactor.
- [x] (2026-04-18 12:29Z) Read the governing planning, UI, browser-testing, and trading UI docs, plus the current implementation and test suite.
- [x] (2026-04-18 12:29Z) Ran read-only subagents to inspect extraction seams, local patterns, and edge-case coverage gaps.
- [x] (2026-04-18 12:36Z) Added RED-phase regression coverage for liquidation drag math, cancel/teardown behavior, stale listener suppression, and missing pointer-up coordinates.
- [x] (2026-04-18 12:36Z) Implemented the first source slice: created `position-overlays.liquidation-drag`, moved the liquidation drag math/label/anchor helpers into it, and updated the root orchestrator to require that namespace while preserving root public vars.
- [x] (2026-04-18 12:52Z) Extracted remaining pure support and presentation helpers into child namespaces while keeping public root vars stable.
- [x] (2026-04-18 12:52Z) Extracted DOM row creation and patching into `position-overlays.rows` using a drag-start callback to avoid circular dependencies.
- [x] (2026-04-18 12:52Z) Reworked the root namespace into the public orchestrator for sidecar state, subscriptions, drag lifecycle, render, sync, and clear.
- [x] (2026-04-18 12:53Z) Removed the stale 1,028-line namespace-size exception for `position_overlays.cljs`.
- [x] (2026-04-18 12:58Z) Ran required repository gates and browser-QA commands; all passed and browser-inspection cleanup completed.

## Surprises & Discoveries

- Observation: Production callers use the facade in `src/hyperopen/views/trading_chart/utils/chart_interop.cljs`, but the implementation namespace also exposes test-visible vars.
  Evidence: `chart_interop.cljs` delegates only `sync-position-overlays!` and `clear-position-overlays!`, while `position_overlays_test.cljs` redefines `position-overlays/render-overlays!` and the dynamic repaint vars.

- Observation: The existing test suite already asserts in-place DOM patching and text-node retention, which are essential safeguards for this refactor.
  Evidence: `position-overlays-sync-patches-retained-nodes-in-place-test` and `position-overlays-sync-retains-text-nodes-while-patching-text-test` preserve node identity across syncs.

- Observation: The sidecar state is a single `WeakMap` keyed by `chart-obj`; losing that single shared owner would silently break sync, clear, and drag state.
  Evidence: the current `overlay-state`, `set-overlay-state!`, `clear-position-overlays!`, and drag functions all read and write the same `position-overlays-sidecar`.

- Observation: The first RED test slice fails at compile time for the intended reason before implementation.
  Evidence: `npx shadow-cljs --force-spawn compile test` reports `The required namespace "hyperopen.views.trading-chart.utils.chart-interop.position-overlays.liquidation-drag" is not available`, required by `position_overlays_test.cljs`.

- Observation: The first source slice clears the missing-namespace RED failure without changing the test contract.
  Evidence: After adding `position-overlays.liquidation-drag`, `npx shadow-cljs --force-spawn compile test` completed successfully and `node out/test.js` reported `Ran 3256 tests containing 17518 assertions. 0 failures, 0 errors.`

- Observation: The initial `node out/test.js` run after compile was blocked by missing npm dependencies in this worktree.
  Evidence: Node reported `Cannot find module 'lucide/dist/esm/icons/external-link.js'` and `ls node_modules` reported no `node_modules` directory. Running `npm ci` installed the locked dependencies, after which the Node test runner completed successfully.

- Observation: The repository `npm run check` gate is blocked by the frozen RED test file exceeding its namespace-size exception, not by the source extraction.
  Evidence: `npm run check` stops at `lint:namespace-sizes` with `[size-exception-exceeded] test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs - namespace has 823 lines; exception allows at most 679`. The current source slice is not allowed to edit tests.

- Observation: The completed split removes the source namespace size violation without adding new source exceptions.
  Evidence: `wc -l` reports `position_overlays.cljs` 381 lines, `rows.cljs` 335 lines, `presentation.cljs` 151 lines, `liquidation_drag.cljs` 105 lines, and `support.cljs` 95 lines.

- Observation: The new drag tests initially pushed the existing test namespace over its exception limit, so the tests were moved into a focused drag suite.
  Evidence: `position_overlays_test.cljs` is back to 679 lines and `position_overlays_drag_test.cljs` is 199 lines; `npm run lint:namespace-sizes` passes after removing the stale source exception.

- Observation: The default Playwright config could not start because another local process already owned port 8080.
  Evidence: the first focused Playwright run failed with `http://127.0.0.1:8080/ is already used`; `lsof -nP -iTCP:8080 -sTCP:LISTEN` showed a Java process listening. A temporary local config reused the existing app server for Playwright verification and was removed afterward.

- Observation: Final read-only review found no split-specific behavior regression.
  Evidence: the reviewer confirmed single sidecar ownership, stable root public API, symmetric drag listener teardown, passing namespace guardrails, and no tracked temporary artifacts. The reviewer noted that the new child namespaces and focused drag test are intentionally untracked until included in the final change set.

## Decision Log

- Decision: Keep `position_overlays.cljs` as the public orchestration namespace instead of moving the public API to a new namespace.
  Rationale: Production imports and existing tests already target this namespace. A facade preserves API stability and lets the split happen behind the existing chart interop boundary.
  Date/Author: 2026-04-18 / Codex

- Decision: Extract private clusters into child namespaces under `hyperopen.views.trading-chart.utils.chart-interop.position-overlays.*`.
  Rationale: These helpers are chart-view interop code and may depend on view-local contracts. Keeping them under `views/trading_chart` avoids namespace-boundary drift while shrinking the oversized file.
  Date/Author: 2026-04-18 / Codex

- Decision: Keep `render-overlays!`, `*schedule-overlay-repaint-frame!*`, `*cancel-overlay-repaint-frame!*`, `begin-liquidation-drag!`, `sync-position-overlays!`, and `clear-position-overlays!` visible from the root namespace.
  Rationale: Existing tests and callers use these vars directly or through the facade. This refactor should reduce code shape risk without forcing call-site churn.
  Date/Author: 2026-04-18 / Codex

- Decision: Start with tests around liquidation drag and lifecycle teardown before moving production code.
  Rationale: The current worst hotspots are around liquidation row patching, drag startup, and sync/clear lifecycle. Tests for cancel, stale listeners, and direction math make the later extraction safer.
  Date/Author: 2026-04-18 / Codex

- Decision: Split the new drag/liquidation regression tests into `position_overlays_drag_test.cljs` rather than growing `position_overlays_test.cljs`.
  Rationale: This preserves the existing test exception limit while making the new lifecycle coverage easier to scan.
  Date/Author: 2026-04-18 / Codex

## Outcomes & Retrospective

The implementation milestone is complete. The root namespace now orchestrates chart-object state, repaint subscriptions, drag lifecycle, render, sync, and clear. Pure calculations, presentation formatting, and DOM row patching live in child namespaces below the 500-line target. Required repository gates, focused Playwright, smoke Playwright, and governed design QA all pass. Overall complexity is lower because the previous all-in-one interop namespace has been replaced by focused owners without changing the public chart interop API.

## Context and Orientation

`src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` is a browser interop namespace. Browser interop means ClojureScript code that directly calls JavaScript objects such as DOM nodes, `window`, `document`, and the Lightweight Charts API. The namespace renders two overlay rows above the chart: a PNL row at the position entry price and a liquidation row at the liquidation price.

The current root namespace does all of the following:

- stores per-chart mutable sidecar state in a `WeakMap` at line 5;
- owns colors, layout numbers, formatting, and liquidation-margin math near the top of the file;
- creates and patches DOM rows, including a large PNL patcher around line 410 and liquidation patcher around line 568;
- subscribes to chart visible-range, logical-range, size, and data-change events;
- coalesces those subscription events through `requestAnimationFrame`;
- starts and finalizes liquidation drag by adding pointer, mouse, and touch listeners around line 854;
- exposes `render-overlays!`, `clear-position-overlays!`, and `sync-position-overlays!` near the bottom of the file.

The public production boundary is `src/hyperopen/views/trading_chart/utils/chart_interop.cljs`, which delegates `sync-position-overlays!` and `clear-position-overlays!`. The direct test suite is `test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs`. That suite uses `test/hyperopen/views/trading_chart/test_support/fake_dom.cljs` to simulate DOM nodes and events in Node-based ClojureScript tests.

## Plan of Work

First, add focused regression tests for the most fragile behavior that currently has partial coverage. The first tests should cover the liquidation margin direction matrix for long and short positions, pointer cancellation suppressing confirmation and removing listeners, and finalizing a drag with a missing pointer-up coordinate using the last valid preview. These tests belong in `test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs` unless the file is split during the test cleanup milestone.

Second, create child namespaces under `src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays/`:

- `support.cljs` owns shared low-level helpers: finite number checks, parsing, clamping, DOM append/clear helpers, text-node update, method invocation, document resolution, and the single sidecar state store.
- `presentation.cljs` owns colors, badge geometry, visibility checks, PNL tone, and user-facing text formatting.
- `liquidation_drag.cljs` owns pure liquidation-margin calculations, event coordinate helpers, anchor calculation, and drag suggestion labels.
- `rows.cljs` owns overlay root row DOM creation, row hide functions, and row patch functions. It must accept an `on-liquidation-drag-start` callback when creating liquidation row nodes so it does not need to require the root namespace.

Third, turn `position_overlays.cljs` into a narrower orchestrator. It should require the child namespaces, preserve the existing public vars, wire row callbacks to `begin-liquidation-drag!`, keep repaint scheduling and subscription lifecycle in one place, keep drag listener lifecycle in one place, and call the child row/presentation helpers from `render-overlays!`.

Fourth, reduce test-file shape if needed. If `position_overlays_test.cljs` grows materially above its current exception during RED coverage, extract shared fixture helpers into `test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test_support.cljs` and split tests by behavior into lifecycle, rendering, and drag suites. Do this only if it makes the test suite clearer and does not obscure the first implementation slice.

Fifth, update `dev/namespace_size_exceptions.edn`. Remove the `position_overlays.cljs` exception if every resulting source namespace is under 500 lines. If a temporary exception remains necessary, it must be smaller, specific, and justified by remaining work in this plan.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/4212/hyperopen`.

1. Confirm the initial shape:

       wc -l src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs
       rg -n "^(\\(defn|\\(defonce|\\(def \\^|\\(declare)" src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs

2. Add the RED tests using the existing fake DOM fixture. If the first new test directly exercises pure liquidation calculations, expose them through a child namespace as part of implementation rather than using private-var access.

3. Run the smallest practical Node test compile loop:

       npm run test:runner:generate
       npx shadow-cljs --force-spawn compile test
       node out/test.js

   The newly added RED tests should fail before the production extraction if they assert newly exposed pure helper behavior or missing lifecycle cleanup. If an existing behavior already passes, record that in `Surprises & Discoveries` and keep the test as regression coverage.

4. Add child namespaces and move helpers in small batches. After each batch, re-run:

       npx shadow-cljs --force-spawn compile test
       node out/test.js

5. Once the focused suite passes, run namespace checks:

       npm run lint:namespace-sizes
       npm run lint:namespace-boundaries

6. Before completing the plan, run required gates:

       npm run check
       npm test
       npm run test:websocket

7. Because this touches UI-facing chart interop, account for browser QA. Start with the smallest relevant Playwright command, then broaden if it passes:

       playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "trade chart right-click opens the custom context menu"
       npm run test:playwright:smoke
       npm run qa:design-ui -- --targets trade-route --manage-local-app
       npm run browser:cleanup

## Validation and Acceptance

Acceptance is behavior-preserving. The overlay user experience is accepted when the existing and new tests pass, the root implementation namespace is below 500 lines or has a clearly smaller temporary exception, and the `/trade` chart still renders position overlays without layout jumps.

Required validation before closing `hyperopen-hv9i`:

- `npm run check` passes.
- `npm test` passes.
- `npm run test:websocket` passes.
- Namespace size and boundary checks pass with `position_overlays.cljs` no longer carrying the 1,028-line exception.
- Browser QA is reported as `PASS`, `FAIL`, or `BLOCKED` for visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf passes at widths `375`, `768`, `1280`, and `1440`. Any Browser MCP or browser-inspection session created during QA must be cleaned up with `npm run browser:cleanup`.

## Idempotence and Recovery

This refactor is additive before subtractive. Creating child namespaces and changing requires can be repeated safely as long as the root public vars remain stable. If a helper move breaks tests, revert only the current helper move, keep the RED tests, and continue with a smaller extraction. Do not use destructive git commands to recover; use normal patches and `git diff` to inspect partial work.

If browser QA tooling fails for environmental reasons, record the exact command and error in `Surprises & Discoveries`, run `npm run browser:cleanup`, and leave the QA item marked `BLOCKED` rather than implying coverage.

## Artifacts and Notes

Initial size evidence:

       1028 src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs
        679 test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs

RED test evidence:

       npm run test:runner:generate
       Generated test/test_runner_generated.cljs with 491 namespaces.

       npx shadow-cljs --force-spawn compile test
       The required namespace "hyperopen.views.trading-chart.utils.chart-interop.position-overlays.liquidation-drag" is not available, it was required by "hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs".

Current source size evidence:

        381 src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs
        105 src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays/liquidation_drag.cljs
        151 src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays/presentation.cljs
        335 src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays/rows.cljs
         95 src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays/support.cljs
        679 test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs
        199 test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_drag_test.cljs

Focused validation evidence:

       npx shadow-cljs --force-spawn compile test
       Build completed. (1251 files, 4 compiled, 0 warnings, 5.77s)

       node out/test.js
       Ran 3256 tests containing 17518 assertions.
       0 failures, 0 errors.

       npm run lint:namespace-sizes
       Namespace size check passed.

       npm run lint:namespace-boundaries
       Namespace boundary check passed.

Required gate evidence:

       npm run check
       Completed successfully through app, portfolio, worker, vault-detail-worker, and test compilation with 0 warnings.

       npm test
       Ran 3256 tests containing 17518 assertions.
       0 failures, 0 errors.

       npm run test:websocket
       Ran 435 tests containing 2504 assertions.
       0 failures, 0 errors.

Browser verification evidence:

       npx playwright test -c tmp/playwright/reuse-existing.config.mjs tools/playwright/test/trade-regressions.spec.mjs --grep "trade chart right-click opens the custom context menu"
       1 passed.

       npx playwright test -c tmp/playwright/reuse-existing.config.mjs --grep @smoke
       21 passed.

       npm run qa:design-ui -- --targets trade-route --manage-local-app
       reviewOutcome PASS across review widths 375, 768, 1280, and 1440 for visual-evidence-captured, native-control, styling-consistency, interaction, layout-regression, and jank-perf. Residual blind spot: hover, active, disabled, and loading states require targeted route actions when not present by default.

       npm run browser:cleanup
       {"ok": true, "stopped": [], "results": []}

Read-only subagent findings used in this plan:

- Keep root public vars stable because production and tests already reference them.
- Move private helper clusters into `support`, `presentation`, `liquidation-drag`, and `rows`.
- Pass an `on-liquidation-drag-start` callback into row creation to avoid circular namespace dependencies.
- Prioritize tests around drag cancellation, stale listeners, resubscribe behavior, invalid coordinates, offscreen row hiding, and liquidation margin direction math.

## Interfaces and Dependencies

At the end of the refactor, the following source namespaces should exist:

- `hyperopen.views.trading-chart.utils.chart-interop.position-overlays`
- `hyperopen.views.trading-chart.utils.chart-interop.position-overlays.support`
- `hyperopen.views.trading-chart.utils.chart-interop.position-overlays.presentation`
- `hyperopen.views.trading-chart.utils.chart-interop.position-overlays.liquidation-drag`
- `hyperopen.views.trading-chart.utils.chart-interop.position-overlays.rows`

The root namespace must still provide:

- `*schedule-overlay-repaint-frame!*`
- `*cancel-overlay-repaint-frame!*`
- `begin-liquidation-drag!`
- `render-overlays!`
- `clear-position-overlays!`
- `sync-position-overlays!`

The facade namespace `hyperopen.views.trading-chart.utils.chart-interop` must keep the same two public position overlay functions and arities. No caller should need to change to consume this refactor.

Revision note, 2026-04-18: Initial ExecPlan created from local code inspection plus read-only extraction and edge-case test subagent findings. The plan intentionally starts with behavior coverage and a narrow source split to reduce risk in the liquidation drag and repaint lifecycle.

Revision note, 2026-04-18: Recorded the RED test slice and its expected missing-namespace compile failure before implementing the first extraction namespace.

Revision note, 2026-04-18: Completed the first source slice by extracting the liquidation drag helpers into `position-overlays.liquidation-drag`. Remaining split work still needs to move support, presentation, and row DOM helpers.

Revision note, 2026-04-18: Recorded the completed source split, the new focused drag test namespace, stale exception removal, and focused validation evidence.

Revision note, 2026-04-18: Recorded required repository gates, Playwright verification, governed browser-QA results, cleanup evidence, and final outcome.

Revision note, 2026-04-18: Final read-only review completed with no behavior findings; moved the ExecPlan from active to completed after acceptance gates passed.
