# Equal Risk results: flip the story from "solved" to "what changes"

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. Maintain this file in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The Equal Risk results page (the optimizer draft page when the objective is Equal Risk) is honest and mathematically careful, but it is optimized for verifying the solver, not for understanding the decision. In the common happy case — solve quality `Exact` — the flagship Risk Contribution Balance chart renders one identical-length bar per asset, the Target column repeats the same constant percentage on every row, the Deviation column repeats "±0.0 pts", and the KPI strip reads `Exact · 0.0 pts · 0.0 pts · 0`. Most of the card's ink says "the solver worked" while the user's real questions — "what does this rebalance actually change in my book, and what does that do to my outcomes?" — are carried by the faintest elements (small gray "current" circles) or scattered across other cards.

After this change, a user landing on an Exact Equal Risk result sees, in order: a three-chip "What changes if you execute" strip (risk imbalance current → target, modeled volatility current → target, modeled one-year outcome current → target); a balance chart whose per-row columns show where each position's risk sits TODAY and how much it shifts, sorted so the biggest risk donors are at the top; a KPI strip whose cells are informative in every state (current imbalance and biggest shift replace the dead "0.0 pts" cells when the fit is exact); leverage-impact tiles that show the current book's number next to each target number; and a Diversification tab that leads with its verdict sentence and a visual "volatility bridge" instead of burying the conclusion in the last line of fine print. Cosmetic honesty fixes ride along: no more "−0.0 pts", no duplicate "Target" legend entries, a plain-English card subtitle, and an explanation that the equal target is simply 1 ÷ number of held assets.

To see it working: run the workbench (`npm run dev` serves `ui-workbench.html` on :8080, or any static serve of a `shadow-cljs watch portfolio` build), open scene id `hyperopen.workbench.scenes.optimize.equal-risk-correlation-scenes/balance-exact-fit`, and compare against scene `correlation-designer-parity` (approximate fit) — the exact scene shows Current/Shift columns and the imbalance KPIs, the approximate scene keeps the original Target/Deviation columns.

## Context References

Public refs:
- Direct user/maintainer request (2026-07-16, this session): "look specifically under risk parity or equal risk and come up with your best ideas on how we could make it easier for the user to understand what's going on … and what the implications are of the allocation", followed by "create an execution plan and implement it". The accepted proposal is summarized in this plan's milestones.

Repo artifacts:
- Parent ExecPlans: `docs/exec-plans/active/2026-07-10-optimizer-equal-risk-objective.md` (objective + solver), `docs/exec-plans/active/2026-07-11-equal-risk-balance-chart-design-fidelity.md` (balance chart design contract), `docs/exec-plans/active/2026-07-11-optimizer-equal-risk-results-redesign.md` (tabs + structure payload), `docs/exec-plans/active/2026-07-12-optimizer-volatility-intuition.md` (leverage-impact panel).
- Governed docs: `docs/DESIGN.md`, `docs/FRONTEND.md`, `docs/BROWSER_TESTING.md`, `docs/agent-guides/trading-ui-policy.md`.

Local scratch refs (non-authoritative):
- None.

## Background a novice needs

"Risk contribution" here is the signed Euler decomposition of portfolio volatility: with covariance Σ and signed weights w, asset i's relative contribution is w_i (Σw)_i / (wᵀΣw). Contributions sum to exactly 100% of portfolio volatility; a NEGATIVE contribution means the position hedges the book. Equal Risk solves for weights whose contributions are all equal to 1/n (n = held assets). The solved result carries these sections (all read by pure view-models, never recomputed in views):

- `:risk-contributions` — recommended-book contributions, targets, `:quality` (`:exact` / `:approximate` / `:not-converged`), `:rms-error`, `:max-absolute-error`, `:negative-contribution-count`.
- `:current-risk-contributions` — the SAME math over the user's current holdings: `:relative-contributions-by-instrument`, `:rms-error`, `:max-absolute-error`. Persisted pre-redesign results may lack this section; every view must degrade.
- `:risk-structure` — per-asset standalone/diversification split, the capped underlying-correlation matrix, per-asset position-P&L-vs-portfolio correlations, and `:target-diversification` / `:current-diversification` portfolio benchmark scalars (all-move-together, zero-correlation, modeled volatility, plus derived reduction/effect scalars).

