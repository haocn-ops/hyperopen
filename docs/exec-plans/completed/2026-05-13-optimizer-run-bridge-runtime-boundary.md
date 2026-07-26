# Optimizer Run Bridge Runtime Boundary Refactor

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document follows `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The portfolio optimizer run bridge currently mixes pure optimizer run workflow decisions with browser runtime side effects. The application namespace `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs` imports the worker client and global app store, creates atoms, installs worker listeners, posts worker messages, and mutates stores. After this refactor, optimizer application code will only describe run workflow transitions through `run_bridge_workflow.cljs`, while the controller and interpreter code that touches workers, clocks, random run IDs, and stores will live in infrastructure/runtime-owned code.

The change is observable through tests: an application boundary test fails while `application/run_bridge.cljs` exists, then passes after the side-effecting controller moves to `portfolio/optimizer/infrastructure/run_bridge.cljs`. The existing run bridge behavior is preserved by moving its integration tests to the infrastructure namespace and keeping runtime effect adapters pointed at the new owner.

## Context References

Public refs:
- Direct maintainer request in this Codex session on 2026-05-13: "Move runtime side effects out of application.run-bridge."

Repo artifacts:
- `/hyperopen/AGENTS.md` requires side effects to stay in interpreters and infrastructure boundaries.
- `/hyperopen/ARCHITECTURE.md` defines application as orchestration/reducer transitions and infrastructure as side-effect interpreters, transport, timers, and integration input/output operations.
- `/hyperopen/src/hyperopen/portfolio/optimizer/BOUNDARY.md` says optimizer application namespaces may emit effect descriptions but must not perform browser, network, IndexedDB, worker-client, websocket, or exchange-submit side effects directly.
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/run_bridge_workflow.cljs` is the pure workflow namespace to keep.
- `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` is the runtime effect adapter that invokes the run bridge.

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-05-13T14:31:15Z) Read the root AGENTS contract, architecture map, optimizer boundary document, planning docs, feature-flow skill, current run bridge implementation, and related tests.
- [x] (2026-05-13T14:31:15Z) Identified that the implementation should move `make-controller`, `make-controller-resolver`, `request-run!`, `handle-worker-message!`, `next-run-id`, worker listener installation, and command interpretation out of `application/run_bridge.cljs`.
- [x] (2026-05-13T14:33:00Z) Added `test/hyperopen/portfolio/optimizer/application/run_bridge_boundary_test.cljs`, a source boundary test that fails while `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs` exists and while `run_bridge_workflow.cljs` imports runtime side-effect owners.
- [x] (2026-05-13T14:35:00Z) Verified the new boundary test failed for the intended reason before changing production code: `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs should be retired`.
- [x] (2026-05-13T14:36:00Z) Created `src/hyperopen/portfolio/optimizer/infrastructure/run_bridge.cljs` with the controller/interpreter code and dependencies formerly in `application/run_bridge.cljs`.
- [x] (2026-05-13T14:36:00Z) Deleted `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs` so `run_bridge_workflow.cljs` is the only run bridge application namespace.
- [x] (2026-05-13T14:36:00Z) Retargeted `src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` to the infrastructure run bridge namespace.
- [x] (2026-05-13T14:36:00Z) Moved `test/hyperopen/portfolio/optimizer/application/run_bridge_test.cljs` to `test/hyperopen/portfolio/optimizer/infrastructure/run_bridge_test.cljs` and updated its namespace/imports.
- [x] (2026-05-13T14:37:00Z) Regenerated the ClojureScript test runner after test file movement.
- [x] (2026-05-13T14:38:00Z) Ran the focused full CLJS runner and observed `3868 tests containing 21355 assertions. 0 failures, 0 errors.`
- [x] (2026-05-13T14:40:00Z) Ran required repository gates: `npm run check`, `npm test`, and `npm run test:websocket`; all exited 0.
- [x] (2026-05-13T14:40:08Z) Moved this ExecPlan to `docs/exec-plans/completed/` after validation was recorded.

## Surprises & Discoveries

- Observation: The pure workflow split already exists in `run_bridge_workflow.cljs`; the remaining violation is concentrated in the sibling application `run_bridge.cljs`.
  Evidence: `run_bridge_workflow.cljs` returns state and command descriptions, while `run_bridge.cljs` imports `worker-client` and `hyperopen.system`, constructs atoms/delays, calls `worker-client/post-run!`, and mutates stores.

- Observation: The worktree initially had no `node_modules`, so the first post-syntax RED run compiled but stopped before executing tests with a missing `lucide/dist/esm/icons/external-link.js` module.
  Evidence: `node out/test.js` reported `Error: Cannot find module 'lucide/dist/esm/icons/external-link.js'`. Running `npm install` from the existing lockfile installed the expected packages, after which the same command reached the new boundary test.

