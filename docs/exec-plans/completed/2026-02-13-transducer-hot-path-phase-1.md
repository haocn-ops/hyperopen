# Transducer Hot-Path Optimization Phase 1 (Health, Trades, Funding History)


This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture


This plan reduces avoidable allocation and sequence churn in three high-frequency data paths: websocket health matching, websocket trade-to-candle normalization, and funding-history normalization/filtering. After this change, users should see smoother behavior during bursty websocket traffic because the same work is done with fewer intermediate collections. The behavior of the application must remain identical, and this plan includes explicit regression validation so we can prove we improved performance without changing outputs.

The user-visible outcome is not a new feature toggle; it is better runtime efficiency under load while preserving all existing rendering, sorting, filtering, and subscription behavior.

## Progress


- [x] (2026-02-13 16:44Z) Created phase-1 ExecPlan and selected first three transducer targets.
- [x] (2026-02-13 17:20Z) Implemented Milestone 1 (websocket health pipelines) with duplicate/ordering regression tests.
- [x] (2026-02-13 17:25Z) Implemented Milestone 2 (websocket trades normalization pipeline) and added focused websocket trades tests.
- [x] (2026-02-13 17:29Z) Implemented Milestone 3 (domain funding-history pipelines) and expanded normalization/filter coverage.
- [x] (2026-02-13 17:34Z) Ran required validation gates and recorded evidence in this plan.
- [x] (2026-02-13 17:39Z) Captured benchmark checkpoints for all three refactor targets and completed retrospective notes.

## Surprises & Discoveries


- Observation: funding-history normalization and filtering logic now lives in `/hyperopen/src/hyperopen/domain/funding_history.cljs` and is reused by websocket user handlers and account-history effects.
  Evidence: `/hyperopen/src/hyperopen/websocket/user.cljs` calls `funding-history/normalize-ws-funding-rows`, `merge-funding-history-rows`, and `filter-funding-history-rows`.

- Observation: several apparent candidates are sort-dominated, so transducers only help in the pre-sort stages.
  Evidence: `/hyperopen/src/hyperopen/websocket/trades.cljs` and `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` both end with `sort-by` and then truncate.

- Observation: transducer composition must use `(distinct)` (transducer arity) rather than `distinct` (collection arity), or runtime reducer failures occur under websocket tests.
  Evidence: initial `npm run test:websocket` run raised `TypeError: rf.call is not a function` across `health/match-stream-keys` call paths until `(distinct)` was applied.

## Decision Log


- Decision: Phase 1 will target exactly three pipelines: health matching, trades normalization, and funding-history normalization/filtering.
  Rationale: These are frequently executed and have straightforward map/filter/remove chains that can be fused without changing domain behavior.
  Date/Author: 2026-02-13 / Codex

- Decision: Keep sorting logic unchanged in this phase.
  Rationale: sorting can dominate runtime and has ordering semantics tied to deterministic behavior; this phase focuses on eliminating intermediate collections before sort.
  Date/Author: 2026-02-13 / Codex

- Decision: Use output-equivalence tests as the correctness gate, and use lightweight benchmark checkpoints as evidence rather than hard performance assertions.
  Rationale: strict timing assertions in CI are flaky; equivalence tests are deterministic and timing evidence can still be documented for review.
  Date/Author: 2026-02-13 / Codex

- Decision: Add dedicated websocket trades unit tests and wire them into both test runners.
  Rationale: `update-candles-from-trades!` had no direct test coverage for mixed coin payloads, invalid rows, and ordering behavior after the refactor.
  Date/Author: 2026-02-13 / Codex

## Outcomes & Retrospective


Implemented outcomes:

- Milestone 1: refactored `extract-trades-coins`, `extract-user-candidates`, and `match-stream-keys` in `/hyperopen/src/hyperopen/websocket/health.cljs` to use fused transducer pipelines while preserving ordering and fallback semantics.
- Milestone 2: refactored trade normalization/filtering pre-sort stage in `/hyperopen/src/hyperopen/websocket/trades.cljs` to a single transducer pass before `sort-by`.
- Milestone 3: refactored `/hyperopen/src/hyperopen/domain/funding_history.cljs` normalization/filter pipelines (`normalize-info-funding-rows`, `normalize-ws-funding-rows`, `filter-funding-history-rows`) to transducer forms.

Regression coverage added/updated:

- `/hyperopen/test/hyperopen/websocket/health_test.cljs`: duplicate-heavy candidate dedupe/order test and single-active fallback test.
- `/hyperopen/test/hyperopen/websocket/trades_test.cljs`: new focused tests for normalize/filter/sort behavior and entry-shape preservation.
- `/hyperopen/test/hyperopen/domain/funding_history_test.cljs`: invalid-row rejection, duplicate-id merge preference, and filter-window/tie-break sorting tests.
- `/hyperopen/test/websocket_test_runner.cljs` and `/hyperopen/test/test_runner.cljs`: include new websocket trades suite.

