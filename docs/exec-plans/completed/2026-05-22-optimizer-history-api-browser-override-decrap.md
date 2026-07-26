# DeCRAP Optimizer History API Browser Override

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is governed by `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The durable work context is the direct maintainer request from 2026-05-22: deCRAP `optimizer-history-api-browser-override` in `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs`, reported at CRAP `132.74`, complexity `14`, and coverage `0.15`.

## Purpose / Big Picture

After this change, the browser-side optimizer history API override can be understood and verified without reading a branch-heavy parser. The public behavior stays the same: when browser tests or local sessions set `globalThis.__HYPEROPEN_OPTIMIZER_HISTORY_API__`, the runtime config still accepts both kebab-case and camelCase aliases for `enabled`, `baseUrl`, `proxyPolicy`, `includeAlignedReturns`, and `fallbackToLegacy`, and it still falls back to the checked-in app config when the override is absent.

The observable outcome is a focused test that fails before the production refactor, passes after the refactor, and a CRAP report showing the named function no longer exceeds the repository threshold. No UI view or browser interaction changes are intended.

## Context References

Public refs:

- Direct user/maintainer request in this Codex thread on 2026-05-22.

Repo artifacts:

- `/hyperopen/.agents/PLANS.md` defines the required ExecPlan structure.
- `/hyperopen/docs/PLANS.md` defines the active/completed plan lifecycle.
- `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` contains the hotspot.
- `/hyperopen/test/hyperopen/runtime/effect_adapters/portfolio_optimizer_test.cljs` owns nearby runtime adapter tests.
- `/hyperopen/test/hyperopen/runtime/effect_adapters/portfolio_optimizer_history_test.cljs` already verifies API v2 dependency pass-through for history loading.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-22 12:55Z) Reviewed the hotspot source, nearby runtime adapter tests, relevant optimizer history API tests, package scripts, and ExecPlan requirements.
- [x] (2026-05-22 12:55Z) Created this active ExecPlan.
- [x] (2026-05-22 12:57Z) Added focused browser override tests for absent override, camelCase parsing, kebab-case precedence/default fallback, invalid boolean fallback behavior, and JS global object normalization.
- [x] (2026-05-22 12:57Z) Verified the RED state: `npx shadow-cljs --force-spawn compile test` failed because `normalize-optimizer-history-api-browser-override` did not exist yet.
- [x] (2026-05-22 13:01Z) Refactored the browser override parser into small data-driven helpers while preserving config merge behavior.
- [x] (2026-05-22 13:01Z) Fixed the refactor's boolean-presence bug after the new tests caught valid `false` values being dropped.
- [x] (2026-05-22 13:01Z) Ran `npx shadow-cljs --force-spawn compile test` and `node out/test.js`; the test runner passed with `3998` tests and `22002` assertions.
- [x] (2026-05-22 13:04Z) Split the remaining parser dispatcher after the first focused CRAP report showed `override-field-entry` as the new highest parser-related score at CRAP `22.39`.
- [x] (2026-05-22 13:05Z) Reran `npm test`; it passed with `3998` tests and `22002` assertions.
- [x] (2026-05-22 13:06Z) Regenerated coverage and reran the focused CRAP report. `optimizer-history-api-browser-override` is now CRAP `2.0`, complexity `2`, coverage `1.0`; the module has `crappy-functions = 0`, `project-crapload = 0.0`, and `max-crap = 12.0`.
- [x] (2026-05-22 13:06Z) Ran required repository gates. `npm test`, `npm run coverage`, and `npm run test:websocket` passed. `npm run check` is blocked by the known unrelated stale-doc review-cycle failure for `docs/product-specs/portfolio-page-parity-prd.md`.
- [x] (2026-05-22 13:06Z) Recorded validation evidence, updated the retrospective, and moved this plan to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The current hotspot is private but the repository already permits private-var tests when a small pure helper is the right seam.
  Evidence: Existing tests use `@#'namespace/private-name` in files such as `/hyperopen/test/hyperopen/funding/domain/policy_test.cljs` and `/hyperopen/test/hyperopen/funding/infrastructure/route_clients_test.cljs`.

- Observation: The existing public history effect test verifies injected API config pass-through but does not exercise the browser global override branches.
  Evidence: `/hyperopen/test/hyperopen/runtime/effect_adapters/portfolio_optimizer_history_test.cljs` redefines `*optimizer-history-api-config*` directly and leaves `globalThis.__HYPEROPEN_OPTIMIZER_HISTORY_API__` unset.

- Observation: The new tests caught a subtle refactor-only regression: boolean override parsing must key off alias presence, not the truthiness of the parsed value.
  Evidence: the first post-refactor `node out/test.js` run failed three assertions because `:include-aligned-returns? false`, `:fallback-to-legacy? false`, and fallback `:enabled? false` were omitted from the normalized map.

- Observation: The first focused CRAP report after the main refactor moved parser risk into `override-field-entry` even though the original target was fixed.
  Evidence: `bb tools/crap_report.clj --module src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs --format json --top-functions 50` reported `optimizer-history-api-browser-override` at CRAP `2.0`, but `override-field-entry` at CRAP `22.3859808830223`, complexity `6`, coverage `0.23076923076923078`. Splitting field entry handlers dropped the module `max-crap` to `12.0`.

