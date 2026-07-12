# Optimizer Volatility Intuition and Leverage Risk

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The optimizer's recommendation tab reports annualized volatility (for a levered
crypto book this is routinely several hundred percent), and nothing on the page
translates that number into something a person can feel. A user staring at
"411.82% annualized" cannot answer: what does a normal day look like? A normal
week? A month? And when the target uses meaningful leverage, nothing warns them
that at these volatility levels the *median* compounded outcome collapses far
below the arithmetic expected return, or how likely a ruinous drawdown is.

After this change, the recommendation tab answers those questions in three
places, all derived from numbers the run already produced and all explanatory —
optimization weights, constraints, expected returns, covariance estimation, and
frontier construction are untouched:

1. A **Volatility intuition** card near the top of the recommendation right
   rail translating the annualized volatility of the target (toggleable to the
   current book) into daily / weekly / monthly one-standard-deviation move
   scales, with compact magnitude bars, the scaling convention stated
   ("365 calendar days"), severity messaging for elevated → extreme volatility,
   and an explicit boundary note when a ±1σ monthly range crosses −100%.
2. The **Current and Target frontier tooltips** gain a divider plus
   daily / weekly / monthly 1σ rows so hovering the two decision-relevant
   markers gives the same short-horizon read.
3. A one-line **insight strip** under the frontier chart when target
   volatility is very high (≥100% annualized), stating the daily 1σ scale and
   that path dependency and liquidation risk can dominate the average return.
4. A **Leverage risk** card in the right rail, shown only when the target is
   actually levered (gross exposure ≥ 2x) or extremely volatile (≥100%
   annualized), giving modeled one-year outcomes on account equity: median and
   5th-percentile ending equity (current vs target), the probability of ending
   the year down 50% or more, and the probability of *touching* a 50% drawdown
   at any point during the year — under an explicitly labeled lognormal model
   with its limitations stated on the card.

A developer can see it working by running the ui-workbench scenes added by
this plan (`resources/public/ui-workbench.html?id=hyperopen.workbench.scenes.optimize.volatility-intuition-scenes`
on the shadow-cljs dev server) or by running any optimizer scenario to a solved
result and opening the Recommendation tab.

Design provenance: direct user request on 2026-07-12 with two designer mockups.
The user explicitly kept (a) the volatility-intuition card with
daily/weekly/monthly bars and (b) the one-year modeled leverage impact
component, renamed "Leverage risk" and surfaced only under higher leverage; the
rest of the mockups' additions (compounding-drag KPI tile, funding/execution
cost tiles inside the leverage card, standalone-overlay changes) were judged
"busy" and are deliberately out of scope.

## Context References

Public refs:

- Direct user request on 2026-07-12 (designer prompt + two mockups) captured in
  this plan's Purpose section.

Repo artifacts:

- `/hyperopen/docs/exec-plans/active/2026-07-04-optimizer-right-rail-decision-aid.md`
  established the right-rail confidence/trust card idiom this plan extends.
- `/hyperopen/docs/exec-plans/active/2026-07-11-optimizer-equal-risk-results-redesign.md`
  established the DOM-state radio + `:has()` tab pattern reused for the
  Current/Target toggle.
- `/hyperopen/src/hyperopen/portfolio/optimizer/domain/risk.cljs` and
  `/hyperopen/src/hyperopen/portfolio/optimizer/domain/returns.cljs` define the
  annualization convention (365 periods per year) this feature must match.

## The annualization convention (critical consistency requirement)

The optimizer works on daily crypto returns and annualizes with **365 calendar
days per year**: `default-periods-per-year` is `365` in both
`src/hyperopen/portfolio/optimizer/domain/risk.cljs` (covariance is scaled by
`periods-per-year`) and `src/hyperopen/portfolio/optimizer/domain/returns.cljs`
(mean returns likewise). The optimizer request never overrides the non-funding
`:periods-per-year`, so every displayed annualized volatility in the optimizer
is a √365-scaled daily volatility.

Therefore every shorter-horizon value this feature displays must be derived
from the displayed annualized volatility with the same basis:

    daily   = annualized / sqrt(365)
    weekly  = annualized * sqrt(7 / 365)     (7 calendar days)
    monthly = annualized * sqrt(30 / 365)    (30 calendar days)

