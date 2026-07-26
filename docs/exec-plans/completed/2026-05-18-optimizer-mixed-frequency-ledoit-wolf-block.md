# Mixed-Frequency Ledoit-Wolf Dense Block

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Milestone 1 made `:ledoit-wolf-dense` truthful for fully dense optimizer universes and made sparse universes route safely to `:mixed-frequency`. Milestone 2 should improve the mixed sparse/dense case itself: when a user explicitly requests `{:risk-model {:kind :ledoit-wolf-dense}}` and the selected universe contains both dense market assets and sparse vault-like assets, the dense/dense covariance block should come from the true Ledoit-Wolf estimator instead of pairwise mixed-frequency sample covariance. Dense/sparse and sparse/sparse pairs must continue to use native interval semantics so vault histories are never forward-filled or downsampled into fake daily returns.

After this change, a BTC/ETH/HYPE/HLP mixed universe requesting `:ledoit-wolf-dense` should return `:model :mixed-frequency`, preserve `:requested-model :ledoit-wolf-dense`, and expose metadata showing that BTC/ETH/HYPE dense pairs used `:ledoit-wolf-dense` while HLP-involved pairs used sparse interval aggregation with correlation retention. The user-visible outcome is a safer efficient frontier for mixed portfolios: dense crypto assets benefit from data-driven shrinkage, while sparse vault assets remain capped and warning-backed.

## Context References

Public refs:

- Direct user request in this Codex session on 2026-05-18: create an execution plan for Milestone 2 after committing the Milestone 1 implementation.

Repo artifacts:

- `/hyperopen/AGENTS.md` requires complex optimizer work to use an active ExecPlan and requires `npm run check`, `npm test`, and `npm run test:websocket` after code changes.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define this active ExecPlan contract.
- `/hyperopen/docs/exec-plans/active/2026-05-18-optimizer-covariance-shrinkage.md` is the parent plan for Milestone 1. It records that dense-only `:ledoit-wolf-dense` is complete and that mixed-frequency dense-block Ledoit-Wolf remains the follow-up.
- `src/hyperopen/portfolio/optimizer/domain/risk_ledoit_wolf.cljs` exposes the pure dense estimator added in Milestone 1.
- `src/hyperopen/portfolio/optimizer/domain/risk.cljs` routes dense, sparse, and mixed covariance estimation.
- `src/hyperopen/portfolio/optimizer/domain/risk_mixed_frequency.cljs` builds mixed-frequency covariance matrices and pair metadata.
- `test/hyperopen/portfolio/optimizer/domain/risk_test.cljs` contains the existing mixed-frequency fixtures and is the main test surface for this plan.

Local scratch refs:

- None.

## Progress

- [x] (2026-05-18 20:02Z) Committed Milestone 1 as `91e7fb65` with message `feat: add dense ledoit wolf risk model`.
- [x] (2026-05-18 20:05Z) Reviewed `src/hyperopen/portfolio/optimizer/domain/risk_mixed_frequency.cljs`, `src/hyperopen/portfolio/optimizer/domain/risk.cljs`, and the mixed-frequency risk tests to map the dense-block insertion point.
- [x] (2026-05-18 20:20Z) Added RED tests proving dense/dense mixed-frequency pairs use the Ledoit-Wolf dense block for explicit `:ledoit-wolf-dense` requests.
- [x] (2026-05-18 20:20Z) Added RED tests proving dense-block fallback keeps the old pairwise path and emits a warning when the native dense block is insufficient or non-rectangular.
- [x] (2026-05-18 20:22Z) Ran `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`; the new risk tests failed as expected on missing dense-block covariance and metadata, plus the existing unrelated cancel-request `ReferenceError`.
- [x] (2026-05-18 17:15Z) Implemented dense-block precomputation and pair replacement in `risk_mixed_frequency.cljs`.
- [x] (2026-05-18 17:15Z) Threaded dense-block options from `risk.cljs` only for explicit `:ledoit-wolf-dense` mixed-frequency requests.
- [x] (2026-05-18 17:15Z) Reran `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`; the Milestone 2 risk assertions pass with `0 failures`, and the runner still exits on the known unrelated `hyperopen.api.trading.cancel-request-test` `ReferenceError`.
- [x] (2026-05-18 17:20Z) Ran required repo gates: `npm run check` passed, `npm run test:websocket` passed, and `npm test` completed with `0 failures, 1 errors` from the known unrelated `hyperopen.api.trading.cancel-request-test` generated-code `ReferenceError`.
- [x] (2026-05-18 17:35Z) Addressed review feedback that the first implementation still used sparse-collapsed `:return-series-by-instrument`; dense-block Ledoit-Wolf now derives returns from the dense instruments' native raw price rows and their dense-only common calendar.
- [x] (2026-05-18 17:35Z) Added focused regression coverage for ignoring sparse-collapsed aligned returns, falling back on insufficient native dense observations, and falling back on non-rectangular native dense returns. Focused risk command passed with `14 tests, 84 assertions, 0 failures, 0 errors`.
- [x] (2026-05-18 17:45Z) Moved this ExecPlan from `active/` to `completed/` after the docs lint rejected a fully checked active plan; reran final gates with `npm run check` passing, `npm run test:websocket` passing, and `npm test` completing with `0 failures, 1 errors` from the known unrelated cancel-request generated-code error.

