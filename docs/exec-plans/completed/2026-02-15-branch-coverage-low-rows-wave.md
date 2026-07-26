# Raise Lowest Branch Coverage Rows for Websocket Infrastructure, Startup, Flow, and Utility Paths

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The latest coverage report still has several low branch-coverage rows in websocket and startup-critical modules. These branches encode fallback paths, error handling, and state transition guardrails that should be explicitly tested to reduce regression risk. After this plan is implemented, the lowest branch rows in the screenshot will move out of low status, and the new tests will align with `/hyperopen/docs/QUALITY_SCORE.md` requirements for deterministic, behavior-oriented, websocket-reliable coverage.

## Progress

- [x] (2026-02-15 23:38Z) Captured current low branch rows from `coverage/index.html` and mapped each row to concrete source files.
- [x] (2026-02-15 23:39Z) Extracted per-file branch percentages from `coverage/lcov.info` for targeted rows.
- [x] (2026-02-15 23:40Z) Authored active ExecPlan with milestones, test matrix, acceptance thresholds, and validation commands.
- [x] (2026-02-15 23:45Z) Added websocket-visible interval coverage test for all `interval-to-milliseconds` multimethod branches.
- [x] (2026-02-15 23:49Z) Added websocket infrastructure tests for `transport.cljs` and `runtime_effects.cljs` (success, fallback, error, guard/no-op paths).
- [x] (2026-02-15 23:50Z) Expanded ACL tests for `hyperliquid.cljs` to cover validation-enabled path, default-source path, and non-string/missing-channel branches.
- [x] (2026-02-15 23:52Z) Added targeted flow branch tests (`flow_branch_coverage_test.cljs`) for zero-range, index-zero, cond-triad, and net-volume sign branches.
- [x] (2026-02-15 23:55Z) Added startup runtime branch tests (`startup/runtime_test.cljs`) and expanded init tests for reset/runtime-fallback behavior.
- [x] (2026-02-16 00:01Z) Expanded wallet address watcher tests for guard/no-op/error/private-branch paths and invalid handler precondition coverage.
- [x] (2026-02-16 00:02Z) Ran required gates and regenerated coverage; captured before/after branch percentages for all targeted rows.

## Surprises & Discoveries

- Observation: `ws-test` branch coverage only includes namespaces matching the ws build regexp, not generic test-runner requires.
  Evidence: `/hyperopen/shadow-cljs.edn` sets `:ws-test` `:ns-regexp` to `^(hyperopen\.websocket\..*-test|hyperopen\.wallet\.address-watcher-test)$`.
- Observation: `test/dev/out/cljs-runtime/hyperopen/websocket/infrastructure` low branch coverage is dominated by `transport.cljs` (`13/81`, `16.05%`), not `runtime_effects.cljs`.
  Evidence: LCOV per-file breakdown for infrastructure modules shows transport as the largest branch deficit.
- Observation: startup low branch coverage is concentrated in `startup/init.cljs` (`3/25`, `12%`) and `startup/runtime.cljs` (`23/68`, `33.82%`), and there is currently no dedicated `startup/runtime_test.cljs`.
  Evidence: `test/hyperopen/startup/` contains `collaborators_test.cljs`, `composition_test.cljs`, `init_test.cljs`, and `wiring_test.cljs` only.
- Observation: infrastructure branch coverage crossed the low threshold with only one additional covered branch once websocket runtime-effect guard paths were exercised.
  Evidence: `test/ws-test ... websocket/infrastructure` moved from `47/149` (`31.54%`) to `93/186` (`50%`).
- Observation: wallet ws-test branch percentage improved substantially but remains constrained by high denominator growth from generated protocol/guard branches.
  Evidence: `ws-test ... wallet` moved from `21/49` (`42.85%`) to `31/58` (`53.44%`) after expanding deterministic edge-path tests.

## Decision Log

- Decision: Treat branch-coverage recovery as behavior-specification work, not metric-only work.
  Rationale: `/hyperopen/docs/QUALITY_SCORE.md` requires deterministic, invariant-focused websocket and startup tests, so each added test must assert domain behavior, not only execute lines.
  Date/Author: 2026-02-15 / Codex
- Decision: Prioritize files with both low branch percentage and high branch-count denominator first.
  Rationale: Improving `transport.cljs`, `startup/runtime.cljs`, and `startup/init.cljs` yields the largest risk reduction per test.
  Date/Author: 2026-02-15 / Codex
