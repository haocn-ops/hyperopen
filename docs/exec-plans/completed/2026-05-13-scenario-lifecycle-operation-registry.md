# Split scenario lifecycle operation registry

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `.agents/PLANS.md` from the repository root. It is self-contained so a future agent can continue the refactor without relying on prior chat context.

## Purpose / Big Picture

Scenario lifecycle work in the portfolio optimizer includes loading saved scenarios, saving the current solved run as a scenario, archiving, duplicating, and enabling manual tracking. Before this refactor, the pure workflow namespace and the runtime Promise interpreter both needed to know which operation types could continue after a loaded scenario record, continue after a loaded scenario index, complete after persistence, or fail. That duplication makes future save/archive/duplicate/tracking changes risky because a maintainer has to inspect both pure code and effect-interpreter code.

After this change, the operation-specific lifecycle decisions live behind a single pure operation registry in `src/hyperopen/portfolio/optimizer/application/scenario_operations.cljs`. The runtime adapter in `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs` remains responsible for Promises and browser persistence calls, but it delegates operation-specific branching to the operation layer. A human can see the refactor working by running focused ClojureScript tests that fail before the registry functions exist and pass after the adapter delegates through them.

## Context References

Public refs:

- Direct user/maintainer request on 2026-05-13: "Split scenario lifecycle workflow/interpreter complexity. scenario_workflow.cljs and portfolio_optimizer_scenarios.cljs mirror the same operation types across large case blocks. Adding save/archive/duplicate/tracking behavior still requires scanning both pure workflow and Promise interpreter paths. Refactor: operation table or per-operation namespaces for load, save, archive, duplicate, manual_tracking."

Repo artifacts:

- `AGENTS.md` requires complex refactors to use `docs/exec-plans`, preserve public APIs, keep websocket runtime decisions pure and deterministic, and run `npm run check`, `npm test`, and `npm run test:websocket` when code changes.
- `.agents/PLANS.md` defines this ExecPlan format and requires living updates.
- `docs/PLANS.md` defines active, completed, and deferred ExecPlan lifecycle.
- `src/hyperopen/portfolio/optimizer/application/scenario_workflow.cljs` is the pure scenario lifecycle workflow namespace.
- `src/hyperopen/portfolio/optimizer/application/scenario_operations.cljs` is the pure operation registry namespace for lifecycle operation dispatch.
- `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs` is the Promise-based runtime interpreter for scenario persistence commands.
- `test/hyperopen/portfolio/optimizer/application/scenario_workflow_test.cljs` covers pure scenario workflow command planning.
- `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios_test.cljs` covers runtime adapter behavior.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-13T19:14Z) Loaded repository instructions, planning docs, relevant Superpowers skills, the scenario workflow namespace, the scenario runtime adapter, and focused tests.
- [x] (2026-05-13T19:14Z) Confirmed this checkout is already an isolated linked worktree at `/Users/barry/.codex/worktrees/84bb/hyperopen` with no local modifications before starting.
- [x] (2026-05-13T19:17Z) Added RED tests for pure operation-registry functions that advance and fail lifecycle operations without adapter-local operation cases.
- [x] (2026-05-13T19:17Z) Ran focused RED validation after installing dependencies; `hyperopen.portfolio.optimizer.application.scenario-workflow-test` reported 11 tests, 34 assertions, 0 failures, and 3 errors for undefined workflow vars.
- [x] (2026-05-13T19:20Z) Implemented the first scenario lifecycle operation registry in `scenario_workflow.cljs` and delegated adapter operation branching through it while preserving existing public effect APIs.
- [x] (2026-05-13T19:20Z) Ran focused GREEN validation; `scenario-workflow-test` and `portfolio-optimizer-scenarios-test` passed with 17 tests, 76 assertions, 0 failures, and 0 errors.
- [x] (2026-05-13T19:23Z) Split the registry into new pure namespace `src/hyperopen/portfolio/optimizer/application/scenario_operations.cljs` after `npm run check` found the expanded workflow namespace exceeded the size gate, then reran focused validation successfully.
- [x] (2026-05-13T19:28Z) Ran required gates: `npm run check`, `npm test`, and `npm run test:websocket`; all passed.
- [x] (2026-05-13T19:28Z) Completed acceptance review and prepared this plan to move to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The current adapter already has a generic `interpret-result!` loop, so the refactor can stay small and avoid a new async framework.
  Evidence: `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs` interprets `:commands` recursively through `interpret-command!`, but still has several operation-type `case` expressions to choose pure workflow continuations.

