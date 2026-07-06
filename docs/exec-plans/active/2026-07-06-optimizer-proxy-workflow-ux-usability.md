# Proxy Workflow Usability: Limiting-Asset Visibility, Collapsible Cards, Per-Asset Assumption Memory

## Purpose

Three usability gaps in the optimizer's proxy workflow for short-history assets
(`/portfolio/optimize/new`), reported by the owner on 2026-07-06:

1. **The user can't see which assets are the limiting ones.** The covariance
   window is the intersection of every asset's return calendar, so the asset
   with the fewest days of returns silently caps the whole model — but nothing
   on the setup page ranks assets by return-day count. The "Model an asset
   with proxies…" dropdown lists assets in universe order with bare labels,
   so the user proxies out assets blind. Fix: every option shows its native
   return-day count ("SOPH (403 days)") and the list sorts ascending by that
   count, so the most limiting assets sit at the top.
2. **Configured cards pile into a very long scroll.** Each assumption card
   renders fully expanded forever; three or four configured assets already
   push the rest of the page below the fold. Fix: cards collapse to a
   one-line summary row. A card whose assumption is complete AND acknowledged
   (Apply) collapses by default; every card with a chosen mode gets an
   explicit collapse/expand control; expanding is one click.
3. **Assumptions only persist inside the current per-wallet draft.** The
   draft autosave (`draft::<address>`) does carry `:history-assumptions` and
   `:proxy-reference-instruments`, so a plain reload keeps them — but the
   memory is draft-scoped, not asset-scoped. Remove the asset and re-add it,
   preseed a fresh draft, or start over, and the user re-authors the same
   proxy basket from scratch. Fix: a per-wallet **assumption library**
   (`assumption-library::<address>` in IndexedDB, mirroring the return-view
   library) remembers the last authored assumption per instrument-id and
   re-hydrates it whenever that asset is in the universe without a draft
   entry.

Durable context: direct maintainer request (2026-07-06 session). Parent
ExecPlan: `2026-07-05-optimizer-proxy-history-assumptions.md` (engine-backed
proxy behavior; all seams documented there still hold).

## Orientation

- Dropdown + cards view: `src/hyperopen/views/portfolio/optimize/setup_history_assumptions.cljs`
  (`add-asset-select`, `assumption-card`, `history-assumptions-section`).
- Card view-model: `src/hyperopen/portfolio/optimizer/application/view_model/setup_history_assumption_cards.cljs`
  (`history-assumption-cards` builds `:cards` and `:addable-assets`).
- Observation counts: `universe/native-history-observations` (readiness arity is
  the production source — api-v2 sessions have empty state candles).
- Assumption actions: `src/hyperopen/portfolio/optimizer/actions/draft.cljs`
  (`save-history-assumptions` / `save-assumptions+refs` are the two funnels every
  assumption mutation goes through).
- Library precedent: `application/view_library.cljs` (pure),
  `infrastructure/persistence.cljs` (IDB keys), effects in
  `runtime/effect_adapters/portfolio_optimizer.cljs`
  (`load-/sync-portfolio-optimizer-view-library-effect`), loaded from
  `actions/run.cljs::load-portfolio-optimizer-route`.
- Restore funnel: `actions/draft_persistence.cljs`
  (`hydrate-portfolio-optimizer-draft`), restore effect dispatches actions via
  `*dispatch!*` — the idiom the library hydration reuses.
- Contract surface for new actions/effects: registration rows in
  `schema/runtime_registration/portfolio.cljs`, arg specs in
  `schema/contracts/action_args.cljs` / `effect_args.cljs`, wiring in
  `app/actions.cljs` + optimizer `runtime_catalog.cljs`, facade
  `portfolio/optimizer/actions.cljs`. The view-library effects are NOT in the
  Lean effect-order policy, so the analogous assumption-library effects don't
  touch the formal surface.

## Design

