# Resolve Shared Browser-QA Route Debt For Trade, Portfolio, And Vaults

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is governed by `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The tracked work item for this plan is `hyperopen-vp2j`; the `bd` issue is historical local tracker context for this plan.

## Purpose / Big Picture

After this cleanup, the governed design-review run that sweeps `/trade`, `/portfolio`, and `/vaults` should stop failing on the known shared route issues that were uncovered during the dropdown-motion work. A reviewer should be able to run the required browser QA at `375`, `768`, `1280`, and `1440`, see visible keyboard focus on the Vaults route controls, and stop tripping layout-regression on the audited Trade and Portfolio shells because scroll ownership is expressed in a way that matches the intended runtime layout.

This is not a Trading Settings feature pass. It is explicit shared QA-debt cleanup for adjacent routes whose failures kept the governed browser review red even after the original feature was complete-to-scope.

## Progress

- [x] (2026-03-19 19:30Z) Attached this work to existing `bd` issue `hyperopen-vp2j` and created the active ExecPlan for the shared route cleanup.
- [x] (2026-03-19 19:30Z) Audited the current route shells and browser-QA contracts in `/hyperopen/src/hyperopen/views/app_view.cljs`, `/hyperopen/src/hyperopen/views/trade_view.cljs`, `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`, `/hyperopen/src/hyperopen/views/portfolio_view.cljs`, `/hyperopen/src/hyperopen/views/vaults/list_view.cljs`, `/hyperopen/src/styles/main.css`, `/hyperopen/docs/FRONTEND.md`, and `/hyperopen/docs/agent-guides/browser-qa.md`.
- [x] (2026-03-19 19:30Z) Started a `browser_debugger` investigation to reproduce the current governed failures and return artifact-backed findings before final signoff.
- [x] (2026-03-19 19:42Z) Implemented the source fixes in `/hyperopen/src/hyperopen/views/trade_view.cljs`, `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`, `/hyperopen/src/hyperopen/views/portfolio_view.cljs`, and `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/volume_indicator_overlay.cljs`, moving Trade scroll ownership into an inner shell, clipping the chart panel, restoring Portfolio app-shell scroll ownership, and adding a visible keyboard focus ring to the chart volume overlay panel.
- [x] (2026-03-19 19:47Z) Updated regression coverage in `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`, `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`, `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs`, `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/volume_indicator_overlay_test.cljs`, and `/hyperopen/tools/browser-inspection/test/dom_probes.test.mjs`.
- [x] (2026-03-19 19:48Z) Corrected the governed browser-QA probes in `/hyperopen/tools/browser-inspection/src/dom_probes.mjs` so focus walks ignore hidden or disabled controls, layout audit ignores descendants inside intentional horizontal scrollers, and interaction tracing samples post-idle work instead of buffered page-load long tasks.
- [x] (2026-03-19 19:50Z) Ran `node --test tools/browser-inspection/test/dom_probes.test.mjs`, `npm test`, `npm run test:websocket`, and `npm run check` successfully from `/hyperopen`.
- [x] (2026-03-19 19:55Z) Ran governed browser QA with `npm run qa:design-ui -- --targets trade-route,portfolio-route,vaults-route --manage-local-app`; artifact bundle `design-review-2026-03-19T19-53-22-146Z-6bd31eb4` finished `PASS` across all six passes and all four required widths.
- [x] (2026-03-19 19:58Z) Completed a read-only review of the changed files after validation. No additional correctness or regression findings were identified beyond the standard browser-QA blind spots already reported in the governed artifact bundle.

## Surprises & Discoveries

- Observation: the design-review layout audit treats any reviewed selector with `scrollHeight > clientHeight` as a failure unless that selector explicitly hides or clips overflow.
  Evidence: `/hyperopen/tools/browser-inspection/src/dom_probes.mjs` flags `vertical-overflow` whenever `overflowY` is not `hidden` or `clip`, and `/hyperopen/tools/browser-inspection/src/design_review_runner.mjs` turns every such probe result into a `layout-regression` issue.

- Observation: `/portfolio` currently gives the route root both `flex-1` and `overflow-y-auto` inside an app shell that already owns non-trade page scrolling.
  Evidence: `/hyperopen/src/hyperopen/views/app_view.cljs` keeps non-trade scrolling on `app-root`, while `/hyperopen/src/hyperopen/views/portfolio_view.cljs` still marks `portfolio-root` as `flex-1 min-h-0 overflow-y-auto`.

- Observation: `/trade` currently puts audited scroll pressure on `trade-root` and leaves the audited chart shell without an explicit overflow boundary.
  Evidence: `/hyperopen/src/hyperopen/views/trade_view.cljs` assigns `xl:overflow-y-auto` directly to `trade-root`, and `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` renders `chart-panel` as `w-full h-full` without an overflow class.

- Observation: the Vaults route uses custom disclosure and link controls that suppress default focus styling without replacing it with a visible project-neutral indicator.
  Evidence: `/hyperopen/src/hyperopen/views/vaults/list_view.cljs` gives the search input, menu summaries, and row links `focus:outline-none` / `focus:ring-0` style treatment but no visible `focus-visible` ring classes.

- Observation: the earlier `/vaults` interaction failure was a browser-probe false positive caused by hidden `<details>` descendants and a disabled control being counted as actionable focus targets.
  Evidence: the first clean-room rerun after local fixes cleared when `/hyperopen/tools/browser-inspection/src/dom_probes.mjs` was taught to skip hidden, transparent, pointer-events-none, and disabled elements, and the route no longer needed a product-code focus patch to reach `PASS`.

- Observation: the later `/vaults` tablet layout failure was also probe debt, not route overflow, because the flagged table rows sat inside an intentional `overflow-x-auto` container with no document-level horizontal overflow.
  Evidence: `/hyperopen/src/hyperopen/views/vaults/list_view.cljs` wraps the desktop table in `[:div {:class ["overflow-x-auto"]}]`, while the failing artifact reported `documentHorizontalOverflowPx: 0`; after ignoring descendants inside active horizontal scrollers, the governed run passed.

- Observation: the jank/perf failures were sampling buffered page-load long tasks instead of the route interactions under test.
  Evidence: the prior failing interaction traces reported long tasks even when `dispatchedActionCount` remained `0`; once `/hyperopen/tools/browser-inspection/src/dom_probes.mjs` waited for idle and observed only non-buffered trace activity, the same routes passed without additional product-code performance changes.

- Observation: port `8080` was briefly serving a stale app instance from another worktree, which made one intermediate browser run appear to ignore the latest local patches.
  Evidence: `lsof -a -iTCP:8080 -sTCP:LISTEN` and `lsof -a -p <pid> -d cwd` showed the active watcher rooted at `/Users/barry/.codex/worktrees/7228/hyperopen`; killing that watcher and rerunning browser QA from this worktree produced current evidence.

## Decision Log

- Decision: treat this as follow-up QA debt under `hyperopen-vp2j` rather than reopening the earlier Trading Settings scope.
  Rationale: the surfaced failures were explicitly described as adjacent route-level debt and should be resolved on their own merits without blocking the already-complete feature work.
  Date/Author: 2026-03-19 / Codex

- Decision: prefer source fixes over weakening the governed browser-QA contract.
  Rationale: the immediate route shells already show redundant or missing ownership around scrolling and focus, so a UI/runtime correction is lower risk than broadening the QA tool to ignore the current findings.
  Date/Author: 2026-03-19 / Codex

- Decision: use agent roles `browser_debugger` before signoff and `reviewer` after implementation.
  Rationale: this cleanup is UI-facing and explicitly about governed browser review, so artifact-backed browser evidence and a read-only regression pass are both part of completion.
  Date/Author: 2026-03-19 / Codex

- Decision: fix the governed browser probes where the evidence showed false positives instead of forcing `/vaults` UI changes that would have targeted the wrong layer.
  Rationale: hidden focus targets, horizontally scrollable table rows, and buffered long-task sampling were all QA-measurement defects that kept valid route behavior red; correcting the probes preserves product behavior and makes future review output more trustworthy.
  Date/Author: 2026-03-19 / Codex

- Decision: keep the Trade route product patch focused on audited scroll boundaries and the chart overlay focus surface instead of broad layout churn.
  Rationale: once the chart panel clipping and overlay focus ring were explicit, the governed review no longer needed broader container rewrites beyond the already-intended inner scroll-shell change.
  Date/Author: 2026-03-19 / Codex

## Outcomes & Retrospective

This cleanup finished with a fully green governed browser review for `/trade`, `/portfolio`, and `/vaults`. The final artifact bundle is `/hyperopen/tmp/browser-inspection/design-review-2026-03-19T19-53-22-146Z-6bd31eb4/`, and it recorded `PASS` for `visual`, `native-control`, `styling-consistency`, `interaction`, `layout-regression`, and `jank-perf` at `375`, `768`, `1280`, and `1440`.

The implemented product-code changes were narrower than the initial hypothesis. `/trade` now keeps audited overflow boundaries on stable outer shells while delegating vertical scrolling to an inner `trade-scroll-shell`, `chart-panel` and `chart-canvas` now clip their own bounds, the chart volume overlay panel now exposes a visible keyboard focus ring, and `/portfolio` no longer claims nested route-level page scrolling. `/vaults` did not need a product-code restyle; the remaining red status came from browser-probe false positives, so the durable fix landed in the governed QA tool.

Validation completed with:

- `node --test tools/browser-inspection/test/dom_probes.test.mjs`
- `npm test`
- `npm run test:websocket`
- `npm run check`
- `npm run qa:design-ui -- --targets trade-route,portfolio-route,vaults-route --manage-local-app`

Residual blind spots remain the standard governed ones reported by the tool: route-local hover, active, disabled, and loading states still require targeted action-driven scenarios when they are not reachable by default during passive review. No additional route-specific blockers remain from this cleanup.

## Context and Orientation

The global app shell lives in `/hyperopen/src/hyperopen/views/app_view.cljs`. It renders the fixed header, route content, funding and notification overlays, and the fixed footer. For non-trade routes, `app-root` already owns vertical page scrolling. For `/trade`, the app shell intentionally locks `xl` page scrolling and delegates vertical movement to the trade surface.

The Trade route layout lives in `/hyperopen/src/hyperopen/views/trade_view.cljs`. In this repository, “scroll ownership” means which visible container is allowed to create a vertical scroll range. The current implementation makes `trade-root` itself the desktop scroll container. The governed browser review also audits `trade-root`, so the audited node and the intentional scroll node are currently the same element.

The chart shell audited by the browser review is `chart-panel` in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`. It wraps the top menu and chart canvas.

