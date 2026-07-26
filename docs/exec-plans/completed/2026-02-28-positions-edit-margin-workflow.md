# Positions Tab Editable Margin Workflow (Hyperliquid Parity)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan follows `/hyperopen/.agents/PLANS.md` and must be maintained in accordance with that file.

## Purpose / Big Picture

Users can currently see `Margin` in the Positions tab but cannot edit it. After this change, clicking the pencil in the `Margin` cell will open an edit surface that lets the user add or remove isolated margin and submit an onchain update through the same signed exchange flow used by the rest of the app. Users will be able to verify it by opening Positions, clicking the margin edit control, entering an amount, submitting Add/Remove, and seeing a success/error toast and refreshed position/account data.

## Progress

- [x] (2026-02-28 13:50Z) Audited current Hyperopen positions rendering, runtime action/effect plumbing, and state ownership for existing position overlays (`TP/SL`, `Reduce`).
- [x] (2026-02-28 13:50Z) Verified Hyperliquid protocol requirements for margin updates from official Exchange endpoint docs and reference SDKs.
- [x] (2026-02-28 13:50Z) Inspected Hyperliquid production bundle (`main.be4d3bab.js`) to confirm UI submit action shape from the margin edit flow.
- [x] (2026-02-28 15:36Z) Implemented `position-margin` workflow wiring in account-history actions (`open/close/set/submit`) and mutual overlay exclusivity with TP/SL and Reduce.
- [x] (2026-02-28 15:36Z) Implemented margin edit UI (margin-cell pencil trigger + anchored `Edit Margin` modal with amount input, slider, add/remove toggle, and submit/error states) and click-away integration.
- [x] (2026-02-28 15:36Z) Wired runtime registrations/contracts/effects for `:effects/api-submit-position-margin` and new margin action IDs.
- [x] (2026-02-28 15:36Z) Expanded tests across actions, positions tab rendering/dispatch, startup click-away behavior, app defaults, and runtime effect dependency wiring.
- [x] (2026-02-28 15:36Z) Ran required gates: `npm run check`, `npm test`, `npm run test:websocket`.

## Surprises & Discoveries

- Observation: Hyperliquid’s live margin editor sends `updateIsolatedMargin` with signed micro-USDC integer amount (`ntli = amount * 1e6`) and sign-driven add/remove direction.
  Evidence: Production bundle snippet from `https://app.hyperliquid.xyz/static/js/main.be4d3bab.js` builds `{ type:"updateIsolatedMargin", asset: U, isBuy: !0, ntli: (add?1:-1) * Number(...) }`.

- Observation: Official reference SDKs encode the same exchange payload contract and unit scale.
  Evidence: `@nktkas/hyperliquid` schema for `updateIsolatedMargin` documents `ntli` as `float * 1e6`; official `hyperliquid-python-sdk` `update_isolated_margin` uses `float_to_usd_int(amount)` and sends `{"type":"updateIsolatedMargin", ...}`.

- Observation: Hyperopen currently has no existing margin-edit overlay state/action path; only `:positions-ui :tpsl-modal` and `:positions-ui :reduce-popover` exist.
  Evidence: `/hyperopen/src/hyperopen/state/app_defaults.cljs`, `/hyperopen/src/hyperopen/account/history/actions.cljs`, and `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs`.

- Observation: `set-position-margin-amount-to-max` resolves to `0` whenever clearinghouse withdrawable/account summary context is missing, so deterministic tests must seed non-zero `:available-to-add`.
  Evidence: Initial test failures in `test/hyperopen/account/history/actions_test.cljs` before injecting `:available-to-add` fixture state.

- Observation: DEX-namespaced positions can carry per-dex clearinghouse snapshots without withdrawable/summary fields, causing false `available-to-add = 0` even when default clearinghouse or unified spot balances show free USDC.
  Evidence: Live QA report for `GOLD` (`xyz` chip) plus follow-up regression tests in `/hyperopen/test/hyperopen/account/history/position_margin_test.cljs`.

## Decision Log

- Decision: Implement margin edit as a third `positions-ui` overlay (`:margin-modal`) parallel to TP/SL and Reduce, rather than mutating existing overlays.
  Rationale: This preserves current interaction boundaries and keeps each overlay responsibility isolated and testable.
  Date/Author: 2026-02-28 / Codex