- Decision: Keep implementation additive and avoid production refactors in this wave.
  Rationale: The goal is branch coverage uplift with minimal behavior change risk; additive tests preserve runtime stability.
  Date/Author: 2026-02-15 / Codex

## Outcomes & Retrospective

Implementation completed with additive test-only changes.

Branch coverage deltas for targeted rows:

- `ws-test/dev/out/cljs-runtime/hyperopen/utils`: `3/16` (`18.75%`) -> `15/16` (`93.75%`)
- `test/dev/out/cljs-runtime/hyperopen/websocket/infrastructure`: `47/149` (`31.54%`) -> `93/186` (`50%`)
- `ws-test/dev/out/cljs-runtime/hyperopen/websocket/infrastructure`: `47/149` (`31.54%`) -> `93/186` (`50%`)
- `test/dev/out/cljs-runtime/hyperopen/websocket/acl`: `2/6` (`33.33%`) -> `4/6` (`66.66%`)
- `ws-test/dev/out/cljs-runtime/hyperopen/websocket/acl`: `2/6` (`33.33%`) -> `4/6` (`66.66%`)
- `test/dev/out/cljs-runtime/hyperopen/domain/trading/indicators/flow`: `46/110` (`41.81%`) -> `66/121` (`54.54%`)
- `ws-test/dev/out/cljs-runtime/hyperopen/wallet`: `21/49` (`42.85%`) -> `31/58` (`53.44%`)
- `test/dev/out/cljs-runtime/hyperopen/startup`: `103/211` (`48.81%`) -> `147/245` (`60%`)

Validation gates:

- `npm run check` passed.
- `npm test` passed.
- `npm run test:websocket` passed.
- `npm run coverage` passed; global branch coverage is `63.14%` (`6927/10970`).

Acceptance criteria status:

- Met: utils, websocket infrastructure (test + ws-test), websocket ACL (test + ws-test), startup.
- Near-miss: flow ended at `54.54%` vs `55%` target.
- Partial: ws wallet improved strongly but ended at `53.44%` vs `60%` target.

## Context and Orientation

Targeted low branch rows from `coverage/index.html` (baseline):

- `ws-test/dev/out/cljs-runtime/hyperopen/utils`: `3/16` (`18.75%`)
- `test/dev/out/cljs-runtime/hyperopen/websocket/infrastructure`: `47/149` (`31.54%`)
- `ws-test/dev/out/cljs-runtime/hyperopen/websocket/infrastructure`: `47/149` (`31.54%`)
- `test/dev/out/cljs-runtime/hyperopen/websocket/acl`: `2/6` (`33.33%`)
- `ws-test/dev/out/cljs-runtime/hyperopen/websocket/acl`: `2/6` (`33.33%`)
- `test/dev/out/cljs-runtime/hyperopen/domain/trading/indicators/flow`: `46/110` (`41.81%`)
- `ws-test/dev/out/cljs-runtime/hyperopen/wallet`: `21/49` (`42.85%`)
- `test/dev/out/cljs-runtime/hyperopen/startup`: `103/211` (`48.81%`)

Corresponding source files to target:

- `/hyperopen/src/hyperopen/utils/interval.cljs`
- `/hyperopen/src/hyperopen/websocket/infrastructure/transport.cljs`
- `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`
- `/hyperopen/src/hyperopen/websocket/acl/hyperliquid.cljs`
- `/hyperopen/src/hyperopen/domain/trading/indicators/flow/money.cljs`
- `/hyperopen/src/hyperopen/domain/trading/indicators/flow/volume.cljs`
- `/hyperopen/src/hyperopen/wallet/address_watcher.cljs`
- `/hyperopen/src/hyperopen/startup/init.cljs`
- `/hyperopen/src/hyperopen/startup/runtime.cljs`

## Plan of Work

Milestone 1 adds a websocket-regexp-compatible test that executes all interval multimethod branches (`:1m`, `:3m`, `:5m`, `:15m`, `:30m`, `:1h`, `:2h`, `:4h`, `:8h`, `:12h`, `:1d`, `:3d`, `:1w`, `:1M`, and default fallback). This closes the ws-test utilities branch deficit while preserving deterministic assertions.

Milestone 2 introduces direct infrastructure unit tests:

- `transport.cljs`: exercise `FunctionTransport`, `FunctionScheduler`, and `FunctionClock` branches including nil socket guards, online/offline default behavior, hidden-tab fallback, open/connecting/active socket predicates, and handler attach/detach.
- `runtime_effects.cljs`: execute `interpret-effect!` branches for each `:fx/type`, including success and error/no-op paths (`socket-send` closed socket, socket errors, timer set/clear both kinds, lifecycle listener idempotence, parse success/error, projection updates, log/dead-letter).

Milestone 3 expands ACL branch tests in `hyperliquid_test.cljs`:

- `contracts/validation-enabled?` true path with provider-message assertion.
- source fallback branch (`source` omitted => `:hyperliquid/ws`).
- non-string `:channel` and missing `:channel` branches.

Milestone 4 adds explicit flow branch tests in new or expanded indicator test namespaces:

- `volume.cljs`: zero range branch in accumulation distribution, index-zero branch in ASI, all three `cond` arms in range selection and net-volume sign branches (`>`, `<`, `=`).
- `money.cljs`: parse-period fallback/clamp branches by passing nil/string/out-of-range params and asserting stable result shape.

Milestone 5 adds startup and wallet branch tests:

- startup init/runtime: branch matrix for startup-runtime atom present/absent, idle callback available/unavailable, service worker register success/failure/unsupported, deferred bootstrap schedule idempotence, account bootstrap nil-address and stale-address guards.
- wallet address watcher: branches for not-watching no-op listener, ws-connected and ws-disconnected notification paths, handler error catch path, start/stop idempotence, pending subscription present/absent.

Milestone 6 runs validation and captures evidence.

## Concrete Steps

From `/hyperopen`:

1. Add/expand websocket tests:
   - `/hyperopen/test/hyperopen/websocket/interval_coverage_test.cljs` (new)
   - `/hyperopen/test/hyperopen/websocket/infrastructure/transport_test.cljs` (new)
   - `/hyperopen/test/hyperopen/websocket/infrastructure/runtime_effects_test.cljs` (new)
   - `/hyperopen/test/hyperopen/websocket/acl/hyperliquid_test.cljs` (expand)
2. Add/expand flow tests:
   - `/hyperopen/test/hyperopen/domain/trading/indicators/flow_branch_coverage_test.cljs` (new)
3. Add/expand startup tests:
   - `/hyperopen/test/hyperopen/startup/runtime_test.cljs` (new)
   - `/hyperopen/test/hyperopen/startup/init_test.cljs` (expand)
4. Expand wallet watcher tests:
   - `/hyperopen/test/hyperopen/wallet/address_watcher_test.cljs`
5. Ensure new namespaces are included where required by runners/build regex behavior.
6. Run required commands:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
   - `npm run coverage`

## Validation and Acceptance

Acceptance criteria:

- Branch coverage for each targeted row improves from baseline.
- Target minimums for this wave:
  - `ws-test/.../utils` >= `60%`
  - `test/ws-test .../websocket/infrastructure` >= `50%`
  - `test/ws-test .../websocket/acl` >= `66%`
  - `test .../domain/trading/indicators/flow` >= `55%`
  - `ws-test .../wallet` >= `60%`
  - `test .../startup` >= `60%`
- Tests remain deterministic and behavior-oriented per `/hyperopen/docs/QUALITY_SCORE.md`.
- Required gates pass:
  - `npm run check`
  - `npm test`
  - `npm run test:websocket`

Stretch target: all targeted rows reach >= `70%` branch coverage where practical without production code changes.

## Idempotence and Recovery

All steps are additive tests. Re-running test and coverage commands is safe. If a test introduces global-state coupling, restore globals in `finally` blocks and isolate fixtures to `:each` scope.

## Artifacts and Notes

Baseline row metrics were captured from `/hyperopen/coverage/index.html` generated on 2026-02-15.

## Interfaces and Dependencies

No production interfaces change in this plan. Tests must target existing public functions/protocols and preserve runtime contracts, with special care for websocket determinism, lifecycle behavior, and effect ordering constraints from `/hyperopen/docs/QUALITY_SCORE.md`.

Plan revision note: 2026-02-15 23:40Z - Initial ExecPlan created for low branch-coverage rows in latest report.
Plan revision note: 2026-02-16 00:02Z - Implementation completed; branch deltas and validation outcomes recorded.
