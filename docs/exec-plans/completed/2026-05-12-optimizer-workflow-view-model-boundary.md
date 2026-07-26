# Finish optimizer workflow view-model boundary

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current while the work proceeds. This document follows `.agents/PLANS.md` from the repository root.

## Purpose / Big Picture

The portfolio optimizer workflow is easier to maintain when views render already-projected data instead of reading optimizer storage paths directly. Recent refactors added `src/hyperopen/portfolio/optimizer/application/view_model.cljs` for setup and scenario detail projection, but tracking, execution, inputs audit, and the legacy universe panel still reach into global state. After this change, the named views can stay mostly Hiccup-only: they will receive maps from the application view-model layer and keep markup, CSS classes, action vectors, and formatting in the view namespace.

This is internal boundary work. A human can see it working by running the ClojureScript tests: new tests in `test/hyperopen/portfolio/optimizer/application/view_model_test.cljs` fail before the model functions exist and pass after the views are rewired without changing rendered behavior.

## Context References

Public refs:

- Direct user/maintainer request on 2026-05-12: finish the `/portfolio/optimize` selector/view-model boundary by adding pure `tracking-model`, `execution-modal-model`, and `inputs-audit-model` functions under `application.view-model`, then keeping views mostly Hiccup-only.

Repo artifacts:

- Prior completed ExecPlan: `docs/exec-plans/completed/2026-05-12-optimizer-view-model-selector-layer.md`.
- Current selector namespace: `src/hyperopen/portfolio/optimizer/application/view_model.cljs`.
- Optimizer storage path constants: `src/hyperopen/portfolio/optimizer/contracts.cljs`.
- Target views: `src/hyperopen/views/portfolio/optimize/universe_panel.cljs`, `src/hyperopen/views/portfolio/optimize/tracking_panel.cljs`, `src/hyperopen/views/portfolio/optimize/execution_modal.cljs`, and `src/hyperopen/views/portfolio/optimize/inputs_tab.cljs`.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-12 13:40Z) Read the repo planning contract, previous optimizer selector-layer plan, current view-model namespace, target views, and existing optimizer view tests.
- [x] (2026-05-12 13:40Z) Created this active ExecPlan.
- [x] (2026-05-12 13:43Z) Added RED tests for tracking, execution modal, inputs audit, and legacy universe panel model projection.
- [x] (2026-05-12 13:44Z) Verified RED with `npm test`; compilation produced undeclared-var warnings for `tracking-model`, `execution-modal-model`, `inputs-audit-model`, and `universe-panel-model`.
- [x] (2026-05-12 13:48Z) Implemented pure model functions in `src/hyperopen/portfolio/optimizer/application/view_model.cljs`.
- [x] (2026-05-12 13:50Z) Rewired target views to consume model maps instead of reading optimizer storage directly.
- [x] (2026-05-12 13:54Z) Ran `npm test`; fixed one brittle `with-redefs` test by replacing it with a real-data asset-query ranking assertion.
- [x] (2026-05-12 13:56Z) Re-ran `npm test`; it passed with 3842 tests and 21246 assertions.
- [x] (2026-05-12 13:59Z) Ran required `npm run check`; it passed including optimizer contract-path, namespace, lint, and Shadow compile gates.
- [x] (2026-05-12 14:00Z) Ran required `npm run test:websocket`; it passed with 524 tests and 3043 assertions.
- [x] (2026-05-12 14:00Z) Updated this ExecPlan with final evidence and retrospective; it is ready to move to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The requested `view_model.cljs` already exists from the recent selector-layer work.
  Evidence: `src/hyperopen/portfolio/optimizer/application/view_model.cljs` exposes `workspace-model`, `scenario-detail-model`, `universe-section-model`, and `selected-history-label`.
- Observation: `setup_v4_universe.cljs` already consumes `universe-section-model`, but `universe_panel.cljs` still reads the UI search query and candidate markets directly.
  Evidence: `setup_v4_universe.cljs` requires `application.view-model`; `universe_panel.cljs` still requires `application.universe-candidates` and calls `candidate-markets`.
- Observation: The first RED run also failed at runtime because this worktree did not have installed JS dependencies.
  Evidence: `npm test` compiled the new test namespace and showed the expected undeclared-var warnings, then Node stopped at `Cannot find module 'lucide/dist/esm/icons/external-link.js'`. Running `npm ci` restored dependencies from `package-lock.json`.
- Observation: A direct `with-redefs` stub around `universe-candidates/candidate-markets` was brittle for the four-arity CLJS call shape.
  Evidence: The first GREEN attempt errored with `candidate_markets.cljs$core$IFn$_invoke$arity$4 is not a function`. Replacing the stub with real market data proved the intended ranking behavior without redefining the multi-arity function.
