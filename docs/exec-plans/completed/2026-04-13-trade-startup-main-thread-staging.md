# Stage Trade Startup For Lower Main-Thread Busy Time

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md`. The live `bd` issue for this work is `hyperopen-ye7x` ("Reduce trade startup main-thread work"), and `bd` was the local tracker used at the time until this plan is moved out of `active`.

This plan is a narrow follow-up to `/hyperopen/docs/exec-plans/completed/2026-04-01-desktop-trade-route-startup-tbt.md`. That earlier work already split the trade chart, trading crypto, and indicator runtime off the cold route. The remaining work in scope here is not another chunk-splitting wave. It is startup orchestration work: do less before or immediately after first paint, move obviously non-visible work later, and stop scheduling non-trade startup work on the default `/trade` route.

## Purpose / Big Picture

The 2026-04-13 production trace taken from `https://www.hyperopen.xyz/` shows that the page is already visually fast. The main frame reaches first paint and first contentful paint at about `358ms`, reaches largest contentful paint at about `509ms`, and fires the main-frame load event at about `792ms`. The remaining user-facing problem is that the default trade route keeps the main browser thread busy after the route is already visible. The trace shows `main.js` still costs about `64.6ms` to evaluate and `32.6ms` to compile on a cached load, and then keeps producing repeated `13–25ms` work slices over the next `1–3s`.

After this work, the default `/trade` route should still paint at roughly the same time, but it should settle sooner because non-visible startup work will no longer compete with the initial trade shell. A contributor should be able to prove the improvement with the existing release-backed startup profiler, deterministic Playwright smoke, the required repository gates, and the governed `/trade` browser QA pass.

## Progress

- [x] (2026-04-13 16:05Z) Created and claimed `bd` issue `hyperopen-ye7x` for the startup-main-thread optimization work.
- [x] (2026-04-13 16:12Z) Audited the 2026-04-13 production trace and the completed 2026-04-01 startup TBT plan to isolate the remaining high-return, lower-risk work.
- [x] (2026-04-13 16:12Z) Authored this active ExecPlan with scope limited to startup staging, deferred non-visible restores, and route-fan-out removal.
- [x] (2026-04-13 16:19Z) Captured the fresh local release-backed baseline at `/Users/barry/.codex/worktrees/e0de/hyperopen/tmp/browser-inspection/trade-startup-profile-2026-04-13T16-19-37-712Z-bcf350f3` with `blockingTimeProxyMs=78`, `maxSingleBlockingTaskMs=123`, `tradeRootVisibleMs=301`, and `orderFormVisibleMs=306.3`.
- [x] (2026-04-13 16:24Z) Implemented Milestone 1 by splitting startup restores into `restore-critical-ui-state!` and `restore-deferred-ui-state!`, keeping only visible trade-route state synchronous.
- [x] (2026-04-13 16:24Z) Implemented Milestone 2 by staging post-render startup into an immediate phase plus a yielded background phase through `yield-to-main!`.
- [x] (2026-04-13 16:24Z) Implemented Milestone 3 by removing default-trade route fan-out and replacing address-change refresh fan-out with current-route-only refresh behavior.
- [x] (2026-04-13 16:32Z) Ran the post-change startup profiler, targeted Playwright smoke, `npm test`, `npm run test:websocket`, governed `/trade` QA, and browser cleanup.
- [x] (2026-04-13 16:39Z) Moved the completed stale active ExecPlans into `docs/exec-plans/completed`, reran `npm run check`, and confirmed the required repository gates pass on the final tree.

## Surprises & Discoveries

- Observation: the production trace does not support a “make the page paint faster” story; it supports a “make the route calm down sooner” story.
  Evidence: the 2026-04-13 trace shows `firstPaint` and `firstContentfulPaint` at about `358ms`, `largestContentfulPaint::Candidate` at about `509ms`, and the main-frame `MarkLoad` at about `792ms`.

- Observation: browser extensions contribute several long tasks in the production trace, so the production trace is directionally useful but not clean enough to use as the only acceptance artifact.
  Evidence: extension-attributed script work in the trace includes roughly `203.8ms` from `chrome-extension://hdokiejnpimakedhajhdlcegeplioahd/web-client-content-script.js` and roughly `96.2ms` from `chrome-extension://aapbdbdomjkkjkaonfhkkikfgjllcleb/bubble_compiled.js`.

