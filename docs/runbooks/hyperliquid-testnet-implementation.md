---
owner: platform
status: runbook
last_reviewed: 2026-07-24
review_cycle_days: 30
source_of_truth: false
---

# Hyperliquid Testnet Implementation Runbook

## Purpose

This runbook explains how to build, deploy, operate, and verify HyperOpen's
Hyperliquid Testnet integration. It is intended for a developer taking over
the project or rebuilding the implementation from a fresh checkout.

It covers the supported end-to-end path:

1. Select Hyperliquid Testnet at page load.
2. Connect an Arbitrum Sepolia wallet.
3. Deposit current Testnet USDC2 through Bridge2.
4. Enable an API-wallet trading session.
5. Place and close a small perp order.
6. Transfer USDC between Perps and Spot.
7. Withdraw USDC to Arbitrum Sepolia and verify the actual ERC-20 transfer.

Do not use this runbook to operate Mainnet with real funds. Testnet actions
still require explicit user approval in the wallet and must not be automated
through a wallet-extension user interface.

## Project Retrospective

The integration was completed through a sequence of increasingly realistic
checks. This order matters because a successful build or wallet transaction
does not prove that the complete protocol flow is correct.

| Phase | Work completed | Main lesson |
|---|---|---|
| Baseline and release | Preserved the upstream application, added the Cloudflare Static Assets Worker and same-origin HyperUnit proxy, then deployed to the existing Worker origin. | Keep Cloudflare behavior as a release adapter; do not fork ordinary local-development behavior. |
| Testnet transport | Added one startup network contract for `/info`, `/exchange`, WebSocket, signing chain ID, and Hyperliquid chain name. | Endpoint selection and EIP-712 metadata must change together. |
| Release security | Added Testnet REST/WebSocket origins to generated CSP and preconnect metadata, with deployment-header verification. | Source configuration can be correct while a deployed browser is still blocked by release headers. |
| Funding correction | Replaced retired Circle test-USDC/Bridge2 values with the current USDC2 and Bridge2 contracts. | An on-chain transaction with status `1` does not prove Hyperliquid credited the account. |
| Wallet identity verification | Compared the connected address, signed-action destination, and API state after an unexpected address appeared in an external faucet flow. | Verify address at every boundary before assuming malware or interception. No application-side address substitution was found. |
| Signed trading | Corrected Testnet API-wallet signing metadata, enabled trading, opened a minimal BTC position, and closed it completely. | Owner signatures and local agent signatures are separate lifecycles and need separate recovery paths. |
| Account transfers | Exercised Perps -> Spot and Spot -> Perps with API verification. | Page state can be stale; use `spotClearinghouseState` as the settlement source of truth. |
| Withdrawal | Submitted minimum USDC withdrawals, verified Perps debits, and then followed Bridge2 ERC-20 events to the wallet. | “Submitted” is not “delivered”; external settlement requires a second verification stage. |
| Operational defects | Reproduced a stale local agent session, a hidden Mainnet fallback, duplicate-withdrawal risk, delayed delivery, and an inaccurate zero-fee label. | Real browser testing uncovered integration defects that deterministic protocol tests could not expose alone. |

The latest recorded release verification for this work reported:

- Worker origin: `https://hyperopen.izhenghaocn.workers.dev`
- Worker version: `588a4869-597b-4fd0-b744-42a631b7ec6f`
- `npm test`: 5,814 tests and 32,350 assertions passed
- `npm run gates`: 34/34 passed
- release SEO Playwright: 6/6 passed

These counts are historical evidence, not permanent acceptance thresholds.
Future changes must use the current repository gates and record their own
results.

## Operational Model

HyperOpen is a ClojureScript single-page application. The browser owns wallet
connection, EIP-712 signatures, and agent private-key material. The Cloudflare
Worker serves static release assets and provides only the same-origin HyperUnit
proxy; it never stores wallet credentials or signs trading actions.

