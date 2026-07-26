# Implement Hyperliquid Subaccounts

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Hyperopen currently treats the connected wallet as the writable trading account. Hyperliquid subaccounts introduce a third identity: the master wallet still owns signing authority, while a selected subaccount owns balances, positions, open orders, fills, and post-trade refreshes. The implementation must let a connected master wallet list, create, rename, fund, select, and trade subaccounts without weakening the existing Spectate Mode safety model.

The user-visible proof is a new `/subAccounts` management route and an account selector that can switch the active trading target between the master account and owned subaccounts. When a subaccount is selected, account surfaces, websocket user streams, orders, cancels, TP/SL, margin changes, fills, and post-mutation refreshes all target the subaccount address. Signatures remain owned by the connected master wallet or its approved API wallet, and order-like exchange payloads include `vaultAddress` equal to the selected subaccount address.

## Context References

User request:

- Direct user request on 2026-06-03: develop an execution plan for implementing Hyperliquid subaccounts in Hyperopen, referencing Hyperliquid's main app, official docs, and external SDKs already referenced in the repo.

Official Hyperliquid references:

- `https://app.hyperliquid.xyz/subAccounts` - Hyperliquid's subaccounts management route. In a restricted unauthenticated session it showed a `Sub-Accounts` page with `Master Account` and `Sub-Accounts` tables, `Connect`, and `Establish Connection` controls. The live chunk also contains `createSubAccount` and `subAccountModify` actions.
- `https://hyperliquid.gitbook.io/hyperliquid-docs/trading/sub-accounts` - subaccount limits and behavior: creation requires at least `$100,000` volume, starts at up to 10 subaccounts, increases by 1 per additional `$100M` volume up to 50, shares master fee tiers, and increases API-wallet allowance.
- `https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/info-endpoint` - `/info` request `{"type":"subAccounts","user":"0x..."}` returns `null` or rows with `name`, `subAccountUser`, `master`, `clearinghouseState`, and `spotState`; user account data queries must use the actual master or subaccount address, not the agent wallet address.
- `https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/exchange-endpoint` - subaccounts and vaults have no private keys; the master signs and the outer exchange payload sets `vaultAddress` to the subaccount or vault address for order-like actions.
- `https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/nonces-and-api-wallets` - API wallets can sign on behalf of the master account or subaccounts, and nonce behavior remains signer-centered.

SDK references:

- `/hyperopen/docs/references/hyperliquid-sdks.md` - repo reference listing the Python SDK, `nktkas/hyperliquid`, and `nomeida/hyperliquid` as SDKs to cross-check.
- `https://github.com/hyperliquid-dex/hyperliquid-python-sdk/blob/master/hyperliquid/info.py` - `query_sub_accounts` wraps `/info {"type":"subAccounts"}`.
- `https://github.com/hyperliquid-dex/hyperliquid-python-sdk/blob/master/hyperliquid/exchange.py` - `Exchange(..., vault_address=...)`, `create_sub_account`, `sub_account_transfer`, `sub_account_spot_transfer`, plus special `usd_class_transfer` and `send_asset` handling for subaccounts.
- `https://github.com/nktkas/hyperliquid/blob/main/src/api/info/_methods/subAccounts.ts` - typed `subAccounts` request/response shape.
- `https://github.com/nktkas/hyperliquid/blob/main/src/api/exchange/_methods/createSubAccount.ts` - create action and 1-16 character name validation.
- `https://github.com/nktkas/hyperliquid/blob/main/src/api/exchange/_methods/subAccountModify.ts` - rename action and 1-16 character name validation.
- `https://github.com/nktkas/hyperliquid/blob/main/src/api/exchange/_methods/subAccountTransfer.ts` - perp USDC master/subaccount transfer action with `usd` in raw 1e6 units.
- `https://github.com/nktkas/hyperliquid/blob/main/src/api/exchange/_methods/subAccountSpotTransfer.ts` - spot master/subaccount transfer action.
- `https://github.com/nomeida/hyperliquid/blob/main/src/rest/info/general.ts` and `https://github.com/nomeida/hyperliquid/blob/main/src/rest/exchange.ts` - third SDK confirmation of `getSubAccounts`, `createSubAccount`, `subAccountModify`, `subAccountTransfer`, and `subAccountSpotTransfer`.

Repo references:

