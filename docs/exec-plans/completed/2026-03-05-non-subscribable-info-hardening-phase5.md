# Non-Subscribable `/info` Hardening (TTL, Dedupe, Route Gating)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

This slice reduces background `/info` POST pressure for endpoint types that do not have direct websocket parity yet. After this change, repeated calls for portfolio/fees/history/metadata/vault detail surfaces are bounded by endpoint-specific TTL cache policy and in-flight coalescing, and route/tab inactive effects skip network calls instead of polling in the background.

You can verify this by running websocket tests and observing that endpoint option payloads now include cache TTL policy fields, info-client cache behavior is covered by tests, and inactive route/tab effects short-circuit without firing requests.

## Progress

- [x] (2026-03-05 04:10Z) Claimed and resumed `hyperopen-nhv.5`; audited `/info` call paths and existing dedupe seams.
- [x] (2026-03-05 04:18Z) Added central request policy module at `/hyperopen/src/hyperopen/api/request_policy.cljs` with endpoint-type TTL defaults.
- [x] (2026-03-05 04:21Z) Extended `/hyperopen/src/hyperopen/api/info_client.cljs` with TTL response cache keyed by `:cache-key`/`:dedupe-key`, `:force-refresh?` bypass, and cache reset integration.
- [x] (2026-03-05 04:26Z) Wired non-subscribable endpoint defaults (TTL + dedupe) across account/market/orders/vault endpoint modules.
- [x] (2026-03-05 04:31Z) Implemented route/tab gating for inactive surfaces in `/hyperopen/src/hyperopen/account/history/actions.cljs`, `/hyperopen/src/hyperopen/account/history/effects.cljs`, `/hyperopen/src/hyperopen/funding_comparison/effects.cljs`, and `/hyperopen/src/hyperopen/vaults/effects.cljs`.
- [x] (2026-03-05 04:40Z) Added/updated regression coverage in endpoint/effect/api tests.
- [x] (2026-03-05 04:45Z) Validation complete for websocket test suite (`315 tests, 1768 assertions, 0 failures`).
- [ ] Run full quality gates in environment with `shadow-cljs` binary on PATH and `@noble/secp256k1` installed.
- [ ] Update `bd` notes and close `hyperopen-nhv.5` after commit.

## Surprises & Discoveries

- Observation: Existing in-flight coalescing was already present in `info_client`, but only when callers passed `:dedupe-key`; cache-key-only callers were not deduped.
  Evidence: Prior logic in `/hyperopen/src/hyperopen/api/info_client.cljs` used `with-single-flight!` exclusively with `:dedupe-key`.

- Observation: `refresh-order-history` always dispatched a fetch effect even when order history tab was inactive, causing avoidable historical-order refresh traffic after mutations.
  Evidence: `/hyperopen/src/hyperopen/account/history/actions.cljs` previously emitted `:effects/api-fetch-historical-orders` unconditionally.

- Observation: `npm run test:websocket` and `npm test` fail in this environment because `shadow-cljs` is not on PATH, while `npx shadow-cljs ...` works.
  Evidence: shell output: `sh: shadow-cljs: command not found` from npm scripts.

- Observation: `npm run check` still fails at app compile due missing `@noble/secp256k1` (pre-existing environment blocker).
  Evidence: `The required JS dependency "@noble/secp256k1" is not available` during `npx shadow-cljs compile app`.

## Decision Log

- Decision: Introduce a dedicated policy module (`/hyperopen/src/hyperopen/api/request_policy.cljs`) for REST fallback TTL defaults by endpoint kind.
  Rationale: Keeps endpoint aging policy centralized and auditable, instead of scattering magic TTL numbers across endpoint files.
  Date/Author: 2026-03-05 / Codex

- Decision: Add response cache behavior to `info_client` rather than service wrappers.
  Rationale: Single place for all `/info` callers guarantees consistent dedupe/cache semantics and keeps API façade stable.
  Date/Author: 2026-03-05 / Codex

- Decision: Gate account-history fetches by active tab and vault/funding-comparison fetches by active route.
  Rationale: Preventing inactive-surface requests directly addresses the rate-limit pressure from background refresh fanout.
  Date/Author: 2026-03-05 / Codex

## Outcomes & Retrospective

`hyperopen-nhv.5` implementation is complete in code and websocket tests.

Delivered outcomes:

- New centralized TTL policy map for non-subscribable `/info` endpoint families.
- `info_client` now supports bounded response caching and `:force-refresh?` bypass.
- Endpoint defaults now consistently supply dedupe+TTL for unsupported subscription surfaces.
- Route/tab gating suppresses inactive background fetches for account history, funding comparison, and vault views.
- Tests cover cache semantics, endpoint policy option shaping, and inactive-route/tab skip behavior.

Remaining gap is environment-only: full `npm run check` cannot complete until missing JS dependency is installed.

## Context and Orientation

Relevant runtime layers:

- `/hyperopen/src/hyperopen/api/info_client.cljs` is the low-level `/info` POST queue, retry, and single-flight boundary.
- `/hyperopen/src/hyperopen/api/endpoints/*.cljs` build request bodies and per-endpoint request options.
- `/hyperopen/src/hyperopen/account/history/actions.cljs` and `/hyperopen/src/hyperopen/account/history/effects.cljs` drive account-history surface fetches.
- `/hyperopen/src/hyperopen/funding_comparison/effects.cljs` and `/hyperopen/src/hyperopen/vaults/effects.cljs` execute route-driven page fetches.

The migration goal in this phase is not to eliminate REST entirely; it is to make unsupported paths bounded and deduped while websocket migration continues.

## Plan of Work

Implement a central request policy function that attaches endpoint TTL defaults. Extend the info client to read/write cache entries by stable keys and to dedupe concurrent requests by dedupe-key or cache-key. Apply the policy in non-subscribable endpoint request builders so callers inherit TTL behavior without changing external API signatures. Then gate effect execution in route/tab inactive paths to avoid unnecessary requests.

Add regression coverage in endpoint tests, account-history/vault/funding-comparison effect tests, and API-level info-client tests to prove caching and skip behavior.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/api/info_client.cljs` and add TTL response cache flow.
2. Add `/hyperopen/src/hyperopen/api/request_policy.cljs` and wire policy into endpoint request builders.
3. Edit route/tab gating in:
   - `/hyperopen/src/hyperopen/account/history/actions.cljs`
   - `/hyperopen/src/hyperopen/account/history/effects.cljs`
   - `/hyperopen/src/hyperopen/funding_comparison/effects.cljs`
   - `/hyperopen/src/hyperopen/vaults/effects.cljs`
4. Update tests:
   - `/hyperopen/test/hyperopen/api_test.cljs`
   - `/hyperopen/test/hyperopen/api/endpoints/account_test.cljs`
   - `/hyperopen/test/hyperopen/api/endpoints/market_test.cljs`
   - `/hyperopen/test/hyperopen/api/endpoints/orders_test.cljs`
   - `/hyperopen/test/hyperopen/api/endpoints/vaults_test.cljs`
   - `/hyperopen/test/hyperopen/account/history/effects_test.cljs`
   - `/hyperopen/test/hyperopen/core_bootstrap/account_history_actions_test.cljs`
   - `/hyperopen/test/hyperopen/funding_comparison/effects_test.cljs`
   - `/hyperopen/test/hyperopen/vaults/effects_test.cljs`
5. Run validation:
   - `npx shadow-cljs compile ws-test && node out/ws-test.js`
   - `npm run test:websocket` (environment currently missing `shadow-cljs` on PATH)
   - `npm test` (same PATH blocker)
   - `npm run check` (app compile blocked by missing `@noble/secp256k1`)

## Validation and Acceptance

Acceptance criteria:

1. Non-subscribable `/info` endpoint families default to explicit TTL policy in request opts.
2. `info_client` serves fresh cached responses within TTL and supports explicit refresh bypass.
3. Concurrent identical requests are coalesced via dedupe/cache key.
4. Inactive tab/route effects skip network execution.
5. Websocket test suite remains green.

Observed results in this environment:

- `npx shadow-cljs compile ws-test && node out/ws-test.js` passed (`315 tests containing 1768 assertions. 0 failures, 0 errors.`)
- `npm run test:websocket` failed due missing PATH binary (`shadow-cljs: command not found`)
- `npm test` failed due same PATH binary issue
- `npm run check` failed at app compile due missing `@noble/secp256k1`

## Idempotence and Recovery

Changes are additive and safe to rerun. TTL policy can be overridden per-call with explicit `:cache-ttl-ms` (including disabling by omitting or non-positive value) and cache bypass is available with `:force-refresh? true`. Route gating can be bypassed in specific calls using `:skip-route-gate? true` for controlled fallback scenarios.

## Artifacts and Notes

Key command transcript:

    npx shadow-cljs compile ws-test && node out/ws-test.js
    Ran 315 tests containing 1768 assertions.
    0 failures, 0 errors.

Blocked command transcripts:

    npm run test:websocket
    sh: shadow-cljs: command not found

    npm run check
    The required JS dependency "@noble/secp256k1" is not available

## Interfaces and Dependencies

New internal module:

- `/hyperopen/src/hyperopen/api/request_policy.cljs`
  - `default-info-request-ttl-ms`
  - `default-ttl-ms`
  - `normalize-ttl-ms`
  - `apply-info-request-policy`

Extended existing interface behavior (no signature break):

- `/hyperopen/src/hyperopen/api/info_client.cljs`
  - `request-info!` now honors `:cache-key`, `:cache-ttl-ms`, and `:force-refresh?` options.

Revision note (2026-03-05): Initial phase-5 ExecPlan authored after implementing and validating TTL/dedupe/gating behavior to preserve handoff context for next slices.
