# Fix /trade Browser-QA Route Regressions

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is governed by `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The tracked work item for this plan is `hyperopen-zw5t`; the `bd` issue is historical local tracker context for this plan.

## Purpose / Big Picture

After this change, the governed browser-QA run for `/trade` should no longer fail on the route-level layout-regression findings for `[data-parity-id='trade-root']` and `[data-parity-id='chart-panel']`, and it should no longer fail the `768` jank-perf pass because of layout shift caused by the same layout instability.

This is a trade-route containment task, not a general browser-QA cleanup. The current failure signatures point to the route layout and chart container sizing rather than the recently redesigned Trading Settings surface.

## Progress

- [x] (2026-03-19 16:59Z) Created and claimed `bd` issue `hyperopen-zw5t` for the `/trade` browser-QA follow-up.
- [x] (2026-03-19 17:01Z) Reviewed the latest governed browser artifact for `/trade` and confirmed the active failures are route-level overflow on `trade-root` and `chart-panel` plus `768` layout-shift jank.
- [x] (2026-03-19 17:06Z) Inspected the trade-route layout and chart container contracts and narrowed the likely cause to chart-panel overflow containment plus redundant `h-full` sizing in the chart stack.
- [x] (2026-03-19 17:08Z) Implemented containment fixes in the trade chart shell and panel wrappers, removing redundant `h-full` sizing from the chart canvas host and loading shell while preserving the `min-h-[360px]` chart-host contract.
- [x] (2026-03-19 17:09Z) Updated deterministic layout tests to assert the new overflow-hidden and `min-w-0` containment contract instead of the older oversized route expectations.
- [x] (2026-03-19 17:10Z) Moved the completed Trading Settings ExecPlan out of `docs/exec-plans/active/` so the docs gate reflects the current active work correctly.
- [x] (2026-03-19 17:11Z) Reran `npm test`, `npm run test:websocket`, `npm run check`, and governed browser QA. `/trade` now passes layout-regression and jank-perf at `375`, `768`, `1280`, and `1440`; the overall design-review run still fails on unrelated `/portfolio` mobile findings.

## Surprises & Discoveries

- Observation: the governed browser artifact for `/trade` does not report settings-panel selectors. It reports route-level selectors only: `[data-parity-id='trade-root']` and `[data-parity-id='chart-panel']`.
  Evidence: `/hyperopen/tmp/browser-inspection/design-review-2026-03-19T16-48-49-238Z-6c11409e/summary.json`.

- Observation: mobile-width `375` has no document-level horizontal overflow, but `chart-panel` still has local horizontal overflow (`scrollWidth 419`, `clientWidth 375`).
  Evidence: `/hyperopen/tmp/browser-inspection/design-review-2026-03-19T16-48-49-238Z-6c11409e/trade-route/review-375/probes/layout-audit.json`.

- Observation: the current trade grid hard-codes an `xl:min-h-[964px]` contract and desktop row minimums, which can exceed the `900px` review-height budgets at `1280` and `1440`.
  Evidence: `/hyperopen/src/hyperopen/views/trade_view.cljs` and `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`.

- Observation: the current chart stack still renders `chart-panel` with `h-full` and no explicit overflow containment, while the toolbar and chart host also contribute their own height constraints.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`.

