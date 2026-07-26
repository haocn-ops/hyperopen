---
owner: platform
status: completed
source_of_truth: true
---

# Centralize Optimizer Test and Browser State Builders

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. It is self-contained so an engineer can execute the work without relying on the conversation that produced it.

## Purpose / Big Picture

Optimizer tests and Playwright specs still create app state through repeated raw paths such as `[:portfolio :optimizer ...]` and direct browser-side ClojureScript map/vector construction. That makes future optimizer state shape changes risky: a test can keep passing against a stale shape, or a browser spec can mutate the app store in a way production code no longer expects.

After this work, optimizer test fixtures expose canonical helpers that build and read optimizer state through `hyperopen.portfolio.optimizer.contracts`, and the highest-risk Playwright Black-Litterman spec seeds optimizer state through a shared browser helper. The observable outcome is that the focused fixture tests, the helper guard tests, and the migrated Playwright regression can run without each spec rebuilding the optimizer root paths by hand.

## Context References

Public refs:

- Direct user/maintainer request on 2026-05-13: "Centralize optimizer test/browser state construction."

Repo artifacts:

- `src/hyperopen/portfolio/optimizer/contracts.cljs` owns canonical optimizer state paths such as `optimizer-path`, `draft-path`, and `optimizer-ui-path`.
- `test/hyperopen/portfolio/optimizer/fixtures.cljs` already owns reusable optimizer CLJS fixture data.
- `tools/playwright/test/optimizer-black-litterman-views.spec.mjs` is the worst current browser case because it manually builds CLJS maps/vectors inside `page.evaluate` and mutates `globalThis.hyperopen.system.store`.
- `tools/optimizer/check-contract-paths.mjs` already guards production optimizer code against hardcoded root paths.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-13T22:54:55Z) Inspected optimizer contracts, fixture builders, Playwright helper conventions, and the Black-Litterman browser spec.
- [x] (2026-05-13T22:55:00Z) Captured this active ExecPlan with scope, acceptance, and validation commands.
- [x] (2026-05-13T22:57:00Z) Added failing CLJS fixture coverage for canonical optimizer state path helpers.
- [x] (2026-05-13T22:57:30Z) Added failing Node coverage for a shared Playwright optimizer state helper and focused browser-spec guard.
- [x] (2026-05-13T23:02:00Z) Implemented CLJS fixture helpers and migrated fixture tests plus the real fixture read in `contracts_test.cljs`.
- [x] (2026-05-13T23:04:00Z) Implemented the Playwright optimizer seed helper and migrated `optimizer-black-litterman-views.spec.mjs`.
- [x] (2026-05-13T23:07:00Z) Ran focused Node and Playwright validation: helper/guard tests passed, targeted Playwright editor flow passed, and the full migrated Black-Litterman Playwright spec passed.
- [x] (2026-05-13T23:08:00Z) Ran required `npm test` and `npm run test:websocket` gates successfully.
- [x] (2026-05-13T23:10:13Z) Ran required `npm run check` gate successfully against the completed-plan layout.

## Surprises & Discoveries

- Observation: A production static guard already exists for optimizer root path literals in `src/hyperopen/portfolio/optimizer` and selected runtime adapter files.
  Evidence: `package.json` contains `lint:optimizer-contract-paths` and `test:optimizer-contract-paths`, backed by `tools/optimizer/check-contract-paths.mjs`.

- Observation: The Playwright Black-Litterman spec repeats a browser-side CLJS conversion pattern instead of using a support module.
  Evidence: `tools/playwright/test/optimizer-black-litterman-views.spec.mjs` contains repeated `PersistentArrayMap`, `PersistentVector`, and `assoc_in` calls in its seed helpers.

- Observation: The fresh worktree did not have `node_modules` installed.
  Evidence: Before `npm install`, `npm ls lucide @playwright/test --depth=0` returned `(empty)`, and CLJS test execution failed resolving `lucide/dist/esm/icons/external-link.js`.

- Observation: After installing dependencies, the full CLJS test command passed with the new fixture helpers.
  Evidence: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js` reported `Ran 3888 tests containing 21442 assertions. 0 failures, 0 errors.`

- Observation: The existing local dev server occupied `127.0.0.1:8080` during Playwright validation.
  Evidence: The first Playwright command exited with `http://127.0.0.1:8080 is already used`; rerunning with `PLAYWRIGHT_REUSE_EXISTING_SERVER=true` allowed the targeted and full migrated spec checks to pass.

## Decision Log

- Decision: Limit the first browser migration to `tools/playwright/test/optimizer-black-litterman-views.spec.mjs`.
  Rationale: The user called this file the worst case, and a narrow migration creates the shared helper without forcing a risky large rewrite of every optimizer Playwright spec in one pass.
  Date/Author: 2026-05-13 / Codex

- Decision: Put browser seed primitives in `tools/playwright/support/optimizer_state.mjs`, not inside an individual spec.
  Rationale: Playwright support modules are already the repo pattern for shared browser helpers, and this makes future optimizer specs opt into the same conversion and path functions.
  Date/Author: 2026-05-13 / Codex

