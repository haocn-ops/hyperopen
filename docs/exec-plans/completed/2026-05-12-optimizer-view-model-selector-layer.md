# Add optimizer view-model selector layer

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current while the work proceeds. This document follows `.agents/PLANS.md` from the repository root.

## Purpose / Big Picture

The optimizer setup and scenario detail screens currently read nested application state directly in view namespaces. That makes UI work harder because a contributor changing markup must also understand optimizer storage paths, route mismatch rules, run-currentness rules, and history loading fallbacks. After this change, setup and scenario detail views can ask one pure selector namespace for the model they need and keep rendering focused on Hiccup structure.

The live `bd` issue for this work is `hyperopen-1dkg`.

## Progress

- [x] (2026-05-12 01:33Z) Inspected the optimizer views, existing optimizer application namespaces, test coverage, ExecPlan rules, and `bd` workflow.
- [x] (2026-05-12 01:34Z) Created and claimed `bd` issue `hyperopen-1dkg`.
- [x] (2026-05-12 01:35Z) Wrote this active ExecPlan.
- [x] (2026-05-12 01:37Z) Added RED tests for the new pure selector/view-model namespace; `npm test` failed because `hyperopen.portfolio.optimizer.application.view-model` did not exist.
- [x] (2026-05-12 01:43Z) Implemented the selector namespace and wired setup, universe, and scenario detail views through it.
- [x] (2026-05-12 01:47Z) Ran `npm test`; it passed with 3836 tests and 21202 assertions.
- [x] (2026-05-12 01:44Z) Fixed the optimizer contract-path guardrail by adding name path constants to `contracts.cljs`.
- [x] (2026-05-12 01:49Z) Ran required validation: `npm run check`, `npm test`, and `npm run test:websocket` all passed.
- [x] (2026-05-12 01:49Z) Moved this ExecPlan to completed and closed `hyperopen-1dkg`.

## Surprises & Discoveries

- Observation: The files named in the user request live under `src/hyperopen/views/portfolio/optimize/`, not directly under `src/hyperopen/views/`.
  Evidence: `scenario_detail_view.cljs`, `workspace_view.cljs`, and `setup_v4_universe.cljs` are all in `src/hyperopen/views/portfolio/optimize/`.
- Observation: The optimizer already has an application namespace family for pure state decisions.
  Evidence: `src/hyperopen/portfolio/optimizer/application/run_identity.cljs` centralizes current/stale run checks, and `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` centralizes request readiness and history status decisions.
