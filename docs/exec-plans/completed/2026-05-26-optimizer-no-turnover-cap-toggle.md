# Optimizer No Turnover Cap Toggle

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan follows `/.agents/PLANS.md` from the repository root. It must remain self-contained enough for a future contributor to continue the work without reading prior chat context.

## Purpose / Big Picture

The portfolio optimizer can fail a valid-looking scenario when the user's current portfolio is much larger than the optimizer's target capital base and the default turnover cap remains enabled. A user should be able to explicitly say "do not constrain turnover for this optimization" from the Constraints section, rerun the scenario, and let the solver choose a target subject to the remaining exposure, per-asset, and risk/return constraints.

After this change, the setup route at `/portfolio/optimize/new` will show a Turnover cap switch. When the switch is off, the optimizer draft stores `:max-turnover nil`, request building preserves that nil value, and solver-plan construction omits the turnover L1 constraint. When the switch is on, the existing numeric turnover cap input continues to behave as it does now.

## Context References

Public refs:
- Direct user/maintainer request on 2026-05-26: "Can we satisfy this by having a switch for no turnover cap in the constraint section?" followed by approval to create this execution plan and implement it.

Repo artifacts:
- `AGENTS.md` requires ExecPlans for risky UI work, TDD for committed behavior changes, Playwright or governed browser QA accounting for UI changes, and required gates when code changes.
- `docs/PLANS.md` and `/.agents/PLANS.md` define the active ExecPlan format and lifecycle.
- `docs/FRONTEND.md`, `docs/BROWSER_TESTING.md`, `docs/agent-guides/ui-foundations.md`, and `docs/agent-guides/browser-qa.md` govern UI interaction and browser QA expectations.

Local scratch refs (non-authoritative):
- Live Shadow nREPL port `56205` was used to inspect the current failure. The captured request had current weights about `[3.2719, 1.1552, 5.7908]`, target net exposure `1.0`, max asset weight `0.5`, and draft max turnover `1.0`, which becomes solver turnover cap `2.0`. The minimum possible turnover to move from the current weights to any target summing to `1.0` is about `9.2179`, so the turnover constraint makes the scenario infeasible.

## Progress

- [x] (2026-05-26 19:29Z) Confirmed the live failure is caused by the turnover cap, not by missing OSQP setup or history warnings.
- [x] (2026-05-26 19:29Z) Chose an explicit Constraints-section toggle that stores `:max-turnover nil` when disabled.
- [x] (2026-05-26 19:29Z) Created this active ExecPlan.
- [x] (2026-05-26 20:04Z) Added RED tests proving nil max turnover is accepted by request normalization, omits the solver turnover L1 constraint, and renders a UI switch with correct actions.
- [x] (2026-05-26 20:05Z) Implemented the minimal production changes in draft actions and setup constraint controls.
- [x] (2026-05-26 20:08Z) Ran the ClojureScript test suite for the changed behavior; the post-implementation run passed.
- [x] (2026-05-26 20:20Z) Ran required repository gates or recorded blockers with exact command output.
- [x] (2026-05-26 20:25Z) Updated this ExecPlan with outcomes and moved it to completed.

## Surprises & Discoveries

- Observation: The failure reason `:solver-returned-no-solution` was a downstream target-selection symptom. The solver results reported `:solved` but returned decoded weights `[0 0 0]`, which fail Hyperopen's feasibility check because net exposure must equal `1.0`.
  Evidence: `src/hyperopen/portfolio/optimizer/application/engine/target_selection.cljs` only accepts results that are `:solved`, finite, have the expected weight count, and satisfy bounds, equalities, inequalities, and L1 constraints.

- Observation: The request builder and solver-plan path already support a missing turnover cap naturally. `constraints?` allows finite fields to be nil, and `objectives/l1-constraints` only emits a turnover constraint when `:max-turnover` is a finite number.
  Evidence: `src/hyperopen/portfolio/optimizer/domain/objectives.cljs` checks `(finite-number? max-turnover)` before adding `{:code :turnover ...}`.

- Observation: The first `npm test` attempt was blocked before assertions by incomplete local dependencies: Node could not resolve `lucide/dist/esm/icons/external-link.js`. Running `npm install` restored the package tree without changing tracked package files.
  Evidence: `npm ls lucide --depth=0` initially returned `(empty)`, then `npm install` added local packages with no `package.json` or `package-lock.json` diff.

- Observation: `npm run check` remains blocked by an unrelated active ExecPlan lint issue.
  Evidence: The command failed at `npm run lint:docs` with `[active-exec-plan-no-unchecked-progress] docs/exec-plans/active/2026-05-24-rebalance-slippage-snapshot-estimates.md - active ExecPlan has no remaining unchecked progress items; move it out of active`.