**1. Dropdown day counts.** `history-assumption-cards` enriches each addable
asset with `:observations` (`universe/native-history-observations state
readiness instrument` — nil while history is still loading) and a prebuilt
`:option-label`: `"<label> (N days)"` / `"<label> (1 day)"`, bare label when
nil. Sort: known counts ascending, nil counts last, label as tiebreak. The
view renders `:option-label` verbatim (options stay direct `[:option]`
siblings — see the parent plan's Replicant nested-children gotcha).

**2. Collapsible cards.** Presentation state, not draft state: a new
UI-state map `[:portfolio-ui :optimizer :assumption-cards-collapsed]`
(`{instrument-id bool}`) holds explicit user overrides. Card model gains
`:collapsible?` (mode chosen) and `:collapsed?` =
`(get overrides id default)` where default = `:configured?` (complete? AND
acknowledged?). Consequences: Apply collapses (it acknowledges and clears the
override), editing any field withdraws acknowledgment (existing
`unacknowledged`) so the default re-expands, reload keeps configured cards
collapsed because acknowledged? persists in the draft. New action
`:actions/set-portfolio-optimizer-history-assumption-card-collapsed`
(instrument-id, collapsed?) writes the override; `apply`/`set-mode`/`clear`
dissoc the override so the default takes over. Collapsed rendering: header
row (label + status chip + summary line + expand control); controlled
divs, NOT `<details>` (state-driven collapse would fight a native toggle —
the risk-guardrails drawer note).

**3. Assumption library.** Mirrors the view library:

- Pure ns `application/assumption_library.cljs`: `library-version`, `entry`
  (assumption entry + per-entry `:reference-instruments` — the resolved
  instrument maps for the entry's out-of-universe proxies, so a hydrated
  proxy basket can re-seed `:proxy-reference-instruments` and prefetch),
  `apply-sync` (upserts/removes, effect stamps `:updated-at-ms`),
  `library-record`/`usable-record?`/`record->entries`, and
  `hydrate-assumptions` (gap-fill: universe instruments with no draft entry
  take their library entry; existing draft entries always win; returns nil
  when nothing changes, plus the new reference instruments to add).
- Persistence: `assumption-library-key` = `"assumption-library::<addr>"`,
  `load-/save-assumption-library!` in `infrastructure/persistence.cljs`.
- State mirror: `assumption-library-path` in contracts.
- Effects: `:effects/load-portfolio-optimizer-assumption-library` (route
  load, next to the view-library load; loads mirror then dispatches the
  hydrate action) and `:effects/sync-portfolio-optimizer-assumption-library`
  (applies upserts/removes to the mirror, stamps time, persists unless
  spectating — same shape as the view-library sync).
- Sync emission: the two save funnels in `actions/draft.cljs` grow a
  changed-id parameter and append the sync effect — one upsert with the
  entry when present, one remove when absent (Clear). Every assumption
  mutation (mode, fields, proxies, strength, apply, reset, clear) flows
  through them, so incomplete works-in-progress are remembered too.
- Hydration (gap-fill only, idempotent, never clobbers a draft entry):
  new pure action `:actions/hydrate-portfolio-optimizer-history-assumption-library`
  emits a plain save-many (assumptions + reconciled refs + prefetch state —
  deliberately NOT the dirty-marking draft-save helper) and the
  history-prefetch effect for newly-referenced out-of-universe proxies.
  Dispatched by ONE new store watcher
  (`install-assumption-library-hydrate-watcher!` beside the draft-autosave
  watchers): whenever the (universe, draft assumptions, library mirror)
  triple changes AND some universe instrument lacks a draft entry the
  library remembers, hydrate fires. One mechanism covers every ordering —
  library-mirror arrival, draft restore, holdings preseed, universe add —
  with no per-call-site hooks.
- Deliberate: removing an asset from the universe does NOT remove its
  library entry (re-adding restores it); only explicit Clear forgets it.

## Decision Log

- 2026-07-06 Dropdown counts come from `native-history-observations` with the
  readiness arity (aligned api-v2 series first) — the same source as the
  cards' "days of returns" label, so the two never disagree.
- 2026-07-06 Collapse is controlled state, not `<details>`: Apply must
  auto-collapse, and a state-computed `:open` on a native details element
  re-asserts itself against the user's toggle (documented gotcha on the
  risk-guardrails drawer).
- 2026-07-06 Collapse default keys off the EXISTING acknowledged? metadata
  (persisted in the draft) rather than a new draft field — reload-stable
  with zero contract changes; explicit overrides are UI-state only.
- 2026-07-06 Library entries store works-in-progress (any entry shape), not
  just complete assumptions — remembering a half-built basket is strictly
  better than forgetting it, and hydrated incomplete entries surface as the
  same incomplete card the user left.
- 2026-07-06 Library removal happens ONLY on explicit Clear. Universe
  removal keeps the entry (the whole point is re-adding the asset later).
- 2026-07-06 Per-entry reference instruments ride the library entry so a
  hydrated proxy basket restores out-of-universe proxies (instrument
  metadata + prefetch) without a catalog round-trip; membership is
  re-checked against the hydrating draft's universe at hydrate time.
- 2026-07-06 Hydration is a store WATCHER + idempotent action, not per-site
  hooks. The original plan hooked the load effect, the restore effect, and
  `add-portfolio-optimizer-universe-instrument`; the universe-add action is
  in `effect-order-policy-required-action-ids` (Lean-modeled), so appending
  effects to it would have dragged in the formal surface. The watcher
  (established draft-autosave idiom) covers all paths without touching any
  policy-listed action.
- 2026-07-06 Library REMOVES execute before the draft write, upserts after.
  Clear's draft save fires the hydrate watcher; a mirror that still held the
  entry at that instant would gap-fill the just-cleared card right back.
  Ordering the sync-remove first closes the race; upserts can never create a
  gap so they stay after the save (state already carries the entry).

## Surprises & Discoveries

- The parent feature's "Apply collapses the card to its summary" (2026-07-05
  decision log) was never actually implemented in the view — Apply only flips
  the status chip; the card stays fully expanded. This plan implements the
  collapse for real.
- `remove-portfolio-optimizer-universe-instrument` already deletes the
  asset's draft assumption in the same save-many that drops the universe row
  — which is exactly right for the library design: the draft forgets, the
  library remembers, re-adding restores. No universe-action changes needed.
