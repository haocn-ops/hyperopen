# Equal Risk results: correlation view and per-asset contribution breakdown

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. Maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

When the optimizer runs the Equal Risk objective (risk parity — every position should contribute the same share of portfolio volatility), correlation is the entire story: a short position's P&L stream flips the sign of its correlation to the longs, which is exactly why it can hedge the book while still contributing positive risk. Today the results page shows only the signed contribution balance chart; nothing explains *why* an asset's contribution is what it is.

After this change, the RISK CONTRIBUTION BALANCE card (the Equal Risk centerpiece that replaces the efficient-frontier chart at `/portfolio/optimize/draft`) grows from two to four DOM-state tabs — RISK CONTRIBUTION | BREAKDOWN | CORRELATION | RISK / RETURN — built to the designer's 2026-07-11 correlation-view mock:

- The CORRELATION tab shows a correlation heatmap with a POSITION P&L / UNDERLYING RETURNS toggle (position-P&L correlations are the underlying correlations with signs flipped by position side: corr(sᵢrᵢ, sⱼrⱼ) = sᵢsⱼ·corr(rᵢ, rⱼ)), a diverging color legend, and — beside it — a CONTRIBUTION BREAKDOWN panel that decomposes the selected asset's net risk contribution into "standalone risk" (its own-variance term, always positive) plus "diversification effect" (its cross-covariance term, signed), proving visually that net = standalone + diversification.
- The BREAKDOWN tab shows that same decomposition for every charted asset as diverging stacked lanes with a net marker and the equal-target line.
- Clicking any allocation row (asset or leg) in the left Allocation table selects that instrument: the row gets a highlight ring and the CONTRIBUTION BREAKDOWN panel re-renders for it. Each allocation row also gains a small "P&L corr. to portfolio" line — corr(sᵢrᵢ, r_p), the correlation of that position's P&L with the whole portfolio's P&L.
- The WHY THIS RISK ALLOCATION strip swaps its third card to CORRELATION VIEW (a click on it jumps to the Correlation tab).

Everything is Equal-Risk-only: other objectives keep the frontier chart untouched, and persisted pre-change Equal Risk results (which lack the new payload section) degrade gracefully to the current two tabs.

To see it working: `npm run dev:portfolio` (or `npm run dev:browser-inspection`), open `http://localhost:8080/ui-workbench.html`, and open the "Equal Risk correlation" scenes; or run a real Equal Risk optimization in the app and open the Recommendation tab.

## Context References

Public refs:
- Direct user request (2026-07-11) attaching the designer's correlation-view mock of the results page plus the designer's commentary (math for position-P&L correlation sign flips, the standalone-vs-diversification decomposition RRCᵢ = (wᵢ²σᵢ² + Σⱼ≠ᵢ wᵢwⱼΣᵢⱼ)/(wᵀΣw), the "P&L corr. to portfolio" column, and the four-tab layout).

Repo artifacts:
- Parent ExecPlan: `/hyperopen/docs/exec-plans/active/2026-07-11-equal-risk-balance-chart-design-fidelity.md` (the card shell, DOM-state tab pattern, KPI strip, and `--optimizer-target` purple this plan extends).
- Domain math this builds on: `/hyperopen/src/hyperopen/portfolio/optimizer/domain/risk_contributions.cljs` (signed Euler contributions; exposes m = Σw, q = wᵀΣw, σ = √q).
- Design doc to keep current: `/hyperopen/docs/design-docs/optimizer-equal-risk.md`.

Local scratch refs (non-authoritative): none.

## Orientation

The optimizer engine solves in a web worker and assembles a plain-data result payload in `src/hyperopen/portfolio/optimizer/application/engine/payload.cljs`; Equal-Risk-only payload sections come from `engine/equal_risk_payload.cljs` (which receives the covariance via `:risk-result`) and are merged by a `select-keys` allowlist. Results persist per wallet (scenario library, IndexedDB), so anything the views need later must live in the payload — the covariance itself is discarded after the run. Views are Replicant hiccup: `views/portfolio/optimize/results_panel.cljs` lays out Allocation table (left, `target_exposure_table.cljs`) / center panel (`risk_contributions_card.cljs` for Equal Risk) / confidence rail (right). Read-models for Equal Risk views live in `application/view_model/equal_risk_results.cljs` — every Equal Risk view branch reads from a view-model namespace so the page cannot disagree with itself. Tab state on the card is deliberately NOT app state: visually-hidden radio inputs + scoped `:has()` CSS in `src/styles/surfaces/optimizer/results.css` (see the parent plan). Runtime actions are pure functions returning effect vectors, registered across a drift-tested contract surface (see Milestone B for the exact file list).

