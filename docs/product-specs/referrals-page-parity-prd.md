---
owner: product
status: supporting
last_reviewed: 2026-06-08
review_cycle_days: 90
source_of_truth: true
---

# Referrals Page Parity PRD (Hyperliquid Reference)

## Overview

Build a dedicated `/referrals` experience in Hyperopen that matches the
Hyperliquid referrals product model and user workflow: enter a referral code,
create and share a personal referral code after the volume requirement, inspect
referred traders, and claim referral rewards.

This PRD defines the target product behavior and a phased delivery path. The
first implementation pass should be backed by an active ExecPlan before source
changes, because the route touches account data, signed exchange actions,
navigation, browser flows, and responsive UI.

## Reference Inspection Summary

Reference sources inspected on June 8, 2026:
- Live Hyperliquid route: `https://app.hyperliquid.xyz/referrals`
- Official referral docs: `https://hyperliquid.gitbook.io/hyperliquid-docs/referrals`
- Official info endpoint docs: `https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/info-endpoint`
- Official exchange endpoint docs: `https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/exchange-endpoint`
- Official Python SDK:
  - `https://github.com/hyperliquid-dex/hyperliquid-python-sdk`
  - `hyperliquid/exchange.py`
  - `hyperliquid/info.py`
  - `examples/basic_set_referrer.py`
- Reference TypeScript SDK docs:
  - `https://jsr.io/@nktkas/hyperliquid/doc/api/info/~/ReferralRequest`
  - `https://jsr.io/@nktkas/hyperliquid/doc/api/exchange`
  - `https://jsr.io/@nktkas/hyperliquid/doc/all_symbols`
- Hyperopen repo evidence:
  - `src/hyperopen/views/header/nav.cljs`
  - `src/hyperopen/route_modules.cljs`
  - `src/hyperopen/app/startup.cljs`
  - `src/hyperopen/app/effects.cljs`
  - `src/hyperopen/startup/route_refresh.cljs`
  - `src/hyperopen/api/gateway/account.cljs`
  - `src/hyperopen/api/endpoints/account.cljs`
  - `src/hyperopen/api/endpoints/account/portfolio.cljs`
  - `src/hyperopen/api/instance.cljs`
  - `src/hyperopen/api/trading.cljs`
  - `src/hyperopen/api/trading/agent_actions.cljs`
  - `src/hyperopen/portfolio/fee_schedule.cljs`

Observed Hyperliquid page structure:
1. Global nav includes `Referrals` between `Staking` and `Leaderboard`.
2. The route title is `Referrals`.
3. Subtitle says the page is for referring users and earning rewards, with a
   `Learn more` link to the referral docs.
4. Primary action row contains:
   - `Enter Code`
   - `Create Code` or `Share Code`
   - `Claim Rewards`
5. KPI cards show:
   - `Traders Referred`
   - `Rewards Earned`
   - `Claimable Rewards`
6. Tabbed history surface contains:
   - `Referrals`
   - `Legacy Reward History`
7. The unauthenticated or legally restricted surface still renders the title,
   actions, KPI shells, tabs, and empty table region, while trading actions are
   disabled by account/trader state.

Observed Hyperliquid table behavior from the live app:
1. `Referrals` table columns:
   - `Address`
   - `Date Joined`
   - `Total Volume`
   - `Fees Paid`
   - `Your Rewards`
2. `Referrals` defaults to sorting by `Total Volume` descending and paginates at
   10 rows.
3. Empty referrer table text is `No referrals yet`.
4. `Legacy Reward History` table columns:
   - `Date`
   - `Your Volume`
   - `Your Referral Volume`
   - `Total Rewards Earned`
5. `Legacy Reward History` defaults to sorting by time descending and paginates
   at 10 rows.
6. Empty legacy history text is `No rewards earned`.

Observed protocol and docs behavior:
1. A user can create a referral code after `$10,000` of volume.
2. A referrer receives 10% of referred users' fees, net of any referral discount.
3. Referral rewards apply to the referred user's first `$1B` of volume.
4. Referral discounts apply to the referred user's first `$25M` of volume.
5. The share link format is `https://app.hyperliquid.xyz/join/YOURCODE`.
6. Rewards accrue across quote assets and can be claimed once the claimable value
   is greater than `$1`.
