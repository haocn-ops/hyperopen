# Add a runtime Hyperliquid Testnet transport override with explicit Mainnet selection

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. Maintain it in accordance with `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`.

## Purpose / Big Picture

Hyperopen currently hard-codes Hyperliquid Mainnet independently in its REST `/exchange`, REST `/info`, and WebSocket setup. A developer cannot direct the application to Testnet without editing source, and any one-off endpoint replacement can leave the other transports or signing environment on a different network.

After this change, a developer can reload the app with `?hyperliquidNetwork=testnet` or set `globalThis.HYPEROPEN_HYPERLIQUID_NETWORK = "testnet"` before the application bundle initializes. All three Hyperliquid transports then resolve to `https://api.hyperliquid-testnet.xyz/exchange`, `https://api.hyperliquid-testnet.xyz/info`, and `wss://api.hyperliquid-testnet.xyz/ws`; signing uses Testnet metadata and rejects a wallet on the wrong chain before a request is sent. `?hyperliquidNetwork=mainnet` and the equivalent pre-load global are explicit Mainnet overrides. With neither selector, the current Mainnet behavior remains unchanged for release builds, existing tests, and users.

An opt-in live smoke proves the actual Testnet `/info`, `/exchange`, and WebSocket endpoints are reachable without submitting a signed or state-changing action. This is a network-configuration and safety change, not performance work; no optimization, benchmark, or profiling work is proposed.

## Context References

Public refs:

- Direct maintainer request, 2026-07-22: connect REST `/exchange`, REST `/info`, and WebSocket to Hyperliquid Testnet; provide an explicit Mainnet override; define functional and live-smoke acceptance.
- Implementation direction confirmed by the parent orchestration thread, 2026-07-22: use a query/global runtime override so normal unit tests and release builds retain their existing Mainnet default.

Repo artifacts:

- `AGENTS.md` requires deterministic websocket decisions, boundary-only side effects, and the `npm run check`, `npm test`, and `npm run test:websocket` gates.
- `docs/BROWSER_TESTING.md` assigns deterministic browser work to Playwright and live inspection to Browser MCP. This plan changes no view or interaction contract, so neither is a required completion gate.
- `docs/exec-plans/active/2026-07-14-base-dex-clearinghouse-stream-replaces-webdata2.md` documents the existing Mainnet WebSocket topology. This plan changes only its selected origin, not subscription intent or reducer behavior.

Local scratch refs (non-authoritative):

- None.

## Scope and Non-Goals

The scope is a single runtime Hyperliquid network selector and all hard-coded transport consumers: `src/hyperopen/config.cljs` for selection and source-of-truth values, `src/hyperopen/api/trading/http.cljs` for `/exchange` and its direct `/info` helper, `src/hyperopen/api/info_client/runtime.cljs` for queued `/info` calls, and `src/hyperopen/runtime/state.cljs` plus the existing websocket adapter path for the connection URL.

Because a signed action must describe the same Hyperliquid chain as the endpoint receiving it, the scope includes the default agent-signing environment in `src/hyperopen/runtime/action_adapters/wallet.cljs` and user-signed context in `src/hyperopen/api/trading/user_actions.cljs`. A recognized connected-wallet chain that differs from the selected network must fail locally before crypto or `fetch`.

The selector is evaluated once during application configuration initialization. The query string `hyperliquidNetwork` has priority when valid, then pre-load `globalThis.HYPEROPEN_HYPERLIQUID_NETWORK`; accepted exact normalized values are `testnet` and `mainnet`; when neither selector is valid the app defaults to Mainnet. Changing the value after the bundle has initialized is unsupported and requires a full page reload. The selector never persists itself to browser storage.

Out of scope are automatic wallet-network switching, a UI setting, browser persistence, modifying third-party Hyperunit, stats-data, icon, price-history, or explorer URLs, funding accounts, adding credentials, or submitting a valid Testnet order. The `/exchange` live probe must be deliberately incomplete and must not contain a signature, private key, agent key, wallet address, or action that can change account state.

