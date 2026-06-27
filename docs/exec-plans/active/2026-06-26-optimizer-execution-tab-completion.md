# Optimizer Execution Tab Completion: make the laid-out execution surface actually execute

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This plan is maintained in accordance with `docs/PLANS.md` (and its detailed contract `.agents/PLANS.md`).

## Purpose / Big Picture

The optimizer **Execution** tab (`/portfolio/optimize/draft?...&otab=execution`, rendered by `src/hyperopen/views/portfolio/optimize/execution_tab.cljs`) was rebuilt as a full scenario tab with a `staged → armed → running → done | halted` phase machine, a per-order type editor, an Execution-health rail, and KPI strip. The chrome is complete and matches the designer's v4 mockup. **The behaviour behind much of that chrome is not wired.** The tab honestly labels its own gaps ("Live orders submit as Market — Limit / TWAP / Passive preview routing is not yet wired", "Revert is not yet wired", "Re-stage is not yet wired").

This plan defines, per workstream, exactly what is laid out but not implemented, and how to implement each one. A reader can pick up any milestone and know the files, functions, data-contract changes, tests that will break, and risks.

After this work a trader on the Execution tab will be able to: pick a real per-order execution strategy (Market / Limit / TWAP / Passive) and have it routed accordingly; execute spot rebalance legs (not just perps); watch orders fill incrementally with a live progress bar and per-row state; recover correctly from a partial run (Resume without re-sending filled orders, Revert filled, Re-stage smaller); and use the header escape hatches (export fills, copy orders, open in ticket, abort). Today none of these change real behaviour — every ready row submits as a market order, spot legs are blocked, the progress bar jumps 0→100, and the recovery buttons are dead or buggy.

**The single most important finding: the order gateway already supports everything the UI implies.** `hyperopen.api.gateway.orders.commands/build-order-request` routes `:market` (marketable IOC), `:limit` (GTC, or `Alo` post-only), and `:twap` (`twapOrder` action) and already handles spot markets. This is overwhelmingly a *wiring* problem in the optimizer layer, not a from-scratch gateway build. The two genuine exceptions are: `:passive` has no gateway builder (it must be mapped to `:limit` + post-only), and incremental live progress requires the effect adapter to emit per-row store updates it currently does not.

This scope was produced by a multi-agent audit of the full execution stack and the v4 designer mockup, with every gap adversarially verified against the source (see Decision Log).

## Context References

Public refs:
- Direct user request (this session): for the optimizer rebalance execution page, identify what is laid out in UI but not implemented (order types, execution status), and produce an ExecPlan that lays out how each remaining piece would be implemented — measured against the designer specs and the existing UI.
- Designer source of truth: `ExecutionV4` component in the handoff bundle `hyperopen-portfolio-optimizer/project/v4.jsx` (lines ~2776–3198; `OrderTypeEditor` ~2828–2891; `recommendExecType` ~2834). Narrative constraints in the same bundle's `DESIGN.md`, `notes.jsx`, `states.jsx`, `wireframes.jsx`.

Repo artifacts:
- Canonical planning contract: `docs/PLANS.md`, `.agents/PLANS.md`.
- Operating contract / validation gates: `AGENTS.md`.
- Action/effect surface playbook: `docs/` add-action-effect-contract-surface notes; registration lives in `src/hyperopen/portfolio/optimizer/runtime_catalog.cljs` (action map + `effect-deps`), with re-export defs in `actions.cljs`, `runtime/action_adapters.cljs`, `runtime/effect_adapters.cljs`, and `runtime/effect_adapters/portfolio_optimizer.cljs`.
- Prior execution rebuild ExecPlan (for orientation): the "rebuild Execution as a full scenario tab" work (commit `d6eb5813`).

