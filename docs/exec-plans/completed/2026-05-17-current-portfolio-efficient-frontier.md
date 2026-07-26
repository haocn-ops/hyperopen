# Current Portfolio Efficient Frontier Integration

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The portfolio optimizer result already computes current portfolio expected return and volatility from the same return and covariance inputs used for the target allocation. The efficient frontier chart, however, currently renders the frontier, target portfolio, and asset overlays while explicitly omitting the current portfolio marker. After this change, users can see where their current allocation sits relative to feasible frontier alternatives and the selected target allocation.

This work builds on the Optimizer History API v2 integration. When users seed the optimizer universe from current holdings, the existing backend `history-bundle` endpoint can request all selected current-portfolio instruments in one batched call, so plotting the current point does not require browser-side per-asset fanout.

## Context References

Public refs:

- Direct user request on 2026-05-17: create an execution plan and implement current portfolio integration into the efficient frontier, relying on the backend API path that can fetch a full current-portfolio universe quickly.

Repo artifacts:

- `/hyperopen/.agents/PLANS.md` is the detailed ExecPlan contract.
- `/hyperopen/docs/PLANS.md` defines active/completed ExecPlan lifecycle.
- `/hyperopen/docs/FRONTEND.md` and `/hyperopen/docs/BROWSER_TESTING.md` govern UI validation.
- `/hyperopen/docs/exec-plans/active/2026-05-14-optimizer-history-api-v2-integration.md` records the backend history API v2 path and batched `history-bundle` behavior.
- `/hyperopen/docs/exec-plans/completed/2026-04-28-portfolio-optimizer-frontier-callouts.md` describes the prior chart intent for target and current portfolio callouts.

Local scratch refs, non-authoritative:

- None.

## Progress

- [x] (2026-05-18 00:06Z) Inspected optimizer result construction, current portfolio snapshot generation, frontier chart rendering, prior optimizer ExecPlans, and existing chart contract tests.
- [x] (2026-05-18 00:06Z) Created this active ExecPlan from the direct request.
- [x] (2026-05-18 00:11Z) Added RED tests for current marker domain inclusion and results chart rendering; focused run failed on the expected missing current point/marker/callout assertions.
- [x] (2026-05-18 00:18Z) Implemented the current portfolio marker and callout without changing solver or target-selection behavior; focused chart tests passed.
- [x] (2026-05-18 00:25Z) Updated deterministic Playwright coverage so a solved result with current weights/current metrics renders the current marker and hover callout.
- [x] (2026-05-18 00:32Z) Ran focused tests, governed browser review, cleanup, and all required repo gates.
- [x] (2026-05-18 02:49Z) Investigated user screenshots showing the current marker only when HYPE was part of the selected universe; confirmed current metrics were still selected-universe scoped.
- [x] (2026-05-18 03:09Z) Added RED coverage for a separate current-portfolio history universe, separate current-history storage, engine metrics for HYPE outside a BTC/ETH selected universe, and chart rendering from `:current-portfolio-weights`.
- [x] (2026-05-18 03:26Z) Implemented the separate current-history request/result path and current-only engine analysis while keeping selected optimizer history/frontier inputs selected-only.
- [x] (2026-05-18 03:55Z) Split the outside-universe current-portfolio engine regression into a focused test namespace after `npm run check` caught the enlarged engine test namespace.
- [x] (2026-05-18 04:03Z) Re-ran focused tests, governed browser design QA, cleanup, `git diff --check`, and all required repo gates on the final patch.

## Surprises & Discoveries

- Observation: The optimizer result payload already includes current portfolio metrics.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/application/engine/payload.cljs` computes `:current-expected-return`, `:current-volatility`, `:current-performance`, and `:current-weights-by-instrument`.

- Observation: The current chart deliberately omits the current marker today.
  Evidence: `/hyperopen/test/hyperopen/views/portfolio/optimize/frontier_chart_contract_test.cljs` asserts `(nil? current-marker)` and the Playwright optimizer regression asserts `[data-role='portfolio-optimizer-frontier-current-marker']` has count 0.

- Observation: The backend optimizer history API is already the default path for selected universes.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/infrastructure/history_api_v2_client.cljs` posts all selected `:universe` instruments to `/v1/optimizer/history-bundle` with `include_aligned_returns`.

