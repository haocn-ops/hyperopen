# Split Oversized Portfolio Test Suites

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and tracks live work in `bd` issue `hyperopen-ulzt`.

## Purpose / Big Picture

The portfolio tests recently grew past the repository namespace-size threshold while adding coverage for portfolio benchmark actions and fee-schedule context behavior. After this change, contributors can run and edit narrower suites: general portfolio actions stay in `hyperopen.portfolio.actions-test`, returns-benchmark action behavior lives in `hyperopen.portfolio.benchmark-actions-test`, fee row math stays in `hyperopen.portfolio.fee-schedule-test`, and wallet plus active-market fee context behavior lives in `hyperopen.portfolio.fee-context-test`. The observable result is that generated test discovery includes the new namespaces, the focused portfolio test command passes, and `npm run check` no longer needs the temporary oversized test namespace exceptions.

## Progress

- [x] (2026-04-20 01:24Z) Created and claimed `bd` task `hyperopen-ulzt`; verified the worktree is isolated and switched from detached HEAD to branch `codex/portfolio-test-suite-split`.
- [x] (2026-04-20 01:25Z) Inspected current oversized suites, generated runner discovery, namespace-size policy, and portfolio boundary docs.
- [x] (2026-04-20 01:28Z) Split `test/hyperopen/portfolio/actions_test.cljs` into general action coverage and new `test/hyperopen/portfolio/benchmark_actions_test.cljs` benchmark action coverage.
- [x] (2026-04-20 01:31Z) Split `test/hyperopen/portfolio/fee_schedule_test.cljs` into fee row math coverage and new `test/hyperopen/portfolio/fee_context_test.cljs` fee context coverage.
- [x] (2026-04-20 01:32Z) Removed the temporary portfolio test namespace-size exceptions and updated portfolio boundary docs to name the new test namespaces.
- [x] (2026-04-20 01:32Z) Regenerated `test/test_runner_generated.cljs`; remaining work is focused portfolio test execution.
- [x] (2026-04-20 01:33Z) Ran focused portfolio tests; 37 tests and 236 assertions passed with zero failures and zero errors.
- [x] (2026-04-20 01:35Z) Ran `npm run lint:namespace-sizes`; namespace size check passed.
- [x] (2026-04-20 01:36Z) Ran `npm run check`; command completed successfully after regenerating the runner, running lint/tooling tests, and compiling app, portfolio, worker, websocket worker, and test builds.
- [x] (2026-04-20 01:37Z) Ran `npm test`; 3312 tests and 18085 assertions passed with zero failures and zero errors.
- [x] (2026-04-20 01:38Z) Ran `npm run test:websocket`; 449 tests and 2701 assertions passed with zero failures and zero errors.
- [x] (2026-04-20 01:36Z) Closed `hyperopen-ulzt`; moving this ExecPlan to `docs/exec-plans/completed/` is the final archival step.

## Surprises & Discoveries

- Observation: `tools/generate-test-runner.mjs` recursively discovers every file under `test/hyperopen` ending in `_test.cljs` and derives namespaces from file paths by replacing `/` with `.` and `_` with `-`.
  Evidence: `test/hyperopen/portfolio/benchmark_actions_test.cljs` will be discovered as `hyperopen.portfolio.benchmark-actions-test`; no handwritten runner registration is needed beyond running `npm run test:runner:generate`.
- Observation: the namespace-size threshold is 500 lines and the temporary exceptions for `test/hyperopen/portfolio/actions_test.cljs` and `test/hyperopen/portfolio/fee_schedule_test.cljs` were explicitly added as deferrals for this exact split.
  Evidence: `dev/check_namespace_sizes.clj` defines threshold `500`; `dev/namespace_size_exceptions.edn` entries mention splitting benchmark/popover action tests and active-market fee-schedule context cases.

## Decision Log

- Decision: Use `test/hyperopen/portfolio/benchmark_actions_test.cljs` for all returns benchmark command tests rather than nesting under `test/hyperopen/portfolio/actions/`.
  Rationale: The generated namespace stays short and obvious, `hyperopen.portfolio.benchmark-actions-test`, and the suite tests the public `hyperopen.portfolio.actions` seam while narrowing by behavior.
  Date/Author: 2026-04-20 / Codex.
