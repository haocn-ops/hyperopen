# Sub-Accounts Page Recovery + Owner-Mode Transfer Routing

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` are maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Durable context: direct user/maintainer request — bug report "it worked! I was able to transfer to spot, but I can't get the subaccount page to load to transfer to the master" (master `0x999e9a397b703d68af21113abededd827b309068` = unifiedAccount, sub-account Tenor `0xbce774ef2382a4eb9376ea6f20408b318b10b63e`). Builds on parent ExecPlan `docs/exec-plans/completed/2026-06-06-pooled-account-named-dex-transfer-routing.md`.

## Purpose / Big Picture

The Sub-Accounts page could get permanently stuck at "Loading subaccounts…" with the Refresh button stuck at "Refreshing…", and even once recovered the "transfer to master" path was unreliable for a unified (portfolio-margin) master. This plan makes the page self-recoverable and routes sub-account transfers by the **master/owner** account mode rather than the active trading account's mode, so a withdraw back to a unified master works even while a classic sub-account is the active trading account.

## Progress

- [x] (2026-06-07) Added an `AbortController` + configurable deadline to every `/info` fetch (`fetch-with-timeout!` in `src/hyperopen/api/info_client/flow.cljs`, default `request-timeout-ms` 15000 in `runtime.cljs`) so a stalled request rejects instead of permanently poisoning the single-flight cache.
- [x] (2026-06-07) Made `load-subaccounts-route` idempotent and added a non-destructive `refresh-subaccounts` action + `api-refresh-subaccounts` effect using a tokenized force key, wired the Refresh button, and kept rows visible during refresh. Registered across the full action/effect contract surface incl. the Lean formal model.
- [x] (2026-06-07) Tracked the master/owner account mode at `[:account-context :subaccounts :owner-mode]` (fetched best-effort via `request-user-account-mode!` on every Sub-Accounts load/refresh) and added `account-context/subaccounts-owner-unified?`. Replaced the active-account `unified-account-mode?` checks in `subaccounts/effects.cljs`, `subaccounts/management.cljs`, and `views/subaccounts_view.cljs`.
- [x] (2026-06-07) Fixed the view so the "Loading subaccounts…" placeholder only renders when there are no rows yet; existing rows (and their transfer controls) stay reachable while a load is in flight.
- [x] (2026-06-07) Hardened `normalize-subaccounts` to also accept a `{:subAccounts [...]}` map wrapper, not only a bare array.
- [x] (2026-06-07) Added regression coverage: CLJS effects (mixed-mode deposit + withdraw, owner-mode load), CLJS view (loading-with-rows, loading-only-when-empty, owner-mode popover), CLJS endpoint (wrapped payload), and a Playwright e2e (`unified master withdraw uses sendAsset even when a classic sub-account is active`).
- [ ] Deferred (out of scope, tracked for a follow-up): derive the spot USDC token id in `unified-send-asset-token` from spot metadata / network config instead of the hardcoded mainnet `USDC:0x6d1e7cde53ba9467b783cb7c530ce054`.
- [ ] Deferred (owner action): `docs/DESIGN.md` is past its `last_reviewed` cycle (pre-existing; unrelated to this change) so `npm run check` stops at `lint:docs`. The document owner must refresh it.

## Surprises & Discoveries

- Observation: `[:account :mode]` is the **active/effective** account's mode, not the master's. Evidence: `api/projections/user_abstraction.cljs` writes `[:account ...]` only when the requested address equals `account-context/effective-account-address`, which becomes the selected sub-account once one is chosen for trading. This is why a unified master with a classic sub-account selected fell through to the legacy `subAccountTransfer`.
- Observation: A simple fallback to `[:account :mode]` (used only until `owner-mode` loads) keeps the existing unified tests/e2e green because they connect with the master active and no sub-account selected, while the explicit `owner-mode` makes the mixed-mode case correct.
- Observation: The Sub-Accounts route loader never blanks rows for the same owner once loaded, so a stuck `:loading` with rows present should be rare — but the view fix is still correct/defensive and is covered by a dedicated test.

## Decision Log

- Decision: Track `owner-mode` by piggybacking the existing `:effects/api-load-subaccounts` / `:effects/api-refresh-subaccounts` effects (new `:request-owner-mode!` dep) rather than adding a new action/effect. Rationale: avoids expanding the action/effect/formal contract surface for a read-only side fetch; the fetch is best-effort and never rejects the subaccounts load. Date/Author: 2026-06-07.
- Decision: `subaccounts-owner-unified?` prefers `owner-mode` and falls back to `[:account :mode]` only when `owner-mode` is unknown. Rationale: conservative during the brief pre-load window; authoritative thereafter. Date/Author: 2026-06-07.
- Decision: Leave the hardcoded mainnet USDC token id in place for now. Rationale: it is correct on mainnet and changing the money-movement payload without a metadata source is riskier than the deferred improvement; tracked above. Date/Author: 2026-06-07.

## Validation and Acceptance

- `npm test` — 4268 tests / 23564 assertions, 0 failures.
- `npm run lint:namespace-sizes`, `lint:test`, `lint:hiccup`, `lint:input-parsing`, `lint:namespace-boundaries`, `lint:docs:test` — all pass.
- `npx shadow-cljs compile app` — 0 warnings.
- Playwright `subaccounts-regressions.spec.mjs` — 4/4 pass (incl. the new mixed-mode withdraw regression).
- Known: `npm run lint:docs` fails on the pre-existing `docs/DESIGN.md` staleness (owner action above), unrelated to this change.

## Outcomes & Retrospective

The Sub-Accounts page is now self-recoverable (timeout at the source, idempotent loader, force-refresh escape hatch) and sub-account transfers route by the master's account mode, fixing the reported "transfer to master" gap for a unified master with a classic sub-account active. Deterministic CLJS tests plus an executed Playwright e2e encode the regressions. Two follow-ups are deferred and tracked above (spot token derivation; DESIGN.md owner refresh).
