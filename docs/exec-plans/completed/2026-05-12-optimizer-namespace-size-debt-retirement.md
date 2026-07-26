# Retire Optimizer Namespace-Size Exceptions

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan follows `/hyperopen/.agents/PLANS.md`. It is based on a direct user request to retire the remaining explicit optimizer namespace-size debt in `/hyperopen/dev/namespace_size_exceptions.edn`, especially the setup v4 section namespace and the oversized optimizer test suites at exception lines 45 through 47.

## Purpose / Big Picture

The repository enforces a 500-line namespace-size guardrail for ClojureScript files under `src` and `test`. The optimizer setup UI and several optimizer tests still rely on temporary exceptions, which makes future optimizer work harder because small additions must either grow debt or perform cleanup first. After this change, the optimizer setup controls and setup summary workspace will live in focused namespaces, the large optimizer test files will be split into smaller fixture-backed suites, and `npm run lint:namespace-sizes` will pass without the targeted optimizer exception entries.

The observable result is structural and behavioral. A contributor can run namespace-size lint and see no exception for `src/hyperopen/views/portfolio/optimize/setup_v4_sections.cljs`, `test/hyperopen/portfolio/optimizer/application/history_loader_test.cljs`, `test/hyperopen/views/portfolio/optimize/setup_v4_layout_test.cljs`, or `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_test.cljs`. The old public setup namespace must continue to expose `control-rail`, `setup-bottom-actions`, and `summary-pane`, and the moved tests must still be discovered by the generated test runner.

## Context References

Public refs:

- Direct user request in the current Codex session: "Retire optimizer namespace-size exceptions. Current explicit optimizer debt remains in dev/namespace_size_exceptions.edn (line 17), especially setup_v4_sections.cljs, plus oversized optimizer tests at lines 45-47. Refactor: split setup sections into focused model_controls, objective_controls, constraint_controls, setup_actions, and use_my_views_workspace namespaces. Split big tests into fixture-backed suites."

Repo artifacts:

- `/hyperopen/AGENTS.md` requires ExecPlans for significant refactors and required gates after code changes.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define the ExecPlan contract.
- `/hyperopen/dev/check_namespace_sizes.clj` defines the 500-line namespace guard and validates `/hyperopen/dev/namespace_size_exceptions.edn`.
- `/hyperopen/dev/namespace_size_exceptions.edn` contains the targeted temporary exceptions.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-12T02:45Z) Confirmed this checkout is already an isolated linked worktree and `git status --short` is clean.
- [x] (2026-05-12T02:45Z) Read the namespace-size exception registry, planning contract, setup namespace, and target test files.
- [x] (2026-05-12T02:45Z) Ran `npm run lint:namespace-sizes`; it passed with the existing temporary exceptions still present.
- [x] (2026-05-12T02:49Z) Split `src/hyperopen/views/portfolio/optimize/setup_v4_sections.cljs` into focused setup namespaces while preserving public entry points.
- [x] (2026-05-12T02:52Z) Split the three oversized optimizer test suites into fixture-backed suites and shared test support.
- [x] (2026-05-12T02:52Z) Removed the four retired exception entries from `dev/namespace_size_exceptions.edn` and regenerated `test/test_runner_generated.cljs`.
- [x] (2026-05-12T02:57Z) Ran namespace-size lint and required gates: `npm run lint:namespace-sizes`, `npm test`, `npm run check`, and `npm run test:websocket` all exited 0.
- [x] (2026-05-12T02:59Z) Accounted for UI browser QA with `npm run test:playwright:smoke`; the deterministic smoke suite passed.
- [x] (2026-05-12T03:04Z) Ran governed design-system browser QA for `portfolio-optimizer-route`; all required widths and passes completed with overall `PASS`.
- [x] (2026-05-12T02:57Z) Moved this ExecPlan to `docs/exec-plans/completed/` after acceptance criteria passed.

## Surprises & Discoveries

- Observation: The existing setup source split already has nearby focused namespaces for header, context, summary, universe, and use-my-views cards, so this cleanup should extend the existing pattern instead of introducing a new component directory.
  Evidence: `src/hyperopen/views/portfolio/optimize/setup_v4_header.cljs`, `setup_v4_context.cljs`, `setup_v4_summary.cljs`, `setup_v4_universe.cljs`, and `setup_v4_use_my_views_cards.cljs` are already under the 500-line threshold.

