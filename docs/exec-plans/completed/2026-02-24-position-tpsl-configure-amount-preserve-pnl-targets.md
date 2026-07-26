# Position TP/SL Configure Amount Should Preserve Gain/Loss Targets

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, when a user adjusts `Configure Amount` in the Position TP/SL modal, Hyperopen will preserve the Gain/Loss input target (USD or %) and recompute TP/SL trigger prices from the new configured size. This matches the expected behavior shown in TradeXYZ and Hyperliquid style UX where Gain/Loss stays stable while Expected profit/Expected loss changes with configured amount.

A user can verify this by setting Gain to `1 $`, then lowering configured amount from `100%` to `80%`: Gain stays `1 $`, TP price moves further from entry, and Expected profit percent increases.

## Progress

- [x] (2026-02-24 18:49Z) Captured behavior mismatch and authored this execution plan.
- [x] (2026-02-24 19:36Z) Implemented TP/SL price recomputation on configure-size changes while preserving mode-specific Gain/Loss targets.
- [x] (2026-02-24 19:40Z) Added regression tests for USD and percent mode behavior when size changes, plus a modal-size display parity test.
- [x] (2026-02-24 19:50Z) Ran required validation gates: `npm run check`, `npm test`, `npm run test:websocket`.

## Surprises & Discoveries

- Observation: Existing implementation recomputes TP/SL from Gain/Loss input on direct Gain/Loss edits only; changing configured size does not re-run that mapping.
  Evidence: `set-modal-field` in `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` updates `:tp-price`/`:sl-price` for `[:tp-gain]` and `[:sl-loss]`, but size paths only mutate size fields.

- Observation: The modal "Size" metric rendered `active-size` instead of full `position-size`, which made the top summary drift as configure amount changed.
  Evidence: In `/hyperopen/src/hyperopen/views/account_info/position_tpsl_modal.cljs`, the metric row previously used `(position-tpsl/active-size modal*)`.

## Decision Log

- Decision: Preserve target according to active input mode (`:usd` preserves nominal pnl, `:percent` preserves percent pnl) whenever active size changes.
  Rationale: This mirrors expected UX semantics: user’s chosen input field remains authoritative.
  Date/Author: 2026-02-24 / Codex

- Decision: Reprice from preserved targets on both direct size edits and configure-amount toggle transitions.
  Rationale: Toggling configure amount changes active order size and must keep Gain/Loss intent stable just like slider/text edits.
  Date/Author: 2026-02-24 / Codex

- Decision: Keep the top "Size" metric bound to `:position-size` (full position), not configured order size.
  Rationale: This matches TradeXYZ/Hyperliquid behavior and preserves stable position context in the summary area.
  Date/Author: 2026-02-24 / Codex

## Outcomes & Retrospective

Implementation completed and validated. Configure amount changes now preserve Gain/Loss targets by active mode and recompute TP/SL trigger prices from the new configured size. In USD mode this keeps nominal Gain/Loss constant while Expected profit/Expected loss percent changes with size; in percent mode it preserves percent target behavior. Modal summary parity was also corrected so top "Size" remains full position size while configure amount changes only affect order sizing controls and expectation math.

## Context and Orientation

TP/SL modal translation logic lives in `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs`. Configure amount controls update `:size-input` / `:size-percent-input` through `set-modal-field`. Gain/Loss-to-price mapping currently exists via `pnl-input->price-text` and `pnl-percent-input->price-text`.

The missing link is recomputing prices when size changes.

## Plan of Work

Milestone 1 updates modal domain behavior:

- Add a helper to capture current mode-specific TP/SL targets from the pre-change modal.
- Add a helper to re-resolve TP/SL trigger prices on the post-size-change modal using preserved targets.
- Apply this helper in size-edit paths and configure-amount toggle paths.

Milestone 2 adds tests:

- Add regression tests to assert:
  - USD mode: changing size keeps Gain USD constant and increases percent expectation as size decreases.
  - Percent mode: changing size keeps Gain percent constant.

Milestone 3 validates with required gates.

## Concrete Steps

1. Patch `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` and run tests.

   cd /hyperopen
   npm test -- hyperopen.account.history.position-tpsl-test

2. Run full required gates.

   cd /hyperopen
   npm run check
   npm test
   npm run test:websocket

## Validation and Acceptance

Acceptance is complete when:

1. In USD mode, changing configured amount preserves Gain/Loss USD values.
2. In percent mode, changing configured amount preserves Gain/Loss percent values.
3. Expected profit percent increases when configured size decreases while USD gain is fixed.
4. Required validation gates pass.

## Idempotence and Recovery

Changes are additive in modal translation logic and can be rolled back by removing only the size-change preservation helper wiring if needed.

## Artifacts and Notes

Relevant files:

- `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs`
- `/hyperopen/src/hyperopen/views/account_info/position_tpsl_modal.cljs`
- `/hyperopen/test/hyperopen/account/history/position_tpsl_test.cljs`
- `/hyperopen/test/hyperopen/views/account_info/position_tpsl_modal_test.cljs`

Validation evidence:

- `npm run check` passed.
- `npm test` passed (1277 tests, 6005 assertions, 0 failures).
- `npm run test:websocket` passed (147 tests, 643 assertions, 0 failures).

Plan revision note: 2026-02-24 18:49Z - Initial plan authored before implementation.
Plan revision note: 2026-02-24 19:50Z - Marked implementation complete, added discoveries/decisions, and recorded validation outcomes.

## Interfaces and Dependencies

No new external dependencies. No action IDs or request contracts change.
