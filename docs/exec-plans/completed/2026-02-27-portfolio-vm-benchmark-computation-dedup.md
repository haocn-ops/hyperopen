# De-duplicate Benchmark Computation in Portfolio VM

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the portfolio VM will compute benchmark alignment from candle closes to strategy-return timestamps only once per benchmark coin for each summary range/timeline build, then reuse that aligned data for both returns-chart benchmark series and performance-metric benchmark columns.

Today, benchmark alignment work is duplicated between chart and metrics paths in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`, which increases avoidable per-render CPU cost on the portfolio page. After implementation, contributors should still see identical chart lines and metric values, but with a single benchmark-alignment pass feeding both consumers.

## Progress

- [x] (2026-02-27 01:36Z) Reviewed `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md` requirements for ExecPlan structure and active-plan placement.
- [x] (2026-02-27 01:36Z) Audited benchmark computation flow in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` (`benchmark-returns-points`, `benchmark-performance-column`, `build-chart-model`, `performance-metrics-model`) and current benchmark VM tests in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`.
- [x] (2026-02-27 01:36Z) Authored this ExecPlan.
- [x] (2026-02-27 01:41Z) Installed workspace dependencies with `npm install` after initial gate execution failed due missing local `shadow-cljs` binary in this worktree.
- [x] (2026-02-27 01:43Z) Implemented shared benchmark computation context in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` (`benchmark-computation-context`) with a single `benchmark-cumulative-return-rows-by-coin` alignment pass reused by chart and metrics.
- [x] (2026-02-27 01:43Z) Refactored `performance-metrics-model` and `build-chart-model` to consume precomputed benchmark cumulative rows and removed duplicated chart-path alignment helper usage.
- [x] (2026-02-27 01:44Z) Added regression coverage in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` (`portfolio-vm-reuses-benchmark-candle-request-for-chart-and-metrics-test`) to lock one benchmark-candle request resolution per VM build with chart+metrics benchmark outputs present.
- [x] (2026-02-27 01:45Z) Ran required validation gates with passing results: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-02-27 01:47Z) Re-ran required validation gates after final ExecPlan consistency edits; all gates remained green.
- [x] (2026-02-27 02:00Z) Added requested follow-up VM regressions in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`: no benchmark-request when benchmark selection is empty, one `returns-history-rows` call per VM build, and non-returns chart tab preserving benchmark metrics with one request setup.
- [x] (2026-02-27 02:00Z) Re-ran required validation gates after follow-up tests; all remained green.

## Surprises & Discoveries

- Observation: Benchmark candle interval selection (`returns-benchmark-candle-request`) and candle-to-aligned-return transformation are currently executed in two separate branches, one for chart rendering and one for metric computation.
  Evidence: `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` calls benchmark alignment via `benchmark-returns-points` from `build-chart-model` and again via `benchmark-cumulative-return-rows`/`benchmark-performance-column` from `performance-metrics-model`.

- Observation: Alignment logic depends only on benchmark candle timeline and strategy point timestamps, not on chart-specific y-axis normalization.
  Evidence: `aligned-benchmark-return-rows` consumes `benchmark-points` and `strategy-points` time data, while chart rounding happens later in `rows->chart-points` (`:returns` normalization).

- Observation: Performance metrics are computed regardless of chart-tab selection, so shared benchmark computation must not be gated only by `:chart-tab :returns`.
  Evidence: `portfolio-vm` always builds `performance-metrics` and `chart`; `performance-metrics-model` currently computes benchmark columns independently of chart tab.

- Observation: The validation gate scripts assume local dependencies are already installed; on a fresh worktree `shadow-cljs` was missing until `npm install` was run.
  Evidence: Initial `npm test` exited with `sh: shadow-cljs: command not found`; rerun after `npm install` succeeded.

- Observation: Counting calls to `portfolio-actions/returns-benchmark-candle-request` is a stable seam for verifying deduplicated benchmark-alignment setup across chart and metrics consumers.
  Evidence: New VM test asserts one call for a two-benchmark returns-tab build while chart benchmark series and performance benchmark columns are both present.

- Observation: The follow-up regression requests map cleanly to VM seams without exposing private helpers: benchmark request setup can be observed by redefining `portfolio-actions/returns-benchmark-candle-request`, and strategy recomputation can be observed by redefining `portfolio-metrics/returns-history-rows`.
  Evidence: Added tests in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` assert zero/one request calls and one returns-history invocation under representative states.

## Decision Log

- Decision: Introduce one VM-local benchmark alignment context (single source of truth) and pass it into both `performance-metrics-model` and `build-chart-model`.
  Rationale: This removes duplicated candle alignment work without changing action/runtime boundaries or adding side effects.
  Date/Author: 2026-02-27 / Codex

- Decision: Keep the deduplication scope inside `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` and tests in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`; do not expand to runtime/action layers.
  Rationale: The reported hot path is VM-local and can be fixed without widening blast radius.
  Date/Author: 2026-02-27 / Codex

