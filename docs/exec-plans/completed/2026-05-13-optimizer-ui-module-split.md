# Split optimizer UI modules below the namespace guardrail

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. It exists because a direct maintainer request on 2026-05-13 asked to split near-threshold optimizer UI modules before adding more features.

## Purpose / Big Picture

The portfolio optimizer UI currently has several ClojureScript view namespaces close to the 500-line guardrail: `src/hyperopen/views/portfolio/optimize/frontier_chart.cljs`, `src/hyperopen/views/portfolio/optimize/frontier_overlay_markers.cljs`, `src/hyperopen/views/portfolio/optimize/results_panel.cljs`, and `src/hyperopen/views/portfolio/optimize/black_litterman_views_panel.cljs`. These files render working UI today, but they mix local data shaping, chart geometry, SVG marker layers, toolbar controls, diagnostics, and summaries. After this change, the user-visible optimizer screens should behave the same, while the source files are split by responsibility so future feature work has smaller, safer edit targets.

The observable result is that existing Hiccup contract tests and Playwright selector contracts still find the same `data-role` anchors, while `npm run lint:namespace-sizes` reports no new optimizer namespace-size failures.

## Context References

Public refs:

- Direct user request in the current Codex session: "Split near-threshold UI modules before adding more features."

Repo artifacts:

- `/hyperopen/AGENTS.md` user-provided root contract for this work.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` for ExecPlan structure.
- `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/BROWSER_TESTING.md`, `/hyperopen/docs/agent-guides/browser-qa.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md` for UI validation expectations.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-13T17:10Z) Confirmed current line counts: `frontier_chart.cljs` 488, `frontier_overlay_markers.cljs` 486, `results_panel.cljs` 480, and `black_litterman_views_panel.cljs` 417.
- [x] (2026-05-13T17:10Z) Read the target source files, target tests, namespace guardrail scripts, and UI/browser QA docs.
- [x] (2026-05-13T17:34Z) Extracted frontier chart model/geometry, toolbar controls, and SVG layers while preserving `frontier-chart/frontier-chart` arities.
- [x] (2026-05-13T17:34Z) Extracted frontier overlay point model/copy and vault marker SVG rendering while preserving `frontier-overlay-markers/marker` and `frontier-overlay-markers/callout`.
- [x] (2026-05-13T17:34Z) Extracted results-panel result data shaping, diagnostics rail, summary helpers, and rebalance preview rendering while preserving `results-panel/results-panel` arities.
- [x] (2026-05-13T17:34Z) Extracted Black-Litterman editor controls and active-view list rendering while preserving `black-litterman-views-panel/black-litterman-views-panel` arities.
- [x] (2026-05-13T18:03Z) Ran namespace guardrails, full project gates, websocket tests, full ClojureScript tests, and focused optimizer Playwright coverage.
- [x] (2026-05-13T18:09Z) Moved this plan to `docs/exec-plans/completed/` after recording validation evidence and the remaining formal Browser Inspection blocker.

## Surprises & Discoveries

- Observation: `results_panel.cljs` contains private helper panels such as `warnings-panel`, `diagnostics-panel`, `assumptions-strip`, `trust-caution-panel`, and `performance-summary` that are not currently called by `results-panel`.
  Evidence: reading `results_panel.cljs` showed only `stale-result-banner`, `target-exposure-table`, `frontier-chart/frontier-chart`, `trust-diagnostics-rail`, and `rebalance-preview` are used in the exported render path.
- Observation: The evidence locations named by the maintainer line up with the mixed responsibilities: `frontier_chart.cljs:300` begins the exported chart component after geometry, toolbar, point, and CSS helpers; `results_panel.cljs:124` begins diagnostics rendering inside the same file that also shapes labels and renders the final grid.
  Evidence: `nl -ba` around those lines shows the described functions.
- Observation: The worktree initially had no `node_modules`, so `npm test` could not start because the generated runner could not resolve `lucide/dist/esm/icons/external-link.js`.
  Evidence: first `npm test` failed before executing tests with a Node module resolution error. Running `npm ci` restored dependencies without changing `package.json` or `package-lock.json`.
- Observation: A normal local Playwright run could not own the expected browser-test port because `127.0.0.1:8080` was already used by another Hyperopen worktree.
  Evidence: `lsof` identified the listener as `/Users/barry/.codex/worktrees/d329/hyperopen`, so this work avoided killing or reusing that unrelated process.
- Observation: Static Playwright verification needed the compiled stylesheet.
  Evidence: the first static-server run exposed CSS-dependent failures for the Black-Litterman listbox and frontier checkbox. After `npm run css:build`, the focused optimizer Playwright command passed.

## Decision Log

- Decision: Keep the existing public entry namespaces and exported component functions as facades instead of changing callers.
  Rationale: This is a structural split. Preserving public arities and `data-role` anchors minimizes regression risk and keeps current tests meaningful.
  Date/Author: 2026-05-13 / Codex.
- Decision: Split by render responsibility inside the existing `hyperopen.views.portfolio.optimize` package, not by moving code into domain/application namespaces.
  Rationale: The extracted code is still view-specific Hiccup, chart geometry, and UI copy. Moving it out of view namespaces would blur architecture boundaries.
  Date/Author: 2026-05-13 / Codex.
- Decision: Treat Browser MCP/design-review evidence as required accounting, but rely first on deterministic Hiccup, namespace, compile, and Playwright commands for regression proof because this task should not change visuals.
  Rationale: The requested behavior is a no-behavior-change refactor. Stable browser paths should remain covered by existing Playwright selectors; design-review passes may be reported as blocked only if the tooling cannot run.
  Date/Author: 2026-05-13 / Codex.
- Decision: Use a temporary static SPA server on port 18080 for focused Playwright verification after the default browser-test port collision.
  Rationale: The default dev-server route was occupied by an unrelated worktree. Serving already compiled `resources/public` on an unused port allowed deterministic verification of the changed optimizer surfaces without disturbing the other process.
  Date/Author: 2026-05-13 / Codex.

## Outcomes & Retrospective

The split lowered the original near-threshold files well under the 500-line guardrail and isolated responsibilities into model, layer, control, diagnostics, summary, and preview namespaces. The public facades and arities remain in place for existing callers and tests.

Validation passed for namespace size, Hiccup style, namespace boundaries, full ClojureScript tests, websocket tests, the full `npm run check` gate, and focused optimizer Playwright coverage. The formal Browser Inspection six-pass/four-width design-review run remains `BLOCKED` because the default browser-inspection/dev-server port was already owned by another Hyperopen worktree and this task did not kill unrelated sessions. No Browser MCP session was intentionally created; the temporary Playwright static server exited cleanly, and ports 18080, 8082, and 8083 were clean afterward.

## Context and Orientation

Hyperopen uses ClojureScript and Hiccup-style view vectors. Hiccup nodes look like `[:section {:data-role "role"} child]`; tests inspect these vectors directly. `data-role` attributes are stable test and browser selectors, so this plan must keep them unchanged.

The target optimizer files live under `src/hyperopen/views/portfolio/optimize/`. `frontier_chart.cljs` owns the efficient frontier chart component. It currently computes domains and SVG point positions, renders toolbar controls, draws grid/path/points, and delegates target and overlay markers. `frontier_overlay_markers.cljs` owns overlay data filtering, overlay copy, asset/vault marker SVGs, hitboxes, and callouts. `results_panel.cljs` owns result label enrichment, stale banner, diagnostics rail, and rebalance preview. `black_litterman_views_panel.cljs` owns the Black-Litterman right-rail editor, including draft derivation, input controls, preview text, active view cards, and clear confirmation.

The main contract tests to preserve are `test/hyperopen/views/portfolio/optimize/results_panel_test.cljs`, `test/hyperopen/views/portfolio/optimize/frontier_chart_contract_test.cljs`, `test/hyperopen/views/portfolio/optimize/frontier_overlay_markers_test.cljs`, and `test/hyperopen/views/portfolio/optimize/black_litterman_views_panel_test.cljs`. Playwright optimizer coverage lives in `tools/playwright/test/portfolio-regressions.spec.mjs` and `tools/playwright/test/optimizer-black-litterman-views.spec.mjs`.

## Plan of Work

First, create `frontier_chart_model.cljs` for chart constants, domain scaling, point positioning, path generation, objective target derivation, point actions, frontier-point selection, and the derived chart model. Create `frontier_chart_toolbar.cljs` for the constrain-frontier checkbox and overlay mode segmented control. Create `frontier_chart_layers.cljs` for SVG grid, axis labels, frontier points, callout CSS, and the SVG assembly. Keep `frontier_chart.cljs` as the small public component facade.

Second, create `frontier_overlay_model.cljs` for overlay modes, normalization, visible/all point filtering, copy strings, overlay labels, vault detection, and market identity derivation. Create `frontier_vault_markers.cljs` for lucide conversion, vault short-code derivation, vault marker layout, and inline vault icon rendering. Keep `frontier_overlay_markers.cljs` focused on rendering standalone/contribution markers and callouts, with delegating public aliases for any existing public helpers.

Third, create `results_model.cljs` for vault-safe label enrichment and user-facing instrument labels. Create `results_summary.cljs` for reusable summary cards, compact facts, panel shell, stale result banner, and run-assumption labels. Create `results_diagnostics_rail.cljs` for warning rows, conditioning/sensitivity rows, status tokens, and the right diagnostics rail. Create `results_rebalance_preview.cljs` for the rebalance preview panel. Keep `results_panel.cljs` as the layout facade.

Fourth, move Black-Litterman editor draft derivation helpers into `black_litterman_views_model.cljs` where they can sit beside existing display helpers. Create `black_litterman_views_controls.cljs` for segmented buttons, option groups, instrument selectors, text inputs, and notes input. Create `black_litterman_active_views.cljs` for active view cards and clear confirmation. Keep `black_litterman_views_panel.cljs` as the section facade.

Each extraction should be mechanical: move code, update requires, and keep generated Hiccup identical unless a private unused helper is intentionally retired. Do not add new actions, runtime effects, CSS, or user-facing copy beyond what already exists.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/9b4c/hyperopen`.

