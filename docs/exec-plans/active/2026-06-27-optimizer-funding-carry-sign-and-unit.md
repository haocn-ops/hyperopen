# Optimizer funding-carry: fix the expected-return sign and the annualization unit

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. Maintained per `docs/PLANS.md`.

## Purpose / Big Picture

The optimizer's per-instrument expected-return vector folds perp funding carry into each asset's return (`domain/returns.cljs` `estimate-expected-returns`). Two defects make that fold wrong, and both silently bias **every** perp optimization:

1. **Sign is backwards.** `total = return-part + funding-part` with `funding-part = (:annualized-carry …)`, and `:annualized-carry` carries the **raw** Hyperliquid funding sign (positive ⇒ longs pay shorts). The expected-return vector `μ` is the per-unit **long** return (weights are signed: `wᵀμ`). A long *pays* positive funding, so positive funding is a **cost** to a long and must be **subtracted**. Adding it makes the optimizer treat funding cost as income — biasing it to go **long** the highest-funding perps (the legs that bleed the most funding), the exact opposite of a carry trade.

2. **Annualization unit is ~8× too small.** `:annualized-carry = average-rate × funding-periods-per-year` with `funding-periods-per-year` defaulting to **1095** (= 365×3, an 8-hour-interval assumption). But Hyperliquid charges funding **hourly**, and `:average-rate` is the mean of per-1h `:funding-rate-raw` rows — so the correct factor is **8760** (= 24×365). `8760/1095 = 8` exactly. The markets screen (`utils/formatting.cljs` ×24×365) and the new execution Fund-8h path (`domain/rebalance.cljs`, `rate×8`) already use the correct hourly basis; the optimizer objective is the one inconsistent place.

Combined effect today: the optimizer applies funding with the **wrong sign** and at **1/8 the correct magnitude**. Fixing both makes a high-positive-funding perp's long expected return meaningfully *lower* (encouraging avoid/short), matching the UI and the real carry economics.

After this change a carry-aware solve will correctly penalize paying funding and reward earning it, at the right magnitude.

## Context References

