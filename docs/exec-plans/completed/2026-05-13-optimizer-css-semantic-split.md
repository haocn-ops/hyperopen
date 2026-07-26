---
owner: trading-ui
status: completed
source_of_truth: true
---

# Optimizer CSS Semantic Split

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`.

## Purpose / Big Picture

The optimizer stylesheet is currently one large file, `src/styles/surfaces/optimizer.css`, with styling coupled to `data-role` attributes that are meant to be stable test hooks. This makes CSS ownership hard to scan and creates a risk that changing a selector for presentation can break tests. After this change, the optimizer CSS entrypoint remains stable, but the actual rules are split into focused setup, results, universe, and frontier partials. The rendered UI keeps existing `data-role` attributes for tests while semantic component classes become the styling contract.

## Context References

Public refs:

- Direct maintainer request on 2026-05-13: "Optimizer styling is a large data-role-driven CSS surface ... Refactor: split setup/results/universe/frontier CSS and move from test-oriented data-role selectors to semantic component classes while preserving data-role for tests."

Repo artifacts:

- Root repo contract: `AGENTS.md`.
- UI contract: `docs/FRONTEND.md`, `docs/BROWSER_TESTING.md`, `docs/agent-guides/browser-qa.md`, `docs/agent-guides/ui-foundations.md`, and `docs/agent-guides/trading-ui-policy.md`.
- Planning contract: `docs/PLANS.md` and `.agents/PLANS.md`.
- Current stylesheet: `src/styles/surfaces/optimizer.css`.
- Current optimizer views: `src/hyperopen/views/portfolio/optimize/**`.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-13T13:48:29Z) Read the repo instructions, planning docs, browser testing docs, frontend policy, existing optimizer CSS, stylesheet imports, style tests, and optimizer view markup that owns setup/results/universe/frontier surfaces.
- [x] (2026-05-13T13:48:29Z) Created this active ExecPlan before editing source or test files.
- [x] (2026-05-13T13:51:44Z) Added focused style tests proving optimizer CSS is split and optimizer CSS partials do not style through `[data-role...]` or substring class selectors.
- [x] (2026-05-13T13:55:36Z) Split the current optimizer stylesheet into `base.css`, `setup.css`, `results.css`, `universe.css`, and `frontier.css` under `src/styles/surfaces/optimizer/`, leaving `src/styles/surfaces/optimizer.css` as a small import manifest.
- [x] (2026-05-13T13:57:12Z) Added semantic component classes to optimizer view markup for setup, results, universe, and frontier surfaces while preserving existing `data-role` attributes used by tests.
- [x] (2026-05-13T14:03:21Z) Ran focused style and optimizer view validation, then required gates: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-05-13T14:03:21Z) Accounted for browser QA passes: deterministic Playwright route smoke passed; governed design-review browser QA is blocked by local app manager/Shadow server conflict before inspection.

## Surprises & Discoveries

- Observation: `src/styles/surfaces/optimizer.css` is 797 lines and currently mixes root theme tokens, broad utility overrides, setup layout, results layout, target exposure table styling, frontier chart styling, universe search styling, chips, and notes in one file.
  Evidence: `wc -l src/styles/surfaces/optimizer.css` returned `797`, and `rg -n "data-role|rounded|text-trading" src/styles/surfaces/optimizer.css` showed broad selectors near the top of the file.

- Observation: The optimizer stylesheet is imported only through `src/styles/main.css`, and the existing style test expects that import path to remain.
  Evidence: `src/styles/main.css` imports `./surfaces/optimizer.css`, and `tools/styles/main_css_split.test.mjs` lists `./surfaces/optimizer.css` in `expectedImports`.

- Observation: The first focused style-test run could not execute because dependencies were not installed in the worktree.
  Evidence: `npm run test:styles` failed with `Cannot find module '/Users/barry/.codex/worktrees/d460/hyperopen/node_modules/tailwindcss/lib/cli.js'`; `npm ci` then installed the local dependencies.

- Observation: The new style guardrail failed red before the split and passed after the split.
  Evidence: the pre-implementation `npm run test:styles` run reported missing optimizer partials and a non-import-only `optimizer.css`; the post-implementation run passed six style tests, including `optimizer.css remains a focused import entrypoint`, `optimizer CSS partials are split by surface responsibility`, and `optimizer CSS partials style semantic classes instead of data-role test hooks`.

- Observation: The deterministic Playwright optimizer smoke route passed when reusing the already-running local server.
  Evidence: `PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep @smoke` passed one test in 8.7s.

- Observation: The governed design-review browser QA command was blocked before inspection by a local app manager conflict with an already-running Shadow server.
  Evidence: `npm run qa:design-ui -- --targets portfolio-optimizer-route,portfolio-optimizer-results-route --local-url http://127.0.0.1:8080` failed during startup with `shadow-cljs already running in project`; `npm run browser:cleanup` later stopped the lingering browser-inspection session successfully.

## Decision Log

- Decision: Keep `src/styles/surfaces/optimizer.css` as the imported entrypoint and make it import the focused optimizer partials.
  Rationale: This preserves the existing `main.css` import manifest and avoids changing app or workbench CSS load paths while still reducing the large file into scoped ownership areas.
  Date/Author: 2026-05-13 / Codex.

- Decision: Preserve `data-role` attributes in Hiccup and Playwright-facing markup, but remove `[data-role...]` selectors from optimizer CSS files.
  Rationale: `data-role` is still the test and QA locator contract. CSS should rely on semantic classes so presentation can change independently of selectors that tests consume.
  Date/Author: 2026-05-13 / Codex.

- Decision: Treat this as a visual refactor, not a behavior change.
  Rationale: No optimizer state, actions, persistence, worker, or route semantics should change. Validation should prove the CSS compiles and existing view tests still find the same data-role hooks.
  Date/Author: 2026-05-13 / Codex.

- Decision: Keep root-scoped optimizer compatibility utility overrides in `base.css`, but replace the prior substring class selector with explicit semantic/utility selectors.
  Rationale: The optimizer surface still relies on local theme-token overrides for existing Tailwind utility classes. Removing them in the same change would risk visual drift beyond the requested selector refactor, while the data-role coupling and broad rounded substring selector are removed from optimizer CSS.
  Date/Author: 2026-05-13 / Codex.

## Outcomes & Retrospective

Implemented the optimizer CSS refactor. `src/styles/surfaces/optimizer.css` is now a five-line import manifest, and focused partials live in `src/styles/surfaces/optimizer/base.css`, `setup.css`, `results.css`, `universe.css`, and `frontier.css`. The partials style semantic optimizer component classes instead of `data-role` selectors, and the CSS split is protected by `tools/styles/optimizer_css_split.test.mjs`.

Added semantic classes across optimizer setup, results, universe, frontier, scenario detail, target exposure, and rebalance view markup while preserving existing `data-role` hooks for tests and browser tooling.

Validation passed for focused CSS tests, full repo check, full test suite, websocket tests, and deterministic Playwright route smoke. Governed browser design-review QA is not complete because the design-review runner failed before browser inspection due to an existing Shadow server/local app startup conflict. Browser inspection sessions created during that attempt were stopped with `npm run browser:cleanup`.

## Context and Orientation

The optimizer UI lives under `src/hyperopen/views/portfolio/optimize/`. Its CSS currently lives in `src/styles/surfaces/optimizer.css` and is bundled through Tailwind CLI from `src/styles/main.css` into `resources/public/css/main.css`. A `data-role` attribute is a stable testing hook used by ClojureScript view tests, Playwright tests, and browser-inspection tooling. A semantic component class is a CSS class whose name describes a UI responsibility, such as `optimizer-results-grid`, instead of a test locator such as `portfolio-optimizer-results-grid`.

The main setup route surface is rendered by `workspace_view.cljs`, `setup_v4_header.cljs`, `setup_v4_sections.cljs`, `setup_v4_context.cljs`, `setup_v4_controls.cljs`, `setup_v4_universe.cljs`, and related setup modules. Results and scenario detail surfaces are rendered by `scenario_detail_view.cljs`, `results_panel.cljs`, `target_exposure_table.cljs`, and `rebalance_tab.cljs`. Frontier chart surfaces are rendered by `frontier_chart.cljs`, `frontier_target.cljs`, `frontier_overlay_markers.cljs`, and `frontier_callout*.cljs`. Universe search/list controls are primarily in `setup_v4_universe.cljs`, with older `universe_panel.cljs` still present for focused view coverage.

## Plan of Work

First, add a focused Node style test under `tools/styles/` that treats `src/styles/surfaces/optimizer.css` as an import manifest and checks that every CSS partial under `src/styles/surfaces/optimizer/` avoids `[data-role...]` selectors. This test should fail before the split because the current single file still contains `data-role` selectors and no partial directory exists.

Second, split the CSS. The root token and shared compatibility rules go in `src/styles/surfaces/optimizer/base.css`. Setup route, setup rail, setup cards, model assumptions, and action bar rules go in `setup.css`. Results grid, scenario header, tabs, KPI strip, target exposure table, diagnostics rail, and rebalance/result panels go in `results.css`. Universe search, candidate row, add button, and chip rules go in `universe.css`. Frontier panel, overlay mode controls, constrain checkbox, chart box, markers, focus ring, and callouts go in `frontier.css`.

Third, add semantic classes in the Hiccup markup at the same nodes that previously depended on `data-role` selectors. Keep all existing `data-role` values unchanged. Use collection class values when there is more than one class token, following `docs/FRONTEND.md`.

Fourth, run focused validation. The smallest useful commands are `npm run test:styles` and selected ClojureScript optimizer view tests via the full generated test runner if focused test selection is unavailable. Because this is a UI styling refactor, run the smallest relevant Playwright smoke route if practical and account for the governed browser-QA matrix. Finally run the required code-change gates from `AGENTS.md`: `npm run check`, `npm test`, and `npm run test:websocket`.

## Concrete Steps

All commands run from `/Users/barry/.codex/worktrees/d460/hyperopen`.

Add the style guardrail test, then run:

    npm run test:styles

Expected before implementation: the new test fails because optimizer CSS has not been split and still contains `[data-role...]` styling selectors.

After the split and markup update, run:

    npm run test:styles
    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

Expected after implementation: style tests pass, ClojureScript tests compile, and existing optimizer view tests continue to find the same data-role hooks.

Run final required gates:

    npm run check
    npm test
    npm run test:websocket

Expected at completion: each command exits with code 0. If any command fails for an unrelated environmental or pre-existing reason, record the failure and evidence here and in the final response.

## Validation and Acceptance

Acceptance is met when `src/styles/surfaces/optimizer.css` is no longer a 797-line stylesheet and instead imports focused optimizer CSS partials. The new partials must not contain selectors that target `data-role` attributes. The optimizer views must preserve their existing `data-role` attributes so view tests, Playwright tests, and browser-inspection tools continue to work without selector rewrites. CSS should compile through Tailwind, existing optimizer view tests should pass, and the required gates `npm run check`, `npm test`, and `npm run test:websocket` should pass or have explicit blocker evidence.

Browser QA must be accounted for because this touches UI styling. The required passes are visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf across 375, 768, 1280, and 1440 widths. If the governed browser QA command is too broad for the current turn, the final response must state it as blocked or not run rather than implying visual completion.

## Idempotence and Recovery

The changes are a presentation refactor. Re-running the style split and tests is safe. If CSS import order causes a visual or compile issue, restore `optimizer.css` as the stable entrypoint and adjust only its partial import order. If a ClojureScript test fails because a `data-role` disappeared, restore the exact prior `data-role` value and keep the semantic class as an additional class. Do not alter optimizer actions, state paths, persisted scenario formats, route names, or worker behavior in this plan.

## Artifacts and Notes

- Added `tools/styles/optimizer_css_split.test.mjs` to protect the split and semantic selector contract.
- `npm ci` succeeded after the initial style-test run exposed missing `node_modules`.
- `npm run test:styles` passed: 6 tests, including the optimizer split and semantic selector guardrails.
- `npm run lint:hiccup` passed.
- `npm run lint:class-attrs` passed.
- `npm run lint:style-keys` passed.
- `PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep @smoke` passed: 1 test.
- `npm run qa:design-ui -- --targets portfolio-optimizer-route,portfolio-optimizer-results-route --local-url http://127.0.0.1:8080` was blocked before inspection by `shadow-cljs already running in project`.
- `npm run browser:cleanup` succeeded and stopped `sess-1778680755769-fdeb22`.
- `npm run check` passed.
- `npm test` passed: 3867 tests, 21353 assertions, 0 failures, 0 errors.
- `npm run test:websocket` passed: 524 tests, 3043 assertions, 0 failures, 0 errors.
- Selector verification: `rg -n "\[data-role|\[class\*=" src/styles/surfaces/optimizer src/styles/surfaces/optimizer.css tools/styles/optimizer_css_split.test.mjs` returned only the test's own regex pattern.
- Line-count verification: `src/styles/surfaces/optimizer.css` is 5 lines; the optimizer partials total 737 lines.

Revision note (2026-05-13 / Codex): Initial active ExecPlan created from the direct maintainer request after reading the current optimizer CSS, style import path, and optimizer view ownership.

Revision note (2026-05-13 / Codex): Completed the CSS split, semantic class migration, validation, and browser-QA accounting.
