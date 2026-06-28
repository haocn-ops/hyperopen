# ExecPlan — Optimizer Execution: wire-valid order size + agent-lock unlock prompt

- **Status:** active
- **Owner:** Geronimo
- **Created:** 2026-06-28
- **Branch:** feature/sweet-newton-a2029f
- **Flow:** `$bug-flow` (diagnosis-first → ExecPlan → RED → smallest fix → review → gates)

## Purpose

Two independent defects in the optimizer **Execution** tab, both reported from one live run
(spectate account `0x4096…`, nREPL :50460):

1. **Every armed order fails** with Hyperliquid `{:status "err", :error "Failed to
   deserialize the JSON body into the target type"}`. 9/9 submitted rows failed; 0 filled.
2. **Arming dead-ends when trading is locked.** The run shows a raw error and never offers
   to unlock — unlike normal order entry, which prompts the passkey unlock and proceeds.

## Context References

Public refs:
- Direct maintainer request (this session): "I'm trying to submit these orders … and they're all failing … it wouldn't start because trading was locked … it should just immediately prompt me to unlock just like it does when I'm submitting any other type of order." Diagnosed live via the shadow-cljs nREPL (:50460, store atom `hyperopen.system/store`).

Repo artifacts:
- Parent ExecPlan: [2026-06-27 native-mark reference](2026-06-27-optimizer-execution-native-mark-reference.md) (same Execution tab; this defect surfaced while exercising live execution after that fix landed).
- Order builder: `src/hyperopen/api/gateway/orders/commands.cljs` (`build-standard-order-action` — `:p` canonical, `:s` raw); manual-path size canonicalization `src/hyperopen/domain/trading/market.cljs` (`base-size-string`).
- Lock pattern being mirrored: `src/hyperopen/order/effects.cljs` (`api-submit-order`, `dispatch-unlock-agent-trading!`), `src/hyperopen/runtime/effect_adapters/wallet.cljs` (`unlock-agent-trading`, `dispatch-after-success-actions!`).

Local scratch refs (non-authoritative): live capture of the 9 failing rows + `szDecimals` table (nREPL :50460).

## Root cause

### Issue 1 — wire `:s` size exceeds the asset's `szDecimals`

The order-action builder canonicalizes **price** but not **size**:

- `hyperopen.api.gateway.orders.commands/build-standard-order-action`
  ([commands.cljs:184](/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs)) sets
  `:p (canonical-price-text …)` (≤5 sig-figs / tick) but `:s (str size)` — the raw parsed
  number, with **no szDecimals flooring**.
- The shared builder's contract (and the Lean `order_request_contracts` spec) expects the
  **caller** to pass a clean size; the manual order path honours this by pre-rounding via
  `domain.trading/base-size-string` (truncate-to-szDecimals). The optimizer execution path
  does **not** — `order-form-for-row` feeds `:size (:quantity intent)`, a raw
  `|notional| / price` float
  ([execution.cljs:200](/hyperopen/src/hyperopen/portfolio/optimizer/application/execution.cljs)).
- The optimizer's own `round-down-quantity` is a no-op here: `size-decimals` reads
  `:szDecimals` off the optimizer-*universe* instrument, which carries none, and the floor is
  guarded by `(integer? decimals)` → returns the raw float unchanged.

Hyperliquid deserializes `s`/`p` into precision-bounded decimal types, so an over-precise
size fails at the serde layer with the generic "Failed to deserialize…" error (price is fine
because it is already canonical). **Live proof** (run on :50460, all 9 rows failed):

| coin | szDecimals | sent `:s` | valid `:s` |
|---|---|---|---|
| SOPH | 0 | `7968.855385980289` | `7968` |
| REZ | 0 | `24879.29985104574` | `24879` |
| ZETA | 1 | `1119.2330758196308` | `1119.2` |
| xyz:SILVER | 2 | `1.089665624397716` | `1.08` |
| xyz:BABA | 3 | `0.57497234024216` | `0.574` |

Both main-dex (`SOPH` `:a 197`) and HIP-3 (`xyz:*` `:a 110028`) rows fail identically →
**not** an asset-id encoding problem; the action shape is otherwise textbook-correct. Live
target has no vault (`exchange-vault nil`) and the agent was `:ready` at submit → not a
signature/lock issue. Simulator off → real Hyperliquid rejection.

