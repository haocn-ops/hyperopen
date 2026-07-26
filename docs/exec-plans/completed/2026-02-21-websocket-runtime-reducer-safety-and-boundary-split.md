# Refactor WebSocket Runtime Reducer For Safety And Boundary Clarity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The websocket runtime reducer in `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs` is currently the central state machine for connection lifecycle, subscription intent, stream telemetry, health hysteresis cadence, market coalescing, and projection emission. The architecture choice is sound (pure reducer + effect data), but the module has accumulated enough scope that correctness risks and maintenance costs are rising.

After this refactor, runtime behavior remains API-compatible but safer: stale decoded payloads from old sockets are ignored deterministically, intentional teardown paths clean up consistently, market coalesce buffers cannot leak stale data across reconnect boundaries, and the reducer contract is explicit about required config and timing semantics. The reducer internals are then split into cohesive pure modules without changing public runtime APIs.

## Progress

- [x] (2026-02-21 12:42Z) Reviewed `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/RELIABILITY.md`, `/hyperopen/docs/ARCHITECTURE.md`, and `/hyperopen/docs/QUALITY_SCORE.md` constraints.
- [x] (2026-02-21 12:42Z) Audited reducer, engine, interpreter, watcher, ACL, and client flows in `/hyperopen/src/hyperopen/websocket/**`.
- [x] (2026-02-21 12:42Z) Triaged reviewer suggestions against full-codebase context and wrote accepted/modified/vetoed scope in this plan.
- [x] (2026-02-21 13:08Z) Added failing websocket regression tests for stale decoded/parse socket-id handling, offline/disconnect/force-reconnect market pending cleanup, hidden lifecycle toggles, replay ordering, and parse-effect socket-id propagation.
- [x] (2026-02-21 13:16Z) Implemented Milestone 1 safety fixes across reducer/runtime-effects/model/runtime mappings and made stale-socket regressions pass.
- [x] (2026-02-21 13:31Z) Implemented Milestone 2 boundary split by extracting pure connection/market/subscription helper modules and delegating reducer orchestration to them with behavior parity preserved.
- [x] (2026-02-21 13:35Z) Implemented Milestone 3 hardening by adding reducer config normalization/assertions and explicit runtime time/state/tier orientation comments.
- [x] (2026-02-21 13:46Z) Ran required gates: `npm run check`, `npm test`, `npm run test:websocket` (all passed).
- [x] (2026-02-21 13:46Z) Moved this ExecPlan to `/hyperopen/docs/exec-plans/completed/` after acceptance criteria and required gates passed.

## Surprises & Discoveries

- Observation: The runtime engine uses separate async loops for reducer processing and effect interpretation, so `:evt/socket-message` guard logic does not automatically protect later parsed events.
  Evidence: `/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs` has one `go-loop` writing effects and another interpreting effects.
- Observation: `:fx/parse-raw-message` currently dispatches `:evt/decoded-envelope` and `:evt/parse-error` without `:socket-id`, so reducer branches cannot reject stale parse outputs.
  Evidence: `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs` parse branch omits `:socket-id` in dispatched messages.
- Observation: `:evt/lifecycle-offline` clears `:active-socket-id` before emitting close, so subsequent close event handling is ignored by design and does not run any reducer close-path logic.
  Evidence: `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs` offline handler sets `:active-socket-id nil` before `:fx/socket-close`.
- Observation: `:hidden?` affects retry delay and watchdog thresholds but is never set true by current lifecycle message algebra.
  Evidence: `runtime_effects` visibility listener emits only `:evt/lifecycle-visible`; there is no `:evt/lifecycle-hidden` in `/hyperopen/src/hyperopen/websocket/domain/model.cljs`.
- Observation: “fingerprint equals full projection” is currently an intentional dedupe contract, not a cryptographic hash.
  Evidence: `/hyperopen/docs/exec-plans/completed/2026-02-18-websocket-projection-effect-fingerprint-dedupe.md` and current helpers in reducer/interpreter use projection identity equality semantics.
- Observation: `:evt/parse-error` is used for both websocket parse pipeline failures and non-socket normalization errors, so socket-id guard logic must remain socket-aware but optional.
  Evidence: `/hyperopen/src/hyperopen/websocket/application/runtime.cljs` emits parse-error for unsupported command/transport payloads without socket ids.
