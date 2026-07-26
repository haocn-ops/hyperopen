# Optimizer Position Side Selection

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan follows `/hyperopen/.agents/PLANS.md` and the optimizer boundary in `src/hyperopen/portfolio/optimizer/BOUNDARY.md`.

## Purpose / Big Picture

Portfolio optimizer users need to choose whether each selected tradable position is modeled as long or short. After this change, the setup universe table and scenario allocation table expose compact `L` and `S` controls like the designer screenshots. A long row is constrained to non-negative target weights, while a short row is constrained to non-positive target weights. Because the existing optimizer already solves with signed weights, the covariance and allocation model incorporate the side through signed target weights and existing portfolio variance math.

## Context References

Public refs:
- Direct user request on 2026-06-08 with designer screenshots showing L/S row controls in the optimizer setup universe and allocation tables.

Repo artifacts:
- `AGENTS.md` requires an ExecPlan for complex UI and optimizer changes.
- `src/hyperopen/portfolio/optimizer/BOUNDARY.md` states that new draft actions belong in optimizer actions, request payload semantics belong in `application.request-builder`, math constraints belong in `domain`, and optimizer route UI belongs in `views.portfolio.optimize.*`.
- `docs/BROWSER_TESTING.md` and `docs/FRONTEND.md` require Playwright and browser-QA accounting for UI-facing work.

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-06-08 20:42Z) Inspected optimizer draft actions, request builder, constraints, setup universe view, scenario allocation table, and existing tests.
- [x] (2026-06-08 20:42Z) Decided to represent side as `:position-side` on each draft universe instrument and derive signed bounds from that field.
- [x] (2026-06-08 20:47Z) Added failing tests for the side action, constraint bounds, setup controls, and allocation side display. The RED run showed missing action vars before implementation.
- [x] (2026-06-08 21:01Z) Implemented the side action, runtime catalog registration, normalized instrument defaults, draft migration, and side-aware constraint bounds.
- [x] (2026-06-08 21:01Z) Rendered the setup row L/S segmented control and scenario allocation side column.
- [x] (2026-06-08 21:04Z) Ran the focused CLJS side tests; 55 tests and 320 assertions passed with 0 failures and 0 warnings.
- [x] (2026-06-08 21:24Z) Ran the broader optimizer CLJS slice; 129 tests and 766 assertions passed.
- [x] (2026-06-08 21:36Z) Rebuilt `app` and `portfolio-optimizer-worker`, then ran the optimizer route Playwright smoke; all 10 tests passed.
- [x] (2026-06-08 22:09Z) Ran final required gates: `npm run check`, `npm test`, `npm run test:websocket`, and optimizer route Playwright smoke all exited 0.
- [x] (2026-06-08 22:10Z) Recorded final evidence and moved this plan to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The optimizer already supports signed weights, gross leverage, net exposure, and shortability-aware lower bounds in `src/hyperopen/portfolio/optimizer/domain/constraints.cljs`.
  Evidence: `encode-constraints-defaults-to-signed-perp-bounds-test` expects perp bounds such as `[-0.8]` to `[0.8]`.
- Observation: Existing result allocation rows already derive textual long/short leg labels from signed target and current weights.
  Evidence: `src/hyperopen/portfolio/optimizer/application/view_model/rebalance.cljs` has `signed-label` and `leg-label` helpers.
- Observation: This worktree initially had a stale or incomplete `node_modules` install, and the first RED run stopped after compilation warnings because `lucide/dist/esm/icons/external-link.js` was missing.
  Evidence: `npm install` added 335 packages; rerunning the same focused test command reached the CLJS assertions.
- Observation: The optimizer route smoke requires the dedicated optimizer worker bundle, not only the app bundle.
  Evidence: A Playwright rerun failed with `Uncaught SyntaxError: Unexpected token '<'` from `/js/portfolio_optimizer_worker.js` until `npx shadow-cljs --force-spawn compile app portfolio-optimizer-worker` generated the worker asset.

## Decision Log

- Decision: Store the user selection as `:position-side` on universe instruments with values `:long` or `:short`; default missing or unsupported values to `:long`.
  Rationale: Universe instruments already carry per-row optimizer metadata and are included in request signatures, scenario persistence, and worker payloads. Keeping the field on the instrument avoids adding a parallel map that can drift from row identity.
  Date/Author: 2026-06-08 / Codex.
- Decision: Implement side by changing constraint bounds, not by creating synthetic inverted assets.
  Rationale: The current engine already models negative weights directly. If a row is short-only, bounds of `[-cap, 0]` let expected returns and covariance participate through the existing signed objective and portfolio variance equations.
  Date/Author: 2026-06-08 / Codex.
