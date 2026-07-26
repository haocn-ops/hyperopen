# Positions TP/SL Non-Blocking Panel Parity with trade.xyz

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, opening TP/SL from a Positions row will feel like trade.xyz: a lightweight floating order box appears without dimming or blocking the rest of the trading surface. The chart and other account-table hover interactions remain usable while the TP/SL box is open, and the box closes when the user clicks elsewhere.

A user can verify this by opening the Positions tab, clicking TP/SL, moving the mouse over chart and table surfaces while the TP/SL box remains open, then clicking outside the box and seeing it close immediately without a full-screen backdrop transition.

## Progress

- [x] (2026-02-24 17:07Z) Audited current Hyperopen implementation: TP/SL is rendered as a full-screen modal overlay in `/hyperopen/src/hyperopen/views/account_info/position_tpsl_modal.cljs` and mounted from `/hyperopen/src/hyperopen/views/app_view.cljs`.
- [x] (2026-02-24 17:07Z) Gathered trade.xyz references via browser inspection and bundle analysis (`/hyperopen/tmp/browser-inspection/inspect-2026-02-24T17-02-33-498Z-01d80dc1/`, `/hyperopen/tmp/trade-xyz-inspect/beauty/7899-0617508e185314bd.beauty.js`).
- [x] (2026-02-24 17:08Z) Authored this active ExecPlan.
- [x] (2026-02-24 17:12Z) Implemented non-blocking row-anchored TP/SL panel rendering and removed global modal mount path.
- [x] (2026-02-24 17:13Z) Added click-away close runtime listener (`mousedown`) that does not block background pointer/hover interactions.
- [x] (2026-02-24 17:14Z) Updated regression tests for panel rendering and startup listener behavior.
- [x] (2026-02-24 17:16Z) Ran required validation gates successfully: `npm run check`, `npm test`, `npm run test:websocket`.

## Surprises & Discoveries

- Observation: trade.xyz production landing route and app route differ (`https://trade.xyz` vs `https://app.trade.xyz`), and direct authenticated live TP/SL panel DOM capture is not guaranteed from headless read-only inspection sessions.
  Evidence: Browser-inspection artifacts show route-dependent content and read-only eval guardrails blocking synthetic clicks.

- Observation: Hyperopen currently mounts Position TP/SL once at app-root level, with `fixed inset-0` and backdrop close handling.
  Evidence: `/hyperopen/src/hyperopen/views/account_info/position_tpsl_modal.cljs` uses `:class ["fixed" "inset-0" ...]` and a `bg-black/60` backdrop layer.

- Observation: trade.xyz position TP/SL flow semantics already implemented in Hyperopen validation/request logic are correct for this scope; the parity gap is primarily interaction model and layering behavior.
  Evidence: Existing validated module `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` includes side-aware validation and dynamic submit labels that match reverse-engineered trade.xyz bundle strings.

- Observation: `npm run test:websocket` can be transient in this workspace; one run failed in `runtime_engine_test` and an immediate rerun passed with no code changes.
  Evidence: First websocket run reported 3 failures in `start-engine-records-runtime-messages-and-effects-test`; second run passed all 147 websocket tests.

## Decision Log

- Decision: Convert TP/SL presentation from global modal to row-anchored floating panel rendered within the Positions table cell.
  Rationale: This directly removes full-screen dimming/blocking and matches the user-visible interaction goal without changing request semantics.
  Date/Author: 2026-02-24 / Codex

- Decision: Implement click-away close via a global `mousedown` listener installed at startup instead of backdrop interception.
  Rationale: Click-away should close the panel while allowing chart/table hover and pointer behaviors to continue unblocked.
  Date/Author: 2026-02-24 / Codex

## Outcomes & Retrospective

Implemented and validated. Position TP/SL now renders as an inline floating panel anchored to the Positions row action cell, with no full-screen backdrop or `aria-modal` blocking semantics. Background chart/table hover behavior remains available while TP/SL is open, and click-away close is handled by a startup-installed global `mousedown` listener that checks panel/trigger data attributes before dispatching close.

Submit path and validation logic remain unchanged and are still routed through existing position TP/SL actions/effects. Tests were extended for panel rendering and startup listener behavior, and required gates passed (`npm run check`, `npm test`, `npm run test:websocket`).

