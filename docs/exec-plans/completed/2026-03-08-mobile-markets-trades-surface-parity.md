# Mobile Markets Trades Surface Parity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and must be maintained in accordance with that file.

## Purpose / Big Picture

The mobile trade route currently conflates two different intents. The top market tabs show `Chart`, `Order Book`, and `Trade`, but the third tab opens the order ticket instead of recent trades. That makes the middle market panel diverge from Hyperliquid’s mobile layout and forces users to leave the markets surface when they only wanted to inspect prints.

After this change, the top mobile tabs on `/trade` will show `Chart`, `Order Book`, and `Trades`. Selecting `Trades` will keep the user in the markets surface and render the recent trades list in the middle panel. The bottom `Trade` button will remain the only mobile control that opens the order ticket. A user can verify the behavior by opening the mobile trade view, tapping the top `Trades` tab, and seeing recent trades in the center panel while the bottom `Markets` nav stays active.

## Progress

- [x] (2026-03-08 22:46Z) Read `/hyperopen/AGENTS.md`, `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md`.
- [x] (2026-03-08 22:46Z) Traced the current implementation in `/hyperopen/src/hyperopen/views/trade_view.cljs`, `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`, `/hyperopen/src/hyperopen/views/footer_view.cljs`, `/hyperopen/src/hyperopen/trade/layout_actions.cljs`, and related tests.
- [x] (2026-03-08 22:46Z) Created and claimed `bd` issue `hyperopen-e6n` for this work.
- [x] (2026-03-08 22:49Z) Implemented the mobile surface split so top tabs control market-only surfaces (`:chart`, `:orderbook`, `:trades`) while the bottom nav remains the ticket owner.
- [x] (2026-03-08 22:50Z) Added regression coverage for trade view tab labels/state, footer nav highlighting, and mobile orderbook/trades rendering without duplicate tab rows.
- [x] (2026-03-08 22:52Z) Ran `npx shadow-cljs compile test` and `node out/test.js` successfully while iterating.
- [x] (2026-03-08 22:52Z) Ran required validation commands: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-03-08 22:52Z) Updated this plan with final outcomes and prepared it for move to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The repository already has a working recent-trades list in `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`, but it is only reachable through the nested order book tabs, not through the top mobile market tabs.
  Evidence: `orderbook-tabs-row` renders `Order Book` and `Trades`, and `trades-panel` already renders live trade rows.

- Observation: The bottom mobile nav already treats `:ticket` as the dedicated trading surface, so the bug is not in the bottom CTA. The bug is that the top tab bar also points at `:ticket`.
  Evidence: `/hyperopen/src/hyperopen/views/footer_view.cljs` dispatches `[:actions/select-trade-mobile-surface :ticket]` for the `Trade` button.

- Observation: The trade route reuses the same order book panel subtree for both mobile and desktop, with desktop visibility restored through responsive classes.
  Evidence: `/hyperopen/src/hyperopen/views/trade_view.cljs` applies `lg:block` to the order book panel even when the mobile branch is hidden.

- Observation: Because the desktop order book panel stays mounted in the same view tree, a global `:trades` override on the shared order book component would have changed desktop behavior too.
  Evidence: The final implementation had to render separate mobile and desktop wrappers inside the order book panel so the mobile `:active-tab-override` could stay `lg:hidden`.

## Decision Log

- Decision: Introduce a dedicated `:trades` mobile trade surface instead of reusing `:ticket`.
  Rationale: The user-visible problem is a routing/state ownership problem, not a missing trade list. A separate surface cleanly maps one top tab to one market subview and keeps the order ticket under the bottom CTA.
  Date/Author: 2026-03-08 / Codex

- Decision: Keep the existing order book component as the renderer for both mobile `Order Book` and mobile `Trades`, but add a render-time override so mobile top tabs can force the content without mutating shared desktop order book state.
  Rationale: This reuses the existing trade feed presentation, avoids duplicating layout code, and preserves desktop behavior where the nested orderbook/trades control still applies.
  Date/Author: 2026-03-08 / Codex

- Decision: Treat `:trades` as part of the bottom `Markets` bucket for active-state highlighting.
  Rationale: The recent-trades view is still a market-inspection surface. Highlighting `Trade` in that state would incorrectly imply that the order ticket is open.
  Date/Author: 2026-03-08 / Codex

