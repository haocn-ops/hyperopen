# Optimizer Rebalance Preview Fidelity: kill phantom legs, make fees honest, make slippage size-aware

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This plan is maintained in accordance with `docs/PLANS.md` (and its detailed contract `.agents/PLANS.md`).

## Purpose / Big Picture

The optimizer "Rebalance preview" tab (`/portfolio/optimize/draft?...&otab=rebalance`) currently shows three user-visible defects on a real spectated account:

1. Every held asset renders as TWO rows — a real perp leg (e.g. `perp` for `BTC`) and a phantom blocked leg labelled with a leading-colon id like `:perp:BTC` / `:perp:xyz:XYZ100`, always blocked with reason `market-metadata-missing`. There were 29 such phantom rows, doubling the "Buys/Sells" totals, the per-asset Δ%/notional, and the readiness split (shown as `29 / 29`).
2. The "Est. fees + slippage" KPI shows a fee component of exactly $0 (the value is pure slippage) even though the label promises fees.
3. The slippage estimate is size-blind: orders larger than the visible order book ("depth limited") are charged a flat 25 bps and reported at the unimpacted reference price, badly understating real execution cost for multi-million-dollar legs.

After this change a user looking at the rebalance preview will see: one row per single-implementation asset (no phantom `:perp:` duplicates); a "Buys/Sells" total and asset count that match the real, tradeable rebalance; a fee component that reflects the chosen taker/maker fee schedule; and a depth-limited slippage number that grows with how far an order exceeds the visible book instead of flat-lining at 25 bps. You can see it working by opening the rebalance tab for a held portfolio: each asset is a single row, the readiness count reflects only genuinely blocked rows, and the cost KPI is non-zero on its fee component.

This work was scoped from a full audit (see Decision Log) and the user explicitly approved: wire real fees AND keep the "fees + slippage" label, and improve the depth-limited slippage now (conservative model).

## Context References

Public refs:
- Direct user request (this session): audit and then fix the rebalance preview's fee/slippage estimation, the duplicate per-asset rows, and the spot-asset appearance. User approved scope: wire real fees + keep label; improve depth-limited slippage now (conservative).

