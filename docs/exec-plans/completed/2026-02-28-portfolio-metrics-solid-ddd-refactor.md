# Portfolio Metrics SOLID and DDD Refactor

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, portfolio metric computation remains behavior-compatible but is easier to understand and safer to evolve. The metrics engine will no longer depend on view-layer parsing utilities, the metrics orchestration path will be decomposed into small deterministic steps, and presentation catalog concerns will be moved out of the core computation flow.

A contributor can verify this by reading smaller focused modules, running the required test gates, and observing that `/portfolio` and vault performance metric paths still produce stable values and status/reason metadata.

## Progress

- [x] (2026-02-28 21:53Z) Fast-forwarded this worktree to local `main` (`46ac8c0 -> 9c9b6b0`) before any refactor edits.
- [x] (2026-02-28 21:53Z) Completed SOLID/DDD audit for `/hyperopen/src/hyperopen/portfolio/metrics.cljs` and captured concrete findings.
- [x] (2026-02-28 21:53Z) Authored this ExecPlan under `/hyperopen/docs/exec-plans/active/`.
- [x] (2026-02-28 21:56Z) Extracted parsing/input-shape utilities into `/hyperopen/src/hyperopen/portfolio/metrics/parsing.cljs` and rewired `metrics.cljs` to remove view-layer parsing dependency.
- [x] (2026-02-28 21:57Z) Moved metric row catalog/presentation mapping into `/hyperopen/src/hyperopen/portfolio/metrics/catalog.cljs` and kept `metrics/metric-rows` as a facade.
- [x] (2026-02-28 21:59Z) Decomposed `compute-performance-metrics` into staged helpers (`resolve-*`, `build-*`, and grouped `add-*` metric assemblers) with explicit data flow.
- [x] (2026-02-28 22:00Z) Added tests for compatibility seams in `/hyperopen/test/hyperopen/portfolio/metrics_test.cljs` (summary helper parity and `metric-rows` metadata propagation).
- [x] (2026-02-28 22:02Z) Ran required validation gates successfully: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-02-28 22:02Z) Updated this ExecPlan with implementation evidence and prepared it for move to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: `hyperopen.portfolio.metrics` currently depends on `hyperopen.views.account-info.projections` only to consume `parse-optional-num`, which inverts domain-to-view dependency direction for otherwise pure metric logic.
  Evidence: `/hyperopen/src/hyperopen/portfolio/metrics.cljs` namespace require and `/hyperopen/src/hyperopen/views/account_info/projections.cljs` re-export structure.

- Observation: `compute-performance-metrics` is both a policy engine and an output formatting/status assembly function, making change impact difficult to isolate.
  Evidence: `/hyperopen/src/hyperopen/portfolio/metrics.cljs` lines around 1389-1665.

- Observation: Adding a new one-argument overload to `returns-history-rows` caused a VM test regression under `with-redefs` because callsites expect the established 3-argument signature dispatch path.
  Evidence: `npm test` error in `portfolio-vm-returns-tab-uses-shared-portfolio-metrics-returns-source-test` (`returns_history_rows ... arity$3 is not a function`), resolved by restoring the public 3-argument function shape.

## Decision Log

- Decision: Preserve the existing public API surface of `hyperopen.portfolio.metrics` while internally moving responsibilities into focused helper namespaces.
  Rationale: VM and worker consumers already rely on stable function names and return shapes; preserving the facade avoids unnecessary integration churn.
  Date/Author: 2026-02-28 / Codex

- Decision: Keep formulas and metric semantics unchanged in this refactor unless tests reveal clear defects.
  Rationale: This task is architecture/maintainability focused; behavior changes should be separate, explicit remediation work.
  Date/Author: 2026-02-28 / Codex

- Decision: Keep `returns-history-rows` public signature at exactly 3 arguments and expose `returns-history-rows-from-summary` as a separate helper for focused composition.
  Rationale: Preserves compatibility with existing VM tests and callsites while still reducing mixed responsibilities.
  Date/Author: 2026-02-28 / Codex

## Outcomes & Retrospective

The refactor completed in one pass while preserving behavior and existing facade contracts. The key architecture outcomes are:

- Removed view-layer dependency from portfolio metric computation by introducing `/hyperopen/src/hyperopen/portfolio/metrics/parsing.cljs`.
- Moved presentation taxonomy into `/hyperopen/src/hyperopen/portfolio/metrics/catalog.cljs` so display row metadata no longer lives in core metric orchestration.
- Reworked `compute-performance-metrics` in `/hyperopen/src/hyperopen/portfolio/metrics.cljs` into small staged helpers (input resolution, quality/benchmark/window context building, and grouped output assembly) without changing downstream VM contracts.
- Added seam tests in `/hyperopen/test/hyperopen/portfolio/metrics_test.cljs` for metadata propagation and helper compatibility.

Validation results:

- `npm run check` passed.
- `npm test` passed (`Ran 1555 tests containing 8049 assertions. 0 failures, 0 errors.`).
- `npm run test:websocket` passed (`Ran 154 tests containing 710 assertions. 0 failures, 0 errors.`).

## Context and Orientation

The current portfolio metric engine lives in one large namespace:

- `/hyperopen/src/hyperopen/portfolio/metrics.cljs`

It serves both portfolio and vault view-model paths and worker offload paths:

- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`
- `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs`
- `/hyperopen/src/hyperopen/portfolio/worker.cljs`

The key architecture issue is dependency direction: core metric computation should not depend on view-level aggregators. The second issue is responsibility sprawl: one function (`compute-performance-metrics`) owns normalization, diagnostics, quality gating, benchmark alignment, rolling-window slicing, and final status/reason assembly.

## Plan of Work

Milestone 1 creates explicit non-view parsing seams in `src/hyperopen/portfolio/metrics/` and rewires `metrics.cljs` to consume those seams. This removes the cross-layer dependency inversion and makes input normalization intent explicit.

Milestone 2 extracts presentation taxonomy (`performance-metric-groups` and row assembly) into a dedicated catalog module so display metadata and core calculations no longer change together.

Milestone 3 decomposes `compute-performance-metrics` into small stage helpers that model a deterministic pipeline: input resolution, interval/daily derivation, diagnostics/gate computation, and final metric assembly. The top-level function remains a thin orchestrator returning the same contract.

Milestone 4 updates tests to protect compatibility and then runs the required compile and test gates.

## Concrete Steps

From `/hyperopen`:

1. Add helper namespaces under `/hyperopen/src/hyperopen/portfolio/metrics/` for parsing and catalog concerns.
2. Update `/hyperopen/src/hyperopen/portfolio/metrics.cljs` to require new helpers, remove view-layer require, and split the orchestration flow into staged private helpers.
3. Update/add tests in `/hyperopen/test/hyperopen/portfolio/metrics_test.cljs` for API-shape compatibility and orchestration behavior.
4. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
5. Update ExecPlan progress/outcomes and move plan file to completed.

## Validation and Acceptance

Acceptance criteria are:

- `hyperopen.portfolio.metrics` no longer requires view-layer projection namespace for number parsing.
- `metric-rows` behavior remains stable for VM consumers.
- `returns-history-rows` compatibility signature remains available for callers.
- Required gates all pass:
  - `npm run check`
  - `npm test`
  - `npm run test:websocket`

## Idempotence and Recovery

The refactor is additive and namespace-scoped. If a stage fails, rerun tests after each milestone and keep API wrappers stable. Recovery path is to keep the new helper namespaces in place and temporarily route facade functions back to previous in-file implementations while maintaining call contracts.

## Artifacts and Notes

Command evidence captured during implementation:

- Initial `npm run check` failed due missing local dependency (`@noble/secp256k1`), then succeeded after `npm install`.
- Full gates re-run after code edits all passed:
  - `npm run check`
  - `npm test`
  - `npm run test:websocket`

## Interfaces and Dependencies

The public facade functions in `/hyperopen/src/hyperopen/portfolio/metrics.cljs` must remain callable by current consumers:

- `returns-history-rows`
- `daily-compounded-returns`
- `compute-performance-metrics`
- `metric-rows`

New helper modules should remain pure and depend only on ClojureScript core and other portfolio metric helper namespaces.

## Plan Revision Notes

- 2026-02-28 / Codex: Updated plan from draft to completed state with concrete implementation evidence, added regression discovery/decision for `returns-history-rows` arity compatibility, and marked all milestones complete.
