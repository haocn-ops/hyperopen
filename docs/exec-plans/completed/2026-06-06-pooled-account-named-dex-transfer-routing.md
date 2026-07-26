# Named-DEX Transfer: Subaccount Identity + Pooled-Account Routing Fix

Follow-up correction to the 2026-06-05 named-DEX transfer feature. Maintained per
`docs/PLANS.md` and `.agents/PLANS.md`.

## Purpose / Big Picture

After `b9434ee5` ("show and transfer HIP-3 named-DEX USDC collateral"), submitting a
`Perps (xyz) -> Spot` transfer failed with:

> Transfer failed: Unified account only supports sending assets through spot

Two distinct issues were found while diagnosing the live address
`0x999e9a397b703d68af21113abededd827b309068`:

1. **(Primary — the reported failure) Master/subaccount identity mismatch.** The user
   trades on a **classic subaccount** `Tenor` (`0xbce774…b63e`) that holds the `xyz`
   balance, under a **unified master** (`0x999…9068`). Balances are read from the
   selected subaccount (effective account), but the transfer payload used the connected
   master wallet as source with `fromSubAccount ""` — i.e. "send xyz from the unified
   master", which the unified master cannot do. The classic subaccount can; the action
   must carry `fromSubAccount = subaccount` (owner still signs).

2. **(Secondary — complementary) Pooled-account routing.** For an account that is *itself*
   unified / portfolio-margin / DEX Abstraction, a per-DEX `sendAsset` (named `sourceDex`)
   is rejected; such perps<->spot moves must use the default perps DEX (`sourceDex`/
   `destinationDex` in {"", "spot"}). This is the master's own behavior (its 2026 ledger).

After this change:
- A transfer whose visible balance belongs to a selected owner-controlled subaccount
  builds a `sendAsset` with `fromSubAccount` and `destination` set to that subaccount,
  keeps `sourceDex` as the named dex (the classic subaccount can send from it), signs with
  the connected owner wallet, and refreshes the effective (subaccount) account.
- A transfer on a pooled effective account collapses the perps side to the default DEX.
- Standard master-account transfers are unchanged.

## Context References

Durable references:

- Direct maintainer request on 2026-06-06/07. The reporting user supplied the affected
  address; an independent reviewer (Codex) identified the master/subaccount mismatch:
  `/hyperopen` sibling worktree plan `docs/exec-plans/active/2026-06-07-named-dex-subaccount-transfer-source.md`.
- Parent feature ExecPlan: `docs/exec-plans/completed/2026-06-05-named-dex-transfer-balances.md`.
- `sendAsset` field semantics / prior identical error: `docs/exec-plans/completed/2026-06-03-subaccount-unified-sendasset-routing.md`.

Live ground truth (public info API, 2026-06-06/07):

- Master `0x999…9068`: `userAbstraction = unifiedAccount`; owns subaccount `Tenor`
  `0xbce774…b63e`; master `xyz` and default perps withdrawable both `0.0`.
- Subaccount `0xbce774…b63e`: `userAbstraction = disabled` (classic); `xyz` withdrawable
  `1923.973478`; spot USDC `78.2234409` — the exact balances the user sees.
- Subaccount ledger, 2026-05-28: a successful `send` (`sendAsset`)
  `{user:0xbce774…, destination:0xbce774…, sourceDex:"spot", destinationDex:"xyz"}` —
  i.e. a within-subaccount spot->xyz move. The reported xyz->spot is its exact reverse,
  confirming `destination`/`fromSubAccount` = subaccount.
- Master ledger, 2026: perps<->spot are `sendAsset` with `sourceDex`/`destinationDex` in
  {"", "spot"}, never a named dex, and no `usdClassTransfer` since the unified migration.

Key code references:

- `src/hyperopen/account/context.cljs` — `exchange-vault-address` (selected owned
  subaccount), `effective-account-address`, `owner-address`.
- `src/hyperopen/funding/application/modal_commands.cljs` — derives transfer identity at
  modal-open.
- `src/hyperopen/funding/application/modal_state.cljs` — new modal keys.
- `src/hyperopen/funding/domain/preview.cljs` — `named-dex-transfer-request` reads the
  identity; pooled branch via `availability/pooled-perps-collateral?`.
- `src/hyperopen/funding/application/submit_effects.cljs` — owner signs, effective account
  refreshes.

## Scope

In scope:
- `:transfer-destination-address` / `:transfer-from-subaccount` modal keys, derived from
  `exchange-vault-address` at modal-open (explicit context overrides allowed).
- `named-dex-transfer-request` uses those for `destination`/`fromSubAccount` (fallback:
  wallet / "").
- Submit signs with the owner wallet; post-success refresh targets the effective account.
- `pooled-perps-collateral?` + default-perp collapse for pooled effective accounts.
- Tests across modal context, preview, and submit effects.

Out of scope:
- Standard master-account transfers (unchanged).
- Tightening the startup race where a named-DEX row can appear before abstraction loads.

## Progress

- [x] (2026-06-06) Shipped + verified the pooled-account default-perp `sendAsset` routing
  against the master's mode and 2026 ledger.
- [x] (2026-06-07) Confirmed the reviewer's master/subaccount finding live: the visible
  balance is on the classic subaccount; the payload used the unified master as source.
