# Optimizer API v2 Return-First Readiness

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Optimizer users selecting assets backed by the Optimizer History API v2 should be judged by usable optimizer return history, not by whether the API also returned enough raw candle or close rows. The API can return `aligned_returns_by_instrument`, which is already the clean daily return vector the optimizer needs for expected return and covariance calculations. After this change, a selected pair such as ETH-USDC and BTC-USDC can run when the API returns aligned returns for both assets, even if one or both series has sparse or missing candle points for chart or diagnostic use.

The user-visible result is that readiness no longer shows confusing `missing-candle-history` or `insufficient-candle-history` warnings when aligned return vectors are present and usable. If the API does not provide usable aligned returns, the UI should say "optimizer return history" instead of "candle history" for API v2 rows, because the optimizer's requirement is returns.

## Context References

Public refs:

- Direct maintainer request on 2026-05-15 after testing two selected optimizer assets and seeing readiness warnings for candle history even though the API response already included close values and daily returns.

Repo artifacts:

- `/hyperopen/docs/exec-plans/active/2026-05-14-optimizer-history-api-v2-integration.md` is the parent API v2 integration plan. It records the decision that API-provided aligned returns should be preferred over recomputing returns from close prices.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define the required active ExecPlan lifecycle.
- `/hyperopen/docs/BROWSER_TESTING.md` governs browser validation for optimizer UI behavior.

Local scratch refs, non-authoritative:

- A screenshot from the maintainer's local session on 2026-05-15 showed readiness text "ETH-USDC: not enough usable candle observations" and "BTC-USDC: no candle history returned" while the v2 integration was being tested.

## Progress

- [x] (2026-05-15 16:51Z) Investigated the current API v2 frontend alignment path and confirmed it checks `series.points` before it checks whether `aligned_returns_by_instrument` is usable.
- [x] (2026-05-15 16:51Z) Authored this active ExecPlan with acceptance criteria, file-level orientation, and concrete tests for the return-first behavior.
- [x] (2026-05-15 17:12Z) Added failing CLJS tests proving API v2 aligned returns make sparse or missing candle points nonblocking and proving missing aligned returns produce return-history warnings.
- [x] (2026-05-15 17:12Z) Implemented return-first eligibility and warning normalization in `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs`.
- [x] (2026-05-15 17:12Z) Updated readiness and selected-row warning copy so API v2 failures talk about return history, not candle history.
- [x] (2026-05-15 17:12Z) Added browser regression coverage for a two-asset API v2 response with aligned returns and sparse or missing candle points.
- [x] (2026-05-15 17:12Z) Ran focused tests, `npm test`, `npm run test:websocket`, Playwright API-v2 regression, delimiter preflight, and `git diff --check`.
- [ ] Completion remains blocked by the repo docs-review gate: `npm run check` fails in `npm run lint:docs` because unchanged canonical docs are 91 days old with a 90-day review cycle.

## Surprises & Discoveries

- Observation: The API v2 aligner currently excludes an instrument with sparse or missing points before aligned returns can make it eligible.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs` `align-api-v2-history-inputs` builds `prepared` by calling `usable-series?` and checking `(count (:points series))` before it computes `use-aligned?` from `aligned_returns_by_instrument`.

- Observation: The optimizer engine consumes return vectors for risk and return math; close prices are secondary.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/domain/risk.cljs` builds covariance from `[:history :return-series-by-instrument]`, and `/hyperopen/src/hyperopen/portfolio/optimizer/domain/returns.cljs` builds expected returns from `[:history :return-series-by-instrument]` or `[:history :expected-return-series-by-instrument]`. `/hyperopen/src/hyperopen/portfolio/optimizer/application/engine/payload.cljs` reads latest close prices only when available for payload enrichment.

