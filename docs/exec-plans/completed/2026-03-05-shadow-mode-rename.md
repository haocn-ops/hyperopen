# Rename Ghost Mode to Shadow Mode Across Live Code

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Tracking issue: `hyperopen-93r`.

## Purpose / Big Picture

After this change, Hyperopen users will see `Shadow Mode` everywhere the product currently exposes `Ghost Mode`. The read-only spectating workflow, stop controls, watchlist behavior, and mutation guardrails must remain unchanged; only the product name changes. A user can verify the result by opening the wallet menu, entering the spectating popover, activating spectating, and confirming that the header, banner, order-entry guidance, and supporting docs all say `Shadow Mode` instead of `Ghost Mode`.

This rename also includes the live code paths that model the feature. Internal action ids, namespaces, state keys, and view modules will move from `ghost-*` to `shadow-*` so future code and tests match the product language. Existing browser data must survive the rename, so stored watchlist and last-search values need a compatibility read path from old `ghost-mode-*` localStorage keys into the new `shadow-mode-*` keys.

## Progress

- [x] (2026-03-05 19:49Z) Re-read `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/WORK_TRACKING.md`, `/hyperopen/docs/BROWSER_STORAGE.md`, and `/hyperopen/docs/FRONTEND.md` to confirm planning, storage, and UI constraints for a rename touching spectating flows.
- [x] (2026-03-05 19:49Z) Created and claimed `hyperopen-93r` to track the rename in `bd`.
- [x] (2026-03-05 19:49Z) Audited current `Ghost Mode` usage across `src`, `test`, and live operational docs to identify user-facing copy, internal action/state names, module/file names, and persistence keys.
- [x] (2026-03-05 19:49Z) Decided scope: rename live code, tests, and current operational/canonical docs to `Shadow Mode`; preserve completed ExecPlans as historical artifacts unless they block the rename.
- [x] (2026-03-05 19:52Z) Renamed live source and test code from `ghost-*` to `shadow-*`, including module/file moves for `/hyperopen/src/hyperopen/account/shadow_mode_actions.cljs`, `/hyperopen/src/hyperopen/views/shadow_mode_modal.cljs`, and `/hyperopen/test/hyperopen/account/shadow_mode_actions_test.cljs`.
- [x] (2026-03-05 19:53Z) Updated runtime action ids, state keys, selectors, UI copy, data roles, and generated test-runner references from `Ghost Mode` / `ghost-*` to `Shadow Mode` / `shadow-*`.
- [x] (2026-03-05 19:54Z) Added startup restore compatibility that reads legacy `ghost-mode-*` localStorage keys, migrates their normalized values into new `shadow-mode-*` keys, and removes the legacy keys after migration.
- [x] (2026-03-05 19:54Z) Renamed current operational docs and file paths to `shadow-mode-*`, including the ADR, QA matrix, QA evidence note, and rollout runbook.
- [x] (2026-03-05 19:56Z) Ran `npm run check`, `npm test`, and `npm run test:websocket` successfully after installing missing npm dependencies required by `shadow-cljs`.

## Surprises & Discoveries

- Observation: The current feature name is embedded not only in copy but in runtime action ids, view module names, schema contract entries, state paths, and test namespace names.
  Evidence: `src/hyperopen/runtime/action_adapters.cljs`, `src/hyperopen/schema/runtime_registration_catalog.cljs`, `src/hyperopen/views/ghost_mode_modal.cljs`, and `test/hyperopen/account/ghost_mode_actions_test.cljs` all use `ghost-*` identifiers directly.

- Observation: Watchlist persistence currently depends on two `localStorage` keys and startup restore does not have any migration fallback.
  Evidence: `src/hyperopen/account/context.cljs` defines `ghost-mode-watchlist:v1` and `ghost-mode-last-search:v1`, and `src/hyperopen/startup/restore.cljs` reads only those exact keys.

- Observation: Validation initially failed for an environment reason unrelated to the rename because `node_modules` was incomplete.
  Evidence: The first `npm run check` attempt failed with `The required JS dependency "@noble/secp256k1" is not available`; running `npm install` resolved the compile step and subsequent gates passed.

## Decision Log

- Decision: Treat this as a full live-code rename, not a copy-only relabel.
  Rationale: Leaving live action ids, module names, and state keys as `ghost-*` would keep the codebase out of sync with the product name and make future maintenance harder.
  Date/Author: 2026-03-05 / Codex

- Decision: Keep a temporary compatibility read path for old `ghost-mode-*` localStorage keys, but move steady-state writes to `shadow-mode-*` keys only.
  Rationale: This preserves user watchlists and last search after upgrade without creating a permanent dual-write path that would violate storage hygiene guidance.
  Date/Author: 2026-03-05 / Codex

- Decision: Completed ExecPlans are out of scope for textual cleanup unless they block current tooling or validation.
  Rationale: They are historical artifacts, not the live product surface. Updating active/canonical docs is sufficient for the current rename.
  Date/Author: 2026-03-05 / Codex

