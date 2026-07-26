# Optimizer execution: reframe the commit-moment "margin after %" as account leverage + free-margin headroom

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document must be maintained in accordance with `docs/PLANS.md` (and its detailed contract `/.agents/PLANS.md`).

## Purpose / Big Picture

On the optimizer Execution screen — the armed "confirm to send" moment — the commit band, the KPI strip, and the execution-health rail all show a single figure labelled **"margin after"** / **"Margin after"** / **"Cross-margin after"** with the sub-text **"post-rebalance maint."** / **"post-rebalance maintenance margin"**. A user about to send live orders cannot tell what that 38.24% means, and it does not match how the rest of the app talks about margin and leverage.

Three concrete problems sit behind that one figure:

1. **It is mislabelled.** The number is `(totalMarginUsed + Σ|Δnotional|/leverage) ÷ equity` — built from Hyperliquid's `marginSummary.totalMarginUsed`, which is **initial** margin (the collateral locked to open positions), plus an initial-margin-style impact. Calling it "maintenance margin" is simply wrong.
2. **It collides with the app's real liquidation metric.** Everywhere else the app shows **"Cross Margin Ratio"** / **"Unified Account Ratio"** = `maintenanceMargin ÷ equity`, the genuine liquidation gauge (liquidated at 100% / at-risk above 95%). A trader reading "Cross-margin after 38.24%" will reasonably believe they are at 38% of liquidation — they are not. The optimizer figure is a different, more conservative number with no liquidation meaning.
3. **A latent overstatement bug.** The impact term `|Δnotional| / leverage` divides by a per-instrument leverage **cap** that the user almost never sets, so it defaults to `1×` — i.e. it assumes every dollar of perp notional consumes a dollar of margin (full collateralisation). On any non-trivial rebalance this overstates margin consumption by the real leverage factor.

After this change the Execution screen states, in the app's existing vocabulary, **projected account leverage as a multiple** (e.g. "Account leverage after — 1.85×, was 1.79×") plus a **free-margin headroom** read ("$2.84M free of $4.62M equity"). This is the same family of figure as the "Cross/Unified Account Leverage" multiples shown in the account-equity panels, it is well-defined from data the optimizer already computes (account-wide gross exposure ÷ unified-corrected NAV), and it carries no false liquidation implication. The "maintenance"/"Cross-margin" wording is removed everywhere. You will see the new figure in the running app on the armed band, the KPI strip, and the health rail, and in tests that fail before the change and pass after.

This plan is scoped to the commit-moment margin figure only. It does not touch the optimizer's objective/constraint machinery, the order-routing logic, or the live-order confirmation gate.

## Context References

Public refs:

- Direct maintainer request (this session): "On the execution screen right where it's armed and about to be sent, what does margin after mean? Because that percentage figure doesn't look like the normal UX that's being used in other parts of the application, such as what the gross and net utilization are." Followed by: "Based on this, go ahead. Propose a fix. Create an execution plan for it, and then implement it." The maintainer then chose, from a presented fork, **"Account leverage (×) + headroom"** — match the app's existing leverage vocabulary — over a minimal relabel-as-initial-margin or a modelled liquidation-ratio reframe.

Repo artifacts:

- `AGENTS.md`, `docs/PLANS.md` — operating contract and planning rules.
- `docs/agent-guides/trading-ui-policy.md`, `docs/agent-guides/ui-foundations.md`, `docs/PRODUCT_SENSE.md` — trading-safety / honesty rules that bound this change (no weakening of live-order confirmation, honest status, no fake placeholders). The relabel here is a direct honesty fix under these rules.
- Sibling completed plan establishing this area's conventions: `docs/exec-plans/completed/2026-06-28-optimizer-flow-simplification.md` and the active `docs/exec-plans/active/2026-06-29-optimizer-execution-usability-hardening.md`.
- The unified-account NAV correction this figure depends on: `src/hyperopen/portfolio/optimizer/application/current_portfolio.cljs:406-417` (NAV = cash + non-cash spot collateral for unified/PM accounts).

Code-grounding (verified this session):

