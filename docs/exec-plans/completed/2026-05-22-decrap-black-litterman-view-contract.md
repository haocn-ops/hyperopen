# DeCRAP Black-Litterman View Contract

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `.agents/PLANS.md`, the repository's canonical ExecPlan contract.

## Purpose / Big Picture

The optimizer accepts Black-Litterman views inside portfolio optimizer drafts and engine requests. A Black-Litterman view is a user-supplied expected-return opinion, either absolute for one instrument or relative between instruments. The private validator `black-litterman-view?` in `src/hyperopen/portfolio/optimizer/contracts/specs.cljs` currently has the highest CRAP score in production source: `306.00`, with complexity `17` and coverage `0.00`.

After this change, the public optimizer contract specs will exercise the accepted and rejected Black-Litterman view shapes through `cljs.spec`, reducing change risk without changing production behavior. The work is observable by running the contract test suite, the CRAP report for `specs.cljs`, and the repository gates.

## Context References

Public refs:
- Direct user request on 2026-05-22: create an execution plan and implement deCRAPing for `src/hyperopen/portfolio/optimizer/contracts/specs.cljs:143` `black-litterman-view?`.

Repo artifacts:
- `AGENTS.md` requires the validation return contract and code-change gates.
- `.agents/PLANS.md` defines this ExecPlan format.
- `docs/PLANS.md` defines active/completed plan lifecycle.
- `src/hyperopen/portfolio/optimizer/contracts/specs.cljs` defines the private `black-litterman-view?` predicate and the public specs that call it.
- `test/hyperopen/portfolio/optimizer/contracts_test.cljs` already owns optimizer contract validation tests.

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-05-22T12:25Z) Confirmed the worktree is already an isolated linked worktree at `/Users/barry/.codex/worktrees/5df4/hyperopen`.
- [x] (2026-05-22T12:25Z) Confirmed the RED quality condition with `bb tools/crap_report.clj --module src/hyperopen/portfolio/optimizer/contracts/specs.cljs --top-functions 5 --top-modules 1`; `black-litterman-view?` reports CRAP `306.00`, coverage `0.00`, complexity `17`.
- [x] (2026-05-22T12:34Z) Added focused contract tests that validate accepted Black-Litterman view shapes through `::contracts/draft`.
- [x] (2026-05-22T12:34Z) Added focused contract tests that reject malformed Black-Litterman view shapes through `::contracts/draft`.
- [x] (2026-05-22T12:35Z) Ran `npm test`; result was `3996` tests, `22013` assertions, `0 failures`, `0 errors`.
- [x] (2026-05-22T12:37Z) Ran `npm run coverage`; result was main runner `3996` tests and websocket runner `524` tests with `0 failures`, `0 errors`; coverage summary reported statements and lines `90.65%`, branches `70.06%`, functions `83.52%`.
- [x] (2026-05-22T12:38Z) Re-ran `bb tools/crap_report.clj --module src/hyperopen/portfolio/optimizer/contracts/specs.cljs --top-functions 8 --top-modules 1`; `black-litterman-view?` now reports CRAP `17.00`, coverage `1.00`, and the module reports `crappy_functions=0`.
- [x] (2026-05-22T12:42Z) Ran `npm test` again; result was `3996` tests, `22013` assertions, `0 failures`, `0 errors`.
- [x] (2026-05-22T12:41Z) Ran `npm run test:websocket`; result was `524` tests, `3043` assertions, `0 failures`, `0 errors`.
- [x] (2026-05-22T12:42Z) Full `npm run check` remains blocked by unrelated repository guardrails: stale `docs/product-specs/portfolio-page-parity-prd.md` and namespace-size exceptions outside this change.
- [x] (2026-05-22T12:50Z) Moved this ExecPlan to `docs/exec-plans/completed/` after explicit user instruction to move it to inactive or complete.

## Surprises & Discoveries

- Observation: The existing contract tests mention Black-Litterman return models for request-signature canonicalization and worker wire normalization, but they do not validate Black-Litterman views through `::contracts/draft` or `::contracts/engine-request`.
  Evidence: The CRAP report for `src/hyperopen/portfolio/optimizer/contracts/specs.cljs` reports `black-litterman-view?` coverage `0.00`.

- Observation: `npm run check` did not reach completion because `lint:docs` fails before later gates.
  Evidence: `bb -m dev.check-docs` reports `[stale-doc] docs/product-specs/portfolio-page-parity-prd.md - document is stale: 92 days old, max allowed 90`. This file was not modified by the deCRAP work.

- Observation: Running the remaining check tail past `lint:docs` also hits namespace-size guardrail failures unrelated to this change.
  Evidence: `bb -m dev.check-namespace-sizes` reports missing or exceeded exceptions for `test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs`, `src/hyperopen/views/portfolio/optimize/scenario_objective_menu.cljs`, `test/hyperopen/portfolio/optimizer/application/engine_test.cljs`, `src/hyperopen/portfolio/optimizer/actions/draft.cljs`, and `src/hyperopen/schema/contracts/action_args.cljs`. None of these files were modified by this plan.

