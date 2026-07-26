# Finish Optimizer Setup View-Model Boundary

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current while the work proceeds.

This document follows `/hyperopen/docs/PLANS.md` and the detailed ExecPlan contract in `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Optimizer setup views should render already-projected data instead of owning optimizer application semantics. `src/hyperopen/portfolio/optimizer/BOUNDARY.md` allows views under `hyperopen.views.portfolio.optimize.*` to consume optimizer view models, formatters, and action maps, but readiness/history math and copy decisions should stay in the application boundary.

After this change, the setup universe, readiness, summary, and Use My Views card paths consume pure maps from `hyperopen.portfolio.optimizer.application.view-model.*`. Views continue to own Hiccup, CSS classes, action vectors, and local visual structure. Observable behavior should remain stable: existing view tests keep passing, and new application view-model tests prove row labels, readiness copy, history labels, setup summary rows, and Black-Litterman preview card data are projected outside the views.

## Context References

Public refs:

- Direct user request on 2026-05-13: "Finish the optimizer view-model boundary" by moving row labels, readiness copy, history status labels, and Black-Litterman preview card data into `application.view-model.*`.

Repo artifacts:

- Boundary rule: `src/hyperopen/portfolio/optimizer/BOUNDARY.md`
- Existing facade: `src/hyperopen/portfolio/optimizer/application/view_model.cljs`
- Existing focused namespace: `src/hyperopen/portfolio/optimizer/application/view_model/universe.cljs`
- Readiness semantics: `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs`
- Black-Litterman preview inputs: `src/hyperopen/portfolio/optimizer/application/black_litterman_preview.cljs`
- Target views: `src/hyperopen/views/portfolio/optimize/setup_v4_universe.cljs`, `src/hyperopen/views/portfolio/optimize/setup_readiness_panel.cljs`, `src/hyperopen/views/portfolio/optimize/setup_v4_sections.cljs`, `src/hyperopen/views/portfolio/optimize/setup_v4_use_my_views_cards.cljs`, and `src/hyperopen/views/portfolio/optimize/black_litterman_preview_chart.cljs`

Local scratch refs:

- None.

## Progress

- [x] (2026-05-13T21:10Z) Read `BOUNDARY.md`, current optimizer setup views, existing application view-model namespaces, and related tests.
- [x] (2026-05-13T21:12Z) Created this active ExecPlan.
- [x] (2026-05-13T21:51Z) Added RED tests for application-owned row labels, readiness panel copy, setup summary rows, and Black-Litterman card models.
- [x] (2026-05-13T21:51Z) Ran the focused RED command; compilation warned that `readiness-panel-model`, `setup-summary-model`, and `black-litterman-cards-model` are undeclared, then the node runner stopped on a missing local `lucide` package.
- [x] (2026-05-13T21:52Z) Ran `npm ci` to restore this worktree's JS dependencies before GREEN validation.
- [x] (2026-05-13T22:01Z) Implemented pure view-model helpers and facade functions under `src/hyperopen/portfolio/optimizer/application/view_model*.cljs`.
- [x] (2026-05-13T22:05Z) Rewired setup views to consume projected maps while preserving Hiccup structure and action vectors.
- [x] (2026-05-13T22:08Z) Split the new boundary tests into `view_model_setup_boundary_test.cljs` after namespace-size guardrails caught the expanded existing test namespace.
- [x] (2026-05-13T22:09Z) Ran focused application and setup view tests; they passed with 20 tests and 174 assertions.
- [x] (2026-05-13T22:15Z) Ran required repository gates: `npm run check`, `npm test`, and `npm run test:websocket`; all passed.
- [x] (2026-05-13T22:23Z) Ran optimizer Black-Litterman Playwright regression after rebuilding CSS for the static server; all 5 tests passed.
- [x] (2026-05-13T22:29Z) Ran governed browser design review for `portfolio-optimizer-route`; all six required passes returned PASS across 375, 768, 1280, and 1440 widths.
- [x] (2026-05-13T22:31Z) Ran `npm run browser:cleanup` and verified no leftover static server, browser-inspection, Shadow watch, or relevant listener processes remained.
- [x] (2026-05-13T22:35Z) Moved this plan to `docs/exec-plans/completed/` after acceptance criteria passed.

## Surprises & Discoveries

- Observation: The setup universe view already calls `universe-section-model`, but still computes selected/candidate row labels, ADV labels, liquidity labels, and market display names locally.
  Evidence: `setup_v4_universe.cljs` requires both `application.view-model` and `application.universe-candidates`, then defines `adv-label`, `liquidity-label`, and calls `universe-candidates/market-display` from row renderers.
- Observation: Readiness warning messages are partly application-owned already, but the visible panel status copy and warning fallback labels are still view-local.
  Evidence: `setup_readiness_panel.cljs` defines `readiness-copy`, `history-load-copy`, `warning-code-label`, and `warning-message`.
- Observation: Use My Views insight cards currently build their semantic card rows in a view namespace.
  Evidence: `setup_v4_use_my_views_cards.cljs` directly requires `application.black-litterman-preview` and `application.instrument-labels`, then computes active views, labels, confidence text, row selection, and output callouts.
- Observation: This worktree did not have a complete `node_modules` install when the RED command first ran.
  Evidence: the focused compile produced the intended undeclared-var warnings, then `node out/test.js` failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`; `npm ci` restored dependencies.
