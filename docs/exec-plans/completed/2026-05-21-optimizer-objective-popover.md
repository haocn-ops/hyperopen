# Convert Optimizer Objective Modal To Anchored Popover

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. A future worker should be able to read only this file plus the repository and complete the change without prior conversation context.

## Purpose / Big Picture

The optimizer scenario detail page currently opens the objective selector as a centered modal with a dimmed full-screen backdrop. That is too heavy for a local setting in the provenance strip, and it separates the choice from the button the user clicked. After this change, clicking the objective label in the optimizer draft or scenario detail page opens a compact popover directly under that objective button, visually behaving like a dropdown while preserving the existing ability to choose an objective and apply it with an optimizer rerun.

The change also documents a product UI standard: when a control edits nearby, recoverable page-local state, Hyperopen should prefer an anchored popover or dropdown over a blocking modal. Modals remain appropriate for route-level interruption, destructive confirmation, multi-step wallet or funding flows, or interactions that need full focus isolation.

## Context References

Public refs:
- Direct user/maintainer request on 2026-05-20 with screenshot evidence showing the current objective selector as a centered modal over the optimizer draft page. The requested behavior is a popover near the objective button, just under it, like an almost-dropdown.

Repo artifacts:
- `/hyperopen/AGENTS.md` requires an ExecPlan for risky UI work and requires UI work to account for `/hyperopen/docs/BROWSER_TESTING.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/browser-qa.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md`.
- `/hyperopen/.agents/PLANS.md` defines the required ExecPlan format.
- `/hyperopen/docs/FRONTEND.md` is the canonical place to document UI interaction standards.
- `/hyperopen/docs/BROWSER_TESTING.md` defines Playwright versus Browser MCP routing.
- `/hyperopen/docs/agent-guides/browser-qa.md` defines six required browser-QA passes and review widths `375`, `768`, `1280`, and `1440`.
- `/hyperopen/src/hyperopen/views/portfolio/optimize/scenario_objective_menu.cljs` currently renders the objective selector.
- `/hyperopen/src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs` mounts the objective trigger and menu inside the provenance strip.
- `/hyperopen/src/styles/surfaces/optimizer/results.css` contains the current objective menu CSS.
- `/hyperopen/src/hyperopen/portfolio/optimizer/actions/draft.cljs` owns objective menu open, close, selection, Escape handling, and apply-plus-rerun action effects.
- `/hyperopen/test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs` contains render-level assertions for the current objective menu.
- `/hyperopen/tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs` contains browser coverage for changing the objective and viewport containment.

Local scratch refs (non-authoritative):
- User-provided screenshot at `/var/folders/dg/3nkyzrp12fn141vv7f6rc9v40000gn/T/TemporaryItems/NSIRD_screencaptureui_jxXP08/Screenshot 2026-05-20 at 10.04.43 PM.png`.

## Progress

- [x] (2026-05-21 02:07Z) Inspected the current objective selector implementation and confirmed it uses a fixed full-screen backdrop plus centered `role="dialog"` section in `/hyperopen/src/hyperopen/views/portfolio/optimize/scenario_objective_menu.cljs`.
- [x] (2026-05-21 02:07Z) Located the trigger mount in `/hyperopen/src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs`, current CSS in `/hyperopen/src/styles/surfaces/optimizer/results.css`, action state in `/hyperopen/src/hyperopen/portfolio/optimizer/actions/draft.cljs`, ClojureScript tests, and Playwright tests.
- [x] (2026-05-21 02:07Z) Captured the product decision that the objective selector should be an anchored popover under the objective button, not a blocking modal.
- [x] (2026-05-21 13:02Z) Added a frontend policy rule to `/hyperopen/docs/FRONTEND.md` preferring anchored popovers, dropdowns, inline disclosure, or responsive sheets over full-screen modals for page-local recoverable controls.
- [x] (2026-05-21 13:05Z) Converted the optimizer objective selector from a fixed backdrop plus centered dialog into a provenance-cell anchored popover under the objective trigger.
- [x] (2026-05-21 13:05Z) Updated render tests to assert valid trigger popup semantics, `aria-expanded`, absence of `portfolio-optimizer-objective-menu-backdrop`, `role="region"`, and no `aria-modal`.
- [x] (2026-05-21 13:06Z) Updated Playwright coverage to assert no backdrop, viewport containment, and trigger-relative placement at `375`, `768`, `1280`, and `1440`.
- [x] (2026-05-21 13:14Z) Ran targeted and full validation. `npm run check` is blocked by an unrelated stale product spec, documented below; `npm test`, `npm run test:websocket`, focused Playwright, style tests, and design review passed.

