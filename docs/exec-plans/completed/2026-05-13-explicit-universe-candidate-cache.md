# Explicit Universe Candidate Cache

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `.agents/PLANS.md` from the repository root. That file requires a self-contained plan with current progress, discoveries, decisions, validation evidence, and recovery notes.

## Purpose / Big Picture

The portfolio optimizer's universe candidate projection currently hides mutable vault-candidate memoization in a namespace-level `defonce` atom in `src/hyperopen/portfolio/optimizer/application/universe_candidates.cljs`. That makes the projection harder to reason about and forces tests to reset global state between examples. After this change, calling `candidate-markets` without an explicit cache should be pure from the caller's perspective: the result is derived from the provided state, universe, query, and options only. Callers that need memoized vault candidate pools can pass their own cache object explicitly, which keeps the mutable behavior visible at the call site.

The behavior is observable through ClojureScript tests: a new regression test proves repeated calls without a cache rebuild the vault pool instead of consulting hidden state, and a second test proves two explicit cache objects do not share entries.

## Context References

Public refs:
- Direct user request on 2026-05-13: "Remove hidden mutable cache from universe candidate projection."

Repo artifacts:
- `AGENTS.md` instructions supplied for `/Users/barry/.codex/worktrees/652b/hyperopen`.
- `docs/PLANS.md` and `.agents/PLANS.md` define this ExecPlan contract.
- `src/hyperopen/portfolio/optimizer/BOUNDARY.md` states optimizer application code may emit pure descriptions and infrastructure/runtime code owns side effects.

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-05-13 14:51Z) Confirmed the worktree is already isolated: `.git` resolves to `/Users/barry/projects/hyperopen/.git/worktrees/hyperopen6`, common git dir is `/Users/barry/projects/hyperopen/.git`, and the checkout is detached with a clean status.
- [x] (2026-05-13 14:51Z) Installed missing Node dependencies with `npm install` so test failures are not caused by absent `node_modules`.
- [x] (2026-05-13 14:51Z) Inspected `src/hyperopen/portfolio/optimizer/application/universe_candidates.cljs`, `src/hyperopen/portfolio/optimizer/application/view_model/universe.cljs`, existing universe-candidate tests, and the optimizer boundary document.
- [x] (2026-05-13 14:56Z) Added regression tests that fail against the hidden `defonce` cache and removed reset fixtures from universe candidate tests.
- [x] (2026-05-13 14:58Z) Refactored `universe_candidates.cljs` so the default projection is pure and memoization requires an explicit cache atom passed in options.
- [x] (2026-05-13 14:58Z) Ran reader preflight with `bb -m dev.check-delimiters --changed`; it passed for 3 files.
- [x] (2026-05-13 14:59Z) Ran `npm test` after the production refactor; it passed with 3,870 tests, 21,362 assertions, 0 failures, and 0 errors.
- [x] (2026-05-13 15:00Z) Split cache-focused tests into `test/hyperopen/portfolio/optimizer/application/universe_candidates_cache_test.cljs` after `npm run check` reported `universe_candidates_test.cljs` exceeded the namespace-size guard.
- [x] (2026-05-13 15:01Z) Reran reader preflight and namespace-size checks; both passed, with `universe_candidates_test.cljs` at 247 lines and `universe_candidates_cache_test.cljs` at 341 lines.
- [x] (2026-05-13 15:02Z) Ran `npm run check`; it passed, including docs checks, namespace checks, boundary checks, tool tests, and Shadow builds for app, portfolio, workers, and test.
- [x] (2026-05-13 15:03Z) Ran final `npm test`; it passed with 3,870 tests, 21,362 assertions, 0 failures, and 0 errors.
- [x] (2026-05-13 15:03Z) Ran `npm run test:websocket`; it passed with 524 tests, 3,043 assertions, 0 failures, and 0 errors.

## Surprises & Discoveries

- Observation: `node_modules` was absent in this isolated worktree.
  Evidence: `test -d node_modules && echo node_modules-present || echo node_modules-missing` printed `node_modules-missing`; `npm install` then added 335 packages and reported 14 existing audit findings.
- Observation: Only `src/hyperopen/portfolio/optimizer/application/view_model/universe.cljs` calls `universe-candidates/candidate-markets` in production source.
  Evidence: `rg -n "candidate-markets|universe-section-model|universe-panel-model" src test -g '*.cljs'` found the production call in the universe view model and test calls in optimizer tests.
