# Incremental Chart Sync and Identity-Gated Overlay Updates

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The trading chart update path currently re-sends full candle datasets to Lightweight Charts and rebuilds open-order overlay DOM on most updates, even when inputs are unchanged or only the tail candle changed. After this change, chart updates should short-circuit when data identity is unchanged, and should use incremental series updates (`.update`) for append/update-last-candle cases instead of full dataset resets (`.setData`) where safe.

Users should experience lower UI work during live updates, especially when the feed updates the current candle frequently and when open-order overlays are unchanged.

## Progress

- [x] (2026-02-18 00:59Z) Reviewed planning/runtime UI policy docs and located hotspot call-sites in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`, `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`, and `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs`.
- [x] (2026-02-18 00:59Z) Created this active ExecPlan.
- [x] (2026-02-18 01:03Z) Implemented main+volume series identity gating and incremental sync in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` with sidecar-based mode inference (`:noop`, `:append-last`, `:update-last`, `:full-reset`).
- [x] (2026-02-18 01:03Z) Implemented marker/overlay identity gating in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/markers.cljs` and `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs`.
- [x] (2026-02-18 01:03Z) Stabilized overlay formatter dependency identities in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` by replacing inline closure with a dedicated function.
- [x] (2026-02-18 01:04Z) Added regression coverage in `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop_test.cljs` for identity no-op and incremental update paths.
- [x] (2026-02-18 01:06Z) Ran required validation gates: `npm run check`, `npm test`, `npm run test:websocket` (all passed).
- [x] (2026-02-18 01:06Z) Moved this plan to `/hyperopen/docs/exec-plans/completed/` after acceptance criteria passed.

## Surprises & Discoveries

