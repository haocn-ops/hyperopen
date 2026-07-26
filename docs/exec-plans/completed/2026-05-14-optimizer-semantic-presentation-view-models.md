# Move optimizer semantic presentation models out of views

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current while the work proceeds. This document follows `.agents/PLANS.md` from the repository root.

## Purpose / Big Picture

The optimizer results screen currently renders correctly, but several view namespaces still build semantic presentation models before emitting Hiccup. That makes the UI layer harder to maintain because result label enrichment, frontier overlay filtering, and rebalance target grouping live beside class names and DOM actions. After this change, those pure read-model decisions live under `src/hyperopen/portfolio/optimizer/application/view_model/`, and the views under `src/hyperopen/views/portfolio/optimize/` remain renderers that consume prepared data, format cells, and wire actions.

This is an internal architecture refactor with no intended visual or interaction behavior change. A human can see it working by running the ClojureScript tests: the new application view-model tests fail before implementation because the requested namespaces do not exist, then pass after the model code moves. Existing optimizer view tests must also pass, proving the rendered Hiccup contract stayed stable.

## Context References

Public refs:

- Direct user/maintainer request on 2026-05-14: "Highest-Leverage Refactors. Finish moving semantic presentation models out of views. Setup is mostly improved now, but results/frontier/rebalance still do model work inside view namespaces. Examples: results_model.cljs (line 37), frontier_overlay_model.cljs (line 17), and target_exposure_table.cljs (line 203). Refactor into application.view-model.results, application.view-model.frontier, and application.view-model.rebalance; leave views as Hiccup/class/action renderers."

Repo artifacts:

- Root repo contract: `AGENTS.md`.
- Planning contract: `docs/PLANS.md` and `.agents/PLANS.md`.
- UI testing contract: `docs/BROWSER_TESTING.md` and `docs/FRONTEND.md`.
- Prior optimizer view-model split: `docs/exec-plans/completed/2026-05-13-optimizer-view-model-facade-split.md`.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-14 01:41Z) Read the root instructions, planning contract, UI testing contract, prior optimizer view-model split, and the three called-out source files.
- [x] (2026-05-14 01:41Z) Created this active ExecPlan from the direct maintainer request.
- [x] (2026-05-14 01:44Z) Added RED tests requiring `hyperopen.portfolio.optimizer.application.view-model.results`, `frontier`, and `rebalance`.
- [x] (2026-05-14 01:45Z) Verified RED with `npm test`; compilation failed because `hyperopen.portfolio.optimizer.application.view-model.frontier` was unavailable.
- [x] (2026-05-14 01:52Z) Moved pure results, frontier overlay, and rebalance target-exposure model code into application view-model namespaces.
- [x] (2026-05-14 01:52Z) Updated optimizer views to consume the application view-model namespaces and removed the old view-local model namespaces.
- [x] (2026-05-14 01:57Z) Restored JS dependencies with `npm ci` after the first GREEN compile could not load Lucide from missing `node_modules`.
- [x] (2026-05-14 02:03Z) Ran `npm test`; it passed with 3898 tests and 21483 assertions after correcting the new results test to match the existing non-vault label contract.
- [x] (2026-05-14 02:05Z) Ran `npm run check`; it passed all repo checks and Shadow builds with 0 warnings.
- [x] (2026-05-14 02:08Z) Ran standalone required gates `npm test` and `npm run test:websocket`; both passed.
- [x] (2026-05-14 02:13Z) Re-ran governed Browser QA after restoring this worktree's dependencies; `npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app` passed for `portfolio-optimizer-results-route` across `review-375`, `review-768`, `review-1280`, and `review-1440`.
- [x] (2026-05-14 02:13Z) Ran `npm run browser:cleanup` after Browser QA; no tracked browser-inspection sessions remained.
- [x] (2026-05-14 02:14Z) Closed the stale active-plan signal by updating Browser QA accounting from blocked to passed and preparing this ExecPlan for `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The current application view-model facade already uses focused files under `src/hyperopen/portfolio/optimizer/application/view_model/`.
  Evidence: `src/hyperopen/portfolio/optimizer/application/view_model.cljs` delegates to child namespaces such as `view-model.workspace`, `view-model.scenario`, `view-model.universe`, and `view-model.execution`.
- Observation: The called-out frontier overlay model only needs pure string and numeric helpers plus optimizer IDs.
  Evidence: `src/hyperopen/views/portfolio/optimize/frontier_overlay_model.cljs` imports `clojure.string`, optimizer `coercion`, optimizer `ids`, and view formatting only for `finite-number?`, which can be replaced with `coercion/finite-number?` in application code.
- Observation: The called-out target exposure table mixes model derivation and Hiccup rendering in one function.
  Evidence: `target-exposure-table` computes `capital-usd`, `binding-instrument-ids`, row maps, and grouped assets before returning `[:section ...]`.
- Observation: The new application view-model tests fail before implementation for the intended boundary reason.
  Evidence: `npm test` generated `test/test_runner_generated.cljs` with 642 namespaces, then Shadow CLJS stopped with `The required namespace "hyperopen.portfolio.optimizer.application.view-model.frontier" is not available`.
- Observation: The first implementation compile succeeded, but the local JS dependency tree was absent.
  Evidence: `npm test` built `[:test]` with 0 warnings, then Node failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`. Running `npm ci` installed 335 packages.