At 411.82% annualized this gives ±21.56% daily, ±57.03% weekly, ±118.06%
monthly. (The mockup's 25.9%/57.1%/118.9% mixed a 252-trading-day daily with
365-day weekly/monthly and is intentionally not reproduced.) The calculation
module also understands a 252-trading-day basis (weekly 5, monthly 21) so the
math is not silently wrong if the convention ever changes, but it resolves the
basis from an explicit periods-per-year input and reports "unavailable" for
conventions it does not recognize — it never invents one.

Volatility values everywhere in the result payload are decimal fractions
(0.40 = 40%); `hyperopen.views.portfolio.optimize.format/format-pct`
multiplies by 100 exactly once at render time. The calculation modules work in
decimals end to end.

## What the leverage-risk card models (and does not)

The result payload carries the target's annualized arithmetic expected return
`:expected-return` (μ) and volatility `:volatility` (σ), the current book's
`:current-expected-return` / `:current-volatility`, and account equity in
dollars at `[:rebalance-preview :capital-usd]`. It does not carry realized
portfolio return series (so the existing empirical Monte Carlo engine in
`src/hyperopen/portfolio/montecarlo/engine.cljs` cannot be fed from a run
result) and it does not carry per-asset maintenance margins (so a true
liquidation probability cannot be computed honestly).

The card therefore uses the standard lognormal (geometric Brownian motion)
model, stated on the card: annual log-growth is normal with mean
ν = ln(1 + μ) − σ²/2 and standard deviation σ. This parameterization keeps the
model's *mean* ending wealth exactly equal to the arithmetic expectation
(E[W₁] = W₀·(1+μ)) while the *median* ending wealth W₀·e^ν shows the
volatility drag the user needs to see. Outputs:

- median ending equity: `equity * exp(ν)`
- 5th / 95th percentile ending equity: `equity * exp(ν ± 1.645σ)`
- probability of ending the year down at least half:
  `Φ((ln 0.5 − ν) / σ)` where Φ is the standard normal CDF
- probability of touching a 50% drawdown from the starting equity at any point
  in the year (first passage of GBM to barrier b = 0.5):
  `Φ((ln b − ν)/σ) + b^(2ν/σ²) · Φ((ln b + ν)/σ)` (time T = 1 absorbed into
  ν and σ), clamped to [0, 1].

Assumptions the card states in fine print: lognormal returns scaled from the
run's estimates, continuous rebalancing to target weights, no fat tails, no
funding drift, no execution costs, and **no margin/liquidation mechanics — a
levered book is typically forced out before a 50% drawdown completes, so the
drawdown probability is a lower bound on ruin-type risk**. The card never
prints a "probability of liquidation" because the data to compute one honestly
is not in the result.

μ ≤ −100% or non-finite inputs make the model unavailable (the card hides its
modeled rows rather than rendering NaN). requires σ > 0; σ = 0 degenerates to
the deterministic outcome (median = mean, probabilities 0 or 1 by sign).

## UI placement and behavior

The recommendation tab is composed in
`src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs`
(verdict headline → results grid) and the grid in
`src/hyperopen/views/portfolio/optimize/results_panel.cljs`: left column is the
allocation table, center is the frontier chart + "Why this target" card +
refinement card, right rail is the confidence rail then trust rail then the
collapsed views editor (`results_diagnostics_rail.cljs`).

- The Volatility intuition card renders as a rail `<aside>` (same chrome as the
  confidence/trust rails) inserted **between the confidence rail and the trust
  rail**. It renders only when a finite positive target volatility exists.
  The Target/Current toggle is a pair of visually hidden radio inputs inside
  label "tabs" (the `optimizer-risk-balance-tab` idiom from
  `risk_contributions_card.cljs`), toggled purely by scoped `:has()` CSS in
  `src/styles/surfaces/optimizer/results.css` — no app state, radio group name
  keyed by the result's `:as-of-ms` so co-rendered workbench fixtures don't
  share a group. Default (no radio checked) shows Target. The Current tab only
  renders when the current book has a finite positive volatility.
- Horizon bars are plain divs whose widths are percentages of the largest
  displayed horizon (monthly); they are `aria-hidden` and every value is also
  plain text. Values above 100% render uncapped.
