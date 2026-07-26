# Order Form SOLID/DDD Slice 3 (Boundary Finalization and Component Decomposition)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

This slice completes the next seven SOLID/DDD refinements for the order form after slice 2. After these changes, order-form UI flags live only in `:order-form-ui`, the view becomes formatting-free assembly over VM data, order-type section rendering is data-driven, commands express domain intent instead of raw action payload shapes, component code is split by responsibility, and dedicated contracts/tests protect shared instrument and UI-state boundaries.

A user-visible way to see this working is to render the order form in tests and confirm behavior does not regress (dropdowns, TP/SL, scale/twap sections, submit tooltip), while validation gates stay green (`npm run check`, `npm test`, `npm run test:websocket`).

## Progress

- [x] (2026-02-15 20:31Z) Created this active ExecPlan for all seven requested follow-up items.
- [x] (2026-02-15 20:33Z) Removed legacy fallback from `order-form-ui-state` and updated tests/helpers to source UI flags from `:order-form-ui`.
- [x] (2026-02-15 20:35Z) Moved remaining view formatting into presenter/VM output (`order_form_presenter.cljs` + VM `:display` payload).
- [x] (2026-02-15 20:35Z) Replaced section branching with a config-driven section renderer registry map in section components.
- [x] (2026-02-15 20:36Z) Expanded command API with semantic intent helpers and migrated view/components to those helpers.
- [x] (2026-02-15 20:36Z) Split monolithic components namespace into primitives + sections modules and rewired the view.
- [x] (2026-02-15 20:37Z) Added explicit `OrderFormUIState` app-state contract validation and focused instrument parser domain tests.
- [x] (2026-02-15 20:38Z) Ran required validation gates and recorded outcomes (`check`, `test`, `test:websocket` all green).

## Surprises & Discoveries

- Observation: Several view tests pass UI flags in `order-form` overrides instead of `:order-form-ui`, so removing legacy fallback will break tests unless helpers are upgraded first.
  Evidence: `/hyperopen/test/hyperopen/views/trade/order_form_view_test.cljs` currently uses overrides like `:pro-order-type-dropdown-open?` and `:price-input-focused?` in the order-form test helper.

- Observation: The command semantic refactor did not require updating most interaction assertions because semantic helpers preserve the same action vectors.
  Evidence: `order_form_view_test.cljs` action assertions (for example select mode, dropdown toggle, slider input) continued to pass unchanged after command migration.

## Decision Log

- Decision: Keep runtime behavior unchanged while removing fallback by migrating test helpers to split UI overrides automatically.
  Rationale: This finalizes the boundary without forcing large repetitive fixture rewrites.
  Date/Author: 2026-02-15 / Codex

- Decision: Introduce a dedicated presenter namespace (`order_form_presenter.cljs`) instead of embedding new formatting helpers directly in the VM.
  Rationale: This keeps VM focused on orchestration and keeps formatting rules reusable and isolated.
  Date/Author: 2026-02-15 / Codex

- Decision: Delete the old monolithic components namespace after splitting primitives/sections rather than keeping a compatibility wrapper.
  Rationale: No other namespace imported it, so deletion avoids duplicate ownership and enforces the new module boundaries immediately.
  Date/Author: 2026-02-15 / Codex

## Outcomes & Retrospective

All seven planned items were implemented in this slice. The order-form UI-state boundary is now strict (`:order-form-ui` only), view formatting is moved to VM/presenter output, order-type sections are registry-driven, command call sites are semantic, and components are split into primitives and section modules. Schema contracts now validate UI-state booleans explicitly, and shared instrument parsing has focused domain tests.

Validation succeeded across all required gates, with no compile warnings and full test pass. The main remaining work for future slices is incremental ergonomics (for example further reducing view surface area by extracting the submit button/tooltip block into its own section component if desired), not correctness or architecture boundary gaps from this seven-item scope.

## Context and Orientation

Relevant files for this slice:

- `/hyperopen/src/hyperopen/state/trading.cljs` currently computes `order-form-ui-state` with a legacy fallback from `:order-form` flags.
- `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs` still contains formatting helpers (`format-usdc`, percent formatting, position label formatting).
- `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs` already holds order-type config, submit policy usage, and most derived values.
- `/hyperopen/src/hyperopen/views/trade/order_form_commands.cljs` has command wrappers but still exposes generic path/value command shape.
- `/hyperopen/src/hyperopen/views/trade/order_form_component_primitives.cljs` and `/hyperopen/src/hyperopen/views/trade/order_form_component_sections.cljs` now own low-level primitives vs. form-section composition.
- `/hyperopen/src/hyperopen/schema/contracts.cljs` requires `:order-form-ui` but does not define a dedicated UI-state contract.
- `/hyperopen/src/hyperopen/domain/market/instrument.cljs` contains shared parser logic and needs focused tests.

In this repository, a "VM" (view model) means a plain map of display-ready values for rendering. A "presenter" helper means pure formatting functions that convert raw numeric/domain fields into display strings. "UI state" means ephemeral presentation flags (dropdown open, focus lock, TP/SL panel open), not trade intent fields.

