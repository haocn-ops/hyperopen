# File Size Guardrail Exceptions/Splitting Strategy (Maintainability)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, file-size guardrails will be enforceable and maintainable instead of advisory. Contributors will have one deterministic check that flags oversized namespaces, one explicit exception registry for temporary allowances, and one repeatable splitting strategy that keeps public APIs stable while reducing monolithic files.

A contributor can verify the result by running `npm run check` and seeing namespace-size guardrails enforced, with any oversized namespace either split below threshold or listed in an explicit exception registry that includes owner, rationale, and retirement plan.

## Progress

- [x] (2026-02-25 22:28Z) Reviewed `/hyperopen/.agents/PLANS.md`, `/hyperopen/ARCHITECTURE.md`, and `/hyperopen/docs/PLANS.md` for planning and complexity-boundary requirements.
- [x] (2026-02-25 22:28Z) Audited current source/test namespace sizes and guardrail debt inventory using `wc -l` across `/hyperopen/src` and `/hyperopen/test`.
- [x] (2026-02-25 22:28Z) Audited current lint/check infrastructure in `/hyperopen/package.json`, `/hyperopen/bb.edn`, and `/hyperopen/dev/check_docs.clj` to locate integration seams for automated size enforcement.
- [x] (2026-02-25 22:28Z) Authored initial ExecPlan with explicit exception policy, splitting strategy, and validation gates.
- [ ] Implement Milestone 1 (add automated namespace-size checker with failing coverage).
- [ ] Implement Milestone 2 (establish machine-readable exception registry and governance docs).
- [ ] Implement Milestone 3 (execute first splitting wave to prove strategy and reduce exception count).
- [ ] Implement Milestone 4 (wire checks into required gates and finalize ADR/documentation updates).

## Surprises & Discoveries

- Observation: architecture policy declares a size guardrail, but no automated checker currently enforces it.
  Evidence: `/hyperopen/ARCHITECTURE.md` states “new namespaces under 500 LOC,” while `/hyperopen/package.json` `check` script has no namespace-size lint step.

- Observation: current source code already has substantial oversized-namespace debt.
  Evidence: audit found 16 source `.cljs` files above 500 LOC, including `/hyperopen/src/hyperopen/account/history/actions.cljs` (654), `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs` (649), and `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs` (619).

- Observation: test code also has oversized files, so maintainability policy needs explicit test strategy and not source-only language.
  Evidence: audit found 10 test `.cljs` files above 500 LOC, including `/hyperopen/test/hyperopen/websocket/client_test.cljs` (615) and `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs` (622).

- Observation: existing debt tracker has no entries, so there is currently no structured place to record namespace-size exceptions and retirement criteria.
  Evidence: `/hyperopen/docs/exec-plans/tech-debt-tracker.md` currently contains “None recorded yet.”

- Observation: several oversized files already have bounded-context sibling modules, indicating feasible split seams with compatibility facades.
  Evidence: runtime reducer already delegates to `/hyperopen/src/hyperopen/websocket/application/runtime/connection.cljs`, `/market.cljs`, and `/subscriptions.cljs`, and chart interop already has module folders under `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/`.

## Decision Log

- Decision: Enforce namespace-size guardrails with one deterministic repository check integrated into `npm run check`.
  Rationale: A policy without automation drifts and becomes advisory instead of operational.
  Date/Author: 2026-02-25 / Codex

- Decision: Use one machine-readable exception registry for oversized namespaces, not ad hoc comments in individual files.
  Rationale: Centralized exceptions make ownership, expiry, and retirement auditable.
  Date/Author: 2026-02-25 / Codex

- Decision: Require each exception entry to include owner, maximum allowed lines, reason, retirement target, and linked execution plan/ADR.
  Rationale: Size exceptions are technical debt and must carry explicit accountability and exit criteria.
  Date/Author: 2026-02-25 / Codex

- Decision: Keep splitting strategy compatibility-first by preserving public facade namespaces while extracting bounded-context modules.
  Rationale: Guardrail work must improve maintainability without destabilizing public APIs.
  Date/Author: 2026-02-25 / Codex

- Decision: Treat runtime-core oversized namespaces as temporary exceptions first, then split with dedicated bounded-context milestones.
  Rationale: Runtime files have high correctness risk; policy can land immediately while complex splits proceed safely.
  Date/Author: 2026-02-25 / Codex

- Decision: Add ADR `0023` to codify permanent exception governance and splitting obligations.
  Rationale: This changes architecture governance, not only lint tooling.
  Date/Author: 2026-02-25 / Codex

