# Finish optimizer index view-model boundary

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current while the work proceeds. This document follows `.agents/PLANS.md` from the repository root.

## Purpose / Big Picture

The portfolio optimizer index route shows the saved scenario board at `/portfolio/optimize`. Today `src/hyperopen/views/portfolio/optimize/index_view.cljs` reads the persisted scenario index directly from `[:portfolio :optimizer :scenario-index]` and derives ordered scenario summaries inside the view namespace. That keeps storage knowledge in Hiccup code and leaves one known gap in the optimizer view-model boundary.

After this change, the index view will ask `hyperopen.portfolio.optimizer.application.view-model` for an index route model. The view will keep only layout, CSS classes, links, buttons, and formatting. A contributor can see the change working by running tests that fail before the application model exists and pass after the view consumes it.

## Context References

Public refs:

- Direct user/maintainer request on 2026-05-13: "Finish the optimizer view-model boundary. index_view.cljs still reads [:portfolio :optimizer :scenario-index] and builds scenario summaries directly in the view. Move that into application.view-model.index or application.scenario-index, keeping the view Hiccup-only. Evidence: index_view.cljs (line 7)."

Repo artifacts:

- Root repo contract: `AGENTS.md`.
- Planning contract: `docs/PLANS.md` and `.agents/PLANS.md`.
- Optimizer boundary contract: `src/hyperopen/portfolio/optimizer/BOUNDARY.md`.
- Prior selector-layer plan: `docs/exec-plans/completed/2026-05-12-optimizer-view-model-selector-layer.md`.
- Prior workflow boundary plan: `docs/exec-plans/completed/2026-05-12-optimizer-workflow-view-model-boundary.md`.
- Prior facade split plan: `docs/exec-plans/completed/2026-05-13-optimizer-view-model-facade-split.md`.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-13 16:42Z) Read the root instructions, planning docs, optimizer boundary document, prior optimizer view-model plans, current `index_view.cljs`, current view-model facade, child scenario namespace, existing index view test, and package scripts.
- [x] (2026-05-13 16:43Z) Created this active ExecPlan from the direct maintainer request.
- [x] (2026-05-13 16:47Z) Added RED application view-model tests for optimizer index route scenario summaries and empty defaults.
- [x] (2026-05-13 16:50Z) Verified RED with `npm test`; after restoring dependencies with `npm ci`, the new tests failed because `view-model/index-model` was undeclared.
- [x] (2026-05-13 16:54Z) Implemented `application.view-model.index`, exposed `index-model` from the facade, and wired `index_view.cljs` through the model.
- [x] (2026-05-13 16:55Z) Verified GREEN with `npm test`; 3872 tests and 21364 assertions passed with 0 failures and 0 errors.
- [x] (2026-05-13 16:59Z) Ran required validation: `npm run check`, `npm test`, and `npm run test:websocket` all passed.
- [x] (2026-05-13 17:04Z) Ran focused Playwright browser regression for the optimizer index route; the first run was blocked by an existing server on port 8080, then the reuse-existing-server run passed.
- [x] (2026-05-13 17:05Z) Updated this ExecPlan with evidence, outcomes, and final file list after moving it to `docs/exec-plans/completed/`.
- [x] (2026-05-13 17:06Z) Re-ran docs validation after the completed-plan evidence update; `npm run lint:docs` and `npm run lint:docs:test` passed.

## Surprises & Discoveries

- Observation: The exact gap is documented by the previous workflow boundary plan, so this is completion of a known refactor rather than a new UI behavior.
  Evidence: `docs/exec-plans/completed/2026-05-12-optimizer-workflow-view-model-boundary.md` says the remaining nearby direct read is `index_view.cljs` scenario-index projection.
- Observation: The optimizer contract-path guardrail checks application and runtime optimizer namespaces, not view namespaces.
  Evidence: `tools/optimizer/check-contract-paths.mjs` scans `src/hyperopen/portfolio/optimizer` and `src/hyperopen/runtime/effect_adapters`, but not `src/hyperopen/views`. The requested direct view read is still architectural debt even though the current guardrail does not catch it.
- Observation: The existing `application.view-model.scenario` namespace owns scenario detail route projection, while index-list projection is route-facing and belongs behind the public `application.view-model` facade. A child namespace named `application.view-model.index` keeps the list model near other route-facing models without mixing it into scenario detail logic.
  Evidence: `src/hyperopen/portfolio/optimizer/application/view_model.cljs` is already a compatibility facade over child namespaces such as `view_model/scenario.cljs`, `view_model/workspace.cljs`, and `view_model/universe.cljs`.
