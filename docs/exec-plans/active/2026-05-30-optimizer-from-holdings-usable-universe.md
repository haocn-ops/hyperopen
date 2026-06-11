# Make From Holdings Produce a Usable Optimizer Universe

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Users should be able to open the portfolio optimizer, click `From holdings`, and get a universe that reflects their usable holdings without a fixed asset-count cap. In the usability audit this previously produced a 132-asset universe while the UI still said `cap: 25 assets`, then failed waiting for optimizer history. The cap was first enforced to make the flow usable, then removed by explicit user request on 2026-06-11. Current behavior should prefer holdings with optimizer history discovery metadata that is not known-missing or rejected, rank selected holdings by largest absolute current exposure, and import all holdings that survive that known-unusable-history filter.

The user-visible proof is simple: a portfolio with many holdings should no longer be truncated to 25 assets, and the setup UI should no longer show a 25-asset cap. A portfolio with discovery metadata that marks some holdings as missing or rejected should still skip those known-unusable holdings when building the draft universe.

## Context References

Public refs:

- Direct user request on 2026-05-30: "Create an execution plan and implement: Make 'From holdings' produce a usable universe."

Repo artifacts:

- `docs/qa/optimizer-usability-scenarios-2026-05-29.md` records the exploratory finding that a large existing portfolio imported 132 holdings despite the visible 25-asset cap.
- `src/hyperopen/portfolio/optimizer/BOUNDARY.md` identifies optimizer universe sources and setup UI as optimizer-owned behavior.
- `docs/BROWSER_TESTING.md` explains that deterministic browser assertions should use Playwright if UI behavior changes.

Local scratch refs, non-authoritative:

- None.

## Progress

- [x] (2026-05-30) Verified this session is already in a linked Codex worktree at `/Users/barry/.codex/worktrees/39cc/hyperopen`.
- [x] (2026-05-30) Inspected `set-portfolio-optimizer-universe-from-current` and the current action tests.
- [x] (2026-05-30) Created this active ExecPlan before production edits.
- [x] (2026-05-30) Added failing action coverage for capping and skipping known-unusable holdings.
- [x] (2026-05-30) Implemented capped, ranked, discovery-aware From holdings universe selection.
- [x] (2026-05-30) Ran focused optimizer action tests after implementation.
- [x] (2026-05-30) Moved the new test into a dedicated namespace after the namespace-size guard flagged the existing universe action test file.
- [x] (2026-05-30) Ran required validation attempts and recorded outcomes.
- [x] (2026-06-11) Removed the fixed From holdings asset-count cap by explicit user request while keeping the known-unusable-history filter and notional ranking.
- [ ] Resolve or explicitly accept unrelated repo documentation/namespace-size blockers before moving this plan to completed.

## Surprises & Discoveries

- Observation: The current action imports all current exposures.
  Evidence: `src/hyperopen/portfolio/optimizer/actions/universe.cljs` maps `(:exposures snapshot)` through `common/exposure->universe-instrument`, dedupes, and saves the full vector.

- Observation: Current-portfolio exposures already carry `:abs-notional-usdc`, `:signed-notional-usdc`, and stable local `:instrument-id` values, but `common/exposure->universe-instrument` intentionally drops notional fields from the saved optimizer universe.
  Evidence: `src/hyperopen/portfolio/optimizer/application/current_portfolio.cljs` computes exposure notionals, while `src/hyperopen/portfolio/optimizer/actions/common.cljs` stores only optimizer instrument identity/display metadata.

- Observation: Optimizer history discovery metadata can be merged into any local instrument-like map by local id.
  Evidence: `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/discovery.cljs` `with-discovery-metadata` checks `:key`, `:instrument-id`, and HIP-3 alias keys against `:backend-id-by-local-id`.

- Observation: `test/hyperopen/portfolio/optimizer/universe_actions_test.cljs` was already close to the namespace-size limit.
  Evidence: adding the new test there made `npm run lint:namespace-sizes` report `[missing-size-exception] test/hyperopen/portfolio/optimizer/universe_actions_test.cljs - namespace has 526 lines`. The new coverage was moved to `test/hyperopen/portfolio/optimizer/universe_from_holdings_actions_test.cljs`, after which that specific namespace-size finding disappeared.

## Decision Log

- Decision: Scope this change to the From holdings action, not the manual asset search flow.
  Rationale: The user requested the From holdings import behavior. Manual search ranking is a separate usability issue from the audit and should be handled independently.
  Date/Author: 2026-05-30 / Codex

