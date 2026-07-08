# Tech Debt Tracker

## Purpose
Track known debt with clear owner and retirement path.

## Entries
- Debt summary: Locale-aware numeric parsing now covers audited high-traffic decimal boundaries and low-traffic integer input boundaries. Remaining debt is primarily regression prevention for newly added input surfaces.
  Owner team: Platform
  Impact: Uncovered/new input surfaces can still regress for international users if they bypass locale-aware parsing utilities.
  Retirement criteria: Maintain a recurring boundary audit for user-entered decimal paths, require locale-aware parsing in new numeric input transitions, enforce boundary lint guardrails in `npm run check`, and keep regression coverage plus gate validation (`npm test`, `npm run check`, `npm run test:websocket`) for each new input feature.
  Tracking reference: `/hyperopen/docs/exec-plans/completed/2026-03-02-international-number-formatting-migration.md`; `/hyperopen/docs/exec-plans/completed/2026-03-02-locale-input-parsing-boundaries-order-and-position-modals.md`; `/hyperopen/docs/exec-plans/completed/2026-03-02-locale-input-parsing-order-form-leverage.md`; `/hyperopen/docs/exec-plans/completed/2026-03-02-locale-input-parsing-currency-helper-standardization.md`; `/hyperopen/docs/exec-plans/completed/2026-03-02-locale-input-parsing-low-traffic-integer-boundaries.md`; `/hyperopen/docs/exec-plans/completed/2026-03-02-input-parsing-guardrail-lint.md`
- Debt summary: Namespace-size guardrails are now enforced, but the baseline repo still carries explicit temporary exceptions for oversized source and test namespaces in `/hyperopen/dev/namespace_size_exceptions.edn`.
  Owner team: Platform
  Impact: Large namespaces still concentrate change fan-out, make reviews slower, and increase merge pressure until the split wave retires those exceptions.
  Retirement criteria: Each entry must shrink to `<= 500` lines or move behind a thinner facade before its `:retire-by` date, and the registry must stay free of stale or expired entries while `npm run check`, `npm test`, and `npm run test:websocket` remain green.
  Tracking reference: `/hyperopen/docs/exec-plans/active/2026-03-24-architecture-audit-remediation-wave.md`; `/hyperopen/docs/exec-plans/deferred/2026-02-25-file-size-guardrail-exceptions-splitting-strategy-maintainability.md`; `/hyperopen/dev/namespace_size_exceptions.edn`
- Debt summary: Non-view imports from `hyperopen.views.*` are now enforced through `/hyperopen/dev/namespace_boundary_exceptions.edn`, and `DIP-01` plus `DIP-02` have retired the shared helper and view-model bridges, but three temporary exceptions still remain for the outer bootstrap and console-preload boundaries.
  Owner team: Platform
  Impact: These imports still blur ownership boundaries and let non-view code depend on view-layer helpers or models until the planned extractions land.
  Retirement criteria: The remaining bootstrap / console-preload bridges need either extraction or an explicit permanent owner before their `:retire-by` dates. The registry must stay aligned with the live imports so resolved exceptions are removed immediately.
  Tracking reference: `/hyperopen/docs/exec-plans/active/2026-03-24-architecture-audit-remediation-wave.md`; `/hyperopen/dev/namespace_boundary_exceptions.edn`
- Debt summary: The optimizer history-bundle load path has three profiled costs outside the 2026-07-08 loading-visibility/perf pass: the backend `POST /v1/optimizer/history-bundle` response itself took 6.2s for a 51-asset spectate universe; the Hyperliquid `/info` 429 backoff storm (four duplicate parallel POSTs per retry round) delayed holdings→universe seeding and thus the bundle request start by ~4s; and `request-builder/align-history` still burns ~1.6s on the main thread recomputing alignment when the bundle lands.
  Owner team: Portfolio
  Impact: Even with loading indicators, a cold optimizer open on a large spectated book takes ~20s to reach settled assumptions; the alignment task can drop input events when it runs.
  Retirement criteria: Backend bundle latency budget agreed and measured; `/info` request dedup or shared backoff removes duplicate parallel retries; alignment recompute moved off the main thread (portfolio-optimizer worker) or incrementalized. Verified against a fresh performance trace of the same spectate flow.
  Tracking reference: `/hyperopen/docs/exec-plans/active/2026-07-08-optimizer-proxy-loading-rail-and-normalize-perf.md` (trace analysis in Purpose section)
