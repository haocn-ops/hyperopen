# Solver Adapter Split

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`.

## Purpose / Big Picture

The portfolio optimizer currently routes quadratic programs through one infrastructure namespace, `src/hyperopen/portfolio/optimizer/infrastructure/solver_adapter.cljs`. That namespace mixes problem adaptation, split-variable expansion for L1 constraints, quadprog wiring, OSQP wiring, dense-matrix-to-CSC conversion, fallback behavior, and result normalization. After this refactor, a maintainer can change one solver adapter or the shared problem expansion without reading unrelated solver plumbing, while callers keep using the same public facade namespace and functions.

The behavior is observable through tests. Existing solver facade tests must still pass through `hyperopen.portfolio.optimizer.infrastructure.solver-adapter/solve-with-quadprog` and `/solve-with-osqp`. New module-boundary tests must prove that `problem_adapter`, `osqp`, and `fallback` own the split-variable expansion, dense-to-CSC conversion, and fallback result shaping that used to be hidden inside the facade.

## Context References

Public refs:
- Direct user request on 2026-05-13: "High-Value Refactors / Solver adapter split: solver_adapter.cljs (line 1) mixes OSQP, quadprog, L1 split-variable expansion, dense-to-CSC conversion, fallback behavior, and result normalization. Split into problem_adapter, quadprog, osqp, and fallback while keeping the public facade."

Repo artifacts:
- `/hyperopen/AGENTS.md` requires `$feature-flow` for significant refactors and required gates after code changes.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define this ExecPlan format.
- Existing tests: `test/hyperopen/portfolio/optimizer/infrastructure/solver_adapter_test.cljs` and `test/hyperopen/portfolio/optimizer/infrastructure/solver_adapter_parity_test.cljs`.

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-05-13T23:59Z) Verified the workspace is already an isolated linked worktree and switched detached HEAD to branch `codex/solver-adapter-split`.
- [x] (2026-05-13T23:59Z) Inspected `solver_adapter.cljs`, existing solver facade tests, repo planning rules, and test runner generation.
- [x] (2026-05-14T00:00Z) Created this active ExecPlan with the intended split and validation strategy.
- [x] (2026-05-14T00:01Z) Added `solver_adapter_modules_test.cljs`, regenerated the runner, and verified RED: `npx shadow-cljs --force-spawn compile test` failed because `hyperopen.portfolio.optimizer.infrastructure.fallback` was missing.
- [x] (2026-05-14T00:04Z) Extracted shared problem adaptation and objective utilities into `src/hyperopen/portfolio/optimizer/infrastructure/problem_adapter.cljs`.
- [x] (2026-05-14T00:04Z) Extracted quadprog-specific columns, one-indexed conversion, result normalization, and solving into `src/hyperopen/portfolio/optimizer/infrastructure/quadprog.cljs`.
- [x] (2026-05-14T00:04Z) Extracted OSQP-specific CSC conversion, row construction, settings, result normalization, and solving into `src/hyperopen/portfolio/optimizer/infrastructure/osqp.cljs`.
- [x] (2026-05-14T00:04Z) Extracted OSQP-to-quadprog recovery result shaping into `src/hyperopen/portfolio/optimizer/infrastructure/fallback.cljs`.
- [x] (2026-05-14T00:04Z) Replaced `solver_adapter.cljs` with a small public facade that preserves `solve-with-quadprog` and `solve-with-osqp`.
- [x] (2026-05-14T00:05Z) Ran focused solver validation: 18 tests and 77 assertions passed with 0 failures and 0 errors.
- [x] (2026-05-14T00:09Z) Ran required repository gates: `npm run lint:namespace-sizes`, `npm run check`, `npm test`, and `npm run test:websocket` all exited 0. Moved this plan to completed.

## Surprises & Discoveries

- Observation: `solver_adapter.cljs` is under the 500-line namespace-size threshold at 424 lines, so this refactor is about responsibility split and reviewability rather than passing an existing size gate.
  Evidence: `wc -l src/hyperopen/portfolio/optimizer/infrastructure/solver_adapter.cljs` returned `424`.
- Observation: the generated ClojureScript test runner discovers `_test.cljs` files automatically, but the generated runner is tracked and must be refreshed after adding a new test namespace.
  Evidence: `tools/generate-test-runner.mjs` recursively scans `test/hyperopen` and writes `test/test_runner_generated.cljs`.
- Observation: The structural RED phase failed for the target module split before any production extraction.
  Evidence: `npx shadow-cljs --force-spawn compile test` reported `The required namespace "hyperopen.portfolio.optimizer.infrastructure.fallback" is not available`, required by `solver_adapter_modules_test.cljs`.
- Observation: Initial focused test execution was blocked by dependency install drift rather than solver code.
  Evidence: after compile succeeded, `node out/test.js ...` failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`; `npm ls lucide --depth=0` returned `(empty)`. Running `npm install` restored declared dependencies, did not modify `package.json` or `package-lock.json`, and `npm ls lucide --depth=0` then showed `lucide@0.577.0`.

