# Add One-Entry Identity Memoization for Account Tab Normalize/Sort Paths

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The Account Info tabs for Order History, Funding History, and Positions currently recompute normalization and/or sorting during each render even when the input collections and sort controls are unchanged. This creates avoidable work during frequent account updates. After this change, these tabs will use the same one-entry identity memoization pattern already used in Open Orders and Trade History, so no-op rerenders reuse previously derived vectors.

A user-visible confirmation path is to keep the Account Info panel open on these tabs under live updates and observe stable table behavior with reduced repeated derivation work. A code-level confirmation path is deterministic tests that count sort/normalize calls across repeated renders.

## Progress

- [x] (2026-02-18 12:52Z) Reviewed `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md`.
- [x] (2026-02-18 12:52Z) Located existing one-entry memoization patterns in `/hyperopen/src/hyperopen/views/account_info/tabs/open_orders.cljs` and `/hyperopen/src/hyperopen/views/account_info/tabs/trade_history.cljs`.
- [x] (2026-02-18 12:52Z) Confirmed current non-memoized hotspots in `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs`, `/hyperopen/src/hyperopen/views/account_info/tabs/funding_history.cljs`, and `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs`.
- [x] (2026-02-18 12:52Z) Authored this active ExecPlan.
- [x] (2026-02-18 12:55Z) Implemented one-entry memoization and reset hooks in `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs`, `/hyperopen/src/hyperopen/views/account_info/tabs/funding_history.cljs`, and `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs`.
- [x] (2026-02-18 12:55Z) Updated `/hyperopen/src/hyperopen/views/account_info_view.cljs` exports and added memoization regression tests in `/hyperopen/test/hyperopen/views/account_info_view_test.cljs`.
- [x] (2026-02-18 12:56Z) Ran required validation gates successfully: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-02-18 12:56Z) Updated retrospective and prepared plan move to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: Order History does both row normalization and sorting inside render-time bindings, so unchanged rerenders repeat the whole chain.
  Evidence: `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs` computes `normalized`, `filtered`, and `sorted` inside `order-history-table`.

- Observation: Funding History and Positions each call their sort functions directly from render-time bindings, with no identity guard.
  Evidence: `/hyperopen/src/hyperopen/views/account_info/tabs/funding_history.cljs` and `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs` compute sorted rows in tab renderers.

- Observation: Existing tests already enforce one-entry cache semantics for Open Orders and Trade History, so extending the same pattern to other tabs fit naturally into the same call-count test style.
  Evidence: `/hyperopen/test/hyperopen/views/account_info_view_test.cljs` had established cache-hit/miss assertions for these two tabs before this refactor.

## Decision Log

- Decision: Use the same one-entry cache style already established in Open Orders and Trade History (`defonce` atom, `identical?` input checks, keyed sort controls, cached `:result`).
  Rationale: This keeps behavior consistent and avoids introducing multi-entry cache complexity.
  Date/Author: 2026-02-18 / Codex

- Decision: Add reset functions for each new cache and export them via `/hyperopen/src/hyperopen/views/account_info_view.cljs` for deterministic tests.
  Rationale: Existing memoization paths use reset hooks in tests; this keeps testing ergonomics consistent.
  Date/Author: 2026-02-18 / Codex

- Decision: Memoize Order History as one combined normalize/filter/sort pipeline cache rather than separate per-stage caches.
  Rationale: The user-reported hotspot is render-time normalize+sort work; a single cache removes all repeated pipeline stages while keeping a simple one-entry policy.
  Date/Author: 2026-02-18 / Codex

## Outcomes & Retrospective

Completed. The three targeted Account Info tabs now follow the same one-entry identity memoization behavior as Open Orders and Trade History.

Implemented outcomes:

- `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs` now memoizes the normalize/filter/sort pipeline by order-history input identity, status filter, and sort state, with `reset-order-history-sort-cache!`.
- `/hyperopen/src/hyperopen/views/account_info/tabs/funding_history.cljs` now memoizes sorted fundings by input identity and sort state, with `reset-funding-history-sort-cache!`.
- `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs` now memoizes sorted positions by input identity and sort state, with `reset-positions-sort-cache!`.
- `/hyperopen/src/hyperopen/views/account_info_view.cljs` re-exports the three new reset helpers for test ergonomics.
- `/hyperopen/test/hyperopen/views/account_info_view_test.cljs` includes new call-count tests validating cache hit/miss behavior for Order History, Funding History, and Positions.