- Decision: Preserve existing output contracts for `:chart` and `:performance-metrics` in the view model.
  Rationale: This is a performance/refactor task, not a product-behavior change; view rendering and selectors should remain stable.
  Date/Author: 2026-02-27 / Codex

- Decision: Compute strategy cumulative returns once inside `benchmark-computation-context` and reuse that same series for returns-tab chart strategy points.
  Rationale: This keeps benchmark alignment and returns-tab strategy plotting on one consistent timeline and avoids recomputing returns rows in separate branches.
  Date/Author: 2026-02-27 / Codex

- Decision: Validate deduplication by asserting one `returns-benchmark-candle-request` evaluation per VM build instead of exposing private alignment helpers for test instrumentation.
  Rationale: This preserves VM helper encapsulation while still proving chart+metrics setup shares one benchmark alignment preparation path.
  Date/Author: 2026-02-27 / Codex

## Outcomes & Retrospective

Implemented as planned in VM and VM regression tests.

Results:

- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` now builds one `benchmark-computation-context` per `portfolio-vm` call. It computes strategy cumulative returns once and computes aligned benchmark cumulative rows once per selected benchmark coin using one interval lookup from `returns-benchmark-candle-request`.
- `performance-metrics-model` now reads benchmark cumulative rows from context instead of recomputing candle alignment per benchmark column.
- `build-chart-model` now reads benchmark cumulative rows from the same context and only performs chart-point normalization locally.
- Duplicated benchmark-specific path (`benchmark-returns-points`) is removed from chart model flow, and benchmark alignment is no longer split across separate chart/metrics branches.
- New regression test confirms chart and metrics both populate benchmark output while benchmark candle request setup is executed once per VM build.

Validation evidence:

- `npm run check` passed.
- `npm test` passed (`Ran 1474 tests containing 7428 assertions. 0 failures, 0 errors.`).
- `npm run test:websocket` passed (`Ran 153 tests containing 701 assertions. 0 failures, 0 errors.`).
- Follow-up requested tests were added and full gates re-ran green with the same pass status.

Scope gaps: none identified for this refactor scope.

## Context and Orientation

The portfolio view model is built in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`. Before this refactor, benchmark alignment from candles to strategy timestamps ran in two separate branches: a chart branch (`benchmark-returns-points` from `build-chart-model`) and a metrics branch (`benchmark-cumulative-return-rows` used by `benchmark-performance-column`).

After this refactor, benchmark alignment is centralized in `benchmark-computation-context` and `benchmark-cumulative-return-rows-by-coin`, then reused by both `build-chart-model` and `performance-metrics-model`.

In this plan, “aligned benchmark series” means cumulative percent-return rows generated from benchmark closes and sampled at strategy-return timestamps. The deduplication target is this alignment step, not formatting, axis normalization, or display-only map shaping.

