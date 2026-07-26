# Harden Spot Sell Affordability Mutation Coverage

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. Keep it self-contained, update it whenever implementation discoveries change the plan, and leave at least one unchecked progress item while the plan remains active.

## Purpose / Big Picture

The 2026-06-20 nightly mutation sweep found one surviving mutant in `/hyperopen/src/hyperopen/domain/trading/validation.cljs`. The original production behavior allows a classic spot sell when the submitted size exactly equals the base-token balance, because the local affordability check should reject only sizes greater than available balance. The mutation changed `>` to `>=` at line 131, which would incorrectly reject the exact-balance sell, and the existing tests did not catch it.

After this plan is complete, the test suite should explicitly protect that equality boundary. A future mutation that changes the sell check from `>` to `>=` should fail the focused validation test and the targeted mutation command should report the mutant killed.

## Context References

Public refs:

- Direct user request on 2026-06-20: "Create an execution plan and address the one survivor."

Repo artifacts:

- `/hyperopen/AGENTS.md` requires an ExecPlan for risky bug work and requires `npm run check`, `npm test`, and `npm run test:websocket` when code changes.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define the active ExecPlan contract.
- `/hyperopen/target/mutation/nightly/2026-06-20T01-02-16.844363Z/summary.md` records the nightly result: 4/4 targets passed, 106/107 mutants killed, 1 survivor, 0 uncovered selected mutants, 99.1% overall kill rate.
- `/hyperopen/target/mutation/reports/2026-06-20T01-24-12.811893Z-src-hyperopen-domain-trading-validation.cljs.edn` records the survivor in `src/hyperopen/domain/trading/validation.cljs`: line 131, mutation `> -> >=`, category `:comparison`.

Local scratch refs, non-authoritative:

- `/Users/barry/.codex/automations/daily-bug-scan/memory.md` records the same survivor and compares it with the 2026-06-14 automation baseline.

## Progress

- [x] (2026-06-20 02:16Z) Read the nightly mutation memory, summary artifact, survivor location, current validation source, existing trading validation tests, and ExecPlan requirements.
- [x] (2026-06-20 02:18Z) Created this active ExecPlan and scoped the change to test coverage for the existing spot sell equality behavior.
- [x] (2026-06-20 15:12Z) Added an explicit test proving classic spot sells at exactly the held base balance validate without `:spot/insufficient-base-balance`.
- [x] (2026-06-20 15:15Z) Corrected the spot fixture to include the base-token balance and to make the over-balance case sell 6 PURR against 5 PURR available.
- [x] (2026-06-20 15:16Z) Verified RED by temporarily changing the production comparison from `>` to `>=`; the new exact-balance assertion failed with `:spot/insufficient-base-balance`.
- [x] (2026-06-20 15:17Z) Reverted the temporary mutant and confirmed the generated CLJS test path passed with 4765 tests, 26392 assertions, 0 failures, and 0 errors.
- [x] (2026-06-20 15:23Z) Reran the targeted mutation command for `src/hyperopen/domain/trading/validation.cljs`; all 22 mutants were killed, including line 131 `> -> >=`.
- [x] (2026-06-20 15:25Z) Ran `npm test` and `npm run test:websocket` successfully.
- [ ] Re-run `npm run check` after the unrelated stale-doc guard for `docs/design-docs/core-beliefs.md` is resolved or explicitly accepted.

## Surprises & Discoveries

- Observation: Existing spot affordability coverage already checks classic spot sell over-balance rejection and unified-account skip behavior, but it does not check the exact-balance boundary.
  Evidence: `test/hyperopen/state/trading/validation_and_scale_test.cljs` has `classic account caps a spot sell at held base balance` with size `5` and no base balance, but no assertion where size equals available base.

- Observation: The original minimal `spot-context` fixture could not represent a meaningful base-balance boundary because it had no `:active-asset`, no spot `:coin`, and no base-token balance.
  Evidence: After the first test addition, the restored production comparison still failed the exact-balance assertion because `market/spot-base-available` resolved the available base balance as 0. The corrected fixture now uses `:active-asset "PURR"`, `:coin "PURR/USDC"`, and a `PURR` balance of 5.

- Observation: `npm run check` is currently blocked before compile validation by an unrelated governed-doc staleness failure.
  Evidence: `npm run check` exits during `npm run lint:docs` with `[stale-doc] docs/design-docs/core-beliefs.md - document is stale: 94 days old, max allowed 90`.

## Decision Log

- Decision: Treat the production code as correct and address the survivor with a test-only change unless RED evidence shows otherwise.
  Rationale: The docstring for `spot-affordability-errors` says "base-token available >= size"; line 131 already implements this by rejecting only when `size` is greater than available base. The survivor means the equality case is untested, not that the source branch should change.
  Date/Author: 2026-06-20 / Codex