- Observation: introducing `:evt/lifecycle-hidden` requires updates in domain event algebra, runtime normalization, and infrastructure visibility listeners together; partial wiring silently degrades behavior.
  Evidence: failing test in `/hyperopen/test/hyperopen/websocket/infrastructure/runtime_effects_test.cljs` only passed when all three layers were updated.

## Decision Log

- Decision: Accept the reviewer’s high-risk correctness items as Phase 1 scope: socket-id guards for decoded/parse-error, teardown consistency, market pending cleanup, and deterministic subscription replay sorting.
  Rationale: These items address silent state-corruption and stale-data risks with high impact and limited blast radius.
  Date/Author: 2026-02-21 / Codex
- Decision: Modify (not fully accept) the config-contract feedback by implementing reducer-side config normalization plus explicit dev/test assertions, rather than introducing broad new schema machinery in this pass.
  Rationale: Production already receives defaults from `/hyperopen/src/hyperopen/websocket/client.cljs`, but reducer-level normalization hardens direct/replay usage without adding heavy runtime overhead.
  Date/Author: 2026-02-21 / Codex
- Decision: Veto replacing private `defmulti` runtime message dispatch with `case` in this refactor.
  Rationale: Polymorphic dispatch was intentionally introduced recently for additive extension seams in `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs`; reverting now creates churn without addressing priority safety defects.
  Date/Author: 2026-02-21 / Codex
- Decision: Veto changing projection fingerprint semantics to partial/minimal hashes in this refactor.
  Rationale: Interpreter dedupe correctness depends on identity equivalence for projected atoms; narrowing fingerprints risks suppressing legitimate projection updates. Instead, we will document that these are projection identity keys.
  Date/Author: 2026-02-21 / Codex
- Decision: Add lifecycle hidden-state support (`:evt/lifecycle-hidden`) instead of removing hidden-aware policy.
  Rationale: Hidden/visible retry and stale thresholds are already part of config and client policy, so wiring hidden lifecycle events completes intended behavior with low risk.
  Date/Author: 2026-02-21 / Codex
- Decision: Guard decoded/parse-error handling only when a socket id is present, while still accepting non-socket parse errors.
  Rationale: Unsupported command/event normalization must continue to surface parse errors even though they are not tied to a websocket generation.
  Date/Author: 2026-02-21 / Codex
- Decision: Execute Milestone 2 extraction with three pure helper modules (`connection`, `market`, `subscriptions`) and keep health/projection derivation in reducer for now.
  Rationale: This split materially reduces reducer scope pressure and merge risk while avoiding a larger projection/health move in the same safety patch.
  Date/Author: 2026-02-21 / Codex

## Outcomes & Retrospective

Implementation completed end-to-end for the scoped plan.

What was achieved:

- Stale parsed websocket outputs are now generation-guarded by socket id at both decode and parse-error reducer branches.
- Runtime parser effect now emits socket ids on decoded/parse-error messages.
- Lifecycle hidden behavior is now first-class (`:lifecycle/hidden` -> `:evt/lifecycle-hidden`) and toggles runtime `:hidden?` deterministically.
- Offline/disconnect/force-reconnect teardown paths now clear market flush timers and pending coalesce buffers, and offline now detaches handlers before close.
- Unexpected socket-close path now clears market-flush state before retry scheduling.
- Replay subscription ordering now uses domain key ordering (`model/subscription-key`) rather than `pr-str`.
- Reducer config contract is explicit via defaults + assertions, and top-of-file orientation comments now define runtime state/time semantics for maintainers and agents.
- Reducer responsibilities were split into internal pure modules:
  - `/hyperopen/src/hyperopen/websocket/application/runtime/connection.cljs`
  - `/hyperopen/src/hyperopen/websocket/application/runtime/market.cljs`
  - `/hyperopen/src/hyperopen/websocket/application/runtime/subscriptions.cljs`

Validation summary:

- `npm run check`: pass.
- `npm test`: pass (`1182` tests, `5497` assertions, `0` failures, `0` errors).
- `npm run test:websocket`: pass (`141` tests, `617` assertions, `0` failures, `0` errors).