- Observation: The new regression tests catch the current hidden cache behavior.
  Evidence: `npm test` failed with `candidate-markets-does-not-cache-vault-pool-without-explicit-cache-test` and `candidate-markets-uses-explicit-vault-cache-without-sharing-between-caches-test`; both expected build count `4` and observed `2`.
- Observation: The refactored pure-default plus explicit-cache implementation satisfies the full ClojureScript test suite.
  Evidence: `npm test` passed after the production change with `Ran 3870 tests containing 21362 assertions. 0 failures, 0 errors.`
- Observation: Adding the cache regression tests to the original universe candidate test namespace crossed the namespace-size guard.
  Evidence: The first `npm run check` stopped at `lint:namespace-sizes` with `[missing-size-exception] test/hyperopen/portfolio/optimizer/application/universe_candidates_test.cljs - namespace has 581 lines`; after moving cache tests into `universe_candidates_cache_test.cljs`, `bb -m dev.check-namespace-sizes` passed.

## Decision Log

- Decision: Preserve the public `candidate-markets` function and add an optional explicit cache via the existing four-argument options map.
  Rationale: Preserving the existing public seam avoids broad call-site churn. The options map already carries projection behavior such as `:ranking`, so an explicit `:vault-candidate-cache` option keeps memoization visible without changing common callers.
  Date/Author: 2026-05-13 / Codex.
- Decision: Make the no-cache path pure and expose the vault-pool builder as `vault-candidate-pool`.
  Rationale: A named pure function gives tests and future agents a simple unit to reason about. It also means default `candidate-markets` behavior no longer depends on hidden process lifetime.
  Date/Author: 2026-05-13 / Codex.
- Decision: Split cache-specific tests into `test/hyperopen/portfolio/optimizer/application/universe_candidates_cache_test.cljs`.
  Rationale: The original universe candidate test namespace was already close to the size guard. A focused cache namespace keeps the new regression coverage together and avoids adding a size exception.
  Date/Author: 2026-05-13 / Codex.

## Outcomes & Retrospective

The implementation removed the hidden namespace-level mutable vault candidate cache from `src/hyperopen/portfolio/optimizer/application/universe_candidates.cljs`. Default universe candidate projection now derives vault candidates from the passed state on each call, while explicit memoization remains available through `{:vault-candidate-cache cache-atom}`.

The change reduces reasoning complexity in the application namespace because default projection no longer depends on process lifetime. It slightly increases API surface by exposing `vault-candidate-pool` and `cached-vault-candidate-pool`, but that surface is narrower and easier to test than hidden global mutation. The final required gates passed: `npm run check`, `npm test`, and `npm run test:websocket`.

## Context and Orientation

The universe candidate projection builds the "Manual Add" search list for the portfolio optimizer. It combines normal market rows from `state[:asset-selector :markets]` with vault rows from `state[:vaults :merged-index-rows]`. A vault row represents a Hyperliquid vault candidate, and a vault candidate is the normalized map used by the optimizer UI, with keys such as `:key`, `:market-type`, `:coin`, `:vault-address`, `:name`, `:symbol`, and `:tvl`.

The relevant production file is `src/hyperopen/portfolio/optimizer/application/universe_candidates.cljs`. Before this change it defined `vault-candidates-cache` as a namespace-level `defonce` atom, `memoized-vault-candidates` read and mutated that atom, and `candidate-vaults` called the memoized helper. The relevant tests are `test/hyperopen/portfolio/optimizer/application/universe_candidates_test.cljs`, `test/hyperopen/portfolio/optimizer/application/universe_candidates_cache_test.cljs`, and `test/hyperopen/portfolio/optimizer/application/universe_candidates_vault_ordering_test.cljs`. They no longer install cache reset fixtures.

The target design is:

- `vault-candidate-pool` is a public pure function from vault index rows to normalized, sorted vault candidate maps.
- `cached-vault-candidate-pool` is a public helper that takes an explicit cache atom and rows, reusing candidates only through that supplied atom.
- `candidate-markets` calls the cached helper only when the options map contains `:vault-candidate-cache`; otherwise it calls the pure pool builder.
- The namespace-level `defonce` atom and `reset-universe-candidates-cache!` are removed.

## Plan of Work

First, modify the universe candidate tests before production code. Remove the reset fixtures and add two regression tests. One test calls `candidate-markets` twice with the same vault rows and no cache while instrumenting `vault-row->candidate`; it should observe four builds for two rows across two calls, proving default behavior is not memoized. The second test passes two different cache atoms through `{:vault-candidate-cache cache}` and should observe one build per explicit cache, proving caches do not share hidden state.

