# Portfolio Sparse Benchmark R2

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan follows `/hyperopen/.agents/PLANS.md`. It was created because the user directly requested an execution plan and implementation on 2026-05-25 after local debugging showed that `R^2` is hidden for a sparse 1Y portfolio benchmark comparison.

## Purpose / Big Picture

Portfolio users can select BTC, HYPE, or another benchmark on `/portfolio` and open the Performance Metrics tab. Today the `R^2` and `Information Ratio` rows disappear for accounts whose portfolio history is sampled weekly or biweekly, even when benchmark history exists and the chart can draw both series. After this change, sparse portfolio histories should still show benchmark-relative metrics as low-confidence estimates by comparing each portfolio observation interval against the benchmark return over the same interval. A user should be able to revisit the reported spectated portfolio with `range=1y&bench=BTC&bench=HYPE&tab=performance-metrics` and see `R^2` and `Information Ratio` between `Volatility (ann.)` and `Calmar`, marked with the existing estimated-row affordance.

## Context References

Public refs:
- Direct user request in this Codex session: “create an execution plan and implement it.”

Repo artifacts:
- `/hyperopen/AGENTS.md` requires ExecPlans for complex and risky UI-affecting work.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define the ExecPlan contract.
- `/hyperopen/src/hyperopen/portfolio/metrics/builder.cljs` assembles portfolio metrics.
- `/hyperopen/src/hyperopen/portfolio/metrics/builder/benchmark.cljs` computes benchmark-relative metrics.
- `/hyperopen/src/hyperopen/views/portfolio/performance_metrics_view.cljs` hides rows whose portfolio and benchmark cells all format as `--`.

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-05-25 15:30Z) Reproduced the hidden `R^2` row locally on `http://localhost:8081/portfolio?spectate=0x7c930969fcf3e5a5c78bcf2e1cefda3f53e3c8fd&range=1y&scope=all&chart=returns&bench=BTC&bench=HYPE&tab=performance-metrics`.
- [x] (2026-05-25 15:30Z) Confirmed the immediate cause: `:portfolio-values :metric-status :r2` is `:suppressed` with `:benchmark-coverage-gate-failed`.
- [x] (2026-05-25 15:30Z) Confirmed the deeper cause: the portfolio return series has 42 daily return points over a 365-day span, `:daily-coverage` about 0.115, `:daily-max-missing-streak` 13, and `:daily-min? false`.
- [x] (2026-05-25 15:30Z) Confirmed benchmark data is present: BTC and HYPE each have 365 daily benchmark rows and 42 aligned portfolio end days.
- [x] (2026-05-25 15:30Z) Created this active ExecPlan.
- [x] (2026-05-25 15:32Z) Added a failing model test for sparse portfolio interval benchmark metrics.
- [x] (2026-05-25 15:33Z) Verified RED: focused builder test failed because `:r2` and `:information-ratio` were `nil`, `:suppressed`, and still used `:benchmark-coverage-gate-failed`.
- [x] (2026-05-25 15:43Z) Implemented the sparse interval fallback in portfolio metric benchmark building.
- [x] (2026-05-25 15:43Z) Added low-confidence reason copy for sparse interval benchmark estimates.
- [x] (2026-05-25 15:46Z) Verified GREEN for the focused builder test: `hyperopen.portfolio.metrics.builder-test` passed with 10 tests and 65 assertions.
- [x] (2026-05-25 15:49Z) Verified surrounding portfolio VM/view tests: 18 tests and 132 assertions passed.
- [x] (2026-05-25 16:02Z) Verified required repo gates: `npm run check`, `npm test`, and `npm run test:websocket` all passed. The first `npm test` attempt hit a websocket coalescing failure that passed in isolation and on rerun.
- [x] (2026-05-25 16:12Z) Verified the linked portfolio route in Playwright: `R^2` and `Information Ratio` render between `Volatility (ann.)` and `Calmar`, each with the `~` estimated marker.
- [x] (2026-05-25 16:13Z) Stopped dev server and browser-inspection sessions with `npm run dev:kill && npm run browser:cleanup`.
- [x] (2026-05-25 16:14Z) Move this plan to `docs/exec-plans/completed/` after acceptance passes.