### Issue 2 — execution arm/confirm has no agent-lock gate

`confirm-portfolio-optimizer-execution`
([actions/execution.cljs:97](/hyperopen/src/hyperopen/portfolio/optimizer/actions/execution.cljs))
gates only on `:execution-disabled?` (derived from `account-context/mutations-blocked-message`
= spectate / read-only) and `ready-count`. It **never reads `[:wallet :agent :status]`**, so a
`:locked` agent passes the guard, orders dispatch, and `agent-actions/missing-agent-session-
rejection` rejects each row → run halts on a raw error with no unlock affordance.

Normal entry (`order/effects.cljs` `api-submit-order`) handles the identical case: on
`:locked` it routes through `:actions/unlock-agent-trading` with `:after-success-actions` that
**replay** the submit once the passkey unlock resolves. That is the seam to reuse.

## Scope

- `src/hyperopen/portfolio/optimizer/application/execution.cljs` — canonicalize the order
  size to the catalog `:szDecimals` in `order-request-for-row` (where the catalog `market` is
  already resolved) before `build-order-request`; block as `:quantity-below-lot` if it floors
  to non-positive.
- `src/hyperopen/portfolio/optimizer/actions/execution.cljs` — gate
  `confirm-portfolio-optimizer-execution` on `[:wallet :agent :status]` so **only `:ready`
  submits**: `:locked` → prompt unlock + replay confirm; `:unlocking` → wait; every other
  non-ready status (the default `:not-ready`, plus `:approving`/`:error`) → open the
  enable-trading recovery modal — mirroring manual order entry (`api-submit-order`).
- `src/hyperopen/views/portfolio/optimize/format.cljs` — friendly copy for the
  `:quantity-below-lot` block reason ("Below one lot").
- Tests: `test/hyperopen/portfolio/optimizer/application/execution_test.cljs` (wire size),
  `test/hyperopen/portfolio/optimizer/execution_actions_test.cljs` (lock/enable gate).

**Out of scope / deliberately not done:**
- The shared builder `build-standard-order-action` is left unchanged — its formal contract is
  szDecimals-agnostic and the manual path already feeds clean sizes; fixing the optimizer
  (the raw-size origin) avoids destabilizing the Lean order-request surface. Committed formal
  vectors are unaffected either way (none carry a size exceeding its szDecimals).
- The **staged** QTY column still shows the raw pre-floor quantity (sub-cent / sub-lot delta
  vs the floored fill). Honest-display alignment at staging would require threading the
  catalog into `build-execution-plan`; noted as a follow-up, not required to fix "orders fail."

## Why this is safe

- The size fix is **idempotent** for already-clean sizes (manual path, existing fixtures:
  `0.25`@szDecimals4 → `"0.25"`). It only changes output when the raw size exceeds szDecimals,
  which is exactly the broken case. No shared-builder / formal-vector change.
- The lock gate makes **only `:ready` submit** (mirroring `api-submit-order`). `:locked`
  unlock-replay is loop/double-submit-safe — the unlock effect replays the queued confirm only
  once status flips to `:ready`, and the lock branch never touches `:submitting?` or the modal
  `:plan`, so a failed unlock can't replay and a successful one submits the intact plan exactly
  once. Two pre-existing confirm tests that omitted wallet state (relying on `nil ≠ submit`) were
  made realistic with `{:agent {:status :ready}}`.

## Progress

- [x] RED: wire-size tests (over-precise quantity → szDecimals-clean `:s`; sub-lot → blocked)
- [x] RED: lock-gate tests (`:locked` → unlock+replay; `:unlocking` → message; `:not-ready` →
  enable-recovery; `:ready` → submit)
- [x] GREEN: size canonicalization in `order-request-for-row`
- [x] GREEN: status gate (only `:ready` submits) in `confirm-portfolio-optimizer-execution`
- [x] Adversarial review (3-agent workflow) — escalated the non-ready-status gap to a real fix
- [x] Gates: `npm run gates` → 33/33 (5569 tests, 0 failures)

## Surprises & Discoveries

