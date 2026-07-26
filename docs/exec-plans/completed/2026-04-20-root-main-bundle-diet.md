# Shrink the root main bundle for portfolio startup

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. It is self-contained so a future contributor can continue from this file without relying on conversation history.

Tracked issue: `hyperopen-5o4d` ("Shrink portfolio startup root bundle").

## Purpose / Big Picture

The production portfolio page currently loads a very large Shadow CLJS startup bundle before its route-specific portfolio module can render. A PageSpeed desktop report for `https://hyperopen.xyz/portfolio`, fetched on 2026-04-20, measured a 52 performance score, 2.1s LCP, 2,030ms total blocking time, and an audit-estimated 1.8s total blocking time saving from reducing JavaScript bootup work. The largest startup artifact is `main.*.js`, which contains root app runtime code plus non-critical surfaces that are not needed for the first portfolio paint, such as modal implementations and route-specific collaborators.

After this work, a production build should show a smaller `out/release-public/js/main.*.js` file while the portfolio route and critical trade startup still work. The user-visible effect is that `/portfolio` has less JavaScript to parse, compile, and execute before the page becomes responsive. The measurable proof is a before-and-after production build benchmark using raw, gzip, and brotli byte sizes for `main.*.js`, plus the repository's required validation gates.

## Progress

- [x] (2026-04-20 13:32Z) Created and claimed `bd` issue `hyperopen-5o4d`.
- [x] (2026-04-20 13:32Z) Ran the initial production build attempt; it failed before compilation because this fresh worktree had no `node_modules` and `tailwindcss` was unavailable.
- [x] (2026-04-20 13:32Z) Ran `npm install` to install locked local dependencies.
- [x] (2026-04-20 13:33Z) Reran `npm run build`; the production app and workers compiled successfully and release artifacts were written to `out/release-public`.
- [x] (2026-04-20 13:33Z) Recorded the baseline production bundle benchmark for `main.8796E662E57246EF2133F5812AECA0FC.js`: 2,721,359 raw bytes, 624,506 gzip bytes, and 476,740 brotli bytes.
- [x] (2026-04-20 13:33Z) Dispatched read-only explorer agents for root UI imports and runtime collaborator/action-adapter imports.
- [x] (2026-04-20 13:34Z) Received the root UI explorer report. It ranked `hyperopen.views.funding-modal` as the best first split because `hyperopen.views.app-view` always requires and renders it even while closed.
- [x] (2026-04-20 13:35Z) Received the runtime explorer report. It found that root registry aggregators in `runtime/collaborators.cljs`, `app/effects.cljs`, and `runtime/action_adapters.cljs` are a larger follow-up boundary and recommended breaking aggregators before route self-registration.
- [x] (2026-04-20 13:39Z) Added RED coverage for the funding-modal lazy surface contract. The focused command `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.funding.actions-test --test=hyperopen.app.effects-test --test=hyperopen.runtime.wiring-test` failed as expected because funding modal open actions do not emit `[:effects/load-surface-module :funding-modal]` and runtime effect deps do not expose `:load-surface-module`.
- [x] (2026-04-20 13:51Z) Implemented the first funding modal root-surface split: added a lazy `:funding-modal` surface loader, a `:funding_modal` Shadow module entry, runtime `:effects/load-surface-module` wiring, funding open-action load effects, and removed the static funding modal require from `app_view.cljs`.
- [x] (2026-04-20 13:51Z) Reran the focused RED contract command after implementation. It passed with 55 tests, 220 assertions, 0 failures, and 0 errors.
- [x] (2026-04-20 13:51Z) Ran `npx shadow-cljs --force-spawn compile app`; the app build completed with 832 files, 811 compiled, 0 warnings.
- [x] (2026-04-20 13:51Z) Ran `npm run test:websocket`; it passed with 449 tests, 2,701 assertions, 0 failures, and 0 errors.
- [x] (2026-04-20) Added the spectate-mode modal to the existing lazy surface module pattern and reran the frozen spectate-focused contract. `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.account.spectate-mode-actions-test --test=hyperopen.header.actions-test --test=hyperopen.views.app-view-test` passed with 35 tests, 120 assertions, 0 failures, and 0 errors.
- [x] (2026-04-20) Ran `npx shadow-cljs --force-spawn compile app` after the spectate module-map change; the app build completed with 835 files, 47 compiled, 0 warnings.
- [x] (2026-04-20) Ran deterministic browser smoke for the UI-touching spectate split. `npm run test:playwright:smoke` passed with 22 tests, 0 failures.
- [x] (2026-04-20 14:11Z) Resolved the broad test/tooling blockers by updating the public compat expectation and raising the funding actions test namespace-size exception to 1,320 lines with a scoped reason.
- [x] (2026-04-20 14:13Z) Ran the post-change production build and benchmark. `main.1E87D483EA5081F043A7396EDF38295F.js` measured 2,657,341 raw bytes, 614,390 gzip bytes, and 469,485 brotli bytes.
- [x] (2026-04-20 14:19Z) Ran `npm run check`; it passed.
- [x] (2026-04-20 14:20Z) Ran `npm test`; it passed with 3,317 tests, 18,117 assertions, 0 failures, and 0 errors.
- [x] (2026-04-20 14:21Z) Ran `npm run test:websocket`; it passed with 449 tests, 2,701 assertions, 0 failures, and 0 errors.
- [x] (2026-04-20 14:24Z) Reran `npm run test:playwright:smoke` from the parent thread after stopping the stale port-8080 Java process; it passed with 22 tests, 0 failures.