- `/hyperopen/docs/architecture-decision-records/0024-effective-account-address-and-spectate-mode-ownership.md` - owner signing identity must stay separate from read-side account identity.
- `/hyperopen/docs/product-specs/agent-wallet-order-signing-prd.md` - data queries/subscriptions must use actual user, subaccount, or vault addresses; the agent address is signing identity only.
- `/hyperopen/docs/BROWSER_STORAGE.md` - any persisted selected-subaccount preference must follow browser-storage policy.
- `/hyperopen/docs/BROWSER_TESTING.md` - Browser MCP is for exploratory Hyperliquid parity; Playwright is required for committed deterministic browser coverage.
- `/hyperopen/src/hyperopen/account/context.cljs` - current owner/spectate/trader effective-address selectors and mutation guards.
- `/hyperopen/src/hyperopen/account/surface_service.cljs` - account-surface bootstrap and refresh pipeline for the active address.
- `/hyperopen/src/hyperopen/api/endpoints/account/agents.cljs` - existing `extraAgents` and `webData2` account endpoint pattern; add subaccounts alongside this seam.
- `/hyperopen/src/hyperopen/api/trading/agent_actions.cljs` and `/hyperopen/src/hyperopen/api/trading/http.cljs` - low-level L1 signing and outer `/exchange` post path already support `vault-address`, but public order/cancel facades do not pass it yet.
- `/hyperopen/src/hyperopen/api/trading/user_actions.cljs` - user-signed transfer path; subaccount `usdClassTransfer` and `sendAsset` are not ordinary `vaultAddress` actions.
- `/hyperopen/src/hyperopen/order/effects.cljs` - order submit/cancel/margin/TP-SL paths currently submit and refresh against `[:wallet :address]`; this is the main correctness risk.
- `/hyperopen/src/hyperopen/websocket/user_runtime/subscriptions.cljs` - user stream subscriptions must switch to the selected actual account address.
- `/hyperopen/src/hyperopen/views/header/wallet.cljs` and `/hyperopen/src/hyperopen/views/app_view.cljs` - likely surfaces for active account selection/status.

## Current Protocol Facts To Preserve

- `POST /info {"type":"subAccounts","user":master}` returns `null` or subaccount rows with the subaccount's actual perp and spot state.
- Account reads for a selected subaccount must pass `subAccountUser` as `user`; using the API wallet address is a documented pitfall.
- Subaccounts do not have private keys. The master wallet or approved agent wallet signs; order-like L1 actions include outer `vaultAddress` equal to the subaccount address and include that value in the L1 signature hash.
- `createSubAccount`, `subAccountModify`, `subAccountTransfer`, and `subAccountSpotTransfer` are master-scoped management actions and should sign with no outer `vaultAddress`.
- `subAccountTransfer` uses `usd` in raw 1e6 units. UI amount `1.23 USDC` becomes integer `1230000`.
- `usdClassTransfer` is user-signed and targets a subaccount by appending ` subaccount:<address>` to the string `amount`; the outer payload must not set `vaultAddress`.
- `sendAsset` is user-signed and targets a source subaccount through `fromSubAccount`; the outer payload must not set `vaultAddress`.
- Subaccount names should be normalized and validated as 1-16 characters, matching the live app and SDK constraints.
- API wallets remain owner-scoped. A master-approved agent can be used for subaccount trading if Hyperliquid accepts the master agent plus `vaultAddress`; this must be verified live, but it is the documented and SDK-modeled path.

## Initial Scope

V1 includes:

- List subaccounts for the connected master wallet.
- Create and rename subaccounts.
- Select master or one owned subaccount as the active writable trading account.
- Persist the last selected owned subaccount per master wallet, after validating ownership from the latest `subAccounts` response.
- Route account surfaces, websocket user streams, open orders, fills, historical orders, funding history, and portfolio views to the selected actual account address.
- Route order, cancel, cancel-by-cloid if present, modify if present, TP/SL, isolated-margin, leverage, schedule-cancel, and optimizer-generated order submission through the master signer with `vaultAddress` when a subaccount is selected.
- Support master/subaccount perp USDC transfers through `subAccountTransfer`.
- Add deterministic unit/integration tests and a committed Playwright smoke flow using Hyperopen's simulator/debug bridge.

