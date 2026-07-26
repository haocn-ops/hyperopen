# Wallet Provider Hardening for Coinbase and Multi-Wallet Desktops

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan follows `.agents/PLANS.md` and exists because the maintainer directly requested an execution plan and implementation on 2026-05-23 after a user reported that Coinbase Wallet could not connect or enable trading on desktop while MetaMask worked.

## Purpose / Big Picture

Desktop wallet users should be able to connect and enable trading with Coinbase Wallet, MetaMask, or another injected Ethereum wallet without Hyperopen accidentally switching providers between connection and signature approval. Today Hyperopen relies on whichever object is currently exposed as `window.ethereum` or `globalThis.ethereum`. That is brittle when several wallet extensions are installed because browsers and wallet extensions can expose more than one provider and the singleton provider can be overwritten.

After this change, Hyperopen discovers multiple EIP-1193 wallet providers, lets the user choose a provider when more than one is available, remembers that provider for the current page session, and uses the same provider for connection, chain lookup, typed-data signing, deposits, and provider event listeners. A human can see the behavior by installing a simulated multi-provider wallet in tests, choosing Coinbase, and observing that the signature request goes to Coinbase even if MetaMask is the global singleton.

## Context References

Public refs:
- Direct maintainer request in this Codex thread on 2026-05-23.

Repo artifacts:
- `AGENTS.md` in this repository requires an ExecPlan for complex feature work and requires the gates `npm run check`, `npm test`, and `npm run test:websocket` when code changes.
- `docs/PLANS.md` and `.agents/PLANS.md` define this plan format.
- `docs/qa/agent-wallet-manual-matrix.md` currently lists MetaMask software and hardware accounts but does not list Coinbase Wallet.

External standards, summarized here so the plan is self-contained:
- EIP-1193 is the common JavaScript provider interface exposed by Ethereum wallets. Hyperopen already calls `provider.request({method, params})`, which is the important part of that interface.
- EIP-6963 is a browser-event discovery standard for multiple injected wallet providers. Wallets announce themselves through an `eip6963:announceProvider` event containing user-facing metadata and a provider object. Dapps request announcements through `eip6963:requestProvider`.
- Some legacy wallets also expose an array at `window.ethereum.providers`. Coinbase Wallet can be identified in that legacy array by provider flags such as `isCoinbaseWallet`; MetaMask commonly exposes `isMetaMask`.

Local scratch refs:
- None.

## Progress

- [x] (2026-05-23T22:27:09Z) Captured the active ExecPlan with scope, acceptance criteria, and implementation approach.
- [x] (2026-05-23T22:31Z) Added failing tests proving Coinbase selection, selected-provider signing, header provider actions, and connect action payloads.
- [x] (2026-05-23T22:35Z) Implemented provider discovery and selected-provider ownership in `src/hyperopen/wallet/provider_registry.cljs`.
- [x] (2026-05-23T22:36Z) Wired connection, post-connect chain lookup, event listeners, typed-data signing, and provider access through the selected provider.
- [x] (2026-05-23T22:37Z) Added a compact header provider menu for multi-wallet desktops.
- [x] (2026-05-23T22:41Z) Extended simulator and manual QA coverage for Coinbase and multi-provider desktops, including `eth_chainId` simulator support.
- [x] (2026-05-23T22:47Z) Ran focused browser coverage and required gates where possible; recorded evidence below.

## Surprises & Discoveries

- Observation: The existing connection path uses `window.ethereum` through `hyperopen.wallet.core/provider`, but the typed-data signing path uses `globalThis.ethereum` directly.
  Evidence: `src/hyperopen/wallet/core.cljs` defines `provider` from `window.ethereum`, while `src/hyperopen/utils/hl_signing.cljs` obtains the signing provider from `globalThis.ethereum`.

- Observation: The debug wallet simulator did not implement `eth_chainId`, so browser evidence initially showed `chainId = null` even after the production connection path requested the chain id.
  Evidence: `wallet-enable-trading-simulated` passed before app recompilation but reported `chainId: null`; adding simulator `eth_chainId` support and recompiling produced a passing run with `chainId: "0xa4b1"`.

- Observation: `npm run check` is currently blocked by an unrelated stale documentation gate.
  Evidence: the command stopped at `lint:docs` with `[stale-doc] docs/product-specs/portfolio-page-parity-prd.md - document is stale: 93 days old, max allowed 90`.

- Observation: The live Coinbase report did not hang at signature submission; Coinbase returned the signature and Hyperliquid rejected the action because the connected account had not deposited yet.
  Evidence: querying the live Shadow nREPL session after the user clicked Sign showed wallet state `:agent {:status :error, :error "Must deposit before performing actions. User: 0x154f3ebeb01616b168de3a966d3b4948cd8646f2"}`.

- Observation: The Coinbase modal showed `domain.chainId: 1` because the approval path inherited the wallet extension network id from the connected provider.
  Evidence: the user-provided signature payload had `domain.chainId: 1`, while Hyperliquid mainnet approvals should default to the Arbitrum signing id `0xa4b1` unless explicitly overridden.

