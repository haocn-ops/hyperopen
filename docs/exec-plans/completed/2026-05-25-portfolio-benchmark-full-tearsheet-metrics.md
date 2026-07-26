# Portfolio Benchmark Full Tearsheet Metrics

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the `/portfolio` Performance Metrics tab will include the benchmark rows shown in the user-provided reference image: best and worst period returns, average drawdown diagnostics, recovery and ulcer/serenity ratios, monthly win/loss averages, win rates by day/month/quarter/year, and benchmark-relative Beta, Alpha, Correlation, and Treynor Ratio. A portfolio user who selects returns benchmarks will be able to compare the portfolio and every selected benchmark across the same richer tear-sheet rows, instead of seeing only the current base metric set plus `R^2` and `Information Ratio`.

The work is metric-centric. It should not add new network requests, websocket subscriptions, browser storage, or route behavior. The existing benchmark selector and performance-metrics worker already provide the required daily return series for the portfolio and selected benchmarks.

## Context References

Public refs:

- Direct user request on 2026-05-25: "I want you to create an execution plan to implement them, and then proceed with implementation."

Repo artifacts:

- `/hyperopen/docs/exec-plans/completed/2026-02-26-portfolio-quantstats-metrics-foundation.md` introduced the existing QuantStats-style portfolio performance metrics engine.
- `/hyperopen/docs/exec-plans/completed/2026-02-26-performance-metrics-quantstats-parity-remediation.md` records the later parity fixes for CAGR, rolling windows, and VaR/CVaR.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define this plan format.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-25T16:09Z) Reviewed the portfolio metrics catalog, builder, benchmark model, performance-metrics view, tests, planning docs, and repo feature-flow contract.
- [x] (2026-05-25T16:09Z) Checked QuantStats primary source on GitHub for the requested rows and mapped formulas to existing Hyperopen metric helpers.
- [x] (2026-05-25T16:09Z) Created this active ExecPlan with scope, formula decisions, tests, and validation gates.
- [x] (2026-05-25T16:13Z) Added RED tests in `test/hyperopen/portfolio/metrics/quantstats_parity_test.cljs`, `test/hyperopen/portfolio/metrics/builder_test.cljs`, and `test/hyperopen/views/portfolio/vm/benchmarks_test.cljs`.
- [x] (2026-05-25T16:14Z) Verified RED with `npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.metrics.quantstats-parity-test --test=hyperopen.portfolio.metrics.builder-test --test=hyperopen.views.portfolio.vm.benchmarks-test`; expected failures showed missing helpers and catalog rows.
- [x] (2026-05-25T16:22Z) Implemented pure helpers for period extremes, win rates, drawdown averages, recovery factor, ulcer index, serenity index, beta, alpha, correlation, and Treynor ratio.
- [x] (2026-05-25T16:22Z) Wired standalone rows through `compute-performance-metrics` and benchmark-relative rows through `benchmark/add-benchmark-relative-metrics`.
- [x] (2026-05-25T16:22Z) Updated `metrics/catalog.cljs` with the new drawdown, period-extreme, win-rate, and benchmark-relative rows.
- [x] (2026-05-25T16:23Z) Targeted tests passed: `npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.metrics.quantstats-parity-test --test=hyperopen.portfolio.metrics.builder-test --test=hyperopen.views.portfolio.vm.benchmarks-test`.
- [x] (2026-05-25T16:24Z) View/worker bridge tests passed: `npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.views.portfolio.performance-metrics-view-test --test=hyperopen.portfolio.worker-test --test=hyperopen.portfolio.application.metrics-bridge-test`.
- [x] (2026-05-25) `npm run check` passed.
- [x] (2026-05-25) `npm test` passed.
- [x] (2026-05-25) `npm run test:websocket` passed.
- [x] (2026-05-25) Moved this ExecPlan to `/hyperopen/docs/exec-plans/completed/` with completed outcomes after acceptance passed.
- [x] (2026-05-25) Added a metrics request signature schema version so already-open worker-backed portfolio and vault detail pages recompute after this metric surface change.
- [x] (2026-05-25) Follow-up: rendered Beta, Alpha, Correlation, and Treynor Ratio under each selected benchmark column relative to the portfolio, and bumped the portfolio metrics request signature schema to `3`.

