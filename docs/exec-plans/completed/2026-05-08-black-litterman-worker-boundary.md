---
owner: platform
status: completed
created: 2026-05-08
source_of_truth: false
tracked_issue: hyperopen-mnib
---

# Fix Black-Litterman Worker Boundary View Weights

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current while work proceeds. This document follows `.agents/PLANS.md`.

Tracked issue: `hyperopen-mnib` ("Fix Black-Litterman worker view weights").

## Purpose / Big Picture

Users can enter Black-Litterman views on the optimizer setup page and see the setup preview respond correctly, but the solved result can still look identical to a baseline highest-Sharpe run. The suspected cause is that Black-Litterman view weights are string-keyed before worker serialization and keyword-keyed after worker decoding, so the optimizer math cannot find each view's instrument weight by string instrument id. After this change, a high-confidence positive BTC view will survive the web-worker boundary, change the effective expected-return vector used by the solver, and produce result overlays/frontier data that match the run's Black-Litterman inputs.

The observable behavior is: create two otherwise identical optimizer runs, one baseline max-sharpe and one Black-Litterman max-sharpe with a positive BTC absolute view. The Black-Litterman result must expose a positive or materially changed BTC effective expected return in the result payload and overlays, and the page must not show a stale historical frontier as if it were actionable.

## Progress

- [x] (2026-05-08 23:32Z) Created tracked bug `hyperopen-mnib` for this plan.
- [x] (2026-05-08 23:32Z) Confirmed current worker normalization covers known instrument-keyed maps but not `[:return-model :views * :weights]`.
- [x] (2026-05-08 23:32Z) Confirmed current result rendering can render solved stale results with allocation and frontier while only showing a stale banner.
- [x] (2026-05-08 23:39Z) Added a wire-level RED test for keywordized Black-Litterman view weights and observed the expected failure: normalized `:weights` remained `{:perp:BTC 1}` instead of `{"perp:BTC" 1}`.
- [x] (2026-05-08 23:41Z) Normalized Black-Litterman view weight keys at the worker boundary and reran `npm test`; CLJS suite passed with 3800 tests and 20930 assertions.
- [x] (2026-05-08 23:47Z) Added worker/engine-level regression coverage for keywordized Black-Litterman view weights and explicit effective-return instrumentation; observed RED on missing `:expected-returns-by-instrument`, then added payload and wire normalization support.
- [x] (2026-05-08 23:56Z) Added Black-Litterman validation that blocks zero view rows instead of silently collapsing to the prior; domain and engine regressions pass, and invalid warnings are ordered before generic baseline warnings.
- [x] (2026-05-08 23:49Z) Added explicit expected-return-by-instrument instrumentation to solved result payloads and worker message normalization; `npm test` passed with 3801 tests and 20936 assertions.
- [x] (2026-05-09 00:18Z) Changed the recommendation tab so stale solved results render a blocking run-again state instead of the actionable allocation/frontier body; updated stale-route and unsaved-route tests. `npm test` passed with 3803 tests and 20953 assertions.
- [x] (2026-05-09 00:43Z) Extended the Black-Litterman Playwright regression so the browser-backed worker run must expose positive BTC effective return in both `:expected-returns-by-instrument` and the standalone overlay. The full focused spec passed: 5 tests.
- [x] (2026-05-09 00:47Z) Required gates passed: `npm run check`, `npm test`, `npm run test:websocket`, and `npm run browser:cleanup`.
- [x] (2026-05-09 00:48Z) Move this ExecPlan to `docs/exec-plans/completed/` after acceptance criteria pass.

## Surprises & Discoveries

- Observation: The setup preview and the actual optimization run are separated by a worker serialization boundary.
  Evidence: `src/hyperopen/portfolio/optimizer/infrastructure/worker_client.cljs` posts optimizer requests with `clj->js`; `src/hyperopen/portfolio/optimizer/worker.cljs` decodes message payloads with `js->clj ... :keywordize-keys true` and then calls `wire/normalize-worker-boundary`.
- Observation: Existing worker boundary normalization already accounts for many instrument-keyed maps.
  Evidence: `src/hyperopen/portfolio/optimizer/infrastructure/wire.cljs` normalizes paths such as `[:current-portfolio :by-instrument]`, `[:history :return-series-by-instrument]`, `[:black-litterman-prior :weights-by-instrument]`, and result payload weight maps, but there is no recursive handling for `[:return-model :views * :weights]`.