- [x] (2026-06-07) Confirmed the correct payload from the subaccount's own ledger (the
  spot->xyz entry is the exact reverse of the failing xyz->spot).
- [x] (2026-06-07) Implemented modal source identity, preview payload, owner-signs/
  effective-refresh, and tests; bumped the modal_commands size exception (split deferred).

## Test Plan

- `transfer_modal_context_test.cljs`: a selected owned subaccount sets
  `:transfer-from-subaccount`/`:transfer-destination-address` to the subaccount; no
  subaccount defaults destination to the wallet and fromSubAccount to "".
- `named_dex_transfer_preview_test.cljs`: subaccount-identity modal builds `sendAsset`
  with `destination`/`fromSubAccount` = subaccount and `sourceDex:"xyz"` (and the spot->xyz
  reverse); existing master tests (wallet destination, `fromSubAccount ""`) unchanged;
  pooled-account default-perp collapse covered.
- `submit_effects_test.cljs`: a subaccount-source `sendAsset` is signed with the owner
  wallet (not the subaccount) and refreshes the effective (subaccount) address.
- `actions_test.cljs` / `core_public_actions_test.cljs`: default modal maps include the two
  new keys.

## Validation And Acceptance

- `npm test` — PASS (4252 tests, 0 failures, 0 errors).
- `npm run test:websocket` — PASS (531 tests, 0 failures, 0 errors).
- `app`/`portfolio` compile with 0 warnings; `lint:namespace-sizes` passes (modal_commands
  exception bumped 545 -> 560, split still retire-by 2026-06-30).
- Live API confirmation of the master/subaccount shapes and the subaccount ledger payload.
- Pre-existing, unrelated `npm run check` items remain (optional-solver optimizer test;
  `docs/DESIGN.md` 90-day review staleness).

## Decision Log

- Decision: Carry the selected subaccount source identity (`fromSubAccount` + `destination`)
  into the transfer payload; keep `sourceDex` as the named dex for a classic subaccount.
  Rationale: The funds live on the classic subaccount, which can send from a named dex; only
  the source identity was wrong. Confirmed by the subaccount's own successful ledger entry.
  Date/Author: 2026-06-07 / Claude

- Decision: Sign with the connected owner wallet; refresh the effective account.
  Rationale: Hyperliquid subaccount actions are owner-signed, but the changed balances are
  the subaccount's.
  Date/Author: 2026-06-07 / Claude

- Decision: Keep the pooled-account default-perp `sendAsset` routing as a complementary fix.
  Rationale: Correct for an account that is itself unified (the master's own 2026 ledger),
  even though the reported failure was the subaccount case.
  Date/Author: 2026-06-07 / Claude

- Decision: Route *any* transfer with a non-empty `:transfer-from-subaccount` through
  `sendAsset` (not just named-DEX rows) — including the subaccount's default-perp and spot
  USDC rows. `usdClassTransfer` is used only for a plain master-account default transfer.
  Rationale: `usdClassTransfer` has no `fromSubAccount` field and is posted without a
  `vaultAddress` (`user-actions/sign-and-post-user-action!` passes no options), so it acts
  on the signing owner. For a selected subaccount that silently moves the owner's USDC (or
  fails), while the post-success refresh of the untouched subaccount hides the regression.
  Surfaced by /ultrareview (bug_001); fixed by a single `use-send-asset?` branch.
  Date/Author: 2026-06-07 / Claude

- Decision: Make `transfer-max-amount` pooled-aware so a pooled account's named-DEX
  perps->spot validates/displays the max against the default pooled perps balance
  (`perps-withdrawable`), matching the collapsed `sourceDex ""` it submits with — not the
  (possibly empty) named bucket.
  Rationale: Otherwise a pooled account (reachable for `dexAbstraction`, which is `:classic`
  so named rows are not unified-gated) with pooled default-perps collateral but an empty
  `xyz` bucket fails with "No perps balance available" before the fixed request path runs.
  `transfer-max-amount` drives both the displayed MAX and the preview validation, so fixing
  it there keeps both consistent. Surfaced by a second external review.
  Date/Author: 2026-06-07 / Claude

## Surprises & Discoveries

- The reported failure was a master/subaccount identity bug, not (only) a unified-account
  routing bug. An earlier in-app "verification" gave a false positive because it spectated
  the master directly (effective = master = unified) rather than the subaccount-selected
  state the user actually has (effective = subaccount = classic), where the pooled fix does
  not even fire.
- The subaccount's own ledger is the gold-standard payload reference: its 2026-05-28
  spot->xyz `sendAsset` (destination = subaccount) is the exact reverse of the failing move.
- `:transfer-dex` set the precedent that internal modal keys need not be added to the VM
  contract; the two new keys follow it.

## Outcomes & Retrospective

Shipped both fixes. The reported case (classic subaccount under a unified master) now builds
a `sendAsset` with `fromSubAccount`/`destination` = the subaccount and `sourceDex:"xyz"`,
signed by the owner and refreshing the subaccount — matching the subaccount's proven ledger.
The pooled-account routing remains for accounts that are themselves unified.

Residual (not blocking): the modal source identity is captured from the *currently selected*
subaccount at modal-open. The startup race where a named-DEX row can briefly appear before
abstraction loads is unchanged; gating the affordance on abstraction-loaded is optional
hardening. Live signed submission was not run (read-only probing only).
