# Outcome Category Market Grouping Parity

This ExecPlan is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Hyperliquid now renders grouped outcome categories as a single selector row with a compact list of available child markets underneath. The June 7 screenshot shows `2026 World Cup Champion` as the row title with leading candidates such as France, Spain, and Argentina underneath, and sports binaries such as `2026 NBA Finals champion` with team probabilities under the title. Hyperopen's previous outcome parity work added question rows and tabs, but it still does not match this row-summary behavior for large grouped questions and sports-style binaries.

After this work, Hyperopen's Outcome tab should display one row per grouped question/category, keep all named child markets available for selection and trading, summarize only the leading child markets in the asset selector, and show sports binary side markets underneath the binary title when the side labels are meaningful.

## Live Evidence

- On 2026-06-08, `POST https://api.hyperliquid.xyz/info` with `{"type":"outcomeMeta"}` returned 62 `outcomes` and 3 `questions`.
- The live payload includes `question:32` named `2026 World Cup Champion`, `fallbackOutcome 171`, and 48 `namedOutcomes` for teams from Algeria through Uzbekistan.
- The live payload still includes `question:19` for `May CPI year-over-year` and `question:33` for the recurring BTC price bucket.
- Public `webData2` for the zero address returned 124 outcome side contexts whose coins begin with `#`, including the World Cup team side coins.

## Context References

- Direct user request on 2026-06-08: bring Hyperopen Outcome rows to parity with Hyperliquid's category row behavior shown in the attached Sports screenshot.
- Parent parity plan: `docs/exec-plans/completed/2026-06-05-outcome-market-parity.md`.
- Primary implementation surfaces: `src/hyperopen/asset_selector/markets.cljs`, `src/hyperopen/asset_selector/market_live_projection.cljs`, `src/hyperopen/views/asset_selector/rows.cljs`, and `tools/playwright/test/trade-regressions.spec.mjs`.

## Scope

In scope:

- Keep explicit `outcomeMeta.questions` as the source of truth for categories such as `2026 World Cup Champion`.
- Preserve every named outcome in `:question-options` so order-form option selection, side routing, account enrichment, and websocket aliases continue to work.
- Limit selector summaries for question rows to the top three active options by Yes-side mark, with deterministic tie-breaking.
- Add binary side summaries for sports-style binary outcomes whose side labels are not generic `Yes` and `No`.
- Keep summaries stable when `activeSpotAssetCtx` patches question options.
- Add unit and Playwright coverage for the grouped category row and sports binary row summary.

Out of scope:

- Inventing category grouping when Hyperliquid does not send a question graph.
- Reworking the order form for dozens of options beyond preserving the existing option selector model.
- Changing exchange asset id encoding or side routing.

## Implementation Plan

1. Add RED fixtures and tests for the live championship shape:
   - Extend `test/hyperopen/asset_selector/outcome_fixtures.cljs` with `question:32`, fallback outcome `171`, and named outcomes for France, Spain, Argentina, and lower-ranked teams.
   - Extend `test/hyperopen/asset_selector/markets_test.cljs` to assert a single `question:32` row, full `:question-options`, a compact top-three `:outcome-summary`, no standalone team rows, and side-coin resolution back to the grouped row.
   - Extend selector processing tests so the Sports tab includes `question:32` and search matches child labels.
   - Extend row/order-form tests only if the existing view model fails to keep all options selectable.

2. Implement summary helpers in `src/hyperopen/asset_selector/markets.cljs`:
   - Add a deterministic `outcome-option-summary` that sorts options by descending mark, then volume, then label, and emits at most three labels.
   - Add a binary side summary helper for meaningful non-generic side labels.
   - Reuse the same summary helper in question-market construction and expose it for live projection.

3. Update live projection in `src/hyperopen/asset_selector/market_live_projection.cljs`:
   - Recompute question summaries through the shared helper after patching option marks.
   - Preserve bounded top-three behavior after websocket updates.

4. Update deterministic UI coverage:
   - Extend `tools/playwright/test/trade-regressions.spec.mjs` to seed or assert the grouped sports outcome row summary and ensure fallback/team component rows are not primary selector rows.

5. Validate:
   - Run the focused CLJS test command for markets and selector processing, noting that this repo's `--focus` flag may still run the full suite.
   - Run `npm test`, `npm run test:websocket`, and `npm run check`.
   - Run the smallest relevant Playwright outcome smoke.
   - Run governed browser QA for the trade route and `npm run browser:cleanup`.

