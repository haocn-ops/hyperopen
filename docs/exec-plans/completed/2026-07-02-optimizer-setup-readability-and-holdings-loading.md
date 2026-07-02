# Optimizer Setup: Readable Type Scale and a Visible Holdings-Loading State

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`,
`Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.
Maintain it in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The optimizer setup route (`/portfolio/optimize/new`) recently became an opinionated
assistant: it seeds its asset universe from the user's holdings automatically and lands on
results after a run. Two usability problems remain, both raised by a maintainer-relayed
designer review on 2026-07-02.

First, the page is set almost entirely in 8.5–11px type (Tailwind arbitrary classes like
`text-[0.53125rem]`…`text-[0.6875rem]`, plus 9–10.5px rules in the setup stylesheet), most
of it uppercase monospace. Primary interface concepts — section titles, asset symbols,
constraint numbers, run status — require leaning in to read. The fix is a raised type
ladder with real hierarchy, not less density: primary body/values 13px, secondary 12px,
micro tags 10–11px, section titles 14px sentence case, monospace reserved for numbers and
identifiers.

Second, the holdings auto-seed has no visible pending state. Between route entry and the
arrival of account data over the websocket, the page shows an empty universe, and the run
bar says "Add assets to run" — instructing the user to do the exact work the machine is
about to do. After this change the waiting window is explicit: the Universe panel shows
"Loading holdings…" with skeleton rows, the Scenario-contract card's Universe row reads
"Loading holdings…", the Readiness copy says holdings are being waited on, and the Run CTA
reads "Loading holdings…" (disabled) until the seed resolves or the user takes over.

You can see it working by opening `/portfolio/optimize/new` with a spectated account on a
cold reload: the Universe panel shows the loading strip and skeleton rows for the first
seconds, then flips to "From holdings · N included · M omitted" as the seed lands, the Run
bar flips from "Loading holdings…" to "Ready to run", and every label on the page is
readable at normal viewing distance.

## Context References

Public refs:
- Direct maintainer request (2026-07-02) relaying an expert design review: raise the type
  scale (13px body / 14–16px titles / uppercase-mono only for tags), add a modeless
  inline holdings-loading state (Universe panel + scenario contract + run gating; no
  full-page loader), demote explanation blocks to a collapsed tertiary tier, and keep
  density through hierarchy rather than tiny text. Maintainer delegated final design
  judgment to the implementer.

Repo artifacts:
- Builds directly on `/hyperopen/docs/exec-plans/completed/2026-07-01-optimizer-default-path-and-results-reveal.md`
  (holdings-by-default seeding, per-source arrival watcher, draft autosave/restore,
  collapsed-header live summaries) and
  `/hyperopen/docs/exec-plans/completed/2026-07-01-optimizer-setup-ia-restructure.md`
  (3-column layout: universe / policy / contract rail).
- Governed docs: `/hyperopen/docs/agent-guides/ui-foundations.md` (24px minimum targets,
  contrast), `/hyperopen/docs/agent-guides/trading-ui-policy.md` (full instrument
  identity, consistent terminology), `/hyperopen/docs/BROWSER_TESTING.md`.

Local scratch refs (non-authoritative): None.

## How the pieces fit (orientation for a novice)

The setup page is rendered by `src/hyperopen/views/portfolio/optimize/workspace_view.cljs`,
which calls `workspace-model` in
`src/hyperopen/portfolio/optimizer/application/view_model/workspace.cljs` to derive every
input the views need from the app state, including `readiness` — a pure map built by
`build-readiness` in `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs`
whose `:reason` keyword (`:missing-universe`, `:history-loading`, …) names the single
blocking condition. The status pill and Run button derive from `run-status` in
`src/hyperopen/views/portfolio/optimize/setup_actions.cljs`; the right-rail Readiness copy
comes from `readiness-copy` in
`src/hyperopen/portfolio/optimizer/application/view_model/setup.cljs`.

