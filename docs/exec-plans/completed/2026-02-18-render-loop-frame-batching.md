# Batch Global Renders to One Frame in Runtime Bootstrap

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The runtime render loop previously rendered immediately on every store write from `/hyperopen/src/hyperopen/runtime/bootstrap.cljs`, which amplified websocket burst traffic into many full app renders. This change collapses store writes in the same browser frame so rendering runs at most once per animation frame and always uses the latest queued state. Users get smoother updates under websocket load without losing final state correctness.

## Progress

- [x] (2026-02-18 00:50Z) Reviewed `/hyperopen/.agents/PLANS.md` and identified the render hot path in `/hyperopen/src/hyperopen/runtime/bootstrap.cljs`.
- [x] (2026-02-18 00:50Z) Confirmed wiring path from `/hyperopen/src/hyperopen/app/bootstrap.cljs` into `install-render-loop!`.
- [x] (2026-02-18 00:50Z) Authored this active ExecPlan before implementation.
- [x] (2026-02-18 00:51Z) Implemented frame-batched render scheduling in `/hyperopen/src/hyperopen/runtime/bootstrap.cljs` and wired `:request-animation-frame!` from `/hyperopen/src/hyperopen/app/bootstrap.cljs`.
- [x] (2026-02-18 00:51Z) Updated `/hyperopen/test/hyperopen/runtime/bootstrap_test.cljs` to prove one render per frame with latest-state wins behavior.
- [x] (2026-02-18 00:53Z) Ran required validation gates (`npm run check`, `npm test`, `npm run test:websocket`) and captured outcomes.
- [x] (2026-02-18 00:53Z) Moved this plan to `/hyperopen/docs/exec-plans/completed/` after implementation and validation.

## Surprises & Discoveries

- Observation: The existing `install-render-loop!` path had no scheduling boundary, so every `swap!` was a direct render trigger.
  Evidence: Original `/hyperopen/src/hyperopen/runtime/bootstrap.cljs` `add-watch` callback called `render!` directly.
- Observation: Clearing `frame-pending?` before invoking `render!` is required so store writes that occur during render can schedule the next frame.
  Evidence: Updated test in `/hyperopen/test/hyperopen/runtime/bootstrap_test.cljs` performs a second post-flush write and confirms a second frame callback is scheduled.

## Decision Log

- Decision: Implement frame batching in the central runtime render watch instead of adding per-feature throttles.
  Rationale: Centralizing in `/hyperopen/src/hyperopen/runtime/bootstrap.cljs` guarantees one-frame coalescing across all store writes, including websocket paths, with minimal surface-area change.
  Date/Author: 2026-02-18 / Codex
- Decision: Keep scheduler dependency injectable through `:render-loop-deps` and pass `platform/request-animation-frame!` from app bootstrap.
  Rationale: This preserves deterministic tests and keeps runtime behavior explicit at wiring boundaries.
  Date/Author: 2026-02-18 / Codex

## Outcomes & Retrospective

Completed as planned. `/hyperopen/src/hyperopen/runtime/bootstrap.cljs` now batches global renders to one per frame using a pending-state atom and single in-flight frame guard. `/hyperopen/src/hyperopen/app/bootstrap.cljs` now provides `:request-animation-frame!` to runtime bootstrap wiring. `/hyperopen/test/hyperopen/runtime/bootstrap_test.cljs` now verifies both coalescing (two writes -> one render with latest state) and re-scheduling on the next frame.

Validation outcomes:

- `npm run check`: pass.
- `npm test`: pass (`1082` tests, `4922` assertions, `0` failures, `0` errors).
- `npm run test:websocket`: pass (`124` tests, `510` assertions, `0` failures, `0` errors).

No remaining work for this scoped performance issue.

## Context and Orientation

The Hyperopen app render path is connected in `/hyperopen/src/hyperopen/app/bootstrap.cljs` via `:render-loop-deps`, which are passed into `/hyperopen/src/hyperopen/runtime/bootstrap.cljs` `install-render-loop!`. The store is an atom. Before this change, each `swap!` reached `render!` immediately via `add-watch`.

In this plan, “batch to one frame” means queueing render requests with `requestAnimationFrame` and rendering once when the callback fires, using the most recent queued store state and discarding intermediate states from the same frame.

## Plan of Work

`install-render-loop!` in `/hyperopen/src/hyperopen/runtime/bootstrap.cljs` now maintains local watch-scoped render state with two atoms: `pending-state` and `frame-pending?`. On each store update, it stores the newest state and schedules a frame only if one is not already pending. During frame flush, it snapshots and clears pending state, clears the pending-frame flag, then invokes `render!` with the latest queued state.

`/hyperopen/src/hyperopen/app/bootstrap.cljs` now passes `platform/request-animation-frame!` in `:render-loop-deps`.

`/hyperopen/test/hyperopen/runtime/bootstrap_test.cljs` now asserts that multiple writes before frame flush produce one render with the latest state and that later writes schedule subsequent frame callbacks.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/runtime/bootstrap.cljs` to replace immediate watch renders with frame-scheduled coalescing while preserving dispatch wiring and document checks.
2. Edit `/hyperopen/src/hyperopen/app/bootstrap.cljs` to include `:request-animation-frame!` in `:render-loop-deps`.
3. Edit `/hyperopen/test/hyperopen/runtime/bootstrap_test.cljs` to validate frame coalescing and latest-state rendering.
4. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

Observed transcript summary:

  - `npm run check` completed successfully with lint/docs checks and app+test compilation passing.
  - `npm test` completed successfully with `1082` tests and `0` failures.
  - `npm run test:websocket` completed successfully with `124` tests and `0` failures.

## Validation and Acceptance

Acceptance is satisfied:

- Multiple `swap!` writes before frame flush now result in one render callback.
- That render callback receives the latest queued state for the frame.
- Dispatch wiring in `install-render-loop!` remains intact and covered by test assertions.
- Required validation gates all pass.

## Idempotence and Recovery

Changes are source-only and safe to reapply. Test commands are safe to rerun. If behavior needs rollback, restore direct render invocation in `install-render-loop!` while retaining tests for expected coalescing semantics.

## Artifacts and Notes

Changed files:

- `/hyperopen/src/hyperopen/runtime/bootstrap.cljs`
- `/hyperopen/src/hyperopen/app/bootstrap.cljs`
- `/hyperopen/test/hyperopen/runtime/bootstrap_test.cljs`
- `/hyperopen/docs/exec-plans/completed/2026-02-18-render-loop-frame-batching.md`

## Interfaces and Dependencies

Stable interfaces preserved:

- `/hyperopen/src/hyperopen/runtime/bootstrap.cljs`
  - `install-render-loop!`
  - `bootstrap-runtime!`
- `/hyperopen/src/hyperopen/app/bootstrap.cljs`
  - `bootstrap-runtime!`
  - `reload!`

Scheduling dependency:

- `/hyperopen/src/hyperopen/platform.cljs` `request-animation-frame!`.

Plan revision note: 2026-02-18 00:50Z - Initial plan created to implement one-render-per-frame batching in runtime bootstrap.
Plan revision note: 2026-02-18 00:53Z - Updated living sections after implementation and validation; marked all steps complete and moved the plan into completed.
