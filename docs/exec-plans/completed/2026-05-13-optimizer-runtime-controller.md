# Remove Singleton Optimizer Runtime State

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `.agents/PLANS.md` from the repository root. It is self-contained so a future agent can continue the refactor without relying on prior chat context.

## Purpose / Big Picture

The portfolio optimizer can currently dedupe and receive worker messages through namespace-level runtime state. `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs` stores the previous run request in a global atom, and `src/hyperopen/portfolio/optimizer/infrastructure/worker_client.cljs` routes worker messages through a singleton message handler. That makes isolated tests and future runtime composition harder because one optimizer runtime can affect another.

After this change, optimizer run state is owned by a controller created for a specific runtime and store. A direct run and a one-click pipeline run that use the same store should share dedupe state, while a separate store should have independent dedupe state and worker message handling. The behavior is visible by running focused ClojureScript tests that fail before the refactor and pass after it.

## Context References

Public refs:

- Direct user/maintainer request on 2026-05-13: remove singleton optimizer runtime state and prefer a per-store/per-runtime controller passed through env.

Repo artifacts:

- `.agents/PLANS.md` defines this ExecPlan format.
- `AGENTS.md` requires complex refactors to use `docs/exec-plans`.
- `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs` owns optimizer worker request interpretation.
- `src/hyperopen/portfolio/optimizer/application/run_bridge_workflow.cljs` is the pure workflow for starting runs and handling worker messages.
- `src/hyperopen/portfolio/optimizer/infrastructure/worker_client.cljs` owns browser Worker construction and postMessage normalization.
- `src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` adapts registered runtime effects to the optimizer application bridge.
- `src/hyperopen/app/effects.cljs` builds runtime-bound effect handlers.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-13T02:17Z) Read the root repo instructions, `docs/PLANS.md`, `.agents/PLANS.md`, current optimizer bridge code, worker client code, runtime effect adapter code, and existing focused tests.
- [x] (2026-05-13T02:17Z) Confirmed this checkout is already an isolated linked worktree at `/Users/barry/.codex/worktrees/34cc/hyperopen` and is on detached `HEAD`.
- [x] (2026-05-13T02:21Z) Added RED tests that demonstrate optimizer dedupe and worker message handling are controller-local instead of namespace-global.
- [x] (2026-05-13T02:27Z) Refactored `run_bridge.cljs` and `worker_client.cljs` so controller state owns the last run request, worker reference, and installed message listener.
- [x] (2026-05-13T02:27Z) Updated runtime effect wiring so app-created optimizer effects share the controller resolver for the runtime and store, including direct runs and pipeline runs.
- [x] (2026-05-13T02:28Z) Ran focused optimizer bridge and runtime adapter tests; 18 tests and 157 assertions passed.
- [x] (2026-05-13T02:34Z) Ran required gates: `npm run check`, `npm test`, and `npm run test:websocket`; all passed.
- [x] (2026-05-13T02:35Z) Completed this plan and prepared it for move from `docs/exec-plans/active/` to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: `run_bridge_workflow.cljs` is already pure and accepts `last-run-request` as input, so the stateful debt is limited to the interpreter bridge and worker-client boundary rather than the workflow itself.
  Evidence: `workflow/start-run` takes `:last-run-request` and returns `:last-run-request` in its result map.

- Observation: Runtime effect handlers are already assembled from a runtime argument in `src/hyperopen/app/effects.cljs`, so this refactor can follow existing factory patterns used by wallet, orders, websocket health, and asset icon runtime handlers.
  Evidence: `runtime-effect-overrides` calls factories such as `effect-adapters/make-api-submit-order` and `effect-adapters/make-refresh-websocket-health` with the current runtime.

- Observation: The first RED run failed for the intended missing controller and factory vars, but `node out/test.js` also failed because `node_modules/lucide` was absent in this worktree.
  Evidence: The focused command reported undeclared vars for `make-controller`, `make-run-portfolio-optimizer`, and `make-run-portfolio-optimizer-pipeline`, then `Error: Cannot find module 'lucide/dist/esm/icons/external-link.js'`. Running `npm install` restored 335 packages.

