# Optimizer Covariance Estimator Recommendation

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The portfolio optimizer currently accepts `:ledoit-wolf` as a risk-model kind, but the implementation does not run a true Ledoit-Wolf estimator. In `src/hyperopen/portfolio/optimizer/domain/risk.cljs`, `:ledoit-wolf` is normalized to `:diagonal-shrink`, so dense universes never receive a data-driven Ledoit-Wolf shrinkage estimate. Sparse universes already route into mixed-frequency covariance, but that path does not yet reuse dense-block Ledoit-Wolf information or expose clear routing metadata.

After this change, an explicit `{:risk-model {:kind :ledoit-wolf-dense}}` request with dense aligned history should run a true Ledoit-Wolf covariance estimator. If any sparse asset is present, the optimizer must not attempt full-matrix Ledoit-Wolf on a non-rectangular history matrix. Instead, it should route to mixed-frequency covariance, apply runtime-only sparse safety caps, and surface warnings and metadata that explain what happened. The legacy `:ledoit-wolf` keyword remains an alias to `:diagonal-shrink` for stored-scenario compatibility during this rollout.

A contributor can verify the outcome with focused optimizer tests. A dense fixture should return `:model :ledoit-wolf-dense`, a scaled-identity shrinkage target, and a shrinkage coefficient between `0` and `1`. A mixed-frequency fixture that requests `:ledoit-wolf-dense` should return `:model :mixed-frequency`, `:requested-model :ledoit-wolf-dense`, and warnings that explain both the sparse routing decision and any runtime cap that affected the solve.

## Context References

Public refs:

- Direct user request in this Codex session on 2026-05-18 to create or refresh an active ExecPlan for the covariance estimator recommendation only.

Repo artifacts:

- `/hyperopen/AGENTS.md` requires complex optimizer work to use an ExecPlan and keeps optimizer math under `src/hyperopen/portfolio/optimizer/**`.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define the active ExecPlan contract.
- `/hyperopen/docs/MULTI_AGENT.md` and `/hyperopen/docs/WORK_TRACKING.md` require the active ExecPlan to be the durable implementation artifact.
- `/hyperopen/src/hyperopen/portfolio/optimizer/BOUNDARY.md` identifies the domain, application, runtime, and view seams relevant to risk-model work.
- `/hyperopen/docs/exec-plans/completed/2026-04-25-portfolio-optimizer-v1-remediation.md` documents why `:diagonal-shrink` became the honest default and why the old `:ledoit-wolf` behavior is misleading today.
- `/hyperopen/docs/exec-plans/completed/2026-05-17-mixed-frequency-optimizer-risk-model.md` is the completed plan for the current mixed-frequency estimator that this work extends.
- `/hyperopen/docs/exec-plans/active/2026-05-14-optimizer-history-api-v2-integration.md` documents the aligned-history path that already preserves native raw price rows for dense and sparse routing.

Local scratch refs:

- None.

## Progress

