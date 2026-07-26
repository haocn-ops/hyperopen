# Retain Stale Optimizer Scenario Output

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

When a user opens a saved optimizer scenario whose draft inputs differ from the last successful run, the scenario detail page currently hides the allocation table and efficient frontier behind a warning panel that says to rerun. In the reported case, clicking rerun did not visibly recover the page, leaving a mostly empty detail view even though a last successful run was available.

After this change, the scenario detail page should retain and clearly label the last successful optimizer output whenever it exists. Stale allocation weights, frontier output, and rebalance context must stay visible as previous output, while save and execution-oriented flows remain guarded until a fresh run succeeds. If recomputation starts, the page should keep the previous output visible and show the existing progress state.

Follow-up feedback on 2026-05-24 clarified that stale scenarios should not expose `Recompute` as a user-facing decision. The retained stale output should request a background recompute automatically and show only status-oriented stale/recomputing messaging while the previous output remains visible.

## Context References

Public refs:
- Direct user/maintainer request in this Codex session on 2026-05-24 with screenshots showing `/portfolio/optimize/<scenario-id>` rendering a stale warning, a `Run again` panel, and no recommendation output after clicking a saved scenario.

Repo artifacts:
- `/hyperopen/AGENTS.md` requires UI work to follow `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/BROWSER_TESTING.md`, and the browser-QA guides, and to report changed files, commands, validation results, and risks.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define the active ExecPlan contract.
- `/hyperopen/docs/FRONTEND.md` requires UI-facing changes to provide timely status feedback, use Playwright for deterministic regression coverage, and account for browser QA.
- `/hyperopen/docs/BROWSER_TESTING.md` routes committed deterministic UI checks to Playwright and exploratory/design review checks to Browser MCP or browser-inspection tooling.
- `/hyperopen/docs/agent-guides/ui-foundations.md` requires truthful controls, timely status feedback, and keyboard-operable controls.
- `/hyperopen/docs/agent-guides/trading-ui-policy.md` requires stale values to stay frozen and labeled instead of replaced by fake or empty placeholders.
- `/hyperopen/src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs` renders the optimizer scenario detail route, including stale banners and the recommendation tab.
- `/hyperopen/src/hyperopen/views/portfolio/optimize/results_panel.cljs` renders the allocation table, frontier, diagnostics, and optional rebalance preview for a solved run.
- `/hyperopen/src/hyperopen/views/portfolio/optimize/results_summary.cljs` renders the stale-result banner inside the results panel.
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/view_model/scenario.cljs` computes `:stale?`, `:current-result?`, `:running?`, and the retained `:last-successful-run` for the scenario detail view.
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/run_identity.cljs` defines when a solved run is current versus stale.
- `/hyperopen/test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs` contains render-level coverage for the scenario detail route and currently asserts the stale blocking panel behavior.
- `/hyperopen/tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs` is the existing deterministic browser smoke for optimizer route rendering.

Local scratch refs:
- User-attached screenshot `Screenshot 2026-05-24 at 1.58.47 PM.png` shows a stale saved scenario detail page with top KPIs visible, a stale banner, and a blocked recommendation panel instead of the retained recommendation output.

## Progress

