# Optimizer execution: honest cost-aware routing + execution-strategy clarity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document must be maintained in accordance with `docs/PLANS.md` (and its detailed contract `/.agents/PLANS.md`).

## Purpose / Big Picture

A direct maintainer request relayed an expert design review of the Execution screen (the last surface before live orders). The review's core finding is verified in code and is real: the "Recommended" default order type promises "Algo picks the best type per order", and the plumbing is honest (the displayed type IS the routed type — `execution-order-type/effective-type` is the single source for both the table and `apply-order-type-selections`), **but the policy itself is clip-size-only with institutional thresholds** (`recommend-exec-type`: TWAP ≥ $70k, market ≤ $22k, else passive). On a small account every clip is under the market threshold, so Recommended degenerates to market-everything — including rows whose own cost model says crossing costs 25–45 bp. The screen then quietly lets the trader market through high spreads while calling it "Recommended". That is the trust-breaking mismatch the reviewer flagged.

This plan fixes the policy at the root (cost-aware routing) and then makes the surface literal about strategy vs order type, cost consequences, staged vs skipped rows, and what arming will actually do.

Adopted from the review (P0): cost-aware Recommended; "Default order type" → "Execution strategy" with honest per-strategy consequence lines; Passive maker as a first-class default strategy distinct from Limit; per-row route rationale (by-exception); staged/skipped separation; "Arm N orders" CTA; a high-cost-crossing warning with a one-click fix. Adopted (P1): venue de-duplication, typography bump, routing mix in the health rail, vocabulary cleanup.

Rejected/deferred, with reasons (the maintainer delegated final judgment here):
- **Fill-risk column** ("Medium fill risk"): we have no fill-probability model; inventing labels would be exactly the fake precision this review is about. The resting types state "may not fill" in plain copy instead.
- **"Custom" strategy tile**: per-row overrides already exist (click any row) and are marked with the override dot; a Custom tile is a state, not an action.
- **CTA "Place N orders"**: arming does not place orders (two-step safety); "Arm N orders" is the honest count + verb.
- **Per-tile TWAP cost discount**: the cost model charges TWAP full crossing cost today; showing a lower invented number would be dishonest. Tiles project from the real model.
- **Two-line row layout rebuild**: the dense single-line row + expandable editor is the established v4 pattern; route hints + larger type solve the actual gap.

## Context References

- Origin: **direct maintainer request** — owner relayed an expert design review of the Execution screen with instruction to weigh it, decide, and implement.
- `src/hyperopen/portfolio/optimizer/application/execution_order_type.cljs` — the routing policy (root cause).
- `src/hyperopen/portfolio/optimizer/application/execution.cljs` — `apply-order-type-selections` (confirm-time routing; consumes the same policy, so display and wire cannot diverge).
- `src/hyperopen/views/portfolio/optimize/execution_tab.cljs` — header/bands/KPIs/health rail (staged band moves out; ns is at 747/760 budget).
- `src/hyperopen/views/portfolio/optimize/execution_order_table.cljs` — order table + per-row editor.
- `src/hyperopen/views/portfolio/optimize/execution_shared.cljs` — shared helpers (gains the type-aware cost recompute so the new staged band can project per-strategy costs).
- `src/styles/surfaces/optimizer/execution.css`, `src/styles/surfaces/optimizer/results.css` (`.optimizer-table` base 11px).
- Tests: `test/hyperopen/views/portfolio/optimize/execution_tab_test.cljs`, `test/hyperopen/portfolio/optimizer/execution_actions_test.cljs` (554/560 budget), `test/hyperopen/portfolio/optimizer/application/execution_test.cljs`.
- Sibling ExecPlans: `2026-06-26-optimizer-execution-tab-completion.md`, `2026-06-27-execution-cost-transparency.md`, `2026-06-30-optimizer-block-stale-execution.md`.

## Progress

