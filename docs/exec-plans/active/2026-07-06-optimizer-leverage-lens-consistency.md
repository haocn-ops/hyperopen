# Reconcile the optimizer's leverage figure with the trade page's Unified Account Leverage

## Purpose

A user with spot holdings sees the trade route's Unified Account Summary report "Unified Account Leverage 1.43x" while the optimizer's Execution tab reports "Account leverage after 2.91x — was 2.52x". Both numbers are internally correct, but they measure different things with near-identical labels, and the optimizer's tooltip falsely claims they are "the same leverage metric the account panels show". This plan makes each surface honest about its lens, renames the optimizer metric so the two stop looking like the same number disagreeing, and adds a bridge figure ("venue" perp-margin leverage) on the execution surfaces so a trader can relate the optimizer's number to the trade page's number without leaving the tab.

Direct user request, captured 2026-07-06: "get to the root cause first of why the difference is and come up with UX recommendations … create an execution plan and implement steps 1,2,3,4."

## Root cause (context a novice needs)

Two different formulas:

- Trade route (`src/hyperopen/views/account_equity/metrics.cljs`, `unified-account-leverage*`): sum of cross-margin perp position notional (`crossMarginSummary.totalNtlPos`) across all dexes, divided by the USD value of collateral quote tokens (USDC/USDH-style spot balances). Non-stable spot holdings (HYPE etc.) appear nowhere in this ratio. Classic (non-unified) accounts fall back to perp notional ÷ total portfolio value.
- Optimizer (`src/hyperopen/portfolio/optimizer/application/current_portfolio.cljs` feeding `src/hyperopen/portfolio/optimizer/domain/rebalance.cljs` `margin-summary`): gross leverage = sum of absolute notional of perp exposures AND non-cash spot exposures, divided by NAV (cash + all non-cash spot).

So a $100 spot holding is ignored by the trade page's ratio but adds $100 to both numerator and denominator of the optimizer's ratio, pushing the optimizer figure up whenever spot holdings are a meaningful share of the book. The trade page answers "how levered is my margin account at the venue" (liquidation lens); the optimizer answers "how much market exposure per dollar of equity" (portfolio lens). The defect is presentational: same-sounding labels, plus an execution-tab tooltip that asserts the two are identical.

## Scope: the four agreed UX fixes

1. Fix the false tooltip on the execution KPI ("the same leverage metric the account panels show") with an honest formula that names the difference.
2. Rename the optimizer metric from "Account leverage after" to "Gross leverage after" on both the execution KPI strip and the execution-health rail.
3. Bridge the two lenses on the execution surfaces: compute a projected venue-lens leverage (perp-only notional ÷ collateral) in the margin model and append it to the leverage sub-line, e.g. "was 2.52x · $260 free · venue ≈1.4x".
4. Trade page: extend the Unified Account Leverage tooltip with "Perp positions only; spot holdings are not counted as exposure here."

## Design decisions for the bridge figure (step 3)

The margin model (`margin-summary` in `src/hyperopen/portfolio/optimizer/domain/rebalance.cljs`) is pure; it must receive the venue inputs from the application layer rather than reading state. The application layer (`current_portfolio.cljs`) picks the venue denominator per account mode, mirroring the trade page's own mode split:

- unified mode: collateral = USDC spot cash plus stable-dollar spot tokens (USDH/USDT/USDE-prefixed), approximating the trade page's "collateral quote tokens" set;
- classic mode: collateral = NAV (matching the trade page's classic fallback of perp notional ÷ portfolio value).

Numerator: current perp-only gross exposure (already separable in the snapshot builder), projected forward by the ready rows' perp weight deltas the same way `after-gross-leverage` is projected. The figure is displayed with "≈" because the collateral approximation can differ slightly from the venue's own quote-token enumeration; honesty over false precision.

New preview opts: `:current-perp-gross-usdc`, `:venue-collateral-usd`. New margin-summary keys: `:before-venue-leverage`, `:after-venue-leverage` (nil when inputs are missing — display must degrade to today's sub-line). Both preview opts are derived inside `rebalance_preview.cljs` from the snapshot the request already carries (`[:current-portfolio :exposures]` rows expose `:market-type`, `:coin`, `:abs-notional-usdc`; `[:current-portfolio :capital :cash-usdc]` and `:nav-usdc`; account mode at `[:current-portfolio :account :mode]`) — the snapshot builder itself is untouched.

## Files to touch

- `src/hyperopen/portfolio/optimizer/application/rebalance_preview.cljs` — derive perp-only gross and venue collateral from the snapshot and thread the two new opts into `build-rebalance-preview`.
- `src/hyperopen/portfolio/optimizer/domain/rebalance.cljs` — `margin-summary` computes before/after venue leverage from perp-only ready deltas.
- `src/hyperopen/views/portfolio/optimize/execution_tab.cljs` — rename KPI + rail labels, honest tooltip.
- `src/hyperopen/views/portfolio/optimize/execution_shared.cljs` — `leverage-headroom-sub` appends "venue ≈X" when available.
- `src/hyperopen/views/account_equity/panels.cljs` (and the duplicate private tooltip in `format.cljs` if referenced) — extend tooltip copy.
- Tests: `test/hyperopen/portfolio/optimizer/domain/rebalance_test.cljs` (exact-equality margin map gains keys; new venue projection test proving spot rows don't move the venue figure), `test/hyperopen/views/portfolio/optimize/execution_tab_test.cljs` (label string), `test/hyperopen/views/account_equity_view_test.cljs` (tooltip string).

## Validation

From the repo root: `npm run setup:worktree` once, then `npm run gates` (runs `npm run check`, `npm test`, `npm run test:websocket` with a PASS/FAIL matrix). Acceptance: all gates pass; the execution tab renders "Gross leverage after" with the corrected tooltip and a venue figure in the sub-line when venue inputs exist; the account-equity view test asserts the extended tooltip sentence.

## Progress

- [x] Root cause investigated and confirmed in code (metrics.cljs vs current_portfolio.cljs/rebalance.cljs).
- [x] Step 1+2: execution tab label rename + honest tooltip.
- [x] Step 3: venue inputs derived in rebalance_preview, margin-summary venue keys, sub-line display.
- [x] Step 4: trade-page tooltip extension.
- [x] Tests updated/added; `npm run gates` green.
- [ ] Plan moved to completed after review.

## Surprises & Discoveries

- The execution KPI tooltip literally claimed "the same leverage metric the account panels show" — the root confusion was authored copy, not a data bug.
- `format.cljs` and `panels.cljs` both define a private `unified-account-leverage-tooltip`; only the `panels.cljs` one renders. Both updated to stay in sync (the format.cljs copy is dead but kept matching).
- The optimizer counts USDH/USDT-style stables as spot *exposures* (only USDC is cash), while the trade page counts them as *collateral* — a second, smaller divergence beyond the headline spot-exposure one; the venue-collateral approximation in this plan accounts for it.
- The `lint:namespace-sizes` ratchet rejected the first-cut implementation: `current_portfolio.cljs` was already at its per-file line cap, so adding the venue inputs to the snapshot builder failed the gate. The inputs turned out to be fully derivable from data the preview request already carries, so the computation moved to `rebalance_preview.cljs` and the snapshot builder was left untouched — a strictly smaller change.

## Decision Log

- Rename to "Gross leverage after" rather than aligning the optimizer to the venue formula: the portfolio lens (spot counts as exposure) is the correct risk measure for an optimizer; only the naming/claim of equivalence was wrong.
- Venue bridge is computed in the pure domain from application-supplied inputs (no state reads in domain), with the unified-vs-classic mode split resolved in `rebalance_preview.cljs` from the snapshot's account mode, mirroring how the trade page itself splits unified vs classic. (Originally planned for `current_portfolio.cljs`; moved — see Surprises.)
- Venue figure is labeled with "≈" and omitted entirely when inputs are absent, rather than showing a possibly-wrong exact number.
- Stable-token predicate duplicated (small, pure, one `starts-with "USD"` check) into `rebalance_preview.cljs` instead of importing from the account-equity view namespace, to avoid an optimizer→view dependency.

## Outcomes & Retrospective

Completed 2026-07-06. All four UX fixes landed and `npm run gates` is fully green (34/34 gates, 5914 tests, 31586 assertions). The change is presentational plus one additive pure-domain projection; net complexity increase is small (two preview opts, two margin keys) and justified by removing a false equivalence claim shown at the commit-money moment. The namespace-size ratchet pushed the design to a better altitude (derive from the request instead of widening the snapshot). Remaining: move this plan to completed after review.
