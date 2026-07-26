# Fix Optimizer Vault Search Cache Regression

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and the repository planning entry point `/hyperopen/docs/PLANS.md`. The live `bd` issue for this work is `hyperopen-23z2`.

## Purpose / Big Picture

Users on `/portfolio/optimize/new` must be able to search and add vaults without waiting for the full live vault index download. The regression is that the optimizer route schedules the uncached vault index effect, so a cold route depends on a roughly 14 MB request to `https://stats-data.hyperliquid.xyz/Mainnet/vaults`; measured from this workspace, that request took about 14.3 seconds. After this change, optimizer routes should use the existing stale-while-revalidate vault index path: cached vault rows hydrate quickly from IndexedDB, while the live request refreshes the data in the background.

## Progress

- [x] (2026-05-05T19:24:47Z) Measured the live vault index endpoint from the workspace with `curl -L -w`, observing `status=200 total=14.300958 size=14191720`.
- [x] (2026-05-05T19:24:47Z) Located the uncached optimizer route effect in `/hyperopen/src/hyperopen/portfolio/optimizer/actions/common.cljs` function `vault-list-metadata-fetch-effects`.
- [x] (2026-05-05T19:24:47Z) Confirmed the cache-backed effect exists as `:effects/api-fetch-vault-index-with-cache` and is already wired in `/hyperopen/src/hyperopen/app/effects.cljs`.
- [x] (2026-05-05T19:24:47Z) Created live `bd` issue `hyperopen-23z2`.
- [x] (2026-05-05T19:31:00Z) Added a focused failing test in `/hyperopen/test/hyperopen/portfolio/optimizer/actions_test.cljs` that expects optimizer route bootstrap to emit `:effects/api-fetch-vault-index-with-cache`; it failed in three route cases before the source change.
- [x] (2026-05-05T19:33:00Z) Changed `/hyperopen/src/hyperopen/portfolio/optimizer/actions/common.cljs` so optimizer vault metadata bootstrap uses the cache-backed effect.
- [x] (2026-05-05T19:45:00Z) Added Playwright regression coverage proving cached vault rows can appear for optimizer vault search before a slow live vault index response completes.
- [x] (2026-05-05T19:52:00Z) Ran focused test commands, the Playwright regression, `npm run check`, `npm test`, `npm run test:websocket`, and `npm run browser:cleanup`; all passed.

## Surprises & Discoveries

- Observation: The existing `/vaults` list route already uses `:effects/api-fetch-vault-index-with-cache`, but the optimizer route uses `:effects/api-fetch-vault-index`.
  Evidence: `/hyperopen/src/hyperopen/vaults/application/route_loading.cljs` emits `[:effects/api-fetch-vault-index-with-cache]`, while `/hyperopen/src/hyperopen/portfolio/optimizer/actions/common.cljs` emits `[:effects/api-fetch-vault-index]`.

- Observation: The live full vault index endpoint is large enough to make route search feel broken when it gates cold metadata.
  Evidence: `curl -L -w '\nstatus=%{http_code} total=%{time_total} size=%{size_download}\n' -o /tmp/hyperopen-vaults.json https://stats-data.hyperliquid.xyz/Mainnet/vaults` returned `status=200 total=14.300958 size=14191720`.

- Observation: The first focused CLJS test attempt failed before executing tests because the workspace dependency tree is incomplete.
  Evidence: `node out/test.js --test=hyperopen.portfolio.optimizer.actions-test ...` reported `Cannot find module 'lucide/dist/esm/icons/external-link.js'`.

- Observation: After `npm install` restored the missing `lucide` package locally, the focused route action test reached assertions and failed exactly on the uncached effect id.
  Evidence: `node out/test.js --test=hyperopen.portfolio.optimizer.actions-test` reported three failures where expected `:effects/api-fetch-vault-index-with-cache` but actual was `:effects/api-fetch-vault-index`.

