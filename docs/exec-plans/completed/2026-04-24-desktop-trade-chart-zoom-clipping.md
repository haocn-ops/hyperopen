# Fix desktop trade chart clipping under browser zoom

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. It is self-contained so a future contributor can continue from this file without relying on conversation history.

Tracked issue: `hyperopen-bh1e` ("Fix desktop trade chart clipping under browser zoom").

## Purpose / Big Picture

Users who zoom in with Chrome or another desktop browser must still see a coherent trade chart. Browser zoom reduces the number of CSS pixels available inside the same physical browser window. Today the desktop `/trade` shell reaches a height combination where the chart canvas is taller than the chart row below the market strip, so the chart's lower price and volume region is clipped at the account-panel boundary. The fix should make the desktop trade shell either fit the chart into the available row or let the outer trade page scroll, but it must never let the account panel hide the chart canvas.

After this plan is implemented, opening `/trade` at normal desktop sizes and at zoom-equivalent desktop sizes such as `1285x535` and `1102x459` CSS pixels should show no internal clipping between the chart canvas and the lower account panel. The chart row, order book, and account panels should still preserve the governed geometry contract from `/hyperopen/docs/agent-guides/browser-qa.md`: chart and order-book bottoms flush to the account panel when they are in the same visible desktop shell, and no standard account tab should change the account panel's outer box.

## Progress

