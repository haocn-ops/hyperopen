# Make Ledoit-Wolf the default optimizer risk model

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. Keep it self-contained and leave at least one unchecked progress item while the plan remains active.

## Purpose / Big Picture

New optimizer scenarios default their risk model to `:diagonal-shrink` (labeled "Stabilized Covariance" in the UI). On thin/stale data — common for HIP-3 perps and young instruments — diagonal-shrink leaves each asset's sample variance untouched and only de-noises correlations, so a single name with a spuriously-low variance estimate can dominate the "minimum volatility" solution. After this change, new scenarios default to `:ledoit-wolf-dense`, a true data-driven Ledoit-Wolf estimator that shrinks the whole covariance (variances included) toward a scaled-identity target with an intensity that rises automatically as assets grow relative to history. A user creating a fresh optimization will see the "Ledoit-Wolf" risk model selected by default and get better-conditioned covariances out of the box.

This was verified live before writing the plan (see Surprises): re-running a 34-asset spectate scenario with `:ledoit-wolf-dense` cut the dominant position from +990% to +523%, improved the covariance condition number from ~4,827 to ~380, and raised Effective N from 0.010 to 0.024 — without rerouting away from true Ledoit-Wolf.

## Context References

Public refs:

- Direct user request on 2026-06-22: "can we implement the default now being Ledoit-Wolf?", following a live A/B that showed Ledoit-Wolf de-concentrates the degenerate solution.

Repo artifacts:

- This reverses a prior, deliberate decision recorded in `/hyperopen/docs/exec-plans/active/2026-05-18-optimizer-covariance-shrinkage.md` ("Keep `:diagonal-shrink` as the default risk model … Default drafts stay stable"). That decision was made when changing defaults was out of scope for an estimator-correctness ticket, not because diagonal-shrink is better; it is now superseded.
- `/hyperopen/docs/exec-plans/completed/2026-04-25-portfolio-optimizer-v1-remediation.md` explains why `:diagonal-shrink` became the "honest default" (the old `:ledoit-wolf` keyword was mislabeled and actually ran diagonal-shrink). A real `:ledoit-wolf-dense` now exists, so defaulting to it is honest.
- `/hyperopen/AGENTS.md` requires `npm run check`, `npm test`, `npm run test:websocket` when code changes.

Local scratch refs, non-authoritative:

- None.

## Progress

- [x] (2026-06-22) Mapped every default/fallback site and confirmed `:ledoit-wolf-dense` is already an allowed contract kind (`contracts/specs.cljs`), has a progress label (`application/progress.cljs`), and a preset entry (`actions/draft_options.cljs`). Confirmed sparse universes route to mixed-frequency with the same `override-warning` for any full-matrix model, so the default switch does not add warnings on sparse universes.
- [x] (2026-06-22) Changed the user-facing default to `:ledoit-wolf-dense` in `defaults.cljs` (`default-draft`) and `application/request_builder.cljs` (`default-risk-model`). Left the deep estimator-level fallbacks in `domain/risk.cljs` as diagonal-shrink.
- [x] (2026-06-22) Updated `defaults_test.cljs` and `state/app_defaults_test.cljs` to expect `:ledoit-wolf-dense`. The full suite surfaced no other breakage — every other test that references diagonal-shrink sets it explicitly, and the net-min tests that build from `default-draft` and run real OSQP passed with the Ledoit-Wolf covariance (no OSQP fallback, no LDL errors).
- [x] (2026-06-22) Ran gates. `npm test` = `Ran 4787 tests`, `0 failures, 0 errors`. `npm run test:websocket` = `0 failures, 0 errors`. `lint:namespace-sizes` and the `app`/`portfolio`/`portfolio-optimizer-worker` compiles pass; `lint:docs` flags only the pre-existing `docs/FRONTEND.md` staleness (this ExecPlan passes its structure checks), the same blocker as the prior optimizer work.
- [ ] Live confirmation pending a dev-server restart: the nREPL session that proved this in-app (port 65033) was stopped, so re-confirm that a fresh draft reports `:risk-model {:kind :ledoit-wolf-dense}` once the dev server is running, then move this plan to `completed/`.

## Surprises & Discoveries

- Observation: Ledoit-Wolf improves the portfolio but does NOT open up the efficient frontier's visible span. In the live A/B the frontier stayed a tiny near-vertical cluster (it just moved from ~124% to ~204% volatility). So this change is justified by covariance quality / de-concentration, not by frontier visibility (which is a separate chart-framing + sweep-range issue).
  Evidence: live nREPL read of `[:portfolio :optimizer :last-successful-run :result]` before/after switching the risk model.