- Observation: the repository already has a startup staging model, but too much still runs in the first post-render callback.
  Evidence: `/hyperopen/src/hyperopen/startup/init.cljs` currently runs one `post-render-startup!` callback that installs shortcuts, installs clickaway handlers, registers the service worker, reveals desktop secondary panels, triggers route-module loads, and initializes remote data streams in one burst.

- Observation: startup still restores many preferences synchronously even though several of them do not affect the default cold `/trade` surface before the user interacts.
  Evidence: `/hyperopen/src/hyperopen/startup/init.cljs` `restore-persisted-ui-state!` currently restores portfolio range, vault snapshot range, leaderboard preferences, open-orders sort, and three history pagination preferences before the startup sequence reaches router init and first render.

- Observation: startup still dispatches non-trade route loaders even when the current route is `/trade`.
  Evidence: `/hyperopen/src/hyperopen/startup/runtime.cljs` `initialize-remote-data-streams!` dispatches `:actions/load-leaderboard-route`, `:actions/load-vault-route`, `:actions/load-funding-comparison-route`, `:actions/load-staking-route`, and `:actions/load-api-wallet-route` using the current route, even though those actions are relevant only when the current route matches their own route predicates.

- Observation: the staged startup cut the measured blocking-time proxy by more than the plan’s percentage target without regressing visible trade readiness.
  Evidence: the baseline profile at `/Users/barry/.codex/worktrees/e0de/hyperopen/tmp/browser-inspection/trade-startup-profile-2026-04-13T16-19-37-712Z-bcf350f3/profile.json` recorded `blockingTimeProxyMs=78`, while the final profile at `/Users/barry/.codex/worktrees/e0de/hyperopen/tmp/browser-inspection/trade-startup-profile-2026-04-13T16-31-33-362Z-8f9c4587/profile.json` recorded `blockingTimeProxyMs=51` (`-27ms`, `-34.6%`), `maxSingleBlockingTaskMs=101` (`-22ms`), `tradeRootVisibleMs=243.7` (`-57.3ms`), and `orderFormVisibleMs=277` (`-29.3ms`).

- Observation: the governed `/trade` QA pass needed one explicit cleanup step because a stale local `shadow-cljs` server was already bound to the project port.
  Evidence: the first `npm run qa:design-ui -- --targets trade-route --manage-local-app` attempt failed until `npx shadow-cljs stop` cleared the existing server, after which the rerun passed for widths `375`, `768`, `1280`, and `1440`.

- Observation: the final repo-wide gate run required aligning ExecPlan lifecycle state with the completed work before the docs linter would pass.
  Evidence: moving `docs/exec-plans/active/2026-04-12-mobile-active-asset-funding-tooltip-sheet.md` and this startup plan into `docs/exec-plans/completed` cleared the `active-exec-plan-no-unchecked-progress` docs-lint failures, after which `npm run check` completed successfully.

## Decision Log

- Decision: scope this plan to startup orchestration only and explicitly exclude deep render-loop surgery, further chart micro-optimizations, and another chunk-splitting initiative.
  Rationale: the 2026-04-13 trace shows the remaining low-risk leverage is in what startup schedules and when it schedules it. The lower-return ideas are still available later, but they are not the best risk-adjusted next step.
  Date/Author: 2026-04-13 / Codex

- Decision: treat the production trace as the directional baseline and require a fresh local release-backed startup profile for implementation acceptance.
  Rationale: the production trace contains too much extension noise for exact acceptance thresholds, while the checked-in local profiler already records the same cold-route workload without that contamination.
  Date/Author: 2026-04-13 / Codex

- Decision: keep visibly route-shaping restores synchronous and move only obviously non-visible restores later in this wave.
  Rationale: this plan aims for low-risk wins. Deferring the wrong preference would create a visible flash or wrong initial route state, which is not acceptable for a modest startup gain.
  Date/Author: 2026-04-13 / Codex

- Decision: remove startup route-load fan-out from the default `/trade` path, but keep route-specific data loads on actual route entry and current-route refresh paths.
  Rationale: the startup work should stop paying for five non-trade route predicates and dispatches on every default trade boot, but the repository still needs correct behavior when the user is actually on those routes.
  Date/Author: 2026-04-13 / Codex

## Outcomes & Retrospective

This plan landed the scoped startup orchestration changes. The final local release-backed profile improved `blockingTimeProxyMs` from `78` to `51` (`-27ms`, `-34.6%`) and reduced `maxSingleBlockingTaskMs` from `123` to `101` (`-22ms`, `-17.9%`). Visible readiness did not regress; in this run it improved as well, with `tradeRootVisibleMs` moving from `301` to `243.7` and `orderFormVisibleMs` moving from `306.3` to `277`.

