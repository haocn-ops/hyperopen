# Execution tab: stage sell-to-zero for held assets excluded from the optimization

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. Maintain this document in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

When a trader removes a currently-held asset from the optimizer universe and re-runs,
the allocator expresses no target for it. Since 2026-07-12 (the excluded-holdings
sell-to-zero fix) a missing target correctly means HOLD, never a silent sell — so the
Execution tab shows those rows as `skipped · excluded from the optimization — held, not
sold`. That fix was right for accidentally-excluded holdings (out-of-universe spot,
dropped history), but it left no clean flow for the *intentional* case: "I removed ENS
and WLD from the universe because I want out of them." Today the only explicit-exit
path is the draft blocklist + re-run, which is undiscoverable from the execution
surface and forces a full re-solve for a decision the optimizer has no opinion about.

This plan adds an execution-time affordance: each skipped excluded-holding row gets a
"Sell instead" control (plus a sell-all bulk control). Marking a row re-stages the
execution plan with that instrument treated as an explicit exit — target weight 0, a
real sell order with quantity, notional, cost estimate, and margin impact folded into
the plan summary. The choice is per-staging (modal-scoped), reversible before arming,
and never touches the run inputs, so it cannot trip the stale-recommendation gate.

## Context References

- Direct user request (2026-07-15): removed no-longer-wanted long/short positions from
  the universe; execution excluded them ("held, not sold") though the intent was to go
  to zero on them as part of the rebalance; asked for a way to sell them from the
  optimizer/execution screen.
- `/hyperopen/docs/exec-plans/completed/*` 2026-07-12 excluded-holdings fix: nil target
  = HOLD; blocklist = explicit exit (target 0). This plan builds the third leg:
  execution-scoped explicit exit.
- Key seams: `application/rebalance_preview.cljs` (`target-weight-for` unions blocklist
  with the new exit set), `actions/execution.cljs` (`staged-plan` rebuild),
  `views/portfolio/optimize/execution_order_table.cljs` (skipped section).

## Design decisions

- Exit marks live in the execution modal state (`:exit-instrument-ids`, a set), beside
  `:overrides` / `:params` / `:overlap-cancels`. They reset on tab entry and discard —
  every staging starts from the run's honest plan and sells stay an explicit, fresh
  decision per staging session.
- Exits are threaded as an opts argument into the derived-preview build and unioned
  with the draft blocklist (identical semantics: explicit user exit ⇒ target 0). They
  never enter the request, the input-signature, or the persisted run, so staleness and
  scenario persistence are untouched, and the Recommendation tab's preview stays the
  allocator's pure output.
- Toggling an exit rebuilds the staged plan from the stored run request (which carries
  any snapshot cost contexts already fetched) and resets the phase to `:staged` — an
  armed plan whose orders just changed must be re-reviewed.
- Cost honesty: a newly exit-marked row prices from an existing cost context when its
  coin already has one, else the flat fallback (25 bp) — rendered with the existing
  cost-source provenance ("Fallback bps"). Extending the slippage-snapshot refresh to
  cover exit-marked rows is a deliberate follow-up, not in scope.
- The affordance is available pre-run (`:staged`/`:armed` phases) including read-only
  spectate (it is a state-only simulation edit, same policy as order-type toggles);
  arm/confirm gates are unchanged.

## Milestones

### Milestone A — preview derivation accepts explicit exits
`rebalance_preview.cljs`: `build-derived-preview` gains an opts arg
(`{:exit-instrument-ids #{...}}`) folded into the blocklist set;
`result-with-refreshed-rebalance-preview` gains a 3-arity passing opts through.
Acceptance: unit test mirroring the blocklist test — a held instrument absent from
targets becomes a ready sell-to-zero row when listed in exits, stays `:excluded`
otherwise.

