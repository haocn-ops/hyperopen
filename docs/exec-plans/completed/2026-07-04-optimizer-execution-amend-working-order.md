# Amend a working order from the optimizer Execution tab

## Purpose

Once an optimizer execution run leaves passive/limit orders resting ("open") on the book, the Execution tab today only lets the trader watch them (or manage them from the trade ticket). There is no way to UPDATE a working order in place: nudge its limit price closer to (or onto) the mark, re-peg it after the market moved, or give up on resting and cross immediately as a market order.

After this change, every working ("open") row in the Execution order list is clickable and expands an inline amend editor that can:

1. **Reprice the resting order** — pick a new offset from the live mark in basis points (At mark / ±2 / ±5 / ±10 bp on the order's own side), submitted as a GTC limit, or post-only (Passive) at the live touch.
2. **Convert it to a market order** — cancel the resting order and cross immediately for the REMAINING (unfilled) size.

(A standalone "cancel without replacing" button is deliberately out of scope — see the Decision Log.)

You can see it working by: running an execution whose passive order rests, clicking the open row in the order list, choosing "At mark" (or Market), and pressing "Update order". The old order disappears from Hyperliquid's open orders, the replacement appears (or fills), and the Execution tab's ledger, KPIs, and carryover tracking all follow the replacement order — including cross-run stale-order cleanup if the replacement itself rests.

Durable context: direct user request (2026-07-04 session): "there's currently no way to update a working order … maybe change the amount that it's away from the price, do it at the mid price, change the number of basis points, or change it to a market order."

## Background a novice needs

A "working order" here is an execution-ledger row with status `:resting`: the exchange accepted the order and it sits open on the book, identified by an exchange order id (oid) at `[:response :data :statuses 0 :resting :oid]`. The optimizer's ledger is a frozen audit snapshot; the view-model overlays live open-orders/fills state per render (`view_model/execution_reconcile.cljs`), so "still working" means *reconciled* status is still `:resting` and the oid is on the live book.

Hyperliquid exposes a `modify` wire action, but this client has never implemented it — every order mutation in the app is cancel + resubmit, and the optimizer already has hardened machinery for exactly that: the execute effect (`runtime/effect_adapters/portfolio_optimizer/execution.cljs`) submits a plan's `:cancel-orders` as one batched cancel BEFORE any order and **halts the run if the cancel fails** (so a replacement can never stack on top of a live original), then submits ready rows with post-only reprice-on-reject, cloid tagging, size flooring, ledger append, carryover bookkeeping, toast, and account refresh. Resume/Revert/Restage already set the precedent of building *derived plans* from the latest ledger and reusing this one effect (`:effects/execute-portfolio-optimizer-plan`) — no new effect means no Lean formal-surface work.

Two facts make a derived "amend plan" cheap and safe:

- `build-execution-attempt`/`attempt-row` (`application/execution.cljs:303`) pass every non-`:ready` row through **unchanged** — so the amend plan can carry the run's OTHER rows (filled, resting siblings, blocked…) verbatim into the fresh ledger attempt. Sibling resting rows keep their `:response`/oid, stay reconciled by the live overlay, and re-merge idempotently into the resting carryover (keyed by oid).
- A resting ledger row's own frozen request carries the wire asset index at `[:request :action :orders 0 :a]` — the same field `carryover-entry` freezes — so the cancel wire for the amended order needs no market-metadata resolution at amend time.

Remaining size: the live open-order row (merged via `carryover/open-order-rows`, keyed by oid) carries `:sz` = the still-unfilled size and `:limitPx` = the current resting price. A partially-filled order must be replaced for `:sz`, not the original quantity, or the amend would over-order.

Live price reference: catalog entries at `[:asset-selector :market-by-key]` carry the live native mark (`:markRaw` preferred over display-rounded `:mark` — see `setup_readiness.cljs` `native-mark-price`). The amend must refresh the row's `:price` from this, because the frozen row price is the mark at plan-build time and "At mark (0 bp)" must mean the mark *now*.

## Design

New pure namespace `src/hyperopen/portfolio/optimizer/application/execution_amend.cljs`:

- `live-order-by-oid` — the live open-order row for an oid, from `carryover/open-order-rows` (all four feed sources, deduped).
- `live-native-mark` — `:markRaw`/`:mark` off the row's catalog entry (resolved like `execution/row-market`).
- `amend-target` — given state + the latest ledger row: `{:oid :asset-id :live-row :remaining-size :limit-px :live-mark}`; nil when the row is not `:resting`, has no oid, or (book hydrated) the oid is gone.
- `build-amend-plan` — `{:plan :ledger :row-id :selections :target}` → a plan tagged `:kind :amend` whose rows are the latest ledger's rows with the target row re-armed: `:status :ready`, `:quantity` = remaining size, `:price` = live mark (fallback: frozen price), fresh `:intent` carrying the selected `:order-type` (`:limit`/`:passive`/`:market`) + `:limit-bps`, with `:request`/`:response`/`:realized`/`:error`/`:reconciled` stripped; every other row passes through verbatim. `:cancel-orders` = exactly one carryover-style entry for the target (oid + frozen asset-id + coin). Summary ready/blocked/skipped counts + gross-ready notional recomputed (mirrors `build-resume-plan`). Refuses (returns `{:error :remaining-below-lot}`) when the remaining size floors below one lot for the catalog market — cancelling and then failing to replace would silently turn an amend into a cancel.
- `amend-selections` — resolves the user's amend choice for a row from the modal `:overrides`/`:params` (type limited to `:limit`/`:passive`/`:market`, default = the row's current type; `:limit-bps` default 0 = at the live mark). Shared by the action and the view-model so display and submission cannot drift.