1. Added the new view namespaces listed above and updated the four public facade files.
2. Ran full `npm test` after restoring missing dependencies with `npm ci`; the suite passed.
3. Ran `npm run lint:namespace-sizes`; it passed with no optimizer namespace-size failure.
4. Ran required gates for code changes: `npm run check`, `npm test`, and `npm run test:websocket`; all passed.
5. Ran focused optimizer Playwright coverage against a temporary static SPA server after building CSS; the relevant Black-Litterman and recommendation-chart regression tests passed. The formal Browser Inspection design-review run is recorded as blocked by the unrelated port-8080 listener.

## Validation and Acceptance

Acceptance requires all four public view entry points to continue returning the same visible optimizer UI structure for solved results and Black-Litterman editor states. Existing tests should still find stale-result, frontier SVG, target exposure, overlay mode, vault marker, diagnostics rail, rebalance preview, Black-Litterman editor, pending view, active view, and clear-confirmation roles.

Run and record:

- `npm run lint:namespace-sizes` passed: `Namespace size check passed.`
- `npm run lint:hiccup` passed: no space-separated class strings and no string keys in literal style maps.
- `npm run lint:namespace-boundaries` passed.
- `npm test` passed: `Ran 3872 tests containing 21364 assertions. 0 failures, 0 errors.`
- `npm run test:websocket` passed: `Ran 524 tests containing 3043 assertions. 0 failures, 0 errors.`
- `npm run check` passed, including docs, namespace checks, release/style tests, and app/portfolio/worker/test compiles.
- Focused optimizer Playwright passed after `npm run css:build` using `PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080` and a temporary static server: 2 tests passed for the Black-Litterman Edit Views contract and recommendation chart overlays.
- Formal Browser Inspection design-review: `BLOCKED` by an unrelated Hyperopen worktree already listening on `127.0.0.1:8080`; no unrelated process was killed.

