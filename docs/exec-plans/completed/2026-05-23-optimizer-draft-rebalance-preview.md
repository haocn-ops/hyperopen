# Optimizer Draft Rebalance Preview Backfill

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The optimizer can compute target allocation weights while still rendering the draft Rebalance Preview route as unavailable when the retained solved result does not contain a prebuilt `:rebalance-preview` payload. After this change, a solved draft or scenario result with target/current weights should be able to show a rebalance preview by deriving rows from the solved result, active portfolio snapshot, and draft execution assumptions.

## Context References

- Direct user request on 2026-05-23: clicking Rebalance Preview from the draft page shows no rebalance content even when a target portfolio is active, and the requested outcome is an execution plan plus implementation.
- `/hyperopen/AGENTS.md` requires ExecPlans for complex changes and UI/browser QA accounting for optimizer UI work.
- `/hyperopen/docs/FRONTEND.md` and `/hyperopen/docs/BROWSER_TESTING.md` require deterministic Playwright coverage for repeatable UI flows and governed browser-QA accounting for UI-facing changes.
- `/hyperopen/src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs` selects the scenario tab and delegates the rebalance tab.
- `/hyperopen/src/hyperopen/views/portfolio/optimize/rebalance_tab.cljs` renders an empty state when solved results lack `:rebalance-preview`.
- `/hyperopen/src/hyperopen/portfolio/optimizer/domain/rebalance.cljs` already owns executable row classification, blocked reasons, costs, and margin summary calculation.
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` builds the active optimizer request, including current portfolio snapshot, manual capital, orderbook cost contexts, and draft execution assumptions.
- `/hyperopen/src/hyperopen/portfolio/optimizer/actions/execution.cljs` opens the execution review modal from the last successful run's `:rebalance-preview`.

## Progress

- [x] Investigated static code paths for draft/scenario routing, rebalance tab rendering, execution modal opening, optimizer engine payload construction, and current portfolio snapshot construction.
- [x] Attempted to inspect the user-provided nREPL endpoint on port `62548`; local connection was refused, so live REPL inspection is blocked in this environment.
- [x] Identified the root cause class: solved retained results can have enough target/current weight data for an allocation preview while missing the precomputed `:rebalance-preview` map, causing the rebalance tab and execution modal action to treat the preview as unavailable.
- [x] Added RED tests for derived rebalance preview on the unsaved draft route and execution modal action.
- [x] Verified RED behavior: focused tests failed because the action returned no effects and the `/portfolio/optimize/draft` rebalance tab rendered the unavailable empty state.
- [x] Implemented a pure rebalance-preview backfill helper and wired it through scenario/workspace view models plus execution actions.
- [x] Focused GREEN run passed for `hyperopen.views.portfolio.optimize.unsaved-draft-route-test` and `hyperopen.portfolio.optimizer.execution-actions-test`: 8 tests, 26 assertions, 0 failures.
- [x] Focused adjacent view-model and optimizer result tests passed: 46 tests, 412 assertions, 0 failures.
- [x] Focused Playwright smoke passed for `optimizer-view-model-routes.smoke.spec.mjs --grep "setup and retained draft detail"` against the managed app origin on port `8082`.
- [x] Required gates were accounted for: `npm test` and `npm run test:websocket` passed; `npm run check` remains blocked by an unrelated stale docs lint finding for `/hyperopen/docs/product-specs/portfolio-page-parity-prd.md`.
- [x] Governed browser QA passed for `portfolio-optimizer-results-route` at widths `375`, `768`, `1280`, and `1440`; visual evidence, native-control, styling-consistency, interaction, layout-regression, and jank/perf all reported `PASS`.
- [x] Browser cleanup completed with no tracked sessions left running.
- [x] Follow-up investigation found that clicking the scenario tab anchor could trigger a document navigation to `/portfolio/optimize/draft?otab=rebalance`, reloading the app and losing the unsaved in-memory solved draft run before the rebalance tab rendered.
- [x] Added component and Playwright regression coverage for tab switching without browser document navigation.

## Surprises & Discoveries

- The optimizer engine already builds `:rebalance-preview` for new solved runs, so the fix should preserve engine output and only synthesize a preview when a solved result lacks that map.
- The scenario route already has special handling for `/portfolio/optimize/draft`; the missing-preview behavior is not a route parser problem by itself.
- The execution modal action reads directly from app state, so view-only enrichment would make the tab render but leave the modal action inert. The same pure backfill needs to be used by both view model and action paths.
- Port `8080` was owned by another Hyperopen worktree during validation. The focused Playwright smoke used the managed app origin `http://127.0.0.1:8082`, and governed browser QA also inspected `http://localhost:8082/portfolio/optimize/qa-frontier`.
- `npm run check` is blocked by a pre-existing stale-doc policy failure: `/hyperopen/docs/product-specs/portfolio-page-parity-prd.md` is 93 days old against a 90-day review cycle. This blocker is unrelated to the optimizer rebalance-preview changes.
- The "successful run disappeared" symptom was a second bug rather than evidence that no solved run existed. The tab UI used real anchors while router navigation normally happens through explicit `:actions/navigate` effects; the anchor click could reload the single-page app and reset draft-only memory.