- Observation: The UI warning copy still reflects the legacy Hyperliquid candle-loader path.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` maps `:missing-candle-history` to "no candle history returned" and `:insufficient-candle-history` to "not enough usable candle observations." Those messages are accurate for legacy local candle reconstruction, but misleading for API v2 when aligned returns are present.

- Observation: Sequential UI selection prefetches can issue one API v2 history request per newly selected asset instead of one batch for the full selected universe.
  Evidence: The Playwright regression initially expected one ETH+BTC history request after selecting ETH and BTC in sequence, but the stable UI flow emitted an ETH prefetch and then a BTC prefetch. The test now asserts that each request uses API v2 aligned returns and that both selected rows become sufficient after the per-row responses merge.

## Decision Log

- Decision: Treat API v2 aligned returns as the primary optimizer history artifact.
  Rationale: The backend owns proxy stitching, return calendar alignment, and stitch-boundary null return semantics. When `aligned_returns_by_instrument` is present for all selected rows, requiring raw close points duplicates legacy assumptions and can block or warn on data the optimizer does not need.
  Date/Author: 2026-05-15 / Codex

- Decision: Keep legacy candle warning codes for the legacy history loader, but map API v2 return-history failures to API-v2-specific warnings.
  Rationale: The legacy loader really does need candles or vault detail points to compute returns. The API v2 loader should communicate missing or insufficient optimizer return vectors instead. This avoids changing legacy behavior while improving v2 readiness copy.
  Date/Author: 2026-05-15 / Codex

- Decision: Do not synthesize close prices from returns when API points are missing.
  Rationale: Synthetic prices would imply a level and latest mark the API did not provide. The optimizer can run from returns alone; price display and latest-price enrichment should remain absent when the API did not provide point rows.
  Date/Author: 2026-05-15 / Codex

## Outcomes & Retrospective

This section is intentionally open while the plan is active. When implementation completes, record the changed files, validation commands, whether the two-asset readiness screenshot scenario is fixed, and whether the change reduced or increased complexity. The expected complexity change is a small increase in the API v2 anti-corruption layer in exchange for removing a misleading legacy candle dependency from the optimizer readiness path.

Implementation status as of 2026-05-15 17:12Z: the frontend fix is implemented and validated, but this ExecPlan remains active because `npm run check` still fails on unrelated stale-doc review-cycle diagnostics.

Changed behavior:

- API v2 aligned returns are now the primary readiness input when all non-hard-blocked selected rows have usable aligned return vectors.
- API v2 `missing-candle-history` and `insufficient-candle-history` warnings are suppressed for rows with usable aligned returns.
- API v2 rows without usable aligned returns produce `:missing-return-history` or `:insufficient-return-history`, with readiness copy that says optimizer return history instead of candle history.
- The optimizer does not synthesize close prices when API point rows are sparse or missing.

Validation results:

- `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-test --test=hyperopen.portfolio.optimizer.application.setup-readiness-test --test=hyperopen.portfolio.optimizer.application.view-model-test` passed: 29 tests, 159 assertions.
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/optimizer-history-api-v2.spec.mjs --workers=1` passed: 2 tests.
- `npm run lint:delimiters -- --changed` passed for 6 files.
- `git diff --check` passed.
- `npm test` passed: 3931 tests, 21613 assertions.
- `npm run test:websocket` passed: 524 tests, 3043 assertions.
- `npm run check` failed in `npm run lint:docs` after earlier subcommands passed. The stale-doc diagnostics are unchanged canonical docs including `docs/PRODUCT_SENSE.md`, `docs/RELIABILITY.md`, `docs/SECURITY.md`, product specs, and references, all 91 days old against a 90-day review cycle.

The implementation slightly increases the API v2 anti-corruption layer complexity, but removes a misleading dependency on point rows from API v2 readiness. The net behavior is simpler for users: return history determines optimizer readiness.

## Context and Orientation

The portfolio optimizer's frontend state uses stable local instrument IDs such as `perp:BTC` and `spot:ETH/USDC`. The Optimizer History API v2 uses backend IDs such as `hl:perp:BTC`. The frontend sends both: `client_instrument_id` is the local row key, and `instrument_id` is the backend history identity. The API returns `series_by_instrument` and `aligned_returns_by_instrument` keyed by the local `client_instrument_id`.

The API v2 history alignment code lives in `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs`. This is the anti-corruption layer, meaning the pure translation boundary that converts backend JSON-like maps into optimizer-owned maps. Its public entry points are `normalize-discovery`, `with-discovery-metadata`, `normalize-history-bundle`, and `align-api-v2-history-inputs`.