- Observation: The new boundary test produced the intended RED failure before production changes.
  Evidence: `FAIL in (run-bridge-runtime-side-effects-stay-out-of-application-test)` with the message `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs should be retired; runtime controllers and interpreters belong in optimizer infrastructure or runtime effect adapters.`

## Decision Log

- Decision: Preserve the existing run bridge function names and behavior, but move them from `hyperopen.portfolio.optimizer.application.run-bridge` to `hyperopen.portfolio.optimizer.infrastructure.run-bridge`.
  Rationale: The runtime effect adapter already owns interpretation of optimizer effects and can depend on infrastructure. Keeping names stable inside the new namespace minimizes behavior risk while correcting the dependency direction.
  Date/Author: 2026-05-13 / Codex

- Decision: Add a source boundary test instead of relying only on moved behavioral tests.
  Rationale: Behavioral tests prove the controller still works, but they do not prevent the same side effects from drifting back into application. The boundary test directly guards the architectural requirement from the maintainer request.
  Date/Author: 2026-05-13 / Codex

## Outcomes & Retrospective

Completed. The side-effecting run bridge controller and interpreter now live in `src/hyperopen/portfolio/optimizer/infrastructure/run_bridge.cljs`. The pure workflow remains in `src/hyperopen/portfolio/optimizer/application/run_bridge_workflow.cljs`, and `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs` has been removed. `src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` now imports the infrastructure run bridge, preserving the runtime adapter behavior while correcting the dependency direction.

The change reduced architectural complexity by removing an application namespace that mixed pure workflow and runtime effects. The operational code itself is intentionally close to the original implementation to minimize behavioral risk, but its ownership is now aligned with the optimizer boundary map.

Validation passed:

        npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js
        Ran 3868 tests containing 21355 assertions.
        0 failures, 0 errors.

        npm run check
        Exit 0. Included docs, namespace, tooling, style, release asset checks, and app/portfolio/worker/test Shadow CLJS compiles.

        npm test
        Ran 3868 tests containing 21355 assertions.
        0 failures, 0 errors.

        npm run test:websocket
        Ran 524 tests containing 3043 assertions.
        0 failures, 0 errors.

## Context and Orientation

The portfolio optimizer has a layered structure. "Application" namespaces under `src/hyperopen/portfolio/optimizer/application/` should be deterministic logic: they transform input state and data into output state and effect descriptions. "Infrastructure" namespaces under `src/hyperopen/portfolio/optimizer/infrastructure/` may touch browser and worker APIs, normalize external protocol shapes, and perform integration side effects. Runtime effect adapters under `src/hyperopen/runtime/effect_adapters/` are another approved side-effect boundary.

`run_bridge_workflow.cljs` is already a pure application workflow. `start-run` accepts state, request data, a run id, and a timestamp, then returns updated state and command maps such as `:optimizer.workflow/post-worker-run`. `handle-worker-message` accepts state and a normalized worker message, then returns updated state. It does not need to know how a worker is created, how the app store is stored, or how messages are posted.

`run_bridge.cljs` is the namespace to retire from the application layer. It currently creates a controller map containing a store atom, the last run request atom, a worker reference, and a worker handler installation atom. It generates run IDs from `js/Date` and `rand-int`, installs worker listeners, posts worker messages, and mutates the app store. Those are runtime side effects and belong in infrastructure/runtime code.

`src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` currently imports the application run bridge and calls `request-run!`, `make-controller-resolver`, and `next-run-id`. This file should instead import the new infrastructure run bridge.

## Plan of Work

First, add a focused boundary test under `test/hyperopen/portfolio/optimizer/application/` that reads source files from disk. It should assert that `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs` does not exist and that `run_bridge_workflow.cljs` does not mention runtime side-effect dependencies such as `worker-client`, `hyperopen.system`, `js/Date`, `atom`, `reset!`, `swap!`, `make-worker!`, or `post-run!`. Run the ClojureScript tests and confirm this new test fails because `application/run_bridge.cljs` still exists.

Second, create `src/hyperopen/portfolio/optimizer/infrastructure/run_bridge.cljs`. Move the existing controller/interpreter implementation into that namespace with the same public vars: `next-run-id`, `make-controller`, `make-controller-resolver`, `request-run!`, and `handle-worker-message!`. Keep its dependency on `run_bridge_workflow.cljs`, `worker-client`, and `hyperopen.system`, because those dependencies are now in an approved infrastructure boundary.

