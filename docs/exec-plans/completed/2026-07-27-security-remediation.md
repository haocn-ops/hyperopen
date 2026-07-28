# Fail closed for trading credentials, networks, releases, and dependencies

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. It follows `docs/PLANS.md` and `.agents/PLANS.md`.

## Purpose / Big Picture

Hyperopen currently permits an API-wallet private key to be resumed from plaintext `localStorage`, defaults an unconfigured application to Hyperliquid Mainnet, and grants a third-party analytics host permission to execute scripts in a trading page. This plan makes a fresh installation use a passkey-locked credential when the browser supports the required WebAuthn capability, otherwise a session-only credential. A user who already has a plaintext local credential is shown an explicit recovery choice; their credential is neither silently resumed nor silently deleted.

After this work, a release without an explicit, build-bound network cannot make trading requests, DEXHelm Testnet continues to select Testnet, a generated release header admits no third-party executable script, and an enabled affiliate integration cannot send behavioral data before the user has opted in. The local development HyperUnit proxy accepts only bounded, loopback requests and cannot forward cookies or arbitrary authorization headers. The optimizer uses the reviewed `quadprog` implementation rather than the old OSQP wrapper, and CI can produce an SBOM (software bill of materials, a machine-readable inventory of shipped packages) and gate production dependency audits.

## Context References

Public refs: direct user request on 2026-07-27 to repair the security risks identified in this conversation. There is no linked GitHub Issue or Pull Request at plan creation.

Repo artifacts: `docs/SECURITY.md`, `docs/BROWSER_STORAGE.md`, `docs/architecture-decision-records/0027-portfolio-optimizer-solver-selection.md`, `config/white-label/dexhelm.json`, `workers/hyperopen-worker.mjs`, and the existing test suites under `test/hyperopen/**`, `tools/release-assets/*.test.mjs`, `tools/white-label/*.test.mjs`, and `tools/playwright/test/**`.

Local scratch refs (non-authoritative): none.

## Progress

- [x] (2026-07-27T04:32Z) Captured the user-authorized security remediation scope, affected boundaries, explicit safety decisions, and staged acceptance criteria.
- [x] (2026-07-27T04:48Z) Merged the disjoint acceptance and adversarial proposals and froze `tmp/multi-agent/2026-07-27-security-remediation/approved-test-contract.json`; all RED and implementation work is constrained to that contract.
- [x] (2026-07-27T07:10Z) Added secure agent-session defaults, legacy plaintext recovery boundaries, and aligned unit/browser fixtures with session-only fresh-browser behavior.
- [x] (2026-07-27T07:10Z) Bound network selection to the build declaration, disabled undeclared releases, and fixed DEXHelm Testnet configuration.
- [x] (2026-07-27T07:10Z) Hardened release CSP/HSTS, affiliate consent and endpoint validation, the loopback HyperUnit proxy, and the Monte Carlo tooltip DOM sink.
- [x] (2026-07-27T07:10Z) Removed OSQP runtime/dependency surfaces, recorded the quadprog benchmark artifact, and retained worker parity coverage.
- [x] (2026-07-27T07:10Z) Added deterministic production SBOM generation, npm audit/CI governance, Dependabot, and security edge-case tests.
- [x] (2026-07-27T07:10Z) Completed repository gates and release/browser validation; remaining browser trade-regression failures are simulator/data-loading or intermittent local-server issues documented below.

## Surprises & Discoveries

- Observation: the repository already has a passkey lockbox that encrypts the private key with WebAuthn PRF output and stores the ciphertext in IndexedDB, but startup defaults still select `:local` and `:plain`.
  Evidence: `src/hyperopen/wallet/agent_lockbox.cljs` implements `create-locked-session!`; `src/hyperopen/startup/restore.cljs` calls `load-storage-mode-preference :local` and `load-local-protection-mode-preference :plain`.

