# Migrate Monte Carlo To The QuantStats Shuffle Method (Add A Method Toggle)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The portfolio and vault detail pages each have a `Monte Carlo` tab. Today both run one algorithm: a bootstrap that draws the strategy's realized daily returns *with replacement* over a user-chosen future horizon (30/90/180/365 days). That algorithm has a defect the user observed in production: for a vault with ~99 days of strongly positive realized history, a 365-day forecast reports a P5 ("worst case") total return of roughly +744% — higher than the vault's own realized return of +132%. A worst case that beats reality is implausible. The cause is not a coding typo: drawing 365 i.i.d. days from a 99-day sample with a large positive daily mean compounds that mean over a year, so *every* path — even the 5th-percentile one — inflates far past what actually happened.

The library the tab was modeled on, QuantStats (`https://github.com/ranaroussi/quantstats`, file `quantstats/_montecarlo.py`), does something fundamentally different. It **shuffles** the realized returns rather than resampling them: each simulation is a random *reordering* (a permutation, no replacement) of the exact same set of daily returns, and the simulation length always equals the realized history length (there is no separate forecast horizon). Because reordering cannot change the product of the daily growth factors `(1 + r)`, every shuffled path ends at the **exact same terminal value** — the vault's realized return. What differs between paths is only the *route* taken to get there, which changes the **maximum drawdown** (and other path-dependent quantities). In QuantStats, "worst case" terminal return is therefore equal to reality by construction, never better; the genuine distribution of interest is drawdown.

After this change, a user can do two things they could not do before. First, open the Monte Carlo tab on either surface and choose a **method** with a toggle: "Sequence risk" (the faithful QuantStats shuffle) or "Forecast" (the bootstrap). Second, in either mode they see numbers that are no longer implausible: in Sequence-risk mode the terminal return is pinned to the realized return and the tab foregrounds the drawdown distribution and the probability that some ordering of the same returns would have breached a drawdown threshold; in Forecast mode the horizon is clamped so it can never exceed the realized history length, which removes the "worst case beats reality" artifact while keeping the familiar forward-looking fan, terminal percentile table, and four histograms.

How to see it working: run the app (`npm run dev`, then open `http://localhost:8080`), spectate a vault with real history at `/vaults/<address>`, open the `Monte Carlo` tab, and toggle the method. In Sequence-risk mode the "Probability of goal" is binary (the realized return either clears the goal or it does not) and the terminal outcome is a single realized figure; the max-drawdown histogram and "Probability of bust" remain rich. In Forecast mode the four histograms and the percentile table return, but the day-365 option is disabled for a vault that has fewer than 365 days of history.

## Context References

Public refs:

- Direct user request on 2026-06-02: the current Monte Carlo "doesn't seem to match the implementation in quant stats" and produces implausible results (a P5 worst-case return higher than the vault itself); examine how QuantStats implements Monte Carlo versus this app, create an ExecPlan to migrate to that method including its distributions, and implement it. On being shown the trade-off (a faithful shuffle pins terminal value to reality and removes the future horizon, collapsing much of the current tab), the user chose to **offer both methods via a toggle**.
- QuantStats Monte Carlo source: `https://github.com/ranaroussi/quantstats/blob/main/quantstats/_montecarlo.py` (Apache-2.0). Its method and the exact behavior this plan depends on are restated in plain language under "Context and Orientation" so this plan is self-contained.

Repo artifacts:

- `/hyperopen/docs/exec-plans/active/2026-06-02-portfolio-monte-carlo-tab.md` — the portfolio tab that introduced the engine (note: it called the bootstrap "QuantStats-style", which is what this plan corrects).
- `/hyperopen/docs/exec-plans/active/2026-06-02-vault-monte-carlo-tab.md` — the vault tab that reuses the same engine and shared views.
- `/hyperopen/src/hyperopen/portfolio/montecarlo/engine.cljs` — the pure, deterministic engine this plan extends with a second method.

Local scratch refs (non-authoritative):

- None.

## Context and Orientation

The reader is assumed to know nothing about this repository. Orientation follows.

