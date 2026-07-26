# Order Form SOLID/DDD Slice 4 (Transition Purity and Aggregate Hardening)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

This slice finishes the next seven architecture improvements for the order form by moving behavior out of action handlers into pure transitions, decoupling UI components from command wiring, separating order draft data from runtime workflow state, introducing explicit value-object helpers for core order fields, making market identity canonical-first, and adding stronger contract/invariant tests.

After this change, contributors can modify order behavior in pure functions without touching effect code, and the UI reads a clearer domain/application boundary: order draft (`:order-form`), UI flags (`:order-form-ui`), and runtime workflow (`:order-form-runtime`).

## Progress

- [x] (2026-02-15 20:46Z) Created this active ExecPlan for the seven requested SOLID/DDD follow-up tasks.
- [x] (2026-02-15 20:50Z) Extracted pure transitions into `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs` and rewired `/hyperopen/src/hyperopen/order/actions.cljs` to adapter-style effect emission.
- [x] (2026-02-15 20:52Z) Decoupled section components from command namespace; view now injects callback payloads into `/hyperopen/src/hyperopen/views/trade/order_form_component_sections.cljs`.
- [x] (2026-02-15 20:53Z) Split runtime workflow state into `:order-form-runtime` across defaults, selectors, effects, VM, and schema contracts.
- [x] (2026-02-15 20:54Z) Added value-object helpers in `/hyperopen/src/hyperopen/domain/trading/order_values.cljs` and integrated them into normalization/transitions.
- [x] (2026-02-15 20:55Z) Updated instrument inference to canonical-market-first behavior in `/hyperopen/src/hyperopen/domain/market/instrument.cljs`.
- [x] (2026-02-15 20:56Z) Added order-type plugin contract coverage and property-style invariant tests (`order_form_vm_test`, `order_form_transitions_test`, `order_values_test`).
- [x] (2026-02-15 20:58Z) Ran required gates successfully (`npm run check`, `npm test`, `npm run test:websocket`).

## Surprises & Discoveries

- Observation: The current runtime path still stores order submit workflow status in the order draft map, and both actions and effects mutate `[:order-form :error]` and `[:order-form :submitting?]` directly.
  Evidence: `/hyperopen/src/hyperopen/order/actions.cljs` and `/hyperopen/src/hyperopen/order/effects.cljs` both reference these keys.

- Observation: A threaded `reduce` form in `normalize-order-form` (`->` + `reduce`) produced invalid argument ordering and broad runtime failures.
  Evidence: Initial test run produced 97 errors with `No protocol method IAssociative.-assoc defined for type cljs.core/Keyword: :error`; fixed by replacing threaded `reduce` with explicit nested `reduce` calls.

- Observation: Canonical-first market identity changed read-only spot inference in tests that relied on slash-delimited active asset while market-type was explicitly `:perp`.
  Evidence: `order-form-vm-read-only-identity-test` failed until fixture market-type was set to `:spot`.

## Decision Log

- Decision: Implement transition extraction in an application namespace (`/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`) rather than directly inside action handlers.
  Rationale: This keeps behavior pure while preserving existing action/effect contracts.
  Date/Author: 2026-02-15 / Codex

- Decision: Keep public action/effect IDs and payload contracts stable while migrating runtime state path to `:order-form-runtime`.
  Rationale: This minimizes integration risk outside the order-form slice while still enforcing aggregate separation.
  Date/Author: 2026-02-15 / Codex

- Decision: Use callback-injected section components instead of requiring command functions within section namespaces.
  Rationale: This removes framework-specific coupling from reusable UI section renderers and improves substitutability.
  Date/Author: 2026-02-15 / Codex

## Outcomes & Retrospective

All seven scoped improvements were implemented. Order-form transitions are now pure and centralized, section components are decoupled from command wiring, runtime workflow state is explicitly separated from order draft state, value-object helpers exist and are integrated, canonical market metadata now takes precedence in identity inference, and new contract/invariant tests protect the refactor.

Validation passed across required gates. The residual future work is optional hardening (for example adding transition-level generators beyond current property-style loops), not missing coverage for the seven requested SOLID/DDD tasks.

## Context and Orientation

Key current files:

- `/hyperopen/src/hyperopen/order/actions.cljs` currently computes all order-form transitions inline and then emits effects.
- `/hyperopen/src/hyperopen/order/effects.cljs` currently mutates submit runtime state at `:order-form` paths.
- `/hyperopen/src/hyperopen/trading/order_form_state.cljs` currently defines order draft defaults and still includes runtime keys.
- `/hyperopen/src/hyperopen/views/trade/order_form_component_sections.cljs` currently hardcodes command namespace usage.
- `/hyperopen/src/hyperopen/domain/market/instrument.cljs` still includes string heuristics that can override explicit market metadata.