- Observation: The target views no longer contain the direct state reads called out by the maintainer request.
  Evidence: `rg -n "get-in state \\[:portfolio|\\[:portfolio :optimizer|\\[:portfolio-ui :optimizer" src/hyperopen/views/portfolio/optimize/{universe_panel.cljs,tracking_panel.cljs,execution_modal.cljs,inputs_tab.cljs}` returned no matches.

## Decision Log

- Decision: Extend the existing `hyperopen.portfolio.optimizer.application.view-model` namespace instead of creating per-view model namespaces.
  Rationale: The prior selector-layer plan intentionally made this namespace the application projection boundary for optimizer views. Adding the remaining workflow projections there keeps one stable boundary and avoids scattering storage path knowledge.
  Date/Author: 2026-05-12 / Codex.
- Decision: Keep formatting and rendered labels in the view layer unless they are needed to hide optimizer storage or identify a row.
  Rationale: This refactor is about state boundary clarity, not visual redesign. Views should keep Hiccup, CSS, user-facing copy, and formatting helpers, while the application model returns raw data, selected rows, current snapshots, labels maps, and booleans.
  Date/Author: 2026-05-12 / Codex.
- Decision: Add `universe-panel-model` even though the maintainer specifically named three new functions.
  Rationale: The request also calls out `universe_panel.cljs` as a remaining direct-storage reader. A small model wrapper around the existing `universe-section-model` closes that gap without changing the newer setup universe section.
  Date/Author: 2026-05-12 / Codex.

## Outcomes & Retrospective

Work is in progress. At completion, summarize whether the boundary became simpler, which views no longer know optimizer storage paths, and any remaining direct reads intentionally left outside this scope.

Completed. The optimizer workflow boundary is simpler: `tracking_panel.cljs`, `execution_modal.cljs`, `inputs_tab.cljs`, and `universe_panel.cljs` now call the application view-model layer for storage projection and keep their responsibilities focused on Hiccup, styling, action vectors, and formatting. This reduced duplicated vault-label logic in tracking and execution, moved inputs audit labels and scenario id projection into pure data, and made the legacy universe panel share the same selector layer as the newer setup universe section. The only remaining optimizer direct reads in nearby view code are outside this scoped request, such as `index_view.cljs` scenario-index projection.

## Context and Orientation

The portfolio optimizer stores application state under `[:portfolio :optimizer]` and UI-only optimizer state under `[:portfolio-ui :optimizer]`. Shared path constants live in `src/hyperopen/portfolio/optimizer/contracts.cljs`; application code should prefer those constants over literal storage paths. The view-model namespace is pure ClojureScript: it takes `state`, `route`, or `draft` maps and returns plain maps. It must not return Hiccup, DOM nodes, browser APIs, or event handlers.

The target views currently split responsibilities unevenly. `tracking_panel.cljs` decides whether manual tracking can be enabled, finds the tracking record, finds the latest snapshot, and resolves vault labels from the last successful run. `execution_modal.cljs` reads the modal state, latest execution history, labels, disabled state, and confirmation status. `inputs_tab.cljs` loads the draft, scenario id, constraints, execution assumptions, and Black-Litterman views directly. `universe_panel.cljs` reads the universe search query, candidate markets, active keyboard index, and candidate keys directly.

## Plan of Work

First, extend `test/hyperopen/portfolio/optimizer/application/view_model_test.cljs` with pure model tests. `tracking-model` should return the active scenario status, tracking record, latest snapshot, label map, and a boolean indicating whether manual tracking can be enabled. `execution-modal-model` should return the modal, plan, summary, latest attempt, label map, submitting state, ready state, confirm-disabled state, and disabled message. `inputs-audit-model` should return the defaulted draft, scenario id, constraints, execution assumptions, views, and instrument labels needed by the audit view. `universe-panel-model` should project selected universe rows, search query, candidate markets, active index, and candidate keys using asset-query ranking.

Second, run `npm test` to verify RED. The expected failure is assertion failure or missing function errors in `hyperopen.portfolio.optimizer.application.view-model-test` because the new model functions are not implemented yet.

Third, implement the pure model functions in `src/hyperopen/portfolio/optimizer/application/view_model.cljs`. Reuse existing helpers where possible. Add private helpers for `labels-by-instrument`, `latest-snapshot`, `active-scenario-id`, and model-specific row labeling only if they reduce duplication. Use path constants from `contracts.cljs` for optimizer storage reads.

Fourth, rewire `tracking_panel.cljs`, `execution_modal.cljs`, `inputs_tab.cljs`, and `universe_panel.cljs`. Each view should require `hyperopen.portfolio.optimizer.application.view-model`, call the relevant model function once near the top-level render function, and pass projected values into existing Hiccup helpers. Remove direct `get-in state [:portfolio ...]` and `get-in state [:portfolio-ui ...]` reads from those views where the new model covers them.

Fifth, run focused and required validation. Because this is UI-facing optimizer refactor without intended visual changes, deterministic ClojureScript tests are the smallest relevant verification. Existing view tests must continue to pass, proving the rendered strings, buttons, and action vectors remain stable.

