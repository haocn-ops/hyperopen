# Vaults Hard-Reload Synchronous Preview Cache

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Linked live work: `hyperopen-thb7` ("Accelerate /vaults hard reload with synchronous preview cache").

## Purpose / Big Picture

After this work, a user who has already visited `/vaults` should be able to hard reload `http://localhost:8082/vaults` and see recognizable cached vault content almost immediately instead of waiting through a blank frame, a generic route-loading shell, and then a second loading state before any rows appear. The target behavior is that a warm-cache hard reload paints route-specific vault content within roughly `250-450 ms` in a clean release run, then hands off to the existing async IndexedDB and network refresh path without flashing back to a generic loader.

This is a stronger goal than the March 20, 2026 stale-while-revalidate pass. That earlier pass solved repeated network cost and route re-entry behavior after the `/vaults` module was already loaded, but it did not solve the hard reload path where the browser must reconstruct the page from scratch. The March 21, 2026 browser trace at `/Users/barry/Downloads/Trace-20260321T120317.json` shows that the visible gap is dominated by deferred route loading and by the fact that the existing vault cache restore is asynchronous. The plan below closes that gap by adding a tiny synchronous preview cache in `localStorage`, restoring it before the first app render, and rendering a vault-specific preview shell from the main bundle while the full route module and full dataset continue loading in the background.

## Progress

- [x] (2026-03-21 16:26Z) Re-read `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/docs/WORK_TRACKING.md`, `/hyperopen/docs/MULTI_AGENT.md`, and `/hyperopen/docs/BROWSER_STORAGE.md` before planning this browser-storage and route-startup change.
- [x] (2026-03-21 16:26Z) Analyzed the March 21, 2026 hard-reload trace at `/Users/barry/Downloads/Trace-20260321T120317.json` and mapped the blank frame, route-shell paint, and delayed vault-index fetch to the current code paths.
- [x] (2026-03-21 16:24Z) Created linked `bd` issue `hyperopen-thb7` for this work so the active plan points at live tracked scope.
- [x] (2026-03-21 16:26Z) Authored this active ExecPlan with explicit performance targets, storage boundaries, route-shell work, and a multi-agent execution roster.
- [x] (2026-03-21 19:40Z) Implemented Milestone 1 by adding a bounded synchronous `/vaults` preview cache record, restoring it before first render, and validating restore through TTL, snapshot-range, and wallet guards instead of raw localStorage hydration.
- [x] (2026-03-21 19:40Z) Implemented Milestone 2 by replacing the generic deferred route shell on `/vaults` with a route-specific cached preview surface in the main bundle.
- [x] (2026-03-21 19:40Z) Implemented Milestone 3 by teaching the full `/vaults` list VM and list view to reuse the startup-preview baseline until live cached/network rows take over.
- [x] (2026-03-21 20:00Z) Implemented Milestone 4 by persisting the preview record from successful full-data list states, validating fallback behavior in deterministic tests, building the release root, and capturing warm hard-reload evidence against `http://localhost:8082/vaults`.
- [x] (2026-03-21 20:09Z) Completed the governed `/vaults` browser QA rerun on the supported managed-app path, closed `hyperopen-thb7`, and prepared this plan to move to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: the black gap on hard reload happens before the existing vault cache path can help.
  Evidence: the March 21, 2026 trace shows `vaults_route.js` requested at `817.836 ms`, the first route-specific shell screenshot at about `856.763 ms`, and the first visible vault rows only around `1981.707 ms`, while the existing cache-aware vault effect does not begin the large vault-index request until `1706.266 ms`.

- Observation: the current stale-while-revalidate implementation restores cached full rows only through asynchronous IndexedDB, so it cannot satisfy a sub-`450 ms` first useful vault paint on a hard reload by itself.
  Evidence: `/hyperopen/src/hyperopen/vaults/effects.cljs` currently calls `load-vault-index-cache-record!` inside `api-fetch-vault-index-with-cache!` and only applies cached rows after the Promise resolves.

