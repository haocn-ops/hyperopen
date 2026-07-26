# Asset Selector Shortcut Symbol Parity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, Hyperopen’s asset selector shortcut footer will match Hyperliquid’s shortcut presentation style: symbol-based keycaps (`⌘K`, icon-based navigate, `Enter`, `⌘S`, `Esc`) instead of verbose text combinations. Users should see the same compact scan pattern and visual semantics in the selector footer.

## Progress

- [x] (2026-02-19 17:34Z) Confirmed current Hyperopen footer implementation uses text labels (`Cmd/Ctrl+K`, `Up/Down`, `Cmd/Ctrl+S`) in `/hyperopen/src/hyperopen/views/asset_selector_view.cljs`.
- [x] (2026-02-19 17:48Z) Completed live Hyperliquid browser inspection using repository browser-inspection tooling and extracted shortcut footer DOM/style evidence.
- [x] (2026-02-19 17:50Z) Confirmed Hyperliquid bundle implementation for footer commands and navigate icon path in `tmp/hyperliquid-main.ccb853ef.js`.
- [x] (2026-02-19 17:52Z) Implemented footer rendering parity in `/hyperopen/src/hyperopen/views/asset_selector_view.cljs` (`⌘K`, SVG navigate keycap, `Enter`, `⌘S`, `Esc`).
- [x] (2026-02-19 17:52Z) Updated selector footer assertions in `/hyperopen/test/hyperopen/views/asset_selector_view_test.cljs`.
- [x] (2026-02-19 17:53Z) Ran required validation gates successfully: `npm run check`, `npm test`, `npm run test:websocket`.

## Surprises & Discoveries

- Observation: The selector footer only appears after opening the selector via keyboard shortcut (`Cmd/Ctrl+K`) in the inspected desktop flow; it is not present in the default page render.
  Evidence: Browser capture at `/hyperopen/tmp/browser-inspection/manual-shortcut-check/hyperliquid-after-cmdk-1771523281302.png`.
- Observation: Hyperliquid renders Navigate as an inline SVG glyph, not a text token like `Up/Down`.
  Evidence: Footer HTML capture includes `<svg ... viewBox="0 0 22 13">` command content for navigate.
- Observation: Hyperliquid’s command strings are hardcoded as `⌘K` and `⌘S` in the current bundle slice, with no nearby OS-branching logic in the footer item construction.
  Evidence: `tmp/hyperliquid-main.ccb853ef.js` contains `command:"\u2318K"` and `command:"\u2318S"` in the footer `items` list.
- Observation: Emulating a Windows browser surface still renders `⌘K` and `⌘S` in Hyperliquid’s footer.
  Evidence: Inspection run with `navigator.platform = "Win32"` and `navigator.userAgentData.platform = "Windows"` still produced `⌘KOpen...⌘SFavorite...` output.

## Decision Log

- Decision: Mirror Hyperliquid’s current footer behavior exactly, including symbol keycaps and navigate SVG glyph, instead of keeping mixed `Cmd/Ctrl` wording.
  Rationale: The user requested parity with Hyperliquid’s display method; direct implementation evidence shows symbol-first keycaps.
  Date/Author: 2026-02-19 / Codex
- Decision: Keep this scope confined to selector footer rendering and tests; do not alter selector shortcut action behavior.
  Rationale: The request is presentational parity for keycap display, not input handling changes.
  Date/Author: 2026-02-19 / Codex

## Outcomes & Retrospective

Implemented shortcut footer display parity for Hyperopen’s asset selector.

Delivered result:

- Footer now renders symbol keycaps `⌘K`, `Enter`, `⌘S`, and `Esc` plus the same navigate SVG glyph shape observed in Hyperliquid.
- Footer label copy remains `Open`, `Navigate`, `Select`, `Favorite`, and `Close`.
- Legacy verbose key labels (`Cmd/Ctrl+K`, `Up/Down`, `Cmd/Ctrl+S`) were removed from rendering and tests.

Validation results:

- `npm run check` passed.
- `npm test` passed (1129 tests, 5195 assertions, 0 failures).
- `npm run test:websocket` passed (135 tests, 587 assertions, 0 failures).

## Context and Orientation

Shortcut footer rendering for the asset selector is defined in `/hyperopen/src/hyperopen/views/asset_selector_view.cljs` (`shortcut-keycap`, `shortcut-item`, and `selector-shortcut-footer`). Current content uses full textual key strings (`Cmd/Ctrl+K`, `Up/Down`, `Cmd/Ctrl+S`).

Selector view behavior tests for footer content are in `/hyperopen/test/hyperopen/views/asset_selector_view_test.cljs`, specifically `asset-selector-dropdown-renders-shortcut-footer-and-keydown-dispatch-test`.

Hyperliquid parity evidence for this task comes from:

- Runtime capture: `/hyperopen/tmp/browser-inspection/manual-shortcut-check/hyperliquid-after-cmdk-1771523281302.png`
- Runtime footer HTML dump captured during inspection (local terminal output)
- Bundle snippet: `/hyperopen/tmp/hyperliquid-main.ccb853ef.js` around `command:"\u2318K"` and the navigate glyph component path.

## Plan of Work

Update footer rendering helpers so keycaps accept rich content, not only plain strings. Replace the navigate keycap text with the inspected SVG glyph path. Update footer item definitions to match Hyperliquid commands (`⌘K`, navigate icon, `Enter`, `⌘S`, `Esc`).

Adjust tests to assert symbol-based commands and remove legacy assertions that expect `Cmd/Ctrl` and `Up/Down` wording.

Run full required repository validation gates and record the result in this plan.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/asset_selector_view.cljs`.
2. Edit `/hyperopen/test/hyperopen/views/asset_selector_view_test.cljs`.
3. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

## Validation and Acceptance

Acceptance criteria:

- Selector footer displays `⌘K`, navigate icon glyph, `Enter`, `⌘S`, and `Esc` keycaps.
- Footer labels remain `Open`, `Navigate`, `Select`, `Favorite`, and `Close`.
- No selector interaction behavior regresses.
- Required validation gates pass.

Observed acceptance evidence:

- Footer content assertions in `asset-selector-dropdown-renders-shortcut-footer-and-keydown-dispatch-test` now validate symbol keycaps and navigate SVG presence.
- Required validation gates all passed on this branch.

## Idempotence and Recovery

Changes are additive in one view file and one test file. If visual regression occurs, revert both files together so footer rendering and assertions stay consistent.

## Artifacts and Notes

Inspection artifacts used for implementation decisions:

- `/hyperopen/tmp/browser-inspection/manual-shortcut-check/hyperliquid-after-cmdk-1771523281302.png`
- `/hyperopen/tmp/browser-inspection/manual-shortcut-check/hyperliquid-windows-emulated-1771523377968.png`
- `/hyperopen/tmp/hyperliquid-main.ccb853ef.js`

## Interfaces and Dependencies

No new dependencies are required.

Interface impact:

- No action/event contract changes.
- View-only shortcut footer rendering update in existing selector component.

Plan revision note: 2026-02-19 17:51Z - Initial plan created from live inspection + bundle evidence before code edits.
Plan revision note: 2026-02-19 17:53Z - Marked implementation complete, recorded validation results, and documented Windows-emulation shortcut observation.
