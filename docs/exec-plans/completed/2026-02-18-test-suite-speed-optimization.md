# Test Suite Speed Optimization Without Coverage Loss

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, developers and CI should run the same quality gates faster while preserving current test and lint coverage expectations. A contributor should still run the required commands (`npm run check`, `npm test`, `npm run test:websocket`) and get the same pass/fail guarantees, but with less redundant work and less fixed sleep time inside asynchronous tests. Success is observable as reduced wall-clock time for the required gates and no reduction in test assertions or lint rule enforcement.

## Progress

- [x] (2026-02-18 15:34Z) Captured baseline timings and bottlenecks (lint cost, duplicated websocket runs, async sleeps) and prepared implementation plan.
- [x] (2026-02-18 15:34Z) Created ExecPlan in active plans directory.
- [x] (2026-02-18 15:38Z) Replaced slow character-by-character hiccup lint scanning with parser-based checks and candidate file filtering in `/hyperopen/dev/hiccup_lint.clj`; added combined lint entrypoint `/hyperopen/dev/check_hiccup_attrs.clj` and wired `check` to `lint:hiccup`.
- [x] (2026-02-18 15:39Z) Removed duplicate websocket suite execution by scoping Shadow `:test` to non-websocket namespaces and `:ws-test` to websocket namespaces only in `/hyperopen/shadow-cljs.edn`.
- [x] (2026-02-18 15:40Z) Reduced fixed websocket async waits by replacing non-essential 20–30ms waits with 0ms event-loop waits and keeping a minimal 10ms coalescing wait where required for determinism.
- [x] (2026-02-18 15:41Z) Ran required validation gates and captured post-change timings in `/hyperopen/tmp/test-audit/after/*.log`.
- [x] (2026-02-18 15:41Z) Moved this plan file from `/hyperopen/docs/exec-plans/active/` to `/hyperopen/docs/exec-plans/completed/` after validation passed.

## Surprises & Discoveries

- Observation: `npm run check` is dominated by two babashka lint scripts (`lint:class-attrs`, `lint:style-keys`) that each take ~43–45 seconds locally.
  Evidence: baseline timing logs in `/hyperopen/tmp/test-audit/check-breakdown/*.log`.
- Observation: `npm test` currently runs websocket namespaces that are also run by `npm run test:websocket`, causing duplicate runtime in CI and local full-gate runs.
  Evidence: overlap list in `/hyperopen/tmp/test-audit/overlap-ns.txt`.
- Observation: Several websocket tests use explicit `js/setTimeout` waits of 20–50ms where event-loop tick waits are sufficient.
  Evidence: `/hyperopen/test/hyperopen/websocket/client_test.cljs` and `/hyperopen/test/hyperopen/websocket/application/runtime_test.cljs`.
- Observation: Shadow `:node-test` target execution is governed by build namespace selection (`:ns-regexp`), not by the hand-maintained namespace lists in `/hyperopen/test/test_runner.cljs` and `/hyperopen/test/websocket_test_runner.cljs`.
  Evidence: baseline logs ran websocket namespaces not listed in runner `run-tests` forms; post-change disjointness was achieved by editing `/hyperopen/shadow-cljs.edn`, producing `overlap_ns=0`.
- Observation: Setting websocket coalescing test delay to 0 introduced a race where assertions fired before the coalesce callback.
  Evidence: temporary failures in `market-coalescing-invariant-test`; resolved by restoring a minimal 10ms wait for that assertion.

## Decision Log

- Decision: Implement speed improvements in three slices: lint runtime path, suite de-duplication, and async wait tightening.
  Rationale: This ordering delivers largest deterministic wins first and keeps behavior risk bounded by validating after each slice.
  Date/Author: 2026-02-18 / Codex
- Decision: Preserve required validation commands exactly as documented (`npm run check`, `npm test`, `npm run test:websocket`).
  Rationale: Repository guardrails require these gates; optimization must not rely on changing required command contract.
  Date/Author: 2026-02-18 / Codex
- Decision: Use Shadow build regex partitioning (`:test` excludes websocket, `:ws-test` includes websocket only) rather than editing custom runner namespace lists.
  Rationale: This controls actual executed namespaces for `node-test` builds and guarantees disjoint required gates.
  Date/Author: 2026-02-18 / Codex
- Decision: Keep one non-zero websocket runtime delay (10ms) in coalescing test while zeroing other waits.
  Rationale: This is the minimum deterministic wait that preserves coalescing behavior without the previous 50ms penalty.
  Date/Author: 2026-02-18 / Codex

## Outcomes & Retrospective

The optimization goal was achieved with required gates still green. `npm run check` dropped from 93.67s baseline to 3.91s after changes, primarily from replacing slow hiccup lint scanning and collapsing to one hiccup lint command. `npm test` now runs non-websocket namespaces only (993 tests / 4526 assertions in this environment), and `npm run test:websocket` runs websocket-only namespaces (118 tests / 510 assertions), with measured namespace overlap reduced from 28 to 0. Websocket async tests now avoid most fixed 20–30ms waits, reducing idle delay while preserving deterministic behavior.

