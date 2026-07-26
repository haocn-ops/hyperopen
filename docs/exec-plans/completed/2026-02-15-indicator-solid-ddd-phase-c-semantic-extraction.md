# Indicator SOLID/DDD Phase C Semantic Extraction

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this phase, trend and oscillator indicator implementations will be further decomposed into semantic sub-namespaces instead of large mixed modules, and the newly shared math kernels will have explicit parity/performance tests. This improves cohesion and makes behavior easier to validate and evolve without editing monolithic files.

## Progress

- [x] (2026-02-15 18:44Z) Created active ExecPlan for Phase C semantic extraction and kernel test hardening.
- [x] (2026-02-15 18:48Z) Milestone 1 completed: extracted trend moving-average/overlay calculators to `/hyperopen/src/hyperopen/domain/trading/indicators/trend/moving_averages.cljs` and rewired `trend.cljs` calculator map delegates.
- [x] (2026-02-15 18:50Z) Milestone 2 completed: extracted remaining oscillator calculators to `/hyperopen/src/hyperopen/domain/trading/indicators/oscillators/{classic,structure}.cljs` and reduced `oscillators.cljs` to family registry composition.
- [x] (2026-02-15 18:51Z) Milestone 3 completed: added focused parity + micro-bench coverage in `/hyperopen/test/hyperopen/domain/trading/indicators/math_kernels_test.cljs` for rolling regression, rolling correlation, and zigzag kernels.
- [x] (2026-02-15 18:53Z) Milestone 4 completed: required validation gates passed (`npm run check`, `npm test`, `npm run test:websocket`).

## Surprises & Discoveries

- Observation: zigzag parity at exact threshold boundaries is sensitive to floating-point precision.
  Evidence: test input using `110` at a `0.10` threshold did not trigger an uptrend because `100 * 1.1` evaluates slightly above `110` in JS floating-point; parity input was adjusted to clear the threshold margin (`111`).
- Observation: full semantic extraction of oscillators can be done without changing family API shape by making the top-level namespace a pure registry/composition module.
  Evidence: `/hyperopen/src/hyperopen/domain/trading/indicators/oscillators.cljs` now contains no calculator implementations and still satisfies parity/registry tests.

## Decision Log

- Decision: create a new Phase C ExecPlan before code changes.
  Rationale: work spans multiple large domain modules and test architecture, so it needs a living execution artifact per `/hyperopen/.agents/PLANS.md`.
  Date/Author: 2026-02-15 / Codex
- Decision: extract trend moving-average/overlay calculators into one cohesive `trend.moving-averages` module first.
  Rationale: it is the largest coherent cluster in `trend.cljs` and offers high cohesion with low integration risk.
  Date/Author: 2026-02-15 / Codex
- Decision: split remaining oscillators into `oscillators.classic` and `oscillators.structure` modules, keeping `momentum` and `statistics` as-is.
  Rationale: this keeps domain semantics clear while avoiding over-fragmentation into many tiny files.
  Date/Author: 2026-02-15 / Codex
- Decision: add a dedicated `math-kernels` test namespace for explicit kernel parity/perf coverage instead of only extending legacy heavy tests.
  Rationale: focused tests make regression scope explicit and keep kernel-level assertions discoverable.
  Date/Author: 2026-02-15 / Codex

## Outcomes & Retrospective

Phase C achieved the intended SOLID/DDD improvements. Trend moving-average/overlay calculators are extracted from `trend.cljs` into a dedicated semantic namespace. Remaining oscillator implementations were extracted from `oscillators.cljs` into `classic` and `structure` semantic namespaces, leaving the top-level oscillator namespace as a family composition boundary only. Shared math kernel tests now include targeted parity and micro-benchmark assertions for rolling regression, rolling correlation, and zigzag pivot detection.

All required gates passed after implementation and after fixing zigzag parity inputs to avoid floating-point threshold boundary ambiguity. No public family APIs changed.

## Context and Orientation

Relevant files for this phase:

- `/hyperopen/src/hyperopen/domain/trading/indicators/trend.cljs` currently contains mixed trend indicator families, including moving-average/overlay calculators.
- `/hyperopen/src/hyperopen/domain/trading/indicators/oscillators.cljs` currently still contains many non-momentum/non-statistics calculators.
- `/hyperopen/src/hyperopen/domain/trading/indicators/oscillators/momentum.cljs` and `/hyperopen/src/hyperopen/domain/trading/indicators/oscillators/statistics.cljs` already represent prior semantic extraction slices.
- `/hyperopen/src/hyperopen/domain/trading/indicators/math/statistics.cljs` and `/hyperopen/src/hyperopen/domain/trading/indicators/math/patterns.cljs` expose shared kernels that need additional parity/perf coverage.

## Plan of Work

Milestone 1 will add a new trend semantic namespace dedicated to moving-average/overlay calculators and supporting helper math, then update `/hyperopen/src/hyperopen/domain/trading/indicators/trend.cljs` to delegate those indicator IDs via the existing calculator map.

Milestone 2 will add semantic oscillator namespaces for remaining calculators and update `/hyperopen/src/hyperopen/domain/trading/indicators/oscillators.cljs` so it acts as a thin family registry module.

Milestone 3 will add focused tests in the domain indicator test suite for kernel parity and micro-bench behavior, targeting the shared math namespaces directly.

Milestone 4 will run required gates from repository root and record evidence in this document.

## Concrete Steps

From `/hyperopen`:

1. Create trend and oscillator semantic sub-namespace files and move cohesive calculator groups.
2. Update family calculator maps in `trend.cljs` and `oscillators.cljs` to reference extracted calculators.
3. Add focused kernel tests under `/hyperopen/test/hyperopen/domain/trading/indicators/`.
4. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
5. Update this plan sections with outcomes and evidence.

## Validation and Acceptance

Acceptance criteria for this phase:

- Trend moving-average/overlay calculators are no longer implemented in `trend.cljs`; they are delegated from semantic sub-namespace modules.
- Remaining oscillator calculators are no longer implemented directly in `oscillators.cljs`; they are delegated from semantic sub-namespace modules.
- New tests verify deterministic parity expectations for shared kernels and include focused runtime bounds.
- Required gates pass without regressions.

## Idempotence and Recovery

All work is refactor-oriented and additive. If regressions appear, rollback is straightforward by restoring calculator function references in `trend.cljs`/`oscillators.cljs` while keeping new modules/tests for incremental migration.

## Artifacts and Notes

Validation evidence from `/hyperopen`:

- `npm run check` passed with zero failures and zero compile warnings.
- `npm test` passed with zero failures (`Ran 789 tests containing 3058 assertions. 0 failures, 0 errors.`), including new `hyperopen.domain.trading.indicators.math-kernels-test`.
- `npm run test:websocket` passed with zero failures (`Ran 86 tests containing 267 assertions. 0 failures, 0 errors.`).

## Interfaces and Dependencies

Public interfaces to preserve:

- `hyperopen.domain.trading.indicators.trend/get-trend-indicators`
- `hyperopen.domain.trading.indicators.trend/calculate-trend-indicator`
- `hyperopen.domain.trading.indicators.oscillators/get-oscillator-indicators`
- `hyperopen.domain.trading.indicators.oscillators/calculate-oscillator-indicator`

Internal dependencies:

- `hyperopen.domain.trading.indicators.family-runtime`
- `hyperopen.domain.trading.indicators.math`
- `hyperopen.domain.trading.indicators.math-adapter`
- `hyperopen.domain.trading.indicators.math.statistics`
- `hyperopen.domain.trading.indicators.math.patterns`

Plan revision note: 2026-02-15 18:44Z - Initial Phase C plan created for semantic extraction of trend/oscillators and focused kernel parity/perf tests.
Plan revision note: 2026-02-15 18:50Z - Completed Milestones 1-3 with trend/oscillator semantic extraction and focused kernel tests.
Plan revision note: 2026-02-15 18:53Z - Completed Milestone 4 after full gate validation and recorded evidence/discoveries.
