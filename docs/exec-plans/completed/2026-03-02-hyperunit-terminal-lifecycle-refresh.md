# HyperUnit Terminal Lifecycle Account Refresh Fallback

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md` and follows completed queue parity work in `/hyperopen/docs/exec-plans/completed/2026-03-02-hyperunit-withdrawal-queue-lifecycle-parity.md`.

## Purpose / Big Picture

After this milestone, HyperUnit deposit/withdraw flows will trigger a deterministic `load-user-data` refresh once lifecycle polling reaches a terminal state. This provides a reconciliation fallback for balances and account summary even when websocket/account-stream updates are delayed.

Contributors can verify success by submitting a Unit deposit/withdraw in `/trade`, waiting for lifecycle terminal state, and observing one `:actions/load-user-data` dispatch.

## Progress

- [x] (2026-03-02 22:03Z) Created Milestone 8 ExecPlan and scoped implementation surfaces (funding effects + tests).
- [x] (2026-03-02 22:08Z) Added terminal lifecycle callback support to polling runtime (`on-terminal-lifecycle!`) and invoked it once on terminal state transition.
- [x] (2026-03-02 22:11Z) Wired callback from HyperUnit deposit/withdraw keep-modal-open submit flows to dispatch `:actions/load-user-data`.
- [x] (2026-03-02 22:16Z) Added regression tests covering terminal refresh dispatch behavior for both withdraw and deposit lifecycle completion paths.
- [x] (2026-03-02 22:22Z) Ran required gates (`npm run check`, `npm test`, `npm run test:websocket`) successfully.

## Surprises & Discoveries

- Observation: Keep-modal-open HyperUnit flows currently do not dispatch `:actions/load-user-data` when lifecycle reaches terminal state.
  Evidence: `/hyperopen/src/hyperopen/funding/effects.cljs` only calls `refresh-after-funding-submit!` in close-modal branches.

## Decision Log

- Decision: Use an optional `on-terminal-lifecycle!` callback in polling runtime rather than hard-coding refresh behavior into polling internals.
  Rationale: Preserves polling reuse and keeps terminal-side effects explicit at call sites.
  Date/Author: 2026-03-02 / Codex

## Outcomes & Retrospective

Milestone 8 is complete.

Delivered behavior:

- HyperUnit polling now accepts `on-terminal-lifecycle!` callback and executes it once when lifecycle reaches terminal state.
- HyperUnit deposit and withdraw keep-modal-open flows now dispatch `:actions/load-user-data <wallet-address>` on terminal lifecycle completion.
- Existing submit-path behavior is preserved (no modal close side effects changed), while adding a deterministic reconciliation fallback.

Regression coverage:

- `api-submit-funding-withdraw-hyperunit-send-asset-polls-and-updates-lifecycle-test` now verifies terminal refresh dispatch.
- Added `api-submit-funding-deposit-hyperunit-address-terminal-lifecycle-refreshes-user-data-test`.

Validation:

- `npm test` passed (2026-03-02).
- `npm run check` passed (2026-03-02).
- `npm run test:websocket` passed (2026-03-02).