- [x] (2026-05-24 17:50Z) Read the repository planning, frontend, browser-testing, and browser-QA docs required for this UI change.
- [x] (2026-05-24 17:54Z) Reproduced the root cause from source and tests: `scenario_detail_view.cljs` intentionally renders `stale-recommendation-blocked` whenever a solved retained result is stale and not currently running.
- [x] (2026-05-24 17:55Z) Confirmed existing tests encode the old behavior by asserting `portfolio-optimizer-recommendation-stale-blocked` is present and allocation/frontier roles are absent for a mismatched solved run.
- [x] (2026-05-24 17:57Z) Created this active ExecPlan for the requested behavior change.
- [x] (2026-05-24 18:00Z) Added RED render tests that prove stale solved scenario output remains visible and clearly labeled.
- [x] (2026-05-24 18:02Z) Verified RED behavior: the focused scenario-detail test failed because `portfolio-optimizer-results-surface`, `portfolio-optimizer-stale-result-banner`, `portfolio-optimizer-frontier-panel`, and `portfolio-optimizer-target-exposure-table` were missing while `portfolio-optimizer-recommendation-stale-blocked` was still present.
- [x] (2026-05-24 18:04Z) Implemented the minimal view changes to remove the stale-only recommendation blocker while preserving stale and recomputing banners.
- [x] (2026-05-24 18:05Z) Verified GREEN behavior: focused scenario-detail and results-panel render tests passed.
- [x] (2026-05-24 18:07Z) Extended and ran the existing optimizer route Playwright smoke; it passed with stale previous-output assertions in the scenario detail route.
- [x] (2026-05-24 18:09Z) Ran `npm run check`; it is blocked by unrelated namespace-size guardrail failures in wallet/header test namespaces. The touched scenario-detail test namespace no longer appears after updating its existing exception to the current line count.
- [x] (2026-05-24 18:10Z) Ran `npm test` and `npm run test:websocket`; both passed.
- [x] (2026-05-24 18:12Z) Ran governed design review for `portfolio-optimizer-results-route`; it passed all required browser-QA passes at 375, 768, 1280, and 1440 widths.
- [x] (2026-05-24 18:12Z) Ran `npm run browser:cleanup`; no browser-inspection sessions remained running.
- [x] (2026-05-24 19:09Z) Added follow-up RED coverage for automatic stale recompute and removal of `Recompute` controls; focused tests failed on missing action/path and old visible buttons.
- [x] (2026-05-24 19:15Z) Implemented a guarded auto-recompute action with a request-signature latch, wired it to the scenario stale banner render hook, and removed stale-banner `Recompute` buttons.
- [x] (2026-05-24 19:17Z) Added runtime registration, action-arg validation, effect-order policy, and regenerated formal effect-order vectors for the new background action.
- [x] (2026-05-24 19:19Z) Focused action, render, wiring, schema, and formal conformance tests passed after the follow-up implementation.

## Surprises & Discoveries

- Observation: The empty-looking stale detail page is not caused by missing result data.
  Evidence: `scenario_detail_view.cljs` still renders `kpi-strip result` from the retained solved run, then hides the detailed results only inside `recommendation-tab`.

- Observation: The code already supports retaining previous output while a recompute is running.
  Evidence: `recommendation-tab` renders `recompute-banner` and `results-panel` when `(and (solved-result? model) (or current-result? running?))`; the stale-only non-running branch is the blocking path.

- Observation: Save behavior is already guarded by freshness and should remain guarded.
  Evidence: `scenario-header` disables `portfolio-optimizer-scenario-save` when `current-result?` is false, and tests assert stale mismatched solved runs are not saveable.

