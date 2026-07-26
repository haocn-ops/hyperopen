# WS-First Startup Account Bootstrap with Bounded REST Backfill

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, account startup bootstrap will avoid immediate high-churn `/info` POST fanout for data that already has websocket subscription parity. Instead, startup will subscribe first, then only issue bounded delayed REST backfill when matching websocket streams are not live. This reduces launch-time request spikes that contribute to rate limits while preserving deterministic recovery when streams are stale or unavailable.

You can verify this by running startup/runtime tests that assert startup skips stream-covered REST calls when websocket health is live and issues delayed fallback only when stream health is not live.

## Progress

- [x] (2026-03-05 03:14Z) Claimed `hyperopen-nhv.4` in `bd` for startup WS-first migration.
- [x] (2026-03-05 03:16Z) Audited startup bootstrap flow in `/hyperopen/src/hyperopen/startup/runtime.cljs` and supporting collaborators/tests to locate launch fanout seams.
- [x] (2026-03-05 03:18Z) Authored this ExecPlan before implementation.
- [x] (2026-03-05 03:33Z) Implemented WS-first startup fallback scheduler and topic-live helpers in `/hyperopen/src/hyperopen/startup/runtime.cljs`; stage A stream-covered fetches now use delayed fallback and stage-B per-dex open-order fetches now skip while `openOrders` stream is live.
- [x] (2026-03-05 03:36Z) Added startup stream backfill config wiring (`/hyperopen/src/hyperopen/config.cljs`, `/hyperopen/src/hyperopen/runtime/state.cljs`, `/hyperopen/src/hyperopen/app/startup.cljs`).
- [x] (2026-03-05 03:41Z) Expanded startup/runtime regression coverage in `/hyperopen/test/hyperopen/startup/runtime_test.cljs` and adjusted bootstrap integration coverage in `/hyperopen/test/hyperopen/core_bootstrap/runtime_startup_test.cljs`.
- [x] (2026-03-05 03:43Z) Validation complete for websocket suite (`304 tests, 1741 assertions, 0 failures`); `npm run check` remains blocked by missing `@noble/secp256k1`.
- [x] (2026-03-05 03:45Z) Updated `bd` issue `hyperopen-nhv.4` notes with implementation details and validation evidence.

## Surprises & Discoveries

- Observation: Startup account bootstrap currently issues stage-A high-priority REST calls immediately for open orders, user fills, and funding history without checking websocket stream health.
  Evidence: `/hyperopen/src/hyperopen/startup/runtime.cljs` function `bootstrap-account-data!` calls `fetch-frontend-open-orders!`, `fetch-user-fills!`, and `fetch-and-merge-funding-history!` unconditionally in stage A.

- Observation: Per-dex stage-B bootstrap currently issues staggered open-order and clearinghouse fetches for every dex, even though open orders are already covered by the `openOrders` stream.
  Evidence: `/hyperopen/src/hyperopen/startup/runtime.cljs` function `stage-b-account-bootstrap!` unconditionally calls `fetch-frontend-open-orders!` and `fetch-clearinghouse-state!` for each normalized dex.

- Observation: Address change handlers subscribe user streams before invoking bootstrap, but bootstrap still runs in the same address change turn and therefore can race stream-live readiness.
  Evidence: `/hyperopen/src/hyperopen/startup/runtime.cljs` `install-address-handlers!` adds `create-user-handler` first and startup bootstrap handler second; `/hyperopen/src/hyperopen/wallet/address_watcher.cljs` notifies handlers sequentially on address change.

- Observation: Delayed fallback callbacks now include active-address stale guards, so tests that bootstrap a synthetic address without updating `[:wallet :address]` no longer execute stream-backed fallbacks.
  Evidence: `bootstrap-account-data-covers-nil-repeat-success-and-error-branches-test` required updating wallet address before third bootstrap to assert fallback calls.

## Decision Log

- Decision: Treat stream-covered startup fetches as delayed fallback work instead of immediate stage-A work.
  Rationale: This preserves recovery for degraded websocket conditions while reducing launch burst pressure when stream snapshots become live quickly.
  Date/Author: 2026-03-05 / Codex

- Decision: In this milestone, apply WS-first startup gating to `openOrders`, `userFills`, and `userFundings`-backed startup fetches and per-dex open-orders fanout, while keeping non-subscribable or non-parity startup fetches unchanged.
  Rationale: This delivers meaningful request reduction with lower risk than broader bootstrap rewrites in one slice.
  Date/Author: 2026-03-05 / Codex

- Decision: Configure startup stream fallback delay via startup config (`:startup :stream-backfill-delay-ms`) and pass it through runtime state/system startup deps.
  Rationale: This keeps rollout tuning centralized and avoids hard-coding migration tuning in runtime logic.
  Date/Author: 2026-03-05 / Codex

## Outcomes & Retrospective

`hyperopen-nhv.4` implementation slice is complete for WS-first startup bootstrap gating.

Delivered behavior:

- Stage-A startup fetches with websocket parity now run as delayed bounded fallback:
  - `openOrders` (`fetch-frontend-open-orders!`)
  - `userFills` (`fetch-user-fills!`)
  - `userFundings` (`fetch-and-merge-funding-history!`)
- Stage-B per-dex open-order fanout now skips while the `openOrders` stream is live for the active address.
- Fallback callbacks now re-check active address and stream liveness before executing.

Unchanged behavior in this slice:

- Non-parity startup fetches remain immediate (`spot`, `user abstraction`, `portfolio`, `user fees`, per-dex clearinghouse).

Validation summary:

- Websocket suite passed after changes: `Ran 304 tests containing 1741 assertions. 0 failures, 0 errors.`
- Required `npm run check` remains environment-blocked by missing npm package `@noble/secp256k1` during `shadow-cljs compile app`.

## Context and Orientation

Startup account bootstrap is defined in `/hyperopen/src/hyperopen/startup/runtime.cljs`.

- `bootstrap-account-data!` is the entry point for account-scoped bootstrap when the effective address becomes available.
- Stage A currently resets account slices and issues immediate high-priority fetches.
- Stage B currently fetches per-dex account snapshots with staggered low-priority requests.

Websocket stream health is projected in store state under `[:websocket :health]` and already used by order/user refresh fanout gating via `hyperopen.websocket.health-projection/topic-stream-live?`.

In this plan, “stream-covered startup fetches” means startup fetches that have websocket parity for current-user snapshots:

- `openOrders` stream for open orders,
- `userFills` stream for fills,
- `userFundings` stream for funding rows.

“Bounded REST backfill” means we keep exactly one delayed fallback fetch per stream-covered surface when websocket health is not live, instead of unconditional immediate calls.

## Plan of Work

### Milestone 1: Add startup WS-health helpers and fallback scheduling

Extend `/hyperopen/src/hyperopen/startup/runtime.cljs` with pure helper functions that determine topic liveness for an address using projected websocket health, and with a small delayed fallback scheduler for stream-covered startup fetches.

Behavior target:

- If stream is already live for the active address, skip startup REST fetch for that surface.
- If stream is not live, schedule one delayed fallback and re-check stream liveness before executing the fetch.

### Milestone 2: Apply WS-first gating to startup stage A and stage B

Update `bootstrap-account-data!` so stream-covered startup fetches (`openOrders`, `userFills`, funding history) use delayed fallback scheduling instead of unconditional immediate calls.

Update `stage-b-account-bootstrap!` to skip per-dex open-order refreshes when `openOrders` stream is live for the active address at execution time.

Keep non-parity startup fetches (spot balances, user abstraction, portfolio, fees, per-dex clearinghouse) unchanged in this slice.

### Milestone 3: Add regression tests and validate

Update tests in:

- `/hyperopen/test/hyperopen/startup/runtime_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap/runtime_startup_test.cljs`

Add assertions for:

- stream-live startup skip behavior,
- delayed fallback execution when stream is not live,
- per-dex open-orders fanout suppression when stream is live.

Then run websocket suite and capture results.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/startup/runtime.cljs`.
2. Edit `/hyperopen/test/hyperopen/startup/runtime_test.cljs`.
3. Edit `/hyperopen/test/hyperopen/core_bootstrap/runtime_startup_test.cljs`.
4. Run:
   - `npx shadow-cljs compile ws-test && node out/ws-test.js`
   - `npm run check` (best effort, record blocker if unchanged)

## Validation and Acceptance

Acceptance criteria:

1. Startup bootstrap does not immediately fetch open-orders/fills/fundings when corresponding websocket topic is live for active address.
2. Startup bootstrap schedules and executes fallback REST fetches when topic is not live after delay.
3. Stage-B per-dex open-order fanout is suppressed when `openOrders` stream is live.
4. Existing startup behavior for non-parity surfaces remains intact.
5. Updated tests pass in websocket suite.

## Idempotence and Recovery

Changes are additive and guarded by state-driven health checks. Re-running startup sequences is safe because bootstrap already guards by `:bootstrapped-address`, and fallback fetches re-check active address and stream liveness before execution.

## Artifacts and Notes

Key command evidence:

- `npx shadow-cljs compile ws-test && node out/ws-test.js`
  - Output: `Ran 304 tests containing 1741 assertions. 0 failures, 0 errors.`
- `npm run check`
  - Blocker: `The required JS dependency "@noble/secp256k1" is not available` during `shadow-cljs compile app`.

## Interfaces and Dependencies

No public API signatures are removed.

Primary internal interfaces touched:

- `/hyperopen/src/hyperopen/startup/runtime.cljs`
  - `bootstrap-account-data!`
  - `stage-b-account-bootstrap!`
- `/hyperopen/src/hyperopen/websocket/health_projection.cljs`
  - `topic-stream-live?` (consumer only)

Revision note (2026-03-05): Updated after implementing startup WS-first fallback gating, adding config/runtime wiring, expanding regression tests, and recording validation outcomes/blockers.