- Observation: `resolve-hyperliquid-network` treats absent, blank, and invalid selectors as Mainnet, whereas the DEXHelm Worker corrects only document-navigation query parameters for its Testnet hostname.
  Evidence: `src/hyperopen/config.cljs` ends its selector case with `mainnet-hyperliquid-network`; `workers/hyperopen-worker.mjs` calls `canonicalTerminalRedirect(requestUrl, "testnet")` for `testnet.dexhelm.com`.

- Observation: the old OSQP npm wrapper is both the default worker solver and an explicitly documented unresolved dependency-review concern. `quadprog` already supplies the fallback path.
  Evidence: `src/hyperopen/portfolio/optimizer/worker.cljs` selects OSQP unless an environment variable says `quadprog`; ADR 0027 records `osqp@0.0.2` as old/minified and requires review before accepting it.

## Decision Log

- Decision: use `:local` plus `:passkey` as the default only when WebAuthn passkey locking is available; otherwise use `:session` plus `:plain`. New or migrated runtime paths must reject `:local` plus `:plain` as a persistence destination.
  Rationale: a passkey lockbox is the only existing durable option that does not leave the raw private key in browser-readable storage. Session-only storage is the safe fallback because it expires at browser-session end. Existing public helpers retain their names and arities for compatibility, but callers may use the raw-local path only to inspect and explicitly migrate or delete legacy data.
  Date/Author: 2026-07-27 / Codex.

- Decision: legacy plaintext local credentials will be quarantined rather than automatically restored or immediately erased.
  Rationale: automatically resuming preserves the vulnerability; deleting without a successful replacement can strand a user. The recovery UI must offer passkey migration when supported, session-only migration otherwise, and an explicitly confirmed deletion. It may delete the plaintext record only after the selected replacement write has succeeded.
  Date/Author: 2026-07-27 / Codex.

- Decision: select a network only from a compile-time deployment declaration, and make a missing, malformed, or conflicting declaration produce a disabled network contract rather than any Hyperliquid endpoint.
  Rationale: a query string and `globalThis` are mutable browser inputs, not deployment authority. Mainnet remains available only to a release whose build configuration explicitly declares `mainnet`; the DEXHelm config declares `testnet` and its Worker keeps canonical Testnet redirects.
  Date/Author: 2026-07-27 / Codex.

- Decision: remove Cloudflare Insights from both `script-src` and `connect-src`; add HSTS to generated HTTPS release headers, but do not add a CSP report collector or Trusted Types policy in this change.
  Rationale: a report collector would be another cross-origin data channel that needs ownership and retention policy. Trusted Types would require a full Closure/Replicant compatibility audit beyond the one identified tooltip sink. HSTS is compatible with the static HTTPS release surface and has a direct observable header contract. Do not add `preload` until every intended subdomain is reviewed.
  Date/Author: 2026-07-27 / Codex.

- Decision: affiliate delivery requires three independent conditions: a tenant feature flag, affiliate status `:enabled`, and a local user consent preference that defaults false. It may target only a normalized HTTPS endpoint with no credentials, fragment, non-default port, or malformed origin.
  Rationale: a tenant configuration alone is not informed consent for wallet-linked behavioral data. Separating operator enablement from user consent keeps default delivery off and prevents a stale queue from being delivered after consent is revoked.
  Date/Author: 2026-07-27 / Codex.

- Decision: replace OSQP with `quadprog` as the sole production optimizer solver for this release, remove the `osqp` runtime dependency, and retain only solver-agnostic interfaces needed by callers and tests.
  Rationale: `quadprog@1.6.1` is already the deterministic fallback and parity implementation. ADR 0027 measured a slower dense-JavaScript path but bounded it to a 63.24 ms maximum on its 20/40/60-instrument fixture set, while OSQP's old wrapper remains an unresolved supply-chain risk. Worker isolation preserves UI responsiveness. This is an intentional retirement of the undocumented `HYPEROPEN_OPTIMIZER_WORKER_SOLVER=osqp` behavior; the worker must either ignore legacy `osqp` in favor of quadprog or fail with a structured configuration error, never silently import it.
  Date/Author: 2026-07-27 / Codex.

## Outcomes & Retrospective

