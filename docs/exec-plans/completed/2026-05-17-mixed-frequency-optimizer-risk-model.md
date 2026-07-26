# Mixed-Frequency Optimizer Risk Model

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan follows `.agents/PLANS.md` and the public planning contract in `docs/PLANS.md`.

## Purpose / Big Picture

The portfolio optimizer currently estimates risk from one globally aligned return matrix. When dense market assets such as BTC, ETH, and HYPE are selected with a sparse vault such as HLP, that shared matrix collapses dense assets onto the vault calendar and can use a dense asset's single-day return on the sparse end date instead of the dense asset's close-to-close return over the whole sparse interval. This materially distorts volatility, covariance, and optimizer weights.

After this change, the optimizer can include sparse vaults without discarding dense market history. Dense/dense covariance is estimated from daily returns, dense/sparse covariance is estimated by aggregating dense close prices across the sparse asset's actual intervals, sparse/sparse covariance is estimated only on shared sparse intervals, sparse cross-correlations are shrunk, and the final matrix is repaired to be positive semidefinite before it reaches the solver. The behavior is observable through focused optimizer domain tests that use the BTC/HLP interval from the problem statement and through result warnings that explain sparse-history treatment.

## Context References

Public refs:
- Direct user request in this Codex thread on 2026-05-17 to implement the mixed-frequency front-end optimizer fix.

Repo artifacts:
- `AGENTS.md`, especially the optimizer validation gates and multi-agent write-surface rules.
- `docs/PLANS.md` and `.agents/PLANS.md` for this ExecPlan's required shape.
- `src/hyperopen/portfolio/optimizer/domain/risk.cljs`, the current single-calendar covariance estimator.
- `src/hyperopen/portfolio/optimizer/application/history_loader/alignment.cljs`, the legacy history alignment reducer.
- `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs`, the optimizer history API v2 reducer.
- `src/hyperopen/portfolio/optimizer/domain/returns.cljs`, the interval-aware expected return estimator.

Local scratch refs, non-authoritative:
- `/Users/barry/Desktop/problem_statement.txt`, which records the BTC/HLP interval bug and API review context.

## Progress

- [x] (2026-05-17T19:16:08Z) Read the user recommendation, the problem statement, the repo planning rules, and the current optimizer risk/history/return code.
- [x] (2026-05-17T19:16:08Z) Created this active ExecPlan before implementation.
- [x] (2026-05-17T19:20:00Z) Added RED tests for raw per-instrument history preservation, mixed-frequency BTC/HLP interval aggregation, sparse pair metadata, sparse warnings, and PSD repair.
- [x] (2026-05-17T19:45:00Z) Implemented raw-history/cadence metadata in both legacy and API v2 history loaders.
- [x] (2026-05-17T19:52:00Z) Implemented the mixed-frequency covariance estimator, sparse correlation shrinkage, PSD repair, pair metadata, and sparse-history warnings.
- [x] (2026-05-17T19:58:00Z) Wired `:mixed-frequency` risk model controls, specs, draft actions, progress labels, result metadata, and sparse objective warnings.
- [x] (2026-05-17T20:20:00Z) Addressed read-only review findings: legacy fallback now keeps preferred vault raw history for risk, mixed-frequency risk excludes aligned-only instruments without native rows instead of producing zero-risk rows, subdaily expected-return anchors are preserved when the total window is meaningful, and automatic mixed-frequency overrides emit an explicit warning.
- [x] (2026-05-17T20:32:00Z) Extracted mixed-frequency estimator internals into `risk_mixed_frequency.cljs` so namespace-size checks pass without adding a size exception.
- [x] (2026-05-17T20:41:00Z) Verified required gates: `npm test`, `npm run check`, `npm run test:websocket`, focused Playwright optimizer history API v2 spec, `npm run browser:cleanup`, and `git diff --check`.
- [x] (2026-05-17T20:44:00Z) Moved this ExecPlan to `docs/exec-plans/completed/` after acceptance was verified.

## Surprises & Discoveries

