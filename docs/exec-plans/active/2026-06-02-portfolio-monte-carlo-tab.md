# Add A Portfolio Monte Carlo Forecast Tab

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Portfolio users can read what already happened (the performance tearsheet) but have no view of the range of outcomes the same strategy could plausibly produce going forward. This change adds a `Monte Carlo` tab to the portfolio account-tabs row that runs a QuantStats-style bootstrap: it resamples the strategy's realized daily returns at random with replacement over a future horizon, thousands of times, to map the distribution of paths. This separates luck from skill across drawdowns, Sharpe, and terminal value.

A designer delivered a working React/Canvas prototype (`mc-engine.js`, `charts.js`, `monte-carlo.jsx`). This work ports that algorithm to ClojureScript, feeds it the portfolio's real realized returns instead of the prototype's synthetic series, and renders the designer's exact visual in the Replicant/Nexus architecture.

The user-visible proof: on `/portfolio` (or a spectated `/portfolio/trader/<address>`), a new `Monte Carlo` tab with a "New" badge sits after `Outcomes`. Opening it shows an intro/method header, a control strip (simulations, horizon, bust-drawdown threshold, return goal, seed, re-run), a simulated-equity-paths chart with P5–P95 and P25–P75 bands, a median line and goal/bust reference lines, a probability-of-goal / probability-of-bust rail, a terminal-outcome percentile table in dollars, and four distribution histograms (total return, max drawdown, Sharpe, annualized vol). Changing a control re-runs the simulation and updates every panel.

## Context References

Public refs:

- Direct user request on 2026-06-02: implement the designer's Monte Carlo spec as a dedicated portfolio tab; reference https://github.com/ranaroussi/quantstats/blob/main/docs/montecarlo.md for the algorithm (ported from Python).
- Designer prototype delivered as `Hyperopen Portfolio Monte Carlo.zip` (engine, canvas charts, JSX layout, screenshots).

Repo artifacts:

- `/hyperopen/src/hyperopen/portfolio/BOUNDARY.md` — portfolio metric/worker seams.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` — the ExecPlan contract.
- `/hyperopen/src/hyperopen/views/portfolio/chart_view.cljs` — the canonical chart pattern via the `:replicant/on-render` lifecycle hook, reused here for canvas.

## What Was Built

Pure, worker-safe domain code:

- `/hyperopen/src/hyperopen/portfolio/montecarlo/engine.cljs` — deterministic bootstrap engine (`mulberry32` seeded RNG, `run`, `percentile`, `dist-stats`, `histogram`). The hot loop uses `js/Float64Array`; only the capped sample paths and percentile bands are materialized for the view.
- `/hyperopen/src/hyperopen/portfolio/montecarlo/actions.cljs` — control normalization/defaults and the `set-portfolio-monte-carlo-control` / `rerun-portfolio-monte-carlo` Nexus handlers.

View model and rendering:

- `/hyperopen/src/hyperopen/views/portfolio/vm/montecarlo.cljs` — cached read model. Derives realized returns from `strategy-cumulative-rows` via `hyperopen.portfolio.metrics/daily-compounded-returns` + `returns-values`, runs the engine once per input signature, and gates on a 30-sample minimum. `portfolio-vm` exposes the cheap inputs under `:monte-carlo`.
- `/hyperopen/src/hyperopen/views/portfolio/montecarlo/{panel,controls,summary,distributions,chart,format}.cljs` — the surface; `chart.cljs` is the canvas renderer (ported `charts.js`) with a contained `requestAnimationFrame` reveal.
- `/hyperopen/src/styles/surfaces/montecarlo.css` (+ import in `main.css`) — styles scoped under `.portfolio-monte-carlo`; palette matches the app theme.

Wiring: `:monte-carlo` added to `account-info-tab-options`/aliases (`portfolio/actions.cljs`), tab order + click action + extra-tab render (`views/portfolio/account_tabs.cljs`), the "New" badge (`views/account_info/tab_actions.cljs`), and action registration (`runtime/collaborators/chart.cljs`, `schema/runtime_registration/portfolio.cljs`, `schema/contracts/action_args.cljs`).

## Acceptance Criteria

- `npm test` passes, including new engine determinism/counting and action-normalization tests.
- `npm run check` passes (lint + compile gates).
- In a running app, the `Monte Carlo` tab appears after `Outcomes` with a `New` badge; opening it renders the chart, probability rail, percentile table, and four histograms from real realized returns; changing a control re-runs and updates all panels; a portfolio with fewer than 30 realized daily returns shows the explanatory low-history state.

## Progress

- [x] M1 — Pure engine + actions with unit tests (`test/hyperopen/portfolio/montecarlo/{engine,actions}_test.cljs`).
- [x] M2 — Cached view model wired into `portfolio-vm` with the short-history gate; unit-equity engine run so live-equity ticks do not bust the cache.
- [x] M3 — Tab registered end to end (options, aliases, order, click action, extra-tab render, "New" badge, action registration + arg contract).
- [x] M4 — Views, canvas spaghetti/histogram renderers, reveal animation, and scoped CSS.
- [x] M5 — `npm test` (4127 tests, 0 failures), targeted lints, app compile, and browser verification against a spectated portfolio all green.
- [ ] Follow-up — optional hover tooltip on the equity chart (P5/median/P95 at the hovered day) and optional Web Worker offload if profiling ever shows main-thread jank.

## Surprises & Discoveries

- The app theme's `trading-green` is exactly the prototype's accent `#00d4aa`, so the design palette mapped to the app 1:1 with no reinterpretation.
- The account-tab `:render` closure is invoked only for the active tab (`account_info_view/tab-content`), so the engine runs lazily on tab open rather than on every portfolio re-render.
- Live total equity changes every websocket tick. Running the engine in unit-equity space and scaling to dollars only in the percentile table keeps the cache stable; equity is not part of the engine signature.

## Decision Log

- Annualize Sharpe/CAGR on 365 periods/year (not the prototype's 252) to match the rest of the portfolio metrics pipeline for this 24/7 market.
- Canvas (not SVG) for the equity chart: ~120 paths over hundreds of days is too many DOM nodes; canvas also matches the prototype pixel-for-pixel. Histograms use canvas too for consistency.
- No Web Worker: worst case ~2,500 paths × 365 days ≈ 0.9M iterations plus per-grid percentile sorts runs in tens of milliseconds, and the single-entry result cache prevents recompute across renders. A worker is noted as a future option only if profiling shows jank.
- Source the realized returns from the existing scope + time-range selectors so the method tag ("Bootstrap · <scope> · <range> window") is truthful and no new fetch is needed.

## Outcomes & Retrospective

The feature is implemented and verified end to end. Browser verification against a spectated portfolio rendered the full surface with real data (probability rail, percentile table in dollars, and four histograms), and switching simulations from 1,000 to 2,500 re-ran the engine and updated every panel with no console errors. Net complexity is additive and well isolated: a small pure engine, a cached read model, and a self-contained view subtree, with no changes to existing metric math. Remaining work is limited to the optional hover tooltip and a possible worker offload, neither of which affects the numbers.