- Observation: `instrument-label` intentionally returns raw IDs for non-vault instruments.
  Evidence: the new results test initially expected `"BTC"` for `"perp:BTC"`, but the existing implementation and renderer contract return `"perp:BTC"` for non-vault IDs. The test was corrected to keep the assertion focused on vault label enrichment.
- Observation: Governed browser QA selected the correct optimizer results target but could not launch a managed app for this worktree.
  Evidence: dry run matched `portfolio-optimizer`; the real run for `portfolio-optimizer-results-route` failed because Shadow CLJS reported `shadow-cljs already running in project on http://localhost:9632`. A process check showed the active Shadow server belonged to another worktree, not this `def8` worktree, so it was not terminated.
- Observation: The stale Browser QA blocker was environment-specific, not a product or test failure.
  Evidence: On 2026-05-14 02:11Z in this `9651` worktree, `lsof -nP -iTCP:9632 -sTCP:LISTEN` returned no process. The first cleanup attempt failed only because this worktree lacked installed browser-inspection dependencies: Node could not import `pixelmatch`.
- Observation: Restoring dependencies made browser-inspection commands available in this worktree.
  Evidence: `npm ci` installed 335 packages. A follow-up `npm run browser:cleanup` exited 0 with `{"ok": true, "stopped": [], "results": []}`.
- Observation: The governed optimizer results Browser QA target now passes.
  Evidence: `npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app` produced `runStatus: "completed"`, `reviewOutcome: "PASS"`, and `state: "PASS"` for `/portfolio/optimize/qa-frontier` at `review-375`, `review-768`, `review-1280`, and `review-1440`. Each viewport reported PASS for visual evidence, native controls, styling consistency, interaction, layout regression, and jank/perf.

## Decision Log

- Decision: Create `results.cljs`, `frontier.cljs`, and `rebalance.cljs` under `src/hyperopen/portfolio/optimizer/application/view_model/`.
  Rationale: This matches the existing ClojureScript hyphen/underscore convention for the requested `application.view-model.*` namespaces and keeps all pure optimizer view-model work in one application subtree.
  Date/Author: 2026-05-14 / Codex.
- Decision: Keep formatting functions in view namespaces while moving semantic derivation to application namespaces.
  Rationale: Formatting such as percent and USDC strings is tied to presentation, while model derivation such as row grouping, label fallback, overlay filtering, and market identity belongs in the application view-model layer. This avoids application namespaces importing `hyperopen.views.*`.
  Date/Author: 2026-05-14 / Codex.
- Decision: Delete `hyperopen.views.portfolio.optimize.results-model` and `hyperopen.views.portfolio.optimize.frontier-overlay-model` instead of leaving delegation wrappers.
  Rationale: The maintainer asked to finish moving semantic presentation models out of views. Keeping view-local wrapper namespaces would leave model-shaped namespaces under `hyperopen.views.*` even if they delegated.
  Date/Author: 2026-05-14 / Codex.
- Decision: Leave `target-exposure-table.cljs` in the view layer, but make it consume `target-exposure-table-model`.
  Rationale: The namespace still owns Hiccup, classes, data-role tokens, cell formatting, and asset-icon rendering, while the application view-model owns grouping, binding flags, signs, notionals, leg labels, and market identity.
  Date/Author: 2026-05-14 / Codex.
- Decision: Treat the earlier Browser QA block as superseded by the fresh 2026-05-14 governed design-review pass and move this plan to completed.
  Rationale: Active ExecPlans are for work being executed now. Leaving a stale unchecked blocker would mislead future agents even though the extraction, repository gates, and governed Browser QA have all completed.
  Date/Author: 2026-05-14 / Codex.

