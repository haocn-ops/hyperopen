# Subaccounts Route Refresh Stale Loader

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. Keep it self-contained, update it whenever implementation discoveries change the plan, and leave at least one unchecked progress item while the plan remains active.

## Purpose / Big Picture

A user reported that transferring to spot now works, but the `/subAccounts` page stays on "Loading subaccounts..." and cannot expose the owned subaccount row needed to transfer funds back to the master account. After this change, opening `/subAccounts` for a connected master wallet that owns subaccounts should show the existing rows, and pressing Refresh should force a fresh `subAccounts` request without blanking the table or rejoining a stale normal-load request. The visible proof is that the page can show the Tenor row after load, keep it visible while refreshing, and still open the row transfer control for moving funds between the subaccount and master.

## Context References

Public refs:

- Direct user request on 2026-06-07: investigate a suspected subaccount regression where the page stays at "Loading subaccounts..." after a successful funding transfer.

Repo artifacts:

- `/hyperopen/AGENTS.md` requires an ExecPlan for risky bug and UI work and requires `npm run check`, `npm test`, and `npm run test:websocket` when code changes.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define active ExecPlan requirements.
- `/hyperopen/docs/BROWSER_TESTING.md` governs browser-tool routing and Playwright validation.

Local scratch refs, non-authoritative:

- `/Users/barry/.codex/attachments/310ed74f-23ac-495c-8e71-953634708200/pasted-text.txt` contains the maintainer-provided report and external analysis. The important local facts to preserve in this plan are that the screenshot shows the master table rendered, the subaccount table still says "Loading subaccounts...", and the Refresh button label is "Refreshing...".

## Progress

- [x] (2026-06-07 19:53Z) Traced the current route, view, effect, and info-client code paths and found that the Refresh button dispatches the route-enter action, which destructively sets status to `:loading`, clears rows, and emits the normal cached/single-flight `:effects/api-load-subaccounts`.
- [x] (2026-06-07 19:53Z) Confirmed the current local worktree already has uncommitted spectate-mode subaccounts changes. These changes compile and test, but they do not address the refresh/idempotence root cause described here.
- [x] (2026-06-07 19:54Z) Created this active ExecPlan and checked formatting with `git diff --check`. `npm run lint:docs` is currently blocked by an unrelated stale-doc guardrail on `docs/DESIGN.md`.
- [ ] Add RED unit tests for idempotent route enter, force refresh, and stale-row rendering while loading.
- [ ] Implement the minimal action/effect/runtime registration changes for a separate force-refresh path.
- [ ] Update the `/subAccounts` view so Refresh dispatches the new refresh action and loaded rows stay visible during refresh.
- [ ] Add deterministic Playwright regression coverage for the stuck-loader/refresh scenario.
- [ ] Run the required validation gates and record evidence in this plan.

## Surprises & Discoveries

- Observation: The current local checkout is dirty before this plan was created, with changes in `src/hyperopen/subaccounts/**`, `src/hyperopen/views/subaccounts_view**`, `test/hyperopen/subaccounts/**`, `test/hyperopen/views/subaccounts_view_test.cljs`, and `tools/playwright/test/subaccounts-regressions.spec.mjs`.
  Evidence: `git status --short --branch` showed those files modified. The existing diff introduces `viewed-master-address` and spectate-mode read-only behavior. Do not revert or overwrite those changes while implementing this plan.

