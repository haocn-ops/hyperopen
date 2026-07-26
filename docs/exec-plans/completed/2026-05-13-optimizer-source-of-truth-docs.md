# Promote Portfolio Optimizer Source-of-Truth Docs

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, an agent or human changing `/portfolio/optimize` can open the canonical architecture and optimizer boundary docs and quickly answer: what owns the optimizer, which source files should change for a given kind of work, what must stay pure, where side effects belong, and which tests or browser checks prove the change. The stale active results parity plan will no longer signal that implementation work is still active when only design-review history remains.

This is a documentation refactor only. It should not change runtime behavior, compiled assets, optimizer math, route parsing, persistence, workers, or tests.

## Context References

Public refs:

- Direct user and maintainer request in this Codex session on 2026-05-13: “Optimizer source-of-truth docs are stale or incomplete” and “Refactor: promote/update the optimizer boundary doc and add a small ‘where to change what’ map for agents and humans.”

Repo artifacts:

- `src/hyperopen/portfolio/optimizer/BOUNDARY.md`, currently marked draft and reviewed before later optimizer refactors.
- `ARCHITECTURE.md`, the canonical top-level architecture map that does not yet describe `/portfolio/optimize`.
- `docs/exec-plans/active/2026-04-27-portfolio-optimizer-results-v4-parity.md`, an implementation plan whose remaining unchecked item is design review rather than active implementation.
- Completed optimizer implementation context under `docs/exec-plans/completed/`, especially the v4 setup, results, frontier, vault universe, fixture, and vault common-history plans.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-13 13:04Z) Read the current optimizer boundary doc, top-level architecture map, planning rules, active results parity plan, route dispatcher, route parser, optimizer view model, pipeline workflow, worker entrypoint, and optimizer file/test layout.
- [x] (2026-05-13 13:08Z) Updated `src/hyperopen/portfolio/optimizer/BOUNDARY.md` to canonical status, current review date, ownership rules, stable seams, dependency rules, key tests, and a concise “Where This Change Goes” map.
- [x] (2026-05-13 13:08Z) Updated `ARCHITECTURE.md` so the canonical architecture map explicitly describes `/portfolio/optimize`, the route family, optimizer layers, side-effect boundaries, worker boundary, UI ownership, and source-of-truth docs.
- [x] (2026-05-13 13:08Z) Moved `docs/exec-plans/active/2026-04-27-portfolio-optimizer-results-v4-parity.md` to `docs/exec-plans/completed/2026-04-27-portfolio-optimizer-results-v4-parity.md` and revised it to explain that implementation is closed historical context, with design review no longer represented as active implementation.
- [x] (2026-05-13 13:11Z) Ran focused documentation validation and broader repo validation. `npm run lint:docs` passed. The first `npm run check` attempt failed because this worktree had no `node_modules`, so declared packages `zod` and `smol-toml` were unavailable. `npm ci` installed the locked dependency set, and the rerun of `npm run check` passed.
- [x] (2026-05-13 13:11Z) Moved this ExecPlan to `docs/exec-plans/completed/` after acceptance criteria were satisfied and validation evidence was recorded.

## Surprises & Discoveries

- Observation: `/portfolio/optimize` already has clear route separation in code, but that separation is not reflected in canonical architecture docs.
  Evidence: `src/hyperopen/portfolio/routes.cljs` parses `:optimize-index`, `:optimize-new`, and `:optimize-scenario`, while `src/hyperopen/views/portfolio/optimize/view.cljs` dispatches those route kinds to `index-view`, `setup-view`, and `scenario-detail-view`.

- Observation: The current optimizer boundary doc names several post-refactor responsibilities, but its frontmatter still says `status: draft` and `last_reviewed: 2026-04-23`.
  Evidence: `src/hyperopen/portfolio/optimizer/BOUNDARY.md` starts with draft frontmatter even though later completed ExecPlans added v4 setup/results surfaces, worker contracts, vault instruments, fixture seams, and common-history fallback.

- Observation: The active results parity plan is not active implementation work.
  Evidence: `docs/exec-plans/active/2026-04-27-portfolio-optimizer-results-v4-parity.md` has all implementation progress checked and one unchecked item: “Await product/design review of the updated results route against the v4 Results artboard before moving this ExecPlan out of `active`.”

