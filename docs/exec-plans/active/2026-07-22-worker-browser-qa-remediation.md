# Repair release deep links and trade account-panel geometry

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This plan follows `docs/PLANS.md` and `.agents/PLANS.md`.

## Purpose / Big Picture

The deployed Hyperopen testnet site must survive direct navigation and refresh on the Portfolio Optimizer routes, and the desktop trade account panel must keep the same outer geometry while users switch among its seven standard tabs. After the repair, the same Cloudflare Worker will be redeployed, verified, and exercised with the user's testnet wallet using the smallest allowed signed order and transfer amounts.

## Context References

Public refs: the direct user request on 2026-07-22 to repair the browser-QA findings, redeploy, connect a wallet, sign, place a testnet order, and exercise deposit and withdrawal.

Repo artifacts: `tmp/browser-inspection/inspect-2026-07-22T03-27-19-190Z-578f2e74/browser-report.json`, `docs/BROWSER_TESTING.md`, `docs/agent-guides/browser-qa.md`, and `docs/exec-plans/completed/2026-07-21-cloudflare-worker-deployment.md`.

Local scratch refs: none.

## Progress

- [x] (2026-07-22 12:30+08:00) Reproduced direct-route HTTP 404 for `/portfolio/optimize` and `/portfolio/optimize/new`, while confirming in-app navigation loads the optimizer.
- [x] (2026-07-22 12:30+08:00) Captured the reported 1280px account-panel measurements: Balances `960x198`; six other standard tabs `960x146`.
- [x] (2026-07-22 13:05+08:00) Added deterministic Worker and Playwright coverage; observed optimizer deep links return 404 and inner account-table height differs by 52px before implementation.
- [x] (2026-07-22 13:10+08:00) Added a selective optimizer-shell fallback in the Worker and made the desktop account-panel parent explicitly full-height; focused tests now pass.
- [x] (2026-07-22) Corrected Testnet USDC funding-chain projection to use Arbitrum Sepolia when `:is-mainnet` is false, with domain regression coverage for stale Mainnet wallet-chain state.
- [x] (2026-07-22) Corrected the 1024-1279px trade stacking context so funding controls paint above orderbook rows; the deterministic 1116x643 Playwright hit-test passes for Deposit, Transfer, and Withdraw.
- [x] Run focused tests, release Playwright, Cloudflare validation, and repository gates. Release Playwright exited successfully; Worker tests passed 13/13, release-asset tests passed 48/48, and the full gates run completed after compiling all app targets. (The terminal stream did not retain its final gate matrix.)
- [x] Deploy to the existing `hyperopen` Worker and verify public version `2713e467-dd3b-4e29-b320-b338937a8381` at `https://hyperopen.izhenghaocn.workers.dev`.
- [ ] In a browser, complete the user-authorized Testnet transactions after the connected wallet has test ETH for gas and test USDC for funding: wallet connection and Testnet identity are confirmed, and the USDC deposit flow is staged before final submission.
- [x] Repair the live testnet funding-chain projection: with `hyperliquidNetwork=testnet`, USDC deposits must render and submit against Arbitrum Sepolia rather than Arbitrum One.
- [x] Repair the intermediate desktop trade layout where the order book covers the Account Equity funding actions near a 1116px viewport width.
- [ ] Complete final browser QA after funded Testnet transaction verification, clean all sessions, and move this plan to `docs/exec-plans/completed/`.
- [x] (2026-07-22 21:00+08:00) Diagnosed two successful 5 USDC Arbitrum Sepolia transfers that did not credit Hyperliquid Testnet because the release used retired Circle USDC and Bridge2 addresses.
- [x] (2026-07-22 21:24+08:00) Replaced the retired Testnet token and bridge addresses with Hyperliquid's current official USDC2 and Bridge2 contracts, added exact regression coverage, rebuilt, redeployed, and verified public Worker version `22f0460a-f31c-4b52-8f44-038d5afffec0`.
- [ ] Obtain current USDC2 through an authorized source before resuming signed deposit, order, transfer, and withdrawal testing; do not send additional Circle test USDC to any Hyperliquid bridge.
- [x] (2026-07-22 21:25+08:00) Refreshed the connected live Chrome tab and verified the current wallet, realtime Testnet market data, USDC deposit staging on Arbitrum Sepolia, zero-balance withdrawal gating, zero-balance Spot/Perps transfer gating, and order-form staging without submitting a signature.
- [x] (2026-07-23) Reproduced the funded Testnet order rejection and traced the reported missing API Wallet address to L1 actions defaulting to the Mainnet signing domain even while transport correctly targeted Testnet.
- [x] (2026-07-23) Made API Wallet actions inherit the selected Hyperliquid network, passed the 34/34 gate matrix, and deployed the same Worker as version `588a4869-597b-4fd0-b744-42a631b7ec6f`.
- [x] (2026-07-23) Verified a 15 USDC BTC market order filled on Testnet for `0.00023 BTC` at `65040.0`; no open order remains.
- [ ] Complete the staged 1 USDC Perps-to-Spot signature, return the transfer, close the test position, and verify withdrawal.