Holdings seeding: on `/optimize/new` with an untouched draft, a route funnel
(`actions/draft_persistence.cljs`) restores a persisted per-wallet draft from IndexedDB
or, failing that, preseeds the universe from holdings; a store watcher
(`infrastructure/draft_autosave.cljs`) re-enters that funnel when a holdings source
(perp clearinghouse snapshot at `[:webdata2 :clearinghouseState]`, spot balances at
`[:spot :clearinghouse-state :balances]`) arrives. `holdings-sources-signature` in
`application/current_portfolio.cljs` reports per-source availability, and
`untouched-draft?` in `defaults.cljs` says whether the draft is still pristine. There is
today no state that says "we are waiting for holdings" — this plan derives it purely from
those existing facts, adding no store paths, actions, or effects.

## Milestones

### M1 — Derived holdings-loading state threaded through the readiness contract

The waiting window is fully derivable: route is `:optimize-new`, the draft is untouched,
an effective account address exists (someone's holdings are coming), and the perp
clearinghouse snapshot has not yet arrived (`(:perp? (holdings-sources-signature state))`
is false). `workspace-model` computes this and, when readiness is blocked on
`:missing-universe`, overrides the reason to `:holdings-loading` — so every existing
consumer (status pill, readiness panel, Run CTA, universe panel, contract card, all of
which already receive `readiness`) agrees without new props. Terminal honesty: if account
data arrives and the book is empty, the seed action writes nothing and the perp-arrived
check ends the loading state, falling back to the existing empty-universe copy; a
restored custom draft makes the draft touched, which also ends it. Copy cases are added to
`readiness-copy`, `readiness-error-message`, and `run-status-label`; `run-status` gains a
`:holdings-loading` branch (tone `:busy`, label "Loading holdings…") ahead of the
zero-assets branch; the Run button label reads "Loading holdings…" while pending.

The Universe panel, while pending, marks the "My holdings" segment active, replaces the
empty-state copy with a "Loading holdings…" line ("Fetching the current portfolio for
this account.") and three shimmer skeleton rows (CSS in
`src/styles/surfaces/optimizer/universe.css`, `prefers-reduced-motion` disables the
sweep). The Scenario-contract card's Universe row shows "Loading holdings…", and the
Trust & Freshness rail treats `:holdings-loading` as a visible readiness reason.

Acceptance: unit tests pin the derivation (untouched+address+no-perp ⇒ loading; arrival,
touched draft, missing address, scenario route ⇒ not), the `run-status` branch, the
readiness copy, the skeleton render, and the contract-card row. Live: cold reload of
`/optimize/new` while spectating shows the loading strip, then resolves to the seeded
universe with no interaction.

### M2 — Raised type ladder across the setup surface

Uniform size sweep over the setup-page view namespaces (`setup_*`, `workspace_view`,
`instrument_overrides_panel`, `optimization_progress_panel`, `run_status_panel`,
`infeasible_panel`, `target_sigma`) mapping the old arbitrary sizes up one tier:
0.6875/0.7/0.71875rem → 0.8125rem (13px primary); 0.65/0.65625rem → 0.75rem (12px
secondary); 0.58/0.59375/0.6/0.625rem → 0.6875rem (11px micro); 0.53125/0.55/0.5625/
0.575rem → 0.625rem (10px floor for chips/table headers). Hand-tuned exceptions: section
titles become 0.875rem (14px) sentence case (uppercase/tracking dropped); segmented picker
buttons drop uppercase; the universe asset symbol is 13px semibold with the secondary
name/id line demoted to 11px muted; the contract card gets a wider label column and a
12px mono constraints line; the run-bar status pill stays a 12px tag; the universe help
text stops being monospace. `setup.css` px/rem font rules (9–10.5px cluster) are raised to
match, and the setup surface's inherited base becomes 13px. A short comment in
`setup_controls.cljs` documents the ladder so future edits stay on it.

Acceptance: no remaining sub-0.625rem text classes in the setup namespaces; gates green;
visual QA confirms titles ≥ body ≥ metadata ≥ tags with no clipped rows or overflowing
panels at the xl 3-column layout and single-column mobile width.

### M3 — Hierarchy: demote explanations, keep contracts scannable

"What this model assumes" (`model-assumptions-panel` in `setup_actions.cljs`) becomes a
collapsed disclosure like the existing "Why this preset is safe" note, preserving its
`data-role` and its position in the policy-pane child order (Playwright pins that order).
Collapsed section headers keep carrying live values (already built) — the sweep in M2
makes them readable.

### M4 — Gates, browser QA, retrospective

`npm run gates` (check / test / websocket matrix), the smallest relevant Playwright specs
(`portfolio-regressions`, `optimizer-view-model-routes.smoke`), and live browser QA on the
worktree build (static-server recipe on :8090 when the main checkout holds :8080; force
renders in hidden preview tabs). Update this plan's living sections and move it to
`completed/`.

## Progress

- [x] (2026-07-02) Mapped the surface: type-size inventory per file, seeding funnel +
  per-source arrival watcher, readiness/run gating, contract-card model, existing tests.
- [x] (2026-07-02) Authored this ExecPlan.
- [x] (2026-07-02) M1 — `holdings-loading?` + `with-holdings-loading-reason` in
  `view_model/workspace.cljs`; copy cases in `view_model/setup.cljs` (readiness-copy),
  `setup_readiness.cljs` (error message), `setup_actions.cljs` (pill branch + label map +
  CTA "Loading holdings…"); universe panel pending strip/count/skeleton block
  (`setup_universe.cljs`) with shimmer CSS in `universe.css` (reduced-motion guarded);
  contract-card Universe row + readiness visibility in `setup_context.cljs`. Unit tests:
  new `view_model/workspace_loading_test.cljs` (5 derivation facts + rail copy),
  run-status/bottom-actions cases, universe skeleton render, full-page mirror test; three
  layout tests that pinned the old "manual affordance while data pends" behavior updated
  to the new intent (no-account fixtures where the intent was "nothing loaded").
- [x] (2026-07-02) M2 — ordered sed sweep across 19 setup view namespaces (13 size
  mappings, descending so produced sizes are never re-bumped), hand-tuned exceptions
  (14px sentence-case section titles, 13px asset symbols with 11px demoted id line,
  92px contract-card label track + 12px mono constraints line, 12px status tag on the
  run bar in readable muted tokens replacing sub-AA `#444951`/`#5a5f68` literals,
  sentence-case segmented pickers and source strip, sans-serif universe help prose);
  `setup.css` raised on the same ladder (9–10.5px cluster → 11–12px, exposure-map micro
  labels → 10px floor, load-bearing axis titles → 11px) plus a 13px inherited base on
  the setup route surface; tailwind safelist gained the new 92px grid string.
- [x] (2026-07-02) M3 — "What this model assumes" demoted to a collapsed disclosure
  (same tertiary tier as "Why this preset is safe"), data-role and policy-pane child
  order preserved.
- [x] (2026-07-02) Gates: `npm run gates` 34/34 PASS (5678 tests / 30594 assertions,
  incl. `npm test` 4973/0 and websocket suite). One namespace-size trip resolved by
  housing the new derivation tests in their own namespace instead of growing
  `view_model_test.cljs` past its exception.
- [x] (2026-07-02) M4 — Playwright: `optimizer-view-model-routes.smoke` +
  `portfolio-regressions` at `--workers=1`: 47/48, the 1 failure ("persisted scenario
  hydrates results and tracking after reload") passes in isolation alongside the
  model-layers test (2/2) — an ordering flake in a spec area this change does not touch.
  Live QA on the worktree dev build (preview server, spectating
  0xfc667adba8d4837586078f4fdcdc29804337ca06): cold load with the persisted draft deleted
  auto-seeded 50 instruments ("From holdings · 50 included · 3 omitted", omission
  disclosure, LOADING history chips); the restored-draft path correctly bypassed the
  loading state (custom-cleared draft rehydrated with its constraint numbers); a
  spot-only account (0x162cc7…) correctly fell back to the manual empty state on arrival
  (no stuck loader). The pending window itself was pixel-elusive (webData2 re-delivers
  sub-second on a warm spectate socket), so it was verified live by an atomic
  state-surgery + synchronous `render-app!` + DOM query: skeleton block ("Loading
  holdings… / Fetching the current portfolio for this account.", 3 shimmer rows,
  `data-holdings-loading=true`), Run CTA "Loading holdings…" disabled, pill
  "Loading holdings · 0 assets" (tone busy), contract-card Universe row
  "Loading holdings…", "My holdings" segment active. Computed-style checks pin the
  ladder: section title 14px sentence case, CTA/asset symbols 13px, secondary id line
  11px, pill 12px at the muted token, detail line brighter, card labels 10px / values
  12px, chips 10px, collapsed constraints summary 12px non-uppercase.

## Surprises & Discoveries

- Observation: the "loaded" and "partial/omitted" states the designer asked for already
  exist (universe source line "From holdings · N included · M omitted" with an expandable
  reasons list, from the 2026-07-01 default-path work). Only the *pending* state is
  missing, which shrinks this plan to a derivation + presentation change.
- Observation: `set-portfolio-optimizer-universe-from-current` returns `[]` (writes
  nothing) when the arrived snapshot yields no seedable universe, so the draft stays
  untouched for empty accounts — the loading predicate must therefore key off source
  *arrival* (perp snapshot present), not off "seed happened", or an empty account would
  show "Loading holdings…" forever.
- Observation: `text-[0.62rem]` occurrences are all in results-surface files
  (`frontier_chart`, `refinement_status_card`), not the setup page — the results/execution
  surfaces keep their existing scale and are explicitly out of scope here.
- Observation: the run bar's status tag used raw hex `#444951`/`#5a5f68` on `#101518` —
  roughly 2.2:1 / 3.2:1 contrast, failing WCAG AA for 12–13px text, and
  `portfolio-regressions` pins "the detail line is component-wise brighter than the tag".
  Replacing the hexes with the readable muted token initially EQUALIZED the two lines and
  would have broken that pin; resolved by promoting the detail line ("Solving <objective>
  · <model>" — the most informative text in the bar) to `text-trading-text/90`, keeping
  the pinned relationship true and the whole bar AA-readable.
- Observation: three layout tests rendered `/optimize/new` with a wallet address and no
  clearinghouse data and asserted the manual "Load my holdings" affordance — under the
  new semantics that exact state IS the pending window, so the pins were updated
  deliberately (no-account fixtures where the tests meant "nothing loaded", new-copy
  assertions where they meant "cold load with an account").
- Observation (live QA): a spectated account holding ONLY spot balances (0x162cc7… with
  19 spot balances, zero perp positions on any dex) resolves to the plain empty state
  with NO omission accounting, because the seed writes nothing when zero exposures are
  usable — pre-existing behavior, filed as a follow-up task ("Account for spot-only
  holdings omitted from optimizer seed") rather than scope-crept here.
- Observation (live QA): `app.bootstrap/render-app!` takes the STATE as an argument
  (`(render-app! @store)`); calling it bare renders `app-view nil` — a header-only
  shell that convincingly imitates a route gate. Cost this session two false trails
  during state-surgery QA.
- Observation (live QA): the rail Readiness copy prioritizes a sticky
  `history-load-state` status over the readiness reason (`history-load-copy`), so in a
  surgically-forced pending state mid-session it can read "history is loaded" while the
  pill says "Loading holdings". Unreachable on a real cold load (history state is
  `:idle` there, and the copy falls through to the holdings-wait line); left as-is.

## Decision Log

- Decision: derive the holdings-loading state purely (route + untouched draft + account
  address + perp source not yet arrived) inside `workspace-model`, and express it by
  overriding the readiness `:reason` from `:missing-universe` to `:holdings-loading`.
  Rationale: every affected view already consumes `readiness`; a store status written by
  actions/effects would add contract surface (paths, specs, action registration) and a
  second source of truth that could disagree with the facts it summarizes. The designer's
  warning ("don't infer loading from an empty asset list") is honored because the
  predicate distinguishes an untouched draft awaiting data from a deliberately cleared or
  custom universe (both make the draft touched / record a custom source).
  Date/Author: 2026-07-02 / Claude.
- Decision: no failure/retry state for holdings, and no "Refresh holdings" affordance.
  Rationale: holdings arrive over live websocket subscriptions — there is no fetch that
  can fail discretely, no staleness to refresh (the snapshot is continuously current),
  and the existing websocket diagnostics own connection loss. Fabricating a retry button
  would claim control the app does not have. The designer's "failed" case maps to the
  no-account path, which keeps the existing manual affordances instead of a loader.
  Date/Author: 2026-07-02 / Claude.
- Decision: disable the Run CTA (label "Loading holdings…") during the pending window,
  as an exception to the established "run-as-retry stays enabled while blocked" posture.
  Rationale: run-as-retry exists for user-fixable blockers (history retries); the pending
  window is a machine state a click cannot advance — `run-triggerable?` is already false
  with an empty universe, so this only changes the label from a misleading "Add assets to
  run" to the truth.
  Date/Author: 2026-07-02 / Claude.
- Decision: raise sizes but keep the section order Objective → Model → Constraints and
  keep the collapsed-summary pattern, rather than reordering to the reviewer's
  Objective → Constraints → Model.
  Rationale: order is not what the review's evidence indicts (size and uniform weight
  are); goal → estimator → limits is a defensible reading order; and Playwright pins the
  policy-pane child sequence — reordering buys churn without addressing the diagnosis.
  Date/Author: 2026-07-02 / Claude.
- Decision: skip "loaded 8:40 AM" timestamps on the universe source line.
  Rationale: the seed action is pure (no clock access) and the header already shows the
  autosave "Saved <time>" note; threading a clock through the seed for a second timestamp
  adds impurity for marginal value.
  Date/Author: 2026-07-02 / Claude.
- Decision: scope the type-scale raise to the setup surface (setup views + setup.css +
  universe.css + the setup-surface inherited base), leaving results/execution surfaces
  unchanged for now. Two shared rail panels (`optimization_progress_panel`,
  `run_status_panel`) are also consumed by the scenario-detail rail and were raised with
  the sweep — a deliberate small spillover that keeps the rail family consistent.
  Rationale: the review targets the setup page; results/execution were speced separately
  (v4 alignment) and deserve their own pass with their own QA rather than an incidental
  cascade from a shared base bump.
  Date/Author: 2026-07-02 / Claude.
- Decision: run-bar text moves to theme tokens — status tag `text-trading-muted`
  (`/80` when ready), detail line `text-trading-text/90` — replacing sub-AA hex
  literals.
  Rationale: the review calls out "low-contrast status copy" explicitly, and when the
  tag names the run blocker it is load-bearing; ui-foundations.md requires WCAG AA.
  Tokens also keep the theme-colors ratchet clean.
  Date/Author: 2026-07-02 / Claude.

## Validation

Required gates (run from the repo root; `npm run setup:worktree` first in a fresh
worktree): `npm run gates` — runs `npm run check`, `npm test`, `npm run test:websocket`
and prints a PASS/FAIL matrix. Browser flows changed, so also run the targeted Playwright
specs (`npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs
tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs` with the repo's
documented env), then live-QA the worktree build: compile `app` + build CSS, serve
`resources/public` on :8090 with an SPA-fallback static server, boot
`/index.html?spectate=<address>`, navigate to `/portfolio/optimize/new`, and observe the
loading → seeded transition and the raised type scale (screenshots as evidence).

## Outcomes & Retrospective

Completed 2026-07-02. Both review findings are addressed. The setup surface moved from an
8.5–11px uppercase-mono wall to a documented ladder (14px sentence-case titles / 13px
primary / 12px secondary / 11px micro / 10px chip floor, monospace reserved for numbers
and tags), with the run bar's sub-AA hex status colors replaced by readable theme tokens.
The holdings auto-seed's silent window is now an explicit modeless state — skeletons and
"Loading holdings…" in the Universe panel, mirrored in the scenario-contract card, the
Readiness copy, the status pill, and a disabled "Loading holdings…" CTA — derived purely
from existing facts (untouched draft + account address + perp source not yet arrived) and
threaded through the existing readiness `:reason` contract, so no store paths, actions,
or effects were added. "What this model assumes" joined "Why this preset is safe" on the
collapsed tertiary tier.

Validation: `npm run gates` 34/34 (5678 tests / 30594 assertions), targeted Playwright
47/48 with the single failure a pre-existing ordering flake that passes in isolation,
and live browser QA covering all four terminal states of the flow (seeded-from-holdings
with omission accounting, restored per-wallet custom draft, spot-only fallback, pending
window verified via forced render + DOM capture).

Complexity: net reduction in conceptual load. The one new derivation
(`holdings-loading?`) replaced a misleading implicit state ("Add assets to run" during
machine work) with an explicit one, without new state machinery; the typography change
is class-level only. Remaining follow-ups: spot-only accounts deserve omission
accounting even when zero assets seed (filed as a spawned task), and the
results/execution surfaces still use the old denser scale and deserve the same ladder
pass with their own QA.