| Concern | Primary owner | Relevant paths |
|---|---|---|
| Network selection | Pure application configuration | `src/hyperopen/config.cljs` |
| Signed user actions | Trading API boundary | `src/hyperopen/api/trading/user_actions.cljs` |
| API-wallet order signing | Local agent-session boundary | `src/hyperopen/wallet/agent_session.cljs`, `src/hyperopen/api/trading/agent_actions.cljs` |
| Funding policy and request construction | Funding domain/application | `src/hyperopen/funding/domain/**`, `src/hyperopen/funding/application/**` |
| Arbitrum/Bridge2 addresses | Funding infrastructure configuration | `src/hyperopen/funding/effects/common.cljs` |
| Release asset rewrite and Worker routing | Cloudflare deployment boundary | `workers/hyperopen-worker.mjs`, `tools/cloudflare/**`, `wrangler.jsonc` |
| Browser evidence | Deterministic tests and exploratory inspection | `playwright*.config.mjs`, `tools/browser-inspection/**` |

Keep domain logic pure. Network calls, wallet operations, browser storage, and
timers belong in effect interpreters or infrastructure boundaries, as defined
in [ARCHITECTURE.md](/hyperopen/ARCHITECTURE.md).

## Network Contract

The deployment intentionally remains Mainnet by default. Testnet is selected
once during page initialization and is not a live toggle.

| Network | Selector | Wallet chain | Hyperliquid chain | REST / WebSocket |
|---|---|---|---|---|
| Mainnet | no valid selector or `mainnet` | Arbitrum One (`0xa4b1`) | `Mainnet` | `api.hyperliquid.xyz` |
| Testnet | `testnet` | Arbitrum Sepolia (`0x66eee`, decimal `421614`) | `Testnet` | `api.hyperliquid-testnet.xyz` |

Use the explicit Testnet URL for every live Testnet session:

```text
https://<worker-origin>/trade?hyperliquidNetwork=testnet
```

For example, the current Workers release is:

```text
https://hyperopen.izhenghaocn.workers.dev/trade?hyperliquidNetwork=testnet
```

The query parameter has precedence over
`globalThis.HYPEROPEN_HYPERLIQUID_NETWORK`. Invalid or absent values fall back
to Mainnet. This fallback is a frequent source of false balance reports: a
page opened without `hyperliquidNetwork=testnet` can show a valid Mainnet
balance for the same address.

The selector resolves a single coherent contract containing `:info-url`,
`:exchange-url`, `:ws-url`, `:is-mainnet`, `:signature-chain-id`, and
`:hyperliquid-chain`. REST requests, WebSocket startup, and EIP-712 signing
must all consume this same contract. Never hardcode a Testnet endpoint in only
one of those paths.

## Wallet and Asset Preconditions

Before live testing, verify all of the following in the wallet:

- The connected address is the intended test wallet, written as
  `<wallet-address>` in commands below.
- The wallet network is **Arbitrum Sepolia** (`421614` / `0x66eee`).
- The wallet has a small amount of Sepolia ETH for ordinary on-chain deposit
  approval or transfer gas.
- The wallet has imported the current Testnet USDC2 contract, not only a
  similarly named older test token.

Current Testnet funding contracts are defined in
`src/hyperopen/funding/effects/common.cljs`:

| Item | Value |
|---|---|
| USDC2 | `0x1baAbB04529D43a73232B713C0FE471f7c7334d5` |
| Bridge2 | `0x08cfc1B6b2dCF36A1480b99353A354AA8AC56f89` |
| RPC | `https://sepolia-rollup.arbitrum.io/rpc` |
| Explorer | `https://sepolia.arbiscan.io` |
| USDC decimals | `6` |

MetaMask can display multiple assets named `USDC`. Import the exact USDC2
contract address and do not infer the balance from a different token with the
same symbol. The external wallet balance and Hyperliquid Perps balance are
different ledgers.

## Local Development

Run commands from `/Users/zh/Documents/Hyperopen`.

```bash
npm run setup:worktree
npm run dev
```

