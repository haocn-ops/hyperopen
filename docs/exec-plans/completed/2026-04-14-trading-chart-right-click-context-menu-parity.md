# Implement Trading Chart Right-Click Context Menu Parity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

This plan is tracked by `bd` issue `hyperopen-mjsz`, "Implement trading chart right-click context menu parity".

## Purpose / Big Picture

After this change, right-clicking inside the trading chart will open a compact Hyperliquid-style menu instead of the browser or system context menu. A user will be able to reset the chart viewport and copy the price at the clicked chart location from the chart itself, without leaving the trading surface or seeing generic browser actions that do not belong to the product.

The user-visible outcome is easy to verify. On `/trade`, right-clicking inside the chart drawable area should open a dark anchored menu with exactly two actions, `Reset chart view` and `Copy price <value>`. Choosing `Reset chart view` should restore the same visible range that the existing chart reset control restores. Choosing `Copy price` should copy the resolved chart price and show lightweight confirmation. Right-clicking outside the chart should continue to behave normally.

## Progress

- [x] (2026-04-14 12:18Z) Audited the trading chart runtime, overlay infrastructure, and planning constraints in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`, `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`, `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/chart_navigation_overlay.cljs`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/MULTI_AGENT.md`.
- [x] (2026-04-14 12:19Z) Collected implementation guidance from sub-agents for chart architecture seams and Hyperliquid-style menu behavior.
- [x] (2026-04-14 12:18Z) Created `bd` issue `hyperopen-mjsz` for active lifecycle tracking.
- [x] (2026-04-14 12:22Z) Authored this active ExecPlan in `/hyperopen/docs/exec-plans/active/2026-04-14-trading-chart-right-click-context-menu-parity.md`.
- [x] (2026-04-14 15:53Z) Implemented a chart-local context menu overlay module in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs` and wired it through `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` and `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`.
- [x] (2026-04-14 16:00Z) Added deterministic tests for context-menu open/close behavior, keyboard support, copy/reset action wiring, stale-context cleanup, and overlay teardown in `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay_test.cljs`, plus integration assertions in `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop_test.cljs` and `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs`.
- [x] (2026-04-14 16:18Z) Added the smallest stable Playwright regression on `/trade` in `/hyperopen/tools/playwright/test/trade-regressions.spec.mjs` for custom right-click menu visibility and clean dismissal.
- [x] (2026-04-14 16:39Z) Passed required validation gates: `npm run check`, `npm test`, `npm run test:websocket`, and targeted Playwright verification for the new chart context-menu flow.

## Surprises & Discoveries

- Observation: The browser context menu appears today because Hyperopen does not intercept the chart surface `contextmenu` event anywhere in the trading chart path.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` mounts the chart host div without an `onContextMenu` handler, and `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/chart_navigation_overlay.cljs` only attaches `pointerenter`, `pointermove`, and `pointerleave` listeners.

- Observation: The trading chart already has a strong overlay pattern that attaches absolute-positioned DOM to the chart container and stores lifecycle state in a `WeakMap`.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/chart_navigation_overlay.cljs`, `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/volume_indicator_overlay.cljs`, `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs`, and `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` all follow that model.

- Observation: Hyperopen already has a single normalized chart reset path that preserves visible-range bookkeeping and should be reused instead of duplicated.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` routes overlay reset behavior through `reset-visible-range!` and `mark-visible-range-interaction!` via `sync-navigation-overlay!`.