- Decision: Use `updateIsolatedMargin` exchange action with `ntli` integer micro-USDC conversion and position-side `isBuy` derived from signed size.
  Rationale: This matches official Exchange docs and reference SDK schemas while preserving correct long/short semantics.
  Date/Author: 2026-02-28 / Codex

- Decision: Keep submit flow consistent with existing order feedback runtime (toast + refresh account/order surfaces) via a dedicated runtime effect handler.
  Rationale: Users get consistent pending/success/error behavior, and state refresh semantics remain centralized in `order.effects`.
  Date/Author: 2026-02-28 / Codex

- Decision: Reuse `trading-api/submit-order!` for margin updates and post-submit account refresh, but with margin-specific precondition and toast copy.
  Rationale: Hyperliquid `updateIsolatedMargin` is still an exchange action payload and should pass through existing signing/agent submission infrastructure.
  Date/Author: 2026-02-28 / Codex

- Decision: Compute available margin using unified USDC spot availability first (for unified mode), then fall back across candidate clearinghouse snapshots (dex-specific then default).
  Rationale: Prevent false zeros when one snapshot lacks summary fields while preserving deterministic behavior across classic/unified account modes and dex routes.
  Date/Author: 2026-02-28 / Codex

## Outcomes & Retrospective

Implemented end-to-end editable isolated margin flow in Positions with Hyperliquid payload parity:

- Margin cell now has a pencil affordance and opens an anchored `Edit Margin` modal.
- Modal supports typed amount input, slider/percent input, `MAX`, and Add/Remove mode toggles.
- Submit builds `updateIsolatedMargin` with signed micro-USDC `ntli` (`amount * 1e6`) and correct `isBuy` side mapping from position sign.
- Runtime now supports `:effects/api-submit-position-margin` with wallet/agent precondition checks, success/error toasts, modal state updates, and account surface refresh.
- Click-away and keyboard escape handling now include the margin modal surface.

Validation outcomes:

- `npm run check`: pass
- `npm test`: pass
- `npm run test:websocket`: pass

## Context and Orientation

Positions rows are rendered in `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs`. That file currently renders `Margin` as static text and already hosts two interactive surfaces: TP/SL modal and Reduce popover. Their state lives in app state under `:positions-ui` and is initialized by `/hyperopen/src/hyperopen/state/app_defaults.cljs`.

Action orchestration for Positions UI is owned by `/hyperopen/src/hyperopen/account/history/actions.cljs`, with runtime registration in:

- `/hyperopen/src/hyperopen/runtime/collaborators.cljs`
- `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`
- `/hyperopen/src/hyperopen/registry/runtime.cljs`
- `/hyperopen/src/hyperopen/schema/contracts.cljs`

Exchange submissions for position-related UI flows are executed in `/hyperopen/src/hyperopen/order/effects.cljs` through signed action posting in `/hyperopen/src/hyperopen/api/trading.cljs`.

In this plan, “margin edit modal” means the anchored UI panel opened from the Positions `Margin` cell edit affordance. “Submit action” means Hyperliquid exchange action `updateIsolatedMargin` signed via existing agent wallet flow.

## Plan of Work

Milestone 1 adds a new position-margin domain module in `/hyperopen/src/hyperopen/account/history/position_margin.cljs`. This module will own modal state defaults, input normalization, max-amount derivation, validation, and request construction for `updateIsolatedMargin`. It will derive asset identity from `:coin` and optional `:dex` using the same market map strategy used by position-reduce. It will convert user-entered USDC amount to signed integer `ntli` using `round(amount * 1e6)` with add/remove sign.

Milestone 2 extends account-history actions in `/hyperopen/src/hyperopen/account/history/actions.cljs` to open/close/update/submit the new margin modal and to enforce mutual exclusivity with TP/SL and Reduce overlays. It will add `:positions-ui :margin-modal` state transitions and emit a new effect `:effects/api-submit-position-margin` after successful validation.

Milestone 3 adds UI rendering and interaction wiring:

- `/hyperopen/src/hyperopen/views/account_info/position_margin_modal.cljs` for the anchored panel UI matching the provided reference shape.
- `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs` to render a clickable margin cell with edit icon, open action, and inline modal mount for the active row key.
- `/hyperopen/src/hyperopen/views/account_info/vm.cljs` and `/hyperopen/src/hyperopen/views/account_info_view.cljs` to pass margin modal state to the Positions tab renderer.
- `/hyperopen/src/hyperopen/startup/runtime.cljs` click-away guards updated to include new surface and trigger selectors.

Milestone 4 wires runtime effect and contracts for the new submit path:

- `/hyperopen/src/hyperopen/order/effects.cljs` new `api-submit-position-margin` handler with precondition checks, toast semantics, and post-submit surface refresh.
- Runtime effect adapter and factory wiring in `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs` and `/hyperopen/src/hyperopen/app/effects.cljs`.
- Registration and contract coverage updates in `/hyperopen/src/hyperopen/runtime/collaborators.cljs`, `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`, `/hyperopen/src/hyperopen/registry/runtime.cljs`, `/hyperopen/src/hyperopen/schema/contracts.cljs`, and `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`.
- Default state update in `/hyperopen/src/hyperopen/state/app_defaults.cljs`.

Milestone 5 adds and updates tests for deterministic behavior and regression safety across domain/action/view/runtime wiring.

## Concrete Steps

From repository root `/hyperopen`:

1. Implement new margin module and UI wiring files described above.
2. Update runtime registration/contracts/defaults/action/effect adapters.
3. Update existing tests and add new test namespace(s) for margin behavior.
4. Run required gates:

    npm run check
    npm test
    npm run test:websocket

Expected result: all commands pass; positions margin edit interaction is wired and test-covered.

## Validation and Acceptance

Acceptance is satisfied when all of the following are true:

1. Positions `Margin` cell presents an edit affordance and dispatches open action with anchor bounds.
2. Margin modal renders with asset, margin used, available-to-add, amount controls, add/remove mode controls, and submit button state.
3. Submitting valid input emits `:effects/api-submit-position-margin` with exchange action type `updateIsolatedMargin`, signed integer `ntli` (1e6 scaling), and correct side flag.
4. Submit preconditions and validation errors surface in modal state and user feedback toasts.
5. Click-away closes the margin modal the same way existing position overlays close.
6. Required gates (`check`, `test`, `test:websocket`) pass.

## Idempotence and Recovery

All changes are additive and local to positions/account-history/runtime wiring. Re-running test runner generation and test commands is safe. If partial edits leave runtime registration mismatched, fix drift by aligning all three sources of truth together in one pass: `registry/runtime.cljs`, `schema/contracts.cljs`, and `runtime/registry_composition.cljs`.

## Artifacts and Notes

Primary external evidence used:

- Hyperliquid Exchange endpoint docs (update isolated margin + update leverage).
- Hyperliquid production bundle action construction for margin edit (`main.be4d3bab.js`).
- `@nktkas/hyperliquid` exchange method schemas.
- `hyperliquid-python-sdk` `update_isolated_margin` and `float_to_usd_int` implementation.

## Interfaces and Dependencies

New runtime action IDs (to be added):

- `:actions/open-position-margin-modal`
- `:actions/close-position-margin-modal`
- `:actions/handle-position-margin-modal-keydown`
- `:actions/set-position-margin-modal-field`
- `:actions/set-position-margin-amount-percent`
- `:actions/set-position-margin-amount-to-max`
- `:actions/submit-position-margin-update`

New runtime effect ID (to be added):

- `:effects/api-submit-position-margin`

New domain/view interfaces:

- `hyperopen.account.history.position-margin/default-modal-state`
- `hyperopen.account.history.position-margin/open?`
- `hyperopen.account.history.position-margin/from-position-row`
- `hyperopen.account.history.position-margin/set-modal-field`
- `hyperopen.account.history.position-margin/set-amount-percent`
- `hyperopen.account.history.position-margin/set-amount-to-max`
- `hyperopen.account.history.position-margin/prepare-submit`
- `hyperopen.views.account-info.position-margin-modal/position-margin-modal-view`

Plan revision note: 2026-02-28 13:50Z - Initial plan created after protocol + SDK + bundle inspection to support editable positions margin flow end-to-end.
