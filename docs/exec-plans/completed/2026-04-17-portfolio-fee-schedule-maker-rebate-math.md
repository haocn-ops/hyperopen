# Fix portfolio fee schedule maker rebate math

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. It is self-contained so a future contributor can implement the fix without relying on conversation history.

Tracked issue: `hyperopen-tvr3` ("Fix portfolio fee schedule maker rebate tier math").

## Purpose / Big Picture

The portfolio fee schedule popover lets a user preview how referral discount, staking discount, maker rebate tier, and market type affect fees. The current maker column becomes wrong when a maker rebate tier is selected: Hyperopen replaces every maker fee with the selected negative rebate value before market modifiers run. That flattens the maker column and erases the volume-tier schedule.

After this change, the user can open `/portfolio`, click `View Fee Schedule`, select any Maker Rebate Tier, and see each volume tier retain its own maker fee after the selected rebate adjustment. Lower-volume tiers can remain positive while higher-volume tiers become zero or negative. This matches the inspected reference behavior and the public fee schedule semantics.

## Progress

- [x] (2026-04-17 17:20Z) Consulted the public fee documentation and the Hyperliquid developer fee formula.
- [x] (2026-04-17 17:32Z) Inspected the current Hyperopen fee schedule model and tests.
- [x] (2026-04-17 17:45Z) Captured reference fee schedule behavior showing maker rebate tiers adjust each maker row instead of replacing the whole maker column.
- [x] (2026-04-17 17:54Z) Created tracker `hyperopen-tvr3`.
- [x] (2026-04-17 18:02Z) Wrote this active ExecPlan with the corrected product and implementation contract.
- [x] (2026-04-17 18:06Z) Wrote failing unit tests for additive maker rebate behavior in the portfolio fee schedule model.
- [x] (2026-04-17 18:07Z) Confirmed the RED focused CLJS run failed only on old flat maker-rebate expectations.
- [x] (2026-04-17 18:08Z) Updated implementation to apply maker rebate as a post-schedule maker adjustment.
- [x] (2026-04-17 18:08Z) Focused CLJS model/view tests passed after the implementation change.
- [x] (2026-04-17 18:08Z) Updated Playwright expectations that previously asserted a flat negative maker column.
- [x] (2026-04-17 18:10Z) Focused Playwright fee schedule regression passed after forcing a fresh app compile.
- [x] (2026-04-17 18:11Z) `npm run check` passed.
- [x] (2026-04-17 18:12Z) `npm test` passed with 3220 tests, 17273 assertions, 0 failures, and 0 errors.
- [x] (2026-04-17 18:12Z) `npm run test:websocket` passed with 432 tests, 2479 assertions, 0 failures, and 0 errors.
- [x] (2026-04-17 18:15Z) Governed browser QA rerun passed for portfolio-route at review widths 375, 768, 1280, and 1440.
- [x] (2026-04-17 18:15Z) Ran `npm run browser:cleanup` after browser QA; no sessions remained.

## Surprises & Discoveries

- Observation: The public Hyperliquid developer formula covers final fee calculation for a concrete market when the caller already has `fees.makerRate`, `fees.takerRate`, and `activeReferralDiscount` from the `userFees` info endpoint. It does not, by itself, define how to synthesize a what-if matrix across all volume tiers and maker rebate scenarios.
  Evidence: The formula takes `fees: { makerRate, takerRate }`, `activeReferralDiscount`, `isAlignedQuoteToken`, and either spot or perp market arguments. It has no separate maker-rebate-tier argument and no volume-tier table construction step. Source: `https://hyperliquid.gitbook.io/hyperliquid-docs/trading/fees#fee-formula-for-developers`.

- Observation: The current shared trading-fee domain formula is structurally aligned with the Hyperliquid developer formula and should not be changed for this fix.
  Evidence: `/Users/barry/.codex/worktrees/09e3/hyperopen/src/hyperopen/domain/trading/fees.cljs` computes stable-pair scale, HIP-3 positive-fee scale, deployer share, growth-mode scale, aligned taker scale, and aligned negative-maker-rebate scale in `adjust-percentage-rates`. The mismatch is in `/Users/barry/.codex/worktrees/09e3/hyperopen/src/hyperopen/portfolio/fee_schedule.cljs`, where the what-if schedule turns a maker rebate tier into a replacement maker cell.

