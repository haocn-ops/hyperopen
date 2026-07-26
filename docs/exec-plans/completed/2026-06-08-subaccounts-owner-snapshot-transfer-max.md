# Subaccounts Owner Snapshot and Transfer Max Fix

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document follows `.agents/PLANS.md` and `docs/PLANS.md`.

## Purpose / Big Picture

A user reported that the Sub-Accounts page shows the same balance on the master account and the selected subaccount, then rejects a transfer with insufficient balance. After this change, the Sub-Accounts page must display the master account from a master-owned snapshot, not from the selected subaccount's active trading state. The transfer popover must calculate `MAX` from the actual source side for the current transfer direction, so a master-to-subaccount transfer cannot offer subaccount-owned funds as if the master owned them.

## Context References

Public refs:
- Direct user request on 2026-06-08: create an execution plan and implement the fix based on the subaccount balance/transfer root-cause trace.

Repo artifacts:
- `docs/exec-plans/completed/2026-06-03-subaccount-unified-sendasset-routing.md` records why unified subaccount transfers use `sendAsset` with `sourceDex: "spot"` and `destinationDex: "spot"` for the known unified spot transfer case.
- `docs/exec-plans/completed/2026-06-07-subaccounts-owner-mode-identity-scope.md` records why Sub-Accounts transfer routing must be governed by the displayed master account's mode, not the active selected subaccount mode.
- `docs/BROWSER_TESTING.md` and `docs/FRONTEND.md` define Playwright and browser-QA expectations for this UI-facing flow.

Local scratch refs, non-authoritative:
- Reviewer feedback pasted into `/Users/barry/.codex/attachments/7c88c5a5-19a9-48c8-9840-072ef4833904/pasted-text.txt`.

## Progress

- [x] (2026-06-08 12:56Z) Verified the root cause against code: `src/hyperopen/views/subaccounts_view.cljs` reads top-level `:webdata2` and `:spot` for the master row even when `effective-account-address` is the selected subaccount.
- [x] (2026-06-08 12:56Z) Chose a conservative implementation route: add an owner snapshot and source-specific max calculation, while preserving the known unified `spot -> spot` sendAsset path.
- [x] (2026-06-08 13:00Z) Added RED tests in `test/hyperopen/views/subaccounts_view_test.cljs`, `test/hyperopen/subaccounts/effects_test.cljs`, and `tools/playwright/test/subaccounts-regressions.spec.mjs`.
- [x] (2026-06-08 13:01Z) Confirmed RED with `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js hyperopen.views.subaccounts-view-test hyperopen.subaccounts.effects-test`; the runner ignored namespace args and ran the full suite. The expected new failures showed master duplication, equity-based max, enabled zero submit, and missing owner snapshot loading.
- [x] (2026-06-08 13:08Z) Implemented owner snapshot loading and projection under `[:account-context :subaccounts :owner-snapshot]`.
- [x] (2026-06-08 13:08Z) Updated the Sub-Accounts view to render the master row and deposit source max from the owner snapshot, and to render withdrawal max from the selected row source.
- [x] (2026-06-08 13:11Z) Reran `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js hyperopen.views.subaccounts-view-test hyperopen.subaccounts.effects-test`; the command exited 0 after expanding to the full suite.
- [x] (2026-06-08 13:14Z) Ran the focused Playwright regression with the build-aware web server command; `selected subaccount does not duplicate master balance` passed.
- [x] (2026-06-08 13:16Z) Ran the full Sub-Accounts Playwright regression file with the build-aware web server command; all 6 tests passed.
- [x] (2026-06-08 13:18Z) Initial `npm run check` rerun failed at `lint:namespace-sizes`; `test/hyperopen/subaccounts/actions_test.cljs` was 501 lines, `src/hyperopen/subaccounts/effects.cljs` needed a new owner exception, and `src/hyperopen/runtime/effect_adapters.cljs` needed its existing exception raised from 576 to 582 lines.
- [x] (2026-06-08 13:19Z) Trimmed the action test namespace to 500 lines, updated production namespace-size exceptions, and verified `npm run lint:namespace-sizes` passed.
- [x] (2026-06-08 13:20Z) Required gates passed: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-06-08 13:20Z) Browser cleanup and whitespace checks passed: `npm run browser:cleanup` stopped no sessions, and `git diff --check` exited 0.
- [x] (2026-06-08 13:20Z) Accounted for browser QA and moved this plan to `docs/exec-plans/completed/`.
- [x] (2026-06-08 13:22Z) After final test indentation cleanup, reran `npm run lint:delimiters -- --changed`, `git diff --check`, and `npm test`; all exited 0.

