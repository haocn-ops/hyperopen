# Market Order Execution Parity and Submit-Status Truthfulness

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, a user placing a market long or short from Hyperopen will get execution behavior and feedback that matches Hyperliquid protocol expectations: the outgoing order price will respect exchange tick/lot constraints, and submit feedback will only report success when the exchange actually accepted the order statuses without per-order errors. Users will be able to verify this by placing a market order and seeing one of two truthful outcomes: either a real fill/resting result with valid IDs, or a clear rejection reason such as IOC cancel/no liquidity/tick rejection.

## Progress

- [x] (2026-02-24 14:20Z) Audited Hyperliquid docs and canonical SDK references listed in `/hyperopen/docs/references/hyperliquid-sdks.md`.
- [x] (2026-02-24 14:21Z) Compared audited sources against current Hyperopen market-order and submit-response handling paths.
- [x] (2026-02-24 14:22Z) Authored this ExecPlan with implementation milestones, file-level scope, and validation gates.
- [x] (2026-03-09 18:31Z) Moved this plan to `/hyperopen/docs/exec-plans/deferred/` during ExecPlan lifecycle cleanup and recorded local `bd` issue `hyperopen-gvp` as historical scratch context.
- [ ] Implement exchange-compliant price formatting for market-order submission paths.
- [ ] Implement nested `response.data.statuses` outcome parsing and truthful submit toasts/errors.
- [ ] Align default market slippage policy with source-backed behavior and add explicit no-fill guidance.
- [ ] Add/adjust tests and run required validation gates (`npm run check`, `npm test`, `npm run test:websocket`).

## Surprises & Discoveries

- Observation: Hyperliquid exchange docs explicitly show that top-level `status: "ok"` can still carry per-order status objects inside `response.data.statuses`, including `{ "error": "..." }` entries.
  Evidence: Hyperliquid Exchange Endpoint examples include `resting`, `filled`, and `error` status variants under `status: "ok"`.

- Observation: Hyperliquid tick/lot rules require price normalization beyond raw decimal formatting: max 5 significant figures, plus max decimals based on `szDecimals`.
  Evidence: Hyperliquid Tick and Lot Size docs specify `MAX_DECIMALS` rules and 5 significant figures; canonical SDK `@nktkas/hyperliquid` implements this in `formatPrice`.

- Observation: Canonical SDKs model market orders as aggressive IOC limit orders, not a distinct exchange action type.
  Evidence: `hyperliquid-python-sdk` `market_open` and `market_close` call `order(..., {"limit": {"tif": "Ioc"}})` after applying slippage.

- Observation: Canonical SDK handling treats nested status errors as request failure semantics.
  Evidence: `@nktkas/hyperliquid` `assertSuccessResponse` inspects `response.data.statuses` and throws when any status contains `error`.

- Observation: Hyperopen currently formats market submit price with up to 8 decimals (`number->clean-string ... 8`) and reports success solely from top-level `:status == "ok"`.
  Evidence: `/hyperopen/src/hyperopen/domain/trading/market.cljs` (`apply-market-price`) and `/hyperopen/src/hyperopen/order/effects.cljs` (`api-submit-order`).

## Decision Log

- Decision: Keep Hyperopen market-order semantics as IOC limit orders, and do not introduce a new protocol action type.
  Rationale: This is the protocol-correct pattern in Hyperliquid docs and all audited SDK references.
  Date/Author: 2026-02-24 / Codex

- Decision: Treat nested status entries (`response.data.statuses`) as the source of truth for submit success/failure, not top-level status alone.
  Rationale: Top-level `ok` is transport/acceptance-level only; per-order `error` entries still represent failed execution outcomes that must not be shown as success.
  Date/Author: 2026-02-24 / Codex

- Decision: Implement explicit exchange price formatting rules for market-order generated prices before serialization.
  Rationale: Current 8-decimal formatting can violate tick/significant-figure constraints and produce hidden status errors.
  Date/Author: 2026-02-24 / Codex

- Decision: Keep the scope focused on market-order correctness and submit truthfulness first, then evaluate expansion of the same formatter to all order types as follow-up work.
  Rationale: This addresses the user-visible failure mode directly while limiting regression risk in unrelated order-entry paths.
  Date/Author: 2026-02-24 / Codex

