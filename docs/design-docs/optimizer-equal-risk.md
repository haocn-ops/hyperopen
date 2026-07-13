---
owner: portfolio
status: canonical
last_reviewed: 2026-07-12
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
in, which are long, which are short, and the gross leverage target. Equal Risk only
SIZES those predetermined positions so that each position's signed Euler contribution
to total portfolio volatility is as equal as possible. Stored net exposure policy is
left intact in the draft for reversible objective switching, but it is not a
constraint for `:equal-risk`.

With covariance `Sigma`, weights `w`, `m = Sigma*w`, and portfolio variance
`q = w'Sigma*w`, asset `i`'s relative risk contribution is `RRC_i = w_i*m_i / q`;
the contributions sum to one. The solver minimizes
`F(w) = 0.5 * mean_i((RRC_i - 1/n)^2)` subject to every hard constraint. Risk budgets
are fixed at `1/n`; there is no per-asset budget control.

Equal Risk never chooses assets, never flips a side, never searches sign combinations,
and is not HRP/HERC or clustering of any kind.

## Exposure semantics: gross target, net output

The Positioning control's gross target `G` (the midpoint from the canonical
exposure-policy conversion) is honored EXACTLY, not as an upper limit. Equal Risk
uses one signed-gross equality: long weights plus absolute short weights must sum to
`G`. Stored net target, net band, and raw net min/max fields are ignored by Equal
Risk in presolve, seed projection, SQP subproblems, and final result validation.

Long-only runs use the selected gross target when present and otherwise keep the
legacy fully-invested fallback. Feasibility requires `G > 0` and enough aggregate
magnitude capacity across the selected fixed-side positions. Fixed sides, bounds,
locks, shortability, and turnover remain hard constraints with specific
`:equal-risk-*` presolve reasons and human-readable messages in the infeasible
banner.

## Why exact equality may be impossible

The exact gross target, per-asset caps, locks, shortability, and the turnover budget
take priority over exact risk parity. A one-long/one-short book now has one free
allocation dimension after gross normalization, so covariance can choose the
long/short split. A single selected position, or a book whose caps/locks/turnover
constraints consume the remaining degrees of freedom, can still leave no adjustable
composition. The result is then the closest achievable balance and is labeled
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
through zero — one row per asset with the recommended share as a neutral purple
bar capped by a purple recommended dot, the current share as a gray outlined
circle joined to the bar end by a dashed connector, a continuous dashed
**purple** equal-target line (`--optimizer-target`, defined in the scoped
optimizer palette) with per-row target ticks, and purple Target / sign-colored
Deviation columns (above target = red, below = green). Between the header and
the plot sits a five-cell KPI strip: Equal target (purple), Status
(exact/approximate/not-converged), RMS and Max deviation (magnitude-toned:
green ≤20% of the target, amber ≤50%, red beyond), and a neutral Negative
contributors count. A READING THIS footnote explains that Equal Risk balances
risk ownership but does not minimize total volatility and names hedges. Rows
are capped at 16 selected by worst |deviation| (so the rows that
explain an Approximate verdict always survive), then displayed in signed
share descending order with an honest remainder line. No pie, donut, stacked,
or absolute form is ever used: those all misrepresent negative contributions
and >100% positive shares. No efficient frontier is computed, drawn, or
implied for this objective anywhere. The card carries four visible labels over
stable DOM-state identities: RISK BALANCE | DIVERSIFICATION | CORRELATION
DRIVERS | RISK / RETURN. Visually-hidden radio inputs are toggled by scoped
`:has()` CSS, so tab state remains in the DOM rather than application state.

DIVERSIFICATION first shows current and recommended books on one absolute
annualized-volatility scale. For each book it compares the modeled volatility
`sqrt(w' Sigma w)` with the zero-correlation benchmark
`sqrt(sum_i w_i^2 Sigma_ii)` and the all-held-position-profit-and-loss-streams
moving-together benchmark `sum_i abs(w_i) sqrt(Sigma_ii)`. The displayed
diversification benefit is the reduction from the moving-together benchmark;
the separate signed modeled-minus-zero-correlation value explains whether
correlations amplify or offset risk versus independent positions. This makes
it explicit that positive cross-covariance can coexist with genuine
diversification versus perfect comovement.