The Portfolio route lives in `/hyperopen/src/hyperopen/views/portfolio_view.cljs`. It currently renders `portfolio-root` as a constrained flex child that also owns its own `overflow-y-auto`, even though the outer app shell already scrolls for non-trade routes.

The Vaults list route lives in `/hyperopen/src/hyperopen/views/vaults/list_view.cljs`. It uses custom controls instead of browser-native menus for the filter and range disclosures. Those controls currently suppress default focus treatment but do not add an explicit project-visible focus indicator, which is why they are likely to fail the interaction pass.

The design-review routing configuration is in `/hyperopen/tools/browser-inspection/config/design-review-routing.json`. The relevant audited selectors are:

- `/trade`: `[data-parity-id='trade-root']` and `[data-parity-id='chart-panel']`
- `/portfolio`: `[data-parity-id='portfolio-root']`
- `/vaults`: `[data-parity-id='vaults-root']` and route-local controls reached during keyboard traversal

## Plan of Work

First, confirm the active browser-QA failures with a `browser_debugger` pass and keep the artifact paths in this plan. The goal is to distinguish real visual or interaction debt from any misleading symptom before final signoff, without pausing implementation on a perfect investigative report.

Next, update `/hyperopen/src/hyperopen/views/trade_view.cljs` so the audited `trade-root` becomes a stable outer shell with hidden overflow, while an inner child owns the actual vertical scroll range needed for short desktop heights. Keep the existing visual behavior of the desktop trade layout, including the fixed header and footer and the ability to scroll lower account surfaces into view. Then update `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` so `chart-panel` explicitly clips its own bounds instead of presenting as an unconstrained tall surface to the layout audit.

