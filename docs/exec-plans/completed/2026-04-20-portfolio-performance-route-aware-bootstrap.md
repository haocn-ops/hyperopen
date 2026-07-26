# Make portfolio performance metrics bootstrap route-aware

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. It is self-contained so a future contributor can continue from this file without relying on conversation history.

Tracked issue: `hyperopen-43ml` ("Make portfolio performance metrics bootstrap route-aware").

## Purpose / Big Picture

Opening the production portfolio performance metrics URL should spend its first network budget on the data that is actually needed to draw that route: portfolio summary, fee context, account mode, spot balances, the selected BTC benchmark candles, and the route query state that says the visible tab is `performance-metrics`. The current startup path is route-agnostic. It starts portfolio data, account tables, per-DEX clearinghouse backfills, and asset-selector market expansion together, so the visible portfolio metrics compete with non-visible account and trade data.

After this change, a first load of `/portfolio?spectate=0x162cc7c861ebd0c06b3d72319201150482518185&range=1y&scope=all&chart=returns&bench=BTC&tab=performance-metrics` should still render the portfolio summary, user fee context, and BTC comparison metrics, while open orders, fills, funding history, per-DEX clearinghouse/open-orders work, and asset-selector market fan-out wait for idle or background work. The visible proof is a focused test run showing the immediate startup calls are route-aware, plus a follow-up browser trace showing the early `/info` request wave no longer contains those non-visible account and selector requests.

## Scope / Non-Goals

The scope is limited to startup request ordering for portfolio routes whose visible account tab is `:performance-metrics`. In code, that means route state under `[:router :path]` is a portfolio route and query/restored state under `[:portfolio-ui :account-info-tab]` normalizes to `:performance-metrics`.

The slashless production redirect is not in scope. It is tracked separately by `docs/exec-plans/completed/2026-04-20-eliminate-portfolio-slash-redirect.md`; that completed plan covers the production `308` redirect from `/portfolio` to `/portfolio/`.

This plan does not change public APIs, portfolio metric formulas, chart rendering, websocket stream semantics, or the account table UI. It should not edit `/hyperopen/src/**` during the spec-writing phase. The later `worker` phase may edit `src/**` as described below.

## Baseline and Performance Rationale

Baseline evidence comes from `/Users/barry/Downloads/Trace-20260420T112210.json`, captured by Chrome DevTools on 2026-04-20 with `3G` trace throttling. The production no-slash `/portfolio` request redirects with HTTP `308` to `/portfolio/`, and the redirect costs about 2.0 seconds under that throttling. First Contentful Paint is about 4.13 seconds and Largest Contentful Paint is about 4.37 seconds. The first `/info` POST wave begins around 4.28 seconds, with later `/info` waves continuing through about 8.8 seconds. Main-thread Total Blocking Time is low, so the target is request contention and route-agnostic bootstrap rather than JavaScript CPU.

The workload assumption for this plan is the production performance URL above, including spectate mode, range `1y`, scope `all`, chart `returns`, benchmark `BTC`, and tab `performance-metrics`. A simpler priority-only change is insufficient because the current code still starts many `/info` POSTs in the same startup window; even `:priority :low` requests occupy browser/server/network capacity during the route's visible data load. The implementation must avoid starting non-visible requests until idle/background scheduling has run.

## Progress