## Outcomes & Retrospective

This plan was authored but never executed. It was moved out of `/hyperopen/docs/exec-plans/active/` during the 2026-03-09 lifecycle cleanup so that active ExecPlans only describe live implementation. If this work is resumed, use GitHub or a current active ExecPlan for durable tracking; local `bd` issue `hyperopen-gvp` is historical scratch context only. The intended user outcome remains elimination of false-positive success feedback for rejected market orders and protocol-compliant market submit formatting.

## Context and Orientation

Hyperopen market-order submission currently flows through these files:

- Market price generation: `/hyperopen/src/hyperopen/domain/trading/market.cljs` (`apply-market-price`).
- Default slippage: `/hyperopen/src/hyperopen/trading/order_form_state.cljs` (`default-slippage`).
- Order action construction: `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs`.
- Signed submit transport: `/hyperopen/src/hyperopen/api/trading.cljs`.
- Submit-side UX outcome handling: `/hyperopen/src/hyperopen/order/effects.cljs` (`api-submit-order`).

The current behavior is internally consistent but protocol-incomplete in two ways relevant to this issue:

1. Market price formatting is not normalized to Hyperliquid tick/lot constraints before serialization.
2. Submit success/failure logic does not inspect nested per-order statuses and therefore can show success when the exchange returns per-order errors.

In Hyperliquid protocol language, a market order here means “an aggressive limit order with `t.limit.tif = Ioc` and price offset enough to cross immediately.” This repo already uses IOC, but it must also format price to valid protocol constraints and parse execution statuses correctly.

## Plan of Work

### Milestone 1: Add exchange-compliant market price formatting

Create a dedicated formatting helper in the trading domain that encodes Hyperliquid rules needed for market-order price strings: maximum significant figures and max decimal places derived from `szDecimals` and market class (`perp` vs `spot`). Integrate this helper into market-order price generation so the generated `:price` in market mode is always protocol-compliant before request construction.

The integration point is the market submit preparation path, not generic UI rendering. The view may still display user-facing values with existing formatting, but the value serialized into `action.orders[].p` for market submissions must pass exchange rules deterministically.

### Milestone 2: Parse nested submit statuses and make submit feedback truthful

Add a submit-outcome interpreter that accepts the raw exchange response and classifies result states from `response.data.statuses`: successful entries (`filled`, `resting`, waiting variants) and failed entries (`error`). Use this interpreter in `api-submit-order` so Hyperopen only shows the success toast when no per-order errors are present.

When any status includes `error`, show an actionable error message and set `:order-form-runtime :error`. If the response is mixed (some success, some errors), prefer a warning or error message that explicitly reports partial success and includes the failing reason(s).

### Milestone 3: Slippage policy alignment and no-fill guidance

Adjust market-order default slippage policy using source-backed behavior from audited references. At minimum, move away from an overly tight default that increases IOC cancel likelihood and provide deterministic guidance copy when statuses indicate no fill or no liquidity.

This milestone must keep slippage user-editable and deterministic in state. If final default differs from current value, update tests and any user-facing summary strings accordingly.

### Milestone 4: Regression coverage and acceptance hardening

Add targeted tests covering:

- market submit price formatting compliance on representative inputs;
- submit response classification for nested status errors;
- prevention of false-positive success toast on status-level errors;
- continued success behavior for true filled/resting outcomes.

Then run required repository gates and capture the result summary in this plan.

## Concrete Steps

From `/hyperopen`:

1. Implement market price formatting helper and wire it into market submit preparation paths.
   - Edit: `src/hyperopen/domain/trading/market.cljs`
   - Add: `src/hyperopen/domain/trading/exchange_format.cljs` (or equivalent domain-local formatter namespace)
   - Update tests in `test/hyperopen/state/trading/market_summary_test.cljs` or adjacent trading-domain test files.

2. Implement submit response outcome interpreter and integrate into submit effect.
   - Add: `src/hyperopen/order/submit_outcome.cljs` (name may vary, keep responsibility isolated)
   - Edit: `src/hyperopen/order/effects.cljs`
   - Update tests: `test/hyperopen/core_bootstrap/order_effects_test.cljs`

