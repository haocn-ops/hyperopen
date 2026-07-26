# Portfolio Benchmark Relative Metric Columns

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan follows `/hyperopen/.agents/PLANS.md`. It was created because the user asked on 2026-05-25 to move ambiguous benchmark-relative metrics such as `R^2` out of the Portfolio column and into each selected benchmark column.

## Purpose / Big Picture

When more than one benchmark is selected on the Portfolio Performance Metrics tab, `R^2` shown in the Portfolio column is ambiguous because it is not obvious which benchmark it references. After this change, `R^2` and `Information Ratio` should display per selected benchmark column, with the Portfolio column blank for those rows. The value in the `BTC` column should mean the portfolio’s benchmark-relative metric versus BTC; the value in the `HYPE` column should mean the same metric versus HYPE.

## Context References

- Direct user request in this Codex session: “Because we have multiple benchmarks, I think the way we should show R2 or correlation is for each benchmark relative to the portfolio.”
- `/hyperopen/src/hyperopen/views/portfolio/vm/performance.cljs` builds benchmark metric columns for the Portfolio page.
- `/hyperopen/src/hyperopen/portfolio/application/metrics_bridge.cljs` computes metrics synchronously when no worker is available and normalizes worker results.
- `/hyperopen/src/hyperopen/portfolio/worker.cljs` computes metrics in the portfolio Web Worker.
- `/hyperopen/src/hyperopen/views/portfolio/performance_metrics_view.cljs` renders rows from VM-enriched metric groups.
- Prior completed plan: `/hyperopen/docs/exec-plans/completed/2026-05-25-portfolio-sparse-benchmark-r2.md`.

## Progress

- [x] (2026-05-25 16:24Z) Created this active ExecPlan.
- [x] (2026-05-25 16:31Z) Added RED tests for request shape, per-benchmark relative metric computation, and VM column placement.
- [x] (2026-05-25 16:40Z) Implemented per-benchmark relative metric overlay in sync and worker computation.
- [x] (2026-05-25 16:40Z) Blank the Portfolio column for benchmark-relative metric rows when benchmarks are selected.
- [x] (2026-05-25 16:42Z) Verified focused portfolio tests: 32 tests and 194 assertions passed.
- [x] (2026-05-25 16:53Z) Verified required non-browser gates: `npm run check`, `npm test`, and `npm run test:websocket` all passed.
- [x] (2026-05-25 17:00Z) Verified the linked multi-benchmark portfolio route in Playwright at widths 375, 768, 1280, and 1440.
- [x] (2026-05-25 17:01Z) Stopped dev server and browser-inspection sessions with `npm run dev:kill && npm run browser:cleanup`.
- [x] (2026-05-25 17:02Z) Move this plan to `docs/exec-plans/completed/` after acceptance passes.

## Surprises & Discoveries

- Observation: Current request data gives the portfolio metrics computation only the first selected benchmark through `:benchmark-cumulative-rows`.
  Evidence: `build-metrics-request-data` sets the portfolio request benchmark from `(some-> benchmark-requests first :request :strategy-cumulative-rows)`.

- Observation: Current benchmark columns are built from standalone benchmark metrics, so benchmark `R^2` cells are `--`.
  Evidence: `benchmark-values-by-coin` is populated by computing each benchmark as its own strategy with no benchmark rows.

- Observation: The RED tests fail in the expected places.
  Evidence: Focused run of `hyperopen.views.portfolio.vm.performance-helpers-test` and `hyperopen.portfolio.application.metrics-bridge-test` reported failures for the stale first-benchmark request shape, missing per-benchmark overlay, and non-blank Portfolio relative metric cells.

- Observation: The focused portfolio metrics and VM slice is green.
  Evidence: `node out/test.js --test=hyperopen.portfolio.application.metrics-bridge-test --test=hyperopen.portfolio.worker-test --test=hyperopen.views.portfolio.vm.performance-helpers-test --test=hyperopen.views.portfolio.vm.benchmarks-test --test=hyperopen.views.portfolio.performance-metrics-view-test --test=hyperopen.views.portfolio.vm-test --test=hyperopen.views.portfolio-view-performance-metrics-test` passed 32 tests and 194 assertions.