7. Using a referral code gives the referred user a 4% fee discount for the first
   `$25M` of volume.
8. Referral discounts do not apply to vaults or subaccounts.
9. Claimed referral rewards land in the user's spot balance.

Observed API and SDK behavior:
1. Referral read model comes from `/info` with:

   ```json
   {"type": "referral", "user": "0x..."}
   ```

2. `ReferralResponse` includes:
   - `referredBy`
   - `cumVlm`
   - `unclaimedRewards`
   - `claimedRewards`
   - `builderRewards`
   - `tokenToState`
   - `referrerState`
   - `rewardHistory`
3. `referrerState` has three target stages:
   - `ready`
   - `needToCreateCode`
   - `needToTrade`
4. `ready` state includes the user's code, referral count, and referred-trader
   rows.
5. `needToTrade` includes a `required` amount that the UI should display instead
   of hard-coding the threshold.
6. `rewardHistory` is legacy data; current reward claims should be checked in
   non-funding ledger updates when audit/history parity is expanded.
7. Exchange action for claiming rewards is:

   ```json
   {"type": "claimRewards"}
   ```

8. Reference SDKs also expose:
   - `setReferrer` with action `{"type": "setReferrer", "code": "..."}`
   - `registerReferrer` with action `{"type": "registerReferrer", "code": "..."}`
9. The official Python SDK signs `setReferrer` as an L1 action with no vault
   address.
10. Hyperliquid's current web UI signs `setReferrer`, `registerReferrer`, and
    `claimRewards` through the active agent-wallet path when trading is ready.

Observed Hyperopen state:
1. Hyperopen has no first-class `/referrals` route or route module today.
2. Header nav currently includes `Trade`, `Portfolio`, `Funding`, `Vaults`,
   `Staking`, `Leaderboard`, `API`, and `Sub-Accounts`, but not `Referrals`.
3. Deferred route modules are registered in `shadow-cljs.edn` and
   `src/hyperopen/route_modules.cljs`.
4. Route entry actions are wired through:
   - `src/hyperopen/startup/route_refresh.cljs`
   - `src/hyperopen/runtime/action_adapters/navigation.cljs`
   - `src/hyperopen/app/actions.cljs`
5. Account `/info` requests are centralized under
   `src/hyperopen/api/endpoints/account.cljs` with per-feature submodules.
6. Default API exports flow through `src/hyperopen/api/default.cljs`, while
   injectable API-service instances also wire account ops in
   `src/hyperopen/api/instance.cljs`.
7. Runtime effect registration is finalized in `src/hyperopen/app/effects.cljs`.
8. Signed L1 actions already flow through
   `src/hyperopen/api/trading/agent_actions.cljs` for agent-wallet actions.
9. Portfolio fee schedule already understands `activeReferralDiscount` from
   `userFees`; the referrals page must not duplicate or diverge from that
   existing fee model.

## Problem Statement

Hyperopen does not expose Hyperliquid's referral workflow. Users cannot apply a
referral code, create and share their own code, inspect referred traders, or
claim referral rewards from Hyperopen. This is a visible parity gap because
Hyperliquid places referrals in top-level navigation and supports the workflow
as an account-management surface.

## Product Goals

1. Add `/referrals` as a first-class route with navigation parity.
2. Let connected users enter a referral code when they are eligible.
3. Let eligible referrers create, view, copy, and share their referral code.
4. Show referrer KPIs and referred-trader history using the official referral
   info payload.
5. Let users claim referral rewards through the same signed-action path as other
   agent-wallet L1 actions.
6. Preserve existing fee-schedule behavior that reads `activeReferralDiscount`
   from `userFees`.
7. Keep vault and subaccount account modes conservative: display referral data
   for the master account and disable referral mutations when the effective
   trading context is a vault or subaccount.

## Non-Goals

1. Building a custom Hyperopen referral program.
2. Changing fee schedule calculation outside the official Hyperliquid referral
   data and existing `userFees` payload.
