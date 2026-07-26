# Restore withdraw modal parity and correct withdrawable asset balances

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the withdraw flow on `/trade` will match the same two-step interaction pattern users already see in the deposit modal: first a searchable asset picker that shows how much of each asset is actually withdrawable, then an amount-entry screen for the selected asset. The functional goal is to stop showing fake zeros for assets that are withdrawable, especially USDC in unified-account contexts, and to make the withdraw UI visibly trustworthy and consistent with the deposit flow.

The user-visible proof is direct. Open the withdraw modal from the account equity panel, verify the first screen lists assets with withdrawable amounts, pick an asset, and confirm the second screen shows the selected asset card plus an amount input whose MAX and available text match the account’s actual withdrawable balance.

## Progress

- [x] (2026-03-07 23:18Z) Re-read `/hyperopen/AGENTS.md`, `/hyperopen/ARCHITECTURE.md`, `/hyperopen/docs/WORK_TRACKING.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, `/hyperopen/docs/agent-guides/trading-ui-policy.md`, and `/hyperopen/.agents/PLANS.md`.
- [x] (2026-03-07 23:24Z) Audited the current funding modal implementation in `/hyperopen/src/hyperopen/funding/domain/policy.cljs`, `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs`, `/hyperopen/src/hyperopen/funding/application/modal_commands.cljs`, and `/hyperopen/src/hyperopen/views/funding_modal.cljs`.
- [x] (2026-03-07 23:29Z) Created and claimed `bd` issue `hyperopen-q33` for this bug.
- [x] (2026-03-07 23:33Z) Authored this ExecPlan with the confirmed root-cause direction and implementation steps.
- [x] (2026-03-07 23:44Z) Implemented unified-account-aware withdraw availability and exposed per-asset withdrawable amounts in the withdraw asset catalog and view-model.
- [x] (2026-03-07 23:49Z) Converted withdraw from a one-screen select form to a two-step searchable asset-picker plus amount-entry flow, including a safer destination reset on asset change.
- [x] (2026-03-07 23:53Z) Added and updated regression tests for unified USDC availability precedence, withdraw step transitions, withdraw asset search, withdraw select-step rendering, and withdraw detail rendering.
- [x] (2026-03-07 23:59Z) Installed missing npm dependencies in the worktree, then ran the required validation gates: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-03-08 00:01Z) Closed `hyperopen-q33`, moved this plan to `/hyperopen/docs/exec-plans/completed/`, and recorded the final outcome.

## Surprises & Discoveries

- Observation: the current withdraw UI does not have its own step model at all; it jumps directly into a detail form with a `<select>` control.
  Evidence: `/hyperopen/src/hyperopen/funding/domain/modal_state.cljs` defines `:deposit-step` but no withdraw step, and `/hyperopen/src/hyperopen/views/funding_modal.cljs` renders `withdraw-content` directly for `:withdraw/form`.

- Observation: USDC withdraw availability currently ignores unified-account spot availability and uses a narrower perps-only helper.
  Evidence: `/hyperopen/src/hyperopen/funding/domain/policy.cljs` `withdraw-max-amount` returns `perps-withdrawable` when the selected asset key is `:usdc`, while `hyperopen.domain.trading.market/available-to-trade` and `hyperopen.account.history.position-margin/available-to-add` explicitly prefer unified spot USDC availability first.

- Observation: the existing `perps-withdrawable` helper is less defensive than similar account-surface logic elsewhere in the repo.
  Evidence: it only inspects top-level `[:webdata2 :clearinghouseState]` fields, while `/hyperopen/src/hyperopen/account/history/position_margin.cljs` checks both root and nested `:clearinghouseState` candidates plus summary fallbacks.

- Observation: reusing a previously entered withdraw destination across asset switches is unsafe because the current HyperUnit destination validation only checks for non-blank text.
  Evidence: before this change, selecting BTC after USDC preserved the prior destination string in `/hyperopen/src/hyperopen/funding/application/modal_commands.cljs`; `withdraw-preview` in `/hyperopen/src/hyperopen/funding/domain/policy.cljs` accepts any non-blank destination for `:hyperunit-address` flows.

- Observation: the worktree did not have `node_modules`, so `npm test` initially failed before application code was exercised.
  Evidence: the first targeted test attempt failed with `sh: shadow-cljs: command not found`, then the compiled runner failed with `Cannot find module '@noble/secp256k1'` until `npm install` completed.

## Decision Log

- Decision: add dedicated withdraw-step state instead of overloading `:deposit-step`.
  Rationale: deposit and withdraw now have different first-step selection behavior, and separate fields keep transitions explicit and deterministic.
  Date/Author: 2026-03-07 / Codex

- Decision: compute withdrawable amounts in funding domain policy and attach them to the withdraw asset catalog before rendering.
  Rationale: both validation and UI need the same source of truth for per-asset availability, so the amount must be derived once in pure policy code rather than separately in the view.
  Date/Author: 2026-03-07 / Codex

- Decision: make unified-account USDC availability prefer spot availability before falling back to broader clearinghouse withdrawable fields.
  Rationale: this matches how other account-surface features interpret unified-account available balance and addresses the user-reported false-zero case.
  Date/Author: 2026-03-07 / Codex

- Decision: reset the withdraw destination when the user changes the selected asset.
  Rationale: a previously entered EVM address should not silently carry into a non-EVM withdraw flow, especially because current HyperUnit destination validation is intentionally shallow.
  Date/Author: 2026-03-07 / Codex

## Outcomes & Retrospective

Implementation is complete.

Delivered behavior:

- The withdraw modal now opens on a searchable asset picker that mirrors the deposit first step and shows each supported asset’s current withdrawable amount.
- Selecting an asset advances to a dedicated withdraw detail step with a selected-asset card, amount input, MAX action, USDC quick-amount chips, summary rows, and the existing HyperUnit queue/protocol/lifecycle detail blocks.
- Unified-account USDC now prefers actual spot availability before falling back to broader clearinghouse withdrawable fields, which fixes the false-zero case reported by the user.
- Withdraw asset changes now reset amount and destination state so addresses do not carry across incompatible networks.

Validation evidence:

- `npm run check` passed.
- `npm test` passed.
- `npm run test:websocket` passed.

No follow-up issue was required for this user request. The remaining known risk is that HyperUnit address validation is still network-agnostic string validation, but the new asset-change reset removes the most dangerous stale-address path from this workflow.

## Context and Orientation

The funding modal view is rendered by `/hyperopen/src/hyperopen/views/funding_modal.cljs`. It consumes a normalized view-model from `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs`, which in turn depends on modal state defaults from `/hyperopen/src/hyperopen/funding/domain/modal_state.cljs`, state transitions from `/hyperopen/src/hyperopen/funding/application/modal_commands.cljs`, and balance/validation policy from `/hyperopen/src/hyperopen/funding/domain/policy.cljs` plus `/hyperopen/src/hyperopen/funding/domain/assets.cljs`.

In the current implementation, deposit has two explicit steps (`:asset-select` and `:amount-entry`) but withdraw does not. Withdraw renders one form that combines asset selection, destination entry, amount entry, queue/lifecycle details, and submit. That makes parity with the deposit modal impossible and also hides per-asset availability from the first interaction step.

The current balance bug centers on the withdraw policy. `withdraw-max-amount` uses `perps-withdrawable` for USDC and spot balances for non-USDC assets. That is too narrow for unified accounts, where the app already treats spot USDC availability as the user’s canonical available balance in other flows. The fix must stay pure and deterministic because funding policy is part of the domain/application boundary, not an effect layer.

## Plan of Work

Milestone 1 fixes the balance source. Update `/hyperopen/src/hyperopen/funding/domain/policy.cljs` so the withdraw policy has explicit helpers for unified-account detection, robust clearinghouse withdrawable fallback, and per-asset availability calculation. Add a withdraw-asset enrichment helper that returns each supported asset together with its withdrawable amount and display text, and make `withdraw-max-amount` read from that same source of truth.

Milestone 2 adds withdraw workflow state. Update `/hyperopen/src/hyperopen/funding/domain/modal_state.cljs` and `/hyperopen/src/hyperopen/funding/application/modal_commands.cljs` to introduce a withdraw step and withdraw search input. Opening withdraw should land on asset selection. Selecting an asset should reset amount and lifecycle-sensitive state, then advance to amount entry. A back action should return to the withdraw asset picker without closing the modal.

Milestone 3 reshapes the view-model and UI. Update `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs` so withdraw can render either an asset-select step or an amount-entry step. The asset-select step should expose a searchable list of assets with symbol, network, and withdrawable amount display. The amount-entry step should keep the existing queue, protocol-address, and lifecycle details, but its visual framing should mirror the deposit amount-entry layout more closely.

Milestone 4 adds regression coverage. Extend funding-domain, modal-view-model, action, and funding-modal view tests to verify unified USDC uses the correct availability precedence, withdraw asset rows show amounts, withdraw starts on the asset picker, selecting an asset advances to amount entry, and the amount-entry screen shows the selected asset card plus correct MAX and available copy.

Milestone 5 validates and lands the work. Run the required quality gates, update this plan with the actual results, close `hyperopen-q33`, and move the plan to `/hyperopen/docs/exec-plans/completed/`.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/funding/domain/policy.cljs` to add robust withdrawable helpers and per-asset availability enrichment.
2. Edit `/hyperopen/src/hyperopen/funding/domain/modal_state.cljs` and `/hyperopen/src/hyperopen/funding/application/modal_commands.cljs` to add withdraw step/search state and transitions.
3. Edit `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs` to expose withdraw select-step and detail-step models with asset amount display.
4. Edit `/hyperopen/src/hyperopen/views/funding_modal.cljs` to replace the single-screen withdraw form with a deposit-parity asset picker plus detail view.
5. Update focused tests under:
   - `/hyperopen/test/hyperopen/funding/actions_test.cljs`
   - `/hyperopen/test/hyperopen/funding/application/modal_vm_test.cljs`
   - `/hyperopen/test/hyperopen/views/funding_modal_test.cljs`
   - any new or existing funding policy tests if needed