Third, delete `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs` and retarget the runtime effect adapter to `hyperopen.portfolio.optimizer.infrastructure.run-bridge`. Move the behavior test file from `application/run_bridge_test.cljs` to `infrastructure/run_bridge_test.cljs`, update the namespace to `hyperopen.portfolio.optimizer.infrastructure.run-bridge-test`, and alias the new infrastructure namespace as `run-bridge`.

Fourth, regenerate `test/test_runner_generated.cljs` so the deleted application test namespace is removed and the new infrastructure test namespace is included.

Fifth, run focused tests first, then the full required gates. Because this is not UI-facing and does not change browser flows or browser-test tooling, no Playwright or Browser MCP validation is required.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/d4ba/hyperopen`.

1. Add the boundary test:

        test/hyperopen/portfolio/optimizer/application/run_bridge_boundary_test.cljs

   Expected RED command:

        npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

   Expected RED observation before implementation: the new test fails with a message stating that `application/run_bridge.cljs` should be retired.

2. Add the new infrastructure owner:

        src/hyperopen/portfolio/optimizer/infrastructure/run_bridge.cljs

   The file should expose the same operational API that the runtime adapter already uses.

3. Delete the old application owner:

        src/hyperopen/portfolio/optimizer/application/run_bridge.cljs

4. Update the runtime adapter import in:

        src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs

   The alias can remain `run-bridge`, but it must resolve to `hyperopen.portfolio.optimizer.infrastructure.run-bridge`.

5. Move and retarget the integration tests:

        test/hyperopen/portfolio/optimizer/infrastructure/run_bridge_test.cljs

6. Regenerate the test runner:

        npm run test:runner:generate

7. Run focused and full validation:

        npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js
        npm run check
        npm test
        npm run test:websocket

## Validation and Acceptance

Acceptance criteria:

The application layer no longer contains `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs`. `src/hyperopen/portfolio/optimizer/application/run_bridge_workflow.cljs` remains pure and does not import or mention worker clients, the global system store, JS clocks, atoms, store mutations, or worker post helpers. Runtime effect adapters still run optimizer worker requests through the moved infrastructure controller. Existing behavior remains intact: request dedupe, per-store controller reuse, worker listener installation, worker result handling, stale message ignores, error handling, and last-successful-run preservation all pass in the moved infrastructure tests.

Required validation commands:

        npm run check
        npm test
        npm run test:websocket

Expected final state: all three commands exit 0. If any command fails, record the failing command and relevant error in this plan and do not claim completion.

## Idempotence and Recovery

These edits are safe to repeat. If test runner generation creates an unexpected diff, re-run `npm run test:runner:generate` after confirming the test file paths. If a compile error says the old application run bridge namespace cannot be found, search with `rg "application.run-bridge|application/run_bridge|run-bridge"` and retarget the remaining internal import or test namespace to the new infrastructure namespace. Do not restore `application/run_bridge.cljs`; that would reintroduce the boundary violation.

No remote sync is part of this plan. Do not run `git pull --rebase` or `git push` unless the maintainer explicitly requests it.

## Artifacts and Notes

Important starting evidence:

        src/hyperopen/portfolio/optimizer/application/run_bridge.cljs imports:
        - hyperopen.portfolio.optimizer.infrastructure.worker-client
        - hyperopen.system

        It also creates atoms, delays worker construction, installs worker listeners, posts worker run messages, and calls reset!/swap! on stores.

This plan was created on 2026-05-13 to make the architecture boundary explicit before code movement.

## Interfaces and Dependencies

At the end of the work, `hyperopen.portfolio.optimizer.infrastructure.run-bridge` must provide:

        (next-run-id) => string
        (make-controller) => controller map
        (make-controller {:keys [store worker-ref worker-url]}) => controller map
        (make-controller-resolver) => function from store atom to controller map
        (request-run! {:keys [controller request request-signature computed-at-ms store run-id]}) => run id string or nil
        (handle-worker-message! message) => nil
        (handle-worker-message! controller message) => nil
        (handle-worker-message! controller message {:keys [computed-at-ms]}) => nil

`hyperopen.portfolio.optimizer.application.run-bridge-workflow` remains the pure application API:

        (start-run {:keys [state last-run-request request request-signature run-id computed-at-ms explicit-run-id?]}) => {:state ... :last-run-request ... :run-id ... :commands [...]}
        (handle-worker-message {:keys [state message computed-at-ms]}) => {:state ... :commands []}

The runtime adapter `hyperopen.runtime.effect-adapters.portfolio-optimizer` depends on the infrastructure run bridge. No optimizer domain, application workflow, or view namespace should depend on the infrastructure run bridge for this change.

## Plan Update Notes

- 2026-05-13T14:40:08Z: Moved the plan from active to completed and recorded RED/GREEN validation evidence after implementation. The reason is that all planned code movement, boundary tests, and required gates completed successfully.