V1 does not include until live/testnet evidence resolves the gaps:

- Deleting subaccounts. The referenced docs/SDKs expose rename, not delete.
- Full spot token master/subaccount transfer UI. The action shape is known, but exact token-string selection for USDC and non-USDC assets needs a live pass.
- External user-to-user subaccount sends beyond the documented `sendAsset` source-subaccount field.
- Staking, vault-deposit, validator delegation, and bridge withdraw actions from a selected subaccount. These should remain owner-only or disabled with a clear message until protocol support is verified.
- Keyed side-by-side owner and subaccount account-surface caches. V1 keeps Hyperopen's existing single active-account surface model and resets/refetches on switch.

## Progress

- [x] 2026-06-03 - Read repo operating contract, plan requirements, browser-testing routing, and relevant account/trading code.
- [x] 2026-06-03 - Researched official Hyperliquid docs for subaccount limits, `/info` shape, exchange `vaultAddress` semantics, and API wallet context.
- [x] 2026-06-03 - Cross-checked Python, TypeScript, and Rust/third-party SDK exposure for subaccount actions and signing special cases.
- [x] 2026-06-03 - Inspected Hyperliquid's live `/subAccounts` route in a restricted unauthenticated environment and checked the app bundle for create/rename action names.
- [x] 2026-06-03 - Ran a read-only Hyperopen identity/trading audit with an explorer agent.
- [x] 2026-06-03 - Ran a read-only Hyperliquid protocol/app/SDK research pass with an explorer agent.
- [x] M1a - Add `/info` endpoint support and account API exposure for subaccounts, with RED tests proving normalization, request policy, gateway delegation, and default facade access.
- [x] M2a - Add account-context selectors for selected owned subaccount, active trading account, exchange `vaultAddress`, read-only overrides, and stale/unowned selection blocking.
- [x] M5a - Add public trading facade option propagation for order, cancel, and schedule-cancel; route order submit, cancel, TP/SL, and margin effects through owner signer plus selected subaccount `vaultAddress`.
- [x] M1b - Added master-scoped `createSubAccount`, `subAccountModify`, and `subAccountTransfer` trading facades with tests proving management actions omit outer `vaultAddress`, while order-like actions propagate it.
- [x] M2b - Added subaccount load/select actions, route refresh effects, stale/unowned selection reset, per-owner localStorage selection persistence, runtime wiring, schema contracts, and formal effect-order registration.
- [x] M3 - Built the `/subAccounts` management route with master/subaccount rows, refresh, route-local account selection, create, rename, and perp USDC transfer controls; added header More navigation to the route.
- [x] M4 - Routed active read-side account defaults and websocket desired user stream address through selected owned subaccounts; added API-load and websocket regression coverage for selected subaccounts.
- [x] M5b - Routed optimizer execution and agent-safety schedule-cancel through owner signer plus selected subaccount `vaultAddress`, with focused regression coverage.
- [x] M6 - Added master/subaccount perp USDC transfer support, decimal-to-micro-USDC parsing, direction mapping, success/error effects, and refresh behavior.
- [x] M7 - Added the global header account selector, simulator request-payload capture, and deterministic Playwright selector/order-payload smoke proving subaccount `vaultAddress` is present and master `vaultAddress` is absent.
- [x] Follow-up - Moved connected-wallet Hyperliquid parity, live/testnet response-body gaps, spot transfer validation, subaccount-sensitive owner-only action validation, and unrelated smoke-suite health to `/hyperopen/docs/exec-plans/deferred/2026-06-03-hyperliquid-subaccounts-live-parity-and-smoke-health.md`.
- [x] 2026-06-03 - RED tests confirmed missing management/trading/routing functions before implementation; subsequent CLJS runner passed after implementation.
- [x] 2026-06-03 - `npm run formal:sync -- --surface effect-order-contract` passed after adding subaccount management effect-order policies and regenerating vectors.
- [x] 2026-06-03 - `npm run check` passed after adding the header selector and simulator request-capture test support.
- [x] 2026-06-03 - `npm test` passed: 4,178 tests, 23,147 assertions, 0 failures, 0 errors.
- [x] 2026-06-03 - `npm run test:websocket` passed: 531 tests, 3,079 assertions, 0 failures, 0 errors.
- [x] 2026-06-03 - Focused Playwright route smoke passed for `/subAccounts` using this worktree's static dev-assets server on port 4174.
- [x] 2026-06-03 - Focused Playwright selector/order-payload smoke passed: `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=4174 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "header account selector routes subaccount order payloads"`.
- [x] 2026-06-03 - `npm run test:playwright:smoke` ran after the focused selector smoke. Subaccount route and selector smokes passed; the suite result was 35 passed and 3 unrelated optimizer failures, now recorded in the deferred follow-up plan.

