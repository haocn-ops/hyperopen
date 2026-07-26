# Order Form SOLID/DDD Slice 7 (Intent Boundary, UI/Domain Field Split, Closed Contracts, and Generative Model Checks)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

This slice finishes the remaining architecture cleanup for the order-form component by isolating view intent construction from runtime action IDs, fully separating UI-owned ticket fields from persisted domain draft fields, hardening order-type extension invariants, narrowing the VM application boundary, closing schema contracts, and upgrading transition checks to true generative model-based tests. After this change, order-form rendering and transitions remain behaviorally equivalent for users, but architecture boundaries become explicit and test-enforced.

## Progress

- [x] (2026-02-15 23:59Z) Audited current slice-6 code and identified exact remaining gaps against six requested items.
- [x] (2026-02-16 00:03Z) Created active ExecPlan with concrete files, milestones, and validation strategy.
- [x] (2026-02-16 00:21Z) Implemented intent adapter boundary (`order_form_intent_adapter.cljs`) and migrated order-form command constructors to semantic command maps.
- [x] (2026-02-16 00:34Z) Completed UI/domain split for `:entry-mode`, `:ui-leverage`, and `:size-display` across state defaults, transition ownership, action persistence, submit flow, and asset-switch reset handling.
- [x] (2026-02-16 00:42Z) Added extension registry invariants with explicit validation and debug-time fail-fast checks.
- [x] (2026-02-16 00:46Z) Narrowed VM application boundary with explicit state projection + pure context composition (`build-order-form-context`) and capability-key narrowing.
- [x] (2026-02-16 00:53Z) Closed order-form contracts with exact-key VM/transition schemas and tightened app-state order-form + order-form-ui ownership rules.
- [x] (2026-02-16 01:00Z) Replaced deterministic pseudo-random transition simulation with test.check generative/model-based transition properties.
- [x] (2026-02-16 01:16Z) Ran required validation gates successfully (`npm run check`, `npm test`, `npm run test:websocket`).

## Surprises & Discoveries

- Observation: slice-6 already moved most branching into registry/VM controls; remaining gaps are mainly boundary strictness and contract closure.
  Evidence: `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs` now consumes VM `:controls` instead of direct order-type checks.
- Observation: test.check must be added to `shadow-cljs.edn` dependencies for the node-test build; `deps.edn` alone is insufficient in this repo setup.
  Evidence: `npm run check` failed with `The required namespace "clojure.test.check" is not available` until `shadow-cljs.edn` was updated.
- Observation: extension invariant validation cannot require `:sections` in raw `order-type-config` entries because `:market` and `:limit` inherit `:sections []` from registry defaults.
  Evidence: test suite raised `Invalid order-form type extension registry ... :invalid-sections` for `:market` and `:limit` until validation switched to merged `order-type-entry`.
- Observation: adding invariant enforcement inside `supported-order-type-sections` caused recursive validation calls.
  Evidence: `npm test` initially failed with `RangeError: Maximum call stack size exceeded` across order-form view tests until recursion was removed.

## Decision Log

- Decision: Keep runtime action IDs unchanged and introduce an order-form-local intent adapter layer at the view boundary.
  Rationale: This decouples component logic from volatile action DSL while preserving runtime wiring and minimizing churn.
  Date/Author: 2026-02-16 / Codex
- Decision: Enforce UI/domain split at persistence boundaries (effects payloads and submit path) while preserving legacy form-field read compatibility.
  Rationale: This allows strict architecture for live state without breaking existing test fixtures and transitional callers that still include legacy keys.
  Date/Author: 2026-02-16 / Codex
- Decision: Enforce field ownership inside transitions (not only in order actions) so pure transition outputs are contract-safe for direct transition tests and consumers.
  Rationale: This makes state ownership deterministic at the earliest boundary and avoids test/runtime divergence.
  Date/Author: 2026-02-16 / Codex
- Decision: Keep VM payload focused on view-consumed fields and enforce exact-key contracts on this slimmer public VM shape.
  Rationale: Closed contracts are practical only when the VM surface is explicit and intentional.
  Date/Author: 2026-02-16 / Codex

## Outcomes & Retrospective

All six requested SOLID/DDD items were implemented. Order-form commands are now semantic intents translated by a single adapter boundary; UI-owned fields (`:entry-mode`, `:ui-leverage`, `:size-display`) are persisted under `:order-form-ui` with transition/action ownership enforcement; extension invariants are explicit and test-covered; VM application context composition is narrowed and partially pure; VM/transition schema contracts are closed; and transition invariants now use generative/model-based property tests.

Required validation gates passed with zero failures across lint/compile (`npm run check`), full tests (`npm test`), and websocket tests (`npm run test:websocket`).

## Context and Orientation

Current order-form concerns are spread across:

- `/hyperopen/src/hyperopen/views/trade/order_form_commands.cljs` and `/hyperopen/src/hyperopen/views/trade/order_form_handlers.cljs` for event wiring.
- `/hyperopen/src/hyperopen/trading/order_form_state.cljs`, `/hyperopen/src/hyperopen/state/trading.cljs`, `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`, and `/hyperopen/src/hyperopen/order/actions.cljs` for order draft/UI/runtime state transitions and persistence effects.
- `/hyperopen/src/hyperopen/views/trade/order_form_type_extensions.cljs` and `/hyperopen/src/hyperopen/trading/order_type_registry.cljs` for order-type extension behavior.
- `/hyperopen/src/hyperopen/trading/order_form_application.cljs` and `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs` for VM context assembly.
- `/hyperopen/src/hyperopen/schema/order_form_contracts.cljs` and `/hyperopen/src/hyperopen/schema/contracts.cljs` for contracts.
- `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs` and related tests for invariant coverage.

