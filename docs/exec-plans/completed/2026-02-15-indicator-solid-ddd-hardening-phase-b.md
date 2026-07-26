# Indicator SOLID/DDD Hardening Phase B

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The indicator architecture has improved, but there are still gaps against SOLID/DDD boundaries: contracts do not enforce series cardinality, parameter validation is still mostly generic, the view facade still has one direct family dependency path, and legacy `wave2/wave3` terminology remains in tests. After this phase, input/output contracts will be stricter and more indicator-aware, the facade will route through one domain orchestration path, and naming will align with semantic domain language.

## Progress

- [x] (2026-02-15 19:05Z) Created active ExecPlan for Phase B hardening.
- [x] (2026-02-15 19:15Z) Milestone 1 completed: added `/hyperopen/src/hyperopen/domain/trading/indicators/schema.cljs` and wired contracts to indicator-aware param specs sourced from catalog metadata.
- [x] (2026-02-15 19:17Z) Milestone 2 completed: series cardinality is now enforced in `valid-series?` using `expected-length`.
- [x] (2026-02-15 19:20Z) Milestone 3 completed: `calculate-sma` now routes through registry orchestration instead of direct trend-family calls.
- [x] (2026-02-15 19:22Z) Milestone 4 completed: removed `wave2/wave3` terminology from indicator facade tests and helper sets.
- [x] (2026-02-15 19:30Z) Milestone 5 completed: required validation gates passed (`npm run check`, `npm test`, `npm run test:websocket`).
- [x] (2026-02-15 19:32Z) Milestone 6 completed: phase evidence recorded; plan kept active for follow-on SOLID/DDD slices.
- [x] (2026-02-15 20:10Z) Milestone 7 completed: moved style metadata from view adapter into dedicated style catalog namespace with semantic family projections.
- [x] (2026-02-15 21:35Z) Milestone 8 completed: extended semantic extraction to volatility channels via `/hyperopen/src/hyperopen/domain/trading/indicators/volatility/channels.cljs`, while preserving registry routing and public indicator interfaces.
- [x] (2026-02-15 21:40Z) Milestone 9 completed: replaced per-family duplicated dispatch/contract plumbing with shared `family-runtime` single-source declaration model in all six family modules.
- [x] (2026-02-15 21:45Z) Milestone 10 completed: extracted heavy math/pattern helpers to reusable domain math namespaces (`/hyperopen/src/hyperopen/domain/trading/indicators/math/statistics.cljs` and `/hyperopen/src/hyperopen/domain/trading/indicators/math/patterns.cljs`) and rewired callers/tests.

## Surprises & Discoveries

- Observation: output contract validation currently accepts any vector size for series values.
  Evidence: `valid-series?` in `/hyperopen/src/hyperopen/domain/trading/indicators/contracts.cljs` ignores `expected-length`.
- Observation: per-indicator parameter validation is not yet sourced from indicator catalog metadata.
  Evidence: `/hyperopen/src/hyperopen/domain/trading/indicators/contracts.cljs` uses a global `numeric-param-keys` set.
- Observation: strict cardinality surfaced a real mismatch in Ichimoku output lengths from the math adapter path.
  Evidence: `npm test` initially failed in `calculate-indicator-ichimoku-and-vwap-shape-test` because `:ichimoku-cloud` was rejected by the new output-length contract.
