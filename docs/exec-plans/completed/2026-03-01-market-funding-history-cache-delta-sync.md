# Market Funding History Cache and Delta Sync Foundation

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, Hyperopen will have a reusable infrastructure layer for historical market funding rates (coin-level, not user-position funding) that avoids repeatedly pulling full history from Hyperliquid. The system will keep a local rolling 30-day cache per coin and fetch only the missing delta from the most recent cached timestamp to now. This makes future analytics work (signal-to-noise ratio, persistence/autocorrelation, and ranking screens) predictable and lightweight.

A developer will be able to verify this by calling a single cache sync function for a coin twice in a row: the first call fetches network data and writes cache, and the second call returns cached rows without refetching if nothing is missing.

## Progress

- [x] (2026-03-01 15:55Z) Confirmed `/info` `fundingHistory` support in live endpoint and docs/SDK context.
- [x] (2026-03-01 16:03Z) Surveyed current repository funding paths (`userFunding`) and identified missing market-level funding history wrapper.
- [x] (2026-03-01 16:09Z) Reviewed existing local-storage cache and restore patterns to align implementation style.
- [x] (2026-03-01 16:26Z) Implemented market funding-history API wrappers in endpoint/gateway/default/instance layers.
- [x] (2026-03-01 16:36Z) Implemented reusable 30-day local cache + delta sync module with merge, trim, and warm-cache skip behavior.
- [x] (2026-03-01 16:44Z) Added focused tests for endpoint wrapper normalization and cache cold/warm/delta flows.
- [x] (2026-03-01 17:08Z) Passed required validation gates: `npm run check`, `npm test`, `npm run test:websocket`.

## Surprises & Discoveries

- Observation: Existing funding history in app state is account-specific (`userFunding`) and cannot directly support asset-level predictability ranking.
  Evidence: `/hyperopen/src/hyperopen/api/endpoints/account.cljs` builds body `{"type":"userFunding","user":...}`.

- Observation: Hyperopen has robust local-storage cache patterns for selector metadata but no canonical market funding history cache yet.
  Evidence: `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs` persists normalized rows to localStorage and restores on bootstrap.

- Observation: Validation initially failed in this environment because a required npm dependency was not installed locally.
  Evidence: `npm run check` first failed with missing package `@noble/secp256k1`; after `npm install`, all required gates passed.

## Decision Log

- Decision: Introduce a new API wrapper `request-market-funding-history!` rather than overloading existing `request-user-funding-history!`.
  Rationale: The two request types have different semantics and inputs (`coin` vs `user`), and combining them would create ambiguity and accidental misuse.
  Date/Author: 2026-03-01 / Codex

- Decision: Keep the cache infrastructure as an independent reusable module, not wired to a UI route yet.
  Rationale: User asked for infrastructure first; this minimizes integration risk and provides a stable foundation for later feature wiring.
  Date/Author: 2026-03-01 / Codex

- Decision: Normalize and store market funding rows in ascending timestamp order and dedupe by timestamp per coin.
  Rationale: Deterministic ordering simplifies incremental delta fetch (`last-time + 1`) and downstream analytics window slicing.
  Date/Author: 2026-03-01 / Codex

- Decision: Add a refresh cooldown (`cache-min-refresh-interval-ms`) and explicit `:force?` override in the sync API.
  Rationale: Prevents accidental endpoint hammering in hot code paths while still allowing immediate refresh when needed.
  Date/Author: 2026-03-01 / Codex

## Outcomes & Retrospective

Implemented. Hyperopen now has a deterministic, test-backed market funding history data pipeline with 30-day retention and delta-only refresh behavior.

Concrete outcomes:

- Added market endpoint wrapper `request-market-funding-history!` in:
  - `/hyperopen/src/hyperopen/api/endpoints/market.cljs`
  - `/hyperopen/src/hyperopen/api/gateway/market.cljs`
  - `/hyperopen/src/hyperopen/api/default.cljs`
  - `/hyperopen/src/hyperopen/api/instance.cljs`
- Added cache infrastructure module:
  - `/hyperopen/src/hyperopen/funding/history_cache.cljs`
- Added/updated tests:
  - `/hyperopen/test/hyperopen/api/endpoints/market_test.cljs`
  - `/hyperopen/test/hyperopen/api/gateway/market_test.cljs`
  - `/hyperopen/test/hyperopen/api/default_test.cljs`
  - `/hyperopen/test/hyperopen/api/instance_test.cljs`
  - `/hyperopen/test/hyperopen/funding/history_cache_test.cljs`

Validation summary:

- `npm run check` passed (build emits pre-existing warnings in unrelated portfolio benchmark tests).
- `npm test` passed (`1673` tests, `8723` assertions, `0` failures).
- `npm run test:websocket` passed (`289` tests, `1645` assertions, `0` failures).

## Context and Orientation

There are currently two relevant families of modules in this repository.

