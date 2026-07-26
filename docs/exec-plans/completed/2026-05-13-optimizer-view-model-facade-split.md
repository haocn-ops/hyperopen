# Split optimizer view-model facade into focused namespaces

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current while the work proceeds. This document follows `.agents/PLANS.md` from the repository root.

## Purpose / Big Picture

The portfolio optimizer application view-model facade has grown into a near-limit namespace that builds unrelated read models for the setup workspace, scenario detail route, universe history/search state, tracking panel, execution modal, and inputs audit tab. That makes future optimizer work harder because a contributor changing one read model must scan unrelated state projection logic. After this change, the existing public namespace `hyperopen.portfolio.optimizer.application.view-model` remains compatible for callers, while its implementation delegates to focused child namespaces under `hyperopen.portfolio.optimizer.application.view-model.*`.

This is an internal architecture refactor with no intended UI behavior change. A human can see it working by running the ClojureScript tests: new tests prove that each child namespace returns the same maps as the compatibility facade, and the existing optimizer view tests prove rendered behavior remains stable.

## Context References

Public refs:

- Direct user/maintainer request on 2026-05-13: "The application view-model facade is doing too many jobs ... Refactor: split into view_model/workspace.cljs, scenario.cljs, universe.cljs, tracking.cljs, execution.cljs, keeping the current namespace as a compatibility facade."

Repo artifacts:

- Root repo contract: `AGENTS.md`.
- Planning contract: `docs/PLANS.md` and `.agents/PLANS.md`.
- Optimizer boundary contract: `src/hyperopen/portfolio/optimizer/BOUNDARY.md`.
- Prior selector-layer plan: `docs/exec-plans/completed/2026-05-12-optimizer-view-model-selector-layer.md`.
- Prior workflow boundary plan: `docs/exec-plans/completed/2026-05-12-optimizer-workflow-view-model-boundary.md`.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-13 13:20Z) Read the root instructions, local `$feature-flow` workflow, planning contract, prior optimizer view-model ExecPlans, current `view_model.cljs`, existing tests, and optimizer boundary document.
- [x] (2026-05-13 13:20Z) Created this active ExecPlan from the direct maintainer request.
- [x] (2026-05-13 13:22Z) Added RED tests requiring the focused child namespaces and proving facade delegation.
- [x] (2026-05-13 13:23Z) Verified RED with `npm test`; compilation failed because `hyperopen.portfolio.optimizer.application.view-model.execution` was unavailable.
- [x] (2026-05-13 13:31Z) Moved the current model-building functions into `view_model/workspace.cljs`, `view_model/scenario.cljs`, `view_model/universe.cljs`, `view_model/tracking.cljs`, and `view_model/execution.cljs`.
- [x] (2026-05-13 13:31Z) Reduced `view_model.cljs` to a compatibility facade exporting the existing public functions plus `selected-history-status`.
- [x] (2026-05-13 13:35Z) Restored JS dependencies with `npm ci` after `npm test` compiled successfully but could not load Lucide modules from an empty `node_modules`.
- [x] (2026-05-13 13:40Z) Moved the new facade contract tests into `view_model_facade_test.cljs` after `npm run check` caught a namespace-size guardrail violation in the existing `view_model_test.cljs`.
- [x] (2026-05-13 13:46Z) Ran required validation: final `npm run check`, `npm test`, and `npm run test:websocket` all passed.
- [x] (2026-05-13 13:47Z) Updated this ExecPlan with evidence, outcomes, and the final file list; it is ready to move to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: `view_model.cljs` already contains the complete route-facing read model introduced by the two 2026-05-12 optimizer view-model plans.
  Evidence: `rg` found `workspace-model`, `scenario-detail-model`, `selected-history-label`, `universe-section-model`, `universe-panel-model`, `tracking-model`, `execution-modal-model`, and `inputs-audit-model` in `src/hyperopen/portfolio/optimizer/application/view_model.cljs`.
- Observation: The requested child namespace file names map cleanly to ClojureScript namespace names with the existing hyphen/underscore convention.
  Evidence: `hyperopen.portfolio.optimizer.application.view-model.workspace` should live at `src/hyperopen/portfolio/optimizer/application/view_model/workspace.cljs`, matching the requested `view_model/workspace.cljs` path shape.