### Milestone B — action + modal state + contract surface
`defaults.cljs` modal state gains `:exit-instrument-ids #{}`.
`actions/execution.cljs` adds `set-portfolio-optimizer-execution-exit
[state instrument-ids exit?]`: updates the set, rebuilds the staged plan with exits
(re-deriving the preview from the stored run request), resets phase to `:staged`,
clears the error; no-ops while submitting or with no staged plan or after a terminal
run. Registration: `schema/contracts/action_args.cljs`,
`schema/runtime_registration/portfolio.cljs`, `runtime/action_adapters.cljs`,
`portfolio/optimizer/actions.cljs`, `portfolio/optimizer/runtime_catalog.cljs`.
Save-only action ⇒ no effect-order/Lean surface.
Acceptance: action tests — toggle stages a ready sell row and bumps ready-count;
un-toggle restores the skipped row; guards hold.

### Milestone C — UI affordances
`view_model/execution.cljs` stamps `:exit? true` (instrument in the exit set) and
`:exitable? true` (skipped `:excluded-from-optimization` row, pre-run editable) onto
display rows. `execution_order_table.cljs`: skipped section renders a per-row
"Sell instead" button and a bulk "Sell all N" control when more than one row is
exitable; an exit-marked row (now in the order list) shows an "exit" chip and its
expanded editor offers "↺ keep holding" to revert. Skip-reason copy for exitable rows
points at the affordance.
Acceptance: view/view-model tests for the stamps and buttons; the numbers in the
summary strip (orders to send, sells notional, margin after) move when an exit is
toggled.

### Milestone D — validation + browser QA
Gates + a Playwright pass over the worktree build driving the full flow: run with a
held asset excluded → Execution tab → Sell instead → row becomes a sell order →
revert → row returns to skipped.

### Milestone E — persisted auto-exit preference (user follow-up, same day)
Owner request: closing removed positions should be the DEFAULT, not per-row opt-in,
with a browser-persisted setting to opt out; spot (an excluded asset class) is never
auto-closed; direction handled both ways (sell a long, buy back a short — the
target-0 path already does this).

- `trading-settings`: `:optimizer-auto-exit-excluded?` (default TRUE; `(not (false? …))`
  normalization so pre-feature stored blobs restore as ON). Persisted via the existing
  localStorage settings pattern (`:effects/save [:trading-settings]` +
  `:effects/local-storage-set-json`).
- Entry seeding: `open-portfolio-optimizer-execution` computes auto-exit candidates
  from the base plan — skipped `:excluded-from-optimization` PERP rows whose
  instrument-id is NOT in the stored request's `:requested-universe` — and, when the
  preference is on, seeds the modal exit set and re-derives the plan with them.
  Exempt (held-by-default, still manually closable per row): spot holdings, and
  perps the trader REQUESTED but the engine dropped (missing history / calendar
  exclusion) — auto-closing those would trade against intent on a data failure.
- New action `set-portfolio-optimizer-execution-auto-exit [enabled?]`
  (::common/boolean-args): persists the preference and, when a pre-run plan is
  staged, re-seeds it (ON recomputes candidates — clearing manual per-row tweaks,
  the preference is source-of-truth when toggled; OFF reverts every close to a hold);
  phase drops to :staged.