## Concrete Steps

All commands run from `/Users/barry/.codex/worktrees/d329/hyperopen`.

Add RED tests and run:

    npm test

Expected before implementation: `hyperopen.portfolio.optimizer.application.view-model-test` fails because `tracking-model`, `execution-modal-model`, `inputs-audit-model`, or `universe-panel-model` is missing or returns incomplete data.

Implement source and view wiring, then run:

    npm test

Expected after implementation: the new view-model tests and existing optimizer view tests pass with 0 failures and 0 errors.

Required repository gates after code changes:

    npm run check
    npm test
    npm run test:websocket

Expected at completion: each command exits with code 0. If a command fails for an unrelated pre-existing reason, record the command, failure, and evidence here before handing off.

Observed RED validation:

    npm test
    Generated test/test_runner_generated.cljs with 625 namespaces.
    Warnings: Use of undeclared Var hyperopen.portfolio.optimizer.application.view-model/tracking-model.
    Warnings: Use of undeclared Var hyperopen.portfolio.optimizer.application.view-model/execution-modal-model.
    Warnings: Use of undeclared Var hyperopen.portfolio.optimizer.application.view-model/inputs-audit-model.
    Warnings: Use of undeclared Var hyperopen.portfolio.optimizer.application.view-model/universe-panel-model.

Observed GREEN validation:

    npm test
    Ran 3842 tests containing 21246 assertions.
    0 failures, 0 errors.

Observed required validation:

    npm run check
    Exited 0 after repo guardrails, optimizer contract path checks, namespace checks, release/styles/dev-server tests, and Shadow app/portfolio/worker/test builds completed with 0 warnings.

    npm test
    Ran 3842 tests containing 21246 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

## Validation and Acceptance

Acceptance is met when `tracking_panel.cljs`, `execution_modal.cljs`, `inputs_tab.cljs`, and `universe_panel.cljs` no longer own the optimizer state projection decisions called out by the maintainer. The views may still receive `state` where existing child functions require it, but the highlighted direct reads for tracking status/record, execution modal/history, inputs draft/scenario id, and universe search/candidate projection must come from `hyperopen.portfolio.optimizer.application.view-model`.

The new model tests must fail before implementation and pass after implementation. Existing view tests for tracking, execution modal, inputs tab, and universe panel must continue to pass, proving no user-visible behavior changed. Required gates are `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

The planned edits are additive before rewiring. Re-running tests is safe. If a view wiring change causes a rendered regression, keep the model tests and revert only that view hunk before retrying the wiring with a smaller model shape. Do not alter optimizer persisted storage shape, scenario records, tracking records, or execution records in this plan.

## Artifacts and Notes

Important source hotspots:

    src/hyperopen/portfolio/optimizer/application/view_model.cljs
      Existing pure optimizer projection namespace; add the remaining workflow models here.

    src/hyperopen/views/portfolio/optimize/tracking_panel.cljs
      Should call `tracking-model` and render from projected tracking data.

    src/hyperopen/views/portfolio/optimize/execution_modal.cljs
      Should call `execution-modal-model` and render from projected modal data.

    src/hyperopen/views/portfolio/optimize/inputs_tab.cljs
      Should call `inputs-audit-model` and render audit cards from projected draft data.

    src/hyperopen/views/portfolio/optimize/universe_panel.cljs
      Should call `universe-panel-model` and render selected/candidate rows from projected search data.

Final implementation touched:

    docs/exec-plans/completed/2026-05-12-optimizer-workflow-view-model-boundary.md
    src/hyperopen/portfolio/optimizer/application/view_model.cljs
    src/hyperopen/views/portfolio/optimize/execution_modal.cljs
    src/hyperopen/views/portfolio/optimize/inputs_tab.cljs
    src/hyperopen/views/portfolio/optimize/tracking_panel.cljs
    src/hyperopen/views/portfolio/optimize/universe_panel.cljs
    test/hyperopen/portfolio/optimizer/application/view_model_test.cljs

## Interfaces and Dependencies

`src/hyperopen/portfolio/optimizer/application/view_model.cljs` must expose these additional stable functions:

    tracking-model [state] -> map
    execution-modal-model [state] -> map
    inputs-audit-model [state] -> map
    universe-panel-model [state draft] -> map

The returned maps are plain data. They must not include Hiccup, CSS class vectors, DOM event maps, or formatted presentation strings that belong in the view layer. The namespace may depend on existing optimizer application helpers, optimizer contracts, defaults, coercion, ids, and universe candidate helpers.

## Plan Revision Notes

2026-05-12 / Codex: Created the active plan from the maintainer request after inspecting the current selector-layer implementation and target views. The plan extends the existing application view-model boundary and keeps visual behavior unchanged.

2026-05-12 / Codex: Updated progress, discoveries, validation evidence, final file list, and retrospective after implementation and required gates passed. This plan is ready to move from `active` to `completed`.