## Outcomes & Retrospective

Code implementation is complete. The view layer no longer contains the called-out results and frontier model namespaces, and the target exposure table now renders a prepared rebalance model instead of deriving rows inline. Overall complexity decreased because result label enrichment, frontier overlay filtering, and rebalance grouping are now independently testable pure application view-models, while the remaining view files focus on Hiccup, classes, formatting, data-role tokens, and action vectors.

Browser QA is now complete. The earlier managed-app startup block was superseded on 2026-05-14 by a fresh governed design review of `portfolio-optimizer-results-route`; it passed every configured pass across all checked-in review viewports. The only residual blind spot reported by the tool is the standard state-sampling note that hover, active, disabled, and loading states require targeted route actions when they are not present by default. No browser-inspection sessions remained after cleanup.

## Context and Orientation

The optimizer UI lives under `src/hyperopen/views/portfolio/optimize/`. In this repository, "Hiccup" means ClojureScript vectors such as `[:div {:class [...]} "text"]` that describe DOM nodes. A view renderer should assemble those vectors, CSS class collections, and action vectors such as `[[:actions/run-portfolio-optimizer-from-draft]]`. A view-model namespace should accept plain maps and return plain maps or vectors of data that a renderer can consume without recomputing semantic state.

Three view-local model files are in scope. `src/hyperopen/views/portfolio/optimize/results_model.cljs` enriches result labels, especially vault labels, before the results panel and diagnostics render them. `src/hyperopen/views/portfolio/optimize/frontier_overlay_model.cljs` normalizes overlay mode, filters valid overlay points, returns overlay copy, detects vault points, and builds market identity maps for asset icons. `src/hyperopen/views/portfolio/optimize/target_exposure_table.cljs` computes target exposure rows and groups before rendering the allocation table.

The new application view-model files should be:

- `src/hyperopen/portfolio/optimizer/application/view_model/results.cljs`, responsible for `instrument-label` and `enrich-result-labels`.
- `src/hyperopen/portfolio/optimizer/application/view_model/frontier.cljs`, responsible for overlay modes, mode normalization, overlay point filtering, overlay copy, overlay labels, vault point detection, and point market identity.
- `src/hyperopen/portfolio/optimizer/application/view_model/rebalance.cljs`, responsible for target exposure table data: asset grouping, per-leg rows, binding flags, signed row labels, target/current notionals, and icon market data.

## Plan of Work

First, add application-level tests that import the requested namespaces. The results tests should prove vault raw addresses are replaced with draft labels through `enrich-result-labels`. The frontier tests should prove invalid overlay points are filtered and mode copy stays stable. The rebalance tests should prove `target-exposure-table-model` groups spot and perp legs by asset, marks binding rows, derives signed labels, and provides market data for asset icons without rendering Hiccup.

Second, run `npm test` before implementation. The expected failure is a ClojureScript compile error because at least one of the new `hyperopen.portfolio.optimizer.application.view-model.*` namespaces is unavailable.

Third, move code from the view-local files into the new application namespaces. Do not import any `hyperopen.views.*` namespace from application code. Use `hyperopen.portfolio.optimizer.coercion/finite-number?` for numeric validity and keep cell formatting in the renderers.

Fourth, update views. `results-panel`, `results-diagnostics-rail`, and `results-rebalance-preview` should import `hyperopen.portfolio.optimizer.application.view-model.results`. Frontier chart toolbar, layers, markers, vault marker helpers, and chart model should import `hyperopen.portfolio.optimizer.application.view-model.frontier`. `target-exposure-table` should call `hyperopen.portfolio.optimizer.application.view-model.rebalance/target-exposure-table-model` and render the returned rows and groups.

Fifth, remove the obsolete view-local model namespaces once no source or test requires them. Then run focused tests and the required repository gates.

Sixth, close the process debt after implementation. Re-run the governed Browser QA target if the previous environment blocker is gone, record the result in this plan, clean up browser-inspection sessions, and move this file from `docs/exec-plans/active/` to `docs/exec-plans/completed/` so future agents do not treat the finished extraction as still in flight.

## Concrete Steps

Initial implementation commands ran from `/Users/barry/.codex/worktrees/def8/hyperopen`. The Browser QA closure commands on 2026-05-14 ran from `/Users/barry/.codex/worktrees/9651/hyperopen`.

