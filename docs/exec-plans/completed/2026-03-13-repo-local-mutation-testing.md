# Build Repo-Local Mutation Testing

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Linked `bd` issue: `hyperopen-c22o`.

## Purpose / Big Picture

Hyperopen already has good line coverage and a repo-local CRAP hotspot tool, but neither tells us whether the tests would fail if a branch, comparator, fallback, or threshold were wrong. After this change, contributors will be able to run a repo-local mutation tool against one `src/hyperopen/**/*.cljs` module at a time, use existing `coverage/lcov.info` data to skip uncovered lines, and verify whether the main test suite, websocket suite, or both actually kill the mutant. The visible proof is a new `bb tools/mutate.clj --module <repo-path>` command that prints mutation results and writes manifests and reports under `target/mutation/**` without modifying tracked source files.

## Progress

- [x] (2026-03-13 16:44Z) Created and claimed `bd` issue `hyperopen-c22o` for the mutation-testing feature.
- [x] (2026-03-13 17:04Z) Added the Babashka mutation tool entrypoint plus helper namespaces under `/hyperopen/tools/mutate/**`.
- [x] (2026-03-13 17:04Z) Extended LCOV/build routing so the mutation tool can reuse the repo’s existing `coverage/lcov.info` mapping logic.
- [x] (2026-03-13 17:13Z) Added Babashka unit tests in `/hyperopen/dev/mutation_test.clj`.
- [x] (2026-03-13 17:16Z) Wired npm/docs surfaces and ran `npm run check`, `npm test`, `npm run test:websocket`, and `npm run coverage`.
- [x] (2026-03-13 17:21Z) Ran the smoke mutation flows, validated differential manifests, and moved this plan to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The current repository has no active ExecPlan file for this feature, so the implementation has to establish its own active plan before code changes.
  Evidence: `/hyperopen/docs/exec-plans/active/` only contained `README.md` at the start of the task.
- Observation: `edamame` provides exact row/column metadata for lists and symbols in `.cljs` source, but not for numeric or boolean literals.
  Evidence: Local `bb -e` probe on 2026-03-13 showed metadata for `(if ...)` and `if`, but `nil` metadata for literal `0` and `true`, so literal mutation has to anchor from parent form metadata.
- Observation: `npm run check` initially failed because `node_modules` was incomplete, not because of the mutation-tool changes.
  Evidence: `shadow-cljs` could not resolve `@noble/secp256k1` on 2026-03-13 even though it was already declared in `/hyperopen/package.json` and `/hyperopen/package-lock.json`; running `npm install` restored the missing package and the gate passed afterward.
- Observation: The draft websocket smoke command line in this plan did not hit any mutation site in the current `orderbook_policy.cljs`.
  Evidence: `bb tools/mutate.clj --module src/hyperopen/websocket/orderbook_policy.cljs --suite auto --lines 56` reported `Selected mutation sites: 0` on 2026-03-13, and a source+coverage probe showed the nearest covered dual-suite mutation site on line 49.

## Decision Log

- Decision: Scope v1 to local-only hotspot workflows rather than CI thresholds or repo-wide mutation scoring.
  Rationale: The main risk is compile-and-run correctness for `shadow-cljs` suites, not scoring or enforcement. Keeping v1 local reduces flake and lets the implementation focus on correctness.
  Date/Author: 2026-03-13 / Codex
- Decision: Store manifests, backups, and reports under `target/mutation/**` instead of embedding metadata in source files.
  Rationale: Hyperopen should not rewrite tracked source files just to record mutation history.
  Date/Author: 2026-03-13 / Codex
- Decision: Support both `test` and `ws-test` in v1, with `auto` suite selection derived from LCOV build hits.
  Rationale: The repo already splits validation between these suites, and mutation results would be misleading if the tool only considered one of them.
  Date/Author: 2026-03-13 / Codex