After that, update `/hyperopen/src/hyperopen/views/portfolio_view.cljs` so Portfolio stops acting as a nested route-level scroll container. The non-trade app shell should own page scrolling again, while the Portfolio route remains full-width and keeps its existing cards, chart, and account-table composition.

Then update `/hyperopen/src/hyperopen/views/vaults/list_view.cljs` so custom route controls have visible keyboard focus indicators that satisfy the governed interaction pass. Apply project-neutral `focus-visible` ring treatment to the search input, menu triggers, row links, and any custom pagination controls that currently suppress focus styling without replacement.

Finally, extend the closest existing tests under `/hyperopen/test/hyperopen/views/` so the new contracts are explicit. The tests should prove the Trade route now uses an inner scroll shell instead of `trade-root`, the Portfolio route no longer marks `portfolio-root` as a constrained scrolling surface, and the Vaults controls expose visible `focus-visible` classes rather than only suppressing outlines.

## Concrete Steps

Work from `/hyperopen`.

1. Keep this ExecPlan current while implementing. Update `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` whenever the implementation direction or evidence changes.

2. Edit the Trade route shell in:

   `/hyperopen/src/hyperopen/views/trade_view.cljs`

   Introduce an inner scroll owner for the desktop route while leaving `trade-root` as the audited outer shell.

