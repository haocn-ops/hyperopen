# Refactor funding modal view seams and fix modal UI regressions

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Tracked issue: `hyperopen-v9gb` ("Refactor funding modal view seams and fix UI regressions").

## Purpose / Big Picture

After this change, the funding modal should remain visually familiar while becoming easier to reason about and safer to extend. A contributor should be able to open `/hyperopen/src/hyperopen/views/funding_modal.cljs`, see that it only handles rendering concerns, and rely on a small dedicated helper seam for layout measurement instead of tracing raw selector strings and `js/globalThis` reads through the view itself. The visible behavior should improve in concrete ways: the deposit-address step should expose the correct data role, decimal inputs should request the expected mobile keypad, deposit quick controls should stop mutating while submit is in flight, unknown content states should render an explicit fallback panel instead of disappearing, and the close button should carry an accessible label.

## Progress

- [x] 2026-03-12 00:43Z Read `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/WORK_TRACKING.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, `/hyperopen/docs/agent-guides/trading-ui-policy.md`, and `/hyperopen/src/hyperopen/views/funding_modal.cljs` to scope the refactor against repository policy.
- [x] 2026-03-12 00:43Z Inspected adjacent selector owners in `/hyperopen/src/hyperopen/views/account_equity_view.cljs` and `/hyperopen/src/hyperopen/views/trade_view.cljs`, plus current regression coverage in `/hyperopen/test/hyperopen/views/funding_modal_test.cljs`.
- [x] 2026-03-12 00:43Z Created and claimed `hyperopen-v9gb` for this refactor.
- [x] 2026-03-12 00:47Z Added `/hyperopen/src/hyperopen/views/ui/funding_modal_positioning.cljs`, moved the layout math and DOM reads into that seam, and switched `/hyperopen/src/hyperopen/views/account_equity_view.cljs` plus `/hyperopen/src/hyperopen/views/trade_view.cljs` to shared anchor constants.
- [x] 2026-03-12 00:49Z Simplified `/hyperopen/src/hyperopen/views/funding_modal.cljs` so layout resolution happens only inside the `when open?` branch, fixed the deposit-address `data-role`, normalized decimal inputs to `:inputmode`, disabled deposit quick controls while submitting, added an unknown-content fallback, and labeled the close button.
- [x] 2026-03-12 00:50Z Added focused regression coverage in `/hyperopen/test/hyperopen/views/funding_modal_test.cljs` and `/hyperopen/test/hyperopen/views/ui/funding_modal_positioning_test.cljs`, then let `test/test_runner_generated.cljs` regenerate with the new namespace.
- [x] 2026-03-12 00:53Z Restored missing local npm dependencies with `npm ci`, then passed `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] 2026-03-12 00:54Z Filed follow-up `hyperopen-tyy8` for the intentionally deferred accessibility/focus-management and view-model contract work, then prepared this plan for `completed`.
- [x] 2026-03-12 01:20Z Ran headed browser-inspection QA for the funding modal on desktop and mobile, captured artifacts under `/hyperopen/tmp/browser-inspection/`, and recorded the results in `/hyperopen/docs/qa/funding-modal-view-seams-browser-qa-2026-03-12.md`.

## Surprises & Discoveries

- Observation: the funding modal already has focused rendering tests for selector fallback, popover alignment, lifecycle panels, and mobile sheet behavior.
  Evidence: `/hyperopen/test/hyperopen/views/funding_modal_test.cljs` already covers fallback-anchor selection and trade-panel divider alignment, which means the refactor can extend existing tests rather than inventing a brand-new harness.

- Observation: the selector coupling called out in review really does span multiple files, not just the modal.
  Evidence: `/hyperopen/src/hyperopen/views/funding_modal.cljs` hard-codes `[data-role='funding-action-deposit']`, `[data-role='funding-action-transfer']`, `[data-role='funding-action-withdraw']`, and `[data-parity-id='trade-order-entry-panel']`, while `/hyperopen/src/hyperopen/views/account_equity_view.cljs` and `/hyperopen/src/hyperopen/views/trade_view.cljs` own the corresponding DOM attributes as separate literals.

- Observation: this repository already prefers `:inputmode` for numeric keyboard hints in interactive views.
  Evidence: nearby inputs in `/hyperopen/src/hyperopen/views/account_info/position_tpsl_modal.cljs`, `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs`, and `/hyperopen/src/hyperopen/views/active_asset/funding_tooltip.cljs` use `:inputmode`, while `/hyperopen/src/hyperopen/views/funding_modal.cljs` still uses `:input-mode`.

- Observation: one existing funding modal test was implicitly relying on the deposit-address copy-paste bug.
  Evidence: after correcting the address-step root `data-role`, `/hyperopen/test/hyperopen/views/funding_modal_test.cljs` failed because `deposit-flow-does-not-render-withdraw-lifecycle-panel-test` was still searching for `funding-deposit-amount-step`; updating that assertion to `funding-deposit-address-step` made the test describe the intended behavior again.