## Surprises & Discoveries

- Observation: The existing benchmark columns already call `compute-performance-metrics` with each benchmark's cumulative return rows as its own strategy series.
  Evidence: `/hyperopen/src/hyperopen/views/portfolio/vm/performance.cljs` builds `:benchmark-requests`, and `/hyperopen/src/hyperopen/portfolio/application/metrics_bridge.cljs` computes `:benchmark-values-by-coin` by calling `portfolio-metrics/compute-performance-metrics`.

- Observation: Existing benchmark-relative metrics are limited to `:r2` and `:information-ratio`.
  Evidence: `/hyperopen/src/hyperopen/portfolio/metrics/builder/benchmark.cljs` only adds those two keys after aligning strategy and benchmark daily returns.

- Observation: The requested screenshot rows are QuantStats "full mode" report rows that come after the already implemented base rows.
  Evidence: QuantStats `reports.py` emits "Best Day %", "Worst Day %", "Best Month %", "Worst Month %", "Best Year %", "Worst Year %", drawdown averages, "Recovery Factor", "Ulcer Index", "Serenity Index", "Avg. Up Month %", "Avg. Down Month %", "Win Days %%", "Win Month %%", "Win Quarter %%", "Win Year %%", "Beta", "Alpha", "Correlation", and "Treynor Ratio".

## Decision Log

- Decision: Treat the user-provided screenshot as a QuantStats-style reference and implement the exact report-row semantics where the codebase can support them.
  Rationale: Hyperopen already names the current metrics as QuantStats-style parity work, and the screenshot labels match QuantStats full report rows. Conventional finance formulas could drift from the expected row values.
  Date/Author: 2026-05-25 / Codex

- Decision: Keep the new rows in the existing Performance Metrics table and metric catalog, not a separate benchmark-only table.
  Rationale: The current view already supports a metric label column, zero or more benchmark columns, and a portfolio column. Reusing that table keeps the user workflow intact and avoids a new UI surface.
  Date/Author: 2026-05-25 / Codex

- Decision: Compute Beta, Alpha, Correlation, and Treynor Ratio only for the portfolio values, using the first selected benchmark as the comparison series, while benchmark columns for those rows remain unavailable.
  Rationale: In QuantStats, Greek and correlation rows compare each strategy against the benchmark and show `-` for the benchmark column itself. Hyperopen's current `:portfolio-request` already carries the primary benchmark series, while each standalone benchmark request does not have a second benchmark to compare against.
  Date/Author: 2026-05-25 / Codex

## Outcomes & Retrospective

Implemented the requested QuantStats full-report rows in the existing portfolio metrics engine and catalog. The change stayed additive and pure: no network requests, websocket subscriptions, route behavior, browser storage, or new dependencies were introduced. Benchmark-relative rows compute for the portfolio against the primary selected benchmark and remain unavailable for standalone benchmark columns, matching the reference report behavior.

Validation completed with targeted metric and benchmark tests plus the required repository gates. Browser QA was not applicable because the production change does not alter UI interaction behavior, layout primitives, or browser flows; the existing generic Performance Metrics table renders the new catalog rows.

A follow-up runtime check found that an already-open dev page could retain the previous worker metric result because the request signature tracked only time range, selected benchmark coins, and source versions. The signature now includes `:metrics-schema-version`, currently `3`, forcing recomputation after metric schema expansions while preserving normal dedupe behavior for unchanged inputs.

## Context and Orientation

Portfolio performance metrics flow through a small pure-computation pipeline. `/hyperopen/src/hyperopen/views/portfolio/vm/performance.cljs` builds request data from the portfolio's cumulative return rows and any selected benchmark cumulative rows. `/hyperopen/src/hyperopen/portfolio/application/metrics_bridge.cljs` computes metrics synchronously in tests or posts the request to `/hyperopen/src/hyperopen/portfolio/worker.cljs` in the browser. Both paths call `/hyperopen/src/hyperopen/portfolio/metrics.cljs`, which is a facade over smaller namespaces under `/hyperopen/src/hyperopen/portfolio/metrics/`.

