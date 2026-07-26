# Memoize Trading Chart Render Derivations

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The trading chart currently recomputes heavy derivations on each render: candle normalization plus sorting in `/hyperopen/src/hyperopen/views/trading_chart/utils/data_processing.cljs` and indicator calculations in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`. After this change, chart derivations will be memoized so they recompute only when chart inputs change (`candles`, `selected-timeframe`, `active-indicators` configuration). Users should observe smoother chart responsiveness during unrelated UI/websocket updates because render-path work is reused.

The user-visible way to confirm behavior is to render the chart repeatedly with unchanged inputs and verify candle transform and indicator calculation functions are not re-invoked, then change timeframe/config/candles and verify recomputation occurs once.

## Progress

- [x] (2026-02-16 02:24Z) Reviewed `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/docs/FRONTEND.md`, and companion UI guides for interaction/runtime constraints.
- [x] (2026-02-16 02:24Z) Confirmed hotspot evidence in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` (`line 114` indicator calculations, `line 245` candle transform) and `/hyperopen/src/hyperopen/views/trading_chart/utils/data_processing.cljs` (`line 7` normalization/sort).
- [x] (2026-02-16 02:24Z) Authored this active ExecPlan with concrete edits, validation gates, and acceptance criteria.
- [x] (2026-02-16 02:25Z) Added `/hyperopen/src/hyperopen/views/trading_chart/derived_cache.cljs` with one-entry memoized candle and indicator derivation helpers plus reset hooks.
- [x] (2026-02-16 02:25Z) Rewired `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` to consume memoized candle transforms and indicator outputs.
- [x] (2026-02-16 02:26Z) Added regression tests in `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs` covering cache-hit and cache-invalidation behavior for candles/timeframe/indicator config inputs.
- [x] (2026-02-16 02:29Z) Ran required validation gates and confirmed pass on final tree: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-02-16 02:29Z) Moved this plan to `/hyperopen/docs/exec-plans/completed/` after acceptance criteria passed.
- [x] (2026-02-16 11:39Z) Investigated post-merge regression report (“chart turns black when adding indicator”), reproduced indicator-enabled mount failure in tests, and traced root cause to chart interop indicator contract mismatch.
- [x] (2026-02-16 11:39Z) Fixed `/hyperopen/src/hyperopen/schema/chart_interop_contracts.cljs` to validate view indicator series with `:data` payloads, matching runtime indicator output shape.
- [x] (2026-02-16 11:39Z) Added `/hyperopen/test/hyperopen/schema/chart_interop_contracts_test.cljs` and wired `/hyperopen/test/test_runner.cljs` to guard indicator contract compatibility with real projected indicator output.
- [x] (2026-02-16 11:39Z) Re-ran required validation gates after regression fix and confirmed pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Surprises & Discoveries