The code stayed within the intended complexity budget. `/hyperopen/src/hyperopen/startup/init.cljs` now has an explicit critical/deferred restore split and a clearer immediate/background startup phase boundary. `/hyperopen/src/hyperopen/startup/runtime.cljs` now has a small `yield-to-main!` helper and a deterministic current-route refresh helper instead of the previous five-route startup fan-out.

Validation is complete. `npm run check`, `npm test`, `npm run test:websocket`, the targeted Playwright smoke, the fresh startup profiler, the governed `/trade` QA pass, and `npm run browser:cleanup` all completed successfully on the final tree.

## Context and Orientation

The cold startup path begins in `/hyperopen/resources/public/index.html`, which fetches `/build-id.txt`, fetches `/js/manifest.json`, and then injects the fingerprinted `main.js` script. `/hyperopen/shadow-cljs.edn` already splits `trade_chart`, `trading_crypto`, and `trading_indicators` out of the `main` browser module, so the problem here is not that the default route still loads those libraries eagerly from the build graph. The problem is that `main` still owns too much startup coordination.

The startup sequence lives in three main places. `/hyperopen/src/hyperopen/app/startup.cljs` wires runtime collaborators and route-module behavior into startup. `/hyperopen/src/hyperopen/startup/init.cljs` runs the high-level sequence: reset startup state, restore persisted UI state, initialize wallet and router, kick the first render, and then schedule a single post-render callback. `/hyperopen/src/hyperopen/startup/runtime.cljs` owns the heavier runtime work such as websocket initialization, current-account bootstrap, route refresh dispatches, and the deferred asset-selector market bootstrap.

The current default `/trade` behavior is shaped by these specific details:

`/hyperopen/src/hyperopen/startup/init.cljs`
  `restore-persisted-ui-state!` restores many preferences synchronously before router init and first render.

`/hyperopen/src/hyperopen/startup/init.cljs`
  `initialize-systems!` runs one `post-render-startup!` callback that currently includes both essential trade startup work and background work.

`/hyperopen/src/hyperopen/startup/runtime.cljs`
  `initialize-remote-data-streams!` starts websocket modules, subscribes the active asset, dispatches five route-loader actions, installs address handlers, starts critical bootstrap, and schedules deferred bootstrap.

`/hyperopen/src/hyperopen/startup/runtime.cljs`
  `install-address-handlers!` currently repeats the same route-loader fan-out on every address change.

The repository already has test seams that cover this area. `/hyperopen/test/hyperopen/startup/init_test.cljs` checks startup ordering and the scheduler hook. `/hyperopen/test/hyperopen/startup/runtime_test.cljs` checks phased bootstrap, address handlers, and deferred bootstrap behavior. `/hyperopen/test/hyperopen/app/startup_test.cljs` checks how `app.startup` wires route-module loading and runtime-owned callbacks. `/hyperopen/test/hyperopen/core_bootstrap/runtime_startup_test.cljs` exercises the full runtime bootstrap shape from `hyperopen.core`.

For browser validation, `/hyperopen/tools/browser-inspection/src/trade_startup_profile.mjs` already captures a release-backed cold `/trade` startup profile and writes `profile.json` containing `longTaskSummary.blockingTimeProxyMs`, `longTaskSummary.maxSingleBlockingTaskMs`, `timings.tradeRootVisibleMs`, and `timings.orderFormVisibleMs`. That profiler is the primary performance proof for this plan.

## Plan of Work

### Milestone 1: Split synchronous startup restores into critical and deferred batches

The first milestone keeps the visible `/trade` shell stable while removing obviously non-visible work from the synchronous boot path. In `/hyperopen/src/hyperopen/startup/init.cljs`, replace the monolithic `restore-persisted-ui-state!` flow with two explicit functions: a critical restore batch that still runs before `init-wallet!`, `init-router!`, and `kick-render!`, and a deferred restore batch that will run only after the first visible trade shell has already been queued.

The critical batch must keep every restore that changes the initial visible route shape or first-screen meaning. Keep `restore-ui-font-preference!`, `restore-ui-locale-preference!`, `restore-chart-options!`, `restore-orderbook-ui!`, `restore-trading-settings!`, `restore-spectate-mode-preferences!`, `restore-spectate-mode-url!`, `restore-trade-route-tab!`, and `restore-active-asset!` in the synchronous path. Do not move those in this wave.

