---
owner: product
status: supporting
last_reviewed: 2026-05-31
review_cycle_days: 90
source_of_truth: false
---

# Funding Autocorrelation Plot Component PRD

## Objective

Add a compact autocorrelation bar chart to the active-asset funding tooltip so users can visually judge funding-rate persistence over the 30-day analysis window, instead of relying only on three scalar lag values.

## User Problem

`Mean`, `Volatility`, and selected lag values (`1d`, `5d`, `15d`) summarize predictability but do not show structure across intermediate lags. Users cannot quickly tell whether persistence decays smoothly, flips sign at specific horizons, or is mostly noise.

## User Outcome

When a user opens the funding tooltip for a perp asset, they can see:
- a compact bar chart with x-axis lag days and y-axis autocorrelation value
- the autocorrelation profile for lags `1..29` derived from the same 30-day funding window
- immediate visual cues for persistence strength (positive bars), mean reversion (negative bars), and noisy regimes (bars near zero)

## Scope

### In Scope
- Daily-lag autocorrelation series computation from existing 30-day funding cache window.
- Tooltip-level plot component rendered with static SVG bars.
- Existing scalar lag rows (`1d`, `5d`, `15d`) retained.
- Deterministic, read-only visualization (no hover interactions required).

### Out of Scope
- New backend endpoints.
- New global route/page for analytics.
- Confidence intervals, p-values, or statistical significance shading.
- User-configurable lookback windows.

## Data and Statistical Definitions

- Source rows: market `fundingHistory` rows already cached locally.
- Window: last 30 calendar days (same current predictability computation).
- Day aggregation: rows are bucketed by UTC day; each day uses mean funding rate for that day.
- Lag unit: days, not hours.
- Plot series: lag days `1..29`.
- Value per lag: Pearson correlation between daily series `x[t]` and `x[t-lag]` using valid aligned pairs only.
- Missing/undefined lags: when insufficient valid pairs exist, lag value is undefined and rendered as neutral/empty bar.

## UX Requirements

- Component title: `Autocorrelation (30d Daily Lags)`.
- X-axis: lag day progression from `1` to `29`.
- Y-axis: fixed domain `[-1, +1]` with visible zero baseline.
- Bar encoding:
  - positive values use success-toned color
  - negative values use error-toned color
  - undefined values use low-contrast neutral fill
- Keep tooltip readable and compact:
  - no dense numeric labels per bar
  - sparse axis tick labels only
  - preserve existing position/projection/predictability metrics above chart

## Accessibility and Interaction

- Chart must include an accessible label (e.g. `role="img"` with descriptive `aria-label`).
- Meaning cannot be color-only; zero baseline and axis labels must provide structure.
- Must not introduce hover-only dependency for core interpretation.

## Technical Requirements

- Reuse existing funding cache and predictability effect pipeline.
- Keep statistics pure in funding domain module.
- Keep side-effects in runtime effect adapters.
- No API contract changes.
- Tests must cover:
  - series length and lag indexing
  - undefined lag handling
  - tooltip rendering path includes chart title and expected bars metadata

## Acceptance Criteria

1. Tooltip for perp assets shows a new autocorrelation bar chart section.
2. Chart uses lag days `1..29` from 30-day daily aggregated data.
3. Existing `ACF Lag 1d/5d/15d` rows remain present and consistent with plotted lags.
4. Undefined lags render safely without NaN text or runtime errors.
5. `npm run check`, `npm test`, and `npm run test:websocket` pass.
