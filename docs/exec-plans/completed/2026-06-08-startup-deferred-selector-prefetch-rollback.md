# Startup Deferred Selector Prefetch Rollback

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, opening the trade route on a fresh release build should no longer warm the full asset-selector market catalog in the background during startup. A user should still get the existing bootstrap data needed for the visible trade shell, and the full selector-market expansion should wait until an explicit demand path such as opening the asset selector or entering a route that already requests the full market set. The observable win is fewer `/info` requests and less startup work before the user asks for the full catalog.

## Context References

Public refs:
- Direct user request on 2026-06-08 to create an execution plan and implement the selected highest-leverage performance fix.

Repo artifacts:
- `/hyperopen/docs/exec-plans/completed/2026-03-16-release-build-performance-leverage-plan.md` records the earlier accepted contract: cold `/trade` startup keeps only bootstrap selector data and does not auto-schedule full selector-market expansion.
- `/hyperopen/tmp/perf-audit-20260608-summary.json` contains the current startup-profile and Lighthouse evidence for the regression.
- `/hyperopen/tmp/perf-audit-20260608-info-requests.json` captures the current startup `/info` request burst, including `perpDexs` and named DEX `metaAndAssetCtxs` requests.

Local scratch refs (non-authoritative):
- `/Users/barry/.codex/automations/performance-audit/memory.md`

## Progress

- [x] (2026-06-08 21:03Z) Confirmed the current regression boundary from saved audit artifacts and source: `src/hyperopen/startup/runtime.cljs` still calls deferred `{:phase :full}` selector-market expansion during startup.
- [x] (2026-06-08 21:03Z) Confirmed the existing explicit-demand paths already exist: opening the asset selector requests full selector markets in `src/hyperopen/asset_selector/actions.cljs`, and portfolio optimize route loading requests `{:phase :full}` in `src/hyperopen/portfolio/optimizer/actions/run.cljs`.
- [x] (2026-06-08 21:06Z) Updated the startup regression coverage in `test/hyperopen/startup/deferred_bootstrap_outcome_cache_test.cljs`, `test/hyperopen/startup/runtime_test.cljs`, and `test/hyperopen/core_bootstrap/runtime_startup_test.cljs` so deferred startup now proves it records no selector `{:phase :full}` fetches while preserving bootstrap completion bookkeeping.
- [x] (2026-06-08 21:07Z) Rolled back `src/hyperopen/startup/runtime.cljs` so deferred startup resolves without issuing selector-market expansion and removed the dead startup-only gating helpers plus the unused `portfolio-routes` dependency.
- [x] (2026-06-08 21:13Z) Ran `npm test`, `npm run test:websocket`, `npm run check`, and `npm run build`, then captured a post-fix release startup `/info` trace on `http://127.0.0.1:4173/trade?market=HYPE&tab=positions` to confirm the named-DEX fan-out is gone. This plan is ready to move to `completed/`.

## Surprises & Discoveries

- Observation: the current route-aware portfolio optimize path already requests `{:phase :full}` explicitly during route loading, so startup deferred bootstrap is not the only way that route can obtain the full selector catalog.
  Evidence: `src/hyperopen/portfolio/optimizer/actions/run.cljs` adds `[:effects/fetch-asset-selector-markets {:phase :full}]` when the optimize route loads and the selector phase is not already `:full`.

- Observation: the current deferred startup path is broader than the March 2026 accepted trade-startup contract.
  Evidence: `/hyperopen/docs/exec-plans/completed/2026-03-16-release-build-performance-leverage-plan.md` records that the accepted Milestone 3 cut removed automatic deferred full selector-market expansion from cold `/trade` startup, while `src/hyperopen/startup/runtime.cljs` currently still calls `fetch-asset-selector-markets!` with `{:phase :full}` from `run-deferred-bootstrap!`.