- Observation: The default Playwright webServer path was blocked by an existing listener on `127.0.0.1:8080`, and the existing broader setup regression has stale default-state drift around the Long Only checkbox. A focused turnover-cap Playwright regression passed against this worktree's compiled static bundle on `127.0.0.1:18080`.
  Evidence: `npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "portfolio optimizer setup exposes separate model layers"` first failed on port occupancy, then later reached an unrelated `longOnly` expectation. The focused command `PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "portfolio optimizer setup turnover cap switch" --workers=1` passed.

## Decision Log

- Decision: Represent "no turnover cap" as `:max-turnover nil` in the optimizer draft and request payload.
  Rationale: This is the smallest state change that uses the existing solver-plan semantics. It avoids adding a second boolean that could drift from the numeric cap.
  Date/Author: 2026-05-26 / Codex

- Decision: Keep the default turnover cap enabled at `1.0`.
  Rationale: The default is a safety guard for normal portfolio runs. The user asked for an explicit switch, not a global weakening of defaults.
  Date/Author: 2026-05-26 / Codex

- Decision: Keep rebalance tolerance separate from turnover cap.
  Rationale: Rebalance tolerance controls output preview actionability. Turnover cap constrains the solver search space. Disabling one should not silently change the other.
  Date/Author: 2026-05-26 / Codex

## Outcomes & Retrospective

Completed on 2026-05-26. The setup Constraints section now renders a Turnover cap switch. Turning it off dispatches `[:actions/set-portfolio-optimizer-constraint :max-turnover nil]`, disables the numeric turnover input, and shows `no cap`. Turning it back on restores the default cap value `1.0`.

The draft action path now allows `nil` only for `:max-turnover`; other numeric constraints still ignore invalid or nil inputs. Request building preserves nil max turnover, and solver-plan construction omits the turnover L1 constraint when the cap is nil.

