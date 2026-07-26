# Refine Trading Settings Brand Expression And Visual Identity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md`. The tracked work item for this plan is `hyperopen-gzpg`; the `bd` issue is historical local tracker context for this plan.

## Purpose / Big Picture

The latest Trading Settings refactor made the panel cleaner and flatter, but it still reads more like a well-executed settings popover than a distinctly Hyperopen trading surface. This follow-up pass exists to push the component from “aligned enough” to “recognizably ours” without changing its behavior or bloating the UI.

After this change, users should still see the same settings, helper copy, and toggle behavior, but the panel should express the product’s brand more clearly in three ways: accent color appears in more intentional, system-level places; the typography feels more proprietary and technical; and the composition feels less like separated preference groups and more like a continuous instrument panel for trading controls.

## Progress

- [x] (2026-03-30 14:01Z) Created and claimed `bd` issue `hyperopen-gzpg` for the Trading Settings brand-expression refinement pass.
- [x] (2026-03-30 14:01Z) Reviewed the current post-refactor implementation in `/hyperopen/src/hyperopen/views/header/settings.cljs` and the prior completed ExecPlan in `/hyperopen/docs/exec-plans/completed/2026-03-30-trading-settings-brand-alignment-refactor.md`.
- [x] (2026-03-30 14:01Z) Authored this active ExecPlan with the three requested refinement tracks translated into concrete implementation and validation milestones.
- [x] (2026-03-30 14:12Z) Captured the fresh browser-review baseline and final evidence on `/trade` with `npm run qa:design-ui -- --targets trade-route --manage-local-app`, using the review artifacts to judge accent placement, typography voice, section continuity, and icon usefulness.
- [x] (2026-03-30 14:08Z) Refined accent placement in `/hyperopen/src/hyperopen/views/header/settings.cljs` by strengthening the shell top rule, adding accent-led section markers, accenting the inline confirmation treatment, and tying close-button hover more clearly to the panel’s trading accent.
- [x] (2026-03-30 14:08Z) Refined title, section-label, and inline action typography in `/hyperopen/src/hyperopen/views/header/settings.cljs` so the panel reads more technical and proprietary without changing user-facing copy.
- [x] (2026-03-30 14:08Z) Flattened section grouping one step further in `/hyperopen/src/hyperopen/views/header/settings.cljs` by collapsing section shells into one continuous plane separated by disciplined dividers and section markers.
- [x] (2026-03-30 14:08Z) Audited row icons and removed the redundant confirmation-row icons via `/hyperopen/src/hyperopen/views/header/vm.cljs`, keeping the remaining icons where they still improve scanning.
- [x] (2026-03-30 14:09Z) Updated deterministic tests in `/hyperopen/test/hyperopen/views/header_view_test.cljs` for the continuous-sheet section contract, accent marker, and icon-removal assertions.
- [x] (2026-03-30 14:09Z) Reused the existing smallest relevant Playwright seam because the browser-visible behavior contract did not change.
- [x] (2026-03-30 14:12Z) Ran required validation: `npm run check`, `npm test`, `npm run test:websocket`, `npx playwright test tools/playwright/test/trade-regressions.spec.mjs -g "trading settings confirmation toggles respond to visible switch clicks"`, and governed browser QA for the trade-route surface.
- [x] (2026-03-30 14:12Z) Updated this plan with the actual outcome and validation evidence in preparation for moving it out of `active/`.

## Surprises & Discoveries

- Observation: the current post-refactor component already solved the biggest structural problem, which was stacked card-within-card chrome. The next pass is therefore refinement work, not another broad shell rewrite.
  Evidence: `/hyperopen/src/hyperopen/views/header/settings.cljs` and `/hyperopen/docs/exec-plans/completed/2026-03-30-trading-settings-brand-alignment-refactor.md`.

- Observation: the current settings panel already has strong regression anchors through `data-role` hooks, deterministic view tests, a committed Playwright toggle seam, and governed browser-QA coverage on `/trade`.
  Evidence: `/hyperopen/test/hyperopen/views/header_view_test.cljs`, `/hyperopen/tools/playwright/test/trade-regressions.spec.mjs`, and `/hyperopen/tmp/browser-inspection/design-review-2026-03-30T13-47-28-982Z-9cb9f3bd/summary.json`.

