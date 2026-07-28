# Close residual Worker, in-memory credential, attribution, and Clojure supply-chain risks

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. It follows `docs/PLANS.md` and `.agents/PLANS.md`.

## Purpose / Big Picture

The first 2026-07-27 security remediation removed plaintext `localStorage` as the default credential posture and hardened the local development proxy, but a public Cloudflare Worker still proxies HyperUnit on arbitrary hosts and accepts unbounded request streams. Its non-passkey fallback also writes a raw agent private key to `sessionStorage`. This plan closes those residual paths.

After implementation, only `testnet.dexhelm.com` can serve the DEXHelm static terminal or forward a narrow Testnet HyperUnit request. Mainnet, apex, status, arbitrary hostnames, and the Workers development hostname cannot reach either static trade assets or upstream HyperUnit. A request over one MiB, an unsupported method, an upstream timeout, and an invalid proxy target each receive a stable documented response. A browser without passkey support can use an agent key only in live memory until reload; it never writes that raw key to browser storage. Affiliate endpoints are stored in one canonical representation, attribution identifiers use synchronous SHA-256, and the project gains automated scanning of its resolved Clojure/Maven dependency graph without adding a runtime library.

## Context References

Public refs: direct user request on 2026-07-27 to fix residual security risks found after the first remediation. No GitHub Issue or Pull Request is linked at plan creation.

Repo artifacts: `docs/exec-plans/completed/2026-07-27-security-remediation.md`, `docs/exec-plans/completed/2026-07-27-security-review-fixes.md`, `workers/hyperopen-worker.mjs`, `wrangler.jsonc`, `tools/hyperunit-proxy/server.mjs`, `src/hyperopen/wallet/agent_session.cljs`, `src/hyperopen/wallet/core.cljs`, `src/hyperopen/service/{tenant_config,attribution}.cljs`, `src/hyperopen/runtime/effect_adapters/attribution.cljs`, `deps.edn`, `tools/security/sbom.mjs`, and `.github/workflows/security.yml`.

Local scratch refs (non-authoritative): none.

## Progress

- [x] (2026-07-27T12:10Z) Inspected the post-remediation worktree and captured the residual risk boundaries, compatible tests, and an executable Clojure dependency-tree feasibility result.
- [x] (2026-07-27T12:18Z) Validated the active plan with `git diff --check` and `npm run lint:docs`; the worktree has no whitespace errors and the documentation guard passed.
- [x] (2026-07-27T14:26Z) Added Worker regression coverage for exclusive Testnet host authority, exact proxy routes, method and body limits, timeout cleanup, generic failures, and disabled `workers.dev` exposure.
- [x] (2026-07-27T13:33Z) Added the approved ClojureScript contract coverage for memory-only credentials and legacy cleanup, canonical affiliate endpoint vectors in ClojureScript and Node, synchronous SHA-256 vectors, and consent revocation despite storage failure.
- [x] (2026-07-27T13:33Z) Implemented the ClojureScript hardening slice: new raw credentials cannot enter browser storage, legacy session records are removed, local raw records are quarantined rather than restored, session enablement uses only the lockbox cache, affiliate endpoints retain `URL.href`, and new attribution identifiers use Closure SHA-256.
- [x] (2026-07-27T14:26Z) Restricted Worker proxying to the exact Testnet host/path, removed Mainnet and generic upstream routes/binding, allowed only GET/HEAD/POST, bounded bodies to 1 MiB, applied a 15-second abort deadline, and validated all 27 Worker tests plus the Cloudflare dry run.
- [x] (2026-07-27T14:26Z) Added a pinned 35-coordinate Clojure/Maven inventory, deterministic generator and fail-closed OSV scanner, CI artifact coverage, and fixture tests; the live OSV result contains zero advisories.
- [x] (2026-07-27T13:33Z) Validated the current worktree with `npm run check`, `node --no-experimental-webstorage out/test.js` (5,827 tests / 32,403 assertions), `npm run test:websocket` (561 tests / 3,184 assertions), and `npm run gates`; all passed with the repository-local JRE and the Node 25 web-storage workaround. Browser QA is not applicable because this slice changes no UI surface.
- [x] (2026-07-27T14:26Z) Revalidated the final worktree: `npm run test:cloudflare-worker` passed 27/27, the Wrangler dry run exposed only `HYPERUNIT_TESTNET_URL`, and `npm run gates` passed 35/35 with 6,566 tests and 35,799 assertions.
- [x] (2026-07-27T15:12Z) Extended the Worker deadline across client upload and upstream response streaming; the complete Worker suite now passes 29/29, including deterministic stalled-stream regressions.
- [x] (2026-07-27T15:12Z) Included CI-used aliases in the Maven graph, upgraded Shadow CLJS to 3.4.0, and scanned all 62 locked Maven coordinates with zero OSV advisories.
- [x] (2026-07-27T15:12Z) Aligned release/runtime affiliate endpoint length validation on trimmed raw input before canonicalization and added the long dot-segment regression vector.
- [x] (2026-07-27T15:12Z) Materialized all five approved ClojureScript security suites and passed `npm run gates` at 35/35 with 6,574 tests and 35,820 assertions.
- [x] (2026-07-27T15:48Z) Enforced Maven lock freshness before OSV queries and in CI, with added/removed/version-changed dependency regressions; expanded the frozen ClojureScript suites across real enable-to-sign, reload, cleanup, canonical delivery, digest compatibility, and revoked-retry paths.
- [x] (2026-07-27T15:48Z) Corrected the live signing path to read session credentials only from the process-local lockbox and made all enablement failure exits clear stale session/cache state.
- [x] (2026-07-27T15:54Z) Passed final read-only security review with no findings and revalidated `npm run gates` at 35/35 with 6,577 tests and 35,839 assertions; archive this completed plan.