- Observation: the current main-bundle route shell is generic and knows nothing about `/vaults`, even when the browser already has enough local state to show a meaningful vault preview.
  Evidence: `/hyperopen/src/hyperopen/views/app_view.cljs` renders `deferred-route-loading-shell` whenever a deferred route is not ready, and that shell always shows the same centered "Loading Route" surface.

- Observation: this repository already accepts a small synchronous cache in `localStorage` when the data is tiny, bounded, and specifically useful during startup.
  Evidence: `/hyperopen/docs/BROWSER_STORAGE.md` allows `localStorage` for small fixed-size startup state, `/hyperopen/src/hyperopen/vaults/infrastructure/persistence.cljs` already uses it for the vault snapshot-range preference, and `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs` shows a pattern where a small synchronous copy complements a larger async IndexedDB cache.

- Observation: the current dev/watch build is not an appropriate sign-off target for the `250-450 ms` goal because the measured route loads through a large watch-mode `main.js` and a later async route chunk request.
  Evidence: the trace came from `http://localhost:8081/vaults`, where `main.js` in the worktree is about `14 MB` and `vaults_route.js` is about `818 KB`; the release build already has a documented `http://localhost:8082` measurement loop and is the only stable target for reproducible performance sign-off in this repo.

- Observation: gating preview restore on `[:router :path]` before `init-router!` runs silently disables the feature on hard `/vaults` reloads.
  Evidence: a review pass on 2026-03-21 found that `restore-persisted-ui-state!` runs before router initialization, so `[:router :path]` is still the default `/trade`; the startup restore now keys off `window.location.pathname` and validates through `restore-vault-startup-preview`.

- Observation: the updated release-root warm reload lands well under the original `250-450 ms` target once the preview path is actually reachable.
  Evidence: `/Users/barry/.codex/worktrees/e6b7/hyperopen/tmp/browser-inspection/hard-reload-2026-03-21T20-00-07-468Z/vaults-hard-reload-report.json` recorded `startupPreviewShellMs: 162.1`, `startupPreviewListMs: 174.3`, `firstContentfulPaintMs: 188`, and no `genericLoadingRouteTextMs` mark on `http://localhost:8082/vaults`.

- Observation: the design-review CLI `--local-url` flag does not override target origin when the configured review target and the supplied local URL use different ports.
  Evidence: the invalid run at `/Users/barry/.codex/worktrees/e6b7/hyperopen/tmp/browser-inspection/design-review-2026-03-21T19-59-15-134Z-0a372a75/summary.json` still navigated to `http://localhost:8080/vaults` and captured `chrome-error://chromewebdata/`; the managed-app rerun on the expected `8080` origin then passed.

## Decision Log

- Decision: define the `250-450 ms` target against the generated release artifact root on `http://localhost:8082/vaults`, not against the dev/watch server on `http://localhost:8081/vaults`.
  Rationale: the dev/watch trace is useful for diagnosis, but the release root is the repository’s stable performance sign-off surface and removes watch-mode bundle noise that would otherwise dominate the measurement.
  Date/Author: 2026-03-21 / Codex

- Decision: use a tiny bounded `localStorage` preview record instead of trying to force the full normalized vault index into synchronous storage.
  Rationale: `/hyperopen/docs/BROWSER_STORAGE.md` explicitly reserves synchronous browser storage for tiny startup-benefiting payloads, while the full vault index is large and already belongs in IndexedDB.
  Date/Author: 2026-03-21 / Codex

- Decision: render a route-specific `/vaults` preview shell from the main bundle instead of waiting for `vaults_route.js` before showing any vault-specific content.
  Rationale: the March 21 trace proves that waiting for the deferred route module already loses most of the current visible budget, so the preview surface must exist in code that is available before the deferred route module resolves.
  Date/Author: 2026-03-21 / Codex

- Decision: keep the first aggressive pass scoped to the `/vaults` list route and not extend it to `/vaults/<address>` detail in the same implementation.
  Rationale: the user goal is specifically the list-route hard reload, the bounded preview cache is easiest to keep correct on a stable list layout, and including detail-route semantics would widen both the storage schema and the route-shell surface without helping the first target trace.
  Date/Author: 2026-03-21 / Codex