## Surprises & Discoveries

- Observation: The existing `unified-send-asset-action` using `sourceDex: "spot"` and `destinationDex: "spot"` is not accidental.
  Evidence: `docs/exec-plans/completed/2026-06-03-subaccount-unified-sendasset-routing.md` records a live Hyperliquid failure where unified subaccount transfers failed until they matched Hyperliquid's spot-to-spot sendAsset payload.

- Observation: The transfer popover currently uses account equity as a max fallback for trading transfers.
  Evidence: `src/hyperopen/views/subaccounts_view.cljs` derives `master-perps-value` from `marginSummary.accountValue` and passes it into `deposit-max`; this is an equity display value, not necessarily withdrawable collateral.

- Observation: The ClojureScript test runner does not honor namespace arguments passed to `node out/test.js`.
  Evidence: The RED command printed `Unknown arg: hyperopen.views.subaccounts-view-test` and `Unknown arg: hyperopen.subaccounts.effects-test`, then ran 4,284 tests and surfaced the expected new failures.

- Observation: The initial RED run was blocked by a missing local dependency tree.
  Evidence: `node out/test.js` failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'` until `npm ci` restored `node_modules`.

- Observation: The Playwright static server command in the original plan needed an explicit app build in this fresh worktree.
  Evidence: The static-only server timed out waiting for `/js/manifest.json` and `/js/main.js`; the build-aware command `npm run css:build && npx shadow-cljs --force-spawn compile app portfolio portfolio-worker portfolio-optimizer-worker && PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs` served the compiled app and passed the focused and full Sub-Accounts regression runs.

## Decision Log

- Decision: Store master display and source data in `[:account-context :subaccounts :owner-snapshot]` with the owner address, default clearinghouse state, and spot clearinghouse state.
  Rationale: The Sub-Accounts page can be viewed while a subaccount is selected for trading, so global account state belongs to the active selected account. An address-scoped owner snapshot prevents the master row and master transfer max from reading the wrong identity.
  Date/Author: 2026-06-08 / Codex.

- Decision: Preserve the existing unified subaccount `spot -> spot` sendAsset route for cases where the source balance is unified spot, and do not introduce broad named-DEX transfer routing in this plan.
  Rationale: The current report can be fixed by correcting identity and source max. Adding named-DEX source selection to the Sub-Accounts modal requires explicit source-dex UI/state that the current modal does not reliably carry.
  Date/Author: 2026-06-08 / Codex.

- Decision: Missing or mismatched owner snapshots should produce `--` for display values and `0` for source max, not fall back to active top-level account data.
  Rationale: A blank or disabled transfer is safer than offering the selected subaccount's funds as a master balance.
  Date/Author: 2026-06-08 / Codex.

## Outcomes & Retrospective

Implemented the owner snapshot and source-specific transfer max fix. The master row now renders from `[:account-context :subaccounts :owner-snapshot]`, which is loaded for the viewed master address, instead of top-level active-account `:webdata2` / `:spot` state. This prevents a selected subaccount's active balance from appearing as the master account balance.

The transfer popover now computes `MAX` from the actual source side for the selected direction. Classic trading sources use perps withdrawable, unified spot sources use spot USDC availability, and missing or mismatched source snapshots yield zero rather than falling back to the selected active account. The unified route keeps the previously validated `sendAsset` spot-to-spot payload, but its visible account label now says `Spot Account` instead of `Trading Account`.

Validation passed:

- `npm run lint:delimiters -- --changed`
- `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js hyperopen.views.subaccounts-view-test hyperopen.subaccounts.effects-test`
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='npm run css:build && npx shadow-cljs --force-spawn compile app portfolio portfolio-worker portfolio-optimizer-worker && PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/subaccounts-regressions.spec.mjs --grep "selected subaccount does not duplicate master balance"`
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='npm run css:build && npx shadow-cljs --force-spawn compile app portfolio portfolio-worker portfolio-optimizer-worker && PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/subaccounts-regressions.spec.mjs`
- `npm run lint:namespace-sizes`
- `npm run check`
- `npm test`
- `npm run test:websocket`
- `npm run browser:cleanup`
- `git diff --check`
- After final indentation cleanup: `npm run lint:delimiters -- --changed`, `git diff --check`, and `npm test`

