# Restore immediate dex-scoped open-order refresh after order mutation

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, placing or cancelling an order on a named perpetual DEX should update the Open Orders tab without requiring a browser refresh. The user-visible proof is simple: submit a scale order on a named DEX, keep the Open Orders tab visible, and observe the new rows appear immediately after the submit succeeds. The same behavior should hold for order cancellation, with the affected rows disappearing promptly.

## Progress

- [x] (2026-03-11 14:37Z) Investigated the order-mutation refresh path, websocket subscription model, and Open Orders view data sources; identified the stale-data bug as a coverage mismatch between websocket `openOrders` assumptions and dex-scoped REST snapshots.
- [x] (2026-03-11 14:41Z) Updated `/hyperopen/src/hyperopen/account/surface_service.cljs` so successful order mutations still refresh named-DEX open-order snapshots even when the generic `openOrders` websocket stream is live.
- [x] (2026-03-11 14:44Z) Added regression coverage in `/hyperopen/test/hyperopen/websocket/account_surface_service_coverage_test.cljs` and updated the order-effect expectations in `/hyperopen/test/hyperopen/core_bootstrap/order_effects_test.cljs` to match the corrected contract.
- [x] (2026-03-11 14:48Z) Ran `npm run check`, `npm test`, and `npm run test:websocket` successfully on the final tree after installing locked Node dependencies with `npm ci`.

## Surprises & Discoveries

- Observation: The Open Orders tab is not sourced from a single websocket list. It merges `:orders :open-orders`, `:orders :open-orders-snapshot`, and `:orders :open-orders-snapshot-by-dex`, with named DEX rows coming from the per-dex snapshot map.
  Evidence: `/hyperopen/src/hyperopen/views/account_info/projections/orders.cljs` builds `open-orders-source` by concatenating live orders with `open-orders-snapshot-by-dex`.

- Observation: Successful order submits and cancels already trigger `account.surface-service/refresh-after-order-mutation!`; the stale UI is not caused by a missing post-submit hook.
  Evidence: `/hyperopen/src/hyperopen/order/effects.cljs` calls `refresh-account-surfaces-after-order-mutation!` on successful submit, partial-submit, and cancel paths.

- Observation: Hyperliquid’s `openOrders` websocket subscription is selector-shaped and can include a `dex` value, but the current user runtime subscribes only to `{type "openOrders" user <address>}`. The order-mutation refresh logic still treats that as full open-order coverage.
  Evidence: `/hyperopen/src/hyperopen/websocket/user_runtime/subscriptions.cljs` defines user subscriptions without any dex for `openOrders`, while `/hyperopen/src/hyperopen/account/surface_service.cljs` suppresses all `frontendOpenOrders` refreshes whenever `topic-usable-for-address?` reports generic `openOrders` coverage.

- Observation: The worktree did not have `node_modules`, so executable validation initially failed even though the ClojureScript compile succeeded.
  Evidence: `node out/test.js` failed with `Cannot find module '@noble/secp256k1'` until `npm ci` installed the lockfile-pinned packages.

## Decision Log

- Decision: Fix the bug by changing the post-mutation refresh contract instead of adding new websocket state ownership in this patch.
  Rationale: The immediate regression is caused by refresh suppression, and the existing view model already relies on REST-populated `:open-orders-snapshot-by-dex` for named DEX rows. Restoring that refresh path is the smallest change that matches current architecture boundaries and avoids introducing parallel websocket aggregation semantics mid-fix.
  Date/Author: 2026-03-11 / Codex

- Decision: Keep websocket subscription work out of scope for this fix unless the implementation uncovers a blocking defect.
  Rationale: Named-DEX websocket support would require new subscription ownership, health semantics, and state-shaping rules for multiple `openOrders` streams. That is larger than the reported bug and should only be pursued as separate work if the refresh fix proves insufficient.
  Date/Author: 2026-03-11 / Codex

- Decision: Scope the implementation to the order-mutation refresh path and update tests to codify that contract, rather than changing startup bootstrap or user-fill refresh behavior in the same patch.
  Rationale: The reported defect occurs immediately after successful order placement, and the order-mutation path is already the canonical follow-up hook for submit and cancel flows. Existing tests document different bootstrap and fill tradeoffs, so widening the fix would expand scope beyond the reported regression.
  Date/Author: 2026-03-11 / Codex

## Outcomes & Retrospective

The fix shipped by making `/hyperopen/src/hyperopen/account/surface_service.cljs` treat named-DEX open-order refreshes as a required post-mutation backstop even when the generic `openOrders` websocket stream is live. That preserves the current default-stream optimization while restoring the `:orders :open-orders-snapshot-by-dex` data that the Open Orders tab needs for named DEX rows.

Regression coverage now proves the corrected behavior in the websocket-live branch, and the order-effect tests confirm submit flows still dispatch order-history refreshes while issuing the expected named-DEX snapshot requests. Validation passed with `npm run check`, `npm test`, and `npm run test:websocket`.

This change reduced overall complexity slightly because it removed an incorrect assumption from the refresh policy instead of adding new websocket state ownership. The system still has one clear source for named-DEX open-order hydration after mutations: the existing `frontendOpenOrders` snapshot path.

## Context and Orientation

The affected code lives in four places.

`/hyperopen/src/hyperopen/order/effects.cljs` is the imperative order-mutation layer. It reacts to successful submit and cancel responses, shows toasts, and triggers follow-up account refreshes. That file does not decide which account surfaces to refresh; it delegates that policy.

`/hyperopen/src/hyperopen/account/surface_service.cljs` is the account-surface refresh policy and orchestration layer. It decides whether to refresh open orders, clearinghouse state, and per-dex account data after startup, fills, and order mutations. This file is the most likely fix location because it already centralizes websocket-versus-REST fallback decisions.

