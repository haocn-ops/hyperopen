# Refactor Account Info Projections into Canonical Parsers and Bounded Context Modules

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

`/hyperopen/src/hyperopen/views/account_info/projections.cljs` currently combines anti-corruption parsing, domain calculations, read-model shaping, and presentation mapping in one large namespace. That concentration increases change risk and allows correctness bugs to slip through (notably trigger boolean coercion, inconsistent order-id typing, and inconsistent timestamp normalization). After this change, account projection behavior remains API-compatible for current callers, but parsing and projection logic is split into bounded-context namespaces with canonical invariants for booleans, IDs, and epoch timestamps. Users should observe unchanged UI behavior with more consistent open-order filtering/deduping and safer normalization under heterogeneous payloads.

You can verify this by running account-info projection tests that cover canonical ID normalization, trigger coercion, and epoch conversion, then running the required repository gates.

## Progress

- [x] (2026-02-18 23:18Z) Reviewed planning and architecture guardrails in `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/ARCHITECTURE.md`, `/hyperopen/docs/RELIABILITY.md`, `/hyperopen/docs/SECURITY.md`, `/hyperopen/docs/QUALITY_SCORE.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md`.
- [x] (2026-02-18 23:18Z) Audited current projection hotspots in `/hyperopen/src/hyperopen/views/account_info/projections.cljs` and existing projection tests in `/hyperopen/test/hyperopen/views/account_info/projections_test.cljs` plus account info view regressions.
- [x] (2026-02-18 23:18Z) Authored this active ExecPlan with milestones, acceptance criteria, and command gates.
- [x] (2026-02-18 23:22Z) Implemented canonical parsing helpers and order normalization invariants in `/hyperopen/src/hyperopen/views/account_info/projections/parse.cljs` and `/hyperopen/src/hyperopen/views/account_info/projections/orders.cljs`.
- [x] (2026-02-18 23:22Z) Split projection concerns into bounded-context namespaces (`parse`, `coins`, `orders`, `balances`, `trades`) and converted `/hyperopen/src/hyperopen/views/account_info/projections.cljs` to a compatibility facade.
- [x] (2026-02-18 23:23Z) Added invariant-focused tests for ID normalization, trigger coercion, epoch parsing, and valuation fallback in `/hyperopen/test/hyperopen/views/account_info/projections_test.cljs`; updated integration expectations in `/hyperopen/test/hyperopen/views/account_info_view_test.cljs`.
- [x] (2026-02-18 23:24Z) Ran required gates with success: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-02-18 23:24Z) Moved plan to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: `normalize-open-order` currently uses raw truthiness for `:isTrigger`, so string values such as `"false"` are treated as true.
  Evidence: `/hyperopen/src/hyperopen/views/account_info/projections.cljs` computes `is-trigger?` as `(or (:isTrigger root) (:isTrigger order))` and branches directly.

- Observation: pending cancel order IDs are normalized as integers, while normalized open orders preserve heterogeneous raw ID shapes (string or number).
  Evidence: `/hyperopen/src/hyperopen/views/account_info/projections.cljs` uses `parse-optional-int` in `pending-cancel-oid-set` but keeps raw `:oid` in `normalize-open-order`.

- Observation: timestamp normalization logic is duplicated and inconsistent between open orders, order history, and trade history.
  Evidence: `parse-time-ms` floors only, `trade-history-time-ms` applies seconds-vs-milliseconds conversion, and `normalize-open-order` currently returns raw `:time`.

- Observation: canonical order-id normalization changed integration expectations from numeric to string IDs in account-info view tests.
  Evidence: `normalized-open-orders-prefers-live-source-and-includes-dex-snapshots-test` initially failed with `#{1 3}` versus `#{\"1\" \"3\"}` and passed after updating expectations.

- Observation: switching missing spot valuation from `0` to `nil` did not break existing balance and equity tests because downstream aggregate paths already parse optional numeric values through `parse-num`.
  Evidence: full `npm test` pass after `spot-balance-valuation` changed `:usdc-value` fallback semantics.

