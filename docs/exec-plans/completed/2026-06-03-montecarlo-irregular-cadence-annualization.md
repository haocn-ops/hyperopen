# Annualize Monte Carlo On Elapsed Time, Not Assumed Trading Days

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The Monte Carlo tab's Sharpe, volatility and CAGR distributions disagree with the numbers on the same vault's Performance Metrics tearsheet. Example: vault `0xac26cf5f3c46b5e102048c65b977d2551b72a9c7` shows Sharpe **1.20** on the tearsheet, but the Monte Carlo "Sharpe ratio" card centres near **3.37**. The cause is a wrong assumption in the Monte Carlo engine: it treats every realized data point as one consecutive trading day and annualizes by `√365`. Vault history is not daily — it is sparse and irregular (gaps of many days between snapshots). For this vault the "2-year" window holds roughly 63 data points, so the engine labels the chart "63 trading days" and annualizes as if 63 days had elapsed, when in reality those 63 points span the better part of two years. Compressing ~2 years of variance into "63 days" inflates the annualized Sharpe/vol/CAGR by roughly `√(365 / points-per-year)`.

The Performance Metrics pipeline already solves this. Its "core" metrics (Sharpe, volatility, CAGR, Sortino) are computed by *interval* math that annualizes by **actual elapsed time**: each consecutive pair of cumulative-return rows becomes an interval carrying its real duration `dt-years` and its `log-return`, and the annualized drift and variance are rates per year (`Σlog-return / Σdt-years`, etc.). That is what produces Sharpe 1.20.

After this change, the Monte Carlo engine consumes those same irregular intervals and computes Sharpe, volatility and CAGR with the same elapsed-time annualization, so the Monte Carlo metric distributions agree with the tearsheet (the shuffle-mode median Sharpe lands on ~1.20, not ~3.37). The chart and labels stop saying "63 trading days" and instead express the real elapsed span (e.g. "~1.8 years"), and the Forecast horizon is expressed in calendar time (months/years) rather than a count of sparse points. The drawdown distribution and the equity-path fan are unchanged in spirit (drawdown is dimensionless and order-dependent; elapsed time does not change it).

How to see it working: run the app (`npm run dev`, open `http://localhost:8080`), spectate `/vaults/0xac26cf5f3c46b5e102048c65b977d2551b72a9c7`, open `Monte Carlo` (Sequence risk / shuffle). The "Sharpe ratio" card median should sit at the tearsheet's Sharpe, the "CAGR" spike should equal the tearsheet CAGR, and the chart should describe the realized span in years, not a day count.

## Context References

Public refs:

- Direct user request on 2026-06-03: the Monte Carlo metric distributions differ from Performance Metrics because the engine assumes each point is a trading day; vault points are not daily and have gaps; examine how Performance Metrics handles it and apply the same to Monte Carlo; a "2-year period showing 63 trading days" is really ~2 years across 63 points. Make a plan, then implement.

Repo artifacts:

- `/hyperopen/docs/exec-plans/completed/2026-06-02-montecarlo-quantstats-shuffle-method.md` — the Monte Carlo method/toggle this builds on.
- `/hyperopen/src/hyperopen/portfolio/metrics/returns.cljs` — the irregular-interval math (`interval-drift-rate`, `interval-variance-rate`, `volatility-ann-irregular`, `sharpe-irregular`, `interval-cagr`) that the tearsheet's core metrics use; this plan reuses the exact same formulas.
- `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs` — `cumulative-rows->irregular-intervals` builds the `{:simple-return :log-return :dt-years}` intervals from cumulative-percent rows.
- `/hyperopen/src/hyperopen/portfolio/metrics/builder.cljs` / `builder/core.cljs` — shows the tearsheet computing core metrics from `intervals` (not from a `periods-per-year` assumption).

Local scratch refs (non-authoritative):

- None.

## Context and Orientation

The reader is assumed to know nothing about this repository. It is a ClojureScript single-page app (shadow-cljs); UI is data-described hiccup rendered from cached view models.

