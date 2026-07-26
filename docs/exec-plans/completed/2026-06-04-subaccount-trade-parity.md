# Subaccount Trade Parity Implementation Plan

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## Purpose / Big Picture

Bring Hyperopen's selected subaccount trading UI into parity with Hyperliquid by using one connected-account dropdown and showing selected subaccount funds in the order ticket.

Context reference: direct maintainer request in the 2026-06-04 Codex session with Hyperliquid screenshots showing the single Master/Sub wallet dropdown and selected subaccount order ticket availability.

**Architecture:** Keep `hyperopen.account.context` as the owner/read/trading identity seam. The header wallet dropdown becomes the single connected-account selector surface, and the order-form VM reads the selected owned subaccount's inline `clearinghouse-state` and `spot-state` from the `subAccounts` response before falling back to top-level account surfaces.

**Tech Stack:** ClojureScript, Replicant Hiccup views, existing Nexus action/effect model, existing CLJS tests, existing Playwright smoke harness.

---

## Context

Hyperliquid documents that subaccounts do not have private keys; the master signs order-like actions and `vaultAddress` is set to the subaccount address. Its `subAccounts` info response includes each subaccount's `clearinghouseState` and `spotState`, which are enough to display the selected subaccount's account value and spot balances while slower live surfaces catch up.

Hyperopen already routes submitted orders through the master signer with subaccount `vaultAddress`, but the header currently renders a second account-target dropdown next to the wallet dropdown. The order ticket also derives "Available to Trade" from top-level `:webdata2` and `:spot` state, which can still reflect the master or an empty snapshot immediately after a transfer or selection.

## Progress

- [x] 2026-06-04 - Investigated account context, header selector, wallet menu, order form VM, subaccount effects, and Hyperliquid docs.
- [x] Add RED tests for single-dropdown header parity.
- [x] Add RED tests for selected subaccount inline account snapshot usage.
- [x] Implement single connected-account dropdown in the header.
- [x] Implement selected subaccount snapshot preference for order-form availability.
- [x] Run focused tests, Playwright regression, required gates, and cleanup.

## Surprises & Discoveries

- Hyperliquid uses one connected-account dropdown. Hyperopen renders `account-selector/render` and `wallet/render` separately, creating two dropdowns when subaccounts exist.
- Hyperliquid's order ticket can display selected subaccount funds because its subaccount state includes inline `clearinghouseState` and `spotState`. Hyperopen normalizes those into each subaccount row but the order-form trading context does not consume them.
- Existing order submission routing is not the failing layer: tests already prove selected subaccount orders are submitted from the owner address with `{:vault-address selected-subaccount-address}`.
- Playwright initially reused a stale Shadow server on `127.0.0.1:8080`, so the focused browser run did not include the current wallet-menu account-selector section. The verified run used the repo static Playwright server on `127.0.0.1:4173` after compiling the current bundle.

## Decision Log

- Decision: Collapse the subaccount selector into `wallet/render` instead of preserving a second header dropdown.
  Rationale: Hyperliquid presents Master/Sub selection inside the connected wallet dropdown, and this removes the duplicate dropdown that confused trading context selection.

- Decision: Prefer selected owned subaccount row snapshots in the trading context before falling back to top-level `:webdata2` and `:spot`.
  Rationale: Hyperliquid's `subAccounts` response carries `clearinghouseState` and `spotState`, which are the freshest known selected-subaccount balances immediately after transfer or selection.

- Decision: Keep order and cancel submission routing unchanged.
  Rationale: Existing coverage already validates owner signing plus selected subaccount `vaultAddress`; the broken behavior was display/context availability, not request signing.

## Outcomes & Retrospective

The header now uses the connected wallet dropdown as the single Master/Sub selection surface. Selecting a subaccount updates the trigger to `Sub: <name>`, keeps copy controls for both addresses in the same menu, and preserves the active subaccount trading banner.

The order ticket now prefers the selected owned subaccount row's inline `spotState` and `clearinghouseState` snapshots for availability/account context before falling back to top-level master surfaces. Existing owner-signed order/cancel routing with selected subaccount `vaultAddress` remains covered.

