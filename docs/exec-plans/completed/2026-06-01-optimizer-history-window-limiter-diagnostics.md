# Display Optimizer History Window And Limiting Asset

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Optimizer users need to understand how much historical return data the recommendation actually used. The result trust rail already explains conditioning, diversification, weight stability, and warnings, but it does not say whether the optimizer used a full shared year, a shortened shared window, or a weekday-only proxy calendar. After this change, the optimized result view will show the shared return history as return observations and elapsed calendar days, and it will name the asset that limits the shared history when that can be derived from available source data.

The user-visible proof is a result page whose right-side "How much to trust this" rail contains a new `History Used` row. For a mixed crypto plus weekday proxy case, it should read in substance: `252 returns over 365 days`, with supporting text naming the proxy asset as the limiter because it has the fewest source return observations or a sparser source calendar. For a late-starting asset, the same row should name that asset as the limiter because it starts later than the rest.

## Context References

Public refs:

- Direct user request on 2026-06-01: display how many days of returns are used and which asset limits the optimizer to that amount.

Repo artifacts:

- `/hyperopen/AGENTS.md` requires ExecPlans for complex or risky UI and optimizer work and requires `npm run check`, `npm test`, and `npm run test:websocket` when code changes.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define the ExecPlan contract.
- `/hyperopen/docs/exec-plans/active/2026-05-14-optimizer-history-api-v2-integration.md` explains that the API v2 backend owns proxy stitching and aligned return calendars.
- `/hyperopen/docs/exec-plans/active/2026-05-15-optimizer-api-v2-return-first-readiness.md` explains that usable API v2 `aligned_returns_by_instrument` can drive optimizer readiness even when raw candle points are sparse or absent.
- `/hyperopen/docs/exec-plans/active/2026-05-31-optimizer-targeted-history-fallback.md` explains the current API v2 plus targeted legacy fallback history path.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-06-01 13:16Z) Created this active ExecPlan from the direct user request and current code inspection.
- [x] (2026-06-01 13:24Z) Added RED tests for API v2 weekday proxy limiting history, legacy late-starting history, engine summary fields, and result rail copy.
- [x] (2026-06-01 13:25Z) Verified RED with focused test run: 14 expected failures, 0 errors, all for missing metadata or missing UI row.
- [x] (2026-06-01 13:30Z) Implemented source-coverage and limiting-asset metadata in optimizer history alignment.
- [x] (2026-06-01 13:30Z) Extended solved result payload history summary with return-observation, elapsed-day, and limiter fields.
- [x] (2026-06-01 13:30Z) Rendered the new `History Used` trust rail row without changing optimizer math.
- [x] (2026-06-01 13:30Z) Focused GREEN run passed for API v2 alignment, legacy alignment, engine payload, and result panel rendering.
- [x] (2026-06-01 13:56Z) Run focused tests, relevant browser/UI coverage, and repository gates.

## Surprises & Discoveries

- Observation: The solved result already carries `:history-summary` with `:return-observations`, `:oldest-common-ms`, `:latest-common-ms`, `:age-ms`, and `:stale?`.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/application/engine/payload.cljs` function `history-summary`.

- Observation: API v2 aligned returns are accepted when every selected asset has a finite return vector matching the API `:return-calendar`, even if candle points are sparse or missing.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/alignment.cljs` functions `usable-aligned-returns?` and `all-selected-aligned-returns-usable?`.

- Observation: Weekend gaps should not be represented as zero-return days in the frontend. The current API v2 plan explicitly says the backend owns proxy stitching and boundary null-return semantics.
  Evidence: `/hyperopen/docs/exec-plans/active/2026-05-14-optimizer-history-api-v2-integration.md` says not to treat `nil` returns as zero and not to recompute returns across stitched boundaries.