- Observation: The broader check depends on local Node dependencies being installed.
  Evidence: The first `npm run check` attempt failed in `npm run test:multi-agent` because Node could not find `zod` and `smol-toml`. Both packages are declared in `package.json` and `package-lock.json`; after `npm ci`, the same check progressed past multi-agent tests and completed successfully.

## Decision Log

- Decision: Treat this as a documentation-only refactor with no source-code edits.
  Rationale: The debt is stale source-of-truth guidance, not broken optimizer behavior. Updating docs and plan lifecycle state reduces future agent error without risking runtime changes.
  Date/Author: 2026-05-13 / Codex

- Decision: Put the “where to change what” map in `src/hyperopen/portfolio/optimizer/BOUNDARY.md` and summarize the route-level boundary in `ARCHITECTURE.md`.
  Rationale: The boundary doc is closest to the optimizer code and can hold operational guidance, while `ARCHITECTURE.md` should stay concise and point readers to the canonical boundary doc for detail.
  Date/Author: 2026-05-13 / Codex

- Decision: Move the stale results parity plan to `completed/` rather than keep it in `active/`.
  Rationale: Hyperopen’s planning rules say `active` means work is being executed now. A pending design-review memory is useful historical context, but it should not make the active plan set look like implementation is still in progress.
  Date/Author: 2026-05-13 / Codex

## Outcomes & Retrospective

The source-of-truth documentation now reflects the current optimizer architecture. `src/hyperopen/portfolio/optimizer/BOUNDARY.md` is canonical and gives future agents a concrete map from change type to owning source files and tests. `ARCHITECTURE.md` now names `/portfolio/optimize` explicitly, points to the optimizer boundary doc, and states the route, domain/application/infrastructure, worker, UI, style, and browser-testing boundaries.

The stale results parity ExecPlan is no longer active implementation work. It has been moved to `docs/exec-plans/completed/` and revised so future design-review feedback starts from a new durable work item instead of keeping historical implementation context active.

This reduced documentation complexity: future optimizer changes now have one boundary doc and one architecture section to consult instead of relying on stale active plans and scattered completed implementation history. Runtime complexity is unchanged because no production code changed.

## Context and Orientation

The Portfolio Optimizer is the route family under `/portfolio/optimize`. It has three route shapes. `/portfolio/optimize` is the scenario index or landing/history surface. `/portfolio/optimize/new` is the editable setup workspace where a user selects a universe, objective, return model, risk model, constraints, and Black-Litterman views before running the optimizer. `/portfolio/optimize/:scenario-id` is the scenario detail surface with recommendation, rebalance, tracking, and inputs tabs.

The route parser is `src/hyperopen/portfolio/routes.cljs`. The view dispatcher is `src/hyperopen/views/portfolio/optimize/view.cljs`. Setup UI is composed through `setup_view.cljs` and `workspace_view.cljs`; saved or retained scenario detail UI is composed through `scenario_detail_view.cljs` and the tab-specific view namespaces. Optimizer state paths and wire contracts are centralized in `src/hyperopen/portfolio/optimizer/contracts.cljs`.

The optimizer code follows the repository’s domain, application, and infrastructure layering. Domain namespaces under `src/hyperopen/portfolio/optimizer/domain/` own pure math and policy. Application namespaces under `src/hyperopen/portfolio/optimizer/application/` own deterministic orchestration such as request building, setup readiness, history alignment, pipeline workflow, execution workflow, scenario records, tracking, and view-model shaping. Infrastructure namespaces under `src/hyperopen/portfolio/optimizer/infrastructure/` own API, persistence, solver adapter, worker wire normalization, and worker client integration. Runtime effect adapters under `src/hyperopen/runtime/effect_adapters/portfolio_optimizer*.cljs` interpret side effects against the browser store, history loading, persistence, and worker requests. The worker entrypoint is `src/hyperopen/portfolio/optimizer/worker.cljs`.

## Plan of Work