The legacy history loader lives in `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/alignment.cljs`. It still needs candle or vault point rows because it computes returns locally from price or vault detail history. This plan must not change legacy behavior.

The request builder in `/hyperopen/src/hyperopen/portfolio/optimizer/application/request_builder.cljs` calls `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader.cljs` `align-history-inputs`. That function delegates to API v2 when request data contains `:api-v2-history`; otherwise it delegates to the legacy alignment namespace.

The readiness surface is computed by `/hyperopen/src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs`. It inspects the built engine request and turns warning codes into user-facing messages. Selected universe row status is derived by `/hyperopen/src/hyperopen/portfolio/optimizer/application/view_model/universe.cljs`.

The optimizer engine math consumes `:return-series-by-instrument`. It can tolerate missing latest prices because latest close prices are optional payload enrichment. Do not create fake close rows from returns.

Key terms:

- "Aligned returns" means the API's `aligned_returns_by_instrument` map plus `return_calendar`. Every selected asset has a vector of returns with the same length and date positions. This is directly usable by the optimizer.
- "Point rows" means API `series_by_instrument[local-id].points`, usually daily close rows. Point rows may contain `close` and `return`, but they are no longer the primary API v2 eligibility signal when aligned returns are present.
- "Usable return history" means a selected instrument has finite return values for the shared `return_calendar`, and every selected non-blocked instrument has the same number of returns.

## Plan of Work

Milestone 1 adds failing tests that reproduce the maintainer's issue in pure CLJS. Extend `/hyperopen/test/hyperopen/portfolio/optimizer/application/history_loader_api_v2_test.cljs` with a test named `align-api-v2-history-accepts-aligned-returns-with-sparse-or-missing-points-test`. The test should create ETH and BTC universe rows with backend IDs, an API v2 history bundle containing `common_calendar`, `return_calendar`, and `aligned_returns_by_instrument` for both local IDs, and sparse or empty `series_by_instrument` point rows. Include backend warnings such as `missing-candle-history` and `insufficient-candle-history`. The expected result is that both instruments are eligible, `:alignment-source :kind` is `:api-v2-aligned-returns`, `:return-series-by-instrument` contains both vectors, and final `:warnings` does not include candle-history warning codes for those usable aligned-return rows.

In the same test file, add a second failing test named `align-api-v2-history-reports-return-history-warning-when-aligned-returns-are-missing-test`. This test should omit BTC from `aligned_returns_by_instrument` and give BTC no usable point returns. The expected result is that BTC is excluded with a new warning code `:missing-return-history` or `:insufficient-return-history`, not `:missing-candle-history` or `:insufficient-candle-history`.

Milestone 2 changes `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs` so return eligibility is computed before point eligibility. Add helpers with stable names:

    (defn- aligned-return-entry [api-v2-history local-id] ...)
    (defn- usable-aligned-returns? [api-v2-history local-id min-return-observations] ...)
    (defn- all-selected-aligned-returns-usable? [api-v2-history local-ids min-return-observations] ...)
    (defn- api-v2-blocking-warning? [warning] ...)
    (defn- display-data-warning? [warning] ...)
    (defn- return-history-warning [instrument api-v2-history] ...)

Use `min-return-observations` as `(max 1 (dec min-observations*))` so the existing default of 2 price observations still means at least 1 usable return. `usable-aligned-returns?` must require a non-empty `return_calendar`, a return vector for the local ID whose count equals the return calendar count, and at least `min-return-observations` finite numeric returns. Treat `nil`, `NaN`, strings that do not parse to finite numbers, and missing vectors as unusable.

In `align-api-v2-history-inputs`, first normalize the local IDs and series by local ID. Determine whether all selected rows that are not identity-ambiguous and not explicitly rejected by hard backend validation warnings have usable aligned returns. If they do, set all of those rows as eligible even when `series.points` is empty or absent. Build `:return-series-by-instrument` from `aligned_returns_by_instrument`, `:return-calendar` from the API `return_calendar`, and `:return-intervals` from `return-intervals-for-calendar`. Keep `:price-series-by-instrument` only for rows where point data exists; do not synthesize point rows.

