# HIP3 Open Orders Immediate Visibility After Submit

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, when a user submits a HIP3 limit order, the order appears in Hyperopen open orders without requiring a full page refresh. The expected behavior is parity with Hyperliquid UX responsiveness: submit succeeds, then open orders reflect the newly placed order in-session.

A user can verify the result by placing a HIP3 limit order and observing that Open Orders updates shortly after the submit success toast, with no browser refresh.

## Progress

- [x] (2026-02-19 20:49Z) Audited order submit effect pipeline and open-orders projection flow.
- [x] (2026-02-19 20:50Z) Confirmed current submit success path refreshes order history only, while cancel path refreshes open-orders snapshots (default + per-dex).
- [x] (2026-02-19 20:51Z) Created this active ExecPlan.
- [x] (2026-02-19 20:52Z) Implemented submit success open-orders refresh by reusing the existing order-mutation refresh helper in `/hyperopen/src/hyperopen/order/effects.cljs`.
- [x] (2026-02-19 20:53Z) Updated submit-effect regression coverage in `/hyperopen/test/hyperopen/core_bootstrap_test.cljs` to assert open-orders refresh calls.
- [x] (2026-02-19 20:55Z) Ran required validation gates (`npm run check`, `npm test`, `npm run test:websocket`) with all green.
- [x] (2026-02-19 20:55Z) Finalized outcomes and prepared this plan for move to completed.
- [x] (2026-02-19 20:57Z) Re-ran required validation gates after final plan-file move/edit to confirm final-tree green status.

## Surprises & Discoveries

- Observation: The submit success effect did not trigger any open-orders hydration.
  Evidence: `/hyperopen/src/hyperopen/order/effects.cljs` `api-submit-order` success branch previously only cleared submit error, showed toast, and dispatched `[:actions/refresh-order-history]`.

- Observation: The cancel success effect already implemented open-orders rehydration (default + per-dex), which explains why cancel visibility is immediate while submit visibility lagged.
  Evidence: `/hyperopen/src/hyperopen/order/effects.cljs` `api-cancel-order` calls a helper that invokes `request-frontend-open-orders!` and iterates dexs from `ensure-perp-dexs-data!`.

- Observation: Open-order projection uses live websocket orders when present and fallback snapshot when live is absent, while always appending per-dex snapshots.
  Evidence: `/hyperopen/src/hyperopen/views/account_info/projections/orders.cljs` `open-orders-source` composes `(if (seq live) live fallback)` plus `dex-orders`.

## Decision Log

- Decision: Reuse the existing open-orders refresh flow used by cancel and invoke it after successful submit.
  Rationale: This is the smallest change with low regression risk and directly addresses HIP3 named-dex visibility by refreshing per-dex snapshots immediately after submit success.
  Date/Author: 2026-02-19 / Codex

- Decision: Rename the helper to `refresh-open-orders-after-order-mutation!` and use it from both submit and cancel flows.
  Rationale: The behavior is no longer cancel-specific; shared naming reduces ambiguity and keeps logic centralized.
  Date/Author: 2026-02-19 / Codex

- Decision: Keep open-orders source precedence logic unchanged in this fix.
  Rationale: The reported defect is post-submit visibility for HIP3; changing source precedence is broader scope and unnecessary for the requested bug fix.
  Date/Author: 2026-02-19 / Codex

## Outcomes & Retrospective

Implementation completed successfully. Submit success now triggers the same open-orders refresh workflow previously used only by cancel. This adds immediate default snapshot refresh and named-dex snapshot refresh after successful order placement, while preserving existing toast and order-history refresh behavior.

Validation outcomes:

- `npm run check`: pass
- `npm test`: pass
- `npm run test:websocket`: pass
- Final-tree re-run after completed-plan move/edit: pass for all three commands.

Retrospective: Centralizing submit/cancel post-mutation hydration behind one helper reduced change surface and test overhead. The fix remains bounded to order effects and one submit regression test.

