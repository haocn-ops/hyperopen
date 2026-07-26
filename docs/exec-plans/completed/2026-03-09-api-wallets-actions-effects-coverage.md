# Increase API Wallets Actions/Effects Coverage

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, regressions in the API-wallet runtime will be caught by automated tests before they reach the browser. The observable outcome is stronger coverage for `/hyperopen/src/hyperopen/api_wallets/actions.cljs` and `/hyperopen/src/hyperopen/api_wallets/effects.cljs`, especially around route gating, modal validation, refresh behavior, and async error handling. A contributor can prove the change by running coverage plus the required repo gates and seeing the targeted files rise above their prior percentages.

## Progress

- [x] (2026-03-09 16:08Z) Read `/hyperopen/AGENTS.md`, `/hyperopen/docs/WORK_TRACKING.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/.agents/PLANS.md`; inspected the current API-wallet action/effect source and tests.
- [x] (2026-03-09 16:08Z) Created and claimed `bd` task `hyperopen-v7u` to track this coverage work.
- [x] (2026-03-09 16:18Z) Added regression tests in `/hyperopen/test/hyperopen/api_wallets/actions_test.cljs` for route normalization, ownerless route loads, form field normalization, modal guards, close/reset behavior, validation failures, and unknown modal types.
- [x] (2026-03-09 16:21Z) Added regression tests in `/hyperopen/test/hyperopen/api_wallets/effects_test.cljs` for inactive-route short-circuiting, force-refresh request options, remote webdata fetches, generator success/failure, wrapper behavior, authorize/remove validation failures, and async rejection handling.
- [x] (2026-03-09 16:26Z) Installed the pinned Node dependencies with `npm ci` because the worktree did not contain `node_modules`, then ran `npm test` successfully.
- [x] (2026-03-09 16:28Z) Ran `npm run coverage` and confirmed the targeted API-wallet runtime files exceeded their starting coverage levels.
- [x] (2026-03-09 16:30Z) Ran the required validation gates `npm run check` and `npm run test:websocket` successfully after the coverage run.
- [x] (2026-03-09 16:31Z) Moved this ExecPlan to `/hyperopen/docs/exec-plans/completed/`, recorded the final outcomes, and prepared `hyperopen-v7u` for closure.

## Surprises & Discoveries

- Observation: The workspace did not have a fresh local `coverage/` artifact to inspect directly.
  Evidence: `ls coverage` reported `missing`, so branch targeting had to start from source inspection before a fresh coverage run.

- Observation: The worktree also did not contain `node_modules`, so the first `npm test` attempt failed before compilation reached the new tests.
  Evidence: `npm test` exited with `sh: shadow-cljs: command not found`; `npm ci` restored the local toolchain and unblocked all validation commands.

- Observation: The new public-API tests were enough to drive both target namespaces to 100% function coverage without reaching into private vars.
  Evidence: `coverage/coverage-summary.json` reports `/hyperopen/.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/api_wallets/actions.cljs` at `functions 12/12 (100%)` and `/hyperopen/.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/api_wallets/effects.cljs` at `functions 14/14 (100%)`.

## Decision Log

- Decision: Track this as a `bd` task even though the user provided the scope directly.
  Rationale: `/hyperopen/docs/WORK_TRACKING.md` requires `bd` as the local issue tracker for implementation work.
  Date/Author: 2026-03-09 / Codex

- Decision: Focus the change on tests rather than code changes in `/hyperopen/src/hyperopen/api_wallets/actions.cljs` and `/hyperopen/src/hyperopen/api_wallets/effects.cljs`.
  Rationale: The request is explicitly to increase coverage, and the existing source already exposes enough public seams to cover the missing branches safely.
  Date/Author: 2026-03-09 / Codex

- Decision: Cover the effect helper behavior through the public action/effect entry points rather than by dereferencing private helper vars.
  Rationale: The public functions already expose stable dependency-injection seams, which keeps the tests closer to user-visible behavior and avoids brittle private-var coupling.
  Date/Author: 2026-03-09 / Codex

## Outcomes & Retrospective

The task completed as a test-only change. `/hyperopen/test/hyperopen/api_wallets/actions_test.cljs` now covers the route-normalization and modal edge cases that were previously untested, and `/hyperopen/test/hyperopen/api_wallets/effects_test.cljs` now covers the route-gating, refresh, generator, validation, and async rejection branches that previously accounted for the low branch/function numbers in the coverage report.