- Decision: make the preview shell and the full `/vaults` list consume the same restored preview state so the UI does not regress from "preview rows visible" back to skeletons when `vaults_route.js` finishes loading.
  Rationale: a preview shown only in the generic route shell would still flash to a second loading state during the handoff. The restored preview must survive until either IndexedDB hydration or fresh network data provides a better baseline.
  Date/Author: 2026-03-21 / Codex

- Decision: restore startup previews by inspecting the real browser pathname and validating through the preview-cache restore function before associating state.
  Rationale: preview restore must happen before `init-router!`, so store router state is not yet trustworthy, and raw localStorage hydration would bypass the TTL/wallet/snapshot checks that prevent stale or cross-wallet leakage.
  Date/Author: 2026-03-21 / Codex

## Outcomes & Retrospective

This plan is now implemented in code and validated through deterministic tests, release builds, release-root performance evidence, and governed browser QA. The shipped result adds a small bounded startup-preview cache in `localStorage`, a main-bundle `/vaults` preview shell, and a list-view handoff path that keeps preview content visible until live cached or fresh rows replace it.

The measured warm hard-reload result beat the original target. In `/Users/barry/.codex/worktrees/e6b7/hyperopen/tmp/browser-inspection/hard-reload-2026-03-21T20-00-07-468Z/vaults-hard-reload-report.json`, the updated release build painted the vault startup-preview shell at `162.1 ms`, the preview-backed list at `174.3 ms`, and `firstContentfulPaint` at `188 ms`. The generic centered `Loading Route` shell did not appear on that path.

Governed browser QA is complete on the supported managed-app path. The final `/vaults` review at `/Users/barry/.codex/worktrees/e6b7/hyperopen/tmp/browser-inspection/design-review-2026-03-21T20-08-33-904Z-f26c0de2/summary.json` passed `visual`, `native-control`, `styling-consistency`, `interaction`, `layout-regression`, and `jank-perf` at `375`, `768`, `1280`, and `1440`. `hyperopen-thb7` was closed on 2026-03-21 after those passes and the release-root hard-reload metrics were both in hand.

## Context and Orientation

The current `/vaults` route uses three separate layers that matter for this work.

The first layer is deferred route loading. `/hyperopen/src/hyperopen/app/startup.cljs` schedules `[:effects/load-route-module normalized-path]` for deferred routes. `/hyperopen/src/hyperopen/route_modules.cljs` maps `/vaults` and `/vaults/<address>` to the `vaults_route` browser module and resolves its exported list and detail views. Until that module is ready, `/hyperopen/src/hyperopen/views/app_view.cljs` renders the generic `deferred-route-loading-shell`.

The second layer is state restoration during startup. `/hyperopen/src/hyperopen/startup/init.cljs` restores synchronous UI preferences before calling `kick-render!`. `/hyperopen/src/hyperopen/state/app_defaults.cljs` already reads the vault snapshot-range preference from `localStorage` through `/hyperopen/src/hyperopen/vaults/infrastructure/persistence.cljs`, so this repository already accepts synchronous restore for tiny startup-relevant vault state.

The third layer is the existing vault data cache and refresh path. `/hyperopen/src/hyperopen/vaults/application/route_loading.cljs` starts the list-route effects. `/hyperopen/src/hyperopen/vaults/effects.cljs` runs the cache-aware fetch path, which optionally hydrates the full normalized index rows from IndexedDB through `/hyperopen/src/hyperopen/vaults/infrastructure/list_cache.cljs` and then performs the conditional network fetch. `/hyperopen/src/hyperopen/api/projections.cljs` merges hydrated or fresh index rows with live summaries. `/hyperopen/src/hyperopen/views/vaults/vm.cljs` decides whether the route is loading or refreshing, and `/hyperopen/src/hyperopen/views/vaults/list_view.cljs` decides whether to show skeletons or real content.

The key limitation is that the full cache lives behind asynchronous IndexedDB, while the black frame and generic route loader happen before the user can benefit from that cache. This plan adds a fourth layer: a tiny startup-only preview cache stored synchronously in `localStorage`. That preview cache is not a second source of truth for the full dataset. It is a bounded visual artifact that exists only to shorten the hard-reload gap until the real route module and the real list data are ready.