Residual scope:

- Health/projection helper extraction remains in reducer by deliberate choice; behavior is stable, and further decomposition can be a follow-up without blocking current correctness goals.

## Context and Orientation

Websocket runtime architecture in this repository is split intentionally:

- Pure runtime transition logic: `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs`.
- Single-writer orchestration loop: `/hyperopen/src/hyperopen/websocket/application/runtime_engine.cljs`.
- Side-effect interpretation boundary: `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`.
- Input normalization and startup wiring: `/hyperopen/src/hyperopen/websocket/application/runtime.cljs`.
- Public client seam: `/hyperopen/src/hyperopen/websocket/client.cljs`.

In this plan, “decoded pipeline guard” means validating that parsed payload messages still belong to the currently active socket generation before mutating reducer state. “Teardown consistency” means intentional disconnect/offline/reconnect paths all detach handlers, clear relevant timers, and do not leak buffered market payloads into later sessions.

This plan preserves existing public API functions in `/hyperopen/src/hyperopen/websocket/client.cljs` and keeps reducer purity and explicit `RuntimeEffect` output intact.

## Reviewer Feedback Disposition

Accepted now:

1. Add socket-id guard for `:evt/decoded-envelope` and `:evt/parse-error`.
2. Fix offline/disconnect teardown consistency.
3. Clear `[:market-coalesce :pending]` when severing connection intent.
4. Replace replay `sort-by pr-str` with deterministic domain key ordering.
5. Make runtime config contract explicit at reducer boundary.
6. Document state machine/time semantics for maintainers and agents.

Accepted with adaptation:

1. SRP/DDD scope concerns are addressed through internal module extraction while preserving the public `step` API and current message/effect algebra.
2. Timer-state entropy is reduced by helper consolidation first; full timer-map state-shape migration is deferred to avoid broad behavioral churn.

Vetoed for this refactor:

1. Reverting private `defmulti` dispatch back to `case`.
2. Replacing projection identity fingerprints with reduced partial fingerprints.
3. Renaming bounded context/public namespaces in this pass (too much external churn for limited safety gain).

## Plan of Work

Milestone 1 addresses correctness risks without architectural churn. We first add failing tests that prove stale parsed payloads can currently mutate active state after socket generation changes, then harden message/effect contracts so decoded and parse-error events carry `:socket-id` and reducer branches ignore mismatches. The same milestone unifies teardown behavior for disconnect/offline/reconnect intent, including market coalesce pending cleanup and handler detachment parity, and replaces subscription replay ordering with domain-stable key sorting.

Milestone 2 reduces scope pressure by extracting cohesive pure helper modules while keeping `step` and message/effect signatures unchanged. The top-level reducer remains orchestrator-only and delegates to focused helpers for connection lifecycle, subscription/stream bookkeeping, market coalescing, health refresh cadence, and projection assembly. This is explicitly a behavior-preserving internal split backed by parity tests.

Milestone 3 hardens interface clarity. We define reducer config defaults and required-key assertions at initialization boundaries, standardize runtime message time-field semantics in comments and tests, and add a compact state-machine orientation block at the reducer entry for human/agent maintainers.

## Concrete Steps

From `/hyperopen`:

1. Add failing regression tests first.
   - Update `/hyperopen/test/hyperopen/websocket/application/runtime_reducer_test.cljs` with stale decoded/parse-error socket-id mismatch tests, teardown cleanup tests, market pending disconnect cleanup tests, and replay ordering tests.
   - Update `/hyperopen/test/hyperopen/websocket/infrastructure/runtime_effects_test.cljs` to assert parse-dispatched messages include `:socket-id`.
2. Implement Milestone 1 safety changes.
   - Edit `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs` parse effect dispatch payloads.
   - Edit `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs` guard logic and teardown helpers.
   - Edit `/hyperopen/src/hyperopen/websocket/domain/model.cljs` and `/hyperopen/src/hyperopen/websocket/application/runtime.cljs` if adding `:evt/lifecycle-hidden` / `:lifecycle/hidden` transport mapping.
