# Migrate the three remaining REST `webData2` call sites to supported /info endpoints

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. Maintain this document in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Hyperliquid deprecated `webData2`. The websocket subscription is already gone provider-side and was replaced on 2026-07-14 by the base-dex `clearinghouseState` stream (see the companion plan `/hyperopen/docs/exec-plans/active/2026-07-14-base-dex-clearinghouse-stream-replaces-webdata2.md`). What remains are three REST `/info {"type":"webData2"}` call sites. The REST variant still responds today (verified live 2026-07-14), but if Hyperliquid finishes the deprecation, three user-visible surfaces break: the asset-selector spot market list, the vault detail page (positions, open orders, balances, TWAPs), and the API Wallets page. This plan moves the first two surfaces onto endpoints that are individually supported and documented, and makes the third degrade gracefully instead of erroring, so no existing functionality is lost either now or when the provider removes the REST variant.

"webData2" is a Hyperliquid aggregate endpoint: one POST to `https://api.hyperliquid.xyz/info` with body `{"type":"webData2","user":<address>}` returns a large map (`clearinghouseState`, `openOrders`, `spotState`, `twapStates`, `spotAssetCtxs`, `agentAddress`, `serverTime`, …). The replacements are the narrower per-concern `/info` types that each return one slice of that map.

