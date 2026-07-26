# Per-Dex Clearinghouse WS Subscription Hotfix

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, high-churn `/info` POST fanout for `{"type":"clearinghouseState","user", "dex"}` should be reduced by using websocket `clearinghouseState` subscriptions for each active user + perp-dex combination. The user-visible outcome is lower request pressure and fewer chances of rate limiting while Ghost Mode or active accounts receive frequent fill/ledger updates.

You can verify this by enabling Ghost Mode for an active address, resetting request runtime stats, waiting 45-60 seconds, and observing that `clearinghouseState` no longer dominates the request mix when matching websocket streams are subscribed and usable.

## Progress

- [x] (2026-03-05 15:49Z) Created and claimed bug `hyperopen-6x3` from QA evidence (`discovered-from:hyperopen-918`).
- [x] (2026-03-05 15:52Z) Confirmed websocket docs expose `clearinghouseState` subscriptions with `user` + `dex` fields.
- [x] (2026-03-05 15:54Z) Confirmed current repo behavior: user websocket module subscribes only `openOrders/userFills/userFundings/userNonFundingLedgerUpdates`; per-dex clearinghouse still refreshes through REST fanout.
- [x] (2026-03-05 16:01Z) Implemented websocket per-dex clearinghouse subscription lifecycle and inbound handler in `hyperopen.websocket.user`.
- [x] (2026-03-05 16:05Z) Added per-dex REST fallback gating in user refresh and startup stage-B for stream-covered/snapshot-ready paths.
- [x] (2026-03-05 16:06Z) Hardened `request-clearinghouse-state!` with dedupe + TTL defaults (`:clearinghouse-state` policy).
- [x] (2026-03-05 16:08Z) Added/updated websocket, startup, health-projection, and account-endpoint regression tests.
- [x] (2026-03-05 16:10Z) Required validation gates passed: `npm run test:websocket`, `npm test`, `npm run check`.
- [x] (2026-03-05 16:15Z) Manual Ghost Mode QA (address `0x162cc7c861ebd0c06b3d72319201150482518185`) captured repeated post-reset samples with `clearinghouseState=0` and no per-dex refresh fanout.
- [ ] Update Beads notes and close `hyperopen-6x3` if validated.

## Surprises & Discoveries

- Observation: Existing migration work intentionally left per-dex clearinghouse as REST backstop.
  Evidence: `/hyperopen/docs/qa/info-post-hotspot-baseline-2026-03-05.md` notes “per-dex/spot remain bounded REST backstop.”

- Observation: Current user websocket subscriptions do not include `clearinghouseState` and no handler is registered for channel `"clearinghouseState"`.
  Evidence: `/hyperopen/src/hyperopen/websocket/user.cljs` `subscribe-user!` + `init!`.

## Decision Log

- Decision: Implement per-dex clearinghouse stream ownership inside `hyperopen.websocket.user` rather than creating a new websocket module in this hotfix.
  Rationale: This keeps behavior close to the existing post-fill refresh logic and minimizes wiring/risk for a targeted bug fix.
  Date/Author: 2026-03-05 / Codex

- Decision: Keep bounded REST fallback paths, but add stream-usability gating and request-policy dedupe/TTL at endpoint boundary.
  Rationale: This preserves recovery behavior when streams are unavailable while reducing redundant burst calls.
  Date/Author: 2026-03-05 / Codex

## Outcomes & Retrospective

Implemented per-dex websocket subscription ownership for `clearinghouseState`, startup/user-refresh stream gating, and fallback request hardening. Required gates all passed on this branch:

- `npm run test:websocket` -> 332 tests / 1829 assertions / 0 failures
- `npm test` -> 1911 tests / 9826 assertions / 0 failures
- `npm run check` -> pass (lint + app/test/worker compile)

Manual QA (branch-local build on `http://localhost:8082`) in Ghost Mode with `0x162cc7c861ebd0c06b3d72319201150482518185`:

- Sample 1 (60s post-reset): `startedTotal=26`, `clearinghouseState=0`, `spotClearinghouseState=16`, `userFunding=10`.
- Sample 2 (50s post-reset): `startedTotal=7`, `clearinghouseState=0`, `spotClearinghouseState=7`.
- Websocket health showed per-dex `clearinghouseState` streams registered for `hyna/vntl/km/abcd/flx/xyz/cash` (status `idle`, `messageCount=0` during sample windows).

Net result: dominant repeated per-dex `clearinghouseState` POST fanout observed pre-fix is suppressed in sampled Ghost Mode operation while retaining bounded fallback behavior.

## Context and Orientation

`/hyperopen/src/hyperopen/websocket/user.cljs` currently schedules account-surface refreshes after `userFills` and `userNonFundingLedgerUpdates`. In that refresh, it calls `request-clearinghouse-state!` for each perp dex returned by metadata (`ensure-and-apply-perp-dex-metadata!`), causing repeated `/info` POST fanout under active event flow.