Existing benchmark behavior tests live in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`, especially:

- `portfolio-vm-returns-benchmark-series-aligns-to-portfolio-return-timestamps-test`
- `portfolio-vm-performance-metrics-include-all-selected-benchmarks-test`

These tests establish current functional parity and must continue passing after refactor.

## Plan of Work

### Milestone 1: Introduce Shared Benchmark Alignment Context

Add a VM helper that computes and stores aligned benchmark cumulative rows once for each selected benchmark coin using one strategy timeline and one summary-time-range interval selection. This helper should accept state, summary-time-range, selected benchmark coins, and strategy time points, and return a deterministic map by coin.

The helper is the only place where candle rows are read from `[:candles coin interval]` and passed through benchmark alignment. Preserve deterministic ordering by iterating selected coins in selector order.

### Milestone 2: Refactor Performance Metrics to Consume Shared Rows

Update `performance-metrics-model` so benchmark columns consume precomputed aligned cumulative rows from Milestone 1 rather than invoking an internal candle/alignment path per coin. Keep existing benchmark label behavior, primary benchmark selection, and benchmark-values maps unchanged.

The model should continue to derive benchmark daily rows and metrics values from aligned cumulative rows exactly as today.

### Milestone 3: Refactor Chart Builder to Consume Shared Rows

Update `build-chart-model` so benchmark series `:raw-points` are built from the same precomputed aligned cumulative rows used by performance metrics. Conversion from cumulative rows to chart points (`rows->chart-points`) remains chart-local, but no benchmark candle alignment should happen in chart builder anymore.

Remove or collapse obsolete helpers that only existed to support duplicated benchmark alignment paths (`benchmark-returns-points` and redundant wrappers) once both consumers are migrated.

### Milestone 4: Add Regression Coverage for Shared Path + Behavior Parity

Extend VM tests to cover:

- Existing benchmark chart timestamp/value alignment remains unchanged.
- Existing benchmark metric-column population remains unchanged for multi-benchmark selection.
- A new dedupe-focused regression that proves there is one benchmark alignment entry path per coin per VM build (for example by instrumenting a shared helper call count under `with-redefs` or equivalent seam).

Keep tests deterministic and avoid coupling to internal sort/hash map iteration.

### Milestone 5: Run Required Validation Gates and Record Evidence

Run repository-required gates from `/hyperopen`:

- `npm run check`
- `npm test`
- `npm run test:websocket`

Capture pass/fail evidence in this plan and update all living sections.

## Concrete Steps

1. Edit `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` to add a shared benchmark alignment helper and a benchmark rows map keyed by coin.

2. Edit `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` to thread shared benchmark rows into `performance-metrics-model` and remove duplicated alignment calls there.

3. Edit `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` to thread shared benchmark rows into `build-chart-model` and remove duplicated alignment calls there.

4. Edit `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` to add/adjust tests for dedupe and parity.

5. Run from `/hyperopen`:

       npm run check
       npm test
       npm run test:websocket

## Validation and Acceptance

Acceptance is complete when all conditions below are true.

1. Benchmark alignment from candle closes to strategy timestamps is computed through one shared VM path and reused by both chart and metrics consumers.
2. Returns chart benchmark series values/timestamps match pre-refactor behavior for existing fixtures.
3. Performance metric benchmark columns (including per-coin benchmark-values and primary benchmark fields) match pre-refactor behavior for existing fixtures.
4. No additional side effects or action/runtime wiring changes are introduced.
5. Required validation gates pass: `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

This refactor is VM-local and can be applied incrementally. If regressions appear, recovery is to keep the shared helper in place and temporarily route only one consumer (metrics or chart) to it while preserving old behavior for the other, then complete convergence after tests are green.

No schema migration or destructive operation is involved. Re-running edits/tests is safe.

## Artifacts and Notes

Primary implementation target:

- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`

Primary regression target:

- `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`

Related prior context already in-repo:

- `/hyperopen/docs/exec-plans/active/2026-02-26-portfolio-returns-benchmark-series.md`
- `/hyperopen/docs/exec-plans/active/2026-02-26-portfolio-quantstats-metrics-foundation.md`

This plan does not require new external dependencies.

Captured implementation evidence:

- New dedupe regression test:
  `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` (`portfolio-vm-reuses-benchmark-candle-request-for-chart-and-metrics-test`).
- Follow-up regressions in the same file:
  - `portfolio-vm-skips-benchmark-request-when-no-benchmarks-selected-test`
  - `portfolio-vm-computes-returns-history-once-per-build-test`
  - `portfolio-vm-non-returns-chart-tab-keeps-benchmark-metrics-and-single-request-test`
- Required gates run on 2026-02-27:
  - `npm run check` (pass)
  - `npm test` (pass; 1474 tests / 7428 assertions)
  - `npm run test:websocket` (pass; 153 tests / 701 assertions)

## Interfaces and Dependencies

Interfaces that must remain stable:

- `hyperopen.views.portfolio.vm/portfolio-vm` return shape used by `/hyperopen/src/hyperopen/views/portfolio_view.cljs`.
- Existing `:chart :series` entries (`:strategy` and benchmark series ids/labels/strokes).
- Existing `:performance-metrics` benchmark fields (`:benchmark-selected?`, `:benchmark-coin`, `:benchmark-label`, `:benchmark-coins`, `:benchmark-columns`, and row-level `:benchmark-values`).

Internal refactor interface to introduce:

- A shared benchmark-alignment helper in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` that returns aligned cumulative benchmark rows by coin for a given `(summary-time-range, selected coin list, strategy timeline)` input.

Plan revision note: 2026-02-27 01:36Z - Initial ExecPlan created for VM-local benchmark-alignment de-duplication between chart and performance-metrics hot paths.
Plan revision note: 2026-02-27 01:45Z - Marked implementation complete; documented VM refactor details, dedupe regression test, dependency-install prerequisite, and required gate outcomes.
Plan revision note: 2026-02-27 01:47Z - Revalidated final tree after documentation consistency updates; no regressions.
Plan revision note: 2026-02-27 02:00Z - Added three follow-up VM tests requested in review and re-ran full required validation gates.