- Severity messaging: <50% none; ≥50% "Elevated volatility" (muted);
  ≥100% "Very high volatility" (warning tint); ≥200% "Extreme volatility"
  callout with the square-root-of-time limitation sentence. When the monthly 1σ
  value is ≥100% an additional boundary note explains the value is a dispersion
  scale, not a literal symmetric range, because simple returns cannot fall
  below −100%.
- The card states "A 1σ move is a volatility scale, not a forecast or maximum
  loss." and "Scaled from annualized volatility using √time · 365 calendar
  days/year." as always-visible fine print (no hover-only tooltip, so the
  explanation is keyboard/screen-reader reachable by default).
- The frontier Current and Target callouts (in
  `frontier_current.cljs` / `frontier_target.cljs`, rows built by
  `frontier-callout/point-rows`, rendered by `frontier_callout.cljs` and
  `frontier_callout_blended.cljs`) gain a divider row and three ± horizon rows.
  Sweep-point callouts (dozens of hover targets) stay unchanged to avoid
  clutter.
- The insight strip is one bordered line rendered by the volatility card
  namespace and placed by `results_panel.cljs` directly under the frontier
  chart, only when target annualized volatility ≥ 100%.
- The Leverage risk card renders under the volatility card in the rail when
  target gross exposure (`[:diagnostics :gross-exposure]`) ≥ 2 or target
  volatility ≥ 100%. Dollar rows render only when
  `[:rebalance-preview :capital-usd]` is a finite positive number; otherwise
  the card speaks in percentages of equity only.

## Milestones

Milestone 1 — pure calculation modules with tests. Two new worker-safe,
side-effect-free namespaces:
`src/hyperopen/portfolio/optimizer/domain/volatility_intuition.cljs`
(basis resolution 365→{7,30} / 252→{5,21}, horizon scaling, severity
classification, boundary warnings, nil-safe guards for missing / zero /
negative / NaN / infinite volatility and unknown bases) and
`src/hyperopen/portfolio/optimizer/domain/leverage_risk.cljs`
(normal CDF via the Abramowitz–Stegun erf approximation, lognormal outcome
model, terminal-loss probability, first-passage drawdown probability, dollar
projection). Unit tests in
`test/hyperopen/portfolio/optimizer/domain/volatility_intuition_test.cljs` and
`test/hyperopen/portfolio/optimizer/domain/leverage_risk_test.cljs` cover the
worked examples above (40% and 411.82% on both bases), monotonicity, the
mean-consistency identity, first-passage sanity bounds, and every edge case.
Acceptance: `npx shadow-cljs compile test && TZ=UTC node out/test.js` passes
with the new tests included.

Milestone 2 — view-model and rail cards. A read-model namespace
`src/hyperopen/portfolio/optimizer/application/view_model/volatility_intuition.cljs`
extracts (result → card model) once so the card, tooltip, and strip cannot
disagree, and view namespaces
`src/hyperopen/views/portfolio/optimize/volatility_intuition_card.cljs`
(rail card + insight strip) and
`src/hyperopen/views/portfolio/optimize/leverage_risk_card.cljs` render them.
`results_panel.cljs` mounts the cards in the rail and the strip under the
frontier chart. Static CSS lands in
`src/styles/surfaces/optimizer/results.css`. View tests cover rendering,
gating, toggle markup, boundary note, and that no NaN/Infinity ever reaches
hiccup. The results-panel hierarchy test is updated for the new rail order.
Acceptance: `npm test` green; the workbench scenes (Milestone 3) show the
designed layouts.

Milestone 3 — frontier tooltip rows, workbench scenes, browser QA, Playwright.
`point-rows` accepts a `:horizons` option and both callout row renderers
support a divider row; Current/Target models pass horizon values. Workbench
scenes namespace
`src/hyperopen/workbench/scenes/optimize/volatility_intuition_scenes.cljs`
renders the card/strip/leverage states (normal, elevated, very high, extreme,
no-current, no-capital), a committed Playwright spec
`tools/playwright/test/optimizer-volatility-intuition-workbench.spec.mjs`
asserts the calculated values, convention line, gating, and uncapped monthly
value, and a manual browser pass screenshots the scenes. Acceptance: the new
spec passes against the worktree build; `npm run gates` is green.

## Validation and Acceptance