## Surprises & Discoveries

- Observation: `risk_mixed_frequency.cljs` currently estimates every pair independently, including dense/dense pairs.
  Evidence: `matrix` reduces over every `[row-idx col-idx]`, calls `pair-estimate`, and stores the returned covariance and metadata. Dense/dense pairs receive `:calendar-kind :daily` and `:correlation-retention 1`, but no shrinkage metadata.

- Observation: Dense-only Ledoit-Wolf uses `:return-series-by-instrument`, but in mixed sparse/dense histories that field can be the sparse-collapsed common aligned calendar.
  Evidence: the history loaders expose native rows through `:raw-price-series-by-instrument` and native expected returns through `:expected-return-series-by-instrument`, while `:return-series-by-instrument` can be built from the common calendar. The dense block must therefore use the dense instruments' native raw price rows, not the mixed universe's global aligned return matrix.

- Observation: The existing pair metadata map is already the right transport for dense-block provenance.
  Evidence: solved payloads already include `:pair-metadata`, and existing tests assert `:calendar-kind`, `:observations`, and `:correlation-retention` for pair keys such as `perp:BTC|perp:ETH`.

- Observation: The final PSD repair can add small diagonal loading after the dense Ledoit-Wolf block has been inserted.
  Evidence: the first GREEN run showed dense-block off-diagonal entries matching the direct dense-only estimate while dense diagonals differed by approximately the reported `:psd-repair-applied :diagonal-loading`; the contract test now asserts dense diagonals through that final repair step.

## Decision Log

- Decision: Use the dense block only when the mixed-frequency request came from explicit `:ledoit-wolf-dense`.
  Rationale: Milestone 1 deliberately preserved legacy `:ledoit-wolf` and default `:diagonal-shrink` behavior. Applying dense Ledoit-Wolf to all mixed-frequency requests would silently change `:sample-covariance`, `:diagonal-shrink`, and `:mixed-frequency` semantics. This plan adds the recommended hybrid behavior for the explicit model without widening the blast radius.
  Date/Author: 2026-05-18 / Codex

- Decision: Build the mixed-frequency dense block from dense instruments' native raw price rows aligned on their dense-only common timestamp grid.
  Rationale: The global `:return-series-by-instrument` matrix can be sparse-collapsed by vault history, which would defeat the purpose of a dense block. Aligning only dense native price rows preserves the Ledoit-Wolf rectangular-matrix requirement without allowing sparse vault cadence to shorten BTC/ETH/HYPE history. Sparse-involved pairs still use raw native intervals to preserve the mixed-frequency guarantee.
  Date/Author: 2026-05-18 / Codex

- Decision: Keep dense-block failure non-fatal.
  Rationale: A mixed universe can still produce a valid interval covariance matrix when a dense block cannot be formed. The implementation should fall back to the current pairwise dense/dense path and emit a warning, not abort the whole optimizer run.
  Date/Author: 2026-05-18 / Codex

## Outcomes & Retrospective

Milestone 2 is implemented. Explicit `:ledoit-wolf-dense` requests on mixed dense/sparse histories now return `:model :mixed-frequency` with a true Ledoit-Wolf dense/dense block, sparse-involved pairs still use native interval aggregation, and dense-block provenance is exposed through both `:pair-metadata` and `:risk-estimation`.

