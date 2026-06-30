# Optimizer execution: reconcile resting orders against live fills (stop showing filled orders as "open")

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document must be maintained in accordance with `docs/PLANS.md` (and its detailed contract `/.agents/PLANS.md`).

## Purpose / Big Picture

Origin: a **direct maintainer request** — on the optimizer Execution tab, passive (post-only/ALO) limit orders submit and rest on the book showing "open"/`:resting`. When the market later reaches their price and they FILL on Hyperliquid, the tab keeps showing them as "open" ("0/5 filled" forever). The trading screen reflects such fills live; Execution does not. Goal: make Execution update like the trading screen.

Root cause (confirmed by reading the live `hyperopen.system/store` and the code): the optimizer **execution ledger is a frozen submission snapshot, never reconciled with live exchange state**. The effect adapter records `{:status :resting :response …}` once and seals the row into the execution history; the view-model re-reads it verbatim, so `:resting` is permanent (there is even a baked-in comment: *"for resting/limit orders, where no fill event ever fires"*). The trading screen never snapshots a status — it recomputes from the live `[:orders :open-orders]` / `[:orders :fills]` feeds on every render (a filled order simply vanishes from open-orders and appears in fills).

The resting row already carries its exchange oid at `[:response :data :statuses 0 :resting :oid]` — the same key the live feeds use — so the link to reconcile exists; nothing used it. Fix = a **read-only render-time overlay** in the execution view-model that re-derives a resting row's display status from the live feeds, scoped to the ledger's own oids, WITHOUT mutating the (audit) ledger.

## Context References

- Origin: **direct maintainer request** (sibling of the execution-hardening pass — see the **parent ExecPlan** `docs/exec-plans/active/2026-06-30-optimizer-block-stale-execution.md` and `optimizer-execution-resting-order-state` memory, which made `:resting` a first-class submit-time status but did NOT reconcile later fills).
- Frozen ledger: `src/hyperopen/runtime/effect_adapters/portfolio_optimizer/execution.cljs` (submit path), `src/hyperopen/portfolio/optimizer/application/execution.cljs` (`settled-row-status`, `realized-fill`, `final-ledger-status`, `resting-oid`).
- Live feeds: `src/hyperopen/websocket/user_runtime/handlers.cljs` (`[:orders :open-orders]` + `:open-orders-hydrated?`, `[:orders :fills]`), gated to the own connected account via `message-for-live-user-address?`.
- View-model: `src/hyperopen/portfolio/optimizer/application/view_model/execution.cljs`.

## Progress

- [x] (2026-06-30) Diagnosed via a 3-probe + synthesis workflow AND a live `hyperopen.system/store` read while spectating: confirmed the ledger is frozen and never reconciled; confirmed `[:orders :open-orders]`/`[:orders :fills]` are the **own-account** feeds (hydrated-but-empty in spectate, while the viewed address's orders sit in `[:webdata2 :openOrders]`) — i.e. exactly the account the optimizer executes on, so they populate during a real execution.
- [x] (2026-06-30) Domain helpers: made `execution/coin-for-row` public; added `execution/resting-oid` (reads the oid from the settled resting response) and `execution/realized-from-avg` (factored out of `realized-fill` so the live-fill overlay produces an identical `:realized` shape). `application/execution.cljs` size exception bumped 575→595.
- [x] (2026-06-30) New pure overlay `view-model/execution-reconcile.cljs`: builds the live open-oid set (`[:orders :open-orders]`, hydration-gated) + a fills index (`[:orders :fills]`) and re-derives each resting row — **conservative**: oid on book ⇒ stay resting (partial if also in fills); oid with fills + off-book (or book unknown) ⇒ `:submitted` (filled) + realized px/size from the fills; otherwise unchanged. Safe degradation (spectate / vault-target / not-hydrated / no-feed ⇒ row stays `:resting` exactly as today; never a false fill).
- [x] (2026-06-30) Wired into `execution-tab-model`: reconciles the latest-attempt rows + the display-rows source, and re-derives the run phase from the reconciled rows (via `final-ledger-status`) so the tab advances `:resting → :done` as the book fills. Every KPI / fill-count / progress bar already keys off row `:status`, so they update for free. View-model needed no contract-path literal (reuses `contracts/*`).
- [x] (2026-06-30) Tests: `view_model/execution_reconcile_test.cljs` (8 pure cases: still-resting / filled+realized / partial / no-fill-no-change / not-hydrated±fill / non-resting untouched / multi-fill VWAP, plus an integration case through `execution-tab-model` asserting `:phase :done` + `:submitted` + latest `:executed`).
- [x] (2026-06-30) Validation: `npm run gates` 34/34 PASS (5611 tests / 30291 assertions; all five builds compile; optimizer-contract-paths green after rewording a docstring that used a literal `[:portfolio :optimizer …]` path). **Live-app proof** via the running dev build: ran the shipped compiled `execution-tab-model` + `execution-tab` view in the browser runtime against a resting-ledger + fills-feed state — model returns filled/`:done`/realized; the rendered view contains "filled" + "complete" and no "open" for the filled row.
- [ ] Land: commit on the feature branch and (on maintainer review) merge to local `main`, then move this plan to `completed/`.

## Surprises & Discoveries

- (2026-06-30) `[:orders :open-orders]`/`[:orders :fills]` are **own-account** feeds (gated on the live-user address). In spectate, the viewed address's orders live in `[:webdata2 :openOrders]` and `[:orders :open-orders]` is hydrated-but-empty. This is *good* for the fix: it means the overlay is a no-op in spectate (no own-account feed) and only fires for the executing account — safe degradation, no extra gate needed.
- (2026-06-30) Cancel detection was deliberately dropped from scope: "gone from a hydrated book + no fill" could be a genuine cancel OR the openOrders/userFills propagation race, and mis-showing a filled order as canceled is worse than briefly showing resting. The overlay only promotes on POSITIVE fill evidence; everything else stays `:resting`. (Distinct `:canceled` display is a possible follow-up once `[:orders :ledger]` per-oid terminal status is consulted.)

## Decision Log

- (2026-06-30) Read-only render-time overlay (option A), NOT a websocket-driven ledger mutation (option B): the ledger is the audit record Resume/Revert/Re-stage read, and the trading screen's own pattern is recompute-on-render.
- (2026-06-30) Conservative promotion (positive fill evidence only) over aggressive absence-from-book inference, to eliminate any false-fill / false-cancel under feed races and for non-own-account (vault/spectate) cases.
- (2026-06-30) Shared the realized math (`realized-from-avg`) between the submit path and the overlay so a reconciled fill's `:realized` is identical to a crossed fill's.

## Outcomes & Retrospective

- Resting orders that fill now read "filled" and the run completes (`:resting → :done`), matching the trading screen. No persisted-ledger mutation; full safe degradation for spectate/vault/unhydrated. Pending commit + maintainer review.

## Validation and Acceptance

- `npm run gates` 34/34 PASS.
- A resting row with a matching fill in `[:orders :fills]` renders "filled" with realized px; a still-on-book row stays "open"; no own-account feed ⇒ unchanged.

## Idempotence and Recovery

Pure view-model overlay + additive domain helpers; re-running gates is the recovery. No data migration; no new action/effect contract surface; the persisted ledger is never mutated.

## Artifacts and Notes

- New file: `src/hyperopen/portfolio/optimizer/application/view_model/execution_reconcile.cljs`.
- Live verification used the running dev build (`hyperopen.system/store` read + `execution-tab-model`/`execution-tab` invoked in the browser runtime).
