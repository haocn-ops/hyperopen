# Implement Wave 3 Indicator Expansion for Trading Chart

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and is self-contained for contributors working only from the current tree.

## Purpose / Big Picture

After this change, the chart indicator list includes the remaining feasible TradingView-style indicators from the user-provided screenshots. A user should be able to open the indicator dropdown, add these indicators, and see deterministic line/histogram output using the existing single-symbol OHLCV candle stream.

This wave focuses on indicators that can be represented as line/histogram time-series. Indicators requiring unsupported geometry (for example volume profile fixed/visible range) are deferred.

## Progress

- [x] (2026-02-14 22:35Z) Audited remaining screenshot indicators against current wave-1 + wave-2 catalog and chart renderer constraints.
- [x] (2026-02-14 22:35Z) Created this wave-3 ExecPlan in `/hyperopen/docs/exec-plans/active/2026-02-14-indicator-parity-wave-3.md`.
- [x] (2026-02-14 22:49Z) Implemented `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave3.cljs` with 34 new indicator definitions and deterministic calculations.
- [x] (2026-02-14 22:49Z) Integrated wave-3 metadata + dispatcher fallback in `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs`.
- [x] (2026-02-14 22:50Z) Extended `/hyperopen/test/hyperopen/views/trading_chart/utils/indicators_test.cljs` for wave-3 availability and representative shape assertions.
- [x] (2026-02-14 22:53Z) Passed required validation gates: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-02-14 22:54Z) Moved plan to `/hyperopen/docs/exec-plans/completed/` with outcomes.

## Surprises & Discoveries

- Observation: The renderer supports `:line` and `:histogram` series in panes but not price-axis profile geometry overlays.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` only allocates line/histogram indicator series.
- Observation: The indicator catalog now reaches 102 entries after wave-3 integration.
  Evidence: `rg '^\\s*\\{:id\\s*:' src/hyperopen/views/trading_chart/utils/indicators.cljs src/hyperopen/views/trading_chart/utils/indicators_wave2.cljs src/hyperopen/views/trading_chart/utils/indicators_wave3.cljs | wc -l` returned `102`.

## Decision Log

- Decision: Implement remaining indicators in a dedicated wave-3 namespace instead of expanding wave-2 further.
  Rationale: keeps file size manageable and preserves maintainability of per-wave changes.
  Date/Author: 2026-02-14 / Codex
- Decision: Provide single-stream proxies for `Ratio` and `Spread` using delayed close values.
  Rationale: chart runtime currently has one symbol stream and no cross-symbol input plumbing.
  Date/Author: 2026-02-14 / Codex
- Decision: Defer only volume profile fixed/visible range due unsupported series geometry.
  Rationale: current renderer supports time-series line/histogram panes only.
  Date/Author: 2026-02-14 / Codex

## Outcomes & Retrospective

Wave 3 is complete. Added 34 new indicators in a dedicated namespace and wired them into the canonical indicator list/dispatcher. Representative wave-3 tests were added for GMMA, pivots, Fisher, SMI Ergodic, volume histogram, standard error bands, Williams Alligator/Fractal, and Zig Zag.

Required validation gates all passed:

- `npm run check`
- `npm test`
- `npm run test:websocket`

Remaining deferral from the screenshot list is limited to volume profile fixed/visible range, which requires renderer capabilities outside current line/histogram support.

## Context and Orientation

Key files:

- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs`: canonical indicator registry and dispatcher.
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave2.cljs`: wave-2 indicator catalog and formulas.
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`: chart series capabilities (`:line` and `:histogram`).
- `/hyperopen/test/hyperopen/views/trading_chart/utils/indicators_test.cljs`: indicator utility tests.

The chart runtime currently receives single-symbol OHLCV candles, so cross-symbol indicators must use explicit single-stream proxies or remain deferred.

## Plan of Work

Create `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave3.cljs` with wave-3 definitions and calculations for remaining single-stream indicators. Prefer deterministic formulas and explicit defaults; return whitespace points (`{:time ...}`) when values are not yet computable.

Update `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs` to append wave-3 indicator metadata to the dropdown list and cascade calculation dispatch from wave-1 -> wave-2 -> wave-3.

Expand tests in `/hyperopen/test/hyperopen/views/trading_chart/utils/indicators_test.cljs` to assert wave-3 indicator availability and representative output shapes for complex multi-series indicators.

## Concrete Steps

All commands run from `/Users//projects/hyperopen`.

1. Add wave-3 namespace and formulas.

    rg -n "defn calculate-wave3-indicator|wave3-indicator-definitions" src/hyperopen/views/trading_chart/utils/indicators_wave3.cljs

2. Wire wave-3 into canonical dispatcher.

    rg -n "indicators-wave3|get-available-indicators|calculate-indicator" src/hyperopen/views/trading_chart/utils/indicators.cljs

3. Update tests.

    rg -n "available-indicators-test|wave3" test/hyperopen/views/trading_chart/utils/indicators_test.cljs

4. Run validation.

    npm run check
    npm test
    npm run test:websocket

## Validation and Acceptance

Acceptance criteria:

- Newly-added wave-3 indicators are present in dropdown metadata.
- Representative wave-3 indicators render expected number/types of series.
- Required validation gates pass.

Manual verification:

1. Run `npm run dev`.
2. Open chart indicator dropdown.
3. Add new wave-3 indicators from each family (volatility, regression, momentum, Williams tools).
4. Confirm indicator series render in overlay/separate panes and update with timeframe changes.

## Idempotence and Recovery

Changes are additive and safe to re-run. If a specific formula path produces invalid data, remove only that indicator from wave-3 definitions and dispatch while retaining the rest.

## Artifacts and Notes

TradingView built-in indicator index referenced for naming and behavior alignment:

- https://www.tradingview.com/support/folders/43000587405-built-in-indicators/

## Interfaces and Dependencies

No public action or state-shape changes are required. Existing actions remain intact:

- `:actions/add-indicator`
- `:actions/remove-indicator`
- `:actions/update-indicator-period`

Wave-3 formulas rely on existing OHLCV candles and deterministic math helpers.

Plan revision note: 2026-02-14 22:35Z - Initial wave-3 plan created.
Plan revision note: 2026-02-14 22:54Z - Updated with completed implementation, validation evidence, and final outcomes.