- Decision: Render separate mobile and desktop order book component instances inside `/hyperopen/src/hyperopen/views/trade_view.cljs`.
  Rationale: Mobile needs a hidden internal tab row plus a forced active tab, while desktop must keep its existing nested orderbook/trades control and remain unaffected by the mobile top-tab state.
  Date/Author: 2026-03-08 / Codex

## Outcomes & Retrospective

Implemented outcome:

The mobile trade route now separates market inspection from order entry correctly. The top mobile tab row in `/hyperopen/src/hyperopen/views/trade_view.cljs` now reads `Chart`, `Order Book`, and `Trades`, and the third tab dispatches `:trades` instead of `:ticket`. The bottom mobile `Trade` nav in `/hyperopen/src/hyperopen/views/footer_view.cljs` remains the only control that selects `:ticket`.

To support this without disturbing desktop behavior, `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` now accepts an additive render contract: callers may suppress the internal orderbook tab row and force a temporary active tab. `/hyperopen/src/hyperopen/views/trade_view.cljs` uses that contract only for the mobile market panel, while desktop still renders the original nested `Order Book` / `Trades` control.

Regression coverage now proves the intended split:

- `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs` verifies that the top mobile tabs route to `:chart`, `:orderbook`, and `:trades`, and that the account tables remain visible for the `:trades` market surface while the ticket stays hidden.
- `/hyperopen/test/hyperopen/views/footer_view_test.cljs` verifies that `Markets` remains highlighted for `:trades`.
- `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs` verifies that the order book component can hide its internal tabs and render the trades panel directly.

Validation outcome:

- `npx shadow-cljs compile test`: pass.
- `node out/test.js`: pass (`2042` tests, `10636` assertions, `0` failures, `0` errors).
- `npm run check`: pass.
- `npm test`: pass (`2042` tests, `10636` assertions, `0` failures, `0` errors).
- `npm run test:websocket`: pass (`339` tests, `1852` assertions, `0` failures, `0` errors).

## Context and Orientation

`/hyperopen/src/hyperopen/views/trade_view.cljs` is the main trade route layout. On mobile it renders a top tab row, the center market/ticket panel, and the bottom account tables panel. The current `trade-mobile-surfaces` vector is the immediate source of the bug because it maps the third top tab to `:ticket` with the label `Trade`.

`/hyperopen/src/hyperopen/trade/layout_actions.cljs` defines `normalize-trade-mobile-surface` and the allowed mobile surface keywords. Any new mobile surface must be allowed here or it will fall back to `:chart`.

`/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` already contains two subviews: the order book depth view and the recent trades list. Today it always renders its own `Order Book`/`Trades` row. For mobile parity, the top trade-view tabs should become the owner of that choice, so this component needs a way to hide its internal tabs and accept a forced active tab on mobile while preserving desktop behavior.

`/hyperopen/src/hyperopen/views/footer_view.cljs` renders the mobile bottom nav. It decides whether `Markets`, `Trade`, or `Account` is active by reading `[:trade-ui :mobile-surface]`. That logic must recognize `:trades` as a markets surface.

Regression tests live in `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`, `/hyperopen/test/hyperopen/views/footer_view_test.cljs`, `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs`, `/hyperopen/test/hyperopen/orderbook/settings_test.cljs`, and `/hyperopen/test/hyperopen/state/app_defaults_test.cljs`. These tests already verify mobile tab labels, surface activation, and order book rendering conventions, so they are the right places to update.

## Plan of Work

First, update the mobile surface state model in `/hyperopen/src/hyperopen/trade/layout_actions.cljs` so `:trades` is a valid top-level mobile surface. Then revise `/hyperopen/src/hyperopen/views/trade_view.cljs` so the top mobile tab row advertises `Chart`, `Order Book`, and `Trades`, the market panel renders the order book for `:orderbook`, the trades list for `:trades`, and the ticket only for `:ticket`, and the account tables remain visible for market surfaces including `:trades`.

Next, extend `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` with a small render contract for mobile callers: an optional forced active tab and an option to hide the component’s internal tabs row. Use that contract from `/hyperopen/src/hyperopen/views/trade_view.cljs` on mobile only so the user sees a single tab system instead of stacked tab bars.

Then update `/hyperopen/src/hyperopen/views/footer_view.cljs` so the bottom `Markets` button stays active for `:chart`, `:orderbook`, and `:trades`. Leave the bottom `Trade` button mapped to `:ticket` exactly as-is.

