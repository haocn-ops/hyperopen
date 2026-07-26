# Reduce CRAP in Funding Modal View Model

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

This plan executes `bd` issue `hyperopen-6cp`.

## Purpose / Big Picture

`/hyperopen/src/hyperopen/funding/application/modal_vm.cljs` currently packs modal normalization, asset selection, lifecycle derivation, fee and queue derivation, submit-state logic, and final view-model assembly into one large pure function. The repo-local CRAP report identified `hyperopen.funding.application.modal-vm/funding-modal-view-model` as the highest-risk hotspot in the repository at `CRAP 3824.41`, `complexity 93`, and `coverage 0.24`.

After this change, contributors should be able to adjust deposit, transfer, withdraw, lifecycle, and feedback derivation without editing one monolithic branch-heavy function. The public `funding-modal-view-model` output must stay stable, but the implementation should be sliced into smaller helpers and backed by direct application-level tests. A contributor can verify success by running the funding tests, the required quality gates, and a focused CRAP report for `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs`.

## Progress

- [x] (2026-03-07 18:36Z) Re-read `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/docs/WORK_TRACKING.md`, and `/hyperopen/ARCHITECTURE.md` for planning, issue-tracking, and boundary rules.
- [x] (2026-03-07 18:36Z) Ran `bd ready --json`, identified `hyperopen-6cp` as the highest-priority ready CRAP remediation issue, and claimed it with `bd update hyperopen-6cp --claim --json`.
- [x] (2026-03-07 18:36Z) Audited `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs`, `/hyperopen/src/hyperopen/funding/application/modal_actions.cljs`, `/hyperopen/test/hyperopen/funding/actions_test.cljs`, and existing ExecPlans to define the change boundary.
- [x] (2026-03-07 18:36Z) Authored this ExecPlan.
- [x] (2026-03-07 18:50Z) Replaced the monolithic `funding-modal-view-model` body with a context-building helper pipeline in `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs`, preserving the existing public read-model shape while moving branch-heavy derivation into smaller private helpers.
- [x] (2026-03-07 18:50Z) Added direct seam coverage in `/hyperopen/test/hyperopen/funding/application/modal_vm_test.cljs` for generated deposit addresses, pre-amount deposit feedback gating, withdraw lifecycle and queue derivation, withdraw preview feedback, and unsupported deposit flow handling.
- [x] (2026-03-07 18:50Z) Installed missing local npm dependencies with `npm ci`, regenerated `/hyperopen/test/test_runner_generated.cljs`, and passed `npm test`, `npm run check`, `npm run test:websocket`, and `npm run coverage`.
- [x] (2026-03-07 18:50Z) Ran `bb tools/crap_report.clj --module src/hyperopen/funding/application/modal_vm.cljs --format json --top-functions 100` and confirmed the hotspot reduction from `complexity 93 / CRAP 3824.41` to `complexity 1 / CRAP 1.0` for `funding-modal-view-model`, with module `crapload 0.0` and `crappy-functions 0`.
- [x] (2026-03-07 18:51Z) Closed `bd` issue `hyperopen-6cp` as completed and moved this ExecPlan to `/hyperopen/docs/exec-plans/completed/2026-03-07-funding-modal-vm-crap-reduction.md`.

## Surprises & Discoveries

- Observation: The public function already has a strong test seam because it accepts a dependency map and raw state as arguments.
  Evidence: `/hyperopen/src/hyperopen/funding/application/modal_actions.cljs` injects helpers into `modal-vm/funding-modal-view-model`, so tests can call the application function directly with stubbed collaborators.

- Observation: The recent funding modal workflow refactor already moved render-specific branching out of the view, so the current hotspot is almost entirely in pure read-model derivation.
  Evidence: `/hyperopen/docs/exec-plans/completed/2026-03-07-funding-modal-workflow-slices.md` records the nested read-model refactor, and the current `modal_vm.cljs` function now concentrates most of the remaining branch density.

- Observation: The generated test runner is a tracked artifact, so adding a new test namespace requires regenerating `/hyperopen/test/test_runner_generated.cljs` as part of the implementation.
  Evidence: `npm test` rewrote the generated runner to include `hyperopen.funding.application.modal-vm-test`, and `git status --short` showed `/hyperopen/test/test_runner_generated.cljs` as a modified tracked file.