Validation outcome:

- Required gates are passing (`npm run check`, `npm test`, `npm run test:websocket`).

Performance checkpoints:

- Milestone 1 (`health/match-stream-keys` shape benchmark, 5,000 streams + 800 payload rows):
  - Old shape: 0.550ms avg (80 runs)
  - New shape: 0.420ms avg (80 runs)
  - Approx improvement: 23.7%
- Milestone 2 (`trades` normalize/filter pre-sort shape benchmark, 25,000 rows):
  - Old shape: 5.342ms avg (60 runs)
  - New shape: 4.372ms avg (60 runs)
  - Approx improvement: 18.1%
- Milestone 3 (`funding-history` shape benchmarks):
  - normalize-info (20,000 rows): 0.454ms -> 0.265ms (41.6%)
  - normalize-ws (20,000 rows): 0.613ms -> 0.365ms (40.5%)
  - filter (50,000 rows): 4.914ms -> 4.542ms (7.6%)

Inference note:

- These timing checkpoints use controlled local old-shape/new-shape fixtures to estimate the effect of fused transformation stages. They are representative and directionally useful, but they are not a production profiler trace.

## Context and Orientation


A transducer is a reusable transformation pipeline (for example, map then filter) that runs directly inside a reducing operation, so intermediate lazy sequences or temporary vectors do not need to be allocated at each stage.

The first target area is websocket health processing in `/hyperopen/src/hyperopen/websocket/health.cljs`. This file computes descriptor candidates and matches incoming envelopes to active stream keys. It currently uses multi-pass sequence pipelines such as map/keep/distinct/vec and filter/map/distinct/vec in hot paths.

The second target area is trade ingestion in `/hyperopen/src/hyperopen/websocket/trades.cljs`. Incoming trade batches are normalized and filtered before candle updates. The current pipeline maps and filters multiple times before sorting.

The third target area is funding history domain logic in `/hyperopen/src/hyperopen/domain/funding_history.cljs`. The file contains normalization, merge, and filter pipelines used by websocket handlers and account-history effects. These functions are pure and ideal for transducer refactors with strong regression tests.

Relevant tests already exist and should be expanded rather than replaced:

- `/hyperopen/test/hyperopen/websocket/health_test.cljs`
- `/hyperopen/test/hyperopen/domain/funding_history_test.cljs`
- `/hyperopen/test/hyperopen/websocket/health_runtime_test.cljs`

If additional direct unit coverage is needed for websocket trades module behavior, add a focused file under `/hyperopen/test/hyperopen/websocket/`.

## Plan of Work


### Milestone 1: Refactor websocket health pipelines to fused transformations


At the end of this milestone, health descriptor extraction and stream-key matching will produce the same results but with fewer intermediate collections. Update `/hyperopen/src/hyperopen/websocket/health.cljs` to replace chained map/filter/remove/keep pipelines in `extract-trades-coins`, `extract-user-candidates`, and `match-stream-keys` with transducer-based reductions (`into` with `comp`, or `transduce` where a custom reducing function is clearer).

Maintain deterministic ordering and deduplication behavior exactly as today. If distinct-order semantics are currently relied upon, preserve first-seen ordering. Expand `/hyperopen/test/hyperopen/websocket/health_test.cljs` with regression cases that compare old-expected output vectors for duplicate-heavy payloads and mixed subscribed/unsubscribed streams.

### Milestone 2: Refactor websocket trade normalization pre-sort pipeline


At the end of this milestone, trade batches for candle updates will still sort and apply identically, but normalization/filtering before sort will run as one fused pass. In `/hyperopen/src/hyperopen/websocket/trades.cljs`, refactor the `normalized` pipeline in `update-candles-from-trades!` to use a transducer composition for normalize+filter logic prior to the existing `sort-by` step.

Keep `policy/upsert-candle` behavior untouched and keep batching/window behavior untouched (`schedule-candle-update!` and pending buffer semantics must not change). Add or extend tests under `/hyperopen/test/hyperopen/websocket/` to lock in equivalence for mixed coin payloads, missing fields, and ordering after sort.

### Milestone 3: Refactor domain funding-history normalization/filtering pipelines


At the end of this milestone, funding row normalization/merge/filter functions will remain pure and output-equivalent, but map/remove/filter chains will be fused where feasible. In `/hyperopen/src/hyperopen/domain/funding_history.cljs`, refactor:

- `normalize-info-funding-rows`
- `normalize-ws-funding-rows`
- `filter-funding-history-rows`

