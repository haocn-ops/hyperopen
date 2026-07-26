# Active-Asset Websocket Selector Updates: Key->Index Incremental Row Patching

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Today, each coalesced `activeAssetCtx` websocket flush can still remap the entire selector market vector through `mapv`, even when only one or two rows changed. This happens in `/hyperopen/src/hyperopen/asset_selector/market_live_projection.cljs` and is invoked from `/hyperopen/src/hyperopen/websocket/active_asset_ctx.cljs` inside the frame-coalesced projection write.

After this refactor, selector row patching will use a persisted key-to-index map and update only changed row indices. User-visible selector behavior (prices, 24H change, funding, volume, open interest) remains the same, but hot-path allocation and per-flush CPU should drop under websocket churn.

## Progress

- [x] (2026-03-02 14:25Z) Confirmed ExecPlan requirements from `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`.
- [x] (2026-03-02 14:25Z) Audited active-asset websocket projection path in `/hyperopen/src/hyperopen/websocket/active_asset_ctx.cljs` and selector patch implementation in `/hyperopen/src/hyperopen/asset_selector/market_live_projection.cljs`.
- [x] (2026-03-02 14:25Z) Audited selector market ownership paths (`/hyperopen/src/hyperopen/api/endpoints/market.cljs`, `/hyperopen/src/hyperopen/api/projections.cljs`, `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs`, `/hyperopen/src/hyperopen/state/app_defaults.cljs`) and relevant tests.
- [x] (2026-03-02 14:25Z) Authored this active ExecPlan.
- [x] (2026-03-02 14:31Z) Implemented `:market-index-by-key` ownership and hydration across selector state producers (`market` endpoint build path, selector projections, cache restore, app defaults).
- [x] (2026-03-02 14:33Z) Refactored selector live projection to patch changed vector indices using key->index lookup with stale-index rebuild fallback.
- [x] (2026-03-02 14:36Z) Added regression coverage for indexed patching behavior, stale-index fallback, and selector state-shape expectations.
- [x] (2026-03-02 14:39Z) Ran required validation gates (`npm run check`, `npm test`, `npm run test:websocket`) successfully.

## Surprises & Discoveries

- Observation: Selector live patching still performs a full vector remap per projection flush.
  Evidence: `/hyperopen/src/hyperopen/asset_selector/market_live_projection.cljs` currently uses `patch-selector-markets` with `mapv` over the whole markets vector.

- Observation: Selector state currently stores `:markets` and `:market-by-key` but not a key-to-row-index map.
  Evidence: `/hyperopen/src/hyperopen/state/app_defaults.cljs` default selector shape omits any index map, and hydration/projection paths in `/hyperopen/src/hyperopen/api/projections.cljs` and `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs` do not set one.

- Observation: Existing tests prove value updates but do not yet assert structural behavior (only changed indices replaced, unchanged rows preserved by identity).
  Evidence: `/hyperopen/test/hyperopen/asset_selector/market_live_projection_test.cljs` and `/hyperopen/test/hyperopen/websocket/active_asset_ctx_test.cljs` assert content fields only.

- Observation: `npm test` does not accept direct test-file arguments in this repository; extra file args are ignored and the full generated runner executes.
  Evidence: Running `npm test -- <file ...>` printed `Unknown arg: ...` and still ran the full suite.

- Observation: Fresh workspace execution required dependency install before gates because `shadow-cljs` binary was initially unavailable.
  Evidence: First test attempt failed with `sh: shadow-cljs: command not found`; `npm ci` resolved it.

## Decision Log

- Decision: Introduce selector row index ownership at `[:asset-selector :market-index-by-key]`.
  Rationale: This name mirrors existing selector map naming (`:market-by-key`) and supports O(1) row lookup for patching.
  Date/Author: 2026-03-02 / Codex

- Decision: Keep `market-live-projection/apply-active-asset-ctx-update` public function signature unchanged and make indexed patching an internal implementation detail.
  Rationale: The call boundary from `/hyperopen/src/hyperopen/websocket/active_asset_ctx.cljs` remains stable while the hot path is optimized.
  Date/Author: 2026-03-02 / Codex

- Decision: Treat `:market-index-by-key` as self-healing and compatibility-safe by rebuilding an index from `:markets` when missing or stale.
  Rationale: Existing state fixtures and persisted snapshots may not carry the new key initially; fallback avoids correctness risk while preserving deterministic behavior.
  Date/Author: 2026-03-02 / Codex

- Decision: In `apply-asset-selector-success`, derive `:market-index-by-key` from `:markets` when callers do not provide it.
  Rationale: Existing projection call sites and tests may omit the new key during transition; deriving fallback index keeps projection deterministic and backward-compatible.
  Date/Author: 2026-03-02 / Codex

