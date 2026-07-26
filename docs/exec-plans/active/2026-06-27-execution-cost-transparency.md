# Execution cost transparency: price cost (spread + impact), all-in cost, per-row breakdown

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. Maintained per `docs/PLANS.md`.

## Purpose / Big Picture

The Execution tab shows a single "Est. slippage" number but never explains what drives it — a trader can't see that a market order pays both the **spread crossing** and the **book impact**, nor how that changes when they route a clip as a resting Limit/Passive order. This rework makes the price of execution legible:

1. **Rename "Est. slippage" → "Est. price cost"** and explain it: price cost = **spread crossing + book impact** (the book-impact component is the slippage we already estimate). Supporting text shows the split (`spread $X + impact $Y · Z bp avg`).
2. **Add "Est. all-in cost" = price cost + fees** so the trader sees the true total. This replaces the **Fund 8h** KPI box (and the per-row Fund 8h column) — funding-8h was display-only and is dropped to make room (see Decision Log; this reverts the earlier M8a slice's execution-tab funding surface).
3. **Per-row expansion** shows the execution-cost breakdown for that clip: `spread crossing + book impact = price cost`, then `+ fees = all-in`, in both bp and $.
4. Everything stays **type-aware and live** (the existing dynamic recompute): a resting Limit/Passive row pays no spread/impact (it rests) and the maker fee, so its price cost ≈ 0 and its all-in ≈ the maker fee — updating instantly as the user toggles types, no re-stage.

The key enabler: today's `:slippage-bps` is already `(VWAP − mark)/mark`, i.e. it **already bundles** the half-spread (touch − mark) and the book impact (VWAP − touch). So the decomposition is a clean split at the **touch** (best book level), not new cost modelling — spread = touch vs mark, impact = the residual to the VWAP, and spread + impact = the existing total.

## Context References

- Parent ExecPlan: `docs/exec-plans/active/2026-06-26-optimizer-execution-tab-completion.md` (this builds on the M6 KPI honesty + the live type-aware slippage/fee recompute, and removes the M8a execution-tab funding surface). Direct maintainer request (this session) with two designer mockups: a KPI row [Est. price cost · Est. fees · Est. all-in cost] and a per-row breakdown [spread crossing + book impact = price cost + fees = all-in].
- Cost model: `domain/rebalance.cljs` `cost-context`/`cost-estimate` (`:slippage-bps` = VWAP-vs-mark; `:estimated-fee-usd` taker, `:maker-fee-usd` maker). HL fee schedule `domain/trading/core.cljs default-fees`.

## Scope

- **Domain (`domain/rebalance.cljs`)** — decompose the price cost: a `side-touch-price` helper (first book level on the fill side) + a `cost-split` (spread = touch-vs-mark floored, impact = total − spread). `cost-estimate` adds `:spread-bps`/`:impact-bps`/`:spread-usd`/`:impact-usd` (nil when there is no book to split, e.g. flat-fallback/untrusted/prebaked). `:slippage-bps`/`:estimated-slippage-usd` stay the TOTAL price cost (no rename → no consumer churn). Remove the M8a funding-8h computation (`funding-8h-bps`, `funding-window-hours`, the summary `:funding-8h-proj-usd`).
- **`application/rebalance_preview.cljs`** — drop the `:funding-by-id` threading (keep `maker-fee-bps`).
- **`application/execution.cljs`** — drop the `:funding-8h-bps` row copy + the `:funding-8h-proj-usd` summary key (the spread/impact ride along inside the copied `:cost` map automatically).
- **View (`views/portfolio/optimize/execution_tab.cljs`)** — KPI strip: Est. price cost (spread/impact sub, realized post-run), Est. fees, Est. all-in cost (price cost + fees); remove the Funding KPI; the cost trio reads left→right. Remove the Fund 8h column (colspan 11→10) and rename the per-row "Slip" column → "Cost" (still type-aware: resting reads "rests"). Health rail: price-cost + all-in diags replace the slippage + funding diags. Per-row editor: an Execution-cost breakdown. `type-aware-costs` accumulates spread/impact + all-in.
- **Tests + workbench** updated; funding tests/scenes removed.

## Progress

- [x] (2026-06-27) Design verified: `:slippage-bps` already bundles spread+impact, so the split is at the touch price; decomposition is additive (existing `:slippage-bps` assertions unchanged); funding-8h is display-only and safe to remove from the execution tab.
- [x] (2026-06-27) Domain: `side-touch-price` + `cost-split` → `:spread-bps`/`:impact-bps`/`:spread-usd`/`:impact-usd` on book-based cost rows (nil for flat fallback/untrusted/prebaked); `:slippage-bps` unchanged. Removed funding-8h computation (`funding-8h-bps`, `funding-window-hours`, summary `:funding-8h-proj-usd`).
- [x] (2026-06-27) Application: dropped `:funding-by-id` from `rebalance_preview` (kept `maker-fee-bps`) and the `:funding-8h-*` row copy + summary key from `execution`.
- [x] (2026-06-27) View: KPI strip → Margin / Est. price cost (spread+impact sub, realized post-run) / Est. fees / Est. all-in cost; Funding KPI removed; Fund 8h column removed (colspan 11→10); Slip→Cost; per-row Execution-cost breakdown (spread crossing + book impact = price cost + fees = all-in, bp + $); `type-aware-costs` now tracks spread/impact; ⓘ tooltips on the cost KPIs; the price-cost health diag keeps the cost-source signal.
- [x] (2026-06-27) Tests: domain spread/impact split + top-of-book (no impact); view cost-KPI reactivity (market→limit) + row-expansion breakdown; funding tests removed; execution_test / execution_actions_test / execution_tab_test fixtures updated. Budget `execution_tab.cljs` 910→960.
- [x] (2026-06-27) `npm run gates` 33/33 (5557 tests / 30135 assertions / 0 failures) + workbench visual check (BTC market breakdown spread $61 + impact $30 = price cost $91 + fees $52 = all-in $143; ETH limit → rests; KPIs price cost $136 / fees $96 / all-in $232).
- [x] (2026-06-27) Per-row breakdown layout refinement (maintainer feedback: the stacked-below breakdown was cramped and left the right ~40% of the wide row empty). The expanded editor is now a two-column flex grid (`optimizer-exec-editor-grid`): left = order-type controls + plain-English consequence + per-row "Cost basis · <source>" honesty line + the recommended note; right = the cost equation laid out across the remaining width (`optimizer-exec-cost-panel`/`-cost-eq`) with larger bp values (18px inputs, 20px price-cost/all-in), $ underneath, `+`/`=` operators between, and the all-in boxed in accent gold. Layout lives in static CSS (`execution.css`) not Tailwind arbitrary classes. Verified in the workbench at a representative 928px row: controls 345px / cost panel 531px (fills the formerly-empty right space), equation reconciles ($61+$30=$91, +$52=$143; 3.5+1.7=5.2 bp, +4.5=9.7), and a resting ETH-limit row collapses spread+impact into a "Resting order — no spread or market impact" note with price cost $0 / all-in = maker fee. Budget `execution_tab.cljs` 960→975 (two-column wrappers + cost-basis line).
- [ ] User review.

## Surprises & Discoveries

- The decomposition was free: `:slippage-bps` is already `(VWAP − mark)/mark`, so spread = touch-vs-mark and impact = the residual, summing back to the existing total. No new cost modelling, and existing `:slippage-bps` assertions stayed green.
- Removing the slippage diag's `sources` sub silently dropped the "L2 depth at <time>" / source honesty signal (a staged test asserted "snapshot"); the price-cost diag now carries both the spread/impact split AND the source frequencies.
- For a crossing row with no real book to split (flat fallback), attributing the whole price cost to "impact" keeps `spread + impact = price cost` reconciled in both the KPI sub and the per-row breakdown.
- `side-touch-price` initially sat above `orderbook-fill-price` in `rebalance.cljs` → an `undeclared Var` compile warning. It ran fine at runtime (the var resolves by call-time) and `npm run gates` stayed green (shadow-cljs warnings don't fail the build), so it slipped past the gate — caught only from the build log. Fixed by moving `side-touch-price`/`cost-split` below the orderbook helpers; all three builds (app/portfolio/test) now report 0 warnings. Takeaway: the gate matrix's PASS doesn't assert zero compile warnings — check the build log when adding forward-referencing defns.
- The portfolio workbench renders scenes inside a `/ui-workbench-canvas.html` iframe; `preview_screenshot` captures the whole 1440px page scaled into a small dark JPEG, so the dense dark-on-dark execution surface is effectively unreadable from the screenshot. DOM geometry + computed styles via `preview_eval`/`preview_inspect` into `iframe.contentDocument` are the reliable verification path (exact widths, colors, font sizes). For a crisp human-facing rendering, a `visualize` `show_widget` mockup mirroring the implemented markup/tokens beats fighting the workbench capture. (Also: the static-serve trick — `python3 -m http.server … --directory resources/public` on a fresh port — serves the dev watcher's freshly-built `portfolio.js`/`main.css` since 8080 is held by the worktree's own shadow-cljs/java process.)

## Decision Log

- Decision: keep `:slippage-bps`/`:estimated-slippage-usd` as the TOTAL price cost and ADD `:spread-bps`/`:impact-bps` (don't rename the key).
  Rationale: the rename would churn many consumers/tests for no behavior gain; "price cost" is a display label over the existing total, and the split is additive metadata.
  Date/Author: 2026-06-27 / Geronimo.
- Decision: remove the Fund 8h KPI box AND the per-row Fund 8h column AND the now-unused domain funding-8h computation (revert M8a's execution-tab funding surface), rather than leave it computed-but-hidden.
  Rationale: the maintainer dropped funding-8h to make room for all-in cost; with the display gone the domain computation is dead. The optimizer's funding-carry in `returns.cljs` (expected-return objective) is a SEPARATE concern and STAYS.
  Date/Author: 2026-06-27 / Geronimo.
- Decision: spread/impact are nil (no split shown) when there is no real book to split (flat fallback, untrusted snapshot, prebaked slippage); the breakdown then shows the total price cost as a flat estimate.
  Rationale: honesty — only decompose when the book actually supports the split; never fabricate a spread/impact attribution.
  Date/Author: 2026-06-27 / Geronimo.
- Decision: the per-row breakdown is a two-column layout (type controls left, cost equation right) with the equation spread across the full panel width and the all-in boxed, rather than the original stacked-below strip.
  Rationale: maintainer feedback — the stacked strip was cramped while the wide row left ~40% empty on the right; a two-column layout uses that space and makes the equation readable (larger bp values, clear `+`/`=` flow, accent all-in). The left column also gains a per-row "Cost basis · <source>" line, restoring the cost-source honesty signal at the row level (not just the health rail). Layout is static CSS hook-classes in `execution.css` (not Tailwind arbitrary classes) to stay robust in both prod and the workbench, per the universe-grid precedent.
  Date/Author: 2026-06-27 / Geronimo.

## Outcomes & Retrospective

Landed 2026-06-27 (branch `feature/friendly-kirch-b26798`), gates 33/33, workbench-verified. Files:
- `domain/rebalance.cljs` — `side-touch-price`, `cost-split`, `:touch-price` on book branches, spread/impact in `cost-estimate`; removed funding-8h.
- `application/rebalance_preview.cljs` + `application/execution.cljs` — dropped funding threading.
- `views/portfolio/optimize/execution_tab.cljs` — price-cost / fees / all-in KPIs (ⓘ tooltips, realized post-run), Cost column, per-row Execution-cost breakdown, `type-aware-costs` spread/impact; removed funding cell/KPI/diag + dead `signed-bps`/`format-funding-usd`.
- Tests: `domain/rebalance_test`, `application/execution_test`, `execution_actions_test`, `views/.../execution_tab_test`; `workbench/.../execution_scenes.cljs` (spread/impact demo data, ETH-limit + BTC-expanded staged scene). Budget `execution_tab.cljs` 910→960.

Behavior: the trader now sees price cost decomposed into spread crossing + book impact, the all-in cost (price cost + fees), and a per-clip breakdown — all type-aware and live. Funding-8h (display-only) was removed from the execution tab; the optimizer's `returns.cljs` funding-carry (a separate, expected-return concern) is untouched.

This reverts the M8a execution-tab funding surface from `2026-06-26-optimizer-execution-tab-completion.md` — that plan's Progress/Outcomes still describe the funding work as it was built; this plan is the authoritative record of its removal.

## Validation & Acceptance

Required gates (per `AGENTS.md`): `npm run gates`.

Acceptance:
- [x] KPI strip shows Est. price cost (with `spread $X + impact $Y` sub), Est. fees, and Est. all-in cost (= price cost + fees); no Fund 8h box.
- [x] Per-row expansion shows `spread + impact = price cost`, `+ fees = all-in` (bp and $), type-aware (resting rows read as resting/maker).
- [x] Toggling a row Market→Limit recomputes price cost (→ ~0) and all-in (→ maker fee) live; the per-row Cost column reads "rests".
- [x] `:slippage-bps` unchanged for existing tests; new domain tests assert the spread/impact split; funding-8h removed (no dead code) while `returns.cljs` funding-carry is untouched.
- [x] `npm run gates` PASS + workbench visual check.