- Observation: The chart stack already proves the price-from-pointer seam needed for `Copy price`.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` uses the main series `coordinateToPrice` API to convert chart-space pointer coordinates into price values.

- Observation: The Hyperliquid reference is intentionally constrained rather than feature-rich.
  Evidence: The supplied screenshot shows only two rows and one divider; the design review sub-agent independently recommended keeping viewport actions first, data-copy second, and deferring any larger chart toolbox behavior.

- Observation: The repo-level namespace-size gate needed explicit budget updates once the new overlay and adjacent chart tests landed.
  Evidence: `npm run check` initially failed on `dev.check-namespace-sizes` for `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs`, `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`, and `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs`; updating `/hyperopen/dev/namespace_size_exceptions.edn` resolved the policy failure without changing runtime behavior.

## Decision Log

- Decision: Implement the right-click menu as a chart-local overlay module parallel to the existing navigation overlay instead of a Replicant-managed global menu.
  Rationale: The current trading chart already attaches imperative overlay DOM during decoration passes, and keeping the menu in that same lifecycle minimizes state fan-out, avoids routing menu anchor state through app data, and stays aligned with existing chart-specific cleanup behavior.
  Date/Author: 2026-04-14 / Codex

- Decision: Version 1 scope is desktop parity only: custom context menu on mouse right-click plus keyboard invocation (`Shift+F10` / context-menu key when chart is focused). Touch long-press behavior is explicitly deferred.
  Rationale: The user request is anchored to a desktop Hyperliquid screenshot. Shipping desktop parity first keeps the initial implementation small, testable, and visually correct without prematurely designing a mobile bottom sheet or long-press interaction contract.
  Date/Author: 2026-04-14 / Codex

- Decision: Reuse the existing viewport reset pathway rather than introducing a second reset implementation inside the menu overlay.
  Rationale: Chart visible-range restore already carries persistence and interaction bookkeeping. A separate menu-specific reset path would increase drift risk and make future viewport bugs harder to reason about.
  Date/Author: 2026-04-14 / Codex

- Decision: Keep the first menu to two items only: `Reset chart view` and `Copy price <value>`, with `Copy price` disabled when no meaningful price can be resolved.
  Rationale: This matches the Hyperliquid reference, reduces QA surface, and prevents the first release from turning into a generic chart toolbox with unclear prioritization.
  Date/Author: 2026-04-14 / Codex

- Decision: Resolve the copy value from chart context in priority order: clicked crosshair-equivalent price from pointer coordinates first, then the last visible main-series price as fallback.
  Rationale: Hyperopen does not currently store a dedicated right-click price in application state. Pointer-to-price conversion already exists, while last visible price is a safe fallback that avoids presenting an empty label for common cases.
  Date/Author: 2026-04-14 / Codex

## Outcomes & Retrospective

The implementation shipped as planned. Right-clicking the trading chart now suppresses the browser menu and opens a chart-local custom menu with `Reset chart view` and `Copy price <value>`, with keyboard invocation and dismissal support included for the focused chart host. The reset action reuses the existing visible-range reset path, and the copy action resolves a clicked price from `coordinateToPrice` with a fallback to the latest candle-derived value before using the browser clipboard.

The implementation stayed within the existing chart-overlay architecture rather than broadening app state. The main tradeoff was adding a relatively large new overlay namespace and a small increase to existing chart test/core namespace budgets; both were recorded in `/hyperopen/dev/namespace_size_exceptions.edn` so the repo policy remains explicit and reviewable.

Validation passed across local deterministic coverage and browser verification. Successful commands were `npm test`, `npx playwright test tools/playwright/test/trade-regressions.spec.mjs --headed --workers=1 --grep "trade chart right-click opens the custom context menu"`, `npm run test:websocket`, and `npm run check`.

## Context and Orientation

The trading chart surface is rendered in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`. The `chart-canvas` function creates the chart host DOM node and installs a Replicant `:replicant/on-render` lifecycle handler. That lifecycle delegates to `mount-chart!`, `update-chart!`, and `unmount-chart!`, which own chart creation, updates, and teardown.

Hyperopen uses Lightweight Charts as the JavaScript chart engine. The interop boundary lives in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`. That namespace exports chart construction, series updates, legend management, overlay synchronization, and visible-range helpers. The chart handle returned from `create-chart-with-volume-and-series!` or `create-chart-with-indicators!` contains the `chart` object plus series references such as `mainSeries`.

The most relevant existing overlay is `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/chart_navigation_overlay.cljs`. It mounts chart-local DOM into the chart container, keeps per-chart state in a `WeakMap`, attaches container and document listeners, and exposes `sync-chart-navigation-overlay!` plus `clear-chart-navigation-overlay!`. The volume indicator and order/position overlays follow the same pattern. This plan intentionally reuses that architecture for the new context menu overlay.

The term "chart-local overlay" in this plan means imperative DOM owned by the chart runtime, attached inside the chart container, and cleaned up when the chart updates or unmounts. The term "visible range" means the time window currently shown on the chart. The term "drawable area" means the chart surface where price candles and crosshair interactions occur, not the rest of the page.

The current browser menu bug is simple: Hyperopen never suppresses `contextmenu` on the chart surface, so the browser default wins. The requested behavior is not a global right-click override. It only applies inside the trading chart interaction layer.

## Plan of Work

Start by introducing a new chart overlay module at `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs`. Follow the same structural pattern used by `chart_navigation_overlay.cljs`: a `WeakMap` sidecar for per-chart state, helper functions to create and tear down an overlay root element, chart-container-relative positioning, and explicit document listener cleanup. This module should own all menu DOM, event listeners, keyboard navigation, and clipboard feedback state for the chart menu.

Add thin exports in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` named `sync-chart-context-menu-overlay!` and `clear-chart-context-menu-overlay!`. This keeps the chart core working through the established interop boundary instead of directly reaching into a new overlay namespace. The sync function should accept the chart handle, chart container, candle data, and explicit callbacks or dependencies needed for reset and copy behavior.