- [x] (2026-05-18 16:12Z) Reviewed `/hyperopen/AGENTS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/MULTI_AGENT.md`, and `/hyperopen/docs/WORK_TRACKING.md`.
- [x] (2026-05-18 16:12Z) Confirmed the current risk path: `src/hyperopen/portfolio/optimizer/domain/risk.cljs` normalizes `:ledoit-wolf` to `:diagonal-shrink`, `src/hyperopen/portfolio/optimizer/defaults.cljs` still defaults to `:diagonal-shrink`, and sparse cadence routes into `src/hyperopen/portfolio/optimizer/domain/risk_mixed_frequency.cljs`.
- [x] (2026-05-18 16:12Z) Confirmed both `src/hyperopen/portfolio/optimizer/application/history_loader/alignment.cljs` and `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs` already preserve `:raw-price-series-by-instrument`, `:cadence-by-instrument`, and `:risk-estimation` for dense and sparse routing.
- [x] (2026-05-18 16:12Z) Refreshed the existing active covariance ExecPlan instead of creating a parallel active plan file.
- [x] (2026-05-18 17:05Z) Added RED tests in `test/hyperopen/portfolio/optimizer/domain/risk_test.cljs` for `:ledoit-wolf-dense` dense covariance and sparse routing, plus sparse runtime cap coverage in optimizer application/domain tests.
- [x] (2026-05-18 18:35Z) Added `src/hyperopen/portfolio/optimizer/domain/risk_ledoit_wolf.cljs` and wired dense `:ledoit-wolf-dense` requests through `src/hyperopen/portfolio/optimizer/domain/risk.cljs` while keeping legacy `:ledoit-wolf` behavior unchanged as `:diagonal-shrink`.
- [x] (2026-05-18 18:35Z) Routed sparse `:ledoit-wolf-dense` requests to `:mixed-frequency` with the expected `:risk-model-overridden-for-mixed-frequency` warning and preserved `:requested-model` semantics.
- [x] (2026-05-18 18:35Z) Added runtime sparse safety caps in `src/hyperopen/portfolio/optimizer/domain/constraints.cljs` and threaded history-aware constraint encoding through both `src/hyperopen/portfolio/optimizer/application/engine/context.cljs` and `src/hyperopen/portfolio/optimizer/application/display_frontier.cljs` without mutating draft constraints.
- [x] (2026-05-18 18:45Z) Added `:sparse-history-weight-cap-applied` warnings to encoded constraints and included encoded warnings in solved payloads.
- [x] (2026-05-18 19:05Z) Addressed static-review findings: sparse caps now fail loud instead of falling back to uncapped constraints, vault-like instruments without native cadence metadata receive a conservative runtime cap instead of being treated as dense, and infeasible warning payloads are deduped.
- [x] (2026-05-18 19:18Z) Added direct regression coverage in `test/hyperopen/portfolio/optimizer/domain/constraints_test.cljs` for sparse cap tiers, capped infeasibility, and missing native cadence metadata.
- [x] (2026-05-18 19:28Z) Verified the compiled test suite with `npm test`: optimizer tests are green, and the only remaining suite error is the unrelated `hyperopen.api.trading.cancel-request-test` compiled `ReferenceError`.
- [x] (2026-05-18 19:31Z) Ran `npm run check`; it passed, including `portfolio-optimizer-worker` and `test` builds.
- [x] (2026-05-18 19:34Z) Ran `npm run test:websocket`; it passed with `524` tests, `3043` assertions, `0` failures, `0` errors.
- [ ] Milestone 2 follow-up: mixed-frequency covariance still does not reuse a dense Ledoit-Wolf block or emit dense-block pair metadata. This implementation intentionally stops at the dense-only Ledoit-Wolf path plus sparse routing, warnings, and caps.

## Surprises & Discoveries

- Observation: The current setup UI does not expose a Ledoit-Wolf button, but the draft and contract layer still recognize the keyword.
  Evidence: `src/hyperopen/portfolio/optimizer/contracts/specs.cljs` allows `:ledoit-wolf`; `src/hyperopen/portfolio/optimizer/actions/draft.cljs` still coerces it to `{:kind :diagonal-shrink}`; `src/hyperopen/views/portfolio/optimize/setup_model_controls.cljs` renders only `:diagonal-shrink`, `:mixed-frequency`, and `:sample-covariance`.

- Observation: Result payloads already carry `:requested-risk-model`, `:risk-estimation`, and `:pair-metadata`, so most new diagnostics can ride existing payload surfaces instead of adding a new transport.
  Evidence: `src/hyperopen/portfolio/optimizer/application/engine/payload.cljs` includes those fields in solved payloads, and `src/hyperopen/views/portfolio/optimize/results_diagnostics_rail.cljs` already renders result warnings and conditioning diagnostics.

- Observation: Sparse safety caps cannot live only in the solve path or only in the display-frontier path without creating visible inconsistency.
  Evidence: both `src/hyperopen/portfolio/optimizer/application/engine/context.cljs` and `src/hyperopen/portfolio/optimizer/application/display_frontier.cljs` call `src/hyperopen/portfolio/optimizer/domain/constraints.cljs` independently.