## Decision Log

- Decision: Preserve any existing engine-produced `:rebalance-preview` exactly.
  Rationale: New optimizer runs already have the richer preview, including quantities, cost context, and margin summary. Backfill should be compatibility behavior for retained or persisted solved results that lack that field.
  Date/Author: 2026-05-23 / Codex

- Decision: Build the fallback from the active readiness request plus solved result weights.
  Rationale: `setup_readiness/build-readiness` already applies manual capital, current account state, orderbook cost contexts, and draft execution assumptions. Duplicating those concerns in views would spread ownership.
  Date/Author: 2026-05-23 / Codex

- Decision: Include current-only instruments in the derived preview with target weight `0`.
  Rationale: A rebalance into a target portfolio should surface sell-to-zero rows for currently held instruments that are absent from the target, even if they are blocked due missing metadata or unsupported execution.
  Date/Author: 2026-05-23 / Codex

- Decision: Render scenario result tabs as buttons that dispatch tab state changes instead of anchors with `href`.
  Rationale: Tab switching is an in-app state transition. A browser-level navigation can reload the application and discard unsaved optimizer run state on `/portfolio/optimize/draft`.
  Date/Author: 2026-05-23 / Codex

## Plan of Work

1. Add RED coverage in `test/hyperopen/views/portfolio/optimize/unsaved_draft_route_test.cljs` for `/portfolio/optimize/draft` with `:results-tab :rebalance`, a solved retained result that has target/current weights but no `:rebalance-preview`, and an active current portfolio snapshot. The test should expect the rebalance review surface, summary KPIs, and at least one rebalance row to render.

2. Add RED coverage in `test/hyperopen/portfolio/optimizer/execution_actions_test.cljs` proving `open-portfolio-optimizer-execution-modal` can build a plan for a solved retained result without a prebuilt preview when current capital, prices, and draft execution assumptions are available.

3. Create `src/hyperopen/portfolio/optimizer/application/rebalance_preview.cljs`. It should expose a pure `result-with-rebalance-preview` function that:
   - returns non-solved results unchanged,
   - returns results with an existing map `:rebalance-preview` unchanged,
   - derives ordered instrument ids from target result ids, target/current weight maps, current portfolio ids, and current-only exposures,
   - merges instrument metadata from request universe, requested universe, current portfolio universe, and current portfolio exposures,
   - merges prices from history, current portfolio marks, and explicit execution assumption prices,
   - delegates row classification and summary math to `domain.rebalance/build-rebalance-preview`.

4. Wire the helper into `src/hyperopen/portfolio/optimizer/application/view_model/scenario.cljs` so `:result` and `:last-successful-run` in the scenario detail model include the derived preview when needed.

5. Wire the helper into `src/hyperopen/portfolio/optimizer/application/view_model/workspace.cljs` so setup-side retained draft links and downstream model consumers see the same enriched retained run.

6. Wire the helper into `src/hyperopen/portfolio/optimizer/actions/execution.cljs` before building the execution modal plan, so the modal opens from the same preview the UI can render.

7. Run focused tests:

       npm run test:runner:generate
       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.views.portfolio.optimize.unsaved-draft-route-test --test=hyperopen.portfolio.optimizer.execution-actions-test

8. Run the smallest stable browser regression that covers the retained draft optimizer route:

       npx playwright test -c playwright.config.mjs tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep "setup and retained draft detail"

9. Run required gates:

       npm run check
       npm test
       npm run test:websocket

10. Run governed UI QA for the optimizer results route or document any blocker:

        npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app
        npm run browser:cleanup

## Validation and Acceptance