## Surprises & Discoveries

- Observation: The benchmark overlap itself is not missing. BTC and HYPE each align with 42 sparse portfolio observations, but `benchmark-enabled?` still fails because it also requires the portfolio daily-quality gate.
  Evidence: Local browser state showed `BTC.alignedCount = 42`, `HYPE.alignedCount = 42`, while portfolio quality had `daily-points = 42`, `daily-coverage = 0.11475409836065574`, `daily-max-missing-streak = 13`, and `daily-min? = false`.

- Observation: If the gate is manually relaxed, an `R^2` value is computable but the current calculation compares sparse portfolio interval returns to same-day benchmark daily returns, which is not the right user-facing metric.
  Evidence: Local probe computed `BTC` `r2IfGateRelaxed` around `0.0026` and `HYPE` around `0.0216`; those values use the existing daily alignment, not matched interval windows.

- Observation: The RED test failed for the intended current behavior.
  Evidence: `node out/test.js --test=hyperopen.portfolio.metrics.builder-test` reported `expected: (number? (:r2 metrics*)) actual: nil`, and the status remained `:suppressed` with reason `:benchmark-coverage-gate-failed`.

- Observation: The first fallback implementation was too broad and changed an existing short dense daily parity case.
  Evidence: The focused builder test reported `compute-performance-metrics-parity-test` failures because `:r2` became `0.9564942673385225` with `:low-confidence` status where the test expects suppression. The fallback now only applies when portfolio intervals contain gaps longer than one daily period.

- Observation: The focused builder test is green after narrowing the fallback to sparse interval histories.
  Evidence: `node out/test.js --test=hyperopen.portfolio.metrics.builder-test` passed 10 tests and 65 assertions with 0 failures and 0 errors.

- Observation: The surrounding portfolio VM/view tests are green.
  Evidence: `node out/test.js --test=hyperopen.views.portfolio.vm.benchmarks-test --test=hyperopen.views.portfolio.performance-metrics-view-test --test=hyperopen.views.portfolio.vm-test --test=hyperopen.views.portfolio-view-performance-metrics-test` passed 18 tests and 132 assertions with 0 failures and 0 errors.

- Observation: The first full `npm test` attempt failed in websocket runtime coalescing, but the failure did not reproduce in isolation or on full-suite rerun.
  Evidence: The first `npm test` reported 3 failures in `hyperopen.websocket.application.runtime-test/market-coalescing-invariant-test`; the targeted namespace then passed 5 tests and 12 assertions, and rerunning `npm test` passed 4046 tests and 22259 assertions.

- Observation: The required repo gates are green.
  Evidence: `npm run check` exited 0, `npm test` exited 0 on rerun with 4046 tests and 22259 assertions, and `npm run test:websocket` exited 0 with 526 tests and 3046 assertions.

- Observation: The linked portfolio route now renders benchmark-relative metric rows.
  Evidence: A Playwright probe against `http://localhost:8081/portfolio?spectate=0x7c930969fcf3e5a5c78bcf2e1cefda3f53e3c8fd&range=1y&scope=all&chart=returns&bench=BTC&bench=HYPE&tab=performance-metrics` found the card order `Volatility (ann.)`, `R^2`, `Information Ratio`, `Calmar`; `R^2` showed a `~` marker and portfolio value `0.10`, and `Information Ratio` showed a `~` marker and portfolio value `0.12`. The banner tooltip included `Estimated from sparse portfolio intervals.`

## Decision Log

- Decision: Implement a sparse interval fallback instead of simply rendering suppressed daily-aligned `R^2`.
  Rationale: The portfolio observations are roughly weekly or biweekly. Comparing those interval returns against one-day benchmark returns would be misleading. Comparing benchmark return over the same start and end timestamps is more defensible and explains the low-confidence estimate clearly.
  Date/Author: 2026-05-25 / Codex.

- Decision: Preserve current daily-aligned behavior whenever the portfolio daily-quality gate passes.
  Rationale: Existing QuantStats parity and high-coverage daily metrics should remain stable. The fallback is only for sparse portfolio histories that fail daily coverage but still have enough matched intervals for benchmark comparison.
  Date/Author: 2026-05-25 / Codex.

