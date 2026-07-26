# Reduce WebSocket ACL Parse Allocations for Market Channels

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The websocket ACL currently performs full JSON decode plus full ClojureScript keywordized map conversion for every inbound message in `/hyperopen/src/hyperopen/websocket/acl/hyperliquid.cljs`. For market channels (`l2Book`, `trades`, `activeAssetCtx`) this is expensive because runtime coalescing later drops superseded messages before handlers see them. After this change, market messages are parsed through a fast path that extracts only routing/coalescing fields first and defers full map conversion until dispatch time when the message survives coalescing. Users should see lower websocket allocation pressure during burst market traffic with unchanged handler behavior.

## Progress

- [x] (2026-02-16 00:00Z) Reviewed `/hyperopen/.agents/PLANS.md`, `/hyperopen/ARCHITECTURE.md`, `/hyperopen/docs/RELIABILITY.md`, and `/hyperopen/docs/QUALITY_SCORE.md` guardrails for runtime purity and validation gates.
- [x] (2026-02-16 00:05Z) Confirmed hotspot in `/hyperopen/src/hyperopen/websocket/acl/hyperliquid.cljs` where `js/JSON.parse` and `js->clj :keywordize-keys true` run on every message.
- [x] (2026-02-16 00:08Z) Traced runtime flow through `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs`, `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`, and `/hyperopen/src/hyperopen/websocket/application/runtime.cljs` to identify safe deferred-conversion boundary.
- [x] (2026-02-16 00:10Z) Authored this active ExecPlan.
- [x] (2026-02-16 00:24Z) Implemented ACL fast-path parsing with minimal `channel`/`coin` extraction and deferred payload markers in `/hyperopen/src/hyperopen/websocket/acl/hyperliquid.cljs`.
- [x] (2026-02-16 00:27Z) Added deferred hydration wiring through runtime startup and infrastructure dispatch in `/hyperopen/src/hyperopen/websocket/application/runtime.cljs`, `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`, and `/hyperopen/src/hyperopen/websocket/client.cljs`.
- [x] (2026-02-16 00:31Z) Added websocket regression tests for deferred market parsing and dispatch hydration in `/hyperopen/test/hyperopen/websocket/acl/hyperliquid_test.cljs` and `/hyperopen/test/hyperopen/websocket/infrastructure/runtime_effects_test.cljs`.
- [x] (2026-02-16 00:35Z) Hardened deferred hydration fallback to avoid interpreter-loop failures when deferred raw payload cannot be parsed.
- [x] (2026-02-16 00:55Z) Ran required gates successfully: `npm run check`, `npm test`, and `npm run test:websocket`.

## Surprises & Discoveries

- Observation: Runtime already coalesces market envelopes by `[topic coin]` in reducer state before dispatch, so per-message full payload conversion is often wasted for superseded market updates.
  Evidence: `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs` stores market envelopes under `[:market-coalesce :pending key]` and only dispatches on `:evt/timer-market-flush-fired`.
- Observation: Topic handlers expect keywordized maps (`:channel`, `:data`, nested keyword keys), so deferred conversion must complete before router handler invocation.
  Evidence: `/hyperopen/src/hyperopen/websocket/trades.cljs`, `/hyperopen/src/hyperopen/websocket/orderbook.cljs`, and `/hyperopen/src/hyperopen/websocket/active_asset_ctx.cljs` read keyword keys from payload maps.
- Observation: Adding hydration as an injected runtime collaborator kept domain/application reducer behavior unchanged while enabling late conversion exactly at routing boundary.
  Evidence: `/hyperopen/src/hyperopen/websocket/application/runtime.cljs` now passes `:hydrate-envelope` into interpreter context; reducer files required no edits.
- Observation: Hydration should degrade safely when deferred payload raw JSON cannot be parsed, otherwise interpreter failures are emitted and envelope dispatch is dropped.
  Evidence: `/hyperopen/src/hyperopen/websocket/acl/hyperliquid.cljs` now catches hydrate parse failures and falls back to minimal non-deferred payload (`:channel`/identity fields) instead of throwing.

## Decision Log

- Decision: Keep domain reducer and coalescing logic unchanged, and implement deferred hydration at infrastructure dispatch boundary.
  Rationale: This preserves reducer determinism and single-writer invariants while moving heavy conversion to the latest safe point.
  Date/Author: 2026-02-16 / Codex
