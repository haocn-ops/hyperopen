# Bring Subaccount Selection Into Header Parity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is governed by `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The durable work reference is a direct user request on 2026-06-03 to match Hyperliquid's subaccount header behavior while preserving Hyperopen's existing popover preference for the create-subaccount flow.

## Purpose / Big Picture

After this change, a connected user with subaccounts can tell from the header which account they are trading through. When the master account is active, the header selector exposes the master and owned subaccounts. When a subaccount is active, the trigger reads `Sub: <name>`, the dropdown shows a Master row and Sub row with copy-address controls, the dropdown includes a `Disconnect` action that returns to master, and a teal strip below the header states that the user is trading on behalf of that subaccount. This makes Hyperopen's account target state visible in the same place Hyperliquid makes it visible.

## Context References

Public refs:
- Direct user request with screenshots from `https://app.hyperliquid.xyz/subAccounts` showing the selected-subaccount trigger, banner, and dropdown rows.

Repo artifacts:
- `/hyperopen/docs/FRONTEND.md` requires anchored popovers or dropdowns for recoverable page-local controls and Playwright-backed UI verification.
- `/hyperopen/docs/BROWSER_TESTING.md` requires Playwright for deterministic browser validation and cleanup.
- `/hyperopen/src/hyperopen/views/header/account_selector.cljs` already owns the header account target selector view model and rendering.
- `/hyperopen/src/hyperopen/subaccounts/actions.cljs` already owns `select-subaccount` and `select-master-account`.

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-06-03 19:44Z) Inspected current header selector, subaccount actions, account context, UI docs, and existing header/subaccount tests.
- [x] (2026-06-03 19:44Z) Wrote this active ExecPlan for the header parity gap.
- [x] (2026-06-03 19:49Z) Added RED tests for selected-subaccount trigger copy, menu copy actions, disconnect-to-master, and active-subaccount banner; `npm test -- --focus hyperopen.views.header-account-selector-test` fell through to the full suite and failed with 20 expected assertions in the new tests.
- [x] (2026-06-03 20:21Z) Added coverage for restoring subaccount rows on `/trade` startup so persisted selected subaccounts can appear in the header.
- [x] (2026-06-03 20:21Z) Implemented the selector VM, render, loader, route-refresh, and banner changes.
- [x] (2026-06-03 20:21Z) Ran deterministic Playwright browser verification for the changed header flow.
- [x] (2026-06-03 20:21Z) Ran required repo gates and recorded evidence.

## Surprises & Discoveries

- Observation: the current header account selector already filters rows to subaccounts owned by the connected wallet and emits `select-subaccount` / `select-master-account` actions.
  Evidence: `/hyperopen/src/hyperopen/views/header/account_selector.cljs`.

- Observation: the current selected subaccount is already a real trading target, because `active-trading-account-address` resolves to the selected owned subaccount and mutation gating remains allowed for an owned selection.
  Evidence: `/hyperopen/src/hyperopen/account/context.cljs`.

- Observation: the header selector only works when subaccount rows are already loaded. On `/trade`, startup route refresh does not load those rows, and `load-subaccounts!` currently refuses to fetch outside `/subAccounts`.
  Evidence: `/hyperopen/src/hyperopen/startup/route_refresh.cljs` only emits `load-subaccounts-route` for `/subAccounts`, and `/hyperopen/src/hyperopen/subaccounts/effects.cljs` checks `load-route-active?`.

- Observation: Replicant rejects React-style `:<>` fragments at runtime when used as the header root.
  Evidence: browser console showed `InvalidCharacterError: Failed to execute 'createElement' on 'Document': The tag name provided ('<>') is not a valid name.` The final header uses a `display: contents` wrapper and the focused header test asserts the root is not `:<>`.

- Observation: direct Playwright store seeding must explicitly flush a render for this header fixture.
  Evidence: the subaccount state existed in `hyperopen.system/store`, but the header DOM remained unchanged until the helper called `hyperopen.app.bootstrap/render-app!` with the seeded state.

## Decision Log

- Decision: treat this as a header projection and affordance fix, not a new subaccount domain model.
  Rationale: domain state and trading routing already exist; the screenshots show the missing behavior in the header selector and global banner.
  Date/Author: 2026-06-03 / Codex

- Decision: implement `Disconnect` as the existing `select-master-account` action.
  Rationale: Hyperliquid's label means stop trading through the subaccount; in Hyperopen the safe equivalent is clearing `:selected-address`, persisting the empty selection, and reloading user data for the master account.
  Date/Author: 2026-06-03 / Codex

- Decision: load subaccount rows as global header/account state when the address watcher sees a connected wallet on non-`/subAccounts` routes.
  Rationale: the selector cannot safely display or restore a selected subaccount without owned rows; doing this from startup preserves the route-specific `/subAccounts` page action and avoids a duplicate fetch on that page.
  Date/Author: 2026-06-03 / Codex

## Outcomes & Retrospective

Implemented the requested parity behavior. The header selector now shows `Sub: <name>` when a selected owned subaccount is active, the menu shows separate Master and Sub rows with copy-address buttons, selected subaccount state exposes a `Disconnect` action back to master, and the header renders a teal active-subaccount strip below the header. Subaccount rows also load as global account/header state on non-`/subAccounts` routes when a wallet is connected, so a persisted selected subaccount can appear on `/trade`.

The `/subAccounts` create flow remains an anchored popover; it was not changed to a modal.