Local scratch refs (non-authoritative):
- Audit transcript + per-gap verified findings (this session's workflow `wf_ab2f8d66-a9d`).

## Scope map — what's laid out vs. wired

| # | Workstream | UI laid out? | Wired? | Verdict | Effort |
|---|---|---|---|---|---|
| M0 | Order-type policy single-source-of-truth | n/a (refactor) | partial (view-only) | prerequisite | S |
| M1 | Order-type **routing** (Market/Limit/TWAP/Passive) | yes (editor + tiles + params) | **cosmetic-only** | gap real | M |
| M2 | **Spot** leg execution | yes (rows render) | **not-implemented** (blocked) | gap real | M |
| M3 | **Incremental running** progress / per-row fills | yes (bar, glyphs) | **cosmetic-only** (0→100) | gap real | M |
| M4 | **Halted recovery** (Resume / Revert / Re-stage) | yes (buttons) | partial + **double-submit bug** | gap real + P0 bug | L |
| M5 | Header **overflow + running controls** | no | not-implemented | gap real (3 easy / 2 deferred) | M |
| M6 | Order-list + KPI **honesty** (Fund col, filter, funding/realized slippage) | partial | partial | mixed | L |

Dependency order: **M0 → M1**; **M2** independent; **M3** before honest M5 (Pause/abort) and M6 (Working filter, realized slippage); **M4** depends on M1 for non-market reverts but its P0 bug fix is independent. Recommended sequence: **M4 (P0 fix only) → M0 → M1 → M2 → M3 → M5 → M6**, with M4 (Revert/Re-stage) and the deferred M5 items landing after M1/M3.

---

## M0 — Order-type policy: one source of truth

**Problem.** `recommend-exec-type` (`execution_tab.cljs:50-58`), `effective-type` (`:68-73`), and `row-params` defaults (`:75-79`) live **only in the view**. M1 must resolve the same per-row type in the application/submission layer; if it re-derives the logic, the table preview and the submitted orders will silently diverge.

**Implementation.**
- Create `src/hyperopen/portfolio/optimizer/application/execution_order_type.cljs` (pure): `(recommend-exec-type {:instrument-type :side :delta-notional-usd})`, `(effective-type {:default-order-type :overrides} row)` (expands `:recommended`, preserves `:passive`), `(row-params {:params} row) -> {:limit-bps :twap-min}` with the existing buy=−2/sell=+2 and `>=70k ⇒ 20min` defaults.
- Resolve the effective type **in the view-model** (`view_model/execution.cljs` `execution-tab-model`) so both the view and the plan/submission read one resolved value. Have `execution_tab.cljs` consume the resolved field and delete its private copies.
- Guarantee `effective-type` always returns one of the four UI types; the submission builder (M1) must translate `:passive`/`:recommended` before handing `:type` to the gateway (see M1 hazard).

**Tests.** New `execution_order_type_test.cljs` (pure). Existing view test parity unaffected (same outputs).
**Effort:** S.

---

## M1 — Order-type routing (the headline gap)

**What the UI implies.** A default-order-type selector (Recommended/Market/Limit/TWAP/Passive), a per-row `OrderTypeEditor` with overrides, and per-row params (limit-bps offset, TWAP duration/slices). Each type maps to a distinct submission: Market crosses, Limit rests at mid±bps (GTC), Passive is post-only (never crosses), TWAP slices over N minutes.

**What's wired today.** The selections are captured but dropped before submission. The view writes `:default-order-type` / `:overrides` / `:params` to `execution-modal-path` (`actions/execution.cljs:62-89`). But `intent-for-row` (`application/execution.cljs:29-36`) reads `:default-order-type` from **`draft-execution-assumptions-path`** (a *different* key, default `:market`) and ignores the modal entirely. `order-form-for-row` (`:154-162`) emits only `{:type :side :size :price :reduce-only :margin-mode}` — no `:tif`, `:post-only`, `:twap`, or limit offset — so `build-order-request` always builds a marketable IOC limit. Even the default-order-type *tiles* are cosmetic (the modal `:default-order-type` ≠ the assumptions key that's consumed).

**Gateway capability (confirmed).** `build-order-request` (`commands.cljs:398-408`) → `:limit` sets `{:t {:limit {:tif (if post-only "Alo" tif)}}}` (`:154-157`); `:market` is IOC; `:twap` → `build-twap-action` (`:276-294`) reads `(:type :side :size)` + `(get-in form [:twap])` via `trading-domain/twap-total-minutes` and `[:twap :randomize]`, requiring `valid-twap-runtime?` (≥ 5 min). Limit/Passive need a `:price` → `canonical-price-text`. Mid/best-bid/ask come from the orderbook helpers in `domain/trading/market.cljs` (`mid-price-summary`, `best-bid-price`, `best-ask-price`, `effective-limit-price`).

**Critical seam (verifier correction).** `order-form-for-row` / `order-request-for-row` do **not** run at confirm time — they run inside the **effect adapter** (`effect_adapters/portfolio_optimizer/execution.cljs:228-244` → `build-execution-attempt` → `attempt-row` → `order-request-for-row` → `order-form-for-row`). The action only dispatches `plan`, which carries per-row `:intent`. Therefore the resolved per-row `:order-type` / `:limit-bps` / `:twap-min` **must be stamped onto each row's `:intent`** so they survive the dispatch boundary into the adapter — you cannot re-read the modal inside the adapter. The adapter already threads `:orderbooks` and `:market-by-key` into `build-execution-attempt`, so mid/best-bid-ask pricing is computable exactly where the form is built.