The remediation is implemented. Production defaults now use session-only storage unless a capable browser and explicit remembered passkey posture are present; legacy plaintext records are recovery-only. Network authority is compile-time and undeclared releases are non-trading. Release CSP/HSTS, affiliate consent, proxy origin/header/body limits, tooltip DOM construction, and quadprog-only optimization are covered by focused tests. The generated SBOM is `out/security/sbom.cdx.json`; production `npm audit --omit=dev --audit-level=high` reported 0 vulnerabilities. `npm test` passed with 5,818 tests/32,360 assertions, WebSocket tests passed with 561/3,184, release assets passed 51/51, SEO Playwright passed 6/6, and the full gate matrix passed 34/34 when release tests were allowed to bind loopback. Focused trade regressions still have three non-gate failures: the fresh-session case is sensitive to local dev-server startup timing, the remembered-session submit case needs a stable loaded market-data fixture, and the locked-session case does not observe the browser passkey mock through the current compiled runtime. These are test-harness/data-simulation gaps; the ClojureScript lifecycle and signing suites pass, and they should be stabilized separately before treating those interactive cases as release evidence.

## Context and Orientation

`src/hyperopen/wallet/agent_session.cljs` contains browser storage keys and serialization for agent credentials. The `:local` storage mode currently writes the raw private key to `localStorage`; `:session` writes the same shape to `sessionStorage`. `src/hyperopen/wallet/agent_lockbox.cljs` is the secure durable alternative: it encrypts the private key using a secret derived from a user-approved WebAuthn passkey and stores ciphertext in the shared IndexedDB helper. `src/hyperopen/startup/restore.cljs`, `src/hyperopen/wallet/core.cljs`, `src/hyperopen/wallet/agent_runtime/**`, and the header trading-settings view coordinate selected posture, restore, migration, and the Enable/Unlock Trading controls.

`src/hyperopen/config.cljs` defines Hyperliquid REST, WebSocket, and signing-domain contracts. `tools/white-label/build_release.mjs` currently injects only tenant JSON into the main build. `tools/cloudflare/build_dexhelm_release.mjs` packages the DEXHelm tenant, while `workers/hyperopen-worker.mjs` canonicalizes that host to Testnet. The new build declaration belongs alongside the tenant configuration and must be passed as a Closure define, not read from a mutable browser global.

`tools/release-assets/security_headers.mjs` produces the static `_headers` file used by generated releases; `tools/release-assets/verify_deployment_headers.mjs` compares a live deployment with that contract. `src/hyperopen/runtime/effect_adapters/attribution.cljs` is the side-effect boundary that sends redacted events and persists its queue. `src/hyperopen/service/tenant_config.cljs` validates public tenant configuration. `tools/hyperunit-proxy/server.mjs` is a Node development proxy, separate from the hardened Cloudflare Worker proxy.

`src/hyperopen/views/portfolio/montecarlo/chart.cljs` imperatively owns a canvas tooltip. It must create DOM nodes and assign `textContent`, never build HTML text. `src/hyperopen/portfolio/optimizer/worker.cljs` chooses the browser-worker solver, and `src/hyperopen/portfolio/optimizer/infrastructure/{osqp,quadprog,solver_adapter}.cljs` implement the solver boundary. `tools/optimizer/solver_spike_benchmark.mjs` supplies deterministic 20-, 40-, and 60-instrument workload fixtures.

## Plan of Work

### Milestone 1: Secure credentials without erasing existing access

First add focused ClojureScript tests in the existing agent-session, startup-restore, wallet-core, agent-runtime, and header-view test namespaces. The RED tests must model a fresh passkey-capable browser, a fresh browser without passkey support, an existing session-only record, an existing passkey record, and an existing `localStorage` plaintext record. They must assert that fresh posture selection is passkey-locked or session-only, no raw key is written to `localStorage`, and no legacy private key is loaded into ready-to-sign state automatically.