- Decision: Keep the existing production path guard and add a focused guard for the migrated Black-Litterman spec rather than failing all current test/browser literals at once.
  Rationale: The repository still has many legitimate existing literals. A focused guard prevents regression in the highest-risk migrated file while leaving the broader cleanup for incremental follow-up.
  Date/Author: 2026-05-13 / Codex

## Outcomes & Retrospective

Implemented the first high-value slice. `test/hyperopen/portfolio/optimizer/fixtures.cljs` now provides canonical optimizer path and state access helpers backed by `src/hyperopen/portfolio/optimizer/contracts.cljs`, and `sample-scenario-state` assembles optimizer roots through those constants. `tools/playwright/support/optimizer_state.mjs` now owns browser seed descriptors, path builders, CLJS conversion, app-store patching, market seeding, and optimizer-state reads. `tools/playwright/test/optimizer-black-litterman-views.spec.mjs` now uses that helper instead of hand-building CLJS maps/vectors or direct `assoc_in` calls. `tools/optimizer/check-contract-paths.mjs` now includes a focused guard that keeps the migrated spec helper-based.

Validation completed so far:

- `node --test tools/playwright/support/optimizer_state.test.mjs tools/optimizer/check-contract-paths.test.mjs`: 5 passed.
- `PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs --grep "Edit Views contract"`: 1 passed.
- `PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs`: 5 passed.
- `npm test`: 3888 tests, 21442 assertions, 0 failures, 0 errors.
- `npm run test:websocket`: 524 tests, 3043 assertions, 0 failures, 0 errors.
- `npm run check`: completed successfully, including the new Playwright support unit test script, optimizer path guards, docs lint, namespace checks, release/style/dev-server tests, and CLJS app/portfolio/worker/test compilation.

The change reduces complexity in the migrated browser spec by replacing repeated low-level CLJS construction with plain JavaScript seed data and shared path helpers. Overall repository complexity increases slightly by adding a helper module and guard test, but that centralizes the tricky browser conversion behavior in one reviewed place instead of repeating it across specs. Broader cleanup remains: many optimizer CLJS tests and other Playwright specs still contain raw root paths and can migrate incrementally onto these helpers.

## Context and Orientation

The optimizer app state lives under two roots. The business state root is `[:portfolio :optimizer]`; it contains the draft, run state, history data, scenario index, last successful run, tracking state, and execution modal state. The UI state root is `[:portfolio-ui :optimizer]`; it contains selected tabs, filters, editor fields, overlay modes, and other presentation settings. Both roots are already defined in `src/hyperopen/portfolio/optimizer/contracts.cljs`.

CLJS unit tests use `test/hyperopen/portfolio/optimizer/fixtures.cljs` to build reusable optimizer data such as `sample-draft`, `sample-solved-result`, `sample-last-successful-run`, and `sample-scenario-state`. That file should become the test-facing builder for app state roots, so tests do not need to repeat root paths when they only want to read or override optimizer state.

Playwright specs run in a browser and can mutate the debug app store by calling `page.evaluate`. The current Black-Litterman spec builds CLJS values by hand with `globalThis.cljs.core.PersistentArrayMap.fromArray` and `PersistentVector.fromArray`. A shared helper should accept plain JavaScript seed data, convert it to CLJS maps/vectors in the page, and write it with canonical optimizer path functions. Dynamic map keys such as `"perp:BTC"` and `"BTC"` must stay strings, while fixed field names such as `"return-model"` must become Clojure keywords.

## Plan of Work

First, add CLJS fixture helper coverage in `test/hyperopen/portfolio/optimizer/fixtures_test.cljs`. The new tests should describe the desired helper API before implementation: `optimizer-path`, `optimizer-ui-path`, `get-optimizer-in`, `get-optimizer-ui-in`, `assoc-optimizer-in`, and `assoc-optimizer-ui-in`. The tests must fail until those helpers exist.

Second, implement the CLJS helpers in `test/hyperopen/portfolio/optimizer/fixtures.cljs`. Require `hyperopen.portfolio.optimizer.contracts` and build helper paths with the constants in that namespace. Update `sample-scenario-state` so it assembles the optimizer and optimizer UI roots with `assoc-in` through `contracts/optimizer-path` and `contracts/optimizer-ui-path`. Update the fixture tests and any nearby real-fixture access in `contracts_test.cljs` to use the new helpers where it avoids root-path repetition.

Third, add Node tests for the Playwright helper. The helper must export `keyword`, `stringMap`, `stateKey`, `optimizerPath`, `optimizerUiPath`, `seedOptimizerState`, `seedOptimizerMarkets`, and `readOptimizerState`. Unit tests can validate path construction and serializable seed descriptors without requiring a real browser. Extend `tools/optimizer/check-contract-paths.mjs` with a focused scanner that fails if the migrated Black-Litterman spec reintroduces direct CLJS store mutation primitives.

