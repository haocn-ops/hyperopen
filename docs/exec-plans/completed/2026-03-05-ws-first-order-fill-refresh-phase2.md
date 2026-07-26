# WS-First Gating for Post-Order and Post-Fill Account Surface Refresh

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, order submit/cancel and user fill handlers will stop issuing redundant `/info` snapshot requests when equivalent websocket streams are already live and subscribed for the active user. This reduces burst load that contributes to rate limiting while preserving fallback fetches when websocket coverage is stale or unavailable.

You will be able to verify this by running order and websocket tests that assert refresh fanout is skipped under healthy stream conditions and preserved when websocket health is not live.

## Progress

- [x] (2026-03-05 03:07Z) Claimed `hyperopen-nhv.2` in `bd`.
- [x] (2026-03-05 03:10Z) Audited `/hyperopen/src/hyperopen/order/effects.cljs` and `/hyperopen/src/hyperopen/websocket/user.cljs` fanout paths and corresponding tests.
- [x] (2026-03-05 03:11Z) Authored this ExecPlan for the first WS-first migration slice.
- [x] (2026-03-05 03:18Z) Implemented stream-health selector helper in `/hyperopen/src/hyperopen/websocket/health_projection.cljs` (`find-live-topic-stream`, `topic-stream-live?`) with case-insensitive address matching and transport-live gating.
- [x] (2026-03-05 03:20Z) Applied WS-first gating to order mutation and user fill refresh fanout in `/hyperopen/src/hyperopen/order/effects.cljs` and `/hyperopen/src/hyperopen/websocket/user.cljs`.
- [x] (2026-03-05 03:22Z) Added regression tests in `/hyperopen/test/hyperopen/websocket/health_projection_test.cljs`, `/hyperopen/test/hyperopen/core_bootstrap/order_effects_test.cljs`, and `/hyperopen/test/hyperopen/websocket/user_test.cljs`.
- [ ] (2026-03-05 03:24Z) Validation partially complete: websocket suite passes; full `npm run check` remains blocked by missing `@noble/secp256k1` dependency in this environment.
- [ ] Update `bd` notes with this milestone outcome.

## Surprises & Discoveries

- Observation: The user websocket module already subscribes to `openOrders` and updates `[:orders :open-orders]` directly, but still triggers post-fill REST refresh fanout for open orders and clearinghouse snapshots.
  Evidence: `/hyperopen/src/hyperopen/websocket/user.cljs` `open-orders-handler` updates state from channel messages while `schedule-account-surface-refresh-after-fill!` calls `refresh-account-surfaces-after-user-fill!` which performs REST fanout.

- Observation: Existing order mutation tests mostly mock API request functions and do not include websocket health state, so default behavior can remain unchanged unless explicit health fixtures are added.
  Evidence: `/hyperopen/test/hyperopen/core_bootstrap/order_effects_test.cljs` stubs `api/request-frontend-open-orders!` and `api/request-clearinghouse-state!` without websocket health setup.

- Observation: Gating on store-projected websocket health required transport freshness checks in addition to stream status; stream-level `:live` alone is insufficient if transport is disconnected.
  Evidence: `topic-stream-live-requires-live-transport-and-unique-live-match-test` in `/hyperopen/test/hyperopen/websocket/health_projection_test.cljs`.

- Observation: `npm run check` still fails due missing npm dependency unrelated to this change (`@noble/secp256k1`), so milestone validation relies on websocket suite evidence.
  Evidence: `npm run check` failure during `npx shadow-cljs compile app` with module resolution error in `hyperopen/wallet/agent_session.cljs`.

## Decision Log

- Decision: Use store-projected websocket health (`[:websocket :health]`) to decide WS-first gating, rather than querying websocket client runtime directly.
  Rationale: This keeps decisions deterministic and state-driven, aligns with runtime projection architecture, and simplifies tests.
  Date/Author: 2026-03-05 / Codex

- Decision: First migration slice will skip redundant `frontendOpenOrders` and default `clearinghouseState` refresh calls when matching `openOrders` / `webData2` streams are live for the active user, while retaining spot and per-dex backstop refreshes.
  Rationale: This delivers immediate request reduction with lower regression risk than removing all refresh paths at once.
  Date/Author: 2026-03-05 / Codex

## Outcomes & Retrospective

First WS-first gating slice is implemented and verified in websocket tests.

What now works:

- Shared helper determines whether a topic stream is live for a selector from projected health (`topic-stream-live?`).
- Order mutation refresh now skips:
  - `frontendOpenOrders` snapshot calls when `openOrders` stream is live for user.
  - default `clearinghouseState` snapshot call when `webData2` stream is live for user.
- User fill/ledger refresh path now applies the same skip rules.
- Spot and per-dex fallback refreshes remain in place.

Validation:

- `npx shadow-cljs compile ws-test && node out/ws-test.js` passed (`Ran 301 tests containing 1727 assertions. 0 failures, 0 errors.`).
- `npm run check` remains blocked by missing dependency (`@noble/secp256k1`) in this environment.

## Context and Orientation

Two modules currently emit high-churn REST refresh fanout after mutable events:

- `/hyperopen/src/hyperopen/order/effects.cljs`: `refresh-account-surfaces-after-order-mutation!`
- `/hyperopen/src/hyperopen/websocket/user.cljs`: `refresh-account-surfaces-after-user-fill!` via debounced scheduling

Both call `/info` endpoint operations for open orders and clearinghouse snapshots. However, websocket subscriptions already cover `openOrders` and `webData2`, which can provide equivalent live data for many of these immediate refreshes.

Websocket health is projected into app state under `[:websocket :health]` with stream rows carrying `:topic`, `:descriptor`, `:subscribed?`, and `:status`. We can use that state to determine if a topic is live for the current user and then skip redundant REST calls.

## Plan of Work

### Milestone 1: Add reusable websocket health stream-selector helper

Extend `/hyperopen/src/hyperopen/websocket/health_projection.cljs` with a pure helper that reports whether a topic stream is live for a selector (for this task, primarily `{ :user <address> }`). It must support address case-insensitive matching and prefer exact subscription-key matches when available.

### Milestone 2: Apply WS-first gating to order and user refresh fanout

Update:

- `/hyperopen/src/hyperopen/order/effects.cljs`
- `/hyperopen/src/hyperopen/websocket/user.cljs`

Behavior:

- When `openOrders` stream is live for the active address, skip `request-frontend-open-orders!` refresh calls in that cycle.
- When `webData2` stream is live for the active address, skip default `request-clearinghouse-state!` refresh call in that cycle.
- Keep spot and per-dex clearinghouse fallback refreshes unchanged in this slice.

### Milestone 3: Add regression tests and validate

Add tests in:

- `/hyperopen/test/hyperopen/websocket/health_projection_test.cljs` for selector-based live stream detection.
- `/hyperopen/test/hyperopen/core_bootstrap/order_effects_test.cljs` for WS-live gating in order submit/cancel refresh path.
- `/hyperopen/test/hyperopen/websocket/user_test.cljs` for WS-live gating in fill/ledger-triggered refresh path.

## Concrete Steps

From `/hyperopen`:

1. Edit `src/hyperopen/websocket/health_projection.cljs`.
2. Edit `src/hyperopen/order/effects.cljs` and `src/hyperopen/websocket/user.cljs` to call helper and gate fanout.
3. Edit tests in `test/hyperopen/websocket/health_projection_test.cljs`, `test/hyperopen/core_bootstrap/order_effects_test.cljs`, and `test/hyperopen/websocket/user_test.cljs`.
4. Run:
   - `npx shadow-cljs compile ws-test && node out/ws-test.js`
   - `npm run check` (best effort; report environment blockers)

## Validation and Acceptance

Acceptance criteria:

1. WS-live selector helper correctly identifies live stream matches by topic/user selector.
2. Order mutation refresh skips open-orders and default clearinghouse REST calls when corresponding streams are live.
3. User fill refresh skips open-orders and default clearinghouse REST calls when corresponding streams are live.
4. Fallback behavior is preserved when websocket health is missing/not live.
5. Updated tests pass in websocket suite.

## Idempotence and Recovery

Changes are additive and guarded by runtime health checks. If gating causes regressions, disabling the condition restores previous behavior without data migration.

## Artifacts and Notes

Command evidence:

- Pass: `npx shadow-cljs compile ws-test && node out/ws-test.js`
  - Output: `Ran 301 tests containing 1727 assertions. 0 failures, 0 errors.`
- Blocked: `npm run check`
  - Failure: missing `@noble/secp256k1` during app compile.

## Interfaces and Dependencies

No public API signatures are removed. The change introduces internal WS-health gating only.

Revision note (2026-03-05): Updated after implementing WS-first gating helpers/usage and adding regression coverage; recorded validation evidence and environment blocker.