- Observation: the remaining gap is mostly about composition and art direction rather than missing controls, copy, or behavior.
  Evidence: the current render path already preserves the intended settings list, helper copy, confirmation strip, and persisted toggle behavior.

- Observation: once the panel became more continuous and section-led, the confirmation-row icons were redundant and made that section feel busier than the rest of the sheet.
  Evidence: `/hyperopen/src/hyperopen/views/header/vm.cljs` now projects `nil` icon kinds for `Confirm open orders` and `Confirm close position`, while the updated tests assert those row icons are absent.

- Observation: the only real validation friction in this pass was tooling state, not product behavior. `npm run check` initially failed only because the touched view-test namespace exceeded the repo’s namespace-size exception by four lines, and governed browser QA initially failed only because an old `shadow-cljs` server was still bound to port `9630`.
  Evidence: the temporary `check` failure was `[size-exception-exceeded] test/hyperopen/views/header_view_test.cljs - namespace has 566 lines; exception allows at most 562`, and the temporary QA failure was `shadow-cljs already running in project on http://localhost:9630`.

## Decision Log

- Decision: scope this follow-up around exactly three refinement tracks: accent placement, typography voice, and flatter continuous grouping.
  Rationale: those are the specific remaining gaps called out in the design critique, and they can be executed without reopening broader product or behavior scope.
  Date/Author: 2026-03-30 / Codex

- Decision: preserve current settings behavior, copy, persistence, and accessibility unless a concrete visual fit issue requires a narrowly justified adjustment.
  Rationale: this pass is about stronger brand expression, not product-scope expansion or interaction redesign.
  Date/Author: 2026-03-30 / Codex

- Decision: prefer fewer, more deliberate accent moments rather than adding more color.
  Rationale: the panel should feel more branded, but Hyperopen’s app surfaces remain strongest when one accent system is used sparingly and with purpose.
  Date/Author: 2026-03-30 / Codex

- Decision: remove the confirmation-row icons while keeping the remaining section and control icons.
  Rationale: the confirmation rows already live inside a clearly labeled `Confirmations` section, so their icons no longer improved scanning after the panel shifted toward one continuous sheet.
  Date/Author: 2026-03-30 / Codex

- Decision: reuse the existing committed Playwright toggle regression instead of broadening browser coverage in this pass.
  Rationale: this refinement changed composition and visual identity, not the browser-visible behavior contract; governed browser QA remained the correct tool for the stronger visual acceptance bar.
  Date/Author: 2026-03-30 / Codex

## Outcomes & Retrospective

This refinement pass landed in `/hyperopen/src/hyperopen/views/header/settings.cljs`, `/hyperopen/src/hyperopen/views/header/vm.cljs`, and `/hyperopen/test/hyperopen/views/header_view_test.cljs`. The panel now reads more clearly as a Hyperopen control surface: accent is concentrated into stronger structural moments, the typography is more technical and deliberate, the composition is flatter and more continuous, and the `Confirmations` rows no longer carry redundant icons.

The result reduced perceived genericness without increasing visual clutter. The previous pass made the component clean; this pass made it feel more system-owned. Complexity stayed roughly flat in code terms but dropped perceptually in the UI because the panel now depends less on separate blocks and more on one coherent rhythm.

Validation results:

- `npm test` passed: 2898 tests, 15548 assertions, 0 failures.
- `npm run test:websocket` passed: 429 tests, 2434 assertions, 0 failures.
- `npx playwright test tools/playwright/test/trade-regressions.spec.mjs -g "trading settings confirmation toggles respond to visible switch clicks"` passed.
- `npm run check` passed.
- `npm run qa:design-ui -- --targets trade-route --manage-local-app` passed with `reviewOutcome: "PASS"` and artifact root `/hyperopen/tmp/browser-inspection/design-review-2026-03-30T14-12-03-720Z-9269c269`.

## Context and Orientation

The current Trading Settings implementation lives primarily in `/hyperopen/src/hyperopen/views/header/settings.cljs`. That file renders the trigger, desktop popover, mobile sheet, shell header, section wrappers, rows, icon placement, toggle styling, and the storage-mode confirmation strip. This is the main file that will change.

