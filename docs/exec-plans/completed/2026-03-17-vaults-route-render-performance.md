# Vaults Route Render Performance

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, `/vaults` should stay visually stable while it hydrates and should stop doing avoidable route-local work on every render. A user should be able to open `/vaults`, see the list shell hold its shape while data loads, and get the same search, filter, sort, pagination, and navigation behavior with less hidden DOM work and less repeated list derivation.

## Progress

- [x] (2026-03-17 18:44Z) Created `bd` work item `hyperopen-r350` and claimed it for this route-local `/vaults` performance pass.
- [x] (2026-03-17 18:44Z) Re-read `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/WORK_TRACKING.md`, and the current `/vaults` list view and VM tests to align the implementation with repo workflow and existing regression coverage.
- [x] (2026-03-17 18:49Z) Implemented viewport-gated `/vaults` list rendering so `section-table` renders only the active desktop table or mobile card subtree, added loading-shell sizing to reduce layout movement during hydration, and fixed the label slug regex while wiring the new branch selection.
- [x] (2026-03-17 18:50Z) Added source-aware caching for parsed vault rows and the final list view-model, precomputed search and sort keys inside parsed rows, and memoized sparkline path generation so unchanged series reuse the same SVG path model across renders.
- [x] (2026-03-17 18:51Z) Extended `/vaults` view and VM regression tests with viewport-aware hidden-subtree assertions and cache-stability assertions for unrelated state changes.
- [x] (2026-03-17 18:59Z) Passed `npm test`, `npm run check`, `npm run test:websocket`, and `npm run build`, then captured a clean headless Lighthouse smoke for `http://localhost:8082/vaults` at `/hyperopen/tmp/lighthouse-20260317-vaults-route-followup/run1.json`.

## Surprises & Discoveries

- Observation: the current `/vaults` list view always constructs both the desktop table tree and the mobile card tree in the same render pass.
  Evidence: `/hyperopen/src/hyperopen/views/vaults/list_view.cljs` currently renders both the `md:block` table branch and the `md:hidden` mobile branch inside `section-table`.

- Observation: the current `/vaults` list VM rebuilds parsed rows, filtering, sorting, grouping, pagination, and TVL totals from scratch on each call.
  Evidence: `/hyperopen/src/hyperopen/views/vaults/vm.cljs` currently performs the full derivation inline inside `vault-list-vm` with no cache.

- Observation: the clean post-change release Lighthouse smoke still shows `/vaults` dominated by the vault payload rather than hidden subtree render duplication.
  Evidence: `/hyperopen/tmp/lighthouse-20260317-vaults-route-followup/run1.json` reports Performance `42`, TBT `2,600 ms`, `10` long tasks, and `14,611 KiB` transferred. That is materially better than the extension-contaminated trace's `14,971 ms` TBT and `20` long tasks, but the route still spends most of its budget on the large vault index response and downstream work it enables.

## Decision Log

- Decision: keep this pass route-local and do not redesign startup bootstrap or the vault data contract.
  Rationale: the user requested implementation of the route-local plan, and the strongest low-risk wins are the hidden subtree render cost, repeated list derivation, and list hydration stability inside `/vaults`.
  Date/Author: 2026-03-17 / Codex

- Decision: create a new tracked task instead of reusing an unrelated existing `bd` issue.
  Rationale: the ready queue did not contain an existing issue for this exact `/vaults` route render-performance pass, and the repository policy at the time used `bd` for work tracking.
  Date/Author: 2026-03-17 / Codex

- Decision: use the generated release artifact root on `http://localhost:8082` for the clean Lighthouse rerun instead of waiting for a separate dev/watch server to appear.
  Rationale: the workspace did not have a live server on port `8082`, the repository already documents `npx serve -s out/release-public -l 8082` as the release measurement loop, and the performance question was about route-local client cost rather than watch-mode behavior.
  Date/Author: 2026-03-17 / Codex

## Outcomes & Retrospective

This plan shipped the intended route-local changes. `/vaults` now renders only the active breakpoint-specific list subtree, keeps a steadier loading shell, caches parsed row derivation and the final list view model behind explicit invalidation inputs, and reuses memoized sparkline path models for unchanged series. Regression coverage now proves the hidden subtree is skipped on both desktop and mobile widths and that unrelated store churn does not rebuild the full list model.

The required repository gates all passed, and the clean release Lighthouse smoke is directionally better on CPU cost than the original noisy trace: TBT dropped from `14,971 ms` to `2,600 ms` and long tasks dropped from `20` to `10`. The route is still not healthy overall, because the release smoke reported Performance `42`, CLS `0.108`, and a `14,611 KiB` payload with pathological LCP/TTI. The remaining bottleneck is no longer the hidden desktop/mobile duplication or repeated route-local derivation; it is the size and downstream processing cost of the vault payload itself. Complexity decreased slightly overall because the render and derivation work is now partitioned behind explicit helpers and cache boundaries instead of being recomputed implicitly on every call. The likely next follow-up is payload-shaping or fetch-phase work outside this route-local pass, not more micro-optimizing inside the existing `/vaults` list view.

