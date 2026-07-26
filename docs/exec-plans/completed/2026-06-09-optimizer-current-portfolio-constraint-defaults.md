# Seed Optimizer Constraints From Current Portfolio

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

When a user opens `/portfolio/optimize/new` and starts from their current holdings, the optimizer should not begin from static constraints that describe a generic 100 percent net-long, 200 percent gross book. It should seed the draft constraints from the user's current portfolio, so a user with a levered or hedged book can rebalance into a target with similar gross and net exposure without immediately fighting infeasible defaults.

After this change, the current-holdings seed action derives gross and net exposure ratios from the current portfolio snapshot, saves those ratios into the draft constraints, and clears the turnover cap. The visible Constraints panel then shows editable values that reflect the user's current book. Static `default-draft` remains deterministic for app boot, tests, saved scenario fallbacks, and empty portfolios.

## Context References

Public refs:
- Direct user/maintainer request on 2026-06-09: "in optimize/new the constraints should default to what the users current portfolio is and should be in a configuration that allows for easy rebalancing into similar Gross and Net Exposures. Come up with an execution plan and implement it."

Repo artifacts:
- `AGENTS.md` requires an ExecPlan for complex UI/optimizer changes and required gates after code changes.
- `docs/PLANS.md` and `.agents/PLANS.md` define the living ExecPlan contract.
- `docs/exec-plans/active/2026-05-30-optimizer-from-holdings-usable-universe.md` owns the existing From holdings universe import path this work extends.
- `docs/exec-plans/completed/2026-05-26-optimizer-no-turnover-cap-toggle.md` explains why a static turnover cap can make current-portfolio rebalancing infeasible.

Local scratch refs (non-authoritative):
- The user attached a screenshot of the current Constraints panel showing static values: gross exposure 2, net exposure min 1, net exposure max 1, and turnover cap 1.

## Progress

- [x] (2026-06-09T15:16Z) Inspected optimizer default draft, current portfolio snapshot, From holdings action, setup Constraints UI, and relevant completed ExecPlans.
- [x] (2026-06-09T15:16Z) Chose to seed current-portfolio defaults through the From holdings action/helper rather than making `default-draft` depend on live state.
- [x] (2026-06-09T15:16Z) Created this active ExecPlan before production edits.
- [x] (2026-06-09T15:18Z) Added RED tests for current-derived gross, net, max-asset, long-only, and turnover constraint defaults.
- [x] (2026-06-09T15:20Z) Restored missing local `node_modules` with `npm install` after the first RED attempt was blocked by missing `lucide`.
- [x] (2026-06-09T15:21Z) Confirmed RED: focused test run produced undeclared-var errors for `current-derived-constraints` and missing saved constraint assertions.
- [x] (2026-06-09T15:25Z) Implemented `current-derived-constraints` and wired it into `set-portfolio-optimizer-universe-from-current`.
- [x] (2026-06-09T15:26Z) Confirmed GREEN: focused current-portfolio and action tests passed, then the existing universe action namespace passed.
- [x] (2026-06-09T16:16Z) Ran required gates: `npm run check`, `npm test`, and `npm run test:websocket` passed.
- [x] (2026-06-09T16:55Z) Added and passed a focused Playwright regression for `/portfolio/optimize/new` -> From holdings -> current exposure constraints.

## Surprises & Discoveries

- Observation: The static `default-draft` is a zero-argument contract used by app state defaults, route fallbacks, scenario mismatch scoping, and test fixtures.
  Evidence: `src/hyperopen/state/app_defaults.cljs` stores `(portfolio-optimizer-defaults/default-optimizer-state)`, and `src/hyperopen/portfolio/optimizer/application/view_model/scenario.cljs` uses `(optimizer-defaults/default-draft)` while hiding stale scenario state.

- Observation: The current From holdings action already has the right snapshot boundary and current exposure metadata.
  Evidence: `src/hyperopen/portfolio/optimizer/actions/universe.cljs` calls `current-portfolio/current-portfolio-snapshot`, and `src/hyperopen/portfolio/optimizer/application/current_portfolio.cljs` computes `:gross-exposure-usdc`, `:net-exposure-usdc`, `:nav-usdc`, signed weights, and long/short side.