## Decision Log

- Decision: Add tests to `test/hyperopen/portfolio/optimizer/contracts_test.cljs` instead of exposing `black-litterman-view?`.
  Rationale: `black-litterman-view?` is deliberately private implementation detail. The stable behavior is the public contract spec, especially `::contracts/draft`, which calls the private predicate through `return-model?`.
  Date/Author: 2026-05-22 / Codex.

- Decision: Keep production code unchanged unless tests expose a real contract defect.
  Rationale: The CRAP issue is a missing-coverage risk, not a reported behavior bug. The minimum safe implementation is targeted test coverage.
  Date/Author: 2026-05-22 / Codex.

- Decision: Leave this ExecPlan active instead of moving it to completed.
  Rationale: The target implementation and CRAP acceptance passed, but the repository's required `npm run check` gate is blocked by unrelated stale-doc and namespace-size guardrails. Closing the plan would hide that validation remains blocked.
  Date/Author: 2026-05-22 / Codex.

- Decision: Move this ExecPlan to completed.
  Rationale: The user explicitly instructed Codex to move this document to either inactive or complete. The deCRAP implementation and targeted acceptance are complete, and this plan preserves the unrelated `npm run check` blocker evidence for follow-up.
  Date/Author: 2026-05-22 / Codex.

## Outcomes & Retrospective

The production implementation stayed unchanged. The test suite now validates five accepted Black-Litterman view shapes and ten malformed shapes through the public `::contracts/draft` spec. This reduced `black-litterman-view?` from CRAP `306.00`, coverage `0.00`, and module `crappy_functions=1` to CRAP `17.00`, coverage `1.00`, and module `crappy_functions=0`.

The change reduces measured change risk by adding branch coverage around an existing private contract predicate. It does not reduce the predicate's intrinsic complexity; future cleanup could split the predicate into named absolute-view and relative-view validators, but this plan intentionally avoided production refactoring.

The work is moved to completed after explicit user instruction. `npm run check` remains blocked by unrelated repository guardrails. Direct test evidence for this change is passing, and the remaining blocker is outside the files modified by this plan.

## Context and Orientation

The public namespace `hyperopen.portfolio.optimizer.contracts` re-exports ClojureScript specs from `hyperopen.portfolio.optimizer.contracts.specs`. ClojureScript specs are runtime predicates registered under names such as `::contracts/draft`. Calling `(s/valid? ::contracts/draft draft)` executes nested private predicates, including `black-litterman-view?` when the draft's return model has `{:kind :black-litterman :views [...]}`.

The target function accepts a map only when it has `:kind` equal to `:absolute` or `:relative`, has a finite numeric `:return`, has optional finite `:confidence` and `:confidence-variance`, has optional `:direction` in `#{:outperform :underperform}`, has optional map `:weights`, and has a valid instrument identity. Absolute views need either a non-blank `:instrument-id` or non-empty `:weights`. Relative views need either different `:instrument-id` and `:comparator-instrument-id`, different `:long-instrument-id` and `:short-instrument-id`, or non-empty `:weights`.

The existing test file `test/hyperopen/portfolio/optimizer/contracts_test.cljs` already imports `cljs.test`, `clojure.spec.alpha`, optimizer contract fixtures, the public contracts namespace, and optimizer fixtures. It is the correct owner for this coverage because it already tests draft, request, result, migration, and worker-envelope contract validity.

## Plan of Work

Add small local helpers to `test/hyperopen/portfolio/optimizer/contracts_test.cljs` near the existing sample fixtures. The helpers will create a valid draft whose return model is Black-Litterman and will make individual view validation readable without reaching into private production vars.

Add one test for accepted shapes. It should assert that `::contracts/draft` accepts these views: an absolute view with an instrument id, an absolute view represented by weights, a relative view with `:instrument-id` and `:comparator-instrument-id`, a relative view with `:long-instrument-id` and `:short-instrument-id`, and a relative view represented by weights. Include allowed directions and finite optional confidence fields across the cases so the predicate's optional branches execute.

Add one test for rejected shapes. It should assert that `::contracts/draft` rejects: unknown kind, missing or non-finite return, non-finite confidence, invalid direction, non-map weights, absolute view without an instrument or weights, relative view with identical primary/comparator instruments, relative view with identical long/short instruments, and relative view with no valid identity or weights.

Do not edit production source unless a test failure reveals an actual contract bug. If that happens, update this plan's `Surprises & Discoveries` and `Decision Log` before changing production code.

## Concrete Steps

Run commands from `/Users/barry/.codex/worktrees/5df4/hyperopen`.

