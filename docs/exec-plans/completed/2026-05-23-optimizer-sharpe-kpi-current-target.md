# Show Current-To-Target Sharpe In Optimizer KPI

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The optimizer scenario page currently shows volatility and expected return as "current -> target" comparisons in the top KPI strip, but Sharpe is shown as one number. That is confusing when the frontier target callout shows a different Sharpe value, because the top card has been displaying the optimizer's 50% shrunk Sharpe while the callout displays raw target Sharpe.

After this change, the top Sharpe card should mirror the volatility and expected return cards. It should show the current portfolio's raw Sharpe, an arrow, and the target portfolio's raw Sharpe. The small line underneath should show the raw Sharpe change, with green when Sharpe improves and yellow when Sharpe deteriorates.

## Context References

Public refs:
- Direct user request in this Codex session on 2026-05-23 after screenshots showed the top Sharpe KPI as `0.566` while the target callout showed `1.133`.

Repo artifacts:
- `/hyperopen/AGENTS.md` requires UI work to follow the optimizer UI and browser-testing guidance and to report validation results.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define the active ExecPlan contract.
- `/hyperopen/docs/FRONTEND.md` requires UI-facing changes to account for browser QA and to keep Hiccup class values as collections.
- `/hyperopen/docs/BROWSER_TESTING.md` routes committed deterministic UI checks to Playwright and exploratory/design review checks to Browser MCP.
- `/hyperopen/src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs` renders the scenario KPI strip.
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/engine/payload.cljs` computes `:performance {:in-sample-sharpe ... :shrunk-sharpe ...}` for target performance and `:current-performance` for current portfolio performance.
- `/hyperopen/src/hyperopen/views/portfolio/optimize/frontier_target.cljs` renders the target callout using `[:performance :in-sample-sharpe]`.
- `/hyperopen/test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs` contains render-level coverage for the scenario KPI strip.

Local scratch refs:
- User-provided screenshots in this session show volatility `79.11% -> 51.59%`, expected return `79.95% -> 58.43%`, top Sharpe `0.566`, and target callout Sharpe `1.133`.

## Progress

- [x] (2026-05-23 12:48Z) Investigated the discrepancy and found that `0.566` is exactly half of the raw target Sharpe `58.43% / 51.59% = 1.133`.
- [x] (2026-05-23 12:48Z) Confirmed the root cause: `scenario_detail_view.cljs` prefers `:shrunk-sharpe`, while `frontier_target.cljs` uses `:in-sample-sharpe`.
- [x] (2026-05-23 12:48Z) Created this active ExecPlan for the requested UI change.
- [x] (2026-05-23 12:51Z) Added RED render tests for Sharpe current-to-target text and positive/negative delta color classes.
- [x] (2026-05-23 12:51Z) Verified RED behavior: focused scenario-detail tests failed because the Sharpe card rendered `Sharpe`, `0.6`, and `optimized run`, and a negative Sharpe delta still used `text-trading-green`.
- [x] (2026-05-23 12:53Z) Implemented current-to-target raw Sharpe rendering in `scenario_detail_view.cljs`.
- [x] (2026-05-23 12:54Z) Added Playwright smoke coverage for the retained optimizer detail route to assert raw current/target Sharpe and absence of the shrunk target value.
- [x] (2026-05-23 12:57Z) Ran focused tests, full tests, websocket tests, focused Playwright, governed design review, and browser cleanup. `npm run check` remains blocked by an unrelated stale document guard documented below.

## Surprises & Discoveries

- Observation: The top card value and target callout value are mathematically consistent but semantically mismatched.
  Evidence: `58.43% / 51.59% = 1.133`; the payload's shrunk value is `1.133 * 0.5 = 0.566`.

- Observation: This fresh worktree needed JavaScript dependencies restored before the ClojureScript test bundle could run.
  Evidence: the first focused test run compiled successfully, then Node failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`. Running `npm ci` restored 335 locked packages, after which the test bundle executed.

- Observation: The new tests caught the intended old behavior before implementation.
  Evidence: `node out/test.js --test=hyperopen.views.portfolio.optimize.scenario-detail-view-test` reported six failures. The Sharpe KPI strings were `#{"Sharpe" "0.6" "optimized run"}`, and the negative Sharpe delta class still contained `text-trading-green`.

- Observation: `npm run check` is blocked before later compile/style stages by an unrelated stale product spec.
  Evidence: `bb -m dev.check-docs` reported `[stale-doc] docs/product-specs/portfolio-page-parity-prd.md - document is stale: 93 days old, max allowed 90`.

## Decision Log

- Decision: The Sharpe KPI should display raw current and raw target Sharpe, not shrunk Sharpe.
  Rationale: The user asked for parity with volatility and expected return, and those cards compare the same metric definition between current and target. The frontier target callout already uses raw target Sharpe, so the top card should use the same raw definition to avoid two unlabeled definitions of "Sharpe" on one screen.
  Date/Author: 2026-05-23 / Codex

