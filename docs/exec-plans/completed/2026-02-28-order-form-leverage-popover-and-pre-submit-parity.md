# Order-Form Leverage Popover And Submit Parity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Users currently see the leverage chip value change when clicked, but the interaction does not provide the expected Hyperliquid-style leverage editor and, in some runtime market-shape cases, the selected leverage can be skipped from pre-submit synchronization. After this change, the order form will provide an `Adjust Leverage` popover (slider + numeric input + confirm), and order submission will reliably send an `updateLeverage` pre-action for perp markets before placing the order.

A user will be able to: open the leverage popover from the top-row leverage button, set leverage explicitly, confirm, place an order, and observe that pre-submit leverage sync is included in the request path that runs before order placement.

## Progress

- [x] (2026-02-28 15:37Z) Inspected current order-form leverage UI and submit flow (`order_form_view`, transitions/actions, request builder, submit effects) and confirmed existing pre-submit support path.
- [x] (2026-02-28 15:37Z) Cross-checked Hyperliquid docs and SDK behavior for leverage sync action shape (`type=updateLeverage`, `asset`, `isCross`, `leverage`).
- [x] (2026-02-28 15:43Z) Implemented leverage popover state/actions/commands/transitions, including draft leverage and confirm behavior.
- [x] (2026-02-28 15:44Z) Replaced leverage cycle UI with Hyperliquid-style popover in `order_form_view`, including keyboard close and overlay-close behavior.
- [x] (2026-02-28 15:44Z) Hardened request builder fallback so pre-submit leverage sync is emitted whenever market identity is effectively perp, even if `:market-type` is temporarily missing.
- [x] (2026-02-28 15:45Z) Added/updated tests for transitions, command catalog/gateway coverage, view interaction wiring, schema contracts, and order request pre-actions.
- [x] (2026-02-28 15:46Z) Ran required gates successfully: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-02-28 15:46Z) Updated final outcomes and moved this plan to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The submit pipeline already supports serial pre-submit actions and fail-fast behavior before order placement.
  Evidence: `/hyperopen/src/hyperopen/order/effects.cljs` `run-pre-submit-actions!` and `api-submit-order`.

- Observation: Existing pre-submit leverage builder is gated on strict `:market-type :perp`; if market metadata is temporarily partial, leverage sync can be omitted.
  Evidence: `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs` `build-update-leverage-action`.

- Observation: Calling a helper before its definition in `order_form_transitions.cljs` triggered `:undeclared-var` compiler warnings in `check`.
  Evidence: `npm run check` warning for `current-ui-leverage` before adding `(declare ...)`.

## Decision Log

- Decision: Keep leverage synchronization as a submit-time pre-action and do not emit network effects from leverage UI editing interactions.
  Rationale: This preserves deterministic UI responsiveness and keeps heavy side effects bound to submit intent.
  Date/Author: 2026-02-28 / Codex

- Decision: Introduce an explicit leverage draft value in `:order-form-ui` that is committed only on confirm.
  Rationale: Matches requested UX (`Adjust Leverage` popover + `Confirm`) and avoids accidental leverage commits while sliding.
  Date/Author: 2026-02-28 / Codex

- Decision: Treat markets as perp for leverage pre-action eligibility unless market type is explicitly spot (or instrument format is spot-like `BASE/QUOTE`).
  Rationale: Prevents silent omission of leverage sync under partial market metadata while still excluding spot-like instruments.
  Date/Author: 2026-02-28 / Codex

## Outcomes & Retrospective

Implemented end-to-end leverage UX and submit reliability:

- Added a leverage popover workflow (`toggle`, `draft`, `confirm`, `close`) with explicit UI state ownership.
- Replaced leverage cycle button UX with a side popover matching requested interaction model.
- Ensured submit request pre-actions include `updateLeverage` under perp fallback inference when market type metadata is missing.
- Preserved existing deterministic submit pipeline (pre-actions still run before order action).

Validation results:

- `npm run check` passed.
- `npm test` passed (1530 tests, 7900 assertions).
- `npm run test:websocket` passed (153 tests, 701 assertions).

## Context and Orientation

The order form has three relevant layers:

1. View layer: `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs` renders the top strip (`Cross`, leverage chip, `Classic`) and dispatches order-form commands through `/hyperopen/src/hyperopen/views/trade/order_form_handlers.cljs`.
2. Transition/action layer: `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs` and `/hyperopen/src/hyperopen/order/actions.cljs` own deterministic order-form state transitions and persistence effects into `:order-form` and `:order-form-ui`.
3. Submit/request layer: `/hyperopen/src/hyperopen/state/trading.cljs` builds submit policy and delegates to `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs` to build request payloads (including optional `:pre-actions`). Runtime execution in `/hyperopen/src/hyperopen/order/effects.cljs` runs pre-actions first.

Key protocol fact used for parity (embedded here so the plan is self-contained): Hyperliquid leverage updates are signed exchange actions with this shape:

- `{"type":"updateLeverage","asset":<unsigned int>,"isCross":<boolean>,"leverage":<unsigned int>=1}`