- Observation: The first RED run exposed missing local npm dependencies before assertions could run.
  Evidence: `node out/test.js --test=hyperopen.views.portfolio.optimize.frontier-chart-model-test --test=hyperopen.views.portfolio.optimize.frontier-chart-contract-test` initially failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`; `node_modules` was absent. After `npm install`, the same command reached the expected RED failures.

- Observation: The browser regression cannot reliably seed current account state by mutating `:webdata2 :clearinghouseState` before the pipeline run, because the app's background account refresh can overwrite that test state before readiness builds the optimizer request.
  Evidence: A failed Playwright attempt rendered current summary values as `0.00% -> target` after the seeded clearinghouse state was overwritten by the test runtime's account refresh. The committed Playwright assertion now mutates the solved result fixture after the run so it covers the chart/UI contract directly, while CLJS tests cover the current-point model predicate.

- Observation: The first implementation still computed `:current-expected-return` and `:current-volatility` from `current-weights*` aligned only to optimizer-selected instruments.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/application/engine/context.cljs` produced `current-weights` by mapping `[:current-portfolio :by-instrument]` over selected risk-model instrument ids, and `/hyperopen/src/hyperopen/portfolio/optimizer/application/engine/payload.cljs` computed current metrics from that selected vector.

- Observation: Adding current-only assets to the selected API v2 `history-bundle` request would risk changing the efficient frontier by narrowing aligned returns to a larger common calendar.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs` uses the response `:return-calendar` when aligned returns are usable, so selected optimizer history must remain selected-universe scoped.

- Observation: The outside-universe engine regression made the existing engine test namespace exceed the repository namespace-size limit.
  Evidence: `npm run check` failed in `lint:namespace-sizes` with `test/hyperopen/portfolio/optimizer/application/engine_test.cljs - namespace has 553 lines`; moving the new regression to `/hyperopen/test/hyperopen/portfolio/optimizer/application/engine_current_portfolio_test.cljs` brought the check back to PASS without adding a size exception.

## Decision Log

- Decision: Reuse existing result payload metrics instead of changing optimizer math.
  Rationale: `:current-expected-return` and `:current-volatility` are computed from the same expected-return vector and covariance matrix as target and frontier points. Plotting those values is a presentation change; changing solver inputs would risk altering target selection.
  Date/Author: 2026-05-18 / Codex

- Decision: Scope this implementation to a first-class current marker on the efficient frontier chart.
  Rationale: The data-fetching foundation already exists through API v2 history bundles and the "Use Current Holdings" universe action. The visible missing behavior is that the chart does not plot current allocation once those inputs are available.
  Date/Author: 2026-05-18 / Codex

- Decision: Render a current marker only when the solved result has finite current risk/return metrics and non-zero current allocation exposure.
  Rationale: A manually-created scenario can legitimately solve with all current weights equal to zero. Rendering a `0,0` point as "current portfolio" would be misleading and produced a sparse callout with no allocation section.
  Date/Author: 2026-05-18 / Codex

- Decision: Fetch current-portfolio history as a separate bundle when the current holding set differs from the selected optimizer universe.
  Rationale: This lets the current marker include holdings like HYPE even when the selected frontier is BTC/ETH/SOL, without allowing HYPE to constrain the selected assets' aligned return calendar or enter the solver/frontier universe.
  Date/Author: 2026-05-18 / Codex

- Decision: Keep `:current-weights` and `:current-weights-by-instrument` aligned to selected optimizer instruments, and add `:current-portfolio-weights`, `:current-portfolio-instrument-ids`, and `:current-portfolio-weights-by-instrument` for the plotted current marker.
  Rationale: Existing diagnostics and rebalance preview code depend on selected-universe current weights. The marker needs full current-portfolio allocation separately.
  Date/Author: 2026-05-18 / Codex

## Outcomes & Retrospective

Implemented. The efficient frontier chart model now exposes `:current-point` only when current return, volatility, and non-zero current allocation exposure are present. The chart domain includes that current point so out-of-frontier current allocations are not clipped. The SVG layers render a focusable `portfolio-optimizer-frontier-current-marker` and `portfolio-optimizer-frontier-callout-current` using the shared callout model, including current return, volatility, Sharpe, gross exposure, net exposure, and allocation rows.

Follow-up implemented after user scenario testing: the current marker now uses a current-portfolio analysis that is independent from the selected optimizer universe. Full history loads carry a separate `:current-portfolio-universe` derived from non-zero current exposures. The runtime adapter stores that response under `:current-portfolio-history-data`, and the engine computes current risk/return from `:current-portfolio-history` while leaving selected solver/frontier inputs unchanged.

Changed files:

- `/hyperopen/docs/exec-plans/completed/2026-05-17-current-portfolio-efficient-frontier.md`
- `/hyperopen/src/hyperopen/views/portfolio/optimize/frontier_chart_model.cljs`
- `/hyperopen/src/hyperopen/views/portfolio/optimize/frontier_chart_layers.cljs`
- `/hyperopen/src/hyperopen/views/portfolio/optimize/frontier_callout.cljs`
- `/hyperopen/src/hyperopen/views/portfolio/optimize/frontier_current.cljs`
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/engine/context.cljs`
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/engine/payload.cljs`
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_workflow.cljs`
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/request_builder.cljs`
- `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer/history.cljs`
- `/hyperopen/test/hyperopen/portfolio/optimizer/application/engine_current_portfolio_test.cljs`
- `/hyperopen/test/hyperopen/portfolio/optimizer/application/history_workflow_test.cljs`
- `/hyperopen/test/hyperopen/runtime/effect_adapters/portfolio_optimizer_history_test.cljs`
- `/hyperopen/test/hyperopen/views/portfolio/optimize/frontier_chart_model_test.cljs`
- `/hyperopen/test/hyperopen/views/portfolio/optimize/frontier_chart_contract_test.cljs`
- `/hyperopen/test/test_runner_generated.cljs`
- `/hyperopen/tools/playwright/test/portfolio-regressions.spec.mjs`

Validation completed on 2026-05-18:

- `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.views.portfolio.optimize.frontier-chart-model-test --test=hyperopen.views.portfolio.optimize.frontier-chart-contract-test`: 3 tests, 125 assertions, 0 failures, 0 errors.
- `npm run css:build && npx shadow-cljs --force-spawn compile app portfolio-worker portfolio-optimizer-worker vault-detail-worker`: build completed with 0 warnings.
- `npx shadow-cljs --force-spawn compile app portfolio-optimizer-worker`: build completed with 0 warnings after omitting empty `:current-portfolio-universe` from requests.
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs -g "portfolio optimizer recommendation chart" --workers=1`: 1 passed. Two earlier runs failed because the optimizer history prefetch timed out when an empty current-portfolio universe changed request identity; omitting the empty field fixed the regression.
- `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.history-workflow-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-history-test --test=hyperopen.portfolio.optimizer.application.engine-current-portfolio-test --test=hyperopen.views.portfolio.optimize.frontier-chart-model-test --test=hyperopen.views.portfolio.optimize.frontier-chart-contract-test && npm run lint:namespace-sizes`: 20 tests, 198 assertions, 0 failures, 0 errors; namespace size check passed.
- `npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app`: `reviewOutcome: PASS`, run `design-review-2026-05-18T03-02-33-883Z-9072f0b6`, with visual-evidence, native-control, styling-consistency, interaction, layout-regression, and jank/perf PASS at 375/768/1280/1440. Residual blind spot from the tool: hover, active, disabled, and loading states still require targeted route actions when not present by default.
- `npm run browser:cleanup`: ok, stopped 0 sessions.
- `git diff --check`: passed.
- `npm run check`: passed.
- `npm test`: 3950 tests, 21728 assertions, 0 failures, 0 errors.
- `npm run test:websocket`: 524 tests, 3043 assertions, 0 failures, 0 errors.