## Surprises & Discoveries

- Observation: in-app Optimize navigation succeeds even though a direct request fails.
  Evidence: the Header button carries `href="/portfolio/optimize"`; clicking it loads the full optimizer, while direct HTTP requests to `/portfolio/optimize` and `/portfolio/optimize/new` return 404.
- Observation: an existing Playwright regression already asserts stable geometry for all seven tabs at 1280x800 and 1440x900.
  Evidence: `tools/playwright/test/trade-regressions.spec.mjs` contains `desktop trade shell keeps the chart dominant while account tabs stay geometry-stable`.
- Observation: a connected MetaMask wallet on Arbitrum Sepolia (chain `421614`) still renders the live testnet funding modal as `From the Arbitrum network`.
  Evidence: on `https://hyperopen.izhenghaocn.workers.dev/trade?hyperliquidNetwork=testnet`, after switching MetaMask to the added Arbitrum Sepolia network and reconnecting, the visible modal retained `USDC / Arbitrum` rather than `Arbitrum Sepolia`.
- Resolution: the pre-deployment page retained a stale release module. After version `2713e467-dd3b-4e29-b320-b338937a8381` was deployed and the user tab was refreshed, the asset picker and `Deposit USDC` modal both rendered `Arbitrum Sepolia`; the modal states `From the Arbitrum Sepolia network` and preserves the 5 USDC minimum.
- Observation: at a 1116x643 Chrome viewport, the order-book depth body intercepts the funding action controls.
  Evidence: the Deposit button rect is `x=809 y=313.5 w=295 h=34`, while `document.elementFromPoint` at its center returns an order-book level value. At 1440px, the same point resolves to the Deposit control.
- Observation: the originally reported measurements were for the inner `account-tables` shell, not the governed outer `trade-account-tables-panel` shell.
  Evidence: direct production DOM measurement showed the outer shell remained `960x288` for every tab, while the inner shell was `960x198` for Balances and `960x146` for the other tabs.
- Observation: the deployed Testnet funding chain used a retired token/bridge pair even though its chain label correctly said Arbitrum Sepolia.
  Evidence: transactions `0x7ed263e28e0f6c9681d28a2f4884ae979b92c299d2b04553b792d0fd494a2e38` and `0x0aa8705330b43a525d52edf9ef1c036c20ac7907454d1228a72563a401feb2f6` each transferred 5 units of Circle test USDC `0x75faf114eafb1bdbe2f0316df893fd58ce46aa4d` to retired bridge `0xccd552b49b4383aa0a4f45689de3e29f142fa3ad`. Both receipts succeeded, but `clearinghouseState` and `userNonFundingLedgerUpdates` remained empty.
- Observation: Hyperliquid's current official Testnet contracts differ from the release configuration.
  Evidence: the official Bridge2 documentation names Testnet USDC2 `0x1baAbB04529D43a73232B713C0FE471f7c7334d5`, chain ID `421614`, and Testnet Bridge2 spender `0x08cfc1B6b2dCF36A1480b99353A354AA8AC56f89`. The USDC2 contract is verified on Arbitrum Sepolia and reports 6 decimals.
- Observation: the official Testnet faucet cannot currently fund this wallet.
  Evidence: Hyperliquid documents that `/drip` requires a prior mainnet deposit from the same address; both mainnet `clearinghouseState` and mainnet non-funding ledger updates are empty for `0x36A47878219fB346e031f6cf82CBFC8C77E35932`.
- Observation: the repaired live release stages a 5 USDC deposit on Arbitrum Sepolia, while current on-chain balances remain 10 legacy Circle test USDC, 0 USDC2, and 0 Hyperliquid Testnet USDC.
  Evidence: the refreshed connected Chrome tab identifies `0x36a4...5932`, the deposit modal says `From the Arbitrum Sepolia network`, the release JavaScript contains only the current USDC2/Bridge2 pair for chain `421614`, and direct read-only RPC/API queries return zero for USDC2 and the clearinghouse account.
