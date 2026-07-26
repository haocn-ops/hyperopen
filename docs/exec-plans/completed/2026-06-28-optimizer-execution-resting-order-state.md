# ExecPlan — Optimizer Execution: resting (open) orders mislabeled "filled"

- **Status:** active
- **Owner:** Geronimo
- **Created:** 2026-06-28
- **Branch:** feature/sweet-newton-a2029f
- **Flow:** `$bug-flow` (diagnosis-first → ExecPlan → RED → smallest fix → review → gates)

## Purpose

In the optimizer **Execution** tab, a live run of all-**Passive** (resting maker) limit orders
reports every order's STATE as **"filled"** and the run as **"11 / 11 filled · complete"** —
but the orders never filled. They are resting **open orders** on the book (confirmed in the
Hyperliquid Open Orders panel: 7 live limit orders). "Filled" is the wrong state; a resting
order should read as **open / working**, and the run summary must not claim fills that did not
happen.

## Context References

Public refs:
- Direct maintainer report (this session, with screenshots): "they were all passive type
  orders … for the state, it shows them all as filled, but the reality is that they were not
  all filled. In fact, they're … open orders … Filled is clearly the incorrect state … since
  they're working orders, we should be able to see [their] state from that screen."

Repo artifacts:
- Parent ExecPlan: [2026-06-28 submit-size + lock](../completed/2026-06-28-optimizer-execution-submit-size-and-lock.md)
  (same Execution tab; the size fix made orders submittable, which exposed this state defect —
  once orders are *accepted* the run shows their state, and resting acceptance reads "filled").
- Effect adapter (status assignment): `src/hyperopen/runtime/effect_adapters/portfolio_optimizer/execution.cljs` (`submit-execution-row!`).
- Status model: `src/hyperopen/portfolio/optimizer/application/execution.cljs` (`response-ok?`, `realized-fill`, `final-ledger-status`, recover/revert/restage).
- Phase derivation: `src/hyperopen/portfolio/optimizer/application/view_model/execution.cljs` (`derive-phase`, `terminal-run-statuses`).
- View: `src/hyperopen/views/portfolio/optimize/execution_tab.cljs` (state cell, counts, filters, bands, health rail).
- HL response shape mirrored from the manual path: `src/hyperopen/order/effects.cljs` (`submit-status-entries`).

## Root cause

The execution model conflates **"the exchange accepted the order"** with **"the order
filled."** When a row's order response is OK, `submit-execution-row!` unconditionally marks the
row `:submitted`, and `:submitted` is treated as *filled* everywhere downstream (state label,
fill counts, `final-ledger-status :executed`, `derive-phase :done`).

A passive/post-only limit order that does not cross returns a Hyperliquid **`{:resting {:oid …}}`**
per-order status entry (not `{:filled …}`). `response-ok?` passes it (no `:error`), so the row
is marked `:submitted` → rendered "filled", even though `realized-fill` correctly returns nil
(its own docstring already notes "a post-only order that only rests has no `:filled` entry").
There is no distinct *resting* state — so a live, unfilled open order is indistinguishable from
a true fill.

HL per-order status entries (at `[:response :data :statuses]`, already extracted by
`response-statuses`):
- `{:resting {:oid N}}` — accepted, **resting on the book, not filled**.
- `{:filled {:totalSz S :avgPx P :oid N}}` — (fully/partially) **filled** on placement.
- `{:error …}` — rejected.

## Scope