- Observation: This worktree initially had incomplete `node_modules`, so the first RED attempt reached the intended undeclared-var warnings but Node could not load the generated test runner.
  Evidence: `node out/test.js` first failed with `Error: Cannot find module 'lucide/dist/esm/icons/external-link.js'`. Running `npm ci` added 335 packages, after which the same focused command reached the intended three undefined workflow function errors.

- Observation: Duplicate scenario failure state uses `:source-scenario-id`, not `:scenario-id`.
  Evidence: The first focused GREEN attempt failed only in `operation-registry-fails-duplicate-operation-test`; `scenario_state.cljs` defines `failed-scenario-duplicate-state` with `:source-scenario-id`, and correcting the test expectation made the focused command pass.

- Observation: Keeping the operation registry inside `scenario_workflow.cljs` violated the namespace-size gate.
  Evidence: `npm run check` stopped at `lint:namespace-sizes` with `[missing-size-exception] src/hyperopen/portfolio/optimizer/application/scenario_workflow.cljs - namespace has 664 lines`. Moving the registry into `scenario_operations.cljs` kept the focused workflow and adapter tests passing.

## Decision Log

- Decision: Use an operation registry in `scenario_workflow.cljs` instead of adding per-operation namespaces for this pass.
  Rationale: The operation bodies are small and already live in one pure namespace with focused tests. A registry removes adapter duplication with less file churn than five new namespaces and preserves the existing public pure helper functions.
  Date/Author: 2026-05-13 / Codex.

- Decision: Move the operation registry into `scenario_operations.cljs` after the first implementation hit the namespace-size gate.
  Rationale: The registry is still pure and table-driven, but it is now a real split from both the state-transition workflow namespace and the Promise interpreter. This satisfies the requested lifecycle split while keeping namespace sizes within repository guardrails.
  Date/Author: 2026-05-13 / Codex.

- Decision: Keep all exported effect function names and Promise-returning behavior stable.
  Rationale: Runtime registration and existing adapter tests call `load-portfolio-optimizer-scenario-index-effect`, `load-portfolio-optimizer-scenario-effect`, `save-portfolio-optimizer-scenario-effect`, `archive-portfolio-optimizer-scenario-effect`, `duplicate-portfolio-optimizer-scenario-effect`, and `enable-portfolio-optimizer-manual-tracking-effect` directly.
  Date/Author: 2026-05-13 / Codex.

## Outcomes & Retrospective

Complete. Scenario lifecycle operation dispatch now lives in `src/hyperopen/portfolio/optimizer/application/scenario_operations.cljs`, a pure operation registry that maps `:scenario-index`, `:load`, `:save`, `:archive`, `:duplicate`, and `:manual-tracking` to result-value, failure, completion, and continuation handlers. The runtime Promise adapter still owns persistence calls and Promise sequencing, but it no longer carries the lifecycle operation matrix across separate `case` expressions.

The implementation reduces complexity in the adapter and keeps the workflow state-transition namespace within repository size guardrails. It adds one small pure namespace, which is an acceptable increase in file count because future lifecycle behavior can now be added or reviewed in the operation registry instead of scanning both pure workflow functions and adapter-local operation cases.

## Context and Orientation

The portfolio optimizer stores scenario state under `[:portfolio :optimizer]`. A "pure workflow" in this repository means a namespace that receives ordinary ClojureScript maps and returns ordinary maps without mutating atoms, calling browser storage, or creating Promises. `src/hyperopen/portfolio/optimizer/application/scenario_workflow.cljs` is pure: it marks state as loading/saving, builds command maps such as `{:command/type :optimizer.workflow/load-scenario-index ...}`, and returns results shaped like `{:state next-state :commands [...]}`.

