# Effect-Order Authority Contract For Deterministic UI Responsiveness

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, effect-order policy for interaction responsiveness is enforced by one explicit authority instead of scattered action-specific conventions. The authority contract will ensure user-visible projection effects (for example dropdown close, tab selection, UI error reset) happen before heavy side effects (subscriptions, fetches, reconnects, API calls) for designated interaction actions.

A contributor can verify the outcome by dispatching covered actions in tests and seeing deterministic ordering guarantees enforced centrally, with clear failures when an action emits heavy effects before immediate UI projection effects.

## Progress

- [x] (2026-02-25 20:05Z) Reviewed `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/RELIABILITY.md`, and `/hyperopen/docs/QUALITY_SCORE.md` for responsiveness and effect-order requirements.
- [x] (2026-02-25 20:05Z) Audited current action/effect paths in `/hyperopen/src/hyperopen/asset_selector/actions.cljs`, `/hyperopen/src/hyperopen/order/actions.cljs`, `/hyperopen/src/hyperopen/account/history/actions.cljs`, `/hyperopen/src/hyperopen/chart/actions.cljs`, `/hyperopen/src/hyperopen/orderbook/settings.cljs`, and `/hyperopen/src/hyperopen/websocket/diagnostics_actions.cljs`.
- [x] (2026-02-25 20:05Z) Audited runtime validation and contracts wiring in `/hyperopen/src/hyperopen/runtime/validation.cljs` and `/hyperopen/src/hyperopen/schema/contracts.cljs`.
- [x] (2026-02-25 20:05Z) Audited existing ordering-focused tests in `/hyperopen/test/hyperopen/core_bootstrap/*` and related action test suites.
- [x] (2026-02-25 20:05Z) Authored initial ExecPlan with centralized authority design, migration milestones, and validation gates.
- [x] (2026-02-25 20:11Z) Implemented Milestone 1 by adding `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs` with phase vocabulary and policy entries for all eight covered actions.
- [x] (2026-02-25 20:11Z) Implemented Milestone 2 by wiring `assert-action-effect-order!` into `/hyperopen/src/hyperopen/runtime/validation.cljs` after effect-shape validation.
- [x] (2026-02-25 20:13Z) Implemented Milestone 3 by extending `/hyperopen/test/hyperopen/runtime/validation_test.cljs`, adding reusable phase helper utilities in `/hyperopen/test/hyperopen/core_bootstrap/test_support/effect_extractors.cljs`, and adding no-duplicate/projection-before-heavy assertions in covered action suites.
- [x] (2026-02-25 20:16Z) Implemented Milestone 4 by updating `/hyperopen/docs/RELIABILITY.md` and `/hyperopen/docs/FRONTEND.md`, adding ADR `/hyperopen/docs/architecture-decision-records/0018-effect-order-authority-contract.md`, and passing required gates (`npm run check`, `npm test`, `npm run test:websocket`).

## Surprises & Discoveries

- Observation: The repository already has strong policy language for ordering, but no single code-level authority enforces it globally.
  Evidence: `/hyperopen/docs/FRONTEND.md` and `/hyperopen/docs/RELIABILITY.md` require UI-first ordering, while `/hyperopen/src/hyperopen/runtime/validation.cljs` currently validates payload shape only.

- Observation: Existing ordering checks are mostly action-by-action expected-vector assertions, which are high-signal but not centralized.
  Evidence: `/hyperopen/test/hyperopen/core_bootstrap/asset_selector_actions_test.cljs`, `/hyperopen/test/hyperopen/core_bootstrap/chart_menu_and_storage_test.cljs`, and `/hyperopen/test/hyperopen/core_bootstrap/account_history_actions_test.cljs`.

- Observation: Critical actions currently satisfy ordering by convention, not by contract, so regressions can be introduced silently when effects are refactored.
  Evidence: `/hyperopen/src/hyperopen/asset_selector/actions.cljs` and `/hyperopen/src/hyperopen/chart/actions.cljs` manually place `:effects/save`/`:effects/save-many` before subscription/fetch effects.