3. Builder-code fee approval UX. `builderRewards` can be parsed and displayed
   later, but builder-code setup is separate from referrals.
4. Staking referral proposal support. The docs list that as a proposal, not as
   confirmed referrals page parity.
5. Applying referral discounts to vaults or subaccounts.
6. Backfilling all modern reward-claim ledger history in Phase 1.

## Users

1. New or low-volume traders applying a friend's referral code before crossing
   the eligibility limit.
2. Active traders who have enough volume to create and share a referral code.
3. Referrers tracking traders, volume, fees, rewards, and claimable balances.
4. Mobile users copying a referral link or checking claimable rewards.

## Information Architecture

Primary sections in order:
1. Page header:
   - title `Referrals`
   - short referral rewards description
   - `Learn more` link to the official Hyperliquid referrals docs
2. Action row:
   - `Enter Code`
   - `Create Code` or `Share Code`
   - `Claim Rewards`
3. KPI grid:
   - `Traders Referred`
   - `Rewards Earned`
   - `Claimable Rewards`
4. Tabbed detail panel:
   - `Referrals`
   - `Legacy Reward History`
5. Modal layer:
   - enter-code modal
   - create/share-code modal
   - claim-rewards confirmation modal

The route should feel like a compact account-management page, not a marketing
landing page. Use Hyperopen's existing route shell, dark trading surface, table
styling, mobile card fallback, and focused action buttons.

## Functional Requirements

### FR-1 Route and Navigation

1. `/referrals` MUST render a dedicated referrals page.
2. Header nav MUST include `Referrals` in desktop navigation near `Staking` and
   `Leaderboard`, matching the reference IA.
3. Mobile navigation MUST expose `Referrals` in the secondary page list.
4. Direct navigation, refresh, and browser back/forward MUST preserve a valid
   route state.
5. `/join/:code` MUST be recognized as a referral join route.
6. `/join/:code` MUST persist the pending referral code and guide the user
   through connect/enable-trading until the code can be applied.
7. After a join code is terminally accepted, rejected, or identified as already
   set, the pending join code MUST be cleared.

### FR-2 Data Loading

1. The page MUST request referral data for the active master account with:

   ```json
   {"type": "referral", "user": "<master address>"}
   ```

2. The request MUST be route-gated to `/referrals` and `/join/:code`.
3. The request MUST refetch when the active wallet/master account changes.
4. The page MUST not use a spectated address, vault address, or subaccount
   address for signed referral mutations.
5. The normalizer MUST preserve the raw payload for debugging while projecting
   stable view fields.
6. `tokenToState` MUST be normalized to token-aware rows by joining token IDs
   against `spotMeta` when available.
7. Numeric strings MUST be parsed only at the view-model boundary; raw payload
   values should remain available for exact protocol evidence.
8. If `spotMeta` is missing, token IDs MUST still render as deterministic
   fallback labels.

### FR-3 Header Actions

1. `Enter Code` opens the enter-code modal.
2. `Create Code` opens the create-code modal when `referrerState.stage` is
   `needToCreateCode`.
3. `Create Code` remains visible but disabled with eligibility copy when
   `referrerState.stage` is `needToTrade`.
4. `Share Code` replaces `Create Code` when `referrerState.stage` is `ready`.
5. `Claim Rewards` opens a claim confirmation modal.
6. Action buttons MUST be disabled while disconnected, legally blocked,
   read-only, in spectate mode, or when agent trading is unavailable.
7. Referral mutations MUST also be disabled while a connected user has an owned
   subaccount selected as the active trading context. Hyperopen's general
   read-only guard does not block that state today, so referrals need an
   explicit master-account-only action guard.
8. Disabled actions MUST expose concise reason copy through the same affordance
   pattern used elsewhere in Hyperopen.

### FR-4 KPI Cards

1. `Traders Referred` MUST display `referrerState.data.nReferrals` when the
   referrer state is `ready`; otherwise it displays `0`.
2. `Rewards Earned` MUST display `claimedRewards + unclaimedRewards`, aggregated
   by quote asset where token-aware data is available.
3. `Claimable Rewards` MUST display `unclaimedRewards`, aggregated by quote
   asset where token-aware data is available.