- Decision: Prove RED by applying the exact surviving mutation manually and running the normal CLJS test path.
  Rationale: A new boundary test should pass against current correct source, so the meaningful failing state is the mutant reported by the mutation tool. A temporary manual mutant gives a fast, explicit RED proof before reverting to the intended source.
  Date/Author: 2026-06-20 / Codex

- Decision: Do not edit `docs/design-docs/core-beliefs.md` as part of this survivor fix.
  Rationale: The stale-doc failure is unrelated to the trading validation mutation survivor. Updating a canonical design doc's review metadata without actually reviewing that document would hide governance work outside this plan's scope.
  Date/Author: 2026-06-20 / Codex

## Outcomes & Retrospective

The mutation survivor is addressed with a test-only hardening patch. The production validation comparison remains unchanged at `>`, and the new test makes the intended equality boundary explicit: a classic spot sell of exactly the held base balance is valid, while selling above that balance still returns `:spot/insufficient-base-balance`. This reduces ambiguity without adding production complexity.

Validation is complete for the changed behavior except for the unrelated `npm run check` docs-staleness blocker. The RED run failed against the temporary manual `>=` mutant with one expected failure and zero errors. The restored-source GREEN run passed with 4765 tests, 26392 assertions, 0 failures, and 0 errors. The targeted mutation rerun reported `22/22 mutants killed (100.0%)`, including line 131 `> -> >=`. `npm test` passed with 4765 tests and 26392 assertions. `npm run test:websocket` passed with 545 tests and 3131 assertions. `npm run check` failed before compile validation on `docs/design-docs/core-beliefs.md` stale-doc metadata, which is outside this plan's behavioral scope.

## Context and Orientation

`src/hyperopen/domain/trading/validation.cljs` is the pure domain namespace that turns an order form into validation errors. Its private helper `spot-affordability-errors` runs only when the current market is a spot market, balances are loaded, the account is not in unified portfolio-margin mode, and the parsed order size is positive. For spot buys it rejects when `size * reference price` is greater than available USDC. For spot sells it rejects when `size` is greater than available base-token balance.

The relevant test namespace is `test/hyperopen/state/trading/validation_and_scale_test.cljs`. It already imports `hyperopen.domain.trading.validation` as `domain-validation` and defines `validation-codes` for comparing error code sets. The existing `spot-affordability-skips-unified-portfolio-margin-test` builds a minimal spot context and is the right place to add the equality boundary assertion.

## Plan of Work

First, add an assertion to `spot-affordability-skips-unified-portfolio-margin-test`. The minimal context must include a spot market, classic account mode, `:active-asset "PURR"`, spot market coin `"PURR/USDC"`, and balances for both USDC and the PURR base token. The new test should call `domain-validation/validate-order-form` with `{:type :market :side :sell :size "5"}` and expect an empty validation-code set when the base-token available amount is exactly `5`. The existing over-balance sell case should sell `6` against 5 available PURR so it continues proving the rejection branch.

Second, prove the test kills the survivor. Temporarily edit `src/hyperopen/domain/trading/validation.cljs` line 131 from `(> size (market/spot-base-available context))` to `(>= size (market/spot-base-available context))`. Run `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js` and confirm the new assertion fails with `:spot/insufficient-base-balance` present for the exact-balance sell. Immediately revert only the temporary comparison edit.

Third, run the same generated CLJS test path again against the restored source and confirm the suite passes. Then run `bb tools/mutate.clj --module src/hyperopen/domain/trading/validation.cljs --suite test --mutate-all` and confirm the previously surviving line 131 mutant is killed.

Finally, run `npm run check`, `npm test`, and `npm run test:websocket` as required by `AGENTS.md`, update this ExecPlan with evidence, and move it from `docs/exec-plans/active/` to `docs/exec-plans/completed/`.

## Concrete Steps

From `/Users/barry/.codex/worktrees/5100/hyperopen`:

1. Edit `test/hyperopen/state/trading/validation_and_scale_test.cljs` inside `spot-affordability-skips-unified-portfolio-margin-test` so the classic-account block includes this assertion:

       (testing "classic account allows selling exactly the held base balance"
         (is (empty? (domain-validation/validate-order-form
                      (spot-context :classic)
                      {:type :market :side :sell :size "5"}))))

