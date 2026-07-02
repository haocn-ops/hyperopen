# Optimizer Constraints Panel: Compact Layout, Fixed-Scale Zoom, Sticky Run Bar

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` are maintained in accordance with `/hyperopen/docs/PLANS.md`.

Context: direct user request (2026-07-01) after using the redesigned Constraints panel on the optimizer setup route. Follows the completed exposure-map ExecPlan (`docs/exec-plans/completed/`), the parent ExecPlan for the exposure-map constraints redesign.

## Purpose / Big Picture

The 2D exposure-map Positioning control shipped with four usability defects the user hit immediately:

1. **The open Constraints panel is taller than a screen.** The pad renders at `width: 100%; aspect-ratio: 1/1` inside the wide center policy pane, so the pad alone is ~700–900px tall, and every sub-control (memory row, band sliders, echo, preview, presets) stacks vertically below it.
2. **Gross-leverage feedback is illegible.** The y-axis tick labels render at 0.5rem (8px), there is no prominent live value, and the tick labels do not align with the drawn gridlines (labels at top/mid/bottom, gridlines at 33%/67%).
3. **Dragging is janky and the axis rescales mid-gesture.** The axis scale is re-derived from the policy on every render with 1.12 headroom; dragging to the top edge grows the axis, which remaps the pointer to a larger value, which grows the axis again — a feedback loop that ratchets 3×→5×→10×→20×→40×→… ("jumps from 5x to 200x") while the pointer is held at the edge.
4. **The Run bar falls below the fold** when the Constraints disclosure opens, because it is the last element of a very tall center column.

After this change: the pad is bounded (~336px) with the controls beside it in a second column; a large live readout shows the exact gross/net targets; the axis scale is explicit fixed state changed only by dedicated zoom buttons (never by dragging — drag values clamp to the visible scale, so the mapping under the pointer can never change mid-gesture); and the Run bar is sticky at the viewport bottom while the policy pane is in view.

## Progress

- [x] (2026-07-01) Domain: replaced headroom-adaptive `axis-scale` with paired fixed zoom levels, `fit-level`, `render-axis`, and a band-aware clamp in `point->targets`.
- [x] (2026-07-01) State/actions: `:exposure-zoom-level` in optimizer UI state, `set-portfolio-optimizer-exposure-zoom-level` action registered across the contract surface; presets/reset/apply-default clear the stored zoom.
- [x] (2026-07-01) View: two-column exposure-map layout, big live readout, zoom buttons, gridline-aligned ticks, extent-labeled x-axis ends.
- [x] (2026-07-01) Layout: Risk guards + Rebalance behavior side by side; Run bar as sticky last child of the policy pane.
- [x] (2026-07-01) Tests: exposure-policy/vm/actions unit tests, setup-layout test, defaults test, workbench scenes, Playwright exposure-map spec; `npm test` green (4943 tests, 0 failures).
- [x] (2026-07-01) Review fixes: adversarial multi-agent review found the derive-only scale still shrank mid-drag and the band clamp was defeatable via advanced raw fields; added interaction pinning (drag/band dispatches bake + store the render level) and net-reach ≤ gross-reach.
- [x] (2026-07-01) Validation: `npm run check` (exit 0, 0 warnings), `npm test` (4944 tests, 0 failures), `npm run test:websocket` (546 tests, 0 failures), Playwright exposure-map spec 6/6 + the run-bar placement regression, live browser QA (pad 305px, sticky run bar pinned, readout tracks clicks, fixed scale holds through 25 synthetic edge pointermoves).

## Surprises & Discoveries

- Observation: holding the pointer at the pad's top edge grows gross leverage without bound.
  Evidence: `axis-scale` applies `axis-headroom 1.12` to `gross-target`, so any target equal to the current axis max quantizes to the next step; the drag dispatch bakes the new scale, so the same pointer position maps to a bigger target on the next `pointermove`.
- Observation: the first fixed-zoom implementation still rescaled mid-drag — downward. Adversarial review (multi-agent, independently confirmed) found that `render-axis` re-fits from the live policy each render, so dragging the handle down from a high-gross policy cascaded 10×→5×→3× under the pointer; narrowing a band slider jittered the scale the same way.
  Evidence: `level (max fit (or stored fit))` with stored `nil` (the default) tracks `fit-level` downward on every policy write.
- Observation: the `target + band ≤ axis max` drag clamp could be defeated through the advanced raw fields, where the gross band is not capped at `max-band`: the `gross ≥ |net|` lift ran after the clamp and was itself unclamped.
  Evidence: gross-min 0 / gross-max 3 gives gross-band 1.5; at the floor level a full-right drag lifted gross-target to |net| = 2.0, so 2.0 + 1.5 > 3.0 forced a mid-drag re-fit.
- Fixes: interactions PIN the zoom level they were performed at (the view bakes the current render level into drag/band dispatches; the handler stores it, and the view model only ever widens past it), and net reach is additionally clamped to the gross reach so the lift can never push the box out of view.

## Decision Log

- Decision: the pad's axis scale becomes explicit UI state changed only by zoom buttons (or cleared by preset/reset/profile actions); dragging clamps to the visible scale and can never rescale the pad.
  Rationale: a control surface that remaps its own coordinate system mid-gesture violates natural mapping (Design of Everyday Things) and quantitative honesty ("use consistent axes for exposure maps" — optimizer agent KB §4.8). Reachability beyond the current scale is preserved through the zoom control instead of accidental runaway growth.
  Date/Author: 2026-07-01 / Claude
- Decision: drag targets clamp so the whole band box stays inside the view (`target + band ≤ axis max` per axis).
  Rationale: this is the only fixpoint that prevents a one-step rescale when dragging to the edge with a positive band, and it keeps the solver-bound region fully visible at all times.
  Date/Author: 2026-07-01 / Claude
- Decision: zoom levels are paired per axis — gross [3 5 10 20 40] with net [2 3 5 10 20] — and a single control steps both.
  Rationale: independent per-axis zoom doubles the control count for marginal value; paired steps keep the pad's proportions predictable. Raw values beyond the largest level (advanced fields) fall back to a computed overflow scale with zoom disabled.
  Date/Author: 2026-07-01 / Claude
- Decision: the Run bar stays in the center column but becomes `position: sticky; bottom: 0` as the last direct child of the policy pane.
  Rationale: the KB wants the run action never far from readiness/summary and never below the fold; sticky keeps the recent setup-IA decision (run bar in center) while making it always visible. The bar already carries its own status pill and solid background, so pinning it costs nothing.
  Date/Author: 2026-07-01 / Claude
- Decision: `:exposure-zoom-level` is UI-only state (optimizer UI state, not the draft) and is not persisted.
  Rationale: KB §6.2 layer discipline — it changes nothing the solver sees, so it must not touch draft/request/worker layers or trip draft dirty tracking.
  Date/Author: 2026-07-01 / Claude
- Decision: drag and band interactions pin the stored zoom to the render level they were performed at (grow-only ratchet); the scale shrinks only via explicit zoom-in or a preset/profile/reset re-fit.
  Rationale: the adversarial review proved a derive-only scale still shrinks under the pointer when the edited policy re-fits smaller. Pinning through the same baked-dispatch pattern the pointer math already uses keeps the invariant "the scale never changes during a pointer interaction" without any impure drag-in-progress tracking.
  Date/Author: 2026-07-01 / Claude

## Context and Orientation

- `src/hyperopen/portfolio/optimizer/domain/exposure_policy.cljs`: pure policy↔constraints conversion, pad pointer math, axis scales. All numeric changes land here first.
- `src/hyperopen/portfolio/optimizer/application/view_model/exposure.cljs`: builds the display model; gains stored-zoom awareness and zoom-button levels.
- `src/hyperopen/portfolio/optimizer/actions/exposure.cljs`: pure handlers; gains the zoom-level action and zoom-clearing on preset/reset/profile-apply.
- `src/hyperopen/views/portfolio/optimize/setup_exposure_map.cljs` + `src/styles/surfaces/optimizer/setup.css`: the rendered control and its layout.
- `src/hyperopen/views/portfolio/optimize/setup_sections.cljs` / `setup_constraint_controls.cljs`: policy-pane structure and constraint grouping.
- Contract surface for the new action: `runtime_catalog.cljs`, `schema/runtime_registration/portfolio.cljs`, `schema/contracts/action_args.cljs`, `contracts/paths.cljs` (+ path index), `defaults.cljs`.

## Plan of Work

First, rework the domain axis model (fixed paired levels, fit without headroom, band-aware drag clamp) with unit tests. Second, add the UI-state path and zoom action across the registration/contract surface, and clear stored zoom in the preset/reset/profile actions. Third, restructure the exposure-map view into a bounded pad column plus controls column with the live readout and zoom buttons, and align gridlines with legible tick labels. Fourth, compact the Constraints panel (guards/rebalance side by side) and make the Run bar sticky. Fifth, update unit tests, workbench scenes, and the Playwright spec, then run the full gates and browser QA.

## Validation

- `npm run gates` (check, test, test:websocket) passes.
- `npx playwright test optimizer-exposure-map.spec.mjs` passes, including new assertions: pad height bounded, zoom control steps the axis, run bar visible at 1440×900 with the Constraints panel open.
- Live browser QA: dragging to the pad's top edge never rescales the axis; the readout tracks the drag; presets refit the view; the Run bar stays visible while editing constraints.
- Acceptance: with the Constraints panel open at 1440×900, the Run action and the whole Positioning control are visible without scrolling past one screen; the gross-leverage value is readable at a glance.

## Outcomes & Retrospective

All four user complaints are resolved: the open Constraints panel fits one screen (bounded ~305px pad beside its controls, Risk guards + Rebalance side by side), the gross-leverage value reads at a glance (15px live readout under the pad, 10px gridline-aligned ticks), the pad scale is fixed and only the explicit zoom control changes it (dragging clamps and pins — the 5×→200× runaway is structurally impossible), and the Run bar is sticky at the viewport bottom so expanding any panel can never hide it.

Retrospective: the first "fixed scale" implementation only removed the *upward* re-fit; adversarial review caught the downward cascade and an advanced-fields bypass of the band clamp. The durable invariant needed all three mechanisms (band-aware clamp with net-reach ≤ gross-reach, interaction pinning of the render level, refit only on discontinuous policy changes). Also learned: Playwright drags against a cached `boundingBox()` silently miss after auto-scroll — re-capture per gesture.