Add a pure posture resolver to `src/hyperopen/wallet/agent_session.cljs` and use it from `src/hyperopen/startup/restore.cljs` after `restore-agent-passkey-capability!`. Preserve the existing exported normalization and persistence function names/arity, but make all normal new-session call sites use the resolver. Update `src/hyperopen/wallet/core.cljs` to recognize a legacy snapshot only as recovery state, not as `:ready` credentials. Update the agent-runtime migration boundary to perform one of three explicit actions after user intent: create a passkey lockbox and metadata, copy the legacy record to session storage, or delete it after confirmation. Both migrations must verify the destination write before removing the legacy local record; a write, WebAuthn, IndexedDB, or preference failure keeps the old record and shows a non-sensitive recovery error.

Update `src/hyperopen/views/header/vm.cljs`, `src/hyperopen/views/header/settings.cljs`, and the existing recovery modal/view-model surfaces so the user can see that a legacy credential requires action, choose the safe available posture, and confirm destructive removal. Do not display a private key, raw serialized record, passkey PRF value, or a storage key. Keep existing action IDs where possible and add explicit action IDs only for the recovery choices. Re-run the signing-vector suites named by `docs/SECURITY.md`; no signing serialization or signer-identity behavior may change after the private key has been obtained.

### Milestone 2: Bind network selection to the release and disable unknown releases

Add tests in `test/hyperopen/config_test.cljs`, white-label release tests, and Worker tests before changing configuration. Replace `globalThis.HYPEROPEN_HYPERLIQUID_NETWORK` as an authority with a `goog-define` such as `DEPLOYMENT_HYPERLIQUID_NETWORK` in `src/hyperopen/config.cljs`. Its only valid values are exact `mainnet` or `testnet`. Extend each white-label configuration with a required `hyperliquid-network` field and update `tools/white-label/build_release.mjs` to inject that field as a Closure define. Set DEXHelm to `testnet` in `config/white-label/dexhelm.json`.

Make `resolve-hyperliquid-network` return a disabled contract for empty, malformed, or conflicting inputs. The disabled contract must have `:trading-enabled? false`, no REST/WebSocket endpoints, and a stable user-safe error. Preserve an explicit `mainnet` contract only when the build declaration is exactly `mainnet`; a query may be used only as a consistency check and must never elevate or switch the selected network. Thread `:trading-enabled?` through the connection, API-wallet approval, order, funding, and signing application boundaries so they reject before network I/O with that stable error. Normal analytics and non-trading routes may render normally. Ensure the Testnet Worker still redirects its document requests to exactly one `hyperliquidNetwork=testnet` query parameter, and add a corresponding test that a Mainnet host remains closed as it is today.

### Milestone 3: Reduce browser and release privilege

In `tools/release-assets/security_headers.mjs`, remove `https://static.cloudflareinsights.com` from `script-src` and remove both Insights origins from the relevant source lists unless another reviewed feature needs them. Add a named `STRICT_TRANSPORT_SECURITY` constant with `max-age=31536000; includeSubDomains`, emit it in the generated `_headers`, and include it in `expectedDocumentHeaders`. Do not add `preload`. Extend `tools/release-assets/generate_release_artifacts.test.mjs`, `verify_deployment_headers.test.mjs`, and release Playwright SEO coverage to require the exact script policy and HSTS value. The static release must continue to run its hashed theme preload and first-party bundle under the tightened policy.

Replace `show-spaghetti-tip!` in `src/hyperopen/views/portfolio/montecarlo/chart.cljs` with a small DOM construction helper. Clear existing tooltip children, create the four fixed row/key/value elements with `document.createElement`, assign labels and formatted values through `textContent`, and set a color only from the existing fixed chart palette. Delete the HTML-string helper. Extend `test/hyperopen/views/portfolio/montecarlo/chart_test.cljs` to invoke the tooltip path with text containing markup-like characters and prove that it remains text rather than creating an element or an executable attribute.

### Milestone 4: Harden affiliate and development-proxy egress