Wire the overlay from `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`. Extend `apply-chart-decorations!` so the context menu overlay is synchronized alongside navigation, volume, and order overlays. Extend the chart runtime setup and teardown path so the new overlay is cleared on unmount and on relevant chart changes. Reuse the existing `reset-visible-range!` function by passing it into the overlay rather than reimplementing viewport restore logic inside the overlay module.

Within the new overlay, intercept `contextmenu` on the chart container only when the event originates from the chart interaction surface. Call `preventDefault` there so the browser menu does not appear over the chart. Resolve the anchor point from the event coordinates, then compute the menu placement relative to the container bounds. Placement must flip left and upward when the preferred position would overflow the chart bounds. Avoid clipping the menu under the pointer. The menu should not render outside the chart host.

Implement only two rows. The first row is `Reset chart view` and uses the same callback path already used by the existing chart reset control. The second row is `Copy price <value>`. Resolve `<value>` from the main series using pointer Y-coordinate to price conversion at right-click time. If that resolution fails, fall back to the last visible main-series price derived from the current candle set or chart context. If both resolution paths fail, render the row disabled as `Copy price --` and prevent activation.

Keep visual design deliberately restrained. Reuse the chart overlay visual language already present in the repo: dark surface, subtle border, compact spacing, one divider, and no extra chart utilities. The row hover state and keyboard focus state should match. Use `role="menu"` on the container and `role="menuitem"` on the items. When the menu opens from keyboard, move focus to the first enabled item. Support `ArrowUp`, `ArrowDown`, `Enter`, `Space`, and `Escape`, and return focus to the chart host when the menu closes.

For close behavior, dismiss the menu on outside pointer down, `Escape`, successful action selection, symbol or timeframe switch that causes the chart decoration pass to rerun, and chart navigation gestures that make the open menu stale. The menu must not remain orphaned after chart teardown, asset change, or page navigation.

For copy feedback, keep the first version local to the menu overlay. The simplest acceptable behavior is a temporary label swap from `Copy price <value>` to `Copied` for a short window, after which the normal label returns. This avoids broadening the scope into a shared toast or global clipboard runtime unless the implementation discovers a clean reuse path.

Testing should cover both pure logic and lifecycle behavior. Add a new deterministic unit test file at `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay_test.cljs` for menu rendering, price-label resolution, disabled state, position flipping, keyboard navigation, and cleanup behavior using the fake DOM helpers already present under `/hyperopen/test/hyperopen/views/trading_chart/test_support/fake_dom.cljs`. Expand `/hyperopen/test/shims/lightweight_charts_stub.cjs` only as needed to support the specific chart methods used by the overlay. Add targeted integration assertions in `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs` or `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop_test.cljs` so the core chart decoration lifecycle proves the new overlay is synchronized and cleared correctly.

Once the local interaction is stable, add the smallest Playwright regression in `/hyperopen/tools/playwright/test/trade-regressions.spec.mjs`. It only needs to prove that right-clicking the chart on `/trade` opens the custom menu, shows the expected action labels, and closes cleanly. Do not broaden Playwright coverage into deep clipboard assertions unless the app exposes a deterministic bridge for clipboard observation in the existing test harness.

## Concrete Steps

All commands run from `/Users/barry/.codex/worktrees/0a1a/hyperopen`.

1. Inspect the existing chart overlay seams before editing.

    rg -n "apply-chart-decorations!|mount-chart!|unmount-chart!|reset-visible-range!" src/hyperopen/views/trading_chart/core.cljs
    rg -n "sync-chart-navigation-overlay|clear-chart-navigation-overlay|coordinateToPrice" src/hyperopen/views/trading_chart/utils/chart_interop*

