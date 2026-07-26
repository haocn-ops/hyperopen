# Migrate Asset Selector Markets Cache from localStorage to IndexedDB

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Today the selector market cache is stored as one large JSON payload in browser `localStorage` under `asset-selector-markets-cache`. That write is triggered by a store watch that tracks `[:asset-selector :markets]`, and this vector can change frequently because active asset context websocket updates patch market rows continuously. `localStorage` writes are synchronous and block the main thread, so this path can add avoidable UI latency under market churn.

After this change, selector cache persistence moves to IndexedDB (a browser storage system with asynchronous read and write APIs). Warm selector hydration still works on reload, but large cache writes no longer run as synchronous `localStorage.setItem` calls. A contributor can verify this by opening the app, watching selector updates under websocket traffic, and confirming cache rows are written in IndexedDB while selector behavior remains unchanged.

## Progress

- [x] (2026-03-05 15:15Z) Audited existing selector cache read/write flow in `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs`, `/hyperopen/src/hyperopen/startup/watchers.cljs`, and `/hyperopen/src/hyperopen/startup/init.cljs`.
- [x] (2026-03-05 15:15Z) Confirmed high-frequency market vector mutation source via websocket patching in `/hyperopen/src/hyperopen/asset_selector/market_live_projection.cljs`.
- [x] (2026-03-05 15:15Z) Authored initial IndexedDB cutover plan with staged migration and rollback path.
- [x] (2026-03-05 15:18Z) Filed tracking issue in `bd`: `hyperopen-eux.1` under epic `hyperopen-eux`.
- [x] (2026-03-05 19:05Z) Added shared IndexedDB platform boundary and Promise-aware browser test doubles.
- [x] (2026-03-05 19:05Z) Migrated selector cache persistence and restore to IndexedDB-first behavior with localStorage fallback metadata.
- [x] (2026-03-05 19:05Z) Added requestAnimationFrame write coalescing for selector cache store watches.
- [x] (2026-03-05 19:05Z) Updated selector cache, runtime bootstrap, watcher, and platform tests; ran `npm run check`, `npm test`, and `npm run test:websocket`.

## Surprises & Discoveries

- Observation: Selector cache writes are currently triggered from a store watch any time `[:asset-selector :markets]` changes.
  Evidence: `/hyperopen/src/hyperopen/startup/watchers.cljs` `install-store-cache-watchers!` compares old/new `:markets` and calls `persist-asset-selector-markets-cache!`.

- Observation: `:markets` changes are fed by active-asset websocket context updates, not only by full selector refetches.
  Evidence: `/hyperopen/src/hyperopen/websocket/active_asset_ctx.cljs` calls `market-live-projection/apply-active-asset-ctx-update`, which updates `[:asset-selector :markets]` in `/hyperopen/src/hyperopen/asset_selector/market_live_projection.cljs`.

- Observation: Startup restore currently assumes synchronous local cache reads.
  Evidence: `/hyperopen/src/hyperopen/startup/init.cljs` calls `restore-asset-selector-markets-cache!` inside `restore-persisted-ui-state!` before post-render startup scheduling.

## Decision Log

- Decision: Use a staged cutover with read fallback rather than a single hard switch.
  Rationale: Selector startup behavior is user-visible and must stay deterministic; fallback allows rollback without losing warm-start behavior.
  Date/Author: 2026-03-05 / Codex

- Decision: Keep selector cache payload shape unchanged while moving storage backend.
  Rationale: The existing normalization and restore logic is already tested; backend migration risk is lower when schema remains stable.
  Date/Author: 2026-03-05 / Codex

- Decision: Add write coalescing before persisting to IndexedDB.
  Rationale: Asynchronous storage removes sync blocking cost, but unbounded write volume can still increase CPU and transaction overhead.
  Date/Author: 2026-03-05 / Codex

## Outcomes & Retrospective

Implemented as planned. `/hyperopen/src/hyperopen/platform/indexed_db.cljs` now provides the shared async storage boundary, `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs` persists selector rows to IndexedDB-first records with fallback hydration from localStorage, and `/hyperopen/src/hyperopen/startup/watchers.cljs` coalesces selector writes to one snapshot per animation frame. Validation passed through `npm run check`, `npm test`, and `npm run test:websocket`.

## Context and Orientation

The selector cache has four key seams:

- `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs` currently builds normalized cache rows and reads/writes `localStorage`.
- `/hyperopen/src/hyperopen/startup/watchers.cljs` persists cache whenever selector markets change.
- `/hyperopen/src/hyperopen/startup/init.cljs` restores cache during startup.
- `/hyperopen/src/hyperopen/runtime/effect_adapters/asset_selector.cljs` is the adapter boundary used by startup/bootstrap wiring.

In this repository, “IndexedDB” means browser-managed asynchronous storage organized as a database containing object stores (tables keyed by primary key). It is different from `localStorage`, which exposes synchronous string-only key/value access.

A novice implementing this plan should preserve all current selector-facing state contracts (`:asset-selector :markets`, `:market-by-key`, `:market-index-by-key`, `:cache-hydrated?`) and only change storage transport behavior.

Issue tracking for this plan is in `bd`:

- Epic: `hyperopen-eux`
- Task: `hyperopen-eux.1`

## Plan of Work

### Milestone 1: Add a Shared IndexedDB Platform Boundary