## Milestones

### Milestone A — risk-structure math and payload section

Scope: a new pure domain namespace `src/hyperopen/portfolio/optimizer/domain/risk_structure.cljs` and a new Equal-Risk payload section `:risk-structure`, so every number the new views need is computed once, from the same covariance and published weights as the existing `:risk-contributions` section, and persists with the result.

The math, with covariance Σ, signed target weights w, m = Σw, q = wᵀΣw, σ_p = √q, σᵢ = √Σᵢᵢ, sᵢ = sign(wᵢ):

    underlying correlation      ρᵢⱼ = Σᵢⱼ / (σᵢσⱼ)          (nil when either σ is degenerate)
    standalone share            stdᵢ = wᵢ²σᵢ² / q            (always ≥ 0)
    net share (existing RRCᵢ)   netᵢ = wᵢmᵢ / q              (already in :risk-contributions)
    diversification share       divᵢ = netᵢ − stdᵢ           (= Σⱼ≠ᵢ wᵢwⱼΣᵢⱼ / q, signed)
    P&L corr. to portfolio      pᵢ = sᵢmᵢ / (σᵢσ_p)          (nil when wᵢ = 0 or σᵢ degenerate)

The `:risk-structure` section carries `:portfolio-volatility`, `:standalone-share-by-instrument`, `:diversification-share-by-instrument`, `:pnl-portfolio-correlation-by-instrument` (all instruments), and `:correlation {:instrument-ids [...] :matrix [[...]] :hidden-count n}` — the underlying-returns matrix only, capped to the 12 largest positions by |net share| (zero-weight instruments excluded — they hold no position, so they have no P&L stream), ordered by signed net share descending to match the balance chart's display order. The signed position-P&L matrix is derived in the view-model from this matrix plus weight signs; it is never persisted twice.

Wire-up: `equal_risk_payload.cljs/equal-risk-sections` gains the section; `payload.cljs` adds `:risk-structure` to its `select-keys` allowlist. Acceptance: new unit tests in `test/hyperopen/portfolio/optimizer/domain/risk_structure_test.cljs` prove, on dyadic fixtures, that the matrix has unit diagonal and symmetry, that stdᵢ + divᵢ = netᵢ exactly and Σnetᵢ = 1, that a positively-correlated long/short pair yields negative div for both, sign flips for pᵢ, the cap/order/exclusion rules, and nil-propagation on a zero-variance asset; `engine_equal_risk_test.cljs` proves a solved Equal Risk result carries the section aligned with `:risk-contributions` and that other objectives don't.

### Milestone B — selected-instrument state and action

Scope: one new UI action so an allocation-row click can drive the breakdown panel. The card's tabs stay DOM-state radios, but the *selected asset* cannot: its value must pick which asset's numbers render in another card's subtree, and `:has()` CSS cannot map "radio for instrument X checked" to "panel for instrument X" for dynamic instrument ids without per-index selector enumeration. So this is real app state, following the existing small-UI-action pattern (`set-portfolio-optimizer-draft-add-asset-open`).

Files (the drift-tested action contract surface): path `ui-selected-risk-instrument-path` in `src/hyperopen/portfolio/optimizer/contracts/paths.cljs` (+ its path registry map) re-exported from `contracts.cljs`; action fn `set-portfolio-optimizer-selected-risk-instrument` (arg: instrument-id or nil, effect `[:effects/save-many [[path value]]]`) in `src/hyperopen/portfolio/optimizer/actions/universe.cljs`; re-export in `optimizer/actions.cljs`; entries in `optimizer/runtime_catalog.cljs`, `schema/runtime_registration/portfolio.cljs`, `schema/contracts/action_args.cljs` (reuse `::portfolio-optimizer-instrument-id-args`), and `runtime/action_adapters.cljs`. No effect-order-contract / Lean entry: the action emits only `save-many` (same as the add-asset-open precedent, which has no entry).

Selection semantics live in the view-model, not the state: the state holds the last clicked instrument id; when that id is absent from the current result (new run, changed universe) the model falls back to the most negative net contributor if any exists (the asset the "Negative contributors" KPI points at), else the largest |net| contributor. No cleanup effects needed.

### Milestone C — views and CSS to designer parity

Scope: the visible feature. `risk_contributions_card.cljs` gains the two tabs (rendered only when `:risk-structure` exists) and delegates the new panels to two new namespaces to stay under the namespace-size gate: `views/portfolio/optimize/risk_correlation_panel.cljs` (KPI strip + heatmap block + selected-asset breakdown block) and `risk_breakdown_panel.cljs` (all-asset decomposition lanes). View-model joins live in a new `application/view_model/equal_risk_structure.cljs`.

