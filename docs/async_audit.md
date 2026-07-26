---
owner: platform
status: draft
last_reviewed: 2026-02-20
source_of_truth: false
---

# Core.async Audit (CLJS Browser Runtime)

## Scope
This audit covers all `core.async` usage in `/hyperopen/src`, `/hyperopen/test`, and `/hyperopen/dev`.

Scan results:
- Namespaces requiring `cljs.core.async`: 2
- Namespaces requiring `cljs.core.async.macros`: 2
- `go-loop` sites: 3
- `go` sites: 0
- `alts!`, `timeout`, `pipeline`, `pipeline-async`, `mult`, `mix`: not used
- `pub/sub`: used in one router implementation

## Inventory of `core.async` usage

| Namespace | Usage pattern | Channels + buffer policy | Fan-in / fan-out primitives | Lifecycle / cancellation | Error handling | Risk notes |
| --- | --- | --- | --- | --- | --- | --- |
| `/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs` | Two `go-loop`s: mailbox reducer loop (`<!`, `>!`) and effect interpreter loop (`<!`) | `mailbox-ch` fixed buffer (default `4096`), `effects-ch` fixed buffer (default `4096`) at `/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs:25` and `/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs:26` | None | `stop!` closes both channels (`/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs:65`) | Reducer exceptions converted to `:fx/dead-letter` (`/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs:43`); interpreter exceptions only telemetry (`/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs:56`) | Backpressure can stall reducer loop on full `effects-ch`; enqueue failures are swallowed by `safe-put!` |
| `/hyperopen/src/hyperopen/websocket/application/runtime.cljs` | Router handler worker `go-loop` per topic (`<!`) at `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:38`; non-blocking ingress via `put!` | `bus-ch` fixed buffer (default `4096`) at `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:62`; per-topic `ch`: market uses `sliding-buffer` (default `64`), others fixed buffer `64` at `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:31` | `async/pub`, `async/sub`, `async/unsub` at `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:63`, `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:37`, `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:29` | `stop-router!` unsubscribes/closes all topic channels then closes `bus-ch` (`/hyperopen/src/hyperopen/websocket/application/runtime.cljs:51`); `stop-runtime!` clears timers/sockets, stops router and engine (`/hyperopen/src/hyperopen/websocket/application/runtime.cljs:238`) | Handler exceptions are emitted as telemetry only (`/hyperopen/src/hyperopen/websocket/application/runtime.cljs:47`) | Lifecycle listeners installed in effects interpreter are not removed on stop; observability channels exist as dead code (`create-runtime-channels`) |

### Unused/legacy channel factory
`create-runtime-channels` defines `metrics-ch` and `dead-letter-ch` with dropping buffers, but is not used by runtime startup (`/hyperopen/src/hyperopen/websocket/application/runtime.cljs:67`). Its current use is test-only contract coverage (`/hyperopen/test/hyperopen/websocket/application/runtime_test.cljs:103`).

## Correctness and performance audit (significant pipelines)

### 1) Engine mailbox -> reducer -> effects -> interpreter
Graph:

```
external dispatch!/events
  -> mailbox-ch (fixed 4096)
    -> reducer go-loop
      -> effects-ch (fixed 4096)
        -> interpreter go-loop
          -> transport/timer/router/projection side effects
```