- Observation: `runtime.validation/wrap-action-handler` is already the central chokepoint with action id and emitted effects available, making it the best place to enforce an authority contract.
  Evidence: `/hyperopen/src/hyperopen/runtime/validation.cljs` calls `contracts/assert-action-args!` and `contracts/assert-emitted-effects!` with action id context.

- Observation: Some flows involve mixed effect types (`save`, local-storage persistence, heavy I/O), so strict full-vector equality is too brittle as a global contract; phased ordering categories are needed.
  Evidence: `/hyperopen/src/hyperopen/chart/actions.cljs` `select-chart-timeframe` emits projection, storage persistence, then candle fetch.

- Observation: `npm test -- <namespace>` does not filter tests in this repository; it runs the full generated runner and logs `Unknown arg: <namespace>`.
  Evidence: Running `npm test -- hyperopen.runtime.validation-test` printed `Unknown arg: hyperopen.runtime.validation-test` and then executed the full suite.

## Decision Log

- Decision: Establish one centralized effect-order authority contract in runtime validation, not in individual action modules.
  Rationale: Action modules should express domain intent; cross-cutting enforcement belongs in one boundary for consistency and maintainability.
  Date/Author: 2026-02-25 / Codex

- Decision: Use a categorized order contract (projection effects, persistence effects, heavy I/O effects) rather than brittle full-sequence hardcoding.
  Rationale: Categorization enforces responsiveness invariants while allowing benign reorderings inside each category.
  Date/Author: 2026-02-25 / Codex

- Decision: Apply the contract first to a curated set of interaction-critical actions, then expand coverage.
  Rationale: This reduces migration risk and keeps failures actionable while proving the model on known high-impact flows.
  Date/Author: 2026-02-25 / Codex

- Decision: Keep existing action-level regression tests and add central contract tests; do not replace one with the other.
  Rationale: Action tests catch business specifics, while central contract tests guard cross-cutting ordering invariants.
  Date/Author: 2026-02-25 / Codex

- Decision: Add an ADR documenting effect-order authority ownership and escalation rules for new actions.
  Rationale: The contract changes architectural governance for interaction flows and should be durable beyond code.
  Date/Author: 2026-02-25 / Codex

- Decision: Keep covered action handlers unchanged unless contract violations are present; enforce ordering at runtime boundary and expand tests instead.
  Rationale: Covered handlers were already compliant by emitted effect order, so central enforcement plus regression assertions delivered the contract without unnecessary behavior churn.
  Date/Author: 2026-02-25 / Codex

## Outcomes & Retrospective

Implemented. Effect-order authority now lives in runtime validation contract enforcement. `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs` defines phase vocabulary and per-action policies for:

- `:actions/select-asset`
- `:actions/select-chart-timeframe`
- `:actions/select-account-info-tab`
- `:actions/apply-funding-history-filters`
- `:actions/view-all-funding-history`
- `:actions/enable-agent-trading`
- `:actions/ws-diagnostics-reconnect-now`
- `:actions/select-orderbook-price-aggregation`

`/hyperopen/src/hyperopen/runtime/validation.cljs` calls the contract after existing emitted-effect schema checks, so failures now include action id, effect index, and violated rule.

Regression coverage was added in runtime validation tests plus covered action suites with a reusable phase helper in `/hyperopen/test/hyperopen/core_bootstrap/test_support/effect_extractors.cljs`.

Documentation authority is explicit in `/hyperopen/docs/RELIABILITY.md`, `/hyperopen/docs/FRONTEND.md`, and ADR 0018.

Deferred action coverage: none in this scope; all planned action IDs were implemented.

## Context and Orientation

In this repository, user interactions dispatch action handlers that return effect vectors. Effect vectors are then executed in order by the runtime/effect system. Deterministic responsiveness depends on the order in that emitted vector.

Action handlers live across domain modules such as:

- `/hyperopen/src/hyperopen/asset_selector/actions.cljs`
- `/hyperopen/src/hyperopen/chart/actions.cljs`
- `/hyperopen/src/hyperopen/order/actions.cljs`
- `/hyperopen/src/hyperopen/account/history/actions.cljs`
- `/hyperopen/src/hyperopen/orderbook/settings.cljs`
- `/hyperopen/src/hyperopen/websocket/diagnostics_actions.cljs`

Runtime registration and validation flow is:

- Action IDs are registered in `/hyperopen/src/hyperopen/registry/runtime.cljs`.
- Handlers are wired via `/hyperopen/src/hyperopen/runtime/registry-composition.cljs`.
- Action dispatch validation runs in `/hyperopen/src/hyperopen/runtime/validation.cljs`.
- Payload/effect argument contracts currently live in `/hyperopen/src/hyperopen/schema/contracts.cljs`.

For this plan:

- "Projection effect" means immediate user-visible state projection (`:effects/save`, `:effects/save-many`) that must happen first for responsiveness.
- "Persistence effect" means local durability effects (`:effects/local-storage-set`, `:effects/local-storage-set-json`) that may follow projection without impacting immediate responsiveness.
- "Heavy I/O effect" means subscription, fetch, reconnect, or API effects that can take time and must not block visible UI transitions.

## Plan of Work

### Milestone 1: Define The Effect-Order Contract Vocabulary And Policy Map

Create one new contract module (for example `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`) that classifies effects into ordered phases and defines action-level order requirements for critical interaction actions.

The initial covered action set should include high-impact responsiveness flows:

- `:actions/select-asset`
- `:actions/select-chart-timeframe`
- `:actions/select-account-info-tab`
- `:actions/apply-funding-history-filters`
- `:actions/view-all-funding-history`
- `:actions/enable-agent-trading`
- `:actions/ws-diagnostics-reconnect-now`
- `:actions/select-orderbook-price-aggregation`

Each contract entry must define required phase order and duplicate-heavy-effect policy for that action.

### Milestone 2: Enforce The Contract In Runtime Validation

Integrate contract checks in `/hyperopen/src/hyperopen/runtime/validation.cljs` after existing `assert-emitted-effects!` checks. Enforcement must run only when validation is enabled, matching current behavior.

Contract failures must include action id, effect index, and violated rule (for example "heavy I/O emitted before projection phase"). Error messages should be concrete enough for quick debugging.

Keep existing payload schema checks unchanged; this is an additive enforcement layer.

### Milestone 3: Migrate Critical Flows And Add Regression Coverage

Refactor any covered actions that violate the new contract so they emit deterministic phase order.

Add or update tests in:

- `/hyperopen/test/hyperopen/runtime/validation_test.cljs` for pass/fail contract enforcement.
- Existing core-bootstrap action suites for behavioral ordering and no-duplicate-heavy-effect regressions.
- Any affected action-local test files where ordering semantics are part of function-level guarantees.

Add a small reusable test helper (under test support) for asserting phase order to avoid repetitive bespoke assertions.

### Milestone 4: Document Authority Ownership And Run Gates

Document authority ownership and extension rules:

- Update `/hyperopen/docs/RELIABILITY.md` and `/hyperopen/docs/FRONTEND.md` with explicit "effect-order authority lives in runtime validation contract" wording.
- Add ADR `/hyperopen/docs/architecture-decision-records/0018-effect-order-authority-contract.md` describing why authority is centralized and how new actions opt in.

Then run required gates and capture evidence.

## Concrete Steps

From `/hyperopen`:

1. Add contract module and contract-aware validation tests first.

   - Create effect-order contract module and hook checks into runtime validation.
   - Add failing tests in `runtime/validation_test` for:
     - projection-before-heavy success case,
     - heavy-before-projection failure case,
     - duplicate-heavy-effect rejection for covered actions,
     - uncovered actions remaining unaffected.

   Run:

   - `npm test -- hyperopen.runtime.validation-test`