Run everything from the repository root. `npm run setup:worktree` first on a
fresh worktree (links `node_modules`), then `npm run gates` — it runs
`npm run check`, `npm test`, and `npm run test:websocket` and prints a
PASS/FAIL matrix that must be all-PASS. The unit surface for this feature is
`test/hyperopen/portfolio/optimizer/domain/volatility_intuition_test.cljs`,
`test/hyperopen/portfolio/optimizer/domain/leverage_risk_test.cljs`,
`test/hyperopen/portfolio/optimizer/application/view_model/volatility_intuition_test.cljs`,
and the four view test namespaces named in Milestone 2.

Browser acceptance (deterministic): compile the workbench
(`npm run css:build && npx shadow-cljs --force-spawn compile app portfolio`),
serve `resources/public` statically (the gitignored `.claude/launch.json`
`static` entry serves it on :8090), then

    PLAYWRIGHT_BASE_URL=http://localhost:8090 \
    PLAYWRIGHT_REUSE_EXISTING_SERVER=true \
    npx playwright test optimizer-volatility-intuition-workbench --workers=1

must pass 5/5. Human-observable acceptance on the scenes at
`/ui-workbench.html?id=hyperopen.workbench.scenes.optimize.volatility-intuition-scenes/<scene>`:
the extreme scene shows 411.8% translating to ±21.56% / ±57.03% / ±118.1%
(uncapped) with the extreme callout, boundary note, insight strip, and a
leverage-risk card whose target median is $408 on $100,000 with 87.8% / 98.2%
loss odds; the moderate scene shows ±2.09% / ±5.54% / ±11.47% with no
warnings, no strip, and no leverage card; the very-high scene has no
Target/Current toggle and a leverage card speaking in multiples of starting
equity. Optimizer weights, constraints, returns, covariance, and frontier
construction are unchanged by construction — nothing in the diff touches the
engine paths, and the full existing optimizer test surface stays green.

## Progress

- [x] (2026-07-12) Recon: annualization convention confirmed 365 in
  `domain/risk.cljs` + `domain/returns.cljs`; result payload keys confirmed
  (`:volatility`, `:current-volatility`, `:expected-return`,
  `:current-expected-return`, `[:diagnostics :gross-exposure]`,
  `[:rebalance-preview :capital-usd]`, `:as-of-ms`); rail/card/tab idioms and
  callout row pipeline mapped; no existing compounding/scenario component
  overlaps.
- [x] (2026-07-12) ExecPlan written.
- [x] (2026-07-12) Milestone 1: domain namespaces + unit tests (full CLJS
  suite green: 5547 tests / 0 failures after the view milestones).
- [x] (2026-07-12) Milestone 2: view-model, rail cards, insight strip, CSS,
  view tests, hierarchy test update; frontier-chart contract test updated for
  the two intended new callout row sets.
- [x] (2026-07-12) Milestone 3: tooltip rows + callout divider, workbench
  scenes (`volatility-intuition-scenes`: extreme / moderate / very-high),
  browser QA on the compiled worktree build (static :8090; DOM-radio toggle,
  gating, and start-tick clip fix verified live), Playwright workbench spec
  5/5 green plus the sibling risk-correlation workbench spec 6/6 as a
  workbench regression check.
- [x] (2026-07-12) Validation gates: `npm run gates` 34/34 PASS (6253 tests /
  33294 assertions across check + npm test + websocket; first run failed only
  `lint:docs` because this plan lacked an explicit `## Validation` heading —
  added, re-run all-PASS). Focused Playwright workbench spec 5/5.
- [x] (2026-07-12) Follow-up: relocated the Frontier quality / Selection
  stability / Stop reason rows (direct user request) from leading the right
  rail to trailing the volatility-intuition and leverage-risk cards.
  `result-confidence-rail` now renders only the "Result confidence" header +
  next-step row; a new `result-confidence-quality-rail` (titled "Solve
  quality" to avoid a duplicate header once the two cards are no longer
  adjacent) carries the three relocated rows and mounts right after the
  always-present `optimizer-volatility-risk-cards` wrapper, so the ordering
  vs. the leverage-risk card is a structural guarantee, not a conditional one.
  Equal Risk's own confidence rail is untouched (different content, not shown
  in the request). Full suite 5548/5548 green; `npm run gates` 34/34 PASS;
  browser-verified on the workbench build (screenshot showed the
  duplicate-header rough edge, which prompted the "Solve quality" rename).