- [x] (2026-04-20 16:35Z) Created this active ExecPlan for `hyperopen-43ml` and recorded the supplied trace baseline, scope, implementation decisions, TDD surfaces, validation commands, and non-goals.
- [x] (2026-04-20 20:47Z) Added RED tests in `test/hyperopen/account/surface_service_test.cljs` and `test/hyperopen/startup/runtime_test.cljs` proving the current bootstrap starts non-visible account and selector work too early for the portfolio performance metrics route. The focused command reaches the tests and fails for the expected behavior gaps.
- [x] (2026-04-20 21:19Z) Implemented the route-aware account bootstrap split in `src/hyperopen/account/surface_service.cljs` and `src/hyperopen/startup/runtime.cljs`. The performance-metrics route now keeps portfolio-visible account data immediate and schedules open orders, fills, funding history, and per-DEX stage-B work for background warmup.
- [x] (2026-04-20 21:19Z) Implemented the route-aware critical bootstrap gate in `src/hyperopen/startup/runtime.cljs`. On portfolio performance metrics, the critical path now marks readiness without starting asset-context or asset-selector bootstrap fan-out.
- [x] (2026-04-20 21:19Z) Moved route-aware startup tests into `test/hyperopen/startup/route_aware_bootstrap_test.cljs` after the namespace-size guard rejected additional coverage in `test/hyperopen/startup/runtime_test.cljs`.
- [x] (2026-04-20 21:33Z) Validation passed: focused route-aware tests, `npm run check`, `npm test`, `npm run test:websocket`, and `npm run test:playwright:smoke`.
- [x] (2026-04-20 21:40Z) Revalidated after final cleanup: focused route-aware command ran 33 tests / 232 assertions; `npm test` ran 3327 tests / 18163 assertions; `npm run test:websocket` ran 449 tests / 2701 assertions; `npm run test:playwright:smoke` ran 22 browser smoke tests.
- [x] (2026-04-20 22:14Z) Built a local release artifact with `npm run build` and measured the target route with Playwright against `out/release-public`. The first measurement showed idle scheduling was too aggressive: hidden account requests and selector bootstrap still started before the visible portfolio wave settled.
- [x] (2026-04-20 22:24Z) Tightened the portfolio performance-metrics route to use a real timeout delay, not immediate `requestIdleCallback`, for hidden account warmup and deferred selector bootstrap.
- [x] (2026-04-20 22:32Z) Rebuilt and remeasured the local release artifact. Across 3 local runs, `hiddenBefore1200` and `selectorBefore1200` were empty, and the first 1.5 seconds after the first `/info` request contained only visible route inputs: BTC candle snapshots, spot clearinghouse state, user abstraction, portfolio, and user fees.
- [x] (2026-04-20 22:40Z) Revalidated after the local-build measurement fix: focused route-aware command ran 35 tests / 239 assertions; `npm run check` passed; `npm test` ran 3329 tests / 18170 assertions; `npm run test:websocket` ran 449 tests / 2701 assertions; `npm run test:playwright:smoke` ran 22 browser smoke tests.
- [x] (2026-04-20 22:45Z) Recorded the deployed production trace as a release-readiness follow-up rather than a blocker for this local implementation. The local release build measurement is the implementation acceptance artifact for this pass.

## Surprises & Discoveries

- Observation: `src/hyperopen/startup/runtime.cljs` currently calls `prefetch-order-history!` before `hyperopen.account.surface-service/bootstrap-account-surfaces!`.
  Evidence: `bootstrap-account-data!` resets account state, calls `prefetch-order-history!`, then calls `account-surface-service/bootstrap-account-surfaces!`. This is another non-visible account-history request surface, but the requested implementation decisions specifically name open orders, user fills, funding history, per-DEX stage-B work, and asset-selector fan-out. If implementation evidence shows order history still competes in the same first `/info` wave, update this plan's Decision Log before changing that surface.

- Observation: `src/hyperopen/startup/route_refresh.cljs` already preserves portfolio benchmark candle bootstrap on address refresh.
  Evidence: when `new-address` is non-nil and the route is a portfolio route, `current-route-refresh-effects` appends `[:actions/select-portfolio-chart-tab (get-in state [:portfolio-ui :chart-tab])]`. `select-portfolio-chart-tab` in `src/hyperopen/portfolio/actions.cljs` fetches selected benchmark candles when the chart tab is `:returns`.