- UI: a persistent setting strip on the order-table surface ("Close perp positions
  removed from the allocation (saved preference)") rendered whenever the decision
  applies pre-run — including when every candidate is already auto-staged, so the
  opt-out is discoverable where its effect shows. Copy made direction-neutral
  ("Close instead" / "Close all N" / "buy back a short").
- Placement decision: Execution tab only (user offered setup page as alternative) —
  the toggle sits where its consequence (the order list) is visible, and the entry
  seeding makes it effective regardless of where the run was configured.

## Validation

- [x] `npm run check` — PASS (via `npm run gates`, 34/34, 2026-07-15)
- [x] `npm test` — PASS (5710 tests / 31516 assertions incl. the new preview, action,
      and view coverage)
- [x] `npm run test:websocket` — PASS
- [x] Browser QA: worktree build served on :8090 (SPA-fallback), throwaway Playwright
      spec drove the full flow — skipped section advertises "N can be sold instead",
      "Sell instead" staged a −$200 ENS sell (exit chip, KPI Sells/order-count
      recompute), second held-out asset staged, revert returned the row to a skipped
      hold. Spec deleted after the run; screenshots delivered in-session.
- [x] Milestone E gates: `npm run gates` 34/34 PASS (6436 tests / 34932 assertions,
      2026-07-15) including the new settings, entry-seeding, toggle-action, and
      strip view tests.
- [x] Milestone E browser QA: entry with default-on preference pre-staged both
      removed perps as closes ("Arm 3 orders", EXIT chips, spot holding stayed a
      skipped hold); unchecking the strip reverted them ("1 to send") and persisted
      `optimizer-auto-exit-excluded? false` to localStorage; re-checking re-staged
      and persisted true. Spec deleted after the run.

## Progress

- [x] Plan written; seams confirmed against current code (staged-plan, target-weight-for, skipped-section).
- [x] Milestone A — preview derivation accepts explicit exits (opts arg, unioned into blocklist).
- [x] Milestone B — `set-portfolio-optimizer-execution-exit` action + modal `:exit-instrument-ids`
      + full contract-surface registration (args spec, binding row, catalog, re-export).
- [x] Milestone C — view-model `:exit?`/`:exitable?` stamps; skipped-section Sell instead +
      Sell all N; exit chip + revert-to-hold in the row editor.
- [x] Milestone D — gates + browser QA (see Validation).
- [x] Milestone E — persisted default-on auto-exit preference (setting, entry
      seeding with spot/engine-dropped exemptions, toggle action + surface strip,
      direction-neutral copy).
- [ ] Follow-up (deferred): extend the slippage-snapshot refresh to cover exit-marked
      rows so a freshly staged exit prices from a live book instead of the flat
      fallback (25 bp, honestly labeled "Fallback bps" today).

## Surprises & Discoveries

- The toggle-off path cannot rely on `last-successful-run-with-rebalance-preview`
  filling a missing preview from the live readiness request (a minimal draft yields a
  nil derived preview) — `staged-plan` re-derives from the STORED run request whenever
  an exit set is provided, even an empty one.
- Forcing the skipped `<details>` `:open` when exitable rows exist would fight the
  user's collapse on every re-render (Replicant re-asserts attributes); the summary
  line advertises the affordance ("N can be sold instead") instead. The keyed details
  correctly stays open across a plan restage (verified in browser QA).
- Save-only actions need no effect-order/Lean surface — only heavy-effect actions in
  `effect-order-policy-required-action-ids` do.

## Decision Log

- Exit marks are modal-scoped and reset on execution-tab entry (safety over
  convenience: staged sells must be re-chosen per staging session).
- Union-with-blocklist implementation keeps one code path for "explicit exit ⇒
  target 0" instead of a parallel mechanism.
- Affordance stays available in read-only/spectate (state-only simulation edit, same
  policy as order-type toggles); arm/confirm gates unchanged. Hidden once a run
  attempt leaves `:idle` (the plan already executed).

## Outcomes & Retrospective

- Shipped 2026-07-15 on branch feature/optimize-rebalancing-sales-b000ea (worktree,
  uncommitted at session end). Changed: rebalance_preview (exits opts),
  actions/execution (toggle + staged-plan re-derivation), defaults (modal key),
  view_model/execution (stamps), execution_order_table (affordances), 4 contract
  registration files, 3 test files (one new), 3 ns-size exception bumps.
- The 2026-07-12 excluded-holdings HOLD contract is untouched: nil target still never
  silently sells; only an explicit user mark (blocklist at run time, exit toggle at
  execution time) stages a sell-to-zero.