The operation registry in `src/hyperopen/portfolio/optimizer/application/scenario_operations.cljs` is pure. It maps lifecycle operation types to the existing workflow functions in `scenario_workflow.cljs`. The runtime interpreter in `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs` owns side effects. It reads the store atom, calls persistence functions from its env such as `:load-scenario!` and `:save-scenario-index!`, and chains Promises. It should not own scenario lifecycle rules such as "archive continues from loaded record to loaded index" or "manual tracking completes only after saving the updated scenario and index." Those rules belong in the pure operation registry.

The lifecycle operation types in scope are `:scenario-index`, `:load`, `:save`, `:archive`, `:duplicate`, and `:manual-tracking`. The user request called out load, save, archive, duplicate, and manual tracking; `:scenario-index` is included because the same adapter operation dispatch currently handles index-only loads.

## Plan of Work

First, add RED tests in `test/hyperopen/portfolio/optimizer/application/scenario_workflow_test.cljs`. The tests should exercise new operation-registry functions that will replace adapter-local operation cases. One test should prove `operations/continue-after-scenario-record` dispatches an archive operation to an index-load command using only operation data, state, command, record, and completion time. Another should prove `operations/continue-after-scenario-index` dispatches a save operation to ordered scenario and index save commands. A third should prove `operations/fail` maps a duplicate operation error to the duplicate failure state. These tests fail before implementation because the new functions do not exist.

Second, implement the operation registry in `src/hyperopen/portfolio/optimizer/application/scenario_operations.cljs`. Keep existing helper functions in `scenario_workflow.cljs`, such as `begin-save`, `continue-save-after-index`, `complete-archive`, and `fail-duplicate`, intact for compatibility. Add a private map keyed by operation type. Each value should contain only the handlers supported by that operation, for example `:fail`, `:complete`, `:after-scenario-record`, and `:after-scenario-index`. Add public pure functions:

    (defn result-value [operation state] ...)
    (defn fail [operation state error completed-at-ms] ...)
    (defn complete [operation state completed-at-ms] ...)
    (defn continue-after-scenario-record [operation state command scenario-record completed-at-ms] ...)
    (defn continue-after-scenario-index [operation state command loaded-index completed-at-ms] ...)

These functions should look up the handler in the registry and call it. If a handler is missing, they should return `nil` so the interpreter can no-op only for unsupported paths. They must not call Promises or mutate the store.

Third, update `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs`. Remove or shrink adapter-local `case` expressions for operation result values, operation failure, operation completion, and operation continuation. `operation-result-value` should call `scenario-operations/result-value`. `fail-operation-result` should call `scenario-operations/fail`. `complete-operation-result` should call `scenario-operations/complete`. `continue-after-scenario-record` and `continue-after-scenario-index` should call the corresponding operation functions. Keep `apply-result!` and command interpretation side effects in the adapter because they are runtime concerns.

Fourth, run the focused command from the repository root:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.scenario-workflow-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-scenarios-test

Expected after implementation: both focused namespaces pass with zero failures and zero errors.

Finally, run the required gates from `AGENTS.md`:

    npm run check
    npm test
    npm run test:websocket

Expected: each command exits with code 0. If a gate fails because of this refactor, fix the issue and rerun the smallest relevant command before broadening again. If a gate fails for an unrelated pre-existing reason, record the exact evidence in this plan and in the final response.

## Concrete Steps

Run all commands from `/Users/barry/.codex/worktrees/84bb/hyperopen`.

1. Edit `test/hyperopen/portfolio/optimizer/application/scenario_workflow_test.cljs` to add operation-registry RED tests requiring `hyperopen.portfolio.optimizer.application.scenario-operations`.

2. Verify RED:

       npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.scenario-workflow-test

   Expected before implementation: Shadow compile fails or tests fail because `operations/continue-after-scenario-record`, `operations/continue-after-scenario-index`, and `operations/fail` are not defined.

3. Edit `src/hyperopen/portfolio/optimizer/application/scenario_operations.cljs` to add the operation registry and public operation helper functions.

4. Edit `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs` to delegate operation-specific branching through the operation helpers.

5. Run focused GREEN validation:

       npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.scenario-workflow-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-scenarios-test

   Expected after implementation: the command exits 0 with zero failures and zero errors.

