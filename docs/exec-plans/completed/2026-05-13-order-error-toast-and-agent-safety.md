# Order Error Toast And Agent Safety Messaging

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document follows `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

When a trader closes a market position and the exchange rejects either the actual order or the background agent safety action, Hyperopen should tell the truth in readable language. The current UI flattens exchange errors into one long toast headline, so important text is truncated behind an ellipsis. After this work, order placement failures render as a short headline with a wrapped reason, and the `scheduleCancel` volume-gate response is recognized as a background safety limitation instead of being confused with the close-order request.

The user-visible proof is a toast that says `Order not placed` and shows the exchange rejection reason on a readable second line or detail line. The implementation proof is focused ClojureScript tests that fail before the change and pass after it, plus the required repo gates.

## Context References

Public refs:
- Direct user request in this Codex session on 2026-05-13: root-cause a hard-to-read close market order error toast and implement a friendlier display strategy.

Repo artifacts:
- `/hyperopen/docs/PLANS.md`
- `/hyperopen/.agents/PLANS.md`
- `/hyperopen/docs/FRONTEND.md`
- `/hyperopen/docs/agent-guides/trading-ui-policy.md`
- `/hyperopen/docs/BROWSER_TESTING.md`

Local scratch refs:
- None.

## Progress

- [x] (2026-05-13 21:25Z) Root-caused the attached network response as the background `scheduleCancel` agent safety action and the visible toast as the generic order submit error path.
- [x] (2026-05-13 21:25Z) Created this active ExecPlan.
- [x] (2026-05-13 21:31Z) Added failing tests for order error toast payloads, readable notification rendering, and `scheduleCancel` volume-gate classification.
- [x] (2026-05-13 21:39Z) Implemented formatter, submit-effect wiring, readable detail rendering, and schedule-cancel volume-gate handling.
- [x] (2026-05-13 21:43Z) Ran governed design review for `/trade`; all required widths and passes returned `PASS`.
- [x] (2026-05-13 21:45Z) Ran fresh required repo gates: `npm test`, `npm run check`, and `npm run test:websocket`.
- [x] (2026-05-13 21:45Z) Moved this plan to `docs/exec-plans/completed/` with validation evidence.

## Surprises & Discoveries

- Observation: The response in the user’s first screenshot is not the close-position order. It is `scheduleCancel`, a dead-man-switch style safety request sent while the agent wallet is ready.
  Evidence: `/hyperopen/src/hyperopen/wallet/agent_safety.cljs` calls `trading/schedule-cancel!` when the wallet agent status is `:ready` and on every refresh timer.

- Observation: The visible toast is hard to read because generic toast rendering puts the whole exchange message in a single truncated headline.
  Evidence: `/hyperopen/src/hyperopen/views/notifications_view.cljs` renders `headline` with class `truncate`, and `/hyperopen/src/hyperopen/order/effects.cljs` builds strings like `Order placement failed: Order 1: ...`.

- Observation: The first red test run failed before production code existed because `hyperopen.order.exchange-errors` was missing.
  Evidence: `npx shadow-cljs --force-spawn compile test && node out/test.js` reported `The required namespace "hyperopen.order.exchange-errors" is not available`.

- Observation: The worktree initially had no `node_modules`, so the compiled test runner could not import Lucide icons.
  Evidence: `node out/test.js` reported `Cannot find module 'lucide/dist/esm/icons/external-link.js'`; `test -d node_modules` returned missing. Running `npm install` restored declared dependencies.

- Observation: After implementation, the full CLJS test runner passed.
  Evidence: `npx shadow-cljs --force-spawn compile test && node out/test.js` ended with `Ran 3883 tests containing 21417 assertions. 0 failures, 0 errors.`

- Observation: The governed design review passed for the trade route across the required widths.
  Evidence: `npm run qa:design-ui -- --targets trade-route --manage-local-app` wrote `/Users/barry/.codex/worktrees/3894/hyperopen/tmp/browser-inspection/design-review-2026-05-13T21-43-20-082Z-545d5dc0` and returned `reviewOutcome: PASS`, `state: PASS` for widths `375`, `768`, `1280`, and `1440`. It reported the standard state-sampling blind spot for hover, active, disabled, and loading states not present by default.

- Observation: Browser-inspection cleanup did not leave tracked sessions running.
  Evidence: `npm run browser:cleanup` returned `{"ok": true, "stopped": [], "results": []}`.

- Observation: The fresh required repo gates all passed.
  Evidence: `npm test` ended with `Ran 3883 tests containing 21417 assertions. 0 failures, 0 errors.`; `npm run check` completed through the final `[:test] Build completed. (1662 files, 4 compiled, 0 warnings, 6.83s)`; `npm run test:websocket` ended with `Ran 524 tests containing 3043 assertions. 0 failures, 0 errors.`

## Decision Log

- Decision: Keep the order rejection and safety-heartbeat handling separate.
  Rationale: `scheduleCancel` protects stale agent sessions, while the market close request is a normal order submission. Combining those into one toast would make the user trust model worse.
  Date/Author: 2026-05-13 / Codex

- Decision: Use structured toast payloads instead of trying to make every exchange string fit a one-line toast.
  Rationale: Trading UI policy requires explaining what happened and what the user can do next. A short headline plus wrapped reason is clearer than an ellipsis.
  Date/Author: 2026-05-13 / Codex

- Decision: Make structured order rejection toasts persistent until dismissed.
  Rationale: Exchange rejection detail can be operationally important and should not disappear before a trader can read it.
  Date/Author: 2026-05-13 / Codex

## Outcomes & Retrospective

Implemented. The close-order submit path now sends structured exchange rejection payloads to the toast runtime, so the visible toast can use a short headline and wrapped detail instead of truncating the full error into one line. The background `scheduleCancel` volume-gate response is classified as agent safety unavailable due to volume and stored quietly under wallet agent state, without continuing the refresh loop for that ineligible session.

The change adds a small formatter namespace rather than spreading string parsing through effects and views. That keeps the exchange-specific parsing testable and preserves the raw exchange detail in `:detail` and legacy `:message`.

Residual risk: if an account crosses the required trading-volume threshold while the same agent wallet session remains ready, the safety scheduler will not retry until a wallet status, address, or agent-address fingerprint change causes the watcher to reconsider safety state. That is intentional for this fix because the exchange explicitly rejected the background safety action, but it is worth revisiting if live sessions commonly cross the threshold without reconnecting.

## Context and Orientation

Hyperopen submits close-position market orders through `/hyperopen/src/hyperopen/account/history/position_reduce.cljs`. A market close becomes a reduce-only IOC order through `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs`. The submit effect in `/hyperopen/src/hyperopen/order/effects.cljs` interprets exchange responses, sets local order submit error state, and calls the order feedback toast runtime.

The toast runtime lives in `/hyperopen/src/hyperopen/order/feedback_runtime.cljs`. It can already store map payloads with `:headline`, `:subline`, and `:message`, so this work should reuse that shape rather than adding a second notification system. The view that renders generic toasts is `/hyperopen/src/hyperopen/views/notifications_view.cljs`.

The background safety action lives in `/hyperopen/src/hyperopen/wallet/agent_safety.cljs`. It calls `/hyperopen/src/hyperopen/api/trading.cljs` function `schedule-cancel!`, which signs and posts an exchange action with type `"scheduleCancel"`. Hyperliquid can reject this with a text response that says the account has not traded enough volume. That response should be classified and recorded quietly so it does not look like a close order failure.

## Plan of Work

First, add focused tests. Add a new order error formatting namespace test that proves a single-order rejection becomes a structured toast payload with headline `Order not placed`, a concise subline, and the raw reason preserved. Extend notification view tests so generic error toasts with detail text render the detail without `truncate`. Add wallet agent safety tests proving the schedule-cancel volume-gate response is classified and saved without repeatedly scheduling the doomed refresh.

Second, implement a small formatter namespace under `/hyperopen/src/hyperopen/order/`. The formatter should accept raw exchange status error text and return a toast map. For a single order it should remove the noisy `Order 1:` prefix from the human-readable line; for multiple orders it should preserve order numbering in the detail. It should keep `:message` populated for legacy readers.

Third, update `/hyperopen/src/hyperopen/order/effects.cljs` so submit failures pass structured maps to `show-toast!`. Keep `:order-form-runtime :error` as plain text so existing inline error surfaces remain simple.

Fourth, update `/hyperopen/src/hyperopen/views/notifications_view.cljs` so generic toasts can render `:detail` or `:body` as wrapped text. Avoid changing trade confirmation toasts.

Fifth, update `/hyperopen/src/hyperopen/wallet/agent_safety.cljs` to recognize the schedule-cancel volume-gate response. Store a quiet status under the wallet agent branch and stop the refresh loop for that session when the exchange says the account is not eligible. Do not show a global order toast from this background path.

## Concrete Steps

Run all commands from `/Users/barry/.codex/worktrees/3894/hyperopen`.

1. Write tests:
   - `test/hyperopen/order/exchange_errors_test.cljs`
   - Extend `test/hyperopen/core_bootstrap/order_effects/submit_failures_test.cljs`
   - Extend `test/hyperopen/views/notifications_view_trade_confirmation_test.cljs`
   - Add `test/hyperopen/wallet/agent_safety_test.cljs`

2. Verify the tests fail before implementation:
   - `npm run test:runner:generate`
   - `npx shadow-cljs --force-spawn compile test && node out/test.js`

3. Implement:
   - Create `/hyperopen/src/hyperopen/order/exchange_errors.cljs`
   - Modify `/hyperopen/src/hyperopen/order/effects.cljs`
   - Modify `/hyperopen/src/hyperopen/views/notifications_view.cljs`
   - Modify `/hyperopen/src/hyperopen/wallet/agent_safety.cljs`

4. Verify focused behavior:
   - `npm test`
   - `npm run check`
   - `npm run test:websocket`

5. Account for browser QA:
   - `npm run qa:design-ui -- --targets trade-route --manage-local-app`
   - `npm run browser:cleanup`

## Validation and Acceptance

Acceptance is met when an exchange response containing one nested order status error no longer creates a one-line `Order placement failed: Order 1: ...` toast. Instead, the toast payload has headline `Order not placed`, a concise readable reason, and the full exchange detail in a wrapped detail area.

Acceptance is also met when a `scheduleCancel` response matching `Cannot set scheduled cancel time until enough volume traded. Required: $1000000. Traded: $890168.23.` is classified as agent safety unavailable due to volume, recorded under wallet agent state, and does not continue a tight retry loop.

Required gates when code changes are `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

