# Lift test Build Coverage for Targeted Websocket Runtime Files

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The `coverage/index.html` screenshot shows low `test/dev/out/cljs-runtime/hyperopen/websocket/*` coverage for specific runtime files (for example `active_asset_ctx.cljs`, `client.cljs`, `orderbook.cljs`, `trades.cljs`, `webdata2.cljs`). These files already have meaningful websocket test suites, but those suites run in `:ws-test` and are excluded from `:test`. After this change, `npm test` will include only the websocket namespaces that map to the low rows, raising coverage for those files in the `:test` build output while keeping scope tighter than “all websocket tests”.

## Progress

- [x] (2026-02-18 18:29Z) Captured baseline `:test` and `:ws-test` metrics for all target files from `coverage/lcov.info`.
- [x] (2026-02-18 18:29Z) Confirmed matching websocket test namespaces already exist for every target file.
- [x] (2026-02-18 18:29Z) Authored active ExecPlan with scoped implementation and acceptance criteria.
- [x] (2026-02-18 18:29Z) Updated `/hyperopen/shadow-cljs.edn` `:test` `:ns-regexp` to include only the target websocket test namespaces.
- [x] (2026-02-18 18:30Z) Ran validation gates (`npm run check`, `npm test`, `npm run test:websocket`) and regenerated coverage (`npm run coverage`).
- [x] (2026-02-18 18:30Z) Recorded before/after metrics for all target files and finalized living sections.

## Surprises & Discoveries

- Observation: The low rows are mainly a test-build selection issue, not missing tests.
  Evidence: For all target files, `:ws-test` rows already exceed `:test` rows substantially (for example `client.cljs` is `LH/LF 224/354` in `:test` vs `297/354` in `:ws-test`; `FNH/FNF 2/38` vs `22/38`).
- Observation: Each low-coverage file in the screenshot has a corresponding websocket namespace test file already checked in.
  Evidence: Existing test files include `/hyperopen/test/hyperopen/websocket/active_asset_ctx_test.cljs`, `/hyperopen/test/hyperopen/websocket/client_test.cljs`, `/hyperopen/test/hyperopen/websocket/orderbook_test.cljs`, `/hyperopen/test/hyperopen/websocket/trades_test.cljs`, `/hyperopen/test/hyperopen/websocket/webdata2_test.cljs`, and others for every listed row.
- Observation: A test-build allowlist can materially raise function and branch coverage without writing any new tests.
  Evidence: After regex update, `:test` row deltas include `trades_policy.cljs` functions `0/4 -> 4/4`, `orderbook_policy.cljs` branches `0/17 -> 37/50`, and `market_projection_runtime.cljs` functions `0/6 -> 6/6`.

## Decision Log

- Decision: Increase `:test` coverage by including a targeted allowlist of websocket namespaces in `:test`, rather than broad-including all websocket tests.
  Rationale: Delivers the requested file-level coverage lift with minimal expansion of `npm test` runtime and blast radius.
  Date/Author: 2026-02-18 / Codex

## Outcomes & Retrospective

Implementation completed with one configuration change and no new test-source code.

Before/after for targeted `:test` coverage rows:

- `active_asset_ctx.cljs`: `LH/LF 24/128 -> 88/128`, `BRH/BRF 0/13 -> 6/25`, `FNH/FNF 0/12 -> 3/12`
- `client.cljs`: `LH/LF 224/354 -> 297/354`, `BRH/BRF 4/46 -> 53/85`, `FNH/FNF 2/38 -> 22/38`
- `market_projection_runtime.cljs`: `LH/LF 13/93 -> 89/93`, `BRH/BRF 0/7 -> 16/24`, `FNH/FNF 0/6 -> 6/6`
- `orderbook.cljs`: `LH/LF 44/102 -> 79/102`, `BRH/BRF 0/12 -> 7/21`, `FNH/FNF 0/11 -> 3/11`
- `orderbook_policy.cljs`: `LH/LF 51/118 -> 110/118`, `BRH/BRF 0/17 -> 37/50`, `FNH/FNF 0/17 -> 15/17`
- `subscriptions_runtime.cljs`: `LH/LF 32/79 -> 50/79`, `BRH/BRF 6/22 -> 8/28`, `FNH/FNF 1/7 -> 3/7`
- `trades.cljs`: `LH/LF 82/325 -> 289/325`, `BRH/BRF 3/33 -> 91/137`, `FNH/FNF 3/29 -> 26/30`
- `trades_policy.cljs`: `LH/LF 34/61 -> 60/61`, `BRH/BRF 0/4 -> 40/55`, `FNH/FNF 0/4 -> 4/4`
- `user.cljs`: `LH/LF 57/113 -> 91/113`, `BRH/BRF 1/13 -> 18/32`, `FNH/FNF 1/11 -> 6/11`
- `webdata2.cljs`: `LH/LF 33/99 -> 71/99`, `BRH/BRF 0/10 -> 14/26`, `FNH/FNF 0/9 -> 2/9`

