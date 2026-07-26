# Rename Shadow Mode to Spectate Mode and Adopt the Binoculars Icon

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Tracking issue: `hyperopen-mlv`.

## Purpose / Big Picture

After this change, Hyperopen users will see `Spectate Mode` instead of `Shadow Mode` anywhere the live product exposes the read-only account-spectating feature. The trigger affordance should also stop using the current ghost glyph and instead use the official filled Phosphor binoculars icon so the entry point reads as observation rather than haunting. A user can verify the result by opening the header trigger, seeing the binoculars icon on the `Spectate Mode` entry, activating spectating, and confirming that the banner, modal, order-entry remediation, and current operational docs all say `Spectate Mode`.

This is another full live-code rename, not a copy-only relabel. Internal action ids, selectors, state keys, view modules, and tests will move from `shadow-*` to `spectate-*`. Browser-stored watchlist and last-search data must survive the rename, so startup restore has to read new `spectate-mode-*` keys first, then fall back to `shadow-mode-*`, then `ghost-mode-*`, and migrate any legacy values into the new keys.

## Progress

- [x] (2026-03-05 20:46Z) Verified the worktree was clean after the aborted prior turn.
- [x] (2026-03-05 20:46Z) Created and claimed `hyperopen-mlv` in `bd`.
- [x] (2026-03-05 20:46Z) Audited current `Shadow Mode` usage across live source, tests, and current operational docs to size the rename.
- [x] (2026-03-05 20:46Z) Pulled the official filled Phosphor binoculars SVG from the `@phosphor-icons/core` package tarball so the implementation can use the real path data without adding a runtime dependency.
- [x] (2026-03-05 20:49Z) Renamed live source, tests, and current operational docs from `shadow-*` / `Shadow Mode` to `spectate-*` / `Spectate Mode`, including module/file moves for `/hyperopen/src/hyperopen/account/spectate_mode_actions.cljs`, `/hyperopen/src/hyperopen/views/spectate_mode_modal.cljs`, and `/hyperopen/test/hyperopen/account/spectate_mode_actions_test.cljs`.
- [x] (2026-03-05 20:50Z) Rewrote the doubled watchlist action name produced by the mechanical rename from `spectate-spectate-mode-watchlist-address` to `start-spectate-mode-watchlist-address`.
- [x] (2026-03-05 20:51Z) Updated startup restore to use `spectate-mode-*` keys and migrate legacy data from both `shadow-mode-*` and `ghost-mode-*`.
- [x] (2026-03-05 20:51Z) Replaced the header trigger glyph with the official filled Phosphor binoculars icon while preserving the existing trigger size, color, and dispatch behavior.
- [x] (2026-03-05 20:54Z) Ran `npm run check`, `npm test`, and `npm run test:websocket` successfully after correcting accidental style-token rewrites from the broad rename pass.

## Surprises & Discoveries

- Observation: The current live rename from `ghost-*` to `shadow-*` was implemented broadly, so the next rename touches the same deep seams: runtime action ids, selectors, state keys, docs, and generated tests.
  Evidence: `src/hyperopen/account/shadow_mode_actions.cljs`, `src/hyperopen/schema/runtime_registration_catalog.cljs`, `src/hyperopen/startup/restore.cljs`, and `test/hyperopen/account/shadow_mode_actions_test.cljs` all use `shadow-*` identifiers directly.

- Observation: One action name is semantically awkward under a naive search-and-replace.
  Evidence: `spectate-shadow-mode-watchlist-address` would become `spectate-spectate-mode-watchlist-address` if renamed mechanically; this should be rewritten to a clearer action name instead of doubled wording.

- Observation: A broad `shadow` -> `spectate` pass also rewrote unrelated styling tokens such as `box-shadow`, Tailwind `shadow-*` classes, and `focus:shadow-none`.
  Evidence: Initial `npm run check` produced a compile warning at `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/volume_indicator_overlay.cljs:177` because `boxShadow` had become `boxSpectate`; targeted reverts were required in CSS and view class lists before rerunning the gates.