## Progress

- [x] (2026-07-22 01:58Z) Read repository planning, work-tracking, multi-agent, and ExecPlan contracts; inspected all current endpoint definitions and focused tests.
- [x] (2026-07-22 02:00Z) Clarified selection semantics with orchestration: query/global Testnet override, query/global explicit Mainnet selection, and Mainnet fallback for compatibility.
- [x] (2026-07-22 02:26Z) Implemented the central runtime resolver, endpoint consumers, signing consistency checks, deterministic tests, and opt-in Testnet live smoke.
- [x] (2026-07-22 03:18Z) Ran deterministic smoke coverage and the opt-in live Testnet probe; all passed. The ClojureScript gates stop before compilation because this machine has no Java Runtime.
- [x] (2026-07-22 03:31Z) Re-ran the full gate matrix with loopback permission: 26/34 gates passed, with the remaining eight failures limited to Shadow-CLJS/`npm test`/WebSocket compilation and all reporting the missing Java Runtime.

## Surprises & Discoveries

- Observation: the three required transport families do not currently share one source of truth.
  Evidence: `src/hyperopen/config.cljs` hard-codes only `:ws-url`; `src/hyperopen/api/trading/http.cljs` separately defines Mainnet `/exchange` and `/info`; `src/hyperopen/api/info_client/runtime.cljs` separately defines Mainnet `:info-url`.

- Observation: Testnet signing support already exists, but normal runtime defaults can still select Mainnet signing.
  Evidence: `src/hyperopen/api/trading/user_actions.cljs` recognizes Testnet chain ID `0x66eee`; `src/hyperopen/runtime/action_adapters/wallet.cljs` defaults `:is-mainnet` to `true` when enabling agent trading.

- Observation: current test and release defaults assert Mainnet endpoints.
  Evidence: `test/hyperopen/config_test.cljs` asserts `wss://api.hyperliquid.xyz/ws`; `test/hyperopen/api/info_client_test.cljs` asserts `https://api.hyperliquid.xyz/info`; `npm run build` has no endpoint configuration input.

- Observation: Node supplies `fetch` and `WebSocket` without adding a package in the supported runtime.
  Evidence: the opt-in smoke uses only these platform globals and deterministic fakes in `tools/hyperliquid/testnet_live_smoke.test.mjs`.

- Observation: this macOS environment's Node resolver intermittently returns `ENOTFOUND` for the Testnet hostname even though `curl` and the provider are reachable. Running the live probe with `NODE_OPTIONS=--dns-result-order=ipv4first` completed successfully.
  Evidence: the successful run returned `/info` universe-count 210, `/exchange` status 422, and WebSocket `allMids` market-count 2283.

- Observation: browser release security headers also needed the Testnet REST and WebSocket origins before the runtime selector could work in a deployed page.
  Evidence: `tools/release-assets/security_headers.mjs` and `site_metadata.mjs` now allow and preconnect to `api.hyperliquid-testnet.xyz`, with release-artifact assertions covering both origins.

## Decision Log

- Decision: Retain Mainnet as the no-selector fallback and make Testnet an explicit runtime override.
  Rationale: This delivers Testnet connectivity without changing the tested/released default network. It satisfies the requested Mainnet override through an explicit `mainnet` selector while preserving compatibility for every existing deployment that provides no selector.
  Date/Author: 2026-07-22 / spec_writer.

- Decision: Use the first valid selector from `?hyperliquidNetwork=<network>` and then `globalThis.HYPEROPEN_HYPERLIQUID_NETWORK`; ignore invalid values.
  Rationale: The query form is visible, shareable, and easy to exercise in local browser testing. The pre-load global supports local hosting and integration harnesses. Valid-query precedence is deterministic, while an invalid query does not suppress a valid pre-load override; no valid selector retains the existing Mainnet behavior.
  Date/Author: 2026-07-22 / spec_writer.

