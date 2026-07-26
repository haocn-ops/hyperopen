# Precompute Orderbook and Trades View Slices at Ingest Time

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The L2 orderbook and trades view currently repeats expensive data work on every render: sorting asks and bids, recomputing cumulative depth totals, and filtering/sorting recent trades for the active coin. Under sustained websocket traffic this creates avoidable CPU churn and increases render pressure. After this change, websocket ingest normalizes numeric values once and stores pre-sorted, pre-cumulative depth slices plus coin-indexed normalized trade slices in state, so the view can render directly with minimal transformation. Users should see equivalent orderbook/trades behavior with lower render overhead and steadier UI responsiveness.

## Progress

- [x] (2026-02-16 21:08Z) Reviewed `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md` for UI/runtime constraints.
- [x] (2026-02-16 21:09Z) Confirmed hot paths in `/hyperopen/src/hyperopen/websocket/orderbook.cljs`, `/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs`, `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`, and `/hyperopen/src/hyperopen/websocket/trades.cljs`.
- [x] (2026-02-16 21:10Z) Authored this active ExecPlan with implementation and validation milestones.
- [x] (2026-02-16 21:17Z) Implemented ingest-side orderbook normalization, cumulative helpers, and render-slice builder in `/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs`.
- [x] (2026-02-16 21:17Z) Persisted pre-sorted/pre-cumulative orderbook render slices in `/hyperopen/src/hyperopen/websocket/orderbook.cljs` while preserving legacy `:bids`/`:asks`.
- [x] (2026-02-16 21:19Z) Updated `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` to consume precomputed render snapshots with deterministic fallback.
- [x] (2026-02-16 21:20Z) Added coin-indexed normalized trade cache in `/hyperopen/src/hyperopen/websocket/trades.cljs` and switched `recent-trades-for-coin` to prefer cached slices.
- [x] (2026-02-16 21:21Z) Added/updated regression coverage in `/hyperopen/test/hyperopen/websocket/orderbook_policy_test.cljs`, `/hyperopen/test/hyperopen/websocket/orderbook_test.cljs`, `/hyperopen/test/hyperopen/websocket/trades_test.cljs`, and `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs`.
- [x] (2026-02-16 21:31Z) Ran required gates with green results: `npm test`, `npm run check`, `npm run test:websocket`.
- [x] (2026-02-16 21:32Z) Updated this plan’s living sections with outcomes, evidence, and revision notes.

## Surprises & Discoveries

- Observation: `sort-asks` intentionally keeps legacy descending order in policy, so the view performs a second ascending sort to derive display order.
  Evidence: `/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs` comment and `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` `display-asks` derivation.
- Observation: Trades websocket state currently stores one global raw list (`:trades`) with no per-coin index, forcing filter + normalize + sort in the view every render.
  Evidence: `/hyperopen/src/hyperopen/websocket/trades.cljs` and `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` `recent-trades-for-coin`.
- Observation: Keeping precomputed render slices inside a nested `:render` map avoided collisions with existing top-level orderbook keys used across state fixtures.
  Evidence: Existing tests and selectors in `/hyperopen/test/hyperopen/state/trading_test.cljs` and `/hyperopen/src/hyperopen/state/trading.cljs` continue using `:bids`/`:asks` unchanged.
- Observation: The view needs a strict fallback path because many view tests build direct orderbook fixtures without websocket-ingest preprocessing.
  Evidence: `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs` constructs `:orderbook` maps directly in multiple tests.

## Decision Log

- Decision: Keep `:bids` and `:asks` in orderbook state for compatibility and add derived keys for render-optimized slices instead of replacing existing keys.
  Rationale: Multiple non-view consumers and tests rely on `:bids`/`:asks`; additive derived keys reduce break risk while enabling the optimization.
  Date/Author: 2026-02-16 / Codex
- Decision: Keep trades raw stream (`:trades`) and add coin-indexed normalized slices for view retrieval.
  Rationale: Existing modules may still rely on raw trade payloads; additive cache supports optimized rendering without API breakage.
  Date/Author: 2026-02-16 / Codex
