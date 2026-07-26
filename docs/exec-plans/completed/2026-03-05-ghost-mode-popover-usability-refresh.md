# Keep Ghost Mode as a Popover While Adopting TradeXYZ-Style Internal Usability

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, Ghost Mode still opens as an anchored popover (not a blocking modal), but the content inside the popover becomes easier to scan and faster to use. The user-visible outcome is that the watchlist table dominates the panel, typography has clear hierarchy (title > row > header), actions are predictable, and copy feedback appears without jarring layout shifts. You can see the result by opening Ghost Mode and comparing one saved-row workflow (spectate, copy, edit) against the current baseline.

## Progress

- [x] (2026-03-05 17:04Z) Completed visual audit of TradeXYZ Ghost Mode and current Hyperopen Ghost Mode implementation.
- [x] (2026-03-05 17:04Z) Captured concrete UI structure and typography evidence from TradeXYZ (live app screenshot + computed styles).
- [x] (2026-03-05 17:06Z) Created and linked `bd` epic + child tasks for this usability refresh (`hyperopen-z1n`, `hyperopen-z1n.1`, `hyperopen-z1n.2`, `hyperopen-z1n.3`, `hyperopen-z1n.4`).
- [x] (2026-03-05 17:15Z) Implemented popover internal layout refresh in `/hyperopen/src/hyperopen/views/ghost_mode_modal.cljs` while preserving anchored popover behavior.
- [x] (2026-03-05 17:17Z) Ran required validation gates (`npm run check`, `npm test`, `npm run test:websocket`) after each issue and completed targeted manual Ghost Mode QA via local Playwright interaction.
- [x] (2026-03-05 17:17Z) Moved this plan to `/hyperopen/docs/exec-plans/completed/` after landing the refresh.

## Surprises & Discoveries

- Observation: TradeXYZ’s readability advantage comes mostly from hierarchy and density, not from color.
  Evidence: Trade computed styles sampled from live UI are approximately title `17.1px/600`, rows `13.3px/400`, headers `11.4px/500`.

- Observation: Our current Tailwind scale collapses `text-sm` and `text-xs` to the same `12px` size.
  Evidence: `/hyperopen/tailwind.config.js` defines `xs` as `12px` and overrides `sm` to `12px`, reducing visual separation in Ghost Mode table content.

- Observation: Current Ghost Mode popover places many controls above the list, reducing immediate scanability of saved addresses.
  Evidence: `/hyperopen/src/hyperopen/views/ghost_mode_modal.cljs` includes description text, dual labeled inputs, and a large multi-button action bar before the watchlist block.

- Observation: Clipboard write can fail in headless local QA, but the new feedback slot still shows a stable inline error without reflowing the watchlist.
  Evidence: Local manual run produced `"Couldn't copy address"` while `/tmp/hyperopen-ghost-modal-qa.png` shows the feedback row anchored in the reserved footer slot.

## Decision Log

- Decision: Keep Ghost Mode as an anchored popover and do not convert to modal architecture.
  Rationale: The product requirement is explicit, and the current anchored interaction preserves context while trading.
  Date/Author: 2026-03-05 / Codex

- Decision: Adopt TradeXYZ’s internal information architecture (single primary search row, compact table-first center area, subtle keyboard hint footer) while keeping Hyperopen behavior contracts.
  Rationale: This directly targets readability and speed without breaking existing runtime/state design.
  Date/Author: 2026-03-05 / Codex

- Decision: Prioritize typography and spacing changes before adding any new controls.
  Rationale: The measured usability gap is hierarchy/density; control proliferation is already a source of visual noise.
  Date/Author: 2026-03-05 / Codex

## Outcomes & Retrospective

The refresh met the stated purpose. Ghost Mode remains an anchored popover, and internal readability now follows a clearer hierarchy: compact title bar, search-first interaction, conditional label input, table-first watchlist region, and a dedicated footer for hints plus copy feedback. The watchlist row treatment now emphasizes label readability while keeping address truncated and action controls visually secondary.

Validation and QA were completed repeatedly during execution: `npm run check`, `npm test`, and `npm run test:websocket` passed after each tracked issue. A local browser QA pass (Playwright against `http://localhost:8080/index.html`) confirmed modal anchoring, row rendering, footer hint visibility, and feedback-slot behavior.

Remaining gap: copy success in local headless QA could not be validated because clipboard permission is restricted in that context; only the error-path rendering was observed in browser automation. Runtime copy behavior remains wired through existing wallet-copy feedback flow.

## Context and Orientation

Ghost Mode rendering is implemented in `/hyperopen/src/hyperopen/views/ghost_mode_modal.cljs`. This file currently does two jobs: (1) anchored popover positioning (`anchored-panel-layout-style`) and (2) all content markup (header, inputs, action buttons, watchlist table, copy feedback). Ghost Mode state and watchlist actions are handled through existing runtime actions and reducers in `/hyperopen/src/hyperopen/account/ghost_mode_actions.cljs` and supporting account context helpers in `/hyperopen/src/hyperopen/account/context.cljs`.