- [x] (2026-07-12) Follow-up (direct user request, revisiting the mockup's
  ONE-YEAR MODELED LEVERAGE IMPACT component): the leverage-risk content was
  promoted from a right-rail card to its own full-width center-column panel
  (`leverage_impact_panel.cljs`) directly under the efficient frontier,
  replacing the one-line volatility insight strip (deleted along with
  `insight-model`; the rail card `leverage_risk_card.cljs` deleted too — the
  rail keeps only the volatility-intuition card). The panel follows the
  mockup: median-ending-wealth bars with legend, a big signed "Median wealth
  shortfall vs current" headline, a mean / 5th-percentile / loss-odds tile
  row (funding + execution cost tiles deliberately omitted per the user), and
  the NEW piece — the modeled target ending-wealth distribution: the
  lognormal density drawn on a log dollar axis with 5th-pct / median / mean
  markers (`domain.leverage-risk/outcome-model` now exposes `:log-sigma` so
  the view can draw its own model). Marker labels use compact dollars
  ($408 / $2M) with a push-apart pass so median/mean labels never collide at
  moderate σ; the axis is explicitly disclosed as log-scaled (linear dollars
  would crush the skew the panel exists to show) and carries no invented
  ticks. Distribution is skipped for the degenerate σ=0 case. Suite
  5549/5549 green, workbench Playwright spec 6/6 (now including distribution
  marker ordering: mean strictly right of median by the σ²/2 drag),
  browser-verified in dollar and multiples modes.