- Observation: The Black-Litterman math currently reads view rows by string instrument id.
  Evidence: `src/hyperopen/portfolio/optimizer/domain/black_litterman.cljs` builds each row with `(get-in view [:weights instrument-id])`, and `instrument-id` comes from the risk model as strings such as `"perp:BTC"`.
- Observation: The result chart is likely faithfully displaying the run payload rather than independently pulling historical returns.
  Evidence: `src/hyperopen/portfolio/optimizer/application/engine/context.cljs` passes one `expected-returns` vector into both `objectives/build-solver-plan` and `display-frontier/build-plans`; `src/hyperopen/portfolio/optimizer/application/engine/payload.cljs` builds overlays from that same vector.

## Decision Log

- Decision: Fix the key-shape problem in `wire/normalize-worker-boundary`, not in the Black-Litterman math lookup.
  Rationale: The worker boundary is already the canonical place for repairing key shapes after `js->clj :keywordize-keys true`. Keeping the math layer string-keyed preserves the existing domain model and avoids making every instrument lookup tolerate multiple key types.
  Date/Author: 2026-05-08 / Codex
- Decision: Treat zero Black-Litterman view rows as blocking invalid input.
  Rationale: A user-entered view with no matching instrument exposure is not a harmless warning. Proceeding returns the prior and makes the app look like it ignored the user's view.
  Date/Author: 2026-05-08 / Codex
- Decision: Gate full recommendation rendering on `current-result?` in the scenario detail route rather than changing the low-level results panel contract first.
  Rationale: `results_panel.cljs` is a reusable renderer and has tests for stale banners. The route owns whether the retained run matches the current draft, so it is the right layer to decide whether stale allocation/frontier output is actionable.
  Date/Author: 2026-05-08 / Codex

## Context and Orientation

The portfolio optimizer is a ClojureScript app with optimizer work delegated to a web worker. A web worker is a separate browser thread. Values sent to the worker are converted from ClojureScript data into JavaScript objects with `clj->js`, then converted back with `js->clj`. The option `:keywordize-keys true` turns JavaScript object keys into Clojure keywords, which is useful for ordinary record fields such as `:status` but dangerous for maps keyed by dynamic instrument ids such as `"perp:BTC"`.

The optimizer request begins in the main app and is posted by `src/hyperopen/portfolio/optimizer/infrastructure/worker_client.cljs`. The worker receives it in `src/hyperopen/portfolio/optimizer/worker.cljs`, decodes it, calls `wire/normalize-worker-boundary`, and then calls `engine/run-optimization-async`. Boundary normalization lives in `src/hyperopen/portfolio/optimizer/infrastructure/wire.cljs`.

Black-Litterman is a return model. In this repository, it blends baseline expected returns with user-entered views and confidence values. Its math lives in `src/hyperopen/portfolio/optimizer/domain/black_litterman.cljs`. The optimizer engine path that calls it lives in `src/hyperopen/portfolio/optimizer/application/engine/context.cljs`. The solved result payload is assembled in `src/hyperopen/portfolio/optimizer/application/engine/payload.cljs`.

The results page is rendered by `src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs` and `src/hyperopen/views/portfolio/optimize/results_panel.cljs`. `scenario_detail_view.cljs` already computes `current-result?` and `stale?` with `src/hyperopen/portfolio/optimizer/application/run_identity.cljs`.

## Plan of Work

The implementation has five milestones. Each milestone has its own focused tests and can be reviewed independently.

Milestone 1 proves the worker-boundary key-shape bug. Add a wire-level regression in `test/hyperopen/portfolio/optimizer/infrastructure/wire_test.cljs` that constructs a Black-Litterman return model with `:weights {(keyword "perp:BTC") 1}` and expects `wire/normalize-worker-boundary` to return `{"perp:BTC" 1}` at `[:return-model :views 0 :weights]`. Add a higher-level regression in `test/hyperopen/portfolio/optimizer/application/black_litterman_calibration_test.cljs` or `test/hyperopen/portfolio/optimizer/worker_test.cljs` that normalizes a request with keywordized view-weight keys and then runs the engine, asserting the BTC standalone overlay becomes positive after a +20% BTC view. The wire-level test should fail before the fix.

