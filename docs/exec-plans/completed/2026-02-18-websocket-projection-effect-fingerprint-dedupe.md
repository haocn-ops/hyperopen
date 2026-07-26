# Dedupe WebSocket Runtime Projections At Effect Boundary

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The websocket runtime currently emits `:fx/project-connection-state` and `:fx/project-stream-metrics` on every reducer step, and the effect interpreter always calls `reset!` on public projection atoms. That causes `add-watch` callbacks in `/hyperopen/src/hyperopen/startup/watchers.cljs` to run far more often than needed, including extra store merge work when semantic websocket projection state has not changed.

After this change, projection effects include stable fingerprints and the interpreter deduplicates at the effect boundary, so unchanged projections skip `reset!`. The watcher layer also gates legacy store merges to only run when the legacy projection keys change. Users should see lower websocket-induced store churn while preserving websocket health/status behavior and public API compatibility.

## Progress

- [x] (2026-02-18 02:11Z) Reviewed `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/RELIABILITY.md`, and `/hyperopen/docs/QUALITY_SCORE.md` for runtime and test invariants.
- [x] (2026-02-18 02:11Z) Confirmed hotspots in `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs`, `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`, and `/hyperopen/src/hyperopen/startup/watchers.cljs`.
- [x] (2026-02-18 02:11Z) Authored active ExecPlan with implementation and validation milestones.
- [x] (2026-02-18 02:15Z) Implemented reducer-side projection fingerprint emission for connection and stream projections.
- [x] (2026-02-18 02:15Z) Implemented effect-boundary projection dedupe in runtime interpreter so unchanged fingerprints skip atom resets.
- [x] (2026-02-18 02:15Z) Tightened watcher connection projection merge logic so non-legacy projection changes do not enqueue store merges.
- [x] (2026-02-18 02:16Z) Added focused runtime reducer/effects/watcher tests proving dedupe skips redundant watch/reset churn and preserves transition behavior.
- [x] (2026-02-18 02:17Z) Ran required gates: `npm run check`, `npm test`, `npm run test:websocket` (all passed).
- [x] (2026-02-18 02:17Z) Moved this ExecPlan to `/hyperopen/docs/exec-plans/completed/` after validation passed.

## Surprises & Discoveries

- Observation: Connection watcher currently always queues a microtask to merge legacy websocket projection data into the app store, even when only non-legacy fields changed.
  Evidence: In `/hyperopen/src/hyperopen/startup/watchers.cljs`, the `connection-state` watch callback calls `platform/queue-microtask!` unconditionally.
- Observation: Runtime health synchronization already dedupes now-only churn by fingerprint in a downstream layer, but projection atom resets still trigger upstream watcher callbacks.
  Evidence: `/hyperopen/src/hyperopen/websocket/health_runtime.cljs` checks projected fingerprint before syncing.
- Observation: Fingerprint dedupe at interpreter boundary suppresses repeated atom watcher invocations when duplicate projection effects are replayed.
  Evidence: `/hyperopen/test/hyperopen/websocket/infrastructure/runtime_effects_test.cljs` now asserts duplicate projection effects keep watcher counts at 2 total writes (first + changed), not 3.
- Observation: Connection watcher merge gating avoids microtask/store work when only `:now-ms` and `:last-activity-at-ms` churn.
  Evidence: `/hyperopen/test/hyperopen/startup/watchers_test.cljs` now asserts `queue-microtask` call count remains 0 for now-only churn.

## Decision Log

- Decision: Keep reducer purity and existing message/effect algebra intact, and add projection fingerprint metadata as additive effect fields.
  Rationale: This keeps deterministic reducer behavior and avoids side effects in reducer logic while enabling efficient dedupe in the interpreter.
  Date/Author: 2026-02-18 / Codex
- Decision: Perform dedupe at effect boundary (interpreter) using interpreter-owned last-fingerprint state in `io-state`.
  Rationale: This directly addresses redundant `reset!` work without changing websocket client public APIs.
  Date/Author: 2026-02-18 / Codex
- Decision: Add a watcher-level legacy projection equality gate in addition to interpreter dedupe.
  Rationale: If future changes add projection fields that churn but are irrelevant to legacy store projection, watcher overhead remains bounded.
  Date/Author: 2026-02-18 / Codex

## Outcomes & Retrospective

Implementation completed and validated.

What changed:

- Reducer projection effects now carry deterministic `:projection-fingerprint` payloads in `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs`.
- Interpreter projection handlers in `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs` now cache prior fingerprints in `io-state` and skip `reset!` when fingerprints match.
- Connection watcher in `/hyperopen/src/hyperopen/startup/watchers.cljs` now queues legacy store merge microtasks only when legacy projection keys actually changed.

Validation evidence:

- `npm run test:websocket`: pass (`129` tests, `530` assertions, `0` failures, `0` errors).
- `npm run check`: pass (all lint/doc checks + app/test compile passes).
- `npm test`: pass (`1093` tests, `4961` assertions, `0` failures, `0` errors).

Residual risk:

- Fingerprint correctness is now part of runtime-effect contract for projection effects. New projection fields must either be represented in fingerprints or intentionally excluded with a documented rationale.

## Context and Orientation

Websocket runtime decisions live in `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs` as a pure `step` function that emits `RuntimeEffect` values. Projection effects are emitted in `append-projections` and now include explicit `:projection-fingerprint` metadata.

Input/output boundaries are implemented in `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`. For projection effects, the interpreter now compares incoming fingerprints against cached applied fingerprints and skips `reset!` when unchanged.

Startup watchers are installed from `/hyperopen/src/hyperopen/startup/watchers.cljs`. `connection-state` watcher updates legacy store websocket projection fields and dispatch diagnostics on transitions; `stream-runtime` watcher triggers websocket health sync when health fingerprint changes.

In this plan, a projection fingerprint means a deterministic value attached to a projection effect that represents semantic projection identity for dedupe decisions. Equal fingerprint values mean interpreter should skip atom reset and therefore skip downstream `add-watch` callback work.

## Plan of Work

Milestone 1 updates `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs` to compute and attach connection and stream projection fingerprints when emitting projection effects. Fingerprints are computed from projection content and socket identity inputs needed for connection projection hydration.

Milestone 2 updates `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs` so projection interpreter branches compare incoming fingerprints against `io-state` cached fingerprints. Interpreter updates cached fingerprint and performs `reset!` only when the fingerprint changes.

Milestone 3 updates `/hyperopen/src/hyperopen/startup/watchers.cljs` so connection-state watcher queues legacy store merge work only when the legacy projection map changes, while preserving status-transition diagnostics and health sync behavior.

Milestone 4 updates tests in `/hyperopen/test/hyperopen/websocket/infrastructure/runtime_effects_test.cljs` and `/hyperopen/test/hyperopen/startup/watchers_test.cljs` to prove dedupe behavior and transition behavior remain correct.

Milestone 5 runs required gates and records outputs in this plan before moving it to completed.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs`:
   - Add private helpers for connection projection fingerprint and stream projection fingerprint.
   - Include those fingerprints in `:fx/project-connection-state` and `:fx/project-stream-metrics` payloads.
2. Edit `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`:
   - Add helper to update projection atom only when fingerprint changed.
   - Track last fingerprints in interpreter `io-state` under a dedicated key.
3. Edit `/hyperopen/src/hyperopen/startup/watchers.cljs`:
   - Add helper to derive legacy websocket projection map.
   - Gate `queue-microtask!` store merge on legacy projection inequality.
4. Update tests:
   - `/hyperopen/test/hyperopen/websocket/infrastructure/runtime_effects_test.cljs`.
   - `/hyperopen/test/hyperopen/startup/watchers_test.cljs`.
5. Run validation gates:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

Expected command results:

  - All three commands complete with zero test failures/errors and no compile/lint gate failures.
  - Updated tests explicitly assert duplicate projection effects do not trigger repeated atom watch callbacks.

## Validation and Acceptance

Acceptance criteria:

- Reducer still emits projection effects every step, but each projection effect includes a deterministic fingerprint.
- Runtime effect interpreter skips `reset!` for `connection-state-atom` and `stream-runtime-atom` when incoming fingerprint equals previously applied fingerprint.
- Startup connection watcher does not enqueue legacy store merge microtasks when only non-legacy connection projection fields change.
- Connection status transitions still emit diagnostics/health sync/connect-disconnect callbacks.
- Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

These edits are additive and localized to runtime projection wiring and watcher gating. Re-running tests and gate commands is safe. If a regression is found, recovery path is to revert only the projection dedupe branches while retaining new tests to guide repair.

## Artifacts and Notes

Planned changed paths:

- `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs`
- `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`
- `/hyperopen/src/hyperopen/startup/watchers.cljs`
- `/hyperopen/test/hyperopen/websocket/infrastructure/runtime_effects_test.cljs`
- `/hyperopen/test/hyperopen/startup/watchers_test.cljs`
- `/hyperopen/docs/exec-plans/completed/2026-02-18-websocket-projection-effect-fingerprint-dedupe.md`

Evidence to capture during completion:

- Focused tests demonstrating duplicate projection effects do not re-trigger watch callbacks.
- Gate command summaries for required checks.

## Interfaces and Dependencies

Public websocket client interfaces in `/hyperopen/src/hyperopen/websocket/client.cljs` remain unchanged.

Dependencies used by this change:

- Runtime reducer/effect modules:
  - `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs`
  - `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`
- Startup watcher wiring:
  - `/hyperopen/src/hyperopen/startup/watchers.cljs`
- Existing runtime IO-state atom in runtime startup path:
  - `/hyperopen/src/hyperopen/websocket/application/runtime.cljs`

Plan revision note: 2026-02-18 02:11Z - Initial plan created from hotspot analysis and websocket reliability constraints.
Plan revision note: 2026-02-18 02:17Z - Updated living sections after implementing projection fingerprint dedupe, adding regression tests, and passing required validation gates.