## Surprises & Discoveries

- Observation: The local worktree did not have npm dependencies installed.
  Evidence: The first `npm run build` failed in `css:build` with `sh: tailwindcss: command not found`. After `npm install`, the same build completed.

- Observation: The current production build closely matches the PageSpeed report's main-bundle byte size.
  Evidence: PageSpeed reported the deployed main script at 2,721,318 resource bytes and 625,472 transfer bytes. The local production build produced `main.8796E662E57246EF2133F5812AECA0FC.js` at 2,721,359 raw bytes and 624,506 gzip bytes.

- Observation: `hyperopen.views.app-view` always imports and invokes modal views that usually render nothing.
  Evidence: `src/hyperopen/views/app_view.cljs` requires `hyperopen.views.funding-modal`, `hyperopen.views.spectate-mode-modal`, `hyperopen.views.agent-trading-recovery-modal`, and `hyperopen.views.order-submit-confirmation-modal`, then calls each view after `main`, regardless of whether the corresponding modal is open.

- Observation: The first root UI explorer ranked the funding modal split as the highest-return isolated candidate.
  Evidence: The explorer reported that `hyperopen.views.funding-modal` pulls deposit, send, transfer, withdraw, shared modal views, and funding action view-model code into `:main`, while it is only needed after funding modal state opens.

- Observation: Runtime aggregators are a second, larger root-bundle attractor but carry higher ordering risk than the funding-modal split.
  Evidence: The runtime explorer reported that `src/hyperopen/runtime/collaborators.cljs` eagerly requires account history, funding, leaderboard, funding comparison, vaults, staking, wallet, chart, and order collaborator maps. It also reported that `src/hyperopen/runtime/action_adapters.cljs` imports a broad adapter set and that the registry currently expects all catalog handlers to exist at root bootstrap.

- Observation: The production funding modal command namespace stayed within its current namespace-size exception after implementation cleanup, but the materialized RED test namespace now exceeds its exception.
  Evidence: After shrinking the production edit, `wc -l src/hyperopen/funding/application/modal_commands.cljs test/hyperopen/funding/actions_test.cljs` reported 538 and 1,310 lines respectively. `npm run lint:namespace-sizes` failed only on `test/hyperopen/funding/actions_test.cljs`, whose exception allows at most 1,300 lines.

- Observation: The broad CLJS suite contains one stale pre-existing public-compat assertion for the old funding-modal effect vector.
  Evidence: `npm test` ran 3,317 tests with 18,133 assertions and failed once in `hyperopen.core-public-actions-test`, where `compat/set-funding-modal` was still expected to return only `[:effects/save ...]` instead of the new approved leading `[:effects/load-surface-module :funding-modal]`.