Repo artifacts:
- Canonical planning contract: `docs/PLANS.md`, `.agents/PLANS.md`.
- Operating contract / validation gates: `AGENTS.md`.
- Prior optimizer rebalance ExecPlan (layout/spec, for orientation only): `docs/exec-plans/completed/2026-04-26-portfolio-optimizer-v4-alignment.md`.

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-06-22) Audit completed; root causes identified and adversarially verified.
- [x] (2026-06-22) Scope approved by user (fees: wire + keep label; slippage: improve now).
- [x] (2026-06-22) M1 — Phantom-leg fix: added `:current-portfolio-weights-by-instrument` to `instrument-keyed-map-keys`; hardened the preview id path through `ids/instrument-id-key`; codec test + new `rebalance_preview_test.cljs`.
- [x] (2026-06-22) M2 — Honest fees (first cut): added `fee-bps-for-mode` + `default-{taker,maker}-fee-bps` in the domain, `:default-fee-bps` wired from `:fee-mode` in the FRONTEND `build-derived-preview`; cost model falls back to it; domain tests added.
- [x] (2026-06-23) M2′ — Honest fees (corrected & completed): discovered the displayed preview is the WORKER's (`payload.cljs`), so the frontend-only M2 wiring was dead on the primary path → fees were still $0. Moved resolution to `request_builder/normalize-execution-assumptions` sourcing the canonical `domain.trading.core/default-fees`, stored as `:default-fee-bps` in `:execution-assumptions`; BOTH build sites (worker `payload.cljs` + frontend `rebalance_preview.cljs`) now read it. Removed the domain resolver/constants (single source). Added `:default-fee-bps` to the execution-assumptions spec. Tests: worker-path (`engine_test`), derivation (`request_builder_test`), domain nonzero-fee (`rebalance_test`), frontend (`rebalance_preview_test`).
- [x] (2026-06-22) M3 — Size-aware depth-limited slippage: `depth-limited-slippage-bps` blends visible-VWAP with a shortfall-scaled remainder penalty; existing flat-25 test replaced + monotonicity test added.
- [x] (2026-06-22) M4 — KPI honesty: `side-totals` excludes blocked rows; held-spot note "spot · manage manually"; view test added.
- [x] (2026-06-23) M5a — Required gates green: `npm run check` exit 0 (all lints + 5 shadow-cljs builds, 0 warnings); `npm test` 4806 tests / 26558 assertions / 0 failures; `npm run test:websocket` 545 tests / 0 failures. Namespace-size budgets updated: `rebalance_tab.cljs` (512), `contracts_test.cljs` (535), and for the M2′ fee work `request_builder.cljs` (511→526), `request_builder_test.cljs` (547→561), `engine_test.cljs` (522→547).
- [ ] M5b — Final acceptance: user review of the change (optionally live-verify the rebalance tab on the spectate scenario), then move this plan to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: the duplicate rows are NOT a UI/grouping defect; they are phantom instrument-ids created by a worker-boundary keywordization gap.
  Evidence: `worker_client.cljs:12` does `(js->clj data :keywordize-keys true)`, turning result-map keys `"perp:BTC"` into the keyword `:perp:BTC`. `instrument_keyed_codec.cljs` `normalize-instrument-keyed-maps` (lines 119-138) only re-stringifies inner keys for maps whose key is in `instrument-keyed-map-keys` (lines 25-47), and `:current-portfolio-weights-by-instrument` is absent. `coercion/non-blank-text` (lines 4-8) does `(some-> value str ...)`, and `(str :perp:BTC)` → the literal `":perp:BTC"` (leading colon). `preview-instrument-ids` (`rebalance_preview.cljs:123`) folds those keys into the id set; `instruments-by-id` has no entry → `row-status` returns `:blocked :market-metadata-missing` (`rebalance.cljs:229-231`).
- Observation: cost roll-ups (Gross trade, Est. fees + slippage) are already ready-only and were NOT inflated by phantoms; only `side-totals` (Buys/Sells + asset counts), group rows, and the readiness split were.
  Evidence: `rebalance.cljs:356-362` filters `ready-rows`; `rebalance_tab.cljs:190-201` `side-totals` reduces over ALL rows.
- Observation: spot is already excluded from the target universe by default (`:include-spot? false`, `defaults.cljs:16`, enforced in `engine/context.cljs`). The on-screen "spot" impression is the phantom perp legs; genuine held spot would show a `spot:`/`@` id with reason `:spot-submit-unsupported`. No spot fix required beyond a clearer label.
- Observation: `:fee-bps-by-id` is read only at `rebalance.cljs:212` and written only by tests; `:fee-mode :taker` is present in the request's `:execution-assumptions` but never mapped to bps. Canonical default fee schedule lives at `hyperopen.domain.trading.core/default-fees` `{:taker 0.045 :maker 0.015}` expressed in PERCENT (0.045% = 4.5 bps).
- Observation (2026-06-23, important correction): `build-rebalance-preview` has TWO production callers — `engine/payload.cljs:374` (inside the WORKER) and `rebalance_preview.cljs:162` (frontend). The worker attaches `:rebalance-preview` to the solved result, and the frontend `result-with-rebalance-preview` SHORT-CIRCUITS when a preview already exists. So the displayed preview is normally the worker's; only the snapshot-refresh path (`result-with-refreshed-rebalance-preview`, `rebalance_snapshot.cljs:174`) rebuilds on the frontend. The first-cut M2 wired fees only in the frontend builder, which was dead on the primary worker path → fees were still $0, exactly as re-reported.
  Evidence: `payload.cljs:374` `:rebalance-preview (rebalance-preview request ...)`; `payload.cljs:253` passed only `:fee-bps-by-id` (never populated); `rebalance_preview.cljs:191-196` short-circuit on existing `:rebalance-preview`.