- Metric chain: `domain/rebalance.cljs` `margin-summary`/`utilization`/`margin-impact-usd`/`leverage-for-row` (≈409-471) → `application/rebalance_preview.cljs` `build-derived-preview` (≈159-192) → `application/execution.cljs` `build-execution-plan` copies `[:summary :margin]` verbatim (109-136) → `views/portfolio/optimize/execution_tab.cljs` armed band (≈214-263), KPI strip (≈523-562), health rail (≈631-675).
- App margin vocabulary: `views/account_equity/panels.cljs` ("Cross Margin Ratio", "Unified Account Ratio", "Cross/Unified Account Leverage"), `views/account_equity/metrics.cljs` (`cross-margin-ratio = maintenance/account-value`; `*-account-leverage = total-ntl-pos/account-value`), `views/account_equity/format.cljs` (`display-leverage` → "2.40x"). Optimizer side already has `format-multiple` ("Nx") and `format-usdc` in `views/portfolio/optimize/format.cljs`, and `format-knotional` in `execution_shared.cljs`.
- Data availability: the current-portfolio snapshot already carries account-wide, unified-corrected `:capital {:nav-usdc :gross-exposure-usdc :net-exposure-usdc :total-margin-used-usdc}` (`current_portfolio.cljs:440-446`), threaded into the engine request as `:current-portfolio` (`request_builder.cljs:506`).

Local scratch refs (non-authoritative): understanding sweep `wgr64fyno` (5-agent map of metric chain / display sites / app vocabulary / HL semantics / tests).

## Progress

- [x] (2026-06-29) Diagnosed the figure via a 5-agent understanding sweep: confirmed it is initial-margin utilization (`totalMarginUsed`-based), mislabelled as maintenance, colliding with the app's `maintenance ÷ equity` liquidation ratio, and that the impact divisor defaults to `1×` because `:perp-leverage` is an almost-always-unset per-instrument cap.
- [x] (2026-06-29) Presented the metric fork to the maintainer; **decision: account leverage (×) + free-margin headroom**, matching the account-equity panels' leverage vocabulary.
- [x] (2026-06-29) Confirmed data plumbing: account-wide gross exposure + unified NAV already on the snapshot/request; the execution plan copies the rebalance `:margin` map verbatim, so extending `margin-summary` reaches all three display sites with no new contract surface.
- [x] (2026-06-29) M1 — Domain: `margin-summary` now emits `:before-gross-leverage` / `:after-gross-leverage` / `:free-margin-usd` (alongside the retained utilization keys); `:capital-usd` doubles as equity. `leverage-for-row` falls back to a `:default-leverage` opt = account effective leverage (`account-effective-leverage` = gross ÷ margin-used) instead of `1×`; `build-rebalance-preview` computes account-wide `current-gross-usd` (opt `:current-gross-exposure-usdc`, else universe fallback) and the ready-row Δgross. `rebalance_preview.cljs` threads `:current-gross-exposure-usdc` from `[:current-portfolio :capital :gross-exposure-usdc]`. `rebalance.cljs` trimmed to exactly 500 lines (under the default budget).
- [x] (2026-06-29) M2 — View: all three execution_tab sites (armed band, KPI strip "Account leverage after", health rail "Account leverage after") now render the leverage multiple + free-margin headroom; every "maintenance" / "Cross-margin" / "margin after" string is gone; copy centralised in `execution_shared.cljs` (`margin-warn?`, `format-compact-usd`, `leverage-after-label`, `leverage-headroom-sub`). `execution_tab.cljs` stayed at 740/760.
- [x] (2026-06-29) M3 — Tests: updated the exact margin-map assertion in `rebalance_test.cljs`, added `build-rebalance-preview-projects-account-leverage-and-effective-margin-impact-test` (proves impact = notional ÷ effective leverage, not ÷ 1×, and the 2.0×→2.3× projection), and updated `execution_tab_test.cljs` fixtures/labels (incl. asserting the "1.85x" figure). Added a `dev/namespace_size_exceptions.edn` entry for the grown `rebalance_test.cljs` (536 lines, max 560, retire 2026-09-30), matching the optimizer test-suite convention.
- [x] (2026-06-29) Browser-QA discovery: the optimizer smoke `optimizer-view-model-routes.smoke.spec.mjs` had a **pre-existing** failure unrelated to this change — it clicked the retired `portfolio-optimizer-scenario-tab-rebalance` and asserted `?otab=rebalance`, both removed when the Rebalance preview tab was retired (`1e0769e1`). Repointed that block to the Execution tab (`?otab=execution`, `portfolio-optimizer-execution-tab-shell`), the surface the rebalance now stages into.
- [x] (2026-06-29) Validation: `npm run gates` 34/34 PASS (5597 tests / 30257 assertions; all five ClojureScript builds compile; lints incl. namespace-sizes green). Optimizer Playwright smoke green at `--workers=1` (the repointed retained-draft test passes; full-spec clean re-run in progress at close-out). Live nREPL spot-check not possible — the `:app` JS runtime was not attached to the dev server's REPL this session ("No available JS runtime"); the figure is instead proven by the domain regression (concrete 2.0×→2.3× / impact-600 math) and the display tests (render "Account leverage after" / "1.85x" / "leverage").
- [ ] Land: commit on the feature branch and (on review) merge to local `main`, then move this plan to `completed/`.

