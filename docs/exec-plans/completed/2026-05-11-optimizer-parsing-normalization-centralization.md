---
owner: portfolio
status: completed
source_of_truth: true
tracked_issue: hyperopen-d6nb
---

# Optimizer Parsing And Normalization Centralization ExecPlan

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current while the work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. It is self-contained so an engineer can execute the work without relying on the conversation that produced it.

Tracked issue: `hyperopen-d6nb` ("Centralize optimizer parsing and normalization helpers").

## Purpose / Big Picture

The portfolio optimizer currently repeats small parsing and normalization helpers in many files. The repeated helpers trim user and API text, parse numeric fields, normalize keyword-like route values, normalize vault addresses, and convert maps keyed by instrument ids before data crosses the optimizer worker boundary. This creates a maintenance hazard: a new optimizer request or result field can quietly miss normalization, especially in the worker wire path.

After this change, optimizer code will use three small optimizer-owned modules: `hyperopen.portfolio.optimizer.coercion`, `hyperopen.portfolio.optimizer.ids`, and `hyperopen.portfolio.optimizer.instrument-keyed-codec`. The existing public API names in `contracts.cljs` and `infrastructure/wire.cljs` will remain available, but they will delegate to the focused modules. A developer can see the refactor working by running focused ClojureScript tests that fail before the new modules exist and pass after callers delegate to them. The required repository gates must also pass.

## Progress

- [x] (2026-05-11T00:00:00-04:00) Inspected optimizer contracts, request building, current portfolio, query state, readiness, universe candidates, Black-Litterman action helpers, wire tests, and current ExecPlan requirements.
- [x] (2026-05-11T00:00:00-04:00) Created tracked issue `hyperopen-d6nb` and claimed it for this refactor.
- [x] (2026-05-11T00:00:00-04:00) Created this active ExecPlan before implementation.
- [x] (2026-05-11T23:43:29Z) Added focused RED tests for the new `coercion`, `ids`, and `instrument-keyed-codec` modules.
- [x] (2026-05-11T23:43:29Z) Ran `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test`; it failed as expected because `hyperopen.portfolio.optimizer.coercion` does not exist yet.
- [x] (2026-05-11T23:54:15Z) Implemented `coercion.cljs`, `ids.cljs`, and `instrument_keyed_codec.cljs`; `contracts.cljs` and `wire.cljs` now delegate existing public codec names to the new codec module.
- [x] (2026-05-11T23:54:15Z) Refactored duplicated callers in optimizer actions, query state, readiness, request building, current portfolio, history loading, infrastructure helpers, optimizer domain math/objectives/rebalance, and focused optimizer views.
- [x] (2026-05-11T23:54:15Z) Ran `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`; it passed with 3829 tests, 21138 assertions, 0 failures, 0 errors.
- [x] (2026-05-11T23:56:47Z) Ran `npm run check`; it passed, including docs, namespace checks, optimizer contract path checks, app/portfolio/worker compiles, and test compile.
- [x] (2026-05-11T23:56:47Z) Ran `npm test`; it passed with 3829 tests, 21138 assertions, 0 failures, 0 errors.
- [x] (2026-05-11T23:56:47Z) Ran `npm run test:websocket`; it passed with 524 tests, 3043 assertions, 0 failures, 0 errors.
- [x] (2026-05-11T23:56:47Z) Updated outcomes and prepared this plan for `docs/exec-plans/completed/` after acceptance passed.
- [x] (2026-05-12T00:01:32Z) Ran `npm run qa:design-ui -- --targets portfolio-optimizer-route,portfolio-optimizer-results-route --manage-local-app`; it completed with `reviewOutcome: PASS` for `/portfolio/optimize/new` and `/portfolio/optimize/qa-frontier` across 375, 768, 1280, and 1440px review viewports. Residual blind spots were limited to hover, active, disabled, and loading states that were not present by default.
- [x] (2026-05-12T00:02:00Z) Ran `npm run browser:cleanup`; it returned `ok: true` with no remaining browser-inspection sessions to stop.
- [x] (2026-05-12T00:04:01Z) Re-ran final validation after recording browser-QA evidence: `npm run lint:docs`, `npm run check`, `npm test`, and `npm run test:websocket` all exited 0.