- Observation: indicator view adapter was carrying a very large mixed-responsibility style map that made adapter logic noisy and harder to reason about.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/utils/indicator_view_adapter.cljs` previously contained line/histogram/marker style maps plus projection logic in one namespace.
- Observation: oscillator semantic splitting can be done incrementally without registry/interface churn by extracting focused calculator namespaces and delegating through the existing calculator map.
  Evidence: momentum-family calculators now live in `/hyperopen/src/hyperopen/domain/trading/indicators/oscillators/momentum.cljs` while `/hyperopen/src/hyperopen/domain/trading/indicators/oscillators.cljs` preserves public entrypoints.
- Observation: extracting correlation/TSI/SMI logic into a statistics module removed complex helper math from the main oscillators namespace without changing caller contracts.
  Evidence: `/hyperopen/src/hyperopen/domain/trading/indicators/oscillators/statistics.cljs` now owns Pearson/rolling-correlation/TSI helpers and related calculators.
- Observation: trend regression indicators can be extracted cleanly as a cohesive semantic unit with no registry/public API changes.
  Evidence: LSMA/LRC/LRS calculators now live in `/hyperopen/src/hyperopen/domain/trading/indicators/trend/regression.cljs` and are referenced through the existing trend calculator map.
- Observation: parity guardrails can catch catalog/calculator drift immediately with minimal runtime impact.
  Evidence: `test/hyperopen/domain/trading/indicators/family_parity_test.cljs` asserts equality between catalog IDs and supported calculator IDs for all six families.
- Observation: replacing per-family plumbing with a shared runtime introduced forward-reference compiler warnings where `get-*-indicators` called `*-family` before it was defined.
  Evidence: `npm run check` reported `:undeclared-var` warnings in all six family modules until explicit `declare` forms were added.
- Observation: extracting heavy math helpers into dedicated modules allows tests to exercise stable public math APIs instead of dereferencing private vars in family modules.
  Evidence: `/hyperopen/test/hyperopen/domain/trading/indicators/heavy_algorithms_test.cljs` now imports `/hyperopen/src/hyperopen/domain/trading/indicators/math/{statistics,patterns}.cljs`.

## Decision Log

- Decision: implement contract and boundary hardening before further namespace sharding.
  Rationale: stronger contracts reduce migration risk while larger semantic extraction continues.
  Date/Author: 2026-02-15 / Codex
- Decision: derive parameter schemas from catalog metadata instead of hand-maintained numeric-key allowlists.
  Rationale: this reduces drift and keeps validation policy close to indicator definitions.
  Date/Author: 2026-02-15 / Codex
- Decision: keep cardinality enforcement strict and normalize Ichimoku series lengths in-domain.
  Rationale: output contracts should remain invariant; calculator implementations should adapt to satisfy the contract.
  Date/Author: 2026-02-15 / Codex
- Decision: extract style metadata into a dedicated style catalog namespace and compute semantic family style maps there.
  Rationale: this keeps the adapter focused on projection behavior while moving presentation configuration to a separate boundary.
  Date/Author: 2026-02-15 / Codex
- Decision: start Milestone 8 decomposition with momentum oscillators first.
  Rationale: momentum calculators are cohesive and low-risk to move, enabling safe incremental decomposition of the largest family module.
  Date/Author: 2026-02-15 / Codex
- Decision: continue Milestone 8 by extracting trend regression as a second family slice.
  Rationale: regression calculators have clear cohesion and minimal dependencies, making them an effective next extraction target.
  Date/Author: 2026-02-15 / Codex
- Decision: implement interim family parity guardrails as the first Milestone 9 step.
  Rationale: this prevents silent drift while larger single-source calculator/metadata unification is still in progress.
  Date/Author: 2026-02-15 / Codex
- Decision: converge all indicator families on one shared family runtime abstraction (`build-family`, `indicators`, `supported-indicator-ids`, `calculate`).
  Rationale: this removes repeated dispatch/contract code and turns catalog-calculator parity into a load-time invariant at a single boundary.
  Date/Author: 2026-02-15 / Codex
- Decision: complete semantic sharding with a volatility channels module before closing Milestone 8.
  Rationale: Milestone 8 explicitly targeted oscillator, trend, and volatility decomposition; channels are a cohesive volatility subgroup with minimal external dependencies.
  Date/Author: 2026-02-15 / Codex
- Decision: keep legacy family public entrypoints intact while moving implementations into semantic submodules and math helpers.
  Rationale: this preserves view-facing and registry-facing APIs while improving DDD boundary shape internally.
  Date/Author: 2026-02-15 / Codex

## Outcomes & Retrospective

This phase delivered concrete SOLID/DDD improvements end to end: contracts are indicator-aware and enforce series-length invariants; the SMA facade path uses the same domain registry boundary as other indicators; style metadata has a dedicated catalog boundary; semantic wording replaced `wave2/wave3` in tests; family modules share one runtime declaration model with parity checks; and heavy math/pattern helpers now live in reusable domain math namespaces.

Milestones 8, 9, and 10 are now complete. The architecture is materially less coupled: family modules focus on business calculations, shared runtime concerns are centralized, and reusable math kernels are no longer hidden in view-adjacent family files. Remaining future work is optional hardening/optimization, not required for this phase.

## Context and Orientation

Relevant files for this phase:

- `/hyperopen/src/hyperopen/domain/trading/indicators/contracts.cljs` enforces input/output guardrails.
- `/hyperopen/src/hyperopen/domain/trading/indicators/catalog/*.cljs` contains indicator metadata and defaults.
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs` is the view facade for available indicators and calculation calls.
- `/hyperopen/test/hyperopen/domain/trading/indicators/contracts_test.cljs` and `/hyperopen/test/hyperopen/views/trading_chart/utils/indicators_test.cljs` verify contract and facade behavior.

Terms used:

- Cardinality: the number of datapoints in a series. For chart compatibility, each output series should align 1:1 with input candle count.
- Parameter schema: per-indicator validation rules (type and range) derived from indicator metadata/default config.
- Facade boundary purity: view utility functions should depend on one domain orchestration interface, not bypass it for specific family internals.

## Plan of Work

Milestone 1 introduces a small indicator schema module that composes catalog metadata into per-indicator parameter specs (period min/max plus typed defaults). Contracts will consume this schema instead of relying only on a global key allowlist.

Milestone 2 extends output contracts so each returned series vector length must match expected input candle count.

Milestone 3 rewires `calculate-sma` in the view facade to use the domain registry path, removing direct dependency on `domain-trend` calculations.

Milestone 4 updates tests and helper names to semantic wording, removing `wave2/wave3` vocabulary while preserving behavioral coverage.

Milestone 5 executes required validation gates and records evidence.

## Concrete Steps

From `/hyperopen`:

1. Add a new schema helper namespace under `/hyperopen/src/hyperopen/domain/trading/indicators/` that merges catalog definitions and emits per-indicator parameter specs.
2. Update `/hyperopen/src/hyperopen/domain/trading/indicators/contracts.cljs` to consume schema rules and enforce series cardinality.
3. Update `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs` to route SMA calculation through registry orchestration.
4. Update tests in `/hyperopen/test/hyperopen/domain/trading/indicators/contracts_test.cljs` and `/hyperopen/test/hyperopen/views/trading_chart/utils/indicators_test.cljs`.
5. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
6. Update this plan sections with concrete outcomes and evidence.

## Validation and Acceptance

Acceptance criteria for this phase:

- Invalid output series length is rejected by domain contracts.
- Per-indicator period bounds are enforced from catalog metadata.
- Unknown numeric strings and non-numeric period values are rejected.
- `calculate-sma` still returns the same points shape but no longer calls family module directly.
- `wave2/wave3` wording is removed from indicator facade tests.
- Required gates pass.

## Idempotence and Recovery

All changes are safe refactors and validation hardening. If a regression appears, rollback is straightforward by restoring previous contract checks and facade call path while keeping schema module additive. No persistent migrations are involved.

## Artifacts and Notes

Validation evidence from `/hyperopen`:

- `npm run check` passed (lint + docs checks + app/test compile).
- `npm test` passed after Ichimoku cardinality normalization (`Ran 782 tests containing 3019 assertions. 0 failures, 0 errors.`).
- `npm run test:websocket` passed (`Ran 86 tests containing 267 assertions. 0 failures, 0 errors.`).
- After style-catalog extraction, gates passed again: `npm run check`, `npm test`, and `npm run test:websocket` with zero failures.
- After momentum sub-namespace extraction, gates passed again: `npm run check`, `npm test`, and `npm run test:websocket` with zero failures.
- After trend regression extraction, gates passed again: `npm run check`, `npm test`, and `npm run test:websocket` with zero failures.
- After family parity guardrails were added, gates passed again: `npm run check`, `npm test`, and `npm run test:websocket` with zero failures.
- After family-runtime unification, heavy-helper extraction, and volatility channels semantic split, gates passed again: `npm run check`, `npm test`, and `npm run test:websocket` with zero failures.

## Interfaces and Dependencies

Public interfaces to preserve:

- `hyperopen.views.trading-chart.utils.indicators/get-available-indicators`
- `hyperopen.views.trading-chart.utils.indicators/calculate-indicator`
- `hyperopen.views.trading-chart.utils.indicators/calculate-sma`
- `hyperopen.domain.trading.indicators.contracts/valid-indicator-input?`
- `hyperopen.domain.trading.indicators.contracts/enforce-indicator-result`

Plan revision note: 2026-02-15 19:05Z - Initial Phase B plan created for contract hardening, boundary cleanup, and semantic naming alignment.
Plan revision note: 2026-02-15 19:32Z - Completed Milestones 1-6, recorded validation evidence, and extended plan with follow-on structural milestones 7-10.
Plan revision note: 2026-02-15 20:10Z - Completed Milestone 7 by extracting style metadata into a dedicated style catalog and revalidating all required gates.
Plan revision note: 2026-02-15 20:25Z - Started Milestone 8 with momentum oscillator extraction and revalidated all required gates.
Plan revision note: 2026-02-15 20:45Z - Extended Milestone 8 with statistics oscillator extraction and revalidated all required gates.
Plan revision note: 2026-02-15 21:00Z - Extended Milestone 8 with trend regression extraction and revalidated all required gates.
Plan revision note: 2026-02-15 21:15Z - Started Milestone 9 with catalog/calculator parity guardrails and revalidated all required gates.
Plan revision note: 2026-02-15 21:40Z - Completed Milestone 9 with `family-runtime` single-source declaration model across all indicator families.
Plan revision note: 2026-02-15 21:45Z - Completed Milestone 10 by extracting reusable math/pattern helpers and rewiring callers and heavy-algorithm tests.
Plan revision note: 2026-02-15 21:50Z - Completed Milestone 8 by extracting volatility channel calculators into a semantic sub-namespace and revalidating required gates.
