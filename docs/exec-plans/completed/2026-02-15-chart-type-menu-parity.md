# Implement Sectioned Chart Type Menu Parity with Hyperliquid-Style Ordering

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and is self-contained for contributors who only have this repository checkout.

## Purpose / Big Picture

After this change, users can open the chart type menu and see a Hyperliquid-style grouped order (with visual section breaks), then select additional chart styles that are currently missing. The immediate outcome is a more familiar chart selection UX for users migrating from Hyperliquid, without changing chart data sources or websocket behavior.

A user should be able to open the chart-type dropdown, see grouped menu sections in this order, and switch chart render modes without page refresh:

1. Bars, Candles, Hollow candles
2. Line, Line with markers, Step line
3. Area, HLC area, Baseline
4. Columns, High-low
5. Heikin Ashi

## Progress

- [x] (2026-02-15 19:35Z) Audited current chart-type UI, chart interop capabilities, and chart-type persistence paths.
- [x] (2026-02-15 19:36Z) Verified `lightweight-charts@5.0.8` supports `lineType` and `pointMarkersVisible`, enabling Step line and Line with markers without custom renderer work.
- [x] (2026-02-15 19:38Z) Created this ExecPlan in `/hyperopen/docs/exec-plans/active/2026-02-15-chart-type-menu-parity.md`.
- [x] (2026-02-15 20:01Z) Implemented sectioned chart-type dropdown ordering, new labels, and legacy `:histogram` normalization in `/hyperopen/src/hyperopen/views/trading_chart/chart_type_dropdown.cljs`.
- [x] (2026-02-15 20:09Z) Implemented Wave 1 chart-style profiles and transforms (hollow candles, line with markers, step line, HLC area, columns, high-low, Heikin Ashi) in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`.
- [x] (2026-02-15 20:11Z) Expanded persisted chart-type keys and legacy migration logic in `/hyperopen/src/hyperopen/chart/settings.cljs`.
- [x] (2026-02-15 20:18Z) Added/updated tests for dropdown ordering, interop transforms, and chart-type restore behavior in `/hyperopen/test/hyperopen/views/trading_chart/chart_type_dropdown_test.cljs`, `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop_test.cljs`, and `/hyperopen/test/hyperopen/chart/settings_test.cljs`; wired them into `/hyperopen/test/test_runner.cljs`.
- [x] (2026-02-15 20:23Z) Passed required validation gates: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-02-15 20:26Z) Moved this plan to `/hyperopen/docs/exec-plans/completed/2026-02-15-chart-type-menu-parity.md` after acceptance and gate completion.

## Surprises & Discoveries

- Observation: The current menu is a flat list of six types and does not encode section/group structure.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/chart_type_dropdown.cljs` defines one flat `supported-chart-types` vector and renders all items in one loop.
- Observation: Existing chart interop already has enough primitives for several missing types without introducing custom series rendering.
  Evidence: `lightweight-charts` typings in `/hyperopen/node_modules/lightweight-charts/dist/typings.d.ts` include `LineStyleOptions.lineType` and `pointMarkersVisible`.
- Observation: Chart type persistence currently hard-whitelists keys and will drop unknown types back to `:candlestick`.
  Evidence: `/hyperopen/src/hyperopen/chart/settings.cljs` has a `chart-types` set limited to `:area`, `:bar`, `:baseline`, `:candlestick`, `:histogram`, `:line`.
