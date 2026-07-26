# Split Trading State Module into Domain, Form State, and Hyperliquid Adapter

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` are maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The trading module previously mixed form state defaults, domain math, validation rules, and Hyperliquid wire payload construction in one namespace. After this change, those concerns are separated so each module has one clear reason to change while preserving the existing `hyperopen.state.trading` API for callers. Users should see unchanged runtime behavior and passing test suites, with cleaner extension points for new order types and validation rules.

## Progress

- [x] (2026-02-13 16:10Z) Created a module split: `/hyperopen/src/hyperopen/trading/order_form_state.cljs`, `/hyperopen/src/hyperopen/domain/trading/core.cljs`, `/hyperopen/src/hyperopen/domain/trading/market.cljs`, `/hyperopen/src/hyperopen/domain/trading/validation.cljs`, and `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs`.
- [x] (2026-02-13 16:25Z) Replaced string-based validation outputs with structured error maps and added deterministic message and required-field mapping helpers.
- [x] (2026-02-13 16:30Z) Converted `/hyperopen/src/hyperopen/state/trading.cljs` to a compatibility facade that delegates to domain and adapter modules through compact contexts.
- [x] (2026-02-13 16:35Z) Updated submit path in `/hyperopen/src/hyperopen/order/actions.cljs` to render the first validation message from structured errors.
- [x] (2026-02-13 16:55Z) Updated tests and passed required gates: `npm run check`, `npm test`, and `npm run test:websocket`.

## Surprises & Discoveries

- Observation: Market-slippage tests regressed to `0` after initial extraction.
  Evidence: `market-slippage-estimate-*` tests failed because normalized forms can force `:type` away from the originally requested type.
- Observation: Preserving prior behavior required carrying the raw requested order type into summary evaluation.
  Evidence: adding `:requested-type` in the facade and reading it in `/hyperopen/src/hyperopen/domain/trading/market.cljs` restored all slippage assertions.

## Decision Log

- Decision: Keep `/hyperopen/src/hyperopen/state/trading.cljs` as the public compatibility facade.
  Rationale: Existing call sites and tests import this namespace broadly; a facade avoids high-churn call-site edits while improving internals.
  Date/Author: 2026-02-13 / Codex
- Decision: Implement structured validation as `{:code ... :fields [...]}` and provide `validation-error-message` helper.
  Rationale: Removes brittle string coupling while keeping UI and tooltip behavior stable.
  Date/Author: 2026-02-13 / Codex
- Decision: Split domain logic across `core`, `market`, and `validation`.
  Rationale: Keeps each new namespace below architecture size limits and aligns SRP boundaries.
  Date/Author: 2026-02-13 / Codex

## Outcomes & Retrospective

The refactor achieved the boundary split with unchanged external API behavior and full test parity. Validation is now machine-structured and safer for copy changes. Remaining follow-up work (optional) is to migrate call sites over time from `hyperopen.state.trading` to focused namespaces directly, then retire facade-only indirection if desired.

## Context and Orientation

Relevant modules after the change:

- `/hyperopen/src/hyperopen/state/trading.cljs`: compatibility facade and state-to-context extraction.
- `/hyperopen/src/hyperopen/trading/order_form_state.cljs`: default form shape and UI-flag normalization.
- `/hyperopen/src/hyperopen/domain/trading/core.cljs`: core order-type policy, parsing helpers, scale math.
- `/hyperopen/src/hyperopen/domain/trading/market.cljs`: market/pricing/account calculations against a compact context map.
- `/hyperopen/src/hyperopen/domain/trading/validation.cljs`: structured validation rules and error-to-UI mapping.
- `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs`: Hyperliquid command payload builders and wire constants.

Structured validation now uses error maps, for example:

- `{:code :order/size-invalid :fields [:size]}`

UI-facing strings are derived at the boundary by `validation-error-message`.

## Plan of Work

The work was executed in five stages. First, move UI form defaults and shape normalization into a focused form-state module. Second, isolate domain policy and calculations from full-store shape by introducing compact context maps and pure domain modules. Third, isolate Hyperliquid payload and command construction into an adapter namespace. Fourth, keep public compatibility by turning `hyperopen.state.trading` into a thin delegating facade. Fifth, update submit validation integration and tests, then run required gates.

## Concrete Steps

All commands were run from `/Users//projects/hyperopen`.

1. Create new namespaces and move functionality into the split modules.
2. Update `order/actions.cljs` to display the first structured validation error through `validation-error-message`.
3. Update trading tests to assert validation codes instead of message strings.
4. Compile and run full validation gates.

Executed commands:

    npx shadow-cljs compile test
    npx shadow-cljs compile app
    npm run check
    npm test
    npm run test:websocket

## Validation and Acceptance

Acceptance criteria:

- Public `hyperopen.state.trading` API remains callable by existing imports.
- Validation returns structured errors; tooltips and submit errors still show deterministic text.
- Full repository validation gates pass.

Observed results:

- `npm run check`: passed.
- `npm test`: passed (695 tests, 0 failures).
- `npm run test:websocket`: passed (82 tests, 0 failures).

## Idempotence and Recovery

The refactor is additive with a compatibility facade, so repeating build/test commands is safe. If a split module causes regression, callers remain insulated behind `hyperopen.state.trading`, allowing internal fixes without broad call-site churn.

## Artifacts and Notes

Key files changed:

- `/hyperopen/src/hyperopen/state/trading.cljs`
- `/hyperopen/src/hyperopen/trading/order_form_state.cljs`
- `/hyperopen/src/hyperopen/domain/trading.cljs`
- `/hyperopen/src/hyperopen/domain/trading/core.cljs`
- `/hyperopen/src/hyperopen/domain/trading/market.cljs`
- `/hyperopen/src/hyperopen/domain/trading/validation.cljs`
- `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs`
- `/hyperopen/src/hyperopen/order/actions.cljs`
- `/hyperopen/test/hyperopen/state/trading_test.cljs`

## Interfaces and Dependencies

Stable facade interfaces kept in `/hyperopen/src/hyperopen/state/trading.cljs`:

- `default-order-form`
- `normalize-order-form`
- `validate-order-form`
- `submit-required-fields`
- `build-order-request`

Domain interfaces:

- `hyperopen.domain.trading.core`: type policy, parsing, scale ladder math.
- `hyperopen.domain.trading.market`: pricing/account/position calculations against compact context.
- `hyperopen.domain.trading.validation`: coded validation and message/field mapping.

Adapter interface:

- `hyperopen.api.gateway.orders.commands`: Hyperliquid action payload construction.

Plan revision note: created directly in completed state after implementation because this task was executed end-to-end in one session, and the final document captures both plan and execution evidence for recovery.
