# Let read-only (Spectate) views simulate execution strategy & per-order edits, with sending still blocked

This ExecPlan is a living document maintained in accordance with /hyperopen/.agents/PLANS.md. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current as work proceeds.

## Purpose / Big Picture

On the optimizer **Execution** tab, a single plan-level flag — `:execution-disabled?` — does two
jobs at once:

1. **Blocks committing** live orders — Arm, Confirm & send, and amend/revert/resume of live orders. This is correct: you must not trade an account you're only viewing.
2. **Locks the simulation controls** — the execution-strategy selector (Recommended / Market / Passive maker / TWAP) and the per-order type/param editors.

That flag is `true` for every read-only viewing context via
`hyperopen.account.context/mutations-blocked-message` (Spectate Mode, viewing another trader's
portfolio, an unavailable subaccount), plus the separate stale-recommendation case.

The Execution tab is largely a **what-if cost simulator**: choosing a strategy or an order's type
only re-projects estimated costs through `execution-shared/type-aware-costs` — the same pure
recompute that already feeds the KPI strip and the health rail. It sends nothing. So a user in
Spectate Mode should be able to compare Market vs Passive vs TWAP (and per-order tweaks) and watch
**Est. price cost / fees / all-in** move, while Arm / Confirm / send stay hard-blocked.

This change decouples "can simulate" from "can send." Per the requester (2026-07-07), simulation is
unlocked in **all read-only viewing contexts**, not only Spectate.

Observable outcome: with the app running under Spectate Mode, opening a solved scenario's Execution
tab lets you click the Market / Passive maker / TWAP tiles and open any order row to change its type
— the KPI strip re-projects live — while the "Spectate Mode is read-only…" notice explains that
arming and sending remain disabled and the Arm button stays greyed out.

## Context References

Public refs:
- Direct user request (2026-07-07): in Spectate Mode on the Execution screen, allow changing the execution strategy and individual order rows to see cost impact; keep order submission blocked. Chosen scope: all read-only viewing contexts.

Repo artifacts:
- src/hyperopen/portfolio/optimizer/application/view_model/execution.cljs (`execution-tab-model`) — where the plan flag is projected onto the tab model.
- src/hyperopen/portfolio/optimizer/application/execution.cljs (`build-execution-plan`) — sets `:execution-disabled?` / `:disabled-reason` / `:disabled-message` from `mutations-blocked-message`. Source of truth for the commit block; unchanged.
- src/hyperopen/portfolio/optimizer/actions/execution.cljs — commit-side action gates (`confirm-`, `amend-`, `revert-`, `resume-`) short-circuit on `:execution-disabled?`; unchanged.
- src/hyperopen/views/portfolio/optimize/execution_strategy_band.cljs, .../execution_order_table.cljs — the two view surfaces that read the old `:read-only?` model key.
- src/hyperopen/account/context.cljs — `mutations-blocked-message` / `inspected-account-read-only?` (global, shared across trade/funding; unchanged).

## Orientation: how the flag flows today

`build-execution-plan` stamps `:execution-disabled? (boolean disabled-message)` where
`disabled-message` is `mutations-blocked-message` (nil unless read-only) — plus a stale override in
`actions/execution.cljs/staged-plan`. `execution-tab-model` re-exposes that as `:read-only?` and
also as part of `:arm-disabled?` / `:confirm-disabled?`. The two view files consume `:read-only?`:
`execution-strategy-band/mode-tile` sets `:disabled` + drops the click handler; `staged-band`
renders the notice and threads read-only into the tiles + the high-cost "rest passively" button;
`execution-order-table/editable?` = `(and staged/armed (not read-only?))` gates click-to-open on the
per-order editor. The simulation *actions* themselves
(`set-…-default-order-type`, `set-…-row-order-type`, `set-…-row-param`, `toggle-…-row`) write only
to execution-modal state and never check the flag — they were simply unreachable from the disabled
view.

## Design (single milestone)

Split the conflated flag in the **view-model** and stop the simulation controls from reading the
commit gate. `build-execution-plan` and the action gates are untouched.

