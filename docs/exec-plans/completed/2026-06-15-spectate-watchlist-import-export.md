# Spectate Mode Watchlist Import and Export

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. It is written to be self-contained: a contributor with only the current working tree and this file can implement and verify the feature end to end.

## Purpose / Big Picture

Spectate Mode is the feature that lets a user watch any Hyperliquid account read-only. Its modal (top-right "Spectate Mode" panel) keeps a personal *watchlist*: a saved list of `{address, label}` pairs the user has curated over time (for example "wintermute → 0xecb6…2b00"). The watchlist is persisted only in this one browser's `localStorage`. Today there is no way to move that list off the device.

After this change, the Spectate Mode modal gains an **Export** button and an **Import** button. Export downloads the entire watchlist as a single human-readable `.json` file. Import reads such a file back and merges its entries into the current watchlist. This makes the list portable: a user can back it up, move it to another browser or machine, or share a curated list of traders with someone else.

A user can verify this by opening Spectate Mode, clicking **Export** to download `spectate-watchlist-<timestamp>.json`, clearing their browser storage (or using another browser), clicking **Import**, choosing that file, and seeing every label and address restored in the watchlist table, with a confirmation message such as "Imported 18 addresses. 18 saved."

Definitions used in this plan:

- *Watchlist*: the vector of saved entries stored at app-state path `[:account-context :watchlist]` and persisted to `localStorage` under the key string `"spectate-mode-watchlist:v1"`. Each entry is a map `{:address <lowercase 0x + 40 hex> :label <string or nil>}`.
- *Action handler*: a pure ClojureScript function that takes the current app state (and optional arguments) and returns a vector of *effect vectors*. It performs no side effects itself. Spectate handlers live in `src/hyperopen/account/spectate_mode_actions.cljs`.
- *Effect*: a data vector like `[:effects/local-storage-set-json "key" value]` interpreted later by a runtime *effect adapter* that actually touches the browser. Effect adapters live under `src/hyperopen/runtime/effect_adapters/`.
- *Contract drift gate*: a load-time check in `src/hyperopen/schema/contracts.cljs` that throws unless the set of registered action ids exactly equals the set of action ids that have an argument spec (and likewise for effects). This means every new action and effect must be registered *and* given a spec, or the test suite fails to load.

## Context References

- Direct user request (2026-06-15): "Spectate mode does not have a way to import and export the full list of addresses and names we have… come up with a spec or execution plan… and then implement it."
- Prior art incorporated by reference (already merged, files present in tree):
  - `docs/exec-plans/completed/2026-03-05-ghost-mode-watchlist-labels-and-actions.md` — established the labeled `{:address :label}` entry shape, the `localStorage` JSON persistence, and the `normalize-watchlist` / `upsert-watchlist-entry` / `remove-watchlist-entry` helpers in `src/hyperopen/account/context.cljs`.
  - `docs/exec-plans/completed/2026-03-05-spectate-url-entry-seam-and-link-copy.md` — established the modal copy-feedback surface (`[:wallet :copy-feedback]`) reused here for success/error messages.
- Existing file-download precedent reused as a template: `src/hyperopen/account/history/effects.cljs` (`export-funding-history-csv-effect`) — Blob + `URL.createObjectURL` + temporary `<a download>` anchor.

## Orientation: how the spectate action pipeline fits together

A button in the modal emits an action vector such as `[[:actions/export-spectate-mode-watchlist]]`. That keyword is routed through several small files, each of which must agree about the action's name:

1. `src/hyperopen/account/spectate_mode_actions.cljs` — the pure handler function.
2. `src/hyperopen/runtime/action_adapters/spectate_mode.cljs` — a `def` alias exposing the handler to the runtime.
3. `src/hyperopen/runtime/collaborators/spectate_mode.cljs` — adds the handler to the `action-deps` map.
4. `src/hyperopen/schema/runtime_registration/spectate_mode.cljs` — `action-binding-rows` maps the `:actions/…` keyword to the dep key; `effect-binding-rows` does the same for `:effects/…`.
5. `src/hyperopen/schema/contracts/action_args.cljs` — `action-args-spec-by-id` gives every action an argument spec.
6. `src/hyperopen/app/actions.cljs` — the `:spectate-mode` group of the dispatch table maps the dep key to the adapter.

Effects follow the mirror path: `src/hyperopen/app/effects.cljs` registers the effect keyword in the `:spectate-mode` group; an effect adapter implements the browser side; `src/hyperopen/schema/contracts/effect_args.cljs` gives it an argument spec; and `effect-binding-rows` keeps it in the catalog so the contract-drift gate stays satisfied.

The modal view itself is `src/hyperopen/views/spectate_mode_modal.cljs`, with row rendering in `src/hyperopen/views/spectate_mode_modal/watchlist.cljs` and the search row in `src/hyperopen/views/spectate_mode_modal/search.cljs`.

## What is being added

Three new actions:

- `:actions/export-spectate-mode-watchlist` — no arguments. Reads the watchlist from state, builds a JSON document plus a timestamped filename, and emits a download effect and a success/empty feedback effect.
- `:actions/import-spectate-mode-watchlist` — no arguments. Emits a single effect that opens the OS file picker. (The actual file reading is asynchronous and therefore happens in the effect adapter, which dispatches the next action when the file is parsed.)
- `:actions/apply-imported-spectate-watchlist` — one argument, the parsed JSON value (or `nil` if parsing failed). Pure. Validates and merges the imported entries into the existing watchlist, persists, and emits a success or error feedback effect. Keeping the merge logic in a pure action makes every outcome (added/duplicate/invalid/empty) unit-testable by value.

Three new effects:

- `:effects/download-spectate-watchlist-file` — one map argument `{:filename :document :count}`. Serializes `:document` to pretty JSON and triggers a browser download (Blob + anchor, mirroring the funding-history CSV export).
- `:effects/pick-spectate-watchlist-file` — no arguments. Creates a hidden `<input type="file">`, reads the chosen file with `FileReader`, `JSON.parse`s it inside a try/catch, and dispatches `[:actions/apply-imported-spectate-watchlist <parsed-or-nil>]`.
- `:effects/spectate-watchlist-feedback` — two arguments `[kind message]`. Sets the existing modal copy-feedback toast (`[:wallet :copy-feedback]`) and schedules its auto-clear, reusing `hyperopen.wallet.copy-feedback-runtime`.

One new pure namespace holds the serialize/parse/merge/message logic so it can be tested independently of state and of the DOM:

- `src/hyperopen/account/spectate_watchlist_io.cljs`.

One new effect-adapter namespace holds the three browser-side effects (so that the already-at-budget facade `src/hyperopen/runtime/effect_adapters.cljs` is not touched):

- `src/hyperopen/runtime/effect_adapters/spectate_mode.cljs`.

One new view namespace holds the toolbar with the two buttons (so the already-large modal file grows by only two lines):

- `src/hyperopen/views/spectate_mode_modal/import_export.cljs`.

## The export file format

Export writes one JSON object. A novice can read it directly:

    {
      "type": "hyperopen-spectate-watchlist",
      "version": 1,
      "exported-at": 1718452800000,
      "entries": [
        { "address": "0xecb6...2b00", "label": "wintermute" },
        { "address": "0x5a26...ed36", "label": "loracle" }
      ]
    }

`type` and `version` exist so a future change can recognise and migrate old files. `exported-at` is the millisecond timestamp at export time. `entries` is the watchlist in saved order.

Import is deliberately lenient and accepts any of:

- the full object above (reads its `entries`);
- a bare JSON array of `{address,label}` objects;
- a bare JSON array of address strings (the original pre-label watchlist format).