This is a ClojureScript single-page app built with `shadow-cljs`. UI is data-described (Replicant/hiccup) and rendered from cached "view models". The Monte Carlo feature is split into four layers, all reused across the portfolio and vault surfaces:

1. The pure engine: `/hyperopen/src/hyperopen/portfolio/montecarlo/engine.cljs`. Its `run` function takes a map of options and returns a result map. It performs no I/O and is unit-tested. Randomness comes from a seeded generator `mulberry32` (a small deterministic pseudo-random function returning floats in `[0,1)`), so identical inputs always produce identical output. The hot loop uses JavaScript typed arrays (`js/Float64Array`) for speed; only a capped set of sample paths and per-grid percentile bands are turned into ClojureScript vectors for the view.

2. The control actions and defaults: `/hyperopen/src/hyperopen/portfolio/montecarlo/actions.cljs`. "Controls" are the user-adjustable values (`:sims`, `:horizon`, `:bust`, `:goal`, `:seed`) stored in app state. `default-controls` holds defaults; `control-keys` is the set a user may set; `normalize-control` coerces a raw value (often a string from an input box) into a valid value; `controls-at` reads the current controls from app state at a given path, filling defaults. The portfolio surface stores its controls at `[:portfolio-ui :monte-carlo]`; the vault surface at `[:vaults-ui :monte-carlo]` (`/hyperopen/src/hyperopen/vaults/application/ui_state.cljs`, `vault-monte-carlo-state-path`). The Nexus action handlers `set-control-at` and `rerun-at` are state-path-generic, so both surfaces share them; the vault wrappers live in `/hyperopen/src/hyperopen/vaults/application/detail_commands.cljs`.

3. The view models: `/hyperopen/src/hyperopen/views/portfolio/vm/montecarlo.cljs` and `/hyperopen/src/hyperopen/views/vaults/detail_vm/montecarlo.cljs`. Each turns realized cumulative-return rows plus the user's controls into a render-ready model, calling `engine/run` once per input signature (cached so live websocket ticks do not recompute). The engine runs in "unit-equity" space (start equity = 1) so live dollar equity is not part of the cache key; the view multiplies by a live dollar base only where dollars are shown. A "min sample" gate (30 realized daily returns) shows an explanatory state when there is too little history.

4. The shared views: `/hyperopen/src/hyperopen/views/portfolio/montecarlo/{panel,controls,summary,distributions,chart,format}.cljs`. Per-surface differences (copy strings, the `data-role` prefix used by tests, the root CSS class, and the action ids) arrive in the model under a `:chrome` map, so one implementation drives both surfaces. `panel.cljs` is the top-level layout; `controls.cljs` is the control strip; `summary.cljs` is the probability rail and the terminal percentile table; `distributions.cljs` is the four histogram cards; `chart.cljs` draws the equity-paths spaghetti chart and the histograms onto a `<canvas>` via Replicant's `:replicant/on-render` lifecycle hook; `format.cljs` has display formatters. Styling is in `/hyperopen/src/styles/surfaces/montecarlo.css`, scoped under a neutral `.monte-carlo` class.

Key terms, defined plainly:

- "Bootstrap (with replacement)": to build one simulated path of length H, draw H daily returns by picking, independently and uniformly at random, an index into the realized-return array each day — the same return can be picked many times or never. Terminal value varies between paths.
- "Shuffle / permutation (without replacement)": to build one simulated path, take the realized-return array and reorder it into a random sequence that uses each return exactly once. Terminal value is identical between paths because the multiset of returns (hence the product of `(1+r)`) is unchanged.
- "Terminal value": the path's total cumulative return at its final day, e.g. `+1.32` meaning +132%.
- "Max drawdown": along a path, the worst peak-to-trough decline of equity, e.g. `-0.18` meaning the equity fell 18% below a prior peak at the worst point. This is path-dependent: reordering the same returns changes it.
- "Sharpe ratio" and "annualized volatility" as computed here are functions only of the *set* of daily returns (their mean and standard deviation), so a permutation leaves them unchanged; only the bootstrap (which changes the set) makes them vary.

What QuantStats `_montecarlo.py` does, restated so this plan is self-contained (this is the behavior we are matching for the shuffle method):