- Observation: the required validation gates were blocked first by missing local npm dependencies, not by a source-level regression.
  Evidence: the first `npm run check` failed during `shadow-cljs compile app` with `The required JS dependency "@noble/secp256k1" is not available`; running `npm ci` restored `node_modules` and the same gate then passed.

## Decision Log

- Decision: keep the refactor incremental and local to the presentation layer by introducing a dedicated UI helper namespace instead of moving anchoring rules into funding domain/application code.
  Rationale: the review correctly identified layout measurement as presentation infrastructure, not business logic. A small helper seam removes `js/globalThis` and selector math from the view file without widening the change into domain-level workflow code.
  Date/Author: 2026-03-12 / Codex

- Decision: centralize the selector-driving DOM identifiers into shared constants and consume those constants from the owning views.
  Rationale: the brittle part is not just selector lookup; it is selector drift between the modal and the components it anchors to. Sharing the identifiers from one small namespace is a low-risk way to reduce that drift without redesigning the open-modal action contract in this task.
  Date/Author: 2026-03-12 / Codex

- Decision: keep the new helper seam presentation-specific and place it in `/hyperopen/src/hyperopen/views/ui/funding_modal_positioning.cljs`.
  Rationale: the seam now owns two things that change together: the shared DOM hooks the modal depends on and the imperative positioning logic that consumes those hooks. Keeping that pair together avoids reintroducing the same brittle selector knowledge inside the modal view while still keeping the helper out of domain/application namespaces.
  Date/Author: 2026-03-12 / Codex

- Decision: defer focus-trap/focus-restore work, stronger input-label associations, and view-model contract/schema documentation into follow-up `hyperopen-tyy8`.
  Rationale: those changes are worthwhile, but they widen the blast radius beyond the scoped refactor the review recommended. Landing the selector/layout seam and the concrete regressions first keeps this task shippable while still recording the remaining work in the repository’s source-of-truth tracker.
  Date/Author: 2026-03-12 / Codex

## Outcomes & Retrospective

The refactor achieved the scoped goal. `/hyperopen/src/hyperopen/views/funding_modal.cljs` now reads like a view again: it renders content, branches on content kinds, and delegates positioning to `/hyperopen/src/hyperopen/views/ui/funding_modal_positioning.cljs`. The selector coupling is still present as a UI concern, but the brittle DOM hooks are now centralized and shared with their owning views in `/hyperopen/src/hyperopen/views/account_equity_view.cljs` and `/hyperopen/src/hyperopen/views/trade_view.cljs` instead of being duplicated as unrelated string literals.

The visible regressions from the review were fixed. Deposit-address content now exposes `funding-deposit-address-step`, decimal inputs use `:inputmode`, deposit minimum and quick-amount buttons disable during submit, unknown content renders an explicit fallback panel, and the visible close button includes `aria-label "Close funding dialog"`. The modal also stops touching DOM measurement code while closed because layout resolution now happens only inside the open-modal branch.

Overall complexity decreased. The view file lost its imperative measurement helpers and noisy `*`-suffixed locals, while the remaining complexity moved into a dedicated seam with direct tests. The codebase gained one small helper namespace and one small helper test namespace, but that addition reduced duplication and made the presentation boundary easier for humans and agents to follow.

Validation passed after restoring missing local npm dependencies with `npm ci`: `npm run check`, `npm test`, and `npm run test:websocket` all exited successfully. Remaining broader funding modal follow-up work is tracked in `hyperopen-tyy8`.

Follow-up browser QA also passed in a headed Chrome session. Desktop `/trade` deposit flow rendered as an anchored popover, mobile `/trade` deposit flow rendered as a bottom sheet, and live mobile DOM checks confirmed the close-button `aria-label` plus the corrected `funding-deposit-address-step` on the BTC deposit path. The browser QA note is `/hyperopen/docs/qa/funding-modal-view-seams-browser-qa-2026-03-12.md`.

## Context and Orientation

The main view entry point is `/hyperopen/src/hyperopen/views/funding_modal.cljs`. It renders a funding modal from a prebuilt view model returned by `hyperopen.funding.actions/funding-modal-view-model`. In this repository, a "view model" means a pure data map that has already translated application state into render-ready content such as `{:content {:kind :deposit/address} ...}`. The modal view is not supposed to decide business rules like withdrawal policy or fee math; it should decide what to render for each already-shaped content kind.

Today the file still owns a second concern: DOM measurement and anchor fallback. It computes viewport dimensions, queries the DOM with raw selectors, and nudges the popover against the trade order-entry divider. That work is still presentation-layer code, but it is imperative infrastructure rather than declarative view composition. The same file also carries a handful of smaller view regressions that the review identified: one duplicated `data-role`, inconsistent `:input-mode` attributes, mutable quick controls during submit, a silent `nil` branch for unknown content, and a missing close-button label.

The selector owners live in adjacent views. `/hyperopen/src/hyperopen/views/account_equity_view.cljs` renders the funding action buttons with `data-role` values such as `funding-action-deposit`. `/hyperopen/src/hyperopen/views/trade_view.cljs` renders the order-entry rail with `data-parity-id "trade-order-entry-panel"`. The refactor should make these identifiers shared constants so the modal and the anchor owners do not drift apart.

