# Make Equal Risk determine net exposure

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current while work proceeds. This document is maintained in accordance with `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`.

## Purpose / Big Picture

Equal Risk currently treats both gross exposure and net exposure as exact equations. In a portfolio with one selected long and one selected short, those two equations determine both weights before the covariance-based Equal Risk objective can change anything. A trader can therefore request Equal Risk and receive a portfolio whose signed Euler risk contributions are far from equal even though the solver reports a feasible result.

After this change, choosing Equal Risk keeps the trader's selected assets, long/short sides, gross target, position bounds, locks, shortability, and turnover policy, but does not constrain net exposure. The optimizer chooses the relative long and short magnitudes to balance signed Euler contributions, scales the result to the selected gross target, and reports the resulting net exposure as an output. Stored net settings remain untouched so switching to another objective restores the trader's prior policy. The setup surface makes net unavailable while Equal Risk is selected and explains that covariance and the selected sides determine it.

## Context References

Public refs:

- Direct user/maintainer request dated 2026-07-12: create and execute a plan so Equal Risk removes or ignores net exposure while leaving the trader in control of gross leverage.

Repo artifacts:

- `/hyperopen/docs/design-docs/optimizer-equal-risk.md`
- `/hyperopen/docs/exec-plans/active/2026-07-10-optimizer-equal-risk-objective.md`
- `/hyperopen/src/hyperopen/portfolio/optimizer/BOUNDARY.md`
- `/hyperopen/docs/FRONTEND.md`
- `/hyperopen/docs/BROWSER_TESTING.md`

Local scratch refs (non-authoritative):

- `/hyperopen/tmp/multi-agent/equal-risk-ignore-net/`

## Progress

- [x] (2026-07-12 13:28Z) Reviewed the existing request, constraint, presolve, seed, SQP, payload, setup UI, results copy, and execution paths; reproduced the zero-degree one-long/one-short behavior.
- [x] (2026-07-12 13:28Z) Froze the product contract: net settings are preserved in the draft but ignored by Equal Risk; gross remains user-controlled; no new leverage, volatility, or execution gate is added.
- [x] (2026-07-12 18:40Z) Produced and approved acceptance plus edge-case coverage through the frozen contract under `tmp/multi-agent/equal-risk-ignore-net/`.
- [x] (2026-07-12 18:40Z) Materialized RED tests proving Equal Risk ignores net while other objectives and stored net settings remain unchanged.
- [x] (2026-07-12 19:20Z) Implemented the gross-only Equal Risk feasible region, seed projection, allocation-freedom diagnostics, result copy, and setup UI.
- [x] (2026-07-12 19:30Z) Updated canonical Equal Risk documentation and this plan's living sections.
- [x] (2026-07-12 16:47Z) Applied the post-QA GREEN fix so Equal Risk setup summary surfaces show selected gross plus output-only resulting net instead of persisted net targets; `npx shadow-cljs --force-spawn compile test` and `node out/test.js` passed.
- [x] (2026-07-12 16:55Z) Passed the final 34-gate repository matrix: 6,189 tests, 33,026 assertions, zero failures; focused Playwright passed 11/11.
- [x] (2026-07-12 16:55Z) Passed all six governed browser-QA passes at 375, 768, 1280, and 1440 with no overflow, runtime errors, or unstable interactions; cleaned all browser sessions.
- [x] (2026-07-12 16:55Z) Completed findings-first static review with no remaining findings and moved this plan to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: Equal Risk currently encodes two book-sum equalities, so one long plus one short has zero free dimensions.
  Evidence: `domain/equal_risk_plan.cljs` creates independent long-book and short-book equations; `application/engine/equal_risk_payload.cljs` subtracts one degree per book.

- Observation: the same setup constraint can be displayed as a ceiling or band while Equal Risk reconstructs its midpoint as an exact target.
  Evidence: `views/portfolio/optimize/setup_exposure_map.cljs` renders `gross <=`, while `domain/exposure_policy.cljs` and `domain/equal_risk.cljs` recover exact exposure targets.

- Observation: net values must remain persisted even though they are inactive for Equal Risk, otherwise selecting the objective would destructively rewrite the policy used by every other objective.
  Evidence: all objectives share the same draft constraint map and request signature.

- Observation: the legacy worker test still asserted an exact stored net target on Equal Risk after the new contract was frozen.
  Evidence: `test/hyperopen/portfolio/optimizer/worker_test.cljs` expected `:net` near `0.5`; the new contract requires exact gross and resulting net, so the legacy assertion was updated without touching the frozen materialized tests.

- Observation: Equal Risk copy existed outside the setup/result view models and still claimed net bias was preserved or worth loosening.
  Evidence: `scenario_objective_menu.cljs` and `equal_risk_confidence_rail.cljs` contained stale net-bias language after the domain patch.

- Observation: the first responsive QA pass found that the Run summary still echoed persisted net policy as if it were an Equal Risk target even though the solver ignored it.
  Evidence: the 1280px browser capture showed `16.80x gross · +0.10x net long`; a new RED view-model test reproduced the defect, and the final capture shows `16.80x gross · Resulting net`.

