# Position TP/SL Configure Amount Slider + Percent Controls

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the Position TP/SL modal in Hyperopen will provide a full "Configure Amount" control surface instead of only a single amount text input. When the user enables "Configure Amount", they will see controls under the toggle that include:

- a configurable amount input,
- a percentage slider,
- and a percent input box.

All three controls will stay in sync and represent a reduce-only TP/SL size between `0%` and `100%` of the current position size. This mirrors the expected interaction model in modern perp UIs while keeping Hyperopen’s existing submit and validation pipeline intact.

A user can verify behavior by opening TP/SL on a position, toggling "Configure Amount", moving the slider, editing percent, and seeing amount update immediately; then editing amount and seeing percent/slider update in return.

## Progress

- [x] (2026-02-24 22:03Z) Audited current TP/SL modal state/actions/view/tests and confirmed current behavior only renders amount input when "Configure Amount" is enabled.
- [x] (2026-02-24 22:05Z) Reviewed UI policy constraints in `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md`.
- [x] (2026-02-24 22:10Z) Performed external reference review: Hyperliquid order type docs and SDK order schema for TP/SL trigger payload shape.
- [x] (2026-02-24 22:13Z) Authored this ExecPlan with implementation and validation milestones.
- [x] (2026-02-24 18:40Z) Implemented modal domain state synchronization for amount <-> percent in `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` (`:size-percent-input`, conversion helpers, `set-modal-field` path handling, configure reset behavior).
- [x] (2026-02-24 18:41Z) Implemented TP/SL modal UI controls under "Configure Amount" in `/hyperopen/src/hyperopen/views/account_info/position_tpsl_modal.cljs` (amount input + slider + percent box + max button).
- [x] (2026-02-24 18:42Z) Extended tests for state synchronization and UI wiring in `/hyperopen/test/hyperopen/account/history/position_tpsl_test.cljs` and `/hyperopen/test/hyperopen/views/account_info/position_tpsl_modal_test.cljs`.
- [x] (2026-02-24 18:44Z) Ran required validation gates successfully: `npm run check`, `npm test`, `npm run test:websocket`.

## Surprises & Discoveries

- Observation: The existing modal already computes "active size" from `:size-input` when `:configure-amount?` is true, so no submit schema changes are required.
  Evidence: `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` `parsed-inputs` and `submit-form`.

- Observation: Current view placement renders amount input above checkbox rows; placement must be moved to satisfy requested interaction parity.
  Evidence: `/hyperopen/src/hyperopen/views/account_info/position_tpsl_modal.cljs` currently renders `(when (boolean (:configure-amount? modal*)) (input-row "Amount" ...))` before checkbox section.

- Observation: Hyperliquid TP/SL semantics distinguish between dynamic position-sized TP/SL and explicit configured size (`positionTpsl` vs static size trigger order), so keeping explicit configured size in client state is valid.
  Evidence: Hyperliquid docs `Order Types` describe trigger orders and `positionTpsl`; SDK order shape uses `:s` size + trigger fields (`isMarket`, `triggerPx`, `tpsl`).

- Observation: `npm test -- <namespace>` currently ignores the namespace argument and executes the full generated test suite.
  Evidence: runner output includes `Unknown arg: hyperopen.account.history.position-tpsl-test` and then executes all namespaces (`Ran 1273 tests ... 0 failures, 0 errors`).

## Decision Log

- Decision: Keep existing action IDs and submit request shape (`:actions/set-position-tpsl-modal-field`, `build-tpsl-orders`) and implement percent synchronization inside `position_tpsl.cljs`.
  Rationale: This preserves runtime wiring and API contracts while delivering UI capability.
  Date/Author: 2026-02-24 / Codex

- Decision: Constrain percent controls to `0..100` and clamp out-of-range percent input.
  Rationale: "Configure Amount" in this modal is a position-reduction size control and should not exceed current position size.
  Date/Author: 2026-02-24 / Codex

- Decision: Reuse existing slider visual system (`order-size-slider` classes) for consistency with order ticket and to avoid introducing new CSS tokens.
  Rationale: Existing styles are already tested and match Hyperopen’s visual language.
  Date/Author: 2026-02-24 / Codex

- Decision: Add an explicit `MAX` control that sets percent input to `100` via existing modal field action dispatch.
  Rationale: This matches expected operator workflow for \"configure amount\" without introducing new action IDs or effect branches.
  Date/Author: 2026-02-24 / Codex

## Outcomes & Retrospective

Implemented the feature end-to-end. Configure Amount now renders below checkbox toggles and includes synchronized amount, slider, and percent inputs.

State translation remains centralized in `position_tpsl.cljs`; submission payload and action registry contracts are unchanged. The modal exposes `configured-size-percent` for view rendering and keeps both amount and percent fields synchronized with clamped bounds.

Validation outcomes:

- `npm run check` passed.
- `npm test` passed (`Ran 1273 tests containing 5985 assertions. 0 failures, 0 errors.`).
- `npm run test:websocket` passed (`Ran 147 tests containing 643 assertions. 0 failures, 0 errors.`).

## Context and Orientation

