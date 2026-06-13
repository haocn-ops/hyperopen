# Split Account Surfaces By Tab Capability

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. It is self-contained so a future contributor can continue from this file without relying on conversation history.

## Context References

Public refs:
- Direct maintainer request captured in this ExecPlan. No GitHub issue or PR is linked in this checkout.

Repo artifacts:
- Parent root-bundle split: `/Users/barry/.codex/worktrees/662c/hyperopen/docs/exec-plans/completed/2026-04-20-root-bundle-follow-up-surfaces-and-runtime-aggregators.md`

## Purpose / Big Picture

`/trade` already lazy-loads `:account_surfaces`, but `src/hyperopen/views/account_info_view.cljs` still eagerly requires every account tab namespace. That means the cold trade route still pays for balances, positions, outcomes, open orders, order history, TWAP, trade history, and funding history even though only one tab renders at a time. The current production audit evidence recorded the resulting `account_surfaces` startup chunk at roughly `306 KB` raw on cold `/trade`.

After this change, the account-surface shell should stay eager only for the default balances tab plus shared shell/view-model code. Non-default tabs should move behind lazy capability modules so cold `/trade` no longer transfers and evaluates their render code by default. The measurable proof is a before/after release build comparison for the relevant account chunks, plus the required repository gates and at least one deterministic browser smoke covering the trade shell.

## Progress

- [x] (2026-06-10 03:14Z) Read the current account-surface loader, account info shell, startup/navigation paths, and existing bundle-audit evidence.
- [x] (2026-06-10 03:17Z) Confirmed the audit precondition by running `npm ci` before further execution in this worktree.
- [x] (2026-06-10 04:41Z) Added RED/green coverage for account-tab lazy loads in action, startup, navigation, and account-info rendering paths, including test helpers for synchronously rendering lazy tab content in the test bundle.
- [x] (2026-06-10 04:41Z) Implemented grouped lazy account-tab modules, eager-shell refactors, loader state/effects, and route/action wiring for lazy tab requests.
- [x] (2026-06-10 05:26Z) Ran `npm test`, `npm run test:websocket`, the targeted Playwright trade regression, and a release build plus chunk measurement. `npm run check` still fails on an unrelated stale-doc lint in `docs/product-specs/chart-hover-navigation-controls-prd.md`.

## Surprises & Discoveries

- Observation: The eager seam is broader than `account_info_view.cljs` alone because `src/hyperopen/views/account_info/tab_actions.cljs` also requires heavy tab namespaces just to read filter labels and normalization helpers.
  Evidence: `tab_actions.cljs` currently requires `tabs.open-orders`, `tabs.order-history`, `tabs.positions`, and `tabs.trade-history`.

- Observation: Some non-default tabs currently depend on each other, which would cause Shadow CLJS to hoist shared code back into the common ancestor chunk if every tab became its own sibling module.
  Evidence: `tabs/twap.cljs` requires `tabs.trade-history`, `tabs/order_history.cljs` requires `tabs.open-orders`, and `tabs/outcomes.cljs` requires `tabs.positions.shared`.

- Observation: Most of the helper re-exports in `account_info_view.cljs` are not used outside that namespace’s own tests, so the shell can shed those eager tab aliases without breaking production callers.
  Evidence: repo-wide `rg -n "account-info-view/" src test` only found production callers for `account-info-view`, `account-info-panel`, and `available-tabs`, plus tests stubbing `account-info-view/account-info-view`.

## Decision Log

- Decision: Split non-default account tabs by capability groups rather than one module per tab.
  Rationale: Cross-tab dependencies between TWAP/trade history, order history/open orders, and outcomes/positions would otherwise hoist shared code back into the eager `:account_surfaces` ancestor. Capability-group modules keep those shared helpers lazy.
  Date/Author: 2026-06-10 / Codex

- Decision: Keep balances eager inside `:account_surfaces`.
  Rationale: Balances is the default account tab, so making it lazy would trade initial bundle bytes for a user-visible first-click/first-paint regression on the normal `/trade` path.
  Date/Author: 2026-06-10 / Codex

