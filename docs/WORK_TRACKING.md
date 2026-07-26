---
owner: platform
status: canonical
last_reviewed: 2026-05-12
review_cycle_days: 90
source_of_truth: true
---

# Work Tracking and Session Handoff

## Purpose
Define the durable, contributor-visible places where Hyperopen work is tracked and handed off. Local-only tools may help an individual session, but they are not project memory.

## Scope and Precedence
- This document governs work tracking, dependency context, and session handoff expectations.
- Planning artifacts still follow `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.
- If guidance conflicts, task-specific user/developer instructions take precedence for the current task.

## Canonical Work Tracking Model

Public and contributor-visible work tracking:
- GitHub Issues track bugs, features, chores, follow-ups, priorities, and ownership that should survive beyond one local session.
- GitHub Pull Requests track review discussion, implementation status, validation evidence, and merge decisions.
- Docs may reference GitHub issue numbers, PR numbers, or other public work references when those are the durable context for a change.

Implementation records:
- ExecPlans under `/hyperopen/docs/exec-plans/**` are self-contained implementation artifacts for complex features, significant refactors, risky bug work, and governed multi-agent work.
- ExecPlans are not an issue tracker or backlog. They describe active or historical implementation context, decisions, progress, and validation evidence.
- Active ExecPlans should reference durable context when applicable: a GitHub issue, GitHub PR, parent ExecPlan, Improvement Plane artifact, or direct user/maintainer request captured in the plan.

Agent-harness learning:
- Improvement Plane artifacts, when present, live in committed repo paths such as `/hyperopen/improvement/**` or an equivalent governed path.
- Improvement records should use `public_refs` for durable references and `local_refs` for optional local scratch.
- `local_refs` may include Beads / `bd` identifiers only when marked non-authoritative.

Canonical docs and review history:
- Canonical docs capture durable policy, architecture, safety, and product context.
- PR review threads and review summaries are durable review history; do not hide required project context in local-only notes.

## Optional Local Scratch

Beads / `bd` may be useful for a maintainer or agent to decompose local tasks, sort dependencies, or coordinate an ephemeral session. Treat it as local scratch only.

Rules:
- Do not require Beads / `bd` to understand the repository, run CI, review a PR, continue from a fresh clone, or contribute externally.
- Do not store the only copy of a bug, follow-up, design decision, validation result, or blocker in local Beads state.
- If Beads / `bd` is referenced in an ExecPlan or Improvement Plane artifact, label it as a local scratch reference and non-authoritative.
- Do not run `bd sync`, `bd dolt push`, `bd dolt pull`, or similar remote/local tracker sync commands unless the user explicitly asks for a migration or recovery step.

## Promote Scratch To Durable Artifacts

When local notes reveal durable work, promote them before handoff:
- Bug, feature, chore, or follow-up: create or link a GitHub Issue, or capture it in the active PR when it is scoped to that PR.
- Complex or risky implementation context: update or create the relevant ExecPlan under `/hyperopen/docs/exec-plans/**`.
- Agent-harness failure, proposal, or lesson: write an Improvement Plane artifact with `public_refs` and non-authoritative `local_refs`.
- Stable policy, invariant, or operational rule: update the canonical doc that owns that area.
- Review feedback: keep it in the PR review thread or summarize it in the PR so external contributors can see it.

Do not create a markdown backlog or issue queue as a replacement tracker. Markdown checklists are acceptable only inside implementation artifacts, governed docs, product/spec acceptance criteria, or PR descriptions where GitHub remains the visible work surface.

## Session Completion and Handoff
When finishing a coding session or handing work to another contributor:
1. Update the GitHub Issue/PR, active ExecPlan, Improvement Plane artifact, or canonical doc that owns any remaining follow-up.
2. Run required quality gates when code changed:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
3. Record validation evidence and blockers in contributor-visible artifacts.
4. Pull/rebase and push only when the user explicitly requests remote sync in the current session:
   - `git pull --rebase`
   - `git push`
   - `git status` (confirm clean state and up-to-date branch)
5. Provide handoff notes with relevant GitHub refs, ExecPlan paths, Improvement Plane artifacts, and any blockers.

If remote sync is not explicitly requested, stop at local commit and provide handoff notes.

If push is explicitly requested but cannot complete due environment or permissions, record the blocker explicitly in the handoff and in the relevant public or committed artifact.

## Shared Agent Command Phrases
- Machine-readable registry: `/hyperopen/command-phrases.edn`
- Lookup command: `/hyperopen/tools/phrase get "<phrase>"`
- Canonical long-form intent and policy live in this section; keep it aligned with the registry.
- Registry schema: `:schema-version 2` with `:commands` and `:alias->id`.
- Store aliases in normalized form (trimmed, single-space, lowercase) for direct lookup.

### `land the worktree`
- Registry id: `land-the-worktree`
- Alias: `$land`
- Scope: local integration cleanup for commit/rebase/fast-forward merge/worktree cleanup.

Long-form workflow:
1. If the current worktree is detached `HEAD`, create an ephemeral branch from current `HEAD` (for example `codex/land-<timestamp>`).
2. Commit staged changes on the current branch (`git commit ...`). Do not auto-stage files.
3. Rebase the working branch onto local `main` (`git rebase main`).
4. If rebase succeeds, merge into local `main` with fast-forward only:
   - `git checkout main`
   - `git merge --ff-only <working-branch>`
5. Delete the feature worktree and branch:
   - `git worktree remove <feature-worktree-path>` (run from a different worktree)
   - `git branch -d <working-branch>`

Guardrails:
- If unstaged changes exist, stop and request stage/discard before rebase.
- Stop immediately on rebase/merge conflicts; do not force-delete branch/worktree.
- Push behavior is separate unless explicitly requested.