## Plan of Work

Milestone 1 finalizes the UI-state boundary. I will remove legacy fallback reads from `:order-form` in `order-form-ui-state` and keep only normalized `:order-form-ui` + type invariants. I will update tests/helpers that currently rely on legacy fallback.

Milestone 2 moves remaining formatting from the view into VM output. The VM will return display-ready metrics and labels so `order_form_view.cljs` no longer formats amounts/percentages directly.

Milestone 3 makes section rendering fully data-driven by replacing `case` branching with a section renderer registry map keyed by section keyword.

Milestone 4 upgrades command intent naming and signatures so view/components never construct raw action payload paths/events directly. Generic update-path helpers may remain internal but UI call sites will use semantic APIs.

Milestone 5 splits component code into two modules: primitives (buttons/inputs/toggles/rows) and sections (entry tabs, TP/SL, order-type section renderers). The view will import from both modules.

Milestone 6 adds explicit `OrderFormUIState` schema contract checks and dedicated domain tests for `instrument.cljs` parser behavior (delimiter precedence, defaults, spot/hip3 inference, identity map output).

Milestone 7 runs and records the required gates.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/state/trading.cljs` to remove legacy fallback behavior in `order-form-ui-state`.
2. Update tests under `/hyperopen/test/hyperopen/state/trading_test.cljs` and `/hyperopen/test/hyperopen/views/trade/order_form_view_test.cljs` so UI flags are provided via `:order-form-ui`.
3. Edit `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs` to compute display strings currently formatted in the view.
4. Edit `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs` to consume VM-presented strings only.
5. Replace section branching in component rendering with a renderer map.
6. Edit `/hyperopen/src/hyperopen/views/trade/order_form_commands.cljs` and replace call sites in view/components with semantic command names.
7. Split `/hyperopen/src/hyperopen/views/trade/order_form_components.cljs` into two namespaces and update imports.
8. Extend `/hyperopen/src/hyperopen/schema/contracts.cljs` with dedicated `OrderFormUIState` specs and add/adjust contract tests.
9. Add `/hyperopen/test/hyperopen/domain/market/instrument_test.cljs`, then wire it into `/hyperopen/test/test_runner.cljs`.
10. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

## Validation and Acceptance

Acceptance criteria:

- `order-form-ui-state` reads only `:order-form-ui` and applies invariants, with no legacy fallback from `:order-form` UI keys.
- `order_form_view.cljs` no longer defines local formatting helper functions for metrics/percent labels.
- Section rendering uses config/registry lookup (no `case` tree for order-type sections).
- View/component call sites use semantic command helpers instead of raw path/event payload details.
- Components are split into at least two responsibility-focused namespaces and the view uses them.
- App-state contract validates `:order-form-ui` against explicit key-level rules.
- Instrument parser tests cover representative delimiter/inference/default scenarios.
- Required gates pass.

## Idempotence and Recovery

The steps are additive and safe to rerun. If a mid-slice compile failure occurs during module splitting, recovery is to temporarily keep a thin compatibility namespace that re-exports moved functions, then continue migrating imports incrementally until tests pass.

## Artifacts and Notes

Validation transcripts from `/hyperopen`:

    npm run check
    ...
    [:app] Build completed. (... 0 warnings ...)
    [:test] Build completed. (... 0 warnings ...)

    npm test
    ...
    Ran 806 tests containing 3145 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    ...
    Ran 86 tests containing 267 assertions.
    0 failures, 0 errors.

Key file additions/splits:

- `/hyperopen/src/hyperopen/views/trade/order_form_presenter.cljs`
- `/hyperopen/src/hyperopen/views/trade/order_form_component_primitives.cljs`
- `/hyperopen/src/hyperopen/views/trade/order_form_component_sections.cljs`
- `/hyperopen/test/hyperopen/domain/market/instrument_test.cljs`

## Interfaces and Dependencies

Interfaces that must exist after this slice:

- `/hyperopen/src/hyperopen/state/trading.cljs`: `order-form-ui-state` uses only `:order-form-ui` + invariants.
- `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs`: returns presentation-ready metric strings for the view.
- `/hyperopen/src/hyperopen/views/trade/order_form_commands.cljs`: semantic command functions for field and toggle intent.
- `/hyperopen/src/hyperopen/views/trade/order_form_component_primitives.cljs`: reusable low-level UI primitives.
- `/hyperopen/src/hyperopen/views/trade/order_form_component_sections.cljs`: section-specific renderers and order-type renderer registry.
- `/hyperopen/src/hyperopen/schema/contracts.cljs`: explicit `OrderFormUIState` spec integrated into `::app-state`.
- `/hyperopen/test/hyperopen/domain/market/instrument_test.cljs`: focused parser and identity tests.

Plan revision note: 2026-02-15 20:31Z - Initial plan created for full seven-item SOLID/DDD follow-up slice.
Plan revision note: 2026-02-15 20:38Z - Updated living sections with implementation outcomes, decisions, discoveries, and validation evidence after completing all seven items.
