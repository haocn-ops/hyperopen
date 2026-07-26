# Named DEX Perps Transfer Balance Fix

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `docs/PLANS.md` and `.agents/PLANS.md`.

## Purpose / Big Picture

A classic-account user with collateral on a named HIP-3 perps DEX (e.g. `xyz`) cannot see or move that USDC collateral in Hyperopen. The order form already reads `:perp-dex-clearinghouse` for available-to-trade, but the balances table and the funding transfer modal read only the **default** perps clearinghouse (`:webdata2 :clearinghouseState`). For a user whose default perps balance is `0` and whose `xyz` withdrawable is large, this produces a missing balance row and `MAX: 0.00 USDC` in the transfer modal — exactly the user report ("it's not picking up the xyz balance" / "i cant transfer my xyz balance to spot").

After this change:
- The balances table shows a `USDC (Perps)` row tagged with the named DEX (e.g. an `xyz` chip) whenever that DEX has a nonzero clearinghouse value.
- A row-level `Transfer` control opens a transfer modal pre-targeted at that DEX.
- `Perps -> Spot` max reads the named DEX withdrawable.
- The outgoing request for a named-DEX transfer is a Hyperliquid `sendAsset` (`sourceDex: "xyz"`, `destinationDex: "spot"`, full USDC token id, `destination` = self) and is **signed with the `sendAsset` signer**, while default perps <-> spot keeps `usdClassTransfer`.
- Unified / account-abstraction rows keep the existing disabled "Unified" behavior (one shared USDC balance, no per-DEX transfer).

## Context References

Durable references:

- Direct maintainer request on 2026-06-05 (this session) to review the prior diagnosis, produce an independent execution plan, correct it where the prior reviewer was mistaken, and implement.
- Prior diagnosis ExecPlan in a sibling worktree: `/Users/barry/.codex/worktrees/8bcd/hyperopen/docs/exec-plans/active/2026-06-05-named-dex-transfer-balances.md` (reviewed; corrected here — see Decision Log).
- Completed precedent: `docs/exec-plans/completed/2026-06-03-subaccount-unified-sendasset-routing.md` records the `sendAsset` action shape, the `sourceDex`/`destinationDex` semantics, and the full mainnet USDC token id `USDC:0x6d1e7cde53ba9467b783cb7c530ce054`.
- Hyperliquid exchange docs: `usdClassTransfer` is spot <-> default-perp USDC only; `sendAsset` is the generalized transfer carrying `sourceDex`/`destinationDex` (`""` = default perps, `"spot"` = spot, named string = HIP-3 DEX).

Live verification (public read API, 2026-06-05), wallet `0x399965e15d4e61ec3529cc98b7f7ebb93b733336`:

- `clearinghouseState` `dex:"xyz"` → `withdrawable: "58050.194947"`, `marginSummary.accountValue: "74040.64855"`, open `xyz:TSLA` positions.
- `clearinghouseState` (default) → `withdrawable: "0.0"`, `accountValue: "0.0"`, no positions.
- Confirms a real read-side gap, not user confusion about unified accounts.

Key code references:

- `src/hyperopen/api/projections/market.cljs:128` — `:perp-dex-clearinghouse` stores the raw clearinghouse response per DEX (`{:marginSummary {...} :withdrawable "..." :assetPositions [...]}`).
- `src/hyperopen/views/account_info/projections/balances.cljs:372` — `build-balance-rows` builds rows from the default clearinghouse only.
- `src/hyperopen/views/account_info/derived_cache.cljs:12` and `src/hyperopen/views/account_info/vm.cljs:233` — balance rows are memoized without `perp-dex-states`.
- `src/hyperopen/funding/domain/availability.cljs:180` — `transfer-max-amount` reads default perps only; `clearinghouse-withdrawable` already understands both `:withdrawable` and `marginSummary`-derived values.
- `src/hyperopen/funding/domain/preview.cljs:41` — `transfer-preview` only ever emits `usdClassTransfer`.
- `src/hyperopen/funding/application/submit_effects.cljs:49` — `api-submit-funding-transfer!` hardcodes `submit-usd-class-transfer!` and does **not** branch on action type (the gap the prior reviewer missed).
- `src/hyperopen/funding/application/modal_commands.cljs:83` — `open-funding-transfer-modal` takes no context (cf. `open-funding-send-modal`, which already threads a context map).
- `src/hyperopen/views/account_info/tabs/balances/{desktop,mobile,shared}.cljs` — row `Transfer` controls render labels with no action attached.
- `src/hyperopen/subaccounts/effects.cljs:311` — existing `sendAsset` action builder + USDC token fallback precedent.