- Observation: query state is restored before route-load and post-render startup work.
  Evidence: `src/hyperopen/startup/init.cljs` calls `restore-route-query-state!` during critical restore and again after router init; `src/hyperopen/app/startup.cljs` calls `route-query-state/restore-current-route-query-state!` in the route-change handler before calculating route-change effects.

- Observation: The focused RED run needed locked npm dependencies installed first because `node_modules` was absent.
  Evidence: The first focused command compiled but failed before tests with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`; `npm ci` installed the lockfile dependencies, and the rerun reached the new tests with 13 expected failures and 0 errors.

- Observation: `test/hyperopen/startup/runtime_test.cljs` is already at its governed namespace-size limit.
  Evidence: The first `npm run check` after implementation failed with `[size-exception-exceeded] test/hyperopen/startup/runtime_test.cljs - namespace has 1382 lines; exception allows at most 1269`. The route-aware startup tests were moved to `test/hyperopen/startup/route_aware_bootstrap_test.cljs`, and the namespace-size check then passed.

- Observation: On the portfolio performance metrics route, order-history prefetch is also non-visible account-table work.
  Evidence: The implemented route-aware branch skips `prefetch-order-history!` only when `portfolio-performance-metrics-route?` is true. The sibling test proves non-performance portfolio tabs still start order-history prefetch with `{:priority :low}`.

- Observation: `requestIdleCallback` is not a sufficient delay mechanism for this route.
  Evidence: A local release build measured with Playwright showed hidden account work and selector bootstrap still starting within the first local request wave when hidden work was scheduled through the existing idle scheduler. The corrected build uses `setTimeout` for the route-aware delay and remeasures with no hidden account or selector requests before 1200ms.

## Decision Log

- Decision: Keep portfolio summary, user fees, user abstraction, and spot clearinghouse immediate on the portfolio performance metrics route.
  Rationale: these requests feed the visible portfolio metrics, fee schedule context, account mode safety, and balances/account safety cues. Deferring them risks blank or misleading first-route content.
  Date/Author: 2026-04-20 / Codex

- Decision: Defer open orders, user fills, funding history, per-DEX stage-B clearinghouse/open-orders work, and asset-selector bootstrap/full fan-out on the portfolio performance metrics route.
  Rationale: those surfaces are not visible when the account tab is `performance-metrics`, and the trace indicates request contention rather than main-thread CPU is the bottleneck.
  Date/Author: 2026-04-20 / Codex

- Decision: Also skip startup order-history prefetch on the portfolio performance metrics route.
  Rationale: order history is account-table data and is not visible on the selected `performance-metrics` tab. Keeping it eager would leave a non-visible `/info` request competing with the route's visible portfolio inputs.
  Date/Author: 2026-04-20 / Codex

- Decision: Skip both critical-path asset contexts and asset-selector bootstrap fan-out for this route, while preserving the deferred full selector bootstrap path.
  Rationale: the target first viewport is portfolio metrics, not the trade asset selector. Deferring both requests removes avoidable startup contention; the smoke suite verifies primary routes still render, and deferred bootstrap keeps selector data available later.
  Date/Author: 2026-04-20 / Codex

- Decision: Use a strict timeout delay for portfolio performance-metrics background warmups instead of the shared idle scheduler.
  Rationale: the shared scheduler may run immediately when the browser reports idle time, which is good for general startup but bad for this specific route because it can reintroduce hidden request contention before visible portfolio inputs finish.
  Date/Author: 2026-04-20 / Codex

- Decision: Preserve the BTC benchmark candle path through existing portfolio route refresh and chart-tab actions.
  Rationale: the performance metrics route depends on selected benchmark candles for visible comparison columns. Reusing the existing `select-portfolio-chart-tab` effect keeps behavior deterministic and avoids a new one-off startup effect.
  Date/Author: 2026-04-20 / Codex

- Decision: Do not fold the completed slash redirect fix into this plan.
  Rationale: redirect removal is already tracked and completed in `docs/exec-plans/completed/2026-04-20-eliminate-portfolio-slash-redirect.md`; this plan targets the later `/info` request waves that remain after the HTML document is loaded.
  Date/Author: 2026-04-20 / Codex

## Outcomes & Retrospective

The implementation adds one explicit route profile for portfolio performance metrics instead of changing global startup behavior. That keeps the risk bounded: non-performance portfolio tabs, trade, staking, leaderboard, and vault routes continue to use the existing eager startup shape, while the target route defers the surfaces that the user cannot see.

The code change increases bootstrap branching, but the branch is covered by tests at two levels. `test/hyperopen/account/surface_service_test.cljs` proves the immediate account calls are only spot clearinghouse, user abstraction, portfolio, and user fees before the captured background callback runs. `test/hyperopen/startup/route_aware_bootstrap_test.cljs` proves runtime route detection skips order-history prefetch and critical asset fan-out only for the performance-metrics route, while neighboring portfolio tabs keep eager behavior.

Validation proves the code is correct against the local suite and that route smoke still renders. The local release measurement also proves the intended request-order improvement: the early request window now contains visible portfolio inputs only, while hidden account and selector work starts later. It does not prove the exact production time saved; that remains a release-readiness follow-up before or after deployment using the same target URL and throttling as the baseline.

## Context and Orientation

The app is a ClojureScript single-page application. Startup is split across `src/hyperopen/startup/init.cljs`, `src/hyperopen/app/startup.cljs`, `src/hyperopen/startup/runtime.cljs`, and `src/hyperopen/account/surface_service.cljs`.

`src/hyperopen/startup/init.cljs` owns the high-level sequence. It restores critical UI state, initializes wallet and router state, kicks the first render, then schedules post-render startup. Post-render startup installs global handlers, marks trade secondary panels ready, loads post-render route effects, initializes remote data streams, yields once, restores non-visible UI preferences, and registers the icon service worker.

`src/hyperopen/app/startup.cljs` adapts app-specific dependencies into startup runtime calls. Its route-change handler restores route query state before deciding which route module and route data effects to dispatch. Its `initialize-remote-data-streams!` wrapper calls into `src/hyperopen/startup/runtime.cljs`.

`src/hyperopen/startup/runtime.cljs` owns general startup behavior. `bootstrap-account-data!` runs when the effective account changes. It resets account-derived state, prefetches account history, and delegates account surface fetch ordering to `hyperopen.account.surface-service/bootstrap-account-surfaces!`. `start-critical-bootstrap!` currently starts asset contexts and asset-selector market bootstrap immediately. `run-deferred-bootstrap!` starts the full asset-selector market load later through idle/timeout scheduling.

`src/hyperopen/account/surface_service.cljs` owns account surface fetch ordering. `bootstrap-account-surfaces!` currently starts or schedules open orders, user fills, spot clearinghouse, user abstraction, portfolio summary, user fees, funding history, and per-DEX stage-B work without checking whether those account tables are visible on the current route.

`src/hyperopen/startup/route_refresh.cljs` owns current-route refresh effects. For portfolio routes, when an account address is available, it dispatches `[:actions/select-portfolio-chart-tab <chart-tab>]`, which lets `src/hyperopen/portfolio/actions.cljs` request benchmark candles for the selected returns benchmark coins.

The term "stage-B" means the second wave of account bootstrap work after `ensure-perp-dexs!` resolves known DEX names. In this repo, `stage-b-account-bootstrap!` refreshes per-DEX open orders and per-DEX clearinghouse states. The term "asset-selector fan-out" means `fetch-asset-selector-markets!`, which expands selector market metadata by requesting spot metadata and DEX-specific market data. The term "visible metrics inputs" means the route query and data needed by the currently visible portfolio performance metrics tab: range, scope, chart, benchmark selection, portfolio summary, user fees, account mode, spot balances, and benchmark candles.

## Plan of Work

Start with tests. In `test/hyperopen/account/surface_service_test.cljs`, add a RED test for `bootstrap-account-surfaces!` with state shaped like the target URL: `[:router :path]` is `"/portfolio"`, `[:portfolio-ui :account-info-tab]` is `:performance-metrics`, chart tab is `:returns`, range is `:one-year`, and returns benchmark coins are `["BTC"]`. The test should stub every fetch function and assert that the immediate calls are only `:spot`, `:abstraction`, `:portfolio`, and `:user-fees`. It should assert that `:open-orders`, `:fills`, `:fundings`, `:ensure-perp-dexs`, and stage-B calls do not happen before the captured idle/background callback runs. After running the callback, the test should assert those deferred calls happen for the same address and still respect stale-address guards.

In `test/hyperopen/startup/runtime_test.cljs`, add or update RED tests around `bootstrap-account-data!`, `start-critical-bootstrap!`, and `initialize-remote-data-streams!`. The first test should prove the runtime forwards the route-aware performance-metrics profile to `bootstrap-account-surfaces!` and does not make non-visible account work immediate. The second should prove `start-critical-bootstrap!` does not call `fetch-asset-selector-markets!` with `{:phase :bootstrap}` on the portfolio performance metrics route, while the existing trade/default route behavior still does. The third should prove deferred bootstrap remains scheduled so `run-deferred-bootstrap!` can later perform full selector fan-out in the background. The initial plan expected these tests to live in `runtime_test.cljs`; after the namespace-size guard fired, the route-specific coverage moved to `test/hyperopen/startup/route_aware_bootstrap_test.cljs`.

Implement the account bootstrap split in `src/hyperopen/account/surface_service.cljs`. Add a small pure predicate, for example `portfolio-performance-metrics-route?`, that accepts state and returns true only when the current route is a portfolio route and `portfolio-ui/account-info-tab` normalizes to `:performance-metrics`. Use `hyperopen.portfolio.routes/portfolio-route?` and `hyperopen.portfolio.actions/normalize-portfolio-account-info-tab` rather than string-only checks. Keep this helper pure so tests can exercise route decisions without timers.

Still in `src/hyperopen/account/surface_service.cljs`, split `bootstrap-account-surfaces!` into immediate and background pieces. The immediate piece must keep the existing calls to `fetch-spot-clearinghouse-state!`, `fetch-user-abstraction!`, `fetch-portfolio!`, and `fetch-user-fees!` with `{:priority :high}`. On non-performance routes, preserve the existing open-orders, fills, funding-history, and `ensure-perp-dexs!` behavior. On the portfolio performance metrics route, schedule that non-visible work through an injected idle/background scheduler. The scheduler dependency should be explicit in the deps map, with a deterministic fallback so unit tests can capture and run the callback.

Update `src/hyperopen/startup/runtime.cljs` so `bootstrap-account-data!` passes the idle/background scheduler into `bootstrap-account-surfaces!`. Prefer the existing `schedule-idle-or-timeout!` shape already used for deferred selector bootstrap. The background callback must re-check the active effective account before starting non-visible work, matching the current stale-address guard pattern.

Update `src/hyperopen/startup/runtime.cljs` so `start-critical-bootstrap!` is route-aware. When the store is on the portfolio performance metrics route, it should still run `fetch-asset-contexts!` if the existing UI safety need remains, but it must not run `fetch-asset-selector-markets!` with `{:phase :bootstrap}` in the critical path. Keep `run-deferred-bootstrap!` and `schedule-deferred-bootstrap!` available so the full selector market load still happens through idle/background work unless cached state makes it unnecessary.

If `src/hyperopen/app/startup.cljs` needs wiring updates, keep them narrow. It already passes `:schedule-idle-or-timeout!`, `:per-dex-stagger-ms`, and startup delays into `startup-collaborators/startup-base-deps`. Add only the dependency needed for the route-aware background account work; do not change router behavior, route module loading, or public action/effect ids.

After implementation, update this plan's Progress, Surprises & Discoveries, Decision Log, and Outcomes & Retrospective with the exact tests, request-order observations, and any changed interpretation of order-history prefetch.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/7616/hyperopen`.