## Context and Orientation

The current TP/SL state and submit path are already implemented and tested in:

- `/hyperopen/src/hyperopen/account/history/actions.cljs`
- `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs`
- `/hyperopen/src/hyperopen/order/effects.cljs`

The current blocking modal presentation path is:

- `/hyperopen/src/hyperopen/views/account_info/position_tpsl_modal.cljs`
- `/hyperopen/src/hyperopen/views/app_view.cljs`

The Positions row entry point is:

- `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs`

Startup listener wiring for global keyboard shortcuts already exists and is the reference pattern for adding global click-away behavior:

- `/hyperopen/src/hyperopen/startup/runtime.cljs`
- `/hyperopen/src/hyperopen/startup/init.cljs`
- `/hyperopen/src/hyperopen/app/startup.cljs`

Relevant tests:

- `/hyperopen/test/hyperopen/views/account_info/tabs/positions_test.cljs`
- `/hyperopen/test/hyperopen/account/history/actions_test.cljs`
- `/hyperopen/test/hyperopen/startup/runtime_test.cljs`
- `/hyperopen/test/hyperopen/startup/init_test.cljs`

## Plan of Work

Milestone 1 will remove app-root modal layering and render TP/SL as a floating panel anchored to the row TP/SL cell in Positions. This includes preserving existing form content but dropping backdrop semantics (`fixed inset-0`, `aria-modal true`) and introducing panel surface markers for click-away exclusion.

Milestone 2 will add a startup-installed global `mousedown` listener that checks whether the panel is open and whether the event target is outside both the panel surface and TP/SL trigger. If outside, dispatch `:actions/close-position-tpsl-modal`. This must not prevent default behavior, ensuring background interactions stay responsive.

Milestone 3 will update view-model and render wiring so Positions can render the active TP/SL panel by row key, and app root no longer mounts the full-screen component globally.

Milestone 4 will update regression tests to cover panel rendering (non-blocking style shape), click-away behavior, and startup init ordering for the new listener installer.

## Concrete Steps

1. Implement row-anchored panel rendering and remove global modal mount.

   cd /Users//projects/hyperopen
   npm test -- positions

   Expected result: Positions view tests pass with TP/SL panel rendered inline for active row.

2. Add global click-away listener wiring and tests.

   cd /Users//projects/hyperopen
   npm test -- startup/runtime startup/init

   Expected result: Startup listener tests pass; init order includes TP/SL click-away install.

3. Run required validation gates.

   cd /Users//projects/hyperopen
   npm run check
   npm test
   npm run test:websocket

   Expected result: all commands exit 0.

## Validation and Acceptance

This change is accepted when all of the following are true:

1. Clicking TP/SL in a Positions row opens a floating panel without full-screen dimming overlay.
2. The chart and other background surfaces remain hoverable while TP/SL is open.
3. Clicking outside the TP/SL panel closes it.
4. Escape and explicit close button still close the panel.
5. TP/SL submit/validation behavior remains unchanged and functional.
6. Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

All edits are source-level and repeatable. If click-away listener behavior regresses unrelated interactions, disable only the listener installer wiring while retaining row-anchored panel rendering, then re-run tests to isolate runtime-listener-specific issues.

## Artifacts and Notes

Reference artifacts used for this parity pass:

- `/hyperopen/tmp/browser-inspection/inspect-2026-02-24T17-02-33-498Z-01d80dc1/trade-xyz-root-rerun/desktop/screenshot.png`
- `/hyperopen/tmp/trade-xyz-inspect/beauty/7899-0617508e185314bd.beauty.js`

## Interfaces and Dependencies

No new external dependency is required.

Required interfaces after implementation:

- Positions row render path can accept active TP/SL modal state and conditionally render panel by row key.
- Startup runtime exposes a TP/SL click-away installer invoked during app init.
- TP/SL panel root and trigger expose stable data attributes for click-away exclusion checks.

Plan revision note: 2026-02-24 17:08Z - Initial plan authored for non-blocking TP/SL panel parity and click-away behavior.
Plan revision note: 2026-02-24 17:16Z - Marked implementation complete with validation results and runtime-test discovery.