The hybrid path adds some local complexity in `risk_mixed_frequency.cljs`, but keeps ownership clean: dense-block construction is optional, failure is warning-backed and non-fatal, and the top-level router only enables it for explicit `:ledoit-wolf-dense` requests. The remaining product/modeling follow-up is deciding whether a future milestone should make the hybrid dense block the default for all `:mixed-frequency` requests or leave it opt-in.

Validation: `npm run check` passed; `npm run test:websocket` passed; the focused risk command passed with `14 tests, 84 assertions, 0 failures, 0 errors`; `npm test` completed with `3963 tests, 21806 assertions, 0 failures, 1 errors` from the known unrelated `hyperopen.api.trading.cancel-request-test` `ReferenceError`.

## Context and Orientation

The portfolio optimizer estimates covariance in pure domain namespaces under `src/hyperopen/portfolio/optimizer/domain`. A covariance matrix is a square matrix whose rows and columns are ordered by `:instrument-ids`; each entry estimates how two instruments move together. The efficient frontier and target optimizer use that matrix to choose portfolio weights.

`risk.cljs` is the top-level router. It chooses the requested risk model, detects sparse history through `risk_mixed_frequency.cljs`, and repairs the final covariance matrix if it is not positive semidefinite. Positive semidefinite means the matrix is mathematically valid for portfolio variance; the current code repairs invalid matrices with diagonal loading.

`risk_ledoit_wolf.cljs` is a dense estimator. It expects a rectangular matrix of return observations: every instrument must have the same number of returns on the same observation grid. It returns annualized covariance plus shrinkage metadata such as `{:kind :ledoit-wolf :target :scaled-identity :shrinkage alpha}`.

`risk_mixed_frequency.cljs` is the sparse-aware estimator. It uses native raw price rows. Dense/dense pairs currently use shared daily endpoints. Dense/sparse pairs aggregate the dense asset over each sparse asset interval. Sparse/sparse pairs use shared sparse endpoints. Sparse-involved correlations are shrunk by observation count through `:correlation-retention`.

Milestone 2 should combine those two ideas. For explicit `:ledoit-wolf-dense` mixed universes, dense/dense entries should be copied from one dense Ledoit-Wolf block. Pairs involving at least one sparse asset should remain exactly as they are today.

## Scope Clarification

This plan covers:

- Adding dense-block Ledoit-Wolf inside `:mixed-frequency` results for explicit `:ledoit-wolf-dense` requests.
- Emitting dense-block metadata in `:pair-metadata` and `:risk-estimation`.
- Keeping sparse-involved pair semantics, sparse cap behavior, and warning surfaces intact.
- Adding tests for dense-block success and fallback behavior.

This plan does not cover:

- Making dense-block Ledoit-Wolf the default for every `:mixed-frequency` request.
- Changing sparse correlation shrinkage, sparse safety cap tiers, or allocation cap copy.
- Adding a setup UI control for `:ledoit-wolf-dense`.
- Implementing a factor model or backend-owned covariance contract.

## Plan of Work

### Milestone 1: RED Tests For Hybrid Dense Block

Start in `test/hyperopen/portfolio/optimizer/domain/risk_test.cljs`. Add a test below `ledoit-wolf-dense-request-routes-sparse-history-to-mixed-frequency-test` that reuses `mixed-frequency-history`, requests `{:kind :ledoit-wolf-dense}`, and compares the dense/dense covariance entries to a direct dense-only `:ledoit-wolf-dense` estimate for the dense instruments in the fixture.

