# History assumptions: rail-level "Apply all recommended" and Run-click auto-apply

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`,
`Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.
Maintain this document in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The portfolio optimizer's setup page (route `/portfolio/optimize/new`, rendered by
`src/hyperopen/views/portfolio/optimize/workspace_view.cljs`) has a "proxy workflow"
for assets whose return history the risk model cannot trust: each such asset gets a
history-assumption card in the center column, and the backend's history-discovery
payload can carry a per-asset `default-assumption` block — a server-recommended setup
(proxy basket or conservative assumption) that one click applies. Today that one click
lives in exactly one place: a green banner above the cards inside the collapsible
"History assumptions" section (`recommended-banner` in
`src/hyperopen/views/portfolio/optimize/setup_history_assumption_recommendations.cljs`).

Two gaps, both reported directly by the owner (2026-07-23, with a screenshot of a
65-asset scenario where 7 assets needed assumptions and all 7 had server
recommendations):

1. The right rail's "History assumptions" panel says "0 of 7 configured" and lists
   every pending asset, but offers no way to act — the user must scroll the center
   column to find the banner. The rail should carry its own "Apply all recommended"
   button.
2. Clicking "Run optimization" while assumptions are still missing runs the pipeline
   into a dead end: `begin-run` (in
   `src/hyperopen/portfolio/optimizer/application/pipeline_workflow.cljs`) sees the
   draft is not runnable, issues a history load, and `after-history-loaded` then throws
   `"History assumptions needed: …"`, failing the run. When server recommendations
   exist for the pending assets, the click should instead apply them all (exactly what
   the banner button does) and proceed with the run.

After this change: the rail shows an "Apply all recommended (N)" button whenever any
pending card carries an applicable recommendation, and a Run click on a blocked draft
first applies every pending applicable recommendation and then starts the pipeline —
the run that used to fail now succeeds whenever recommendations cover the gaps.

## Context References