## Scope

In scope:
- Classic-account balance projection for named-DEX perps collateral (one `USDC (Perps)` row per nonzero `:perp-dex-clearinghouse` entry), threaded through memoization and the Balances tab count.
- DEX-aware transfer max + `sendAsset` routing in the funding transfer preview.
- Routing the funding **transfer submit effect** by action type so `sendAsset` is signed with the `sendAsset` signer.
- A context-carrying `open-funding-transfer-modal` plus row-level `Transfer` actions on desktop and mobile for USDC perps (default + named) and USDC spot rows.
- Unit + view + browser regression coverage.

Out of scope:
- Live signed submission against the real exchange (cannot be fully verified without a controlled wallet; covered by tight unit/payload tests + the established `sendAsset` precedent).
- A DEX selector inside the **global** `Perps <-> Spot` modal. The global button keeps default-perps behavior; named-DEX transfers are initiated from the row-level control. (Documented limitation.)
- Order-form available-to-trade math, subaccount management flows, and unified/portfolio-margin transfer semantics beyond preserving the existing disabled guard.

## Progress

- [x] (2026-06-05) Reproduced the read-side gap against the public wallet via the info API; confirmed `xyz` withdrawable >> 0 while default perps = 0.
- [x] (2026-06-05) Traced the full transfer path end to end and found the submit-effect signer gap the prior reviewer omitted.
- [x] (2026-06-05) Wrote this corrected, independent ExecPlan.
- [x] (2026-06-05) RED: added failing tests for named-DEX balance rows, DEX-aware transfer max, `sendAsset` transfer preview (+ unchanged `usdClassTransfer`), submit-effect action routing, contextual modal open, and row actions; confirmed the 13 failures/errors were isolated to the new tests.
- [x] (2026-06-05) GREEN: implemented balance projection named-DEX rows + memoization/tab-count threading.
- [x] (2026-06-05) GREEN: implemented DEX-aware transfer max, `sendAsset` preview routing + token/destination derivation, and submit-effect action routing.
- [x] (2026-06-05) GREEN: added `:transfer-dex` modal state, contextual `open-funding-transfer-modal`, the `::funding-transfer-open-args` schema contract, and desktop/mobile row actions; full suite green (4234 tests).
- [x] (2026-06-05) Fixed a missed 4-arg caller of `memoized-balance-rows` in `account_equity/metrics.cljs` (+ its test stub) surfaced as a compiler arity warning; `app`/`portfolio` now compile with 0 warnings.
- [x] (2026-06-05) `npm run check` passes after the Validation-heading fix and the arity fix.
- [x] (2026-06-05) Browser/spectate confirmation against the live repro wallet (see Outcomes).
- [x] (2026-06-05) Recorded Outcomes & Retrospective; plan moved to completed.

## Test Plan

RED phase (new/updated tests):

- `test/hyperopen/views/account_info/tabs/balances/projection_test.cljs`: classic account with `perp-dex-states {"xyz" {:withdrawable "1923.97" :marginSummary {:accountValue "1923.97" :totalMarginUsed "0"}}}` produces a nonzero `USDC (Perps)` row whose `:selection-coin` parses to prefix `xyz`; default perps `0` does not add a default perps row; unified mode does not add named rows.
- `test/hyperopen/funding/domain/availability_test.cljs` (or the closest existing availability test): `transfer-max-amount` with `{:to-perp? false :transfer-dex "xyz"}` reads `:perp-dex-clearinghouse "xyz"` withdrawable, not the default clearinghouse; default `{:to-perp? false}` unchanged; `{:to-perp? true}` unchanged.
- `test/hyperopen/funding/domain/policy_preview_test.cljs`: named-DEX `Perps -> Spot` preview builds a `sendAsset` action (`sourceDex "xyz"`, `destinationDex "spot"`, full USDC token id, `destination` = wallet, `fromSubAccount ""`); named-DEX `Spot -> Perps` builds `sourceDex "spot"`/`destinationDex "xyz"`; default transfer still builds `usdClassTransfer`; missing wallet address yields a clear validation error.
- Funding submit routing test (extend the existing `submit_effects` test): a `sendAsset` transfer request calls `submit-send-asset!`; a `usdClassTransfer` request calls `submit-usd-class-transfer!`.
- `test/hyperopen/funding/.../modal` open test: `open-funding-transfer-modal` with a `{:dex "xyz" :to-perp? false}` context stores `:transfer-dex "xyz"` and `:to-perp? false`; the no-context call preserves today's defaults (`:transfer-dex ""`, `:to-perp? true`).
- `test/hyperopen/views/account_info/tabs/balances/desktop_test.cljs` + `mobile_test.cljs`: a named perps row's enabled `Transfer` control dispatches `:actions/open-funding-transfer-modal` with the row's DEX context; unified rows keep the `Unified` label and no action.