- Decision: Keep `:shrunk-sharpe` in the optimizer payload unchanged.
  Rationale: This task is a presentation fix. Removing or changing the payload field could affect other surfaces or future risk-adjusted displays. The UI problem is solved by choosing the correct displayed field and copy.
  Date/Author: 2026-05-23 / Codex

- Decision: Add browser-level coverage to the existing optimizer view-model route smoke instead of creating a new Playwright file.
  Rationale: The existing smoke already seeds and renders the retained optimizer detail route through the browser. Adding assertions there proves the browser surface shows current-to-target raw Sharpe without introducing a separate route setup.
  Date/Author: 2026-05-23 / Codex

## Outcomes & Retrospective

Implemented the requested KPI change. The optimizer scenario Sharpe card now reads `Sharpe · current → target`, displays raw current Sharpe followed by raw target Sharpe, and shows a signed raw Sharpe change underneath. Positive Sharpe changes use `text-trading-green`; negative Sharpe changes use `text-warning`. The target value no longer displays the shrunk Sharpe field as the unlabeled headline number.

The implementation reduces user-facing complexity by removing two conflicting unlabeled definitions of Sharpe from the same screen. Code complexity increases only slightly with three small private helpers local to the view namespace: one for positive numeric volatility checks, one for choosing raw Sharpe from payload-or-return/volatility fallback, and one for signed decimal delta formatting.

Validation is mostly green. Focused render tests, the full ClojureScript suite, websocket tests, the focused Playwright smoke, and governed design review passed. `npm run check` remains blocked by the unrelated stale-doc guard for `/hyperopen/docs/product-specs/portfolio-page-parity-prd.md`.

## Context and Orientation

The optimizer scenario detail route shows a top KPI strip with five cards. The volatility card already reads current value, arrow, target value, and a delta line underneath. The expected return card follows the same pattern. The Sharpe card currently reads one number and the subtext `optimized run`.

The relevant code is in `/hyperopen/src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs`. Function `kpi-strip` receives a solved optimizer result map named `result*`. That result contains:

- `:current-expected-return` and `:current-volatility`, which describe the current portfolio using the optimizer's risk and return model.
- `:expected-return` and `:volatility`, which describe the target portfolio.
- `:current-performance`, which can contain `:in-sample-sharpe`.
- `:performance`, which can contain both `:in-sample-sharpe` and `:shrunk-sharpe`.

The helper `kpi-delta-class` maps positive changes to a caller-provided positive color class and negative changes to a caller-provided negative color class. For Sharpe, positive change is good and should use `text-trading-green`; negative change is a caution state and should use `text-warning`.

## Plan of Work

First, add tests before editing production code. Extend `test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs` so one test renders a result with `:current-performance {:in-sample-sharpe 0.7}` and `:performance {:in-sample-sharpe 1.2 :shrunk-sharpe 0.6}`. The test should assert that the Sharpe KPI contains `0.7`, `1.2`, and `+0.5`, and should assert that it does not rely on `0.6` as the displayed target value. Extend the existing color helper or add a small helper so the test also proves an improving Sharpe delta gets `text-trading-green` and a deteriorating Sharpe delta gets `text-warning`.

Second, update `scenario_detail_view.cljs`. In `kpi-strip`, introduce `current-performance`, `current-sharpe`, `target-sharpe`, and `sharpe-delta`. Use `[:current-performance :in-sample-sharpe]` for current Sharpe. Use `[:performance :in-sample-sharpe]` for target Sharpe, with a fallback to recomputing `target-return / target-vol` if the performance field is missing and the target volatility is positive. Render the card label as `Sharpe · current → target`. Render the value line like the other two cards, showing muted current Sharpe, arrow, and target Sharpe when current Sharpe is available. Render the delta line as a signed decimal change plus a short label such as `raw Sharpe change`.

Third, run focused tests. The new test should fail before production code changes because the current KPI only renders the shrunk target number and `optimized run`. After the implementation, the focused scenario detail test should pass.