## Decision Log

- Decision: Treat this as a full live-code rename from `shadow-*` to `spectate-*`, not a UI-copy patch.
  Rationale: Leaving internal naming on `shadow-*` would immediately make the codebase inconsistent with the product language again.
  Date/Author: 2026-03-05 / Codex

- Decision: Keep startup restore backward-compatible across three storage generations: `spectate-mode-*`, `shadow-mode-*`, and `ghost-mode-*`.
  Rationale: Users may have stored state from either of the previous names, and this change should not silently drop watchlists or last-search values.
  Date/Author: 2026-03-05 / Codex

- Decision: Inline the official Phosphor binoculars SVG path in Hiccup instead of adding a new icon package dependency.
  Rationale: The repository already renders icons as inline SVG Hiccup vectors, so copying the official path keeps bundle/runtime complexity flat and avoids pulling in a library that is otherwise unused.
  Date/Author: 2026-03-05 / Codex

- Decision: Completed and historical ExecPlans remain out of scope for textual cleanup unless they block validation.
  Rationale: The product rename should update live code and current operational docs, not rewrite historical planning records.
  Date/Author: 2026-03-05 / Codex

- Decision: Limit the binoculars icon swap to the existing primary trigger affordance instead of adding the icon to every Spectate Mode surface.
  Rationale: The user asked to replace the current ghost icon, and the trigger button is the single existing glyph surface. Reusing the icon more broadly would expand the visual change without adding clear functional value.
  Date/Author: 2026-03-05 / Codex

## Outcomes & Retrospective

Implementation is complete. Live code, tests, and current operational docs now use `Spectate Mode`, the header trigger uses the official filled Phosphor binoculars glyph, and browser-stored state restores through a chained fallback of `spectate-mode-*` -> `shadow-mode-*` -> `ghost-mode-*`. Remaining `shadow-mode-*` strings in live code are limited to the explicit legacy-storage fallback constants in `/hyperopen/src/hyperopen/startup/restore.cljs` and the targeted migration assertions in `/hyperopen/test/hyperopen/startup/restore_test.cljs`.

The main execution risk was collateral replacement of unrelated styling tokens during the broad rename pass. That was corrected before final validation, and all required gates now pass.

## Context and Orientation

The spectating feature currently lives under the top-level `:account-context` state branch and uses `:shadow-mode` for the active spectated address plus `:shadow-ui` for modal/search/watchlist UI state. Pure selectors and persistence constants live in `/hyperopen/src/hyperopen/account/context.cljs`. Runtime command handlers live in `/hyperopen/src/hyperopen/account/shadow_mode_actions.cljs`. User-facing views live in `/hyperopen/src/hyperopen/views/header_view.cljs`, `/hyperopen/src/hyperopen/views/app_view.cljs`, and `/hyperopen/src/hyperopen/views/shadow_mode_modal.cljs`. Mutation guardrails surface the read-only message in order, funding, vault, and trade-view code paths.

The runtime dispatch surface is centralized. Action adapters are in `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`. Collaborator maps are in `/hyperopen/src/hyperopen/runtime/collaborators.cljs`. Runtime registration ids are declared in `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`, and argument contracts are defined in `/hyperopen/src/hyperopen/schema/contracts.cljs`. Any rename that misses one of these layers will break dispatch or tests.

Browser persistence is still intentionally tiny and synchronous. `/hyperopen/src/hyperopen/startup/restore.cljs` restores watchlist and last-search data during startup. Right now it treats `shadow-mode-*` as the current keys and already knows how to migrate from `ghost-mode-*`. This task must extend that logic so `spectate-mode-*` becomes the new steady-state storage while `shadow-mode-*` and `ghost-mode-*` remain readable as legacy fallbacks.