One implementation trap is already known. `/hyperopen/src/hyperopen/vaults/effects.cljs` currently skips the IndexedDB load whenever `[:vaults :index-rows]` is already non-empty. A preview restore that writes directly into `:index-rows` would therefore accidentally change the flow from `preview -> IndexedDB -> network` into `preview -> network`. The preview must either live on its own state path such as `[:vaults :startup-preview]` or carry an explicit origin flag that still allows the richer IndexedDB cache to hydrate before the network path wins.

## Multi-Agent Execution

Use the exact agent names from `/hyperopen/docs/MULTI_AGENT.md` and keep `agents.max_depth = 1`. The intended execution order is deliberate: freeze the spec first, then freeze test expectations, then implement in disjoint worker slices, then review and validate.

`spec_writer` owns this plan and any future scope changes. If implementation changes the preview schema, storage TTL, or sign-off metric, update this ExecPlan first so the worker phases do not diverge.

`acceptance_test_writer` should propose the user-visible acceptance contract. The highest-value acceptance cases are: warm-cache hard reload on `/vaults` shows route-specific cached vault content before `450 ms`; the route does not show the centered generic "Loading Route" shell when a valid preview exists; the final list still converges to the full dataset with current search, filter, sort, and pagination semantics intact; a cold start without a preview cache still falls back to the current loading path.

`edge_case_test_writer` should propose the failure and boundary contract. The highest-value cases are: corrupt preview JSON in `localStorage`; preview schema version mismatch; preview saved for a different wallet address; preview older than the allowed freshness window; snapshot-range mismatch; `localStorage` unavailable or throwing; IndexedDB unavailable after preview restore; preview visible while the large vault-index request fails.

`tdd_test_writer` should materialize approved failing tests before implementation. The highest-value write surfaces are `/hyperopen/test/hyperopen/startup/init_test.cljs` for restore ordering, `/hyperopen/test/hyperopen/startup/restore_test.cljs` for localStorage parse fallback, `/hyperopen/test/hyperopen/vaults/infrastructure/persistence_test.cljs` or a new `/hyperopen/test/hyperopen/vaults/infrastructure/preview_cache_test.cljs` for preview-record normalization, `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs` for the deferred route shell swap, `/hyperopen/test/hyperopen/views/vaults/vm_test.cljs` and `/hyperopen/test/hyperopen/views/vaults/list_view_test.cljs` for preview-visible loading semantics, `/hyperopen/test/hyperopen/vaults/effects_test.cljs` for stale-rows-survive refresh behavior, and `/hyperopen/test/hyperopen/route_modules_test.cljs` if the app-shell retry path needs route-module failure coverage. New fixtures should prefer `:snapshot-preview-by-key` data instead of leaning on legacy `:snapshot-by-key` setup where possible.

Implementation should be split across three `worker` threads with disjoint ownership.

The first `worker` owns the startup and route-shell slice: `/hyperopen/src/hyperopen/startup/init.cljs`, `/hyperopen/src/hyperopen/app/startup.cljs` if route preloading needs adjustment, `/hyperopen/src/hyperopen/views/app_view.cljs`, and any small new shared `/hyperopen/src/hyperopen/views/vaults/preview_shell.cljs`.

The second `worker` owns the storage and cache plumbing slice: `/hyperopen/src/hyperopen/vaults/infrastructure/persistence.cljs` or a new `/hyperopen/src/hyperopen/vaults/infrastructure/preview_cache.cljs`, `/hyperopen/src/hyperopen/state/app_defaults.cljs`, `/hyperopen/src/hyperopen/vaults/effects.cljs`, and any runtime collaborator wiring needed to persist the preview cache from effect boundaries.

The third `worker` owns the full route handoff slice: `/hyperopen/src/hyperopen/views/vaults/vm.cljs`, `/hyperopen/src/hyperopen/views/vaults/list_view.cljs`, and any supporting helpers needed so the restored preview survives until full rows are ready.