Second, refactor `src/hyperopen/portfolio/optimizer/application/universe_candidates.cljs`. Delete the `defonce vault-candidates-cache` atom and the reset function. Rename the pure build helper to `vault-candidate-pool`. Keep the signature-based cache logic, but make it accept a `cache` argument and mutate only that supplied atom. Thread the options map from `candidate-markets` into `candidate-vaults`, and have `candidate-vaults` choose cached or pure pool construction based on `:vault-candidate-cache`.

Third, update existing cache-reuse tests so they create local cache atoms and pass them explicitly. Existing invalidation tests remain useful because they prove the explicit cache refreshes when rows change, names change, TVL changes, or relationship type changes.

Fourth, run validation. The smallest relevant signal is `npm test` because the generated ClojureScript test runner does not provide a committed single-namespace script. Required final gates for code changes are `npm run check`, `npm test`, and `npm run test:websocket`.

## Concrete Steps

Run commands from `/Users/barry/.codex/worktrees/652b/hyperopen`.

1. Add the red tests and remove reset fixtures:

    Edit `test/hyperopen/portfolio/optimizer/application/universe_candidates_test.cljs` and `test/hyperopen/portfolio/optimizer/application/universe_candidates_vault_ordering_test.cljs`.

2. Run the red test command:

    npm test

    Expected before production changes: the new no-hidden-cache test fails because the current namespace-level cache reuses the vault pool even when no cache object was passed.

3. Refactor production code:

    Edit `src/hyperopen/portfolio/optimizer/application/universe_candidates.cljs` as described in the Plan of Work.

4. Run the green test command:

    npm test

    Expected after production changes: the new tests and existing universe-candidate tests pass.

5. Run required final gates:

    npm run check
    npm test
    npm run test:websocket

    Final evidence: all three commands passed on 2026-05-13.

## Validation and Acceptance

Acceptance criteria:

1. `src/hyperopen/portfolio/optimizer/application/universe_candidates.cljs` contains no namespace-level mutable cache for vault candidates.
2. `reset-universe-candidates-cache!` is gone and universe candidate tests no longer need reset fixtures.
3. Calling `candidate-markets` without `:vault-candidate-cache` derives vault candidates from the passed state each time.
4. Calling `candidate-markets` with `{:vault-candidate-cache cache}` reuses the vault candidate pool only through that explicit cache object.
5. `npm run check`, `npm test`, and `npm run test:websocket` pass.

## Idempotence and Recovery

The code changes are local and can be repeated safely. If the test runner generation changes `test/test_runner_generated.cljs`, rerun `npm run test:runner:generate` or `npm test`; the file is generated from checked-in test files. If a validation command fails, keep the ExecPlan active, add the failure to `Surprises & Discoveries`, and fix the failing behavior before claiming completion. Avoid `git reset --hard` or checkout commands; inspect diffs and apply focused patches instead.

## Artifacts and Notes

Initial setup evidence:

    npm install
    added 335 packages, and audited 336 packages in 3s
    14 vulnerabilities (5 moderate, 9 high)

Final validation evidence:

    npm run check
    exit 0

    npm test
    Ran 3870 tests containing 21362 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

## Interfaces and Dependencies

The public optimizer candidate API remains:

    (candidate-markets state universe query)
    (candidate-markets state universe query opts)

The new explicit-cache option is:

    {:vault-candidate-cache cache-atom}

where `cache-atom` is an atom whose value is either nil or an internal map with `:rows`, `:rows-signature`, and `:candidates`. Callers should create one cache atom per lifecycle that intentionally shares memoization. Tests should create cache atoms locally inside each test, not through namespace fixtures.

Plan revision note, 2026-05-13 / Codex: Created this active ExecPlan from the direct user request, captured the chosen pure-default plus explicit-cache design, and recorded initial workspace setup evidence.

Plan revision note, 2026-05-13 / Codex: Updated progress after the RED test run, recording that the new tests fail because the current hidden namespace cache reuses vault candidates when no explicit cache was passed and shares memoization across caller-owned cache atoms.

Plan revision note, 2026-05-13 / Codex: Updated progress after the GREEN test run, recording the production refactor, delimiter preflight, and the passing `npm test` result.

Plan revision note, 2026-05-13 / Codex: Recorded the namespace-size check failure, the cache-test namespace split, all final validation results, and moved the completed ExecPlan from `active` to `completed`.