This works because `account-context/normalize-watchlist` already accepts string-keyed maps, keyword-keyed maps, and bare address strings, validates each address against `^0x[0-9a-f]{40}$`, lowercases, de-duplicates, and caps the list at 50 entries.

## Merge semantics (decided, not left to the reader)

Import **merges**, it does not replace. For every valid imported entry, the address is upserted into the existing watchlist. If the imported entry carries a non-blank label, that label wins; if its label is blank, any existing label is preserved. Addresses already present are not duplicated. The combined list is then re-normalized, which re-applies the 50-entry cap. This is the safe default for a backup/share feature: importing a friend's list never silently deletes your own entries. Replacement was rejected because it risks data loss with no undo.

The success message reports newly added addresses and the new total, for example "Imported 5 addresses. 23 saved." If the file contained only addresses you already had, the message is "Watchlist already up to date. 23 saved." If the file is not valid JSON the message is "Import failed: file is not valid JSON." If it parses but contains no valid wallet address the message is "Import failed: no valid wallet addresses found in file."

## Plan of Work

Work proceeds bottom-up so each layer compiles against the one below it.

First, add the pure IO namespace `spectate_watchlist_io.cljs` with: `export-payload`, `extract-entries`, `merge-imported`, and the message builders. Unit-test it in isolation.

Second, add the three action handlers to `spectate_mode_actions.cljs`, delegating all serialization/parse/merge decisions to the IO namespace, and extend the action test file.

Third, wire the three actions through the adapter, collaborator, registration, contract, and dispatch files. Because `src/hyperopen/schema/contracts/action_args.cljs` already sits exactly on its size-exception ceiling (609 lines), bump that ceiling in `dev/namespace_size_exceptions.edn` and record the reason. `src/hyperopen/schema/contracts/effect_args.cljs` has headroom and needs no bump.

Fourth, add the effect-adapter namespace `effect_adapters/spectate_mode.cljs` (download, file-pick, feedback) and register the three effects in `app/effects.cljs` under the existing `:spectate-mode` group and in `effect-binding-rows`.

Fifth, add the toolbar view `spectate_mode_modal/import_export.cljs` and call it from `spectate_mode_modal.cljs` between the controls and the watchlist table.

Finally, run the validation gates and update this plan's Outcomes section.

## Concrete file changes

New files:

- `src/hyperopen/account/spectate_watchlist_io.cljs`
- `src/hyperopen/runtime/effect_adapters/spectate_mode.cljs`
- `src/hyperopen/views/spectate_mode_modal/import_export.cljs`
- `test/hyperopen/account/spectate_watchlist_io_test.cljs`

Edited files:

- `src/hyperopen/account/spectate_mode_actions.cljs` — require the IO namespace; add `export-spectate-mode-watchlist`, `import-spectate-mode-watchlist`, `apply-imported-spectate-watchlist`.
- `src/hyperopen/runtime/action_adapters/spectate_mode.cljs` — three `def` aliases.
- `src/hyperopen/runtime/collaborators/spectate_mode.cljs` — three `action-deps` entries.
- `src/hyperopen/schema/runtime_registration/spectate_mode.cljs` — three action rows, three effect rows.
- `src/hyperopen/schema/contracts/action_args.cljs` — three spec-map entries plus one `s/def`.
- `src/hyperopen/schema/contracts/effect_args.cljs` — three spec-map entries plus two `s/def`s.
- `src/hyperopen/app/effects.cljs` — require the new adapter namespace; three entries in the `:spectate-mode` effect group.
- `src/hyperopen/app/actions.cljs` — three entries in the `:spectate-mode` action group.
- `src/hyperopen/views/spectate_mode_modal.cljs` — require the toolbar namespace; one call in `modal-dialog`.
- `dev/namespace_size_exceptions.edn` — raise the `action_args.cljs` `:max-lines` and extend its reason.
- `test/hyperopen/account/spectate_mode_actions_test.cljs` — export/import/apply tests.

