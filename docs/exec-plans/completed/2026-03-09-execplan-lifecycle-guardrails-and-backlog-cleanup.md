# Audit ExecPlan Lifecycle and Enforce Active-Plan Guardrails

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`.

## Purpose / Big Picture

After this change, `/hyperopen/docs/exec-plans/active/` will mean one thing again: work that is actively being executed right now. Historical plans that describe implemented or closed work will live under `/hyperopen/docs/exec-plans/completed/`, stale authored-only plans that are not currently being executed will move out of `active`, and `npm run check` will fail if a future active ExecPlan has no live `bd` issue or no remaining unchecked progress.

The user-visible proof is simple. Listing `/hyperopen/docs/exec-plans/active/` after this change should show only the README while this work is finished, and the docs check should reject any attempt to leave a completed or orphaned ExecPlan in the active directory.

## Progress

- [x] (2026-03-09 18:21Z) Audited `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/docs/WORK_TRACKING.md`, active/completed ExecPlan directories, and the current docs check implementation.
- [x] (2026-03-09 18:24Z) Created and claimed `bd` task `hyperopen-asq` for this cleanup and guardrail work.
- [x] (2026-03-09 18:28Z) Built an active-plan audit showing 52 root ExecPlan files in `/hyperopen/docs/exec-plans/active/`, including closed-issue plans, authored-only stale plans, and superseded notes.
- [x] (2026-03-09 18:29Z) Added the then-current docs-check guardrails in `/hyperopen/dev/check_docs.clj` for active ExecPlan tracker references and unchecked progress.
- [x] (2026-03-09 18:29Z) Added docs-check regression coverage in `/hyperopen/dev/check_docs_test.clj` for missing `bd` links, closed-only `bd` links, missing unchecked progress, and valid active-plan state.
- [x] (2026-03-09 18:30Z) Reclassified the stale active backlog: moved 48 historical plans to `/hyperopen/docs/exec-plans/completed/` and 4 non-active notes to `/hyperopen/docs/exec-plans/deferred/`.
- [x] (2026-03-09 18:31Z) Created backlog `bd` issues `hyperopen-1ug`, `hyperopen-gvp`, and `hyperopen-0o0` for the deferred authored-only plans that still represent future work.
- [x] (2026-03-09 18:31Z) Updated planning governance docs and README files to document the new active/completed/deferred contract and the docs-check enforcement path.
- [x] (2026-03-09 18:33Z) Bootstrapped missing npm dependencies with `npm ci`, then ran required validation gates: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-03-09 18:33Z) Confirmed `/hyperopen/docs/exec-plans/active/` now contains only this in-flight plan plus `README.md`; after closeout, only `README.md` will remain.
- [x] (2026-03-09 18:34Z) Closed `bd` task `hyperopen-asq` with reason `Completed` and prepared this ExecPlan to move to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The repo policy already says completed plans must move out of `active`; the gap is enforcement, not missing written guidance.
  Evidence: `/hyperopen/docs/PLANS.md` workflow step 3 says to move the plan to completed after acceptance criteria pass.

- Observation: The stale backlog contains more than one failure mode, not just “implemented but never moved.”
  Evidence: The audit found plans tied to closed `bd` issues, plans with every checkbox complete but no tracker link, and authored-only notes with no live execution owner.

- Observation: `dev/check_docs.clj` is already part of `npm run check`, so the cheapest reliable guardrail is to enforce active-plan lifecycle rules there.
  Evidence: `/hyperopen/package.json` runs `npm run lint:docs` from `npm run check`, and `npm run lint:docs` runs `bb -m dev.check-docs`.

- Observation: Several legacy plans were authored before `bd` adoption or before the current plan hygiene rules, so “missing tracker link” does not always mean the work is actually open.
  Evidence: Many moved historical plans had zero unchecked progress items and passed implementation evidence, but referenced no `bd` issue at all.

- Observation: The local environment still needed a dependency bootstrap before the required gates could succeed.
  Evidence: The first validation attempt failed because `@noble/secp256k1` was absent from `node_modules`; after `npm ci`, all three required gates passed.

## Decision Log

- Decision: Enforce active ExecPlan lifecycle in `dev/check_docs.clj` instead of introducing a separate maintenance script.
  Rationale: The repo already requires `npm run check`, so a docs-check guardrail turns this from convention into an existing gate without adding another command contributors must remember.
  Date/Author: 2026-03-09 / Codex

- Decision: Require active ExecPlans to have both a live `bd` reference and at least one unchecked progress item.
  Rationale: Those two conditions capture the intended meaning of “active”: tracked ongoing work that still has remaining implementation steps.
  Date/Author: 2026-03-09 / Codex

- Decision: Move authored-only stale plans that are not completed implementations out of `active` rather than force-fitting them into `completed`.
  Rationale: `completed` should remain for implemented or otherwise finished work; stale planning notes belong outside the live execution queue but should not be mislabeled as done.
  Date/Author: 2026-03-09 / Codex

- Decision: Create `bd` issues for deferred authored-only plans that still represent future work.
  Rationale: Deferred markdown plans are useful context, but `bd` was the local tracker used for open backlog so the cleanup did not replace one shadow tracker with another.
  Date/Author: 2026-03-09 / Codex

## Outcomes & Retrospective

Implementation completed. The active ExecPlan backlog was normalized so that only live execution remains in `/hyperopen/docs/exec-plans/active/`. The cleanup moved 48 historical plans into `/hyperopen/docs/exec-plans/completed/` and 4 non-active notes into `/hyperopen/docs/exec-plans/deferred/`. Three of those deferred notes still represented future backlog, so the cleanup also created `bd` issues `hyperopen-1ug`, `hyperopen-gvp`, and `hyperopen-0o0`.

The historical guardrail added by this task lived in `/hyperopen/dev/check_docs.clj` and was covered by `/hyperopen/dev/check_docs_test.clj`. At the time, `npm run check` failed if a root active ExecPlan had no valid live local tracker reference or no remaining unchecked progress items. The planning docs were updated to describe `active`, `completed`, and `deferred` roles explicitly, and README guidance matched the then-enforced behavior.

Validation results after `npm ci`:

- `npm run check`: pass
- `npm test`: pass (`2196` tests, `11541` assertions)
- `npm run test:websocket`: pass (`370` tests, `2120` assertions)

Complexity impact: overall operational complexity is reduced. The repository now has one enforced meaning for “active ExecPlan,” and the cleanup removed a large amount of stale plan noise without adding a separate maintenance command.

## Context and Orientation

Active ExecPlans currently live in `/hyperopen/docs/exec-plans/active/`, completed plans live in `/hyperopen/docs/exec-plans/completed/`, and deferred plans live in `/hyperopen/docs/exec-plans/deferred/`. The current docs linter is `/hyperopen/dev/check_docs.clj`, with regression coverage in `/hyperopen/dev/check_docs_test.clj`.

This repo previously used `bd` for issue lifecycle tracking. For this historical task, “live local tracker work” meant at least one referenced `bd` issue existed and was not closed. For this task, “remaining unchecked progress” means the active ExecPlan still contains at least one unchecked `- [ ]` progress item.

The current cleanup target is the root of `/hyperopen/docs/exec-plans/active/`. Nested `artifacts/` files are supporting evidence and are not themselves active plans.

## Plan of Work

First, extend `/hyperopen/dev/check_docs.clj` so it inspects root active ExecPlan files, extracts referenced `bd` ids, resolves their status through the local `bd` CLI, and emits docs-check errors when an active plan has no valid `bd` reference, no open `bd` issue, or no unchecked progress items. Keep the implementation deterministic and inject the `bd` lookup function through config so `/hyperopen/dev/check_docs_test.clj` can stub it.

Second, add regression tests that prove the new rules: an active ExecPlan with an open `bd` issue and unchecked work passes; one without a `bd` link fails; one with only closed linked issues fails; and one with no remaining unchecked progress fails.

Third, move stale plan files out of `/hyperopen/docs/exec-plans/active/`. Plans that document implemented or closed work move to `/hyperopen/docs/exec-plans/completed/`. Authored-only stale planning notes that are not currently being executed move to `/hyperopen/docs/exec-plans/deferred/`.

Finally, update this plan with the actual results, run the required validation gates, close `hyperopen-asq`, and move this ExecPlan to `/hyperopen/docs/exec-plans/completed/`.

## Concrete Steps

From `/hyperopen`:

1. Patch `/hyperopen/dev/check_docs.clj` and `/hyperopen/dev/check_docs_test.clj`.
2. Move stale active ExecPlans into `/hyperopen/docs/exec-plans/completed/` or `/hyperopen/docs/exec-plans/deferred/`.
3. Run:

       npm run check
       npm test
       npm run test:websocket

4. Close `hyperopen-asq` and move this plan to `/hyperopen/docs/exec-plans/completed/`.

## Validation and Acceptance

This work is accepted when all of the following are true:

- `/hyperopen/docs/exec-plans/active/` contains no stale historical plans.
- `bb -m dev.check-docs` fails if an active ExecPlan has no live `bd` issue reference.
- `bb -m dev.check-docs` fails if an active ExecPlan has no remaining unchecked progress items.
- `/hyperopen/dev/check_docs_test.clj` covers the new active-plan lifecycle rules.
- Required gates pass:

      npm run check
      npm test
      npm run test:websocket

## Idempotence and Recovery

The docs-check changes are additive and safe to rerun. Moving plan files is also safe as long as filenames do not collide. If validation reveals a plan was misclassified, move it to the correct directory and rerun the docs check. No application runtime behavior changes in this task.

## Artifacts and Notes

Initial audit summary:

- `/hyperopen/docs/exec-plans/active/` contained 52 root plan files plus `README.md`.
- Multiple active plans referenced only closed `bd` issues such as `hyperopen-pze`, `hyperopen-63a`, `hyperopen-fq5`, `hyperopen-nhv.*`, `hyperopen-918`, and `hyperopen-93r`.
- Authored-only stale notes remained in `active`, including `/hyperopen/docs/exec-plans/active/2026-02-24-hyperliquid-positions-tab-aesthetic-parity.md`, `/hyperopen/docs/exec-plans/active/2026-02-24-market-order-execution-parity-and-submit-status.md`, and `/hyperopen/docs/exec-plans/active/2026-03-03-vaults-bounded-context-consolidation.md`.
- `/hyperopen/docs/exec-plans/completed/2026-03-06-portfolio-vm-facade-and-runtime-extraction.md` explicitly states that `/hyperopen/docs/exec-plans/active/refactor_portfolio_vm.md` is stale and superseded.

## Interfaces and Dependencies

`/hyperopen/dev/check_docs.clj` should expose enough internal helpers that tests can supply a fake `bd` lookup function through config. The guardrail must use the local `bd` CLI output as the then-current issue-status reference and must only inspect root active ExecPlan files, not nested artifacts.