## Decision Log

- Decision: Implement a small internal provider registry instead of introducing Wagmi, RainbowKit, Web3Modal, or another wallet UI framework.
  Rationale: Hyperopen is a ClojureScript/Replicant application with an existing EIP-1193 path. A focused boundary addresses the bug with less dependency and UI blast radius than adding a full web3 connection framework.
  Date/Author: 2026-05-23 / Codex.

- Decision: Prefer selected-provider consistency over auto-preferring Coinbase.
  Rationale: Users with both MetaMask and Coinbase installed should explicitly choose the wallet they intend to use. Auto-preferring Coinbase would fix one report by creating surprising behavior for existing MetaMask users.
  Date/Author: 2026-05-23 / Codex.

## Outcomes & Retrospective

Implemented wallet-provider hardening. Hyperopen now discovers legacy multi-provider injections and EIP-6963 announcements, stores serializable provider metadata in wallet state, lets users choose a provider from the header when more than one wallet is present, and keeps the selected provider consistent for connect, event listeners, chain lookup, typed-data signing, and downstream wallet provider calls.

The reported Coinbase failure is plausibly explained by the previous singleton-provider split: connection used `window.ethereum`, while signing used `globalThis.ethereum`. On desktops with Coinbase plus MetaMask, that could connect one wallet and send the enable-trading signature prompt to another provider. The new registry removes that drift.

After live testing with Coinbase, a second failure mode was confirmed. The signature completed and Hyperliquid returned `Must deposit before performing actions`, but the header did not render the agent runtime error, so the user saw no visible failure. The wallet menu now renders the agent error, and approval signing no longer treats an unrelated wallet network such as Ethereum mainnet `0x1` as the Hyperliquid signing chain id.

The browser QA run also exposed that the simulator had been too weak to prove chain propagation. That is now covered by a simulator unit test and browser evidence.

## Context and Orientation

Wallet connection starts in `src/hyperopen/views/header/wallet.cljs`, which renders the header connect control. The view model comes from `src/hyperopen/views/header/vm.cljs`. Clicking the connect control dispatches `[:actions/connect-wallet]`, which resolves through `src/hyperopen/wallet/actions.cljs`, `src/hyperopen/runtime/effect_adapters/wallet.cljs`, and `src/hyperopen/wallet/connection_runtime.cljs` into `src/hyperopen/wallet/core.cljs/request-connection!`.

Trading enablement starts from the same header wallet menu. `src/hyperopen/wallet/agent_runtime/enable.cljs` builds a temporary agent key and calls `src/hyperopen/wallet/agent_runtime/approval.cljs/approve-agent-request!`. That approval path signs a Hyperliquid typed-data request through `src/hyperopen/api/trading/user_actions.cljs`, `src/hyperopen/trading_crypto/module.cljs`, and `src/hyperopen/utils/hl_signing.cljs`.

Funding deposit workflows already accept a provider through `wallet/provider`, so fixing `wallet/provider` to return the selected provider also covers deposit provider calls.

The term "provider" in this plan means an EIP-1193 provider object with a `request` function. The term "selected provider" means the provider Hyperopen has chosen for this page session and must consistently use for connect, sign, event, and transaction calls.

## Plan of Work

First, add failing tests. The tests must show a multi-provider desktop shape where MetaMask is exposed as the global singleton and Coinbase is exposed in `window.ethereum.providers`. Dispatching connect with the Coinbase provider id must call Coinbase's `eth_requestAccounts` and must not call MetaMask. Signing typed data must use the same selected Coinbase provider, not `globalThis.ethereum`.

Second, add `src/hyperopen/wallet/provider_registry.cljs`. This file owns provider discovery, selected-provider id, provider override behavior for tests and browser simulators, EIP-6963 announcement handling, legacy `window.ethereum.providers` handling, and serializable provider metadata for the store. `hyperopen.wallet.core` should delegate provider access to this registry while preserving the public functions `provider`, `set-provider-override!`, and `clear-provider-override!`.

Third, update connection actions and effects to optionally accept a provider id. Existing `[:actions/connect-wallet]` remains valid. A new form `[:actions/connect-wallet provider-id]` selects a discovered provider before requesting accounts. The effect argument schema should accept zero args or one non-empty string.

Fourth, update `src/hyperopen/utils/hl_signing.cljs` to ask the provider registry for the selected provider. This keeps all typed-data wallet prompts on the wallet selected at connection time. Preserve existing fallback behavior for tests by letting the provider registry fall back to singleton providers when no provider has been selected.

Fifth, add a compact header affordance. When one provider is discovered, the existing Connect Wallet button can keep dispatching `[:actions/connect-wallet]`. When multiple providers are discovered, render a small details menu with provider-name buttons. Each button dispatches `[:actions/connect-wallet provider-id]`. Keep the header compact and use existing header styling.