## Surprises & Discoveries

- The retired "Rebalance preview" card renderer (`views/portfolio/optimize/results_rebalance_preview.cljs:50-51`, label "Margin After") is **dead UI**: its only mount (`results_panel.cljs:67`) is gated on `include-rebalance?`, and the sole `results-panel` caller (`scenario_detail_view.cljs:503`) passes `include-rebalance? false`. Left out of scope; noted here so the stale label is not mistaken for a live site.
- `application/execution_test.cljs` hand-builds its rebalance-preview `:margin {:after-used-usd 500}` and asserts the plan copies it verbatim — it does **not** call `build-rebalance-preview`, so the domain change does not touch it.
- `format-multiple` (optimizer) and `display-leverage` (account-equity) already render the "Nx" multiple the maintainer asked to match — no new formatter needed.

## Decision Log

- **Metric = account gross leverage, not margin utilization.** Chosen by the maintainer from a 3-way fork. Rationale: it is the figure-family the rest of the app already shows ("Cross/Unified Account Leverage"), it is exactly defined from optimizer data (account-wide gross ÷ unified NAV), and it carries no false liquidation meaning. Rejected: (a) keep the % but relabel "initial margin used" — honest but introduces a term shown nowhere else; (b) model the maintenance-ratio — most consistent label but the optimizer cannot precisely compute post-trade maintenance margin for new positions, so it would be a fabricated approximation next to a real liquidation gauge.
- **Account-wide, projected.** `before-gross-leverage` uses the account-wide `:gross-exposure-usdc` (all dexes, unified-corrected); `after-gross-leverage` applies only the **ready** rows' Δgross (`Σ(|target|−|current|)·equity`) so blocked/within-tolerance rows — which will not trade — do not move the projection. Fallback when `:gross-exposure-usdc` is absent: the universe rows' own current gross (degrades gracefully in tests/fixtures).
- **Fix the `1×` impact default at the same time.** The free-margin headroom depends on `after-used = totalMarginUsed + Σ|Δnotional|/leverage`. With `:perp-leverage` unset, the old default `1×` overstates margin consumed. Replace the default with the account's **effective** leverage (`gross-exposure ÷ totalMarginUsed`) when both are positive, else `1×` (correct for an all-spot / no-position book). Explicit per-instrument caps still win, so the existing `leverage 5 → impact 400` test is unchanged.
- **Keep the warning semantics, retarget the trigger.** `margin-warning` still fires on margin utilization (`after-used ÷ equity` > 0.8 / > 1.0) — now honest because the impact is honest — and still drives the red value + "breach"/"ok" status; it just no longer mislabels the headline as maintenance. Free-margin is shown on the safe path; thin-headroom copy on the warning path.
- **No new contract surface.** The `:margin` map is internal to the rebalance/execution summary (not spec-validated), so new keys are additive; the execution plan copies it through unchanged.

## Outcomes & Retrospective