- Observation: Adding all new boundary assertions to `view_model_test.cljs` exceeded the repo namespace-size policy.
  Evidence: `bb -m dev.check-namespace-sizes` reported `test/hyperopen/portfolio/optimizer/application/view_model_test.cljs - namespace has 647 lines`; moving the new assertions to `view_model_setup_boundary_test.cljs` made the namespace-size check pass.
- Observation: The optimizer Playwright static-server flow requires built CSS in `resources/public/css/main.css`.
  Evidence: the first `optimizer-black-litterman-views.spec.mjs` run failed two layout assertions while the app was served unstyled because the CSS file was absent; `npm run css:build` generated the ignored CSS artifact, and the focused rerun plus full spec both passed.
- Observation: The managed browser design-review command cannot start while a separate Shadow CLJS server already owns `localhost:9632`.
  Evidence: the first `npm run qa:design-ui -- --targets portfolio-optimizer-route --manage-local-app` run failed with `shadow-cljs - server version: 3.2.0 running at http://localhost:9632`; `npx shadow-cljs stop` cleared the server and the design-review rerun passed.

## Decision Log

- Decision: Extend the existing `application.view-model` facade and add focused sub-namespaces instead of putting more logic in view namespaces.
  Rationale: The repo already established `hyperopen.portfolio.optimizer.application.view-model` as the stable optimizer projection boundary. Focused sub-namespaces keep namespace-size guardrails satisfied while preserving the facade for view consumers.
  Date/Author: 2026-05-13 / Codex.
- Decision: Keep visual formatters in `views.portfolio.optimize.format` for now, but pass formatter functions into card-model projection where the copy needs already-formatted percentages.
  Rationale: The boundary allows views to consume formatters. Passing formatters keeps application semantics pure and avoids making application code depend on view namespaces.
  Date/Author: 2026-05-13 / Codex.
- Decision: Preserve all existing data-role strings and event action vectors.
  Rationale: This is a boundary refactor, not a UI behavior change. Existing view and browser tests depend on stable roles and actions.
  Date/Author: 2026-05-13 / Codex.

## Outcomes & Retrospective

Completed. The optimizer setup boundary now projects row labels, readiness/history copy, setup summary rows, and Black-Litterman preview card data from application view-model namespaces before the view layer renders them.

The universe view now renders `:selected-rows` and `:candidate-rows` from `universe-section-model`. The readiness panel and setup summary section render projected maps from `readiness-panel-model` and `setup-summary-model`. The Use My Views cards render `black-litterman-cards-model`, while the preview chart enters through the optimizer view-model facade. The touched views still own Hiccup, classes, data roles, action vectors, and visual layout.

Validation completed with focused RED/GREEN tests, namespace-size checks, required repository gates, the optimizer Black-Litterman Playwright regression, governed browser design review, and browser cleanup. The only residual browser-QA caveat is the design-review tool's standard state-sampling note: hover, active, disabled, and loading states still require targeted route actions when they are not present by default.

## Context and Orientation

The optimizer setup V4 flow renders a left control rail and a center summary pane. `setup_v4_sections.cljs` orchestrates those areas. The universe section renders selected instruments and candidate search rows. Readiness state is built by `application.setup-readiness`, while per-instrument history labels are already partly projected by `application.view-model.universe`.