## Surprises & Discoveries

- Observation: the Worker routes HyperUnit by path after only checking that the Mainnet custom host is closed. Any other hostname, including the current `workers.dev` hostname, reaches `resolveHyperunitTarget` and can proxy Mainnet or Testnet.
  Evidence: `workers/hyperopen-worker.mjs` checks `DEXHELM_MAINNET_HOST` first, but only applies `matchingHyperunitRoute` later without a host predicate. `wrangler.jsonc` has `"workers_dev": true`; `tools/cloudflare/worker.test.mjs` currently expects a Workers development URL to receive a static asset.

- Observation: the Worker forwards every non-GET/HEAD request body as an upstream stream and has no method allowlist, byte limit, or abort timeout.
  Evidence: `buildHyperunitRequest` accepts any method and assigns `request.body`; `handleRequest` invokes `fetchImpl` with no `AbortSignal` or size check.

- Observation: the first remediation changed new passkey-capable users to passkey locking and quarantined local plaintext credentials, but the non-passkey `:session/:plain` path still serializes the private key to `sessionStorage`.
  Evidence: `persist-agent-session-by-mode!` in `src/hyperopen/wallet/agent_session.cljs` calls `persist-agent-session!` on session storage, and `sanitize-agent-session` contains `:private-key`.

- Observation: Clojure dependency-tree resolution is available in this repository without installing a new tool.
  Evidence: from the repository root, `clojure -Stree` exited 0 in 1.4 seconds on 2026-07-27 and printed the selected transitive Maven coordinates, including `com.fasterxml.jackson.core/jackson-core 2.8.7` and `org.yaml/snakeyaml 1.33`.

- Observation: affiliate endpoint validation is strict but representation is not canonical, and attribution's `evt-` digest is a custom non-cryptographic mixing function.
  Evidence: both `src/hyperopen/service/tenant_config.cljs` and `tools/white-label/tenant_config.mjs` validate `URL` fields but return the trimmed input unchanged; `src/hyperopen/service/attribution.cljs` defines `digest-string` from four `Math.imul` accumulators.

- Observation: Node 25's experimental web-storage implementation makes the ClojureScript test process see a `localStorage` object without the methods expected by the test shims.
  Evidence: the unmodified WebSocket command compiled successfully but reported `localStorage.getItem/setItem is not a function`; rerunning with `NODE_OPTIONS=--no-experimental-webstorage` passed 561 tests and 3,184 assertions. The default shell also had no Java runtime, while `/private/tmp/hyperopen-jre-run/jdk-21.0.11+10-jre/Contents/Home` compiled all ClojureScript targets successfully.

- Observation: the necessary attribution and wallet regression coverage crossed three existing namespace-size caps.
  Evidence: `npm run lint:namespace-sizes` reported 641/640 production attribution lines, 575/560 attribution-test lines, and 620/600 wallet-core-test lines. The exception registry now records the exact measured limits and the check passes.

- Observation: scanning the CI aliases exposed vulnerable transitive dependencies in the old Shadow CLJS build chain; the newest tested release, 3.4.11, also caused the existing asynchronous ClojureScript suite to exit early.
  Evidence: Shadow CLJS 3.4.0 is the first release in the tested range that uses `shadow-http` instead of the vulnerable Undertow chain and still compiles and runs all 5,834 ClojureScript tests. The resulting alias-aware lock contains 62 coordinates and the live OSV scan reports zero advisories.

- Observation: namespaced keywords with the same local name were not totally ordered by attribution canonical serialization.
  Evidence: `:tenant/id` and `:venue/id` both previously sorted only as `id`, so stable sort retained map insertion order and produced different SHA-256 identifiers. Canonical key ordering now includes key type, keyword namespace, and keyword name; the insertion-order regression passes.

