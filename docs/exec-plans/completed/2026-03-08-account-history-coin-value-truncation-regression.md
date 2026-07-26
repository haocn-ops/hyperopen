# Restore Account History Coin and Value Width on Desktop

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Related `bd` issue: `hyperopen-uvr`.

## Purpose / Big Picture

After this change, the trade page `Order History` and `Trade History` desktop tables will again use the available table width to show full coin labels and history values instead of clipping them inside narrow fixed columns. A user should be able to open either tab on a wide desktop trade layout and read symbols like `SILVER` plus value cells such as `0.52 SILVER` without unnecessary truncation when there is visible free space in the table.

## Progress

- [x] 2026-03-08 21:49Z: Created and claimed `bd` issue `hyperopen-uvr`.
- [x] 2026-03-08 21:50Z: Traced the regression to `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs` and `/hyperopen/src/hyperopen/views/account_info/tabs/trade_history.cljs`.
- [x] 2026-03-08 21:52Z: Updated the two desktop history grids so they use flexible `minmax(..., fr)` tracks instead of overly narrow fixed pixel columns, removed unnecessary desktop coin truncation, and kept mobile truncation behavior unchanged.
- [x] 2026-03-08 21:53Z: Added regression tests that prove the desktop history grids use flexible track templates, that the desktop coin labels do not carry truncation classes, and that trade-history desktop value cells stay non-wrapping.
- [x] 2026-03-08 21:55Z: Ran `npm test`, `npm run check`, and `npm run test:websocket` successfully.
- [x] 2026-03-08 21:56Z: Closed `hyperopen-uvr` as completed and prepared this ExecPlan to move to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The regression is not in the shared table wrapper; it comes from tab-specific grid templates in the order-history and trade-history desktop tables.
  Evidence: `/hyperopen/src/hyperopen/views/account_info/table.cljs` only owns vertical scrolling, while the history tabs define their own `grid-cols-[...]` strings with narrow fixed widths such as `90px` for the `Coin` column.

- Observation: One existing order-history test encoded the old implementation detail by searching for the `truncate` class directly, so the test had to be updated after the non-truncating desktop behavior was restored.
  Evidence: `order-history-coin-labels-are-bold-and-side-colored-test` initially failed even though the new render tree contained the correct text and styling.

## Decision Log

- Decision: Fix the regression by widening the desktop history grid tracks and removing desktop coin truncation instead of adding more overflow wrappers.
  Rationale: the screenshots show unused horizontal room inside the existing table surface, so the right fix is to let the tracks consume that space rather than introducing scroll for content that should already fit.
  Date/Author: 2026-03-08 / Codex

- Decision: Keep the mobile history cards unchanged and scope the new non-truncating behavior to desktop rows only.
  Rationale: mobile screens are genuinely space-constrained, while the reported regression is specifically about desktop tables leaving unused room.
  Date/Author: 2026-03-08 / Codex

## Outcomes & Retrospective

The desktop `Order History` and `Trade History` tables now use flexible track templates, so the coin column and adjacent value columns grow into the available room instead of staying pinned to narrow fixed widths. Desktop coin labels are no longer forcibly truncated, and trade-history value cells now stay on one line so formatted strings such as `0.52 SILVER` or `49.53 USDC` remain intact when there is room.

The regression is now covered by targeted tab tests that assert the new flexible grid templates, forbid desktop coin truncation, and preserve non-wrapping value cells. The main lesson is that these history tables should not use rigid pixel-only grids when the containing surface is intentionally wide and elastic.

## Context and Orientation

The `Order History` tab lives in `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs`. Its desktop table is rendered by `order-history-table`, which defines a private grid template string and row/header cell markup. The `Trade History` tab lives in `/hyperopen/src/hyperopen/views/account_info/tabs/trade_history.cljs`. Its desktop table is rendered by `trade-history-table-header` and `trade-history-table-row`, which currently inline the same fixed-pixel desktop grid string in two places.

