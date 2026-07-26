# Implement Wave 1 Trading Indicator Parity in the Trading Chart

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and is intended to be sufficient for a first-time contributor with only the current working tree.

## Purpose / Big Picture

After this change, users can add a first wave of missing indicators from the chart indicator dropdown and immediately see them rendered on the chart, including multi-line indicators like Bollinger Bands and two-line indicators like Aroon and 52 Week High/Low. This expands parity with the indicators listed in the Hyperliquid-style menu screenshot and provides a reusable indicator architecture for future additions.

A user should be able to open the chart indicator menu, search and add any indicator in this wave, and see calculated lines or histograms update as candle data streams in.

## Progress

- [x] (2026-02-14 21:42Z) Audited the current indicator architecture in `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs`, `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`, and `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`.
- [x] (2026-02-14 21:42Z) Collected TradingView reference formulas/defaults for ADL, ALMA, Aroon, ADX/DMI, ATR, AO, BOP, and Bollinger Bands.
- [x] (2026-02-14 21:43Z) Created this ExecPlan in `/hyperopen/docs/exec-plans/active/2026-02-14-indicator-parity-wave-1.md`.
- [x] (2026-02-14 21:47Z) Extended indicator calculation output to normalized multi-series structures with deterministic ordering in `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs`.
- [x] (2026-02-14 21:48Z) Implemented wave 1 indicator metadata and formulas, including multi-line/histogram indicators and pane metadata.
- [x] (2026-02-14 21:49Z) Updated dropdown behavior in `/hyperopen/src/hyperopen/views/trading_chart/indicators_dropdown.cljs` so period controls only render for period-based indicators and add actions use indicator-specific default config maps.
- [x] (2026-02-14 21:49Z) Updated chart rendering/runtime plumbing in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` and `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` to support flattened multi-series indicator updates and per-indicator panes.
- [x] (2026-02-14 21:49Z) Replaced indicator utility tests with wave 1 coverage in `/hyperopen/test/hyperopen/views/trading_chart/utils/indicators_test.cljs`.
- [x] (2026-02-14 21:50Z) Passed required gates: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-02-14 21:51Z) Moved this ExecPlan to `/hyperopen/docs/exec-plans/completed/` after final write-up.

## Surprises & Discoveries

- Observation: The current indicator layer returns exactly one line series per indicator and therefore cannot represent Bollinger Bands or Aroon correctly.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs` currently returns one `:data` sequence, and `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` creates one indicator series per indicator.
- Observation: TradingView’s Advance/Decline expects exchange breadth inputs (advancing and declining issues), which are not present in Hyperopen’s current candle payload.
  Evidence: TradingView help page describes A/D as a cumulative breadth series built from advancing minus declining issues, while local candle rows only include OHLCV for one instrument.
- Observation: Rendering period inputs for every active indicator caused mismatched state for periodless indicators after introducing indicator-specific config maps.
  Evidence: Initial compile pass exposed bracket/structure mismatch while gating period input rendering; fixed by limiting period controls to `:supports-period?` metadata.

## Decision Log

- Decision: Implement this as “wave 1 parity” and ship all visible indicators from the provided screenshot list in one pass.
  Rationale: User request explicitly asks to start implementing these indicators and to proceed after planning.
  Date/Author: 2026-02-14 / Codex
- Decision: Represent indicator outputs as normalized series collections so one indicator can render multiple lines/histograms.
  Rationale: This is required for Bollinger Bands, Aroon, and 52 Week High/Low without introducing special-case rendering code for each indicator.
  Date/Author: 2026-02-14 / Codex
- Decision: Implement Advance/Decline as a deterministic single-instrument proxy using bar-to-bar up/down direction when market breadth feed is unavailable.
  Rationale: True breadth data is out of scope for existing APIs; this preserves functional availability in the dropdown while documenting the limitation.
  Date/Author: 2026-02-14 / Codex
- Decision: Assign each non-overlay indicator to its own pane and keep volume in the pane after all indicator panes.
  Rationale: This keeps oscillator scales isolated and avoids overlaying unrelated indicator ranges while preserving existing volume behavior.
  Date/Author: 2026-02-14 / Codex

## Outcomes & Retrospective

Wave 1 indicator parity implementation is complete for the screenshot set. The chart now supports 14 indicators (including the existing moving average) with multi-series output, mixed line/histogram rendering, and separate pane assignment for non-overlay indicators. The indicator dropdown now persists per-indicator config defaults and only exposes period editing where applicable.

Validation gates passed end-to-end:

- `npm run check`
- `npm test`
- `npm run test:websocket`

Remaining gap relative to full TradingView parity is data-source fidelity for Advance/Decline, which currently uses a documented single-instrument proxy because exchange breadth feeds are not available in current runtime data.

## Context and Orientation

The chart indicator pipeline currently has three key points:

1. `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs` defines available indicators and computes indicator datapoints from candle rows.
2. `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` reads active indicator configs from state, calls indicator calculation, and hands resulting series to chart interop.
3. `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` creates Lightweight Charts series and applies data updates.