## Surprises & Discoveries

- Observation: A prior optimizer contract hardening pass already moved the public worker normalization functions out of `wire.cljs` and into `contracts.cljs`, but the codec still uses a manual `instrument-keyed-map-paths` vector.
  Evidence: `src/hyperopen/portfolio/optimizer/infrastructure/wire.cljs` re-exports `contracts/instrument-keyed-map-paths`, while `src/hyperopen/portfolio/optimizer/contracts.cljs` defines concrete paths such as `[:payload :target-weights-by-instrument]` and `[:diagnostics :weight-sensitivity-by-instrument]`.

- Observation: The same text and number helpers are repeated across application, action, infrastructure, and view namespaces.
  Evidence: `rg` found repeated definitions in `application/request_builder.cljs`, `application/current_portfolio.cljs`, `application/setup_readiness.cljs`, `application/universe_candidates.cljs`, `actions/common.cljs`, `black_litterman_actions/common.cljs`, `application/history_loader/normalization.cljs`, `application/history_loader/instruments.cljs`, `infrastructure/prior_data.cljs`, `application/engine/payload.cljs`, and multiple files under `src/hyperopen/views/portfolio/optimize/`.

- Observation: The RED tests are wired into the generated test runner before the production namespaces exist.
  Evidence: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test` generated 619 namespaces and failed with `The required namespace "hyperopen.portfolio.optimizer.coercion" is not available`.

- Observation: The new codec can normalize a previously unseen nested instrument-keyed result map without a path entry.
  Evidence: `instrument-keyed-codec-test` covers `[:payload :future-result :weights-by-instrument]` and `[:payload :future-result :nested 0 :target-weights-by-instrument]`; the full CLJS test run passed after implementation.

## Decision Log

- Decision: Create `coercion.cljs`, `ids.cljs`, and `instrument_keyed_codec.cljs` at `src/hyperopen/portfolio/optimizer/` instead of adding more helpers to `contracts.cljs`.
  Rationale: `contracts.cljs` should keep owning contract versions, paths, specs, migrations, request signatures, and compatibility re-exports. Focused helper namespaces make the lower-level behavior reusable without making `contracts.cljs` a general utility namespace.
  Date/Author: 2026-05-11 / Codex

- Decision: Preserve the existing public functions and vars exported by `contracts.cljs` and `infrastructure/wire.cljs`.
  Rationale: Runtime adapters, tests, and older optimizer code already call those names. Delegating through them reduces blast radius while still centralizing implementation.
  Date/Author: 2026-05-11 / Codex

- Decision: Replace the path-driven worker map conversion with a key-driven recursive codec for instrument-keyed maps.
  Rationale: New request or result payloads usually use semantic leaf keys such as `:target-weights-by-instrument`, `:return-series-by-instrument`, or `:by-instrument`. A key-driven codec normalizes those maps wherever they appear, avoiding a future manual path update every time a new payload nesting is introduced.
  Date/Author: 2026-05-11 / Codex

- Decision: Keep `instrument-keyed-map-paths` as a compatibility export but remove it from the active normalization algorithm.
  Rationale: Existing callers can still inspect or import the var, but worker normalization now traverses by semantic leaf key through `instrument-keyed-map-keys`, so future result/request nesting does not require editing the path list.
  Date/Author: 2026-05-11 / Codex

## Outcomes & Retrospective

Complete. The implementation adds `src/hyperopen/portfolio/optimizer/coercion.cljs`, `src/hyperopen/portfolio/optimizer/ids.cljs`, and `src/hyperopen/portfolio/optimizer/instrument_keyed_codec.cljs`. Existing compatibility exports remain available through `contracts.cljs` and `infrastructure/wire.cljs`, while the active worker codec now normalizes instrument-keyed maps recursively by semantic key instead of depending on a fixed path list.

The refactor reduces overall complexity by making parsing, keyword normalization, vault id normalization, and instrument-keyed map handling single-purpose modules. The only compatibility compromise is keeping `instrument-keyed-map-paths` as a legacy exported value; it is no longer the algorithm that decides what gets normalized. Validation passed on 2026-05-11 and 2026-05-12: `npm run check`; `npm test` with 3829 tests and 21138 assertions; `npm run test:websocket` with 524 tests and 3043 assertions; and `npm run qa:design-ui -- --targets portfolio-optimizer-route,portfolio-optimizer-results-route --manage-local-app` with `reviewOutcome: PASS`.

## Context And Orientation

The portfolio optimizer bounded context lives under `src/hyperopen/portfolio/optimizer/`. The boundary document says optimizer contracts own map contracts, schema versions, migration entry points, request signatures, state paths, and worker wire codecs. It also says optimizer application and domain code must not depend on `hyperopen.views.*`; views may depend on optimizer modules.

The important existing files are:

- `src/hyperopen/portfolio/optimizer/contracts.cljs`, which currently owns schema versions, state path constants, specs, migrations, request signatures, and worker wire codec functions.
- `src/hyperopen/portfolio/optimizer/infrastructure/wire.cljs`, a small public infrastructure namespace that re-exports the worker codec functions from `contracts.cljs`.
- `src/hyperopen/portfolio/optimizer/actions/common.cljs` and `src/hyperopen/portfolio/optimizer/black_litterman_actions/common.cljs`, which repeat keyword, text, and number normalization used by optimizer actions.
- `src/hyperopen/portfolio/optimizer/query_state.cljs`, which repeats non-blank query text and keyword-like normalization for optimizer URL query parameters.
- `src/hyperopen/portfolio/optimizer/application/request_builder.cljs`, which repeats text and number checks while converting draft state into an engine request.
- `src/hyperopen/portfolio/optimizer/application/current_portfolio.cljs`, which repeats text and number parsing while building current exposure snapshots from account, spot, market, and perp state.
- `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs`, which repeats text and finite-number checks while deciding whether an optimizer request can run.
- `src/hyperopen/portfolio/optimizer/application/universe_candidates.cljs` and `src/hyperopen/portfolio/optimizer/application/history_loader/instruments.cljs`, which both normalize vault addresses and parse vault-style instrument ids.
- Optimizer views under `src/hyperopen/views/portfolio/optimize/`, which repeat simple display-side finite-number and non-blank text helpers.

An instrument id is the optimizer's stable string key for a selected asset. Examples are `perp:BTC`, `perp:dex-a:SOL`, `spot:PURR/USDC`, and `vault:0xabc...`. An instrument-keyed map is any map where those instrument ids are keys. Worker messages can decode EDN-style keyword keys such as `:perp:BTC`; the wire codec must convert such keys back to strings before engine and UI code consume them.

## Plan Of Work

First, add focused tests:

- Create `test/hyperopen/portfolio/optimizer/coercion_test.cljs` to cover `non-blank-text`, `finite-number?`, `parse-number`, `parse-float-number`, `parse-percent-text`, `decimal->percent-text`, `normalize-keyword-like`, `normalize-enum`, and `normalize-id-list`. The test should prove `parse-number` is strict for whole text with `js/Number`, while `parse-float-number` accepts prefix-style values when a caller needs the old `js/parseFloat` behavior.
- Create `test/hyperopen/portfolio/optimizer/ids_test.cljs` to cover vault address normalization, vault instrument id construction, extracting vault addresses from `vault:` ids, keyword/string market type normalization, and generic instrument-id key stringification.
- Create `test/hyperopen/portfolio/optimizer/instrument_keyed_codec_test.cljs` to prove the codec keywordizes known enum strings and stringifies instrument-keyed maps by semantic key at any nesting level. Include a field such as `[:payload :future-result :weights-by-instrument]` that is not in the old manual `instrument-keyed-map-paths` vector; this is the regression test for the future footgun.

Second, run the focused RED compile:

    cd /Users/barry/projects/hyperopen
    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test

Before production modules exist, the expected failure is a missing namespace error for one of the new test requires.

Third, create `src/hyperopen/portfolio/optimizer/coercion.cljs`. It should require only `clojure.string`. Public functions should include `non-blank-text`, `finite-number?`, `parse-number`, `parse-float-number`, `parse-ms`, `positive-number?`, `parse-boolean-value`, `parse-percent-text`, `decimal->percent-text`, `normalize-keyword-like`, `normalize-enum`, and `normalize-id-list`. Existing caller semantics must be preserved: code paths that currently use `js/Number` should call `parse-number`; code paths that intentionally use `js/parseFloat` should call `parse-float-number`.

Fourth, create `src/hyperopen/portfolio/optimizer/ids.cljs`. It should require `clojure.string` and `hyperopen.portfolio.optimizer.coercion`. Public values and functions should include `vault-instrument-prefix`, `normalize-vault-address`, `vault-instrument-id`, `vault-address-from-instrument-id`, `vault-address-from-value`, `vault-instrument-id?`, `vault-instrument?`, `instrument-id-key`, `normalize-market-type`, and `normalize-instrument-id`.

Fifth, create `src/hyperopen/portfolio/optimizer/instrument_keyed_codec.cljs`. It should require `hyperopen.portfolio.optimizer.coercion` and `hyperopen.portfolio.optimizer.ids`. Public values and functions should include `enum-value-keys`, `instrument-keyed-map-keys`, `instrument-keyed-map-paths` as a deprecated compatibility value, `stringify-instrument-keyed-map`, `normalize-wire-values`, `normalize-instrument-keyed-maps`, and `normalize-worker-boundary`. `normalize-instrument-keyed-maps` should recursively traverse maps and vectors. Whenever a map entry key is in `instrument-keyed-map-keys`, and the value is a map, it should stringify that value's keys. It should also normalize Black-Litterman view `:weights` maps under `[:return-model :views]`.

Sixth, refactor callers. Update `contracts.cljs` to require the three modules, remove its private duplicate `finite-number?`, `non-blank-string?`, keyword-value, instrument-id-key, and codec implementation, and re-export the existing public codec vars and functions by delegating to `instrument-keyed-codec`. Update `wire.cljs` only if needed to re-export any new compatibility name. Update duplicated callers in action, query, readiness, request, current portfolio, universe candidate, history loader, prior data, payload, execution, tracking, and focused optimizer view files to call `coercion` and `ids`.

Seventh, run focused tests and then required gates. Start with:

    cd /Users/barry/projects/hyperopen
    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js

Then run the required repo gates:

    cd /Users/barry/projects/hyperopen
    npm run check
    npm test
    npm run test:websocket

Because optimizer view helper imports were touched, browser QA must be accounted for even though this refactor should not alter markup, styles, browser storage, or interaction flows.

## Concrete Steps

1. Add RED tests for the three new helper modules and run the compile command above. Record the missing-namespace failure in `Progress`.
2. Implement the three helper modules with pure functions only. Do not add side effects.
3. Delegate existing `contracts.cljs` and `infrastructure/wire.cljs` public APIs to the new codec and id helpers.
4. Migrate duplicated call sites in small batches. After each batch, compile the CLJS test build if errors are hard to reason about.
5. Run the focused tests and the required repository gates. Record command results in `Progress`.
6. Move this plan to `docs/exec-plans/completed/` after acceptance passes.

## Validation And Acceptance

Acceptance requires all of the following:

1. `src/hyperopen/portfolio/optimizer/coercion.cljs`, `src/hyperopen/portfolio/optimizer/ids.cljs`, and `src/hyperopen/portfolio/optimizer/instrument_keyed_codec.cljs` exist with focused tests under `test/hyperopen/portfolio/optimizer/`.
2. The new tests fail before production modules exist and pass after implementation.
3. Existing public APIs remain available through `contracts.cljs` and `infrastructure/wire.cljs`, including `enum-value-keys`, `instrument-keyed-map-paths`, `instrument-id-key`, `stringify-instrument-keyed-map`, `normalize-wire-values`, `normalize-instrument-keyed-maps`, and `normalize-worker-boundary`.
4. The worker boundary codec stringifies instrument-keyed maps by semantic key at previously unseen nested paths, not only paths listed manually in `wire.cljs` or `contracts.cljs`.
5. Actions, query state, readiness, request building, current portfolio, history loader helpers, optimizer infrastructure helpers, and focused optimizer views use shared optimizer helpers instead of repeating the same parsing and normalization functions.
6. `npm run check`, `npm test`, and `npm run test:websocket` pass.
7. Browser QA for the touched optimizer routes is either passed or explicitly accounted for with evidence and cleanup.

## Idempotence And Recovery

The refactor is additive and safe to retry. New modules are pure and contain no runtime state. If tests fail after a caller migration, compare the caller's prior parsing semantics before changing the shared helper. Use `parse-float-number` only where the old code used `js/parseFloat`; use `parse-number` where the old code used `js/Number` or expected a whole numeric string. If broad tests fail outside optimizer parsing and normalization, do not revert unrelated user changes; record the failure and determine whether this refactor caused it.

## Artifacts And Notes

Focused RED command to run after tests are added:

    cd /Users/barry/projects/hyperopen
    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test

Expected RED evidence before implementation:

    The required namespace "hyperopen.portfolio.optimizer.coercion" is not available.

## Interfaces And Dependencies

In `src/hyperopen/portfolio/optimizer/coercion.cljs`, define these public functions:

    non-blank-text
    finite-number?
    parse-number
    parse-float-number
    parse-ms
    positive-number?
    parse-boolean-value
    parse-percent-text
    decimal->percent-text
    normalize-keyword-like
    normalize-enum
    normalize-id-list

In `src/hyperopen/portfolio/optimizer/ids.cljs`, define these public values and functions:

    vault-instrument-prefix
    normalize-vault-address
    vault-instrument-id
    vault-address-from-instrument-id
    vault-address-from-value
    vault-instrument-id?
    vault-instrument?
    instrument-id-key
    normalize-market-type
    normalize-instrument-id

In `src/hyperopen/portfolio/optimizer/instrument_keyed_codec.cljs`, define these public values and functions:

    enum-value-keys
    instrument-keyed-map-keys
    instrument-keyed-map-paths
    stringify-instrument-keyed-map
    normalize-wire-values
    normalize-instrument-keyed-maps
    normalize-worker-boundary

`contracts.cljs` must require these modules and continue exporting the existing codec names for compatibility. No new third-party dependencies are needed.

## Plan Revision Notes

- 2026-05-11 / Codex: Initial active ExecPlan created after inspecting existing optimizer contracts, duplicated helpers, wire tests, and plan guardrails. The plan chooses additive focused modules with compatibility re-exports to reduce blast radius.
- 2026-05-11 / Codex: Added RED test evidence after creating focused tests for the new helper modules.
- 2026-05-11 / Codex: Added implementation and first green CLJS suite evidence. Recorded the compatibility decision for `instrument-keyed-map-paths`.
- 2026-05-11 / Codex: Added final gate evidence and completion retrospective before moving the plan to completed.
- 2026-05-12 / Codex: Added browser-QA evidence for the optimizer setup and results routes after UI helper-import files were touched.
- 2026-05-12 / Codex: Added final rerun evidence after browser-QA evidence was recorded.
