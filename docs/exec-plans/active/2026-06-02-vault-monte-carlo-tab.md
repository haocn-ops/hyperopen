# Add A Vault Monte Carlo Forecast Tab

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The portfolio account-tabs row gained a `Monte Carlo` tab (see `2026-06-02-portfolio-monte-carlo-tab.md`) that bootstrap-resamples a strategy's realized daily returns to map the range of outcomes the same strategy could produce. Vault detail pages show the same kind of realized history (the performance tearsheet) but offer no forward range-of-outcomes view. This change brings the identical Monte Carlo forecast to the vault detail page so it works for **any** vault.

The vault's `chart-section` view model already assembles a `benchmark-context` containing `:strategy-cumulative-rows` and `:strategy-source-version` — the exact inputs the existing engine consumes. So the numbers come from the vault's real in-app returns, with no new fetch.

The user-visible proof: on `/vaults/<address>`, the activity panel gains a `Monte Carlo` tab (with a `New` badge) next to `Performance Metrics`. Opening it shows the same intro/method header, control strip (simulations, horizon, bust-drawdown threshold, return goal, seed, re-run), simulated-equity-paths chart with P5–P95 / P25–P75 bands, probability-of-goal / probability-of-bust rail, terminal-outcome percentile table (scaled to vault TVL), and four distribution histograms. Changing a control re-runs the simulation and updates every panel; a vault with fewer than 30 realized daily returns shows the explanatory low-history state.

## Context References

Public refs:

- Direct user request on 2026-06-02: "Just as we have implemented Monte Carlo on the portfolio page, let's implement it on vaults page as well, so it works with any vault."

Repo artifacts:

- `/hyperopen/docs/exec-plans/active/2026-06-02-portfolio-monte-carlo-tab.md` — the portfolio precedent this mirrors.
- `/hyperopen/src/hyperopen/portfolio/montecarlo/engine.cljs` — the pure, deterministic engine, reused unchanged.
- `/hyperopen/src/hyperopen/views/vaults/detail_vm/chart_section.cljs` — already builds `benchmark-context` (`:strategy-cumulative-rows`, `:strategy-source-version`); the MC inputs are read from here.
- `/hyperopen/src/hyperopen/views/vaults/detail/activity/shell.cljs` — the activity panel that hosts the new tab.

## What Will Be Built

Reuse without changes (required from vault code):

- `hyperopen.portfolio.montecarlo.engine` — the bootstrap engine.
- `hyperopen.views.portfolio.montecarlo.chart` — canvas spaghetti/histogram renderers.
- `hyperopen.views.portfolio.montecarlo.format` — display formatters.

Generalized for reuse across both surfaces (additive; portfolio behavior preserved exactly):

- `hyperopen.portfolio.montecarlo.actions` — adds state-path-generic `controls-for` / `set-control-for` / `rerun-for`; the existing `controls` / `set-portfolio-monte-carlo-control` / `rerun-portfolio-monte-carlo` become thin wrappers.
- `hyperopen.views.portfolio.montecarlo.{panel,controls,summary,distributions}` — read a `:chrome` map from the model (`:data-role-prefix`, `:set-control-action`, `:rerun-action`, `:root-class`, copy strings, `:equity-label`). Portfolio and vault each pass their own chrome.
- `src/styles/surfaces/montecarlo.css` — scope renamed from `.portfolio-monte-carlo` to a neutral `.monte-carlo`; both surface roots carry that class. No new CSS file (so no `main_css_split` change).

New (vault-specific):

- `hyperopen.vaults.montecarlo.actions` — vault control state at `[:vaults-ui :monte-carlo]`, reusing the generalized helpers; `set-vault-monte-carlo-control` / `rerun-vault-monte-carlo` handlers.
- `hyperopen.views.vaults.detail_vm.montecarlo` — cached read model. Derives realized returns from the chart-section `benchmark-context`, gates on a 30-sample minimum, scales the percentile table to vault TVL, runs the engine in unit-equity space so live ticks do not bust the cache.

Wiring: `:monte-carlo` added to the vault activity-tab set + normalization (`vaults/application/ui_state.cljs`), the activity-tab model (`vaults/detail/activity.cljs`), lazy model build on the MC tab (`views/vaults/detail_vm/chart_section.cljs`), render case + `New` badge (`views/vaults/detail/activity/shell.cljs`), cache reset (`views/vaults/detail_vm/cache.cljs`), and action registration (`runtime/collaborators/vaults.cljs`, `schema/runtime_registration/vaults.cljs`, `schema/contracts/action_args.cljs`).

## Acceptance Criteria