- Observation: the first final review showed that scanning a committed Maven inventory is insufficient unless CI also proves that its direct-dependency snapshot still matches `deps.edn`.
  Evidence: the OSV command formerly trusted the lock file alone. It now runs a deterministic freshness check first, and fixture coverage rejects added, removed, and version-changed direct Maven dependencies.

- Observation: removing browser persistence did not by itself guarantee that every live signing caller used the in-memory credential boundary.
  Evidence: `agent_actions.cljs` still read the legacy session-storage location until the integrated enable-to-sign test exposed it. Signing now reads only the wallet-scoped in-memory lockbox, and approval, synchronization, and persistence failures clear stale session/cache state.

## Decision Log

- Decision: make `testnet.dexhelm.com` the sole terminal/proxy authority. It may proxy only the exact Testnet HyperUnit prefix; no host may proxy a Mainnet prefix or the ambiguous legacy `/api/hyperunit` prefix.
  Rationale: deployment authority must agree with the Testnet-only product promise. A generic path and a host-agnostic Worker permit accidental Mainnet reachability or open-proxy reuse. The custom Mainnet host remains an intentional 503 closure, while apex/status retain only their documented informational routes.
  Date/Author: 2026-07-27 / Codex.

- Decision: disable `workers.dev` in `wrangler.jsonc` and make unknown hosts return a deterministic 404 without delegating to `ASSETS` or an upstream.
  Rationale: disabling the route prevents new public exposure at deployment. The runtime hostname guard protects stale or manually enabled Workers development URLs and prevents arbitrary custom host bindings from inheriting the proxy. No business requirement justifies a public alternate terminal origin.
  Date/Author: 2026-07-27 / Codex.

- Decision: allow only `GET`, `HEAD`, and `POST` on the Testnet proxy. Buffer at most 1 MiB before forwarding and use a 15-second `AbortController` deadline.
  Rationale: HyperUnit calls used by this application are read requests or small JSON submissions. A one-MiB bounded buffer is simple, deterministic, and sufficient; streaming an unknown body makes a reliable 413 response impossible once the upstream fetch begins. `GET` and `HEAD` have no body; `POST` requires a bounded body. Unsupported methods return 405 with `Allow: GET, HEAD, POST`; declared or streamed oversize bodies return 413; invalid upstream configuration and any fetch/timeout failure return the same non-sensitive 502 JSON response.
  Date/Author: 2026-07-27 / Codex.

- Decision: make unsupported-passkey agent credentials memory-only. On reload or a new tab, the agent must return to `:not-ready` and require Enable Trading; legacy `sessionStorage` raw records are deleted rather than restored.
  Rationale: `sessionStorage` is readable by same-origin script and therefore does not protect a raw private key from XSS. It also fails the promised security boundary even though it expires later. The application already keeps an unlocked credential only in `agent-lockbox`'s in-memory cache; that is the correct fallback when passkeys are unavailable. Data loss is explicit and expected for the new fallback, while removal of an old raw session record is a security cleanup because it is not a recoverable durable credential.
  Date/Author: 2026-07-27 / Codex.

- Decision: canonicalize enabled affiliate endpoints with the platform URL parser in both Node release tooling and ClojureScript runtime, then store and use that canonical `href` everywhere.
  Rationale: equivalent inputs such as mixed-case hosts, explicit port 443, or dot path segments should have one stable value for tenant manifests, CSP source derivation, consent behavior, queue comparison, and network delivery. Canonicalization occurs only after the existing HTTPS/no-credentials/no-fragment validation.
  Date/Author: 2026-07-27 / Codex.

- Decision: replace the custom attribution identifier with Closure Library's synchronous SHA-256 and prefix new values `sha256-`.
  Rationale: a synchronous digest preserves the existing pure deterministic API and does not require a browser asynchronous boundary or a new runtime package. Existing persisted `evt-` identifiers remain valid opaque queue keys; newly generated identifiers become 64 lowercase hexadecimal SHA-256 output with an algorithm prefix. Wallet-address hashes are pseudonymous identifiers: they hide the literal string in ordinary UI/network output but remain stable and can be linked or reversed by testing known public addresses. They must never be described as anonymous or as secret protection.
  Date/Author: 2026-07-27 / Codex.