1. Confirm the working tree before edits:

    git status --short --branch

2. Add RED tests in the two approved TDD files:

    test/hyperopen/account/surface_service_test.cljs
    test/hyperopen/startup/route_aware_bootstrap_test.cljs

3. Run the focused RED command and expect failures showing non-visible work still starts immediately:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.account.surface-service-test --test=hyperopen.startup.route-aware-bootstrap-test --test=hyperopen.startup.runtime-test

4. Implement the smallest code change in:

    src/hyperopen/account/surface_service.cljs
    src/hyperopen/startup/runtime.cljs
    src/hyperopen/app/startup.cljs

   Only touch `src/hyperopen/app/startup.cljs` if dependency wiring is required.

5. Rerun the focused command and expect the two target namespaces to pass.

6. Run the repository gates required when code changes:

    npm run check
    npm test
    npm run test:websocket

7. Because this is UI-facing startup behavior, run the smallest deterministic browser smoke that covers route startup. Start with:

    npm run test:playwright:smoke

   If the stock smoke command hits an unrelated dev-server race, document the failure and rerun against a prewarmed server using the same Playwright assertions, following `docs/BROWSER_TESTING.md`.

8. Capture a follow-up Chrome trace with the same production URL and throttling assumptions as the baseline when deployment or a production-like preview is available. Record whether the early `/info` wave excludes `frontendOpenOrders`, `userFills`, `fundingHistory` or `userFundings`, per-DEX clearinghouse/open-order requests, and asset-selector market fan-out.

