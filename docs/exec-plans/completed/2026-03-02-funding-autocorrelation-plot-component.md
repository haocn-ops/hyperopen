# Funding Tooltip Autocorrelation Plot (30d)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the funding tooltip will include a compact autocorrelation bar plot that shows lag structure across the full 30-day horizon (`1..29` day lags). Users will be able to see if funding persistence decays smoothly, oscillates, or collapses to noise, instead of inferring behavior from only `1d`, `5d`, and `15d` scalar rows.

The behavior is verifiable by selecting a perp market, opening the funding tooltip, and observing the new `Autocorrelation (30d Daily Lags)` chart with bars centered around a visible zero baseline.

## Progress

- [x] (2026-03-02 00:12Z) Reviewed UI/runtime/planning policy docs required for UI changes.
- [x] (2026-03-02 00:15Z) Added product spec: `/hyperopen/docs/product-specs/funding-autocorrelation-plot-component-prd.md`.
- [x] (2026-03-02 00:16Z) Indexed product spec in `/hyperopen/docs/product-specs/index.md`.
- [x] (2026-03-02 00:39Z) Implemented lag-series computation (`1..29`) in `/hyperopen/src/hyperopen/funding/predictability.cljs` and preserved scalar lag projections (`1d`, `5d`, `15d`) from that series.
- [x] (2026-03-02 00:47Z) Added reusable SVG autocorrelation plot component in `/hyperopen/src/hyperopen/views/autocorrelation_plot.cljs`.
- [x] (2026-03-02 00:56Z) Rendered chart in funding tooltip (`/hyperopen/src/hyperopen/views/active_asset_view.cljs`) beneath Predictability rows.
- [x] (2026-03-02 01:05Z) Added and updated tests for series computation and chart/tooltip rendering.
- [x] (2026-03-02 01:21Z) Ran required gates: `npm run check`, `npm test`, and `npm run test:websocket` (all passed).

## Surprises & Discoveries

- Observation: Existing tooltip predictability view currently presents only five scalar rows (mean, volatility, and three lag points), with no lag-curve visualization.
  Evidence: `/hyperopen/src/hyperopen/views/active_asset_view.cljs`.

- Observation: Current predictability math already aggregates funding rows to UTC daily means before computing lag autocorrelation.
  Evidence: `/hyperopen/src/hyperopen/funding/predictability.cljs` functions `daily-series` and `lag-autocorrelation`.

- Observation: Some lags become undefined with small daily samples, so visual rendering must explicitly handle missing values and keep bar placement deterministic.
  Evidence: `/hyperopen/test/hyperopen/funding/predictability_test.cljs` asserts undefined tail-lag behavior; `/hyperopen/test/hyperopen/views/autocorrelation_plot_test.cljs` covers clamping/sorting behavior.

## Decision Log

- Decision: Keep the chart inside the existing funding tooltip rather than creating a new route/panel.
  Rationale: User asked for a component tied to current 30-day tooltip analytics; this delivers the feature with minimal navigation overhead.
  Date/Author: 2026-03-02 / Codex

- Decision: Use a lightweight static SVG bar chart instead of integrating a charting library.
  Rationale: The plot is small, deterministic, and read-only; SVG keeps payload and complexity low while remaining testable.
  Date/Author: 2026-03-02 / Codex

- Decision: Keep autocorrelation chart values unitless on a fixed `[-1,1]` y-axis while continuing to show annualized units for mean/volatility rows.
  Rationale: Correlation is dimensionless and directly comparable across lags, while yield statistics remain more interpretable to users when annualized.
  Date/Author: 2026-03-02 / Codex

## Outcomes & Retrospective

Completed. The tooltip now shows a compact `Autocorrelation (30d Daily Lags)` bar chart that visualizes lag structure for day lags `1..29`, based on the same daily aggregated funding series used by existing predictability metrics. The new chart is rendered only when predictability data is available, and existing predictability rows (mean, volatility, `1d/5d/15d` lags) remain intact.

