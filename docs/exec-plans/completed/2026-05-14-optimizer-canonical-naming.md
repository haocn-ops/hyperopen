# Retire optimizer v1 and v4 surface naming

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`.

## Purpose / Big Picture

The `/portfolio/optimize` route family is now the canonical portfolio optimizer surface, but active source and tests still use labels such as `setup-v4-*`, `portfolio-optimizer-v4`, and `v1 results`. Those names imply parallel product generations that no longer exist, which slows future agents and maintainers when they search for the current optimizer implementation. After this change, active optimizer source, tests, styles, and boundary docs should use route-family or semantic names such as `setup-*`, `portfolio-optimizer`, and `results workspace`.

This is a naming cleanup with no intended runtime behavior change. The observable proof is that a new optimizer naming guard fails before the cleanup, passes after the cleanup, and the existing optimizer setup/results contract tests still pass with the same user-facing roles and actions.

## Context References

Public refs:

- Direct user/maintainer request in the current Codex session on 2026-05-14: "Retire or alias away v1 / v4 naming once the current surface is canonical."

Repo artifacts:

- `/hyperopen/AGENTS.md` requires an ExecPlan for significant multi-file refactors and requires `npm run check`, `npm test`, and `npm run test:websocket` after code changes.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define the ExecPlan lifecycle and required sections.
- `/hyperopen/src/hyperopen/portfolio/optimizer/BOUNDARY.md` is the canonical optimizer boundary doc that currently still says "v4 route surfaces" and lists `setup-v4-*` test names.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-14T02:22Z) Confirmed the current workspace is a linked git worktree with a clean status before edits.
- [x] (2026-05-14T02:22Z) Searched active optimizer source, tests, styles, and boundary docs for `setup-v4`, `setup_v4`, `portfolio-optimizer-v4`, and `v1 results`.
- [x] (2026-05-14T02:22Z) Created this active ExecPlan from the direct maintainer request.
- [x] (2026-05-14T02:24Z) Added `tools/optimizer/canonical-naming.test.mjs` and watched it fail for the current active optimizer generation labels.
- [x] (2026-05-14T02:33Z) Renamed active setup view/test files and namespaces from `setup-v4-*` to `setup-*`.
- [x] (2026-05-14T02:33Z) Renamed the route wrapper class from `portfolio-optimizer-v4` to `portfolio-optimizer` across active optimizer views, tests, and scoped styles.
- [x] (2026-05-14T02:33Z) Renamed the remaining active result/setup test names and optimizer boundary doc references to canonical wording.
- [x] (2026-05-14T02:34Z) Regenerated `test/test_runner_generated.cljs`; it reported 642 namespaces.
- [x] (2026-05-14T02:34Z) Re-ran `npm run test:optimizer-spike`; all 7 tests passed, including the new canonical naming guard.
- [x] (2026-05-14T02:34Z) Ran focused optimizer ClojureScript validation after recompiling the test build; 50 tests and 611 assertions passed.
- [x] (2026-05-14T02:34Z) Re-ran the required final gates after the final source formatting cleanup: `npm run check`, `npm test`, and `npm run test:websocket` all passed.
- [x] (2026-05-14T02:34Z) Ran the smallest relevant committed browser pass, `optimizer-view-model-routes.smoke.spec.mjs`, after accounting for the default-port blocker.
- [x] (2026-05-14T02:34Z) Recorded final validation evidence and moved this ExecPlan to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The broad `v1` and `v4` search results include unrelated real version identifiers that must not be renamed by this plan.
  Evidence: `src/hyperopen/portfolio/optimizer/infrastructure/persistence.cljs` uses `:edn-v1`, `src/hyperopen/funding/infrastructure/route_clients.cljs` calls LiFi `/v1/quote`, wallet storage keys include `:v1`, and `tools/browser-inspection/src/mcp_server.mjs` imports `zod/v4`.
- Observation: The optimizer naming debt is concentrated in active view/style/test surfaces and the optimizer boundary doc.
  Evidence: targeted search found `setup_v4*.cljs` view files, `setup_v4*.cljs` test files, `.portfolio-optimizer-v4` CSS selectors, `portfolio-optimizer-v4` route wrapper classes, and `results-panel-renders-v1-*` test names.
- Observation: The first RED run also failed an unrelated solver-spike test because `node_modules` did not contain the optional solver packages declared by the repo.
  Evidence: `npm run test:optimizer-spike` initially failed `benchmarkCandidates uses locally installed optional solver packages when present`; after `npm ci`, that test passed and only the new canonical naming guard failed.
- Observation: `src/hyperopen/views/portfolio_view.cljs` owned an outer optimizer route frame class outside the optimizer subdirectory.
  Evidence: the first focused ClojureScript run failed `portfolio-view-optimizer-route-uses-dark-route-frame-test` because the root node still had `portfolio-optimizer-v4`; adding that file to the naming guard and renaming the class fixed the focused suite.
- Observation: The default Playwright web server port was already occupied.
  Evidence: `npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --workers=1` failed with `http://127.0.0.1:8080 is already used`. The same spec passed using `PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080` and the repo static server command `PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs`.