1. Confirm the RED quality baseline:

       bb tools/crap_report.clj --module src/hyperopen/portfolio/optimizer/contracts/specs.cljs --top-functions 5 --top-modules 1

   Expected current evidence before test additions includes:

       crap=306.00 coverage=0.00 complexity=17 file=src/hyperopen/portfolio/optimizer/contracts/specs.cljs line=143 fn=hyperopen.portfolio.optimizer.contracts.specs/black-litterman-view?

2. Edit `test/hyperopen/portfolio/optimizer/contracts_test.cljs` to add the helper and tests described in `Plan of Work`.

3. Regenerate the test runner:

       npm run test:runner:generate

   Expected output includes:

       Generated test/test_runner_generated.cljs with ... namespaces.

4. Run the ClojureScript test suite:

       npm test

   Expected output includes `0 failures, 0 errors`.

5. Refresh coverage:

       npm run coverage

   Expected output includes `0 failures, 0 errors` for both runners and a coverage summary.

6. Re-run the module CRAP report:

       bb tools/crap_report.clj --module src/hyperopen/portfolio/optimizer/contracts/specs.cljs --top-functions 5 --top-modules 1

   Acceptance evidence is that `black-litterman-view?` no longer appears above threshold or reports coverage high enough to reduce CRAP below `30.00`.

   Observed after implementation:

       crappy_functions=0
       project_crapload=0.00
       crap=17.00 coverage=1.00 complexity=17 file=src/hyperopen/portfolio/optimizer/contracts/specs.cljs line=143 fn=hyperopen.portfolio.optimizer.contracts.specs/black-litterman-view?

7. Run required gates for code changes:

       npm run check
       npm test
       npm run test:websocket

   Expected output for each command is exit code `0`; the two test commands should report `0 failures, 0 errors`.

   Observed after implementation:

       npm test
       Ran 3996 tests containing 22013 assertions.
       0 failures, 0 errors.

       npm run test:websocket
       Ran 524 tests containing 3043 assertions.
       0 failures, 0 errors.

       npm run check
       Blocked at lint:docs by stale docs/product-specs/portfolio-page-parity-prd.md.

       npm run lint:docs:test && ... remaining check tail
       Blocked at lint:namespace-sizes by unrelated namespace-size guardrail failures.

## Validation and Acceptance

Acceptance requires all of the following:

The new tests in `test/hyperopen/portfolio/optimizer/contracts_test.cljs` pass and exercise both accepted and rejected Black-Litterman view contract shapes through the public `::contracts/draft` spec.

The CRAP report for `src/hyperopen/portfolio/optimizer/contracts/specs.cljs` no longer flags `black-litterman-view?` as a crappy function above the `30.00` threshold.

The repository code-change gates from `AGENTS.md` pass: `npm run check`, `npm test`, and `npm run test:websocket`. As of 2026-05-22T12:42Z, `npm test` and `npm run test:websocket` pass. `npm run check` is blocked by unrelated repository guardrails documented in `Surprises & Discoveries`.

No production API changes are introduced. The only intended behavior change is stronger regression coverage around existing contract semantics.

## Idempotence and Recovery

The plan is safe to retry. Re-running `npm run test:runner:generate` is expected and should leave the generated runner unchanged except when test namespaces are added or removed. Re-running coverage replaces `.coverage/` and `coverage/`, which are ignored build artifacts. If a validation command fails, keep the ExecPlan active, add the failure and investigation result to `Surprises & Discoveries`, and do not move the plan to completed.

If a test addition accidentally changes tracked generated output, inspect `git diff` before handoff. Do not revert unrelated user changes.

## Artifacts and Notes

Initial CRAP evidence:

       scope=src/hyperopen/portfolio/optimizer/contracts/specs.cljs
       functions_scanned=31
       crappy_functions=1
       project_crapload=276.00
       top_functions:
         crap=306.00 coverage=0.00 complexity=17 file=src/hyperopen/portfolio/optimizer/contracts/specs.cljs line=143 fn=hyperopen.portfolio.optimizer.contracts.specs/black-litterman-view?

## Interfaces and Dependencies

Use only existing test dependencies already required by `test/hyperopen/portfolio/optimizer/contracts_test.cljs`: `cljs.test`, `clojure.spec.alpha`, `hyperopen.portfolio.optimizer.contract-fixtures`, `hyperopen.portfolio.optimizer.contracts`, and `hyperopen.portfolio.optimizer.fixtures`.

The public interface under test is `::contracts/draft` via `(s/valid? ::contracts/draft draft)`. The tests must not require `hyperopen.portfolio.optimizer.contracts.specs` directly and must not resolve or call the private `black-litterman-view?` var.

## Plan Revision Notes

- 2026-05-22 / Codex: Created the active plan from the user's deCRAP request and baseline CRAP evidence.
- 2026-05-22 / Codex: Updated progress, outcomes, validation evidence, and blocker notes after implementing contract tests and rerunning CRAP/test commands.
- 2026-05-22 / Codex: Moved the plan to completed after explicit user instruction while preserving the unrelated check-blocker evidence.