## Surprises & Discoveries

- Observation: The current objective menu already has an isolated action model, so the conversion should be mostly view and style work rather than optimizer engine work.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/actions/draft.cljs` stores only `:portfolio-ui :optimizer :objective-menu-open?` and `:portfolio-ui :optimizer :objective-menu-selection`, then applies the selected model and reruns through `run-portfolio-optimizer-from-draft`.
- Observation: The existing Playwright test named `portfolio optimizer draft objective menu stays contained across viewports` only checks viewport containment, not trigger-relative placement.
  Evidence: `/hyperopen/tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs` checks menu bounding box is inside the viewport but does not compare the menu box to the trigger box.
- Observation: The UI docs mention modals, dropdowns, and interaction flow, but they do not currently state the standard preference for popovers over modals for local page controls.
  Evidence: `/hyperopen/docs/FRONTEND.md` has UI runtime and QA rules but no explicit modal-vs-popover guidance.
- Observation: The plan's suggested focused `npm test -- hyperopen.views.portfolio.optimize.scenario-detail-view-test hyperopen.portfolio.optimizer.draft-actions-test` command is not supported by the current test runner; it prints `Unknown arg` and then runs the full ClojureScript test suite.
  Evidence: The command completed with `Ran 3987 tests containing 21951 assertions. 0 failures, 0 errors.` after printing `Unknown arg` for both namespace arguments.
- Observation: The registered browser design-review target for this surface is `portfolio-optimizer-results-route`, not the plan's placeholder `portfolio-optimizer`.
  Evidence: `npm run qa:design-ui -- --targets portfolio-optimizer --manage-local-app` failed with `Unknown design-review target id(s): portfolio-optimizer`; `/hyperopen/tools/browser-inspection/config/design-review-routing.json` lists `portfolio-optimizer-results-route`.
- Observation: `npm run check` is currently blocked before build/style stages by an unrelated stale document guard.
  Evidence: `npm run check` failed at `bb -m dev.check-docs` with `[stale-doc] docs/product-specs/portfolio-page-parity-prd.md - document is stale: 91 days old, max allowed 90`.

## Decision Log

- Decision: Keep the current objective menu action ids and draft state paths while changing the visual surface from modal to popover.
  Rationale: The user-visible problem is placement and modality. The existing open, close, selection, Escape, and apply-plus-rerun behavior is already covered and does not need new domain state.
  Date/Author: 2026-05-21 / Codex
- Decision: Render the objective popover in the provenance objective cell, anchored by a relatively positioned wrapper around the trigger and panel, with viewport-aware CSS for mobile containment.
  Rationale: The popover should be spatially connected to the objective button. Keeping it in the same component subtree avoids a new global overlay manager and matches existing local dropdown patterns.
  Date/Author: 2026-05-21 / Codex
- Decision: Remove modal semantics from this selector and use popover/menu semantics instead.
  Rationale: A page-local objective selector is not a blocking workflow. It should not advertise itself as `aria-modal="true"` or use a full-screen modal backdrop.
  Date/Author: 2026-05-21 / Codex
- Decision: Document the general rule in `/hyperopen/docs/FRONTEND.md`, not in optimizer-only docs.
  Rationale: The concern is application-wide design practice, and future UI work should discover it from the canonical frontend policy.
  Date/Author: 2026-05-21 / Codex
- Decision: Use `aria-haspopup="true"` on the trigger and `role="region"` on the panel.
  Rationale: The panel contains option buttons plus Cancel and Apply actions, so it is not a pure ARIA menu. `aria-haspopup="region"` is not a valid token, while `true` validly signals a popup and the labeled region describes the mixed-control panel without claiming modal focus isolation.
  Date/Author: 2026-05-21 / Codex
- Decision: Leave `/hyperopen/docs/product-specs/portfolio-page-parity-prd.md` untouched even though it blocks `npm run check`.
  Rationale: That document is unrelated to the optimizer objective popover work, and updating its review metadata would be an unrelated change. The failure is recorded as a pre-existing governance-date blocker.
  Date/Author: 2026-05-21 / Codex

## Outcomes & Retrospective

Implemented the requested conversion. The optimizer objective selector now stays in the provenance strip's spatial context: the trigger and panel share a relative anchor wrapper, the panel renders as an absolutely positioned popover under the objective button, the full-screen backdrop is gone, and modal-only `role="dialog"` / `aria-modal="true"` semantics were removed. The existing objective-selection and apply-plus-rerun action pipeline remains unchanged.

The implementation reduces interaction complexity by removing a blocking overlay for a local, recoverable setting while preserving keyboard Escape close, explicit Cancel, disabled Apply for unchanged selections, and the existing rerun behavior. No reusable popover helper was extracted because this change only needed CSS-based placement inside an already local provenance cell; extracting a helper would add more abstraction than this one component currently needs.

Validation is mostly green. `npm test`, `npm run test:websocket`, the focused objective Playwright slice, `npm run test:styles`, and design review for `portfolio-optimizer-results-route` passed. `npm run check` remains blocked by an unrelated stale document guard for `/hyperopen/docs/product-specs/portfolio-page-parity-prd.md`.

## Context and Orientation

The optimizer scenario detail route is the review surface for an optimization scenario. A scenario is a saved or draft set of optimizer inputs, including the asset universe, objective, return model, risk model, constraints, and retained result. The provenance strip is the horizontal row below the scenario header that summarizes the current inputs and lets the user inspect or edit some of them.

The objective selector is currently implemented by `/hyperopen/src/hyperopen/views/portfolio/optimize/scenario_objective_menu.cljs`. The trigger is `objective-trigger`, a small button with `data-role="portfolio-optimizer-objective-menu-trigger"`. The open panel is `objective-menu`, which currently returns a full-screen `optimizer-objective-menu-backdrop` containing a centered `section` with `role="dialog"`, `aria-modal="true"`, and `data-role="portfolio-optimizer-objective-menu"`.

The trigger is mounted inside `provenance-strip` in `/hyperopen/src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs`. That function renders an objective cell with class `optimizer-provenance-objective`; inside it, the code renders the label, the trigger, and the menu.

The action layer should remain stable. `/hyperopen/src/hyperopen/portfolio/optimizer/actions/draft.cljs` opens the menu by setting `:portfolio-ui :optimizer :objective-menu-open?` true and initializing `:objective-menu-selection` to the current objective. It closes the menu by setting open false and clearing the selection. It applies the selection by updating the draft objective and, when needed, the return model, closing the menu, then rerunning the optimizer. Do not rename these actions unless a runtime contract forces a change:

- `:actions/open-portfolio-optimizer-objective-menu`
- `:actions/close-portfolio-optimizer-objective-menu`
- `:actions/handle-portfolio-optimizer-objective-menu-keydown`
- `:actions/select-portfolio-optimizer-objective-menu-option`
- `:actions/apply-portfolio-optimizer-objective-menu-selection-and-run`

The visual styling for the objective selector lives in `/hyperopen/src/styles/surfaces/optimizer/results.css`. The current modal classes include `optimizer-objective-menu-backdrop`, `optimizer-objective-menu`, `optimizer-objective-menu-option`, and `optimizer-objective-menu-check`.

## Plan of Work

First, update `/hyperopen/docs/FRONTEND.md` so the policy is explicit. Add a rule under `UI Interaction Runtime Rules (MUST)` or a nearby canonical section that says local, recoverable controls should prefer anchored popovers, dropdowns, inline disclosure, or sheets over full-screen modals. The text should clarify that modals are reserved for interactions requiring focus isolation, destructive confirmation, route-level interruption, wallet/provider flows, or multi-step flows where leaving context would be dangerous. The wording must not ban all modals, because funding, wallet authorization, and other high-risk flows still use them legitimately.

Second, convert `/hyperopen/src/hyperopen/views/portfolio/optimize/scenario_objective_menu.cljs` from modal rendering to popover rendering. Change `objective-trigger` so `:aria-haspopup` is `"menu"` or `"listbox"` instead of `"dialog"`, and add `:aria-expanded` based on open state if that state is available at the call site. The cleanest path is to replace `objective-trigger` with a function that receives `open?` and the label, or add a sibling wrapper function that renders both trigger and menu from one state-aware place. Keep the `data-role` value `portfolio-optimizer-objective-menu-trigger` so existing tests and browser selectors remain stable.

Third, replace the fixed backdrop with a local anchored panel. In `objective-menu`, remove the outer `fixed inset-0 z-50 flex items-center justify-center` backdrop and render a single absolutely positioned panel under the objective trigger. The parent objective cell or a new wrapper should have `relative` and enough stacking context, such as `z-40` while open. The panel should use classes that express this behavior, for example `optimizer-objective-popover absolute left-0 top-full mt-2 w-[min(360px,calc(100vw-2rem))]`. On desktop it should align its left edge with the objective trigger area and sit just below it. On small screens it must clamp to the viewport instead of overflowing horizontally; a practical implementation is to keep the panel inside the provenance strip cell, use `max-width: calc(100vw - 2rem)`, and use responsive left/right rules in CSS if the cell is near the viewport edge.

Fourth, remove modal-only semantics. The popover panel should not set `aria-modal="true"`. It can use `role="menu"`, `role="listbox"`, or `role="region"` with `aria-label="Change objective"` depending on the final keyboard model. Because the current options are buttons and the menu includes Apply and Cancel buttons, `role="region"` with a clear label may be less misleading than `role="menu"` unless full arrow-key menu semantics are implemented. Keep Escape-to-close through the existing keydown action. Preserve focus on open, but do not trap focus as a modal. Clicking Cancel, the close button, or Apply should close it exactly as before.

Fifth, adjust copy and layout to feel like a dropdown popover rather than a modal. Keep the title `Change objective`, the explanatory sentence, the five choices, and Apply/Cancel. Reduce the visual weight by using tighter padding, no dimmed backdrop, and a small pointer or top border accent only if it helps connect the panel to the trigger. The panel should look like an optimizer surface: dark background, one-pixel border, compact typography, no oversized card feel, and no nested cards. Keep option rows stable height and readable. The selected row should remain clearly indicated with the existing check mark and selected border.

Sixth, update `/hyperopen/src/styles/surfaces/optimizer/results.css`. Remove or stop using `.optimizer-objective-menu-backdrop` for this path. Add styles for the popover class, for example background, border, shadow, z-index, max-height, overflow-y, and mobile width clamps. Continue to use the existing option and check styles unless the class names are renamed. Avoid a one-off color palette; use the existing optimizer CSS variables such as `--optimizer-bg`, `--optimizer-text`, `--optimizer-border`, `--optimizer-border-strong`, and `--optimizer-accent`.

Seventh, update ClojureScript render tests in `/hyperopen/test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs`. The existing test `portfolio-optimizer-scenario-detail-objective-menu-renders-actions-test` should continue to assert the trigger action, open panel, option actions, disabled Apply when unchanged, enabled Apply when changed, Cancel, Close, and Escape handling. Add or update assertions so the open panel is not inside `portfolio-optimizer-objective-menu-backdrop`, does not have `aria-modal`, and has the expected popover class. If `aria-expanded` is added to the trigger, assert `false` on the closed view and `true` on the open view.

Eighth, update Playwright coverage in `/hyperopen/tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs`. The existing behavior test `portfolio optimizer draft objective menu changes objective and reruns frontier` should still pass after selector changes. The viewport containment test should be strengthened to compare the menu box with the trigger box. For `1280` and `1440`, assert the menu top is greater than or equal to the trigger bottom minus a small tolerance and that the horizontal distance between the menu left and trigger left is small enough to prove anchoring. For `375` and `768`, assert the menu remains inside the viewport and still opens below the trigger when there is room; if the strip wraps, assert it is visibly connected to the trigger and not centered in the viewport. Also assert the backdrop selector has count zero after opening.

Ninth, run the targeted tests and then the required gates. Because this is UI work, run the smallest relevant Playwright test first, then browser QA according to the repo docs, then the repo gates if code changes were made. Record all commands and outcomes in this plan before completion.

## Concrete Steps

Work from `/hyperopen`, which in this workspace is `/Users/barry/.codex/worktrees/0dcd/hyperopen`.

1. Read the current files before editing:

       sed -n '1,230p' src/hyperopen/views/portfolio/optimize/scenario_objective_menu.cljs
       sed -n '240,285p' src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs
       sed -n '45,110p' src/styles/surfaces/optimizer/results.css
       sed -n '320,405p' test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs
       sed -n '520,640p' tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs

   Expect to see the current full-screen backdrop, centered dialog, objective cell mount, CSS selectors, and test assertions described in this plan.

2. Edit `/hyperopen/docs/FRONTEND.md` to document the popover preference. Place the rule near the existing UI interaction runtime rules so agents see it before implementing interaction surfaces.

3. Edit `/hyperopen/src/hyperopen/views/portfolio/optimize/scenario_objective_menu.cljs` to render an anchored popover instead of a modal. Keep the option data and action dispatches stable. Remove `aria-modal`, change `aria-haspopup`, and preserve Escape handling.

4. If needed, edit `/hyperopen/src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs` so the objective trigger and popover share a relative wrapper and so the trigger can receive accurate `aria-expanded`.

5. Edit `/hyperopen/src/styles/surfaces/optimizer/results.css` to style the anchored popover. Remove any now-unused modal-backdrop styling if no other component uses it.

6. Edit `/hyperopen/test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs` to assert popover semantics and absence of modal semantics.

7. Edit `/hyperopen/tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs` to assert trigger-relative placement and absence of the backdrop in addition to viewport containment.

8. Run targeted ClojureScript tests:

       npm test -- hyperopen.views.portfolio.optimize.scenario-detail-view-test hyperopen.portfolio.optimizer.draft-actions-test

   Actual result: the current test runner does not accept those namespace arguments and printed `Unknown arg`, then ran the full ClojureScript suite. The suite passed with `Ran 3987 tests containing 21951 assertions. 0 failures, 0 errors.`

9. Run the smallest relevant Playwright test first:

       npx playwright test -c playwright.config.mjs tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep "portfolio optimizer draft objective menu"

   Expected result: the objective menu behavior test and containment test pass. The containment test should fail before the popover conversion if it includes the new trigger-relative assertions, then pass after implementation.

10. Run browser QA or the repo’s governed design UI command for the changed route:

       npm run qa:design-ui -- --targets portfolio-optimizer --manage-local-app

   Actual result: this target was not available. The exact failure was `Unknown design-review target id(s): portfolio-optimizer`. The registered optimizer result target was used instead:

       npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app

   This passed with run id `design-review-2026-05-21T13-12-02-330Z-c33cada6` and artifacts under `/Users/barry/.codex/worktrees/0dcd/hyperopen/tmp/browser-inspection/design-review-2026-05-21T13-12-02-330Z-c33cada6`.

11. Run cleanup if Browser MCP or browser-inspection sessions were created:

       npm run browser:cleanup

   Actual result: passed with `{"ok": true, "stopped": [], "results": []}`.

12. Run required repository gates for code changes:

       npm run check
       npm test
       npm run test:websocket

   Actual result: `npm test` passed with `3987` tests and `21951` assertions. `npm run test:websocket` passed with `524` tests and `3043` assertions. `npm run check` failed before reaching style/build stages due to an unrelated stale product spec:

       [stale-doc] docs/product-specs/portfolio-page-parity-prd.md - document is stale: 91 days old, max allowed 90

   Because `npm run check` stopped before style validation, `npm run test:styles` was run separately and passed all `6` tests.

13. Update this ExecPlan with progress timestamps, discoveries, decisions, validation evidence, and an outcome retrospective. Move it from `docs/exec-plans/active/` to `docs/exec-plans/completed/` only after acceptance criteria pass or the maintainer explicitly accepts the remaining risk.

## Validation and Acceptance

Acceptance is user-visible and test-backed.

After implementation, open the optimizer draft or scenario detail page, click the objective label in the provenance strip, and observe that the objective selector opens directly below the objective button. The page behind it should not dim. The selector should read as a compact dropdown-like popover, not a blocking modal. The panel should stay connected to the trigger at desktop widths and remain contained at `375`, `768`, `1280`, and `1440`.

Keyboard acceptance: when the popover is open, Escape closes it. The trigger, option buttons, Cancel, Close, and Apply are keyboard reachable with visible focus. Apply remains disabled when the pending selection equals the current objective. Selecting a different objective enables Apply. Clicking Apply closes the popover, updates the draft objective, and reruns the optimizer.

Documentation acceptance: `/hyperopen/docs/FRONTEND.md` explicitly states that local, recoverable controls should prefer anchored popovers, dropdowns, inline disclosure, or sheets over full-screen modals, while preserving valid modal uses for high-risk or focus-isolated workflows.

Automated acceptance:
- `npm test -- hyperopen.views.portfolio.optimize.scenario-detail-view-test hyperopen.portfolio.optimizer.draft-actions-test` passes.
- `npx playwright test -c playwright.config.mjs tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep "portfolio optimizer draft objective menu"` passes and includes a trigger-relative placement assertion.
- `npm run check`, `npm test`, and `npm run test:websocket` pass before final signoff unless an unrelated pre-existing failure is documented.

Browser-QA acceptance:
- Visual pass: PASS, with the popover visually anchored under the objective trigger and no modal backdrop.
- Native-control pass: PASS, with no new unexpected native controls introduced.
- Styling-consistency pass: PASS, using optimizer tokens and existing option styling.
- Interaction pass: PASS, covering open, close, Escape, selection, Apply, disabled Apply, hover, focus, and resize.
- Layout-regression pass: PASS, with no horizontal overflow or clipping at `375`, `768`, `1280`, and `1440`.
- Jank/perf pass: PASS, with repeated open/close and resize showing no flicker or layout shift beyond the popover itself.

## Idempotence and Recovery

The edits are local view, style, test, and documentation changes. They can be repeated safely by re-running tests after each patch. If the popover overflows at small widths, prefer CSS clamping and wrapping inside the existing provenance strip before introducing JavaScript layout measurement. If CSS-only placement cannot meet the mobile containment requirement, add a minimal layout helper local to `scenario_objective_menu.cljs` following the position-reduce popover pattern in `/hyperopen/src/hyperopen/views/account_info/position_reduce_popover.cljs`; record that decision in this plan before implementation.

Do not change optimizer objective model semantics, solver request construction, scenario persistence, or result rendering for this task. If a test failure indicates a domain behavior change, stop and record the discovery before widening scope.

## Artifacts and Notes

Current modal evidence from source:

    /hyperopen/src/hyperopen/views/portfolio/optimize/scenario_objective_menu.cljs renders
    data-role="portfolio-optimizer-objective-menu-backdrop" with fixed inset-0 z-50
    and data-role="portfolio-optimizer-objective-menu" with role="dialog" and aria-modal="true".

Current action evidence from source:

    open-portfolio-optimizer-objective-menu saves objective-menu-open? true and seeds objective-menu-selection.
    apply-portfolio-optimizer-objective-menu-selection-and-run saves the selected draft objective,
    closes the menu, clears selection, then invokes run-portfolio-optimizer-from-draft.

Planned documentation wording should be close to:

    For page-local, recoverable controls, prefer anchored popovers, dropdowns, inline disclosure, or responsive sheets over full-screen modals. Use a modal only when the workflow requires focus isolation, route-level interruption, destructive confirmation, provider/wallet interaction, or multi-step state that would be risky to leave half-complete.

## Interfaces and Dependencies

No new package dependency should be added.

Keep these data-role selectors stable:
- `portfolio-optimizer-objective-menu-trigger`
- `portfolio-optimizer-objective-menu`
- `portfolio-optimizer-objective-menu-option-minimum-volatility`
- `portfolio-optimizer-objective-menu-option-max-sharpe`
- `portfolio-optimizer-objective-menu-option-target-volatility`
- `portfolio-optimizer-objective-menu-option-maximum-return`
- `portfolio-optimizer-objective-menu-option-use-my-views`
- `portfolio-optimizer-objective-menu-close`
- `portfolio-optimizer-objective-menu-cancel`
- `portfolio-optimizer-objective-menu-apply`

The backdrop selector `portfolio-optimizer-objective-menu-backdrop` should disappear from the objective path. Tests should assert its absence when the popover is open.

The final component should expose a popover-like DOM contract:
- Trigger button has `aria-haspopup` set to a popover-appropriate value and, if feasible, `aria-expanded`.
- Panel has `data-role="portfolio-optimizer-objective-menu"` and a label such as `aria-label="Change objective"`.
- Panel does not have `aria-modal="true"`.
- Panel opens below the trigger and remains inside the viewport.

Revision note, 2026-05-21 / Codex: Created this active ExecPlan from the direct user request to replace the optimizer objective modal with an anchored popover and to document the application-wide preference for popovers over modals for local controls.