## Decision Log

- Decision: Rename the active optimizer setup view namespaces from `setup-v4-*` to `setup-*` instead of keeping compatibility namespaces.
  Rationale: These namespaces are internal view modules under `hyperopen.views.portfolio.optimize`. Keeping old aliases would preserve the same comprehension debt the request asks to remove, while tests and generated runner can be updated mechanically.
  Date/Author: 2026-05-14 / Codex.
- Decision: Rename the route wrapper class from `portfolio-optimizer-v4` to `portfolio-optimizer` and update scoped optimizer CSS selectors accordingly.
  Rationale: The class is a route-local styling hook, not a documented public API. A direct rename removes the misleading generation label from source and tests without changing data roles or user-facing copy.
  Date/Author: 2026-05-14 / Codex.
- Decision: Add a small Node test under `tools/optimizer/` to guard against future optimizer generation labels in active source and tests.
  Rationale: Existing ClojureScript contract tests prove render behavior but do not prevent misleading names from returning. `npm run test:optimizer-spike` already runs `tools/optimizer/*.test.mjs`, so a repository scanner fits the existing check path without new package scripts.
  Date/Author: 2026-05-14 / Codex.

## Outcomes & Retrospective

The active optimizer setup modules, setup tests, route wrapper classes, scoped optimizer CSS selectors, and optimizer boundary doc now use canonical naming instead of `v1`/`v4` generation labels. A new Node guard in `tools/optimizer/canonical-naming.test.mjs` scans active optimizer source, tests, styles, the portfolio route wrapper, the boundary doc, and the generated test runner so the old labels cannot be reintroduced accidentally.

The implementation reduced agent-comprehension complexity by removing stale generation names from the current source of truth. It did not change route paths, action vectors, `data-role` selectors, optimizer math, persistence keys, or external API version strings. Remaining risk is limited to any out-of-repo consumer that depended on the old `.portfolio-optimizer-v4` CSS hook; in-repo usage now depends on `.portfolio-optimizer`, and all focused and required gates passed.

## Context and Orientation

Hyperopen uses ClojureScript namespaces where file names use underscores and namespace segments use hyphens. For example, a file named `src/hyperopen/views/portfolio/optimize/setup_v4_sections.cljs` declares `(ns hyperopen.views.portfolio.optimize.setup-v4-sections)`. The test runner generated by `npm run test:runner:generate` discovers test namespaces from files under `test/` and writes `test/test_runner_generated.cljs`; after renaming test files or namespaces, the runner must be regenerated.

The current optimizer setup route is composed by `src/hyperopen/views/portfolio/optimize/workspace_view.cljs`. It imports internal setup modules named `setup-v4-context`, `setup-v4-header`, and `setup-v4-sections`, then renders a root `:section` with class `portfolio-optimizer-v4` and data role `portfolio-optimizer-setup-route-surface`. The scenario detail route in `src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs` also uses class `portfolio-optimizer-v4`. Scoped CSS under `src/styles/surfaces/optimizer/` targets that class.

The active tests with misleading generation labels live under `test/hyperopen/views/portfolio/optimize/`. They include setup layout files named `setup_v4_layout_test.cljs`, `setup_v4_universe_layout_test.cljs`, `setup_v4_use_my_views_cards_test.cljs`, `setup_v4_use_my_views_workspace_test.cljs`, and a fixture file named `setup_v4_layout_fixtures.cljs`. Results tests include names such as `results-panel-renders-v1-results-workspace-shell-test` and `results-panel-renders-v1-results-workspace-contract-test`.

This plan intentionally does not rename real data-format or third-party API version strings. Examples that must remain are `edn-v1` persistence encoding, wallet/browser storage keys ending in `:v1`, LiFi `/v1/quote`, Playwright storage-key fixtures, and `zod/v4`.

