# Chart Volume Pane Indicator Controls and Menu Toggle

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Today the chart always renders a volume pane but does not expose a visible volume-indicator label in that pane, does not show the hovered bar value in the pane header, and does not provide a direct chart-surface control to remove the volume indicator. After this change, the volume pane will show a TradingView-style top-left indicator label (`Volume SMA` + hovered value), provide a hover/focus control strip with remove and settings placeholder controls, and allow users to remove/re-add the built-in volume pane from the Indicators menu.

## Progress

- [x] (2026-02-18 14:12Z) Reviewed `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md`.
- [x] (2026-02-18 14:12Z) Audited chart runtime flow and target files: `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`, `/hyperopen/src/hyperopen/views/trading_chart/indicators_dropdown.cljs`, `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`, `/hyperopen/src/hyperopen/chart/settings.cljs`, `/hyperopen/src/hyperopen/chart/actions.cljs`, and related contracts/registry wiring.
- [x] (2026-02-18 14:12Z) Created this active ExecPlan.
- [x] (2026-02-18 14:18Z) Implemented chart option state + action wiring for built-in volume visibility (`:volume-visible?`) across defaults, settings restore/persist, runtime collaborators/registry composition, action registry, and action contract specs.
- [x] (2026-02-18 14:19Z) Added `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/volume_indicator_overlay.cljs` and chart interop facade wiring (`sync-volume-indicator-overlay!`, `clear-volume-indicator-overlay!`) for volume pane label/value/control rendering.
- [x] (2026-02-18 14:20Z) Updated chart lifecycle in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` to pass `:volume-visible?`, create/destroy/sync the volume overlay, and dispatch hide action from pane control.
- [x] (2026-02-18 14:20Z) Added Indicators menu built-in volume toggle row in `/hyperopen/src/hyperopen/views/trading_chart/indicators_dropdown.cljs` for remove/re-add behavior.
- [x] (2026-02-18 14:22Z) Added/updated tests in settings/core/runtime/app-defaults/chart-interop suites for new state/actions and hidden-volume chart construction branches.
- [x] (2026-02-18 14:23Z) Ran required gates: `npm run check`, `npm test`, `npm run test:websocket` (all green).
- [x] (2026-02-18 14:24Z) Moved this ExecPlan from `/hyperopen/docs/exec-plans/active/` to `/hyperopen/docs/exec-plans/completed/` after completion.

## Surprises & Discoveries

- Observation: The chart already has a built-in volume pane that is not represented in `:active-indicators`; `:active-indicators` only drives derived domain indicators.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` + `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` always add a dedicated volume histogram series in a separate pane.
- Observation: A domain indicator named `:volume` also exists in the Indicators catalog, so built-in volume pane controls must be modeled separately to avoid conflating behaviors.
  Evidence: `/hyperopen/src/hyperopen/domain/trading/indicators/catalog/flow.cljs` and `/hyperopen/src/hyperopen/views/trading_chart/indicators_dropdown.cljs`.
- Observation: ClojureScript test stubs for redefined multi-arity functions need explicit matching arities; variadic-only stubs caused runtime `arity$N` errors in wrapper tests.
  Evidence: `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop_test.cljs` initial `npm test` run failures and subsequent arity-specific stub fixes.

## Decision Log

- Decision: Represent built-in volume-pane visibility as chart option state (`:chart-options :volume-visible?`) rather than overloading `:active-indicators`.
  Rationale: Built-in pane lifecycle is chart-structural and separate from domain indicator calculations; this keeps responsibilities explicit and avoids accidental derived-indicator side effects.
  Date/Author: 2026-02-18 / Codex
- Decision: Implement the volume pane label/control strip as chart interop DOM overlay logic (same boundary style as legend and open-order overlays).
  Rationale: The overlay depends on chart crosshair callbacks and pane geometry, which are runtime/chart concerns rather than static Hiccup layout concerns.
  Date/Author: 2026-02-18 / Codex
- Decision: Keep a settings gear icon as non-functional placeholder while fully wiring remove/re-add.
  Rationale: Matches requested scope while leaving a stable affordance for future settings implementation.
  Date/Author: 2026-02-18 / Codex
- Decision: Use dedicated action IDs (`:actions/show-volume-indicator`, `:actions/hide-volume-indicator`) instead of overloading `add-indicator/remove-indicator`.
  Rationale: Preserves current domain-indicator semantics and avoids conflicts with the existing catalog `:volume` indicator entry.
  Date/Author: 2026-02-18 / Codex

## Outcomes & Retrospective

Implementation completed for this scope. The chart now has first-class built-in volume visibility state (`:chart-options :volume-visible?`) with persistence, explicit runtime actions, pane-local indicator overlay controls, and Indicators-menu re-add/remove support.

User-visible outcomes:

- Volume pane shows `Volume SMA` + dynamic hovered/latest volume value in the pane header.
- Hover/focus on that header reveals controls with a settings placeholder icon and a working remove (trash) action.
- Removing volume from the pane updates chart structure deterministically.
- Indicators dropdown now includes a chart-level `Volume` row that re-adds the built-in volume pane after removal.

Validation outcomes:

- `npm run check` passed.
- `npm test` passed.
- `npm run test:websocket` passed.

## Context and Orientation

The trading chart view is assembled in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`. It computes transformed candle data and calls chart-interop constructors that currently always create a main series and a built-in volume histogram pane.

The chart JS integration boundary lives in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` and companion modules under `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/**`. These modules already manage chart-local DOM overlays (legend, open-order overlays) with deterministic mount/update/unmount cleanup.