- Observation: The sparse-universe path is unaffected by the default. `risk_mixed_frequency/override-warning` fires for any non-`:mixed-frequency` model on sparse universes, and `estimate-risk-model` routes diagonal-shrink and ledoit-wolf-dense to mixed-frequency identically; only the `:requested-model` label and the optional dense-block estimator differ.

## Decision Log

- Decision: Flip the user-facing default risk model from `:diagonal-shrink` to `:ledoit-wolf-dense`, superseding the 2026-05-18 "keep diagonal-shrink default" decision.
  Rationale: A true Ledoit-Wolf now exists, so the default is honest; it is materially better for the thin/degenerate data this product commonly sees (live A/B evidence); and sparse-universe behavior is unchanged. The accepted tradeoff: when variance estimates ARE reliable and genuinely heterogeneous, Ledoit-Wolf's scaled-identity target flattens real volatility differences more than diagonal-shrink, which can produce blander estimates. This is deemed the better default for a crypto/perp universe where thin history is the common case; diagonal-shrink remains one click away.
  Date/Author: 2026-06-22 / Claude.

- Decision: Leave the estimator-level `(or risk-model {:kind :diagonal-shrink})` fallbacks in `domain/risk.cljs` unchanged.
  Rationale: Those are last-resort safety nets reached only if a risk model is somehow nil deep in estimation (never on normal flows, since the draft and request builder both supply a model). Diagonal-shrink is the safest fallback there because it never routes or depends on dense-matrix preconditions.
  Date/Author: 2026-06-22 / Claude.

## Outcomes & Retrospective

New optimizer scenarios now default to a true Ledoit-Wolf covariance. The change was two default-value edits plus two test-assertion updates; the suite is green (4787 + 545 tests, 0 failures) and the production builds compile. The blast radius was far smaller than the ~40 test files that mention diagonal-shrink, because nearly all of them set the risk model explicitly — only the two default-assertion tests cared. Complexity is unchanged (no new code paths; the Ledoit-Wolf estimator already existed and was already a selectable option).

The one thing to keep in view: this trades robustness on thin data (the common case, and the reason for the change) against fidelity on good data (where the scaled-identity target flattens genuine volatility differences more than diagonal-shrink). Diagonal-shrink remains one click away in the risk-model picker for scenarios with reliable, heterogeneous variances. A future refinement could make the default adaptive — pick Ledoit-Wolf only when conditioning is poor / Effective N collapses — but that was out of scope for this simple default flip.

Remaining: the in-app live confirmation (blocked by the stopped dev server) — tracked as the open Progress item.

## Context and Orientation

The optimizer's default draft lives in `/hyperopen/src/hyperopen/portfolio/optimizer/defaults.cljs` (`default-draft`, which seeds `:risk-model`). The request builder `/hyperopen/src/hyperopen/portfolio/optimizer/application/request_builder.cljs` has a `default-risk-model` fallback used when a request lacks one. The estimator `/hyperopen/src/hyperopen/portfolio/optimizer/domain/risk.cljs` `estimate-risk-model` dispatches on `(:kind risk-model)`: `:diagonal-shrink` shrinks only off-diagonals; `:ledoit-wolf-dense` calls `domain/risk_ledoit_wolf.cljs` (scaled-identity target, data-driven shrinkage). Any sparse asset routes to `domain/risk_mixed_frequency.cljs` regardless of the requested model. The setup UI `/hyperopen/src/hyperopen/views/portfolio/optimize/setup_model_controls.cljs` renders the four risk-model buttons and highlights whichever matches the draft's `:risk-model :kind`, so no UI edit is needed — switching the default flips which button is pre-selected.

## Plan of Work

Edit `defaults.cljs` `default-draft`: `:risk-model {:kind :diagonal-shrink}` -> `{:kind :ledoit-wolf-dense}`. Edit `request_builder.cljs` `default-risk-model`: `{:kind :diagonal-shrink}` -> `{:kind :ledoit-wolf-dense}`. Then run the suite and fix surfaced assertions: update `defaults_test.cljs` and `state/app_defaults_test.cljs` to expect `:ledoit-wolf-dense`; for any other test that built a draft from the default and asserts covariance-dependent results, decide per-test whether to pin `:diagonal-shrink` (incidental use) or update the expectation (genuinely about the default).

## Validation and Acceptance

Behavioral acceptance: opening a brand-new optimization shows "Ledoit-Wolf" pre-selected as the risk model, and a fresh draft in app state reports `:risk-model {:kind :ledoit-wolf-dense}`. Test acceptance: `npm test` and `npm run test:websocket` pass with zero failures; `npm run check` passes every step the change touches. Record the final counts here.

## Idempotence and Recovery

The change is two default-value edits plus test-assertion updates; re-applying is safe, and reverting the two source values restores the prior default. No data migrations. Existing saved scenarios are unaffected because they carry an explicit `:risk-model`.
