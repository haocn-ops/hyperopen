# Optimizer execution cost honesty: bounded depth extrapolation and a real TWAP cost model

## Purpose

On the optimizer's Execution tab (route `/portfolio/optimize`, Execution sub-tab of a computed
scenario), every staged order shows an estimated price cost (crossing the spread + walking the
book), and the strategy band projects an "est. all-in" cost for each bulk strategy
(Recommended / Market / Passive maker / TWAP). Two defects make those numbers untrustworthy on
large orders against thin books:

1. The Market and TWAP strategies project the SAME price cost. The user reported it directly:
   "whether we select market execution or TWAP, the estimated price cost basically looks the
   same". A TWAP (time-weighted average price) order slices a parent order into many small
   suborders spaced over time, letting the book refill between clips, so its book impact must
   be estimated well below a single market order of the full size. Today no TWAP cost model
   exists anywhere in the codebase — the recompute treats TWAP as "crossing" and charges the
   full one-shot book-walk figure.

2. When an order is larger than the visible order-book snapshot, the estimator extrapolates a
   penalty that scales LINEARLY and WITHOUT BOUND in the overrun multiple. A $16.8M sell
   against ~$41k of visible bids produced "10,225 bp" (102% of notional — a fill below zero,
   physically impossible for a sell) and an estimated price cost of $18.6M on a $30.1M
   rebalance, i.e. "the cost is almost 100% of the trade".

After this change: the TWAP strategy tile, the KPI strip, the health rail, and each TWAP-typed
order row show a sliced, replenishment-aware cost estimate that is strictly below the one-shot
market estimate whenever a real book split exists; depth-overrun estimates are capped, carry
explicit "floor" semantics (rendered with a "≥" prefix), and disclose how much of the order the
visible book actually covers.

Durable context: direct user request (Barry, 2026-07-11, session on branch
`feature/optimizer-price-cost-slippage-292601`), reproduced against the live Spectate view of a
draft Equal Risk scenario whose sells were xyz:MSTR (−$16.79M), xyz:SP500 (−$13.29M), HYPE
(−$11.33).

## Context and Orientation

All paths are repository-relative. The code is ClojureScript compiled by shadow-cljs; tests run
under `npm test` (shadow-cljs `:test` build, node runner).

How an execution cost estimate is produced today:

- `src/hyperopen/portfolio/optimizer/domain/rebalance.cljs` builds the rebalance preview.
  For each ready row it calls `cost-estimate`, which resolves a "cost context" for the
  instrument from `[:execution-assumptions :cost-contexts-by-id]` on the engine request.
  A context is either a live-orderbook top-of-book (best bid/ask only, built by
  `src/hyperopen/portfolio/optimizer/application/orderbook_loader.cljs`), a FULL l2 depth
  snapshot with `:bids`/`:asks` level vectors (built post-run by
  `src/hyperopen/portfolio/optimizer/application/rebalance_snapshot.cljs`, perp rows only,
  max 8 coins), or a flat fallback assumption (`:fallback-bps`, default 25 bp).
- With a full snapshot, `visible-depth-fill` walks the levels. If the order quantity fits,
  slippage is the real VWAP vs the reference mark (`:depth-status :full-visible-depth`). If it
  does NOT fit, `depth-limited-slippage-bps` blends the real VWAP on the visible part with a
  penalty on the remainder: `remainder-bps = fallback × (quantity ÷ visible-size)`. That
  multiplier is the bug: 25 bp × 409× overrun = 10,225 bp, displayed as a precise dollar cost.
- `cost-split` decomposes total slippage at the touch price into `:spread-bps` (touch vs mark)
  and `:impact-bps` (walking past the touch), stamped on the row's `:cost` map together with
  `:estimated-slippage-usd`, `:fee-bps`, `:maker-fee-bps`, etc.
- The Execution tab re-projects costs per order type WITHOUT re-staging:
  `type-aware-costs` in `src/hyperopen/views/portfolio/optimize/execution_shared.cljs` sums,
  for each row, the full `:estimated-slippage-usd` when the row's effective type is "crossing"
  (`:market` OR `:twap`) and zero when it rests (`:limit`/`:passive`). This single function
  feeds the KPI strip and health rail (`execution_tab.cljs`), the four strategy tiles and the
  high-cost warning (`execution_strategy_band.cljs`), and the per-row cost equation
  (`execution_order_table.cljs` `cost-breakdown`). Because it has no TWAP-specific arithmetic,
  TWAP ≡ Market everywhere.