The test should use existing helpers `matrix-near?`, `near?`, and `mixed-frequency-history`. Use `perp:BTC`, `perp:ETH`, and `perp:HYPE` as dense assets and the existing HLP vault id as the sparse asset. The test body should be equivalent to this structure:

    (deftest mixed-frequency-ledoit-wolf-request-uses-ledoit-wolf-dense-block-test
      (let [{:keys [history vault-id]} (mixed-frequency-history)
            result (risk/estimate-risk-model
                    {:risk-model {:kind :ledoit-wolf-dense}
                     :periods-per-year 365
                     :history history})
            dense-only (risk/estimate-risk-model
                        {:risk-model {:kind :ledoit-wolf-dense}
                         :periods-per-year 365
                         :history {:return-series-by-instrument
                                   (select-keys (:return-series-by-instrument history)
                                                ["perp:BTC" "perp:ETH" "perp:HYPE"])}})
            ids (:instrument-ids result)
            dense-ids (:instrument-ids dense-only)
            mixed-index (zipmap ids (range))
            dense-index (zipmap dense-ids (range))
            pair-meta (get-in result [:pair-metadata "perp:BTC|perp:ETH"])
            btc-hlp-meta (get-in result [:pair-metadata (str "perp:BTC|" vault-id)])]
        (is (= :mixed-frequency (:model result)))
        (is (= :ledoit-wolf-dense (:requested-model result)))
        (doseq [left-id dense-ids
                right-id dense-ids]
          (is (near? (get-in dense-only
                             [:covariance (dense-index left-id) (dense-index right-id)])
                     (get-in result
                             [:covariance (mixed-index left-id) (mixed-index right-id)])
                     0.0000001)))
        (is (= :ledoit-wolf-dense (:estimator pair-meta)))
        (is (= true (:dense-block? pair-meta)))
        (is (= :scaled-identity (:target pair-meta)))
        (is (number? (:shrinkage pair-meta)))
        (is (= :sparse-interval (:calendar-kind btc-hlp-meta)))
        (is (= (/ 2 (+ 2 30))
               (:correlation-retention btc-hlp-meta)))))

Run:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js

Expected RED result: the full runner may still show the unrelated `hyperopen.api.trading.cancel-request-test` `ReferenceError`, but the new dense-block assertion should fail because `perp:BTC|perp:ETH` still has no `:estimator :ledoit-wolf-dense` metadata and the dense/dense covariance still comes from pairwise mixed-frequency estimation.

### Milestone 2: RED Tests For Dense-Block Fallback

Add a second test in `test/hyperopen/portfolio/optimizer/domain/risk_test.cljs` proving the fallback behavior. Make one dense instrument non-rectangular or remove its aligned return series while keeping raw price history. The mixed-frequency result should still return a matrix, dense/dense pair metadata should remain `:estimator :pairwise-mixed-frequency`, and warnings should include `:dense-block-ledoit-wolf-unavailable`.

Use this shape:

    (deftest mixed-frequency-ledoit-wolf-falls-back-when-dense-aligned-series-is-missing-test
      (let [{:keys [history]} (mixed-frequency-history)
            history* (update history :return-series-by-instrument dissoc "perp:HYPE")
            result (risk/estimate-risk-model
                    {:risk-model {:kind :ledoit-wolf-dense}
                     :history history*})
            pair-meta (get-in result [:pair-metadata "perp:BTC|perp:ETH"])
            warning (some #(when (= :dense-block-ledoit-wolf-unavailable (:code %)) %)
                          (:warnings result))]
        (is (= :mixed-frequency (:model result)))
        (is (= :pairwise-mixed-frequency (:estimator pair-meta)))
        (is (= :missing-dense-return-series (:reason warning)))
        (is (= ["perp:HYPE"] (:instrument-ids warning)))))

Run the same compile and `node out/test.js` command. Expected RED result: the warning and fallback estimator metadata do not exist yet.

### Milestone 3: Implement Dense-Block Precomputation

Modify `src/hyperopen/portfolio/optimizer/domain/risk_mixed_frequency.cljs`.

Add the Ledoit-Wolf namespace require:

    [hyperopen.portfolio.optimizer.domain.risk-ledoit-wolf :as risk-ledoit-wolf]