- Decision: implement Clojure dependency scanning with a checked-in, exact resolved-tree inventory plus a Node OSV query tool, not an unpinned scanner package or broad dependency upgrade.
  Rationale: the repository's `clojure -Stree` is available and all direct `deps.edn` versions are exact. A committed sorted inventory makes CI deterministic and requires only Node 22's built-in `fetch` to query OSV's Maven ecosystem. A test will fail when a direct `deps.edn` coordinate/version is absent from the inventory, forcing explicit regeneration. This adds no application dependency and scopes remediation of any findings to separately reviewed upgrades.
  Date/Author: 2026-07-27 / Codex.

- Decision: use Shadow CLJS 3.4.0 and scan the combined `:dev` and `:test` alias graph.
  Rationale: 3.4.0 removes the vulnerable Undertow-based server chain while preserving compatibility with the repository's asynchronous test runner. Alias-aware direct-dependency validation now fails closed if a CI build dependency such as `thheller/shadow-cljs` is absent or version-mismatched. npm overrides pin patched development-tool transitive versions and remain covered by the full gate matrix.
  Date/Author: 2026-07-27 / Codex.

## Outcomes & Retrospective

The residual hardening scope is complete. Raw agent keys are now process-local unless passkey locking stores the encrypted lockbox form; raw records in legacy session storage are deleted and local raw records cannot become ready state. The endpoint normalizer and SHA-256 helper reuse the platform `URL` parser and Closure Library, so no package was added. This reduces the security surface rather than adding a new persistence or hashing abstraction.

The approved regression vectors cover mixed-case/default-port/dot-segment endpoint canonicalization, SHA-256 for empty, ASCII, Unicode, wallet, and event values, legacy `evt-` compatibility, storage cleanup, memory-only session enablement, and consent revocation when persistence fails. Worker coverage adds host/path authority, bounded methods and bodies, full-lifecycle deadline cleanup, and fail-closed upstream errors; its complete suite passed 29/29. A fresh Cloudflare dry run was not authorized because the command may send built Worker content or release assets to Cloudflare; no deployment occurred. The checked-in configuration and regression tests confirm `workers_dev=false` and only `HYPERUNIT_TESTNET_URL` is bound.

The committed alias-aware Maven inventory contains 62 selected coordinates. The live OSV query reported zero advisories, including Shadow CLJS 3.4.0, Jackson Core 2.18.8, and SnakeYAML 2.0, while fixture tests prove malformed, stale, unreachable, and vulnerable results fail closed. Both `npm audit --omit=dev --audit-level=high` and the full `npm audit --audit-level=high` reported zero vulnerabilities. Wrangler is pinned at 4.114.0, and patched development-tool transitive dependencies are enforced through npm overrides.

The first final-validation attempt passed before a read-only review identified four release blockers: incomplete Worker deadline scope, omitted alias dependencies, endpoint raw-length policy mismatch, and absent frozen ClojureScript suites. A second review then found missing Maven lock freshness enforcement and incomplete integration coverage; resolving those gaps also exposed and fixed legacy storage access in the live signing path and stale credential state after enablement failures. The final read-only review passed with no findings. The final local validation passed `npm run gates` at 35/35 with 6,577 tests and 35,839 assertions; the compiled ClojureScript suite passed 5,836 tests / 32,443 assertions with zero warnings and the independently compiled WebSocket suite passed 561 tests / 3,184 assertions. Node 25 requires `NODE_OPTIONS=--no-experimental-webstorage`, and localhost-binding tests require approved local-loopback permission. The Worker was not deployed; `workers_dev=false`, removal of the Mainnet binding, and the full-lifecycle deadline take effect only at the next separately authorized deployment.

## Context and Orientation

`workers/hyperopen-worker.mjs` is the Cloudflare Worker entrypoint. `handleRequest` currently serves informational pages, delegates static assets through the `ASSETS` binding, or proxies paths beginning with `/api/hyperunit`. A Worker hostname is the hostname in the request URL, not merely the configured custom-domain list. `wrangler.jsonc` currently binds the Worker to four DEXHelm custom domains and also enables the generated `workers.dev` hostname.

The required public Worker responses are: Mainnet custom host always 503 with the current suspended-service document; allowed proxy success preserves selected safe upstream headers; rejected method 405 is JSON with the `Allow` header; body too large 413 is JSON; upstream configuration, upstream non-response, network errors, and timeout are 502 with a generic JSON error. Document/status HTML behavior must remain GET/HEAD-only. `HEAD` must never include an application body. A static terminal request is permitted only on `testnet.dexhelm.com`; apex and status pages are informational and unknown hosts are closed.

`src/hyperopen/wallet/agent_session.cljs` owns serialized agent session records. `src/hyperopen/wallet/agent_lockbox.cljs` uses WebAuthn and IndexedDB to keep durable credentials encrypted, while its unlocked cache is only in memory. `src/hyperopen/wallet/core.cljs` resolves a persisted record into agent state. `src/hyperopen/wallet/agent_runtime/{enable,approval,storage_mode,protection_mode}.cljs` owns credential enablement and changes in persistence posture. `docs/BROWSER_STORAGE.md` already states that browser-readable storage does not protect secrets from XSS.