Validation results:

- `npm test -- --focus hyperopen.views.header-account-selector-test --focus hyperopen.state.trading.market-summary-test --focus hyperopen.views.trade.order-form-view.metrics-and-submit-test` passed with 4,194 tests and 23,263 assertions. The runner reports unknown `--focus` args and runs the full CLJS suite.
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4173 PLAYWRIGHT_WEB_SERVER_COMMAND='npm run css:build && npx shadow-cljs --force-spawn compile app portfolio portfolio-worker portfolio-optimizer-worker vault-detail-worker && PLAYWRIGHT_WEB_PORT=4173 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "header subaccount state|header account selector routes"` passed with 2 tests.
- `npm run check` passed.
- `npm test` passed with 4,194 tests and 23,263 assertions.
- `npm run test:websocket` passed with 531 tests and 3,079 assertions.
- `git diff --check` passed.
- `npm run browser:cleanup` passed with no sessions to stop.

## Implementation Steps

1. Write tests in `test/hyperopen/views/header_account_selector_test.cljs` and `test/hyperopen/views/header_view_test.cljs`:
   - Selected subaccount trigger appears on `wallet-menu-trigger`.
   - `header-account-target-details` is absent.
   - `wallet-menu-panel` contains Master and Sub rows with select and copy actions.
   - Existing wallet enable-trading and disconnect actions remain present.

2. Write tests in `test/hyperopen/state/trading/market_summary_test.cljs` and `test/hyperopen/views/trade/order_form_view/metrics_and_submit_test.cljs`:
   - A selected owned subaccount row with `:spot-state {:balances [{:coin "USDC" :total "15" :hold "0"}]}` drives `available-to-trade` under unified mode even when top-level master spot state is empty.
   - A selected owned subaccount row with `:clearinghouse-state` drives perp fallback availability when unified spot is unavailable.

3. Update `src/hyperopen/views/header/wallet.cljs`:
   - Accept `:account-selector` in the wallet VM.
   - Use its selected trigger label/address label for the connected wallet trigger.
   - Render account rows inside the wallet menu before wallet copy/enable/disconnect controls.
   - Keep copy buttons as separate row controls.

4. Update `src/hyperopen/views/header_view.cljs`:
   - Stop rendering `account-selector/render` as a second dropdown.
   - Pass the selector VM into `wallet/render`.
   - Keep the active subaccount banner unchanged.

5. Update `src/hyperopen/state/trading.cljs`:
   - Add helpers to obtain selected subaccount row `:clearinghouse-state` and `:spot-state`.
   - Prefer selected row snapshots in `active-clearinghouse-state-for-market` and `trading-context` when an owned subaccount is selected.

6. Validate:
   - Focused CLJS tests for header, trading summary, and order form metrics.
   - Focused Playwright trade regression for header subaccount selector/order ticket.
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
   - `npm run browser:cleanup`

## Validation and Acceptance

- With a selected subaccount named `Desk`, there is exactly one connected account dropdown in the header and its trigger reads `Sub: Desk`.
- The dropdown lists Master and Sub rows, exposes copy buttons for both addresses, and keeps Disconnect available.
- The order ticket shows selected subaccount available USDC from the inline subaccount row when top-level account surfaces have not caught up.
- Existing subaccount order submission still signs as master with selected subaccount `vaultAddress`.

Validation commands:

- `npm test -- --focus hyperopen.views.header-account-selector-test --focus hyperopen.state.trading.market-summary-test --focus hyperopen.views.trade.order-form-view.metrics-and-submit-test`
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4173 PLAYWRIGHT_WEB_SERVER_COMMAND='npm run css:build && npx shadow-cljs --force-spawn compile app portfolio portfolio-worker portfolio-optimizer-worker vault-detail-worker && PLAYWRIGHT_WEB_PORT=4173 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "header subaccount state|header account selector routes"`
- `npm run check`
- `npm test`
- `npm run test:websocket`
- `git diff --check`
- `npm run browser:cleanup`
