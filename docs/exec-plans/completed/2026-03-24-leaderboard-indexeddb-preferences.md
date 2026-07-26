# Persist Leaderboard Preferences In IndexedDB

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is governed by `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The tracked work item for this plan is `hyperopen-xnl7`, and that `bd` issue was the local tracker reference while this work was active.

## Purpose / Big Picture

After this change, the `/leaderboard` page should remember stable user preferences across reloads, specifically the selected performance window such as `Month` or `All Time`, the active sort column and direction such as `Volume` descending, and the chosen page size. The easiest way to see the result is to open `/leaderboard`, change those controls, refresh the page, and confirm the same leaderboard controls return without the user having to reselect them.

The user explicitly asked for IndexedDB rather than `localStorage`, so this work must persist the leaderboard preference record through the repository’s shared IndexedDB boundary in `/hyperopen/src/hyperopen/platform/indexed_db.cljs`. Query text and current page number stay transient because they represent the current browsing session rather than a durable preference.

## Progress

- [x] (2026-03-24 18:02Z) Created and claimed `bd` issue `hyperopen-xnl7` for leaderboard preference persistence.
- [x] (2026-03-24 18:09Z) Traced the current leaderboard state and action flow through `/hyperopen/src/hyperopen/leaderboard/actions.cljs`, `/hyperopen/src/hyperopen/views/leaderboard/vm.cljs`, and runtime effect wiring.
- [x] (2026-03-24 18:11Z) Added `/hyperopen/src/hyperopen/leaderboard/preferences.cljs` plus a dedicated IndexedDB object store and guarded restore logic for timeframe, sort, and page-size.
- [x] (2026-03-24 18:12Z) Wired startup restore and explicit `:effects/persist-leaderboard-preferences` emissions for leaderboard timeframe, sort, and page-size actions.
- [x] (2026-03-24 18:17Z) Added regression coverage, a committed Playwright reload-persistence smoke test, passed repo gates, and passed governed browser QA for `/leaderboard`.

## Surprises & Discoveries

- Observation: the leaderboard already keeps the relevant UI state under `:leaderboard-ui`, but none of it is restored from browser persistence today.
  Evidence: `/hyperopen/src/hyperopen/state/app_defaults.cljs` defines `:leaderboard-ui` defaults, and `/hyperopen/src/hyperopen/leaderboard/actions.cljs` only emits `:effects/save` and `:effects/api-fetch-leaderboard`.

- Observation: startup already restores some asynchronous browser-backed state before router initialization, so leaderboard preferences can use that same seam without inventing a second bootstrap phase.
  Evidence: `/hyperopen/src/hyperopen/startup/init.cljs` already calls `restore-asset-selector-markets-cache!`, which is an async IndexedDB-backed restore.

- Observation: an unguarded async restore could overwrite a user change made immediately after startup but before IndexedDB finishes loading.
  Evidence: startup restore is async by design for IndexedDB-backed data, and the current leaderboard controls can be changed at any time through actions such as `set-leaderboard-timeframe` and `set-leaderboard-sort`.

- Observation: using the shared global IndexedDB browser mock inside the new leaderboard persistence test created a full-suite interaction with the generic IndexedDB helper test.
  Evidence: `hyperopen.platform.indexed-db-test` passed in isolation but failed when run beside the first version of `hyperopen.leaderboard.preferences-test`; replacing the leaderboard test with injected persistence seams removed the interference while keeping roundtrip coverage for the feature module.

## Decision Log

- Decision: persist only `:timeframe`, `:sort`, and `:page-size`.
  Rationale: these are stable leaderboard preferences. `:query`, `:page`, and dropdown open state are transient interaction state and should reset naturally.
  Date/Author: 2026-03-24 / Codex

- Decision: restore leaderboard preferences once during startup, not on every route entry.
  Rationale: the app already retains in-memory leaderboard UI state during same-session navigation, so repeated route restores would add unnecessary async churn and create more stale-restore edge cases.
  Date/Author: 2026-03-24 / Codex

- Decision: persist user changes through explicit runtime effects emitted by leaderboard actions, not through a broad store watcher.
  Rationale: the write triggers are narrowly defined and already centralized in leaderboard actions, so explicit effects keep persistence ownership local to the feature and avoid broadening the generic startup cache watcher unnecessarily.
  Date/Author: 2026-03-24 / Codex

- Decision: guard startup restore so it only applies if the relevant leaderboard preferences have not changed since the restore began.
  Rationale: this prevents a slow IndexedDB read from clobbering a user’s fresh selection made during startup.
  Date/Author: 2026-03-24 / Codex

- Decision: keep the feature-level persistence test off the shared global IndexedDB mock and instead inject load/persist seams for its roundtrip assertions.
  Rationale: the platform IndexedDB helper already has direct boundary coverage. Injected seams let the leaderboard feature test prove normalization and restore behavior without cross-test browser-global contention.
  Date/Author: 2026-03-24 / Codex

## Outcomes & Retrospective

The intended outcome was achieved. `/leaderboard` now restores the selected timeframe, sort column and direction, and page size from IndexedDB after reload, while leaving query text, current page, and dropdown-open state transient. Startup restore is guarded so a delayed IndexedDB response cannot overwrite a fresh user selection made during boot.

The implementation slightly increased infrastructure surface area by adding one dedicated persistence module, one new IndexedDB store, and one new runtime effect id. That increase was modest and localized. Overall complexity still went down from the user’s perspective because the leaderboard controls no longer reset on every reload, and the code path remains easy to reason about: startup handles restore, pure actions emit projection plus persist effects, and the persistence module owns normalization plus IndexedDB interaction.

Validation passed at every required layer. Focused CLJS tests passed, the new Playwright leaderboard reload-persistence test passed, `npm run test:websocket` passed, `npm test` passed, `npm run check` passed, and governed browser QA for `leaderboard-route` passed across the `375`, `768`, `1280`, and `1440` review viewports. The remaining blind spot is the standard design-review note that hover, active, disabled, and loading states were not all force-driven during route review, but the overall governed result was still `PASS`.

## Context and Orientation

The leaderboard feature has three relevant layers today. `/hyperopen/src/hyperopen/leaderboard/actions.cljs` owns pure action emission such as changing timeframe, sort, and page size. `/hyperopen/src/hyperopen/views/leaderboard/vm.cljs` derives the renderable view model from `:leaderboard-ui` and `:leaderboard`. `/hyperopen/src/hyperopen/views/leaderboard_view.cljs` binds the UI controls to those actions.

Browser persistence must stay out of those pure view and action layers. Hyperopen’s browser storage policy requires all IndexedDB access to flow through the shared platform boundary in `/hyperopen/src/hyperopen/platform/indexed_db.cljs`. Feature-specific persistence helpers can wrap that boundary, as seen in `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs` and `/hyperopen/src/hyperopen/vaults/infrastructure/list_cache.cljs`.

Startup restore runs through `/hyperopen/src/hyperopen/startup/init.cljs` and `/hyperopen/src/hyperopen/app/startup.cljs`. Runtime effects are exposed through `/hyperopen/src/hyperopen/runtime/effect_adapters/leaderboard.cljs`, `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs`, `/hyperopen/src/hyperopen/app/effects.cljs`, `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`, and `/hyperopen/src/hyperopen/schema/contracts.cljs`. If new leaderboard persistence effects are introduced, all of those registration seams must be updated together so the registry and contract tests stay aligned.

## Plan of Work

First, add a new leaderboard persistence module under `/hyperopen/src/hyperopen/leaderboard/preferences.cljs`. It should define the durable record shape, normalize untrusted stored data, build a deterministic record with a stable id, and load and save through `/hyperopen/src/hyperopen/platform/indexed_db.cljs`. The stored record must include `:saved-at-ms`, and should include a small `:version` field so the record can evolve safely. The record should contain only `:timeframe`, `:sort`, and `:page-size`.

Second, extend `/hyperopen/src/hyperopen/platform/indexed_db.cljs` with a dedicated object store for leaderboard preferences and bump the database version so the new store is created during upgrade.

Third, wire restore into startup. `/hyperopen/src/hyperopen/runtime/effect_adapters/leaderboard.cljs` should expose a `restore-leaderboard-preferences!` helper that delegates to the new persistence module. `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs`, `/hyperopen/src/hyperopen/app/startup.cljs`, and `/hyperopen/src/hyperopen/startup/init.cljs` should thread that restore helper into the existing startup restore sequence. The restore helper must capture the current leaderboard preference fingerprint before the async IndexedDB load begins, and it must only apply restored values if the same fields are still unchanged when the load resolves.

Fourth, persist user changes explicitly from leaderboard actions. `/hyperopen/src/hyperopen/leaderboard/actions.cljs` should append a new persistence effect after the projection save for `set-leaderboard-timeframe`, `set-leaderboard-sort`, and `set-leaderboard-page-size`. The effect can be no-arg and read the latest `:leaderboard-ui` values from the store after projection has been applied. This keeps persistence out of reducers and avoids persisting transient query or page state.

Fifth, register the new effect through the runtime facade and registry surfaces. Update `/hyperopen/src/hyperopen/runtime/effect_adapters/leaderboard.cljs`, `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs`, `/hyperopen/src/hyperopen/app/effects.cljs`, `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`, `/hyperopen/src/hyperopen/schema/contracts.cljs`, and any effect-order contract entries that need to treat the new persistence effect as a persistence-phase effect rather than heavy I/O.

Finally, add focused tests for the pure action output, the persistence helper, the startup restore guard, and the runtime wiring. Because the leaderboard page is user-facing, add the smallest relevant browser verification after code-level tests pass. The browser check only needs to prove the visible behavior: choose a non-default leaderboard preference, reload, and confirm the control state persists.

## Concrete Steps

Work from `/hyperopen`.

1. Create the persistence module and IndexedDB store:

    apply_patch on `/hyperopen/src/hyperopen/leaderboard/preferences.cljs`
    apply_patch on `/hyperopen/src/hyperopen/platform/indexed_db.cljs`

2. Wire startup restore and runtime effects:

    apply_patch on `/hyperopen/src/hyperopen/runtime/effect_adapters/leaderboard.cljs`
    apply_patch on `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs`
    apply_patch on `/hyperopen/src/hyperopen/app/effects.cljs`
    apply_patch on `/hyperopen/src/hyperopen/app/startup.cljs`
    apply_patch on `/hyperopen/src/hyperopen/startup/init.cljs`
    apply_patch on `/hyperopen/src/hyperopen/leaderboard/actions.cljs`
    apply_patch on `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`
    apply_patch on `/hyperopen/src/hyperopen/schema/contracts.cljs`

3. Add regression tests:

    apply_patch on `/hyperopen/test/hyperopen/leaderboard/preferences_test.cljs`
    apply_patch on `/hyperopen/test/hyperopen/leaderboard/actions_test.cljs`
    apply_patch on `/hyperopen/test/hyperopen/startup/init_test.cljs`
    apply_patch on `/hyperopen/test/hyperopen/runtime/effect_adapters/facade_contract_test.cljs`
    apply_patch on `/hyperopen/test/hyperopen/runtime/wiring_test.cljs`
    apply_patch on any additional runtime adapter test file needed for the new leaderboard effect surface

4. Run validation commands:

    cd /hyperopen
    npm exec shadow-cljs -- compile test
    node out/test.js --test=hyperopen.leaderboard.preferences-test,hyperopen.leaderboard.actions-test,hyperopen.startup.init-test,hyperopen.runtime.wiring-test,hyperopen.runtime.effect-adapters.facade-contract-test
    npm run test:websocket
    npm test
    npm run check

5. Run the smallest relevant browser verification once code-level tests pass:

    cd /hyperopen
    npx playwright test tools/playwright/test/routes.smoke.spec.mjs --grep leaderboard

If the existing smoke suite does not cover persisted leaderboard controls, add the smallest deterministic leaderboard preference reload check in the checked-in Playwright suite before broadening beyond that command.

## Validation and Acceptance

Acceptance is user-visible behavior. Start the local app, open `/leaderboard`, change the timeframe from `Month` to `All Time`, change the sort to `Volume`, and if page-size persistence is included change the page size from `10` to `25`. Refresh the browser tab and confirm those control values return automatically. The visible leaderboard rows should follow the restored sort and timeframe without requiring manual reselection.

Automated acceptance requires:

- the new leaderboard persistence helper correctly normalizes malformed or partial stored data and falls back to defaults safely
- leaderboard actions emit the persistence effect after projection saves for the persisted fields
- startup restore calls the leaderboard persistence restore helper
- runtime registration and facade tests know about the new effect handler
- the repository gates pass:

    cd /hyperopen
    npm run test:websocket
    npm test
    npm run check

If browser verification requires a new Playwright assertion, that test should demonstrate a preference change followed by reload with the same visible leaderboard control state.

## Idempotence and Recovery

The change is additive and safe to retry. Re-running startup or the restore helper should simply reapply the same normalized preference record. If the new IndexedDB store or loader misbehaves, the safe rollback is to remove the leaderboard persistence wiring and leave the existing in-memory defaults unchanged. Do not persist transient query text or page number as part of rollback or fallback; that would widen the feature scope instead of safely undoing it.

## Artifacts and Notes

The relevant current state is:

    /hyperopen/src/hyperopen/state/app_defaults.cljs
      :leaderboard-ui {:query ""
                       :timeframe :month
                       :sort {:column :pnl :direction :desc}
                       :page 1
                       :page-size 10
                       :page-size-dropdown-open? false}

The desired persisted subset is:

    {:timeframe :month|:week|:day|:all-time
     :sort {:column :account-value|:pnl|:roi|:volume
            :direction :asc|:desc}
     :page-size 5|10|25|50
     :saved-at-ms <epoch-ms>
     :version 1}

The restore guard should behave like this:

    capture current leaderboard preference fingerprint
    start async IndexedDB load
    when load resolves:
      only apply restored values if the current fingerprint still equals the captured fingerprint

## Interfaces and Dependencies

In `/hyperopen/src/hyperopen/leaderboard/preferences.cljs`, define stable functions with these responsibilities:

    normalize-leaderboard-preferences-record
      Accepts untrusted loaded data and returns a normalized record or nil.

    load-leaderboard-preferences!
      Reads the single leaderboard preference record from IndexedDB and resolves to normalized durable preferences or nil.

    persist-leaderboard-preferences!
      Accepts current leaderboard UI state and writes the durable subset to IndexedDB.

    restore-leaderboard-preferences!
      Accepts the app store atom, loads preferences asynchronously, and conditionally applies them back into `:leaderboard-ui` only if the relevant fields have not changed since restore began.

In `/hyperopen/src/hyperopen/runtime/effect_adapters/leaderboard.cljs`, add a no-argument effect handler named `persist-leaderboard-preferences-effect` that reads the latest store state and delegates to `persist-leaderboard-preferences!`.

In `/hyperopen/src/hyperopen/leaderboard/actions.cljs`, the final action surface must emit `:effects/persist-leaderboard-preferences` for:

    :actions/set-leaderboard-timeframe
    :actions/set-leaderboard-sort
    :actions/set-leaderboard-page-size

Revision note: updated on 2026-03-24 for `hyperopen-xnl7` after implementation and validation completed so the final startup-restore guard, explicit persist effect path, test-mock discovery, and browser evidence are recorded accurately.
