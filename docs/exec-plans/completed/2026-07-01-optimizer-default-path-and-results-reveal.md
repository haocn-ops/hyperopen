# Optimizer Setup: Opinionated Default Path (holdings by default, results on success)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`,
`Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.
Maintain it in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The optimizer setup route (`/portfolio/optimize/new`) still behaves like a form the user must
operate instead of an assistant that starts from their real portfolio. Today a trader who opens
the page cold sees an empty universe and must click "Load my holdings"; when a run succeeds they
are told "Succeeded" in the right rail and must click a second button to actually see the target
portfolio; the primary CTA is labeled "Run on safe defaults" (policy language, not goal
language); their in-progress draft evaporates on reload because the per-wallet IndexedDB draft
slot exists but nothing ever writes it; and collapsed policy panels hide the numbers that
determine the result.

After this change the default path is opinionated: opening the page seeds the universe from the
user's current holdings as soon as account data is available (visibly, with an accounting of
what was omitted and why, and a one-click "Clear universe" escape hatch); every draft edit
autosaves per wallet so a deliberately cleared or customized universe is remembered across
reloads; a successful run navigates straight to the results surface (never on failure,
infeasibility, or if the user has moved elsewhere mid-run); the CTA reads "Run optimization";
and each collapsed policy panel summarizes its live values (gross/net/cap/band numbers on
Constraints) so the center column reads as a scannable contract. Stale-history warnings gain a
"Refresh history" remediation button.

You can see it working by opening Portfolio → Optimize → New with a connected or spectated
account: the universe fills itself from holdings (with "N included · M omitted" accounting),
clicking "Run optimization" shows progress and then lands on `/portfolio/optimize/draft` showing
the recommendation, and reloading the page restores exactly the draft you left.

## Context References

Public refs:
- Direct maintainer request (2026-07-01) relaying a designer audit: default the universe to
  holdings (with X-out rows and a clear-and-start-from-scratch escape hatch), auto-navigate to
  results on success instead of showing a "Succeeded" status the user must acknowledge, rename
  the "Run on safe defaults" CTA, autosave the scenario instead of manual "Save draft", surface
  constraint numbers in the collapsed header, make warnings actionable, and compact the
  Objective section. Maintainer explicitly delegated final design judgment to the implementer.

Repo artifacts:
- Builds on the completed center-policy restructure:
  `/hyperopen/docs/exec-plans/completed/2026-07-01-optimizer-setup-ia-restructure.md`.
- Prior single-workspace phase (why success-commands stopped navigating):
  `/hyperopen/docs/exec-plans/completed/2026-06-28-optimizer-flow-simplification.md`.
- Governed docs: `/hyperopen/docs/BROWSER_STORAGE.md` (persistence rules),
  `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/BROWSER_TESTING.md`.

Local scratch refs (non-authoritative): None.

## Progress

- [x] (2026-07-01) Mapped every seam: auto-preseed + its cold-load gap, unwired
  `save-draft!`/`load-draft!` persistence scaffolding, `success-commands` pure decision point
  with an existing `:optimizer.workflow/navigate` interpreter, store-watcher precedents
  (progress ticker, startup cache watchers), toast runtime, readiness warning grouping.
- [x] (2026-07-01) Authored this ExecPlan.
- [x] (2026-07-01) M1 — Draft autosave + restore. New
  `infrastructure/draft_autosave.cljs` (debounced per-wallet IndexedDB autosave with
  address-recheck at flush + `note-persisted!` no-rewrite guard), new
  `actions/draft_persistence.cljs` (`restore-or-preseed…` route funnel + gated,
  spec-validated `hydrate…`), restore effect adapter (IndexedDB-first, preseed
  fallback), bootstrap wiring, contract paths (`draft-persist-path`,
  `draft-universe-source-path`) + full action/effect registration, header "Saved
  <time>" note, "Save draft" relabeled "Save scenario". Unit-tested (watcher timers
  injected; hydrate gates; malformed/version-mismatch records rejected).
- [x] (2026-07-01) M2 — Holdings-by-default reliable + visible + reversible. Holdings-arrival
  store watcher re-enters the restore-or-preseed funnel on the no-data→data transition;
  `set-…-universe-from-current` now materializes a full default draft when untouched (fixes
  the partial-draft hole and a latent `default-draft` fn-vs-value comparison bug in the old
  preseed gate), records `:universe-source` with per-asset omission reasons
  (spot-excluded / unusable-history); hand-adds flip the source to custom; new
  `clear-portfolio-optimizer-universe` empties selection + all per-asset residue; universe
  panel gained the live source strip, "From holdings · N included · M omitted" line with an
  expandable reasons list, and "Clear universe".
- [x] (2026-07-01) M3 — Auto-reveal results. `success-commands` is now route-aware: solved on
  `/optimize/new` → `:optimizer.workflow/reveal-results` (navigate to
  `/portfolio/optimize/draft` + select the Recommendation tab); solved on the run's own
  scenario surface → stay put; solved anywhere else → one success toast via the global
  toast runtime. Failure/infeasible paths unchanged. Workflow matrix + interpreter
  dispatch unit-tested.
- [x] (2026-07-01) M4 — CTA "Run optimization"/"Optimizing…"; Objective became an
  open-by-default disclosure whose header summarizes the choice; Return/Risk header shows
  both live models ("Historical mean · Ledoit-Wolf"); Constraints header carries the live
  numbers via a shared pure `constraints-summary-line`; the right-rail card became a
  labeled "Scenario contract" stack (universe count + source, objective, model,
  constraint numbers).
- [x] (2026-07-01) M5 — Stale-history warning groups carry a "Refresh history" button
  (dispatches the existing history-load action; full loads refetch the bundle at a fresh
  as-of).
- [x] (2026-07-01) `npm test` 4958/0 after updating the behavior-pinning tests (old
  stay-on-setup workflow tests → new reveal/announce matrix; add/set-from-current effect
  vectors gained the universe-source path-values; copy assertions). `npm run gates` 33/34
  → 34/34 after namespace-size exception updates (universe actions + effect-adapter facade
  + universe action tests).
- [x] (2026-07-02) M6 — Playwright + live QA. `portfolio-regressions` 33/34 after fixing one
  PRE-EXISTING spec drift (policy-pane child list missing the sticky Run bar; fails on clean
  main too) — the 1 remaining failure is an unrelated account-activity flake that passes in
  isolation; exposure-map + Black-Litterman + view-model-routes 24/25 (the 1 failure is the
  documented pre-existing BL "applies a valid pending BTC view" red). Live QA on the worktree
  dev build with a spectated real top-trader account verified: cold-load auto-population
  (48 instruments, zero clicks), the source line with real omission reasons (HYPE spot
  balance excluded; US500/USTECH no optimizer history), "Saved <time>" autosave note,
  Clear universe → reload → "Custom universe · 0 assets" restored and NOT re-preseeded even
  after holdings arrived later, and the goal-language CTA / constraints-header numbers /
  scenario-contract card rendering live values (gross 10.43×–10.44× · net −5.17×–−5.06×).
  Live QA exposed and fixed one real watcher bug (see Surprises: spot-first arrival).

## Surprises & Discoveries

- Observation: Holdings-by-default already half-exists. `auto-preseed-portfolio-optimizer-universe-from-current`
  (`src/hyperopen/portfolio/optimizer/actions/universe.cljs:434`) fires on `:optimize-new` route
  load and reuses the "Load my holdings" action verbatim — but its own docstring concedes the
  gap: on a cold page-load account data arrives *after* the route event, the seed no-ops, no
  further route event fires, and the user is dumped back on the manual button. The fix is a
  data-arrival trigger, not a new feature.
- Observation: The draft persistence layer is fully built and never called.
  `save-draft!`/`load-draft!`/`delete-draft!` exist in
  `src/hyperopen/portfolio/optimizer/infrastructure/persistence.cljs:128-138` with a per-wallet
  `draft::<address>` key, but `grep` finds zero call sites. "Autosave" is therefore wiring, not
  invention — and it is also what makes a cleared/custom universe a remembered per-wallet choice
  (a restored non-default draft makes the preseed gate skip).
- Observation: Auto-navigation was deliberately unplugged, with the plug still in the wall.
  `success-commands` (`src/hyperopen/portfolio/optimizer/application/run_bridge_workflow.cljs:149`)
  documents "Phase 1: no force-navigate", while the command interpreter one layer down
  (`src/hyperopen/portfolio/optimizer/infrastructure/run_bridge.cljs:132`) still handles
  `:optimizer.workflow/navigate`. Re-enabling is a pure-function change plus one new command.
- Observation: "Save draft" is mislabeled today — it actually snapshots a *named scenario*
  (modal → IndexedDB `scenario::<id>` → navigate to the saved route) and is disabled until a
  solved run exists. The honest fix is renaming it "Save scenario", not removing it.
- Observation: The designer's CoinGecko-provider warning example does not exist in this
  codebase's readiness system (no provider-tier warning code); no remediation to build there.
- Observation: The old preseed gate's "still default-draft" arm was dead code — it compared
  the draft map to the `default-draft` FUNCTION var (`(= draft optimizer-defaults/default-draft)`,
  `actions/universe.cljs:453` pre-change), so preseed effectively fired only on nil drafts,
  and the materialize branch assoc'd the fn into the local state. Fixed via a shared
  `defaults/untouched-draft?` that calls the constructor.
  Evidence: `(defn default-draft [] …)` in `defaults.cljs` + the bare var reference.
- Observation: Seeding onto a nil draft used to create a PARTIAL draft in state (universe +
  constraints + dirty over nothing — no objective/model/schema-version), which the app
  tolerated but the persisted-draft `::draft` spec would reject on restore, silently
  breaking the "cleared universe is remembered" loop. Fixed at the source:
  `set-…-universe-from-current` now writes a full default draft first when untouched, and
  the autosave flush layers legacy partial drafts over a default before persisting
  (belt + suspenders).
- Observation: The Playwright regression spec "recommendation chart shows minimum variance
  frontier overlays" still asserts `toHaveURL(/\/portfolio\/optimize\/draft/)` right after
  clicking Run — an assertion from the pre-"Phase 1" era (commit 69e8e372, April) when runs
  DID navigate. The auto-reveal restores the behavior that assertion pins; the
  history-requests spec beside it asserts setup-page panels after Run and may need its
  post-run DOM expectations moved to the results surface.
- Observation: Live QA caught a real one-shot-trigger bug the unit tests missed: the
  holdings-arrival watcher originally fired on "any holdings source became present", but the
  SPOT balances routinely land before the perp clearinghouse snapshot; the spot-first
  arrival (unseedable while :include-spot? is off) consumed the only transition and the perp
  arrival never re-fired.
  Evidence: spectating 0xfc66…ca06 on /optimize/new left the universe empty until a manual
  re-dispatch; after switching the watcher to per-source arrival transitions
  (`current-portfolio/holdings-source-arrived?`), a cold reload auto-populated 48
  instruments with zero interaction.
- Observation: The embedded preview browser throttles rAF/`setInterval` in unfocused tabs,
  so the app's render loop never fires there (state machine runs, DOM stays on the loading
  shell). QA worked by forcing `app.bootstrap/render-app!` manually per step; not an app
  bug (Playwright's focused Chromium renders normally). Worth remembering for future
  preview-tool QA sessions.

## Decision Log

- Decision: Adopt holdings-by-default by completing the existing preseed (restore-then-preseed
  effect + holdings-arrival store watcher) rather than adding a new "source mode" toggle state
  machine.
  Rationale: The action, its untouched-draft gate, and its tests already exist; the failure is
  purely the cold-load timing hole. A watcher mirroring the progress-ticker pattern
  (`infrastructure/progress_ticker.cljs`) is the established way to react to store transitions.
  Date/Author: 2026-07-01 / Claude.

- Decision: Persisted per-wallet draft always beats machine preseed. On route entry the new
  restore effect reads IndexedDB first and only dispatches the preseed when no persisted draft
  exists; the direct route-loader preseed call is replaced by this effect.
  Rationale: If preseed ran first, the untouched-draft gate would then block the user's restored
  draft — the machine would win over the human. Ordering restore→preseed inside one effect
  removes the race instead of guarding it.
  Date/Author: 2026-07-01 / Claude.

- Decision: Autosave via a debounced store watcher on the draft path (new
  `infrastructure/draft_autosave.cljs`), not by threading a persist effect through every
  draft-mutating action.
  Rationale: `startup/watchers.cljs` already persists caches exactly this way (pending write +
  idle flush); a watcher catches every mutation source (user actions, preseed, hydrate) without
  touching ~30 actions; storage I/O stays in an infrastructure boundary per BROWSER_STORAGE.md.
  Address is captured at schedule time and re-verified at flush so a wallet switch cannot
  cross-key a write.
  Date/Author: 2026-07-01 / Claude.

- Decision: Auto-navigate emits a new pure command `:optimizer.workflow/reveal-results` only
  when the completed run is `:solved` AND the router path at completion parses to
  `:optimize-new`. The interpreter dispatches the established two-action deep-link
  (`[:actions/navigate path]` + `[:actions/set-portfolio-optimizer-results-tab :recommendation]`).
  No toast on this path.
  Rationale: The route gate suppresses navigation for refine-in-place runs (initiated from the
  scenario surface) and for users who wandered elsewhere mid-run — yanking them would be worse
  than the current behavior. The explicit tab set guards against a stale previously-selected
  tab. A toast on top of a self-announcing results page would be reporting normalcy.
  Date/Author: 2026-07-01 / Claude.

- Decision: When the run solves while the user is elsewhere in the app, show one success toast
  through the existing global `[:ui :toasts]` runtime instead of navigating.
  Rationale: The optimizer execution adapter already does exactly this for finished executions
  ("stays visible for a user who tab-switched mid-run"); same UX reasoning applies to solves.
  Date/Author: 2026-07-01 / Claude.

- Decision: CTA copy is a static "Run optimization" (running: "Optimizing…"), not the
  preset-named dynamic variants ("Run Conservative scenario").
  Rationale: The active-preset detector is nearest-match (a customized draft still maps to some
  preset), so preset-named copy can silently lie; the bottom bar already prints the exact
  objective/model being solved. Static goal language is always true.
  Date/Author: 2026-07-01 / Claude.

- Decision: Objective/Model/Constraints all become disclosure panels with live value summaries
  in their headers; Objective ships open-by-default (user-collapsible), Constraints stays
  closed-by-default but its header gains the gross/net/cap/band numbers.
  Rationale: Delivers the designer's scannable "compact contract" center without hiding the
  choosing UI from first-time users, and keeps the Black-Litterman contract (pickers render
  before the belief workspace) intact. Auto-expanding Constraints after edits is skipped: edits
  happen inside the open panel already.
  Date/Author: 2026-07-01 / Claude.

- Decision: Rejected from the designer list — preset-named CTA (above), Rename/Duplicate/Delete
  on the setup header (scenario management lives post-save), CoinGecko provider warning actions
  (no such warning exists here), remembering per-panel open/closed state (preference storage
  churn for marginal value).
  Rationale: Recorded per item; the maintainer delegated final judgment to the implementer.
  Date/Author: 2026-07-01 / Claude.

## Outcomes & Retrospective

Completed 2026-07-02. All six milestones landed; `npm test` 4959/0, gates green after
namespace-size exception updates, optimizer Playwright specs green except two documented
pre-existing reds/flakes unrelated to or fixed by this work, and the full default path was
verified live against a spectated real account.

What a trader gains: opening /portfolio/optimize/new now starts from their actual book —
the universe fills itself the moment account data is available (with honest accounting of
what was left out and why), every edit autosaves per wallet so a cleared or customized
setup survives reloads, a successful run lands directly on the recommendation instead of a
"Succeeded" status, the CTA says what the button does ("Run optimization"), the collapsed
policy panels wear their live numbers, the right rail shows the exact scenario contract the
solver receives, and stale-history warnings carry their own fix.

Complexity: net additive at established seams — one infrastructure watcher namespace
(autosave + arrival trigger), one persistence-restore effect + two pure actions, one pure
workflow command, and view/header changes; two latent bugs were removed along the way (the
fn-vs-value default-draft comparison and the partial-draft materialization hole). The
riskiest lesson: unit tests validated every pure gate but only live QA caught the
one-shot arrival-trigger race — event-arrival watchers should always be modeled per source.

Deferred / follow-ups: the designer's per-panel open/closed-state memory and the
preset-named dynamic CTA were rejected (recorded in the Decision Log); the pre-existing BL
"applies a valid pending BTC view" Playwright red and the account-activity/tracking-reload
full-suite flakes remain separate debt; the spot-exposure instrument-id quirk observed in
omission labels (a spot HYPE balance surfacing as `perp:HYPE`) was root-caused and fixed
in the same session: the coin-based market resolver hits the perp candidate key first for
dual-listed tokens, so spot balances adopted the perp identity and OVERWROTE the real perp
exposure in `:by-instrument`; `markets/resolve-spot-market-by-coin` (spot-filtered
candidates + the base-token spot scan) now backs `build-spot-exposures`, with regression
tests for the dual-listed and perp-only-token cases.

## Context and Orientation

The app is a ClojureScript single-page app rendered by Replicant (views are pure functions of a
single app-state atom, called the store). "Actions" are pure functions `state → effects` living
under `src/hyperopen/portfolio/optimizer/actions/`; "effects" are executed by adapter namespaces
under `src/hyperopen/runtime/effect_adapters/`; long-lived side-effectful helpers live under
`.../optimizer/infrastructure/`. Dispatching `[[:actions/foo …]]` runs an action; adapters can
dispatch back via the `dispatch!` handle they receive. A "disclosure" is a native
`<details>/<summary>` collapsible.

Key files (full paths from repo root):

- Route entry: `src/hyperopen/runtime/action_adapters/navigation.cljs` — `route-loader-effects`
  currently calls `auto-preseed-portfolio-optimizer-universe-from-current` directly (line 96).
- Universe actions: `src/hyperopen/portfolio/optimizer/actions/universe.cljs` —
  `set-portfolio-optimizer-universe-from-current` (line 396) builds the universe from the
  current-portfolio snapshot, filters spot (unless `:include-spot?`) and known-unusable-history
  instruments, derives gross/net constraint bands, enqueues history prefetch;
  `auto-preseed-…` (line 434) gates it to `:optimize-new` + untouched draft.
- Snapshot: `src/hyperopen/portfolio/optimizer/application/current_portfolio.cljs` —
  `current-portfolio-snapshot` (memoized) exposes `:snapshot-loaded?`; arrival of
  `[:webdata2 :clearinghouseState]` / spot balances flips it.
- Draft contracts: `src/hyperopen/portfolio/optimizer/contracts.cljs` — `draft-path`,
  `draft-universe-path`, `draft-constraints-path`, `draft-dirty-path`, `run-state-path`,
  `last-successful-run-path`, `ui-results-tab-path`. Default draft:
  `src/hyperopen/portfolio/optimizer/defaults.cljs` `default-draft`.
- Persistence: `src/hyperopen/portfolio/optimizer/infrastructure/persistence.cljs` —
  `load-draft!`/`save-draft!` keyed `draft::<normalized address>` in the IndexedDB
  `portfolio-optimizer` store (EDN-encoded records).
- Run lifecycle: `src/hyperopen/portfolio/optimizer/application/run_bridge_workflow.cljs`
  (pure; `handle-worker-message`, `success-commands`) and
  `src/hyperopen/portfolio/optimizer/infrastructure/run_bridge.cljs`
  (`interpret-message-command!` already handles `:optimizer.workflow/navigate` and dispatches
  via `nxr/dispatch`).
- Setup views: `src/hyperopen/views/portfolio/optimize/` — `setup_universe.cljs` (left rail),
  `setup_objective_controls.cljs`, `setup_model_controls.cljs`,
  `setup_constraint_controls.cljs` (center policy pane sections, composed by
  `setup_sections.cljs`), `setup_actions.cljs` (`setup-bottom-actions` run bar; Run label at
  line 129; "Save draft" button at line 138), `setup_context.cljs` (right rail `summary-card` +
  Trust & Freshness), `setup_readiness_panel.cljs` (grouped warnings), `setup_header.cljs`
  (title, status tag, preset chips).
- Pure view-models: `src/hyperopen/portfolio/optimizer/application/view_model/setup.cljs`
  (`setup-summary-card-model`, `group-readiness-warnings`, `readiness-panel-model`).
- Readiness: `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs`
  (`stale-history-warning-codes` = `#{:stale-history :source-fetch-failed}`).