- Observation: The local test command accepted namespace arguments syntactically but the generated runner ignored them and ran the full CLJS suite.
  Evidence: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js hyperopen.subaccounts.actions-test hyperopen.subaccounts.effects-test hyperopen.views.subaccounts-view-test hyperopen.startup.route-refresh-test` printed `Unknown arg: ...` for each namespace, then ran 4,257 tests containing 23,540 assertions with 0 failures and 0 errors.

- Observation: Docs lint is not yet green after adding this plan because of a pre-existing stale-doc check outside this task.
  Evidence: `npm run lint:docs` failed with `[stale-doc] docs/DESIGN.md - document is stale: 91 days old, max allowed 90`.

- Observation: `src/hyperopen/subaccounts/effects.cljs` already has a private `refresh-subaccounts!` helper that passes `:force-refresh? true` to `load-subaccounts!`, and `request-opts` already creates unique `:dedupe-key` and `:cache-key` values for force refreshes. That path is used after create, rename, and transfer mutations, but the page Refresh button cannot call it.
  Evidence: `refresh-subaccounts!` is private in `src/hyperopen/subaccounts/effects.cljs`, while `src/hyperopen/app/effects.cljs` only registers `:effects/api-load-subaccounts`, `:effects/api-create-subaccount`, `:effects/api-rename-subaccount`, and `:effects/api-transfer-subaccount`.

- Observation: Normal subaccount loads use request policy TTL and single-flight dedupe. This is correct for route/bootstrap load, but wrong for an explicit user Refresh after the page is stuck in loading.
  Evidence: `src/hyperopen/api/endpoints/account/subaccounts.cljs` applies `:dedupe-key [:sub-accounts requested-address]`; `src/hyperopen/api/request_policy.cljs` gives `:sub-accounts` a 5000 ms default TTL; `src/hyperopen/api/info_client/flow.cljs` returns the existing in-flight promise for the same flight key unless `:force-refresh? true` is set.

## Decision Log

- Decision: Treat the stuck loader as a route/refresh lifecycle bug, not as proof that Hyperliquid returned no subaccounts.
  Rationale: The view renders "No subaccounts found for this master account." only after `:status` is not `:loading` and rows are empty. The screenshot shows "Loading subaccounts..." and "Refreshing...", so the state is still `:loading`.
  Date/Author: 2026-06-07 / Codex

- Decision: Keep route entry and explicit Refresh as separate actions.
  Rationale: Route entry should establish initial page state and load missing data, but an explicit user Refresh must bypass cached/single-flight normal load and must not clear visible rows. Reusing the route-enter action for Refresh is the root cause of the stale loader behavior.
  Date/Author: 2026-06-07 / Codex

- Decision: Do not use `effective-account-address` to choose the parent queried by the subaccounts page.
  Rationale: `/subAccounts` lists children owned by a master account. If a selected subaccount is the effective trading account, querying that selected subaccount as the parent would be wrong. Use the currently introduced `viewed-master-address` helper if it remains in the branch; otherwise use a helper with the same master-view semantics.
  Date/Author: 2026-06-07 / Codex

## Outcomes & Retrospective

No implementation has started under this plan yet. The intended outcome is a smaller and more reliable subaccounts loader: route entry becomes idempotent for the same master, explicit Refresh becomes a force-refresh path, and the table keeps already-loaded rows visible while refreshing.

## Context and Orientation

The `/subAccounts` page is a ClojureScript UI for managing Hyperliquid subaccounts. A master account owns subaccounts. The page should query Hyperliquid `/info` with request type `subAccounts` and the master address, then render rows with subaccount names, addresses, perps value, spot value, and actions.

The current route loader starts in `src/hyperopen/subaccounts/actions.cljs`. `load-subaccounts-route` checks whether the path is `/subAccounts`, computes a master/owner address, saves route-local loading state through `load-route-path-values`, and emits `[:effects/api-load-subaccounts]`. `load-route-path-values` currently sets `[:account-context :subaccounts :status]` to `:loading`, sets `:loaded-for-owner`, clears `:rows` to `[]`, clears `:error`, and resets form fields. This is useful for a cold route load, but destructive when repeated for the same already-loaded master.

The view is in `src/hyperopen/views/subaccounts_view.cljs`. The Refresh button currently dispatches `[:actions/load-subaccounts-route subaccounts-actions/canonical-route]`. The subaccounts table currently checks loading status before rows, so when status is `:loading` it renders a single "Loading subaccounts..." row even if stale rows exist in state.

The network effect is in `src/hyperopen/subaccounts/effects.cljs`. `api-load-subaccounts!` calls `load-subaccounts!` with `:force-refresh? false`. A private `refresh-subaccounts!` already calls `load-subaccounts!` with `:force-refresh? true`, but it is only reachable after successful create, rename, and transfer operations. The request endpoint in `src/hyperopen/api/endpoints/account/subaccounts.cljs` uses a stable normal-load dedupe key for `[:sub-accounts requested-address]`. The info-client in `src/hyperopen/api/info_client/flow.cljs` shares a pending request for the same key, and only `:force-refresh? true` bypasses this single-flight path.

Runtime registration is split across several files. Add new action/effect IDs consistently in `src/hyperopen/app/actions.cljs`, `src/hyperopen/app/effects.cljs`, `src/hyperopen/runtime/effect_adapters.cljs`, `src/hyperopen/schema/runtime_registration/subaccounts.cljs`, `src/hyperopen/schema/contracts/action_args.cljs`, `src/hyperopen/schema/contracts/effect_args.cljs`, and `src/hyperopen/runtime/effect_order_contract.cljs`. If effect-order formal vectors are generated from the contract surface, update them with the repo's formal sync command instead of hand-editing generated vector data.

## Plan of Work

First, add failing tests around the actual regression. In `test/hyperopen/subaccounts/actions_test.cljs`, add a test showing that calling `load-subaccounts-route` for the same master while `:status` is `:loaded` or `:loading` and rows already exist does not clear rows and does not emit another normal `:effects/api-load-subaccounts`. Add a second test showing that an owner/master change still clears rows and emits the normal load. Add a test for a new `refresh-subaccounts` action that sets only `:status` to `:loading`, clears `:error`, preserves `:rows`, and emits `[:effects/api-refresh-subaccounts]`.

Next, add effect tests in `test/hyperopen/subaccounts/effects_test.cljs` or the closest existing runtime effect-adapter suite. Assert that the new `api-refresh-subaccounts-effect` reaches `load-subaccounts!` with `:force-refresh? true`. Also add or extend endpoint/effect tests to prove force refresh produces unique subaccount request keys via the existing `request-opts` behavior. If `request-opts` remains private, test this through `load-subaccounts!` by passing a fake `request-sub-accounts!` that records its `opts`.

Then update `src/hyperopen/subaccounts/actions.cljs`. Keep the existing cold-load behavior when there is no valid master in state or the route is not `/subAccounts`. For a valid master, read the current `[:account-context :subaccounts]` state. If `:loaded-for-owner` matches the master and `:status` is either `:loading` or `:loaded`, return `[]` so route re-entry is idempotent and cannot blank the table. If the master differs or state is idle/error for that master, continue to save cold-load state and emit `[:effects/api-load-subaccounts]`. Add a public `refresh-subaccounts` action that computes the same master address, preserves rows, sets `:status` to `:loading`, clears `:error`, and emits `[:effects/api-refresh-subaccounts]` when a master address exists.

Then update `src/hyperopen/subaccounts/effects.cljs` and `src/hyperopen/runtime/effect_adapters.cljs`. Add a public `api-refresh-subaccounts!` wrapper that calls `load-subaccounts!` with `:force-refresh? true`, or expose the existing private helper through a public wrapper. Add `api-refresh-subaccounts-effect` in `effect_adapters.cljs` using `subaccounts-load-deps`. Keep the existing private `refresh-subaccounts!` for post-mutation reloads if it still simplifies create, rename, and transfer code, but make sure the user Refresh path reaches the same force-refresh semantics.

Then update runtime registration. In `src/hyperopen/app/actions.cljs`, register `:refresh-subaccounts` under `:subaccounts`. In `src/hyperopen/app/effects.cljs`, register `:api-refresh-subaccounts` under `:subaccounts`. In `src/hyperopen/schema/runtime_registration/subaccounts.cljs`, add the new effect binding row, action binding row, and effect-order required action ID. In `src/hyperopen/schema/contracts/action_args.cljs`, add `:actions/refresh-subaccounts` with no args. In `src/hyperopen/schema/contracts/effect_args.cljs`, add `:effects/api-refresh-subaccounts` with no args. In `src/hyperopen/runtime/effect_order_contract.cljs`, add a policy for `:actions/refresh-subaccounts` with `:effects/api-refresh-subaccounts` as the heavy effect and the same phase-order requirements as `:actions/load-subaccounts-route`.

Then update `src/hyperopen/views/subaccounts_view.cljs`. Change the Refresh button to dispatch `[:actions/refresh-subaccounts]`. Keep the button enabled whenever a master address exists, even in read-only spectate mode, because refreshing read-only data is safe. Update the subaccount table rendering so already-loaded rows remain visible while `:status` is `:loading`; only show the "Loading subaccounts..." empty row when status is `:loading` and there are no rows. Add a small refresh indicator near the table heading or preserve the existing button label "Refreshing..." so the user can see that a background refresh is in progress without losing the row/action surface.

Then add view and browser coverage. In `test/hyperopen/views/subaccounts_view_test.cljs`, assert that the Refresh button emits `[:actions/refresh-subaccounts]`. Add a test where `:status` is `:loading` but rows contain a subaccount, and assert that the subaccount row remains visible and "Loading subaccounts..." is not the only body content. In `tools/playwright/test/subaccounts-regressions.spec.mjs`, add a regression where the route loads a subaccount, the test delays a subsequent `subAccounts` refresh response, the user clicks Refresh, and the row plus Transfer trigger remain visible while the button shows "Refreshing...". After resolving the delayed response, assert the refreshed row remains visible.

Finally, update generated/formal artifacts if required. Run `bb tools/formal.clj sync --surface effect-order-contract` after editing `src/hyperopen/runtime/effect_order_contract.cljs` if the conformance test reports vector drift. Do not hand-edit `test/hyperopen/formal/effect_order_contract_vectors.cljs` unless the formal tooling is unavailable, and record any tooling issue here.

## Concrete Steps

From `/Users/barry/projects/hyperopen`, before editing implementation files, inspect the current dirty diff so you do not overwrite unrelated local changes:

    git status --short --branch
    git diff -- src/hyperopen/subaccounts/actions.cljs src/hyperopen/subaccounts/effects.cljs src/hyperopen/subaccounts/management.cljs src/hyperopen/views/subaccounts_view.cljs src/hyperopen/views/subaccounts_view/management.cljs test/hyperopen/subaccounts/actions_test.cljs test/hyperopen/subaccounts/effects_test.cljs test/hyperopen/views/subaccounts_view_test.cljs tools/playwright/test/subaccounts-regressions.spec.mjs

Write the RED unit tests first. Then run:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js

Expected before implementation: the new action/view/effect assertions fail. The generated runner currently ignores namespace arguments, so expect it to run the whole generated CLJS suite unless the runner is changed.

Implement the action, effect, registration, and view changes described above. Re-run:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js

Expected after implementation: all CLJS tests pass, including the new subaccounts route refresh tests.

Run the smallest relevant Playwright command first. If the existing Playwright config supports file targeting, run:

    npx playwright test tools/playwright/test/subaccounts-regressions.spec.mjs --grep "subaccounts"

Expected after implementation: the new refresh regression passes, and existing subaccount regression tests still pass.

If the effect-order formal vectors drift after adding `:actions/refresh-subaccounts`, run:

    bb tools/formal.clj sync --surface effect-order-contract

Expected after sync: the committed vector file matches the updated runtime contract.

For final repo validation after code changes, run the required AGENTS gates:

    npm run check
    npm test
    npm run test:websocket

Expected after implementation: all three commands pass. If an unrelated active-plan or stale-doc guardrail fails, record the exact failure here and do not hide it with unrelated metadata edits unless the user approves.

## Validation and Acceptance

Unit acceptance is that `load-subaccounts-route` is idempotent for the same master when rows are already loading or loaded, `refresh-subaccounts` preserves rows while emitting a force-refresh effect, and `api-refresh-subaccounts-effect` bypasses normal single-flight/cached load semantics.

View acceptance is that the Refresh button dispatches `[:actions/refresh-subaccounts]`, rows stay visible while `:status` is `:loading`, and a cold load with no rows still renders "Loading subaccounts..." so first-load behavior remains clear.

Browser acceptance is that a deterministic Playwright scenario can load `/subAccounts`, see a subaccount row, click Refresh while the network response is delayed, and still see the row and Transfer control until the delayed response settles. This directly covers the user's reported inability to reach the subaccount transfer control after the page says "Refreshing...".

Protocol acceptance is that the page still queries the master/viewed-master address for `subAccounts`; it must not query the selected subaccount as the parent merely because that subaccount is the current trading account. Signing and actual transfer payload changes are out of scope for this plan unless tests expose a direct regression in the row transfer action.

## Idempotence and Recovery

The route action changes are idempotent by design: calling the route loader repeatedly for the same master while data is loading or loaded should produce no state reset and no duplicate normal network request. Explicit Refresh remains repeatable because it uses force-refresh request options and preserves visible rows.

If implementation breaks route entry for a new wallet, revert only the new route-idempotence logic and keep unrelated dirty spectate-mode changes intact. If the Playwright regression is flaky due to network interception, replace timing waits with a controlled deferred route fulfillment so the test explicitly controls when the refresh request resolves.

## Artifacts and Notes

Root-cause source evidence from current checkout:

    src/hyperopen/subaccounts/actions.cljs:
      load-route-path-values sets status to :loading and rows to [].
      load-subaccounts-route always applies load-route-path-values and emits :effects/api-load-subaccounts for an active /subAccounts route with a master address.

    src/hyperopen/views/subaccounts_view.cljs:
      the Refresh button dispatches [:actions/load-subaccounts-route subaccounts-actions/canonical-route].
      subaccounts-section renders "Loading subaccounts..." whenever status is :loading, before considering existing rows.

    src/hyperopen/subaccounts/effects.cljs:
      api-load-subaccounts! calls load-subaccounts! with :force-refresh? false.
      refresh-subaccounts! calls load-subaccounts! with :force-refresh? true, but it is private and only used after management mutations.

    src/hyperopen/api/endpoints/account/subaccounts.cljs:
      request-sub-accounts! sends {"type" "subAccounts", "user" requested-address} with normal dedupe-key [:sub-accounts requested-address].

    src/hyperopen/api/info_client/flow.cljs:
      with-single-flight! returns the existing promise for a matching flight key.
      request-info-with-flow! bypasses single-flight only when :force-refresh? is true.

Investigation command already run:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js hyperopen.subaccounts.actions-test hyperopen.subaccounts.effects-test hyperopen.views.subaccounts-view-test hyperopen.startup.route-refresh-test

Observed output:

    Unknown arg: hyperopen.subaccounts.actions-test
    Unknown arg: hyperopen.subaccounts.effects-test
    Unknown arg: hyperopen.views.subaccounts-view-test
    Unknown arg: hyperopen.startup.route-refresh-test
    Generated test/test_runner_generated.cljs with 683 namespaces.
    Ran 4257 tests containing 23540 assertions.
    0 failures, 0 errors.

## Interfaces and Dependencies

New action ID:

    :actions/refresh-subaccounts

It takes no arguments. It reads the master/viewed-master address from app state, preserves existing `[:account-context :subaccounts :rows]`, sets `[:account-context :subaccounts :status]` to `:loading`, clears `[:account-context :subaccounts :error]`, and emits `[:effects/api-refresh-subaccounts]` when there is a master/viewed-master address.

New effect ID:

    :effects/api-refresh-subaccounts

It takes no arguments. Its runtime adapter calls `subaccounts-effects/api-refresh-subaccounts!` or `subaccounts-effects/load-subaccounts!` with `:force-refresh? true` and the existing `subaccounts-load-deps`.

Existing effect to preserve:

    :effects/api-load-subaccounts

It remains the cold/bootstrap load path and should continue to use `:force-refresh? false` for normal route entry and global header state refresh.

Required user-visible behavior:

    Cold /subAccounts load with no rows: show "Loading subaccounts..." until the first response settles.
    Refresh with existing rows: keep rows and row actions visible, show "Refreshing..." or equivalent progress, and bypass stale normal-load single-flight/cache state.
    Same-master route re-entry: do not clear rows and do not dispatch another normal load when status is already :loading or :loaded for that master.

Revision note, 2026-06-07 / Codex: Created this active ExecPlan after source tracing the reported stuck subaccounts page. The plan separates the loader/refresh root cause from pre-existing uncommitted spectate-mode changes in the same files.