## Decision Log

- Decision: Fix the phantom rows at the codec by adding `:current-portfolio-weights-by-instrument` to `instrument-keyed-map-keys`, AND add defense-in-depth by routing the preview's id collection through `ids/instrument-id-key`.
  Rationale: the one-line codec change is the precise root cause and is sufficient; the preview-side normalization makes the surface robust to any future keyed map an author forgets to register, at trivial cost.
  Date/Author: 2026-06-22 / Geronimo (assistant-implemented).
- Decision: Wire a DEFAULT fee-bps derived from `:fee-mode` (taker 4.5 bps / maker 1.5 bps) and keep the "Est. fees + slippage" label; retain the per-instrument `:fee-bps-by-id` override.
  Rationale: user chose "wire fees + keep label". A single mode-derived default is honest, conservative, and keeps the existing per-id override seam for future per-market fees.
  Date/Author: 2026-06-22 / Geronimo.
- Decision (2026-06-23, supersedes the placement of the above): resolve `:fee-mode`→bps ONCE in `request_builder/normalize-execution-assumptions`, sourcing the canonical `hyperopen.domain.trading.core/default-fees` (percent×100), and store the result as `:default-fee-bps` in `:execution-assumptions`. Both preview build sites (worker `payload.cljs` and frontend `rebalance_preview.cljs`) read that single value; the domain `cost-estimate` consumes it. Removed the domain-local `fee-bps-for-mode`/`default-{taker,maker}-fee-bps`.
  Rationale: (1) the first cut wired only the frontend builder, which is dead on the displayed worker path; resolving at request-build time covers BOTH callers from one place. (2) The user explicitly wanted the KNOWN schedule used, not a hardcoded mirror — sourcing `default-fees` removes drift. (3) The "keep the optimizer DOMAIN decoupled from the trading domain" convention is honored because the dependency lives in the application layer (`request_builder`), not the pure domain; `lint:namespace-boundaries` only forbids `hyperopen.views.*` imports, so this is allowed. Spot legs are always blocked (`:spot-submit-unsupported`) so a single perp-derived flat default is honest for every ready row.
  Date/Author: 2026-06-23 / Geronimo.
- Decision: Replace the flat depth-limited fallback with a conservative blended model: real VWAP over the visible (consumed) levels for the filled fraction, plus a penalty for the unfilled remainder that scales with the shortfall multiple (`quantity / visible-size`), floored at the existing fallback so it can never under-charge relative to today.
  Rationale: user chose "improve now (conservative)". The model is monotonic in order size, reduces to ~fallback when the order ≈ the visible book, and never reports a number lower than the current flat fallback. We genuinely cannot see past the visible book, so the remainder penalty is an explicit, documented assumption rather than a fabricated deep-book fill.
  Date/Author: 2026-06-22 / Geronimo.
- Decision: `side-totals` counts only non-blocked rows.
  Rationale: a trade-preview "Buys/Sells" should reflect what will actually be staged; blocked rows (spot-submit-unsupported, below-min-notional, and — until the codec fix lands everywhere — any stray phantom) must not inflate the headline. This also makes Buys/Sells consistent with the ready-only Gross trade KPI.
  Date/Author: 2026-06-22 / Geronimo.

## Outcomes & Retrospective

Achieved (all four implementation milestones landed and validated by tests; gates green):