Open `http://localhost:8080/trade?hyperliquidNetwork=testnet`. The development
server uses direct protocol endpoints; production-only HyperUnit proxy
rewriting happens in `npm run build:cloudflare` and must not be copied into the
normal development path.

For an external connectivity check that cannot create orders or access wallet
data, run:

```bash
npm run smoke:hyperliquid:testnet
```

The smoke checks Testnet `/info`, an inert rejected `/exchange` request, and
an `allMids` WebSocket subscription. It is intentionally opt-in because the
provider is external and variable.

## Signed Action Design

There are two signing paths. Do not collapse them into one generic signer.

| Flow | Signer | Action examples | Safety rule |
|---|---|---|---|
| User-signed action | Connected wallet EIP-712 signature | `usdClassTransfer`, `withdraw3`, API-wallet approval | Require explicit wallet approval for each action. |
| Agent-signed action | Locally generated API-wallet private key | orders, cancels, leverage changes | The private key stays in browser storage according to the selected session policy. |

`approveAgent` creates an API-wallet authorization on Hyperliquid. The local
agent key is generated in the browser; only its public address is sent in the
approval action. Agent actions use the local key after the approval succeeds.

The user-action boundary validates the selected wallet chain before loading
cryptography or calling the exchange. A Testnet page with an Arbitrum One
wallet, or a Mainnet page with an Arbitrum Sepolia wallet, must fail locally
with a clear network mismatch message.

Never log or commit any of the following:

- private keys or browser storage contents
- EIP-712 signatures or full signed exchange bodies
- real wallet addresses unless required in an incident record
- browser cookies, extension data, or wallet recovery material

## End-to-End Test Procedure

Use small disposable Testnet amounts. Stop for manual wallet confirmation at
every signature request. Do not attempt to solve captchas automatically or
click the wallet-extension confirmation UI programmatically.

### 1. Connect and Select Testnet

1. Open the explicit Testnet URL.
2. Connect the intended wallet.
3. Verify the header's shortened address matches the wallet.
4. Confirm the wallet is on Arbitrum Sepolia before pressing Deposit, Transfer,
   Enable Trading, or Withdraw.

If the page shows an unexpected small Mainnet balance, inspect the address bar
first. Reload with `?hyperliquidNetwork=testnet`; switching the wallet network
alone does not change an already initialized application network.

### 2. Deposit USDC2

1. Select **Deposit** and then **USDC**.
2. Confirm the visible source network is Arbitrum Sepolia and the contract is
   the USDC2 value above.
3. Enter at least the displayed minimum and approve/submit the wallet request.
4. Wait for the Bridge2 credit, then verify the Testnet clearinghouse state.

The app uses the current USDC2/Bridge2 pair. Earlier Circle test-USDC and
retired Bridge2 addresses can produce successful Arbitrum transactions that do
not credit Hyperliquid Testnet. Treat a successful wallet receipt as
insufficient until the clearinghouse balance changes.

### 3. Enable Trading and Test an Order

1. From the connected-account menu, select **Enable Trading**.
2. Confirm the wallet's API-wallet approval signature.
3. Verify `extraAgents` contains the newly authorized agent address.
4. Place a minimal market order, then use **Reduce** or **Close** to flatten it.
5. Verify no positions and no open orders remain.

The UI can enter a stale local-session state: it may show a ready agent while
the private key is not available to the agent-action signer. The safe recovery
is to use the account menu's **Trading settings**, change the session
persistence mode, then select **Enable Trading** and approve a new agent. Do
not reuse a missing or unknown agent private key.

### 4. Transfer Perps and Spot USDC

1. Select **Perps <-> Spot**.
2. Submit a small **Perps -> Spot** transfer and manually approve the wallet
   signature.
3. Verify `spotClearinghouseState` reports the amount.
4. Submit the reverse **Spot -> Perps** transfer and verify that Spot falls
   back to the expected balance.

