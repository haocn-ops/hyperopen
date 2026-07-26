# Portfolio Worker Structured-Clone Messaging (Remove EDN Worker Payload Strings)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, portfolio metrics requests and results exchanged between the main thread and the portfolio web worker will no longer pay EDN `pr-str` / `read-string` overhead. Messages will be passed as structured-clone JavaScript objects over `postMessage`, while preserving the Clojure data shape expected by the portfolio view model.

A user can verify this by opening the portfolio view with benchmarks enabled and confirming behavior remains unchanged (metrics values and loading state still update correctly), while code no longer serializes/deserializes worker payloads via EDN strings.

## Progress

- [x] (2026-02-28 02:15Z) Audited current message boundary in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` and `/hyperopen/src/hyperopen/portfolio/worker.cljs`; confirmed EDN string round-trips on both sides.
- [x] (2026-02-28 02:15Z) Audited related coverage in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`; identified an existing test that explicitly codifies EDN serialization behavior.
- [x] (2026-02-28 02:16Z) Authored this ExecPlan.
- [x] (2026-02-28 02:18Z) Implemented structured-clone worker request/response payload handling in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` and `/hyperopen/src/hyperopen/portfolio/worker.cljs` (removed `pr-str`/`read-string` boundary).
- [x] (2026-02-28 02:18Z) Updated `portfolio-vm` test coverage to assert structured-clone round-trip behavior and benchmark coin-key normalization.
- [x] (2026-02-28 02:18Z) Ran required validation gates: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-02-28 02:18Z) Updated this plan with final outcomes/discoveries and prepared move to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: `portfolio-vm` tests include an explicit “worker data serialization integrity” assertion that currently exercises `pr-str`/`cljs.reader/read-string` directly.
  Evidence: `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` lines around the existing test block near `portfolio-vm-builds-performance-metrics-groups-with-benchmark-fallbacks-test`.

- Observation: `js->clj` with `:keywordize-keys true` converts benchmark coin keys (for example `"SPY"`) into keywords (`:SPY`), which breaks downstream string-key lookups unless normalized.
  Evidence: Structured-clone round-trip test update in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` now asserts `"SPY"` exists and `:SPY` does not in normalized `:benchmark-values-by-coin`.

## Decision Log

- Decision: Use structured-clone object payloads (`clj->js` on send, `js->clj` on receive) instead of introducing Transit for this migration.
  Rationale: The request explicitly targets removing EDN `pr-str`/`read-string` overhead; structured clone is native to worker messaging and removes the string parse/print path with minimal dependency surface.
  Date/Author: 2026-02-28 / Codex

- Decision: Keep public behavior stable by normalizing worker result maps back into Clojure maps on the main thread before storing them in app state.
  Rationale: The rest of `portfolio-vm` expects Clojure map semantics (`get`, `get-in`, keyword keys). Converting at the boundary localizes risk.
  Date/Author: 2026-02-28 / Codex

- Decision: Normalize `:benchmark-values-by-coin` keys back to strings and normalize metric token values (`:metric-status`, `:metric-reason`) back to keywords at the main-thread boundary.
  Rationale: This preserves existing UI lookup and status/reason semantics while still removing EDN serialization from worker messaging.
  Date/Author: 2026-02-28 / Codex

## Outcomes & Retrospective

Completed implementation:

- Main thread boundary (`/hyperopen/src/hyperopen/views/portfolio/vm.cljs`):
  - Removed `cljs.reader` usage.
  - Replaced worker response parsing with `js->clj` + normalization (`normalize-worker-metrics-result`).
  - Replaced worker request payload from EDN string to structured-clone object (`clj->js request-data`).
- Worker boundary (`/hyperopen/src/hyperopen/portfolio/worker.cljs`):
  - Removed `cljs.reader` usage.
  - Replaced request payload parsing with `js->clj`.
  - Replaced response payload serialization from `pr-str` to `clj->js` object payload.
- Test updates (`/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`):
  - Replaced EDN serialization test with structured-clone simulation and normalization assertions.

Validation outcomes:

- `npm run check` passed.
- `npm test` passed (`1506` tests, `7645` assertions, `0` failures).
- `npm run test:websocket` passed (`153` tests, `701` assertions, `0` failures).

Retrospective:

- Structured-clone messaging removed EDN parsing overhead without changing public behavior, but required explicit post-conversion normalization to maintain stable key/value semantics expected by existing view logic.

## Context and Orientation

The current worker messaging boundary for portfolio metrics is split across:

- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`
  - Creates the worker (`/js/portfolio_worker.js`).
  - Sends `{:type "compute-metrics" :payload (pr-str request-data)}`.
  - Receives worker message and currently decodes `payload` via `cljs.reader/read-string`.
- `/hyperopen/src/hyperopen/portfolio/worker.cljs`
  - Receives `:payload` as EDN string and decodes with `cljs.reader/read-string`.
  - Sends result payload as `pr-str` string.

In this plan, “structured clone” means passing plain JavaScript object graphs directly via `postMessage` without manual string serialization. Because worker messaging naturally supports object cloning, this removes string parsing overhead from both directions.

## Plan of Work

Milestone 1 updates both worker boundaries to object payloads:

- Main thread send path (`request-metrics-computation!`) sends `:payload` as `clj->js request-data`.
- Worker receive path reads `.-payload` and converts to Clojure map with `js->clj ... :keywordize-keys true`.
- Worker response send path sends `:payload` as `clj->js result-map`.
- Main thread receive path converts result payload back to Clojure map with `js->clj ... :keywordize-keys true` before storing.

Milestone 2 keeps semantic parity for metric status/reason values:

- Because keyword values may transit through JS as strings, add a small normalization step on the main thread for `:metric-status` and `:metric-reason` maps (portfolio + benchmark maps) so downstream UI logic still receives keyword values where expected.

Milestone 3 updates tests and runs required gates:

- Replace explicit EDN round-trip assertion with structured-clone boundary behavior assertions.
- Run `npm run check`, `npm test`, and `npm run test:websocket`.

## Concrete Steps

1. Edit worker boundary code in:
   - `src/hyperopen/views/portfolio/vm.cljs`
   - `src/hyperopen/portfolio/worker.cljs`

2. Update tests in:
   - `test/hyperopen/views/portfolio/vm_test.cljs`

3. Run required gates from `/Users//projects/hyperopen`:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

Expected result: all commands exit with status `0`.

## Validation and Acceptance

Acceptance is complete when all of the following are true:

1. Neither `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` nor `/hyperopen/src/hyperopen/portfolio/worker.cljs` uses EDN `pr-str`/`read-string` for worker message payloads.
2. Portfolio metrics still populate `[:portfolio-ui :metrics-result]` and clear `[:portfolio-ui :metrics-loading?]` after worker responses.
3. Metric status/reason semantics remain intact for UI consumers (keyword status/reason values preserved at the app-state boundary).
4. Required validation gates pass:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

## Idempotence and Recovery

This migration is idempotent and safe to rerun because it modifies only in-memory message shapes and tests; no persisted data or external protocol payloads are migrated.

If a regression appears, the safe rollback is to restore the previous worker payload serialization in the two touched namespaces and re-run the same test gates.

## Artifacts and Notes

Primary implementation targets:

- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`
- `/hyperopen/src/hyperopen/portfolio/worker.cljs`
- `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`

Performance rationale:

- Removing `pr-str`/`read-string` from worker messages avoids full EDN string materialization and parsing on both threads for each metrics recomputation request.

## Interfaces and Dependencies

No new external dependency is required. The messaging interface remains:

- Request message: `{:type "compute-metrics" :payload <object>}`
- Response message: `{:type "metrics-result" :payload <object>}`

Only payload representation changes (EDN string -> structured-clone object). Existing worker type tags and routing remain unchanged.

Plan revision note: 2026-02-28 02:16Z - Initial plan created after auditing current EDN worker message boundary and related tests.
Plan revision note: 2026-02-28 02:18Z - Marked implementation complete, captured normalization decision/discovery, and recorded validation evidence.
