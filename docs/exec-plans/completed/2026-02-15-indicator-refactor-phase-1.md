# Refactor Trading Chart Indicator Utilities (Phase 1: Shared Math + Registry Dispatch)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and is self-contained for contributors working only from the current tree.

## Purpose / Big Picture

After this change, indicator calculation code is less duplicated and easier to extend without editing multiple `case` blocks. Users should observe unchanged indicator behavior, but the implementation will centralize shared math utilities and use registry maps for indicator dispatch in wave-1, wave-2, and wave-3 namespaces.

This is a focused refactor phase, not a full domain relocation. It addresses immediate DRY violations and extension friction while preserving current public APIs and rendering behavior.

## Progress

- [x] (2026-02-15 02:03Z) Audited `indicators.cljs`, `indicators_wave2.cljs`, and `indicators_wave3.cljs` for duplication and dispatch patterns.
- [x] (2026-02-15 02:04Z) Created this ExecPlan at `/hyperopen/docs/exec-plans/active/2026-02-15-indicator-refactor-phase-1.md`.
- [x] (2026-02-15 02:08Z) Implemented shared math kernel namespace at `/hyperopen/src/hyperopen/domain/trading/indicators/math.cljs` and migrated duplicated helper implementations in wave-1/wave-2/wave-3 indicator utilities.
- [x] (2026-02-15 02:08Z) Replaced `case`-based indicator dispatch with registry map dispatch in `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs`, `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave2.cljs`, and `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave3.cljs`.
- [x] (2026-02-15 02:08Z) Preserved existing tests without new test additions; existing warmup and shape assertions continue to validate behavior.
- [x] (2026-02-15 02:09Z) Passed required validation gates: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-02-15 02:09Z) Moved ExecPlan to `/hyperopen/docs/exec-plans/completed/` after acceptance passes.

## Surprises & Discoveries

- Observation: The three indicator namespaces duplicate many helpers (`finite-number?`, `clamp`, `parse-period`, `times`, `field-values`, `mean`, `rolling-apply`, and related rolling math), but not all variants are identical.
  Evidence: helper definitions in `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs`, `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave2.cljs`, and `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave3.cljs`.
- Observation: warmup behavior differs by namespace for some calculations (for example SMA/RMA seed timing), and tests assert this behavior.
  Evidence: `calculate-sma-test` in `/hyperopen/test/hyperopen/views/trading_chart/utils/indicators_test.cljs` expects first two whitespace points for period `2`.
- Observation: migrating dispatch from `case` to map lookups introduced a temporary unmatched delimiter in the new map literal.
  Evidence: initial `npm test` compile failed at `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs:650`; fixed immediately and rerun passed.

## Decision Log

- Decision: Execute an incremental refactor (shared math extraction + registry dispatch) instead of full domain/presentation split in one change.
  Rationale: keeps risk bounded, preserves runtime behavior, and delivers immediate DRY/OCP gains without destabilizing 100+ indicator paths.
  Date/Author: 2026-02-15 / Codex
- Decision: Preserve existing warmup semantics via mode-aware rolling helpers rather than normalizing formulas in this phase.
  Rationale: current tests and charts rely on established output shapes; semantic formula changes should be isolated in a later parity pass.
  Date/Author: 2026-02-15 / Codex

## Outcomes & Retrospective

Phase 1 refactor is complete. Core duplicated helper implementations were extracted into `/hyperopen/src/hyperopen/domain/trading/indicators/math.cljs`, and all three indicator utility namespaces now consume shared helper logic with mode-aware rolling semantics to preserve existing warmup/output behavior.

All three dispatchers now use registry maps instead of hardcoded `case` branches, reducing extension friction and centralizing indicator-function mapping in each namespace.

Required validation gates passed:

- `npm run check`
- `npm test`
- `npm run test:websocket`

Remaining work for future phases is architectural relocation (moving indicator calculation ownership out of `views`) and presentation/style decoupling from domain calculations.

## Context and Orientation

Key files for this work:

- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs`: wave-1 definitions, calculators, and fallback dispatch to wave-2/wave-3.
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave2.cljs`: wave-2 definitions and calculators (mix of local math + `indicatorts` adapter usage).
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave3.cljs`: wave-3 definitions and calculators (local statistical math and marker logic).
- `/hyperopen/test/hyperopen/views/trading_chart/utils/indicators_test.cljs`: current indicator utility assertions, including warmup/output-shape expectations.

Refactor scope for this phase:

- Add a shared, pure helper namespace for indicator math utilities.
- Repoint existing indicator files to shared helpers, keeping public behavior unchanged.
- Replace `case` dispatch with calculator registry maps to reduce modification points for future indicators.

Out of scope for this phase:

- Moving all indicator formulas out of `views` namespaces into new bounded contexts.
- Removing style metadata from indicator calculators.
- Introducing a full protocol-based abstraction for external indicator engines.

## Plan of Work

Create `/hyperopen/src/hyperopen/domain/trading/indicators/math.cljs` to host shared pure helpers used across indicator namespaces. Include finite-number checks, numeric parsing/clamping, candle field extraction, rolling-window helpers, and moving-average/statistics primitives with explicit mode handling to preserve current warmup semantics.

Edit `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs` to require the shared math namespace and remove duplicated helper implementations. Keep local wrappers only where mode selection is needed for legacy warmup behavior.

Edit `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave2.cljs` and `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave3.cljs` similarly, replacing duplicate helper bodies with shared helper calls.

Replace each file’s hardcoded `case` dispatch with a private calculator registry map (`indicator-id` -> function var) and a small lookup-based dispatcher.

Run full validation gates and then move this plan to completed with outcomes and any notable follow-up debt.

## Concrete Steps

All commands run from `/Users//projects/hyperopen`.

1. Create shared helper namespace and wire imports.

    rg -n "defn-? (finite-number\?|clamp|parse-period|times|field-values|mean|rolling-apply|sma-values|stddev-values|rma-values)" src/hyperopen/views/trading_chart/utils/indicators*.cljs

2. Replace dispatch case blocks with registry maps.

    rg -n "defn calculate-(indicator|wave2-indicator|wave3-indicator)|case indicator-type" src/hyperopen/views/trading_chart/utils/indicators*.cljs

3. Run focused indicator tests.

    npm test -- indicators_test.cljs

4. Run required gates.

    npm run check
    npm test
    npm run test:websocket

## Validation and Acceptance

Acceptance criteria:

- Shared helper implementations exist in one namespace and duplicated helper bodies are removed from all three indicator utility files.
- Indicator dispatch in each file uses a registry map lookup instead of a hardcoded `case` branch.
- Existing indicator tests pass, including warmup/output-shape behavior assertions.
- Required validation gates pass.

Manual verification:

1. Run `npm run dev`.
2. Open the trading chart indicator dropdown.
3. Add one indicator from wave-1, wave-2, and wave-3.
4. Confirm chart renders all three without runtime errors and dropdown metadata remains intact.

## Idempotence and Recovery

The refactor is additive and behavior-preserving by design. If any calculator regresses, restore only that indicator’s lookup mapping or helper call path while keeping shared helpers and registry structure intact.

Because no persisted schema/state shape changes are introduced, rollback is file-local and safe.

## Artifacts and Notes

This phase intentionally does not change public indicator payload shape (`{:type :pane :series [:markers]}`) to avoid chart interop regressions.

## Interfaces and Dependencies

Public interfaces intentionally unchanged:

- `hyperopen.views.trading-chart.utils.indicators/get-available-indicators`
- `hyperopen.views.trading-chart.utils.indicators/calculate-indicator`
- `hyperopen.views.trading-chart.utils.indicators-wave2/get-wave2-indicators`
- `hyperopen.views.trading-chart.utils.indicators-wave2/calculate-wave2-indicator`
- `hyperopen.views.trading-chart.utils.indicators-wave3/get-wave3-indicators`
- `hyperopen.views.trading-chart.utils.indicators-wave3/calculate-wave3-indicator`

Dependencies:

- Existing `indicatorts` JS library usage in wave-2 remains in place.
- New shared helper namespace is pure ClojureScript with no side effects.

Plan revision note: 2026-02-15 02:04Z - Initial plan created before implementation.
Plan revision note: 2026-02-15 02:09Z - Updated with completed implementation, validation outcomes, and retrospective.
