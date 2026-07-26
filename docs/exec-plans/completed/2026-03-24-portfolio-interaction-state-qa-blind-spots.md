# Close Portfolio And Trader Portfolio Interaction-State QA Blind Spots

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is governed by `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The tracked `bd` work for this plan is parent epic `hyperopen-6len` with child tasks `hyperopen-l6vw` and `hyperopen-x7uh`, and `bd` was the local tracker used at the time while this plan tracks the implementation story.

## Purpose / Big Picture

The governed design-review flow for `/portfolio` and `/portfolio/trader/:address` currently reports the standard residual interaction blind spot: hover, active, disabled, and loading states are not all force-driven during passive route review. After this plan lands, the repository should have deterministic interaction coverage for the stable route-local states that matter on both portfolio surfaces, plus an explicit durable record of which remaining states are intentionally skipped and why.

The visible proof should be straightforward. A contributor should be able to run committed browser coverage and see stable checks for the portfolio selectors, chart tabs, account tabs, and trader-inspection controls on the real routes. The accompanying QA note should explain which states are now covered directly and which route-local states still depend on view-level tests or are absent by design.

## Progress

- [x] (2026-03-24 19:05 EDT) Audited the governing repo contracts in `/hyperopen/AGENTS.md`, `/hyperopen/docs/MULTI_AGENT.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/docs/BROWSER_TESTING.md`, `/hyperopen/docs/agent-guides/browser-qa.md`, and `/hyperopen/docs/FRONTEND.md`.
- [x] (2026-03-24 19:12 EDT) Audited the affected route surfaces and existing coverage in `/hyperopen/src/hyperopen/views/portfolio_view.cljs`, `/hyperopen/src/hyperopen/views/leaderboard_view.cljs`, `/hyperopen/src/hyperopen/views/account_info_view.cljs`, `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`, `/hyperopen/test/hyperopen/views/leaderboard_view_test.cljs`, and `/hyperopen/tools/playwright/test/routes.smoke.spec.mjs`.
- [x] (2026-03-24 19:16 EDT) Confirmed the design-review tooling behavior in `/hyperopen/tools/browser-inspection/src/design_review/pass_registry.mjs`: passive review always leaves a generic interaction blind spot unless route-specific interactions are driven deliberately.
- [x] (2026-03-24 19:20 EDT) Created this active ExecPlan tied to `hyperopen-6len`, `hyperopen-l6vw`, and `hyperopen-x7uh`.
- [x] (2026-03-24 20:12 EDT) Added stable `data-role` hooks for portfolio action-row buttons and summary-selector triggers/options in `/hyperopen/src/hyperopen/views/portfolio_view.cljs`, and pinned those seams in `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`.
- [x] (2026-03-24 20:20 EDT) Added committed deterministic Playwright coverage in `/hyperopen/tools/playwright/test/portfolio-regressions.spec.mjs` for the stable interaction states on `/portfolio` and `/portfolio/trader`.
- [x] (2026-03-24 20:22 EDT) Added targeted browser-inspection scenarios in `/hyperopen/tools/browser-inspection/scenarios/portfolio-interaction-states.json` and `/hyperopen/tools/browser-inspection/scenarios/trader-portfolio-interaction-states.json` so route-QA artifacts can capture the driven states directly.
- [x] (2026-03-24 20:24 EDT) Wrote `/hyperopen/docs/qa/portfolio-interaction-state-qa-2026-03-24.md` to distinguish covered route-local states from explicit skips for hover-only, loading, or intentionally absent disabled states.
- [x] (2026-03-24 20:31 EDT) Fixed the shared account-info read-only leak so trader-route and spectate-mode read-only state now suppress balances, positions, open-orders, and TWAP mutation affordances through `/hyperopen/src/hyperopen/views/account_info/vm.cljs`, `/hyperopen/src/hyperopen/views/account_info_view.cljs`, and the shared account-info tab renderers.
- [x] (2026-03-24 20:36 EDT) Added composition-level regression coverage for non-empty read-only tabs in `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`, `/hyperopen/test/hyperopen/views/account_info_view_test.cljs`, and `/hyperopen/test/hyperopen/views/account_info/vm_test.cljs`, then tightened the live browser regression in `/hyperopen/tools/playwright/test/portfolio-regressions.spec.mjs` to verify trader balances omit `Send` and `Transfer`.
- [x] (2026-03-24 20:40 EDT) Re-ran governed design review for `portfolio-route` and `trader-portfolio-route` with session reuse. Run `design-review-2026-03-24T23-03-57-882Z-626fed6f` finished `PASS` with the expected residual `state-sampling-limited` blind spots on both routes and all reviewed viewports.
- [x] (2026-03-24 20:43 EDT) Ran the smallest relevant browser commands first, then `npm run check`, `npm test`, and `npm run test:websocket`. All passed.

## Surprises & Discoveries

- Observation: the design-review interaction pass intentionally emits a blind spot even when focus traversal passes, because passive route review does not force route-local hover, active, disabled, or loading states.
  Evidence: `/hyperopen/tools/browser-inspection/src/design_review/pass_registry.mjs` `gradeInteraction` always appends the `state-sampling-limited` blind spot unless the route exposes no focusable controls at all.

- Observation: `/portfolio` and `/portfolio/trader` already expose several stable interaction seams, but some important triggers still lack narrow `data-role` hooks.
  Evidence: `/hyperopen/src/hyperopen/views/portfolio_view.cljs` already gives `data-role` hooks to chart tabs, benchmark selectors, the performance-metrics loading overlay, and the trader-inspection header actions, while the summary selectors and top-level action buttons still rely on generic structure or visible text.

- Observation: the repo precedent for closing a browser-QA interaction blind spot is not “run design review again,” but “add stable selectors plus deterministic targeted browser coverage.”
  Evidence: `/hyperopen/docs/exec-plans/completed/2026-03-21-funding-tooltip-layering-regression-guards.md` added stable selectors, a deterministic browser-inspection scenario, and view-level guardrails after the normal design-review run still left the interaction pass partially blind.

- Observation: the portfolio routes already have solid view-level loading-state coverage for performance metrics, so not every blind spot must be closed with route-level browser forcing.
  Evidence: `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs` already includes `portfolio-view-performance-metrics-loading-overlay-renders-explainer-copy-test`.

- Observation: the `/portfolio` child bead id is `hyperopen-l6vw`, not `hyperopen-16lv`.
  Evidence: `/usr/local/bin/bd children hyperopen-6len --json` lists `hyperopen-l6vw` and `hyperopen-x7uh` as the two open child tasks under the parent epic.

- Observation: the shared account-info read-only predicate covers both trader-route inspection and spectate mode, but the live portfolio fixtures only give deterministic non-empty balances on the trader route.
  Evidence: `/hyperopen/src/hyperopen/account/context.cljs` defines `inspected-account-read-only?` for both paths, `/hyperopen/src/hyperopen/views/account_info/vm.cljs` consumes that predicate generically, and the live Playwright probe shows `/portfolio/trader/:address` has populated balances while positions, open orders, and TWAP are empty in the default fixture state.

## Decision Log

- Decision: close these beads with a combination of stable selectors, committed deterministic browser coverage, and an explicit QA note instead of trying to make governed design review alone prove every state.
  Rationale: Hyperopen policy gives Playwright ownership of committed deterministic browser regression coverage, while the browser-QA/design-review tool is intentionally passive and will otherwise continue to report route-level blind spots.
  Date/Author: 2026-03-24 / Codex

- Decision: focus browser coverage on route-local active and open states that are stable today, and document disabled or loading states explicitly when the route does not present them directly.
  Rationale: `/portfolio` and `/portfolio/trader` have deterministic selectors, tabs, and inspection controls that can be asserted reliably. Some loading or hover-only states are either already covered in view tests or are not honest UI states for the trader-inspection surface.
  Date/Author: 2026-03-24 / Codex

- Decision: keep trader-inspection read-only semantics expressed by omission rather than adding fake disabled mutation controls.
  Rationale: the existing `/portfolio/trader/:address` contract deliberately hides portfolio mutation affordances. Converting that absence into disabled buttons would add misleading UI rather than improving QA fidelity.
  Date/Author: 2026-03-24 / Codex

- Decision: split the regression contract between live-route browser checks and non-empty CLJS composition tests for the shared account-info tabs.
  Rationale: the trader route gives deterministic runtime evidence for balances on the live app, but positions, open orders, and TWAP do not have stable live fixture rows. Composition tests let the repo prove the read-only omissions on real derived state without inventing browser-only seed hooks.
  Date/Author: 2026-03-24 / Codex

## Outcomes & Retrospective

This plan is complete.

- `/portfolio` and `/portfolio/trader/:address` now expose stable browser-addressable selectors for the route-local controls covered by this bead.
- committed Playwright coverage proves the stable active/open interactions on both routes and verifies the trader balances surface stays read-only.
- composition tests now prove that trader-route and spectate-mode read-only state remove `Send`, `Transfer`, `Repay`, `Close All`, `Reduce`, `Edit Margin`, `Edit TP/SL`, `Cancel All`, `Cancel`, and `Terminate` from the shared account-info tabs when real non-empty data is present.
- the durable QA note records the covered omissions, the governed design-review rerun result, and the remaining intentional skips.
- `npm run check`, `npm test`, and `npm run test:websocket` all passed after the final regression additions.

## Context and Orientation

Two route surfaces matter here.

The standard portfolio route is rendered in `/hyperopen/src/hyperopen/views/portfolio_view.cljs`. It contains the top action row, summary selectors, chart tabs, returns-benchmark search and chip controls, the performance-metrics extra tab, and the shared account-info tab strip via `/hyperopen/src/hyperopen/views/account_info_view.cljs`.

The trader-inspection route reuses the same `portfolio-view` entry point but swaps the normal action row for the read-only `portfolio-inspection-header`. That header exposes an in-app `Your Portfolio` return control and a separate Hyperliquid Explorer link, while the route hides the deposits-and-withdrawals extra tab and the normal cash-movement action row.

Existing focused characterization tests already live in:

- `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`
- `/hyperopen/test/hyperopen/views/leaderboard_view_test.cljs`
- `/hyperopen/test/hyperopen/views/account_info/navigation_test.cljs`

Existing browser coverage currently includes only route smoke for these surfaces in:

- `/hyperopen/tools/playwright/test/routes.smoke.spec.mjs`

The browser-QA toolchain and scenario framework live in:

- `/hyperopen/tools/browser-inspection/src/cli.mjs`
- `/hyperopen/tools/browser-inspection/src/scenario_runner.mjs`
- `/hyperopen/tools/browser-inspection/scenarios/`

The key behavioral constraint is that the portfolio work here must remain honest. `/portfolio/trader/:address` is read-only by route contract, so this plan should not add fake enabled or disabled mutation affordances just to create a browser assertion target.

## Plan of Work

First, make the important portfolio controls easy to target. Update `/hyperopen/src/hyperopen/views/portfolio_view.cljs` so summary-selector triggers and options, top action-row buttons, and any other interaction-critical controls needed for deterministic tests expose stable `data-role` hooks. Keep the names narrow and route-specific. Reuse the existing stable roles for chart tabs, benchmark selectors, benchmark chips, the performance-metrics loading overlay, and the trader-inspection header actions where those already exist.

Second, extend the view-level tests so the new selector seams and interaction-state contracts are pinned in CLJS. `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs` should prove the relevant controls render with the intended roles and state attributes on both the standard portfolio route and the trader-inspection route.

Third, add committed browser coverage for the stable interaction paths. The new Playwright coverage should stay narrow and deterministic: opening summary selectors, switching chart tabs, switching account tabs, and validating trader-inspection read-only controls. If route-focused browser-inspection scenarios materially improve governed QA traceability, add those under `/hyperopen/tools/browser-inspection/scenarios/` rather than overloading design review itself.

Fourth, write a durable QA note under `/hyperopen/docs/qa/` that lists what is covered now and what remains an explicit skip. The note should call out when loading behavior is already guarded by view tests, when hover-only interactions are already covered elsewhere, and when disabled state is intentionally absent because the trader route hides mutation affordances by design.

Finally, validate the implementation. Run the smallest relevant Playwright command first, then any targeted browser-inspection scenarios that were added, then the required repository gates from `/hyperopen/AGENTS.md`.

## Concrete Steps

Work from `/hyperopen`.

1. Update selectors and route-local interaction seams in:

   - `/hyperopen/src/hyperopen/views/portfolio_view.cljs`

2. Extend view-level tests in:

   - `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`
   - `/hyperopen/test/hyperopen/views/account_info_view_test.cljs`
   - `/hyperopen/test/hyperopen/views/account_info/vm_test.cljs`

3. Add committed browser regression coverage in:

   - `/hyperopen/tools/playwright/test/portfolio-regressions.spec.mjs`

4. If route-QA artifacts improve closure, add deterministic browser-inspection scenarios in:

   - `/hyperopen/tools/browser-inspection/scenarios/portfolio-interaction-states.json`
   - `/hyperopen/tools/browser-inspection/scenarios/trader-portfolio-interaction-states.json`

5. Write the explicit QA accounting note in:

   - `/hyperopen/docs/qa/portfolio-interaction-state-qa-2026-03-24.md`

6. Run validation commands:

   - `npm run test:playwright:smoke -- --grep "portfolio interaction|trader portfolio"`
   - `npm run test:browser-inspection` if browser-inspection scenario support changes
   - any targeted browser-inspection scenario command needed for the new route artifacts
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

## Validation and Acceptance

Acceptance is behavioral.

On `/portfolio`, deterministic browser coverage must prove that the summary selectors can open, chart tabs expose the expected active state, and the shared account-info tabs can switch without relying on fragile text-only selectors. On `/portfolio/trader/:address`, deterministic browser coverage must prove that the read-only inspection header is present, the explorer link remains separate from the in-app navigation control, and the reusable chart/account tabs still expose stable interaction state.

The durable QA note must explicitly state which remaining blind spots are still intentional. Examples include loading states that are already proven in CLJS view tests and disabled states that do not exist on the trader route because the mutation controls are hidden rather than disabled.

The automated acceptance bar is:

- new focused CLJS tests pass
- new Playwright portfolio interaction coverage passes
- any changed browser-inspection scenario or helper tests pass
- `npm run check` passes
- `npm test` passes
- `npm run test:websocket` passes

## Idempotence and Recovery

This work is additive and safe to repeat. The selector additions should be narrow and should not change route behavior. If a new browser assertion turns out to be flaky, keep the selector seam and fall back to the narrowest deterministic state change instead of weakening the acceptance bar to screenshot-only checks. If the route-specific QA note needs to retain an explicit skip, record the exact reason rather than deleting coverage or inventing a fake disabled state.

## Artifacts and Notes

The most relevant existing references are:

- `/hyperopen/tools/browser-inspection/src/design_review/pass_registry.mjs`
- `/hyperopen/docs/exec-plans/completed/2026-03-21-funding-tooltip-layering-regression-guards.md`
- `/hyperopen/tools/playwright/test/routes.smoke.spec.mjs`
- `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`

These show the current blind-spot source, the existing repo precedent for targeted interaction closure, the current browser baseline for the portfolio routes, and the already-shipped view-level loading coverage.

## Interfaces and Dependencies

Do not add a second browser test framework or a route-specific one-off runner. Playwright should remain the committed deterministic browser regression surface. Browser-inspection scenarios should only be added when they provide route-QA evidence that Playwright does not already capture well.

At the end of this work:

- `/hyperopen/src/hyperopen/views/portfolio_view.cljs` must expose stable route-local selector hooks for the covered interactions.
- `/hyperopen/tools/playwright/test/portfolio-regressions.spec.mjs` must assert the stable `/portfolio` and `/portfolio/trader` interaction states.
- `/hyperopen/docs/qa/portfolio-interaction-state-qa-2026-03-24.md` must account explicitly for covered states and remaining intentional skips.

Plan update note (2026-03-24 19:20 EDT): created this active ExecPlan after auditing the `hyperopen-6len` parent and its child tasks `hyperopen-l6vw` and `hyperopen-x7uh`, the current portfolio/trader route surfaces, and the governed browser-QA toolchain. The next step is implementation of the selector, browser-coverage, and QA-accounting changes described above.