- [x] (2026-04-24 18:11Z) Reproduced the clipping with a local Playwright probe using zoom-equivalent CSS viewport sizes.
- [x] (2026-04-24 18:12Z) Created and claimed `bd` issue `hyperopen-bh1e` for the fix.
- [x] (2026-04-24 18:18Z) Wrote this active ExecPlan with root-cause evidence and the implementation path.
- [x] (2026-04-24 18:37Z) Confirmed the RED handoff coverage is materialized in `tools/playwright/test/trade-regressions.spec.mjs` and `test/hyperopen/views/trade_view/layout_state_test.cljs`.
- [x] (2026-04-24 18:37Z) Implemented the layout sizing change in `src/hyperopen/views/trade_view/layout_state.cljs` only, replacing the fixed `24rem` desktop chart row minimum with named CSS custom properties and a matching grid `min-height`.
- [x] (2026-04-24 18:48Z) Updated stale view-level layout assertions in `test/hyperopen/views/trade_view/layout_test.cljs` to pin the new content-derived grid contract.
- [x] (2026-04-24 18:53Z) Strengthened the Playwright zoom regression so every standard account tab is exercised at normal and zoom-equivalent desktop viewports, with order-book flush asserted at sidecar desktop widths.
- [x] (2026-04-24 18:55Z) Required validation passed: focused layout tests, focused Playwright regressions, `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-04-24 18:56Z) Governed browser QA passed with `npm run qa:design-ui -- --targets trade-route --manage-local-app`.

## Surprises & Discoveries

- Observation: The chart library is not the source of the measured clipping.
  Evidence: In the zoom-equivalent `1285x535` probe, `.tv-lightweight-charts` had `height: 360px` and the chart canvas host also had `height: 360px`, so `tvMinusHostHeight` was `0`. The library is autosizing to the host it is given.

- Observation: The desktop chart row minimum is smaller than the chart panel's own child minimums at compact desktop heights.
  Evidence: `src/hyperopen/views/trade_view/layout_state.cljs` defines the desktop chart row as `minmax(24rem, 1fr)`. At `1285x535`, that resolves to a `384px` chart panel. The market strip plus chart toolbar put the chart canvas top at `195px` while the account panel starts at `449px`, leaving only `254px` of visible canvas space before the account boundary. The chart canvas itself has `min-h-[360px]` in `src/hyperopen/views/trading_chart/core.cljs`, so its bottom lands at `555px`, `106px` below the account-panel top.

- Observation: The current deterministic Playwright geometry test does not catch this class of bug.
  Evidence: `tools/playwright/test/trade-regressions.spec.mjs` checks `chartFlushDelta`, `orderbookFlushDelta`, `lowerPanelShare`, and account height stability, but it does not compare `[data-parity-id="chart-canvas"]` or `.tv-lightweight-charts` against `[data-parity-id="trade-chart-panel"]` and `[data-parity-id="trade-account-tables-panel"]`.

- Observation: The bug is reproducible even without a true browser zoom API if the Playwright viewport is set to the CSS-pixel dimensions produced by browser zoom.
  Evidence: The user screenshot was `1928x803` physical pixels. At 150% browser zoom this is approximately `1285x535` CSS pixels. The local probe at `1285x535` reproduced the same visual failure and measured `accountTopMinusChartCanvasBottom: -106`.

- Observation: The existing full `npm test` suite contained two trade-layout assertions that pinned the removed `24rem` contract.
  Evidence: The first GREEN run failed in `test/hyperopen/views/trade_view/layout_test.cljs` because both `trade-view-root-and-right-column-layout-test` and `trade-view-layout-state-derives-visibility-and-grid-contracts-test` still expected `:grid-template-rows` to equal `minmax(24rem, 1fr) clamp(17rem, 32vh, 23rem)`. Those assertions now pin the CSS-variable row minimum and grid `min-height`.

- Observation: The focused Playwright regression needs account-tab coverage to avoid proving only the initial balances view.
  Evidence: The final zoom regression loops through `balances`, `positions`, `open-orders`, `twap`, `trade-history`, `funding-history`, and `order-history` at `1440x900`, `1280x800`, `1285x535`, and `1102x459`. It asserts the chart canvas remains inside the panel and clear of the account panel for every tab. At widths of at least `1280px`, it also asserts the order book remains flush with the account panel.

## Decision Log

- Decision: Treat this as a desktop shell sizing-contract bug, not a chart-rendering bug.
  Rationale: The chart library's autosized element matches the chart host height; the host is placed into a row that is too short for its minimum content below the desktop market strip and toolbar.
  Date/Author: 2026-04-24 / Codex

- Decision: Use zoom-equivalent CSS viewports in committed Playwright coverage instead of trying to drive Chrome's browser zoom UI.
  Rationale: Browser zoom changes the effective CSS viewport dimensions. Playwright viewport sizing gives deterministic, CI-safe coverage for that contract without depending on Chrome UI state or a nonstandard zoom control path.
  Date/Author: 2026-04-24 / Codex

- Decision: Preserve the chart's `360px` canvas minimum unless implementation evidence shows the desktop chart must shrink instead.
  Rationale: The user-visible bug is hidden chart content. Letting the outer trade shell scroll when the viewport is too short is safer than squeezing the chart into an unreadable height or changing chart rendering semantics. If the implementation proves that modest canvas shrinkage is necessary, record that decision here with before-and-after browser screenshots.
  Date/Author: 2026-04-24 / Codex

## Outcomes & Retrospective

GREEN implementation is in place in `src/hyperopen/views/trade_view/layout_state.cljs` and stays within the intended scope. `desktop-trade-grid-style` now exposes `:--trade-chart-canvas-min-height`, `:--trade-chart-toolbar-height`, `:--trade-chart-market-strip-height`, `:--trade-chart-row-min-height`, and `:--trade-account-panel-height`, and composes `:grid-template-rows` plus grid `:min-height` from those variables. No chart data, chart interop, websocket, or order-book logic changed.

The focused unit validation passed with `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.views.trade-view.layout-state-test --test=hyperopen.views.trade-view.layout-test`, reporting `12 tests`, `109 assertions`, `0 failures`, `0 errors`.

The focused Playwright zoom regression passed with `npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "desktop trade chart does not clip under zoom-equivalent viewports"`. The existing desktop shell geometry regression also passed on rerun with `npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "desktop trade shell keeps the chart dominant while account tabs stay geometry-stable"`.

The required gates passed: `npm run check`, `npm test` (`3386 tests`, `18452 assertions`, `0 failures`, `0 errors`), and `npm run test:websocket` (`461 tests`, `2798 assertions`, `0 failures`, `0 errors`). Governed browser QA passed with `npm run qa:design-ui -- --targets trade-route --manage-local-app`, producing run `design-review-2026-04-24T18-56-12-497Z-01954923` with review outcome `PASS`.

The read-only reviewer reported no findings. The only residual browser-QA notes are the standard design-review blind spots for hover, active, disabled, and loading states that are not present by default; they are not specific to this chart clipping fix.

## Context and Orientation

The working directory is `/Users/barry/.codex/worktrees/f610/hyperopen`.

The affected route is the desktop `/trade` shell. The top-level trade view is `src/hyperopen/views/trade_view.cljs`. It renders a `trade-root`, then a scroll shell, then a CSS grid containing these important panels:

- `[data-parity-id="trade-chart-panel"]`, rendered by `render-trade-chart-panel`.
- `[data-parity-id="trade-orderbook-panel"]`, rendered by `render-orderbook-panel-shell`.
- `[data-parity-id="trade-account-tables-panel"]`, rendered by `render-account-panel-shell`.
- `[data-parity-id="chart-canvas"]`, rendered by `src/hyperopen/views/trading_chart/core.cljs`.

The desktop grid row sizing is generated in `src/hyperopen/views/trade_view/layout_state.cljs` by `desktop-trade-grid-style`. At the time this plan was written it returned:

    {:grid-template-rows "minmax(24rem, 1fr) clamp(17rem, 32vh, 23rem)"}

That means the first desktop row, which contains the market strip and the chart, can collapse to `24rem` or `384px`. The chart canvas itself is rendered in `src/hyperopen/views/trading_chart/core.cljs` with class `min-h-[360px]`. The desktop market strip and chart toolbar sit above that canvas. At compact desktop heights, especially after browser zoom, the row can be only `384px` high while the children need roughly `85px` for the desktop market strip, `45px` for the toolbar, and `360px` for the chart canvas. That sum is about `490px`, so the canvas overflows and is clipped by `overflow-hidden` on the chart panel.

The existing Playwright helper `readTradeShellGeometry` in `tools/playwright/test/trade-regressions.spec.mjs` measures outer chart, order-book, and account panel rectangles. It should be extended or supplemented to measure the inner chart canvas and chart-library host as well.

The relevant project rules are:

- Use Playwright for deterministic browser regression coverage.
- Follow `/hyperopen/docs/BROWSER_TESTING.md` for browser-tool routing.
- Follow `/hyperopen/docs/FRONTEND.md` and `/hyperopen/docs/agent-guides/browser-qa.md` for UI QA.
- When Hiccup style maps use CSS custom properties, keys must be keywords such as `:--trade-chart-row-min-height`, not strings.

## Plan of Work

First add failing browser coverage for the actual hidden-content condition. In `tools/playwright/test/trade-regressions.spec.mjs`, extend `readTradeShellGeometry` or add a new helper named `readTradeChartCanvasGeometry`. The helper should read these rectangles in the page:

    [data-parity-id="trade-chart-panel"]
    [data-parity-id="chart-canvas"]
    [data-parity-id="chart-canvas"] .tv-lightweight-charts
    [data-parity-id="trade-account-tables-panel"]
    [data-parity-id="trade-scroll-shell"]

It should return `accountTopMinusChartCanvasBottom`, `chartPanelBottomMinusChartCanvasBottom`, `chartLibraryBottomMinusHostBottom`, and whether the outer scroll shell can scroll when content exceeds the visible viewport. Add a regression test that visits `/trade`, waits for the chart module to mount, and checks at least these CSS viewports: `1440x900`, `1280x800`, `1285x535`, and `1102x459`. The RED assertion should fail on the current code because `accountTopMinusChartCanvasBottom` is negative at `1285x535` and `1102x459`.

Then add focused unit coverage for the layout contract in `test/hyperopen/views/trade_view/layout_state_test.cljs` and, if needed, `test/hyperopen/views/trade_view/layout_test.cljs`. The unit test should prove that the desktop grid style exposes a chart row minimum derived from the chart panel's real minimum content: desktop market strip allowance, toolbar height, and chart canvas minimum. It should also prove that the desktop grid can grow taller than the viewport through a `min-height` style or an equivalent content-sized row contract, so the outer scroll shell handles the overflow instead of the chart panel clipping its children.

Implement the layout fix in `src/hyperopen/views/trade_view/layout_state.cljs`. The preferred implementation is to replace the `24rem` row minimum with a named content minimum, and to give the desktop grid a matching minimum height. Use semantic constants so the relationship is visible in code. For example, define values for the chart canvas minimum, chart toolbar height, and maximum observed desktop market strip height, then compose:

    chart row minimum = market strip allowance + toolbar height + chart canvas minimum
    grid minimum height = chart row minimum + account panel height

Express these through keyword style keys and CSS custom properties from `desktop-trade-grid-style`, for example:

    {:--trade-chart-canvas-min-height "22.5rem"
     :--trade-chart-toolbar-height "2.8125rem"
     :--trade-chart-market-strip-height "5.5rem"
     :--trade-chart-row-min-height "calc(var(--trade-chart-market-strip-height) + var(--trade-chart-toolbar-height) + var(--trade-chart-canvas-min-height))"
     :--trade-account-panel-height "clamp(17rem, 32vh, 23rem)"
     :grid-template-rows "minmax(var(--trade-chart-row-min-height), 1fr) var(--trade-account-panel-height)"
     :min-height "calc(var(--trade-chart-row-min-height) + var(--trade-account-panel-height))"}

The exact numbers may be adjusted during implementation if measured DOM evidence shows a tighter market strip allowance is safe. Do not make the chart canvas smaller than the existing `360px` minimum in the first implementation pass. If the grid grows taller than the visible shell at high zoom, keep the existing outer scroll shell as the overflow owner.

Update `src/hyperopen/views/trading_chart/core.cljs` only if it is useful to centralize the canvas minimum. If changed, keep the fallback equivalent to `360px`, for example by applying `:style {:min-height "var(--trade-chart-canvas-min-height, 360px)"}` on `[data-parity-id="chart-canvas"]` while preserving the existing `overflow-hidden`, `flex-1`, and `min-w-0` constraints. Update `test/hyperopen/views/trading_chart/core_test.cljs` if this changes the class or style contract.

After the first implementation passes the focused tests, run the Playwright regression again and inspect screenshots at the zoom-equivalent viewports. The expected behavior is that the chart canvas bottom is not below the account panel top. At very short desktop heights, it is acceptable for the lower account panel to require vertical scrolling, but it is not acceptable for the account panel to cover or hide the chart canvas.

## Concrete Steps

1. Confirm the issue and plan are present:

    Working directory: `/Users/barry/.codex/worktrees/f610/hyperopen`

    bd show hyperopen-bh1e --json
    test -f docs/exec-plans/active/2026-04-24-desktop-trade-chart-zoom-clipping.md

2. Write the RED Playwright test in `tools/playwright/test/trade-regressions.spec.mjs`. Add or extend a helper so the test can assert:

    Math.abs(geometry.chartLibraryBottomMinusHostBottom) <= 1
    geometry.accountTopMinusChartCanvasBottom >= -1
    geometry.chartPanelBottomMinusChartCanvasBottom >= -1

    When the viewport is too short to contain the full shell:
    geometry.scrollShellScrollHeight >= geometry.scrollShellClientHeight

3. Run the focused RED browser test and expect failure before the layout fix:

    npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "desktop trade chart does not clip under zoom-equivalent viewports"

    Expected current failure includes a negative `accountTopMinusChartCanvasBottom`, around `-106` at `1285x535`.

4. Write focused unit tests around `desktop-trade-grid-style` in `test/hyperopen/views/trade_view/layout_state_test.cljs`. The tests should assert that the desktop style no longer contains the literal row minimum `minmax(24rem, 1fr)` and does contain a content-derived chart row minimum. Run the focused test and expect it to fail before implementation:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.views.trade-view.layout-state-test

5. Implement the style contract in `src/hyperopen/views/trade_view/layout_state.cljs`. Keep the change scoped to desktop row sizing and grid minimum height. Do not touch websocket, candle data, chart series, or order-book data flow.

6. If the implementation uses a CSS custom property for the chart canvas minimum, update `src/hyperopen/views/trading_chart/core.cljs` and `test/hyperopen/views/trading_chart/core_test.cljs` so the chart canvas still has an explicit `360px` fallback and remains `overflow-hidden`.

7. Rerun the focused unit test:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.views.trade-view.layout-state-test --test=hyperopen.views.trade-view.layout-test --test=hyperopen.views.trading-chart.core-test

    Expected: the new layout assertions pass, and existing chart overflow tests still pass.

8. Rerun the focused Playwright test:

    npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "desktop trade chart does not clip under zoom-equivalent viewports"

    Expected: normal and zoom-equivalent desktop viewports pass. At `1285x535` and `1102x459`, the chart canvas is not hidden by the account panel. The page may have vertical scroll if the full shell cannot physically fit in the visible CSS viewport.

9. Run the existing desktop trade geometry regression to ensure the account-tab stability contract still holds:

    npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "desktop trade shell keeps the chart dominant while account tabs stay geometry-stable"

10. Run required repository gates because this is UI code:

    npm run check
    npm test
    npm run test:websocket

11. Run governed browser QA for the trade route:

    npm run qa:design-ui -- --targets trade-route --manage-local-app

    Record `PASS`, `FAIL`, or `BLOCKED` for visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf passes. Include DOM rect evidence for `1280` and `1440` widths, and account for the zoom-equivalent Playwright evidence separately because the design review widths do not cover browser zoom.

12. Clean up browser-inspection sessions before finishing:

    npm run browser:cleanup

13. Update this ExecPlan with validation output, screenshots or artifact paths, and the final decision if implementation required changing the preferred sizing approach.

## Validation and Acceptance

The work is accepted when all of these are true:

1. A committed Playwright regression fails on the old code because the chart canvas bottom extends below the account-panel top at a zoom-equivalent desktop viewport.

2. The same Playwright regression passes after the layout fix at `1440x900`, `1280x800`, `1285x535`, and `1102x459`.

3. At every tested desktop viewport, `.tv-lightweight-charts` remains within `[data-parity-id="chart-canvas"]`, and `[data-parity-id="chart-canvas"]` is not covered by `[data-parity-id="trade-account-tables-panel"]`.

4. If the full desktop shell is taller than the visible CSS viewport, scrolling belongs to `[data-role="trade-scroll-shell"]` or an existing outer shell, not to hidden overflow inside the chart row.

5. The existing account-tab geometry stability test still passes across the seven standard account tabs: `Balances`, `Positions`, `Open Orders`, `TWAP`, `Trade History`, `Funding History`, and `Order History`.

6. `npm run check`, `npm test`, and `npm run test:websocket` pass, or any failure is documented here with evidence that it is unrelated to the change.

7. Governed browser QA for `trade-route` is completed with explicit results for all six required passes and explicit cleanup confirmation.

## Idempotence and Recovery

The test and browser commands are safe to rerun. The Playwright command starts and stops its own browser. The design-review command manages local browser-inspection sessions when `--manage-local-app` is used, but still run `npm run browser:cleanup` before finishing.

If the first implementation causes the account panel to disappear at normal desktop sizes, revert only the new layout sizing edits and keep the RED tests. Then try the narrower alternative of keeping the account panel height unchanged while using `minmax(min-content, 1fr)` for the chart row and adding only a grid `min-height`. Do not revert unrelated user changes.

If the chart becomes unreadably short because the implementation reduces the canvas minimum, restore the `360px` chart canvas minimum and prefer outer page scrolling. The user-visible priority is that the chart is not internally clipped or covered by the account panel.

If Browser QA reports a design regression unrelated to zoom clipping, create a linked `bd` issue with `--deps discovered-from:hyperopen-bh1e` and record the issue id here instead of expanding this bug fix into broad visual redesign.

## Artifacts and Notes

Root-cause probe artifacts were captured under:

    /Users/barry/.codex/worktrees/f610/hyperopen/tmp/zoom-root-cause-probe/

Important files:

    measurements.json
    normal-1440x900.png
    zoom150-equiv-1285x535.png
    zoom175-equiv-1102x459.png
    short-desktop-1280x600.png

Key measurement summary from the current code:

    normal-1440x900:
      grid rows: 499px 288px
      chart panel height: 499px
      chart canvas height: 385px
      accountTopMinusChartCanvasBottom: 0

    zoom150-equiv-1285x535:
      grid rows: 384px 272px
      chart panel height: 384px
      chart canvas height: 360px
      accountTopMinusChartCanvasBottom: -106
      tvMinusHostHeight: 0

    zoom175-equiv-1102x459:
      grid rows: 384px 272px
      chart panel height: 384px
      chart canvas height: 360px
      accountTopMinusChartCanvasBottom: -90
      tvMinusHostHeight: 0

The negative `accountTopMinusChartCanvasBottom` values prove that the account panel begins before the chart canvas ends. The zero `tvMinusHostHeight` values prove that Lightweight Charts is matching the host size and is not the root cause.

Plan revision note, 2026-04-24 / Codex: Initial plan created after reproducing the bug locally and creating `bd` issue `hyperopen-bh1e`.

## Interfaces and Dependencies

`src/hyperopen/views/trade_view/layout_state.cljs` owns the desktop trade grid style. Keep any new constants private to this namespace unless another namespace needs them for tests.

`src/hyperopen/views/trading_chart/core.cljs` owns the chart canvas host. If this file changes, preserve the chart host attributes `data-parity-id="chart-canvas"`, `data-role="trading-chart-canvas"`, `role="region"`, `tabindex 0`, `overflow-hidden`, `flex-1`, and `min-w-0`.

`tools/playwright/test/trade-regressions.spec.mjs` owns deterministic browser regression coverage for `/trade`. Use existing helpers such as `visitRoute`, `waitForIdle`, and `selectAccountTab` rather than adding a second debug bridge.

The chart library is `lightweight-charts` through `src/hyperopen/views/trading_chart/utils/chart_interop.cljs` and `src/hyperopen/views/trading_chart/utils/chart_options.cljs`. This plan does not require chart-library changes because `base-chart-options` already sets `:autoSize true`, and the probe showed the library host matched its container.