Definitions:

- "Irregular interval": one step between two consecutive cumulative-return snapshots, carrying its real duration in years (`dt-years`), its `simple-return` (`curr/prev − 1`), and its `log-return` (`ln(curr/prev)`). Built by `hyperopen.portfolio.metrics.history/cumulative-rows->irregular-intervals`.
- "Elapsed-time annualization": annualize by real time, not by a count of periods. Annualized drift `μ = Σ log-return / Σ dt-years` (log-return per year). Annualized variance rate `σ² = (Σ (log-return²/dt-years) − (Σlog-return)²/(Σdt-years)) / (n−1)` (this is the one-pass algebraic equivalent of `interval-variance-rate` in `returns.cljs`, which sums `residual²/dt-years` with `residual = log-return − μ·dt-years`). Annualized volatility `σ = √σ²`. Annualized Sharpe `= (μ − ln(1+rf)) / σ`. Annualized CAGR `= exp(μ) − 1`. With `rf = 0`, the Sharpe simplifies to `μ/σ`.
- "Trading-day assumption (the bug)": the current engine computes `mean(simple)/std(simple)·√ppy` with `ppy = 365`, and CAGR `equity^(ppy/H) − 1` with `H` = point count. Both assume one point = one day.

Current Monte Carlo layout (unchanged structurally by this plan):

- `/hyperopen/src/hyperopen/portfolio/montecarlo/engine.cljs` — pure engine. `run` takes `:returns` (simple returns), `:method` (`:shuffle`/`:bootstrap`), `:horizon`, etc., and returns `:terminal`/`:maxdd`/`:sharpe`/`:cagr`/`:vol` distribution maps, `:band`, `:draw-paths`, `:times`, `:meta`, `:bust-prob`, `:goal-prob`.
- `/hyperopen/src/hyperopen/views/portfolio/vm/montecarlo.cljs` and `/hyperopen/src/hyperopen/views/vaults/detail_vm/montecarlo.cljs` — read models. They currently derive simple returns via `metrics/daily-compounded-returns` → `metrics/returns-values` and pass `:returns` to the engine.
- `/hyperopen/src/hyperopen/portfolio/montecarlo/{controls,panel,summary,distributions,chart,format}.cljs` — shared views. `controls` has the horizon segmented control (currently `[30 90 180 365]` days). `chart` labels the x-axis `"<t>d"` and reads `H` from `:meta :horizon`.

## Plan of Work

The engine switches from "a list of daily returns annualized by √365" to "a list of irregular intervals annualized by elapsed time", reusing the tearsheet's exact formulas. Four milestones.

### Milestone M1 — Engine consumes intervals and annualizes by elapsed time

In `engine.cljs`, change `run` to accept `:intervals` — a seq of `{:simple-return :log-return :dt-years}`. Keep a backward-compatible shim: if `:intervals` is absent but `:returns` is present, synthesize intervals with `dt-years = 1/365` and `log-return = ln(1+r)` (this preserves a daily interpretation for any legacy caller and for the existing engine tests). Extract three `js/Float64Array`s (`simp`, `logr`, `dt`); `m` = count. Compute `realized-years = Σ dt` and `ppy-eff = m / realized-years`.

In the per-path hot loop, in addition to the existing equity/peak/worst-drawdown accumulation over `simp`, accumulate `A = Σ log²/dt`, `B = Σ log`, `C = Σ dt`, and the step count `k`. After the loop, per path:

- `terminal = equity − 1` (unchanged: product of `(1+simp)`).
- `maxdd = worst-dd` (unchanged).
- `drift = B/C`; `var-rate = max(0, (A − B²/C)/(k−1))`; `vol = √var-rate`; `cagr = exp(drift) − 1`; `sharpe-full = (drift − ln(1+rf)) / vol`.
- Shuffle Sharpe stays the QuantStats-style leave-one-out, generalized to intervals: drop the first interval of the permutation (`perm[0]`) and recompute from `A − log0²/dt0`, `B − log0`, `C − dt0`, `k−1`. Bootstrap Sharpe uses `sharpe-full`.

