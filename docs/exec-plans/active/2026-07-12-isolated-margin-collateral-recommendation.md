# Isolated-margin collateral recommendation (positions, trade ticket, optimizer execution)

This ExecPlan is a living document maintained in accordance with `/.agents/PLANS.md`. It is kept current as work proceeds; Progress, Surprises & Discoveries, and the Decision Log are updated as milestones land.

## Purpose / Big Picture


Isolated-margin positions (all HIP-3 / named-dex assets, plus any main-dex position opened isolated) carry a per-position liquidation price, and today the user must size the isolated collateral by hand. This plan adds a modeled recommendation: the smallest isolated collateral that keeps the modeled probability of liquidation below the user's chosen risk limit over the window before they are likely to intervene, where the intervention horizon is inferred from the user's own fill history. The recommendation surfaces (1) on the Positions tab as a by-exception risk chip plus an expandable recommendation panel with a one-click "Add recommended margin" action, (2) in the trade ticket for isolated markets at order time with an optional automatic post-fill top-up, and (3) in the optimizer execution tab as a run-level auto-collateral toggle. All data fetching and simulation runs at low priority in the background (idle-scheduled, lazily loaded compute module); nothing blocks initial load or the interactive path.

The estimator is not "volatility times sqrt-time": liquidation is a path-dependent first-touch event, so the engine computes the distribution of maximum adverse excursion via a block bootstrap of hourly bars (close-to-close returns plus intra-bar wick excursions), evaluates exact per-path required equity against the Hyperliquid maintenance-margin curve, and reads the recommended collateral off the (1 − alpha) quantile of that required-equity distribution. Named buffers (funding, exit/slippage, model uncertainty) are added explicitly, never as an unlabeled safety factor.

## Context References