Both tabs use `shared/coin-select-control` from `/hyperopen/src/hyperopen/views/account_info/shared.cljs` to render a clickable coin cell, and both tabs currently give the coin base label a `truncate` class in desktop rows. That was acceptable only when column width was intentionally constrained; it becomes a regression when the surrounding table still has free space. The regression tests for these tabs live in `/hyperopen/test/hyperopen/views/account_info/tabs/order_history_test.cljs` and `/hyperopen/test/hyperopen/views/account_info/tabs/trade_history_test.cljs`.

## Plan of Work

In `order_history.cljs`, replace the narrow fixed desktop grid template with a `minmax(..., fr)` template that keeps every existing column but allows the coin, direction, and value columns to grow with the table width. Remove the desktop `truncate` class from the coin base label and replace it with a non-truncating desktop treatment that preserves a stable one-line label.

In `trade_history.cljs`, extract the desktop grid template into a private constant and switch it to flexible `minmax(..., fr)` tracks. Use that constant for both the header and row grids. Remove the desktop `truncate` class from the coin base label there as well. Keep the mobile card behavior unchanged; mobile still needs truncation because screen width is genuinely constrained.

Then update the tab tests. Add assertions that the desktop grid class strings are the new flexible templates and not the old fixed-pixel ones, and assert that the desktop coin label nodes do not include `truncate`. Keep the existing content/value assertions intact so the tests continue to prove the rendered text itself is unchanged.

## Concrete Steps

From `/Users/barry/.codex/worktrees/6717/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs`.
2. Edit `/hyperopen/src/hyperopen/views/account_info/tabs/trade_history.cljs`.
3. Edit `/hyperopen/test/hyperopen/views/account_info/tabs/order_history_test.cljs`.
4. Edit `/hyperopen/test/hyperopen/views/account_info/tabs/trade_history_test.cljs`.
5. Run `npm test`.
6. Run `npm run check`.
7. Run `npm run test:websocket`.
8. Update this plan with actual outcomes, close `hyperopen-uvr`, and move the plan to `/hyperopen/docs/exec-plans/completed/`.

## Validation and Acceptance

Acceptance is met when all of the following are true:

1. The desktop `Order History` table uses a flexible grid template and does not truncate the rendered coin base label by default.
2. The desktop `Trade History` table uses a flexible grid template and does not truncate the rendered coin base label by default.
3. Existing values such as size, trade value, fee, and closed PNL still render with their full formatted strings in tests.
4. `npm test`, `npm run check`, and `npm run test:websocket` pass.

## Idempotence and Recovery

These changes are safe to reapply because they only affect view-layer grid classes and tests. If a revised grid template makes the table worse, revert only the grid template constant and desktop coin-label classes in the affected tab file and rerun the tab tests until the expected contract is restored.

## Artifacts and Notes

Initial evidence before the fix:

    Order History desktop grid:
      Coin column min width = 84px

    Trade History desktop grid:
      Coin column width = 90px

Those widths are too narrow for base symbol plus namespace chip in the screenshots, especially when the table still has free horizontal room.

Validation results after the fix:

    npm test
      Ran 2035 tests containing 10592 assertions.
      0 failures, 0 errors.

    npm run check
      [:app] Build completed. (467 files, 7 compiled, 0 warnings, 1.05s)
      [:portfolio-worker] Build completed. (58 files, 0 compiled, 0 warnings, 0.25s)
      [:test] Build completed. (770 files, 4 compiled, 0 warnings, 1.70s)

    npm run test:websocket
      Ran 339 tests containing 1852 assertions.
      0 failures, 0 errors.

## Interfaces and Dependencies

No public API changes are required. This work stays inside the desktop account-history view renderers and their existing tab tests.

Update note: revised after implementation to record the flexible desktop grid fix, the restored non-truncating desktop coin behavior, the exact validation results, and the completed `bd` close-out before moving the plan.
