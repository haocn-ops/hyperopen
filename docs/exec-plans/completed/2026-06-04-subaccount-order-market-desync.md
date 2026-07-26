# Subaccount Order Market Desync Fix

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

## Purpose / Big Picture

Fix a live subaccount order failure where the trade route shows BTC and submits a market order, but the signed exchange payload combines the BTC orderbook-derived IOC price with a stale ETH `asset-idx`. This must restore safe order request construction for selected subaccounts and master accounts without changing Hyperliquid's market-order wire convention.

Context reference: direct maintainer request in the 2026-06-04 Codex session, including screenshots of the failed order toast and network payload.

## Context

Hyperliquid market orders are represented as IOC limit-shaped exchange orders. The user-submitted payload had `t: {limit: {tif: "Ioc"}}`, which is expected. Live nREPL inspection on port `60091` showed the real bug: `:active-asset` and route were BTC, the orderbook was BTC around `63,9xx`, but `:active-market` was still ETH with `asset-idx` 1 and mark near `1775`. The request therefore placed an ETH order with a BTC protection price, triggering Hyperliquid's `Order price cannot be more than 95% away from the reference price` rejection.

## Progress

- [x] 2026-06-04 - Inspected live app state through Shadow nREPL and confirmed active asset/market desync.
- [x] 2026-06-04 - Added a RED state-wrapper regression for stale `:active-market` plus matching selector cache; it failed with asset id `1` instead of `0`.
- [x] 2026-06-04 - Routed trading context and related market metadata through the existing resolved active-market helper.
- [x] 2026-06-04 - Confirmed through live nREPL that the same running app state now builds asset id `0` with IOC.
- [x] 2026-06-04 - Ran focused CLJS tests, live nREPL verification, required gates, diff check, and browser cleanup.

## Surprises & Discoveries

- The exchange payload is not incorrectly using a GTC limit order. It uses Hyperliquid's market-order IOC wire shape.
- `hyperopen.state.trading` already has a `resolved-active-market` helper that rejects stale `:active-market` values, but the order construction context still reads raw `(:active-market state)`.
- The source file `src/hyperopen/state/trading.cljs` is already at the namespace-size ceiling, so the fix had to reuse existing lines rather than adding another helper.

## Decision Log

- Decision: Fix the invariant in `hyperopen.state.trading` instead of adding a submit-button guard.
  Rationale: Market precision, asset id, leverage pre-actions, summaries, and request building all depend on the same active-market context. The boundary should not allow cross-market state combinations.

## Outcomes & Retrospective

`src/hyperopen/state/trading.cljs` now resolves the active market from the existing selector cache before deriving market identity, margin policy, clearinghouse state, asset id, and order-request context. This prevents a stale projected `:active-market` from being combined with a newer `:active-asset` and orderbook.

The new regression in `test/hyperopen/state/trading/order_request_test.cljs` reproduces the live failure mode and proves a BTC market order with stale ETH `:active-market` builds asset id `0`, a BTC IOC price, and a BTC leverage pre-action.

Validation results:

- RED: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.state.trading.order-request-test` failed with asset id `1` before implementation.
- GREEN focused: the same command passed with 7 tests and 48 assertions.
- Adjacent focused: `node out/test.js --test=hyperopen.state.trading.order-request-test --test=hyperopen.state.trading.identity-and-submit-policy-test --test=hyperopen.state.trading.market-summary-test` passed with 41 tests and 198 assertions.
- Live nREPL on port `60091` showed the same app state still had `:active-asset "BTC"` and raw `:active-market` coin `ETH`, but now built asset id `0`, pre-action asset `0`, and `tif "Ioc"`.
- `npm run check` passed.
- `npm test` passed with 4,195 tests and 23,268 assertions.
- `npm run test:websocket` passed with 531 tests and 3,079 assertions.
- `git diff --check` passed.
- `npm run browser:cleanup` passed with no sessions to stop.

## Implementation Steps

1. Add a failing regression in `test/hyperopen/state/trading/order_request_test.cljs` that reproduces the live mismatch: active asset BTC, stale ETH active market, BTC in `[:asset-selector :market-by-key]`, and a market buy order.
2. Update `src/hyperopen/state/trading.cljs` so trading context, market identity/info, margin policy, clearinghouse selection, and asset-index resolution use `resolved-active-market` before falling back.
3. Run focused CLJS tests for order requests and submit policy.
4. Use live nREPL or browser QA to confirm the fixed runtime would produce a BTC asset id with BTC protection price.
5. Run required validation: `npm run check`, `npm test`, `npm run test:websocket`, `git diff --check`, and `npm run browser:cleanup`.

## Validation and Acceptance

- A market order with `:active-asset "BTC"` and a stale ETH `:active-market` must build a request using BTC's `asset-id` from selector cache, not ETH's asset id.
- The built request must still use the expected Hyperliquid IOC market-order wire shape.
- Existing formal order-request vectors and subaccount routing tests must continue to pass.