Residual risk: the browser regression proves the current marker/callout for a solved result with current metrics. The full live "Use Current Holdings" flow remains dependent on account refresh timing and the backend history bundle, but this change did not alter that data-fetching path.

## Context and Orientation

The optimizer results surface is rendered by `/hyperopen/src/hyperopen/views/portfolio/optimize/results_panel.cljs`, which delegates the frontier visualization to `/hyperopen/src/hyperopen/views/portfolio/optimize/frontier_chart.cljs`. The chart builds a model in `/hyperopen/src/hyperopen/views/portfolio/optimize/frontier_chart_model.cljs`, renders SVG layers in `/hyperopen/src/hyperopen/views/portfolio/optimize/frontier_chart_layers.cljs`, renders the target marker in `/hyperopen/src/hyperopen/views/portfolio/optimize/frontier_target.cljs`, and renders asset overlays in `/hyperopen/src/hyperopen/views/portfolio/optimize/frontier_overlay_markers.cljs`.

The optimizer engine builds solved result maps in `/hyperopen/src/hyperopen/portfolio/optimizer/application/engine/payload.cljs`. A solved result includes the efficient frontier, target allocation metrics, asset overlay data, and current portfolio metrics. The current metrics are annualized risk and return for the current weight vector over the eligible optimizer universe.