`/hyperopen/src/hyperopen/websocket/user_runtime/subscriptions.cljs` declares which user websocket topics are subscribed for the active address. It currently subscribes to `openOrders`, `userFills`, `userFundings`, and `userNonFundingLedgerUpdates` without any per-dex `openOrders` selectors.

`/hyperopen/src/hyperopen/views/account_info/projections/orders.cljs` shapes the Open Orders tab data. It merges a generic live order feed with snapshot fallback data and named-DEX snapshots. This matters because the screenshoted stale rows are dex-scoped, so a generic live stream alone is not enough to keep the tab current.

The current bug sequence is:

1. A named-DEX order is submitted successfully.
2. `/hyperopen/src/hyperopen/order/effects.cljs` calls `refresh-after-order-mutation!`.
3. `/hyperopen/src/hyperopen/account/surface_service.cljs` checks whether generic `openOrders` is usable for the address.
4. If that generic websocket topic is live, the service skips both the default `frontendOpenOrders` refresh and every per-dex `frontendOpenOrders` refresh.
5. The named-DEX rows in `:orders :open-orders-snapshot-by-dex` remain stale until startup/bootstrap runs again during a page refresh.

For this plan, “named DEX” means a non-default Hyperliquid perpetual DEX identified by a `dex` string such as `xyz` or `vault`. “Generic openOrders stream” means the websocket subscription keyed only by `type` and `user`, with no `dex` selector.

## Plan of Work

First, adjust `/hyperopen/src/hyperopen/account/surface_service.cljs` so order-mutation refreshes do not use the generic `openOrders` websocket health flag as a global reason to skip all open-order refreshes. The default open-orders refresh can still be skipped when the default stream is genuinely sufficient, but named-DEX `frontendOpenOrders` refreshes must continue to run because the current application state has no equivalent websocket-backed per-dex store.

Second, add regression coverage in `/hyperopen/test/hyperopen/websocket/account_surface_service_coverage_test.cljs`. The new tests should construct a websocket-live state where generic `openOrders` is healthy, call `refresh-after-order-mutation!`, and assert that the service still requests the per-dex open-order snapshots. If the implementation also changes default open-order behavior, the expected call list in the tests must document that explicitly.

Third, run the required repository gates and update this plan with the final results, including whether the change reduced or increased complexity. If validation reveals a broader websocket contract problem, record it in `Surprises & Discoveries` and file linked follow-up work in `bd`.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/account/surface_service.cljs` to separate generic open-order websocket coverage from named-DEX snapshot refresh decisions during `refresh-after-order-mutation!`.
2. Edit `/hyperopen/test/hyperopen/websocket/account_surface_service_coverage_test.cljs` to add the failing-before / passing-after regression scenario.
3. Run:

    npm run check
    npm test
    npm run test:websocket

4. If all commands succeed, move this file to `/hyperopen/docs/exec-plans/completed/` and close `hyperopen-bqr` with `bd close hyperopen-bqr --reason "Completed" --json`.

Observed evidence after the fix:

    In the websocket-live regression test, `open-orders-calls` includes named DEX refreshes such as:
    [address "dex-a" {:priority :low}]
    [address "dex-b" {:priority :low}]

    Validation commands completed successfully:
    npm run check
    npm test
    npm run test:websocket

## Validation and Acceptance

Acceptance is behavioral, not structural.

The fix is complete when all of the following are true:

1. A focused automated test proves that `refresh-after-order-mutation!` still triggers named-DEX `frontendOpenOrders` refreshes even when generic `openOrders` websocket health is live.
2. Existing order-effect tests continue to pass, proving the order submit and cancel flows still call the refresh service correctly.
3. `npm run check`, `npm test`, and `npm run test:websocket` pass from the repository root.
4. Manual reasoning against the data flow shows that the Open Orders tab’s named-DEX source (`:open-orders-snapshot-by-dex`) is rehydrated immediately after successful order mutation paths.

## Idempotence and Recovery

This change is safe to repeat because it only alters refresh-orchestration logic and tests. Re-running the test commands does not mutate persistent user data. If a code change causes duplicate open-order fetches, the safe recovery path is to revert only the `surface_service` adjustment and re-run the focused coverage test before expanding the patch again.

## Artifacts and Notes

Hyperliquid behavior embedded here for implementation context:

- The `frontendOpenOrders` info request accepts an optional `dex` selector and returns named-DEX rows that Hyperopen stores in `:orders :open-orders-snapshot-by-dex`.
- The websocket `openOrders` subscription also accepts a `dex` selector, but Hyperopen currently subscribes only to the address-scoped variant without a `dex`.
- Because the current state model does not keep websocket `openOrders` buckets by dex, suppressing per-dex REST refreshes after order mutation leaves the Open Orders tab stale.

## Interfaces and Dependencies

No public API should change.

The important function boundaries after implementation are:

- `/hyperopen/src/hyperopen/account/surface_service.cljs`
  `refresh-after-order-mutation!`
  This function must continue to delegate to `run-post-event-refresh!`, but the refresh policy it passes in must preserve named-DEX open-order hydration.

- `/hyperopen/src/hyperopen/order/effects.cljs`
  `refresh-account-surfaces-after-order-mutation!`
  This wrapper should remain unchanged unless validation proves the service contract itself is insufficient.

- `/hyperopen/test/hyperopen/websocket/account_surface_service_coverage_test.cljs`
  Add an assertion that protects the named-DEX open-order refresh behavior when websocket health is live.

Revision note: Updated on 2026-03-11 after implementation to record the narrower order-mutation-only fix scope, the successful validation commands, and the final outcome for `hyperopen-bqr`.