- Decision: Keep test-only lazy renderer shims outside the production module entry namespaces.
  Rationale: Requiring the production lazy module entry namespaces inside the test bundle made their exported renderers appear globally preloaded, which invalidated startup/navigation lazy-load assertions. Test-only shims preserve the production loading contract while still letting synchronous view tests render lazy tabs.
  Date/Author: 2026-06-10 / Codex

## Context and Orientation

The working directory is `/Users/barry/.codex/worktrees/662c/hyperopen`.

The current shared account surface entry is `src/hyperopen/views/account_surfaces_module.cljs`, loaded by `src/hyperopen/surface_modules.cljs` under the Shadow module name `account_surfaces`. `src/hyperopen/views/trade_view.cljs` resolves that module’s named exports for account info and account equity rendering. `src/hyperopen/app/startup.cljs` and `src/hyperopen/runtime/action_adapters/navigation.cljs` currently lazy-load only the whole `:account-surfaces` module, not individual account-tab renderers.

The tab shell is `src/hyperopen/views/account_info_view.cljs`. It currently requires all tab namespaces at the top of the file and builds a direct `tab-renderers` map from those requires. `src/hyperopen/views/account_info/tab_actions.cljs` also requires several heavy tab namespaces for filter metadata, so shrinking the eager shell requires a second seam that moves those small shared tab-control contracts into lighter owners.

## Plan of Work

First, add RED coverage. Extend the focused tests around startup, navigation, and account-history actions so they prove lazy account-tab module effects are dispatched only for non-default tabs. Add account-info view tests that prove balances still renders eagerly while unresolved lazy tabs show a loading shell instead of requiring the heavy renderer namespace.

Second, add a dedicated lazy account-tab loader, separate from `surface_modules`, because the account tabs have different readiness and concurrency needs than the existing one-at-a-time modal/account-surface loader. The loader should map tab ids to grouped Shadow module names, resolve exported tab-renderer functions from loaded modules, and track loaded/loading/error state under a dedicated `:account-tab-modules` branch in app state.

Third, split the heavy tab renderers into grouped capability entry modules:

- positions plus outcomes
- open orders plus order history
- trade history plus TWAP
- funding history

Each entry namespace should export stable global renderer functions that accept the existing account-info view-model map. The eager shell should keep only balances inline.

Fourth, move lightweight tab-control metadata out of heavy tab namespaces where needed so the eager tab strip does not pull the lazy renderers back in. That includes the direction/status filter options and filter-key normalization used by `tab_actions.cljs`, plus any small shared helpers that currently force cross-tab requires between lazy groups.

Fifth, update startup/navigation/action paths. Selecting a non-default tab should enqueue `[:effects/load-account-tab-module <tab>]` after saving the new selected tab. Entering `/trade` or another route that initially renders a built-in lazy account tab should also enqueue the module load when that tab is visible from state.

## Concrete Steps

1. Write RED tests in the existing focused surfaces:

       test/hyperopen/core_bootstrap/account_history_actions_test.cljs
       test/hyperopen/app/startup_test.cljs
       test/hyperopen/runtime/action_adapters/navigation_test.cljs
       test/hyperopen/views/account_info_view_test.cljs

2. Run the focused compile/test loop and confirm the new contracts fail first:

       npm run test:runner:generate
       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.core-bootstrap.account-history-actions-test --test=hyperopen.app.startup-test --test=hyperopen.runtime.action-adapters.navigation-test --test=hyperopen.views.account-info-view-test

3. Add the lazy account-tab loader, state defaults, effect handler wiring, and module entries in:

       src/hyperopen/account_tab_modules.cljs
       src/hyperopen/app/effects.cljs
       src/hyperopen/runtime/effect_adapters.cljs
       src/hyperopen/schema/runtime_registration/trade.cljs
       src/hyperopen/state/app_defaults.cljs
       shadow-cljs.edn

4. Refactor the eager shell and tab-strip metadata seams in:

       src/hyperopen/views/account_info_view.cljs
       src/hyperopen/views/account_info/tab_actions.cljs
       new lightweight tab metadata/helper namespaces as needed
       new grouped account-tab module entry namespaces under `src/hyperopen/views/`

5. Re-run the focused loop from step 2 until it passes.