`src/hyperopen/service/tenant_config.cljs` validates tenant runtime configuration and `tools/white-label/tenant_config.mjs` validates the same shape before a release is created. `src/hyperopen/runtime/effect_adapters/attribution.cljs` delivers only enabled, consented affiliate events. `src/hyperopen/service/attribution.cljs` builds their pure redacted shape, stable event identifier, and wallet-address pseudonym. Both Node and browser validation must produce exactly the same canonical endpoint so release CSP and runtime delivery have identical authority.

`deps.edn` specifies direct Clojure/Maven dependencies. Maven dependency coordinates use `group/artifact`; OSV's Maven queries use the same coordinate with `/` changed to `:` and an exact version. `clojure -Stree` prints the selected direct and transitive graph with leading tree markers; conflict lines marked `X` are not selected dependencies and must not become scan entries. The existing `tools/security/sbom.mjs` intentionally covers only `package-lock.json`, so it cannot answer Clojure vulnerability questions.

## Plan of Work

### Milestone 1: Make the Worker host and upstream contract exclusive

Write RED Node tests in `tools/cloudflare/worker.test.mjs` and `tools/cloudflare/worker_edge_cases.test.mjs`. Revise old tests that deliberately expect `https://hyperopen.example` or a `workers.dev` URL to serve static assets; those expectations are no longer valid. Test the full matrix before implementation: `testnet.dexhelm.com` forwards only `/api/hyperunit/testnet` to `HYPERUNIT_TESTNET_URL`; it rejects `/api/hyperunit`, `/api/hyperunit/mainnet`, and prefix lookalikes without an upstream call. `app.dexhelm.com` returns the existing 503 for every method/path. `dexhelm.com` and `status.dexhelm.com` preserve their explicit informational pages and health response but never delegate proxy or static terminal traffic. Any other host, including a Workers development hostname, returns 404 and does not call `ASSETS` or `fetchImpl`.

Refactor `workers/hyperopen-worker.mjs` around an explicit host classification helper. Keep exported `handleRequest`, `resolveHyperunitTarget`, and `buildHyperunitRequest` callable with their current required arguments; add optional/internal context only where unavoidable. Make target resolution require an allowed Testnet host and exact Testnet prefix. Remove `HYPERUNIT_MAINNET_PREFIX`, the ambiguous generic proxy prefix, and Mainnet upstream routing from the Worker surface. Keep `HYPERUNIT_MAINNET_URL` only if another reviewed deployment component consumes it; otherwise remove the Worker var with the proxy route. Set `workers_dev` to `false` in `wrangler.jsonc` and add a configuration test that fails if it is re-enabled.

Keep `canonicalTerminalRedirect` for Testnet document navigations. Do not reopen the Mainnet host redirect branch; its first response remains the existing 503. Ensure routing checks host authority before the shared `/api/health` response so an unknown host does not get a positive health surface. Preserve direct DEXHelm apex/status UX only for the explicit documented GET/HEAD routes.

### Milestone 2: Bound Worker request resources and error outcomes

Add RED tests with injected `fetchImpl`, timeout scheduling, and request streams. They must prove `PUT`, `PATCH`, `DELETE`, `OPTIONS`, and other unsupported methods get `405`, `Allow: GET, HEAD, POST`, an exact JSON content type, and zero calls to `fetchImpl`/`ASSETS`. Test `POST` with `Content-Length` above 1,048,576 and a chunked stream that crosses that limit; each must return `413` before an upstream request. Test a successful small JSON `POST`, GET, and HEAD. Test an upstream throw, an abort caused by the 15-second deadline, and missing/invalid Testnet upstream configuration; each must be `502` with no error detail or reflected URL. Confirm only the current request/response header allowlists survive.

Implement named constants such as `MAX_PROXY_BODY_BYTES` and `PROXY_TIMEOUT_MS`. Validate declared content length before reading. For an undeclared/chunked POST, consume into a `Uint8Array` or `ArrayBuffer` only while its cumulative byte count is at most one MiB; cancel the reader and raise a recognized size error on the next byte. Only construct the upstream `Request` after that successful bounded read. Use an `AbortController`, inject timer functions for deterministic tests, clear the timer in `finally`, and classify aborts exactly like other generic proxy failures. Construct a no-body response for `HEAD` paths. Do not log raw request bodies, headers, agent data, or upstream error strings.