- Decision: For market tier when validation is disabled, parse only minimal fields (`channel`, coalesce identity like `coin`) from raw text in ACL and store deferred payload markers.
  Rationale: This reduces hot-path allocations while preserving envelope contract and coalesce key stability.
  Date/Author: 2026-02-16 / Codex
- Decision: Preserve strict behavior under validation-enabled mode by keeping full provider-message assertion path.
  Rationale: Contract checks in debug/test remain high-signal and behaviorally consistent.
  Date/Author: 2026-02-16 / Codex
- Decision: Inject hydration through runtime interpreter context (`:hydrate-envelope`) instead of coupling router/application layer directly to ACL implementation.
  Rationale: This keeps boundary responsibilities explicit and avoids new cross-layer coupling from application router logic to protocol adapter details.
  Date/Author: 2026-02-16 / Codex
- Decision: On deferred hydrate parse failure, fall back to a non-deferred minimal payload instead of throwing.
  Rationale: This prevents interpreter-loop failure churn while maintaining deterministic no-op behavior in topic handlers that require full `:data` content.
  Date/Author: 2026-02-16 / Codex

## Outcomes & Retrospective

Implementation is complete for the scoped medium-impact finding.

What changed:

- Added a market-tier ACL fast path in `/hyperopen/src/hyperopen/websocket/acl/hyperliquid.cljs` that extracts `channel` and coalescing identity (`coin`/`symbol`/`asset`) from raw JSON text and stores deferred payload markers instead of eagerly keywordizing the full payload map.
- Added `hydrate-envelope` in ACL and threaded it into runtime interpreter context so `:fx/router-dispatch-envelope` in `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs` hydrates deferred payloads immediately before topic routing.
- Preserved validation-enabled behavior: when contracts are enabled, ACL still fully parses and asserts provider payload shape.
- Added hydration fallback handling so deferred parse failures do not bubble as interpreter-loop errors.
- Added tests that prove deferred parsing and late hydration behavior.

Validation evidence:

- `npm run check`: pass.
- `npm test`: pass (`1075` tests, `4892` assertions, `0` failures, `0` errors).
- `npm run test:websocket`: pass (`124` tests, `510` assertions, `0` failures, `0` errors).

Behavioral acceptance status:

- Market channel fast path avoids eager full map keywordization when validation is disabled: met.
- Coalescing key behavior remains stable via deferred `:coin` extraction: met.
- Handlers receive full keywordized payload at dispatch time via hydration: met.
- Validation-enabled and lossless parsing behavior remain intact: met.

## Context and Orientation

Inbound websocket payloads enter at `/hyperopen/src/hyperopen/websocket/client.cljs` and flow through runtime effect `:fx/parse-raw-message` in `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`, which calls ACL parser `/hyperopen/src/hyperopen/websocket/acl/hyperliquid.cljs`. The parser emits a domain envelope from `/hyperopen/src/hyperopen/websocket/domain/model.cljs`. The pure reducer `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs` coalesces market envelopes, and later runtime effect `:fx/router-dispatch-envelope` forwards surviving envelopes to topic handlers via `/hyperopen/src/hyperopen/websocket/application/runtime.cljs`.

In this plan, “fast path” means extracting only fields needed immediately for routing and coalescing (`channel`, optional coin identity) without converting full nested payload maps. “Deferred hydration” means converting raw JSON into the full keywordized map only when dispatching to handlers.

## Plan of Work

Milestone 1 updates the ACL parser to perform minimal extraction first for market tier messages when validation is disabled. This path emits a deferred payload map carrying `:channel`, extracted `:coin` for coalescing, and raw JSON for later hydration.

Milestone 2 adds a payload hydration function and threads it through runtime dependencies so `:fx/router-dispatch-envelope` hydrates deferred payloads immediately before router handler invocation. Non-deferred payloads pass through unchanged.

Milestone 3 updates tests in ACL and runtime effects namespaces to prove: fast-path market parsing does not eagerly keywordize full payloads, hydration restores handler-visible map shape, and parse/dispatch behavior for lossless or invalid payloads remains correct.