## Decision Log

- Decision: ignore net only inside the Equal Risk planner and presentation; do not delete or rewrite net fields in draft state.
  Rationale: objective switching must be reversible and must not destroy user settings.
  Date/Author: 2026-07-12 / Codex and user.

- Decision: retain the selected gross target as an exact normalization equation.
  Rationale: signed relative risk contributions are invariant to common weight scaling, so one gross equation identifies the portfolio scale while leaving the composition available to Equal Risk. The user explicitly owns the leverage choice.
  Date/Author: 2026-07-12 / Codex and user.

- Decision: retain fixed sides, bounds, locks, shortability, and turnover.
  Rationale: the requested change removes the conflicting net target, not the trader's directional choices or existing safety/feasibility rules.
  Date/Author: 2026-07-12 / Codex and user.

- Decision: do not add target-volatility scaling, leverage warnings, or execution blocking in this ticket.
  Rationale: those are explicitly outside the user's requested scope.
  Date/Author: 2026-07-12 / Codex and user.

- Decision: present the setup exposure map in an objective-aware gross-only mode rather than deleting persisted net controls globally.
  Rationale: the Equal Risk UI must not imply that net is sent to the solver, while other objectives must retain the existing two-dimensional gross/net policy unchanged.
  Date/Author: 2026-07-12 / Codex.

## Outcomes & Retrospective

Implementation and validation are complete. Equal Risk now models one signed-gross equation instead of separate long/short book sums, so one long plus one short has genuine composition freedom. Non-Equal-Risk behavior remains unchanged because stored net fields are preserved and the Equal Risk objective gates the gross-only presentation and action path. Complexity decreased in the pure domain plan by removing net/book target plumbing, while setup UI gained small objective-aware disabled states.

Post-QA GREEN fix: Equal Risk setup summaries now project selected gross plus output-only resulting net, including the Run summary card and collapsed Portfolio exposure header, so stale stored net policy is no longer echoed as an active target. The final 34-gate matrix passed with 6,189 tests and 33,026 assertions; focused Playwright passed 11/11; all six browser-QA passes passed at the four required widths; cleanup succeeded; and findings-first static review found no remaining release blocker.

## Context and Orientation

The optimizer draft stores generic constraints such as `:gross-min`, `:gross-max`, `:net-min`, and `:net-max`. `application/request_builder.cljs` renames those fields for the engine. `domain/constraints.cljs` encodes signs, bounds, locks, turnover, and exposure metadata. `domain/equal_risk_presolve.cljs` validates the Equal Risk feasible region. `domain/equal_risk_plan.cljs` creates the equations handed to each quadratic-program subproblem. `application/engine/equal_risk_solve.cljs` projects deterministic seeds and runs the sequential Equal Risk solver. `domain/risk_contributions.cljs` computes signed Euler contributions.

Today Equal Risk creates one equality for the total long magnitude and one for the total short magnitude. Those values are derived from gross `G` and net `N`: long magnitude is `(G + N) / 2` and short magnitude is `(G - N) / 2`. This ticket replaces those two equations with one signed-gross equation: every long coefficient is `+1`, every short coefficient is `-1`, and the row target is `G`. Because short weights are negative, this row computes the sum of absolute magnitudes while allowing the long/short split to move.

The setup view is composed by `views/portfolio/optimize/setup_constraint_controls.cljs` and `setup_exposure_map.cljs`, with pure display data from `application/view_model/exposure.cljs` and transitions in `actions/exposure.cljs`. Equal Risk needs a gross-only presentation: vertical gross interaction stays active; net target, net band, and raw net min/max controls are not interactive; explanatory copy states that net is determined by risk balance. The hidden draft net values must not change when gross is edited.

The results read model in `application/view_model/equal_risk_results.cljs` currently says gross and net were preserved. It must instead describe the gross target and label net as resulting exposure. `application/engine/equal_risk_payload.cljs` currently computes free dimensions by subtracting one equation per book; after this change it subtracts one total gross equation, so one long plus one short has one free dimension.

## Plan of Work

Milestone 1 freezes the behavioral contract in tests. Acceptance coverage proves that a mixed two-asset Equal Risk request with unequal volatilities reaches inverse-volatility-like signed Euler parity at the chosen gross regardless of two different net settings carried on the request. Edge coverage proves all-long/all-short books, caps, locks, turnover, deterministic starts, and non-Equal-Risk objectives remain correct. UI coverage proves net controls disappear or become non-interactive only for Equal Risk and that gross edits preserve stored net values.

Milestone 2 changes the pure domain feasible region. In `domain/equal_risk.cljs`, exposure targets become gross-only for this objective and seed construction projects all signed magnitudes onto one bounded gross simplex instead of projecting each side separately. In `domain/equal_risk_presolve.cljs`, delete net-target validation and side-budget capacity checks; validate finite positive gross, fixed sides, covariance, shortability, and aggregate magnitude capacity. In `domain/equal_risk_plan.cljs`, replace the two book equations and the plan-level net rows with one gross equality. Keep bounds and turnover unchanged. In `application/engine/equal_risk_solve.cljs`, carry gross-only target metadata through every initializer and convergence scale.