The local `tools/hyperunit-proxy/server.mjs` is the behavioral baseline for header filtering and simple error output, but do not force shared code across Node and Worker runtimes. Align the documented method/body/timeout policy in both tools only if their existing local tests demonstrate a semantic mismatch; production Worker correctness is the milestone's acceptance boundary.

### Milestone 3: Remove raw agent keys from session storage

Add RED tests in `test/hyperopen/wallet/agent_session_test.cljs`, `test/hyperopen/wallet/core_test.cljs`, `test/hyperopen/startup/restore_test.cljs`, and affected agent-runtime tests. They must simulate a browser without passkey support, enable trading, then inspect both storage objects: no agent private key may appear in `localStorage` or `sessionStorage`; the in-memory agent is ready only for the live document. Simulate reload/new-tab construction with the same storage and verify the next state is `:not-ready`, has no agent address/key, and tells the user to Enable Trading. Test disconnect, wallet account change, failed enablement, and explicit cleanup remove in-memory cache and any legacy raw session item.

Change `persist-agent-session-by-mode!` so it is never a raw-key persistence destination for new sessions. Keep its public arity for existing callers and tests, but return false or a structured no-persistence result for `:session/:plain` after an in-memory cache has been updated by the runtime. Update `load-persisted-agent-session-snapshot`, `wallet.core/load-persisted-agent-session`, and cleanup helpers to treat `hyperopen:agent-session:v1:*` in either local or session storage as legacy raw records. They must never become `:ready`; remove the session record during startup/connection cleanup and quarantine local records under the prior explicit recovery policy. Do not copy a raw session record into any new store.

Update `src/hyperopen/views/header/vm.cljs` and recovery/error copy so unsupported-passkey users are told precisely that trading is available only until reload or a new tab and needs Enable Trading again afterwards. Update `docs/SECURITY.md` and `docs/BROWSER_STORAGE.md`: session-only means in-memory agent credentials, not `sessionStorage`; a browser tab refresh is a terminal condition for that fallback. Preserve signing payload serialization, nonce reconciliation, and passkey unlock behavior. Run the signing reference-vector suites named in `docs/SECURITY.md` after the change.

### Milestone 4: Canonicalize endpoint authority and use standard attribution hashing

Add paired Node and ClojureScript vectors for enabled affiliate endpoints. Each pair must accept a semantically valid mixed-case/default-port/dot-segment URL and yield the same canonical href, reject credentials/fragments/non-HTTPS/non-default port, and leave disabled/unavailable tenants with an empty endpoint. Persist and embed only the canonical href in white-label normalized config, tenant manifests, runtime state, and CSP derivation. Existing queue delivery must resolve against the canonical current endpoint; if an old queue was written before this change, it may be retained because events do not persist an endpoint, but it must deliver only after the normal feature/status/consent checks.

In `src/hyperopen/service/attribution.cljs`, introduce a synchronous text-to-SHA-256 hex helper using the Closure Library already supplied transitively by ClojureScript. Use UTF-8 bytes, a new `goog.crypt.Sha256` instance for each digest, and 64 lowercase hexadecimal characters. Replace the custom `digest-string` for `:event/id`, `idempotency-key`, and `wallet/address-hash`; new event identifiers must be `sha256-` plus the SHA-256 hex digest. Keep existing `evt-` values acceptable to persisted-queue validation so old records do not corrupt restore, but do not generate them again. Add known vectors for `""`, `"abc"`, a Unicode string, a canonical event, and a normalized wallet address. Verify ordering-invariant canonical serialization still creates equal event IDs from maps with different insertion order.

Document in `docs/SECURITY.md` and the attribution module docstring that `wallet/address-hash` is pseudonymous and linkable, not anonymous, encrypted, or irreversible for a known-address candidate set. It is still forbidden to send raw wallet/private-key/seed/signature fields. No new package is permitted for this milestone.

### Milestone 5: Scan the resolved Clojure dependency graph with pinned input

Add a small generator under `tools/security/` that runs `clojure -Stree`, parses only selected tree lines into sorted `{name, version}` records, translates Maven names from `group/artifact` to `group:artifact`, and writes a committed `tools/security/clojure-dependencies.lock.json`. Exclude tree conflict lines marked `X`; reject malformed selected lines, duplicate coordinates with competing versions, blank versions, or a direct dependency from `deps.edn` that is absent or mismatched in the lock. The generator must record the Clojure CLI version and generation command for human audit but scanning must depend on the committed inventory, not on resolving Maven during every CI run.

Add `tools/security/clojure_osv_scan.mjs` that reads the locked inventory and performs OSV `querybatch` requests for ecosystem `Maven`, writing `out/security/clojure-osv.json`. It must validate its input and response shape, use bounded batches, fail on transport/JSON failure, and fail the security job when OSV reports an affected selected package. The output must include the scanned coordinate/version and advisory identifiers but never secrets. Add deterministic Node fixture tests for selected/conflict tree parsing, stale direct-dependency rejection, request batching, response normalization, and a reported vulnerability failure. The scanner must not rely on a mutable `npx` tool, Docker `latest` tag, or a newly added application dependency.