- Observation: Sparse caps must fail loud when they make a long-only request infeasible.
  Evidence: static review found the first implementation fell back to uncapped constraints if caps reduced `sum-upper` below `1`. `src/hyperopen/portfolio/optimizer/domain/constraints.cljs` now returns the capped infeasible result and `test/hyperopen/portfolio/optimizer/domain/constraints_test.cljs` covers that branch.

- Observation: Schema-valid manual or stored requests can lack `:cadence-by-instrument` and `:raw-price-series-by-instrument`.
  Evidence: static review found the first implementation could classify a vault as dense by falling back to aligned `:price-series-by-instrument`. Vault-like instruments without native cadence now receive a conservative `20%` runtime cap with a metadata-unavailable warning.

- Observation: the exact `npm test` gate is still blocked by an unrelated generated-code error outside optimizer work.
  Evidence: `npm test` now reports `0 failures, 1 errors`; the only error is `hyperopen.api.trading.cancel-request-test` failing with `ReferenceError: values__9980__auto___23527 is not defined`.

## Decision Log

- Decision: Add a new explicit `:ledoit-wolf-dense` request kind for true dense-only Ledoit-Wolf and keep legacy `:ledoit-wolf` as its current diagonal-shrink alias.
  Rationale: Existing drafts and scenario records may already contain `:ledoit-wolf` and currently mean fixed diagonal shrinkage. A new explicit keyword avoids silent behavior changes while making the truthful estimator available.
  Date/Author: 2026-05-18 / Codex

- Decision: Keep `:diagonal-shrink` as the default risk model and do not add a fourth setup-control button in this ticket.
  Rationale: The requested work is estimator correctness and routing, not a setup-UI redesign. Default drafts stay stable, while explicit `:ledoit-wolf-dense` requests become truthful for saved scenarios, direct action dispatch, and future UI work.
  Date/Author: 2026-05-18 / Codex

- Decision: Any universe with at least one sparse asset must route to `:mixed-frequency`, including when `:ledoit-wolf-dense`, `:diagonal-shrink`, or `:sample-covariance` is requested.
  Rationale: Full-matrix covariance estimators assume one rectangular return matrix. Sparse vault-like histories already have a safer interval-based path and should not be forward-filled or downsampled into misleading dense rows.
  Date/Author: 2026-05-18 / Codex

- Decision: The first implementation pass keeps existing mixed-frequency pairwise covariance semantics and adds runtime sparse caps before deeper dense-block hybridization.
  Rationale: The current mixed-frequency path already fixes the most dangerous sparse-calendar bug by using native intervals. Runtime caps address product safety immediately; dense-block hybridization can follow behind the same metadata contract.
  Date/Author: 2026-05-18 / Codex

- Decision: Sparse safety caps are runtime-only effective max-weight overrides derived from native interval counts.
  Rationale: These caps are guardrails against weak covariance evidence, not user-authored portfolio policy. They must not mutate the draft, but the solver and the display frontier must both honor the same effective caps.
  Date/Author: 2026-05-18 / Codex

- Decision: This is correctness-first work, not performance-first work.
  Rationale: A simpler diagonal-shrink adjustment is insufficient because the request is to add true dense Ledoit-Wolf while keeping sparse interval semantics intact. If later dense-block hybridization adds measurable worker cost, capture a before-and-after timing with `node --test tools/optimizer/solver_spike_benchmark.test.mjs` using one dense fixture and one mixed fixture before keeping further complexity.
  Date/Author: 2026-05-18 / Codex

## Outcomes & Retrospective

This pass implemented the optimizer-local execution path for the external recommendation. Dense aligned histories now have a true pure ClojureScript `:ledoit-wolf-dense` estimator with scaled-identity shrinkage metadata. Sparse `:ledoit-wolf-dense` requests route back to `:mixed-frequency` with the expected override warning. Runtime sparse caps are applied in shared constraint encoding so solver and display-frontier plans stay aligned without mutating draft state.