- Observation: Splitting only the funding modal produced a real but smaller-than-target root bundle reduction.
  Evidence: The funding-only production build measured `main.E9963418D2DAA7139F7DF8CF9E4259E0.js` at 2,673,294 raw bytes, 617,807 gzip bytes, and 471,920 brotli bytes. Compared to baseline, that was a 48,065 raw-byte and 6,699 gzip-byte reduction.

- Observation: Adding the spectate mode modal split pushed the first pass over the 10KB gzip reduction target.
  Evidence: The combined production build measured `main.1E87D483EA5081F043A7396EDF38295F.js` at 2,657,341 raw bytes, 614,390 gzip bytes, and 469,485 brotli bytes. Compared to baseline, this is a 64,018 raw-byte, 10,116 gzip-byte, and 7,255 brotli-byte reduction. The build also emitted `funding_modal.17E442983F2D566DCC410E40470FCE28.js` at 53,792 raw / 9,032 gzip bytes and `spectate_mode_modal.96E486AA8EDBAA35B50BC320AE569D63.js` at 18,092 raw / 4,075 gzip bytes.

## Decision Log

- Decision: Use production `npm run build` artifacts as the primary benchmark for this implementation pass.
  Rationale: The PageSpeed issue is in production, and the user explicitly requested a production build benchmark before and after execution. Dev builds have different optimization and module output behavior and would not measure the actual startup asset.
  Date/Author: 2026-04-20 / Codex

- Decision: Benchmark `main.*.js` as raw bytes, gzip bytes, and brotli bytes.
  Rationale: Raw bytes approximate parse and compile input size, while gzip and brotli approximate network transfer under common production content encodings. The PageSpeed report surfaced both transfer and resource size, so keeping all three makes local results comparable.
  Date/Author: 2026-04-20 / Codex

- Decision: Start with a root-surface module split rather than changing the account bootstrap fetch fanout.
  Rationale: The requested job is to shrink the root main bundle. Moving non-critical UI surfaces out of `:main` changes the compiled startup module directly, while fetch fanout changes network and runtime work without necessarily reducing `main.*.js`.
  Date/Author: 2026-04-20 / Codex

- Decision: Treat the funding modal as the first implementation target unless the RED-test phase exposes a simpler safety boundary.
  Rationale: It is always imported by `app_view.cljs`, is usually closed on first portfolio paint, and is less entangled with the first portfolio route than trade account-info panels. Its regression risk is real but can be contained with tests around closed-state rendering, open-state loading, and existing funding modal behavior.
  Date/Author: 2026-04-20 / Codex

- Decision: Defer route runtime self-registration to a follow-up unless the first root-surface split fails to produce measurable `:main` reduction.
  Rationale: Route self-registration can remove more route-specific action/effect code from startup, but it requires registry/catalog changes and careful post-load action ordering. The funding modal split is a narrower module-boundary change that can establish the lazy-surface pattern and produce a production-build benchmark quickly.
  Date/Author: 2026-04-20 / Codex

## Outcomes & Retrospective

Completed for the first root-surface pass. The funding modal and spectate mode modal are now lazy surface modules loaded through `:effects/load-surface-module`; `src/hyperopen/views/app_view.cljs` no longer statically requires either heavy modal implementation. The post-change production benchmark reduced `main.*.js` by 64,018 raw bytes, 10,116 gzip bytes, and 7,255 brotli bytes versus baseline. Focused TDD contracts, deterministic Playwright smoke, `npm run check`, `npm test`, and `npm run test:websocket` all pass. Overall complexity increased slightly by adding a generic surface-module loader and two small module entry namespaces, but startup complexity improved because closed-by-default root modals no longer live in the startup bundle. The next higher-return follow-up is route/action/effect aggregator splitting and route self-registration, which remains deferred because it carries more runtime ordering risk.

## Context and Orientation

The working directory is `/Users/barry/.codex/worktrees/7bbe/hyperopen`.