- Shipped: the commit-moment figure is now **account leverage (×) + free-margin headroom** in the app's existing vocabulary, on all three Execution-screen sites. No "maintenance" / "Cross-margin" wording remains, so the figure no longer impersonates the account-equity liquidation ratio. The latent `1×` margin-impact default was replaced with the account's real effective leverage, so the headroom is honest on non-trivial rebalances.
- Validation: `npm run gates` 34/34 (5597 tests / 30257 assertions; five CLJS builds compile; namespace-sizes green). Optimizer smoke green at `--workers=1`.
- Namespace-budget deltas: `rebalance.cljs` 471→500 (at the default ceiling — flagged for a domain split as a follow-up); `execution_shared.cljs` 63→107; `execution_tab.cljs` unchanged at 740/760; new exception for `rebalance_test.cljs` (max 560, retire 2026-09-30).
- Follow-ups: (1) **Done (2026-06-29)** — the dead `results_rebalance_preview.cljs` "Margin After" card was removed entirely (file deleted; `results_panel.cljs` `include-rebalance?` param/branch dropped; `scenario_detail_view.cljs` arg dropped). Two unit assertions flipped to `nil?`, and the already-stale `portfolio-regressions.spec.mjs` Spectate read-only test (which reached Execution via the retired rebalance tab + the dead card button, asserting "Margin after") was repointed to the Execution scenario tab asserting "Account leverage after". (2) `rebalance.cljs` is at the 500-line default ceiling; the leverage/margin helpers are a clean split candidate. (3) a live before/after leverage spot-check on a real unified account once a browser runtime is attached.

## Context and Orientation

The commit-moment figure is produced once in the domain and rendered three times in the view:

- Produced: `domain/rebalance.cljs` `margin-summary` (the `:margin` map), assembled in `build-rebalance-preview`, fed opts by `application/rebalance_preview.cljs` `build-derived-preview`, copied verbatim into the execution plan by `application/execution.cljs` `build-execution-plan`.
- Rendered: `views/portfolio/optimize/execution_tab.cljs` — `armed-band` (inline copy), `kpi-strip` (the "Margin after" KPI card), `health-rail` (the "Cross-margin after" diag).

## Plan of Work

### Milestone 1 — Domain: account-leverage + honest headroom
Extend `margin-summary` with `:equity-usd`, `:free-margin-usd`, `:before-gross-leverage`, `:after-gross-leverage`; compute account-wide before-gross (opt `:current-gross-exposure-usdc`, fallback universe current gross) and ready-row Δgross; replace the `1×` impact default with account-effective leverage; thread `:current-gross-exposure-usdc` from `[:current-portfolio :capital :gross-exposure-usdc]` in `build-derived-preview`. Keep `rebalance.cljs` under 500 lines.

### Milestone 2 — View: leverage (×) + headroom on all three sites
Replace the armed-band inline "margin after %", the KPI "Margin after" card, and the health-rail "Cross-margin after" diag with the leverage multiple + free-margin headroom; strip "maintenance"/"Cross-margin" wording; keep the existing data-roles (`...-kpi-margin`, `...-armed-figures`) for selector stability. Add small copy helpers to `execution_shared.cljs` to keep `execution_tab.cljs` under its 760-line budget.

### Milestone 3 — Tests
Update `rebalance_test.cljs` exact margin-map assertion (new keys/values); update `execution_tab_test.cljs` fixtures (add leverage keys) and label assertions; add a regression asserting the rendered leverage figure and that the impact divisor uses account-effective leverage, not `1×`.

## Validation and Acceptance

- `npm run gates` → 34/34 PASS (incl. all five ClojureScript builds and `lint:namespace-sizes`).
- Smallest relevant optimizer Playwright smoke green at `--workers=1`.
- Live nREPL spot-check on the armed Execution screen: the figure reads as a leverage multiple consistent with the account-equity "Unified Account Leverage", with a positive free-margin headroom.
- No "maintenance" / "Cross-margin" wording remains on the Execution screen.

## Idempotence and Recovery

All edits are pure source changes; re-running gates/tests is idempotent. The `:margin` map gains keys only — older callers ignoring them are unaffected. If the leverage projection is wrong, the safe fallback (universe current gross, `1×` effective leverage) keeps the figure finite and non-misleading.

## Artifacts and Notes

Understanding sweep `wgr64fyno`. No generated artifacts.

## Interfaces and Dependencies

- Depends on the unified-account NAV correction in `current_portfolio.cljs` (already landed) for a correct denominator.
- No public API, no new `:actions/*` or `:effects/*` contract, no Lean/formal surface.
