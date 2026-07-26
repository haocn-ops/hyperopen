# Finish funding modal accessibility and view-model contract follow-up

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/AGENTS.md`, `/hyperopen/docs/FRONTEND.md`, and `/hyperopen/docs/BROWSER_TESTING.md`.

Tracked issue: `hyperopen-tyy8` ("Track remaining funding modal accessibility and view-model contract follow-up").

## Purpose / Big Picture

After this change, the funding modal should behave like a real accessible dialog instead of a visually correct shell. A keyboard user should be able to open the funding modal from any supported trigger, land inside the dialog immediately, keep Tab navigation trapped inside the modal while it is open, dismiss it, and have focus return to the opener when that opener still exists. The modal forms should also expose explicit label-to-input relationships so assistive technology can announce each control reliably.

The funding modal view-model also needs a stable contract so future contributors can change application logic without silently drifting the render shape. In this repository, a "view-model contract" means an executable schema that asserts the map shape returned by `hyperopen.funding.actions/funding-modal-view-model`, backed by tests that fail when required keys or nested structures change unexpectedly.

## Progress

- [x] (2026-04-03 19:46Z) Re-read the prior completed funding modal refactor plan, `bd show hyperopen-tyy8 --json`, the governed UI/runtime docs, and the current funding modal view, command, and VM code to restate the remaining scope precisely.
- [x] (2026-04-03 19:46Z) Investigated house patterns for modal focus behavior and schema-backed VM contracts, including `header/settings`, `order-form` contracts, and current funding opener surfaces.
- [x] (2026-04-03 22:44Z) Implemented dialog focus management via a shared Replicant on-render helper, switched the funding modal shell to `aria-labelledby`, and wired explicit label/input relationships across deposit, send, transfer, and withdraw flows.
- [x] (2026-04-03 23:08Z) Added a schema-backed funding modal VM contract under `/src/hyperopen/schema/**`, exposed a thin funding-owned wrapper, and covered both handcrafted invalid samples and real builder output across all supported content kinds.
- [x] (2026-04-04 00:13Z) Ran the focused unit suite, smallest relevant Playwright regression, governed trade-route browser QA, and the required repo gates `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-04-04 01:03Z) Incorporated the final review pass by tightening opaque asset and lifecycle leaf contracts, extending transfer accessibility coverage, and hardening the focus helper with selector-backed restore fallbacks plus retry behavior.
- [x] (2026-04-04 01:40Z) Re-ran the smallest relevant Playwright regression, governed browser QA, `npm run browser:cleanup`, `npm run check`, `npm test`, and `npm run test:websocket` from the post-review tree.

## Surprises & Discoveries

- Observation: the previous funding modal refactor intentionally stopped short of focus management and schema work, and it named exactly the same remaining concerns that `hyperopen-tyy8` tracks now.
  Evidence: `/Users/barry/.codex/worktrees/dbbd/hyperopen/docs/exec-plans/completed/2026-03-12-funding-modal-view-seams.md`

- Observation: the repo does not have a reusable dialog focus-trap helper today. Existing modal shells mostly stop at `role="dialog"`, `aria-modal`, and Escape handling.
  Evidence: `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/views/funding_modal.cljs`, `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/funding/application/modal_commands.cljs`

- Observation: the cleanest local contract precedent is the order-form VM pair: schema in `/src/hyperopen/schema/**`, thin facade in `/src/hyperopen/**`, and contract assertions in tests instead of runtime UI code.
  Evidence: `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/schema/order_form_contracts.cljs`, `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/trading/order_form_contracts.cljs`, `/Users/barry/.codex/worktrees/dbbd/hyperopen/test/hyperopen/schema/contracts_test.cljs`

- Observation: Replicant seam tests compare render trees structurally, so a freshly allocated `:replicant/on-render` function on each render breaks equality even when the UI is otherwise identical.
  Evidence: `/Users/barry/.codex/worktrees/dbbd/hyperopen/test/hyperopen/views/workbench_render_seams_test.cljs`

- Observation: a stable focus hook reference on the funding modal shell fixes the immediate regression, but the broader seam test also needed to normalize function-valued render hooks so future DOM behavior helpers do not create false-negative seam failures.
  Evidence: `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/views/funding_modal.cljs`, `/Users/barry/.codex/worktrees/dbbd/hyperopen/test/hyperopen/views/workbench_render_seams_test.cljs`

- Observation: the first contract cut was still too loose around asset and lifecycle leaf maps. A read-only review caught that drift inside those nested maps would still validate.
  Evidence: `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/schema/funding_modal_contracts.cljs`, `/Users/barry/.codex/worktrees/dbbd/hyperopen/test/hyperopen/schema/contracts_test.cljs`

- Observation: focus-return behavior is reliable in unit conditions only when the restore path can fall back to a stable opener selector and keep retrying through teardown timing. Real modal-close clicks can outlive a single microtask restore attempt.
  Evidence: `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/views/ui/dialog_focus.cljs`, `/Users/barry/.codex/worktrees/dbbd/hyperopen/test/hyperopen/views/ui/dialog_focus_test.cljs`

## Decision Log

- Decision: implement the funding modal focus trap in a small Replicant `:replicant/on-render` helper instead of widening funding action signatures or storing opener metadata in app state.
  Rationale: the funding modal already renders as a single root dialog surface, and Replicant on-render hooks can safely attach mount/update/unmount DOM behavior. Capturing the previously focused element at mount time gives deterministic restore behavior without changing public action call shapes or contract schemas.
  Date/Author: 2026-04-03 / Codex

- Decision: make the funding modal VM contract schema-backed but test-enforced rather than runtime-enforced.
  Rationale: this repository uses runtime validation for action, effect, and app-state boundaries, but the closest VM contract precedent is test-side. A dedicated funding modal contract plus regression tests protects the shape without adding production overhead or a second validation pathway.
  Date/Author: 2026-04-03 / Codex

- Decision: treat search and amount inputs as part of the deferred accessibility scope, not just the send and withdraw fields that already have visible labels.
  Rationale: the remaining concern is stronger label/input association across the funding modal surface. The asset-search fields currently rely on placeholder text alone, which is weaker than an explicit label and easy to fix while the modal interaction work is open.
  Date/Author: 2026-04-03 / Codex

- Decision: harden the funding modal contract all the way down to the asset, lifecycle, and queue leaf maps instead of stopping at the larger container boundaries.
  Rationale: the bead is explicitly about protecting the funding modal render contract. Leaving those nested leaves as generic `map?` values would still allow silent drift inside the rendered VM even after the headline schema work landed.
  Date/Author: 2026-04-04 / Codex

- Decision: keep the committed Playwright regression focused on live trap-and-label behavior while covering focus-return deterministically in unit tests.
  Rationale: the repo needs a stable CI-safe browser regression. The live browser harness was not giving a deterministic active-element signal for post-close restore, even after the helper was hardened; the unit suite now covers both direct restore and selector-fallback restore paths without introducing a flaky browser gate.
  Date/Author: 2026-04-04 / Codex

## Outcomes & Retrospective

Completed.

The funding modal now behaves like an accessible dialog instead of a passive surface. Opening the modal captures the previously focused element, moves focus into the dialog, traps `Tab` and `Shift+Tab` within the dialog, and restores focus to the opener when the modal unmounts and the opener still exists. The dialog shell is now labeled by its title heading through `aria-labelledby`, and the close button exposes a stable `data-role` used by deterministic tests.

The content flows now expose explicit label relationships for search, destination, and amount fields without changing the existing visual treatment. Search inputs in the deposit and withdraw flows gained visually hidden labels plus stable `id` values, and the shared amount field helpers now accept `input-id` so deposit, send, transfer, and withdraw can expose stable label/input associations.

The view-model follow-up landed as a test-enforced schema contract. `/src/hyperopen/schema/funding_modal_contracts.cljs` now validates the exact key set of the top-level VM and its nested read-model maps, including the previously opaque asset, lifecycle, and queue leaves, while `/src/hyperopen/funding/contracts.cljs` provides the funding-owned entry point used by tests. The contract coverage proves both that real builder output satisfies the schema across the supported content kinds and that invalid drift is rejected.

One regression surfaced during full-suite validation: the workbench funding modal seam test compared raw Hiccup trees and failed once the modal shell started carrying a real function-valued `:replicant/on-render`. That seam was fixed by normalizing function-valued render hooks before equality comparison, which keeps the test focused on render shape instead of function identity.

The final focus helper also needed a second hardening pass after browser verification. It now remembers stable funding trigger selectors for deposit, transfer, and withdraw modals, and it retries focus restoration across teardown timing so the DOM-only restore behavior stays resilient even when the opener re-renders.

## Context and Orientation

The funding modal render entry point is `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/views/funding_modal.cljs`. It receives a pure view-model from `hyperopen.funding.actions/funding-modal-view-model` and renders one of several modal content branches: deposit, send, transfer, withdraw, or a legacy fallback. The current modal shell already renders the right visual structures for desktop anchored popovers and mobile bottom sheets, but its keyboard behavior is still shallow. It closes on Escape, yet it does not actively move focus into the dialog on open, it does not trap Tab navigation inside the modal, and it does not return focus to the opener on close.

The funding content modules live under `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/views/funding_modal/`. `deposit.cljs`, `send.cljs`, `transfer.cljs`, `withdraw.cljs`, and `shared.cljs` own the labeled controls and input shells. Several fields already have visible labels, but some inputs either have no explicit `id` and `for` link or rely only on placeholder copy. Those associations need to be made explicit without changing the existing visual design.

The funding modal state and action entry points live under `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/funding/application/`. `modal_state.cljs` defines the normalized UI state, `modal_commands.cljs` owns pure action logic such as opening and closing the modal, and `modal_actions.cljs` exposes the public funding action functions. The VM builder itself is `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/funding/application/modal_vm.cljs`, with nested model assembly in `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/funding/application/modal_vm/models.cljs`.

The best contract precedent in this repo is the order form. `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/schema/order_form_contracts.cljs` defines the schema, `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/trading/order_form_contracts.cljs` re-exports it, and tests assert valid and invalid VM samples. `hyperopen-tyy8` should follow that style for the funding modal VM.

## Plan of Work

First, add a small UI runtime helper for dialog focus behavior. The helper should live under `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/views/ui/` and own four things: capture the previously focused element on mount, move focus into the dialog after the modal surface appears, trap Tab and Shift+Tab within the current dialog surface, and restore focus to the previous element on unmount when that element is still connected and visible. The helper must keep DOM-only behavior out of the funding action layer.

Second, wire that helper into `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/views/funding_modal.cljs`. Both the mobile sheet and desktop popover surfaces should use the same focus helper. While in that file, strengthen the dialog semantics by switching the shell from bare `aria-label` usage to an explicit heading id relationship so the dialog title is the labeling source.

Third, strengthen the form labeling in the content modules. Add stable input ids and matching label `:for` attributes in `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/views/funding_modal/deposit.cljs`, `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/views/funding_modal/send.cljs`, `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/views/funding_modal/withdraw.cljs`, and the shared input helpers in `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/views/funding_modal/shared.cljs`. The deposit and withdraw asset-search fields should gain explicit labels, even if those labels remain visually hidden, because placeholder-only search prompts are not enough for assistive technology.

Fourth, add the funding modal VM contract. Create `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/schema/funding_modal_contracts.cljs` with exact-key schema checks for the top-level funding modal VM and its nested `:modal`, `:content`, `:feedback`, `:deposit`, `:send`, `:transfer`, `:withdraw`, and `:legacy` maps. Add a thin public wrapper under `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/funding/contracts.cljs` so the contract has a stable funding-owned entry point consistent with other repo patterns.

Fifth, add regression coverage. `/Users/barry/.codex/worktrees/dbbd/hyperopen/test/hyperopen/views/funding_modal_test.cljs` should cover the new accessibility surface, including title labeling, label/input wiring, and the Replicant focus hook behavior. `/Users/barry/.codex/worktrees/dbbd/hyperopen/test/hyperopen/schema/funding_modal_contracts_test.cljs` should exercise one valid and one invalid VM sample. `/Users/barry/.codex/worktrees/dbbd/hyperopen/test/hyperopen/funding/application/modal_vm_test.cljs` should assert that the built funding modal VM satisfies the new contract for real builder output, not just handcrafted samples.

## Concrete Steps

All commands should run from `/Users/barry/.codex/worktrees/dbbd/hyperopen`.

Planned validation sequence:

    npm run test:runner:generate
    npx shadow-cljs compile test
    node out/test.js --test=hyperopen.views.funding-modal-test,hyperopen.funding.application.modal-vm-test,hyperopen.schema.funding-modal-contracts-test,hyperopen.funding.actions-test
    npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "funding modal"
    npm run qa:design-ui -- --targets trade-route --manage-local-app
    npm run browser:cleanup
    npm run check
    npm test
    npm run test:websocket

Commands actually run:

    npm run test:runner:generate
    npx shadow-cljs compile test
    node out/test.js --test=hyperopen.views.funding-modal-test,hyperopen.views.funding-modal-accessibility-test,hyperopen.views.ui.dialog-focus-test,hyperopen.funding.application.modal-vm-test,hyperopen.schema.contracts-test
    node out/test.js --test=hyperopen.views.workbench-render-seams-test
    npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "funding modal accessibility"
    npm run qa:design-ui -- --targets trade-route --manage-local-app
    npm run browser:cleanup
    npm run check
    npm test
    npm run test:websocket
    npx shadow-cljs compile test
    node out/test.js --test=hyperopen.schema.contracts-test,hyperopen.funding.application.modal-vm-test,hyperopen.funding.application.modal-vm.lifecycle-test,hyperopen.views.funding-modal-accessibility-test,hyperopen.views.funding-modal-test,hyperopen.views.ui.dialog-focus-test,hyperopen.views.workbench-render-seams-test
    npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "funding modal accessibility"
    npm run qa:design-ui -- --targets trade-route --manage-local-app
    npm run browser:cleanup
    npm run check
    npm test
    npm run test:websocket

Outcomes:

- `npm run test:runner:generate`: PASS
- `npx shadow-cljs compile test`: PASS
- focused unit and contract run: PASS
- `hyperopen.views.workbench-render-seams-test`: PASS after normalizing function-valued `:replicant/on-render`
- Playwright funding regression: PASS (`1 passed`)
- governed browser QA: PASS across `375`, `768`, `1280`, and `1440` widths
- `npm run browser:cleanup`: PASS
- `npm run check`: PASS
- `npm test`: PASS (`3017` tests, `16121` assertions, `0` failures, `0` errors)
- `npm run test:websocket`: PASS (`432` tests, `2450` assertions, `0` failures, `0` errors)
- post-review focused rerun: PASS (`34` tests, `219` assertions, `0` failures, `0` errors)
- post-review Playwright funding regression: PASS (`1 passed`)
- post-review governed browser QA: PASS across `375`, `768`, `1280`, and `1440` widths
- post-review `npm run browser:cleanup`: PASS
- post-review `npm run check`: PASS
- post-review `npm test`: PASS (`3017` tests, `16121` assertions, `0` failures, `0` errors)
- post-review `npm run test:websocket`: PASS (`432` tests, `2450` assertions, `0` failures, `0` errors)

Governed browser QA artifacts:

- `/Users/barry/.codex/worktrees/dbbd/hyperopen/tmp/browser-inspection/design-review-2026-04-03T22-58-55-700Z-a5e5f360`
- `/Users/barry/.codex/worktrees/dbbd/hyperopen/tmp/browser-inspection/design-review-2026-04-03T23-31-05-957Z-73b6f477`

## Validation and Acceptance

Acceptance is behavioral.

When the funding modal opens from a keyboard-focused trigger, focus should move inside the modal immediately. Repeated Tab presses must stay inside the modal surface, and Shift+Tab from the first focusable control must wrap to the last focusable control instead of falling back into the page behind the dialog. When the modal closes through the close button, the backdrop, or Escape, focus should return to the opener if that opener still exists in the DOM.

Each user-editable funding input should have an explicit accessible name backed by a label relationship, not just placeholder copy. Search fields and amount fields should keep their current visible presentation while exposing stable ids and labels to assistive technology.

The funding modal VM contract is accepted when a valid real VM built by `hyperopen.funding.application.modal-vm/funding-modal-view-model` passes the schema and a deliberately invalid sample fails it with a clear contract error. The change is complete only after the focused funding tests pass, the smallest relevant Playwright funding regression passes, the governed trade-route browser QA is explicitly accounted for, and the required repo gates `npm run check`, `npm test`, and `npm run test:websocket` all pass.

## Idempotence and Recovery

The planned code changes are additive and safe to rerun. The new focus helper only attaches DOM listeners while the dialog exists and must remove them on unmount, so retrying a failed render should not leak listeners when the helper is implemented correctly. If any validation command fails midway, fix the source or test issue and rerun the same command from the same working tree. Browser QA cleanup must end with `npm run browser:cleanup` so no inspection sessions remain open between attempts.

## Artifacts and Notes

Expected durable evidence after completion:

- updated funding modal accessibility and contract tests in `/Users/barry/.codex/worktrees/dbbd/hyperopen/test/hyperopen/**`
- one new funding modal schema contract namespace under `/Users/barry/.codex/worktrees/dbbd/hyperopen/src/hyperopen/schema/**`
- Playwright evidence from the funding modal regression
- governed browser QA artifacts under `/Users/barry/.codex/worktrees/dbbd/hyperopen/tmp/browser-inspection/**`

## Interfaces and Dependencies

The new focus helper should expose one entry point that returns a Replicant on-render handler for dialog surfaces. It must own mount, update, and unmount behavior, and it must be safe for both the funding modal desktop popover and mobile sheet to share.

The funding modal contract surface should expose:

- `funding-modal-vm-valid?`
- `assert-funding-modal-vm!`

The contract should validate the exact key set of the funding modal VM and the exact key sets of its nested read-model maps. Optional values may remain `nil`, but the key presence and nested container shapes should stay stable so future contributors get immediate feedback when they drift the render contract.

Revision note: 2026-04-03 19:46Z. Created this active ExecPlan after re-reading the completed funding modal seam refactor, reviewing the remaining `hyperopen-tyy8` tracker scope, and mapping the current repo patterns for dialog focus behavior and schema-backed VM contracts.
Revision note: 2026-04-04 00:13Z. Completed implementation, recorded validation evidence, and prepared the plan for archival under `completed`.
Revision note: 2026-04-04 01:40Z. Incorporated the final static-review hardening pass, refreshed validation from the updated tree, and confirmed the bead is ready to close.