The current portfolio snapshot is produced by `/hyperopen/src/hyperopen/portfolio/optimizer/application/current_portfolio.cljs`. The action `/hyperopen/src/hyperopen/portfolio/optimizer/actions/universe.cljs` `set-portfolio-optimizer-universe-from-current` turns current holdings into optimizer universe rows. With the API v2 path enabled, history for that full universe is fetched through one `history-bundle` request.

## Plan of Work

First, add failing tests. Extend `/hyperopen/test/hyperopen/portfolio/optimizer/application/engine_test.cljs` to assert that solved results expose finite current metrics and current weights when current holdings are part of the universe. Extend `/hyperopen/test/hyperopen/views/portfolio/optimize/frontier_chart_contract_test.cljs` to assert that the results panel renders `[data-role='portfolio-optimizer-frontier-current-marker']`, `[data-role='portfolio-optimizer-frontier-callout-current']`, readable "Current" copy, and current gross/net exposure rows. Extend `/hyperopen/test/hyperopen/portfolio/optimizer/application/view_model_frontier_test.cljs` or a chart-model test if needed so the chart domain includes the current point; otherwise an out-of-frontier current allocation could be clipped.

Second, implement a small current portfolio chart model. Prefer keeping the calculation in view/chart namespaces because the engine result already owns the numerical values. The chart model should return a `:current-point` only when `:current-expected-return` and `:current-volatility` are finite. The x/y domains should include this current point alongside frontier, target, and overlay points.

Third, add SVG rendering for the current portfolio marker. The marker should use stable data roles `portfolio-optimizer-frontier-current-marker` and `portfolio-optimizer-frontier-callout-current`, be keyboard focusable, and use the shared `frontier-callout` helper for rows. It should be visually distinct from the target marker but less dominant than the target. The callout should show expected return, volatility, Sharpe, gross exposure, net exposure, and current allocation summary.

Fourth, update deterministic browser coverage. The existing optimizer Playwright regression currently asserts no current marker; update it to expect the marker when the solved result includes current weights. If running the full browser flow is blocked by local dev-server state, record the blocker and run focused CLJS contract tests as deterministic coverage.

Finally, run validation from `/hyperopen`:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.application.engine-test --test=hyperopen.portfolio.optimizer.application.view-model-frontier-test --test=hyperopen.views.portfolio.optimize.frontier-chart-contract-test
    git diff --check
    npm run check
    npm test
    npm run test:websocket

Because this touches UI rendering, browser-QA passes must be accounted for before signoff. Use the smallest practical Playwright optimizer regression and, if Browser MCP/design review is not run, record it as a remaining risk rather than claiming full visual QA.

## Validation and Acceptance

Acceptance is met when a solved optimizer result with current portfolio weights renders a current portfolio marker on the efficient frontier chart. The marker must be included in chart scaling, be inspectable through hover/focus callout, and report the same current expected return and volatility values that the result summary uses. Target selection, frontier point click/drag behavior, and asset overlay modes must keep their existing behavior.

Focused CLJS tests must fail before implementation and pass after. Required gates for code changes are `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

The change is additive to result visualization. It does not mutate optimizer scenarios, current portfolio snapshots, backend history requests, or solver inputs. If current metrics are missing or non-finite, the current marker should simply not render, preserving existing behavior.

All listed tests and browser commands are safe to rerun. If browser-inspection sessions are created during validation, run `npm run browser:cleanup` before completion.

## Artifacts and Notes

Plan revision note: 2026-05-18 00:06Z - Created the active ExecPlan after source exploration. The scope is intentionally chart integration for already-computed current portfolio metrics, with API v2 noted as the existing batched history source for current-holdings universes.

## Interfaces and Dependencies

At completion, `/hyperopen/src/hyperopen/views/portfolio/optimize/frontier_chart_model.cljs` should expose current-point data in the chart model only for finite current metrics. `/hyperopen/src/hyperopen/views/portfolio/optimize/frontier_chart_layers.cljs` should render that point with stable `data-role` anchors. No backend API contract or optimizer engine request shape should change unless tests prove current metrics are missing from solved results.