Validation results:

- `npm run check`: pass
- `npm test`: pass
- `npm run test:websocket`: pass
- `npm run coverage`: pass

The requested outcome is achieved: the low `:test` websocket rows from the screenshot are materially improved, with several moving from near-zero branch/function coverage to high coverage.

## Context and Orientation

This task targets the following source-mapped `:test` coverage rows:

- `.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/websocket/active_asset_ctx.cljs`
- `.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/websocket/client.cljs`
- `.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/websocket/market_projection_runtime.cljs`
- `.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/websocket/orderbook.cljs`
- `.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/websocket/orderbook_policy.cljs`
- `.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/websocket/subscriptions_runtime.cljs`
- `.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/websocket/trades.cljs`
- `.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/websocket/trades_policy.cljs`
- `.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/websocket/user.cljs`
- `.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/websocket/webdata2.cljs`

Current test selection is configured in `/hyperopen/shadow-cljs.edn`:

- `:test` currently includes non-websocket tests and websocket application runtime tests.
- `:ws-test` includes all websocket tests (plus wallet address watcher), which is why `:ws-test` rows are much higher.

## Plan of Work

Edit `:builds :test :ns-regexp` in `/hyperopen/shadow-cljs.edn` to append a targeted websocket namespace group for the 10 modules in scope: `active-asset-ctx`, `client`, `market-projection-runtime`, `orderbook`, `orderbook-policy`, `subscriptions-runtime`, `trades`, `trades-policy`, `user`, and `webdata2`.

Then run the required repository gates and coverage. Confirm `npm test` output includes those websocket test namespaces and extract updated `:test` metrics for each target file from `coverage/lcov.info`.

If a target still remains low after inclusion, add focused tests in that module’s existing test namespace only for uncovered branches/functions.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/shadow-cljs.edn`:
   - Update `:builds :test :ns-regexp` to include:
     - `hyperopen.websocket.(active-asset-ctx|client|market-projection-runtime|orderbook|orderbook-policy|subscriptions-runtime|trades|trades-policy|user|webdata2)-test`
2. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
   - `npm run coverage`
3. Extract updated `:test` rows for the 10 target files from `coverage/lcov.info`.

Expected observable output:

  - `npm test` includes the targeted websocket test namespaces.
  - `coverage/lcov.info` shows increased `LH/LF`, `BRH/BRF`, and `FNH/FNF` for targeted `:test` rows.

## Validation and Acceptance

Acceptance requires:

- Required gates pass:
  - `npm run check`
  - `npm test`
  - `npm run test:websocket`
- Coverage generation passes:
  - `npm run coverage`
- For each target file listed above, `:test` row metrics improve versus baseline from this plan.

## Idempotence and Recovery

This is a single regex configuration change and is safe to rerun. If `npm test` grows too much, narrow the allowlist to a smaller subset of websocket namespaces while preserving user-requested targets.

## Artifacts and Notes

Baseline metrics from `coverage/lcov.info` (`:test` rows):

- `active_asset_ctx.cljs`: `LH/LF 24/128`, `BRH/BRF 0/13`, `FNH/FNF 0/12`
- `client.cljs`: `LH/LF 224/354`, `BRH/BRF 4/46`, `FNH/FNF 2/38`
- `market_projection_runtime.cljs`: `LH/LF 13/93`, `BRH/BRF 0/7`, `FNH/FNF 0/6`
- `orderbook.cljs`: `LH/LF 44/102`, `BRH/BRF 0/12`, `FNH/FNF 0/11`
- `orderbook_policy.cljs`: `LH/LF 51/118`, `BRH/BRF 0/17`, `FNH/FNF 0/17`
- `subscriptions_runtime.cljs`: `LH/LF 32/79`, `BRH/BRF 6/22`, `FNH/FNF 1/7`
- `trades.cljs`: `LH/LF 82/325`, `BRH/BRF 3/33`, `FNH/FNF 3/29`
- `trades_policy.cljs`: `LH/LF 34/61`, `BRH/BRF 0/4`, `FNH/FNF 0/4`
- `user.cljs`: `LH/LF 57/113`, `BRH/BRF 1/13`, `FNH/FNF 1/11`
- `webdata2.cljs`: `LH/LF 33/99`, `BRH/BRF 0/10`, `FNH/FNF 0/9`

## Interfaces and Dependencies

No production interfaces change. This work updates test namespace selection only.

Dependencies involved:

- Shadow-CLJS `:node-test` namespace selection via `:ns-regexp` in `/hyperopen/shadow-cljs.edn`.
- Existing websocket test namespaces in `/hyperopen/test/hyperopen/websocket/*_test.cljs`.

Plan revision note: 2026-02-18 18:29Z - Initial plan created with baseline metrics and targeted websocket namespace inclusion strategy.
Plan revision note: 2026-02-18 18:30Z - Recorded implementation completion, validation outputs, and before/after per-file coverage metrics.