- Decision: Use `test/hyperopen/portfolio/fee_context_test.cljs` for wallet status, current fee, selected scenario, and active market context model tests.
  Rationale: These tests all exercise `fee-schedule-model` context derivation, while `fee_schedule_test.cljs` can remain focused on normalizers, options, base rows, and selected discount row math.
  Date/Author: 2026-04-20 / Codex.
- Decision: Do not change production code or public APIs.
  Rationale: This is a test-boundary refactor; preserving all assertions while moving them keeps coverage behavior stable and minimizes blast radius.
  Date/Author: 2026-04-20 / Codex.

## Outcomes & Retrospective

This section will be filled after implementation and validation. The expected outcome is lower test-suite file size, no temporary exceptions for the split portfolio test namespaces, and unchanged test coverage behavior.

Implementation reduced test-maintenance complexity by replacing two oversized mixed-responsibility namespaces with four narrower suites. The general action suite is now 418 lines, the benchmark action suite is 297 lines, the fee schedule row suite is 203 lines, and the fee context suite is 320 lines. No production behavior changed; only tests, generated test discovery, namespace-size exception cleanup, and portfolio boundary documentation changed.

## Context and Orientation

The repository generates its ClojureScript test runner from files under `test/hyperopen`. The generator lives at `tools/generate-test-runner.mjs`; a test file path like `test/hyperopen/portfolio/fee_context_test.cljs` becomes namespace `hyperopen.portfolio.fee-context-test`. The file `test/test_runner_generated.cljs` is generated and tracked, so it must be regenerated after creating new test files.

The current oversized portfolio action suite is `test/hyperopen/portfolio/actions_test.cljs`. It requires `cljs.test`, `hyperopen.platform`, and `hyperopen.portfolio.actions`. It contains two kinds of assertions: general portfolio UI actions such as dropdown toggles, fee schedule popover commands, tab selection, and time-range normalization; and returns benchmark action assertions such as benchmark search state, benchmark token normalization, vault benchmark fetch planning, benchmark selection/removal, keydown handling, and candle request windows.

The current oversized fee schedule suite is `test/hyperopen/portfolio/fee_schedule_test.cljs`. It requires `cljs.test` and `hyperopen.portfolio.fee-schedule`. It also contains two kinds of assertions: fee schedule row and option behavior such as market type normalization, option labels, protocol row variants, and selected discounts; and context model assertions involving wallet connection state, active referral/staking/maker values, local what-if overrides, active spot market context, active HIP-3 market context, and current fee boundary behavior.

The file `dev/namespace_size_exceptions.edn` currently contains temporary entries for the two oversized test files. Once the split drops both files below 500 lines, those exception entries must be removed. The file `src/hyperopen/portfolio/BOUNDARY.md` lists key portfolio test namespaces and should name the new focused suites so future contributors know where to place benchmark action and fee context coverage.

## Plan of Work

First, create `test/hyperopen/portfolio/benchmark_actions_test.cljs` with namespace `hyperopen.portfolio.benchmark-actions-test`. Move the private `replace-shareable-route-query-effect` helper into this file too, because benchmark selection and keydown tests assert the route-query replacement effect. Move the returns benchmark tests from `actions_test.cljs`: search/open state, benchmark coin normalization, vault benchmark address normalization, missing vault benchmark effect planning, select and clear, remove, keydown handling, and candle request windows. Also move the benchmark-fetching assertions currently embedded in `select-portfolio-summary-time-range-normalizes-and-closes-dropdowns-test` and `select-portfolio-chart-tab-normalizes-and-saves-selected-tab-test` into focused tests in the new suite. Leave non-benchmark time-range and tab normalization assertions in `actions_test.cljs`.

Second, create `test/hyperopen/portfolio/fee_context_test.cljs` with namespace `hyperopen.portfolio.fee-context-test`. Move all `fee-schedule-model` context tests from `fee_schedule_test.cljs`: disconnected and connected status, active spot stable aligned market context, active HIP-3 growth aligned context, active market classification boundaries, current wallet discounts from rates, wallet/current fee boundaries, and scenario preview labels. Keep `fee_schedule_test.cljs` focused on normalizers, market options, row rendering, and selected discount row math.

Third, edit `dev/namespace_size_exceptions.edn` to remove the two entries for `test/hyperopen/portfolio/actions_test.cljs` and `test/hyperopen/portfolio/fee_schedule_test.cljs`. Edit `src/hyperopen/portfolio/BOUNDARY.md` so the key test namespace list includes `hyperopen.portfolio.benchmark-actions-test`, `hyperopen.portfolio.fee-schedule-test`, and `hyperopen.portfolio.fee-context-test`.