## Validation and Acceptance

Run from the repository root `/hyperopen` (or this worktree root):

    npm test
    npm run check
    npm run test:websocket

`npm test` must show the new `spectate-watchlist-io` tests and the extended `spectate-mode-actions` tests passing. `npm run check` runs, among other gates, `lint:docs` (this plan's required sections), `lint:namespace-sizes` (no file over budget without an exception), the contract-drift load check inside the compiled `test` target, and `shadow-cljs compile` for `app`, `portfolio`, the workers, and `test`. All must pass.

Manual acceptance (browser): symlink `node_modules` into the worktree if needed, run `npm run dev`, open the app, open Spectate Mode, and confirm:

1. With at least one saved address, clicking **Export** downloads `spectate-watchlist-<timestamp>.json` whose `entries` match the table; the modal shows "Exported N addresses."
2. Clicking **Import** and choosing that file restores/merges the entries; the modal shows "Imported N addresses. M saved." and the table updates.
3. Choosing a non-JSON file shows "Import failed: file is not valid JSON." and changes nothing.
4. With an empty watchlist, **Export** is disabled and shows nothing was exported; **Import** remains available.

## Progress

- [x] (2026-06-15) Researched the spectate subsystem end to end (data model, modal view, action/effect wiring, gates, prior art) via a parallel reader sweep; confirmed the contract-drift gate, the 500-line namespace budget with per-file exceptions, and that no Lean/formal gate applies to watchlist import/export.
- [x] (2026-06-15) Authored this ExecPlan.
- [x] (2026-06-15) Added `src/hyperopen/account/spectate_watchlist_io.cljs` and `test/hyperopen/account/spectate_watchlist_io_test.cljs` (10 unit tests: export payload, extract-entries, merge add/duplicate/blank-label/bare-strings/invalid/empty, messages).
- [x] (2026-06-15) Added the three action handlers to `spectate_mode_actions.cljs` and extended `spectate_mode_actions_test.cljs` (export, export-empty, import-trigger, apply-merge, apply-invalid).
- [x] (2026-06-15) Wired actions through adapter/collaborator/registration/contract/dispatch and the action-adapter facade; bumped the `action_args.cljs` size exception 609 → 613.
- [x] (2026-06-15) Added `runtime/effect_adapters/spectate_mode.cljs` (download/pick/feedback) and registered the three effects in `app/effects.cljs` and the effect-binding rows; fixed a `js/document`-shadowing warning by renaming the destructured `:document` local.
- [x] (2026-06-15) Added `views/spectate_mode_modal/import_export.cljs`, mounted it in the modal, and added two view tests (toolbar render + export-disabled-when-empty).
- [x] (2026-06-15) Validation: `npm test` → 4714 tests / 26184 assertions, 0 failures; `npm run test:websocket` → 536 tests, 0 failures; `app` compile clean (0 warnings); `lint:namespace-sizes`, `lint:docs` (this plan clean), `lint:hiccup`, `lint:input-parsing`, `lint:namespace-boundaries`, `lint:test` all pass.

## Surprises & Discoveries

- Observation: `src/hyperopen/schema/contracts.cljs` enforces an *exact* equality between registered action/effect ids and the ids that have argument specs (it throws "Action/Effect contract metadata drift detected" otherwise). Evidence: `contracts.cljs` lines 10–24. Implication: every new action and effect must be added to both its registration rows and its `*-args-spec-by-id` map in the same change, or the test target fails to load.
- Observation: there is no existing file-*upload* path in the codebase (file *download* exists for funding-history CSV). Evidence: repo-wide search for `FileReader`/`createObjectURL` found only download usages. Implication: the import effect adapter is genuinely new code; its async read is bridged back into the pure pipeline via `nxr/dispatch`, matching the existing `dispatch-after-success-actions!` precedent in `effect_adapters/wallet.cljs`.

## Decision Log

- Decision: Import merges (additive upsert, imported label wins when non-blank) rather than replacing the watchlist. Rationale: a backup/share feature must never silently delete the user's existing curated entries; there is no undo. Date/Author: 2026-06-15 / Codex.
- Decision: Export as JSON (not CSV) with a `{type, version, entries}` envelope. Rationale: the entry has a structured `label` field, JSON is self-describing, and the version tag enables future migration; CSV would lose structure and complicate re-import. Date/Author: 2026-06-15 / Codex.
- Decision: Reuse the existing modal copy-feedback toast (`[:wallet :copy-feedback]` via `hyperopen.wallet.copy-feedback-runtime`) for success/error messages instead of adding a new notification surface. Rationale: the message appears exactly where the user is looking, auto-clears, and adds no new view state. Date/Author: 2026-06-15 / Codex.
- Decision: Put browser-side effects in a new `effect_adapters/spectate_mode.cljs` and the toolbar in a new `spectate_mode_modal/import_export.cljs`, rather than extending `effect_adapters.cljs` (628/628) or growing `spectate_mode_modal.cljs` (529/612). Rationale: both facades are at or near their size-exception ceilings; new sibling namespaces keep the change additive and within budget. Date/Author: 2026-06-15 / Codex.
- Decision: Bump the `action_args.cljs` size exception by the few lines the new specs require, consistent with how every prior feature has extended this central contract map (the file already carries a deferred-split note with retire-by 2026-06-30). Rationale: the contract-drift gate forces the spec entries to live in this one map; splitting the map is out of scope and tracked separately. Date/Author: 2026-06-15 / Codex.

## Outcomes & Retrospective

Shipped: the Spectate Mode modal now has a "Saved addresses" toolbar with **Import** and **Export** buttons between the search controls and the watchlist table. Export downloads the whole watchlist as `spectate-watchlist-<ms>.json` (a versioned `{type,version,exported-at,entries}` envelope) and confirms with "Exported N addresses." Import opens a file picker, parses the chosen file, merges its entries into the current watchlist (additive upsert; imported labels win when non-blank, the 50-entry cap is preserved), persists to `localStorage`, and reports "Imported N addresses. M saved." Malformed files report "Import failed: file is not valid JSON."; files with no valid address report "Import failed: no valid wallet addresses found in file." All feedback reuses the existing modal copy-feedback toast.

Validation results: `npm test` 4714 tests / 26184 assertions, 0 failures, 0 errors; `npm run test:websocket` 536 tests, 0 failures; `app` production target compiled with 0 warnings; `lint:namespace-sizes`, `lint:hiccup`, `lint:input-parsing`, `lint:namespace-boundaries`, `lint:test` all pass, and `lint:docs` raises no error against this plan (its only failures are pre-existing in unrelated active plans / a stale PRD). The contract-drift load check in the `test` target passed, confirming the action/effect registrations and argument specs are mutually consistent.

Browser QA note: live in-browser QA was not run because port 8080 was held by an existing shadow-cljs JVM (a dev server this session did not start); it was left undisturbed. Coverage is instead carried by committed deterministic tests — the modal view test asserts the toolbar renders both buttons with the correct `:on :click` action contracts and that Export is disabled with an empty watchlist, and the action tests assert the exact emitted effect vectors. The two browser-only surfaces (the OS file-download and file-picker dialogs) cannot be exercised headlessly regardless; their DOM code mirrors the already-shipped funding-history CSV export precedent.

Complexity: a small, localized increase — three pure actions, three browser-side effects, one pure IO namespace, and one toolbar view, each following existing repo patterns. The only structural cost is one size-exception bump (609 → 613) on `action_args.cljs`, a file already flagged for a deferred split (retire-by 2026-06-30); no new persistence backend, no change to the watchlist storage key or entry shape, and no impact on the Lean/formal trading-submit surface.