Validation succeeded through the repository-required gates (`npm run check`, `npm test`, `npm run test:websocket`), and targeted tests were added for both domain computation and chart rendering integration. Remaining follow-up work, if desired, is cosmetic tuning (spacing/labels) rather than functional completion.

## Context and Orientation

Funding tooltip rendering lives in `/hyperopen/src/hyperopen/views/active_asset_view.cljs`. Predictability statistics are computed in `/hyperopen/src/hyperopen/funding/predictability.cljs` and projected by the runtime effect path previously implemented.

The existing summary includes:
- annualized mean and annualized volatility rows (display units)
- scalar ACF rows for lag `1d`, `5d`, and `15d`

This plan extends that by adding a full lag series and a compact visual.

## Plan of Work

First, extend the predictability domain output to include `:autocorrelation-series`, a vector of lag objects for `1..29` days. Each element will carry lag index and value (or undefined markers) so view rendering does not recompute math.

Second, add a dedicated view component file for the chart. The component will accept the lag series and render bars on an SVG canvas with fixed y-domain `[-1,1]`, zero baseline, and sparse axis labels.

Third, wire the component into `funding-tooltip-panel` beneath the predictability rows. The tooltip retains existing rows and lag-note copy.

Fourth, add tests for:
- series shape and lag indexing in predictability domain tests
- tooltip/chart rendering presence and key labels in view tests

Finally, run all required validation gates and update this ExecPlan with outcomes.

## Concrete Steps

From repo root `/hyperopen`:

1. Edit predictability domain module:
   - `/hyperopen/src/hyperopen/funding/predictability.cljs`

2. Add autocorrelation plot view component:
   - `/hyperopen/src/hyperopen/views/autocorrelation_plot.cljs`

3. Integrate chart into funding tooltip:
   - `/hyperopen/src/hyperopen/views/active_asset_view.cljs`

4. Add/update tests:
   - `/hyperopen/test/hyperopen/funding/predictability_test.cljs`
   - `/hyperopen/test/hyperopen/views/autocorrelation_plot_test.cljs`
   - `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs`
   - regenerate `/hyperopen/test/test_runner_generated.cljs` via test command

5. Run required validation:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

## Validation and Acceptance

Acceptance is complete when:

1. Funding tooltip includes section label `Autocorrelation (30d Daily Lags)`.
2. Chart shows lag bars across `1..29` for a perp asset with sufficient cached data.
3. Existing predictability rows still render correctly.
4. Undefined lags do not show runtime errors and are rendered as neutral/empty bars.
5. Required gates pass with no new failures.

Manual check:
- Select `BTC` perp.
- Open funding tooltip.
- Confirm the new chart appears below predictability rows and updates when switching assets.

## Idempotence and Recovery

All changes are additive to current tooltip/predictability flow and can be rerun safely. If the chart integration regresses the tooltip, remove only the chart render call while keeping lag-series computation intact; scalar rows continue to function.

## Artifacts and Notes

No external dependencies are required. The SVG plot should remain deterministic and style-token aligned with existing tooltip palette.

Validation transcript highlights:

    npm run check
    ...
    [:test] Build completed. (659 files, 6 compiled, 58 warnings, 1.57s)

    npm test
    ...
    Ran 1686 tests containing 8805 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    ...
    Ran 289 tests containing 1645 assertions.
    0 failures, 0 errors.

## Interfaces and Dependencies

Expected interface additions:

- In `/hyperopen/src/hyperopen/funding/predictability.cljs`:
  - summary map adds `:autocorrelation-series` vector entries with at minimum:
    - `:lag-days`
    - `:value`
    - `:undefined?`
    - `:insufficient?`

- In `/hyperopen/src/hyperopen/views/autocorrelation_plot.cljs`:
  - `autocorrelation-plot` function:
    - input: lag-series vector
    - output: hiccup SVG node

Revision note: 2026-03-02 00:16Z - Initial ExecPlan created after product spec and policy review; implementation pending.
Revision note: 2026-03-02 01:21Z - Updated all living sections to completed state after implementation and full validation; added evidence and final outcome summary.
