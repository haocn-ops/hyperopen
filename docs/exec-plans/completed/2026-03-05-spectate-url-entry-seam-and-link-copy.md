# Spectate URL Entry Seam and Link Copy

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

This plan builds on `/hyperopen/docs/exec-plans/active/2026-03-04-ghost-mode-account-spectating-and-read-identity-seam.md`, which introduced Spectate Mode state, effective-account-address routing, and the modal/watchlist UI. The current task extends that shipped flow with a deterministic URL entry seam and enables the disabled link control in the watchlist row.

Tracking issue: `hyperopen-ro7`.

## Purpose / Big Picture

After this change, a user will be able to open Hyperopen with a URL such as `/trade?spectate=0xabc...` and land directly in Spectate Mode for that address without needing to open the modal first. The same seam will power the watchlist row link button so users can copy a working share link for any watched address.

The behavior is successful when three things are true together. First, a valid `?spectate=` query parameter activates Spectate Mode during startup before account bootstrap work fans out, so account surfaces load for the spectated address immediately. Second, entering or stopping Spectate Mode keeps the URL deterministic so internal navigation and refreshes preserve or clear the seam intentionally. Third, the watchlist link button is enabled and copies a shareable spectate URL with clear clipboard feedback.

## Progress

- [x] (2026-03-05 21:09Z) Re-read `/hyperopen/.agents/PLANS.md`, `/hyperopen/ARCHITECTURE.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/docs/WORK_TRACKING.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md`.
- [x] (2026-03-05 21:09Z) Audited the shipped Spectate Mode implementation in `/hyperopen/src/hyperopen/account/spectate_mode_actions.cljs`, `/hyperopen/src/hyperopen/startup/init.cljs`, `/hyperopen/src/hyperopen/startup/restore.cljs`, `/hyperopen/src/hyperopen/router.cljs`, `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`, and `/hyperopen/src/hyperopen/views/spectate_mode_modal.cljs`.
- [x] (2026-03-05 21:09Z) Created and claimed `bd` feature issue `hyperopen-ro7` for the implementation lifecycle.
- [x] (2026-03-05 21:10Z) Authored this ExecPlan.
- [x] (2026-03-05 21:14Z) Implemented startup deep-link restore via `/hyperopen/src/hyperopen/account/spectate_mode_links.cljs`, `/hyperopen/src/hyperopen/startup/restore.cljs`, `/hyperopen/src/hyperopen/startup/init.cljs`, and `/hyperopen/src/hyperopen/app/startup.cljs` so valid `?spectate=` queries activate Spectate Mode before account bootstrap.
- [x] (2026-03-05 21:16Z) Synced Spectate Mode URL writes in `/hyperopen/src/hyperopen/account/spectate_mode_actions.cljs` and `/hyperopen/src/hyperopen/runtime/action_adapters.cljs` so start, stop, and internal navigation preserve or clear the spectate query deterministically.
- [x] (2026-03-05 21:18Z) Enabled the watchlist link button and added spectate-link clipboard runtime support through `/hyperopen/src/hyperopen/views/spectate_mode_modal.cljs`, `/hyperopen/src/hyperopen/runtime/effect_adapters/wallet.cljs`, `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs`, `/hyperopen/src/hyperopen/app/effects.cljs`, `/hyperopen/src/hyperopen/wallet/copy_feedback_runtime.cljs`, and runtime contract/catalog wiring.
- [x] (2026-03-05 21:19Z) Added focused regression coverage for link parsing/formatting, startup restore, action output ordering, view wiring, schema contracts, telemetry debug ids, and clipboard feedback; regenerated `/hyperopen/test/test_runner_generated.cljs`.
- [x] (2026-03-05 21:26Z) Restored missing local JS dependencies with `npm install --no-fund --no-audit`, then passed `npm run check`, `npm test`, and `npm run test:websocket`.

## Surprises & Discoveries

- Observation: Startup already restores Spectate Mode preferences before `init-router!` and before remote data stream initialization.
  Evidence: `/hyperopen/src/hyperopen/startup/init.cljs` calls `restore-spectate-mode-preferences!` during `restore-persisted-ui-state!`, then performs `init-router!`, and only later schedules remote startup work in `initialize-systems!`.