## Decision Log

- Decision: Keep `hyperopen.portfolio.optimizer.infrastructure.solver-adapter` as the only public facade expected by current callers.
  Rationale: Existing runtime and tests import the facade. Preserving it minimizes blast radius while allowing the implementation to move behind focused namespaces.
  Date/Author: 2026-05-13 / Codex.
- Decision: Add a module-boundary test suite before production extraction instead of relying only on existing facade parity tests.
  Rationale: Existing tests prove behavior but not the intended ownership split. New tests that require the target namespaces provide RED evidence for the refactor structure and pin the most important extracted responsibilities.
  Date/Author: 2026-05-13 / Codex.
- Decision: Keep dense-to-CSC conversion public inside the OSQP adapter namespace.
  Rationale: Dense-to-CSC conversion is explicitly called out by the request as a distinct responsibility and can be tested without invoking the OSQP native solver.
  Date/Author: 2026-05-13 / Codex.
- Decision: Do not spawn repo workflow subagents in this session.
  Rationale: The repo feature-flow describes subagent orchestration, but the current higher-priority Codex instruction only permits spawning agents when the user explicitly asks for sub-agents or parallel agent work. The parent thread will carry out the same phases locally and record the deviation here.
  Date/Author: 2026-05-13 / Codex.

## Outcomes & Retrospective

The solver adapter split is complete. The public facade `hyperopen.portfolio.optimizer.infrastructure.solver-adapter` now contains only `solve-with-quadprog` and `solve-with-osqp`, delegating to focused solver namespaces. Shared split-variable expansion, unsupported L1 constraint checks, diagonal epsilon stabilization, and objective calculation live in `problem_adapter.cljs`. Quadprog-specific conversion and normalization live in `quadprog.cljs`. OSQP-specific CSC conversion, row construction, settings, and async solving live in `osqp.cljs`. OSQP failure recovery result shaping lives in `fallback.cljs`.

The implementation reduced complexity by replacing one mixed 424-line namespace with five smaller source namespaces, each under 207 lines, while preserving public solver behavior. The new module-boundary suite covers the responsibilities explicitly called out in the request, and existing facade/parity tests still pass through the old public namespace. No caller imports outside the solver adapter changed.

## Context and Orientation

The portfolio optimizer represents quadratic optimization requests as Clojure maps. A problem has `:instrument-ids`, a quadratic matrix in `:quadratic`, a linear vector in `:linear`, equality constraints in `:equalities`, inequality constraints in `:inequalities`, optional L1 constraints in `:l1-constraints`, and per-instrument bounds in `:lower-bounds` and `:upper-bounds`.

L1 constraints are constraints involving absolute values, such as gross exposure or turnover. Quadratic programming solvers in this code path do not directly accept absolute values, so the adapter expands each original weight into positive and negative split variables. For turnover, it also creates extra positive and negative turnover variables. The expanded problem then has ordinary linear constraints, and the solution is decoded back to original weights by subtracting each negative split variable from its positive partner.

The current facade namespace is `src/hyperopen/portfolio/optimizer/infrastructure/solver_adapter.cljs`, namespace `hyperopen.portfolio.optimizer.infrastructure.solver-adapter`. It currently requires the JavaScript packages `osqp` and `quadprog` directly and defines all helper functions in one file. The new layout should be:

- `src/hyperopen/portfolio/optimizer/infrastructure/problem_adapter.cljs`, namespace `hyperopen.portfolio.optimizer.infrastructure.problem-adapter`. This owns L1 support checks, unsupported-result maps, diagonal epsilon stabilization, split-variable problem expansion, decoding, and shared objective-value calculation.
- `src/hyperopen/portfolio/optimizer/infrastructure/quadprog.cljs`, namespace `hyperopen.portfolio.optimizer.infrastructure.quadprog`. This owns `quadprog` package integration, one-indexed vector and matrix conversion required by `quadprog`, constraint-column translation, quadprog result normalization, and synchronous solving.
- `src/hyperopen/portfolio/optimizer/infrastructure/osqp.cljs`, namespace `hyperopen.portfolio.optimizer.infrastructure.osqp`. This owns `osqp` package integration, typed-array conversion, dense-to-CSC conversion, OSQP row and settings construction, OSQP result normalization, asynchronous solving, and delegation to fallback recovery when OSQP throws.
- `src/hyperopen/portfolio/optimizer/infrastructure/fallback.cljs`, namespace `hyperopen.portfolio.optimizer.infrastructure.fallback`. This owns result shaping when OSQP fails and quadprog is attempted as a fallback.
- `src/hyperopen/portfolio/optimizer/infrastructure/solver_adapter.cljs` remains the compatibility facade exposing `solve-with-quadprog` and `solve-with-osqp`.

The existing test files already exercise long-only, target-return inequality, gross-exposure split variables, turnover split variables, unsupported turnover constraints, and parity between quadprog and OSQP. A new test file, `test/hyperopen/portfolio/optimizer/infrastructure/solver_adapter_modules_test.cljs`, should assert the extracted helpers directly.

## Plan of Work

First, add `solver_adapter_modules_test.cljs` with tests that require `problem-adapter`, `osqp`, and `fallback`. The tests should prove that gross-plus-turnover problems expand to split variable IDs and decode back to original weights, dense matrices become OSQP-compatible CSC typed arrays, and fallback recovery labels solved quadprog fallback results as `:quadprog-fallback` while returning OSQP errors when the fallback result is not solved. Regenerate `test/test_runner_generated.cljs` and run the narrow compile/test command. Before implementation, this should fail because the new namespaces do not exist.

Second, create `problem_adapter.cljs` by moving the shared constants and problem-shaping helpers out of `solver_adapter.cljs`. Public functions needed by solver namespaces should be named `unsupported-l1-constraints`, `unsupported-result`, `add-diagonal-epsilon`, `adapt-problem`, and `objective-value`. Helper functions for split-variable internals can remain private inside `problem_adapter.cljs`.

Third, create `quadprog.cljs` by moving the quadprog-specific implementation out of the facade. Its public function should be `solve`, accepting the same problem map as the old `solve-with-quadprog`. It should call `problem-adapter/unsupported-l1-constraints`, `problem-adapter/adapt-problem`, `problem-adapter/add-diagonal-epsilon`, and `problem-adapter/objective-value`.

Fourth, create `fallback.cljs` with one public function, `recover-osqp-error`. It should accept the original problem, the OSQP error, and a one-argument fallback solve function. It should return the same fallback maps currently produced in `solver_adapter.cljs`: solved fallback results are relabeled to `:solver :quadprog-fallback` and include `:fallback-from :osqp`, `:fallback-reason :solver-error`, and `:fallback-message`; unsolved fallback results produce an OSQP `:error` map with `:fallback-result`.

Fifth, create `osqp.cljs` by moving OSQP-specific code out of the facade. Its public function should be `solve`, accepting the same problem map as the old `solve-with-osqp`. Its public function `dense->csc` should be available for tests. It should call `quadprog/solve` only through `fallback/recover-osqp-error`, so fallback shaping has one owner.

Sixth, shrink `solver_adapter.cljs` to a facade that requires the new `quadprog` and `osqp` namespaces and delegates `solve-with-quadprog` and `solve-with-osqp` to their `solve` functions. Do not change callers outside this namespace unless a compiler error reveals an existing private helper import, which is not expected.

## Concrete Steps

All commands are run from `/Users/barry/.codex/worktrees/f4fb/hyperopen`.

1. Regenerate and run the new module-boundary tests for RED:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.infrastructure.solver-adapter-modules-test

   Expected before implementation: compile fails because `hyperopen.portfolio.optimizer.infrastructure.problem-adapter`, `hyperopen.portfolio.optimizer.infrastructure.osqp`, and `hyperopen.portfolio.optimizer.infrastructure.fallback` are not present yet.

2. After implementation, run the focused solver tests:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.infrastructure.solver-adapter-modules-test --test=hyperopen.portfolio.optimizer.infrastructure.solver-adapter-test --test=hyperopen.portfolio.optimizer.infrastructure.solver-adapter-parity-test

   Expected after implementation: all three namespaces run with 0 failures and 0 errors.

   Actual after implementation:

    Ran 18 tests containing 77 assertions.
    0 failures, 0 errors.

