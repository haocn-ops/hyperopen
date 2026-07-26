# Positions Tooltip Text Parity With Hyperliquid

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, tooltip copy in Hyperopen's Positions tab will match Hyperliquid's exact wording for the same tooltip surfaces. A user will hover `PNL (ROE %)`, `Margin`, `Funding`, and funding cell values and see the same phrasing used by Hyperliquid instead of approximated local wording.

The result is observable in one UI pass: open the Positions tab, hover the three underlined headers and a funding value, and compare text verbatim against Hyperliquid.

## Progress

- [x] (2026-02-24 15:34Z) Audited current Hyperopen tooltip text in `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs`.
- [x] (2026-02-24 15:34Z) Extracted Hyperliquid live tooltip source text from `en-US` locale bundle (`/static/js/6263.2ab32e56.chunk.js`, module `26263`).
- [x] (2026-02-24 15:34Z) Authored delta comparison and this execution plan.
- [x] (2026-02-24 15:40Z) Updated Positions header tooltip constants to exact Hyperliquid copy and implemented funding tooltip formatter with `sinceChange`/`sinceOpen` fallback in `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs`.
- [x] (2026-02-24 15:40Z) Updated Positions tooltip tests for exact copy parity and added funding tooltip format/fallback assertions in `/hyperopen/test/hyperopen/views/account_info/tabs/positions_test.cljs`.
- [x] (2026-02-24 15:40Z) Ran required validation gates: `npm run check`, `npm test`, `npm run test:websocket`.

## Surprises & Discoveries

- Observation: Hyperliquid's tooltip copy is not embedded as default messages in Positions modules; text is resolved from locale JSON chunks.
  Evidence: `/hyperopen/tmp/hl-6263.js` contains module `26263` with JSON keys `pnl.roe.explanation`, `margin.explanation`, `funding.explanation`, `funding.tooltip`, and `liq.price.portfolio.margin.tooltip`.

- Observation: Hyperliquid funding hover copy has two values (`allTime` and `sinceChange`), while Hyperopen currently shows only one all-time value.
  Evidence: Hyperliquid locale key `funding.tooltip` value is `All-time: ${allTime} Since change: ${sinceChange}`; Hyperopen currently builds `"All-time funding: $<value>"` in `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs`.

- Observation: Hyperliquid's margin explanation intentionally uses lowercase `pnl` in the second clause.
  Evidence: Locale key `margin.explanation` is exactly `For isolated positions, margin includes unrealized pnl.`

## Decision Log

- Decision: Use exact Hyperliquid tooltip strings verbatim for parity-critical surfaces.
  Rationale: The request is text parity, and small wording differences are the regression being fixed.
  Date/Author: 2026-02-24 / Codex

- Decision: Keep Hyperopen's current funding sign convention and only change tooltip phrasing/field coverage in this scope.
  Rationale: Sign behavior affects numerical semantics and should remain unchanged unless explicitly requested.
  Date/Author: 2026-02-24 / Codex

- Decision: Treat Liq. Price tooltip parity as conditional follow-up in this plan, because Hyperliquid only shows that tooltip in specific portfolio-margin contexts.
  Rationale: We need deterministic local conditions before applying a static Liq tooltip universally.
  Date/Author: 2026-02-24 / Codex

- Decision: Defer Liq. Price tooltip copy replacement in this implementation and keep existing liquidation explanation data path.
  Rationale: Current Positions rendering does not receive a reliable portfolio-margin context flag equivalent to Hyperliquid's conditional behavior, and forcing a static Liq tooltip globally would reduce correctness.
  Date/Author: 2026-02-24 / Codex

## Outcomes & Retrospective

Implemented tooltip text parity for `PNL (ROE %)`, `Margin`, and `Funding` headers, plus funding value tooltip format parity (`All-time` and `Since change`) with safe `sinceOpen` and missing-value fallback behavior. Added/updated Positions tests to assert exact strings and fallback outcomes.

Remaining gap: Liq. Price tooltip parity remains intentionally deferred because local rendering still lacks a deterministic portfolio-margin-only condition equivalent to Hyperliquid's behavior.

## Context and Orientation

Current tooltip text for Positions headers and funding row value is defined in `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs`:

- `pnl-header-explanation`
- `margin-header-explanation`
- `funding-header-explanation`
- `funding-tooltip` string assembly inside `position-row`

Positions tooltip rendering affordance is already handled by shared table utilities in `/hyperopen/src/hyperopen/views/account_info/table.cljs`, and existing tests live in `/hyperopen/test/hyperopen/views/account_info/tabs/positions_test.cljs`.

Hyperliquid reference strings (from `en-US`) are:

1. `pnl.roe.explanation`
   `Mark price is used to estimate unrealized PNL. Only trade prices are used for realized PNL.`
2. `margin.explanation`
   `For isolated positions, margin includes unrealized pnl.`
3. `funding.explanation`
   `Net funding payments since the position was opened. Hover for all-time and since changed.`
4. `funding.tooltip`
   `All-time: ${allTime} Since change: ${sinceChange}`
5. `liq.price.portfolio.margin.tooltip`
   `Liquidation price estimate assumes all other prices stay constant. Estimate does not account for hedged positions between spot and perps, even though the actual margining would.`

## Plan of Work

Milestone 1 updates tooltip constants in `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs` to the exact Hyperliquid text for PNL, Margin, and Funding header explanations.

Milestone 2 updates the funding cell tooltip formatter in `position-row` to Hyperliquid format with both placeholders (`All-time` and `Since change`). The implementation will parse `:cumFunding` fields in order of preference (`:sinceChange`, then `:sinceOpen`, then missing) and render fallback placeholder text for unavailable `sinceChange` values without emitting `NaN`.

Milestone 3 scopes Liq. Price tooltip parity by identifying a deterministic local condition equivalent to Hyperliquid's portfolio-margin-only behavior. If the condition can be derived safely from existing view-model state, apply Hyperliquid's `liq.price.portfolio.margin.tooltip` text under that condition. If not derivable, retain existing liquidation explanation behavior and document the gap as explicit follow-up debt.

Milestone 4 updates tests in `/hyperopen/test/hyperopen/views/account_info/tabs/positions_test.cljs` to assert exact new strings and funding tooltip shape, including fallback handling when `sinceChange`/`sinceOpen` is absent.

Milestone 5 runs repository validation gates and confirms no regression in unrelated account table behavior.

## Concrete Steps

1. Update tooltip string constants and funding tooltip formatter.

   cd /Users//projects/hyperopen
   npm test -- positions

   Expected result: positions tests pass after local assertions are updated for new copy.

2. Add/adjust tests for exact string parity and missing `sinceChange` fallback.

   cd /Users//projects/hyperopen
   npm test -- positions

   Expected result: new tooltip-copy assertions pass and no `NaN` appears in tooltip text.

3. If Liq. Price parity condition is derivable, implement and test it; otherwise document explicit deferment in this plan before leaving code unchanged.

   cd /Users//projects/hyperopen
   npm test -- positions

   Expected result: deterministic behavior and tests for whichever branch is chosen.

4. Run full required gates.

   cd /Users//projects/hyperopen
   npm run check
   npm test
   npm run test:websocket

   Expected result: all commands exit 0.

## Validation and Acceptance

The change is accepted when all of the following are true:

1. `PNL (ROE %)`, `Margin`, and `Funding` header tooltip text in Hyperopen exactly matches the Hyperliquid strings listed in this plan.
2. Funding value tooltip format is `All-time: <value> Since change: <value-or-placeholder>` with stable numeric formatting and no `NaN`.
3. Positions tooltip tests assert exact expected strings and pass.
4. `npm run check`, `npm test`, and `npm run test:websocket` all pass.

## Idempotence and Recovery

String constant edits and test updates are idempotent and safe to re-run.

If Liq. Price condition inference causes ambiguity or flaky behavior, revert only that conditional tooltip branch and ship the confirmed header/funding text parity first, with documented follow-up.

## Artifacts and Notes

Primary Hyperliquid source artifacts used for this plan:

- `https://app.hyperliquid.xyz/`
- `https://app.hyperliquid.xyz/static/js/main.7a1533d2.js`
- `https://app.hyperliquid.xyz/static/js/6263.2ab32e56.chunk.js` (module `26263`, `en-US` messages)

Current Hyperopen implementation source:

- `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs`
- `/hyperopen/src/hyperopen/views/account_info/table.cljs`
- `/hyperopen/test/hyperopen/views/account_info/tabs/positions_test.cljs`

Plan revision note: 2026-02-24 15:34Z - Initial plan authored with exact tooltip string extraction from Hyperliquid `en-US` locale bundle.
Plan revision note: 2026-02-24 15:40Z - Updated progress/outcomes after implementation and validation; recorded explicit Liq. Price tooltip deferment due missing deterministic portfolio-margin context in current view inputs.

## Interfaces and Dependencies

No new dependencies are required.

Interfaces that must remain stable:

- `position-row`, `position-table-header`, and tooltip explanation constants in `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs`
- Tooltip rendering API in `/hyperopen/src/hyperopen/views/account_info/table.cljs` (`:explanation` metadata path)
- Positions view tests in `/hyperopen/test/hyperopen/views/account_info/tabs/positions_test.cljs`