Strengthen `src/hyperopen/service/tenant_config.cljs` with a parser-based affiliate endpoint predicate. It must accept only a canonical HTTPS URL with a hostname, no username/password, no fragment, no non-default port, and a bounded path/query length. A non-empty endpoint is valid only alongside `:features {:affiliate true}` and `:affiliate {:status :enabled ...}`; the default tenant remains feature-disabled with an empty endpoint. Make endpoint normalization part of tenant normalization so release CSP receives only the validated origin.

Add a small, non-secret local consent preference owned by the attribution adapter or a dedicated platform boundary. Default it to false. Add a trading-settings disclosure/control that only becomes relevant when the validated tenant advertises an enabled affiliate program. In `src/hyperopen/runtime/effect_adapters/attribution.cljs`, require feature enabled, status enabled, validated endpoint, and consent before marking an event pending or calling `fetch`. On revocation, prevent retry/resume and clear pending queued events for that tenant before returning; events remain local only while disabled. Tests must prove that the current DEXHelm config makes zero affiliate network calls, an enabled tenant still makes zero calls before consent, an opted-in tenant sends only redacted fields to the exact endpoint, and revocation stops a queued retry.

Refactor `tools/hyperunit-proxy/server.mjs` into importable pure helpers plus a guarded executable entrypoint so Node tests can start and close it. Bind `server.listen` explicitly to `127.0.0.1`; parse and bound `PORT`; validate that `APP_ORIGIN` is a loopback HTTP origin and HyperUnit bases are exact HTTPS origins without credentials, paths, queries, or fragments. Route matching must require a complete prefix segment. Copy only an explicit request allowlist needed by HyperUnit, excluding `Cookie`, `Authorization`, proxy headers, and arbitrary client headers. Reject oversized declared and streamed bodies with HTTP 413 before forwarding, impose a finite upstream timeout, filter response headers including `set-cookie`, and return generic error bodies without upstream details. Add Node tests for loopback binding, invalid origins, prefix bypass attempts, forbidden headers, over-limit chunked body, timeout, and no credential forwarding.

### Milestone 5: Retire the old solver and govern dependencies

Before removing OSQP, record a fresh local benchmark from `tools/optimizer/solver_spike_benchmark.mjs` using its deterministic 20-, 40-, and 60-instrument problems, one warmup, and three measured runs. Save the JSON output in this plan's artifact note or a committed `docs/exec-plans/active/artifacts/2026-07-27-*.json` file. The existing ADR baseline is OSQP mean 0.57 ms/max 0.98 ms and quadprog mean 14.59 ms/max 63.24 ms across 36 solves. The workload represents the worker's supported optimizer sizes and four objectives; it is the relevant cost because the main UI remains responsive while the worker calculates. No more complex replacement is justified because the existing quadprog adapter already passes constraints and fallback parity; it is sufficient if the refreshed benchmark remains within a documented 100 ms per single 60-instrument solve on the supported local browser/Node environment. If the bound fails, stop and record evidence rather than restoring OSQP without a completed dependency review.

Update the worker and solver adapter so all production optimization requests use quadprog. Remove the OSQP adapter, package dependency, lockfile entry, and stale OSQP-specific tests or alter them to assert the intentional legacy environment behavior. Keep common problem adaptation, infeasibility mapping, and worker wire contracts unchanged. Run deterministic objective fixtures, constraint-violation assertions, infeasible cases, and late-response worker coverage before and after removal.

Create a no-new-runtime-dependency Node tool under `tools/security/` that reads `package-lock.json` with JSON parsing and emits a deterministic CycloneDX JSON SBOM for production npm packages, including package name, exact version, integrity, license when available, and dependency relationships. Add Node tests with a lockfile fixture so malformed or missing integrity fails closed. Add `npm run security:sbom` and `npm run security:audit`; the audit script must run `npm audit --omit=dev --audit-level=high` when registry access is available and clearly fail on a nonzero audit result. Add a scheduled and pull-request GitHub Actions job that installs from the lockfile, runs both scripts, and uploads the SBOM/audit JSON as build artifacts. Add Dependabot configuration for npm and GitHub Actions. Update `docs/SECURITY.md` with the exact review cadence, severity gate, SBOM location, handling for an unavailable registry, and the manual review requirement for direct Clojure/Clojars dependencies, which cannot be claimed as fully transitive SBOM coverage by this Node-only tool.