## Context and Orientation

In this repository, order submit and cancel side effects are implemented in `/hyperopen/src/hyperopen/order/effects.cljs`.

- `api-submit-order` handles asynchronous submit lifecycle and success/error UI projection.
- `api-cancel-order` handles cancel lifecycle and includes post-success open-orders refresh behavior.

Open orders shown in account views are derived via `/hyperopen/src/hyperopen/views/account_info/projections/orders.cljs` from three sources:

- `[:orders :open-orders]` (live websocket user channel payload)
- `[:orders :open-orders-snapshot]` (default-dex HTTP snapshot)
- `[:orders :open-orders-snapshot-by-dex]` (named-dex HTTP snapshots)

For HIP3 markets (named-dex perps), per-dex snapshots are critical because websocket payload timing may lag newly placed named-dex orders.

## Plan of Work

Milestone 1 updated `/hyperopen/src/hyperopen/order/effects.cljs` so successful submit now triggers the same open-orders refresh helper path as cancel.

Milestone 2 updated regression coverage in `/hyperopen/test/hyperopen/core_bootstrap_test.cljs` to verify submit success triggers open-orders refresh calls (default and per-dex) while still refreshing order history.

Milestone 3 ran required repository validation gates, captured outcomes, and finalized this plan for completed status.

## Concrete Steps

Run from `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/order/effects.cljs`:
   - Rename cancel-specific helper to `refresh-open-orders-after-order-mutation!`.
   - Invoke it in submit success branch.
   - Keep cancel behavior wired to the same helper.

2. Edit `/hyperopen/test/hyperopen/core_bootstrap_test.cljs`:
   - Extend submit success test to mock `api/request-frontend-open-orders!` and `api/ensure-perp-dexs-data!`.
   - Assert refresh call count and existing dispatch behavior.

3. Run required gates:

   npm run check
   npm test
   npm run test:websocket

Observed transcript highlights:

- `npm run check`: app/test compile completed with 0 warnings.
- `npm test`: `Ran 1131 tests containing 5225 assertions. 0 failures, 0 errors.`
- `npm run test:websocket`: `Ran 135 tests containing 587 assertions. 0 failures, 0 errors.`

## Validation and Acceptance

Acceptance criteria status:

- After successful submit effect, runtime triggers open-orders refresh for default and named-dex scopes. Passed.
- Existing submit success behavior remains (success toast + order-history refresh). Passed.
- Existing cancel behavior remains unchanged in outcome, now via shared helper. Passed.
- Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`. Passed.

Manual scenario acceptance target:

- Place HIP3 limit order in a connected wallet session and confirm open order appears without page refresh.

## Idempotence and Recovery

Changes are additive and safe to re-run.

If regression occurs, recovery is localized:

- Revert submit success invocation of `refresh-open-orders-after-order-mutation!` in `/hyperopen/src/hyperopen/order/effects.cljs`.
- Keep cancel path and helper behavior intact.
- Re-run test gates.

No destructive migrations or persistent schema changes were introduced.

## Artifacts and Notes

Primary files changed:

- `/hyperopen/src/hyperopen/order/effects.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap_test.cljs`
- `/hyperopen/docs/exec-plans/completed/2026-02-19-hip3-open-orders-submit-live-refresh.md`

## Interfaces and Dependencies

No new dependencies.

Runtime interfaces remain stable:

- `order-effects/api-submit-order`
- `order-effects/api-cancel-order`
- `api/request-frontend-open-orders!`
- `api/ensure-perp-dexs-data!`

Plan revision note: 2026-02-19 20:51Z - Initial plan created after submit/open-orders flow audit and before implementation.
Plan revision note: 2026-02-19 20:55Z - Updated progress, decisions, and outcomes after implementation and required gate validation completion.
Plan revision note: 2026-02-19 20:57Z - Added final-tree gate re-run evidence after moving plan to completed and updating path references.