- Observation: Turnover cap already supports `nil` as "no cap" in the draft and UI.
  Evidence: `src/hyperopen/views/portfolio/optimize/setup_constraint_controls.cljs` toggles `:max-turnover` between `nil` and `1.0`, while `docs/exec-plans/completed/2026-05-26-optimizer-no-turnover-cap-toggle.md` records the solver path omits the turnover L1 constraint when the value is nil.

- Observation: This worktree initially had no `node_modules`, so the first RED run compiled the new missing-helper warnings but could not execute tests.
  Evidence: `npm ls lucide --depth=0` returned `(empty)`, `test -d node_modules` reported `node_modules-missing`, and `node out/test.js` failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`. Running `npm install` from the checked-in lockfile restored dependencies.

- Observation: The RED test failure matched the intended missing behavior after dependency restoration.
  Evidence: `node out/test.js --test=hyperopen.portfolio.optimizer.application.current-portfolio-test --test=hyperopen.portfolio.optimizer.current-portfolio-constraint-actions-test` reported undeclared-var warnings for `current-derived-constraints`, three helper errors, and seven action assertions where saved constraint values were nil.

- Observation: The `/portfolio/optimize/new` browser route can asynchronously overwrite directly seeded `:webdata2` after wallet bootstrap.
  Evidence: A Playwright probe showed wallet bootstrap requests replaced seeded holdings with the default empty clearinghouse snapshot unless the regression waited for account bootstrap to settle and then seeded current holdings immediately before clicking From holdings.

## Decision Log

- Decision: Seed constraints when the user imports current holdings, not by making `default-draft` dynamic.
  Rationale: The route and scenario code rely on a deterministic empty draft. Current-portfolio data is live account state and belongs in an action/helper that can be tested with explicit state.
  Date/Author: 2026-06-09 / Codex

- Decision: Use current-centered exposure defaults instead of preserving the old static gross cap.
  Rationale: The user asked for defaults that reflect the current portfolio. `:gross-max` should come from the current gross exposure ratio, widened only as much as needed to keep the seeded net band feasible. An exact net equality can make "similar exposure" unnecessarily brittle, so the net bounds use a 5 percentage point band around current net exposure.
  Date/Author: 2026-06-09 / Codex

- Decision: Clear `:max-turnover` when seeding from current holdings.
  Rationale: A turnover cap limits how far target weights can move from current weights. The user's goal is easy rebalancing into similar gross and net exposure, so the default should not impose an unrelated static turnover limit. Users can re-enable the cap in the existing Constraints panel.
  Date/Author: 2026-06-09 / Codex

## Outcomes & Retrospective

Implementation and validation are complete. The change adds a pure application helper, one action save path, focused ClojureScript coverage, and a browser regression for the visible `/portfolio/optimize/new` flow. It does not change solver math, request normalization, or saved scenario migrations.

## Context and Orientation

The optimizer draft lives at `[:portfolio :optimizer :draft]`. Its `:constraints` map controls the optimizer's permitted target weights. Gross exposure is the sum of absolute target weights. Net exposure is the signed sum of target weights. For example, a 130 percent long and 30 percent short book has gross exposure 1.6 and net exposure 1.0.

`src/hyperopen/portfolio/optimizer/defaults.cljs` defines static defaults such as `:gross-max 2.0`, `:net-min 1.0`, `:net-max 1.0`, and `:max-turnover 1.0`. These defaults should stay available as a fallback.

`src/hyperopen/portfolio/optimizer/application/current_portfolio.cljs` builds a pure snapshot from app state. Its `:capital` map includes `:nav-usdc`, `:gross-exposure-usdc`, and `:net-exposure-usdc`. Each exposure row includes signed notional and a `:side` such as `:long` or `:short`.

`src/hyperopen/portfolio/optimizer/actions/universe.cljs` owns the From holdings action `set-portfolio-optimizer-universe-from-current`. That action already builds a usable draft universe from current exposures, caps it to 25 instruments, queues history prefetch, and saves the draft as dirty. This plan extends that action to also save current-derived constraint defaults.

`src/hyperopen/views/portfolio/optimize/setup_constraint_controls.cljs` renders the Constraints panel. No visual redesign is planned. Once the action saves the current-derived constraints, the existing inputs render those values.

## Plan of Work

First, add focused RED tests near the current ownership boundary. Extend `test/hyperopen/portfolio/optimizer/application/current_portfolio_test.cljs` with tests for a new pure helper that converts a current portfolio snapshot into default constraints. The tests should cover a levered long/short portfolio and an empty or zero-capital snapshot. Extend `test/hyperopen/portfolio/optimizer/universe_actions_test.cljs` or a dedicated neighboring action test to prove `set-portfolio-optimizer-universe-from-current` saves `:gross-max`, `:net-min`, `:net-max`, `:long-only?`, `:max-turnover nil`, and preserves unrelated constraint keys such as `:max-asset-weight`.

Second, implement the pure helper in `src/hyperopen/portfolio/optimizer/application/current_portfolio.cljs`. The helper should accept a snapshot and an existing constraints map. It should return nil when `:nav-usdc` is not positive. Otherwise it should derive:

- `gross-ratio = gross-exposure-usdc / nav-usdc`
- `net-ratio = net-exposure-usdc / nav-usdc`
- `:net-min` and `:net-max` as a rounded plus/minus 0.05 band around net ratio.
- `:gross-max` as the rounded current gross ratio, raised only when necessary so it is at least the absolute value of each seeded net bound.
- `:max-asset-weight` as the greater of the existing per-asset cap and the largest current absolute instrument weight, so concentrated current books remain feasible without making diversified books more restrictive.
- `:long-only? false` so the solver honors the seeded net exposure band. In the current constraint encoder, `:long-only? true` implies an exact net target of 1, which would break current-centered net defaults for partially invested, hedged, or levered books.
- `:max-turnover nil` so the target can move while staying in the seeded gross/net envelope.

Third, wire the helper into `set-portfolio-optimizer-universe-from-current` in `src/hyperopen/portfolio/optimizer/actions/universe.cljs`. When the current snapshot has usable capital and a non-empty universe is selected, append a save for the full merged constraints map along with the universe save. If the snapshot cannot produce current-derived defaults, preserve the existing action behavior.

Fourth, run focused tests after RED and again after implementation. Then run the required gates from AGENTS: `npm run check`, `npm test`, and `npm run test:websocket`. If `npm run check` is blocked by unrelated active-plan or stale-doc guardrails already present in this worktree, record the exact failure here and in the final response.

## Concrete Steps

Run commands from `/Users/barry/.codex/worktrees/502f/hyperopen`.

1. Add failing tests with `apply_patch`.

2. Run the focused test command and confirm RED:

       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.portfolio.optimizer.application.current-portfolio-test --test=hyperopen.portfolio.optimizer.universe-actions-test

   RED transcript before implementation:

       Testing hyperopen.portfolio.optimizer.application.current-portfolio-test
       ERROR in (current-derived-constraints-center-gross-and-net-on-current-book-test)
       TypeError: Cannot read properties of undefined (reading 'cljs$core$IFn$_invoke$arity$2')

       Testing hyperopen.portfolio.optimizer.current-portfolio-constraint-actions-test
       FAIL in (from-current-holdings-seeds-current-centered-constraints-test)
       expected: (= false (:long-only? constraints))
         actual: (not (= false nil))

       Ran 8 tests containing 49 assertions.
       7 failures, 3 errors.

3. Implement the helper and action wiring.

4. Rerun the focused command.

   GREEN transcript after implementation:

       Testing hyperopen.portfolio.optimizer.application.current-portfolio-test
       Testing hyperopen.portfolio.optimizer.current-portfolio-constraint-actions-test
       Ran 8 tests containing 60 assertions.
       0 failures, 0 errors.

   Shared-action regression:

       node out/test.js --test=hyperopen.portfolio.optimizer.universe-actions-test
       Ran 16 tests containing 26 assertions.
       0 failures, 0 errors.

5. Run required gates:

       npm run check
       npm test
       npm run test:websocket

   Required gate results:

       npm run check
       completed successfully.

       npm test
       completed successfully.

       npm run test:websocket
       Ran 534 tests containing 3090 assertions.
       0 failures, 0 errors.

6. Because this is UI-visible through the existing Constraints inputs on `/portfolio/optimize/new`, run the smallest relevant browser or Playwright route smoke if time and tooling permit, then run:

       npm run browser:cleanup

   Browser regression result:

       PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "portfolio optimizer From holdings seeds current exposure constraints" --workers=1
       1 passed.

## Validation and Acceptance

Acceptance is met when a user can open `/portfolio/optimize/new`, click `From holdings`, and see the Constraints panel populate with a gross exposure cap and net exposure band derived from the current portfolio rather than the static 2/1/1 defaults. For a current portfolio with 2.0 gross exposure and 0.8 net exposure, the seeded constraints should allow approximately similar exposure, such as gross at least 2.0 and net between 0.75 and 0.85. For a partially invested portfolio with 0.32 gross and 0.20 net, the seeded constraints should stay near that exposure instead of falling back to gross 2 and net 1. The turnover cap should be off by default for that seeded draft.

Automated acceptance is met when the pure helper test proves the math and the action test proves the saved draft paths. Existing optimizer tests must continue to pass or any unrelated blockers must be explicitly recorded.

Acceptance is met. The browser regression verifies a wallet current book with BTC +150 percent notional and ETH -50 percent notional seeds a 2.0 gross cap, 0.95 to 1.05 net band, 1.5 per-asset cap, and no turnover cap after clicking From holdings.

## Idempotence and Recovery

The change is pure and local. Re-running the action recomputes the same constraints from the current snapshot and saves them into the draft. If the policy proves too broad, revert only the helper addition in `current_portfolio.cljs`, the constraint path additions in `universe.cljs`, and the matching tests. No schema migration or destructive operation is involved.

## Artifacts and Notes

Focused validation artifacts:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.current-portfolio-test --test=hyperopen.portfolio.optimizer.current-portfolio-constraint-actions-test
    Ran 8 tests containing 60 assertions.
    0 failures, 0 errors.

    node out/test.js --test=hyperopen.portfolio.optimizer.universe-actions-test
    Ran 16 tests containing 26 assertions.
    0 failures, 0 errors.

Required validation artifacts:

    npm run check
    completed successfully.

    npm test
    completed successfully.

    npm run test:websocket
    Ran 534 tests containing 3090 assertions.
    0 failures, 0 errors.

Browser validation artifact:

    PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "portfolio optimizer From holdings seeds current exposure constraints" --workers=1
    1 passed.

Revision note, 2026-06-09 / Codex: Initial active ExecPlan created from the direct implementation request after inspecting current optimizer defaults, From holdings behavior, and the prior turnover-cap plan.

Revision note, 2026-06-09 / Codex: Tightened the constraint policy so gross exposure is derived from the current portfolio rather than maxing against the old static 2.0 default. Recorded the long-only interaction with net-band semantics.

Revision note, 2026-06-09 / Codex: Recorded RED/GREEN focused test evidence, the local dependency restoration, and the implemented helper/action boundary.

Revision note, 2026-06-09 / Codex: Recorded required gate results, browser regression evidence, and the Playwright account-bootstrap seeding discovery.

## Interfaces and Dependencies

Add a public pure helper in `src/hyperopen/portfolio/optimizer/application/current_portfolio.cljs`:

    (current-derived-constraints snapshot existing-constraints)

It returns either nil or a constraints map suitable for saving at `contracts/draft-constraints-path`. It must not read global app state. `src/hyperopen/portfolio/optimizer/actions/universe.cljs` will call this helper using the snapshot already built by `set-portfolio-optimizer-universe-from-current`.

No new dependencies are required.