- Observation: The required non-browser gates are green.
  Evidence: `npm run check` exited 0, `npm test` exited 0 with 4048 tests and 22270 assertions, and `npm run test:websocket` exited 0 with 526 tests and 3046 assertions.

- Observation: The linked route now displays relative metric values per benchmark and blanks the Portfolio cells.
  Evidence: Playwright against `http://localhost:8081/portfolio?spectate=0x7c930969fcf3e5a5c78bcf2e1cefda3f53e3c8fd&range=1y&scope=all&chart=returns&bench=BTC&bench=HYPE&tab=performance-metrics` reported `R^2` BTC `0.10`, HYPE `0.04`, Portfolio `--`; `Information Ratio` BTC `0.12`, HYPE `0.02`, Portfolio `--`.

- Observation: Basic multi-width browser QA passed for the changed table rows.
  Evidence: At widths 375, 768, 1280, and 1440, Playwright found the metrics card and `R^2` row visible with the same benchmark-column values and Portfolio `--`.

## Decision Log

- Decision: Treat `:r2` and `:information-ratio` as benchmark-relative display keys.
  Rationale: Both metrics depend on a benchmark comparator and become ambiguous in the Portfolio column when multiple benchmarks are selected. `R^2` is the user-reported confusion; `Information Ratio` has the same comparator ambiguity.
  Date/Author: 2026-05-25 / Codex.

- Decision: Preserve standalone benchmark metrics for all other rows, and overlay only benchmark-relative keys into benchmark column metric values.
  Rationale: Existing benchmark columns are still useful for benchmark CAGR, volatility, drawdown, and other standalone metrics. Only comparator-dependent rows need special display behavior.
  Date/Author: 2026-05-25 / Codex.

## Outcomes & Retrospective

Implemented and validated. `R^2` and `Information Ratio` are now computed for each selected benchmark relative to the portfolio and displayed under each benchmark column. The Portfolio column is blank for those comparator-dependent rows when benchmarks are selected, removing the first-benchmark ambiguity. Other benchmark columns continue to use standalone benchmark metrics for non-relative rows.

## Plan of Work

First, add tests before production code. Update the portfolio VM performance helper tests so `build-metrics-request-data` no longer attaches only the first benchmark to the portfolio request. Add a VM enrichment test proving `R^2` and `Information Ratio` blank the Portfolio column and use per-benchmark values when benchmark columns exist. Add a metrics computation test proving sync computation overlays portfolio-vs-benchmark relative values into each benchmark’s metric map.

Second, update metrics computation in both sync and worker paths. Compute portfolio standalone metrics without first-benchmark coupling. For each selected benchmark, compute existing standalone benchmark metrics, then compute portfolio metrics with that benchmark as `:benchmark-daily-rows`, and overlay `:r2` and `:information-ratio` value/status/reason into that benchmark’s metric map.

Third, update the Portfolio VM column enrichment so benchmark-relative rows do not display a Portfolio value when benchmark columns are selected. This keeps the table semantics clear even if stale worker data still contains a portfolio-level relative value.

Fourth, run focused tests, required repo gates, and a Playwright route check against the user’s multi-benchmark route. Clean up dev/browser sessions before final response.

## Validation and Acceptance

Acceptance requires:

- With multiple selected benchmarks, `R^2` and `Information Ratio` render values in each benchmark column.
- The Portfolio column renders `--` for those benchmark-relative rows.
- Other benchmark metric columns continue to show standalone benchmark values.
- The linked portfolio route shows no ambiguous portfolio-level `R^2` value.

Required code gates:

    npm run check
    npm test
    npm run test:websocket

## Idempotence and Recovery

The changes are pure metrics computation and VM row shaping. Re-running tests is safe. If browser verification needs a different port because `8080` is occupied, use the Shadow CLJS printed port and the same route path/query. If a gate fails outside this touched behavior, record it here and rerun a focused test to separate a pre-existing or flaky failure from this change.
