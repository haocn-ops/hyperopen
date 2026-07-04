# Optimizer Setup Copy Diet: De-duplicate Summaries, Helper Text at the Point of Action

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` are maintained in accordance with `/hyperopen/docs/PLANS.md`.

Durable context: direct user request (product owner + designer Q&A, 2026-07-04) — the
optimizer setup screen's middle column is copy-heavy on first landing; duplicated
summaries and misplaced explanatory prose compete with the three real actions
(pick goal, set exposure, run).

## Purpose / Big Picture

The setup route's middle column repeats the same facts in up to four places (goal
card subtitle, section-header trailing summary, exposure header, right-rail
Scenario contract) and buries the one instruction users actually need ("you can
drag the dot") in a paragraph *below* the chart. After this change the middle
column is an editing surface: each section header names what the section is for,
duplicated live summaries render only when a panel is collapsed, direct-manipulation
helper text sits above the pad it explains, and the right-rail Scenario contract
stays the single canonical exact summary. No behavior, dispatch, or data-role
contracts change except where noted.

## Progress

- [x] (2026-07-04) Trailing header summaries (goal one-liner, exposure numbers line) hidden while their disclosure panel is open, via a shared `optimizer-section-trailing` class + CSS rule; still shown collapsed.
- [x] (2026-07-04) Minimum risk card: subtitle tightened to "Lowest volatility · no return forecast needed"; "Recommended for first runs" kicker removed (card order + default selection carry the recommendation).
- [x] (2026-07-04) Holdings-seeded-constraints note moved from the Minimum risk goal card into the Portfolio exposure panel (same `data-role`, shorter copy) — the holdings import seeded the constraints, not the objective.
- [x] (2026-07-04) Portfolio exposure description shortened to "Set target leverage and net long/short bias."
- [x] (2026-07-04) Drag helper moved ABOVE the pad and shortened to "Drag the dot to set target exposure."; the long caption paragraph below the chart removed.
- [x] (2026-07-04) "Sent to solver" echo moved out of the primary controls column into the "Advanced solver limits" drawer (implementation detail, kept for auditability).
- [x] (2026-07-04) Current-portfolio verdict: on-policy state collapses to "Inside policy" (chip-length); off-policy sentences unchanged.
- [x] (2026-07-04) Tests updated (`setup_layout_test`), gates run: `npm run gates` (check + test + test:websocket).

## Surprises & Discoveries

- Observation: the trailing header summaries were designed for the COLLAPSED
  disclosure state ("the panel reads without opening it") but both offending
  panels are open by default, so on first landing they read as pure duplication.
  Evidence: `disclosure-panel-open` in `setup_controls.cljs`; code comments in
  `setup_objective_controls.cljs` and `setup_constraint_controls.cljs`.
  Resolution: hide-when-open CSS keeps the collapsed-state value without the
  first-landing noise — better than deleting the summaries.
- Observation: view tests assert copy strings from the hiccup tree
  (`collect-strings`), so CSS-hidden strings still assert as present; only
  actually-changed copy required test edits.

## Decision Log

- Decision: hide trailing summaries with `details[open] > summary .optimizer-section-trailing { display: none }` instead of removing them.
  Rationale: preserves the documented collapsed-state scannability while removing first-landing duplication; zero test churn for those strings.
  Date/Author: 2026-07-04 / Claude
- Decision: drop the "Recommended for first runs" kicker entirely.
  Rationale: the recommendation is already carried by card order and default selection; the designer review called the line out as noise. The Max Sharpe kicker stays because it carries live view-provenance counts ("1 your view · 1 implied") — real data, not boilerplate.
  Date/Author: 2026-07-04 / Claude
- Decision: keep the Max Sharpe "sensitive to noisy return estimates" caveat.
  Rationale: honesty requirement from the 2026-06-19 objective-picker review outranks the copy diet; it is one clause, not a paragraph.
  Date/Author: 2026-07-04 / Claude
- Decision: keep `data-role "portfolio-optimizer-preset-holdings-constraints-note"` on the relocated note.
  Rationale: the role names the fact (holdings seeded the constraints), not the location; keeping it avoids breaking any external selector.
  Date/Author: 2026-07-04 / Claude
- Decision: leave the right-rail Scenario contract untouched.
  Rationale: it is already the canonical exact summary the designer asked for; the fix is removing the middle-column repeats, not editing the rail.
  Date/Author: 2026-07-04 / Claude

## Outcomes & Retrospective

The middle column now reads: Optimization goal (two cards, one line each) →
Portfolio exposure (one-line purpose, drag hint above the pad, live readout
below it) → grouped guards → Run. Exact numbers appear once while editing (the
readout + advanced drawer) and once as the canonical contract (right rail).
All required gates pass; view tests pin the new copy and the note's new home.

## Context and Orientation

- `/hyperopen/src/hyperopen/views/portfolio/optimize/setup_objective_controls.cljs`: goal cards, header one-liner, (formerly) the holdings note.
- `/hyperopen/src/hyperopen/views/portfolio/optimize/setup_constraint_controls.cljs`: Portfolio exposure panel, description, advanced drawer (now hosts the solver echo), relocated holdings note.
- `/hyperopen/src/hyperopen/views/portfolio/optimize/setup_exposure_map.cljs`: pad, drag hint, readout, on-policy verdict, `solver-echo` (public, rendered by the drawer).
- `/hyperopen/src/hyperopen/views/portfolio/optimize/setup_controls.cljs`: `section-heading` trailing span carries `optimizer-section-trailing`.
- `/hyperopen/src/styles/surfaces/optimizer/setup.css`: hide-when-open rule.
- `/hyperopen/test/hyperopen/views/portfolio/optimize/setup_layout_test.cljs`: copy + placement assertions.

## Validation

- `npm run gates` (runs `npm run check`, `npm test`, `npm run test:websocket`) — all PASS.
- Acceptance: first-landing middle column shows no duplicated gross/net/cap summary in an open Portfolio exposure header; drag hint precedes the pad; "Sent to solver" lives under Advanced solver limits; holdings-seeded note renders inside Portfolio exposure only.