6. Run:

       npm run check
       npm test
       npm run test:websocket

## Validation and Acceptance

This issue is complete when all of the following are true:

1. Opening withdraw first shows a searchable asset list, not the full amount-entry form.
2. Each withdrawable asset row shows the current withdrawable amount for that asset.
3. In unified-account contexts, USDC availability no longer collapses to zero when spot USDC is withdrawable.
4. Selecting an asset opens a second-step withdraw detail view with the asset card, amount input, quick parity styling, and correct MAX/available values.
5. HyperUnit-specific queue, protocol-address, and lifecycle details still render on the withdraw detail step when relevant.
6. `npm run check`, `npm test`, and `npm run test:websocket` pass.

## Idempotence and Recovery

These changes are source-only and safe to re-run. If the withdraw redesign introduces a behavioral regression, keep the new withdraw-step state but temporarily route `:withdraw/detail` back through the old summary blocks until tests pass again. If the balance-source refactor breaks validation, compare against `/hyperopen/src/hyperopen/account/history/position_margin.cljs`, which already has the more defensive unified-account precedence we want to match.

## Artifacts and Notes

Relevant issue:

- `hyperopen-q33` — "Restore withdraw modal parity and correct withdrawable asset balances"

Relevant current hotspots for this bug:

- `/hyperopen/src/hyperopen/funding/domain/policy.cljs` — USDC withdraw availability source
- `/hyperopen/src/hyperopen/funding/domain/modal_state.cljs` — no withdraw step/search state
- `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs` — withdraw view-model only supports one form step
- `/hyperopen/src/hyperopen/views/funding_modal.cljs` — withdraw UI is a one-screen form with a `<select>`

## Interfaces and Dependencies

No new third-party dependency is required.

At completion, the funding modal public entry point remains:

    (hyperopen.views.funding-modal/funding-modal-view state)

Expected internal additions or updates include:

    (hyperopen.funding.domain.policy/withdraw-assets-with-balances state)
    (hyperopen.funding.application.modal-commands/return-to-funding-withdraw-asset-select deps state)
    (hyperopen.funding.application.modal-vm/funding-modal-view-model deps state)

The exact helper names may differ, but the final structure must preserve one pure source of truth for withdrawable amounts and one explicit state machine for withdraw-step transitions.

Plan revision note: 2026-03-07 23:33Z - Initial plan authored after repository audit, root-cause analysis, issue creation (`hyperopen-q33`), and implementation scoping for withdraw parity plus incorrect balance sourcing.
Plan revision note: 2026-03-08 00:01Z - Recorded the implemented balance-source fix, two-step withdraw redesign, npm dependency bootstrap needed for validation, and passing required gates before issue closure.