- Observation: The inspected reference fee schedule applies the selected maker rebate as a row-by-row percentage-point adjustment to the displayed maker schedule. It does not replace every maker cell with the selected negative rebate.
  Evidence: With no selected staking discount, no referral discount, `HIP-3 Perps + Growth mode + Aligned Quote`, and `No rebate`, the reference maker column is `0.003%`, `0.0024%`, `0.0016%`, `0.0008%`, `0%`, `0%`, `0%`. Selecting maker rebate `Tier 2` changes that column to `0.001%`, `0.0004%`, `-0.0004%`, `-0.0012%`, `-0.002%`, `-0.002%`, `-0.002%`. This is exactly the no-rebate maker column plus `-0.002%` per row.

- Observation: The reference popover's scenario table behavior is not the same as passing a negative `makerRate` through the Hyperliquid developer formula. The formula remains correct for actual wallet fee quotes; the scenario table needs its own builder contract.
  Evidence: Passing a negative maker rate into the formula enters the negative-maker rebate branch, which applies growth-mode and aligned-quote rebate scaling. The reference scenario dropdown instead adds the selected rebate amount after the market schedule is displayed; for example, the `Tier 2` scenario above subtracts exactly `0.002%` from each displayed maker row.

- Observation: The current implementation already formats HIP-3 no-rebate rows correctly for the default `deployerFeeScale = 1` reference case.
  Evidence: For `HIP-3 Perps + Growth mode` with no staking/referral/rebate, the expected rows are tier 0 `0.009% / 0.003%`, tier 1 `0.008% / 0.0024%`, tier 2 `0.007% / 0.0016%`, tier 3 `0.006% / 0.0008%`, tier 4 `0.0056% / 0%`, tier 5 `0.0052% / 0%`, and tier 6 `0.0048% / 0%`. The issue appears only once maker rebate scenarios are selected.

- Observation: The RED test run failed on the intended old behavior and did not expose unrelated view-model breakage.
  Evidence: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.fee-schedule-test --test=hyperopen.views.portfolio.fee-schedule-test` ran 8 tests and 146 assertions with 8 failures, all in maker-rebate expectations showing the old flat replacement values such as `-0.002%`.

- Observation: The first focused Playwright run used a stale app bundle and still saw the old `-0.002%` maker value. Forcing a fresh app compile before rerunning the same test fixed the browser evidence without any code changes.
  Evidence: `npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "portfolio fee schedule" --workers=1` first failed at the new `0.0066%` assertion and received `-0.002%`. After `npx shadow-cljs --force-spawn compile app portfolio-worker vault-detail-worker`, the same Playwright command passed.

- Observation: The first governed browser QA run reported a mobile jank long-task issue that did not reproduce.
  Evidence: Run `design-review-2026-04-17T18-12-11-546Z-f9edfa33` failed only `jank-perf` at review-375 with a 251 ms long task. After cleanup, run `design-review-2026-04-17T18-14-03-017Z-8c07d173` passed all required passes and widths.

## Decision Log

- Decision: Treat `hyperopen.domain.trading.fees/adjust-percentage-rates` and `quote-fees` as actual-market fee formula code and keep them unchanged for this fix.
  Rationale: They match the documented developer formula. Changing them to match the scenario-table dropdown would risk breaking actual trade fee summaries.
  Date/Author: 2026-04-17 / Codex

- Decision: In the portfolio fee schedule scenario table, maker rebate tier means a flat maker-fee adjustment applied after volume, staking, market type, and referral transformations have produced a row-specific maker fee.
  Rationale: This is the behavior the user is comparing against, and it is the only model that preserves row-specific maker values while matching the inspected reference table.
  Date/Author: 2026-04-17 / Codex

- Decision: Keep the existing `deployerFeeScale = 1` fallback for generic HIP-3 previews.
  Rationale: The public trade[XYZ] standard and growth tables currently correspond to `deployerFeeScale = 1`, and the prior completed plan intentionally made HIP-3 options selectable without an active market by using that default. This bug is independent of that default.
  Date/Author: 2026-04-17 / Codex

- Decision: Add an epsilon clamp in the portfolio display path if implementation produces `-0.0000%` or `-0%` around exact zero.
  Rationale: A row such as `0.003% + -0.003%` should display `0%`, matching the reference and avoiding floating-point display artifacts.
  Date/Author: 2026-04-17 / Codex

## Outcomes & Retrospective

Implemented and validated. The portfolio fee schedule now treats Maker Rebate Tier as an additive post-schedule maker adjustment, so the maker column remains row-specific and only crosses below zero on the tiers where the adjusted maker fee warrants it. The change reduced conceptual complexity in the portfolio schedule builder by separating actual-market developer formula code from the scenario-table maker-rebate adjustment.

The remaining blind spot is the governed design-review harness's standard sampled-state note: hover, active, disabled, and loading states require targeted route actions when not present by default. The focused Playwright regression covers the relevant fee schedule interaction states for this bug.

## Context and Orientation

The portfolio fee schedule model lives in `/Users/barry/.codex/worktrees/09e3/hyperopen/src/hyperopen/portfolio/fee_schedule.cljs`. It owns the static fee schedules, dropdown options, selected scenario values, active HIP-3 context, and final `fee-schedule-model` consumed by the view.

The view lives in `/Users/barry/.codex/worktrees/09e3/hyperopen/src/hyperopen/views/portfolio/fee_schedule.cljs`. It renders the popover, four dropdown selectors, and the tier table. The view should not do fee math.

The shared actual-market fee formula lives in `/Users/barry/.codex/worktrees/09e3/hyperopen/src/hyperopen/domain/trading/fees.cljs`. It implements the Hyperliquid developer formula for actual `userFees` rates. The implementation plan must avoid changing this file unless a failing test proves the formula itself is wrong.

The currently wrong code path is `apply-maker-rebate-tier` in `/Users/barry/.codex/worktrees/09e3/hyperopen/src/hyperopen/portfolio/fee_schedule.cljs`. It looks up `:maker-rate` from `maker-rebate-tier-options` and uses `(assoc row :maker maker-rate)`. That turns a selected maker rebate into a replacement fee. Because `fee-schedule-rows` calls it before `apply-market-type`, selected rebates also go through market modifiers in ways that do not match the reference schedule.

Relevant docs:

- Hyperliquid fees: `https://hyperliquid.gitbook.io/hyperliquid-docs/trading/fees`
- Hyperliquid developer formula: `https://hyperliquid.gitbook.io/hyperliquid-docs/trading/fees#fee-formula-for-developers`
- trade[XYZ] fees: `https://docs.trade.xyz/perp-mechanics/fees`