- Observation: The user named five child namespaces but also called out the inputs audit model. This plan needs to place `inputs-audit-model` without adding an unrequested sixth child namespace.
  Evidence: The requested split list includes `workspace.cljs`, `scenario.cljs`, `universe.cljs`, `tracking.cljs`, and `execution.cljs`, but not `inputs.cljs`.
- Observation: The repo's `npm test` script is the full generated ClojureScript runner; the planned `--include` form is not a supported focused test path for this repository.
  Evidence: `package.json` defines `test` as `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`; the RED and GREEN validation used `npm test`.
- Observation: The first GREEN `npm test` compile passed but runtime execution failed because JS dependencies were absent.
  Evidence: `npm ls lucide source-map-support --depth=0` reported `(empty)`, and the runner failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`. Running `npm ci` installed 335 packages from `package-lock.json`.
- Observation: Adding the new tests to `view_model_test.cljs` pushed that test namespace over the repo line-count limit.
  Evidence: `npm run check` stopped at `lint:namespace-sizes` with `test/hyperopen/portfolio/optimizer/application/view_model_test.cljs - namespace has 517 lines`; moving the new tests to `view_model_facade_test.cljs` restored the size check.
- Observation: Facade/child equality tests must avoid volatile readiness timestamps.
  Evidence: the first `npm test` after extracting `view_model_facade_test.cljs` failed because two separate `scenario-detail-model` calls produced `:as-of-ms` values 1ms apart. Setting `[:portfolio :optimizer :runtime :as-of-ms]` in the fixture made the model deterministic.

## Decision Log

- Decision: Keep all existing public function names in `hyperopen.portfolio.optimizer.application.view-model`.
  Rationale: The user explicitly asked for a compatibility facade. Existing views and tests should not need import changes to preserve public API stability.
  Date/Author: 2026-05-13 / Codex.
- Decision: Put `inputs-audit-model` in `hyperopen.portfolio.optimizer.application.view-model.scenario`.
  Rationale: The inputs audit tab describes the active or draft scenario's inputs, so it belongs with scenario route/detail projection more naturally than workspace, universe, tracking, or execution. This honors the requested five-file split without introducing an extra child namespace.
  Date/Author: 2026-05-13 / Codex.
- Decision: Add a keyword-returning `selected-history-status` in the universe child namespace and keep `selected-history-label` as compatibility presentation mapping.
  Rationale: The maintainer specifically called out `selected-history-label` returning UI strings from application logic. The child namespace can expose a status value for application decisions while the facade preserves the old string-returning function for current callers.
  Date/Author: 2026-05-13 / Codex.
- Decision: Put the new compatibility contract tests in `test/hyperopen/portfolio/optimizer/application/view_model_facade_test.cljs`.
  Rationale: The existing `view_model_test.cljs` was already close to the namespace-size threshold. A separate facade test namespace keeps coverage focused without adding a size exception.
  Date/Author: 2026-05-13 / Codex.

## Outcomes & Retrospective

Completed. The facade split reduced the original 491-line `view_model.cljs` to an 88-line compatibility wrapper, and the model-building logic now lives in five focused child namespaces whose line counts range from 66 to 166 lines. Existing optimizer views continue importing the same facade, so caller behavior stayed stable. The main remaining debt is that tracking and execution each still own small private row-label enrichment helpers; they are intentionally kept local because adding a sixth shared namespace was outside the requested split and the duplication is narrow.

## Context and Orientation

The portfolio optimizer stores application state under `[:portfolio :optimizer]` and UI-only optimizer state under `[:portfolio-ui :optimizer]`. Shared path constants live in `src/hyperopen/portfolio/optimizer/contracts.cljs`; application code should prefer those constants over literal state paths. The current `src/hyperopen/portfolio/optimizer/application/view_model.cljs` namespace is pure ClojureScript: it takes state, route, or draft maps and returns plain read-model maps. It must not perform browser, network, IndexedDB, websocket, or order-submission side effects.

The existing public callers are optimizer views under `src/hyperopen/views/portfolio/optimize/`. They currently require `hyperopen.portfolio.optimizer.application.view-model` as a facade and call functions such as `workspace-model`, `scenario-detail-model`, `universe-section-model`, `selected-history-label`, `tracking-model`, `execution-modal-model`, and `inputs-audit-model`. This refactor should keep those calls valid.

The requested child namespace paths and responsibilities are:

- `src/hyperopen/portfolio/optimizer/application/view_model/workspace.cljs`: setup workspace projection and shared run-currentness helpers needed by that projection.
- `src/hyperopen/portfolio/optimizer/application/view_model/scenario.cljs`: route mismatch detection, scenario-scoped fallback state, scenario naming, scenario detail projection, and inputs audit projection.
- `src/hyperopen/portfolio/optimizer/application/view_model/universe.cljs`: universe search/candidate projection, history status decisions, and compatibility history labels.
- `src/hyperopen/portfolio/optimizer/application/view_model/tracking.cljs`: tracking record projection, latest snapshot projection, and tracking/manual enablement booleans.
- `src/hyperopen/portfolio/optimizer/application/view_model/execution.cljs`: execution modal projection, execution attempt projection, and submit confirmation booleans.

## Plan of Work

First, add `test/hyperopen/portfolio/optimizer/application/view_model_facade_test.cljs` with imports for the child namespaces. Add tests that call representative child functions and assert that the compatibility facade returns identical values for the same inputs. Include `selected-history-status` to prove the universe child exposes non-string status values while `selected-history-label` remains stable for old callers.

Second, run the narrowest relevant test command. The expected RED failure is a ClojureScript compile failure because the child namespaces do not exist yet.

Third, create the child namespace directory `src/hyperopen/portfolio/optimizer/application/view_model/` and move function groups from `view_model.cljs` into the five child files. Keep helper functions private where they are not part of the public contract. Duplicate no behavior intentionally; if two child namespaces need label enrichment, prefer a small shared public helper in the most appropriate existing namespace only if it avoids real duplication without widening the request.

Fourth, reduce `src/hyperopen/portfolio/optimizer/application/view_model.cljs` to a compatibility facade. It should require the five child namespaces and expose the existing public vars. The facade should retain `workspace-model`, `scenario-detail-model`, `scenario-scoped-state`, `scenario-name`, `optimizer-draft`, `optimizer-running?`, `result`, `solved-result?`, `scenario-stale?`, `current-result?`, `selected-history-label`, `universe-section-model`, `universe-panel-model`, `tracking-model`, `execution-modal-model`, and `inputs-audit-model`. It may also expose `selected-history-status` as a new migration-friendly function.

Fifth, run focused validation, then the required repository gates. Because this refactor changes application view-model code consumed by UI views but does not alter browser flows, deterministic ClojureScript tests are the smallest relevant verification. If rendered optimizer view tests fail, fix the projection split before broadening.

## Concrete Steps

All commands run from `/Users/barry/.codex/worktrees/903b/hyperopen`.

Add RED tests and run:

    npm test

Expected before implementation: compilation fails because one or more `hyperopen.portfolio.optimizer.application.view-model.*` namespaces cannot be found.

Implement the split and run the focused test:

    npm test

Expected after implementation: `hyperopen.portfolio.optimizer.application.view-model-facade-test`, `hyperopen.portfolio.optimizer.application.view-model-test`, and the existing optimizer view tests pass with 0 failures and 0 errors.

Run required repository gates after code changes:

    npm run check
    npm test
    npm run test:websocket

Expected at completion: each command exits with code 0. If a command fails for an unrelated pre-existing reason, record the command, failure, and evidence here before handing off.

Observed RED validation:

    npm test
    Generated test/test_runner_generated.cljs with 630 namespaces.
    The required namespace "hyperopen.portfolio.optimizer.application.view-model.execution" is not available, it was required by "hyperopen/portfolio/optimizer/application/view_model_test.cljs".

Observed dependency restoration after compile-only GREEN:

    npm ci
    added 335 packages, and audited 336 packages in 3s

Observed GREEN validation after implementation and test fixture stabilization:

    npm test
    Ran 3867 tests containing 21353 assertions.
    0 failures, 0 errors.

Observed required validation:

    npm run check
    Exited 0 after repo guardrails, docs checks, namespace checks, release/style/dev-server tests, and Shadow app/portfolio/worker/test builds completed with 0 warnings.

    npm test
    Ran 3867 tests containing 21353 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

## Validation and Acceptance

Acceptance is met when `src/hyperopen/portfolio/optimizer/application/view_model.cljs` is a small compatibility facade and the model-building work lives in the five focused child namespaces requested by the maintainer. Existing callers must continue to compile without import changes. Existing optimizer application and view tests must continue to pass, proving there is no intended behavior change.

The new tests must fail before implementation because the child namespaces are missing, then pass after implementation because the child namespaces return the same read models as the facade. The full gates required by `AGENTS.md` for code changes are `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

