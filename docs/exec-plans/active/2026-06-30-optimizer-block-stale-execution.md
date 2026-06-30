# Optimizer execution: block a stale recommendation from arming / sending live orders

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document must be maintained in accordance with `docs/PLANS.md` (and its detailed contract `/.agents/PLANS.md`).

## Purpose / Big Picture

An external review of this work tree flagged that the Execution surface stages a confirmable plan from `last-successful-run` and lets the trader **arm and commit live Hyperliquid orders even when the recommendation is stale** — i.e. the draft inputs (universe, constraints, objective, return/risk model) have changed since the solve, so the orders no longer reflect what the optimizer would now produce. The staleness predicate already exists (`run-identity/stale-run?`, `current-solved-run?`) and is already surfaced read-only on the Recommendation tab (`:stale?` banner), but it is **not enforced anywhere in the execution action gates**. This is the highest-value, smallest of the five reviewed safety items: a real money-loss vector with the machinery already in place.

The fix wires the existing staleness predicate into the two execution boundaries that can release live orders from a stale plan — **Arm** (`set-portfolio-optimizer-execution-phase :armed`) and **Confirm** (`confirm-portfolio-optimizer-execution`) — and makes the block legible on the surface (disabled Arm + a "re-run first" notice) so it is not a silently dead button.

Scope is deliberately narrow: the "inputs changed since the solve" staleness (`stale-run?`). The separate "plan snapshot drifted vs a newer run" case is out of scope (the entry action re-stages on every Execution-tab entry, so it does not arise in the normal flow).

## Context References

- Origin: a **direct maintainer request** — an external code review of this work tree, relayed by the maintainer, raised five execution-safety items; this ExecPlan implements **item #1** (block stale recommendations from staging live execution). It is a sibling of the **parent ExecPlan** `docs/exec-plans/active/2026-06-29-optimizer-execution-margin-to-account-leverage.md` (same execution-hardening pass / same review).
- `src/hyperopen/portfolio/optimizer/application/run_identity.cljs` — `stale-run?`, `current-solved-run?`, `solved-run?` (existing, unused by execution).
- `src/hyperopen/portfolio/optimizer/actions/run.cljs` — precedent: private `current-solved-run?` / `stale-solved-run?` build the staleness ctx in the action layer.
- `src/hyperopen/portfolio/optimizer/actions/run.cljs` `confirm-portfolio-optimizer-scenario-save` — precedent: a write action gated on `(not (current-solved-run? state))` with a "Rerun this scenario before saving." message.
- `src/hyperopen/portfolio/optimizer/actions/execution.cljs` — the two gates to harden (`set-portfolio-optimizer-execution-phase`, `confirm-portfolio-optimizer-execution`).
- `src/hyperopen/portfolio/optimizer/application/view_model/workspace.cljs` `scenario-stale?` — reusable staleness for the view-model.
- `src/hyperopen/portfolio/optimizer/application/view_model/execution.cljs` — `execution-tab-model`.
- `src/hyperopen/views/portfolio/optimize/execution_tab.cljs` — Arm button + staged band.
- Memory: `optimizer-flow-simplification-execplan` (the review backlog this item belongs to).

## Progress

