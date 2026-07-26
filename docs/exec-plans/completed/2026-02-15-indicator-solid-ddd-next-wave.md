# Indicator SOLID/DDD Next Wave

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The indicator stack now runs through semantic domain namespaces, but key architectural gaps remain: oversized mixed-responsibility files, mutable registry extension state, compatibility fallback in marker projection, and shallow input contracts. After this plan, indicator metadata will be separated from calculators, registry composition will be deterministic and side-effect-free, marker projection will be semantic-only, and heavy algorithm regressions will be guarded by focused tests. Users should see unchanged indicator behavior in the chart while maintainers gain safer extension and lower coupling.

## Progress

- [x] (2026-02-15 17:30Z) Created active ExecPlan for next SOLID/DDD hardening wave.
- [x] (2026-02-15 18:00Z) Milestone 1 completed: catalog metadata extracted into dedicated family catalog namespaces and family modules now consume catalog-owned definitions.
- [x] (2026-02-15 18:10Z) Milestone 2 completed: mutable registry extension state removed and replaced by deterministic `compose-domain-families` composition with explicit optional extension arguments.
- [x] (2026-02-15 18:20Z) Milestone 3 completed: input contract checks hardened to indicator-aware validation with required OHLC/volume fields and numeric parameter shape checks.
- [x] (2026-02-15 18:25Z) Milestone 4 completed: semantic marker boundary enforced in contracts and view adapter marker projection (no rendered-marker passthrough).
- [x] (2026-02-15 18:35Z) Milestone 5 completed: heavy algorithm determinism/performance tests added for tie-aware ranks, zig-zag pivots, and rolling regression.
- [x] (2026-02-15 18:43Z) Required gates passed: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-02-15 18:45Z) Plan updated with completion evidence and readied for move to completed.

## Surprises & Discoveries

- Observation: existing files can be split cleanly because all definition vectors are isolated at the top of each family module.
  Evidence: each family file has `def ^:private *-indicator-definitions` followed by a `get-*` function.
- Observation: `contracts_test` still asserted the previous two-arity contract API, so it failed to represent the new indicator-aware input contract boundary.
  Evidence: `test/hyperopen/domain/trading/indicators/contracts_test.cljs` originally invoked `(valid-indicator-input? data params)` and required an update to `(valid-indicator-input? indicator-type data params)`.
- Observation: strict wall-clock thresholds for heavy math tests are susceptible to CI variance.
  Evidence: thresholds were widened to 8s smoke bounds while keeping deterministic parity assertions as the primary signal.

## Decision Log

- Decision: implement metadata extraction first so later registry and contract edits remain behavior-preserving and low-risk.
  Rationale: catalog extraction is structural and allows immediate SRP improvement without changing calculator math.
  Date/Author: 2026-02-15 / Codex
- Decision: keep registry extension support but make it explicit-input driven rather than mutable global registration.
  Rationale: this preserves extensibility while removing hidden shared state and improving determinism/testability.
  Date/Author: 2026-02-15 / Codex
- Decision: enforce semantic-only markers at the contract boundary and adapter projection layer.
  Rationale: domain output stays presentation-agnostic and rendering concerns remain view-owned.
  Date/Author: 2026-02-15 / Codex
- Decision: include heavy algorithm tests in the global test runner and use conservative perf smoke ceilings.
  Rationale: parity/determinism guards should run continuously while avoiding flaky timing failures.
  Date/Author: 2026-02-15 / Codex

## Outcomes & Retrospective

The refactor wave achieved its intended architecture hardening without user-visible indicator behavior regressions. Indicator catalogs are now separated from calculators, registry composition is deterministic and side-effect free, contracts are indicator-aware, and marker boundaries are semantic-only. Heavy algorithm determinism/performance smoke tests now run in CI via the standard test runner. Required repository gates passed, providing evidence that this slice is production-safe.

Remaining long-tail work is larger semantic decomposition and internal algorithm extraction (for example, moving remaining private heavy helpers into dedicated math/statistics modules), but the foundational SOLID/DDD direction for these components is now materially stronger.

## Context and Orientation

Relevant files:

- `/hyperopen/src/hyperopen/domain/trading/indicators/{trend,oscillators,volatility,flow,structure,price}.cljs` currently contain both catalog definitions and calculator logic.
- `/hyperopen/src/hyperopen/domain/trading/indicators/registry.cljs` currently orchestrates all families and currently includes extension behavior.
- `/hyperopen/src/hyperopen/domain/trading/indicators/contracts.cljs` validates shape contracts and basic marker form.
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicator_view_adapter.cljs` projects domain outputs into chart-ready series and markers.

Terms used in this plan:

- Catalog metadata: indicator definition entries (`:id`, `:name`, `:default-config`, `:supports-period?`) used to render the indicator chooser and defaults.
- Deterministic registry composition: dispatch behavior that depends only on function inputs and static composition, not mutable process state.
- Semantic marker boundary: domain emits marker meaning (`:kind`, `:time`, `:price`) and only the view adapter decides drawing fields (`:shape`, `:color`, `:position`).

## Plan of Work

Milestone 1 will create dedicated catalog namespaces for each indicator family and remove in-file definition vectors from calculator modules. Family modules will depend on catalog namespaces for `get-*` functions.

Milestone 2 will refactor the registry to compose family descriptors from immutable values, remove mutable registration state, and preserve stable public APIs. A deterministic extension path will be supported via explicit function arity/arguments rather than global mutation.

Milestone 3 will strengthen contracts to validate per-indicator candle field requirements and parameter shapes. Family entrypoints will pass indicator type into contract checks.

Milestone 4 will make marker projection semantic-only by removing fallback passthrough of pre-rendered marker objects and updating contracts/tests to enforce semantic markers.

Milestone 5 will add focused tests for heavy internals (`tie-aware-ranks`, `zigzag-pivots`, and rolling regression stats) and performance smoke bounds.

## Concrete Steps

From `/hyperopen`:

1. Add catalog namespaces under `/hyperopen/src/hyperopen/domain/trading/indicators/catalog/` and wire family modules to those catalogs.
2. Refactor `/hyperopen/src/hyperopen/domain/trading/indicators/registry.cljs` to immutable family composition and explicit extension arguments.
3. Update `/hyperopen/src/hyperopen/domain/trading/indicators/contracts.cljs` and family entrypoints for per-indicator input checks.
4. Update `/hyperopen/src/hyperopen/views/trading_chart/utils/indicator_view_adapter.cljs` to semantic-only marker projection.
5. Add/adjust tests in `/hyperopen/test/hyperopen/domain/trading/indicators/*.cljs` and `/hyperopen/test/test_runner.cljs`.
6. Run required gates:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
7. Update plan living sections with evidence and move this file to completed.

## Validation and Acceptance

Acceptance criteria:

- Catalog metadata and calculator implementations are separated in code ownership.
- Registry behavior has no mutable global extension state.
- Domain input validation enforces per-indicator candle/parameter constraints.
- Marker projection accepts semantic markers only; chart marker visuals remain unchanged.
- Focused heavy algorithm tests exist and pass.
- Required gates pass with zero failures.

## Idempotence and Recovery

Changes are additive/refactor-only and test-driven. If regressions appear:

- Re-route `get-*` functions temporarily to in-module vectors while preserving new catalog files.
- Preserve immutable registry helpers and fall back to built-in family list only.
- Keep semantic marker projection and temporarily broaden marker contract acceptance if needed during migration.

No destructive migration or persistent state changes are involved.

## Artifacts and Notes

Validation evidence from `/hyperopen`:

- `npm run check` passed (lint suites + `shadow-cljs compile app` + `shadow-cljs compile test`).
- `npm test` passed with the new heavy suite included (`Ran 782 tests containing 3013 assertions. 0 failures, 0 errors.`).
- `npm run test:websocket` passed (`Ran 86 tests containing 267 assertions. 0 failures, 0 errors.`).

## Interfaces and Dependencies

Stable public APIs to preserve:

- `hyperopen.views.trading-chart.utils.indicators/get-available-indicators`
- `hyperopen.views.trading-chart.utils.indicators/calculate-indicator`
- `hyperopen.domain.trading.indicators.registry/get-domain-indicators`
- `hyperopen.domain.trading.indicators.registry/calculate-domain-indicator`

Plan revision note: 2026-02-15 17:30Z - Initial next-wave plan created for the six remaining SOLID/DDD hardening goals.
Plan revision note: 2026-02-15 18:45Z - Marked milestones complete, recorded validation evidence, and finalized for archive in completed ExecPlans.