- Observation: Namespace-size lint currently passes only because the targeted exceptions are present.
  Evidence: `npm run lint:namespace-sizes` exited 0 at 2026-05-12T02:45Z while `dev/namespace_size_exceptions.edn` still listed the setup source namespace and three optimizer test namespaces.

- Observation: After the source and test splits, namespace-size lint reported exactly the intended four entries as stale.
  Evidence: `npm run lint:namespace-sizes` reported stale exceptions for `src/hyperopen/views/portfolio/optimize/setup_v4_sections.cljs` at 100 lines, `test/hyperopen/portfolio/optimizer/application/history_loader_test.cljs` at 160 lines, `test/hyperopen/views/portfolio/optimize/setup_v4_layout_test.cljs` at 279 lines, and `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_test.cljs` at 50 lines.

- Observation: The first `npm test` compile succeeded but the Node test runtime could not resolve the app's existing `lucide/dist/esm/icons/external-link.js` import because npm dependencies were not installed in this worktree.
  Evidence: `npm test` failed with `Error: Cannot find module 'lucide/dist/esm/icons/external-link.js'`; `npm ls lucide --depth=0` showed `(empty)`. Running `npm install` installed dependencies, and the next `npm test` passed.

- Observation: The first governed design-review attempt was blocked by an existing Shadow CLJS dev server on port 9630, left by prior browser tooling.
  Evidence: `npm run qa:design-ui -- --targets portfolio-optimizer-route --manage-local-app` failed with `shadow-cljs already running in project on http://localhost:9630`. Running `npm run browser:cleanup` and `npm run dev:kill` stopped one browser-inspection session and six worktree dev-server processes; the retry passed.

## Decision Log

- Decision: Keep `hyperopen.views.portfolio.optimize.setup-v4-sections` as a compatibility facade for the existing public functions.
  Rationale: Route code and tests already require that namespace. A facade minimizes UI blast radius while moving private rendering ownership into focused model, objective, constraint, action, and use-my-views namespaces.
  Date/Author: 2026-05-12 / Codex.

- Decision: Split test files by behavior cluster and move duplicated helper data into test-support namespaces instead of repeating fixtures.
  Rationale: The user asked for fixture-backed suites. Shared test helpers keep each moved suite below the guardrail and make future optimizer coverage easier to place.
  Date/Author: 2026-05-12 / Codex.

- Decision: Keep runtime adapter history tests as their own focused suite and scenario persistence tests as a separate suite using the existing `hyperopen.portfolio.optimizer.fixtures` solved-run fixtures.
  Rationale: The original runtime adapter suite had no reusable local helper namespace to extract; splitting by effect-adapter behavior eliminated the exception while preserving the existing fixture-backed scenario records.
  Date/Author: 2026-05-12 / Codex.

## Outcomes & Retrospective

Implementation retired the four targeted optimizer namespace-size exceptions without replacement. Overall complexity went down: `setup_v4_sections.cljs` is now a 100-line compatibility facade over focused setup control/action/workspace namespaces, and the large optimizer tests are split into behavior-specific suites with shared fixture/helper namespaces where local setup had been duplicated. The generated test runner now discovers the new focused suites, the required validation gates passed after installing this worktree's npm dependencies, the committed Playwright smoke suite covered the stable browser route path including optimizer setup/detail rendering, and governed browser QA passed for the optimizer setup route at all required review widths.

## Context and Orientation

The repository uses ClojureScript namespaces under `src/hyperopen/**` for production code and `test/hyperopen/**` for tests. A namespace maps to one `.cljs` file. The guard in `dev/check_namespace_sizes.clj` scans `src` and `test` and fails when any `.cljs` file has more than 500 lines unless the path appears in `dev/namespace_size_exceptions.edn` with a capped temporary exception.

The production target is `src/hyperopen/views/portfolio/optimize/setup_v4_sections.cljs`. It currently owns several different UI responsibilities in one file: generic section controls, objective selection, return and risk model controls, constraints, bottom run/save actions, the use-my-views workspace, and the non-Black-Litterman summary pane. It exposes three public functions that must remain stable: `control-rail`, `setup-bottom-actions`, and `summary-pane`.

The test targets are `test/hyperopen/portfolio/optimizer/application/history_loader_test.cljs`, `test/hyperopen/views/portfolio/optimize/setup_v4_layout_test.cljs`, and `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_test.cljs`. These suites are large because they mix multiple behavior clusters and local helper fixtures. The generated runner at `test/test_runner_generated.cljs` discovers every file ending in `_test.cljs` under `test/hyperopen`; it must be regenerated with `npm run test:runner:generate` after adding new test namespaces.