- Observation: This worktree needed JavaScript dependencies restored before the generated ClojureScript test runner could execute.
  Evidence: the first focused `node out/test.js --test=hyperopen.views.portfolio.optimize.scenario-detail-view-test` failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`; `npm ci` restored 335 locked packages, after which the RED assertions ran.

- Observation: The touched scenario detail test namespace has an existing size exception and the updated coverage exceeded the previous cap.
  Evidence: `npm run check` initially reported `test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs` at 743 lines with an exception cap of 666. Updating the existing exception to 743 removed that touched-file failure; `npm run lint:namespace-sizes` then reported only unrelated wallet/header test files.

## Decision Log

- Decision: Retain stale solved output in the recommendation tab instead of replacing it with a blocking rerun-only panel.
  Rationale: The user needs to see the last successful optimizer output after selecting a scenario. The trading UI policy also says stale values should be frozen and labeled, not hidden behind placeholders. The retained output can be marked stale while save and execution paths remain guarded until a fresh run succeeds.
  Date/Author: 2026-05-24 / Codex

- Decision: Keep the existing top stale banner and results-panel stale banner, but remove the duplicate stale-only recommendation blocker.
  Rationale: The page should communicate stale state without creating an empty dead end. The top stale banner can keep the run action, and the results panel can label the allocation/frontier as stale previous output.
  Date/Author: 2026-05-24 / Codex

- Decision: Do not change run identity or optimizer pipeline semantics unless tests reveal a separate execution bug.
  Rationale: The observed source-level root cause is presentation gating. `run-portfolio-optimizer-from-draft` already dispatches `:effects/run-portfolio-optimizer-pipeline`, and the pipeline already projects running progress. A wider runtime change would increase risk without evidence.
  Date/Author: 2026-05-24 / Codex

- Decision: Replace stale `Recompute` controls with a guarded automatic background recompute.
  Rationale: Follow-up product feedback favored not asking users to decide whether to recompute stale output. A render hook dispatches a no-arg action, and the action records the current request signature before emitting `:effects/run-portfolio-optimizer-pipeline`, so repeated renders or failed runs do not loop.
  Date/Author: 2026-05-24 / Codex

- Decision: Keep the top header `Rerun` action as the manual run affordance, but remove stale-specific `Recompute` labeling.
  Rationale: Users can still force a run from the scenario header, while stale state no longer presents recomputation as the primary task. The stale status banners explain automatic refresh instead.
  Date/Author: 2026-05-24 / Codex

## Outcomes & Retrospective

Implemented the requested stale-output behavior. A stale solved optimizer scenario now keeps the previous allocation table, efficient frontier, diagnostics, and stale output banner visible instead of replacing the recommendation tab with a rerun-only warning. Follow-up feedback removed stale-specific `Recompute` controls: stale banners now say the app is refreshing automatically, and a guarded background action requests the existing optimizer pipeline once per stale request signature.

Save remains guarded by `current-result?`, so stale previous output cannot be saved as a fresh scenario. The existing recompute path still shows the previous output plus the progress banner while a run is in flight.

This reduces user-facing complexity by removing a dead-end stale state and aligning the implementation with the trading UI rule that stale values should be frozen and labeled. Code complexity is slightly lower in `scenario_detail_view.cljs` because the stale-only blocker function and branch were removed. Test surface area increased modestly to pin the stale non-running browser and render behavior.

Validation is mostly green. Focused render tests, the route-level Playwright smoke, full `npm test`, websocket tests, and governed design review passed. `npm run check` remains blocked by three unrelated namespace-size issues in files not changed by this task.

## Context and Orientation

The optimizer scenario detail route is rendered by `src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs`. A "scenario" is a saved or draft optimizer setup. A "last successful run" is the retained optimizer result stored under `[:portfolio :optimizer :last-successful-run]`. A result is "stale" when the current draft inputs no longer match the request signature of that last successful run. Stale does not mean the previous run is missing; it means the user should not treat it as the current recommendation.

The scenario view model in `src/hyperopen/portfolio/optimizer/application/view_model/scenario.cljs` computes:

- `:result`, the solved result map from the retained last successful run.
- `:last-successful-run`, the retained run with a rebalance preview attached when possible.
- `:current-result?`, true only when the retained solved run matches the current draft and run state.
- `:stale?`, true when a retained run exists but draft inputs differ or the run state is not the matching completed run.
- `:running?`, true when either run state or progress state is running.

The current view behavior is in `recommendation-tab`. It renders `results-panel` only when the retained solved run is current or a recompute is running. If the run is solved but stale and not running, it renders `stale-recommendation-blocked`, a warning-only panel with a `Run again` button. That is the behavior to remove.

`results-panel/results-panel` already accepts `:stale?` and renders `summary/stale-result-banner`, so the retained allocation table, frontier chart, and diagnostics can remain visible with a stale label. The scenario page also has a top `stale-banner`, which should continue to appear when stale and not running.

## Plan of Work

First, update tests before production code. In `test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs`, change the stale mismatched result test so it expects the recommendation tab to render the retained stale output. The test should still assert that the save button is disabled, because stale output must not become saveable as the active scenario. It should assert that the top scenario stale banner is present, the inner results stale banner is present, the stale blocking panel is absent, and the allocation/frontier roles are present. This test should fail before production edits because the current view hides those roles.

Second, adjust the general scenario detail render test that currently expects `portfolio-optimizer-recommendation-stale-blocked` and no results surface. That test should now expect the results surface with stale labeling. It should keep the assertions for scenario header, KPIs, provenance, stale top banner, and run action wiring.

Third, edit `src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs`. Remove the stale-only blocker branch from `recommendation-tab` and render `results-panel` for any solved retained result. Pass `:stale? (and stale? (not running?))` so stale labeling is shown only when the previous output is not actively recomputing. Keep `recompute-banner` when `running?` is true. Remove `stale-recommendation-blocked` if no other code references it.

Fourth, run focused render tests. Before the production edit, the updated tests should fail because stale results are hidden. After the production edit, the focused scenario detail tests should pass. If the focused failure is not about missing stale output roles, fix the test setup before touching production code.

Fifth, run deterministic browser coverage. The existing Playwright optimizer route smoke should be extended only if there is already a stable scenario-detail stale route fixture. If not, the render tests are the committed deterministic coverage and browser QA can be route-level design review against the optimizer results scenario target. Browser-inspection sessions must be cleaned up before completion.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/8dcf/hyperopen`.