- Observation: `risk.cljs` currently multiplies sample covariance by `periods-per-year`, which is correct for a single daily matrix but wrong when the matrix contains sparse interval observations.
  Evidence: `covariance-matrix` in `src/hyperopen/portfolio/optimizer/domain/risk.cljs` maps every pair to `(* periods-per-year (sample-covariance xs ys))`.
- Observation: the legacy history loader already has normalized raw histories before `resolve-history-alignment` picks one common calendar, so the mixed estimator can be added without removing the aligned history used by charts and existing display code.
  Evidence: `prepared` entries in `src/hyperopen/portfolio/optimizer/application/history_loader/alignment.cljs` contain `:history` before `price-series-by-instrument` is projected onto `effective-calendar`.
- Observation: expected returns already support per-instrument intervals when `:expected-return-series-by-instrument` and `:expected-return-intervals-by-instrument` are present.
  Evidence: `interval-observations` and `geometric-annualized-return` in `src/hyperopen/portfolio/optimizer/domain/returns.cljs`.
- Observation: this worktree initially had no usable `node_modules`, so the first CLJS test run failed before assertions on a missing `lucide` module. Running `npm install` restored the local dependency tree; `package.json` and `package-lock.json` were unchanged.
  Evidence: the failure was `Error: Cannot find module 'lucide/dist/esm/icons/external-link.js'`; subsequent `npm test` runs passed after dependency installation.
- Observation: preserving subdaily expected-return intervals must distinguish real subdaily anchors from millisecond-spaced test fixtures. Rejecting all subdaily intervals broke the existing expected-return semantics; allowing intervals when the total elapsed window is at least one day preserves 12-hour anchors without reintroducing pathological annualization for tiny fixture windows.
  Evidence: `historical-mean-uses-native-metadata-with-subdaily-expected-return-anchor-test` exercises a 0.5-day first interval and one-year total window, while the full `npm test` suite stayed green.
- Observation: API v2 aligned returns can keep an instrument eligible even when it has no native point rows. In a mixed-frequency run, using the aligned display series as a price fallback would collapse that instrument onto the sparse calendar or produce a zero-risk row.
  Evidence: `mixed-frequency-risk-excludes-aligned-only-instruments-without-native-history-test` now verifies those instruments are dropped from the mixed-frequency risk matrix with `:missing-native-risk-history`.

## Decision Log

- Decision: Preserve the existing aligned history output for display and backward compatibility, but add raw per-instrument price histories and cadence metadata as additional history fields for optimizer risk and expected-return inputs.
  Rationale: This avoids breaking display code while giving the optimizer the native-frequency data it needs.
  Date/Author: 2026-05-17 / Codex
- Decision: Route risk estimation to mixed-frequency whenever the requested risk model is `:mixed-frequency` or the history metadata marks at least one eligible instrument as sparse.
  Rationale: The bug is a correctness issue when sparse assets are present; leaving existing `:diagonal-shrink` drafts on the aligned matrix would keep the bad behavior.
  Date/Author: 2026-05-17 / Codex
- Decision: Keep sparse asset diagonal variance, shrink only sparse-involved cross-correlations, then apply diagonal loading if pairwise estimates produce a non-PSD matrix.
  Rationale: Sparse variance is real information from the vault's own intervals, while sparse cross-correlations are the noisy part that can dominate max-Sharpe and minimum-variance outputs.
  Date/Author: 2026-05-17 / Codex

## Outcomes & Retrospective

Implemented the mixed-frequency optimizer risk model in the front-end. The optimizer now preserves raw per-instrument histories and cadence metadata, routes sparse portfolios to pairwise interval covariance, aggregates dense assets over sparse endpoints with close-at-or-before lookup, shrinks sparse-involved cross-correlations, repairs PSD with diagonal loading, and carries pair metadata and sparse-history warnings into results.

The implementation keeps the existing aligned history fields for display compatibility while adding native raw history fields for risk and expected returns. Legacy fallback windows no longer replace the preferred vault raw history used by the mixed-frequency estimator. API v2 aligned-only assets without native rows are excluded from mixed-frequency risk with an explicit warning instead of entering the covariance matrix as zero-risk assets.