- Decision: Store precomputed depth artifacts under `[:orderbook :render ...]` (`:display-bids`, `:display-asks`, `:bids-with-totals`, `:asks-with-totals`, `:best-bid`, `:best-ask`) instead of adding many top-level keys.
  Rationale: A nested render projection keeps compatibility clear and prevents accidental selector coupling to transient optimization fields.
  Date/Author: 2026-02-16 / Codex
- Decision: Keep render-time fallback derivation in `l2-orderbook-view` when precomputed render slices are absent.
  Rationale: Tests and potential non-websocket fixtures still provide only raw levels; fallback maintains deterministic behavior while preserving optimization for ingest-managed data.
  Date/Author: 2026-02-16 / Codex

## Outcomes & Retrospective

Implementation complete for the scoped performance refactor.

What was achieved:

- Added ingest-time orderbook normalization and cumulative-depth derivation in `/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs`.
- Updated `/hyperopen/src/hyperopen/websocket/orderbook.cljs` to store compatibility `:bids`/`:asks` plus precomputed render slices under `:render`.
- Updated `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` to consume precomputed render snapshots (and fallback to legacy derivation when needed), eliminating repeated sort/cumulative work on standard websocket-driven renders.
- Added coin-indexed normalized trade cache in `/hyperopen/src/hyperopen/websocket/trades.cljs` and updated trades view selection to prefer cached slices.
- Expanded regression tests for new policy helpers, orderbook snapshot structure, cached trades retrieval, and precomputed-view rendering.

Validation summary:

- `npm test`: pass (`1069` tests, `4856` assertions, `0` failures, `0` errors).
- `npm run check`: pass (lint/docs/class/style checks and app/test compiles all green).
- `npm run test:websocket`: pass (`120` tests, `482` assertions, `0` failures, `0` errors).

Remaining risk:

- The view keeps a fallback derivation path for fixture compatibility; if external call sites continue bypassing websocket ingest, they will not get the same render-path savings until they emit precomputed `:render` slices too.

## Context and Orientation

Orderbook websocket ingest is handled in `/hyperopen/src/hyperopen/websocket/orderbook.cljs` by `create-orderbook-data-handler`, which currently sorts incoming levels and stores `{:bids ... :asks ... :timestamp ...}` per coin. Rendering happens in `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` where asks and bids are sorted again into display order and cumulative totals are recomputed on every render.

In this repository, “normalized numeric levels” means each price/size level map includes parsed numeric fields derived from incoming string values (`:px` and `:sz`) so downstream code does not repeatedly parse strings. “Pre-cumulative slices” means vectors that already include running totals (`:cum-size`, `:cum-value`) in display order.

Trades websocket ingest is in `/hyperopen/src/hyperopen/websocket/trades.cljs`. The L2 trades tab currently calls `recent-trades-for-coin` in `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`, which filters global recent trades by coin, normalizes each trade, sorts by time descending, and takes 100 on every render.

The goal is to move these transformations to ingest/runtime state updates, preserving deterministic behavior and stable public module interfaces.

## Plan of Work

Milestone 1 updates `/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs` with pure helpers for orderbook normalization and derived slice construction. The helpers will parse numeric fields once, keep deterministic ordering, and build limited cumulative vectors for bids and asks used by the orderbook panel.

Milestone 2 updates `/hyperopen/src/hyperopen/websocket/orderbook.cljs` handler internals to call those helpers once per payload and persist both compatibility keys (`:bids`, `:asks`) and render-optimized keys (pre-sorted/pre-cumulative slices and best prices). Public subscribe/unsubscribe/init APIs remain unchanged.

Milestone 3 updates `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` to read precomputed slices first and use existing render-time derivation only as a fallback when slices are absent. This keeps behavior stable for tests/fixtures that still pass only `:bids` and `:asks`.

Milestone 4 updates `/hyperopen/src/hyperopen/websocket/trades.cljs` to maintain coin-indexed normalized recent-trade vectors during ingest and expose a retrieval function for one coin. `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` trades panel then consumes cached coin slices directly, removing render-time filter/sort work.