4. KPI fallback values MUST be deterministic during loading, error, and missing
   data states.
5. If multiple reward tokens are present, the KPI should show the primary total
   and expose a compact token breakdown.
6. `builderRewards` MUST NOT be counted into referral reward KPIs unless a later
   spec explicitly adds builder-code parity.

### FR-5 Referrals Table

1. Desktop MUST render a table with:
   - `Address`
   - `Date Joined`
   - `Total Volume`
   - `Fees Paid`
   - `Your Rewards`
2. Mobile MUST render stacked referral cards with the same fields.
3. Default sort MUST be `Total Volume` descending.
4. Pagination MUST default to 10 rows.
5. Address cells MUST show a shortened address and provide copy behavior or a
   detail link using existing address-link conventions.
6. `Date Joined` MUST format `timeJoined` in the user's local date style while
   preserving raw UTC milliseconds in state.
7. `Total Volume` MUST use `cumVlm`.
8. `Fees Paid` MUST use `cumRewardedFeesSinceReferred`.
9. `Your Rewards` MUST use `cumFeesRewardedToReferrer`.
10. Empty state MUST communicate that there are no referrals yet.

### FR-6 Legacy Reward History

1. The second tab MUST be named `Legacy Reward History`.
2. Desktop MUST render a table with:
   - `Date`
   - `Your Volume`
   - `Your Referral Volume`
   - `Total Rewards Earned`
3. Mobile MUST render stacked history cards with the same fields.
4. Default sort MUST be time descending.
5. Pagination MUST default to 10 rows.
6. Empty state MUST communicate that no rewards were earned in the legacy feed.
7. The UI MUST label this history as legacy data so users do not confuse it with
   current claim records.

### FR-7 Enter Code Modal

1. The modal MUST show a single referral-code input and primary `Enter` action.
2. The code input MUST accept only alphanumeric characters.
3. The code input MUST normalize to uppercase.
4. The code input MUST cap at 20 characters.
5. Submit MUST be disabled when the normalized code is empty.
6. Submit MUST be disabled when `referredBy` is already present.
7. Submit MUST be disabled when the account has crossed the protocol's referral
   entry volume limit.
8. The volume limit check SHOULD use referral payload fields when available and
   fall back to the documented `$10,000` threshold only for explanatory copy.
9. Submit MUST sign and post:

   ```json
   {"type": "setReferrer", "code": "<CODE>"}
   ```

10. The signed action MUST use no vault address.
11. Success MUST close the modal, clear pending join code, show success feedback,
    and refetch referral and user-fee data.
12. Known terminal errors MUST show specific copy and clear pending join code
    when appropriate:
    - cannot self-refer
    - referrer already set
    - referral code not registered
    - account no longer eligible

### FR-8 Create and Share Code Modal

1. When `referrerState.stage` is `needToTrade`, the modal MUST explain the
   remaining or required volume using `referrerState.data.required`.
2. When `referrerState.stage` is `needToCreateCode`, the modal MUST show a
   single referral-code input and primary `Create` action.
3. Create-code input validation MUST match enter-code validation:
   alphanumeric, uppercase, max 20 characters.
4. Submit MUST sign and post:

   ```json
   {"type": "registerReferrer", "code": "<CODE>"}
   ```

5. The signed action MUST use no vault address.
6. Success MUST close the creation state, show share state, and refetch referral
   data.
7. When `referrerState.stage` is `ready`, the modal MUST show:
   - referral code
   - canonical join link
   - copy-code action
   - copy-link action
8. The canonical Hyperopen join link MUST be:

   ```text
   https://<hyperopen-host>/join/<CODE>
   ```

9. The UI MAY additionally display the Hyperliquid link format for parity
   explanation, but Hyperopen copy actions should copy Hyperopen's own join
   link.

### FR-9 Claim Rewards Modal

1. `Claim Rewards` MUST be disabled while loading or when claimable rewards are
   zero.
2. The modal MUST show claimable reward totals and a token breakdown when more
   than one quote asset is present.
3. The modal MUST mention that protocol claims succeed when claimable rewards are
   greater than `$1`.
