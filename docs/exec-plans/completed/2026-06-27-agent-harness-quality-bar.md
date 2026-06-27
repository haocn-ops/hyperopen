# Tighten Agent Harness Quality Bar

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This plan follows `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Hyperopen already has a repo-specific agent harness: `AGENTS.md` routes work into explicit roles, `.codex/agents/*.toml` defines the role prompts, and `tools/multi-agent/test/codex_roles.test.mjs` validates manager-facing role configuration. After reviewing a general LLM coding-quality guide, the useful change is not to add a `CLAUDE.md` file or another broad instruction layer. The useful change is to sharpen the existing AGENTS-based harness where it has gaps: worker pre-edit discipline, dependency and diff accountability, reviewer checks for speculative complexity, and debugger role boundaries.

After this work, a maintainer can run `npm run test:multi-agent` and see that the checked-in role prompts preserve those rules. The visible outcome is a narrower, more enforceable agent harness with no new top-level agent contract file.

## Context References

Public refs:
- Direct user request in this Codex thread on 2026-06-27: create an execution plan and implement specific AGENTS-based harness improvements from the prior review.

Repo artifacts:
- `/hyperopen/AGENTS.md` is the root operating contract.
- `/hyperopen/docs/MULTI_AGENT.md` describes the multi-agent workflow and role boundaries.
- `/hyperopen/.codex/agents/worker.toml`, `/hyperopen/.codex/agents/reviewer.toml`, and `/hyperopen/.codex/agents/debugger.toml` are the role prompts to tighten.
- `/hyperopen/tools/multi-agent/test/codex_roles.test.mjs` is the existing role-configuration test surface.

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-06-27 02:22Z) Reviewed the current `AGENTS.md`, `docs/MULTI_AGENT.md`, role TOML files, and role tests to locate the smallest enforceable harness change.
- [x] (2026-06-27 02:24Z) Created this active ExecPlan before editing test or role-prompt surfaces.
- [x] (2026-06-27 02:28Z) Added failing role-prompt tests that assert the new worker, reviewer, and debugger guardrails against the real checked-in role files.
- [x] (2026-06-27 02:31Z) Confirmed RED: after `npm run setup:worktree`, `npm run test:multi-agent` failed only on the new worker, reviewer, and debugger prompt assertions.
- [x] (2026-06-27 02:34Z) Patched `worker.toml`, `reviewer.toml`, and `debugger.toml` to satisfy the new tests without adding a `CLAUDE.md` or duplicate top-level contract.
- [x] (2026-06-27 02:35Z) Confirmed GREEN: `npm run test:multi-agent` passed 14 tests with 0 failures.
- [x] (2026-06-27 02:39Z) Ran required repo validation through `npm run gates`; all 33 gates passed, including `npm test` and `npm run test:websocket`.
- [x] (2026-06-27 02:40Z) Updated this ExecPlan with final evidence and prepared it to move to completed.

## Surprises & Discoveries

- Observation: The current debugger prompt grants implementation authority with language such as “Implement targeted fix,” while the root repo contract reserves `/hyperopen/src/**` edits for `worker` by default.
  Evidence: `/hyperopen/.codex/agents/debugger.toml` and `/hyperopen/AGENTS.md` have different operational boundaries.

- Observation: A fresh worktree needed the documented `node_modules` setup before Node-based multi-agent tests could run.
  Evidence: The first `npm run test:multi-agent` failed with `ERR_MODULE_NOT_FOUND` for `smol-toml` and `zod`; `npm run setup:worktree` linked `node_modules -> /Users/barry/projects/hyperopen/node_modules`, after which the tests reached the intended prompt assertions.

- Observation: `debugger` is an optional configured role rather than a manager role accepted by `loadRoleConfig`.
  Evidence: The first debugger prompt test failed with `unknown role: debugger`; the test now reads `.codex/agents/debugger.toml` directly while worker and reviewer still use `loadRoleConfig`.

## Decision Log

- Decision: Do not add `CLAUDE.md` or a new general-purpose coding guide.
  Rationale: The user explicitly wants to stick to AGENTS, and `/hyperopen/AGENTS.md` already states that it is the root operating contract.
  Date/Author: 2026-06-27 / Codex.

- Decision: Enforce the new quality bar through `tools/multi-agent/test/codex_roles.test.mjs` rather than only prose.
  Rationale: The repo already has a role-config test surface under `npm run test:multi-agent`; adding assertions there keeps prompt drift visible in normal harness validation.
  Date/Author: 2026-06-27 / Codex.

- Decision: Keep the worker, reviewer, and debugger changes role-specific.
  Rationale: The reviewed guide contains useful general practices, but Hyperopen already has more specific workflow skills, ExecPlans, browser QA, and gates. Role-specific prompt changes preserve that structure.
  Date/Author: 2026-06-27 / Codex.

## Outcomes & Retrospective

Completed on 2026-06-27. The outcome is a small prompt-and-test change that reduces ambiguity in the existing harness without adding another instruction source. Complexity decreased because the debugger role boundary is clearer, and worker/reviewer quality expectations are now test-backed through the existing multi-agent test suite.

## Context and Orientation

The files under `.codex/agents/` are TOML custom-agent definitions. The manager loads these roles through `.codex/config.toml`, and `tools/multi-agent/src/codex_roles.mjs` validates basic role identity and manager-required fields. The existing `tools/multi-agent/test/codex_roles.test.mjs` uses Node's built-in test runner and `smol-toml` indirectly through `loadRoleConfig`.

The three role prompts relevant to this work are:

- `.codex/agents/worker.toml`: implementation role. It is the only role allowed to edit `/hyperopen/src/**` by default.
- `.codex/agents/reviewer.toml`: read-only review role. It should flag correctness, regressions, security, race conditions, missing tests, and now the specific LLM failure modes the user approved.
- `.codex/agents/debugger.toml`: diagnosis role. It should capture evidence, reproduction, root cause, suspected fix surface, and verification plan, then hand implementation to `worker` when production code changes are required.

## Plan of Work

First, extend `tools/multi-agent/test/codex_roles.test.mjs` with tests that load the real repository role configs. Add a helper that resolves the repository root from the test file. Add one test for `worker` instructions, one for `reviewer` instructions, and one for `debugger` instructions. These tests should fail before prompt edits because the current prompts lack several required phrases and the debugger prompt still says to implement fixes.

Second, patch `.codex/agents/worker.toml` to require reading target files, nearby tests, and similar local implementations before edits; reusing existing helpers and patterns before new abstractions; explaining new dependencies; and keeping every changed line tied to the approved contract or cleanup caused by the change.

Third, patch `.codex/agents/reviewer.toml` to explicitly flag unearned abstractions, single-use generic layers, speculative configurability, unexplained dependencies, style drift, symptom-only fixes, and diff lines not tied to the accepted contract.

Fourth, patch `.codex/agents/debugger.toml` so it no longer claims implementation authority. It should return diagnosis, reproduction, evidence, likely fix surface, and verification plan, and explicitly hand production-code implementation to `worker`.

Finally, run targeted validation with `npm run test:multi-agent`. Because this changes code and harness files, run `npm run check`, `npm test`, and `npm run test:websocket` or record exact blockers.

## Concrete Steps

Run all commands from `/hyperopen` or the current worktree root `/Users/barry/.codex/worktrees/086f/hyperopen`.

1. Add failing tests:

        npm run test:multi-agent

   Observed before setup: FAIL with missing `smol-toml` and `zod` because the worktree had no linked `node_modules`.

        npm run setup:worktree

   Observed setup result: `[setup:worktree] linked node_modules -> /Users/barry/projects/hyperopen/node_modules`.

   Observed after setup and before prompt edits: FAIL in exactly the new role-prompt guardrail tests for worker, reviewer, and debugger.

2. Apply the role prompt edits.

3. Re-run targeted validation:

        npm run test:multi-agent

   Observed after prompt edits: PASS, 14 tests, 0 failures.

4. Run required gates:

        npm run check
        npm test
        npm run test:websocket

   Implemented with the repo aggregate:

        npm run gates

   Observed: PASS, 33/33 gates. The run included `npm test` with 4,828 tests and 26,664 assertions, `npm run test:websocket` with 546 tests and 3,133 assertions, and reported 5,532 total tests with 30,009 total assertions. `lint:docs` emitted non-blocking stale-doc advisories for `docs/QUALITY_SCORE.md` and `docs/product-specs/leaderboard-page-parity-prd.md`.

## Validation and Acceptance

Acceptance is met. `npm run test:multi-agent` proves the real checked-in role prompts include the new worker and reviewer quality-bar language and the debugger prompt no longer grants production-code implementation authority. `npm run gates` passed all required repo validation.

## Idempotence and Recovery

All edits are text changes to committed repo files. Re-running the tests is safe. If a prompt assertion is too brittle, adjust the test to check a durable concept rather than a long exact sentence. If required gates fail for pre-existing active-plan or environment reasons, record the exact failure and do not mask it as a code defect.

## Artifacts and Notes

The main artifacts are this ExecPlan, the role-prompt tests, and the prompt edits. This plan is ready to move from `docs/exec-plans/active/` to `docs/exec-plans/completed/` after this update.

## Interfaces and Dependencies

No new runtime dependencies are allowed. The tests use existing Node built-ins and the existing `loadRoleConfig(repoRoot, roleName)` interface from `tools/multi-agent/src/codex_roles.mjs`.

Change note 2026-06-27: Initial plan created to capture the direct user request and the intended AGENTS-based implementation path before code changes.

Change note 2026-06-27: Updated the plan with RED/GREEN evidence, gate results, discoveries, and final outcome before moving the plan to completed.