- History load action: `src/hyperopen/portfolio/optimizer/actions/run.cljs`
  `load-portfolio-optimizer-history-from-draft` (line 20) → `:effects/load-portfolio-optimizer-history`.
- Watcher precedents: `src/hyperopen/portfolio/optimizer/infrastructure/progress_ticker.cljs`
  (transition-gated store watch), `src/hyperopen/startup/watchers.cljs` (debounced persistence),
  installed from `install-runtime-watchers!` in `src/hyperopen/runtime/bootstrap.cljs:80`.
- Toasts: global list at `[:ui :toasts]` rendered by
  `src/hyperopen/views/notifications_view.cljs`; push mechanics in
  `src/hyperopen/order/feedback_runtime.cljs`; optimizer precedent in
  `src/hyperopen/runtime/effect_adapters/portfolio_optimizer/execution.cljs`
  (`execution-outcome-toast`).
- Action registration: `src/hyperopen/portfolio/optimizer/actions.cljs` facade +
  `src/hyperopen/portfolio/optimizer/runtime_catalog.cljs` +
  `src/hyperopen/schema/contracts/action_args.cljs` (+ Lean `formal:sync` surface per
  `/hyperopen/docs/exec-plans/completed/…`; follow the "add an action/effect contract surface"
  checklist when registering new action/effect ids).