- Observation: `npm run check` still fails on an unrelated stale-doc gate that predates this work.
  Evidence: the command reached `npm run lint:docs` and printed `[stale-doc] docs/product-specs/portfolio-page-parity-prd.md - document is stale: 92 days old, max allowed 90`. This same blocker is documented in other recent ExecPlans.

## Decision Log

- Decision: Keep the production change inside `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs`.
  Rationale: The hotspot is a small adapter-local parser. Introducing a new namespace would add ownership and dependency churn without reducing the runtime surface.
  Date/Author: 2026-05-22 / Codex

- Decision: Test the private parser directly with `@#'hyperopen.runtime.effect-adapters.portfolio-optimizer/optimizer-history-api-browser-override`.
  Rationale: The parser is pure except for reading one browser global, and direct tests make alias and fallback behavior explicit without forcing asynchronous effect setup.
  Date/Author: 2026-05-22 / Codex

- Decision: Add and test `normalize-optimizer-history-api-browser-override` as a private pure normalizer.
  Rationale: The browser global reader should stay small, and the map normalizer gives tests a deterministic seam for alias precedence, keyword normalization, and boolean defaults without asynchronous effect setup.
  Date/Author: 2026-05-22 / Codex

- Decision: Leave `docs/product-specs/portfolio-page-parity-prd.md` unchanged despite the check failure.
  Rationale: Refreshing a product PRD review date requires product review, and this refactor did not touch that document. Treating the known stale-doc gate as an unrelated blocker avoids hiding review debt with a metadata-only edit.
  Date/Author: 2026-05-22 / Codex

## Outcomes & Retrospective

Implementation completed. The browser override parser now reads the browser global in a tiny wrapper and delegates normalization to data-driven field specs plus focused field-entry helpers. The named function dropped from the maintainer-reported CRAP `132.74`, complexity `14`, coverage `0.15` to CRAP `2.0`, complexity `2`, coverage `1.0`. The focused module report has zero crappy functions and `project-crapload = 0.0`.

The change reduced overall complexity in the target parser. The original single `cond->` owned alias detection, defaulting, normalization, and merge-shape assembly. Those responsibilities are now split into small helpers where boolean field presence is intentionally separate from truthiness, which the RED/GREEN cycle proved by catching dropped `false` values before completion.

Remaining risk is limited to the unrelated repository doc-review gate. `npm run check` is still blocked by stale `docs/product-specs/portfolio-page-parity-prd.md` metadata, which was not changed because this task does not constitute product review. Browser QA is not applicable because the production change is a runtime config parser and no UI source or browser interaction flow changed.

## Context and Orientation

Hyperopen is a ClojureScript application compiled by Shadow CLJS. Runtime effect adapters live under `/hyperopen/src/hyperopen/runtime/effect_adapters/`. The target file, `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs`, builds environment maps for optimizer run, history, scenario, execution, and tracking effects. The named hotspot `optimizer-history-api-browser-override` reads `globalThis.__HYPEROPEN_OPTIMIZER_HISTORY_API__`, converts the JavaScript object into a ClojureScript map, and returns only the config keys explicitly present in the browser override.

For this plan, "alias" means the same logical config key can arrive with different spellings. The current parser accepts both kebab-case keyword keys such as `:base-url` and camelCase keys such as `:baseUrl`. "Default fallback" means that if a boolean override key is present but its value is not a boolean, the parser still emits a configured default for that specific key rather than ignoring the key. The defaults are `false` for `:enabled?`, `true` for `:include-aligned-returns?`, and `true` for `:fallback-to-legacy?`.

The current implementation is branch-heavy because a single `cond->` owns key-presence checks, alias selection, boolean fallback, and keyword normalization for every field. The intended end state is a shallow parser that delegates those concerns to small helpers and a field-spec list.

## Plan of Work

First, add focused RED tests in `/hyperopen/test/hyperopen/runtime/effect_adapters/portfolio_optimizer_test.cljs`. Create a local helper that temporarily installs `globalThis.__HYPEROPEN_OPTIMIZER_HISTORY_API__`, calls the private parser, and restores the original descriptor. Cover no override returning `nil`, camelCase override values mapping into canonical keys, kebab-case values taking precedence over camelCase aliases when both are present, and invalid boolean values using the existing per-field defaults when the key is present.

Second, refactor `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs`. Replace the repeated `cond->` branches with private helpers for alias lookup, boolean field parsing, keyword field parsing, plain field parsing, and one reducer over field specifications. The public adapter functions must keep their names and arities unchanged. `optimizer-history-api-config` must still merge the app config map with the browser override map.

Third, run the smallest relevant CLJS test command first, then the normal test command. Because the test build is generated as one Shadow target in this repository, the narrow verification is:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js

Then run `npm test` as the repository-supported test command. After coverage is regenerated, run:

    npm run coverage
    bb tools/crap_report.clj --module src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs --format json --top-functions 50

Finally, run the required gates for code changes:

    npm run check
    npm test
    npm run test:websocket