## Decision Log

- Decision: Implement the recommendations as an incremental refactor that preserves current public projection function names via `/hyperopen/src/hyperopen/views/account_info/projections.cljs` facade.
  Rationale: This yields SRP and DDD boundary improvements without forcing broad call-site rewrites.
  Date/Author: 2026-02-18 / Codex

- Decision: Canonical order IDs for projection invariants will be normalized as trimmed strings and used for pending-cancel filtering and dedupe identity.
  Rationale: IDs are opaque identities at the projection boundary; canonical strings avoid number/string mismatch bugs.
  Date/Author: 2026-02-18 / Codex

- Decision: Introduce one epoch parser (`parse-epoch-ms`) and apply it to open orders, order history, and trade history.
  Rationale: One invariant parser removes split logic and prevents seconds/milliseconds drift bugs.
  Date/Author: 2026-02-18 / Codex

- Decision: Keep projection facade signatures stable and preserve legacy fields (`:time` plus `:time-ms`) in normalized open-order rows.
  Rationale: Existing view and chart call sites read `:time`; dual fields allow canonicalization without call-site churn.
  Date/Author: 2026-02-18 / Codex

## Outcomes & Retrospective

Implemented. The large projection namespace is now split into bounded-context modules under `/hyperopen/src/hyperopen/views/account_info/projections/` with canonical parsing semantics for booleans, IDs, and epoch timestamps. The top-level namespace remains a stable compatibility facade for all current callers.

Delivered outcomes:

- Added parser module with `parse-epoch-ms`, strict boolean parsing, and canonical ID normalization.
- Fixed open-order trigger coercion so `\"false\"` no longer behaves as truthy.
- Normalized pending-cancel filtering and open-order dedupe identity to canonical string order IDs.
- Standardized time normalization paths for open orders, order history, and trade history.
- Split projection responsibilities into `parse`, `coins`, `orders`, `balances`, and `trades`.
- Added regression tests for the new invariants and updated one integration expectation to match canonical string IDs.

Validation evidence:

- `npm run check`: pass.
- `npm test`: pass (`1117` tests, `5144` assertions, `0` failures, `0` errors).
- `npm run test:websocket`: pass (`135` tests, `587` assertions, `0` failures, `0` errors).

## Context and Orientation

The projection entrypoint used by account UI modules is `/hyperopen/src/hyperopen/views/account_info/projections.cljs`. Current consumers include account tabs (`/hyperopen/src/hyperopen/views/account_info/tabs/*.cljs`), account VM (`/hyperopen/src/hyperopen/views/account_info/vm.cljs`), trading chart overlays (`/hyperopen/src/hyperopen/views/trading_chart/core.cljs`), and tests under `/hyperopen/test/hyperopen/views/`.

In this plan, “canonical parser” means a helper that converts loose payload values into stable internal values (`boolean`, `number`, epoch milliseconds, and normalized ID strings) with explicit nil semantics. “Bounded-context split” means organizing projection code by subdomain (`parse`, `coins`, `orders`, `balances`, `trades`) while the existing facade namespace keeps stable public symbols for callers.

## Plan of Work

Milestone 1 introduces canonical parsing utilities in a new namespace and rewires order normalization to use strict boolean and ID/time parsing. This milestone fixes correctness bugs without changing high-level call signatures.

Milestone 2 extracts coin helpers, order projection logic, balance projection logic, and trade projection logic into focused namespaces under `/hyperopen/src/hyperopen/views/account_info/projections/`. The existing `/hyperopen/src/hyperopen/views/account_info/projections.cljs` file becomes a thin facade that re-exports current public functions.

Milestone 3 adds regression tests in `/hyperopen/test/hyperopen/views/account_info/projections_test.cljs` for ID/boolean/time invariants and valuation fallback semantics and updates any expectations impacted by canonicalization.