The planned edits are a pure refactor. Re-running tests is safe. If the child namespace split causes a compile failure, restore the missing require or facade export rather than changing caller imports. Do not change optimizer persisted storage shape, scenario records, run state, tracking records, execution records, or view markup in this plan.

## Artifacts and Notes

Initial source hotspot:

    src/hyperopen/portfolio/optimizer/application/view_model.cljs
      491 lines. Owns workspace, scenario, universe, tracking, execution, and inputs audit projection.

Expected final source layout:

    src/hyperopen/portfolio/optimizer/application/view_model.cljs
      Compatibility facade only.

    src/hyperopen/portfolio/optimizer/application/view_model/workspace.cljs
      Workspace projection.

    src/hyperopen/portfolio/optimizer/application/view_model/scenario.cljs
      Scenario route/detail and inputs audit projection.

    src/hyperopen/portfolio/optimizer/application/view_model/universe.cljs
      Universe search and history status projection.

    src/hyperopen/portfolio/optimizer/application/view_model/tracking.cljs
      Tracking projection.

    src/hyperopen/portfolio/optimizer/application/view_model/execution.cljs
      Execution modal projection.

Final implementation touched:

    docs/exec-plans/completed/2026-05-13-optimizer-view-model-facade-split.md
    src/hyperopen/portfolio/optimizer/application/view_model.cljs
    src/hyperopen/portfolio/optimizer/application/view_model/workspace.cljs
    src/hyperopen/portfolio/optimizer/application/view_model/scenario.cljs
    src/hyperopen/portfolio/optimizer/application/view_model/universe.cljs
    src/hyperopen/portfolio/optimizer/application/view_model/tracking.cljs
    src/hyperopen/portfolio/optimizer/application/view_model/execution.cljs
    test/hyperopen/portfolio/optimizer/application/view_model_facade_test.cljs
    test/test_runner_generated.cljs