- Observation: Expected-return annualization is already interval-aware when interval metadata matches the return series, so a Friday-to-Monday proxy return can be annualized over roughly three elapsed days instead of one daily observation.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/domain/returns.cljs` function `interval-observations`.

## Decision Log

- Decision: Display both return observations and elapsed calendar days.
  Rationale: A proxy asset can provide about 252 weekday returns across about 365 calendar days. Showing only "days" hides the difference between sample count and elapsed lookback; showing only observations makes a full-year proxy look like a shortened 252-day window. The UI should make both facts visible.
  Date/Author: 2026-06-01 / Codex

- Decision: Compute the limiting asset from source coverage when source rows are available, and do not invent a limiter when only opaque aligned returns are available.
  Rationale: The frontend can honestly identify a limiter from source history start, end, and observation counts. When API v2 returns aligned return vectors but no raw points for one or more selected assets, the frontend can still show the shared return count and elapsed days, but it cannot prove which source asset constrained the backend alignment.
  Date/Author: 2026-06-01 / Codex

- Decision: Treat "limiting asset" as the asset that most reduces the shared usable return set, not necessarily the asset with the shortest raw calendar span.
  Rationale: For proxy assets, the limiter may have the same oldest and latest calendar dates as crypto assets but fewer source return observations because it has no weekend trading. For late listings, the limiter is usually the asset whose source history starts later. For stale or discontinued data, it may be the asset whose source history ends earlier.
  Date/Author: 2026-06-01 / Codex

- Decision: Add metadata only; do not change optimizer return, covariance, or solver math in this plan.
  Rationale: The current math already consumes aligned return series and interval metadata. This work is explanatory and should not alter portfolio weights except through tests exposing an existing bug.
  Date/Author: 2026-06-01 / Codex

## Outcomes & Retrospective

Implemented. The change is bounded to derived history metadata, solved result payload shaping, and the existing result trust rail. No optimizer return, covariance, or solver math changed.

Validation completed on 2026-06-01:

- Focused RED before implementation: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-test --test=hyperopen.portfolio.optimizer.application.history-loader-test --test=hyperopen.portfolio.optimizer.application.engine-test --test=hyperopen.views.portfolio.optimize.results-panel-test` failed as expected with 14 missing-metadata or missing-UI-row assertions and 0 errors.
- Focused GREEN after implementation: `npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-test --test=hyperopen.portfolio.optimizer.application.history-loader-test --test=hyperopen.portfolio.optimizer.application.engine-test --test=hyperopen.views.portfolio.optimize.results-panel-test` passed 37 tests and 255 assertions.
- Final focused namespaces: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.history-window-test --test=hyperopen.portfolio.optimizer.application.engine-history-window-test --test=hyperopen.views.portfolio.optimize.results-panel-test` passed 9 tests and 95 assertions.
- Targeted browser regression: `npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs -g "portfolio optimizer recommendation chart shows minimum variance frontier overlays and honest target weights"` passed 1 test. The scenario now disables live optimizer history API v2 for deterministic fixtures and asserts the visible `History Used` trust row.
- Governed UI design review: `npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app` passed for `review-375`, `review-768`, `review-1280`, and `review-1440`, with all visual-evidence, native-control, styling-consistency, interaction, layout-regression, and jank-perf passes green. The standard route-state blind spots remain for hover, active, disabled, and loading states that are not present by default.
- Required gate `npm run check` passed.
- Required gate `npm test` passed 4106 tests and 22647 assertions.
- Required gate `npm run test:websocket` passed 527 tests and 3067 assertions.
- Repository hygiene `git diff --check` passed.
- Browser cleanup: `npm run browser:cleanup` stopped the design-review browser-inspection session.

Remaining backend metadata gap: when the optimizer receives only opaque aligned returns with no source rows for one or more selected assets, the UI can still display the shared return observations and elapsed days, but it intentionally shows that the limiting asset is unavailable instead of guessing.

## Context and Orientation

The optimizer result payload is assembled in `/hyperopen/src/hyperopen/portfolio/optimizer/application/engine/payload.cljs`. The helper `history-summary` currently reads `request :history` and emits the small summary stored under result key `:history-summary`. That result is displayed in several UI surfaces.

The right rail shown in the user screenshot is rendered by `/hyperopen/src/hyperopen/views/portfolio/optimize/results_diagnostics_rail.cljs` function `trust-diagnostics-rail`. It currently renders rows for `Conditioning`, `Diversification`, and `Weight Stability`, then warnings and an expandable `More Diagnostics` section. The new `History Used` row belongs in this same list because it is a trust and data-quality explanation, not a control.

The API v2 history alignment path lives in `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/alignment.cljs`. It normalizes API v2 history into optimizer-owned history fields such as `:calendar`, `:return-calendar`, `:return-series-by-instrument`, `:return-intervals`, `:raw-price-series-by-instrument`, `:cadence-by-instrument`, `:warnings`, `:freshness`, and `:alignment-source`.

The legacy history alignment path lives in `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/alignment.cljs`. It builds the same optimizer-owned history shape from browser-side candle, funding, and vault data.

In this plan, "return observation" means one usable return value in the optimizer's aligned return matrix. If the optimizer has four assets and `:return-calendar` has 252 timestamps, every selected asset has a 252-value return vector for the optimization run. "Elapsed calendar days" means the sum of `:dt-days` in the return intervals actually used, or the difference between the first and last common history timestamps when interval metadata is incomplete. "Source coverage" means each eligible asset's own available history before intersection with the other selected assets.

## Plan of Work

First, add pure tests around history alignment. In `/hyperopen/test/hyperopen/portfolio/optimizer/application/history_loader_api_v2_test.cljs`, add a test that creates a crypto asset with daily source points including weekends and a proxy asset with weekday source points over the same calendar span. The API v2 fixture must include `:common_calendar`, `:return_calendar`, and `:aligned_returns_by_instrument` matching the weekday proxy calendar. The expected aligned history should include a new metadata map, tentatively named `:history-window`, with the return observation count, elapsed return days, and a primary limiting instrument equal to the proxy asset. This test should fail before implementation because `:history-window` does not exist.

Second, add legacy-path coverage if the same helper is shared by both aligners. In `/hyperopen/test/hyperopen/portfolio/optimizer/application/history_loader_test.cljs`, add or extend a test with two candle histories where one starts later. The expected `:history-window` should identify the late-starting asset with reason `:starts-later`. This protects non-API fallback behavior and keeps the limiter algorithm source-agnostic.

Third, implement a small pure helper under the history-loader boundary. Prefer a new namespace `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/window.cljs` if adding the helper would make either existing alignment namespace harder to read. The helper should expose a function with this shape:

    (defn history-window
      [{:keys [calendar return-calendar return-intervals source-series-by-instrument]}]
      ...)

The returned map should use stable keyword fields:

    {:return-observations 252
     :return-days 365.0
     :calendar-window-days 365.0
     :oldest-common-ms 1714521600000
     :latest-common-ms 1746057600000
     :limiting-instrument-id "external:SP500"
     :limiting-reason :fewest-return-observations
     :limiting-source-return-observations 252
     :limiting-source-calendar-days 365.0
     :limiting-candidate-count 1}

The helper should accept already-normalized rows with `:time-ms`, `:close`, and optional `:return`. It should ignore rows without finite timestamps. The source return observation count should prefer finite point-level `:return` values when present, otherwise use `(max 0 (dec source point count))`. The primary limiter algorithm should be deterministic:

1. If any source coverage starts later than another source and the latest source start is inside the common window, choose the latest-starting asset with reason `:starts-later`.
2. Else if any source coverage ends earlier than another source and the earliest source end is inside the common window, choose the earliest-ending asset with reason `:ends-earlier`.
3. Else if source return observation counts differ, choose the asset with the fewest source return observations with reason `:fewest-return-observations`.
4. Else omit `:limiting-instrument-id` and use reason `:shared-calendar`.

When multiple assets tie for the primary limiter, choose the lexicographically first instrument ID for `:limiting-instrument-id` and set `:limiting-candidate-count` to the number of tied assets. The UI may show the first asset and mention the additional count later if needed, but this first implementation can keep the copy short.

Fourth, call the helper from both history alignment paths. In API v2 alignment, pass `:source-series-by-instrument` from the effective eligible API series points, not from synthesized prices. In legacy alignment, pass the effective eligible `:history` rows. If aligned returns are usable but one or more eligible assets has no source points, the helper should still return `:return-observations` and elapsed days, but omit `:limiting-instrument-id` and set `:limiting-reason :source-coverage-unavailable`.

Fifth, extend solved result payload. In `/hyperopen/src/hyperopen/portfolio/optimizer/application/engine/payload.cljs`, update `history-summary` so it merges the new `:history-window` data into `:history-summary`. Preserve existing fields exactly: `:return-observations`, `:oldest-common-ms`, `:latest-common-ms`, `:age-ms`, and `:stale?`. Add new fields without removing or renaming existing keys. Add or update a test in `/hyperopen/test/hyperopen/portfolio/optimizer/application/engine_test.cljs` that asserts the result contains the new fields.

Sixth, update the result trust rail. In `/hyperopen/src/hyperopen/views/portfolio/optimize/results_diagnostics_rail.cljs`, add a new `trust-row` call after `Conditioning` and before `Diversification`. The row should have label `History Used`, status `:ok` when `:return-observations` is at least 30 and `:caution` below 30, value text such as `252 returns · 365 days`, and subtext naming the limiting asset when available. Use `results-model/instrument-label` with `:labels-by-instrument` so vaults and backend-labeled assets display human names. If no limiter is available but return data is present, use subtext `Shared return calendar from aligned optimizer history.` If source coverage is unavailable, use `Limiter unavailable from aligned returns.`

Seventh, add view coverage. In `/hyperopen/test/hyperopen/views/portfolio/optimize/results_panel_test.cljs`, extend `results-panel-renders-canonical-results-workspace-shell-test` or add a focused test that overrides `solved-result :history-summary` with the new fields and asserts the collected strings include `History Used`, the value text, and the limiting asset label. This is a pure Hiccup test and should not require a browser.

Eighth, add or update deterministic browser coverage only if the implementation changes browser flow or visible result layout beyond static Hiccup rendering. If no browser flow changes, record a Browser QA skip in the implementation notes because the change is covered by pure result rendering tests. If a route-level layout issue appears, run the smallest relevant Playwright test under `/hyperopen/tools/playwright/test/` and record the command and result in this plan.

## Concrete Steps

Start from `/hyperopen`:

    cd /hyperopen
    git status --short

Expected before implementation is a clean tree or only this ExecPlan file if the planning commit has not been made.

Add the API v2 RED test in `test/hyperopen/portfolio/optimizer/application/history_loader_api_v2_test.cljs`. Use a fixture like this in the new test body, adapting helper names already present in that file:

    (let [d0 (day-start-ms "2026-01-01")
          day (fn [n] (+ d0 (* n day-ms)))
          universe [{:instrument-id "perp:BTC"
                     :market-type :perp
                     :coin "BTC"
                     :optimizer-history/instrument-id "hl:perp:BTC"}
                    {:instrument-id "external:SP500"
                     :market-type :external
                     :coin "SP500"
                     :optimizer-history/instrument-id "proxy:sp500"}]
          btc-points (mapv (fn [n]
                             {:time_ms (day n)
                              :close (+ 100 n)
                              :return (when (pos? n) 0.001)})
                           (range 10))
          proxy-days [0 1 2 5 6 7 8 9]
          proxy-points (mapv (fn [idx n]
                               {:time_ms (day n)
                                :close (+ 4000 idx)
                                :return (when (pos? idx) 0.0005)})
                             (range)
                             proxy-days)
          return-calendar (mapv day [1 2 5 6 7 8 9])
          normalized (api-v2/normalize-history-bundle
                      {:universe universe}
                      {:contract_version "optimizer-history-api-v2"
                       :request_id "rid-weekday-proxy"
                       :dataset_version "dv-weekday-proxy"
                       :status "ok"
                       :common_calendar (mapv day proxy-days)
                       :return_calendar return-calendar
                       :aligned_returns_by_instrument
                       {"hl:perp:BTC" {:instrument_id "hl:perp:BTC"
                                       :returns (vec (repeat 7 0.001))}
                        "proxy:sp500" {:instrument_id "proxy:sp500"
                                       :returns (vec (repeat 7 0.0005))}}
                       :series_by_instrument
                       {"hl:perp:BTC" {:instrument_id "hl:perp:BTC"
                                       :lineage_kind "native"
                                       :series_kind "market_price"
                                       :points btc-points
                                       :funding {:status "available"
                                                 :annualized_carry 0}
                                       :warnings []}
                        "proxy:sp500" {:instrument_id "proxy:sp500"
                                       :lineage_kind "approved_proxy"
                                       :series_kind "market_price"
                                       :points proxy-points
                                       :funding {:status "not_applicable"}
                                       :warnings [{:code "proxy-history-used"}]}}
                       :warnings []})
          aligned (api-v2/align-api-v2-history-inputs
                   {:universe universe
                    :api-v2-history normalized
                    :min-observations 2})]
      (is (= "external:SP500"
             (get-in aligned [:history-window :limiting-instrument-id])))
      (is (= :fewest-return-observations
             (get-in aligned [:history-window :limiting-reason])))
      (is (= 7
             (get-in aligned [:history-window :return-observations]))))

Run the focused RED command:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-test

Expected before implementation: the new test fails because `:history-window` is absent or missing limiter fields.

After implementing the helper and aligner wiring, rerun the same command and expect the named test namespace to pass.

Add the engine payload test in `test/hyperopen/portfolio/optimizer/application/engine_test.cljs`. Extend `run-optimization-assembles-solved-result-with-diagnostics-and-rebalance-preview-test` only if the expected history summary remains readable; otherwise add a new focused test named `run-optimization-includes-history-window-limiter-in-summary-test`. Expected assertions should include:

    (is (= 3 (:return-observations (:history-summary result))))
    (is (number? (:return-days (:history-summary result))))
    (is (= "perp:ETH"
           (:limiting-instrument-id (:history-summary result))))

Run:

    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.application.engine-test

Expected before payload implementation: failure for missing fields. Expected after payload implementation: pass.

Add the view test in `test/hyperopen/views/portfolio/optimize/results_panel_test.cljs`. The test should build a result like:

    (let [result (assoc-in solved-result
                           [:history-summary]
                           {:return-observations 252
                            :return-days 365.0
                            :calendar-window-days 365.0
                            :limiting-instrument-id "external:SP500"
                            :limiting-reason :fewest-return-observations})
          result (assoc result
                        :instrument-ids ["perp:BTC" "external:SP500"]
                        :labels-by-instrument {"external:SP500" "S&P 500 Proxy"})
          view-node (results-panel/results-panel
                     {:result result :computed-at-ms 2600}
                     {:objective {:kind :max-sharpe}}
                     {:frontier-overlay-mode :standalone})
          strings (set (collect-strings view-node))]
      (is (contains? strings "History Used"))
      (is (contains? strings "252 returns · 365 days"))
      (is (some #(str/includes? % "S&P 500 Proxy") strings)))

Run:

    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.views.portfolio.optimize.results-panel-test

Expected before view implementation: failure because `History Used` is absent. Expected after view implementation: pass.

After focused tests pass, run the required gates from `/hyperopen`:

    npm run check
    npm test
    npm run test:websocket

Expected final result: all three commands pass, or this ExecPlan records any unrelated pre-existing blocker with the exact output and why it is unrelated.

## Validation and Acceptance

Acceptance is met when:

- A solved optimizer result includes existing `:history-summary` fields and new fields for `:return-days`, `:calendar-window-days`, `:limiting-instrument-id`, `:limiting-reason`, and limiter source observation metadata when source coverage exists.
- A mixed crypto plus weekday proxy fixture identifies the proxy asset as the limiter by `:fewest-return-observations` while still showing the wider elapsed calendar days.
- A late-starting asset fixture identifies the late-starting asset as the limiter by `:starts-later`.
- The result trust rail shows a `History Used` row with return observations, elapsed days, and limiting asset copy.
- When API aligned returns are usable but raw source coverage is unavailable, the UI still shows return observations and elapsed days and explicitly avoids naming a guessed limiter.
- Focused tests for API v2 alignment, legacy alignment if touched, engine payload, and result panel rendering pass.
- Required repository gates are run and recorded: `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