- Order types and per-row parameters resolve in
  `src/hyperopen/portfolio/optimizer/application/execution_order_type.cljs`:
  `effective-type` (per-row override, else bulk default, `:recommended` expands via
  `recommend-exec-type`) and `row-params` (`:twap-min` minutes: 20 for clips ≥ $70k else 10;
  per-row overrides from the editor's 5/10/20 buttons). At submit time
  (`application/execution.cljs` `order-form-for-row`) a `:twap` row becomes a Hyperliquid
  `twapOrder` over `max(5, twap-min)` minutes. The venue executes TWAP orders as suborders
  every 30 seconds (`src/hyperopen/domain/trading/core.cljs`: `twap-frequency-seconds 30`,
  `twap-suborder-count minutes = 1 + minutes×60÷30`).

Terms used below: "one-shot cost" = the existing full-size book-walk estimate (what a market
order pays). "Splittable" = the cost map carries a real `:spread-bps`/`:impact-bps` split
(there was a live touch to split against); flat-fallback and prebaked estimates are not
splittable. "Floor estimate" = a number known to be a lower bound rather than a point estimate.

## The algorithms

Depth-overrun (one-shot) fix, in `depth-limited-slippage-bps`:

    visible-slip  = real VWAP slippage (bps) of the visible, consumable levels   [observed]
    edge-slip     = slippage (bps) of the WORST visible level's price vs mark    [observed]
    overrun       = quantity ÷ visible-size                                      (> 1 here)
    remainder-bps = min(max(edge-slip, fallback) × overrun,
                        max-extrapolated-slippage-bps)          ;; new hard cap, 1,000 bp
    total         = max(fallback,
                        depth-ratio × visible-slip + (1 − depth-ratio) × remainder-bps)

  where depth-ratio = visible-size ÷ quantity. Rationale: the remainder is still charged worse
  than anything observed (it starts from the book-edge marginal price, not the flat fallback,
  and grows with the overrun — for a uniform-density ladder the true average slippage grows
  linearly in size, so the shape is right), but it is CAPPED at 10% because beyond the visible
  book any point estimate is fiction; the estimate becomes an explicit floor. The cost map
  gains `:estimate-floor? true`, `:depth-overrun` (the multiple), `:depth-coverage`
  (visible-size ÷ quantity), and `:visible-notional-usd` so surfaces can disclose coverage.
  A sell's total now stays far below 10,000 bp (visible-slip is physically ≤ 10,000 for a
  sell; the remainder is ≤ 1,000), so a fill below zero can no longer be implied.