Correlation tab, matching the mock: a six-cell KPI strip (the existing five plus a "View — Correlation / decomposition" cell); left block titled POSITION P&L CORRELATION with a two-segment POSITION P&L / UNDERLYING RETURNS toggle (a second DOM-state radio pair, group name suffixed `-corr-mode`; both grids pre-rendered, `:has()` swaps them); the heatmap as a CSS grid (inline `grid-template-columns` since column count is dynamic — static-CSS track rules only cover fixed layouts), cells colored by a diverging scale via `color-mix()` between new optimizer-scoped tokens `--optimizer-corr-positive` / `--optimizer-corr-negative` and the surface color, with an inline `--corr-pct` strength per cell; every cell carries a native multi-line `title` tooltip ("BTC Long × SOL Short / Underlying-return correlation +0.79 / Position-P&L correlation −0.79 / Effect on portfolio risk: Diversifying"); a gradient legend bar labeled −1.0 … 1.0 with the per-mode caption; an honest "+ n more assets not shown" line when the cap bites. Right block titled CONTRIBUTION BREAKDOWN: "Selected asset: <label> <Side>" (side word tinted long/short), three lane rows — Standalone risk ("Risk if held in isolation"), Diversification effect ("From correlations with other positions", green when negative / red when positive), Net risk contribution ("To total portfolio volatility") — over a shared scale with a zero line, dashed purple equal-target line, and a % axis; footer note "Net contribution = standalone risk + diversification effect".

Breakdown tab: same KPI strip; one lane per balance-chart row (same cap and signed-share order as the balance chart) drawing the standalone segment from zero, the diversification segment stacked from its end back (or out) to net, a net marker dot, the shared dashed target line, and Std / Div / Net numeric columns.

