# Restore Trade Desktop Orderbook and Account Tables Layout

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Related `bd` issue: `hyperopen-0gx`.

## Purpose / Big Picture

After this change, the desktop trade page will again match the intended column boundaries: the order book will stop at the top edge of the balances and history surface instead of stretching to the bottom of the page, and the balances/positions/open-orders/history surface will span across the chart and order-book width up to the order-entry rail. The change will be visible on the extra-large desktop trade layout and proved by structural UI tests that fail if the grid contract regresses.

## Progress

- [x] 2026-03-08 21:37Z: Read `/hyperopen/ARCHITECTURE.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, `/hyperopen/docs/agent-guides/trading-ui-policy.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md`.
- [x] 2026-03-08 21:37Z: Created and claimed `bd` issue `hyperopen-0gx` for the regression fix.
- [x] 2026-03-08 21:38Z: Updated `/hyperopen/src/hyperopen/views/trade_view.cljs` so the extra-large grid keeps the order-entry rail full-height, limits the order book to the top row, and lets the account tables span the chart plus order-book columns.
- [x] 2026-03-08 21:38Z: Extended `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs` with layout-contract assertions that fail if the order book spans both rows again or if the account tables stop short of the order-entry rail.
- [x] 2026-03-08 21:40Z: Ran `npm test`, `npm run check`, and `npm run test:websocket` successfully.
- [x] 2026-03-08 21:40Z: Closed `bd` issue `hyperopen-0gx` as completed and prepared this ExecPlan to move to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The current extra-large order-book placement is caused entirely by grid classes in `/hyperopen/src/hyperopen/views/trade_view.cljs`, not by any internal order-book sizing logic.
  Evidence: the order-book panel carries `lg:row-start-2` and `xl:row-span-2`; on `xl`, `row-span-2` overrides the row start and makes the middle column fill both rows.

- Observation: The existing Node test workflow does not expose a built-in single-namespace filter in `package.json`; the fastest deterministic validation path was to run the full `npm test` suite first.
  Evidence: `/hyperopen/tools/generate-test-runner.mjs` generates one aggregate runner that invokes all discovered namespaces.

## Decision Log

- Decision: Fix the regression at the layout-owner level in `trade_view.cljs` instead of patching `l2_orderbook_view.cljs` or `account_info_view.cljs`.
  Rationale: the bug is about the desktop grid contract between sibling panels, so the owning page layout should define the correct row and column spans.
  Date/Author: 2026-03-08 / Codex

- Decision: Guard the fix with trade-view structural tests in `app_shell_spacing_test.cljs`.
  Rationale: the regression is a class-contract problem and can be detected deterministically from the rendered Hiccup tree without a browser-only test.
  Date/Author: 2026-03-08 / Codex

- Decision: Keep the fix scoped to the extra-large grid contract by changing only `xl` panel placement classes.
  Rationale: the `lg` two-column layout was already correct; changing only the `xl` row and column spans minimizes blast radius while restoring the intended desktop relationship between the order book, account tables, and order-entry rail.
  Date/Author: 2026-03-08 / Codex

## Outcomes & Retrospective

The trade desktop layout now matches the intended extra-large structure: the order book stops at the top row of the middle column, the account tables span the entire lower surface under both the chart and order book, and the order-entry rail remains full-height on the right. The regression is now covered by structural tests that assert the exact `xl` row and column span contract.

No follow-up work was required to land the fix. The main lesson is that responsive grid ownership belongs in `/hyperopen/src/hyperopen/views/trade_view.cljs`, and layout regressions of this kind are worth guarding with panel-level class-contract tests instead of relying on manual browser comparison alone.

## Context and Orientation

The desktop trade page layout is assembled in `/hyperopen/src/hyperopen/views/trade_view.cljs`. That file owns the responsive grid that places four sibling surfaces: the chart, the order book, the order-entry rail, and the account tables. The order book content itself lives in `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`, and the balances/positions/open-orders/history tabs live in `/hyperopen/src/hyperopen/views/account_info_view.cljs`, but those files do not decide how much desktop grid space each surface receives.

The regression appears only on the extra-large desktop layout. The current grid uses three columns on `xl`: a large chart column, a 280px order-book column, and a 320px order-entry column. The order-entry rail is intentionally full-height across both rows. The bug is that the order-book panel also spans both rows, leaving the account tables confined to only the chart column. The desired result is different: the order book should occupy only the top row of the middle column, while the account tables should span the bottom row across the first two columns.

The existing layout contract tests live in `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`. Those tests already inspect `data-parity-id` markers such as `trade-orderbook-panel`, `trade-order-entry-panel`, and `trade-account-tables-panel`, and they already verify some grid classes. This makes that file the right place to add a regression guard.

## Plan of Work

Edit `/hyperopen/src/hyperopen/views/trade_view.cljs` in the panel container section. Keep the current `lg` layout intact. For the `xl` layout, change the order-book wrapper so it explicitly starts in row 1 and no longer spans two rows. Keep its `xl` column placement in column 2. Leave the order-entry rail spanning both rows in column 3. Then update the account tables wrapper so that on `xl` it spans two columns, covering the full width under both the chart and order-book surfaces.

After the layout change, update `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`. Add assertions that the order-book panel includes `xl:row-start-1` and does not include `xl:row-span-2`, that the account tables panel includes `xl:col-span-2`, and that the order-entry panel still includes `xl:row-span-2`. Keep the test focused on structural layout ownership rather than styling trivia.

## Concrete Steps

From `/Users/barry/.codex/worktrees/6717/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/trade_view.cljs` to restore the intended `xl` row and column spans.
2. Edit `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs` to encode the layout contract.
3. Run `npm test`.
4. Run `npm run check`.
5. Run `npm run test:websocket`.
6. Update this plan with the actual results and move it to `/hyperopen/docs/exec-plans/completed/`.

## Validation and Acceptance

Acceptance is met when the rendered trade view structure proves all of the following:

1. The order-book wrapper uses the extra-large middle column but starts on the first row and does not span both rows.
2. The order-entry wrapper still spans both rows on extra-large screens.
3. The account tables wrapper spans two columns on the extra-large bottom row.
4. The focused trade layout test passes, and the repository quality gates `npm run check`, `npm test`, and `npm run test:websocket` all pass.

## Idempotence and Recovery

These edits are safe to reapply because they only change layout classes and tests. If a class change breaks the desktop layout, revert only the affected panel wrapper classes in `/hyperopen/src/hyperopen/views/trade_view.cljs` and rerun the focused layout test until the expected contract is restored.

## Artifacts and Notes

Initial evidence before the fix:

    /hyperopen/src/hyperopen/views/trade_view.cljs
      orderbook panel classes include:
        lg:row-start-2
        xl:row-span-2

This combination makes the order book consume both rows on `xl`, which is the regression being fixed.

Validation results after the fix:

    npm test
      Ran 2033 tests containing 10574 assertions.
      0 failures, 0 errors.

    npm run check
      [:app] Build completed. (467 files, 4 compiled, 0 warnings, 1.07s)
      [:portfolio-worker] Build completed. (58 files, 0 compiled, 0 warnings, 0.28s)
      [:test] Build completed. (770 files, 4 compiled, 0 warnings, 1.70s)

    npm run test:websocket
      Ran 339 tests containing 1852 assertions.
      0 failures, 0 errors.

## Interfaces and Dependencies

No public API changes are required. The change stays within the trade page view layout and its structural tests.

Update note: revised after implementation to record the successful `xl` grid fix, the exact validation commands that passed, and the completed `bd` close-out before moving the plan to the completed folder.
