# Handle pre-existing open orders that overlap a rebalance (across a page reload)

## Purpose

The prior ExecPlan (`docs/exec-plans/completed/2026-07-04-optimizer-execution-cancel-stale-resting-orders.md`) made a NEW optimizer run cancel the resting orders a PREVIOUS run left on the book — but only within the same browser session, because it tracks those orders in in-memory state. After a full page reload the tracking is empty, so a fresh session that runs an optimization and executes will submit new orders on top of whatever orders are already resting on the book (from a previous session, or placed manually). If those old orders fill, the account over-allocates — the rebalance is not "properly executed."

After this change, a run started in ANY session (including immediately after a reload) recognizes and cleans up overlapping open orders before it releases new orders:

1. Every order the optimizer places is stamped with a recognizable client order id (a "cloid" — a 128-bit hex tag Hyperliquid stores with the order and echoes back on the open-orders feed). On execute, the run reads the live `frontendOpenOrders` feed, finds any order carrying the optimizer's cloid signature, and cancels those automatically — regardless of which session placed them. This is surgical: it never touches an order the user placed by hand.

2. Any remaining open order that overlaps the rebalance (same instrument as a row that will trade) but does NOT carry the optimizer signature — a manually placed order, or an optimizer order from before this feature shipped — is surfaced on the staged surface as a list the user resolves explicitly (cancel or keep each), because the app cannot prove those are safe to cancel automatically.

You can see it working by: placing a passive optimizer order so it rests, reloading the browser, re-opening the same scenario, and staging execution. The staged surface shows "1 resting order from a previous optimizer run will be cancelled" for the tagged order, and a separate "N open orders overlap this rebalance and weren't placed by the optimizer — choose which to cancel" list for any manual order on an overlapping instrument. Confirming cancels the tagged order automatically and cancels exactly the manual orders the user checked, all before any new order is sent.