- Observation: The focused GREEN run passed after moving state into controllers and restoring dependencies.
  Evidence: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.run-bridge-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-test --test=hyperopen.app.effects-test --test=hyperopen.runtime.wiring-test` ended with `Ran 18 tests containing 157 assertions. 0 failures, 0 errors.`

- Observation: The first broad `npm run check` run failed because adding optimizer factory aliases pushed `src/hyperopen/runtime/effect_adapters.cljs` to 509 lines.
  Evidence: `lint:namespace-sizes` reported `[missing-size-exception] src/hyperopen/runtime/effect_adapters.cljs - namespace has 509 lines`. Tightening adjacent facade alias formatting reduced the file to 485 lines, and `npm run lint:namespace-sizes` then passed.

## Decision Log

- Decision: Introduce a small optimizer run controller rather than putting new mutable state into the pure workflow.
  Rationale: The workflow already has the right pure interface. The controller belongs at the interpreter boundary where worker listeners, store mutation, and dedupe memory are side effects.
  Date/Author: 2026-05-13 / Codex

- Decision: Keep the worker client responsible for Worker creation, message normalization, and posting, but remove the singleton message-handler registry from that namespace.
  Rationale: Worker event listeners should be installed by the owning controller so worker messages update the intended store. Normalization and postMessage serialization remain infrastructure details.
  Date/Author: 2026-05-13 / Codex

- Decision: Use runtime-bound factory handlers for registered app effects and keep compatibility arities for direct tests and legacy direct calls.
  Rationale: Registered effects are the production path and can pass the runtime cleanly. Direct adapter calls should still work for tests, but new focused tests should prove the production path is controller-local.
  Date/Author: 2026-05-13 / Codex

## Outcomes & Retrospective

Singleton optimizer runtime state was removed from the optimizer bridge. `run_bridge.cljs` no longer has a namespace-level `last-run-request` atom, and `worker_client.cljs` no longer has a namespace-level message-handler registry. The optimizer bridge now uses a controller containing the store, last-run request atom, worker reference, and handler installation flag. Runtime-created optimizer handlers share a controller resolver so the direct run effect and pipeline run effect use the same controller for a given store.

Validation passed with `npm run check`, `npm test`, and `npm run test:websocket`. The implementation reduces boundary complexity because the pure workflow remains unchanged and mutable runtime state now sits in the interpreter object that owns the side effect. There is a small additional controller abstraction, but it replaces more surprising global state and makes tests and future runtime composition clearer.

## Context and Orientation

The portfolio optimizer run path has a pure workflow and a side-effecting interpreter. A pure workflow is code that receives data and returns data without mutating browser state. In this repo, `src/hyperopen/portfolio/optimizer/application/run_bridge_workflow.cljs` starts optimizer runs, dedupes identical in-flight requests when supplied with the previous request, and applies worker progress, result, or error messages to app state.

The side-effecting bridge lives in `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs`. It currently has a namespace-level `last-run-request` atom. `request-run!` reads that atom, calls the pure workflow, writes the app store, writes the atom, installs the worker handler, and posts the run request to the browser worker. The browser worker client in `src/hyperopen/portfolio/optimizer/infrastructure/worker_client.cljs` currently has a private singleton `message-handler` atom. Its default `optimizer-worker` delay installs one browser `message` event listener that dispatches to whichever handler is currently stored in that atom.

The desired boundary is a controller. In this plan, a controller is a small map-like object owned by a runtime and store. It contains the store atom to update, an atom holding the last run request for dedupe, a worker reference or delay, and an atom that records whether this controller has installed its worker message listener. Controller state is mutable, but it is no longer global across every optimizer runtime.

Registered app effects are created in `src/hyperopen/app/effects.cljs` through `runtime-effect-overrides`. This function already receives a runtime argument. The optimizer effect adapter in `src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` should expose factory functions that receive that runtime and return handlers. Those handlers should resolve or create the controller for the runtime/store pair and pass it through the env payload to `run_bridge/request-run!` and to the pipeline env.

## Plan of Work

First, add focused RED tests before production edits. Update `test/hyperopen/portfolio/optimizer/application/run_bridge_test.cljs` so tests no longer reset a namespace-level `last-run-request`. Add tests that create two controllers with two stores and fake workers. The first test should start a run on controller A, prove a second identical in-flight request on A dedupes, then start an identical request on controller B and prove it posts because B has independent controller state. The second test should install a fake worker event listener through controller A, invoke that listener with an optimizer result, and prove only controller A's store changes while `system/store` or controller B remains unchanged. Add runtime adapter tests in `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_test.cljs` or `test/hyperopen/app/effects_test.cljs` to show runtime-built handlers share the same controller resolver for a store.

Second, refactor `src/hyperopen/portfolio/optimizer/infrastructure/worker_client.cljs`. Remove the private `message-handler` atom and the default worker delay that depends on it. Add `default-worker-url`, `make-worker!`, and `add-message-listener!`. Keep `normalize-worker-message`, `current-worker`, and `post-run!`. `add-message-listener!` should attach a `message` event listener to a concrete Worker-like object and call the supplied handler with normalized message data.

Third, refactor `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs`. Add `make-controller`, a controller predicate for compatibility arities, and a controller resolver suitable for runtime-bound handlers. `request-run!` should accept `:controller`; it should read and write `(:last-run-request controller)` instead of any namespace-level atom. Installing a worker handler should call `worker-client/add-message-listener!` once per controller and should close over that controller when calling `handle-worker-message!`. Worker result handling should update `(:store controller)`.

Fourth, update `src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` and `src/hyperopen/app/effects.cljs`. Add factory functions such as `make-run-portfolio-optimizer` and `make-run-portfolio-optimizer-pipeline`, or an equivalent shared resolver, so runtime-built effect handlers pass the same per-runtime controller into direct runs and pipeline runs. Keep the existing exported direct functions for facade compatibility and tests.

Finally, run focused tests, update this living plan with discoveries, then run the required repository gates from `AGENTS.md`.

## Concrete Steps

Run all commands from `/Users/barry/.codex/worktrees/34cc/hyperopen`.

1. Add RED tests:

   - Edit `test/hyperopen/portfolio/optimizer/application/run_bridge_test.cljs`.
   - Edit `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_test.cljs`.
   - Edit `test/hyperopen/app/effects_test.cljs` only if runtime factory wiring needs direct coverage there.

2. Verify RED:

       npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.run-bridge-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-test --test=hyperopen.app.effects-test

   Expected before implementation: compile or tests fail because the new controller API and runtime-bound factory behavior do not exist yet, or because duplicate requests still consult the old singleton state.

3. Implement worker-client boundary changes in `src/hyperopen/portfolio/optimizer/infrastructure/worker_client.cljs`.

4. Implement controller-owned interpreter state in `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs`.

5. Implement runtime-bound optimizer effect factories in `src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` and wire them in `src/hyperopen/app/effects.cljs`.

6. Run the focused test command again. Expected after implementation: all named test namespaces pass.

7. Run required gates:

       npm run check
       npm test
       npm run test:websocket

   Expected: each command exits 0. If a command fails, capture the first relevant failure in `Surprises & Discoveries`, fix it if it is caused by this refactor, and rerun the smallest relevant command before broadening again.

## Validation and Acceptance

Acceptance criterion 1: There is no namespace-level `last-run-request` atom in `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs`. Dedupe memory belongs to a controller.

Acceptance criterion 2: `src/hyperopen/portfolio/optimizer/infrastructure/worker_client.cljs` no longer routes messages through a singleton mutable message-handler atom. Message listeners are installed by the owning controller.

Acceptance criterion 3: Two optimizer controllers with identical in-flight request signatures do not affect each other. A duplicate request dedupes within one controller but posts when sent through a different controller/store.

Acceptance criterion 4: A worker result delivered through one controller's installed listener updates that controller's store and does not mutate an unrelated store.

Acceptance criterion 5: Registered runtime effect handlers use a runtime-bound controller path, so direct optimizer runs and one-click pipeline runs for the same store share dedupe state.

Acceptance criterion 6: The required gates `npm run check`, `npm test`, and `npm run test:websocket` pass, or any blocker is documented with evidence and a clear remaining risk.

## Idempotence and Recovery

The test and source edits are ordinary code changes and can be safely rerun. The fake workers in tests should use local atoms to capture posted messages and listeners, so they do not depend on browser globals. If a focused test fails because the generated runner is stale, rerun `npm run test:runner:generate` before compiling the `test` target. If a broad gate fails outside touched optimizer/runtime files, record the failure as a possible pre-existing issue and rerun a focused command to prove this refactor's scope.

Do not run `git pull --rebase` or `git push` during this plan unless the user explicitly requests remote sync in the current session.

## Artifacts and Notes

Current known state before implementation:

    git status --short --branch --untracked-files=normal
    ## HEAD (no branch)

The current implementation has:

    src/hyperopen/portfolio/optimizer/application/run_bridge.cljs
    (defonce last-run-request
      (atom nil))

    src/hyperopen/portfolio/optimizer/infrastructure/worker_client.cljs
    (defonce ^:private message-handler
      (atom nil))

## Interfaces and Dependencies

In `src/hyperopen/portfolio/optimizer/infrastructure/worker_client.cljs`, provide:

    (def default-worker-url "/js/portfolio_optimizer_worker.js")

    (defn make-worker!
      ([] ...)
      ([url] ...))

    (defn add-message-listener!
      [worker handler] ...)

    (defn post-run!
      ([id request] ...)
      ([worker-ref id request] ...))

In `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs`, provide:

    (defn make-controller
      ([] ...)
      ([opts] ...))

    (defn controller-for-store!
      [controllers store] ...)

    (defn request-run!
      [opts] ...)

    (defn handle-worker-message!
      ([message] ...)
      ([controller-or-message message-or-opts] ...)
      ([controller message opts] ...))

The controller should at minimum contain:

    {:store store-atom
     :last-run-request (atom nil)
     :worker-ref worker-or-delay
     :worker-handler-installed? (atom false)}

Plan revision note: 2026-05-13T02:17Z - Initial active ExecPlan created from direct maintainer request and focused source investigation. The plan chooses a controller boundary because the existing workflow is already pure and the remaining debt is interpreter-owned mutable state.

Plan revision note: 2026-05-13T02:28Z - Updated progress and discoveries after RED and focused GREEN runs. The implementation now has controller-local bridge state and runtime-bound optimizer effect factories; broad validation remains.

Plan revision note: 2026-05-13T02:35Z - Recorded successful full validation and completion outcome. This plan is ready to move to `docs/exec-plans/completed/` because all acceptance criteria passed.

Plan revision note: 2026-05-13T02:37Z - Added the namespace-size gate discovery and the final docs-lint verification after moving this plan into `completed/`.