## Interfaces and Dependencies

The compatibility facade `hyperopen.portfolio.optimizer.application.view-model` must keep these stable functions:

    workspace-model [state route] -> map
    scenario-detail-model [state route] -> map
    scenario-scoped-state [state scenario-id] -> state map
    scenario-name [state scenario-id] -> string
    optimizer-draft [state] -> draft map
    optimizer-running? [state] -> boolean
    result [state] -> result map or nil
    solved-result? [state] -> boolean
    scenario-stale? [state readiness] -> boolean
    current-result? [state readiness] -> boolean
    selected-history-label [state readiness history-load-state history-status-by-id instrument] -> string
    universe-section-model [state draft] and [state draft opts] -> map
    universe-panel-model [state draft] -> map
    tracking-model [state] -> map
    execution-modal-model [state] -> map
    inputs-audit-model [state] -> map

The new universe child namespace should expose:

    selected-history-status [state readiness history-load-state history-status-by-id instrument] -> keyword

The returned maps are plain data. They must not include Hiccup, CSS class vectors, DOM event maps, browser APIs, or effect commands.

## Plan Revision Notes

2026-05-13 / Codex: Created the active plan from the direct maintainer request after inspecting the current facade, prior optimizer view-model plans, existing tests, and repository planning rules.

2026-05-13 / Codex: Updated progress, discoveries, decisions, validation evidence, final file list, and retrospective after implementation and required gates passed. This plan is ready to move from `active` to `completed`.
