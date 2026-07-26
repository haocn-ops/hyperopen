---
owner: platform
status: completed
source_of_truth: true
tracked_issue: hyperopen-ww5f
---

# Optimizer Contracts Hardening

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. It is self-contained so an engineer can execute the work without relying on the conversation that produced it.

Tracked issue: `hyperopen-ww5f` ("Harden optimizer contracts follow-up").

## Purpose / Big Picture

The first optimizer contracts pass created `hyperopen.portfolio.optimizer.contracts` as a named place for optimizer map shapes, schema versions, migrations, request signatures, wire codecs, and high-level state paths. This follow-up reduces the remaining guesswork by moving more hardcoded optimizer state paths behind named constants, tightening specs so they validate meaningful shape rather than only checking that some keys exist, and documenting that `optimizer.contracts` owns optimizer contract and codec boundaries.

This is still an internal refactor. A human can see it working by running the focused optimizer contracts tests and the full repo gates. The tests should prove that malformed drafts, signatures, tracking records, and solved result payloads are rejected, while known-good optimizer payloads remain valid.

## Progress

- [x] (2026-05-11T20:01:04Z) Created tracked issue `hyperopen-ww5f` and claimed it for this follow-up work.
- [x] (2026-05-11T20:01:04Z) Inspected the existing contracts namespace, contracts tests, optimizer boundary document, and prior completed optimizer contracts ExecPlan.
- [x] (2026-05-11T20:01:04Z) Created this active ExecPlan before editing production code.
- [x] (2026-05-11T20:04:00Z) Wrote RED tests for additional path constants, stricter specs, solved result payload validation, and explicit no-v2-format-change migration behavior.
- [x] (2026-05-11T20:04:27Z) Ran `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`; it failed with undeclared contract path vars and 20 expected contract assertion failures.
- [x] (2026-05-11T20:14:30Z) Implemented nested path constants and stricter specs in `src/hyperopen/portfolio/optimizer/contracts.cljs`.
- [x] (2026-05-11T20:15:10Z) Replaced remaining production hardcoded optimizer state paths under optimizer source and runtime effect adapters with contract path constants.
- [x] (2026-05-11T20:15:35Z) Updated `src/hyperopen/portfolio/optimizer/BOUNDARY.md` to name `optimizer.contracts` as the contracts/codecs owner.
- [x] (2026-05-11T20:16:06Z) Ran `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`; it passed with 3818 tests, 21055 assertions, 0 failures, 0 errors.
- [x] (2026-05-11T20:17:29Z) Ran required gates: `npm run check`, `npm test`, and `npm run test:websocket`; all passed.
- [x] (2026-05-11T20:18:24Z) Moved this plan to completed and closed `hyperopen-ww5f`.

## Surprises & Discoveries

- Observation: The existing `contracts.cljs` namespace already centralizes major optimizer root paths and the request/wire codec behavior, but several action namespaces still repeat nested paths such as `[:portfolio :optimizer :draft :return-model]` and `[:portfolio-ui :optimizer :black-litterman-editor]`.
  Evidence: `rg "\\[:portfolio :optimizer|\\[:portfolio-ui :optimizer" src/hyperopen/portfolio/optimizer` finds hardcoded path vectors in optimizer action namespaces.

- Observation: The current specs are intentionally permissive and mostly check schema version plus required keys.
  Evidence: `::result-payload` currently accepts any map with `:status`; `::tracking-snapshot` accepts any map with `:scenario-id`, `:as-of-ms`, and `:status`; `::request-signature` does not assert that `:input-signature` is the canonical signature of `:request`.

- Observation: The RED test run compiled but reported missing nested path vars and malformed contract maps accepted by the current specs.
  Evidence: `node out/test.js` reported undeclared vars such as `contracts/draft-return-model-path`, then finished with 3818 tests, 21055 assertions, 20 failures, 0 errors.

- Observation: After the implementation pass, the optimizer source and runtime effect adapters no longer repeat optimizer root state vectors outside the contract namespace.
  Evidence: `rg "\\[:portfolio :optimizer|\\[:portfolio-ui :optimizer" src/hyperopen/portfolio/optimizer src/hyperopen/runtime/effect_adapters -g '*.cljs'` reports only the two root constants in `src/hyperopen/portfolio/optimizer/contracts.cljs`.

## Decision Log

- Decision: Keep all optimizer persisted schema-version constants at `1` and add tests that unsupported version `2` inputs fail until a real persisted format change exists.
  Rationale: The user explicitly asked to add real v2 migration tests when persisted optimizer formats actually change. No such persisted format change is part of this task, so inventing a fake v2 migration would create misleading compatibility guarantees. A failure guard makes the current behavior explicit without pretending a v2 format exists.
  Date/Author: 2026-05-11 / Codex

- Decision: Tighten `::result-payload` for solved optimizer payloads while preserving permissive status-only validation for non-solved payloads.
  Rationale: Downstream UI and tracking code consume solved payload fields such as target/current weights, expected returns, diagnostics, and rebalance trades. Failed or pending payloads may legitimately be smaller. A status-sensitive spec catches malformed solved results without rejecting lightweight error envelopes.
  Date/Author: 2026-05-11 / Codex