Move only the clearly non-visible or non-default-trade preferences into the deferred batch. The deferred batch should own `restore-asset-selector-sort-settings!`, `restore-portfolio-summary-time-range!`, `restore-leaderboard-preferences!`, `restore-open-orders-sort-settings!`, `restore-funding-history-pagination-settings!`, `restore-trade-history-pagination-settings!`, and `restore-order-history-pagination-settings!`. Leave vault-specific and agent-session-specific restores unchanged in this first wave unless the local baseline proves they are still a measurable problem after the earlier cuts land.

Update `/hyperopen/test/hyperopen/startup/init_test.cljs` so it no longer expects every restore to run before `kick-render!`. The test should instead prove that the critical batch remains synchronous, the deferred batch is invoked only from the scheduled post-render path, and the existing visible-route restores still happen before the first render is kicked.

### Milestone 2: Turn post-render startup into an explicit two-phase sequence with one yield

The second milestone makes the already-deferred startup work less monolithic. In `/hyperopen/src/hyperopen/startup/runtime.cljs`, add a small helper named `yield-to-main!` that returns a JavaScript `Promise`. If `globalThis.scheduler?.yield` exists, the helper should use it. Otherwise it should fall back to a zero-delay timeout promise through `/hyperopen/src/hyperopen/platform.cljs`. This helper exists only to give the browser one clear opportunity to paint and process more important work before the background continuation resumes.

Then update `/hyperopen/src/hyperopen/startup/init.cljs` `initialize-systems!` so the scheduled post-render callback becomes a Promise-driven phase chain instead of a single synchronous burst. The first phase should do only the work that the visible trade route benefits from immediately after first render: install any still-required immediate handlers, reveal the desktop secondary panels, run `load-post-render-route-effects!`, start remote data streams, and run the deferred UI restore batch introduced in Milestone 1 if that batch is still needed before the second phase. After that first phase completes, call `yield-to-main!`, and only then run the background phase.

The background phase should own work that has no effect on whether the default trade shell is visible and responsive right now. Put `register-icon-service-worker!` in that phase. Keep the existing deferred asset-selector market bootstrap in that background phase. If local profiling shows `install-asset-selector-shortcuts!` or `install-position-tpsl-clickaway!` are still measurable after the earlier cuts, move them to the background phase too, but only if the deterministic tests still show no regressions in the first interactive frame.

Update `/hyperopen/test/hyperopen/startup/init_test.cljs`, `/hyperopen/test/hyperopen/startup/runtime_test.cljs`, and `/hyperopen/test/hyperopen/app/startup_test.cljs` to prove the new phase ordering. The tests should cover both branches of `yield-to-main!`, verify that the background phase runs only after the first phase has completed, and keep the current route-module defer contract intact.

### Milestone 3: Remove default-trade startup route fan-out and narrow address-change route refreshes

The third milestone stops paying startup CPU for route work that is irrelevant on the default `/trade` path. In `/hyperopen/src/hyperopen/startup/runtime.cljs`, remove the unconditional startup dispatches of `:actions/load-leaderboard-route`, `:actions/load-vault-route`, `:actions/load-funding-comparison-route`, `:actions/load-staking-route`, and `:actions/load-api-wallet-route` from `initialize-remote-data-streams!`. The current route already owns its own route-module loading through router init and route change handling. Default trade startup should not fan out through five non-trade route action adapters just to no-op.

Do not solve this by deleting route refresh behavior entirely. Address-change handling still needs route-specific refresh behavior when the current route is actually a non-trade route. Replace the current five-dispatch fan-out in `install-address-handlers!` with a current-route-only helper. The helper should inspect the current route once and dispatch only the one route refresh action that matches that route. Preserve the special portfolio refresh behavior that already exists for the portfolio chart tab.

Update `/hyperopen/test/hyperopen/startup/runtime_test.cljs` and `/hyperopen/test/hyperopen/app/startup_test.cljs` so they prove the default trade route does not schedule those non-trade startup actions anymore, while a current non-trade route still gets the correct one-route refresh behavior after address changes.

### Milestone 4: Capture performance proof and run governed validation

Before implementation, capture a fresh baseline with `npm run browser:profile:trade-startup` and record the run directory plus the four key summary numbers in this plan: `blockingTimeProxyMs`, `maxSingleBlockingTaskMs`, `tradeRootVisibleMs`, and `orderFormVisibleMs`. After Milestones 1 through 3 land, rerun `npm run browser:profile:trade-startup` and compare the same fields. If fast iteration is needed on the same built assets, use `npm run browser:profile:trade-startup:cached`, but the final recorded proof must come from a fresh build-backed run.