## Outcomes & Retrospective

Planning complete; implementation not started yet. The repository now has a concrete policy and migration strategy for file-size guardrails, including automated enforcement, exception governance, and a phased split rollout.

## Context and Orientation

`/hyperopen/ARCHITECTURE.md` defines a complexity guardrail: new namespaces should remain under 500 lines unless justified. In practice, this rule is currently hard to apply consistently because there is no automated enforcement and no formal exception process.

For this plan:

- A "namespace-size guardrail" means a maximum file-length threshold (line count) for one `.cljs` namespace file.
- An "exception" means a temporary, explicit allowance for a namespace above threshold, with owner and retirement plan.
- A "splitting strategy" means a repeatable refactor pattern that moves cohesive logic into smaller modules while preserving the existing public namespace facade.

Current repository state relevant to this work:

- Guardrail policy source: `/hyperopen/ARCHITECTURE.md`.
- Required gate entrypoint: `/hyperopen/package.json` script `check`.
- Existing babashka lint pattern: `/hyperopen/dev/check_docs.clj`, `/hyperopen/dev/check_docs_test.clj`, and `bb -m ...` commands.
- Debt ledger location: `/hyperopen/docs/exec-plans/tech-debt-tracker.md`.

Current oversized namespace inventory (line-count audit):

- Source `.cljs` over 500 LOC: 16 files.
- Test `.cljs` over 500 LOC: 10 files.
- Representative examples:
  - `/hyperopen/src/hyperopen/account/history/actions.cljs` (654)
  - `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs` (649)
  - `/hyperopen/src/hyperopen/websocket/application/runtime_reducer.cljs` (619)
  - `/hyperopen/test/hyperopen/websocket/client_test.cljs` (615)
  - `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs` (622)

## Plan of Work

### Milestone 1: Add Automated Namespace-Size Checker And Failing Tests

Create one new lint module for namespace-size policy enforcement (for example `/hyperopen/dev/check_namespace_sizes.clj`) plus companion tests (for example `/hyperopen/dev/check_namespace_sizes_test.clj`).

The checker must:

- scan source and test `.cljs` files,
- apply threshold rules,
- consult exception registry entries,
- fail on missing/expired/malformed exceptions,
- fail when a file exceeds its allowed exception max,
- fail when a file remains in exceptions after dropping below threshold (to force cleanup).

This milestone should be test-first where practical so policy behavior is explicit and deterministic.

### Milestone 2: Establish Exception Registry And Governance Policy

Add a machine-readable exception registry (for example `/hyperopen/dev/namespace_size_exceptions.edn`) containing all currently oversized namespaces as initial debt entries.

Each exception entry must include at minimum:

- `:path`
- `:owner`
- `:max-lines`
- `:reason`
- `:retire-by`
- `:plan-ref` (active/completed ExecPlan path)
- `:adr-ref` (when governance-level exemption is required)

Update governance docs to define this as the canonical exception boundary. Add ADR `0023` for the policy and update architecture wording so “unless justified” points to this explicit mechanism.

### Milestone 3: Execute First Splitting Wave To Prove The Strategy

Apply the splitting strategy to a focused first wave of low-to-medium-risk oversized files while keeping public APIs stable via compatibility facades.

Wave 1 target classes:

- UI rendering monoliths (split into VM/selectors + render sections/components).
- Action orchestration monoliths (split into bounded action groups + shared effect helpers).
- Large test monoliths (split into domain-focused namespaces + shared test-support helpers).

Suggested first-wave candidates:

- `/hyperopen/src/hyperopen/views/footer_view.cljs`
- `/hyperopen/src/hyperopen/account/history/actions.cljs`
- `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs`

After each split, remove or tighten corresponding exception entries.

### Milestone 4: Wire Guardrails Into Required Gates And Finalize Documentation

Integrate the checker into `npm run check` via a dedicated script (for example `lint:namespace-sizes`). Ensure gate behavior is deterministic locally and in CI.

Finalize policy docs:

- `/hyperopen/ARCHITECTURE.md` (guardrail wording + exception process)
- `/hyperopen/docs/QUALITY_SCORE.md` (maintainability enforcement expectations)
- `/hyperopen/docs/exec-plans/tech-debt-tracker.md` (ongoing oversized-file retirement tracking)
- `/hyperopen/docs/architecture-decision-records/0023-file-size-guardrail-exception-policy.md`