Static-review findings were addressed before completion: capped infeasibility is preserved, vault-like histories without native cadence metadata do not bypass caps, and duplicate infeasible warnings are deduped. The broader hybrid model remains intentionally incomplete: mixed-frequency covariance has not yet been upgraded to reuse a dense Ledoit-Wolf block.

Validation status: `npm run check` passed; `npm run test:websocket` passed. The exact `npm test` command failed with `0 failures, 1 errors` due to the unrelated `hyperopen.api.trading.cancel-request-test` generated `ReferenceError`.

## Context and Orientation

The portfolio optimizer lives under `src/hyperopen/portfolio/optimizer`. Domain namespaces are pure math and portfolio policy. Application namespaces build requests, encode constraints, and shape worker payloads. Runtime effect adapters own browser side effects. Views consume view models and warnings; they must not implement covariance math.

The dense covariance path begins with aligned return history. In this repository, `:return-series-by-instrument` means per-instrument return vectors already aligned onto the common optimizer calendar. The sparse path depends on native price rows. In this repository, `:raw-price-series-by-instrument` means the original per-instrument time series, preserved specifically so sparse assets can be evaluated over economically valid intervals rather than fake daily fills.

A dense aligned universe means every selected instrument has non-sparse cadence, equal-length return vectors, and the same return calendar. A sparse asset means `src/hyperopen/portfolio/optimizer/domain/history_series.cljs` `cadence-summary` marks the asset with `:sparse? true` because its native intervals are too wide or too infrequent for dense daily covariance assumptions. Mixed-frequency covariance is the current interval-based estimator in `src/hyperopen/portfolio/optimizer/domain/risk_mixed_frequency.cljs`: dense/dense pairs use daily intersections, dense/sparse pairs aggregate dense returns over sparse intervals, and sparse/sparse pairs use shared sparse endpoints.

The relevant files are:

- `src/hyperopen/portfolio/optimizer/domain/math.cljs` for matrix helpers used by covariance code.
- `src/hyperopen/portfolio/optimizer/domain/history_series.cljs` for cadence and risk-estimation metadata.
- `src/hyperopen/portfolio/optimizer/domain/risk.cljs` for top-level covariance routing, PSD repair, and shrinkage metadata.
- `src/hyperopen/portfolio/optimizer/domain/risk_mixed_frequency.cljs` for pairwise sparse estimation and mixed-frequency warnings.
- `src/hyperopen/portfolio/optimizer/application/history_loader/alignment.cljs` and `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs` for the dense return series plus preserved raw rows that feed the risk layer.
- `src/hyperopen/portfolio/optimizer/application/engine/context.cljs` and `src/hyperopen/portfolio/optimizer/application/display_frontier.cljs` for effective constraint encoding.
- `src/hyperopen/portfolio/optimizer/application/engine/payload.cljs` and `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` for warning and metadata exposure.
- `src/hyperopen/portfolio/optimizer/actions/draft.cljs`, `src/hyperopen/portfolio/optimizer/application/progress.cljs`, `src/hyperopen/views/portfolio/optimize/format.cljs`, and `src/hyperopen/views/portfolio/optimize/setup_actions.cljs` for accurate risk-model naming when `:ledoit-wolf-dense` appears in a draft or result.

## Scope Clarification

This plan covers the following work:

- Make an explicit `{:risk-model {:kind :ledoit-wolf-dense}}` request truthful when the selected history is dense and rectangular.
- Keep `:diagonal-shrink` as the default draft value and keep legacy dense and sparse history loaders unchanged.
- Route any sparse `:ledoit-wolf-dense` universe away from full-matrix Ledoit-Wolf while preserving mixed-frequency interval covariance semantics.
- Apply sparse safety caps as runtime effective constraints and expose the route, cap, and estimator metadata through existing warnings and result payload fields.
- Add or extend focused tests that prove dense behavior, sparse routing, cap application, and user-facing warning copy.

This plan does not cover the following work:

- Changing the default risk-model kind from `:diagonal-shrink`.
- Adding a new setup-control button or redesigning the setup model panel.
- Changing history API transport contracts or recomputing aligned returns in the loader.
- Changing return-model logic, Black-Litterman math, or solver algorithm selection.

## Plan of Work

### Milestone 1: Dense Ledoit-Wolf Path And Explicit Request Semantics

Start by locking in the RED phase. Extend `test/hyperopen/portfolio/optimizer/domain/risk_test.cljs` so a dense request using `{:risk-model {:kind :ledoit-wolf-dense}}` returns `:model :ledoit-wolf-dense`, exposes scaled-identity shrinkage metadata, and does not emit the old `:risk-model-renamed` warning. Keep `test/hyperopen/portfolio/optimizer/defaults_test.cljs` and legacy `:ledoit-wolf` tests unchanged except for confirming the default remains `:diagonal-shrink` and old `:ledoit-wolf` requests still map to diagonal shrink with a warning.

Then create `src/hyperopen/portfolio/optimizer/domain/risk_ledoit_wolf.cljs`. The namespace should stay pure and expose one public function, `estimate`. It should accept an ordered vector of equal-length return series plus `periods-per-year`, compute a maximum-likelihood sample covariance `S`, compute `mu = trace(S) / p`, build the scaled-identity target `F = mu I`, compute the Ledoit-Wolf shrinkage coefficient `alpha = clamp(beta-hat / delta-hat, 0, 1)`, and return an annualized covariance `Sigma = alpha F + (1 - alpha) S`. Use the standard Frobenius-norm form where `beta-hat` is the average squared distance between each centered outer product and `S`, and `delta-hat` is the squared distance between `S` and `F`. If the series are not rectangular or contain fewer than two observations, callers must not use this estimator. If `delta-hat` is zero, use `alpha = 0`.

Integrate that estimator in `src/hyperopen/portfolio/optimizer/domain/risk.cljs`. Add `:ledoit-wolf-dense` normalization and keep legacy `:ledoit-wolf` aliasing to `:diagonal-shrink`. Keep `:diagonal-shrink`, `:sample-covariance`, and `:mixed-frequency` intact. Add a dense-rectangular predicate that checks return-series length consistency before calling the new estimator. Update `src/hyperopen/portfolio/optimizer/contracts/specs.cljs`, `src/hyperopen/portfolio/optimizer/actions/draft.cljs`, `src/hyperopen/portfolio/optimizer/application/progress.cljs`, and `src/hyperopen/views/portfolio/optimize/format.cljs` so explicit `:ledoit-wolf-dense` drafts and results are accepted and labeled accurately, even though the default UI can remain unchanged in this ticket.

### Milestone 2: Sparse Routing And Mixed-Frequency Preservation

Keep `src/hyperopen/portfolio/optimizer/domain/risk_mixed_frequency.cljs` as the interval estimator for sparse universes in this first implementation pass. Sparse or mixed pairs must continue to use native interval observations from `:raw-price-series-by-instrument`.

Update `src/hyperopen/portfolio/optimizer/domain/risk.cljs` so any sparse universe still returns `:model :mixed-frequency`, but if the requested risk model was `:ledoit-wolf-dense`, `:diagonal-shrink`, or `:sample-covariance`, the result also records `:requested-model` and emits `:risk-model-overridden-for-mixed-frequency`. Preserve the existing `:risk-estimation` metadata and `:pair-metadata` so later dense-block hybridization can be added without changing payload shape again.

### Milestone 3: Runtime Sparse Safety Caps And Warning Exposure

Use the sparse cadence metadata to derive runtime-only cap recommendations for sparse instruments. The cap policy for this ticket is deliberately concrete: fewer than `2` usable native intervals means `0%` effective max weight; `2` through `7` intervals means `5%`; `8` through `29` means `10%`; `30` through `59` means `20%`; `60` or more means no automatic cap. The interval count should come from `:cadence-by-instrument`, not from aligned daily observations. These caps should be represented as per-instrument effective overrides, not as persisted draft edits.

