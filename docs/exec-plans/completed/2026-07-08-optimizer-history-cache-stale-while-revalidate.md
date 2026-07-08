# Optimizer History Cache: Stale-While-Revalidate

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Third follow-up in the proxy-loading latency series (parents:
`2026-07-08-optimizer-proxy-loading-rail-and-normalize-perf.md`,
`2026-07-08-optimizer-history-double-fetch-elimination.md`, both completed).
Even with the double-fetch gone, a cold open of `/portfolio/optimize` re-downloads
the entire ~50-asset history bundle (5–7s backend response) before the
history-assumption cards can settle — yet a restored draft is by definition a
REPEAT visit: the same universe the user loaded an hour ago, over daily-candle
data that barely changes intra-day.

After this change, the last successful normalized history bundle is persisted
per wallet in IndexedDB (`history-bundle::<address>`, next to `draft::<address>`)
and hydrated by the same restore funnel that hydrates the draft. On a revisit
the assumption cards, universe readiness, and rail configure from the cached
bundle at ~2–3s — while the normal history load still runs and replaces the
cache when it lands (stale-while-revalidate; the loading indicators from the
earlier plans honestly show the refresh in the footer). A user can verify by
loading the optimizer, letting it settle, reloading the page, and seeing the
assumptions configured within a couple of seconds instead of ~12.

Out of scope (already deferred in
`docs/exec-plans/deferred/2026-07-08-optimizer-history-latency-deferred.md`):
backend bundle latency, `/info` dedup, moving parse/alignment off the main
thread. Correction recorded there via this plan: the "heavy-effect deferral"
hypothesis for the load-start gap was wrong — `effect_order_contract.cljs` is
an ordering assertion, not a scheduler; the cache makes the start gap mostly
moot regardless.

## Context References

Public refs:

- Direct maintainer request on 2026-07-08 (chat): after the double-fetch fix
  the assumptions still take long to settle; approved recommendation to build
  the IndexedDB stale-while-revalidate bundle cache.

Repo artifacts:

- `docs/BROWSER_STORAGE.md` — IndexedDB is mandated for caches that grow with
  assets/history rows; records need stable keys, `:saved-at-ms`, `:version`.
- `src/hyperopen/portfolio/optimizer/infrastructure/persistence.cljs` — the
  per-wallet record patterns (`draft::`, `assumption-library::`) this follows.
- `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs`
  `restore-portfolio-optimizer-draft-effect` — the restore funnel the cache
  hydration rides (covers both the restored-draft and holdings-preseed
  branches, plus the holdings-arrival and identity-arrival watchers that
  re-dispatch it).
- `src/hyperopen/portfolio/optimizer/infrastructure/draft_autosave.cljs` —
  the store-watcher home (autosave debounce pattern to copy).
- `src/hyperopen/portfolio/optimizer/application/history_merge.cljs`,
  `history_workflow.cljs` — the load/merge machinery the background
  revalidate reuses unchanged.

## Plan of Work

1. **Pure cache policy** (`application/history_cache.cljs`): record version,
   max hydration age (7 days), `history-cache-record` (build a persistable
   record from state: `:api-v2-history`, legacy candle/funding/vault maps,
   `:warnings`, `:loaded-at-ms`; nil when there is nothing worth persisting),
   and `hydrate-history-cache` (state + record + address + now-ms → state' or
   nil) with guards: version/address match, age cap, non-trivial payload, and
   NEVER clobbering — hydration only applies while the in-memory history data
   is still empty.
2. **Persistence** (`infrastructure/persistence.cljs`): `history-cache-key`
   (`history-bundle::<address>`) + load/save/delete using the shared
   edn-encoded record helpers.
3. **Hydration** (`portfolio_optimizer_scenarios.cljs` restore effect): read
   the cache in PARALLEL with the draft record (the multi-MB edn decode must
   not delay draft hydration) and apply it via the pure guard. Works for both
   restore and preseed branches. The existing
   `[:effects/load-portfolio-optimizer-history]` appended by draft/preseed
   hydration IS the revalidate — no new scheduling. Wire `*load-history-cache!*`
   through `portfolio_optimizer.cljs` like `*load-draft!*`.
4. **Persist watcher** (`draft_autosave.cljs`): `install-history-cache-watcher!`
   registered from `install-optimizer-draft-watchers!`; watches
   `history-data :loaded-at-ms` transitions (cheap), debounced ~1.5s, saves
   the record for the effective address; skips no-address, unchanged, and
   nothing-worth-persisting states.
5. **Tests.** Pure ns: record building (trivial payloads → nil) and every
   hydration guard (fresh hydrates; stale, wrong-address, wrong-version,
   already-loaded → nil). Adapter: restore effect hydrates the cache record
   into history-data alongside the draft. Watcher: loaded-at-ms transition
   saves once, debounced. Playwright: an end-to-end SWR spec — load once with
   mocked api-v2 routes and a seeded spectate identity, wait for Configured +
   the IndexedDB record, then reload with the history-bundle route HELD and
   assert the cards read Configured from cache while the refresh is pending.

## Validation and Acceptance

- Required gates: `npm run check`, `npm test`, `npm run test:websocket` (via
  `npm run gates`) all PASS.