In this plan, “UI-owned fields” means ticket representation fields that are not part of canonical order payload semantics (`:entry-mode`, `:ui-leverage`, `:size-display`).

## Plan of Work

Milestone 1 introduces an intent adapter. Command constructors will emit semantic order-form intents. A dedicated adapter will translate intents to runtime action vectors with placeholder substitution. The view/handlers will only depend on semantic intents.

Milestone 2 completes field ownership split by persisting UI-owned fields under `:order-form-ui` and stripping them from persisted `:order-form` saves, including submit success flow. State selectors will still support legacy input maps for compatibility.

Milestone 3 hardens extension invariants with explicit validation of registry sections and capability shape. Public extension helpers will ensure invariants are checked in debug/test paths.

Milestone 4 narrows VM application boundary by projecting explicit input snapshots from state and composing context in a pure function with a constrained capability payload.

Milestone 5 closes contracts by enforcing exact keys for VM/transition schema and tighter app-state UI contract checks.

Milestone 6 upgrades transition invariant tests to generative/model-based checks using test.check-driven intent sequences and model alignment assertions.

## Concrete Steps

From `/hyperopen`:

1. Edit order-form view intent boundary files:
   - `/hyperopen/src/hyperopen/views/trade/order_form_commands.cljs`
   - `/hyperopen/src/hyperopen/views/trade/order_form_handlers.cljs`
   - add adapter namespace under `/hyperopen/src/hyperopen/views/trade/`.
2. Edit order-form state and persistence boundaries:
   - `/hyperopen/src/hyperopen/trading/order_form_state.cljs`
   - `/hyperopen/src/hyperopen/state/trading.cljs`
   - `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`
   - `/hyperopen/src/hyperopen/order/actions.cljs`
3. Edit extension and application boundary modules:
   - `/hyperopen/src/hyperopen/views/trade/order_form_type_extensions.cljs`
   - `/hyperopen/src/hyperopen/trading/order_form_application.cljs`
   - `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs`
4. Tighten schema contracts:
   - `/hyperopen/src/hyperopen/schema/order_form_contracts.cljs`
   - `/hyperopen/src/hyperopen/schema/contracts.cljs`
5. Update/add tests:
   - `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs`
   - `/hyperopen/test/hyperopen/views/trade/order_form_vm_test.cljs`
   - `/hyperopen/test/hyperopen/schema/contracts_test.cljs`
   - any affected order actions/view tests.
6. Run required gates:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

Expected transcript shape:

    npm run check
    ...
    Build completed ... 0 warnings.

    npm test
    ...
    0 failures, 0 errors.

    npm run test:websocket
    ...
    0 failures, 0 errors.

## Validation and Acceptance

Acceptance criteria:

- Order-form command constructors no longer return `[:actions/* ...]` vectors directly; translation occurs in one adapter boundary.
- Persisted `:order-form` projections no longer store `:entry-mode`, `:ui-leverage`, or `:size-display`; these are owned by `:order-form-ui` and remain behaviorally equivalent in the ticket.
- Order-type extension registry/section mismatch fails fast via invariant checks.
- VM context assembly has explicit state projection and pure composition boundary.
- Order-form VM and transition contracts reject unknown keys and enforce exact map shapes.
- Transition invariants include true generative/model-based checks, not only deterministic scripted loops.
- Required validation gates pass.

## Idempotence and Recovery

All edits are additive/refactor-focused and can be reapplied safely. If any milestone regresses behavior, keep new namespaces and revert call-site wiring incrementally while keeping tests green. No migrations or destructive commands are required.

## Artifacts and Notes

Validation transcripts:

    npm run check
    ...
    [:app] Build completed. (... 0 warnings ...)
    [:test] Build completed. (... 0 warnings ...)

    npm test
    ...
    Ran 826 tests containing 3388 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    ...
    Ran 86 tests containing 267 assertions.
    0 failures, 0 errors.

Primary new files:

- `/hyperopen/src/hyperopen/views/trade/order_form_intent_adapter.cljs`
- `/hyperopen/test/hyperopen/views/trade/order_form_commands_test.cljs`

## Interfaces and Dependencies

Required interfaces after completion:

- Order-form intent adapter function that maps semantic command maps to runtime action vectors.
- Public state helpers for splitting/merging UI-owned order-form fields at persistence boundaries.
- Extension invariant assertion function in order-form type extension layer.
- Application context function split into explicit state projection and pure composition.
- Closed schema validators for order-form VM and transition maps.
- test.check-driven generative model tests for order-form transitions.

Plan revision note: 2026-02-16 00:03Z - Initial active plan created for six-item SOLID/DDD completion slice.
Plan revision note: 2026-02-16 01:16Z - Marked all milestones complete, recorded dependency/invariant discoveries, and added validation evidence.
