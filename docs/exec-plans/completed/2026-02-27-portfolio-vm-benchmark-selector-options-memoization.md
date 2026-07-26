# Memoize Benchmark Selector Option Building in Portfolio VM

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` will stop re-sorting, re-filtering, and re-deduping the full `:asset-selector :markets` list on every `portfolio-vm` build when that list has not meaningfully changed. The benchmark selector options will be reused from a cache keyed by markets identity first and by a deterministic signature fallback when identity churn occurs without content changes.

For users, the visible benchmark selector behavior stays the same (same options, labels, and ranking), but the repeated compute cost in portfolio VM rebuilds is reduced. Contributors can verify this by running the VM tests and the required repository gates.

## Progress

- [x] (2026-02-27 02:23Z) Reviewed `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and required UI policy docs for view-layer edits: `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, `/hyperopen/docs/agent-guides/trading-ui-policy.md`.
- [x] (2026-02-27 02:23Z) Audited current benchmark selector option path in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` and current portfolio VM regressions in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`.
- [x] (2026-02-27 02:24Z) Authored this active ExecPlan.
- [x] (2026-02-27 02:25Z) Implemented benchmark selector option cache in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` with identity-first hits, signature fallback, and invalidation on signature changes.
- [x] (2026-02-27 02:25Z) Added regression coverage in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` (`portfolio-vm-memoizes-benchmark-selector-options-by-markets-identity-and-signature-test`) and per-test cache reset fixture.
- [x] (2026-02-27 02:25Z) Ran required validation gates successfully: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-02-27 02:25Z) Updated this plan with implementation outcomes, discoveries, and validation evidence.
- [x] (2026-02-27 02:26Z) Re-ran all required validation gates on the final tree after ExecPlan updates; all remained green.

## Surprises & Discoveries

- Observation: `benchmark-selector-options` currently sorts and deduplicates `[:asset-selector :markets]` for every VM build with no memoization boundary.
  Evidence: `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` defines `benchmark-selector-options` as a direct `sort-by` + `reduce` pipeline over `state` markets.

- Observation: Existing view-layer cache patterns in this repository use identity-first checks and optional signature fallback to remain stable under structural churn.
  Evidence: `/hyperopen/src/hyperopen/views/account_info/cache_keys.cljs` and `/hyperopen/src/hyperopen/views/account_info/tabs/trade_history.cljs`.

- Observation: A `defonce` VM cache introduces cross-test contamination risk unless tests explicitly reset cache state between cases.
  Evidence: Added `(use-fixtures :each ...)` calling `vm/reset-portfolio-vm-cache!` in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` to keep test ordering irrelevant.

## Decision Log

- Decision: Keep memoization local to portfolio VM namespace instead of introducing runtime/action-layer state changes.
  Rationale: The hot path is purely VM-local derivation and can be optimized without widening state ownership or side effects across boundaries.
  Date/Author: 2026-02-27 / Codex

- Decision: Use identity-first cache hits with deterministic market-list signature fallback.
  Rationale: This captures the fastest path when list identity is stable while still avoiding recomputation when upstream emits equivalent-but-new market vectors.
  Date/Author: 2026-02-27 / Codex

- Decision: Include only benchmark-option-relevant market fields in the signature.
  Rationale: This avoids needless invalidation for unrelated market field churn while still invalidating for any change that can affect benchmark option content or ordering.
  Date/Author: 2026-02-27 / Codex

- Decision: Expose a small test seam (`*build-benchmark-selector-options*`) and `reset-portfolio-vm-cache!` in VM namespace.
  Rationale: This allows direct cache-hit/invalidation verification without exposing private helper internals or changing user-facing view model contracts.
  Date/Author: 2026-02-27 / Codex

## Outcomes & Retrospective

Implemented and validated as planned.

What changed:

- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` now has a memoized benchmark option cache (`benchmark-selector-options-cache`) keyed by markets identity plus deterministic signature fallback.
- The benchmark option pipeline was split into a pure builder (`build-benchmark-selector-options`) and a memoized wrapper (`memoized-benchmark-selector-options`), with `benchmark-selector-options` now reading through that wrapper.
- Cache invalidation occurs when signature-relevant market fields differ from the last cached signature.
- Test support was added via `reset-portfolio-vm-cache!` and dynamic builder seam `*build-benchmark-selector-options*`.
- `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` now verifies identity hits, signature hits, and invalidation recomputation using a builder call counter.

Validation evidence:

- `npm run check` passed.
- `npm test` passed (`Ran 1474 tests containing 7428 assertions. 0 failures, 0 errors.`).
- `npm run test:websocket` passed (`Ran 153 tests containing 701 assertions. 0 failures, 0 errors.`).
- The same three required gates were re-run after final plan edits and remained green.

Scope gaps:

- None identified for this optimization scope.

## Context and Orientation

The benchmark selector shown in portfolio returns UI is produced by `returns-benchmark-selector-model` in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`. It calls `benchmark-selector-options`, which currently:

1. Reads `[:asset-selector :markets]`.
2. Filters to maps.
3. Sorts by benchmark rank (`openInterest`, `cache-order`, then symbol/coin/key fallback).
4. Deduplicates by normalized benchmark coin.
5. Builds selector options (`:value`, `:label`, `:open-interest`).

This work does not change user-facing selector semantics. It introduces a memoized boundary around that option-building pipeline so repeated VM builds can reuse prior results when the market list is unchanged by identity or unchanged by signature.

Related code paths and tests:

- VM source: `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`
- VM tests: `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`

## Plan of Work

### Milestone 1: Add Memoized Benchmark Option Cache in Portfolio VM

Add a cache atom in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` for benchmark selector options. The cache entry stores:

- the last `markets` reference,
- a deterministic signature computed from benchmark-relevant market fields,
- the computed options vector.

Implement helper functions:

- a signature builder for the market list,
- a pure option builder (existing sort/filter/dedupe logic moved here),
- a memoized wrapper that checks identity first, then signature, then recomputes on mismatch.

Wire `benchmark-selector-options` to this memoized wrapper.

### Milestone 2: Add Cache Reset/Test Seams and Regressions

Expose a small reset function in VM namespace for test isolation and define a test seam for counting option-builder invocations. Add regressions in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` that verify:

- same market-list identity performs one build then cache hits,
- different identity with same signature reuses cached options without rebuild,
- changed relevant market data invalidates cache and triggers rebuild.

### Milestone 3: Validate and Capture Evidence

Run required gates from `/hyperopen`:

- `npm run check`
- `npm test`
- `npm run test:websocket`

Record outcomes in this plan’s living sections and close with retrospective.

## Concrete Steps

1. Edit `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` to add signature and memoization helpers for benchmark selector options, plus cache reset function.
2. Edit `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` to add cache behavior regressions and reset fixture wiring.
3. Run validation commands from repository root:

       npm run check
       npm test
       npm run test:websocket

4. Update this plan (`Progress`, `Surprises & Discoveries`, `Decision Log`, `Outcomes & Retrospective`, and revision note).

## Validation and Acceptance

Acceptance is complete when all conditions below are true.

1. `benchmark-selector-options` no longer recomputes on every VM build when market list identity is unchanged.
2. Equivalent market lists with different identity and same signature reuse cached options.
3. Any change to signature-relevant market list content invalidates cache and recomputes options.
4. Existing benchmark selector output behavior remains unchanged for current VM fixtures.
5. Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

The implementation is additive and VM-local. Re-running edits and tests is safe. If regressions appear, recovery path is to temporarily route `benchmark-selector-options` back to the pure non-memoized builder while preserving the signature helper and tests, then reintroduce cache hits incrementally.

No migration, persistent data mutation, or destructive operation is involved.

## Artifacts and Notes

Primary implementation target:

- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`

Primary regression target:

- `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`

New regression in this change:

- `portfolio-vm-memoizes-benchmark-selector-options-by-markets-identity-and-signature-test`

This change does not require new external dependencies.

## Interfaces and Dependencies

Interfaces that remain stable:

- `hyperopen.views.portfolio.vm/portfolio-vm` output shape consumed by `/hyperopen/src/hyperopen/views/portfolio_view.cljs`.
- benchmark selector view-model fields under `[:selectors :returns-benchmark]`.

New internal interface surface in VM namespace:

- a memoized benchmark selector option helper keyed by market list identity/signature,
- a cache reset helper used for deterministic test isolation.

Plan revision note: 2026-02-27 02:24Z - Initial ExecPlan created for benchmark selector option memoization and invalidation behavior in portfolio VM.
Plan revision note: 2026-02-27 02:25Z - Marked implementation complete; documented cache design, regression coverage, and required gate outcomes.
Plan revision note: 2026-02-27 02:26Z - Recorded final-tree gate rerun results after documentation updates.