Definitions used in this plan:

- A volume tier is one row in the fee schedule, based on rolling 14-day weighted volume.
- A maker rebate tier is a separate maker-volume-share reward tier. In the portfolio what-if table, it should subtract `0.001%`, `0.002%`, or `0.003%` from each row's computed maker fee.
- HIP-3 is Hyperliquid's builder-deployed perpetual market system. For generic trade[XYZ] previews, use `deployerFeeScale = 1`, which doubles positive HIP-3 fees before growth mode.
- Growth mode applies a `0.1` multiplier to HIP-3 protocol fees in the public formula and the current app.
- Aligned quote reduces taker fees and improves negative maker rebates in the actual developer formula. The portfolio what-if maker rebate dropdown should not enter the negative-rebate formula branch; it is a post-row schedule adjustment.

## Correct Fee Schedule Contract

For a portfolio schedule row, start from the current base perps or spot row in percentage units. For example, core perps tier 0 is `0.045` taker and `0.015` maker, meaning `0.045% / 0.015%`.

Apply selected staking tier first to positive taker and positive maker fees. Wood is 5%, Bronze 10%, Silver 15%, Gold 20%, Platinum 30%, and Diamond 40%. Do not apply staking discount to a zero or negative maker value.

Apply selected market type next by calling `trading-fees/adjust-percentage-rates` with the correct spot/perp, stable pair, HIP-3 deployer scale, growth mode, and aligned quote flags. This preserves the existing developer-formula implementation for the no-rebate schedule.

Apply selected referral discount next to positive taker and positive maker fees. The current scenario selector only exposes no referral discount and the documented 4% referral discount. Do not apply referral discount to zero or negative maker values.

Apply selected maker rebate tier last. The adjustment is additive in percentage units: no rebate is `0`, Tier 1 is `-0.001`, Tier 2 is `-0.002`, and Tier 3 is `-0.003`. This adjustment applies to the maker cell only, after all preceding transformations. It can turn a positive maker fee into a negative maker fee. It is not passed through `trading-fees/adjust-percentage-rates`, and it is not further adjusted by growth mode, aligned quote, deployer share, staking discount, or referral discount in the scenario table.

Format the final row using the existing display style, but clamp tiny absolute values below an epsilon such as `0.0000001` to `0` before string formatting.

Concrete expected examples:

With no staking, no referral, no maker rebate, and `HIP-3 Perps + Growth mode + Aligned Quote` using default `deployerFeeScale = 1`, the maker column is `0.003%`, `0.0024%`, `0.0016%`, `0.0008%`, `0%`, `0%`, `0%`.

With the same scenario and Maker Rebate Tier 2 selected, the maker column is `0.001%`, `0.0004%`, `-0.0004%`, `-0.0012%`, `-0.002%`, `-0.002%`, `-0.002%`.

With core perps, Diamond staking, no referral, and Maker Rebate Tier 3 selected, the maker column is `0.006%`, `0.0042%`, `0.0018%`, `-0.0006%`, `-0.003%`, `-0.003%`, `-0.003%`.

With core perps, Diamond staking, 4% referral discount, and Maker Rebate Tier 2 selected, tier 0 maker is `0.0066%`: `0.015% * (1 - 0.40) * (1 - 0.04) - 0.002% = 0.00664%`, displayed as `0.0066%`.

## Plan of Work

First, add failing unit tests in `/Users/barry/.codex/worktrees/09e3/hyperopen/test/hyperopen/portfolio/fee_schedule_test.cljs`. Update the existing maker rebate tests that currently expect flat replacement values. Add direct assertions for the `HIP-3 Perps + Growth mode + Aligned Quote` Maker Rebate Tier 2 row sequence and the core perps Diamond Maker Rebate Tier 3 row sequence above. Add one assertion that exact-zero output displays as `0%` rather than `-0%`.

Second, change `/Users/barry/.codex/worktrees/09e3/hyperopen/src/hyperopen/portfolio/fee_schedule.cljs`. Replace the replacement-style `apply-maker-rebate-tier` helper with an additive adjustment helper. The helper should look up the selected option's rebate adjustment and update `:maker` with `+`. Move the helper call to the end of the row pipeline, after `apply-referral-discount`. Rename internal option data from `:maker-rate` to `:maker-adjustment` if that reduces confusion, and update `current-maker-rebate-tier` to compare current wallet `userAddRate` against the configured rebate values as needed.

Third, update model tests in `/Users/barry/.codex/worktrees/09e3/hyperopen/test/hyperopen/portfolio/fee_schedule_test.cljs` that currently assert `:maker "-0.002%"` or `:maker "-0.003%"` for every tier after selecting a maker rebate. Those expectations should now be row-specific. In the connected wallet case with Bronze staking, 4% referral, and Tier 2 rebate, tier 0 maker should be the discounted perps maker fee minus `0.002%`.

Fourth, update view tests in `/Users/barry/.codex/worktrees/09e3/hyperopen/test/hyperopen/views/portfolio/fee_schedule_test.cljs` only if text snapshots or sample model fixtures contain old flat-rebate values.

Fifth, update deterministic Playwright expectations in `/Users/barry/.codex/worktrees/09e3/hyperopen/tools/playwright/test/portfolio-regressions.spec.mjs`. The existing flow selects 4% referral, Diamond staking, Maker Rebate Tier 2, and then asserts tier 0 contains `-0.002%`. Replace that assertion with the corrected row-specific tier 0 maker expectation, `0.0066%`. Later, after switching to `Spot + Aligned Quote + Stable Pair` while the same scenario selections are active, replace the old negative maker expectation with `0.0026%`, because `0.040% * (1 - 0.40) * 0.2 * (1 - 0.04) - 0.002% = 0.002608%`.

Sixth, run focused tests and then required gates. Use the existing test generation and Shadow CLJS flow for model tests, the focused Playwright regression for the popover, then `npm run check`, `npm test`, and `npm run test:websocket`. Because this is UI-facing work, run the governed browser QA design review for the portfolio route and clean up browser sessions with `npm run browser:cleanup`.

## Concrete Steps

From `/Users/barry/.codex/worktrees/09e3/hyperopen`, run the focused model tests before implementation after adding the RED assertions:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.fee-schedule-test --test=hyperopen.views.portfolio.fee-schedule-test

Expected RED result: the new maker rebate assertions fail because `apply-maker-rebate-tier` currently replaces maker values instead of adding an adjustment after market/referral transforms.

After implementation, run the same focused tests and expect zero failures.

Run the focused browser regression:

    npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "portfolio fee schedule" --workers=1

If a dev server is already running on port 8080, use the existing local reuse config pattern under `tmp/playwright/` and keep that temp file untracked.

Run the required repo gates:

    npm run check
    npm test
    npm run test:websocket