## Context and Orientation

The current test/lint command entrypoints are defined in `/hyperopen/package.json`. The required lint checks for Hiccup class/style policies are implemented in `/hyperopen/dev/hiccup_lint.clj` and launched by `/hyperopen/dev/check_hiccup_attrs.clj`, `/hyperopen/dev/check_class_attrs.clj`, and `/hyperopen/dev/check_style_attr_keys.clj`. ClojureScript test partitioning is controlled by Shadow build regexes in `/hyperopen/shadow-cljs.edn` (`:test` and `:ws-test`). CI executes `npm run test:ci` and `npm run test:websocket` in `/hyperopen/.github/workflows/tests.yml`.

The goal is to keep the same rule and test behavior while reducing wasted execution time: avoid expensive lint execution paths, avoid running the same websocket namespaces twice across required gates, and avoid avoidable fixed waits in asynchronous tests.

## Plan of Work

First, update lint execution so the existing lint logic runs through a faster runtime path while keeping the same error reporting contract. This work edits `/hyperopen/dev/hiccup_lint.clj`, adds `/hyperopen/dev/check_hiccup_attrs.clj`, and updates `/hyperopen/package.json` to call the combined lint command in the `check` gate.

Second, make the `test` and `ws-test` suites disjoint by editing `/hyperopen/shadow-cljs.edn` regex selection so websocket-focused namespaces execute in the websocket gate only. Keep overall coverage intact by ensuring every namespace remains in at least one required gate.

Third, replace high-latency async waits in websocket tests with minimal deterministic waits (event-loop tick waits) in `/hyperopen/test/hyperopen/websocket/client_test.cljs` and `/hyperopen/test/hyperopen/websocket/application/runtime_test.cljs`.

Finally, run the required validation gates and collect before/after wall-clock timings. If any optimization causes instability or behavior drift, revert that sub-change and document the rollback decision in this plan.

## Concrete Steps

From `/hyperopen`:

1. Edit scripts and runner files:
   - `/hyperopen/package.json`
   - `/hyperopen/dev/hiccup_lint.clj`
   - `/hyperopen/dev/check_hiccup_attrs.clj`
   - `/hyperopen/shadow-cljs.edn`
   - `/hyperopen/test/hyperopen/websocket/client_test.cljs`
   - `/hyperopen/test/hyperopen/websocket/application/runtime_test.cljs`

2. Run required gates:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

3. Capture timing comparison:
   - `/usr/bin/time -p npm run check`
   - `/usr/bin/time -p npm test`
   - `/usr/bin/time -p npm run test:websocket`

Expected success signals:
- All three required commands pass.
- `check` runtime is materially lower than baseline.
- No duplicated websocket namespace execution between `test` and `test:websocket`.

## Validation and Acceptance

Acceptance criteria for this plan:

- Lint checks still fail when violations are introduced and still pass on current tree.
- Combined required test coverage is preserved: all prior websocket namespaces remain covered via `npm run test:websocket`, while `npm test` no longer reruns them.
- Required gates pass without flaky timing regressions.
- Measured runtime decreases compared to baseline logs recorded during audit.

## Idempotence and Recovery

All edits are source-controlled and idempotent. Re-running the commands is safe. If a specific optimization introduces behavior drift, recovery is to revert only that file-level change and keep the rest of the plan intact. Timing captures are written under `/hyperopen/tmp/test-audit/` so repeated runs do not affect source state.

## Artifacts and Notes

Baseline evidence used by this plan:

- `/hyperopen/tmp/test-audit/check.log`
- `/hyperopen/tmp/test-audit/check-breakdown/lint_class_attrs.log`
- `/hyperopen/tmp/test-audit/check-breakdown/lint_style_keys.log`
- `/hyperopen/tmp/test-audit/run_test_js.log`
- `/hyperopen/tmp/test-audit/run_ws_test_js.log`
- `/hyperopen/tmp/test-audit/overlap-ns.txt`

Post-change evidence captured by implementation:

- `/hyperopen/tmp/test-audit/after/npm_run_check.log`
- `/hyperopen/tmp/test-audit/after/npm_test.log`
- `/hyperopen/tmp/test-audit/after/npm_test_websocket.log`
- `/hyperopen/tmp/test-audit/after/main-ns.txt`
- `/hyperopen/tmp/test-audit/after/ws-ns.txt`

## Interfaces and Dependencies

The plan uses existing repository toolchains only:

- Node/npm scripts in `/hyperopen/package.json`.
- Shadow-CLJS test builds defined in `/hyperopen/shadow-cljs.edn`.
- Clojure/Babashka lint code in `/hyperopen/dev/*.clj`.

No new external services or runtime dependencies are required.

Plan revision note: 2026-02-18 15:34Z - Initial execution plan created from completed audit findings.
Plan revision note: 2026-02-18 15:41Z - Updated progress, discoveries, decisions, and outcomes after implementing lint acceleration, suite partitioning, async wait reductions, and required gate validation.
Plan revision note: 2026-02-18 15:41Z - Moved plan from active to completed after all acceptance checks passed.