## Plan of Work

First, create small setup rendering namespaces beside the existing setup files:

- `src/hyperopen/views/portfolio/optimize/setup_v4_controls.cljs` for shared view helpers such as section headings, panels, labels, percent labels, and number inputs.
- `src/hyperopen/views/portfolio/optimize/setup_v4_model_controls.cljs` for the return and risk model disclosure panel.
- `src/hyperopen/views/portfolio/optimize/setup_v4_objective_controls.cljs` for the objective card grid and target parameter input.
- `src/hyperopen/views/portfolio/optimize/setup_v4_constraint_controls.cljs` for the constraints disclosure panel.
- `src/hyperopen/views/portfolio/optimize/setup_v4_setup_actions.cljs` for bottom run/save/view actions and action label helpers.
- `src/hyperopen/views/portfolio/optimize/setup_v4_use_my_views_workspace.cljs` for the Black-Litterman center workspace and legend.

Then reduce `setup_v4_sections.cljs` to a compatibility owner that requires those focused namespaces, keeps the `active-preset` and summary-row helpers if useful, and delegates `control-rail`, `setup-bottom-actions`, and Black-Litterman summary rendering to the new namespaces. Each new source namespace and the facade must remain below 500 lines.

Next, create fixture-backed test support for the oversized suites. For history loader tests, move date, candle, vault, and summary helpers into `test/hyperopen/portfolio/optimizer/application/history_loader_fixtures.cljs`, then split request-plan coverage and vault-history alignment coverage into focused `_test.cljs` files while leaving common market alignment coverage in the root. For setup v4 layout tests, move Hiccup tree utilities and Black-Litterman setup fixtures into `test/hyperopen/views/portfolio/optimize/setup_v4_layout_fixtures.cljs`, then split route/control, use-my-views workspace, universe search/status, and constraints/action coverage into focused suites. For runtime adapter tests, split history loading/prefetch tests and scenario persistence tests from the facade/root tests; scenario persistence coverage continues to use `hyperopen.portfolio.optimizer.fixtures` for solved-run records.

