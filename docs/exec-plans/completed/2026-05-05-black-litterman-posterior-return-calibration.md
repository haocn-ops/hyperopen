---
owner: portfolio
status: completed
created: 2026-05-05
source_of_truth: false
tracked_issue: hyperopen-wj5y
---

# Black-Litterman Posterior Return Calibration

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current while work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`. The live `bd` issue for this work is `hyperopen-wj5y`; the `bd` issue is historical local tracker context from the active phase.

## Purpose / Big Picture

A user running the Portfolio Optimizer in `Use my views` mode should see standalone frontier expected returns that are consistent with the return beliefs and historical baseline inputs they entered. The motivating report is a Black-Litterman optimization where BTC was changed to `20%` expected return after a negative past-year baseline, the other assets were left unchanged, and the result showed BTC around `62%` and Hyperliquidity Provider (HLP) around `1.86%` on the frontier. Those numbers are implausible because the UI path makes it look like absolute views are annualized return inputs, while the engine currently lets covariance-implied fallback priors dominate them.

After this change, `Use my views` should anchor Black-Litterman posterior returns to the same baseline expected-return estimator used elsewhere in the optimizer, then blend user views against that baseline with confidence measured in the same covariance units as the model. If a user leaves HLP's absolute view at its approximately `20%` one-year baseline, HLP's standalone frontier marker should remain near that baseline rather than collapsing to a low covariance-implied prior. If a user changes BTC to `20%`, the BTC posterior should stay in the defensible range between its baseline and the entered view, never jumping above the entered view to roughly `62%` because of missing market-cap data.

## Progress

- [x] (2026-05-05T20:45Z) Traced the reported behavior to the Black-Litterman posterior return path, not the chart renderer or the vault one-year history estimator.
- [x] (2026-05-05T20:45Z) Created and claimed `bd` bug `hyperopen-wj5y` for this implementation.
- [x] (2026-05-05T20:45Z) Created this active ExecPlan with root-cause evidence and a test-first implementation path.
- [x] (2026-05-06T10:05Z) Added failing domain coverage proving absolute views blend from baseline expected returns instead of covariance-implied fallback priors.
- [x] (2026-05-06T10:16Z) Added failing application coverage proving a BTC/HLP/Growi scenario no longer produces BTC around `62%` or HLP around `1.86%` when absolute views are baseline-anchored.
- [x] (2026-05-06T10:28Z) Implemented baseline-prior support and covariance-scaled Omega in the Black-Litterman domain.
- [x] (2026-05-06T10:42Z) Routed baseline expected returns from the engine context into the Black-Litterman posterior.
- [x] (2026-05-06T10:51Z) Updated setup preview tests and implementation so prior-return preview values come from baseline expected-return inputs, not prior weights.
- [x] (2026-05-06T11:09Z) Ran focused optimizer tests and the required gates: `npm run check`, `npm test`, and `npm run test:websocket`.

## Surprises & Discoveries

- Observation: The frontier chart is not inventing the bad values. It plots `:frontier-overlays`, which are built from the same `expected-returns` vector used by objective scoring.
  Evidence: `src/hyperopen/portfolio/optimizer/application/engine/payload.cljs` calls `frontier-overlays/overlay-series` with `:expected-returns expected-returns`, and `src/hyperopen/portfolio/optimizer/domain/frontier_overlays.cljs` assigns each standalone marker's `:expected-return` from that vector.

- Observation: In Black-Litterman mode, the historical baseline expected-return estimate is computed but not used as the actual expected-return input.
  Evidence: `src/hyperopen/portfolio/optimizer/application/engine/context.cljs` calls `base-return-estimate`, then calls `black-litterman/posterior-returns`, and stores `(:expected-returns-by-instrument posterior)` as the expected-return result. The base estimate is kept only under `:decomposition-by-instrument` and warnings.

- Observation: Missing market-cap and current-portfolio priors cause the Black-Litterman prior weights to fall back to equal weights.
  Evidence: `src/hyperopen/portfolio/optimizer/infrastructure/prior_data.cljs` resolves `:fallback-equal-weight` when market caps are incomplete and current portfolio weights are empty or zero. The screenshot includes `missing-market-cap-prior` and `missing-current-portfolio-prior` warnings, which matches this fallback path.

- Observation: The current Black-Litterman prior return is `risk-aversion * covariance * priorWeights`, so an equal-weight fallback can give a high-volatility asset like BTC a very high implied return before any user view is applied.
  Evidence: `src/hyperopen/portfolio/optimizer/domain/black_litterman.cljs` defines `implied-equilibrium-returns` as `math/scalar-vec risk-aversion (math/mat-vec covariance prior-weights)`. A quick local numeric reproduction with high BTC variance and equal weights produced a BTC prior of about `63%`, matching the reported `62%` class of failure.

- Observation: View confidence is currently dimensionless and too weak relative to the covariance matrix.
  Evidence: `src/hyperopen/portfolio/optimizer/domain/black_litterman.cljs` builds Omega directly from `:confidence-variance`, while editor actions store values such as `0.5` for medium confidence. The Black-Litterman equations compare Omega to `tau * covariance`; using raw `0.5` makes views far weaker than intended when annualized covariance entries are much smaller than `0.5`.

- Observation: HLP's preferred one-year expected-return series is already preserved by the vault history loader.
  Evidence: `src/hyperopen/portfolio/optimizer/application/history_loader/alignment.cljs` emits `:expected-return-series-by-instrument`, and `src/hyperopen/portfolio/optimizer/domain/returns.cljs` prefers that series before the risk-aligned `:return-series-by-instrument`. The HLP `1.86%` result is therefore downstream of baseline estimation, in the Black-Litterman posterior replacement.

- Observation: The focused CLJS compile path succeeded, but the current local test runner exited before running tests because `node_modules` is missing the expected Lucide package entrypoint.
  Evidence: `npx shadow-cljs --force-spawn compile test` completed with zero warnings, then `node out/test.js` failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`. If this recurs during implementation, refresh dependencies with `npm install` and retry before treating it as a source failure.