- **`application/execution.cljs`**
  - New `settled-row-status` classifier: OK response with a `:filled` entry → `:submitted`;
    with a `:resting` entry (no fill) → `:resting`; neither (legacy/empty "ok") → `:submitted`
    (preserves immediate-cross behavior + the `["success"]` string fixtures).
  - `final-ledger-status`: count resting; a run where ≥1 order is live but not every accepted
    order filled (and none failed/blocked) → new ledger status **`:resting`**; all-filled stays
    `:executed`; failures/blocks keep `:partially-executed`/`:failed`/`:blocked`.
  - `recoverable-row` (Resume): demote `:resting` → `:skipped :reason :already-resting` so a
    Resume never double-submits a live order (same guard as `:submitted`).
  - `build-restaged-plan` (Re-stage smaller): drop `:resting` rows too (live → don't duplicate).
  - `build-revert-plan` already filters `:submitted` only — resting rows are correctly *not*
    reversed (no fill to unwind); no change.
- **`runtime/effect_adapters/portfolio_optimizer/execution.cljs`**
  - `submit-execution-row!`: classify via `settled-row-status` instead of hardcoding `:submitted`.
  - `refresh-after-execution!`: refresh user data when any row is `:submitted` **or** `:resting`
    (a resting placement adds an open order to pull).
- **`view_model/execution.cljs`**
  - `terminal-run-statuses` includes `:resting`; `derive-phase` maps `:resting` → new display
    phase `:resting`.
- **`views/portfolio/optimize/execution_tab.cljs`** — honest, resting-aware presentation:
  - `state-glyph`/`row-display-state`/`state-cell`: `:resting` row → display "open" (`text-info`).
  - `row-visible?` "Working" filter includes `:resting` (resting = a working/live order).
  - Shared `fill-counts` so every surface agrees: counts split **filled** vs **resting**;
    header subtitle, running/halted bands, KPI "Orders filled" + sub, and Fill-progress no
    longer report resting orders as filled.
  - New `:resting` phase wired through `status-tag`, `subtitle`, `control-band`,
    `health-note`, fill-progress status label, and `latest-attempt-panel` (chip tone `:info`).
- **`views/portfolio/optimize/format.cljs`** — friendly label for `:already-resting`.
- Tests: `application/execution_test.cljs`, `application/view_model_test.cljs`,
  `runtime/effect_adapters/portfolio_optimizer_execution_test.cljs`.

**Out of scope / deliberately not done:**
- **Canceling** resting orders from the optimizer (no cancel wiring exists; resting orders are
  managed in the trader/Open-Orders surface). Revert covers only *filled* legs by design.
- **Polling resting → filled transitions.** The ledger is a point-in-time placement record;
  later fills are reflected by the existing post-run user-data refresh and the trader surfaces,
  not by mutating a historical ledger.
- **Marking a resting run as `:tracking`/`:executed` for the scenario.** A resting placement is
  not a position; scenario `:status` deliberately stays `:saved` (only `:last-execution-status`
  records `:resting`).

## Why this is safe

- The classifier is **purely additive**: only an explicit `{:resting …}` entry reroutes; every
  existing fixture (`["success"]` string, `{:filled …}` map) still maps to `:submitted`, so
  all-filled and immediate-cross runs are byte-for-byte unchanged (`:executed`/`:done`).
- The new `:resting` ledger status **cannot** corrupt the persisted scenario vocabulary:
  `append-execution-ledger` only adopts `#{:executed :partially-executed}` into scenario
  `:status`; `:resting` lands only in the unconstrained, non-rendered `:last-execution-status`.
- Resume/revert/restage become *stricter* about live orders (resting rows are skipped/dropped,
  never reversed), removing a latent double-submit, not adding one.

## Progress

- [x] RED: classifier — `{:resting …}` response → row `:resting`, not `:submitted`
- [x] RED: `final-ledger-status` — resting-only → `:resting`; mixed-with-fill → `:resting`; all-filled → `:executed`
- [x] RED: effect adapter — resting response → ledger row `:resting`, ledger `:resting`, user-data refresh fires
- [x] RED: `derive-phase` — `:resting` run status → `:resting` phase; resting run is terminal
- [x] RED: resume skips resting rows; restage drops resting rows
- [x] GREEN: implement classifier + status/phase/view/recovery changes
- [x] Adversarial review (5-dimension workflow + per-finding verify) — see Surprises
- [x] Review-driven tests: view render shows "open" not "filled"; revert excludes resting
- [x] Gates: `npm run gates` → 33/33

## Surprises & Discoveries

- **The whole downstream model treated `:submitted` as a synonym for "filled."** Not just the
  view: `final-ledger-status` (`:executed`), `recoverable-row` (resume), `reversing-row`
  (revert, docstring literally says "filled (:submitted)"), and `build-restaged-plan` all keyed
  off `:submitted`. A correct fix had to thread `:resting` through every one of them, not only the
  state-cell label.
- **`realized-fill` already knew about resting** — its docstring said "a post-only order that
  only rests has no `:filled` entry, so realized stays pending (nil)" — so the data to
  distinguish resting from filled was always in the response; the model just never used it.
- **A resting-only run was NOT terminal before this fix.** `final-ledger-status` returned
  `:no-op` for `submitted=0`, which is not in `terminal-run-statuses`, so once rows were
  reclassified to `:resting` the tab would have fallen back to the *staged* plan instead of
  the ledger. Adding `:resting` to both `final-ledger-status` and `terminal-run-statuses` was
  required, not optional.
- **Adversarial review converged on ONE theme (~10 findings): a `:resting` ledger status does
  not promote the scenario-level status.** Half the agents escalated it to "critical spec
  violation"; the careful verifiers (and direct check of `specs.cljs:421` +
  `scenario_records.cljs:106`) proved that a **false positive** — `append-execution-ledger`
  only adopts `#{:executed :partially-executed}` into scenario `:status`, so for a resting run
  `:status` stays `:saved` (spec-valid) and only the *unvalidated, unrendered*
  `:last-execution-status` records `:resting` (a field that has always carried arbitrary ledger
  statuses incl. `:no-op`/`:failed`). No spec violation, no broken render. Leaving the scenario
  `:status` unpromoted is the **deliberate, honest** choice (a resting order is not a fill or a
  position) — see Decision Log.
