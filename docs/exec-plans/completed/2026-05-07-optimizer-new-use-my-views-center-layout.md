# Optimizer New Use My Views Center Layout

Tracked issue: `hyperopen-oknd` ("Align optimizer new use-my-views center layout").

## Goal

Align the center pane of `/portfolio/optimize/new` while the `Use my views` / Black-Litterman return model is active to the attached designer spec: a dedicated explanation workspace with a title/legend, a large prior-vs-posterior chart, three explanatory cards, and the existing run actions at the bottom.

## Scope

- Change only the setup-route Black-Litterman center layout and the preview chart presentation needed for that layout.
- Preserve existing optimizer draft, action, request-building, run, and right-rail editor behavior.
- Preserve the existing non-Black-Litterman setup summary layout.
- Keep the route under `.portfolio-optimizer-v4` and existing data-role conventions.

## Current State

- `src/hyperopen/views/portfolio/optimize/workspace_view.cljs` renders a three-column setup surface.
- `src/hyperopen/views/portfolio/optimize/setup_v4_sections.cljs` renders the center `summary-pane`.
- `src/hyperopen/views/portfolio/optimize/black_litterman_preview_chart.cljs` renders the preview chart with its legend inside the SVG footer.
- `src/hyperopen/views/portfolio/optimize/setup_v4_context.cljs` renders the right rail editor via `black_litterman_views_panel.cljs`.
- Existing deterministic coverage lives in `test/hyperopen/views/portfolio/optimize/setup_v4_layout_test.cljs`, `test/hyperopen/views/portfolio/optimize/black_litterman_preview_chart_test.cljs`, and `tools/playwright/test/optimizer-black-litterman-views.spec.mjs`.

## Design Decisions

- Add a Black-Litterman-specific center branch in `summary-pane` instead of changing the generic setup summary for every preset.
- Move the chart legend for this mode above the chart content so it matches the screenshot and keeps the chart area focused on plotting.
- Add stable data roles for the dedicated center workspace, external legend, chart shell, insight card row, and each insight card.
- Keep the right `Edit views` rail unchanged except for any necessary visual compatibility from shared CSS.
- Use CSS under `src/styles/surfaces/optimizer.css` only for `.portfolio-optimizer-v4` refinements that cannot be expressed cleanly in component classes.

## Tasks

- [x] Add a failing CLJS layout test proving Black-Litterman center mode renders the designer-spec branch, omits the generic summary table, has the external three-item legend, has exactly three insight cards in order, and keeps `portfolio-optimizer-setup-bottom-actions`.
- [x] Add/adjust a focused chart test proving the preview panel can render without the internal SVG legend when used by the setup center.
- [x] Update `black_litterman_preview_chart.cljs` to support an external-legend layout without changing default chart behavior for existing callers.
- [x] Update `setup_v4_sections.cljs` to render the Black-Litterman center workspace: heading, external legend, chart, three cards, assumptions note if still needed, and bottom actions.
- [x] Add scoped optimizer CSS for the new center workspace, chart shell, card row, and sticky action alignment. Not needed; existing optimizer surface classes and component classes satisfy the scoped layout.
- [x] Update Playwright coverage in `optimizer-black-litterman-views.spec.mjs` to assert the center layout across `375`, `768`, `1280`, and `1440`, including no horizontal overflow.
- [x] Run focused CLJS tests first, then focused Playwright, then required gates: `npm run check`, `npm test`, `npm run test:websocket`, and browser cleanup if browser-inspection tooling is used.

## Progress

- 2026-05-07: Verified the RED phase by running the canonical CLJS test runner and confirming the new failures in `setup_v4_layout_test.cljs` and `black_litterman_preview_chart_test.cljs`.
- 2026-05-07: Implemented the preview-chart optional opts contract with `{:legend-layout :external}` while preserving the default internal legend for existing callers.
- 2026-05-07: Replaced the visible Black-Litterman generic summary branch with a dedicated center workspace and kept `setup-bottom-actions`.
- 2026-05-07: Added Playwright coverage for the dedicated center workspace across the four governed widths.
- 2026-05-07: Fixed a browser-only Replicant issue by removing unsupported fragment Hiccup from the non-Black-Litterman branch.
- 2026-05-07: Created `bd` issue `hyperopen-oknd` so the active ExecPlan passes docs governance.
- 2026-05-07: Required gates passed: `npm run check`, `npm test`, `npm run test:websocket`, focused Playwright, and `npm run browser:cleanup`.

## Surprises & Discoveries

- The repo test runner does not support namespace `--focus` flags; passing them causes the runner to ignore the filter and execute the full suite.
- Existing route coverage still referenced `portfolio-optimizer-setup-use-my-views-context`, so the new workspace retains that role around the Black-Litterman explanatory content.
- Hiccup tests allowed `:<>` fragment syntax, but browser QA proved Replicant treats it as an invalid DOM tag. The implementation now splices ordinary child vectors instead.

## Decision Log

- Kept the change inside `src/hyperopen/views/portfolio/optimize/**` and skipped CSS because the approved contract was satisfied with structural Hiccup changes alone.
- Preserved the chart panel heading and default SVG legend behavior, adding only an opt-in external legend mode for the new setup-center caller.
- Updated the older vault-name summary test to exercise the non-Black-Litterman summary path, preserving the new rule that Black-Litterman mode does not render the generic summary table.

## Outcomes & Retrospective

- The new Black-Litterman center workspace contract is implemented in source, covered by CLJS and Playwright assertions, and no scoped CSS was required beyond existing optimizer surface classes.

## Acceptance Criteria

- In `/portfolio/optimize/new` with `:return-model :black-litterman`, the center pane text starts with `Use my views` and `What the model assumes and what your views change`.
- The center pane does not show the generic summary table headed `Summary`.
- The legend appears above the chart and includes `Market reference (prior)`, `Your view`, and `Combined output (posterior)`.
- Three cards render below the chart in order: `Market reference`, `Your views`, `Combined output`.
- The run actions stay visible after the cards and continue dispatching the existing run/save actions.
- Existing editor interactions and active view behavior still pass.

## Validation Notes

- Focused CLJS command: `npm test -- --focus hyperopen.views.portfolio.optimize.setup-v4-layout-test`
- Focused Playwright command: `playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs --grep "use my views"`
- Required repo gates after focused checks: `npm run check`, `npm test`, `npm run test:websocket`.
- Actual GREEN verification on 2026-05-07: `npm test` because the current CLJS runner does not implement namespace `--focus` filtering.
- Browser verification on 2026-05-07: `npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs` passed all 3 tests.
- Required gate verification on 2026-05-07: `npm run check` passed, `npm test` passed with 3,783 tests / 20,857 assertions, `npm run test:websocket` passed with 524 tests / 3,043 assertions, and `npm run browser:cleanup` stopped no lingering sessions.
