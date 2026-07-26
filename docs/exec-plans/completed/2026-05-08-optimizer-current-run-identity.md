# Prevent stale optimizer runs from appearing current

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document follows `.agents/PLANS.md` from the repository root.

## Purpose / Big Picture

The portfolio optimizer must not show or save a solved result from one optimizer setup as if it belongs to another setup. A user can choose a Black-Litterman "Use my views" run with BTC expected return set positive, then compare it with a max-Sharpe run. After this fix, the optimizer draft, saved scenario view, and efficient frontier only treat a run as current when the run was produced from the same optimizer inputs as the active draft. If a retained result belongs to different inputs, save and "view weights" actions are blocked and the scenario surface marks it stale instead of presenting it as reliable current output.

The live `bd` issue for this work is `hyperopen-rpu1`.

## Progress

- [x] (2026-05-08 21:26Z) Reproduced the code-path risk by reading the save/load and view code: `save-portfolio-optimizer-scenario-from-current` accepts any solved `:last-successful-run`, while setup and scenario detail views only check run status and draft dirtiness.
- [x] (2026-05-08 21:26Z) Created `bd` issue `hyperopen-rpu1` for the stale optimizer run identity bug.
- [x] (2026-05-08 21:29Z) Added failing tests showing stale solved runs were still saveable and visible as current for clean Black-Litterman drafts.
- [x] (2026-05-08 21:32Z) Implemented shared optimizer run identity helpers and wired them into run actions, setup view, scenario detail view, and pipeline request signatures.
- [x] (2026-05-08 21:34Z) Ran the Black-Litterman Playwright regression spec and browser cleanup successfully.
- [x] (2026-05-08 21:36Z) Ran repository gates: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-05-08 21:36Z) Completed this ExecPlan and moved it to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: Scenario load deliberately copies `:saved-run` into the global `[:portfolio :optimizer :last-successful-run]`.
  Evidence: `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenario_state.cljs` in `apply-scenario-load-success` associates `(:saved-run scenario-record)` at that path.
- Observation: Saving currently only checks that the global last successful run is solved, not that it was solved for the active draft.
  Evidence: `src/hyperopen/portfolio/optimizer/actions/run.cljs` has `save-portfolio-optimizer-scenario-from-current` check only `[:portfolio :optimizer :last-successful-run :result :status]`.
- Observation: The setup route hides stale results when `:metadata :dirty?` is true or a run is active, but a mismatched run with a clean draft still appears current.
  Evidence: `src/hyperopen/views/portfolio/optimize/workspace_view.cljs` has `current-solved-run?` check solved status, running status, and draft dirty status only.
- Observation: The first RED test run proved the bug and exposed a test fixture issue.
  Evidence: `npm test` failed in `save-portfolio-optimizer-scenario-from-current-rejects-stale-solved-run-test` because the action emitted `[:effects/save-portfolio-optimizer-scenario]` for a mismatched historical run. It also failed the scenario-detail stale assertions. The workspace test initially hit a singular Black-Litterman preview matrix because the fixture had insufficient one-asset/two-point history, so the fixture was changed to two assets with four price points.

## Decision Log

- Decision: Use an optimizer input identity, not full scenario identity, when deciding whether a result belongs to the active draft.
  Rationale: Saved scenarios can change scenario IDs during save/duplicate/load, but the target allocation is determined by optimizer inputs such as universe, portfolio snapshot, return model, risk model, objective, constraints, execution assumptions, history, and priors. Comparing full run metadata would falsely mark saved scenarios stale because IDs and timestamps can change without changing the optimization.
  Date/Author: 2026-05-08 / Codex.
- Decision: Block stale saves in the action layer as well as hiding UI affordances.
  Rationale: UI hiding alone does not protect keyboard, future, or programmatic action dispatch paths. The action must be the authoritative guard before persisting a scenario record.
  Date/Author: 2026-05-08 / Codex.

## Outcomes & Retrospective

Work is in progress. The expected outcome is a small shared run identity helper plus tests that fail before the change and pass after it. This should reduce behavioral ambiguity by centralizing the definition of a current optimizer result.

Completed. The optimizer now has a shared run identity helper in `src/hyperopen/portfolio/optimizer/application/run_identity.cljs`. A solved result is current only when its optimizer-input signature matches the active draft request, the draft is not dirty, and no run is active. The save action blocks stale or mismatched results, the setup page hides "view weights" and results links for mismatched retained runs, and the scenario detail page marks mismatched results stale and disables save. This reduces complexity by replacing multiple local, weaker checks with one shared definition.

## Context and Orientation

The optimizer stores editable inputs under `[:portfolio :optimizer :draft]`. A run request is built by `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs`, then executed through `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_pipeline.cljs` and `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs`. Successful worker results are stored at `[:portfolio :optimizer :last-successful-run]` with a `:request-signature` and `:result`.