In this plan, “transition” means a pure function that takes current state and intent input and returns new state slices (form/ui/runtime) without side effects. “Runtime workflow state” means submit lifecycle fields (`submitting`, submit error), distinct from order draft fields required to build an order request.

## Plan of Work

Milestone 1 introduces pure transitions in a dedicated namespace and rewires actions to call them. Actions become thin adapters that only construct effect vectors.

Milestone 2 splits `:order-form-runtime` from `:order-form` and updates defaults, schema contracts, VM reads, action writes, and submit effects accordingly.

Milestone 3 decouples section components from command namespace. View builds callback/event payloads and passes them into component functions.

Milestone 4 introduces order value-object helpers and integrates them where normalization/transition logic currently manipulates raw values ad hoc.

Milestone 5 updates instrument identity to prefer canonical market metadata and use delimiter heuristics only when canonical fields are missing.

Milestone 6 adds tests: plugin-contract tests for order-type configuration and property-style invariant tests for transitions/submit policy.

Milestone 7 runs required validation gates and updates this plan’s living sections with evidence.

## Concrete Steps

From `/hyperopen`:

1. Add `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs` and move transition logic out of `/hyperopen/src/hyperopen/order/actions.cljs`.
2. Update `/hyperopen/src/hyperopen/order/actions.cljs` to delegate to transitions and keep effect emission only.
3. Add runtime state support in `/hyperopen/src/hyperopen/trading/order_form_state.cljs`, `/hyperopen/src/hyperopen/state/trading.cljs`, `/hyperopen/src/hyperopen/state/app_defaults.cljs`, `/hyperopen/src/hyperopen/system.cljs`, `/hyperopen/src/hyperopen/schema/contracts.cljs`, `/hyperopen/src/hyperopen/order/effects.cljs`, `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs`.
4. Refactor `/hyperopen/src/hyperopen/views/trade/order_form_component_sections.cljs` to accept callbacks/handlers instead of importing commands; update `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs`.
5. Add value-object namespace (order field wrappers/normalizers) and integrate in trading transitions/normalization flow.
6. Update `/hyperopen/src/hyperopen/domain/market/instrument.cljs` to canonical-first identity/type inference and adjust affected tests.
7. Add tests:
   - `/hyperopen/test/hyperopen/views/trade/order_form_vm_test.cljs` plugin contract coverage.
   - `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs` property-style invariants.
   - Update existing state/core/schema/view tests for `:order-form-runtime` path changes.
8. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

## Validation and Acceptance

Acceptance criteria:

- Transition behavior in actions is delegated to pure transition functions in a separate namespace.
- Section components no longer import command modules directly.
- Submit runtime state is stored under `:order-form-runtime`, not in `:order-form`.
- Value-object helpers exist and are used for core field normalization/interpretation.
- Instrument market identity honors explicit market metadata before falling back to string heuristics.
- Order-type plugin contract tests and transition/policy invariant tests exist and pass.
- Required validation gates pass.

## Idempotence and Recovery

All changes are source-only and safe to rerun. If regressions appear after state-path migration, the safe recovery is to keep new runtime state path and add temporary compatibility reads in VM/tests only (without reintroducing writes to old paths), then finish migration incrementally.

## Artifacts and Notes

Validation outputs from `/hyperopen`:

    npm run check
    ...
    [:app] Build completed. (... 0 warnings ...)
    [:test] Build completed. (... 0 warnings ...)

    npm test
    ...
    Ran 813 tests containing 3275 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    ...
    Ran 86 tests containing 267 assertions.
    0 failures, 0 errors.

Key new files:

- `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`
- `/hyperopen/src/hyperopen/domain/trading/order_values.cljs`
- `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs`
- `/hyperopen/test/hyperopen/domain/trading/order_values_test.cljs`

## Interfaces and Dependencies

Required interfaces after this slice:

- `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`: pure transition functions returning next form/ui/runtime maps.
- `/hyperopen/src/hyperopen/state/trading.cljs`: selectors for `order-form-runtime` and value-object-aware normalization hooks.
- `/hyperopen/src/hyperopen/views/trade/order_form_component_sections.cljs`: component functions parameterized by callbacks.
- `/hyperopen/src/hyperopen/domain/trading/order_values.cljs` (or equivalent): explicit value-object constructors/normalizers.
- `/hyperopen/src/hyperopen/domain/market/instrument.cljs`: canonical-first market identity behavior.

Plan revision note: 2026-02-15 20:46Z - Initial plan created for seven-item transition/runtime/value-object/domain hardening slice.
Plan revision note: 2026-02-15 20:58Z - Updated living sections after implementing all seven milestones, fixing migration issues, and passing required validation gates.