- Decision: Resolve the selected network once at configuration initialization; do not implement live network switching.
  Rationale: Switching during a session would require coherent cache invalidation, WebSocket teardown/reconnect, subscription replay, and agent/session migration. Those requirements substantially exceed the task and risk violating the websocket purity contract.
  Date/Author: 2026-07-22 / spec_writer.

- Decision: Centralize the resolved REST, WebSocket, and signing metadata in `hyperopen.config`, while retaining existing public URL aliases.
  Rationale: This removes three independently maintained endpoints without breaking callers of `hyperopen.api/info-url`, `hyperopen.api.trading/exchange-url`, or `runtime-state/websocket-url`.
  Date/Author: 2026-07-22 / spec_writer.

- Decision: Block wallet/signing-network mismatch before `fetch`.
  Rationale: Without this preflight, Testnet endpoints can receive Mainnet signatures and produce an opaque provider rejection. The local error gives an actionable correction and ensures the endpoint selector and signing metadata never diverge.
  Date/Author: 2026-07-22 / spec_writer.

- Decision: Keep the Testnet live smoke opt-in and outside CI, but commit the script and its deterministic unit tests.
  Rationale: provider availability, DNS, and public rate limits are external variables. CI remains deterministic; a maintainer can collect endpoint evidence intentionally before a Testnet release or diagnostic session.
  Date/Author: 2026-07-22 / spec_writer.

## Context and Orientation

`hyperopen.config` is compiled into the browser app and already supplies `:ws-url`. `hyperopen.runtime.state/websocket-url` passes that value through startup collaborators to `hyperopen.runtime.effect-adapters.websocket/init-connection!`. The runtime reducer owns subscription decisions and must not inspect browser globals or mutate the chosen network.

REST currently bypasses that configuration. `hyperopen.api.trading.http/json-post!` posts to its `exchange-url` constant and its account helpers use its `info-url` constant. Independently, `hyperopen.api.info-client.runtime/default-config` seeds the shared rate-limited `/info` client. Both must read the same resolved `:info-url` as the configuration map.

The selected environment determines Hyperliquid L1 signing metadata: Mainnet uses `"Mainnet"` and the established Mainnet signature chain, while Testnet uses `"Testnet"` and `0x66eee`. A *preflight error* is a local failure before any crypto or network request. It must name the selected network and tell the user to change the wallet network or reload with the appropriate selector.

The live smoke is a Node program independent of app code. It receives the three Testnet URLs as constants or command options, applies a finite timeout, and closes the WebSocket in `finally`. Its incomplete `/exchange` request tests reachability only; it is never a trading action.

## Plan of Work

### 1. Establish a pure, deterministic network selector

In `src/hyperopen/config.cljs`, add pure helpers to normalize a raw selector and resolve `{:query-network ..., :global-network ...}` into a stable map with keys `:network`, `:is-mainnet`, `:signature-chain-id`, `:hyperliquid-chain`, `:info-url`, `:exchange-url`, and `:ws-url`. The resolver must read no global values itself so tests can exercise precedence. A small impure boundary reads `js/location.search` and `js/globalThis.HYPEROPEN_HYPERLIQUID_NETWORK` once and passes both inputs to the resolver.

The Testnet map must use `https://api.hyperliquid-testnet.xyz/info`, `https://api.hyperliquid-testnet.xyz/exchange`, `wss://api.hyperliquid-testnet.xyz/ws`, `false`, `"0x66eee"`, and `"Testnet"`. Mainnet retains the current `.xyz` origins, `true`, the existing Mainnet chain ID, and `"Mainnet"`. The config map exposes the resolved map at `[:hyperliquid]` and retains top-level `:ws-url` as the stable compatibility value.

### 2. Route all three transports through that contract

In `src/hyperopen/api/trading/http.cljs`, replace literal `exchange-url` and `info-url` values with aliases of the resolved config values. Keep public names and `json-post!` behavior unchanged. In `src/hyperopen/api/info_client/runtime.cljs`, source `default-config/:info-url` from the same central value; retry, queue, dedupe, and rate-limit behavior remains unchanged.