- Observation: withdrawal and Spot/Perps transfer correctly block submission at zero balance, but the order form enables `Place Order` after a 5 USDC size is entered even though `Available to Trade` is 0.00 USDC.
  Evidence: live Chrome DOM inspection showed `0 USDC available` plus a disabled Withdraw button, `MAX: 0.00 USDC` plus a disabled Transfer button, and an enabled Place Order button with an approximately 0.23 USDC margin requirement. No order was submitted.
- Observation: after the faucet credited 999 Testnet USDC, a staged order reached the Testnet exchange but was rejected as `User or API Wallet 0x51100660082bfd8cb4dae279d0ef5494c49f8edc does not exist.`
  Evidence: `normalize-agent-action-options` defaulted `:is-mainnet` to `true`; the trade submit path did not pass an override, so Testnet requests were signed with the Mainnet L1 source and Hyperliquid recovered the wrong signer address.

## Decision Log

- Decision: keep all live financial actions on Hyperliquid Testnet and use the smallest platform-valid amounts.
  Rationale: the user explicitly authorized signing and fund movements, but did not authorize mainnet asset risk.
  Date/Author: 2026-07-22 / Codex.
- Decision: verify the existing geometry test before changing `/src/**`.
  Rationale: the source already declares a fixed grid row and an existing regression may reveal that the prior measurement was a release-only problem or an inaccurate probe.
  Date/Author: 2026-07-22 / Codex.
- Decision: stabilize the inner account-table shell as well as preserving the already-stable outer shell.
  Rationale: the user asked to repair the reported inconsistency; adding `lg:h-full` to the desktop parent supplies the containing height needed by the existing child `h-full` contract without affecting mobile layout.
  Date/Author: 2026-07-22 / Codex.
- Decision: implement the optimizer deep-link repair as a narrow Worker fallback instead of enumerating unbounded scenario HTML files.
  Rationale: optimizer scenario IDs are dynamic. Retrying only recognized GET/HEAD optimizer paths against `/portfolio` preserves unknown-route 404 behavior and uses the already-generated Portfolio application shell.
  Date/Author: 2026-07-22 / Codex.
- Decision: stop all additional signed deposits until the Testnet contract pair is corrected and a genuine USDC2 balance is available.
  Rationale: Circle test USDC and USDC2 are distinct ERC-20 contracts. A successful Circle USDC transfer to the retired bridge does not create a Hyperliquid credit, so retrying would only strand more test assets.
  Date/Author: 2026-07-22 / Codex.
- Decision: keep the repair limited to the existing Arbitrum Sepolia entry in `chain-config-by-id` and add explicit address assertions.
  Rationale: chain selection, wallet switching, transfer encoding, and receipt handling already behaved correctly. The defect is stale protocol configuration, and a narrow fix reduces regression risk.
  Date/Author: 2026-07-22 / Codex.
- Decision: default API Wallet L1 signing to `runtime-state/hyperliquid-network` while preserving an explicit `:is-mainnet` option.
  Rationale: transport, websocket, wallet approval, and user-signed actions already derive from the startup network selection. The agent signer must use the same source of truth so every omitted-option call remains correct on Testnet and Mainnet.
  Date/Author: 2026-07-23 / Codex.

## Outcomes & Retrospective

Focused implementation and the corrected Worker release are complete. The deployed Worker remains `https://hyperopen.izhenghaocn.workers.dev`, now at version `22f0460a-f31c-4b52-8f44-038d5afffec0`. Public `verify:deployment-headers` passed for the Trade document and release assets; `verify:cloudflare-worker` passed both read-only mainnet and testnet fee probes; `/trade` and the optimizer deep links return 200 while an unknown route retains 404. The refreshed live Chrome testnet page has the connected `0x36a4...5932` account and correctly presents USDC funding on Arbitrum Sepolia. The remaining acceptance criteria require an authorized source of current USDC2, then explicit per-transaction wallet confirmations for funding, order placement, transfer, and withdrawal.

## Context and Orientation

`tools/release-assets/generate_release_artifacts.mjs` emits one HTML file per public metadata route. It emits `portfolio.html` but not HTML for nested optimizer URLs. `workers/hyperopen-worker.mjs` delegates ordinary page requests to the Cloudflare Static Assets binding and currently has no selective application-shell fallback. `src/hyperopen/views/trade_view/layout_state.cljs` defines desktop grid rows, while `src/hyperopen/views/trade_view/shell.cljs` mounts the account panel. `tools/playwright/test/trade-regressions.spec.mjs` owns deterministic trade geometry assertions.

