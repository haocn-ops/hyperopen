# Reduce Staking View Structural CRAP

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

The active tracked issue for this work is `hyperopen-y00w`.

## Purpose / Big Picture

After this change, the staking page remains visually and behaviorally the same, but the render logic is no longer concentrated in one 1,151-line namespace. A contributor should be able to change staking popovers, validator table behavior, or history panels without reopening one giant render function or reintroducing the current CRAP hotspots in `/hyperopen/src/hyperopen/views/staking_view.cljs`.

The observable proof is:

    npm run check
    npm test
    npm run test:websocket
    npm run coverage
    npm run crap:report -- --module src/hyperopen/views/staking_view.cljs --format json
    npm run crap:report -- --module src/hyperopen/views/staking/popovers.cljs --format json
    npm run crap:report -- --module src/hyperopen/views/staking/validators.cljs --format json
    npm run crap:report -- --module src/hyperopen/views/staking/history.cljs --format json

The first three commands are the repository gates. The coverage and CRAP commands prove that the old hotspot logic has been split into smaller namespaces and that the touched staking view namespaces remain below the CRAP threshold.

## Progress

- [x] (2026-03-13 20:03Z) Reviewed the repo guardrails, current staking view/test shape, and prior CRAP-remediation precedents.
- [x] (2026-03-13 20:03Z) Created and claimed `bd` issue `hyperopen-y00w` for this work.
- [x] (2026-03-13 20:04Z) Authored this active ExecPlan.
- [x] (2026-03-13 20:11Z) Extracted the staking shared, popover, validator, and history rendering into internal `/hyperopen/src/hyperopen/views/staking/*.cljs` namespaces while keeping `hyperopen.views.staking-view/staking-view` as the public entrypoint.
- [x] (2026-03-13 20:13Z) Expanded `/hyperopen/test/hyperopen/views/staking_view_test.cljs` to cover open popovers, histories, error rendering, and connected-only popover gating.
- [x] (2026-03-13 20:20Z) Ran `npm run check`, `npm test`, `npm run test:websocket`, `npm run coverage`, and module CRAP reports for the touched staking view namespaces; confirmed zero CRAP hotspots in the touched staking view modules.
- [x] (2026-03-13 20:20Z) Closed `hyperopen-y00w` and moved this plan to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: `/hyperopen/src/hyperopen/views/staking_view.cljs` is currently 1,151 lines and materially exceeds the architecture target used for new namespaces.
  Evidence: `wc -l src/hyperopen/views/staking_view.cljs` returned `1151`.

- Observation: The largest static complexity hotspots are concentrated in the coordinator and view-owned controls rather than in the VM.
  Evidence: The repo-local complexity analyzer reported `staking-view` complexity `19`, `popover-validator-select` `16`, `staking-timeframe-menu` `8`, `transfer-popover-content` `8`, `action-popover-layer` `6`, and `validator-pagination` `5`.

- Observation: The current public staking view tests cover only disconnected connect gating, basic validator table rendering, inactive tooltip rendering, description tooltip rendering, and pagination/view-all behavior.
  Evidence: `/hyperopen/test/hyperopen/views/staking_view_test.cljs` currently has no coverage for open popovers, reward/action history tabs, error banner rendering, or connected-only popover gating.

- Observation: The earlier `fetch_compat_test` runner crash was environmental rather than a repository-stable blocker.
  Evidence: After restoring local JS dependencies with `npm ci`, `npm test` passed with `Ran 2369 tests containing 12435 assertions. 0 failures, 0 errors.` and `npm run coverage` completed successfully.

- Observation: `npm test -- --runInBand test/hyperopen/views/staking_view_test.cljs` does not filter the generated Node runner in this repository.
  Evidence: The command prints `Unknown arg: --runInBand` and `Unknown arg: test/hyperopen/views/staking_view_test.cljs`, then proceeds to run the full generated suite anyway.

## Decision Log

- Decision: Use a structural split into internal staking view namespaces instead of only carving smaller helpers inside `/hyperopen/src/hyperopen/views/staking_view.cljs`.
  Rationale: The file is far above the repository size guidance, and the hotspot logic groups naturally into reusable subsections already reflected in the page layout: shared primitives, popovers, validator-performance UI, and histories.
  Date/Author: 2026-03-13 / Codex

- Decision: Keep `/hyperopen/src/hyperopen/views/staking/vm.cljs` out of scope.
  Rationale: The approved task is to reduce CRAP in the view module without widening into a second architectural refactor. The VM already provides a usable coordinator contract for the extracted render sections.
  Date/Author: 2026-03-13 / Codex

- Decision: Preserve existing `data-role` hooks, action vectors, tab ids, copy, and public `(staking-view state)` entrypoint.
  Rationale: This is a maintainability refactor with regression-hardening, not a UI redesign or runtime contract change.
  Date/Author: 2026-03-13 / Codex