**Implementation.**
1. (M0) Resolve effective type + params in the view-model / shared ns.
2. Thread modal selections into plan building. Because `open-portfolio-optimizer-execution` (`actions/execution.cljs:38-53`) stages the plan then *resets* the modal, and selections change afterward, **rebuild the plan from the live modal in `confirm-portfolio-optimizer-execution`** (`:91-117`) before dispatch (or stamp resolved type onto intents at confirm). Add `:order-type-selections {:default-order-type :overrides :params}` as an input to `build-execution-plan` (`application/execution.cljs:99-103`).
3. In `intent-for-row` (`:29-36`) / `execution-row` (`:46`), replace the hardcoded `(or (:default-order-type execution-assumptions) :market)` with `(execution-order-type/effective-type selections row)`, and assoc `:limit-bps` / `:twap-min` from `row-params` onto the intent.
4. Expand `order-form-for-row` into a `case` on `(:order-type intent)`: `:market` → unchanged (IOC); `:limit` → `{:type :limit :tif :gtc :price <mid·(1+bps/1e4) string>}`; `:passive` → `{:type :limit :post-only true :price <best-bid for buy / best-ask for sell>}` (→ `Alo`); `:twap` → `{:type :twap :twap {:minutes (:twap-min intent) :randomize true}}` (ensure `twap-min ≥ 5`). Compute limit/passive price strings in `order-request-for-row` (it owns `command-context` + orderbook); fall back to the row's mark `:price` deterministically (the existing `:missing-price` guard already requires a finite mark, so a row can never reach the form without one).
5. **Hazard guard:** `normalize-order-type` maps any unknown keyword → `:limit` (a resting GTC order the user didn't intend). `:passive` and `:recommended` are *not* in `order-type-spec`. The form builder must translate `:passive` → `:limit`+post-only and `:recommended` → its resolved type *before* `build-order-request`, with an explicit default-to-`:market` so nothing leaks.
6. Loosen the honesty banners (`execution_tab.cljs:240-242, :257-258, :510-513, :625`) once routing is real — keep an honest caveat that Limit/Passive may not fully fill and TWAP works over time, and that Passive can still open/flip exposure (reduce-only is off).

**Data-contract changes.** No new persisted paths (modal keys + setter actions already exist). Intent gains a resolved `:order-type` + `:limit-bps`/`:twap-min`. `order-form-for-row` output gains optional `:tif`, `:post-only`, `:twap`. All keys are already honored by `commands.cljs`.

**Tests that WILL break / be added.** `application/execution_test.cljs:74-104` pins the exact market-IOC wire payload (`{:t {:limit {:tif "Ioc"}}}`) — extend with sibling assertions for GTC/ALO limit and `twapOrder` shapes. `execution_actions_test.cljs:23-58` pins the full execution-modal save shape — update if intents now carry resolved type. Add: plan-layer test (override → intent `:order-type`), attempt-layer test (limit/twap wire actions), effect-adapter test (`*submit-order!*` receives limit/twap action), view test (editor no longer captioned preview-only).

**Risks.** Single-source divergence (mitigated by M0); the `normalize-order-type` fallback money-hazard (step 5); limit/passive price honesty when orderbook is thin; TWAP 5-min floor must satisfy `valid-twap-runtime?`; plan-recompute timing across the staging/reset boundary.
**Effort:** M.

---

## M2 — Spot leg execution

**What the UI implies.** Every rebalance leg (perp *and* spot) is executable; the recommend logic even special-cases "liquid spot sell → rest at mid". The view already renders spot rows (the test seeds `…-order-row-spot-PURR`).

**What's wired today.** Spot is blocked **twice** and never reaches the gateway:
1. Upstream in `domain/rebalance.cljs` `row-status` (`:269-271`): an explicit early gate `(= :spot instrument-type) {:status :blocked :reason :spot-submit-unsupported}` — runs *before* price/notional checks.
2. In `application/execution.cljs` `ready-perp-row?` (`:21-27`) requires `:perp`; a spot row already blocked upstream passes through the `:blocked` branch (`:75-78`) carrying `:spot-submit-unsupported`.

Everything downstream is already spot-capable: `coin-for-row` strips `spot:` → base; `row-market` → `markets/resolve-or-infer-market-by-coin` resolves base → USDC spot market; `market-asset-idx` reads `:asset-id` first, and spot markets are stamped `:asset-id (spot-asset-id idx)` = **`10000+idx`** by `build-spot-market-entry` (so the correct offset wire id is already present — no raw-idx leak). The gateway forces `:r false` for spot and suppresses TP/SL.

**Implementation.**
1. Remove the upstream hard block (`rebalance.cljs:269-271`) so spot rows flow through the normal `:missing-price` / `:zero-delta-notional` / `:below-min-notional` / `:quantity-below-lot` / `:ready` checks (`executable-quantity` is instrument-type-agnostic).
2. Add `ready-spot-row?` mirroring `ready-perp-row?` (`:spot`, finite-positive qty, non-`:none` side, non-zero delta) and `ready-row? = (or ready-perp-row? ready-spot-row?)` used in `execution-row`. Keep `:unsupported-market-type` for genuinely unknown types.
3. Force `:reduce-only? false` explicitly on spot intents (gateway enforces it too via `effective-reduce-only`); margin-impact already returns 0 for non-perp. Optionally tag `:kind :spot-order`.
4. Add a regression test feeding a `:spot :ready` row + a `market-by-key` entry (`:market-type :spot`, `:asset-id 10000+N`) through `build-execution-plan` → `build-execution-attempt`, asserting the request carries `:a` = `10000+N` and `:r false`.

**Cross-tab + test blast radius (verifier corrections — load-bearing).**
- `row-status` is shared **domain** code rendered in the **Rebalance preview tab** (`rebalance_tab.cljs:248` shows "spot · manage manually"). Removing the gate flips spot rows there from blocked → priced ready/blocked, changing that tab's Buys/Sells/fees/slippage summaries. This cross-tab behaviour change is intended but must be re-verified.
- `:spot-submit-unsupported` is asserted in **nine** test files (e.g. `domain/rebalance_test.cljs:47`, `application/execution_test.cljs:26,60`, `application/view_model_test.cljs:404`, `views/.../frontier_chart_contract_test.cljs:348`, `views/.../execution_tab_test.cljs:96,121`, `views/.../result_vault_labels_test.cljs:148`, `runtime/.../portfolio_optimizer_execution_test.cljs:150`, `views/.../test_support.cljs:136`). These are behavioural assertions (some assert spot renders that string) that must be rewritten, not orphan-keyword cleanup.

**Risks.** First non-manual **live** spot submit path → verify on **testnet only** first (PURR/USDC idx 0 → 10000, HYPE/USDC @1035 → 11035); gate behind existing read-only/mutations-blocked checks. Spot dust without a mark price will honestly `:missing-price`-block. Spot `szDecimals` differ from perp — confirm spot instruments carry `szDecimals` through the universe payload. `prices-by-id` is instrument-keyed (`spot:<BASE>` leading-colon hazard) — if a spot price source is added, handle the keywordize boundary. No optimizer-side spot-affordability check (exchange rejects under-collateralized buys → honest `:failed`).
**Effort:** M.

---

## M3 — Incremental running progress

**What the UI implies.** During `:running`, each row animates `staged → working → filled|failed`, the progress bar fills as orders land, and the KPIs update incrementally.

**What's wired today.** The run is atomic. `confirm` sets `execution-modal-submitting-path true`, then `execute-portfolio-optimizer-plan-effect` writes `begin-execution-state` once and `submit-execution-rows!` (`:73-83`) reduces over **all** rows in one serial promise chain, mutating the store only at the end via `apply-execution-ledger` (`:270`). `derive-phase` keys `:running` purely off modal `:submitting?`; `display-rows` returns static plan rows whenever `terminal?` is false (status `:submitting` is not terminal). So `running-band` computes `filled = 0` throughout and snaps to 100% only when the batch settles. The per-row sequential result already exists inside the chain — it's just never written to the store mid-run.

**Implementation.**
1. Add an in-flight run-attempt path: `execution-run-attempt-path = (conj execution-path :run-attempt)` in `contracts/paths.cljs`, register in `path-catalog`, re-export in `contracts.cljs`. Add `:run-attempt nil` to `default-execution-state`.
2. `begin-execution-state` (`execution_workflow.cljs:13-20`) seeds `:run-attempt` = attempt with every `:ready` row reassigned `:status :queued` and `:working-row-id nil`. Add pure helpers (`mark-row-working`, `apply-row-result`). `apply-execution-ledger` clears `:run-attempt` to nil on terminal write.
3. In `submit-execution-row!` (`:35-71`), **guarded by `(= :ready (:status row))`** (so blocked/skipped rows don't flash "sending"): `swap!` the row to `:working` before the await, then write the resolved `:submitted`/`:failed` row into `:run-attempt` rows by `:row-id` after it resolves. Keep the reduce accumulation for the final ledger.
4. In `execution-tab-model`, read `run-attempt = (enrich-execution-attempt resolve-label (get-in state execution-run-attempt-path))` — enrichment is **mandatory** or rows render as bare ids. When `submitting?` and run-attempt rows present, set `display-rows` to the run-attempt rows. **Coordination note:** `:submitting?` lives on the *modal* path while `:run-attempt` lives on the *execution* path — keep `derive-phase` gating on modal `:submitting?` but read live rows from execution `:run-attempt`.
5. Render `:working`: add to `state-glyph` (`:93-99`), add a `:working` branch in `row-display-state` (`:101-109`, else it falls through to `:staged`) and a "sending" `state-cell`. `running-band` / health "Fill progress" / `kpi-strip` already count `:submitted` — include `:queued`/`:working` in the denominator so the bar starts partial.
6. (Optional, smoothness) a `:running`-gated re-render ticker following `infrastructure/progress_ticker.cljs`; not required for correctness since per-row swaps already trigger renders.

**Honesty.** `:submitted` means the order was **acked**, not necessarily fully filled (`response-ok?` only checks top-level ok + absence of per-status `:error`). Running copy should say "submitted/acked", not "filled". Submission stays strictly serial; do not parallelize (halt-as-status-classification depends on serial order).

**Tests.** Effect-adapter test driving multiple ready rows and asserting intermediate `:run-attempt` writes per row; view test for the `:working` glyph/state during `:running`.
**Effort:** M.

---

## M4 — Halted recovery (contains a P0 money bug)

**What the UI implies.** Resume (retry only still-actionable rows, never re-send filled), Re-stage smaller (reduce clips and rebuild), Revert filled (reversing orders to back out a partial rebalance).

**What's wired today.** Only Resume, and **incorrectly**. The halted-band Resume dispatches the *same* `[:actions/confirm-portfolio-optimizer-execution]` as the staged/armed Confirm. `confirm` reads `:plan` from `execution-modal-path` and dispatches `[:effects/execute-portfolio-optimizer-plan plan]` regardless of phase. **A completed run writes its outcome only to `execution-path` (the ledger); it never mutates the modal `:plan`, whose rows keep their original `:ready` status forever.** So Resume re-runs `build-execution-attempt` over all originally-ready rows and **re-submits already-filled orders** — a real-money double-submit. (Displayed halted rows come from the ledger attempt, so the bug is purely action-side: `confirm` reads the wrong data source.) `confirm-disabled?` is computed from the original plan summary, so the Resume button is enabled and the bug is reachable. Revert filled / Re-stage smaller are hard-disabled stubs ("not yet wired").

**Implementation.**
1. **P0 — stop the double-submit.** New action `resume-portfolio-optimizer-execution` reads the latest ledger (`(last (get-in state execution-history-path))`), guards on `:halted` + not submitting, and dispatches a new `[:effects/resume-portfolio-optimizer-execution latest-ledger]`. Add pure `recoverable-rows [ledger-rows]`: reset `:failed` rows to `:ready` (drop `:response`/`:error`, keep `:intent`) so the attempt rebuilds a fresh `:request`; mark `:submitted` rows `:status :skipped :reason :already-filled` so `submit-execution-row!` short-circuits; leave `:blocked`/`:skipped` as-is. Point the Resume button at the new action (`execution_tab.cljs:349`). The resume effect mirrors the execute effect (full env: `:now-ms`/`:submit-order!`/`:dispatch!` + persistence env) and **conj's a new ledger** (audit trail preserved).
2. **Revert filled.** Action `revert-portfolio-optimizer-execution-filled` → effect; pure `reversing-rows`: for each `:submitted` ledger row, emit a fresh `:ready` row with opposite `:side` (`trading-domain/opposite-side`), same qty/instrument, and `:intent` `{:reduce-only? true}`. Reuse `order-request-for-row`/`order-form-for-row` unchanged. Write a new ledger tagged `:kind :revert` (so `final-ledger-status` doesn't mislabel a revert as a normal execution in scenario history). Enable the button + add a confirm sub-state (it places live orders). **Spot caveat:** reduce-only is force-false for spot, so spot reversals are plain opposite-side orders that could oversell — size strictly to filled qty.
3. **Re-stage smaller.** Action `restage-portfolio-optimizer-execution-smaller` (default factor 0.5). Pure `restage-plan-smaller [plan ledger factor]`: take the modal plan, **exclude rows already `:submitted`** in the latest ledger, multiply remaining ready rows' `:quantity`/`:delta-notional-usd` by factor, recompute intents + summary. Save to `execution-modal-path :plan`, reset `execution-path` to default **but preserve `:history`** (otherwise the prior ledger is lost from in-memory state), and set phase `:staged`. Pure state rewrite, no network. Keep plan rows non-empty (else `has-plan?` falls to the empty state).
4. Register the 3 actions + 2 effects in `runtime_catalog.cljs` (action map + `effect-deps` closing over `runtime`) and the re-export defs; the new effects need the same env threading as the execute effect. Update halted-band + health-note copy to stop saying not-wired and to state Resume skips filled rows.

**Tests.** Action test for resume `[:effects/...]` shape + that it rebuilds only from failed/blocked; effect-adapter test that resume submits only remaining rows and appends a ledger; view test asserting Resume's click-actions (today only the button's *existence* is checked, not its wiring). Recovery naturally re-enters the phase machine (`final-ledger-status` → `:executed` ⇒ `:done`, else `:halted`) — assert that.
**Effort:** L. **Priority:** ship step 1 (the P0 bug) ahead of everything else.

---

## M5 — Header overflow + running controls

**What the UI implies (v4 `:2972-2991`).** An overflow kebab — `Export fill report (CSV)`, `Copy orders as JSON`, `Open in trade ticket`, `Abort & discard`; a running-phase `Pause / abort` danger button; a halted `Resume from #N` label naming the resume point.

**What's wired today.** None of these exist. The header renders only Back/View-tracking + a staged-only Arm button. `running-band` is a read-only bar with no buttons. The halted Resume is a bare "Resume" with no number.

**Implementation — the three easy ones (reuse existing precedents).**
1. Overflow menu component in `header` (`execution_tab.cljs:163`), items gated by phase/plan.
2. **Export fills (CSV).** Pure `fill-report-csv` builder over latest-attempt rows (label/side/type/qty/notional/slippage-bps/status/error) → CSV text + `optimizer-fills-<now-ms>.csv`. Reuse the spectate file-download precedent (`effect_adapters/spectate_mode.cljs:34-47` builds Blob + object URL + anchor.click) — generalize it to `:effects/download-file {:filename :content :mime}`. **Gate to terminal phases** (no `latest-attempt` until a ledger exists).
3. **Copy orders as JSON.** Reuse the clipboard precedent (`websocket/diagnostics_copy.cljs` `navigator.clipboard.writeText`); shape plan rows (instrument-id/side/qty/order-type/notional/intent). Source = **plan** (works pre-run). Surface success via the wallet-copy-feedback toast like spectate.
4. **Abort & discard.** Action mirroring the inverse of open: overwrite modal + execution paths with defaults, then `[:actions/set-portfolio-optimizer-results-tab :rebalance]`. Guard against running (mirror confirm's `:submitting?` no-op) — discarding mid-run would orphan an in-flight chain that still writes a terminal status (write-after-discard race).
5. **Open in trade ticket.** Action navigating `[:actions/navigate (router/trade-browser-path {:market coin})]` (the spectate pattern). Note `coin-for-row` is `defn-` private — promote it or expose `:coin` on model rows. Side/size prefill is a follow-up.

**Deferred (blocked on M3): Pause/abort + Resume-from-#N.** These need the run to be interruptible. After M3 makes it incremental: in `submit-execution-rows!`, check an `:abort-requested?` flag before each row and short-circuit; add `pause-portfolio-optimizer-execution`. **The abort must also clear modal `:submitting?`** (the `:running` phase derivation key) or the band won't transition to `:halted`. For "Resume from #N", give plan rows a stable 1-based display index and surface the first still-`:ready` number.

**Honesty.** Do **not** ship a cosmetic Pause before M3 — it would claim to stop live orders it cannot stop. Effect-order policy is per-action — register the new actions' effect order.
**Effort:** M (3 easy items) + the deferred pair after M3.

---

## M6 — Order-list + KPI honesty

**v4 vs. current.** v4's list has a per-row **Fund 8h** column and an **All/Working/Filled** filter; v4 KPIs = [Orders filled, Notional executed, **Avg (realized) slippage** vs −8 bp budget, **Cross-margin buffer ($)**, **Funding 8h proj.**]; current KPIs = [Orders filled, Notional executed, **Est. slippage**, **Margin after (%)**, **Est. fees**]. The health rail omits a funding diag.

**Classification (verified).**
- *Honest-but-different (keep or reframe, no new data):* "Est. slippage" (estimate, no budget framing), "Margin after %" (= v4's buffer, expressed as utilization), "Est. fees" (extra honest metric v4 dropped).
- *Genuinely missing — no data source today:* **Funding** (per-row + projection), **realized/avg slippage** (needs per-fill data), **slippage-budget** concept (exists nowhere).
- *Correctly absent under atomic submit:* the **Working** filter + per-row working glyph (meaningful only after M3).

**Implementation.**
1. **Fund 8h column** (`execution_tab.cljs` thead `:576-586`, row `:515-559`): add `[:th.right "Fund 8h"]` and a per-row signed-bps cell of `(:funding-bps row)` (dash for spot/cash). Bump `order-editor-row` colspan 10 → 11.
2. **Thread funding** into rows + summary. The data exists in app state (`[:history :funding-by-instrument]` / `funding-by-coin`, `funding-periods-per-year` at `paths.cljs:28`) but is not in execution. `build-execution-plan` has **no app-state access**, so the **caller `staged-plan` (`actions/execution.cljs:30-36`)** must inject `funding-by-coin` + `funding-periods-per-year`. In `execution-row` assoc `:funding-bps` for perp rows; in the summary add `:funding-8h-proj-usd` (= Σ perp `delta-notional · funding-8h-fraction`; the 8h fraction derives from the period length, not periods-per-year directly — get the sign convention right vs. the solve) and `:funding-annualized-pct` (≈ free: `average-rate · periods-per-year`, cf. `history_loader/alignment.cljs:154`).
3. **Funding KPI + health diag**, and reframe slippage with a budget. Decide the 5-card layout (v4 dropped fees for funding; the grid is `lg:grid-cols-5` so a 6th needs a layout change). Slippage budget = new `:execution-assumptions :slippage-budget-bps` (honest default, labelled a *target* not a guarantee — never hardcode −8 as a fake number).
4. **All/Working/Filled filter.** Add `:order-filter :all` to `default-execution-modal-state`, an action, model exposure, and the toggle in the list header; filter rows by display-state before render. **Until M3, "Working" is empty** — disable it (not-yet-wired title) or scope to "Filled vs Pending".
5. **Realized slippage** (depends on M3 + fill parsing). In `submit-execution-row!` success branch, parse avg fill px from `[:response :data :statuses][i][:filled :avgPx]` (note: nested under `:filled`, not directly on the status; handle the `:resting` post-only case with no `:filled`; average by filled size) and assoc `:realized-slippage-bps`/`-usd` onto the ledger row. Measure against the **same reference the estimate used** (rebalance `:estimated-fill-price`/reference-price), not just the execution mark, or the two aren't comparable. Pre-run KPIs stay on the estimate; only post-run can the label say "realized within/over budget".

**Honesty.** Never label an estimate as "realized". Nil-guard new summary keys (a stale modal from before the change lacks them). Per-row realized cells must read off the **latest-attempt** rows (post-run display source), which they will since the effect swaps the full row.
**Effort:** L.

---

## Cross-cutting honesty & policy constraints (from the designer narrative)

These are hard rules the implementation must respect (DESIGN.md / notes.jsx / states.jsx / wireframes.jsx):
- **Execution is desktop-only** (stated repeatedly): do not render a working commit surface on mobile — at most a preview that dead-ends to "Open on desktop to execute".
- **Commit is deliberately multi-click** (mode → arm → confirm), never a one-click submit. Do not collapse the arm/confirm gate.
- **Preview reads "no orders submitted" until commit**; the staged phase must never read as if orders are live.
- **Cost figures must state their assumption + source** (e.g. "maker-only assumption", "L2 depth at <time>").
- **Right rail mirrors the Results/Rebalance diagnostics rail** (visual continuity = trust).
- **Partial/halted offers resume / re-stage / revert only — never silent auto-retry.**
- **Running progress must reflect real per-order submission, not a decorative spinner.**
- **Shadow/read-only scenarios must refuse to submit.** Verify the existing `mutations-blocked` / read-only gate covers this (it gates the Arm button today).
- **CTA colour discipline:** primary green for affirmative actions (Arm/Stage), danger red for destructive (Revert/Abort), ghost for secondary. All numbers mono + tabular-nums.
- **Hyperliquid-only reality:** the v4 mockup shows a "Venue" column with Binance — that is mockup fiction. Keep venue = Hyperliquid; do not build multi-venue routing.

## Validation & Acceptance

Required gates when code changes (per `AGENTS.md`); in a fresh worktree run `npm run setup:worktree` first:
- `npm run check` (lints incl. `lint:docs` for this plan, `lint:namespace-sizes`, `optimizer-contract-paths`, + 5 shadow-cljs builds).
- `npm test` and `npm run test:websocket` (or `npm run gates` for a single PASS/FAIL matrix).
- UI work touches `views/portfolio/optimize/**` → run the smallest relevant Playwright command first, then broaden.
- Watch the **namespace-size budgets**: `execution_tab.cljs` (729 lines), `application/execution.cljs`, and the execution test files are all candidates to breach budgets; bump the EDN budgets as part of each milestone.

Acceptance per milestone:
- [x] M0 — `recommend/effective/row-params` live in one pure ns; view + plan consume the same resolved type; no view-output change.
- [x] M1 — picking Limit/TWAP/Passive (default or per-row) produces the corresponding wire action (GTC/ALO limit, `twapOrder`); `:passive`/`:recommended` can never leak to the gateway as a stray GTC limit; honesty banner removed/loosened; attempt + adapter + view tests updated/added.
- [x] M2 — a spot rebalance leg stages as `:ready` and submits with wire `:a = 10000+idx`, `:r false`; spot test assertions updated (the block had one direct test, `domain/rebalance_test`; the fixture-based blocked-row tests still validate the blocked-row path and pass unchanged); rebalance-preview tab now prices spot. (testnet live check still open — see Final.)
- [x] M3 — during a run, rows animate `queued → working → submitted/failed` and the bar fills incrementally; running/health copy says "submitted"; workflow + view-model tests assert the live run-attempt path; the existing effect-adapter test exercises the per-row swaps.
- [x] M4 — Resume no longer re-submits filled rows (P0 fixed) and re-enters the phase machine correctly; Revert filled and Re-stage smaller are wired with ledger/audit handling; action tests added.
- [x] M5 — Open in ticket + Abort & discard (overflow menu), Pause/abort (running), and Resume-from-#N shipped; Copy JSON + Export CSV deferred (each needs a new global clipboard/download effect).
- [x] M6 (filter) — All/Working/Filled filter shipped (stable order numbers under filtering). Fund 8h column + funding projection + realized slippage deferred (data plumbing / live fill-response verification — see Decision Log + Progress).
- [ ] Final — Playwright browser-QA pass + user review + testnet verification of live spot (M2) and TWAP/limit (M1), then move this plan to `docs/exec-plans/completed/`.

## Progress

- [x] (2026-06-26) Audit of the full execution stack + v4 designer mockup completed; six workstreams identified, each adversarially verified against source.
- [x] (2026-06-26) M0 — order-type policy single-source-of-truth: extracted `application/execution_order_type.cljs` (recommend/effective/row-params); the view aliases it so the table preview and the submitted plan cannot diverge.
- [x] (2026-06-26) M1 — order-type routing: `apply-order-type-selections` stamps the resolved type/params onto each row's `:intent` at confirm; `order-form-for-row` emits Limit (GTC + mark±bps), Passive (limit + post-only/ALO), TWAP (`twapOrder` over twap-min), Market (IOC); unmapped types fall back to `:market` (closes the normalize-order-type hazard). Honesty banners updated.
- [x] (2026-06-26) M2 — spot leg execution: removed the upstream `:spot-submit-unsupported` block (`domain/rebalance.cljs`); `ready-row?` accepts spot; spot intents tag `:spot-order` + reduce-only false; wire asset-id 10000+idx verified by test. Cross-tab: the rebalance preview now prices spot rows too.
- [x] (2026-06-26) M3 — incremental running progress: new `execution-run-attempt-path`; `begin-execution-state` seeds it; the submit loop swaps each row `:working` → `:submitted/:failed` live; the view-model shows in-flight rows during `:running`; `:working` glyph/"sending…" cell + counting updated.
- [x] (2026-06-26) M4 — halted recovery: **P0 Resume double-submit fixed** (`build-resume-plan` retries only failed rows, demotes filled → `:skipped :already-filled`); Revert filled (reversing reduce-only market orders, ledger tagged `:revert`) and Re-stage smaller (half-size unfilled rows, preserves history, returns to staged) wired. All reuse the existing execute-plan effect.
- [x] (2026-06-26) M5 — header overflow + run controls: overflow `<details>` menu (Open in trade ticket / Abort & discard), running-phase Pause/abort (abort flag + submit-loop checkpoint, now feasible post-M3), "Resume from #N" label.
- [x] (2026-06-26) M6 (partial) — order-list All/Working/Filled filter (now meaningful post-M3); stable order-number indexing under filtering.
- [x] (2026-06-27) Slip-column honesty fix (from live review): the SLIP column was showing the book-crossing market-impact estimate for ALL order types, badly overstating resting orders (a SOPH limit read 1088.8 bp). Now type-aware via `slip-cell` — Limit/Passive read "rests", Market/TWAP show the impact estimate; the per-order editor's est-fill is consistent ("rests — fills at your price or better"). View test added.
- [ ] Follow-up (from live review): 0 bp on staged Market rows is misleading. `slippage-bps-from-fill-price` clamps *favorable* fills to 0 (`(max 0 …)`); when a market's slippage snapshot is stale/mismatched (live store had only `xyz:STRC` subscribed, and synthetic `xyz:` markets showed est-fill prices wildly off mark — COPPER 6.24 vs 39.55, EIGEN 0.2329 vs 0.2098), the implausible "favorable" fill clamps to a deceptive 0 bp. Guard: when the snapshot fill is implausibly far from the reference (esp. favorable beyond a threshold), treat the estimate as untrusted (fall back to fallback-bps / a stale marker) instead of reporting 0. Cost-model change in `domain/rebalance.cljs` (affects rebalance preview too) — likely partly environmental (stale testnet books), so confirm against a live account first.
- [ ] M6 (deferred) — Fund 8h column + funding-8h projection, and realized slippage from `[:filled :avgPx]`. Both need new data plumbing (funding-by-coin threading; live HL fill-response capture to verify the parse shape + sign/reference conventions). Deferred deliberately: shipping an unverified funding sign or an estimate mislabelled "realized" would violate the project's honesty mandate. Tracked here as the next slice.
- [x] (2026-06-26) All gates green after each milestone: final `npm run gates` 33/33 — 5544 tests, 30079 assertions, 0 failures.
- [ ] Browser-QA: live Playwright pass on the execution tab (staged → armed → running → done | halted, plus the new overflow/filter/recovery affordances) on a worktree dev server. Not yet run (view-render unit tests pass; AGENTS.md routes committed browser coverage to Playwright).
- [ ] User review + testnet verification of live spot (M2) and TWAP/limit (M1) routing before production use; then move this plan to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The order gateway already supports every order type the UI implies. Evidence: `commands.cljs:398-408` `build-order-request` routes `:limit` (`{:t {:limit {:tif "Alo"|tif}}}`, `:154-157`), `:market` (IOC), and `:twap` (`build-twap-action`, `:276-294`). The gap is the optimizer never passes the type/params.
- Observation: `:passive` has **no** gateway builder. Evidence: `order-type-spec` (`domain/trading/core.cljs:4-45`) lists only market/limit/stop/take/scale/twap; `normalize-order-type` maps any unknown keyword → `:limit`. So Passive must be implemented as `:limit` + post-only, and an unmapped `:passive`/`:recommended` leaking to the gateway becomes an unintended resting GTC limit (a real-money hazard).
- Observation: The per-order-type editor, default-type tiles, and params are written to `execution-modal-path` but the submission reads `draft-execution-assumptions-path` — two different keys. Even the default tiles are cosmetic. The submission form is materialized in the **effect adapter**, not at confirm, so resolved type must be stamped onto each row's `:intent` to cross the dispatch boundary.
- Observation: Resume is a live double-submit bug. Evidence: a run writes results only to `execution-path`'s ledger; the modal `:plan` rows stay `:ready` forever, so `confirm`'s replay re-submits filled orders. The button is enabled (its `confirm-disabled?` reads the stale plan summary), so the bug is reachable.
- Observation: Spot is blocked one stage earlier than the orchestrator brief assumed — at `rebalance.cljs:269-271` (`:spot-submit-unsupported`), shared with the Rebalance preview tab, and asserted across nine test files. The execution-layer `:unsupported-market-type` fallback is effectively dead for spot.
- Observation: Live progress needs both an effect change (per-row store swaps) **and** a view-model change — even if the effect wrote incremental statuses, `display-rows` only swaps to attempt rows when `terminal?`, so `:running` would still read static plan rows.
- Observation: `:submitted` means **acked**, not filled (`response-ok?` only checks top-level ok + per-status `:error`); realized fill price is recoverable from `[:response :data :statuses][i][:filled :avgPx]` but nothing parses it today.

## Decision Log

- Decision: Sequence the P0 Resume double-submit fix (M4 step 1) ahead of all feature work.
  Rationale: it can re-send already-filled live orders today and is reachable from an enabled button — a correctness/money bug, not a feature gap.
  Date/Author: 2026-06-26 / Geronimo (assistant-drafted, pending user confirmation).
- Decision: Implement `:passive` as `:limit` + post-only (`Alo`) rather than adding a new gateway order type.
  Rationale: post-only ALO limit is exactly the "never cross the spread" semantics the UI describes, and the gateway already supports it; a new builder would be redundant surface.
  Date/Author: 2026-06-26 / Geronimo.
- Decision: Resolve the effective per-row order type once (in the view-model / a shared pure ns) and have both the table preview and the submitted plan consume it (M0 precedes M1).
  Rationale: the resolution currently lives only in the view; duplicating it in the submission layer would let the preview and the real orders diverge silently.
  Date/Author: 2026-06-26 / Geronimo.
- Decision: Treat "honest-but-different" KPIs (Est. slippage, Margin-after %, Est. fees) as acceptable rather than forcing exact v4 parity; only add the genuinely-missing funding/realized-slippage metrics, and never hardcode a fake slippage budget.
  Rationale: the designer's overriding rule is honesty over breathless parity; a fabricated −8 bp budget or an estimate mislabelled "realized" would violate it.
  Date/Author: 2026-06-26 / Geronimo.
- Decision: Defer Pause/abort and "Resume from #N" until M3 makes the run incremental; ship the three data-only overflow items (Export/Copy/Abort) and Open-in-ticket first.
  Rationale: a Pause button over an atomic, non-interruptible promise chain would be cosmetic and dishonest.
  Date/Author: 2026-06-26 / Geronimo.
- Decision (revised at implementation time): M3 landed first, so Pause/abort WAS implemented (abort flag + submit-loop checkpoint). Of the overflow items, Open-in-ticket and Abort & discard shipped; Copy-orders-JSON and Export-fills-CSV were deferred (each needs a new global side-effecting effect — clipboard/download — for read-only convenience value).
  Date/Author: 2026-06-26 / Geronimo.
- Decision: Defer the M6 funding column/projection and realized-slippage sub-items.
  Rationale: both require data that isn't safely available here — the funding-8h projection needs funding-by-coin threaded through `staged-plan` plus a correct period-fraction and pay/receive sign convention (easy to get wrong → misleading), and realized slippage needs the live Hyperliquid fill response shape (`[:filled :avgPx]`) verified against a real capture, measured against the SAME reference the estimate used. The project's honesty mandate makes shipping an unverified sign or an estimate mislabelled "realized" worse than deferring. The All/Working/Filled filter (the self-contained, M3-enabled M6 item) shipped.
  Date/Author: 2026-06-26 / Geronimo.

## Outcomes & Retrospective

Landed 2026-06-26 (branch `feature/friendly-kirch-b26798`). M0–M5 fully, M6 partially (filter), behind green gates after each milestone (final `npm run gates` 33/33: 5544 tests / 30079 assertions / 0 failures).

Files changed:
- New: `src/hyperopen/portfolio/optimizer/application/execution_order_type.cljs` (shared policy).
- Application: `application/execution.cljs` (order-form per-type builder, `apply-order-type-selections`, `ready-row?`/spot intent, `build-resume-plan` / `build-revert-plan` / `build-restaged-plan`), `application/execution_workflow.cljs` (`:run-attempt` seed + `set-run-attempt-row-status`), `application/view_model/execution.cljs` (live run-attempt display + `:order-filter`), `domain/rebalance.cljs` (removed spot block).
- Actions/effects: `actions/execution.cljs` (resume/revert/restage/pause/discard/open-in-ticket/order-filter), `runtime/effect_adapters/portfolio_optimizer/execution.cljs` (per-row `:working` swaps + abort checkpoint). All recovery/run actions reuse the existing `:effects/execute-portfolio-optimizer-plan` effect — no new effects, so no Lean effect-order gate.
- Contract surface: `contracts/paths.cljs` + `contracts.cljs` (run-attempt + abort-requested paths), `actions.cljs`, `runtime/action_adapters.cljs`, `runtime_catalog.cljs`, `schema/contracts/action_args.cljs`, `schema/runtime_registration/portfolio.cljs` (7 new actions registered).
- View/style: `views/portfolio/optimize/execution_tab.cljs` (overflow menu, Pause, recovery buttons, `:working` state, filter toggle, honesty copy), `styles/surfaces/optimizer/execution.css` (overflow menu).
- Tests: extended `execution_actions_test`, `application/execution_test`, `application/execution_workflow_test`, `application/view_model_test`, `domain/rebalance_test`.
- Budgets bumped (`dev/namespace_size_exceptions.edn`): `action_args.cljs` 632→642, `execution_tab.cljs` 735→785, `view_model_test.cljs` 525→545.

What changed in real behaviour: the default `Recommended` now actually routes per clip (TWAP/limit/passive/market) instead of everything-as-Market; spot legs execute; the running bar animates per fill; Resume no longer re-sends filled orders; Revert/Re-stage/Pause/Abort/Open-in-ticket work; the order list filters.

Deferred (next slice): M6 funding column/projection + realized slippage — see Decision Log. Browser-QA Playwright pass and testnet verification of live spot/TWAP remain open before this moves to `completed/`.