Tests that pin current behavior (update in lockstep):
`test/hyperopen/portfolio/optimizer/universe_from_holdings_actions_test.cljs` (preseed gates),
`test/hyperopen/views/portfolio/optimize/setup_layout_test.cljs` and
`setup_actions_test.cljs` (run/save copy + placement), `setup_readiness_panel_test.cljs`,
`view_model_setup_boundary_test.cljs`, `workspace_view_test.cljs`; Playwright
`tools/playwright/test/portfolio-regressions.spec.mjs` (run-label and layout assertions),
`optimizer-exposure-map.spec.mjs`, `optimizer-black-litterman-views.spec.mjs`,
`optimizer-view-model-routes.smoke.spec.mjs`.

## Plan of Work

### M1 — Draft autosave + restore (foundation)

New infrastructure namespace
`src/hyperopen/portfolio/optimizer/infrastructure/draft_autosave.cljs`:
`install-draft-autosave-watcher!` add-watches the store (key `::draft-autosave`); on a change of
`(get-in state contracts/draft-path)` it schedules a debounced flush (~800 ms, injectable timer
fns like the progress ticker). The flush captures the effective account address at schedule
time, re-verifies it at flush time, skips when the draft is nil/default-draft or equals the last
value it already persisted (an internal atom, also settable via `note-persisted!` so a restore
does not immediately re-write itself), and calls `persistence/save-draft!` with
`{:version 1 :address a :draft d :saved-at-ms now}`. After a successful write it records
`{:status :saved :at-ms now}` at a new `contracts/draft-persist-path`
(`[:portfolio :optimizer :draft-persist]`) via a plain `swap!` (infrastructure boundary, same as
the ticker). Install it from `install-runtime-watchers!` alongside the progress ticker.