All planned edits are additive and can be repeated safely. The new metadata should be derived from already-loaded history and should not trigger network requests, mutate stored scenarios, alter request signatures, or change optimizer weights. If a focused test exposes an existing math or alignment bug, pause implementation, record the discovery in this plan, and either expand the plan with a minimal fix or split the bug into a separate ExecPlan before changing optimizer math.

If the UI row becomes too tall in the existing right rail, keep the row but shorten copy before adding new layout structure. Do not move warnings or diagnostics out of the right rail as part of this plan.

## Artifacts and Notes

RED evidence:

    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-test --test=hyperopen.portfolio.optimizer.application.history-loader-test --test=hyperopen.portfolio.optimizer.application.engine-test --test=hyperopen.views.portfolio.optimize.results-panel-test

    Ran 37 tests containing 255 assertions.
    14 failures, 0 errors.

The failures were expected: missing `:history-window` fields, missing new `:history-summary` fields, and missing `History Used` rail copy.

Focused GREEN evidence:

    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-test --test=hyperopen.portfolio.optimizer.application.history-loader-test --test=hyperopen.portfolio.optimizer.application.engine-test --test=hyperopen.views.portfolio.optimize.results-panel-test

    Ran 37 tests containing 255 assertions.
    0 failures, 0 errors.

## Interfaces and Dependencies

The new history-window helper should be pure ClojureScript and should not depend on browser APIs. It may use existing optimizer coercion/math helpers for finite-number checks if needed. It should return ordinary maps consumed by the existing history and result payload contracts.

The result payload interface at completion should preserve this existing shape:

    :history-summary {:return-observations 252
                      :oldest-common-ms 1714521600000
                      :latest-common-ms 1746057600000
                      :age-ms 1000
                      :stale? false}

and extend it to this shape:

    :history-summary {:return-observations 252
                      :return-days 365.0
                      :calendar-window-days 365.0
                      :oldest-common-ms 1714521600000
                      :latest-common-ms 1746057600000
                      :age-ms 1000
                      :stale? false
                      :limiting-instrument-id "external:SP500"
                      :limiting-reason :fewest-return-observations
                      :limiting-source-return-observations 252
                      :limiting-source-calendar-days 365.0
                      :limiting-candidate-count 1}

The UI should consume only `result :history-summary` and `result :labels-by-instrument`. It should not inspect raw history, API v2 history, candle histories, or funding maps.

Revision note, 2026-06-01 / Codex: Initial active ExecPlan created from the user request to show return-history depth and limiting asset on optimizer results.