- Observation: Refreshing local dependencies resolved the Lucide entrypoint failure without changing package metadata.
  Evidence: `npm install` restored the missing module path. `package.json` and lockfile state were unchanged after the install; `npm` reported existing dependency vulnerabilities but did not block tests.

- Observation: The application regression is clearer as a focused namespace than as another large `engine-test` case.
  Evidence: `test/hyperopen/portfolio/optimizer/application/black_litterman_calibration_test.cljs` builds the reported three-asset universe with a stub solver and asserts standalone overlay expected returns plus Black-Litterman diagnostics directly.

## Decision Log

- Decision: Treat this as a Black-Litterman calibration bug, not as a chart, solver, or vault-history bug.
  Rationale: The bad numbers are already present in the expected-return vector before the chart renders. The solver and SVG only consume that vector.
  Date/Author: 2026-05-05 / Codex

- Decision: Use the existing baseline expected-return estimator as the default prior return for the optimizer's `Use my views` Black-Litterman path.
  Rationale: The UI pre-fills absolute view values from the baseline estimator and users reasonably expect unchanged assets to keep those expected returns. Falling back to equal-weight covariance-implied prior returns when market caps are missing creates misleading values for mixed perp/vault universes, especially high-volatility assets.
  Date/Author: 2026-05-05 / Codex

- Decision: Keep `implied-equilibrium-returns` as a lower-level domain helper, but make `posterior-returns` accept explicit `:prior-returns`.
  Rationale: This preserves the classical Black-Litterman formula for callers that intentionally want covariance-implied returns, while allowing the application path to pass a more trustworthy prior return vector.
  Date/Author: 2026-05-05 / Codex

- Decision: Derive Omega from view confidence and `tau * covariance`, rather than using raw `:confidence-variance` as an absolute variance.
  Rationale: Omega is a variance of view uncertainty. It must be in the same units as `tau * Sigma`. Using raw values like `0.5` silently weakens views and lets priors dominate even when the UI says the user is providing an absolute expected-return belief.
  Date/Author: 2026-05-05 / Codex

- Decision: Preserve legacy `:confidence-variance` input by translating it to confidence as `1 - confidence-variance` when `:confidence` is absent.
  Rationale: Persisted or older view data should still produce a valid posterior, while new calibration code uses a confidence value that has a direct monotonic interpretation.
  Date/Author: 2026-05-06 / Codex

