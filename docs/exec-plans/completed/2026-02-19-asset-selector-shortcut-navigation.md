# Asset Selector Shortcut Navigation Parity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, users can operate the asset selector with Hyperliquid-style bottom shortcut navigation cues and matching keyboard behavior. When the selector is open, users should see a footer row that advertises open, navigate, select, favorite, and close shortcuts; arrow keys move a highlighted row, `Enter` selects it, `Cmd/Ctrl+S` toggles favorite on it, and `Esc` closes the selector.

## Progress

- [x] (2026-02-19 16:39Z) Created ExecPlan and confirmed target files for asset-selector view/actions/runtime contracts.
- [x] (2026-02-19 16:45Z) Implemented selector shortcut action logic and selector state updates for highlighted-row navigation.
- [x] (2026-02-19 16:47Z) Wired shortcut keydown handling and footer shortcut UI into selector/trigger views.
- [x] (2026-02-19 16:49Z) Updated runtime registrations/contracts/collaborators for the new shortcut action and keyboard modifier placeholders.
- [x] (2026-02-19 16:52Z) Added and updated unit tests for shortcut action behavior, row highlight behavior, and selector footer/key dispatch rendering.
- [x] (2026-02-19 16:54Z) Ran required validation gates: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-02-19 16:56Z) Finalized plan and moved it to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: Repo already includes a browser inspection tool configured for Hyperliquid parity capture, but captured default page state does not open the asset selector automatically.
  Evidence: `/hyperopen/tmp/browser-inspection/inspect-2026-02-19T16-37-52-633Z-b2773afb/hyperliquid/desktop/screenshot.png`.
- Observation: Typography policy tests reject explicit sub-16px utility tokens such as `text-[11px]`.
  Evidence: `npm test` failure in `hyperopen.views.typography-scale-test` until shortcut keycap class was changed to `text-xs`.
- Observation: Selector-local `:on :keydown` wiring is not sufficient for global shortcuts when focus remains on unrelated elements.
  Evidence: Manual validation after first implementation showed `Esc`/`Cmd+K` were inert unless selector container had focus.

## Decision Log

- Decision: Implement shortcut handling in existing selector action/view pathways and avoid adding a new global keybinding runtime layer.
  Rationale: This keeps behavior deterministic, local to selector interaction flow, and minimizes risk across unrelated views.
  Date/Author: 2026-02-19 / Codex
- Decision: Keep shortcuts scoped to selector/trigger focus flow instead of introducing app-wide document listeners.
  Rationale: Initial approach prioritized minimal scope, but it did not satisfy expected shortcut behavior.
  Date/Author: 2026-02-19 / Codex
- Decision: Install a startup-time global keydown listener for `Cmd/Ctrl+K` and `Esc`, dispatching into existing selector shortcut action flow.
  Rationale: This makes open/close shortcuts reliable regardless of focus while keeping all state transitions in existing action handlers.
  Date/Author: 2026-02-19 / Codex

## Outcomes & Retrospective

Implemented Hyperliquid-style selector footer shortcuts and matching scoped keyboard behavior in Hyperopen’s asset selector.

Delivered behavior:

- Selector footer renders shortcut cues for `Cmd/Ctrl+K`, `Up/Down`, `Enter`, `Cmd/Ctrl+S`, and `Esc`.
- New action `:actions/handle-asset-selector-shortcut` handles open/close/navigate/select/favorite intents.
- Selector state now tracks `:highlighted-market-key` and row rendering applies highlight styling independently of active-market ring state.
- Startup now installs a global selector shortcut listener so `Cmd/Ctrl+K` opens selector and `Esc` closes it without requiring selector focus.
- Runtime contracts, registrations, collaborators, public action aliases, and key placeholders (`:event/metaKey`, `:event/ctrlKey`) were updated to keep action wiring coherent and contract-safe.
- Tests were expanded for shortcut action behavior and selector dropdown footer/keydown wiring.

