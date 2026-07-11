---
owner: portfolio
status: canonical
last_reviewed: 2026-07-11
review_cycle_days: 90
source_of_truth: true
---

# Optimizer Equal Risk Objective

## Purpose

Defines what the `Equal Risk` allocation objective (`:equal-risk`) optimizes, how its
exposure semantics work, why its results are labeled exact or approximate, and what
users should expect from the risk-contribution readout. The implementation lives in
`/hyperopen/src/hyperopen/portfolio/optimizer/domain/risk_contributions.cljs` (contribution
math and gradients, with the formulas in docstrings),
`/hyperopen/src/hyperopen/portfolio/optimizer/domain/equal_risk.cljs` (targets, books,
presolve, tolerances), and
`/hyperopen/src/hyperopen/portfolio/optimizer/application/engine/equal_risk_solve.cljs`
(the sequential solver).

## What Equal Risk optimizes

The user has already made every directional decision before the run: which assets are
in, which are long, which are short, the gross leverage target, and the net bias
target. Equal Risk only SIZES those predetermined positions so that each position's
signed Euler contribution to total portfolio volatility is as equal as possible.

With covariance `Sigma`, weights `w`, `m = Sigma*w`, and portfolio variance
`q = w'Sigma*w`, asset `i`'s relative risk contribution is `RRC_i = w_i*m_i / q`;
the contributions sum to one. The solver minimizes
`F(w) = 0.5 * mean_i((RRC_i - 1/n)^2)` subject to every hard constraint. Risk budgets
are fixed at `1/n`; there is no per-asset budget control.

Equal Risk never chooses assets, never flips a side, never searches sign combinations,
and is not HRP/HERC or clustering of any kind.

## Exposure semantics: gross and net are targets

The Positioning control's target point (gross target `G`, net target `N` — the band
midpoints from the canonical exposure-policy conversion) is honored EXACTLY, not as an
upper limit: the long book must sum to `(G + N) / 2` and the short book to
`(G - N) / 2`. Long-only runs pin `G = N = 1`. Feasibility requires `G > 0` and
`|N| <= G`; requests violating this (or whose sides/caps/locks make a book target
unreachable) fail before the solver runs, with specific `:equal-risk-*` presolve
reasons and human-readable messages in the infeasible banner.

## Why exact equality may be impossible

Exact exposure targets, per-asset caps, locks, shortability, and the turnover budget
take priority over exact risk parity. A two-asset long/short book at `G = 2, N = -0.5`
pins the weights at `[0.75, -1.25]` outright — no freedom remains to balance
contributions. The result is then the closest achievable balance and is labeled
truthfully:

- `exact` — the solver converged AND both the max and rms deviation of realized
  contributions from `1/n` are within 1% of the `1/n` target share.
- `approximate` — the solver converged but material deviation remains. UI copy says
  "best solution found under the selected constraints"; it never claims exact parity
  was proven impossible.
- `not-converged` — the solver hit its iteration limit; the best feasible portfolio
  found is still published, with a visible warning.

## Why contributions are signed

Contributions are signed Euler decompositions, never absolute values. A short position
in a book that is net long typically has a POSITIVE contribution (it adds risk of its
own) but can have a NEGATIVE one when it hedges the book — reducing total volatility.
Negative contributions are preserved and displayed with a "hedges the book" note; the
contributions still sum to 100% of portfolio volatility.

## Expected returns never move the weights

Equal Risk is covariance-only: the selected risk model's covariance matrix fully
determines the sizing. Expected-return forecasts, Black-Litterman views, and funding
carry affect only display metrics (portfolio expected return / Sharpe). If the return
model is invalid the run still solves; return-based display metrics are omitted with a
warning instead of being fabricated. There is no efficient frontier for this objective
— the results page replaces the frontier chart with the risk-contribution table.

## Solver (for maintainers)

Deterministic sequential quadratic programming through the existing injected QP
adapter (quadprog synchronously, OSQP in the worker): four deterministic initializers
(equal-notional, inverse-volatility, inverse-variance, current-weights — each
projected onto the feasible set by a QP), damped-BFGS model Hessian, Armijo
backtracking along always-feasible segments, per-iteration progress events, and an
early exit once a start reaches `exact` quality. Turnover and the gross cap ride the
existing split-variable L1 channel. All tolerances are centralized and documented in
`domain/equal_risk.cljs tolerances`. Degenerate portfolio variance (a perfectly
hedged singular covariance, or an all-zero matrix) fails explicitly with
`:equal-risk-degenerate-variance` rather than dividing by an epsilon.

## Key tests

- `hyperopen.portfolio.optimizer.domain.risk-contributions-test` (math, analytic
  gradient vs central differences, degeneracy, sign preservation)
- `hyperopen.portfolio.optimizer.domain.equal-risk-test` (targets, books, presolve,
  seeds, plan shape)
- `hyperopen.portfolio.optimizer.application.engine.equal-risk-solve-test` (the
  16-case mathematical battery: symmetries, invariances, caps, locks, turnover,
  determinism)
- `hyperopen.portfolio.optimizer.application.engine-equal-risk-test` (end-to-end
  engine + payload + return-model bypass + no-frontier-sweep)
- `hyperopen.portfolio.optimizer.contracts-equal-risk-test` (specs, migration
  round-trip, worker-wire codec)
