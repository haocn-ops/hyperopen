# Migrate Market Funding History Cache from localStorage to IndexedDB

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Today market funding history snapshots are persisted in `localStorage` using dynamic keys like `market-funding-history-cache:<coin>`. Each record can hold up to 30 days of per-interval rows. This can create storage pressure and synchronous main-thread write cost as more coins are cached.

After this change, market funding history persistence moves to IndexedDB. Sync behavior stays the same from the caller perspective: first sync can fetch from network, warm sync can return cache, and delta sync still fetches only missing windows. A contributor can verify success by running sync twice and seeing the second read come from IndexedDB-backed cache with no localStorage dependency.

## Progress

- [x] (2026-03-05 15:15Z) Audited current funding cache design in `/hyperopen/src/hyperopen/funding/history_cache.cljs`.
- [x] (2026-03-05 15:15Z) Confirmed funding predictability runtime entrypoint via `/hyperopen/src/hyperopen/runtime/effect_adapters/funding.cljs`.
- [x] (2026-03-05 15:15Z) Reviewed existing async tests in `/hyperopen/test/hyperopen/funding/history_cache_test.cljs` for migration impact.
- [x] (2026-03-05 15:15Z) Authored initial IndexedDB migration plan.
- [x] (2026-03-05 15:18Z) Filed tracking issue in `bd`: `hyperopen-eux.2` under epic `hyperopen-eux`.
- [x] (2026-03-05 19:05Z) Converted funding cache load/persist/clear boundaries to IndexedDB-first Promise-based adapters.
- [x] (2026-03-05 19:05Z) Preserved cold/warm/delta sync semantics while awaiting Promise-returning cache collaborators in `sync-market-funding-history-cache!`.
- [x] (2026-03-05 19:05Z) Added fallback handling and async-adapter coverage in `/hyperopen/test/hyperopen/funding/history_cache_test.cljs`.
- [x] (2026-03-05 19:05Z) Ran `npm run check`, `npm test`, and `npm run test:websocket` after the migration.

## Surprises & Discoveries

- Observation: Cache retention and merge semantics are already deterministic and well isolated.
  Evidence: `/hyperopen/src/hyperopen/funding/history_cache.cljs` has pure helpers for normalize, merge, trim, and delta window calculation.

- Observation: `sync-market-funding-history-cache!` is Promise-based overall but currently assumes synchronous cache load function values.
  Evidence: `load-cache-fn` result is used directly inside `normalize-market-funding-history-cache` without awaiting a Promise.

- Observation: There is currently no code path that clears stale per-coin keys automatically beyond explicit clear function.
  Evidence: `clear-market-funding-history-cache!` exists but is not used by runtime flow.

## Decision Log

- Decision: Preserve cache snapshot schema and coin-key normalization while changing backend transport.
  Rationale: This minimizes risk and keeps predictability math parity.
  Date/Author: 2026-03-05 / Codex

- Decision: Make cache load/persist adapters Promise-aware and await them inside `sync-market-funding-history-cache!`.
  Rationale: IndexedDB is asynchronous by API; hiding async via sync shims would increase complexity and race risk.
  Date/Author: 2026-03-05 / Codex

- Decision: Keep localStorage fallback during transition.
  Rationale: Rollback safety and support for environments where IndexedDB is unavailable or blocked.
  Date/Author: 2026-03-05 / Codex

## Outcomes & Retrospective

Implemented as planned. Funding cache persistence now uses the shared IndexedDB boundary with localStorage fallback, and `sync-market-funding-history-cache!` awaits Promise-based cache adapters without changing freshness, delta, or retention semantics. Validation passed through `npm run check`, `npm test`, and `npm run test:websocket`.

## Context and Orientation

Funding cache flow is centered in `/hyperopen/src/hyperopen/funding/history_cache.cljs`:

- `sync-market-funding-history-cache!` orchestrates load, freshness checks, network fetch, merge, trim, and persist.
- `load-market-funding-history-cache` and `persist-market-funding-history-cache!` are current localStorage boundary calls.
- Runtime caller `/hyperopen/src/hyperopen/runtime/effect_adapters/funding.cljs` uses sync output to compute 30-day predictability summaries.

In this repository, a “snapshot” means a map:

- `:coin`
- `:rows` (ascending by `:time-ms`)
- `:last-row-time-ms`
- `:last-sync-ms`
- `:version`

Migration must keep this snapshot contract stable.

Issue tracking for this plan is in `bd`:

- Epic: `hyperopen-eux`
- Task: `hyperopen-eux.2`

## Plan of Work

