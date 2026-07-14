# Base-Dex Clearinghouse Stream Replaces Dead webData2 Subscription

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md`.

## Purpose / Big Picture

The Positions tab (and every other surface fed by `[:webdata2
:clearinghouseState]`) silently lost all base-dex perp positions. A book with
19 open positions renders only 8 — the ones on the `xyz` builder dex — and the
missing 11 are mostly shorts, so the book looks direction-biased when it is
not. Balances (perps USDC row), account equity, withdrawable, and the
optimizer's current-portfolio snapshot read the same bucket and degrade with
it.

Root cause, verified live against `wss://api.hyperliquid.xyz/ws` on
2026-07-14:

1. **Hyperliquid removed the `webData2` websocket subscription type.** A
   fresh connection sending the canonical
   `{"method":"subscribe","subscription":{"type":"webData2","user":…}}`
   receives `channel "error"` with `Error parsing JSON into valid websocket
   request: …` (also with `dex: ""`/`"ALL_DEXS"` variants). The app's only
   startup source for the base-dex clearinghouse was this stream; the REST
   fallback (`refresh-default-clearinghouse-snapshot!`) runs only after
   fill/ledger events. Startup stage-b REST-hydrates *named* dexes only
   (`normalize-dex-names` drops the blank base dex). Net effect: the base
   clearinghouse bucket never hydrates.
2. **The rejection was invisible.** The client has no handler for the wire
   `error` / `subscriptionResponse` channels and marks streams `subscribed?
   true` optimistically at send time. Nothing downgrades a rejected
   subscription, so the outage produced no cue and no fallback.
3. **`clearinghouseState` stream accounting is blind.** `health.cljs`
   `topic->matcher` has no `"clearinghouseState"` entry, so
   `match-stream-keys` never associates inbound frames with their stream
   (with >1 active stream the lone-key fallback also fails): `message-count`
   stays 0 and status stays `:idle` forever, even though frames verifiably
   reach the registered handler.
4. The per-dex handler **drops base-dex payloads**: the server echoes
   `data.dex = ""` for the base dex, and `clearinghouse-state-handler` bails
   on blank dex via `when-let` + `normalized-dex`.

The replacement API is verified working: subscribing
`{type "clearinghouseState", user, dex ""}` is ACKed and immediately pushes a
snapshot whose `data.clearinghouseState.assetPositions` carries all 11 base
positions; the server then pushes periodic updates (observed ~2s cadence) for
the base and every named dex.

After this change: the app subscribes `clearinghouseState` with `dex ""` for
the effective address alongside the named dexes, routes blank-dex payloads
into `[:webdata2 :clearinghouseState]` (the canonical base bucket every
consumer already reads), REST-hydrates that bucket at account bootstrap, stops
subscribing the dead `webData2` topic, repoints the health gates and freshness
cues that referenced it, adds `clearinghouseState` stream accounting, and
downgrades schema-rejected subscriptions so this class of provider-side API
removal degrades loudly to REST instead of silently dropping data.

A developer can see it working by loading `/trade` with a wallet that has
base-dex positions: the Positions tab count includes them, shorts render, and
`[:webdata2 :clearinghouseState]` updates live without any fill event.

Provenance: direct user request on 2026-07-14 — "a major book where a bunch
of positions are not being shown … mostly the short positions … a different
front end shows nineteen positions open"; root cause + plan + implementation
in one pass.

## Context References

- `src/hyperopen/websocket/user_runtime/subscriptions.cljs` — per-dex
  clearinghouse subscribe/unsubscribe sync (`sync-perp-dex-clearinghouse-subscriptions!`).
- `src/hyperopen/websocket/user_runtime/handlers.cljs` —
  `clearinghouse-state-handler` (blank-dex drop lives here).
- `src/hyperopen/websocket/user_runtime/common.cljs` — `normalized-dex`.
- `src/hyperopen/account/surface_service.cljs` — bootstrap + post-event
  refresh orchestration (`webdata2-live?` gate).
- `src/hyperopen/startup/collaborators.cljs`, `src/hyperopen/startup/runtime.cljs`
  — account bootstrap deps + webData2 address-watcher wiring
  (`init-with-webdata2!`).
- `src/hyperopen/websocket/webdata2.cljs` — legacy webData2 module (handler
  registration stays; subscribe wiring retires).
- `src/hyperopen/websocket/health.cljs` — `topic->matcher`,
  `match-stream-keys`, `status-rank`.