One new action in `portfolio/optimizer/actions/execution.cljs`, registered across the standard six surfaces (`actions.cljs` facade, `runtime/action_adapters.cljs`, `runtime_catalog.cljs`, `schema/runtime_registration/portfolio.cljs`, `schema/contracts/action_args.cljs`):

- `:actions/amend-portfolio-optimizer-execution-order` (arg: row-id) — guards mirror confirm where they protect money: no-op when `:submitting?` / no plan / no ledger; error when `:execution-disabled?` (spectate); the full agent-status gate (only `:ready` submits; `:locked` → optimistic `:unlocking` + `:effects/unlock-agent-trading` with this action (and its row-id arg) as the replay; `:unlocking` → hold; anything else → enable-trading recovery modal). **Deliberately NOT gated on `stale-recommendation?`** — like Revert, amending an already-live order never derives new exposure from stale inputs (same instrument, side, remaining size; only price/immediacy change), and gating it would strand a working order. Reads the user's choices from the existing modal `:overrides`/`:params` for the row (default: the row's current type, `:limit-bps` 0 = at mark), resolves the target via `amend-target`, then emits submitting?=true, error=nil, and `:effects/execute-portfolio-optimizer-plan` with the amend plan. It must NOT run `attach-carryover-cancels` — the session carryover contains the run's OTHER live orders, and attaching it would cancel them all.

Effect layer: unchanged. Cancel-first + halt-on-failure means a failed cancel fails the re-armed row and sends nothing; `apply-execution-ledger` appends the amend attempt, prunes the cancelled oid from the resting carryover (via the `:cancellations` receipt) and records the replacement's oid; the outcome toast and account-surface refresh come for free. `:kind :amend` ≠ `:revert`, so replacement orders stay cloid-tagged (a later run can still recognize and clean them up).

View-model (`view_model/execution.cljs`): stamp each reconciled display row with `:amend {:amendable? … :oid :remaining-size :limit-px :live-mark}` — amendable when the reconciled status is `:resting`, an oid + live book row exist, and the surface is not read-only/submitting. The view never touches state for this.