Milestone 5 updates tests in `/hyperopen/test/hyperopen/websocket/orderbook_policy_test.cljs`, `/hyperopen/test/hyperopen/websocket/orderbook_test.cljs`, and `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs` (and any additional touched suites) so expected behavior is explicit for normalized slices and cached trades retrieval.

Milestone 6 runs required validation gates and records pass/fail evidence and any residual risks.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs` to add normalization + cumulative helper functions and maintain existing sort compatibility behavior for legacy keys.
2. Edit `/hyperopen/src/hyperopen/websocket/orderbook.cljs` to build/store derived render slices during `l2Book` ingest.
3. Edit `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` to consume derived slices and simplify runtime computations.
4. Edit `/hyperopen/src/hyperopen/websocket/trades.cljs` to maintain coin-indexed normalized recent trades and expose retrieval.
5. Edit `/hyperopen/test/hyperopen/websocket/orderbook_policy_test.cljs`, `/hyperopen/test/hyperopen/websocket/orderbook_test.cljs`, `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs`, and any impacted tests.
6. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

Expected transcript shape:

  - `npm run check` exits 0 with no lint/compile failures.
  - `npm test` exits 0 with `0` failures and `0` errors.
  - `npm run test:websocket` exits 0 with `0` failures and `0` errors.

## Validation and Acceptance

Acceptance criteria:

- Orderbook payload ingest stores render-ready depth slices with normalized numeric values and cumulative totals so the orderbook view no longer needs to sort/recompute totals on each render.
- L2 orderbook rendering behavior remains stable (spread, depth bars, tab behavior, freshness cue) using precomputed slices.
- Trades tab retrieval no longer filters/sorts the global trade list during render; it reads cached per-coin normalized trades.
- Legacy compatibility remains intact for existing consumers using `:bids` and `:asks`.
- Required validation gates pass: `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

The refactor is additive and can be applied safely in repeated runs. If regressions appear, recovery is straightforward by switching view code to fallback paths that derive from `:bids`/`:asks` and `get-recent-trades` while keeping new helpers/tests in place for iterative fixes. No destructive migrations or irreversible commands are involved.

## Artifacts and Notes

Planned touched paths:

- `/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs`
- `/hyperopen/src/hyperopen/websocket/orderbook.cljs`
- `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`
- `/hyperopen/src/hyperopen/websocket/trades.cljs`
- `/hyperopen/test/hyperopen/websocket/orderbook_policy_test.cljs`
- `/hyperopen/test/hyperopen/websocket/orderbook_test.cljs`
- `/hyperopen/test/hyperopen/websocket/trades_test.cljs`
- `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs`
- `/hyperopen/docs/exec-plans/active/2026-02-16-orderbook-ingest-render-precompute.md`

Evidence to capture during implementation:

- Test assertions proving presence and correctness of precomputed orderbook render slices.
- Test assertions proving trades cache is coin-scoped and sorted newest-first.
- Required validation gate outputs.

## Interfaces and Dependencies

Public interfaces that must remain stable:

- `/hyperopen/src/hyperopen/websocket/orderbook.cljs`
  - `subscribe-orderbook!`
  - `unsubscribe-orderbook!`
  - `create-orderbook-data-handler`
  - `init!`
- `/hyperopen/src/hyperopen/websocket/trades.cljs`
  - `subscribe-trades!`
  - `unsubscribe-trades!`
  - `create-trades-handler`
  - `get-recent-trades`
  - `clear-trades!`
  - `init!`
- `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`
  - `l2-orderbook-panel`
  - `l2-orderbook-view`

Dependencies:

- `/hyperopen/src/hyperopen/websocket/orderbook-policy.cljs` for pure normalization/sorting helpers.
- `/hyperopen/src/hyperopen/websocket/trades-policy.cljs` for trade normalization primitives.
- Existing websocket market projection runtime behavior in `/hyperopen/src/hyperopen/websocket/market_projection_runtime.cljs` remains unchanged.

Plan revision note: 2026-02-16 21:10Z - Initial plan created from hotspot analysis and repository runtime/UI constraints.
Plan revision note: 2026-02-16 21:32Z - Updated living sections after implementing ingest-time orderbook/trades precomputation, adding regression tests, and passing required validation gates.