Browser regression:
- Deterministic spectate/debug fixture: classic account, default perps `0`, `xyz` withdrawable `> 0` → balances show the `xyz` `USDC (Perps)` row and the forced transfer modal shows a nonzero `MAX`; spectate keeps mutation blocked.

Required gates:
- Focused CLJS tests for funding domain/effects and balances projection/views.
- `npm run check`
- `npm test`
- `npm run test:websocket`
- `git diff --check`
- `npm run browser:cleanup` (only if browser tooling is used)

## Validation And Acceptance

Acceptance criteria:

- A classic account with default perps `0` and a named DEX (`xyz`) withdrawable `> 0` shows a `USDC (Perps)` row tagged `xyz` in the balances table, and that DEX counts toward the Balances tab badge.
- Opening transfer from that row shows a nonzero `Perps -> Spot` max sourced from the `xyz` withdrawable.
- A named-DEX transfer submits a `sendAsset` action (`sourceDex`/`destinationDex`, full USDC token id, self destination) signed with the sendAsset signer; default spot <-> perps still submits `usdClassTransfer`.
- Unified accounts keep the existing single shared USDC row with the disabled "Unified" transfer affordance.
- Spectate mode still blocks the mutation.

Required gates and current status:

- `npm test` — PASS (4237 tests, 23467 assertions, 0 failures, 0 errors).
- `npm run test:websocket` — PASS (531 tests, 3080 assertions, 0 failures, 0 errors).
- `git diff --check` — PASS.
- `npm run check` — PASS (lints, namespace size/boundary checks, docs, and the app/portfolio/worker/test compiles). `app` and `portfolio` compile with 0 warnings.
- Browser/spectate confirmation of the read-side repro — PASS (see Outcomes & Retrospective).

## Decision Log

- Decision: Treat named-DEX perps collateral as an explicit, DEX-tagged transfer source rather than folding it into default perps.
  Rationale: Folding `xyz` into default perps would fix display math but still submit the wrong (`usdClassTransfer`) action shape.
  Date/Author: 2026-06-05 / Claude

- Decision: Carry a single `:transfer-dex` field on the funding modal (`""` = default perps, `"xyz"` = named DEX). `transfer-max-amount` and `transfer-preview` already receive the modal, so both derive source/destination from `:transfer-dex` + `:to-perp?`.
  Rationale: Minimal state surface; preserves the existing direction toggle and lets the modal stay on one DEX across direction flips.
  Date/Author: 2026-06-05 / Claude

- Decision: Keep `usdClassTransfer` for default spot <-> default perps; use `sendAsset` only when `:transfer-dex` is a named DEX.
  Rationale: Preserves existing behavior/tests and matches the Hyperliquid DEX-field action model and the 2026-06-03 precedent.
  Date/Author: 2026-06-05 / Claude

- Decision (correction to prior reviewer): Also route the funding **transfer submit effect** by `(:type action)` so `sendAsset` is signed with `:sign-send-asset-action!`. The prior plan changed only `transfer-preview`; `api-submit-funding-transfer!` hardcodes `submit-usd-class-transfer!`, so without this the `sendAsset` payload would be mis-signed and rejected.
  Rationale: End-to-end correctness; mirrors how `api-submit-funding-withdraw!`/`-deposit!` already switch on action type.
  Date/Author: 2026-06-05 / Claude