In `src/hyperopen/runtime/state.cljs`, expose the resolved network fields needed by runtime collaborators while preserving `websocket-url`. `src/hyperopen/runtime/effect_adapters/websocket.cljs` must retain supplied `:ws-url` precedence for isolated tests and otherwise use `runtime-state/websocket-url`. Do not modify websocket reducers, desired subscriptions, health calculations, or connection side effects beyond this URL input.

### 3. Keep signing and the selected endpoint coherent

In `src/hyperopen/api/trading/user_actions.cljs`, make `resolve-user-signing-context` obtain the expected chain and Hyperliquid name from central configuration. It must validate a connected wallet chain before crypto loading: Mainnet selection accepts the existing Mainnet chain; Testnet selection accepts `0x66eee`; a recognized opposite chain rejects locally and leaves the fetch-call count at zero. Preserve successful action payload field names and nonce behavior.

In `src/hyperopen/runtime/action_adapters/wallet.cljs` and the narrow agent runtime call path, derive ordinary runtime `:is-mainnet` defaults from `[:hyperliquid :is-mainnet]`. Preserve explicitly passed test options and dependency injection seams. The default Mainnet application produces its current agent action; a Testnet-selected application produces Testnet signing metadata.

### 4. Add deterministic functional coverage and an opt-in live smoke

Extend `test/hyperopen/config_test.cljs` to cover query-over-global precedence, global fallback, exact `mainnet` and `testnet`, blank/invalid fallback to Mainnet, and the exact three URLs. Extend `test/hyperopen/runtime/state_test.cljs`, `test/hyperopen/api/info_client_test.cljs`, and `test/hyperopen/api/trading/internal_seams_test.cljs` to prove the normal test build keeps existing Mainnet URLs while resolved Testnet fixtures are consumed at each transport boundary. A websocket adapter test must prove an injected URL still wins over the default.

Extend `test/hyperopen/api/trading/sign_and_submit_test.cljs` and `test/hyperopen/runtime/action_adapters/wallet_test.cljs` to assert Testnet signing metadata for a Testnet resolver fixture and zero `fetch` calls plus an actionable mismatch error for an opposite wallet chain. Preserve the existing Mainnet assertions as regression coverage.

Add `tools/hyperliquid/testnet_live_smoke.mjs`, unit tests under `tools/hyperliquid/*.test.mjs`, and an explicitly non-CI script `npm run smoke:hyperliquid:testnet`. The script must `POST /info` `{ "type": "meta" }` and require HTTP 200 plus a JSON `universe` array; connect to Testnet WebSocket, subscribe to `{ "type": "allMids" }`, require one nonempty `allMids` payload, then close; and post a structurally incomplete body to `/exchange`, requiring a bounded non-5xx rejection in JSON or explicit plain-text deserialization form. It must reject any input that contains credentials, signature fields, wallet addresses, or a recognized executable action.

## Concrete Steps

Run all commands from `/Users/zh/Documents/Hyperopen`.

1. Bootstrap local dependencies before running gates:

       npm run setup:worktree

   Expect a linked dependency tree or a clear `npm ci` instruction. Do not diagnose missing local `shadow-cljs` before this setup step as a product failure.

