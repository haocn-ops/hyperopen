---
owner: platform
status: draft
last_reviewed: 2026-02-20
source_of_truth: false
---

# Flow Fit Analysis (`clojure.core.async.flow`) for Hyperopen (CLJS)

## Executive recommendation
**Decision: Don’t adopt Flow in the browser runtime now.**

Reason: this codebase is ClojureScript/browser-only for runtime paths, while `clojure.core.async.flow` is currently delivered as JVM Clojure namespaces (no `cljs/core/async/flow` in published artifacts). Adopting now would require a platform shift, not a local refactor.

## CLJS feasibility reality check

### What we run today
- Current dependency pin: `org.clojure/core.async "1.6.681"` in `/hyperopen/deps.edn:8`.
- Runtime async usage is CLJS (`cljs.core.async`) in:
  - `/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs`
  - `/hyperopen/src/hyperopen/websocket/application/runtime.cljs`

### What Flow currently ships as
- Latest published core.async observed during audit: `1.9.829-alpha2` (alpha track).
- `1.9.829-alpha2` contains:
  - `clojure/core/async/flow.clj`
  - `clojure/core/async/flow/impl/*.clj`
- It does **not** contain `cljs/core/async/flow.cljs`.

Implication: there is no direct Flow runtime to require from CLJS browser code today.

## Candidate subsystems (flow-shaped topology)

## 1) WebSocket runtime core (best conceptual fit, blocked technically)

Current topology:

```
commands + transport/lifecycle events
  -> mailbox-ch
    -> reducer (single-writer state transition)
      -> effects-ch
        -> interpreter
          -> socket/timer/lifecycle/router/projection side effects
```

Key nodes/processes:
- Reducer loop: `/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs:34`
- Effect interpreter loop: `/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs:49`
- Effect handlers: `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`

Channels:
- `mailbox-ch` fixed buffer (`4096`) and `effects-ch` fixed buffer (`4096`) from `/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs:25`.

Fan-in/fan-out:
- Fan-in at mailbox from command and transport events via `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:228` and `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:233`.
- Fan-out through effect interpreter branches (`:fx/socket-*`, `:fx/timer-*`, `:fx/router-*`, projection effects).

Lifecycle boundaries:
- Start: `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:183`
- Stop: `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:238`
- Public manager start: `/hyperopen/src/hyperopen/websocket/client.cljs:253`

Pain points today:
- Listener teardown gap (install without remove).
- Limited queue observability.
- CPU-heavy parse/hydration on event loop.

Why Flow helps:
- Explicit graph topology and centralized lifecycle (`start/stop/pause/resume`).
- Built-in report/error channels map directly to current observability gaps.
- Process contracts can isolate reducer/interpreter concerns with formal wiring.

Why Flow doesn’t help (today):
- Not loadable in CLJS browser runtime.

Risks:
- Alpha API churn.
- Forced dependency/platform upgrade cost.

Migration strategy if/when CLJS support appears:
- Keep existing external API (`publish-command!`, `publish-transport-event!`, `stop-runtime!`) and swap internals under feature flag.
- Start with reducer+interpreter only; keep existing transport/router handlers unchanged.
- Interop via existing core.async channels at the boundary.

## 2) Async topic router + topic handlers (best pilot target when feasible)

Current topology:

```
router-dispatch-envelope effect
  -> bus-ch (pub :topic)
    -> topic ch (per topic, market = sliding, others = fixed)
      -> handler go-loop
        -> module handler fn (trades/orderbook/user/webdata2/activeAssetCtx)
```

Key nodes/processes:
- Router + per-topic loops: `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:23`
- Handler registration/replace: `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:27`

Channels:
- `bus-ch` fixed buffer `4096` (`/hyperopen/src/hyperopen/websocket/application/runtime.cljs:62`)
- Topic channels buffered per tier (`/hyperopen/src/hyperopen/websocket/application/runtime.cljs:33`)

Fan-in/fan-out:
- Fan-out via `pub/sub` at `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:63`.

Lifecycle boundaries:
- Register/replace handler: `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:27`
- Router stop: `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:51`

Pain points today:
- Slow handlers can create backlog and event-loop pressure.
- No per-topic lag/depth visibility.
- Errors are telemetry-only, not structured runtime faults.

Why Flow helps:
- Natural fit as graph of `ingress -> route -> handlers`.
- Report/error channels provide centralized monitoring.
- Process-level pause/resume could help controlled diagnostics or backfills.

Why Flow doesn’t help (today):
- Same CLJS runtime blocker.

Risks:
- Router rewrite can affect ordering guarantees if not parity-tested.

Migration strategy if/when feasible:
- Keep `IMessageRouter` API stable.
- Build a Flow-backed `IMessageRouter` implementation selected by config flag.
- Run dual-path parity tests against current router behavior.

## 3) Timer + lifecycle supervision loop (moderate fit)

Current topology:

```
reducer emits timer/lifecycle effects
  -> runtime_effects interpreter manages io-state timers/listeners
    -> scheduler callbacks dispatch runtime messages back into engine
```

Key nodes/processes:
- Reducer timer effects: `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs:238`
- Interpreter timer/lifecycle handlers: `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs:129`

Fan-in/fan-out:
- Fan-in from all timer callback events (`:timer/retry`, `:timer/watchdog`, `:timer/health`, `:timer/market-flush`) into runtime event dispatch.

Lifecycle boundaries:
- Runtime start/stop + reducer command transitions.

Pain points today:
- Lifecycle listener uninstall missing.
- Active timer/listener state not visible from public diagnostics.

Why Flow helps:
- Supervision and transition hooks map well to timer/listener lifecycle concerns.

Why Flow doesn’t help (today):
- CLJS support gap.

Risks:
- Rewriting scheduler integration for alpha API may add churn without user-visible benefit.

Migration strategy if/when feasible:
- Keep timer keys/effects contract stable.
- Replace only supervisor internals first, preserve reducer message algebra.

## Summary matrix

| Candidate | Complexity reduction potential | Current feasibility in this repo | Recommended action |
| --- | --- | --- | --- |
| WebSocket runtime core | High | Blocked | Defer; implement non-Flow quick wins now |
| Topic router + handlers | High (for pilot) | Blocked | Prepare pilot plan, keep feature-flag design ready |
| Timer/lifecycle supervision | Medium | Blocked | Fix lifecycle gaps in existing core.async path first |

## Final recommendation
- **Now:** Do not migrate production CLJS runtime to Flow.
- **Near-term:** Apply non-Flow improvements from `/hyperopen/docs/async_audit.md` (lifecycle teardown, enqueue-failure signaling, queue/latency observability).
- **Trigger to revisit:** only when a published core.async artifact includes CLJS Flow support (or an approved architecture move puts runtime on JVM side).