Assessment:
- Leak risk: low for channels (both closed by `stop!`), but runtime itself is a long-lived singleton started from startup flow and rarely stopped in production (`/hyperopen/src/hyperopen/websocket/client.cljs:193`, `/hyperopen/src/hyperopen/startup/runtime.cljs:260`).
- Backpressure: reducer loop parks on `>!` when `effects-ch` is full (`/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs:41`), which can throttle ingress but also delay critical control messages.
- Queue overflow behavior: ingress uses non-blocking `put!` via `safe-put!` (`/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs:7`). In CLJS core.async, pending puts are capped (1024); exceeding this throws, then `safe-put!` returns `false` and drops signal silently.
- UI impact: effect interpreter executes CPU-heavy work inline, including JSON decode/hydration in message parsing (`/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs:201`, `/hyperopen/src/hyperopen/websocket/acl/hyperliquid.cljs:35`, `/hyperopen/src/hyperopen/websocket/acl/hyperliquid.cljs:55`). Under burst traffic this can monopolize the JS event loop.
- Ordering: deterministic single-writer reducer semantics are strong and align with reliability invariants.
- Error propagation: reducer failures become dead-letter effects; interpreter failures are telemetry-only and do not feed a structured runtime error stream.
- Observability: no runtime API for mailbox/effects depth, in-flight work, or handler latency.

### 2) Router bus -> pub/sub -> per-topic handler loops
Graph:

```
:fx/router-dispatch-envelope
  -> bus-ch (pub by :topic)
    -> topic channel per handler
      -> handler go-loop -> user/module handler fn
```

Assessment:
- Leak risk: channel cleanup is mostly correct (`unsub` + `close!` on replace/stop). However, close does not detach already-buffered messages; a replaced handler can still process buffered envelopes before exiting.
- Backpressure: market topics intentionally use sliding buffers (`/hyperopen/src/hyperopen/websocket/application/runtime.cljs:34`) which drops older messages; lossless topics use fixed buffers.
- UI impact: handlers run synchronously inside go-loop callbacks; several handlers perform expensive `swap!`/normalization work:
  - `/hyperopen/src/hyperopen/websocket/webdata2.cljs:61`
  - `/hyperopen/src/hyperopen/websocket/user.cljs:86`
  - `/hyperopen/src/hyperopen/websocket/trades.cljs:205`
- Ordering/concurrency: per-topic ordering is preserved inside each channel; cross-topic ordering is intentionally not guaranteed.
- Error propagation: handler exceptions are only logged to telemetry (`/hyperopen/src/hyperopen/websocket/application/runtime.cljs:47`).
- Observability: no counters for per-topic queue depth, lag, dropped-market messages, or handler execution time.

### 3) Timer/lifecycle feedback loop (effect interpreter + scheduler callbacks)
Graph:

```
reducer emits :fx/timer-* / :fx/lifecycle-install-listeners
  -> interpreter mutates io-state timers/listeners
    -> scheduler callback dispatches runtime msg
      -> reducer
```

Assessment:
- Leak risk: lifecycle event listeners are installed (`/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs:178`) but never removed in `stop-runtime!` (`/hyperopen/src/hyperopen/websocket/application/runtime.cljs:238`).
- Cleanup: timer cleanup is explicit and generally strong (`/hyperopen/src/hyperopen/websocket/application/runtime.cljs:243`).
- Observability: no public introspection of active timers/listeners beyond private `io-state` internals.

## Top 10 issues (ranked)

1. **Listener lifecycle leak across runtime restarts** (High)
   - `:fx/lifecycle-install-listeners` adds global listeners, but stop path does not remove them.
   - Code: `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs:162`, `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs:178`, `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:238`

2. **Potential silent message loss under channel pressure** (High)
   - `safe-put!` catches all enqueue failures and returns `false` without telemetry/dead-letter signaling.
   - Code: `/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs:7`, `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:16`

3. **Interpreter loop can block rendering under burst CPU load** (High)
   - JSON parse/hydration and envelope decode happen inline in effect loop.
   - Code: `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs:201`, `/hyperopen/src/hyperopen/websocket/acl/hyperliquid.cljs:35`, `/hyperopen/src/hyperopen/websocket/acl/hyperliquid.cljs:55`

4. **Handler loop executes heavy store transforms synchronously** (High)
   - Topic handler callbacks include large normalization/merge logic.
   - Code: `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:38`, `/hyperopen/src/hyperopen/websocket/webdata2.cljs:61`, `/hyperopen/src/hyperopen/websocket/user.cljs:86`, `/hyperopen/src/hyperopen/websocket/trades.cljs:205`

