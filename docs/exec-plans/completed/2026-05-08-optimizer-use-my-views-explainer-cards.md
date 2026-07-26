# Optimizer Use My Views Explainer Cards Execution Plan

## Goal
Make the three bottom explainer cards in `/portfolio/optimize/new` during `Use my views` match the designer spec and reflect the current Black-Litterman model data instead of static explanatory copy.

## Tracking
- bd: `hyperopen-v2k4`

## Card Analysis

### 1. Market Reference
- The card must explain and show the baseline expected returns before user views are blended.
- Data source: `black-litterman-preview/build-preview` rows, specifically `:prior-return`, using the same instrument ordering and labels as the preview chart.
- Styling target: neutral dark panel, small mono step label `1 · MARKET REFERENCE`, strong title, muted explanatory body, compact two-column ticker/value rows with right-aligned tabular percentages.
- Empty/pending behavior: show a compact message when model readiness is unavailable or no active view preview can be computed; never display zeros as real data.

### 2. Your Views
- The card must show the active user-authored Black-Litterman views.
- Data source: normalized readiness request views when available, falling back to draft views while readiness catches up.
- Styling target: same card system, but accented with the warm warning/gold border and step label because this is the user-authored layer.
- Row behavior: absolute views render as `BTC` plus signed expected return and confidence; relative views render as `ETH > SOL by` or `ETH < SOL by` plus signed return and confidence.
- Empty behavior: explicitly state that no active views exist and show one compact empty-state line.

### 3. Combined Output
- The card must show how the posterior output changed after blending the market reference and the user views.
- Data source: preview rows from `black-litterman-preview/build-preview`, using `:prior-return`, `:posterior-return`, and computed delta.
- Row behavior: show the primary instruments from active views in view order, deduplicated. Fall back to changed preview rows when active-view IDs are not available.
- Styling target: neutral card shell, muted prior value, gold posterior value, muted signed delta in parentheses.
- Note behavior: surface a dynamic confidence note, prioritizing low-confidence views, so users understand why posterior output may only move partway toward a large view.

## Implementation Tasks

- [x] Extract explainer-card rendering into a focused `setup_v4_use_my_views_cards.cljs` namespace.
- [x] Add pure helpers for labels, finite-number checks, percent formatting, confidence labels, view row models, posterior row models, row truncation, and empty/pending states.
- [x] Update `setup_v4_sections.cljs` to render the new cards and keep existing `data-role` anchors.
- [x] Allow the preview chart panel to accept a precomputed preview so chart and cards can share the same model output.
- [x] Update ClojureScript layout tests to assert card styling contracts and dynamic model data for all three cards.
- [x] Add empty-view coverage for the no-active-views state.
- [x] Update the committed Playwright regression to assert the designer card narrative and data rows across the four design-review widths.
- [x] Run targeted ClojureScript and Playwright tests, then required repo gates: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] Run browser cleanup and record the six UI QA pass outcomes in the final handoff.

## Acceptance Criteria
- The three cards read as a left-to-right narrative: market reference, user views, combined output.
- Card 2 is the only accented card.
- Card 1 uses actual prior expected returns.
- Card 2 uses actual active view definitions and confidence.
- Card 3 uses actual posterior output and computed deltas.
- No stale zeros are rendered when model data is unavailable.
- The layout remains stable without horizontal overflow at 375, 768, 1280, and 1440 px.

## Validation
- `npm test` passed: 3,785 tests, 20,884 assertions.
- `npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs` passed: 3/3 tests, including 375, 768, 1280, and 1440 px review widths.
- `npm run check` passed.
- `npm run test:websocket` passed: 524 tests, 3,043 assertions.
- `npm run browser:cleanup` passed with no tracked sessions to stop.
- `git diff --check` passed.