### Milestone 1: Add IndexedDB Store for Funding Snapshots

Reuse the shared IndexedDB platform boundary (from the storage migration wave) and add a dedicated object store for funding history snapshots keyed by normalized coin (for example `BTC`, `xyz:GOLD`). Store the snapshot map as JSON-compatible data.

This milestone is complete when get/put/delete for coin snapshots works in tests without touching localStorage.

### Milestone 2: Make Cache APIs Async-Aware

Update `/hyperopen/src/hyperopen/funding/history_cache.cljs` so cache ingress and egress functions can await Promise-based storage adapters. `sync-market-funding-history-cache!` should wrap load and persist calls in Promise chaining and keep all existing branch semantics (`:recent-sync`, `:up-to-date`, `:network`) unchanged.

This milestone is complete when existing sync tests pass with Promise-returning load/persist stubs.

### Milestone 3: Keep Migration Fallback and Error Tolerance

Implement fallback behavior:

- IndexedDB read failure -> fallback load from localStorage.
- IndexedDB write failure -> optional fallback write to localStorage plus warning.
- Invalid or missing cache payload -> treat as empty snapshot (current behavior).

This milestone is complete when failures do not break funding predictability calculations and runtime caller still resolves.

### Milestone 4: Tighten Retention and Optional Cleanup Follow-Up

Keep retention at 30 days exactly as now. Add optional follow-up item to prune inactive coin entries if object store growth becomes large. Do not change retention behavior in this migration unless explicitly required by product.

## Concrete Steps

All commands run from `/hyperopen`.

1. Inspect existing funding cache seams before edits.

    rg -n "load-market-funding-history-cache|persist-market-funding-history-cache|sync-market-funding-history-cache" src/hyperopen/funding/history_cache.cljs src/hyperopen/runtime/effect_adapters/funding.cljs

2. Add IndexedDB-backed cache adapter functions and Promise-aware orchestration.

    rg -n "local-storage-get-fn|local-storage-set-fn|local-storage-remove-fn" src/hyperopen/funding/history_cache.cljs

3. Extend tests for Promise-based adapters and fallback.

    rg -n "sync-market-funding-history-cache" test/hyperopen/funding/history_cache_test.cljs test/hyperopen/runtime/effect_adapters/funding_test.cljs

4. Run required gates.

    npm run check
    npm test
    npm run test:websocket

## Validation and Acceptance

Acceptance is behavior-based:

1. Cold sync still returns `:source :network` and persists normalized rows.
2. Warm sync still returns `:source :cache` with `:reason :recent-sync` when within refresh interval.
3. Delta sync still requests from `last-row-time-ms + 1` and appends unique rows.
4. Storage backend is IndexedDB primary (localStorage used only as migration fallback path).
5. Runtime funding predictability surface remains unchanged for users.
6. Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

Manual verification example:

- Trigger active-asset funding predictability for a coin.
- Confirm IndexedDB funding cache store has coin snapshot row.
- Reload and trigger again within refresh interval; verify cache path is used (no full refetch).

## Idempotence and Recovery

The migration is safe to re-run because snapshot normalization, merge, and trim are deterministic. If IndexedDB APIs fail in a browser session, fallback path keeps behavior available.

Rollback strategy:

- Keep a single backend selection seam in funding cache module.
- If regressions appear, temporarily route load/persist back to localStorage while retaining async-compatible orchestration code.

## Artifacts and Notes

Suggested IndexedDB object store row shape:

    {:coin "BTC"
     :version 1
     :saved-at-ms <epoch-ms>
     :snapshot {:version 1
                :coin "BTC"
                :rows [...]
                :last-row-time-ms 1700003600000
                :last-sync-ms 1700007200000}}

The nested `:snapshot` preserves existing in-memory contract and avoids broad downstream changes.

## Interfaces and Dependencies

Define (or equivalent) functions by the end:

- In `/hyperopen/src/hyperopen/funding/history_cache.cljs`:

    load-market-funding-history-cache! :: coin [opts] -> js/Promise
    persist-market-funding-history-cache! :: coin snapshot [opts] -> js/Promise
    clear-market-funding-history-cache! :: coin [opts] -> js/Promise
    sync-market-funding-history-cache! :: coin [opts] -> js/Promise

- Adapter options for `sync-market-funding-history-cache!` must support Promise-returning collaborators:

    :load-cache-fn
    :persist-cache-fn
    :request-market-funding-history!

These collaborators must remain injectable for deterministic tests.

Revision note (2026-03-05 / Codex): Initial plan drafted from current funding cache architecture to migrate per-coin persistence to IndexedDB while preserving sync semantics.
