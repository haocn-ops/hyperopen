# Extract optimizer workflow state machines

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current while the work proceeds. This document follows `.agents/PLANS.md` from the repository root.

## Purpose / Big Picture

The `/portfolio/optimize` runtime currently hides important workflow decisions inside nested Promise chains, recursive polling, and a global worker-run atom. After this refactor, the same optimizer behavior should be easier to reason about because workflow decisions will live in pure functions that return command maps, while runtime adapters only interpret those commands.

This is internal boundary work. A human can see it working by running ClojureScript tests that fail before the pure workflow reducers exist and pass after the adapters are rewired without changing public effect signatures.

## Context References

Public refs:

- Direct user/maintainer request on 2026-05-12: assuming “portfolio optimization” means the `/portfolio/optimize` optimizer feature, extract async optimizer workflows into explicit state machines.

Repo artifacts:

- Root operating contract: `AGENTS.md`.
- Planning contract: `.agents/PLANS.md` and `docs/PLANS.md`.
- Multi-agent workflow contract: `docs/MULTI_AGENT.md`.
- Current runtime adapter hotspots: `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_pipeline.cljs`, `src/hyperopen/runtime/effect_adapters/portfolio_optimizer/history.cljs`, `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs`, and `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs`.
- Existing test surfaces: `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_pipeline_test.cljs`, `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_history_test.cljs`, `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios_test.cljs`, and `test/hyperopen/portfolio/optimizer/application/run_bridge_test.cljs`.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-12 16:05Z) Loaded the repo workflow instructions and relevant Superpowers skills for feature-flow, planning, TDD, subagents, worktree detection, and verification.
- [x] (2026-05-12 16:08Z) Confirmed this session is already in an isolated linked worktree at `/Users/barry/.codex/worktrees/3471/hyperopen` on detached HEAD.
- [x] (2026-05-12 16:15Z) Inspected the optimizer pipeline, history prefetch, scenario persistence, worker run bridge, contracts, and existing tests.
- [x] (2026-05-12 16:20Z) Spawned explorer and test-proposal agents; one explorer returned the current async behavior map and reducer boundary candidates, and another returned existing test coverage and suggested missing cases.
- [x] (2026-05-12 16:26Z) Ran the initial ClojureScript baseline command; Shadow compiled the test build, but Node failed before tests because dependencies were not installed in this worktree.
- [x] (2026-05-12 16:27Z) Ran `npm ci` to install locked dependencies after the baseline failed at `Cannot find module 'lucide/dist/esm/icons/external-link.js'`.
- [x] (2026-05-12 16:28Z) Reran the ClojureScript baseline after dependency install; `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js` passed with 3842 tests and 21246 assertions.
- [x] (2026-05-12 16:29Z) Received acceptance and edge-case test proposals and froze a focused contract around pure workflow command planning, stale worker messages, history prefetch failure/termination, and scenario persistence command ordering.
- [x] (2026-05-12 16:34Z) Added RED tests for pure workflow reducers and command plans under `test/hyperopen/portfolio/optimizer/application/*_workflow_test.cljs`.
- [x] (2026-05-12 16:35Z) Verified RED with `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test`; Shadow failed because `hyperopen.portfolio.optimizer.application.history-workflow` was not available.
- [x] (2026-05-12 16:54Z) Implemented pure workflow reducers for pipeline, history, scenario save, and worker run bridge; rewired the corresponding runtime adapters as interpreters.
- [x] (2026-05-12 16:56Z) Ran `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`; it passed with 3853 tests and 21285 assertions.
- [x] (2026-05-12 20:06Z) Resumed the plan in the current isolated worktree at `/Users/barry/.codex/worktrees/ac6d/hyperopen` to finish the remaining interpreter cleanup called out by the maintainer: nested Promise sequencing in `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs` around line 95 and `src/hyperopen/runtime/effect_adapters/portfolio_optimizer/execution.cljs` around line 141.
- [x] (2026-05-12 20:06Z) Ran the focused baseline command for scenario workflow, scenario adapter, execution planner, and execution adapter tests. The first attempt compiled but Node failed to start because this worktree had no installed `lucide` package under `node_modules`.
- [x] (2026-05-12 20:06Z) Ran `npm ci` in the current worktree, then reran the focused baseline. It passed with 21 tests, 115 assertions, 0 failures, and 0 errors.
- [x] (2026-05-12 20:08Z) Added RED tests for `scenario-workflow/advance-command-result` and the new `execution-workflow` command planning boundary. The focused command failed as intended because `hyperopen.portfolio.optimizer.application.execution-workflow` does not exist yet.
- [x] (2026-05-12 20:16Z) Implemented the remaining command interpreters. Scenario persistence now advances through a single `interpret-result!` / `interpret-command!` path, and execution ledger persistence now uses `application/execution_workflow.cljs` for record/index command planning.
- [x] (2026-05-12 20:16Z) Reran the focused command after implementation. It passed with 25 tests, 127 assertions, 0 failures, and 0 errors.
- [x] (2026-05-12 20:25Z) Ran required validation gates. `npm run check` passed, `npm test` passed with 3,862 tests and 21,320 assertions, and `npm run test:websocket` passed with 524 tests and 3,043 assertions.