Create a small platform layer that hides browser IndexedDB boilerplate behind Promise-returning helpers. Add a new namespace at `/hyperopen/src/hyperopen/platform/indexed_db.cljs` and expose narrowly scoped operations from `/hyperopen/src/hyperopen/platform.cljs` (or via direct require where appropriate). The boundary must support get, put, and delete for JSON payloads and must return graceful defaults when IndexedDB is unavailable.

This milestone is complete when selector cache code can call a stable async API without directly using `js/indexedDB` calls.

### Milestone 2: Implement Selector Cache Backend Migration

Update `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs` so persistence writes to IndexedDB, and restore reads IndexedDB first with a temporary localStorage fallback. Keep existing normalization and restore logic unchanged. Include migration metadata (`saved-at-ms` or version field) so newer persisted records win when both backends are present.

This milestone is complete when restore can hydrate from IndexedDB and the old localStorage path is only fallback.

### Milestone 3: Coalesce Writes from Store Watchers

Update `/hyperopen/src/hyperopen/startup/watchers.cljs` and any supporting runtime helpers to avoid writing on every small `:markets` mutation. Add a coalescing strategy (for example one queued write per animation frame with latest snapshot, and optional minimum interval). Keep behavior deterministic: projection updates still happen immediately in store; only persistence side effects are deferred.

This milestone is complete when repeated websocket market patches generate bounded persistence writes.

### Milestone 4: Tighten Tests and Finalize Cutover

Update existing tests and add new ones:

- `/hyperopen/test/hyperopen/asset_selector/markets_cache_test.cljs`
- `/hyperopen/test/hyperopen/startup/watchers_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap/asset_cache_persistence_test.cljs`
- New platform IndexedDB tests (for example `/hyperopen/test/hyperopen/platform/indexed_db_test.cljs`)

Add tests for fallback behavior, unavailable IndexedDB, and write coalescing. After tests pass with fallback in place, optionally add a follow-up task to remove selector-specific localStorage fallback once rollout confidence is high.

## Concrete Steps

All commands run from `/hyperopen`.

1. Create or extend IndexedDB platform helper namespace and tests.

    ls src/hyperopen/platform
    rg -n "local-storage|indexed" src/hyperopen/platform.cljs src/hyperopen/platform

2. Add selector cache backend adapter functions and staged fallback behavior.

    rg -n "asset-selector-markets-cache|persist-asset-selector-markets-cache|restore-asset-selector-markets-cache" src/hyperopen/asset_selector/markets_cache.cljs src/hyperopen/startup/watchers.cljs src/hyperopen/startup/init.cljs

3. Add write coalescing in watcher/runtime seam.

    rg -n "install-store-cache-watchers|persist-asset-selector-markets-cache" src/hyperopen/startup/watchers.cljs src/hyperopen/runtime

4. Update or add tests.

    npm test -- --focus=asset_selector/markets_cache

   If focused test execution is unavailable, run full suite commands below.

5. Run required repository gates.

    npm run check
    npm test
    npm run test:websocket

## Validation and Acceptance

Acceptance is behavior-based:

1. Selector warm hydration still works after reload: cached symbols appear when selector opens before network fetch completes.
2. Under active websocket market updates, persistence writes do not occur once per every store patch; they are coalesced.
3. IndexedDB contains selector cache records in the configured store.
4. Existing selector behavior is unchanged: active-market resolution, cache-hydrated flag behavior, and sort/order semantics remain intact.
5. Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

Manual verification example:

- Load trade page, open asset selector, wait for market data churn.
- In browser devtools Application tab, confirm IndexedDB selector store entry updates.
- Reload page and confirm selector symbols hydrate without waiting for full market fetch.

## Idempotence and Recovery

Implementation steps are additive and safe to re-run. If IndexedDB wiring fails in a browser or test environment, fallback read path from localStorage must continue to work.

Rollback strategy:

- Keep a single selector cache backend toggle in code during rollout.
- If IndexedDB regression appears, switch toggle back to localStorage-only behavior while retaining migration code for later fix.
- Do not delete existing localStorage key until fallback decommission is explicitly approved.

## Artifacts and Notes

Use one IndexedDB database for app persistence (shared with other migrations), with a dedicated object store for selector cache records. Keep store key deterministic (for example singleton key `"asset-selector-markets-cache"`) and store JSON payload plus metadata:

    {:id "asset-selector-markets-cache"
     :version 1
     :saved-at-ms <epoch-ms>
     :rows [..normalized selector cache rows..]}

This format keeps migration simple while preserving existing restore logic.

## Interfaces and Dependencies

Define (or equivalent) async interfaces at the end of implementation:

- In `/hyperopen/src/hyperopen/platform/indexed_db.cljs`:

    open-db! :: map -> js/Promise
    get-json! :: db-name store-name key -> js/Promise
    put-json! :: db-name store-name key value -> js/Promise
    delete-key! :: db-name store-name key -> js/Promise

- In `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs`:

    persist-asset-selector-markets-cache! :: markets state [opts] -> js/Promise
    load-asset-selector-markets-cache :: [opts] -> js/Promise
    restore-asset-selector-markets-cache! :: store [opts] -> js/Promise

`restore-asset-selector-markets-cache!` must remain safe to call when cache is empty and must never overwrite non-empty in-memory selector markets.

Revision note (2026-03-05 / Codex): Initial plan drafted from current localStorage audit to drive IndexedDB cutover with low-risk staged migration.