- Decision: Superseded on 2026-06-11 - enforce a 25-instrument cap in the action rather than only changing display copy.
  Rationale: The setup UI already says `cap: 25 assets`; importing more than 25 contradicts the UI and creates slow, failure-prone history prefetch.
  Date/Author: 2026-05-30 / Codex

- Decision: Remove the fixed From holdings asset-count cap.
  Rationale: The user explicitly requested no asset-count limit on 2026-06-11. The action should still filter known-unusable history rows and preserve deterministic largest-notional ordering, but it should not truncate otherwise usable holdings.
  Date/Author: 2026-06-11 / Codex

- Decision: Skip holdings that discovery metadata marks as known-unusable, but keep holdings whose history status is unknown.
  Rationale: Discovery may not have loaded when a user clicks From holdings. Unknown rows can still prefetch and prove themselves. Rows already known as missing, rejected, unavailable, unsupported, or disabled should not be selected when building a usable universe.
  Date/Author: 2026-05-30 / Codex

- Decision: Rank selected holdings by absolute current exposure after filtering known-unusable rows.
  Rationale: When a portfolio has more than 25 usable or unknown holdings, the largest exposures are the most representative default universe and avoid randomly selecting tiny dust balances.
  Date/Author: 2026-05-30 / Codex

## Outcomes & Retrospective

Implementation is complete and focused red/green validation is complete. Before implementation, `node out/test.js --test=hyperopen.portfolio.optimizer.universe-actions-test` failed the new test because the action imported all 30 current holdings, included the two holdings whose discovery status was `:missing`, and saved no optimizer-history metadata. After implementation and moving the new coverage into `hyperopen.portfolio.optimizer.universe-from-holdings-actions-test`, the focused test namespace passed with 1 test and 6 assertions, and the original universe action test namespace still passed.

Full `npm test` passed after regenerating the test runner with 659 namespaces. `npm run test:websocket` passed after the production action change. `npm run check` remains blocked by unrelated repository guardrails: `docs/references/hyperliquid-portfolio-history-and-returns.md` is stale, and `docs/exec-plans/active/2026-05-24-rebalance-slippage-snapshot-estimates.md` is an older active ExecPlan with no unchecked progress. A direct `npm run lint:namespace-sizes` also reports pre-existing size-exception overruns in `src/hyperopen/portfolio/optimizer/actions/draft.cljs` and `test/hyperopen/portfolio/optimizer/draft_actions_test.cljs`; the new From holdings test namespace no longer triggers a size finding.

## Context and Orientation

The optimizer draft universe lives at `[:portfolio :optimizer :draft :universe]`. A universe instrument is a small map such as `{:instrument-id "perp:BTC" :market-type :perp :coin "BTC" :shortable? true}`. The setup route renders this vector and the optimizer run pipeline uses it to request history and solve target weights.

`src/hyperopen/portfolio/optimizer/actions/universe.cljs` owns draft-universe mutations. The public action `set-portfolio-optimizer-universe-from-current` builds the universe from `current-portfolio/current-portfolio-snapshot`. That snapshot is pure application data assembled in `src/hyperopen/portfolio/optimizer/application/current_portfolio.cljs`; each exposure includes an optimizer-local `:instrument-id`, a market type, a coin, and notional fields such as `:abs-notional-usdc`.

Optimizer history API discovery is stored under `[:portfolio :optimizer :history-discovery]`. Discovery can map a local optimizer id like `perp:BTC` to a backend history id like `hl:perp:BTC` and can attach a history status such as `:available`, `:missing`, or `:rejected`. The From holdings action should use that metadata when present, but it must still work if discovery has not loaded.

## Plan of Work

First, add/update a failing unit test in `test/hyperopen/portfolio/optimizer/universe_from_holdings_actions_test.cljs`. The test should build a state with 30 current perp positions and history discovery metadata that marks the two largest positions as `:missing`. It should assert that `set-portfolio-optimizer-universe-from-current` saves all 28 usable instruments, skips the known-missing top two positions, preserves descending notional order, and queues history prefetch only for the selected universe. This dedicated namespace keeps the already-large general universe action test namespace from growing further.

Second, update `src/hyperopen/portfolio/optimizer/actions/universe.cljs`. Keep the private helpers for known-unusable history status, absolute notional ranking, and conversion from snapshot exposure to discovery-enriched universe candidate. Change `set-portfolio-optimizer-universe-from-current` to build candidates from current exposures, drop known-unusable candidates, sort the remainder by history priority and descending absolute notional, and save the full remaining instrument set. Preserve the existing Black-Litterman cleanup and history-prefetch behavior for the selected universe.

Third, run focused tests. The new test should fail before implementation and pass after implementation. Existing tests should continue to pass because small portfolios should still import the same instruments and preserve discovery metadata.