This app is built with Shadow CLJS modules. A module is a JavaScript output chunk. The startup module is named `:main`; route modules such as `:portfolio_route`, `:leaderboard_route`, and `:vaults_route` depend on `:main` and load on demand. The relevant configuration is in `shadow-cljs.edn`. The release app build has a `:main` module with `:init-fn hyperopen.core/start!`, plus route modules. The `npm run build` script refreshes a build id, builds minified CSS, runs `npx shadow-cljs release app portfolio-worker vault-detail-worker`, and then generates fingerprinted release artifacts under `out/release-public`.

The root app render path starts at `src/hyperopen/core.cljs`, which calls `hyperopen.app.bootstrap/bootstrap-runtime!` and `hyperopen.app.startup/init!`. The rendered root view is `src/hyperopen/views/app_view.cljs`. That namespace imports `hyperopen.views.funding-modal` and always calls `funding-modal/funding-modal-view` after the route content. Because ClojureScript requires are static, this pulls the funding modal implementation into `:main` even when the modal is closed and the current route is `/portfolio`.

The app already has a route module loader in `src/hyperopen/route_modules.cljs`. It maps route ids to Shadow module names, calls `shadow.loader/load`, resolves exported view functions from the global Closure namespace, and tracks loading/error state under `[:route-modules ...]`. A surface module loader for modals should use the same idea but for optional root surfaces. A "surface" in this plan means a UI component mounted near the root of the app that is only needed after a user action or state transition, such as opening a modal.

The current benchmark command is:

    npm run build
    node - <<'NODE'
    const fs = require('fs');
    const zlib = require('zlib');
    const files = fs.readdirSync('out/release-public/js')
      .filter((file) => file.endsWith('.js'))
      .sort();
    for (const file of files) {
      const path = 'out/release-public/js/' + file;
      const bytes = fs.readFileSync(path);
      const gzip = zlib.gzipSync(bytes, {level: 9});
      const brotli = zlib.brotliCompressSync(bytes, {
        params: {[zlib.constants.BROTLI_PARAM_QUALITY]: 11}
      });
      console.log(`${file}\traw=${bytes.length}\tgzip=${gzip.length}\tbrotli=${brotli.length}`);
    }
    NODE

The baseline output for the startup and nearby route artifacts was:

    main.8796E662E57246EF2133F5812AECA0FC.js raw=2721359 gzip=624506 brotli=476740
    portfolio_route.F30E9610B14E2C9C93526CA084C76E9E.js raw=126434 gzip=28886 brotli=23945
    portfolio_worker.js raw=176833 gzip=41415 brotli=34858
    vaults_route.CB6223A813712C356804A2F2E2DD9FD7.js raw=212481 gzip=41788 brotli=33869
    trade_chart.4F1B02553F3FDF74DE2C40DE6235A7B4.js raw=412751 gzip=116326 brotli=97471

The post-change output for the root and new lazy surface chunks was:

    main.1E87D483EA5081F043A7396EDF38295F.js raw=2657341 gzip=614390 brotli=469485
    funding_modal.17E442983F2D566DCC410E40470FCE28.js raw=53792 gzip=9032 brotli=7770
    spectate_mode_modal.96E486AA8EDBAA35B50BC320AE569D63.js raw=18092 gzip=4075 brotli=3612

The root `main.*.js` delta versus baseline was:

    raw:    -64018 bytes
    gzip:   -10116 bytes
    brotli:  -7255 bytes

## Plan of Work

First, add tests that lock down the lazy-loading contract for optional root surfaces. The important behavior is that the closed funding modal path should not need the heavy funding modal namespace loaded synchronously, and the open funding modal path should have an explicit load mechanism. Prefer tests against a small pure helper or loader namespace rather than brittle tests that inspect compiled JavaScript text. Tests should cover the state predicates and the load bookkeeping that root rendering will use.

Second, add a new Shadow CLJS module for the funding modal. In `shadow-cljs.edn`, add a module such as `:funding_modal` with an entry namespace that exports a global view function, and make it depend on `:main`. The entry namespace should be small and should require `hyperopen.views.funding-modal`, then expose a stable function name for the loader to resolve. The entry namespace should not be required from `app_view.cljs`; otherwise the split is defeated.