- **Main-dex rows fail identically to HIP-3 rows.** `SOPH` (`:a 197`, main perp) and `xyz:BABA`
  (`:a 110028`, HIP-3) both return the same deserialize error → ruled out asset-id encoding and
  pointed straight at a field common to all rows. The common field was the over-precise `:s`.
- **The agent was `:ready` at submit time** (live `[:wallet :agent :status] :ready`) — so the
  deserialize failure is genuinely a payload defect, *independent* of the lock dead-end. Issues
  1 and 2 are separate bugs that happened to surface in one run, not one root cause.
- **The optimizer already "rounds" — but the floor is a silent no-op.** `round-down-quantity`
  is guarded by `(integer? decimals)` and reads szDecimals off the *universe* instrument (which
  carries none), so it returns the raw float. The intended rounding never ran.
- **`npm run check`/`gates` does not run `formal:verify`**, and no committed order-request vector
  carries a size exceeding its szDecimals — so the fix is gate-safe at either layer; choosing the
  optimizer layer keeps the Lean order-request surface untouched.
- **An action cannot emit `[:actions/...]` — and the unit test didn't catch it.** The first lock
  fix had confirm return `[:actions/unlock-agent-trading {...}]`. At runtime (MetaMask locked →
  agent `:locked`), `wrap-action-handler`'s `assert-emitted-effects!` rejected it (`::effect-id`
  requires the `effects` namespace), aborting the whole dispatch → "nothing happened." The
  return-value unit test passed because validation only runs through the dispatch wrapper. Fix:
  emit the `:effects/unlock-agent-trading` EFFECT directly (inline what `unlock-agent-trading-
  action` emits); regression guard: tests now run emissions through `assert-emitted-effects!`.
  (`open-in-ticket` in the same ns has the identical latent bug — pre-existing, unreported.)

## Decision Log

- **Fix Issue 1 in the optimizer, not the shared builder.** The builder's `:s` contract is
  szDecimals-agnostic (`positive-numberish-string?`) and Lean-governed; the manual path
  satisfies it by pre-rounding. The optimizer is the lone caller passing raw floats, so it is
  the correct, contained, formal-surface-neutral place to fix.
- **Reuse `domain.trading/base-size-string`** (truncate-to-szDecimals + clean-string) — the
  exact helper the manual path uses — guaranteeing wire-consistent sizes.
- **Lock gate at the action level** (`confirm`), not a per-row effect catch: a row-level catch
  is too late (orders may have partially submitted). Replay `:actions/confirm-portfolio-
  optimizer-execution` itself as the after-success action (no new contract surface).

## Outcomes & Retrospective

- **RED → GREEN.** 5 new tests fail on the pre-fix tree for the exact reasons (actual `:s
  "7968.855385980289"`; locked agent returns `:effects/execute-portfolio-optimizer-plan`), then
  pass after the fix. Full gates: 32/33 then 33/33 (4864 cljs tests, 0 failures; websocket green).
- **Issue 1:** `order-request-for-row` now floors the row size to the catalog szDecimals via
  `base-size-string` before `build-order-request`, blocking sub-lot dust as `:quantity-below-lot`.
- **Issue 2:** `confirm-portfolio-optimizer-execution` now submits **only** when the agent is
  `:ready`; `:locked` prompts the passkey unlock and replays itself on success, `:unlocking`
  holds with a message, and every other non-ready status (the default `:not-ready`, plus
  `:approving`/`:error`) opens the enable-trading recovery modal — exactly like manual entry.
- **Review caught a real gap:** the adversarial verify upgraded the lock reviewer's "minor" to a
  confirmed **major** — the first cut only handled `:locked`/`:unlocking`, so a connected-but-
  never-enabled wallet (default `:not-ready`) still dead-ended. The complete fix above closes it.
- **Follow-up (not done):** align the *staged* QTY display with the floored submit size (threads
  the catalog into `build-execution-plan`); honest but cosmetic, sub-lot delta. Apply the same
  status gate to `resume`/`revert`/`restage` (lower-frequency recovery actions, not reported).

## Validation & acceptance

- Required gates: `npm run gates`.
- Acceptance: armed rows submit szDecimals-clean `:s`; a `:locked` agent triggers the passkey
  unlock and, on success, execution proceeds without re-arming.
