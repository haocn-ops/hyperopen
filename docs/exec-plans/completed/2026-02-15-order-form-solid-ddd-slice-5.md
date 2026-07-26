# Order Form SOLID/DDD Slice 5 (VM Decomposition, Registry Consolidation, Contracts, and Invariants)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The order form is already split into view, VM, transitions, and domain adapters, but the VM still carries too much orchestration and the order-type extension surface is still concentrated in one VM namespace. After this slice, one canonical order-type registry will drive labels/options/sections, VM derivation will be split into smaller selector modules, command/event wiring will be isolated from render layout, and explicit contracts plus stronger invariant tests will protect the boundaries. A user-visible result is unchanged behavior with lower regression risk when adding a new order type or changing submit policy.

## Progress

- [x] (2026-02-15 23:14Z) Created active ExecPlan with seven-item scope, milestones, and validation gates.
- [x] (2026-02-15 23:16Z) Extracted VM orchestration helpers into `/hyperopen/src/hyperopen/views/trade/order_form_vm_selectors.cljs` and `/hyperopen/src/hyperopen/views/trade/order_form_vm_submit.cljs`; reduced `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs` to composition.
- [x] (2026-02-15 23:17Z) Introduced canonical registry in `/hyperopen/src/hyperopen/trading/order_type_registry.cljs` and rewired VM order-type helpers to use it.
- [x] (2026-02-15 23:18Z) Tightened aggregate boundaries by adding `raw-order-form-draft` and `order-form-draft` selectors and routing transition/action reads through those selectors.
- [x] (2026-02-15 23:19Z) Added canonical `market-info` selector in `/hyperopen/src/hyperopen/state/trading.cljs` and removed ad hoc `:active-market` shape reads from VM.
- [x] (2026-02-15 23:20Z) Added `/hyperopen/src/hyperopen/views/trade/order_form_handlers.cljs` and switched `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs` to handler-map wiring.
- [x] (2026-02-15 23:22Z) Added explicit contracts in `/hyperopen/src/hyperopen/trading/order_form_contracts.cljs` and asserted them in VM/transition tests.
- [x] (2026-02-15 23:24Z) Extended invariants in `/hyperopen/test/hyperopen/views/trade/order_form_vm_test.cljs`, `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs`, and `/hyperopen/test/hyperopen/state/trading_test.cljs`.
- [x] (2026-02-15 23:26Z) Ran required gates with passing results: `npm run check`, `npm test`, `npm run test:websocket`.

## Surprises & Discoveries

- Observation: Previous slices already moved a substantial amount of policy into `order_form_vm.cljs` and pure transitions, so this slice is mostly architectural hardening rather than behavior migration.
  Evidence: `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs` is primarily rendering and dispatch wiring; `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs` is already pure.
- Observation: Shadow-cljs flagged `market-info` as using undeclared vars because it was added above `market-identity` and `market-max-leverage`.
  Evidence: `npm run check` emitted `:undeclared-var` warnings at `/hyperopen/src/hyperopen/state/trading.cljs:106` and `:109`; adding `declare` entries removed warnings.
- Observation: Existing order-form view tests were stable despite handler-map indirection because the resulting action vectors remained unchanged.
  Evidence: `/hyperopen/test/hyperopen/views/trade/order_form_view_test.cljs` passed unchanged under `npm test`.

## Decision Log

- Decision: Implement this slice as additive modules plus small rewires, preserving existing public action IDs and most view structure.
  Rationale: This gives stronger SOLID/DDD boundaries with low behavior risk and minimal churn in snapshot-like view tests.
  Date/Author: 2026-02-15 / Codex
- Decision: Keep contracts as lightweight predicate-based validators in source (`order_form_contracts.cljs`) instead of introducing broader spec instrumentation.
  Rationale: The goal is boundary protection in tests with minimal runtime overhead and no additional instrumentation setup.
  Date/Author: 2026-02-15 / Codex
- Decision: Reject legacy UI/runtime paths in `transitions/update-order-form` rather than silently persisting them into the order draft.
  Rationale: This enforces aggregate boundaries in one place and prevents drift back toward mixed UI/domain draft state.
  Date/Author: 2026-02-15 / Codex

## Outcomes & Retrospective

This slice delivered all seven scoped improvements. The VM is now a composition layer over focused selector and submit-policy helpers; order-type metadata is centralized in one registry; market and draft selectors in `state/trading` now provide explicit boundaries; and the view consumes a handlers adapter instead of direct command wiring throughout layout code.

Contracts and stronger invariants were added and are now enforced by tests, including cross-order-type VM checks, transition shape checks, and aggregate-boundary checks that reject UI/runtime paths in `update-order-form`.

Validation gates passed with zero failures. Remaining future work is optional deeper decomposition (for example section-specific container components), not missing scope from this seven-item slice.

## Context and Orientation

Relevant files and roles:

- `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs` renders the ticket and currently imports commands directly.
- `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs` currently combines selector orchestration, order-type config, formatting assembly, and submit tooltip policy.
- `/hyperopen/src/hyperopen/views/trade/order_form_component_sections.cljs` renders section blocks keyed by section IDs.
- `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs` holds pure state transitions and enforces form/ui/runtime updates.
- `/hyperopen/src/hyperopen/state/trading.cljs` is the boundary over domain functions and state shape.
- `/hyperopen/test/hyperopen/views/trade/order_form_vm_test.cljs` and `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs` provide current coverage.

In this plan, “registry” means a data map that defines order-type labels, dropdown order, and section keys in one place. “Contract” means an explicit shape validator for VM and transition return maps that tests can assert across many scenarios.

## Plan of Work

Milestone 1 decomposes VM orchestration into small modules. Create selector/policy namespaces for order-type registry access, submit tooltip policy, and derived display calculations; then trim `order_form_vm.cljs` to composition.

Milestone 2 centralizes order-type extension points. Introduce a canonical order-type registry namespace and route VM consumers through it so labels/options/sections are no longer local VM constants.

Milestone 3 hardens aggregate boundaries and market-info interfaces. Add selectors that expose draft/runtime/UI and market-info explicitly from `state/trading`, then replace VM direct state-shape reads with those selectors.

Milestone 4 isolates command wiring from layout. Add a handlers adapter namespace that builds all command payload vectors and pass handler maps into view sections, reducing direct dependency on command functions in render code.

Milestone 5 introduces source-level VM/transition contracts and extends tests to enforce them plus broader invariants across order types and transition paths.

Milestone 6 runs required validation gates and updates this plan’s living sections with outcomes.

## Concrete Steps

From `/hyperopen`:

1. Add selector/policy/registry modules under `/hyperopen/src/hyperopen/views/trade/` and `/hyperopen/src/hyperopen/trading/`.
2. Refactor `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs` to compose those modules.
3. Add aggregate and market-info selectors in `/hyperopen/src/hyperopen/state/trading.cljs` and update VM/action usage.
4. Add `/hyperopen/src/hyperopen/views/trade/order_form_handlers.cljs` and switch `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs` to consume handler maps.
5. Add contracts namespace and wire tests in:
   - `/hyperopen/test/hyperopen/views/trade/order_form_vm_test.cljs`
   - `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs`
6. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

Expected transcript shape:

    npm run check
    ...
    Build completed.

    npm test
    ...
    0 failures, 0 errors.

    npm run test:websocket
    ...
    0 failures, 0 errors.

## Validation and Acceptance

Acceptance criteria:

- `order_form_vm.cljs` no longer owns registry constants or most helper policy logic; it composes focused modules.
- One canonical registry defines order-type labels/options/sections used by VM rendering pathways.
- VM consumes explicit market-info selectors rather than raw active-market field plucks.
- View layout is wired through a handlers adapter namespace rather than direct command namespace calls throughout render blocks.
- Source-level contract helpers exist for VM and transition outputs and are asserted in tests.
- Expanded tests cover all registry order types for VM invariants and key transition invariants.
- Required validation gates pass.

## Idempotence and Recovery

This refactor is additive-first and safe to rerun. If a regression occurs, keep new modules in place and temporarily route call sites back to previous helpers while retaining tests, then rewire incrementally. No schema migration or destructive operation is required.

## Artifacts and Notes

Validation outputs from `/hyperopen`:

    npm run check
    ...
    [:app] Build completed. (... 0 warnings ...)
    [:test] Build completed. (... 0 warnings ...)

    npm test
    ...
    Ran 817 tests containing 3354 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    ...
    Ran 86 tests containing 267 assertions.
    0 failures, 0 errors.

Key new files:

- `/hyperopen/src/hyperopen/trading/order_type_registry.cljs`
- `/hyperopen/src/hyperopen/views/trade/order_form_vm_selectors.cljs`
- `/hyperopen/src/hyperopen/views/trade/order_form_vm_submit.cljs`
- `/hyperopen/src/hyperopen/views/trade/order_form_handlers.cljs`
- `/hyperopen/src/hyperopen/trading/order_form_contracts.cljs`

## Interfaces and Dependencies

Required interfaces by completion:

- `/hyperopen/src/hyperopen/trading/order_type_registry.cljs` exposes order-type registry and helper functions for labels/options/sections.
- `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs` composes smaller modules and re-exports compatibility helpers used by existing tests.
- `/hyperopen/src/hyperopen/views/trade/order_form_handlers.cljs` exposes a handler map builder used by the view.
- `/hyperopen/src/hyperopen/state/trading.cljs` provides explicit market-info and draft/runtime selector helpers consumed by VM/actions.
- `/hyperopen/src/hyperopen/trading/order_form_contracts.cljs` (or equivalent) provides VM/transition shape validators.

Plan revision note: 2026-02-15 23:14Z - Initial plan created for SOLID/DDD slice 5 (VM decomposition, registry consolidation, contract and invariant hardening).
Plan revision note: 2026-02-15 23:26Z - Updated living sections after completing implementation, fixing compile warnings, and passing all required validation gates.