For deterministic browser validation, run the smallest relevant Playwright smoke first:

    npx playwright test tools/playwright/test/routes.smoke.spec.mjs --grep "trade cold startup does not render the static boot loading shell|trade route exposes score-bearing accessibility hooks"

Once that passes, run the required repository gates:

    npm run check
    npm test
    npm run test:websocket

Finish with the governed `/trade` browser QA pass and cleanup:

    npm run qa:design-ui -- --targets trade-route --manage-local-app
    npm run browser:cleanup

The plan is complete when the fresh local startup profile shows a meaningful improvement on the current tree, the Playwright smoke remains green, the three required repository gates pass, and the governed `/trade` QA pass explicitly accounts for all required widths without a startup regression.

## Concrete Steps

Run all commands from `/Users/barry/.codex/worktrees/e0de/hyperopen`.

1. Capture and record a fresh baseline.

    npm run browser:profile:trade-startup

   Record the emitted `runDir`, then read `profile.json` and copy these values into the `Progress` or `Artifacts and Notes` section of this plan:

    longTaskSummary.blockingTimeProxyMs
    longTaskSummary.maxSingleBlockingTaskMs
    timings.tradeRootVisibleMs
    timings.orderFormVisibleMs

2. Edit `/hyperopen/src/hyperopen/startup/init.cljs` to split restore work into critical and deferred batches, and update `/hyperopen/src/hyperopen/app/startup.cljs` so the new deferred batch is wired into startup.

3. Edit `/hyperopen/src/hyperopen/startup/runtime.cljs` to add `yield-to-main!`, to sequence the post-render phases, and to delete startup route-loader fan-out from `initialize-remote-data-streams!`.

4. Edit `/hyperopen/src/hyperopen/startup/runtime.cljs` again to narrow address-change route refreshes to one current-route-aware helper instead of five unconditional route action dispatches.

5. Update the startup-focused tests:

    /hyperopen/test/hyperopen/startup/init_test.cljs
    /hyperopen/test/hyperopen/startup/runtime_test.cljs
    /hyperopen/test/hyperopen/app/startup_test.cljs
    /hyperopen/test/hyperopen/core_bootstrap/runtime_startup_test.cljs

6. Run the smallest relevant Playwright smoke first.

    npx playwright test tools/playwright/test/routes.smoke.spec.mjs --grep "trade cold startup does not render the static boot loading shell|trade route exposes score-bearing accessibility hooks"

7. Run the required repository gates.

    npm run check
    npm test
    npm run test:websocket

8. Capture the fresh post-change startup profile and compare it to the baseline.

    npm run browser:profile:trade-startup

9. Run governed `/trade` QA and cleanup.

    npm run qa:design-ui -- --targets trade-route --manage-local-app
    npm run browser:cleanup

## Validation and Acceptance

Acceptance is primarily behavioral and measurement-driven.

On a fresh local release-backed startup profile, the default `/trade` route should still expose `trade-root` and `order-form` quickly, but the profile should show less post-paint main-thread busy time than the baseline captured at the start of this plan. The preferred acceptance target for this wave is a reduction of at least `70ms` or at least `20%` in `blockingTimeProxyMs`, whichever is easier to achieve, while keeping `tradeRootVisibleMs` and `orderFormVisibleMs` within roughly `50ms` of the fresh baseline. The exact baseline numbers must be recorded in this plan once Milestone 4 begins.

The implementation must not regress visible default-route correctness. The deterministic Playwright smoke must still show that `/trade` does not render the static boot shell and still exposes the score-bearing accessibility hooks. `npm run check`, `npm test`, and `npm run test:websocket` must all exit with status `0`. The governed `/trade` browser QA pass must still explicitly account for the required widths `375`, `768`, `1280`, and `1440`, and any browser-inspection sessions started for that verification must be closed with `npm run browser:cleanup`.

This plan is intentionally not judged by a large `LCP` win. If `FCP`, `LCP`, or visible route readiness regress materially, the change should be considered a failure even if `blockingTimeProxyMs` improves.

## Idempotence and Recovery

This work is safe to iterate on because it is confined to startup sequencing and tests. The baseline profiler can be rerun multiple times; the final acceptance run should always use the fresh build-backed `browser:profile:trade-startup` command rather than the cached variant. If an intermediate step regresses route correctness, the safest rollback is to restore the previous sequencing in `/hyperopen/src/hyperopen/startup/init.cljs` and `/hyperopen/src/hyperopen/startup/runtime.cljs` before touching any lower-level route or render code.

