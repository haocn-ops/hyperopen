---
owner: platform
status: completed
source_of_truth: false
tracked_issue: hyperopen-5di4
---

# Stale Optimizer V4 ExecPlan Consolidation

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current while the work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md`. The live `bd` issue for this work is `hyperopen-5di4`.

## Purpose / Big Picture

Humans and agents should be able to open `/hyperopen/docs/exec-plans/active/` and see only genuinely live implementation plans. The optimizer v4 alignment, setup pixel parity, and visual remediation plans are historical records whose implementation work already landed, but they still sit in `active/`, all three are marked `source_of_truth: true`, and they point at old temporary or worktree paths. This pass consolidates those plans into completed history, leaves any unresolved product/design review as notes rather than active implementation scope, and closes the matching `bd` issues so status is not split between markdown and `bd`.

## Progress

- [x] (2026-05-12 02:31Z) Created and claimed `bd` issue `hyperopen-5di4` for the consolidation.
- [x] (2026-05-12 02:32Z) Audited the active optimizer v4 ExecPlans and confirmed that `2026-04-26-portfolio-optimizer-v4-alignment.md`, `2026-04-27-portfolio-optimizer-setup-v4-pixel-parity.md`, and `2026-04-27-portfolio-optimizer-v4-visual-parity-remediation.md` are stale historical plans.
- [x] (2026-05-12 02:34Z) Moved the three stale optimizer v4 ExecPlans from `docs/exec-plans/active/` to `docs/exec-plans/completed/`.
- [x] (2026-05-12 02:36Z) Normalized their front matter and cross-plan references so they no longer advertise themselves as active source-of-truth documents or point at old `d394` worktree-specific plan paths.
- [x] (2026-05-12 02:37Z) Closed `hyperopen-q7j5`, `hyperopen-g0m6`, and `hyperopen-cwlf` as completed historical implementation work.
- [x] (2026-05-12 02:38Z) Ran `npm run lint:docs`; docs check passed.
- [x] (2026-05-12 02:39Z) Updated this consolidation plan for completion and prepared it to move to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The stale plans are still structurally passing the active-plan guardrails because their `bd` issues remain `in_progress` and each has an unchecked "await review" item.
  Evidence: `bd show hyperopen-q7j5 --json`, `bd show hyperopen-g0m6 --json`, and `bd show hyperopen-cwlf --json` all report `status: in_progress`, while the plans retain unchecked design-review or visual-normalization progress items.

- Observation: The active optimizer results parity plan is already marked `source_of_truth: false` and was not the specific source-of-truth collision called out by this ticket.
  Evidence: `docs/exec-plans/active/2026-04-27-portfolio-optimizer-results-v4-parity.md` has `source_of_truth: false` in front matter. This pass leaves it in place unless docs validation shows it must move as part of the same cleanup.

- Observation: The targeted docs validation script is `npm run lint:docs`, not `npm run docs:check`.
  Evidence: `package.json` defines `lint:docs: bb -m dev.check-docs`; `npm run lint:docs` completed with `Docs check passed.`

## Decision Log

- Decision: Move the three called-out v4 plans to `completed/`, not `deferred/`.
  Rationale: Their implementation progress sections record completed route decomposition, setup parity, visual remediation, test passes, and browser artifacts. The only remaining checklist items are review wait states, not active engineering scope.
  Date/Author: 2026-05-12 / Codex

- Decision: Close the matching `bd` issues after the markdown move.
  Rationale: `/hyperopen/docs/WORK_TRACKING.md` makes `bd` the issue lifecycle source of truth. Leaving the issues `in_progress` after moving the plans would preserve the status split this task is meant to remove.
  Date/Author: 2026-05-12 / Codex

- Decision: Preserve historical design/download artifact references but remove old worktree-specific plan references from front matter and current-state prose.
  Rationale: Some artifact paths document where screenshots or design files came from during the original work. The confusing part is an active source-of-truth plan pointing at another agent's `/Users/barry/.codex/worktrees/d394/...` plan path as if it were current.
  Date/Author: 2026-05-12 / Codex

## Outcomes & Retrospective

The stale optimizer v4 source-of-truth collision is resolved. The v4 alignment, setup pixel parity, and visual remediation plans now live under `docs/exec-plans/completed/`, are marked `status: completed` and `source_of_truth: false`, include consolidation notes, and no longer point at the old `d394` worktree for cross-plan references. Their lingering review wait states are recorded as historical notes rather than active implementation scope.

This reduced planning complexity without touching production code. After this plan moved to completed, the active directory retained only the root bundle follow-up and the already non-source-of-truth optimizer results parity plan. The matching `bd` issues `hyperopen-q7j5`, `hyperopen-g0m6`, and `hyperopen-cwlf` are closed, so issue lifecycle and markdown location now agree.

## Context and Orientation

ExecPlans live under `/hyperopen/docs/exec-plans/`. Plans in `active/` are treated as live work by `/hyperopen/dev/check_docs.clj`: each active plan must reference at least one open `bd` issue and must keep at least one unchecked progress item. That guardrail is useful for real implementation work, but stale historical plans can satisfy it indefinitely if their issues stay open and they contain an unchecked review item.

The stale optimizer v4 plans are:

- `docs/exec-plans/active/2026-04-26-portfolio-optimizer-v4-alignment.md`, tracked by `hyperopen-q7j5`.
- `docs/exec-plans/active/2026-04-27-portfolio-optimizer-setup-v4-pixel-parity.md`, tracked by `hyperopen-g0m6`.
- `docs/exec-plans/active/2026-04-27-portfolio-optimizer-v4-visual-parity-remediation.md`, tracked by `hyperopen-cwlf`.

These plans predate later completed optimizer refactors such as contracts/codecs, parsing normalization, Black-Litterman worker boundary work, and the selector layer. They should remain searchable as completed implementation history, but they should not compete as canonical active guidance for new optimizer refactors.

## Plan of Work

First, move the three stale files from `docs/exec-plans/active/` to `docs/exec-plans/completed/`. Keep their filenames unchanged so existing search history remains stable.

Second, edit each moved file's front matter from `status: active` to `status: completed` and from `source_of_truth: true` to `source_of_truth: false`. Add a short consolidation note near the top and a revision note near the bottom explaining that the plan was moved out of active on 2026-05-12 because it is historical implementation evidence, not live source-of-truth scope.

Third, replace worktree-specific cross-plan references in the moved files with repository-root references to their completed locations. For this pass, the target references are the setup parity and visual remediation `based_on` entries that pointed at another local worktree's `docs/exec-plans/active/...` paths.

Fourth, close `hyperopen-q7j5`, `hyperopen-g0m6`, and `hyperopen-cwlf` with a reason that states they were consolidated into completed ExecPlan history. Then update this plan's progress, outcome, and validation notes, move it to `docs/exec-plans/completed/`, and close `hyperopen-5di4`.

## Concrete Steps

Run these commands from `/Users/barry/.codex/worktrees/2be1/hyperopen`.

    git mv docs/exec-plans/active/2026-04-26-portfolio-optimizer-v4-alignment.md docs/exec-plans/completed/2026-04-26-portfolio-optimizer-v4-alignment.md
    git mv docs/exec-plans/active/2026-04-27-portfolio-optimizer-setup-v4-pixel-parity.md docs/exec-plans/completed/2026-04-27-portfolio-optimizer-setup-v4-pixel-parity.md
    git mv docs/exec-plans/active/2026-04-27-portfolio-optimizer-v4-visual-parity-remediation.md docs/exec-plans/completed/2026-04-27-portfolio-optimizer-v4-visual-parity-remediation.md

Use `apply_patch` to update the moved plan metadata and consolidation notes.

Then run:

    bd close hyperopen-q7j5 --reason "Completed implementation plan; moved to completed ExecPlan history during hyperopen-5di4 consolidation." --json
    bd close hyperopen-g0m6 --reason "Completed setup parity plan; moved to completed ExecPlan history during hyperopen-5di4 consolidation." --json
    bd close hyperopen-cwlf --reason "Completed visual remediation plan; moved to completed ExecPlan history during hyperopen-5di4 consolidation." --json
    npm run lint:docs

If `npm run lint:docs` is not available, run the docs check through the project check command and record the result:

    npm run check

After validation passes, update this plan, move it to `docs/exec-plans/completed/`, and close `hyperopen-5di4`.

## Validation and Acceptance

Acceptance is documentation and lifecycle clarity. The active ExecPlan directory no longer contains the three stale optimizer v4 source-of-truth plans. The moved plans clearly say they are completed historical records. Their front matter no longer marks them as active source-of-truth documents. Old worktree-specific cross-plan references are replaced with repository-root completed-plan references. `bd` no longer reports `hyperopen-q7j5`, `hyperopen-g0m6`, or `hyperopen-cwlf` as `in_progress`.

The validation command must pass at least the docs guardrail portion. Because this is docs-only work, full application, websocket, and browser QA gates are not required unless `npm run check` reveals a broader repository issue.

## Idempotence and Recovery

The file moves are safe to repeat only if the files still exist at the source path; otherwise inspect `git status --short` and continue from the current location. If a moved file needs to be restored to active, use `git mv` back to `docs/exec-plans/active/` and reopen its `bd` issue. Do not use `git reset --hard`.

## Artifacts and Notes

Initial audit commands:

    bd show hyperopen-q7j5 --json
    bd show hyperopen-g0m6 --json
    bd show hyperopen-cwlf --json

All three issues reported `status: in_progress` before this cleanup.

Validation transcript:

    npm run lint:docs
    # Docs check passed.

Revision note (2026-05-12 / Codex): Implemented the consolidation, recorded docs validation, and prepared this plan for completed history under `hyperopen-5di4`.

## Interfaces and Dependencies

No production code interfaces change. This work depends only on the repository planning contract in `docs/PLANS.md`, the issue lifecycle contract in `docs/WORK_TRACKING.md`, the docs guardrail implementation in `dev/check_docs.clj`, and the local `bd` CLI.