Third, add a root surface loader namespace. It should mirror the minimal pieces of `route_modules.cljs`: map a surface id such as `:funding-modal` to a Shadow module name such as `"funding_modal"`, call `shadow.loader/load`, resolve the exported view function from the global Closure namespace, and keep a small atom cache for resolved views. State transitions should be explicit and observable, for example under `[:surface-modules :loading]`, `[:surface-modules :loaded]`, and `[:surface-modules :errors]`, or through equivalent existing runtime state if a suitable location already exists.

Fourth, update `src/hyperopen/views/app_view.cljs` so the closed funding modal renders nothing from the heavy module. When funding modal state is open and the view has not resolved, the root may render a minimal placeholder or nothing while scheduling the load through an effect path. The safest pattern is to trigger loading before or when the modal state opens from funding actions, and also have root render tolerate the module being loaded but not yet resolved. The app must not synchronously require `hyperopen.views.funding-modal` from `app_view.cljs`.

Fifth, wire an effect or action path that loads the `:funding_modal` module. If an existing app effect map can host `:effects/load-surface-module`, add it there. If a funding modal open action already returns effect vectors, extend that action to request the surface module before or alongside opening state. Preserve public action names and arguments. If existing funding action tests assert exact effect vectors, update them only after the RED test demonstrates the new contract.

Sixth, rerun focused tests, then rerun the production build benchmark. The expected result is that `main.*.js` shrinks and a new `funding_modal.*.js` artifact appears. A useful first-pass win is any reduction above 10KB gzip in `main.*.js`; a stronger win is 20KB or more gzip. If the reduction is smaller, record the result honestly and choose the next split candidate from the ranked list, likely `spectate-mode-modal` or account-info secondary panels, depending on risk.

The first funding-only benchmark came in below 10KB gzip reduction, so this plan also split `spectate-mode-modal`. This is still part of the same root-surface pattern: it is a closed-by-default root modal, its banner/open button remains in `app_view.cljs`, and its heavy modal body now lives behind the surface loader.

Finally, run the required validation gates. Because this work touches `src/hyperopen/views/app_view.cljs`, account for UI/browser QA explicitly. If no committed browser flow is changed beyond first-open lazy loading, run the smallest deterministic browser or Playwright smoke that covers `/portfolio` startup and the funding modal open path if available; otherwise document why browser QA is blocked and what manual command would cover it.

The larger runtime route-splitting follow-up should be planned separately if needed. The recommended path is to first replace broad aggregator imports with direct smaller namespaces, then let each route module register route-specific actions and effects after `route-modules/load-route-module!` finishes loading the Shadow module. That follow-up must handle registry catalog validation and route-init ordering; it is not part of the first funding-modal milestone unless the benchmark shows the modal split is insufficient.

## Concrete Steps

1. Search for current funding modal state, open actions, and tests:

    rg "funding-modal|funding-ui|set-funding-modal|open-funding" src test

2. Add RED tests around the planned surface module predicate/loader contract. If introducing a new namespace such as `hyperopen.surface-modules`, add a matching test namespace under `test/hyperopen/...`. Run the focused test command and confirm it fails for the intended reason before production edits.

3. Add the `:funding_modal` module to both the app and release module maps in `shadow-cljs.edn`.

4. Add the funding modal entry namespace under `src/hyperopen/views/` or a similarly local path. It should export a function that delegates to `hyperopen.views.funding-modal/funding-modal-view`.

5. Add the surface-module loader and wire it through app effects/actions so opening the funding modal loads the module. Keep closed-state rendering independent of `hyperopen.views.funding-modal`.

6. Update `src/hyperopen/views/app_view.cljs` to remove the static `hyperopen.views.funding-modal` require and use the resolved optional surface view.

7. Run focused tests. At minimum include the new surface-module tests and existing funding modal tests:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=<new-test-ns> --test=hyperopen.views.funding-modal-test

8. Run the post-change production build and benchmark command from the Context section. Record the exact `main.*.js` line and any new `funding_modal.*.js` line in this plan.