6. Run required final gates:

       npm run check
       npm test
       npm run test:websocket

   Expected after implementation: all commands exit 0.

Observed final validation on 2026-05-13:

    npm run check
    Exit code 0. Namespace size, docs, namespace boundary, tooling, release asset, style, dev-server cleanup, and Shadow CLJS app/portfolio/worker/test compiles completed with 0 reported failures.

    npm test
    Ran 3875 tests containing 21370 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

## Validation and Acceptance

Acceptance criterion 1: `src/hyperopen/portfolio/optimizer/application/scenario_operations.cljs` exposes pure operation helper functions backed by a single operation registry. The new tests demonstrate archive record continuation, save index continuation, and duplicate failure dispatch without requiring the runtime adapter.

Acceptance criterion 2: `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs` no longer mirrors the lifecycle operation matrix across separate large `case` expressions for result values, failure, completion, and continuation. It should interpret Promises and persistence commands, then delegate lifecycle decisions to `scenario_operations.cljs`.

Acceptance criterion 3: Existing effect APIs and behavior remain stable. The focused runtime adapter tests pass, proving save, load, archive, duplicate, and index-load effects still mutate store state and call persistence functions in the expected order.

Acceptance criterion 4: The required gates `npm run check`, `npm test`, and `npm run test:websocket` pass, or any blocker is documented with evidence and residual risk.

## Idempotence and Recovery

The refactor is ordinary source and test editing and can be repeated safely. Test runner generation is safe to rerun. If focused tests fail after implementation, first inspect whether the failure is in pure workflow dispatch or adapter Promise sequencing. The adapter should remain reversible by restoring only the delegated helper calls, because the existing public pure functions stay intact.

Do not change persisted scenario record schemas, browser storage keys, runtime effect IDs, action IDs, optimizer worker messages, or public route behavior in this plan. Do not run `git pull --rebase` or `git push` unless the user explicitly requests remote sync in the current session.

## Artifacts and Notes

Current known state before implementation:

    git status --short
    <empty output>

The current adapter operation duplication exists in:

    src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs
    operation-result-value
    fail-operation-result
    complete-operation-result
    continue-after-scenario-record
    continue-after-scenario-index

The workflow namespace still contains pure operation-specific state transition functions such as:

    continue-save-after-index
    fail-save
    continue-archive-after-record
    continue-archive-after-index
    complete-archive
    fail-archive
    continue-duplicate-after-record
    continue-duplicate-after-index
    complete-duplicate
    fail-duplicate
    continue-manual-tracking-after-record
    continue-manual-tracking-after-index
    complete-manual-tracking
    fail-manual-tracking

## Interfaces and Dependencies

In `src/hyperopen/portfolio/optimizer/application/scenario_operations.cljs`, provide:

    (defn result-value
      [operation state])

    (defn fail
      [operation state error completed-at-ms])

    (defn complete
      [operation state completed-at-ms])

    (defn continue-after-scenario-record
      [operation state command scenario-record completed-at-ms])

    (defn continue-after-scenario-index
      [operation state command loaded-index completed-at-ms])

The `operation` argument is a map containing at least `:operation/type` and operation context such as `:scenario-id`, `:address`, `:duplicated-scenario-id`, `:loaded-scenario-record`, `:scenario-record`, `:scenario-index`, and `:started-at-ms`. The `command` argument is the current command map being interpreted. The functions return a workflow result map such as `{:state state :commands [...]}` or `nil` for unsupported paths.

Plan revision note: 2026-05-13T19:14Z - Initial active ExecPlan created from direct maintainer request and source investigation. The plan chooses a single operation registry because it directly removes mirrored interpreter operation cases while keeping the existing workflow functions and public effect APIs stable.

Plan revision note: 2026-05-13T19:23Z - Revised the implementation location from `scenario_workflow.cljs` to `scenario_operations.cljs` after the namespace-size lint gate failed. This preserves the registry design while satisfying repository size guardrails.

Plan revision note: 2026-05-13T19:28Z - Recorded successful required validation and completion outcome. This plan is ready to move to `docs/exec-plans/completed/`.
