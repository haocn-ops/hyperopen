# Improve Portfolio First Paint by Deferring Startup Network Bootstrap Until After Initial Render

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, a hard refresh on `/portfolio` should paint visible UI sooner because Hyperopen will render first and only then start heavy remote bootstrap work (WebSocket module init + initial API fetch kickoff). Today, startup begins remote streams and account bootstrap before the first render tick, which delays visible paint and makes the page feel blank longer than necessary.

A user should be able to open `/portfolio` and see the app shell render before backend request fan-out begins, while preserving existing runtime behavior once startup finishes.

## Progress

- [x] (2026-02-27 23:28Z) Reviewed `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md` plan requirements.
- [x] (2026-02-27 23:28Z) Analyzed `/Users//Downloads/Trace-20260227T182311.json` and extracted first-paint critical-path timing evidence.
- [x] (2026-02-27 23:28Z) Located startup ordering path in `/hyperopen/src/hyperopen/startup/init.cljs` and `/hyperopen/src/hyperopen/startup/runtime.cljs`.
- [x] (2026-02-27 23:28Z) Authored this ExecPlan.
- [x] (2026-02-27 23:29Z) Implemented deferred post-render startup seam in `/hyperopen/src/hyperopen/startup/init.cljs` with immediate fallback when scheduler is absent.
- [x] (2026-02-27 23:29Z) Wired scheduler boundary in `/hyperopen/src/hyperopen/app/startup.cljs` via new runtime helper `schedule-post-render-startup!` in `/hyperopen/src/hyperopen/startup/runtime.cljs`.
- [x] (2026-02-27 23:30Z) Updated `/hyperopen/test/hyperopen/startup/init_test.cljs` expectations for render-before-bootstrap order and added deferred scheduling regression test.
- [x] (2026-02-27 23:30Z) Ran required gates with passing results: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-02-27 23:30Z) Finalized plan sections with implementation outcomes and validation evidence.

## Surprises & Discoveries

- Observation: The traced first-paint delay is not caused by waiting on portfolio API responses.
  Evidence: `firstContentfulPaint` occurs at +809.446ms, while first `/info` API responses arrive around +1741ms in `/Users//Downloads/Trace-20260227T182311.json`.

- Observation: Startup initiates network fan-out before first paint.
  Evidence: Initial portfolio/vault API requests (`https://stats-data.hyperliquid.xyz/Mainnet/vaults` and multiple `https://api.hyperliquid.xyz/info`) start around +701ms, preceding `firstContentfulPaint` at +809ms.

- Observation: Render sequencing currently places `initialize-remote-data-streams!` before `kick-render!`.
  Evidence: `/hyperopen/src/hyperopen/startup/init.cljs` invokes `initialize-remote-data-streams!` and only then `kick-render!` in `initialize-systems!`.

- Observation: Existing startup tests encoded the old ordering contract and needed explicit update when render-first sequencing was introduced.
  Evidence: `/hyperopen/test/hyperopen/startup/init_test.cljs` expected `:kick-render` after `:initialize-streams` before this patch.

## Decision Log

- Decision: Treat startup ordering (render vs. remote bootstrap) as the actionable root cause to fix.
  Rationale: This is directly controllable in repo code and aligns with repository responsiveness invariant that user-visible updates should precede heavy fetch/subscription work.
  Date/Author: 2026-02-27 / Codex

- Decision: Add a scheduling seam so heavy startup work runs on the next task after `kick-render!`, rather than in the same synchronous startup turn.
  Rationale: Simply moving `kick-render!` earlier in the same JS task does not guarantee a paint; yielding to the event loop does.
  Date/Author: 2026-02-27 / Codex

- Decision: Keep an immediate fallback path when `:schedule-post-render-startup!` is not provided.
  Rationale: This avoids breaking direct callers and keeps tests/simple harnesses deterministic without requiring async wiring everywhere.
  Date/Author: 2026-02-27 / Codex

## Outcomes & Retrospective

Implemented as planned. Startup orchestration now guarantees `kick-render!` is invoked before non-visual startup work is run, and production startup now supplies a scheduler that yields one macrotask (`setTimeout 0`) before beginning remote stream/bootstrap initialization.

What changed:

- `/hyperopen/src/hyperopen/startup/init.cljs`: `initialize-systems!` now kicks render first, then runs installer/service-worker/remote-bootstrap work through `:schedule-post-render-startup!` when available.
- `/hyperopen/src/hyperopen/app/startup.cljs`: startup init deps now include `:schedule-post-render-startup!`.
- `/hyperopen/src/hyperopen/startup/runtime.cljs`: added `schedule-post-render-startup!` helper that schedules callback on the next macrotask.
- `/hyperopen/test/hyperopen/startup/init_test.cljs`: deterministic-order assertions updated; added explicit deferred callback regression coverage.

Validation evidence:

- `npm run check` passed.
- `npm test` passed (`Ran 1504 tests containing 7636 assertions. 0 failures, 0 errors.`).
- `npm run test:websocket` passed (`Ran 153 tests containing 701 assertions. 0 failures, 0 errors.`).

Remaining gaps:

- This patch removes pre-paint startup network fan-out from the critical turn but does not reduce bundle parse/eval cost (`/js/main.js` long task remains a separate optimization opportunity).