- `src/hyperopen/websocket/application/runtime_reducer.cljs`,
  `src/hyperopen/websocket/application/runtime/subscriptions.cljs`,
  `src/hyperopen/websocket/domain/model.cljs` — desired-subscription replay,
  stream intents (rejection state lands here; decisions stay pure).
- `src/hyperopen/api/projections/market.cljs` — projection writers.
- `src/hyperopen/views/account_info/vm.cljs` — positions freshness cue
  (currently `topic "webData2"`).
- `docs/RELIABILITY.md`, `docs/ARCHITECTURE.md` (websocket runtime purity
  contract).

## Plan Of Work

1. **Base-dex stream** — `sync-perp-dex-clearinghouse-subscriptions!` always
   includes the base dex (`""`) in the desired set for the address;
   `subscribed-clearinghouse-keys-for-address` and `unsubscribe-user!`
   recognize the blank-dex descriptor so address switches clean it up.
   Handler: blank/absent `data.dex` with a `clearinghouseState` payload map →
   write `[:webdata2 :clearinghouseState]`; non-blank dex unchanged
   (`[:perp-dex-clearinghouse dex]`).
2. **REST bootstrap** — new pure projection
   `apply-default-clearinghouse-success` (write `[:webdata2
   :clearinghouseState]`), used by a new
   `fetch-default-clearinghouse-state!` collaborator called from
   `bootstrap-visible-account-surfaces!` (priority `:high`), and reused by
   `refresh-default-clearinghouse-snapshot!`.
3. **Retire webData2** — remove the address-watcher subscribe wiring
   (`init-with-webdata2!` call sites and `subscribe-webdata2!`/
   `unsubscribe-webdata2!` deps threading); keep the channel handler
   registered (inert, zero risk, resumes if the provider ever restores the
   stream). Repoint `run-post-event-refresh!`'s `webdata2-live?` gate and the
   account-info positions freshness cue to `clearinghouseState` +
   `{:user address :dex ""}`; `topic-usable-for-address-and-dex?` accepts
   `dex ""` (guard on `string?` instead of `seq`).
4. **Stream accounting** — `topic->matcher` gains `"clearinghouseState"`
   extracting `{:user, :dex}` descriptor candidates (dex may be `""`);
   verify `subscription-key` alignment for base and named shapes.