## Outcomes & Retrospective

Implementation is complete for the scoped indexed selector patching refactor.

Delivered outcomes:

- Added `:market-index-by-key` ownership in selector market state creation/hydration paths:
  - `/hyperopen/src/hyperopen/api/endpoints/market.cljs`
  - `/hyperopen/src/hyperopen/api/projections.cljs`
  - `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs`
  - `/hyperopen/src/hyperopen/state/app_defaults.cljs`
- Replaced selector full-vector websocket remap with indexed row patching in `/hyperopen/src/hyperopen/asset_selector/market_live_projection.cljs`.
- Indexed patching now validates key/index integrity and deterministically rebuilds index map once when stale/missing.
- Added structural regression assertions:
  - unchanged row identity preserved,
  - stale index fallback rebuilds and patches correct row,
  - selector state-shape expectations updated for `:market-index-by-key`.

Validation evidence:

- `npm run check`: pass.
- `npm test`: pass (`1692` tests, `8852` assertions, `0` failures, `0` errors).
- `npm run test:websocket`: pass (`289` tests, `1651` assertions, `0` failures, `0` errors).

Acceptance status:

- Full-vector `mapv` remap removed from active-asset selector patch hot path: met.
- Selector state ownership includes synchronized `:market-index-by-key`: met.
- Indexed patching regression coverage and fallback safety: met.
- Required validation gates: met.

## Context and Orientation

The active-asset websocket flow is frame-coalesced in `/hyperopen/src/hyperopen/websocket/active_asset_ctx.cljs` through `market-projection-runtime/queue-market-projection!`. During flush, it updates active-asset context and applies selector live patches via `market-live-projection/apply-active-asset-ctx-update`.

Selector data has two canonical synchronized shapes in app state:

- `[:asset-selector :markets]`: ordered vector used for rendering and downstream selectors.
- `[:asset-selector :market-by-key]`: map for key lookup.

This plan adds a third synchronized shape:

- `[:asset-selector :market-index-by-key]`: map from market key to row index in `:markets`.

In this plan, “incremental row patching” means only updating vector indices whose markets changed in the current websocket payload. It explicitly avoids scanning/remapping the full vector each flush.

## Plan of Work

### Milestone 1: Add Selector Key->Index Ownership at State Producers

Add deterministic construction and persistence of `:market-index-by-key` everywhere selector market snapshots are created or restored.

In `/hyperopen/src/hyperopen/api/endpoints/market.cljs`, extend `build-market-state` so it returns `:market-index-by-key` derived from `:markets` ordering. In `/hyperopen/src/hyperopen/api/projections.cljs`, wire `apply-asset-selector-success` to persist that map alongside `:markets` and `:market-by-key`.

In `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs`, update cache restore projection so hydrated selector markets also set `:market-index-by-key`. In `/hyperopen/src/hyperopen/state/app_defaults.cljs`, add an empty default map for this key so cold-start state shape is explicit.

At the end of this milestone, all canonical selector market snapshot writers maintain the new map.

### Milestone 2: Refactor Market Live Projection to Indexed Patching

Refactor `/hyperopen/src/hyperopen/asset_selector/market_live_projection.cljs` so websocket row updates are index-targeted.

The refactor should keep existing patch semantics for numeric parsing and market-type behavior, but replace the full-vector `mapv` pass with logic that:

1. Computes the set of actually changed selector market keys.
2. Resolves row indices through `:market-index-by-key`.
3. Applies only those row updates into a transient/persistent vector copy.
4. Validates index integrity by checking row key at index; if mismatch is detected, rebuild index from current vector once and retry deterministically.
5. Preserves no-op behavior when no candidate keys match.

The function should continue to normalize non-sequential `:markets` input into an empty vector, matching current behavior.

At the end of this milestone, active-asset websocket projection no longer remaps the entire selector vector on each flush.

### Milestone 3: Add Regression Coverage for Indexed Patch Safety

Update and extend tests to lock in the new behavior:

- `/hyperopen/test/hyperopen/asset_selector/market_live_projection_test.cljs`: add assertions that unchanged rows keep identity and only targeted indices are replaced.
- `/hyperopen/test/hyperopen/websocket/active_asset_ctx_test.cljs`: keep coalescing behavior and assert selector row updates still land correctly with index map present.
- `/hyperopen/test/hyperopen/websocket/asset_selector_coverage_test.cljs`: cover missing/stale index-map fallback and non-sequential markets behavior.
- `/hyperopen/test/hyperopen/api/endpoints/market_test.cljs`, `/hyperopen/test/hyperopen/api/projections_test.cljs`, `/hyperopen/test/hyperopen/asset_selector/markets_cache_test.cljs`, and `/hyperopen/test/hyperopen/state/app_defaults_test.cljs`: update expectations for the added selector state key where applicable.