Also record final `wc -l` for every modified source namespace. The target files must be comfortably below 500 lines, and new files must also stay below 500 lines.

## Idempotence and Recovery

The extraction is source-only and safe to retry. If a compile error identifies a missing helper or circular dependency, move the helper toward the model namespace for pure functions or toward the layer/control namespace for Hiccup render helpers. If a Hiccup contract test fails because a `data-role`, action vector, or class collection changed, restore the old value unless the direct maintainer request explicitly requires a new selector. If browser sessions are started, clean them with `npm run browser:cleanup` before signoff.

## Artifacts and Notes

Initial line counts:

    488 src/hyperopen/views/portfolio/optimize/frontier_chart.cljs
    486 src/hyperopen/views/portfolio/optimize/frontier_overlay_markers.cljs
    480 src/hyperopen/views/portfolio/optimize/results_panel.cljs
    417 src/hyperopen/views/portfolio/optimize/black_litterman_views_panel.cljs

Evidence snippets:

    src/hyperopen/views/portfolio/optimize/frontier_chart.cljs:300 begins `frontier-chart` after chart constants, geometry, callout, point, and toolbar helpers.
    src/hyperopen/views/portfolio/optimize/results_panel.cljs:124 begins `diagnostics-panel` inside the same file that also enriches labels and renders the final result grid.

Final source line counts:

     35 src/hyperopen/views/portfolio/optimize/frontier_chart.cljs
    163 src/hyperopen/views/portfolio/optimize/frontier_chart_model.cljs
    261 src/hyperopen/views/portfolio/optimize/frontier_chart_layers.cljs
     72 src/hyperopen/views/portfolio/optimize/frontier_chart_toolbar.cljs
    252 src/hyperopen/views/portfolio/optimize/frontier_overlay_markers.cljs
     97 src/hyperopen/views/portfolio/optimize/frontier_overlay_model.cljs
    158 src/hyperopen/views/portfolio/optimize/frontier_vault_markers.cljs
     39 src/hyperopen/views/portfolio/optimize/results_panel.cljs
     57 src/hyperopen/views/portfolio/optimize/results_model.cljs
     84 src/hyperopen/views/portfolio/optimize/results_summary.cljs
    170 src/hyperopen/views/portfolio/optimize/results_diagnostics_rail.cljs
     84 src/hyperopen/views/portfolio/optimize/results_rebalance_preview.cljs
    119 src/hyperopen/views/portfolio/optimize/black_litterman_views_panel.cljs
    204 src/hyperopen/views/portfolio/optimize/black_litterman_views_model.cljs
    127 src/hyperopen/views/portfolio/optimize/black_litterman_views_controls.cljs
     75 src/hyperopen/views/portfolio/optimize/black_litterman_active_views.cljs

## Interfaces and Dependencies

Preserve these public functions and arities:

- `hyperopen.views.portfolio.optimize.frontier-chart/frontier-chart` with arities `[draft result]`, `[draft result overlay-mode]`, and `[draft result overlay-mode constrain-frontier?]`.
- `hyperopen.views.portfolio.optimize.frontier-overlay-markers/marker` and `/callout`, each accepting the existing opts map.
- `hyperopen.views.portfolio.optimize.results-panel/results-panel` with arities `[last-successful-run]`, `[last-successful-run draft]`, and `[last-successful-run draft opts]`.
- `hyperopen.views.portfolio.optimize.black-litterman-views-panel/black-litterman-views-panel` with arities `[draft readiness]` and `[draft readiness editor-state]`.

New helper namespaces may expose small functions used by sibling view namespaces, but they should not introduce new application state paths, action types, or side effects.

## Revision Notes

- 2026-05-13T17:10Z: Created the active plan from the direct maintainer request and initial source/test audit so implementation can proceed under the repo planning contract.
- 2026-05-13T18:09Z: Completed implementation, validation, focused Playwright verification, and moved the plan from active to completed.