View (`views/portfolio/optimize/execution_order_table.cljs`): a `:resting` row becomes clickable (reusing the `:open-row` accordion) and expands an amend-editor variant of the inline editor: header "Amend working order · SYMBOL", a facts line (resting at `:limit-px`, remaining `:remaining-size`, live mark), a Limit/Passive/Market type toggle (no TWAP — slicing a live single order is out of scope), the bps preset row for Limit (At mark / ±2 / ±5 / ±10 on the own side, via the existing `set-…-row-param` action), plain-language consequence copy per type, and one committing button: "Update order" → amend action. Styles stay in `styles/surfaces/optimizer/execution.css` inside `@layer components` reusing `--optimizer-*` tokens. The view renders the amend selection from the view-model's `:amend` projection (resolved by the same `amend-selections` fn the action uses), so what the editor shows and what the action submits cannot drift.

## Milestones

Milestone A — pure amend layer. `execution_amend.cljs` + `execution_amend_test.cljs`: target resolution (resting row → oid/frozen asset-id/live row/remaining size/live mark; nil for filled/gone rows), amend plan (target re-armed with fresh intent + refreshed price + remaining size, request/response stripped, siblings byte-identical, exactly one cancel entry, counts recomputed, below-lot refusal). What exists after: the full amend decision + plan construction provable in tests, nothing wired.

Milestone B — action + registration. The amend action with the guard stack, registered across the six contract surfaces. Tests (new `execution_amend_actions_test.cljs`): guard matrix (submitting/no-ledger/disabled/locked→unlock-with-replay/not-ready→recovery-modal), happy path emits exactly [save, save, execute] with a plan whose `:cancel-orders` is ONLY the target entry (regression against carryover attachment), emissions run through `assert-emitted-effects!`. What exists after: dispatching the action from a REPL amends a live order end-to-end through the existing effect.

Milestone C — view-model amend affordances. `:amend` stamped on display rows (amendable only when reconciled-resting + live oid + writable); view-model tests cover amendable, gone-from-book, spectate. What exists after: the view has a verified projection to render from.

Milestone D — the amend editor UI. Clickable resting rows, the editor variant with type toggle/bps presets/facts line/"Update order" button, CSS, honesty copy; view tests assert the editor renders for an open resting row and the button dispatches the amend action with the row-id. What exists after: the full user-facing feature described under Purpose.

## Validation

Every milestone: `npm run setup:worktree` once, then `npm run gates` (check + test + test:websocket as one PASS/FAIL matrix). New tests must fail before their milestone lands (RED) and pass after. Browser flow changed ⇒ run the smallest relevant Playwright command for the optimizer execution surface first (per BROWSER_TESTING.md), broadening only after it passes. Environment limit: I cannot place a live Hyperliquid order from here, so cancel+replace against the real exchange is validated by unit tests over the frozen request/response shapes; the final live confirmation (amend a real resting order, watch the oid swap on the open-orders feed) belongs to whoever runs the next supervised session.

## Progress

