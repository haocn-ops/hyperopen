# Refresh Portfolio Chart Hover Tooltip Visual Style

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, hovering the `/portfolio` chart will show a tooltip panel styled like the provided Hyperliquid reference: a rounded elevated card with a muted timestamp row and a second row that separates metric label from highlighted value. Users will get the same deterministic hover behavior already implemented, but with clearer hierarchy and visual readability.

A user can verify this by opening `/portfolio`, hovering chart points in `Account Value`, `PNL`, and `Returns`, and confirming the tooltip appears as a two-line card where the metric value is visually emphasized.

## Progress

- [x] (2026-02-28 18:14Z) Reviewed required planning and UI policy docs: `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md`.
- [x] (2026-02-28 18:14Z) Audited existing portfolio hover tooltip implementation and tests in `/hyperopen/src/hyperopen/views/portfolio_view.cljs` and `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`.
- [x] (2026-02-28 18:14Z) Authored this ExecPlan in active plans.
- [x] (2026-02-28 18:15Z) Implemented tooltip content model updates (timestamp line + metric label/value line) in `/hyperopen/src/hyperopen/views/portfolio_view.cljs`.
- [x] (2026-02-28 18:15Z) Implemented tooltip visual refresh classes/styles in `/hyperopen/src/hyperopen/views/portfolio_view.cljs` with rounded panel, border, gradient background, and sign-aware value accent classes.
- [x] (2026-02-28 18:15Z) Updated view regression coverage in `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs` for structured tooltip content and panel class contract.
- [x] (2026-02-28 18:15Z) Ran required validation gates with passing results: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-02-28 18:15Z) Updated this plan’s living sections and prepared move to `/hyperopen/docs/exec-plans/completed/`.
- [x] (2026-02-28 18:17Z) Re-ran validation after plan move to confirm final-tree status (`npm run check`, `npm test`, `npm run test:websocket`) remained fully green.

## Surprises & Discoveries

- Observation: The current portfolio tooltip is rendered as a single string (`"<timestamp>: <value>"`) with inline black pill styles, so visual parity with the reference requires a small content-structure refactor in addition to style classes.
  Evidence: `/hyperopen/src/hyperopen/views/portfolio_view.cljs` `chart-tooltip-label` and tooltip node rendering under `:data-role "portfolio-chart-hover-tooltip"`.

- Observation: Existing tests assert tooltip text by concatenating all strings, which is resilient to nested tooltip markup and can be adapted without introducing DOM coupling.
  Evidence: `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs` `collect-strings` usage in `portfolio-view-chart-hover-overlay-renders-date-and-time-tooltip-variants-test`.

- Observation: Asserting exact concatenated tooltip text became brittle after splitting the tooltip into multiple rows, so set-membership assertions on individual text tokens provide stronger structural resilience.
  Evidence: Updated test now validates timestamp token, metric label token, and value token independently from `collect-strings` output in `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`.

## Decision Log

- Decision: Keep hover state/action/view-model behavior unchanged and scope this task to tooltip presentation/content decomposition inside the view.
  Rationale: The user request is explicitly a visual style change; preserving action/runtime semantics minimizes regression risk.
  Date/Author: 2026-02-28 / Codex

- Decision: Keep existing date/time token rules by range (time in `24H`, date in longer ranges) unless implementation findings require otherwise.
  Rationale: This preserves prior parity behavior while still enabling the requested visual style overhaul.
  Date/Author: 2026-02-28 / Codex

- Decision: Drive tooltip value accent color directly from selected chart tab and numeric sign (`PNL`/`Returns` sign-aware, `Account Value` fixed amber).
  Rationale: This mirrors the reference visual hierarchy while keeping formatting logic local to the view and avoiding VM contract changes.
  Date/Author: 2026-02-28 / Codex

## Outcomes & Retrospective

Implemented and validated end-to-end.

Outcome summary:

- Portfolio chart tooltip now renders as a two-row panel with a muted timestamp row and a metric/value row.
- Tooltip content uses a structured model (`:timestamp`, `:metric-label`, `:metric-value`, `:value-classes`) instead of a single concatenated label.
- Value styling now highlights `Account Value` in amber and uses sign-aware green/red accents for `PNL` and `Returns`.
- Hover lifecycle, marker rendering, placement, clamping, and left/right flip logic remained unchanged.
- View tests now validate structured tooltip tokens and key style-class contract entries.

Validation evidence:

- `npm run check` passed.
- `npm test` passed (`Ran 1541 tests containing 7967 assertions. 0 failures, 0 errors.`).
- `npm run test:websocket` passed (`Ran 154 tests containing 710 assertions. 0 failures, 0 errors.`).

## Context and Orientation

Portfolio chart hover UI is rendered in `/hyperopen/src/hyperopen/views/portfolio_view.cljs` inside `chart-card`. The chart hover state comes from `chart` view-model data (specifically `:hover` with `:active?` and `:point`) and is already deterministic and action-driven. This task should not modify runtime contracts, action wiring, or state ownership.

In this plan, “tooltip content model” means splitting display text into explicit pieces used by render markup: timestamp string, metric label string, and formatted value string.

The chart plot area hover line and tooltip placement are already implemented and should stay intact:

- Hover x-position uses normalized `:x-ratio` from the hovered point.
- Tooltip y-position clamps around `:y-ratio`.
- Tooltip flips left when pointer is near the right side.

Tests for chart hover rendering live in `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`.

## Plan of Work

### Milestone 1: Introduce Tooltip Content Pieces for Structured Rendering

Refactor tooltip formatting helpers in `/hyperopen/src/hyperopen/views/portfolio_view.cljs` so the chart card can render a timestamp row and a metric/value row instead of one concatenated text string. Keep formatting rules for value and summary-range timestamp selection compatible with current behavior.

### Milestone 2: Apply New Tooltip Visual Style in Chart View

Replace the current small black pill tooltip markup with a rounded elevated panel style close to the reference screenshots. Use deterministic classes and explicit style keys (keyword keys only), including border, translucent gradient background, shadow, and spacing tuned for readability. Keep tooltip non-interactive (`pointer-events-none`) and preserve existing placement transform logic.

### Milestone 3: Extend Tooltip Rendering Regression Coverage

Update hover overlay test assertions in `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs` to verify:

- Tooltip still appears with hover line.
- Timestamp text remains correct for `month` and `day` ranges.
- Metric label and formatted value are present for selected tab.
- Tooltip class contract includes the new panel styling tokens.

### Milestone 4: Validate and Finalize Plan Lifecycle

Run required gates from `/hyperopen`, update the plan with execution evidence and final decisions, then move the plan from `active` to `completed`.

## Concrete Steps

1. Edit `/hyperopen/src/hyperopen/views/portfolio_view.cljs` to replace `chart-tooltip-label` with a structured helper returning timestamp/label/value metadata.
2. Edit the tooltip render block in `chart-card` to output two rows with updated classes/styles and value accent classes by chart tab/value sign.
3. Edit `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs` to assert structured tooltip text and key style classes.
4. Run from `/Users//projects/hyperopen`:

       npm run check
       npm test
       npm run test:websocket

5. Update this plan’s living sections and move it to `/hyperopen/docs/exec-plans/completed/`.

## Validation and Acceptance

This work is accepted when:

1. Hovering the portfolio chart still shows and hides marker + tooltip deterministically.
2. Tooltip appears as a rounded card with a muted timestamp row and separate metric/value row.
3. Tooltip value is color-accented by metric context (for example `PNL` sign-aware accent).
4. Existing hover behaviors (positioning, clamping, left/right flip) remain intact.
5. Required validation gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

Changes are source-level and safe to reapply. If style tuning regresses readability, keep structured tooltip content helper and roll back only class/style values in the tooltip render node while preserving tests for content structure.

No schema migrations or destructive operations are involved.

## Artifacts and Notes

Primary implementation target:

- `/hyperopen/src/hyperopen/views/portfolio_view.cljs`

Primary regression target:

- `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`

This change does not introduce new runtime dependencies.

Validation artifacts captured on 2026-02-28:

- `npm run check` (pass)
- `npm test` (pass)
- `npm run test:websocket` (pass)

## Interfaces and Dependencies

Interfaces expected to remain stable:

- `hyperopen.views.portfolio-view/portfolio-view` entrypoint.
- `:chart :hover` shape from `hyperopen.views.portfolio.vm/portfolio-vm`.
- Existing hover actions and contracts in `hyperopen.portfolio.actions` and runtime registries.

No new external libraries are required.

Plan revision note: 2026-02-28 18:14Z - Initial ExecPlan created for portfolio chart hover tooltip visual refresh request.
Plan revision note: 2026-02-28 18:15Z - Marked implementation complete, captured validation evidence, and finalized plan for move to completed plans.
Plan revision note: 2026-02-28 18:17Z - Added post-move validation rerun evidence for final workspace state.
