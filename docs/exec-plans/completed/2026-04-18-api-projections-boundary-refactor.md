# API Projections Boundary Refactor

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan follows `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`. The live `bd` issue is `hyperopen-9rzm`.

## Purpose / Big Picture

`src/hyperopen/api/projections.cljs` currently owns many unrelated projection writers in one 922-line namespace. A projection writer is a pure function that takes application state plus an API result or error and returns updated application state. The current namespace mixes market metadata, account snapshots, orders, asset selector state, funding comparison rows, leaderboard cache state, staking data, API wallet rows, and vault route data. This makes ownership unclear and keeps an oversized file exception in `dev/namespace_size_exceptions.edn`.

After this change, maintainers should be able to find projection logic by product boundary without changing callers. The public facade `hyperopen.api.projections` remains available for existing runtime adapters and compatibility code, but each public var delegates to a cohesive owner namespace under `src/hyperopen/api/projections/`. The result is observable by running tests that prove the facade vars are identical to owner vars, the old projection behavior still passes, and the namespace-size exception for the old dumping ground is retired.

## Progress

- [x] (2026-04-18 21:56 EDT) Created and claimed `bd` issue `hyperopen-9rzm`.
- [x] (2026-04-18 21:59 EDT) Mapped `src/hyperopen/api/projections.cljs`, its current tests, callers, and size-exception entries.
- [x] (2026-04-18 22:00 EDT) Drafted this active ExecPlan with proposed boundaries and validation.
- [x] (2026-04-18 22:23 EDT) Froze the approved test contract from acceptance and edge-case proposal outputs.
- [x] (2026-04-18 22:27 EDT) Added RED-phase boundary tests that require the new owner namespaces and prove the facade delegates to them.
- [x] (2026-04-18 22:28 EDT) Verified RED phase fails before production code moves: `npx shadow-cljs --force-spawn compile test` reports missing namespace `hyperopen.api.projections.api-wallets`.
- [x] (2026-04-18 22:42 EDT) Moved projection code into cohesive owner namespaces and replaced `hyperopen.api.projections` with a facade of stable public aliases.
- [x] (2026-04-18 22:42 EDT) Split the oversized API projection test file into owner-focused test namespaces.
- [x] (2026-04-18 22:43 EDT) Removed stale namespace-size exceptions for the retired oversized source/test files.
- [x] (2026-04-18 22:54 EDT) Ran focused tests and the required repository gates.
- [x] (2026-04-18 22:58 EDT) Read-only review returned no code findings; addressed its residual test coverage note by adding direct portfolio and staking owner tests.
- [x] (2026-04-18 22:58 EDT) Reran `npm run check`, `npm test`, and `npm run test:websocket` from the final file state.

## Surprises & Discoveries

- Observation: The repository already has a small extraction pattern at `src/hyperopen/api/projections/user_fees.cljs`, with `hyperopen.api.projections` re-exporting the public vars through top-level `def`s.
  Evidence: `src/hyperopen/api/projections.cljs` currently contains `def begin-user-fees-load user-fees/begin-load`, `def apply-user-fees-success user-fees/apply-success`, and `def apply-user-fees-error user-fees/apply-error`.

- Observation: The current source and test files both have namespace-size exceptions that should become stale after the split.
  Evidence: `dev/namespace_size_exceptions.edn` lists `src/hyperopen/api/projections.cljs` with `:max-lines 938` and `test/hyperopen/api/projections_test.cljs` with `:max-lines 534`.