- [x] Milestone A: execution_amend.cljs (target + amend plan builder) + tests
- [x] Milestone B: amend action + six-surface registration + guard tests
- [x] Milestone C: view-model `:amend` row affordances + tests
- [x] Milestone D: amend editor UI + view tests (no new CSS needed — reuses the staging editor's classes)
- [x] `npm run gates` green (34/34, 5,790 tests / 31,036 assertions); Playwright execution regressions 2/2; ExecPlan moved to completed

## Surprises & Discoveries

- `attempt-row`'s untouched pass-through of non-`:ready` rows held up exactly as hoped: sibling resting/filled/blocked rows flow into the amend attempt byte-identical, keeping reconcile + carryover tracking alive with zero effect-layer changes.
- A cancel-only plan (no replacement row) has NO honest landing spot in `final-ledger-status` — zero accepted rows ⇒ `:blocked` ("halted") or `:no-op` (non-terminal ⇒ staged-plan fallback). That's why the standalone Cancel button was descoped mid-plan (see Decision Log) rather than shipped with a lying phase.
- `application/execution.cljs` sat 1 line under its 595-line size cap — publicizing `row-market` fit only after trimming its docstring. `actions/execution.cljs` (504) and `execution_order_table.cljs` (599) crossed the 500 default and gained exception entries.
- No new CSS was needed: the amend editor composes entirely from the staging editor's existing `optimizer-exec-*` classes.
- The live remaining size (`:sz`) and current resting price (`:limitPx`) must tolerate both the flat and nested `{:order …}` open-order row shapes, mirroring `carryover/order-oid`.

## Decision Log

- Cancel + resubmit, not the exchange `modify` action: the client has no modify implementation, and cancel-first/halt-on-failure is already the hardened primitive for "never let two versions of the same order be live". A modify wire action would be a new gateway + signing surface for no behavioral gain.
- Reuse `:effects/execute-portfolio-optimizer-plan` (the Resume/Revert precedent): no new effect ⇒ no Lean/effect-order-policy surface; ledger/carryover/toast/refresh semantics stay single-sourced.
- Amend is NOT stale-gated (mirrors Revert's reasoning): the order is already live; repricing or crossing its remaining size never creates exposure beyond what was confirmed. Cancel-only likewise.
- Single-click commit (no arm/confirm two-step): the run-level double confirm protects releasing a *batch* of new exposure; an amend mutates one already-authorized order — the same trust level as cancelling an order from the trade ticket.
- Replacement size = live remaining size (`:sz`), never the original quantity — replacing a partially-filled order at full size would over-allocate.
- Below-lot remainder refuses to amend rather than cancel-then-block: silently converting "update my order" into "your order is gone" is the worse surprise.
- Standalone "cancel without replacing" is descoped: a cancel-only ledger has no accepted rows, so `final-ledger-status` lands on `:blocked` (reads "halted") or `:no-op` (non-terminal ⇒ the surface falls back to the staged plan) — both dishonest displays. Fixing that means a new first-class `:cancelled` ledger/phase threaded through every `case phase` site, which is its own scoped change. Reprice/convert always re-arm a row and always land in a well-defined terminal state. Cancel stays where it lives today: the trade ticket.
- Amend plans carry sibling rows verbatim (unlike Resume, which demotes resting siblings to `:skipped :already-resting`): the siblings are still live and must stay reconciled/tracked; `attempt-row`'s pass-through and oid-keyed carryover merging make this free.

## Outcomes & Retrospective

Landed across all four milestones in one session. Every working ("open") row in the Execution order list now expands an inline amend editor: Limit (At mark / ±2 / ±5 / ±10 bp presets off the LIVE native mark), Passive (post-only at the touch, self-correcting via the existing reprice-on-reject), or Market for the remaining unfilled size — committed by a single "Update order" click through a derived `:kind :amend` plan that cancels exactly the one target oid first (halt-on-cancel-failure) and reuses `:effects/execute-portfolio-optimizer-plan` end to end (ledger append, carryover prune/record, cloid tagging, toast, account refresh — all inherited, no new effect, no Lean surface).

Validation: `npm run gates` 34/34 (5,790 tests / 31,036 assertions, includes 15 new amend tests across builder/action/view-model/view layers); Playwright execution regressions 2/2 against a fresh dev server. Complexity cost: one new pure namespace + one new action; the view/effect layers grew only additively. Remaining risk: the cancel+replace path is validated against frozen wire shapes, not a live exchange round-trip — the final confirmation (amend a real resting order, watch the oid swap on frontendOpenOrders) belongs to the next supervised live session. Known deferrals: standalone cancel-without-replace (needs a first-class `:cancelled` ledger phase), TWAP amendment, and a custom (non-preset) bps input.