## Surprises & Discoveries

- Hyperopen already has the right architectural seam for this feature: ADR 0024 separates signing owner identity from read-side effective account identity. Subaccounts should extend that seam rather than introduce a parallel account router.
- The low-level trading path already accepts `:vault-address` for L1 signing and outer exchange payloads, but the order/cancel facades and `order/effects.cljs` call sites do not pass it. The high-risk work is propagation, not cryptography from scratch.
- Hyperliquid's official exchange page is explicit about `vaultAddress` for subaccounts and vaults, but it is incomplete for create/rename/transfer action details. The SDKs and live app bundle provide the missing action shapes.
- `usdClassTransfer` and `sendAsset` are exceptions. They are user-signed actions and do not use outer `vaultAddress`; the selected subaccount must be encoded in action fields instead.
- Hyperliquid's main app did not issue `subAccounts` in the restricted unauthenticated session, so connected-wallet parity still needs a live Browser MCP pass.
- The current Hyperopen account page stores one global active-account set of surfaces. Fast switching is feasible with reset/refetch guards; side-by-side owner/subaccount dashboards are a larger future design.
- Port 8080 was already occupied by another Hyperopen worktree's dev server, so focused Playwright browser validation used the repo static server on port 4174 against this worktree's compiled dev assets. The full smoke suite used the repo smoke script and passed all subaccount route and selector checks.
- The final implementation added a compact global header account selector in addition to the route-local `/subAccounts` selector. The Playwright smoke uncovered a test fixture issue: signing with the previous fake private key failed before the exchange simulator could capture payloads, so the smoke now seeds a valid 32-byte fixture key.
- The debug exchange simulator originally recorded only matched response paths. Capturing the request body in simulator calls made browser-level `vaultAddress` assertions possible without weakening production exchange transport behavior.
- The remaining full-smoke failures on 2026-06-03 were optimizer-specific: missing target-exposure exclude button, unexpected Use My Views return value, and add-asset selector overflow at 1280px. They did not reproduce the earlier diagnostics, spectate, footer, mobile-header, or chart-context-menu failures and are deferred outside this subaccounts implementation.

## Decision Log

- Extend `hyperopen.account.context` as the canonical owner/read/trading identity seam. Do not create a second wallet model for subaccounts.
- Keep `[:wallet :address]` as the only owner signing authority and agent-session storage key. Never persist or treat a subaccount address as a signer.
- Add a selected writable subaccount below spectate/trader read-only overrides. Priority for account reads becomes trader portfolio route, then active Spectate Mode, then selected owned subaccount, then owner wallet.
- Mutations remain blocked for trader portfolio and Spectate Mode. Mutations are allowed for a selected subaccount only when the connected owner matches the subaccount row's `master`.
- Reuse the existing API-wallet enabled-trading model for subaccount trading. If live verification later shows Hyperliquid requires per-subaccount API-wallet approval, revise the agent-session design before shipping trading support.
- Ship perp USDC master/subaccount transfer first because its action shape and unit conversion are clear across SDKs. Treat spot transfer as a follow-up until token string UX is verified live.
- Persist only a convenience selection, not protocol authority. On startup or wallet change, discard the persisted selection unless it appears in the latest owner `subAccounts` result.
- Do not enable staking, vault, bridge, or validator mutations for selected subaccounts in V1. These paths currently assume the owner account and have different protocol semantics.
- Split new trading signing coverage into focused `subaccount-vault-signing` and `subaccount-management-signing` test namespaces instead of growing the existing oversized signing suite.
- Close this active ExecPlan after the deterministic subaccount implementation and local browser proof are complete. Connected-wallet Hyperliquid parity and unrelated optimizer smoke health are not active subaccount implementation work and belong in a deferred plan until credentials or a separate optimizer-smoke ticket are available.

## Plan Of Work

### M1 - Protocol and Endpoint Foundation