The content and action model live in `/hyperopen/src/hyperopen/views/header/vm.cljs`. That file decides which rows exist, their titles and helper copy, the checked state for each setting, and the action vectors that fire on change. Do not treat it as a styling sandbox. Change it only if a copy adjustment or a deliberate icon/removal decision requires it.

The deterministic rendering contract lives in `/hyperopen/test/hyperopen/views/header_view_test.cljs`. That test suite already pins shell width, shell/background tokens, section ordering, row presence, and key motion hooks. It must be updated when class tokens or structural grouping change.

Committed browser coverage for Trading Settings behavior already exists in `/hyperopen/tools/playwright/test/trade-regressions.spec.mjs`. That test opens the header settings panel and verifies that the confirmation toggles change runtime state and persisted local storage. Unless this follow-up changes behavior rather than styling, that existing seam should remain sufficient.

Governed browser QA for this surface runs through `npm run qa:design-ui -- --targets trade-route --manage-local-app`. Because this work is UI-facing, that review is required again.

## Design Thesis

Visual thesis: make the panel feel like a branded control surface, not a neutral preference popover, by concentrating one accent system, sharpening the typographic voice, and turning grouped blocks into a continuous technical rhythm.

Content plan: keep the current title, sections, row titles, helper copy, and footer note, but let hierarchy and tone come more from type rhythm, divider cadence, and accent placement than from isolated blocks.

Interaction thesis: preserve the current anchored open and mobile-sheet motion, but make static composition do more of the identity work so the panel feels deliberate even at rest.

## Plan of Work

Start with a visual audit of the current panel on `/trade` at the required review widths. The audit must explicitly answer four questions: where accent currently appears and where it does not; whether the title and section labels feel neutral or proprietary; whether the eye reads the panel as separate groups or as one continuous sheet; and whether each row icon still improves scan speed. This step should produce before-state screenshots and concrete design notes under `/hyperopen/tmp/browser-inspection/`.

Then refine accent placement in `/hyperopen/src/hyperopen/views/header/settings.cljs`. The goal is not “more teal everywhere.” The goal is to place the trading accent in a few system-level moments that make the panel feel more branded: for example the top rule, selected emphasis, toggle-on state, or another single high-signal surface that unifies the panel. Avoid introducing a second accent family, decorative gradients that read like marketing chrome, or a color treatment that competes with the main trading workspace beneath the popover.

Next refine the typography. Keep the existing utility copy, but adjust title weight, section-label spacing, case treatment, tracking, and row-title rhythm so the panel feels more technical and product-native. The section labels should scan like system markers rather than generic settings headings. The row titles should stay readable, but their rhythm should feel more precise and less “standard preference panel.” Do not drift into aspirational or homepage-style language.

After typography, flatten the grouping one step further. The current panel still uses distinct section blocks. This pass should explore a quieter structure where the eye reads one continuous control sheet with disciplined breaks, not a stack of separate cards. The likely implementation path is to reduce section-shell distinction, depend more on spacing and dividers, and keep one dominant visual plane. If an individual icon no longer helps in that flatter composition, remove it instead of compensating with more chrome.

Finally, update the deterministic tests for any changed class contract, run the existing smallest meaningful Playwright seam unless behavior changed enough to justify more, and rerun governed browser QA on `trade-route`. If implementation discovers a visual or structural gap that merits a follow-up instead of a larger current change, file a `bd` issue and record it here rather than leaving open TODOs in prose.

## Concrete Steps

From `/hyperopen`:

1. Capture a new baseline:
   `npm run qa:design-ui -- --targets trade-route --manage-local-app`

   Use the resulting artifacts to write explicit notes on accent placement, typography voice, section continuity, and icon usefulness.

2. Edit the primary render seam:
   `/hyperopen/src/hyperopen/views/header/settings.cljs`

   Implement:
   - more intentional accent placement
   - stronger title and section-label typography
   - flatter continuous grouping
   - icon removals or simplifications only where justified by scanability

3. Edit supporting seams only if needed:
   `/hyperopen/src/hyperopen/views/header/vm.cljs`
   `/hyperopen/src/hyperopen/views/header_view.cljs`

4. Update deterministic test coverage:
   `/hyperopen/test/hyperopen/views/header_view_test.cljs`