Finally, remove the targeted entries from `dev/namespace_size_exceptions.edn`, regenerate the generated runner, run focused tests and required gates, update this plan with evidence, and move it to completed.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/537f/hyperopen`.

1. Create and wire the setup v4 focused source namespaces, then check line counts with:

       wc -l src/hyperopen/views/portfolio/optimize/setup_v4_sections.cljs src/hyperopen/views/portfolio/optimize/setup_v4_*controls.cljs src/hyperopen/views/portfolio/optimize/setup_v4_setup_actions.cljs src/hyperopen/views/portfolio/optimize/setup_v4_use_my_views_workspace.cljs

2. Create fixture-backed optimizer test support and split the three oversized test namespaces. Regenerate the test runner:

       npm run test:runner:generate

3. Remove the retired exception entries and run:

       npm run lint:namespace-sizes

   Expected result after implementation: `Namespace size check passed.`

4. Run focused ClojureScript tests with:

       npm test

   If full `npm test` is too slow to debug a focused failure, use the test names in the failure output to inspect the moved suite, but rerun full `npm test` before claiming acceptance.

5. Run required gates:

       npm run check
       npm test
       npm run test:websocket

## Validation and Acceptance

Acceptance requires all of the following:

- `dev/namespace_size_exceptions.edn` no longer contains entries for `src/hyperopen/views/portfolio/optimize/setup_v4_sections.cljs`, `test/hyperopen/portfolio/optimizer/application/history_loader_test.cljs`, `test/hyperopen/views/portfolio/optimize/setup_v4_layout_test.cljs`, or `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_test.cljs`.
- Every moved source and test namespace is at or below 500 lines.
- Existing public setup entry points remain available from `hyperopen.views.portfolio.optimize.setup-v4-sections`.
- `test/test_runner_generated.cljs` includes the new focused test namespaces.
- `npm run lint:namespace-sizes`, `npm run check`, `npm test`, and `npm run test:websocket` all exit 0, or any unrelated blocker is recorded here with exact output.

## Idempotence and Recovery

The split is additive-first. Create focused namespaces, update the old namespace to delegate, and run tests before deleting or trimming old helpers. If a moved test fails, compare the moved assertion against the pre-split source and restore the exact helper or fixture data rather than changing behavior. `npm run test:runner:generate` is safe to repeat and deterministically rewrites `test/test_runner_generated.cljs`.

If namespace-size lint fails, inspect `wc -l` for the reported files and continue the split until the file is below 500 lines. Do not add replacement exceptions for the targeted optimizer files; that would fail the purpose of this plan.

## Artifacts and Notes

Initial structural baseline:

    npm run lint:namespace-sizes
    Namespace size check passed.

Initial target line counts:

    545 src/hyperopen/views/portfolio/optimize/setup_v4_sections.cljs
    630 test/hyperopen/portfolio/optimizer/application/history_loader_test.cljs
    860 test/hyperopen/views/portfolio/optimize/setup_v4_layout_test.cljs
    632 test/hyperopen/runtime/effect_adapters/portfolio_optimizer_test.cljs

Final target line counts:

    100 src/hyperopen/views/portfolio/optimize/setup_v4_sections.cljs
    160 test/hyperopen/portfolio/optimizer/application/history_loader_test.cljs
    279 test/hyperopen/views/portfolio/optimize/setup_v4_layout_test.cljs
     50 test/hyperopen/runtime/effect_adapters/portfolio_optimizer_test.cljs
    414 test/hyperopen/portfolio/optimizer/application/history_loader_vaults_test.cljs
    274 test/hyperopen/views/portfolio/optimize/setup_v4_universe_layout_test.cljs
    324 test/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios_test.cljs

Validation evidence:

    npm run lint:namespace-sizes
    Namespace size check passed.

    npm test
    Ran 3838 tests containing 21211 assertions.
    0 failures, 0 errors.

    npm run check
    Completed successfully, including namespace-size lint, namespace-boundary lint, release/style/dev-server tests, and app/portfolio/worker/test Shadow CLJS compiles with 0 warnings.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

    npm run test:playwright:smoke
    26 passed.

    npm run qa:design-ui -- --targets portfolio-optimizer-route --manage-local-app
    Overall state: PASS.
    Review widths: review-375, review-768, review-1280, review-1440.
    Passes: visual-evidence-captured PASS, native-control PASS, styling-consistency PASS, interaction PASS, layout-regression PASS, jank-perf PASS.
    Artifacts: /Users/barry/.codex/worktrees/537f/hyperopen/tmp/browser-inspection/design-review-2026-05-12T03-03-44-741Z-216540fb
    Residual blind spot: default passive sampling does not force hover, active, disabled, and loading states when they are not present by default.

    npm run browser:cleanup
    stopped: []

    npm run dev:kill
    No Hyperopen dev server processes found for this worktree.

## Interfaces and Dependencies

The source split must preserve these public functions:

    hyperopen.views.portfolio.optimize.setup-v4-sections/control-rail
    hyperopen.views.portfolio.optimize.setup-v4-sections/setup-bottom-actions
    hyperopen.views.portfolio.optimize.setup-v4-sections/summary-pane

The new setup source namespaces may expose only the functions needed by the facade and sibling namespaces. Hiccup data remains ordinary ClojureScript vectors and maps. UI events must keep existing action vectors such as `[:actions/run-portfolio-optimizer-from-draft]`, `[:actions/set-portfolio-optimizer-return-model-kind :black-litterman]`, and `[:actions/set-portfolio-optimizer-constraint :gross-max [:event.target/value]]`.

The new test fixture namespaces should not end with `_test.cljs`, because they are support modules rather than suites. Test files that do end with `_test.cljs` will be picked up automatically by `tools/generate-test-runner.mjs`.

Revision note, 2026-05-12T02:45Z: Initial ExecPlan created from the user request after auditing current namespace-size exceptions, existing setup source ownership, reusable optimizer fixtures, and test runner discovery.

Revision note, 2026-05-12T02:57Z: Implemented the optimizer namespace-size debt retirement, removed the targeted exception entries, regenerated test discovery, validated required gates, recorded final evidence, and moved the plan to completed.

Revision note, 2026-05-12T02:59Z: Added deterministic Playwright smoke evidence for the UI-facing source refactor. No Browser MCP or browser-inspection sessions were created.

Revision note, 2026-05-12T03:04Z: Added governed design-system browser QA evidence for `portfolio-optimizer-route`, recorded the initial stale dev-server blocker and cleanup, and confirmed browser cleanup after the passing review.
