# WebSocket Market Projection Burst Telemetry and Regression Guardrails

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, websocket market projection bursts will be observable with concrete queue and flush measurements instead of implicit behavior. A contributor (or on-call developer) will be able to answer: how deep the coalescing queue got, how many updates were overwritten in-frame, and how long flushes took (including p95) for each store.

You will be able to verify this by running websocket tests and inspecting deterministic telemetry assertions, then capturing a debug snapshot in dev that includes market projection runtime stats and flush telemetry events.

## Progress

- [x] (2026-02-26 01:10Z) Reviewed `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and active websocket ExecPlans for required structure and rigor.
- [x] (2026-02-26 01:11Z) Audited current implementation and tests in `/hyperopen/src/hyperopen/websocket/market_projection_runtime.cljs`, `/hyperopen/test/hyperopen/websocket/market_projection_runtime_test.cljs`, `/hyperopen/src/hyperopen/websocket/orderbook.cljs`, and `/hyperopen/src/hyperopen/websocket/active_asset_ctx.cljs`.
- [x] (2026-02-26 01:12Z) Authored this ExecPlan with milestones, telemetry payload contract, and acceptance gates.
- [x] (2026-02-26 01:38Z) Implemented market projection queue/flush telemetry state, bounded percentile window tracking, and flush event emission contract in `/hyperopen/src/hyperopen/websocket/market_projection_runtime.cljs`.
- [x] (2026-02-26 01:44Z) Added deterministic websocket telemetry regression tests (queue depth, overwrite counters, flush duration, p95 window behavior, per-flush overwrite reset) in `/hyperopen/test/hyperopen/websocket/market_projection_runtime_test.cljs`.
- [x] (2026-02-26 01:47Z) Surfaced market projection telemetry in debug snapshot payload via `/hyperopen/src/hyperopen/telemetry/console_preload.cljs`.
- [x] (2026-02-26 01:56Z) Ran required gates and captured green evidence for `npm run check`, `npm test`, and `npm run test:websocket`.

## Surprises & Discoveries

- Observation: Market projection coalescing currently has good behavioral tests (coalescing and frame scheduling) but no direct telemetry assertions for queue depth or flush latency.
  Evidence: `/hyperopen/test/hyperopen/websocket/market_projection_runtime_test.cljs` verifies write counts and coalescing correctness only.

- Observation: The runtime currently stores only `:pending` and `:frame-handle`, so there is no built-in place to compute p95 flush duration or overwritten-key counts.
  Evidence: `/hyperopen/src/hyperopen/websocket/market_projection_runtime.cljs` keeps `market-projection-runtime` state as `{:stores {store {:pending ... :frame-handle ...}}}`.

- Observation: Existing websocket telemetry uses event names under `:websocket/...` and is exercised in tests, so adding market projection events should follow that namespace style.
  Evidence: `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs` emits `:websocket/runtime-log` and `:websocket/dead-letter`, with tests in `/hyperopen/test/hyperopen/websocket/infrastructure/runtime_effects_test.cljs`.

- Observation: Queueing call sites are concentrated and already route through `queue-market-projection!`, so instrumentation at that boundary will cover orderbook and active asset bursts without touching many call sites.
  Evidence: `/hyperopen/src/hyperopen/websocket/orderbook.cljs` and `/hyperopen/src/hyperopen/websocket/active_asset_ctx.cljs` both call `queue-market-projection!` for store projection writes.

- Observation: `start-engine-records-runtime-messages-and-effects-test` in websocket runtime engine suite was timing-sensitive with `setTimeout 0` and could assert before async go-loops drained mailbox/effects channels in this environment.
  Evidence: Repeated failures at `/hyperopen/test/hyperopen/websocket/application/runtime_engine_test.cljs` lines 36-39 until assertion delay was increased to `10ms`.

## Decision Log

- Decision: Instrument at `/hyperopen/src/hyperopen/websocket/market_projection_runtime.cljs` instead of instrumenting each caller.
  Rationale: This is the single coalescing boundary for burst writes; adding telemetry once avoids drift and keeps behavior consistent across topics.
  Date/Author: 2026-02-26 / Codex

- Decision: Emit one flush telemetry event per flush and keep queue observability in aggregated counters/watermarks rather than emitting one event per queue call.
  Rationale: Per-queue events would flood the dev event ring (`max-events` 2000) during bursts and reduce signal quality.
  Date/Author: 2026-02-26 / Codex

- Decision: Add deterministic injection seams (`now-ms-fn`, `emit-fn`) for telemetry paths in `queue-market-projection!` and `flush-store-updates!`.
  Rationale: This allows repeatable tests for latency and event payloads without relying on wall clock timing.
  Date/Author: 2026-02-26 / Codex

- Decision: Provide a runtime stats snapshot accessor for debug tooling instead of exposing mutable runtime internals.
  Rationale: Read-only snapshots preserve encapsulation and keep diagnostics explicit.
  Date/Author: 2026-02-26 / Codex

## Outcomes & Retrospective

- Milestone 1 outcome: Runtime state now tracks per-store telemetry (`queued-total`, `overwrite-total`, `flush-count`, `max-pending-depth`, bounded flush/queue-wait samples, p95 summaries) and exposes read-only diagnostics through `market-projection-telemetry-snapshot`.
- Milestone 2 outcome: `queue-market-projection!` and `flush-store-updates!` now accept optional deterministic seams (`:now-ms-fn`, `:emit-fn`) and emit `:websocket/market-projection-flush` with required payload keys (`store-id`, `pending-count`, `overwrite-count`, `flush-duration-ms`, `queue-wait-ms`, `flush-count`, `max-pending-depth`, `p95-flush-duration-ms`).
- Milestone 3 outcome: Burst-focused telemetry regressions are covered with deterministic tests in `market_projection_runtime_test` validating queue depth watermarks, overwrite accounting, flush duration telemetry, p95 windowing, and per-flush overwrite reset behavior.
- Milestone 4 outcome: Debug snapshot output now includes `:websocket :market-projection-telemetry`, and all required gates passed.
- Validation evidence:
  - `npm run test:websocket` (pass, 152 tests / 691 assertions, 0 failures, 0 errors).
  - `npm run check` (pass; docs/test/hiccup lint and app/test compiles green).
  - `npm test` (pass, 1382 tests / 6829 assertions, 0 failures, 0 errors).

## Context and Orientation

In this repository, market websocket payloads are coalesced by key and flushed to the app store on the next animation frame. Coalescing means “keep only the latest update for each key until flush time,” which reduces redundant writes under burst traffic.

The coalescing runtime lives in `/hyperopen/src/hyperopen/websocket/market_projection_runtime.cljs`:

- `queue-market-projection!` stores pending updates by `coalesce-key` and schedules one frame callback.
- `flush-store-updates!` drains pending updates and applies them in deterministic key order.

Two high-frequency call sites use this runtime:

- `/hyperopen/src/hyperopen/websocket/orderbook.cljs` for `[:orderbook coin]` updates.
- `/hyperopen/src/hyperopen/websocket/active_asset_ctx.cljs` for `[:active-asset-ctx coin]` updates.

Current gap: coalescing correctness is tested, but operational observability is missing. There is no native queue-depth history, overwrite counter, flush-duration metric, or percentile summary. When regressions happen (for example, a flush starts taking too long under burst traffic), there is no direct signal beyond subjective UI lag.

For this plan:

- “Queue depth” means number of pending coalesce keys waiting to flush.
- “Overwrite count” means number of queued updates that replaced an existing pending key before flush.
- “Flush duration” means elapsed time spent in `flush-store-updates!` applying pending updates.
- “p95 flush duration” means the 95th percentile across a bounded rolling window of recent flush durations.

## Plan of Work

### Milestone 1: Add Telemetry State Model and Read-Only Snapshot API

Extend `market-projection-runtime` state to track per-store telemetry counters and bounded latency samples without changing coalescing semantics. Add helper functions for bounded sample retention and percentile computation, then expose a read-only snapshot accessor.

At the end of this milestone, contributors can call a new snapshot function and see queue/flush counters for each store, even before event emission is wired.

### Milestone 2: Emit Queue/Flush Telemetry at Runtime Boundary

Wire telemetry updates inside `queue-market-projection!` and `flush-store-updates!` with deterministic dependency injection options for tests:

- Queue path updates counters (`queued-total`, `overwrite-total`, `max-pending-depth`) and last-enqueue timestamps.
- Flush path computes `pending-count`, `flush-duration-ms`, and queue-wait latency (time from frame scheduling to flush start), updates percentile windows, and emits `:websocket/market-projection-flush`.

Event payload must include store identifier, pending count, overwrite count for that flush window, flush duration, queue wait, and updated summary stats (`max-pending-depth`, `flush-count`, p95).

At the end of this milestone, a burst flush produces deterministic telemetry events and updated runtime stats.

### Milestone 3: Add Regression-Focused Tests and Burst Scenario Coverage

Extend `/hyperopen/test/hyperopen/websocket/market_projection_runtime_test.cljs` to validate telemetry behavior under controlled burst inputs:

- Overwrite behavior increments overwrite counters correctly.
- Flush events include expected payload fields and values.
- Bounded sample windows produce expected percentile values.
- Multiple frame cycles preserve counters and reset per-flush transient tallies correctly.

Use injected `now-ms-fn` and `emit-fn` plus controlled scheduled callbacks so tests are deterministic and do not rely on real animation frame timing.

At the end of this milestone, observability behavior is protected by regression tests rather than only manual inspection.

### Milestone 4: Surface Diagnostics and Finalize Validation

Expose market projection telemetry snapshot in debug snapshot output (`/hyperopen/src/hyperopen/telemetry/console_preload.cljs`) so engineers can export a single artifact that includes coalescing stats alongside existing websocket runtime data.

Then run full required gates and capture evidence. Update reliability/docs language only if needed to codify the new observability contract for burst coalescing.

At the end of this milestone, developers can inspect both live events and snapshot summaries, and all mandatory gates are green.

## Concrete Steps

From `/hyperopen`:

1. Implement telemetry state and snapshot helpers in market projection runtime.

   Edit `/hyperopen/src/hyperopen/websocket/market_projection_runtime.cljs`:

   - Add per-store telemetry fields inside runtime state.
   - Add private helpers for bounded sample windows and percentile calculation.
   - Add a public read-only accessor (for example `market-projection-telemetry-snapshot`).

   Run:

       npm run test:websocket

   Expected signal:

   - Existing websocket tests still pass or only fail where new telemetry tests are not yet updated.
   - No behavior change in coalescing tests.

2. Add queue/flush telemetry emission and injected dependencies.

   Edit `/hyperopen/src/hyperopen/websocket/market_projection_runtime.cljs`:

   - Add optional `:now-ms-fn` and `:emit-fn` keys to queue/flush args.
   - Emit `:websocket/market-projection-flush` with stable payload keys.
   - Keep default behavior backward-compatible for existing callers.

   Run:

       npm run test:websocket

   Expected signal:

   - Existing coalescing tests still validate single-frame write behavior.
   - New telemetry tests can assert event payload values.

3. Add deterministic burst telemetry tests.

   Edit `/hyperopen/test/hyperopen/websocket/market_projection_runtime_test.cljs`:

   - Add tests for overwrite counts, max queue depth, flush duration, and p95 computation.
   - Use injected clock and emit function stubs for repeatable assertions.

   Run:

       npm run test:websocket

   Expected signal:

   - Test output includes new passing tests in `hyperopen.websocket.market-projection-runtime-test`.
   - Failures should be actionable payload/counter mismatches, not timing flakes.

4. Expose debug snapshot stats and run required gates.

   Edit `/hyperopen/src/hyperopen/telemetry/console_preload.cljs` to include market projection telemetry snapshot under websocket diagnostics payload.

   Run:

       npm run check
       npm test
       npm run test:websocket

   Expected signal:

   - All required gates pass.
   - Debug snapshot JSON includes market projection telemetry summary fields.

## Validation and Acceptance

Acceptance is complete when all conditions below are true.

1. Market projection runtime exposes read-only per-store telemetry summary including at least:
   - total queued updates,
   - total overwritten updates,
   - flush count,
   - max pending depth,
   - p95 flush duration (bounded window).
2. Each flush emits a telemetry event `:websocket/market-projection-flush` with deterministic payload fields for queue/flush observability.
3. Existing coalescing semantics remain unchanged:
   - one frame schedule per frame window,
   - latest update wins per coalesce key,
   - deterministic flush ordering.
4. Burst-focused telemetry tests pass and would fail if counters or payload values regress.
5. Debug snapshot output includes market projection telemetry summary.
6. Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

This plan is safe to apply incrementally:

- Telemetry additions are additive and should not alter caller contracts.
- Injection seams are optional defaults, so existing call sites remain valid.
- Tests can be rerun repeatedly without environment mutation beyond normal build artifacts.

If a telemetry change causes regressions:

- Keep new tests and snapshot accessors.
- Temporarily disable only event emission (not counters) behind injected `emit-fn` defaults while preserving coalescing behavior.
- Re-enable emission once payload shape and timing assumptions are fixed.

If percentile logic proves contentious, retain raw bounded samples and `max`/`avg` first, then add percentile after deterministic tests are stable.

## Artifacts and Notes

Primary files expected to change:

- `/hyperopen/src/hyperopen/websocket/market_projection_runtime.cljs`
- `/hyperopen/test/hyperopen/websocket/market_projection_runtime_test.cljs`
- `/hyperopen/src/hyperopen/telemetry/console_preload.cljs`

Optional docs updates if contract wording is tightened:

- `/hyperopen/docs/RELIABILITY.md`

Evidence to capture during implementation:

- Test output showing telemetry assertions passing in `market-projection-runtime-test`.
- Example debug snapshot excerpt with market projection telemetry fields.
- Gate outputs for:
  - `npm run check`
  - `npm test`
  - `npm run test:websocket`

## Interfaces and Dependencies

Dependencies already in repo:

- `hyperopen.platform/now-ms` for timing.
- `hyperopen.telemetry/emit!` for event emission.
- Existing queue coalescing APIs in `market_projection_runtime`.

Interface adjustments to introduce:

- `queue-market-projection!` supports optional instrumentation keys:
  - `:now-ms-fn` (defaults to `platform/now-ms`)
  - `:emit-fn` (defaults to `telemetry/emit!`)
- `flush-store-updates!` supports matching optional instrumentation keys for deterministic tests.
- New public read-only accessor in `market_projection_runtime` returning telemetry snapshots (must not expose mutable internal atoms directly).

Telemetry event contract:

- Event name: `:websocket/market-projection-flush`
- Required payload keys:
  - store identifier (string form),
  - `:pending-count`,
  - `:overwrite-count`,
  - `:flush-duration-ms`,
  - `:queue-wait-ms`,
  - `:flush-count`,
  - `:max-pending-depth`,
  - `:p95-flush-duration-ms`.

No new external libraries are required.

Plan revision note: 2026-02-26 01:12Z - Initial ExecPlan created to close websocket market projection burst observability gap with queue/flush telemetry, deterministic tests, and required-gate validation.
