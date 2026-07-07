# Make /portfolio/optimize the working page, with save-anytime named scenarios and an in-page scenario library

This ExecPlan is a living document maintained in accordance with /hyperopen/.agents/PLANS.md. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current as work proceeds.

## Purpose / Big Picture

Today a trader who clicks "Optimize" in the top navigation lands on `/portfolio/optimize`, which renders a mostly-empty "Optimization Scenarios" board (`index_view.cljs`) — a table that is blank for every new user and adds a click before any real work. The actual working surface lives at `/portfolio/optimize/new` (the setup workspace, which restores the per-wallet autosaved draft). Worse, on that workspace the "Save scenario" button is disabled until the *current* draft has a *current, non-stale, solved* optimization run — a condition that does not survive a page reload (the last run lives only in memory) — so in practice users see a permanently dead Save button and have no way to name and keep a configuration, nor any way to reopen one they kept before.

After this change:

1. Navigating to `/portfolio/optimize` lands directly on the setup workspace (the page formerly at `/portfolio/optimize/new`). The scenario-board index page is deleted. `/portfolio/optimize/new` keeps working as a legacy alias for old links.
2. "Save scenario" works at any time: it opens the existing name modal and persists a named scenario to IndexedDB (per wallet). If the draft has a current solved run, the results snapshot is saved with it (today's behavior); otherwise a setup-only scenario is saved honestly with no results attached.
3. The workspace header grows a scenario strip: the current scenario's name, a "Scenarios" menu listing every saved scenario for the connected wallet (click to open it), per-row Duplicate/Archive, and a "New scenario" action that resets the workspace to a fresh draft.

Observable outcome: with the dev server running (`npm run dev`, app on http://localhost:8080), visiting `/portfolio/optimize` shows the three-column setup workspace immediately. Clicking "Save scenario" (before ever running an optimization) opens the "Save scenario as" modal; entering "My first mix" and confirming shows the name in the header and adds a row to the Scenarios menu. Reloading the page and opening the Scenarios menu still lists "My first mix"; clicking it opens `/portfolio/optimize/<id>` with that configuration loaded.

## Context References

Public refs:
- Direct user/maintainer request (2026-07-06): remove the scenarios index page, land `/portfolio/optimize` on the working page, make scenario save actually work with naming + persistence, and provide load/new-scenario affordances from that page.

Repo artifacts:
- docs/exec-plans/active/2026-05-23-optimizer-save-scenario-modal-and-route.md (introduced the save modal, scenario records, and `/portfolio/optimize/<id>` routes this plan builds on).
- src/hyperopen/portfolio/optimizer/application/scenario_workflow.cljs and src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs (the save/load/archive/duplicate state machine).
- src/hyperopen/portfolio/optimizer/infrastructure/persistence.cljs (IndexedDB keys: `scenario-index::<addr>`, `scenario::<id>`, `draft::<addr>`).

Local scratch refs (non-authoritative): none.

## Orientation: how the optimizer routes and scenario machinery fit together

The portfolio optimizer is a ClojureScript single-page app surface. Route strings are parsed by `src/hyperopen/portfolio/routes.cljs` (`parse-portfolio-route`) into kinds: `:optimize-index` (`/portfolio/optimize`), `:optimize-new` (`/portfolio/optimize/new`), and `:optimize-scenario` (`/portfolio/optimize/<scenario-id>`). `src/hyperopen/views/portfolio/optimize/view.cljs` switches on the kind: index → `index_view.cljs` (the board being removed), new → `setup_view.cljs` → `workspace_view.cljs` (three-column setup), scenario → `scenario_detail_view.cljs` (results/execution tabs for a loaded or just-run scenario; the reserved id `draft` shows the current unsaved run).

State changes flow through nexus actions (pure fns returning effect vectors) and effects (side-effecting adapters). Scenario persistence is a small command interpreter: actions dispatch effects like `:effects/save-portfolio-optimizer-scenario`; the adapter in `runtime/effect_adapters/portfolio_optimizer_scenarios.cljs` drives pure workflow fns in `application/scenario_workflow.cljs` that emit `:optimizer.workflow/*` commands (load index, save record, save index), each executed against IndexedDB via `infrastructure/persistence.cljs`. Scenario records (built in `application/scenario_records.cljs`) store the full draft config plus optionally `:saved-run` (the last successful optimization). The per-wallet scenario index (`scenario-index::<addr>`) holds ordered summaries for listing.

Independently, the *draft* autosaves per wallet (`draft::<addr>`) on every edit while on the setup route, and is restored on arrival when the in-memory draft is untouched (`restore-portfolio-optimizer-draft-effect`). That autosave is why the setup page "prepopulates with cached values" — it is per-wallet resume, not a named scenario, and this plan keeps it unchanged.

Why Save looks broken today, precisely: `workspace-model` (application/view_model/workspace.cljs) computes `current-result?` via `run-identity/current-solved-run?`, which requires a solved run in `last-successful-run` whose request signature matches the *current* draft and readiness. `setup_actions.cljs` disables the Save button on `(not solved-run?)`; `confirm-portfolio-optimizer-scenario-save` (actions/run.cljs) additionally rejects with "Rerun this scenario before saving."; and `scenario-workflow/begin-save` fails without a solved run. `last-successful-run` is never persisted, so after any reload the button is dead until a fresh run — and after a run the app auto-navigates to `/portfolio/optimize/draft`, so users rarely ever see the setup-page Save enabled.

## Milestone 1 — `/portfolio/optimize` is the workspace; the index page is deleted

Scope: collapse the routing so the landing page is the setup workspace, keep the `/new` alias, and remove the index view plus its view-model.

Work, all paths repo-relative:

- src/hyperopen/portfolio/routes.cljs: parse `/portfolio/optimize` as `{:kind :optimize-new :scenario-id nil}` (identical to `/portfolio/optimize/new`), delete the `:optimize-index` kind, and keep the explicit `= path "/portfolio/optimize/new"` check ahead of the scenario-id branch so "new" never parses as a scenario id. Make `portfolio-optimize-new-path` return `/portfolio/optimize` (the canonical working URL) and keep `portfolio-optimize-index-path` as an alias returning the same string so existing callers (header nav) keep compiling. Remove `:optimize-index` from `portfolio-known-route?` and `portfolio-optimize-route?` sets.
- src/hyperopen/route_query_state.cljs (line ~46): drop `:optimize-index` from the optimizer-kind set.
- src/hyperopen/portfolio/optimizer/actions/run.cljs `load-portfolio-optimizer-route`: remove the `:optimize-index` branch and make the `:optimize-new` branch dispatch `[:effects/load-portfolio-optimizer-scenario-index]` (the workspace now needs the saved-scenario list for the Scenarios menu). Drop `:optimize-index` from `history-discovery-effects` and the `optimizer-route?` set.
- src/hyperopen/views/portfolio/optimize/view.cljs: remove the index branches; default and `:optimize-new` render `setup-view`.
- Delete src/hyperopen/views/portfolio/optimize/index_view.cljs, src/hyperopen/portfolio/optimizer/application/view_model/index.cljs, the `index-model` delegate in application/view_model.cljs, and test/hyperopen/views/portfolio/optimize/index_view_test.cljs.
- Update remaining `:optimize-index` references (grep `optimize-index`) in run_bridge_workflow, effect adapters, and tests: routes_test, view_test, route_modules_test, startup/route_aware_bootstrap_test, views/portfolio/header_test, etc. Update src/hyperopen/views/header/nav.cljs and src/hyperopen/views/portfolio/header.cljs to the canonical path helper.

Acceptance: `npm test` passes; in the running app `/portfolio/optimize` renders `data-role="portfolio-optimizer-setup-route-surface"` (not `portfolio-optimizer-index`), `/portfolio/optimize/new` renders the same surface, and `/portfolio/optimize/scn-xyz` still renders the scenario detail surface.

## Milestone 2 — Save works any time and re-saves update the open scenario

Scope: remove the solved-run gate from the whole save path, attach results only when they are honest (current), keep scenario identity stable across repeat saves, and stop yanking the user off the setup page on save.

Work:

- src/hyperopen/portfolio/optimizer/application/scenario_workflow.cljs `begin-save`: require only address + scenario-id (error message becomes "Cannot save scenario without a connected wallet."). In `continue-save-after-index`, compute whether the run snapshot is attachable: reuse `run-identity/current-solved-run?` with `setup-readiness/build-readiness` on the passed state (both are pure application-layer namespaces), and pass `last-successful-run` to `build-saved-scenario-record` only when current; otherwise nil so the record is setup-only (`:saved-run nil`, summaries show N/A — `scenario-records` already tolerates this). A scenario saved with results keeps `:status :saved` exactly as today.
- src/hyperopen/portfolio/optimizer/actions/run.cljs `confirm-portfolio-optimizer-scenario-save`: delete the "Rerun this scenario before saving." branch (name presence remains the only client-side validation). `save-portfolio-optimizer-scenario-from-current` drops its `current-solved-run?` gate.
- src/hyperopen/views/portfolio/optimize/setup_actions.cljs: Save button disabled only while `saving-scenario?`.
- src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs `current-scenario-id`: remove the `:optimize-new` special-case that forced a fresh id (it existed because `/new` meant "always a new scenario"; freshness is now an explicit "New scenario" action). Resolution order becomes: explicit `opts` id → active loaded scenario id → draft id (both filtered through `saved-scenario-id`, which already rejects the reserved `draft`/`draft-*` ids) → newly minted id. This is what makes "open scenario → tweak → Save" update in place instead of forking a copy on every save.
- Same file, `dispatch-saved-scenario-route!`: only navigate when the current route is already an `:optimize-scenario` route (that is the `/portfolio/optimize/draft` → `/portfolio/optimize/<id>` URL canonicalization after a post-run save). Saving from the workspace stays on the workspace; feedback comes from the header strip (Milestone 3) which reflects `active-scenario` name and the save state.

Acceptance: from a fresh reload of `/portfolio/optimize` with a wallet connected and no run, clicking "Save scenario" opens the modal and confirming persists; IndexedDB (`hyperopen` DB, portfolio-optimizer store) gains `scenario::<id>` and an updated `scenario-index::<addr>`; the URL does not change. Running the optimizer, landing on `/portfolio/optimize/draft`, and saving there still navigates to `/portfolio/optimize/<id>`. Saving twice from the workspace with the same open scenario updates one record (no duplicate rows in the menu).

## Milestone 3 — Scenario strip: name, Scenarios menu, New scenario

Scope: the workspace header (`setup_header.cljs`) becomes scenario-aware and hosts the library UI; the scenario detail page gets an "Edit setup" path back.

Work:

- New view namespace src/hyperopen/views/portfolio/optimize/scenario_picker.cljs rendering into the header row: a "Scenarios" trigger button and a "Save scenario" button (the bottom-bar Save stays, un-gated, for the post-configuration flow). The menu is a controlled popover following the objective-menu pattern (open flag in app state — a `<details>` element would be reset by unkeyed sibling churn; see memory of Replicant details gotchas). Menu content: a "New scenario" row on top, then saved scenarios for the wallet from `contracts/scenario-index-path`, newest first, filtered to non-archived, each row showing name, status tag, updated date, and expected return/vol when a results snapshot exists; row click dispatches `[:actions/navigate "/portfolio/optimize/<id>"]` (the existing route loader hydrates the draft, last run, and tracking). Per-row trailing Duplicate and Archive buttons dispatch the existing `:actions/duplicate-portfolio-optimizer-scenario` / `:actions/archive-portfolio-optimizer-scenario` (this preserves the only two management affordances the deleted index page had). Empty states: no wallet → "Connect a wallet to save scenarios"; none saved → "No saved scenarios yet".
- Menu open/close: new no-arg actions `:actions/toggle-portfolio-optimizer-scenario-menu` and `:actions/close-portfolio-optimizer-scenario-menu` writing a `contracts` ui path via `:effects/save`; the open action also dispatches `:effects/load-portfolio-optimizer-scenario-index` so the list is fresh whenever opened (covers wallet switches without a watcher).
- New scenario: no-arg action `:actions/new-portfolio-optimizer-scenario` dispatching a new effect `:effects/reset-portfolio-optimizer-draft` that (a) deletes the persisted per-wallet draft (`persistence/delete-draft!` exists, currently unwired), (b) resets in-state draft to `optimizer-defaults/default-draft`, clears `active-scenario`, last-successful-run, run/save/load states, and (c) re-dispatches `[:actions/set-portfolio-optimizer-universe-from-current]` so the fresh draft preseeds from holdings exactly like a first visit. Deleting the persisted draft is required: the restore effect only overwrites untouched drafts, and a freshly-reset draft *is* untouched, so a lingering `draft::<addr>` record would instantly resurrect the old universe.
- Contract surface for the three new actions + one new effect (drift-gated): schema/contracts/action_args.cljs and effect_args.cljs (`::common/no-args`), schema/runtime_registration/portfolio.cljs binding rows, runtime/action_adapters.cljs + runtime/effect_adapters.cljs delegates, portfolio/optimizer/runtime_catalog.cljs, and — if any new action must emit a heavy effect — runtime/effect_order_contract.cljs plus the Lean source spec/lean/Hyperopen/Formal/EffectOrderContract.lean synced via `bb tools/formal.clj sync --surface effect-order-contract`. Respect the namespace-size lint (`dev/namespace_size_exceptions.edn`): the new actions live in a new small namespace (e.g. portfolio/optimizer/actions/scenario_library.cljs) rather than growing actions/run.cljs.
- src/hyperopen/views/portfolio/optimize/setup_header.cljs: title shows, in priority order, the active saved scenario name → draft name → "Untitled scenario"; after a successful save show a transient "Saved" note (mirroring the existing autosave clock note) driven by `scenario-save-state`.
- src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs header: add an "Edit setup" button navigating to `/portfolio/optimize` — the load already hydrated the draft with the scenario config, so the workspace opens editing that scenario and Save updates it (Milestone 2 identity rule).

Acceptance: on `/portfolio/optimize`, the header shows "Scenarios" and "Save scenario"; saving "My first mix" puts the name in the H1; the menu lists it with a status tag; "New scenario" resets the universe to the holdings preseed and the title back to "Untitled scenario"; clicking the saved row opens `/portfolio/optimize/<id>`; "Edit setup" there returns to the workspace with the same name in the header.

## Validation

Environment: run `npm run setup:worktree` once in a fresh worktree (symlinks node_modules), else every gate fails environmentally.

- `npm run gates` — runs `npm run check`, `npm test`, `npm run test:websocket` with a PASS/FAIL matrix. All must pass.
- Unit coverage to add/adjust: routes_test (landing parse + alias), actions tests for confirm-save (no rerun gate) and new-scenario, scenario_workflow tests (save without run → record with nil saved-run; save with stale run → nil saved-run; with current run → attached), scenarios effect-adapter tests (id reuse on workspace save, no navigation from workspace, navigation from `/optimize/draft`), scenario_picker view tests (rows, empty states, actions), setup_header title priority.
- Browser: smallest relevant Playwright spec first (specs touching `/portfolio/optimize` routing), then a manual Browser-MCP/preview pass on the worktree dev server: land → save pre-run → reload → menu lists it → open → edit setup → save (no fork) → new scenario. Stop browser sessions when done (`npm run browser:cleanup`).

## Progress

- [x] (2026-07-06) Explored routing, save workflow, persistence, and view models; root-caused the dead Save button (in-memory solved-run gate + reload).
- [x] (2026-07-06) ExecPlan authored.
- [x] (2026-07-06) Milestone 1: routes collapsed (`/portfolio/optimize` → workspace, `:optimize-index` removed, `/new` alias kept), index view + index view-model deleted (view-model repurposed as `scenario-library`), nav/header links canonicalized via new `portfolio-optimize-path`, loaders updated (workspace loads the scenario index), tests adjusted.
- [x] (2026-07-06) Milestone 2: save-anytime — gates removed at action/workflow/view layers, `attachable-saved-run` attaches results only when current, workspace saves reuse the active scenario id, navigation only from `:optimize-scenario` routes.
- [x] (2026-07-06) Milestone 3: scenario strip (header title from draft name, header Save, Scenarios menu with New/Duplicate/Archive rows), `:effects/reset-portfolio-optimizer-draft` + three menu actions wired through the whole contract surface, "Edit setup" on scenario detail.
- [x] (2026-07-06) Gates green: `npm run check` PASS, `npm test` 5229 tests / 28281 assertions 0 failures, `npm run test:websocket` PASS (gates matrix), namespace-size exceptions bumped with reasons.
- [x] (2026-07-06) Browser QA on the worktree build (static :8090 + spectate identity): landing on workspace, pre-run save with name (stays on page, IndexedDB record + index written), menu lists/loads the scenario, Edit setup returns with identity intact, re-save updates in place (no fork), New scenario resets + survives reload, Duplicate/Archive from menu rows, reload persistence. Playwright optimizer regression sweep updated (index-board specs rewritten against the menu; 4 stale-on-main expectations repaired) and green.
- [x] (2026-07-06) Regression fix (user report): running from a workspace whose draft was a RESTORED SAVED scenario revealed `/portfolio/optimize/draft` as a masked idle N/A shell (no frontier). Root cause: the draft alias only rendered runs whose draft was UNSAVED (status gate), a contract from the era when saving navigated away. Fixed by (a) the alias now renders the CURRENT WORKSPACE run whenever the stamped loaded-id matches the draft id, saved or not; (b) solved runs always reveal on the alias (revealing on `/scn_X` re-triggers the route load, which replaces the fresh run with the record's saved-run — nil for setup-only saves); (c) workspace CTAs (`View results`, `Review & execute`) target the alias for the same reason. Verified live: save → reload → run now lands on `/draft` with the full results surface + frontier panel.
- [ ] Move this plan to completed/ after user acceptance.

## Surprises & Discoveries

- The entire scenario persistence machine (records, index, IndexedDB, modal, detail route) already exists and works; the feature gap is almost purely gating + navigation + a missing list UI on the workspace. Evidence: `save-portfolio-optimizer-scenario-effect` → workflow commands → `persistence/save-scenario!` chain is fully wired and covered by tests.
- `last-successful-run` is never persisted, so any save gate tied to it can never pass after a reload — the direct cause of the user-visible "Save button doesn't work".
- `persistence/delete-draft!` existed but was unwired (no effect calls it) — exactly what "New scenario" needs to keep the restore effect from resurrecting the old draft.
- The Scenarios popover at Tailwind `z-50` was unclickable whenever the sticky run banner (z-index 190) was pinned under the header — Playwright's actionability check caught the banner intercepting pointer events. Fixed with `z-[240]` on the popover (root stacking context; no ancestor creates one). Evidence: `<section data-role="portfolio-optimizer-run-banner"> intercepts pointer events` in the failed click log.
- Four `@regression` Playwright expectations were already stale against committed main renames (Objective→"Optimization goal", "Account leverage after"→"Gross leverage after", targets behind the "More goals" drawer, the proxy-workflow-slot pane child) — repaired here since the same file was being edited.
- `default-draft` carries `:name "Untitled Optimization"`, so the header title must treat that machine name as a placeholder or every fresh workspace claims a saved-sounding name.
- Save-in-place invalidated a hidden contract: `retained-unsaved-run?` (view-model.scenario) gated the `/draft` alias on the draft being UNSAVED, and the run-bridge reveal relied on that mask — both were written for the old save-navigates-away flow. The combination masked a just-finished run to an idle N/A shell whenever the workspace draft was a restored saved scenario (user-reported regression, screenshot: `scenario id draft · IDLE`, all KPIs N/A).
  Evidence: reproduced live (save "Wedge Repro" → reload → run → masked shell before the fix; full results + frontier after).
- Scenario-route arrival loads unconditionally overwrite `last-successful-run` with the record's `saved-run` (`apply-scenario-load-success`) — with setup-only records (`saved-run nil`) this silently wipes a fresh in-memory run. That made `/scn_X` an unsafe reveal/CTA target from the workspace; everything now points at the `/draft` alias, which renders in place.

## Decision Log

- Keep the route kind name `:optimize-new` for the workspace even though the canonical path is now `/portfolio/optimize`: ~20 call sites (draft restore, autosave, holdings preseed, run reveal, view models) key on it; renaming is churn with zero user-visible value. Recorded here so the name mismatch doesn't confuse future readers.
- `/portfolio/optimize/new` stays parseable as an alias (old bookmarks, tests, muscle memory) but all in-app links emit `/portfolio/optimize`.
- Save without a current solved run stores a setup-only record (`:saved-run nil`) rather than attaching a stale result: the optimizer surfaces are honesty-first (stale results are already banner-flagged elsewhere), and `scenario-records`/detail view tolerate nil runs natively.
- Repeat saves reuse the active scenario id (update-in-place); "save as a copy" is served by the Duplicate row action instead of a separate Save-As path. Forking on every save (old `/new` behavior) would silently litter the library.
- Saving from the workspace does not navigate away; only the post-run `/portfolio/optimize/draft` save re-routes (URL canonicalization to the new id). Keeps the user's editing context stable.
- Scenario menu rows navigate to `/portfolio/optimize/<id>` (existing loader hydrates everything, deep-linkable) instead of hydrating in place; the detail page gains "Edit setup" back to the workspace so setup-only scenarios are still one click from editing.
- The Scenarios menu is a state-controlled popover (objective-menu pattern), not `<details>`, due to known Replicant unkeyed-sibling reset gotchas.
- New actions live in a new namespace `portfolio/optimizer/actions/scenario_library.cljs` to respect the namespace-size gate (actions/run.cljs & draft.cljs are at/near caps).
- Scenario-menu UI state lives under the existing optimizer `:ui` subtree in `contracts` so it resets with the optimizer state and needs no persistence.
- The `/draft` alias's meaning changed from "renders only UNSAVED runs" to "renders the CURRENT WORKSPACE run" (loaded-id matches the draft id — saved scenarios being edited in place included). Consequently all workspace-origin result navigation (run reveal, View results, Review & execute) targets the alias, never `/scn_X`: the scenario route's arrival load would replace the fresh in-memory run with the record's `saved-run` snapshot (nil for setup-only saves). `/scn_X` remains the surface for OPENING a saved scenario from the library and for post-save URL canonicalization from the alias.

## Outcomes & Retrospective

- Delivered: `/portfolio/optimize` lands on the setup workspace (index board deleted); Save scenario works at any time, attaches results only when honest, updates the open scenario in place, and never yanks the user off the workspace; the header hosts a scenario library (title, Save, Scenarios menu with New/Duplicate/Archive); scenario detail gained "Edit setup" back to the workspace.
- Verified: `npm run gates` 34/34 PASS (5934 tests, 31626 assertions); Playwright optimizer regression sweep 22/22 at --workers=1 against the worktree build; manual browser QA of save → menu → load → edit → re-save → new → reload persistence with a spectate identity.
- Complexity: net reduction — a whole page, its view-model, and a route kind were removed; the additions (one picker view, one small actions namespace, one effect) reuse the existing scenario workflow unchanged. Route-kind name `:optimize-new` intentionally kept despite the collapsed URL (see Decision Log).
- Remaining: user acceptance, then move this plan to completed/. Follow-up candidate (not in scope): persist `last-successful-run` so a reload keeps the Results surface warm; consider surfacing archived scenarios somewhere (currently reachable only via IndexedDB).