## Context and Orientation

The `/vaults` list route lives in `/hyperopen/src/hyperopen/views/vaults/list_view.cljs`. That file renders the route shell, the toolbar, and both the desktop table and mobile card variants for each list section. The list data is assembled in `/hyperopen/src/hyperopen/views/vaults/vm.cljs`. In this repository, a “view model” means a pure data map derived from application state that a view function can render directly. The current `vault-list-vm` function reads the raw vault rows and builds parsed rows, filtered rows, sorted rows, grouped sections, pagination state, and aggregate totals on every call.

The `/vaults` route is lazy-loaded, but its route shell still needs to remain stable when the module swaps in and when the vault data finishes loading. Existing `/vaults` regression coverage lives in `/hyperopen/test/hyperopen/views/vaults/list_view_test.cljs` and `/hyperopen/test/hyperopen/views/vaults/vm_test.cljs`. Existing viewport-aware hidden-subtree tests live in `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs` and provide a local pattern for simulating viewport width in CLJS tests.

The issue tracker source of truth for this task is `hyperopen-r350`.

## Plan of Work

Update `/hyperopen/src/hyperopen/views/vaults/list_view.cljs` first. Add a viewport-width helper keyed to the `md` breakpoint used by the existing Tailwind classes so the route can decide whether to render the desktop table or the mobile card list. Change `section-table` to accept the resolved breakpoint mode and render only the active subtree instead of both hidden branches. While updating the view, add stable markers for the mobile card branch so the new tests can prove the hidden subtree is not built. Also make the loading layout hold closer to the steady-state shape by using loading row counts and container sizing that match the user vault pagination path more closely.

Then update `/hyperopen/src/hyperopen/views/vaults/vm.cljs`. Split the derivation into a parsed-row cache and a list-model cache. The parsed-row cache should invalidate when the raw vault rows, user-equity map, wallet address, selected snapshot range, or current day bucket changes. The list-model cache should invalidate when the parsed rows change or when search, filters, sort, page size, page number, dropdown-open state, loading state, or error state changes. Preserve the current output shape so callers do not need to change. Also make the route-level sparkline path computation memoized so repeated renders reuse the same SVG path for unchanged series data.

After the code change, extend `/hyperopen/test/hyperopen/views/vaults/list_view_test.cljs` with viewport-aware tests proving desktop does not build mobile cards and mobile does not build the desktop table branch. Extend `/hyperopen/test/hyperopen/views/vaults/vm_test.cljs` with cache-stability tests that show unrelated state changes reuse the cached view model while relevant list inputs still invalidate it.

## Concrete Steps

Work from `/Users/barry/projects/hyperopen`.

Run the targeted test suite while iterating:

    npm test -- hyperopen.views.vaults.list-view-test hyperopen.views.vaults.vm-test

Run the required repo gates before finishing:

    npm run check
    npm test
    npm run test:websocket
    npm run build

Update `bd` status when the implementation is validated:

    bd close hyperopen-r350 --reason "Completed" --json

If validation fails, keep `hyperopen-r350` open and record the blocker in this document and in the handoff.

## Validation and Acceptance

Acceptance is behavioral. On desktop-width renders, `/vaults` should build the desktop table branch and not build the mobile card branch. On mobile-width renders, `/vaults` should build the mobile card branch and not build the desktop table branch. Search, filter, sort, pagination, and row navigation behavior must remain unchanged in the existing tests.

Acceptance is also structural. Repeated calls to `vault-list-vm` with unchanged list inputs should return the cached view-model object even if unrelated state changes elsewhere in the store. Calls that change list inputs such as search or raw row source must still produce a fresh view model.

The final repo gates must pass:

- `npm run check`
- `npm test`
- `npm run test:websocket`
- `npm run build`

## Idempotence and Recovery

This change is additive and safe to re-run. The new caches are derived-only and can be reset by reloading the CLJS runtime. If a validation command fails, fix the failing code or test and re-run the same command. Do not create new markdown TODO trackers; keep follow-up work in `bd` and keep this ExecPlan synchronized with the real state of the work.

## Artifacts and Notes

Initial tracking artifacts created for this work:

    bd issue: hyperopen-r350
    ExecPlan: /hyperopen/docs/exec-plans/active/2026-03-17-vaults-route-render-performance.md

## Interfaces and Dependencies

No user-facing API changes are planned. The main implementation surface is:

- `/hyperopen/src/hyperopen/views/vaults/list_view.cljs`
- `/hyperopen/src/hyperopen/views/vaults/vm.cljs`
- `/hyperopen/test/hyperopen/views/vaults/list_view_test.cljs`
- `/hyperopen/test/hyperopen/views/vaults/vm_test.cljs`

The viewport gating should follow the existing window-width pattern already used in `/hyperopen/src/hyperopen/views/trade_view.cljs` and the test helper style used in `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`.

Plan revision note: 2026-03-17 18:44Z - Created the active ExecPlan and linked the new `bd` task before code edits so the implementation can proceed under the repository’s required planning and tracking workflow.