This path creates the user-signed `usdClassTransfer` action. A correct Testnet
signature requires both `signatureChainId: "0x66eee"` and
`hyperliquidChain: "Testnet"`.

### 5. Withdraw USDC and Verify Delivery

1. Select **Withdraw**, choose USDC, and confirm the destination address.
2. The current UI enforces a `5 USDC` minimum.
3. Submit the `withdraw3` wallet signature.
4. First verify the Perps balance changed. Then verify the external USDC2
   balance and `Transfer` event on Arbitrum Sepolia.
5. Do not submit a second request while the first is pending solely because the
   page says “submitted”; wait for the external-chain verification.

The current implementation displays a fixed approximate delivery time and a
`Network fee: None` label. Real Testnet testing observed delayed payouts and
an observed output of `4 USDC` for each `5 USDC` withdrawal request. The
Arbitrum receipt transferred only `4 USDC` from Bridge2 and did not contain a
separate `1 USDC` ERC-20 transfer. This means the difference occurs before the
Bridge2 payout, not as wallet gas. Until the upstream fee policy is documented
and surfaced by a reliable API, treat the display as inaccurate and disclose
the possible difference before testing.

## Verification Commands

Use read-only API and RPC calls. Replace `<wallet-address>` and placeholders;
never put a private key, signature, or a valid exchange action in a shell
command.

### Hyperliquid Testnet Account State

```bash
curl -sS https://api.hyperliquid-testnet.xyz/info \
  -H 'Content-Type: application/json' \
  --data '{"type":"clearinghouseState","user":"<wallet-address>"}'

curl -sS https://api.hyperliquid-testnet.xyz/info \
  -H 'Content-Type: application/json' \
  --data '{"type":"spotClearinghouseState","user":"<wallet-address>"}'

curl -sS https://api.hyperliquid-testnet.xyz/info \
  -H 'Content-Type: application/json' \
  --data '{"type":"openOrders","user":"<wallet-address>"}'

curl -sS https://api.hyperliquid-testnet.xyz/info \
  -H 'Content-Type: application/json' \
  --data '{"type":"userFills","user":"<wallet-address>"}'

curl -sS https://api.hyperliquid-testnet.xyz/info \
  -H 'Content-Type: application/json' \
  --data '{"type":"extraAgents","user":"<wallet-address>"}'
```

Successful cleanup after the order test requires `assetPositions: []` and an
empty `openOrders` array. Do not treat the page alone as the source of truth.

### Arbitrum Sepolia USDC2 Balance

The `balanceOf` call uses ERC-20 selector `0x70a08231`. Substitute the
lowercase 40-hex-character address, without the leading `0x`, after the
selector and left-pad it to 32 bytes.

```bash
curl -sS https://sepolia-rollup.arbitrum.io/rpc \
  -H 'Content-Type: application/json' \
  --data '{
    "jsonrpc":"2.0",
    "id":1,
    "method":"eth_call",
    "params":[{
      "to":"0x1baAbB04529D43a73232B713C0FE471f7c7334d5",
      "data":"0x70a082310000000000000000000000<40-hex-wallet-address>"
    },"latest"]
  }'
```

The hexadecimal result is micro-USDC because USDC2 uses six decimals. For
withdrawal confirmation, query `eth_getLogs` for the ERC-20 `Transfer` topic
and the wallet as the indexed recipient. The source should be the Testnet
Bridge2 contract:

```text
Transfer topic: 0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef
Bridge2:        0x08cfc1B6b2dCF36A1480b99353A354AA8AC56f89
USDC2:          0x1baAbB04529D43a73232B713C0FE471f7c7334d5
```

An Arbitrum receipt's native-ETH gas is paid by the Bridge2 relayer, not by the
wallet requesting a `withdraw3` action. It does not explain an output amount
that differs from the Hyperliquid balance decrease.

## Cloudflare Release Procedure

Use the repository-local `deploy-hyperopen-cloudflare` workflow for release
changes and deployments. It preserves direct local-development endpoints,
rewrites only generated release assets, and performs safe Worker verification.

