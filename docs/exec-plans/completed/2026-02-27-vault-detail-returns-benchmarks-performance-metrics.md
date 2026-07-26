# Vault Detail Returns, Benchmarks, and Performance Metrics Parity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained under `/hyperopen/.agents/PLANS.md` and follows that contract.

## Purpose / Big Picture

After this change, a user on `/vaults/<vaultAddress>` can switch the chart to `Returns`, add/remove benchmark symbols, and compare the vault against those benchmarks visually. The same page also exposes a `Performance Metrics` tab that renders the same metric families used on the portfolio page performance metrics component, including benchmark comparison columns. A user can verify by selecting `Returns`, adding a benchmark, and opening the `Performance Metrics` tab to see populated rows such as Sharpe and Max Drawdown.

## Progress

- [x] (2026-02-27 16:30Z) Reviewed UI/runtime guardrails and vault detail + portfolio implementations (`docs/FRONTEND.md`, UI guides, `src/hyperopen/views/portfolio_view.cljs`, `src/hyperopen/views/portfolio/vm.cljs`, `src/hyperopen/views/vault_detail_view.cljs`, `src/hyperopen/views/vaults/detail_vm.cljs`, `src/hyperopen/vaults/actions.cljs`).
- [x] (2026-02-27 16:49Z) Extended vault action/state runtime for returns benchmark state and fetch flows, including new action IDs and runtime wiring/contracts.
- [x] (2026-02-27 17:09Z) Implemented vault detail VM support for returns series, benchmark-aligned series, and performance metrics model projection.
- [x] (2026-02-27 17:33Z) Implemented vault detail view support for returns benchmark selector/chips/legend and performance metrics tab rendering.
- [x] (2026-02-27 17:44Z) Ran validation gates and confirmed success: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-02-27 17:45Z) Prepared completion handoff and moved this ExecPlan to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: `set-vaults-snapshot-range` is used by both vault list and vault detail contexts.
  Evidence: Existing action updates both list pagination and detail chart hover state in one handler; benchmark-fetch behavior had to be route-gated to avoid unnecessary list-page candle fetches.

## Decision Log

- Decision: Keep benchmark state vault-specific (`:vaults-ui :detail-returns-benchmark-*`) instead of reusing portfolio UI state.
  Rationale: Prevents cross-page state coupling between portfolio and vault detail interactions.
  Date/Author: 2026-02-27 / Codex

- Decision: Reuse `hyperopen.portfolio.metrics` for metric computation instead of forking formulas.
  Rationale: Ensures vault `Performance Metrics` tab stays numerically aligned with portfolio metric definitions.
  Date/Author: 2026-02-27 / Codex

- Decision: Route-gate vault benchmark candle fetches to detail routes only.
  Rationale: Avoids list-page snapshot-range interactions triggering unrelated candle requests.
  Date/Author: 2026-02-27 / Codex

## Outcomes & Retrospective

Completed. Vault detail now supports a `Returns` chart mode with benchmark add/remove controls and multi-series rendering, plus a `Performance Metrics` tab backed by the portfolio metrics computation layer. All required validation gates passed with no failures.

## Context and Orientation

Vault detail rendering is in `/hyperopen/src/hyperopen/views/vault_detail_view.cljs` and data projection is in `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs`. Vault UI actions are defined in `/hyperopen/src/hyperopen/vaults/actions.cljs` and registered via `/hyperopen/src/hyperopen/runtime/collaborators.cljs`, `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`, and `/hyperopen/src/hyperopen/registry/runtime.cljs`. Action argument contracts are in `/hyperopen/src/hyperopen/schema/contracts.cljs`, and effect ordering contracts are in `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`.

## Plan of Work

Complete by validating and fixing regressions. Ensure benchmark fetch actions remain projection-first and covered by effect-order contracts. Confirm tests and state defaults for new vault benchmark fields and performance metrics tab shape.

## Concrete Steps

From `/hyperopen`:

1. Run `npm run check`.
2. Run `npm test`.
3. Run `npm run test:websocket`.
4. If all pass, move this file to `/hyperopen/docs/exec-plans/completed/` and update this document with completion notes.

## Validation and Acceptance

Acceptance is met when:

1. Vault detail chart has a `Returns` tab and still supports `Account Value` and `PNL`.
2. In `Returns`, benchmark search/add/remove controls render and update chart series.
3. `Performance Metrics` tab appears and shows metric rows aligned with portfolio metric families.
4. Required repository gates pass.

## Idempotence and Recovery

All changes are additive and can be re-run safely. If validations fail, fix failing assertions/compilation and rerun the same commands. No destructive migrations are involved.

## Artifacts and Notes

No external artifacts required for this pass.

## Interfaces and Dependencies

The implementation reuses:

- `hyperopen.portfolio.actions/returns-benchmark-candle-request` for benchmark candle interval/bar selection.
- `hyperopen.portfolio.metrics/*` for daily-return derivation and metrics computation.

No new external libraries are introduced.

Revision note (2026-02-27): Created during implementation to track vault detail returns + benchmarks + performance metrics parity work and required validations.
Revision note (2026-02-27): Updated progress/outcomes after successful validation gates and completion.