Add helpers near `cadence-by-instrument`:

    (defn- dense-cadence?
      [cadence]
      (not (sparse-cadence? cadence)))

    (defn- dense-instrument-ids
      [history instrument-ids]
      (filterv #(dense-cadence? (cadence-for history %)) instrument-ids))

    (defn- dense-return-series-by-id
      [history instrument-ids]
      (into {}
            (keep (fn [instrument-id]
                    (when-let [series (seq (get-in history [:return-series-by-instrument instrument-id]))]
                      [instrument-id (vec series)])))
            instrument-ids))

    (defn- rectangular-series?
      [series-by-id instrument-ids]
      (let [series (mapv series-by-id instrument-ids)
            sample-counts (mapv count series)]
        (and (seq instrument-ids)
             (every? seq series)
             (apply = sample-counts))))

Add a helper that returns either a dense block or a warning:

    (defn- dense-ledoit-wolf-block
      [history instrument-ids periods-per-year]
      (let [dense-ids (dense-instrument-ids history instrument-ids)
            series-by-id* (dense-return-series-by-id history dense-ids)
            missing-ids (filterv #(not (contains? series-by-id* %)) dense-ids)]
        (cond
          (empty? dense-ids)
          {:block nil :warnings []}

          (seq missing-ids)
          {:block nil
           :warnings [{:code :dense-block-ledoit-wolf-unavailable
                       :reason :missing-dense-return-series
                       :instrument-ids missing-ids}]}

          (not (rectangular-series? series-by-id* dense-ids))
          {:block nil
           :warnings [{:code :dense-block-ledoit-wolf-unavailable
                       :reason :non-rectangular-dense-return-series
                       :instrument-ids dense-ids}]}

          :else
          (let [estimate (risk-ledoit-wolf/estimate
                          {:series (mapv series-by-id* dense-ids)
                           :periods-per-year periods-per-year})]
            {:block (assoc estimate
                           :instrument-ids dense-ids
                           :index-by-id (zipmap dense-ids (range)))
             :warnings []}))))

If ClojureScript complains about `apply =` on an empty sequence, keep the explicit `(seq instrument-ids)` guard shown above.

### Milestone 4: Replace Dense/Dense Pair Estimates When Requested

Still in `risk_mixed_frequency.cljs`, add helpers before `pair-estimate`:

    (defn- dense-block-covariance
      [dense-block left-id right-id]
      (when (and dense-block
                 (contains? (:index-by-id dense-block) left-id)
                 (contains? (:index-by-id dense-block) right-id))
        (let [left-idx (get (:index-by-id dense-block) left-id)
              right-idx (get (:index-by-id dense-block) right-id)]
          (get-in dense-block [:covariance left-idx right-idx]))))

    (defn- dense-block-pair-estimate
      [dense-block left-id right-id]
      (when-let [covariance (dense-block-covariance dense-block left-id right-id)]
        {:covariance covariance
         :metadata {:calendar-kind :daily
                    :observations (:sample-count dense-block)
                    :correlation-retention 1
                    :estimator :ledoit-wolf-dense
                    :dense-block? true
                    :target (get-in dense-block [:shrinkage :target])
                    :shrinkage (get-in dense-block [:shrinkage :shrinkage])}
         :warnings []}))

Update `pair-estimate` so the old pairwise metadata includes an explicit estimator:

    :metadata {:calendar-kind calendar-kind
               :observations observation-count
               :correlation-retention retention
               :estimator :pairwise-mixed-frequency}

Change `matrix` to support an options arity:

    (defn matrix
      ([history instrument-ids]
       (matrix history instrument-ids {}))
      ([history instrument-ids {:keys [dense-block-estimator periods-per-year]}]
       ...))

Inside the two-arity body, precompute:

    (let [{:keys [block warnings]} (if (= :ledoit-wolf-dense dense-block-estimator)
                                     (dense-ledoit-wolf-block history
                                                              instrument-ids
                                                              (or periods-per-year 365))
                                     {:block nil :warnings []})]
      ...)

In the reducer, replace:

    (pair-estimate history left-id right-id)

with:

    (or (dense-block-pair-estimate block left-id right-id)
        (pair-estimate history left-id right-id))

Seed the accumulated warnings with the dense-block warnings:

    {:covariance zero-matrix
     :pair-metadata {}
     :warnings warnings
     :dense-block (when block
                    (select-keys block [:instrument-ids :shrinkage :sample-count :feature-count]))}

Keep the public return keys backward-compatible: existing callers should still get `:covariance`, `:pair-metadata`, and `:warnings`. The new optional `:dense-block` key is additive.

### Milestone 5: Thread Options And Risk Metadata From The Router

Modify `src/hyperopen/portfolio/optimizer/domain/risk.cljs`.

In the mixed-frequency branch, change the call from:

    (mixed-frequency/matrix history risk-instrument-ids)

to:

    (mixed-frequency/matrix
     history
     risk-instrument-ids
     {:dense-block-estimator (when (= :ledoit-wolf-dense model-kind)
                               :ledoit-wolf-dense)
      :periods-per-year periods-per-year*})

Update destructuring to include `dense-block`:

    {:keys [covariance pair-metadata warnings dense-block]}

When building the return map, add dense-block metadata into `:risk-estimation`:

    :risk-estimation (cond-> (mixed-frequency/risk-estimation history)
                       dense-block
                       (assoc :dense-block-estimator :ledoit-wolf-dense
                              :dense-block-instrument-ids (:instrument-ids dense-block)
                              :dense-block-shrinkage (:shrinkage dense-block)
                              :dense-block-sample-count (:sample-count dense-block)
                              :dense-block-feature-count (:feature-count dense-block)))

Do not change the final PSD repair step. The full matrix can still become non-positive-semidefinite because dense-block entries and sparse interval entries come from different observation calendars. Existing diagonal loading remains the final safety layer.

### Milestone 6: Validation And Cleanup

Run the smallest affected tests first:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js

Interpretation: until the unrelated `hyperopen.api.trading.cancel-request-test` generated `ReferenceError` is fixed elsewhere, `node out/test.js` may exit nonzero. For this plan, there must be no optimizer-domain failures in the output, and the test summary should remain `0 failures, 1 errors` if the unrelated error persists.

Run the required repo gates:

    npm run check
    npm test
    npm run test:websocket

Expected results:

- `npm run check` exits `0`.
- `npm run test:websocket` exits `0`.
- `npm test` either exits `0` if the unrelated cancel-request error has been fixed, or exits `1` with only `hyperopen.api.trading.cancel-request-test` reporting `ReferenceError: values__9980__auto___23527 is not defined`.

If implementation changes make the `portfolio-optimizer-worker` compile slower or if review flags performance risk, record a baseline and run:

    node --test tools/optimizer/*.test.mjs

This command should pass. Do not keep additional performance complexity unless the evidence shows the simple dense-block precomputation is too slow for the expected optimizer universe sizes.

## Validation and Acceptance

Acceptance is met when all of the following are true:

- A mixed BTC/ETH/HYPE/HLP fixture requesting `:ledoit-wolf-dense` returns `:model :mixed-frequency`, `:requested-model :ledoit-wolf-dense`, and dense/dense covariance entries that match a direct dense-only `:ledoit-wolf-dense` estimate for BTC/ETH/HYPE.
- Dense/dense pair metadata includes `:estimator :ledoit-wolf-dense`, `:dense-block? true`, `:target :scaled-identity`, `:shrinkage`, and `:observations` equal to the dense Ledoit-Wolf sample count.
- Sparse-involved pair metadata remains `:calendar-kind :sparse-interval` with the same `:correlation-retention` behavior as Milestone 1.
- If the native dense block cannot be formed, the result falls back to pairwise mixed-frequency dense/dense estimates and emits `:dense-block-ledoit-wolf-unavailable`.
- Existing legacy behavior remains stable: `:diagonal-shrink` mixed-frequency requests still apply final diagonal shrink, legacy `:ledoit-wolf` still maps to `:diagonal-shrink`, and sparse runtime caps still apply through `constraints.cljs`.
- `npm run check` and `npm run test:websocket` pass. `npm test` either passes or records only the unrelated cancel-request generated-code error already documented in the parent plan.

## Idempotence and Recovery

All work is additive and pure ClojureScript. Re-running the implementation steps should not mutate drafts, browser storage, or external services. If the dense block creates unexpected covariance values, remove only the new `dense-block-estimator` option threading from `risk.cljs` to return to Milestone 1 behavior while keeping tests and helpers available for diagnosis.

If the dense block introduces a PSD repair warning in fixtures that previously did not need repair, do not remove the repair. Inspect whether the dense block and sparse cross-pairs are numerically inconsistent, then record the finding in `Surprises & Discoveries`. The final covariance must remain positive semidefinite after `risk.cljs` repair.

## Artifacts and Notes

Use these metadata conventions so downstream views and future backend contracts can inspect how the matrix was built:

- Dense/dense pair from Ledoit-Wolf block: `{:estimator :ledoit-wolf-dense :dense-block? true :calendar-kind :daily ...}`
- Pairwise mixed-frequency pair: `{:estimator :pairwise-mixed-frequency :calendar-kind :daily|:sparse-interval ...}`
- Dense-block unavailable warning: `{:code :dense-block-ledoit-wolf-unavailable :reason :missing-dense-native-price-series|:insufficient-dense-return-observations|:non-rectangular-dense-return-series :instrument-ids [...]}`
- Risk-estimation metadata: `:dense-block-estimator`, `:dense-block-instrument-ids`, `:dense-block-shrinkage`, `:dense-block-sample-count`, and `:dense-block-feature-count`.

Do not introduce a new dependency. The estimator already exists in `risk_ledoit_wolf.cljs`; Milestone 2 is wiring and metadata, not new math.