- Playwright: the new SWR spec plus `optimizer-proxy-loading-ux.spec.mjs`,
  `optimizer-history-api-v2.spec.mjs`, `optimizer-history-assumptions-io.spec.mjs`
  pass.
- Acceptance: on a reload with a warm cache, the history-assumption cards
  show Configured before the history-bundle network response resolves; the
  background load still fires and replaces the cached data when it lands; a
  cache older than the age cap, for another wallet, or racing already-loaded
  data is ignored.

## Progress

- [x] (2026-07-08) Surveyed BROWSER_STORAGE policy, persistence/record patterns, restore funnel, watcher bootstrap, effect-order contract (scheduler hypothesis disproven).
- [x] (2026-07-08) Pure `history-cache` ns (`history_cache.cljs`) + `history-cache-test` (record building, every hydration guard) + `history-cache-roundtrip-test` (normalize → record → edn round trip → hydrate → build-readiness, incl. migrated-draft + in-flight-revalidate conditions).
- [x] (2026-07-08) Persistence record fns (`history-cache-key`/`load-history-cache!`/`save-history-cache!`/`delete-history-cache!`).
- [x] (2026-07-08) Restore-effect hydration (parallel with draft load via `js/Promise.all`) + adapter wiring (`*load-history-cache!*`) + `portfolio-optimizer-history-cache-restore-test`.
- [x] (2026-07-08) Autosave-style persist watcher (`install-history-cache-watcher!`, 1.5s debounce, `:loaded-at-ms` transition, `:restored-from-cache?` write-back guard) + tests in `draft_autosave_test.cljs`.
- [x] (2026-07-08) Playwright SWR spec (`optimizer-history-cache-swr.spec.mjs`); `npm run gates` 34/34 PASS (6,000 tests / 31,909 assertions); 7/7 history-related Playwright specs PASS.

## Surprises & Discoveries

- First browser run of the SWR Playwright spec: hydration itself works (probe
  confirms `:restored-from-cache?` lands in state), but the WLFI card read
  `incomplete`+`data-loading` — BTC was not in the usable-proxy set. The
  `history-cache-roundtrip-test` unit repro (normalize → record → edn round
  trip → hydrate → build-readiness, including migrated-draft + in-flight
  `:loading` load-state) PASSED clean, proving the cache/hydration/readiness
  pipeline itself was correct — the bug had to be environmental to the
  browser spec.
- Root cause: a TEST bug, not a product bug. The draft autosave watcher resets
  its debounce timer on every edit (by design), so an EARLIER flush (right
  after adding WLFI) can already have written a `draft::<addr>` record before
  BTC is even clicked. The spec's `.not.toBeNull()` check on that key passed
  on the stale, pre-BTC record instead of waiting for the LATEST edit's
  write — phase 2 then correctly restored the (incomplete, WLFI-only) draft
  that had actually been persisted. Fixed by polling
  `draft-persist :at-ms` (the app's own flush-completion timestamp) against a
  captured "BTC was just added" timestamp instead of a bare existence check.
  This is a reusable lesson for any future test against a debounced
  autosave watcher: presence of a persisted record is not proof it reflects
  the latest edit.

- `effect_order_contract.cljs` does not defer anything — it asserts effect
  ORDER within an action dispatch. The earlier deferred-plan hypothesis that
  exempting the history load from "heavy-effect deferral" would shave ~3s was
  wrong; the remaining start gap is script/module/IDB/identity timing, and the
  cache sidesteps it.

## Decision Log

- Cache hydration rides the existing restore funnel instead of a new
  `:effects/*` id: the restore effect is dispatched by every path that needs
  it (route entry, holdings arrival, identity arrival), and avoiding a new
  effect id avoids the full contract surface (registration, arg contracts,
  Lean effect-order sync) for what is intrinsically part of "restore the
  workspace".
- The cache is NOT deleted on draft reset ("New scenario"): it holds market
  series, not user intent, and a fresh holdings preseed benefits from the same
  cached series. It is overwritten on every successful load and ignored after
  the 7-day age cap.
- Hydration never writes `history-load-state`: the revalidate's own
  begin-load stamps `:loading` immediately, and readiness/cards judge
  usability from the hydrated data itself — cards read Configured while the
  footer honestly shows the refresh.
- Persisted via the shared edn-v1 record encoding for consistency with every
  other optimizer record; the multi-MB pr-str/read-string cost (~hundreds of
  ms) sits off the interaction path (debounced save, parallel restore read).
  If profiling later shows the decode hurting hydration time, a
  transit/structured-clone upgrade is a contained follow-up.

## Outcomes & Retrospective

Landed as planned. A revisit to `/portfolio/optimize` now hydrates the last
successful history bundle from IndexedDB in parallel with the draft restore,
so the assumption cards and readiness settle from cache in ~2-3s while the
real history load still runs in the background and replaces the cache when
it lands — proven end-to-end by `optimizer-history-cache-swr.spec.mjs`, which
holds the history-bundle network response open through the entire reload and
asserts the cards read Configured anyway. All gates and the full
history-related Playwright suite (7 specs) pass. Diagnosing the first failing
spec run doubled as a lesson about testing debounced autosave watchers
(recorded above); no product-level surprises turned up once the test itself
was fixed.
