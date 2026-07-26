# Equal Risk BREAKDOWN tab: per-asset inspection view + full-width correlation matrix

This ExecPlan is a living document maintained in accordance with
`/hyperopen/.agents/PLANS.md`. The sections `Progress`, `Surprises &
Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to
date as work proceeds.

## Purpose / Big Picture

The Equal Risk results page centers on the RISK CONTRIBUTION BALANCE card
(`src/hyperopen/views/portfolio/optimize/risk_contributions_card.cljs`), which
today has four tabs: RISK CONTRIBUTION, BREAKDOWN, CORRELATION, RISK/RETURN.
The BREAKDOWN tab currently shows every asset at once (one lane per asset,
standalone + diversification sub-bars). The CORRELATION tab splits its width
between the correlation heatmap and a small per-selected-asset "Contribution
breakdown" block.

After this change, a user clicking BREAKDOWN lands on the designer's 2026-07-11
per-asset inspection view: one asset at a time, chosen via a "Change asset"
dropdown (or by clicking a row in the Allocation table, which already sets the
same selection), showing that asset's Standalone Risk, Diversification Effect,
and Net Risk Contribution as labeled horizontal bars over a shared axis, a
framed "Net = Standalone + Diversification" equation with the actual numbers,
and four summary tiles (equal-risk fit, this asset's diversification, its net
contribution vs target, allocation freedom). A small toggle switches between
this per-asset view (default, "Selected asset") and the existing all-assets
chart ("All assets") — none of the shipped all-assets work is discarded.
Because the per-asset panel now owns the decomposition story, the CORRELATION
tab drops its duplicate breakdown block and the correlation heatmap expands to
the card's full width, which matters as held-asset counts grow toward the
payload's 12-asset matrix cap.

To see it working: run the Playwright workbench spec
`tools/playwright/test/optimizer-risk-correlation-workbench.spec.mjs`, or open
`http://127.0.0.1:8080/ui-workbench.html?id=hyperopen.workbench.scenes.optimize.equal-risk-correlation-scenes/correlation-designer-parity`
against `npm run dev:browser-inspection` and click the BREAKDOWN tab.

## Context References

Public refs:
- Direct user/maintainer request (2026-07-11, this session): make BREAKDOWN a
  per-asset inspection view matching the designer mock (per-asset bars +
  equation + tiles + "Change asset" picker), keep the all-assets view behind a
  sub-toggle with per-asset as default, remove the correlation tab's
  contribution-breakdown half and let the P&L correlation matrix use the full
  width for larger books.

Repo artifacts:
- Sibling shipped work this builds directly on: the correlation/breakdown views
  (`src/hyperopen/views/portfolio/optimize/risk_correlation_panel.cljs`,
  `risk_breakdown_panel.cljs`), the structure view-model
  (`src/hyperopen/portfolio/optimizer/application/view_model/equal_risk_structure.cljs`),
  the `:risk-structure` payload section
  (`src/hyperopen/portfolio/optimizer/application/engine/equal_risk_payload.cljs`),
  and the workbench scenes
  (`portfolio/hyperopen/workbench/scenes/optimize/equal_risk_correlation_scenes.cljs`).

Local scratch refs (non-authoritative):
- None.

## Orientation: how the pieces fit

The card renders all tab panels up front; which panel shows is pure DOM state —
each tab label wraps a visually-hidden radio (`data-risk-view`), and scoped
`:has()` CSS in `src/styles/surfaces/optimizer/results.css` displays the
matching `.optimizer-risk-balance-panel--*`. No app state is involved in tab
switching. The ONE piece of app state in this area is
`[:portfolio-ui :optimizer :selected-risk-instrument]`, set by
`:actions/set-portfolio-optimizer-selected-risk-instrument`
(`src/hyperopen/portfolio/optimizer/actions/run.cljs`), today dispatched by
Allocation-table row clicks; the view-model
(`equal_risk_structure.cljs` → `selected-instrument`) falls back to the most
negative net contributor (else largest |net|) when the selection is nil or
stale. All decomposition numbers come from the persisted `:risk-structure`
result section: `:standalone-share-by-instrument`,
`:diversification-share-by-instrument` (full universe, NOT capped), and
`:correlation {:instrument-ids :matrix :hidden-count}` (capped at 12 by
|net share|). `standalone + diversification = net` holds exactly per asset,
where net is `[:risk-contributions :relative-contributions-by-instrument]`.
Allocation-freedom facts (binding caps) live at
`[:equal-risk-solver :allocation-freedom]` and already have formatted copy in
`equal_risk_results.cljs` → `freedom-card-view` ("Limited · 2 binding caps" /
"Caps constrain exact equality").

