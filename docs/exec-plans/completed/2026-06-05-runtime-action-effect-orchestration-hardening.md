# Runtime Action/Effect Orchestration Hardening

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Runtime actions are the events emitted by the user interface and application runtime, such as `:actions/select-asset` or `:actions/submit-order`. Effects are the side-effect requests those actions return, such as state saves, local-storage writes, API calls, and websocket subscription changes. Hyperopen already validates action argument shapes through `/hyperopen/src/hyperopen/schema/contracts/action_args.cljs` and validates ordering for interaction-critical actions through `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`.

The problem is drift risk: a new action can be registered and work in the UI while the maintainer forgets to add the matching argument spec or effect-order policy. The action-argument side already has a strong catalog equality check, but effect-order coverage is still separate from the runtime registration catalog. After this change, effect-order policy intent is declared next to runtime action registration metadata and cross-checked against the central policy map. A missing policy or stale policy entry should fail focused tests before it reaches a runtime interaction.

## Context References

Public refs:

- Direct user request on 2026-06-05: "Come up with an execution plan, and then fix the below. Runtime action/effect orchestration: effect_order_contract.cljs (line 16) and action_args.cljs (line 119) are large central registries. Likely bug mode: new action works in UI but misses spec, ordering, duplicate-effect, or heavy-IO policy."

Repo artifacts:

- `/hyperopen/AGENTS.md` requires an ExecPlan for complex or risky runtime changes.
- `/hyperopen/docs/architecture-decision-records/0018-effect-order-authority-contract.md` says new interaction-critical actions with heavy I/O require explicit policy entries.
- `/hyperopen/docs/architecture-decision-records/0023-action-effect-runtime-registration-catalog-authority.md` says runtime action/effect registration metadata is centralized in `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`.
- `/hyperopen/docs/RELIABILITY.md` and `/hyperopen/docs/FRONTEND.md` require projection-before-heavy ordering for interaction-critical actions.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-06-05T19:01:15Z) Read the relevant repo workflow docs, ADRs, runtime registration catalog, schema contract coverage tests, and effect-order contract tests.
- [x] (2026-06-05T19:01:15Z) Confirmed that action argument specs already fail fast against the runtime registration catalog in `/hyperopen/src/hyperopen/schema/contracts.cljs`.
- [x] (2026-06-05T19:04:00Z) Added a failing focused test proving that effect-order policy coverage must be declared by runtime registration metadata.
- [x] (2026-06-05T19:08:00Z) Implemented per-domain `effect-order-policy-required-action-ids` sets and catalog validation that every marked action is registered.
- [x] (2026-06-05T19:09:00Z) Added import-time effect-order policy/catalog synchronization through `/hyperopen/src/hyperopen/runtime/effect_order/policy_registration.cljs`.
- [x] (2026-06-05T19:15:29Z) Ran focused compile, `npm test`, `npm run check`, `npm run test:websocket`, and `git diff --check`.
- [x] (2026-06-05T19:15:29Z) Recorded outcomes, validation evidence, and residual risk.

## Surprises & Discoveries

- Observation: The first half of the reported risk is already guarded. `/hyperopen/src/hyperopen/schema/contracts.cljs` compares registered action IDs to `action-args/action-args-spec-by-id` and throws on missing or extra specs.
  Evidence: Lines 9-15 in that file throw `"Action contract metadata drift detected"` with missing and extra sets.

- Observation: The effect-order side exposes covered action IDs, but those IDs are currently owned only by `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`.
  Evidence: `covered-action-ids` returns the keys of the private policy map; the runtime registration catalog has no effect-order intent metadata yet.

- Observation: The first RED run caught the intended missing catalog API before implementation, and the first full `npm test` after implementation caught a real unmarked existing policy action: `:actions/submit-order`.
  Evidence: Shadow initially warned that `hyperopen.schema.runtime-registration-catalog/effect-order-policy-required-action-ids` was undeclared. Later `npm test` failed at import time with `unmarked-policy=[:actions/submit-order]`, and adding that trade-domain marker resolved it.

