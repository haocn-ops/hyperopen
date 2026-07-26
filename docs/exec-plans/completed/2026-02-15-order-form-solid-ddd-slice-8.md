# Order Form SOLID/DDD Slice 8 (Gateway Hardening, Spec Contracts, UI Grouping, and Property Diagnostics)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

This slice implements the five remaining order-form hardening suggestions after slice 7: runtime gateway decoupling for command translation, spec-first contract hardening, explicit placeholder anti-corruption mapping, UI-state grouping to reduce flat-map coupling, and richer model-check failure diagnostics. The user-visible order form behavior should remain the same; the result is cleaner boundaries and stronger diagnostics when invariants fail.

## Progress

- [x] (2026-02-16 01:23Z) Created active ExecPlan for five-item hardening slice.
- [x] Introduced `order-form-runtime-gateway` protocol/default implementation and delegated adapter translation through it.
- [x] Added placeholder anti-corruption module and updated command tokens to source from it.
- [x] Converted order-form contracts to spec-first shape definitions with exact-key guards.
- [x] Added grouped UI projection in application context and consumed grouped semantics in VM composition.
- [x] Improved generative/model test failure diagnostics with shrunk intent trace + failure snapshot output.
- [x] Ran `npm run check`, `npm test`, and `npm run test:websocket` successfully.

## Surprises & Discoveries

- Observation: slice 7 already removed direct `[:actions/* ...]` output from commands; remaining coupling is now concentrated in one adapter namespace.
  Evidence: `/hyperopen/src/hyperopen/views/trade/order_form_commands.cljs` emits semantic `:command-id` maps.

## Decision Log

- Decision: Scope the “remove runtime action coupling” suggestion to order-form component boundaries rather than repository-wide migrations.
  Rationale: The user asked about this component, and repository-wide migration is separate work.
  Date/Author: 2026-02-16 / Codex

## Outcomes & Retrospective

Implemented all five planned hardening items without changing user-visible order-form behavior. Runtime action translation is now behind an explicit gateway seam, placeholder token mapping is isolated, contracts are more declarative/spec-first, VM composition depends on grouped UI projections from the application layer, and property-test failures now emit actionable shrunk diagnostics. Required validation gates all passed.

## Context and Orientation

Key files for this slice:

- `/hyperopen/src/hyperopen/views/trade/order_form_commands.cljs`
- `/hyperopen/src/hyperopen/views/trade/order_form_intent_adapter.cljs`
- `/hyperopen/src/hyperopen/schema/order_form_contracts.cljs`
- `/hyperopen/src/hyperopen/trading/order_form_application.cljs`
- `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs`
- `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs`

Terms used in this plan:

- Runtime gateway: a boundary object/protocol that translates semantic order-form intents into concrete runtime action vectors.
- Anti-corruption placeholder mapping: isolated translation between component-local placeholder tokens and runtime placeholder syntax.
- UI state grouping: a grouped projection (`:entry`, `:interaction`, `:panels`) derived from the flat UI map to reduce coupling and improve intent readability.

## Plan of Work

Milestone 1 introduces a runtime gateway protocol and default implementation. The adapter will delegate command translation through this gateway so runtime action IDs are no longer hardcoded into core command translation logic.

Milestone 2 extracts placeholder translation into a dedicated module and wires the gateway to use it.

Milestone 3 upgrades contracts to spec-first definitions with exact-key guards, replacing most ad hoc predicate shape checks.

Milestone 4 introduces UI grouping projection in application context and consumes grouped UI semantics in VM composition.

Milestone 5 improves generative transition tests with better failure summaries (including shrunk sample and intent trace excerpts).

Milestone 6 runs required checks and updates this plan.

## Concrete Steps

From `/hyperopen`:

1. Add gateway + placeholder modules under `/hyperopen/src/hyperopen/views/trade/`.
2. Refactor adapter/handlers to consume gateway APIs.
3. Refactor `/hyperopen/src/hyperopen/schema/order_form_contracts.cljs` to spec-first forms with exact-key guards.
4. Add grouped UI projection in `/hyperopen/src/hyperopen/trading/order_form_application.cljs` and use in `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs`.
5. Update tests in:
   - `/hyperopen/test/hyperopen/views/trade/order_form_commands_test.cljs`
   - `/hyperopen/test/hyperopen/views/trade/order_form_vm_test.cljs`
   - `/hyperopen/test/hyperopen/schema/contracts_test.cljs`
   - `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs`
6. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

## Validation and Acceptance

Acceptance criteria:

- Adapter translation depends on gateway abstraction, not embedded action-id constants.
- Placeholder mapping is isolated in a dedicated anti-corruption module.
- Order-form contracts are primarily defined through `cljs.spec` forms with exact-key guards.
- Application context exposes grouped UI projection and VM consumes grouped semantics.
- Generative tests provide actionable failure diagnostics with shrunk trace details.
- Required validation gates pass.

## Idempotence and Recovery

Changes are additive/refactor-focused and safe to re-run. If regressions appear, keep new gateway/spec modules and revert call-site wiring incrementally while tests remain green.

## Artifacts and Notes

Implemented/updated files:

- `/hyperopen/src/hyperopen/views/trade/order_form_runtime_gateway.cljs`
- `/hyperopen/src/hyperopen/views/trade/order_form_placeholders.cljs`
- `/hyperopen/src/hyperopen/views/trade/order_form_commands.cljs`
- `/hyperopen/src/hyperopen/views/trade/order_form_intent_adapter.cljs`
- `/hyperopen/src/hyperopen/schema/order_form_contracts.cljs`
- `/hyperopen/src/hyperopen/trading/order_form_application.cljs`
- `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs`
- `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs`

## Interfaces and Dependencies

Expected post-slice interfaces:

- `order-form runtime gateway` protocol + default implementation.
- Placeholder translator API independent from command constructors.
- Spec-based order-form VM/transition contracts with exact-key enforcement.
- `order-form-context` includes grouped UI projection for VM consumers.

Plan revision note: 2026-02-16 01:23Z - Initial plan created for five-item hardening slice.