Milestone 2 implements boundary normalization. Modify `src/hyperopen/portfolio/optimizer/infrastructure/wire.cljs` to add a helper that maps over every Black-Litterman view and applies `stringify-instrument-keyed-map` to `:weights` when present. The existing static `instrument-keyed-map-paths` vector cannot express a wildcard over each view, so do this as a recursive function rather than trying to add a static path.

The intended implementation shape is:

    (defn- normalize-black-litterman-view-weights
      [value]
      (update-existing-in
       value
       [:return-model :views]
       (fn [views]
         (mapv (fn [view]
                 (update-existing-in view
                                     [:weights]
                                     stringify-instrument-keyed-map))
               views))))

    (defn normalize-worker-boundary
      [value]
      (-> value
          normalize-wire-values
          normalize-instrument-keyed-maps
          normalize-black-litterman-view-weights))

After this milestone, both the wire-level test and the worker/engine-level test should pass. The domain math should remain string-keyed and unchanged except for the hardening milestone below.

Milestone 3 hardens Black-Litterman input validation. Modify `src/hyperopen/portfolio/optimizer/domain/black_litterman.cljs` to detect rows that are all zero after mapping view weights onto the request instrument order. Add helpers named `zero-view-row?` and `invalid-view-row-warning`. A zero row means a view has no matching instrument exposure in the current universe. For any zero row, return a Black-Litterman result with `:status :invalid`, the baseline prior returns for diagnostic visibility, and a warning with code `:black-litterman-view-has-no-matching-instrument`. Do not silently compute a posterior from zero rows.

The warning shape must be:

    {:code :black-litterman-view-has-no-matching-instrument
     :view-id (:id view)
     :instrument-ids (keys (:weights view))}

Modify `src/hyperopen/portfolio/optimizer/application/engine/context.cljs` so an invalid return result creates an infeasible solver plan instead of solving. The solver plan should be:

    {:status :infeasible
     :reason :invalid-return-model
     :warnings (:warnings return-result)
     :problems []}

Keep `:return-result` in the optimization context so `payload/infeasible-payload` includes the warning. Add domain tests in `test/hyperopen/portfolio/optimizer/domain/black_litterman_test.cljs` for a zero row and add an engine test in `test/hyperopen/portfolio/optimizer/application/engine_test.cljs` that verifies a bad Black-Litterman view returns `{:status :infeasible :reason :invalid-return-model}` with the warning code.

Milestone 4 adds explicit result instrumentation. Modify `src/hyperopen/portfolio/optimizer/application/engine/payload.cljs` so solved payloads include:

    :expected-returns-by-instrument (zipmap instrument-ids expected-returns)

Place it near `:target-weights-by-instrument` and `:current-weights-by-instrument`. Modify `src/hyperopen/portfolio/optimizer/infrastructure/wire.cljs` to include `[:payload :expected-returns-by-instrument]` and `[:expected-returns-by-instrument]` in `instrument-keyed-map-paths`, because solved worker results also cross a `clj->js` / `js->clj` boundary. Update `test/hyperopen/portfolio/optimizer/application/black_litterman_calibration_test.cljs` to assert `(pos? (get-in result [:expected-returns-by-instrument btc-id]))` for the +20% BTC view. Update `test/hyperopen/portfolio/optimizer/infrastructure/wire_test.cljs` to assert result payload expected-return map keys survive worker message normalization.

Milestone 5 blocks actionable stale result rendering. Modify `src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs` so `recommendation-tab` receives `current-result?` in addition to `stale?`. If there is a solved result and `current-result?` is true, render `results-panel/results-panel` as today. If there is a solved result and `current-result?` is false, render a blocking stale-state panel with data role `portfolio-optimizer-recommendation-stale-blocked`, explanatory copy, and a button with `:data-role "portfolio-optimizer-recommendation-run-again"` dispatching `[[:actions/run-portfolio-optimizer-from-draft]]`. Do not render `portfolio-optimizer-frontier-panel` or `portfolio-optimizer-target-exposure-table` in that stale branch.