## Outcomes & Retrospective

Implemented and validated. Sparse portfolio histories now compute low-confidence `R^2` and `Information Ratio` by matching each portfolio observation interval to the compounded benchmark return over the same interval. Dense daily behavior is preserved: short dense daily histories that previously suppressed benchmark-relative metrics still suppress them, and high-quality dense daily histories still return `:ok` benchmark metrics.

The UI now explains `:benchmark-sparse-intervals` as a low-confidence reason. The linked 1Y spectated portfolio route renders both rows in the Performance Metrics table with the existing estimated-row affordance.

## Context and Orientation

The portfolio metrics pipeline is pure ClojureScript. The view model builds cumulative portfolio return rows for the selected range, derives benchmark cumulative rows for selected benchmark coins, and sends those rows to the metrics builder. The metrics builder turns portfolio cumulative rows into daily rows and irregular intervals. “Daily rows” are one return per calendar day. “Intervals” are returns between adjacent observed portfolio points; for sparse accounts, one interval may span several days.

The current benchmark path lives in `/hyperopen/src/hyperopen/portfolio/metrics/builder/benchmark.cljs`. It calls `history/align-daily-returns`, which matches portfolio daily rows and benchmark daily rows by exact day string. Then `/hyperopen/src/hyperopen/portfolio/metrics/builder/core.cljs` only enables `R^2` and `Information Ratio` when both daily portfolio quality and benchmark overlap are acceptable. The visible table in `/hyperopen/src/hyperopen/views/portfolio/performance_metrics_view.cljs` filters rows with no displayable values, so suppressed `R^2` disappears entirely.

The sparse fallback should stay inside `hyperopen.portfolio.metrics.builder.benchmark` because it is benchmark-relative metric logic. It should not modify portfolio view code except for adding a low-confidence reason title in `/hyperopen/src/hyperopen/views/portfolio/low_confidence.cljs`.

## Plan of Work

First, add a RED test in `/hyperopen/test/hyperopen/portfolio/metrics/builder_test.cljs`. The test will build a sparse portfolio cumulative return series with observations every 14 days and a benchmark return series with one benchmark return at each interval end. The default daily-quality gate should fail for the sparse portfolio, but there are enough matched intervals for a low-confidence `R^2` and `Information Ratio`. Before implementation, the test should fail because both metrics are `nil` and `:suppressed`.

Second, update `/hyperopen/src/hyperopen/portfolio/metrics/builder/benchmark.cljs`. Add helpers that take strategy intervals and normalized benchmark daily rows, compound benchmark returns whose timestamps fall within each strategy interval, and return aligned interval pairs. Extend `build-benchmark-context` to include both the existing daily alignment and the sparse interval alignment. When daily quality passes, keep using the existing daily alignment with `:ok` status. When daily quality fails but interval alignment has at least `:benchmark-min-points`, use the interval alignment with `:low-confidence` status and reason `:benchmark-sparse-intervals`. If neither path has enough points, preserve the existing suppressed `:benchmark-coverage-gate-failed` result.

Third, update `/hyperopen/src/hyperopen/views/portfolio/low_confidence.cljs` so `:benchmark-sparse-intervals` explains the estimate as coming from sparse portfolio intervals and appears in the banner reason ordering.

Fourth, run focused tests, then run the repository-required validation gates for code changes: `npm run check`, `npm test`, and `npm run test:websocket`. Because this changes UI-visible metrics on the portfolio page, also run a focused browser verification against the linked portfolio route or record an explicit blocker if browser verification cannot complete.

## Concrete Steps

Run all commands from `/Users/barry/.codex/worktrees/1804/hyperopen`.

1. Write the RED test in `test/hyperopen/portfolio/metrics/builder_test.cljs`.
2. Run `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.metrics.builder-test`.
   Expected before production changes: the new sparse interval test fails because `(:r2 metrics*)` is `nil` or its status is `:suppressed`.
