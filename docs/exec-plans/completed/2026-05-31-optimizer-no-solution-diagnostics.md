# Explain Optimizer No-Solution Results

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

When the optimizer solver returns no usable solution, the UI should explain why rather than showing only `solver-returned-no-solution`. In the current live scenario, the solver result was tagged solved but returned all-zero weights, which violate the requested net exposure and turnover constraints. The app should preserve those validation failures and show the user the specific failed constraints.

## Context References

- Direct user request on 2026-05-31: inspect the live REPL and improve UI debugging for solver no-solution results.
- `src/hyperopen/portfolio/optimizer/application/engine/target_selection.cljs` validates solver outputs and currently collapses all rejected solver results into `:solver-returned-no-solution`.
- `src/hyperopen/views/portfolio/optimize/infeasible_panel.cljs` renders the infeasible banner.

## Progress

- [x] (2026-05-31) Connected to the live Shadow CLJS REPL on port 55147 and inspected the current optimizer run state.
- [x] (2026-05-31) Identified that OSQP returned one `:solved` result with all-zero weights, violating `net-exposure = 1` and turnover constraints.
- [x] (2026-05-31) Add RED coverage for solver-result rejection diagnostics and infeasible-panel copy.
- [x] (2026-05-31) Implement structured rejected-solver-result diagnostics.
- [x] (2026-05-31) Render diagnostic messages in the infeasible banner and highlight net exposure / turnover controls.
- [x] (2026-05-31) Add deterministic Playwright coverage for the seeded solver-rejection UI state.
- [x] (2026-05-31) Run focused and required validation.

## Surprises & Discoveries

- Observation: The run did not fail because there were zero optimizer history rows. The selected 25-asset request had 54 common return observations and only stale-history warnings in selected history.
  Evidence: Live REPL query showed `:history-return-observations 54`, `:history-instrument-count 25`, and `:history-warnings {:stale-history 25}`.

- Observation: The solver result was marked solved, but the returned vector was all zeros.
  Evidence: Live REPL query showed `:solver-status-counts {:solved 1}`, `:weights [0 0 ...]`, `:weight-sum 0`, and an equality diff of `-1` for net exposure target `1`.

- Observation: The current turnover constraint was also violated by the returned zero vector.
  Evidence: Live REPL query showed `:l1 [{:code :gross-exposure, :value 0, :max 1} {:code :turnover, :value 31.313329083687073, :max 2}]`.

## Decision Log

- Decision: Treat solver-result validation failures as first-class infeasibility details.
  Rationale: The app already validates solver outputs before accepting them; the missing piece is preserving the validation failure details for the user and developer.
  Date/Author: 2026-05-31 / Codex

## Outcomes & Retrospective

Implemented solver-result rejection diagnostics in target selection. When every solver result is unusable, the engine now distinguishes generic no-solution results from solved-but-invalid solver outputs and preserves structured validation details under `[:details :violations]`.

The infeasible setup banner now renders the top-level explanation and individual diagnostic messages, while the setup controls highlight the related constraint inputs for gross exposure, net exposure, and turnover violations.

Follow-up UX fix: the banner now renders unique violation-code chips. Frontier sweeps can reject many solver points for the same constraint pair, but repeated `:solver-result-equality-violation` and `:solver-result-turnover-violation` details should not create repeated chips.

Validation completed:

- `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.engine-solver-diagnostics-test --test=hyperopen.views.portfolio.optimize.workspace-view-test` passed: 13 tests, 72 assertions.
- `npm test` passed: 4100 tests, 22613 assertions.
- `npm run test:websocket` passed: 527 tests, 3067 assertions.
- `git diff --check` passed.
- `npm run lint:namespace-boundaries` passed.
- `npx shadow-cljs --force-spawn compile app` passed.
- `npx shadow-cljs --force-spawn compile portfolio-optimizer-worker` passed.
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:8081 PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs -g "explains rejected solver output" --workers=1` passed.
- `npm run check` still stops on pre-existing repository hygiene failures: stale `docs/references/hyperliquid-portfolio-history-and-returns.md` and completed active plan `docs/exec-plans/active/2026-05-24-rebalance-slippage-snapshot-estimates.md`.
- `npm run lint:namespace-sizes` still stops on pre-existing namespace-size exceptions in `src/hyperopen/portfolio/optimizer/actions/draft.cljs` and `test/hyperopen/portfolio/optimizer/draft_actions_test.cljs`.

## Validation and Acceptance

Acceptance is met when:

- A solver result tagged solved but violating constraints returns an infeasible result with diagnostic violations.
- The infeasible UI displays the diagnostic messages, not just a reason token.
- Focused optimizer engine/UI tests pass.
- Required repository validation outcomes are recorded.
