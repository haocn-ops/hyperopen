# Portfolio Chart Parity: Hover Tooltip + Vertical Marker Interaction on `/portfolio`

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Today the `/portfolio` chart renders historical lines but does not expose Hyperliquid-like hover inspection behavior. Users cannot hover the line to read the exact timestamp and value for that point in history. After this change, hovering the portfolio chart will show a vertical marker and a tooltip with timestamp and value at the hovered point, matching the interaction pattern users expect from Hyperliquid for `Account Value` and `PNL` charts (and preserving deterministic behavior for `Returns` as well).

A user can verify this by opening `/portfolio`, moving the pointer across the chart, and observing that a vertical hover marker appears with a tooltip whose first token is time for `24H` and date for longer ranges, followed by the corresponding value.

## Progress

- [x] (2026-02-26 17:43Z) Audited current portfolio chart VM/view/actions and required UI policy docs (`/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, `/hyperopen/docs/agent-guides/trading-ui-policy.md`).
- [x] (2026-02-26 17:43Z) Authored this ExecPlan.
- [x] (2026-02-26 17:47Z) Implemented chart hover state plumbing for deterministic pointer tracking (actions, runtime wiring, placeholder registration, contracts, defaults).
- [x] (2026-02-26 17:48Z) Implemented chart hover overlay rendering (vertical marker + tooltip text/value formatting, date/time split by range).
- [x] (2026-02-26 17:49Z) Added/adjusted tests for action behavior, VM output, and view rendering contracts.
- [x] (2026-02-26 17:50Z) Ran required gates (`npm run check`, `npm test`, `npm run test:websocket`) and confirmed green status.

## Surprises & Discoveries

- Observation: Existing placeholder registration does not currently expose pointer x-position (`clientX`/`offsetX`) used for hover interpolation; only target value/checked, key, timestamp, scrollTop, and currentTarget bounds are registered.
  Evidence: `/hyperopen/src/hyperopen/registry/runtime.cljs` `register-placeholders!` includes `:event.currentTarget/bounds` but no `:event/clientX`.

- Observation: Portfolio chart already carries normalized point coordinates (`:x-ratio`, `:y-ratio`, `:value`, `:time-ms`) in VM output, so hover display can be added without adding side effects or new data-fetch dependencies.
  Evidence: `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` `normalize-chart-points` and `build-chart-model`.

- Observation: `re-matches` in ClojureScript requires careful regex escaping for literal `$` in tooltip assertions; the first test pattern used an over-escaped form and failed despite correct runtime output.
  Evidence: First `npm test` run failed only at `portfolio-view-chart-hover-overlay-renders-date-and-time-tooltip-variants-test` while actual tooltip text rendered `\"08:30: $203\"`; second run passed after pattern fix.

- Observation: If pointer payload resolution fails (for example stale runtime placeholder registration during hot-reload), hover updates can remain `nil` and suppress the tooltip.
  Evidence: Hover behavior depends on `:event/clientX` + bounds payload in `set-portfolio-chart-hover`; adding graceful fallback index selection prevents tooltip suppression when pointer coordinates are unavailable.

- Observation: Contract rejection can prevent hover fallback logic from running when `clientX` is missing, because payload validation runs before the action body.
  Evidence: `:actions/set-portfolio-chart-hover` originally required numeric `clientX`; relaxing that arg to nilable allows fallback path execution.

## Decision Log

- Decision: Implement hover state via action-driven state (`:portfolio-ui :chart-hover-index`) rather than ad hoc local mutable DOM state.
  Rationale: This keeps behavior deterministic and testable under existing runtime/action architecture.
  Date/Author: 2026-02-26 / Codex

- Decision: Compute nearest hovered data index in an action from pointer x and chart bounds, then derive display payload in VM.
  Rationale: Keeps rendering pure and reusable while allowing thin event handlers in the view.
  Date/Author: 2026-02-26 / Codex

- Decision: Clear chart hover index whenever chart context changes (scope/range/tab selection).
  Rationale: Prevents stale hover references when the plotted series or point count changes.
  Date/Author: 2026-02-26 / Codex

- Decision: Treat missing/invalid pointer payload in hover updates as a non-destructive fallback path (retain current hover index or default to index `0` when points exist), instead of clearing hover state.
  Rationale: Keeps tooltip visible under degraded placeholder-resolution conditions while still clearing explicitly on mouse leave.
  Date/Author: 2026-02-26 / Codex

- Decision: Bind additional hover lifecycle events (`mouseenter`, `pointermove`, `pointerenter`, `pointerleave`, `mouseout`) in addition to `mousemove`/`mouseleave`.
  Rationale: Reduces dependence on one mouse event path and improves cross-input reliability for tooltip activation/teardown.
  Date/Author: 2026-02-26 / Codex

## Outcomes & Retrospective

Implemented and validated end-to-end. The `/portfolio` chart now supports hover parity behavior with:

- Pointer-driven vertical marker on the plot area.
- Tooltip text that shows `HH:MM` in `24H` mode and `YYYY Mon DD` for longer ranges.
- Hovered value formatting tied to selected tab (`$` values for account value/PNL and percent for returns).
- Deterministic state handling through new `:portfolio-ui :chart-hover-index` action-driven updates.

Coverage was added at the action, VM, view, contracts, collaborator-wiring, and defaults levels. The only test iteration issue was a regex literal escape typo in one new view assertion; that was corrected and all suites passed.

## Context and Orientation

Primary files for this change:

- `/hyperopen/src/hyperopen/views/portfolio_view.cljs` renders the chart card and SVG series.
- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` derives chart series, points, y-axis ticks, and tab metadata.
- `/hyperopen/src/hyperopen/portfolio/actions.cljs` owns portfolio UI action handlers and effect ordering for chart-tab/range interactions.
- `/hyperopen/src/hyperopen/state/app_defaults.cljs` defines default `:portfolio-ui` state.
- `/hyperopen/src/hyperopen/registry/runtime.cljs`, `/hyperopen/src/hyperopen/runtime/collaborators.cljs`, `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`, `/hyperopen/src/hyperopen/schema/contracts.cljs`, and `/hyperopen/src/hyperopen/core/public_actions.cljs` wire action IDs/placeholders/contracts.

Primary tests:

- `/hyperopen/test/hyperopen/portfolio/actions_test.cljs`
- `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`
- `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`

## Plan of Work

Milestone 1 adds chart-hover state transitions by introducing action handlers for hover move and hover clear, wiring those action IDs through runtime registries/collaborators/contracts, and adding any required event placeholders for pointer coordinates.

Milestone 2 extends portfolio VM output with hovered-point details that are safe under all chart states (no data, single point, out-of-range index). This includes formatting-ready fields for timestamp and value.

Milestone 3 updates portfolio chart rendering to attach mouse handlers on the plot area and render a vertical marker plus tooltip when hover state is active.

Milestone 4 updates tests and executes required validation gates.

## Concrete Steps

1. Add hover actions and helper math in `/hyperopen/src/hyperopen/portfolio/actions.cljs`.
2. Wire new actions in runtime registry/collaborator/composition/public-exports/contracts.
3. Add pointer placeholder(s) in `/hyperopen/src/hyperopen/registry/runtime.cljs`.
4. Add default hover field in `/hyperopen/src/hyperopen/state/app_defaults.cljs`.
5. Extend `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` to expose hovered point payload.
6. Update `/hyperopen/src/hyperopen/views/portfolio_view.cljs` with hover event handlers and overlay markup.
7. Update tests in actions/vm/view test namespaces.
8. Run from repo root (`/Users//projects/hyperopen`):

    npm run check
    npm test
    npm run test:websocket

## Validation and Acceptance

The work is accepted when:

1. Hovering the portfolio chart plot area shows a vertical marker at the hovered x-position and hides it on mouse leave.
2. Tooltip text follows parity intent: `24H` range shows `HH:MM`, longer ranges show date, and tooltip includes corresponding chart value.
3. Behavior is deterministic with missing/empty chart points (no runtime errors).
4. Tests covering actions, VM, and view contracts pass.
5. Required gates pass (`npm run check`, `npm test`, `npm run test:websocket`).

## Idempotence and Recovery

All changes are source-level and can be reapplied safely. If hover rendering regresses chart stability, recovery is to keep action/state additions and temporarily disable overlay rendering in the view while preserving tests for the state transitions.

## Artifacts and Notes

Validation commands executed from `/Users//projects/hyperopen`:

- `npm run check` (pass)
- `npm test` (pass after one assertion-pattern fix)
- `npm run test:websocket` (pass)

## Interfaces and Dependencies

Expected interfaces after completion:

- New action IDs for chart hover update/clear.
- New `:portfolio-ui` hover field storing hovered chart index.
- VM chart payload including optional hovered-point metadata used by the chart view.

No external dependencies are introduced.

Plan revision note: 2026-02-26 17:43Z - Initial plan authored for portfolio chart hover tooltip + vertical marker parity implementation.
Plan revision note: 2026-02-26 17:50Z - Updated progress/outcomes after implementation and required gate validation; ready to move to completed plans.
Plan revision note: 2026-02-26 18:03Z - Added defensive hover-index fallback to prevent missing-tooltip behavior when pointer payload resolution is unavailable; re-ran full validation gates.
Plan revision note: 2026-02-26 18:16Z - Added nil-tolerant hover-action contract and expanded pointer/mouse enter/leave bindings for robust tooltip activation.
