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

## Results page

The primary evidence the objective worked is the **Risk Contribution Balance
card** (`views/portfolio/optimize/risk_contributions_card.cljs`, built to the
designer's 2026-07-11 spec): a horizontal diverging chart on a signed axis
through zero — one row per asset with the recommended share as a sign-colored
bar capped by a green recommended dot, the current share as a gray outlined
circle joined to the bar end by a dashed connector, a continuous dashed
**purple** equal-target line (`--optimizer-target`, defined in the scoped
optimizer palette) with per-row target ticks, and purple Target / sign-colored
Deviation columns (above target = red, below = green). Between the header and
the plot sits a five-cell KPI strip: Equal target (purple), Status
(exact/approximate/not-converged), RMS and Max deviation (magnitude-toned:
green ≤20% of the target, amber ≤50%, red beyond), and Negative contributors
(red when any). A READING THIS footnote explains the encoding and names
hedges. Rows are capped at 16 selected by worst |deviation| (so the rows that
explain an Approximate verdict always survive), then displayed in signed
share descending order with an honest remainder line. No pie, donut, stacked,
or absolute form is ever used: those all misrepresent negative contributions
and >100% positive shares. No efficient frontier is computed, drawn, or
implied for this objective anywhere; the card's second tab, RISK / RETURN
(`risk_return_context.cljs`), shows current/recommended/standalone points with
an explicit returns-are-context-only note. The tab switcher is a pair of
visually-hidden radio inputs toggled by scoped `:has()` CSS — tab state lives
in the DOM, never in app state. The "Why this risk allocation" card renders
four icon facts (book shape, equal target, largest risk contributor over the
full universe, allocation freedom with its binding-cap count); the
binding-constraint enumeration lives in the trust-diagnostics rail. Workbench
scenes (`workbench/scenes/optimize/equal_risk_scenes.cljs`) pin the
designer-parity book plus the hedged/exact/capped/persisted states.

The KPI strip (`scenario_kpi_strip.cljs`) swaps the Sharpe tile for RISK
BALANCE (current → recommended max deviation in points) and renders the
volatility/return deltas neutrally — direction is not success for a balance
objective. The confidence rail (`equal_risk_confidence_rail.cljs`) shows
Equal-Risk Fit, **Allocation Freedom**, Solution Stability (agreement of the
deterministic starts), and the solver's real stop reason. Allocation freedom
comes from the payload (`:equal-risk-solver :allocation-freedom`): each book's
sum equality pins its last unlocked member, so a book with one asset has zero
freedom — a one-long/one-short book is **fully determined** by the gross/net
targets, and the page says so ("Equal Risk evaluates the resulting balance
but cannot improve it") instead of implying the optimizer chose the weights.
The payload also carries `:current-risk-contributions` (same summary over the
current aligned book) for the current-vs-recommended comparison; views degrade
to em-dashes on persisted results that predate these fields.

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