Browser QA accounting: deterministic Playwright covered the Sub-Accounts data and transfer interactions, including the new selected-subaccount duplication regression and existing responsive popover coverage at 375, 768, 1280, and 1440 widths. A full manual/governed visual design review was not run because this was a data ownership and transfer-availability fix with only a small label correction.

Remaining risk: no live Hyperliquid transfer was submitted in this worktree. The tests verify the client state, max calculation, disabled zero-source behavior, and existing submit payload path.

## Context and Orientation

The Sub-Accounts page is rendered by `src/hyperopen/views/subaccounts_view.cljs`. It displays one master row and one row per subaccount returned by Hyperliquid's `subAccounts` info endpoint. The active trading account is resolved in `src/hyperopen/account/context.cljs`; when a user selects a subaccount for trading, `effective-account-address` becomes that subaccount address. Therefore top-level account paths such as `[:webdata2 :clearinghouseState]` and `[:spot :clearinghouse-state]` are active-account data, not always master data.

Subaccount loading and mutation side effects live in `src/hyperopen/subaccounts/effects.cljs`. Runtime wiring for those effects lives in `src/hyperopen/runtime/effect_adapters.cljs`. Existing loader dependencies already request subaccount rows and owner account mode. This plan adds owner clearinghouse and owner spot requests to the same boundary.

Transfer amount normalization lives in `src/hyperopen/subaccounts/transfer_amount.cljs`, while the transfer popover UI lives in `src/hyperopen/views/subaccounts_view/management.cljs` and `src/hyperopen/views/subaccounts_view/transfer_dropdowns.cljs`. Funding availability helpers in `src/hyperopen/funding/domain/availability.cljs` already know how to parse spot USDC availability and perps withdrawable values; this plan reuses those helpers instead of duplicating amount parsing in the view.

## Plan of Work

First, add RED tests. In `test/hyperopen/views/subaccounts_view_test.cljs`, add a state where `selected-address` is the subaccount, top-level `:webdata2` has the selected subaccount's `$2,002.20` equity, the row has the same subaccount equity, but `:owner-snapshot` has the master at zero. The test must prove the master row does not render `$2,002.20` and the default master-to-subaccount max is `0 USDC` with the submit button disabled.

Second, add an effects test in `test/hyperopen/subaccounts/effects_test.cljs` proving `load-subaccounts!` calls injected owner clearinghouse and spot request functions with the viewed master address and stores the result under `:owner-snapshot`.

Third, add a deterministic Playwright regression in `tools/playwright/test/subaccounts-regressions.spec.mjs` that seeds a selected subaccount, an active subaccount top-level balance, and a zero master owner snapshot. The test should assert that the master row does not duplicate the selected subaccount balance and the deposit submit is disabled at max zero.

Fourth, implement owner snapshot loading. In `src/hyperopen/subaccounts/actions.cljs` and `src/hyperopen/subaccounts/effects.cljs`, initialize and preserve `:owner-snapshot` with the displayed master address. In `src/hyperopen/runtime/effect_adapters.cljs`, inject `api/request-clearinghouse-state!` and `api/request-spot-clearinghouse-state!` into subaccount loading. In `src/hyperopen/subaccounts/effects.cljs`, fetch both owner states during route load, refresh, and post-transfer refresh, and apply them only if the displayed master still matches the response owner.

Fifth, implement source-specific max. In `src/hyperopen/views/subaccounts_view.cljs`, read the master row and deposit source from `:owner-snapshot` instead of global account state. Use `funding-availability/spot-usdc-available` for unified `spot -> spot` source balances and `funding-availability/perps-withdrawable` for classic trading source balances. Use `0` when the required snapshot is absent. Pass a boolean to the popover so `Send` is disabled when the selected source max is not positive.

Sixth, update labels for the unified spot route if needed so the UI does not call a spot-to-spot transfer a perps trading transfer. This should be a small view-only label change and must not change the existing unified sendAsset payload.

