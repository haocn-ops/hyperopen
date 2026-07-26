# Vaults Route Stale-While-Revalidate

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is governed by `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The tracked work item for this plan is `hyperopen-dmiv`; the `bd` issue is historical local tracker context for this plan.

## Purpose / Big Picture

The `/vaults` route currently re-downloads and re-parses a large static vault index on every route load, and any list loading state blanks the visible list and TVL card with skeletons even when usable rows already exist in memory. This change makes vault metadata behave like a stale-while-revalidate surface: cached normalized preview rows can appear immediately on vault routes, refreshes keep stale data visible, and the large index fetch becomes conditional via `ETag` and `Last-Modified`.

After this change, users should see cached or already-loaded vault rows remain visible during refresh, with a lightweight `Refreshing vaults…` banner instead of a full skeleton reset. Fresh cold loads with no cached or live baseline should still show the current loading skeletons.

## Progress

- [x] (2026-03-20 16:38Z) Created and claimed `hyperopen-dmiv` for the vault stale-while-revalidate work.
- [x] (2026-03-20 16:38Z) Audited the current vault route loaders, endpoint/gateway/effect stack, IndexedDB helpers, startup cache patterns, and vault list loading behavior.
- [x] (2026-03-20 17:06Z) Implemented the vault index cache module, IndexedDB store registration, structured validator-aware requests, and the cache-aware fetch effect.
- [x] (2026-03-20 17:08Z) Updated vault route loaders, projections, and VM/view loading semantics so refreshes preserve visible rows and TVL while showing `Refreshing vaults…`.
- [x] (2026-03-20 17:16Z) Extended targeted tests, installed missing JS dependencies, ran `npm run check`, `npm test`, `npm run test:websocket`, and executed governed browser QA for `vaults-route` and `vault-detail-route`.

## Surprises & Discoveries

- Observation: the current route-scoped vault loaders emit `:effects/api-fetch-vault-index` directly, but `/portfolio` also conditionally fetches vault metadata when vault benchmarks are in use, so the route-specific cache path must stay off the cold `/portfolio` flow in this phase.
  Evidence: `/hyperopen/src/hyperopen/vaults/application/route_loading.cljs`, `/hyperopen/src/hyperopen/vaults/application/detail_commands.cljs`, and `/hyperopen/src/hyperopen/portfolio/actions.cljs`.

- Observation: the runtime effect-order contract only treats `:effects/save` and localStorage effects as tracked projection and persistence phases, so a new cache-aware fetch effect can remain a heavy I/O effect without needing additional contract categories.
  Evidence: `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs` classifies only `:effects/save`, `:effects/save-many`, `:effects/local-storage-set`, and `:effects/local-storage-set-json` into ordered phases.

- Observation: the first cache-module pass accidentally re-normalized already-normalized preview rows through the raw vault endpoint normalizer, which would have stripped `:vault-address`, relationship shape, and `:create-time-ms` from persisted rows.
  Evidence: the preview-row cache regression was caught while writing `/hyperopen/test/hyperopen/vaults/infrastructure/list_cache_test.cljs`, then fixed in `/hyperopen/src/hyperopen/vaults/infrastructure/list_cache.cljs` by adding a dedicated preview-row normalizer.

- Observation: the governed design-review tooling can account for the vault detail surface visually and structurally, but the interaction pass remains blocked when the managed route setup cannot reach hover/active/disabled/loading states for `/vaults/detail`.
  Evidence: `/hyperopen/tmp/browser-inspection/design-review-2026-03-20T17-14-48-930Z-3f8545ce/summary.json` reports `interaction: BLOCKED` with the residual blind spot `/vaults/detail: hover, active, disabled, and loading states were not reachable.`

## Decision Log

- Decision: restore cached vault index rows only from vault route metadata paths, not global startup and not cold `/portfolio`.
  Rationale: this keeps the rollout scoped to the user-reported slow path while still allowing `/portfolio` to reuse warmed in-memory validators and rows after a vault route has already run in the session.
  Date/Author: 2026-03-20 / Codex

- Decision: cache normalized vault index preview rows rather than raw response payloads.
  Rationale: the list, vault benchmark selectors, and detail metadata bootstrap already depend on normalized preview row shape, and caching that shape avoids repeating large-payload parsing on the hot route path.
  Date/Author: 2026-03-20 / Codex

## Outcomes & Retrospective

This rollout achieved the route-scoped stale-while-revalidate behavior without changing the existing full-dataset list semantics:

- vault list and vault detail metadata loaders now emit a cache-aware internal effect while `/portfolio` remains network-only on cold load
- the large vault index request now supports conditional `ETag` / `Last-Modified` revalidation with explicit `304` handling
- normalized preview rows plus validator metadata persist in IndexedDB and hydrate into state only on vault routes
- vault refreshes keep stale rows and TVL visible with a `Refreshing vaults…` banner instead of replacing the surface with skeletons
- the old non-cache vault index effect now also forwards in-memory validators so warmed `/portfolio` flows benefit in-session
- focused test coverage now covers cache normalization, hydration, `304` handling, stale-row preservation on errors, route emissions, and refresh-versus-initial rendering

Validation results for this work were:

- `npm run check`: PASS
- `npm test`: PASS (`2566` tests, `13671` assertions, `0` failures, `0` errors)
- `npm run test:websocket`: PASS (`401` tests, `2297` assertions, `0` failures, `0` errors)
- `npm run qa:design-ui -- --targets vaults-route,vault-detail-route --manage-local-app`: BLOCKED overall
  `vaults-route`: PASS for visual, native-control, styling-consistency, interaction, layout-regression, and jank-perf at `375`, `768`, `1280`, and `1440`
  `vault-detail-route`: PASS for visual, native-control, styling-consistency, layout-regression, and jank-perf at `375`, `768`, `1280`, and `1440`; `interaction` BLOCKED at all four widths because the managed review could not reach hover/active/disabled/loading states

## Context and Orientation

Vault list metadata today flows through `/hyperopen/src/hyperopen/vaults/application/route_loading.cljs`, `/hyperopen/src/hyperopen/vaults/effects.cljs`, `/hyperopen/src/hyperopen/runtime/effect_adapters/vaults.cljs`, `/hyperopen/src/hyperopen/api/default.cljs`, `/hyperopen/src/hyperopen/api/gateway/vaults.cljs`, and `/hyperopen/src/hyperopen/api/endpoints/vaults.cljs`. Successful index and summaries responses are merged into `:vaults :merged-index-rows` by `/hyperopen/src/hyperopen/api/projections.cljs`, then rendered by `/hyperopen/src/hyperopen/views/vaults/vm.cljs` and `/hyperopen/src/hyperopen/views/vaults/list_view.cljs`.

The browser persistence boundary for IndexedDB already lives in `/hyperopen/src/hyperopen/platform/indexed_db.cljs`, and existing cache modules such as `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs` and `/hyperopen/src/hyperopen/funding/history_cache.cljs` show the repo’s preferred patterns for deterministic records with `:saved-at-ms`, IndexedDB-first reads, and graceful fallback when browser storage is unavailable.

For this feature, “vault index” means the large static JSON file returned by `request-vault-index!`, not the smaller `vaultSummaries` info request or `userVaultEquities` info request. The stale-while-revalidate goal applies only to that large static index fetch. Summaries and user equities stay live and uncached beyond existing request TTL policy.

## Plan of Work

First, introduce a new vault cache boundary under `/hyperopen/src/hyperopen/vaults/infrastructure/` plus a dedicated IndexedDB object store entry in `/hyperopen/src/hyperopen/platform/indexed_db.cljs`. The cache record will carry a fixed key, `:version`, `:saved-at-ms`, `:etag`, `:last-modified`, and normalized `:rows`. The module must expose record normalization, IndexedDB load, IndexedDB persist, and helper functions that update app state with cached rows and validators without touching unrelated vault data.

Next, extend the vault endpoint stack so there is an internal structured request path that can return either `{:status :ok ...}` with normalized rows and validator headers or `{:status :not-modified ...}` for HTTP `304`. The public `request-vault-index!` wrapper must still return rows only, because other callers and tests already depend on that surface. The vault index effect layer must then learn two paths: the existing network-only effect should pass any in-memory validators into the request, and a new cache-aware effect should hydrate cache on vault routes only when in-memory index rows are empty before issuing the conditional request.

Then, update the vault projections and view model so loading distinguishes between “no baseline rows exist” and “a refresh is running over already-visible rows.” Cached hydration and `304` responses must update `[:vaults :index-cache]` while preserving stale rows. The list view must continue to use skeletons on a truly empty first load, but when rows exist it must render them with a small `Refreshing vaults…` status banner and keep the TVL card visible.

Finally, extend the existing focused tests for cache record behavior, route loader emissions, endpoint/gateway conditional handling, effect ordering, projection state transitions, and list rendering. After the code is in place, run the required repo gates and governed browser QA, then record the results and remaining risks back into this plan.

## Concrete Steps

1. Add the active vault index cache module and IndexedDB store registration.
2. Add the structured conditional vault index request path and keep the legacy row-only wrapper.
3. Add `:effects/api-fetch-vault-index-with-cache` and wire it into vault route metadata loaders only.
4. Update vault projections, VM, and list view for refresh-preserving rendering.
5. Extend automated tests for cache, requests, effects, projections, and UI rendering.
6. Run `npm run check`, `npm test`, `npm run test:websocket`, and `npm run qa:design-ui -- --targets vaults-route,vault-detail-route --manage-local-app`.

## Validation and Acceptance

Acceptance is behavior-based:

- cold `/vaults` visits with no live rows and no cache still show loading skeletons
- `/vaults` visits with cached or already-loaded rows keep rows and TVL visible while refresh is in flight
- the list surface shows `Refreshing vaults…` during refresh instead of replacing visible rows with skeletons
- conditional vault index requests send `If-None-Match` and `If-Modified-Since` when validators are available
- HTTP `304` clears loading and refresh metadata without replacing visible rows
- `/portfolio` does not hydrate IndexedDB on cold load in this phase, but can reuse warmed in-memory vault rows and validators after vault routes load
- `npm run check`, `npm test`, `npm run test:websocket`, and governed browser QA are all explicitly accounted for

## Idempotence and Recovery

The source changes are additive and safe to re-run. If the cache path causes regressions, the safest fallback is to disable the new cache-aware effect emission and keep the structured conditional request plumbing, because that preserves the smaller behavior change of refresh-preserving UI plus validator-aware network fetches. IndexedDB failures must not block vault route rendering; the implementation should catch storage errors, log a warning, and continue with the network path.

## Artifacts and Notes

The tracked issue is `hyperopen-dmiv`.

Governed browser-QA artifacts for this work live under `/hyperopen/tmp/browser-inspection/design-review-2026-03-20T17-14-48-930Z-3f8545ce`, including `summary.json`, `summary.md`, per-route screenshots, and pass probes.

## Interfaces and Dependencies

This feature adds one internal effect id, `:effects/api-fetch-vault-index-with-cache`, and one internal state path, `[:vaults :index-cache]`, containing `{:hydrated? boolean :saved-at-ms ms-or-nil :etag string-or-nil :last-modified string-or-nil}`. No public route, action, or user-facing API contracts are intended to change. The existing client-side full-dataset sort/filter-before-pagination behavior in `/hyperopen/src/hyperopen/views/vaults/vm.cljs` remains unchanged.

Plan update note (2026-03-20): implementation is complete, the repo validation gates passed, and the governed browser QA result is explicitly recorded as `BLOCKED` only on the vault-detail interaction pass because the managed review could not reach those interactive states.