- Observation: The existing Spectate Mode start action is session-mutating in a way that is not appropriate for URL restore.
  Evidence: `/hyperopen/src/hyperopen/account/spectate_mode_actions.cljs` `start-spectate-mode` writes `started-at-ms`, persists the last search, and upserts the watchlist entry. A URL restore path should activate spectating without silently mutating the local watchlist.

- Observation: Router state currently stores only a normalized path, while browser history effects already accept arbitrary path strings.
  Evidence: `/hyperopen/src/hyperopen/router.cljs` only writes `{:path ...}` to store state, but `/hyperopen/src/hyperopen/runtime/app_effects.cljs` `push-state!` and `replace-state!` pass their string argument directly to `history.pushState` and `history.replaceState`.

- Observation: This worktree had an incomplete `node_modules` install even though the missing package was already declared in repo metadata.
  Evidence: The first `npm run check` failed with `The required JS dependency "@noble/secp256k1" is not available`; `package.json` and `package-lock.json` already declared it, and `npm install --no-fund --no-audit` restored 281 packages before the next full gate pass.

- Observation: Telemetry debug-console tests still referenced the old pre-rename Ghost Mode action ids.
  Evidence: The first `npm test` run failed in `/hyperopen/test/hyperopen/telemetry/console_preload_test.cljs` until its expected ids were updated from `start-ghost-mode` / `stop-ghost-mode` to `start-spectate-mode` / `stop-spectate-mode`.

## Decision Log

- Decision: Keep the `?spectate=` parser in a dedicated Spectate link helper instead of teaching the router generic query-param ownership.
  Rationale: This feature only needs one canonical query seam today. A focused helper keeps the router path model stable while still letting startup, actions, and copy flows share one deterministic URL formatter/parser.
  Date/Author: 2026-03-05 / Codex

- Decision: Restore Spectate Mode from the URL with a dedicated startup restore helper rather than reusing `start-spectate-mode`.
  Rationale: Deep-link activation must not auto-edit the watchlist or local-storage history just because someone opened an incoming link. Startup only needs to activate read identity, not persist a new local preference.
  Date/Author: 2026-03-05 / Codex

- Decision: Preserve the spectate query parameter during app navigation when Spectate Mode is active, and clear it when Spectate Mode stops.
  Rationale: Without this, the deep link only works on the initial landing page and silently disappears on the next route change or refresh, which would make the seam non-deterministic from a user perspective.
  Date/Author: 2026-03-05 / Codex

- Decision: Add a dedicated public `:effects/copy-spectate-link` effect backed by a generic internal clipboard helper, instead of reusing the wallet-address copy effect for URLs.
  Rationale: The link button should report link-specific success and error messages while still sharing timeout and feedback machinery with existing wallet copy behavior.
  Date/Author: 2026-03-05 / Codex

## Outcomes & Retrospective

This work shipped the missing Spectate Mode URL seam end to end. Hyperopen now restores Spectate Mode from a valid `?spectate=` query during startup, preserves that query while a user navigates internally, clears it when Spectate Mode stops, and exposes an enabled watchlist link button that copies a full spectate URL with link-specific feedback text.

The implementation stayed scoped to existing Spectate Mode boundaries. Read-side account routing did not need additional data-pipeline changes because the earlier effective-account-address work was already in place; this plan only had to activate and preserve that seam. Validation finished cleanly after restoring missing local JS dependencies: `npm run check`, `npm test`, and `npm run test:websocket` all passed.

## Context and Orientation

Spectate Mode already exists in this repository. The canonical account identity helpers are in `/hyperopen/src/hyperopen/account/context.cljs`. Spectate Mode actions live in `/hyperopen/src/hyperopen/account/spectate_mode_actions.cljs`. The modal UI with the currently disabled link button lives in `/hyperopen/src/hyperopen/views/spectate_mode_modal.cljs`. Startup restoration lives in `/hyperopen/src/hyperopen/startup/restore.cljs` and `/hyperopen/src/hyperopen/startup/init.cljs`. App navigation is modeled as a normalized pathname in `/hyperopen/src/hyperopen/router.cljs`, while browser history writes are interpreted in `/hyperopen/src/hyperopen/runtime/app_effects.cljs` and wired through `/hyperopen/src/hyperopen/app/effects.cljs`.