Validation:
- `npm test`: passed, 4091 tests / 22567 assertions.
- `npm run test:websocket`: passed, 527 tests / 3067 assertions.
- `npx shadow-cljs --force-spawn compile app`: passed.
- `npm run css:build`: passed, with existing Browserslist age warnings.
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "portfolio optimizer setup turnover cap switch" --workers=1`: passed, 1 test.
- `BROWSER_INSPECTION_CONFIG=tmp/browser-inspection-static-config.json node tools/browser-inspection/src/cli.mjs design-review --targets portfolio-optimizer-route`: passed. Artifact: `/Users/barry/.codex/worktrees/d5d5/hyperopen/tmp/browser-inspection/design-review-2026-05-26T20-18-59-652Z-28d8c341`. All six browser-QA passes were `PASS` across `review-375`, `review-768`, `review-1280`, and `review-1440`.
- `npm run browser:cleanup`: passed and stopped `sess-1779826492444-00e21e`.
- `npm run check`: blocked by the unrelated active ExecPlan docs lint issue recorded in Surprises & Discoveries.

## Context and Orientation

This repository is a ClojureScript application using Replicant for UI rendering. Optimizer draft state lives under `[:portfolio :optimizer :draft]`. A draft has a `:constraints` map that currently includes `:max-turnover 1.0` by default. Draft actions in `src/hyperopen/portfolio/optimizer/actions/draft.cljs` parse UI event payloads and return Nexus-style effects such as `[:effects/save path value]`.

The setup Constraints UI is rendered by `src/hyperopen/views/portfolio/optimize/setup_constraint_controls.cljs`. It currently renders text inputs for `:max-turnover` and other numeric constraints through `constraint-row`.

Request building is handled by `src/hyperopen/portfolio/optimizer/application/request_builder.cljs`. It normalizes draft constraints into solver request constraints. Solver-plan construction happens in `src/hyperopen/portfolio/optimizer/domain/objectives.cljs`. An "L1 constraint" means a constraint on an absolute-value sum, such as total absolute exposure or total turnover. In this repo, turnover L1 constraints are emitted only when `:max-turnover` is a finite number.

The tests to update are focused ClojureScript unit tests:
- `test/hyperopen/portfolio/optimizer/application/request_builder_test.cljs` if present, or an adjacent application/domain test if request-builder coverage exists elsewhere.
- `test/hyperopen/portfolio/optimizer/domain/objectives_test.cljs` for solver-plan behavior.
- `test/hyperopen/portfolio/optimizer/actions_test.cljs` or an existing draft/action test if one exists for `set-portfolio-optimizer-constraint`.
- `test/hyperopen/views/portfolio/optimize/setup_view_test.cljs` and/or `test/hyperopen/views/portfolio/optimize/setup_layout_test.cljs` for rendered control and action wiring.

## Plan of Work

First, add RED tests. The request-builder test should construct a draft with `:constraints {:max-turnover nil}` and assert that the built request keeps `[:constraints :max-turnover]` nil or absent rather than converting it back to `1.0`. The objectives test should build an encoded constraint map with `:max-turnover nil` and assert no `:turnover` L1 constraint appears. The UI/action tests should assert that the turnover switch renders, dispatches the shared switch click action, writes `:max-turnover nil` when switched off, and that the turnover input is disabled or visually marked inactive when nil.

Second, implement the minimal production change. In `src/hyperopen/portfolio/optimizer/actions/draft.cljs`, allow `set-portfolio-optimizer-constraint` to save nil specifically for `:max-turnover`. The existing numeric parser rejects nil/blank values for every numeric constraint; this change must stay scoped to turnover cap clearing. In `setup_constraint_controls.cljs`, add a checkbox-style switch for turnover cap enabled state. When enabled, keep the numeric input active with the current value. When disabled, show the current value as blank or disabled and explain "No cap" in compact product copy. The switch should dispatch `[:actions/set-portfolio-optimizer-constraint :max-turnover nil]` when turning off and dispatch `[:actions/set-portfolio-optimizer-constraint :max-turnover 1.0]` when turning on from nil.

Third, run narrow tests. Generate the test runner, compile the test build, and run only the affected namespaces if the runner supports `--test=...`. If targeted names are awkward, run `node out/test.js --test=hyperopen.portfolio.optimizer.domain.objectives-test --test=hyperopen.views.portfolio.optimize.setup-view-test --test=hyperopen.views.portfolio.optimize.setup-layout-test` after compilation.

Fourth, account for UI QA. Because this changes a setup control, run at least the relevant ClojureScript view tests and a browser/design QA pass if the dev app can run in the environment. If a full browser QA matrix is blocked by time or tooling, record each required pass as `BLOCKED` with the reason and keep Playwright/browser cleanup explicit.

Finally, run the required repo gates from `AGENTS.md`: `npm run check`, `npm test`, and `npm run test:websocket`. If these are too long or fail for unrelated existing issues, record the exact command and failure evidence in this plan and in the final response.

## Concrete Steps

All commands run from `/Users/barry/.codex/worktrees/d5d5/hyperopen`.

1. Add failing tests with `apply_patch`.

2. Run the narrow tests to verify RED. Expected result: at least one assertion fails because the switch and nil-saving behavior are not implemented yet.

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.domain.objectives-test --test=hyperopen.views.portfolio.optimize.setup-view-test --test=hyperopen.views.portfolio.optimize.setup-layout-test

3. Implement the scoped action and UI changes with `apply_patch`.

4. Rerun the same narrow command. Expected result: the new tests pass.

5. Run required gates.

    npm run check
    npm test
    npm run test:websocket

6. If browser QA is feasible, run the smallest relevant browser inspection or Playwright route smoke for `/portfolio/optimize/new`, then clean up any browser sessions created.

    npm run browser:cleanup

## Validation and Acceptance

Acceptance is met when a user can open `/portfolio/optimize/new`, expand Constraints, turn off the Turnover cap switch, and run the optimizer without the solver request containing a turnover L1 constraint. Existing defaults remain unchanged: new drafts still start with `:max-turnover 1.0`, and turning the switch back on restores a numeric cap.

Automated acceptance:
- The objectives test proves nil max turnover omits the turnover L1 constraint.
- The action test proves the draft can store nil for `:max-turnover`.
- The setup UI tests prove the switch is visible and wired to nil/restored values.
- Existing optimizer tests continue to pass.

Manual/live acceptance:
- In the same scenario that previously failed because current selected weights summed to about `10.2179`, disabling turnover cap should remove the mathematical conflict. Other constraints may still fail if they are independently infeasible, but turnover should no longer be the blocker.

## Idempotence and Recovery

The changes are additive and scoped. Re-running the tests is safe. If UI tests fail because test helpers cannot locate the new switch, inspect the rendered Hiccup tree with existing `node-by-role`, `change-actions`, and `input-actions` helpers rather than adding new helpers. If the nil draft state violates a contract, update the contract only if the contract currently forbids nil; do not add a separate boolean unless tests prove nil cannot be represented safely.

If the implementation creates a bad local state during manual browser testing, restore the default by toggling Turnover cap back on or by setting `[:portfolio :optimizer :draft :constraints :max-turnover]` to `1.0` through the action path.

## Artifacts and Notes

Live REPL evidence before implementation:

    current weights: [3.2718802450613165 1.1552370954809061 5.790825911308429]
    current weight sum: 10.217943251850652
    target net: 1
    draft max turnover: 1
    solver turnover cap: 2
    mathematical minimum turnover: 9.217943251850652
    feasible under turnover cap?: false

## Interfaces and Dependencies

No new dependencies are required.

`src/hyperopen/portfolio/optimizer/actions/draft.cljs` must continue to expose `set-portfolio-optimizer-constraint` with the same public action id through the runtime catalog. The action must accept existing numeric and boolean updates unchanged, plus the new scoped nil update for `:max-turnover`.

`src/hyperopen/views/portfolio/optimize/setup_constraint_controls.cljs` must continue to expose `constraints-section`. It may add small private helpers for switch rendering if that keeps the file readable.

`src/hyperopen/portfolio/optimizer/domain/objectives.cljs` should not need production changes if tests confirm nil turnover already omits the L1 constraint.

Revision note 2026-05-26: Initial plan created from the live optimizer infeasibility diagnosis and user-approved toggle design.