Milestone 3 updates payload and presentation truth. In `application/engine/equal_risk_payload.cljs`, compute free dimensions as unlocked selected positions minus the one gross equality, then account for binding caps in the existing limited/open classification. In `application/view_model/equal_risk_results.cljs` and the confidence rail, describe net as a result rather than a preserved input. Update setup views/actions so Equal Risk has a gross-only interaction and stored net values remain inert. Keep all existing behavior for other objectives.

Milestone 4 updates `/hyperopen/docs/design-docs/optimizer-equal-risk.md`, adds deterministic Playwright coverage to the existing optimizer setup flow or a focused optimizer spec, runs browser QA at 375, 768, 1280, and 1440 widths, runs full gates, and performs findings-first static review. Any defect found by review or QA returns to RED before implementation changes.

## Concrete Steps

All commands run from `/Users/barry/.codex/worktrees/8372/hyperopen`.

First bootstrap and materialize the approved failing tests:

    npm run setup:worktree
    npx shadow-cljs --force-spawn compile test
    node out/test.js

The new focused tests must fail before production changes for the intended reasons: the solver still pins net, the plan still contains net rows, allocation freedom is still zero for one long plus one short, or Equal Risk still exposes interactive net controls.

After implementation, run focused compilation/tests first, then the complete gates:

    npx shadow-cljs --force-spawn compile test
    node out/test.js
    npm run gates

Run the smallest relevant committed browser test first. Prefer extending the existing optimizer setup regression when its selectors already cover objective selection and exposure controls:

    npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "Equal Risk" --workers=1

Then run governed UI QA and cleanup:

    npm run qa:design-ui -- --changed-files <comma-separated changed UI files> --manage-local-app
    npm run browser:cleanup

## Validation and Acceptance

The implementation is accepted only when all of the following are true.

An Equal Risk request containing one fixed long and one fixed short, unequal diagonal variances, gross target `2.0`, and net settings `0.0/0.0` solves near the equal-contribution ratio rather than `[1,-1]`. The same request with materially different stored net settings produces the same target weights and signed contribution summary. Gross remains `2.0`; resulting net is whatever those weights imply.

The Equal Risk solver plan contains one gross equality and no net equality or net inequality. Presolve does not reject a request because its stored net target exceeds gross or falls outside a net band. Aggregate magnitude bounds, fixed sides, shortability, locks, and turnover remain enforced.

One long plus one short reports one free allocation dimension rather than fully determined. A single selected position remains fully determined by gross. Existing signed contribution, covariance-scale, global-sign, permutation, determinism, cap, lock, turnover, worker, and payload tests remain green.

When Equal Risk is selected on the setup route, the user can select gross leverage but cannot select net target or net band. The surface says that resulting net is determined by Equal Risk from covariance and selected sides. Editing gross does not alter stored `:net-min`, `:net-max`, or `:net-band-pct`. Switching to another objective restores the existing two-dimensional net controls with the prior values.

Equal Risk results describe gross as the selected target and net as the resulting exposure. Other objectives preserve their existing gross/net request, solver, setup, and result behavior.

The focused Playwright test passes, every required browser-QA pass is explicitly `PASS`, `FAIL`, or `BLOCKED` at 375, 768, 1280, and 1440 widths, browser sessions are cleaned up, and `npm run gates` reports all required commands passing.

## Idempotence and Recovery

All changes are local source, test, and documentation edits. No external state, migration, persistence rewrite, or remote synchronization is required. Re-running tests, Playwright, browser QA, and cleanup is safe. If implementation fails partway, keep the active ExecPlan current and return to the smallest failing test. Stored net fields must never be deleted as a recovery shortcut.

## Artifacts and Notes

Multi-agent proposal, review, and browser artifacts live under `/hyperopen/tmp/multi-agent/equal-risk-ignore-net/` and `/hyperopen/tmp/browser-inspection/`; they are non-authoritative. Durable decisions and validation evidence belong in this ExecPlan.

## Interfaces and Dependencies

No new external library is required. Preserve public objective kind `:equal-risk`, persisted draft shapes, worker wire formats, and existing action names where practical. The key post-change domain interfaces are gross-only Equal Risk target derivation, aggregate bounded-magnitude seed projection, one gross equality in every Equal Risk subproblem, and objective-aware setup read models. Domain code remains pure; views do not compute optimization math.

Revision note (2026-07-12): initial plan created from the direct user request and the completed algorithm review. It freezes net as output-only for Equal Risk while preserving stored net policy for reversible objective switching.

Revision note (2026-07-12): implementation completed, a post-QA stale-net summary defect was driven through RED/GREEN, all repository and browser gates passed, and the plan was closed.