5. **Rejection handling** — pure classifier
   (`hyperopen.websocket.acl.subscription-errors`): parse the wire error
   string; `Already subscribed`/`Already unsubscribed` → benign; `Error
   parsing JSON into valid websocket request` / invalid-subscription shapes
   with an embedded *subscribe* request → `:rejected`. Infra: register an
   `error` channel handler that logs to telemetry and publishes
   `:evt/subscription-rejected`; reducer marks the stream `subscribed? false`,
   `:status :rejected` (derived from a `:rejected-at-ms` stamp so health
   ticks don't reset it), so health reports unusable → REST fallbacks engage.
   `desired-subscriptions` is deliberately untouched (see Decision Log).
   Transient/unknown error text only logs.
6. **Tests + gates + QA** — unit coverage listed under Validation; all three
   gates; Playwright/browser QA of the Positions tab on the worktree build.

## Progress

- [x] Root cause verified live (nREPL probes + clean-connection schema probes;
      see Surprises & Discoveries).
- [x] Base-dex clearinghouseState subscription + blank-dex payload routing +
      unsubscribe symmetry, with tests.
- [x] `apply-default-clearinghouse-success` projection + REST bootstrap of the
      base clearinghouse at account bootstrap, with tests.
- [x] webData2 subscribe wiring removed; `webdata2-live?` gate and positions
      freshness cue repointed to the base clearinghouseState stream, with
      tests.
- [x] `topic->matcher` entry for `clearinghouseState`, with tests.
- [x] Subscription-rejection classifier + `error` channel handler +
      `:evt/subscription-rejected` reducer transition, with tests.
- [x] Gates green: full `npm run gates` matrix 34/34 PASS (includes
      `npm run check`, `npm test`, `npm run test:websocket`).
- [x] Browser QA (worktree build on :8090, spectating the reference wallet):
      Positions tab shows "Positions (19)"; app-db base bucket = 11 positions
      (9 shorts: WLD, TIA, ZEN, MANTA, ZETA, W, REZ, SAND, TRUMP; longs ENS,
      SOPH) + xyz 8; base `clearinghouseState :time` advances via the stream
      (not just REST); runtime streams show the dex `""` entry subscribed,
      `n-a`, message-count > 0, and no `webData2` stream remains.
- [ ] Owner sign-off + merge to main (the user's running dev session still
      serves the pre-fix main-checkout build until then); delete this plan's
      temp QA artifacts if any resurface.

## Surprises & Discoveries

- The live app store had `:webdata2 {}` (never written) while the runtime
  view claimed `subscribed? true` for webData2 — the flag is set at send
  time; nothing consumes the wire `subscriptionResponse`/`error` channels.
- All clearinghouseState WS frames were reaching the registered handler the
  whole time (probe wrapper observed pushes for every dex within ~2s); only
  health accounting (`message-count 0`, status `:idle`) was blind, because
  `topic->matcher` lacks the topic.
- The server echoes the base dex as `data.dex = ""` and ACKs an explicit
  `:dex ""` in the subscribe payload; `{type "clearinghouseState", user}`
  without `:dex` is also ACKed and normalized to `dex ""` in the response.
- Ground truth for the reference wallet (public info endpoint): 11 base
  positions (WLD, TIA, ZEN, MANTA, ZETA, W, REZ, SAND, TRUMP short; ENS, SOPH
  long) + 8 xyz longs = 19, matching the other frontend.
- **Docs re-check (2026-07-14, hyperliquid.gitbook.io …/websocket/subscriptions
  + live probe):** the subscription list no longer contains a `webData2`
  entry; the account-summary sub is now **`webData3`** `{type, user}`. But
  `webData3`'s payload is summary-only — `data = {userState, perpDexStates}`
  (agent/abstraction metadata + per-dex `totalVaultEquity` + OI caps), with
  **no `assetPositions` / `clearinghouseState` / `meta` / `assetCtxs`** (raw
  ~600 bytes; every position field grep-negative). So subscribing `webData3`
  would NOT have restored the missing positions — they now come from per-dex
  `clearinghouseState` (this fix) or the new bundled `allDexsClearinghouseState`.
- The provider also added **`allDexsClearinghouseState`** `{type, user}` — a
  single subscription returning all 19 positions as a `clearinghouseStates`
  array of `[dexInfo, state]` pairs — plus `allDexsAssetCtxs`, `spotState`,
  and `fastAssetCtxs`. These are possible future consolidations (see Decision
  Log), not needed for the fix.

## Decision Log

- Represent the base dex as `""` end-to-end (subscription payload, stream
  key, desired-set key) rather than a sentinel keyword: it matches the
  server's own echo and keeps `subscription-key` alignment trivial.
- Base payloads land in `[:webdata2 :clearinghouseState]` rather than
  `[:perp-dex-clearinghouse ""]`: every existing consumer (positions,
  balances, equity, optimizer, vaults adapter, order effects) already reads
  the former; migrating consumers would balloon the diff for zero behavior
  gain.
- Keep the `webData2` channel *handler* registered but stop subscribing:
  inert if the provider never sends it, self-healing if they restore it.
- Rejection handling only downgrades on schema-level errors that embed the
  offending *subscribe* request; transient/unknown errors just log, and
  unsubscribe echoes never downgrade a stream.
- **Deviation from the original plan:** rejection does NOT remove the entry
  from `desired-subscriptions`. The replay set is exactly what the TLA spec
  (`spec/tla/websocket_runtime.tla`) models; mutating it from a new message
  type would add an unmodeled transition. Keeping it means one extra error
  frame per reconnect for a dead topic — negligible — and gives free
  self-healing: replay re-arms the stream (`mark-subscribe` clears
  `:rejected-at-ms`), and if the provider restored the topic it just works.
  Health-side bookkeeping (`:streams`) is not part of the spec's replay
  decisions, so the reducer change is formally neutral.
- `:status :rejected` is derived from a `:rejected-at-ms` stamp inside
  `derive-stream-status` rather than only assigned imperatively — health
  hysteresis re-derives statuses every tick and would otherwise snap the
  stream back to `:idle` within a second.
- Classification and stream-state transitions are pure (ACL/reducer);
  the registered `error` handler only logs and publishes the runtime message
  (side effects stay in interpreters/infrastructure per ARCHITECTURE.md).
- The `:actions/subscribe-to-webdata2` / `:effects/subscribe-webdata2`
  contract surface (schema registrations + Lean formal surface) stays: no
  production dispatcher remains, and deleting it drags in the formal-surface
  sync for zero runtime gain. Logged in the tech-debt tracker instead.
- **Kept per-dex `clearinghouseState` over the alternatives (2026-07-14 docs
  re-check).** `webData3` is the documented webData2 replacement but is
  summary-only (no positions), so it is not a candidate for the position book.
  `allDexsClearinghouseState` DOES bundle all-dex positions in one sub, but its
  `clearinghouseStates` array-of-pairs shape needs new parsing, routing, and
  health accounting, and would diverge from the per-dex architecture the app
  already runs for HIP-3 named dexes (named-dex refresh flows, margin-rec).
  Adding the base `""` to the existing per-dex machinery is the minimal,
  consistent change. Follow-ups worth considering separately: (a) subscribe
  `webData3` to restore the account-summary data the old webData2 fed
  (`abstraction` mode, agent info, per-dex equity) — the app still has latent
  reads of `[:webdata2 :spotMeta]`/`:openOrders`/`:totalVaultEquity`/`:fills`
  that have been dead since the provider dropped webData2 (pre-existing, not
  caused by this fix); (b) evaluate `allDexsClearinghouseState` /
  `allDexsAssetCtxs` as a consolidation of the per-dex subscription fan-out.

## Validation

Required gates (all must pass before completion):

- `npm run check`
- `npm test`
- `npm run test:websocket`

Targeted unit coverage added with the change:

- subscriptions: desired set includes `["<addr>" ""]`; unsubscribe collects
  the base key; no duplicate subscribe when already present.
- handlers: blank-dex payload → `[:webdata2 :clearinghouseState]` (other
  webdata2 keys preserved); named dex unchanged; malformed payload (no
  clearinghouseState map, no dex) still dropped.
- projections: `apply-default-clearinghouse-success` writes the bucket and
  preserves siblings.
- health: `descriptor-candidates` for clearinghouseState base + named;
  `match-stream-keys` resolves among multiple active clearinghouse streams.
- domain subscription-errors: classifier table (already-subscribed benign,
  parse-error rejected, unknown logged-only).
- reducer: `:evt/subscription-rejected` marks stream rejected + drops the
  desired subscription; a later subscribe intent re-arms it; replay after
  rejection does not re-send the dead subscription.
- surface service: bootstrap calls the default-clearinghouse fetch; post-event
  refresh gates on the base clearinghouse stream topic.

Browser QA (worktree build, Playwright or workbench recipe):

- Positions tab renders base + named dex rows (19 for the reference wallet),
  count badge matches, shorts visible, live tick updates without fills.
- Balances perps USDC row and account equity render non-empty.

## Outcomes & Retrospective

- Implemented and validated 2026-07-14: 34/34 gates PASS; browser QA confirms
  the full 19-position book renders (11 base incl. 9 shorts + 8 xyz longs)
  and the base bucket live-updates from the dex `""` stream.
- The outage was invisible for exactly the reasons this plan hardens: no
  wire-error handling, optimistic `subscribed?`, and blind clearinghouse
  stream accounting. All three are now covered by unit tests, so a future
  provider-side topic removal degrades to REST with a `:rejected` stream
  status instead of silently dropping data.
- Diagnosis technique worth keeping: bb/bencode nREPL client into the running
  shadow-cljs app to read app-db + runtime-view, then a duplicate-subscribe
  probe on the LIVE socket ("Already subscribed" vs ack vs error
  discriminates server-side subscription state), then clean-connection Node
  `WebSocket` probes to separate connection state from API schema changes.
- Files changed: see the branch diff
  (`feature/missing-short-positions-18e85a`); key seams are
  `user_runtime/subscriptions.cljs` (base-dex desired set),
  `user_runtime/handlers.cljs` (blank-dex routing),
  `api/projections/market.cljs` (+ facade) `apply-default-clearinghouse-success`,
  `startup/collaborators.cljs` + `account/surface_service.cljs`
  (REST bootstrap + gate repoint), `websocket/health.cljs` (matcher,
  `:rejected`), `runtime_reducer.cljs` (+ subscriptions bookkeeping),
  `acl/subscription_errors.cljs` + `websocket/subscription_errors.cljs`
  (error-channel handling), `views/account_info/vm.cljs` (freshness cue),
  `wallet/address_watcher.cljs` + `startup/runtime.cljs` (webData2
  retirement).