- Observation: the rollback removes the startup `/info` burst exactly where the audit said it would.
  Evidence: the pre-change artifact `/hyperopen/tmp/perf-audit-20260608-info-requests.json` recorded `14` startup `/info` requests including `perpDexs` and eight named-DEX `metaAndAssetCtxs` calls. After the rollback, a direct release capture against `http://127.0.0.1:4173/trade?market=HYPE&tab=positions` saw only `5` startup `/info` requests: `candleSnapshot`, `metaAndAssetCtxs`, `outcomeMeta`, `spotMeta`, and `webData2`.

## Decision Log

- Decision: implement the rollback in `src/hyperopen/startup/runtime.cljs` instead of changing the selector fetcher or the market loader.
  Rationale: the performance regression is not that `:full` does too much; it is that startup still asks for `:full` at all. The on-demand route and asset-selector entry points should remain intact.
  Date/Author: 2026-06-08 / Codex

- Decision: keep explicit-demand `:full` fetch paths such as asset-selector open and portfolio optimize route load unchanged.
  Rationale: the selected fix is a startup rollback. Those other call sites are product-driven demand paths rather than unsolicited background warmup.
  Date/Author: 2026-06-08 / Codex

## Outcomes & Retrospective

The rollback is complete. `src/hyperopen/startup/runtime.cljs` now keeps startup on the accepted `:bootstrap` contract only; deferred startup no longer issues `fetch-asset-selector-markets! {:phase :full}` at all. Full selector-market expansion is still preserved on explicit-demand paths such as asset-selector open and portfolio optimize route load.

The regression is locked in across three suites:
- `test/hyperopen/startup/deferred_bootstrap_outcome_cache_test.cljs`
- `test/hyperopen/startup/runtime_test.cljs`
- `test/hyperopen/core_bootstrap/runtime_startup_test.cljs`

Validation passed:
- `npm test`
- `npm run test:websocket`
- `npm run check`
- `npm run build`

Measured startup-network outcome:
- Pre-change audit artifact `/hyperopen/tmp/perf-audit-20260608-info-requests.json` showed `14` startup `/info` requests within five seconds, including `perpDexs` plus named-DEX `metaAndAssetCtxs` for `xyz`, `flx`, `vntl`, `hyna`, `km`, `abcd`, `cash`, and `para`.
- Post-change direct release capture on `http://127.0.0.1:4173/trade?market=HYPE&tab=positions` showed `5` startup `/info` requests: `candleSnapshot`, `metaAndAssetCtxs`, `outcomeMeta`, `spotMeta`, and `webData2`. There were no `perpDexs` or named-DEX `metaAndAssetCtxs` calls.

Remaining measurement debt:
- The direct post-fix trace was one release capture, which is enough to confirm the request-pattern change but not enough to claim new median startup timings or Lighthouse score deltas. If a fresh perf sign-off is needed, re-run the full cached startup-profile and Lighthouse audit set against the same route used in `/hyperopen/tmp/perf-audit-20260608-summary.json`.

## Context and Orientation

The startup runtime is owned by `src/hyperopen/startup/runtime.cljs`. In this repository, “bootstrap” means the minimum network and state setup needed so the first visible route can render useful content. The startup runtime splits that work into “critical bootstrap”, which happens immediately for the visible route, and “deferred bootstrap”, which is extra post-render work scheduled after the first paint. The selected regression lives in the deferred half.

The full asset-selector market catalog is assembled through `src/hyperopen/api/market_loader.cljs`. That code accepts a `:phase` option. `:bootstrap` means only the default market context needed for startup; `:full` means include named perp DEX metadata and the broader selector-market set. The performance regression is that startup still asks for `:full`, which then fans out into `perpDexs` plus one `metaAndAssetCtxs` request per named DEX.

The demand-driven paths already exist elsewhere. Opening the asset selector in `src/hyperopen/asset_selector/actions.cljs` requests full selector markets when the current selector data is empty or not yet `:full`. Portfolio optimize route loading in `src/hyperopen/portfolio/optimizer/actions/run.cljs` also requests `{:phase :full}` explicitly when needed. Those are the paths that should continue to own full expansion.

The most relevant tests already live in `test/hyperopen/startup/runtime_test.cljs` and `test/hyperopen/startup/deferred_bootstrap_outcome_cache_test.cljs`. They cover the current startup bootstrap branches and are the correct place to pin the rollback.