- Decision: Reuse engine context return-input helpers for the Black-Litterman setup preview.
  Rationale: The preview should show the same baseline and posterior expected-return inputs as the optimization path, and should not maintain a separate approximation that treats prior weights as prior returns.
  Date/Author: 2026-05-06 / Codex

## Outcomes & Retrospective

Implementation landed. The domain now accepts explicit prior returns, reports whether they were provided or covariance-implied, and calibrates Omega from view confidence in `tau * covariance` units. The engine passes baseline expected returns into the Black-Litterman posterior, preserving missing market-cap/current-portfolio warnings as weight-prior diagnostics instead of letting fallback equal weights dictate expected returns. The setup preview now reads baseline and posterior expected-return inputs from engine context helpers rather than displaying prior weights as returns.

Validation evidence:

    npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.domain.black-litterman-test
    4 tests, 11 assertions, 0 failures, 0 errors.

    npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.black-litterman-calibration-test --test=hyperopen.portfolio.optimizer.application.black-litterman-preview-test
    4 tests, 16 assertions, 0 failures, 0 errors.

    npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.domain.black-litterman-test --test=hyperopen.portfolio.optimizer.domain.returns-test --test=hyperopen.portfolio.optimizer.application.return-inputs-test --test=hyperopen.portfolio.optimizer.application.black-litterman-preview-test --test=hyperopen.portfolio.optimizer.application.black-litterman-calibration-test --test=hyperopen.portfolio.optimizer.application.engine-test --test=hyperopen.portfolio.optimizer.application.history-loader-test --test=hyperopen.views.portfolio.optimize.black-litterman-views-panel-test
    52 tests, 227 assertions, 0 failures, 0 errors.

    npm run check
    Exited 0.

    npm test
    3775 tests, 20800 assertions, 0 failures, 0 errors.

    npm run test:websocket
    524 tests, 3043 assertions, 0 failures, 0 errors.

No browser QA was required because implementation did not touch `src/hyperopen/views/portfolio/optimize/**` or browser-test tooling. The only user-visible behavior change is the optimizer data feeding the existing frontier and setup preview surfaces.

## Context and Orientation

The Portfolio Optimizer code is split into a pure optimizer bounded context and view namespaces. Pure domain math lives under `src/hyperopen/portfolio/optimizer/domain/**`. Application orchestration that converts an engine request into risk estimates, return estimates, solver plans, and display payloads lives under `src/hyperopen/portfolio/optimizer/application/**`. The setup and results UI lives under `src/hyperopen/views/portfolio/optimize/**`.

The relevant route is `/portfolio/optimize/new` for setup and `/portfolio/optimize/:scenario-id?otab=recommendation` for saved or solved results. The user-facing chart in the screenshot is the efficient frontier chart. Its standalone overlays show each selected asset as a point at that asset's standalone volatility and expected return. These markers are generated from engine output, not calculated in the renderer.

Black-Litterman is a return-model mode selected under `[:portfolio :optimizer :draft :return-model :kind]` with value `:black-litterman`. A user view is stored under `[:portfolio :optimizer :draft :return-model :views]`. An absolute view is a belief about one asset's annualized expected return, such as `BTC expected return 20% annualized`. A relative view is a belief about one asset outperforming or underperforming another by an annualized spread.

In the current engine path, `src/hyperopen/portfolio/optimizer/application/engine/context.cljs` first builds a baseline expected-return estimate with `returns/estimate-expected-returns`. That baseline estimator already handles market candles, vault expected-return histories, elapsed-time sparse annualization, and funding carry. When the return model is Black-Litterman, the code computes a Black-Litterman posterior and uses it as the expected-return result. The existing posterior derives its prior returns from `covariance * priorWeights`, where prior weights come from `src/hyperopen/portfolio/optimizer/infrastructure/prior_data.cljs`.

The key problem is that prior weights are not prior returns. When market-cap data is missing for vaults and no current portfolio weights exist, prior weights fall back to equal weights. Multiplying annualized covariance by equal weights can create a large implied return for the high-volatility instrument, which is why BTC can show around `62%` even after the user entered `20%`. The second problem is that Omega, the uncertainty assigned to user views, is stored as values like `0.5` but the equations compare it against `tau * covariance`; that makes medium-confidence views much weaker than intended.