Add `npm run security:clojure-tree`, `npm run security:clojure-audit`, and a combined production security command. Extend `.github/workflows/security.yml` to run the Node scanner from the committed lock, upload both npm and Clojure reports, and keep registry/API failure non-green. Include a deliberate refresh step in dependency-review documentation: change `deps.edn`, run the locked-tree generator with the pinned local Clojure CLI, inspect the diff, then run the scan. The existing successful `clojure -Stree` result proves this is repository-compatible. If implementation reveals a parser ambiguity that cannot distinguish selected coordinates, stop before weakening the gate and record the exact offending output and parser limitation in this plan; do not claim Clojure coverage until a deterministic inventory is produced.

## Non-Goals

This plan does not reopen DEXHelm Mainnet, deploy or delete Cloudflare resources, rotate existing API-wallet keys, alter Hyperliquid signing wire format, introduce third-party analytics, or broadly upgrade dependencies to clear a future advisory. It does not promise that a wallet-address pseudonym is anonymous. It does not change the local proxy unless a focused parity test proves the documented production behavior and local behavior contradict each other.

## Concrete Steps

Run commands from `/Users/zh/Documents/Hyperopen`. Bootstrap the worktree before ClojureScript tests:

    npm run setup:worktree
    npm run test:cloudflare-worker
    node --test tools/cloudflare/worker.test.mjs tools/cloudflare/worker_edge_cases.test.mjs
    node --test tools/hyperunit-proxy/security_proxy_edge_cases.test.mjs
    npx shadow-cljs --force-spawn compile test
    node out/test.js
    clojure -Stree
    npm run security:sbom
    npm run security:audit
    npm run security:clojure-tree
    npm run security:clojure-audit
    npm run build:cloudflare
    npm run cloudflare:check
    npm run gates

Use focused test namespaces or test-runner selectors while developing if supported by the generated runner. The RED phase should show the old host-agnostic proxy/static behavior, raw `sessionStorage` record, noncanonical endpoint output, custom `evt-` identifier, or absent Clojure scanner as the specific failure; do not accept unrelated test failures as RED evidence.

`security:audit` and the planned OSV scanner require external services. A DNS, registry, or OSV API outage is not a passed scan. Retry once using approved network access, then record a failed/blocked security result in this plan and CI output. No remote Worker deployment is authorized by this plan; `cloudflare:check` is a dry-run only. Browser interaction wording changes require the smallest related Playwright trade regression first, then `npm run test:playwright:seo` if release headers/routes changed; clean browser sessions with `npm run browser:cleanup` if Browser MCP is used.

## Validation and Acceptance

Host authority is accepted when focused Worker tests show all of the following observable outcomes: a small Testnet request to `https://testnet.dexhelm.com/api/hyperunit/testnet/...` reaches only `https://api.hyperunit-testnet.xyz/...`; Testnet Mainnet/generic proxy paths do not invoke upstream; every request to `app.dexhelm.com` is 503; apex and status host informational routes retain their documented 200/404 behavior but their HyperUnit attempts never invoke upstream; an arbitrary hostname and a former `*.workers.dev` URL return 404 with no asset/upstream invocation. `wrangler.jsonc` must set `workers_dev` false and its regression test must reject true.

Resource handling is accepted when `GET`, `HEAD`, and small JSON `POST` are the only accepted proxy methods, all other methods receive 405 and exactly `Allow: GET, HEAD, POST`, both declared and chunked body overflow receive 413 before upstream invocation, and a simulated 15-second deadline/missing upstream/throw produces the same deterministic 502 JSON body without error details. Successful proxy responses retain only allowlisted response headers and never emit `set-cookie`. HEAD responses have no body.

Credential acceptance is met when an unsupported-passkey user can enable/sign only in the current live page, and an inspection of `localStorage` and `sessionStorage` contains no raw agent private key before or after enabling. Recreating app state after reload/new-tab yields no ready agent and requires Enable Trading. A legacy session record is removed without being loaded; a legacy local record remains quarantined for the existing explicit recovery flow. Disconnect, account switching, failure, and explicit disable clear the in-memory cached key.

Attribution acceptance is met when Node release tooling and ClojureScript runtime normalize the same affiliate endpoint to identical canonical href text, generated manifests/CSP use that value, malformed values fail closed, and consent/status checks continue to prevent disabled delivery. New event IDs, idempotency keys, and wallet pseudonyms must match SHA-256 known vectors and start `sha256-`; old valid `evt-` queue records restore safely. Tests and documentation must state that pseudonymous wallet hashes remain linkable.