Durable context: direct user request (2026-07-04 session), follow-up to the parent ExecPlan `docs/exec-plans/completed/2026-07-04-optimizer-execution-cancel-stale-resting-orders.md`; the user chose the "Both" option (auto-cancel the optimizer's own tagged orders AND surface remaining overlaps for an explicit decision).

## Background a novice needs

"cloid" = client order id. Hyperliquid lets the caller attach an arbitrary 16-byte value (`0x` + 32 hex chars) to an order via a `:c` field on the wire order. The exchange stores it and returns it on the `frontendOpenOrders` query. We use it purely as a recognizable fingerprint: every optimizer order gets a cloid whose first 4 bytes are a fixed magic tag (`0x0770c0de…`), so later we can pick our own orders out of the live book with no server-side state.

Order signing: `src/hyperopen/utils/hl_signing.cljs` (`compute-connection-id`) hashes `msgpack(action)` — it serializes the action map AS-IS, with no hardcoded field allowlist. So adding a `:c` key to the wire order array-map is automatically covered by the signature; the only requirement is field ORDER. Hyperliquid's canonical order struct is `a,b,p,s,r,t,c` (cloid last), and the builders use `array-map` (insertion-ordered), so `:c` must be assoc'd AFTER `:t`.

The open-orders feeds (verified): the flat `[:orders :open-orders]` path is overwritten by the websocket `openOrders` channel (`src/hyperopen/websocket/user_runtime/handlers.cljs`), whose rows do NOT carry cloid. The cloid-bearing source is `frontendOpenOrders`, fetched by `request-frontend-open-orders!` and stored at `[:orders :open-orders-snapshot]` (`src/hyperopen/api/projections/orders.cljs`, `apply-open-orders-success`). Recognition MUST read the snapshot, not the flat feed. A `frontendOpenOrders` row carries `:coin`, `:oid`, `:side`, `:sz`, `:limitPx`, and `:cloid` (when set).

Existing cancel plumbing to reuse: `src/hyperopen/api/trading/cancel_request.cljs` (`build-cancel-order-request` / `build-cancel-orders-request`) turns order rows into the `{:type "cancel" :cancels [{:a :o}]}` wire action; the optimizer execute effect already submits such a batched cancel before its order rows (from the completed same-session ExecPlan). This plan feeds MORE entries into that same cancel-first path.

## Design

New pure namespace `src/hyperopen/portfolio/optimizer/application/execution_cloid.cljs`:

- `OPTIMIZER-CLOID-PREFIX` = `"0x0770c0de"` (the magic tag; 4 bytes / 8 hex after `0x`).
- `optimizer-cloid?` — true when a normalized cloid string starts with the prefix.
- `make-cloid` — builds a full 34-char cloid from the prefix + a supplied 24-hex-char suffix (so the suffix source is injectable/testable; the effect passes crypto-random hex, tests pass a fixed suffix).
- `snapshot-open-orders` — reads `[:orders :open-orders-snapshot]` (the cloid-bearing feed), returning normalized rows `{:oid :coin :side :cloid :sz :limitPx}`.
- `classify-overlap` — given the snapshot rows and the plan's ready rows, returns `{:optimizer-owned [...] :untagged-overlap [...]}`: rows whose cloid is ours go to `:optimizer-owned` (auto-cancel); rows on a coin that a ready plan row trades but without our cloid go to `:untagged-overlap` (user decides); everything else is ignored. Coin matching reuses `execution/coin-for-row`.

Wire tagging (gateway): `build-standard-order-action` in `src/hyperopen/api/gateway/orders/commands.cljs` appends `:c (:cloid form)` to the single primary wire order when the form carries a non-blank `:cloid` (TP/SL legs are untagged; the optimizer places none). Purely additive — every existing caller omits `:cloid` and is unchanged.

Optimizer supplies the cloid: `order-request-for-row` / `build-execution-attempt` in `src/hyperopen/portfolio/optimizer/application/execution.cljs` accept an injected `:cloid-fn` (a 0-arg function returning a fresh cloid) and stamp `:cloid (cloid-fn)` onto each ready row's order form. The effect adapter passes a real generator (magic prefix + `crypto.getRandomValues` hex); tests pass a deterministic stub. Kept out of the pure plan build via injection so `build-execution-attempt` stays pure and testable.

Cross-session cleanup at confirm: `attach-carryover-cancels` in `src/hyperopen/portfolio/optimizer/actions/execution.cljs` is extended (or paired with a new `attach-overlap-cancels`) to also add the `:optimizer-owned` snapshot rows to the plan's `:cancel-orders` (deduped by oid against the in-memory carryover, so the same order is never cancelled twice). The `:untagged-overlap` rows are NOT auto-added; they flow to the view-model for the decision UI. A new action `set-portfolio-optimizer-execution-overlap-cancel` records the user's per-oid cancel/keep choices at `execution-modal-path :overlap-cancels`; confirm merges the checked ones into `:cancel-orders`.

The existing effect-adapter cancel-first path (`cancel-stale-orders!`) already halts the run if the batched cancel fails, so feeding it more entries needs no effect change beyond building wire cancels for feed-sourced rows (they carry `:oid` + `:coin`; the effect resolves the asset index via `build-cancel-order-request`, which reads market metadata from state — unlike carryover entries, which pre-freeze it. So the effect gains a small step: for entries lacking `:asset-id`, resolve it via `cancel-request/build-cancel-order-request` using state's market-by-key).

View-model + UI: `execution-tab-model` exposes `:overlap-orders` (the `:untagged-overlap` list with each row's current cancel/keep choice) and keeps the existing `:carryover-count` (now also counting `:optimizer-owned` snapshot rows). `src/hyperopen/views/portfolio/optimize/execution_tab.cljs` renders, on the staged/armed surface, the overlap decision list (`data-role "portfolio-optimizer-execution-overlap"`) with a checkbox per order and a summary; the confirm CTA reads "Cancel N & arm" when any are checked.

## Milestones

Milestone A — cloid identity (pure, no risk). New `execution_cloid.cljs` with prefix/recognizer/make-cloid/snapshot-read/classify-overlap. Unit tests in a new `execution_cloid_test.cljs`: recognizer matches only the magic prefix; classify splits owned vs untagged-overlap vs ignored by coin+cloid. What exists after: the ability to recognize optimizer orders and classify a live book against a plan, provable in tests, with nothing wired yet.

Milestone B — wire tagging + signing safety. Gateway appends `:c` after `:t`; optimizer stamps an injected cloid onto ready order forms. Tests: `build-standard-order-action` emits `:c` in the correct array-map position only when the form carries `:cloid` (and omits it otherwise); a round-trip through `compute-connection-id` still returns a `0x…` hash with the cloid present (signing doesn't throw and the field is included). What exists after: optimizer orders are recognizable on the exchange; every existing order path is byte-for-byte unchanged when no cloid is supplied.

Milestone C — cross-session recognition, auto-cancel, overlap classification wired. Confirm adds `:optimizer-owned` snapshot rows to `:cancel-orders` (deduped vs the in-memory carryover); the effect resolves asset indices for feed-sourced cancels and still halts on cancel failure; `:untagged-overlap` reaches the view-model. Tests in `execution_carryover_actions_test.cljs` (confirm attaches feed-recognized owned orders) and the effect cancel test (feed-sourced cancel resolves its asset id and is submitted before order rows). What exists after: after a reload, the optimizer's own resting orders are cancelled automatically before a new run.

Milestone D — decision surface for untagged overlaps. View-model `:overlap-orders`; the staged/armed overlap list with per-order cancel/keep; the `set-…-overlap-cancel` action; confirm merges checked oids. Covered by tests at the seams rather than through a heavy view-model fixture: `execution_cloid_test` proves the owned/untagged/ignored classification; `execution_overlap_actions_test` proves the action records/clears a choice and that confirm attaches a checked untagged oid (and does NOT attach an unselected one) to `:cancel-orders`. The view reads `:overlap-orders`/`:carryover-count` straight off that verified projection. What exists after: manual/untagged overlapping orders are shown and the user's explicit choices are honored — the full "Both" behavior.

## Validation

For every milestone: from the repo root run `npm run setup:worktree` then `npm run gates` (runs `npm run check`, `npm test`, `npm run test:websocket` as one PASS/FAIL matrix). New tests must fail before their milestone's implementation and pass after. Acceptance is the behavior described under Purpose: after a reload, staging shows the auto-cancel note for a cloid-tagged resting order and the decision list for an untagged overlapping order; confirming cancels the tagged order plus exactly the checked untagged ones before any new order is sent. Note the one limit I cannot clear from this environment: I cannot place a live Hyperliquid order, so the cloid signing is validated by the msgpack/round-trip unit test, not by an accepted testnet fill — a live testnet order is the final confirmation step for whoever ships this.

## Progress

- [x] Milestone A: execution_cloid.cljs (recognizer + classify) + tests
- [x] Milestone B: gateway `:c` tagging + optimizer cloid stamping + signing round-trip test
- [x] Milestone C: confirm auto-cancels feed-recognized owned orders; effect resolves feed asset ids; halt-on-failure preserved
- [x] Milestone D: overlap decision surface (view-model + view + action) + tests
- [x] `npm run gates` green (34/34); ExecPlan moved to completed

## Surprises & Discoveries

- Signing msgpacks the action as-is (no field allowlist), so cloid tagging needs only correct array-map field order (`:c` after `:t`) — verified in `hl_signing.cljs`.
- The flat `[:orders :open-orders]` WS feed strips cloid; only the `frontendOpenOrders` snapshot (`[:orders :open-orders-snapshot]`) carries it. Recognition must read the snapshot.
- `execution/coin-for-row` was latently nil-unsafe (crashed on a row with neither `:coin` nor `:instrument-id`); the overlap classification exercised it through minimal test fixtures. Fixed with a `when-let` guard.
- Registering a new action requires a matching entry in `schema/runtime_registration/portfolio.cljs` or the `schema/contracts.cljs` drift check throws at test-bundle load ("Action contract metadata drift detected") — the failure mode is every downstream test erroring, not a lint message.
- `market-by-key` is keyed by instrument id (`"perp:ZETA"`), not bare coin — a feed-sourced cancel entry resolves its asset index through `cancel-request`'s candidate-key chain, which handles that.

### Live-session validation addendum (2026-07-04, same day)

A supervised live mainnet test (user-driven browser + worktree Shadow nREPL inspection) confirmed the wire path and exposed three client-side read bugs, all fixed and regression-tested the same day:

- CONFIRMED: Hyperliquid accepts cloid-tagged orders — two tagged orders rested with `:c "0x0770c0de…"` echoed back on `frontendOpenOrders`. The signing risk noted in Validation is closed.
- BUG (fixed): the websocket `openOrders` channel stores the WHOLE payload map `{:dex "" :user 0x… :orders […]}` at `[:orders :open-orders]`, not a row vector. `open-oids` iterated MapEntries, produced an EMPTY set with `hydrated? true`, and `live-resting-carryover` silently dropped every carryover entry — the in-session cancel never fired in production despite green unit tests (whose fixtures used the vector shape). Fixed by a shape-aware `open-order-rows` reader in `execution_carryover.cljs`; regression test pins the payload-map shape.
- BUG (fixed): recognition read only `[:orders :open-orders-snapshot]`, which covers the DEFAULT dex; named-dex (HIP-3, e.g. `xyz:ORCL`) orders live under `open-orders-snapshot-by-dex` with BARE coins, and webData2 is a third partial view. `cloid/live-open-orders` now merges all four sources, namespacing per-dex coins.
- BUG (fixed): nothing ever refreshed the per-dex `frontendOpenOrders` snapshots after startup — the account surface-service comment documents that the generic stream "does not hydrate the named-DEX snapshot map". Staging now dispatches a new `:effects/refresh-portfolio-optimizer-open-orders` (reusing manual order entry's `refresh-account-surfaces-after-order-mutation!`), so the cloid-bearing rows are fresh by confirm time.

## Decision Log

- Recognize via a magic-prefixed cloid rather than server-side state, so recognition survives a reload and needs no persistence.
- Auto-cancel only cloid-tagged (optimizer-owned) orders; never auto-cancel untagged orders — a manual order the user placed must not vanish silently. Untagged overlaps get an explicit decision surface instead.
- Cancel (not "modify" or "net into deltas") is the primitive: a resting order is not a guaranteed fill, so netting its size into a new order risks under-allocation; cancelling restores the positions-vs-target invariant the plan already assumes.
- Reuse the existing cancel-first / halt-on-failure effect path from the completed same-session ExecPlan rather than adding a second cancellation mechanism.

## Outcomes & Retrospective

Landed across all four milestones; `npm run gates` 34/34 PASS (5,761 tests / 30,947 assertions). Optimizer orders are now cloid-tagged on the wire (`:c`, signed automatically because the action is msgpacked as-is); confirm auto-cancels cloid-recognized orders from the live snapshot (reload-proof) plus the in-session carryover, deduped by oid; untagged overlapping orders surface on a staged decision list (`portfolio-optimizer-execution-overlap`) and only user-checked ones are cancelled; the cancel-first / halt-on-failure effect path is shared by all three sources, with feed-sourced entries resolving their asset index via the shared cancel-request builder. Complexity: moderate increase (one new identity concept, one new decision surface) buying reload-safe correctness for real money. Remaining: a live testnet order to confirm the exchange accepts the tagged wire order end-to-end (cannot be exercised from this environment); optimizer orders placed BEFORE this feature carry no tag and will appear once in the decision list instead of auto-cancelling — a one-time migration gap that resolves as those orders fill or are cancelled.