- Decision: Rename the current ADR, QA, and rollout file paths to `shadow-mode-*` instead of only changing their headings.
  Rationale: These are live support artifacts that should align with the product language in both content and discoverable file names.
  Date/Author: 2026-03-05 / Codex

## Outcomes & Retrospective

Implementation is complete. Live code, tests, and current product-facing docs now use `Shadow Mode`, while existing browser-stored watchlists and last-search preferences continue to restore from legacy `ghost-mode-*` keys and are migrated forward into `shadow-mode-*` keys. Historical ExecPlans still mention `Ghost Mode` by design, and legacy key strings remain only in `/hyperopen/src/hyperopen/startup/restore.cljs` plus its targeted migration tests.

The main technical risk was partial rename breakage across runtime contracts and generated tests. That risk was retired by renaming the source modules, regenerating the test runner, and passing all required gates.

## Context and Orientation

In this repository, the spectating feature lives under the top-level `:account-context` state branch. Today that branch uses `:ghost-mode` for the active spectated address and `:ghost-ui` for popover/search/watchlist UI state. Pure selectors and persistence constants live in `/hyperopen/src/hyperopen/account/context.cljs`. Runtime command handlers live in `/hyperopen/src/hyperopen/account/ghost_mode_actions.cljs`. User-facing UI lives primarily in `/hyperopen/src/hyperopen/views/header_view.cljs`, `/hyperopen/src/hyperopen/views/app_view.cljs`, `/hyperopen/src/hyperopen/views/ghost_mode_modal.cljs`, and order-entry/funding/vault guardrail surfaces that display the read-only message.

The runtime dispatch surface is centralized. Action adapters are in `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`. Collaborator maps are in `/hyperopen/src/hyperopen/runtime/collaborators.cljs`. Runtime registration ids are declared in `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`, and argument contracts are defined in `/hyperopen/src/hyperopen/schema/contracts.cljs`. A rename that leaves any of these layers behind will break dispatch or tests.

Browser persistence for this feature is intentionally tiny and synchronous. The watchlist and last-search preference are stored in `localStorage` and restored during startup by `/hyperopen/src/hyperopen/startup/restore.cljs`, which is wired into `/hyperopen/src/hyperopen/startup/init.cljs` and `/hyperopen/src/hyperopen/app/startup.cljs`. Because existing users may already have `ghost-mode-*` keys in their browser profile, the restore step must read the new `shadow-mode-*` keys first and then fall back to the old keys if the new keys are absent.

Operational docs that describe current behavior also need renaming so support and QA language stays aligned with the product. The important live docs are `/hyperopen/docs/architecture-decision-records/0024-effective-account-address-and-ghost-mode-ownership.md`, `/hyperopen/docs/qa/ghost-mode-manual-matrix.md`, `/hyperopen/docs/qa/user-funding-spot-clearinghouse-ghost-mode-sampling-2026-03-05.md`, `/hyperopen/docs/runbooks/ghost-mode-rollout.md`, and active ExecPlans that still reference the old feature name.

## Plan of Work

First, rename the core feature language in the domain layer. In `/hyperopen/src/hyperopen/account/context.cljs`, rename the exported storage-key constants, selectors, and user-facing read-only message from `ghost` to `shadow`. Replace the `:ghost-mode` and `:ghost-ui` state branches returned by `default-account-context-state` with `:shadow-mode` and `:shadow-ui`. Any helper or selector that currently reads or writes the old keys must be updated to the new names.

Next, rename the command module and view module. Move `/hyperopen/src/hyperopen/account/ghost_mode_actions.cljs` to `/hyperopen/src/hyperopen/account/shadow_mode_actions.cljs` and `/hyperopen/src/hyperopen/views/ghost_mode_modal.cljs` to `/hyperopen/src/hyperopen/views/shadow_mode_modal.cljs`. Update their namespaces and all requires. Inside those files, rename functions, local variables, `data-role` attributes, and user-facing strings to `shadow-*` or `Shadow Mode` as appropriate. Keep interaction order the same so UI state changes still happen before any heavy effects.

Then, migrate the runtime wiring. Replace `:actions/open-ghost-mode-modal` and the other `ghost` action ids with `shadow` equivalents in `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`, `/hyperopen/src/hyperopen/runtime/collaborators.cljs`, `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`, `/hyperopen/src/hyperopen/schema/contracts.cljs`, `/hyperopen/src/hyperopen/app/actions.cljs`, and any tests or views that dispatch those ids. Update startup restore injection names from `restore-ghost-mode-preferences!` to `restore-shadow-mode-preferences!`.

After that, migrate persistence. Replace the steady-state storage keys with `shadow-mode-watchlist:v1` and `shadow-mode-last-search:v1`. In `/hyperopen/src/hyperopen/startup/restore.cljs`, add helper logic that attempts the new key first and falls back to the old `ghost-mode-*` key when necessary. The restore function should populate the renamed `:shadow-ui` fields and `:watchlist` exactly as before so the rest of the app behavior remains unchanged.