- Phantom `:perp:…` legs eliminated at the root: `:current-portfolio-weights-by-instrument` is now re-stringified at the worker boundary, and the preview id path is additionally hardened through `ids/instrument-id-key`. Each single-implementation held asset now renders as one row; the spurious `market-metadata-missing` blocked rows and the doubled readiness split are gone.
- The "Est. fees + slippage" KPI now includes a real fee component derived from `:fee-mode` (taker 4.5 bps / maker 1.5 bps) sourced from the canonical `domain.trading.core/default-fees`, with the per-instrument `:fee-bps-by-id` override preserved. Crucially this is resolved at request-build time into `:execution-assumptions :default-fee-bps`, so BOTH the worker-built preview (the one shown by default) and the frontend refresh apply it — the first cut wired only the frontend and was dead on the worker path. The label stays accurate.
- Depth-limited slippage is now size-aware: visible-VWAP on the filled portion blended with a shortfall-scaled penalty on the unfilled remainder, floored at the fallback (so it never under-charges relative to the prior flat 25 bps) and strictly increasing with order size.
- Buys/Sells now exclude blocked rows, so the headline reconciles with the ready-only Gross trade KPI; held spot reads "spot · manage manually" instead of a raw keyword.

Complexity: net roughly neutral. The codec fix removes an entire class of phantom rows (a simplification of rendered state). The slippage model adds one small, well-contained pure helper (`depth-limited-slippage-bps`). The fee resolver now lives once in `request_builder` (`fee-bps-for-mode`) sourcing the canonical schedule, replacing the domain-local mirror constants — net a slight simplification (single source of truth) despite the extra request-build dependency.

Residual follow-ups (intentionally out of scope, candidates for separate ExecPlans): per-market / HIP-3 deployer fee schedules instead of a single mode-derived default; an executable-side (best bid/ask) reference price instead of mark/oracle/mid; subscribing deeper L2 depth before staging so the largest legs get a measured rather than extrapolated number; and a generic contract test asserting every instrument-keyed result/request map is registered in `instrument-keyed-map-keys`.

Live verification note: the exact spectated scenario was not re-reproduced in a fresh dev server (it depends on a re-run optimization over live market data). The behavioral changes are instead locked by fail-before/pass-after unit and view tests (see Validation and Acceptance). A live spot-check on the rebalance tab is the M5b acceptance step.

## Context and Orientation

The optimizer runs in a Web Worker. The worker returns a result map whose instrument-keyed sub-maps (e.g. `:target-weights-by-instrument`, `:current-portfolio-weights-by-instrument`) use plain string keys like `"perp:BTC"`. On the main thread, `src/hyperopen/portfolio/optimizer/infrastructure/worker_client.cljs` decodes the message with `(js->clj data :keywordize-keys true)`, which turns every map key into a keyword, then calls `normalize-worker-boundary`. The normalizer in `src/hyperopen/portfolio/optimizer/instrument_keyed_codec.cljs` re-stringifies the keys of any map whose KEY appears in the allow-list `instrument-keyed-map-keys`. A map missing from that list keeps its keyword keys.

The rebalance preview is derived on the main thread in `src/hyperopen/portfolio/optimizer/application/rebalance_preview.cljs` (`build-derived-preview`), which assembles the set of instrument-ids to show (`preview-instrument-ids`), the current/target weights, prices, and per-instrument cost contexts, then calls the pure domain function `build-rebalance-preview` in `src/hyperopen/portfolio/optimizer/domain/rebalance.cljs`. That domain function builds one row per instrument-id, classifies each row (`row-status`), and for ready rows computes a `cost` (`cost-estimate` → `cost-context`). The view `src/hyperopen/views/portfolio/optimize/rebalance_tab.cljs` groups rows by base symbol, renders KPI blocks (`summary-kpis`, `side-totals`), and a per-asset table (`asset-tbody`, `trade-row`, `leg-label`, `row-notes`).

Key terms: an "instrument-id" is a string like `"perp:BTC"` (perp) or `"perp:xyz:XYZ100"` (a HIP-3 perp on the `xyz` deployer) or `"spot:PURR"` (spot). A "leg" is one tradeable implementation row under an asset group. "Slippage" is the estimated adverse price move between a reference price and the achievable fill; here expressed in basis points (bps; 1 bps = 0.01%). "Depth limited" means the requested size exceeds the visible order book depth in the snapshot.

## Plan of Work