Validation passed:
- RED check: `npm test -- --focus hyperopen.views.header-account-selector-test` fell through to the full suite and failed with the expected new assertions before implementation.
- Browser: `PLAYWRIGHT_BASE_URL=http://127.0.0.1:8081 PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "header subaccount state|header account selector routes"` passed, including 375, 768, 1280, and 1440 width checks plus order/cancel `vaultAddress` routing and copy controls.
- `npm test` passed: 4,183 tests, 23,190 assertions, 0 failures, 0 errors.
- `npm run test:websocket` passed: 531 tests, 3,079 assertions, 0 failures, 0 errors.
- `npm run check` passed, including namespace size, boundary, browser support, release asset, style, dev cleanup, app, portfolio, worker, and test compiles with 0 warnings.

Remaining risk: the browser fixture uses a direct render flush after direct store seeding. This is limited to Playwright setup; production state changes still flow through app actions/effects and the existing render watch.

## Context and Orientation

The header renders from `/hyperopen/src/hyperopen/views/header_view.cljs`. That file calls `/hyperopen/src/hyperopen/views/header/vm.cljs`, which includes `:account-selector` from `/hyperopen/src/hyperopen/views/header/account_selector.cljs`. The account selector currently appears only when the wallet is connected and the connected owner has at least one owned subaccount row in `[:account-context :subaccounts :rows]`.

An "owned subaccount" means a subaccount row whose `:master` address matches the connected wallet owner address and whose `:sub-account-user` address is a normalized Ethereum-style address. The selected subaccount address lives at `[:account-context :subaccounts :selected-address]`. `/hyperopen/src/hyperopen/account/context.cljs` already treats that selected owned subaccount as the active trading account for orders and user data refreshes.

## Plan of Work

First, add deterministic tests in `/hyperopen/test/hyperopen/views/header_account_selector_test.cljs` and `/hyperopen/test/hyperopen/views/header_view_test.cljs`. The tests must prove the current gap before production code changes: selected subaccounts should render as `Sub: Desk`, option rows should include copy buttons for master and subaccount addresses, `Disconnect` should map to `select-master-account`, and a banner should appear below the header only for an owned selected subaccount.

Next, update `/hyperopen/src/hyperopen/views/header/account_selector.cljs`. Keep its existing owned-row filtering. Change the selected-subaccount trigger label to `Sub: <name>`, model each menu row as a row with a select action and a separate copy action, and include a disconnect action only when a subaccount is selected.

Then, update `/hyperopen/src/hyperopen/views/header_view.cljs` or the header VM to render a compact teal banner beneath the header when the account selector reports an active subaccount. The banner text should be `IMPORTANT: You are trading on behalf of your sub-account <name>`.

Finally, run focused tests, browser checks at representative widths, all required repo gates, and cleanup. If everything passes, move this file from `docs/exec-plans/active/` to `docs/exec-plans/completed/` and record the validation transcript in this plan.

## Concrete Steps

Run these commands from `/Users/barry/.codex/worktrees/2eb4/hyperopen`.

1. Add and verify RED tests:

   `npm test -- --focus hyperopen.views.header-account-selector-test`

   If the runner does not support `--focus`, run `npm test` and confirm the new assertions fail before implementation.

2. Implement the selector and banner changes.

3. Re-run focused tests, then:

   `npm test`
   `npm run test:websocket`
   `npm run check`

4. Run deterministic browser verification for the subaccount header flow. Reuse the local app server if available; otherwise start the smallest repo-supported local server and capture evidence at `375`, `768`, `1280`, and `1440` widths.

5. Run cleanup:

   `npm run browser:cleanup`

## Validation and Acceptance

Acceptance requires all of the following:

When state contains a connected master wallet, one owned subaccount named `Desk`, and selected address equal to that subaccount address, `selector/vm` returns a trigger label of `Sub: Desk` and the rendered header includes `Sub: Desk`.

The selector menu contains a Master row and Sub row. Each row has a copy button wired to `[:actions/copy-subaccount-address <address>]` for the relevant address. The selected subaccount menu also contains `Disconnect`, wired to `[:actions/select-master-account]`.

The rendered header includes the text `IMPORTANT: You are trading on behalf of your sub-account Desk` only while an owned subaccount is selected. It must not appear when no subaccount is selected or when the selected address is stale or unowned.

The create-subaccount flow remains an anchored popover on the `/subAccounts` page and is not converted into a modal.

Required repo gates pass: `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

The planned edits are additive and local to header selector rendering, header rendering, tests, and this plan file. Re-running tests and browser scripts is safe. If browser verification leaves any inspection sessions running, use `npm run browser:cleanup`. If the implementation introduces a bad UI direction, revert only the files touched in this plan and keep unrelated user changes intact.

## Artifacts and Notes

Primary changed files:
- `/hyperopen/src/hyperopen/views/header/account_selector.cljs`
- `/hyperopen/src/hyperopen/views/header_view.cljs`
- `/hyperopen/src/hyperopen/subaccounts/effects.cljs`
- `/hyperopen/src/hyperopen/startup/route_refresh.cljs`
- `/hyperopen/test/hyperopen/views/header_account_selector_test.cljs`
- `/hyperopen/test/hyperopen/views/header_subaccount_state_test.cljs`
- `/hyperopen/test/hyperopen/subaccounts/effects_test.cljs`
- `/hyperopen/test/hyperopen/startup/route_refresh_test.cljs`
- `/hyperopen/tools/playwright/test/trade-regressions.spec.mjs`

## Interfaces and Dependencies

No new libraries are needed. Use the existing Hiccup/Replicant render style, `hyperopen.account.context` address normalization helpers, `hyperopen.wallet.core/short-addr`, existing `:actions/select-subaccount`, existing `:actions/select-master-account`, and existing `:actions/copy-subaccount-address`.

Revision note: created on 2026-06-03 to capture the requested header-subaccount parity work before implementation.