Allocation table (`target_exposure_table.cljs`), Equal-Risk-only: group and leg rows with an instrument id become selectable (`:on :click` dispatching the Milestone B action; `data-selected` + accent ring via CSS on the selected instrument's row) and grow a second line "P&L corr. to portfolio ±0.xx" (sign-toned) when the payload carries a value for that instrument. The table's single-entry render memo keys must gain the selected id and structure inputs. `results_panel.cljs` passes the selected id (read from state at the existing contracts path) into both the table and the card. Why-card (`results_summary.cljs`): the third fact card becomes CORRELATION VIEW ("Position P&L — shows how held trades interact"), implemented as a `label[for]` targeting the correlation tab radio (radios gain deterministic ids), highlighted while the correlation tab is active via a `:has()` rule scoped to the center panel; the largest-risk-contributor fact it replaces stays recoverable (top balance-chart row + row tooltips).

### Milestone D — scenes, committed coverage, QA, docs

Scope: proof. New workbench scene file `portfolio/hyperopen/workbench/scenes/optimize/equal_risk_correlation_scenes.cljs`: a designer-parity scene (5-asset long/short book rendered as Allocation table + card side by side inside `layout/interactive-shell`, whose scene-local reducer implements the selection action against a scene store — the workbench dispatcher resolves `:actions/*` per scene), plus cap-overflow (16 assets → 12 shown + remainder line), degenerate-column, and pre-structure-payload degradation scenes. Extend `test/hyperopen/views/portfolio/optimize/results_panel_equal_risk_test.cljs` for: four tabs when `:risk-structure` present, two when absent; both heatmap grids rendered with expected cell values and titles; breakdown panel honoring explicit/fallback selection; the allocation rows' P&L-corr line, click binding, and `data-selected`; the swapped why-card. New Playwright spec `tools/playwright/test/optimizer-risk-correlation-workbench.spec.mjs` drives the workbench scenes end-to-end: switch to CORRELATION tab, toggle UNDERLYING RETURNS, click an allocation row and assert the breakdown panel re-targets and the highlight moves. Browser QA per `/hyperopen/docs/BROWSER_TESTING.md`: run the new spec (smallest first), then a visual parity pass over the scenes in the browser with screenshots; stop sessions cleanly. Update `docs/design-docs/optimizer-equal-risk.md`. Gates: `npm run gates` (runs `npm run check`, `npm test`, `npm run test:websocket`) — all green from the worktree after `npm run setup:worktree`.

## Validation

- `npm run gates` — single PASS/FAIL matrix over check + full cljs test suite + websocket runtime tests. Run after each milestone; must be green before completion.
- `npx playwright test tools/playwright/test/optimizer-risk-correlation-workbench.spec.mjs` — the new committed browser coverage (dev server auto-started by the Playwright config).
- Manual: workbench scenes listed above; a real Equal Risk run in the app shows the four tabs; a Min-Variance run shows the frontier untouched; a persisted pre-change Equal Risk scenario opens with two tabs and no errors.

## Progress

- [x] (2026-07-11) Explored the surface end-to-end: card/tab CSS pattern, `equal_risk_payload` covariance access, allocation-table group/leg model (`:instrument-id` only on single-leg groups; legs carry their own), the 6-file action contract surface (no Lean entry for save-many-only actions), workbench interactive dispatch, plan/template rules.
- [x] (2026-07-11) Milestone A: `domain/risk_structure.cljs` (correlation matrix, decomposition, P&L-to-portfolio correlation, capped correlation section) + `:risk-structure` wired through `equal_risk_payload.cljs` / `payload.cljs`; the three new instrument-keyed maps registered in `instrument_keyed_codec.cljs` (worker-boundary key stringification — the phantom-legs class of bug) and a validation clause added to `contracts/specs.cljs`. Domain + engine tests green.
- [x] (2026-07-11) Milestone B: `set-portfolio-optimizer-selected-risk-instrument` + `ui-selected-risk-instrument-path` wired across paths/contracts/action/actions/runtime-catalog/registration/action-args/adapters; action unit test added; drift suites green.
- [x] (2026-07-11) Milestone C: `view_model/equal_risk_structure.cljs` (+unit tests), `risk_correlation_panel.cljs`, `risk_breakdown_panel.cljs`, four-tab card, allocation-table selection + P&L-corr line (single render per instrument — hidden mirror leg rows skip it), why-card CORRELATION VIEW label card, ~420 lines of scoped CSS. Heatmap endpoints reuse `--optimizer-long/short` (no new tokens needed).
- [x] (2026-07-11) Milestone D: interactive workbench scenes (fixtures computed through the real domain math; designer-parity weights numerically tuned to sit a nudge off the true equal-risk solution so the card reproduces the mock's near-balanced Approximate state), results-panel view tests, 4-test Playwright workbench spec green, browser QA on the live dev server with screenshots, design-doc results section rewritten.
- [x] (2026-07-11) Gates green: `npm run gates` Overall PASS 34/34 (6156 tests / 32750 assertions) after adding namespace-size exception entries for the two files that crossed the cap and bumping `action_args.cljs`'s.
- [ ] User review in the live session, then land the branch (merge is the user's call).

## Surprises & Discoveries

- The allocation table's group rows only carry an `:instrument-id` when the group has a single leg; multi-leg groups expose per-leg ids on the (currently auto-hidden) leg rows. Selection therefore targets both row kinds, and a multi-leg group row itself is not selectable. Single-leg groups RENDER their mirror leg row too (CSS-hidden), so the P&L-corr line must skip hidden rows or Playwright strict-mode sees two nodes per instrument — caught by the spec's first run.
- The workbench is not render-only: `hyperopen.workbench.support.dispatch` installs a per-scene Replicant dispatcher with scene-local reducers, so the click-to-select flow is testable deterministically in Playwright without solving a real optimization in-app.
- Portfolio's canvas iframe receives its scene via postMessage, not a URL — specs must drive the MAIN `/ui-workbench.html?id=<ns>/<scene>` page and reach through `frameLocator`.
- Arbitrary "plausible" weights make the card look nothing like the mock: equal risk with a 0.18-vol SP500 next to 0.9-vol coins demands a ~45% SP500 weight. The parity scene's weights were tuned numerically (multiplicative fixed point on |RRC|, then perturbed) so the REAL math yields the mock's story — RMS 1.8 pts, MSTR short standalone 32.6% − 10.0% diversification = +22.7% net.

## Decision Log

- Decision: The per-asset decomposition uses the variance-normalized split (stdᵢ = wᵢ²σᵢ²/q, divᵢ = netᵢ − stdᵢ) rather than a volatility-unit split (|wᵢ|σᵢ/σ_p).
  Rationale: it is the designer's own expansion of RRCᵢ; the identity std + div = net holds exactly per asset AND the components sum exactly across assets (Σstd + Σdiv = 100%); net equals the risk share every other view already displays, so the page stays self-consistent. The mock's "(|σᵢ|)" label hint is dropped from the UI copy because it would misdescribe the plotted number; the sub-line "Risk if held in isolation" stays.
  Date: 2026-07-11.
- Decision: Selected asset is real app state (new `set-portfolio-optimizer-selected-risk-instrument` action), while tabs and the P&L/underlying toggle remain DOM-state radios.
  Rationale: the parent plan's zero-app-state rule was satisfiable for tabs because the panel set is static and enumerable in CSS; selection must route *data* (which instrument's numbers render) across sibling cards for a dynamic id set, which `:has()` cannot express. The action is pure `save-many` UI state — the cheapest legitimate mechanism the runtime offers.
  Date: 2026-07-11.
- Decision: Persist only the underlying-returns correlation matrix, capped to 12 instruments by |net share| (zero-weight instruments excluded), display-ordered by signed net share.
  Rationale: results persist per wallet in IndexedDB and universes reach 88+ assets — a full matrix is O(n²) dead weight; the signed matrix is a pure sign flip derived in the view-model; 12 keeps 2-decimal cell text legible in the card's half-width; the order matches the balance chart so the two views cross-reference.
  Date: 2026-07-11.
- Decision: Heatmap colors are numeric-sign colors (green positive / red negative via new `--optimizer-corr-positive/negative` tokens + `color-mix()`), not good/bad semantics; the "Effect on portfolio risk" verdict lives in the cell tooltip instead.
  Rationale: matches the mock and the Change column convention; whether a positive correlation is good depends on sides, which is exactly what the tooltip explains. Tokens are optimizer-scoped (`optimizer/base.css`), which the theme-colors ratchet exempts.
  Date: 2026-07-11.
- Decision: The why-card's third card becomes CORRELATION VIEW (a label that activates the correlation tab), replacing Largest risk contributor.
  Rationale: designer's mock; the largest contributor remains visible as the top balance-chart row and in row tooltips; the card doubles as discoverable navigation to the new tab at zero app-state cost.
  Date: 2026-07-11.
- Decision: Default selection = most negative net contributor, else largest |net|.
  Rationale: the breakdown panel exists to explain surprising contributions; a negative contributor is the page's headline anomaly (it has its own KPI cell), and a stable deterministic fallback keeps tests and persisted-state restores predictable.
  Date: 2026-07-11.
- Decision: Heatmap color endpoints reuse `--optimizer-long` / `--optimizer-short` via `color-mix()` with a per-cell `--corr-strength`, instead of the planned new `--optimizer-corr-*` tokens.
  Rationale: green-positive/red-negative is already the surface's numeric-sign convention (Change column, deviation cells); two more tokens would encode the same meaning twice. The plan's token idea is dropped.
  Date: 2026-07-11.
- Decision: Heatmap display order stays signed-net-share descending even when that puts a SHORT first (a short can carry the largest positive contribution), diverging from the mock's longs-first order.
  Rationale: row-for-row cross-referencing with the balance chart is worth more than the mock's incidental ordering; the side is carried by the tooltip pair labels.
  Date: 2026-07-11.
- Decision: The correlation tab's selected-asset block renders no axis title and caps its axis at 4 tick labels (`scale-ticks` gained a max-labels arity); the Breakdown tab keeps the full 8-tick axis + title.
  Rationale: the block is half a card wide; the first render overlapped seven tick labels and wrapped the title vertically. The mock also shows a bare % axis there.
  Date: 2026-07-11.

## Outcomes & Retrospective

- (2026-07-11) Shipped end-to-end in one pass: the Equal Risk card now has RISK CONTRIBUTION | BREAKDOWN | CORRELATION | RISK / RETURN tabs; the correlation tab reproduces the designer's mock (verified on the workbench parity scene against the attached image — heatmap coloring, toggle, selected-asset decomposition, identity footer, why-card link/highlight); Allocation rows select the breakdown target and carry the P&L-correlation line. Everything is computed once in the payload from the same covariance as the sibling section, so the tabs can never disagree with the balance chart.
- Evidence: `npm run gates` Overall PASS (34/34; 6156 tests / 32750 assertions, +~30 tests over baseline); `optimizer-risk-correlation-workbench.spec.mjs` 4/4 green (tab/toggle DOM-state switching, click-to-select, 12-asset cap, degenerate em-dashes); browser QA screenshots captured from the live dev server; no console errors.
- Complexity: net increase, deliberately contained — one new domain ns, one new view-model ns, two new panel namespaces (the card stayed under its size cap by delegating), one new app-state path + action (the only state this feature needed; tabs/toggle stayed DOM-state per the parent plan's constraint). The payload grows by O(n) maps plus one capped 12×12 matrix per equal-risk result; nothing new is computed at render time beyond sign flips and joins.
- Remaining: user review in the live session; landing the branch. A follow-up candidate (out of scope here): making the BREAKDOWN tab rows click-select the correlation panel's asset too.