## Concrete Steps

From `/hyperopen`:

1. Build checker and tests.

   - Add `/hyperopen/dev/check_namespace_sizes.clj`.
   - Add `/hyperopen/dev/check_namespace_sizes_test.clj`.
   - Add babashka entrypoint command (`bb -m dev.check-namespace-sizes`).
   - Run:
     - `bb -m dev.check-namespace-sizes-test`

   Expected outcome: checker tests pass and include failure cases for missing/expired exceptions.

2. Add exception registry and seed baseline debt.

   - Create `/hyperopen/dev/namespace_size_exceptions.edn` with current oversized files.
   - Update `/hyperopen/docs/exec-plans/tech-debt-tracker.md` with summarized retirement entries.
   - Run:
     - `bb -m dev.check-namespace-sizes`

   Expected outcome: checker passes with explicit, structured exceptions only.

3. Execute first splitting wave and shrink exception set.

   - Split first-wave files with compatibility-first boundaries.
   - Move extracted helpers into bounded-context modules and keep facade entrypoints stable.
   - Remove or reduce exception entries as files cross below threshold.
   - Run:
     - `npm test`
     - `npm run test:websocket`

   Expected outcome: behavior stays stable, targeted files shrink, and exception registry count decreases.

4. Integrate into required gates and finalize docs/ADR.

   - Update `/hyperopen/package.json` `check` script to include namespace-size lint.
   - Add ADR `0023` and architecture/quality wording updates.
   - Run required gates:
     - `npm run check`
     - `npm test`
     - `npm run test:websocket`

   Expected outcome: guardrail enforcement is mandatory in standard workflow and all required gates pass.

## Validation and Acceptance

Acceptance is complete when all conditions below are true.

1. Namespace-size policy is enforced by automated lint integrated into `npm run check`.
2. Oversized namespace exceptions are recorded in one machine-readable registry with owner and retirement metadata.
3. No oversized namespace exists without a valid exception entry.
4. Exception entries fail validation when expired, malformed, or stale relative to actual line counts.
5. First-wave split targets are reduced or restructured according to bounded-context strategy, with stable public APIs.
6. Architecture and quality documentation explicitly describe the exception/splitting governance path.
7. Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

This migration is safe to run incrementally:

- checker/tests first,
- exception registry second,
- split waves third,
- gate integration last.

If a split causes regressions, keep the new checker and exception governance intact, restore the affected facade logic in a focused rollback commit, and continue by splitting in smaller slices. Never bypass policy by disabling the checker; instead, add a time-bounded exception entry with explicit retirement criteria.

## Artifacts and Notes

Primary files expected to change:

- `/hyperopen/dev/check_namespace_sizes.clj` (new)
- `/hyperopen/dev/check_namespace_sizes_test.clj` (new)
- `/hyperopen/dev/namespace_size_exceptions.edn` (new)
- `/hyperopen/package.json`
- `/hyperopen/ARCHITECTURE.md`
- `/hyperopen/docs/QUALITY_SCORE.md`
- `/hyperopen/docs/exec-plans/tech-debt-tracker.md`
- `/hyperopen/docs/architecture-decision-records/0023-file-size-guardrail-exception-policy.md` (new)
- First-wave split targets and their related test namespaces.

Primary tests expected to change:

- `/hyperopen/dev/check_namespace_sizes_test.clj` (new)
- Targeted module test suites for each split file.
- `/hyperopen/test/test_runner.cljs` if test namespace splits add/remove files requiring explicit runner wiring.

Evidence to capture during implementation:

- Baseline oversized-file inventory before changes.
- Checker output before/after seeded exceptions.
- Exception count before/after first-wave splitting.
- Required gate outputs.

## Interfaces and Dependencies

Interfaces to preserve:

- Existing public namespace APIs for split modules (facade-first compatibility).
- Runtime and websocket contract surfaces described in `/hyperopen/ARCHITECTURE.md` and `/hyperopen/docs/RELIABILITY.md`.

Interfaces to add:

- Namespace-size checker interface callable via babashka.
- Machine-readable exception registry schema consumed by the checker.

Dependency direction constraints:

- Checker tooling lives in `dev` and must not create production runtime dependencies.
- Split refactors must preserve DDD boundary direction and avoid new cyclic dependencies.

No new external libraries are required.

Plan revision note: 2026-02-25 22:28Z - Initial ExecPlan created to operationalize file-size guardrail enforcement with explicit exception governance and a maintainability-first splitting strategy.