Add `:rf` (default 0), `:total-years`/`:ppy-eff` and `:span-years` to `:meta` (`:span-years` = realized-years for shuffle; for bootstrap = `H · realized-years/m`, the calendar time the forecast represents). Keep `:horizon` in `:meta` equal to the step count `H` for the chart grid. Bootstrap still resamples `H` intervals with replacement; the caller now derives `H` from a target calendar span (M2).

Acceptance for M1: a new engine test builds intervals from a known cumulative series, runs `:shuffle`, and asserts the engine's full-set per-path Sharpe equals `returns/sharpe-irregular` on the same intervals and the per-path CAGR equals `returns/interval-cagr` (to a tight tolerance). The existing `:returns`-based tests keep passing via the shim. Run `npx shadow-cljs compile test` and `node out/test.js`; expect 0 failures.

### Milestone M2 — View models feed intervals and a calendar-time horizon

In both read models, replace the `daily-compounded-returns`/`returns-values` derivation with `history/cumulative-rows->irregular-intervals` over the strategy cumulative rows; `sample-size` becomes the interval count; gate on the same 30-interval minimum. Compute `total-years = Σ dt-years`. For `:shuffle`, pass `:intervals` with no horizon. For `:bootstrap`, convert the (now calendar-time) horizon control to `target-years`, clamp to `total-years` (never forecast more calendar time than observed — the same anti-inflation principle as before), compute `steps = max(1, round(target-years · ppy-eff))`, and pass `:intervals` + `:horizon steps`. Include `total-years`/`ppy-eff`/`target-years` in the engine signature and expose them on the model for the views and method tag.

Acceptance for M2: `npm run check` compiles; the model exposes `:total-years` and a calendar `:span-years`.

### Milestone M3 — Controls and labels speak calendar time

In `actions.cljs`, change `horizon-options` to months `[3 6 12 24]` with `normalize-horizon` validating that set (legacy stored day values fall back to the default). In `controls.cljs`, label them `3M/6M/1Y/2Y`, disable options whose months exceed the realized span, and make the run-status read calendar time (`"63 intervals · ~1.8y · reshuffled"` / `"1,000 paths · ~1.0y"`). In `chart.cljs`, label the x-axis in time using `:meta :span-years` (format `<n>mo` under a year, else `<n.n>y`) instead of `"<t>d"`. In `panel.cljs`/`summary.cljs`, update the chart title ("Reshuffled equity paths · 63 snapshots over ~1.8 years" / "Simulated equity paths · ~1.0-year forecast"), the terminal-table caption ("Terminal outcome · ~1.0 year"), the realized-outcome card (note the realized span), and the footnotes (state that metrics are annualized by elapsed time, matching the tearsheet; drop "trading days" language).

Acceptance for M3: browser check — the Sharpe-card median equals the tearsheet Sharpe; the chart and controls read in months/years; no "trading days" text remains.

### Milestone M4 — Validate end to end

`npm run check`, `npm test`, `npm run test:websocket` green; browser-verify the named vault (Sequence-risk Sharpe median ≈ tearsheet Sharpe; CAGR spike ≈ tearsheet CAGR) and the Forecast mode (calendar horizon, clamped). Update living sections; move to `completed/`.

## Concrete Steps

From the repo root (`node_modules` must be present; in a worktree `ln -s ../../../node_modules node_modules`):

    npx shadow-cljs compile test
    node out/test.js
    npm run check
    npm test
    npm run test:websocket
    npm run dev   # then open http://localhost:8080 and spectate /vaults/<address>

## Validation and Acceptance

