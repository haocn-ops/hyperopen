# Refactor webData2 Asset Context Normalization to Static Meta Index and Incremental Patching

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The current websocket `webData2` handler rebuilds the normalized `:asset-contexts` map from scratch for every `webData2` message. That repeatedly recomputes static metadata indexing (asset names, margin-table lookup, and index binding) even though metadata is mostly stable across messages. After this change, the system will compute static meta indexing separately and apply dynamic `assetCtxs` updates incrementally, patching only changed keys and preserving unchanged entries. The user-visible behavior stays the same (same `:asset-contexts` content and filtering rules), but runtime allocation and store churn on the hot websocket path should drop.

## Progress

- [x] (2026-02-18 13:10Z) Audited current hotspot in `/hyperopen/src/hyperopen/websocket/webdata2.cljs` and `/hyperopen/src/hyperopen/utils/data_normalization.cljs`.
- [x] (2026-02-18 13:10Z) Confirmed repository planning and validation requirements from `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`.
- [x] (2026-02-18 13:10Z) Authored this active ExecPlan.
- [x] (2026-02-18 13:11Z) Implemented static meta indexing and incremental patch helpers in `/hyperopen/src/hyperopen/utils/data_normalization.cljs` while preserving legacy `normalize-asset-contexts` behavior.
- [x] (2026-02-18 13:11Z) Refactored `/hyperopen/src/hyperopen/websocket/webdata2.cljs` to cache index metadata and patch `:asset-contexts` incrementally from prior store state.
- [x] (2026-02-18 13:12Z) Added regression coverage for incremental patch behavior and webdata2 handler integration in `/hyperopen/test/hyperopen/utils/data_normalization_test.cljs` and `/hyperopen/test/hyperopen/websocket/webdata2_test.cljs`.
- [x] (2026-02-18 13:12Z) Wired new websocket test namespace into `/hyperopen/test/test_runner.cljs` and `/hyperopen/test/websocket_test_runner.cljs`.
- [x] (2026-02-18 13:20Z) Ran required gates successfully on final tree: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-02-18 13:14Z) Updated this plan with final outcomes/evidence; ready to move to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: `webData2` is the only websocket handler currently writing `:asset-contexts` in store.
  Evidence: Search results show only `/hyperopen/src/hyperopen/websocket/webdata2.cljs` writing that key.
- Observation: Existing utility tests cover only full rebuild normalization; there is no current regression test for incremental patching.
  Evidence: `/hyperopen/test/hyperopen/utils/data_normalization_test.cljs` currently has `preprocess-webdata2` and full `normalize-asset-contexts` tests only.
- Observation: `test` and `ws-test` runners did not include a `webdata2` websocket test namespace, so adding the new tests required explicit runner wiring.
  Evidence: `/hyperopen/test/test_runner.cljs` and `/hyperopen/test/websocket_test_runner.cljs` had no `hyperopen.websocket.webdata2-test` entry before this change.
- Observation: Required gate commands emit environment warnings (`Unknown user/env config "python"`), but all checks still pass.
  Evidence: Warnings appear at the start of each npm gate command and do not affect exit status.

## Decision Log

- Decision: Keep the public `normalize-asset-contexts` utility function and refactor internals into composable helpers (`meta index` + `incremental patch`) rather than changing API shape.
  Rationale: `normalize-asset-contexts` is already used by API endpoint code (`metaAndAssetCtxs`), so preserving signature minimizes integration risk.
  Date/Author: 2026-02-18 / Codex
- Decision: Perform incremental patching against prior store `:asset-contexts` map in the websocket handler.
  Rationale: This removes full-map rebuild churn in the hot path while preserving deterministic filtering behavior.
  Date/Author: 2026-02-18 / Codex
- Decision: Detect static index refresh needs in `webdata2` handler with a stable meta signature (`count` + hash of `[universe marginTables]`) and reset seed map on signature change.
  Rationale: This keeps cached-index correctness for any static-meta content change while avoiding repeated full index rebuild when metadata is unchanged.
  Date/Author: 2026-02-18 / Codex
- Decision: Add dedicated websocket handler tests with `with-redefs` around normalization helpers to assert cache reuse and incremental patch seed behavior.
  Rationale: This validates integration behavior directly in the hot path without brittle assumptions about internal persistent-map implementation details.
  Date/Author: 2026-02-18 / Codex

## Outcomes & Retrospective

Implementation is complete for the scoped performance refactor.

What changed:

- Added `build-asset-context-meta-index` and `patch-asset-contexts` in `/hyperopen/src/hyperopen/utils/data_normalization.cljs`.
- Rewrote `normalize-asset-contexts` as a compatibility wrapper that composes new helpers (`index` + `patch` from empty seed), preserving external API usage.
- Refactored `/hyperopen/src/hyperopen/websocket/webdata2.cljs` to cache static meta index per handler and patch `:asset-contexts` incrementally from prior store state.
- Added meta signature tracking in websocket handler and reset seed map on signature changes to prevent stale keys when shape changes.
- Added new websocket regression tests in `/hyperopen/test/hyperopen/websocket/webdata2_test.cljs` plus utility incremental behavior tests in `/hyperopen/test/hyperopen/utils/data_normalization_test.cljs`.
- Added runner wiring for new websocket test namespace in `/hyperopen/test/test_runner.cljs` and `/hyperopen/test/websocket_test_runner.cljs`.

Validation evidence:

- `npm run check`: pass.
- `npm test`: pass (`1100` tests, `4989` assertions, `0` failures, `0` errors).
- `npm run test:websocket`: pass (`131` tests, `538` assertions, `0` failures, `0` errors).

Acceptance status:

- Static meta indexing split from dynamic patch path: met.
- Dynamic `assetCtxs` updates patch incrementally: met.
- `normalize-asset-contexts` compatibility preserved for API endpoint callers: met.
- Required validation gates: met.

## Context and Orientation

Inbound account websocket payloads are handled in `/hyperopen/src/hyperopen/websocket/webdata2.cljs`. On each `webData2` message, the handler currently does two writes in one merge: it stores raw payload at `:webdata2` and normalizes `[:meta :assetCtxs]` into `:asset-contexts` by calling `preprocess-webdata2` plus `normalize-asset-contexts`.

Normalization logic lives in `/hyperopen/src/hyperopen/utils/data_normalization.cljs`. Today, `normalize-asset-contexts` receives `[meta assetCtxs]`, computes `margin-map`, loops through `universe` with `map-indexed`, filters by `dayNtlVlm` and `openInterest`, and reduces into a fresh map keyed by `keyword name` for every call.

In this plan, “static meta indexing” means deriving per-asset stable fields from meta (`:idx`, `:info`, margin lookup keying) once and reusing them across messages. “Incremental patching” means applying each incoming `assetCtxs` vector onto the previous normalized map, updating only changed keys and removing keys that are no longer eligible.

## Plan of Work

Milestone 1 refactors `/hyperopen/src/hyperopen/utils/data_normalization.cljs` into a two-stage model: one helper builds a reusable meta index from `meta`, and another helper patches a normalized map from dynamic `assetCtxs`. The existing `normalize-asset-contexts` entry point is then rewritten as a compatibility wrapper that composes these helpers and still returns the same result shape for API users.

Milestone 2 updates `/hyperopen/src/hyperopen/websocket/webdata2.cljs` so `create-webdata2-handler` keeps a lightweight cache of the static meta index and reuses it across messages. Store writes will use `swap!` with a function that patches prior `:asset-contexts` incrementally and always updates `:webdata2` first in the same deterministic state transition.

Milestone 3 adds/updates tests to prove both correctness and integration behavior: unchanged rows do not force structural churn, filtered assets are removed incrementally, and handler-level updates produce expected state while avoiding repeated static-index rebuild in normal streams.

Milestone 4 executes required validation gates and records the observed results. If all acceptance criteria pass, this plan file is moved from active to completed.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/utils/data_normalization.cljs`:
   - Add static meta indexing helper(s).
   - Add incremental patch helper(s) that consume prior normalized map and latest dynamic contexts.
   - Preserve `normalize-asset-contexts` as a compatibility wrapper.
2. Edit `/hyperopen/src/hyperopen/websocket/webdata2.cljs`:
   - Cache static meta index per handler lifecycle.
   - Patch `:asset-contexts` incrementally within store `swap!`.
   - Reset seed map when meta signature changes.
3. Edit tests:
   - `/hyperopen/test/hyperopen/utils/data_normalization_test.cljs`
   - Add websocket handler regression tests if needed.
4. Run required gates:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
5. Update this plan’s living sections with actual outcomes and move file to completed directory.

Expected transcript shape:

  - `npm run check` exits without lint/compile failures.
  - `npm test` exits with zero failures and zero errors.
  - `npm run test:websocket` exits with zero failures and zero errors.

## Validation and Acceptance

Acceptance is met when all are true:

- `webData2` handler no longer rebuilds static meta indexing on every message in the normal steady-state stream.
- `:asset-contexts` output remains behaviorally equivalent to previous filtering and payload shape rules.
- Incremental patching updates changed assets, removes filtered/missing assets, and preserves unchanged entries.
- Existing endpoint path `normalize-asset-contexts` remains compatible for non-websocket callers.
- Required gates pass: `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

All edits are source-only and safe to re-run. If incremental behavior regresses at runtime, recovery is to keep new helper functions but route websocket handler back through full rebuild wrapper `normalize-asset-contexts` while tests stay in place to guide a safer re-introduction. No schema/data migrations or destructive operations are involved.

## Artifacts and Notes

Touched paths:

- `/hyperopen/src/hyperopen/utils/data_normalization.cljs`
- `/hyperopen/src/hyperopen/websocket/webdata2.cljs`
- `/hyperopen/test/hyperopen/utils/data_normalization_test.cljs`
- `/hyperopen/test/hyperopen/websocket/webdata2_test.cljs`
- `/hyperopen/test/test_runner.cljs`
- `/hyperopen/test/websocket_test_runner.cljs`

## Interfaces and Dependencies

Interfaces to preserve:

- `/hyperopen/src/hyperopen/utils/data_normalization.cljs` public function `normalize-asset-contexts`.
- `/hyperopen/src/hyperopen/websocket/webdata2.cljs` public lifecycle functions (`create-webdata2-handler`, `init!`, subscribe/unsubscribe).

No external dependency changes are expected. The refactor remains inside existing utility and websocket modules.

Plan revision note: 2026-02-18 13:10Z - Initial ExecPlan created for webData2 asset-context normalization performance refactor (static meta indexing + incremental patching).
Plan revision note: 2026-02-18 13:14Z - Updated with implemented source/test changes, validation evidence, decisions, and completion status before move to completed.
Plan revision note: 2026-02-18 13:20Z - Re-ran required validation gates after final signature hardening to keep evidence aligned with final tree.