- It seeds a random generator, reads the realized returns into an array, and sets `n_periods = len(returns_array)`. There is no horizon parameter; the simulation length is the realized history length.
- It allocates a `(n_periods x sims)` matrix. Column 0 is the original, unshuffled returns. Each subsequent column is `rng.permutation(returns_array)` — a random reordering.
- It computes cumulative paths with `np.cumprod(1 + sim_returns, axis=0) - 1`, so each column is a cumulative-return path that starts near the first day's return and ends at that column's total return.
- `stats` reports min/max/mean/median/std and the 5/25/75/95 percentiles of the terminal row. Because every column ends at the same value, these are all equal.
- `maxdd` computes, per column, the minimum of `(growth - running_max)/running_max` where `growth = path + 1`; then reports min/max/mean/median/std and 5/95 percentiles across columns. This genuinely varies.
- `bust_probability` is the fraction of columns whose max drawdown is `<= bust_threshold`. `goal_probability` is the fraction of columns whose terminal value is `>= goal_threshold` — binary here, since the terminal is constant.
- `percentile(p)` and `confidence_band(level)` return per-day quantiles across columns (a band that is widest in the middle and converges to a single point at the final day).

This app's engine already produces an analogous result shape (`:band` over a time grid, `:terminal`/`:maxdd`/`:sharpe`/`:cagr`/`:vol` distribution maps, `:bust-prob`, `:goal-prob`, `:draw-paths`, `:times`, `:meta`). The shuffle method slots into that shape: terminal/sharpe/cagr/vol come out as degenerate (all-equal) distributions and maxdd as the real one, which is exactly QuantStats's behavior.

## Plan of Work

The work is additive: the bootstrap path is preserved (only its horizon is clamped to the realized sample size, which fixes the artifact in that mode), and a second method is added alongside it, selected by a new `:method` control. Five milestones.

### Milestone M1 — Engine gains a `:method` and a seeded shuffle

