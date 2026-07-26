# Active Asset Funding Tooltip Design Polish

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and must be maintained in accordance with that file.

## Purpose / Big Picture

The funding tooltip is functionally correct, but visual hierarchy and spacing still feel uneven. After this change, the tooltip should read quickly at a glance: section labels, column headers, and values will align cleanly with stronger typographic rhythm and reduced visual noise.

You can verify this by hovering `Funding / Countdown` and checking that `Position`, `Projections`, `Rate`, and `Payment` are aligned and easy to scan without crowding.

## Progress

- [x] (2026-03-01 19:07Z) Logged visual issues from screenshot: oversized heading/body contrast, compressed row rhythm, and inconsistent density between section headers and numeric rows.
- [x] (2026-03-01 19:12Z) Applied design polish to tooltip typography, spacing, and hierarchy in `/hyperopen/src/hyperopen/views/active_asset_view.cljs`.
- [x] (2026-03-01 19:13Z) Re-ran regression tests with `npx shadow-cljs compile test && node out/test.js`.
- [x] (2026-03-01 19:13Z) Updated this plan with outcomes and design decisions.

## Surprises & Discoveries

- Observation: The current card applies relatively large `0.9rem` row text and `leading-5`, which makes the compact tooltip feel dense while still looking vertically uneven.
  Evidence: Existing `funding-tooltip-panel` grid classes in `active_asset_view.cljs`.

## Decision Log

- Decision: Keep existing data model and only refine presentation classes (typography, spacing, and alignment).
  Rationale: User request is design polish rather than behavioral change; preserving content avoids regression risk.
  Date/Author: 2026-03-01 / Codex

- Decision: Place `Projections`, `Rate`, and `Payment` in the same first row of one shared grid.
  Rationale: This guarantees true horizontal alignment and improves scanability of labels to values.
  Date/Author: 2026-03-01 / Codex

- Decision: Reduce visual density by using a restrained type ramp (`text-xs` base, compact row sizes) and slightly larger section headers.
  Rationale: Keeps hierarchy obvious without oversized text that competes with numeric values.
  Date/Author: 2026-03-01 / Codex

## Outcomes & Retrospective

Implemented polish:

- Rebalanced typography hierarchy in the tooltip card:
  - Base card copy moved to `text-xs`.
  - Section labels (`Position`, `Projections`) remain visually primary.
  - Column headers (`Rate`, `Payment`) use lighter, smaller uppercase styling.
- Improved rhythm and readability:
  - More consistent vertical spacing between rows and sections.
  - Cleaner spacing between projection columns to reduce crowding.
- Preserved strict column alignment:
  - `Projections`, `Rate`, and `Payment` now sit on a single shared header row.
  - Value rows remain directly under their corresponding headers.

Validation outcome:

- `npx shadow-cljs compile test && node out/test.js`: pass (`1669` tests, `8697` assertions).

## Context and Orientation

Tooltip rendering is localized in `/hyperopen/src/hyperopen/views/active_asset_view.cljs` in `funding-tooltip-panel`. The panel has two sections (`Position`, `Projections`) and a 3-column projections grid.

The existing tests in `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs` assert content presence, so class-level visual tweaks can be made without changing assertions.

## Plan of Work

Refine tooltip card styling with a small set of design rules:

1. Use clear type scale: slightly smaller row text, restrained heading size, and consistent header emphasis.
2. Improve rhythm: add stable vertical spacing between section header row and data rows.
3. Preserve alignment: keep one shared grid for `Projections` + `Rate` + `Payment` headers and values.
4. Improve readability: tune color contrast and spacing so labels are secondary while numeric values remain primary.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/active_asset_view.cljs` (`funding-tooltip-panel` classes only).
2. Run `npx shadow-cljs compile test && node out/test.js`.
3. Update this plan’s progress/outcomes sections.

## Validation and Acceptance

Acceptance criteria:

1. Tooltip remains functionally identical (same sections and rows).
2. `Projections`, `Rate`, and `Payment` stay on one aligned header row.
3. Body text appears less crowded and more legible.
4. Test suite command passes.

## Idempotence and Recovery

Class-only changes are safe to re-run and low risk. If visual tuning regresses readability, revert only class tweaks in `funding-tooltip-panel` while preserving the row/grid structure.

## Artifacts and Notes

Primary implementation file:

- `/hyperopen/src/hyperopen/views/active_asset_view.cljs`

## Interfaces and Dependencies

No interface or dependency changes. This is a visual styling refinement only.

Plan revision note: 2026-03-01 19:07Z - Created mini design-focused ExecPlan before applying tooltip polish.
Plan revision note: 2026-03-01 19:13Z - Marked implementation complete and recorded final typography/alignment decisions with passing test evidence.
