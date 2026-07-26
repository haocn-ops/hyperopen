# Chart Position Overlay Entry Fill Indexing

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The chart position overlay currently sorts matching fills on every overlay build to find the latest entry transition fill. That sorting work runs in the render-triggered path and costs `O(n log n)` for each update. After this change, the overlay model should derive the latest entry fill in a single pass with no sort, so repeated chart updates do less work while preserving existing overlay behavior (entry marker timing, long/short side selection, and liquidation/PNL values).

A developer can verify the behavior by running the chart overlay tests and repository quality gates. The chart should continue to display the same entry marker semantics for long and short positions while removing the per-build sort hotspot.

## Progress

- [x] (2026-03-04 03:40Z) Claimed `hyperopen-ou6` and validated that `main` still uses `sort-by fill-time` inside `latest-entry-fill`.
- [x] (2026-03-04 03:41Z) Audited planning requirements and created this ExecPlan in `/hyperopen/docs/exec-plans/active/`.
- [x] (2026-03-04 03:42Z) Replaced sort-based entry fill selection in `/hyperopen/src/hyperopen/views/trading_chart/utils/position_overlay_model.cljs` with a single-pass asset/side index.
- [x] (2026-03-04 03:42Z) Extended `/hyperopen/test/hyperopen/views/trading_chart/utils/position_overlay_model_test.cljs` coverage with unsorted fill ordering for short-side transition selection.
- [x] (2026-03-04 03:43Z) Ran required validation gates: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-03-04 03:44Z) Updated `bd` issue status and moved this plan to `/hyperopen/docs/exec-plans/completed/` after acceptance passed.

## Surprises & Discoveries

- Observation: Repository runs in a detached `HEAD` worktree for this task.
  Evidence: `git status --short --branch` reported `## HEAD (no branch)`.

- Observation: Initial test attempt failed because `shadow-cljs` was unavailable before dependency install.
  Evidence: `npm test -- position-overlay-model-test` exited with `sh: shadow-cljs: command not found`; running `npm ci` installed dependencies and subsequent gates passed.

- Observation: Passing a namespace filter argument to `npm test` in this repo still runs the full generated test runner.
  Evidence: `npm test -- position-overlay-model-test` printed `Unknown arg: position-overlay-model-test` and then executed the full suite.

## Decision Log

- Decision: Keep scope narrow to the issue requirement by removing sort work from the existing overlay derivation path, without changing public chart interop interfaces.
  Rationale: `hyperopen-ou6` targets an identified render-path hotspot, and the lowest-risk fix is to keep output shape stable while reducing algorithmic cost.
  Date/Author: 2026-03-04 / Codex

- Decision: Track latest entry fills by side in a single reducer pass over asset-matching fills instead of pre-filter+sort pipelines.
  Rationale: This removes `O(n log n)` sort work and avoids allocating intermediate sorted sequences while preserving side-specific latest-fill selection semantics.
  Date/Author: 2026-03-04 / Codex

- Decision: Only prefer fills with finite timestamps when selecting “latest” transition rows.
  Rationale: Entry marker alignment requires a valid timestamp; rows without parseable time cannot produce marker coordinates and should not displace valid timed entries.
  Date/Author: 2026-03-04 / Codex

## Outcomes & Retrospective

Implemented and validated.

What changed:

- Replaced `latest-entry-fill` sort pipeline with new helpers in `/hyperopen/src/hyperopen/views/trading_chart/utils/position_overlay_model.cljs`:
  - `entry-transition-direction` computes transition side once per fill.
  - `prefer-later-fill` selects the latest timed fill without sorting.
  - `latest-entry-fills-for-asset` scans fills once and pre-indexes latest entry fills by `:long`/`:short`.
- Updated `build-position-overlay` to read `entry-fill` from the pre-indexed per-side map instead of sorting filtered fills.
- Updated short-side test in `/hyperopen/test/hyperopen/views/trading_chart/utils/position_overlay_model_test.cljs` to use unsorted fills, including a later non-transition fill, and still assert latest valid transition time is selected.

Validation evidence:

- `npm run check`: pass (lint/docs/compile checks complete with no failures).
- `npm test`: pass (`1831` tests, `9503` assertions, `0` failures, `0` errors).
- `npm run test:websocket`: pass (`290` tests, `1662` assertions, `0` failures, `0` errors).

## Context and Orientation

The relevant overlay derivation logic is in `/hyperopen/src/hyperopen/views/trading_chart/utils/position_overlay_model.cljs`. The function `build-position-overlay` derives entry marker data from active position plus fills. The current helper `latest-entry-fill` sorts fills by time before selecting the last entry transition. This function is called during chart model construction in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` and therefore executes frequently while the chart updates.

The existing tests are in `/hyperopen/test/hyperopen/views/trading_chart/utils/position_overlay_model_test.cljs`. They cover long and short entry inference, marker omission when fill data is missing, and invalid position guards.

In this plan, “entry transition fill” means a fill that crosses position through zero into the current open side (or explicitly indicates open-long/open-short via the fill `:dir` text).

## Plan of Work

Milestone 1 updates `position_overlay_model.cljs` to remove sort-driven latest-fill lookup. Instead of building an intermediate sorted sequence, we will iterate fills once, keep only rows matching the active asset, and track the latest entry-transition fill per direction (`:long` and `:short`) by timestamp. The overlay builder will read the fill for the active side from this pre-indexed map.

Milestone 2 extends tests to cover unsorted fill input and confirm that entry marker selection remains correct for the latest transition fill. Existing tests should continue to pass unchanged.

Milestone 3 runs all mandatory validation gates and records outcomes in this plan before moving it to the completed plans directory and closing the issue.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/trading_chart/utils/position_overlay_model.cljs`:
   - Remove `latest-entry-fill` sort path.
   - Add helper(s) that compute latest entry fills by side in one pass without sorting.
   - Update `build-position-overlay` to read the pre-indexed fill for the active side.
2. Edit `/hyperopen/test/hyperopen/views/trading_chart/utils/position_overlay_model_test.cljs`:
   - Add or adjust tests with out-of-order fills proving latest transition selection remains correct.
3. Run:

       npm run check
       npm test
       npm run test:websocket

4. Update plan sections (`Progress`, `Surprises & Discoveries`, `Decision Log`, `Outcomes & Retrospective`) with actual results.
5. Move this file to `/hyperopen/docs/exec-plans/completed/` after acceptance is met.

## Validation and Acceptance

Acceptance criteria:

1. `/hyperopen/src/hyperopen/views/trading_chart/utils/position_overlay_model.cljs` no longer uses sort (`sort`, `sort-by`) in the entry fill derivation path.
2. Overlay model still selects latest valid entry transition fill for active side and emits expected marker timing/side fields.
3. New/updated tests prove correctness with unsorted fill order.
4. Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

Edits are source-only and idempotent. Re-running the implementation steps is safe. If the single-pass selection introduces correctness regressions, fallback is to keep the old behavior temporarily by restoring the previous helper in `position_overlay_model.cljs` and rerunning tests before retrying with a corrected reducer.

## Artifacts and Notes

Primary implementation files:

- `/hyperopen/src/hyperopen/views/trading_chart/utils/position_overlay_model.cljs`
- `/hyperopen/test/hyperopen/views/trading_chart/utils/position_overlay_model_test.cljs`
- `/hyperopen/docs/exec-plans/completed/2026-03-04-chart-position-overlay-entry-fill-indexing.md`

Issue tracking anchor:

- `hyperopen-ou6` (`bd`)

## Interfaces and Dependencies

Public interfaces that must remain stable:

- `hyperopen.views.trading-chart.utils.position-overlay-model/build-position-overlay`
- `hyperopen.views.trading-chart.core/trading-chart-view` call contract for position overlay inputs

Dependencies remain unchanged:

- `hyperopen.views.account-info.projections` for numeric/time parsing and coin display resolution
- `hyperopen.utils.interval` for timeframe bucketing

Plan revision note: 2026-03-04 03:41Z - Initial plan authored for `hyperopen-ou6` implementation.
Plan revision note: 2026-03-04 03:44Z - Updated with implementation details, validation outcomes, and final artifact locations.