Acceptance is met when a solved retained draft result without `:rebalance-preview` renders the Rebalance Preview tab instead of the unavailable empty state. The preview should show status KPIs, ready/blocked counts, and row-level deltas. Opening execution review from that derived preview should create an execution plan when at least one row is ready.

Existing solved results with `:rebalance-preview` must keep their existing preview unchanged. Non-solved results must still render the existing unavailable/empty state.

Validation performed:

- `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.views.portfolio.optimize.unsaved-draft-route-test --test=hyperopen.portfolio.optimizer.execution-actions-test` first failed RED, then passed GREEN with 8 tests and 26 assertions.
- `node out/test.js --test=hyperopen.views.portfolio.optimize.scenario-detail-view-test --test=hyperopen.views.portfolio.optimize.workspace-view-test --test=hyperopen.views.portfolio.optimize.results-panel-test --test=hyperopen.views.portfolio.optimize.execution-modal-test --test=hyperopen.portfolio.optimizer.application.view-model-test --test=hyperopen.portfolio.optimizer.application.view-model-rebalance-test` passed with 46 tests and 412 assertions.
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:8082 PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test -c playwright.config.mjs tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep "setup and retained draft detail"` passed with 1 test.
- `npm test` passed with 4009 tests and 22091 assertions.
- `npm run test:websocket` passed with 524 tests and 3043 assertions.
- `npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app` passed with run id `design-review-2026-05-23T17-57-10-675Z-be5d4a0b`; each configured pass was `PASS` across `review-375`, `review-768`, `review-1280`, and `review-1440`. The only residual blind spot is the standard state-sampling note for hover, active, disabled, and loading states when those are not present by default.
- `npm run browser:cleanup` passed with `stopped: []`.
- `npm run check` was run and is blocked by the unrelated stale-doc lint finding for `/hyperopen/docs/product-specs/portfolio-page-parity-prd.md`.
- Follow-up tab-navigation validation adds `hyperopen.views.portfolio.optimize.scenario-detail-view-test` coverage that the Rebalance Preview tab is a `button` without `href`, plus Playwright coverage that clicking it keeps the same document navigation entry count while the solved run remains in app state.
- `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.views.portfolio.optimize.scenario-detail-view-test --test=hyperopen.views.portfolio.optimize.unsaved-draft-route-test --test=hyperopen.portfolio.optimizer.execution-actions-test` passed with 20 tests and 183 assertions.
- `npx shadow-cljs --force-spawn compile app` passed after the patched worktree browser server reported stale output.
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:8082 PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test -c playwright.config.mjs tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep "setup and retained draft detail"` passed with 1 test after asserting route query state, no document navigation, and retained solved result state.
- `npm test` passed with 4009 tests and 22093 assertions.
- `npm run test:websocket` passed with 524 tests and 3043 assertions.
- `npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app` passed with run id `design-review-2026-05-23T22-10-31-761Z-cc7a8c6d`; visual evidence, native-control, styling-consistency, interaction, layout-regression, and jank/perf each reported `PASS` across `review-375`, `review-768`, `review-1280`, and `review-1440`.
- `npm run browser:cleanup` passed with `stopped: []`.

## Idempotence and Recovery

The new helper is pure and can be safely re-run by view models and actions. If focused tests fail because the fallback preview has different ready/blocked status than expected, inspect the derived `:instrument-ids`, current weights, target weights, `:instruments-by-id`, `:prices-by-id`, and capital input before changing UI rendering. If the unrelated stale-doc lint blocker is cleared later, rerun `npm run check` without changing this implementation.

## Outcomes & Retrospective

Implemented a compatibility backfill for solved optimizer draft and scenario results that have target/current weights but lack an engine-produced `:rebalance-preview`. The fallback preserves existing engine previews, leaves non-solved results alone, derives a preview from the active readiness request and solved result weights, and uses the same pure path for scenario detail rendering, workspace view models, and the execution modal action.

The user-facing outcome is that `/portfolio/optimize/draft` can render the Rebalance Preview tab and open the execution review modal for retained solved results instead of falling through to the unavailable empty state. Validation is complete for focused tests, adjacent tests, full test suite, websocket suite, focused Playwright smoke, and governed browser QA. The only incomplete repository gate is `npm run check`, blocked by the unrelated stale-doc policy item recorded above.

The follow-up user-facing outcome is that switching from Recommendation to Rebalance Preview no longer performs a full browser navigation that can discard an unsaved successful run. The query string is still updated through the existing shareable-route effect, but the in-memory optimizer state stays intact.