3. Implement the benchmark sparse interval fallback.
4. Run the same focused test command again.
   Expected after implementation: all `hyperopen.portfolio.metrics.builder-test` tests pass.
5. Run the focused portfolio VM and view tests:
   `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.views.portfolio.vm.benchmarks-test --test=hyperopen.views.portfolio.performance-metrics-view-test --test=hyperopen.views.portfolio-vm-test`
6. Run required gates:
   `npm run check`
   `npm test`
   `npm run test:websocket`
7. Start the local app if browser verification is needed:
   `npm run dev`
   Navigate to `http://localhost:8081/portfolio?spectate=0x7c930969fcf3e5a5c78bcf2e1cefda3f53e3c8fd&range=1y&scope=all&chart=returns&bench=BTC&bench=HYPE&tab=performance-metrics`.
   Expected: `R^2` and `Information Ratio` render between `Volatility (ann.)` and `Calmar`, with the estimated marker.
8. Stop dev/browser sessions with `npm run dev:kill` and `npm run browser:cleanup`.

## Validation and Acceptance

Acceptance is behavioral. For the sparse 1Y portfolio route, Performance Metrics must show `R^2` and `Information Ratio` rather than hiding them. Those rows must be marked low-confidence, not high-confidence, because the portfolio history is not daily. Existing high-quality daily benchmark behavior must remain unchanged and existing tests that expect `R^2` status `:ok` for dense daily history must still pass.

The main automated acceptance test is the new sparse interval metrics test in `hyperopen.portfolio.metrics.builder-test`. It proves that default daily quality can fail while sparse interval `R^2` still computes as low-confidence.

The full acceptance commands are:

    npm run check
    npm test
    npm run test:websocket

All should exit with code 0 before the plan is moved to completed.

## Idempotence and Recovery

The changes are pure computation and view copy. Re-running tests is safe. If browser verification starts a local dev server on a different port because 8080 is occupied, use the port printed by Shadow CLJS and keep the route path/query unchanged. If a validation command fails, keep this ExecPlan active, record the failure in `Surprises & Discoveries`, and fix the smallest failing behavior before moving on.

## Artifacts and Notes

Initial local reproduction found these key values in browser state:

    :metric-status :r2 => :suppressed
    :metric-reason :r2 => :benchmark-coverage-gate-failed
    :quality :daily-points => 42
    :quality :daily-coverage => 0.11475409836065574
    :quality :daily-max-missing-streak => 13
    BTC benchmark daily rows => 365
    HYPE benchmark daily rows => 365
    BTC and HYPE aligned sparse days => 42

## Interfaces and Dependencies

In `/hyperopen/src/hyperopen/portfolio/metrics/builder/benchmark.cljs`, `build-benchmark-context` should accept strategy daily rows, benchmark daily rows, strategy intervals, and quality gates. It should return the existing keys `:aligned-benchmark`, `:strategy-aligned`, `:benchmark-aligned`, and `:benchmark-min?`, plus any private helper data needed by `add-benchmark-relative-metrics`. Consumers outside this namespace should not need to know about the fallback details.

In `/hyperopen/src/hyperopen/portfolio/metrics/builder.cljs`, pass `intervals` into `benchmark/build-benchmark-context`.

In `/hyperopen/src/hyperopen/views/portfolio/low_confidence.cljs`, add `:benchmark-sparse-intervals` to the title and ordering so users can understand why the row is marked estimated.

Revision note: 2026-05-25 15:30Z. Initial active ExecPlan created from local debugging evidence and the selected sparse interval fallback design.

Revision note: 2026-05-25 15:33Z. Added RED-phase evidence after writing the sparse interval benchmark metric test.

Revision note: 2026-05-25 15:46Z. Implemented sparse interval benchmark alignment, added low-confidence copy, preserved dense daily suppression behavior, and verified the focused builder test.

Revision note: 2026-05-25 15:49Z. Verified the focused portfolio VM/view test slice.

Revision note: 2026-05-25 16:02Z. Recorded required validation gates and the non-reproducing full-suite websocket failure from the first `npm test` attempt.

Revision note: 2026-05-25 16:13Z. Recorded Playwright route verification and cleanup.