## Plan of Work

First, add domain-level regression coverage in `test/hyperopen/portfolio/optimizer/domain/black_litterman_test.cljs`. The first failing test should call `black-litterman/posterior-returns` with explicit `:prior-returns [-0.13 0.20 0.20]`, a covariance matrix where BTC has high variance, equal prior weights, and three absolute views where BTC is `0.20`, Growi is unchanged at `0.20`, and HLP is unchanged at `0.20`. The expected behavior is that unchanged absolute views remain near their prior/view values and BTC does not jump above its entered view because of covariance-implied prior returns. The test should assert that BTC is less than or equal to `0.205`, HLP is within a small tolerance of `0.20`, and diagnostics include `:prior-return-source :provided`.

Second, add a confidence-scaling regression in the same domain test namespace. Use one absolute BTC view with prior return `-0.13`, view return `0.20`, and the same high-variance covariance matrix. Run the posterior twice, once with `:confidence 0.25` and once with `:confidence 0.75`. Assert that the high-confidence posterior is closer to `0.20` than the low-confidence posterior and that both results remain between the prior and the view. This proves confidence is monotonic and measured in model units.

Third, update `src/hyperopen/portfolio/optimizer/domain/black_litterman.cljs`. Change `posterior-returns` so it accepts an optional `:prior-returns` vector. If `:prior-returns` is present and has the same count as `:instrument-ids`, use it as `pi`; otherwise fall back to the existing `implied-equilibrium-returns` behavior. Add a private helper that builds Omega from views, `tau-sigma`, and view rows. For each view row `p`, compute view variance as `p * tauSigma * p'`, clamp it above a small epsilon such as `1e-9`, derive confidence from `:confidence` when present or from legacy `:confidence-variance` as `1 - confidence-variance`, clamp confidence to `[0.000001, 0.999999]`, and compute Omega as `viewVariance * ((1 - confidence) / confidence)`. Keep legacy `:confidence-variance` as a fallback input only for persisted views that lack `:confidence`.

Fourth, add application-level regression coverage in `test/hyperopen/portfolio/optimizer/application/engine_test.cljs` or a new focused test namespace if the existing file becomes too large. Build a request with three instruments matching the reported shape: `perp:BTC`, a Growi vault, and `Hyperliquidity Provider (HLP)`. Use synthetic history that makes BTC's baseline approximately `-13%`, Growi's baseline positive, and HLP's preferred expected-return history approximately `20%`. Use a Black-Litterman return model with absolute views for all three assets, changing BTC to `0.20` and leaving the vault views at their baseline values. Use a stub solver that returns fixed weights, then call `engine/run-optimization`. Assert that `[:frontier-overlays :standalone]` contains BTC with expected return at or below `20.5%` and HLP near `20%`. Also assert that warnings may still include missing market-cap prior warnings, proving the fix no longer depends on complete market-cap data.

Fifth, update `src/hyperopen/portfolio/optimizer/application/engine/context.cljs`. In the Black-Litterman branch of `expected-return-result`, compute the baseline expected-return vector in the same instrument order as `risk-result`. Pass that vector to `black-litterman/posterior-returns` as `:prior-returns`, and preserve `:decomposition-by-instrument` from the base result. Add diagnostics fields that make the model auditable, such as `:prior-return-source :baseline-expected-returns`, `:weight-prior-source`, `:view-count`, and `:tau`. Do not change risk estimation, covariance construction, solver selection, constraints, or frontier chart rendering.

Sixth, update setup preview behavior if it still treats prior weights as prior returns. `src/hyperopen/portfolio/optimizer/application/black_litterman_preview.cljs` currently names values from `[:black-litterman-prior :weights-by-instrument]` as `prior-return`. Change the preview model so `:prior-return` uses the same baseline expected-return inputs that the engine will pass into the posterior. Update `test/hyperopen/portfolio/optimizer/application/black_litterman_preview_test.cljs` so it expects return percentages, not prior weights. The preview should remain a lightweight setup aid and must not run the solver.