- Decision: Limit v1 mutation targets to `src/hyperopen/**/*.cljs`.
  Rationale: The repository’s product logic is predominantly ClojureScript application code, while `tools/**` and `dev/**` have different runtime expectations and are better left for a later pass.
  Date/Author: 2026-03-13 / Codex

## Outcomes & Retrospective

Implemented the repo-local mutation-testing v1 tool as planned. The tool now supports repo-local CLI parsing, `edamame`-based CLJS mutation discovery, external manifests and backups under `target/mutation/**`, sequential in-place mutation execution, LCOV-driven suite routing, text and JSON reports, and Babashka unit coverage. It also shares line-to-build routing with the CRAP tooling rather than maintaining a second LCOV path-mapping stack.

Validation passed after restoring the missing npm dependency install. `npm run test:mutation`, `npm run check`, `npm test`, `npm run test:websocket`, and `npm run coverage` all passed on 2026-03-13. The targeted smoke run `bb tools/mutate.clj --module src/hyperopen/api_wallets/domain/policy.cljs --suite test --lines 87` killed `2/2` mutants, and `git diff -- src/hyperopen/api_wallets/domain/policy.cljs src/hyperopen/websocket/orderbook_policy.cljs` was empty after the smoke runs, confirming source restoration worked. The websocket `auto` smoke proof used line 49 instead of the draft line 56 because line 56 currently has no mutation site; `bb tools/mutate.clj --module src/hyperopen/websocket/orderbook_policy.cljs --suite auto --lines 49` killed `1/1` mutant and showed `test+ws-test` selection. A temporary module under `src/hyperopen/` also verified the manifest workflow: `--update-manifest` followed by `--scan` on unchanged code reported `changed-sites: 0`, and changing only one top-level form raised the scan result to `changed-sites: 3` with `changed-form-indices #{2}`.

## Context and Orientation

Hyperopen’s existing static-analysis precedent is the repo-local CRAP tool rooted at `/hyperopen/tools/crap_report.clj` with helper namespaces under `/hyperopen/tools/crap/**` and Babashka tests in `/hyperopen/dev/crap_test.clj`. That tool already parses `coverage/lcov.info` and maps `.shadow-cljs/builds/<build>/dev/out/cljs-runtime/...` paths back to repo-relative `.cljs` files, which is the correct starting point for mutation coverage routing too.

The test runtime is not plain JVM Clojure. The main suite runs through `node tools/generate-test-runner.mjs`, `shadow-cljs compile test`, and `node out/test.js`. The websocket suite runs through `shadow-cljs compile ws-test` and `node out/ws-test.js`. Mutation execution therefore has to recompile the mutated module before every suite run and cannot rely on stale `out/*.js` artifacts.

`target/mutation/**` is reserved for this feature. The implementation must keep one manifest per repo-relative module, one backup per module for interrupted-run recovery, and timestamped report artifacts for scan/update/run commands.

## Plan of Work

First, add the tool surface. Create `/hyperopen/tools/mutate.clj` as a thin entrypoint and add helper namespaces for CLI parsing, source parsing, mutation rules, manifest storage, runner orchestration, coverage routing, and report rendering. The parser must use `edamame` with `:features #{:cljs}` and metadata keys so top-level forms and mutation sites can be tied back to exact source rows where possible.

Second, factor the LCOV build mapping. Extend the existing `/hyperopen/tools/crap/coverage.clj` helper so other tools can ask which builds covered a given repo-relative file and line. Keep the CRAP tool behavior unchanged, then build the mutation tool’s covered-versus-uncovered logic on top of those shared helpers.

Third, implement the mutation workflow. The tool must restore any stale backup for the selected module before doing other work, scan source forms for mutation sites, load any prior external manifest, compute changed top-level forms, and then either print scan/update information or run mutants sequentially. Each mutant run must write the mutated source, run the required suite command or commands, restore the original source in `finally`, and mark compile failures, test failures, or timeouts as killed.

Fourth, add validation and documentation. Create `/hyperopen/dev/mutation_test.clj` to cover CLI validation, source parsing, mutation discovery and suppression, manifest diffing, LCOV suite routing, timeout logic, backup recovery, and report formatting. Then wire package scripts and docs so contributors can discover `bb tools/mutate.clj` and `npm run test:mutation`.