## Milestones

### M1 — Read models for the per-asset panel

`equal_risk_structure.cljs` gains two pure functions plus tests:
`breakdown-asset-options` (the Change-asset dropdown list: every held
instrument with a finite net share, `{:instrument-id :label :side}` sorted by
label) and `asset-breakdown-tiles` (the four-tile model for a selected
breakdown: equal-risk summary from RMS + target with the existing
`deviation-tone` honesty rule; the asset's diversification benefit/cost in
pts; net share with signed pts vs target; allocation freedom reusing
`equal-risk-results/freedom-card-view`). Tiles return data only (labels,
values, subs, tones, icon keywords) — views map tones/icons to markup.
Acceptance: `npm test` passes with new cases in
`test/hyperopen/portfolio/optimizer/application/view_model/equal_risk_structure_test.cljs`
covering option sorting/side derivation and tile copy for benefit vs cost
diversification, missing freedom, and deviation signs.

### M2 — Per-asset panel view + BREAKDOWN sub-view toggle

New namespace
`src/hyperopen/views/portfolio/optimize/risk_asset_breakdown_panel.cljs`
renders the designer panel inside the breakdown tab: block header
("Contribution breakdown for <SYM>" with side-colored asset + S/L badge and
the net-risk explainer line), the "Change asset" `<select>` dispatching
`[:actions/set-portfolio-optimizer-selected-risk-instrument [:event.target/value]]`
(nexus placeholder vector form), three full-height lanes (standalone purple,
diversification green when negative / red when positive, net purple) drawn
from zero on a shared `fit-scale` axis with value labels at bar ends, the
axis + "Contribution to Total Portfolio Volatility" title, the framed
two-line equation with the actual numbers, and the four tiles reusing the
`optimizer-equal-risk-fact` visual language in a container-driven auto-fit
grid. It reuses `risk-breakdown-panel/plot-backdrop`/`axis-rows` and the
existing selected-* data-roles (`portfolio-optimizer-risk-selected-asset`,
`-standalone`, `-diversification`, `-net`, `-identity`) so allocation-click
selection semantics and most test hooks carry over. `risk_breakdown_panel.cljs`
becomes the composition point: a small DOM-radio toggle
(`data-breakdown-view` = `asset` | `all`, per-asset first/default) switches
between the pre-rendered per-asset block and the existing all-assets chart via
scoped `:has()` CSS — same zero-app-state pattern as the correlation mode
toggle. The card (`risk_contributions_card.cljs`) passes `result` +
`selected-risk-instrument` through to the breakdown panel and relabels the
KPI View cell "Breakdown details".

### M3 — Correlation tab becomes the full-width matrix

`risk_correlation_panel.cljs` drops its selected-asset breakdown block and
`decomp-row` helper; the panel body renders the heatmap block alone so the
one-child auto-fit grid gives it the full card width. The card stops passing
the selection into the correlation panel. Dead CSS for the removed block is
pruned only where no longer referenced (the `optimizer-risk-decomp-*` row/bar
rules stay — the all-assets chart and the new panel reuse them).

### M4 — Scenes, browser coverage, gates

The workbench scenes keep rendering the real card so they pick the change up
for free; docstrings are refreshed and the designer-parity store keeps MSTR
preselected for mock parity. The Playwright spec re-targets breakdown
interactions to the BREAKDOWN tab (allocation-row click re-targets the
per-asset panel; the Change-asset select re-targets both panel and table
highlight; the sub-toggle swaps per-asset ↔ all-assets; the correlation tab
now shows only the full-width heatmap). cljs view tests in
`test/hyperopen/views/portfolio/optimize/results_panel_equal_risk_test.cljs`
are updated the same way. Gates: `npm run gates` (check, test,
test:websocket) all PASS, then
`npx playwright test optimizer-risk-correlation-workbench` (with
`PLAYWRIGHT_REUSE_EXISTING_SERVER=true` against a running dev server when
iterating), then a workbench screenshot compared against the designer mock.

## Validation commands

From the worktree root (`npm run setup:worktree` first on a fresh worktree):

    npm run gates
    npx playwright test optimizer-risk-correlation-workbench

