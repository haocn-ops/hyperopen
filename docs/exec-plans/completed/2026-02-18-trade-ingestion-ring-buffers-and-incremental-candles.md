# Refactor Trade Ingestion to Ring Buffers and Incremental Candle Merge

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Trade ingestion currently does extra hot-path work in `/hyperopen/src/hyperopen/websocket/trades.cljs`: it repeatedly concatenates and sorts per-coin trades for display cache maintenance, and it sorts the full pending candle trade set during each flush. Under burst traffic, this causes avoidable CPU and allocation pressure. After this change, each coin keeps a bounded ring buffer of recent trades, per-batch updates merge linearly with existing data, and candle buffering maintains ordered pending data incrementally so flushes can apply directly without re-sorting the whole backlog.

Users should still see the same recent-trades and chart-candle behavior, but with less hot-path work per trade batch.

## Progress

- [x] (2026-02-18 01:29Z) Reviewed `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/RELIABILITY.md` requirements relevant to this refactor.
- [x] (2026-02-18 01:29Z) Confirmed hotspot code paths in `/hyperopen/src/hyperopen/websocket/trades.cljs` and current coverage in `/hyperopen/test/hyperopen/websocket/trades_test.cljs`.
- [x] (2026-02-18 01:29Z) Authored this active ExecPlan with implementation and acceptance criteria.
- [x] (2026-02-18 01:32Z) Implemented ring-buffer-backed per-coin trade cache and linear merge path in `/hyperopen/src/hyperopen/websocket/trades.cljs`.
- [x] (2026-02-18 01:32Z) Implemented incremental pending candle ordering/merge so timer flush no longer sorts the entire pending set in `/hyperopen/src/hyperopen/websocket/trades.cljs`.
- [x] (2026-02-18 01:34Z) Updated and extended `/hyperopen/test/hyperopen/websocket/trades_test.cljs` with bounded ring behavior and incremental candle pending merge coverage.
- [x] (2026-02-18 01:35Z) Ran required gates: `npm run check`, `npm test`, `npm run test:websocket` (all passed).
- [x] (2026-02-18 01:35Z) Updated this plan with outcomes and prepared move to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: existing per-coin trade cache maintenance is currently implemented by concatenating incoming trades with current cache then sorting full vectors on every batch.
  Evidence: `/hyperopen/src/hyperopen/websocket/trades.cljs` uses `(concat incoming (get acc coin []))`, `(sort-by trade-time-ms >)`, and `(take max-recent-trades)`.
- Observation: candle flush currently normalizes and sorts all pending trades each timer cycle.
  Evidence: `/hyperopen/src/hyperopen/websocket/trades.cljs` computes `normalized` and then `(sort-by :time-ms)` inside `update-candles-from-trades!`.
- Observation: tests that previously asserted candle trade map equality needed to ignore `:size` after normalization because the new normalization path always supplies size defaults.
  Evidence: updated assertions in `/hyperopen/test/hyperopen/websocket/trades_test.cljs` compare candle payloads after `(dissoc % :size)`.
- Observation: timer callback assertions for incremental pending merge had to execute inside `with-redefs` scope, otherwise the original callback target was restored before flush invocation.
  Evidence: `schedule-candle-update-incrementally-merges-pending-trades-test` now invokes `@timeout-callback` within the `with-redefs` block.

## Decision Log

- Decision: keep public websocket trade APIs unchanged and limit scope to internal caching and candle-buffer mechanics.
  Rationale: architecture guardrails require stable public seams unless explicitly requested, and the request targets hot-path internals.
  Date/Author: 2026-02-18 / Codex
- Decision: maintain compatibility for callers/tests that may still hold vector-shaped `:trades-by-coin` values while migrating internals to ring-buffer values.
  Rationale: this allows incremental rollout without requiring external state-shape assumptions.
  Date/Author: 2026-02-18 / Codex
- Decision: use monotonic-order checks (`nondecreasing` / `nonincreasing`) plus reverse/linear merge for common batches, with full `sort-by` fallback only for mixed-order batches.
  Rationale: this removes full-sort work on normal websocket traffic while preserving deterministic ordering when out-of-order batches appear.
  Date/Author: 2026-02-18 / Codex
- Decision: split candle update into a normalized fast path (`update-candles-from-normalized-trades!`) used by scheduled flushes, and keep `update-candles-from-trades!` as a compatibility wrapper.
  Rationale: flushes avoid redundant normalization/sorting, and existing direct-call behavior remains testable.
  Date/Author: 2026-02-18 / Codex

## Outcomes & Retrospective

Implementation completed for the targeted performance issue.

What changed:

- `/hyperopen/src/hyperopen/websocket/trades.cljs` now stores per-coin recent trades as bounded ring-buffer entries and updates them via linear merge instead of sorting concatenated vectors per batch.
- Candle buffering now normalizes and orders each incoming batch once, incrementally merges into ordered pending state, and flushes through `update-candles-from-normalized-trades!` without re-sorting pending backlog.
- `/hyperopen/test/hyperopen/websocket/trades_test.cljs` now includes regressions for:
  - per-coin batch merge ordering;
  - bounded retention (`max-recent-trades`);
  - incremental pending merge behavior in `schedule-candle-update!`.