Clojure dependency coverage is accepted when `clojure -Stree` generates a committed sorted inventory, fixture tests reject stale/malformed/conflict input, `npm run security:clojure-audit` queries every selected inventory entry as a Maven coordinate and writes `out/security/clojure-osv.json`, and the security workflow uploads that report along with npm audit/SBOM output. The command must exit nonzero for a reported advisory or unavailable scanner. If this cannot be achieved with the demonstrated tree output and no new runtime dependency, the active plan must instead contain the exact command output and specific parser/API incompatibility as an unchecked blocker; it must not claim coverage.

Finally, `npm run gates` must report all required `check`, ClojureScript test, and websocket gates as passing. Report changed files, focused commands and results, and any remaining external-service blocker in the handoff.

## Idempotence and Recovery

Worker tests use fake upstreams and no Cloudflare mutation; builds and dry runs are repeatable. Disabling `workers_dev` changes deployment exposure only when the user separately deploys, so do not deploy while executing this plan. A rejected/oversize request has no upstream side effect because it is read and classified before fetch. A timeout aborts the in-flight upstream request and returns generic 502; rerunning the client request is independent.

Memory-only agent sessions intentionally do not recover after reload. This is not a migration failure: users must re-enable trading, while old local plaintext records preserve their existing explicit recovery path. Remove old session records through idempotent `removeItem` behavior. URL canonicalization is idempotent: normalizing an already canonical href returns the same text. The Clojure dependency inventory generator overwrites only its named lock file after complete successful tree parsing; write to a temporary path and atomically rename so a failed parse cannot leave partial lock data. OSV output belongs under `out/security/` and is regenerated, not hand-edited.

## Artifacts and Notes

Keep generated scan reports in `out/security/` and upload them in CI. Commit only `tools/security/clojure-dependencies.lock.json`, generator/scanner scripts, and deterministic fixtures. Record the actual selected-coordinate count, OSV response summary, and no-finding/finding resolution in this plan's `Outcomes & Retrospective`. No private key, session storage value, authorization header, live endpoint credential, or raw wallet address belongs in plan artifacts or test logs.

## Interfaces and Dependencies

Keep these Worker exports compatible for callers and current tests:

    resolveHyperunitTarget(requestUrl, env)
    buildHyperunitRequest(request, targetUrl)
    handleRequest(request, env, options)

`handleRequest` may accept additional optional `options` such as `fetchImpl`, timer functions, and timeout constants for deterministic tests, but default production behavior must use the Cloudflare `fetch`, `AbortController`, and 15-second policy. It must not require a new Worker runtime package.

Keep the agent-session public helper arities. New raw-key persistence must be unavailable even though callers can still ask for a session-mode result. The durable credential protocol remains the existing passkey lockbox API; the only temporary key location in an unsupported browser is the process-local unlocked-session cache.

Add pure canonical endpoint helpers in both tenant boundaries with matching input/output vectors. Add a synchronous `sha256-hex`-style helper in the attribution service using Closure Library's `goog.crypt.Sha256`, UTF-8 conversion, and hexadecimal encoding. Do not use asynchronous `crypto.subtle.digest` because current attribution event construction and idempotency APIs are synchronous.

The Clojure inventory contains objects shaped like:

    {"name": "org.clojure:clojure", "version": "1.12.0"}

The OSV scanner submits `{package: {ecosystem: "Maven", name}, version}` objects in bounded batches and writes normalized advisory data keyed by the exact inventory coordinate. No new runtime or development package is necessary; `clojure` CLI, Node 22, and the checked-in `deps.edn` are the only toolchain inputs.

Revision note: created on 2026-07-27T12:10Z for residual security hardening after the completed 2026-07-27 remediation. It preserves the completed passkey/Node-supply-chain work and focuses on the remaining public Worker, session-storage, endpoint/hash, and Maven-graph risks. Updated at 2026-07-27T12:18Z to record plan and whitespace validation before implementation begins. Updated at 2026-07-27T13:33Z to record the completed ClojureScript hardening slice and its validation evidence. Updated at 2026-07-27T14:26Z to record Worker and Maven-scanner implementation, gates, dry-run binding output, and the no-deployment boundary. Reopened after the first read-only review found four release blockers in deadline scope, alias inventory coverage, endpoint validation parity, and frozen test materialization. Updated at 2026-07-27T15:12Z after correcting all four blockers and upgrading the audited build chain. Updated at 2026-07-27T15:54Z after correcting the second review's Maven freshness and integration-test findings, passing the final gate matrix and read-only security review, and completing the plan.
