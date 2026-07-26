# Chart Visible-Range Recentering On Asset Switch

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Users should see recent price action immediately when they switch assets in the trade chart. Today, switching from one asset to another can show a stale middle-of-history window (for example BTC showing mid-year candles) or can pin sparse HIP-3 data to the far left with large empty space on the right. After this change, chart defaulting will be recency-first and asset-aware: the latest candle stays in view on switch, and manual pan/zoom memory is preserved only for the same asset + timeframe.

## Progress

- [x] (2026-02-24 12:30Z) Audited current chart viewport lifecycle in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`, `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`, and `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/visible_range_persistence.cljs`.
- [x] (2026-02-24 12:46Z) Reproduced local bug with browser inspection and captured artifacts for BTC mid-history default and SILVER left-pinned sparse view.
- [x] (2026-02-24 12:42Z) Captured HyperLiquid reference behavior for BTC and SILVER showing recency-in-view defaults after symbol switches.
- [x] (2026-02-24 13:05Z) Implemented v2 asset+timeframe visible-range persistence keys and migrated restore/write paths to use v2 keys.
- [x] (2026-02-24 13:11Z) Implemented persisted-range validity checks and recency-first fallback policy (fit content + right anchor for sparse data, recent logical window for large datasets).
- [x] (2026-02-24 13:18Z) Wired chart runtime persistence dependencies with active asset and transformed candles from `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`.
- [x] (2026-02-24 13:27Z) Added and updated regression tests in `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/visible_range_persistence_test.cljs` and `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs`.
- [x] (2026-02-24 13:36Z) Ran required gates successfully: `npm run check`, `npm test`, `npm run test:websocket`.

## Surprises & Discoveries

- Observation: Persisted visible range is keyed only by timeframe (`chart-visible-time-range:<timeframe>`), not by asset, so one asset’s manual zoom/pan leaks into all assets at that timeframe.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/visible_range_persistence.cljs` function `visible-range-storage-key`.

- Observation: Chart creation always fits content first, then applies persisted range; if persisted exists, it overrides fit behavior.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` in `create-chart-with-volume-and-series!` / `create-chart-with-indicators!` and `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` mount lifecycle (`apply-persisted-visible-range!`).

- Observation: Local reproduction had a 1d persisted logical range of `from 38.10` to `to 169.33`, which is plausible for BTC (~331 bars) but invalid for SILVER (~61 bars), causing left-pinned sparse rendering.
  Evidence: browser eval from `/hyperopen/tmp/browser-inspection/inspect-2026-02-24T12-46-05-334Z-8212a1b3/hyperopen-silver-61bars-after-switch/desktop/snapshot.json` and runtime eval logs.

- Observation: `with-redefs` against a multi-arity CLJS function must preserve the arities used by call sites; using only variadic `fn [& args]` can fail with `arity$N is not a function`.
  Evidence: `npm test` failure in `hyperopen.views.trading-chart.core-test` before fixing redefinition in `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs`.

## Decision Log

- Decision: Replace timeframe-only viewport memory with asset+timeframe viewport memory.
  Rationale: This directly removes cross-asset viewport contamination while preserving user intent when revisiting the same market.
  Date/Author: 2026-02-24 / Codex.

- Decision: Apply persisted ranges only when they are valid for the current candle domain; otherwise use a deterministic recency-first default.
  Rationale: Prevents out-of-domain logical ranges from producing blank/left-pinned charts.
  Date/Author: 2026-02-24 / Codex.

- Decision: Legacy timeframe-only keys will not be used for restore once v2 keys exist.
  Rationale: Legacy keys are the bug vector. Keeping them for restore preserves incorrect behavior.
  Date/Author: 2026-02-24 / Codex.

- Decision: For fallback defaults, use a dataset-size split: if candle count is greater than 120, apply a recent logical window (`last 120 + right offset`); otherwise keep full fit and right-anchor to real time.
  Rationale: This matches expected recency behavior for deep histories while avoiding over-zooming sparse HIP-3 histories.
  Date/Author: 2026-02-24 / Codex.

## Outcomes & Retrospective

The implementation now enforces asset-scoped visible-range memory and recency-first defaults on asset switches. The root UX bug is addressed by preventing cross-asset range reuse and by rejecting persisted ranges that do not fit the current candle domain.

Completed outcomes:

- Added v2 storage keying by timeframe + asset in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/visible_range_persistence.cljs`.
- Added persisted-range validation against candle logical/time domains with deterministic fallback behavior.
- Passed asset and candles into persistence deps from `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`.
- Added regression tests for key semantics, invalid-range fallback, large-dataset default windows, and core wiring.
- Passed all required gates.

Remaining gap:

- Browser-level post-change parity screenshots for this exact branch are not yet captured in this pass; behavior is validated by deterministic unit/integration tests and by prior reproduction artifacts.

## Context and Orientation