Fourth, implement `tools/playwright/support/optimizer_state.mjs`. The page-side conversion function should turn tagged keyword values into CLJS keywords, tagged string maps into CLJS maps with string keys, arrays into CLJS vectors, and plain objects into CLJS maps with keyword keys. `seedOptimizerState` should accept a list of `{path, value}` patches, convert paths to CLJS vectors, `assoc_in` each patch into the app store, reset the store once, and wait for idle. `readOptimizerState` should read a path and convert the result back to JavaScript for assertions.

Fifth, migrate `tools/playwright/test/optimizer-black-litterman-views.spec.mjs`. Replace local `PersistentArrayMap`, `PersistentVector`, `assoc_in`, and repeated optimizer path construction with the shared helper. Keep the same test behavior and UI assertions. Non-optimizer account-context setup can remain local if converting it would broaden the task.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/6348/hyperopen`.

1. Add failing CLJS tests:

   Run:

       npm test -- --grep optimizer.fixtures

   If the test runner does not support `--grep`, run the repository CLJS test command:

       npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

   Expected before helper implementation: failures naming unresolved fixture helper vars.

2. Add failing Node tests:

   Run:

       node --test tools/playwright/support/optimizer_state.test.mjs tools/optimizer/check-contract-paths.test.mjs

   Expected before helper implementation and browser spec migration: failures because the helper file does not exist and the Black-Litterman spec still contains direct CLJS mutation primitives.

3. Implement CLJS fixture helpers and rerun:

       npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

   Expected after implementation: the fixture helper tests pass with zero failures.

4. Implement the Playwright helper, migrate the Black-Litterman spec, and rerun:

       node --test tools/playwright/support/optimizer_state.test.mjs tools/optimizer/check-contract-paths.test.mjs

   Expected after implementation: Node tests pass and the focused guard reports no migrated-spec violations.

5. Run the smallest relevant Playwright check:

       npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs --grep "Edit Views contract"

   Expected: the migrated editor-flow regression passes.

6. Broaden Playwright validation for the changed spec:

       npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs

   Expected: all tests in that spec pass.

7. Run required gates:

       npm run check
       npm test
       npm run test:websocket

   Expected: each command exits 0. If a command fails, update this plan with the failure and either fix the issue or record the blocker.

## Validation and Acceptance

Acceptance requires all of the following:

The CLJS fixture namespace exposes canonical state path and state access helpers, proven by `sample-scenario-state-is-route-and-view-ready-test` and a new helper-focused test that read optimizer state through fixture helpers instead of raw root vectors.

The Playwright support helper has unit coverage for path construction, keyword tags, string-key map tags, and patch descriptors. The migrated Black-Litterman spec contains no direct `PersistentArrayMap`, `PersistentVector`, or `assoc_in` calls.

The Playwright Black-Litterman browser flow still passes after migration. The smallest relevant command is the editor-flow grep, and the broadened command is the full `optimizer-black-litterman-views.spec.mjs` file.

The repository gates `npm run check`, `npm test`, and `npm run test:websocket` pass after the code changes.

## Idempotence and Recovery

The helper changes are additive. Re-running the test commands is safe. If a Playwright run leaves browser processes behind, use the repository cleanup command:

    npm run browser:cleanup

Do not use destructive git commands. If migration of the full Black-Litterman spec uncovers a flaky browser assertion unrelated to state construction, keep the helper and migrate one seed helper at a time until the failure is isolated, then update `Surprises & Discoveries`.

## Artifacts and Notes

The implementation should leave a diff showing a new Playwright support helper, direct use of that helper from the Black-Litterman spec, and a focused scanner extension in `tools/optimizer/check-contract-paths.mjs`.

## Interfaces and Dependencies

In `test/hyperopen/portfolio/optimizer/fixtures.cljs`, define these CLJS functions:

    (optimizer-path & segments)
    (optimizer-ui-path & segments)
    (get-optimizer-in state path)
    (get-optimizer-ui-in state path)
    (assoc-optimizer-in state path value)
    (assoc-optimizer-ui-in state path value)

In `tools/playwright/support/optimizer_state.mjs`, define these JavaScript exports:

    keyword(name)
    stringMap(entries)
    stateKey(name)
    optimizerPath(...segments)
    optimizerUiPath(...segments)
    seedOptimizerState(page, patches, options)
    seedOptimizerMarkets(page, markets, options)
    readOptimizerState(page, path)

`keyword` creates a tagged value that becomes a CLJS keyword. `stringMap` creates a tagged value that becomes a CLJS map whose keys remain JavaScript strings. `stateKey` marks a path segment that must remain a string key instead of becoming a keyword. The two optimizer path functions are the only place browser specs should spell the optimizer root segments.

## Revision Notes

- 2026-05-13T22:55:00Z: Created the plan from the direct maintainer request and scoped the first implementation slice to CLJS fixture helpers plus the highest-risk Black-Litterman Playwright spec.
- 2026-05-13T23:05:01Z: Updated progress after implementing the fixture helpers, browser seed helper, migrated spec, and initial focused Node/CLJS validation.
- 2026-05-13T23:08:08Z: Recorded focused Playwright validation, required `npm test`, required websocket validation, and the implementation outcome before moving the plan to completed.
- 2026-05-13T23:10:13Z: Recorded the successful `npm run check` result after running it against the completed-plan layout.