- Decision: Keep spot and vault rows long-only even if a caller attempts to select short, unless existing `:shortable?` metadata or an override explicitly makes the row shortable.
  Rationale: The repo already treats perps as shortable and spot/vault rows as not shortable by default. The UI should not imply executable short support for non-shortable rows.
  Date/Author: 2026-06-08 / Codex.

## Outcomes & Retrospective

The optimizer now carries a normalized `:position-side` field on universe instruments. Draft setup rows and scenario allocation rows expose compact L/S controls, with short disabled for non-shortable instruments. The draft action marks setup state dirty; the scenario action saves the updated universe and reruns from the draft.

The math change is intentionally narrow: constraint bounds now honor explicit side. Long rows solve with non-negative bounds, shortable short rows solve with non-positive bounds, and non-shortable short requests are coerced back to long. Existing signed covariance, expected-return, gross/net exposure, and row-labeling behavior remains downstream of those signed weights.

Browser-QA accounting: the optimizer Playwright smoke exercises setup route rendering, draft detail rendering, add-asset interaction, exclusion/rerun interaction, objective controls, responsive containment, and seeded result routes. This accounts for visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf passes at the deterministic smoke level.

## Context and Orientation

The optimizer draft lives under `[:portfolio :optimizer :draft]`. Its `:universe` vector contains instrument maps such as `{:instrument-id "perp:BTC" :market-type :perp :coin "BTC" :shortable? true}`. These maps are created by `src/hyperopen/portfolio/optimizer/actions/common.cljs` when adding from markets or current holdings, then modified by `src/hyperopen/portfolio/optimizer/actions/universe.cljs`.

The setup universe UI is `src/hyperopen/views/portfolio/optimize/setup_universe.cljs`. It renders row data produced by `src/hyperopen/portfolio/optimizer/application/view_model/universe.cljs`. The scenario recommendation allocation table is `src/hyperopen/views/portfolio/optimize/target_exposure_table.cljs`, backed by `src/hyperopen/portfolio/optimizer/application/view_model/rebalance.cljs`.

The request builder, `src/hyperopen/portfolio/optimizer/application/request_builder.cljs`, migrates the draft, aligns history, and passes eligible universe instruments into the engine request. The constraint encoder, `src/hyperopen/portfolio/optimizer/domain/constraints.cljs`, converts each instrument and global constraints into solver lower and upper bounds. Existing signed solver math lives downstream of those bounds.

## Plan of Work

First add tests that describe the new behavior. `test/hyperopen/portfolio/optimizer/universe_actions_test.cljs` should assert that a side action updates the matching draft universe row, marks the draft dirty, rejects short for non-shortable rows, and reruns when used from scenario results. `test/hyperopen/portfolio/optimizer/domain/constraints_test.cljs` should assert that `:position-side :long` produces `[0, cap]` and `:position-side :short` produces `[-cap, 0]` for shortable rows. `test/hyperopen/views/portfolio/optimize/setup_universe_layout_test.cljs` should assert that the setup selected row renders L/S buttons and dispatches the side action. `test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs` or a focused allocation-table test should assert that the result table exposes a side column with `L`/`S` according to signed target weights and draft row selection.

Then implement the action. Add a `set-portfolio-optimizer-universe-instrument-side` function in `src/hyperopen/portfolio/optimizer/actions/universe.cljs`, expose it through `src/hyperopen/portfolio/optimizer/actions.cljs`, and register it in `src/hyperopen/portfolio/optimizer/runtime_catalog.cljs`. The action should update only the matched universe row, normalize side to `:long` or `:short`, coerce unsupported short requests back to `:long`, preserve row metadata, and mark `[:portfolio :optimizer :draft :metadata :dirty?]` true. Add an `and-run` variant only if result-table controls need to rerun immediately after changing a saved scenario.

Next, normalize and consume the field. Update `src/hyperopen/portfolio/optimizer/actions/common.cljs` so newly added instruments default to `:position-side :long`. Update `src/hyperopen/portfolio/optimizer/domain/constraints.cljs` so bounds respect the side field while preserving held-position locks, sparse caps, max long/short caps, and existing `:long-only?` behavior. The request builder can pass the field through naturally because it keeps eligible universe maps from history alignment.

Finally, render controls. Extend `selected-row-model` with side labels and whether short can be selected. Add compact buttons in `setup_universe.cljs` beside the history chip. Extend the rebalance table model with side display and add a `Side` column in `target_exposure_table.cljs`. For target rows, signed negative weights display `S`, positive or flat default to `L` unless the draft row explicitly says `:short`.

## Concrete Steps

Run commands from `/Users/barry/.codex/worktrees/df1c/hyperopen`.

