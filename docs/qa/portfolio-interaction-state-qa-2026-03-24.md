# Portfolio Interaction-State QA - 2026-03-24

This note records the targeted follow-up QA for parent epic `hyperopen-6len` and child tasks `hyperopen-l6vw` and `hyperopen-x7uh`.

## Scope

The goal is to reduce the residual interaction blind spots reported by design-review bundle `design-review-2026-03-24T20-14-35-320Z-d902139f` for:

- `/portfolio`
- `/portfolio/trader/:address`

The design-review tool intentionally leaves a generic interaction blind spot when route-local hover, active, disabled, and loading states are not force-driven during passive review. This note accounts for the stable interaction states promoted into deterministic regression coverage and calls out what remains an explicit skip.

## Covered States

Committed deterministic browser coverage now targets these stable route-local states:

- `/portfolio`
  - top action-row buttons remain present on the standard route
  - summary-scope selector opens and can switch from `All` to `Perps`
  - chart tabs expose active state through `aria-pressed`
  - shared account-info tabs expose active state through `aria-pressed`

- `/portfolio/trader/:address`
  - trader inspection header remains present
  - read-only route framing still hides the normal mutation action row
  - the separate Hyperliquid Explorer link remains present
  - summary-scope selector opens and can switch from `All` to `Perps`
  - chart tabs expose active state through `aria-pressed`
  - shared account-info tabs expose active state through `aria-pressed`
  - balances keeps the read-only `Contract` column while omitting `Send`, `Transfer`, and `Repay`
  - positions omits `Close All`, row-level `Reduce`, and inline `Edit Margin` / `Edit TP/SL` affordances
  - open orders omits `Cancel All` and row-level `Cancel`
  - TWAP omits the `Terminate` column and action
  - `Your Portfolio` returns the app to the normal `/portfolio` route

Composed CLJS view coverage now also proves that the shared account-info read-only cleanup stays wired on non-empty tabs for both inspected-account entry paths:

- trader portfolio route composition via `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`
- spectate-mode composition via `/hyperopen/test/hyperopen/views/account_info_view_test.cljs`
- shared read-only flag projection via `/hyperopen/test/hyperopen/views/account_info/vm_test.cljs`

Supporting artifacts live in:

- `/hyperopen/tools/playwright/test/portfolio-regressions.spec.mjs`
- `/hyperopen/tools/browser-inspection/scenarios/portfolio-interaction-states.json`
- `/hyperopen/tools/browser-inspection/scenarios/trader-portfolio-interaction-states.json`

## Validation Results

Validation completed on 2026-03-24 with these results:

1. `npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs`
   Result: PASS (`2 passed`, Node 20.18.2)
2. `node tools/browser-inspection/src/cli.mjs design-review --targets portfolio-route,trader-portfolio-route --session-id sess-1774393431846-673709`
   Result: PASS for run `design-review-2026-03-24T23-03-57-882Z-626fed6f`, with the expected residual `state-sampling-limited` blind spots on both routes and all reviewed viewports
3. `npm run check`
   Result: PASS (Node 24.0.2)
4. `npm test`
   Result: PASS (Node 24.0.2)
5. `npm run test:websocket`
   Result: PASS (Node 24.0.2)

`npm test` still requires Node 24 in this workspace because the Node 20 runtime hits the existing `@noble/secp256k1` ESM/`require` incompatibility outside this change set.

## Explicit Skips

The following states remain explicit skips rather than new route-level browser assertions:

- Portfolio chart hover crosshair and tooltip positioning. Those interactions are pointer-position-sensitive and already have dedicated view-level coverage in `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`.
- Performance-metrics loading overlay. That loading state is already covered directly in `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs` via `portfolio-view-performance-metrics-loading-overlay-renders-explainer-copy-test`.
- Disabled mutation controls on `/portfolio/trader/:address`. The real contract is omission of mutation affordances, and the committed coverage above now proves that omission directly. We still do not add fake disabled buttons just to satisfy the generic design-review interaction rubric.
- Disabled controls on `/portfolio` that belong to other surfaces, such as funding modal or downstream transactional flows. Those states are owned by their respective route or modal tests, not by the portfolio route shell itself.

## Intended Validation Path

The repo-consistent follow-up command path for this work is:

1. Run the focused Playwright regression coverage for the portfolio routes.
2. Re-run governed browser QA for `portfolio-route` and `trader-portfolio-route`.
3. Run the required repository gates: `npm run check`, `npm test`, and `npm run test:websocket`.

If a future design-review rerun still reports the broad generic interaction blind spot, it should be interpreted alongside this note and the committed browser regressions rather than as evidence that the newly covered route-local states remain untested.