Hard backend warnings must still block even if aligned returns appear. Treat at least these warning codes as hard blockers: `:validation-failed`, `:instrument-kind-mismatch`, `:proxy-mapping-unapproved`, and `:proxy-validation-failed`. Do not suppress those warnings. Treat `:missing-candle-history` and `:insufficient-candle-history` from API v2 as display-data warnings. Suppress display-data warnings when aligned returns for that local ID are usable. When aligned returns are not usable, convert display-data warnings into return-history warnings.

Milestone 3 updates readiness copy and row status. In `/hyperopen/src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs`, add `:missing-return-history` and `:insufficient-return-history` to the history blocking or missing/insufficient sets according to the same policy as the current candle codes. Add display messages:

    :missing-return-history
    "<asset>: no optimizer return history returned."

    :insufficient-return-history
    "<asset>: only <observations> usable optimizer return observations; <required> required."

Do not remove the old candle messages because the legacy loader still uses them.

In `/hyperopen/src/hyperopen/portfolio/optimizer/application/view_model/universe.cljs`, add the new return-history warning codes to the selected row status sets so rows with missing API v2 return history show `missing` and rows with insufficient API v2 return history show `insufficient`.

Milestone 4 adds UI-facing regression coverage. Update `/hyperopen/tools/playwright/test/optimizer-history-api-v2.spec.mjs` or add a focused neighboring spec. Route-mock `/v1/optimizer/instruments` for `perp:ETH` and `perp:BTC`. Route-mock `/v1/optimizer/history-bundle` so both assets have aligned returns, ETH has only one point and an `insufficient-candle-history` warning, and BTC has zero points and a `missing-candle-history` warning. After adding both rows in `/portfolio/optimize/new`, assert that both selected rows contain `sufficient`, the readiness panel does not contain `candle history`, and every history request payload has `include_aligned_returns: true` and `proxy_policy: "approved_proxy_allowed"`. The stable UI path may send separate selection-prefetch requests for ETH and BTC; the regression should assert that the union of requested instruments covers both selected rows instead of requiring one batch request.

Milestone 5 updates plan outcomes and runs validation. The implementation must update this ExecPlan's `Progress`, `Surprises & Discoveries`, and `Outcomes & Retrospective` sections with the exact commands and results.

## Concrete Steps

Start in `/Users/barry/.codex/worktrees/6010/hyperopen`.

1. Write the failing CLJS alignment tests in `/hyperopen/test/hyperopen/portfolio/optimizer/application/history_loader_api_v2_test.cljs`.

   The first test should include this core fixture shape, with exact return values chosen for easy assertions:

        (let [universe [{:instrument-id "perp:ETH"
                         :market-type :perp
                         :coin "ETH"
                         :optimizer-history/instrument-id "hl:perp:ETH"}
                        {:instrument-id "perp:BTC"
                         :market-type :perp
                         :coin "BTC"
                         :optimizer-history/instrument-id "hl:perp:BTC"}]
              normalized (api-v2/normalize-history-bundle
                          {:universe universe}
                          {:contract_version "optimizer-history-api-v2"
                           :request_id "rid-aligned-sparse"
                           :dataset_version "dv-return-first"
                           :status "partial"
                           :common_calendar [1000 2000 3000]
                           :return_calendar [2000 3000]
                           :aligned_returns_by_instrument
                           {"perp:ETH" {:instrument_id "hl:perp:ETH"
                                        :returns [0.02 0.03]}
                            "perp:BTC" {:instrument_id "hl:perp:BTC"
                                        :returns [-0.01 0.04]}}
                           :series_by_instrument
                           {"perp:ETH" {:instrument_id "hl:perp:ETH"
                                        :lineage_kind "native"
                                        :series_kind "market_price"
                                        :points [{:time_ms 3000
                                                  :close 2200
                                                  :return 0.03}]
                                        :funding {:status "available"
                                                  :annualized_carry 0.01}
                                        :warnings [{:code "insufficient-candle-history"
                                                    :instrument_id "hl:perp:ETH"}]}
                            "perp:BTC" {:instrument_id "hl:perp:BTC"
                                        :lineage_kind "stitched_native_proxy"
                                        :series_kind "market_price"
                                        :points []
                                        :funding {:status "available"
                                                  :annualized_carry 0.002}
                                        :warnings [{:code "missing-candle-history"
                                                    :instrument_id "hl:perp:BTC"}]}}
                           :warnings []})
              aligned (api-v2/align-api-v2-history-inputs
                       {:universe universe
                        :api-v2-history normalized
                        :min-observations 2})]
          ...)

   Assertions must prove both local IDs are eligible, both return vectors match the API values, candle warnings are absent, and the alignment source is `:api-v2-aligned-returns`.