4. To match reference UX, the button SHOULD be enabled for nonzero claimable
   rewards and let the exchange response provide the final threshold result.
5. Confirm MUST sign and post:

   ```json
   {"type": "claimRewards"}
   ```

6. The signed action MUST use no vault address.
7. Success MUST close the modal, show success feedback, refetch referral data,
   and refresh spot balances.
8. Failure MUST keep the modal open and show the exchange error text.

### FR-10 Join Link Flow

1. `/join/:code` MUST accept the same normalized referral-code format as the
   modal inputs.
2. Invalid join-code paths MUST render a safe referrals page state with an
   invalid-code message.
3. If disconnected, the page MUST preserve the pending code and prompt connect.
4. If connected but agent trading is locked, the page MUST preserve the pending
   code and prompt unlock/enable trading.
5. If connected and eligible, the page MUST submit `setReferrer` once the user
   explicitly confirms. Do not silently sign a transaction.
6. After success, navigate to `/referrals` with the pending code cleared.
7. The join flow MUST never sign for a spectated, vault, or subaccount identity.
8. The join flow MUST display the exact normalized code before signing because
   referral selection is sticky account state.

### FR-11 Fee Schedule Integration

1. The referrals route MUST not create a parallel fee-discount model.
2. The portfolio fee schedule remains the source for displaying
   `activeReferralDiscount` from `userFees`.
3. After `setReferrer` succeeds, Hyperopen MUST refetch `userFees` so portfolio
   fee schedule surfaces can reflect the referral discount.
4. The referrals page MAY show a small status line for the referred user's
   active referral code, but the canonical fee calculation belongs to the
   existing fee schedule surface.

### FR-12 States

1. Loading state MUST show skeleton or stable placeholder content for KPIs and
   the active table.
2. Empty state MUST be explicit for each tab.
3. Error state MUST include a retry action.
4. Disconnected state MUST render the route with connect affordance, not a blank
   screen.
5. Read-only and spectate states MUST render referral data only when loaded and
   disable mutations.
6. Legal/restricted trading state MUST keep the page visible but disable signed
   actions.
7. Stale data MUST not flash for a different address after wallet changes.

### FR-13 Responsiveness and Accessibility

1. Desktop layout MUST keep the action row and KPI cards visible in the first
   viewport.
2. Mobile layout MUST stack the header, actions, KPI cards, and tab panel without
   horizontal overflow.
3. Tables MUST switch to cards on narrow widths if columns cannot remain
   readable.
4. Dialogs MUST use accessible labels, focus trapping, escape close, and focus
   restoration.
5. Copy buttons MUST provide screen-reader feedback and visible copied state.
6. Numeric values MUST include explicit signs or labels where color conveys
   positive/negative meaning.

## Data Requirements

### DR-1 Inputs

Required inputs:
1. Referral info payload from `/info`.
2. `spotMeta` for token ID to symbol mapping.
3. `userFees` for active referral discount cross-checking.
4. Spot balances for post-claim balance refresh.
5. Active wallet and agent-wallet status.
6. Account-context helpers for master, subaccount, vault, and spectate mode.
7. A referral-specific mutation guard that returns a disabled reason unless the
   active signing identity is the connected master account.

### DR-2 Normalized Referral Shape

Recommended state root:

```clojure
[:referrals
 {:loading? boolean
  :error string-or-nil
  :loaded-at-ms number-or-nil
  :loaded-for-address normalized-address-or-nil
  :raw payload
  :referred-by {:referrer address :code string}
  :cum-vlm string
  :token-states [{:token-id number
                  :token-symbol string
                  :cum-vlm string
                  :unclaimed-rewards string
                  :claimed-rewards string
                  :builder-rewards string}]
  :referrer-state {:stage :ready|:need-to-create-code|:need-to-trade
                   :code string-or-nil
                   :required string-or-nil
                   :n-referrals number
                   :referral-states [...]}
  :legacy-reward-history [...]}]
```

Recommended UI root:

```clojure
[:referrals-ui
 {:active-tab :referrals
  :referrals-sort {:column :cum-vlm :direction :desc}
  :legacy-sort {:column :time :direction :desc}
  :page 1
  :page-size 10
  :modal nil
  :pending-code nil
  :form {:code ""}
  :submitting? false
  :last-error nil}]
```