5. Reuse the current smallest relevant Playwright seam unless the follow-up introduces a new stable behavior contract:
   `npx playwright test tools/playwright/test/trade-regressions.spec.mjs -g "trading settings confirmation toggles respond to visible switch clicks"`

6. Run required validation:
   `npm run check`
   `npm test`
   `npm run test:websocket`
   `npx playwright test tools/playwright/test/trade-regressions.spec.mjs -g "trading settings confirmation toggles respond to visible switch clicks"`
   `npm run qa:design-ui -- --targets trade-route --manage-local-app`

7. Update this plan with actual outputs, artifact paths, and final outcomes before closing `hyperopen-gzpg` and moving the plan to `/hyperopen/docs/exec-plans/completed/`.

## Validation and Acceptance

The follow-up pass is accepted only if all of the following are true.

The panel still exposes the same settings, helper copy, toggle semantics, persistence, and dismissal behavior as the current implementation.

Accent color feels more intentional and system-level rather than isolated to toggle state. In practice, the panel should have one coherent accent story, not scattered colored moments.

The title and section-label typography feel more distinctly Hyperopen and more technical without becoming louder, more verbose, or less readable.

The composition feels less like stacked settings groups and more like one continuous control surface while still allowing a user to scan sections immediately.

Any icon removed or retained is a deliberate scanning decision, not inertia from the previous layout.

Deterministic tests pass, required gates pass, the smallest relevant Playwright regression passes, and governed browser QA on `trade-route` explicitly accounts for the refined panel at the required viewports.

## Idempotence and Recovery

This pass should be implemented through safe visual iterations. If a change makes the panel feel more generic, louder, or harder to scan, back out only the styling/test edits for that attempt and keep the rest of the narrower improvements that already hold up.

Browser QA is safe to rerun. If a governed browser-QA failure is caused by unrelated startup or route issues, record that honestly in this plan and in `bd`; do not silently weaken the acceptance bar.

## Artifacts and Notes

Expected artifacts for this work:

- governed browser-review output under `/hyperopen/tmp/browser-inspection/**`
- any multi-agent planning artifacts under `/hyperopen/tmp/multi-agent/hyperopen-gzpg/**`

Recommended exact-agent roster for implementation:

- `spec_writer`: own this ExecPlan if scope shifts during execution.
- `ui_designer`: high-value for making the accent/typography/grouping tradeoffs explicit before implementation locks them in.
- `browser_debugger`: capture before/after evidence and run governed browser QA on `/trade`.
- `worker`: implement the render and style changes in `/hyperopen/src/**`.
- `reviewer`: verify regressions and missing-test risk after the UI pass lands.
- `ui_visual_validator`: final read-only validation that the panel now feels more recognizably Hyperopen.

Optional roles if implementation expands:

- `acceptance_test_writer`: propose browser or integration coverage only if the current Playwright seam becomes insufficient.
- `edge_case_test_writer`: propose boundary coverage for mobile sheet, confirmation strip, and icon-removal states.
- `tdd_test_writer`: materialize approved failing tests only if this follow-up deliberately goes RED-first.

## Interfaces and Dependencies

No new dependency should be introduced for this pass. Reuse the existing header settings render path, action vectors, `replicant` motion hooks, deterministic test suite, committed Playwright seam, and Browser MCP design-review tooling.

The following interfaces must remain stable unless this plan is updated to say otherwise:

- `/hyperopen/src/hyperopen/views/header/vm.cljs` remains the source of truth for settings content and actions.
- `/hyperopen/src/hyperopen/views/header/settings.cljs` remains the source of truth for Trading Settings shell and row rendering.
- Existing `data-role` anchors remain stable for tests and browser tooling unless a deliberate rename is recorded here.
- Existing toggle behavior and persistence semantics remain unchanged.

Plan revision note: 2026-03-30 14:01Z - Created the initial active ExecPlan after opening `hyperopen-gzpg`, reviewing the just-completed brand-alignment refactor, and translating the remaining design feedback into a narrower three-track refinement plan focused on accent placement, typography voice, and flatter continuous grouping.
Plan revision note: 2026-03-30 14:12Z - Recorded the completed implementation, including removal of the redundant confirmation-row icons, the passing validation suite, and the passing governed browser-QA review before moving the plan to `completed/`.
