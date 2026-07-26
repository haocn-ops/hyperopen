# Split Mobile Order Book Layout

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and must be maintained in accordance with that file.

## Purpose / Big Picture

The mobile trade surface already keeps the account tables visible below the market surface, but the order book itself still renders with the desktop ladder layout. After this change, selecting the mobile `Order Book` surface will show a compact split-book presentation like the reference screenshot: bids on the left, asks on the right, with a shallower vertical footprint so the `Balances`, `Positions`, `Open Orders`, `TWAP`, and `Trade History` tabs remain visible below without changing desktop behavior.

You can verify this by opening the trade screen on a phone-width viewport, selecting `Order Book`, and confirming that the order book renders as a side-by-side bid/ask ladder while the lower account info panel remains directly below it. On desktop-width viewports, the existing stacked order book layout must remain unchanged.

## Progress

- [x] (2026-03-08 22:07Z) Reviewed `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md` to capture the UI and interaction constraints.
- [x] (2026-03-08 22:07Z) Inspected `/hyperopen/src/hyperopen/views/trade_view.cljs`, `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`, `/hyperopen/src/hyperopen/views/account_info_view.cljs`, and the related tests to confirm the current mobile trade surface already keeps the account tables visible below chart/order book surfaces.
- [x] (2026-03-08 22:07Z) Created and claimed `bd` issue `hyperopen-7h1` for this feature.
- [x] (2026-03-08 22:14Z) Implemented a responsive mobile-only split order book layout in `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` while preserving the desktop ladder behind `lg` breakpoints.
- [x] (2026-03-08 22:14Z) Tightened the mobile order book panel height contract in `/hyperopen/src/hyperopen/views/trade_view.cljs` without changing the desktop grid layout.
- [x] (2026-03-08 22:15Z) Added regression coverage in `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs` and `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`.
- [x] (2026-03-08 22:16Z) Found and fixed a runtime regression caused by using `into` with three collection arguments in `/hyperopen/src/hyperopen/views/trade_view.cljs`; replaced the invalid class construction and reran the full test suite.
- [x] (2026-03-08 22:18Z) Ran `npm run check`, `npm test`, and `npm run test:websocket` successfully, updated this ExecPlan, and prepared `hyperopen-7h1` for closure.

## Surprises & Discoveries

- Observation: The mobile trade screen already renders the account tables below both `Chart` and `Order Book` surfaces.
  Evidence: `/hyperopen/src/hyperopen/views/trade_view.cljs` shows `trade-account-tables-panel` whenever `mobile-market-surface?` is true, and `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs` already asserts this contract.

- Observation: The current order book component has a single stacked depth-body implementation shared across breakpoints, so it cannot reproduce the reference split-book layout without a responsive render path.
  Evidence: `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` only defines one depth body with vertically stacked asks, spread, and bids in `l2-orderbook-panel`.

- Observation: The first implementation of the mobile height contract introduced a render-time exception because `into` was called with three collection arguments in `trade_view.cljs`.
  Evidence: Browser console and test failures both reported `Error: Key must be integer` at `/hyperopen/src/hyperopen/views/trade_view.cljs:83`; consolidating the class list into a single two-argument `into` call resolved the error.

- Observation: The typography guard rejects explicit sub-16px bracket utilities in view files, even when the visual intent is modest density.
  Evidence: `npm test` initially failed in `/hyperopen/test/hyperopen/views/typography_scale_test.cljs` for `text-[11px]` usages added to `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`; switching those labels and rows to `text-xs` resolved the policy failure.

## Decision Log

- Decision: Implement the split-book as a responsive branch inside `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` instead of adding a second mobile-only container in `/hyperopen/src/hyperopen/views/trade_view.cljs`.
  Rationale: This keeps the order book’s data shaping, controls, and websocket freshness logic in one place while allowing mobile-only presentation changes through responsive classes and small helper functions.
  Date/Author: 2026-03-08 / Codex

- Decision: Preserve the desktop stacked asks/spread/bids ladder exactly and add a new mobile presentation rather than replacing the existing markup.
  Rationale: The user explicitly asked for no desktop behavior change. A responsive branch minimizes risk by making the mobile layout additive.
  Date/Author: 2026-03-08 / Codex

- Decision: Show the top ten bid and ask levels in the mobile split-book view.
  Rationale: The compact mobile surface needs a bounded vertical footprint so the account tables remain visible below; ten paired levels matches the density of the reference screenshot while preserving the full desktop ladder.
  Date/Author: 2026-03-08 / Codex

- Decision: Keep the mobile split-book labels at `text-xs` instead of introducing custom 11px utilities.
  Rationale: This satisfies the repository typography policy while keeping the mobile order book compact enough for the desired layout.
  Date/Author: 2026-03-08 / Codex

## Outcomes & Retrospective

Implemented outcome:

- The mobile `Order Book` surface now renders a split-book view with bid totals and prices on the left and ask prices and totals on the right.
- The existing desktop order book path remains intact and is now explicitly isolated behind `lg` breakpoint classes.
- The mobile order book panel now uses a tighter height contract (`320px` base, `360px` at `sm`) so the account tables remain visible below without disturbing the desktop grid.
- Regression coverage now locks the mobile split DOM contract, the desktop breakpoint contract, and the compact mobile height override.