- Decision: Derive the USDC token id for `sendAsset` from spot metadata (`name == "USDC"` → `USDC:<tokenId>`), falling back to the known mainnet full token id when metadata is unavailable.
  Rationale: Avoids a hardcode where possible while guaranteeing the protocol token value the signer requires; matches the 2026-06-03 precedent fallback.
  Date/Author: 2026-06-05 / Claude

- Decision: Add the row context as a trailing arg to `open-funding-transfer-modal` (`state anchor opener-data-role transfer-context`) and a dedicated `::funding-transfer-open-args` spec, rather than reordering args like the send modal.
  Rationale: Backward compatible with the existing `[:actions/open-funding-transfer-modal anchor data-role]` call sites in account-equity and portfolio header; no global-modal regression.
  Date/Author: 2026-06-05 / Claude

- Decision: The global `Perps <-> Spot` modal keeps default-perps behavior; named-DEX transfers are row-initiated.
  Rationale: A dex selector in the global modal is additional UX scope; the row control is the unambiguous, screenshot-aligned entry point.
  Date/Author: 2026-06-05 / Claude

## Surprises & Discoveries

- The live `clearinghouseState` (with `dex`) returns `:withdrawable`, not the `:availableToWithdraw` used in the prior reviewer's repro snippet. `availability/clearinghouse-withdrawable` already handles both plus a `marginSummary`-derived fallback, so feeding the raw per-DEX state in is sufficient.
- `api-submit-funding-transfer!` does not switch on action type (unlike withdraw/deposit), so the prior "just emit `sendAsset` from the preview" plan would have signed the new payload with the `usdClassTransfer` signer.
- `open-funding-send-modal` already demonstrates the context-threading pattern (normalized max/labels), so the transfer modal can follow it with a smaller `:transfer-dex` payload.
- Desktop/mobile balance rows already render a `Transfer` label with no `:on {:click ...}` — a latent dead control that this change makes functional for USDC rows.

## Outcomes & Retrospective

Shipped. The balances projection now emits a `USDC (Perps)` row per named HIP-3 perps DEX, the funding transfer modal carries a `:transfer-dex`, transfer max + preview are DEX-aware, named-DEX transfers build and sign a `sendAsset` action, and desktop/mobile balance rows wire a context-carrying `Transfer` control.

Results:

- `npm test`: 4237 tests, 23467 assertions, 0 failures, 0 errors.
- `npm run test:websocket`: 531 tests, 3080 assertions, 0 failures, 0 errors.
- `npm run check`: passed; `app` and `portfolio` build with 0 warnings.
- `git diff --check`: clean.

Live browser/spectate verification (dev server on :8080, spectating the public classic-account wallet `0x399965e15d4e61ec3529cc98b7f7ebb93b733336` on `xyz:TSLA`):

- App state showed account mode `classic`, default perps withdrawable `0`, and `:perp-dex-clearinghouse` loaded for `xyz` (and other DEXs) with `xyz` `withdrawable ≈ 56,296`, 22 positions.
- The balances table rendered named-DEX `USDC (Perps)` rows with DEX chips — `xyz` (Total ≈ 73,901 / $73,901), plus `cash`, `flx`, `hyna`, `km`, `vntl` — which were entirely absent before the change.
- Clicking the `xyz` row's `Transfer` control opened the transfer modal with `:transfer-dex "xyz"`, `:to-perp? false`, and `MAX: ~55,953 USDC` (the `xyz` withdrawable) instead of the previous `MAX: 0.00 USDC`.

Retrospective notes:

- The prior reviewer's plan changed only `transfer-preview`; the funding transfer submit effect (`api-submit-funding-transfer!`) hardcoded `submit-usd-class-transfer!`, so the new `sendAsset` payload would have been mis-signed. Routing the submit by `(:type action)` was the load-bearing correction. Covered by `submit_effects_test`.
- Threading `perp-dex-states` through `memoized-balance-rows` had a second, non-obvious caller in `account_equity/metrics.cljs`; the compiler arity warning (non-fatal) caught it. Both callers now pass `perp-dex-states` for cache coherence.
- Live signed submission still cannot be verified without a controlled wallet; this remains covered by unit/payload tests and the established `sendAsset` precedent, and spectate keeps the mutation blocked.
- Follow-up worth considering (out of scope here): the global `Perps <-> Spot` modal still targets default perps; a DEX selector there would help users whose only collateral is on a named DEX and who don't go through the row control.