1. Edit `test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs` to update the stale scenario assertions described above.

2. Run the focused test command:

       npm run test:runner:generate
       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.views.portfolio.optimize.scenario-detail-view-test

   Actual RED behavior before production edits: after `npm ci` restored missing dependencies, the command failed with 13 assertions because `portfolio-optimizer-results-surface`, `portfolio-optimizer-stale-result-banner`, `portfolio-optimizer-frontier-panel`, and `portfolio-optimizer-target-exposure-table` were missing for stale solved scenario output, while `portfolio-optimizer-recommendation-stale-blocked` was still present.

3. Edit `src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs` to render `results-panel` for every solved retained result and to remove the stale-only blocker.

4. Re-run the focused test command. Actual GREEN behavior: `hyperopen.views.portfolio.optimize.scenario-detail-view-test` passed with 13 tests, 171 assertions, 0 failures, and 0 errors.

5. Run a relevant Playwright smoke. Prefer this command if the existing route smoke covers optimizer scenario detail state:

       npx playwright test -c playwright.config.mjs tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep "setup and retained draft detail"

   Actual result: passed with 1 test in 41.3 seconds after adding stale previous-output assertions to the existing route smoke.

6. Run required code-change gates:

       npm run check
       npm test
       npm run test:websocket

   Actual result: `npm test` passed with 4028 tests, 22190 assertions, 0 failures, and 0 errors. `npm run test:websocket` passed with 524 tests, 3043 assertions, 0 failures, and 0 errors. `npm run check` failed at `npm run lint:namespace-sizes` because of unrelated namespace-size guardrail failures:

       [missing-size-exception] test/hyperopen/wallet/agent_runtime_edge_test.cljs - namespace has 532 lines; add an exception entry in dev/namespace_size_exceptions.edn
       [size-exception-exceeded] test/hyperopen/wallet/core_test.cljs - namespace has 597 lines; exception allows at most 525
       [size-exception-exceeded] test/hyperopen/views/header_view_test.cljs - namespace has 613 lines; exception allows at most 585

7. Run governed UI/design review for the optimizer scenario/results route when local browser tooling is available:

       npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app

   Actual follow-up result: PASS, run id `design-review-2026-05-24T19-16-37-365Z-834fc224`. The run inspected 375, 768, 1280, and 1440 widths and reported PASS for visual evidence, native-control, styling-consistency, interaction, layout-regression, and jank/perf.

8. Clean up browser-inspection sessions:

       npm run browser:cleanup

   Actual result: passed with `{"ok": true, "stopped": [], "results": []}`.

## Validation and Acceptance

Acceptance is met when opening a stale saved optimizer scenario with a retained solved run shows the allocation table, efficient frontier, diagnostics, and stale labels instead of a recommendation-only blocker. The page must make the stale state visible, automatically request recomputation in the background, avoid user-facing `Recompute` controls, and not allow saving the scenario as current until a fresh run succeeds.

