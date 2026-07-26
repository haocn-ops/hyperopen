# Optimizer Restore Route Runtime Eager Registration

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows the repo contract in `.agents/PLANS.md`.

## Purpose / Big Picture

When a user opens `/portfolio/optimize`, the browser console can log `[nexus dispatch error]` with `Route runtime action handler unavailable: :portfolio [:portfolio-optimizer :restore-or-preseed-portfolio-optimizer-draft]`. The route still often recovers, but the error means a startup watcher tried to dispatch an optimizer restore action before the lazy portfolio route runtime had exported its action catalog. After this change, the restore/preseed funnel is registered eagerly like the optimizer route loader, so startup and account-arrival watchers can safely dispatch it before the route chunk finishes loading.

The observable outcome is that dispatching `:actions/restore-or-preseed-portfolio-optimizer-draft` before loading the portfolio route module no longer throws. Focused runtime tests prove the handler is direct and the lazy route-runtime wrapper no longer owns it.

## Context References

Public refs:
- Direct user request on 2026-07-08: create an execution plan and implement the fix for the `/portfolio/optimize` console error.

Repo artifacts:
- `.agents/PLANS.md` defines the required ExecPlan structure.
- `docs/PLANS.md` says risky bug work should keep a living ExecPlan under `docs/exec-plans/active/`.
- `docs/MULTI_AGENT.md` says `$bug-flow` is explicit-only and identifies `worker` as the role that edits `src/**`.

Local scratch refs (non-authoritative):
- Browser reproduction during diagnosis: a Playwright one-off dispatched `:actions/restore-or-preseed-portfolio-optimizer-draft` on the loaded app shell before the portfolio route runtime was resolved and saw the same console text as the user screenshot.

## Progress

- [x] (2026-07-08T18:15:18Z) Root cause identified: the handler exists in `src/hyperopen/portfolio/optimizer/runtime_catalog.cljs`, but global optimizer draft watchers can dispatch it before `src/hyperopen/portfolio/route_runtime_module.cljs` has been loaded by `src/hyperopen/route_modules.cljs`.
- [x] (2026-07-08T18:15:18Z) Created this active ExecPlan with scoped acceptance criteria.
- [x] (2026-07-08T18:16:30Z) Added RED tests in `test/hyperopen/runtime/wiring_test.cljs` and `test/hyperopen/portfolio/route_runtime_module_test.cljs`.
- [x] (2026-07-08T18:17:20Z) Verified RED. The generated runner ran the full suite and failed only the new ownership assertions: `route_runtime_module_test.cljs` still found `:restore-or-preseed-portfolio-optimizer-draft` in the route-runtime export, and `wiring_test.cljs` found the app registry handler was `nil` while the lazy action-key set still contained the restore/preseed key.
- [x] (2026-07-08T18:18:05Z) Implemented the minimal runtime-registration change in `src/hyperopen/app/actions.cljs` and `src/hyperopen/portfolio/route_runtime_module.cljs`.
- [x] (2026-07-08T18:19:20Z) Verified GREEN with the generated test suite: 5,275 tests, 28,479 assertions, 0 failures, 0 errors.
- [x] (2026-07-08T18:20:15Z) Verified the browser repro against this worktree's compiled bundle on temporary port 4175: before portfolio route-runtime export load, dispatching `:actions/restore-or-preseed-portfolio-optimizer-draft` produced zero `Route runtime action handler unavailable` console errors.
- [x] (2026-07-08T18:22:58Z) Ran final required gates with `npm run gates`: 34/34 gates passed, 5,981 tests, 31,824 assertions, overall PASS.

## Surprises & Discoveries

- Observation: `localhost:8080/index.html` serves the app shell, but direct GETs to `/` and `/portfolio/optimize` return `404 Not Found` in the current shadow dev server. Existing Playwright helpers intentionally load `/trade` or `/index.html` first and navigate in-app.
  Evidence: `curl http://localhost:8080/index.html` returned HTTP 200 while `/portfolio/optimize` returned HTTP 404.
- Observation: After the portfolio route runtime is loaded, the same restore/preseed action no longer emits the console error.
  Evidence: a one-off browser script navigated to `/portfolio/optimize`, confirmed `hyperopen.portfolio.route_runtime_module.action_deps` exists, then dispatched the restore/preseed action with no console error.
- Observation: The already-running `localhost:8080` shadow server belonged to `/Users/barry/projects/hyperopen`, not this worktree. A browser check against that server still showed the old error after the source fix because it was serving a stale bundle from the main checkout.
  Evidence: `ps` showed `node /Users/barry/projects/hyperopen/node_modules/.bin/shadow-cljs watch ...`; compiling this worktree and serving `resources/public` on port 4175 made the same browser repro pass with `routeRuntimeErrorCount: 0`.
