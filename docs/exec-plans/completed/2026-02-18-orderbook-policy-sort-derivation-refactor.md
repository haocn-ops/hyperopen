# Refactor Orderbook Build to Single-Sort Side Derivations

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

`/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs` currently performs repeated full transforms while building each incoming orderbook packet. In particular, ask levels are fully sorted twice (legacy descending and display ascending), and normalized levels are stripped in separate passes. After this change, each side is sorted once, legacy and display projections are derived from that single sorted representation, and normalization/strip work is consolidated to avoid redundant passes in the hot path. Users should see the same book output shapes with less per-packet compute overhead.

## Progress

- [x] (2026-02-18 01:58Z) Reviewed planning requirements in `/hyperopen/.agents/PLANS.md` and scoped the hotspot in `/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs`.
- [x] (2026-02-18 01:58Z) Captured this active ExecPlan with explicit implementation and validation steps.
- [x] (2026-02-18 02:00Z) Implemented single-sort side derivation refactor in `/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs`.
- [x] (2026-02-18 02:00Z) Added compatibility coverage in `/hyperopen/test/hyperopen/websocket/orderbook_policy_test.cljs` for derived ascending ask display slices and best-ask selection.
- [x] (2026-02-18 02:01Z) Ran required gates: `npm run check`, `npm test`, `npm run test:websocket` (all passed).
- [x] (2026-02-18 02:01Z) Moved this plan from `/hyperopen/docs/exec-plans/active/` to `/hyperopen/docs/exec-plans/completed/` after acceptance confirmation.

## Surprises & Discoveries

- Observation: `build-book` already normalizes bids and asks once per packet, but ask-side sorting is duplicated because both legacy and display views call full sort passes.
  Evidence: `/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs` creates `legacy-asks` via descending sort and `display-asks` via ascending sort from the same normalized vector.
- Observation: `rseq` over the canonical ask vector enables a bounded ascending display slice (`take limit`) without allocating a full reversed vector.
  Evidence: Updated `build-book` now computes `display-asks-limited` with `(into [] (take limit* (rseq sorted-asks)))`.

## Decision Log

- Decision: Use descending sort as the canonical sorted representation for both sides and derive display asks from the reversed traversal of that same vector.
  Rationale: Descending canonical order preserves legacy output behavior while removing the second full ask-side sort.
  Date/Author: 2026-02-18 / Codex
- Decision: Introduce local helpers `sorted-normalized-levels`, `strip-normalized-levels`, and `take-leading-levels` to keep hot-path transforms explicit and single-pass.
  Rationale: Shared helpers reduce duplicated normalize/dissoc code paths and make per-side derivation intent obvious in `build-book`.
  Date/Author: 2026-02-18 / Codex

## Outcomes & Retrospective

Implementation complete for the targeted orderbook performance hotspot.

What was achieved:

- `build-book` now sorts bids once and asks once (both descending canonical vectors).
- Ask display slices are now derived from canonical asks via reverse traversal rather than a second full ascending sort.
- Legacy outputs remain compatibility-ordered (`:bids` and `:asks` descending) while render outputs remain unchanged in meaning.
- Helper functions centralize normalized sorting, stripping, and leading-slice extraction to remove repeated pass logic.
- Added coverage for ask-derivation compatibility in `build-book-ask-derivation-compatibility-test`.

Validation summary:

- `npm run check`: pass.
- `npm test`: pass (`1091` tests, `4947` assertions, `0` failures, `0` errors).
- `npm run test:websocket`: pass (`128` tests, `522` assertions, `0` failures, `0` errors).

Remaining risk:

- This refactor targets orderbook policy transformation overhead only; broader websocket throughput behavior is out of scope.

## Context and Orientation

`/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs` contains the orderbook policy helpers used by websocket handlers. `build-book` is called for each packet and returns both legacy keys (`:bids`, `:asks`) and a render snapshot (`:render`) containing display slices and cumulative totals.

In this file, “normalized level” means a map enriched with numeric helpers `:px-num` and `:sz-num`. Legacy outputs intentionally strip these helper keys. Display outputs intentionally keep helper keys for render math and cumulative totals.

The current hot path sorts asks twice and strips normalized keys in separate map passes. The requested refactor keeps public outputs the same while reducing redundant transforms.

## Plan of Work

First, refactor `build-book` in `/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs` so each side is sorted once. Keep descending sort as canonical for bids and asks, derive display asks from a reversed traversal of canonical asks, and derive best ask from canonical asks without a second sort.

Second, make strip operations explicit from canonical sorted vectors so each side is normalized once and stripped once. Preserve existing compatibility behavior for `:bids`, `:asks`, and render keys.

Third, adjust tests in `/hyperopen/test/hyperopen/websocket/orderbook_policy_test.cljs` if needed to verify compatibility behavior remains intact with the new derivation strategy.

Fourth, run required repo gates and record their outputs in this document. If all pass, move this file from active to completed.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs` and remove the duplicate ask-side full sort in `build-book`.
2. Ensure display asks and best ask are derived from the canonical sorted ask vector.
3. Update `/hyperopen/test/hyperopen/websocket/orderbook_policy_test.cljs` only if assertions need extension for the new derivation path.
4. Run:

       npm run check
       npm test
       npm run test:websocket

5. Move this plan to completed after successful validation.

## Validation and Acceptance

Acceptance requires all of the following:

- `build-book` sorts each side exactly once per packet and no longer performs a second full ask-side sort.
- Legacy outputs remain compatible:
  - `:bids` descending by price.
  - `:asks` descending by price (legacy order).
- Render outputs remain compatible:
  - `:display-bids` descending limited slice.
  - `:display-asks` ascending limited slice.
  - cumulative totals and best bid/ask unchanged in meaning.
- Required gates pass:
  - `npm run check`
  - `npm test`
  - `npm run test:websocket`

## Idempotence and Recovery

These edits are source-only and safe to repeat. If a regression is introduced, recovery is to re-run tests, compare with this plan’s acceptance criteria, and revert only the touched `orderbook_policy` edits while preserving test additions.

## Artifacts and Notes

Touched files:

- `/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs`
- `/hyperopen/test/hyperopen/websocket/orderbook_policy_test.cljs`
- `/hyperopen/docs/exec-plans/completed/2026-02-18-orderbook-policy-sort-derivation-refactor.md`

Validation evidence:

- `npm run check` completed with docs/lint checks passing and clean `:app`/`:test` compiles.
- `npm test` reported `Ran 1091 tests containing 4947 assertions. 0 failures, 0 errors.`
- `npm run test:websocket` reported `Ran 128 tests containing 522 assertions. 0 failures, 0 errors.`

## Interfaces and Dependencies

The following public functions in `/hyperopen/src/hyperopen/websocket/orderbook_policy.cljs` must remain stable:

- `sort-display-asks`
- `sort-bids`
- `sort-asks`
- `build-book`
- `build-subscription`

No external dependency changes are required. The refactor stays within existing data shapes and websocket orderbook policy semantics.

Plan revision note: 2026-02-18 01:58Z - Initial plan created for single-sort side derivation and redundant transform reduction in orderbook build path.
Plan revision note: 2026-02-18 02:01Z - Updated living sections after implementing canonical single-sort derivations, adding coverage, and passing required validation gates.
Plan revision note: 2026-02-18 02:01Z - Archived plan to completed status and reconciled path references.