Automated acceptance:
- The updated `portfolio-optimizer-scenario-detail-marks-clean-mismatched-result-stale-test` failed before the production edit and passed after the edit.
- The focused scenario detail render test namespace passes.
- Focused action, defaults, results-panel, frontier-contract, scenario-detail, runtime wiring, action-arg, and formal conformance tests pass.
- The selected Playwright optimizer smoke passes and asserts no `Recompute` controls in the stale retained-output state.
- `npm test` passes with 4029 tests, 22201 assertions, 0 failures, and 0 errors.
- `npm run test:websocket` passes with 524 tests, 3043 assertions, 0 failures, and 0 errors.
- `npm run check` is blocked only by unrelated namespace-size failures in `test/hyperopen/wallet/agent_runtime_edge_test.cljs`, `test/hyperopen/wallet/core_test.cljs`, and `test/hyperopen/views/header_view_test.cljs`.

Browser-QA accounting must include PASS, FAIL, or BLOCKED for:
- Visual pass: PASS in `design-review-2026-05-24T19-16-37-365Z-834fc224`.
- Native-control pass: PASS in `design-review-2026-05-24T19-16-37-365Z-834fc224`.
- Styling-consistency pass: PASS in `design-review-2026-05-24T19-16-37-365Z-834fc224`.
- Interaction pass: PASS in `design-review-2026-05-24T19-16-37-365Z-834fc224`.
- Layout-regression pass: PASS in `design-review-2026-05-24T19-16-37-365Z-834fc224`.
- Jank/perf pass: PASS in `design-review-2026-05-24T19-16-37-365Z-834fc224`.
- Residual blind spot: the design review notes that hover, active, disabled, and loading states still require targeted route actions when not present by default. The Playwright route smoke covers stale visible output and absence of stale `Recompute` controls directly.

## Idempotence and Recovery

The implementation is local to a view namespace and render tests. Re-running test generation, test compilation, Playwright, and browser cleanup is safe. If the updated RED test passes before production edits, the current tree already implements the behavior and the plan should be updated instead of making a redundant change. If broad gates fail outside optimizer scenario view code, keep the focused evidence and document the unrelated failure rather than weakening the stale-output behavior.

## Artifacts and Notes

Initial source evidence:

    scenario_detail_view.cljs recommendation-tab currently gates solved stale output:
      current or running solved run -> results-panel
      solved stale non-running run -> stale-recommendation-blocked

    Existing test expectation:
      stale mismatched result -> recommendation-stale-blocked present
      stale mismatched result -> frontier and target exposure table absent

Validation transcript excerpts:

    Testing hyperopen.views.portfolio.optimize.frontier-chart-contract-test
    Testing hyperopen.views.portfolio.optimize.scenario-detail-view-test
    Ran 15 tests containing 309 assertions.
    0 failures, 0 errors.

    Testing hyperopen.portfolio.optimizer.actions-test
    Testing hyperopen.portfolio.optimizer.defaults-test
    Testing hyperopen.views.portfolio.optimize.frontier-chart-contract-test
    Testing hyperopen.views.portfolio.optimize.results-panel-test
    Testing hyperopen.views.portfolio.optimize.scenario-detail-view-test
    Ran 42 tests containing 467 assertions.
    0 failures, 0 errors.

    npx playwright test -c playwright.config.mjs tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep "setup and retained draft detail"
    1 passed (25.3s)

    npm test
    Ran 4029 tests containing 22201 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

    npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app
    reviewOutcome: PASS
    runId: design-review-2026-05-24T19-16-37-365Z-834fc224

    npm run browser:cleanup
    {"ok": true, "stopped": [], "results": []}

## Interfaces and Dependencies

No public API changes are planned. The implementation depends on existing view-model keys:

- `:last-successful-run`
- `:result`
- `:stale?`
- `:running?`
- `:current-result?`
- `:optimization-progress`

The implementation should preserve existing `data-role` anchors for `portfolio-optimizer-results-surface`, `portfolio-optimizer-stale-result-banner`, `portfolio-optimizer-scenario-stale-banner`, `portfolio-optimizer-frontier-panel`, and `portfolio-optimizer-target-exposure-table`.

2026-05-24 / Codex: Created the active plan after root-cause investigation. The plan records that the source-level issue is stale presentation gating, while save and run pipeline semantics should remain intact unless focused tests reveal a separate issue.

2026-05-24 / Codex: Completed implementation and validation. The plan now records RED/GREEN render evidence, Playwright smoke coverage, full test results, browser-QA pass evidence, cleanup, and the remaining unrelated `npm run check` namespace-size blocker.