Validation evidence:

- `npm run check` passed.
- `npm test` passed (`1096` tests, `4973` assertions, `0` failures, `0` errors).
- `npm run test:websocket` passed (`129` tests, `530` assertions, `0` failures, `0` errors).

## Context and Orientation

Relevant files for this refactor:

- `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs`: currently normalizes and sorts order rows per render.
- `/hyperopen/src/hyperopen/views/account_info/tabs/funding_history.cljs`: currently sorts funding rows per render.
- `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs`: currently sorts positions per render.
- `/hyperopen/src/hyperopen/views/account_info/tabs/open_orders.cljs` and `/hyperopen/src/hyperopen/views/account_info/tabs/trade_history.cljs`: reference implementations for one-entry memoization.
- `/hyperopen/src/hyperopen/views/account_info_view.cljs`: re-exports tab helpers used by tests.
- `/hyperopen/test/hyperopen/views/account_info_view_test.cljs`: integration-style view tests where memoization call-count regressions are asserted.

In this plan, “one-entry identity memoization” means each tab stores only the most recent derivation result and returns it when input references are unchanged (`identical?`) and sort controls are unchanged (`=`), otherwise recomputing once and replacing the cache.

## Plan of Work

Milestone 1 updates tab modules so expensive derivations are wrapped behind one-entry cache functions and reset hooks. Order History will memoize the normalize/filter/sort pipeline together because all three steps are currently render-time work. Funding History and Positions will memoize sorted rows by input identity and sort state.

Milestone 2 updates account info view exports and tests so the new cache behavior is observable and deterministic under repeated renders.

Milestone 3 runs required gates and captures outcomes.

## Concrete Steps

1. Edit `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs`:
   - Add `defonce` cache atom and reset function.
   - Add memoized helper keyed by order-history input identity + status filter + sort column/direction.
   - Replace direct normalize/filter/sort render bindings with memoized helper call.

2. Edit `/hyperopen/src/hyperopen/views/account_info/tabs/funding_history.cljs` and `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs`:
   - Add one-entry sort caches and reset functions.
   - Route render-time sorted row binding through memoized helpers.

3. Edit `/hyperopen/src/hyperopen/views/account_info_view.cljs`:
   - Re-export new reset functions for tests.

4. Edit `/hyperopen/test/hyperopen/views/account_info_view_test.cljs`:
   - Add memoization tests for Order History normalize+sort, Funding History sort, and Positions sort.

5. From `/hyperopen`, run:

       npm run check
       npm test
       npm run test:websocket

## Validation and Acceptance

Acceptance criteria:

- Repeated renders with identical row input references and unchanged sort state do not rerun normalize/sort for Order History.
- Repeated renders with identical row input references and unchanged sort state do not rerun sort for Funding History and Positions.
- Changing sort direction or changing input identity invalidates cache and recomputes once.
- Required validation gates pass (`npm run check`, `npm test`, `npm run test:websocket`).

## Idempotence and Recovery

Edits are source-only and safe to reapply. The caches are one-entry atoms and do not alter persisted state. If regressions appear, recovery is to temporarily route through the preexisting direct normalize/sort functions and rerun the same tests.

## Artifacts and Notes

Target code paths:

- `/hyperopen/src/hyperopen/views/account_info/tabs/order_history.cljs`
- `/hyperopen/src/hyperopen/views/account_info/tabs/funding_history.cljs`
- `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs`
- `/hyperopen/src/hyperopen/views/account_info_view.cljs`
- `/hyperopen/test/hyperopen/views/account_info_view_test.cljs`

Validation artifacts to capture after implementation:

- Passing outputs for `npm run check`, `npm test`, and `npm run test:websocket`.
- New/updated tests demonstrating cache hit/miss behavior under repeated renders.

## Interfaces and Dependencies

No public API contract changes are expected. Existing function signatures for tab content and sort helpers remain stable. New reset helpers are additive and used for tests.

Plan revision note: 2026-02-18 12:52Z - Initial plan created before implementation to cover one-entry memoization rollout for account tab normalize/sort hotspots.
Plan revision note: 2026-02-18 12:56Z - Updated after implementation with completed progress, test evidence, and final outcomes prior to moving plan to completed.