The TP/SL modal state is stored at `:positions-ui :tpsl-modal` and initialized via `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` (`from-position-row`).

The modal UI is rendered in `/hyperopen/src/hyperopen/views/account_info/position_tpsl_modal.cljs`. Today, enabling `:configure-amount?` only reveals one amount text input and no slider/percent control.

User edits dispatch through `/hyperopen/src/hyperopen/account/history/actions.cljs` `set-position-tpsl-modal-field`, which delegates to `position-tpsl/set-modal-field` in `position_tpsl.cljs`. This is the canonical state translation boundary for modal inputs.

Order submission for TP/SL is built in `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs` `build-tpsl-orders`, which already accepts a single size and produces trigger orders. No API contract changes are needed.

## Plan of Work

Milestone 1 (domain state sync):

Update `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` so modal state includes a percent text field for configured amount and keeps it synchronized with `:size-input`.

- Add `:size-percent-input` to default/open modal state.
- Add helpers to convert between amount and percent using current `:position-size`.
- Extend `set-modal-field` to:
  - sync percent whenever `[:size-input]` changes,
  - handle `[:size-percent-input]` by clamping percent and updating `:size-input`.
- Extend `set-configure-amount` so toggling off resets both size and percent to full position defaults.
- Expose a helper (or equivalent) for view slider percent value.

Milestone 2 (modal UI):

Update `/hyperopen/src/hyperopen/views/account_info/position_tpsl_modal.cljs` to render configure-amount controls below the checkbox row.

- Keep "Configure Amount" checkbox where it is.
- Render a new block beneath checkboxes when enabled:
  - amount input,
  - slider,
  - percent input.
- Wire slider + percent input to `:actions/set-position-tpsl-modal-field` with `[:size-percent-input]`.
- Keep amount input wired to `[:size-input]`.

Milestone 3 (tests):

Update tests to cover synchronization and rendering.

- `/hyperopen/test/hyperopen/account/history/position_tpsl_test.cljs`
  - assert default/open state includes size percent defaults,
  - assert amount edits update percent,
  - assert percent edits update amount and clamp >100.
- `/hyperopen/test/hyperopen/views/account_info/position_tpsl_modal_test.cljs`
  - assert configure-amount surface includes slider and percent input controls,
  - assert slider/percent controls dispatch expected action path.

Milestone 4 (validation):

Run required repository gates and record outcomes.

## Concrete Steps

1. Implement domain synchronization in `position_tpsl.cljs` and run targeted TP/SL domain tests.

   cd /hyperopen
   npm test -- hyperopen.account.history.position-tpsl-test

2. Implement modal UI rendering and run targeted modal view tests.

   cd /hyperopen
   npm test -- hyperopen.views.account-info.position-tpsl-modal-test

3. Run full required validation gates.

   cd /hyperopen
   npm run check
   npm test
   npm run test:websocket

## Validation and Acceptance

Acceptance is complete when all of the following are true:

1. Enabling "Configure Amount" displays amount + slider + percent controls under the checkbox rows.
2. Moving slider updates percent input and amount input in the same render cycle.
3. Editing percent input updates slider and amount; values clamp to max `100%`.
4. Editing amount input updates percent and slider based on current position size.
5. Existing TP/SL submit and validation behavior remains unchanged.
6. Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

These changes are additive and idempotent. Reapplying patches preserves deterministic state defaults and action behavior.

If a regression appears, safe rollback is limited to the new percent synchronization paths and configure-amount UI block while keeping `:size-input` behavior and existing submission flow untouched.

## Artifacts and Notes

External references reviewed during planning:

- Hyperliquid docs: `Order Types` and trigger-order semantics (`tp`/`sl`, `positionTpsl`) at https://hyperliquid.gitbook.io/hyperliquid-docs/trading/order-types
- Hyperliquid Python SDK order schema (`exchange.py`) for trigger fields and TP/SL order composition: https://github.com/hyperliquid-dex/hyperliquid-python-sdk

Internal files in scope:

- `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs`
- `/hyperopen/src/hyperopen/views/account_info/position_tpsl_modal.cljs`
- `/hyperopen/test/hyperopen/account/history/position_tpsl_test.cljs`
- `/hyperopen/test/hyperopen/views/account_info/position_tpsl_modal_test.cljs`

Plan revision note: 2026-02-24 22:13Z - Initial plan authored before implementation.
Plan revision note: 2026-02-24 18:44Z - Marked implementation/test milestones complete and recorded validation outcomes and discoveries.
Plan revision note: 2026-02-24 18:46Z - Moved plan from `/hyperopen/docs/exec-plans/active/` to `/hyperopen/docs/exec-plans/completed/` after all required validation gates passed.

## Interfaces and Dependencies

No new external dependencies are introduced.

Stable interfaces that remain unchanged:

- Action IDs: `:actions/set-position-tpsl-modal-field`, `:actions/set-position-tpsl-configure-amount`, `:actions/submit-position-tpsl`
- Submit shape from `position-tpsl/prepare-submit` and `order-commands/build-tpsl-orders`
- Modal state root path: `[:positions-ui :tpsl-modal]`

New internal interface additions in `position_tpsl.cljs` will be view-facing helpers for percent synchronization only.