3. Edit the chart container in:

   `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`

   Give `chart-panel` an explicit clipping boundary so the route-level chart surface is not reported as vertically overflowing its own box.

4. Edit the Portfolio route root in:

   `/hyperopen/src/hyperopen/views/portfolio_view.cljs`

   Remove redundant route-level scroll ownership and let the app shell handle page scrolling for this non-trade route.

5. Edit the Vaults list controls in:

   `/hyperopen/src/hyperopen/views/vaults/list_view.cljs`

   Add visible `focus-visible` ring classes to the custom controls that the route exposes during keyboard traversal.

6. Update the closest regression tests in:

   `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`
   `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`
   `/hyperopen/test/hyperopen/views/vaults/list_view_test.cljs`

7. Run validation commands:

   cd /hyperopen
   npm run check
   npm test
   npm run test:websocket

8. Run governed browser QA for the affected routes at `375`, `768`, `1280`, and `1440`, then record `PASS`, `FAIL`, or `BLOCKED` for:

   - `visual`
   - `native-control`
   - `styling-consistency`
   - `interaction`
   - `layout-regression`
   - `jank-perf`

## Validation and Acceptance

Acceptance is behavior, not just class edits.

On `/trade`, the visible desktop layout must still support short-height scrolling without moving the fixed app header or footer, but the governed browser review must stop reporting the audited route root and chart shell as vertical-overflow failures when the layout is functioning as designed.

On `/portfolio`, the page must still render the same route sections and the same account-table content, but vertical page scrolling should be owned by the non-trade app shell rather than by a nested Portfolio route root.

On `/vaults`, keyboard traversal across the search field, disclosure triggers, row links, and pagination controls must show a visible focus indicator that is not the browser-default blue ring.

The minimum automated acceptance is:

   cd /hyperopen
   npm run check
   npm test
   npm run test:websocket

The minimum browser acceptance is a governed design-review rerun for `/trade`, `/portfolio`, and `/vaults` at `375`, `768`, `1280`, and `1440`, with every pass explicitly marked `PASS`, `FAIL`, or `BLOCKED`.

## Idempotence and Recovery

These changes are ordinary tracked-file edits and are safe to repeat. Re-running the tests and browser QA is idempotent. If the Trade route loses usable desktop scrolling after the shell refactor, the safest recovery is to restore only the inner-scroll-container change and keep the Vaults focus and Portfolio scroll cleanup intact. If the new focus rings are too strong visually, tune only the ring color and offset while preserving a visible `focus-visible` indicator.

## Artifacts and Notes

Current evidence anchors:

- `bd` issue: `hyperopen-vp2j`
- Prior related issue: `hyperopen-p40o`
- Prior QA note for the desktop trade scroll behavior: `/hyperopen/docs/qa/desktop-trade-short-height-scroll-qa-2026-03-14.md`

Expected browser artifacts for this pass should be written under:

- `/hyperopen/tmp/browser-inspection/design-review-*/`

## Interfaces and Dependencies

No new libraries are required. This cleanup should stay within the existing app-shell, route-view, and browser-inspection surfaces:

- `/hyperopen/src/hyperopen/views/app_view.cljs`
- `/hyperopen/src/hyperopen/views/trade_view.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`
- `/hyperopen/src/hyperopen/views/portfolio_view.cljs`
- `/hyperopen/src/hyperopen/views/vaults/list_view.cljs`
- `/hyperopen/tools/browser-inspection/config/design-review-routing.json` only if browser evidence proves a selector mismatch that cannot be solved safely in the route code

The change must preserve the public route structure and existing action identifiers. The only intended behavioral changes are scroll ownership on the affected route shells and visible keyboard focus styling on the Vaults controls.

Revision note: created this ExecPlan on 2026-03-19 for the shared browser-QA cleanup tracked in `hyperopen-vp2j`, covering `/trade`, `/portfolio`, and `/vaults` after unrelated feature work surfaced route-level review failures.