- Observation: `lineType` must be provided as the enum numeric value in current CLJS interop (`1` for `WithSteps`) to keep options deterministic across compile targets.
  Evidence: Step-line rendering validated in implementation via `add-step-line-series!` in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` and full test gate pass.

## Decision Log

- Decision: Implement this as a two-wave parity pass, with Wave 1 delivering grouped menu + feasible missing styles and Wave 2 reserved only if exact High-low parity needs custom rendering.
  Rationale: Most requested styles are supported by current chart primitives; custom-series investment should be deferred until a concrete visual mismatch remains after Wave 1.
  Date/Author: 2026-02-15 / Codex
- Decision: Treat `:columns` as the user-facing replacement for the current histogram mode and keep compatibility with persisted `"histogram"` values.
  Rationale: The screenshot label is Columns; backward compatibility prevents existing users from losing saved preference.
  Date/Author: 2026-02-15 / Codex
- Decision: Keep chart interaction behavior unchanged (selection updates UI state immediately; no additional fetch/subscription effects).
  Rationale: `/hyperopen/docs/FRONTEND.md` requires deterministic immediate UI updates and no duplicate side effects in interaction flows.
  Date/Author: 2026-02-15 / Codex
- Decision: Add chart-type-specific tests to existing test runner (`chart-type-dropdown`, `chart settings`, `chart interop`) instead of relying on manual checks only.
  Rationale: This closes regression risk for ordering, transform formulas, and persistence migration in future UI changes.
  Date/Author: 2026-02-15 / Codex

## Outcomes & Retrospective

Wave 1 implementation is complete. The chart-type dropdown now matches the requested Hyperliquid-style grouped order and includes the missing options from the screenshot. New chart-type rendering profiles were added for all planned Wave 1 entries, and legacy persisted `histogram` values now restore as `columns`.

Required validation gates passed end to end:

- `npm run check`
- `npm test`
- `npm run test:websocket`

Residual gap intentionally left for future optional Wave 2: exact visual parity tuning for `High-low` if UX review determines the current high-low bar profile needs custom glyph rendering.

## Context and Orientation

Relevant files and roles:

- `/hyperopen/src/hyperopen/views/trading_chart/chart_type_dropdown.cljs`: chart-type menu metadata, labels/icons, and dropdown rendering.
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`: maps selected chart type to Lightweight Charts series creation and data transformation.
- `/hyperopen/src/hyperopen/chart/settings.cljs`: restores and validates persisted chart type from local storage.
- `/hyperopen/src/hyperopen/chart/actions.cljs`: selection action semantics and projection ordering.
- `/hyperopen/test/hyperopen/core_bootstrap_test.cljs`: verifies chart menu actions stay batched and side-effect safe.
- `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs`: chart top-menu structural/style tests.
- `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop_test.cljs`: pure interop behavior tests (currently visible-range persistence coverage).

Terms used in this plan:

- "Sectioned menu" means one dropdown with grouped rows separated by divider lines (like the screenshot), not separate dropdown controls.
- "Profile" means a chart-type definition that specifies both underlying Lightweight Charts series kind and style/data transform behavior.
- "Heikin Ashi" means synthetic OHLC values derived from raw OHLC candles:
  - `haClose = (open + high + low + close) / 4`
  - `haOpen = (previous haOpen + previous haClose) / 2` (first row uses `(open + close) / 2`)
  - `haHigh = max(high, haOpen, haClose)`
  - `haLow = min(low, haOpen, haClose)`

## Plan of Work

Wave 1 starts by replacing the flat `supported-chart-types` list in `/hyperopen/src/hyperopen/views/trading_chart/chart_type_dropdown.cljs` with grouped metadata that preserves deterministic ordering and allows section separators. The dropdown render loop will iterate groups first, then items, drawing divider rows between groups to mirror the Hyperliquid organization.

In `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`, introduce chart-type profiles that decouple user-facing keys from raw series constructors. This adds missing styles while reusing existing series types:

- `:hollow-candles` -> Candlestick series with hollow-up styling.
- `:line-with-markers` -> Line series with `pointMarkersVisible` enabled.
- `:step-line` -> Line series with `lineType` set to `WithSteps`.
- `:hlc-area` -> Area series using `(high + low + close) / 3` value projection.
- `:columns` -> Histogram series with directional per-point colors.
- `:high-low` -> Bar-series profile with high-low-focused styling in Wave 1.
- `:heikin-ashi` -> Candlestick series fed by Heikin Ashi transformed candles.

In `/hyperopen/src/hyperopen/chart/settings.cljs`, extend valid chart types and add migration logic mapping legacy persisted `:histogram` to `:columns` so previous selections remain valid.