## Plan of Work

First, add Worker tests that model a 404 from the asset binding and require known optimizer paths to retry against `portfolio.html` while unknown routes remain 404. Run the focused Worker tests and confirm the new assertion fails. Then implement a narrow, GET/HEAD-only fallback in the Worker. Separately, run the existing desktop geometry Playwright regression against the current source. If it fails, make the smallest layout correction under `src/hyperopen/views/trade_view/**`; if it passes, reproduce against the release artifact or public Worker and adjust only the release-specific cause backed by that evidence.

After focused verification, run release Playwright, rebuild the Cloudflare artifact, run preflight and dry-run checks, and execute `npm run gates`. Deploy only after these pass. Capture the exact version ID, run both public verifiers, and then use the browser for the authorized testnet wallet flows. Wallet prompts and provider UI are manual-exception surfaces; use the user's existing browser state and stop if any prompt identifies mainnet or real assets.

## Concrete Steps

Run all commands from `/Users/zh/Documents/Hyperopen`:

    npm run setup:worktree
    npm run test:cloudflare-worker
    npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "desktop trade shell keeps"
    npm run test:release-assets
    npm run test:playwright:seo
    npm run build:cloudflare
    node .agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs --artifact
    npm run cloudflare:check
    env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH NODE_OPTIONS=--localstorage-file=/tmp/hyperopen-node-gates-localstorage npm run gates
    npx wrangler whoami
    env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH npm run deploy:cloudflare

## Validation and Acceptance

The Worker tests must prove optimizer deep links receive the Portfolio shell only when the original asset response is 404 and the method is GET or HEAD. Unknown routes must stay 404. The desktop geometry test must show less than or equal to one pixel difference in account height and width across all seven tabs at 1280 and 1440 widths. Public verification must show HTTP 200 for `/portfolio/optimize` and `/portfolio/optimize/new`, retain HTTP 404 for an unknown route, and pass both repository deployment verifiers. The wallet portion is accepted only when the UI visibly identifies testnet, the wallet connects, requested signatures complete, an order reaches an accepted/open/filled state, and deposit and withdrawal each reach an observable testnet success state.

## Idempotence and Recovery

Builds, tests, preflight, and dry-run commands are repeatable. The deployment command creates a new Worker version; if public verification fails, stop financial testing and use Cloudflare's prior version only after explicit rollback authorization. Testnet orders should be canceled if left open. Do not delete the Worker or change DNS.

## Artifacts and Notes

The initial browser report is `tmp/browser-inspection/inspect-2026-07-22T03-27-19-190Z-578f2e74/browser-report.json`. New screenshots and browser reports belong under `tmp/browser-inspection/**`.

## Interfaces and Dependencies

Keep the existing Cloudflare `ASSETS.fetch(request)` binding and add no external dependency. Reuse Playwright's existing `data-parity-id` and `data-role` anchors. Preserve direct development endpoints and all public application APIs.

Revision note: created on 2026-07-22 to capture the user-requested remediation, deployment, and authorized Hyperliquid Testnet transaction verification.

Revision note: updated on 2026-07-22 after focused RED/GREEN validation to record the inner-versus-outer geometry distinction and the selected narrow fixes.

Revision note: updated on 2026-07-22 after live signed Testnet deposits exposed retired Circle USDC and Bridge2 addresses. The plan now requires the official USDC2/Bridge2 pair, explicit regression coverage, redeployment, and an authorized USDC2 source before further wallet transactions.

Revision note: updated on 2026-07-22 after the official USDC2/Bridge2 configuration passed the complete ClojureScript test suite (5813 tests, 32348 assertions, zero failures). Release validation and deployment remain pending.

Revision note: updated on 2026-07-22 after deployment of Worker version `22f0460a-f31c-4b52-8f44-038d5afffec0`, successful public verification, and live Chrome validation of the repaired funding route and zero-balance transaction gates.

Revision note: updated on 2026-07-23 after funded live Testnet submission exposed a Mainnet-default agent-signing bug. The plan now tracks network-derived L1 signing, redeployment, and completion of signed Testnet transaction verification.

Revision note: updated on 2026-07-23 after Worker version `588a4869-597b-4fd0-b744-42a631b7ec6f` passed public verification and the repaired signer produced a real Testnet BTC fill. Wallet-confirmed transfer and withdrawal remain in progress.
