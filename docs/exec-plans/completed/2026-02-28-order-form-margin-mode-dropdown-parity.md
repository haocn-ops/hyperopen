# Order-Form Margin Mode Dropdown Parity With Hyperliquid

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the first chip in the order-form control row will show the active margin mode (`Cross` or `Isolated`) instead of being hardcoded to `Cross`, and users will be able to click that chip and choose the mode from an inline dropdown. This mirrors Hyperliquid’s behavior that the margin-mode control is stateful per asset and user-selectable, while intentionally using a dropdown instead of Hyperliquid’s full modal to reduce interface interruption. A user can verify the behavior by opening trade, seeing the active label, opening the dropdown, selecting a mode, and seeing the label and stored UI state update immediately.

## Progress

- [x] (2026-02-28 14:44Z) Reviewed repository guardrails (`/hyperopen/ARCHITECTURE.md`, `/hyperopen/docs/RELIABILITY.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, `/hyperopen/docs/agent-guides/trading-ui-policy.md`) and order-form architecture files.
- [x] (2026-02-28 14:44Z) Researched Hyperliquid behavior from official/live sources (frontend bundle, Hyperliquid Python SDK, nktkas SDK examples).
- [x] (2026-02-28 15:36Z) Implemented margin-mode dropdown state, commands, transitions, handlers, registry wiring, and order-form view rendering.
- [x] (2026-02-28 15:52Z) Added and updated tests across order-form state/contracts/commands/transitions/view, plus impacted asset-selector and websocket schema fixtures.
- [x] (2026-02-28 15:58Z) Ran required validation gates successfully: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-02-28 16:02Z) Moved this plan to `/hyperopen/docs/exec-plans/completed/` after acceptance criteria passed.

## Surprises & Discoveries

- Observation: Hyperliquid’s live bundle shows margin mode changes are submitted via `updateLeverage` and pass the existing leverage with `isCross` flipped, while isolated-margin amount edits are a separate `updateIsolatedMargin` action.
  Evidence: In `https://app.hyperliquid.xyz/static/js/main.be4d3bab.js`, minified logic emits `{"type":"updateLeverage","asset":...,"isCross":...,"leverage":...}` for margin-mode change and `{"type":"updateIsolatedMargin",...}` for isolated margin add/remove.
- Observation: Existing Hyperopen order-form architecture already has reusable dropdown interaction patterns (size unit, TIF, TP/SL unit) with pure transitions and minimal action handlers; margin-mode can be integrated with the same pattern without touching transport/effects.
  Evidence: `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs` and `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs`.
- Observation: Several non-order-form tests compared full `:order-form-ui` maps inline and broke after adding new required UI keys.
  Evidence: `npm test` initially failed in `/hyperopen/test/hyperopen/core_bootstrap/asset_selector_actions_test.cljs`; replacing literal maps with `trading/default-order-form-ui` stabilized expectations.

## Decision Log

- Decision: Implement the user request as a dropdown-only UX in the order form and do not add a new full-screen/modal flow.
  Rationale: This is explicitly requested and keeps interaction density aligned with existing compact controls.
  Date/Author: 2026-02-28 / Codex
- Decision: Phase 1 implementation will update deterministic local UI state and parity affordance without adding a new API effect.
  Rationale: Hyperopen currently treats this chip row as order-form local state; adding signed `updateLeverage` submission would require new effect contracts, API wiring, and failure-handling UX. Shipping dropdown/state parity first resolves the direct discrepancy safely while preserving current order-flow invariants.
  Date/Author: 2026-02-28 / Codex
- Decision: Default margin mode is `:cross`, with optional hydration from position leverage type when present.
  Rationale: Existing behavior and user expectation are cross-default, while still allowing correct label when active position metadata includes isolated/cross typing.
  Date/Author: 2026-02-28 / Codex
- Decision: Keep `:margin-mode` UI-owned and out of persisted `:order-form` payloads, matching existing field-ownership policy.
  Rationale: Repository invariants separate UI state from persisted domain draft state; this avoids schema drift and keeps canonical order payload deterministic.
  Date/Author: 2026-02-28 / Codex

## Outcomes & Retrospective

The order-form leverage row now mirrors Hyperliquid’s margin-mode affordance without modal interruption: the first chip renders `Cross`/`Isolated` from effective UI state, opens a dropdown, supports outside-click and Escape close, and dispatches explicit mode-selection actions. The architecture remains deterministic and pure at transition level, with UI-first updates before heavy effects unchanged.

Implementation touched state normalization/ownership (`order_form_state`, `order_form_key_policy`, `state/trading`), transition/action/runtime wiring, command catalog/contracts, and the trade order-form view. Regression coverage was added for command translation, runtime gateway command exposure, view dropdown interactions, transition behavior, and schema fixtures.

Validation outcomes:

- `npm run check` passed.
- `npm test` passed (1521 tests, 7767 assertions).
- `npm run test:websocket` passed (153 tests, 701 assertions).

No remaining blockers for this scope. Follow-on work (out of scope for this plan) is wiring actual exchange-side `updateLeverage`/`isCross` submission if parity beyond UI state is desired.

## Context and Orientation

The order form is built from three layers. Pure transitions live in `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`; action handlers that convert transitions into effect vectors live in `/hyperopen/src/hyperopen/order/actions.cljs`; and view rendering with command handlers lives in `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs` plus `/hyperopen/src/hyperopen/views/trade/order_form_handlers.cljs` and `/hyperopen/src/hyperopen/views/trade/order_form_commands.cljs`.

Order-form command IDs are canonicalized in `/hyperopen/src/hyperopen/schema/order_form_command_catalog.cljs`. Runtime action registration depends on this catalog through `/hyperopen/src/hyperopen/registry/runtime.cljs` and the collaborators map in `/hyperopen/src/hyperopen/runtime/collaborators.cljs` and `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`.

Order-form UI state shape is strict and contract-checked. The default shape comes from `/hyperopen/src/hyperopen/trading/order_form_state.cljs`; key ownership is defined in `/hyperopen/src/hyperopen/state/trading/order_form_key_policy.cljs`; and app/action schema checks are in `/hyperopen/src/hyperopen/schema/contracts.cljs`. Tests assert exact key sets, so adding a UI key requires synchronized updates in contracts and tests.

## Plan of Work

First, extend order-form UI state to include margin-mode ownership and dropdown visibility. The canonical key set should include `:margin-mode` as a UI-owned field and `:margin-mode-dropdown-open?` as a UI flag. Add normalization helpers to coerce `cross` or `isolated` values and default to `cross`.

Second, add transition functions mirroring existing dropdown patterns. Create pure transitions to toggle, close, handle Escape keydown, and set selected margin mode. The set function must close the dropdown and clear runtime errors consistently with existing transitions.

Third, expose margin-mode interactions through command and runtime wiring. Add command builders in `order_form_commands.cljs`, catalog bindings in `order_form_command_catalog.cljs`, action specs in `schema/contracts.cljs`, and runtime collaborator/registry handler keys so dispatched commands resolve end-to-end.

Fourth, update the order form view. Replace the static left chip in `leverage-row` with an interactive chip-like dropdown control that displays current mode (`Cross` or `Isolated`) and provides both options. Keep keyboard close on Escape and outside-click close behavior consistent with existing dropdown affordances.

Fifth, update tests in every contract-sensitive layer. This includes key-policy and state defaults, schema contract fixtures, command coverage, transition behavior, and view assertions that currently expect hardcoded `Cross` only. Add at least one regression test for margin-mode dropdown open/close/select behavior and emitted actions.

Finally, run required validation gates and record outcomes. If all pass, move this plan to completed and summarize accepted behavior.

## Concrete Steps

From repository root `/Users//projects/hyperopen`:

1. Edit state and key ownership files:
   - `/hyperopen/src/hyperopen/trading/order_form_state.cljs`
   - `/hyperopen/src/hyperopen/state/trading/order_form_key_policy.cljs`
   - `/hyperopen/src/hyperopen/state/trading.cljs` (if helper plumbing is needed)
2. Edit transition/action/command/runtime wiring files:
   - `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`
   - `/hyperopen/src/hyperopen/order/actions.cljs`
   - `/hyperopen/src/hyperopen/views/trade/order_form_commands.cljs`
   - `/hyperopen/src/hyperopen/views/trade/order_form_handlers.cljs`
   - `/hyperopen/src/hyperopen/schema/order_form_command_catalog.cljs`
   - `/hyperopen/src/hyperopen/runtime/collaborators.cljs`
   - `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`
   - `/hyperopen/src/hyperopen/schema/contracts.cljs`
3. Edit rendering and VM files:
   - `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs`
   - `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs` (if new VM field is required)
4. Update tests:
   - `/hyperopen/test/hyperopen/state/trading/order_form_state_test.cljs`
   - `/hyperopen/test/hyperopen/state/trading/order_form_key_policy_test.cljs`
   - `/hyperopen/test/hyperopen/schema/contracts_test.cljs`
   - `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs`
   - `/hyperopen/test/hyperopen/views/trade/order_form_commands_test.cljs`
   - `/hyperopen/test/hyperopen/views/trade/order_form_runtime_gateway_test.cljs`
   - `/hyperopen/test/hyperopen/views/trade/order_form_view_test.cljs`
   - `/hyperopen/test/hyperopen/views/trade/order_form_view/size_and_slider_test.cljs` (if selector expectations overlap)
5. Run validations:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

## Validation and Acceptance

Acceptance is met when all of the following are true:

- The left control chip in order form shows `Cross` or `Isolated` based on current order-form UI state.
- Clicking the chip opens a dropdown with both options.
- Selecting an option updates displayed label immediately and closes the dropdown.
- Pressing Escape while dropdown is open closes it.
- Existing leverage and classic chips continue to render and behave as before.
- `npm run check`, `npm test`, and `npm run test:websocket` pass.

Manual verification scenario:

- Start app in development mode.
- Open `/trade`.
- Click the margin-mode chip.
- Select `Isolated`, confirm chip label changes.
- Reopen and select `Cross`, confirm chip label changes back.
- Use keyboard Escape while dropdown is open, confirm close.

## Idempotence and Recovery

These edits are additive and deterministic. Re-running normalization and tests should produce the same result. If any command wiring step fails, revert only the affected file edits and re-run the corresponding focused tests before re-running full validation gates.

## Artifacts and Notes

Research artifacts used for implementation decisions:

- Hyperliquid live frontend bundle path (as of 2026-02-28): `https://app.hyperliquid.xyz/static/js/main.be4d3bab.js`.
- Hyperliquid Python SDK action methods: `https://raw.githubusercontent.com/hyperliquid-dex/hyperliquid-python-sdk/master/hyperliquid/exchange.py`.
- nktkas SDK usage example including `updateLeverage`: `https://raw.githubusercontent.com/nktkas/hyperliquid/main/README.md`.

## Interfaces and Dependencies

New command/action interfaces to exist after implementation:

- Order-form commands:
  - `:order-form/toggle-margin-mode-dropdown`
  - `:order-form/close-margin-mode-dropdown`
  - `:order-form/handle-margin-mode-dropdown-keydown`
  - `:order-form/set-order-margin-mode`
- Runtime actions:
  - `:actions/toggle-margin-mode-dropdown`
  - `:actions/close-margin-mode-dropdown`
  - `:actions/handle-margin-mode-dropdown-keydown`
  - `:actions/set-order-margin-mode`
- Transition functions in `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`:
  - `toggle-margin-mode-dropdown`
  - `close-margin-mode-dropdown`
  - `handle-margin-mode-dropdown-keydown`
  - `set-order-margin-mode`

Revision note (2026-02-28): Initial plan created from repository and Hyperliquid source research before code edits.
Revision note (2026-02-28): Updated progress, discoveries, decisions, and outcomes after implementation/testing completion; added validation evidence and completion status before archiving plan to completed.