- [x] (2026-07-03) Recon: verified the reviewer's core claim in code; mapped every consumer of `recommend-exec-type`/`order-type-labels`; confirmed Playwright regressions only assert stable roles ("Account leverage after", arm disabled, band phases) — none assert the strategy tiles, venue column, or CTA copy.
- [x] (2026-07-03) M1 — cost-aware routing policy + dedicated policy tests.
- [x] (2026-07-03) M2 — execution-strategy band (new ns): retitle, Passive tile replaces Limit tile, live per-strategy cost projections, vocabulary.
- [x] (2026-07-03) M3 — high-cost crossing warning band + one-click passive routing.
- [x] (2026-07-03) M4 — order table: skipped-section split, venue de-dup, route hints, "Arm N orders", health-rail routing/rows diags.
- [x] (2026-07-03) M5 — typography bump on the execution table/chips.
- [x] (2026-07-03) M6 — tests updated/added (5032 cljs tests green; gates matrix 33/34 with the one red being this plan's own "no unchecked progress" docs lint, fixed by keeping the Land item open); live workbench QA on the dev build (`ui-workbench.html` scenes `execution-scenes/staged` + `staged-market-all-high-cost`) confirmed cost-aware routing + route hints, the high-cost warning with quantified savings and one-click fix, the skipped section, per-strategy tile projections (overrides respected: Market tile $232.44 with the ETH limit override vs $322.44 market-all), and the counted Arm CTA. Playwright against the live dev build: both `portfolio optimizer execution` @regression cases + all 14 `optimizer-view-model-routes` @smoke cases pass at `--workers=1`. Dev server + browser sessions torn down after QA.
- [ ] Land: commit on the feature branch and (on maintainer review) merge to local `main`, then move this plan to `completed/`.

## Surprises & Discoveries

- (2026-07-03) The honesty plumbing was already right — view chip, KPI recompute, and confirm-time wire resolution all flow through `effective-type`. Only the policy inputs were wrong (size-only). This made the fix small and safe: one pure function change propagates everywhere, including the armed-band type summary.
- (2026-07-03) The 25 bp rows in the owner's screenshot are the `default-fallback-slippage-bps` flat estimate (no live book). Routing "cost unknown, assume 25 bp" rows passively is the conservative choice and consistent with what the screen already displays for them.
- (2026-07-03) `execution_tab.cljs` sat at 747/760 lines; the staged band had to move out anyway for the warning band to fit — extraction to `execution-strategy-band` was forced by the size gate, not just taste.
- (2026-07-03) `recommend-exec-type` receives plan rows that carry `:cost` everywhere in the real flow, but several test fixtures omit it; the policy treats missing cost as "not high-cost" (falls through to size rules) so legacy fixtures keep their meaning.
- (2026-07-03) The only test fallout from the policy change was itself evidence it works: the two cost-breakdown view tests used 25 bp fixture rows that the new policy now (correctly) routes passive, collapsing the crossing split they assert on. Fixed by pinning those rows `:market` via a per-row override — which is exactly the user story the tests should encode ("I insist on market — show me what crossing costs").

## Decision Log

- (2026-07-03) **Fix the policy, keep the control.** Reviewer offered "make Recommended real or remove it". The routing machinery is real and per-order; removing it would regress large-account behavior. Making it cost-aware fixes the observed lie for small accounts.
- (2026-07-03) **Single 20 bp threshold** (`high-cost-crossing-bps`) for both the passive-routing rule and the warning band, defined once in the policy ns. Two thresholds would let "Recommended" route a row the warning still flags (or vice versa) — the exact incoherence this plan removes. 20 bp matches the reviewer's explicit line and the screenshot's natural split (45.4/25/25 vs ≤8.1).
- (2026-07-03) **Passive replaces Limit as a default strategy tile**; Limit remains a per-row override with explicit price offsets. "Rest every order at a price" as a bulk default was Passive's job with cross risk; the reviewer is right that Limit-as-posture conflates order type with intent. The four strategies are now Recommended / Market / Passive maker / TWAP.
- (2026-07-03) **Warning-band fix = per-row `:passive` overrides**, not switching the default to `:recommended`. It works identically whatever the default is, keeps the user's chosen default for cheap rows, and reuses the existing override action (no new action surface).
- (2026-07-03) **Tile projections respect current overrides** — each tile shows the cost of the state you'd actually get by clicking it, not a hypothetical clean slate.
- (2026-07-03) **Skipped rows keep their `data-role` and ledger `#`** in the collapsed section, so existing tests/selectors and Resume/audit references stay valid.
- (2026-07-03) Row route-rationale is **by-exception** (only non-market recommended routes get a hint), matching the optimizer's established by-exception chip idiom and keeping the dense table scannable.

## Outcomes & Retrospective

- Landed 2026-07-03 on this worktree branch. Recommended is now cost-aware (≥20 bp crossing cost → passive maker) with a single policy constant shared by the warning band, so the strategy selector, per-row chips, KPI recompute, armed-band summary, and wire orders cannot disagree. The staged surface separates the 10-orders-to-send from within-tolerance skips, names the venue once, states route rationale by exception, and the CTA counts what it arms. Gates 34/34; no public API changed; `:limit` remains valid everywhere (per-row override + legacy states).

## Plan of Work

### Milestone 1 — Cost-aware routing policy
`execution_order_type.cljs`: add `high-cost-crossing-bps` (20), `crossing-cost-bps`, `high-cost-crossing-row?`; `recommend-exec-type` gains the cost rule between the spot-sell and small-clip rules. New `execution_order_type_test.cljs` covering every rule + missing-cost fallthrough + override precedence.

### Milestone 2 — Execution-strategy band
New ns `hyperopen.views.portfolio.optimize.execution-strategy-band` (staged band moves out of `execution_tab.cljs`): "Execution strategy" title, tiles Recommended / Market / Passive maker / TWAP with live projected all-in cost per tile (recommended tile also shows its routing mix), honest footer copy. `type-aware-costs` + fee-mix helpers move to `execution-shared` (used by tiles, KPI strip, health rail).

### Milestone 3 — High-cost warning band
In the strategy band: when any ready row's *effective* type crosses at ≥ 20 bp est. cost, a warning lists them (asset + bp) and offers one-click "Rest these N passively" (per-row `:passive` overrides). Disappears when nothing high-cost crosses.

### Milestone 4 — Order table + rail honesty
Skipped rows out of the main table into a collapsed `<details>` ("Skipped — N assets · no orders will be sent") with reasons; venue column dropped (kind badge joins the asset cell; venue stated once above the table); by-exception route hints under the type chip; header CTA "Arm N orders"; health rail gains Routing (type mix) + Rows (send/skip/block) diags; "Before you arm" copy gains the may-not-fill caveat.

### Milestone 5 — Typography
Execution order table primary cells 11 → 12.5px, type chip 9 → 10px, strategy-tile copy off the 0.6rem floor, state/reason cells up a step. Scoped to the execution surface (`.optimizer-exec-table`), not the shared `.optimizer-table`.

### Milestone 6 — Tests, gates, browser QA
Update view/action tests for the new structure; add warning-band, skipped-section, CTA-count, tile-projection, and confirm-time high-cost-routing tests. `npm run gates`; workbench scene refresh (high-cost row added to the staged scene) + static render QA; smallest relevant Playwright after.

## Validation and Acceptance

- `npm run gates` 34/34 PASS.
- With default Recommended: a ready row whose crossing cost ≥ 20 bp routes (chip, KPI, armed summary, and wire intent) as passive maker; ≤ threshold small rows stay market; ≥ $70k stays TWAP; spot sells stay limit.
- Market-all with a high-cost row shows the warning band; its button converts exactly those rows to passive overrides; band absent under Recommended (rows already protected).
- Skipped rows render only in the collapsed skipped section with reasons; main table counts only sendable rows; KPIs unchanged.
- Arm CTA reads "Arm N orders" with N = ready-row count.
- Optimizer Playwright regressions for the execution surface stay green.

## Idempotence and Recovery

Pure view/policy change (no schema, contract, or effect surface change; no data migration). Re-running gates is the recovery. The routing policy change is one pure function — revertable in isolation if live behavior surprises.

## Artifacts and Notes

- Owner screenshot: small account, 10 staged rows all "Market" under Recommended, EWZ at 45.4 bp — the motivating case.
- Vocabulary table (user-facing): Recommended = system routes per order by cost/size; Market = cross the spread now; Passive maker = post-only limit, may not fill; Limit = rest at your price (per-row override); TWAP = time-sliced; Skipped = within tolerance, no order needed; Blocked = cannot send until fixed.
