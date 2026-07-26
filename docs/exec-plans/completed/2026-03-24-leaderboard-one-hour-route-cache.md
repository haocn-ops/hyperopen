# Add A One-Hour IndexedDB Cache For Leaderboard Route Visits

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is governed by `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The tracked work item for this plan is `hyperopen-4juc`, and that `bd` issue was the local tracker reference while this work was active.

## Purpose / Big Picture

After this change, revisiting `/leaderboard` should feel immediate for up to one hour instead of forcing another full leaderboard reload each time the user moves around the app or refreshes the page. The visible behavior to prove is simple: open `/leaderboard`, let it load once, leave the route, come back within an hour, and confirm the leaderboard renders from cached data without depending on a fresh network response.

This cache is not a tiny startup preference. The payload can grow with leaderboard rows and excluded addresses, so the repository storage policy requires IndexedDB instead of `localStorage`. The cache must live behind the shared IndexedDB boundary in `/hyperopen/src/hyperopen/platform/indexed_db.cljs`, and the route fetch logic must stay deterministic and reducer-free.

## Progress

- [x] (2026-03-24 18:31Z) Created and claimed `bd` issue `hyperopen-4juc` for one-hour leaderboard caching.
- [x] (2026-03-24 18:39Z) Traced the current leaderboard route path through `/hyperopen/src/hyperopen/leaderboard/actions.cljs`, `/hyperopen/src/hyperopen/leaderboard/effects.cljs`, `/hyperopen/src/hyperopen/runtime/effect_adapters/leaderboard.cljs`, and `/hyperopen/src/hyperopen/api/projections.cljs`.
- [x] (2026-03-24 18:42Z) Confirmed via repo storage policy and cache-module examples that this should be an on-demand IndexedDB cache module, not a startup restore path and not an API-client TTL tweak.
- [x] (2026-03-24 18:53Z) Implemented the IndexedDB-backed leaderboard cache module, cache-aware route effect, projection hydration path, and runtime wiring.
- [x] (2026-03-24 18:57Z) Added focused CLJS coverage for cache normalization, TTL reuse, fresh-memory reuse, fresh IndexedDB hydration, stale cache fallback, stale in-memory fallback, and force-refresh bypass.
- [x] (2026-03-24 18:58Z) Added deterministic Playwright smoke coverage for reload-from-cache and in-app revisit-without-refetch behavior.
- [x] (2026-03-24 19:00Z) Completed required validation: focused tests, Playwright smoke, `npm run test:websocket`, `npm test`, `npm run check`, and governed browser QA for `leaderboard-route`.

## Surprises & Discoveries

- Observation: current leaderboard revisits already keep old rows in memory, but the route action still triggers a fresh heavy fetch every time.
  Evidence: `/hyperopen/src/hyperopen/leaderboard/actions.cljs` emits `[:effects/api-fetch-leaderboard]` on each `/leaderboard` route load, while `/hyperopen/src/hyperopen/api/projections.cljs` keeps prior `:leaderboard :rows` in store until a new result arrives.

- Observation: the leaderboard endpoint is outside the shared info-client TTL path, so adding a request-policy TTL alone would not solve cross-refresh route caching or excluded-address reuse.
  Evidence: `/hyperopen/src/hyperopen/api/endpoints/leaderboard.cljs` performs a direct `fetch`, and `/hyperopen/src/hyperopen/api/request_policy.cljs` has no leaderboard entry.

- Observation: startup restore is the wrong seam for this payload even though the app already restores some IndexedDB-backed state during boot.
  Evidence: the user asked to avoid repeated route reload slowness while moving around the app, and `/hyperopen/docs/BROWSER_STORAGE.md` says large or growing caches should use IndexedDB with async restore only where that surface already tolerates it. Loading a leaderboard-sized payload at startup would broaden work onto routes that do not need it.

- Observation: the nearest repository pattern is vault index caching, but leaderboard needs a stricter “serve fresh cache and skip network entirely” branch because there is no conditional GET validator path here.
  Evidence: `/hyperopen/src/hyperopen/vaults/effects.cljs` always falls through to a network request after optional cache hydration, while the leaderboard endpoint in `/hyperopen/src/hyperopen/api/endpoints/leaderboard.cljs` has no ETag or last-modified handling.

- Observation: the `/vaults` route reuses the same vault-index endpoint that leaderboard uses to derive excluded addresses, so the SPA revisit browser proof has to snapshot network counters after leaving `/leaderboard` rather than expecting the raw vault request total to stay flat across the whole round-trip.
  Evidence: the initial Playwright revisit assertion failed with `vaultRequests = 2` after navigating to `/vaults`, while the return navigation back to `/leaderboard` did not add any further leaderboard or vault requests.

## Decision Log

- Decision: add a dedicated `/hyperopen/src/hyperopen/leaderboard/cache.cljs` module instead of folding cache logic into `leaderboard/effects.cljs` or `runtime/effect_adapters/leaderboard.cljs`.
  Rationale: feature modules in this repo own normalization, record shape, and freshness policy, while effect adapters stay thin and reducers stay pure.
  Date/Author: 2026-03-24 / Codex

- Decision: make cache loading route-driven, not startup-driven.
  Rationale: the user problem is repeated `/leaderboard` visits, not initial app boot. Route-driven loading avoids putting a large async payload into unrelated startup work.
  Date/Author: 2026-03-24 / Codex

- Decision: use a one-hour TTL keyed by cache record `:saved-at-ms`, and treat current in-memory `:leaderboard :loaded-at-ms` as equally authoritative for the same freshness decision.
  Rationale: this solves both rapid in-session revisits and full-page refresh revisits without needing two separate policies.
  Date/Author: 2026-03-24 / Codex

- Decision: persist the already-derived payload `{ :rows ... :excluded-addresses ... }` after a successful network load.
  Rationale: replaying only raw leaderboard rows would still require another vault-index request to rebuild excluded-address filtering before rendering, which defeats much of the revisit-speed benefit.
  Date/Author: 2026-03-24 / Codex

## Outcomes & Retrospective

The leaderboard now keeps a one-hour IndexedDB cache of the already-derived payload `{ :rows ... :excluded-addresses ... }` and uses the same freshness policy for both in-memory state and persisted route cache. Revisiting `/leaderboard` within one hour reuses the current snapshot when it is already in memory, while a full-page reload can rehydrate from IndexedDB and skip the network. Explicit user refresh still bypasses cache through a force-refresh flag so retry semantics remain intact.

Validation covered both internal and user-visible behavior. Focused CLJS tests pin normalization, TTL, fresh-memory reuse, fresh IndexedDB reuse, stale cache fallback, stale in-memory fallback, and force-refresh bypass. Playwright smoke now proves both blocked-network reload recovery and SPA leave-and-return reuse without a second leaderboard fetch. Repository gates passed via `npm run test:websocket`, `npm test`, and `npm run check`, and governed browser QA passed for `leaderboard-route` across `375`, `768`, `1280`, and `1440`.

Residual risk is limited to the standard browser-QA blind spot that hover, active, disabled, and loading states were not all force-driven during governed review. No blocker or correctness issue remains from the implementation itself.

## Context and Orientation

The leaderboard route has four relevant layers. `/hyperopen/src/hyperopen/leaderboard/actions.cljs` emits route-load and control-change effects. `/hyperopen/src/hyperopen/leaderboard/effects.cljs` owns the asynchronous fetch flow for leaderboard data and currently requests leaderboard rows plus vault index rows in parallel, then derives `:excluded-addresses` before writing store state. `/hyperopen/src/hyperopen/runtime/effect_adapters/leaderboard.cljs` is the thin runtime seam that injects API and projection functions into that feature effect. `/hyperopen/src/hyperopen/api/projections.cljs` applies the final leaderboard payload to store state.

Browser persistence must not appear in reducers or domain logic. The shared IndexedDB boundary is `/hyperopen/src/hyperopen/platform/indexed_db.cljs`. Existing feature cache examples include `/hyperopen/src/hyperopen/vaults/infrastructure/list_cache.cljs`, `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs`, and `/hyperopen/src/hyperopen/leaderboard/preferences.cljs`. Those modules all normalize untrusted stored records, carry a stable `:id`, `:version`, and `:saved-at-ms`, and keep TTL or restore policy outside projections.

The current leaderboard state shape lives in `/hyperopen/src/hyperopen/state/app_defaults.cljs` as:

    :leaderboard {:rows []
                  :excluded-addresses #{}
                  :loading? false
                  :error nil
                  :error-category nil
                  :loaded-at-ms nil}

That existing `:loaded-at-ms` field can be treated as the age of the current in-memory snapshot regardless of whether it came from network or IndexedDB. That lets one freshness function govern both in-session reuse and post-refresh cache hydration.

## Plan of Work

First, add `/hyperopen/src/hyperopen/leaderboard/cache.cljs`. This module will define the leaderboard cache record, normalize stored rows and excluded addresses defensively, build a deterministic record for IndexedDB, load and persist through `/hyperopen/src/hyperopen/platform/indexed_db.cljs`, and expose freshness helpers for both cache records and current store state. The record must include `:id`, `:version`, `:saved-at-ms`, `:rows`, and `:excluded-addresses`. TTL will be one hour, defined in milliseconds inside the cache module.

Second, extend `/hyperopen/src/hyperopen/platform/indexed_db.cljs` with a new leaderboard cache object store and bump the shared database version so the store is created during upgrade. The cache module should use that store only; it should not add direct `js/indexedDB` access anywhere else.

Third, teach `/hyperopen/src/hyperopen/api/projections.cljs` to hydrate leaderboard state from a cached record. Add a dedicated projection such as `apply-leaderboard-cache-hydration` that writes rows, excluded addresses, clears errors, ensures loading is false, and sets `:loaded-at-ms` from cache `:saved-at-ms`. Keep `apply-leaderboard-success` as the network-success projection.

Fourth, update `/hyperopen/src/hyperopen/leaderboard/effects.cljs` so `api-fetch-leaderboard!` becomes a cache-aware route effect. It should:

1. honor the existing route gate,
2. return immediately when the current in-memory leaderboard state is still fresh,
3. otherwise try loading an IndexedDB cache record,
4. hydrate the store and skip the network when that record is still fresh and newer than current state,
5. otherwise perform the existing network request, apply success or error, and persist the derived success payload back into IndexedDB.

The effect must not let an older cache record overwrite newer network data. Use a guard based on current `:leaderboard :loaded-at-ms` and cache `:saved-at-ms` before applying cache hydration.

Fifth, keep the runtime surface thin. `/hyperopen/src/hyperopen/runtime/effect_adapters/leaderboard.cljs` should inject cache load and persist seams plus the new cache-hydration projection into the feature effect. If any public facade tests need to know about new helpers, update `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs` and its corresponding tests, but avoid adding a new effect id unless it is truly necessary.

Finally, add focused tests for cache normalization, TTL behavior, projections, effect behavior, and route-level browser proof. The Playwright proof should demonstrate that after an initial leaderboard load, a second visit can still render rows when the network is intentionally blocked, which proves the route is using cache rather than silently refetching.

## Concrete Steps

Work from `/hyperopen`.

1. Add the cache module and IndexedDB store:

    apply_patch on `/hyperopen/src/hyperopen/leaderboard/cache.cljs`
    apply_patch on `/hyperopen/src/hyperopen/platform/indexed_db.cljs`

2. Add cache hydration projection and cache-aware effect logic:

    apply_patch on `/hyperopen/src/hyperopen/api/projections.cljs`
    apply_patch on `/hyperopen/src/hyperopen/leaderboard/effects.cljs`
    apply_patch on `/hyperopen/src/hyperopen/runtime/effect_adapters/leaderboard.cljs`

3. Add or update regression tests:

    apply_patch on `/hyperopen/test/hyperopen/leaderboard/cache_test.cljs`
    apply_patch on `/hyperopen/test/hyperopen/api/projections_test.cljs`
    apply_patch on `/hyperopen/test/hyperopen/leaderboard/effects_test.cljs`
    apply_patch on any runtime facade or wiring test that needs the cache seam covered

4. Add the smallest deterministic browser regression:

    apply_patch on `/hyperopen/tools/playwright/test/routes.smoke.spec.mjs`

5. Run focused and full validation:

    cd /hyperopen
    npm exec shadow-cljs -- compile test
    node out/test.js --test=hyperopen.leaderboard.cache-test,hyperopen.leaderboard.effects-test,hyperopen.api.projections-test,hyperopen.runtime.wiring-test,hyperopen.runtime.effect-adapters.facade-contract-test
    npx playwright test tools/playwright/test/routes.smoke.spec.mjs --grep "leaderboard cache"
    npm run test:websocket
    npm test
    npm run check

If governed browser QA is required after implementation because the interaction flow materially changed, run:

    cd /hyperopen
    npm run qa:design-ui -- --targets leaderboard-route --manage-local-app

## Validation and Acceptance

Acceptance is user-visible route behavior. After the first successful `/leaderboard` load, leaving the route and returning within one hour should reuse cached data instead of forcing another blocking leaderboard refresh. Reloading the page within one hour should also be able to render the leaderboard from IndexedDB.

Automated acceptance requires:

- malformed or partial leaderboard cache records normalize safely
- a fresh cache record is recognized as fresh for one hour
- stale or invalid cache records do not suppress the network request
- fresh in-memory leaderboard state suppresses repeated route fetches
- fresh IndexedDB cache hydrates store state and skips the network request
- successful network responses persist the derived leaderboard payload back to IndexedDB
- repository gates pass:

    cd /hyperopen
    npm run test:websocket
    npm test
    npm run check

The browser regression should prove cache use by scenario, not by internal state. A good proof is: seed a successful leaderboard response, then block the leaderboard network endpoint and confirm a second `/leaderboard` visit still renders the known row from cache and does not surface a network error.

## Idempotence and Recovery

This work is additive and safe to retry. Re-running the route effect will either reuse a fresh snapshot or fetch and overwrite the cache with a newer snapshot. If the cache module misbehaves, the safe rollback is to remove the cache store, cache hydration projection, and cache-aware branches, leaving the existing direct network path intact. Do not move the cache into startup restore as a fallback; that would widen the surface rather than restoring the prior behavior.

## Artifacts and Notes

The intended cache record shape is:

    {:id "leaderboard-cache:v1"
     :version 1
     :saved-at-ms <epoch-ms>
     :rows [{:eth-address "0x..."
             :account-value 123
             :display-name "Desk"
             :prize 0
             :window-performances {...}}]
     :excluded-addresses ["0xvault" "0xsystem"]}

The intended route decision flow is:

    route enters /leaderboard
    if current store leaderboard snapshot is fresh:
      skip network
    else:
      load IndexedDB cache
      if cache is fresh and not older than current store state:
        hydrate store from cache
        skip network
      else:
        fetch leaderboard rows + vault index
        derive excluded addresses
        write success projection
        persist new cache record

## Interfaces and Dependencies

In `/hyperopen/src/hyperopen/leaderboard/cache.cljs`, define stable helpers with these responsibilities:

    normalize-leaderboard-cache-record
      Accepts untrusted stored data and returns a normalized cache record or nil.

    build-leaderboard-cache-record
      Accepts derived leaderboard payload and produces the deterministic IndexedDB record.

    load-leaderboard-cache-record!
      Reads the single cache record from IndexedDB and resolves to normalized cache or nil.

    persist-leaderboard-cache-record!
      Persists the derived payload to IndexedDB and resolves to boolean success.

    fresh-leaderboard-snapshot?
      Returns true when a `saved-at-ms` or `loaded-at-ms` timestamp is within the one-hour TTL.

In `/hyperopen/src/hyperopen/api/projections.cljs`, add:

    apply-leaderboard-cache-hydration
      Accepts app state and a normalized cache record, writes rows plus excluded addresses, clears error state, and sets `:loaded-at-ms` from `:saved-at-ms`.

In `/hyperopen/src/hyperopen/leaderboard/effects.cljs`, keep the public `api-fetch-leaderboard!` entry point but extend its dependency map to optionally accept:

    :load-leaderboard-cache-record!
    :persist-leaderboard-cache-record!
    :apply-leaderboard-cache-hydration

Revision note: created on 2026-03-24 for `hyperopen-4juc` after route-flow research and before implementation so the cache record shape, TTL policy, route-driven design, and validation strategy are explicit.