- Observation: After changing `vault-list-metadata-fetch-effects`, the focused optimizer action test passes.
  Evidence: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.actions-test` ran 11 tests and 33 assertions with 0 failures and 0 errors.

- Observation: Browser requests to the cross-origin live vault index intentionally strip `If-None-Match` and `If-Modified-Since` request headers.
  Evidence: `/hyperopen/src/hyperopen/api/endpoints/vaults/index.cljs` function `browser-safe-vault-index-opts` removes conditional headers for cross-origin requests and sets `cache` to `no-cache` when needed, so the Playwright regression asserts cached-row visibility while the live request is blocked instead of asserting custom request headers.

- Observation: The browser regression passes with the live vault index route held open.
  Evidence: `npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "vault search hydrates"` passed 1 test in 10.7 seconds.

- Observation: All required repository gates passed after the fix.
  Evidence: `npm run check` exited 0, `npm test` ran 3,769 tests and 20,785 assertions with 0 failures and 0 errors, and `npm run test:websocket` ran 524 tests and 3,043 assertions with 0 failures and 0 errors. `npm run browser:cleanup` returned `{"ok": true, "stopped": [], "results": []}`.

## Decision Log

- Decision: Fix the optimizer route bootstrap by switching only the vault index effect to `:effects/api-fetch-vault-index-with-cache`, while leaving `:effects/api-fetch-vault-summaries` in place.
  Rationale: The cache-backed effect preserves the live refresh behavior and already handles IndexedDB hydration, conditional validators, `:not-modified`, and single-flight protection. Reworking vault candidate filtering would not address the observed 14 MB route bootstrap bottleneck.
  Date/Author: 2026-05-05 / Codex

- Decision: Keep the scope to optimizer route metadata loading and focused regression coverage.
  Rationale: The existing cache implementation is tested and shared by vault routes. The regression is caused by bypassing it, not by the cache implementation itself.
  Date/Author: 2026-05-05 / Codex

## Outcomes & Retrospective

The optimizer route now uses the same cache-backed vault index path as the vault list route. Users on `/portfolio/optimize/new`, `/portfolio/optimize`, and saved optimizer scenario routes can get vault candidates from persisted `vault-index-cache` rows while the live vault index refresh remains in flight. This reduces complexity by removing the optimizer route's special uncached path and reusing the established stale-while-revalidate effect.

The regression is covered at two levels. The CLJS action test locks the emitted effect id so the optimizer route cannot quietly regress to the uncached fetch. The Playwright regression seeds IndexedDB, blocks the live vault index endpoint, and proves that the cached `Alpha Yield` vault row is searchable before the live request is released. All required gates passed.

## Context and Orientation

The portfolio optimizer route loader is the action that runs when the app navigates to optimizer pages such as `/portfolio/optimize`, `/portfolio/optimize/new`, and `/portfolio/optimize/<scenario-id>`. It lives in `/hyperopen/src/hyperopen/portfolio/optimizer/actions/run.cljs` as `load-portfolio-optimizer-route`. That action adds route-specific effects, then appends shared vault metadata effects from `/hyperopen/src/hyperopen/portfolio/optimizer/actions/common.cljs`.

An effect is an instruction returned by an action, such as `[:effects/api-fetch-vault-index]`, that the runtime later interprets as asynchronous work. The existing uncached vault index effect downloads the live full vault index. The cache-backed effect `[:effects/api-fetch-vault-index-with-cache]` lives behind `/hyperopen/src/hyperopen/vaults/effects.cljs` function `api-fetch-vault-index-with-cache!`; it first starts loading state, reads cached vault index metadata and rows from IndexedDB through `/hyperopen/src/hyperopen/vaults/infrastructure/list_cache.cljs`, hydrates rows into app state through projections, and also validates against the live endpoint.

The optimizer universe search includes vault candidates, so optimizer routes need vault list metadata. When `[:vaults :merged-index-rows]` is empty, `vault-list-metadata-fetch-effects` currently schedules `:effects/api-fetch-vault-index` and `:effects/api-fetch-vault-summaries`. That choice bypasses the stale-while-revalidate cache path and causes the user-visible regression.

## Plan of Work

First, update `/hyperopen/test/hyperopen/portfolio/optimizer/actions_test.cljs` so the existing route metadata test expects `:effects/api-fetch-vault-index-with-cache` for `/portfolio/optimize/new`, `/portfolio/optimize`, and `/portfolio/optimize/<scenario-id>` when vault rows are missing. Run that focused test and record that it fails against the current production code.

Second, edit `/hyperopen/src/hyperopen/portfolio/optimizer/actions/common.cljs` in `vault-list-metadata-fetch-effects` to emit `[:effects/api-fetch-vault-index-with-cache]` instead of `[:effects/api-fetch-vault-index]`. Keep the guard that returns no effects when `[:vaults :merged-index-rows]` already has rows, and keep `[:effects/api-fetch-vault-summaries]`.

Third, add regression coverage for the stale-while-revalidate behavior. Prefer a deterministic Playwright test under `/hyperopen/tools/playwright/test/portfolio-regressions.spec.mjs` that seeds IndexedDB with one vault cache row, delays the live `stats-data.hyperliquid.xyz/Mainnet/vaults` route, opens `/portfolio/optimize/new`, searches for that vault, and asserts the cached vault is available before the delayed route is released. If the existing harness cannot seed the cache reliably without excessive new test infrastructure, add a focused CLJS effect test that exercises `api-fetch-vault-index-with-cache!` with a portfolio optimizer route and a delayed live request, then document the Playwright limitation and run the smallest relevant existing Playwright optimizer regression.

Finally, run focused CLJS and browser tests, then the required repository gates: `npm run check`, `npm test`, and `npm run test:websocket`. If a command is blocked by missing dependencies or local services, capture the exact output and the highest-confidence narrower verification that did run.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/481e/hyperopen`.