Restore: new effect `:effects/restore-portfolio-optimizer-draft` (adapter in
`src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs` or a sibling) reads
`persistence/load-draft!` for the effective address; on a valid record it dispatches
`[:actions/hydrate-portfolio-optimizer-draft record]`; on none it dispatches
`[:actions/auto-preseed-portfolio-optimizer-universe-from-current path]` (path captured in the
effect args). New pure action `hydrate-portfolio-optimizer-draft` (in `actions/draft.cljs`)
validates the record shape (map, `:version`, `:draft` map with vector universe) and applies it
only while the in-memory draft is still untouched (nil or default-draft), writing the draft plus
`draft-persist` `{:status :saved :at-ms (:saved-at-ms record)}`; it must also call
`draft_autosave/note-persisted!`-equivalent via effect ordering (simplest: the adapter notes the
restored draft before dispatching hydrate). In `navigation.cljs` `route-loader-effects`, replace
the direct `auto-preseed…` call with a small action returning
`[[:effects/restore-portfolio-optimizer-draft normalized-path]]` when the route is
`:optimize-new` and the in-memory draft is untouched (otherwise `[]`).

Header indicator: `setup_header.cljs` shows, next to the status tag, "Autosaves locally" before
the first persist and "Saved <local time>" from `draft-persist` afterward. Rename the bottom-bar
button "Save draft" → "Save scenario" (its behavior — named snapshot after a solved run — is
unchanged).