- Observation: This worktree initially had no installed JS dependencies, so the first RED run compiled the new undeclared-var warnings but stopped at the test runner's Lucide import before executing the tests.
  Evidence: `npm test` failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`. Running `npm ci` installed 335 packages from `package-lock.json`; rerunning `npm test` then reached `hyperopen.portfolio.optimizer.application.view-model-index-test` and failed with two `TypeError` errors caused by undeclared `view-model/index-model`.

## Decision Log

- Decision: Implement `hyperopen.portfolio.optimizer.application.view-model.index` and expose `index-model` from the existing `hyperopen.portfolio.optimizer.application.view-model` facade.
  Rationale: The user allowed `application.view-model.index` or `application.scenario-index`. The existing architecture already uses `application.view-model.*` child namespaces for route-facing read models. An index child namespace preserves that pattern and keeps scenario detail code focused on a single scenario route.
  Date/Author: 2026-05-13 / Codex.
- Decision: Keep title casing and percentage formatting in `index_view.cljs`.
  Rationale: Formatting strings for labels and percentages is presentation logic. The requested boundary is about storage projection and scenario summary ordering, not visual text formatting or Hiccup.
  Date/Author: 2026-05-13 / Codex.

## Outcomes & Retrospective

Completed. The optimizer index route now consumes a pure application view model for scenario summaries. The view no longer reads `[:portfolio :optimizer :scenario-index]` or derives ordered summaries from `:ordered-ids` and `:by-id`; it only renders the data returned by `view-model/index-model`. This reduces overall UI complexity by closing the last direct scenario-index projection gap called out by the earlier workflow-boundary plan, while preserving the existing table markup, labels, links, and row action dispatch behavior.

## Context and Orientation

The portfolio optimizer stores persisted scenario-list metadata under the canonical path `contracts/scenario-index-path`, whose value is `[:portfolio :optimizer :scenario-index]`. A scenario index is a map with `:ordered-ids`, a vector of scenario ids in display order, and `:by-id`, a map from scenario id to summary maps. Summary maps contain values such as `:id`, `:name`, `:status`, `:objective-kind`, `:return-model-kind`, `:risk-model-kind`, `:expected-return`, and `:volatility`.

`src/hyperopen/views/portfolio/optimize/index_view.cljs` renders the optimizer index route. It should own Hiccup, CSS class vectors, route links, data roles, and button click handlers. It should not know the raw optimizer storage path or assemble scenario summaries from the persisted index.

`src/hyperopen/portfolio/optimizer/application/view_model.cljs` is the public route-facing view-model facade. It delegates to focused child namespaces under `src/hyperopen/portfolio/optimizer/application/view_model/`. This plan adds `src/hyperopen/portfolio/optimizer/application/view_model/index.cljs` for optimizer index route projection.

## Plan of Work

First, add a focused ClojureScript test namespace at `test/hyperopen/portfolio/optimizer/application/view_model_index_test.cljs`. The tests will call `view-model/index-model` with a state containing an out-of-order `:by-id` map and an explicit `:ordered-ids` vector, then assert that `:scenario-summaries` follows `:ordered-ids` and skips missing ids. A second assertion will call `index-model` with no scenario index and assert an empty vector. Running `npm test` before implementation should fail because `view-model/index-model` does not exist.

Second, implement `src/hyperopen/portfolio/optimizer/application/view_model/index.cljs`. It should require `hyperopen.portfolio.optimizer.application.scenario-state` for `default-scenario-index` and `hyperopen.portfolio.optimizer.contracts` for `scenario-index-path`. Define a private `scenario-index` helper that returns the state index or the default empty index. Define a public `index-model` function that returns `{:scenario-summaries [...]}` with summaries ordered by `:ordered-ids`, using `keep` to ignore stale ids missing from `:by-id`.

Third, update `src/hyperopen/portfolio/optimizer/application/view_model.cljs` to require the new index child namespace and delegate `index-model`.

Fourth, update `src/hyperopen/views/portfolio/optimize/index_view.cljs` to require the public view-model facade and use `(:scenario-summaries (view-model/index-model state))`. Remove the private `scenario-index` and `scenario-summaries` helpers from the view namespace. Leave row rendering, action dispatch, title labels, percent labels, and route paths unchanged.

Fifth, update `test/hyperopen/views/portfolio/optimize/index_view_test.cljs` only if necessary to keep it focused on rendered Hiccup. Existing assertions should still pass because the rendered output and action vectors are unchanged.

## Concrete Steps

All commands run from `/Users/barry/.codex/worktrees/ef7e/hyperopen`.

Add RED tests and run:

    npm test

Expected before implementation: the ClojureScript test compile fails because `hyperopen.portfolio.optimizer.application.view-model/index-model` is unavailable, or the new assertions fail because the model function is not implemented.

Observed RED validation:

    npm ci
    added 335 packages, and audited 336 packages in 4s

    npm test
    WARNING: Use of undeclared Var hyperopen.portfolio.optimizer.application.view-model/index-model
    Testing hyperopen.portfolio.optimizer.application.view-model-index-test
    ERROR in (index-model-projects-ordered-scenario-summaries-test)
    ERROR in (index-model-defaults-empty-scenario-summaries-test)
    Ran 3872 tests containing 21364 assertions.
    0 failures, 2 errors.

Implement the model and view wiring, then run:

    npm test

Expected after implementation: the new application view-model index test passes, and the existing optimizer index view test still passes with no changes to rendered rows, labels, links, or row action dispatch.

Observed GREEN validation:

    npm test
    Ran 3872 tests containing 21364 assertions.
    0 failures, 0 errors.

Run required repository gates after code changes:

    npm run check
    npm test
    npm run test:websocket

Expected at completion: each command exits with code 0. If any command fails for an unrelated pre-existing reason, record the command, exit status, and failure evidence in this plan before handing off.

Observed required validation:

    npm run check
    Exited 0 after docs checks, optimizer contract path checks, namespace checks, release/style/dev-server tests, and Shadow app/portfolio/worker/test builds completed with 0 warnings.

    npm test
    Ran 3872 tests containing 21364 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

Observed focused browser validation:

    npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "portfolio optimizer scenario board renders the local scenario surface" --workers=1
    Error: http://127.0.0.1:8080 is already used

    PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "portfolio optimizer scenario board renders the local scenario surface" --workers=1
    1 passed (6.0s)

The six design-system browser-QA passes from `docs/agent-guides/browser-qa.md` are accounted for as not materially changed by this refactor: there were no visual, native-control, styling, interaction, layout, or jank/perf changes to the Hiccup structure, CSS classes, controls, or action vectors. The deterministic Playwright index-route regression above exercised the changed route surface and click-through to the setup route. No Browser MCP or browser-inspection session was created, so there was no browser-inspection cleanup step to run.

Observed docs validation after the completed-plan update:

    npm run lint:docs
    Docs check passed.

    npm run lint:docs:test
    Ran 14 tests containing 17 assertions.
    0 failures, 0 errors.

## Validation and Acceptance

Acceptance is met when `index_view.cljs` no longer contains the literal optimizer scenario index path and no longer derives scenario summaries from `:ordered-ids` and `:by-id`. `hyperopen.portfolio.optimizer.application.view-model/index-model` must provide the scenario summaries as plain data, preserving display order and empty-default behavior. The view must remain Hiccup-only for this concern: it renders the model data and owns visual formatting, but not optimizer storage projection.

The new test must fail before implementation and pass after implementation. Existing `hyperopen.views.portfolio.optimize.index-view-test` must continue to pass. Required final validation remains `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

