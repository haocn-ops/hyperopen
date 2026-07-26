# Throttle WebSocket Health Projection and Fingerprint-Gate Sync

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The websocket runtime currently recalculates transport/stream health hysteresis on nearly every reducer step and emits projection effects that trigger health-sync watchers far more often than meaningful health state actually changes. After this refactor, health hysteresis recomputation is interval-throttled, and the startup watcher only runs websocket health sync when a reducer-projected health fingerprint changes (or on explicit forced transitions). Users should see unchanged websocket behavior, but with lower redundant recompute and store-write pressure during burst websocket traffic.

## Progress

- [x] (2026-02-16 16:05Z) Reviewed `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/RELIABILITY.md`, and target websocket reducer/watcher/health runtime files.
- [x] (2026-02-16 16:06Z) Authored this active ExecPlan with concrete scope, validation gates, and acceptance criteria.
- [x] (2026-02-16 16:20Z) Implemented interval-throttled health hysteresis refresh, reducer health fingerprint projection, and refresh metadata in `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs`.
- [x] (2026-02-16 16:21Z) Added health fingerprint plumbing through stream projection effect handling in `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs` and runtime defaults in `/hyperopen/src/hyperopen/websocket/client.cljs`.
- [x] (2026-02-16 16:23Z) Updated websocket startup watcher sync gating and runtime adapter plumbing in `/hyperopen/src/hyperopen/startup/watchers.cljs`, `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs`, and `/hyperopen/src/hyperopen/app/bootstrap.cljs`.
- [x] (2026-02-16 16:24Z) Added health-runtime short-circuit path for unchanged projected fingerprints in `/hyperopen/src/hyperopen/websocket/health_runtime.cljs`.
- [x] (2026-02-16 16:26Z) Added regression tests in `/hyperopen/test/hyperopen/websocket/application/runtime_reducer_test.cljs`, `/hyperopen/test/hyperopen/websocket/health_runtime_test.cljs`, and new `/hyperopen/test/hyperopen/startup/watchers_test.cljs`; updated touched projection-shape tests.
- [x] (2026-02-16 16:28Z) Ran `npm test` and fixed two reducer-test regressions by preserving immediate non-market decoded-envelope refresh while keeping market-path throttling.
- [x] (2026-02-16 16:32Z) Ran required gates: `npm run check`, `npm test`, and `npm run test:websocket` (all passed).
- [x] (2026-02-16 16:34Z) Moved this plan to `/hyperopen/docs/exec-plans/completed/` after acceptance passed and living sections were updated.

## Surprises & Discoveries

- Observation: The heaviest sync path is not only reducer hysteresis recomputation; repeated `stream-runtime` atom writes trigger `install-websocket-watchers!` health sync calls, which then call `get-health-snapshot` and `derive-health-snapshot` repeatedly.
  Evidence: `/hyperopen/src/hyperopen/startup/watchers.cljs` runs `sync-websocket-health!` on every `stream-runtime` watch notification; `/hyperopen/src/hyperopen/websocket/health_runtime.cljs` calls `get-health-snapshot` at function entry.
- Observation: `sync-websocket-health!` already dedupes store writes by fingerprint, but still performs snapshot computation and eligibility checks before that dedupe.
  Evidence: `should-sync?` check happens after `health` acquisition and transition checks in `/hyperopen/src/hyperopen/websocket/health_runtime.cljs`.
- Observation: Transport hysteresis may keep stable freshness at `:offline` while pending transitions evolve; forcing assertions on stable freshness alone can mask whether interval refresh logic ran.
  Evidence: During test iteration, `:freshness-pending-status` moved from `:live` to `:delayed` across interval refreshes while stable `:freshness` stayed `:offline` under hysteresis rules.
- Observation: For non-market decoded envelopes (for example OMS/account topics), skipping immediate refresh changed stream status expectations from `:n-a` to `:idle`.
  Evidence: `event-driven-stream-remains-neutral-without-threshold-test` failed until non-market decoded-envelope branches were set to force health refresh.

## Decision Log

- Decision: Throttle hysteresis refresh in reducer using a dedicated projection interval (fallback to health tick interval) rather than recomputing on every message.
  Rationale: This directly addresses the high-frequency reducer hot path while preserving deterministic timer-driven health transitions.
  Date/Author: 2026-02-16 / Codex
- Decision: Project a reducer-owned websocket health fingerprint into `stream-runtime`, and gate watcher sync calls on fingerprint transition.
  Rationale: This gives an O(1) watch-time comparison so health sync no longer runs on metrics-only or no-op projection updates.
  Date/Author: 2026-02-16 / Codex
- Decision: Keep explicit forced sync on connection status transitions.
  Rationale: Status transition responsiveness is a user-visible diagnostic invariant and should not wait for interval gating.
  Date/Author: 2026-02-16 / Codex
- Decision: Preserve immediate health refresh for non-market decoded envelopes while throttling market decoded-envelope refresh.
  Rationale: Market traffic drives the hot path; non-market streams are lower volume and need prompt status projection semantics used by existing diagnostics tests.
  Date/Author: 2026-02-16 / Codex

## Outcomes & Retrospective

Implementation completed for the scoped medium-impact websocket health projection issue.

What was delivered:

- Reducer health refresh is now interval-aware with explicit refresh metadata and projected health fingerprint storage.
- Stream runtime projections now include `:health-fingerprint`.
- Websocket startup stream watcher now calls health sync only when projected health fingerprint changes.
- Connection watcher still forces sync on status transitions for immediate responsiveness.
- Health runtime can short-circuit before expensive snapshot recomputation when the projected fingerprint matches prior runtime fingerprint.
- Non-market decoded envelope branches retain immediate refresh behavior to preserve existing diagnostics semantics.

Validation evidence:

- `npm run check`: pass.
- `npm test`: pass (`1073` tests, `4877` assertions, `0` failures, `0` errors).
- `npm run test:websocket`: pass (`122` tests, `495` assertions, `0` failures, `0` errors).

Residual risk:

- `stream-runtime` projection effects still emit each step for metrics/runtime visibility, so watch callbacks still fire; the expensive health sync path is now gated by reducer-projected fingerprint transitions.

## Context and Orientation

The websocket runtime flow spans these files:

- `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs`: pure reducer state transitions, hysteresis refresh, and projection effect emission.
- `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`: interprets projection effects into public atoms (`connection-state`, `stream-runtime`).
- `/hyperopen/src/hyperopen/startup/watchers.cljs`: watches those atoms and triggers app-store projection/sync side effects.
- `/hyperopen/src/hyperopen/websocket/health_runtime.cljs`: computes app websocket health snapshot, applies fingerprint dedupe, auto-recover, and diagnostics transitions.

In this plan, “health fingerprint” means the minimal set of websocket health fields that materially affect UI diagnostics state: transport state/freshness, group worst-status rollups, and group gap-detected booleans.

Scope is intentionally limited to websocket health projection cadence and sync triggering. Public websocket client API surfaces remain unchanged.

## Plan of Work

Milestone 1 updates reducer health projection cadence. The reducer will stop recalculating stream/transport hysteresis on every step and instead refresh on interval/forced transitions, while persisting the latest projected health fingerprint in runtime state.

Milestone 2 threads that fingerprint through stream projection effects so infrastructure can expose it on `stream-runtime` without changing architecture boundaries or introducing side effects into the reducer.

Milestone 3 updates startup watchers and health runtime sync entry to avoid expensive health-snapshot recomputation unless the projected fingerprint changes or a forced status transition occurs.

Milestone 4 adds regression tests validating throttled reducer behavior and fingerprint-gated sync behavior. Tests will assert deterministic behavior under repeated events and ensure transition responsiveness remains intact.

Milestone 5 runs required quality gates and records outputs before moving this plan to completed.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs`:
   - add projection interval lookup and forced refresh message-type policy,
   - gate hysteresis refresh by interval/forced transition,
   - compute/store projected health fingerprint,
   - include projected fingerprint in stream projection payload.
2. Edit `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs` to carry fingerprint into `stream-runtime` atom updates.
3. Edit `/hyperopen/src/hyperopen/websocket/client.cljs` default/reset `stream-runtime` shape to include fingerprint key.
4. Edit `/hyperopen/src/hyperopen/startup/watchers.cljs` and `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs`:
   - only invoke health sync from stream watcher on fingerprint change,
   - pass projected fingerprint through optional args,
   - keep forced sync on status transitions.
5. Edit `/hyperopen/src/hyperopen/websocket/health_runtime.cljs` to short-circuit before snapshot recomputation when projected fingerprint is unchanged and `force?` is false.
6. Add or update tests in:
   - `/hyperopen/test/hyperopen/websocket/application/runtime_reducer_test.cljs`
   - `/hyperopen/test/hyperopen/websocket/health_runtime_test.cljs`
   - `/hyperopen/test/hyperopen/startup/watchers_test.cljs` (new)
   - plus touched projection shape tests as needed.
7. Run required gates:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

Expected command outcomes:

- `npm run check` exits 0.
- `npm test` exits 0 with no failures/errors.
- `npm run test:websocket` exits 0 with no failures/errors.

## Validation and Acceptance

Acceptance is met when all of the following are true:

- Reducer no longer performs full hysteresis recompute on every websocket step during burst envelope traffic; recompute is interval/forced-transition driven.
- `stream-runtime` carries a projected health fingerprint updated by reducer projection cadence.
- Startup websocket watcher calls `sync-websocket-health!` only when projected health fingerprint changes (plus explicit forced status transitions).
- Health runtime sync can skip expensive snapshot work when passed an unchanged projected fingerprint.
- Required validation gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

All edits are code-only and repeatable. If a change causes regression, rollback can be done safely by reverting touched files and rerunning tests; no migrations or destructive operations are involved. Validation commands are safe to rerun.

## Artifacts and Notes

Touched files:

- `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs`
- `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`
- `/hyperopen/src/hyperopen/startup/watchers.cljs`
- `/hyperopen/src/hyperopen/websocket/health_runtime.cljs`
- `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs`
- `/hyperopen/src/hyperopen/websocket/client.cljs`
- `/hyperopen/src/hyperopen/app/bootstrap.cljs`
- `/hyperopen/test/hyperopen/websocket/application/runtime_reducer_test.cljs`
- `/hyperopen/test/hyperopen/websocket/health_runtime_test.cljs`
- `/hyperopen/test/hyperopen/startup/watchers_test.cljs`

Evidence to capture:

- New tests proving fingerprint-gated sync and throttled health recomputation.
- Required gate command results.

## Interfaces and Dependencies

Stable interfaces to preserve:

- `/hyperopen/src/hyperopen/websocket/client.cljs` public API (`init-connection!`, `disconnect!`, `force-reconnect!`, `register-handler!`, `send-message!`, `get-health-snapshot`).
- Runtime reducer message/effect algebra in `/hyperopen/src/hyperopen/websocket/domain/model.cljs`.

Dependencies used:

- Existing websocket health derivation logic in `/hyperopen/src/hyperopen/websocket/health.cljs`.
- Existing websocket health fingerprint semantics in `/hyperopen/src/hyperopen/websocket/health_projection.cljs` (kept semantically aligned).

Plan revision note: 2026-02-16 16:06Z - Initial plan created before implementation after hotspot and invariant review.
Plan revision note: 2026-02-16 16:33Z - Updated living sections after implementation, regression fixes, and successful required validation gates.