Milestone 4 runs repository-required gates and records concise evidence, then updates this plan’s living sections to final state.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/websocket/acl/hyperliquid.cljs`:
   - Add minimal extraction helpers for channel and coin from raw JSON string.
   - Add deferred payload marker and hydration helper.
   - Keep full parse path for non-market or validation-enabled cases.
2. Edit `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs` and `/hyperopen/src/hyperopen/websocket/application/runtime.cljs`:
   - Inject optional `hydrate-envelope` collaborator and call it in `:fx/router-dispatch-envelope`.
3. Edit `/hyperopen/src/hyperopen/websocket/client.cljs`:
   - Provide ACL hydrate function to runtime startup wiring.
4. Update tests:
   - `/hyperopen/test/hyperopen/websocket/acl/hyperliquid_test.cljs`
   - `/hyperopen/test/hyperopen/websocket/infrastructure/runtime_effects_test.cljs`
5. Run validation:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

Expected transcript shape:

  - `npm run check` finishes without compile/lint failures.
  - `npm test` reports zero failures/errors.
  - `npm run test:websocket` reports zero failures/errors including updated websocket ACL/effects tests.

## Validation and Acceptance

Acceptance is met when all are true:

- Market channel ACL parsing no longer performs eager full keywordization on every inbound message when validation is disabled.
- Market coalescing key behavior remains stable (latest message per `[topic coin]` survives).
- Topic handlers still receive full keywordized payload maps at dispatch time.
- Non-market parsing, validation-enabled contract assertions, and invalid JSON error behavior remain correct.
- Required gates pass: `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

Edits are code-only and safe to reapply. If deferred hydration introduces regressions, recovery is to keep helper wiring but route market tier through existing eager conversion path while preserving new tests for iterative hardening. No schema migration or destructive operation is involved.

## Artifacts and Notes

Touched paths:

- `/hyperopen/src/hyperopen/websocket/acl/hyperliquid.cljs`
- `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`
- `/hyperopen/src/hyperopen/websocket/application/runtime.cljs`
- `/hyperopen/src/hyperopen/websocket/client.cljs`
- `/hyperopen/test/hyperopen/websocket/acl/hyperliquid_test.cljs`
- `/hyperopen/test/hyperopen/websocket/infrastructure/runtime_effects_test.cljs`
- `/hyperopen/docs/exec-plans/completed/2026-02-16-websocket-acl-parse-fast-path.md`

Key evidence captured:

- `/hyperopen/test/hyperopen/websocket/acl/hyperliquid_test.cljs` includes `parse-raw-envelope-market-fast-path-defers-full-conversion-test`, asserting deferred payload markers and successful hydration into full keywordized map.
- `/hyperopen/test/hyperopen/websocket/acl/hyperliquid_test.cljs` includes `parse-raw-envelope-lossless-path-stays-eager-when-validation-disabled-test`, proving non-market behavior remains eager parse.
- `/hyperopen/test/hyperopen/websocket/infrastructure/runtime_effects_test.cljs` includes hydration collaborator dispatch assertion, proving interpreter applies hydration before routing.
- Gate outputs:
  - `npm run check` completed successfully (lint/docs/app+test compile all pass).
  - `npm test` completed successfully (`1075` tests, `4892` assertions).
  - `npm run test:websocket` completed successfully (`124` tests, `510` assertions).

## Interfaces and Dependencies

Stable interfaces to preserve:

- `/hyperopen/src/hyperopen/websocket/client.cljs` public API (`init-connection!`, `register-handler!`, etc.)
- Runtime message/effect algebra in `/hyperopen/src/hyperopen/websocket/domain/model.cljs`

Dependencies introduced or reused:

- ACL helper functions in `/hyperopen/src/hyperopen/websocket/acl/hyperliquid.cljs` for deferred payload detection/hydration.
- Runtime effect collaborator injection through `/hyperopen/src/hyperopen/websocket/application/runtime.cljs` into `/hyperopen/src/hyperopen/websocket/infrastructure/runtime_effects.cljs`.

Plan revision note: 2026-02-16 00:10Z - Initial ExecPlan created for medium-impact websocket ACL allocation optimization with deferred market payload conversion strategy.
Plan revision note: 2026-02-16 00:55Z - Updated living sections after implementing ACL fast-path/deferred hydration wiring, adding regression tests, hardening hydration fallback, and passing all required validation gates.
Plan revision note: 2026-02-16 01:05Z - Moved plan from active to completed after acceptance criteria pass and re-ran full required gates to confirm final-tree green status.