2. Temporarily edit `src/hyperopen/domain/trading/validation.cljs` line 131 to inject the survivor:

       (when (>= size (market/spot-base-available context))

3. Run:

       npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

   Expected RED result: the new equality assertion fails because the temporary mutant emits `:spot/insufficient-base-balance`.

4. Revert the temporary source edit back to:

       (when (> size (market/spot-base-available context))

5. Run:

       npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

   Expected GREEN result: the suite passes with 0 failures and 0 errors.

6. Run:

       bb tools/mutate.clj --module src/hyperopen/domain/trading/validation.cljs --suite test --mutate-all

   Actual mutation result on 2026-06-20: 22/22 selected mutants killed, 0 survivors, 0 uncovered selected mutants. Report path:

       target/mutation/reports/2026-06-20T15-23-27.746285Z-src-hyperopen-domain-trading-validation.cljs.edn

7. Run the repository gates:

       npm run check
       npm test
       npm run test:websocket

   Actual result on 2026-06-20: `npm test` and `npm run test:websocket` exit 0. `npm run check` exits 1 during `npm run lint:docs` because `docs/design-docs/core-beliefs.md` is stale at 94 days with a 90-day max.

## Validation and Acceptance

Acceptance requires five things. First, the new test must fail against the exact temporary `>=` mutant and pass after reverting to the intended `>` source. This is satisfied. Second, the targeted mutation command for `src/hyperopen/domain/trading/validation.cljs` must no longer report the line 131 `> -> >=` survivor. This is satisfied by the 22/22 targeted mutation report. Third, no production trading validation behavior may change except the temporary RED mutation that is reverted before completion. This is satisfied; `src/hyperopen/domain/trading/validation.cljs` has no permanent diff. Fourth, `npm test` and `npm run test:websocket` must pass. This is satisfied. Fifth, `npm run check` must pass before this plan moves to `completed/`; this remains blocked by an unrelated stale-doc guard.

Browser QA is not required because this change is pure domain validation coverage and does not touch UI code, browser flows, browser storage, styles, or interaction behavior.

## Idempotence and Recovery

The test addition is additive and safe to rerun. The temporary manual mutation must be reverted before any final validation or handoff. If the RED command fails for syntax or compilation reasons, revert the temporary source edit, run `git diff` to inspect only intentional changes, fix the test syntax, and repeat the RED step. Do not use destructive git commands for recovery.

If a validation command fails because local dependencies are missing, run `test -d node_modules/lucide || npm ci` and retry the same command once. If the mutation command leaves a backup or partial restore file, inspect `git diff` and restore only the intended source comparison manually.

## Artifacts and Notes

Key nightly evidence available before implementation:

    target/mutation/nightly/2026-06-20T01-02-16.844363Z/summary.md
      src/hyperopen/domain/trading/validation.cljs -> executed 22, killed 21, survived 1, uncovered 0, kill 95.5%

    target/mutation/reports/2026-06-20T01-24-12.811893Z-src-hyperopen-domain-trading-validation.cljs.edn
      survivor: line 131, column 15, comparison mutation `> -> >=`, suite `:test`

Implementation evidence:

    RED, temporary manual mutant `(>= size ...)`:
      Ran 4765 tests containing 26392 assertions.
      1 failures, 0 errors.
      Failure: `classic account allows selling exactly the held base balance`
      Actual error code: `:spot/insufficient-base-balance`

    GREEN, restored source `(> size ...)`:
      Ran 4765 tests containing 26392 assertions.
      0 failures, 0 errors.

    Targeted mutation:
      bb tools/mutate.clj --module src/hyperopen/domain/trading/validation.cljs --suite test --mutate-all
      [  9/22] KILLED   L131  test         > -> >=
      Summary: 22/22 mutants killed (100.0%)

    Required gates:
      npm test -> Ran 4765 tests containing 26392 assertions. 0 failures, 0 errors.
      npm run test:websocket -> Ran 545 tests containing 3131 assertions. 0 failures, 0 errors.
      npm run check -> failed at `npm run lint:docs` with `[stale-doc] docs/design-docs/core-beliefs.md - document is stale: 94 days old, max allowed 90`.

The exact source branch at the start of the plan:

    (if (= :sell (:side form))
      (when (> size (market/spot-base-available context))
        [(validation-error :spot/insufficient-base-balance)])
      ...)

## Interfaces and Dependencies

No public interface changes are planned. The stable function under test is:

- `hyperopen.domain.trading.validation/validate-order-form`

The test should continue using existing helpers and namespaces:

- `hyperopen.domain.trading.validation` as `domain-validation`
- `validation-codes` in `test/hyperopen/state/trading/validation_and_scale_test.cljs`

No new libraries, runtime dependencies, browser tools, or public APIs are needed.

Plan revision note, 2026-06-20 02:18Z / Codex: Initial active ExecPlan created from the 2026-06-20 nightly mutation artifact and scoped to a test-only equality-boundary hardening pass.
Plan revision note, 2026-06-20 15:25Z / Codex: Recorded the completed RED/GREEN test cycle, the targeted 22/22 mutation result, passing `npm test` and websocket gates, and the unrelated `npm run check` stale-doc blocker that keeps this plan active.