### DR-3 Derived Metrics

1. `traders-referred = referrerState.data.nReferrals` when ready, else `0`.
2. `rewards-earned = sum(claimedRewards + unclaimedRewards)` by token.
3. `claimable-rewards = sum(unclaimedRewards)` by token.
4. Referrals table rows derive from `referrerState.data.referralStates`.
5. Legacy table rows derive from `rewardHistory`.
6. Join-code eligibility derives from `referredBy`, account volume, and exchange
   responses.

### DR-4 Fallback Policy

1. Missing numbers render as `--` in detailed rows and `0` in count-style KPIs.
2. Missing token metadata renders as `Token <id>`.
3. Missing `referrerState` renders as a non-ready state with all mutations
   disabled until data is refreshed.
4. Unknown API fields are preserved under `:raw` and ignored by the view model.

## Technical Plan

### TP-1 New Product Namespaces

Recommended additions:
1. `src/hyperopen/referrals/actions.cljs`
2. `src/hyperopen/referrals/effects.cljs`
3. `src/hyperopen/referrals/normalization.cljs`
4. `src/hyperopen/referrals/vm.cljs`
5. `src/hyperopen/api/endpoints/account/referrals.cljs`
6. `src/hyperopen/api/projections/referrals.cljs`
7. `src/hyperopen/runtime/effect_adapters/referrals.cljs`
8. `src/hyperopen/runtime/action_adapters/referrals.cljs`
9. `src/hyperopen/views/referrals_view.cljs`
10. `src/hyperopen/views/referrals/modals.cljs`
11. `src/hyperopen/views/referrals/tables.cljs`
12. `src/hyperopen/views/referrals/shared.cljs`

### TP-2 Route Wiring

Implementation should update:
1. `shadow-cljs.edn`
   - add `:referrals_route` module for `hyperopen.views.referrals-view`
2. `src/hyperopen/route_modules.cljs`
   - add `:referrals` module ID, export path, and route detection
3. `src/hyperopen/views/header/nav.cljs`
   - add `Referrals` nav item
4. `src/hyperopen/startup/route_refresh.cljs`
   - add route refresh action
5. `src/hyperopen/runtime/action_adapters/navigation.cljs`
   - add route loader effects
6. `src/hyperopen/app/actions.cljs`
   - register referrals actions
7. `src/hyperopen/app/effects.cljs`
   - register referrals effects

### TP-3 API Wiring

Implementation should add:
1. `request-referral!` wrapper under account endpoints.
2. `request-referral!` forwarding through `src/hyperopen/api/gateway/account.cljs`.
3. `request-referral!` export from `src/hyperopen/api/default.cljs`.
4. `request-referral!` account-op wiring in `src/hyperopen/api/instance.cljs`.
5. Projection functions:
   - `begin-referral-load`
   - `apply-referral-success`
   - `apply-referral-error`
6. Runtime effect:
   - `:effects/api-fetch-referral`
7. Route-gated effect logic matching leaderboard and staking patterns.

### TP-4 Signed Action Wiring

Implementation should add exchange helpers:
1. `submit-set-referrer!`
2. `submit-register-referrer!`
3. `submit-claim-rewards!`

All three helpers should route through the existing agent L1 signing path:
`src/hyperopen/api/trading/agent_actions.cljs`.

Required action bodies:

```clojure
{:type "setReferrer" :code code}
{:type "registerReferrer" :code code}
{:type "claimRewards"}
```

Required signing options:
1. `:vault-address nil`
2. normal `expiresAfter` behavior unless exchange testing shows that referrals
   reject expiry metadata
3. mainnet/testnet resolution from the existing agent-action helper

If implementation testing shows any action cannot be submitted by an approved
agent wallet, fallback must be explicit in the ExecPlan and use the existing
user-signed action path only after verifying the official behavior.

### TP-5 Browser Storage

Pending `/join/:code` state may be route/query state or local ephemeral state.
If implementation persists a pending referral code across reloads, follow
`/hyperopen/docs/BROWSER_STORAGE.md` and treat referral codes as non-secret but
user-visible account workflow state.

