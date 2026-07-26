# Collapse optimizer runtime registration into an owned catalog

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. It is self-contained so a future contributor can continue from this file without relying on conversation history.

## Context References

Public refs:
- Direct maintainer request on 2026-05-14: "Collapse optimizer action/effect registration fan-out" so adding one optimizer action no longer requires touching multiple global runtime registries.

Repo artifacts:
- `/hyperopen/AGENTS.md` requires an ExecPlan for significant refactors and requires `npm run check`, `npm test`, and `npm run test:websocket` when code changes.
- `/hyperopen/docs/PLANS.md` defines the public planning contract for this file.
- `/hyperopen/src/hyperopen/app/actions.cljs`, `/hyperopen/src/hyperopen/app/effects.cljs`, `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`, and `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs` are the current fan-out points named by the maintainer.

Local scratch refs (non-authoritative):
- None.

## Purpose / Big Picture

Optimizer actions and effects are currently registered through several broad runtime files. A future contributor adding one optimizer action has to remember to update the action handler map, often a generic action adapter facade, the effect handler map, and often a generic effect adapter facade. That makes optimizer work drift-prone because registration knowledge is spread outside the optimizer feature area.

After this change, the optimizer owns a single runtime catalog namespace that lists its action handlers and effect handlers. The app runtime will merge that catalog once for actions and once for effects. The observable proof is that a test can replace the optimizer catalog with a sentinel handler and see the app runtime pick it up without editing the broad runtime maps, and the existing runtime registration tests still prove all optimizer handler keys resolve.

## Progress

- [x] (2026-05-14 15:48Z) Read the current runtime fan-out in `src/hyperopen/app/actions.cljs`, `src/hyperopen/runtime/action_adapters.cljs`, `src/hyperopen/app/effects.cljs`, and `src/hyperopen/runtime/effect_adapters.cljs`.
- [x] (2026-05-14 15:48Z) Confirmed the working tree started clean with `git status --short`.
- [x] (2026-05-14 15:48Z) Created this active ExecPlan before production code changes.
- [x] (2026-05-14 15:51Z) Added RED coverage in `test/hyperopen/portfolio/optimizer/runtime_catalog_test.cljs`; after installing lockfile dependencies with `npm ci`, the generated test command reached the assertions and failed with 6 failures from the new catalog expectations.
- [x] (2026-05-14 15:57Z) Implemented `src/hyperopen/portfolio/optimizer/runtime_catalog.cljs` and moved optimizer action/effect dependency maps there.
- [x] (2026-05-14 15:57Z) Updated `src/hyperopen/app/actions.cljs` and `src/hyperopen/app/effects.cljs` to merge the optimizer catalog once.
- [x] (2026-05-14 15:57Z) Updated wiring and app effect tests to assert optimizer registration through the catalog while preserving generic facade contract tests.
- [x] (2026-05-14 15:57Z) Ran the generated test command after implementation; it passed with 3,906 tests, 21,520 assertions, 0 failures, and 0 errors.
- [x] (2026-05-14 16:00Z) Ran required repository validation gates. `npm run check` exited 0, `npm test` passed with 3,906 tests and 21,520 assertions, and `npm run test:websocket` passed with 524 tests and 3,043 assertions.
- [x] (2026-05-14 16:00Z) Prepared this plan to move to `docs/exec-plans/completed/` after validation was recorded.

## Surprises & Discoveries

- Observation: `src/hyperopen/runtime/registry_composition.cljs` already accepts nested dependency maps and flattens them by handler key.
  Evidence: `runtime-action-handlers` and `runtime-effect-handlers` call `build-runtime-handlers`, which collects leaves from any nested dependency graph before matching keys from `hyperopen.schema.runtime-registration-catalog`.

- Observation: Generic action and effect adapter facades have tests asserting the existing public facade functions remain present.
  Evidence: `test/hyperopen/runtime/action_adapters_test.cljs` checks optimizer action adapter vars are functions, and `test/hyperopen/runtime/effect_adapters/facade_contract_test.cljs` checks optimizer effect adapter vars are functions.

- Observation: The local worktree did not have `node_modules` installed when the first RED command ran.
  Evidence: `node out/test.js` initially failed before assertions with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`; `npm ci` installed 335 packages from the lockfile and the next RED run reached the intended catalog assertion failures.

- Observation: The RED test failed for the intended registration fan-out.
  Evidence: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js` reported 6 failures, including "app action deps should require the optimizer-owned runtime catalog", "app effect deps should merge the optimizer effect catalog once", and "app effect deps should not enumerate optimizer handlers inline".