`/hyperopen/src/hyperopen/startup/runtime.cljs` stage-B bootstrap also loops through each perp dex and calls `fetch-clearinghouse-state!` with low priority. It already gates per-dex open-orders by websocket health, but not per-dex clearinghouse.

`/hyperopen/src/hyperopen/api/endpoints/account.cljs` `request-clearinghouse-state!` currently lacks request-policy dedupe/TTL defaults, unlike several hardened endpoints. This means fallback calls are less protected from repetitive invocation.

`/hyperopen/src/hyperopen/websocket/health_projection.cljs` already supports topic selectors with `:user` and `:dex`, so stream usability gating can be precise once subscriptions exist.

## Plan of Work

First, extend `hyperopen.websocket.user` to manage an additional subscription family for `clearinghouseState` keyed by address+dex. Add helper functions to normalize dex names, sync desired subscriptions for the active address, and unsubscribe stale address+dex keys on address change. Register a new inbound websocket handler for `"clearinghouseState"` that applies payloads into `:perp-dex-clearinghouse` through existing API projections.

Second, in user post-fill refresh, after resolving perp dex names, sync clearinghouse subscriptions and only invoke REST per-dex clearinghouse fallback when the corresponding websocket stream is not usable for `{user,dex}`.

Third, update startup stage-B to skip per-dex clearinghouse REST fetches when websocket stream coverage exists for `{user,dex}` (while preserving current behavior if streams are not usable).

Fourth, harden `request-clearinghouse-state!` in account endpoints by applying request-policy TTL + dedupe defaults keyed by normalized `{address,dex}` and allowing explicit override.

Finally, update tests and run required gates, then perform manual browser QA in Ghost Mode and attach evidence to `hyperopen-6x3`.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/websocket/user.cljs` to add per-dex clearinghouse subscription sync + handler + gating.
2. Edit `/hyperopen/src/hyperopen/startup/runtime.cljs` to gate stage-B per-dex clearinghouse fallback by topic usability.
3. Edit `/hyperopen/src/hyperopen/api/request_policy.cljs` and `/hyperopen/src/hyperopen/api/endpoints/account.cljs` for clearinghouse request policy defaults.
4. Update tests:
   - `/hyperopen/test/hyperopen/websocket/user_test.cljs`
   - `/hyperopen/test/hyperopen/startup/runtime_test.cljs`
   - `/hyperopen/test/hyperopen/api/endpoints/account_test.cljs`
5. Run required gates:
   - `npm run test:websocket`
   - `npm test`
   - `npm run check`
6. Manual QA:
   - Start app (`npm run dev`) and activate Ghost Mode with `0x162cc7c861ebd0c06b3d72319201150482518185`.
   - Reset request runtime stats (`hyperopen.api.default$.reset_request_runtime_BANG_()`).
   - Sample for 45-60 seconds.
   - Read stats (`cljs.core.clj__GT_js(hyperopen.api.default$.get_request_stats())`) and confirm reduced `clearinghouseState` cadence under active WS streams.

## Validation and Acceptance

Acceptance criteria:

1. Websocket runtime subscribes to per-dex `clearinghouseState` for active address/dex pairs and applies inbound payloads to `:perp-dex-clearinghouse`.
2. User refresh fanout no longer calls per-dex clearinghouse REST when matching stream is usable (`:live` or `:n-a`).
3. Startup stage-B avoids per-dex clearinghouse REST fetches when matching stream is usable.
4. Endpoint fallback calls use dedupe+TTL policy by default for `clearinghouseState`.
5. Required gates pass.
6. Manual QA with Ghost Mode shows materially lower `clearinghouseState` request volume relative to current baseline pattern.

## Idempotence and Recovery

All changes are additive and safe to re-run. If websocket stream reliability regresses, fallback remains available because gating only suppresses REST when stream is usable. If needed during troubleshooting, callers can force refresh with explicit options while preserving endpoint policy defaults.

## Artifacts and Notes

- Manual QA browser session: `sess-1772727019934-e17295`
- Validation command transcripts captured in shell history for:
  - `npm run test:websocket`
  - `npm test`
  - `npm run check`

## Interfaces and Dependencies

No new external dependencies.

Interfaces changed in this plan:

- `/hyperopen/src/hyperopen/websocket/user.cljs`
  - Add per-dex clearinghouse subscription sync helper(s).
  - Register `"clearinghouseState"` handler.
  - Gate per-dex REST fallback by `{user,dex}` stream usability.

- `/hyperopen/src/hyperopen/startup/runtime.cljs`
  - Add `{user,dex}` stream usability helper and stage-B gating for per-dex clearinghouse fallback.

- `/hyperopen/src/hyperopen/api/request_policy.cljs`
  - Add default TTL for `:clearinghouse-state`.

- `/hyperopen/src/hyperopen/api/endpoints/account.cljs`
  - Apply request policy + dedupe key defaults in `request-clearinghouse-state!`.

Revision note (2026-03-05): Initial ExecPlan created after manual QA evidence showed `clearinghouseState` as dominant remaining high-churn `/info` request path.