2. Run the focused failing tests:

        npm run test:runner:generate
        npx shadow-cljs --force-spawn compile test
        node out/test.js --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-test

   Expected before implementation: failure in the new sparse or missing points test because BTC or ETH is excluded by the current `usable-series?` and point-count checks, and failure in the missing aligned return test because the warning code still says candle history.

3. Implement return-first API v2 alignment in `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs`.

   Keep the legacy point-level fallback. The new control flow should read as:

        If every non-hard-blocked selected local ID has usable aligned returns:
          use API aligned returns;
          do not require points for eligibility;
          suppress API v2 display-data candle warnings for those local IDs.
        Else:
          use the current point-level return fallback;
          convert API v2 candle warning codes into return-history warning codes before readiness sees them.

   Preserve `:proxy-history-used`, `:vault-derived-history-used`, `:funding-history-missing`, and `:stale-history` warnings. Preserve hard validation warnings.

4. Update readiness and selected-row warning classification in `/hyperopen/src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` and `/hyperopen/src/hyperopen/portfolio/optimizer/application/view_model/universe.cljs`.

   Add tests in `/hyperopen/test/hyperopen/portfolio/optimizer/application/setup_readiness_test.cljs` for the new messages:

        "Bitcoin: no optimizer return history returned."
        "Ether: only 1 usable optimizer return observations; 2 required."

   If there is an existing selected-row status test for warning codes, extend it so `:missing-return-history` maps to `missing` and `:insufficient-return-history` maps to `insufficient`.

5. Re-run the focused CLJS tests and expect them to pass:

        npm run test:runner:generate
        npx shadow-cljs --force-spawn compile test
        node out/test.js --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-test --test=hyperopen.portfolio.optimizer.application.setup-readiness-test

6. Add or update Playwright regression coverage under `/hyperopen/tools/playwright/test/`. Prefer extending `/hyperopen/tools/playwright/test/optimizer-history-api-v2.spec.mjs` if the existing test remains readable; otherwise create `/hyperopen/tools/playwright/test/optimizer-history-api-v2-return-first.spec.mjs`.

   The route-mocked response must include `aligned_returns_by_instrument` for both ETH and BTC and sparse or missing `points`. Assert that the readiness panel does not contain `candle history` and that selected rows are sufficient.

7. Run the Playwright regression:

        PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/optimizer-history-api-v2.spec.mjs --workers=1

   If a new spec file is created, run that file explicitly as well.

8. Run required validation:

        git diff --check
        npm test
        npm run test:websocket
        npm run check

   The repository currently has a known `npm run check` stale-doc blocker where unchanged canonical docs are over their 90-day review cycle. If that still occurs, record the exact stale-doc output in `Outcomes & Retrospective` and state that the code/test gates passed while the docs-review gate remains blocked.

9. Update this ExecPlan's living sections. Mark completed progress items, add discoveries, and summarize outcomes. Commit the implementation and the plan update together with a message such as:

        git add docs/exec-plans/active/2026-05-15-optimizer-api-v2-return-first-readiness.md src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs src/hyperopen/portfolio/optimizer/application/view_model/universe.cljs test/hyperopen/portfolio/optimizer/application/history_loader_api_v2_test.cljs test/hyperopen/portfolio/optimizer/application/setup_readiness_test.cljs tools/playwright/test/optimizer-history-api-v2.spec.mjs
        git commit -m "Use API v2 aligned returns for optimizer readiness"

## Validation and Acceptance

The pure acceptance criterion is that API v2 aligned returns drive readiness. A CLJS test must fail before implementation and pass after implementation for a response where ETH has only one point, BTC has zero points, and both have aligned returns. The final aligned history must include both instruments in `:eligible-instruments`, both vectors in `:return-series-by-instrument`, `:alignment-source :kind` equal to `:api-v2-aligned-returns`, and no `:missing-candle-history` or `:insufficient-candle-history` warnings.

