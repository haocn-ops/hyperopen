# HyperUnit Withdrawal Queue Lifecycle Parity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md` and builds on the completed milestones in `/hyperopen/docs/exec-plans/completed/2026-03-02-hyperunit-deposit-withdrawal-compliance.md`.

## Purpose / Big Picture

After this change, Unit-asset withdrawals in `/trade` will include live queue metadata from HyperUnit `GET /withdrawal-queue`, not only operation-scoped `positionInWithdrawQueue`. Users will see current queue length and queue health while withdrawal lifecycle updates are running, matching HyperUnit lifecycle expectations more closely.

Contributors can verify success by opening the withdraw modal for a Unit asset, submitting a withdrawal, and seeing queue metadata update in the modal while lifecycle polling runs.

## Progress

- [x] (2026-03-02 20:57Z) Created Milestone 7 ExecPlan and scoped queue-integration work across actions/effects/view/runtime/contracts/tests.
- [x] (2026-03-02 21:34Z) Implemented withdraw queue state model in funding modal defaults/normalization/view-model and action-triggered fetch emissions.
- [x] (2026-03-02 21:39Z) Added `api-fetch-hyperunit-withdrawal-queue!`, runtime effect wiring/contracts, and integrated queue refresh into withdraw lifecycle polling.
- [x] (2026-03-02 21:42Z) Rendered queue metadata in withdraw UI (queue length + latest queue tx id + loading/error copy) and added regression coverage in funding/core/default tests.
- [x] (2026-03-02 21:58Z) Ran required gates (`npm run check`, `npm test`, `npm run test:websocket`) successfully.

## Surprises & Discoveries

- Observation: Current withdraw lifecycle relies solely on `operations` data and does not call `withdrawal-queue`.
  Evidence: `/hyperopen/src/hyperopen/funding/effects.cljs` currently wires `request-hyperunit-operations!` into lifecycle polling without queue endpoint calls.

## Decision Log

- Decision: Keep queue state separate from `:hyperunit-lifecycle` and model it as dedicated modal state.
  Rationale: Queue endpoint is chain-level status and can be updated independently from operation lifecycle records.
  Date/Author: 2026-03-02 / Codex

- Decision: Refresh queue state during withdraw lifecycle polling without re-entering `:loading`.
  Rationale: Avoids flicker/regression in queue rows while still keeping metadata fresh on each lifecycle poll cycle.
  Date/Author: 2026-03-02 / Codex

## Outcomes & Retrospective

Milestone 7 delivered queue parity for Unit withdrawals:

- New effect `:effects/api-fetch-hyperunit-withdrawal-queue` is now registered end-to-end (actions -> runtime -> contracts -> effect adapter -> funding effect).
- Withdraw modal queue state is normalized and visible in UI (`Withdrawal queue`, `Last queue tx`) with loading and non-blocking error copy.
- Withdraw lifecycle polling now refreshes queue metadata each cycle for active Unit withdraw modal sessions.
- Regression tests now cover:
  - withdraw-open + asset-change queue-fetch effect emissions,
  - queue effect success/failure state transitions,
  - queue refresh integration with HyperUnit withdraw polling,
  - default/compat modal shape updates.

Validation completed:

- `npm test` passed (2026-03-02).
- `npm run check` passed (2026-03-02).
- `npm run test:websocket` passed (2026-03-02).

## Context and Orientation

The funding modal state is owned by `/hyperopen/src/hyperopen/funding/actions.cljs`. Runtime side effects are in `/hyperopen/src/hyperopen/funding/effects.cljs` and are wired through adapters (`/hyperopen/src/hyperopen/runtime/effect_adapters.cljs`), collaborator maps (`/hyperopen/src/hyperopen/runtime/collaborators.cljs`), registry composition (`/hyperopen/src/hyperopen/runtime/registry_composition.cljs`), runtime registry (`/hyperopen/src/hyperopen/registry/runtime.cljs`), and effect contracts (`/hyperopen/src/hyperopen/schema/contracts.cljs`).