Fourth, run `npm run test:runner:generate` to update `test/test_runner_generated.cljs`. Then run a focused command from `/Users/barry/.codex/worktrees/bfba/hyperopen`:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.actions-test --test=hyperopen.portfolio.benchmark-actions-test --test=hyperopen.portfolio.fee-schedule-test --test=hyperopen.portfolio.fee-context-test

The focused command should compile the test build and report all four portfolio namespaces with zero failures and zero errors.

Finally, run the required repository gates from `AGENTS.md`: `npm run check`, `npm test`, and `npm run test:websocket`. If all pass, update this plan with evidence, move it to `docs/exec-plans/completed/`, and close `hyperopen-ulzt` with reason `Completed`.

## Concrete Steps

1. Edit `test/hyperopen/portfolio/actions_test.cljs` to remove benchmark-only assertions and keep the general action tests.
2. Add `test/hyperopen/portfolio/benchmark_actions_test.cljs` containing the moved benchmark action assertions.
3. Edit `test/hyperopen/portfolio/fee_schedule_test.cljs` to remove fee context model assertions and keep row/option assertions.
4. Add `test/hyperopen/portfolio/fee_context_test.cljs` containing the moved fee context model assertions.
5. Remove the two portfolio test exceptions from `dev/namespace_size_exceptions.edn`.
6. Update `src/hyperopen/portfolio/BOUNDARY.md` with the new key test namespaces.
7. Run `npm run test:runner:generate`.
8. Run the focused portfolio test command listed above.
9. Run `npm run check`, `npm test`, and `npm run test:websocket`.

## Validation and Acceptance

Acceptance requires all of the following:

The generated runner `test/test_runner_generated.cljs` includes `hyperopen.portfolio.benchmark-actions-test` and `hyperopen.portfolio.fee-context-test`.

The focused portfolio command runs `hyperopen.portfolio.actions-test`, `hyperopen.portfolio.benchmark-actions-test`, `hyperopen.portfolio.fee-schedule-test`, and `hyperopen.portfolio.fee-context-test` with zero failures and zero errors.

`npm run lint:namespace-sizes` passes without entries for `test/hyperopen/portfolio/actions_test.cljs` or `test/hyperopen/portfolio/fee_schedule_test.cljs`.

The required gates `npm run check`, `npm test`, and `npm run test:websocket` pass.

No production source files under `src/hyperopen` change except the documentation file `src/hyperopen/portfolio/BOUNDARY.md`.

## Idempotence and Recovery

The edits are test-only plus documentation, generated runner, and exception cleanup. If a moved test fails, compare the moved assertion with the original source in git history and restore the exact required helper or namespace require. Running `npm run test:runner:generate` is safe to repeat; it deterministically rewrites `test/test_runner_generated.cljs`. If namespace-size lint fails, run `wc -l` on the four target test files and either finish the intended split or restore a bounded exception only if a file still has a justified size above 500 lines.

## Artifacts and Notes

Initial file sizes before the split:

    705 test/hyperopen/portfolio/actions_test.cljs
    520 test/hyperopen/portfolio/fee_schedule_test.cljs

Initial temporary exceptions to retire:

    test/hyperopen/portfolio/actions_test.cljs
    test/hyperopen/portfolio/fee_schedule_test.cljs

Focused portfolio validation:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.actions-test --test=hyperopen.portfolio.benchmark-actions-test --test=hyperopen.portfolio.fee-schedule-test --test=hyperopen.portfolio.fee-context-test
    Ran 37 tests containing 236 assertions.
    0 failures, 0 errors.

Required gate evidence:

    npm run lint:namespace-sizes
    Namespace size check passed.

    npm test
    Ran 3312 tests containing 18085 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 449 tests containing 2701 assertions.
    0 failures, 0 errors.

## Interfaces and Dependencies

All tests continue to call public functions from `hyperopen.portfolio.actions` and `hyperopen.portfolio.fee-schedule`. The helper `replace-shareable-route-query-effect` is private test data and may be duplicated in `actions_test.cljs` and `benchmark_actions_test.cljs` because each namespace is an independent test suite. No production interfaces, function signatures, action names, effect names, or public namespaces are changed by this plan.

Revision note, 2026-04-20 / Codex: Updated the living plan after implementation to record the exact split, validation evidence, and complexity outcome before moving the plan to completed.