Milestone 4 runs required validation gates and records outputs. If all gates pass, move this plan from `active` to `completed` and update this document’s final sections.

## Concrete Steps

1. Create parser module and wire order logic to canonical helpers.

   Files to edit/create:
   - `/hyperopen/src/hyperopen/views/account_info/projections/parse.cljs`
   - `/hyperopen/src/hyperopen/views/account_info/projections/orders.cljs`
   - `/hyperopen/src/hyperopen/views/account_info/projections.cljs`

2. Extract remaining bounded-context modules and keep facade API stable.

   Files to edit/create:
   - `/hyperopen/src/hyperopen/views/account_info/projections/coins.cljs`
   - `/hyperopen/src/hyperopen/views/account_info/projections/balances.cljs`
   - `/hyperopen/src/hyperopen/views/account_info/projections/trades.cljs`
   - `/hyperopen/src/hyperopen/views/account_info/projections.cljs`

3. Add/adjust tests and run required gates from `/hyperopen`.

   Commands:

       npm run check
       npm test
       npm run test:websocket

Expected gate shape:

       ...
       0 failures, 0 errors

## Validation and Acceptance

Acceptance is met when all conditions below are true.

- `normalize-open-order` no longer treats `"false"` as true for trigger logic.
- Canonical ID normalization is used consistently for pending-cancel filtering and open-order dedupe identity.
- Open-order, order-history, and trade-history timestamp paths use epoch-millisecond normalization.
- Projection facade API remains compatible for existing callers.
- New projection invariants are covered by deterministic tests in `/hyperopen/test/hyperopen/views/account_info/projections_test.cljs`.
- Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

All steps are source changes and test runs only; there are no data migrations. If a split introduces regressions, recovery is to temporarily route the affected facade function back to prior implementation logic while keeping new tests to preserve discovered invariants.

## Artifacts and Notes

Primary implementation targets:

- `/hyperopen/src/hyperopen/views/account_info/projections.cljs`
- `/hyperopen/src/hyperopen/views/account_info/projections/parse.cljs`
- `/hyperopen/src/hyperopen/views/account_info/projections/coins.cljs`
- `/hyperopen/src/hyperopen/views/account_info/projections/orders.cljs`
- `/hyperopen/src/hyperopen/views/account_info/projections/balances.cljs`
- `/hyperopen/src/hyperopen/views/account_info/projections/trades.cljs`
- `/hyperopen/test/hyperopen/views/account_info/projections_test.cljs`

Evidence to capture:

- Before/after test assertions for string boolean coercion, ID canonicalization, and epoch conversion.
- Required gate command outputs.

## Interfaces and Dependencies

Public projection interfaces that must remain stable in the facade namespace:

- `parse-num`
- `parse-optional-num`
- `parse-optional-int`
- `parse-time-ms`
- `boolean-value`
- `title-case-label`
- `non-blank-text`
- `parse-coin-namespace`
- `resolve-coin-display`
- `normalize-open-order`
- `pending-cancel-oid-set`
- `normalized-open-orders`
- `normalized-open-orders-for-active-asset`
- `normalize-balance-contract-id`
- `build-balance-rows`
- `portfolio-usdc-value`
- `collect-positions`
- `normalize-order-history-row`
- `normalized-order-history`
- `trade-history-coin`
- `trade-history-time-ms`
- `trade-history-value-number`
- `trade-history-fee-number`
- `trade-history-closed-pnl-number`
- `trade-history-row-id`

Internal dependency rule for this refactor: parsing helpers live in `parse.cljs`; domain-specific modules depend on parser helpers; the facade depends on domain-specific modules and re-exports symbols.

Plan revision note: 2026-02-18 23:18Z - Initial plan created from recommendation audit and repository guardrails; execution will proceed milestone-by-milestone with in-file progress updates.
Plan revision note: 2026-02-18 23:24Z - Updated after implementation with completed milestones, validation evidence, integration-test adjustments for canonical string order IDs, and final outcomes.