If any command fails for an unrelated pre-existing reason, record the exact failure and stop rather than claiming completion.

## Concrete Steps

From `/Users/barry/.codex/worktrees/c592/hyperopen`, edit only these source and test files unless a validation command reveals an unavoidable neighboring issue:

1. Modify `/hyperopen/test/hyperopen/runtime/effect_adapters/portfolio_optimizer_test.cljs` to add direct parser tests around the browser global override.
2. Run the generated test build and expect the new tests to fail before implementation because the current parser does not expose the full desired branch assertions through the new test names.
3. Modify `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` to add the small helper functions and field spec reducer.
4. Rerun the targeted test build and expect the new tests to pass.
5. Run `npm test`, `npm run coverage`, the focused CRAP report, `npm run check`, and `npm run test:websocket`.
6. Update this ExecPlan with the test transcripts and CRAP before/after evidence, then move it to `/hyperopen/docs/exec-plans/completed/`.

## Validation and Acceptance

The work is accepted when all of the following are true:

- The new tests demonstrate the browser override parser handles absent override, camelCase aliases, kebab-case aliases, alias precedence, keyword normalization, and invalid boolean fallback defaults.
- `optimizer-history-api-browser-override` no longer exceeds the CRAP threshold in the focused report for `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs`.
- The public runtime adapter functions and dynamic vars in `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` keep their existing names and arities.
- `npm test` and `npm run test:websocket` pass from `/Users/barry/.codex/worktrees/c592/hyperopen`. `npm run check` should pass once the unrelated stale-doc review-cycle blocker in `docs/product-specs/portfolio-page-parity-prd.md` is resolved by the owning area.
- Browser QA is explicitly recorded as not applicable because this plan changes a runtime config parser and unit tests, not UI source under `/hyperopen/src/hyperopen/views/**` or browser interaction flow code.

## Idempotence and Recovery

The tests install the browser override with `js/Object.defineProperty` and restore or delete it in `finally`, so repeated test runs should not leak global state. The source refactor is behavior-preserving; if a test failure reveals an accidental behavior change, restore the parser behavior described in this plan rather than changing the test expectation. If the focused CRAP report appears stale, rerun `npm run coverage` before reading CRAP output because the report consumes the generated LCOV artifact.

## Artifacts and Notes

Baseline from the maintainer request:

- `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs:54` `optimizer-history-api-browser-override`
- CRAP `132.74`
- complexity `14`
- coverage `0.15`

Validation evidence:

RED evidence:

    npx shadow-cljs --force-spawn compile test
    ...
    Unable to resolve var: normalize-optimizer-history-api-browser-override

Focused GREEN evidence:

    npx shadow-cljs --force-spawn compile test
    [:test] Build completed. (1696 files, 53 compiled, 0 warnings, 6.66s)

    node out/test.js
    Ran 3998 tests containing 22002 assertions.
    0 failures, 0 errors.

Repository test evidence after the final split:

    npm test
    Ran 3998 tests containing 22002 assertions.
    0 failures, 0 errors.

Coverage evidence after the final split:

    npm run coverage
    Ran 3998 tests containing 22002 assertions.
    0 failures, 0 errors.
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.
    Statements   : 90.65% ( 165430/182480 )
    Branches     : 70.05% ( 36914/52690 )
    Functions    : 83.53% ( 10281/12308 )
    Lines        : 90.65% ( 165430/182480 )

Focused CRAP evidence after the final split:

    bb tools/crap_report.clj --module src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs --format json --top-functions 50
    summary.crappy-functions = 0
    summary.project-crapload = 0.0
    modules[0].max-crap = 12.0
    optimizer-history-api-browser-override = CRAP 2.0, complexity 2, coverage 1.0

Required gate evidence:

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

    npm run check
    [stale-doc] docs/product-specs/portfolio-page-parity-prd.md - document is stale: 92 days old, max allowed 90

The `npm run check` failure occurred after the earlier check subcommands shown in the terminal output passed through `lint:input-parsing`. It is an existing docs-review blocker, not a failure in the changed source or tests.

## Interfaces and Dependencies

At the end of the work, `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` must continue to expose the same public functions and dynamic vars, including:

    *optimizer-history-api-config*
    *optimizer-history-api-fetch*
    *optimizer-history-api-request-id*
    run-portfolio-optimizer-effect
    make-run-portfolio-optimizer
    load-portfolio-optimizer-history-effect
    load-portfolio-optimizer-history-discovery-effect
    run-portfolio-optimizer-pipeline-effect
    make-run-portfolio-optimizer-pipeline

The browser override parser may remain private. It must continue to read `globalThis.__HYPEROPEN_OPTIMIZER_HISTORY_API__` and return canonical ClojureScript keys suitable for merging into `*optimizer-history-api-config*`.

Revision note: Created the active plan from the direct maintainer request so implementation can proceed with TDD and focused CRAP validation.

Revision note: Completed the plan after adding RED parser coverage, refactoring the browser override parser, rerunning tests/coverage/CRAP, recording the unrelated stale-doc `npm run check` blocker, and moving the plan to completed.