HyperUnit queue normalization already exists in `/hyperopen/src/hyperopen/api/endpoints/funding_hyperunit.cljs` and is covered by `/hyperopen/test/hyperopen/api/endpoints/funding_hyperunit_test.cljs`.

UI rendering for withdraw lifecycle is in `/hyperopen/src/hyperopen/views/funding_modal.cljs`. Existing tests relevant to this flow are in:

- `/hyperopen/test/hyperopen/funding/actions_test.cljs`
- `/hyperopen/test/hyperopen/funding/effects_test.cljs`
- `/hyperopen/test/hyperopen/core_public_actions_test.cljs`
- `/hyperopen/test/hyperopen/state/app_defaults_test.cljs`

## Plan of Work

Introduce a new normalized modal state map `:hyperunit-withdrawal-queue` with status, chain data, timestamps, and error fields. Add normalization helpers and expose queue-derived fields in `funding-modal-view-model`.

Implement a new funding effect `api-fetch-hyperunit-withdrawal-queue!` that loads queue data from HyperUnit and updates modal queue state with loading/ready/error transitions. Wire a runtime effect ID for this function.

Trigger queue fetches for withdraw mode on modal open, on withdraw-asset selection change, and while withdraw lifecycle polling runs so queue metadata remains fresh during active lifecycle updates.

Render queue metadata in withdraw UI (queue length and latest queue tx id when available), plus loading/error fallback copy.

Extend tests to cover new state defaults, action-triggered effect emissions, queue effect success/failure behavior, and compat fixture updates.

## Concrete Steps

From `/Users//.codex/worktrees/b69c/hyperopen`:

1. Edit funding state and actions:
   - `src/hyperopen/funding/actions.cljs`
   - `src/hyperopen/views/funding_modal.cljs`

2. Edit funding effects and runtime wiring:
   - `src/hyperopen/funding/effects.cljs`
   - `src/hyperopen/runtime/effect_adapters.cljs`
   - `src/hyperopen/runtime/collaborators.cljs`
   - `src/hyperopen/runtime/registry_composition.cljs`
   - `src/hyperopen/registry/runtime.cljs`
   - `src/hyperopen/app/effects.cljs`
   - `src/hyperopen/schema/contracts.cljs`

3. Update regression tests:
   - `test/hyperopen/funding/actions_test.cljs`
   - `test/hyperopen/funding/effects_test.cljs`
   - `test/hyperopen/core_public_actions_test.cljs`
   - `test/hyperopen/state/app_defaults_test.cljs`

4. Run validation:
   - `npm test`
   - `npm run check`
   - `npm run test:websocket`

## Validation and Acceptance

Acceptance criteria:

1. Opening withdraw modal emits queue-fetch effect and queue state enters loading/ready or error.
2. Withdraw lifecycle polling refreshes queue metadata while modal remains active.
3. Withdraw UI displays queue length when available and presents readable loading/error fallback text.
4. Default app state and compat tests include the new queue state shape.
5. Required gates pass.

## Idempotence and Recovery

All changes are additive and safe to re-run. Queue fetch failures do not block submit or lifecycle polling; they only set queue error metadata while preserving existing withdraw behavior.

## Artifacts and Notes

Key API behavior source:

- `GET /withdrawal-queue` normalization coverage in `/hyperopen/test/hyperopen/api/endpoints/funding_hyperunit_test.cljs`.

## Interfaces and Dependencies

Expected additions:

- Funding actions state helper:
  - `default-hyperunit-withdrawal-queue-state`
  - `normalize-hyperunit-withdrawal-queue`

- Funding effects:
  - `api-fetch-hyperunit-withdrawal-queue!`

- Runtime effect ID:
  - `:effects/api-fetch-hyperunit-withdrawal-queue`

Revision note (2026-03-02): Created Milestone 7 plan to integrate HyperUnit withdrawal queue endpoint into active withdraw lifecycle UX and runtime behavior.