## Context and Orientation

Relevant startup layers:

- `/hyperopen/src/hyperopen/app/startup.cljs` wires permanent dependencies into startup init collaborators.
- `/hyperopen/src/hyperopen/startup/init.cljs` defines startup orchestration (`reset-startup-state!`, state restore, system init ordering).
- `/hyperopen/src/hyperopen/startup/runtime.cljs` owns runtime-side startup helpers and scheduling primitives.
- `/hyperopen/test/hyperopen/startup/init_test.cljs` validates deterministic startup ordering and collaborator invocation.

In this plan, “remote bootstrap” means `initialize-remote-data-streams!`, which starts WebSocket modules, dispatches route/asset subscriptions, installs address handlers, and triggers account/bootstrap fetch flows.

Trace evidence from `/Users//Downloads/Trace-20260227T182311.json`:

- `navigationStart`: 0ms baseline.
- `firstPaint` / `firstContentfulPaint`: +809.446ms.
- Parser-blocking app script request: `/js/main.js` starts +25.125ms, finishes +198.723ms.
- Main-thread `EvaluateScript` for `/js/main.js`: +198.765ms to +712.035ms (513.27ms long task).
- Additional long task immediately after: +712.512ms to +768.385ms.
- Startup API fan-out begins around +701ms before first paint.

This points to startup work running in the first critical turn before a paint opportunity.

## Plan of Work

### Milestone 1: Add Post-Render Startup Scheduling Hook

Edit `/hyperopen/src/hyperopen/startup/init.cljs` so `initialize-systems!`:

1. Performs mandatory synchronous setup (`set-on-connected-handler!`, `init-wallet!`, `init-router!`, vault range restore).
2. Calls `kick-render!` immediately after route/wallet state is ready.
3. Schedules non-visual startup work (`install-asset-selector-shortcuts!`, `install-position-tpsl-clickaway!`, `register-icon-service-worker!`, `initialize-remote-data-streams!`) through a provided scheduler callback that runs in a future turn.
4. Falls back to immediate execution if scheduler dependency is absent, preserving compatibility for existing direct callers/tests.

### Milestone 2: Wire Scheduler from App Startup Boundary

Edit `/hyperopen/src/hyperopen/app/startup.cljs` to pass a scheduler dependency into `startup-init/init!` that uses runtime/platform timer scheduling for a next-turn callback (`setTimeout 0` style) so browser can paint first.

### Milestone 3: Lock Ordering with Tests

Edit `/hyperopen/test/hyperopen/startup/init_test.cljs` to add regression coverage proving:

- `kick-render!` happens before deferred remote bootstrap callback is executed.
- Deferred callback includes remote stream initialization and optional installers.
- Existing deterministic startup path still works when scheduler is absent.

### Milestone 4: Validate and Record

Run required gates from `/hyperopen` and capture results:

- `npm run check`
- `npm test`
- `npm run test:websocket`

Then update this plan’s `Progress`, `Decision Log`, and `Outcomes & Retrospective` with final state.

## Concrete Steps

1. Edit `/hyperopen/src/hyperopen/startup/init.cljs` to introduce deferred post-render startup execution path.
2. Edit `/hyperopen/src/hyperopen/app/startup.cljs` to provide scheduling collaborator into startup init deps.
3. Edit `/hyperopen/test/hyperopen/startup/init_test.cljs` for deferred-order regression assertions.
4. Run from `/hyperopen`:

       npm run check
       npm test
       npm run test:websocket

## Validation and Acceptance

Acceptance is complete when all items below are true:

1. Startup triggers `kick-render!` before remote bootstrap execution in production path.
2. Remote bootstrap still runs automatically (deferred by one event-loop turn) and existing functionality remains intact.
3. Startup ordering tests prove render-before-bootstrap behavior.
4. Required gates pass: `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

The code changes are additive and localized to startup orchestration boundaries. Reapplying edits is safe. If regressions appear, recovery is to disable deferred scheduling by using immediate fallback execution in `initialize-systems!`, restoring previous startup timing while preserving new seam for controlled rollout.

## Artifacts and Notes

Trace artifact analyzed:

- `/Users//Downloads/Trace-20260227T182311.json`

Target implementation files:

- `/hyperopen/src/hyperopen/startup/init.cljs`
- `/hyperopen/src/hyperopen/app/startup.cljs`
- `/hyperopen/src/hyperopen/startup/runtime.cljs`
- `/hyperopen/test/hyperopen/startup/init_test.cljs`

## Interfaces and Dependencies

Interfaces that remain stable:

- `hyperopen.app.startup/init!` public behavior: startup still restores state, initializes systems, and starts remote data.
- `hyperopen.startup.runtime/initialize-remote-data-streams!` behavior and signature.

New collaborator seam:

- `:schedule-post-render-startup!` dependency passed into `hyperopen.startup.init/initialize-systems!` from `/hyperopen/src/hyperopen/app/startup.cljs`.

Plan revision note: 2026-02-27 23:28Z - Initial plan created from trace diagnosis and startup ordering root-cause analysis.
Plan revision note: 2026-02-27 23:30Z - Marked implementation complete, recorded new scheduling seam, test updates, and required gate results.