## Plan of Work

First, add or update a startup regression test so the deferred startup path proves it no longer calls `fetch-asset-selector-markets!` with `{:phase :full}`. The test should still assert that the `app:full-bootstrap:ready` performance mark fires, because the runtime bookkeeping remains part of startup completion even when the network work is removed.

Next, update `src/hyperopen/startup/runtime.cljs` so `run-deferred-bootstrap!` resolves without issuing the full selector-market fetch. Once that fetch is gone, remove any helper functions in the same file that existed only to decide whether deferred `:full` should run, because that conditional logic will be dead and misleading.

Finally, run the smallest targeted tests first, then the required repo validation commands for code changes: `npm run check`, `npm test`, and `npm run test:websocket`. After the implementation is accepted, move this plan from `active/` to `completed/` and record the final outcome with the measured effect or any remaining measurement debt.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/6a9c/hyperopen`.

1. Add the regression in `test/hyperopen/startup/runtime_test.cljs` or the focused deferred-bootstrap suite so a deferred startup call records no `{:phase :full}` fetches while still recording the completion mark.
2. Run the smallest relevant test command first, either:
   `npm test -- --focus hyperopen.startup.runtime-test`
   or
   `npm test -- --focus hyperopen.startup.deferred-bootstrap-outcome-cache-test`
   depending on the final test location.
3. Update `src/hyperopen/startup/runtime.cljs` to remove the deferred full selector-market fetch and any dead helpers that only supported that fetch.
4. Re-run the focused startup test.
5. Run the required broader validation:
   `npm run check`
   `npm test`
   `npm run test:websocket`
6. Move this file to `docs/exec-plans/completed/` once all acceptance criteria are satisfied, then re-run the docs-inclusive validation if needed.

## Validation and Acceptance

Acceptance has three parts.

First, the startup regression test must fail before the change and pass after it. The passing assertion must prove that deferred startup does not call `fetch-asset-selector-markets!` at all, or at minimum never calls it with `{:phase :full}`.

Second, the required repo commands must pass:
- `npm run check`
- `npm test`
- `npm run test:websocket`

Third, the implementation must preserve the explicit-demand paths. Source inspection after the change must still show the asset-selector open path in `src/hyperopen/asset_selector/actions.cljs` and the portfolio optimize route path in `src/hyperopen/portfolio/optimizer/actions/run.cljs` issuing full selector-market fetches.

## Idempotence and Recovery

The test and runtime edits are idempotent. Re-running the focused tests and repo validation is safe. If a targeted test fails unexpectedly after the rollback, restore only the changed startup-runtime logic and re-run the focused startup suite before touching broader route loaders; the selected fix should not require edits outside startup runtime plus its regression coverage.

## Artifacts and Notes

Key pre-change evidence:

  /hyperopen/tmp/perf-audit-20260608-info-requests.json
    count = 14 startup `/info` requests within 5 seconds
    includes `perpDexs` and named DEX `metaAndAssetCtxs` calls for
    `xyz`, `flx`, `vntl`, `hyna`, `km`, `abcd`, `cash`, and `para`

  /hyperopen/tmp/perf-audit-20260608-summary.json
    startupProfile.medians.startupBytes = 4089027
    lighthouse.categories.performance.score = 0.42

## Interfaces and Dependencies

`src/hyperopen/startup/runtime.cljs` must continue to export:

  start-critical-bootstrap!
  run-deferred-bootstrap!
  schedule-deferred-bootstrap!
  initialize-remote-data-streams!

`run-deferred-bootstrap!` must still return a JavaScript Promise and must still mark `app:full-bootstrap:ready` when that Promise settles. After this change, it must no longer depend on startup-only helpers that inspect cache hydration, named outcome presence, or active perp leverage completeness in order to decide whether to run `{:phase :full}` selector-market expansion, because startup no longer owns that expansion.

Plan revision note: 2026-06-08 - Authored the active ExecPlan for the selected deferred selector-market prefetch rollback so the implementation can proceed with repo-native planning, regression coverage, and validation.