M1 — Phantom-leg fix (root cause).

- In `src/hyperopen/portfolio/optimizer/instrument_keyed_codec.cljs`, add `:current-portfolio-weights-by-instrument` to the `instrument-keyed-map-keys` set (the alphabetically/logically grouped `*-by-instrument` block near `:current-weights-by-instrument`). This makes `normalize-instrument-keyed-maps` re-stringify its keys back to `"perp:BTC"` via `stringify-instrument-keyed-map` → `ids/instrument-id-key`.
- Defense-in-depth in `src/hyperopen/portfolio/optimizer/application/rebalance_preview.cljs`: where `preview-instrument-ids` folds `(keys (:current-portfolio-weights-by-instrument result))` (line 123) and where `result-current-weights-by-id` reads that map (lines 49-52), normalize keys through `hyperopen.portfolio.optimizer.ids/instrument-id-key` so a stray leading-colon keyword can never become a phantom id even if some future keyed map is missing from the codec list. Require the `ids` ns (alias `ids`).
- Tests: extend `wire-codec-normalizes-worker-boundary-test` in `test/hyperopen/portfolio/optimizer/contracts_test.cljs` to include `:current-portfolio-weights-by-instrument {(keyword "perp:BTC") 0.5}` and assert it normalizes to `{"perp:BTC" 0.5}`. Add a focused application test (new file `test/hyperopen/portfolio/optimizer/application/rebalance_preview_test.cljs` if none exists) feeding a result whose `:current-portfolio-weights-by-instrument` has a leading-colon keyword key and asserting the preview produces a single row per asset (no `:market-metadata-missing` phantom).

M2 — Honest fees.

- In `src/hyperopen/portfolio/optimizer/domain/rebalance.cljs`, add local fee constants and a resolver near the existing `default-fallback-slippage-bps`: `default-taker-fee-bps` (4.5), `default-maker-fee-bps` (1.5), with a comment citing `hyperopen.domain.trading.core/default-fees` (percent×100), and `(defn fee-bps-for-mode [mode] ...)` returning maker bps for `:maker` else taker bps. In `cost-estimate`, change `fee-bps` to `(or (get-in opts [:fee-bps-by-id instrument-id]) (:default-fee-bps opts) 0)`.
- In `src/hyperopen/portfolio/optimizer/application/rebalance_preview.cljs` `build-derived-preview`, pass `:default-fee-bps (rebalance/fee-bps-for-mode (get-in request [:execution-assumptions :fee-mode]))`.
- Tests: in `test/hyperopen/portfolio/optimizer/domain/rebalance_test.cljs`, assert that with no `:fee-bps-by-id` but `:default-fee-bps 4.5`, a ready row's `:estimated-fee-usd` ≈ notional × 4.5/10000, and that a per-id `:fee-bps-by-id` still overrides the default.

M3 — Size-aware depth-limited slippage.

- In `src/hyperopen/portfolio/optimizer/domain/rebalance.cljs`, extend `visible-depth-fill` so the insufficient-depth branch also returns the consumed notional and size (e.g. `{:visible-size filled :visible-notional notional :depth-status :insufficient-visible-depth}`). Add a helper `depth-limited-slippage-bps` that computes: `visible-vwap = visible-notional / visible-size`; `visible-slip = (or (slippage-bps-from-fill-price side reference visible-vwap) fallback)`; `depth-ratio = (min 1 (/ visible-size quantity))`; `shortfall-mult = (/ quantity visible-size)`; `remainder-bps = (* fallback (max 1 shortfall-mult))`; `blended = (+ (* depth-ratio visible-slip) (* (- 1 depth-ratio) remainder-bps))`; return `(max fallback blended)`. Update the `:insufficient-visible-depth` branch of `cost-context` to use this blended value, set `:source :depth-extrapolated`, keep `:depth-status :insufficient-visible-depth` and `:fallback-reason :snapshot-depth-limited`, and set `:estimated-fill-price` to the blended-implied price (or keep reference if size/price unavailable). Guard all divisions against zero/non-finite.
- View: `src/hyperopen/views/portfolio/optimize/rebalance_tab.cljs` already renders the `:source` via `opt-format/keyword-label`; `:depth-extrapolated` will read as "Depth extrapolated" in the per-row Notes and the source-count summary. Confirm `slippage-summary-detail` still counts depth-limited rows via `:depth-status` (unchanged).
- Tests: replace the existing flat-25 assertion for the insufficient-depth case in `rebalance_test.cljs` with assertions that (a) a small order fully inside the book is unaffected; (b) an order modestly exceeding the book yields slippage ≥ fallback and ≤ a sane bound; (c) a much larger order yields strictly greater slippage than a smaller over-book order (monotonicity); (d) `:source` is `:depth-extrapolated` and `:depth-status` is `:insufficient-visible-depth`.