- Decision: Replace production hardcoded paths opportunistically in action and optimizer-local modules, but leave literal paths in tests where they are asserting expected state shape.
  Rationale: Production code benefits from one source of truth. Test literals remain useful as assertions that the constants resolve to the intended state layout.
  Date/Author: 2026-05-11 / Codex

- Decision: Extend the path-constant replacement through optimizer runtime effect adapters, not only action namespaces.
  Rationale: Runtime adapters are a major boundary for scenario, history, tracking, execution, and pipeline state writes. Moving those paths behind `contracts.cljs` materially reduces remaining implicit state-layout knowledge while preserving the state shape.
  Date/Author: 2026-05-11 / Codex

## Outcomes & Retrospective

Complete. The implementation adds stricter optimizer specs, more path constants, and production-wide optimizer path replacement under the optimizer source and runtime effect adapters. It keeps persisted schema versions at `1` and documents that real v2 migration tests should wait for a real persisted format change. Overall complexity is reduced because optimizer state layout, request signatures, migrations, and wire codecs now have one clearer owner instead of repeated map-path knowledge across action and runtime namespaces.

## Context and Orientation

The optimizer bounded context lives under `src/hyperopen/portfolio/optimizer/`. Its contract layer is `src/hyperopen/portfolio/optimizer/contracts.cljs`. In this plan, a "contract" means a named shape at a boundary: an optimizer draft map, engine request map, request signature, saved scenario record, tracking record, worker message envelope, or optimizer result payload. A "codec" means a function that translates data as it crosses a boundary; here, the worker wire codec turns wire strings into keywords and normalizes maps keyed by instrument ids.

The main existing contract file, `src/hyperopen/portfolio/optimizer/contracts.cljs`, already defines root state path constants, schema version constants, migration functions, `clojure.spec.alpha` specs, request signature helpers, and worker wire normalization helpers. The follow-up should extend that file rather than introduce another schema library or split ownership again.

Production files that still repeat state paths include action namespaces such as `src/hyperopen/portfolio/optimizer/actions/common.cljs`, `src/hyperopen/portfolio/optimizer/actions/run.cljs`, `src/hyperopen/portfolio/optimizer/actions/draft.cljs`, `src/hyperopen/portfolio/optimizer/actions/execution.cljs`, `src/hyperopen/portfolio/optimizer/actions/universe.cljs`, `src/hyperopen/portfolio/optimizer/frontier_actions.cljs`, and Black-Litterman action helpers under `src/hyperopen/portfolio/optimizer/black_litterman_actions/`. These paths should use constants from `contracts.cljs` where the constant names make the code clearer.

The optimizer boundary document is `src/hyperopen/portfolio/optimizer/BOUNDARY.md`. It should explicitly say that `hyperopen.portfolio.optimizer.contracts` owns optimizer map contracts, schema versions, migrations, canonical request signatures, and worker wire codecs.

## Plan of Work

First, update `test/hyperopen/portfolio/optimizer/contracts_test.cljs` with failing tests. Add assertions for new nested path constants such as draft return model, draft constraints, execution modal fields, history request signatures, UI Black-Litterman editor paths, and frontier overlay paths. Add malformed-data tests showing that draft `:universe` must be a vector, known status fields must be recognized keywords, a request signature's `:input-signature` must match its `:request`, tracking snapshots must use stable status values, and solved result payloads must have coherent vector/map fields. Add an explicit migration guard showing that current schema versions remain `1` and unsupported version `2` inputs throw.

Second, run the focused CLJS test build and observe the new tests fail. From `/Users/barry/projects/hyperopen`, run:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js

Expected RED evidence is either missing-var failures for the new constants or spec assertions that currently pass malformed maps.

Third, update `src/hyperopen/portfolio/optimizer/contracts.cljs`. Add nested path constants for the action paths that are used repeatedly. Add small predicates for status values, non-empty strings, vectors, maps, request-signature consistency, tracking record coherence, and solved result payload dimensional checks. Keep specs open to extension keys so existing consumers can attach additional diagnostics.

Fourth, update production callers to use the new constants. Prioritize optimizer-local action namespaces and runtime-facing optimizer modules that repeatedly manipulate state by path. Do not change public API names or state layout. If a path is dynamic, use a named base constant plus `conj` or `into` instead of creating one-off string manipulation.

Fifth, update `src/hyperopen/portfolio/optimizer/BOUNDARY.md` with the contract ownership statement. The wording should be direct and should not broaden optimizer ownership beyond the existing bounded context.

Sixth, validate. Run the focused test build and then the required gates:

    npm run check
    npm test
    npm run test:websocket

No browser QA is required unless this task unexpectedly changes UI behavior or browser-test tooling. This task should remain a data-contract and application-action refactor.

## Concrete Steps