- Observation: The post-change generated CLJS test suite passed before the required gates.
  Evidence: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js` passed with 3,906 tests, 21,520 assertions, 0 failures, and 0 errors.

- Observation: The repository gates passed after the refactor.
  Evidence: `npm run check` exited 0 after the tooling checks and Shadow CLJS app, portfolio, worker, and test compiles completed with 0 warnings. `npm test` passed with 3,906 tests and 21,520 assertions. `npm run test:websocket` passed with 524 tests and 3,043 assertions.

## Decision Log

- Decision: Create the optimizer catalog under `src/hyperopen/portfolio/optimizer/runtime_catalog.cljs`.
  Rationale: The catalog belongs to the optimizer feature area, and locating it beside optimizer actions makes future optimizer additions easier to discover than placing the map in generic app or runtime namespaces.
  Date/Author: 2026-05-14 / Codex

- Decision: Preserve the existing generic action/effect adapter public facade vars while moving runtime registration ownership to the optimizer catalog.
  Rationale: `AGENTS.md` says to preserve public APIs unless explicitly requested. The goal is to eliminate registration fan-out for app runtime wiring, not to force unrelated callers or tests off existing facade vars in the same change.
  Date/Author: 2026-05-14 / Codex

## Outcomes & Retrospective

The optimizer runtime registration refactor is implemented and validated. `src/hyperopen/portfolio/optimizer/runtime_catalog.cljs` now owns optimizer action and effect dependency maps. `src/hyperopen/app/actions.cljs` and `src/hyperopen/app/effects.cljs` merge that catalog once, so future optimizer runtime additions can be made in the optimizer-owned catalog instead of enumerating handlers in broad app maps.

The existing generic facade aliases in `hyperopen.runtime.action-adapters` and `hyperopen.runtime.effect-adapters` remain present and tested for compatibility. Overall complexity is reduced because runtime registration ownership moved from broad global maps to a feature-local catalog, while the cost is one small feature-local namespace and a compatibility layer that was intentionally left in place.

## Context and Orientation

The working directory is `/Users/barry/.codex/worktrees/b329/hyperopen`.

Runtime registration in this app is driven by handler keys. A handler key is a keyword such as `:run-portfolio-optimizer` that connects a registered runtime id such as `:actions/run-portfolio-optimizer` or `:effects/run-portfolio-optimizer` to an actual ClojureScript function. The runtime registration catalog in `src/hyperopen/schema/runtime_registration_catalog.cljs` lists all ids and handler keys. `src/hyperopen/runtime/registry_composition.cljs` then flattens nested dependency maps and builds the final action and effect handler maps.

The current app-level action dependencies live in `src/hyperopen/app/actions.cljs`. That file has a large `:portfolio-optimizer` map that points to public aliases in `src/hyperopen/runtime/action_adapters.cljs`. The optimizer action implementations live in `src/hyperopen/portfolio/optimizer/actions.cljs`, `src/hyperopen/portfolio/optimizer/black_litterman_actions.cljs`, and `src/hyperopen/portfolio/optimizer/frontier_actions.cljs`.

The current app-level effect dependencies live in `src/hyperopen/app/effects.cljs`. That file has a large `:portfolio-optimizer` map that points to public aliases and factories in `src/hyperopen/runtime/effect_adapters.cljs`. The optimizer effect implementations and factories live in `src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs`.

This plan does not change runtime ids, handler keys, action behavior, effect behavior, browser storage, websocket behavior, or UI rendering. It only changes where optimizer runtime dependency maps are owned and merged.

## Plan of Work

First add a focused RED test in a new test namespace, `test/hyperopen/portfolio/optimizer/runtime_catalog_test.cljs`. The test should use `with-redefs` to replace `hyperopen.portfolio.optimizer.runtime-catalog/action-deps` and `effect-deps` with sentinel maps. Calling `hyperopen.app.actions/runtime-action-deps` must expose the sentinel action handler under `[:portfolio-optimizer :sentinel-action]`. Calling `hyperopen.app.effects/runtime-effect-deps` with a sentinel runtime must expose the sentinel effect handler under `[:portfolio-optimizer :sentinel-effect]` and prove the same runtime was passed into `effect-deps`. This test must fail before implementation because the app runtime does not yet require or call the optimizer catalog.

Then create `src/hyperopen/portfolio/optimizer/runtime_catalog.cljs`. Define `action-deps` to return `{:portfolio-optimizer {...}}` with the same optimizer action handler keys currently listed in `src/hyperopen/app/actions.cljs`, pointing directly to the optimizer action namespaces. Define `effect-deps` to accept `runtime`, build one optimizer controller resolver through `hyperopen.runtime.effect-adapters.portfolio-optimizer/make-portfolio-optimizer-controller-resolver`, and return `{:portfolio-optimizer {...}}` with the same optimizer effect handler keys currently listed in `src/hyperopen/app/effects.cljs`.

Next update `src/hyperopen/app/actions.cljs` to require the optimizer runtime catalog and remove the inline `:portfolio-optimizer` map from `runtime-action-overrides`. `runtime-action-deps` should merge `(runtime-action-overrides)` with `(optimizer-runtime-catalog/action-deps)` before passing the result into `hyperopen.runtime.collaborators/runtime-action-deps`. This is the single action-side app merge point.

Then update `src/hyperopen/app/effects.cljs` to require the optimizer runtime catalog and remove the inline `:portfolio-optimizer` map and local optimizer controller resolver. `runtime-effect-deps` should merge `(runtime-effect-overrides runtime)` with `(optimizer-runtime-catalog/effect-deps runtime)` before passing the result into `hyperopen.runtime.collaborators/runtime-effect-deps`. This is the single effect-side app merge point.

Update tests that assert runtime wiring identity so they compare optimizer runtime dependencies to the optimizer catalog instead of to broad generic adapter facades. Leave facade contract tests intact so existing public vars continue to be covered.

## Concrete Steps

1. Add the RED catalog merge tests:

    edit test/hyperopen/portfolio/optimizer/runtime_catalog_test.cljs
    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

   Before implementation, expect the new sentinel assertions to fail because app runtime deps do not yet call the optimizer catalog.

2. Implement the catalog namespace:

    create src/hyperopen/portfolio/optimizer/runtime_catalog.cljs

   The namespace must require only optimizer-owned action namespaces and the optimizer-specific effect adapter namespace. It should not depend on `hyperopen.app.actions` or `hyperopen.app.effects`.

3. Replace app inline optimizer maps with one catalog merge:

    edit src/hyperopen/app/actions.cljs
    edit src/hyperopen/app/effects.cljs

   The broad app files should no longer enumerate optimizer action or effect handler keys.

4. Update wiring tests where their expected optimizer source moved:

    edit test/hyperopen/runtime/wiring_test.cljs

   The runtime deps should remain behaviorally identical, but optimizer expectations should read from `hyperopen.portfolio.optimizer.runtime-catalog`.

5. Run focused validation:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

   Expect all ClojureScript tests to pass.

6. Run the required repository gates for code changes:

    npm run check
    npm test
    npm run test:websocket

   Expect each command to exit 0. If a command fails for an unrelated existing issue, record the exact failure and do not claim the gate passed.

7. Update this ExecPlan with final progress, discoveries, validation evidence, and retrospective. Move it from `docs/exec-plans/active/` to `docs/exec-plans/completed/` after acceptance criteria pass.

## Validation and Acceptance

The work is accepted when all of these are true:

1. `src/hyperopen/portfolio/optimizer/runtime_catalog.cljs` exists and owns optimizer action and effect dependency maps.

2. `src/hyperopen/app/actions.cljs` no longer has an inline `:portfolio-optimizer` handler map; it merges `optimizer-runtime-catalog/action-deps` once.

3. `src/hyperopen/app/effects.cljs` no longer has an inline `:portfolio-optimizer` handler map; it merges `optimizer-runtime-catalog/effect-deps` once.

4. `test/hyperopen/portfolio/optimizer/runtime_catalog_test.cljs` proves app runtime deps consume catalog-provided sentinel action and effect handlers.

5. Runtime registration still resolves all optimizer action and effect handler keys from `src/hyperopen/schema/runtime_registration/portfolio.cljs`.

6. Existing generic facade vars in `hyperopen.runtime.action-adapters` and `hyperopen.runtime.effect-adapters` remain callable.

7. `npm run check`, `npm test`, and `npm run test:websocket` are run and results are recorded.

## Idempotence and Recovery

The implementation is source-only and can be retried safely. Test runner generation is idempotent and rewrites `test/test_runner_generated.cljs` based on current test namespaces.

If the new catalog introduces a circular dependency, the recovery path is to keep the catalog under `hyperopen.portfolio.optimizer.runtime-catalog` but depend directly on lower-level optimizer implementation namespaces instead of generic app or runtime facades. If runtime registration keys go missing, compare `hyperopen.schema.runtime-registration.portfolio/action-binding-rows` and `effect-binding-rows` against the new catalog maps and restore any missing handler key in the catalog. Do not run destructive git commands or revert unrelated user work.

## Artifacts and Notes

Initial source inspection found these current fan-out points:

    src/hyperopen/app/actions.cljs contains an inline :portfolio-optimizer action map.
    src/hyperopen/runtime/action_adapters.cljs contains optimizer action facade aliases.
    src/hyperopen/app/effects.cljs contains an inline :portfolio-optimizer effect map.
    src/hyperopen/runtime/effect_adapters.cljs contains optimizer effect facade aliases.

Revision note, 2026-05-14 / Codex: Created the plan from the direct maintainer request before production code changes so the refactor can be resumed from this file alone.

Revision note, 2026-05-14 / Codex: Updated progress, discoveries, validation evidence, and retrospective after implementing the optimizer runtime catalog and running the required gates.
