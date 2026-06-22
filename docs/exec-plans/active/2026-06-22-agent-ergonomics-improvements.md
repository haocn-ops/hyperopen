# Agent Ergonomics: Surface Silent Failures and De-Brittle the Feedback Loop

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

A codebase audit identified that the single systemic theme working against coding agents in this repository is **silent failure**: the runtime does correct work but discards or hides the signal an agent needs to reason about cause and effect. This ExecPlan implements the four highest impact-to-effort fixes from that audit (ranked #1, #3, #4, #7):

1. **#1 — Capture swallowed nexus dispatch errors.** `nexus.core/dispatch` folds every handler exception, unknown-effect keyword, and malformed effect into a returned `{:results :errors}` map, but every dispatch call site discards it, so a thrown handler or a typo'd `:effects/*` keyword becomes an invisible no-op in both dev and release. Register one `:after-dispatch` interceptor that records errors to an always-on bounded ring buffer, emits telemetry, and fails loud in dev.
2. **#3 — Worktree dependency bootstrap.** A fresh git worktree has no `node_modules` and `shadow-cljs` is local-only, so every required gate fails with an opaque error that masquerades as a code defect. Add a `setup:worktree` bootstrap script wired as a `pretest`/`precheck` guard and document the prerequisite in the operating contract.
3. **#4 — Non-short-circuiting gate aggregator.** `npm run check` is a 29-link `&&` chain that short-circuits, so an agent sees only the first failing gate. Expand `tools/run_gates_summary.mjs` to run every gate without breaking and print a PASS/FAIL matrix with copy-pasteable rerun commands.
4. **#7 — Stale-doc gate fix and contract-doc governance.** `npm run check` currently red-fails on an unrelated 92-day-old doc clock ahead of every compile gate. Re-stamp the stale doc, make the staleness check advisory (non-blocking) so a doc clock can never block compiles/tests, and bring the AGENTS.md MUST-follow contract guides under doc governance.

A developer can verify the result by: dispatching a throwing/unknown action and seeing it recorded and logged instead of vanishing; running `npm run setup:worktree` in a fresh worktree and getting usable `node_modules`; running `npm run gates` and seeing the full PASS/FAIL matrix even when an early gate fails; and running `npm run lint:docs` and `npm test` green.

## Context References

Public refs:

- Direct user request on 2026-06-22 to create an ExecPlan and implement audit findings #1, #3, #4, and #7.

Repo artifacts:

- `/hyperopen/ARCHITECTURE.md` — single-boundary error-normalization and idempotent-bootstrap rules that this work reinforces.
- `/hyperopen/AGENTS.md` — operating contract whose Validation section gains the worktree prerequisite.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` — ExecPlan lifecycle and writing contract.

## Progress

- [x] #1 Add `hyperopen.runtime.dispatch-errors` (pure summarizer + always-on bounded ring buffer + idempotent interceptor install) and thread `register-interceptors!` through the runtime registration deps.
- [x] #1 Surface recent dispatch errors in the console_preload QA snapshot and debug API.
- [x] #1 Unit tests for the summarizer, ring buffer, interceptor behavior, and install idempotency.
- [x] #3 Add `tools/worktree/ensure_node_modules.mjs` + `setup:worktree`/`pretest`/`precheck` wiring + AGENTS.md prerequisite line + tools.md promotion.
- [x] #4 Expand `tools/run_gates_summary.mjs` to run all derived check segments without short-circuiting and print rerun commands.
- [x] #7 Re-stamp stale governed docs and make the staleness check advisory (non-blocking) in `dev/check-docs`.
- [x] #7 Govern the AGENTS.md MUST-follow contract guides (front matter + governed set + required AGENTS links) and add tests.
- [ ] #7 follow-up: bring the seven `src/**/BOUNDARY.md` source-of-truth maps under governance (needs front matter on six of them plus extending `dev/check-docs` to scan `src/**`); deferred to avoid introducing blocking broken-link findings in this change.
- [ ] #8 tie-in follow-up: route the dispatch-error ring buffer through an always-on telemetry/critical tier and expose it via a window accessor outside the `goog.DEBUG` gate so production has the trail too.

## Surprises & Discoveries

- Observation: `nexus` is a Maven dependency (`no.cjohansen/nexus`), not vendored source, so #1 must register an interceptor in our code rather than edit nexus.
  Evidence: `shadow-cljs.edn` / `deps.edn` declare `no.cjohansen/nexus "2025.07.1"`; the API is `nexus.registry/register-interceptor!`.
- Observation: `nexus.registry/register-interceptor!` appends (does not replace), so install must be idempotent across dev hot-reload.
  Evidence: `register-interceptor!` does `(swap! !registry update :nexus/interceptors (fnil conj []) interceptor)`; `register-runtime!` re-runs on reload.
- Observation: the only live `dev.check-docs` failure is one stale doc; the four contract guides have no internal markdown links, so governing them carries no broken-link risk.
  Evidence: `bb -m dev.check-docs` reports only `[stale-doc] docs/FRONTEND.md`; link grep of the four guides is empty.

## Decision Log

- Decision: Record dispatch errors to an always-on ring buffer but keep the loud `console.error` dev-gated; do not synchronously rethrow.
  Rationale: A synchronous throw inside an `:after-dispatch` interceptor is re-caught by nexus and re-added to `:errors`, so it cannot escape; `console.error` is the loud-in-dev signal and the buffer is the always-on artifact (the production window accessor is deferred to the #8 tie-in).
  Date/Author: 2026-06-22 / platform
- Decision: Make `:stale-doc` advisory in `dev.check-docs` rather than honoring per-doc cycles beyond 90 days.
  Rationale: The core footgun is a doc clock blocking unrelated compiles; demoting staleness to a non-blocking advisory removes that while preserving the quarterly-review signal. The `min`-90 cap semantics are left unchanged to minimize behavior drift.
  Date/Author: 2026-06-22 / platform
- Decision: Govern the four AGENTS.md MUST-follow guides now; defer the `src/**/BOUNDARY.md` maps.
  Rationale: The guides are link-clean and low-risk; the BOUNDARY maps need front matter on six files and a new `src/**` scan, which risks introducing blocking findings and is better done as a focused follow-up.
  Date/Author: 2026-06-22 / platform

## Outcomes & Retrospective

Implemented all four findings. Changed files, commands, and validation results are recorded in the session summary and below in the Validation section. Two genuine follow-ups remain unchecked in Progress (BOUNDARY-map governance and the #8 production-observability tie-in); this plan stays active until at least one is scheduled or moved to a dedicated plan.

## Validation and Acceptance

- `npm run check` passes (notably `lint:docs`, `lint:test`, `lint:namespace-sizes`, and the cljs compiles for the new namespace).
- `npm test` passes, including the new `hyperopen.runtime.dispatch-errors` suite and the updated `dev.check-docs` advisory tests.
- `npm run test:websocket` passes.
- `npm run gates` prints a full PASS/FAIL matrix (no short-circuit) with rerun commands.
- `npm run setup:worktree` reports usable `node_modules` (links from the main checkout in a fresh worktree).