TWAP model, new pure fns in `domain/rebalance.cljs`:

    n            = 1 + 2 × max(5, minutes)        ;; venue cadence: one suborder each 30s
    slice-impact = impact-bps ÷ n                 ;; temporary impact of one clip, assuming
                                                  ;; the book refills between 30s suborders
                                                  ;; (linear-ladder approximation: walking
                                                  ;; 1/n of the size costs ~1/n the average
                                                  ;; impact)
    drift        = φ × impact-bps × (n − 1) ÷ (2n)  ;; permanent-impact accumulation: a
                                                  ;; fraction φ of each clip's impact does
                                                  ;; NOT recover; clip i has absorbed i prior
                                                  ;; permanent shifts; averaging over clips
                                                  ;; gives (n−1)/2 × φ × slice-impact
    twap-impact  = min(slice-impact + drift, impact-bps)
    twap-slip    = spread-bps + twap-impact       ;; every suborder still crosses the spread

  with φ = `twap-permanent-impact-fraction` = 0.3. Properties: at n = 1 the formula equals the
  one-shot cost (continuity); as n → ∞ it approaches spread + 0.15 × impact (slicing kills
  temporary impact but can never dodge the permanent component or the spread); it is
  monotonically non-increasing in duration; it can never exceed the one-shot estimate.
  A non-splittable cost (flat 25 bp fallback, prebaked `:slippage-bps`, untrusted snapshot)
  returns unchanged with `:twap-adjusted? false` — a flat assumption cannot honestly be
  discounted by slicing. Floor semantics propagate: a TWAP over a floor-flagged one-shot is
  itself a floor, and when notional ÷ n still exceeds the visible book notional the result is
  flagged `:slice-exceeds-visible-depth? true` (the UI can then say "even sliced, each clip
  overruns the visible book — consider a longer duration").

Layering: the math lives in the optimizer domain (`domain/rebalance.cljs`, pure and
deterministic). Type-resolution glue lives in `application/execution_order_type.cljs`: a new
`effective-crossing-cost` returns, for a row under the live staging selections, the cost
figures its EFFECTIVE type would pay (market/limit-crossing → one-shot; twap → the TWAP model
at the row's live `:twap-min`). The trading domain's 30s cadence constant is mirrored locally
in the optimizer domain (same deliberate decoupling as `min-order-notional-usd`, see the
comment at the top of `rebalance.cljs`).

## Plan of Work

Milestone 1 — bounded, honest depth extrapolation (domain). In
`src/hyperopen/portfolio/optimizer/domain/rebalance.cljs`: extend `visible-depth-fill`'s
insufficient branch to also return the worst consumed level price (`:worst-price`); rewrite
`depth-limited-slippage-bps` per the formula above with new constant
`max-extrapolated-slippage-bps` (1000); have the `:insufficient-visible-depth` branch of
`cost-context` merge the new honesty metadata onto the cost map. Update
`test/hyperopen/portfolio/optimizer/domain/rebalance_test.cljs`: recompute the exact-value
extrapolation test (ref 100, one ask 101×1, qty 3 → edge-slip 100, remainder 300, blended
233.333... bp, $7.00) and add: a huge-overrun sell whose bps stays ≤ ~1,000 blended and whose
`:estimate-floor?` is true, plus the invariant that a sell can never exceed 10,000 bp.
The existing monotonicity test must keep passing unmodified.

Milestone 2 — TWAP cost model (domain + application). Add to `domain/rebalance.cljs`:
`twap-suborder-count` (mirrors the venue cadence, minutes floored at 5) and `twap-cost`
(cost-map + minutes + notional → twap figures map, as specified above). Add to
`application/execution_order_type.cljs`: `effective-crossing-cost` (selections + row → the
row's effective-type cost figures {slippage-bps/usd, spread, impact, floor?, twap metadata}).
New tests in `rebalance_test.cljs` (continuity at n=1, monotone in duration, asymptote above
spread + φ/2 × impact, non-splittable passthrough, slice-overrun flag) and
`execution_order_type_test.cljs` (market rows return one-shot; twap rows return the reduced
figures at the live per-row duration).

Milestone 3 — surfaces. `views/portfolio/optimize/execution_shared.cljs`: `type-aware-costs`
consumes `effective-crossing-cost` per row (so a TWAP row contributes its sliced figures to
totals, spread/impact split, and the avg-bps samples) and returns `:floor?` when any included
crossing row is a floor; expose `effective-crossing-cost-bps` for the warning/table.
`execution_strategy_band.cljs`: the high-cost warning flags and lists rows by their
EFFECTIVE-type cost (a TWAP row that works below 20 bp is no longer listed; routing policy
`recommend-exec-type` intentionally keeps using the one-shot cost). `execution_tab.cljs`: KPI
"Est. price cost" / "Est. all-in cost" and the rail totals prefix "≥" when `:floor?`.
`execution_order_table.cljs`: `cost-breakdown` uses the effective figures (a TWAP row's
equation shows its sliced impact + "worked over Nm · M clips"); the row cost cell and
cost-source label disclose floor/coverage ("≥", "book covers X% of order"); fix the TWAP
editor's wrong clip count (`(max 2 (round (÷ twap-min 2)))` → the venue suborder count).
Update the corresponding view tests (`execution_strategy_band_test.cljs`,
`execution_tab_test.cljs`, `execution_order_table_test.cljs`): fixtures gain a splittable
depth row so the TWAP tile projects strictly less than the Market tile, and a floor-flagged
row renders the "≥" prefix.

## Concrete Steps

Work from the repository root. Before any gate on a fresh worktree run:

    npm run setup:worktree

Then iterate with targeted tests, and finish with the full gates:

    npm test               # shadow-cljs :test build + node runner
    npm run check          # lint + contract surfaces
    npm run test:websocket # websocket runtime suite
    npm run gates          # all of the above, single PASS/FAIL matrix

Browser verification (flows changed on the Execution tab):

    npx playwright test optimizer-execution-spectate --workers=1

## Validation and Acceptance

Acceptance is behavior, verified by unit tests and one Playwright spec:

1. Domain: for a sell of quantity 3 against a single visible ask level (101 × 1, mark 100,
   fallback 25 bp), the estimate is 233.33 bp / $7.00 with `:estimate-floor? true`,
   `:depth-overrun` 3 and `:depth-coverage` 1/3. For an order 1000× the visible book the
   estimate stays below ~1,010 bp instead of 25,000 bp. No sell estimate can reach 10,000 bp.
2. Domain: `twap-cost` with spread 2 bp, impact 100 bp, 10 minutes (21 suborders) yields
   impact 100/21 + 0.3×100×20/42 = 19.05 bp (vs 100 one-shot) and total 21.05 bp (vs 102);
   20 minutes yields a strictly smaller figure; a non-splittable cost passes through
   unchanged with `:twap-adjusted? false`.
3. Views: with a splittable snapshot row staged, the TWAP strategy tile's "est. all-in" is
   strictly below the Market tile's; before this change they were equal. A floor-flagged row
   renders "≥" on its cost and the KPI strip totals carry "≥".
4. `npm run gates` reports PASS on all three gates; the Playwright spectate spec passes.

## Progress

- [x] Root-cause both defects (confirmed: no TWAP model anywhere; unbounded
      `fallback × overrun` remainder in `depth-limited-slippage-bps`).
- [x] Milestone 1: bounded depth extrapolation + honesty metadata + domain tests.
- [x] Milestone 2: TWAP cost model + effective-crossing-cost + tests.
- [x] Milestone 3: strategy band / KPI strip / rail / order table surfaces + view tests.
- [x] Full gates green (`npm run gates`), Playwright spectate spec green.
- [ ] Owner review of the two new assumption constants (cap 1,000 bp; φ 0.3) — tune if
      real-world fills disagree.

## Surprises & Discoveries

- The l2 depth snapshots only ever cover perp rows (max 8 coins) — spot rows always price
  off top-of-book or the flat fallback, so the TWAP discount correctly never applies to them
  unless a split exists.
- The TWAP editor's "N slices" copy divided minutes by 2 (20 min → "10 slices") while the
  venue slices every 30 seconds (20 min → 41 suborders). Fixed as part of Milestone 3 so the
  copy, the cost model, and the venue agree.
- The KPI avg-bps line matched the screenshots exactly once modeled: Recommended averaged the
  2 crossing rows ((10,225 + 1,065.7) / 2 = 5,645.3), TWAP averaged 3 ((10,225 + 1,065.7 +
  25) / 3 = 3,771.9) — confirming TWAP rows were charged full one-shot cost.

## Decision Log

- Decision: cap the beyond-book remainder at 1,000 bp and mark the whole estimate a floor,
  instead of extrapolating further or refusing to estimate.
  Rationale: beyond the visible book any point estimate is unknowable; a capped floor keeps
  every downstream gate (high-cost ≥ 20 bp, passive recommendation) firing while ending the
  ">100% of notional" absurdity. The overrun multiple and coverage are disclosed instead.
  Date/Author: 2026-07-11 / Claude (Barry's session).
- Decision: TWAP model = spread + impact/n + φ·impact·(n−1)/(2n), φ = 0.3, n from the venue's
  30s cadence, computed from the one-shot spread/impact split rather than re-walking the book
  per slice.
  Rationale: one formula computable at view time from the row's existing cost map keeps the
  tiles/KPI/rail consistency contract (single recompute path) and stays exact for a linear
  ladder; per-slice book walks would need level data the staged rows don't carry and would
  produce a second model that can disagree with the first. Slice-vs-book feasibility is still
  checked via the stored visible notional.
  Date/Author: 2026-07-11 / Claude.
- Decision: `recommend-exec-type` keeps using the ONE-SHOT crossing cost; only the staged
  warning and cost projections use effective-type costs.
  Rationale: the routing question is "would crossing be expensive?" — that is what pushes a
  row to passive/TWAP in the first place; discounting it by the TWAP model would let huge
  clips route as cheap crossers.
  Date/Author: 2026-07-11 / Claude.
- Decision: non-splittable costs (flat fallback / prebaked / untrusted snapshot) get NO TWAP
  discount.
  Rationale: a flat 25 bp assumption has no impact component to slice; discounting it would
  manufacture precision from nothing.
  Date/Author: 2026-07-11 / Claude.

## Idempotence and Recovery

All edits are additive or in-place function rewrites guarded by unit tests; re-running the
steps is safe. If a milestone breaks the gates, `git checkout -- <file>` restores the prior
estimator (the one-shot path is not removed, only bounded and reused).

## Outcomes & Retrospective

2026-07-11: Shipped. Both defects fixed at the estimator level and every Execution-tab
surface (strategy tiles, KPI strip, health rail, order rows, high-cost warning) now derives
from one type-aware cost path. Market vs TWAP now projects a real tradeoff (TWAP strictly
cheaper whenever a book split exists), and depth-overrun estimates are bounded floors with
disclosed coverage instead of >100%-of-notional dollar figures. Complexity: slightly increased
in the domain (two new pure functions, four constants) but DECREASED at the surfaces — the
strategy band, KPI strip, rail, and order table all consume one `effective-crossing-cost`
instead of re-deriving crossing semantics each. Remaining: tune φ and the 1,000 bp cap against
realized fills once TWAP runs accumulate (unchecked item above).
