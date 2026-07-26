# Harden optimizer contract shapes

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. It is self-contained so an engineer can execute the work without relying on the conversation that produced it.

## Purpose / Big Picture

Future optimizer changes should not require reading defaults, request-builder internals, worker payload shaping, and scattered tests just to understand what a draft, engine request, or solved result must contain. This work tightens the canonical optimizer contracts so malformed nested shapes fail at the contract boundary, and it adds reusable fixture/path helpers that let tests build valid contract examples from one place. The observable result is a set of focused optimizer contract tests that fail before the schema work and pass after the contract layer becomes stricter.

## Context References

Public refs:

- Direct user request on 2026-05-14: "Create an execution plan and then implement it" for the remaining optimizer debt that "Contract shape is still too shallow."

Repo artifacts:

- `src/hyperopen/portfolio/optimizer/BOUNDARY.md` is the canonical optimizer boundary doc. It says `hyperopen.portfolio.optimizer.contracts` owns optimizer state paths, schema versions, specs, request signatures, and wire codecs.
- `docs/exec-plans/completed/2026-05-11-optimizer-contracts-hardening.md` added the first stricter contract pass.
- `docs/exec-plans/completed/2026-05-14-optimizer-contracts-facade-split.md` split the public contract facade from implementation namespaces.
- `docs/exec-plans/completed/2026-05-13-optimizer-test-browser-state-builders.md` added canonical test state helpers and is relevant precedent for fixture/path helper placement.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-14T15:18:10Z) Read the repo instructions, planning rules, current optimizer contracts facade, specs, paths, migrations, signatures, request builder, defaults, fixtures, current contract tests, optimizer boundary doc, and recent completed optimizer contract/source-of-truth plans.
- [x] (2026-05-14T15:18:10Z) Created this active ExecPlan before editing production code.
- [x] (2026-05-14T15:22:00Z) Added RED tests for nested draft, engine-request, request-signature, result-payload, contract fixture, and generated path-helper expectations.
- [x] (2026-05-14T15:23:00Z) Ran the focused RED command. Shadow CLJS compiled with expected undeclared-var warnings for new path helper functions; `node out/test.js` then failed before executing tests because this worktree had no installed `lucide` package.
- [x] (2026-05-14T15:24:00Z) Installed the locked npm dependency set with `npm ci`; npm reported 335 packages added and retained 14 audit findings from the lockfile.
- [x] (2026-05-14T15:30:00Z) Implemented stricter contract predicates, canonical path helpers, facade re-exports, and shared contract fixture builders.
- [x] (2026-05-14T15:31:00Z) Ran the focused contract test command; it passed with 15 tests, 108 assertions, 0 failures, and 0 errors.
- [x] (2026-05-14T15:40:00Z) Ran required repo gates. `npm run check`, `npm test`, and `npm run test:websocket` all passed.
- [x] (2026-05-14T15:41:00Z) Moved this ExecPlan to `docs/exec-plans/completed/` after validation evidence was recorded.

## Surprises & Discoveries

- Observation: `src/hyperopen/portfolio/optimizer/contracts/specs.cljs` already validates lifecycle statuses and solved result vector lengths, but draft and engine request checks remain mostly container-level.
  Evidence: `::draft` checks that nested values are maps or vectors, while `::engine-request` checks key presence and map/vector containers without requiring model kinds, aligned history fields, warnings, or instrument identifiers.

- Observation: The existing test fixture layer already has canonical app-state path helpers, but contract tests still build local samples by hand.
  Evidence: `test/hyperopen/portfolio/optimizer/fixtures.cljs` exposes `optimizer-path`, `optimizer-ui-path`, `get-optimizer-in`, and `assoc-optimizer-in`; `test/hyperopen/portfolio/optimizer/contracts_test.cljs` defines separate `sample-request`, `sample-draft`, and `sample-solved-result` maps.