The current trigger icon in `/hyperopen/src/hyperopen/views/header_view.cljs` is a custom inline SVG under `shadow-mode-icon`. The user has asked to replace that glyph with the official filled Phosphor binoculars icon referenced from [phosphoricons.com](https://phosphoricons.com/?q=binoculars&weight=fill). This repository does not use a shared icon package, so the correct local integration is to replace the inline path data directly and keep the existing size/color control through CSS classes and `currentColor`.

Current operational docs also need renaming so support and QA language stays aligned with the product: `/hyperopen/docs/architecture-decision-records/0024-effective-account-address-and-shadow-mode-ownership.md`, `/hyperopen/docs/qa/shadow-mode-manual-matrix.md`, `/hyperopen/docs/qa/user-funding-spot-clearinghouse-shadow-mode-sampling-2026-03-05.md`, `/hyperopen/docs/runbooks/shadow-mode-rollout.md`, and the reference from `/hyperopen/docs/runbooks/ws-migration-rollout.md`.

## Plan of Work

First, rename the core feature language in the domain layer. In `/hyperopen/src/hyperopen/account/context.cljs`, rename the storage-key constants, selectors, state keys, and read-only message from `shadow` to `spectate`. Replace `:shadow-mode` and `:shadow-ui` in the default account-context state with `:spectate-mode` and `:spectate-ui`. Update any helper that derives the effective account address or blocked-mutation message to use the new names.

Next, rename the command module and modal module. Move `/hyperopen/src/hyperopen/account/shadow_mode_actions.cljs` to `/hyperopen/src/hyperopen/account/spectate_mode_actions.cljs` and `/hyperopen/src/hyperopen/views/shadow_mode_modal.cljs` to `/hyperopen/src/hyperopen/views/spectate_mode_modal.cljs`. Update all requires, exported function names, and `data-role` values. Avoid a purely mechanical rename for the watchlist row action: use a clearer verb such as `start-spectate-mode-watchlist-address` rather than a doubled `spectate-spectate-*` name.

Then, migrate the runtime wiring. Replace `:actions/*shadow-mode*` ids with `:actions/*spectate-mode*` ids in `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`, `/hyperopen/src/hyperopen/runtime/collaborators.cljs`, `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`, `/hyperopen/src/hyperopen/schema/contracts.cljs`, `/hyperopen/src/hyperopen/app/actions.cljs`, and all views/tests that dispatch those actions. Update startup restore injection names from `restore-shadow-mode-preferences!` to `restore-spectate-mode-preferences!`.

After that, extend persistence compatibility. Replace the steady-state storage keys with `spectate-mode-watchlist:v1` and `spectate-mode-last-search:v1`. In `/hyperopen/src/hyperopen/startup/restore.cljs`, generalize the restore helper so it reads the new key first, then the shadow key, then the ghost key, returning both the value and which legacy key was used. If a legacy key is used, normalize the value, persist it into the new spectate key, and remove the matched legacy key.

Finally, replace the icon affordance. In `/hyperopen/src/hyperopen/views/header_view.cljs`, replace the current custom trigger SVG with the official filled Phosphor binoculars path. Keep the icon inline and driven by `currentColor`. If the modal header benefits from the same icon for consistency, use the same inline helper there; otherwise keep the icon limited to the primary trigger button so the affordance stays focused.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/178e/hyperopen`.

1. Create this ExecPlan and keep it updated as work proceeds.
2. Move source and test files from `shadow_*` names to `spectate_*` names.
3. Rename live source, tests, and operational docs from `shadow-*` / `Shadow Mode` to `spectate-*` / `Spectate Mode`.
4. Replace the current trigger SVG with the official filled Phosphor binoculars SVG path.
5. Update startup restore fallback and migration logic to support spectate -> shadow -> ghost storage reads.
6. Run:

    npm run check
    npm test
    npm run test:websocket

7. Run targeted completion checks such as:

    rg -n "Shadow Mode|shadow mode" src docs/runbooks docs/qa docs/architecture-decision-records
    rg -n "shadow-mode|shadow_mode|:shadow-mode|:shadow-ui" src test

Expected completion state is that any remaining `shadow-*` strings are intentionally limited to legacy-storage migration code/tests or historical planning artifacts, and not present in the live product surface or current operational docs.

## Validation and Acceptance

Acceptance is behavior-based:

- The header trigger must render `Spectate Mode` with the binoculars icon and still dispatch the modal-open action.
- Activating spectating must render a persistent app banner labeled `Spectate Mode` with a `Stop Spectate Mode` control.
- Order, funding, and vault mutation guardrails must display `Spectate Mode is read-only. Stop Spectate Mode to place trades or move funds.` wherever the old message appeared.
- Reloading the app with only `shadow-mode-*` or only `ghost-mode-*` localStorage entries present must still restore the watchlist and last search into the renamed `spectate` state branch.
- `npm run check`, `npm test`, and `npm run test:websocket` must all pass.

Targeted automated proof must include restore tests for both shadow-era and ghost-era legacy keys plus the new steady-state spectate keys.

## Idempotence and Recovery

The rename is safe to re-run because module moves and text replacements are deterministic. If any step leaves the tree inconsistent, rerun the grep commands above to locate remaining `shadow` references in live code and finish the migration before running tests. The storage migration is additive and safe: if spectate keys already exist, restore ignores older shadow and ghost keys.

## Artifacts and Notes

- Official binoculars SVG source used for path data:
  `@phosphor-icons/core` `2.1.1`, file `package/assets/fill/binoculars-fill.svg`
- Primary live-code touchpoints:
  `/hyperopen/src/hyperopen/account/context.cljs`
  `/hyperopen/src/hyperopen/account/shadow_mode_actions.cljs`
  `/hyperopen/src/hyperopen/views/header_view.cljs`
  `/hyperopen/src/hyperopen/views/app_view.cljs`
  `/hyperopen/src/hyperopen/views/shadow_mode_modal.cljs`
  `/hyperopen/src/hyperopen/startup/restore.cljs`
  `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`
  `/hyperopen/src/hyperopen/runtime/collaborators.cljs`
  `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`
  `/hyperopen/src/hyperopen/schema/contracts.cljs`
- Validation evidence:
  - `npm run check` passed with `0 warnings` after correcting unintended style-token replacements.
  - `npm test` passed with `1933 tests`, `9923 assertions`, `0 failures`, `0 errors`.
  - `npm run test:websocket` passed with `333 tests`, `1840 assertions`, `0 failures`, `0 errors`.
  - `rg -n "Shadow Mode|shadow mode" src docs/runbooks docs/qa docs/architecture-decision-records` returned no matches.
  - `rg -n ":shadow-mode|:shadow-ui|shadow-mode-|shadow_mode" src test` returned only the intentional legacy-storage fallback constants and migration assertions.

## Interfaces and Dependencies

At the end of this work, these live interfaces must exist:

- `/hyperopen/src/hyperopen/account/context.cljs` exports `spectate-watchlist-storage-key`, `spectate-last-search-storage-key`, `spectate-mode-read-only-message`, `spectate-address`, and `spectate-mode-active?`.
- `/hyperopen/src/hyperopen/account/spectate_mode_actions.cljs` exports the renamed action handlers such as `open-spectate-mode-modal`, `start-spectate-mode`, and the watchlist-entry start action.
- `/hyperopen/src/hyperopen/views/spectate_mode_modal.cljs` exports `spectate-mode-modal-view`.
- Runtime action ids under `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs` use `:actions/*spectate-mode*` names.
- `/hyperopen/src/hyperopen/startup/restore.cljs` exposes `restore-spectate-mode-preferences!` and reads `spectate-mode-*` keys first, then `shadow-mode-*`, then `ghost-mode-*`.

Revision note (2026-03-05): Created this ExecPlan after verifying the clean post-commit tree, auditing current `Shadow Mode` usage, and pulling the official Phosphor binoculars fill asset so the rename and icon swap can be implemented as one coherent migration.