- Observation: This fresh worktree had no `node_modules`, which caused an initial `npm test` module-load failure for `lucide/dist/esm/icons/external-link.js`.
  Evidence: `npm ls lucide --depth=0` returned empty and `ls node_modules` showed the directory was absent. `npm ci` restored dependencies from `package-lock.json`.

## Decision Log

- Decision: Do not split all action argument specs in this pass.
  Rationale: The action-argument registry is large, but its ID coverage is already linked to runtime registration. Splitting every spec by domain would be high churn and would not address the immediate bug mode as directly as a drift guard.
  Date/Author: 2026-06-05 / Codex

- Decision: Add effect-order policy intent to the runtime registration catalog rather than inferring it from action names or effect names.
  Rationale: Action handlers return effects dynamically, so static inference from registration rows would be unreliable. Explicit per-domain declarations keep the decision reviewable next to the registration data a maintainer edits when registering an action.
  Date/Author: 2026-06-05 / Codex

- Decision: Use per-domain `effect-order-policy-required-action-ids` sets instead of adding optional metadata to every action binding row.
  Rationale: This preserves the existing `[action-id handler-key]` row shape for all callers while still keeping effect-order intent next to each domain's action registration. It also avoided broad mechanical edits across large registries.
  Date/Author: 2026-06-05 / Codex

- Decision: Preserve the current public APIs and runtime behavior.
  Rationale: This is an architecture hardening change. The acceptance signal is a new invariant and focused tests, not changed user-facing behavior.
  Date/Author: 2026-06-05 / Codex

## Outcomes & Retrospective

Implemented a catalog-owned effect-order coverage contract. Each runtime registration domain that owns interaction-critical actions now declares `effect-order-policy-required-action-ids`. The central runtime registration catalog validates those IDs against registered actions, and the effect-order contract validates at import time that the catalog-required set exactly matches the policy map keys.

The user-facing runtime behavior is unchanged. The change makes missing or stale effect-order policy fail in tests and during runtime contract import instead of allowing a UI action to work with incomplete heavy-I/O, duplicate-effect, or ordering policy.

## Context and Orientation

The runtime action registration catalog lives in `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`. It concatenates per-domain registration rows from files under `/hyperopen/src/hyperopen/schema/runtime_registration/`. A registration row is currently `[action-id handler-key]`, where `action-id` is the public event keyword and `handler-key` is the key used to find the action handler in the runtime dependency map.

Action payload validation lives in `/hyperopen/src/hyperopen/schema/contracts/action_args.cljs`. Its large `action-args-spec-by-id` map links every registered action ID to a ClojureScript spec for the action arguments. `/hyperopen/src/hyperopen/schema/contracts.cljs` already compares the action IDs in that map to the runtime registration catalog and fails if the sets differ.

Effect-order validation lives in `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`. It has a private map from action ID to policy. A policy defines the ordered phases that matter for a user interaction: projection effects update visible UI state, persistence effects save local state, and heavy I/O effects make network, websocket, or other expensive side-effect requests. Runtime validation calls this contract after an action handler returns effects.

The drift gap is that the registration catalog does not say which action IDs require effect-order policy entries. This plan adds explicit per-domain declarations and checks them against the policy map.

## Plan of Work

First, add a RED test in `/hyperopen/test/hyperopen/runtime/effect_order_contract_test.cljs`. The test will ask the runtime registration catalog for action IDs marked as requiring effect-order policy and assert that the set equals `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs` covered action IDs. It should fail because the catalog has no such helper yet.

Second, add `effect-order-policy-required-action-ids` to the per-domain runtime registration namespaces that own policy-covered actions. This keeps effect-order intent beside the domain's action registration without changing the existing action row shape.