Fourth, run broader validation. Because this is a UI-facing code change, run the stable automated checks first. A full governed browser design-review pass may be blocked or disproportionate for this text-only KPI change; if it is not run, record the browser-QA passes as not executed with a clear reason and residual risk. If a local dev app is available, a small browser smoke against the optimizer route can be run, but the committed render test is the primary deterministic coverage.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/549d/hyperopen`.

1. Add RED tests in `test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs`.

2. Run the focused test command:

       npm run test:runner:generate
       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.views.portfolio.optimize.scenario-detail-view-test

   Actual RED behavior before production edits: the command failed with six assertions. The Sharpe KPI rendered `Sharpe`, `0.6`, and `optimized run`; it did not render `Sharpe · current → target`, `0.7`, `1.2`, or `+0.5 · raw Sharpe change`. The negative Sharpe delta class still contained `text-trading-green`.

3. Edit `src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs` to compute and render current-to-target Sharpe.

4. Re-run the focused test command. Actual GREEN behavior: `hyperopen.views.portfolio.optimize.scenario-detail-view-test` passed with `12` tests, `155` assertions, `0` failures, and `0` errors.

5. Run the focused browser smoke:

       npx playwright test -c playwright.config.mjs tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep "setup and retained draft detail"

   Actual result: passed with `1` test in `38.0s`.

6. Run required code-change gates:

       npm run check
       npm test
       npm run test:websocket

   Actual result: `npm run check` failed at `npm run lint:docs` because `/hyperopen/docs/product-specs/portfolio-page-parity-prd.md` is stale by the docs guard. `npm test` passed with `4007` tests, `22078` assertions, `0` failures, and `0` errors. `npm run test:websocket` passed with `524` tests, `3043` assertions, `0` failures, and `0` errors.

7. Run governed design review for the optimizer result route:

       npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app

   Actual result: passed with run id `design-review-2026-05-23T12-56-54-507Z-cad64328`. The run inspected `375`, `768`, `1280`, and `1440` widths and reported `PASS` for visual evidence, native-control, styling-consistency, interaction, layout-regression, and jank/perf.

8. Clean up browser-inspection sessions:

       npm run browser:cleanup

   Actual result: passed with `{"ok": true, "stopped": [], "results": []}`.

## Validation and Acceptance

Acceptance is met when the optimizer scenario KPI strip shows the Sharpe card as `Sharpe · current → target`. If the result has current Sharpe `0.7` and target raw Sharpe `1.2`, the value line should display `0.7 → 1.2` and the delta line should display `+0.5 · raw Sharpe change` in green. If current Sharpe is `1.2` and target raw Sharpe is `0.7`, the delta line should display `-0.5 · raw Sharpe change` in yellow.

The screenshot scenario should no longer show top Sharpe `0.566` as the main target Sharpe value. With expected return `58.43%` and volatility `51.59%`, the target side of the top Sharpe card should align with the frontier callout's raw Sharpe of about `1.133`.

Automated acceptance:
- `node out/test.js --test=hyperopen.views.portfolio.optimize.scenario-detail-view-test` passes after failing before the implementation.
- `npm run check`, `npm test`, and `npm run test:websocket` pass unless an unrelated pre-existing blocker is documented.
- `npx playwright test -c playwright.config.mjs tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep "setup and retained draft detail"` passes.

Browser-QA accounting:
- Visual pass: PASS in design-review run `design-review-2026-05-23T12-56-54-507Z-cad64328`.
- Native-control pass: PASS in design-review run `design-review-2026-05-23T12-56-54-507Z-cad64328`.
- Styling-consistency pass: PASS in design-review run `design-review-2026-05-23T12-56-54-507Z-cad64328`.
- Interaction pass: PASS in design-review run `design-review-2026-05-23T12-56-54-507Z-cad64328`.
- Layout-regression pass: PASS in design-review run `design-review-2026-05-23T12-56-54-507Z-cad64328`.
- Jank/perf pass: PASS in design-review run `design-review-2026-05-23T12-56-54-507Z-cad64328`.
- Residual blind spot: the design review notes that hover, active, disabled, and loading states require targeted route actions when not present by default. This is not material to the text-only Sharpe KPI change.

## Idempotence and Recovery

The change is local to one view namespace and its tests. Re-running the test runner generation and compile commands is safe. If the new render test fails after implementation, inspect the Hiccup tree for the Sharpe KPI by `data-role="portfolio-optimizer-scenario-kpi-sharpe"` and confirm the expected text nodes and class list are present. If broad gates fail in unrelated areas, leave the implementation in place, keep this plan active or document the blocker, and do not weaken the focused Sharpe acceptance.

## Artifacts and Notes

Initial investigation:

    target raw Sharpe = 0.5843 / 0.5159 = 1.1325838340763714
    shrunk Sharpe = 0.5 * 1.1325838340763714 = 0.5662919170381857

Validation transcript excerpts:

    Testing hyperopen.views.portfolio.optimize.scenario-detail-view-test
    Ran 12 tests containing 155 assertions.
    0 failures, 0 errors.

    npx playwright test -c playwright.config.mjs tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep "setup and retained draft detail"
    1 passed (38.0s)

    npm test
    Ran 4007 tests containing 22078 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

## Interfaces and Dependencies

No public API changes are required. The implementation depends on existing result payload keys:

- `[:current-performance :in-sample-sharpe]`
- `[:performance :in-sample-sharpe]`
- `:current-expected-return`
- `:current-volatility`
- `:expected-return`
- `:volatility`

The implementation should not rename `kpi-card`, `kpi-delta-class`, or the existing `data-role` values used by tests and browser selectors.