9. Run required gates from the repository root:

    npm run check
    npm test
    npm run test:websocket

10. If UI/browser QA is required and a relevant Playwright command exists, run the smallest stable command covering `/portfolio` startup and funding modal first-open behavior. Record the exact command and outcome.

## Validation and Acceptance

The work is accepted when all of these are true:

1. The active plan records both baseline and post-change production bundle sizes for `main.*.js` using raw, gzip, and brotli bytes.

2. `shadow-cljs.edn` emits a separate module for the first non-critical root surface that is moved out of `:main`.

3. `src/hyperopen/views/app_view.cljs` no longer statically requires the moved heavy surface namespace.

4. Tests prove the new lazy-loading contract for the moved surface and existing funding modal behavior still passes.

5. A production build after the change produces a smaller `main.*.js` than the baseline. The plan must record the exact byte delta. If the first split does not reduce `main.*.js`, the implementation is not accepted until the cause is explained and either corrected or explicitly scoped as a blocker.

6. `npm run check`, `npm test`, and `npm run test:websocket` pass, or any failure is documented with a clear blocker that is unrelated to this change.

7. UI/browser QA for `/portfolio` startup and the moved surface's first-open path is either passed or explicitly accounted for as blocked with the missing prerequisite named.

## Idempotence and Recovery

`npm install` is safe to rerun in this worktree and should not change `package-lock.json` when the lockfile is already current. `npm run build` overwrites generated files under `resources/public/js`, `resources/public/css`, and `out/release-public`; these are build artifacts. The benchmark command only reads files and prints sizes.

If a Shadow module split causes runtime loading errors, the fastest recovery path is to restore the static require and call in `app_view.cljs`, remove the new module entry from `shadow-cljs.edn`, rerun the focused tests, and then reattempt with a smaller surface such as `agent-trading-recovery-modal`. Do not delete user changes or run destructive git commands. Keep this plan updated with the failed attempt and the reason for changing course.

## Artifacts and Notes

The production baseline build completed with:

    [:app] Build completed. (824 files, 693 compiled, 0 warnings, 33.74s)
    [:portfolio-worker] Build completed. (58 files, 14 compiled, 0 warnings, 4.29s)
    [:vault-detail-worker] Build completed. (59 files, 15 compiled, 0 warnings, 3.49s)
    Generated release artifacts in /Users/barry/.codex/worktrees/7bbe/hyperopen/out/release-public with main.2F6EC219D3E49E2E.css

The local dependency install reported 11 npm audit findings. That is not part of this performance scope and should not be changed here unless it blocks required validation.

## Interfaces and Dependencies

The new module loader should depend on `shadow.loader` and `goog.object`, following the pattern in `src/hyperopen/route_modules.cljs`. It should expose stable functions similar to:

    surface-module-id for mapping state or surface keywords to module ids.
    resolved-surface-view for returning a cached view function if available.
    surface-ready? for checking whether a surface implementation is loaded.
    load-surface-module! for initiating `shadow.loader/load` and updating app state.

The funding modal entry namespace should expose a globally resolvable function path such as `hyperopen.views.funding_modal_module.funding_modal_view`. The exact namespace can differ, but it must be named in the loader map and must not be required by `app_view.cljs`.

The implementation should preserve existing public action ids such as `:actions/set-funding-modal` and any existing funding action aliases. New effects should use existing runtime effect wiring patterns in `src/hyperopen/app/effects.cljs` and `src/hyperopen/runtime/effect_adapters.cljs` where possible.

## Revision Notes

- 2026-04-20 / Codex: Created the active ExecPlan after baseline production benchmarking and first read-only explorer results. The plan targets the funding modal split first because it is a closed-by-default root surface currently pulled into `:main`.
- 2026-04-20 / Codex: Added runtime explorer findings and explicitly deferred route self-registration to a later milestone unless the first split fails to reduce `main.*.js`.
- 2026-04-20 / Codex: Recorded the funding-only benchmark, expanded the same surface-module pattern to spectate mode modal because funding alone missed the 10KB gzip target, and recorded the combined post-change production benchmark.