Implement the sparse caps inside the existing pure `src/hyperopen/portfolio/optimizer/domain/constraints.cljs` encoding path. It accepts request history, derives runtime-only per-asset max-weight overrides, returns warnings alongside encoded bounds, and is used by both the target solver and display-frontier planning.

Expose the routing and cap information through existing warning surfaces instead of inventing a new payload transport. The cap warning code is `:sparse-history-weight-cap-applied` with `:instrument-id`, `:interval-count`, and `:max-weight`. `src/hyperopen/portfolio/optimizer/application/engine/payload.cljs` includes encoded constraint warnings in solved payloads and dedupes infeasible warnings.

### Milestone 4: Focused Tests, Benchmarks When Needed, And Final Gates

Finish by proving the change from the smallest affected surfaces outward. The dense estimator test must pass first. Then the sparse routing and cap tests must pass. If later dense-block hybridization causes noticeable worker slowdown, record a before-and-after timing with `node --test tools/optimizer/solver_spike_benchmark.test.mjs` using one dense fixture and one mixed fixture before adding any further algorithmic complexity.

Run the focused optimizer suites before broadening to repository-wide gates. Only after the focused suites pass should the implementation run `npm run check`, `npm test`, and `npm run test:websocket`. If any of those three commands fail for unrelated reasons, record the exact command, the failure summary, and whether the covariance work itself remained green.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/48dd/hyperopen`.

For Milestone 1, add the new dense estimator test file and update the risk and draft-action tests first, then run:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.domain.risk-test --test=hyperopen.portfolio.optimizer.defaults-test

Expected RED behavior before implementation: `hyperopen.portfolio.optimizer.domain.risk-test` fails because `:ledoit-wolf-dense` is not yet implemented. Expected GREEN behavior after Milestone 1: dense `:ledoit-wolf-dense` requests pass, legacy `:ledoit-wolf` keeps mapping to diagonal shrink, and the default draft remains `:diagonal-shrink`.

For Milestone 2, extend the mixed-frequency risk tests and run:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.domain.risk-test --test=hyperopen.portfolio.optimizer.application.engine-warning-labels-test

Expected GREEN behavior: the mixed-frequency fixture returns `:model :mixed-frequency`, `:requested-model :ledoit-wolf-dense`, existing pair metadata keeps daily and sparse-interval semantics, and result warnings explain the override.

For Milestone 3, add the effective-constraint helper tests and warning-surface tests, then run:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js

Expected GREEN behavior: sparse instruments receive the documented runtime caps without mutating the draft, the solver target problem shows the capped upper bounds, and no optimizer tests fail. The current repo still has one unrelated `hyperopen.api.trading.cancel-request-test` error when the full runner is used.

If Milestone 2 or 3 appears materially slower in the worker, record the benchmark baseline and rerun:

    node --test tools/optimizer/solver_spike_benchmark.test.mjs

Expected behavior: the benchmark command still passes. If manual timing instrumentation is added temporarily during implementation, record the before-and-after numbers in `Surprises & Discoveries` or `Outcomes & Retrospective`.

Before moving this plan out of `active/`, run the required repo gates:

    npm run check
    npm test
    npm run test:websocket

Expected behavior: all three commands exit successfully. If any command fails for an unrelated repo-wide reason, leave the plan active and record the blocker explicitly.

## Validation and Acceptance

Acceptance is met only when all of the following are true:

- Running `node out/test.js --test=hyperopen.portfolio.optimizer.domain.risk-test` from `/Users/barry/.codex/worktrees/48dd/hyperopen` passes, and the dense fixture proves that `{:risk-model {:kind :ledoit-wolf-dense}}` returns `:model :ledoit-wolf-dense` and shrinkage metadata with `:target :scaled-identity`.
- The same focused run proves that a sparse fixture requesting `:ledoit-wolf-dense` does not run full-matrix Ledoit-Wolf. It returns `:model :mixed-frequency`, keeps `:requested-model :ledoit-wolf-dense`, and emits `:risk-model-overridden-for-mixed-frequency`.
- Running the compiled optimizer tests passes and proves that sparse cap tiers of `0%`, `5%`, `10%`, and `20%` are applied as runtime effective constraints only, not by mutating the draft stored in `:portfolio :optimizer :draft`.
- Running `node out/test.js --test=hyperopen.portfolio.optimizer.application.setup-readiness-test --test=hyperopen.portfolio.optimizer.application.engine-warning-labels-test --test=hyperopen.views.portfolio.optimize.results-panel-test --test=hyperopen.views.portfolio.optimize.setup-readiness-panel-test` passes and proves that routing and cap warnings are visible on existing warning surfaces.
- Existing non-Ledoit-Wolf behavior remains intact: `:diagonal-shrink` remains the default in `src/hyperopen/portfolio/optimizer/defaults.cljs`, existing `:sample-covariance` requests still pass their focused tests, and legacy loader tests in `test/hyperopen/portfolio/optimizer/application/history_loader_test.cljs`, `test/hyperopen/portfolio/optimizer/application/history_loader_vaults_test.cljs`, and `test/hyperopen/portfolio/optimizer/application/history_loader_api_v2_test.cljs` stay green if included in broader runs.
- The final implementation either passes `npm run check`, `npm test`, and `npm run test:websocket`, or this plan records the exact unrelated blocker that prevented completion.

## Idempotence and Recovery

The implementation work described here is additive and optimizer-local. Re-running the focused commands is safe. The sparse cap merge must be pure and runtime-only, so a failed experiment should never leave persisted draft data mutated.

If the new estimator math or mixed-frequency metadata breaks worker transport, inspect `src/hyperopen/portfolio/optimizer/instrument_keyed_codec.cljs`. Only update that file if the implementation adds a new instrument-keyed payload map that the worker bridge does not already normalize.

If the runtime cap helper causes solver and frontier divergence, keep the plan active and move the shared merge logic into one pure helper instead of duplicating more conditionals. If a repository-wide gate fails for an unrelated reason, do not hide it by weakening acceptance. Record the blocker in this plan and stop before moving the file out of `active/`.

## Artifacts and Notes

Use the following formulas directly in the implementation and tests so the estimator behavior is reproducible without external references.

For the dense Ledoit-Wolf estimator:

    S = (1 / n) * X^T X
    mu = trace(S) / p
    F = mu * I
    beta-hat = average squared Frobenius distance between each centered outer product and S
    delta-hat = squared Frobenius distance between S and F
    alpha = clamp(beta-hat / delta-hat, 0, 1)
    Sigma = alpha * F + (1 - alpha) * S

Here `X` is the centered `n x p` return matrix, `n` is the number of observations, and `p` is the number of instruments. Annualize `Sigma` only after the shrinkage step.

For sparse runtime caps:

- fewer than `2` usable native intervals -> `0%`
- `2` through `7` -> `5%`
- `8` through `29` -> `10%`
- `30` through `59` -> `20%`
- `60` or more -> no automatic cap

## Interfaces and Dependencies

Create one new pure namespace:

    src/hyperopen/portfolio/optimizer/domain/risk_ledoit_wolf.cljs

It must expose:

    estimate
      [{:keys [series periods-per-year]}]
      => {:covariance [[...]]
          :shrinkage {:kind :ledoit-wolf
                      :target :scaled-identity
                      :shrinkage alpha}
          :sample-count n
          :feature-count p}

Update one existing pure domain namespace:

    src/hyperopen/portfolio/optimizer/domain/constraints.cljs

It exposes the existing `encode-constraints` entry point, now with optional `:history` input:

    encode-constraints
      [{:keys [universe current-weights constraints history]}]
      => {:status ...
          :upper-bounds [...]
          :warnings [{:code :sparse-history-weight-cap-applied ...}]}

No new external dependency is planned. Keep the implementation in pure ClojureScript so it remains worker-safe under the existing optimizer worker build.

Revision note 2026-05-18: Corrected the implementation path back to explicit `:ledoit-wolf-dense` after spec review identified that changing legacy `:ledoit-wolf` in place would silently alter stored scenarios.