The tests are additive and safe to rerun. The agent safety change should be data-only: if the wallet disconnects, locks, or changes agent address, the existing watcher fingerprint causes safety state to be reconsidered. If the new formatter is wrong, existing exchange error text is still preserved in `:detail` and legacy `:message`.

## Artifacts and Notes

Initial evidence from source inspection:

    src/hyperopen/wallet/agent_safety.cljs calls trading/schedule-cancel! on ready agent sessions.
    src/hyperopen/order/effects.cljs builds flat submit failure strings.
    src/hyperopen/views/notifications_view.cljs truncates generic toast headline and subline text.

## Interfaces and Dependencies

Define `hyperopen.order.exchange-errors/submit-error-toast-payload` with signature:

    (submit-error-toast-payload error-detail & [{:keys [partial?]}]) => map

The returned map must include `:headline`, `:subline`, `:detail`, and `:message`.

Define `hyperopen.order.exchange-errors/schedule-cancel-volume-gate` with signature:

    (schedule-cancel-volume-gate response-text) => nil or map

The returned map must include `:status`, `:reason`, `:required`, `:traded`, and `:message`.

Revision note 2026-05-13 21:25Z: Created the plan from the direct user request and initial root-cause investigation.

Revision note 2026-05-13 21:39Z: Updated progress and evidence after RED/GREEN TDD cycle for formatter, order submit toast payloads, readable notification rendering, and schedule-cancel volume-gate handling.

Revision note 2026-05-13 21:45Z: Added final validation evidence from design review, browser cleanup, and the required repo gates before moving the plan to completed.