- Observation: Splitting the public function into helper stages and adding direct application-level tests completely removed the module from the CRAP hotspot list.
  Evidence: The post-change module report for `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs` reported `crappy-functions 0`, `project-crapload 0.0`, module `max-crap 11.560185185185185`, and `funding-modal-view-model` at `complexity 1`, `coverage 1.0`, `crap 1.0`.

## Decision Log

- Decision: Keep `hyperopen.funding.application.modal-vm/funding-modal-view-model` as the public entry point and extract smaller private helpers in the same namespace instead of widening the change to new namespaces.
  Rationale: The issue is function-level CRAP, not namespace ownership. Keeping the existing public seam stable reduces migration risk while still allowing the complexity to be distributed across small, testable helpers.
  Date/Author: 2026-03-07 / Codex

- Decision: Add a dedicated application-level test namespace for `modal_vm.cljs` rather than only extending `funding/actions_test.cljs`.
  Rationale: The issue explicitly calls for direct coverage around the extracted logic. Testing the injected dependency seam directly makes the new helper structure easier to protect without going through unrelated command and domain wiring.
  Date/Author: 2026-03-07 / Codex

- Decision: Keep the new helper clusters in `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs` instead of splitting them into another namespace during this pass.
  Rationale: The highest risk was the single oversized function, not namespace size or dependency direction. Staying in one file kept the change narrowly scoped while still dropping the public function to a trivial orchestration layer.
  Date/Author: 2026-03-07 / Codex

## Outcomes & Retrospective

Implementation completed. `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs` now derives its read model through a sequence of small helper functions for base modal normalization, asset context, generated address state, preview state, fee and queue state, lifecycle state, amount bounds, presentation text, and final section assembly. The public `funding-modal-view-model` entry point is now a short pipeline at lines `838-849` instead of the previous monolithic branch-heavy body.

Direct application-level regression coverage now lives in `/hyperopen/test/hyperopen/funding/application/modal_vm_test.cljs`. Those tests exercise the injected dependency seam without going through the wrapper in `/hyperopen/src/hyperopen/funding/application/modal_actions.cljs`, while the existing funding action tests continue to protect the external compatibility path.

Validation completed successfully:

- `npm test`: pass (`2002` tests, `10260` assertions)
- `npm run check`: pass
- `npm run test:websocket`: pass (`339` tests, `1852` assertions)
- `npm run coverage`: pass (`Lines 89.19%`, `Functions 83.7%`)
- `bd close hyperopen-6cp --reason "Completed" --json`: pass

Focused CRAP report results for `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs`:

- Baseline hotspot before the refactor: `funding-modal-view-model` at `complexity 93`, `coverage 0.24`, `crap 3824.41`
- Post-change: `funding-modal-view-model` at `complexity 1`, `coverage 1.0`, `crap 1.0`
- Module post-change summary: `crappy-functions 0`, `crapload 0.0`, `max-crap 11.560185185185185`, `avg-coverage 0.9318071411041725`

## Context and Orientation

The funding modal read model lives in `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs`. It is a pure application-layer namespace. The only public var is `funding-modal-view-model`, which accepts two arguments:

1. A dependency map containing normalization, preview, asset lookup, formatting, and lifecycle helper functions.
2. The full application state map.

`/hyperopen/src/hyperopen/funding/application/modal_actions.cljs` is the compatibility seam that supplies the real collaborators and exposes `funding-modal-view-model` to the rest of the app. The existing user-visible tests in `/hyperopen/test/hyperopen/funding/actions_test.cljs` already protect major behaviors such as multi-asset deposit options, lifecycle outcome derivation, fee display, and queue state. Those tests should remain intact because they prove the public app surface still behaves the same after the refactor.

For this plan, “CRAP” is the maintainability metric produced by `/hyperopen/tools/crap_report.clj`. It rises when function complexity is high and test coverage is low. The baseline hotspot from the report is:

    file=src/hyperopen/funding/application/modal_vm.cljs
    fn=hyperopen.funding.application.modal-vm/funding-modal-view-model
    complexity=93
    coverage=0.24
    crap=3824.41