At the end of this milestone, correctness and structural optimization behavior are regression-protected.

### Milestone 4: Run Required Gates and Capture Acceptance Evidence

Run all required gates and record outcomes in this plan:

- `npm run check`
- `npm test`
- `npm run test:websocket`

If any failures occur, resolve them without widening scope beyond selector indexed patching and state-shape propagation.

At the end of this milestone, the refactor is accepted only when all required gates pass.

## Concrete Steps

From `/hyperopen`:

1. Implement selector index map ownership.
   - Edit `/hyperopen/src/hyperopen/api/endpoints/market.cljs` to include `:market-index-by-key` in `build-market-state`.
   - Edit `/hyperopen/src/hyperopen/api/projections.cljs` to project `:market-index-by-key` in `apply-asset-selector-success`.
   - Edit `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs` and `/hyperopen/src/hyperopen/state/app_defaults.cljs` to hydrate/default this key.

2. Refactor websocket selector live patching.
   - Edit `/hyperopen/src/hyperopen/asset_selector/market_live_projection.cljs` to patch changed rows by index with stale-map guard and fallback index rebuild.

3. Update tests.
   - Edit targeted tests listed in Milestone 3 for state-shape and incremental-indexed-patching assertions.

4. Run validation gates.
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

Expected transcript shape:

- All three commands exit successfully with zero failures/errors.
- New tests fail before the refactor and pass after it.

## Validation and Acceptance

Acceptance is met when all conditions below are true:

1. `activeAssetCtx` selector live updates no longer rely on full-vector `mapv` remapping per flush in `/hyperopen/src/hyperopen/asset_selector/market_live_projection.cljs`.
2. Selector state writers keep `:markets`, `:market-by-key`, and `:market-index-by-key` consistent in normal fetch and cache-restore flows.
3. Indexed patching updates only changed row indices and preserves unchanged row identity in regression tests.
4. Missing or stale index-map scenarios recover deterministically without incorrect row mutation.
5. Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

This plan is source-only and safe to re-run. Reapplying the same edits should not create duplicate state keys or unstable behavior.

If indexed patching regresses correctness, recovery is to temporarily route selector vector updates back through the prior full remap path while keeping `:market-index-by-key` ownership and new tests in place. This keeps behavior correct while preserving refactor scaffolding for a follow-up fix.

No schema migration or destructive operation is required.

## Artifacts and Notes

Primary implementation files:

- `/hyperopen/src/hyperopen/asset_selector/market_live_projection.cljs`
- `/hyperopen/src/hyperopen/api/endpoints/market.cljs`
- `/hyperopen/src/hyperopen/api/projections.cljs`
- `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs`
- `/hyperopen/src/hyperopen/state/app_defaults.cljs`

Primary tests expected to change:

- `/hyperopen/test/hyperopen/asset_selector/market_live_projection_test.cljs`
- `/hyperopen/test/hyperopen/websocket/active_asset_ctx_test.cljs`
- `/hyperopen/test/hyperopen/websocket/asset_selector_coverage_test.cljs`
- `/hyperopen/test/hyperopen/api/endpoints/market_test.cljs`
- `/hyperopen/test/hyperopen/api/projections_test.cljs`
- `/hyperopen/test/hyperopen/asset_selector/markets_cache_test.cljs`
- `/hyperopen/test/hyperopen/state/app_defaults_test.cljs`

## Interfaces and Dependencies

Public interfaces that must remain stable:

- `/hyperopen/src/hyperopen/asset_selector/market_live_projection.cljs`: `apply-active-asset-ctx-update` function signature.
- `/hyperopen/src/hyperopen/websocket/active_asset_ctx.cljs`: `create-active-asset-data-handler` projection flow and owner-scoped subscription APIs.

State contract scope for this refactor:

- Add `:market-index-by-key` under `:asset-selector` as an internal state optimization map.
- Keep existing `:markets` ordering semantics and `:market-by-key` lookup semantics unchanged.

No new external library dependencies are required.

Plan revision note: 2026-03-02 14:25Z - Initial ExecPlan created to replace selector full-vector websocket row remap with key->index incremental patching while preserving behavior and required validation gates.
Plan revision note: 2026-03-02 14:39Z - Updated progress/discoveries/decisions/outcomes with implemented source+test changes and successful gate evidence.