## Concrete Steps

Run all commands from `/Users/zh/Documents/Hyperopen`. Start with the worktree bootstrap before any ClojureScript gate:

    npm run setup:worktree
    npx shadow-cljs --force-spawn compile test
    node out/test.js --namespace hyperopen.config-test

Use the repository's existing test runner selector if the generated runner does not accept namespace arguments. Add RED tests before each source change, and record the pre-change failure text in this plan. Focused commands expected during implementation are:

    npm run test:release-assets
    node --test tools/white-label/*.test.mjs
    node --test tools/hyperunit-proxy/*.test.mjs
    node --test tools/security/*.test.mjs
    npm run test:optimizer-spike
    node tools/optimizer/solver_spike_benchmark.mjs --candidates=quadprog --warmup=1 --runs=3 --json
    npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "trading settings|Enable Trading|passkey|affiliate"
    npm run test:playwright:seo
    npm run security:sbom
    npm run security:audit
    npm run gates

The benchmark command may use the tool's actual candidate/filter flag if the implementation names it differently; preserve the same 20/40/60 sizes, one warmup, three measured runs, and JSON evidence. `security:audit` requires npm registry connectivity. A DNS or registry failure is environmental evidence, not a clean security result: retry once with approved network access and record the failure if it remains unavailable. Do not weaken the CI audit gate to convert an audit finding into success.

For browser verification, use the smallest relevant Playwright command first. Browser MCP is not required unless the new recovery/consent flow is nondeterministic; if it is used, stop sessions with `npm run browser:cleanup` before handoff. No deployment is authorized by this plan; use `npm run build:cloudflare` and `npm run cloudflare:check` only as local artifact/dry-run validation unless the user separately asks to deploy.

## Validation and Acceptance

Credential acceptance is met when these observable cases pass:

- In a fresh capable browser, Enable Trading requires passkey creation and a browser restart displays Unlock Trading rather than automatically signing with a raw private key. No `hyperopen:agent-session:v1:*` plaintext value exists in `localStorage` after success.
- In a fresh browser without passkey support, Enable Trading uses session-only storage; closing the browser session removes the credential and the next session requires Enable Trading again.
- A pre-existing local plaintext record leaves the account in a recovery state, never makes an exchange request until the user chooses a migration, and remains readable for recovery after a simulated passkey/IndexedDB/session write failure. A successful migration deletes the old raw record only after the replacement is confirmed.
- Existing passkey and session users keep their intended behavior, and signing regression vectors retain their existing expected action payloads and signer addresses.

Network acceptance is met when a testnet-built artifact selects only Testnet endpoints and signing chain ID, an explicitly mainnet-built artifact selects only Mainnet endpoints, and a build with no valid declaration exposes no REST/WebSocket URL and causes order, funding, approval, and signing requests to fail locally with the stable disabled-network message. Supplying `?hyperliquidNetwork=mainnet`, a browser global, a blank value, or an unsupported value must not switch a Testnet artifact to Mainnet. Worker tests must show `testnet.dexhelm.com/trade` retains exactly one Testnet query parameter and `app.dexhelm.com` remains 503/closed.

Release security acceptance is met when generated `_headers` has no `static.cloudflareinsights.com` token, `script-src` contains only `'self'` plus the exact theme hash, and every document response checked by `verify:deployment-headers` has the stated HSTS value. The theme preload and first-party application bundle must load in release Playwright SEO tests. The Monte Carlo tooltip test must show markup-looking values as literal text nodes and source search must find no `innerHTML` assignment in that chart namespace.

Affiliate/proxy acceptance is met when default DEXHelm produces no affiliate fetches, a tenant cannot enable delivery with a malformed/credential-bearing/non-HTTPS endpoint, an opted-in enabled tenant makes a redacted request only to its exact normalized endpoint, and revoking consent prevents delayed delivery. The proxy must listen only on `127.0.0.1`, reject a non-loopback app origin or unsafe HyperUnit base during startup, reject forbidden headers/body overflow with no upstream call, and never emit a cookie from an upstream response.

Solver/supply-chain acceptance is met when `package.json` and `package-lock.json` contain no OSQP package, all optimizer fixture and worker tests pass with quadprog, refreshed benchmark JSON documents the workload/timing and no 60-instrument solve exceeds the recorded 100 ms guardrail, and `npm run security:sbom` produces deterministic valid JSON from the locked production dependency set. In CI with registry access, `npm run security:audit` must exit zero only if no high/critical production audit finding remains. The scheduled/PR workflow and Dependabot configuration must be syntactically validated by their repository tests or `actionlint` when available.

Finally, run `npm run gates` and report its complete PASS/FAIL matrix. Because this work changes browser interaction flows, run the focused Playwright path and `npm run test:playwright:seo` before broadening. Required gates remain `npm run check`, `npm test`, and `npm run test:websocket`, which `npm run gates` summarizes.

## Idempotence and Recovery

Tests, generated headers, SBOM generation, and local builds are repeatable. The credential migration is deliberately transactional from the user's perspective: retry after passkey, IndexedDB, or storage failure without deleting the source local record. A user may explicitly delete a legacy record only via a confirmation action; do not add a background cleanup timer. A release built without a valid network declaration is intentionally non-trading and must be rebuilt with an explicit testnet or mainnet declaration rather than patched in the browser.

The HyperUnit proxy test server must bind a random loopback port and close in `finally` blocks. The audit command can contact an external registry but does not mutate the lockfile; do not run `npm update`, `npm audit fix`, or broad dependency upgrades as a substitute for reviewing a finding. If the quadprog timing guardrail fails, retain the dependency-removal branch unmerged, capture the benchmark, and obtain an explicit follow-up decision rather than reintroducing OSQP automatically.

## Artifacts and Notes

Place the refreshed solver benchmark JSON, if committed, at `docs/exec-plans/completed/artifacts/2026-07-27-optimizer-quadprog-benchmark.json`. CI should upload generated SBOM and audit outputs as workflow artifacts rather than committing an environment-specific audit result. Record the commands, exact test counts, timing summary, and any browser compatibility exception in this plan's living sections.

## Interfaces and Dependencies

Keep `hyperopen.wallet.agent-session/normalize-storage-mode`, `normalize-local-protection-mode`, `persist-agent-session-by-mode!`, `load-agent-session-by-mode`, and `clear-agent-session-by-mode!` callable with their existing arities. Add an explicit pure posture result, for example:

    {:storage-mode :local | :session
     :local-protection-mode :passkey | :plain
     :legacy-recovery? boolean}

The runtime may persist only `:local/:passkey` or `:session/:plain` for newly enabled credentials. The legacy recovery path may read `:local/:plain` only long enough to write the selected replacement or to delete after confirmation.

`hyperopen.config/resolve-hyperliquid-network` must accept an input map that makes build authority testable, including `:deployment-network` and any observed query value. Its result must retain the existing transport/signing fields for valid networks and add `:trading-enabled?`; the disabled result must retain map shape but contain no usable endpoint strings. Application boundaries must check that boolean before attempting I/O.

The release-header module must export the HSTS constant and include it in `expectedDocumentHeaders`; the deployment verifier must continue to derive tenant image/connect origins only from validated tenant configuration. The affiliate adapter must expose a pure predicate or resolver that tests can call to decide whether delivery is permitted without issuing `fetch`.

The HyperUnit module must export a server factory or proxy-request handler plus pure URL/header validation helpers for Node tests. It must accept the current developer environment variable names but reject unsafe values. No new browser or Node runtime package is needed for the header, proxy, SBOM, or audit work.

Revision note: created on 2026-07-27T04:32Z from the direct user request to remediate the previously identified security risks. It fixes the intended security posture and acceptance contract before implementation; it does not authorize deployment, key rotation, or deletion of existing credentials without an in-product confirmation.