- Observation: The generated test runner does not support `--test=...` filtering; passing those arguments still runs the generated namespace list.
  Evidence: `node out/test.js --test=hyperopen.runtime.wiring-test ...` ran 5,275 tests. The plan's focused command was corrected operationally by redirecting full runner output to a temp log and inspecting the failure or summary lines.

## Decision Log

- Decision: Fix the error by eagerly registering `:restore-or-preseed-portfolio-optimizer-draft`, not by making lazy action handlers perform asynchronous module loads.
  Rationale: This action is a route-entry and startup recovery funnel. It can run before any portfolio route UI interaction, just like `:load-portfolio-optimizer-route`, and its namespace is already eagerly reachable from navigation and watcher code. Changing lazy action dispatch to async would be broader and risk changing the runtime action contract.
  Date/Author: 2026-07-08 / Codex.
- Decision: Move the ExecPlan to `docs/exec-plans/completed/` after the gate matrix passed.
  Rationale: The purpose and all acceptance criteria are satisfied, so keeping it active would violate the repo's active-plan lifecycle rule.
  Date/Author: 2026-07-08 / Codex.

## Outcomes & Retrospective

Completed on 2026-07-08. Startup watchers can now dispatch the draft restore/preseed action without depending on portfolio route chunk timing, while the rest of the optimizer action catalog remains lazy. The change reduces complexity at the failure site because the route-entry restore/preseed funnel now has the same eager ownership model as `:load-portfolio-optimizer-route`; it does not add asynchronous action dispatch or broaden the lazy route-runtime contract.

## Context and Orientation

Hyperopen uses Nexus actions and effects. An action is a pure function that receives app state and returns effects or follow-up actions. Runtime registration maps public action ids such as `:actions/restore-or-preseed-portfolio-optimizer-draft` to handler keys such as `:restore-or-preseed-portfolio-optimizer-draft`.

To reduce the initial app bundle, most portfolio optimizer action handlers are lazy. The lazy wrappers are assembled in `src/hyperopen/app/actions.cljs` by calling `route-modules/lazy-route-action-leaf-deps` for the `:portfolio` route module and the `:portfolio-optimizer` handler group. The wrappers resolve real handlers from `src/hyperopen/portfolio/route_runtime_module.cljs`, which exports `hyperopen.portfolio.route_runtime_module.action_deps` once the portfolio route chunk has loaded.

The failing action is special. `src/hyperopen/portfolio/optimizer/actions/draft_persistence.cljs` defines `restore-or-preseed-portfolio-optimizer-draft`, which returns `[:effects/restore-portfolio-optimizer-draft]` only on an untouched `/portfolio/optimize` or `/portfolio/optimize/new` draft. It is used both during explicit navigation in `src/hyperopen/runtime/action_adapters/navigation.cljs` and later by startup watchers in `src/hyperopen/portfolio/optimizer/infrastructure/draft_autosave.cljs`. Those watchers fire when account identity or holdings arrive and may run before the route runtime has been loaded. That is why the action must be eager.

## Plan of Work

First, update tests before production code. In `test/hyperopen/runtime/wiring_test.cljs`, add an assertion that `wiring/runtime-action-deps` returns the actual `portfolio-optimizer-actions/restore-or-preseed-portfolio-optimizer-draft` function at `[:portfolio-optimizer :restore-or-preseed-portfolio-optimizer-draft]`, and verify the lazy route action helper was not asked to wrap that key. In `test/hyperopen/portfolio/route_runtime_module_test.cljs`, add the key to the route-runtime module exclusion assertions so the portfolio chunk no longer exports an eager handler duplicate.

Second, implement the minimal code. In `src/hyperopen/app/actions.cljs`, add `:restore-or-preseed-portfolio-optimizer-draft` to the eager optimizer action keys, remove it from `lazy-portfolio-optimizer-action-keys`, and include the direct handler in the `:portfolio-optimizer` eager merge. In `src/hyperopen/portfolio/route_runtime_module.cljs`, add the same key to `eager-action-keys`.

Third, run the focused test slice. The command should compile the ClojureScript test build and run the two affected test namespaces:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.runtime.wiring-test --test=hyperopen.portfolio.route-runtime-module-test --test=hyperopen.route-modules-test

If these pass, run an optional one-off browser check by loading a compiled app shell for this worktree and dispatching `:actions/restore-or-preseed-portfolio-optimizer-draft` before navigating to the portfolio route. The expected console result is no `Route runtime action handler unavailable` error. This one-off browser check is evidence only and does not replace the deterministic tests.

## Concrete Steps

Run every command from `/Users/barry/.codex/worktrees/368f/hyperopen`.

1. Ensure dependencies are linked:

    npm run setup:worktree

2. Write the failing tests in `test/hyperopen/runtime/wiring_test.cljs` and `test/hyperopen/portfolio/route_runtime_module_test.cljs`.