The popover must remain a non-blocking anchored surface, so all layout work must stay inside `ghost_mode_modal.cljs` and preserve the current open/close/anchor contract. The objective is to reorganize visual structure and typography only where it improves clarity, while preserving existing user operations: start spectating, stop spectating, add/edit/remove watchlist entry, copy address, placeholder link icon, and spectate from watchlist row.

## Plan of Work

Milestone 1 modernizes the top of the popover to a scan-first structure. Replace the current description-heavy header plus dual stacked form groups with a compact header and one primary search row. Keep label entry available, but render it contextually (for add/edit cases) so the default state is not visually overloaded.

Milestone 2 refactors the watchlist table region to become the visual center. Use a stable three-column grid with explicit column intent: label, address, actions. Keep label untruncated where possible; keep address truncated with ellipsis. Adjust row padding, text sizes, and icon affordances to reduce noise while keeping all existing actions.

Milestone 3 reworks inline feedback and footer hints to reduce motion disruption. Keep copy feedback inside popover scope but anchor it to the bottom area so it does not shift the full list stack. Keep keyboard hint strip subtle and secondary to table content.

Milestone 4 focuses on validation and polish. Run all required gates, then execute manual Ghost Mode checks for keyboard navigation, edit flow, copy feedback timing, and anchor stability near viewport edges.

## Concrete Steps

From `/Users/barry/.codex/worktrees/5da9/hyperopen`:

1. Create tracking issues in `bd` for this plan (epic + children) and attach plan path in descriptions.
2. Edit `/hyperopen/src/hyperopen/views/ghost_mode_modal.cljs`:
   - Keep `anchored-panel-layout-style` and popover shell behavior unchanged.
   - Recompose content sections in this order: compact header, primary search row (+ spectate action), conditional label row, tabs, table header, rows/list, bottom feedback/hints.
   - Update typographic class usage so title, rows, and column headers are clearly differentiated.
3. If needed for global text-scale parity, adjust `/hyperopen/tailwind.config.js` only if this change does not regress other views; otherwise use explicit utility classes in Ghost Mode only.
4. Run required quality gates:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
5. Perform manual QA:
   - Open Ghost Mode from header.
   - Confirm popover remains anchored and non-modal.
   - Confirm row readability with at least three watchlist entries.
   - Confirm copy feedback appears in bottom area without jarring list displacement.
   - Confirm label is fully readable while address truncates with ellipsis.

Expected transcript snippets after successful validation should include all three gate commands exiting with status `0`.

## Validation and Acceptance

Acceptance is complete when all conditions hold:

- Ghost Mode remains an anchored popover and does not block broader page context.
- The first visible interaction path is search/spectate plus readable watchlist table, not a control-heavy form wall.
- Watchlist rows are easier to scan: label prominence is higher than address; address truncation is consistent; actions are visually secondary but discoverable.
- Copy action provides clear transient confirmation in the popover bottom area with reduced layout shift.
- Existing Ghost Mode behaviors remain functional (start/stop, add/edit/remove, copy, placeholder link, row spectate).
- Required validation gates pass.

## Idempotence and Recovery

All steps are safe to rerun. UI edits are localized to Ghost Mode rendering and can be reapplied without schema migration. If a style iteration regresses readability, revert only affected class lists in `/hyperopen/src/hyperopen/views/ghost_mode_modal.cljs` and re-run gates. If broader font-scale changes are attempted in Tailwind and cause cross-view regressions, revert the Tailwind edit and keep typography overrides local to Ghost Mode.

## Artifacts and Notes

Evidence artifacts captured during planning:

- Trade live Ghost Mode screenshot: `/tmp/trade-app-after-shortcut.png`.
- Trade Ghost Mode style sample JSON: `/tmp/trade-ghost-style.json`.
- Current Hyperopen Ghost Mode implementation: `/hyperopen/src/hyperopen/views/ghost_mode_modal.cljs`.

## Interfaces and Dependencies

No new runtime interface is required. Keep existing action handlers and data flow intact:

- `:actions/start-ghost-mode`
- `:actions/stop-ghost-mode`
- `:actions/add-ghost-mode-watchlist-address`
- `:actions/edit-ghost-mode-watchlist-address`
- `:actions/remove-ghost-mode-watchlist-address`
- `:actions/copy-ghost-mode-watchlist-address`
- `:actions/spectate-ghost-mode-watchlist-address`

If helper view functions are added, keep them private to `/hyperopen/src/hyperopen/views/ghost_mode_modal.cljs` and avoid expanding public API surface.

Revision note (2026-03-05): Created initial ExecPlan for Ghost Mode popover usability refresh based on direct TradeXYZ visual inspection and current Hyperopen source audit, so implementation can proceed with explicit acceptance criteria.
Revision note (2026-03-05): Updated Progress to reflect creation of `bd` epic and child tracking issues so execution status is accurate and restart-safe.
Revision note (2026-03-05): Updated Progress, Surprises, and Outcomes after completing implementation, repeated validation gates, and local browser QA evidence capture.
Revision note (2026-03-05): Moved plan from active to completed after all tracked work and validations finished.