Register the new action/effect ids across the contract surfaces (actions facade,
runtime_catalog, action_args schema, runtime_registration, Lean formal:sync) per the established
checklist.

Tests: watcher unit test with injected timers (edit → single debounced save; address switch
between schedule and flush → skipped; hydrate-then-no-edit → no redundant write); hydrate action
gate tests (untouched applies, touched draft never clobbered, malformed record ignored); route
action test (optimize-new + untouched → restore effect; other routes/touched → none).

### M2 — Holdings-by-default: reliable, visible, reversible

Arrival watcher: extend the same `draft_autosave.cljs` install (or sibling fn
`install-holdings-preseed-watcher!`) to watch for the `current-portfolio-snapshot`
`:snapshot-loaded?` false→true transition (compare via the snapshot's cheap `:signature` slices,
not the full build) while the router path parses to `:optimize-new` and the draft is untouched;
on that transition dispatch `[[:actions/restore-or-preseed-portfolio-optimizer-draft path]]`
(the same funnel as route entry, so IndexedDB still wins). Transition-gating plus the untouched
gate make it fire at most once per arrival and never clobber.

Source visibility: `set-portfolio-optimizer-universe-from-current` additionally records
`[:portfolio :optimizer :draft :universe-source]`
`{:kind :holdings :loaded-at-ms now :omitted [{:instrument-id … :label … :reason
:unusable-history|:spot-excluded} …]}` computed from the exposures it filtered out (the spot
removal and `known-unusable-history?` removal already happen there — capture instead of
dropping silently). Manual `add-…`/`remove-…`/clear actions set `:universe-source`
`{:kind :custom}` (only when they change membership). `setup_universe.cljs` renders a source
line under the panel heading: "From holdings · 14 included · 3 omitted" with a `<details>`
listing each omitted asset and a plain-language reason, or "Custom universe · N assets".