Finally, update tests so they prove the user-visible behavior. The tests must show that the trade view’s top tab copy is pluralized to `Trades`, that the account panel stays visible on the `:trades` mobile surface, that the footer still points the bottom `Trade` CTA at `:ticket` while `Markets` remains active for `:trades`, and that the order book component can render a forced trades panel without showing its own nested tab row.

## Concrete Steps

From `/Users/barry/.codex/worktrees/6717/hyperopen`:

1. Edited `/hyperopen/src/hyperopen/trade/layout_actions.cljs` to add `:trades` to the normalized mobile market surface set.
2. Edited `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` to support an optional caller-supplied active tab and optional internal tab-row suppression, with new `data-role` hooks for regression tests.
3. Edited `/hyperopen/src/hyperopen/views/trade_view.cljs` to use `Chart`, `Order Book`, and `Trades` on mobile, dispatch `:trades` from the third top tab, render a mobile-only forced trades/order book panel, and keep the ticket under the bottom nav surface only.
4. Edited `/hyperopen/src/hyperopen/views/footer_view.cljs` so `Markets` is active for `:chart`, `:orderbook`, and `:trades`.
5. Updated `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`, `/hyperopen/test/hyperopen/views/footer_view_test.cljs`, and `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs`.
6. Ran iterative validation:
   `npx shadow-cljs compile test`
   `node out/test.js`
7. Ran required validation gates:
   `npm run check`
   `npm test`
   `npm run test:websocket`
8. Updated this plan with results, moved it to `/hyperopen/docs/exec-plans/completed/`, and closed `bd` issue `hyperopen-e6n`.

## Validation and Acceptance

Acceptance is behavioral:

1. On the mobile trade route, the top tab row reads `Chart`, `Order Book`, and `Trades`; it does not include `Trade`.
2. When `[:trade-ui :mobile-surface]` is `:trades`, the center panel shows recent trade rows with `Price`, `Size`, and `Time`, and the account tables panel remains visible below.
3. The mobile bottom nav still opens `:ticket` only from the bottom `Trade` button, and `Markets` is highlighted for `:chart`, `:orderbook`, and `:trades`.
4. Desktop order book behavior remains intact, including the nested `Order Book`/`Trades` control where it already existed.
5. Targeted regression tests pass, and the required project validation commands pass or are documented with blockers.

## Idempotence and Recovery

These edits are safe to reapply because they only refine view state normalization, render branching, and tests. If a regression appears, the safest rollback is to revert the new `:trades` mobile-surface branch in `/hyperopen/src/hyperopen/views/trade_view.cljs` and the accompanying normalization changes in `/hyperopen/src/hyperopen/trade/layout_actions.cljs`, then re-run the targeted tests to confirm the mobile view returns to its previous behavior.

## Artifacts and Notes

Relevant implementation files:

- `/hyperopen/src/hyperopen/trade/layout_actions.cljs`
- `/hyperopen/src/hyperopen/views/trade_view.cljs`
- `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`
- `/hyperopen/src/hyperopen/views/footer_view.cljs`

Relevant regression tests:

- `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`
- `/hyperopen/test/hyperopen/views/footer_view_test.cljs`
- `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs`

Tracked issue:

- `hyperopen-e6n`

Observed validation transcripts:

    $ npx shadow-cljs compile test
    [:test] Build completed. (770 files, 26 compiled, 0 warnings, 2.72s)

    $ node out/test.js
    Ran 2042 tests containing 10636 assertions.
    0 failures, 0 errors.

    $ npm run test:websocket
    Ran 339 tests containing 1852 assertions.
    0 failures, 0 errors.

## Interfaces and Dependencies

No external dependencies are required. The new internal render contract for `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` should remain additive and optional so existing callers keep working. At the end of the change, `hyperopen.trade.layout-actions/normalize-trade-mobile-surface` must accept `:trades`, and `hyperopen.views.l2-orderbook-view/l2-orderbook-view` must accept optional keys that let callers force the active tab and suppress the internal tabs row without changing desktop defaults.

Plan revision note: 2026-03-08 22:46Z - Created the ExecPlan after tracing the current mobile trade surface wiring and before implementation.
Plan revision note: 2026-03-08 22:52Z - Marked the work complete, recorded the mobile/desktop split decision, and added validation evidence from the required gates.