## Validation and Acceptance

Acceptance criterion 1: Running `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.account.surface-service-test --test=hyperopen.startup.route-aware-bootstrap-test --test=hyperopen.startup.runtime-test` must pass after implementation. The new account surface test must fail before implementation because open orders, fills, funding history, and per-DEX stage-B work currently start immediately.

Acceptance criterion 2: In `test/hyperopen/account/surface_service_test.cljs`, the portfolio performance metrics route case must observe immediate calls for portfolio summary, user fees, user abstraction, and spot clearinghouse only. The same test must observe open orders, user fills, funding history, `ensure-perp-dexs!`, and stage-B per-DEX clearinghouse/open-orders only after the idle/background callback runs.

Acceptance criterion 3: In `test/hyperopen/startup/route_aware_bootstrap_test.cljs`, the route-aware critical bootstrap test must observe no `fetch-asset-selector-markets!` call with `{:phase :bootstrap}` while the store route is `/portfolio` and the visible account tab is `:performance-metrics`. A sibling assertion must show that a non-performance portfolio tab still calls selector bootstrap as before.

Acceptance criterion 4: Deferred bootstrap must still be scheduled once and remain idempotent. Existing runtime coverage must continue to allow `run-deferred-bootstrap!` to request `fetch-asset-selector-markets!` with `{:phase :full}` unless the existing cache-skip conditions apply.