1. Add the focused failing tests:

   `test/hyperopen/portfolio/optimizer/universe_actions_test.cljs`
   `test/hyperopen/portfolio/optimizer/domain/constraints_test.cljs`
   `test/hyperopen/views/portfolio/optimize/setup_universe_layout_test.cljs`
   `test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs`

2. Verify RED with:

   `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.universe-actions-test --test=hyperopen.portfolio.optimizer.domain.constraints-test --test=hyperopen.views.portfolio.optimize.setup-universe-layout-test --test=hyperopen.views.portfolio.optimize.scenario-detail-view-test`

   Expected before implementation: the new assertions fail because the action, bounds, and controls do not exist.

3. Implement production changes in:

   `src/hyperopen/portfolio/optimizer/actions/common.cljs`
   `src/hyperopen/portfolio/optimizer/actions/universe.cljs`
   `src/hyperopen/portfolio/optimizer/actions.cljs`
   `src/hyperopen/portfolio/optimizer/runtime_catalog.cljs`
   `src/hyperopen/portfolio/optimizer/domain/constraints.cljs`
   `src/hyperopen/portfolio/optimizer/application/view_model/universe.cljs`
   `src/hyperopen/views/portfolio/optimize/setup_universe.cljs`
   `src/hyperopen/portfolio/optimizer/application/view_model/rebalance.cljs`
   `src/hyperopen/views/portfolio/optimize/target_exposure_table.cljs`

4. Verify GREEN with the focused command from step 2.

5. Run UI/browser checks. At minimum, run the smallest relevant Playwright optimizer smoke:

   `PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --workers=1`

   If this cannot run locally, record the blocker and the browser-QA pass status in the final response.

6. Run required repo gates:

   `npm run check`
   `npm test`
   `npm run test:websocket`

## Validation and Acceptance

The feature is accepted when the setup universe row shows a stable L/S segmented control for perps, selecting `S` updates the draft row and reruns where appropriate, non-shortable rows cannot be made short, and the engine request encodes selected short rows with non-positive solver bounds. A solved scenario should show `S` for negative target allocations in the allocation side column.

The focused CLJS command should pass after implementation. Final required gates are `npm run check`, `npm test`, and `npm run test:websocket`. UI work must account for the six browser-QA passes from `docs/FRONTEND.md`: visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf.

## Idempotence and Recovery

All edits are source-only and can be rerun safely. If test generation changes `test/test_runner_generated.cljs`, treat that as expected validation churn and inspect the diff before final reporting. If a browser or Playwright process remains running, use `npm run browser:cleanup` before concluding browser QA.

## Artifacts and Notes

Pending. Add focused test transcripts and final gate output summaries here as the work completes.

Focused side test transcript after implementation:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.universe-actions-test --test=hyperopen.portfolio.optimizer.domain.constraints-test --test=hyperopen.views.portfolio.optimize.setup-universe-layout-test --test=hyperopen.views.portfolio.optimize.scenario-detail-view-test
    Ran 55 tests containing 320 assertions.
    0 failures, 0 errors.

Broader optimizer CLJS transcript:

    node out/test.js --test=hyperopen.portfolio.optimizer.actions-test --test=hyperopen.portfolio.optimizer.universe-actions-test --test=hyperopen.portfolio.optimizer.contracts-test --test=hyperopen.portfolio.optimizer.defaults-test --test=hyperopen.portfolio.optimizer.application.request-builder-test --test=hyperopen.portfolio.optimizer.application.engine-test --test=hyperopen.portfolio.optimizer.application.engine-current-portfolio-test --test=hyperopen.portfolio.optimizer.application.engine-sparse-caps-test --test=hyperopen.portfolio.optimizer.application.setup-readiness-test --test=hyperopen.views.portfolio.optimize.setup-universe-layout-test --test=hyperopen.views.portfolio.optimize.scenario-detail-view-test --test=hyperopen.views.portfolio.optimize.results-panel-test --test=hyperopen.views.portfolio.optimize.view-test
    Ran 129 tests containing 766 assertions.
    0 failures, 0 errors.

Playwright browser smoke transcript:

    npx shadow-cljs --force-spawn compile app portfolio-optimizer-worker
    PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --workers=1
    10 passed.

Final gate transcripts:

    npm run check
    exit 0

    npm test
    exit 0

    npm run test:websocket
    Ran 534 tests containing 3090 assertions.
    0 failures, 0 errors.

    PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --workers=1
    10 passed.

## Interfaces and Dependencies

The new draft field is:

    :position-side :long

or:

    :position-side :short

The public action is:

    [:actions/set-portfolio-optimizer-universe-instrument-side instrument-id side]

The action accepts `side` as a keyword or keyword-like string. It normalizes only `:long` and `:short`; anything else becomes `:long`.