2. Run focused deterministic coverage during implementation:

       npm run test:runner:generate
       npx shadow-cljs --force-spawn compile test
       node out/test.js
       npm run test:websocket
       node --test tools/hyperliquid/*.test.mjs

   Expect exit code 0. The ClojureScript tests must distinguish Mainnet fallback from Testnet selection and prove all three transport clients follow the resolved network; the Node tests must use fakes and make no public network requests.

3. Verify application configuration behavior locally:

       npm run dev

   With a browser reload at `http://localhost:8080/trade?hyperliquidNetwork=testnet`, inspect `globalThis.hyperopen` or browser DevTools network records and observe Testnet `/info` and `wss://api.hyperliquid-testnet.xyz/ws`; reload without the selector and observe the existing Mainnet origins. Stop any browser-inspection sessions created for this check with `npm run browser:cleanup`. Do not make this a committed browser test because the inspected provider data is inherently live and variable.

4. Run required project gates after code changes:

       npm run gates

   Expect the summary matrix to report PASS for `check`, `test`, and `test:websocket`.

5. With intentional public-network access, run the separate non-CI smoke:

       npm run smoke:hyperliquid:testnet

   Expect three PASS lines naming `https://api.hyperliquid-testnet.xyz/info`, `https://api.hyperliquid-testnet.xyz/exchange`, and `wss://api.hyperliquid-testnet.xyz/ws`. The output must show `/info` meta data and an `allMids` frame; `/exchange` must say `rejected-as-expected`, never `submitted` or `executed`.

## Validation and Acceptance

1. `test/hyperopen/config_test.cljs` demonstrates concrete selection behavior: `?hyperliquidNetwork=testnet` wins over a global `mainnet`, a global-only `testnet` selects Testnet, either explicit `mainnet` selects Mainnet, and absent/invalid input retains current Mainnet endpoints. Every case asserts the exact `/info`, `/exchange`, and WebSocket URL.

2. `test/hyperopen/runtime/state_test.cljs`, `test/hyperopen/api/info_client_test.cljs`, and `test/hyperopen/api/trading/internal_seams_test.cljs` show observable endpoint routing. With the no-selector fixture, `runtime-state/websocket-url`, queued `/info`, and signed `/exchange` retain their existing Mainnet URLs. With a Testnet resolved fixture, each uses the Testnet URL. Existing injected websocket URLs still win for isolated adapter tests.

3. `test/hyperopen/api/trading/sign_and_submit_test.cljs` and `test/hyperopen/runtime/action_adapters/wallet_test.cljs` prove Testnet selection creates `"Testnet"` / `0x66eee` signing metadata for a matching wallet. For both mismatched directions, the action reports a local network mismatch and `fetch` is never called. Mainnet signing tests remain green without selectors.

4. `node --test tools/hyperliquid/*.test.mjs` proves the live-smoke request builder rejects credential-bearing or executable payloads, fails on a timeout/non-5xx unexpected shape, accepts the provider's bounded JSON or plain-text deserialization rejection, closes its fake socket, and succeeds only after a nonempty `allMids` message.

5. `npm run smoke:hyperliquid:testnet`, when public network access is intentionally available, proves Testnet `/info` returns meta, Testnet WebSocket returns an `allMids` frame, and Testnet `/exchange` is reachable but rejects a non-executable request. No private information or order is supplied.

6. `npm run gates` reports PASS. Committed Playwright/browser QA is not required because no view, interaction, selector, or persistence contract changes; the live browser diagnostic is an optional inspection step, the deterministic websocket gate covers runtime behavior, and the external smoke covers the provider boundary.

## Idempotence and Recovery

The resolver is pure and the global/query values are read only at page initialization, so reloads with either selector are safe. The live smoke is safe to rerun because it sends no valid exchange action and closes the socket in `finally`. If Testnet is unavailable or rate-limits the smoke, record the provider response in this plan and mark only the live-smoke criterion blocked; do not weaken deterministic tests or change the default fallback.

To leave Testnet, remove `hyperliquidNetwork=testnet` from the URL and reload, or set the global value to `mainnet` before loading and reload. Do not patch generated JavaScript, persist the selector, or attempt a live network transition.

## Artifacts and Notes

Expected live-smoke output, with timestamps and volatile provider fields omitted:

    Hyperliquid network: testnet
    PASS info https://api.hyperliquid-testnet.xyz/info type=meta universe-count=<positive integer>
    PASS exchange https://api.hyperliquid-testnet.xyz/exchange rejected-as-expected status=<non-5xx>
    PASS websocket wss://api.hyperliquid-testnet.xyz/ws channel=allMids market-count=<positive integer>

No private keys, signatures, account addresses, complete HTTP payloads, or WebSocket frames belong in committed artifacts or this ExecPlan.

## Interfaces and Dependencies

`hyperopen.config/resolve-hyperliquid-network` must be pure and accept selector inputs so no test needs to mutate `js/location` or globals. It returns this stable conceptual shape:

    {:network :testnet | :mainnet
     :is-mainnet boolean
     :signature-chain-id string
     :hyperliquid-chain "Testnet" | "Mainnet"
     :info-url string
     :exchange-url string
     :ws-url string}

The impure configuration boundary owns exactly two reads: query key `hyperliquidNetwork` from `js/location.search` and `HYPEROPEN_HYPERLIQUID_NETWORK` from `js/globalThis`. It passes plain strings to the pure resolver. It does not read localStorage, call `fetch`, construct a socket, or dispatch an action.

Existing public aliases `hyperopen.api.trading/exchange-url`, `hyperopen.api.trading/info-url`, `hyperopen.api/info-url`, and `hyperopen.runtime.state/websocket-url` remain available. Their values derive from the selected network but their function names and caller-facing shapes remain unchanged.

The smoke depends only on Node's supported `fetch` and WebSocket APIs, or an already-declared direct dependency if the repository's supported Node version requires one. It must not add an application runtime dependency solely for release-time connectivity diagnostics.

## Outcomes & Retrospective

Implemented a single startup snapshot for the three transport endpoints and signing metadata. The resolver accepts the first valid query/global selector, falling back to Mainnet only when neither is valid; no persistence or dynamic switching was introduced. User actions now reject a recognized opposite wallet chain before crypto loading or `fetch`, while agent and API-wallet approval defaults carry the selected signing metadata.

Deterministic smoke coverage passed with `node --test tools/hyperliquid/testnet_live_smoke.test.mjs`; delimiter, docs, worktree setup, namespace, release-assets (48 tests), and test-lint checks also passed. The full gate matrix reached 26/34: the eight failed gates are only the Shadow-CLJS compile targets plus `npm test` and `npm run test:websocket`, all blocked before compilation because this worktree lacks a Java Runtime. With `NODE_OPTIONS=--dns-result-order=ipv4first`, the opt-in live smoke completed against all three Testnet transports: `/info` returned a 210-entry universe, `/exchange` returned the expected inert-probe HTTP 422 rejection, and WebSocket `allMids` returned 2283 markets.

The provider's live Testnet `/exchange` boundary responds to the empty inert object with HTTP 422 plain text, `Failed to deserialize the JSON body into the target type`. The smoke recognizes this bounded deserialization rejection without logging the response body; `/info` remains JSON-only.

## Deployment Record

The Cloudflare Worker deployment completed on 2026-07-22 to `https://hyperopen.izhenghaocn.workers.dev` with version ID `2689ce41-3c13-4376-81b7-7c66c332fb31`. `npm run test:playwright:seo` passed 6/6, the corrected full gate matrix passed 34/34 (6,538 tests and 35,734 assertions), `npm run cloudflare:check` passed, and local Wrangler parsed 24 header rules. The public Worker verifier returned 200 JSON for both mainnet and testnet fee probes, `/trade` returned 200, the unknown route returned 404, and `/api/health` returned 200 JSON with `no-store`. The first public headers request briefly observed the previous edge CSP during propagation; a cache-busted request and the subsequent standard verifier confirmed the deployed CSP includes both Testnet REST and WebSocket origins. The bundle budget remains a soft warning: main gzip is 653,643 bytes versus 640,000 (+13,643).

## Revision Note

2026-07-22: Created from the Testnet transport request and immediately refreshed after selection semantics were clarified. The contract is Mainnet-compatible fallback with query/global Testnet selection and explicit Mainnet selection, rather than a Testnet-default release build. Completed after live smoke evidence, release CSP/preconnect coverage, and gate results were recorded; the remaining compile failures are environmental Java absence.