In this repository, “effective account address” means the address read-side account data should follow. When Spectate Mode is active it equals the spectated address, and startup account bootstrap, address watchers, and account history fetches already honor that seam. That means the missing work is not the data pipeline itself; the missing work is activating that seam from a URL early enough and preserving the URL once a user is spectating.

At the start of this work, the watchlist link button rendered as a disabled placeholder with `data-role "spectate-mode-watchlist-link-placeholder"` in `/hyperopen/src/hyperopen/views/spectate_mode_modal.cljs`. Clipboard feedback for wallet copy actions already flowed through `/hyperopen/src/hyperopen/wallet/copy_feedback_runtime.cljs` and surfaced in `[:wallet :copy-feedback]`, which made it a good reuse point for a spectate-link copy flow.

## Plan of Work

First, add a small Spectate link helper namespace, likely under `/hyperopen/src/hyperopen/account/`, that owns two pure behaviors: parsing a spectate address from a query string and formatting a route path back into a deterministic spectate URL. The helper must normalize the pathname through `/hyperopen/src/hyperopen/router.cljs` and normalize the address through `/hyperopen/src/hyperopen/account/context.cljs` so all callers share the same canonical representation.

Second, extend `/hyperopen/src/hyperopen/startup/restore.cljs` with a dedicated startup restore helper for the URL seam. That helper should read the current browser location query, detect a valid `spectate` address, and activate `[:account-context :spectate-mode]` plus the Spectate Mode search fields without mutating the watchlist. Wire this helper into `/hyperopen/src/hyperopen/startup/init.cljs` and `/hyperopen/src/hyperopen/app/startup.cljs` immediately after Spectate Mode preference restore so URL state overrides the locally restored search value before startup account bootstrap begins.

Third, update `/hyperopen/src/hyperopen/account/spectate_mode_actions.cljs` so entering and stopping Spectate Mode emits a browser-history replace effect using the shared URL helper. This keeps the current page shareable without creating a noisy history stack for mode toggles. Then update `/hyperopen/src/hyperopen/runtime/action_adapters.cljs` navigation so route changes preserve the `?spectate=` seam whenever Spectate Mode is already active.

Fourth, replace the disabled watchlist link placeholder with a real action. Add a new Spectate Mode action that emits a dedicated `:effects/copy-spectate-link` effect for the target address and current route. Add the new action and effect through `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`, `/hyperopen/src/hyperopen/runtime/collaborators.cljs`, `/hyperopen/src/hyperopen/app/actions.cljs`, `/hyperopen/src/hyperopen/app/effects.cljs`, `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs`, `/hyperopen/src/hyperopen/runtime/effect_adapters/wallet.cljs`, `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`, and `/hyperopen/src/hyperopen/schema/contracts.cljs`. Reuse the existing wallet copy-feedback state so the modal can display success or failure copy without inventing a second feedback channel.

Finally, add regression tests for the new parser/formatter, startup restore precedence, action output ordering, link button dispatch wiring, and any new clipboard effect behavior. Then run the required repo validation gates and update this plan’s living sections with the results.

## Concrete Steps

Run from repository root `/hyperopen`.

1. Add the Spectate link helper and tests.
   - Edit or add `/hyperopen/src/hyperopen/account/spectate_mode_links.cljs`.
   - Add focused tests for query parsing and deterministic URL formatting.

2. Implement startup deep-link restore.
   - Edit `/hyperopen/src/hyperopen/startup/restore.cljs`.
   - Edit `/hyperopen/src/hyperopen/startup/init.cljs`.
   - Edit `/hyperopen/src/hyperopen/app/startup.cljs`.
   - Update startup tests in `/hyperopen/test/hyperopen/startup/restore_test.cljs` and `/hyperopen/test/hyperopen/startup/init_test.cljs`.