Acceptance criterion 5: Existing portfolio benchmark candle behavior must remain observable. For the target state with `:chart-tab :returns`, `:summary-time-range :one-year`, and `:returns-benchmark-coins ["BTC"]`, route refresh on an available account address must still lead to `[:effects/fetch-candle-snapshot :coin "BTC" :interval :12h :bars 900]` through `select-portfolio-chart-tab`. Existing tests may already cover this; if they are updated, keep the assertion concrete.

Acceptance criterion 6: The implementation must not remove websocket stream guards. When streams are live or event-driven, the existing tests proving stream-backed skips and fallbacks for open orders, user fills, and funding history must continue to pass.

Acceptance criterion 7: `npm run check`, `npm test`, and `npm run test:websocket` must pass, or this plan must document the exact unrelated blocker and the focused command that proves this change itself works.

Acceptance criterion 8: A follow-up trace or production-like waterfall should show that the first `/info` wave for the target URL prioritizes the visible portfolio requests and BTC benchmark candle input. Non-visible open orders, user fills, funding history, per-DEX clearinghouse/open-orders, and asset-selector market fan-out should not start until idle/background scheduling. If trace capture is unavailable in the implementation session, record the missing artifact as a remaining verification item rather than claiming the performance outcome.

## Idempotence and Recovery

