# Optimizer Three-Year History Lookback

This ExecPlan is a living document. Keep `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` current while the work proceeds.

## Purpose / Big Picture

The optimizer requested a fixed ~1-year (`365` daily bars) return window for every asset, so a scenario like SP500 + XYZ100 showed only "248 returns · 360 days" - SP500 (a weekday-only index) capped the shared window even though its proxy/index-backed series has far more history available in the backend. A one-year window is too short for sample mean-variance to add value over naive 1/N weighting; the standard remedy is a longer estimation window (~3 years). This change raises the request window to ~3 years so the optimizer asks the history backend for all the (proxy-backed) history it has, then lets alignment intersect to the true shared window.

After this change `default-bars` (the single source of truth for the request window) is `1095` and is referenced everywhere instead of being re-hardcoded as `365`.

## Context Reference

Direct user request on 2026-06-19: extend the optimizer history lookback to ideally three years, motivated by academic research (DeMiguel, Garlappi & Uppal 2009, "Optimal Versus Naive Diversification") showing sample mean-variance only beats equal weighting with a long enough estimation window. Follows the same session's diagnosis that `lookback_days` was hardcoded to 365. User will supply the specific research backing; capture it in the Decision Log when received.

## Progress

- [x] (2026-06-19) Traced every place that assumed the 1-year window: `request-plan/default-bars` (source of truth), a duplicate literal `:bars 365` in `history-workflow/history-request`, and the defensive `lookback_days` fallback in `infrastructure/history-api-v2-client`. Confirmed the other `365`s in `returns`/`risk`/`calendar`/`history-series` are annualization factors, not the window.
- [x] (2026-06-19) Bumped `default-bars` 365 -> 1095 with rationale, and replaced the two duplicates so they reference the constant (single source of truth).
- [x] (2026-06-19) Refreshed the now-stale comment on `short-history-min-observations` (value unchanged at 360 = "< ~1 year"; it flags thin history, not "less than the full fetch window").
- [x] (2026-06-19) Added a regression test (`history_loader_test.cljs`) asserting the default plan requests `>= 1095` daily bars; confirmed the explicit-`:bars` client/plan tests are unaffected.
- [x] (2026-06-19) `npm test` green (4772 tests, 0 failures); `app` + `portfolio` builds compile with 0 warnings; `lint:namespace-boundaries` (new infra->application require), `lint:namespace-sizes`, `lint:docs` (this plan), and `lint:input-parsing` pass. Full `npm run check` blocked only by the unrelated pre-existing stale `docs/design-docs/core-beliefs.md`.
- [x] (2026-06-20) Verified live by probing `price-history.hyperopen.xyz/v1/optimizer/history-bundle` directly: the change works (SP500/XYZ100 window grew 360->521 days). But the ~3-year ceiling is backend-bound - SP500's proxy is REJECTED (`no_single_lineage_covers_window`), so the optimizer falls back to SP500's ~521-day native history. Filed as a backend issue (see Outcomes).
- [ ] Move this ExecPlan to `docs/exec-plans/completed/` after the backend proxy-coverage issue is resolved (the frontend portion is done).

## Surprises & Discoveries

- Observation: The api-v2 backend already reports `max_native_lookback_days` and `max_lookback_days` per instrument (proxy extends the latter), but the frontend does not consume them today - it just sends a single global `lookback_days`. So requesting `1095` simply lets the backend serve up to its per-instrument max; no per-asset request logic is needed for v1.
  Evidence: fields appear in `history_loader_api_v2_test.cljs` fixtures; a repo grep finds no `max_lookback`/`max-lookback` consumer in `src`.
- Observation: Funding history is summarized to a single annualized-carry scalar per asset (`:annualized-carry`/`:observation-count`), not a per-day series aligned to returns, so it does NOT need to match the return window.
  Evidence: `api-v2/bundle.cljs` `normalize-funding`.

## Decision Log

- Decision: Set the window to `1095` days (3 x 365) and keep it a single `def` constant (`request-plan/default-bars`) rather than a config/UI knob for now.
  Rationale: The user wants 3 years as the behavior; a constant matches the existing pattern and keeps the change minimal. A UI window selector (1y/2y/3y/Max) is a clean follow-up if desired.
  Date/Author: 2026-06-19 / Claude
- Decision: Leave the funding window at 1 year (`default-funding-window-ms`).
  Rationale: Funding feeds a forward-looking annualized-carry estimate where the recent regime is more representative, and it is independent of the return calendar (see Surprises). Extending it is a separate call.
  Date/Author: 2026-06-19 / Claude
- Decision: Do not change `short-history-min-observations` (360); only refresh its comment.
  Rationale: "Short" should still mean "< ~1 year of returns" regardless of the larger fetch window; raising it would spuriously card full-year assets.
  Date/Author: 2026-06-19 / Claude

## Validation and Acceptance

- `default-bars` is `1095` and the history-bundle request sends `lookback_days: 1095` by default; `history-workflow` and the api-v2 client reference the constant (no remaining hardcoded `365` window).
- Explicit-`:bars` tests (client mapping, request-plan with a passed `:bars`) still pass unchanged; any test asserting the default window is updated to 1095.
- `npm test` is green; `app` and `portfolio` builds compile clean.
- Manual: on the SP500/XYZ100 scenario, "history used" return count increases beyond 248 (subject to the backend actually returning >1 year of proxy history). If the backend caps `lookback_days`, the request is still valid and returns whatever it has.

Required final commands:

- `npm run check`
- `npm test`

## Outcomes & Retrospective

Frontend portion DONE and validated: `default-bars` 365->1095, single source of truth, tests + builds green. A live probe of the history backend confirmed the request now reaches past one year (SP500/XYZ100 window 360->521 days).

The remaining 3-year gap is BACKEND-bound, not a frontend issue. Direct probe of `POST price-history.hyperopen.xyz/v1/optimizer/history-bundle` at `lookback_days: 1095`:
- `perp:xyz:XYZ100` -> Tiingo QQQ proxy, 829 pts (~3 yrs, 2023-06 -> 2026-06), quality `passed`. Served fine.
- `perp:xyz:SP500` -> `lineage_kind: missing`, `quality: rejected`, 0 usable points. Warning `insufficient-candle-history`: native 76 pts + proxy 687 pts, `lineage_selection_reason: no_single_lineage_covers_window`. Rejected at EVERY window (540/730/900/1000/1095) and EVERY proxy policy tried. So the optimizer falls back to SP500's ~521-day native HL history; the ~2.6yr Tiingo proxy never reaches the engine.

Action: backend issue written up (2026-06-20) and handed to the user to route to the history-backend team. The ask is to serve SP500's proxy (proxy-only or stitched native+proxy, as XYZ100 already is) instead of returning `missing` - and ideally to return the longest available coverage rather than `missing` when no single lineage spans the full requested window. Move this plan to `completed/` once that lands. Frontend `1095` default stays (it correctly unlocks full native history and any served proxy; it does not drop assets, since rejected proxies fall back to native).
