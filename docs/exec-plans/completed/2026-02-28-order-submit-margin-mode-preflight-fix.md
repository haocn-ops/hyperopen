# Order Submit Margin-Mode Preflight Sync

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Users can currently select `Cross` or `Isolated` in the order form, but submitted orders still follow whatever margin mode Hyperliquid already has on the account for that asset. After this change, submitting an order will first sync the selected margin mode (and leverage) via Hyperliquid `updateLeverage`, then place the order. A user can verify by selecting `Cross`, submitting, and observing the position margin label on Hyperliquid reflects cross behavior instead of remaining isolated from stale exchange state.

## Progress

- [x] (2026-02-28 16:20Z) Reproduced and confirmed root cause in code: margin-mode dropdown is local UI only and is not included in exchange submission flow.
- [x] (2026-02-28 16:34Z) Implemented pre-submit `updateLeverage` action construction from order form state and attached it to order submit requests as `:pre-actions`.
- [x] (2026-02-28 16:38Z) Implemented serial pre-submit execution in `api-submit-order` with fail-fast behavior; order submission is skipped when margin-mode sync fails.
- [x] (2026-02-28 16:44Z) Added regression tests for pre-action request construction, submit sequencing, and pre-action failure handling.
- [x] (2026-02-28 16:52Z) Ran required validation gates successfully (`npm run check`, `npm test`, `npm run test:websocket`).
- [x] (2026-02-28 16:53Z) Moved this plan to `/hyperopen/docs/exec-plans/completed/` after acceptance criteria passed.

## Surprises & Discoveries

- Observation: The existing submit path (`order/actions.cljs` -> `:effects/api-submit-order` -> `order/effects.cljs`) only sends the `:action` from request and has no concept of pre-submit exchange actions.
  Evidence: `/hyperopen/src/hyperopen/order/actions.cljs` and `/hyperopen/src/hyperopen/order/effects.cljs`.
- Observation: Hyperopen already uses Hyperliquid non-order actions through the same signing transport (`submit-order!`), e.g. `updateIsolatedMargin`, so `updateLeverage` can be sent without new transport primitives.
  Evidence: `/hyperopen/src/hyperopen/account/history/position_margin.cljs` and `/hyperopen/src/hyperopen/api/trading.cljs`.
- Observation: Existing order-entry tests assumed a different effect tuple shape when extracting submit request payload (`nth 2`), causing false failures after new assertions were added.
  Evidence: Updated extraction in `/hyperopen/test/hyperopen/core_bootstrap/order_entry_actions_test.cljs` to use `(second effect)` for `[:effects/api-submit-order request]`.

## Decision Log

- Decision: Apply margin mode at submit-time as an explicit pre-submit step (`updateLeverage`) and abort order placement if that step fails.
  Rationale: This guarantees the selected mode is actually applied before any new order is sent, preventing silent mode mismatch.
  Date/Author: 2026-02-28 / Codex
- Decision: Keep `updateLeverage` sync in submit pipeline rather than firing network effects directly from dropdown selection.
  Rationale: This keeps dropdown interactions deterministic/UI-local and aligns heavy side effects with submit intent boundaries.
  Date/Author: 2026-02-28 / Codex

## Outcomes & Retrospective

Root cause was confirmed and fixed: margin mode previously never left the UI. Submit requests for perp orders now include `:pre-actions` with a signed `updateLeverage` action derived from `:margin-mode` and `:ui-leverage`, and runtime executes those actions before order placement.

Behavior now:

- On submit, margin mode sync is attempted first.
- If sync fails, order is not sent and user sees `Margin mode update failed: ...`.
- If sync succeeds, order flow continues unchanged.

Validation outcomes:

- `npm run check` passed.
- `npm test` passed (1525 tests, 7797 assertions).
- `npm run test:websocket` passed (153 tests, 701 assertions).

## Context and Orientation

Order submission starts in `/hyperopen/src/hyperopen/order/actions.cljs` (`submit-order`) and emits `[:effects/api-submit-order request]`. Request construction comes from `/hyperopen/src/hyperopen/state/trading.cljs` via `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs` (`build-order-request`). Runtime effect execution is in `/hyperopen/src/hyperopen/order/effects.cljs` (`api-submit-order`), which currently calls `trading-api/submit-order!` exactly once for the order action.

The margin-mode dropdown currently changes `:order-form-ui` state (`:margin-mode`) via transitions in `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`, but nothing in request construction or effects submits that mode to Hyperliquid.

## Plan of Work

Add a pre-submit action builder in `orders/commands.cljs` that produces Hyperliquid `updateLeverage` action from the same command context used for order requests (`asset-idx`, `ui-leverage`, `margin-mode`, and perp market type). Include this action in submit request as `:pre-actions` when available.

Update `order/effects.cljs` `api-submit-order` to execute `:pre-actions` serially before sending the main order action. If any pre-action returns non-`ok` status or throws, stop and surface a clear margin-mode sync error in runtime error state and toast, without sending the order action.

Add tests in:
- `test/hyperopen/api/gateway/orders/commands_test.cljs` for `:pre-actions` contents.
- `test/hyperopen/core_bootstrap/order_effects_test.cljs` for sequencing and fail-fast behavior.

## Concrete Steps

From `/Users//projects/hyperopen`:

1. Edit request builder:
   - `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs`
2. Edit submit effect runtime:
   - `/hyperopen/src/hyperopen/order/effects.cljs`
3. Add/update tests:
   - `/hyperopen/test/hyperopen/api/gateway/orders/commands_test.cljs`
   - `/hyperopen/test/hyperopen/core_bootstrap/order_effects_test.cljs`
4. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

## Validation and Acceptance

Acceptance criteria:

- Submit request for perp orders includes a pre-submit `updateLeverage` action reflecting selected `:margin-mode` and `:ui-leverage`.
- `api-submit-order` executes `updateLeverage` before order action.
- If `updateLeverage` fails, order action is not sent and user sees a margin-mode sync error.
- Required validation gates pass.

## Idempotence and Recovery

Changes are additive and safe to re-run. If sequencing tests fail, run targeted tests first, then full gates. If needed, rollback only changed files in this task and re-apply incrementally.

## Artifacts and Notes

Root cause excerpt:

- Margin-mode selection only updates local state (`set-order-margin-mode`) and submit emits only one order action effect:
  - `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`
  - `/hyperopen/src/hyperopen/order/actions.cljs`

## Interfaces and Dependencies

New request shape (backward-compatible):

- Existing: `{:action {...}}`
- New optional: `{:action {...} :pre-actions [{...}]}` where each pre-action is a signed Hyperliquid action map (starting with `updateLeverage`).

Revision note (2026-02-28): Initial plan created after reproducing margin-mode mismatch root cause.
Revision note (2026-02-28): Updated with implementation results, decisions, validation evidence, and completion status before archiving.