Use My Views is the Black-Litterman setup mode. `application.black-litterman-preview/build-preview` returns raw prior/posterior return rows. The view currently turns those rows plus draft views into three explanatory cards. This plan moves that card data projection into application view-model code so the view renders card maps.

## Plan of Work

First, extend `test/hyperopen/portfolio/optimizer/application/view_model_test.cljs` with RED tests. These tests should assert:

- `universe-section-model` returns selected row maps with `primary-label`, `secondary-label`, `history-label`, `liquidity-label`, and candidate row maps with `label`, `name`, `adv-label`, and `market-type`.
- `readiness-panel-model` returns visible readiness copy, warning rows with fallback messages and codes, and history-load error text.
- `setup-summary-model` returns the active preset, Black-Litterman mode flag, and non-Black-Litterman summary row maps.
- `black-litterman-cards-model` returns the three Use My Views card models with titles, empty states, formatted values, confidence labels, and output callouts.

Second, run the focused ClojureScript test command to verify RED. The expected failure is missing function/key assertions in `hyperopen.portfolio.optimizer.application.view-model-test`.

Third, implement focused application view-model code:

- Extend `application.view-model.universe` with row-label helpers and row model projection.
- Add a setup/readiness view-model namespace for panel copy and summary rows.
- Add a Black-Litterman cards view-model namespace for card data, active view labels, confidence labels, output row selection, and callout copy.
- Expose facade functions from `application.view-model`.

Fourth, rewire target views:

- `setup_v4_universe.cljs` should render selected and candidate row maps from `universe-section-model`.
- `setup_readiness_panel.cljs` should render `readiness-panel-model`.
- `setup_v4_sections.cljs` should use `setup-summary-model` for preset and summary row data, and pass a projected Use My Views model into the workspace.
- `setup_v4_use_my_views_cards.cljs` should render card maps and no longer require application preview or instrument label namespaces directly.
- `black_litterman_preview_chart.cljs` should receive a preview model from the caller or facade and avoid building preview directly where the caller already projected it.

Fifth, run validation. Because this is UI-facing but behavior-preserving boundary work, the smallest relevant browser-adjacent coverage is the existing deterministic Hiccup/view tests and ClojureScript test suite. Required gates are still `npm run check`, `npm test`, and `npm run test:websocket`.

## Concrete Steps

From `/Users/barry/.codex/worktrees/3ae6/hyperopen`, add RED tests and run:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.view-model-test

Expected before implementation: the focused test command exits non-zero because the new view-model functions or projected keys are not implemented yet.

Observed RED validation:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.view-model-test
    Generated test/test_runner_generated.cljs with 635 namespaces.
    Warnings: Use of undeclared Var hyperopen.portfolio.optimizer.application.view-model/readiness-panel-model.
    Warnings: Use of undeclared Var hyperopen.portfolio.optimizer.application.view-model/setup-summary-model.
    Warnings: Use of undeclared Var hyperopen.portfolio.optimizer.application.view-model/black-litterman-cards-model.
    Node then stopped at missing local dependency `lucide/dist/esm/icons/external-link.js`; `npm ci` completed successfully afterward.

After implementation, run the focused command again plus existing view coverage:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.view-model-test --test=hyperopen.portfolio.optimizer.application.view-model-setup-boundary-test --test=hyperopen.views.portfolio.optimize.setup-v4-use-my-views-workspace-test --test=hyperopen.views.portfolio.optimize.black-litterman-preview-chart-test

Expected after implementation: all listed tests pass with zero failures and zero errors.

Observed focused GREEN validation:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.view-model-test --test=hyperopen.portfolio.optimizer.application.view-model-setup-boundary-test --test=hyperopen.views.portfolio.optimize.setup-v4-use-my-views-workspace-test --test=hyperopen.views.portfolio.optimize.black-litterman-preview-chart-test
    Ran 20 tests containing 174 assertions.
    0 failures, 0 errors.

Observed namespace-size validation:

    bb -m dev.check-namespace-sizes
    Namespace size check passed.

Required repository gates after code changes:

    npm run check
    npm test
    npm run test:websocket