Seventh, update view copy only if tests show the current labels are misleading after the model change. The likely safe copy is already present: `Views adjust expected returns only. Risk (covariance) is unchanged.` If a new diagnostics summary is added to `src/hyperopen/views/portfolio/optimize/inputs_tab.cljs` or `results_panel.cljs`, keep it concise and avoid implying that Black-Litterman changes covariance.

## Concrete Steps

Run all commands from `/Users/barry/.codex/worktrees/c4dd/hyperopen`.

1. Refresh generated test runner:

       npm run test:runner:generate

   Expected result: the command exits `0` and prints a generated namespace count.

2. Add the domain RED tests in `test/hyperopen/portfolio/optimizer/domain/black_litterman_test.cljs`, then run:

       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.portfolio.optimizer.domain.black-litterman-test

   Expected before implementation: the compile succeeds and the new tests fail because `posterior-returns` ignores `:prior-returns` and raw Omega lets the covariance-implied prior dominate.

3. Implement `:prior-returns` and covariance-scaled Omega in `src/hyperopen/portfolio/optimizer/domain/black_litterman.cljs`, then rerun:

       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.portfolio.optimizer.domain.black-litterman-test

   Expected after implementation: the domain namespace passes. The existing `posterior-returns-combine-prior-and-views-test` may need its expected values updated only if it exercises the new Omega calibration with confidence; do not weaken its assertion to merely check non-nil output.

4. Add the engine regression in `test/hyperopen/portfolio/optimizer/application/engine_test.cljs` or a focused new namespace, then run:

       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.portfolio.optimizer.application.engine-test

   Expected before application wiring: the test fails because the engine does not pass baseline prior returns into `posterior-returns`.

5. Update `src/hyperopen/portfolio/optimizer/application/engine/context.cljs`, then rerun the engine test command. Expected after implementation: the reported shape passes and the result diagnostics identify baseline expected returns as the prior return source.

6. Update preview tests and preview implementation if necessary:

       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.portfolio.optimizer.application.black-litterman-preview-test --test=hyperopen.views.portfolio.optimize.black-litterman-views-panel-test

   Expected result: setup preview and editor prefill tests pass and still show user-facing percent values.

7. Run the focused optimizer suite:

       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.portfolio.optimizer.domain.black-litterman-test --test=hyperopen.portfolio.optimizer.domain.returns-test --test=hyperopen.portfolio.optimizer.application.return-inputs-test --test=hyperopen.portfolio.optimizer.application.black-litterman-preview-test --test=hyperopen.portfolio.optimizer.application.engine-test --test=hyperopen.portfolio.optimizer.application.history-loader-test

   Expected result: all listed namespaces pass. If `node out/test.js` fails before tests with `Cannot find module 'lucide/dist/esm/icons/external-link.js`, run `npm install` once and retry the exact command. Record that dependency refresh in this plan's `Surprises & Discoveries`.

8. Because this is a code change, run the required gates:

       npm run check
       npm test
       npm run test:websocket

   Expected result: all three commands exit `0`. If an unrelated active ExecPlan docs lint or environment issue blocks `npm run check`, record the exact blocker here and still run `npm test` plus `npm run test:websocket`.

9. Browser QA is not automatically required unless source changes touch `src/hyperopen/views/portfolio/optimize/**` or browser-test tooling. If preview or result UI source changes, run the smallest relevant Playwright command first, then browser cleanup:

       npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs
       npm run browser:cleanup

   Expected result: the Playwright test exits `0`, and browser cleanup exits `0`.

## Validation and Acceptance

Acceptance is satisfied only when the new tests prove the reported shape cannot recur.

The domain test must prove that `posterior-returns` can use explicit baseline prior returns. A high-volatility BTC covariance entry must not produce a BTC standalone expected return above the `20%` absolute view when the provided prior return is below the view. Unchanged absolute views for Growi and HLP must remain close to their provided baseline/view values.

The confidence test must prove monotonic blending. With the same prior and view, a higher confidence value must produce a posterior closer to the view than a lower confidence value, and both posterior values must remain between the prior and the view.

