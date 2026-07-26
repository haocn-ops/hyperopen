# Offload Benchmark Daily-Return Derivation Fully Into Portfolio Worker

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the portfolio VM no longer computes benchmark daily-compounded return rows per selected benchmark on the main UI thread before posting work to the metrics worker. The UI thread will post cumulative benchmark rows only, and benchmark daily-row derivation will happen inside the compute boundary (worker path, with sync fallback parity).

For users, benchmark chart overlays and performance metrics remain unchanged, but VM main-thread cost no longer scales with benchmark count due to pre-worker daily derivation.

## Progress

- [x] (2026-02-28 01:52Z) Reviewed planning/runtime guardrails and benchmark-worker code paths in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`, `/hyperopen/src/hyperopen/portfolio/worker.cljs`, and `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`.
- [x] (2026-02-28 01:52Z) Authored this ExecPlan.
- [x] (2026-02-28 01:53Z) Refactored VM metrics request shaping in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` so benchmark requests carry cumulative rows only and portfolio request carries `:benchmark-cumulative-rows`.
- [x] (2026-02-28 01:53Z) Moved benchmark daily-row derivation into compute-boundary helpers in both `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` (`compute-metrics-sync`) and `/hyperopen/src/hyperopen/portfolio/worker.cljs`.
- [x] (2026-02-28 01:54Z) Added VM regressions in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` for cumulative-only benchmark request shaping and sync-path daily-derivation parity.
- [x] (2026-02-28 01:55Z) Ran required gates successfully: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-02-28 01:55Z) Updated this ExecPlan and prepared move to `/hyperopen/docs/exec-plans/completed/`.
- [x] (2026-02-28 01:56Z) Re-ran required gates on the final tree after final worker formatting cleanup; all remained green.

## Surprises & Discoveries

- Observation: The current worker offload still receives benchmark daily rows that are derived in VM before the worker request is posted.
  Evidence: `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` lines around `performance-metrics-model` currently call `portfolio-metrics/daily-compounded-returns` inside benchmark request assembly.

- Observation: `compute-performance-metrics` already supports deriving daily rows from cumulative rows when daily rows are omitted.
  Evidence: `/hyperopen/src/hyperopen/portfolio/metrics.cljs` computes `strategy-rows` from cumulative rows when `strategy-daily-rows` is absent.

- Observation: Preserving backward-compatible request fields (`:benchmark-daily-rows` and `:strategy-daily-rows`) in compute-boundary helpers allows safe handling of both old and new payload shapes.
  Evidence: New helper functions `request-benchmark-daily-rows` and `request-strategy-daily-rows` in VM and worker first honor explicit daily rows when present, otherwise derive from cumulative rows.

## Decision Log

- Decision: Keep the request contract explicit by introducing `:benchmark-cumulative-rows` on portfolio request and removing per-benchmark `:strategy-daily-rows` from benchmark requests generated in VM.
  Rationale: This preserves benchmark-relative portfolio metrics while ensuring benchmark daily derivation does not occur in pre-worker UI-thread request shaping.
  Date/Author: 2026-02-28 / Codex

- Decision: Preserve a sync fallback path in VM that mirrors worker behavior by deriving benchmark daily rows inside compute code rather than request assembly.
  Rationale: Tests and non-worker environments must remain behaviorally consistent with the worker path.
  Date/Author: 2026-02-28 / Codex

## Outcomes & Retrospective

Implemented as planned.

What changed:

- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` now builds request payloads via `build-metrics-request-data` where benchmark requests include only `:strategy-cumulative-rows`.
- Portfolio request now carries `:benchmark-cumulative-rows` (primary benchmark cumulative series) instead of precomputed benchmark daily rows.
- VM sync fallback derives benchmark daily rows inside compute handling via `request-benchmark-daily-rows` and `request-strategy-daily-rows`.
- Worker compute path mirrors the same derivation behavior so benchmark daily conversion occurs inside worker compute handling.
- `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` now includes:
  - `portfolio-vm-builds-cumulative-only-benchmark-worker-requests-test`
  - `portfolio-vm-sync-metrics-derives-benchmark-daily-rows-from-cumulative-test`

Validation evidence:

- `npm run check` passed.
- `npm test` passed (`Ran 1506 tests containing 7643 assertions. 0 failures, 0 errors.`).
- `npm run test:websocket` passed (`Ran 153 tests containing 701 assertions. 0 failures, 0 errors.`).
- Re-ran all three required gates after final-tree cleanup; results remained unchanged and passing.

