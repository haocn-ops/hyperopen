# Spectate Mode `/info` Sampling: userFunding + spotClearinghouseState (2026-03-05)

## Scope

Manual browser-inspection sampling on local branch `codex/ws-migration-manual-testing` for Spectate Mode address:

- `0x162cc7c861ebd0c06b3d72319201150482518185`

Sampling target:

- `/info` POST request volume aggregated by `type`
- rate-limit counters by `type`

Window size:

- `120s` each sample

## Method

Used browser-inspection live session against `http://localhost:8082/` and queried runtime request telemetry from `hyperopen.api.default$/get_request_stats`.

Runtime controls used during sampling:

- reset counters via `reset_request_runtime_BANG_`
- force Spectate Mode active with the target address
- set Account Info selected tab to `:funding-history` for funding workflow coverage

## Results

### A. Pre-fix (before spot-clearinghouse gating/TTL hardening), steady-state 120s

- `startedTotal`: `18`
- `startedByType`:
  - `spotClearinghouseState`: `18`
- `rateLimitedByType`: none

### B. Post-fix, cold-start 120s after reload

- `startedTotal`: `48`
- `startedByType`:
  - `userFunding`: `30`
  - `clearinghouseState`: `7`
  - `frontendOpenOrders`: `3`
  - `spotClearinghouseState`: `1`
  - `userAbstraction`: `1`
  - `userFees`: `1`
  - `userNonFundingLedgerUpdates`: `1`
  - `historicalOrders`: `1`
  - `userFills`: `1`
  - `perpDexs`: `1`
  - `portfolio`: `1`
- `rateLimitedByType`: none

Notes:

- Cold-start still includes one-time user funding backfill fanout (`userFunding: 30`) in this forced immediate funding-tab scenario.
- `spotClearinghouseState` dropped to `1` in the same cold-start window.

### C. Post-fix, steady-state 120s (no reload)

- `startedTotal`: `0`
- `startedByType`: none
- `rateLimitedByType`: none

## Interpretation

- `spotClearinghouseState` cadence was materially reduced for the sampled funding-history workflow:
  - `18 -> 0` in steady-state (`100%` reduction).
  - `18 -> 1` in cold-start comparable window (`94.4%` reduction).
- No rate-limit events were observed in sampled windows.
- Remaining cold-start `userFunding` fanout is bounded to startup/recovery conditions in this scenario, not steady-state polling.