Validation completed:
- `npm test` passed: 3,942 tests, 21,682 assertions, 0 failures, 0 errors.
- `npm run check` passed, including namespace size/boundary checks and Shadow CLJS compile targets.
- `npm run test:websocket` passed: 524 tests, 3,043 assertions, 0 failures, 0 errors.
- `npx playwright test tools/playwright/test/optimizer-history-api-v2.spec.mjs --workers=1` passed: 2 tests.
- `npm run browser:cleanup` returned `{"ok": true, "stopped": [], "results": []}`.
- `git diff --check` passed.

Remaining risk: the implementation warns and shrinks sparse cross-correlations, but it does not implement the stricter product policy of excluding every sparse asset below 8 intervals. The BTC/HLP regression test intentionally uses a small synthetic sample to prove interval semantics; production sparse-history handling remains warning-based unless a later product decision requires hard exclusion.

## Context and Orientation

The optimizer engine request contains a `:history` map. The current risk estimator reads `:return-series-by-instrument` from that history map, sorts instrument ids, computes sample covariance for every pair, annualizes every covariance with a single `periods-per-year`, and returns a matrix to the solver. The legacy and API v2 history loaders both reduce source data into this aligned map. The aligned map remains useful for charts and for equal-frequency assets, but it is too lossy for a portfolio that combines daily market assets and sparse vault assets.

A dense instrument is one whose observations are close to daily over the lookback window. A sparse instrument is one whose observations are much less frequent, such as HLP with roughly 25 return intervals over a year. A sparse interval is the time from one sparse observation to the next. For a dense asset on a sparse interval, the correct return is `log(close_at_or_before_end / close_at_or_before_start)` across the whole interval, not the daily return on the interval end date.

The core files are:
- `src/hyperopen/portfolio/optimizer/application/history_loader/alignment.cljs`, which builds legacy history from candle and vault-detail data.
- `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs`, which normalizes and aligns optimizer history API v2 responses.
- `src/hyperopen/portfolio/optimizer/domain/risk.cljs`, which estimates covariance and exposes covariance conditioning.
- `src/hyperopen/portfolio/optimizer/domain/returns.cljs`, which estimates expected returns and already supports intervals.
- `src/hyperopen/portfolio/optimizer/application/engine/payload.cljs` and `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs`, which carry warnings into result and setup surfaces.

## Plan of Work

First, add tests. `test/hyperopen/portfolio/optimizer/domain/risk_test.cljs` should include the BTC/HLP interval example: BTC closes at 107818 on 2025-05-28 and 108668 on 2025-06-11, while its daily return on 2025-06-11 is represented by a prior close that would imply about -1.54 percent. The mixed-frequency estimator must use the interval return of about +0.79 percent. The same test should assert that BTC/ETH/HYPE dense pairs use daily observation counts, HLP pairs use sparse interval counts and `:sparse-interval` metadata, the matrix is symmetric, and PSD repair leaves covariance conditioning non-negative. `test/hyperopen/portfolio/optimizer/application/history_loader_vaults_test.cljs` and `test/hyperopen/portfolio/optimizer/application/history_loader_api_v2_test.cljs` should assert that raw histories, cadence metadata, expected-return series, and expected-return intervals are preserved without replacing existing aligned fields.

Next, add a small shared history-loader helper namespace if needed so cadence classification and raw-return construction are not duplicated. The helper should normalize raw rows with `:time-ms` and numeric `:close`, classify cadence with `observations`, `interval-count`, `median-dt-days`, `max-dt-days`, `density-vs-daily`, and `sparse?`, and build simple return series plus interval metadata per instrument.

Then update both history loaders. The legacy loader should emit `:raw-price-series-by-instrument`, `:cadence-by-instrument`, `:expected-return-series-by-instrument`, `:expected-return-intervals-by-instrument`, and `:risk-estimation` for eligible instruments from their native histories. The API v2 loader should do the same from each normalized series' `:points`. Existing aligned `:calendar`, `:return-calendar`, `:price-series-by-instrument`, and `:return-series-by-instrument` must remain present.