- Observation: The focused RED run compiled far enough to prove the new path helper API is absent, but the Node test runner could not execute because npm dependencies are not installed in this worktree.
  Evidence: `npx shadow-cljs --force-spawn compile test` emitted undeclared-var warnings for `contracts/contract-path`, `contracts/optimizer-state-path`, and `contracts/optimizer-ui-state-path`; `node out/test.js --test=hyperopen.portfolio.optimizer.contracts-test` then failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`.

## Decision Log

- Decision: Keep `hyperopen.portfolio.optimizer.contracts` as the stable public facade and place stricter predicate implementation in `contracts.specs`.
  Rationale: Recent work intentionally split the facade from implementation ownership. Tightening specs in the focused namespace preserves public API names such as `::contracts/draft`, `::contracts/engine-request`, and `contracts/contract-specs`.
  Date/Author: 2026-05-14 / Codex

- Decision: Add path-generation helpers to the canonical paths namespace instead of requiring callers to assemble root vectors by hand.
  Rationale: Existing constants remain useful for common fields, but a catalog plus helper function gives tests and future code a canonical way to append dynamic segments without hardcoding `[:portfolio :optimizer]` or `[:portfolio-ui :optimizer]`.
  Date/Author: 2026-05-14 / Codex

- Decision: Add contract fixture builders in the test contract fixture namespace, backed by current optimizer fixtures and request builders.
  Rationale: The goal is contract clarity, not another production runtime dependency. Test fixtures can generate valid drafts, engine requests, request signatures, solved results, and invalid variants from canonical sources without shipping fixture builders in application code.
  Date/Author: 2026-05-14 / Codex

## Outcomes & Retrospective

Complete. The optimizer contract layer now validates the nested shapes future agents were previously forced to infer from defaults, request-builder behavior, and tests. Draft contracts validate instrument entries, objective/return/risk model kinds, constraints, execution assumptions, and metadata. Engine-request contracts validate request-builder output more deeply, including aligned history, model maps, warnings, and current portfolio containers. Request signatures now require their nested request to satisfy the engine-request contract before signature equality is checked. Solved result payloads now reject non-finite weight vectors, malformed optional sections such as warnings/frontier/rebalance rows, and instrument-keyed maps missing finite values for every solved instrument.

The implementation reduced contract-comprehension complexity by putting the nested shape rules in `contracts.specs` and by adding generated path helpers plus shared contract fixtures. It increases the spec namespace size, but the extra code is boundary-specific predicate code that replaces implicit knowledge spread across defaults, request builders, payload builders, and local test maps.

## Context and Orientation

The portfolio optimizer code lives under `src/hyperopen/portfolio/optimizer/`. In this plan, a "contract" means a named map shape at a boundary: a persisted or in-memory optimizer draft, an engine request produced by `application.request-builder/build-engine-request`, a request signature produced by `contracts/build-request-signature`, a saved scenario record, a tracking record, a worker message envelope, or an optimizer result payload. A "fixture" means test data that is deliberately shaped like a real contract so tests do not have to rediscover the nested fields by reading production defaults and builders.

The public contract facade is `src/hyperopen/portfolio/optimizer/contracts.cljs`. It re-exports specs such as `::contracts/draft`, schema versions, path constants, request signature helpers, migration helpers, and worker wire helpers from focused namespaces under `src/hyperopen/portfolio/optimizer/contracts/`.

The current spec implementation is `src/hyperopen/portfolio/optimizer/contracts/specs.cljs`. That is the correct place for stricter nested validation. Specs must remain open to extension keys, because result payloads and diagnostics can carry additional fields, but they should reject wrong core shapes such as a draft objective without a known `:kind`, a request history missing aligned history collections, a solved result with non-finite weights, or an instrument entry without an `:instrument-id`.

The canonical path constants live in `src/hyperopen/portfolio/optimizer/contracts/paths.cljs`. Many common paths are already named, such as `draft-return-model-path` and `last-successful-run-result-path`. This work should add helper functions and a path catalog so tests and dynamic callers can derive paths from canonical roots or named path ids.

The reusable optimizer test fixtures live in `test/hyperopen/portfolio/optimizer/fixtures.cljs`. The persisted-v1 contract examples live in `test/hyperopen/portfolio/optimizer/contract_fixtures.cljs`. This plan will extend `contract_fixtures.cljs` with valid contract builders and map-variant helpers while keeping the existing persisted fixtures available.

## Plan of Work

First, update `test/hyperopen/portfolio/optimizer/contracts_test.cljs` and `test/hyperopen/portfolio/optimizer/contract_fixtures.cljs` in a RED step. Contract tests should use generated fixture builders rather than local hand-written sample maps for the new checks. The new assertions should prove that valid generated draft, engine request, request signature, and solved result fixtures validate successfully, while malformed nested variants fail. The tests should also prove that canonical path helper functions produce `[:portfolio :optimizer ...]` and `[:portfolio-ui :optimizer ...]` paths without callers spelling the roots directly.

Second, run the focused contract test command from `/Users/barry/.codex/worktrees/9284/hyperopen`:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.contracts-test

Expected before implementation: the compile or test run fails because the new fixture/path helper functions do not exist and the current specs accept several malformed nested maps.

Third, update `src/hyperopen/portfolio/optimizer/contracts/paths.cljs` with a small path catalog and helper functions. Preserve every existing path var. Add helpers that append dynamic segments to the canonical optimizer roots and named paths. Re-export these new helpers from `src/hyperopen/portfolio/optimizer/contracts.cljs`.

Fourth, update `src/hyperopen/portfolio/optimizer/contracts/specs.cljs`. Keep `clojure.spec.alpha` as the only schema library. Add focused predicates for instrument maps, model maps, constraints, execution assumptions, aligned history, engine requests, request signatures, solved result numeric vectors, instrument-keyed maps, and optional diagnostics/result sections. Do not require every possible optional field, but require the fields that downstream request builder, engine, result UI, and tracking consumers assume are present.

Fifth, rerun the focused contract test command. If it passes, run the required code-change gates:

    npm run check
    npm test
    npm run test:websocket

No Browser MCP or Playwright browser QA is expected because this change does not touch UI code or browser interaction flows.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/9284/hyperopen`.

