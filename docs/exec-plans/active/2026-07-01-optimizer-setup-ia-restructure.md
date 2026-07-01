# Optimizer Setup: Information-Architecture Restructure (center = policy builder)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`,
`Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.
Maintain it in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The optimizer setup route (`/portfolio/optimize/new`) currently has its information architecture
backwards. The controls that actually change the optimizer — objective, return/risk model, and the
constraints (the 2D exposure map, risk guards, rebalance behavior) — are crammed into a narrow
left rail, so a trader must scroll a skinny column to configure critical policy. The wide center
column is spent on passive explanation ("What this scenario will solve for"), and the right column
repeats the same warning ("cached history is stale") once per asset.

After this change, the three columns match what the user is actually doing:

- **Left = Universe only.** Search, load holdings, the asset list, and per-asset side toggles.
- **Center = the editable Scenario Policy.** Objective, return/risk model, Positioning (the 2D
  gross/net exposure map + band sliders), Risk guards, Rebalance behavior, an Advanced solver
  drawer, and a small collapsed "Why this preset is safe" note at the bottom.
- **Right = live summary + readiness.** A one-line compact summary card, the Run CTA, and a
  Readiness panel whose warnings are grouped ("13 assets use stale cached history" with an
  expandable affected-asset list) instead of repeated fifteen times.

You can see it working by opening Portfolio → Optimize → New: the exposure map and all policy
controls are above the fold in the wide center column, the left column is just the universe, the
right column shows a compact summary + Run + grouped warnings, and the validation gates + updated
layout tests pass.

## Context References

Public refs:
- Direct designer feedback relayed by the maintainer (2026-07-01): "The current layout is
  backwards… center should be the scenario builder, left should be universe selection, right
  should be readiness/warnings/summary. Group repeated warnings. The 2D map is a good control —
  stop hiding it below the fold; put it in the center under Positioning."

Repo artifacts:
- Builds directly on the completed exposure-map work:
  `/hyperopen/docs/exec-plans/completed/2026-06-30-optimizer-exposure-map-constraints.md`.
- Governed UI docs: `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`,
  `/hyperopen/docs/agent-guides/trading-ui-policy.md`, `/hyperopen/docs/BROWSER_TESTING.md`.

Local scratch refs (non-authoritative): None.

## Progress

- [x] (2026-07-01) Mapped the current 3-column IA (rails, grid CSS, readiness/warnings pipeline,
  summary+actions, and every layout test that asserts control placement/copy).
- [x] (2026-07-01) Authored this ExecPlan.
- [x] (2026-07-01) R1 — Column content move. `control-rail` → universe-only; new `policy-pane`
  (center) holds objective/model/constraints/advanced + a collapsed "Why this preset is safe"
  note + the Run bottom bar (kept in center per the designer's "right column OR bottom bar");
  the passive 6-row summary is replaced by a compact `summary-card` in the right column, and the
  non-BL "why safe" moved out of the right rail. Grid widened center (5/12/7); Tailwind safelist +
  CSS updated; orphan `setup_summary.cljs` deleted. Black-Litterman path preserved. Updated the
  cljs placement/copy tests (control-rail→universe order, policy-pane order, summary-card, run
  placement). `npm test` green (4936 tests); hiccup/theme/boundary/styles lints + portfolio build
  clean.
- [x] (2026-07-01) R2 — Grouped/expandable readiness warnings. Pure `group-readiness-warnings`
  (by `:code`, first-seen order; count + affected-asset labels via a now-public
  `setup-readiness/warning-asset-label` + new `warning-code-summary`); `readiness-panel-model`
  emits grouped rows; the panel renders one row per kind with a count badge and a `<details>`
  "Show N assets" list (`data-role portfolio-optimizer-readiness-warning-assets`), keeping the
  `portfolio-optimizer-readiness-warning` role. Raw readiness is untouched. Updated the boundary
  test to the grouped shape + added a 3-warning→2-group test. `npm test` green (4937 tests).
- [ ] R3 — Playwright layout-spec updates, full gates, browser verification.

## Surprises & Discoveries

- Observation: The section views already reflow when widened. Evidence: objective cards are
  `grid-cols-1 sm:grid-cols-2`, model segments `grid-cols-3`/`grid-cols-4`, constraint rows
  `grid-cols-[minmax(0,1fr)_92px]` — all stretch cleanly in a wide center column. The only
  narrow-authored artifact is the exposure-map WORKBENCH scene (`max-width:360px`), not the live
  view.
- Observation: Stale-history warnings are one-per-asset. Evidence: `setup_readiness/build-readiness`
  keeps `{:code :stale-history :instrument-id "…"}` per asset in `readiness :warnings` (non-blocking),
  so a 13-stale universe yields 13 near-identical rows. Grouping must happen purely in the
  view-model projection — `history-status-by-instrument` and the history-assumption cards consume
  the raw `:warnings`/`:blocking-warnings` lists, so those must not be mutated.
- Observation: A pre-existing test conflict. Evidence: `setup_layout_test.cljs:84-91` asserts panel
  numbering ("02Objective") is ABSENT (current code removed numbering), while
  `portfolio-regressions.spec.mjs:1525-1528` asserts it is PRESENT. The Playwright spec is stale;
  reconcile toward the unnumbered current code.
- Observation: `src/hyperopen/views/portfolio/optimize/setup_summary.cljs` is a dead orphan (no live
  requirer; the live summary uses the VM's private `universe-summary`). Safe to delete.

## Decision Log

- Decision: Move the existing objective/model/constraint controls into the center AS-IS; do not
  redesign the objective picker into inline segmented buttons.
  Rationale: The designer's "minimal implementation change" says "Move Objective, Return/Risk Model,
  and Constraints from the left column into the center." The segmented-button mock is illustrative.
  The current objective control hosts the Black-Litterman "use my views" belief flow, which is
  load-bearing; redesigning it is a separate, riskier change. Flagged as a designer question.
  Date/Author: 2026-07-01 / Claude.

- Decision: One Run CTA, relocated to the right column (near readiness), not two.
  Rationale: Today there is exactly one Run ("Run on safe defaults", `data-role
  portfolio-optimizer-run-draft`) and the tests assert it is absent from the header. The designer
  wants Run near readiness ("[Run on safe defaults]" in the right column). Adding a second header
  Run would contradict the "exactly one run-draft" contract and risk confusing dual CTAs. Keep one,
  move it right. Flagged as a designer question.
  Date/Author: 2026-07-01 / Claude.

- Decision: The passive 6-row summary becomes a compact one-line card in the RIGHT column; the
  center shows no summary table.
  Rationale: The designer offered "right column OR compact card atop center" and the shipped
  layout puts it in the right column above Readiness. One line frees the center for controls.
  Date/Author: 2026-07-01 / Claude.

- Decision: "Why this preset is safe" becomes a collapsed `<details>` at the BOTTOM of the center
  policy column (non-BL path); the Black-Litterman branch keeps its views editor.
  Rationale: The designer said "below or beside the controls… small/expandable… do not put it above
  the controls." Bottom-of-center collapsed keeps it available without dominating.
  Date/Author: 2026-07-01 / Claude.

- Decision: Defer per-column independent scrolling to a follow-up; keep whole-page scroll for now.
  Rationale: The primary complaint (controls buried below the fold in a narrow rail) is fixed by the
  MOVE itself. Independent scroll needs a bounded-height layout the route doesn't have today and
  adds risk. Flagged as a designer question.
  Date/Author: 2026-07-01 / Claude.

## Outcomes & Retrospective

To be completed at milestone boundaries. Expectation: the common configuration path becomes far
more usable (policy controls above the fold in the wide center; warnings readable at a glance);
internal complexity rises modestly (one column-content reshuffle + one pure warning-grouping fn),
with the heaviest cost being the many placement/copy TEST updates that pin the old layout.

## Context and Orientation

The setup route is a pure Replicant/hiccup tree. `workspace-view`
(`src/hyperopen/views/portfolio/optimize/workspace_view.cljs`) renders a header, a preset row, an
optional infeasible banner, then a 3-column CSS grid (`optimizer-setup-surface`) whose track string
is `xl:grid-cols-[minmax(420px,7fr)_minmax(0,11fr)_minmax(360px,6fr)]` (mirrored in
`tailwind.config.js` safelist). The three grid children, in DOM order, are:

- `setup/control-rail` — LEFT `<aside class="optimizer-control-rail">`
  (`setup_sections.cljs` L17): universe → objective → model → constraints → Advanced Overrides.
- `setup/summary-pane` — CENTER `<main class="optimizer-summary-pane">`
  (`setup_sections.cljs` L58): "What this scenario will solve for" summary rows + model-assumptions
  + `setup-bottom-actions` (Run/Save). Black-Litterman branch renders `use-my-views-workspace`.
- `setup-context/context-rail` — RIGHT `<aside class="optimizer-context-rail">`
  (`setup_context.cljs` L11): "Why this preset is safe" (or the BL views editor) + Trust & Freshness
  (snapshot status, progress, readiness/warnings, run status, results links).

Key term definitions: "rail" = a grid column; "policy" = the objective/model/constraint choices
that determine the optimizer result; "readiness" = whether the run is safe/valid/ready; a
"disclosure panel" = a native `<details>`/`<summary>` collapsible.

Supporting pure/view files:
- `src/hyperopen/portfolio/optimizer/application/view_model/setup.cljs` — `setup-summary-model`
  (6 rows: Preset/Universe/Expected Returns/Objective/Constraints/Horizon), `readiness-panel-model`
  (projects `:warnings` to `{:message :code-label}` rows), private `universe-summary`/`active-preset`.
- `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` — `build-readiness`,
  `warning-display-message` (per-`:code` case), `warning-asset-label`, `stale-history-warning-codes`,
  `insufficient-history-warning-codes`, `history-status-by-instrument` (consumes raw warnings).
- `src/hyperopen/views/portfolio/optimize/setup_actions.cljs` — `setup-bottom-actions` (Run/Save +
  status pill), `run-status` (pure CTA/pill deriver). Run: `data-role portfolio-optimizer-run-draft`,
  `:disabled (not run-triggerable?)`, on-click branches on Black-Litterman.
- `src/hyperopen/views/portfolio/optimize/setup_readiness_panel.cljs` — flat per-warning render.
- Section entry points that MOVE columns: `setup_objective_controls.cljs` (objective-section),
  `setup_model_controls.cljs` (model-section), `setup_constraint_controls.cljs` (constraints-section),
  `instrument_overrides_panel.cljs` (Advanced Overrides), `setup_universe.cljs` (universe-section, stays left).
- CSS: `src/styles/surfaces/optimizer/setup.css` (rail backgrounds/borders; the
  `.optimizer-control-rail > :is(section,details)` seam-border styling; no widths/scroll here).

Tests that pin the OLD layout (must update in lockstep — full list in Plan of Work):
- `test/hyperopen/views/portfolio/optimize/setup_layout_test.cljs` — control-rail child-roles+order,
  run/save placement in center, "What this scenario will solve for" copy, panel-numbering asserts.
- `setup_view_test.cljs`, `workspace_view_test.cljs`, `setup_use_my_views_workspace_test.cljs`,
  `setup_readiness_panel_test.cljs`, `view_model_setup_boundary_test.cljs`, `setup_layout_fixtures.cljs`.
- Playwright: `tools/playwright/test/portfolio-regressions.spec.mjs` (control-rail order, bottom-actions
  boundingBox in summary-pane, numbered-panel copy), `optimizer-exposure-map.spec.mjs`,
  `optimizer-black-litterman-views.spec.mjs`, `optimizer-view-model-routes.smoke.spec.mjs`.
  Robust (no update): existence-only `node-by-role`/`page.locator` and attribute-on-role checks.

## Plan of Work

### R1 — Column content move (left = universe, center = policy, right = summary + Run + readiness)

Goal: the structural move. Nothing about which controls exist changes; only which column renders
them, plus a compact summary and a relocated Run.

1. `setup_sections.cljs`: split `control-rail` into two builders. `control-rail` (LEFT) renders
   ONLY `(setup-universe/universe-section …)`. New `policy-pane` (CENTER, a `<main
   class="optimizer-policy-pane">`, `data-role portfolio-optimizer-setup-policy-pane`) renders
   objective-section, model-section, constraints-section, the Advanced Overrides disclosure, and (non-BL)
   a collapsed "Why this preset is safe" `<details>`. For Black-Litterman keep the
   `use-my-views-workspace` as the center policy body (it already edits the return model = policy).
   Delete the now-passive summary heading + 6-row table from the center.
2. New RIGHT-column content in `setup_context.cljs` `context-rail`: prepend a compact summary card
   (`data-role portfolio-optimizer-setup-summary-card`) rendering one line
   "`<preset> · <n> assets · <objective> · <return model> · gross a–b · net c–d · cap X%`" from a new
   pure `setup-summary-card-model` in `view_model/setup.cljs`. Add the relocated Run CTA (extract
   `run-cta` from `setup_actions.cljs`, preserving `data-role portfolio-optimizer-run-draft`, the
   dual Black-Litterman dispatch, `:disabled (not run-triggerable?)`, and the status pill). Keep
   the existing Trust & Freshness / readiness content below.
3. `workspace_view.cljs`: render `control-rail` (left) → `policy-pane` (center) → `context-rail`
   (right). Change the grid track string to widen the center and narrow the left, e.g.
   `xl:grid-cols-[minmax(300px,5fr)_minmax(0,12fr)_minmax(360px,7fr)]`, and add the new string to the
   `tailwind.config.js` safelist (the old string can stay or be removed).
4. CSS (`setup.css`): make the center policy panels carry the same seam-panel styling the left rail
   had (generalize `.optimizer-control-rail > :is(section,details)` to also cover
   `.optimizer-policy-pane > :is(section,details)`), and give `.optimizer-policy-pane` a surface
   background/border consistent with the rails. Keep whole-page scroll (independent scroll deferred).
5. Delete the orphan `setup_summary.cljs` after confirming no requirer.
6. Update cljs tests in lockstep: `setup_layout_test.cljs` (control-rail child-roles → `[universe]`;
   the policy panels now assert membership in the policy-pane; remove/upgrade the "What this scenario
   will solve for" copy assertion; reconcile numbering; move the run/save placement test to the new
   Run location and the "exactly one run-draft / absent from header" invariant); `setup_view_test.cljs`
   (keep model-grid/column roles); `setup_use_my_views_workspace_test.cljs` (Run location);
   `setup_layout_fixtures.cljs` if a helper needs a new accessor. Preserve every control's existing
   `data-role` so existence/attribute tests stay green.

Acceptance: `npm test` green; the exposure map + all policy controls render in the center; left is
universe-only; right shows the compact summary + Run + readiness. `lint:hiccup`, `lint:theme-colors`,
`test:styles`, and the `portfolio` build pass.

### R2 — Grouped / expandable readiness warnings

Goal: stop rendering the same warning N times.

1. Pure view-model: add `group-readiness-warnings [readiness]` to `view_model/setup.cljs` that groups
   the chosen warning list (`(or (seq blocking) warnings)`) by `:code`, preserving first-seen order,
   emitting `{:code :code-label :count :summary-message :assets [{:instrument-id :label}]}` where
   `:label` = `setup-readiness/warning-asset-label` (needs `(:request readiness)`), and `:summary-message`
   is a code-level sentence (e.g. "Optimizer history may be stale for 13 assets."). Do NOT mutate
   `readiness`. Have `readiness-panel-model` expose the grouped rows (keep the single-warning shape a
   count-1 group).
2. View: `setup_readiness_panel.cljs` renders each group as one row (keep `data-role
   portfolio-optimizer-readiness-warning`) with a count badge and, when `count > 1`, a native
   `<details>` "Show N assets" whose body lists the affected labels
   (`data-role portfolio-optimizer-readiness-warning-assets`).
3. Tests: add a VM test (13 stale → one group, count 13, 13 labeled assets); update
   `view_model_setup_boundary_test.cljs` and `setup_readiness_panel_test.cljs` to the grouped shape;
   keep `workspace_view_test.cljs`'s readiness-warning existence + `missing-candle-history` copy green.

Acceptance: a 13-stale universe shows one "stale history" row with a count and an expandable
13-asset list; `npm test` green.

### R3 — Playwright, gates, browser verification

1. Update the Playwright layout specs to the new IA: control-rail children (`portfolio-regressions.spec.mjs:1529-1538`)
   → universe-only; bottom-actions/Run scope + boundingBox (`:1658-1695`) → the new Run location;
   reconcile the numbered-panel copy (`:1525-1528`) with the unnumbered current code; keep
   `optimizer-exposure-map.spec.mjs` (constraints panel still a `<details>` in the center) and the BL
   specs green (summary-heading/panel remain absent).
2. Run `npm run setup:worktree`, then `npm run gates` (single PASS/FAIL matrix), then the smallest
   relevant Playwright command; broaden to the optimizer set after it passes.
3. Browser-verify the live route in the workbench/preview: left=universe, center=policy above the
   fold, right=compact summary + Run + grouped warnings.

Acceptance: `npm run gates` PASS; the updated Playwright layout specs pass; the rendered route matches
the target IA.

## Concrete Steps

Run from the worktree root:

    npm run setup:worktree
    npm test
    npm run gates
    npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --workers=1

Per-milestone, prefer the fastest gate first (`npm test` for view/VM logic; `npm run lint:hiccup &&
npm run test:styles` for view/CSS) and broaden to `npm run gates` before marking a milestone done.

## Validation and Acceptance

Behavioral: open Portfolio → Optimize → New. The left column shows only Universe (search + asset
list). The center shows the editable policy — Objective, Return/Risk model, Positioning (the 2D map
+ band sliders), Risk guards, Rebalance behavior, an Advanced drawer, and a collapsed "Why this
preset is safe" note. The right column shows a one-line summary card, a Run CTA, and a Readiness
panel where "stale history" appears once with a count and an expandable affected-asset list. Test:
new/updated unit tests fail before and pass after; `npm run gates` reports PASS; the updated
Playwright layout specs pass.

## Idempotence and Recovery

Every step is additive/reversible. The move relocates existing view calls between three container
functions and regroups warnings in a pure projection; it changes no action, effect, spec, or solver
behavior, and preserves every control's `data-role`. If a milestone is reverted, earlier ones remain
green. Keep the Black-Litterman branch working at each step (it is the main non-obvious path).

## Artifacts and Notes

Mapping evidence and exact breaking-test line anchors are captured under Context and Orientation and
the R-milestone steps. Keep short failing-then-passing test transcripts here as milestones complete.

## Interfaces and Dependencies

New pure `view_model/setup.cljs` functions:

    (setup-summary-card-model [draft opts]) ; → {:preset-label :asset-count :objective-label
                                            ;    :return-label :gross [min max] :net [min max] :cap}
    (group-readiness-warnings [readiness])  ; → [{:code :code-label :count :summary-message
                                            ;     :assets [{:instrument-id :label}]}]

New view builders: `setup_sections.cljs/policy-pane`, `setup_actions.cljs/run-cta`,
a compact summary card + grouped-warnings render. No new libraries.

## Note on revisions

2026-07-01 (initial): Authored from a full subsystem mapping of the 3-column IA and every layout
test that pins control placement/copy. Records the center=policy / left=universe / right=summary
move, the pure warning-grouping approach, the Run/summary relocation, and the Black-Litterman
preservation constraint. Reason: designer feedback (relayed by the maintainer) that the layout is
backwards.