Then update `risk.cljs`. Add `:mixed-frequency` to risk model normalization. Build pair calendars from raw rows and cadence metadata. Use value-at-or-before endpoint lookup with bounded staleness, compute log returns over pair intervals, estimate annual covariance with elapsed interval years, shrink sparse-involved correlations by `n / (n + 30)`, and repair PSD by diagonal loading using the existing eigenvalue helper. Return `:pair-metadata`, `:risk-estimation`, and warnings for sparse histories, insufficient pairwise history, and PSD repair.

Finally, wire metadata and copy. Contracts should allow `:mixed-frequency`. Setup controls may offer a mixed-frequency risk model option, and warning copy should explain sparse history in plain language. Result payload should add a stronger warning for `:max-sharpe` and `:minimum-variance` when sparse-history warnings are present.

## Concrete Steps

Run these commands from `/Users/barry/.codex/worktrees/0394/hyperopen`.

1. Generate RED tests:
   `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`
   Before implementation, the new mixed-frequency tests should fail because `:mixed-frequency` is not recognized and raw history metadata is absent.

2. Implement history metadata and risk estimator changes.

3. Run focused tests:
   `npm test`
   This project currently runs all generated CLJS tests rather than a single namespace filter.

4. Run required gates after code changes:
   `npm run check`
   `npm test`
   `npm run test:websocket`

5. If browser sessions are created during optional UI inspection, stop them with:
   `npm run browser:cleanup`

## Validation and Acceptance

Acceptance is met when the automated tests prove these behaviors:

1. BTC's return for the HLP sparse interval 2025-05-28 to 2025-06-11 uses `108668 / 107818 - 1`, about `+0.79%`, not BTC's single-day 2025-06-11 return of about `-1.54%`.
2. With BTC, ETH, HYPE, and HLP, dense/dense pair metadata reports a daily calendar and roughly daily observation count, while HLP pairs report sparse interval metadata and roughly the HLP interval count.
3. The final covariance matrix is symmetric and has non-negative covariance conditioning after PSD repair.
4. Sparse history warnings include observation counts, elapsed days, and the mixed-frequency policy.
5. Expected return inputs use each instrument's own return intervals, so sparse vault returns are not annualized as `mean * 365`.
6. Existing aligned history fields still exist for display compatibility.

The required repository gates must pass: `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

All planned edits are additive or local rewrites of optimizer front-end code and tests. Re-running tests is safe. If a history-loader change breaks display assumptions, keep the existing aligned fields unchanged and add new raw/mixed-frequency fields instead of removing or renaming old keys. If PSD repair produces unexpected conditioning, inspect the returned `:pair-metadata` and `:warnings` before changing solver code; the solver should continue receiving a plain covariance matrix.

## Artifacts and Notes

The core BTC interval example is:

    start: 2025-05-28 BTC close 107818
    end:   2025-06-11 BTC close 108668
    correct sparse-interval simple return: 108668 / 107818 - 1 = about +0.00788
    incorrect daily end-date return: about -0.0154

## Interfaces and Dependencies

At completion, `history` maps passed to the optimizer engine should allow these optional keys:

    :raw-price-series-by-instrument
    {"perp:BTC" [{:time-ms 1748390400000 :close 107818} ...]}

    :cadence-by-instrument
    {"vault:..." {:observations 26
                  :interval-count 25
                  :median-dt-days 14
                  :max-dt-days 15
                  :density-vs-daily 0.07
                  :sparse? true
                  :kind :sparse}}

    :risk-estimation
    {:kind :mixed-frequency
     :dense-block-calendar :daily
     :sparse-policy :pairwise-interval-aggregation
     :sparse-correlation-shrinkage true}

`risk/estimate-risk-model` should continue accepting `{:risk-model ... :periods-per-year ... :history ...}` and should return the existing `:model`, `:instrument-ids`, `:covariance`, and `:warnings` keys, plus optional `:pair-metadata`, `:risk-estimation`, and `:requested-model` keys for mixed-frequency runs.

## Revision Notes

- 2026-05-17 / Codex: Initial plan created from the direct user request and codebase inspection so implementation can proceed in this session.