The existing regression harness lives in `/hyperopen/test/hyperopen/views/funding_modal_test.cljs`. Those tests traverse Hiccup nodes directly, which is enough to verify classes, styles, `aria-*` attributes, button disabled state, and rendered fallback copy without spinning up the whole UI.

## Plan of Work

This plan has been implemented. The work happened in four concrete steps.

First, a new helper namespace was added at `/hyperopen/src/hyperopen/views/ui/funding_modal_positioning.cljs`. It now owns the anchor identifiers, selector lookup, viewport fallback, trade-divider alignment, and the one public `resolve-modal-layout` function that returns the render-ready layout map.

Second, the DOM anchor owners were rewired to use shared constants. `/hyperopen/src/hyperopen/views/account_equity_view.cljs` now reads the deposit/transfer/withdraw action roles from the helper namespace, and `/hyperopen/src/hyperopen/views/trade_view.cljs` reads the order-entry panel parity id from the same seam.

Third, `/hyperopen/src/hyperopen/views/funding_modal.cljs` was simplified to compute layout only while open and to consume the new helper result instead of measuring directly. In the same pass, the view-only bug fixes from the review were applied.

Fourth, focused tests were added for the new seam and the bug fixes, then the required repository gates were run and passed.

## Concrete Steps

From `/Users/barry/.codex/worktrees/456b/hyperopen`, the commands actually run were:

    npm run check
    npm ci
    npm run check
    npm test
    npm run test:websocket

The first `npm run check` exposed the missing local npm dependencies. After `npm ci`, the second `npm run check`, `npm test`, and `npm run test:websocket` all completed successfully.

## Validation and Acceptance

Acceptance is behavioral.

On desktop, opening a funding modal with a complete anchor should still render the anchored desktop surface at the expected width and alignment. If the stored anchor is missing, the modal should still fall back to the shared funding-action selectors and the trade order-entry divider selector. On mobile-width viewports, the send, deposit, transfer, and withdraw modes should still render as bottom sheets.

Within the rendered content, the deposit-address step must expose its own `data-role`, decimal inputs must emit `:inputmode "decimal"`, the deposit minimum and quick-amount buttons must disable when submit is already in flight, and an unrecognized content kind must render a visible error/fallback panel instead of returning `nil`. The visible close button must include an accessible label.

This work is complete. Those behaviors are now covered by tests, and `npm run check`, `npm test`, and `npm run test:websocket` all passed after restoring local npm dependencies with `npm ci`.

## Idempotence and Recovery

The implementation followed the planned safe order: add the helper seam first, switch callers to shared constants, rewire the modal view, then delete the old view-local layout helpers. The only recovery step needed during execution was `npm ci` to restore `node_modules` before rerunning the validation gates. No data migrations, persisted state changes, or remote operations were involved.

## Artifacts and Notes

Important current hotspots:

- `/hyperopen/src/hyperopen/views/funding_modal.cljs`
- `/hyperopen/src/hyperopen/views/account_equity_view.cljs`
- `/hyperopen/src/hyperopen/views/trade_view.cljs`
- `/hyperopen/test/hyperopen/views/funding_modal_test.cljs`

Important artifacts added during implementation:

- `/hyperopen/src/hyperopen/views/ui/funding_modal_positioning.cljs`
- `/hyperopen/test/hyperopen/views/ui/funding_modal_positioning_test.cljs`

The helper seam should stay presentation-only. It may know about viewport size, DOM rectangles, selectors, and popover width estimates, but it must not know about deposit routes, fee estimates, or lifecycle state transitions.

## Interfaces and Dependencies

The new helper namespace should expose stable, presentation-focused entry points. A suitable final surface is one public function that receives the modal map and returns a resolved layout map containing:

- `:anchor`, the best available anchor map after stored-anchor fallback and order-entry alignment.
- `:mobile-sheet?`, a boolean that says whether to render the bottom-sheet variant.
- `:anchored-popover?`, a boolean that says whether anchored desktop positioning is available.
- `:popover-style`, the computed style map for the anchored desktop surface when available.
- `:sheet-style`, the computed style map for the mobile sheet when applicable.

The helper namespace should also expose shared identifier constants for the funding action buttons and the trade order-entry panel so the modal view and the DOM owners can agree on the same anchor hooks.

Revision note: created this plan on 2026-03-12 after reading the governing planning, work-tracking, and UI policy docs, auditing the funding modal implementation, and creating `hyperopen-v9gb` for the scoped refactor.
Revision note: updated this plan on 2026-03-12 after implementing the new funding modal positioning seam, fixing the view regressions, regenerating the test runner, restoring missing npm dependencies with `npm ci`, passing all required validation gates, and filing `hyperopen-tyy8` for the intentionally deferred follow-up work.
Revision note: updated this plan again on 2026-03-12 after running headed browser-inspection QA for the desktop anchored-popover and mobile-sheet funding flows and recording the artifact paths plus caveats in a QA note.
