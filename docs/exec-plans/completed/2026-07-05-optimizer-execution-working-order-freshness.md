# Keep named-DEX working orders live on the Execution tab

## Purpose

A passive optimizer order resting on a named (HIP-3) dex — e.g. `xyz:TSM` — is invisible to the app's merged open-orders view for its entire resting life: the websocket `openOrders` channel covers only the default dex, `webData2` only the main dex, and the per-dex `frontendOpenOrders` snapshots are refreshed only at STAGING, before the order exists. Consequences observed live (2026-07-05):

1. The row is never amendable — the amend affordance (correctly, for safety) requires seeing the order live, so a named-dex working order cannot be repriced or converted from the Execution tab at all.
2. Fill detection leans on the fills feed alone; the on-book signal that stamps partial fills and corroborates state is absent, and the surface can lag or read stale ("open" after a fill, fill progress stuck) until some unrelated action refreshes account surfaces.

After this change, a run that leaves orders resting refreshes every open-order surface immediately, and keeps them fresh on a gentle interval for as long as any working order remains — so named-dex rows become amendable within seconds of resting, fills/cancels reflect within one polling period, and the poller stops itself the moment nothing is working.

Durable context: direct user request (2026-07-05 session, bug report following the amend-working-order feature; parent ExecPlan `docs/exec-plans/completed/2026-07-04-optimizer-execution-amend-working-order.md`); verified live via the worktree dev server's nREPL — the `xyz` snapshot was stale-empty while the TSM order rested, fills arrived on the WS feed, and the reconcile overlay promoted the row correctly the moment both signals were present.

## Background a novice needs

`:effects/refresh-portfolio-optimizer-open-orders` (effect-adapter facade `*refresh-account-open-orders!*` → `order-effects/refresh-account-surfaces-after-order-mutation!`) already refreshes the base snapshot AND every named dex's `frontendOpenOrders` — it is just only dispatched at staging. The execution effect's `refresh-after-execution!` dispatches `load-user-data`/`refresh-order-history`, neither of which hydrates the per-dex snapshot map (documented in the account surface service).

The Execution tab's display state is a render-time reconcile (`execution_reconcile.cljs`) over the frozen ledger: oid on the live book ⇒ stay resting; oid off-book with fills ⇒ filled; otherwise unchanged. The LEDGER row stays `:resting` forever, so any "still working?" gate must consult the reconciled rows, not the frozen ledger — but reconciling on every store mutation is render-jank territory, so the watch gate must stay cheap and the reconcile check belongs inside the (slow) interval tick.

`progress_ticker.cljs` is the house pattern for a store-watch-gated interval with injectable timers.

## Design

1. **Refresh at run completion.** `execution-env` (effect-adapter facade) gains `:refresh-open-orders!` = the same fn the staging effect uses. `refresh-after-execution!` invokes it (fire-and-forget) whenever the settled ledger contains a `:resting` row — that is exactly when the book gained orders the streams won't cover.

2. **Poll while anything is working.** New infrastructure watcher `portfolio/optimizer/infrastructure/working_order_refresh.cljs` (`install-working-order-refresh!`, progress-ticker shape, injectable store/refresh-fn/timer fns/interval):
   - Cheap watch gate (runs on every store change, so no reconcile here): route path under `/portfolio/optimize`, own wallet address present, and the latest in-memory execution ledger has a `:resting`-status row. Start/stop only on gate transitions.
   - Interval tick (default 20s): reconcile the latest ledger rows against live feeds; if any row is still `:resting` after reconcile ⇒ call the refresh fn; else stop the interval (self-terminating — the frozen ledger keeps saying `:resting` forever, so the tick is where "everything filled/cancelled" is detected).
   - (Re)install on dev reload covers a run already resting, mirroring the ticker.

3. **Wiring.** `runtime/bootstrap.cljs` `install-runtime-watchers!` accepts `install-working-order-refresh!`/`working-order-refresh-deps`; `app/bootstrap.cljs` supplies the deps (store + the facade's refresh fn).

Deliberately NOT: subscribing per-dex websocket user channels (a websocket-runtime change, far larger surface); relaxing the amend gate (its strictness is what makes cancel+replace safe on partial fills).

## Milestones

A — completion refresh: env key + `refresh-after-execution!` change + effect-adapter test (resting ledger ⇒ refresh fn called once; no resting rows ⇒ not called).

B — working-order poller: the new infra ns + tests (gate transition starts/stops; tick refreshes while a reconciled-resting row remains; tick self-stops once fills/off-book evidence lands; no reconcile work while the gate is closed) + bootstrap wiring.

## Validation

`npm run gates` per milestone (34 gates). Live acceptance on the worktree dev server: place a passive named-dex order via execution, watch the row become amendable within one poll, fill it, watch the state flip to filled and fill-progress advance without touching anything.

## Progress

- [x] Milestone A: refresh at run completion + test
- [x] Milestone B: working-order refresh watcher + tests + bootstrap wiring
- [x] `npm run gates` green (34/34, 5,797 tests / 31,066 assertions); ExecPlan moved to completed

## Surprises & Discoveries

- The env's refresh hook must be captured BY VALUE (`:refresh-open-orders! *refresh-account-open-orders!*`, like `:submit-order!`), not wrapped in a var-dereferencing closure: the effect's async continuation runs after `with-redefs` restores the root, so a call-time deref would silently escape test rebinding and fire live fetches from unit tests.
- Installing the watcher from main bootstrap pulls the reconcile/execution namespaces toward the `:main` module (they otherwise live in the lazy `portfolio_route` chunk) — measured at +20KB on the unoptimized dev bundle (~0.1%), and the release-assets gate stayed green, so the simple wiring won over installing from the lazy route module.
- The live verification that motivated the design: during the resting window the `xyz` per-dex snapshot was stale-empty (refreshed only at staging), while the `userFills` feed DID stream the named-dex fill — so fills-only evidence eventually corrected the display, but amendability and on-book corroboration were impossible the whole time.
- `lint:docs` requires active ExecPlans to carry a literal durable-context reference phrase ("direct user request" / parent ExecPlan link).

## Decision Log

- Poll (REST refresh of surfaces that already exist) instead of new per-dex websocket subscriptions: smallest change that restores correctness; the WS-runtime extension can supersede it later.
- The reconcile check lives in the interval tick, not the store watch: the watch fires on every mutation (market data ticks constantly) and reconciling there re-introduces the render-jank class of bug.
- 20s default interval: named-dex fills already stream via `userFills`, so the poll only serves amendability, partial-stamping, and cancel-freshness; ~10 REST calls per tick stays far inside the /info budget.

## Outcomes & Retrospective

Landed both milestones. A run that leaves orders resting now force-refreshes every open-order surface (base + per-dex frontendOpenOrders) at completion, and the new `working-order-refresh` watcher keeps them fresh every 20s for exactly as long as any row of the latest attempt still reconciles to `:resting` — cheap frozen-ledger gate on the store watch, reconcile only inside the tick, self-terminating when everything fills/cancels. Named-dex working orders are now amendable within seconds of resting and their state/fill-progress reflects within one polling period. Gates 34/34 (5,797 tests / 31,066 assertions); live-verified on the running session (`watch-candidate?`/`working-rows?` both true against the user's resting TRUMP order). Known deferral: per-dex websocket user subscriptions would make this push-based and retire the poll — a websocket-runtime workstream. The watcher activates at bootstrap, so the running session needs one page reload to pick it up.