For a release already approved by the user:

```bash
npm run setup:worktree
npm run test:cloudflare-worker
npm run test:release-assets
npm run test:playwright:seo
npm run build:cloudflare
node .agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs --artifact
npm run cloudflare:check
npm run gates
npm run deploy:cloudflare
```

Run `npx wrangler whoami` immediately before deployment and record the exact
origin and Worker version returned by Wrangler. Publicly verify that exact
origin with:

```bash
HYPEROPEN_VERIFY_ORIGIN=https://<worker-origin> npm run verify:deployment-headers
HYPEROPEN_VERIFY_ORIGIN=https://<worker-origin> npm run verify:cloudflare-worker
```

Do not deploy merely to inspect or diagnose an existing Worker. Use
`npm run cloudflare:check` for a dry run. Deployment and rollback require
explicit user authorization.

## Required Validation

For code changes, run the governed matrix:

```bash
npm run gates
```

For a browser-flow or UI change, first run the smallest relevant committed
Playwright test, then broaden coverage as appropriate. Use Browser MCP only
for exploratory live-wallet inspection and always stop created sessions with:

```bash
npm run browser:cleanup
```

For a documentation-only change, at minimum run:

```bash
npm run lint:docs
npm run lint:docs:test
```

## Acceptance Checklist

- [ ] Testnet page URL includes `hyperliquidNetwork=testnet` before initial load.
- [ ] Wallet chain, signature chain, REST endpoint, and WebSocket endpoint all
      resolve to Testnet.
- [ ] The wallet displays the current USDC2 contract, not only an older USDC
      token with the same symbol.
- [ ] Deposit is verified by `clearinghouseState`, not merely by an L1 receipt.
- [ ] `extraAgents` contains a fresh API-wallet address after enablement.
- [ ] A small order is filled and the account is flattened afterward.
- [ ] Perps/Spot transfer is verified in both directions by API state.
- [ ] Withdrawal is verified by both Perps balance movement and an Arbitrum
      Sepolia USDC2 `Transfer` event.
- [ ] Actual delivered amount and any discrepancy are recorded; no UI fee label
      is treated as authoritative without external verification.
- [ ] Browser sessions created for testing are closed.

## Known Findings and Follow-up Work

| Finding | Impact | Required follow-up |
|---|---|---|
| Mainnet is the no-selector fallback. | Users can view the wrong ledger for the same wallet address. | Make the selected network visible in the UI or provide a deliberate network switch flow. |
| Agent state can be ready while local signing credentials are absent. | Order submit fails with `Agent session unavailable`. | Reproduce with deterministic browser-storage tests and make state reflect signer availability. |
| A direct USDC withdrawal can be marked submitted before external delivery. | Users may retry and create additional withdrawals. | Add durable withdrawal lifecycle/status tracking and disable duplicate submission while settlement is unresolved. |
| Testnet `5 USDC` withdrawals were observed to deliver `4 USDC`. | UI says no network fee but delivered amount differs. | Obtain the upstream fee policy, expose the actual fee/expected output, and add an integration regression around the displayed value. |
| Multiple tokens can share the `USDC` symbol in MetaMask. | Users may conclude a payout is missing. | Show the exact USDC2 contract address in the withdrawal completion UI and documentation. |

## Related Documents

- [Architecture Map](/hyperopen/ARCHITECTURE.md)
- [Browser Testing](/hyperopen/docs/BROWSER_TESTING.md)
- [Agent Trading Rollout](/hyperopen/docs/runbooks/agent-trading-rollout.md)
- [Cloudflare Worker Browser-QA Remediation Plan](/hyperopen/docs/exec-plans/active/2026-07-22-worker-browser-qa-remediation.md)
- [Hyperliquid Testnet Network Default Plan](/hyperopen/docs/exec-plans/completed/2026-07-22-hyperliquid-testnet-network-default.md)
- [Hyperliquid Protocol Reference](/hyperopen/docs/references/hyperliquid-protocol.md)