5. **Backpressure stalls are opaque** (Medium)
   - Reducer loop parks on full `effects-ch`; no depth/lag metrics exist.
   - Code: `/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs:41`

6. **Error channels are conceptual, not operational** (Medium)
   - `create-runtime-channels` defines metrics/dead-letter channels but runtime does not wire them.
   - Code: `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:67`, `/hyperopen/test/hyperopen/websocket/application/runtime_test.cljs:103`

7. **Handler replacement can process stale buffered messages with old handler** (Medium)
   - On re-register, old topic channel is closed but buffered items may still drain to old callback.
   - Code: `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:27`, `/hyperopen/src/hyperopen/websocket/application/runtime.cljs:38`

8. **Interpreter failures are telemetry-only, no structured supervision path** (Medium)
   - Exceptions are emitted as log events and otherwise dropped.
   - Code: `/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs:56`

9. **Observability is dev-mode gated** (Medium)
   - `telemetry/emit!` records only when `goog.DEBUG`, limiting production diagnosis.
   - Code: `/hyperopen/src/hyperopen/telemetry.cljs:14`, `/hyperopen/src/hyperopen/telemetry.cljs:56`

10. **Global singleton lifecycle is implicit and hard to reason about** (Low/Medium)
    - Runtime, router, and handler registry are `defonce` globals with no explicit app-level stop boundary except test reset.
    - Code: `/hyperopen/src/hyperopen/websocket/client.cljs:27`, `/hyperopen/src/hyperopen/websocket/client.cljs:46`, `/hyperopen/src/hyperopen/websocket/client.cljs:193`, `/hyperopen/src/hyperopen/websocket/client.cljs:320`

## Quick wins without Flow

1. **Add lifecycle listener teardown effect**
   - Introduce `:fx/lifecycle-remove-listeners`, store listener handles in `io-state`, and invoke during `stop-runtime!`.
   - Touchpoints: `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`, `/hyperopen/src/hyperopen/websocket/application/runtime.cljs`

2. **Make enqueue failures observable**
   - On `safe-put!` failure, emit telemetry and optionally dead-letter with channel name + msg summary.
   - Touchpoints: `/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs`, `/hyperopen/src/hyperopen/websocket/application/runtime.cljs`

3. **Instrument queue depth and handler latency**
   - Add lightweight counters to runtime projections (mailbox depth, effects depth, per-topic lag).
   - Touchpoints: `/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs`, `/hyperopen/src/hyperopen/websocket/application/runtime.cljs`, `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`

4. **Bound heavy handler work per tick**
   - Move expensive normalization updates behind frame/microtask batching where freshness allows.
   - Touchpoints: `/hyperopen/src/hyperopen/websocket/webdata2.cljs`, `/hyperopen/src/hyperopen/websocket/user.cljs`, `/hyperopen/src/hyperopen/websocket/trades.cljs`

5. **Wire or remove dead channel factory**
   - Either integrate `create-runtime-channels` into runtime startup or delete it to remove architecture drift.
   - Touchpoints: `/hyperopen/src/hyperopen/websocket/application/runtime.cljs`, `/hyperopen/test/hyperopen/websocket/application/runtime_test.cljs`

6. **Add missing regression tests**
   - New tests for listener teardown, enqueue-failure telemetry, and re-register old-handler drain behavior.
   - Touchpoints: `/hyperopen/test/hyperopen/websocket/infrastructure/runtime_effects_test.cljs`, `/hyperopen/test/hyperopen/websocket/application/runtime_test.cljs`

## Summary
The existing design already enforces strong deterministic ordering in the canonical runtime reducer and explicit effect interpretation boundaries. The highest-risk gaps are lifecycle teardown completeness, overloaded enqueue visibility, and event-loop pressure from heavy decode/handler work in long-lived loops.