The trade chart uses Lightweight Charts via ClojureScript interop. The chart mount/update lifecycle lives in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`. Interop helpers live in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`. Visible-range persistence (localStorage read/write and subscriptions) lives in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/visible_range_persistence.cljs`.

When an asset changes, `:actions/select-asset` in `/hyperopen/src/hyperopen/asset_selector/actions.cljs` updates active market state and triggers candle fetch through active-asset subscription effects. The chart remount key includes symbol/timeframe, so each asset switch creates a fresh chart instance, then restore logic re-applies persisted visible range.

A “logical range” means Lightweight Charts index-space coordinates (`from`/`to` bar positions), not absolute timestamps. Logical ranges are not portable between datasets with very different lengths unless validated and normalized.

## Plan of Work

Milestone 1 introduces a v2 storage key model in `visible_range_persistence.cljs` using both asset and timeframe. New key shape: `chart-visible-time-range:v2:<timeframe>:<asset-token>`, where `<asset-token>` is a stable encoded asset identifier (`BTC`, `xyz:SILVER`, etc.). Existing writes move to v2 keys. Restore reads only v2 by default.

Milestone 2 adds a viewport policy layer in `visible_range_persistence.cljs` and exposes it via `chart_interop.cljs`. Restore will validate persisted logical/time ranges against current candle data boundaries and range width. If invalid or missing, it will apply recency-first defaults (`fitContent` then right-anchor/real-time scroll, plus deterministic right offset) so newest candles are visible immediately.

Milestone 3 wires asset-aware persistence dependencies from `core.cljs`: pass `active-asset`, timeframe, and candle metadata into restore/subscribe calls. Keep existing responsiveness invariant: state projection remains immediate; heavy work stays in chart interop lifecycle.

Milestone 4 expands tests: update storage-key expectations in `visible_range_persistence_test.cljs`, add invalid-range fallback tests, add cross-asset isolation tests, and add chart-core lifecycle tests to confirm the correct policy path is called on asset switch.

## Concrete Steps

From `/hyperopen`:

1. Update persistence keying and validation.
   Edit:
   `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/visible_range_persistence.cljs`

2. Update interop wrappers and optionally add a dedicated defaulting helper.
   Edit:
   `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`

3. Pass active asset and data context into persistence dependencies.
   Edit:
   `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`

4. Update/add tests.
   Edit:
   `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/visible_range_persistence_test.cljs`
   `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop_test.cljs`
   `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs`

5. Run required gates.

   cd /hyperopen
   npm run check
   npm test
   npm run test:websocket

## Validation and Acceptance

Acceptance is behavioral and must be verified in browser inspection plus tests:

1. Switch `BTC-USDC` -> `SILVER-USDC` on `1d` in Hyperopen:
   latest SILVER candle is visible by default, chart is not left-pinned with large empty future space.

2. Switch `SILVER-USDC` -> `BTC-USDC` on `1d` in Hyperopen:
   chart opens with recent BTC candles in view; it does not default to a stale middle-of-history slice.

3. Asset-scoped memory:
   manual pan/zoom on BTC `1d` is restored when returning to BTC `1d`, but does not affect SILVER `1d`.

4. Storage assertions:
   v2 keys are written per asset+timeframe, and legacy timeframe-only keys are not used for restore.

5. Regression suite:
   all required gates pass.

## Idempotence and Recovery

These changes are additive and safe to re-run. If behavior regresses, disable restore path by forcing recency fallback in `apply-persisted-visible-range!` while preserving writes, then re-enable after tests pass. If needed, clear stale local keys beginning with `chart-visible-time-range` to recover deterministic defaults during QA.

## Artifacts and Notes

Local bug captures:

- `/hyperopen/tmp/browser-inspection/inspect-2026-02-24T12-45-02-808Z-592b7d79/hyperopen-btc-after-switch-back/desktop/screenshot.png`
- `/hyperopen/tmp/browser-inspection/inspect-2026-02-24T12-46-05-334Z-8212a1b3/hyperopen-silver-61bars-after-switch/desktop/screenshot.png`

HyperLiquid reference captures:

- `/hyperopen/tmp/browser-inspection/inspect-2026-02-24T12-37-04-105Z-03a204c3/hyperliquid-btc-after-switch/desktop/screenshot.png`
- `/hyperopen/tmp/browser-inspection/inspect-2026-02-24T12-42-33-486Z-9c550181/hyperliquid-silver-confirmed/desktop/screenshot.png`

## Interfaces and Dependencies

Public interfaces should remain stable. Prefer extending existing functions with optional context maps:

- `hyperopen.views.trading-chart.utils.chart-interop/apply-persisted-visible-range!`
  Add optional keys: `:asset`, `:candles`, and validation/default-policy deps.

- `hyperopen.views.trading-chart.utils.chart-interop/subscribe-visible-range-persistence!`
  Add optional key: `:asset` for v2 storage key selection.

- `hyperopen.views.trading-chart.utils.chart-interop.visible-range-persistence`
  Add helpers for:
  key generation (`asset + timeframe`),
  range validation against candle domain,
  recency-first fallback application.

Maintain deterministic behavior and keep side effects at interop/infrastructure boundaries only.

Plan revision note: 2026-02-24 12:47Z - Initial plan created after live browser parity inspection and local bug reproduction, with implementation milestones and acceptance criteria.
Plan revision note: 2026-02-24 13:36Z - Updated progress, discoveries, decisions, and retrospective after implementation completion and passing required validation gates.