## Outcomes & Retrospective

Implementation completed. `/hyperopen/src/hyperopen/views/staking_view.cljs` is now a 236-line coordinator that delegates to four focused internal namespaces:

- `/hyperopen/src/hyperopen/views/staking/shared.cljs` (`110` lines)
- `/hyperopen/src/hyperopen/views/staking/popovers.cljs` (`490` lines)
- `/hyperopen/src/hyperopen/views/staking/validators.cljs` (`315` lines)
- `/hyperopen/src/hyperopen/views/staking/history.cljs` (`61` lines)

This reduced overall complexity substantially. The old static hotspot cluster in `/hyperopen/src/hyperopen/views/staking_view.cljs` is gone, and the refreshed CRAP reports show zero functions above threshold in every touched staking view namespace. The public staking view tests also now cover transfer, stake, and unstake popovers, reward/action history tabs, error rendering, and disconnected popover gating.

Final validation outcomes:

- `npm run check`: pass
- `npm test`: pass with `Ran 2369 tests containing 12435 assertions. 0 failures, 0 errors.`
- `npm run test:websocket`: pass with `Ran 385 tests containing 2187 assertions. 0 failures, 0 errors.`
- `npm run coverage`: pass with `Statements 90.43%`, `Branches 67.61%`, `Functions 85.2%`, and `Lines 90.43%`

Final CRAP outcomes:

- `/hyperopen/src/hyperopen/views/staking_view.cljs`: `crappy-functions 0`, `max-crap 3.0`
- `/hyperopen/src/hyperopen/views/staking/shared.cljs`: `crappy-functions 0`, `max-crap 10.2059`
- `/hyperopen/src/hyperopen/views/staking/popovers.cljs`: `crappy-functions 0`, `max-crap 13.7930`
- `/hyperopen/src/hyperopen/views/staking/validators.cljs`: `crappy-functions 0`, `max-crap 8.0`
- `/hyperopen/src/hyperopen/views/staking/history.cljs`: `crappy-functions 0`, `max-crap 5.0`

## Context and Orientation

The current staking route render entrypoint lives in `/hyperopen/src/hyperopen/views/staking_view.cljs` and delegates state shaping to `/hyperopen/src/hyperopen/views/staking/vm.cljs`. The view namespace currently owns four different responsibilities at once:

1. page-level shell and summary rendering,
2. action-popover rendering and anchor/layout helpers,
3. validator table, timeframe menu, sorting, row rendering, and pagination,
4. reward/action history tables.

The repository already uses this style of structural split in other large view areas, such as `/hyperopen/src/hyperopen/views/account_info/**` and `/hyperopen/src/hyperopen/views/trade/**`. This task should follow that precedent and create focused internal namespaces under `/hyperopen/src/hyperopen/views/staking/`.

The public tests for the staking view live in `/hyperopen/test/hyperopen/views/staking_view_test.cljs`. Reuse the existing tree-walking helpers in that file instead of creating a second assertion style.

## Plan of Work

First, create internal staking render namespaces. `/hyperopen/src/hyperopen/views/staking/shared.cljs` will own common formatters and small shared render primitives such as status pills, summary cards, key-value rows, and shared focus-class constants. `/hyperopen/src/hyperopen/views/staking/popovers.cljs` will own the action popover boundary, including anchor lookup, panel layout, amount inputs, validator selector rendering, and the exported `action-popover-layer`. `/hyperopen/src/hyperopen/views/staking/validators.cljs` will own the validator-performance surface, including timeframe menu, sortable headers, validator rows, description tooltips, pagination, and the exported `validator-performance-panel`. `/hyperopen/src/hyperopen/views/staking/history.cljs` will own the generic history table shell and the exported rewards/action history panels.

Second, shrink `/hyperopen/src/hyperopen/views/staking_view.cljs` into a coordinator. It should still require the existing VM, build the page shell, summary cards, balance panel, tab strip, error banner, and final popover mount, but it must delegate the major subsection rendering to the new internal namespaces.

Third, widen `/hyperopen/test/hyperopen/views/staking_view_test.cljs`. Cover the transfer popover with open state, direction toggle action, MAX button wiring, and submitting copy; cover the stake and unstake popovers with validator dropdown state, selected validator display, and empty search results; cover reward and action history tab rendering, loading copy, and empty copy; cover the error banner; and cover that the action popover is not mounted when disconnected even if the UI state map says it is open.

Finally, run the required validation commands and CRAP reports. The full suite and coverage now pass in this worktree after restoring dependencies, so no blocker handling was required in the final landing pass.

## Concrete Steps

From `/Users/barry/.codex/worktrees/6126/hyperopen`:

1. Create the new internal staking render namespaces under `src/hyperopen/views/staking/`.
2. Rewire `src/hyperopen/views/staking_view.cljs` to use those namespaces while keeping the public entrypoint stable.
3. Expand `test/hyperopen/views/staking_view_test.cljs`.
4. Run:

       npm test -- --runInBand test/hyperopen/views/staking_view_test.cljs
       npm run check
       npm test
       npm run test:websocket
       npm run coverage
       npm run crap:report -- --module src/hyperopen/views/staking_view.cljs --format json
       npm run crap:report -- --module src/hyperopen/views/staking/popovers.cljs --format json
       npm run crap:report -- --module src/hyperopen/views/staking/validators.cljs --format json
       npm run crap:report -- --module src/hyperopen/views/staking/history.cljs --format json

Expected result: the focused staking view tests pass, the repository gates pass, and the touched staking view namespaces report zero CRAP hotspots above threshold.

## Validation and Acceptance

This work is complete when all of the following are true:

- `(hyperopen.views.staking-view/staking-view state)` remains the public render entrypoint.
- The staking page still renders the same copy, action vectors, `data-role` hooks, connect gating, summary cards, balance panel, validator table, histories, and action popovers.
- The extracted internal seams exist and are narrow: shared render primitives, popover rendering, validator-performance rendering, and history rendering are each in their own namespace.
- `/hyperopen/test/hyperopen/views/staking_view_test.cljs` covers open popovers, history tabs, error rendering, and connected-only popover gating.
- `npm run check`, `npm test`, `npm run test:websocket`, and the module CRAP reports pass.

## Idempotence and Recovery

These changes are source refactors plus test additions. Reapplying the split is safe. If a new internal namespace changes visible behavior, compare the old and new Hiccup shape through the public staking view tests and move only the minimum missing detail back into the subsection helper. If a future worktree lacks dependencies, `npm ci` is the expected recovery step before rerunning the repository commands.

## Artifacts and Notes

Tracked work:

- `bd` issue `hyperopen-y00w`: "Reduce CRAP in staking view structural render paths" (`closed` with reason `Completed`)

Baseline hotspot context:

- `/hyperopen/src/hyperopen/views/staking_view.cljs` `staking-view` complexity `19`
- `/hyperopen/src/hyperopen/views/staking_view.cljs` `popover-validator-select` complexity `16`
- `/hyperopen/src/hyperopen/views/staking_view.cljs` `staking-timeframe-menu` complexity `8`
- `/hyperopen/src/hyperopen/views/staking_view.cljs` `transfer-popover-content` complexity `8`

Final validation artifacts:

- `npm run check` passed.
- `npm test` passed with `Ran 2369 tests containing 12435 assertions. 0 failures, 0 errors.`
- `npm run test:websocket` passed with `Ran 385 tests containing 2187 assertions. 0 failures, 0 errors.`
- `npm run coverage` passed with `Statements 90.43%`, `Branches 67.61%`, `Functions 85.2%`, and `Lines 90.43%`.
- `npm run crap:report -- --module src/hyperopen/views/staking_view.cljs --format json` reported `crappy-functions 0` and `max-crap 3.0`.
- `npm run crap:report -- --module src/hyperopen/views/staking/shared.cljs --format json` reported `crappy-functions 0` and `max-crap 10.2059`.
- `npm run crap:report -- --module src/hyperopen/views/staking/popovers.cljs --format json` reported `crappy-functions 0` and `max-crap 13.7930`.
- `npm run crap:report -- --module src/hyperopen/views/staking/validators.cljs --format json` reported `crappy-functions 0` and `max-crap 8.0`.
- `npm run crap:report -- --module src/hyperopen/views/staking/history.cljs --format json` reported `crappy-functions 0` and `max-crap 5.0`.

## Interfaces and Dependencies

The public interface must remain:

    (hyperopen.views.staking-view/staking-view state)

The new internal namespace interfaces should be:

    hyperopen.views.staking.popovers/action-popover-layer
    hyperopen.views.staking.validators/validator-performance-panel
    hyperopen.views.staking.history/rewards-history-panel
    hyperopen.views.staking.history/action-history-panel

`/hyperopen/src/hyperopen/views/staking/shared.cljs` may expose shared formatter/render helpers needed by those namespaces. Keep all lower-level helpers private and do not change the current VM contract in `/hyperopen/src/hyperopen/views/staking/vm.cljs`.

Plan revision note: 2026-03-13 20:04Z - Initial active ExecPlan created after claiming `hyperopen-y00w` and confirming the current staking view hotspots, test gaps, and baseline validation blocker.
Plan revision note: 2026-03-13 20:20Z - Recorded the completed namespace split, widened public staking view coverage, passing repository gates, refreshed coverage, and final CRAP reports showing zero hotspots in all touched staking view namespaces.