Use transducer-based forms that keep result order and existing nil rejection behavior intact. Keep merge and sorting semantics stable in `merge-funding-history-rows` and `sort-funding-history-rows`. Expand `/hyperopen/test/hyperopen/domain/funding_history_test.cljs` with duplicate-id, invalid-row, and filter-window equivalence coverage.

## Concrete Steps


Work from repository root `/hyperopen`.

1. Implement Milestone 1 changes and tests.

   Expected workflow commands:

       npm test
       npm run test:websocket

2. Implement Milestone 2 changes and tests.

   Expected workflow commands:

       npm test
       npm run test:websocket

3. Implement Milestone 3 changes and tests.

   Expected workflow commands:

       npm test
       npm run test:websocket

4. Run full required validation gates after all milestones are complete.

   Required commands:

       npm run check
       npm test
       npm run test:websocket

5. Capture short before/after timing notes for the three touched functions using a repeatable local invocation (same fixture sizes, same machine session) and record results in `Artifacts and Notes` and `Outcomes & Retrospective`.

## Validation and Acceptance


Acceptance requires both behavioral correctness and evidence of lower transformation overhead:

- Behavioral correctness:
  - Updated and new tests for health, trades, and funding-history must pass.
  - No regressions in required validation gates.

- Functional equivalence:
  - For fixed fixtures, pre-refactor expected outputs (documented in tests) match post-refactor outputs exactly.
  - Ordering of result vectors remains deterministic and unchanged where previously sorted.

- Performance evidence:
  - For duplicate-heavy and high-cardinality fixtures used in tests or benchmark notes, before/after timing notes show reduced time or reduced transient allocation pressure for the refactored transformation stage.
  - If one target does not improve, document it explicitly and keep the refactor only if readability and maintainability are not degraded.

## Idempotence and Recovery


This work is safe to apply incrementally. Each milestone is isolated to pure transformation functions and associated tests, so partial completion can be validated independently.

If a refactor introduces an output mismatch, revert only the affected function to its prior pipeline shape and keep the newly added regression tests. Then reattempt the refactor with smaller steps (for example, first fuse map+filter, then fuse dedupe).

If performance evidence is inconclusive for a specific function, keep correctness changes only when they do not reduce readability. Otherwise, restore the previous implementation and note the decision in `Decision Log`.

## Artifacts and Notes


Implementation artifacts to include as this plan executes:

- Small benchmark note snippet per milestone with fixture size and elapsed times.
- Test output snippets confirming touched suites pass.
- Any behavior-equivalence table can be expressed in prose; do not add large raw dumps.

Validation excerpts (local):

    npm run check
    ...
    [:app] Build completed. (233 files, 25 compiled, 0 warnings, 1.28s)
    [:test] Build completed. (324 files, 60 compiled, 0 warnings, 2.03s)

    npm test
    ...
    Ran 704 tests containing 2687 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    ...
    Ran 86 tests containing 267 assertions.
    0 failures, 0 errors.

Benchmark note:

- Milestone 1 benchmark (local old/new shape, Node 22, same process):
  - Fixture: 5,000 streams, 800 descriptor payload rows.
  - Before: 0.550ms average over 80 runs.
  - After: 0.420ms average over 80 runs.

- Milestone 2 benchmark (local old/new shape, Node 22, same process):
  - Fixture: 25,000 trade rows.
  - Before: 5.342ms average over 60 runs.
  - After: 4.372ms average over 60 runs.

- Milestone 3 benchmarks (local old/new shape, Node 22, same process):
  - normalize-info fixture (20,000 rows): 0.454ms -> 0.265ms over 60 runs.
  - normalize-ws fixture (20,000 rows): 0.613ms -> 0.365ms over 60 runs.
  - filter fixture (50,000 rows): 4.914ms -> 4.542ms over 60 runs.

## Interfaces and Dependencies


Do not change public APIs in websocket client/runtime or domain module function signatures unless strictly necessary.

Preserve these callable interfaces:

- `/hyperopen/src/hyperopen/websocket/health.cljs`
  - `descriptor-candidates`
  - `match-stream-keys`
  - `derive-health-snapshot`

- `/hyperopen/src/hyperopen/websocket/trades.cljs`
  - `create-trades-handler`
  - internal behavior of `update-candles-from-trades!` as consumed by handler path

- `/hyperopen/src/hyperopen/domain/funding_history.cljs`
  - `normalize-info-funding-rows`
  - `normalize-ws-funding-rows`
  - `merge-funding-history-rows`
  - `filter-funding-history-rows`

Dependencies remain the same. This phase should use standard ClojureScript sequence/transducer primitives only (`comp`, `map`, `filter`, `keep`, `distinct`, `transduce`, `into`) and should not introduce new libraries.

---

Revision Note: Initial plan creation on 2026-02-13 to execute the first three transducer refactors identified in the repository audit.