- Observation: The first GREEN test run compiled the new namespace but could not execute because this worktree had no `node_modules`.
  Evidence: `npm test` stopped with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`, and `npm ls --depth=0` reported every JS dependency as missing. Running `npm ci` installed dependencies from `package-lock.json`.
- Observation: Universe candidate active index is intentionally clamped by the existing application helper.
  Evidence: `universe-candidates/active-index` clamps oversized indices to the last available candidate, and the existing `active-index-clamps-negative-oversized-and-invalid-values-test` documents that behavior.
- Observation: The optimizer contract-path guardrail also applies to the new application view-model namespace.
  Evidence: `npm run check` initially failed in `tools/optimizer/check-contract-paths.test.mjs` because `view_model.cljs` had literal `[:portfolio :optimizer ...]` paths for scenario names. Adding `draft-name-path` and `active-scenario-name-path` to `contracts.cljs` resolved the guardrail.

## Decision Log

- Decision: Add `hyperopen.portfolio.optimizer.application.view-model` instead of `hyperopen.views.portfolio.optimize.model`.
  Rationale: The selectors are pure application-state projection functions, and they depend on existing application helpers such as setup readiness, current portfolio snapshots, run identity, and universe candidates. Keeping them under `portfolio.optimizer.application` prevents view namespaces from becoming the storage contract.
  Date/Author: 2026-05-12 / Codex.
- Decision: Keep formatting and Hiccup rendering in existing view namespaces.
  Rationale: This change is a boundary refactor, not a redesign. The new namespace should return Clojure maps and primitive values that are easy to test, while the existing views keep CSS classes, data roles, event vectors, and formatting calls.
  Date/Author: 2026-05-12 / Codex.
- Decision: Preserve the existing universe active-index clamping contract in the model layer.
  Rationale: The view-model layer should project current application behavior, not redefine keyboard navigation. The failing test expectation was corrected to assert the existing clamped value.
  Date/Author: 2026-05-12 / Codex.

## Outcomes & Retrospective

Completed. The optimizer setup, scenario detail, and setup universe views now consume a pure selector layer in `src/hyperopen/portfolio/optimizer/application/view_model.cljs` for setup projection, scenario route scoping, active result tab selection, current/stale result checks, retained result paths, universe search candidates, and selected history labels. The implementation reduces overall UI complexity by moving nested optimizer path reads and route/currentness rules out of Hiccup rendering namespaces while keeping markup, formatting, and action vectors in existing views.

## Context and Orientation

The portfolio optimizer stores most state under `[:portfolio :optimizer]` and UI-only optimizer controls under `[:portfolio-ui :optimizer]`. Shared path constants live in `src/hyperopen/portfolio/optimizer/contracts.cljs`. Editable optimizer inputs are the draft at `[:portfolio :optimizer :draft]`; the latest successful solver output is at `[:portfolio :optimizer :last-successful-run]`; route-specific saved scenario metadata is at `[:portfolio :optimizer :active-scenario]`.

The setup route is rendered by `src/hyperopen/views/portfolio/optimize/workspace_view.cljs`. It currently assembles a workspace model inline: current portfolio snapshot, draft defaults, readiness, run state, optimization progress, save state, history load state, whether a result is current, and the route where retained weights should open.

The scenario detail route is rendered by `src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs`. It currently owns route mismatch handling. If a user navigates to a scenario id while old draft/run state is still retained, the view temporarily scopes state to loading defaults so old weights are not shown as if they belonged to the new route.

The setup universe panel is rendered by `src/hyperopen/views/portfolio/optimize/setup_v4_universe.cljs`. It currently owns selected-instrument history labels and universe search state. Those labels depend on readiness warnings, history load request signatures, prefetch status, and cached candle or vault history.

## Plan of Work

First, add a focused ClojureScript test namespace at `test/hyperopen/portfolio/optimizer/application/view_model_test.cljs`. The tests should call pure functions directly and prove the behavior currently buried in views: workspace models default missing state and compute current result links; scenario detail models normalize route-scoped state and selected tabs; universe models skip blank searches and compute selected history labels from loading, missing, insufficient, and sufficient history states. Run `npm test` and record the expected RED failure because the namespace does not exist yet.

Second, create `src/hyperopen/portfolio/optimizer/application/view_model.cljs`. It should require existing pure helpers: `current-portfolio`, `run-identity`, `setup-readiness`, `universe-candidates`, `optimizer-defaults`, `contracts`, `coercion`, `optimizer-query-state`, and `portfolio-routes`. Public functions should include `workspace-model`, `scenario-detail-model`, `scenario-scoped-state`, `scenario-name`, `selected-history-label`, and `universe-section-model`. Helper functions may stay private when views do not need them.

Third, wire `workspace_view.cljs` to call `view-model/workspace-model` and destructure the returned map. The view should still compute infeasible presentation details via `infeasible-panel`, because that is view-specific. It should stop duplicating draft defaults, readiness building, running state, scenario save state, history load state, current solved run checks, and retained result path logic.

Fourth, wire `scenario_detail_view.cljs` to call `view-model/scenario-detail-model`. Keep its Hiccup helpers, tab markup, KPI formatting, provenance strip, and copy link behavior in the view. Change helpers such as the header, provenance strip, recommendation tab, rebalance tab, and tab body to accept model values or the already scoped state rather than recomputing route state decisions.

Fifth, wire `setup_v4_universe.cljs` to call `view-model/universe-section-model` and `view-model/selected-history-label`. The rendering functions should keep row markup unchanged, but the history label and search/candidate data should come from the model layer.

Finally, run focused and required validation. Because this refactor touches UI-facing optimizer views but not browser tooling or visual behavior, deterministic ClojureScript tests are the smallest relevant validation. If a markup regression appears in focused tests, use the existing optimizer view tests to identify and fix it before broad gates.

## Concrete Steps

All commands run from `/Users/barry/.codex/worktrees/83aa/hyperopen`.

Write RED tests:

    npm test

Expected before implementation: compilation fails because `hyperopen.portfolio.optimizer.application.view-model` does not exist, or direct assertions fail because functions are not implemented.

Implement and run focused validation:

    npm test

Expected after implementation: the new `hyperopen.portfolio.optimizer.application.view-model-test` tests pass, and existing optimizer view tests still pass as part of the full test suite.

Required repository gates after code changes:

    npm run check
    npm test
    npm run test:websocket

Expected at completion: each command exits with code 0. If a command fails for an unrelated pre-existing reason, record the command, failure, and evidence here before handing off.

## Validation and Acceptance

Acceptance is met when `workspace_view.cljs`, `scenario_detail_view.cljs`, and `setup_v4_universe.cljs` no longer own the optimizer state projection decisions called out in the user request. They may still pass full `state` to child views that already expect it, but setup-level running/currentness, scenario route scoping, active result tab selection, retained result links, universe search data, and selected history labels must be produced by `hyperopen.portfolio.optimizer.application.view-model`.

The new selector tests must fail before the implementation and pass after it. Existing setup and scenario detail view tests must continue to pass, proving the rendered controls, links, status text, and action vectors are preserved. Required gates are `npm run check`, `npm test`, and `npm run test:websocket`.

Observed focused validation:

    npm test
    Generated test/test_runner_generated.cljs with 620 namespaces.
    Ran 3836 tests containing 21202 assertions.
    0 failures, 0 errors.

Observed required validation:

    npm run check
    Exited 0 after repo guardrails, optimizer contract path checks, namespace checks, release/styles/dev-server tests, and Shadow app/portfolio/worker/test builds completed with 0 warnings.

    npm test
    Ran 3836 tests containing 21202 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

## Idempotence and Recovery

The planned edits are additive first: create tests and one new application namespace, then update views to consume it. Re-running tests is safe. If a view wiring change causes a regression, revert only the small wiring hunk for that view and keep the selector tests as the guide. Do not alter optimizer storage shape or saved scenario records in this plan.

## Artifacts and Notes

Initial source hot spots:

    src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs
      owns active result tab selection, route mismatch detection, scenario-scoped fallback state, scenario naming, current-result, stale-result, and deep result reads.

    src/hyperopen/views/portfolio/optimize/workspace_view.cljs
      owns retained result path, default draft/run/save/history states, readiness, running state, and current solved run checks.

    src/hyperopen/views/portfolio/optimize/setup_v4_universe.cljs
      owns history row lookup, selected history labels, UI search query, candidate markets, active index, and candidate key projection.

Final implementation touched:

    src/hyperopen/portfolio/optimizer/application/view_model.cljs
    src/hyperopen/portfolio/optimizer/contracts.cljs
    src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs
    src/hyperopen/views/portfolio/optimize/setup_v4_universe.cljs
    src/hyperopen/views/portfolio/optimize/workspace_view.cljs
    test/hyperopen/portfolio/optimizer/application/view_model_test.cljs
    test/test_runner_generated.cljs

## Interfaces and Dependencies

`src/hyperopen/portfolio/optimizer/application/view_model.cljs` must expose these stable functions:

    workspace-model [state route] -> map
    scenario-detail-model [state route] -> map
    scenario-scoped-state [state scenario-id] -> state map
    scenario-name [state scenario-id] -> string
    selected-history-label [state readiness history-load-state history-status-by-id instrument] -> string
    universe-section-model [state draft opts] -> map

The model maps are plain ClojureScript data. They must not contain Hiccup nodes, event handlers, DOM references, or formatted strings that belong to a view. The namespace may depend on existing optimizer application helpers and route/query-state helpers, but view namespaces must not become dependencies of the application namespace.

## Plan Revision Notes

2026-05-12 / Codex: Updated progress, discoveries, validation evidence, final file list, and retrospective after implementing and validating the selector layer. The plan is ready to move from `active` to `completed`.