Testing work will cover three layers. First, dropdown structure/order assertions to ensure section sequencing and labels match target order. Second, interop unit checks for new pure transforms (Heikin Ashi and HLC area projection) and chart-type profile routing. Third, action-level regression confirmation that `select-chart-type` still emits only projection + local-storage effects (no fetch/subscription side effects).

Wave 2 is optional and only triggered if QA rejects Wave 1 High-low visual parity. That follow-up would evaluate a custom series renderer for strict high-low glyph parity, isolated behind the existing chart-type key without changing user-facing menu order.

## Concrete Steps

All commands run from `/Users//projects/hyperopen`.

1. Implement grouped chart-type metadata and render order.

    rg -n "supported-chart-types|chart-type-dropdown" src/hyperopen/views/trading_chart/chart_type_dropdown.cljs

2. Implement profile-based series creation and transforms for missing chart types.

    rg -n "add-series!|set-series-data!|transform-data" src/hyperopen/views/trading_chart/utils/chart_interop.cljs

3. Extend persistence whitelist and legacy key migration.

    rg -n "chart-types|restore-chart-options|load-chart-option" src/hyperopen/chart/settings.cljs

4. Add/update tests for ordering, transforms, and action safety.

    rg -n "chart-type|select-chart-type|chart-top-menu|chart-interop" test/hyperopen

5. Run required validation gates.

    npm run check
    npm test
    npm run test:websocket

## Validation and Acceptance

Acceptance criteria:

- Dropdown order and grouping visibly match the section sequence listed in "Purpose / Big Picture".
- Existing chart types still render correctly (`Bars`, `Candles`, `Line`, `Area`, `Baseline`).
- New chart types are selectable and render without runtime errors (`Hollow candles`, `Line with markers`, `Step line`, `HLC area`, `Columns`, `High-low`, `Heikin Ashi`).
- Chart-type selection still performs immediate UI projection and local storage persistence with no new network side effects.
- Reloading the app restores selected chart type, including backward-compat restore from persisted `histogram` to `Columns`.
- Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

Manual verification flow:

1. Start app with `npm run dev`.
2. Open chart-type dropdown from trading chart toolbar.
3. Confirm grouped section layout and row order.
4. Select each new type and visually confirm expected render mode.
5. Reload browser and confirm selected type persists.
6. Seed local storage `chart-type=histogram`, reload, and confirm `Columns` is selected.

## Idempotence and Recovery

All steps are additive and safe to re-run. If a specific new chart profile causes rendering issues, disable only that profile key in dropdown metadata and profile routing while leaving grouped menu and other types intact. If Wave 1 High-low approximation is unacceptable, keep the menu item but temporarily route it to the stable `:bar` profile until custom-series follow-up is complete.

## Artifacts and Notes

Reference implementation anchors:

- Current menu source: `/hyperopen/src/hyperopen/views/trading_chart/chart_type_dropdown.cljs`
- Current series router: `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`
- Chart-type persistence: `/hyperopen/src/hyperopen/chart/settings.cljs`

Library capability evidence used for feasibility:

- `/hyperopen/node_modules/lightweight-charts/dist/typings.d.ts` (`lineType`, `pointMarkersVisible`, `BarStyleOptions.openVisible`, candlestick style fields)

## Interfaces and Dependencies

No public action names change. Existing interfaces remain:

- `:actions/toggle-chart-type-dropdown`
- `:actions/select-chart-type`
- `:chart-options :selected-chart-type`

Dependencies and constraints:

- Keep deterministic UI-first action semantics required by `/hyperopen/docs/FRONTEND.md`.
- Preserve existing chart update lifecycle in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`.
- Keep websocket runtime logic unchanged; this is view/interop/persistence work only.

Plan revision note: 2026-02-15 19:38Z - Initial plan created from user-provided Hyperliquid chart-type screenshot and repository chart runtime audit.
Plan revision note: 2026-02-15 20:23Z - Updated with completed implementation, test coverage additions, and validation evidence.
Plan revision note: 2026-02-15 20:26Z - Moved plan from active to completed after all required gates passed.