Use `apply_patch` for manual edits. Keep changes scoped to:

- `docs/exec-plans/active/2026-05-14-optimizer-contract-shape-hardening.md`
- `src/hyperopen/portfolio/optimizer/contracts.cljs`
- `src/hyperopen/portfolio/optimizer/contracts/paths.cljs`
- `src/hyperopen/portfolio/optimizer/contracts/specs.cljs`
- `test/hyperopen/portfolio/optimizer/contract_fixtures.cljs`
- `test/hyperopen/portfolio/optimizer/contracts_test.cljs`

After the RED test is observed, implement the minimal production code needed to satisfy it. After each meaningful milestone, update this ExecPlan's `Progress`, `Surprises & Discoveries`, `Decision Log`, `Outcomes & Retrospective`, or validation notes.

## Validation and Acceptance

Acceptance requires all of the following:

1. `::contracts/draft` accepts canonical generated drafts and rejects malformed nested objective, return-model, risk-model, universe, constraint, execution-assumption, and metadata shapes.
2. `::contracts/engine-request` accepts the canonical request built by `request-builder/build-engine-request` and rejects malformed request history, model, warning, and universe shapes.
3. `::contracts/request-signature` still verifies that `:input-signature` matches the request and now rejects signatures whose nested request is not a valid engine request.
4. `::contracts/result-payload` accepts the canonical solved result fixture and rejects solved results with non-finite vectors or missing instrument-keyed values.
5. Contract tests use shared fixture builders from `test/hyperopen/portfolio/optimizer/contract_fixtures.cljs` rather than inventing new local contract maps for the new checks.
6. New canonical path helpers are exposed through `hyperopen.portfolio.optimizer.contracts` and prove callers can generate dynamic paths without spelling optimizer root vectors.
7. `npm run check`, `npm test`, and `npm run test:websocket` pass.

## Idempotence and Recovery

The implementation is additive and safe to retry. It does not change persisted schema versions, migrations, storage keys, route paths, worker message types, optimizer math, or browser behavior. If a stricter spec breaks an existing valid fixture, first decide whether the fixture is missing a field the contract truly requires or whether the new predicate became too narrow. Preserve public facade names and existing path constants. If broad validation fails outside optimizer contracts, inspect whether this change caused the failure before editing unrelated files.

## Artifacts and Notes

Validation evidence will be recorded here as commands run.

RED transcript summary:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.contracts-test
    Generated test/test_runner_generated.cljs with 642 namespaces.
    [:test] Build completed. (1678 files, 1677 compiled, 5 warnings, 19.65s)
    Warnings: undeclared vars for contracts/contract-path, contracts/optimizer-state-path, and contracts/optimizer-ui-state-path.
    Node test runner failed before test execution because lucide was not installed in node_modules.

Focused GREEN transcript summary:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.contracts-test
    Generated test/test_runner_generated.cljs with 642 namespaces.
    [:test] Build completed. (1678 files, 1677 compiled, 0 warnings, 26.97s)
    Ran 15 tests containing 108 assertions.
    0 failures, 0 errors.

Required gate summary:

    npm run check
    Passed. The command completed all guardrails and compiled app, portfolio, portfolio-worker, portfolio-optimizer-worker, vault-detail-worker, and test targets with 0 warnings.

    npm test
    Ran 3903 tests containing 21513 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

## Interfaces and Dependencies

The public facade `hyperopen.portfolio.optimizer.contracts` must continue to expose all existing names and spec aliases. New public helper names planned by this ExecPlan are:

- `contracts/path-catalog`, a map from stable path ids such as `:optimizer/draft` and `:optimizer-ui/results-tab` to canonical path vectors.
- `contracts/contract-path`, a function that takes a path id and optional dynamic segments and returns the canonical path vector.
- `contracts/optimizer-state-path`, a function that appends dynamic segments to `contracts/optimizer-path`.
- `contracts/optimizer-ui-state-path`, a function that appends dynamic segments to `contracts/optimizer-ui-path`.

The test fixture namespace `hyperopen.portfolio.optimizer.contract-fixtures` should expose:

- `valid-draft`
- `valid-engine-request`
- `valid-request-signature`
- `valid-solved-result`
- `assoc-contract-in`
- `dissoc-contract-in`

## Plan Revision Notes

- 2026-05-14 / Codex: Initial active ExecPlan created after inspecting the current optimizer contract layer, tests, fixtures, boundary doc, and recent completed optimizer debt plans.
- 2026-05-14 / Codex: Added RED contract tests and recorded the first focused failure. The failure shows the new path helper API is missing and that this worktree needs `npm ci` before Node can execute the generated test bundle.
- 2026-05-14 / Codex: Implemented the path helpers, stricter contract predicates, facade re-exports, and shared generated contract fixtures. Focused contract validation passed.
- 2026-05-14 / Codex: Added required gate evidence after `npm run check`, `npm test`, and `npm run test:websocket` passed.
- 2026-05-14 / Codex: Moved the plan from active to completed after acceptance criteria and required gates passed.