1. **View-model** `execution-tab-model`:
   - Rename emitted `:read-only?` → `:commit-blocked?` (same value). Semantics: "cannot Arm/Confirm/send."
   - Add `:commit-blocked-reason` = `(:disabled-reason plan)` (`:read-only` | `:stale-recommendation`).
   - Add `:commit-blocked-message`: for `:read-only`, the existing message + `simulation-allowed-note`; for stale, the stale message verbatim (its only honest fix is a re-run).
   - Unchanged: `:arm-disabled?`, `:confirm-disabled?`, `:disabled-message`, `:stale?`/`:stale-message`, and the `stamp-amend-affordances` `writable?` guard (live-order amend stays gated on `(not execution-disabled?)`).

2. **Strategy band**: `mode-tile` always interactive (band only renders pre-commit); `staged-band` reads `:commit-blocked?` / `:commit-blocked-message` (notice still shown); high-cost "rest passively" button always rendered.

3. **Order table**: `editable?` → `(contains? #{:staged :armed} phase)` (drops `(not read-only?)`).

Defense in depth kept: Arm/Confirm buttons disabled via `:arm-disabled?`/`:confirm-disabled?`; the
`confirm-/amend-/revert-/resume-` actions still refuse a disabled plan; the plan flag is unchanged.

## Validation

- `npm run setup:worktree`, then `npm run gates` (`npm run check`, `npm test`, `npm run test:websocket`).
- New tests: view-model decoupling (read-only vs stale message), strategy-band tiles interactive + Arm disabled under a read-only plan, order-table rows clickable under a read-only plan. Kept: `build-execution-plan-keeps-spectate-mode-read-only-test` (plan flag unchanged).
- Browser QA (Playwright first, then live under Spectate Mode on :8080): pick a strategy tile and confirm the Est. price cost / fees / all-in KPIs move; open a row and change its type/params; confirm Arm stays disabled with the explanatory notice; confirm no order can be sent.

## Progress

- 2026-07-07: Implemented the view-model split (`:commit-blocked?` / `:commit-blocked-reason` / `:commit-blocked-message`), both view surfaces (strategy band + order table), and tests. ExecPlan created.
- 2026-07-07: Gates green — `npm run check` (docs/lint/namespace-sizes/6 shadow-cljs compiles) exit 0; `npm test` 5240 tests / 28327 assertions, 0 failures; `npm run test:websocket` pass. New execution view-model tests were split into a dedicated `view-model.execution-commit-block-test` namespace to keep `view_model_test.cljs` under its size exception.
- 2026-07-07: Browser QA green — `tools/playwright/test/optimizer-execution-spectate.spec.mjs` passes against the worktree build: under an active spectate session the Market tile is interactive, clicking it re-projects the KPIs and flips `default-order-type`, the notice reads "…you can still model…", and Arm stays disabled with the plan still `execution-disabled?`. (Worktree `:8080` was held by the main checkout; served the worktree build on a `:8090` SPA-fallback server and ran Playwright with `PLAYWRIGHT_REUSE_EXISTING_SERVER=true`.)
- [ ] Land the branch (merge) and move this plan to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- The simulation actions were already side-effect-free and ungated; the block was purely a view-layer consequence of reusing one flag for two purposes.

## Decision Log

- Scope = all read-only viewing contexts (not Spectate-only): the clean decouple covers them uniformly, and narrowing to Spectate would need special-casing for identical read-only states. (Requester-confirmed.)
- Stale-recommendation plans also get editable sim controls (harmless — still just projection), but their notice keeps the re-run message, not the simulate-anyway copy.
- Global `mutations-blocked-message` left unchanged (shared across trade/funding surfaces); the simulate-anyway clarification is appended only in the optimizer execution view-model.

## Outcomes & Retrospective

- Delivered: read-only viewers (Spectate / trader-portfolio / unavailable subaccount) can now model execution strategy and per-order type/params on the Execution tab; the KPI strip re-projects live; Arm/Confirm/amend/send stay hard-blocked at both the view (`:arm-disabled?`/`:confirm-disabled?`) and action (`:execution-disabled?` gates) layers.
- The fix was almost entirely a view-layer decouple — the simulation actions were already pure modal-state writes and ungated; only the view's reuse of one flag for two purposes blocked them.
- Verified by 5 new deterministic CLJS tests (2 view-model, 2 strategy-band, 1 order-table) rendering the real views through the real view-model, plus a passing end-to-end Playwright spec under active spectate.
- Remaining: land the branch, then move this ExecPlan to `completed/`.
