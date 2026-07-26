# Mobile Balances Send Modal

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`.

## Purpose / Big Picture

After this change, tapping `Send` from a balance card or balance row opens a real `Send Tokens` flow instead of doing nothing. On phone-sized trade views, a user can expand a balance card, tap `Send`, and immediately see a mobile-friendly send sheet with destination, account, asset, amount, and max-balance affordances backed by the existing `sendAsset` API path.

The behavior is observable by opening `/hyperopen/trade?spectate=<address>` in an iPhone 14 Pro Max viewport, expanding a sendable balance card, and tapping `Send`. A `Send Tokens` modal should appear with `Trading Account`, the clicked asset, a destination field, a max label, and a working submit path.

## Progress

- [x] (2026-03-08 20:05Z) Audited the balances-tab action wiring and confirmed that `Send` was presentational only in `/hyperopen/src/hyperopen/views/account_info/tabs/balances.cljs`.
- [x] (2026-03-08 20:09Z) Confirmed that the funding modal only supported `deposit`, `transfer`, and `withdraw`, while the low-level `sendAsset` submit path already existed in `/hyperopen/src/hyperopen/api/trading.cljs`.
- [x] (2026-03-08 20:14Z) Claimed tracking issue `hyperopen-p29`.
- [x] (2026-03-08 20:27Z) Added a dedicated funding `send` mode, submit action/effect plumbing, and send-preview validation.
- [x] (2026-03-08 20:33Z) Wired desktop/mobile balances `Send` actions to open the new modal with clicked-asset context and disabled the synthetic perps row from opening the flow.
- [x] (2026-03-08 20:37Z) Added regression coverage across actions, effects, balances view wiring, funding modal rendering, and runtime effect-adapter exposure.
- [x] (2026-03-08 20:41Z) Passed `npm test`, `npm run check`, and `npm run test:websocket`.
- [x] (2026-03-08 20:45Z) Completed iPhone 14 Pro Max browser QA with a populated spectate account and captured artifact `/hyperopen/tmp/browser-inspection/inspect-2026-03-08T20-45-23-381Z-70fc474f/`.

## Surprises & Discoveries

- Observation: The balances `Send` affordance was non-functional in both desktop rows and mobile cards; there was no dormant click wiring to reactivate.
  Evidence: `/hyperopen/src/hyperopen/views/account_info/tabs/balances.cljs` rendered `Send` as a button/span without any `:on` dispatch.

- Observation: The existing funding modal’s legacy `:send` compatibility path incorrectly routed to the spot/perps transfer workflow.
  Evidence: `/hyperopen/src/hyperopen/funding/application/modal_commands.cljs` previously mapped `:send` to `open-funding-transfer-modal-fn`.

- Observation: The mobile send UI needed its own phone-sheet presentation even though the funding modal already supported anchored popovers.
  Evidence: Balances `Send` originates from mobile cards in the trade account panel; the new live QA artifact shows the bottom sheet pattern under the iPhone viewport instead of an anchor-bound desktop popover.

## Decision Log

- Decision: Add a dedicated funding `send` mode and submit effect instead of overloading the existing withdraw workflow.
  Rationale: `sendAsset` is a different action type with different validation, copy, and success/error messaging. A dedicated mode keeps the funding architecture honest and avoids misleading `withdraw` naming throughout the runtime.
  Date/Author: 2026-03-08 / Codex

- Decision: Preload the send modal from the clicked balance row rather than deriving a fresh asset catalog inside the modal.
  Rationale: The balances surface already knows which token, namespace chip, and available balance the user selected. Reusing that row context keeps the flow fast and avoids introducing a second asset-selection problem for this fix.
  Date/Author: 2026-03-08 / Codex

- Decision: Disable `Send` for the synthetic perps-only USDC row.
  Rationale: That row does not represent a spot token balance that can back a `sendAsset` request. Exposing the modal there would produce a flow that can only fail.
  Date/Author: 2026-03-08 / Codex

- Decision: Render the send modal as a mobile bottom sheet in small viewports while leaving the existing funding modal anchor behavior intact for non-send flows.
  Rationale: The user request is phone-specific and matches the repo’s established mobile sheet pattern for other account-panel overlays.
  Date/Author: 2026-03-08 / Codex

## Outcomes & Retrospective

The balances `Send` affordance now works end to end. The funding runtime gained a proper `send` mode, a `submit-funding-send` action, and an `api-submit-funding-send` effect that submits the existing `sendAsset` action and reuses the established toast and post-submit refresh behavior.

The view-side change stayed local. `/hyperopen/src/hyperopen/views/account_info/tabs/balances.cljs` now opens the modal from sendable spot rows/cards, while `/hyperopen/src/hyperopen/views/funding_modal.cljs` renders the `Send Tokens` UI and uses a phone sheet when the viewport is mobile.

Manual QA verified the user-visible goal. In the iPhone 14 Pro Max viewport against spectate address `0x162cc7c861ebd0c06b3d72319201150482518185`, expanding the `spot-0` USDC balance card and tapping `Send` opened a `Send Tokens` sheet with a destination input, `Trading Account`, `USDC`, a populated max label, and a submit button. Evidence is recorded in `/hyperopen/docs/qa/mobile-balances-send-modal-qa-2026-03-08.md`.

## Context and Orientation

The balances tab renderer lives in `/hyperopen/src/hyperopen/views/account_info/tabs/balances.cljs`. That file decides which actions the user can take from both the desktop table rows and the mobile expandable cards.

The funding modal runtime is split across four areas:

- `/hyperopen/src/hyperopen/funding/domain/modal_state.cljs` defines the stored modal shape.
- `/hyperopen/src/hyperopen/funding/domain/policy.cljs` validates funding actions and produces request payload previews.
- `/hyperopen/src/hyperopen/funding/application/modal_commands.cljs` and `/hyperopen/src/hyperopen/funding/application/modal_actions.cljs` open, edit, and submit the modal.
- `/hyperopen/src/hyperopen/views/funding_modal.cljs` renders the modal UI.

The submit side then runs through `/hyperopen/src/hyperopen/funding/application/submit_effects.cljs`, `/hyperopen/src/hyperopen/funding/effects.cljs`, and the runtime registration files under `/hyperopen/src/hyperopen/runtime/` and `/hyperopen/src/hyperopen/schema/`.

## Plan of Work

The first step is to extend the funding modal with a real `send` mode. Add send-specific modal state, send validation, a send open action that accepts clicked-balance context, and a submit action/effect that routes to the existing `submit-send-asset!` API path.

The second step is to wire the balances tab to that new mode. Desktop rows and mobile cards should dispatch `:actions/open-funding-send-modal` with the clicked token, namespace chip, and available balance. The synthetic perps-only row must not open the flow.

The third step is to render the modal. Add `Send Tokens` content to `/hyperopen/src/hyperopen/views/funding_modal.cljs`, use a phone-sheet presentation for small viewports, and keep the current funding modal behavior intact for the other modes.

The fourth step is regression protection. Add tests for action/effect plumbing, balances wiring, and send-modal rendering, then run the required gates and a browser-based mobile verification pass.

## Concrete Steps

Work from `/hyperopen`.

1. Add send-mode modal state and validation in:
   - `/hyperopen/src/hyperopen/funding/domain/modal_state.cljs`
   - `/hyperopen/src/hyperopen/funding/domain/policy.cljs`

2. Add action/command plumbing in:
   - `/hyperopen/src/hyperopen/funding/application/modal_commands.cljs`
   - `/hyperopen/src/hyperopen/funding/application/modal_actions.cljs`
   - `/hyperopen/src/hyperopen/funding/actions.cljs`

3. Add submit effect wiring in:
   - `/hyperopen/src/hyperopen/funding/application/submit_effects.cljs`
   - `/hyperopen/src/hyperopen/funding/effects.cljs`
   - `/hyperopen/src/hyperopen/runtime/effect_adapters/funding.cljs`
   - `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs`
   - `/hyperopen/src/hyperopen/app/effects.cljs`

4. Register the new action/effect ids in:
   - `/hyperopen/src/hyperopen/runtime/collaborators.cljs`
   - `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`
   - `/hyperopen/src/hyperopen/schema/contracts.cljs`
   - `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`

5. Wire balances actions and modal rendering in:
   - `/hyperopen/src/hyperopen/views/account_info/tabs/balances.cljs`
   - `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs`
   - `/hyperopen/src/hyperopen/views/funding_modal.cljs`

6. Add regression coverage in:
   - `/hyperopen/test/hyperopen/funding/actions_test.cljs`
   - `/hyperopen/test/hyperopen/funding/effects_test.cljs`
   - `/hyperopen/test/hyperopen/views/funding_modal_test.cljs`
   - `/hyperopen/test/hyperopen/views/account_info/tabs/balances_test.cljs`
   - `/hyperopen/test/hyperopen/runtime/effect_adapters/facade_contract_test.cljs`
   - `/hyperopen/test/hyperopen/runtime/effect_adapters/funding_test.cljs`
   - `/hyperopen/test/hyperopen/core_public_actions_test.cljs`

7. Run validation:
   - `npm test`
   - `npm run check`
   - `npm run test:websocket`

8. Run iPhone 14 Pro Max browser QA against a populated spectate route and capture screenshot evidence.

## Validation and Acceptance

Acceptance requires:

- `npm test`, `npm run check`, and `npm run test:websocket` pass.
- On a phone viewport, the balances `Send` action opens a `Send Tokens` modal instead of doing nothing.
- The modal shows destination, account, asset, amount, and max-balance affordances.
- The clicked asset and available balance are preloaded into the modal.
- The synthetic perps-only row does not incorrectly expose the send flow.

## Idempotence and Recovery

The runtime additions are additive and safe to rerun. If the new send path regresses, the lowest-risk recovery is to leave the funding modal `send` mode in place and temporarily remove the balances-side dispatches until the issue is fixed; that preserves the new architecture without leaving the user with a broken clickable control.

Browser-inspection captures are also safe to rerun; they only create new timestamped directories under `/hyperopen/tmp/browser-inspection/`.

## Artifacts and Notes

- Tracking issue: `hyperopen-p29`
- QA note: `/hyperopen/docs/qa/mobile-balances-send-modal-qa-2026-03-08.md`
- Browser artifact:
  - `/hyperopen/tmp/browser-inspection/inspect-2026-03-08T20-45-23-381Z-70fc474f/manifest.json`
  - `/hyperopen/tmp/browser-inspection/inspect-2026-03-08T20-45-23-381Z-70fc474f/local-send-modal-open/mobile/screenshot.png`
  - `/hyperopen/tmp/browser-inspection/inspect-2026-03-08T20-45-23-381Z-70fc474f/summary.json`

Plan revision note: 2026-03-08 20:46Z - Initial completed ExecPlan written after implementing the mobile balances send modal, passing required gates, and recording iPhone browser QA evidence.