Observed required repository gates:

    npm run check
    Passed. The command completed docs, namespace, boundary, tooling/style tests, and Shadow builds for app, portfolio, portfolio-worker, portfolio-optimizer-worker, vault-detail-worker, and test with zero warnings.

    npm test
    Passed. Ran 3882 tests containing 21411 assertions with 0 failures and 0 errors.

    npm run test:websocket
    Passed. Ran 524 tests containing 3043 assertions with 0 failures and 0 errors.

Observed deterministic browser regression:

    PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 PLAYWRIGHT_WEB_PORT=4174 PLAYWRIGHT_WEB_SERVER_COMMAND='node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs

    Initial run failed two layout assertions because the static server had no built CSS artifact. After `npm run css:build`, the focused failed-test rerun passed 2/2 and the full command passed 5/5.

Observed governed browser QA:

    npm run qa:design-ui -- --targets portfolio-optimizer-route --manage-local-app

    Initial run was blocked by an existing Shadow CLJS server on `localhost:9632`; after `npx shadow-cljs stop`, the rerun passed. Run id: `design-review-2026-05-13T22-12-09-963Z-d690ecf0`. Artifacts: `tmp/browser-inspection/design-review-2026-05-13T22-12-09-963Z-d690ecf0`. Review outcome: PASS. Inspected widths: 375, 768, 1280, and 1440. Required passes: baseline-visual, interaction-states, responsiveness, accessibility-smoke, console-network, and standards-alignment all PASS with 0 issues.

Observed cleanup:

    npm run browser:cleanup

    Stopped browser session `sess-1778710230139-a2444c`. Follow-up `lsof` and `ps` checks found no relevant static server, browser-inspection, Shadow watch, Tailwind watch, or listener processes on the tested ports.

## Validation and Acceptance

Acceptance is met when the target views no longer import optimizer application logic directly for row labels, readiness copy, history labels, setup summary semantics, or Black-Litterman card data. The view-model layer must project those concepts into plain maps. Views may still use local Hiccup helpers, CSS class vectors, static data roles, action vectors, and formatter functions.

New application view-model tests must fail before implementation and pass after implementation. Existing optimizer setup Hiccup tests must continue to pass, proving no rendered behavior changed. Required gates remain `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

The changes are pure source refactors plus tests. Re-running tests is safe. If a view rendering regression appears, keep the application model tests and narrow the view rewire until existing data-role and text assertions pass. Do not change optimizer contracts, persisted scenario shapes, history payloads, or action names in this plan.

## Artifacts and Notes

Important files changed:

    docs/exec-plans/completed/2026-05-13-optimizer-setup-view-model-boundary.md
    src/hyperopen/portfolio/optimizer/application/view_model.cljs
    src/hyperopen/portfolio/optimizer/application/view_model/black_litterman.cljs
    src/hyperopen/portfolio/optimizer/application/view_model/setup.cljs
    src/hyperopen/portfolio/optimizer/application/view_model/universe.cljs
    src/hyperopen/views/portfolio/optimize/black_litterman_preview_chart.cljs
    src/hyperopen/views/portfolio/optimize/setup_v4_universe.cljs
    src/hyperopen/views/portfolio/optimize/setup_readiness_panel.cljs
    src/hyperopen/views/portfolio/optimize/setup_v4_sections.cljs
    src/hyperopen/views/portfolio/optimize/setup_v4_use_my_views_cards.cljs
    test/hyperopen/portfolio/optimizer/application/view_model_test.cljs
    test/hyperopen/portfolio/optimizer/application/view_model_setup_boundary_test.cljs
    test/test_runner_generated.cljs

Generated ignored artifacts:

    resources/public/css/main.css
    tmp/browser-inspection/design-review-2026-05-13T22-12-09-963Z-d690ecf0/

## Interfaces and Dependencies

Expected facade additions:

    readiness-panel-model [readiness history-load-state] -> map
    setup-summary-model [draft] -> map
    black-litterman-cards-model [draft readiness preview formatters] -> map

Expected extended existing projection:

    universe-section-model [state draft opts] -> map with `:selected-rows` and `:candidate-rows`

Returned maps must not include Hiccup, CSS classes, DOM/event maps, or browser APIs. They may include display copy, labels, row roles, instrument ids, market types, tone keywords, and values already prepared for rendering.

## Plan Revision Notes

2026-05-13 / Codex: Initial active ExecPlan created from the direct request after reading the current boundary doc, setup views, application model namespace, and tests.
2026-05-13 / Codex: Completed implementation and validation, then moved the plan to completed.