3. Verify RED:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.runtime.wiring-test --test=hyperopen.portfolio.route-runtime-module-test

Expected before the production change: at least one assertion fails because the restore/preseed action is still in the lazy handler-key set or still exported from the route runtime module.

4. Make the eager-registration implementation in `src/hyperopen/app/actions.cljs` and `src/hyperopen/portfolio/route_runtime_module.cljs`.

5. Verify GREEN with the generated runner. Because `node out/test.js` does not support namespace filtering, redirect the full runner output to a temp log when a compact transcript is needed:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js > /tmp/hyperopen-runtime-green.log 2>&1

Expected after the production change: the generated test suite reports `Ran 5275 tests containing 28479 assertions. 0 failures, 0 errors.`

6. Run the required gate matrix:

    npm run gates

Expected after the production change: `Overall: PASS` with all 34 gates passing.

7. Update this ExecPlan with the actual command output summary and any discoveries.

## Validation and Acceptance

Acceptance criteria:

1. `wiring/runtime-action-deps` exposes a direct function for `:restore-or-preseed-portfolio-optimizer-draft`, not a lazy route wrapper. Satisfied by `hyperopen.runtime.wiring-test`.
2. `hyperopen.portfolio.route-runtime-module/action-deps` does not export `:restore-or-preseed-portfolio-optimizer-draft`, preventing duplicate eager/lazy ownership. Satisfied by `hyperopen.portfolio.route-runtime-module-test`.
3. The generated test suite passes. Satisfied by `node out/test.js`: 5,275 tests, 28,479 assertions, 0 failures, 0 errors.
4. A direct browser dispatch of `:actions/restore-or-preseed-portfolio-optimizer-draft` before portfolio route-module load does not emit `Route runtime action handler unavailable`. Satisfied by the temporary port 4175 browser repro: `routeRuntimeErrorCount: 0`.

Required broader gates for code changes remain `npm run check`, `npm test`, and `npm run test:websocket`; `npm run gates` ran the required matrix and reported 34/34 PASS.

## Idempotence and Recovery

All planned edits are regular source and test changes. The test commands can be rerun safely. If shadow-cljs reports a stale or already-running process, run `npm run browser:cleanup` for browser sessions if any were started, then rerun the focused test command with `--force-spawn` as shown above. If a test fails for a reason unrelated to this change, capture the failure text in `Surprises & Discoveries` before deciding whether to broaden the fix.

## Artifacts and Notes

Diagnosis evidence from the one-off browser repro, shortened:

    [nexus dispatch error] {phase: expand-action,
      action-id: restore-or-preseed-portfolio-optimizer-draft,
      error: Route runtime action handler unavailable:
        :portfolio [:portfolio-optimizer :restore-or-preseed-portfolio-optimizer-draft]}

The relevant handler exists before the fix in `src/hyperopen/portfolio/optimizer/runtime_catalog.cljs`, but the route wrapper in `src/hyperopen/route_modules.cljs` throws when `resolved-route-runtime-action-deps` is not ready.

RED evidence, shortened:

    FAIL in hyperopen.portfolio.route-runtime-module-test
    expected: restore/preseed key not exported from route runtime
    actual: key was present

    FAIL in hyperopen.runtime.wiring-test
    expected: direct restore/preseed handler in runtime-action-deps
    actual: nil, and lazy route action keys contained restore/preseed

GREEN evidence:

    Ran 5275 tests containing 28479 assertions.
    0 failures, 0 errors.

Browser evidence against this worktree bundle:

    {
      "before": {"runtimeExportPresent": false},
      "events": [],
      "routeRuntimeErrorCount": 0
    }

Gate evidence:

    Gate matrix (34 gates): all PASS
    Totals: gates passed 34/34; tests run 5981; assertions run 31824
    Overall: PASS

## Interfaces and Dependencies

The public action id remains `:actions/restore-or-preseed-portfolio-optimizer-draft`. The handler function remains `hyperopen.portfolio.optimizer.actions/restore-or-preseed-portfolio-optimizer-draft` with signature `[state path] -> effects-vector`.

No new dependencies are required. The fix only changes runtime handler ownership:

- eager owner: `src/hyperopen/app/actions.cljs`
- lazy route export exclusion: `src/hyperopen/portfolio/route_runtime_module.cljs`
- runtime lookup helper remains unchanged: `src/hyperopen/route_modules.cljs`

Revision note 2026-07-08: Initial plan created from the user-reported console error and the local root-cause investigation. The plan chooses eager action registration because the action is a startup route-entry funnel and does not need portfolio route UI code to compute its effects.

Revision note 2026-07-08: Implementation completed, validation evidence recorded, and plan moved to `docs/exec-plans/completed/` because all acceptance criteria and required gates passed.