Add endpoint support beside the existing account endpoint seams:

- Add `request-sub-accounts!` under `/hyperopen/src/hyperopen/api/endpoints/account/**`, likely alongside `agents.cljs` or a dedicated `subaccounts.cljs`.
- Normalize `null` to an empty vector for app state, while retaining tests that prove raw `null` is accepted.
- Normalize addresses to lowercase, preserve display names, and keep `master`, `sub-account-user`, `clearinghouse-state`, and `spot-state`.
- Expose the function through `/hyperopen/src/hyperopen/api/gateway/account.cljs`, `/hyperopen/src/hyperopen/api/default.account.cljs`, and `/hyperopen/src/hyperopen/api/default.cljs`.
- Add request-policy keys and dedupe keys scoped by owner address.

Add exchange action helpers:

- Add master-scoped L1 action helpers for `createSubAccount`, `subAccountModify`, `subAccountTransfer`, and `subAccountSpotTransfer`.
- Reuse `sign-and-post-agent-action!` for agent-signed L1 management actions, but pass explicit options so outer `vaultAddress` is nil.
- Verify whether user-wallet signing is needed or whether approved-agent signing is sufficient for management actions; default to the SDK-modeled L1 signer path and cover both happy and error responses.

Add protocol tests:

- Unit-test `subAccounts` request body and row normalization.
- Add `hl_signing` vectors for `vaultAddress` on order-like actions if not already covered.
- Add vectors from the Python SDK tests for `createSubAccount` and `subAccountTransfer`.
- Add simulator fixtures for `subAccounts`, `createSubAccount`, `subAccountModify`, and `subAccountTransfer`.

### M2 - Account Context and State Model

Introduce a small domain state shape:

```clojure
{:subaccounts
 {:status :idle
  :loaded-for-owner nil
  :rows []
  :error nil
  :selected-address nil
  :selection-loaded? false
  :creating? false
  :renaming-address nil
  :transferring-address nil}}
```

Extend selectors in `/hyperopen/src/hyperopen/account/context.cljs`:

- `selected-subaccount-address`
- `selected-subaccount-row`
- `selected-subaccount-owned-by-owner?`
- `active-trading-account-address` - owner or selected owned subaccount, never spectate/trader.
- `exchange-vault-address` - selected owned subaccount address, else nil.
- `effective-account-address` - read target with trader/spectate overrides first, then selected owned subaccount, then owner.
- `mutations-allowed?` and `mutations-blocked-message` - block read-only routes/modes, allow selected owned subaccounts, and block stale/unowned selected addresses.

Add actions/effects:

- Fetch subaccounts after wallet connect and on manual refresh.
- Clear subaccount rows and selection on wallet disconnect.
- Store last selected subaccount per owner in local storage using a versioned key. Validate against fetched rows before applying.
- When selection changes, reset active account surfaces and request account bootstrap for the selected actual address.
- Ensure router transitions to trader portfolio and Spectate Mode do not overwrite the selected writable subaccount preference.

Tests:

- Owner-only effective address remains unchanged.
- Selected owned subaccount becomes the effective read address and active trading address.
- Spectate and trader routes override selected subaccount for reads and still block mutations.
- Stale/unowned persisted selection is ignored.
- Wallet disconnect clears in-memory subaccount selection.

### M3 - Management Route and Selector UI

Add a route and navigation entry:

- Register `/subAccounts` with the app router and route modules.
- Build a page matching Hyperopen's operational UI style and Hyperliquid's main route semantics: `Master Account` table, `Sub-Accounts` table, create button, per-row rename and transfer actions.
- Show perps account value from `clearinghouseState.marginSummary.accountValue`, spot value as a summarized spot USDC/total display from `spotState.balances`, row address, and current selection state.
- Show eligibility/error messages from the exchange response for create failures, especially volume/count restrictions.

Add account selector:

- Put a compact account target selector near the wallet/trading controls in the header.
- Options are `Master account` plus fetched subaccounts.
- Show selected subaccount name/address in the account-context/banner area so users can tell they are trading a subaccount.
- Disable selector while Spectate Mode/trader portfolio route is controlling reads, but keep the selected writable target visible as the return target.

Add modals/forms:

- Create subaccount: name field, 1-16 character validation, submit `createSubAccount`.
- Rename subaccount: existing name, 1-16 character validation, submit `subAccountModify`.
- Perp USDC transfer: amount input, direction master-to-subaccount or subaccount-to-master, convert to raw 1e6 `usd`, submit `subAccountTransfer`.

Tests:

- VM tests for table rows, selected state, disabled state, form validation, amount conversion.
- Action tests for create/rename/transfer status transitions and refresh.

### M4 - Read-Side Account Data and Websocket Routing

Route all read-side account data to the selected actual account address:

- Audit all consumers that still read `[:wallet :address]` for account fetches and migrate them to `account-context/effective-account-address` or `active-trading-account-address`, depending on read-only semantics.
- Update account-surface bootstrap/reset to use the selected subaccount actual address.
- Update open orders, fills, historical orders, funding history, portfolio, user fees, spot clearinghouse, clearinghouse, user abstraction, and per-dex clearinghouse requests.
- Ensure request-policy dedupe keys include the selected actual account address.
- Ensure websocket `userEvents` and account-refresh subscriptions use `live-user-stream-address`.
- Guard stale async completions against the current effective account address, not only `[:wallet :address]`.

Tests:

- Account-surface service bootstraps selected subaccount rows.
- Open orders/fills/funding/portfolio requests send subaccount address.
- Websocket subscription changes from owner to selected subaccount and back.
- Stale owner fetch completions do not overwrite selected subaccount state, and vice versa.

### M5 - Trading Mutation Routing

Introduce a trading target object at mutation boundaries:

```clojure
{:owner-address "0xMASTER"
 :account-address "0xSELECTED_OR_MASTER"
 :vault-address "0xSUBACCOUNT_OR_NIL"}
```

Apply it to order and position effects:

- In `/hyperopen/src/hyperopen/order/effects.cljs`, compute the target from `account-context`.
- Call `trading-api/submit-order!`, `cancel-order!`, schedule cancel, TP/SL, isolated margin, leverage, and any modify/cancel-by-cloid paths with owner address plus `{:vault-address selected-subaccount}`.
- Refresh open orders, clearinghouse, spot clearinghouse, and account surfaces using `account-address`, not owner address.
- Update `active-wallet-address?` and stale guards to compare against the current active trading/effective account.
- Update optimizer-generated order submission paths if they bypass `order/effects.cljs`.
- Update spot-refresh code so post-outcome-order spot refresh uses selected account address.

Apply special transfer rules:

- For `usdClassTransfer`, when a subaccount is selected, append ` subaccount:<address>` to `amount` before signing and keep outer `vaultAddress` nil.
- For `sendAsset`, when a subaccount is selected, set `fromSubAccount` to the selected address and keep outer `vaultAddress` nil.
- Do not silently apply selected subaccount to staking, vault, bridge withdraw, or validator actions.

Tests:

- Order submit signs and posts with outer `vaultAddress` for selected subaccount.
- Cancel, schedule cancel, TP/SL, isolated margin, and leverage actions carry the same `vaultAddress`.
- Management actions create/rename/transfer do not carry outer `vaultAddress`.
- `usdClassTransfer` and `sendAsset` encode selected subaccount in action fields and do not send outer `vaultAddress`.
- After a successful subaccount order, account refreshes use the subaccount address.
- Existing master-account trading tests still pass with nil `vaultAddress`.

### M6 - Perp USDC Transfer and Refresh

Implement the V1 funding-management path:

- Use `subAccountTransfer` for master/subaccount perp USDC transfer.
- Convert user decimal input to integer micro-USDC with existing numeric parsing helpers; reject negative, zero, more-than-balance, NaN, and too many precision inputs.
- Direction `isDeposit true` means master to subaccount; `false` means subaccount to master.
- After success, refresh owner and selected subaccount subaccount rows, active account surfaces, and visible balances.
- Show error text from Hyperliquid for insufficient balance, ineligible account, jurisdiction, or count/volume constraints.

Tests:

- Amount parsing and micro-USDC conversion.
- Direction mapping.
- Refresh calls after success.
- Error propagation.

### M7 - Browser QA, Playwright, and Final Gates

Browser and UI work must follow `/hyperopen/docs/BROWSER_TESTING.md`.

Deterministic Playwright:

- Add a committed Playwright smoke flow that uses the simulator/debug bridge to:
  - connect a wallet,
  - load subaccounts,
  - select a subaccount,
  - verify account target UI changes,
  - submit and cancel a simulated order,
  - assert the captured exchange payload includes `vaultAddress`,
  - switch back to master and assert `vaultAddress` is absent.
- Run the smallest relevant command first: `npm run test:playwright:smoke -- --grep <subaccount test name>` if a focused grep is available, otherwise the exact targeted `playwright test` invocation for the new spec.
- Broaden to `npm run test:playwright:smoke` after the focused path passes.

Browser MCP exploratory parity:

- Use Browser MCP for a connected or testnet-compatible parity pass against `https://app.hyperliquid.xyz/subAccounts` only when credentials/environment allow it.
- Capture the observed create/rename/transfer UI details and network action bodies in a QA note if the flow becomes accessible.
- Stop Browser MCP sessions with `npm run browser:cleanup` before concluding.

Required repo gates for code changes:

- `npm run check`
- `npm test`
- `npm run test:websocket`
- Focused Playwright command for the new flow.
- `npm run test:playwright:smoke` after the focused browser test passes.

## Acceptance Criteria

- Connected master wallet can open `/subAccounts`, see master and subaccount rows, and refresh subaccount data from `/info {"type":"subAccounts"}`.
- A user can create a subaccount when eligible, see the new address/name returned or refreshed, and see clear Hyperliquid error text when ineligible.
- A user can rename an owned subaccount with 1-16 character validation.
- A user can transfer perp USDC between master and subaccount with correct 1e6 unit conversion and post-success refresh.
- A user can select an owned subaccount as the active account target and later switch back to master.
- Account surfaces, websocket stream, open orders, fills, portfolio, spot balances, and account history follow the selected actual account address.
- Trading through a selected subaccount signs with the owner/API wallet and posts order-like exchange actions with outer `vaultAddress` equal to the selected subaccount address.
- User-signed `usdClassTransfer` and `sendAsset` follow the documented subaccount-specific action-field encoding and do not use outer `vaultAddress`.
- Spectate Mode and trader portfolio routes remain read-only and cannot place trades or move funds, even if a writable subaccount is selected for normal routes.
- Staking, vault, bridge, and validator mutations are owner-only or blocked with a clear message while a subaccount is selected.
- Existing master-account trading behavior is unchanged.
- Required unit, check, websocket, focused Playwright, and subaccount smoke coverage pass for the selector/order-payload behavior. The full smoke suite was run and passed all subaccount checks; unrelated optimizer failures are documented in the deferred follow-up plan.

## Deferred Validation Gaps

- Exact successful response bodies for create, rename, perp transfer, and spot transfer on mainnet/testnet.
- Whether create failures expose distinct messages for volume, count, restricted jurisdiction, or unactivated accounts.
- Whether `subAccountTransfer` is strictly master-to-subaccount/subaccount-to-master or supports any broader routing.
- Exact accepted `token` string for `subAccountSpotTransfer`, especially USDC and non-USDC spot assets.
- Whether Hyperliquid requires API-wallet approval per subaccount in any current UI state, despite SDKs modeling master-approved agent plus `vaultAddress`.
- Whether restricted-jurisdiction checks are UI-only or affect API submission from the same environment.
- Whether every user-signed action rejects `expiresAfter` today, matching the official exchange docs.

## Outcomes & Retrospective

Implemented the core subaccount path: `/info` loading, selection persistence, route-local management UI, global header account selection, master-scoped create/rename/perp-transfer actions, selected-subaccount read routing, websocket desired-address routing, order/cancel/TP-SL/margin routing, optimizer execution routing, agent safety schedule-cancel routing, and simulator-backed browser proof that signed order-like payloads include `vaultAddress` only while a subaccount is selected.

Validation is strong for unit, integration, formal, runtime, websocket, focused browser, and subaccount smoke behavior. The implementation modestly increases UI and simulator complexity, but the added pieces are isolated: `hyperopen.views.header.account-selector` owns header selection presentation, and the debug exchange simulator records request payloads only when installed. Remaining live/testnet and unrelated smoke-health work has been moved to `/hyperopen/docs/exec-plans/deferred/2026-06-03-hyperliquid-subaccounts-live-parity-and-smoke-health.md` so this completed plan remains a historical implementation record rather than a generic backlog.