Expected: gates matrix all PASS; the Playwright spec's tests green. Manual
proof: the workbench scene `correlation-designer-parity` BREAKDOWN tab shows
the per-asset panel for MSTR with equation `net = standalone + (div)` numbers
matching the scene fixture (standalone 32.6%, diversification −10.0%, net
+22.7% — real domain math, intentionally not the mock's illustrative numbers),
and the CORRELATION tab shows the 5×5 matrix spanning the full card width.

## Progress

- [x] (2026-07-11) Explored current card/panels/view-model/payload/CSS/tests;
      confirmed selection app-state + action already exist and
      `freedom-card-view` already words the freedom tile.
- [x] (2026-07-11) ExecPlan written.
- [x] (2026-07-11) M1: view-model fns (`breakdown-asset-options`,
      `asset-breakdown-tiles`) + unit tests.
- [x] (2026-07-11) M2: `risk_asset_breakdown_panel.cljs`, breakdown
      sub-toggle, card wiring, CSS (incl. fact-icon `short` tone).
- [x] (2026-07-11) M3: correlation panel slimmed to full-width heatmap; dead
      decomp-row/selected/identity CSS pruned.
- [x] (2026-07-11) M4: scenes/docstrings, Playwright spec re-targeted +
      2 new tests, cljs view tests updated. `node out/test.js`: 5458 tests /
      29502 assertions, 0 failures.
- [x] (2026-07-11) Fixed two integration defects found by browser QA: the
      workbench dispatcher's bare-keyword placeholder double-resolution
      (nexus-parity fix + regression test) and the select's first-render
      value (per-option `:selected`); flipped cramped negative end-labels to
      the zero side with sign-true color.
- [x] (2026-07-11) Gates green (`npm run gates` 34/34 PASS, 6165 tests /
      32848 assertions), focused Playwright spec 6/6 green on the worktree's
      static bundle (:18080), workbench screenshots vs mock captured
      (per-asset default, all-assets sub-view, full-width correlation).
- [x] (2026-07-11) ExecPlan moved to completed/.

## Surprises & Discoveries

- Observation: nexus placeholders are vector forms — a select dispatches
  `[:actions/... [:event.target/value]]`, not the bare keyword.
  Evidence: `nexus/core.cljc` `interpolate` walks with
  `(when (vector? x) (get placeholders (first x)))`.
- Observation: the correlation tab's two-block layout already collapses to a
  single full-width column when only one child renders
  (`grid-template-columns: repeat(auto-fit, minmax(330px, 1fr))`), so M3 needs
  no new layout CSS.
- Observation: `structure-summary`'s by-instrument standalone/diversification
  maps are full-universe (only the correlation matrix is capped at 12), so the
  Change-asset dropdown can offer every held asset — including ones the capped
  all-assets chart hides.
- Observation: the designer mock's tile numbers are internally inconsistent
  (net 14.2% vs 20% target is 5.8 pts off while "Max deviation" reads 3.4 and
  the tile says "Within ± 2.4 pts"); the implementation words the net tile
  from the actual signed deviation instead of copying mock phrasing.
- Observation: Replicant sets an element's attributes BEFORE its children
  mount (replicant.core `create-node`: `set-attributes` then append children),
  so `:value` on a `<select>` is written while the select has zero options and
  silently falls back to the first option on first render. Selection must
  ride the OPTIONS as `:selected` (Replicant writes the `.selected` property,
  which works both at mount and on diffs).
  Evidence: Playwright `expect(picker).toHaveValue("perp:MSTR")` failed on
  first render with `:value`, passes with per-option `:selected`.
- Observation: the workbench scene dispatcher resolved placeholder KEYWORDS
  anywhere in an action, while the real runtime (nexus `interpolate`) only
  matches the VECTOR form `[:event.target/value]`. Because `walk/postwalk`
  visits inner forms first, the bare-keyword branch double-resolved the
  canonical vector form into a WRAPPED value (`["perp:ETH"]`), which the
  reducer stored verbatim; the stale-selection fallback then silently
  re-targeted MSTR. Fixed `hyperopen.workbench.support.dispatch` to
  vector-form-only (nexus parity) and converted its tests' bare keywords to
  vector form, adding a regression test that a bare keyword reaches reducers
  untouched as data.
  Evidence: live workbench probe — `change` with value `perp:ETH` left the
  panel on MSTR before the fix; Playwright spec 6/6 after.
- Observation: with a data-hugging `fit-scale`, a dominant NEGATIVE component
  bar ends so close to the lane's left edge that an outside end-label
  collides with the value column ("-10.0%-10.0%"). Fixed by flipping cramped
  negative labels to the zero side of the bar (`end-label-placement`,
  threshold x < 15%), and splitting the label's `data-dir` (placement) from
  `data-sign` (color) so a flipped negative label keeps its negative color.
  Evidence: workbench screenshots before/after at 1500px.

## Decision Log

- Decision: reuse the existing `selected-risk-instrument` app state + action
  for the Change-asset dropdown instead of introducing new state.
  Why: it is the established "only data-routing state" for this card;
  Allocation-row clicks and the dropdown then stay in perfect agreement, and
  the stale-selection fallback already exists in the view-model.
- Decision: sub-view switching (Selected asset / All assets) is DOM-radio +
  scoped `:has()` CSS, defaulting to the per-asset view via the
  first-tab-active-when-nothing-checked pattern.
  Why: identical to the card tabs and the correlation mode toggle; zero app
  state, zero re-render, and persisted results need no migration.
- Decision: per-asset panel lives in a NEW namespace
  `risk_asset_breakdown_panel.cljs` rather than growing
  `risk_breakdown_panel.cljs`.
  Why: the 500-line namespace-size gate (`dev/check_namespace_sizes.clj`); the
  breakdown panel stays the composition point and keeps the shared plot
  primitives the new panel reuses.
- Decision: keep the mock's zero-line-only plot (no purple equal-target line)
  in the per-asset panel, and fit the scale to the asset's three values only.
  Why: matches the designer drawing; the target comparison is carried by the
  net tile ("+X pts vs target") where it can be stated exactly.
- Decision: reuse the `portfolio-optimizer-risk-selected-*` data-roles from
  the retired correlation-tab block for the per-asset panel.
  Why: same semantics ("the selected asset's decomposition"), keeps the
  Playwright/view-test churn to re-targeting which tab hosts them.
- Decision: the CORRELATION tab keeps its "Correlation / decomposition" KPI
  View label replaced with "Correlation matrix", and BREAKDOWN's becomes
  "Breakdown details" (mock's strip).
  Why: the strip labels what each tab shows; the correlation tab no longer
  shows a decomposition.
- Decision: tiles reuse the why-card's `optimizer-equal-risk-fact` classes but
  sit in a new container-driven `auto-fit` grid class instead of the why-card's
  viewport-breakpoint grid.
  Why: the card renders inside workbench iframes where viewport media queries
  lie about available width (established 2026-07-11 auto-fit ratchet).
- Decision: fix the workbench dispatcher to nexus-parity (vector-form-only
  placeholder interpolation) rather than switching the view to the workbench's
  bare-keyword form.
  Why: the app's runtime is the contract — every production view uses the
  vector form (e.g. history-pagination's page-size select), so a workbench
  that resolves bare keywords green-lights code that would break in the app
  and corrupts canonical-form args. No scene used the bare form (repo grep),
  so only the dispatcher's own tests needed updating.
- Decision: the select carries no `:value`; the matching option carries
  `:selected` instead.
  Why: Replicant writes attributes before children exist, so a select-level
  value cannot survive first render; the `.selected` property write works at
  mount and on every diff.

## Outcomes & Retrospective

(2026-07-11, complete) Shipped as planned. The BREAKDOWN tab now defaults to
the designer-parity per-asset inspection panel — asset picker synced with
Allocation-row selection, three labeled component bars over a shared axis,
the exact-identity equation strip with real numbers, and four summary tiles
(the freedom tile reuses the why-card's copy verbatim) — with the previous
all-assets chart preserved behind a zero-app-state DOM-radio sub-toggle. The
CORRELATION tab dropped its duplicate breakdown block; the P&L/underlying
matrix now spans the full card width (heatmap ≈ 94% of card width, padding
only). Validation: `npm run gates` 34/34 PASS (6165 tests / 32848
assertions), the re-targeted `optimizer-risk-correlation-workbench` Playwright
spec 6/6 (including new sub-toggle and full-width tests), and workbench
screenshots confirming mock parity (numbers derive from the scene's real
covariance math — standalone 32.6% / diversification −10.0% / net 22.7% —
rather than the mock's internally inconsistent illustrative values).

Complexity went DOWN net of features: the correlation panel lost a whole
sub-block and its private row renderer; the new panel reuses the existing
plot primitives, selection state/action, fact-card visual language, and
corr-block chrome; the only new state-bearing control rides an action that
already existed. The work also hardened shared infrastructure: the workbench
dispatcher now interpolates placeholders with exact nexus parity (a latent
double-resolution bug that would have silently corrupted ANY scene's
event-value dispatch), with a regression test pinning bare keywords as data.

Remaining/known limits: positive end-labels can spill a few px into the
block padding at extreme narrow widths (cosmetic; the scale's rounding
headroom covers realistic cases); if designers later want the equal-target
line inside the per-asset plot, `fit-scale` already accepts it as one more
value.
