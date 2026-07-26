# Portfolio Performance Metrics QuantStats Parity Remediation

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the metrics shown in the `/portfolio` Performance Metrics tab will match QuantStats report semantics for the discrepancies identified in the parity audit: annualization basis for CAGR-family rows, rolling-window boundary inclusion, and VaR/CVaR sign conventions. A contributor can verify success by opening the performance metrics tab and seeing corrected values, then running required test and check gates without regressions.

## Progress

- [x] (2026-02-26 00:00Z) Reviewed parity audit report and isolated 11 discrepancy rows to fix.
- [x] (2026-02-26 20:49Z) Implemented parity fixes in `/hyperopen/src/hyperopen/portfolio/metrics.cljs`:
  - removed elapsed-span `:years` overrides from `compute-performance-metrics` CAGR-family rows;
  - switched rolling-window row inclusion to timestamp anchors (`:time-ms` with day fallback);
  - normalized `:daily-var` and `:expected-shortfall` to `-abs(...)` to match QuantStats report output.
- [x] (2026-02-26 20:50Z) Removed VM-level `:cagr-years` overrides in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`.
- [x] (2026-02-26 20:51Z) Updated parity tests in `/hyperopen/test/hyperopen/portfolio/metrics_test.cljs`:
  - replaced elapsed-span CAGR expectations with period-count annualization expectations;
  - added window-boundary timestamp inclusion coverage;
  - added VaR/CVaR sign normalization coverage.
- [x] (2026-02-26 20:54Z) Ran required validation gates:
  - `npm run check` (pass)
  - `npm test` (pass: `1470` tests, `7383` assertions)
  - `npm run test:websocket` (pass: `153` tests, `701` assertions)
- [x] (2026-02-26 20:54Z) Updated ExecPlan outcomes and prepared move to completed plans folder.

## Surprises & Discoveries

- Observation: The largest parity drift comes from report-layer semantics, not core function formulas.
  Evidence: `/hyperopen/docs/qa/performance-metrics-quantstats-parity-report-2026-02-26.md` marks function-level matches for most ratios while flagging compute/wiring-level differences.
- Observation: VM-level `:cagr-years` injection was sufficient to keep function-level parity tests green while still drifting from QuantStats report-level behavior.
  Evidence: Pre-change `compute-performance-metrics` accepted `:cagr-years`; post-change tests validate parity without this override and all gates remain green.

## Decision Log

- Decision: Fix parity by changing production metric wiring and compute logic instead of introducing UI-only post-processing.
  Rationale: QuantStats semantics are defined in metric construction layer (`reports.metrics`), so parity should live in Hyperopen metric computation output.
  Date/Author: 2026-02-26 / Codex
- Decision: Keep `cagr` support for explicit `:years` in the function API but remove all production call sites that passed elapsed-span years.
  Rationale: Preserves utility/test flexibility while restoring parity in user-visible metric outputs.
  Date/Author: 2026-02-26 / Codex

## Outcomes & Retrospective

Remediation is complete for all discrepancy categories identified in the parity audit:

- CAGR-family metrics now annualize by return-count periods (QuantStats semantics) in production metric output.
- Rolling windows (`3M`, `6M`, `1Y` and dependent annualized windows) now evaluate inclusion using timestamp anchors instead of day-midnight parsing.
- Daily VaR and Expected Shortfall values now follow QuantStats report sign conventions (`-abs(...)`).

Validation results:

- `npm run check` passed.
- `npm test` passed (`1470` tests, `7383` assertions, `0` failures).
- `npm run test:websocket` passed (`153` tests, `701` assertions, `0` failures).

No further implementation work remains for this remediation scope.

## Context and Orientation

Performance metrics are computed in `/hyperopen/src/hyperopen/portfolio/metrics.cljs` and assembled for the portfolio page in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`. The rendered table itself lives in `/hyperopen/src/hyperopen/views/portfolio_view.cljs`, but this remediation does not require UI structural changes.

The parity audit in `/hyperopen/docs/qa/performance-metrics-quantstats-parity-report-2026-02-26.md` identifies three root causes:

1. Hyperopen annualizes CAGR-family metrics using elapsed calendar span (`rows-span-years`) while QuantStats uses `len(returns) / periods`.
2. Hyperopen rolling windows use parsed day-midnight in `rows-since-ms`; QuantStats compares full datetime index timestamps.
3. Hyperopen emits raw VaR/CVaR values; QuantStats report rows normalize sign as `-abs(var)` and `-abs(cvar)`.

## Plan of Work

First, update `compute-performance-metrics` in `/hyperopen/src/hyperopen/portfolio/metrics.cljs` so all CAGR-family rows (`:cagr`, `:calmar`, `:y3-ann`, `:y5-ann`, `:y10-ann`, `:all-time-ann`) rely on the existing `cagr` default annualization basis (`count returns / periods-per-year`) and no longer pass elapsed-span `:years` values.

Second, update `rows-since-ms` in the same file to compare threshold against each row's concrete timestamp (`:time-ms`, with a day parse fallback), matching QuantStats timestamp cutoff behavior.

Third, adjust `:daily-var` and `:expected-shortfall` in `compute-performance-metrics` to emit `-abs(...)` values, matching QuantStats report semantics.

Fourth, remove the now-unwanted `:cagr-years` wiring from `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` so VM always uses parity-consistent compute behavior.

Fifth, update `/hyperopen/test/hyperopen/portfolio/metrics_test.cljs` by replacing elapsed-span CAGR expectations with QuantStats annualization expectations and adding tests that pin timestamp window behavior and VaR/CVaR sign normalization.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/portfolio/metrics.cljs` to apply parity logic changes.
2. Edit `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` to remove `:cagr-years` override flow.
3. Edit `/hyperopen/test/hyperopen/portfolio/metrics_test.cljs` to match the new parity semantics.
4. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
5. Move this plan to `/hyperopen/docs/exec-plans/completed/` after gates pass and update all living sections.

## Validation and Acceptance

Acceptance is met when:

- The discrepancy categories in `/hyperopen/docs/qa/performance-metrics-quantstats-parity-report-2026-02-26.md` are addressed by code changes:
  - CAGR-family metrics use QuantStats annualization basis.
  - Rolling window cutoff is timestamp-based.
  - Daily VaR and Expected Shortfall are negative by report convention.
- Updated tests pass in `hyperopen.portfolio.metrics-test` and no regressions appear elsewhere.
- Required repo gates all pass.

## Idempotence and Recovery

These edits are additive and deterministic; rerunning the steps produces the same output. If any gate fails, rerun the failing command after fixing assertions. No schema/data migrations or destructive operations are involved.

## Artifacts and Notes

Primary audit input:

- `/hyperopen/docs/qa/performance-metrics-quantstats-parity-report-2026-02-26.md`

## Interfaces and Dependencies

No public API contracts are changed. Implementation remains in pure metric functions and VM derivation layers. Existing UI formatting contracts (`:percent`, `:ratio`, `:date`, `:integer`) remain unchanged.

Plan revision note: 2026-02-26 00:00Z - Initial remediation ExecPlan created from the completed parity audit to drive implementation of all identified discrepancies.
Plan revision note: 2026-02-26 20:54Z - Completed implementation, validation, and retrospective updates; plan ready for archive in completed folder.