## Concrete Steps

From the repository root:

1. Implement the new namespaces under `/hyperopen/tools/mutate/**` plus `/hyperopen/tools/mutate.clj`.
2. Extend `/hyperopen/tools/crap/coverage.clj` with shared build-routing helpers needed by the mutation tool.
3. Add `/hyperopen/dev/mutation_test.clj`.
4. Update `/hyperopen/package.json`, `/hyperopen/docs/tools.md`, and `/hyperopen/docs/references/toolchain.md`.
5. Run:
   - `npm run test:mutation`
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
   - `npm run coverage`
6. Run smoke flows:
   - `bb tools/mutate.clj --module src/hyperopen/api_wallets/domain/policy.cljs --suite test --lines 87`
   - `bb tools/mutate.clj --module src/hyperopen/websocket/orderbook_policy.cljs --suite auto --lines 49`
   - `bb tools/mutate.clj --update-manifest --module <module>`
   - `bb tools/mutate.clj --scan --module <module>`

## Validation and Acceptance

Acceptance requires all repository gates to pass and the new tool to produce observable mutation behavior. After `npm run coverage`, a scan against a target module must report total mutation sites and coverage-aware counts. A single-line run against `src/hyperopen/api_wallets/domain/policy.cljs` must report at least one killed mutant and leave the source file identical to Git content after completion. A single-line run against `src/hyperopen/websocket/orderbook_policy.cljs` with `--suite auto` must show that the selected mutation requires both `test` and `ws-test` when both builds cover the line. `--update-manifest` followed by `--scan` on an unchanged file must report zero changed mutation sites.

## Idempotence and Recovery

All commands must be safe to rerun. The mutation tool itself must restore stale backups automatically on startup for the selected module, overwrite manifests atomically, and end successful runs by recompiling the original source for every suite it touched so generated artifacts match the unmutated workspace. If a mutation run is interrupted, rerunning the same command must restore the source from the backup before proceeding.

## Artifacts and Notes

Implementation artifacts are the new tool namespaces, the Babashka unit suite, package/docs updates, and `target/mutation/**` output created by smoke runs.

Observed command summaries:

- `npm run test:mutation` passed on 2026-03-13 with `7` tests, `38` assertions, and no failures.
- `npm run check` passed on 2026-03-13 after restoring `node_modules`.
- `npm test` passed on 2026-03-13 with `2363` tests and `12406` assertions.
- `npm run test:websocket` passed on 2026-03-13 with `385` tests and `2187` assertions.
- `npm run coverage` passed on 2026-03-13 and generated `coverage/lcov.info` with line coverage `89.99%`.
- `bb tools/mutate.clj --module src/hyperopen/api_wallets/domain/policy.cljs --suite test --lines 87` reported `2/2 mutants killed (100.0%)`.
- `bb tools/mutate.clj --module src/hyperopen/websocket/orderbook_policy.cljs --suite auto --lines 49` reported `1/1 mutants killed (100.0%)` with `test+ws-test`.
- `bb tools/mutate.clj --scan --module src/hyperopen/mutation_acceptance_temp.cljs --format json` reported `changed-sites: 0` after `--update-manifest`, then `changed-sites: 3` after editing only the second top-level form.

## Interfaces and Dependencies

The final implementation must expose:

- `bb tools/mutate.clj --module <repo-relative-path>` as the primary entrypoint.
- A helper API under `/hyperopen/tools/mutate/**` that covers CLI parsing, source parsing, manifest IO, coverage routing, shell execution, and report printing.
- Shared LCOV helper functions in `/hyperopen/tools/crap/coverage.clj` that let callers determine build coverage by repo-relative file and line, not only merged function coverage.

The mutation tool must depend on Babashka-compatible libraries already available in the repo toolchain, especially `edamame` for source parsing and `cheshire` for optional JSON report output.
