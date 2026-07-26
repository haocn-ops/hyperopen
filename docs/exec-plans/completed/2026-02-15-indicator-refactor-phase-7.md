# Refactor Trading Chart Indicators (Phase 7: Wave3 Oscillator Family Migration)

This ExecPlan record is maintained in accordance with `/hyperopen/.agents/PLANS.md` and captures a completed migration slice.

## Purpose / Big Picture

Migrate another wave3 calculator family into semantic domain ownership by moving selected oscillator implementations into `hyperopen.domain.trading.indicators.oscillators`, externalizing their presentation metadata in the adapter, and removing duplicate wave3 implementations after parity validation.

## Progress

- [x] (2026-02-15 15:00Z) Added domain oscillator calculators for `:coppock-curve`, `:fisher-transform`, `:majority-rule`, `:ratio`, and `:spread` in `src/hyperopen/domain/trading/indicators/oscillators.cljs`.
- [x] (2026-02-15 15:00Z) Added view adapter line-series metadata for newly migrated IDs/series in `src/hyperopen/views/trading_chart/utils/indicator_view_adapter.cljs`.
- [x] (2026-02-15 15:01Z) Removed migrated definitions, calculator functions, helper, and dispatch entries from `src/hyperopen/views/trading_chart/utils/indicators_wave3.cljs`.
- [x] (2026-02-15 15:03Z) Ran required validation gates (`npm run check`, `npm test`, `npm run test:websocket`) with zero failures.

## Surprises & Discoveries

- Observation: Parity for these wave3 oscillators depends on aligned rolling windows, while existing oscillator helpers default to lagged windows.
  Evidence: Introduced explicit aligned helper wrappers in domain oscillators and validation remained green.

## Decision Log

- Decision: Keep existing lagged helper behavior in domain oscillators for previously migrated indicators and add explicit aligned helpers only for wave3-parity calculators.
  Rationale: Avoid behavior regressions in already migrated indicators while preserving wave3 output parity for this family.
  Date/Author: 2026-02-15 / Codex

## Outcomes & Retrospective

A second wave3 family is now domain-owned and removed from wave3 fallback code. This advances semantic extraction and shrinks legacy wave3 scope without changing public indicator APIs.

## Context and Orientation

Files changed in this slice:

- `/hyperopen/src/hyperopen/domain/trading/indicators/oscillators.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicator_view_adapter.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave3.cljs`

Coordinator behavior remains domain-first in `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs`, so removed wave3 IDs resolve through domain calculators.

## Validation and Acceptance

Validation commands run from `/hyperopen`:

- `npm run check`
- `npm test`
- `npm run test:websocket`

Acceptance criteria met:

- Migrated IDs removed from wave3 definitions and calculator map.
- Domain oscillator calculators return these IDs through the registry path.
- Adapter metadata provides names/colors for migrated series.
- Required validation gates pass.

## Idempotence and Recovery

This slice is safe to re-validate repeatedly; no destructive migrations are involved. Recovery path is limited to restoring removed wave3 entries if a future regression is discovered.

## Interfaces and Dependencies

Public entry points remain unchanged:

- `hyperopen.views.trading-chart.utils.indicators/get-available-indicators`
- `hyperopen.views.trading-chart.utils.indicators/calculate-indicator`

Plan revision note: 2026-02-15 15:03Z - Completed phase-7 record created after parity validation and wave3 oscillator family removal.