The metric catalog lives in `/hyperopen/src/hyperopen/portfolio/metrics/catalog.cljs`. It defines the row order, labels, value formatting kind, and descriptions. The renderer in `/hyperopen/src/hyperopen/views/portfolio/performance_metrics_view.cljs` is generic: it renders whatever grouped rows the catalog returns, hides rows where every portfolio and benchmark value formats as `"--"`, and displays benchmark values from `:benchmark-values`.

Terms used in this plan:

- "Daily rows" means a vector of maps like `{:day "2024-01-01" :time-ms 1704067200000 :return 0.01}`.
- "Cumulative rows" means the chart-friendly return series used by the current portfolio page, where each point represents cumulative percent return at a timestamp.
- "Benchmark-relative metric" means a metric requiring two aligned return series: the portfolio returns and a selected benchmark's returns.
- "Primary benchmark" means the first selected benchmark coin in the existing returns benchmark selector.

## Plan of Work

First, add tests before production changes. Extend `/hyperopen/test/hyperopen/portfolio/metrics/quantstats_parity_test.cljs` with expected values for the new pure helpers: best/worst day/month/year, average win/loss month, win rates by day/month/quarter/year, average drawdown percent/days, recovery factor, ulcer index, serenity index, beta, alpha, correlation, and Treynor ratio. Extend `/hyperopen/test/hyperopen/portfolio/metrics/builder_test.cljs` to assert that `compute-performance-metrics` produces the new keys and that `metric-rows` includes them in deterministic groups. Extend `/hyperopen/test/hyperopen/views/portfolio/vm/benchmarks_test.cljs` or `/hyperopen/test/hyperopen/views/portfolio/performance_metrics_view_test.cljs` only as needed to verify benchmark column rendering and the benchmark-relative `-` behavior.

Second, implement pure helpers in existing focused namespaces. Add period aggregation helpers to `/hyperopen/src/hyperopen/portfolio/metrics/distribution.cljs` so the same daily-row grouping can support `:day`, `:month`, `:quarter`, and `:year`. Add drawdown average, ulcer, recovery, and serenity helpers to `/hyperopen/src/hyperopen/portfolio/metrics/drawdown.cljs`. Add beta, alpha, correlation, and treynor helpers to `/hyperopen/src/hyperopen/portfolio/metrics/distribution.cljs` or another existing metrics namespace if a clearer split emerges while editing. Export those helpers from `/hyperopen/src/hyperopen/portfolio/metrics.cljs`.

Third, wire the helpers through the builder. In `/hyperopen/src/hyperopen/portfolio/metrics/builder/core.cljs`, add standalone return-shape metrics that use the strategy daily rows and strategy returns. In `/hyperopen/src/hyperopen/portfolio/metrics/builder/benchmark.cljs`, add benchmark-relative metrics from the aligned strategy and primary benchmark arrays. Because `compute-performance-metrics` already calls `benchmark/add-benchmark-relative-metrics`, this preserves both sync and worker execution paths.

Fourth, extend `/hyperopen/src/hyperopen/portfolio/metrics/catalog.cljs` with the new rows. The new rows should be grouped near their QuantStats report sections: best/worst period rows after period returns, drawdown averages with drawdown rows, recovery/ulcer/serenity with drawdown/risk rows, monthly averages and win rates in a new win-rate group, and benchmark-relative rows with the existing relative metrics. Use existing formatting kinds where possible: `:percent`, `:ratio`, and `:integer`.

Fifth, run targeted tests after each slice, then the required repo gates. No browser QA is required because this changes row data in an existing generic table and does not alter interaction behavior or visual layout primitives. The final response must still explicitly account for browser QA as not applicable and list the commands run.

## Concrete Steps

From `/hyperopen`, run narrow tests while developing:

    npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.metrics.quantstats-parity-test --test=hyperopen.portfolio.metrics.builder-test

After VM or view test updates, run:

    npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.views.portfolio.vm.benchmarks-test --test=hyperopen.views.portfolio.performance-metrics-view-test

