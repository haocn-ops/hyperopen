# Replace Portfolio Metrics Request Deep Hash With Lightweight Signature Dedupe

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, portfolio metrics request dedupe in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` will no longer hash the full nested request payload on every VM build. Instead, dedupe will compare a lightweight request signature built from selected summary time range, selected benchmark coins, and stable source version counters derived from already-built strategy and benchmark cumulative rows.

For users, chart and metrics output remain the same, while avoidable VM CPU overhead from deep payload hashing is removed. Contributors can verify this by running VM regressions and required repository gates.

## Progress

- [x] (2026-02-28 02:38Z) Reviewed planning/runtime guardrails and UI policy docs required for `/hyperopen/src/hyperopen/views/**` edits.
- [x] (2026-02-28 02:38Z) Audited current metrics request dedupe path in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` (`last-metrics-request`, `request-metrics-computation!`) and test coverage in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`.
- [x] (2026-02-28 02:39Z) Authored this active ExecPlan.
- [x] (2026-02-28 02:40Z) Implemented lightweight request signature helpers and replaced deep payload hash dedupe in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`.
- [x] (2026-02-28 02:40Z) Threaded summary time range and source-version counters through benchmark context and `performance-metrics-model`, and updated `request-metrics-computation!` to compare lightweight signatures.
- [x] (2026-02-28 02:41Z) Added regressions in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` for signature capture and dedupe behavior under differing payloads.
- [x] (2026-02-28 02:42Z) Ran required validation gates successfully: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-02-28 02:42Z) Updated this plan with final outcomes and prepared move to `/hyperopen/docs/exec-plans/completed/`.
- [x] (2026-02-28 02:43Z) Re-ran full required gates on the final tree after moving this plan to completed; all remained green.

## Surprises & Discoveries

- Observation: Current request dedupe computes `(hash request-data)` inside `request-metrics-computation!`, where `request-data` includes nested cumulative row vectors for portfolio and benchmarks.
  Evidence: `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` currently hashes full `request-data` before compare.

- Observation: Current VM tests validate benchmark computation reuse and request shaping, but do not assert dedupe behavior keyed by lightweight request signatures.
  Evidence: `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` covers benchmark request setup and compute behavior, but has no test invoking `request-metrics-computation!` dedupe contract.

- Observation: A compact sampled series counter (`count + first/mid/last time/value`) is enough to produce stable source versions without traversing full cumulative rows.
  Evidence: Added `sampled-series-source-version-counter` in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`; dedupe now compares this compact signature rather than hashing the nested request payload.

- Observation: Request dedupe behavior can be regression-tested without a real worker by observing `:metrics-loading?` projection writes behind `system/store`.
  Evidence: `portfolio-vm-request-metrics-computation-dedupes-by-lightweight-signature-test` in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` verifies only two writes across three calls when the middle call reuses the same signature.

## Decision Log

- Decision: Keep the optimization scoped to portfolio VM dedupe and tests; do not widen to runtime/action contracts.
  Rationale: The reported hot path is local to VM request dedupe and can be fixed without changing public APIs or runtime orchestration.
  Date/Author: 2026-02-28 / Codex

- Decision: Build dedupe signature from summary time range, selected benchmark coins, and compact per-series source counters rather than full payload hashes.
  Rationale: This preserves meaningful invalidation semantics while avoiding O(payload) hash traversal each VM build.
  Date/Author: 2026-02-28 / Codex

- Decision: Keep dedupe signature values as plain Clojure data (`:summary-time-range`, ordered `:selected-benchmark-coins`, ordered benchmark source version pairs) and compare via `=`.
  Rationale: Structural equality over small signature data is deterministic and avoids introducing custom comparator complexity.
  Date/Author: 2026-02-28 / Codex

## Outcomes & Retrospective

Implemented as planned.

What changed:

- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` no longer computes `(hash request-data)` inside `request-metrics-computation!`.
- Added `sampled-series-source-version-counter`, `benchmark-source-version-by-coin`, and `metrics-request-signature` in the portfolio VM.
- `benchmark-computation-context` now emits source-version data (`:strategy-source-version`, `:benchmark-source-version-map`) alongside cumulative rows.
- `performance-metrics-model` now takes `summary-time-range`, builds a lightweight signature, and calls `request-metrics-computation!` with that signature.
- `portfolio-vm` now passes `summary-time-range` into `performance-metrics-model`.
- `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` now includes:
  - `portfolio-vm-request-metrics-computation-dedupes-by-lightweight-signature-test`
  - `portfolio-vm-metrics-request-signature-captures-time-range-coins-and-source-versions-test`
  - fixture reset for `last-metrics-request` to keep tests isolated.

Validation evidence:

- `npm run check` passed.
- `npm test` passed (`Ran 1506 tests containing 7645 assertions. 0 failures, 0 errors.`).
- `npm run test:websocket` passed (`Ran 153 tests containing 701 assertions. 0 failures, 0 errors.`).

Scope gaps:

- None identified for this optimization scope.

## Context and Orientation

Portfolio metrics are assembled in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` under `performance-metrics-model`. That function builds `request-data`, then calls `request-metrics-computation!` to post to the portfolio worker when available.

Today, dedupe state is stored in `last-metrics-request`, and dedupe compares `(hash request-data)` to the previous hash. Because `request-data` holds nested vectors for strategy and benchmark cumulative rows, this hash traversal scales with payload size.

This task keeps current metrics computation behavior and request payload shape intact. Only dedupe keying changes: we replace full payload hashing with a compact signature that tracks user selection and source-series versions.

Relevant files:

- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`
- `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`

## Plan of Work

### Milestone 1: Introduce Lightweight Metrics Request Signature Helpers

Add small VM-local helpers that compute deterministic source version counters from strategy and benchmark cumulative rows without traversing every row element. Add a request-signature helper that includes:

- normalized summary time range,
- selected benchmark coin vector (order-preserving),
- strategy source version counter,
- benchmark source version counters by selected coin.

### Milestone 2: Replace Deep Hash Dedupe in Request Dispatcher

Refactor `request-metrics-computation!` to accept the precomputed lightweight signature and compare that signature against `last-metrics-request`. Remove deep `(hash request-data)` usage from this path. Keep side-effect order unchanged: mark loading before post and post only when worker exists.

### Milestone 3: Thread Signature Inputs Through Metrics Model

Update `performance-metrics-model` and its call site to pass summary time range and benchmark context source counters into the new signature helper. Keep existing request payload shaping and metrics result projection contracts unchanged.

### Milestone 4: Add Regressions and Run Required Gates

Add regressions in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` that prove:

- same lightweight signature suppresses duplicate worker posts even if payload objects differ,
- changing a signature input triggers a new post.

Then run required gates from `/hyperopen`:

- `npm run check`
- `npm test`
- `npm run test:websocket`

## Concrete Steps

1. Edit `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`:
   - add source-version counter helpers,
   - add metrics-request-signature helper,
   - update `request-metrics-computation!` to compare the new signature,
   - thread new signature inputs through `benchmark-computation-context`, `performance-metrics-model`, and `portfolio-vm`.
2. Edit `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`:
   - add worker-post dedupe regression for `request-metrics-computation!`,
   - verify post issuance when signature changes.
3. Run required gates from `/Users//projects/hyperopen`:

       npm run check
       npm test
       npm run test:websocket

4. Update all living sections in this plan and move file to completed directory after validation passes.

## Validation and Acceptance

Acceptance is complete when all conditions below are true.

1. `request-metrics-computation!` no longer computes `(hash request-data)` for dedupe.
2. Dedupe uses a lightweight signature composed of summary time range, selected benchmark coins, and source version counters.
3. Metrics payload shape and downstream worker/sync compute behavior remain unchanged.
4. Regression tests verify that duplicate signatures suppress duplicate worker posts and changed signatures trigger new posts.
5. Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

This refactor is VM/test local and safe to reapply. If regressions appear, recovery path is to retain signature helper scaffolding and temporarily route dedupe back to always-send behavior (or prior hash compare) while preserving new tests to guide a corrected lightweight implementation.

No migrations or destructive operations are involved.

## Artifacts and Notes

Primary implementation target:

- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`

Primary regression target:

- `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`

No new external dependency is required.

## Interfaces and Dependencies

Interfaces that must remain stable:

- `hyperopen.views.portfolio.vm/portfolio-vm` output shape consumed by portfolio view.
- Worker message contract for `"compute-metrics"` and `"metrics-result"`.
- `build-metrics-request-data` and `compute-metrics-sync` payload semantics.

Internal interface additions in this plan:

- VM-local helper for compact source version counters from cumulative rows.
- VM-local helper for lightweight metrics request signatures.

Plan revision note: 2026-02-28 02:39Z - Initial ExecPlan created for replacing deep metrics request hashing with lightweight signature dedupe in portfolio VM.
Plan revision note: 2026-02-28 02:42Z - Marked implementation complete with VM signature dedupe refactor, added regressions, and recorded required gate outcomes.
Plan revision note: 2026-02-28 02:43Z - Revalidated final tree after moving plan to completed; required gates remained green.