Chart option persistence and indicator menu actions are handled in `/hyperopen/src/hyperopen/chart/settings.cljs`, `/hyperopen/src/hyperopen/chart/actions.cljs`, runtime collaborator wiring, and schema contracts/registry maps. Any new action IDs must be added consistently across contracts and runtime registration to preserve action validation invariants.

## Plan of Work

Milestone 1 introduces a dedicated built-in volume visibility state (`:volume-visible?`) in chart options defaults, restore path, and persistence path. It also adds explicit chart settings handlers for hide/show volume, and wires those handlers through runtime collaborators/registry plus action-contract maps.

Milestone 2 updates chart rendering so volume series and pane are created only when `:volume-visible?` is true. This includes passing the flag through `trading-chart-view -> chart-canvas -> chart-interop` and ensuring remount/update keys capture visibility changes.

Milestone 3 adds a new chart interop overlay module for the volume pane header text and controls. The overlay tracks crosshair position to show hovered volume value, renders `Volume SMA` label text, and reveals control buttons on hover/focus. The remove button triggers the new hide-volume action; the settings gear is rendered as a non-functional placeholder button.

Milestone 4 extends the Indicators dropdown UI to include a chart-indicator row for built-in volume so users can re-add it after removal. This row uses explicit add/remove actions and keeps existing domain indicator rows unchanged.

Milestone 5 extends tests and then runs mandatory validation gates.

## Concrete Steps

From `/hyperopen`:

1. Add volume visibility defaults/restore/persist:
   - `/hyperopen/src/hyperopen/state/app_defaults.cljs`
   - `/hyperopen/src/hyperopen/chart/settings.cljs`
2. Wire new actions through contracts/registry/collaborators:
   - `/hyperopen/src/hyperopen/schema/contracts.cljs`
   - `/hyperopen/src/hyperopen/runtime/collaborators.cljs`
   - `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`
   - `/hyperopen/src/hyperopen/registry/runtime.cljs`
   - `/hyperopen/src/hyperopen/core/public_actions.cljs`
   - `/hyperopen/src/hyperopen/core/macros.clj`
3. Implement chart interop + view updates:
   - `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`
   - `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`
   - `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/volume_indicator_overlay.cljs` (new)
4. Add indicators-menu built-in volume row:
   - `/hyperopen/src/hyperopen/views/trading_chart/indicators_dropdown.cljs`
5. Update tests:
   - `/hyperopen/test/hyperopen/chart/settings_test.cljs`
   - `/hyperopen/test/hyperopen/core_bootstrap_test.cljs`
   - `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs`
   - `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop_test.cljs`
   - `/hyperopen/test/hyperopen/state/app_defaults_test.cljs`
   - any additional registry/contract drift tests if required by failures
6. Run validation:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

Expected transcript shape:

  - `npm run check`: completes without lint/compile failures.
  - `npm test`: all test suites pass.
  - `npm run test:websocket`: websocket suite passes.

## Validation and Acceptance

Acceptance is met when:

- The volume pane displays top-left indicator text in chart runtime (`Volume SMA`) and shows a dynamic hovered value that follows crosshair position (falling back to latest value when not hovering a bar).
- Hovering or keyboard-focusing the volume indicator header reveals a control strip with remove and settings placeholder icons.
- Clicking remove from the volume pane header hides the built-in volume pane immediately.
- Indicators menu includes a built-in volume row that can re-add the pane after removal.
- Existing domain indicator add/remove behavior remains intact.
- Required validation gates pass.

## Idempotence and Recovery

Changes are additive and deterministic. Re-running toggles and render paths is safe because chart interop state is sidecar-managed and teardown removes subscriptions/DOM. If regressions occur, disabling the new volume overlay module and restoring `:volume-visible?` default behavior returns chart rendering to prior always-on volume behavior without schema/data migration.

## Artifacts and Notes

Planned changed files:

- `/hyperopen/docs/exec-plans/active/2026-02-18-chart-volume-pane-indicator-controls.md`
- `/hyperopen/docs/exec-plans/completed/2026-02-18-chart-volume-pane-indicator-controls.md`
- `/hyperopen/src/hyperopen/state/app_defaults.cljs`
- `/hyperopen/src/hyperopen/chart/settings.cljs`
- `/hyperopen/src/hyperopen/schema/contracts.cljs`
- `/hyperopen/src/hyperopen/runtime/collaborators.cljs`
- `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`
- `/hyperopen/src/hyperopen/registry/runtime.cljs`
- `/hyperopen/src/hyperopen/core/public_actions.cljs`
- `/hyperopen/src/hyperopen/core/macros.clj`
- `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/indicators_dropdown.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/volume_indicator_overlay.cljs` (new)
- `/hyperopen/test/hyperopen/chart/settings_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap_test.cljs`
- `/hyperopen/test/hyperopen/state/app_defaults_test.cljs`
- `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs`
- `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop_test.cljs`

## Interfaces and Dependencies

At completion, these interfaces should exist:

- `hyperopen.chart.settings/hide-volume-indicator`
- `hyperopen.chart.settings/show-volume-indicator`
- `hyperopen.views.trading-chart.utils.chart-interop/sync-volume-indicator-overlay!`
- `hyperopen.views.trading-chart.utils.chart-interop/clear-volume-indicator-overlay!`

Interop assumptions:

- Lightweight Charts crosshair callbacks continue to provide a time key usable for candle lookup.
- Chart pane indexing remains stable enough to map the built-in volume pane index from chart creation context.

Plan revision note: 2026-02-18 14:12Z - Initial ExecPlan created for built-in chart volume indicator controls and menu-based remove/re-add behavior.
Plan revision note: 2026-02-18 14:23Z - Updated living sections after implementation completion, test fixes, and successful validation gates.
Plan revision note: 2026-02-18 14:24Z - Moved plan to completed per planning workflow after scope acceptance and validation.