Add RED tests and run:

    npm test

Expected before implementation: compilation fails because one or more of `hyperopen.portfolio.optimizer.application.view-model.results`, `hyperopen.portfolio.optimizer.application.view-model.frontier`, or `hyperopen.portfolio.optimizer.application.view-model.rebalance` cannot be found.

Observed RED validation:

    npm test
    Generated test/test_runner_generated.cljs with 642 namespaces.
    The required namespace "hyperopen.portfolio.optimizer.application.view-model.frontier" is not available, it was required by "hyperopen/portfolio/optimizer/application/view_model_frontier_test.cljs".

Implement the extraction and run:

    npm test

Expected after implementation: the new application view-model tests and existing optimizer view tests pass with 0 failures and 0 errors.

Observed dependency restoration:

    npm ci
    added 335 packages, and audited 336 packages in 3s

Observed GREEN validation after implementation:

    npm test
    Ran 3898 tests containing 21483 assertions.
    0 failures, 0 errors.

Run required repository gates after code changes:

    npm run check
    npm test
    npm run test:websocket

Expected at completion: each command exits with code 0. Because this refactor touches UI-facing source but does not change markup, CSS, actions, or browser flows, deterministic ClojureScript tests are the smallest relevant browser-adjacent verification. Browser QA passes must be explicitly accounted for in the final notes.

Observed required validation:

    npm run check
    Shadow app, portfolio, portfolio-worker, portfolio-optimizer-worker, vault-detail-worker, and test builds completed with 0 warnings after all repo lint, docs, namespace, release, style, and tooling checks passed.

    npm test
    Ran 3898 tests containing 21483 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

Observed browser QA attempt:

    node tools/browser-inspection/src/cli.mjs design-review --changed-files src/hyperopen/views/portfolio/optimize/results_panel.cljs,src/hyperopen/views/portfolio/optimize/frontier_chart_model.cljs,src/hyperopen/views/portfolio/optimize/target_exposure_table.cljs --dry-run
    matchedRuleIds: ["portfolio-optimizer"]
    targets included: portfolio-optimizer-results-route

    npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app
    BLOCKED: Local app command exited early because Shadow CLJS was already running on http://localhost:9632.

    npm run browser:cleanup
    stopped: ["sess-1778723634310-5e7724"]

Observed Browser QA closure:

    npm ci
    added 335 packages, and audited 336 packages in 3s

    npm run browser:cleanup
    {"ok": true, "stopped": [], "results": []}

    npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app
    runStatus: "completed"
    reviewOutcome: "PASS"
    state: "PASS"
    target: portfolio-optimizer-results-route
    route: /portfolio/optimize/qa-frontier
    viewports: review-375, review-768, review-1280, review-1440
    passes: visual-evidence-captured, native-control, styling-consistency, interaction, layout-regression, jank-perf all PASS with issueCount 0
    artifacts: tmp/browser-inspection/design-review-2026-05-14T02-12-01-847Z-2f571265

    npm run browser:cleanup
    {"ok": true, "stopped": [], "results": []}

## Validation and Acceptance

Acceptance is met when semantic presentation model code no longer lives in the called-out view namespaces, the requested application view-model namespaces exist, and optimizer views continue to render the same Hiccup contracts through existing tests. Existing callers should not need behavior changes. The old view-local model namespaces should be deleted or left unused only if removal would break a documented public API; the default target is deletion.

The new tests must fail before implementation because the application namespaces are missing, then pass after implementation. The full gates required by `AGENTS.md` for code changes are `npm run check`, `npm test`, and `npm run test:websocket`.

Browser QA accounting for `portfolio-optimizer-results-route`:

- Visual pass: PASS on `review-375`, `review-768`, `review-1280`, and `review-1440`.
- Native-control pass: PASS on `review-375`, `review-768`, `review-1280`, and `review-1440`.
- Styling-consistency pass: PASS on `review-375`, `review-768`, `review-1280`, and `review-1440`.
- Interaction pass: PASS on `review-375`, `review-768`, `review-1280`, and `review-1440`; residual blind spot is limited to hover, active, disabled, and loading states not present by default.
- Layout-regression pass: PASS on `review-375`, `review-768`, `review-1280`, and `review-1440`.
- Jank/perf pass: PASS on `review-375`, `review-768`, `review-1280`, and `review-1440`.

## Idempotence and Recovery