Update `tab-body` to pass `current-result?`, and update `scenario-detail-view` where it already computes `current-result?`. Add or update tests in `test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs`. The existing `portfolio-optimizer-scenario-detail-marks-clean-mismatched-result-stale-test` is the right place to extend: it should assert the stale blocked panel exists, `portfolio-optimizer-frontier-panel` is nil, `portfolio-optimizer-target-exposure-table` is nil, and clicking the run-again button dispatches `[[:actions/run-portfolio-optimizer-from-draft]]`.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/eb1c/hyperopen`.

First, materialize the RED tests. Edit `test/hyperopen/portfolio/optimizer/infrastructure/wire_test.cljs` and add a test named `normalize-worker-boundary-stringifies-black-litterman-view-weights-test`:

    (deftest normalize-worker-boundary-stringifies-black-litterman-view-weights-test
      (let [decoded-id (keyword "perp:BTC")
            normalized (wire/normalize-worker-boundary
                        {:return-model {:kind "black-litterman"
                                        :views [{:id "view-1"
                                                 :kind "absolute"
                                                 :instrument-id "perp:BTC"
                                                 :return 0.2
                                                 :confidence 0.75
                                                 :weights {decoded-id 1}}]}})]
        (is (= {"perp:BTC" 1}
               (get-in normalized [:return-model :views 0 :weights])))
        (is (= :black-litterman
               (get-in normalized [:return-model :kind])))))

Run:

    npm test

Expected before the fix: the new wire test fails because the weight map key remains keywordized. If dependency resolution fails with `repo1.maven.org` DNS errors, record that in this ExecPlan under `Surprises & Discoveries` and continue with the remaining edits; otherwise continue only after seeing the RED failure.

Second, implement `normalize-black-litterman-view-weights` in `src/hyperopen/portfolio/optimizer/infrastructure/wire.cljs` as described in Milestone 2. Run `npm test` again and expect the new wire test to pass.

Third, add a higher-level regression for normalized Black-Litterman requests. Prefer extending `test/hyperopen/portfolio/optimizer/application/black_litterman_calibration_test.cljs` because it already has a deterministic +20% BTC view fixture. Add `hyperopen.portfolio.optimizer.infrastructure.wire` to the namespace requires. Add a test that uses the same request shape but replaces every `:weights {btc-id 1}` with `:weights {(keyword btc-id) 1}` before calling `wire/normalize-worker-boundary` and `engine/run-optimization`. Assert that the BTC standalone overlay expected return is positive and that `:black-litterman-diagnostics :view-count` remains the number of views. After Milestone 4, also assert `:expected-returns-by-instrument` is positive for BTC.

Fourth, add zero-row hardening tests in `test/hyperopen/portfolio/optimizer/domain/black_litterman_test.cljs`. The test should call `black-litterman/posterior-returns` with `:instrument-ids ["perp:BTC"]` and one view whose `:weights {"perp:ETH" 1}`. It should expect `:status :invalid`, warning code `:black-litterman-view-has-no-matching-instrument`, and diagnostic `:view-count 1`. Then implement the validation in `src/hyperopen/portfolio/optimizer/domain/black_litterman.cljs` and wire invalid return-result handling in `src/hyperopen/portfolio/optimizer/application/engine/context.cljs`. Run `npm test` and expect the domain and engine tests to pass.

Fifth, add result instrumentation in `src/hyperopen/portfolio/optimizer/application/engine/payload.cljs` and result-key normalization in `src/hyperopen/portfolio/optimizer/infrastructure/wire.cljs`. Extend the wire test and calibration test. Run `npm test` and expect all ClojureScript tests to pass.

Sixth, change stale route rendering in `src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs` and extend `test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs`. Run `npm test` and expect the stale-route assertions to pass. Existing direct `results_panel.cljs` tests may continue to assert the stale banner behavior because the route, not the reusable panel, now blocks stale actionability.

Seventh, run the browser regression that exercises Black-Litterman setup through worker-backed optimization:

    PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs

Expected: all tests in that spec pass. If the spec does not compare BL versus baseline results, add a follow-up Playwright test in `tools/playwright/test/optimizer-black-litterman-views.spec.mjs` only after the lower-level deterministic tests are green. The Playwright assertion should verify that the result page exposes a non-negative or positive BTC effective expected return after the +20% BTC view. Use existing `data-role` hooks wherever possible rather than screenshot matching.

Finally run the required gates:

    npm run check
    npm test
    npm run test:websocket
    npm run browser:cleanup

Expected: all commands exit 0. If browser tests started a dev server owned by this session, stop it with `npm run dev:kill`; do not kill a server if it was already running for the user and is needed for manual review.

## Validation and Acceptance

The implementation is accepted when these behaviors are demonstrable:

1. `wire/normalize-worker-boundary` converts `[:return-model :views 0 :weights]` from `{(keyword "perp:BTC") 1}` to `{"perp:BTC" 1}` while still keywordizing enum values such as `"black-litterman"` into `:black-litterman`.
2. A normalized Black-Litterman request with a +20% BTC absolute view produces a positive or materially changed BTC expected return in both `:expected-returns-by-instrument` and the standalone frontier overlay.
3. A Black-Litterman view with no matching instrument exposure returns an infeasible optimizer result with reason `:invalid-return-model` and warning code `:black-litterman-view-has-no-matching-instrument`.
4. When a retained solved result does not match the current draft, the recommendation route renders `portfolio-optimizer-recommendation-stale-blocked` and does not render `portfolio-optimizer-frontier-panel` or `portfolio-optimizer-target-exposure-table`.
5. `npm run check`, `npm test`, `npm run test:websocket`, and the focused Black-Litterman Playwright spec pass.

## Idempotence and Recovery

All proposed changes are additive or local edits to optimizer boundary normalization, Black-Litterman validation, payload instrumentation, and scenario detail rendering. Re-running `npm run test:runner:generate` is safe; it rewrites `test/test_runner_generated.cljs` deterministically. If a test command fails because dependencies cannot be resolved from Maven Central, do not change code to work around the network problem. Record the exact error under `Surprises & Discoveries`, verify with any already-installed local build artifacts if available, and rerun the command when DNS is restored.

If the stale UI change causes many component tests to fail, keep `results_panel.cljs` behavior unchanged and confine the block to `scenario_detail_view.cljs`. That preserves reusable panel tests while making the user-facing route safer.

## Interfaces and Dependencies

The public function `hyperopen.portfolio.optimizer.infrastructure.wire/normalize-worker-boundary` remains the worker-boundary normalizer for both requests and result messages. Its contract expands to include Black-Litterman view weight maps and solved payload expected-return maps.

The public function `hyperopen.portfolio.optimizer.domain.black-litterman/posterior-returns` keeps returning a map. It may now return `:status :invalid` when user-entered views cannot be mapped to the current universe. Callers must inspect `:status` before treating `:expected-returns-by-instrument` as a solved posterior.

The solved payload contract from `hyperopen.portfolio.optimizer.application.engine.payload/solved-payload` gains `:expected-returns-by-instrument`. This field is intentionally redundant with overlays because it makes effective return inputs directly inspectable by the UI, diagnostics, tests, and expert reviewers.

The route-level renderer `hyperopen.views.portfolio.optimize.scenario-detail-view/recommendation-tab` should render actionable results only for a current solved run. Stale solved results remain visible as a blocking state with a run-again action, not as an old frontier/allocation body.

## Artifacts and Notes

The expert review package created before this plan is `/Users/barry/Desktop/hyperopen-optimizer-review-2026-05-08.zip`. It includes the screenshots showing the identical Black-Litterman and highest-Sharpe frontiers. This plan is based on that review and on direct inspection of the current worktree.

At plan creation, the latest relevant commits on this branch were:

    6aca4cda Keep optimizer result button after run
    c79857d7 Guard optimizer results by request identity
    b6b699cb Prevent stale optimizer weights navigation
    4a771a42 Fix Black-Litterman pending view runs

## Outcomes & Retrospective

Implemented. The fix adds a small amount of boundary-normalization and validation code, but removes the silent failure mode where user-entered Black-Litterman views collapsed to the baseline after worker serialization. Effective return inputs are now explicit in the solved payload, zero-row Black-Litterman views become blocking invalid input, and stale scenario-detail recommendations no longer render old allocation/frontier output as actionable.

Validation passed on 2026-05-09:

    PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs
    npm run check
    npm test
    npm run test:websocket
    npm run browser:cleanup

## Revision Notes

2026-05-08 / Codex: Created this active ExecPlan from the expert recommendations and direct code inspection. The plan is intentionally scoped to the worker-boundary bug, Black-Litterman hardening, stale result blocking, and diagnostics needed to make effective return inputs observable.