Live probes (2026-07-14, `curl -X POST https://api.hyperliquid.xyz/info`) confirmed:

    spotMetaAndAssetCtxs        -> [spotMeta, spotAssetCtxs]; the spotAssetCtxs entries are
                                   byte-identical in shape to webData2's :spotAssetCtxs
    clearinghouseState          -> assetPositions + margin summaries (same map webData2 nests)
    frontendOpenOrders          -> array of open orders with frontend fields
    spotClearinghouseState      -> {"balances":[...]} (same shape as webData2's spotState)
    twapHistory                 -> array of {time, state, status} rows; the vault adapter's
                                   normalize-twap-row already unwraps :state and reads :time
    extraAgents                 -> named agents only; does NOT carry the default agent
    webData2 (REST)             -> still serving, still returns agentAddress/agentValidUntil

## Context References

Public refs:
- Direct user/maintainer request (2026-07-14): audit webData2 usage and migrate to non-deprecated endpoints without losing any existing functionality.

Repo artifacts:
- `/hyperopen/docs/exec-plans/active/2026-07-14-base-dex-clearinghouse-stream-replaces-webdata2.md` (websocket half, already landed as commit bd38d665).
- `/hyperopen/docs/exec-plans/tech-debt-tracker.md` entry on the retained-but-inert `subscribe-webdata2` action/effect contract surface. Removing that dead plumbing is explicitly OUT of scope here; it has its own retirement criteria in the tracker.

Local scratch refs (non-authoritative): none.

## Where the three call sites live

1. Public market snapshot: `src/hyperopen/api/endpoints/market.cljs` `request-public-webdata2!` posts `{"type":"webData2","user":"0x000…000"}`. Its only consumer is `src/hyperopen/api/market_loader.cljs`, which reads exactly one key from the response: `:spotAssetCtxs`. The result is cached per api-service in `src/hyperopen/api/runtime.cljs` (`public-webdata2-cache`) via `ensure-public-webdata2!` in `src/hyperopen/api/service.cljs`.
2. Vault detail: `src/hyperopen/api/endpoints/vaults/details.cljs` `request-vault-webdata2!` posts webData2 for the vault address. The payload is stored whole under `[:vaults :webdata-by-vault <addr>]` and read by `src/hyperopen/vaults/adapters/webdata.cljs`, which consumes four slices: `[:clearinghouseState :assetPositions]`, `[:openOrders]`, `[:spotState :balances]`, and `[:twapStates]`. The adapter's `rows-from-source` probes several key paths per slice, which makes a merged replacement payload easy to shape.
3. API Wallets default agent: `src/hyperopen/api/endpoints/account/agents.cljs` `request-user-webdata2!`. The page (`src/hyperopen/api_wallets/effects.cljs`, projections in `src/hyperopen/api/projections/api_wallets.cljs`) uses only `agentAddress`, `agentValidUntil`, and `serverTime`. There is NO replacement /info endpoint for the default (unnamed) agent — `extraAgents` returns named agents only — so this call must stay, with graceful degradation added.

## Milestones

### Milestone A — public spot snapshot moves to `spotMetaAndAssetCtxs`

Change `request-public-webdata2!` in `src/hyperopen/api/endpoints/market.cljs` to post `{"type":"spotMetaAndAssetCtxs"}` (no user needed) and normalize the tuple response into a map `{:spotMeta <first> :spotAssetCtxs <second>}` so every downstream consumer (market_loader's `(:spotAssetCtxs webdata2)`, the api-service cache) keeps working unchanged. Function names, op keys, request-policy key (`:public-webdata2`), and cache plumbing are deliberately left alone to keep the diff minimal; renaming is cosmetic and can ride the tech-debt tracker. Acceptance: unit test asserting the endpoint posts `spotMetaAndAssetCtxs` and returns a map with `:spotAssetCtxs` equal to the tuple's second element; asset-selector spot rows still render in the app.

### Milestone B — vault detail moves to a four-endpoint merge

Change `request-vault-webdata2!` in `src/hyperopen/api/endpoints/vaults/details.cljs` to fan out four `/info` posts for the vault address — `clearinghouseState`, `frontendOpenOrders`, `spotClearinghouseState`, `twapHistory` — and merge them into one map shaped like the slice of webData2 the adapter reads:

    {:clearinghouseState <clearinghouseState resp>
     :openOrders         <frontendOpenOrders resp>
     :spotState          <spotClearinghouseState resp>
     :twapStates         <twapHistory rows filtered to active>}

`twapHistory` returns finished/terminated rows too; webData2's `twapStates` carried only live ones, so filter to rows whose status is `activated` or `running` (accept both `(:status row)` string and `(get-in row [:status :status])` nesting). Failure semantics: `clearinghouseState` failure rejects the whole request (that is the core of the panel, and a silent empty panel would be a functionality loss); the other three degrade to absent slices with a telemetry log, which the adapter already treats as empty lists. Add request-policy TTL keys for the two new per-vault request kinds that lack them (`:vault-open-orders`, `:vault-twap-history`) in `src/hyperopen/api/request_policy.cljs`; `:clearinghouse-state` and `:spot-clearinghouse-state` already exist. Acceptance: unit test feeding a stub `post-info!` that records bodies and returns canned slices, asserting the merged shape and the active-only twap filter; vault detail page still shows positions, orders, balances, TWAPs.

### Milestone C — API Wallets keeps webData2 but degrades gracefully

Keep `request-user-webdata2!` (still served; no replacement exists for the default agent). Two hardening changes so a future provider-side removal cannot blank the page:

1. In `src/hyperopen/views/api_wallets/vm.cljs`, stop folding the default-agent error into the page-level `:error` when the named-agents fetch succeeded. Today `:error` is `(or extra-agents-error default-agent-error)` and the rows-section shows it whenever there are zero rows — so if webData2 dies, a user with no named agents sees a red error instead of their (empty) wallet list. New rule: `:error` is the extra-agents error, or the default-agent error only when extra-agents did not load successfully (`[:api-wallets :loaded-at-ms :extra-agents]` nil). Expose the suppressed default-agent error as `:default-agent-error` in the vm and render it as a subtle inline notice above the table in `src/hyperopen/views/api_wallets/rows.cljs` ("Default API wallet status unavailable.") so the information is not silently dropped.
2. No change to the fetch itself: `promise-effects` already isolates the two requests via `allSettled`, and `apply-api-wallets-default-agent-error` already records per-source state.

Acceptance: unit test on the vm — default-agent error + successful extra-agents load ⇒ `:error` nil, `:default-agent-error` set; both failed ⇒ `:error` set.

## Validation

Run from the repo root (worktree needs `npm run setup:worktree` once):

    npm run gates          # runs npm run check, npm test, npm run test:websocket with a PASS/FAIL matrix

No browser flows or browser-test tooling change, but the touched surfaces are user-visible, so do one browser QA pass on the dev server (:8080 per the worktree preview recipe): asset-selector spot rows populate, a vault detail page renders positions/orders/balances, and the API Wallets page renders. Confirm via the network panel that no request body contains `"type":"webData2"` except the API Wallets default-agent fetch.

## Progress

- [x] (2026-07-14) Audit complete: three REST call sites identified, replacement endpoints probed live, consumer field-usage mapped.
- [x] (2026-07-14) Milestone A: `request-public-webdata2!` posts `spotMetaAndAssetCtxs` and normalizes to `{:spotMeta … :spotAssetCtxs …}`; endpoint unit tests updated/added.
- [x] (2026-07-14) Milestone B: vault detail fan-out merge — `vaultDetails` child resolution, then per-address `clearinghouseState`/`frontendOpenOrders`/`twapHistory` (+ parent-only `spotClearinghouseState`), active-twap filter, per-slice failure semantics; request-policy keys added; unit tests added including a parent-vault aggregation case.
- [x] (2026-07-14) Milestone C: api-wallets vm error split + inline notice; unit tests added.
- [x] (2026-07-14) Gates green (`npm run gates` 34/34, 6415 tests / 34842 assertions) and browser QA pass recorded under Outcomes.
- [ ] Post-deprecation follow-up: when Hyperliquid publishes a default-agent replacement endpoint (or removes REST webData2), swap the API Wallets fetch and close out via the tech-debt tracker entry.

## Surprises & Discoveries

- Observation: webData2's `spotAssetCtxs` and `spotMetaAndAssetCtxs`'s second tuple element are byte-identical per-entry.
  Evidence: live probe 2026-07-14, first entry of each: `{"prevDayPx":"0.07605","dayNtlVlm":"629502.29…","markPx":"0.08266",…,"coin":"PURR/USDC",…}`.
- Observation: the vault adapter consumes a fourth slice, `twapStates`, that the initial audit missed; `twapHistory` is shape-compatible because `normalize-twap-row` already unwraps `{:state …}` rows, but it includes non-live rows that must be filtered.
- Observation: `extraAgents` does not include the default (unnamed) agent, so REST webData2 currently has no full replacement for the API Wallets page.
  Evidence: live probe returns `[]` for an address whose webData2 payload carries `agentAddress` keys.
- Observation: the API Wallets rows-section already prefers rows over errors, so the only user-visible regression window is "default-agent fetch fails AND user has no named agents".
- Observation: for PARENT vaults (e.g. HLP, `relationship.type = "parent"` with `childAddresses`), the webData2 aggregate folded every child vault's positions and open orders into the parent response, while the standalone endpoints return empty for the parent address. Browser QA caught this as "No active positions" on the HLP detail page.
  Evidence: live probes 2026-07-14 — `webData2(HLP).clearinghouseState.assetPositions` = 175 and `openOrders` = 100, but `clearinghouseState(HLP)` = 0 positions and `frontendOpenOrders(HLP)` = 0; a normal vault (0xd6e5…5b42) returns 35 = 35 both ways. Fixed by resolving `childAddresses` via `vaultDetails` and fanning the per-address endpoints out over parent + children, concatenating positions/orders/twaps.
- Observation: two structurally identical vault-gateway delegation tests exist (`test/hyperopen/api/gateway/vaults_test.cljs` and `test/hyperopen/websocket/gateway/vaults_test.cljs`); both pin the wire bodies and both needed the same update.
- Observation: `test/hyperopen/websocket/endpoints_coverage_test.cljs` sits exactly at its namespace-size exception (829 lines), so any net line growth there trips `npm run lint:namespace-sizes`; assertions had to be compacted rather than appended.

## Decision Log

- Decision: keep function/op names containing "webdata2" (e.g. `request-public-webdata2!`, `public-webdata2-cache`) while swapping the wire type underneath.
  Why: the names are internal; renaming ripples through instance/gateway/default/service layers and tests for zero behavior gain. Recorded as cosmetic debt in the tech-debt tracker instead.
- Decision: the vault fan-out first fetches `vaultDetails` (dedupe-keyed identically to the page's own details request) to resolve `childAddresses`, then requests `clearinghouseState`/`frontendOpenOrders`/`twapHistory` for the vault AND each child, concatenating results into the singular `:clearinghouseState :assetPositions` / `:openOrders` / `:twapStates` slices.
  Why: webData2 itself presented parent-vault aggregates in the singular keys, and the adapter's `first-sequential` treats an empty parent `assetPositions` vector as authoritative — populating the plural `:clearinghouseStates` path would be masked. `spotClearinghouseState` stays parent-only because webData2 returned no `spotState` for parent vaults (verified live on HLP).
- Decision: vault merge rejects only on the vault's own `clearinghouseState` failure; child-vault and non-core slices degrade to empty.
  Why: positions are the core of the panel and silently rendering an empty panel would be a functionality loss; orders/balances/twaps rendering empty with a logged warning matches how the adapter already treats absent slices.
- Decision: keep the API Wallets webData2 REST call rather than dropping the default-agent row.
  Why: no replacement endpoint exists for the default agent; dropping the row would lose functionality, which the request explicitly forbids. Hardened the failure path instead.
- Decision: leave the inert websocket subscribe/unsubscribe contract surface untouched.
  Why: already governed by its own tech-debt-tracker entry with explicit retirement criteria including a Lean formal-surface sync; bundling it here would balloon risk without user-visible benefit.

## Outcomes & Retrospective

2026-07-14 — Plan complete except the provider-dependent follow-up item. All three milestones landed; `npm run gates` PASS (34/34 gates, 6415 tests, 34842 assertions). Browser QA on the worktree dev server (:8080): asset-selector shows 296 spot rows with live prices sourced from `spotMetaAndAssetCtxs`; the HLP vault detail — the hardest case, a parent vault whose book only existed inside the webData2 aggregate — shows "Positions (100+)" and "Open Orders (100+)" with a fully populated positions table plus "Balances (1)" from `spotClearinghouseState`; the API page renders its normal empty state.

The single most valuable step was browser QA on a parent vault: unit tests alone would have shipped a silent functionality loss (HLP positions vanishing), because the standalone endpoints genuinely return empty for parent addresses. Complexity: the vault endpoint went from one request to an N-address fan-out with a merge function — a real increase, but it is the minimum shape that reproduces the deprecated aggregate from supported endpoints; the market and api-wallets changes are net-neutral. The remaining unchecked item is externally blocked on Hyperliquid providing a default-agent replacement (or removing REST webData2), tracked here and in the tech-debt tracker.

Milestone B addendum: for parent vaults the per-request TTLs/dedupe keys are per child address, so a 7-child vault issues 1 + 1 + 3×8 = 26 requests on a cold load, bounded by the info-client scheduler and 8s TTLs; acceptable for a detail page visit, noted in case rate-limit surfacing (`set-on-rate-limit!`) starts flagging vault views.