Finally, update supporting docs and tests. Rename and edit the live QA/runbook/ADR files so they use `Shadow Mode`. Update all unit tests to the new namespace/action/state names, regenerate or edit `/hyperopen/test/test_runner_generated.cljs` if necessary, and add or update startup restore tests so they prove the fallback from old keys still works. At the end, run the required validation gates and use targeted grep checks to confirm that live code and live docs no longer use `Ghost Mode`.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/178e/hyperopen`.

1. Rename source and test modules plus their namespace references.
2. Update runtime action ids, state paths, strings, and storage-key constants from `ghost` to `shadow`.
3. Add startup restore fallback from old `ghost-mode-*` keys to new `shadow-mode-*` keys.
4. Rename current operational docs and update references.
5. Run:

    npm run check
    npm test
    npm run test:websocket

6. Run targeted completion checks such as:

    rg -n "Ghost Mode|ghost mode|ghost-mode|ghost_mode" src test docs/runbooks docs/qa docs/architecture-decision-records docs/exec-plans/active

Expected completion state is that this grep only returns intentionally historical artifacts or legacy-key migration coverage, and no live code paths or current product docs.

## Validation and Acceptance

Acceptance is behavior-based, not just textual. After the rename:

- Opening the wallet menu must show `Open Shadow Mode` or `Manage Shadow Mode`, never `Ghost Mode`.
- Activating spectating must render a persistent app banner that says `Shadow Mode` and offers `Stop Shadow Mode`.
- Order, funding, and vault mutation guardrails must show `Shadow Mode is read-only. Stop Shadow Mode to place trades or move funds.` wherever the old message appeared.
- Reloading the app with only old `ghost-mode-*` localStorage entries present must still restore the watchlist and last search into the renamed `shadow` state branch.
- `npm run check`, `npm test`, and `npm run test:websocket` must pass.

Targeted automated proof for the storage migration should include a test where only the old keys exist and the renamed restore function still hydrates `:account-context :shadow-ui` and `:watchlist`.

## Idempotence and Recovery

The rename is safe to re-run because namespace/file moves and text replacements are deterministic. If a file move or wide rename leaves the tree inconsistent, re-run the grep commands above to locate remaining `ghost` references and finish the migration before running tests. The localStorage fallback is additive and safe: if new keys already exist, restore should ignore the old keys. No destructive data migration is required.

## Artifacts and Notes

- Initial live-code audit identified these primary feature files: `/hyperopen/src/hyperopen/account/context.cljs`, `/hyperopen/src/hyperopen/account/ghost_mode_actions.cljs`, `/hyperopen/src/hyperopen/views/ghost_mode_modal.cljs`, `/hyperopen/src/hyperopen/views/header_view.cljs`, `/hyperopen/src/hyperopen/views/app_view.cljs`, `/hyperopen/src/hyperopen/startup/restore.cljs`, `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`, `/hyperopen/src/hyperopen/runtime/collaborators.cljs`, `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`, and `/hyperopen/src/hyperopen/schema/contracts.cljs`.
- Validation evidence:
  - `npm run check` passed after `npm install`; `shadow-cljs` compiled `:app`, `:portfolio-worker`, and `:test` with `0 warnings`.
  - `npm test` passed with `1932 tests` and `9919 assertions`, `0 failures`, `0 errors`.
  - `npm run test:websocket` passed with `333 tests` and `1840 assertions`, `0 failures`, `0 errors`.
  - `rg -n "Ghost Mode|ghost mode" src docs/runbooks docs/qa docs/architecture-decision-records` returned no matches.
  - `rg -n "ghost-mode|ghost_mode|:ghost-mode|:ghost-ui" src test` returned only the legacy localStorage fallback constants in `/hyperopen/src/hyperopen/startup/restore.cljs` and the migration assertions in `/hyperopen/test/hyperopen/startup/restore_test.cljs`.

## Interfaces and Dependencies

At the end of this work, these live interfaces must exist:

- `/hyperopen/src/hyperopen/account/context.cljs` exports `shadow-watchlist-storage-key`, `shadow-last-search-storage-key`, `shadow-mode-read-only-message`, `shadow-address`, and `shadow-mode-active?`, while preserving the existing effective-address and mutation-policy behavior.
- `/hyperopen/src/hyperopen/account/shadow_mode_actions.cljs` exports the renamed action handlers such as `open-shadow-mode-modal`, `start-shadow-mode`, and `stop-shadow-mode`.
- `/hyperopen/src/hyperopen/views/shadow_mode_modal.cljs` exports `shadow-mode-modal-view`.
- Runtime action ids under `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs` use `:actions/*shadow-mode*` names instead of `:actions/*ghost-mode*`.
- `/hyperopen/src/hyperopen/startup/restore.cljs` exposes `restore-shadow-mode-preferences!` and reads new `shadow-mode-*` localStorage keys first, then old `ghost-mode-*` keys as fallback.

Revision note (2026-03-05): Created this ExecPlan after repository audit and issue creation so the rename can be executed as a full live-code migration with explicit storage-compatibility rules.
