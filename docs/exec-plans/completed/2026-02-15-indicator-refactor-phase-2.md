# Refactor Trading Chart Indicators (Phase 2: View Adapter + Semantic Split Start)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and is self-contained for contributors working only from the current tree.

## Purpose / Big Picture

After this phase, a dedicated view adapter map will own presentation metadata (line names/colors, histogram names/colors) for the first semantically split indicator groups. Indicator calculation logic for an initial subset will move into semantic namespaces (`trend`, `oscillators`, `volatility`) while preserving the registry dispatch pattern and existing chart behavior.

Users should see unchanged indicator rendering, but the codebase will gain clearer extension seams and reduced coupling between calculation and presentation concerns.

## Progress

- [x] (2026-02-15 14:24Z) Created this ExecPlan at `/hyperopen/docs/exec-plans/active/2026-02-15-indicator-refactor-phase-2.md`.
- [x] (2026-02-15 14:27Z) Added dedicated indicator view adapter namespace at `/hyperopen/src/hyperopen/views/trading_chart/utils/indicator_view_adapter.cljs` with style metadata map and series constructors for the split subset.
- [x] (2026-02-15 14:27Z) Created semantic namespaces `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_trend.cljs`, `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_oscillators.cljs`, and `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_volatility.cljs`.
- [x] (2026-02-15 14:27Z) Rewired `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs` to consume semantic registries while preserving API behavior.
- [x] (2026-02-15 14:28Z) Passed required validation gates: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-02-15 14:28Z) Moved plan to completed with outcomes and follow-up notes.

## Surprises & Discoveries

- Observation: Wave-1 indicators are a practical subset to split first because they are fewer and already implemented without external library wrappers.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs` currently owns a compact set of core indicator calculators.
- Observation: Map-literal dispatcher refactors are prone to delimiter mistakes during conversion from `case`.
  Evidence: initial `npm test` compile reported unmatched delimiter in `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_trend.cljs`; fixed and rerun passed.

## Decision Log

- Decision: Start semantic split with a focused subset (wave-1 trend/oscillator/volatility indicators) while leaving wave-2/wave-3 internals intact this phase.
  Rationale: lower risk and meaningful architectural progress without destabilizing >100 indicator flows in one change.
  Date/Author: 2026-02-15 / Codex

## Outcomes & Retrospective

Phase 2 is complete for the initial semantic split scope. Presentation metadata (series names/colors and histogram colors) for the split subset now lives in `/hyperopen/src/hyperopen/views/trading_chart/utils/indicator_view_adapter.cljs` instead of being hardcoded in moved calculator functions.

Core wave-1 subset indicators now run from semantic namespaces while the public coordinator remains stable:

- Trend: `sma`, `alma`, `aroon`, `adx`
- Oscillators: `accelerator-oscillator`, `awesome-oscillator`, `balance-of-power`, `advance-decline`
- Volatility: `week-52-high-low`, `atr`, `bollinger-bands`

Public API remained stable (`calculate-sma`, `get-available-indicators`, `calculate-indicator`) and required gates passed:

- `npm run check`
- `npm test`
- `npm run test:websocket`

Follow-up scope remains to migrate additional indicator families (wave-2 and wave-3 internals) into semantic namespaces and expand adapter coverage.

## Context and Orientation

Key files expected in this phase:

- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave2.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave3.cljs`
- `/hyperopen/src/hyperopen/domain/trading/indicators/math.cljs`

New files to be introduced:

- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicator_view_adapter.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_trend.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_oscillators.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_volatility.cljs`

## Plan of Work

Introduce a dedicated view adapter map for style/presentation metadata and centralized series construction for the semantically split subset. Move selected wave-1 calculators into semantic namespaces and expose each as metadata vectors plus registry maps. Keep `calculate-indicator` in `indicators.cljs` as the stable public coordinator, but route to semantic calculators before wave-2/wave-3 fallbacks.

## Concrete Steps

All commands run from `/Users//projects/hyperopen`.

1. Add semantic namespace files and view adapter.

    rg --files src/hyperopen/views/trading_chart/utils

2. Rewire coordinator registry.

    rg -n "get-available-indicators|calculate-indicator|indicator-calculators" src/hyperopen/views/trading_chart/utils/indicators.cljs

3. Run required validation.

    npm run check
    npm test
    npm run test:websocket

## Validation and Acceptance

Acceptance criteria:

- View styling metadata for the split subset lives in a dedicated adapter map.
- Trend/oscillator/volatility semantic namespaces exist and provide registry-style calculator dispatch.
- Public API (`get-available-indicators`, `calculate-indicator`, `calculate-sma`) remains intact.
- Required validation gates pass.

## Idempotence and Recovery

Changes are additive. If a split indicator regresses, fall back that indicator to the legacy calculator path while keeping semantic namespace scaffolding and adapter map intact.

## Artifacts and Notes

This phase is intentionally incremental and does not fully retire wave-2/wave-3 presentation coupling.

## Interfaces and Dependencies

Public interface compatibility required:

- `hyperopen.views.trading-chart.utils.indicators/get-available-indicators`
- `hyperopen.views.trading-chart.utils.indicators/calculate-indicator`
- `hyperopen.views.trading-chart.utils.indicators/calculate-sma`

Plan revision note: 2026-02-15 14:24Z - Initial phase-2 plan created before implementation.
Plan revision note: 2026-02-15 14:28Z - Updated with completed implementation, validation outcomes, and retrospective.