Third, update `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs` to union the per-domain sets and validate that every policy-required action ID is registered.

Fourth, update `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs` to validate its policy map through `/hyperopen/src/hyperopen/runtime/effect_order/policy_registration.cljs`. The helper asserts that every policy-covered action is registered and every action marked as requiring effect-order policy has a policy entry. Keep the existing `covered-action-ids`, `action-policy`, and assertion APIs stable.

Fifth, run focused tests for runtime effect-order and schema contract coverage. Then run the repo gates required for code changes: `npm run check`, `npm test`, and `npm run test:websocket`, unless time or environment failures block them.

## Concrete Steps

All commands run from `/Users/barry/.codex/worktrees/7269/hyperopen`.

1. Add the RED test and compile the test target:

       npm test

   Observed before implementation: the new test target warned because `effect-order-policy-required-action-ids` did not exist, then the fresh worktree failed at Node module load because dependencies were not installed.

2. Implement per-domain policy-required sets, catalog validation, and effect-order assertions.

3. Run focused verification:

       npx shadow-cljs --force-spawn compile test

   Observed after implementation: compile passed with 0 warnings.

4. Run required gates:

       npm run check
       npm test
       npm run test:websocket

   Expected after implementation: all commands exit 0.

## Validation and Acceptance

Acceptance is internal and test-backed. The change is accepted when:

- The new effect-order coverage test fails before the implementation and passes after it.
- `/hyperopen/src/hyperopen/schema/contracts.cljs` continues to enforce action argument spec coverage against the runtime registration catalog.
- The runtime registration catalog can identify every action row that requires effect-order policy.
- `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs` fails fast if policy metadata and policy map coverage drift.
- Required code-change gates pass or any blocker is reported with exact command output.

## Idempotence and Recovery

The changes are additive and deterministic. Re-running tests is safe. If an action ID is accidentally added to a domain's `effect-order-policy-required-action-ids` set but no policy exists, the focused test and import-time assertion should report the missing action ID. If a policy remains after the action is unregistered, the assertion should report the stale policy action ID. Recovery is to either add the missing policy, remove stale policy-required action IDs, or remove stale policy map entries so the sets match again.

## Artifacts and Notes

- RED evidence: `npm test` initially reported undeclared var `hyperopen.schema.runtime-registration-catalog/effect-order-policy-required-action-ids`.
- Dependency recovery: `npm ci` restored `node_modules` after `npm test` failed to load `lucide/dist/esm/icons/external-link.js` in a worktree without installed dependencies.
- Drift evidence: `npm test` failed once with `Effect-order policy registration drift detected ... unmarked-policy=[:actions/submit-order]`, proving the new import-time assertion catches existing unmarked policy entries.
- Focused compile: `npx shadow-cljs --force-spawn compile test` passed with 0 warnings.
- Required tests: `npm test` passed: 4222 tests, 23424 assertions, 0 failures, 0 errors.
- Required check gate: `npm run check` passed with 0 warnings on all Shadow compile targets.
- Required websocket gate: `npm run test:websocket` passed: 531 tests, 3080 assertions, 0 failures, 0 errors.
- Hygiene: `git diff --check` passed.

## Interfaces and Dependencies

The runtime registration catalog must expose:

- `action-binding-rows`, returning rows compatible with existing callers.
- `action-ids`, returning the set of all registered action IDs.
- `effect-order-policy-required-action-ids`, returning the union of per-domain action IDs that require effect-order policy.

Runtime registration domain namespaces may expose:

- `effect-order-policy-required-action-ids`, a set of registered action IDs owned by that domain that must have explicit effect-order policy.

The effect-order contract must preserve:

- `action-policy`
- `covered-action-ids`
- `covered-action?`
- `effect-order-summary`
- `assert-action-effect-order!`

Plan revision note, 2026-06-05T19:01:15Z: Initial plan created to address the direct user request and capture the chosen scoped implementation.