Key files:

- View-model: `src/hyperopen/portfolio/optimizer/application/view_model/equal_risk_results.cljs` (`balance-model`, `kpi-risk-balance`, formatting helpers).
- Diversification view-model: `src/hyperopen/portfolio/optimizer/application/view_model/equal_risk_diversification.cljs` (`comparison-model` — note its currently-unused `:cards` key computes exactly the per-book benchmark values/positions a bridge visual needs).
- Views: `src/hyperopen/views/portfolio/optimize/risk_contributions_card.cljs` (balance tab + KPI strip + tabs), `risk_diversification_summary.cljs` (Diversification tab matrix), `leverage_impact_panel.cljs` (one-year modeled outcomes), `results_panel.cljs` (page composition), `results_summary.cljs` (`equal-risk-context-card`, whose `equal-risk-fact-card` markup + `.optimizer-equal-risk-fact` CSS the new strip reuses).
- Styles: `src/styles/surfaces/optimizer/results.css` (scoped under `.portfolio-optimizer`; `npm run test:styles` forbids `[data-role]` selectors in CSS).
- Workbench scenes (fixtures computed by the REAL domain math): `portfolio/hyperopen/workbench/scenes/optimize/equal_risk_correlation_scenes.cljs`. The `portfolio/` source root is NOT scanned by Tailwind — scene-only markup must reuse utility classes that already exist in `src/**`.
- Tests: `test/hyperopen/portfolio/optimizer/application/view_model/equal_risk_results_test.cljs`, `test/hyperopen/views/portfolio/optimize/leverage_impact_panel_test.cljs`, `test/hyperopen/views/portfolio/optimize/results_panel_equal_risk_test.cljs`, `test/hyperopen/views/portfolio/optimize/risk_diversification_semantics_edge_test.cljs`, `test/hyperopen/views/portfolio/optimize/risk_diversification_help_edge_test.cljs`; Playwright `tools/playwright/test/optimizer-risk-correlation-workbench.spec.mjs`.

Existing behavior constraints that must survive:

- Tab/toggle state is DOM-only (sr-only radios + scoped `:has()` CSS); the new strip's deep-links must use `<label for=radio-id>` (`structure-model/risk-view-radio-id`), never app state.
- The Playwright workbench spec pins: exactly 6 diversification help triggers, benchmark rows keep one current + one recommended marker each, the decision-summary copy ("Modeled volatility falls … all-move-together stress rises"), and heatmap behavior. The Diversification changes must be additive around those anchors (no new help-disclosure triggers; keep the shared-scale lane and the decision-summary `data-role` + copy shape).
- Honesty contracts: never color a contribution by position side; no pies/stacked bars for signed shares; no "probability of liquidation" language; "Modeled" labeling stays on the leverage panel.
- All views must degrade on persisted results lacking `:current-risk-contributions` or `:risk-structure` (fall back to today's rendering).

## Milestones

### Milestone 1 — view-model: shift rows, exact display mode, honest zero formatting

Scope: `equal_risk_results.cljs` + its test namespace. `balance-model` learns a `:display-mode`: `:shift` when quality is `:exact` AND at least one row has a finite current share, else `:deviation` (today's behavior, bit-for-bit). Every row gains `:shift-pts` (recommended share − current share, in points; nil-safe). In `:shift` mode the row cap keeps the largest |shift| rows (the movers are the story) and display order becomes current-share descending (today's risk, high to low — bars all end on the target line, so the gray circles form the descending silhouette and the chart reads "risk drains from the top rows into the bottom rows"); in `:deviation` mode cap and order are unchanged (worst |deviation| capped, signed share descending). The model also returns `:asset-count` (held assets, pre-cap) and, under `:current`, a `:biggest-shift` `{:instrument-id :label :shift-pts}` computed over ALL rows before capping. `format-signed-pts` snaps |value| < 0.05 to unsigned "0.0 pts" so "−0.0 pts" can never render; `format-pts` is untouched. Existing tests (all fixtures use `:quality :approximate`) must pass unchanged; new tests cover the mode flip, shift cap/order, biggest-shift, asset-count, signed-zero snapping, and degradation without current data.

Acceptance: `npm test` green; new deftests assert an `:exact` + current fixture flips mode and ordering while an `:approximate` fixture (same shares) keeps today's ordering.

### Milestone 2 — Risk Balance card: informative columns, KPIs, and copy in the exact case

Scope: `risk_contributions_card.cljs` + `results.css` + view tests. In `:shift` mode the two right-hand columns become Current (gray, current share %) and Shift (signed pts, neutral color — a shift is not good or bad); col-heads and per-row `:title` strings follow; `data-role`s for the new cells are `portfolio-optimizer-risk-contribution-current-cell` and `portfolio-optimizer-risk-contribution-shift`. In `:deviation` mode the card renders exactly as today. KPI strip in `:shift` mode: "RMS deviation" / "Max deviation" (dead "0.0 pts" cells) are replaced by "Current imbalance" (`RMS current → recommended pts`, echoing the scenario KPI strip's `current → target` arrow convention) and "Biggest shift" (asset label + signed pts). The Equal target KPI gains a plain-language `:title` tooltip: "Every held asset owns the same slice of portfolio risk: 1 ÷ N assets = X%." The per-row purple target tick is drawn only when a row's target differs from the uniform equal target (it never does for Equal Risk today — the tick duplicated the continuous dashed line at the same x), and its "Target (equal)" legend entry follows the same condition. The card subtitle becomes "How much of the portfolio's risk each position owns", with the exact technical phrase ("Signed Euler contribution to total portfolio volatility") preserved in the subtitle's `:title` tooltip. The overflow line in `:shift` mode reads "+ N more with smaller shifts (the largest risk shifts are shown)". The reading-note in `:shift` mode explains the columns ("Bars show recommended contribution — every position lands on the equal target. The gray circle is where its risk sits today; Shift is what the rebalance adds (+) or removes (−).") and keeps the "does not minimize total volatility" sentence.

Acceptance: node view tests assert both modes' column heads, KPI cells, tick suppression, and subtitle; `npm test` green.

### Milestone 3 — leverage-impact tiles: current-book comparators

Scope: `leverage_impact_panel.cljs` + its test namespace. Each of the four stat tiles (Mean ending wealth, 5th percentile, Odds of ending down 50%+, Odds of touching −50%) gains a small "Now: X" line above the existing subtext, rendered only when the current book's outcome model exists — the domain `outcome-model` already computes every current-book number; nothing new is modeled. Dollar tiles format with the same `factor-text` (dollars when account equity is known, multiples otherwise); odds tiles use the same probability formatting. The warning color threshold continues to read the TARGET value only.

Acceptance: panel test renders a fixture with current + target and asserts each tile contains the current comparator; a target-only fixture renders no comparator lines; `npm test` green.

### Milestone 4 — "What changes if you execute" strip

Scope: new view namespace `src/hyperopen/views/portfolio/optimize/equal_risk_impact_strip.cljs`, wired into `results_panel.cljs` directly above the risk card in the Equal Risk center column; new node test namespace; minimal CSS (the strip reuses `.optimizer-equal-risk-fact` cards and the why-card's section title styling; only a 3-column grid rule is new). Three chips, each `current → target`:

1. Risk balance — `RMS imbalance 2.3 → 0.0 pts` from `balance-model`'s `:current` and `:rms-pts`; renders as a `<label>` deep-linking to the Risk Balance tab radio.
2. Modeled volatility — `66.6% → 71.5%` from `:risk-structure` `:current-diversification` / `:target-diversification` `:modeled-volatility` (fallback: result `:current-volatility` / `:volatility`); deep-links to the Diversification tab radio; sub-line names the direction honestly ("takes more total volatility to balance risk" / "also lowers total volatility").
3. One-year outcome — `median $536 → $546` plus `touch −50% odds 12.1% → 13.7%` from the same `leverage-risk-model` the panel below uses; renders only when that model exists (same gross/vol gate), and is a plain card (no tab to link).

The strip renders nil unless the result is Equal Risk AND at least two chips have data (a strip with one chip is noise). Chips carry `data-role`s `portfolio-optimizer-equal-risk-impact-{balance,volatility,outcome}` under a `portfolio-optimizer-equal-risk-impact-strip` section.

Acceptance: node tests cover full-data (3 chips + correct radio `for` targets), no-leverage-gate (2 chips), no-current-data (strip absent), non-equal-risk (strip absent); `npm test` green.

### Milestone 5 — Diversification tab: verdict first, volatility bridge, calm tones

Scope: `equal_risk_diversification.cljs`, `risk_diversification_summary.cljs`, `results.css`, the two diversification test namespaces. Three changes:

1. Materiality threshold: `shared-row` gains displayed-points-aware toning — when |change-points| < 0.5 the row's `:tone` becomes `:neutral` and its direction copy becomes "≈ unchanged" (direction key `:unchanged` semantics reuse); red/green fire only on material change. The Playwright parity scene's modeled row moves several points, so its `falls` assertions stay valid.
2. Verdict first: the decision-summary paragraph (same `data-role`, same copy shape — the workbench spec matches on it) moves from the matrix footer to directly under the tab intro, restyled as the tab's lead sentence (`optimizer-risk-diversification-decision--lead` class, larger type). The matrix follows it.
3. Volatility bridge: a new compact visual between the verdict and the matrix, built from `comparison-model`'s existing (previously unused) `:cards` data for the recommended book: one horizontal lane on the 0 → all-move-together scale with a solid segment to modeled volatility, a hatched/soft segment from modeled to all-move-together labeled "diversification −X pts", a tick at zero-correlation labeled "independent baseline", and a signed annotation "correlations +Y pts vs independent" (offsets/amplifies wording from `cross-effect-direction`). Plain divs with % lefts (the balance-lane idiom), classes `optimizer-risk-divbridge-*`, no `[data-role]` CSS selectors, NO new help-disclosure trigger (the workbench spec pins exactly 6; the bridge's explanation lives in a native `title` and visible labels).

Acceptance: node tests pin the threshold boundary (0.4 pts → neutral "≈ unchanged"; 0.6 pts → toned direction copy), the lead placement (decision summary precedes the matrix in the hiccup tree), and bridge geometry (segment widths monotone, labels carry the pts); `npm test` green; the workbench Playwright spec passes untouched except where it asserts things this milestone deliberately changed (none expected).

### Milestone 6 — workbench scene, Playwright coverage, gates, browser QA

Scope: extend `equal_risk_correlation_scenes.cljs` — `structured-result` also computes `:current-risk-contributions` from the current weights via the real `contribution-summary` (designer-parity gains honest current circles exactly as the app draws them), accepts a `:quality` override, and a new `balance-exact-fit` scene renders the results-scene layout (strip above card) with `:quality :exact` so the exact-mode UI is directly inspectable and testable. Add one Playwright test to the existing workbench spec: open `balance-exact-fit`, assert the Current/Shift col-heads, the Current-imbalance and Biggest-shift KPI cells, the impact strip's three chips, and that clicking the volatility chip activates the Diversification tab (DOM-radio deep-link). Run the required gates and the smallest relevant Playwright command first, then the full spec file.

Acceptance: `npm run gates` (check + test + websocket) PASS; `npx playwright test tools/playwright/test/optimizer-risk-correlation-workbench.spec.mjs` PASS; workbench screenshots of the exact scene captured for the QA record.

## Validation

Commands (run from the repo root of this worktree; `npm run setup:worktree` first — a fresh worktree has no `node_modules` and `shadow-cljs` is local-only, so unbootstrapped gates fail with opaque environmental errors):

- `npm run setup:worktree`
- `npm run gates` — runs `npm run check`, `npm test`, `npm run test:websocket` with a single PASS/FAIL matrix.
- `npx playwright test tools/playwright/test/optimizer-risk-correlation-workbench.spec.mjs` — the risk-card workbench spec (build the portfolio bundle first if stale; the spec serves `ui-workbench.html` scenes).
- Browser QA: workbench scenes `balance-exact-fit` (new UI) and `correlation-designer-parity` (approximate fallback unchanged), screenshots at 1280px; confirm no horizontal overflow and both tab deep-links work. Stop any browser-inspection sessions when done (`npm run browser:cleanup`).

Expected observations: exact scene shows the strip + Current/Shift columns + imbalance KPIs; approximate scene is pixel-equivalent to main except the additive strip/comparators/bridge; no "−0.0 pts" anywhere; diversification tab leads with the verdict sentence and shows the bridge; sub-0.5-pt changes render neutral.

## Progress

- [x] ExecPlan drafted and saved as active.
- [x] Milestone 1: view-model shift mode + formatting (code + tests). Full node suite green (5719 tests / 31566 assertions).
- [x] Milestone 2: Risk Balance card exact-mode columns/KPIs/copy (code + tests + CSS).
- [x] Milestone 3: leverage tile comparators (code + tests).
- [x] Milestone 4: impact strip view + results-panel wiring (code + tests; no new CSS needed — the strip reuses the why-card's fact-card classes and scanned grid utilities). Suite green after 2–4: 5726 tests / 31615 assertions.
- [x] Milestone 5: diversification verdict/bridge/tones (code + tests + CSS). Suite green: 5728 tests / 31630 assertions.
- [x] Milestone 6: scenes + Playwright + gates + browser QA evidence. `npm run gates` 34/34 PASS (6448 tests / 35016 assertions, 1m50s); workbench spec 10/10 PASS against the worktree's own build on :8090 (including the new `balance-exact-fit` coverage), re-run green after the namespace splits; browser-pane QA screenshots confirmed the exact scene (strip, Current/Shift columns, imbalance/biggest-shift KPIs, verdict-lead + bridge on the Diversification tab) and the approximate parity scene fallback (RMS/Max deviation cells and Target/Deviation columns unchanged); zero console errors; static server and browser sessions cleaned up.
- [ ] Post-merge follow-ups triaged (per-row dollar-risk toggle, weight-vs-risk column, correlation top-drivers strip, current-distribution overlay — deliberately deferred, see Decision Log).

## Surprises & Discoveries

Pre-implementation research findings (these shaped the milestones):

- The diversification view-model's `comparison-model` already computes a `:cards` structure (per-book benchmark values + 0–100 positions) that no view consumed — the bridge visual needs exactly this data, so Milestone 5 is view-only.
- `balance-model` already computed current-book RMS/max imbalance and `kpi-risk-balance` already computed the current→target delta for the Execution tab's scenario strip; the draft page simply never surfaced them.
- The workbench Playwright spec pins exactly 6 diversification help triggers and per-row benchmark markers — discovered before implementation; shaped the "additive bridge, no new help triggers, keep the lane" scoping.
- The `portfolio/` workbench source root is outside Tailwind's content globs — scene markup must not introduce new utility classes, only classes already emitted from `src/**`.
- Replicant stringifies a list-as-one-child — the strip's chips must go `into` the tag vector, never render as a bare `(map ...)` child.

Implementation findings:

- The shared KPI strip renders once per tab panel (contribution + breakdown + correlation), so any Playwright locator on a KPI cell data-role resolves to THREE elements — assertions must scope `.first()`. Hit as a strict-mode violation on the new exact-fit test; fixed in the spec.
- Port 8080 is held by the main checkout's long-running shadow-cljs dev server (its `:dev-http` port is hardcoded), so the workbench spec was run against THIS worktree's own compiled build served statically on :8090 with `PLAYWRIGHT_BASE_URL` + `PLAYWRIGHT_REUSE_EXISTING_SERVER=true` — the documented worktree lane. Never kill the :8080 process; it is not this worktree's.
- The float-noise trap in view-model tests: shift/deviation equality assertions must use binary-exact fixtures (1/32 grids, 0.25 steps) — 1/25-style targets make `(= 0.2 ...)` fail on the 17th decimal.
- The exact-fit workbench fixture can be honestly `:exact` with zero solver involvement: uniform pairwise correlation + equal per-position risk scale (w·σ constant, all long) makes the true Euler contributions exactly 1/n by symmetry, so the real domain math grades the book exact up to float noise.
- The additions pushed four namespaces over the 500-line `lint:namespace-sizes` cap (first gates run: 33/34). Fixed with real splits, no exception entries: the KPI strip cluster moved to `risk_balance_kpi_strip.cljs` (card 580 → ~470); the info-tip primitive + static tip copy moved to `leverage_impact_tips.cljs` (panel 520 → 443); the results-panel Equal Risk tests split into `results_panel_equal_risk_fixtures.cljs` (shared, no deftests — the setup_layout_fixtures precedent) + `results_panel_equal_risk_structure_test.cljs`; the two new diversification tests moved to `risk_diversification_reading_flow_test.cljs`.

## Decision Log

- Shift-mode display order is CURRENT share descending (not signed shift): the bars all end on the target line in the exact case, so ordering by today's share makes the gray circles form a descending silhouette — "risk drains from the top rows into the bottom rows" — and matches the existing "longs high-to-low" designer language. Signed-shift order is its mirror and was rejected as it puts the least-affected rows in the middle of both extremes.
- Shift column is neutral-colored: a position gaining or shedding risk share is not good or bad, and the deviation column's red/green severity semantics must not leak onto it. Sign colors on the Shift column were explicitly rejected.
- The per-row target tick is suppressed only when the row target equals the uniform target (|diff| < 1e-9), not removed: the payload supports per-instrument targets and a future risk-budget objective must keep its ticks.
- The Diversification shared-scale lane STAYS this pass: the designer-parity Playwright spec pins its markers and positions, and removing it would rewrite a designer-approved contract without a designer in the loop. The bridge is additive; lane removal is a deferred follow-up to raise with design.
- The bridge draws the RECOMMENDED book only: the matrix below already compares current vs recommended per benchmark, and a twin bridge doubled the visual load without adding a number the matrix lacks.
- The impact strip's third chip (one-year outcome) respects the leverage panel's existing gross ≥ 2x / vol ≥ 100% gate rather than computing outcomes for ungated books: an ungated book has no leverage panel below to reconcile against, and the gate is the panel's honesty contract.
- Materiality threshold for diversification tones is 0.5 displayed points, applied to tone and direction copy but NOT to the stored `:change-points` value or the decision summary (the verdict sentence keeps exact numbers; only the red/green judgment is thresholded).
- The Current-imbalance KPI keeps the deviation-tone grading of the CURRENT book (red when today's book is badly lopsided) even though its arrow ends at 0.0: the tone grades the problem the rebalance fixes, sitting deliberately next to the green Exact status. If this reads as alarming in review, neutralizing the tone is a one-line change.
- The bridge's under-lane labels are a justify-between caption row, not markers positioned at segment ends — segment ends already carry the tick/border language, and positioned text labels collide at narrow widths. The note sentence names every number with its benchmark.
- Deferred (not in this plan's scope, candidates for follow-up plans): per-row dollar-risk toggle, weight-vs-risk column, correlation-tab top-drivers strip, current-book distribution overlay on the leverage panel, per-row "Now/Shift" columns for the approximate case. Rationale: each is independently shippable, and this plan already touches every Equal Risk surface — smaller reviewable increments beat one omnibus diff.

## Outcomes & Retrospective

- 2026-07-16: All six milestones implemented and validated in one pass. Evidence: `npm run gates` 34/34 PASS (check + node suite 6448 tests / 35016 assertions + websocket); `optimizer-risk-correlation-workbench.spec.mjs` 10/10 PASS including the new exact-fit test, run against this worktree's own compiled build (the :8090 static lane — :8080 belongs to the main checkout); browser-pane screenshots of both scenes captured during QA. Uncommitted on the worktree branch pending review.
- Net complexity: slightly up in `balance-model` (one explicit display-mode branch) and DOWN at the page level — the exact case now has zero dead cells, four namespaces were split back under the size cap along the way (`risk_balance_kpi_strip`, `leverage_impact_tips`, the results-panel test fixtures/structure split, the diversification reading-flow test), and three previously-computed-but-unconsumed models (`:current` imbalance, the current-book outcome model, the diversification `:cards`) gained consumers instead of new parallel math being added.
- Lesson: the comprehension gaps were almost entirely SURFACING problems, not modeling problems — the domain layer already knew every answer (current imbalance, current outcome odds, bridge geometry); the views were only showing the solver's report card. The one genuinely new judgment introduced is the 0.5-displayed-pt materiality threshold on diversification tones.
- Remaining: the deferred follow-ups in the Decision Log, and a design conversation about retiring the diversification shared-scale lane now that the bridge exists (the Playwright designer-parity contract pins the lane today).