## Surprises & Discoveries

- Observation: The optimizer namespace-size debt has already been retired, so this plan should avoid broad file splitting and instead target the remaining async workflow boundaries.
  Evidence: Recent commit `0338bc1b` is `refactor: retire optimizer namespace size exceptions`, and the user explicitly identified boundary/workflow clarity as the remaining debt.
- Observation: The first baseline compile succeeded but the Node test runner could not start because this worktree lacked installed dependencies.
  Evidence: `npx shadow-cljs --force-spawn compile test` completed with `0 warnings`, then `node out/test.js` failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`.
- Observation: Existing scenario state transitions are already pure in `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenario_state.cljs`, but the persistence workflow ordering remains embedded in nested Promise chains.
  Evidence: `portfolio_optimizer_scenarios.cljs` re-exports state helpers from `portfolio_optimizer_scenario_state.cljs`, while load, save, archive, duplicate, and manual-tracking functions sequence persistence calls inline.
- Observation: The worker bridge has good behavioral tests, but the pure decision boundary is private and the global `last-run-request` dedupe can block same-signature retry after a failed run unless an explicit run id bypasses it.
  Evidence: Existing `request-run-dedupes-identical-in-flight-signature-test` covers in-flight dedupe, while the test explorer proposed `request-run-retries-identical-signature-after-failed-run-test` as missing coverage.
- Observation: The earlier state-machine pass extracted pure scenario workflow functions, but the runtime scenario adapter still owns sequencing through nested `.then` blocks. Execution ledger persistence still constructs the record/index persistence plan inside `portfolio_optimizer/execution.cljs`.
  Evidence: `portfolio_optimizer_scenarios.cljs` still nests `load-scenario!`, `load-tracking!`, `load-scenario-index!`, `save-scenario!`, and `save-scenario-index!` continuations. `portfolio_optimizer/execution.cljs` still loads the scenario, loads the index, appends the ledger, saves both records, and mutates persistence state inside `persist-execution-ledger!`.

## Decision Log

- Decision: Keep public effect function names and Promise-returning behavior stable while extracting pure reducer functions behind them.
  Rationale: Existing runtime registration, facade tests, and action effects call these adapters directly. Changing those APIs would increase blast radius without improving workflow clarity.
  Date/Author: 2026-05-12 / Codex.
- Decision: Use small workflow namespaces under `src/hyperopen/portfolio/optimizer/application/` instead of introducing a generic workflow framework.
  Rationale: The debt is localized to optimizer adapters. A command vocabulary per workflow keeps behavior explicit and testable without adding a new cross-app abstraction.
  Date/Author: 2026-05-12 / Codex.
- Decision: Treat this refactor as non-UI-facing for browser QA.
  Rationale: No view, CSS, browser storage, or interaction flow behavior is intended to change. Deterministic ClojureScript tests and required repo gates are the appropriate validation path.
  Date/Author: 2026-05-12 / Codex.
- Decision: Continue the existing optimizer workflow-state-machine ExecPlan instead of creating a second active plan for the same cleanup.
  Rationale: The current maintainer request is explicitly a continuation of this plan's unfinished interpreter cleanup. Updating the active plan avoids duplicate implementation artifacts and follows `docs/PLANS.md`.
  Date/Author: 2026-05-12 / Codex.

## Outcomes & Retrospective

Complete. The optimizer workflow decisions named in this plan now live in pure workflow namespaces or small command planners, while runtime adapters interpret command maps and own only browser persistence, worker, order submission, and dispatch effects. The resumed cleanup specifically removed the branch-specific nested persistence planning from `portfolio_optimizer_scenarios.cljs` and `portfolio_optimizer/execution.cljs`. Overall complexity decreased at the behavior boundary because the state transitions and persistence order are now visible in tests as data, even though the adapters still necessarily contain Promise code to interpret effects. Remaining Promise chains are effect-level order submission or one-command interpreter calls, not hidden scenario or ledger planning.

## Context and Orientation

The portfolio optimizer stores application data under `[:portfolio :optimizer]`. Shared path constants live in `src/hyperopen/portfolio/optimizer/contracts.cljs`. Runtime effect adapters live under `src/hyperopen/runtime/effect_adapters/**`; they are allowed to touch browser APIs, worker clients, persistence, and network functions. Application workflow namespaces under `src/hyperopen/portfolio/optimizer/application/**` should remain pure: given ordinary maps and event data, they return a new state and command maps, but they do not call Promise APIs, mutate atoms, post worker messages, or read global state.

The four debt hotspots are independent but related. The pipeline adapter decides whether to fail, run immediately, wait for active history prefetch, or load history before running. The history adapter drains one queued selection prefetch at a time and applies success or failure while guarding stale request signatures. The scenario adapter sequences persistence calls for index load, scenario load, archive, duplicate, manual-tracking enablement, and save. The worker run bridge dedupes run requests, installs a worker handler, posts worker messages, applies progress, and ignores stale results.

## Plan of Work

First, establish test-first coverage for the command boundary. Add tests that call pure workflow functions directly and assert command maps for the important branches: pipeline waits for active prefetch instead of loading immediately, selection prefetch starts the next queued request and terminates when no queue remains, scenario save and archive produce explicit persistence command sequences, and run bridge start/message reducers return worker commands while preserving stale-response guards.

Second, add pure workflow namespaces under `src/hyperopen/portfolio/optimizer/application/`. `pipeline_workflow.cljs` should own branch planning and progress state updates for pipeline runs. `history_workflow.cljs` should own history load and selection-prefetch state transitions plus commands for requesting bundles. `scenario_workflow.cljs` should wrap existing scenario state reducers and build persistence command steps. `run_bridge_workflow.cljs` should own run dedupe, run state transitions, stale message detection, and worker command production.

Third, rewire the runtime adapters as interpreters. Each adapter should read the store, call the workflow reducer, `swap!` only through the returned state update, and interpret commands with the existing environment functions. Promise chaining may remain in interpreters where an effect truly depends on an earlier result, but branch decisions and state transitions should be in pure functions that tests can exercise without timers, workers, or persistence.

Fourth, preserve compatibility. Keep `run-portfolio-optimizer-pipeline-effect`, `load-portfolio-optimizer-history-effect`, `save-portfolio-optimizer-scenario-effect`, `request-run!`, and `handle-worker-message!` signatures stable. Keep existing dynamic vars in `portfolio_optimizer.cljs` so tests and facade wiring continue to work.

Fifth, run validation. Because code changes touch optimizer runtime behavior, required gates are `npm run check`, `npm test`, and `npm run test:websocket`. If any browser-flow tooling changes unexpectedly, run the smallest relevant Playwright command, but none is planned.

The resumed cleanup adds one final interpreter boundary pass. In `src/hyperopen/portfolio/optimizer/application/scenario_workflow.cljs`, add a small pure command-step helper that can advance a scenario persistence plan from one completed command to the next. It should not know about Promises, stores, or browser persistence APIs. In `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs`, replace the nested Promise blocks with a single command interpreter that dispatches on `:command/type` and chains only by interpreting the next explicit command returned by the workflow layer. In `src/hyperopen/portfolio/optimizer/application/execution_workflow.cljs`, add pure helpers that build an execution ledger, apply ledger state, plan ledger persistence from a loaded scenario record and loaded index, and apply persistence success or failure. In `src/hyperopen/runtime/effect_adapters/portfolio_optimizer/execution.cljs`, keep order submission as an effect interpreter, but move scenario record/index planning out of the Promise callback and interpret the execution persistence commands explicitly.

## Concrete Steps

All commands run from `/Users/barry/.codex/worktrees/3471/hyperopen`.

The resumed cleanup commands run from `/Users/barry/.codex/worktrees/ac6d/hyperopen`.

The focused baseline for the resumed cleanup is:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.scenario-workflow-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-scenarios-test --test=hyperopen.portfolio.optimizer.application.execution-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-execution-test

Observed before installing dependencies in this worktree:

    [:test] Build completed. (1631 files, 1630 compiled, 0 warnings, 20.25s)
    Error: Cannot find module 'lucide/dist/esm/icons/external-link.js'

After `npm ci`, observed focused baseline:

    Ran 21 tests containing 115 assertions.
    0 failures, 0 errors.

The RED tests for the resumed cleanup should fail before implementation because there is no execution persistence workflow namespace or because the scenario interpreter helper does not yet expose the command advancement shape asserted by the tests. After implementation, the same focused command should pass before running the broader required gates.

Observed RED validation:

    The required namespace "hyperopen.portfolio.optimizer.application.execution-workflow" is not available, it was required by "hyperopen/portfolio/optimizer/application/execution_workflow_test.cljs".

Observed focused GREEN validation:

    Ran 25 tests containing 127 assertions.
    0 failures, 0 errors.

Install dependencies if the worktree has no usable `node_modules`:

    npm ci

Observed on this worktree:

    added 335 packages, and audited 336 packages in 3s

Run the baseline before adding RED tests:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

Expected baseline after dependency install: the command exits with code 0 and reports zero ClojureScript failures and errors.

Observed baseline after dependency install:

    Ran 3842 tests containing 21246 assertions.
    0 failures, 0 errors.

Add RED tests in the relevant test files, then rerun:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

Expected before implementation: failures name missing workflow functions or mismatched command maps in the new reducer tests.

Observed RED validation:

    The required namespace "hyperopen.portfolio.optimizer.application.history-workflow" is not available, it was required by "hyperopen/portfolio/optimizer/application/history_workflow_test.cljs".

Implement source changes and rerun:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

Expected after implementation: all ClojureScript tests pass with 0 failures and 0 errors.

Observed GREEN validation:

    Ran 3853 tests containing 21285 assertions.
    0 failures, 0 errors.

Required final gates:

    npm run check
    npm test
    npm run test:websocket

Expected final result: each command exits with code 0. If a command fails for an unrelated pre-existing reason, record the exact output and residual risk before handoff.

Observed final gates:

    npm run check
    Exit code 0. Shadow CLJS app, portfolio, portfolio-worker, portfolio-optimizer-worker, vault-detail-worker, and test compiles completed with 0 warnings.

    npm test
    Ran 3862 tests containing 21320 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

## Validation and Acceptance

Acceptance is met when the optimizer runtime adapters no longer own the main workflow decisions named in the user request. The decisions must be observable in pure tests: command maps describe wait, load, run, request bundle, persist scenario, post worker, and no-op stale message outcomes. The adapter tests must still pass, proving the existing public effect behavior did not change.

The worker run bridge must still dedupe identical in-flight signatures, but the new tests should clarify retry behavior after failed or completed runs. Stale worker result, error, and progress messages must not mutate current state when the run id or active scenario no longer matches.

The history selection-prefetch workflow must still drain queued instruments sequentially and terminate cleanly when active work exists or the queue is empty. Removed instruments must not merge stale history into optimizer data.

The scenario workflows must still persist records and indexes in the same order as today, but their next persistence steps must be represented by explicit pure commands before interpretation.

The resumed cleanup is accepted when the named adapter hotspots no longer contain branch-specific nested Promise chains for scenario load/archive/duplicate/manual-tracking/save or execution ledger persistence. Adapters may still return Promises because they interpret browser persistence and order-submission effects, but the next operation to run must be visible as command data or a small interpreter dispatch, not hidden inside nested planner code in a Promise callback.

## Idempotence and Recovery

The refactor is additive before adapter rewiring. If a new reducer test fails for the wrong reason, keep the test file and correct only the expected command shape before touching source. If rewiring an adapter causes an existing behavior regression, keep the new pure namespace and revert only the adapter hunk before applying a smaller interpreter change.

Running `npm ci`, test generation, Shadow compilation, and Node tests is safe to repeat. Do not change persisted scenario record schemas, optimizer request contracts, worker wire schemas, or browser storage keys in this plan.

The resumed cleanup is designed to be mechanically reversible by restoring only the affected adapter hunks if focused adapter tests fail. Do not delete the existing pure workflow modules while iterating; keep the state-machine tests as the guide for public behavior.

## Artifacts and Notes

Important source hotspots:

    src/hyperopen/runtime/effect_adapters/portfolio_optimizer_pipeline.cljs
      Current pipeline branch decisions and prefetch polling interpreter.

    src/hyperopen/runtime/effect_adapters/portfolio_optimizer/history.cljs
      Current history load state transitions and selection-prefetch drain interpreter.

    src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs
      Current scenario persistence workflows.

    src/hyperopen/portfolio/optimizer/application/run_bridge.cljs
      Current global last-run dedupe atom, worker command posting, and worker message state transitions.

Planned pure workflow modules:

    src/hyperopen/portfolio/optimizer/application/pipeline_workflow.cljs
    src/hyperopen/portfolio/optimizer/application/history_workflow.cljs
    src/hyperopen/portfolio/optimizer/application/scenario_workflow.cljs
    src/hyperopen/portfolio/optimizer/application/run_bridge_workflow.cljs
    src/hyperopen/portfolio/optimizer/application/execution_workflow.cljs

## Interfaces and Dependencies

The pure workflow functions should return maps shaped like:

    {:state next-state
     :commands [{:command/type :optimizer.workflow/some-command
                 ...}]}

Commands are plain data. Interpreters may choose to return Promises, but reducers must not construct Promises or call side effects.

The command vocabulary is intentionally local. Pipeline commands include waiting for history idle, loading history, and requesting a worker run. History commands include requesting a history bundle. Scenario commands include loading and saving scenario persistence records and indexes. Run bridge commands include installing the worker handler and posting a worker run.

For the resumed cleanup, scenario interpreter commands continue to use the existing `:optimizer.workflow/load-scenario`, `:optimizer.workflow/load-tracking`, `:optimizer.workflow/load-scenario-index`, `:optimizer.workflow/save-scenario`, and `:optimizer.workflow/save-scenario-index` maps. Execution ledger persistence should use the same command vocabulary where possible so the runtime adapter can reuse the same persistence interpreter shape.

## Plan Revision Notes

2026-05-12 / Codex: Created the active plan from the maintainer request after inspecting the optimizer runtime adapters, existing tests, recent completed optimizer boundary plans, and subagent findings. The plan keeps public effect APIs stable and moves decision logic into small pure workflow namespaces.

2026-05-12 / Codex: Resumed the active plan for the maintainer's "Finish workflow interpreter cleanup" request. The earlier state-machine pass extracted pure reducers but left nested Promise sequencing in the scenario and execution adapters. This revision narrows the remaining work to explicit command interpreter dispatch for those two adapter hotspots.