Public refs: Hyperliquid margining docs (https://hyperliquid.gitbook.io/hyperliquid-docs/trading/margining), liquidations docs (https://hyperliquid.gitbook.io/hyperliquid-docs/trading/liquidations), exchange endpoint docs for `updateIsolatedMargin` (https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/exchange-endpoint).

Repo artifacts: this plan; existing isolated-margin submit machinery `src/hyperopen/account/history/position_margin.cljs`; positions surface `src/hyperopen/views/account_info/tabs/positions/*.cljs`; margin tier ingestion `src/hyperopen/utils/data_normalization.cljs`; seeded PRNG precedent `src/hyperopen/portfolio/montecarlo/engine.cljs`; idle scheduling `src/hyperopen/platform.cljs`.

This work was initiated by a direct user request (product owner) accompanied by a designer/analyst specification; the designer mockups are advisory and this plan is the authoritative engineering decision record.

## Progress


- [x] (2026-07-12) Exploration: mapped positions UI, order flows, market-data infra, worker/nexus/contract surfaces, gates.
- [x] (2026-07-12) Engine namespaces (`src/hyperopen/margin_rec/{tiers,episodes,paths,recommend}.cljs`) with unit tests.
- [x] (2026-07-12) State bucket, input signatures, intents (`src/hyperopen/margin_rec/state.cljs`) with unit tests.
- [x] (2026-07-12) Actions + effects + registration + arg contracts + effect-order policy (+ Lean sync) wired.
- [x] (2026-07-12) Lazy compute module (`:margin_rec` shadow module) + chunked idle driver.
- [x] (2026-07-12) Background triggers: startup watcher, fills fetch, candle fetch reuse.
- [x] (2026-07-12) Positions tab UI: risk chip, recommendation panel, margin-modal prefill hint; view contract tests.
- [x] (2026-07-12) Trading settings: risk mode + auto top-up keys, persistence, panel controls.
- [x] (2026-07-12) Trade ticket: auto top-up toggle + note + post-fill intent at submit.
- [x] (2026-07-12) Optimizer execution: armed-band auto-collateral toggle + intents on confirm.
- [x] (2026-07-12) Gates green: `npm run gates` 34/34 incl. formal verify and Lean-synced vectors.
- [x] (2026-07-12) Browser QA on the worktree build: seeded fixture position drove the real watcher -> sync -> lazy module -> batched simulation loop in the browser (p_liq 34% -> chip + panel), screenshots captured.
- [ ] Fills fetch retry-on-error with cooldown (an errored fills fetch currently stays errored for the session; the horizon falls back to the 72 h default, which is safe but loses personalization).
- [ ] Mobile positions card: compact recommendation line inside the expanded card (the shared margin-modal hint already covers the mobile add flow).
- [ ] Probability-vs-collateral curve in a side drawer (the sorted required-equity distribution already computes it; only presentation is missing).
- [ ] Persist fills to IndexedDB for long-horizon behavior modeling (BROWSER_STORAGE-governed store; today's session-only fetch covers ~2000 recent fills).

## Surprises & Discoveries


- Observation: Hyperliquid margin tier tables (`marginTables`) are already ingested and resolved per asset at `[:asset-contexts <coin> :margin]` by `src/hyperopen/utils/data_normalization.cljs`, but no code consumes them today.
  Evidence: grep over `asset_selector/`, `state/`, `domain/` shows only `:idx` read from asset contexts.
- Observation: `updateIsolatedMargin` submit, validation, withdrawable math, and asset-id resolution already exist end to end in `src/hyperopen/account/history/position_margin.cljs` + `src/hyperopen/order/effects.cljs` (`api-submit-position-margin`), including a prefill seam (`:prefill-margin-amount`).
  Evidence: `prepare-submit` builds `{:type "updateIsolatedMargin" :asset .. :isBuy .. :ntli ..}`; effect registered and flagged heavy in the effect-order contract.
- Observation: per-dex (HIP-3) meta does not retain margin tier tables anywhere in app state; only scalar `maxLeverage` survives into market entries.
  Evidence: `build-market-state` / `asset_selector/markets.cljs` keep explicit keys only; `[:asset-contexts]` is main-dex only (and filtered by volume/OI activity).
- Observation: the app-state contract (`src/hyperopen/schema/contracts/state.cljs` `::app-state`) requires specific keys but is open — a new `[:margin-rec]` top-level bucket does not require a contract change.
- Observation: positions rows carry `maxLeverage` and exchange-computed `liquidationPx` per position, which enables maintenance-rate calibration when tier tables are unavailable.
- Observation: the trade route renders the account panel and order form from memoized `select-keys` state slices (`src/hyperopen/views/trade_view.cljs`); a new app-db bucket is invisible to those surfaces (and their memos never invalidate) until its key is added to the slice.
  Evidence: browser QA showed a computed recommendation in state with no chip in the DOM; adding `:margin-rec`/`:trading-settings` to `account-info-view-base-state-keys` and `order-form-view-state-keys` fixed it. The portfolio route passes full state and was unaffected.
- Observation: dev-mode account lifecycle invariants forbid seeding account-derived surfaces (e.g. `[:perp-dex-clearinghouse]`) without an effective account, and a spectated account's live clearinghouse pushes clobber seeded fixtures; browser QA must spectate first, pre-load lazy modules, then take the context offline before seeding.
- Observation: a chip placed beside the liquidation price overflowed its grid track and the neighboring margin cell intercepted its clicks; stacking secondary content under the primary value (as the margin cell's recommendation line does) is the layout-safe pattern in the dense positions grid.

## Decision Log


- Decision: compute the recommendation from the per-path required-equity maximum, not by binary search over candidate collateral.
  Rationale: for a fixed path, required equity to survive is `max_t (mm(|q|·P_t) − q·(P_t − P0))`, which is exact and monotone in collateral; the (1 − alpha) quantile of that distribution IS the minimal collateral, and the full liquidation-probability-vs-collateral curve falls out for free. One simulation pass replaces ~12 bisection passes.
  Date/Author: 2026-07-12, feature branch owner.
- Decision: run the simulation on the main thread in idle-scheduled, deterministic path batches inside a lazily loaded shadow module (`:margin_rec`), not in a new web worker.
  Rationale: bounded work (≤ ~4000 paths × ≤ 720 hourly steps, batched ~512 paths per idle slice); the repo precedent (portfolio Monte Carlo) runs full MC synchronously in view models, so chunked idle compute is strictly gentler; a fourth worker adds wire-codec surface and build targets for no measurable benefit at this size. Lazy module keeps the math out of the `:main` bundle (PageSpeed budget).
  Date/Author: 2026-07-12.
- Decision: exclude funding and exit fees from the path simulation and account for them as separate named buffers.
  Rationale: keeps the breakdown decomposition exact (adverse-path, maintenance, funding, exit, model terms sum to the total), keeps the simulation kernel simple, and matches the product requirement that every buffer have a named source and dollar amount. Funding drift over a ≤ 30-day horizon is second-order relative to price risk at the leverage levels involved.
  Date/Author: 2026-07-12.
- Decision: bootstrap raw hourly bars in blocks (close-to-close log return + high/low wick excursions), rescaled by the ratio of current EWMA vol to sample vol (clamped), with a 15% oversample of top-decile-vol blocks; do not standardize-then-rescale per bar.
  Rationale: raw blocks preserve volatility clustering and fat tails as observed; per-bar standardization then constant rescale destroys clustering unless a vol process is simulated. Wick excursions capture intra-hour first-touch that close-only series miss.
  Date/Author: 2026-07-12.
- Decision: maintenance model = tier table from `[:asset-contexts <coin> :margin]` when present, else flat rate `1/(2·maxLeverage)`; in both cases calibrate against the exchange-provided `liquidationPx` for the current state and prefer the calibrated flat rate when the closed-form liquidation price disagrees with the exchange by more than 10%.
  Rationale: named-dex tiers are not available in app state; the exchange's own liquidation price is ground truth for the current position, so calibration bounds model error and any residual disagreement widens the model-uncertainty buffer instead of being hidden.
  Date/Author: 2026-07-12.
- Decision: intervention horizon = 80th percentile of intervention gaps from position episodes reconstructed from fills (per-coin when ≥ 8 completed gaps, else account-level when ≥ 8, else 72 h default), clamped to [6 h, 720 h]. Fills come from a one-shot low-priority REST `userFills` fetch (~2000 fills) stored under `[:margin-rec :fills]`; the 200-capped `[:orders :fills]` bucket is not relied on.
  Rationale: matches the designer's episode model with honest fallbacks; the REST fetch is cheap, deduped, and background-priority. Persisting fills to IndexedDB is deferred (recorded under Outcomes as follow-up).
  Date/Author: 2026-07-12.
- Decision: auto top-up executes only as a one-shot, post-fill intent created at order time (trade ticket toggle, optimizer run toggle), never as a standing background rescue. An intent expires after 3 minutes, fires at most once, requires the observed position size to be within 2% of the expected post-fill size, and is capped by available-to-add collateral.
  Rationale: designer's own guardrail analysis (never chase a falling position; do not drain the account); one-shot semantics make the automation auditable and safe without a policy console in v1.
  Date/Author: 2026-07-12.
- Decision: the "Automatic margin recommendations" bulk console (third mockup) is out of scope for v1; its policy inputs are reduced to two persisted trading settings (risk mode; auto top-up after trades) plus the per-run optimizer toggle.
  Rationale: the per-row chips, panel, and trade-time automation deliver the user value; a standing policy engine with caps/reserves deserves its own plan after the recommendation quality is validated in the field.
  Date/Author: 2026-07-12.
- Decision: risk-limit presets alpha = 1% (Conservative), 2% (Balanced, default), 5% (Capital efficient), matching the designer's drawer copy.
  Date/Author: 2026-07-12.
- Decision: trade-time recommendations reuse the post-fill background loop instead of a pre-trade "planned position" simulation: the submit-time intent carries no target, and the intent pass waits for the recommendation the loop computes for the actual filled position (exact size, entry, and exchange liquidation price) before topping up once.
  Rationale: a planned-position estimate would duplicate the compute keying and still be wrong about fills; the post-fill number is strictly more accurate, and the 3-minute intent window plus the size-match guard bound the behavior.
  Date/Author: 2026-07-12.
- Decision: the optimizer execution toggle controls the same persisted trading setting as the trade ticket (no per-run override state).
  Rationale: one coherent auto-top-up policy across surfaces, no optimizer contract/migration surface; a per-run override can layer on later if requested.
  Date/Author: 2026-07-12.
- Decision: the panel's one-click "Add recommended margin" opens the existing Adjust Margin modal prefilled (one more click to sign) rather than submitting directly.
  Rationale: reuses the validated submit path (clamping to available collateral, submitting state, agent gating) and avoids double-submit hazards; the truly automatic path is the post-fill intent, which is deliberate and bounded.
  Date/Author: 2026-07-12.

## Outcomes & Retrospective


Shipped 2026-07-12 on the feature branch: engine (tiers/paths/episodes/recommend, ~900 lines, fully unit-tested incl. an exactly-derivable degenerate-path case and batch-slicing invariance), background orchestration (store watcher, signature-gated sync, lazy `:margin_rec` module, idle-batched simulation), positions-tab chip + panel + margin-modal hint, persisted risk-mode/auto-top-up settings, trade-ticket toggle + post-fill intents, optimizer armed-band toggle + per-leg intents. `npm run gates` 34/34; browser QA exercised the full loop against the compiled worktree build (recommendation computed in-browser through the lazy module; screenshots in the session artifacts).

Known limitations / deferred follow-ups (also tracked as unchecked Progress items): fills fetch does not retry after an error (horizon falls back to the 72 h default); mobile card lacks the inline recommendation line (modal hint covers the flow); no probability-vs-collateral drawer chart yet; fills are not persisted to IndexedDB; per-dex margin-tier retention and annual liquidation-risk budgeting remain open; the bulk "Automatic margin recommendations" console from the third mockup was deliberately cut from v1.

## Context and Orientation


State and data flow. Positions arrive via webData2 (`[:webdata2 :clearinghouseState :assetPositions]`) and per-dex clearinghouse subscriptions (`[:perp-dex-clearinghouse <dex>]`), assembled by `src/hyperopen/views/account_info/projections/positions.cljs` and rendered by `src/hyperopen/views/account_info/tabs/positions/{desktop,mobile}.cljs` from `positions_vm.cljs` row VMs. Isolated rows already show an Edit Margin pencil that opens `position_margin_modal.cljs`, whose pure core (`src/hyperopen/account/history/position_margin.cljs`) builds the signed `updateIsolatedMargin` request submitted by `:effects/api-submit-position-margin` (`src/hyperopen/order/effects.cljs`). Hourly candles are fetchable via the existing `:effects/fetch-candle-snapshot` (projected into `[:candles <coin> <interval>]`). Live mark, funding (per-1h decimal), margin tier table, and universe metadata for main-dex assets live at `[:asset-contexts <coin-keyword>]`. Trading preferences live in `[:trading-settings]` (localStorage `hyperopen:trading-settings:v1`, closed normalization in `src/hyperopen/trading_settings.cljs`).

Runtime contract surface. New actions/effects require: binding rows in a new `src/hyperopen/schema/runtime_registration/margin_rec.cljs` aggregated by `runtime_registration_catalog.cljs`; handler wiring in `src/hyperopen/runtime/collaborators.cljs` (+ `app/actions.cljs`, `app/effects.cljs` deps); arg specs in `src/hyperopen/schema/contracts/{action_args,effect_args}.cljs`; and, for actions flagged as emitting ordered heavy effects, entries in `src/hyperopen/runtime/effect_order_contract.cljs` mirrored in `spec/lean/Hyperopen/Formal/EffectOrderContract.lean` and regenerated via `bb tools/formal.clj sync --surface effect-order-contract`.

Bundle discipline. Heavy compute must not ride `:main`. The engine lives behind a new lazy shadow module `:margin_rec` (entries `hyperopen.margin-rec.engine-module`, `:depends-on #{:main}`) added to both `:app` and `:release` builds, loaded on demand by the compute effect via `shadow.loader` (same pattern as `trading_crypto_modules`). View code rides the existing `:account_positions_outcomes` module.

## Plan of Work


Model, in math terms (the engine implements exactly this):

Let q be signed size, P0 the current mark, E0 the current isolated equity (`marginUsed`; for isolated positions it already includes unrealized PnL). Maintenance margin at price P is mm(P) = m_k·|q|·P − d_k with the tier (m_k, d_k) selected by notional |q|·P; deductions d_k are derived from tier boundaries for continuity (d_1 = 0, d_k = d_{k−1} + b_k·(m_k − m_{k−1})). Untiered fallback: m = 1/(2·maxLeverage), d = 0. Calibration: solve m̂ from the exchange's own liquidationPx given (E0, q, P0) with d = 0; if the closed-form liquidation price from the configured model disagrees with the exchange by > 10% relative, use m̂ and widen the model buffer.

Hourly bars (target 45 days, minimum 60 bars) give per-bar log return r_i = ln(c_i/c_{i−1}) plus wick excursions lowx_i = ln(l_i/c_{i−1}), highx_i = ln(h_i/c_{i−1}). EWMA per-hour vol uses lambda = exp(ln 0.5 / 72) (3-day half-life); scale rho = clamp(sigma_now/sigma_sample, 0.75, 2.5). Paths are built from 24-bar blocks sampled uniformly, except with probability 0.15 the block start is drawn from the top decile of realized block vol. Per simulated bar the adverse wick point (low for long, high for short) and the close are both tested. Per path j, requiredE_j = max over tested points of [mm(|q|·P) − q·(P − P0)], including the t = 0 point mm(|q|·P0). The engine returns the sorted requiredE distribution; p_liq(E) is the fraction strictly above E and C*(alpha) is the (1 − alpha) quantile. Path count: 4000 (2500 when horizon > 240 bars), batched 512 per idle slice, PRNG mulberry32 seeded from the input signature so results are reproducible.

Recommended equity E_rec = C*(alpha) + B_funding + B_exit + B_model, where B_funding = max(adverse funding rate, 0) · H_hours · N0 (adverse = pays for our side, from the live per-1h rate), B_exit = exit-fraction · N0 (1.0% named-dex, 0.4% main-dex constants, documented in code), B_model = u · max(0, C*(alpha) − mm(P0)) with u = 0.10 + 0.40·max(0, 1 − n_bars/720) + 0.10·[rho clamped] + 0.15·[calibration mismatch], capped at 0.60. Additional collateral = max(0, E_rec − E0). The breakdown rows (maintenance mm(P0); adverse-path C* − mm(P0); funding; exit; model) sum exactly to E_rec. New estimated liquidation price at any equity E solves E + q·(P − P0) = mm(|q|·P) piecewise per tier. The sigma-distance chip is |P0 − liqPx|/P0 divided by sigma_now·sqrt(24).

Horizon: reconstruct per-coin episodes from fills (start when net size leaves zero; interventions are risk-reducing fills, flips, and closes; gaps are the times between consecutive interventions, episode start included). Horizon = 80th percentile of completed gaps (per-coin n ≥ 8, else account-level n ≥ 8, else 72 h), clamped [6 h, 720 h], labeled with its source and episode count.

Background orchestration: a store watcher (installed with the existing deferred/startup watchers) observes isolated positions, the order form, trading settings, and pending intents, debounced ~1.5 s, and dispatches `:actions/margin-rec-sync`. Sync is pure: it diffs desired work against `[:margin-rec]` (input signatures bucket mark moves at 0.25% and enforce a 120 s per-key recompute floor) and emits: a one-shot fills fetch, per-coin candle fetches (`:effects/fetch-candle-snapshot`, low priority), and at most one `:effects/margin-rec-compute` at a time. Compute loads the lazy module, runs the chunked simulation, and dispatches `:actions/margin-rec-apply-result`. Intents are processed by `:actions/margin-rec-process-intents` when a position snapshot matches an intent (size within 2%, fresh, single-shot) and emit the existing `:effects/api-submit-position-margin`.

## Concrete Steps


1. Engine namespaces + tests: `src/hyperopen/margin_rec/tiers.cljs` (tier parse, mm, closed-form liq, calibration), `paths.cljs` (bar prep, EWMA, mulberry32, block bootstrap, required-equity simulation, quantiles), `episodes.cljs` (fills → episodes → horizon), `recommend.cljs` (orchestration, buffers, breakdown, statuses, risk modes); tests under `test/hyperopen/margin_rec/`.
2. `state.cljs`: `[:margin-rec]` default (added to `src/hyperopen/state/app_defaults.cljs`), input signatures, staleness, queue selection, intent lifecycle helpers; tests.
3. Runtime wiring: actions in `src/hyperopen/margin_rec/actions.cljs`; effects in `src/hyperopen/runtime/effect_adapters/margin_rec.cljs` (fills fetch via `request-user-fills!`; compute driver via lazy module + `platform/schedule-idle-or-timeout!`); registration rows, collaborators, arg contracts; effect-order policy entries + Lean sync for `:actions/margin-rec-sync`, `:actions/margin-rec-apply`, `:actions/margin-rec-process-intents`.
4. Shadow modules: add `:margin_rec` to `:app` and `:release`; loader ns `src/hyperopen/margin_rec_modules.cljs`; engine module entry `src/hyperopen/margin_rec/engine_module.cljs` with exported compute.
5. Watcher: install in `src/hyperopen/startup/watchers.cljs` (idle-deferred), debounced sync dispatch.
6. Settings: `:margin-rec-risk-mode` + `:margin-rec-auto-topup?` in `trading_settings.cljs` (+ normalize + accessors), setter actions following `persist-trading-settings`.
7. Positions UI: VM additions (`positions_vm.cljs`, `vm.cljs`), risk chip + recommendation line in `desktop.cljs`/`mobile.cljs`, panel `src/hyperopen/views/account_info/margin_recommendation_panel.cljs` (tiles, breakdown table, risk scale, actions: add recommended / reduce instead / risk-mode segmented control), margin-modal recommended hint; view contract test pinning the data-role tree and callout strings.
8. Trade ticket: recommendation line + toggle in the order form (isolated perp markets only); `submit-order` gains an intent `:effects/save` (projection phase) when enabled.
9. Optimizer execution: `:auto-isolated-collateral?` in the execution modal state (defaults + paths via optimizer contracts), toggle UI in the commit block, intents created on confirm for isolated rows.
10. Gates + QA: `npm run gates`; compile the worktree app, serve on 8090 with the SPA-fallback static server, drive a temp Playwright spec with seeded fixture positions/candles/results, screenshot the panel and chip; `npm run browser:cleanup`.

## Validation and Acceptance


- Unit: tier continuity across boundaries; closed-form liquidation matches simulation on single-step paths; calibration recovers a known maintenance rate; deterministic simulation under a fixed seed; a constant-volatility synthetic series yields a required-equity quantile within tolerance of the lognormal closed form; long/short asymmetry; episode reconstruction on fixture fills incl. flips and open episodes; breakdown sums exactly; risk-mode alphas; input-signature bucketing; intent lifecycle (expiry, size tolerance, single-shot, cap by available).
- Contract: new actions/effects registered without drift (`schema` coverage tests pass); effect-order conformance incl. regenerated Lean vectors; view contract test for the panel.
- Gates: `npm run check`, `npm test`, `npm run test:websocket` all pass (TZ=UTC); `npm run formal:verify -- --surface effect-order-contract` clean; namespace sizes within limits.
- Behavior: with a seeded fixture position whose modeled risk is high, the Positions tab shows the risk chip and recommended line; opening the panel shows tiles/breakdown/actions; "Add recommended margin" produces a well-formed `updateIsolatedMargin` request (asserted in tests against the pure `prepare-submit`); no margin-rec fetch or compute occurs before first paint (watcher installs in deferred startup; effects are idle/low-priority).

## Idempotence and Recovery


All engine functions are pure and deterministic (seeded PRNG); recompute is signature-gated so replays are no-ops. The compute effect tolerates missing data by recording a per-key status (`:insufficient-history`, `:error`) instead of throwing. Intents are single-shot with expiry; a crashed session leaves only stale intents that expire unprocessed. Candle/fills fetches are deduped by the info-client single-flight keys and safe to re-issue. If a step fails mid-way, re-running `:actions/margin-rec-sync` reconverges from current state.

## Artifacts and Notes


Design mockups (three screenshots: positions-row expansion panel, side drawer, bulk policy console) were provided by the designer; the panel variant is the shipped v1 surface. Constants that encode product policy (alphas, exit fractions, clamps, thresholds) are centralized in `recommend.cljs` and documented inline. UI copy follows the mockups where the underlying quantity is actually computed ("Modeled liq. probability", "before next intervention", named buffer rows) and deliberately avoids implying guarantees.

## Interfaces and Dependencies


Consumes: `[:webdata2]`, `[:perp-dex-clearinghouse]`, `[:asset-contexts]`, `[:candles <coin> "1h"]`, `[:orders :fills]` (display only), REST `userFills`, `[:trading-settings]`, `position_margin.cljs` (`from-position-row`, `prepare-submit`), `:effects/fetch-candle-snapshot`, `:effects/api-submit-position-margin`, `platform/schedule-idle-or-timeout!`, `shadow.loader`.

Provides: `[:margin-rec]` state bucket (recs by position key, fills, episodes, intents, panel selection), actions `:actions/margin-rec-{sync,apply-fills,apply-result,note-compute-error,apply,process-intents,toggle-panel}` plus settings setters, effects `:effects/margin-rec-{fetch-fills,compute}`, lazy module `:margin_rec`, panel view + row chip, trade-ticket line/toggle, optimizer execution toggle.