The negative acceptance criterion is that missing aligned returns still blocks or warns clearly. A CLJS test must prove that an asset without usable aligned returns and without point-level fallback is excluded with `:missing-return-history` or `:insufficient-return-history`.

The UI acceptance criterion is that the optimizer setup route can add ETH and BTC from a route-mocked API v2 response, both selected rows show sufficient history, and readiness does not contain "candle history" when optimizer returns are available. The readiness panel may still show valid nonblocking warnings such as approved proxy history, stale history, vault-derived history, or funding history missing.

The required validation commands are:

    npm test
    npm run test:websocket
    PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/optimizer-history-api-v2.spec.mjs --workers=1
    git diff --check
    npm run check

If `npm run check` fails only on the known stale-doc review-cycle gate, that is a documented external blocker, not a failure of this change. All code and browser tests must still pass.

## Idempotence and Recovery

All implementation steps are additive or local edits to pure frontend code and tests. Re-running `npm run test:runner:generate` is safe; it rewrites `/hyperopen/test/test_runner_generated.cljs` based on current test namespaces. Re-running the route-mocked Playwright test is safe because it uses an isolated static server and mocked API routes.

If the API v2 alignment refactor breaks legacy tests, first confirm that `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader.cljs` still delegates to `api_v2.cljs` only when `:api-v2-history` is present. Legacy candle-loader tests should continue to exercise `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/alignment.cljs` unchanged.

If the UI still shows candle warning text after pure alignment tests pass, inspect whether the warning is coming from top-level `:warnings` in `api-v2-history` rather than series-level warnings. The suppression/conversion policy must apply to both top-level API warnings and per-series warnings for local IDs whose aligned returns are usable.

## Artifacts and Notes

The user-facing symptom that motivated this work was a readiness panel with:

    ETH-USDC: not enough usable candle observations.
    INSUFFICIENT-CANDLE-HISTORY

    BTC-USDC: no candle history returned.
    MISSING-CANDLE-HISTORY

The desired user-facing behavior for the same data shape, when `aligned_returns_by_instrument` contains usable vectors for both rows, is:

    Optimizer history is loaded for the selected assets.

No candle-history warning should be visible for those rows. If return vectors are absent or too short, use return-history wording instead:

    ETH-USDC: not enough usable optimizer return observations.
    INSUFFICIENT-RETURN-HISTORY

    BTC-USDC: no optimizer return history returned.
    MISSING-RETURN-HISTORY

## Interfaces and Dependencies

No new third-party dependencies are required. Use existing ClojureScript helpers in `/hyperopen/src/hyperopen/portfolio/optimizer/coercion.cljs`, especially `parse-number`, `finite-number?`, and `non-blank-text` where appropriate.

The final API v2 history map produced by `align-api-v2-history-inputs` must still satisfy the optimizer history contract in `/hyperopen/src/hyperopen/portfolio/optimizer/contracts/specs.cljs`: it must contain `:calendar`, `:return-calendar`, `:eligible-instruments`, `:excluded-instruments`, `:price-series-by-instrument`, `:return-series-by-instrument`, `:return-intervals`, `:funding-by-instrument`, `:warnings`, and `:freshness`.

The `:return-series-by-instrument` keys must remain local optimizer IDs such as `perp:BTC`. The `:price-series-by-instrument` keys, when present, must also remain local optimizer IDs. Backend IDs such as `hl:perp:BTC` must remain metadata only and must not replace local IDs in optimizer request history.

The final implementation should keep these public function names stable:

- `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs` `normalize-discovery`
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs` `with-discovery-metadata`
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs` `normalize-history-bundle`
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs` `align-api-v2-history-inputs`

Any new helper functions may stay private unless tests need to exercise them through the public alignment function.

## Revision Notes

- 2026-05-15 17:12Z / Codex: Implemented the plan and updated it with the per-selection-prefetch browser discovery, validation evidence, and the remaining stale-doc `npm run check` blocker. This note keeps the active ExecPlan self-contained for the next reader.
