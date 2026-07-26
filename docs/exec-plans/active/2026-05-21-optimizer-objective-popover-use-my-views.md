# Optimizer Change Objective Use My Views Popover

## Purpose
Direct user request on 2026-05-21: in the optimizer draft Change Objective popover, selecting `Use my views` must expose return-estimate inputs with confidence levels for the Black-Litterman model, matching the provided design reference.

## Goal
When a user selects `Use my views` inside the Change Objective popover, expose compact Black-Litterman absolute return views with per-asset return estimates and low/medium/high confidence controls before applying and rerunning.

## Progress
- [x] Mapped the current Change Objective popover, Black-Litterman editor model, action wiring, and Playwright coverage.
- [x] Added temporary objective-menu inline view state paths and defaults.
- [x] Added objective-menu inline return/confidence/add/remove actions.
- [x] Rendered compact annualized return rows and confidence controls when `Use my views` is selected.
- [x] Materialized valid inline rows into absolute Black-Litterman views on `Apply & re-run`.
- [x] Added focused action, default-state, Hiccup, and Playwright regression coverage.
- [ ] Complete final repo gates and browser-QA accounting before moving this plan to completed.

## Scope
- Extend the existing objective popover in `src/hyperopen/views/portfolio/optimize/scenario_objective_menu.cljs`.
- Add small draft actions in `src/hyperopen/portfolio/optimizer/actions/draft.cljs` for inline absolute view return text, confidence, row removal, and row addition.
- Keep the existing full Black-Litterman editor as the owner for relative views, notes, horizons, and detailed caveats.
- Preserve existing public action names and runtime action registration.

## Design
The popover will continue to use `:portfolio-ui :optimizer :objective-menu-selection` as the pending objective choice. When the pending choice is `:use-my-views`, it will render a compact `YOUR RETURN VIEWS` section below the objective options.

Rows are keyed by instrument id. The row set comes from temporary UI order if present, otherwise active absolute Black-Litterman views, otherwise the first three draft universe instruments. Each row shows the asset label, a text return input interpreted as an annualized percentage, and low/medium/high confidence buttons. Editing these controls writes only to objective-menu UI draft state until the user presses `Apply & re-run`.

On apply, if `:use-my-views` is selected, the draft action materializes valid nonblank inline rows into absolute Black-Litterman views, preserving existing relative views and replacing absolute views for the edited instruments. Invalid or blank inline rows are ignored so the popover cannot commit malformed model inputs. The resulting return model remains `{:kind :black-litterman :views [...]}` and the existing solver/run path handles Black-Litterman calibration.

## Files
- Modify `src/hyperopen/portfolio/optimizer/contracts/paths.cljs` to name the objective-menu inline view paths.
- Modify `src/hyperopen/portfolio/optimizer/defaults.cljs` to initialize inline view order/drafts.
- Modify `src/hyperopen/portfolio/optimizer/actions/draft.cljs` to add inline view actions and use them during apply.
- Modify `src/hyperopen/portfolio/optimizer/actions.cljs`, `src/hyperopen/runtime/action_adapters.cljs`, `src/hyperopen/portfolio/optimizer/runtime_catalog.cljs`, `src/hyperopen/schema/runtime_registration/portfolio.cljs`, and `src/hyperopen/schema/contracts/action_args.cljs` to expose the new actions.
- Modify `src/hyperopen/views/portfolio/optimize/scenario_objective_menu.cljs` to render and wire the inline controls.
- Modify `src/styles/surfaces/optimizer/results.css` for compact popover rows.
- Update `test/hyperopen/portfolio/optimizer/draft_actions_test.cljs` for action/materialization behavior.
- Update `test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs` for the rendered popover contract.
- Update `tools/playwright/test/optimizer-black-litterman-views.spec.mjs` with a deterministic Change Objective popover flow.

## Validation
- Focused ClojureScript tests:
  - `npm run test:runner:generate`
  - `npx shadow-cljs --force-spawn compile test`
  - `node out/test.js --test=hyperopen.portfolio.optimizer.draft-actions-test --test=hyperopen.views.portfolio.optimize.scenario-detail-view-test`
- Focused Playwright:
  - `npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs -g "objective popover"`
- Required code-change gates:
  - `npm run check`
  - `npm test`
  - `npm run test:websocket`
- UI QA must account for visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf passes across widths `375`, `768`, `1280`, and `1440`. If full governed browser review cannot complete in-session, report it as a blocker rather than claiming UI completion.

## Surprises & Discoveries
- Runtime `:effects/save` paths must be keyword-only. Temporary UI drafts therefore use keywordized instrument ids as path keys, while materialized Black-Litterman views preserve the original string instrument ids.
- The existing local Playwright server on `127.0.0.1:8080` was serving an older app bundle. Focused browser verification used the repo static server on `127.0.0.1:18080` after compiling current app bundles.
- The existing full Black-Litterman editor already owns relative views, caveats, notes, and horizons, so the popover implementation stays intentionally compact and absolute-view only.

## Decision Log
- Use temporary UI state for inline popover edits and materialize only on `Apply & re-run`. This preserves the existing two-step Change Objective interaction and avoids mutating the draft while the user is only previewing a selection.
- Preserve existing relative views during inline absolute-view edits. Relative views are out of scope for the compact popover but must not be discarded.
- Keep blank or invalid inline rows out of the materialized return model. This prevents malformed Black-Litterman views from entering the solver request.
- Use the existing low/medium/high confidence mapping from `black_litterman_editor_model` so the popover and full editor share model semantics.

## Outcomes & Retrospective
- Pending final validation. Early focused checks pass for ClojureScript compile, action/Hiccup unit tests, websocket suite, and the new Playwright regression.
- `npm run check` is currently blocked at docs lint by one pre-existing stale document plus the ExecPlan tracking format, which this update addresses for the new plan.

## Risks
- The compact popover intentionally supports absolute views only. Relative views remain in the full editor, surfaced by helper copy.
- Blank rows are skipped on apply. This avoids invalid Black-Litterman input but means a selected row with no return estimate does not become a view.
- Existing active relative views must be preserved when inline absolute rows are edited.