The first family is API transport wrappers. `src/hyperopen/api/endpoints/market.cljs` is responsible for constructing `POST /info` request bodies and normalizing payloads. `src/hyperopen/api/gateway/market.cljs` exposes endpoint calls through the gateway boundary. `src/hyperopen/api/default.cljs` is the shared default facade used across runtime code. `src/hyperopen/api/instance.cljs` builds per-instance API maps used in tests and service composition.

The second family is local cache infrastructure. `src/hyperopen/asset_selector/markets_cache.cljs` shows canonical localStorage normalization, persistence, and restore patterns. `src/hyperopen/platform.cljs` is the only place this layer should touch browser local storage APIs.

A key domain distinction for this work is: “market funding history” means historical rates by coin from `{"type":"fundingHistory"}`, while “user funding history” means account funding payments from `{"type":"userFunding"}`.

## Plan of Work

First, add a market endpoint wrapper that sends `{"type":"fundingHistory","coin":...,"startTime":...,"endTime":...}` through `POST /info`, normalizes the returned rows to stable keys, and handles payload wrappers consistently. Thread this wrapper through gateway, default facade, and API instance so any runtime component can call it.

Second, add a dedicated funding cache module under `src/hyperopen/funding/` that does three things in one place: normalize rows, merge-and-dedupe by timestamp, and enforce rolling retention of exactly 30 days by trimming old rows. The same module will include a sync function that loads local cache for a coin, computes missing delta window (`last-cached-time + 1` to now, bounded by retention start), optionally skips fetch when recently synced, fetches only missing rows when needed, then persists the updated snapshot.

Third, add focused tests that prove request body correctness, dedupe/trim correctness, and delta sync behavior (initial cold fetch, warm no-op, and incremental fetch). Keep tests deterministic by injecting `now-ms`, storage adapters, and API request function.

## Concrete Steps

From `/hyperopen`:

1. Edit market API wrappers:
   - `/hyperopen/src/hyperopen/api/endpoints/market.cljs`
   - `/hyperopen/src/hyperopen/api/gateway/market.cljs`
   - `/hyperopen/src/hyperopen/api/default.cljs`
   - `/hyperopen/src/hyperopen/api/instance.cljs`

2. Add cache module:
   - `/hyperopen/src/hyperopen/funding/history_cache.cljs`

3. Add or update tests:
   - `/hyperopen/test/hyperopen/api/endpoints/market_test.cljs`
   - `/hyperopen/test/hyperopen/api/gateway/market_test.cljs`
   - `/hyperopen/test/hyperopen/api/default_test.cljs`
   - `/hyperopen/test/hyperopen/api/instance_test.cljs`
   - `/hyperopen/test/hyperopen/funding/history_cache_test.cljs`

4. Run validation commands:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

Expected result is all commands succeeding with no regressions.

## Validation and Acceptance

Acceptance is behavior-based and does not require UI wiring yet.

1. Calling the new API wrapper with coin `BTC` and start time should produce a `fundingHistory` request body and normalized rows.
2. A cold cache sync should fetch from network, persist rows, and return source `:network`.
3. A warm sync with no missing interval should return source `:cache` and perform zero network requests.
4. A sync after newer mocked rows exist should fetch only the delta interval and merge without duplicates.
5. The cache should never contain rows older than 30 days from the effective `now-ms` used in sync.

## Idempotence and Recovery

All changes are additive and idempotent. Re-running sync repeatedly is safe because merging is dedupe-based and retention trimming is deterministic. If local cache payload is malformed, the module will treat it as empty and rebuild from fresh network data within the 30-day window.

## Artifacts and Notes

Expected sync contract shape (example):

    {:coin "BTC"
     :rows [...normalized rows...]
     :source :network|:cache
     :fetched-count 0
     :start-time-ms 1700000000000
     :end-time-ms 1700086400000
     :last-sync-ms 1700086400000}

This contract is intended to make downstream analytics (SNR and autocorrelation windows) independent from transport details.

## Interfaces and Dependencies

Add the following interfaces:

- In `/hyperopen/src/hyperopen/api/endpoints/market.cljs`:
  - `request-market-funding-history!` with signature `(post-info! coin opts) -> Promise<Vec<Row>>`.

- In `/hyperopen/src/hyperopen/api/gateway/market.cljs`:
  - `request-market-funding-history!` passthrough wrapper.

- In `/hyperopen/src/hyperopen/api/default.cljs`:
  - `request-market-funding-history!` public facade wrapper.

- In `/hyperopen/src/hyperopen/api/instance.cljs`:
  - include `:request-market-funding-history!` in generated market request ops map.

- In `/hyperopen/src/hyperopen/funding/history_cache.cljs`:
  - `sync-market-funding-history-cache!` (primary orchestrator with injectable deps)
  - `load-market-funding-history-cache`
  - `persist-market-funding-history-cache!`
  - `clear-market-funding-history-cache!`
  - `rows-for-window` helper for downstream analytics windows.

Revision note (2026-03-01 / Codex): Created initial execution plan from repository and endpoint research to guide implementation of market funding cache infrastructure.
Revision note (2026-03-01 / Codex): Updated the plan to reflect completed implementation, validation outcomes, and final design decisions so the document remains a self-sufficient execution record.