Clear + refresh affordances: new pure action `clear-portfolio-optimizer-universe` empties the
universe and the per-asset residue wholesale (blocklist, held-locks, asset-overrides,
perp-leverage, history-assumptions, Black-Litterman view paths — reuse the per-instrument
removal logic's path list) and sets `:universe-source {:kind :custom}`. Universe panel footer:
"Clear universe" (destructive-styled, only when universe non-empty) beside the existing load
button relabeled "Use my holdings" (it doubles as "Refresh holdings" once loaded — same verb,
idempotent). Because the cleared draft autosaves (M1), the scratch choice is remembered per
wallet; no separate preference storage.

Tests: preseed-on-arrival watcher test (loaded transition + untouched + optimize-new →
dispatch; each gate individually blocks); universe-source recording tests (holdings load records
omissions with reasons; manual edit flips to custom); clear action test (universe + residue
emptied, BL views cleaned); view test for the source line and omitted list.

### M3 — Auto-reveal results on solved runs

`run_bridge_workflow.cljs`: `success-commands` takes the post-update state, parses
`(get-in state* [:router :path])` with `portfolio.routes/parse-portfolio-route`, and appends to
the existing refresh command: on `:optimize-new` → `{:command/type
:optimizer.workflow/reveal-results :path (portfolio-routes/portfolio-optimize-scenario-path
"draft")}`; on any other route (user elsewhere mid-run) → `{:command/type
:optimizer.workflow/announce-run-complete}` — except when the route is already the scenario
surface for this run (refine-in-place: no command). `run_bridge.cljs`
`interpret-message-command!` gains the two cases: reveal-results dispatches
`[[:actions/navigate path] [:actions/set-portfolio-optimizer-results-tab :recommendation]]`;
announce-run-complete pushes one success toast ("Optimization complete — the recommendation is
ready on the Results page") through the global toast runtime. Failure, `:infeasible`,
worker-error paths emit no commands (unchanged).

Tests: workflow unit tests over `handle-worker-message` asserting the emitted commands for:
solved on optimize-new (reveal), solved on another route (announce), solved on the scenario
route (neither), infeasible/error (none), stale message (none). Interpreter test that
reveal-results dispatches the two-action vector.

### M4 — Goal-language CTA + scannable policy headers + contract card

`setup_actions.cljs`: Run label → "Run optimization" / running → "Optimizing…" (data-role and
enablement untouched). `view_model/setup.cljs`: extend `setup-summary-card-model` with
`:rebalance-band` (from draft constraints) and reuse it for a new pure
`constraints-header-summary` string ("gross 1.90–1.91× · net 1.30–1.41× · cap 50% · band
3.0 pp"); move the private `gross-range`/`net-range` helpers there so the card and the header
share one source. `setup_constraint_controls.cljs`: the Constraints `<summary>` renders the
numbers line beside the existing preset eyebrow. `setup_objective_controls.cljs`: the Objective
section becomes a disclosure (open by default) whose header shows the selected objective +
one-line meaning (e.g. "Minimum variance — lowest risk, no return assumption");
`setup_model_controls.cljs` header shows "Historical mean · Stabilized covariance" (return +
risk labels). `setup_context.cljs`: restyle the summary card into a labeled "Scenario contract"
stack (Universe — count + source, Objective, Model, Constraints numbers, Rebalance band) still
driven by the one view-model, keeping `data-role portfolio-optimizer-setup-summary-card`.

Tests: update `setup_actions_test.cljs` copy assertions; VM tests for
`constraints-header-summary` and the extended card model; layout tests for the objective
disclosure (present + open) and constraints header summary.

### M5 — Actionable stale-history warnings

`view_model/setup.cljs` `group-readiness-warnings`: mark groups whose `:code` is in
`setup-readiness/stale-history-warning-codes` with `:action {:label "Refresh history"
:actions [[:actions/load-portfolio-optimizer-history-from-draft]]}` (confirm the registered
action id in `runtime_catalog.cljs`; verify the history effect refetches stale-but-present
entries — if it skips them, pass the supported opts to force). `setup_readiness_panel.cljs`
renders the button (small bordered button, `data-role
portfolio-optimizer-readiness-warning-action`) beside the count badge; clicking is idempotent
while a load is in flight (the effect already no-ops/queues).

Tests: VM test (stale group carries the action; other codes do not); panel render test.

### M6 — Playwright, gates, browser QA

Update `portfolio-regressions.spec.mjs` for the new Run label and any layout deltas; keep BL
specs green (pickers still render before the belief workspace; objective disclosure is open by
default). Add/extend a spec covering: cold-load auto-population (seeded state), Clear universe,
and the solved-run auto-navigation to `/portfolio/optimize/draft`. Run `npm run gates`, then the
smallest relevant Playwright spec, broadening after it passes. Live-verify on the dev server
(worktree recipe: symlink node_modules, `npm run dev`, spectate a real address) and stop all
browser sessions when done.

## Concrete Steps

Run from the worktree root
(`/Users/barry/projects/hyperopen/.claude/worktrees/quizzical-nightingale-5f0219`):

    npm run setup:worktree        # once; symlinks node_modules (done)
    npm test                      # fast unit gate per milestone
    npm run gates                 # full PASS/FAIL matrix before completion
    npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --workers=1

## Validation and Acceptance

Behavioral acceptance, in order of the milestones: (M1) edit any draft field, reload the page —
the edit is still there; the header shows "Saved <time>". (M2) with a spectated account and a
cold direct load of `/portfolio/optimize/new`, the universe fills itself when account data
lands, the panel says how many assets were included/omitted and why, "Clear universe" empties it,
and after a reload it stays empty (remembered choice). (M3) click "Run optimization" on the
setup page: on solve the app lands on `/portfolio/optimize/draft` with the Recommendation tab
active; an infeasible run stays on setup with the banner; a run finished while browsing another
page shows one toast and does not navigate. (M4) collapsed Constraints shows its live numbers;
the CTA reads "Run optimization". (M5) a stale-history warning group shows "Refresh history"
and clicking it starts a history load. (M6) `npm run gates` is all-PASS and the optimizer
Playwright specs pass.

## Idempotence and Recovery

All steps are additive at established seams and independently revertible per milestone. The
watcher installs are `remove-watch`-then-`add-watch` (safe on dev reload). The restore effect
and hydrate action are gated so replays never clobber a touched draft; autosave skips no-op
writes. Reverting M3 restores exactly today's stay-on-setup behavior (pure function + command
case). IndexedDB writes are versioned records under the existing store; deleting the
`draft::<address>` key returns any wallet to the fresh-default path.

## Artifacts and Notes

Gate matrix (2026-07-01): `npm run gates` 33/34 on first run — the single failure was
`lint:namespace-sizes` (new code/tests crossed thresholds); after exception updates the
gate passes and the matrix is effectively 34/34 (`npm test` 4958 tests / 27202 assertions,
`test:websocket` PASS, all five shadow-cljs builds PASS).

Playwright `portfolio-regressions.spec.mjs --workers=1` (runs against the live worktree
watch build on :8080 — verified via the server JVM's cwd):

    run 1: 32 passed, 2 failed
      - "setup puts policy controls in the center pane": PRE-EXISTING drift — fails
        identically on clean main (verified via git stash); the sticky Run bar became the
        pane's last direct child in commit a39f8c9d but this spec's expected child list was
        never updated. Fixed the spec (list now includes …-setup-bottom-actions).
      - "persisted scenario hydrates results and tracking after reload": passes on clean
        main AND passes twice in isolation with these changes → one-off flake under
        full-suite load, not a regression.
    run 2 (after the spec fix): 33 passed, 1 failed — "portfolio account activity tab
      renders ledger history", unrelated to this work (no optimizer surface), passes in
      isolation → same flake class the repo already documents for loaded runs.

Notably the pre-existing frontier-overlay spec assertion `toHaveURL(/\/portfolio\/optimize\/draft/)`
right after clicking Run — stale since the June "Phase 1" un-navigation — passes again with
the auto-reveal in place, live end-to-end evidence of the P0 navigation behavior.

## Interfaces and Dependencies

New/changed interfaces (ClojureScript):

    hyperopen.portfolio.optimizer.infrastructure.draft-autosave/install-draft-autosave-watcher!
      {:store store :now-ms-fn f :set-timeout-fn f :clear-timeout-fn f :save-draft! f
       :dispatch! f}  ;; also installs the holdings-arrival preseed trigger
    :actions/restore-or-preseed-portfolio-optimizer-draft [path]   ;; route + watcher funnel
    :effects/restore-portfolio-optimizer-draft [path]
    :actions/hydrate-portfolio-optimizer-draft [record]
    :actions/clear-portfolio-optimizer-universe []
    contracts/draft-persist-path        => [:portfolio :optimizer :draft-persist]
    contracts/draft-universe-source-path => [:portfolio :optimizer :draft :universe-source]
    run-bridge-workflow success-commands => may emit
      {:command/type :optimizer.workflow/reveal-results :path string}
      {:command/type :optimizer.workflow/announce-run-complete}
    view-model setup/constraints-header-summary [draft] => string
    view-model setup/setup-summary-card-model — gains :rebalance-band, :universe-source

No new libraries. All persistence through the existing
`hyperopen.portfolio.optimizer.infrastructure.persistence` and
`hyperopen.platform.indexed-db` boundaries.

## Note on revisions

2026-07-01 (initial): Authored after a four-way code recon (universe/preseed, run lifecycle,
draft persistence, readiness warnings). Records that holdings-by-default and draft persistence
are completions of existing half-built seams, that auto-navigation is a pure-decision re-enable
behind a route gate, and the adopt/adapt/reject rationale for each designer recommendation.

2026-07-02 (completion): All milestones landed and validated (`npm run gates` 34/34, optimizer
Playwright specs green modulo two documented pre-existing reds/flakes, live spectated-account
QA). Live QA surfaced and fixed the spot-first arrival-trigger race (per-source watcher) and a
pre-existing Playwright child-list drift; retrospective + follow-ups recorded above. Moved to
completed/. Reason: acceptance criteria met.