Scope gaps:

- None identified for the requested offload scope.

## Context and Orientation

Portfolio metrics are built in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`. A request payload is assembled in `performance-metrics-model` and sent to `request-metrics-computation!`, which posts to `/js/portfolio_worker.js` when workers are available.

Today, each selected benchmark coin is converted from cumulative rows to daily-compounded rows inside `performance-metrics-model` before worker post. This means UI-thread work still grows with benchmark count even though metric computation itself is workerized.

The worker implementation in `/hyperopen/src/hyperopen/portfolio/worker.cljs` computes metrics from the posted request payload. The VM also has a sync fallback (`compute-metrics-sync`) used when worker support is unavailable.

## Plan of Work

### Milestone 1: Cumulative-Only Benchmark Request Shaping in VM

Introduce a VM helper that builds metrics request data where each benchmark request contains only `:strategy-cumulative-rows`. Preserve selected benchmark ordering and coin mapping. Keep strategy daily-row derivation for the portfolio request only (single strategy pass), and include first benchmark cumulative rows as `:benchmark-cumulative-rows` for benchmark-relative portfolio metrics.

### Milestone 2: Compute-Boundary Benchmark Daily Derivation

Update worker and sync fallback compute paths so benchmark daily rows are derived from cumulative rows inside compute handling. Portfolio-level benchmark-relative metrics should derive benchmark daily rows from `:benchmark-cumulative-rows`; benchmark-per-coin metrics should derive strategy daily rows from each benchmark request cumulative series.

### Milestone 3: Regression Coverage + Validation

Add VM regressions that validate:

- Request shaping exposes cumulative-only benchmark requests before compute offload.
- Sync compute path still derives benchmark daily rows from cumulative-only request payloads.

Then run repository-required gates:

- `npm run check`
- `npm test`
- `npm run test:websocket`

## Concrete Steps

1. Edit `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` to build cumulative-only benchmark requests and thread `:benchmark-cumulative-rows` for portfolio request.
2. Edit `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` sync fallback helper(s) to derive benchmark daily rows inside compute path.
3. Edit `/hyperopen/src/hyperopen/portfolio/worker.cljs` to derive benchmark daily rows inside worker compute handling.
4. Edit `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` for regression coverage.
5. Run required gates from `/Users//projects/hyperopen`.
6. Update this plan sections and move file to `/hyperopen/docs/exec-plans/completed/`.

## Validation and Acceptance

Acceptance is complete when all conditions below are true.

1. In VM request assembly, benchmark requests contain cumulative rows only and do not include precomputed benchmark daily rows.
2. Portfolio benchmark-relative metrics are preserved by deriving benchmark daily rows in compute handling from posted cumulative rows.
3. Benchmark-per-coin metric computation derives daily rows inside compute handling and no longer depends on VM-precomputed benchmark daily rows.
4. Existing benchmark chart series and performance metric outputs remain behaviorally equivalent for current fixtures.
5. `npm run check`, `npm test`, and `npm run test:websocket` pass.

## Idempotence and Recovery

This change is VM/worker-local and safe to reapply. If regressions appear, recovery path is to keep the cumulative-only request helper and temporarily route sync/worker compute through existing optional daily-row fallback behavior in `compute-performance-metrics` while preserving tests.

No destructive operations or schema migrations are required.

## Artifacts and Notes

Primary implementation targets:

- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`
- `/hyperopen/src/hyperopen/portfolio/worker.cljs`
- `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`

No new dependencies are required.

## Interfaces and Dependencies

Stable interfaces to preserve:

- `hyperopen.views.portfolio.vm/portfolio-vm` view-model output shape.
- Worker message type `"compute-metrics"` and response type `"metrics-result"`.
- `hyperopen.portfolio.metrics/compute-performance-metrics` function contract.

Internal request schema update in this plan:

- Portfolio request now carries `:benchmark-cumulative-rows` for primary benchmark relative metrics.
- Benchmark requests carry `:strategy-cumulative-rows` only; benchmark daily rows are derived in compute code.

Plan revision note: 2026-02-28 01:52Z - Initial ExecPlan created for benchmark daily-row derivation offload so VM request assembly posts cumulative benchmark rows only.
Plan revision note: 2026-02-28 01:55Z - Marked implementation complete with VM/worker request-shape and compute-boundary updates, added regressions, and recorded required gate outcomes.
Plan revision note: 2026-02-28 01:56Z - Added final-tree gate rerun evidence after worker formatting-only cleanup.