The setup page is rendered by `src/hyperopen/views/portfolio/optimize/workspace_view.cljs`. The saved/draft results page is rendered by `src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs`. The current bug appears when those views and the scenario save action trust `:last-successful-run` because it is solved, even when its request came from a different return model or objective. In the user's screenshots, a max-Sharpe and Black-Litterman scenario show identical weights and frontier, and BTC still has the prior negative expected return, which is consistent with the view rendering a retained non-Black-Litterman result.

## Plan of Work

First, add unit tests that encode the missing invariant. In `test/hyperopen/portfolio/optimizer/actions_test.cljs`, add a test where the active draft is Black-Litterman but `:last-successful-run` contains a solved historical request; saving must emit no effect. Also update the existing solved-run save test so a matching request still saves. In `test/hyperopen/views/portfolio/optimize/workspace_view_test.cljs`, add a clean draft with a mismatched solved run and assert that "View weights" and the setup results link are absent. In `test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs`, add or update coverage so a mismatched run produces the stale banner and disables save.

Second, add a shared helper namespace under `src/hyperopen/portfolio/optimizer/application/` that can build the stored run signature and compare optimizer-input signatures. The input signature should include fields that change the optimization output, such as current portfolio, universe, return model, risk model, objective, constraints, execution assumptions, history, and Black-Litterman prior. It should ignore fields that identify the container rather than the computation, such as scenario id and wall-clock metadata. Reuse this helper from `actions/common.cljs` and `runtime/effect_adapters/portfolio_optimizer_pipeline.cljs` so future run signatures stay consistent.

Third, change `save-portfolio-optimizer-scenario-from-current` to require a solved last run whose optimizer inputs match the request built from the current draft. Change `workspace_view.cljs` and `scenario_detail_view.cljs` to use the same predicate for current-result and stale-state decisions.

Fourth, run the targeted CLJS tests, the existing Black-Litterman Playwright spec, browser cleanup, and the required repository gates.

## Concrete Steps

All commands run from `/Users/barry/.codex/worktrees/eb1c/hyperopen`.

Targeted unit tests:

    npm test

Targeted browser regression:

    npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs
    npm run browser:cleanup

Required gates:

    npm run check
    npm test
    npm run test:websocket

## Validation and Acceptance

Acceptance is met when a clean Black-Litterman draft with BTC set to a positive view cannot save or navigate to a mismatched historical/max-Sharpe run, and the scenario detail page warns that the output is stale if such a mismatch is present. The targeted tests must fail before the implementation and pass after it. The existing Black-Litterman Playwright spec must continue to pass, proving the visible workflow still runs. The repository gates must pass.

Observed validation:

    npm test
    Ran 3797 tests containing 20926 assertions.
    0 failures, 0 errors.

    npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs
    5 passed (55.1s).

    npm run browser:cleanup
    {"ok": true, "stopped": [], "results": []}

    npm run check
    Completed successfully with no reported failures or warnings.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

## Idempotence and Recovery

The code changes are additive and local to optimizer identity checks. Tests can be rerun safely. If a targeted test fails, inspect whether the failure is a deliberate RED-phase failure before implementation or a real regression after implementation. No destructive database, git, or browser state operation is required; `npm run browser:cleanup` is the standard cleanup command for browser sessions.

## Artifacts and Notes

The initial root-cause evidence is the combination of these current functions:

    src/hyperopen/portfolio/optimizer/actions/run.cljs
      save-portfolio-optimizer-scenario-from-current checks only solved status.

    src/hyperopen/views/portfolio/optimize/workspace_view.cljs
      current-solved-run? checks solved status, not running, and not dirty.

    src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs
      scenario-stale? checks only dirty metadata.

This plan was created after the user supplied screenshots on 2026-05-08 showing identical max-Sharpe and Black-Litterman outputs.

The final implementation touched:

    src/hyperopen/portfolio/optimizer/application/run_identity.cljs
    src/hyperopen/portfolio/optimizer/actions/common.cljs
    src/hyperopen/portfolio/optimizer/actions/run.cljs
    src/hyperopen/runtime/effect_adapters/portfolio_optimizer_pipeline.cljs
    src/hyperopen/views/portfolio/optimize/workspace_view.cljs
    src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs
    src/hyperopen/views/portfolio/optimize/results_panel.cljs
    test/hyperopen/portfolio/optimizer/actions_test.cljs
    test/hyperopen/views/portfolio/optimize/workspace_view_test.cljs
    test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs

## Interfaces and Dependencies

Define a helper namespace such as `hyperopen.portfolio.optimizer.application.run-identity` with functions equivalent to:

    build-request-signature [request] -> {:scenario-id ..., :as-of-ms ..., :request ...}
    optimizer-input-signature [request] -> map of optimization-driving request fields
    matching-request? [request last-successful-run] -> boolean
    current-solved-run? [readiness running? draft last-successful-run] -> boolean

The exact function names can change if the implementation reveals a better fit, but the behavior must be shared by actions and views rather than duplicated.