The targeted coverage improved materially from the starting screenshot. `/hyperopen/.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/api_wallets/actions.cljs` is now `99.37%` statements/lines, `90.69%` branches, and `100%` functions. `/hyperopen/.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/api_wallets/effects.cljs` is now `88.30%` statements/lines, `73.33%` branches, and `100%` functions. This reduced overall regression risk with only a small increase in test-suite complexity because the new tests stay localized to the existing API-wallet fixtures and dependency seams.

## Context and Orientation

The API-wallet runtime is split into two small namespaces. `/hyperopen/src/hyperopen/api_wallets/actions.cljs` builds pure action outputs for route entry, form edits, modal opening, modal closing, and modal confirmation. `/hyperopen/src/hyperopen/api_wallets/effects.cljs` performs the async and stateful parts: loading API-wallet rows, generating credentials, authorizing a wallet, and removing a wallet. The current tests live in `/hyperopen/test/hyperopen/api_wallets/actions_test.cljs` and `/hyperopen/test/hyperopen/api_wallets/effects_test.cljs`. The missing coverage is concentrated in branchy code paths such as route rejection, missing-owner handling, invalid modal state, force-refresh request options, and promise rejection behavior.

## Plan of Work

Extend the existing action tests with cases that exercise branches not currently hit: route normalization edge cases, API route loads without an owner, ignored form fields, the success path for opening the authorize modal, the non-map guard for remove modals, the modal close reset payload, validation failures inside `confirm-api-wallet-modal`, and the default `case` branch when an unknown modal type is confirmed.

Extend the effect tests with cases that exercise the public entry points through observable state changes and dependency calls. Add tests for `load-api-wallets!` when the route is inactive, when force refresh is enabled and the store snapshot is not reusable, and when remote promises reject. Add tests for `api-load-api-wallets!` as the no-refresh wrapper, for `generate-api-wallet!` success and failure, for validation failures inside `api-authorize-api-wallet!`, for authorize rejection handling, for remove-modal validation failure, and for the non-default-row removal path that should not clear stored agent sessions.

## Concrete Steps

From `/hyperopen`:

1. Edited `/hyperopen/test/hyperopen/api_wallets/actions_test.cljs`.
2. Edited `/hyperopen/test/hyperopen/api_wallets/effects_test.cljs`.
3. Ran `npm test`, saw `shadow-cljs: command not found`, then ran `npm ci` because `node_modules` was absent.
4. Re-ran `npm test` successfully.
5. Ran `npm run coverage` successfully.
6. Ran `npm run check` successfully.
7. Ran `npm run test:websocket` successfully.
8. Moved this ExecPlan to completed and closed the tracked `bd` task.

## Validation and Acceptance

Acceptance is met. The proof points are:

- `npm test` passed (`Ran 2103 tests containing 10957 assertions. 0 failures, 0 errors.`).
- `npm run coverage` passed and improved the target files to:
  `/hyperopen/.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/api_wallets/actions.cljs`: `99.37%` statements/lines, `90.69%` branches, `100%` functions.
  `/hyperopen/.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/api_wallets/effects.cljs`: `88.30%` statements/lines, `73.33%` branches, `100%` functions.
- `npm run check` passed.
- `npm run test:websocket` passed (`Ran 342 tests containing 1869 assertions. 0 failures, 0 errors.`).

## Idempotence and Recovery

These edits are additive test changes plus one planning artifact. Re-running the commands is safe. If a test fails during development, correct the test or the fixture data and rerun the same command; no migration or persistent state change is involved.

## Artifacts and Notes

Key issue id for this task: `hyperopen-v7u`.

Relevant validation evidence:

- `npm ci` installed `281` packages and restored the missing local toolchain.
- `npm test` -> pass.
- `npm run coverage` -> pass.
- `npm run check` -> pass.
- `npm run test:websocket` -> pass.

## Interfaces and Dependencies

The work relies on the existing public functions in `/hyperopen/src/hyperopen/api_wallets/actions.cljs` and `/hyperopen/src/hyperopen/api_wallets/effects.cljs`. No production interface should change. The tests will continue to use `cljs.test`, atoms as fake stores, and dependency injection through the existing function parameters such as `request-extra-agents!`, `approve-agent-request!`, `load-api-wallets!`, `clear-agent-session-by-mode!`, and `runtime-error-message`.

Plan revision note: 2026-03-09 16:08Z - Created the initial ExecPlan after source inspection and `bd` task setup so implementation can proceed with a tracked validation checklist.
Plan revision note: 2026-03-09 16:31Z - Recorded the completed test additions, dependency-install prerequisite, final coverage metrics, required gate results, and completion handoff state before moving the ExecPlan to completed.