This shape is reflected in Hyperliquid docs and in the three SDK references listed in `/hyperopen/docs/references/hyperliquid-sdks.md`.

## Plan of Work

Implement the UX and functional fix in small vertical slices.

First, add leverage popover UI state to normalized `order-form-ui` and key-policy ownership sets so app-state contracts remain strict and deterministic. Add transitions/actions/commands for opening/closing the popover, keyboard escape close, editing a draft leverage value, and confirming that draft into canonical `:ui-leverage`.

Second, replace the current leverage cycle button rendering with a popover component in `order_form_view`. The popover includes title, max leverage readout, max position size placeholder (`N/A`), slider, numeric field, and confirm button. Open/close is managed by runtime actions; overlay click and Escape close the popover.

Third, harden request builder leverage pre-action eligibility. Instead of requiring only `:market-type :perp`, infer perp eligibility with deterministic fallback when market type is missing (treat explicit spot as spot; otherwise default to perp when active instrument is non-spot). Keep action payload shape unchanged.

Fourth, update tests that cover command catalog bindings, runtime gateway supported ids, transitions, view wiring, schema contracts for `:order-form-ui`, and order request pre-action behavior.

## Concrete Steps

From `/Users//projects/hyperopen`:

1. Edit order-form UI state and ownership policy:
   - `/hyperopen/src/hyperopen/trading/order_form_state.cljs`
   - `/hyperopen/src/hyperopen/state/trading/order_form_key_policy.cljs`
   - `/hyperopen/src/hyperopen/state/trading.cljs`
   - `/hyperopen/src/hyperopen/schema/contracts.cljs`

2. Add leverage popover command/action plumbing:
   - `/hyperopen/src/hyperopen/views/trade/order_form_commands.cljs`
   - `/hyperopen/src/hyperopen/schema/order_form_command_catalog.cljs`
   - `/hyperopen/src/hyperopen/views/trade/order_form_handlers.cljs`
   - `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`
   - `/hyperopen/src/hyperopen/order/actions.cljs`
   - `/hyperopen/src/hyperopen/runtime/collaborators.cljs`
   - `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`

3. Render leverage popover and style it:
   - `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs`
   - `/hyperopen/src/styles/main.css` (only if additional slider/track styles are needed)

4. Harden request builder leverage pre-action fallback:
   - `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs`

5. Update tests:
   - `/hyperopen/test/hyperopen/views/trade/order_form_view_test.cljs`
   - `/hyperopen/test/hyperopen/views/trade/order_form_view/size_and_slider_test.cljs`
   - `/hyperopen/test/hyperopen/views/trade/order_form_commands_test.cljs`
   - `/hyperopen/test/hyperopen/views/trade/order_form_runtime_gateway_test.cljs`
   - `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs`
   - `/hyperopen/test/hyperopen/api/gateway/orders/commands_test.cljs`
   - `/hyperopen/test/hyperopen/schema/contracts_test.cljs`
   - `/hyperopen/test/hyperopen/state/trading/order_form_state_test.cljs`

6. Run validation:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

## Validation and Acceptance

Acceptance is met when all of the following are true:

- Clicking the top-row leverage button opens an `Adjust Leverage` popover with slider, numeric input, and confirm action.
- Escape key and overlay click close the leverage popover.
- Confirm updates displayed leverage chip text (for example `20x` to `12x`) and persists through order-form UI normalization.
- Submitting a perp order includes `:pre-actions` with one `updateLeverage` action reflecting selected leverage and margin mode.
- Leverage pre-action still appears when market metadata is partial but the instrument is effectively perp.
- Required validation gates pass.

## Idempotence and Recovery

Edits are additive and safe to apply incrementally. If a test fails mid-way, run the specific failing namespace first, then full suite. If UI wiring fails, keep request-builder hardening and popover state plumbing in place, then iterate on view tests until command/action payloads align.

## Artifacts and Notes

Embedded protocol parity facts used for implementation:

- Hyperliquid docs `updateLeverage` action fields: `asset`, `isCross`, `leverage`.
- Hyperliquid Python SDK `exchange.py` `update_leverage` constructs exactly those fields.
- nktkas TypeScript SDK `updateLeverage.ts` validates the same shape and minimum leverage of `1`.
- nomeida SDK `exchange.ts` `updateLeverage` maps leverage mode to `isCross` and posts signed action.

## Interfaces and Dependencies

Public/runtime interfaces affected:

- New order-form command IDs and matching action IDs for leverage popover lifecycle and confirm flow.
- `:order-form-ui` schema shape gains leverage popover state fields and must remain exactly-keyed by contract.

No transport/signing interface changes are required. Existing signed action path (`trading-api/submit-order!`) remains authoritative.

Revision note (2026-02-28): Initial plan created after code-path and SDK/doc parity research to implement leverage UX + submit reliability end-to-end.
Revision note (2026-02-28): Updated after implementation with completed milestones, test evidence, and final outcomes before archival.
