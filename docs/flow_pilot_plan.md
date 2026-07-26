---
owner: platform
status: draft
last_reviewed: 2026-02-20
source_of_truth: false
---

# Flow Pilot Plan (Minimal + Reversible)

## Chosen pilot subsystem
**Pilot target: topic router + handler fan-out** in `/hyperopen/src/hyperopen/websocket/application/runtime.cljs`.

Why this one:
- Stable topology (ingress bus -> route by topic -> per-topic handler workers).
- Clear lifecycle points (`register-topic-handler!`, `stop-router!`, runtime start/stop).
- Existing pain points are mainly observability + supervision, which are Flow-shaped.
- Can be swapped behind existing `IMessageRouter` boundary with no public API break.

## Important feasibility gate
This pilot requires Flow to be available in CLJS runtime. Current audit found it is not.

**Gate 0 (must pass before coding Flow path):** confirm published core.async artifact includes `cljs/core/async/flow.*` and compiles in this repo.

If Gate 0 fails, do not implement Flow code; implement only instrumentation and lifecycle hardening in the current router path.

## External API contract to preserve
The pilot must keep these contracts unchanged:
- `route-domain-message!`
- `register-topic-handler!`
- `stop-router!`
- Runtime entrypoints that use router callbacks in `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:183`

## Proposed pilot architecture (1–3 processes)

```
[Process A: ingress]
  in:  [:router :in/envelope]
  out: [:router :out/by-topic]
  work: normalize envelope + emit report counters

[Process B: topic route]
  in:  [:router :in/by-topic]
  out: [:router :out/handler]
  work: route to topic-specific outputs/channels

[Process C: handler executor]
  in:  [:router :in/handler]
  out: [:router :out/report] [:router :out/error]
  work: invoke registered handler-fn, capture latency/errors
```

Observability outputs:
- report stream: queue depth snapshots, handler latency, drop counts, last-message timestamps.
- error stream: structured handler exceptions with topic + payload metadata.

Lifecycle hooks:
- Start from runtime startup path (`start-runtime!`).
- Stop from runtime teardown path (`stop-runtime!`).
- Optional pause/resume can be wired later to visibility transitions.

## Feature flag and rollback

Flag:
- Add `:ws-flow-router-pilot?` (default `false`) to websocket config in `/hyperopen/src/hyperopen/websocket/client.cljs`.

Enable path:
- In `make-router`, choose Flow-backed router only when flag is true and Flow runtime is available.

Rollback path:
- Set `:ws-flow-router-pilot?` to `false`.
- Keep legacy `AsyncTopicRouter` implementation intact.
- No data migration required.

## Step-by-step implementation plan (small PR slices)

1. **PR1: Add pilot guard + config plumbing (no behavior change)**
   - Add config flag and router factory branch.
   - Add compile-time/runtime capability check helper.
   - Keep legacy router selected by default.

2. **PR2: Add Flow router adapter shell (no production enablement)**
   - New namespace: `/hyperopen/src/hyperopen/websocket/application/flow_router_pilot.cljs` (or `.cljc` only if needed for platform guards).
   - Implement same `IMessageRouter` API surface.
   - Expose `:report-chan` / `:error-chan` adapter outputs.

3. **PR3: Implement minimal graph (A/B/C processes)**
   - Route by topic.
   - Call existing handler fns.
   - Emit report/error events.

4. **PR4: Lifecycle wiring**
   - Start/stop in runtime lifecycle.
   - Ensure stop closes report/error channels and detaches all topic routes.

5. **PR5: Parity + observability tests**
   - Add parity tests vs legacy router behavior.
   - Add diagnostics assertions for report/error streams.

6. **PR6: Controlled rollout (still default off)**
   - Enable in targeted dev/test profile only.
   - Collect stability/perf evidence.

## Testing plan

### Unit tests
- Flow router route correctness by topic.
- Handler replacement semantics.
- Structured error emission on handler throw.
- Stop lifecycle closes channels and prevents post-stop dispatch.

Suggested file additions:
- `/hyperopen/test/hyperopen/websocket/application/flow_router_pilot_test.cljs`

### Integration tests
- Existing runtime tests run with feature flag off and on.
- Verify same ordering guarantees for market/lossless paths.
- Verify no duplicate handler invocation after re-register.

Commands/gates:
- `npm run check`
- `npm test`
- `npm run test:websocket`

## Success criteria (measurable)

1. **Correctness parity**
   - 0 regressions in existing websocket runtime tests with pilot disabled.
   - Parity suite passes with pilot enabled.

2. **Lifecycle robustness**
   - No residual handler routes/channels after `stop-runtime!` in tests.
   - No duplicate handler execution across repeated start/stop cycles.

3. **Observability**
   - Report stream provides per-topic backlog/latency snapshots without ad-hoc `println` debugging.
   - Error stream captures handler faults with topic and message metadata.

4. **Maintainability**
   - Router glue code shrinks in `runtime.cljs`.
   - Boilerplate for handler supervision/error reporting is centralized.

## If Gate 0 fails (current expected outcome)
Implement a **Flow-inspired pilot on existing core.async router** instead:
- Add report/error channels and router instrumentation to current `AsyncTopicRouter`.
- Add explicit lifecycle teardown for listeners/timers in existing runtime.
- Keep same feature flag and adapter seam so true Flow can be swapped in later with minimal diff.