The existing implementation supports one indicator (`:sma`) and assumes one line series per indicator. To support the wave 1 set, indicator calculations must return a collection of renderable series with explicit `:series-type` metadata and deterministic ordering.

In this repository, a “candle row” is a map with at least `:time`, `:open`, `:high`, `:low`, `:close`, and `:volume`. Indicator output points must remain Lightweight Charts-compatible maps with `:time` and optional `:value` to preserve whitespace behavior during warmup.

## Plan of Work

First, rework indicator utilities in `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs` so `calculate-indicator` returns a normalized map such as `{:type :adx :series [{:series-type :line :data ...}]}` and each indicator can contribute one or more plotted series. Keep indicator ordering deterministic by sorting active indicator keys before calculation.

Second, update `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` and `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` to create and update indicator series from this normalized multi-series structure. Add support for `:line` and `:histogram` indicator series and preserve volume pane behavior.

Third, expand `get-available-indicators` metadata and dropdown rendering in `/hyperopen/src/hyperopen/views/trading_chart/indicators_dropdown.cljs` so indicators that do not use length settings do not show a period input in the active list.

Fourth, add deterministic unit coverage in `/hyperopen/test/hyperopen/views/trading_chart/utils/indicators_test.cljs` for representative outputs across the new formulas, and adjust view tests that assume single-indicator availability.

Finally, run repository-required validation gates and move this plan to completed with observed outcomes.

## Concrete Steps

All commands are run from `/Users//projects/hyperopen`.

1. Implement multi-series indicator data model and formulas.

    rg -n "defn get-available-indicators|defn calculate-indicator" src/hyperopen/views/trading_chart/utils/indicators.cljs

2. Wire multi-series creation/update through chart core and interop.

    rg -n "create-chart-with-indicators!|set-indicator-data!|indicatorSeries" src/hyperopen/views/trading_chart/core.cljs src/hyperopen/views/trading_chart/utils/chart_interop.cljs

3. Update tests for indicator metadata and calculations.

    npm test -- --help
    npm test

4. Run required validation gates.

    npm run check
    npm test
    npm run test:websocket

## Validation and Acceptance

Acceptance is complete when:

- The indicator dropdown includes the wave 1 indicators and users can add/remove each one.
- Indicators with multiple lines (for example Bollinger Bands and Aroon) render all lines correctly.
- Histogram indicators (Awesome Oscillator and Accelerator Oscillator) render as histogram series.
- Indicators continue updating on chart refresh and websocket candle updates without runtime errors.
- Required gates pass: `npm run check`, `npm test`, and `npm run test:websocket`.

Manual verification flow:

1. Start the app with `npm run dev`.
2. Open the trading chart and indicator dropdown.
3. Add `Bollinger Bands`, `Aroon`, and `Awesome Oscillator`.
4. Confirm lines/histograms appear and move when switching timeframes.
5. Remove indicators and verify chart remains stable.

## Idempotence and Recovery

The changes are additive and local to chart indicator modules and tests. Re-running the listed commands is safe. If a new indicator formula causes render issues, recovery is to remove the specific indicator from `get-available-indicators` and its `calculate-indicator` case while preserving the normalized multi-series plumbing.

## Artifacts and Notes

TradingView reference pages used for formula/default alignment include:

- ADL: https://www.tradingview.com/support/solutions/43000501770-accumulation-distribution-adl/
- ALMA: https://www.tradingview.com/support/solutions/43000594683-arnaud-legoux-moving-average/
- Aroon: https://www.tradingview.com/support/solutions/43000501801-aroon/
- ADX and DMI math: https://www.tradingview.com/support/solutions/43000589099-average-directional-index-adx/ and https://www.tradingview.com/support/solutions/43000502250-directional-movement-dmi/
- ATR: https://www.tradingview.com/support/solutions/43000501823-average-true-range-atr/
- Awesome Oscillator: https://www.tradingview.com/support/solutions/43000501826-awesome-oscillator-ao/
- Balance of Power: https://www.tradingview.com/support/solutions/43000589100-balance-of-power-bop/
- Bollinger Bands: https://www.tradingview.com/support/solutions/43000501840-bollinger-bands-b-b/

For indicators not covered by that support folder entry list (Accelerator Oscillator, Accumulative Swing Index, Average Price, and 52 Week High/Low), implementations will use established canonical formulas and be documented inline in indicator utility comments for maintainability.

## Interfaces and Dependencies

The implementation must preserve existing public action/event interfaces (`:actions/add-indicator`, `:actions/remove-indicator`, `:actions/update-indicator-period`) and local storage key `chart-active-indicators`.

The chart renderer must continue to rely on Lightweight Charts series APIs via `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` and keep deterministic update behavior in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`.

`/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs` remains the single source of indicator metadata and formulas for the UI dropdown and chart calculations.

Plan revision note: 2026-02-14 21:43Z - Initial plan created before implementation to satisfy required planning workflow and capture formula/repo constraints.
Plan revision note: 2026-02-14 21:50Z - Updated progress, decisions, discoveries, and outcomes after implementation and successful validation gates.
Plan revision note: 2026-02-14 21:51Z - Moved plan from active to completed after all required gates passed.
