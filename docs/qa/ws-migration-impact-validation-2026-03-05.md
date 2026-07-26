# WS Migration Impact Validation (2026-03-05)

## Scope

This report summarizes deterministic before/after request-load and rate-limit evidence for the websocket-first `/info` migration epic (`hyperopen-nhv`). All metrics below come from repository tests that compare ws-first behavior against legacy REST fallback behavior.

## Evidence Summary

### 1) Non-subscribable identical `/info` requests (cache hardening)

Source test:
- `/hyperopen/test/hyperopen/api_test.cljs`
- `info-client-cache-reduces-rate-limit-retries-for-identical-requests-test`

Scenario:
- Two identical logical `portfolio` requests under transient `429 -> 200` response patterns.
- Compare cached (`:cache-key` + TTL) vs uncached baseline (`:force-refresh? true` on second logical request).

Observed counts:
- Cached path: `2` fetch attempts, `1` rate-limit event.
- Uncached path: `4` fetch attempts, `2` rate-limit events.

Delta:
- Fetch/load reduction: `50%`.
- 429-rate-limit event reduction: `50%`.

### 2) Post-order refresh fanout with live streams

Source tests:
- `/hyperopen/test/hyperopen/core_bootstrap/order_effects_test.cljs`
- `api-submit-order-effect-shows-success-toast-and-refreshes-history-and-open-orders-test`
- `api-submit-order-effect-skips-open-orders-and-default-clearinghouse-when-ws-streams-live-test`

Scenario:
- Order submit flow, compare legacy fallback vs ws-first when `openOrders` and `webData2` streams are live.

Observed counts:
- Legacy fallback path:
  - open-orders refresh calls: `2`
  - clearinghouse refresh calls: `2`
  - total refresh calls: `4`
- WS-first live-stream path:
  - open-orders refresh calls: `0`
  - clearinghouse refresh calls: `1` (per-dex backstop only)
  - total refresh calls: `1`

Delta:
- Total refresh call reduction: `75%`.
- Stream-covered fallback calls (`openOrders`, default clearinghouse) removed: `100%`.

### 3) User ledger-triggered refresh fanout with live streams

Source tests:
- `/hyperopen/test/hyperopen/websocket/user_test.cljs`
- `user-ledger-refresh-schedules-account-refresh-requests-after-ledger-update-test`
- `user-ledger-refresh-skips-open-orders-and-default-clearinghouse-when-ws-live-test`

Scenario:
- `userNonFundingLedgerUpdates` trigger account refresh scheduling, compare fallback vs ws-first with live streams.

Observed counts:
- Legacy fallback path:
  - open-orders refresh: `1`
  - perp clearinghouse refresh: `1`
  - spot clearinghouse refresh: `1`
  - total: `3`
- WS-first live-stream path:
  - open-orders refresh: `0`
  - perp clearinghouse refresh: `0`
  - spot clearinghouse refresh: `1`
  - total: `1`

Delta:
- Total refresh call reduction: `66.7%`.
- Stream-covered fallback calls removed: `100%`.

### 4) Startup bootstrap stream-covered fetches

Source tests:
- `/hyperopen/test/hyperopen/startup/runtime_test.cljs`
- `bootstrap-account-data-ws-first-skips-stream-covered-fetches-when-streams-live-test`
- `bootstrap-account-data-flag-off-fetches-stream-backed-surfaces-immediately-test`

Scenario:
- Stage-A startup bootstrap when `openOrders`, `userFills`, and `userFundings` streams are live.

Observed behavior:
- WS-first enabled: immediate stage-A calls do not include `:open-orders`, `:fills`, `:fundings`.
- WS-first disabled: immediate stage-A calls include all three (`:open-orders`, `:fills`, `:fundings`).

Delta:
- Immediate stream-covered startup fetches reduced from `3` to `0` (`100%` reduction for those surfaces).

### 5) Inactive route/tab gating for fallback REST surfaces

Source tests:
- `/hyperopen/test/hyperopen/account/history/effects_test.cljs`
  - `api-fetch-user-funding-history-effect-skips-when-funding-tab-is-inactive-test`
  - `api-fetch-historical-orders-effect-skips-when-order-history-tab-is-inactive-test`
- `/hyperopen/test/hyperopen/funding_comparison/effects_test.cljs`
  - `api-fetch-predicted-fundings-skips-when-route-is-inactive-test`
- `/hyperopen/test/hyperopen/vaults/effects_test.cljs`
  - `api-fetch-vault-details-skips-when-detail-route-is-inactive-test`

Observed counts:
- In each inactive-route/tab case, request calls are `0` and load projections are not started.

Delta:
- Background/inactive request pressure on these surfaces is reduced from one attempted poll-triggered fetch to zero (`100%` suppression while inactive).

## Validation Command

From `/hyperopen`:

    npx shadow-cljs compile ws-test && node out/ws-test.js

Observed output:

    Ran 316 tests containing 1772 assertions.
    0 failures, 0 errors.

## Acceptance Mapping (`hyperopen-nhv.7`)

- `/info` POST volume reduced materially on key journeys: satisfied by sections 1-5.
- Rate-limit response frequency reduced vs baseline: satisfied by section 1 (`50%` reduction in 429 events under deterministic retry scenario).
- Regression tests updated/added: satisfied by API test update plus existing ws-first/fallback suites.
- Summary report with before/after metrics: this document.

## Limitations

- These are deterministic test-harness metrics, not production telemetry snapshots.
- Full required gates remain environment-blocked in this workspace (`shadow-cljs` PATH for npm scripts and missing `@noble/secp256k1` for `npm run check`).