## Plan of Work

First, add a RED test in `tools/optimizer/canonical-naming.test.mjs`. The test should scan active optimizer paths and report any occurrence of the misleading generation patterns in either file paths or file contents. It should include `src/hyperopen/views/portfolio/optimize`, `test/hyperopen/views/portfolio/optimize`, `src/styles/surfaces/optimizer`, `src/hyperopen/portfolio/optimizer/BOUNDARY.md`, and `test/test_runner_generated.cljs`. It should not scan completed ExecPlans or unrelated versioned persistence/API files. Running `npm run test:optimizer-spike` before implementation should fail and list the current `setup_v4`, `setup-v4`, `portfolio-optimizer-v4`, and `v1-results` locations.

Second, rename active setup view files and namespaces from `setup_v4_*` / `setup-v4-*` to canonical `setup_*` / `setup-*`. Update all requires and local aliases in `workspace_view.cljs`, setup sibling namespaces, and setup tests. Keep function names, action vectors, `data-role` values, and visible copy unchanged unless a test name itself contains the obsolete generation label.

Third, rename route wrapper class usage from `portfolio-optimizer-v4` to `portfolio-optimizer` in `workspace_view.cljs`, `scenario_detail_view.cljs`, `index_view.cljs`, `src/styles/surfaces/optimizer/*.css`, and the affected tests. Keep existing `optimizer-*` child classes and all `data-role` selectors intact.

Fourth, update active test names and docs. Rename setup test files and namespaces to `setup-layout-test`, `setup-universe-layout-test`, `setup-use-my-views-cards-test`, `setup-use-my-views-workspace-test`, and `setup-layout-fixtures`. Rename `results-panel-renders-v1-*` tests to `results-panel-renders-canonical-*`. Update `src/hyperopen/portfolio/optimizer/BOUNDARY.md` so it refers to canonical route surfaces and setup tests rather than `v4` route surfaces.

Finally, regenerate `test/test_runner_generated.cljs`, run focused optimizer tests, run the new naming guard, run required gates, and move this plan to `docs/exec-plans/completed/` with validation evidence.

## Concrete Steps

Run all commands from `/Users/barry/.codex/worktrees/6d50/hyperopen`.

Add the RED guard:

    npm run test:optimizer-spike

Expected before implementation: the new `optimizer canonical naming` test fails and prints active optimizer path/content violations for `setup_v4`, `setup-v4`, `portfolio-optimizer-v4`, and `v1-results` naming.

Rename files with normal filesystem moves or `git mv`, then update namespace declarations and requires. Regenerate the test runner:

    npm run test:runner:generate

Run focused ClojureScript tests after the renames:

    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.views.portfolio.optimize.setup-layout-test --test=hyperopen.views.portfolio.optimize.setup-universe-layout-test --test=hyperopen.views.portfolio.optimize.setup-use-my-views-cards-test --test=hyperopen.views.portfolio.optimize.setup-use-my-views-workspace-test --test=hyperopen.views.portfolio.optimize.results-panel-test --test=hyperopen.views.portfolio.optimize.frontier-chart-contract-test --test=hyperopen.views.portfolio.optimize.workspace-view-test --test=hyperopen.views.portfolio.optimize.scenario-detail-view-test --test=hyperopen.views.portfolio-view-test

Run the guard after implementation:

    npm run test:optimizer-spike

Observed after implementation:

    tests 7
    pass 7
    fail 0

Run required final gates:

    npm run check
    npm test
    npm run test:websocket

Observed after the final source tree was in place:

    npm run check
    exit 0; all docs/tooling checks and app, portfolio, worker, optimizer-worker, vault-worker, and test Shadow builds completed with 0 warnings.

    npm test
    Ran 3898 tests containing 21483 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

    PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --workers=1
    1 passed.

If a focused test fails because a namespace was not renamed everywhere, use `rg "setup-v4|setup_v4|portfolio-optimizer-v4|v1-results|v1 results" <path>` to find the missed reference, update it, regenerate the test runner, and rerun the focused command.

## Validation and Acceptance

Acceptance requires all active optimizer setup/result source and tests to use canonical naming. The new naming guard must pass with no violations in active optimizer paths. Existing focused optimizer tests must pass, proving the rename did not alter Hiccup roles, action vectors, or result layout contracts. Required project gates must pass: `npm run check`, `npm test`, and `npm run test:websocket`.

