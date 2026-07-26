# Lift ws-test Coverage for Wallet Address Watcher

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The websocket coverage report shows `hyperopen/wallet/address_watcher.cljs` as red in the `ws-test` build even though this module already has comprehensive tests in the repository. This creates a false signal in websocket coverage triage and leaves branch/function metrics at zero in that build. After this change, `npm run test:websocket` will include the existing wallet address watcher tests, and the `ws-test` row for `address_watcher.cljs` will move from low/zero branch-function coverage to high coverage.

## Progress

- [x] (2026-02-18 18:24Z) Captured baseline `ws-test` coverage metrics for `address_watcher.cljs` from `coverage/lcov.info`.
- [x] (2026-02-18 18:24Z) Confirmed existing tests in `/hyperopen/test/hyperopen/wallet/address_watcher_test.cljs` already cover core logic paths.
- [x] (2026-02-18 18:24Z) Authored active ExecPlan with implementation and validation steps.
- [x] (2026-02-18 18:25Z) Updated `:ws-test` namespace regex to include `hyperopen.wallet.address-watcher-test`.
- [x] (2026-02-18 18:26Z) Ran validation gates (`npm run check`, `npm test`, `npm run test:websocket`) and regenerated coverage.
- [x] (2026-02-18 18:26Z) Captured improved `ws-test` metrics for `address_watcher.cljs` and finalized plan outcomes.

## Surprises & Discoveries

- Observation: The same source file already has strong coverage in the `:test` build but poor coverage in `:ws-test`.
  Evidence: Baseline rows from `coverage/lcov.info`:
  - `.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/wallet/address_watcher.cljs` => `LH/LF 168/174`, `FNH/FNF 10/12`.
  - `.shadow-cljs/builds/ws-test/dev/out/cljs-runtime/hyperopen/wallet/address_watcher.cljs` => `LH/LF 69/174`, `FNH/FNF 0/12`.
- Observation: `hyperopen.wallet.address-watcher-test` exists and exercises private/public watcher branches, but `:ws-test` selection excludes it.
  Evidence: `/hyperopen/shadow-cljs.edn` uses `:ns-regexp "^(hyperopen\\.websocket\\..*-test)$"` for `:ws-test`.
- Observation: Coverage improvement came entirely from test inclusion; no test-source edits were needed.
  Evidence: `npm run test:websocket` now includes `Testing hyperopen.wallet.address-watcher-test`, and ws-test metrics moved from `LH/LF 69/174, BRH/BRF 0/26, FNH/FNF 0/12` to `LH/LF 168/174, BRH/BRF 31/58, FNH/FNF 10/12`.

## Decision Log

- Decision: Raise `ws-test` coverage by including the existing wallet test namespace rather than duplicating tests under a websocket namespace.
  Rationale: Reuses well-maintained coverage tests and avoids test duplication drift.
  Date/Author: 2026-02-18 / Codex

## Outcomes & Retrospective

Implementation completed with one config change in `/hyperopen/shadow-cljs.edn`.

Coverage delta for `.shadow-cljs/builds/ws-test/dev/out/cljs-runtime/hyperopen/wallet/address_watcher.cljs`:

- Lines: `69/174` -> `168/174`
- Branches: `0/26` -> `31/58`
- Functions: `0/12` -> `10/12`

Validation results:

- `npm run check`: pass
- `npm test`: pass
- `npm run test:websocket`: pass
- `npm run coverage`: pass

Outcome: The targeted ws-test row is no longer a low/zero-branch-function hotspot and now reflects real module coverage already present in the repository.

## Context and Orientation

The low row in the screenshot corresponds to this source file in websocket coverage:

- `.shadow-cljs/builds/ws-test/dev/out/cljs-runtime/hyperopen/wallet/address_watcher.cljs`

The production source is:

- `/hyperopen/src/hyperopen/wallet/address_watcher.cljs`

Existing tests are:

- `/hyperopen/test/hyperopen/wallet/address_watcher_test.cljs`

The selection issue is in:

- `/hyperopen/shadow-cljs.edn` under `:builds :ws-test :ns-regexp`.

## Plan of Work

Edit `/hyperopen/shadow-cljs.edn` so the `:ws-test` regex includes one additional namespace: `hyperopen.wallet.address-watcher-test`, while preserving all existing websocket namespace matching.

After the config change, run the required validation gates and regenerate coverage. Confirm that websocket test output includes `Testing hyperopen.wallet.address-watcher-test`, then extract the updated `ws-test` row for `address_watcher.cljs` from `coverage/lcov.info`.

If coverage remains unexpectedly low after inclusion, add narrowly scoped assertions only for still-missed branches in `/hyperopen/test/hyperopen/wallet/address_watcher_test.cljs`.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/shadow-cljs.edn`:
   - Change `:builds :ws-test :ns-regexp` from websocket-only matching to websocket-or-wallet-address-watcher matching.
2. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
   - `npm run coverage`
3. Extract metrics from `coverage/lcov.info` for:
   - `.shadow-cljs/builds/ws-test/dev/out/cljs-runtime/hyperopen/wallet/address_watcher.cljs`

Expected observable output:

  - `npm run test:websocket` includes `Testing hyperopen.wallet.address-watcher-test`.
  - `ws-test` row for `address_watcher.cljs` shows branch/function hits greater than zero.

## Validation and Acceptance

Acceptance criteria:

- Required validation gates pass:
  - `npm run check`
  - `npm test`
  - `npm run test:websocket`
- `npm run coverage` succeeds.
- `ws-test` row for `address_watcher.cljs` improves from baseline:
  - `LH/LF` above `69/174`.
  - `BRH/BRF` above `0/26`.
  - `FNH/FNF` above `0/12`.

## Idempotence and Recovery

The regex edit is safe to rerun and revert. If `ws-test` unexpectedly expands too much or slows significantly, narrow the regex back to websocket namespaces plus only `hyperopen.wallet.address-watcher-test`.

## Artifacts and Notes

Baseline from `coverage/lcov.info` (2026-02-18):

- `.shadow-cljs/builds/ws-test/dev/out/cljs-runtime/hyperopen/wallet/address_watcher.cljs`
  - `LH/LF 69/174`
  - `BRH/BRF 0/26`
  - `FNH/FNF 0/12`

## Interfaces and Dependencies

No production code interfaces are changed. This task only adjusts test namespace selection.

Relevant interfaces:

- Shadow-CLJS `:node-test` selection via `:ns-regexp`.
- Existing `cljs.test` namespace: `hyperopen.wallet.address-watcher-test`.

Plan revision note: 2026-02-18 18:24Z - Initial plan authored with baseline metrics and scoped `ws-test` regex inclusion strategy.
Plan revision note: 2026-02-18 18:26Z - Recorded successful regex inclusion, validation results, and before/after ws-test coverage metrics.
