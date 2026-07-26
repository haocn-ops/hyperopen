# Current Portfolio History Readiness Fix

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The current portfolio marker should appear on the efficient frontier the first time a user runs an optimizer scenario, even when none of the current holdings are part of the selected optimizer universe. A user reported that the marker is missing on a clean BTC/ETH-style run, appears after they temporarily add held assets such as HYPE/PUMP to the selected universe, and continues to appear after they remove those held assets. That order-dependent behavior means the marker is currently relying on incidental history cache contents instead of a deterministic current-portfolio history fetch.

After this change, the optimizer run pipeline must detect when selected-universe history is ready but outside-universe current-portfolio history is missing, fetch the current-portfolio bundle, and then run the optimizer. The selected frontier must remain selected-universe scoped.

## Context References

Public refs:

- Direct user report on 2026-05-18 with screenshot: current marker appears only after held assets were added to the selected universe once and then removed.

Repo artifacts:

- `/hyperopen/docs/exec-plans/completed/2026-05-17-current-portfolio-efficient-frontier.md` introduced the current marker and separate current-history bundle.
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/pipeline_workflow.cljs` decides whether to run immediately or load history.
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` builds the engine request used by the pipeline.
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/request_builder.cljs` assembles selected and current-portfolio history inputs.
- `/hyperopen/docs/FRONTEND.md` and `/hyperopen/docs/BROWSER_TESTING.md` govern UI validation.

## Progress

- [x] (2026-05-18 13:05Z) Reproduced the state-order bug from code inspection: selected history readiness can skip the current-history request.
- [x] (2026-05-18 13:07Z) Created this ExecPlan from the user follow-up.
- [x] (2026-05-18 13:16Z) Added RED tests for the pipeline and request builder; they failed because selected-ready runs skipped current history and request building used incidental selected-cache data.
- [x] (2026-05-18 13:24Z) Implemented deterministic current-history readiness and selected-history cache isolation; focused bug tests passed.
- [x] (2026-05-18 13:58Z) Ran focused tests, browser coverage, required gates, and recorded outcomes.

## Surprises & Discoveries

- Observation: `pipeline-workflow/begin-run` runs immediately when `setup-readiness/build-readiness` reports the selected universe as runnable.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/application/pipeline_workflow.cljs` checks `(:runnable? initial-readiness)` before issuing `:optimizer.workflow/load-history`.

- Observation: `setup-readiness/build-readiness` currently evaluates selected-universe history completeness only.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` compares `:requested-universe` against selected `:universe`, but does not check whether `:current-portfolio-history` exists for outside-universe current holdings.

- Observation: `request-builder/build-engine-request` can compute outside-universe current metrics from the general selected history cache when no separate current-history bundle exists.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/application/request_builder.cljs` currently falls back from `(:current-portfolio-history-data history-data)` to `history-data`. That lets a previous run that selected HYPE/PUMP mask the missing current-history bundle on later runs.

- Observation: Governed design QA could not be rerun with managed local app because the user's live shadow-cljs/nREPL session already owns the project shadow server port.
  Evidence: `npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app` and the same command with `--local-url http://localhost:8080/index.html` both failed before inspection with `shadow-cljs already running in project on http://localhost:9630`; browser cleanup found no sessions to stop.

## Decision Log

- Decision: Treat current-portfolio history as a run-readiness dependency only when current holding ids are not a subset of the selected requested universe and the built request lacks `:current-portfolio-history`.
  Rationale: Selected history already covers current holdings when they are selected. Outside-universe current holdings require the separate bundle, but a failed or unavailable current-history bundle should not permanently block the optimization run after the load attempt completes.
  Date/Author: 2026-05-18 / Codex

- Decision: Stop using the selected history cache as a fallback source for outside-universe current history.
  Rationale: The marker must be deterministic from the current-portfolio bundle, not from stale general cache entries left by prior selected universes.
  Date/Author: 2026-05-18 / Codex

## Outcomes & Retrospective

Implemented. The request builder no longer populates outside-universe `:current-portfolio-history` from the selected history cache when `:current-portfolio-history-data` is absent. The run pipeline now asks the history loader to run before worker execution when the selected universe is runnable but outside-universe current holdings lack current-history coverage.

Changed files:

- `/hyperopen/docs/exec-plans/completed/2026-05-18-current-portfolio-history-readiness.md`
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/pipeline_workflow.cljs`
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/request_builder.cljs`
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs`
- `/hyperopen/test/hyperopen/portfolio/optimizer/application/pipeline_workflow_test.cljs`
- `/hyperopen/test/hyperopen/portfolio/optimizer/application/request_builder_test.cljs`
- `/hyperopen/test/hyperopen/runtime/effect_adapters/portfolio_optimizer_pipeline_test.cljs`
- `/hyperopen/test/test_runner_generated.cljs`

Validation completed on 2026-05-18:

- RED: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.request-builder-test --test=hyperopen.portfolio.optimizer.application.pipeline-workflow-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-pipeline-test`: failed as expected. Failures showed the selected-cache fallback and immediate worker run before current-history load.
- GREEN: `npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.request-builder-test --test=hyperopen.portfolio.optimizer.application.pipeline-workflow-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-pipeline-test`: 20 tests, 90 assertions, 0 failures, 0 errors.
- Broader focused regression: `npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.request-builder-test --test=hyperopen.portfolio.optimizer.application.pipeline-workflow-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-pipeline-test --test=hyperopen.portfolio.optimizer.application.history-workflow-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-history-test --test=hyperopen.portfolio.optimizer.application.engine-current-portfolio-test --test=hyperopen.views.portfolio.optimize.frontier-chart-model-test --test=hyperopen.views.portfolio.optimize.frontier-chart-contract-test`: 40 tests, 288 assertions, 0 failures, 0 errors.
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs -g "portfolio optimizer recommendation chart" --workers=1`: 1 passed.
- `npm run browser:cleanup`: ok, stopped 0 sessions.
- `git diff --check`: passed.
- `npm run check`: passed.
- `npm test`: 3953 tests, 21735 assertions, 0 failures, 0 errors.
- `npm run test:websocket`: 524 tests, 3043 assertions, 0 failures, 0 errors.
- `npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app`: blocked before inspection because an existing shadow-cljs server was already running on `localhost:9630`.
- `npm run qa:design-ui -- --targets portfolio-optimizer-results-route --local-url http://localhost:8080/index.html`: blocked for the same shadow-cljs port collision.

## Context and Orientation

The optimizer pipeline calls `setup-readiness/build-readiness` before deciding whether to run immediately. The readiness request is built from the draft, current portfolio snapshot, and optimizer `:history-data`. The selected frontier must use `:history`, while the current marker should use `:current-portfolio-history` when holdings are outside the selected universe.

The failure happens when selected history is already complete, because the pipeline never calls the history loader. Since the current-history request lives inside that loader, the current marker has no data. Adding current assets to the selected universe once populates the general history cache with those assets, which then makes later runs appear to work by accident.

## Plan of Work

1. Add a request-builder regression proving outside-universe current holdings do not use incidental selected-history cache data when `:current-portfolio-history-data` is absent.
2. Add a pipeline workflow regression proving a ready selected universe still issues `:optimizer.workflow/load-history` when outside-universe current history is missing.
3. Add a runtime pipeline regression proving the pipeline performs `[:history :run]` in that state and the final worker request carries current-portfolio history after the history loader stores it.
4. Update `request_builder.cljs` so selected history is a current-history source only when current holding ids are covered by the selected requested universe.
5. Update `setup_readiness.cljs` and `pipeline_workflow.cljs` so the run pipeline loads history before running when outside-universe current history is missing.
6. Run focused tests, the relevant Playwright optimizer chart regression, UI/browser cleanup, and required repo gates.

## Validation and Acceptance

Acceptance is met when a clean selected-universe run with current HYPE/PUMP holdings first loads current-portfolio history and then renders/runs with a current marker without requiring the user to add HYPE/PUMP to the selected universe. The target frontier remains selected-universe scoped.

Required validation:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.application.request-builder-test --test=hyperopen.portfolio.optimizer.application.pipeline-workflow-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-pipeline-test --test=hyperopen.portfolio.optimizer.application.engine-current-portfolio-test --test=hyperopen.views.portfolio.optimize.frontier-chart-model-test --test=hyperopen.views.portfolio.optimize.frontier-chart-contract-test
    PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs -g "portfolio optimizer recommendation chart" --workers=1
    npm run browser:cleanup
    git diff --check
    npm run check
    npm test
    npm run test:websocket

## Idempotence and Recovery

The change is additive to run readiness. If a current-history fetch fails, the existing runtime adapter stores a warning bundle and the optimizer can still proceed without the current marker. The selected frontier inputs are not expanded by current holdings.

All listed commands are safe to rerun.

## Artifacts and Notes

None yet.

## Interfaces and Dependencies

`setup-readiness` should expose a small predicate that the pipeline can call with the already-built readiness map. `request-builder` should keep `:history` selected-only and only populate `:current-portfolio-history` from selected history when current ids are selected, or from `:current-portfolio-history-data` otherwise.