- Observation: the chart toolbar’s closed absolute-positioned dropdown menus still contribute to the parent panel’s measured scroll width unless the panel boundary explicitly clips overflow.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` renders the chart-type and indicators menus as absolute descendants inside the toolbar, and the governed `375` audit recorded `chart-panel` `scrollWidth 419` versus `clientWidth 375` before the containment fix.

## Decision Log

- Decision: keep this task scoped to `/trade` route containers and chart layout only.
  Rationale: the current governed failures are localized to `trade-root` and `chart-panel`, and widening into unrelated shared-route cleanup would delay the highest-leverage fix.
  Date/Author: 2026-03-19 / Codex

- Decision: treat the overflow findings as a layout-sizing problem first, not as a browser-tool false positive.
  Rationale: the audit shows concrete `scrollHeight > clientHeight` and local `scrollWidth > clientWidth` evidence on the failing selectors across multiple review widths.
  Date/Author: 2026-03-19 / Codex

- Decision: fix the chart stack by tightening containment at `chart-panel` and removing redundant `h-full` sizing inside the chart and loading-shell hosts, rather than redesigning the `/trade` layout or changing panel visibility rules.
  Rationale: the failure signatures pointed to local chart overflow, and the smallest safe fix was to make the panel own its own bounds while preserving the existing route information architecture.
  Date/Author: 2026-03-19 / Codex

## Outcomes & Retrospective

This plan started from a known governed browser failure set rather than a user-visible functional bug. The implemented fix kept the product surface unchanged and tightened only the chart-panel containment path. The updated governed artifact is `/hyperopen/tmp/browser-inspection/design-review-2026-03-19T17-10-37-365Z-78dce821/`: `/trade` no longer reports any layout-regression or jank-perf issues at `375`, `768`, `1280`, or `1440`, while the overall run still fails because `/portfolio` retains an unrelated mobile overflow and jank issue at `375`. Interaction coverage for `/trade` is still marked `BLOCKED` in the governed run because additional hover, active, disabled, and loading states were not reachable by default.

## Context and Orientation

The current `/trade` route is assembled in `/hyperopen/src/hyperopen/views/trade_view.cljs`. That file owns the route root, the mobile-surface switching logic, the desktop grid layout, and the panel wrappers for the chart, orderbook, order entry, and account tables.

The current chart stack is rooted in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`. That file owns the chart toolbar, the chart panel container, and the chart canvas host sizing. The governed browser artifact shows that `chart-panel` overflows locally on both mobile and desktop review widths, which strongly suggests the fix will involve both the trade layout wrapper and the chart container contract.

The latest governed artifact for this task is `/hyperopen/tmp/browser-inspection/design-review-2026-03-19T16-48-49-238Z-6c11409e/`. At `375`, the route shows local vertical overflow on `trade-root` and local horizontal plus vertical overflow on `chart-panel`. At `768`, the route still overflows vertically and the jank pass records layout shift `0.134`. At `1280` and `1440`, the layout-regression pass still reports vertical overflow on both selectors.

The existing test coverage for this area lives primarily in `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs` and `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs`. Those tests already encode important grid and sizing expectations, including the current `xl:min-h-[964px]` contract and the chart host minimum-height contract.

## Proposed Product Scope

This bug fix does not change the product feature set on `/trade`. It should preserve the current trade-route information architecture while tightening the layout so the route stays within the governed review bounds.

The expected user-facing result is:

- the chart route surface should fit within the available route height without route-level vertical spill
- the chart toolbar should not create local horizontal overflow at mobile width
- the route should remain visually stable during the `768` focus-and-scroll QA pass

Non-goals for this task:

- no redesign of the order form, orderbook, or account tables
- no changes to `/portfolio` or `/vaults`
- no new settings rows or chart features

## Plan of Work

First, inspect the `/trade` layout and chart probes together and identify which height and overflow contracts are driving the audited selectors out of bounds. Pay special attention to the `xl:min-h-[964px]` grid contract, mobile chart sizing, and the chart toolbar width behavior.

Next, make the smallest route-level containment fix in `/hyperopen/src/hyperopen/views/trade_view.cljs` and `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`. The likely solution space includes reducing or removing oversized desktop minimums, adding proper `min-h-0` and overflow containment where the layout expects shrinking behavior, and constraining the chart toolbar or chart stack so it no longer causes local horizontal or vertical spill.

Then, update the deterministic tests that encode the old sizing contract. The tests should continue to protect the intended trade layout, but they should stop asserting values that are now known to create governed browser failures.

Finally, rerun the required repo gates and the governed design-review matrix for the changed `/trade` surfaces. The task is not done until the `/trade` route’s governed browser result is explicitly accounted for.

## Concrete Steps

1. Keep this plan current while the `/trade` bug fix is in progress.

2. Inspect and, if needed, update route layout code in:

    `/hyperopen/src/hyperopen/views/trade_view.cljs`
    `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`

3. Update deterministic tests in:

    `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`
    `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs`

4. Run required validation:

    `npm test`
    `npm run test:websocket`
    `npm run check`
    `npm run qa:design-ui -- --changed-files src/hyperopen/views/trade_view.cljs,src/hyperopen/views/trading_chart/core.cljs --manage-local-app`