- `npm run check`, `npm test`, and `npm run test:websocket` pass, including new vault action-normalization tests and the preserved portfolio tests.
- In a running app, the vault detail activity panel shows a `Monte Carlo` tab with a `New` badge next to `Performance Metrics`; opening it renders the chart, probability rail, percentile table (in dollars scaled to TVL), and four histograms from the vault's real realized returns; changing a control re-runs and updates all panels; a vault with fewer than 30 realized daily returns shows the explanatory low-history state.
- The existing portfolio Monte Carlo tab is visually and behaviorally unchanged (same data-roles, copy, and actions).

## Progress

- [x] M1 — Generalized the portfolio MC control layer (`controls-at`/`set-control-at`/`rerun-at`) and added vault MC handlers under `[:vaults-ui :monte-carlo]` in `detail-commands`, exposed via the `vaults.actions` facade and registered in collaborators + schema; unit tests in `detail_commands_test` and `ui_state_test`.
- [x] M2 — Parameterized the shared MC views (`panel`/`controls`/`summary`/`distributions`) via a `:chrome` map; portfolio supplies its chrome with identical data-roles/copy/actions; CSS scope neutralized to `.monte-carlo`.
- [x] M3 — Vault MC read model (`detail_vm/montecarlo`) built lazily in `chart-section` on the MC tab, scaled to TVL; activity-tab model + render case + `New` badge wired in the activity shell.
- [x] M4 — `npm run check`, `npm test` (4129 tests), and `npm run test:websocket` (527 tests) green; browser-verified against a real vault (HLP): the MC tab renders the chart, probability rail, TVL-scaled percentile table, and four histograms from all-time realized returns, control changes re-run the engine, and the portfolio MC tab is unchanged (data-roles, copy, scope/range method tag).
- [ ] Follow-up — optional: when the viewer is a depositor, offer a "Your stake" dollar basis (their deposit) alongside the TVL-based projection; and a shared hover tooltip on the equity chart (P5/median/P95 at the hovered day) for both surfaces.

## Surprises & Discoveries

- The vault detail page has no Monte Carlo-specific range control: the chart range selector lives inside the performance-metrics card, which is replaced (not just hidden) when the MC tab is active. So tying MC returns to the selected snapshot range (the portfolio's approach) left the default 30D window with only ~3 realized daily returns for HLP and no way to widen it. Switching the vault MC to always resample the vault's **all-time** realized returns fixed this and is the right basis for a forecast anyway. Browser-verified: HLP then renders a healthy ready surface (1,000 paths, sensible Sharpe/vol/drawdown distributions).
- The vault `chart-section` already assembles the exact engine input shape (`benchmark-context` → `:strategy-cumulative-rows` / `:strategy-source-version`), so no new data plumbing or fetch was needed — only an all-time variant of the same returns derivation.
- The CSS was scoped entirely under `.portfolio-monte-carlo` (89 rules); renaming the scope to a neutral `.monte-carlo` and having each surface root add its own class let both tabs share one stylesheet with no duplication and no new CSS file (so no `main_css_split` change).

## Decision Log

- Placement: the activity-panel tab strip, next to `Performance Metrics`, mirrors the portfolio MC's placement next to its Performance Metrics tab — the strongest structural parallel.
- Dollar base for the percentile table: vault **TVL** (always present for any vault, so the surface works universally) rather than the viewer's personal deposit (absent for spectators / non-depositors).
- Reuse over duplication: the engine, canvas renderers, and formatters are surface-agnostic and are required directly; the action + view layers are generalized with a `:chrome` config rather than forked, keeping one source of truth and honoring the repo's reuse principles.
- Lazy engine run: the MC model is built only when the activity tab is `:monte-carlo`, matching the portfolio's tab-`:render`-closure laziness so the engine does not run on unrelated vault re-renders.

## Outcomes & Retrospective

The vault detail activity panel now has a `Monte Carlo` tab (with a `New` badge) next to `Performance Metrics`, working for any vault. Browser verification against the live HLP vault rendered the full surface from real all-time returns — spaghetti/confidence-band equity chart, 91% probability-of-goal / 0% probability-of-bust rail, a terminal-outcome table scaled to vault TVL ($504M–$915M projected), and four distribution histograms — and switching simulations from 1,000 to 2,500 re-ran the deterministic engine and updated every panel with no console errors.

Net complexity is small and well-isolated: the pure engine, canvas renderers, and formatters are reused verbatim; the action + view layers were generalized (state-path-parameterized control helpers; a `:chrome` config on the shared views) so portfolio and vault share one implementation with per-surface copy/data-roles/actions; and the only vault-specific code is a thin cached read model plus the activity-tab wiring. The portfolio Monte Carlo tab was re-verified unchanged (same `portfolio-monte-carlo` data-roles, "your realized returns" copy, and scope/range method tag), with no vault-role leakage. Remaining work is the optional depositor-relative dollar basis and a shared chart hover tooltip, neither of which affects the numbers.