- Engine test: the per-path full-set irregular Sharpe/CAGR equal `returns/sharpe-irregular` / `returns/interval-cagr` on the same intervals; a series whose points span N years annualizes by N years (not by `count/365`). New test fails before the change (the old engine has no interval path) and passes after.
- Behavioral: on `/vaults/0xac26…`, the Monte Carlo "Sharpe ratio" median equals the tearsheet "Sharpe" (≈1.20, not ≈3.37) and "CAGR" equals the tearsheet CAGR; the chart x-axis and the horizon control read in months/years; the footnote states elapsed-time annualization.
- `npm run check`, `npm test`, and `npm run test:websocket` are green.

## Idempotence and Recovery

Edits are additive and reversible. The `:returns` compatibility shim means any caller still passing simple returns keeps the old daily behavior, so the change can land without breaking unrelated callers; the view models are the only callers switched to `:intervals`. No data migration; horizon control values that were stored as days normalize to the new default.

## Interfaces and Dependencies

No new libraries. `engine/run` gains `:intervals` (`[{:simple-return :log-return :dt-years} …]`) and `:rf`; `:meta` gains `:total-years`, `:ppy-eff`, `:span-years`. The view models require `hyperopen.portfolio.metrics.history` for `cumulative-rows->irregular-intervals`; the engine test requires `hyperopen.portfolio.metrics.returns` to assert consistency. The Nexus actions/schema are unchanged (the control-args spec accepts any keyword control).

## Progress

- [x] (2026-06-03) Research: read the irregular-interval math (`returns.cljs`), `cumulative-rows->irregular-intervals` (`history.cljs`), and the tearsheet builder (`builder/core.cljs`); confirmed the tearsheet Sharpe is `sharpe-irregular` (elapsed-time), while the Monte Carlo engine assumes one point = one day and annualizes by √365. Derived the one-pass `A/B/C` equivalent of `interval-variance-rate`.
- [x] (2026-06-03) ExecPlan written.
- [x] (2026-06-03) M1 — Engine consumes `:intervals` (with a `:returns` daily shim), accumulates `A/B/C` one-pass, annualizes Sharpe/vol/CAGR by elapsed time; shuffle Sharpe leave-one-out drops the first interval; `:meta` gains `:total-years`/`:ppy-eff`/`:span-years`. New tests assert the engine matches `returns/sharpe-irregular`/`interval-cagr`/`volatility-ann-irregular` and that a 2-year series annualizes by 2 years (not count/365). All existing tests pass via the shim.
- [x] (2026-06-03) M2 — Both read models derive `cumulative-rows->irregular-intervals`, compute `total-years`/`ppy-eff`, convert the calendar horizon to a clamped step count, and pass `:intervals` to the engine.
- [x] (2026-06-03) M3 — Horizon control is months `[3 6 12 24]` (3M/6M/1Y/2Y) with out-of-range spans disabled; chart x-axis, run-status, chart title, terminal caption and footnotes all read in calendar time and state the elapsed-time annualization; `years-label` lives in `format.cljs`.
- [x] (2026-06-03) M4 — `npm run check` EXIT 0, `npm test` 4137, `npm run test:websocket` 527; browser-verified `0xac26…`: Sequence-risk Sharpe median **1.22** (tearsheet **1.20**, was ~3.37), CAGR **+310%** (tearsheet ~+301%), chart reads "63 snapshots over ~1.1y"; Forecast mode shows months with **2Y disabled** for ~1.1y history. No console errors.

## Surprises & Discoveries

- Observation: The tearsheet's core Sharpe/vol/CAGR never use `periods-per-year`; they use interval math weighted by `dt-years`. `periods-per-year` (default 252) only feeds the "daily/smart" metrics (smart-sharpe, omega, PSR) that are separately gated on daily coverage. So matching the tearsheet means reusing the interval math, not picking a better `periods-per-year`.
  Evidence: `builder/core.cljs` `build-core-metric-context` calls `returns/sharpe-irregular`/`interval-cagr`/`volatility-ann-irregular` on `intervals`; `returns.cljs` defines them.