Work from `/Users/barry/projects/hyperopen`.

Create or update only source, test, and documentation files needed for the contract hardening. Use `apply_patch` for manual edits. After each meaningful milestone, update this ExecPlan's `Progress`, `Surprises & Discoveries`, `Decision Log`, or `Outcomes & Retrospective` sections so a later agent can resume from this file alone.

The first expected test command is:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

The final required commands are:

    npm run check
    npm test
    npm run test:websocket

Success means all three required gates exit with code 0. The CLJS test output should include no failures or errors.

## Validation and Acceptance

Acceptance requires all of the following:

1. `test/hyperopen/portfolio/optimizer/contracts_test.cljs` contains tests for additional path constants, stricter contract rejection, solved result payload validation, and unsupported v2 migration behavior.
2. `src/hyperopen/portfolio/optimizer/contracts.cljs` exposes the new path constants and stricter specs while keeping existing public constants and helper functions available.
3. Production optimizer code replaces additional hardcoded `[:portfolio :optimizer ...]` and `[:portfolio-ui :optimizer ...]` paths with `contracts/*-path` constants where it improves clarity.
4. No fake v2 migration is introduced while persisted optimizer formats remain version `1`; unsupported future versions must still fail loudly.
5. `src/hyperopen/portfolio/optimizer/BOUNDARY.md` explicitly names `hyperopen.portfolio.optimizer.contracts` as the optimizer contracts/codecs owner.
6. `npm run check`, `npm test`, and `npm run test:websocket` pass.

## Idempotence and Recovery

The changes are additive and safe to retry. Path constants must resolve to the same vectors as the existing hardcoded paths, so replacing callers should not change state layout. Specs should be stricter at boundary tests but still open to extension keys, which keeps callers from breaking when they include additional diagnostics.

If a test fails after replacing paths, compare the failed state path with the constant definition before changing application behavior. If a spec failure appears in existing tests, decide whether the existing data is truly malformed or whether the new spec became narrower than the stabilized contract supports. Do not change persisted schema-version constants to `2` unless this task also changes persisted data formats and includes real fixture-based migrations.

## Artifacts and Notes

The prior completed contracts plan is available at `docs/exec-plans/completed/2026-05-11-optimizer-contracts-codecs.md`. It established the initial contract namespace and kept schema versions at `1`.

RED and GREEN validation transcripts should be summarized here as implementation proceeds.

RED transcript summary:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js
    Generated test/test_runner_generated.cljs with 616 namespaces.
    Use of undeclared Var hyperopen.portfolio.optimizer.contracts/draft-return-model-path
    Ran 3818 tests containing 21055 assertions.
    20 failures, 0 errors.

GREEN transcript summary:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js
    Generated test/test_runner_generated.cljs with 616 namespaces.
    [:test] Build completed. (1601 files, 245 compiled, 0 warnings, 8.30s)
    Ran 3818 tests containing 21055 assertions.
    0 failures, 0 errors.

Required gate summary:

    npm run check
    Passed, including docs guardrails, namespace checks, app/portfolio/worker compiles, and test compile.

    npm test
    Ran 3818 tests containing 21055 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

## Interfaces and Dependencies

Use `clojure.spec.alpha` in `src/hyperopen/portfolio/optimizer/contracts.cljs`; do not introduce a new schema dependency. Existing public contract APIs must remain available:

- schema version constants such as `draft-schema-version`, `scenario-record-schema-version`, `tracking-record-schema-version`, `request-signature-schema-version`, `result-payload-schema-version`, and `worker-wire-schema-version`.
- root path constants such as `optimizer-path`, `draft-path`, `run-state-path`, `tracking-path`, and `optimizer-ui-path`.
- contract map `contract-specs`.
- migration functions `migrate-draft`, `migrate-scenario-record`, `migrate-tracking-record`, and `migrate-contract`.
- request signature helpers `optimizer-input-signature` and `build-request-signature`.
- wire helpers `normalize-worker-boundary`, `normalize-wire-values`, `normalize-instrument-keyed-maps`, `instrument-id-key`, `stringify-instrument-keyed-map`, `enum-value-keys`, and `instrument-keyed-map-paths`.

New path constants should follow the existing naming style: `<state-area>-path` for base paths and `<state-area>-<field>-path` for nested fields. Specs should remain namespaced under `hyperopen.portfolio.optimizer.contracts`.

## Plan Revision Notes

- 2026-05-11 / Codex: Initial active ExecPlan created after inspecting current contract, test, and boundary surfaces. The plan records that v2 migration tests should wait for a real persisted format change and that solved result payloads are the current downstream validation target.
- 2026-05-11 / Codex: Added RED evidence after writing focused contract hardening tests. The failures prove the current contracts lack the new path constants and stricter validations.
- 2026-05-11 / Codex: Implemented nested constants, stricter specs, production path replacement across optimizer/runtime adapter namespaces, and boundary ownership wording before running GREEN validation.
- 2026-05-11 / Codex: Added required gate evidence and completion retrospective after validation passed.