- Observation: indicator derivation and flattening currently live directly in `chart-canvas`, so every render rebuilds indicator output vectors.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` builds `indicators-data-vec`, `indicator-series-data-vec`, and `indicator-marker-data-vec` inside render-time `let` bindings.

- Observation: the repository already uses one-entry identity memoization caches for render-path heavy work.
  Evidence: `/hyperopen/src/hyperopen/views/asset_selector_view.cljs` has `processed-assets-cache`, and `/hyperopen/src/hyperopen/views/account_info/derived_cache.cljs` has identity-keyed memoized helpers plus reset hooks.

- Observation: `(vec some-vector)` returns the same vector identity in ClojureScript, so it does not force cache invalidation in identity-based tests.
  Evidence: initial regression assertions expecting recompute on `(vec existing-vector)` failed until fixtures switched to `(mapv identity existing-vector)`.

- Observation: indicator-enabled chart mount can fail fast in debug builds when interop contracts reject the indicator series shape, which leaves the chart host mounted without a chart handle (“black chart” symptom).
  Evidence: reproduced test failure emitted `indicator contract validation failed ... :series [{... :data [...]}]` from `/hyperopen/src/hyperopen/schema/chart_interop_contracts.cljs` during `create-chart-with-indicators!`.

## Decision Log

- Decision: Use a dedicated chart derivation cache namespace instead of embedding caches directly in `core.cljs`.
  Rationale: keeps `core.cljs` focused on view composition and aligns with existing separation patterns (`derived_cache` helpers).
  Date/Author: 2026-02-16 / Codex

- Decision: Use bounded one-entry caches keyed by input identity plus scalar/map controls (`timeframe`, `active-indicators`) and provide reset hooks for deterministic tests.
  Rationale: prevents unbounded memo growth and matches established project approach.
  Date/Author: 2026-02-16 / Codex

- Decision: Keep identity checks (`identical?`) for candle vectors and candle-data vectors, with `=` checks for timeframe/config controls.
  Rationale: identity checks avoid deep-equality cost on large candle collections while still ensuring required invalidation on timeframe/config changes.
  Date/Author: 2026-02-16 / Codex

- Decision: Align chart interop indicator contract `::series-def` with actual projected indicator series key `:data` instead of a non-existent `:series-data` key.
  Rationale: runtime/view indicator adapters consistently emit `:data`; contract mismatch caused debug-mode mount failures and user-visible blank chart on indicator add.
  Date/Author: 2026-02-16 / Codex

## Outcomes & Retrospective

Implemented. Candle normalization/sorting and indicator calculations are now memoized in a dedicated chart derivation helper, and `core.cljs` render-time work routes through that boundary.

What was achieved:

- Added `/hyperopen/src/hyperopen/views/trading_chart/derived_cache.cljs` with:
  - `memoized-candle-data` keyed by raw-candle identity + timeframe.
  - `memoized-indicator-outputs` keyed by candle-data identity + timeframe + active-indicators config.
  - dynamic collaborator vars and `reset-derived-cache!` for deterministic tests.
- Updated `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`:
  - `trading-chart-view` now calls `memoized-candle-data` instead of direct `process-candle-data`.
  - `chart-canvas` now consumes `memoized-indicator-outputs` instead of inline indicator recomputation/flattening.
- Added regression tests in `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs` that verify:
  - unchanged inputs hit cache,
  - timeframe/config/candle-identity changes invalidate cache once.
- Fixed debug-mode indicator mount regression by updating `/hyperopen/src/hyperopen/schema/chart_interop_contracts.cljs` indicator series contract to accept `:data` payloads used by view indicator adapters.
- Added `/hyperopen/test/hyperopen/schema/chart_interop_contracts_test.cljs` plus `/hyperopen/test/test_runner.cljs` wiring to prevent indicator contract drift.

Validation evidence:

- `npm run check`: pass.
- `npm test`: pass (`910` tests, `4124` assertions, `0` failures, `0` errors).
- `npm run test:websocket`: pass (`116` tests, `458` assertions, `0` failures, `0` errors).

Residual risk:

- Candle derivation invalidation is identity-based for candle vectors. If a future caller frequently allocates equal-but-new candle vectors, recomputation will still occur by design; this trades some potential misses for predictable O(1) cache key checks on hot paths.

## Context and Orientation

The chart view entrypoint is `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` via `trading-chart-view` and `chart-canvas`. In this context, “candle transform” means converting raw API candles (`:t/:o/:h/:l/:c/:v`) into Lightweight Charts rows and sorting by `:time`. “Indicator outputs” means the calculated indicator payloads plus flattened series/marker vectors consumed by chart interop.

Current render-time hotspots:

- `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`: `trading-chart-view` calls `dp/process-candle-data` during render.
- `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`: `chart-canvas` calls `indicators/calculate-indicator` for all active indicators during render.
- `/hyperopen/src/hyperopen/views/trading_chart/utils/data_processing.cljs`: `process-candle-data` performs parse/filter/sort work.

The required behavior is: recompute only when `candles`, `selected-timeframe`, or `active-indicators` config changes.

## Plan of Work

Milestone 1 creates a new helper namespace `/hyperopen/src/hyperopen/views/trading_chart/derived_cache.cljs` with bounded memoized functions for candle transforms and indicator outputs. The helper will store one-entry caches in private atoms and expose a reset function for tests.

Milestone 2 rewires `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` to call memoized helper functions instead of performing raw transform/calculation work inline. Public function signatures (`trading-chart-view`, `chart-canvas`) remain unchanged.

Milestone 3 adds regression coverage in `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs` using dynamic collaborators/counters to assert recomputation boundaries: unchanged inputs hit cache; changing candles/timeframe/indicator config invalidates cache.

Milestone 4 runs required gates and records outputs in this plan, then moves the plan file to completed.

## Concrete Steps

1. Add cache helper namespace.

   Create `/hyperopen/src/hyperopen/views/trading_chart/derived_cache.cljs` with:

   - `memoized-candle-data` keyed by raw-candle identity + timeframe.
   - `memoized-indicator-outputs` keyed by candle-data identity + timeframe + indicator config.
   - `reset-derived-cache!` for tests.
   - Dynamic collaborator vars for test call-count assertions.

2. Rewire chart render path.

   Edit `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`:

   - Replace direct `dp/process-candle-data` call with `derived-cache/memoized-candle-data`.
   - Replace inline indicator calculation/flattening in `chart-canvas` with `derived-cache/memoized-indicator-outputs`.

3. Add regression tests in `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs`.

   - Test candle transform cache hit with unchanged state and cache invalidation on timeframe/raw candle changes.
   - Test indicator cache hit with unchanged inputs and invalidation on timeframe/config/candle changes.

4. Run required validation from `/hyperopen`.

       npm run check
       npm test
       npm run test:websocket

Expected transcript shape:

       ...
       0 failures, 0 errors

## Validation and Acceptance

Acceptance criteria:

- Repeated `trading-chart-view` calls with unchanged `raw-candles` and `selected-timeframe` do not re-run candle transform.
- Repeated `chart-canvas` calls with unchanged `candle-data`, `selected-timeframe`, and `active-indicators` do not re-run indicator calculations.
- Changing any required invalidation input (`candles`, `timeframe`, `active-indicators`) re-runs derivation exactly once per change.
- `npm run check`, `npm test`, and `npm run test:websocket` pass.

## Idempotence and Recovery

This change is source-only and idempotent. Cache helpers are bounded one-entry stores and safe to reset. If regressions appear, rollback path is local: route `core.cljs` back to direct non-memoized calls while keeping added tests to preserve the expected recomputation contract for follow-up fixes.

## Artifacts and Notes

Planned changed paths:

- `/hyperopen/src/hyperopen/views/trading_chart/derived_cache.cljs` (new)
- `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`
- `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs`
- `/hyperopen/src/hyperopen/schema/chart_interop_contracts.cljs`
- `/hyperopen/test/hyperopen/schema/chart_interop_contracts_test.cljs` (new)
- `/hyperopen/test/test_runner.cljs`
- `/hyperopen/docs/exec-plans/completed/2026-02-16-trading-chart-render-pipeline-memoization.md` (this file)

## Interfaces and Dependencies

Stable interfaces to preserve:

- `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` `trading-chart-view`
- `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` `chart-canvas`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/data_processing.cljs` `process-candle-data`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs` `calculate-indicator`

New helper interfaces to add:

- `(memoized-candle-data raw-candles selected-timeframe) => vector`
- `(memoized-indicator-outputs candle-data selected-timeframe active-indicators) => {:indicators-data vector :indicator-series vector :indicator-markers vector}`
- `(reset-derived-cache!) => nil`

Plan revision note: 2026-02-16 02:24Z - Initial plan created from hotspot evidence and repository memoization patterns; selected bounded one-entry cache design to target render-path recomputation.
Plan revision note: 2026-02-16 02:29Z - Updated after implementation with completed milestones, identity-cache test discovery, and final gate evidence.
Plan revision note: 2026-02-16 11:39Z - Reopened to capture and fix post-merge indicator black-chart regression caused by interop contract mismatch; added contract regression coverage and refreshed validation evidence.