`reviewer` must run after implementation to look for stale-data traps, route-handoff regressions, wallet-specific preview leakage, and missing tests. Because this is a UI-facing performance change, `browser_debugger` is required before sign-off. The browser run must capture a clean before/after hard-reload trace and governed design-review coverage for `/vaults`. `architect_review` is optional but recommended if the workers end up broadening route-module or startup responsibilities beyond the scoped files above.

## Plan of Work

Milestone 1 adds a bounded preview cache that the browser can read synchronously before the first render. Create a dedicated infrastructure boundary at `/hyperopen/src/hyperopen/vaults/infrastructure/preview_cache.cljs` unless the final code proves that the existing `/hyperopen/src/hyperopen/vaults/infrastructure/persistence.cljs` can hold the extra logic without becoming ambiguous. The new boundary should own a stable `localStorage` key such as `vaults-list-preview:v1`, a versioned record parser, a builder for a tiny preview artifact, and explicit read, write, and clear helpers. The stored record must stay small and fixed-size to comply with `/hyperopen/docs/BROWSER_STORAGE.md`. It must not duplicate the full normalized vault index.

The preview record should contain only what the startup surface must paint. Persist the restored snapshot range, the saved-at timestamp, the optional wallet address that the preview was built for, the total visible TVL used by the hero card, and bounded vectors for protocol and user rows. Cap each vector to the first visible viewport slice, for example six protocol rows and six user rows. Each row should contain only the render-critical fields that the current list surface needs before the full route module and full row parser are ready: vault address, name, leader, TVL, APR, create-time in milliseconds, relationship role, and sparkline preview data for the restored snapshot range. Do not put all rows, query text, pagination state, raw payloads, or full detail-route data into this record.

Restore this preview synchronously during startup before the first `kick-render!`. Extend `/hyperopen/src/hyperopen/startup/init.cljs` so `restore-persisted-ui-state!` also calls a new `restore-vaults-list-preview!` boundary when the current router path is the list route. Restore only the startup-safe artifact, not the full cached list, and do not write the preview directly into `[:vaults :index-rows]` because that would bypass the existing IndexedDB hydrate path. Store it under a new state path such as `[:vaults :startup-preview]` with enough metadata to tell the view layer whether it is present, stale, and wallet-compatible. Reject and clear unusable records on parse failure, version mismatch, snapshot-range mismatch, or excessive age. The initial plan target is a one-hour preview freshness window. If implementation data shows that this is too strict or too lenient, update the Decision Log before changing it.

Milestone 2 replaces the generic route-loading experience for `/vaults`. Add a route-specific preview surface that lives in the main bundle and is available before `vaults_route.js` resolves. A new shared component file under `/hyperopen/src/hyperopen/views/vaults/preview_shell.cljs` is the safest path because `/hyperopen/src/hyperopen/views/app_view.cljs` can require it without pulling in the full route module. That preview shell must look like the `/vaults` route rather than a centered generic loader: page title, TVL card, toolbar outline, and actual cached preview rows. Keep it obviously transitional by adding a compact status treatment such as `Refreshing vaults…`, but do not replace the route with a blank or centered spinner when a valid preview exists.

Milestone 3 makes the preview survive the route-module handoff. Today `/hyperopen/src/hyperopen/views/vaults/vm.cljs` treats the list as loading whenever the full parsed row source is absent. That logic must learn about the restored preview baseline. Add explicit `previewing?` or `startup-preview?` view-model state so the full route can render the same preview rows until one of two stronger baselines arrives: the existing IndexedDB full-row hydrate or a fresh network success. The actual list view in `/hyperopen/src/hyperopen/views/vaults/list_view.cljs` must stop flashing from preview rows back to skeletons while the route module resolves. The preview state should disappear automatically once real parsed rows are available.

Milestone 4 persists the preview from real full-data list states and verifies the browser result. The preview record should be written only from successful, full list states after the current stale-while-revalidate flow has produced a trustworthy baseline. The safest implementation boundary is `/hyperopen/src/hyperopen/vaults/effects.cljs`, because that file already owns persistence side effects after successful vault-index fetches. The preview builder should derive the preview from a normalized startup list configuration, not from whatever ad hoc search string or temporary dropdown state the user left in memory. That means the builder should use the restored snapshot range and the default list filters, sort, and page sizes that a hard reload actually starts with. Persist again after user-equity data arrives if a wallet-specific user section is being shown, but never leak user rows across wallet addresses. If the current wallet does not match the preview’s wallet, keep protocol rows and drop user rows.