Sixth, extend simulator and QA coverage. The debug wallet simulator should remain compatible with the new registry by setting the provider override. The manual QA matrix should list Coinbase Wallet extension as a covered desktop provider. The checked-in Playwright wallet scenario should still pass because simulator override maps to a single selected provider.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/4ffb/hyperopen`.

Run a focused failing test cycle first:

    npm test

Expected before implementation: at least the newly added provider-selection tests fail because the selected-provider registry does not exist or signing still uses the wrong global provider.

After implementation, run:

    npm test

Expected after implementation: all ClojureScript tests pass.

Because wallet UI changes touch browser flows, run the smallest relevant Playwright or browser-inspection command before broad gates:

    node tools/browser-inspection/src/cli.mjs scenario run --ids wallet-enable-trading-simulated --manage-local-app --run-kind wallet-provider-hardening

Expected: the scenario reports PASS for the simulated wallet enable-trading flow.

Finally run the repository-required gates:

    npm run check
    npm test
    npm run test:websocket

Expected: each command exits with code 0. If a command fails, update this plan with the failure and continue from the failing boundary.

## Validation and Acceptance

Acceptance criteria:

1. With two legacy providers in `window.ethereum.providers`, Hyperopen can connect to the provider selected by id and stores the selected provider id in wallet state.
2. After selecting Coinbase, typed-data signing for `approveAgent` uses Coinbase even when `globalThis.ethereum` points at MetaMask.
3. Existing singleton-provider behavior still works for MetaMask and the existing simulator.
4. `eth_chainId` is requested after connect and wallet state records the resulting chain id when the provider supports it.
5. The header still shows a compact Connect Wallet control; when multiple providers are available, it offers provider-specific connect actions.
6. Manual QA docs explicitly include Coinbase Wallet desktop coverage.

## Idempotence and Recovery

The implementation is additive and can be retried safely. Provider discovery only reads browser globals and stores metadata under wallet state. The selected provider object is held in memory only and is reset by page reload, provider override cleanup, or test cleanup. If EIP-6963 browser constructors are unavailable in tests, discovery should silently fall back to legacy providers.

If a test run fails halfway, run `npm run browser:cleanup` before repeating browser-inspection scenarios. Do not run `git pull --rebase` or `git push` during this plan unless explicitly requested by the maintainer.

## Artifacts and Notes

- `npm ci` was required because `node_modules` was absent in this worktree. It completed, with npm reporting existing audit findings: 7 moderate and 9 high vulnerabilities.
- `npm test` before implementation produced expected RED warnings for missing `wallet/reset-provider-registry!` and the new `request-connection!` arity. After implementation and the typography token fix, full unit tests passed: 4010 tests / 22095 assertions.
- `npx shadow-cljs --force-spawn compile app` passed with 0 warnings after simulator chain-id support.
- `node tools/browser-inspection/src/cli.mjs scenario run --ids wallet-enable-trading-simulated --manage-local-app --run-kind wallet-provider-hardening-postcompile-retry` passed. Artifact directory: `tmp/browser-inspection/wallet-provider-hardening-postcompile-retry-2026-05-23T22-42-55-313Z-c2a5834f`. Evidence includes connected wallet `0x1111111111111111111111111111111111111111`, `chainId: "0xa4b1"`, and `agentStatus: "ready"`.
- `npx playwright test tools/playwright/test/trade-regressions.spec.mjs -g "wallet connect and enable trading stays deterministic"` passed: 1 test.
- `npm run check` did not complete because of the unrelated stale-doc gate noted above.
- `npm test` after all changes passed: 4011 tests / 22096 assertions.
- `npm run test:websocket` passed: 524 tests / 3043 assertions.
- `npm run browser:cleanup` passed after browser work and reported no lingering browser-inspection sessions.
- Live Coinbase debugging through the user-provided Shadow nREPL session showed the final runtime state: signature returned, `approveAgent` was submitted, and Hyperliquid rejected it with `Must deposit before performing actions`.
- Added regression coverage for rendering `[:wallet :agent :error]` in the header menu and for ignoring unsupported wallet chain ids like `0x1` when deriving Hyperliquid approval signing context.

## Interfaces and Dependencies

Create `src/hyperopen/wallet/provider_registry.cljs` with these public functions:

- `install-discovery!` accepts an optional store atom and installs EIP-6963 discovery when possible.
- `sync-provider-list!` writes serializable provider metadata to `[:wallet :providers]` and selected id to `[:wallet :selected-provider-id]`.
- `provider-records` returns provider records containing `:id`, `:name`, `:rdns`, `:source`, and `:provider`.
- `provider-metadata` returns provider records without the raw provider object.
- `select-provider!` accepts nil or a provider id and returns the selected provider record.
- `provider` returns the selected provider object or the best fallback provider object.
- `set-provider-override!`, `clear-provider-override!`, and `reset-provider-registry!` support tests and debug simulators.

Keep `src/hyperopen/wallet/core.cljs/provider`, `set-provider-override!`, and `clear-provider-override!` as compatibility wrappers around the registry.

Revision note 2026-05-23: Initial plan created because the maintainer requested implementation of wallet provider hardening for Coinbase and similar wallets.