Before completion, run the required gates:

    npm run check
    npm test
    npm run test:websocket

Expected success means each command exits with status 0. The test runner prints a summary with zero failures and zero errors.

## Validation and Acceptance

Acceptance is complete when all conditions are true:

1. `compute-performance-metrics` returns numeric values, or deterministic suppressed status when inputs are insufficient, for every requested screenshot row.
2. `metric-rows` includes deterministic catalog rows for `Best Day`, `Worst Day`, `Best Month`, `Worst Month`, `Best Year`, `Worst Year`, `Avg. Drawdown`, `Avg. Drawdown Days`, `Recovery Factor`, `Ulcer Index`, `Serenity Index`, `Avg. Up Month`, `Avg. Down Month`, `Win Days`, `Win Month`, `Win Quarter`, `Win Year`, `Beta`, `Alpha`, `Correlation`, and `Treynor Ratio`.
3. When a portfolio benchmark is selected, Beta, Alpha, Correlation, and Treynor Ratio are computed for the portfolio column against the primary selected benchmark and remain unavailable for the benchmark column, matching QuantStats report behavior.
4. Existing benchmark columns continue to show standalone benchmark values for non-relative rows such as best/worst returns, drawdown averages, recovery factor, ulcer index, serenity index, and win-rate rows.
5. Targeted tests pass, then `npm run check`, `npm test`, and `npm run test:websocket` pass.

## Idempotence and Recovery

The work is additive and source-only. Re-running tests and compile commands is safe. If a metric formula causes regressions, remove the catalog row temporarily while keeping the pure helper test isolated, then fix the helper before re-adding the row. If worker serialization causes status tokens to arrive as strings, use the existing normalization path in `/hyperopen/src/hyperopen/portfolio/application/metrics_bridge.cljs` rather than adding view-specific conversions.

No migrations, destructive commands, git pull, or git push are needed.

## Artifacts and Notes

The QuantStats source inspected during planning defines:

- Best/worst period rows via `best` and `worst`, which aggregate returns when a period is provided and then take max or min.
- Average up/down month via `avg_win` and `avg_loss` with monthly aggregation.
- Win rates as positive periods divided by non-zero periods.
- Average drawdown percent and days from drawdown-details mean values.
- Recovery factor as absolute sum of returns minus risk-free rate divided by absolute max drawdown.
- Ulcer index as root mean square drawdown over `n - 1`.
- Serenity index as sum of returns minus risk-free rate divided by ulcer index times a drawdown-CVaR pitfall term.
- Greeks as beta equals covariance(strategy, benchmark) divided by benchmark variance, and alpha equals average strategy return minus beta times average benchmark return, annualized by periods per year.
- Treynor ratio as compounded strategy return minus risk-free rate divided by beta.

## Interfaces and Dependencies

Existing dependencies are sufficient. Use ClojureScript, the current metrics namespaces, and existing CLJS test runner commands. Do not introduce a new npm or Python dependency.

At completion, the facade `/hyperopen/src/hyperopen/portfolio/metrics.cljs` should export these additional public helpers or equivalents with stable names:

- `best-period-return`
- `worst-period-return`
- `avg-win`
- `avg-loss`
- `avg-drawdown`
- `avg-drawdown-days`
- `recovery-factor`
- `ulcer-index`
- `serenity-index`
- `beta`
- `alpha`
- `correlation`
- `treynor-ratio`

The metric map from `compute-performance-metrics` should include these new keys:

- `:best-day`, `:worst-day`, `:best-month`, `:worst-month`, `:best-year`, `:worst-year`
- `:avg-drawdown`, `:avg-drawdown-days`, `:recovery-factor`, `:ulcer-index`, `:serenity-index`
- `:avg-up-month`, `:avg-down-month`, `:win-days`, `:win-month`, `:win-quarter`, `:win-year`
- `:beta`, `:alpha`, `:correlation`, `:treynor-ratio`

Revision note, 2026-05-25T16:09Z / Codex: Initial active ExecPlan created from the direct user request, existing metrics implementation, and QuantStats source mapping.
