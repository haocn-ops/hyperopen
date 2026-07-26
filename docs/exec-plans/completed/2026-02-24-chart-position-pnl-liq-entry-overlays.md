# Trading Chart Position Overlays: PNL Bar, Entry Marker, and Liquidation Line

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, when a user has an open position on the active asset, the chart will show three position-context overlays that are currently missing in Hyperopen: (1) a PNL bar anchored at the position entry price, (2) an entry circle marker anchored to the bar/time where the currently open position side was entered, and (3) a liquidation price line.

A user can verify this by opening a position, opening the chart for that asset, and seeing: a signed `PNL` overlay with position size, a `L` or `S` entry marker on the corresponding candle/time bucket, and a `Liq. Price` horizontal line when liquidation is defined.

## Progress

- [x] (2026-02-24 21:15Z) Audited existing chart runtime and overlay lifecycle in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` and `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs`.
- [x] (2026-02-24 21:15Z) Verified active position and liquidation source-of-truth shape from Hyperopen selectors (`state/trading.position-for-active-asset`) and live Hyperliquid API responses.
- [x] (2026-02-24 21:15Z) Inspected Hyperliquid current production chart behavior from live bundle (`https://app.hyperliquid.xyz/static/js/main.7a1533d2.js`) and documented target parity constraints.
- [x] (2026-02-24 21:16Z) Authored this ExecPlan.
- [x] (2026-02-24 22:03Z) Implemented pure position-overlay derivation model from active position + fills + timeframe bucketing in `/hyperopen/src/hyperopen/views/trading_chart/utils/position_overlay_model.cljs`.
- [x] (2026-02-24 22:06Z) Implemented chart interop position overlay module and facade wiring, then integrated mount/update/unmount lifecycle in chart core.
- [x] (2026-02-24 22:08Z) Added entry marker merge path so indicator markers and position entry marker render together.
- [x] (2026-02-24 22:12Z) Added tests for model derivation, overlay module behavior, wrapper delegation, and runtime option plumbing.
- [x] (2026-02-24 22:20Z) Ran required validation gates: `npm run check`, `npm test`, `npm run test:websocket`.

## Surprises & Discoveries

- Observation: Hyperliquid’s current chart implementation does not draw these as generic lightweight markers only; it explicitly creates chart order lines for position PNL and liquidation.
  Evidence: production bundle snippet contains `createOrderLine().setLineLength(75).setPrice(entryPx)...setText("PNL ...")` and a second line with `setText("Liq. Price ")`.

- Observation: Hyperliquid’s entry circles come from fill marks rather than from position state directly.
  Evidence: production bundle `getMarks` path requests `type: "userFillsByTime"` and maps each fill to mark labels (`B`/`S`).

- Observation: `clearinghouseState.assetPositions[].position` does not expose entry timestamp.
  Evidence: official SDK `info.py` docs and live payloads include `entryPx`, `unrealizedPnl`, `liquidationPx`, `szi`, but no open-time field.

- Observation: fill rows include `startPosition`, which enables deterministic inference of the latest transition into the currently open side.
  Evidence: official SDK docs for `userFills` / `userFillsByTime` include `startPosition`, `side`, `sz`, and `time`.

## Decision Log

- Decision: Derive entry marker time from fills by selecting the latest transition across zero into the current position direction.
  Rationale: position payload has no open-time field; this method uses available deterministic fields (`startPosition`, `side`, `sz`, `time`) without adding new backend dependencies.
  Date/Author: 2026-02-24 / Codex

- Decision: Implement PNL and liquidation overlays via chart-local DOM sidecar (same pattern as open-order overlays), not app-global state.
  Rationale: overlay geometry is viewport/runtime-dependent (`priceToCoordinate`, `timeToCoordinate`) and should stay at the chart interop boundary.
  Date/Author: 2026-02-24 / Codex

- Decision: Keep public chart APIs stable by extending existing chart runtime options and interop facade, not changing global action/effect contracts.
  Rationale: request is chart parity; this avoids touching trading/signing flows and keeps scope bounded.
  Date/Author: 2026-02-24 / Codex

## Outcomes & Retrospective

Implemented features:

- Position overlay model that derives deterministic chart overlays from active position + fills:
  - entry price and signed/absolute size
  - signed unrealized PNL
  - liquidation price (when positive)
  - entry time inference using latest transition fill into current side
  - timeframe-bucket alignment for marker placement
- Position chart overlays rendered via chart-local DOM sidecar:
  - side-colored PNL bar with PNL + size badge
  - side circle anchor on known entry-time placement with hover `title` timestamp
  - liquidation dashed line with `Liq. Price` badge
- Chart runtime integration:
  - `sync-position-overlays!` on mount/update
  - `clear-position-overlays!` on unmount
  - marker merge so indicator markers are preserved and position entry marker is added

Validation executed successfully:

- `npm run check` (pass)
- `npm test` (pass, 1293 tests / 6084 assertions)
- `npm run test:websocket` (pass, 147 tests / 643 assertions)

Retrospective:

- Entry timestamp is not present in position payloads, so fill-transition inference was required and is now deterministic when fills include `startPosition` and/or direction hints.
- A correctness edge case was discovered during implementation: missing fill time could default to epoch; this was fixed so no synthetic epoch marker is rendered.

## Context and Orientation

The chart rendering pipeline currently lives in:

- `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` (mount/update/unmount orchestration)
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` (interop facade)
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs` (existing overlay lifecycle template)
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/markers.cljs` (main-series marker plugin)

Active position inputs already exist in normalized form via:

- `/hyperopen/src/hyperopen/state/trading.cljs` (`position-for-active-asset`)
- `/hyperopen/src/hyperopen/domain/trading/market.cljs` (position/liquidation summary helpers)

Fill inputs already exist in app state as `:orders :fills` (with fallback `:webdata2 :fills`) and include the fields needed for entry transition inference.

In this plan, “entry marker” means a circle marker associated with the latest fill that transitions into the currently open direction (long/short). “PNL bar” means a horizontal overlay at entry price showing signed unrealized PNL and position size context.

## Plan of Work

Milestone 1 introduces a pure derivation module in the chart view layer that takes active asset, active position, fills, selected timeframe, and candle data and returns a normalized position overlay model:

- side (`:long` / `:short`)
- entry price
- signed size and absolute size
- unrealized PNL
- liquidation price (when positive)
- inferred entry timestamp (time-bucket aligned)
- marker payload for main series

Milestone 2 adds a new chart interop module for position overlays. It creates/updates/cleans chart-local DOM overlays and subscriptions, rendering:

- a side-colored PNL bar with signed PNL and size label
- an optional liquidation line with label
- an optional entry anchor glyph aligned to entry time coordinate on the PNL bar

Milestone 3 wires chart core and interop facade:

- chart runtime options now carry position overlay model
- mount/update call `sync-position-overlays!`
- unmount calls `clear-position-overlays!`
- main-series markers merge existing indicator markers with entry marker

Milestone 4 expands tests for confidence:

- pure model transition logic tests
- interop overlay rendering + teardown tests using fake DOM stubs
- interop facade delegation/guard tests

Milestone 5 runs repository validation gates.

## Concrete Steps

1. Add pure model derivation namespace and tests.

   cd /Users//projects/hyperopen
   npm test -- position-overlay

   Expected result: deterministic tests pass for long/short transition detection and marker generation.

2. Add interop position overlay module and facade wiring.

   cd /Users//projects/hyperopen
   npm test -- chart-interop

   Expected result: overlay render/clear tests pass and no existing interop wrapper regressions.

3. Wire trading chart core lifecycle + marker merge.

   cd /Users//projects/hyperopen
   npm test -- trading-chart

   Expected result: chart core tests pass and runtime options include position overlay inputs.

4. Run required validation gates.

   cd /Users//projects/hyperopen
   npm run check
   npm test
   npm run test:websocket

   Expected result: all commands exit 0.

## Validation and Acceptance

Acceptance is met when all of the following are true:

1. With an open active-asset position, chart shows a PNL overlay line/bar at entry price with signed PNL text.
2. Chart shows an entry circle marker (`L` or `S`) tied to the inferred entry time bucket.
3. Chart shows `Liq. Price` overlay line when liquidation price is present and positive.
4. Existing indicator markers continue to render alongside the new entry marker.
5. Overlay lifecycle is deterministic across mount/update/unmount with no stale DOM leftovers.
6. Required validation gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

All steps are additive source edits and safe to rerun.

If entry-time inference is noisy for edge cases, fallback behavior is to keep PNL/liquidation overlays while omitting entry marker for ambiguous fills. If interop rendering fails, recovery is to disable only the new position overlay sync calls in chart core while preserving existing open-order overlays and markers.

## Artifacts and Notes

Research anchors used for this plan:

- Hyperliquid production bundle: `https://app.hyperliquid.xyz/static/js/main.7a1533d2.js` (position lines and fill marks behavior)
- Hyperliquid SDK docs: `hyperliquid/info.py` and `api/info/userstate.yaml` in official sdk mirror (`tmp/hyperliquid-python-sdk`)
- Live API payload verification from `POST https://api.hyperliquid.xyz/info` for `clearinghouseState` and `userFills`

Primary implementation targets:

- `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` (new)
- `/hyperopen/src/hyperopen/views/trading_chart/utils/position_overlay_model.cljs` (new)
- `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs` (new)
- `/hyperopen/test/hyperopen/views/trading_chart/utils/position_overlay_model_test.cljs` (new)

## Interfaces and Dependencies

No new external dependency is required.

Interfaces expected at completion:

- `hyperopen.views.trading-chart.utils.position-overlay-model/build-position-overlay`
- `hyperopen.views.trading-chart.utils.chart-interop/sync-position-overlays!`
- `hyperopen.views.trading-chart.utils.chart-interop/clear-position-overlays!`

Existing interfaces that must remain stable:

- chart runtime mount/update/unmount in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`
- open-order overlay path and cancel action flow
- indicator marker path via `set-main-series-markers!`

Plan revision note: 2026-02-24 21:16Z - Initial plan authored from live Hyperliquid bundle/API inspection and current Hyperopen chart audit.
