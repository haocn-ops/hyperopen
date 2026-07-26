# Optimizer Draft Page: Review-and-Act Hierarchy

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/docs/PLANS.md`. Keep it self-contained, update it whenever implementation discoveries change the plan, and leave at least one unchecked progress item while the plan remains active.

## Purpose / Big Picture

The scenario draft page (`/portfolio/optimize/draft`, Recommendation tab) is the page a
user lands on after an optimization run. Its job, in order: (1) state what the optimizer
recommends, (2) let the user judge whether to trust it, (3) take them to Execution,
(4) only by exception, let them refine solver density or edit return-view inputs.

Today the page inverts that order in two ways the owner called out directly:

- The one full-size "Review & execute" CTA renders **after** the whole results grid
  (`scenario_detail_view.cljs` `recommendation-tab` appends `review-rebalance-cta` last),
  so with a real universe it sits below the fold while an advanced panel — the
  always-expanded refinement depth picker — holds prime center space under the frontier.
- The right rail leads with the full Black-Litterman return-views editor (one row per
  asset), an input-editing tool, pushing the trust/confidence content and everything
  after it off-screen exactly when the universe is large.

An external expert-designer review (see Context References) recommends restructuring the
page around "review and act": primary action above the fold, refinement demoted behind
progressive disclosure, input editing moved out of the recommendation flow, and the
reclaimed center space used for decision-support content. This plan adapts those
recommendations to codebase reality (see Decision Log for where and why we deviate).

## Context References

Public refs:

- Direct user request (repo owner, 2026-07-02): redesign the optimizer draft page for
  hierarchy; owner supplied an expert-designer review and delegated final decisions to
  the implementer ("lean on their insights, but ultimately your decision").

Repo artifacts:

- `src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs` — page shell, tabs,
  header actions, verdict, end-of-flow CTA.
- `src/hyperopen/views/portfolio/optimize/results_panel.cljs` — 3-column results grid.
- `src/hyperopen/views/portfolio/optimize/refinement_status_card.cljs` — the
  "Optimization status" / refinement panel this plan demotes.
- `src/hyperopen/views/portfolio/optimize/results_diagnostics_rail.cljs` — confidence +
  trust rails.
- `src/hyperopen/views/portfolio/optimize/return_views_panel.cljs` — shared return-views
  editor (setup rail, objective menu, results rail).
- `src/styles/surfaces/optimizer/results.css` — results-page surface styles.
- Related completed plan: `docs/exec-plans/completed/2026-07-02-optimizer-return-views-consolidation.md`
  (the views editor this plan collapses was consolidated there).

## Progress

- [x] (2026-07-02) Recon: mapped page composition, CSS grid mechanics, pinned tests,
  existing-but-unused refinement open/close actions, views-editor summary model.
- [x] (2026-07-02) ExecPlan authored.
- [x] (2026-07-02) Step 1: primary CTA into the verdict bar; drop end-of-grid duplicate.
- [x] (2026-07-02) Step 2: refinement card compacted; options behind a `<details>` disclosure.
- [x] (2026-07-02) Step 3: right rail reordered; views editor collapsible + collapsed on results.
- [x] (2026-07-02) Step 4: "Why this target" decision-support card in reclaimed center space.
- [x] (2026-07-02) Step 5: CSS for disclosures, verdict CTA, target-context card.
- [x] (2026-07-02) Step 6: unit tests updated/added (results_panel_test +2, new
  refinement_status_card_test with 5 tests; full suite 5001 tests / 0 failures).
  The BL smoke spec's rail-editor section now opens the disclosure first.
- [x] (2026-07-02) Step 7: workbench recommendation scenes (`recommendation_scenes.cljs`:
  many-assets-black-litterman, few-assets-historical).
- [x] (2026-07-02) Step 8: gates green — `npm run gates` 34/34 PASS (5706 tests,
  30760 assertions). Playwright: optimizer-view-model-routes.smoke.spec.mjs 14/14,
  portfolio-regressions `-g optimizer` 22/22 at --workers=1 (one pre-existing drifted
  spec repaired, see Discoveries).
- [x] (2026-07-02) Step 9: browser QA recorded in Validation (workbench
  recommendation scenes via preview tooling; DOM-geometry measurements at
  375 / 1440 / 1600(2xl) plus screenshots).

## Surprises & Discoveries

- Observation: the refinement view-model already exposes `:open?` backed by
  `ui-refinement-open-path`, with `open-/close-portfolio-optimizer-refinement` actions
  registered — but `refinement_status_card` never reads it; the options are always
  expanded. A collapsed-by-default panel was apparently intended at some point.
  Evidence: `application/view_model/refinement.cljs:60`, `actions/refinement.cljs:27-37`,
  no view dispatches either action.
- Observation: `.optimizer-results-grid` is `overflow: hidden` (results.css), which makes
  any `position: sticky` inside the grid inert (the grid never scrolls; the page does).
  A "sticky action rail" as the expert sketched would require re-plumbing the grid's
  overflow behavior. Evidence: `src/styles/surfaces/optimizer/results.css` `.optimizer-results-grid`.
- Observation: the right rail is only a side column at ≥2xl (1536px). At xl it is
  `col-span-2` — a full-width band *below* left+center — so a rail-anchored CTA would
  still be below the fold at 1280, one of the required QA widths.
  Evidence: `results_panel.cljs:42-44,60-61`.
- Observation: binding constraints are computed by the engine
  (`diagnostics :binding-constraints`) but not rendered anywhere on the Recommendation
  tab — the only renderer (`diagnostics-panel`) is not part of this page's composition.
- Observation: `scenario_detail_view_test.cljs` sits at its namespace-size exception
  ceiling (bumped to 876 on a prior change), so new assertions for this work go into
  `results_panel_test.cljs` and a new `refinement_status_card_test.cljs` instead.
  (`results_panel_test` itself then crossed the 500-line ceiling; the new hierarchy
  assertions moved to `results_panel_hierarchy_test.cljs` rather than adding a ratchet
  exception, per the namespace-split direction.)
- Observation: the fixed app footer (z-170) eclipses the tail of tall route pages.
  `main`'s `lg:pb-12` never reaches them because `main` is a fixed-height flex cell
  (`flex-1 min-h-0`) that the route content overflows — measured at max scroll the
  page tail sat 13px above the viewport bottom under a 43px footer, so the collapsed
  views-editor toggle (the new last rail row) was unclickable. Evidence: Playwright
  interception logs + a geometry dump (toggle rect 870–887 inside footer band
  857–900 at `scrollTop == scrollHeight - clientHeight`). Fixed with
  `padding-bottom: 56px` on `.optimizer-scenario-surface`. The old end-of-grid CTA
  had been clickable mostly by virtue of its height.
- Observation: mounting/unmounting the recompute banner between the verdict and the
  results grid re-created the grid subtree (unkeyed positional reconciliation), which
  reset open `<details>` state mid-edit — editing a return view triggers the auto
  rerun, so the views editor would snap shut under the user's cursor. Fixed by adding
  `:replicant/key` to the verdict, recompute banner, stale banner, results surface,
  and grid. Caught by the BL views smoke spec exercising the full edit loop.
- Observation: `portfolio-regressions.spec.mjs` "selected vault rows show shared gap"
  was already red on main — drift from the same-day universe-chips removal (rows keep
  only `data-history-status`; the visible chip text moved to the Readiness panel).
  Repaired to the new contract in passing (specs run outside `npm run check`, so they
  drift silently — known pattern).
- Observation: the add-asset popover containment spec passed previously with ~8px of
  slack that depended on incidental page height above the anchor; the taller verdict
  bar consumed it. The spec now centers the anchor before opening so it pins the
  popover's own sizing, not unrelated layout above it.

## Decision Log

- Decision: primary CTA lives in the verdict bar at the very top of the Recommendation
  tab (verdict text left, "Review & execute" right), not in a sticky right rail.
  Rationale: the rail is not a column below 2xl and the grid's `overflow: hidden` kills
  sticky positioning inside it; top-of-tab placement is robust at every width with no
  new scroll machinery, and pairs the action with the plain-language verdict ("what
  happened → act"). The end-of-grid duplicate CTA is removed; the small header CTA and
  the confidence-rail "Rebalance" link remain as secondary paths (layered redundancy is
  an accepted pattern here, cf. the run-status banner plan).
  Date/Author: 2026-07-02 / Claude (owner-delegated).
- Decision: primary CTA keeps the label "Review & execute" and the
  `portfolio-optimizer-recommendation-rebalance-cta` role, and keeps staging into the
  Execution tab. The expert's "Go to Rebalance" label is not adopted: the standalone
  Rebalance tab was retired (2026-06-29; `?otab=rebalance` aliases to `:execution`), and
  tests pin the role + dispatched action.
  Date/Author: 2026-07-02 / Claude.
- Decision: refinement options are demoted with a native uncontrolled `<details>`
  disclosure inside a compacted status card (expert's "Option C"), not a modal/side
  sheet. The card keeps: head row (pip · "Optimization status" · tier/points meta),
  ready title, quality/stability/points/runtime tags, and the post-refinement outcome
  block; the explanatory copy + depth tiles + "Refine now" button move inside the
  disclosure. The in-flight (running) view is unchanged — progress feedback stays
  primary while a refinement runs. We do NOT wire the latent `:open?` state: native
  disclosure needs no dispatch round-trip, matches the trust rail's existing accordions,
  and auto-collapses again after a refinement completes (the card re-renders).
  Date/Author: 2026-07-02 / Claude.
- Decision: the return-views editor stays on the page but collapsed by default inside a
  `<details>` whose summary shows live counts ("Return views · 1 your view · 12 implied").
  The expert suggested moving it to the Inputs tab or a drawer; here the Inputs tab is
  deliberately a *read-only audit* ("Duplicate the scenario before editing inputs"), and
  the editor's in-place edit → auto-rerun loop (stale-banner recompute) is recent,
  tested, deliberate UX (return-views consolidation plan). Collapsing preserves that
  loop and the implied-row "Adopt at" flow while freeing the rail; a new `:collapsible?`
  opt keeps the setup rail and objective-menu usages unchanged.
  Date/Author: 2026-07-02 / Claude.
- Decision: rail order becomes confidence → trust → views editor (was views editor
  first). Trust signals are review content and belong adjacent to the top; input
  editing is by-exception and goes last.
  Date/Author: 2026-07-02 / Claude.
- Decision: reclaimed center space gets a compact "Why this target" card (portfolio
  shape: long/short counts + largest target position; limits hit: engine-reported
  binding constraints, with an explicit "none" state). Only engine-derived facts — no
  invented causal narration. The expert's "Top changes" trade-preview card is NOT
  adopted: the left allocation table already renders per-asset current → target deltas
  with signed change cells and diverging delta bars, and the Execution tab is one click
  away; duplicating three of its rows in the center adds noise, not information.
  Date/Author: 2026-07-02 / Claude.
- Decision: no new results tabs and no relocation of editing to the setup page as part
  of this change. Single-workspace direction is preserved; scope stays inside the
  Recommendation tab's composition + one shared panel opt.
  Date/Author: 2026-07-02 / Claude.

## Outcomes & Retrospective

Landed 2026-07-02. The Recommendation tab now reads review-and-act: verdict + primary
"Review & execute" CTA in the first ~110px of the tab (previously after a ~1900px
grid), a 143px refinement status card with options behind a disclosure, a rail that
leads with confidence/trust and ends with a 42px collapsed views editor, and a new
"Why this target" card that surfaces binding constraints on this page for the first
time.

Retro:
- The expert review's hierarchy diagnosis was right; two of its remedies were adapted
  to codebase reality (CTA target is Execution, not the retired Rebalance tab; views
  editing collapsed in place rather than moved to the read-only Inputs tab).
- The pinned data-role/dispatch test discipline made an aggressive recomposition safe
  — no behavioral assertion changed.
- The highest-value finds came from running the full Playwright edit loop, not the
  layout work itself: the fixed footer eclipsing page tails (systemic; now padded on
  this surface) and unkeyed sibling reconciliation resetting `<details>` state
  mid-edit (fixed with `:replicant/key`). Both would have shipped invisible under
  unit tests alone.
- Follow-up (addressed 2026-07-03, same branch): the latent
  `open-/close-portfolio-optimizer-refinement` actions were retired end-to-end
  (handlers, facade, runtime catalog/registration, action-args, the
  `ui-refinement-open-path` contract + default, view-model `:open?`, tests).
  The footer-eclipse mechanism was also pinned down more precisely than the Step-8
  observation: `main`'s clamped flex cell only breaks clearance when the route root
  ALSO defeats the `min-height: auto` content floor — via an explicit `min-height`
  (the optimizer frame) or explicit `height` (`h-full` on funding-comparison). Fixes:
  `shrink-0` on the optimizer route frame (its existing 3.5rem padding now lands at
  the true page end, so the interim `.optimizer-scenario-surface` 56px CSS was
  removed), `h-full` dropped from the funding-comparison root, and vaults/leaderboard
  roots bumped from 16px to `pb-16` (the fixed footer is ~49px). Verified live per
  route (boxes no longer clamped, padding at the content end) plus 34/34 gates and
  the 46-test Playwright smoke suite at --workers=1.

## Context and Orientation

Page composition (all under `src/hyperopen/views/portfolio/optimize/`):

    scenario-detail-view                    scenario_detail_view.cljs
      scenario-header                       (small actions: Refine · Save · Rerun · Review & execute)
      provenance-strip · scenario-tabs · target-sigma-strip · kpi-strip · stale-banner
      recommendation-tab
        verdict-headline                    results_summary.cljs   ← CTA moves in here
        recompute-banner (running)
        results-panel                       results_panel.cljs
          left: target-exposure-table       (per-asset current → target, delta bars)
          center: frontier-chart
                  refinement-status-card    refinement_status_card.cljs  ← compact + details
          right: views editor (BL only)     return_views_panel.cljs      ← collapsible, moves last
                 result-confidence-rail     results_diagnostics_rail.cljs ← moves first
                 trust-diagnostics-rail
        review-rebalance-cta                ← removed (relocated into verdict)

Pinned test contracts that must keep passing (no assertion edits):

- `scenario_detail_view_test.cljs`: verdict section is the first child of the
  recommendation tab; `portfolio-optimizer-recommendation-rebalance-cta` exists when
  solved, dispatches `[[:actions/open-portfolio-optimizer-execution]]`, contains
  "Already at target" on zero trades; header CTA classes; "From here" rail copy.
- `results_panel_test.cljs`: grid + panel roles; views editor roles
  (`…-your-views-editor`, `…-rows`, `…-summary`, `…-apply`), summary text
  "1 your view · 3 implied", apply dispatch; description copy.

## Plan of Work

1. `results_summary.cljs`: extend `verdict-headline` to render a right-aligned CTA
   (role/action/mute semantics moved verbatim from `review-rebalance-cta`); flex-wrap so
   the button drops below the text on narrow widths. Add `target-context-card`
   ("Why this target"): shape row (counts of positive/negative target weights, largest
   |weight| with label), limits row (from `:diagnostics :binding-constraints`, warning
   tint when non-empty, plain "No constraints binding" otherwise). Skip the card when
   target weights are absent.
2. `scenario_detail_view.cljs`: delete the local `review-rebalance-cta` + its trailing
   `conj`; verdict call now carries the CTA.
3. `refinement_status_card.cljs`: restructure the solved/idle branch as
   status-header (kept) + outcome (kept) + `[:details.optimizer-refinement-disclosure]`
   containing the explanatory sentence + `refine-options`. Keep all data-roles.
4. `results_panel.cljs`: reorder rail children; pass `{:collapsible? true}` to the views
   editor; insert `target-context-card` between chart and refinement card.
5. `return_views_panel.cljs`: `:collapsible?` opt — when set, the section renders as
   `[:details]` with a `[:summary]` header row (title + `return-views/summary-line`),
   body unchanged. Non-BL fallthrough notes stay non-collapsible (they are one-liners).
6. `results.css`: disclosure summary styling (both new `<details>`), verdict CTA sizing,
   target-context card rows, views-editor border adjustment for its new rail position.
7. Tests: new `refinement_status_card_test.cljs` (options inside a closed-by-default
   details; in-flight view unaffected; roles preserved). Extend `results_panel_test.cljs`
   (rail order; views editor renders as details with counts in summary; context card
   renders binding constraints). Comment-only touch-ups where prose drifted.
8. Workbench: new `recommendation_scenes.cljs` (`:optimize` collection): many-assets BL
   fixture (16 rows, warnings, binding constraints) and a no-views variant, composing
   verdict + results-panel the way `recommendation-tab` does.
9. Gates + browser QA per Validation.

## Validation

Required gates (code changed): `npm run check`, `npm test`, `npm run test:websocket`
(run as `npm run gates` for the full matrix). Playwright: smallest relevant spec first —
`npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs -g "optimizer"`
— then broaden only if it fails for reasons this change could cause.

Browser QA (workbench recommendation scenes; required passes for a layout change):
visual, native-control, styling-consistency, interaction, layout-regression, jank —
at 375 / 768 / 1280 / 1440.

Acceptance criteria and results (2026-07-02):

- CTA above the fold with a 16-asset universe: MET. Workbench many-assets scene at
  1440: CTA occupies y 56–109 inside the tab (the results grid — the CTA's old
  position — ends at y 1916). Verified rendering at 375 (CTA wraps full-width under
  the verdict, zero horizontal overflow), 1440 (xl band layout), and 1600 (true 2xl
  three-column rail; CTA top-right).
- Refinement demoted: MET. Compact card is 143px (head + ready line + tags), the
  depth tiles + "Refine now · Thorough · 72 points" render only inside the closed-by-
  default disclosure; the in-flight view is unchanged (pinned by unit test).
- Views editor collapsed: MET. Rail order confidence → trust → editor; collapsed
  editor is 42px showing "Return views · 2 your views · 14 implied"; opened, all 16
  rows, filters, confidence buttons (30px hit targets) and Apply are reachable
  (2052px when open — the bulk removed from the default state). Setup rail and
  objective-menu structure unchanged (shared-panel tests + BL smoke spec green).
- Binding constraints on the tab: MET — "Limits hit · AAPL at cap 25.00% · DOGE at
  floor −6.00%" renders in "Why this target".
- Pinned contracts: MET — zero assertion edits in scenario_detail_view_test /
  results_panel_test; full suite 5001 tests, 0 failures.

Gates: `npm run gates` 34/34 PASS (5706 tests / 30760 assertions, 1m30s).
Playwright: optimizer-view-model-routes.smoke.spec.mjs 14/14; portfolio-regressions
`-g optimizer` 22/22 (both at --workers=1). Browser sessions cleaned up
(`npm run browser:cleanup`).

Browser QA passes (workbench recommendation scenes, preview tooling):
- Visual: verdict bar + CTA, compact status card, "Why this target", rail order —
  screenshots + computed geometry ✓
- Layout-regression: 375 / 1440 / 1600 measured; no horizontal overflow; grid
  breakpoints intact ✓
- Interaction: both disclosures toggle (native details activation), all inner
  controls reachable when open; end-to-end edit loop exercised by the BL smoke spec
  (fill → auto-rerun → confidence click) ✓
- Native-control: no new native form controls; details/summary matches the trust
  rail's existing accordion idiom ✓
- Styling-consistency: tokens only; lint:theme-colors green ✓
- Jank: keyed reconciliation prevents subtree recreation on banner mount/unmount;
  disclosure toggling shifts only content below the card; reduced-motion handling
  untouched ✓
