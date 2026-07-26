# Harden Vault Transfer Policy Mutation Coverage

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. Keep it self-contained, update it whenever implementation discoveries change the plan, and leave at least one unchecked progress item while the plan remains active.

## Purpose / Big Picture

The 2026-06-09 nightly mutation sweep found that `/hyperopen/src/hyperopen/vaults/domain/transfer_policy.cljs` is now the weakest audited module in the repository: it killed `0/19` executed mutants and also left `3` selected mutants uncovered. Initial follow-up work assumed this was primarily a test-gap inside the vault transfer domain. That assumption was wrong. Manual mutation probes and a temporary guaranteed-failing test proved that `npm test` was exiting with code `0` before it reached the vault namespaces at all. After this change, the JavaScript test runner should wait for true `cljs.test` completion, the vault transfer tests should actually execute in the normal suite, and a targeted mutation rerun should then reflect the strengthened vault transfer assertions instead of a silent runner blind spot.

## Context References

Public refs:

- Direct user request on 2026-06-09: create an execution plan and implement the first mutation-follow-up priority from the nightly sweep.

Repo artifacts:

- `/hyperopen/AGENTS.md` requires an ExecPlan for risky bug work and requires `npm run check`, `npm test`, and `npm run test:websocket` when code changes.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define the active ExecPlan contract.
- `/hyperopen/target/mutation/nightly/2026-06-09T15-03-33.091457Z/summary.md` records the nightly result that ranked `transfer_policy.cljs` worst at `0.0%` kill with `19` survivors and `3` uncovered selected mutants.
- `/hyperopen/target/mutation/reports/2026-06-09T15-30-01.277098Z-src-hyperopen-vaults-domain-transfer_policy.cljs.edn` contains the exact surviving and uncovered site list for this pass.
- `/hyperopen/docs/exec-plans/completed/2026-03-15-top-3-mutation-coverage.md` records the previous transfer-policy mutation hardening pass and its earlier residuals; this plan builds on that work instead of redoing its already-covered cases.

Local scratch refs, non-authoritative:

- None.

## Progress

- [x] (2026-06-09 16:26Z) Re-read the nightly summary, the per-module mutation artifact, the current transfer-policy source, and the existing direct/property/formal-vector test coverage.
- [x] (2026-06-09 16:28Z) Created this active ExecPlan and scoped the implementation to `transfer_policy.cljs`, leaving the smaller `trading/validation.cljs` survivor pair out of scope for this pass.
- [x] (2026-06-09 16:36Z) Added direct transfer-policy assertions for invalid-mode fallback, liquidator deposit blocking, non-map modal handling, explicit modal address precedence, and withdraw-all request shaping.
- [x] (2026-06-09 16:44Z) Proved the mutation result was runner-limited, not assertion-limited, by mutating `transfer_policy.cljs` and by inserting a guaranteed failing test; `npm test` still exited `0` and never reached the vault namespaces.
- [x] (2026-06-09 17:05Z) Repaired the generated JavaScript test runner so `cljs.test` asynchronous completion controls summary timing, process exit, and explicit final termination after the summary.
- [x] (2026-06-09 17:12Z) Re-ran `npm test`, confirmed it reached `hyperopen.vaults.domain.transfer-policy-test`, and rebuilt checkout-current coverage successfully.
- [x] (2026-06-09 17:20Z) Reran the targeted mutation command for `transfer_policy.cljs`; the module now reports `22/22` killed and `0` uncovered.
- [x] (2026-06-09 17:21Z) Ran the required repository gates: `npm run check`, `npm test`, and `npm run test:websocket`.

## Surprises & Discoveries

- Observation: The module already has much broader direct and property-based coverage than its current `0.0%` mutation score suggests.
  Evidence: `test/hyperopen/vaults/domain/transfer_policy_test.cljs` already covers integer, trailing-decimal, leading-decimal, truncation, zero, overflow, localized parsing, invalid vault routing, disabled deposits, and withdraw-all preview behavior; `test/hyperopen/vaults/domain/transfer_policy_properties_test.cljs` also exercises parser, deposit-eligibility, and preview properties with `quick-check`.