Validation results:

- `npm run check` passed.
- `npm test` passed (1129 tests, 5187 assertions, 0 failures).
- `npm run test:websocket` passed (135 tests, 587 assertions, 0 failures).

## Context and Orientation

Asset selector rendering lives in `/hyperopen/src/hyperopen/views/asset_selector_view.cljs`. Asset selector state transitions live in `/hyperopen/src/hyperopen/asset_selector/actions.cljs` and are wired through `/hyperopen/src/hyperopen/core/public_actions.cljs`, `/hyperopen/src/hyperopen/registry/runtime.cljs`, `/hyperopen/src/hyperopen/runtime/collaborators.cljs`, and `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`. Runtime action contracts and event placeholders live in `/hyperopen/src/hyperopen/schema/contracts.cljs` and `/hyperopen/src/hyperopen/registry/runtime.cljs`.

In this repository, the selector is a deterministic view projection over app state. Any keyboard shortcut behavior must map to explicit action handlers and preserve immediate UI-state-first updates before heavier side effects.

## Plan of Work

Add a dedicated selector shortcut action that receives key and modifier flags plus the currently filtered market order. The action will handle open/close/select/favorite/navigation shortcut intents and update selector UI state in one deterministic transition. Extend selector state with a highlighted market key so keyboard navigation does not overwrite the currently active market until `Enter` confirms selection.

Update selector view composition to emit keydown actions and render a bottom shortcut footer row that mirrors Hyperliquid’s cues. Update row rendering so the highlighted row receives a visible style distinct from active-market selection.

Update runtime contract wiring for the new action and event placeholders (`metaKey`, `ctrlKey`). Expand action and view tests to cover keyboard paths and footer content.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/asset_selector/actions.cljs` to add shortcut action behavior and highlighted-key state updates.
2. Edit `/hyperopen/src/hyperopen/views/asset_selector_view.cljs` and `/hyperopen/src/hyperopen/views/active_asset_view.cljs` to wire keydown events and render footer shortcuts.
3. Edit runtime wiring files and contracts for new action registration/args/placeholders.
4. Add/adjust tests in `/hyperopen/test/hyperopen/asset_selector/actions_test.cljs`, `/hyperopen/test/hyperopen/views/asset_selector_view_test.cljs`, and any impacted view/runtime tests.
5. Run validation gates:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

## Validation and Acceptance

Acceptance criteria:

- Selector footer displays shortcut cues for open, navigate, select, favorite, and close.
- Keyboard behavior works in selector interaction scope: arrow keys move highlight, `Enter` selects highlighted market, `Cmd/Ctrl+S` toggles favorite on highlighted market, and `Esc` closes selector.
- Existing asset selection ordering and UI-first transition invariants remain intact.
- Required validation gates pass.

## Idempotence and Recovery

Changes are additive and safe to rerun. If shortcut handling regresses selector interactions, revert to existing selector action paths by removing the new shortcut action wiring while keeping tests as behavioral guardrails.

## Artifacts and Notes

Reference artifact used for parity context:

- `/hyperopen/tmp/browser-inspection/inspect-2026-02-19T16-37-52-633Z-b2773afb/hyperliquid/desktop/screenshot.png`

## Interfaces and Dependencies

No new external dependencies are required.

Expected interface additions:

- New action ID for selector shortcut key handling.
- Asset selector state key for highlighted market identity.
- New event placeholders for keyboard modifier detection.

Plan revision note: 2026-02-19 16:39Z - Initial plan created for shortcut footer parity and scoped keyboard behavior.
Plan revision note: 2026-02-19 16:56Z - Completed implementation, updated tests/contracts, validated required gates, and marked plan ready for completed folder.
Plan revision note: 2026-02-19 17:12Z - Added global keydown installation for reliable `Cmd/Ctrl+K` and `Esc` behavior regardless of focus, then reran required validation gates.