- Observation: The RED boundary test fails for the intended reason before any source move.
  Evidence: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test` exits 1 with `The required namespace "hyperopen.api.projections.api-wallets" is not available`.

- Observation: The post-split facade and owner tests are now included by both main and websocket test runners, including direct portfolio and staking owner tests.
  Evidence: `npm test` ran `hyperopen.api.projections.*-test` namespaces and passed 3295 tests / 17868 assertions; `npm run test:websocket` ran the same projection tests through the websocket bridge and passed 449 tests / 2701 assertions.

- Observation: The final read-only review found no code defects.
  Evidence: Reviewer `019da391-922e-7eb0-a2ab-a79f7ecd96a6` reported no correctness, regression, security, race/stale-write, or namespace-boundary findings.

## Decision Log

- Decision: Preserve `hyperopen.api.projections` as the public compatibility facade instead of rewriting all existing callers in this change.
  Rationale: Runtime adapters, startup collaborators, websocket refresh code, and compatibility helpers already depend on the facade. Keeping the public API stable reduces behavior risk and lets this refactor focus on ownership boundaries.
  Date/Author: 2026-04-18 / Codex

- Decision: Split by product/state ownership, not by generic CRUD operation.
  Rationale: The issue is unclear ownership across asset selector, leaderboard, staking, wallets, spot/perp, and vault surfaces. Product-owned namespaces make future changes discoverable and avoid a second dumping ground of generic `apply-*` helpers.
  Date/Author: 2026-04-18 / Codex

- Decision: Use facade identity tests as the RED-phase contract.
  Rationale: This is behavior-preserving refactoring. Tests that require new namespaces and assert `(identical? owner/var projections/var)` fail before implementation because the owner namespaces do not exist, then pass only when the facade delegates directly rather than wrapping behavior.
  Date/Author: 2026-04-18 / Codex

- Decision: Avoid a shared `common` projection namespace in the first pass.
  Rationale: The ClojureScript architecture review noted that a tiny shared helper namespace would add another dependency node and could become a new grab bag. Owner-local private helpers and direct `api-errors/normalize-error` calls keep the split clearer.
  Date/Author: 2026-04-18 / Codex

- Decision: Treat this as a maintainability and ownership refactor, not a performance optimization.
  Rationale: The issue is driven by mixed ownership and namespace-size debt. No measured runtime bottleneck, profiling baseline, or more complex data structure is required for this plan.
  Date/Author: 2026-04-19 / Codex

## Outcomes & Retrospective

The implementation reduced structural complexity by replacing one 922-line mixed source namespace with a 100-line public facade and focused owner namespaces. The largest new source owner is `src/hyperopen/api/projections/vaults.cljs` at 423 lines, below the architecture threshold. The old 534-line projection test file was split into focused owner tests, with the largest new projection test file under the threshold. The stale size exceptions for `src/hyperopen/api/projections.cljs` and `test/hyperopen/api/projections_test.cljs` were removed.

The review follow-up added direct owner tests for `portfolio.cljs` and `staking.cljs`, so the simple load/success/error paths are documented in owner-level behavior tests in addition to the facade identity contract.

## Context and Orientation

The repository root is `/hyperopen`. Work for this plan is in `/Users/barry/.codex/worktrees/cf7d/hyperopen` on branch `codex/projections-boundary-refactor`.

The current namespace `src/hyperopen/api/projections.cljs` is a pure state-update boundary used by API effects and runtime adapters. It must not perform network, browser storage, routing, or timer side effects. Its functions are called from:

- `src/hyperopen/api/compat.cljs`, which exposes compatibility dependency maps for API fetch helpers.
- `src/hyperopen/runtime/effect_adapters.cljs` and focused adapters under `src/hyperopen/runtime/effect_adapters/`.
- `src/hyperopen/startup/collaborators.cljs`.
- `src/hyperopen/websocket/user_runtime/refresh.cljs` and `src/hyperopen/websocket/user_runtime/handlers.cljs`.
- Tests under `test/hyperopen/api/`, `test/hyperopen/runtime/effect_adapters/`, and websocket coverage bridges.

The new owner namespaces should live under `src/hyperopen/api/projections/`:

- `market.cljs`: spot metadata, asset contexts, perp DEX metadata, candle snapshots, spot balances, and perp DEX clearinghouse projections.
- `orders.cljs`: open-order and user-fill projections.
- `portfolio.cljs`: portfolio summary load/success/error projections. User-fees remains in the existing `user_fees.cljs`.
- `asset_selector.cljs`: asset-selector loading and market index projection.
- `funding.cljs`: funding comparison projections.
- `leaderboard.cljs`: leaderboard load, cache hydration, success, and error projections.
- `staking.cljs`: staking validator, delegator summary, delegation, reward, and history projections.
- `api_wallets.cljs`: API wallet row/default-agent projections and stale-owner guards for default-agent writes.
- `user_abstraction.cljs`: user abstraction snapshot projection.
- `vaults.cljs`: vault list, index-cache, details, benchmark details, webdata2, equities, and activity-history projections.

At the end, `src/hyperopen/api/projections.cljs` should contain only the namespace declaration, requires for owner namespaces, and public `def` aliases that preserve the current public var names. Existing public names must not be removed or renamed.

## Plan of Work

First add a new test namespace `test/hyperopen/api/projections/boundary_test.cljs`. It should require `hyperopen.api.projections` and each planned owner namespace. It should assert that representative and complete public facade vars are `identical?` to their owner vars. This is the RED test because the new owner namespaces do not exist yet.

Then move the existing implementation into owner namespaces without changing function bodies beyond local helper names and requires. Prefer direct namespace-local helpers over a broad shared helper so the refactor does not create a new generic dumping ground. Small repeated calls to `hyperopen.api.errors/normalize-error` are acceptable. Keep API-wallet-specific parsing in `api_wallets.cljs` and vault-specific address normalization in `vaults.cljs`.

After the owner namespaces compile, reduce `src/hyperopen/api/projections.cljs` to a facade. Use direct `def` aliases, not wrapper `defn`s, so injected dependency tests that compare function identity keep working.

Then split `test/hyperopen/api/projections_test.cljs` into focused test namespaces under `test/hyperopen/api/projections/`. Keep each existing assertion with the owning namespace as the subject under test. The facade boundary test covers public delegation; owner tests cover behavior. Keep `test/hyperopen/api/projections/user_fees_test.cljs` as-is unless the test contract requires a small facade assertion.

Finally run `node tools/generate-test-runner.mjs` to refresh `test/test_runner_generated.cljs`, remove the stale size-exception entries for the old source/test files, and run validation.

## Concrete Steps

1. Add `test/hyperopen/api/projections/boundary_test.cljs` with facade identity assertions. Run from the repo root:

       npm run test:runner:generate
       npx shadow-cljs --force-spawn compile test

   Expected RED result: compilation fails because namespaces such as `hyperopen.api.projections.asset-selector`, `hyperopen.api.projections.market`, and `hyperopen.api.projections.vaults` cannot yet be found.

2. Create the new owner namespaces under `src/hyperopen/api/projections/` and move functions from `src/hyperopen/api/projections.cljs` into their owner. Keep all functions pure.

3. Replace `src/hyperopen/api/projections.cljs` with facade aliases. For example:

       (def begin-asset-selector-load asset-selector/begin-asset-selector-load)
       (def apply-vault-index-success vaults/apply-vault-index-success)

   Do this for every public function currently exported by the file.

4. Split `test/hyperopen/api/projections_test.cljs` into owner-focused tests and run:

       npm run test:runner:generate
       npx shadow-cljs --force-spawn compile test
       node out/test.js

   Expected GREEN result: all generated tests pass.

5. Remove stale entries for `src/hyperopen/api/projections.cljs` and `test/hyperopen/api/projections_test.cljs` from `dev/namespace_size_exceptions.edn`.

6. Run required gates from the repo root:

       npm run check
       npm test
       npm run test:websocket

   Expected result: all commands exit 0. If a command fails, capture the failing test/lint output in this plan, fix the smallest relevant issue, and rerun the failed command before proceeding.

## Validation and Acceptance

Acceptance requires all of the following:

The new boundary test fails before implementation because the planned owner namespaces are missing, then passes after the split. The boundary test must assert direct identity for the facade vars across every owner namespace, not just one example.

The existing projection behavior is preserved. The behavioral tests moved out of `test/hyperopen/api/projections_test.cljs` must still cover late bootstrap asset-selector behavior, funding comparison loading/error handling, leaderboard cache hydration, API wallet default-agent parsing and stale-owner guards, spot/perp/order projection paths, vault cache hydration/live race behavior, vault detail viewer/public payload separation, vault activity row vector coercion, and user abstraction stale-address guards.

`src/hyperopen/api/projections.cljs` must be below the 500-line threshold without a size exception, and each new owner namespace must also be below 500 lines. `test/hyperopen/api/projections_test.cljs` should either be removed or be below threshold without a size exception.

The required repository gates must pass: `npm run check`, `npm test`, and `npm run test:websocket`. This work is not UI-facing, so Browser MCP and Playwright browser QA are explicitly out of scope unless implementation unexpectedly changes browser flows or browser-test tooling.

## Idempotence and Recovery

The refactor is additive until the facade rewrite. If a moved owner namespace fails to compile, re-run `npx shadow-cljs --force-spawn compile test`, use the missing var or namespace from the compiler output, and either add the missing `def` alias to the facade or fix the owner namespace require.

If behavior tests fail after a move, compare the failing owner namespace against the original function in git history with `git show HEAD:src/hyperopen/api/projections.cljs`. Restore the original function body in the new owner namespace rather than changing expected behavior.

If size lint fails, do not add a new size exception for this refactor. Split the oversized owner test or source namespace further by product boundary.

If the facade identity test fails because a facade var is a wrapper function, replace that wrapper with a direct `def` alias unless a caller requires arity adaptation. If arity adaptation is discovered to be necessary, record it in the Decision Log and add a behavior test explaining why identity cannot hold for that single var.

## Artifacts and Notes

Initial measured sizes before implementation:

       922 src/hyperopen/api/projections.cljs
       534 test/hyperopen/api/projections_test.cljs
        38 src/hyperopen/api/projections/user_fees.cljs

Existing source exception entries to retire after successful split:

       src/hyperopen/api/projections.cljs
       test/hyperopen/api/projections_test.cljs

## Interfaces and Dependencies

The stable public interface is the set of public vars currently provided by `hyperopen.api.projections`:

`begin-spot-meta-load`, `apply-spot-meta-success`, `apply-spot-meta-error`, `apply-asset-contexts-success`, `apply-asset-contexts-error`, `apply-perp-dexs-success`, `apply-perp-dexs-error`, `apply-candle-snapshot-success`, `apply-candle-snapshot-error`, `apply-open-orders-success`, `apply-open-orders-error`, `apply-user-fills-success`, `apply-user-fills-error`, `begin-portfolio-load`, `apply-portfolio-success`, `apply-portfolio-error`, `begin-user-fees-load`, `apply-user-fees-success`, `apply-user-fees-error`, `begin-asset-selector-load`, `apply-asset-selector-success`, `apply-asset-selector-error`, `begin-funding-comparison-load`, `apply-funding-comparison-success`, `apply-funding-comparison-error`, `begin-leaderboard-load`, `apply-leaderboard-success`, `apply-leaderboard-cache-hydration`, `apply-leaderboard-error`, all `begin-staking-*`, `apply-staking-*-success`, and `apply-staking-*-error` vars, `clear-api-wallets-errors`, `reset-api-wallets`, `apply-api-wallets-extra-agents-success`, `apply-api-wallets-extra-agents-error`, `apply-api-wallets-default-agent-success`, `apply-api-wallets-default-agent-error`, `begin-spot-balances-load`, `apply-spot-balances-success`, `apply-spot-balances-error`, `apply-user-abstraction-snapshot`, `apply-perp-dex-clearinghouse-success`, `apply-perp-dex-clearinghouse-error`, all vault list/detail/benchmark/webdata/activity begin/success/error vars currently in the file.

The facade must continue to expose those names. New owner namespaces can expose the same names directly. No caller should be required to change for this plan to succeed.

Plan revision note, 2026-04-18 / Codex: Initial plan created from local code inspection and linked to `hyperopen-9rzm`.

Plan revision note, 2026-04-19 / Codex: Refreshed the active plan to explicitly cite both planning contracts, record that no performance baseline is required, align owner names with the requested user-abstraction and spot/perp market boundaries, and document that browser QA is out of scope for this pure API projection refactor.

Plan revision note, 2026-04-18 / Codex: Recorded implementation completion and validation evidence for the projection owner split.

Plan revision note, 2026-04-18 / Codex: Recorded read-only review outcome, added portfolio/staking owner coverage, and refreshed final validation counts.