Do not remove the IndexedDB full-row cache or the conditional network request path from the March 20, 2026 implementation. The preview cache is only the first frame. The existing IndexedDB list cache remains the large, durable cache, and the network refresh remains the final source of truth. This plan succeeds only if those layers cooperate without conflicting.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/e6b7/hyperopen`.

Start by freezing the plan and test contract:

    bd update hyperopen-thb7 --claim --json
    npm run agent:dry-run -- --issue hyperopen-thb7

Materialize and iterate on the deterministic tests before the implementation lands:

    npm test -- hyperopen.vaults.infrastructure.preview-cache-test hyperopen.startup.init-test hyperopen.views.app-shell-spacing-test hyperopen.views.vaults.vm-test hyperopen.views.vaults.list-view-test hyperopen.vaults.effects-test

Run the required repository gates before final handoff:

    npm run check
    npm test
    npm run test:websocket

Build and serve the release artifact root for measurement:

    npm run build
    npx serve -s out/release-public -l 8082

Capture the clean browser evidence against `http://localhost:8082/vaults`. First warm the preview cache by loading `/vaults` and waiting for the full list to settle. Then hard reload while tracing. The before/after traces and filmstrips should be stored under `/hyperopen/tmp/browser-inspection/` or another repo-local temporary directory.

Update the `bd` status only after code and measurement acceptance are complete:

    bd close hyperopen-thb7 --reason "Completed" --json

If any of the required gates or trace targets fail, keep `hyperopen-thb7` open and update this ExecPlan rather than creating a second tracker.

## Validation and Acceptance

Acceptance is first visible behavior, not merely code structure. In a clean release run on `http://localhost:8082/vaults` with a warm preview cache and browser extensions disabled, a hard reload should paint route-specific vault content within `250-450 ms` of `navigationStart`. The generic centered `Loading Route` shell from `/hyperopen/src/hyperopen/views/app_view.cljs` must not appear on that warm-cache path. The user should instead see the `/vaults` title area, TVL card shape, and cached preview rows immediately.

Acceptance is also continuity. Once the deferred `vaults_route` module resolves, the route must not regress from visible preview content back to a skeleton state. The preview baseline should remain visible until either the IndexedDB full-row hydrate or the fresh network result replaces it. The existing `Refreshing vaults…` treatment should remain visible while the route is still reconciling stale content.

Acceptance is also correctness and safety. A preview built for wallet address `A` must never show wallet-specific user rows after reloading as wallet address `B`. A stale or corrupt preview record must be ignored safely and must fall back to the current loading path. The existing stale-while-revalidate behavior for the full normalized index must still work after the preview path is added, including the March 20, 2026 CORS-safe conditional fetch behavior.

The deterministic test suite must cover:

- preview record normalization, versioning, TTL rejection, wallet mismatch handling, and corrupt JSON fallback
- startup restore ordering, proving that preview state exists before the first render path
- app-shell behavior, proving that `/vaults` uses the preview shell instead of the generic deferred route loader when a valid preview exists
- vault list view-model behavior, proving that preview data counts as a visible baseline and therefore suppresses skeleton-only loading
- vault list view behavior, proving that preview rows stay visible during module and data handoff
- effect-layer persistence behavior, proving that successful full-data list states refresh the preview record
- route-module failure fallback, proving that a load failure still shows the existing retry-capable deferred shell rather than a blank route after preview restore

The required sign-off commands are:

- `npm run check`
- `npm test`
- `npm run test:websocket`
- `npm run build`

The required browser evidence is:

- a clean before/after hard-reload trace for `http://localhost:8082/vaults`
- a filmstrip or equivalent screenshot sequence proving the first route-specific vault content paint moved into the `250-450 ms` window
- governed browser QA for the `/vaults` route after the UI change, recorded as `PASS`, `FAIL`, or `BLOCKED`