- [x] (2026-07-12) Follow-up (direct user request, with a detailed tooltip
  guide written for the mockup's full Monte-Carlo model): added an accessible
  info-tip to every field of the leverage-impact panel — panel title, Modeled
  chip, Median ending wealth, the shortfall headline, all four stat tiles, and
  the distribution heading. Reused the codebase's group / :has() hover-card
  idiom (`info-tip`: focusable glyph + `role=tooltip` card, revealed on
  group-hover / group-focus-within, aria-describedby wired, `:end` alignment
  for right-edge tiles so they don't overflow). Copy was ADAPTED to our
  closed-form lognormal model, not copied from the guide's simulation
  language: the panel tip states "not a simulation" and that funding /
  execution / liquidation are "not modeled"; the median tip frames median vs
  mean and anchors the starting equity; the two loss-odds tips draw the
  end-of-year (ending down 50%+) vs path-dependent (touching −50%) distinction
  the guide insists on; the touch tip stays honest ("not a liquidation
  probability, which would need maintenance margins this result does not
  carry"); the distribution tip discloses the log axis. Suite 5554/5554;
  workbench Playwright spec 7/7 (new test hovers AND keyboard-focuses a tip to
  confirm it fades in); browser-verified the render + right-edge positioning.
  Renames from the guide's table (wealth→equity, etc.) were NOT applied — the
  tooltips resolve the ambiguity the user flagged, and a label sweep is a
  separate call left for the owner.
- [x] (2026-07-12) Follow-up (direct user request): shortened the volatility-
  intuition card's three row labels from "Daily 1σ move" / "Weekly 1σ move" /
  "Monthly 1σ move" to plain "Daily" / "Weekly" / "Monthly" — the "1σ"
  explanation stays in the card's always-visible fine print ("A 1σ move is a
  volatility scale...") and the −100%-boundary note, so the meaning isn't
  lost, just not repeated on every row. Suite 5554/5554, workbench Playwright
  spec 7/7 unaffected (assertions target data-role + value text, not the
  label strings), browser-verified.
- [ ] Post-merge follow-up (optional, deferred): empirical short-horizon
  intuition (historical percentile one-day/one-week moves) fed from the
  history store, labeled "Historical" vs the model-scaled values per the
  designer's Phase 2 note.

## Surprises & Discoveries

- The existing Monte Carlo engine (`portfolio/montecarlo/engine.cljs`)
  resamples realized return series; the optimizer result payload carries only
  moments (μ, σ), so the leverage-risk card uses closed-form lognormal math
  instead of simulation. This is also why the card's copy says "modeled" and
  names the distribution.
- The designer mockup's daily/weekly/monthly example values mixed two
  annualization bases (25.9% daily is √252-scaled; 57.1%/118.9% are
  √365-scaled). The plan follows the repo convention (365) for all three.
- `frontier_callout.cljs` has three render paths (plain, target, blended);
  Current and Target both pass allocations so they render via
  `frontier_callout_blended.cljs` — the divider row support has to live in
  both the blended metric-row renderer and the shared `row-nodes` (used by the
  target variant), or one of the two tooltips would silently drop it.
- `frontier_chart_contract_test.cljs` pins the EXACT string sets of the
  Current/Target callouts; the two sets were updated for the intended new
  horizon rows (±2.20/±5.82/±12.04 for the 42% fixture, ±1.26/±3.32/±6.88 for
  the 24% one). Any future callout row change hits the same wall.
- Browser QA found one visual defect the render tests could not: the
  leverage card's starting-equity tick at `left: 100%` inside an
  `overflow: hidden` track is fully clipped whenever start is the scale max
  (i.e. both medians below start). Fixed with `left: calc(X% − 1px)`.
- The Browser-pane scroll/click tools time out over the workbench iframe
  (Portfolio swallows wheel events); driving the scene through
  `javascript_tool` + `contentDocument` works reliably and verified the
  DOM-radio toggle live.

## Decision Log

- Follow the user's trim of the designer spec: no compounding-drag KPI tile,
  no funding/execution-cost tiles in the leverage card, no
  10,000-path-simulation claims. The two components the user kept are built
  fully; the insight strip and tooltip rows are kept because they are
  low-surface-area and directly serve the "translate volatility" goal.
- 365-day basis everywhere (repo convention), weekly = 7 days, monthly = 30
  days. The calc module resolves {365, 252} and returns unavailable for
  anything else rather than guessing (designer's "do not invent a convention"
  requirement).
- Current/Target toggle is DOM radio + `:has()` (zero app state), matching the
  Equal Risk card tabs; selection is presentation-only so it does not belong in
  app state (repo rule: only data-routing state lives there).
- Leverage risk card shows a first-passage 50% drawdown probability instead of
  a "probability of liquidation": per-asset maintenance margins are not in the
  result payload, and printing a liquidation number from invented margins
  would violate the optimizer's honesty ethos. The card explicitly says the
  drawdown number is a lower bound on ruin-type risk for a levered book.
- ν = ln(1+μ) − σ²/2 rather than ν = μ − σ²/2: keeps E[ending equity] exactly
  (1+μ)·equity, so the modeled mean can never contradict the expected-return
  KPI shown two cards above.
- Gating thresholds: leverage-risk card at gross ≥ 2x or σ ≥ 100%; insight
  strip at σ ≥ 100%; severity tiers 50/100/200% per the designer prompt.
- New view namespaces instead of growing `results_diagnostics_rail.cljs`
  (already 503 lines, near the 500-line size gate).
- Relocated confidence-quality card titled "Solve quality" rather than
  reusing "Result confidence": the split moved it two cards away from its
  former sibling, so an identical header would read as an accidental
  duplicate rather than a continuation. Verified live in the browser before
  and after the rename.

## Outcomes & Retrospective

- 2026-07-12: Shipped end to end — two pure domain namespaces
  (volatility-intuition scaling, leverage-risk lognormal outcomes), one shared
  read-model, the rail card with DOM-radio Target/Current toggle, the gated
  Leverage risk card, the under-chart insight strip, Current/Target callout
  horizon rows behind a divider, three workbench scenes, and a 5-test
  Playwright workbench spec. `npm run gates` 34/34 PASS; the sibling
  risk-correlation workbench spec stayed green as a regression check.
- Complexity: additive and contained. All math lives in the two domain
  namespaces (fully unit-tested, including the log-space first-passage guard
  against ∞·0 NaN); the only edits to existing code are three mount points
  (results_panel rail/center), an opt-in `:horizons` option on `point-rows`,
  divider support in the two callout row renderers, and two test updates that
  pin the intended new behavior. Optimizer weights, constraints, returns,
  covariance, and frontier construction are untouched.
- Deliberate omissions (user's trim + honesty): no compounding-drag KPI tile,
  no funding/execution-cost tiles inside the leverage card, no simulated-path
  claims, and no "probability of liquidation" (maintenance margins are not in
  the result payload; the first-passage 50%-drawdown odds are framed as a
  floor on ruin risk instead).
- Remaining: the deferred Phase-2 follow-up tracked in Progress (empirical
  "Historical" horizon values from the history store, labeled distinctly from
  the model-scaled numbers).