M4 — KPI honesty + spot label.

- In `src/hyperopen/views/portfolio/optimize/rebalance_tab.cljs`, change `side-totals` to reduce only over non-blocked rows (`(remove #(= :blocked (:status %)) rows)` or filter to `:ready`/`:within-tolerance`). Keep the function signature.
- Add a friendlier note for held spot in `row-notes`: when `(:reason row)` is `:spot-submit-unsupported`, render "spot · manage manually" instead of the raw keyword label. Keep `market-metadata-missing` as-is (after M1 it should not appear for held assets).
- Tests/snapshot: add or extend a view-model/snapshot test asserting Buys/Sells exclude blocked rows.

M5 — Validation. Run the gates and the smallest relevant Playwright check, capture evidence, update Progress + Outcomes, and move this file to `docs/exec-plans/completed/`.

## Concrete Steps

Working directory: repository root (the active worktree). Bootstrap once (already done): `npm run setup:worktree`.

Run the focused optimizer tests during iteration (faster than the full suite). After all milestones, run the required gates from `AGENTS.md`:

    npm run check
    npm test
    npm run test:websocket

Interpretation: `npm run gates` prints a single PASS/FAIL matrix for all three. `npm run check` enforces repo-state lints (including this plan's active-ExecPlan structure via `dev/check_docs.clj`) and compiles all shadow-cljs builds. Expect PASS on all three before moving the plan to completed.

## Validation and Acceptance

Behavioral acceptance (observe in the rebalance preview for a held portfolio):

1. Phantom rows gone: no row shows a leading-colon id (`:perp:...`) and no held asset is blocked with `market-metadata-missing`. Single-implementation assets render as one row. The readiness KPI reflects only genuinely blocked rows.
2. Fees non-zero: the "Est. fees + slippage" KPI's fee component equals the sum over ready rows of notional × (4.5 bps taker / 1.5 bps maker), and per-id overrides still apply.
3. Depth-limited slippage scales with size: a depth-limited row's est. slip exceeds the flat 25 bps and increases for larger over-book orders; its Notes show "depth limited" and the source reads "Depth extrapolated".
4. Buys/Sells reflect only tradeable (non-blocked) rows and reconcile with Gross trade.

Test acceptance: the new codec assertion fails before M1 and passes after; the fee-default test fails before M2 and passes after; the depth-limited monotonicity test fails before M3 and passes after; the side-totals test fails before M4 and passes after. All three required gates pass.

## Idempotence and Recovery

All edits are additive or small in-place changes and can be re-applied safely. If a gate fails on namespace-size budgets (`lint:namespace-sizes`) because of the added helpers, extract the new slippage helper into the existing domain file's private section (it stays within `rebalance.cljs`) or, if over budget, split per the namespace-size playbook; record the choice in the Decision Log. No destructive operations. The plan file itself must retain at least one unchecked `- [ ]` item until M5 completes, then be moved to `completed/`.

## Artifacts and Notes

To be filled with the before/after cost KPI numbers and the gate output transcripts as milestones complete.