3. Run namespace and required gates:

    npm run lint:namespace-sizes
    npm run check
    npm test
    npm run test:websocket

   Expected after implementation: every command exits 0. If a command fails for unrelated pre-existing state, record the exact failure in this plan and in the final response.

   Actual after implementation:

    npm run lint:namespace-sizes
    Namespace size check passed.

    npm run check
    Exited 0 after test-runner generation, tooling/lint suites, and app, portfolio, portfolio-worker, portfolio-optimizer-worker, vault-detail-worker, and test compiles.

    npm test
    Ran 3893 tests containing 21467 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

## Validation and Acceptance

Acceptance requires the public facade namespace `hyperopen.portfolio.optimizer.infrastructure.solver-adapter` to keep exposing `solve-with-quadprog` and `solve-with-osqp` with the same input and output shapes used by existing tests. The new module-boundary tests must pass and must exercise the shared problem adapter, OSQP CSC conversion, and fallback result shaping. Existing solver adapter and solver parity tests must pass through the facade.

The refactor is not accepted if callers must import new namespaces to solve problems, if unsupported L1 constraints change shape, if OSQP fallback maps lose their current diagnostic fields, or if the split-variable decode returns expanded variables instead of original instrument weights.

## Idempotence and Recovery

The refactor is additive-first: create the new namespaces, delegate from the old facade, and only then remove moved code from `solver_adapter.cljs`. If a focused test fails, inspect whether the failing behavior belongs in shared problem adaptation, a solver-specific adapter, or fallback recovery, and move the smallest relevant helper to the correct namespace. Running `npm run test:runner:generate` is safe to repeat.

If OSQP package setup fails locally while quadprog tests pass, do not change solver semantics to hide it. Record the failure and preserve the fallback behavior. If the required full gates surface unrelated failures, record the command and evidence rather than broadening this refactor.

## Artifacts and Notes

Initial source size:

    424 src/hyperopen/portfolio/optimizer/infrastructure/solver_adapter.cljs

Current split source and test sizes after focused validation:

     11 src/hyperopen/portfolio/optimizer/infrastructure/solver_adapter.cljs
    207 src/hyperopen/portfolio/optimizer/infrastructure/problem_adapter.cljs
     91 src/hyperopen/portfolio/optimizer/infrastructure/quadprog.cljs
    122 src/hyperopen/portfolio/optimizer/infrastructure/osqp.cljs
     16 src/hyperopen/portfolio/optimizer/infrastructure/fallback.cljs
     98 test/hyperopen/portfolio/optimizer/infrastructure/solver_adapter_modules_test.cljs

Important current public functions:

    hyperopen.portfolio.optimizer.infrastructure.solver-adapter/solve-with-quadprog
    hyperopen.portfolio.optimizer.infrastructure.solver-adapter/solve-with-osqp

Expected new infrastructure namespaces:

    hyperopen.portfolio.optimizer.infrastructure.problem-adapter
    hyperopen.portfolio.optimizer.infrastructure.quadprog
    hyperopen.portfolio.optimizer.infrastructure.osqp
    hyperopen.portfolio.optimizer.infrastructure.fallback

## Interfaces and Dependencies

`quadprog.cljs` depends on the JavaScript package `quadprog`, which expects one-indexed vectors and matrices. `osqp.cljs` depends on the JavaScript package `osqp`, which expects sparse matrices in compressed sparse column form and typed arrays for matrix data, row indices, column pointers, linear objective, lower bounds, and upper bounds. `problem_adapter.cljs` depends on `hyperopen.portfolio.optimizer.domain.math` for objective-value calculation. `fallback.cljs` has no package dependency and should only shape maps from a supplied fallback solve function.

The final facade functions must have these signatures:

    (defn solve-with-quadprog [problem] ...)
    (defn solve-with-osqp [problem] ...)

Revision note, 2026-05-13T23:59Z / Codex: Initial active ExecPlan created after inspecting the current solver adapter, existing tests, and repo planning contract. The plan records local execution of the feature-flow phases because this session did not have an explicit user request for subagent orchestration.

Revision note, 2026-05-14T00:09Z / Codex: Recorded RED/GREEN evidence, dependency install drift, final split sizes, required gate results, and completion outcome before moving the plan to completed.