2. Implement the new overlay module and interop exports.

    rg -n "WeakMap|ensure-overlay-root|attach-document-listeners|clear-.*overlay" src/hyperopen/views/trading_chart/utils/chart_interop/chart_navigation_overlay.cljs

3. Add deterministic unit and integration coverage.

    npm test -- chart_context_menu_overlay
    npm test -- trading_chart/core

4. Run the smallest stable Playwright verification once the flow is deterministic.

    npm run test:playwright:headed -- trade-regressions.spec.mjs

5. Run required repository gates before concluding implementation.

    npm run check
    npm test
    npm run test:websocket

If the local Playwright command format differs from the current runner contract at implementation time, prefer the smallest command endorsed by `/hyperopen/docs/BROWSER_TESTING.md` and record the exact command used in this plan.

## Validation and Acceptance

This change is accepted when all of the following are true:

1. On `/trade`, right-clicking inside the chart drawable area suppresses the browser or system context menu and opens a custom menu anchored near the pointer.
2. The menu contains exactly two rows in this order: `Reset chart view`, divider, `Copy price <value-or-placeholder>`.
3. `Reset chart view` triggers the same viewport restore path as the existing chart navigation reset control and does not introduce a separate reset implementation.
4. `Copy price` copies the resolved chart price when available and provides immediate confirmation; when no meaningful price exists, the row is visibly disabled and does not activate.
5. The menu stays within chart bounds by flipping left or upward when needed.
6. The menu closes on outside click, `Escape`, successful action selection, chart teardown, and stale-chart transitions such as symbol or timeframe changes.
7. Keyboard invocation and navigation work when the chart host is focused: `Shift+F10` or context-menu key opens the menu, arrow keys change focus, `Enter` or `Space` activates, and `Escape` closes while restoring focus to the chart.
8. Deterministic tests cover menu lifecycle and action behavior, and the smallest relevant Playwright check passes.
9. Required repository gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

Manual verification should use the local trade route. Open `/trade`, right-click near the center of the chart, then right-click near the right and bottom edges to confirm menu flipping. Exercise both actions, then switch timeframe or active asset and confirm any open menu disappears cleanly.

## Idempotence and Recovery

The implementation is additive and safe to re-run. The new overlay module should clean up all attached document and container listeners on every sync replacement and on chart unmount. If a partial implementation leaves the browser context menu suppressed but fails to render the custom menu, recovery is straightforward: remove or disable the `contextmenu` interception in the new overlay until rendering is fixed. If the copy pathway proves unstable, ship `Reset chart view` only behind the same menu shell and leave `Copy price` disabled rather than widening the scope into broader state plumbing.

## Artifacts and Notes

Relevant file anchors for implementation:

- `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/runtime_state.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/chart_navigation_overlay.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs`
- `/hyperopen/test/hyperopen/views/trading_chart/test_support/fake_dom.cljs`
- `/hyperopen/test/shims/lightweight_charts_stub.cjs`
- `/hyperopen/tools/playwright/test/trade-regressions.spec.mjs`

Sub-agent design guidance captured in this plan:

- Keep the menu compact and chart-native, not a generic browser replacement.
- Keep viewport actions before data-copy actions.
- Disable `Copy price` when price resolution fails instead of hiding it.
- Defer mobile long-press or bottom-sheet behavior to a follow-up issue after desktop parity is proven.

## Interfaces and Dependencies

New internal interface to add through the chart interop boundary:

- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`
  - `sync-chart-context-menu-overlay!`
  - `clear-chart-context-menu-overlay!`

New internal module expected:

- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs`

Dependencies and constraints that must remain true:

- Keep websocket behavior pure and unchanged. This feature is view and interaction work only.
- Preserve public chart actions and chart-option APIs; this feature should not require new route or websocket contracts.
- Reuse the existing chart reset behavior from `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`.
- Follow `/hyperopen/docs/BROWSER_TESTING.md` by adding committed Playwright coverage only after the local flow is deterministic.
- Do not leave Browser MCP or browser-inspection sessions running if exploratory browser work is used during implementation.

Plan revision note: 2026-04-14 12:22Z - Initial plan created after chart-runtime audit, Hyperliquid screenshot review, sub-agent exploration, and creation of `bd` issue `hyperopen-mjsz`.
Plan revision note: 2026-04-14 16:39Z - Implementation completed, tests added, Playwright regression stabilized, and validation results plus namespace-size policy updates recorded.