- `add-portfolio-optimizer-universe-instrument` is in
  `effect-order-policy-required-action-ids` (Lean-modeled) — appending
  effects to it would have touched the formal surface. The watcher design
  sidestepped that entirely.
- Live-QA assertion gotcha: a rehydrated card returns COLLAPSED (the stored
  entry is acknowledged), so proxy chips are not in the DOM — the first
  temp-spec run "failed" by asserting a chip on a collapsed card while the
  feature itself worked perfectly (the screenshot showed the configured
  summary row). Assert the collapsed summary, then expand for chips.
- `lint:docs` matches durable-context phrasing literally: "direct maintainer
  request" / "parent ExecPlan" pass; "owner request" does not.

## Milestones

Milestone 1 — limiting-asset visibility. Addable-asset entries carry
`:observations` + `:option-label`, sorted ascending (nil last); the view
renders them. Proof: view-model tests (sort order, labels, nil handling) and
a view test asserting real `[:option]` siblings with day-count labels.

Milestone 2 — collapsible cards. UI-state override path + collapse action +
card-model `:collapsed?`/`:collapsible?`/`:collapsed-summary` + collapsed
header rendering + Apply/set-mode/clear override clearing. Proof: view-model
default/override matrix tests; view tests (collapsed card hides editors,
shows summary + expand control; expanded card shows collapse control);
action tests.

Milestone 3 — assumption library. Pure ns + persistence keys + effects +
sync emission from the save funnels + hydrate action + three dispatch hooks +
full action/effect contract surface. Proof: pure-ns tests (apply-sync,
hydrate gap-fill, reference-instrument reconciliation), action tests (sync
payloads from every mutation; hydrate emits save-many + prefetch; universe
add gap-fills), effect-adapter tests mirroring the view-library ones.

Milestone 4 — gates + browser QA. `npm run gates` PASS; smallest relevant
Playwright spec for the setup page, then broaden if it touches more.

## Progress

- [x] Explore all seams; write this plan.
- [x] M1 view-model: addable observations + option-label + ascending sort.
- [x] M1 view: render option labels; tests.
- [x] M2 contracts path + collapse action + registration/args surface.
- [x] M2 view-model collapsed?/collapsible?; apply/set-mode/clear override
      clearing.
- [x] M2 view collapsed rendering + tests.
- [x] M3 pure ns + persistence + mirror path.
- [x] M3 effects (load + sync) + adapters + registration/args + route load.
- [x] M3 sync emission from save funnels; Clear removes (ordered first).
- [x] M3 hydrate action + hydrate watcher (single mechanism, all orderings).
- [x] M3 tests (pure ns, actions, effect adapters, watcher).
- [x] M4 `npm run gates` PASS (34/34; namespace-size ledger bumped, docs
      lint durable-context wording fixed).
- [x] M4 browser QA: `optimizer-view-model-routes.smoke.spec.mjs --workers=1`
      14/14; live temp-spec pass (real BTC/ETH/SOL) verified day-count
      dropdown, expand/collapse cycle, Apply-collapse, and the remove →
      re-add rehydration (collapsed CONFIGURED summary row with the full
      basket in the rail).
- [ ] Follow-up (not this pass): a committed deterministic Playwright spec
      for the proxy-workflow section (dropdown counts, collapse cycle,
      library rehydration) — the QA above ran through a throwaway temp spec.

## Validation

`npm run setup:worktree` once. Dev loop: `npx shadow-cljs compile test &&
node out/test.js`. Final: `npm run gates` (check, test, test:websocket — no
short-circuit). Browser: smallest relevant Playwright spec first (setup page
smoke), then the optimizer route smoke if it passes. Manual: add a mix of
long/short-history assets, open the proxy dropdown (ascending day counts),
configure + Apply (card collapses), remove/re-add the asset (assumption
returns), reload (still there), Clear (forgotten).

## Outcomes & Retrospective

- Landed 2026-07-06, all three usability gaps closed. Gates 34/34
  (`npm run check`, `npm test` — 5182 cljs tests / 28149 assertions,
  `npm run test:websocket`); smoke spec 14/14; live browser proof of the
  full memory loop: configure SOL on BTC/ETH → Apply collapses to a one-line
  CONFIGURED row → remove SOL from the universe → re-add → the card returns
  collapsed with the remembered basket and the run flips ready with "All
  short-history assets have assumptions."
- The dropdown now leads with the limiting assets: "(N days)" per option,
  ascending, sourced from the same readiness-arity observation count the
  cards use, so the two can never disagree.
- The single-watcher hydration design (vs. per-call-site hooks) was the
  decisive simplification: no Lean-surface contact, one idempotent gap-fill
  action, and every ordering (mirror load, restore, preseed, universe add)
  covered by construction. Its one real subtlety — Clear's remove must beat
  the draft write to the mirror — is pinned by an exact-order action test.
- 13 pre-existing tests pinned exact effect vectors and were updated to
  expect the new sync/load effects; 6 namespace-size ledger entries bumped
  or added.