## Acceptance Criteria

### AC (Phase 1)

1. `/referrals` appears in nav and renders a dedicated page.
2. Route loading, refresh, and wallet changes request referral data for the active
   master account.
3. The page renders title, learn-more link, three actions, three KPI cards, and
   two tabs.
4. `Referrals` and `Legacy Reward History` support empty, loading, error,
   sorted, paginated, desktop, and mobile states.
5. Enter-code modal validates, normalizes, signs `setReferrer`, handles known
   terminal errors, and refetches referral/user-fee data on success.
6. Create/share modal handles all three `referrerState` stages and signs
   `registerReferrer` only in the eligible state.
7. Claim modal signs `claimRewards`, refetches referral data, and refreshes spot
   balances on success.
8. `/join/:code` preserves and confirms a pending code, then applies it through
   the same `setReferrer` path.
9. Referral mutations are disabled for spectate, vault, and subaccount contexts.
10. Existing portfolio fee-schedule referral discount behavior still works.

### AC (Future Parity)

1. Modern reward-claim history is reconstructed from non-funding ledger updates.
2. Builder rewards are displayed in a separate builder-code-aware surface.
3. Referral link analytics or outbound copy telemetry is added if product wants
   acquisition tracking.
4. A richer invite landing experience is added only if it does not replace the
   account-management-first `/referrals` route.

## Test Requirements

Required before implementation is considered complete:
1. Unit tests for referral normalization, token mapping, numeric aggregation,
   stage handling, and code validation.
2. Action tests for:
   - route load
   - tab switching
   - sorting
   - pagination
   - modal open/close
   - form updates
   - submit effects
   - pending join-code lifecycle
3. API tests for exact request bodies:
   - `{"type":"referral","user":"..."}`
   - `{"type":"setReferrer","code":"..."}`
   - `{"type":"registerReferrer","code":"..."}`
   - `{"type":"claimRewards"}`
4. Signed-action tests MUST assert referral payloads are posted without
   `vaultAddress`.
5. Guard tests MUST prove an owned selected subaccount cannot submit
   `setReferrer`, `registerReferrer`, or `claimRewards`.
6. Runtime/effect tests for route gating, stale-address protection, success
   refetches, and error toasts.
7. View tests for disconnected, loading, error, empty, ready, need-to-trade, and
   need-to-create-code states.
8. Playwright coverage for:
   - `/referrals` first render
   - modal validation
   - tab switching
   - mobile layout
   - `/join/:code` pending-code confirmation
9. Required repository gates for code changes:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
10. UI implementation must account for the browser-QA passes required by
   `/hyperopen/docs/BROWSER_TESTING.md`.

## Revision Notes

- 2026-06-08: Updated implementation planning coverage after external reviewer
  comparison. Added explicit `app/effects.cljs`, API gateway/instance, selected
  subaccount mutation guard, and no-`vaultAddress` signed-action test
  requirements.

## Risks and Mitigations

1. Risk: The live web app's referral route behavior changes before
   implementation.
   - Mitigation: refresh the live UI and official docs during the implementation
     ExecPlan before finalizing UI details.
2. Risk: Agent-wallet signing support differs for referral actions.
   - Mitigation: verify on testnet or controlled account before wiring the UI to
     production action buttons.
3. Risk: `tokenToState` grows beyond USDC-only rewards.
   - Mitigation: normalize token IDs and preserve token-aware displays from the
     first implementation.
4. Risk: Subaccount and vault context could accidentally sign for the wrong
   identity.
   - Mitigation: hard-disable mutations unless the active signing identity is
     the master account.
5. Risk: Claim threshold copy diverges from exchange behavior.
   - Mitigation: display the documented `>$1` rule but rely on exchange response
     for final acceptance.

## Release Plan

1. Phase 1: route, read model, KPIs, tables, modals, signed actions, join flow,
   focused tests, and browser QA.
2. Phase 2: non-funding ledger claim history, token breakdown polish, and
   deeper copy/share affordances.
3. Phase 3: optional builder-rewards companion surface if builder-code parity is
   prioritized.