6. Compile the app and run the required validation gates:

       npm run check
       npm test
       npm run test:websocket

7. Because this changes browser-visible lazy-loading on `/trade`, run the smallest relevant deterministic Playwright smoke first:

       npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "desktop trade shell keeps the chart dominant while account tabs stay geometry-stable"

8. Run a release build and record the relevant chunk sizes before closing the plan:

       npm run build
       node - <<'NODE'
       const fs = require('fs');
       const zlib = require('zlib');
       const files = fs.readdirSync('out/release-public/js')
         .filter((file) => file.endsWith('.js'))
         .sort();
       for (const file of files) {
         const path = 'out/release-public/js/' + file;
         const bytes = fs.readFileSync(path);
         const gzip = zlib.gzipSync(bytes, {level: 9});
         const brotli = zlib.brotliCompressSync(bytes, {
           params: {[zlib.constants.BROTLI_PARAM_QUALITY]: 11}
         });
         console.log(`${file}\traw=${bytes.length}\tgzip=${gzip.length}\tbrotli=${brotli.length}`);
       }
       NODE

## Validation and Acceptance

This work is accepted when all of these are true:

1. The eager `:account_surfaces` shell no longer requires non-default account tab namespaces directly.
2. Non-default account tabs resolve through lazy account-tab modules and show a deterministic loading state until their renderer export is ready.
3. Selecting a lazy account tab dispatches a lazy-load effect without breaking existing save/fetch ordering.
4. Cold-route startup and navigation tests prove only the visible lazy tab is requested, not the full account tab set.
5. A post-change release build records smaller eager account-surface bytes than the current ~`306 KB` raw account-surfaces chunk, or the plan explicitly documents the remaining hoist blocker.
6. `npm run check`, `npm test`, `npm run test:websocket`, and the focused Playwright command pass, or any failure is documented as an unrelated blocker with exact evidence.

## Idempotence and Recovery

The loader/module split is additive before subtractive. New account-tab modules and exports can land first while the old eager renderers still exist. If a grouped module boundary turns out to hoist code back into `:account_surfaces`, revert only that grouping choice, keep the RED tests, and retry with a smaller or different capability grouping. Do not use destructive git commands to recover.

## Outcomes & Retrospective

Outcome:
- The eager account surface shell now keeps only balances plus shared shell/view-model code in `account_surfaces`. Positions/outcomes, open orders/order history, trade history/TWAP, and funding history now resolve through grouped lazy modules with explicit loader state under `:account-tab-modules`.
- `tab_actions.cljs` no longer pulls heavy tab namespaces into startup just to read filter metadata, so the lazy split is not immediately defeated by the tab strip.
- Selecting or routing into a lazy account tab now dispatches `[:effects/load-account-tab-module <tab>]` before the tab tries to render, while preserving the existing projection-before-fetch ordering.

Measured result:
- Release build after the change produced `account_surfaces.D710D896E99AA73744E893DF232549CF.js` at `130582` raw bytes, `29484` gzip bytes, and `24646` brotli bytes.
- The new deferred account-tab chunks measured:
  - `account_positions_outcomes...js`: `88697` raw
  - `account_orders...js`: `38542` raw
  - `account_activity...js`: `32111` raw
  - `account_funding_history...js`: `16221` raw
- Against the issue’s measured pre-change `~306 KB` raw startup chunk for account surfaces, the eager `account_surfaces` chunk is now about `175 KB` smaller, roughly a `57%` reduction. This delta is anchored to the provided pre-change measurement plus the local post-change release build; the pre-change baseline was not rebuilt in this worktree during this run.

Validation:
- Passed: `npm test`
- Passed: `npm run test:websocket`
- Passed: `npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "desktop trade shell keeps the chart dominant while account tabs stay geometry-stable"`
- Failed for unrelated pre-existing lint: `npm run check`
  - Exact blocker: `docs/product-specs/chart-hover-navigation-controls-prd.md` is flagged stale by `lint:docs`.

Follow-up:
- If a fully green `npm run check` is required for this thread, the remaining work is to refresh or otherwise resolve the unrelated stale-doc lint in `docs/product-specs/chart-hover-navigation-controls-prd.md`.
