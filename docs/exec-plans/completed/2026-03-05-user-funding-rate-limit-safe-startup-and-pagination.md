# User Funding Rate-Limit Safe Startup and Pagination

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and must be maintained in accordance with that file.

## Purpose / Big Picture

Users with long account history can trigger large `userFunding` `/info` bursts during startup and first-tab hydration. After this change, startup will fetch only a bounded recent funding window, and full-history pagination will be paced so requests are spread over time instead of burst-posted. The user-visible result is the same funding history UI behavior with lower risk of rate-limit throttling.

## Progress

- [x] (2026-03-05 18:01Z) Created `bd` epic `hyperopen-6co` and child tasks `hyperopen-b07`, `hyperopen-25q`, `hyperopen-i57`; claimed and set in-progress.
- [x] (2026-03-05 18:03Z) Audited current funding request paths and confirmed startup currently uses all-time filters (`start-time-ms 0`) through stage-A bootstrap.
- [x] (2026-03-05 18:14Z) Implemented bounded startup funding window wiring via config/runtime/startup (`:startup :funding-history-lookback-ms`, default 7 days).
- [x] (2026-03-05 18:14Z) Implemented `userFunding` pagination pacing with adaptive inter-page delay and a test seam (`:wait-ms-fn`), including option sanitization before transport.
- [x] (2026-03-05 18:18Z) Added/updated endpoint and startup tests; required gates passed (`npm run check`, `npm test`, `npm run test:websocket`).
- [x] (2026-03-05 18:20Z) Added validation artifact `/hyperopen/docs/qa/user-funding-rate-limit-scheduling-validation-2026-03-05.md` and updated rollout runbook policy notes.

## Surprises & Discoveries

- Observation: the existing startup WS-first logic already delays fallback fetches when streams are usable, but fallback `fetch-and-merge-funding-history!` still defaults to all-time filters once triggered.
  Evidence: `src/hyperopen/startup/runtime.cljs` calls `fetch-and-merge-funding-history!` with `{:priority :high}` only.

- Observation: endpoint tests that intentionally exercise pagination needed explicit wait stubs to avoid introducing real-time sleeps from new pacing.
  Evidence: `request-user-funding-history-paginates-forward-by-time-test` and wrapped payload test now inject `:wait-ms-fn`.

## Decision Log

- Decision: keep deep all-time funding history fetch user-initiated (Funding History tab/filter actions) while limiting startup to a bounded recent window.
  Rationale: preserves startup responsiveness and lowers cold-start request burst without removing full-history capability.
  Date/Author: 2026-03-05 / Codex

- Decision: implement page pacing in the account endpoint pagination loop (source of repeated `userFunding` posts) with a default conservative floor delay.
  Rationale: centralizes control where burst fanout is produced and protects all callers, including startup and tab-driven backfills.
  Date/Author: 2026-03-05 / Codex

- Decision: set startup funding lookback default to 7 days (`604800000ms`) and pagination min delay default to `1250ms`.
  Rationale: provides meaningful recent history coverage while reducing cold-start burst risk and staying within user-level per-second request constraints.
  Date/Author: 2026-03-05 / Codex

## Outcomes & Retrospective

Implemented as planned. Startup now sends bounded funding history windows instead of all-time defaults, and endpoint pagination now spaces `userFunding` pages with conservative delay controls. This preserves existing UI behavior while reducing burst pressure. Required validation gates all passed, and test coverage now explicitly exercises delay behavior and startup lookback options.

## Context and Orientation

`userFunding` requests are built in `/hyperopen/src/hyperopen/api/endpoints/account.cljs` and paginated via `fetch-user-funding-history-loop!`, which currently requests pages immediately back-to-back until exhaustion. Startup account hydration is orchestrated in `/hyperopen/src/hyperopen/startup/runtime.cljs`, where stage-A calls `fetch-and-merge-funding-history!` for the connected address. Runtime and config wiring that feeds startup behavior lives in `/hyperopen/src/hyperopen/config.cljs`, `/hyperopen/src/hyperopen/runtime/state.cljs`, and `/hyperopen/src/hyperopen/app/startup.cljs`. Existing request policy defaults (TTL and normalization helpers) live in `/hyperopen/src/hyperopen/api/request_policy.cljs`.

## Plan of Work

I will add a bounded startup funding lookback config and thread it into startup bootstrap so stage-A funding fetch uses `{start-time-ms,end-time-ms}` covering only recent history. Then I will extend account endpoint pagination to support inter-page pacing by introducing a wait seam and adaptive delay calculation based on returned row count with a conservative minimum delay floor. I will keep these pacing options internal to endpoint behavior and strip non-transport keys before posting `/info` requests so current request metadata and policy semantics remain stable. Finally, I will update and add tests in startup and endpoint suites, then run required gates and capture results.

## Concrete Steps

From `/hyperopen`:

1. Edit startup/config/runtime wiring:
   - `src/hyperopen/config.cljs`
   - `src/hyperopen/runtime/state.cljs`
   - `src/hyperopen/app/startup.cljs`
   - `src/hyperopen/startup/runtime.cljs`
2. Edit endpoint/request policy pacing logic:
   - `src/hyperopen/api/request_policy.cljs`
   - `src/hyperopen/api/endpoints/account.cljs`
3. Update tests:
   - `test/hyperopen/startup/runtime_test.cljs`
   - `test/hyperopen/api/endpoints/account_test.cljs`
4. Run validation:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
5. Record outcomes in this plan and task notes.

## Validation and Acceptance

Acceptance is met when:

1. Startup funding bootstrap requests include a bounded lookback window instead of all-time defaults.
2. `request-user-funding-history!` pagination waits between pages according to delay policy and remains deterministic/testable.
3. Existing funding history UI flows still work (funding rows load and sort/filter behavior unchanged).
4. Required validation gates pass.

## Idempotence and Recovery

All edits are additive and configuration-driven. Re-running test commands is safe. If regressions appear, startup windowing can be temporarily neutralized by setting lookback to a very large value in config while maintaining pacing protections.

## Artifacts and Notes

- Validation artifact: `/hyperopen/docs/qa/user-funding-rate-limit-scheduling-validation-2026-03-05.md`
- Required gates:
  - `npm run check` (pass)
  - `npm test` (pass; `1919 tests`, `9859 assertions`, `0 failures`)
  - `npm run test:websocket` (pass; `333 tests`, `1840 assertions`, `0 failures`)

## Interfaces and Dependencies

This change keeps public action/effect IDs stable. Internal function signatures may extend with optional pacing/wait controls, but callers of `request-user-funding-history!` and `fetch-and-merge-funding-history!` remain source-compatible through optional opts maps. No new external dependencies are introduced.

Revision note (2026-03-05, Codex): Initialized plan with tracked scope, file map, and acceptance for rate-limit-safe startup and pagination behavior.
Revision note (2026-03-05, Codex): Updated progress, decisions, outcomes, and artifacts after implementation and full required-gate validation.