This is a pure refactor. Re-running tests is safe. If a compile error appears after moving code, fix requires and namespace names rather than changing runtime state shape. Do not change persisted optimizer state, domain rebalance calculations, frontier math, route query state, CSS, or action semantics in this plan. If a UI view test fails, compare the rendered data-role and text contract first; the intended fix is to preserve the old renderer output while consuming a prepared model.

## Artifacts and Notes

Initial source hotspots:

    src/hyperopen/views/portfolio/optimize/results_model.cljs
      Owns result label enrichment and instrument display fallback.

    src/hyperopen/views/portfolio/optimize/frontier_overlay_model.cljs
      Owns overlay mode normalization, overlay point filtering, copy, label fallback, vault detection, and market identity.

    src/hyperopen/views/portfolio/optimize/target_exposure_table.cljs
      Owns target exposure model derivation inside `target-exposure-table`.

Expected final source layout:

    src/hyperopen/portfolio/optimizer/application/view_model/results.cljs
    src/hyperopen/portfolio/optimizer/application/view_model/frontier.cljs
    src/hyperopen/portfolio/optimizer/application/view_model/rebalance.cljs

Final implementation touched:

    docs/exec-plans/completed/2026-05-14-optimizer-semantic-presentation-view-models.md
    src/hyperopen/portfolio/optimizer/application/view_model/results.cljs
    src/hyperopen/portfolio/optimizer/application/view_model/frontier.cljs
    src/hyperopen/portfolio/optimizer/application/view_model/rebalance.cljs
    src/hyperopen/views/portfolio/optimize/results_model.cljs
    src/hyperopen/views/portfolio/optimize/frontier_overlay_model.cljs
    src/hyperopen/views/portfolio/optimize/results_panel.cljs
    src/hyperopen/views/portfolio/optimize/results_diagnostics_rail.cljs
    src/hyperopen/views/portfolio/optimize/results_rebalance_preview.cljs
    src/hyperopen/views/portfolio/optimize/frontier_chart_model.cljs
    src/hyperopen/views/portfolio/optimize/frontier_chart_toolbar.cljs
    src/hyperopen/views/portfolio/optimize/frontier_overlay_markers.cljs
    src/hyperopen/views/portfolio/optimize/frontier_chart_layers.cljs
    src/hyperopen/views/portfolio/optimize/frontier_vault_markers.cljs
    src/hyperopen/views/portfolio/optimize/target_exposure_table.cljs
    test/hyperopen/portfolio/optimizer/application/view_model_results_test.cljs
    test/hyperopen/portfolio/optimizer/application/view_model_frontier_test.cljs
    test/hyperopen/portfolio/optimizer/application/view_model_rebalance_test.cljs
    test/test_runner_generated.cljs

## Interfaces and Dependencies

`hyperopen.portfolio.optimizer.application.view-model.results` must expose:

    instrument-label [labels-by-instrument instrument-id] -> string
    enrich-result-labels [result draft] -> result map or original value

`hyperopen.portfolio.optimizer.application.view-model.frontier` must expose:

    modes -> [:standalone :contribution :none]
    normalize-mode [overlay-mode] -> keyword mode
    visible-points [result overlay-mode] -> vector
    all-points [result] -> vector
    copy [overlay-mode] -> map of subtitle, axis prefixes, reading text, and optional legend label
    overlay-label [point] -> string
    vault-point? [point] -> boolean
    point-market [point] -> map with :key, :coin, :symbol, :base, and :market-type

`hyperopen.portfolio.optimizer.application.view-model.rebalance` must expose:

    target-exposure-table-model [result] -> map

The rebalance model map should contain `:capital-usd`, `:labels-by-instrument`, `:binding-instrument-ids`, `:groups`, and grouped rows. Each row should include `:idx`, `:asset`, `:instrument-id`, `:current-weight`, `:target-weight`, `:current-notional`, `:target-notional`, `:delta`, `:delta-notional`, `:binding?`, `:current-sign`, `:target-sign`, `:leg-label`, and `:market`.

Plan revision note: 2026-05-14 01:41Z / Codex created the active plan from the maintainer request after inspecting the relevant source, tests, and planning contracts.

Plan revision note: 2026-05-14 02:10Z / Codex updated the plan with implementation details, validation evidence, and the browser-QA block caused by the existing Shadow CLJS port 9632 owner.

Plan revision note: 2026-05-14 02:14Z / Codex restored this worktree's npm dependencies, re-ran governed Browser QA for `portfolio-optimizer-results-route`, recorded the PASS evidence, and moved the plan to completed because no active work remains.