- Parent ExecPlan: `docs/exec-plans/active/2026-06-26-optimizer-execution-tab-completion.md` (M8a Decision Log spawned this follow-up — the execution code deliberately bypassed `returns.cljs` and used the funding-policy sign + per-1h×8 directly, flagging the `returns.cljs` sign/unit as suspect). Direct maintainer request (this session) to verify and fix the funding-carry sign + the periods-per-year unit mismatch.
- Multi-agent audit (this session, workflow `wf_ca7d4553-4cd`): sign trace (+ devil's-advocate rebuttal), unit/period verification against the Hyperliquid docs, and full blast-radius mapping. Verdicts: sign = `add-is-wrong-should-subtract`; unit = `1095-is-wrong-should-be-8760`.
- Reference convention: `active_asset/funding_policy.cljs:178-187` (`:long -1`, `:short +1` ⇒ positive funding, long pays).
- HL docs: funding is paid every hour at ⅛ of the 8h formula rate; cap quoted per-hour (`hyperliquid-docs/trading/funding`); `fundingHistory` rows are hour-aligned.

## Scope (verified blast radius)

- **Sign fix → `domain/returns.cljs` only.** It is the single place carry crosses into the solver objective. Negating in `alignment.cljs` instead would corrupt `:annualized-carry`'s display/storage semantics, miss the api_v2 primary pass-through path (`api_v2/alignment.cljs:34`), and need 3 edits vs 1. `:annualized-carry` stays a raw, positive-funding-is-positive number everywhere it is produced/parsed.
- **Unit fix → the two `default-funding-periods-per-year` defs** (`history_loader/alignment.cljs:11-12`, `api_v2/legacy_fallback.cljs:6-7`; `history_loader.cljs:18-19` re-exports alignment's). 1095 → 8760.
- **`:funding-component`** (the decomposition) flips sign with the fix but is **presence/label-only** in views (`results_summary.cljs`, `scenario_detail_view.cljs` read `:funding-source`, not the numeric component) — no display breakage.
- **Independent / must NOT move:** the execution Fund-8h path (`domain/rebalance.cljs:338-355`) reads `:average-rate` (per-1h), gates on `:source`, applies its own `funding-window-hours=8` and HL sign — untouched by both fixes. `rebalance_test.cljs:441-469` is the guard that the fix did not leak into execution.
- **Tests pinning the old behavior:** only `returns_test.cljs:16-32` (sign+magnitude). The three alignment "unit" tests (`history_loader_test.cljs:153/162`, `history_loader_api_v2_legacy_fallback_test.cljs:50/67`, `history_loader_api_v2_test.cljs:164`) all pass `funding-periods-per-year` **explicitly** (100/1000/pass-through), so the default change does not touch them.

## Progress

- [x] (2026-06-27) Multi-agent audit completed: sign verdict `add-is-wrong-should-subtract` (no sign flip in the pipeline; μ is the long return; HL convention long-pays-on-positive); unit verdict `1095→8760` (per-1h rate, confirmed vs HL docs); blast radius mapped (sign fix is single-site in returns.cljs; default unit change breaks no existing test).
- [x] (2026-06-27) Implemented: `returns.cljs` now subtracts the long's funding carry (`funding-part (- (funding-carry …))`, `:funding-component` = the long's carry contribution); both `default-funding-periods-per-year` defs (`history_loader/alignment.cljs`, `api_v2/legacy_fallback.cljs`) bumped 1095→8760.
- [x] (2026-06-27) Tests: `returns_test.cljs` sign test updated (total 0.32→0.08, `:funding-component` 0.12→-0.12, renamed "subtracts") + a negative-carry (income-to-long) test; `history_loader_test.cljs` default-unit test (8760 → 0.0015×8760 = 13.14).
- [x] (2026-06-27) `npm run gates` 33/33 (5555 tests / 30119 assertions / 0 failures); only `returns_test.cljs` needed updating (no engine/frontier test relied on the old funding behavior); `rebalance_test.cljs:441-469` (execution Fund-8h) unchanged — the fix did not leak into the execution path.
- [ ] User review (this shifts solver outputs for any perp solve with funding history) + optional live spot-check on a real funded scenario.

## Surprises & Discoveries

- The raw HL `fundingRate` is per-**1h** (HL funds hourly at ⅛ of the 8h formula), not per-8h — so the optimizer's 1095 is an 8h-convention import bug. The app is internally inconsistent: markets screen + execution use 8760/×8 (correct), optimizer used 1095.
- The sign bug and the unit bug compound: wrong sign × 1/8 magnitude. Neither is caught by existing engine/frontier tests because almost all of them seed funding carry = 0.
- `:annualized-carry` is consumed numerically **only** by the optimizer objective (via `returns.cljs`); no view formats its magnitude — so the fixes are well-contained to the solver.

## Decision Log

- Decision: fix the SIGN by subtracting carry in `returns.cljs` (negate `funding-part`, keeping `total = return-part + funding-part` and reporting `:funding-component` as the long's carry contribution), NOT by negating `:annualized-carry` at the producer.
  Rationale: single solver-crossing site; preserves `:annualized-carry`'s raw display meaning; covers all loader paths (native + api_v2 + legacy) at once; one edit vs three.
  Date/Author: 2026-06-27 / Geronimo.
- Decision: fix the UNIT at the two `default-funding-periods-per-year` defs (1095→8760), leaving the multiply logic and the runtime-override path intact.
  Rationale: HL funding is hourly; 8760 matches the markets screen + execution path; existing unit tests pass explicit factors so the default change is non-breaking.
  Date/Author: 2026-06-27 / Geronimo.

## Outcomes & Retrospective

Landed 2026-06-27 (branch `feature/friendly-kirch-b26798`), gates 33/33. Three source edits + three test edits:
- `domain/returns.cljs` — `funding-part (- (funding-carry …))` (subtract); `:funding-component` is now the long's signed carry contribution. Single solver-crossing site, as the audit recommended.
- `application/history_loader/alignment.cljs` + `application/history_loader/api_v2/legacy_fallback.cljs` — `default-funding-periods-per-year` 1095→8760 (hourly HL funding). `history_loader.cljs` re-exports alignment's, so all loader paths pick it up.
- Tests: `returns_test.cljs` (sign + new negative-carry case), `history_loader_test.cljs` (default 8760).

Net behavior change: for any perp solve with market funding history, a positive-funding perp's long expected return now **falls** (carry subtracted) at the **correct ~8× magnitude** — the optimizer will favour shorting/avoiding expensive-to-hold longs and capturing funding income, matching the markets screen and the execution Fund-8h figure. Almost all engine/frontier tests seed carry = 0, so the suite was insensitive to the bug — which is exactly why it survived; consider a small funding-aware engine regression test as future hardening. No view formats `:annualized-carry`/`:funding-component` numerically, so there is no display fallout.

Retro note: the bug was a unit/convention import from an 8h-funding venue (Binance-style) into an hourly-funding venue (Hyperliquid), compounded by adding a short's-income number to a long's return. The audit's devil's-advocate pass (could ADD be right?) and the carry-trade sanity check were what made the verdict safe to act on.

## Validation & Acceptance

Required gates (per `AGENTS.md`): `npm run gates` (or `npm run check` + `npm test` + `npm run test:websocket`).

Acceptance:
- [x] A long position in a positive-funding perp has a LOWER expected return than its price-return alone (carry subtracted); a short benefits (signed weights). Covered by `returns_test.cljs` (subtract test + negative-carry income test).
- [x] Default funding annualization is 8760 (hourly); `:annualized-carry` magnitude is ~8× its prior value for a given `:average-rate`. Covered by `history_loader_test.cljs` default-unit test.
- [x] `returns_test.cljs` sign test updated; a default-unit test added; all other funding tests unchanged; `rebalance_test.cljs:441-469` (execution Fund-8h) stays green (no leak).
- [x] `npm run gates` PASS (33/33).