The test commands and startup benchmarks are safe to rerun. The new idle/background scheduling must be idempotent: repeated startup calls for the same bootstrapped address should not schedule duplicate background work, and stale address changes must prevent old callbacks from fetching for the previous account.

If the route-aware account split breaks visible portfolio data, first restore immediate portfolio, user-fees, user-abstraction, and spot-clearinghouse calls before inspecting deferred work. If selector bootstrap deferral leaves the asset selector empty when the user opens it, route the on-open action through the existing full selector fetch path rather than restoring route-agnostic critical-path fan-out. Do not use destructive git commands such as `git reset --hard` or `git checkout --` unless the user explicitly asks for them.

## Artifacts and Notes

Baseline trace source:

    /Users/barry/Downloads/Trace-20260420T112210.json

Baseline facts to preserve in later reports:

    production /portfolio -> /portfolio/ redirect: HTTP 308, about 2.0s under trace throttling
    FCP: about 4.13s
    LCP: about 4.37s
    first /info POST wave: about 4.28s
    later /info POST waves continue through: about 8.8s
    main-thread TBT: low
    target: request contention and route-agnostic bootstrap

Target URL:

    /portfolio?spectate=0x162cc7c861ebd0c06b3d72319201150482518185&range=1y&scope=all&chart=returns&bench=BTC&tab=performance-metrics

Related completed redirect plan:

    docs/exec-plans/completed/2026-04-20-eliminate-portfolio-slash-redirect.md

Focused TDD files:

    test/hyperopen/account/surface_service_test.cljs
    test/hyperopen/startup/route_aware_bootstrap_test.cljs
    test/hyperopen/startup/runtime_test.cljs

## Interfaces and Dependencies

`src/hyperopen/account/surface_service.cljs` should continue exposing:

    bootstrap-account-surfaces!
    stage-b-account-bootstrap!
    schedule-stream-backed-fallback!
    refresh-after-user-fill!
    refresh-after-order-mutation!

`bootstrap-account-surfaces!` may accept additional optional deps for route-aware scheduling, but existing callers must continue to work. The new optional scheduler should be testable with a callback-capturing function and should default to a safe timeout/idle fallback when omitted.

`src/hyperopen/startup/runtime.cljs` should continue exposing:

    bootstrap-account-data!
    start-critical-bootstrap!
    run-deferred-bootstrap!
    schedule-deferred-bootstrap!
    initialize-remote-data-streams!

Any new helper that decides whether the current route is the portfolio performance metrics route should be pure and deterministic. It should read only the supplied state and should not inspect browser globals.

`src/hyperopen/app/startup.cljs` should keep existing action/effect ids unchanged. No public route, query parameter, or websocket topic contract should change.

## Revision Notes

- 2026-04-20 / Codex: Initial active ExecPlan created for `hyperopen-43ml`, scoped to route-aware startup request ordering for the portfolio performance metrics route. The plan records the supplied trace baseline, separates the completed slash redirect work from this request-contention work, and anchors acceptance to the requested TDD surfaces plus repo validation gates.
- 2026-04-20 / Codex: Implementation and local validation completed. Route-aware runtime tests moved to a new namespace to satisfy the namespace-size guard. A production-like follow-up trace remains open to quantify the actual timing improvement.