- [x] (2026-06-30) Located the gap: `confirm` has a full trading-readiness gate but no staleness gate; `set-phase :armed` is a blind toggle; `open-portfolio-optimizer-execution` stages from `last-successful-run` regardless of draft dirtiness. `stale-run?` / `current-solved-run?` are computed in the view-model (`:stale?`) but never consulted by the execution actions. Confirmed the `confirm-portfolio-optimizer-scenario-save` precedent for the gate shape.
- [x] (2026-06-30) M1 — Action gate: added `stale-recommendation?` (+ private `optimizer-running?`) and `stale-recommendation-message` to `actions/execution.cljs`. It short-circuits on `run-identity/solved-run?` (so plan-only test states with no retained run never build readiness or trip the gate). `confirm-portfolio-optimizer-execution` gained a new `cond` branch (after `:execution-disabled?`, before the ready-count / agent-readiness gates) that refuses to submit a stale plan; `set-portfolio-optimizer-execution-phase` now takes `state` and refuses an arm by holding `:staged` and setting the re-run error.
- [x] (2026-06-30) M2 — Surface: `execution-tab-model` now reuses `workspace/scenario-stale?` to emit `:stale?`, `:stale-message`, `:arm-disabled?`, `:arm-disabled-message` (and folds `stale?` into `confirm-disabled?`). `execution_tab.cljs` header reads the combined `arm-disabled?`/`arm-disabled-message` for the Arm button; the staged band renders a `portfolio-optimizer-execution-stale` notice. `execution_tab.cljs` 740→746 (budget 760).
- [x] (2026-06-30) M3 — Tests: `execution_actions_test` gained `confirm-execution-blocks-stale-recommendation-test` (only the re-run error emitted; no `execute`/`unlock`), `arm-execution-blocked-when-stale-test`, and `confirm-execution-current-solved-run-still-submits-test` (a `completed-run?` fixture proves the gate doesn't block a current run). `execution_tab_test` gained `execution-tab-stale-recommendation-disables-arm-test`; its shared `scenario-view` fixture now carries a matching run-state signature so the default fixture is a genuinely *current* run (was incidentally flagged stale by the predicate because its minimal draft yields a nil readiness signature). Added a `dev/namespace_size_exceptions.edn` entry for `execution_actions_test.cljs` (514 lines, max 560, retire 2026-09-30), matching the optimizer test-suite convention.
- [x] (2026-06-30) Validation: `npm run gates` 34/34 PASS (5601 tests / 30263 assertions; all five ClojureScript builds compile; lints incl. namespace-sizes + docs green). Optimizer Playwright smoke green — all 14 `@smoke` cases passed at `--workers=1` against the worktree's own dev build (the local `npm run dev` bound :8081 as the active fallback; ran with `PLAYWRIGHT_BASE_URL=http://127.0.0.1:8081`). Dev server + watchers torn down after QA (ports free).
- [x] (2026-06-30) M4 — Entry gate (maintainer follow-up: "today only `solved?` gates the execution entry; wire `current-result?` there"): `staged-plan` (called by `open-portfolio-optimizer-execution`) now gates on currency, not just `solved?`. After `build-execution-plan`, a stale-but-solved run stages a plan flagged `:execution-disabled? true` / `:disabled-reason :stale-recommendation` / `:disabled-message` (rows kept for context; a read-only/spectate block still wins). This reuses the existing disabled machinery: the view's read-only banner shows the re-run message, and `confirm` already blocks on `:execution-disabled? plan`. The view-model suppresses the *separate* stale notice when the plan is already disabled (`stale-notice? = (and scenario-stale? (not execution-disabled?))`) to avoid a duplicate banner, while `arm-disabled?`/`confirm-disabled?` keep the live `scenario-stale?` check for post-staging drift. Tests: `open-execution-stages-disabled-plan-when-recommendation-stale-test`; the two existing `open-execution-*` fixtures gained a matching run-state signature (shared `current-run-signature`/`current-run-state`) so a *current* run stays executable (they were stale-by-predicate only because their minimal drafts yield a nil readiness signature). `execution_actions_test.cljs` 554/560.
- [x] (2026-06-30) Re-validation after the entry gate: `npm test` 4897 tests / 26923 assertions, 0 failures; `lint:namespace-sizes` green; `:portfolio` build clean. Adversarial verification workflow (5 path-probes + completeness critic) run to confirm no path reaches `:effects/execute-portfolio-optimizer-plan` from a stale recommendation.
- [ ] Land: commit on the feature branch and (on maintainer review) merge to local `main`, then move this plan to `completed/`.

## Surprises & Discoveries

- (2026-06-30) The cheapest robust "current run still submits" fixture uses `completed-run?` (a `run-state` with `:status :succeeded` + a `:request-signature` whose `:scenario-id`/`:input-signature` match the retained run), because that path makes `stale-run?` false without needing `setup-readiness/build-readiness` to reproduce a matching input-signature from a full draft.
- (2026-06-30) The canonical `stale-run?` treats "can't compute a current signature" the same as "inputs changed" — so the execution view test's minimal `scenario-view` fixture (draft `{:id …}` with no universe ⇒ nil readiness signature ⇒ `matching-request?` false) was *already* stale-by-predicate. Before this change nothing consumed `:stale?` on the execution tab, so it was invisible; wiring the Arm button to it surfaced the under-specified fixture. Fixed by giving the shared fixture a matching run-state signature (a faithful "current solved run", which is what a staged execution plan always implies in the real app) rather than weakening the predicate. The real app never hits the nil-signature path with a staged plan, because staging requires a complete, runnable draft. (Same fix applied to the two `open-execution-*` action fixtures once the entry gate landed.)
- (2026-06-30) **Adversarial verification (6-agent workflow: 5 path-probes + completeness critic).** Verdicts: ENTRY, ARM+CONFIRM, PREDICATE, and VIEW paths are all **sound** — no fresh-staging path derives orders from current inputs without the staleness gate refusing it. Two probes + the critic flagged **Resume/Revert** (`resume-portfolio-optimizer-execution`, `revert-portfolio-optimizer-execution-filled`) as a residual gap: they dispatch `:effects/execute-portfolio-optimizer-plan` and check only `:execution-disabled? plan` (set at entry, not recomputed), so an action-layer bypass (devtools / direct dispatch) could send live orders while the recommendation is stale. Resolution / scope: (a) these replay the **original committed intents from the ledger**, not fresh stale-derived orders (the dedicated recovery probe confirmed they never rebuild from draft/readiness), so this is *not* a fresh-staging hole; (b) the **view already disables Resume** when stale (`confirm-disabled?` includes `scenario-stale?`); (c) **Revert must stay available even when stale** — unwinding a partial fill is always safe, and gating it would strand a half-executed position. This action-layer enforcement is review **item #2** ("reuse the gate for Resume/Revert"), not #1 ("block stale *staging*"). Recorded here so #2 picks it up with the revert-stays-available nuance.

## Decision Log

- (2026-06-30) Gate at **both** Arm and Confirm (defense in depth): Arm disabling is the discoverable UX; Confirm is the hard backstop if state goes stale between arming and committing.
- (2026-06-30) Keep the helper **local to `actions/execution.cljs`** (it already requires `setup-readiness`) rather than promoting to `actions/common.cljs`, to avoid widening `common`'s require surface for a two-call helper; `run.cljs` keeps its own copy by existing convention.
- (2026-06-30) `stale-recommendation?` checks `solved-run?` first and only builds readiness when a solved run exists, so the existing plan-only confirm/arm tests (no `last-successful-run`) are byte-for-byte unaffected.

## Outcomes & Retrospective

- _Pending implementation._

## Plan of Work

### Milestone 1 — Action gate (confirm + arm)
Add `optimizer-running?` + `stale-recommendation?` privates and the `stale-recommendation-message`. New `confirm` branch (after `execution-disabled?`, before the ready/agent gates). `set-phase` takes `state`, refuses an arm when stale.

### Milestone 2 — Surface the block
`execution-tab-model` gains `:stale?`, `:arm-disabled?`, `:arm-disabled-message`. The Arm button reads them; the staged band shows a stale notice.

### Milestone 3 — Tests
Action gate tests (stale-blocks-confirm, stale-blocks-arm, current-run-still-submits/arms), view-model stale projection, view stale-notice render.

## Validation and Acceptance

- `npm run gates` 34/34 PASS.
- Optimizer Playwright smoke green at `--workers=1`.
- A stale staged plan cannot reach `:effects/execute-portfolio-optimizer-plan`; a current solved run is unaffected.

## Idempotence and Recovery

Pure action/view-model change; re-running gates is the recovery. No data migration, no contract surface change.

## Artifacts and Notes

- Belongs to the 5-item execution-safety review backlog (item #1).