Run browser QA for this UI surface:

    npm run browser:cleanup
    npm run qa:design-ui -- --targets portfolio-route --manage-local-app
    npm run browser:cleanup

The actual validation results are recorded in `Progress`, `Surprises & Discoveries`, and `Outcomes & Retrospective`.

## Validation and Acceptance

Acceptance criteria:

1. Opening `/portfolio`, clicking `View Fee Schedule`, and selecting Maker Rebate Tier 1, Tier 2, or Tier 3 keeps the maker column row-specific. The maker column must not become a flat `-0.001%`, `-0.002%`, or `-0.003%` across all rows.

2. With no staking, no referral, Maker Rebate Tier 2, and `HIP-3 Perps + Growth mode + Aligned Quote`, the maker column must read `0.001%`, `0.0004%`, `-0.0004%`, `-0.0012%`, `-0.002%`, `-0.002%`, `-0.002%`.

3. With core perps, Diamond staking, no referral, and Maker Rebate Tier 3, the maker column must read `0.006%`, `0.0042%`, `0.0018%`, `-0.0006%`, `-0.003%`, `-0.003%`, `-0.003%`.

4. With core perps, Diamond staking, 4% referral discount, and Maker Rebate Tier 2, tier 0 maker must display `0.0066%`.

5. No-rebate rows must remain unchanged from the current accepted behavior.

6. `hyperopen.domain.trading.fees` tests must continue to pass without changing the documented developer formula behavior.

7. The fee schedule popover must still fit within the viewport, remain opaque, remain anchored near the trigger, and satisfy the required browser QA passes at widths 375, 768, 1280, and 1440.

## Idempotence and Recovery

The implementation is source-level and safe to rerun. If the new tests fail unexpectedly, isolate whether the failure is from formula order, string formatting, or view text. Revert only the maker rebate scenario changes if needed; do not revert unrelated fee schedule popover work already present on this branch.

If browser QA starts a local app or browser-inspection session, always run `npm run browser:cleanup` before ending the task. If Shadow CLJS reports `already started`, stop the stale Shadow server with `npx shadow-cljs stop` and confirm ports 8080 and 9630 are free before retrying.

## Artifacts and Notes

Reference behavior captured on 2026-04-17 from `https://app.trade.xyz/portfolio?ghost=0x162cc7c861ebd0c06b3d72319201150482518185`:

    HIP-3 Perps + Growth mode + Aligned Quote, No rebate:
    Maker: 0.003%, 0.0024%, 0.0016%, 0.0008%, 0%, 0%, 0%

    HIP-3 Perps + Growth mode + Aligned Quote, Maker Rebate Tier 1:
    Maker: 0.002%, 0.0014%, 0.0006%, -0.0002%, -0.001%, -0.001%, -0.001%

    HIP-3 Perps + Growth mode + Aligned Quote, Maker Rebate Tier 2:
    Maker: 0.001%, 0.0004%, -0.0004%, -0.0012%, -0.002%, -0.002%, -0.002%

    HIP-3 Perps + Growth mode + Aligned Quote, Maker Rebate Tier 3:
    Maker: 0%, -0.0006%, -0.0014%, -0.0022%, -0.003%, -0.003%, -0.003%

Hyperliquid `userFees` for the inspected ghost wallet showed the canonical fee schedule structure includes `feeSchedule.tiers.mm` entries with `add` values `-0.00001`, `-0.00002`, and `-0.00003`, corresponding to `-0.001%`, `-0.002%`, and `-0.003%`.

## Interfaces and Dependencies

The public interface of `/Users/barry/.codex/worktrees/09e3/hyperopen/src/hyperopen/portfolio/fee_schedule.cljs` should remain stable. Keep `normalize-market-type`, `market-type-options`, `fee-schedule-rows`, and `fee-schedule-model` available to existing callers. The action names and view data roles should not change.

The implementation may add private helpers such as `option-maker-adjustment`, `apply-maker-rebate-adjustment`, and `normalize-display-rate-zero`. These helpers should stay private unless a test-only public API already exists for the surrounding helper pattern.

Revision note: 2026-04-17 18:02Z. Created this active ExecPlan after researching the documented formula, inspecting the current implementation, and capturing reference maker-column behavior. The key correction is modeling the Maker Rebate Tier dropdown as an additive, post-schedule maker adjustment rather than a replacement maker rate.

Revision note: 2026-04-17 18:05Z. Tightened the Playwright expectation section with the exact corrected `Spot + Aligned Quote + Stable Pair` maker value so the implementation handoff has no placeholder expectation.