2. Migrate critical action handlers to satisfy contract rules.

   - Adjust effect emission ordering only where contract requires it.
   - Preserve behavior and public action signatures.

   Run targeted suites:

   - `npm test -- hyperopen.core-bootstrap.asset-selector-actions-test`
   - `npm test -- hyperopen.core-bootstrap.chart-menu-and-storage-test`
   - `npm test -- hyperopen.core-bootstrap.account-history-actions-test`
   - `npm test -- hyperopen.wallet.actions-test`
   - `npm test -- hyperopen.orderbook.settings-test`
   - `npm test -- hyperopen.websocket.diagnostics-actions-test`

3. Add or update docs and ADR.

   - Update reliability/frontend docs with authority location and extension guidance.
   - Add ADR with rationale and consequences.

4. Run required gates.

   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

Expected outcome: all gates pass, and ordering violations for covered actions fail fast during validation-enabled runs.

## Validation and Acceptance

Acceptance is complete when all conditions below are true.

1. Covered critical actions are governed by one centralized effect-order authority contract.
2. Runtime validation raises deterministic errors when covered actions emit heavy effects before required projection effects.
3. Covered actions avoid duplicate heavy effects unless explicitly allowed by contract.
4. Existing behavior-focused action tests remain green and still verify user-visible ordering outcomes.
5. Documentation and ADR explicitly describe where effect-order authority lives and how to extend it.
6. Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

This migration is additive and can be applied incrementally by action group. If a contract rule causes unexpected breakage, keep the contract module and tests, temporarily narrow coverage to already-migrated actions, and re-expand once affected flows are corrected.

Avoid broad rewrites of all action modules in one change. Prefer small commits per action domain so regressions are easy to isolate and rollback.

## Artifacts and Notes

Primary files expected to change:

- `/hyperopen/src/hyperopen/runtime/validation.cljs`
- `/hyperopen/src/hyperopen/schema/contracts.cljs` (or delegated contract hook points)
- `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs` (new)
- `/hyperopen/src/hyperopen/asset_selector/actions.cljs`
- `/hyperopen/src/hyperopen/chart/actions.cljs`
- `/hyperopen/src/hyperopen/account/history/actions.cljs`
- `/hyperopen/src/hyperopen/orderbook/settings.cljs`
- `/hyperopen/src/hyperopen/wallet/actions.cljs`
- `/hyperopen/src/hyperopen/websocket/diagnostics_actions.cljs`
- `/hyperopen/docs/FRONTEND.md`
- `/hyperopen/docs/RELIABILITY.md`
- `/hyperopen/docs/architecture-decision-records/0018-effect-order-authority-contract.md` (new)

Primary tests expected to change:

- `/hyperopen/test/hyperopen/runtime/validation_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap/asset_selector_actions_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap/chart_menu_and_storage_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap/account_history_actions_test.cljs`
- `/hyperopen/test/hyperopen/wallet/actions_test.cljs`
- `/hyperopen/test/hyperopen/orderbook/settings_test.cljs`
- `/hyperopen/test/hyperopen/websocket/diagnostics_actions_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap/test_support/effect_extractors.cljs` (or new helper for ordering assertions)

Evidence to capture during implementation:

- Before/after failure transcript for contract violation in `runtime.validation_test`.
- Before/after ordering transcript for at least one migrated critical action.
- Required gate outputs.

## Interfaces and Dependencies

Public interfaces that must remain stable:

- Existing action IDs and argument shapes in `/hyperopen/src/hyperopen/registry/runtime.cljs` and `/hyperopen/src/hyperopen/schema/contracts.cljs`.
- Existing runtime registration composition boundaries.

Internal interfaces expected to be added:

- Effect-order contract API exposed to runtime validation (for example `assert-action-effect-order!`).
- Action coverage map defining which action IDs are contract-governed and what order policy applies.

No external dependencies are required.

Plan revision note: 2026-02-25 20:05Z - Initial ExecPlan created for centralized effect-order authority contract and deterministic UI responsiveness enforcement.
Plan revision note: 2026-02-25 20:16Z - Marked all milestones complete, captured implementation/gate evidence, and recorded final outcomes after central contract enforcement and documentation updates.