- Observation: All three uncovered selected mutants are outside the existing direct example tests and cluster around the mode/deposit guard seams.
  Evidence: The nightly artifact lists uncovered sites at `src/hyperopen/vaults/domain/transfer_policy.cljs:21`, `:50`, and `:51`, which map to `normalize-vault-transfer-mode` fallback and the `leader?` / `liquidator-vault?` decision points in `vault-transfer-deposit-allowed?`.

- Observation: The surviving preview mutants were not a simple assertion gap; the main `npm test` process was exiting before the vault namespaces ran.
  Evidence: After adding stronger direct assertions, the targeted mutation rerun for `transfer_policy.cljs` stayed at `0/19` killed. A manual source edit that flipped an invalid-address branch from `:ok? false` to `:ok? true` still left `npm test` green, and a temporary `(is false)` added to `transfer_policy_test.cljs` also left `npm test` at exit code `0`.

- Observation: The root cause is an async misuse in the custom JavaScript test runner, not in `transfer_policy.cljs`.
  Evidence: `test/test_runner.cljs` treated `generated-runner/run-generated-tests` as if it returned final results synchronously, but `cljs.test/run-tests` explicitly documents that it does not return a meaningful value because asynchronous tests are allowed. The log from `/tmp/transfer-policy-test-after-runner-fix.log` showed `=== Test Results ===` after only three namespaces, then many more namespaces continued streaming until the process stopped long before any `hyperopen.vaults.*` namespace executed.

- Observation: Fixing the runner exposed one real deadlocked async test and one stale contract expectation elsewhere in the suite.
  Evidence: `test/hyperopen/subaccounts/owner_mode_test.cljs` waited for `load-subaccounts!` to resolve before resolving its mocked owner-mode promise, even though `load-subaccounts!` itself awaited that promise; the suite consistently stopped at `hyperopen.subaccounts.owner-mode-test` until that deadlock was removed. After the suite progressed further, `test/hyperopen/views/portfolio/optimize/frontier_chart_contract_test.cljs` still expected current exposure to render as `30.00%` even though `frontier_callout.cljs` formats exposure as a multiplier (`0.30x`), matching the dedicated `frontier_callout_test.cljs` contract.

## Decision Log

- Decision: Scope this pass to `src/hyperopen/vaults/domain/transfer_policy.cljs` only.
  Rationale: It is the clear outlier from the nightly sweep, owning `19` of `21` total survivors and all `3` uncovered selected mutants. Fixing that seam first gives the highest leverage and keeps the validation cycle small enough for an immediate targeted mutation rerun.
  Date/Author: 2026-06-09 / Codex

- Decision: Start with test-only hardening and only change production code if a new failing test exposes a real behavioral defect.
  Rationale: The current behavior already has direct examples, property tests, and formal-vector conformance coverage. Mutation findings alone do not justify production changes if stronger tests close the gap.
  Date/Author: 2026-06-09 / Codex

- Decision: Pivot this plan to repair the JavaScript test runner before making any further claims about the vault transfer mutation score.
  Rationale: The normal test entrypoint was not executing the target namespace at all, so targeted mutation numbers could not improve no matter how many transfer-policy assertions were added. Fixing the runner is now the narrowest change that makes the original mutation objective measurable.
  Date/Author: 2026-06-09 / Codex

## Outcomes & Retrospective

Implementation started as transfer-policy test hardening, then pivoted when the runner fault boundary became clear. The final change set did four things. First, `tools/generate-test-runner.mjs` and `test/test_runner.cljs` now run the generated suite with an explicit `cljs.test` environment instead of assuming synchronous completion. Second, `test/hyperopen/test_runner_support.cljs` now provides a custom reporter that preserves normal `cljs.test` output, applies the exit code from the final `:end-run-tests` summary, and explicitly terminates Node after the summary so leftover handles cannot hang the process. Third, the stronger transfer-policy assertions stayed in place and a deadlocked owner-mode test plus a stale frontier exposure assertion were corrected once the real suite finally ran to completion. Fourth, the targeted mutation rerun for `src/hyperopen/vaults/domain/transfer_policy.cljs` improved from nightly `0/19 killed, 3 uncovered` to `22/22 killed, 0 uncovered, 100.0%`.

Validation results:

- `npm test` -> passed, `4484` tests and `24745` assertions, `0` failures, `0` errors.
- `npm run coverage` -> passed, produced checkout-current `coverage/lcov.info`.
- `bb tools/mutate.clj --module src/hyperopen/vaults/domain/transfer_policy.cljs --suite test --mutate-all` -> passed, `22/22` killed, `0` uncovered, report `target/mutation/reports/2026-06-09T17-19-29.412503Z-src-hyperopen-vaults-domain-transfer_policy.cljs.edn`.
- `npm run test:websocket` -> passed, `534` tests and `3090` assertions, `0` failures, `0` errors.
- `npm run check` -> passed.

No residual transfer-policy mutants remain from this targeted pass, so there is nothing to classify as equivalent or tool-limited for this module.

## Context and Orientation

`src/hyperopen/vaults/domain/transfer_policy.cljs` is a pure domain namespace used by the vault detail transfer UI. It does three jobs. First, `normalize-vault-transfer-mode` coerces user input into either `:deposit` or `:withdraw`, defaulting to `:deposit` for anything invalid. Second, `parse-usdc-micros` turns localized decimal text into integer USDC micro-units with truncation to six decimals and a max-safe-integer ceiling. Third, `vault-transfer-preview` combines the normalized mode, route or modal vault address, deposit-eligibility rules, and parsed amount into either a user-facing error result or a request payload for the actual vault transfer effect.

The focused tests already live in `test/hyperopen/vaults/domain/transfer_policy_test.cljs` and `test/hyperopen/vaults/domain/transfer_policy_properties_test.cljs`. The direct test namespace is where explicit RED tests should go first because it is the clearest place to encode branch-specific expectations from the mutation artifact. The property test namespace should only change if a missing generator case or overly-symmetric property is part of the problem.

The nightly artifact identified these current hotspots:

- Uncovered selected mutants at lines `21`, `50`, and `51`, meaning direct tests do not currently force the invalid-mode fallback or the exact leader-versus-liquidator gating branches.
- Surviving parser mutants at lines `33` through `40`, which cover leading-decimal defaults, fraction padding, micros arithmetic, and max-safe-integer guarding.
- Surviving preview mutants at lines `63` through `102`, which cover non-map modal fallback, route-fallback guards, withdraw-all amount bypass, invalid/zero result maps, and the `:isDeposit` request flag.

This plan treats mutation improvement as the user-visible outcome. The goal is not to rewrite the transfer logic; it is to make the behavioral contract explicit enough that the mutation tool can see the intended semantics.

## Plan of Work

First, keep the stronger direct tests in `test/hyperopen/vaults/domain/transfer_policy_test.cljs` for the exact branches named by the nightly artifact. Those assertions remain useful, but they are no longer the first blocker.

Second, repair the generated JavaScript test runner. `tools/generate-test-runner.mjs` should emit a runner that accepts an explicit `cljs.test` environment, and `test/test_runner.cljs` should use a custom reporter environment from `test/hyperopen/test_runner_support.cljs` so the process exit code is set only from the true `:end-run-tests` callback.

Third, rerun `npm test` and verify that the suite reaches the vault namespaces. If the repaired runner exposes real failing tests elsewhere in the suite, treat those as newly surfaced pre-existing failures and record them precisely before deciding whether they block the original mutation goal.

Finally, rebuild coverage if needed and rerun the module-level mutation command for `src/hyperopen/vaults/domain/transfer_policy.cljs`. Use the result to decide whether the change is complete or whether any residual mutant must be documented here as equivalent or tool-limited. Then run the required repository gates, record evidence, and move this plan to `completed`.

## Concrete Steps

From `/Users/barry/.codex/worktrees/56b4/hyperopen`:

1. Repair `test/hyperopen/test_runner_support.cljs`, `test/test_runner.cljs`, and `tools/generate-test-runner.mjs` so the generated runner accepts a custom env and waits for `:end-run-tests` before setting `process.exitCode`.

2. Regenerate `test/test_runner_generated.cljs`:

       node tools/generate-test-runner.mjs

3. Re-run the full main suite and confirm it reaches the vault namespaces:

       npm test

4. Rebuild coverage for the current checkout:

       npm run coverage

5. Rerun the targeted mutation command for this module:

       bb tools/mutate.clj --module src/hyperopen/vaults/domain/transfer_policy.cljs --suite test --mutate-all