Validation results:

- `npm run check`: pass.
- `npm test`: pass (`1090` tests, `4944` assertions, `0` failures, `0` errors).
- `npm run test:websocket`: pass (`127` tests, `519` assertions, `0` failures, `0` errors).

Remaining risk and scope note:

- This refactor is intentionally scoped to `/hyperopen/src/hyperopen/websocket/trades.cljs`; other websocket ingestion paths are unchanged.

## Context and Orientation

The target module is `/hyperopen/src/hyperopen/websocket/trades.cljs`. It has two independent concerns relevant to this task:

1. Trade display cache ingestion:
It keeps global recent trades and per-coin recent trades (`:trades-by-coin`). The current per-coin path sorts full vectors every batch.

2. Candle updates from buffered trades:
It buffers trade payloads in `trades-buffer` and later applies them to chart candles via `policy/upsert-candle`. The current update path sorts the full pending set each flush.

This refactor stays inside this module and its test namespace `/hyperopen/test/hyperopen/websocket/trades_test.cljs`. No websocket transport, reducer, signing, or client API changes are in scope.

In this plan, a ring buffer means a fixed-size circular structure that retains only the most recent N trades for a coin (`N = 100`) while supporting append and bounded retention without reallocating unbounded vectors.

## Plan of Work

Milestone 1 updates per-coin trade ingestion internals. Add a compact ring-buffer representation and conversion helpers for read APIs. Replace per-batch sort-heavy update logic with linear merge between existing per-coin ordered trades and incoming ordered trades, followed by bounded retention to the newest `max-recent-trades`.

Milestone 2 updates candle buffering internals. Normalize incoming trade batches once, maintain pending trades in time order by incrementally merging each batch into the pending queue, and flush using the already ordered pending data so no full re-sort is needed at flush time.

Milestone 3 updates tests for behavior parity and regression prevention. Preserve existing behavioral assertions for trade ordering and candle update semantics while adding explicit checks for bounded ring retention and incremental pending merge behavior.

Milestone 4 runs full required validation gates and records results in this document, then moves this plan to completed.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/websocket/trades.cljs` to add ring-buffer helper functions, linear merge helpers, and incremental candle pending merge path.
2. Edit `/hyperopen/test/hyperopen/websocket/trades_test.cljs` to align with the refactor and add regression coverage.
3. Run:
   `npm run check`
   `npm test`
   `npm run test:websocket`
4. Record outputs and findings in this plan, then move plan file to completed.

Expected evidence shape after completion:

- No public API changes in websocket trades namespace.
- Recent-trades-per-coin behavior remains newest-first and bounded.
- Candle updates still process trades in ascending time for `policy/upsert-candle`, without full pending sort at flush.
- Required gates report zero failures.

## Validation and Acceptance

Acceptance is met when all of the following are true:

- `/hyperopen/src/hyperopen/websocket/trades.cljs` no longer sorts entire per-coin recent-trade vectors on each batch.
- Per-coin recent-trade retention is bounded by `max-recent-trades` with newest-first read behavior preserved through `get-recent-trades-for-coin`.
- Candle buffering maintains ordered pending trades incrementally and flush path does not perform full pending-sort work.
- Existing websocket trade tests pass with added regression tests for new behavior.
- Required gates pass: `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

All edits are source-only and reversible via version control. Re-running test commands is safe. If regressions appear, fallback is to keep helper scaffolding while restoring previous ingestion/update logic incrementally until tests pass, then reapply optimization in smaller steps.

## Artifacts and Notes

Touched files:

- `/hyperopen/src/hyperopen/websocket/trades.cljs`
- `/hyperopen/test/hyperopen/websocket/trades_test.cljs`
- `/hyperopen/docs/exec-plans/completed/2026-02-18-trade-ingestion-ring-buffers-and-incremental-candles.md`

Key verification artifacts:

- Focused trade test assertions demonstrating newest-first bounded per-coin retrieval after multiple batches.
- Focused trade test assertions demonstrating ordered incremental candle pending merge across multiple schedule calls.
- Required gate outputs.

## Interfaces and Dependencies

Public interface expectations in `/hyperopen/src/hyperopen/websocket/trades.cljs` remain unchanged:

- `subscribe-trades!`
- `unsubscribe-trades!`
- `handle-trade-data!`
- `create-trades-handler`
- `get-subscriptions`
- `get-recent-trades`
- `get-recent-trades-for-coin`
- `clear-trades!`
- `init!`

Dependencies used by this refactor:

- `/hyperopen/src/hyperopen/websocket/trades-policy.cljs` for trade normalization/parsing and `upsert-candle`.
- `/hyperopen/src/hyperopen/platform.cljs` timer scheduling used by candle buffering.

Plan revision note: 2026-02-18 01:29Z - Initial plan created from hotspot analysis and repository planning/reliability constraints.
Plan revision note: 2026-02-18 01:35Z - Updated living sections after implementing ring-buffer/linear-merge refactor, adding incremental candle merge tests, and passing required validation gates.