Validation outcome:

- `npm run check`: pass
- `npm test`: pass (`2039` tests, `10616` assertions)
- `npm run test:websocket`: pass (`339` tests, `1852` assertions)

Lessons learned:

- View-level responsive changes can still trip runtime errors if class vectors are composed with the wrong `into` arity, so trade-view breakpoint wrappers deserve direct regression coverage.
- The repository typography rules are strict enough that compact mobile UI should prefer named scale utilities over bracket text sizes.

## Context and Orientation

The trade page lives in `/hyperopen/src/hyperopen/views/trade_view.cljs`. On mobile widths, the top market-surface tabs are selected through `:trade-ui :mobile-surface`. The `Order Book` surface renders `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`. The lower account tables render through `/hyperopen/src/hyperopen/views/account_info_view.cljs` and are already visible for both `:chart` and `:orderbook` mobile surfaces.

The existing order book panel in `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` does three things that matter here:

1. It derives render-ready bid and ask slices from websocket order book data.
2. It renders header controls for price aggregation, freshness cue, and base/quote unit selection.
3. It renders a desktop-style depth ladder with asks above a spread row and bids below.

For this feature, “split-book” means a mobile layout where bids and asks are shown next to each other in two vertical columns inside the same card. The order book should still use the same data, formatting, and dropdown controls. “Desktop path” means the current stacked ladder must remain the only visible layout at desktop breakpoints.

The key tests are:

- `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs`, which verifies order book rendering contracts and should gain assertions for the new mobile split layout and desktop preservation.
- `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`, which verifies the larger trade page layout, including that the account tables remain visible on mobile market surfaces.

## Plan of Work

First, extend `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` with a responsive layout split. Keep all existing data preparation helpers. Add small rendering helpers for the mobile split-book headers and rows so the mobile DOM can express:

- a shared control/header area,
- a mobile-only split depth body visible below `lg`,
- the current stacked asks/spread/bids ladder visible at `lg` and above.

The mobile split layout should render bids on the left and asks on the right, each with consistent numeric formatting and depth bars. It should use the same size-unit selection already provided by the control row so the labels and values remain truthful. The component should keep the freshness cue and dropdown logic untouched.

Second, adjust `/hyperopen/src/hyperopen/views/trade_view.cljs` only if the mobile order book container needs a tighter fixed or minimum height to keep the account tables visible below. Any such change must stay mobile-only and must not alter the `lg` or `xl` grid contracts already covered by existing tests.

Third, update `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs` to assert the new mobile split DOM contract. At minimum, test for a mobile split wrapper, distinct bid and ask panes, and continued presence of the existing desktop depth-body contract. Update `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs` only if the trade page class contract changes to support the compact mobile height.

Finally, run the full required validation gates and record the exact outcomes in this file. If everything passes, close `hyperopen-7h1`.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` to add the responsive mobile split-book renderer while keeping the current desktop depth body intact.
2. Edit `/hyperopen/src/hyperopen/views/trade_view.cljs` only if the mobile order book container needs a tighter mobile-only size contract.
3. Update `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs` and `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`.
4. Run:

       npm run check
       npm test
       npm run test:websocket

5. Update this ExecPlan’s `Progress`, `Decision Log`, `Outcomes & Retrospective`, and `Artifacts and Notes` sections with the actual implementation and validation results.

## Validation and Acceptance

Acceptance means all of the following are true:

1. On a phone-width viewport, selecting the trade page `Order Book` surface shows a side-by-side bid/ask layout rather than the desktop stacked ladder.
2. The mobile order book remains compact enough that the account tables are still directly visible beneath it on the same route.
3. On desktop widths, the existing stacked order book layout remains the visible path and existing order book interactions continue to work.
4. `npm run check`, `npm test`, and `npm run test:websocket` all pass.

## Idempotence and Recovery

This work is safe to repeat because it is limited to view rendering and tests. If the responsive split-book branch misbehaves, recover by reverting only the new mobile-specific rendering helpers and responsive classes while keeping the existing desktop ladder and test fixtures intact. Do not remove the existing account-table mobile visibility behavior, because that is already a verified contract.

## Artifacts and Notes

Relevant files for this feature:

- `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`
- `/hyperopen/src/hyperopen/views/trade_view.cljs`
- `/hyperopen/src/hyperopen/views/account_info_view.cljs`
- `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs`
- `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`

Validation transcript summary:

- `npm run check` completed with all lint and compile stages passing.
- `npm test` completed with `2039` tests and `10616` assertions passing.
- `npm run test:websocket` completed with `339` tests and `1852` assertions passing.

## Interfaces and Dependencies

No external dependencies are required. The implementation should stay inside existing view modules and reuse the current order book render snapshot, formatting helpers, and websocket freshness surface logic.

Plan revision note: 2026-03-08 22:07Z - Created the ExecPlan after scoping the current mobile and desktop trade/order book layout contracts and opening tracked issue `hyperopen-7h1`.
Plan revision note: 2026-03-08 22:18Z - Marked the work complete, documented the runtime `into` regression and typography-policy discovery, recorded passing validation results, and moved the plan to `completed`.