3. Align slippage default and messaging.
   - Edit: `src/hyperopen/trading/order_form_state.cljs`
   - Edit as needed for UI feedback: `src/hyperopen/views/trade/order_form_vm_submit.cljs` and/or order-form runtime error rendering path
   - Update tests: `test/hyperopen/state/trading/order_form_state_test.cljs`, submit-related view/effect tests.

4. Run validation gates.
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

5. Update this ExecPlan sections (`Progress`, `Surprises & Discoveries`, `Decision Log`, `Outcomes & Retrospective`) with actual implementation evidence and any scope changes.

## Validation and Acceptance

Acceptance is complete when all behaviors below are observable:

- A market order request generated by Hyperopen uses IOC and a protocol-compliant price string (valid sig-fig and decimal constraints for the selected market).
- A submit response with `status: "ok"` but nested `{error: ...}` in `response.data.statuses` does not produce “Order submitted.” toast and instead surfaces an explicit failure message.
- A submit response with only `filled`/`resting` statuses still produces success behavior and existing refresh actions.
- Required gates pass:
  - `npm run check`
  - `npm test`
  - `npm run test:websocket`

## Idempotence and Recovery

All edits are additive and safe to rerun. If formatter integration causes unexpected validation failures, keep the old path behind a local helper branch and gate new behavior with deterministic tests before removing fallback logic. If submit-outcome interpretation initially misclassifies responses, log the raw response in tests and tighten the classifier with explicit fixtures before shipping.

No data migrations or destructive operations are required.

## Artifacts and Notes

Source references used to build this plan:

- Hyperliquid Exchange Endpoint docs: `https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/exchange-endpoint`
- Hyperliquid Tick and Lot Size docs: `https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/tick-and-lot-size`
- Hyperliquid Error Responses docs: `https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/error-responses`
- nktkas SDK FAQ (market order guidance): `https://github.com/nktkas/hyperliquid/blob/798f7a6aad15560264b63f54fa4bbfd3fcab9bb3/docs/other/faq.md`
- nktkas SDK formatter implementation: `https://github.com/nktkas/hyperliquid/blob/798f7a6aad15560264b63f54fa4bbfd3fcab9bb3/src/utils/_format.ts`
- nktkas SDK nested-status error handling: `https://github.com/nktkas/hyperliquid/blob/798f7a6aad15560264b63f54fa4bbfd3fcab9bb3/src/api/exchange/_methods/_base/errors.ts`
- nomeida SDK market open/close helper: `https://github.com/nomeida/hyperliquid/blob/eb21422ab6ae9b466bcf862f58f380e632be939f/src/rest/custom.ts`
- official python SDK market open/close + slippage: `https://github.com/hyperliquid-dex/hyperliquid-python-sdk/blob/b4d2d1bfde9bfb3411fec3f781e3981b48a1a0c5/hyperliquid/exchange.py`
- official python SDK market-order example with status parsing: `https://github.com/hyperliquid-dex/hyperliquid-python-sdk/blob/b4d2d1bfde9bfb3411fec3f781e3981b48a1a0c5/examples/basic_market_order.py`

## Interfaces and Dependencies

No new external dependency is required. The implementation should introduce small internal interfaces with explicit responsibilities:

- Market exchange formatter interface:
  - Input: numeric price, `szDecimals`, market type (`perp` or `spot`).
  - Output: protocol-compliant string price or deterministic formatting failure.

- Submit outcome classifier interface:
  - Input: parsed exchange response map.
  - Output: `{outcome-key, success-count, error-count, messages, raw-statuses}`.

Existing runtime/effect boundaries remain unchanged; new behavior should be injected through `order/effects.cljs` and trading-domain helpers only.

Plan revision note: 2026-02-24 14:22Z - Initial plan created after external docs/SDK audit and local discrepancy analysis.
Plan revision note: 2026-03-09 18:31Z - Moved this authored-only plan to `/hyperopen/docs/exec-plans/deferred/` and recorded local `bd` issue `hyperopen-gvp` as historical scratch context during ExecPlan lifecycle cleanup.
