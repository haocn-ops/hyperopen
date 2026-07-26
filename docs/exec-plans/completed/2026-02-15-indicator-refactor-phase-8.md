# Refactor Trading Chart Indicators (Phase 8: Wave3 Relative/SMI/UO Family Migration)

This ExecPlan record is maintained in accordance with `/hyperopen/.agents/PLANS.md` and captures a completed migration slice.

## Purpose / Big Picture

Continue family-by-family migration from wave3 into domain oscillators by moving the relative/SMI/ultimate oscillator calculators into `hyperopen.domain.trading.indicators.oscillators`, moving visual metadata into the adapter map, and removing duplicated wave3 implementations once parity is validated.

## Progress

- [x] (2026-02-15 15:05Z) Added domain oscillator calculators for `:relative-vigor-index`, `:relative-volatility-index`, `:smi-ergodic`, and `:ultimate-oscillator` in `src/hyperopen/domain/trading/indicators/oscillators.cljs`.
- [x] (2026-02-15 15:05Z) Added line/histogram adapter metadata for migrated series in `src/hyperopen/views/trading_chart/utils/indicator_view_adapter.cljs`.
- [x] (2026-02-15 15:06Z) Removed migrated definitions, calculators, and now-unused helpers from `src/hyperopen/views/trading_chart/utils/indicators_wave3.cljs`.
- [x] (2026-02-15 15:07Z) Ran required validation gates (`npm run check`, `npm test`, `npm run test:websocket`) with zero failures.

## Surprises & Discoveries

- Observation: Wave3 parity for these indicators also depends on aligned rolling behavior and explicit histogram color policy for `:smi-ergodic`.
  Evidence: Added aligned helper functions and histogram metadata in domain/adapter layers; full suites remained green.

## Decision Log

- Decision: Preserve domain oscillator lagged helpers for existing indicators and introduce aligned helper variants for migrated wave3 parity functions.
  Rationale: This avoids regressions in prior migrations while preserving output alignment for wave3-origin families.
  Date/Author: 2026-02-15 / Codex

## Outcomes & Retrospective

The wave3 fallback file is materially smaller and domain ownership expanded for four additional oscillator IDs. Presentation remains adapter-driven, further separating calculator logic from view concerns.

## Context and Orientation

Files changed in this slice:

- `/hyperopen/src/hyperopen/domain/trading/indicators/oscillators.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicator_view_adapter.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave3.cljs`

Coordinator behavior remains domain-first in `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs`.

## Validation and Acceptance

Validation commands run from `/hyperopen`:

- `npm run check`
- `npm test`
- `npm run test:websocket`

Acceptance criteria met:

- Migrated IDs removed from wave3 definitions and wave3 dispatch map.
- Domain oscillator calculators provide those IDs and series.
- Adapter map contains required line/histogram presentation metadata.
- Required validation gates pass.

## Idempotence and Recovery

This migration is additive-safe and can be validated repeatedly. Recovery is scoped to reintroducing removed wave3 entries for any affected ID if regression is observed.

## Interfaces and Dependencies

Public indicator APIs remain unchanged:

- `hyperopen.views.trading-chart.utils.indicators/get-available-indicators`
- `hyperopen.views.trading-chart.utils.indicators/calculate-indicator`

Plan revision note: 2026-02-15 15:07Z - Completed phase-8 record created after parity validation and wave3 relative/SMI/UO family removal.