The refactor should not change persisted modal state, runtime contracts, or view call sites. It should only split the application-layer derivation into smaller pure helpers and add direct tests that execute the same public seam with deterministic fake collaborators.

## Plan of Work

First, refactor `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs` into coherent helper clusters. The function currently interleaves several concerns that can be derived separately: base modal state, selected asset context, generated address state, lifecycle outcome state, fee and queue state, status and submit labels, deposit-specific presentation, withdraw-specific presentation, and final compatibility fields. Extract those concerns into small private functions that accept a shared context map and return derived values in plain data. The top-level `funding-modal-view-model` function should become a short orchestration layer that composes those helpers and assembles the final output map.

Second, add direct tests under `/hyperopen/test/hyperopen/funding/application/modal_vm_test.cljs`. These tests should call `hyperopen.funding.application.modal-vm/funding-modal-view-model` directly with stubbed dependencies so that each major derivation cluster is exercised in isolation from modal commands and runtime registration. At minimum, cover deposit generated-address behavior, withdraw queue and lifecycle derivation, status-message and feedback visibility rules, and content-kind selection across supported and unsupported deposit flows.

Third, keep the existing action-level funding tests in `/hyperopen/test/hyperopen/funding/actions_test.cljs` as regression coverage for the public wrapper path. Only expand that file if a behavior is easier to assert through the wrapper than through the direct seam.

Finally, validate the change with the required gates and a focused CRAP report. The focused report should show that `funding-modal-view-model` no longer carries the previous monolithic complexity and that the module’s CRAP load is now distributed across smaller helpers with better coverage.

## Concrete Steps

Run from repository root `/hyperopen`.

1. Edit `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs` to extract smaller helper functions for the current derivation clusters and reduce branching in the public orchestrator.
2. Add `/hyperopen/test/hyperopen/funding/application/modal_vm_test.cljs` with direct seam tests using stub collaborators.
3. Run the main test suite:

       npm test

4. Run required validation gates:

       npm run check
       npm run test:websocket

5. Generate fresh coverage and a focused CRAP report:

       npm run coverage
       bb tools/crap_report.clj --module src/hyperopen/funding/application/modal_vm.cljs --format json --top-functions 100

6. Update this ExecPlan with the observed post-change results and move the plan to `/hyperopen/docs/exec-plans/completed/`.

## Validation and Acceptance

Acceptance is satisfied when all of the following are true:

1. `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs` still exposes the same public `funding-modal-view-model` function and returns the same application-facing read-model shape expected by current callers.
2. The public function is reduced to a small orchestration layer instead of carrying the original branch-heavy derivation directly.
3. `/hyperopen/test/hyperopen/funding/application/modal_vm_test.cljs` exists and directly exercises the injected application seam for deposit, withdraw, lifecycle, and feedback derivation.
4. Existing funding action tests continue to pass, proving the wrapper path stayed compatible.
5. `npm run check`, `npm test`, and `npm run test:websocket` pass.
6. The focused CRAP report for `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs` shows `funding-modal-view-model` reduced to `complexity 1`, `coverage 1.0`, and `CRAP 1.0`, with module `crapload 0.0`.

## Idempotence and Recovery

This plan is limited to pure application logic and test code. Re-running the refactor and test commands is safe. If a helper extraction introduces a regression, restore the previous branch to the top-level orchestration function before retrying the extraction in smaller steps. If coverage generation or the CRAP report fails due to stale artifacts, rerun `npm run coverage` to rebuild `coverage/lcov.info` before comparing results.

## Artifacts and Notes

Implementation evidence to capture in the completed revision:

- The final helper layout in `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs`.
- The new direct test namespace `/hyperopen/test/hyperopen/funding/application/modal_vm_test.cljs`.
- The post-change focused CRAP report values for the module.
- Required gate results.

Revision note (2026-03-07 18:36Z): Initial ExecPlan created after claiming `hyperopen-6cp`, auditing the funding modal view-model hotspot, and confirming the direct dependency-injection test seam.
Revision note (2026-03-07 18:50Z): Updated after implementation with the helper-pipeline refactor, the new direct application tests, validation output, and the before/after CRAP metrics for `funding-modal-view-model`.
Revision note (2026-03-07 18:51Z): Moved the finished plan to `completed/` and recorded the local `bd` issue closure.