- Observation: `chart-canvas` currently creates a new inline `format-size` function each render, which prevents identity-based short-circuiting in overlay sync unless this dependency is stabilized.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` `overlay-deps` map currently uses `(fn [value] ...)` inside render scope.
- Observation: Marker and overlay interop already use `WeakMap` sidecars, so identity-gating can be added without changing public chart interop interfaces.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/markers.cljs` and `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs`.
- Observation: The first implementation pass introduced an unmatched delimiter in the markers module and was caught immediately by the required compile gate.
  Evidence: `npm run check` failure output (`Unmatched delimiter )` in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/markers.cljs:38`), followed by successful rerun.

## Decision Log

- Decision: Implement incremental append/update-last-candle detection from raw candle vectors and keep full reset as fallback for any non-tail mutation.
  Rationale: This preserves correctness for backfills/rewrites while reducing hot-path `setData` payload pushes.
  Date/Author: 2026-02-18 / Codex
- Decision: Keep changes behind existing interop function names (`set-series-data!`, `set-volume-data!`, `set-main-series-markers!`, `sync-open-order-overlays!`) instead of adding a new public chart API.
  Rationale: Preserves existing call sites and minimizes refactor surface.
  Date/Author: 2026-02-18 / Codex
- Decision: Gate overlay rerenders by stable dependency identities (`orders`, formatter functions, cancel callback, chart/series handles) and make chart-core size formatter a stable function.
  Rationale: Overlay sync is called from chart lifecycle updates; stable function identity is required for `identical?`-based no-op behavior.
  Date/Author: 2026-02-18 / Codex

## Outcomes & Retrospective

Implemented and validated end-to-end.

What changed:

- Added per-series sidecar sync state in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` to avoid redundant `.setData` calls and emit incremental `.update` calls for append/update-last-candle modes where safe.
- Added marker identity gating in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/markers.cljs` to skip redundant marker plugin writes.
- Added overlay identity gating in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs` to skip rerender work when overlay inputs are unchanged.
- Replaced inline overlay size formatter closure with stable function `format-chart-overlay-size` in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` to make overlay identity gating effective.
- Extended `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop_test.cljs` with incremental/no-op regression tests for series, volume, markers, and overlays.

Validation evidence:

- `npm run check`: pass.
- `npm test`: pass (`1087` tests, `4935` assertions, `0` failures, `0` errors).
- `npm run test:websocket`: pass (`124` tests, `510` assertions, `0` failures, `0` errors).

## Context and Orientation

The chart view update lifecycle is in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` (`chart-canvas` mount/update/unmount). The JS interop boundary is `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`, which writes series data (`set-series-data!`, `set-volume-data!`), marker updates (`set-main-series-markers!`), and open-order overlay updates (`sync-open-order-overlays!`).

In this plan:

- “Identity gate” means skip update work when the upstream input reference is unchanged (`identical?`), so no JS API call or DOM rebuild occurs.
- “Incremental update” means calling Lightweight Charts `.update` with a single transformed point when the next candle vector is either an append of one candle or an in-place update of the last candle time bucket.
- “Full reset” means calling `.setData` with the whole transformed dataset.

## Plan of Work

Milestone 1 updates chart interop series sync internals to maintain per-series sidecar state. The sync function will infer an update mode (`:noop`, `:append-last`, `:update-last`, `:full-reset`) by comparing prior and next raw candle vectors. It will apply options only when needed and emit `.update` for incremental modes.

Milestone 2 adds identity-gated updates for marker plugin writes and open-order overlay sync. Overlay sync will avoid rerendering when chart handle, series handle, order reference, and formatter/cancel callbacks are unchanged.

Milestone 3 stabilizes overlay dependency function identities in chart core so overlay identity-gating can activate during normal rerenders.

Milestone 4 adds regression tests for incremental series updates, no-op identity paths, marker no-op behavior, and overlay no-op behavior. Then run all required validation gates.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` to add:
   - Per-series WeakMap sidecar state.
   - Incremental mode detection for candle vectors.
   - No-op skip on identical source data when configuration unchanged.
   - Incremental `.update` path for main series and volume series.
2. Edit `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/markers.cljs` to skip `.setMarkers` when marker vector identity is unchanged for the active plugin/series.
3. Edit `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs` to skip `render-overlays!` when overlay inputs are unchanged.
4. Edit `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` to replace inline overlay size formatter closure with a stable function reference.
5. Extend `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop_test.cljs` with focused regression tests for the new sync behavior.
6. Run:

       npm run check
       npm test
       npm run test:websocket

## Validation and Acceptance

Acceptance criteria:

- Repeated `set-series-data!` calls with identical candle vector identity and unchanged chart configuration do not call `.setData` or `.update`.
- Tail-only candle updates (same count, same prefix, same last `:time`) call `.update` with a single transformed point.
- Single-candle append updates (prefix unchanged, count +1) call `.update` with the appended transformed point.
- Non-tail edits or shape changes still fall back to `.setData` full reset.
- Repeated marker sync with identical marker vector identity does not call plugin `.setMarkers` again.
- Repeated overlay sync with unchanged identity inputs does not rerender overlay rows.
- Required validation gates pass.

## Idempotence and Recovery

The implementation is source-only and idempotent. If incremental detection misclassifies a case, recovery is safe: fallback to full reset by simplifying the mode resolver to always return `:full-reset`. No migrations, persisted schema changes, or destructive operations are involved.

## Artifacts and Notes

Planned files:

- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/markers.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`
- `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop_test.cljs`
- `/hyperopen/docs/exec-plans/completed/2026-02-18-chart-update-incremental-sync-and-identity-gates.md`

## Interfaces and Dependencies

Existing interfaces to preserve:

- `hyperopen.views.trading-chart.utils.chart-interop/set-series-data!`
- `hyperopen.views.trading-chart.utils.chart-interop/set-volume-data!`
- `hyperopen.views.trading-chart.utils.chart-interop/set-main-series-markers!`
- `hyperopen.views.trading-chart.utils.chart-interop/sync-open-order-overlays!`
- `hyperopen.views.trading-chart.core/chart-canvas`

Dependencies and assumptions:

- Lightweight Charts series support `.setData` and `.update`.
- Candle vectors are time-ordered as produced by existing candle preprocessing.
- Overlay rendering remains chart-local sidecar state keyed by chart handle.

Plan revision note: 2026-02-18 00:59Z - Initial plan created for chart data/overlay performance refactor with identity-gated and incremental update goals.
Plan revision note: 2026-02-18 01:06Z - Updated after implementation and validation gates; captured syntax-fix discovery, final outcomes, and completed-plan destination.