First, rewrite `src/hyperopen/portfolio/optimizer/BOUNDARY.md` into the same practical style as the other canonical boundary docs. Keep current optimizer responsibilities, add post-refactor route and worker context, state dependency rules explicitly, list key tests, and add a compact “Where This Change Goes” map. The map should direct future edits to stable seams such as `contracts.cljs`, `request_builder.cljs`, history loader namespaces, `setup_readiness.cljs`, `pipeline_workflow.cljs`, runtime effect adapters, view-model namespaces, `query_state.cljs`, worker infrastructure, and route-specific view files.

Second, add an optimizer section to `ARCHITECTURE.md`. This section should describe the route family and layer ownership at the architecture level without duplicating every file from the boundary doc. It should state that `/portfolio/optimize` is canonical, that `/portfolio/optimizer` is not the route spelling, and that optimizer domain/application code must not depend on `hyperopen.views.*`.

Third, move the stale active results parity ExecPlan into `completed/`. Update its frontmatter status to completed, mark implementation progress as complete, add a retrospective note explaining that design review is historical or follow-up context rather than active implementation, and avoid creating a parallel active planning artifact.

Fourth, validate the docs. Run `npm run lint:docs` first because this change touches planning and source-of-truth docs. If that passes, run `npm run check` because it also enforces active ExecPlan guardrails and canonical documentation checks. Since no runtime code changes are planned, `npm test` and `npm run test:websocket` are not required by the repository’s “when code changes” gate; they may be run if broader confidence is needed.

## Concrete Steps

From `/Users/barry/.codex/worktrees/02e1/hyperopen`, apply the documentation edits with `apply_patch`.

Move the results parity plan with:

    mv docs/exec-plans/active/2026-04-27-portfolio-optimizer-results-v4-parity.md docs/exec-plans/completed/2026-04-27-portfolio-optimizer-results-v4-parity.md

Then edit the moved plan in place.

Validate with:

    npm run lint:docs
    npm run check

If `npm run check` fails because of unrelated pre-existing work, capture the failing command and the specific unrelated reason in this ExecPlan and the final response.

## Validation and Acceptance

Acceptance is met when the following are true:

1. `src/hyperopen/portfolio/optimizer/BOUNDARY.md` is marked canonical, has a current review date, and gives an agent a usable map for optimizer source changes.
2. `ARCHITECTURE.md` explicitly describes `/portfolio/optimize`, its route family, optimizer layer ownership, side-effect boundaries, and source-of-truth boundary doc.
3. `docs/exec-plans/active/2026-04-27-portfolio-optimizer-results-v4-parity.md` no longer exists; the same historical plan exists under `docs/exec-plans/completed/` with completed status and retrospective context.
4. Documentation validation has been run and its output is recorded here.
5. The resulting git diff contains docs and plan lifecycle edits only.

## Idempotence and Recovery

The edits are text-only and safe to repeat. If the move command has already run, do not recreate the file under `active/`; continue editing `docs/exec-plans/completed/2026-04-27-portfolio-optimizer-results-v4-parity.md`. If validation fails due to wording or active-plan guardrails, update the relevant Markdown file and rerun the focused docs check before the broader check. Do not revert unrelated user changes in the working tree.

## Artifacts and Notes

Validation artifacts:

    npm run lint:docs
    Docs check passed.

    npm run check
    Initial result: failed in npm run test:multi-agent because the worktree had no node_modules and Node could not resolve declared packages zod and smol-toml.

    npm ci
    added 335 packages, and audited 336 packages in 3s

    npm run check
    Final result: passed. Shadow CLJS compile targets app, portfolio, portfolio-worker, portfolio-optimizer-worker, vault-detail-worker, and test all completed with 0 warnings.

Revision note (2026-05-13 / Codex): Initial plan created from the direct documentation debt request. The plan chooses source-of-truth documentation updates and ExecPlan lifecycle cleanup only, with no runtime optimizer changes.

Revision note (2026-05-13 / Codex): Implemented the documentation cleanup, recorded validation evidence, and prepared this plan to move from `active/` to `completed/`.

Revision note (2026-05-13 / Codex): Moved this plan from `active/` to `completed/` after documentation validation and repo check completed.
