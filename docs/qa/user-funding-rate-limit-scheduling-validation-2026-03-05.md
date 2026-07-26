# User Funding Rate-Limit Scheduling Validation (2026-03-05)

## Scope

Validate startup and endpoint changes that reduce `userFunding` burst pressure:

- bounded startup funding window,
- paced `userFunding` page-to-page pagination.

## Implementation Summary

- Added startup funding lookback config:
  - `/hyperopen/src/hyperopen/config.cljs`
  - `/hyperopen/src/hyperopen/runtime/state.cljs`
  - `/hyperopen/src/hyperopen/app/startup.cljs`
  - `/hyperopen/src/hyperopen/startup/runtime.cljs`
- Added `userFunding` pagination pacing policy + delay seam:
  - `/hyperopen/src/hyperopen/api/request_policy.cljs`
  - `/hyperopen/src/hyperopen/api/endpoints/account.cljs`
- Added/updated regression tests:
  - `/hyperopen/test/hyperopen/api/endpoints/account_test.cljs`
  - `/hyperopen/test/hyperopen/startup/runtime_test.cljs`

## Behavioral Expectations

1. Startup funding fetch is no longer all-time by default; it requests a bounded recent window (`:startup :funding-history-lookback-ms`, default 7 days).
2. Multi-page `userFunding` backfills no longer issue immediate recursive requests; each subsequent page waits at least 1250ms by default.
3. Delay scaling is adaptive by page density and capped by max delay policy.

## Deterministic Test Evidence

- `request-user-funding-history-paginates-forward-by-time-test` now validates inter-page waits:
  - expected delays: `[1250 1250]`.
- `request-user-funding-history-supports-wrapped-payloads-test` validates one inter-page wait:
  - expected delays: `[1250]`.
- Added `request-user-funding-history-adapts-page-delay-to-page-density-test`:
  - with page-size override `2` and first page count `4`, expected delay: `[2000]`.
- Startup runtime tests validate bounded funding opts include `:start-time-ms` and `:end-time-ms`, including explicit deterministic lookback scenarios.

## Required Gates

Executed from `/hyperopen`:

1. `npm run check`
   - Pass
2. `npm test`
   - Pass
   - Result: `Ran 1919 tests containing 9859 assertions. 0 failures, 0 errors.`
3. `npm run test:websocket`
   - Pass
   - Result: `Ran 333 tests containing 1840 assertions. 0 failures, 0 errors.`

## Risk Notes

- Paging delay increases completion time for very large all-time history fetches, but keeps UI responsive and reduces burst-rate risk.
- Deep history remains available via existing funding-tab flows; this change does not remove user access to full history.