- **One genuinely separate, pre-existing finding (out of scope):** `realized-fill` computes
  `:slippage-usd` from the row's full `:delta-notional-usd` even on a **partial** fill (HL
  `:filled` with `totalSz` < order size), overstating realized $ slippage. This predates the
  change (untouched by it) and is a realized-cost accuracy issue, not a resting-state bug.
  Flagged as a follow-up.

## Decision Log

- **Introduce a distinct `:resting` row + run status rather than overloading `:submitted`.**
  The whole downstream model (counts, ledger status, phase, recovery) keys off `:submitted` ==
  filled; a separate state is the only honest representation and keeps "filled" meaning filled.
- **A resting run does NOT promote scenario-level status (`active-scenario-status` /
  scenario-record `:status`).** Considered and deliberately declined despite heavy review
  signal: promoting to `:executed` would re-introduce exactly the lie this bug fixes (claiming a
  fill), and a new `:resting` *scenario* status would ripple through the closed scenario-status
  vocabulary (`scenario-record-statuses` spec + the runtime validation at `specs.cljs:421` +
  the scenario index/detail badge + the `query_state` filter set + tracking gating) — a far
  larger, separately-scoped change than the reported execution-tab defect. The resting outcome
  is still recorded on the scenario record via `:last-execution-status :resting`. Surfacing that
  on the scenario card is a reasonable follow-up, not part of this fix.
- **Classify in the optimizer status layer reading the existing `response-statuses` entries**
  (not a new HL parser), mirroring the manual path's `[:response :data :statuses]` shape and
  `realized-fill`'s own `[:filled :avgPx]` access.
- **Dedicated `:resting` display phase, not a relabeled `:done`.** Keeps each `case phase` site
  honest; the resting band offers no "View tracking" CTA (a resting placement isn't trackable
  yet), unlike `:done`.

## Outcomes & Retrospective

- **RED → GREEN.** The first run failed 12 assertions for the exact bug (ledger `:executed`
  when nothing filled; row `:submitted` for a resting response; phase `:armed`/rows `:ready`
  instead of terminal `:resting`; resume keeping the resting row; restage keeping it). All pass
  after the fix; the unchanged cases (`:executed` all-filled, `:failed` pure-failures) stayed
  green throughout.
- **Fix shipped across 6 source files:** `application/execution.cljs` (`settled-row-status`
  classifier + resting-aware `final-ledger-status` + resume/restage), the effect adapter
  (classify + refresh-on-resting), `view_model/execution.cljs` (terminal set + `derive-phase`),
  `execution_tab.cljs` (resting display state "open", `fill-counts`, new `:resting` phase across
  every `case phase` site, Working filter), and friendly labels in `format.cljs`.
- **Adversarial review (5 dimensions × per-finding verify, 32 agents):** the one substantive
  theme — scenario-status non-promotion — was verified a *false positive* for spec/render
  safety and confirmed as the deliberate design above. Review surfaced two real coverage gaps,
  both now closed with tests (view render shows "open" not "filled"; `build-revert-plan`
  excludes resting). One pre-existing, out-of-scope finding (`realized-fill` partial-fill
  notional) flagged as a follow-up.
- **Gates:** `npm run gates` → 33/33 (5575 tests / 30185 assertions / 0 failures, websocket
  green); `execution_tab.cljs` and `view_model_test.cljs` size budgets bumped (990→1050,
  545→575) per the established EDN-exception convention for this view.
- **Follow-ups (not done):** (1) surface `:last-execution-status :resting` on the scenario
  card so a resting placement is visible scenario-side; (2) scale `realized-fill`'s
  `:slippage-usd` by the actual filled `totalSz` for partial fills (pre-existing); (3) the
  `:resting` cancel affordance (no cancel wiring today — resting orders are managed in the
  trade ticket).

## Validation & acceptance

- Required gates: `npm run gates`.
- Acceptance: a run of resting/passive orders shows each row's STATE as **open** (not "filled"),
  surfaces them under the **Working** filter, and the run summary reports **0 filled · N
  resting** (never "N filled") with a `resting` phase; an all-crossing run still reports
  `filled` / `complete` unchanged.