Run the focused CLJS test after changing only the test:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.actions-test

Expected before the production change: the route metadata test fails because production emits `:effects/api-fetch-vault-index`.

Run the same focused CLJS test after the production change:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.actions-test

Expected after the production change: the route metadata test passes, unless the workspace dependency issue involving `lucide/dist/esm/icons/external-link.js` still prevents the test runner from starting.

For browser coverage, use the smallest relevant Playwright command after adding the regression:

    npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "<new test title>"

Then run:

    npm run check
    npm test
    npm run test:websocket

## Validation and Acceptance

Acceptance is met when the optimizer route action emits `:effects/api-fetch-vault-index-with-cache` anywhere it needs cold vault metadata, and does not emit either vault metadata effect when `[:vaults :merged-index-rows]` is already populated. The test `load-portfolio-optimizer-route-fetches-vault-metadata-for-universe-search-test` must fail before the production patch and pass after it.

Browser-facing acceptance is that `/portfolio/optimize/new` can expose a cached vault search result while the live full vault index request is still delayed. This proves the route no longer waits on the slow 14 MB endpoint before vault search can work.

Repository acceptance is that `npm run check`, `npm test`, and `npm run test:websocket` pass, or any blocker is documented with the exact error and a clear explanation of which narrower tests still verified the change.

## Idempotence and Recovery

The source change is a one-keyword effect substitution and can be repeated safely. The test update is deterministic. If dependency installation or generated test runner state changes during verification, do not revert unrelated user changes; document the blocker and leave the working tree limited to the plan, source, and test files intentionally touched by this work.

If the Playwright cache-seeding regression becomes too broad for this bug fix, stop before adding brittle browser infrastructure and keep the focused action/effect tests as the required regression proof. Record that decision in `Decision Log` and explain the remaining browser risk in `Outcomes & Retrospective`.

## Artifacts and Notes

Key measured baseline:

    status=200 total=14.300958 size=14191720

Focused test blocker observed before this plan was created:

    Error: Cannot find module 'lucide/dist/esm/icons/external-link.js'

## Interfaces and Dependencies

The effect id `:effects/api-fetch-vault-index-with-cache` is already registered in `/hyperopen/src/hyperopen/schema/runtime_registration/vaults.cljs`, wired in `/hyperopen/src/hyperopen/app/effects.cljs`, exposed through `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs`, and implemented by `/hyperopen/src/hyperopen/vaults/effects.cljs` function `api-fetch-vault-index-with-cache!`. This plan does not add a new public API, route, state key, or runtime registration.

Revision note 2026-05-05: Initial plan created from the measured regression and root-cause investigation so implementation can proceed with a live `bd` issue and restartable context.

Revision note 2026-05-05: Recorded the RED test result after restoring the missing local npm dependency with `npm install`.

Revision note 2026-05-05: Recorded the source change and focused GREEN test result.

Revision note 2026-05-05: Added the Playwright cache-hydration regression and documented why it asserts pre-live cached visibility instead of cross-origin conditional headers.

Revision note 2026-05-05: Recorded final validation results and outcome after all required gates passed.
