# Optimizer Review & Execute CTA Prominence

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

On the scenario Recommendation tab the "Review & execute" verdict-bar CTA is
the one control that moves the user *forward* into execution; everything else
on the page merely adjusts the scenario. In practice the page is dense enough
that users can miss it: the CTA currently renders as a quiet two-line card
(small label + explanatory subtext) that visually blends with the KPI and
context cards around it.

After this change the verdict-bar CTA:

1. Drops its always-visible subtext and renders the label "Review & execute"
   larger and bolder, so the button reads as a button, not another info card.
2. Moves the explanatory sentence into a delayed hover/focus tooltip
   (~0.8s delay, standard hover-intent timing) so the guidance is still
   discoverable without adding standing visual noise.
3. Gains a continuously animated "snake" border — a thin bright arc sweeping
   around the button's perimeter — marking it as the unique forward-motion
   action. The animation only runs in the actionable state (never on the muted
   "Already at target" variant) and is disabled under
   `prefers-reduced-motion: reduce`, falling back to the static accent border.

Design provenance: direct user request on 2026-07-13 (larger label, subtext as
delayed tooltip, animated marching border). The header
`portfolio-optimizer-scenario-review-rebalance` button is deliberately left
unchanged: two simultaneously animated CTAs would dilute the "single primary
action" signal the page already encodes.

A developer can see it working by running any scenario to a solved result and
opening the Recommendation tab (`/portfolio/optimize/draft`), or via the
recommendation verdict scene in the ui-workbench.

## Context References

- `src/hyperopen/views/portfolio/optimize/results_summary.cljs` — `verdict-cta`.
- `src/styles/surfaces/optimizer/results.css` — existing `.optimizer-verdict-cta`
  block and the motion conventions (shimmer keyframes + reduced-motion guard).
- `test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs` —
  pins CTA presence, label strings, and click actions.
- `docs/agent-guides/trading-ui-policy.md`, `docs/DESIGN.md`.

## Progress

- [x] Redesign `verdict-cta` markup: large label, tooltip node, arrow; `--live`
      modifier class only when actionable.
- [x] CSS: conic-gradient sweep border (`@property` angle), delayed tooltip,
      reduced-motion fallback, in `results.css`.
- [x] Unit-test updates/additions pinning the new structure (tooltip present,
      subtext not a standing sibling, `--live` class gated on actionability).
- [x] Gates: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] Browser QA on the Recommendation tab (animation, tooltip delay, muted
      state, reduced-motion).
- [ ] Owner sign-off on sweep speed/brightness after living with it; tune if it
      reads as distracting on the live book.

## Validation

- Unit: `scenario_detail_view_test.cljs` pins (a) the actionable CTA carries
  `optimizer-verdict-cta--live`, (b) the muted "Already at target" variant does
  NOT, (c) the guidance sentence renders inside the
  `optimizer-verdict-cta-tip` node, and (d) the existing label/click-action
  contracts still hold.
- Gates: `npm run check`, `npm test`, `npm run test:websocket` must pass
  (run via `npm run gates`).
- Acceptance (browser QA on the Recommendation tab of a solved scenario):
  the CTA shows only the bold "Review & execute" label with a bright arc
  continuously sweeping its border; hovering ~0.8s reveals the explanatory
  tooltip (instant on keyboard focus); with `prefers-reduced-motion: reduce`
  the border is a static amber ring; the zero-trade state is muted, unlabeled
  as a forward action, and unanimated.

## Surprises & Discoveries

- No existing test pins the CTA subtext string, so moving it into a tooltip is
  test-compatible; label/click-action pins all continue to hold.

## Decision Log

- Native `title` tooltips rejected: delay/styling are not controllable and they
  never show for keyboard focus. A CSS tooltip with `transition-delay` gives
  the requested 1–2s hover-intent pattern plus `:focus-visible` support.
- Tooltip text stays in the DOM (visually hidden until hover) rather than
  `aria-hidden`, so screen readers keep the explanatory sentence the sighted
  subtext used to provide.
- Snake border implemented as a rotating conic-gradient on a masked `::before`
  ring (mask-composite exclude), animating a registered `@property` angle —
  cheap (compositor-friendly), no extra DOM, and it hugs the border radius.
- Animation gated off for the "Already at target" variant and under
  `prefers-reduced-motion`; a no-op button must not beg to be clicked.
- 2026-07-13 follow-up (owner request): label changed from "Review & execute"
  to "Review N trades" — clicking stages a plan, it does not execute, so
  "execute" over-promised; the count ties the button to the verdict's
  "N trades get you there." Fallback "Review trades" when the sendable count is
  zero/unknown (all-blocked plans still get a path in). Tooltip now states
  nothing is sent until the user arms and confirms on the Execution tab.
- 2026-07-13 follow-up (owner request): the header
  `portfolio-optimizer-scenario-review-rebalance` button was REMOVED — pure
  duplicate of the verdict-bar CTA that cluttered the toolbar and diluted the
  single-primary-action signal. Tests now pin its absence. The setup-workspace
  "Review & execute" CTAs (`setup_actions`/`setup_context`) were relabelled
  "Review trades" (count not available on that surface) for phrase consistency.

## Outcomes & Retrospective

- Shipped 2026-07-13. Verdict CTA now reads as the page's single forward
  action: 0.8125rem bold label, sweeping accent border, guidance in a delayed
  tooltip. All gates green; browser QA confirmed sweep, tooltip delay, muted
  no-trades state, and reduced-motion fallback.