Acceptance evidence:

    npm run test:optimizer-spike
    tests 7; pass 7; fail 0.

    node out/test.js --test=hyperopen.views.portfolio.optimize.setup-layout-test --test=hyperopen.views.portfolio.optimize.setup-universe-layout-test --test=hyperopen.views.portfolio.optimize.setup-use-my-views-cards-test --test=hyperopen.views.portfolio.optimize.setup-use-my-views-workspace-test --test=hyperopen.views.portfolio.optimize.results-panel-test --test=hyperopen.views.portfolio.optimize.frontier-chart-contract-test --test=hyperopen.views.portfolio.optimize.workspace-view-test --test=hyperopen.views.portfolio.optimize.scenario-detail-view-test --test=hyperopen.views.portfolio-view-test
    Ran 50 tests containing 611 assertions.
    0 failures, 0 errors.

    npm run check
    exit 0.

    npm test
    Ran 3898 tests containing 21483 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

    PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --workers=1
    1 passed.

No browser QA is required for this plan unless the focused Hiccup or compile tests reveal a behavior or styling regression. The change is source/test/style naming only, does not alter interaction flows, and preserves data roles. If browser tooling is started unexpectedly, run `npm run browser:cleanup` before completion.

## Idempotence and Recovery

The cleanup is safe to retry. File renames can be repeated only if the old file still exists; otherwise update the remaining require or generated-runner references. If `npm run test:runner:generate` changes `test/test_runner_generated.cljs`, keep the generated output rather than hand-editing it. If a broad search reports unrelated `v1` or `v4` strings, do not rename them unless they match this plan's optimizer-generation patterns and live in the active optimizer paths named above.

If the final gates fail for reasons unrelated to the rename, record the exact failure in `Surprises & Discoveries`, keep the plan active, and report the blocker instead of claiming completion.

## Artifacts and Notes

Initial targeted search showed the main active debt:

    src/hyperopen/views/portfolio/optimize/workspace_view.cljs requires setup-v4-* modules and renders portfolio-optimizer-v4.
    src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs renders portfolio-optimizer-v4.
    src/styles/surfaces/optimizer/*.css scopes selectors under portfolio-optimizer-v4.
    test/hyperopen/views/portfolio/optimize/setup_v4_*.cljs declares setup-v4-* namespaces.
    test/hyperopen/views/portfolio/optimize/results_panel_test.cljs names a test results-panel-renders-v1-results-workspace-shell-test.
    test/hyperopen/views/portfolio/optimize/frontier_chart_contract_test.cljs names a test results-panel-renders-v1-results-workspace-contract-test.
    src/hyperopen/portfolio/optimizer/BOUNDARY.md still says v4 route surfaces and setup-v4-* tests.

## Interfaces and Dependencies

Preserve these public UI contracts:

- `hyperopen.views.portfolio.optimize.workspace-view/workspace-view` still renders the setup route shell for `/portfolio/optimize/new`.
- `hyperopen.views.portfolio.optimize.scenario-detail-view/scenario-detail-view` still renders scenario detail tabs for `/portfolio/optimize/:scenario-id`.
- All optimizer `data-role` attributes remain unchanged.
- All optimizer action vectors remain unchanged.
- `hyperopen.views.portfolio.optimize.results-panel/results-panel` public arities remain unchanged.

At completion, active setup view namespaces should use these canonical names:

- `hyperopen.views.portfolio.optimize.setup-controls`
- `hyperopen.views.portfolio.optimize.setup-context`
- `hyperopen.views.portfolio.optimize.setup-header`
- `hyperopen.views.portfolio.optimize.setup-model-controls`
- `hyperopen.views.portfolio.optimize.setup-objective-controls`
- `hyperopen.views.portfolio.optimize.setup-constraint-controls`
- `hyperopen.views.portfolio.optimize.setup-sections`
- `hyperopen.views.portfolio.optimize.setup-actions`
- `hyperopen.views.portfolio.optimize.setup-summary`
- `hyperopen.views.portfolio.optimize.setup-universe`
- `hyperopen.views.portfolio.optimize.setup-use-my-views-cards`
- `hyperopen.views.portfolio.optimize.setup-use-my-views-workspace`

## Revision Notes

- 2026-05-14T02:22Z: Created the active plan from the direct maintainer request and initial source/test audit.
- 2026-05-14T02:34Z: Completed implementation, validation, browser QA accounting, and final evidence recording before moving the plan to completed.