The governed browser-QA rerun should reuse the existing `/vaults` smoke scenarios and the standard four viewport widths from `/hyperopen/docs/agent-guides/browser-qa.md`: `375`, `768`, `1280`, and `1440`. The required `/vaults` route passes remain visual, native-control, styling-consistency, interaction, layout-regression, and jank-perf.

## Idempotence and Recovery

This work is safe to repeat if the storage boundaries stay versioned and bounded. To retry the browser path from a clean state, delete the preview key from browser storage, reload `/vaults` once to warm it again, and then rerun the hard-reload trace. If a preview schema change lands mid-implementation, bump the stored version, reject old records in the parser, and record the schema change in the Decision Log. If a partial implementation leaves the preview shell visible but the full route never takes over, disable the preview by clearing the new state path and removing the new storage key until the handoff path is fixed.

Do not widen the preview payload in response to single missing visual fields unless the record remains obviously small and fixed-size. If implementation pressure starts pushing the preview record toward a full list cache, stop and revisit the storage decision in this document before proceeding.

## Artifacts and Notes

Initial planning artifacts for this work:

    bd issue: hyperopen-thb7
    Trace analyzed: /Users/barry/Downloads/Trace-20260321T120317.json
    Active ExecPlan: /Users/barry/.codex/worktrees/e6b7/hyperopen/docs/exec-plans/active/2026-03-21-vaults-hard-reload-preview-cache.md

The March 21, 2026 trace currently establishes the baseline:

    0.000 ms   navigationStart
    817.836 ms ResourceSendRequest http://localhost:8081/js/vaults_route.js
    856.763 ms first route-shell screenshot (generic loading shell visible)
    879.726 ms firstPaint / firstContentfulPaint
    1706.266 ms ResourceSendRequest https://stats-data.hyperliquid.xyz/Mainnet/vaults
    1981.707 ms first screenshot with actual vault rows

That evidence is why this plan targets both the startup shell and the small synchronous preview cache instead of only the existing IndexedDB list cache.

## Interfaces and Dependencies

The new storage and state interfaces should be explicit.

Add a dedicated preview-cache boundary under `/hyperopen/src/hyperopen/vaults/infrastructure/preview_cache.cljs` with functions equivalent to:

    read-vaults-list-preview
    restore-vaults-list-preview!
    build-vaults-list-preview-record
    persist-vaults-list-preview!
    clear-vaults-list-preview!

Add a new state path under `[:vaults :startup-preview]`. The record stored in state should be normalized and ready for immediate rendering. It should include at least:

    :saved-at-ms
    :snapshot-range
    :wallet-address
    :total-visible-tvl
    :protocol-rows
    :user-rows
    :stale?

Add a small route-shell rendering surface in the main bundle, preferably `/hyperopen/src/hyperopen/views/vaults/preview_shell.cljs`, that can render from the `:startup-preview` state without requiring the deferred `vaults_route` module.

Extend `/hyperopen/src/hyperopen/views/vaults/vm.cljs` so the view model exposes explicit preview-state flags, such as `:previewing?` or `:startup-preview?`, and treats a valid preview as a visible baseline separate from full parsed rows.

Extend `/hyperopen/src/hyperopen/views/vaults/list_view.cljs` so the route can render preview rows and the existing refreshing banner together instead of falling back to skeletons whenever the full parsed row source is still empty.

Extend `/hyperopen/src/hyperopen/vaults/effects.cljs` so successful full-data list states persist the preview record from the effect boundary rather than from reducers or view code. Keep the existing IndexedDB and conditional-request collaborators intact.

Plan revision note: 2026-03-21 16:26Z - Created the active ExecPlan, linked `hyperopen-thb7`, anchored the scope to the March 21 hard-reload trace, and added the multi-agent roster plus the synchronous preview-cache architecture needed to reach the `250-450 ms` first useful vault paint target.
Plan revision note: 2026-03-21 20:09Z - Recorded the final managed-app browser QA PASS, documented the `--local-url` origin-limit discovery from the invalid review attempt, and updated the plan for lifecycle move to `/hyperopen/docs/exec-plans/completed/` after closing `hyperopen-thb7`.