The application test must prove that `engine/run-optimization` uses baseline expected returns as the Black-Litterman prior return input. It should exercise a universe with BTC plus vault assets and missing market-cap/current-portfolio priors. The result may still warn about missing priors, but the standalone frontier overlay must not show BTC around `62%` or HLP around `1.86%` when their absolute views are `20%`.

The preview test must prove setup preview values are actual baseline/posterior expected returns, not prior weights labeled as returns.

The final verification must include `npm run check`, `npm test`, and `npm run test:websocket` unless a documented unrelated environment or active-plan blocker prevents one of them.

## Idempotence and Recovery

The plan is safe to execute incrementally. Start with tests, keep source edits small, and rerun the focused namespace after each change. If the domain change causes matrix inversion failures, keep the failing tests and inspect Omega construction first; do not change solver code or chart code to hide the failure. If a test reveals that covariance-scaled Omega is still too weak or too strong, adjust only the confidence-to-Omega scale and record the calibration decision in `Decision Log`.

Do not run `git pull --rebase` or `git push` for this work unless the user explicitly requests remote sync in the current session. Do not revert unrelated user changes if the worktree is dirty. If local dependency state blocks tests with a missing Node module, refresh dependencies with `npm install` rather than editing source imports as part of this bug.

If implementation touches visible optimizer UI, follow `/hyperopen/docs/BROWSER_TESTING.md`, run the relevant Playwright command, and end with `npm run browser:cleanup` so no browser-inspection sessions remain open.

## Artifacts and Notes

Root-cause numeric reproduction from investigation:

    With equal prior weights, high BTC annualized variance, and current pi = covariance * equalWeights,
    BTC prior can be about 63%.
    With raw Omega values such as 0.5, a 20% absolute BTC view remains weak,
    leaving posterior BTC near the reported 62% class of value.

Important files to inspect before editing:

    src/hyperopen/portfolio/optimizer/domain/black_litterman.cljs
    src/hyperopen/portfolio/optimizer/application/engine/context.cljs
    src/hyperopen/portfolio/optimizer/domain/returns.cljs
    src/hyperopen/portfolio/optimizer/application/black_litterman_preview.cljs
    test/hyperopen/portfolio/optimizer/domain/black_litterman_test.cljs
    test/hyperopen/portfolio/optimizer/application/engine_test.cljs
    test/hyperopen/portfolio/optimizer/application/black_litterman_preview_test.cljs

Current local validation blocker observed before this plan was written:

    npx shadow-cljs --force-spawn compile test
    Build completed. (1590 files, 1589 compiled, 0 warnings)

    node out/test.js --test=...
    Error: Cannot find module 'lucide/dist/esm/icons/external-link.js'

This appears to be local dependency state rather than the Black-Litterman source failure. Retry after `npm install` if it recurs.

## Interfaces and Dependencies

At completion, `hyperopen.portfolio.optimizer.domain.black-litterman/posterior-returns` must accept the current keys plus an optional `:prior-returns` vector:

    {:instrument-ids [...]
     :covariance [[...]]
     :prior-weights [...]
     :prior-returns [...]
     :risk-aversion 1
     :tau 0.05
     :views [...]
     :prior-source :fallback-equal-weight}

When `:prior-returns` is valid, diagnostics should include:

    {:prior-source <weight prior source>
     :prior-return-source :provided
     :view-count <n>
     :tau <tau>}

When `:prior-returns` is absent or invalid, the function should preserve current fallback behavior and diagnostics should make that explicit with `:prior-return-source :implied-equilibrium`.

`hyperopen.portfolio.optimizer.application.engine.context/expected-return-result` must pass the baseline expected-return vector into `posterior-returns`. It must continue to expose `:decomposition-by-instrument` from the baseline estimator so funding and history diagnostics remain visible in result payloads.

`hyperopen.portfolio.optimizer.application.black-litterman-preview/build-preview` should report baseline expected returns as `:prior-return` or rename the field if a clearer term is introduced. It must not display prior weights as returns.

Revision note, 2026-05-05T20:45Z: Initial active ExecPlan created after root-cause tracing of the BTC `62%` and HLP `1.86%` frontier overlay bug. The plan chooses baseline expected returns as the UI path's Black-Litterman prior return and covariance-scaled Omega as the confidence fix.