The planned edits are a pure refactor with no storage migration. Re-running tests is safe. If compile fails after adding the child namespace, check the ClojureScript file path to namespace mapping: `hyperopen.portfolio.optimizer.application.view-model.index` must live at `src/hyperopen/portfolio/optimizer/application/view_model/index.cljs`. If the index view test fails, restore the existing row rendering and action handlers while keeping the model extraction.

## Artifacts and Notes

Initial source hotspot:

    src/hyperopen/views/portfolio/optimize/index_view.cljs
      Owns `scenario-index` and `scenario-summaries` helpers and reads `[:portfolio :optimizer :scenario-index]` directly.

Expected final source layout:

    src/hyperopen/portfolio/optimizer/application/view_model/index.cljs
      Pure optimizer index route read model.

    src/hyperopen/portfolio/optimizer/application/view_model.cljs
      Compatibility facade that exposes `index-model`.

    src/hyperopen/views/portfolio/optimize/index_view.cljs
      Hiccup rendering that consumes `view-model/index-model`.

Final implementation touched:

    docs/exec-plans/completed/2026-05-13-optimizer-index-view-model-boundary.md
    src/hyperopen/portfolio/optimizer/application/view_model.cljs
    src/hyperopen/portfolio/optimizer/application/view_model/index.cljs
    src/hyperopen/views/portfolio/optimize/index_view.cljs
    test/hyperopen/portfolio/optimizer/application/view_model_index_test.cljs
    test/test_runner_generated.cljs

## Interfaces and Dependencies

`hyperopen.portfolio.optimizer.application.view-model.index/index-model` must have this signature:

    index-model [state] -> {:scenario-summaries vector}

`hyperopen.portfolio.optimizer.application.view-model/index-model` must delegate to the child namespace with the same signature. The model map must contain plain data only. It must not contain Hiccup nodes, event handlers, DOM references, route actions, formatted percentages, or CSS class names.

## Plan Revision Notes

2026-05-13 / Codex: Created the active plan before code edits. The plan resolves the namespace placement decision and records that visual formatting remains in the view while storage projection moves to the application view-model layer.

2026-05-13 / Codex: Updated progress, RED/GREEN evidence, required validation, final file list, and retrospective after implementing and validating the boundary refactor. The plan is ready to move from `active` to `completed`.

2026-05-13 / Codex: Added focused Playwright index-route regression evidence and explicit browser-QA accounting after moving the plan to `completed`.

2026-05-13 / Codex: Added post-update docs lint evidence after modifying this completed plan with browser validation results.