Do not broaden rollback into unrelated module-loading changes. This plan does not modify `shadow-cljs.edn`, chart interop files, or the trade render loop. If tests fail because a deferred restore or route refresh moved too far, move only that restore or route action back into the earlier phase and rerun the focused startup tests before changing anything else.

## Artifacts and Notes

The external directional baseline for this plan is the production trace file:

`/Users/barry/Downloads/Trace-20260413T113205.json`

The trace currently supports these working assumptions:

- first paint and first contentful paint occur at about `358ms`
- largest contentful paint occurs at about `509ms`
- main-frame load occurs at about `792ms`
- cached `main.js` still costs about `64.6ms` to evaluate and about `32.6ms` to compile
- cached `trade_chart.js` costs about `12.5ms` to evaluate and about `6.0ms` to compile
- app-owned post-paint frame work still shows repeated `13–25ms` `FunctionCall` slices from `main.js`

The checked-in local profiler that must be used for acceptance is:

`/hyperopen/tools/browser-inspection/src/trade_startup_profile.mjs`

Record the fresh baseline and final run directories in this section when implementation begins.

Implementation evidence for this wave:

- Baseline startup profile run directory:
  `/Users/barry/.codex/worktrees/e0de/hyperopen/tmp/browser-inspection/trade-startup-profile-2026-04-13T16-19-37-712Z-bcf350f3`
- Baseline `profile.json` summary:
  `blockingTimeProxyMs=78`
  `maxSingleBlockingTaskMs=123`
  `tradeRootVisibleMs=301`
  `orderFormVisibleMs=306.3`
- Final startup profile run directory:
  `/Users/barry/.codex/worktrees/e0de/hyperopen/tmp/browser-inspection/trade-startup-profile-2026-04-13T16-31-33-362Z-8f9c4587`
- Final `profile.json` summary:
  `blockingTimeProxyMs=51`
  `maxSingleBlockingTaskMs=101`
  `tradeRootVisibleMs=243.7`
  `orderFormVisibleMs=277`
- Measured deltas:
  `blockingTimeProxyMs=-27ms (-34.6%)`
  `maxSingleBlockingTaskMs=-22ms (-17.9%)`
  `tradeRootVisibleMs=-57.3ms`
  `orderFormVisibleMs=-29.3ms`
- Targeted Playwright smoke:
  `npx playwright test tools/playwright/test/routes.smoke.spec.mjs --grep "trade cold startup does not render the static boot loading shell|trade route exposes score-bearing accessibility hooks"`
  Result: pass (`2/2`)
- `npm test`
  Result: pass
- `npm run test:websocket`
  Result: pass
- Governed `/trade` QA run directory:
  `/Users/barry/.codex/worktrees/e0de/hyperopen/tmp/browser-inspection/design-review-2026-04-13T16-32-15-986Z-62d80123`
  Result: `PASS` across widths `375`, `768`, `1280`, and `1440`
- `npm run browser:cleanup`
  Result: pass
- `npm run check`
  Result: pass

## Interfaces and Dependencies

In `/hyperopen/src/hyperopen/startup/init.cljs`, define or refactor toward these stable responsibilities:

    restore-critical-ui-state!
    restore-deferred-ui-state!
    initialize-systems!

`restore-critical-ui-state!` must own only the restores that directly shape the initial visible `/trade` route. `restore-deferred-ui-state!` must own the non-visible preference restores moved out of the synchronous boot path by this plan.

In `/hyperopen/src/hyperopen/startup/runtime.cljs`, define:

    yield-to-main!

This helper must return a JavaScript `Promise` and must prefer `globalThis.scheduler?.yield?.()` when available, with a zero-delay timeout fallback through `/hyperopen/src/hyperopen/platform.cljs` when it is not.

Also in `/hyperopen/src/hyperopen/startup/runtime.cljs`, split the current startup orchestration into one explicit immediate post-render phase and one explicit background phase. The immediate phase must keep the trade route usable. The background phase must own only non-visible or clearly deferrable work.

Finally, replace the current multi-dispatch route refresh fan-out in startup with one current-route-aware refresh helper. The end state must preserve correct behavior on non-trade routes while ensuring the default `/trade` route no longer pays for five unrelated startup dispatches.

Plan revision note: created on 2026-04-13 to turn the 2026-04-13 production trace review into an implementable, repo-native plan focused only on the highest risk-adjusted return changes still available after the completed 2026-04-01 startup TBT wave.