3. Sync URL writes for Spectate Mode and navigation.
   - Edit `/hyperopen/src/hyperopen/account/spectate_mode_actions.cljs`.
   - Edit `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`.
   - Add or update targeted tests in `/hyperopen/test/hyperopen/account/spectate_mode_actions_test.cljs` and `/hyperopen/test/hyperopen/runtime/action_adapters_test.cljs`.

4. Enable the watchlist link button.
   - Edit `/hyperopen/src/hyperopen/views/spectate_mode_modal.cljs`.
   - Add new action/effect wiring files listed in `Plan of Work`.
   - Add clipboard runtime tests and view wiring tests in `/hyperopen/test/hyperopen/wallet/copy_feedback_runtime_test.cljs`, `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`, and schema/runtime facade tests as needed.

5. Validate and close out.
   - Run `npm run check`.
   - Run `npm test`.
   - Run `npm run test:websocket`.
   - Update this ExecPlan with actual progress, discoveries, and validation outcomes.

## Validation and Acceptance

Acceptance is behavioral, not just structural.

Startup deep link acceptance:

    cd /hyperopen
    npm test -- --runInBand

Relevant new tests must prove that a store restored with a valid location query such as `?spectate=0xabcdef...` ends startup with `[:account-context :spectate-mode :active?]` true, `[:account-context :spectate-mode :address]` equal to the normalized address, and the Spectate search fields prefilled with that address. A malformed query parameter must leave Spectate Mode inactive.

URL sync acceptance:

New action tests must prove that `start-spectate-mode` emits save effects before the URL write effect, and that `stop-spectate-mode` emits a URL-clearing replace effect. Navigation tests must prove that route changes preserve `?spectate=` while Spectate Mode is active and omit it when Spectate Mode is inactive.

Link button acceptance:

View tests must prove the watchlist row renders an enabled link button with a real dispatch action instead of the disabled placeholder. Clipboard runtime tests must prove the copied payload is the full spectate URL and that success feedback is link-specific.

Repository acceptance:

    cd /hyperopen
    npm run check
    npm test
    npm run test:websocket

All three commands must pass after the implementation lands.

## Idempotence and Recovery

This work is additive and retry-safe. Re-running startup restore only re-derives Spectate Mode from the current URL and local preferences. URL formatting must be deterministic so repeating the same start/stop/navigation action produces the same browser URL. If a clipboard effect test is flaky, retry with the same stubbed clipboard because the implementation should be pure except for the Promise resolution path.

If a change causes incorrect URL output, recovery is straightforward: keep the store-side Spectate behavior intact, revert only the URL helper or clipboard wiring, and re-run the focused action and startup tests before rerunning the full validation gates.

## Artifacts and Notes

Expected spectate URL examples:

    /trade?spectate=0xabcdefabcdefabcdefabcdefabcdefabcdefabcd
    /portfolio?spectate=0xabcdefabcdefabcdefabcdefabcdefabcdefabcd

Expected share-link copy feedback text:

    Spectate link copied to clipboard

Revision note: updated on 2026-03-05 after implementation to record the shipped spectate URL seam, the dedicated spectate-link copy effect, the dependency-install validation blocker, and the passing acceptance gates before moving this plan to `/hyperopen/docs/exec-plans/completed/`.

## Interfaces and Dependencies

The new Spectate link helper must expose stable pure helpers for:

    spectate-address-from-search
    spectate-url-path
    spectate-url

`spectate-address-from-search` must accept a raw location search string and return either a normalized address or `nil`.

`spectate-url-path` must accept the current route path and an optional address and return a normalized route string, preserving the route pathname and adding or removing the `spectate` query parameter deterministically.

`spectate-url` must accept browser location context sufficient to produce an absolute URL string for clipboard copy.

The public spectate-link clipboard effect accepts the current route path and target address so the runtime effect adapter can build an absolute URL with browser-origin context. Internally, `/hyperopen/src/hyperopen/wallet/copy_feedback_runtime.cljs` now exposes a shared private text-copy helper so both wallet-address copy and spectate-link copy reuse the same timeout and feedback lifecycle.