- Direct user request (repo owner), 2026-07-23 (screenshot: "0 of 7 configured" rail
  beside a center-column "7 assets have server-recommended setups / APPLY ALL
  RECOMMENDED" banner; Status "Action needed"; bottom bar "NEEDS ASSUMPTIONS ·
  65 ASSETS").
- Prior art this builds on (all in-tree):
  - `src/hyperopen/portfolio/optimizer/actions/default_assumptions.cljs` — the bulk
    apply funnel (`apply-portfolio-optimizer-recommended-history-assumptions`): one
    draft save, one assumption-library sync, reference-instrument reconcile, prefetch
    kick, outcome note.
  - `src/hyperopen/portfolio/optimizer/application/view_model/setup_history_assumption_cards.cljs`
    — computes `:recommended-count` and `:recommended-actions` for the center banner.
  - `src/hyperopen/portfolio/optimizer/application/view_model/setup_history_assumption_rail.cljs`
    — the rail projection (currently drops the recommendation aggregates).
  - `src/hyperopen/views/portfolio/optimize/setup_context.cljs` —
    `history-assumptions-rail-panel` renders the rail rows + configured-count.
  - `src/hyperopen/portfolio/optimizer/actions/run.cljs` —
    `run-portfolio-optimizer-from-draft`, the Run button's action.

## Key terms (plain language)

- **Draft**: the editable scenario stored at `[:portfolio :optimizer :draft]`
  (path helpers in `src/hyperopen/portfolio/optimizer/contracts/paths.cljs`).
- **Readiness**: `setup-readiness/build-readiness` derives, from app state, whether the
  draft can run (`:runnable?`) and, when blocked, a `:reason` such as
  `:missing-history-assumptions`.
- **Recommendation / default-assumption**: the backend history-discovery row for an
  asset may carry a `:default-assumption` block (approach, basket members,
  relationship strength, rationale). The pure planner in
  `src/hyperopen/portfolio/optimizer/application/default_assumptions.cljs` turns those
  into concrete assumption entries; it only ever targets assets that are enrolled in
  the workflow (history adequacy `:none`/`:short`) and have no user-authored entry.
- **Actions and effects**: user intent dispatches an action id; the action function is
  pure (state in, effect vector out); nexus applies `:effects/save`/`:effects/save-many`
  projections to the store synchronously before heavy effects run, so a later heavy
  effect in the same vector observes the updated state. Precedent for composing
  projections + the run pipeline in one action:
  `:actions/add-portfolio-optimizer-universe-instrument-and-run` in
  `src/hyperopen/runtime/effect_order_contract.cljs`.

## Design decisions

- The rail button dispatches the SAME action id the center banner dispatches
  (`:actions/apply-portfolio-optimizer-recommended-history-assumptions`), carried via
  the same `:recommended-actions` map the cards view-model already exposes. No new
  action, no new contract surface, no Lean/formal sync needed for Milestone A.
- The rail view-model passes `:recommended-count` and `:recommended-actions` through
  from the cards model rather than recomputing anything — the two surfaces can never
  disagree about how many recommendations are applicable.
- Run auto-apply lives in the ACTION (`run-portfolio-optimizer-from-draft`), not in the
  pipeline workflow. The pipeline stays a pure decision surface; the apply is a
  state-assembling concern that already exists as an effect-vector builder. The action
  prepends the apply effects before the pipeline effect; nexus's
  projection-before-heavy ordering guarantees the pipeline's `begin-run` reads the
  updated draft. The prefetch kick inside the apply effects composes safely: if the
  apply queues out-of-universe basket-member history, `begin-run` sees
  `selection-prefetch-loading?` and waits for idle — the exact machinery the manual
  "apply then click Run" sequence already exercises.
- Auto-apply fires only when the draft is NOT runnable. A runnable draft with a
  pending recommendation on a thin-but-usable asset must not be silently reconfigured
  by a Run click — the user may have deliberately kept native history; the explicit
  banner/rail buttons remain the opt-in for that case. Blocked-for-any-reason (not
  just `:missing-history-assumptions`) auto-applies, because applying recommendations
  only ever moves a blocked draft toward runnability and covers the
  history-still-loading click-ordering edge.
- Auto-apply is skipped when the click does not reach the pipeline: the
  Black-Litterman invalid-editor path returns field-error effects instead of running,
  and mutating the draft's assumptions as a side effect of a rejected click would be a
  surprise.
- If recommendations do not cover every blocked asset, the run proceeds into the same
  load-history → readiness-error path as today (partial coverage still fails with the
  honest message; nothing regresses).
- The apply's outcome note (`Applied N recommended assumptions…`) still writes on the
  auto-apply path, so the user sees what Run just did on their behalf.
- The bottom bar's "Needs assumptions" status pill copy is deliberately unchanged in
  this pass: it remains true (assumptions ARE needed; Run now supplies them). A
  follow-up could soften it to name the auto-apply, but that copy pass is out of scope.

## Milestones

### Milestone A — rail "Apply all recommended" button

The rail model (`history-assumption-rail-model`) gains `:recommended-count` and
`:recommended-actions`, passed through from `history-assumption-cards`. The rail panel
(`history-assumptions-rail-panel` in `setup_context.cljs`) renders, between the header
and the rows list, a full-width success-toned button labeled
"Apply all recommended (N)" with data-role
`portfolio-optimizer-history-assumptions-rail-apply-all-recommended`, shown only when
`recommended-count` is positive, dispatching the `:apply-all` action id from
`:recommended-actions`.

Acceptance: unit tests — the rail model surfaces the aggregates when a pending
recommendation exists and reports zero when the assets are configured; the rendered
rail contains the button wired to
`:actions/apply-portfolio-optimizer-recommended-history-assumptions` exactly when
pending recommendations exist.

### Milestone B — Run click auto-applies pending recommendations

`actions/default_assumptions.cljs` exposes `pending-recommended-apply-effects`
(state → the bulk-apply effect vector, or nil when nothing would apply), refactored
out of the existing `apply-recommended` so the button path and the run path share one
planner. `actions/run.cljs` `run-portfolio-optimizer-from-draft` prepends those
effects when (a) the universe is non-empty, (b) readiness is not runnable, and (c) the
effects it is about to return actually include the pipeline effect.

Acceptance: unit tests — a blocked draft with pending recommendations returns the
apply effects (draft save-many, library sync, note) followed by
`[:effects/run-portfolio-optimizer-pipeline]` last; a blocked draft with no pending
recommendations returns only the pipeline effect (today's behavior); a runnable draft
never auto-applies.

### Milestone C — committed browser coverage

New Playwright spec `tools/playwright/test/optimizer-history-assumptions-recommended.spec.mjs`
using the existing seeding harness (`tools/playwright/support/optimizer_state.mjs`):
seed a universe with one full-history proxy candidate and one no-history asset whose
discovery row carries a `default-assumption`, then (1) assert the rail button appears
and clicking it flips the rail count to "1 of 1 configured", and (2) reseed and assert
a "Run optimization" click authors the assumption (the auto-apply projection is
observable in the draft/rail regardless of how far the synthetic run itself gets).

Acceptance: `npx playwright test optimizer-history-assumptions-recommended` passes
locally (dev server auto-started by the Playwright config, or reuse an existing one
with `PLAYWRIGHT_REUSE_EXISTING_SERVER=true`).

### Milestone D — validation gates

From the worktree root: `npm run setup:worktree` once, then `npm run gates`
(`npm run check`, `npm test`, `npm run test:websocket` with a single PASS/FAIL
matrix). Browser-flow change ⇒ run the new spec plus the neighboring
`optimizer-history-assumptions-io.spec.mjs` first, broaden only after they pass.

## Progress

- [x] ExecPlan authored.
- [x] Milestone A: rail model pass-through + rail button + unit tests.
- [x] Milestone B: `pending-recommended-apply-effects` + run action auto-apply + unit tests.
- [x] Milestone C: Playwright spec added and passing (2 tests, chromium, against the
      worktree's own compiled build served on :8090).
- [x] Milestone D: `npm run gates` matrix all PASS; targeted Playwright first
      (recommended spec, 2 passed), then neighbors
      (`optimizer-history-assumptions-io` + `optimizer-proxy-loading-ux`, 3 passed).
- [ ] Move this plan to `docs/exec-plans/completed/` after owner acceptance.

## Surprises & Discoveries

- The failed-run behavior the owner hit is a deliberate throw:
  `after-history-loaded` raises `(readiness-error-message readiness)` when the
  post-load readiness is still blocked — so "failing the optimization at that point"
  is the designed dead end this plan removes for the recommendations-covered case.
- `begin-run` already handles a just-kicked prefetch (`selection-prefetch-loading?` →
  wait-for-idle command), which is what makes prepending the apply effects safe with
  no pipeline changes at all.
- The effect-order contract registers
  `:actions/add-portfolio-optimizer-universe-instrument-and-run` with
  projection-before-heavy ordering over this same pipeline effect — composing
  "mutate draft, then run" in one action is an established, validated pattern.
- The apply funnel's outcome-note save lands AFTER its prefetch-kick heavy effect in
  the existing button action, so the composed run action's effect ordering
  (projections → sync/prefetch → note → pipeline) introduces no new ordering shape.
- Seeding `history-load-state` `:succeeded` with a `request-signature` in unit tests is
  what unlocks adequacy judgments (`:none`/`:short`) — without it every asset reads
  `:pending` and no card (hence no recommendation) exists. The Playwright seed relies
  on the same trick via `seedOptimizerState`.
- The readiness `:request` memoizes on the draft/history inputs, so the run action's
  extra `build-readiness` call at click time is cheap (same inputs as the render-time
  call the rail just made).
- The center banner renders inside the History-assumptions `<details>` section which
  rests collapsed when nothing "needs attention"; the rail button is always visible
  when applicable — exactly the discoverability gap the owner described.
- (Milestone C) The dev app boots with demo holdings that auto-seed the draft
  universe; the spec neutralizes this by seeding an explicit universe patch, same as
  the IO spec, so the workflow contains exactly the two seeded assets.
- (Milestone D) `npm run check` includes the docs guardrail (`dev/check_docs.clj`),
  which enforces this plan's unchecked-progress-item rule — the final checkbox above
  stays open until the plan moves to `completed/`.

## Decision Log

- 2026-07-23: Rail button reuses the banner's action id end-to-end (no new action).
  Rationale: one funnel, one set of guards (existing entries win, held members
  disclosed), zero contract-surface churn.
- 2026-07-23: Auto-apply gated on `(not runnable?)` rather than firing on every Run
  click. Rationale: a Run click must never silently reconfigure a draft that would
  have run as-authored; the failure case is the only case the owner asked to fix.
- 2026-07-23: Auto-apply implemented in the action layer, not the pipeline workflow.
  Rationale: the pipeline is pure state+commands and cannot emit the library-sync /
  prefetch / note effects the apply requires; the action layer already composes
  projections before the pipeline effect with contract-enforced precedent.
- 2026-07-23: Black-Litterman invalid-editor clicks skip the auto-apply. Rationale:
  the click is rejected with field errors and no run starts, so mutating assumptions
  would be a side effect of a failed click.
- 2026-07-23: Playwright run-click assertion pins the auto-apply projection (rail
  flips to configured + note appears), not a full solved run. Rationale: the seeded
  synthetic history is not guaranteed to satisfy the whole covariance pipeline in a
  45s spec budget; the unit layer already pins the effect composition, and the
  projection is the user-visible contract ("Run applied my recommendations").

## Validation

- Unit: `npm test` (shadow-cljs `:test` build) — new deftests in
  `test/hyperopen/portfolio/optimizer/recommended_assumptions_actions_test.cljs`
  (run-action composition, via a not-runnable variant of the existing `base-state`),
  `test/hyperopen/portfolio/optimizer/application/view_model_history_assumption_recommendation_test.cljs`
  (rail-model pass-through), and
  `test/hyperopen/views/portfolio/optimize/setup_context_test.cljs` (rail button
  render + wiring).
- Gates: `npm run gates` → PASS matrix for `check`, `test`, `test:websocket`.
- Browser: with the main checkout's dev server holding :8080, the worktree recipe is
  `npm run css:build`, `npx shadow-cljs --force-spawn compile app`, serve
  `resources/public` with an SPA-fallback static server on :8090, then
  `PLAYWRIGHT_BASE_URL=http://127.0.0.1:8090 PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test optimizer-history-assumptions-recommended optimizer-history-assumptions-io optimizer-proxy-loading-ux --workers=1`
  → all green (the io and loading-ux specs guard the shared section and rail).

## Outcomes & Retrospective

- 2026-07-23: Implemented and validated. The rail now carries "Apply all recommended
  (N)" (same funnel as the banner), and a Run click on a blocked draft applies every
  pending applicable recommendation before starting the pipeline, so the
  previously-failing run succeeds when recommendations cover the gaps; partial
  coverage still fails with the honest readiness message. Net complexity: slightly
  reduced at the action layer (the button path and run path now share one
  `recommended-apply-plan` builder); one new button in the rail view; pipeline
  workflow untouched. All gates PASS; targeted Playwright (recommended + io specs)
  green. Remaining: owner acceptance, then move this plan to `completed/`.