Fourth, run the required repository validation gates because this is a production behavior change: `npm run check`, `npm test`, and `npm run test:websocket`. If browser route behavior or UI copy changes later, add the smallest relevant Playwright optimizer assertion and run browser cleanup.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/39cc/hyperopen`.

1. Add the failing test to `test/hyperopen/portfolio/optimizer/universe_from_holdings_actions_test.cljs`.

2. Run the focused test command and confirm the new test fails because the current implementation imports all holdings instead of capping/skipping:

       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.portfolio.optimizer.universe-from-holdings-actions-test

3. Implement the minimal action change in `src/hyperopen/portfolio/optimizer/actions/universe.cljs`.

4. Re-run:

       node out/test.js --test=hyperopen.portfolio.optimizer.universe-from-holdings-actions-test

   Expected after implementation: the namespace exits with code 0 and all assertions pass.

5. Run required gates:

       npm run check
       npm test
       npm run test:websocket

   Expected: all commands exit with code 0. If an unrelated existing docs-review or environment blocker appears, record the exact failure in this plan and in the final response.

## Validation and Acceptance

Acceptance is met when `set-portfolio-optimizer-universe-from-current` has these observable behaviors:

- With fewer than 25 usable holdings, it preserves the existing import behavior.
- With more than 25 usable holdings, it imports only 25.
- With discovery metadata marking holdings as missing, rejected, unavailable, unsupported, or disabled, it skips those holdings.
- With unknown discovery status, it keeps the holding eligible for import so history prefetch can resolve it.
- The selected order is deterministic: usable known-history holdings first, then unknown-history holdings, each group ordered by largest absolute exposure first, with original exposure order as a final tiebreaker.
- The history prefetch queue contains only the selected instruments.

## Idempotence and Recovery

The action change is pure and local. Re-running the tests is safe. If the new ranking policy proves too aggressive, revert only `src/hyperopen/portfolio/optimizer/actions/universe.cljs` and the matching test additions; no persisted migration is involved.

## Artifacts and Notes

Focused RED transcript before implementation:

    Testing hyperopen.portfolio.optimizer.universe-actions-test

    FAIL in (set-draft-universe-from-current-holdings-caps-and-skips-known-unusable-history-test)
    expected: (= 25 (count universe))
      actual: (not (= 25 30))

    Ran 14 tests containing 29 assertions.
    4 failures, 0 errors.

Focused GREEN transcript after implementation:

    Testing hyperopen.portfolio.optimizer.universe-from-holdings-actions-test

    Ran 1 tests containing 6 assertions.
    0 failures, 0 errors.

Full validation transcripts:

    npm test
    Generated test/test_runner_generated.cljs with 659 namespaces.
    Ran 4093 tests containing 22576 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 527 tests containing 3067 assertions.
    0 failures, 0 errors.

    npm run check
    [stale-doc] docs/references/hyperliquid-portfolio-history-and-returns.md - document is stale: 93 days old, max allowed 90
    [active-exec-plan-no-unchecked-progress] docs/exec-plans/active/2026-05-24-rebalance-slippage-snapshot-estimates.md - active ExecPlan has no remaining unchecked progress items; move it out of active

    npm run lint:namespace-sizes
    [size-exception-exceeded] src/hyperopen/portfolio/optimizer/actions/draft.cljs - namespace has 519 lines; exception allows at most 511
    [size-exception-exceeded] test/hyperopen/portfolio/optimizer/draft_actions_test.cljs - namespace has 526 lines; exception allows at most 518

Revision note, 2026-05-30 / Codex: Added red/green action-test evidence after implementing the capped From holdings selection policy.
Revision note, 2026-05-30 / Codex: Updated the plan after moving the new test to a dedicated namespace and after running required validation attempts.
Revision note, 2026-06-11 / Codex: Removed the fixed 25-asset From holdings cap by user request and updated the plan to reflect uncapped import behavior.

## Interfaces and Dependencies

No new public action names or effect names are introduced. The existing public function `hyperopen.portfolio.optimizer.actions.universe/set-portfolio-optimizer-universe-from-current` keeps returning optimizer effects in the same shape as before. The changed private policy is:

- maximum selected From holdings instruments: no fixed asset-count cap
- known-unusable history statuses: `:missing`, `:rejected`, `:unavailable`, `:unsupported`, and `:disabled`
- known-unusable quality statuses: `:failed`, `:rejected`, and `:missing`
- selected sorting: known available history before unknown history, then descending absolute current notional, then original snapshot order

Revision note, 2026-05-30 / Codex: Initial active ExecPlan created from the user's implementation request and the prior optimizer usability audit findings.