- Observation: The fix lands on the named vault. Before, the Monte Carlo Sharpe card centred near 3.37 against a tearsheet Sharpe of 1.20 (a ~√(365/ppy-eff) inflation). After, the shuffle Sharpe median is 1.22 and the CAGR spike is +310% (tearsheet ~+301%), because the engine reuses the tearsheet's interval math. The realized history is ~1.1 years across 63 intervals — the chart now says "63 snapshots over ~1.1y" rather than "63 trading days", and the 2Y forecast option is disabled because the realized span is under two years.
  Evidence: browser inspection of `[data-role$="-dist-sharpe"]` (median 1.22, P5 1.10 · P95 1.37) and the chart title / run-status / terminal caption.

- Observation: `interval-variance-rate` (a two-pass `Σ residual²/dt`) equals the one-pass `(Σlog²/dt − (Σlog)²/Σdt)/(n−1)`, which lets the hot loop accumulate three scalars instead of building an intervals vector per path. For the genuinely-varying inputs here there is no catastrophic cancellation, and a unit test pins the engine output to `returns/volatility-ann-irregular` to 1e-6.
  Evidence: `engine-sharpe-vol-cagr-match-tearsheet-irregular-test`.

## Decision Log

- Decision: Reuse the tearsheet's elapsed-time interval math (one-pass `A/B/C`) inside the engine rather than passing a derived `periods-per-year` and keeping `mean/std·√ppy`.
  Rationale: Only the interval math matches the tearsheet exactly under irregular spacing; an averaged `periods-per-year` would still diverge when gaps are uneven. The one-pass form keeps the hot loop on typed arrays. A test asserts equality with `returns/sharpe-irregular` to lock consistency.
  Date/Author: 2026-06-03, implementer.

- Decision: Express the Forecast horizon in calendar time (months) and clamp to the realized span; keep the engine resampling a step count derived via `ppy-eff`.
  Rationale: "Days" is meaningless for sparse points (the user's core complaint). Calendar time is honest and matches the tearsheet framing; clamping to the realized span preserves the earlier anti-inflation guarantee. Deriving a step count keeps the band/path machinery unchanged.
  Date/Author: 2026-06-03, implementer.

## Outcomes & Retrospective

Delivered and verified. The Monte Carlo engine now consumes the same irregular intervals as the tearsheet and annualizes Sharpe, volatility and CAGR by real elapsed time, so the Monte Carlo metric distributions agree with the Performance Metrics tab. On vault `0xac26cf5f3c46b5e102048c65b977d2551b72a9c7` the Sequence-risk Sharpe card moved from a centre of ~3.37 to a median of **1.22** against the tearsheet's **1.20**, and CAGR reads **+310%** against the tearsheet's ~+301%. The "trading days" framing is gone everywhere: the chart, run-status, chart title, terminal caption and footnotes express calendar time ("63 snapshots over ~1.1y"), and the Forecast horizon is a calendar span (3M/6M/1Y/2Y) clamped to the realized history (2Y disabled here because the vault has ~1.1 years of data).

Validation: `npm test` (4137, with three new engine tests pinning the output to `returns/sharpe-irregular`/`interval-cagr`/`volatility-ann-irregular` and to a 2-year elapsed-time CAGR), `npm run check` (EXIT 0, all six builds, docs guardrails, 0 warnings), `npm run test:websocket` (527), and a browser pass on the named vault with no console errors.

Complexity: a small, well-contained net change. The engine swapped one accumulator pair (`Σr`, `Σr²`) for a triplet (`Σlog²/dt`, `Σlog`, `Σdt`) and replaced the `√ppy` annualization with the tearsheet's elapsed-time formulas; the two view models swapped `daily-compounded-returns` for `cumulative-rows->irregular-intervals` and added a months→steps conversion; the views relabel in calendar time. The change removes a real correctness bug (a units mismatch that inflated every annualized Monte Carlo metric for sparse vault history) and makes the two surfaces consistent. The `:returns` shim keeps the engine usable with plain daily returns, so the surface area of the change is limited to the two callers that were switched to intervals.