Below that summary, `risk_breakdown_panel.cljs` attributes each charted asset's
signed net share to an always-positive **Own-variance term** plus a signed
**Cross-covariance effect**. The own term runs from zero to its endpoint; the
cross-covariance segment begins at that endpoint and moves left when it offsets
risk or right when it amplifies risk, ending at the purple net marker. The copy
calls this final-weight attribution and explicitly says it is not the causal
impact of removing the asset, which would require a new optimization.

CORRELATION DRIVERS (`risk_correlation_panel.cljs`) shows the correlation heatmap
with a POSITION P&L / UNDERLYING RETURNS toggle (position-P&L correlation =
sideᵢ·sideⱼ·underlying correlation; both matrices pre-render and a second
DOM-state radio pair swaps them; every cell's native tooltip carries both
numbers plus a Diversifying/Amplifying/Neutral verdict). In POSITION P&L mode,
negative/offsetting cells use green and positive/amplifying cells use red;
underlying-return mode keeps conventional correlation-sign colors. Which asset is
selected IS app state (`ui-selected-risk-instrument-path`, set by
`set-portfolio-optimizer-selected-risk-instrument` from Allocation-row
clicks — the one selection that must route data across cards); the fallback
is the most negative net contributor, else the largest |net|. The heatmap's
data comes from the persisted `:risk-structure` payload section (the
covariance itself is never persisted), capped at the 24 largest |net share|
positions with an honest remainder line, ordered by signed net share to
match the balance chart. Allocation rows gain a "P&L corr. to portfolio"
line (Corr(sᵢrᵢ, r_p)) with explicit offsets/amplifies text and an accent ring
on the effective selection.
RISK / RETURN (`risk_return_context.cljs`) shows
current/recommended/standalone points with an explicit
returns-are-context-only note. Persisted results that predate
`:risk-structure` keep the original two tabs. The "Why this risk allocation"
card renders four icon facts (book shape, equal target, a CORRELATION VIEW
label that activates the correlation tab — falling back to the largest risk
contributor on pre-structure results — and allocation freedom with its
binding-cap count); the binding-constraint enumeration lives in the
trust-diagnostics rail. Workbench scenes
(`workbench/scenes/optimize/equal_risk_scenes.cljs` and
`equal_risk_correlation_scenes.cljs`) pin the designer-parity books (the
correlation one computes its payload sections through the real domain math,
with an interactive selection store) plus the
hedged/exact/capped/degenerate/persisted states;
`tools/playwright/test/optimizer-risk-correlation-workbench.spec.mjs` covers
the tab/toggle switching, current/recommended benchmark comparison, additive
bridge endpoints, semantic copy, and click-to-select flow deterministically.

The KPI strip (`scenario_kpi_strip.cljs`) swaps the Sharpe tile for RISK
BALANCE (current → recommended max deviation in points) and renders the
volatility/return deltas neutrally — direction is not success for a balance
objective. The confidence rail (`equal_risk_confidence_rail.cljs`) shows
Equal-Risk Fit, **Allocation Freedom**, Solution Stability (agreement of the
deterministic starts), and the solver's real stop reason. Allocation freedom
comes from the payload (`:equal-risk-solver :allocation-freedom`): each book's
free selected members share one gross equality before binding caps are counted.
A one-long/one-short book therefore has one free allocation dimension; a single
selected position remains fully determined. Fully determined cases say so ("Equal
Risk evaluates the resulting balance but cannot improve it") instead of implying the
optimizer chose the weights.
The payload also carries `:current-risk-contributions` (same summary over the
current aligned book) and scalar `:target-diversification` plus optional
`:current-diversification` maps inside `:risk-structure`. These benchmark maps
persist only finite scalar values; covariance is never persisted. Views degrade
to em-dashes on persisted results that predate these fields.

## Solver (for maintainers)

Deterministic sequential quadratic programming through the existing injected QP
adapter (quadprog synchronously, OSQP in the worker): four deterministic initializers
(equal-notional, inverse-volatility, inverse-variance, current-weights — each
projected onto the one-gross-equation feasible set by a QP), damped-BFGS model Hessian, Armijo
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