## Concrete Steps

Run all commands from `/Users/barry/.codex/worktrees/e864/hyperopen`.

1. Write the RED tests in the existing unit and Playwright files listed above.
2. Run `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js hyperopen.views.subaccounts-view-test hyperopen.subaccounts.effects-test`.
   Expected before implementation: the new assertions fail because the master row and max use active top-level account state and no owner snapshot is loaded.
3. Implement the owner snapshot loader and source-specific max changes.
4. Rerun `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js hyperopen.views.subaccounts-view-test hyperopen.subaccounts.effects-test`.
   Expected after implementation: the targeted unit namespaces pass.
5. Run the focused browser regression:
   `PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='npm run css:build && npx shadow-cljs --force-spawn compile app portfolio portfolio-worker portfolio-optimizer-worker && PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/subaccounts-regressions.spec.mjs --grep "selected subaccount does not duplicate master balance"`
   Expected after implementation: the focused Playwright test passes.
6. Run the broader Sub-Accounts Playwright file:
   `PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='npm run css:build && npx shadow-cljs --force-spawn compile app portfolio portfolio-worker portfolio-optimizer-worker && PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/subaccounts-regressions.spec.mjs`
   Expected after implementation: the file passes.
7. Run required gates: `npm run check`, `npm test`, and `npm run test:websocket`.
8. Because this is UI-facing work, account for the six browser-QA passes from `docs/FRONTEND.md`. If full governed design QA is too expensive for this narrow data/interaction fix, record the focused Playwright evidence and explicitly mark residual visual/design review as not run.

## Validation and Acceptance

Acceptance is that, when the selected header account is a subaccount, the Sub-Accounts page does not display that subaccount's active account data in the master row. A master with zero owner snapshot balance must render `$0.00` or `--`, not the subaccount's `$2,002.20`. Opening the default transfer popover for `Master Account -> Tenor` must show `MAX: 0 USDC` and must not allow `Send`.

For a classic master, a trading transfer max must use perps withdrawable from the source account, not `marginSummary.accountValue`. For a unified master, the existing sendAsset path remains spot-to-spot, so the source max must use source spot USDC availability. Missing source snapshots must yield max zero.

The new tests must fail before implementation and pass after implementation. The required repo gates must be run before completion.

## Idempotence and Recovery

All state changes are additive under `[:account-context :subaccounts]` and can be retried. If an owner snapshot request fails, the page should keep rows loaded, mark the snapshot error internally, and leave transfer max at zero rather than clearing the whole Sub-Accounts page. If Playwright leaves a local browser process running, run `npm run browser:cleanup`.

## Artifacts and Notes

The key root-cause excerpt before implementation is:

    src/hyperopen/views/subaccounts_view.cljs
    master-perps-value (account-value {:clearinghouse-state (get-in state [:webdata2 :clearinghouseState])})
    master-spot-state (get-in state [:spot :clearinghouse-state])

Those paths follow `effective-account-address`, which can be the selected subaccount, so they are not safe master sources.

## Interfaces and Dependencies

`[:account-context :subaccounts :owner-snapshot]` must support:

    nil
    {:owner normalized-owner-address
     :clearinghouse-state nil-or-clearinghouse-map
     :spot-state nil-or-spot-clearinghouse-map
     :loading? boolean
     :error nil-or-string}

`src/hyperopen/subaccounts/effects.cljs` must accept optional injected functions:

    :request-owner-clearinghouse-state!
    :request-owner-spot-state!

Each function receives the normalized owner address and request options, and returns a promise resolving to the raw Hyperliquid response map. Runtime wiring in `src/hyperopen/runtime/effect_adapters.cljs` must pass `api/request-clearinghouse-state!` for the default clearinghouse and `api/request-spot-clearinghouse-state!` for spot.

The Sub-Accounts view must continue using existing data-role anchors so the deterministic Playwright test remains reviewable and CI-safe.

## Revision Notes

- 2026-06-08 12:56Z: Created the plan from the user report, code trace, and reviewer feedback. The plan intentionally fixes identity/source max first and avoids adding broader named-DEX transfer UI without explicit source-dex state.
- 2026-06-08 13:01Z: Updated progress and discoveries after RED validation. The test runner expanded targeted namespace args to the full suite, but the failing assertions matched the intended regression.
