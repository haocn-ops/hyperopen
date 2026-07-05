# Cancel stale resting optimizer orders before a new execution run

## Purpose

The portfolio optimizer's Execution tab can leave passive limit orders "resting" (open, unfilled) on the Hyperliquid book. Today, when the trader re-runs the optimizer and executes a NEW plan, those old resting orders are neither cancelled nor accounted for: the new plan's per-instrument deltas are computed from settled positions only (`src/hyperopen/portfolio/optimizer/domain/rebalance.cljs`, `rebalance-row`), and the effect adapter submits the new orders unconditionally (`src/hyperopen/runtime/effect_adapters/portfolio_optimizer/execution.cljs`, `execute-portfolio-optimizer-plan-effect`). If the old resting orders later fill, the account ends up over-allocated relative to the recommendation — a real-money correctness bug.

After this change, confirming a new execution run first cancels any still-open resting orders left by previous optimizer runs (and only those — never the trader's manually placed orders), and the staged surface tells the trader this will happen. If the cancellation request fails outright, the run halts with nothing submitted, because submitting on top of live stale orders is exactly the over-allocation bug this plan removes.

Durable context: direct user/maintainer request (2026-07-04 session) — "if we go back and re-optimize and submit new orders, it must cancel/account for the old resting orders."

## Design

A "carryover" is the set of resting orders created by previous optimizer execution runs that may still be open on the book. It is tracked at a dedicated state path `[:portfolio-optimizer :execution-resting-carryover]` (new `execution-resting-carryover-path` in `src/hyperopen/portfolio/optimizer/contracts/paths.cljs`, re-exported from `contracts.cljs`). A dedicated path is required because the existing `execution-path` map is wholly replaced on staging, discard, re-stage-smaller, and on every applied ledger, so anything stored inside it would be wiped exactly when we need it.

Lifecycle of a carryover entry `{:oid <exchange order id> :asset-id <wire asset index> :coin <token> :instrument-id <canonical id> :side <:buy|:sell> :quantity <lots> :attempt-id <ledger id>}`:

1. Recorded when a ledger is applied: `apply-execution-ledger` (in `src/hyperopen/portfolio/optimizer/application/execution_workflow.cljs`) merges every `:resting` row of the new ledger into the carryover (keyed by oid). The asset index is read from the row's frozen order request (`[:request :action :orders 0 :a]`), so cancellation never needs market-metadata resolution later.
2. Pruned when a ledger carries `:cancellations` with `:status :ok`: the attempted oids are removed from the carryover in the same `apply-execution-ledger` pass.
3. Filtered against the live book at read time: `live-resting-carryover` (new, in `application/execution_carryover.cljs`) drops entries whose oid is no longer in `[:orders :open-orders]` once that feed has hydrated (`[:orders :open-orders-hydrated?]` true). While the feed has not hydrated, all entries are kept (conservative: attempting to cancel an already-gone order is a tolerated per-status error; skipping a live one recreates the bug). The carryover functions and the small oid helpers (`norm-oid`, `order-oid`, `open-oids`, moved from `view_model/execution_reconcile.cljs`) live in a NEW namespace `src/hyperopen/portfolio/optimizer/application/execution_carryover.cljs` (the repo's namespace-size gate caps `application/execution.cljs`), and the reconcile overlay reuses them.

Flow changes:

- `confirm-portfolio-optimizer-execution` and `resume-portfolio-optimizer-execution` (in `src/hyperopen/portfolio/optimizer/actions/execution.cljs`) attach the live-filtered carryover to the plan as `:cancel-orders` before dispatching `[:effects/execute-portfolio-optimizer-plan plan]`. The carryover is read fresh at confirm time (not stashed on the modal), so entries that filled between staging and confirming are not cancelled. Revert and re-stage-smaller are unchanged.
- The effect adapter (`execute-portfolio-optimizer-plan-effect`) submits one batched Hyperliquid cancel action `{:type "cancel" :cancels [{:a <asset-idx> :o <oid>}]}` through the existing `submit-order!` plumbing BEFORE the order rows. Outcomes: top-level "ok" (per-status errors such as "order already canceled or filled" are tolerated) → proceed with the run and stamp `:cancellations {:status :ok :oids [...]}` on the ledger so `apply-execution-ledger` prunes them; top-level failure, thrown error, or any entry missing its asset-id/oid → every still-`:ready` row is marked `:failed` with a plain-language message and NO new orders are sent, `:cancellations {:status :failed ...}` recorded for the audit trail.
- The Execution tab surfaces the pending cancellation: `execution-tab-model` (in `src/hyperopen/portfolio/optimizer/application/view_model/execution.cljs`) exposes `:carryover-count`, and the staged/armed surface in `src/hyperopen/views/portfolio/optimize/execution_tab.cljs` renders a note ("N resting orders from your previous run are still on the book — they'll be cancelled before new orders are sent") with `data-role "portfolio-optimizer-execution-carryover-note"`.

Known limitation (recorded, accepted): the carryover lives in in-memory app state and the persisted scenario record's `:execution-ledger` is never rehydrated into runtime state, so a full page reload between "run 1 rests" and "run 2 confirms" loses the carryover. Closing that requires tagging optimizer orders with a client order id (cloid) so they can be recognized from the live open-orders feed alone — out of scope here; tracked as a follow-up in the tech-debt tracker sense within this plan's Outcomes section.

## Milestones

Milestone 1 — pure layer. Add the carryover path, the entry/merge/filter/prune functions in `application/execution.cljs`, and the recording/pruning in `execution_workflow.cljs`'s `apply-execution-ledger`. Unit tests in `test/hyperopen/portfolio/optimizer/application/execution_test.cljs` and `execution_workflow_test.cljs` prove: a resting ledger row becomes a carryover entry with the asset index from its frozen request; a `:cancellations :ok` ledger prunes; live filtering drops off-book oids only after hydration.

Milestone 2 — actions + effect. Attach `:cancel-orders` at confirm/resume; implement the cancel-first submission and halt-on-failure in the effect adapter. Tests in `test/hyperopen/portfolio/optimizer/execution_actions_test.cljs` (confirm attaches the live-filtered carryover) and `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_execution_test.cljs` (cancel action submitted before order rows; failed cancel halts the run with zero orders sent; ok-with-per-status-errors proceeds).

Milestone 3 — surface + validation. View-model `:carryover-count`, staged-band note in the view. Run the required gates from the repo root: `npm run setup:worktree`, then `npm run gates` (runs `npm run check`, `npm test`, `npm run test:websocket` with a PASS/FAIL matrix). Acceptance: all gates pass; new tests fail before the implementation and pass after; the note renders only when a live carryover exists.

## Progress

- [x] Milestone 1: carryover path + pure functions + ledger recording/pruning + tests
- [x] Milestone 2: confirm/resume attach cancels; effect adapter cancel-first + halt-on-failure + tests
- [x] Milestone 3: view-model + staged note + gates green

## Surprises & Discoveries

- `execution-path` is replaced wholesale in four places (staging, discard, restage-smaller, apply-execution-ledger), which is why the carryover cannot live inside it.
- Persisted scenario `:execution-ledger` is write-only today (never rehydrated), so reload-survival requires cloid tagging — deferred.

## Decision Log

- Cancel-first (not "net the deltas against open orders"): cancelling restores the invariant that deltas-vs-positions are correct, keeps the sizing math untouched, and avoids partially-filled-open-order accounting. The staged plan is already computed against positions, so cancelling stale orders makes it exact.
- Halt the run when the batched cancel fails at the transport/top level: submitting anyway would recreate the over-allocation bug. Per-oid status errors are tolerated because they mean the order is already off the book.
- Carryover is read fresh at confirm time rather than stashed on the modal at staging, so orders that fill in between are not needlessly cancelled and the displayed count stays live.

## Outcomes & Retrospective

Landed as planned across the three milestones; all 34 gates pass via `npm run gates`. Tests live in `test/hyperopen/portfolio/optimizer/application/execution_carryover_test.cljs`, `test/hyperopen/portfolio/optimizer/execution_carryover_actions_test.cljs`, `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_execution_cancel_test.cljs`, plus an `apply-execution-ledger` case in `execution_workflow_test.cljs`. Net complexity: a modest increase (one new state path, one lifecycle concept) in exchange for removing a real-money over-allocation bug; the pure-layer placement keeps every decision unit-testable. Follow-up (not done here): tag optimizer orders with a cloid so carryover survives a full page reload by recognizing optimizer orders directly in the live open-orders feed.