3. Implement Milestone 2 structural split.
   - Create pure helper namespaces under `/hyperopen/src/hyperopen/websocket/application/runtime/` (for example `connection.cljs`, `subscriptions.cljs`, `market.cljs`, `health.cljs`, `projections.cljs`).
   - Keep `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs` as orchestration entry that composes extracted helpers.
4. Implement Milestone 3 contract/documentation hardening.
   - Add reducer config normalization/default helpers in `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs` (or extracted config helper namespace).
   - Add top-of-file orientation comments for status machine states, tier meanings, expected-traffic semantics, and time-field conventions.
5. Run required validation gates.
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

Expected transcript shape:

  - New reducer/effects tests fail before Milestone 1 changes and pass after.
  - All required gates complete with zero failures/errors.

## Validation and Acceptance

Acceptance criteria are behavior-based:

1. Parsed outputs from stale sockets are ignored.
   - Given active socket id `2`, when `:evt/decoded-envelope` or `:evt/parse-error` arrives with socket id `1`, reducer state and effects remain unchanged except standard projections.
2. Teardown is deterministic.
   - `:cmd/disconnect`, `:cmd/force-reconnect`, and `:evt/lifecycle-offline` all clear retry intent and market flush intent and prevent stale pending market envelopes from later dispatch.
3. Hidden lifecycle policy is wired.
   - Runtime can transition `:hidden?` true/false via lifecycle events, and retry/watchdog policy uses the corresponding config thresholds.
4. Replay ordering is deterministic by domain key.
   - Replayed subscribe effects are sorted by `model/subscription-key` rather than `pr-str`.
5. Public websocket APIs remain unchanged.
   - `/hyperopen/src/hyperopen/websocket/client.cljs` exported function signatures and call shapes are preserved.
6. Required gates pass.
   - `npm run check`, `npm test`, `npm run test:websocket` all pass.

## Idempotence and Recovery

This work is additive and safe to rerun. If Milestone 2 extraction introduces a regression, recovery is to keep Milestone 1 safety fixes and temporarily collapse helper delegation back into reducer-local helpers while preserving new regression tests. No data migrations or destructive operations are required.

## Artifacts and Notes

Primary files expected to change:

- `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs`
- `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`
- `/hyperopen/src/hyperopen/websocket/application/runtime.cljs`
- `/hyperopen/src/hyperopen/websocket/domain/model.cljs`
- `/hyperopen/src/hyperopen/websocket/application/runtime/*.cljs` (new internal pure helper namespaces)
- `/hyperopen/test/hyperopen/websocket/application/runtime_reducer_test.cljs`
- `/hyperopen/test/hyperopen/websocket/infrastructure/runtime_effects_test.cljs`
- `/hyperopen/test/hyperopen/websocket/application/runtime_test.cljs` (only if lifecycle-hidden integration coverage is added)
- `/hyperopen/docs/exec-plans/active/2026-02-21-websocket-runtime-reducer-safety-and-boundary-split.md`

Evidence to capture during implementation:

- Before/after failing test proof for stale decoded-socket acceptance.
- Teardown path effect assertions (detach/close/timer clear + pending clear).
- Gate outputs for required commands.

## Interfaces and Dependencies

Public interfaces that must remain stable:

- `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs`: `initial-runtime-state`, `step`.
- `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`: `interpret-effect!`.
- `/hyperopen/src/hyperopen/websocket/application/runtime.cljs`: `start-runtime!`, `publish-command!`, `publish-transport-event!`, `stop-runtime!`.
- `/hyperopen/src/hyperopen/websocket/client.cljs`: client API functions.

Dependency boundaries to preserve:

- Domain and health policy functions from `/hyperopen/src/hyperopen/websocket/domain/model.cljs` and `/hyperopen/src/hyperopen/websocket/health.cljs` remain pure.
- Reducer remains pure and emits effect data only.
- Interpreter remains the only input/output side-effect boundary.

Plan revision note: 2026-02-21 12:42Z - Initial plan created after cross-module runtime audit, with reviewer-feedback triage and scoped refactor sequence (accepted, adapted, and vetoed items documented).
Plan revision note: 2026-02-21 13:46Z - Updated living sections after implementing safety fixes, lifecycle hidden support, reducer helper extraction, and passing all required validation gates.