## Validation / Acceptance Expectations

- `npm test` passes with the grouped championship fixture.
- `npm run test:websocket` passes with no subscription/runtime regressions.
- `npm run check` passes, including docs, namespace-size, and CLJS compile gates.
- Focused Playwright outcome subtab smoke passes after compiling the `app` target.
- Browser QA for the trade route is explicitly accounted for with PASS, FAIL, or BLOCKED status.

## Progress

- [x] Refreshed live `outcomeMeta` and `webData2` evidence.
- [x] Scoped the parity delta against the existing outcome question implementation.
- [x] Added RED tests for grouped category summaries.
- [x] Implemented bounded question and binary side summaries.
- [x] Added/updated browser regression coverage.
- [x] Ran required validation and moved this plan to completed.

## Surprises & Discoveries

- Observation: Hyperliquid's current live payload already exposes World Cup as `question:32`; this follow-up does not need to invent title-derived grouping for that category.
  Evidence: The 2026-06-08 `outcomeMeta` response included `question:32`, `fallbackOutcome 171`, and the full team `namedOutcomes` list.

- Observation: The focused Playwright command can serve a stale `app` bundle after only the CLJS test target has been compiled.
  Evidence: The first focused Playwright run showed the pre-change live World Cup row with every team in the summary. Running `npx shadow-cljs --force-spawn compile app` before rerunning the same Playwright test fixed the browser evidence.

- Observation: The broader non-optimizer smoke run still has an unrelated intermittent subaccount order-routing case.
  Evidence: `npm run test:playwright:smoke -- --grep-invert "portfolio optimizer" --workers=1` passed the updated outcome smoke and 27 other cases, then failed `header account selector routes subaccount order payloads through vaultAddress` because no simulated order/cancel payloads were recorded. Rerunning that single header subaccount test immediately afterward passed.

## Decision Log

- Decision: Treat explicit `outcomeMeta.questions` as authoritative for championship category grouping.
  Rationale: Live Hyperliquid already sends the category graph, so deriving synthetic groups from prose titles would add risk without serving the observed parity gap.
  Date/Author: 2026-06-08 / Codex

- Decision: Keep all named outcomes in `:question-options`, but bound selector row summaries to the top three options by Yes-side mark.
  Rationale: The order form must preserve every tradable option, while the selector row needs Hyperliquid-style compact copy that remains scannable for large categories.
  Date/Author: 2026-06-08 / Codex

- Decision: Render sports binary side labels as `:outcome-summary` only when side labels are meaningful, not generic `Yes` and `No`.
  Rationale: The NBA champion row needs visible child-market side probabilities, but generic binary rows would duplicate the chance column with low-value copy.
  Date/Author: 2026-06-08 / Codex

## Outcomes & Retrospective

Implemented. The slice adds bounded championship question summaries, sports binary side summaries, shared live-projection recomputation, and deterministic unit/Playwright coverage for category rows such as `2026 World Cup Champion` and `2026 NBA Finals champion`.

Validation evidence:

- `npm test -- --focus hyperopen.asset-selector.markets-test` first failed with the intended RED failures for the World Cup and NBA summary strings.
- `npm test -- --focus hyperopen.asset-selector.market-live-projection-test` passed the full runner with 4286 tests and 23699 assertions, 0 failures, 0 errors. The repo runner still ignores `--focus`.
- `npx shadow-cljs --force-spawn compile app` passed before rerunning focused Playwright.
- `npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "outcome subtabs"` passed.
- `git diff --check` passed.
- `npm run test:websocket` passed with 535 tests and 3093 assertions, 0 failures, 0 errors.
- `npm run check` passed after updating the governed namespace-size exception.
- `npm run qa:design-ui -- --targets trade-route --manage-local-app` passed with `reviewOutcome: PASS` across 375, 768, 1280, and 1440 widths. Residual blind spot: hover, active, disabled, and loading states require targeted route actions when not present by default.
- `npm run test:playwright:smoke -- --grep-invert "portfolio optimizer" --workers=1` passed the updated outcome smoke but failed an unrelated header subaccount case; the exact header case passed on focused rerun.
- `npm run browser:cleanup` passed and stopped no lingering sessions.