In `/hyperopen/src/hyperopen/portfolio/montecarlo/engine.cljs`, add a `:method` option to `run` with values `:bootstrap` (default, preserving today's behavior for callers that omit it) and `:shuffle`. Compute `m` (the realized sample size). Define the effective horizon `H`: for `:shuffle`, `H = m` and the `:horizon` argument is ignored (QuantStats has no horizon); for `:bootstrap`, `H = (min horizon m)` so a forecast can never compound more days than were observed — this is the fix for the "worst case beats reality" artifact in Forecast mode. Add a seeded Fisher–Yates shuffle helper `shuffled-indices` that uses the same `mulberry32` generator instance so determinism is preserved. In the per-simulation setup, choose the day's return source by method: bootstrap draws `ret[floor(rng()*m)]` each day as today; shuffle precomputes, for simulation `s`, an index permutation — the identity order `0..m-1` for `s = 0` (matching QuantStats's "column 0 is the original") and a fresh Fisher–Yates permutation for `s > 0` — and reads `ret[perm[t-1]]` on day `t`. Everything downstream (equity path, terminal/maxdd/sharpe/cagr/vol accumulation, percentile bands, capped draw paths, bust/goal counting) is shared and unchanged. Record `:method` and the effective `:horizon` in `:meta`. The existing chart reads `(:horizon (:meta result))` for its x-axis extent, so setting `:meta :horizon` to `H` keeps the chart working with no change for both methods (for shuffle the axis reads "trading days" of realized history, which is correct).

Acceptance for M1: extend `/hyperopen/test/hyperopen/portfolio/montecarlo/engine_test.cljs` with shuffle tests proving (a) determinism by seed; (b) every shuffle path ends at the same terminal value, so `:terminal` has `:p5 == :p50 == :p95` and `:std == 0` while `:maxdd :std > 0`; (c) the simulation length equals the sample size (`:times` ends at `m`, `:meta :horizon == m`); (d) the percentile band converges at the final grid point (`p5`, `p50`, `p95` equal there); and (e) bootstrap clamps the horizon to the sample size (`:meta :horizon == m` when `horizon > m`). Run `npx shadow-cljs compile test` then the node test runner (see Concrete Steps) and expect all green.

### Milestone M2 — Controls gain a `:method`; horizon clamps in the view models

In `/hyperopen/src/hyperopen/portfolio/montecarlo/actions.cljs`: add `method-options` `[:shuffle :bootstrap]`; add `:method` to `default-controls` (default `:shuffle` — the honest method the user asked to migrate to; see Decision Log) and to `control-keys`; add `normalize-method` (coerce keyword-or-string, fall back to `:shuffle`) and a `:method` branch in `normalize-control`. No action-registration or schema change is needed: the action-arg spec `::portfolio-monte-carlo-control-args` / `::vault-monte-carlo-control-args` in `/hyperopen/src/hyperopen/schema/contracts/action_args.cljs` is `(s/tuple ::common/keyword-or-string any?)` and does not enumerate control keys, and `set-control-at` already accepts any key in `control-keys`.

In both view models (`/hyperopen/src/hyperopen/views/portfolio/vm/montecarlo.cljs` and `/hyperopen/src/hyperopen/views/vaults/detail_vm/montecarlo.cljs`): read `:method`; compute `effective-horizon` (`= sample-size` for shuffle, `(min horizon sample-size)` for bootstrap); include `:method` and `effective-horizon` in the engine signature and pass `{:method method :horizon effective-horizon ...}` to `engine/run`; set the method tag prefix to `"Shuffle"` or `"Bootstrap"`; expose `:method`, `:effective-horizon`, and `:method-options` on the model for the views.

Acceptance for M2: extend `/hyperopen/test/hyperopen/portfolio/montecarlo/actions_test.cljs` to cover `normalize-method` (valid keyword/string pass through, junk falls back to `:shuffle`), the new default, and that `controls` includes `:method`. Update the existing `controls-reader-fills-defaults-test` expected map to include `:method`. Run the node test runner and expect green.

### Milestone M3 — Control strip renders the method toggle and adapts

In `/hyperopen/src/hyperopen/views/portfolio/montecarlo/controls.cljs`: add a "Method" segmented control as the first field, dispatching `[set-control-action :method opt]`, labeling `:shuffle` as "Sequence risk" and `:bootstrap` as "Forecast". Show the "Horizon" field only when `method = :bootstrap`. Pass `sample-size` through so horizon options greater than the sample size are rendered disabled (a 99-day vault cannot forecast 365 days). Make the run-status line method-aware: shuffle reads "<m> trading days · reshuffled"; bootstrap reads "<sims> paths · <H>d".

Acceptance for M3: in the running app, opening the tab shows the Method toggle; switching to "Forecast" reveals the Horizon control with out-of-range options disabled; switching to "Sequence risk" hides it. Verified in browser (see Validation).

### Milestone M4 — Panel/summary/distributions adapt per method

In `/hyperopen/src/hyperopen/views/portfolio/montecarlo/panel.cljs`: make the chart-card title method-aware ("Reshuffled equity paths · <m> trading days" for shuffle; "Simulated equity paths · <H>-day forecast" for bootstrap) and write a per-method footnote (shuffle: "Each path reorders this <subject>'s <m> realized daily returns — same returns, different sequence — so every path ends at the same realized return; what varies is the drawdown along the way." bootstrap: the existing wording, but stating the clamped effective horizon). Branch the body: bootstrap keeps today's layout (percentile table + four histograms); shuffle renders the sequence-risk layout described next.

In `/hyperopen/src/hyperopen/views/portfolio/montecarlo/summary.cljs`: keep `prob-card` for both methods but make the goal row method-aware — for shuffle, since terminal is fixed, present it as a deterministic "Goal reached / not reached" indicator (with the realized return) rather than a misleading percentage; the bust row is unchanged and meaningful in both. Replace the terminal `percentile-table` for shuffle with a compact `realized-card` showing the single realized ending equity and total return, labeled "identical across all orderings"; bootstrap keeps `percentile-table`.

In `/hyperopen/src/hyperopen/views/portfolio/montecarlo/distributions.cljs`: for bootstrap, render the four cards as today. For shuffle, render only the Max-drawdown histogram (the genuine distribution) plus a small `realized-strip` of single-value chips for total return, Sharpe, and annualized vol (read from the degenerate distributions' shared value), since those do not vary under shuffling.

Add the small amount of CSS needed in `/hyperopen/src/styles/surfaces/montecarlo.css` for the realized-outcome card, the realized-stats chips, and the single-histogram layout, all scoped under `.monte-carlo`.

Acceptance for M4: in the running app, Sequence-risk mode shows the realized-outcome card, the realized-stats chips, the max-drawdown histogram, and a meaningful Probability-of-bust; the equity fan converges to a single endpoint. Forecast mode shows the percentile table and four histograms with a sensible cone (P5 below realized, P95 above). Verified in browser.

### Milestone M5 — Validate end to end

Run the full gates and browser-verify both surfaces and both methods. Update this plan's living sections. Move the plan to `completed/` only after acceptance passes (leave at least one unchecked item while work remains).

## Concrete Steps

All commands run from the repository root, which in this worktree is `/Users/barry/projects/hyperopen/.claude/worktrees/elastic-wu-8a93d8` (a normal checkout root works the same way). `node_modules` must be present (in a worktree, symlink it: `ln -s ../../node_modules node_modules` if absent).

Compile and run the unit tests (the node runner executes the compiled `:test` build):

    npx shadow-cljs compile test
    node target/test/test.js

Expect a summary like `Ran N tests ... 0 failures, 0 errors` (N is currently ~4129 and grows with the new tests).

Run the repository check gate (lint + compile + doc guardrails) and the websocket suite:

    npm run check
    npm test
    npm run test:websocket

Run the app for browser verification:

    npm run dev
    # then open http://localhost:8080 and spectate a vault: /vaults/<address>

## Validation and Acceptance

Behavioral acceptance, phrased as what to observe:

- Engine unit tests: `node target/test/test.js` passes, including the new shuffle tests. Before the change, a test asserting "all shuffle paths share one terminal value" does not exist; after, it passes. Concretely, for `returns` with a positive mean, `(get-in res [:terminal :p5])` equals `(get-in res [:terminal :p95])` in `:shuffle` mode but is strictly less in `:bootstrap` mode.
- The original artifact is gone. In Forecast mode for a 99-day history, selecting any available horizon yields a P5 total return below the realized return (not above it), because the horizon is clamped to 99 and the bootstrap distribution straddles the realized value. In Sequence-risk mode the P5/P50/P95 terminal returns are all equal to the realized return.
- Toggle works in-app. On `/vaults/<address>` → `Monte Carlo`, the Method toggle switches the surface: "Sequence risk" hides Horizon, shows the realized-outcome card + realized-stats chips + the drawdown histogram + Probability-of-bust, and draws an equity fan that converges to one endpoint; "Forecast" shows Horizon (with day-365 disabled for a short-history vault), the percentile table, and four histograms.
- The portfolio surface behaves identically (same shared views, its own control state and `data-role` prefixes).
- `npm run check`, `npm test`, and `npm run test:websocket` are green.

## Idempotence and Recovery

All edits are to existing files plus this plan; re-running the build/test commands is safe and repeatable. The change is additive and reversible: removing the `:method` control (and reverting the bootstrap horizon clamp) restores the prior single-method behavior. No data migration, no destructive operation. If a milestone fails midway, the bootstrap path remains fully functional because `:method` defaults are normalized and the engine treats an absent/old stored value as `:shuffle` via `normalize-method` (existing users with no stored `:method` simply start in Sequence-risk mode; switching to Forecast restores the familiar view).

## Interfaces and Dependencies

No new libraries. The contracts that must exist at the end:

In `hyperopen.portfolio.montecarlo.engine`, `run` accepts an additional key `:method` (`:bootstrap` | `:shuffle`, default `:bootstrap`) and returns the same result shape, with `:meta` including `:method` and the effective `:horizon`. A private `shuffled-indices` produces a deterministic permutation of `0..m-1` from a `mulberry32` instance.

In `hyperopen.portfolio.montecarlo.actions`, `method-options` is `[:shuffle :bootstrap]`, `default-controls` includes `:method :shuffle`, `control-keys` includes `:method`, and `normalize-method`/`normalize-control` coerce it. `controls`/`controls-at` return maps that include `:method`.

The view models expose `:method`, `:effective-horizon`, and `:method-options` on the render model; the shared views read those plus the existing `:chrome` to drive per-method layout. The Nexus action ids and schema specs are unchanged.

## Progress

- [x] (2026-06-02) Research complete: read QuantStats `_montecarlo.py` in full and the entire hyperopen Monte Carlo surface (engine, actions, both view models, all shared views, tests, CSS, action registration/schema). Root cause confirmed (bootstrap over a horizon longer than history compounds positive drift). User chose the method-toggle direction.
- [x] (2026-06-02) ExecPlan written.
- [x] (2026-06-02) M1 — Engine `:method` (`:bootstrap`/`:shuffle`), seeded Fisher–Yates `shuffled-indices`, bootstrap horizon clamp to sample size, `:method` in `:meta`; five new engine tests (`engine_test.cljs`) green.
- [x] (2026-06-02) M2 — `:method` control (default `:shuffle`), `method-options`, `normalize-method`; both view models thread `:method` + `effective-horizon` and key the engine cache on them; `actions_test.cljs` updated.
- [x] (2026-06-02) M3 — Control strip renders the Method toggle (Sequence risk / Forecast); Horizon shown only in Forecast mode; run-status is method-aware.
- [x] (2026-06-02) M4 — Per-method panel/summary/distributions: shuffle shows the realized-outcome card, fixed-stats chips, the drawdown histogram, a deterministic goal indicator, and a sequence-risk footnote; bootstrap keeps the percentile table + four histograms with a clamp note. CSS added under `.monte-carlo`.
- [x] (2026-06-02) M5 — `npm test` (4134), `npm run check` (EXIT 0, docs check passed, 6 builds, 0 warnings), `npm run test:websocket` (527) all green; browser-verified both methods on the live HLP vault and the method toggle + history gate on a spectated portfolio, no console errors.

All acceptance criteria pass; the plan is complete. Optional, non-blocking follow-ups (not required for acceptance, captured for a future contributor): browser-verify the portfolio surface in a fully *ready* state (the address used during verification had only 27 days in range, so it correctly hit the 30-sample gate rather than rendering the full surface); and optionally tailor the shared intro lede per method (today it reads the same for both modes).

## Surprises & Discoveries

- Observation: The prior portfolio ExecPlan describes the bootstrap as "QuantStats-style", but QuantStats does not bootstrap at all — it shuffles (permutation, no replacement) and has no forecast horizon. The two methods answer different questions; conflating them is the origin of the implausible result.
  Evidence: `quantstats/_montecarlo.py` `run_montecarlo` uses `rng.permutation(returns_array)` with `n_periods = len(returns_array)` and `np.cumprod(1 + sim_returns, axis=0) - 1`; its `stats` reads the terminal row, which is constant across permutations.

- Observation: The fix is confirmed on real data. On the live HLP vault (89 days of all-time realized returns, realized total return +93.0%), Sequence-risk (shuffle) mode reports the terminal P5 = P50 = P95 = +93.0% (worst case equals reality, by construction) and only the drawdown distribution varies; Forecast (bootstrap) mode now reports P5 (worst case) +38.9%, P50 +87.4%, P95 (best case) +159.4% — a sensible cone with the worst case *below* realized. This is the inverse of the reported bug (P5 +744% above a +132% realized return).
  Evidence: browser inspection of `[data-role$="-terminal-table"]` rows and the `-realized-outcome` / `-probabilities` cards; chart titles "Reshuffled equity paths · 89 trading days" (shuffle) and "Simulated equity paths · 89-day forecast" (bootstrap, clamped from the 90-day default).

- Observation: Floating-point order-of-multiplication means shuffled terminal values are equal only to within ~1e-12, not bit-identical, so the engine tests assert terminal P5≈P95 with a 1e-9 tolerance and `:std < 1e-9` rather than exact equality.
  Evidence: `cumprod(1+r)` over a permutation differs in the last ~mantissa bits by reorder; `shuffle-pins-terminal-and-varies-drawdown-test` passes with the tolerance and fails with exact `=`.

- Observation (post-completion follow-up): QuantStats exposes three convenience distributions in `quantstats/stats.py` — `montecarlo_sharpe` (3173), `montecarlo_drawdown` (3231), `montecarlo_cagr` (3256) — that the initial port did not replicate. Reproducing their exact math on a shuffled series (script in the session) shows what each actually yields: `montecarlo_drawdown` (`= mc.maxdd`) genuinely varies (real distribution); `montecarlo_cagr` is computed from the per-path terminal value, which a permutation cannot change, so its "distribution" is a single value (distinct=1, std≈1e-14); and `montecarlo_sharpe` derives each path's returns via `cumret.pct_change().dropna()`, which silently drops the first day — so its Sharpe is a leave-one-out over the n−1 returns after slot 0, giving exactly n distinct values (a narrow ±~3–7% spread that is an artifact of which day is dropped, not a genuine sequencing effect).
  Evidence: on a 89-point series the reproduction printed `CAGR distinct=1 std=1.1e-14`, `SHARPE(full-set) distinct=1`, `SHARPE(qs pct_change drop1) distinct=89 std=0.239`, `MAXDD distinct=948 std=0.035`.

- Observation: A shuffle-invariant metric is not bit-constant (CAGR values differ by ~1e-13 fp noise), so an exact `(== mn mx)` degeneracy check in `histogram` failed and the CAGR card rendered the fp noise as a fake bell curve. Switching to a relative tolerance (`(<= (- mx mn) (* 1e-9 (max 1 |mn| |mx|)))`) collapses it to one centered spike. Browser-verified on HLP: the CAGR card is a single centered bar; Sharpe shows the narrow leave-one-out band; Max drawdown is the genuinely wide one.
  Evidence: `histogram-collapses-near-equal-values-test`; screenshot of the Sequence page with three distribution cards.

## Decision Log

- Decision: Offer both methods behind a `:method` toggle rather than replacing one with the other.
  Rationale: A faithful QuantStats migration pins terminal value to reality and removes the horizon, which collapses the terminal percentile table and three of the four histograms — most of the current tab. The user, shown this trade-off, chose to keep the forward-looking Forecast view and add the faithful Sequence-risk (shuffle) view.
  Date/Author: 2026-06-02, implementer.

- Decision: Default `:method` to `:shuffle` (Sequence risk).
  Rationale: It is the method the user explicitly asked to migrate to and is honest by construction (worst case equals reality, never beats it). The Forecast view remains one click away, and a stored legacy state with no `:method` normalizes to `:shuffle`. Trivial to flip if product prefers continuity.
  Date/Author: 2026-06-02, implementer.

- Decision: In Forecast (bootstrap) mode, clamp the effective horizon to the realized sample size rather than demeaning returns or using block bootstrap.
  Rationale: The artifact is caused purely by compounding more days than were observed; clamping removes it directly, keeps the strategy's realized edge intact (median forecast ≈ realized), yields a sensible cone (P5 < realized < P95), and is far simpler than drift adjustment.
  Date/Author: 2026-06-02, implementer.

- Decision: Surface the bootstrap horizon clamp with an explanatory run-status + footnote rather than disabling out-of-range horizon buttons (a reversal of the plan's initial intent).
  Rationale: The effective horizon `min(requested, sample-size)` is continuous and is usually not one of the discrete options (30/90/180/365), so disabling created a corner case where the selected button is disabled yet highlighted, or no button highlights. Keeping the requested horizon highlighted while the run-status ("1,000 paths · 89d") and footnote ("your 90-day request is clamped to 89 — a forecast cannot compound more days than were observed") state the effective value is clearer and avoids the inconsistency. Browser-verified on HLP (89 days): the 90d button stays selected and the surface reads 89d everywhere it matters.
  Date/Author: 2026-06-02, implementer.

- Decision (post-completion follow-up): Replace the Sequence-mode "fixed-stat chips" with three distribution cards — Sharpe, Max drawdown, CAGR — matching QuantStats' `montecarlo_sharpe`/`montecarlo_drawdown`/`montecarlo_cagr`, after the user pointed out those functions exist.
  Rationale: The user wants parity with the exact QuantStats convenience functions. Faithful to them: Max drawdown is genuinely distributed; CAGR is a single value (shown as a centered spike, truthfully communicating "no spread"); and the shuffle Sharpe now uses QuantStats' per-path `pct_change().dropna()` (drop-first-day, leave-one-out) so it shows the same narrow distribution QuantStats produces. The footnote states plainly that total return and CAGR are fixed and that the Sharpe spread comes from QuantStats' day-drop, so the parity is honest rather than implying sequencing changes Sharpe. `:bootstrap` keeps its genuine full-path Sharpe (each path is a distinct resample).
  Date/Author: 2026-06-02, implementer.

## Revision Note (post-completion)

After acceptance, the user observed that QuantStats ships `montecarlo_sharpe`/`montecarlo_drawdown`/`montecarlo_cagr` (in `quantstats/stats.py`) and asked why the Sequence page lacked those distributions. Reproducing the exact QuantStats math confirmed: drawdown genuinely varies, CAGR is a single value (computed from the shuffle-invariant terminal), and the Sharpe distribution exists only because QuantStats derives per-path returns via `cumret.pct_change().dropna()`, which drops the first day (a leave-one-out). The implementation was extended to (1) compute the shuffle Sharpe via that same leave-one-out so it matches QuantStats; (2) render three distribution cards on the Sequence page (Sharpe, Max drawdown, CAGR) in place of the fixed chips; (3) move the fixed annualized-vol readout onto the realized-outcome card; and (4) fix `histogram` to treat fp-noise-equal values as degenerate (relative tolerance) so the invariant CAGR renders as a single centered spike rather than a fake bell curve. New test `histogram-collapses-near-equal-values-test`; updated `shuffle-pins-terminal-and-varies-drawdown-test` to assert Sharpe varies and CAGR does not. `npm run check`, `npm test` (4135), and a browser pass on the live HLP vault are green; this reason is recorded here per the PLANS.md requirement to note why the spec changed.

## Outcomes & Retrospective

Delivered and verified end to end. The Monte Carlo tab on both the portfolio and vault surfaces now offers a Method toggle: "Sequence risk" (the faithful QuantStats shuffle) and "Forecast" (the bootstrap, with its horizon clamped to the realized history length). The reported defect is resolved: on the live HLP vault (realized +93.0% over 89 days), Sequence-risk mode pins the terminal P5/P50/P95 to +93.0% (the worst case can no longer beat reality) and foregrounds the only quantity a reordering actually changes — the max-drawdown distribution and the probability that some ordering would have breached the bust threshold. Forecast mode now produces a coherent cone (worst case +38.9% < realized +93% < best case +159.4%) instead of the previous +744% worst case, because the horizon can no longer compound more days than were observed.

Validation: `npm test` (4134 tests, 0 failures), `npm run check` (EXIT 0 — lint, docs guardrails, and all six shadow-cljs builds clean with 0 warnings), and `npm run test:websocket` (527 tests, 0 failures). Browser verification against the live HLP vault exercised both methods (toggle, horizon show/hide, percentile table vs realized card, four histograms vs drawdown+chips, method tag, clamp footnote) with no console errors; the spectated portfolio confirmed the shared surface renders the new toggle and correctly gates on a 27<30-day history.

Complexity: a modest, well-isolated increase. The engine gained one method branch and two small permutation helpers; the actions gained one normalized control; each view model threads one extra key; the shared views gained per-method conditionals plus three small presentational helpers (`realized-card`, `goal-indicator`, `realized-strip`) and a handful of scoped CSS rules. No action-registration or schema changes were needed (the control-args spec already accepts any keyword control). The bootstrap path is preserved, so nothing regressed; the only behavioral change to the old path is the horizon clamp, which is the intended fix. Net: the cost is justified by correctness and by keeping the existing Forecast product intact while adding the honest QuantStats view the user asked for.

Note on revisions: this plan was revised during M3/M4 to surface the bootstrap horizon clamp with an explanatory run-status + footnote instead of disabling out-of-range horizon buttons (recorded in the Decision Log), because the continuous effective horizon rarely lines up with the discrete horizon options. All living sections above reflect the delivered implementation.