6. Run the repository gates required by `AGENTS.md`:

       npm run check
       npm test
       npm run test:websocket

Expected outcome after the change: `npm test` now executes the vault namespaces and exits from the final `cljs.test` summary, the targeted mutation report for `transfer_policy.cljs` materially improves from the nightly `0/19` result, and the required repository gates stay green.

## Validation and Acceptance

Acceptance requires five things. First, the stronger transfer-policy tests must remain in place for the invalid-mode fallback, leader-versus-liquidator deposit gating, route-fallback guard, withdraw-all request shaping, and parser boundary behavior that the nightly report marked weak. Second, `npm test` must reach the `hyperopen.vaults.*` namespaces and derive its exit code from the final `cljs.test` summary rather than a premature synchronous return. Third, the targeted mutation rerun for `src/hyperopen/vaults/domain/transfer_policy.cljs` must improve enough that the current uncovered sites are gone and the survivor count is materially lower, or any remaining sites must be explained here with concrete evidence. Fourth, `npm run check`, `npm test`, and `npm run test:websocket` must pass. Fifth, no public vault-transfer API or request shape may change unless a failing test proves the current behavior was actually wrong.

## Idempotence and Recovery

The planned edits are safe to rerun. Test additions are additive, and the mutation tool restores the original module source after each run through its own backup logic. If a production change turns out to be unnecessary, revert only the transfer-policy source edit and keep the stronger tests. If coverage rebuild fails because the JavaScript dependency tree disappears in this worktree, rerun `test -d node_modules/lucide || npm ci` before retrying the mutation flow.

## Artifacts and Notes

Key mutation evidence already available before implementation:

    target/mutation/nightly/2026-06-09T15-03-33.091457Z/summary.md
      transfer_policy.cljs -> executed 19, killed 0, survived 19, uncovered 3, kill 0.0%

    target/mutation/reports/2026-06-09T15-30-01.277098Z-src-hyperopen-vaults-domain-transfer_policy.cljs.edn
      uncovered: line 21 (if -> if-not), line 50 (= -> not=), line 51 (= -> not=)
      survivors: parser lines 33-40 and preview lines 63-102, including withdraw-all and invalid-result branches

    target/mutation/reports/2026-06-09T17-19-29.412503Z-src-hyperopen-vaults-domain-transfer_policy.cljs.edn
      killed: all 22 selected mutation sites
      uncovered: none
      summary: 22/22 mutants killed (100.0%)

Primary files for this pass:

- `/hyperopen/src/hyperopen/vaults/domain/transfer_policy.cljs`
- `/hyperopen/test/hyperopen/vaults/domain/transfer_policy_test.cljs`
- `/hyperopen/test/hyperopen/vaults/domain/transfer_policy_properties_test.cljs`
- `/hyperopen/test/hyperopen/test_runner_support.cljs`
- `/hyperopen/test/test_runner.cljs`
- `/hyperopen/tools/generate-test-runner.mjs`

## Interfaces and Dependencies

No public interface changes are planned. The existing functions that must remain stable are:

- `hyperopen.vaults.domain.transfer-policy/normalize-vault-transfer-mode`
- `hyperopen.vaults.domain.transfer-policy/parse-usdc-micros`
- `hyperopen.vaults.domain.transfer-policy/vault-transfer-deposit-allowed?`
- `hyperopen.vaults.domain.transfer-policy/vault-transfer-preview`

The work depends on the existing ClojureScript test runner, the repo-local mutation tool in `tools/mutate.clj`, and the current LCOV coverage flow used by the nightly sweep.

Plan revision note, 2026-06-09 16:28Z / Codex: Initial plan created from the 2026-06-09 nightly mutation artifact and scoped to the highest-priority `transfer_policy.cljs` follow-up.
Plan revision note, 2026-06-09 16:50Z / Codex: Scope pivoted after proving `npm test` exited before any vault namespace ran; the plan now repairs the JavaScript test runner first so transfer-policy mutation results become measurable.
Plan revision note, 2026-06-09 17:21Z / Codex: Validation completed successfully. The runner repair is now part of the solution because it was the real prerequisite for measuring `transfer_policy.cljs` mutation coverage correctly.
